package com.kosherflip.guard;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Installs and updates the other KosherFlip apps straight from the project's
 * GitHub releases, so a phone set up by QR code needs no computer, and the
 * admin can update everything from the console ("Install / update apps").
 * KosherGuard's own traffic bypasses its DNS filter, so github.com need not be
 * allowed for the user.
 */
public final class Companions {
    private static final String TAG = "KosherGuard";
    static final String DEFAULT_BASE = "https://github.com/aryebleich/kosherflipphne/releases/latest/download/";
    static final String[][] APPS = {
            {BuildConfigShim.LAUNCHER, "FlipLauncher.apk"},
            {BuildConfigShim.BROWSER, "KosherBrowser.apk"},
            {BuildConfigShim.AI, "KosherAI.apk"},
            {BuildConfigShim.KEYS, "KosherKeys.apk"},
            {BuildConfigShim.GUARD, "KosherGuard.apk"}, // last: updating ourselves restarts us
    };
    private static final String ACTION_DONE = "com.kosherflip.guard.INSTALL_DONE";

    /** Progress and Android's "Install?" prompts go to the screen that is open (the setup checklist). */
    interface Listener {
        void progress(String text);
        /** Android needs the user to confirm an install: start this from the visible activity. */
        void confirm(Intent confirmIntent);
    }

    static volatile Listener listener;
    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CountDownLatch> waiting =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Integer, String> results =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Companions() {}

    private static void progress(String text) {
        Listener l = listener;
        if (l != null) l.progress(text);
    }

    /** Direct download address of one app, for the "download in the browser" fallback. */
    static String url(String baseUrl, String file) {
        String base = baseUrl == null || baseUrl.isEmpty() ? DEFAULT_BASE : baseUrl;
        return (base.endsWith("/") ? base : base + "/") + file;
    }

    /** Installs missing apps and updates outdated ones. Blocking; call off the UI thread. */
    static String installOrUpdate(Context ctx, String baseUrl) {
        String base = baseUrl == null || baseUrl.isEmpty() ? DEFAULT_BASE : baseUrl;
        if (!base.endsWith("/")) base += "/";
        StringBuilder report = new StringBuilder();
        JSONObject versions = null;
        try {
            versions = new JSONObject(new String(download(base + "versions.json"), "UTF-8"));
        } catch (Exception e) {
            report.append("No version list online (").append(e.getMessage()).append(") - installing missing apps only.\n");
        }
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        ComponentName admin = PolicyEnforcer.admin(ctx);
        boolean owner = dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName());
        if (owner) {
            // Our own restrictions also stop the owner's installs; lift them while installing.
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS);
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        }
        try {
            for (String[] app : APPS) {
                String pkg = app[0], file = app[1];
                long installed = versionCode(ctx, pkg);
                long latest = versions == null ? -1 : versions.optLong(pkg, -1);
                boolean need = installed < 0 || (latest > 0 && latest > installed);
                if (!need) {
                    report.append(file).append(": up to date\n");
                    continue;
                }
                try {
                    progress("Downloading " + file + "\u2026");
                    byte[] apk = download(base + file, file);
                    progress("Installing " + file + " \u2013 press Install if asked\u2026");
                    String result = install(ctx, apk, pkg);
                    report.append(file).append(result == null
                            ? (installed < 0 ? ": installed\n" : ": updated\n")
                            : ": not installed (" + result + ")\n");
                } catch (Exception e) {
                    Log.w(TAG, "install " + file, e);
                    report.append(file).append(": failed (").append(e.getMessage()).append(")\n");
                }
            }
        } finally {
            if (owner) PolicyEnforcer.apply(ctx); // restores the restrictions
        }
        return report.toString();
    }

    static long versionCode(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            return Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static byte[] download(String url) throws Exception {
        return download(url, null);
    }

    private static byte[] download(String url, String label) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false); // GitHub redirects across hosts
            c.setConnectTimeout(20_000);
            c.setReadTimeout(60_000);
            int code = c.getResponseCode();
            if (code / 100 == 3) {
                url = c.getHeaderField("Location");
                c.disconnect();
                continue;
            }
            if (code != 200) throw new java.io.IOException("download failed, HTTP " + code);
            long total = c.getContentLengthLong();
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[64 * 1024];
                int n;
                long done = 0, lastShown = 0;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                    done += n;
                    if (label != null && done - lastShown > 256 * 1024) {
                        lastShown = done;
                        progress("Downloading " + label + "\u2026 " + (total > 0 ? (done * 100 / total) + "%"
                                : (done / 1024) + " KB"));
                    }
                }
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new java.io.IOException("too many redirects");
    }

    /** Installs one APK and waits for the result. Returns null on success, else the reason. */
    private static String install(Context ctx, byte[] apk, String pkg) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = pi.createSession(params);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        waiting.put(id, latch);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream os = s.openWrite("app.apk", 0, apk.length)) {
                os.write(apk);
                s.fsync(os);
            }
            Intent done = new Intent(ctx, InstallResultReceiver.class).setAction(ACTION_DONE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(ctx, id, done, flags).getIntentSender());
        }
        // Wait for Android (and the user's "Install" press) before the next app.
        boolean finished = latch.await(10, java.util.concurrent.TimeUnit.MINUTES);
        waiting.remove(id);
        String r = results.remove(id);
        if (!finished) return "no answer - press Install when Android asks";
        return "ok".equals(r) ? null : r;
    }

    static String describe(int status, String msg) {
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "cancelled";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "blocked by the phone (Play Protect?)";
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "an older copy signed differently is installed - uninstall it first";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "not compatible with this phone";
            case PackageInstaller.STATUS_FAILURE_INVALID: return "the download is damaged - try again";
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "not enough storage";
            default: return msg == null ? "failed" : msg;
        }
    }

    /** Shows the system confirmation if Android asks for one (not needed when we are device owner). */
    public static class InstallResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            int id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm == null) return;
                Listener l = listener;
                if (l != null) {
                    l.confirm(confirm); // started by the visible screen: never blocked by Android
                } else {
                    try {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (RuntimeException e) {
                        Log.w(TAG, "cannot show install prompt", e);
                    }
                }
                return;
            }
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (status != PackageInstaller.STATUS_SUCCESS) Log.w(TAG, "install failed: " + msg);
            results.put(id, status == PackageInstaller.STATUS_SUCCESS ? "ok" : describe(status, msg));
            java.util.concurrent.CountDownLatch latch = waiting.get(id);
            if (latch != null) latch.countDown();
        }
    }
}
