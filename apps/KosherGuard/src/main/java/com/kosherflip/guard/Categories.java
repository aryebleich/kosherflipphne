package com.kosherflip.guard;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Website categories from assets/categories.json. */
public final class Categories {
    /** Display names, in menu order. Keys match categories.json. */
    public static final String[][] NAMES = {
            {"social", "Social media"},
            {"video", "Video sites"},
            {"streaming", "Movies, TV & music streaming"},
            {"news", "News & gossip"},
            {"games", "Games"},
            {"dating", "Dating"},
            {"gambling", "Gambling"},
            {"ai_chat", "Unfiltered AI chat sites"},
            {"search_unfiltered", "Unfiltered search engines"},
            {"proxies", "Proxies & VPNs"},
            {"messaging_web", "Web chat & messengers"},
            {"shopping", "Shopping & payments"},
            {"jewish", "Torah & Jewish sites"},
            {"essentials", "Banks, travel, government, utilities"},
    };

    private static volatile Map<String, Set<String>> cache;

    private Categories() {}

    public static Map<String, Set<String>> load(Context ctx) {
        Map<String, Set<String>> c = cache;
        if (c != null) return c;
        Map<String, Set<String>> out = new LinkedHashMap<>();
        try (InputStream in = ctx.getAssets().open("categories.json")) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            JSONObject o = new JSONObject(new String(bo.toByteArray(), StandardCharsets.UTF_8));
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray a = o.optJSONArray(k);
                if (a == null) continue;
                Set<String> set = new HashSet<>();
                for (int i = 0; i < a.length(); i++) set.add(a.optString(i).toLowerCase(Locale.ROOT));
                out.put(k, Collections.unmodifiableSet(set));
            }
        } catch (Exception ignored) {
        }
        cache = out;
        return out;
    }

    /** Union of the domains of the given categories. */
    public static Set<String> domains(Map<String, Set<String>> all, Set<String> keys) {
        Set<String> out = new HashSet<>();
        for (String k : keys) {
            Set<String> d = all.get(k);
            if (d != null) out.addAll(d);
        }
        return out;
    }

    public static String label(String key) {
        for (String[] n : NAMES) if (n[0].equals(key)) return n[1];
        return key;
    }
}
