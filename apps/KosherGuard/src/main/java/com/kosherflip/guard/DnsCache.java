package com.kosherflip.guard;

import java.util.LinkedHashMap;
import java.util.Map;

/** Small LRU cache of DNS answers that honours (and caps) record TTLs. */
final class DnsCache {
    private static final long MIN_TTL_MS = 30_000, MAX_TTL_MS = 600_000;
    private final int capacity;
    private final LinkedHashMap<String, CacheEntry> map;

    private static final class CacheEntry {
        final byte[] answer;
        final long expires;
        CacheEntry(byte[] a, long e) { answer = a; expires = e; }
    }

    DnsCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<String, CacheEntry>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > DnsCache.this.capacity;
            }
        };
    }

    synchronized byte[] get(String key) {
        CacheEntry e = map.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expires) {
            map.remove(key);
            return null;
        }
        return e.answer.clone();
    }

    synchronized void put(String key, byte[] answer) {
        if (answer.length < 12) return;
        int rcode = answer[3] & 0x0F;
        if (rcode != 0 && rcode != 3) return; // don't cache server failures
        long ttl = Math.max(MIN_TTL_MS, Math.min(MAX_TTL_MS, minTtlSeconds(answer) * 1000L));
        map.put(key, new CacheEntry(answer.clone(), System.currentTimeMillis() + ttl));
    }

    synchronized void clear() {
        map.clear();
    }

    /** Smallest TTL of the answer records (0 if none / unparsable). */
    static long minTtlSeconds(byte[] m) {
        try {
            int qd = DnsPacket.u16(m, 4), an = DnsPacket.u16(m, 6);
            int p = 12;
            for (int i = 0; i < qd; i++) p = skipName(m, p) + 4;
            long min = Long.MAX_VALUE;
            for (int i = 0; i < an; i++) {
                p = skipName(m, p);
                long ttl = ((long) (m[p + 4] & 0xFF) << 24) | ((m[p + 5] & 0xFF) << 16) | ((m[p + 6] & 0xFF) << 8) | (m[p + 7] & 0xFF);
                min = Math.min(min, ttl);
                int rdlen = DnsPacket.u16(m, p + 8);
                p += 10 + rdlen;
            }
            return min == Long.MAX_VALUE ? 0 : min;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int skipName(byte[] m, int p) {
        while (true) {
            int l = m[p] & 0xFF;
            if (l == 0) return p + 1;
            if ((l & 0xC0) == 0xC0) return p + 2;
            p += 1 + l;
        }
    }
}
