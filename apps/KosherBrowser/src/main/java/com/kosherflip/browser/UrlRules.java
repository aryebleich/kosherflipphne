package com.kosherflip.browser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** URL-level rules of the browser (pure Java, unit tested). */
final class UrlRules {
    private UrlRules() {}

    static final String SEARCH = "https://www.google.com/search?safe=active&q=";

    /** Ad and tracking hosts: blocked for privacy and to make pages load faster. */
    static final Set<String> ADS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com", "amazon-adsystem.com",
            "scorecardresearch.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net", "adnxs.com",
            "pubmatic.com", "rubiconproject.com", "moatads.com", "quantserve.com", "hotjar.com",
            "adsrvr.org", "openx.net", "casalemedia.com", "teads.tv", "sharethrough.com", "media.net",
            "yieldmo.com", "zemanta.com", "mgid.com", "revcontent.com", "connect.facebook.net"));

    static final Set<String> VIDEO_EXT = new HashSet<>(Arrays.asList(
            "mp4", "m4v", "webm", "mov", "mkv", "avi", "flv", "m3u8", "mpd", "ts", "3gp", "wmv"));

    /** Turns what the user typed into a URL: an address, or a SafeSearch Google search. */
    static String fromInput(String input) {
        String s = input.trim();
        if (s.isEmpty()) return null;
        if (s.matches("(?i)^https?://.*")) return s;
        if (!s.contains(" ") && s.matches("(?i)^[a-z0-9.-]+\\.[a-z]{2,}(/.*)?$")) return "https://" + s;
        return SEARCH + URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    static boolean hostMatches(String host, Set<String> rules) {
        String d = host;
        while (!d.isEmpty()) {
            if (rules.contains(d)) return true;
            int dot = d.indexOf('.');
            if (dot < 0) return false;
            d = d.substring(dot + 1);
        }
        return false;
    }

    static boolean isGoogleSearch(String url) {
        String h = host(url);
        return (h.equals("google.com") || h.equals("www.google.com") || h.matches("(www\\.)?google\\.[a-z.]+"))
                && url.contains("/search");
    }

    /** Google image/video search tabs are blocked; everything else gets safe=active. */
    static boolean isBlockedSearch(String url) {
        if (!isGoogleSearch(url)) return false;
        return url.matches(".*[?&](tbm=(isch|vid)|udm=(2|7|39))(&.*)?$");
    }

    static String enforceSafeSearch(String url) {
        if (!isGoogleSearch(url) || url.matches(".*[?&]safe=active(&.*)?$")) return url;
        String cleaned = url.replaceAll("([?&])safe=[^&]*&?", "$1");
        return cleaned + (cleaned.contains("?") ? (cleaned.endsWith("?") || cleaned.endsWith("&") ? "" : "&") : "?") + "safe=active";
    }

    static String extension(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            path = url;
        }
        if (path == null) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static boolean isVideo(String url, String accept) {
        return VIDEO_EXT.contains(extension(url)) || (accept != null && accept.startsWith("video/"));
    }

    /** May this file be downloaded? */
    static boolean downloadAllowed(String fileName, String mime, Set<String> allowedTypes, boolean allowApk) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (ext.equals("apk") || ext.equals("apks") || ext.equals("xapk")
                || "application/vnd.android.package-archive".equals(mime)) {
            return allowApk;
        }
        if (VIDEO_EXT.contains(ext) || (mime != null && mime.startsWith("video/"))) return false;
        return allowedTypes.contains(ext);
    }
}
