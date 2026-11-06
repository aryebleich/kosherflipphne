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

    /** Visible during Shabbos mode. */
    private static final Set<String> SHABBOS_APPS = new HashSet<>();
    /** Hidden in talk-only mode. */
    private static final Set<String> MESSAGING = new HashSet<>();
    static {
        Collections.addAll(SHABBOS_APPS, BuildConfigShim.GUARD, BuildConfigShim.LAUNCHER,
                "com.android.dialer", "com.google.android.dialer", "com.android.contacts",
                "com.google.android.contacts", "com.android.deskclock", "com.google.android.deskclock",
                "com.android.settings", "com.android.stk");
        Collections.addAll(MESSAGING, "com.android.messaging", "com.android.mms",
                "com.google.android.apps.messaging", "com.whatsapp", "com.whatsapp.w4b");
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
            // Standard protection (installed on the phone alone, no QR code or computer):
            // the screen guard blocks apps and tampering, the VPN filters websites.
            report.append("Standard protection (phone-only install).\n");
            report.append(Standard.status(ctx)).append('\n');
            if (dpm != null) {
                report.append(ensureScreenGuard(ctx, dpm, admin, p.screenFilter)).append('\n');
                report.append(ensureKeyboard(ctx, p.t9Keyboard)).append('\n');
            }
            startVpn(ctx);
            PolicyJob.schedule(ctx);
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
        set(dpm, admin, UserManager.DISALLOW_CONFIG_TETHERING, p.blockTethering);
        set(dpm, admin, UserManager.DISALLOW_USB_FILE_TRANSFER, p.blockUsbTransfer);
        set(dpm, admin, UserManager.DISALLOW_BLUETOOTH_SHARING, p.blockUsbTransfer);
        set(dpm, admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, p.blockUsbTransfer);
        try {
            dpm.setCameraDisabled(admin, !p.cameraEnabled);
        } catch (Exception e) {
            Log.w(TAG, "camera", e);
        }
        grantOwnPermissions(ctx, dpm, admin);
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
        boolean shabbos = isShabbosNow(p);
        int hidden = 0, shown = 0;
        for (String pkg : launcherPackages(ctx)) {
            boolean allow = isAppAllowed(p, shabbos, pkg);
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
        if (shabbos) report.append("Shabbos mode is ON now.\n");
        if (p.talkOnly) report.append("Talk-only mode: texts are auto-answered.\n");

        // 3. Settings for KosherAI and KosherBrowser through managed configuration
        //    (also served to them by ConfigProvider, which works without device owner).
        Bundle ai = aiConfig(p);
        Bundle br = browserConfig(ctx, p);
        try {
            dpm.setApplicationRestrictions(admin, BuildConfigShim.AI, ai);
            dpm.setApplicationRestrictions(admin, BuildConfigShim.BROWSER, br);
        } catch (Exception e) {
            Log.w(TAG, "managed config", e);
        }

        // 3b. Screen filter (accessibility service) and the T9 keyboard.
        report.append(ensureScreenGuard(ctx, dpm, admin, p.screenFilter)).append('\n');
        report.append(ensureKeyboard(ctx, p.t9Keyboard)).append('\n');

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
        PolicyJob.schedule(ctx);
        return report.toString();
    }

    /** Is this launcher app allowed right now? Used to hide apps (owner) or block them (standard). */
    static boolean isAppAllowed(Policy p, boolean shabbos, String pkg) {
        if (BuildConfigShim.GUARD.equals(pkg) || BuildConfigShim.LAUNCHER.equals(pkg)) return true;
        if (shabbos) return SHABBOS_APPS.contains(pkg);
        if (BuildConfigShim.AI.equals(pkg)) return p.aiEnabled;
        if (BuildConfigShim.BROWSER.equals(pkg)) return p.browserEnabled;
        if (p.talkOnly && MESSAGING.contains(pkg)) return false;
        return CORE.contains(pkg) || p.allowedApps.contains(pkg);
    }

    static Bundle aiConfig(Policy p) {
        Bundle ai = new Bundle();
        ai.putBoolean("managed", true);
        ai.putString("provider", p.aiProvider);
        ai.putString("model", p.aiModel);
        ai.putString("base_url", p.aiBaseUrl);
        ai.putString("api_key", p.aiApiKey == null ? "" : p.aiApiKey);
        ai.putString("custom_words", joinLines(p.customWords));
        return ai;
    }

    static Bundle browserConfig(Context ctx, Policy p) {
        Bundle br = new Bundle();
        br.putBoolean("managed", true);
        br.putString("image_mode", p.imageMode);
        br.putBoolean("block_video", p.blockVideo);
        br.putBoolean("allow_apk", p.allowAppInstalls);
        br.putString("download_types", joinLines(p.allowedDownloadTypes));
        br.putString("trusted_sites", joinLines(trustedSites(ctx, p)));
        br.putString("custom_words", joinLines(p.customWords));
        return br;
    }

    static boolean isShabbosNow(Policy p) {
        if ("on".equals(p.shabbosMode)) return true;
        if (!"auto".equals(p.shabbosMode)) return false;
        return JewishCalendar.isNow(p, java.time.ZonedDateTime.now());
    }

    /** Sites where the browser may show pictures in "trusted" image mode. */
    static Set<String> trustedSites(Context ctx, Policy p) {
        Set<String> out = new java.util.TreeSet<>(Categories.domains(Categories.load(ctx), p.trustedCategories));
        for (String d : p.allowedDomains) if (!d.startsWith("=")) out.add(d);
        return out;
    }

    private static String joinLines(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(s).append('\n');
        return sb.toString();
    }

    private static void grantOwnPermissions(Context ctx, DevicePolicyManager dpm, ComponentName admin) {
        String[] guard = {"android.permission.RECEIVE_SMS", "android.permission.SEND_SMS",
                "android.permission.POST_NOTIFICATIONS", "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION"};
        for (String perm : guard) grant(dpm, admin, ctx.getPackageName(), perm);
        grant(dpm, admin, BuildConfigShim.LAUNCHER, "android.permission.ACCESS_COARSE_LOCATION");
        grant(dpm, admin, BuildConfigShim.BROWSER, "android.permission.POST_NOTIFICATIONS");
    }

    private static void grant(DevicePolicyManager dpm, ComponentName admin, String pkg, String perm) {
        try {
            dpm.setPermissionGrantState(admin, pkg, perm, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
        } catch (Exception ignored) {
            // App not installed, or permission not requested by it.
        }
    }

    /**
     * Turns the screen filter on (or off). Needs WRITE_SECURE_SETTINGS: granted
     * automatically in the ROM build, or once by adb on the apps-only install.
     * Only KosherGuard (and built-in system services) may be accessibility
     * services, so no other app can read or control the screen.
     */
    static String ensureScreenGuard(Context ctx, DevicePolicyManager dpm, ComponentName admin, boolean on) {
        String me = new ComponentName(ctx, ScreenGuardService.class).flattenToString();
        try {
            dpm.setPermittedAccessibilityServices(admin, java.util.Collections.singletonList(ctx.getPackageName()));
        } catch (Exception ignored) {
        }
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            return on ? "Screen filter: needs one-time setup (see docs) or turn it on in Accessibility settings."
                    : "Screen filter: off.";
        }
        android.content.ContentResolver cr = ctx.getContentResolver();
        String cur = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        Set<String> parts = new java.util.LinkedHashSet<>();
        if (cur != null) for (String s : cur.split(":")) if (!s.isEmpty()) parts.add(s);
        boolean changed = on ? parts.add(me) : parts.remove(me);
        if (changed) {
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    String.join(":", parts));
        }
        if (on) android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        return on ? "Screen filter: on (every app)." : "Screen filter: off.";
    }

    /**
     * Enables KosherKeys (English / Hebrew / Yiddish T9) and, on phones with a
     * number keypad, makes it the default keyboard.
     */
    static String ensureKeyboard(Context ctx, boolean makeDefault) {
        if (!isInstalled(ctx, BuildConfigShim.KEYS)) return "T9 keyboard: not installed.";
        if (ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
            return "T9 keyboard: choose it in Settings > Keyboard.";
        }
        String ime = BuildConfigShim.KEYS + "/.T9Service";
        android.content.ContentResolver cr = ctx.getContentResolver();
        String cur = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_INPUT_METHODS);
        if (cur == null || !cur.contains(ime)) {
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_INPUT_METHODS,
                    cur == null || cur.isEmpty() ? ime : cur + ":" + ime);
        }
        boolean keypad = ctx.getResources().getConfiguration().keyboard
                == android.content.res.Configuration.KEYBOARD_12KEY;
        if (makeDefault && keypad) {
            android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD, ime);
            return "T9 keyboard: default.";
        }
        return "T9 keyboard: available.";
    }

    /** Removes all restrictions and gives up device ownership (admin "uninstall"). */
    public static void release(Context ctx) {
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        ComponentName admin = admin(ctx);
        if (dpm == null) return;
        if (!dpm.isDeviceOwnerApp(ctx.getPackageName())) {
            // Standard protection: drop removal protection and pause the guard so the
            // apps can be switched off and uninstalled in Settings.
            if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin);
            Standard.setSetupDone(ctx, false);
            Maintenance.start(ctx, 60);
            return;
        }
        for (String pkg : launcherPackagesIncludingHidden(ctx)) {
            try { dpm.setApplicationHidden(admin, pkg, false); } catch (Exception ignored) { }
        }
        String[] all = {UserManager.DISALLOW_FACTORY_RESET, UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_REMOVE_USER,
                UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserManager.DISALLOW_APPS_CONTROL, UserManager.DISALLOW_UNINSTALL_APPS,
                UserManager.DISALLOW_INSTALL_APPS, UserManager.DISALLOW_DEBUGGING_FEATURES,
                UserManager.DISALLOW_CONFIG_TETHERING, UserManager.DISALLOW_USB_FILE_TRANSFER,
                UserManager.DISALLOW_BLUETOOTH_SHARING, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA};
        for (String r : all) set(dpm, admin, r, false);
        if (Build.VERSION.SDK_INT >= 29) {
            set(dpm, admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, false);
            set(dpm, admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false);
        }
        try { dpm.setAlwaysOnVpnPackage(admin, null, false); } catch (Exception ignored) { }
        try { dpm.setCameraDisabled(admin, false); } catch (Exception ignored) { }
        try { dpm.setPermittedAccessibilityServices(admin, null); } catch (Exception ignored) { }
        ensureScreenGuard(ctx, dpm, admin, false);
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
