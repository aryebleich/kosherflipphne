package com.kosherflip.guard;

import java.util.Locale;
import java.util.Set;

/** Decides whether a DNS name may be resolved under the current policy. */
public final class DnsFilter {
    private final boolean allowlistMode;
    private final Set<String> allowed;
    private final Set<String> blocked;

    public DnsFilter(Policy p) {
        this.allowlistMode = Policy.MODE_ALLOWLIST.equals(p.filterMode);
        this.allowed = p.allowedDomains;
        this.blocked = p.blockedDomains;
    }

    public boolean isAllowed(String name) {
        String d = normalize(name);
        if (d.isEmpty()) return false;
        if (matches(allowed, d)) return true;
        if (allowlistMode) return false;
        return !matches(blocked, d);
    }

    static String normalize(String name) {
        String d = name.trim().toLowerCase(Locale.ROOT);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d;
    }

    /**
     * An entry "example.com" matches example.com and every subdomain.
     * An entry "=example.com" matches only that exact name.
     */
    static boolean matches(Set<String> rules, String domain) {
        if (rules.contains("=" + domain)) return true;
        String d = domain;
        while (true) {
            if (rules.contains(d)) return true;
            int dot = d.indexOf('.');
            if (dot < 0) return false;
            d = d.substring(dot + 1);
        }
    }
}
