package com.kosherflip.guard;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which system screens the screen guard blocks in standard protection (no
 * device owner): anything that could switch off or remove KosherFlip.
 * Matches the text on screen, in English and Hebrew.
 */
final class TamperRules {
    private TamperRules() {}

    static final Set<String> SYSTEM_SCREENS = new HashSet<>(Arrays.asList(
            "com.android.settings", "com.google.android.settings", "com.samsung.android.settings",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
            "com.android.systemui", "com.android.vpndialogs", "com.android.settings.intelligence",
            "com.android.tv.settings", "com.coloros.settings", "com.miui.securitycenter"));

    static final Set<String> INSTALLERS = new HashSet<>(Arrays.asList(
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller", "com.miui.packageinstaller"));

    /** Names of our apps as they appear in Settings. */
    private static final String[] OUR_NAMES = {
            "kosherguard", "kosherflip", "flip home", "ask ai", "kosherkeys", "kosher browser"};

    /** Actions that would weaken KosherFlip when shown next to one of our names. */
    private static final String[] ACTIONS = {
            "uninstall", "force stop", "disable", "deactivate", "turn off", "use ", "clear storage",
            "clear data", "always-on", "forget", "remove", "stop", "revoke", "don't allow", "not allowed",
            "הסר", "כבה", "השבת", "עצירה"}; // הסר כבה השבת עצירה

    /** Screens that are blocked on their own. */
    private static final String[] ALWAYS = {
            "erase all data", "factory reset", "factory data reset", "reset phone", "reset options",
            "usb debugging", "wireless debugging", "reboot to safe mode", "restart in safe mode",
            "איפוס להגדרות יצרן", // איפוס להגדרות יצרן
            "ניפוי באגים"};                                      // ניפוי באגים

    static boolean isProtectedScreen(String screenText) {
        String t = screenText.toLowerCase(Locale.ROOT);
        // Another VPN app would replace the website filter.
        if (t.contains("wants to set up a vpn connection") && !t.contains("kosherguard")) return true;
        for (String a : ALWAYS) if (t.contains(a)) return true;
        boolean ours = false;
        for (String n : OUR_NAMES) if (t.contains(n)) { ours = true; break; }
        if (!ours) return false;
        for (String a : ACTIONS) if (t.contains(a)) return true;
        return false;
    }
}
