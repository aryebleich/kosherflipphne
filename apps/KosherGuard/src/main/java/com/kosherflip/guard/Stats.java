package com.kosherflip.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Learns how the phone uses the internet: how often each site is used or
 * blocked. Powers "Insights" (most used / most blocked) and "Suggestions"
 * (blocked sites the user keeps trying, which the admin may want to allow).
 * Counts are per site (e.g. "amazon.com"), stored only on the phone.
 */
public final class Stats {
    private static final String PREFS = "stats";
    private static final Map<String, int[]> counts = new ConcurrentHashMap<>(); // site -> {allowed, blocked}
    private static volatile long lastFlush;
    private static volatile boolean loaded;

    private Stats() {}

    static void record(Context ctx, String name, boolean blocked) {
        if (name == null) return;
        ensureLoaded(ctx);
        String site = site(name);
        int[] c = counts.computeIfAbsent(site, k -> new int[2]);
        synchronized (c) {
            c[blocked ? 1 : 0]++;
        }
        long now = System.currentTimeMillis();
        if (now - lastFlush > 120_000) flush(ctx);
    }

    static void recordBlockedScreen(Context ctx, String pkg) {
        record(ctx, "app:" + pkg, true);
    }

    /** "img.cdn.amazon.com" -> "amazon.com", "news.bbc.co.uk" -> "bbc.co.uk". */
    static String site(String name) {
        String d = DnsFilter.normalize(name);
        if (d.startsWith("app:")) return d;
        String[] p = d.split("\\.");
        if (p.length <= 2) return d;
        String tld = p[p.length - 1], sld = p[p.length - 2];
        boolean twoPart = tld.length() == 2 && (sld.equals("co") || sld.equals("com") || sld.equals("org")
                || sld.equals("net") || sld.equals("ac") || sld.equals("gov"));
        int keep = twoPart ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = p.length - keep; i < p.length; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(p[i]);
        }
        return sb.toString();
    }

    public static List<String[]> top(Context ctx, boolean blocked, int limit) {
        ensureLoaded(ctx);
        List<Map.Entry<String, int[]>> list = new ArrayList<>(counts.entrySet());
        int idx = blocked ? 1 : 0;
        list.removeIf(e -> e.getValue()[idx] == 0);
        list.sort((a, b) -> b.getValue()[idx] - a.getValue()[idx]);
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, list.size()); i++) {
            out.add(new String[] {list.get(i).getKey(), String.valueOf(list.get(i).getValue()[idx])});
        }
        return out;
    }

    /** Blocked sites tried at least 5 times: candidates for "allow". */
    public static List<String[]> suggestions(Context ctx) {
        List<String[]> out = new ArrayList<>();
        for (String[] s : top(ctx, true, 50)) {
            if (!s[0].startsWith("app:") && Integer.parseInt(s[1]) >= 5) out.add(s);
            if (out.size() >= 15) break;
        }
        return out;
    }

    public static void clear(Context ctx) {
        counts.clear();
        prefs(ctx).edit().clear().apply();
    }

    static synchronized void flush(Context ctx) {
        lastFlush = System.currentTimeMillis();
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                o.put(e.getKey(), e.getValue()[0] + "," + e.getValue()[1]);
            }
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString("counts", o.toString()).apply();
    }

    private static synchronized void ensureLoaded(Context ctx) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject o = new JSONObject(prefs(ctx).getString("counts", "{}"));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                String[] v = o.getString(k).split(",");
                counts.put(k, new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1])});
            }
        } catch (Exception ignored) {
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
