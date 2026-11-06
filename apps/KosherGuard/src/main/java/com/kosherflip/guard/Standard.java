package com.kosherflip.guard;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.VpnService;
import android.provider.Settings;

/**
 * "Standard protection": KosherFlip installed on the phone alone (no QR code,
 * no computer). Without device-owner rights the filter is enforced by:
 *   - the DNS filter VPN (user allows it once; always-on set in VPN settings),
 *   - the screen guard (accessibility): blocks unapproved apps, the app
 *     installer, and every Settings screen that could switch off or remove
 *     KosherFlip, factory reset, safe mode or USB debugging - unless the admin
 *     entered the PIN in the last minutes,
 *   - device-admin activation, so KosherGuard can't be uninstalled directly.
 * This class checks the setup steps.
 */
final class Standard {
    private Standard() {}

    static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("standard", Context.MODE_PRIVATE);
    }

    static boolean setupDone(Context c) {
        return prefs(c).getBoolean("setupDone", false);
    }

    static void setSetupDone(Context c, boolean done) {
        prefs(c).edit().putBoolean("setupDone", done).apply();
    }

    static boolean vpnReady(Context c) {
        try {
            return VpnService.prepare(c) == null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean screenGuardOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(c, ScreenGuardService.class).flattenToString();
        String shortName = c.getPackageName() + "/.ScreenGuardService";
        return s != null && (s.contains(me) || s.contains(shortName));
    }

    static boolean adminActive(Context c) {
        DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isAdminActive(PolicyEnforcer.admin(c));
    }

    static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    static boolean appsInstalled(Context c) {
        for (String[] a : Companions.APPS) {
            if (!PolicyEnforcer.isInstalled(c, a[0])) return false;
        }
        return true;
    }

    static boolean homeIsOurs(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && BuildConfigShim.LAUNCHER.equals(ri.activityInfo.packageName);
    }

    static boolean hasKeypad(Context c) {
        return c.getResources().getConfiguration().keyboard == Configuration.KEYBOARD_12KEY;
    }

    static boolean keyboardIsOurs(Context c) {
        String ime = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(BuildConfigShim.KEYS + "/");
    }

    static boolean alwaysOnConfirmed(Context c) {
        return prefs(c).getBoolean("alwaysOn", false);
    }

    static void setAlwaysOnConfirmed(Context c) {
        prefs(c).edit().putBoolean("alwaysOn", true).apply();
    }

    static String status(Context c) {
        return (vpnReady(c) ? "✔" : "✘") + " website filter   "
                + (screenGuardOn(c) ? "✔" : "✘") + " screen filter   "
                + (adminActive(c) ? "✔" : "✘") + " removal protection";
    }
}
