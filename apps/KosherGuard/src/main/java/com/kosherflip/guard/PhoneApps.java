package com.kosherflip.guard;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telecom.TelecomManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The apps that make calls on THIS phone (they differ per maker: CAT, Pom, Samsung...).
 * They are never hidden, blocked or covered by the filter, so calls always work.
 */
final class PhoneApps {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
            "com.android.phone", "com.android.server.telecom", "com.android.contacts",
            "com.google.android.contacts", "com.android.emergency", "com.android.stk"));

    private static volatile Set<String> calls = KNOWN;
    private static volatile Set<String> sms = Collections.emptySet();

    private PhoneApps() {}

    /** Asks Android which apps are the dialer, in-call screen and messaging app. */
    static void refresh(Context ctx) {
        Set<String> c = new HashSet<>(KNOWN);
        Set<String> m = new HashSet<>();
        try {
            TelecomManager tm = ctx.getSystemService(TelecomManager.class);
            if (tm != null) {
                add(c, tm.getDefaultDialerPackage());
                if (Build.VERSION.SDK_INT >= 29) add(c, tm.getSystemDialerPackage());
            }
        } catch (RuntimeException ignored) {
        }
        PackageManager pm = ctx.getPackageManager();
        for (Intent i : new Intent[] {new Intent(Intent.ACTION_DIAL), new Intent(Intent.ACTION_DIAL, Uri.parse("tel:1")),
                new Intent(Intent.ACTION_CALL_BUTTON)}) {
            for (ResolveInfo ri : pm.queryIntentActivities(i, PackageManager.MATCH_SYSTEM_ONLY)) {
                c.add(ri.activityInfo.packageName);
            }
        }
        try {
            add(m, Telephony.Sms.getDefaultSmsPackage(ctx));
        } catch (RuntimeException ignored) {
        }
        calls = c;
        sms = m;
    }

    private static void add(Set<String> s, String p) {
        if (p != null && !p.isEmpty()) s.add(p);
    }

    static boolean isCallApp(String pkg) {
        return calls.contains(pkg);
    }

    static boolean isSmsApp(String pkg) {
        return sms.contains(pkg);
    }

    static Set<String> callApps() {
        return calls;
    }
}
