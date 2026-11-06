package com.kosherflip.guard;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * One-tap presets, from strictest to most open. The admin can fine-tune
 * every option afterwards.
 */
public final class Profiles {
    /** All apps and browsers allowed; the content in them is filtered (default). */
    public static final String FILTERED = "filtered";
    /** Talk, text and approved apps only. No browser; only trusted sites resolve. */
    public static final String KOSHER = "kosher";
    /** Filtered web like a heimish filter: browse, shop, download; images only on trusted sites. */
    public static final String HEIMISH = "heimish";
    /** Open web with protection: adult/dating/gambling/proxies blocked, pictures shown, screen filter on. */
    public static final String OPEN = "open";

    public static final String[][] NAMES = {
            {FILTERED, "Filtered - all apps & browsers, content filtered (default)"},
            {KOSHER, "Kosher - calls, texts & approved apps only"},
            {HEIMISH, "Heimish - filtered browsing, shopping & downloads"},
            {OPEN, "Open + protected - most sites, smart filtering"},
    };

    /** Blocked in every profile: harmful, or a way around the filter. */
    private static final String[] ALWAYS = {"dating", "gambling", "proxies"};
    private static final String[] HARMFUL = {"dating", "gambling", "proxies", "search_unfiltered", "ai_chat"};
    private static final String[] DISTRACTING = {"social", "video", "streaming", "news", "games", "messaging_web"};
    private static final String[] TRUSTED = {"shopping", "jewish", "essentials"};

    private Profiles() {}

    public static void apply(Policy p, String profile) {
        p.profile = profile;
        applyCategoryDefaults(p, profile);
        p.screenFilter = true;
        p.allowAllApps = FILTERED.equals(profile);
        p.allowAppInstalls = FILTERED.equals(profile);
        switch (profile) {
            case FILTERED:
                // SafeSearch and YouTube Restricted Mode come from the family DNS.
                p.filterMode = Policy.MODE_BLOCKLIST;
                p.browserEnabled = true;
                p.imageMode = "all";
                p.blockVideo = false;
                break;
            case KOSHER:
                p.filterMode = Policy.MODE_ALLOWLIST;
                p.browserEnabled = false;
                p.imageMode = "none";
                p.blockVideo = true;
                break;
            case OPEN:
                p.filterMode = Policy.MODE_BLOCKLIST;
                p.browserEnabled = true;
                p.imageMode = "all";
                p.blockVideo = false;
                break;
            case HEIMISH:
            default:
                p.filterMode = Policy.MODE_BLOCKLIST;
                p.browserEnabled = true;
                p.imageMode = "trusted";
                p.blockVideo = true;
                break;
        }
    }

    static void applyCategoryDefaults(Policy p, String profile) {
        p.blockedCategories = new LinkedHashSet<>(Arrays.asList(FILTERED.equals(profile) ? ALWAYS : HARMFUL));
        if (!OPEN.equals(profile) && !FILTERED.equals(profile)) p.blockedCategories.addAll(Arrays.asList(DISTRACTING));
        p.trustedCategories = new LinkedHashSet<>(Arrays.asList(TRUSTED));
    }

    public static String label(String key) {
        for (String[] n : NAMES) if (n[0].equals(key)) return n[1];
        return key;
    }
}
