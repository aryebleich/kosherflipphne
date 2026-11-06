package com.kosherflip.browser;

import android.content.Context;
import android.content.RestrictionsManager;
import android.os.Bundle;

import com.kosherflip.common.WordFilter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Settings pushed by KosherGuard (managed configuration), with safe defaults. */
final class BrowserConfig {
    String imageMode = "trusted";
    boolean blockVideo = true;
    boolean allowApk;
    Set<String> downloadTypes = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "mp3", "m4a", "jpg", "jpeg", "png", "zip"));
    Set<String> trustedSites = new HashSet<>(Arrays.asList(
            "amazon.com", "media-amazon.com", "ssl-images-amazon.com", "walmart.com", "walmartimages.com",
            "target.com", "costco.com", "ebay.com", "ebayimg.com", "bhphotovideo.com", "chabad.org",
            "sefaria.org", "ou.org", "hebcal.com", "google.com", "gstatic.com", "googleapis.com"));
    WordFilter words = WordFilter.standard(null);

    static BrowserConfig load(Context ctx) {
        BrowserConfig c = new BrowserConfig();
        Bundle b = fromGuard(ctx);
        if (b == null) {
            RestrictionsManager rm = ctx.getSystemService(RestrictionsManager.class);
            b = rm == null ? null : rm.getApplicationRestrictions();
        }
        if (b == null || !b.getBoolean("managed", false)) return c;
        c.imageMode = b.getString("image_mode", c.imageMode);
        c.blockVideo = b.getBoolean("block_video", true);
        c.allowApk = b.getBoolean("allow_apk", false);
        Set<String> types = lines(b.getString("download_types"));
        if (!types.isEmpty()) c.downloadTypes = types;
        Set<String> trusted = lines(b.getString("trusted_sites"));
        if (!trusted.isEmpty()) c.trustedSites = trusted;
        c.words = WordFilter.standard(lines(b.getString("custom_words")));
        return c;
    }

    /** Settings from KosherGuard (works with and without device owner). */
    static Bundle fromGuard(Context ctx) {
        try {
            return ctx.getContentResolver().call(android.net.Uri.parse("content://com.kosherflip.guard.config"),
                    "config", null, null);
        } catch (RuntimeException e) {
            return null; // KosherGuard not installed
        }
    }

    boolean imagesAllowedOn(String host) {
        switch (imageMode) {
            case "all": case "blur": return true;
            case "none": return false;
            default: return UrlRules.hostMatches(host, trustedSites);
        }
    }

    private static Set<String> lines(String s) {
        Set<String> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String l : s.split("\n")) {
            String t = l.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
