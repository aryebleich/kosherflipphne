package com.kosherflip.guard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Decides whether a DNS name may be resolved under the current policy. */
public final class DnsFilter {
    private final boolean allowlistMode;
    private final Set<String> allowed = new HashSet<>();
    private final Set<String> blocked = new HashSet<>();
    private final Set<String> adminAllowed;

    public DnsFilter(Policy p) {
        this(p, Collections.emptyMap());
    }

    public DnsFilter(Policy p, Map<String, Set<String>> categories) {
        this.allowlistMode = Policy.MODE_ALLOWLIST.equals(p.filterMode);
        this.adminAllowed = new HashSet<>(p.allowedDomains);
        allowed.addAll(p.allowedDomains);
        allowed.addAll(Categories.domains(categories, p.trustedCategories));
        blocked.addAll(p.blockedDomains);
        blocked.addAll(Categories.domains(categories, p.blockedCategories));
        if (p.blockVideo) blocked.addAll(Categories.domains(categories, Collections.singleton("video")));
    }

    /**
     * The most specific matching rule wins. On a tie, the admin's own
     * always-allowed list beats a block; a category block beats a trusted category.
     */
    public boolean isAllowed(String name) {
        String d = normalize(name);
        if (d.isEmpty()) return false;
        String a = longestMatch(allowed, d);
        String b = longestMatch(blocked, d);
        if (b != null) {
            return a != null && (a.length() > b.length()
                    || (a.length() == b.length() && adminAllowed.contains(a)));
        }
        if (a != null) return true;
        return !allowlistMode;
    }

    private static String longestMatch(Set<String> rules, String domain) {
        if (rules.contains("=" + domain)) return "=" + domain;
        String d = domain;
        while (true) {
            if (rules.contains(d)) return d;
            int dot = d.indexOf('.');
            if (dot < 0) return null;
            d = d.substring(dot + 1);
        }
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
        return longestMatch(rules, domain) != null;
    }
}
