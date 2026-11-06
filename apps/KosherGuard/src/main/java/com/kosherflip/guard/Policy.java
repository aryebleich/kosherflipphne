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
    /** Defaults built into the ROM (vendor tree) or the KosherFlip GSI image. */
    private static final String[] ROM_POLICIES = {
            "/product/etc/kosherflip/policy.json", "/system/etc/kosherflip/policy.json"};

    public String filterMode = MODE_BLOCKLIST;
    public List<String> upstreamDns = new ArrayList<>();
    public boolean lockUsbDebugging;
    public boolean allowAppInstalls;
    public Set<String> allowedApps = new LinkedHashSet<>();
    public Set<String> allowedDomains = new LinkedHashSet<>();
    public Set<String> blockedDomains = new LinkedHashSet<>();
    public String profile = Profiles.FILTERED;
    /** Every app is allowed (content is filtered); only blockedApps are blocked. */
    public boolean allowAllApps = true;
    public Set<String> blockedApps = new LinkedHashSet<>();
    public Set<String> blockedCategories = new LinkedHashSet<>();
    public Set<String> trustedCategories = new LinkedHashSet<>();
    /** none | trusted | blur | all - how the browser shows pictures. */
    public String imageMode = "trusted";
    public boolean blockVideo = true;
    public boolean browserEnabled = true;
    /** Scan the text of every screen in every app. */
    public boolean screenFilter = true;
    public Set<String> customWords = new LinkedHashSet<>();
    /** Talk-only: messaging apps hidden, texts auto-answered, verification codes still shown. */
    public boolean talkOnly;
    public boolean cameraEnabled = true;
    public boolean blockTethering = true;
    public boolean blockUsbTransfer = true;
    /** off | auto | on */
    public String shabbosMode = "off";
    public double latitude, longitude;
    public boolean inIsrael;
    public int candleMinutes = 18;
    public int endMinutes = 50;
    public Set<String> allowedDownloadTypes = new LinkedHashSet<>();
    /** Make KosherKeys the default keyboard on keypad phones. */
    public boolean t9Keyboard = true;
    /** Where "Install / update apps" downloads from (blank = this project's GitHub releases). */
    public String updateUrl = "";

    /** AI assistant: provider gemini (free key) | openai (any OpenAI-compatible, e.g. free Groq/OpenRouter models) | claude. */
    public String aiProvider = "gemini";
    public String aiModel = "";
    public String aiBaseUrl = "";
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
        for (String path : ROM_POLICIES) {
            java.io.File rom = new java.io.File(path);
            if (!rom.canRead()) continue;
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
        p.aiProvider = o.optString("aiProvider", "gemini");
        p.aiModel = o.optString("aiModel", "");
        p.aiBaseUrl = o.optString("aiBaseUrl", "");
        p.profile = o.optString("profile", Profiles.FILTERED);
        p.allowAllApps = o.optBoolean("allowAllApps", false);
        readList(o.optJSONArray("blockedApps"), p.blockedApps);
        p.imageMode = o.optString("imageMode", "trusted");
        p.blockVideo = o.optBoolean("blockVideo", true);
        p.browserEnabled = o.optBoolean("browserEnabled", true);
        p.screenFilter = o.optBoolean("screenFilter", true);
        p.talkOnly = o.optBoolean("talkOnly", false);
        p.cameraEnabled = o.optBoolean("cameraEnabled", true);
        p.blockTethering = o.optBoolean("blockTethering", true);
        p.blockUsbTransfer = o.optBoolean("blockUsbTransfer", true);
        p.shabbosMode = o.optString("shabbosMode", "off");
        p.latitude = o.optDouble("latitude", 0);
        p.longitude = o.optDouble("longitude", 0);
        p.inIsrael = o.optBoolean("inIsrael", false);
        p.candleMinutes = o.optInt("candleMinutes", 18);
        p.endMinutes = o.optInt("endMinutes", 50);
        p.t9Keyboard = o.optBoolean("t9Keyboard", true);
        p.updateUrl = o.optString("updateUrl", "");
        readList(o.optJSONArray("blockedCategories"), p.blockedCategories);
        readList(o.optJSONArray("trustedCategories"), p.trustedCategories);
        readList(o.optJSONArray("customWords"), p.customWords);
        readList(o.optJSONArray("allowedDownloadTypes"), p.allowedDownloadTypes);
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
        // Carrier servers (VoLTE, Wi-Fi calling) must always resolve, on every carrier.
        p.allowedDomains.add("3gppnetwork.org");
        if (!o.has("blockedCategories")) {
            // Settings saved by KosherGuard 1.0: fill in the new options from the profile.
            Profiles.applyCategoryDefaults(p, p.profile);
        }
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
            o.put("aiProvider", aiProvider);
            o.put("aiModel", aiModel);
            o.put("aiBaseUrl", aiBaseUrl);
            o.put("profile", profile);
            o.put("allowAllApps", allowAllApps);
            o.put("blockedApps", new JSONArray(blockedApps));
            o.put("imageMode", imageMode);
            o.put("blockVideo", blockVideo);
            o.put("browserEnabled", browserEnabled);
            o.put("screenFilter", screenFilter);
            o.put("talkOnly", talkOnly);
            o.put("cameraEnabled", cameraEnabled);
            o.put("blockTethering", blockTethering);
            o.put("blockUsbTransfer", blockUsbTransfer);
            o.put("shabbosMode", shabbosMode);
            o.put("latitude", latitude);
            o.put("longitude", longitude);
            o.put("inIsrael", inIsrael);
            o.put("candleMinutes", candleMinutes);
            o.put("endMinutes", endMinutes);
            o.put("t9Keyboard", t9Keyboard);
            o.put("updateUrl", updateUrl);
            o.put("blockedCategories", new JSONArray(blockedCategories));
            o.put("trustedCategories", new JSONArray(trustedCategories));
            o.put("customWords", new JSONArray(customWords));
            o.put("allowedDownloadTypes", new JSONArray(allowedDownloadTypes));
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
