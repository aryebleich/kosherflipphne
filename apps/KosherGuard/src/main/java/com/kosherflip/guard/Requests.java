package com.kosherflip.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * "Request access" queue, like the request forms of kosher filter providers:
 * the user asks for a blocked site or hidden app from the block page, the app
 * list or the browser; the admin approves or declines it in the console.
 */
public final class Requests {
    public static final String TYPE_SITE = "site";
    public static final String TYPE_APP = "app";
    private static final String PREFS = "requests";

    private Requests() {}

    public static synchronized void add(Context ctx, String type, String value, String note) {
        if (value == null || value.trim().isEmpty()) return;
        List<JSONObject> all = list(ctx);
        for (JSONObject o : all) {
            if (type.equals(o.optString("type")) && value.equals(o.optString("value"))) return; // already pending
        }
        if (all.size() >= 100) all.remove(0);
        try {
            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("value", value.trim().toLowerCase(java.util.Locale.ROOT));
            o.put("note", note == null ? "" : note);
            o.put("time", System.currentTimeMillis());
            all.add(o);
        } catch (Exception ignored) {
        }
        save(ctx, all);
    }

    public static synchronized List<JSONObject> list(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(ctx).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getJSONObject(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Approves (adds to the policy) or declines a request, then removes it. */
    public static synchronized void resolve(Context ctx, int index, boolean approve) {
        List<JSONObject> all = list(ctx);
        if (index < 0 || index >= all.size()) return;
        JSONObject r = all.remove(index);
        if (approve) {
            Policy p = Policy.load(ctx);
            String v = r.optString("value");
            if (TYPE_APP.equals(r.optString("type"))) {
                String pkg = findPackage(ctx, v);
                if (pkg != null) p.allowedApps.add(pkg);
            }
            else {
                p.allowedDomains.add(v);
                p.blockedDomains.remove(v);
            }
            p.save(ctx);
        }
        save(ctx, all);
    }

    /** The request may name a package or an app's name ("Waze"). */
    static String findPackage(Context ctx, String value) {
        if (PolicyEnforcer.isInstalled(ctx, value)) return value;
        for (String pkg : PolicyEnforcer.launcherPackages(ctx)) {
            if (PolicyEnforcer.label(ctx, pkg).toString().equalsIgnoreCase(value.trim())) return pkg;
        }
        return null;
    }

    private static void save(Context ctx, List<JSONObject> all) {
        prefs(ctx).edit().putString("list", new JSONArray(all).toString()).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
