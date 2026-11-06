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

    private Companions() {}

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
                    install(ctx, download(base + file), pkg);
                    report.append(file).append(installed < 0 ? ": installed\n" : ": updated\n");
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
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                return out.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new java.io.IOException("too many redirects");
    }

    private static void install(Context ctx, byte[] apk, String pkg) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(pkg);
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream os = s.openWrite("app.apk", 0, apk.length)) {
                os.write(apk);
                s.fsync(os);
            }
            Intent done = new Intent(ctx, InstallResultReceiver.class).setAction(ACTION_DONE);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(ctx, id, done, flags).getIntentSender());
        }
    }

    /** Shows the system confirmation if Android asks for one (not needed when we are device owner). */
    public static class InstallResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Log.w(TAG, "install failed: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            }
        }
    }
}
