package com.kosherflip.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UrlRulesTest {
    @Test public void inputBecomesUrlOrSafeSearch() {
        assertEquals("https://amazon.com", UrlRules.fromInput("amazon.com"));
        assertEquals("https://x.org/a", UrlRules.fromInput("https://x.org/a"));
        assertEquals(UrlRules.SEARCH + "shabbos+candles", UrlRules.fromInput("shabbos candles"));
    }

    @Test public void safeSearchForced() {
        assertEquals("https://www.google.com/search?q=a&safe=active",
                UrlRules.enforceSafeSearch("https://www.google.com/search?q=a&safe=off"));
        String ok = "https://www.google.com/search?safe=active&q=a";
        assertEquals(ok, UrlRules.enforceSafeSearch(ok));
        assertEquals("https://amazon.com/s?k=a", UrlRules.enforceSafeSearch("https://amazon.com/s?k=a"));
    }

    @Test public void imageAndVideoSearchBlocked() {
        assertTrue(UrlRules.isBlockedSearch("https://www.google.com/search?q=a&tbm=isch"));
        assertTrue(UrlRules.isBlockedSearch("https://www.google.com/search?q=a&udm=2&safe=active"));
        assertFalse(UrlRules.isBlockedSearch("https://www.google.com/search?q=a&safe=active"));
    }

    @Test public void downloads() {
        Set<String> types = new HashSet<>(Arrays.asList("pdf", "mp3"));
        assertTrue(UrlRules.downloadAllowed("shiur.mp3", "audio/mpeg", types, false));
        assertTrue(UrlRules.downloadAllowed("invoice.pdf", "application/pdf", types, false));
        assertFalse(UrlRules.downloadAllowed("movie.mp4", "video/mp4", types, false));
        assertFalse(UrlRules.downloadAllowed("app.apk", "application/vnd.android.package-archive", types, false));
        assertTrue(UrlRules.downloadAllowed("app.apk", null, types, true));
        assertFalse(UrlRules.downloadAllowed("setup.exe", null, types, true));
    }

    @Test public void hostsAndVideo() {
        assertTrue(UrlRules.hostMatches("pagead2.googlesyndication.com", UrlRules.ADS));
        assertFalse(UrlRules.hostMatches("amazon.com", UrlRules.ADS));
        assertTrue(UrlRules.isVideo("https://cdn.x.com/v/clip.m3u8?x=1", null));
        assertFalse(UrlRules.isVideo("https://cdn.x.com/a.jpg", "image/webp"));
    }
}
