package com.kosherflip.guard;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Admin PIN, stored as a salted, iterated SHA-256 hash. */
final class Pin {
    private static final String PREFS = "pin";

    private Pin() {}

    private static SharedPreferences prefs(Context c) {
        return c.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isSet(Context c) {
        return prefs(c).contains("hash");
    }

    static void set(Context c, String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String s = hex(salt);
        prefs(c).edit().putString("salt", s).putString("hash", hash(s, pin)).putInt("fails", 0).apply();
    }

    static boolean check(Context c, String pin) {
        SharedPreferences sp = prefs(c);
        long lockedUntil = sp.getLong("lockedUntil", 0);
        if (System.currentTimeMillis() < lockedUntil) return false;
        boolean ok = hash(sp.getString("salt", ""), pin).equals(sp.getString("hash", ""));
        int fails = ok ? 0 : sp.getInt("fails", 0) + 1;
        SharedPreferences.Editor e = sp.edit().putInt("fails", fails);
        if (fails >= 5) e.putLong("lockedUntil", System.currentTimeMillis() + 5 * 60_000L).putInt("fails", 0);
        e.apply();
        return ok;
    }

    static long lockedSeconds(Context c) {
        long left = prefs(c).getLong("lockedUntil", 0) - System.currentTimeMillis();
        return left > 0 ? left / 1000 : 0;
    }

    private static String hash(String salt, String pin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = (salt + ":" + pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20000; i++) h = md.digest(h);
            return hex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
