package com.kosherflip.guard;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * After the admin enters the PIN (or while first setup runs), the tamper
 * protection pauses for a few minutes so the admin can use Settings freely.
 */
final class Maintenance {
    private Maintenance() {}

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences("maintenance", Context.MODE_PRIVATE);
    }

    static void start(Context c, int minutes) {
        prefs(c).edit().putLong("until", System.currentTimeMillis() + minutes * 60_000L).apply();
    }

    static void end(Context c) {
        prefs(c).edit().putLong("until", 0).apply();
    }

    static boolean active(Context c) {
        return System.currentTimeMillis() < prefs(c).getLong("until", 0);
    }
}
