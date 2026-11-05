package com.kosherflip.guard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The filter policy. Defaults ship in assets/default_policy.json (and, in the
 * ROM build, may be overridden by /product/etc/kosherflip/policy.json). The
 * admin's edits are stored in private SharedPreferences.
 */
public final class Policy {
    public static final String MODE_BLOCKLIST = "blocklist";
    public static final String MODE_ALLOWLIST = "allowlist";

    private static final String PREFS = "policy";
    private static final String KEY_JSON = "json";
    private static final String ROM_POLICY = "/product/etc/kosherflip/policy.json";

    public String filterMode = MODE_BLOCKLIST;
    public List<String> upstreamDns = new ArrayList<>();
    public boolean lockUsbDebugging;
    public boolean allowAppInstalls;
    public Set<String> allowedApps = new LinkedHashSet<>();
    public Set<String> allowedDomains = new LinkedHashSet<>();
    public Set<String> blockedDomains = new LinkedHashSet<>();
    /** Claude API key handed to the KosherAI app through managed configuration. */
    public String aiApiKey = "";
    public boolean aiEnabled = true;

    public static Policy load(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        String json = sp.getString(KEY_JSON, null);
        if (json == null) json = readDefault(ctx);
        try {
            return fromJson(new JSONObject(json));
        } catch (JSONException e) {
            try {
                return fromJson(new JSONObject(readAsset(ctx)));
            } catch (JSONException e2) {
                return new Policy();
            }
        }
    }

    public void save(Context ctx) {
        prefs(ctx).edit().putString(KEY_JSON, toJson().toString()).apply();
    }

    public static void reset(Context ctx) {
        prefs(ctx).edit().remove(KEY_JSON).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        // Device-protected storage so the filter works before first unlock.
        Context dp = ctx.createDeviceProtectedStorageContext();
        return dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String readDefault(Context ctx) {
        java.io.File rom = new java.io.File(ROM_POLICY);
        if (rom.canRead()) {
            try (InputStream in = new java.io.FileInputStream(rom)) {
                return readAll(in);
            } catch (IOException ignored) {
            }
        }
        return readAsset(ctx);
    }

    private static String readAsset(Context ctx) {
        try (InputStream in = ctx.getAssets().open("default_policy.json")) {
            return readAll(in);
        } catch (IOException e) {
            return "{}";
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    static Policy fromJson(JSONObject o) {
        Policy p = new Policy();
        p.filterMode = MODE_ALLOWLIST.equals(o.optString("filterMode")) ? MODE_ALLOWLIST : MODE_BLOCKLIST;
        p.lockUsbDebugging = o.optBoolean("lockUsbDebugging", false);
        p.allowAppInstalls = o.optBoolean("allowAppInstalls", false);
        p.aiApiKey = o.optString("aiApiKey", "");
        p.aiEnabled = o.optBoolean("aiEnabled", true);
        readList(o.optJSONArray("upstreamDns"), p.upstreamDns);
        readList(o.optJSONArray("allowedApps"), p.allowedApps);
        readList(o.optJSONArray("allowedDomains"), p.allowedDomains);
        readList(o.optJSONArray("blockedDomains"), p.blockedDomains);
        if (p.upstreamDns.isEmpty()) {
            // CleanBrowsing "Family" filter: blocks adult content, proxies/VPNs and forces SafeSearch.
            p.upstreamDns.add("185.228.168.168");
            p.upstreamDns.add("185.228.169.168");
        }
        p.allowedApps.add(BuildConfigShim.GUARD);
        return p;
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("version", 1);
            o.put("filterMode", filterMode);
            o.put("lockUsbDebugging", lockUsbDebugging);
            o.put("allowAppInstalls", allowAppInstalls);
            o.put("aiApiKey", aiApiKey);
            o.put("aiEnabled", aiEnabled);
            o.put("upstreamDns", new JSONArray(upstreamDns));
            o.put("allowedApps", new JSONArray(allowedApps));
            o.put("allowedDomains", new JSONArray(allowedDomains));
            o.put("blockedDomains", new JSONArray(blockedDomains));
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return o;
    }

    private static <T extends java.util.Collection<String>> void readList(JSONArray a, T into) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim().toLowerCase(java.util.Locale.ROOT);
            if (!s.isEmpty()) into.add(s);
        }
    }

    /** Parses one entry per line (used by the admin text editors). */
    public static Set<String> parseLines(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String line : text.split("[\\n,\\s]+")) {
            String s = line.trim().toLowerCase(java.util.Locale.ROOT);
            if (!s.isEmpty() && !s.startsWith("#")) out.add(s);
        }
        return out;
    }

    public static String joinLines(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String s : set) sb.append(s).append('\n');
        return sb.toString();
    }
}
