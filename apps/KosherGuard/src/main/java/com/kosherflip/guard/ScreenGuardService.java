package com.kosherflip.guard;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.kosherflip.common.WordFilter;

import java.util.HashSet;
import java.util.Set;

/**
 * The screen filter: reads the text that any app shows (and notifications),
 * scores it with {@link WordFilter} and, if it is inappropriate, covers the
 * screen and goes back. Runs entirely on the phone, debounced so it costs
 * almost nothing while scrolling.
 */
public class ScreenGuardService extends AccessibilityService {
    private static final long DEBOUNCE_MS = 350;
    private static final int MAX_NODES = 600;
    private static final int MAX_CHARS = 20_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> exempt = new HashSet<>();
    private volatile WordFilter filter;
    private volatile boolean enabled = true;
    private long policyLoadedAt;
    private String pendingPackage;
    private TextView overlay;
    private long lastBlockAt;
    private int recentBlocks;

    private final Runnable scan = this::scanNow;

    @Override
    protected void onServiceConnected() {
        exempt.add(getPackageName());
        exempt.add(BuildConfigShim.LAUNCHER);
        exempt.add(BuildConfigShim.BROWSER); // the browser filters each page itself
        exempt.add("com.android.systemui.keyguard");
        reload();
    }

    private void reload() {
        Policy p = Policy.load(this);
        enabled = p.screenFilter;
        filter = WordFilter.standard(p.customWords);
        policyLoadedAt = SystemClock.elapsedRealtime();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (SystemClock.elapsedRealtime() - policyLoadedAt > 30_000) reload();
        if (!enabled) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (exempt.contains(p) || p.contains("inputmethod") || p.contains("keyboard")) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence t : event.getText()) sb.append(t).append(' ');
            if (filter.check(sb).blocked) {
                Stats.recordBlockedScreen(this, p);
                // A dirty notification: dismiss the shade so it isn't read.
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            return;
        }
        pendingPackage = p;
        handler.removeCallbacks(scan);
        handler.postDelayed(scan, DEBOUNCE_MS);
    }

    private void scanNow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        collect(root, sb, new int[] {0});
        WordFilter.Result r = filter.check(sb);
        if (r.blocked) block(pendingPackage, r.reason);
    }

    private static void collect(AccessibilityNodeInfo n, StringBuilder sb, int[] count) {
        if (n == null || count[0]++ > MAX_NODES || sb.length() > MAX_CHARS) return;
        if (n.isVisibleToUser()) {
            CharSequence t = n.getText();
            if (t != null && !n.isPassword() && !n.isEditable()) sb.append(t).append(' ');
            CharSequence d = n.getContentDescription();
            if (d != null) sb.append(d).append(' ');
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb, count);
    }

    private void block(String pkg, String reason) {
        long now = SystemClock.elapsedRealtime();
        recentBlocks = now - lastBlockAt < 10_000 ? recentBlocks + 1 : 1;
        lastBlockAt = now;
        Stats.recordBlockedScreen(this, pkg);
        showOverlay();
        // Leave the page; if the app keeps showing it, leave the app.
        performGlobalAction(recentBlocks >= 3 ? GLOBAL_ACTION_HOME : GLOBAL_ACTION_BACK);
    }

    private void showOverlay() {
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm == null) return;
        if (overlay == null) {
            overlay = new TextView(this);
            overlay.setText("⛔\nThis content was blocked by KosherGuard");
            overlay.setTextColor(Color.WHITE);
            overlay.setBackgroundColor(0xF0101010);
            overlay.setGravity(Gravity.CENTER);
            overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            try {
                wm.addView(overlay, lp);
            } catch (RuntimeException e) {
                overlay = null;
                return;
            }
        }
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, 1800);
    }

    private final Runnable hideOverlay = () -> {
        if (overlay != null) {
            try {
                getSystemService(WindowManager.class).removeView(overlay);
            } catch (RuntimeException ignored) {
            }
            overlay = null;
        }
    };

    @Override
    public void onInterrupt() {
    }
}
