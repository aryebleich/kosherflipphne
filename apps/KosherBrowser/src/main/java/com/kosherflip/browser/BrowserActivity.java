package com.kosherflip.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.kosherflip.common.WordFilter;

import java.io.ByteArrayInputStream;

/**
 * KosherBrowser: a fast, filtered browser for flip phones.
 *  - every page's text is checked by the word filter; bad pages are replaced by a block page
 *    with "Request access" (sent to the admin in KosherGuard)
 *  - pictures: none / trusted sites only / blurred / all (set by the admin)
 *  - video blocked unless allowed; Google SafeSearch forced; image & video search blocked
 *  - ads and trackers blocked (pages load faster)
 *  - downloads limited to safe file types; apps only when the admin allows installs
 *  - keypad pointer (5), scrolling (2/8), back/forward (4/6), address (0), home (1), menu (*)
 * Websites themselves are additionally filtered system-wide by KosherGuard's DNS filter.
 */
public class BrowserActivity extends Activity {
    private static final String HOME = "kosherflip://home";
    private static final String BLOCK_BASE = "https://blocked.kosherflip.invalid/";

    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private CursorView cursor;
    private boolean cursorOn;
    private volatile BrowserConfig cfg;
    private volatile String pageHost = "";
    private volatile String blockedHost;
    private long lastScan;
    /** A text field inside the page has focus: number keys type instead of acting as shortcuts. */
    private volatile boolean webEditing;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.pad(this);
        cfg = BrowserConfig.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Search or type a website");
        address.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.GRAY);
        address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v, id, ev) -> {
            String url = UrlRules.fromInput(address.getText().toString());
            if (url != null) {
                load(url);
                web.requestFocus();
            }
            return true;
        });
        root.addView(address, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new LinearLayout.LayoutParams(-1, (int) (3 * getResources().getDisplayMetrics().density)));

        FrameLayout frame = new FrameLayout(this);
        web = new WebView(this);
        cursor = new CursorView(this);
        cursor.setVisibility(View.GONE);
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSafeBrowsingEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new Bridge(), "KF");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                scanLater();
            }
        });
        web.setDownloadListener((url, ua, disposition, mime, length) -> download(url, disposition, mime));

        // Pointer on by default on phones without a touch screen.
        setCursor(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN));

        if (!handleIntent(getIntent())) showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = BrowserConfig.load(this); // admin may have changed settings
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private boolean handleIntent(Intent i) {
        if (i == null) return false;
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            load(i.getDataString());
            return true;
        }
        if (Intent.ACTION_WEB_SEARCH.equals(i.getAction())) {
            String q = i.getStringExtra("query");
            if (q != null) {
                load(UrlRules.fromInput(q));
                return true;
            }
        }
        return false;
    }

    private void showHome() {
        blockedHost = null;
        address.setText("");
        web.loadDataWithBaseURL(HOME, HomePage.html(this), "text/html", "utf-8", null);
    }

    private void load(String url) {
        if (url == null) return;
        if (url.startsWith(HOME)) {
            showHome();
            return;
        }
        web.loadUrl(UrlRules.enforceSafeSearch(url));
    }

    private void showBlocked(String host, String reason) {
        blockedHost = host;
        web.stopLoading();
        web.loadDataWithBaseURL(BLOCK_BASE, HomePage.blocked(host, reason, true), "text/html", "utf-8", null);
    }

    // ---- filtering -------------------------------------------------------------

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            String url = req.getUrl().toString();
            String scheme = req.getUrl().getScheme();
            if (scheme == null) return true;
            if (scheme.equals("tel") || scheme.equals("mailto") || scheme.equals("geo") || scheme.equals("sms")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl()));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
            if (!scheme.equals("http") && !scheme.equals("https")) return true; // no intent:// app jumps
            if (UrlRules.isBlockedSearch(url)) {
                showBlocked("Image and video search", "Picture and video search results are not available.");
                return true;
            }
            String safe = UrlRules.enforceSafeSearch(url);
            if (!safe.equals(url)) {
                view.loadUrl(safe);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (url.startsWith(BLOCK_BASE) || url.startsWith(HOME)) return;
            blockedHost = null;
            pageHost = UrlRules.host(url);
            webEditing = false;
            address.setText(url);
            BrowserConfig c = cfg;
            view.getSettings().setBlockNetworkImage(!c.imagesAllowedOn(pageHost));
            HomePage.recordVisit(BrowserActivity.this, url);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            injectCss(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (url.startsWith(BLOCK_BASE) || url.startsWith(HOME)) return;
            injectCss(view);
            view.evaluateJavascript(SCANNER, null);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            String url = req.getUrl().toString();
            String host = UrlRules.host(url);
            if (UrlRules.hostMatches(host, UrlRules.ADS)) return empty();
            BrowserConfig c = cfg;
            if (c.blockVideo) {
                String accept = req.getRequestHeaders() == null ? null : req.getRequestHeaders().get("Accept");
                if (UrlRules.isVideo(url, accept)) return empty();
            }
            return null;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
            if (!req.isForMainFrame()) return;
            int code = err.getErrorCode();
            if (code == ERROR_HOST_LOOKUP) {
                // KosherGuard's DNS filter answered "no such site".
                showBlocked(UrlRules.host(req.getUrl().toString()), "This website is not allowed by the filter.");
            }
        }
    }

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private void injectCss(WebView view) {
        BrowserConfig c = cfg;
        StringBuilder css = new StringBuilder();
        if ("blur".equals(c.imageMode)) {
            css.append("img,picture,svg image,canvas,[style*='background-image']{filter:blur(16px)!important}");
        }
        if (!c.imagesAllowedOn(pageHost)) {
            css.append("img,picture,svg image{visibility:hidden!important}");
        }
        if (c.blockVideo) {
            css.append("video,audio[autoplay],iframe[src*='youtube'],iframe[src*='vimeo'],iframe[src*='dailymotion']"
                    + "{display:none!important}");
        }
        if (css.length() == 0) return;
        String js = "(function(){var s=document.getElementById('kf-css');if(!s){s=document.createElement('style');"
                + "s.id='kf-css';(document.head||document.documentElement).appendChild(s);}s.textContent="
                + org.json.JSONObject.quote(css.toString()) + ";})()";
        view.evaluateJavascript(js, null);
    }

    /** Sends the page text now and again (debounced) whenever the page changes. */
    private static final String SCANNER =
            "(function(){if(window.__kf)return;window.__kf=1;var t;function send(){try{"
            + "KF.scan(document.title+' '+(document.body?document.body.innerText:'').slice(0,40000));}catch(e){}}"
            + "send();new MutationObserver(function(){clearTimeout(t);t=setTimeout(send,900);})"
            + ".observe(document.documentElement,{childList:true,subtree:true,characterData:true});"
            + "function ed(el){return !!el&&(el.isContentEditable||/^(INPUT|TEXTAREA|SELECT)$/.test(el.tagName));}"
            + "document.addEventListener('focusin',function(e){KF.editing(ed(e.target));},true);"
            + "document.addEventListener('focusout',function(){KF.editing(false);},true);})()";

    private void scanLater() {
        if (SystemClock.elapsedRealtime() - lastScan < 500) return;
        lastScan = SystemClock.elapsedRealtime();
        web.evaluateJavascript("try{KF.scan(document.title)}catch(e){}", null);
    }

    private final class Bridge {
        @JavascriptInterface
        public void scan(String text) {
            WordFilter.Result r = cfg.words.check(text);
            if (r.blocked) {
                String host = pageHost;
                runOnUiThread(() -> showBlocked(host, "This page contains content that is not appropriate."));
            }
        }

        @JavascriptInterface
        public void editing(boolean on) {
            webEditing = on;
        }

        @JavascriptInterface
        public void request() {
            String host = blockedHost;
            if (host == null) return; // only from our own block page
            runOnUiThread(() -> askForAccess(host));
        }
    }

    private void askForAccess(String host) {
        EditText note = new EditText(this);
        note.setHint("Why do you need it? (optional)");
        new AlertDialog.Builder(this).setTitle("Request " + host)
                .setView(note)
                .setPositiveButton("Send", (d, w) -> {
                    Intent i = new Intent("com.kosherflip.guard.REQUEST").setPackage("com.kosherflip.guard")
                            .putExtra("type", "site").putExtra("value", host)
                            .putExtra("note", note.getText().toString());
                    sendBroadcast(i);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---- downloads -------------------------------------------------------------

    private void download(String url, String disposition, String mime) {
        String name = URLUtil.guessFileName(url, disposition, mime);
        BrowserConfig c = cfg;
        if (!UrlRules.downloadAllowed(name, mime, c.downloadTypes, c.allowApk)) {
            Toast.makeText(this, "This type of file can't be downloaded: " + name, Toast.LENGTH_LONG).show();
            return;
        }
        DownloadManager dm = getSystemService(DownloadManager.class);
        if (dm == null) return;
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setMimeType(mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) r.addRequestHeader("Cookie", cookies);
        r.addRequestHeader("User-Agent", web.getSettings().getUserAgentString());
        dm.enqueue(r);
        Toast.makeText(this, "Downloading " + name, Toast.LENGTH_SHORT).show();
    }

    // ---- keypad ----------------------------------------------------------------

    private void setCursor(boolean on) {
        cursorOn = on;
        cursor.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (address.hasFocus() && e.getKeyCode() != KeyEvent.KEYCODE_BACK
                && e.getKeyCode() != KeyEvent.KEYCODE_DPAD_DOWN) {
            return super.dispatchKeyEvent(e);
        }
        if (webEditing && handledKey(e.getKeyCode())) {
            return super.dispatchKeyEvent(e); // typing into a page's text field
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) {
            return handledKey(e.getKeyCode()) || super.dispatchKeyEvent(e);
        }
        int step = (int) (18 * getResources().getDisplayMetrics().density) * (e.getRepeatCount() > 2 ? 2 : 1);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (address.hasFocus()) { web.requestFocus(); return true; }
                if (cursorOn) { moveCursor(0, step); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (cursorOn) { moveCursor(0, -step); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (cursorOn) { moveCursor(-step, 0); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (cursorOn) { moveCursor(step, 0); return true; }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (cursorOn) { tap(); return true; }
                break;
            case KeyEvent.KEYCODE_5: setCursor(!cursorOn); return true;
            case KeyEvent.KEYCODE_2: web.pageUp(false); return true;
            case KeyEvent.KEYCODE_8: web.pageDown(false); return true;
            case KeyEvent.KEYCODE_4: if (web.canGoBack()) web.goBack(); return true;
            case KeyEvent.KEYCODE_6: if (web.canGoForward()) web.goForward(); return true;
            case KeyEvent.KEYCODE_1: showHome(); return true;
            case KeyEvent.KEYCODE_0: address.requestFocus(); return true;
            case KeyEvent.KEYCODE_3: zoom(10); return true;
            case KeyEvent.KEYCODE_9: zoom(-10); return true;
            case KeyEvent.KEYCODE_STAR:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SOFT_LEFT:
                showMenu();
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (web.canGoBack()) { web.goBack(); return true; }
                break;
            default:
                break;
        }
        return super.dispatchKeyEvent(e);
    }

    private static boolean handledKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_0: case KeyEvent.KEYCODE_1: case KeyEvent.KEYCODE_2: case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4: case KeyEvent.KEYCODE_5: case KeyEvent.KEYCODE_6: case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9: case KeyEvent.KEYCODE_STAR:
                return true;
            default:
                return false;
        }
    }

    private void moveCursor(int dx, int dy) {
        float nx = cursor.x + dx, ny = cursor.y + dy;
        int w = web.getWidth(), h = web.getHeight();
        int edge = (int) (24 * getResources().getDisplayMetrics().density);
        if (ny > h - edge) { web.scrollBy(0, dy); ny = h - edge; }
        if (ny < edge && web.getScrollY() > 0) { web.scrollBy(0, dy); ny = edge; }
        cursor.x = Math.max(0, Math.min(w - 4, nx));
        cursor.y = Math.max(0, Math.min(h - 4, ny));
        cursor.invalidate();
    }

    private void tap() {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, cursor.x, cursor.y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, cursor.x, cursor.y, 0);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void zoom(int delta) {
        WebSettings s = web.getSettings();
        s.setTextZoom(Math.max(70, Math.min(200, s.getTextZoom() + delta)));
    }

    private void showMenu() {
        String[] items = {"Home", "Back", "Forward", "Reload", "Type address / search",
                (cursorOn ? "Pointer off" : "Pointer on"), "Bigger text", "Smaller text", "Downloads",
                "Request this site from admin"};
        new AlertDialog.Builder(this).setItems(items, (d, which) -> {
            switch (which) {
                case 0: showHome(); break;
                case 1: if (web.canGoBack()) web.goBack(); break;
                case 2: if (web.canGoForward()) web.goForward(); break;
                case 3: web.reload(); break;
                case 4: address.requestFocus(); break;
                case 5: setCursor(!cursorOn); break;
                case 6: zoom(10); break;
                case 7: zoom(-10); break;
                case 8:
                    try {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                    } catch (ActivityNotFoundException ignored) {
                    }
                    break;
                case 9:
                    String h = blockedHost != null ? blockedHost : pageHost;
                    if (h != null && !h.isEmpty()) askForAccess(h);
                    break;
                default: break;
            }
        }).show();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
