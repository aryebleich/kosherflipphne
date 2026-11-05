package com.kosherflip.guard;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies the {@link Policy} to the device. Requires KosherGuard to be device owner. */
public final class PolicyEnforcer {
    private static final String TAG = "KosherGuard";

    /** Never hidden, whatever the policy says. */
    private static final Set<String> CORE = new HashSet<>();
    static {
        Collections.addAll(CORE,
                BuildConfigShim.GUARD, BuildConfigShim.LAUNCHER,
                "android", "com.android.systemui", "com.android.phone", "com.android.server.telecom",
                "com.android.providers.telephony", "com.android.settings", "com.android.emergency",
                "com.android.dialer", "com.google.android.dialer", "com.android.stk",
                "com.android.permissioncontroller", "com.google.android.permissioncontroller",
                "com.android.packageinstaller", "com.google.android.packageinstaller",
                "com.android.inputmethod.latin", "com.google.android.inputmethod.latin",
                "com.android.cellbroadcastreceiver", "com.google.android.cellbroadcastreceiver");
    }

    private PolicyEnforcer() {}

    public static ComponentName admin(Context ctx) {
        return new ComponentName(ctx, GuardAdminReceiver.class);
    }

    public static boolean isDeviceOwner(Context ctx) {
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        return dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName());
    }

    /** Applies everything. Safe to call often. Returns a human readable report. */
    public static String apply(Context ctx) {
        StringBuilder report = new StringBuilder();
        Policy p = Policy.load(ctx);
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        ComponentName admin = admin(ctx);
        if (dpm == null || !dpm.isDeviceOwnerApp(ctx.getPackageName())) {
            report.append("NOT device owner - filter cannot be enforced.\n");
            startVpn(ctx);
            return report.toString();
        }

        // 1. Device restrictions
        set(dpm, admin, UserManager.DISALLOW_FACTORY_RESET, true);
        set(dpm, admin, UserManager.DISALLOW_SAFE_BOOT, true);
        set(dpm, admin, UserManager.DISALLOW_ADD_USER, true);
        set(dpm, admin, UserManager.DISALLOW_REMOVE_USER, true);
        set(dpm, admin, UserManager.DISALLOW_CONFIG_VPN, true);
        set(dpm, admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, true);
        set(dpm, admin, UserManager.DISALLOW_APPS_CONTROL, true);
        set(dpm, admin, UserManager.DISALLOW_UNINSTALL_APPS, !p.allowAppInstalls);
        set(dpm, admin, UserManager.DISALLOW_INSTALL_APPS, !p.allowAppInstalls);
        set(dpm, admin, UserManager.DISALLOW_DEBUGGING_FEATURES, p.lockUsbDebugging);
        if (Build.VERSION.SDK_INT >= 29) {
            set(dpm, admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, true);
            set(dpm, admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, true);
            try {
                // Opportunistic mode = use the network's (our VPN's) resolver; no bypass via DoT.
                dpm.setGlobalPrivateDnsModeOpportunistic(admin);
            } catch (Exception e) {
                Log.w(TAG, "private dns", e);
            }
        }
        dpm.setUninstallBlocked(admin, ctx.getPackageName(), true);
        report.append("Restrictions applied.\n");

        // 2. Apps: hide every launcher app that is not allowed.
        int hidden = 0, shown = 0;
        for (String pkg : launcherPackages(ctx)) {
            boolean allow = BuildConfigShim.AI.equals(pkg)
                    ? p.aiEnabled
                    : CORE.contains(pkg) || p.allowedApps.contains(pkg);
            try {
                boolean wasHidden = dpm.isApplicationHidden(admin, pkg);
                if (allow == wasHidden) {
                    dpm.setApplicationHidden(admin, pkg, !allow);
                }
                if (allow) shown++; else hidden++;
            } catch (Exception e) {
                Log.w(TAG, "hide " + pkg, e);
            }
        }
        report.append("Apps allowed: ").append(shown).append(", hidden: ").append(hidden).append('\n');

        // 3. Hand the AI key to the KosherAI app through managed configuration.
        Bundle ai = new Bundle();
        ai.putString("api_key", p.aiApiKey == null ? "" : p.aiApiKey);
        ai.putBoolean("managed", true);
        try {
            dpm.setApplicationRestrictions(admin, BuildConfigShim.AI, ai);
        } catch (Exception e) {
            Log.w(TAG, "ai config", e);
        }

        // 4. Always-on DNS filter VPN.
        try {
            dpm.setAlwaysOnVpnPackage(admin, ctx.getPackageName(), false);
            report.append("DNS filter: always-on (").append(p.filterMode).append(").\n");
        } catch (Exception e) {
            report.append("DNS filter could not be set always-on: ").append(e.getMessage()).append('\n');
        }
        startVpn(ctx);

        // 5. Our launcher as the default home screen.
        if (isInstalled(ctx, BuildConfigShim.LAUNCHER)) {
            android.content.IntentFilter home = new android.content.IntentFilter(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addCategory(Intent.CATEGORY_DEFAULT);
            try {
                dpm.addPersistentPreferredActivity(admin, home,
                        new ComponentName(BuildConfigShim.LAUNCHER, BuildConfigShim.LAUNCHER + ".HomeActivity"));
            } catch (Exception e) {
                Log.w(TAG, "home", e);
            }
        }
        return report.toString();
    }

    /** Removes all restrictions and gives up device ownership (admin "uninstall"). */
    public static void release(Context ctx) {
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        ComponentName admin = admin(ctx);
        if (dpm == null || !dpm.isDeviceOwnerApp(ctx.getPackageName())) return;
        for (String pkg : launcherPackagesIncludingHidden(ctx)) {
            try { dpm.setApplicationHidden(admin, pkg, false); } catch (Exception ignored) { }
        }
        String[] all = {UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_REMOVE_USER,
                UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_APPS_CONTROL, UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_APPS, UserManager.DISALLOW_DEBUGGING_FEATURES};
        for (String r : all) set(dpm, admin, r, false);
        if (Build.VERSION.SDK_INT >= 29) {
            set(dpm, admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, false);
            set(dpm, admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
        }
        try { dpm.setAlwaysOnVpnPackage(admin, null, false); } catch (Exception ignored) { }
        dpm.clearPackagePersistentPreferredActivities(admin, BuildConfigShim.LAUNCHER);
        dpm.setUninstallBlocked(admin, ctx.getPackageName(), false);
        dpm.clearDeviceOwnerApp(ctx.getPackageName());
    }

    static void startVpn(Context ctx) {
        try {
            if (android.net.VpnService.prepare(ctx) == null) {
                ctx.startService(new Intent(ctx, FilterVpnService.class).setAction(FilterVpnService.ACTION_RELOAD));
            }
        } catch (Exception e) {
            // Background start not allowed right now; the always-on VPN is started by the system.
            Log.w(TAG, "vpn start", e);
        }
    }

    private static void set(DevicePolicyManager dpm, ComponentName admin, String key, boolean on) {
        try {
            if (on) dpm.addUserRestriction(admin, key);
            else dpm.clearUserRestriction(admin, key);
        } catch (Exception e) {
            Log.w(TAG, "restriction " + key, e);
        }
    }

    static boolean isInstalled(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Packages that have a launcher icon (visible or hidden by us). */
    public static List<String> launcherPackagesIncludingHidden(Context ctx) {
        return launcherPackages(ctx);
    }

    static List<String> launcherPackages(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Set<String> out = new java.util.TreeSet<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        int flags = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_UNINSTALLED_PACKAGES;
        for (ResolveInfo ri : pm.queryIntentActivities(i, flags)) {
            out.add(ri.activityInfo.packageName);
        }
        // Hidden apps no longer resolve; include every installed app that declares a launcher activity.
        for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)) {
            Intent li = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(ai.packageName);
            if (!pm.queryIntentActivities(li, flags).isEmpty()) out.add(ai.packageName);
        }
        return new ArrayList<>(out);
    }

    public static CharSequence label(Context ctx, String pkg) {
        PackageManager pm = ctx.getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }
}
