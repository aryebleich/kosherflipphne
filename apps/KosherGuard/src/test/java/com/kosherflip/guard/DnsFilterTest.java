package com.kosherflip.guard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class DnsFilterTest {

    private static Policy policy(String mode) {
        Policy p = new Policy();
        p.filterMode = mode;
        p.allowedDomains.add("api.anthropic.com");
        p.allowedDomains.add("googleapis.com");
        p.blockedDomains.add("youtube.com");
        p.blockedDomains.add("=google.com");
        p.blockedDomains.add("=www.google.com");
        return p;
    }

    @Test
    public void blocklistBlocksDomainAndSubdomains() {
        DnsFilter f = new DnsFilter(policy(Policy.MODE_BLOCKLIST));
        assertFalse(f.isAllowed("youtube.com"));
        assertFalse(f.isAllowed("m.youtube.com."));
        assertFalse(f.isAllowed("WWW.YouTube.com"));
        assertTrue(f.isAllowed("example.org"));
        assertTrue(f.isAllowed("notyoutube.com"));
    }

    @Test
    public void exactRulesOnlyMatchExactName() {
        DnsFilter f = new DnsFilter(policy(Policy.MODE_BLOCKLIST));
        assertFalse(f.isAllowed("google.com"));
        assertFalse(f.isAllowed("www.google.com"));
        assertTrue(f.isAllowed("maps.google.com"));
        assertTrue(f.isAllowed("mail.google.com"));
    }

    @Test
    public void allowlistOnlyAllowsListed() {
        DnsFilter f = new DnsFilter(policy(Policy.MODE_ALLOWLIST));
        assertTrue(f.isAllowed("api.anthropic.com"));
        assertTrue(f.isAllowed("maps.googleapis.com"));
        assertFalse(f.isAllowed("example.org"));
        assertFalse(f.isAllowed("anthropic.com"));
        assertFalse(f.isAllowed(""));
    }

    @Test
    public void allowedBeatsBlocked() {
        Policy p = policy(Policy.MODE_BLOCKLIST);
        p.blockedDomains.add("googleapis.com");
        assertTrue(new DnsFilter(p).isAllowed("maps.googleapis.com"));
    }

    // ---- packets ----

    static byte[] query(String name) {
        ByteArrayOutputStream dns = new ByteArrayOutputStream();
        int[] header = {0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0};
        for (int b : header) dns.write(b);
        for (String label : name.split("\\.")) {
            dns.write(label.length());
            dns.write(label.getBytes(), 0, label.length());
        }
        dns.write(0);
        dns.write(0); dns.write(1); // A
        dns.write(0); dns.write(1); // IN
        byte[] d = dns.toByteArray();
        byte[] pkt = new byte[20 + 8 + d.length];
        pkt[0] = 0x45;
        DnsPacket.put16(pkt, 2, pkt.length);
        pkt[8] = 64;
        pkt[9] = 17;
        byte[] src = {10, 111, (byte) 222, 2}, dst = {10, 111, (byte) 222, 1};
        System.arraycopy(src, 0, pkt, 12, 4);
        System.arraycopy(dst, 0, pkt, 16, 4);
        DnsPacket.put16(pkt, 20, 40000);
        DnsPacket.put16(pkt, 22, 53);
        DnsPacket.put16(pkt, 24, 8 + d.length);
        System.arraycopy(d, 0, pkt, 28, d.length);
        return pkt;
    }

    @Test
    public void parsesQueryName() {
        byte[] q = query("www.example.com");
        DnsPacket p = DnsPacket.parse(q, q.length);
        assertNotNull(p);
        assertEquals("www.example.com", p.queryName());
        assertEquals(40000, p.srcPort);
    }

    @Test
    public void ignoresNonDns() {
        byte[] q = query("a.com");
        DnsPacket.put16(q, 22, 443);
        assertNull(DnsPacket.parse(q, q.length));
    }

    @Test
    public void blockedReplyIsNxdomainAddressedBack() {
        byte[] q = query("youtube.com");
        DnsPacket p = DnsPacket.parse(q, q.length);
        byte[] r = p.blockedReply();
        // IP: swapped addresses, valid checksum
        assertEquals(1, r[15]);   // source = original destination (10.111.222.1)
        assertEquals(2, r[19]);   // destination = original source (10.111.222.2)
        assertEquals(DnsPacket.ipChecksum(r, 0, 20), DnsPacket.u16(r, 10));
        // UDP ports swapped
        assertEquals(53, DnsPacket.u16(r, 20));
        assertEquals(40000, DnsPacket.u16(r, 22));
        // DNS: same id, response, NXDOMAIN, one question, no answers
        int d = 28;
        assertEquals(0x1234, DnsPacket.u16(r, d));
        assertTrue((r[d + 2] & 0x80) != 0);
        assertEquals(3, r[d + 3] & 0x0F);
        assertEquals(1, DnsPacket.u16(r, d + 4));
        assertEquals(0, DnsPacket.u16(r, d + 6));
        assertEquals(r.length - 28, DnsPacket.u16(r, 24) - 8);
    }

    // ---- categories, profiles, helpers ----

    private static java.util.Map<String, java.util.Set<String>> cats() {
        java.util.Map<String, java.util.Set<String>> m = new java.util.HashMap<>();
        m.put("social", new java.util.HashSet<>(java.util.Arrays.asList("facebook.com", "instagram.com")));
        m.put("video", new java.util.HashSet<>(java.util.Arrays.asList("youtube.com", "vimeo.com")));
        m.put("shopping", new java.util.HashSet<>(java.util.Arrays.asList("amazon.com", "walmart.com")));
        return m;
    }

    @Test
    public void categoriesBlockAndTrust() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.HEIMISH);
        DnsFilter f = new DnsFilter(p, cats());
        assertFalse(f.isAllowed("m.facebook.com"));
        assertFalse(f.isAllowed("vimeo.com"));        // video blocked
        assertTrue(f.isAllowed("www.amazon.com"));     // trusted
        assertTrue(f.isAllowed("example.org"));        // standard mode
    }

    @Test
    public void kosherProfileAllowsOnlyTrusted() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.KOSHER);
        DnsFilter f = new DnsFilter(p, cats());
        assertTrue(f.isAllowed("amazon.com"));
        assertFalse(f.isAllowed("example.org"));
    }

    @Test
    public void openProfileAllowsVideoWhenNotBlocked() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.OPEN);
        DnsFilter f = new DnsFilter(p, cats());
        assertTrue(f.isAllowed("youtube.com"));
        assertTrue(f.isAllowed("facebook.com"));
    }

    @Test
    public void adminBlockBeatsTrustedCategory() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.HEIMISH);
        p.blockedDomains.add("walmart.com");
        assertFalse(new DnsFilter(p, cats()).isAllowed("walmart.com"));
    }

    @Test
    public void verificationCodes() {
        assertEquals("482913", SmsReceiver.verificationCode("Your Google verification code is 482913"));
        assertEquals("1234", SmsReceiver.verificationCode("Chase: one-time code 1234. Don't share it."));
        assertNull(SmsReceiver.verificationCode("Call me at 3pm, room 1204"));
    }

    @Test
    public void siteGrouping() {
        assertEquals("amazon.com", Stats.site("images-na.ssl.amazon.com."));
        assertEquals("bbc.co.uk", Stats.site("news.bbc.co.uk"));
        assertEquals("x.com", Stats.site("x.com"));
    }

    @Test
    public void cacheTtlFromAnswer() {
        // header: id, flags, qd=1, an=1; question a.com A IN; answer ptr->12, A IN ttl=300 rdlen 4
        byte[] m = {0, 1, (byte) 0x81, (byte) 0x80, 0, 1, 0, 1, 0, 0, 0, 0,
                1, 'a', 3, 'c', 'o', 'm', 0, 0, 1, 0, 1,
                (byte) 0xC0, 12, 0, 1, 0, 1, 0, 0, 1, 44, 0, 4, 1, 2, 3, 4};
        assertEquals(300, DnsCache.minTtlSeconds(m));
        DnsCache c = new DnsCache(10);
        c.put("a.com/1/1", m);
        assertNotNull(c.get("a.com/1/1"));
    }

    @Test
    public void tamperRules() {
        assertTrue(TamperRules.isProtectedScreen("App info KosherGuard Uninstall Force stop"));
        assertTrue(TamperRules.isProtectedScreen("Use KosherGuard screen filter"));
        assertTrue(TamperRules.isProtectedScreen("Reset options Erase all data (factory reset)"));
        assertTrue(TamperRules.isProtectedScreen("Debugging USB debugging"));
        assertTrue(TamperRules.isProtectedScreen("Reboot to safe mode? OK Cancel"));
        assertFalse(TamperRules.isProtectedScreen("Apps: Calculator Camera KosherGuard Maps"));
        assertFalse(TamperRules.isProtectedScreen("Wi-Fi Network & internet Bluetooth Display"));
        assertFalse(TamperRules.isProtectedScreen("Camera Uninstall Force stop"));
    }

    @Test
    public void filteredProfileAllowsAppsAndSitesButNotHarmful() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.FILTERED);
        java.util.Map<String, java.util.Set<String>> m = cats();
        m.put("dating", new java.util.HashSet<>(java.util.Arrays.asList("tinder.com")));
        DnsFilter f = new DnsFilter(p, m);
        assertTrue(f.isAllowed("youtube.com"));
        assertTrue(f.isAllowed("facebook.com"));
        assertFalse(f.isAllowed("tinder.com"));
        assertTrue(PolicyEnforcer.isAppAllowed(p, false, "com.android.chrome"));
        assertTrue(PolicyEnforcer.isAppAllowed(p, false, "com.google.android.youtube"));
        p.blockedApps.add("com.zhiliaoapp.musically");
        assertFalse(PolicyEnforcer.isAppAllowed(p, false, "com.zhiliaoapp.musically"));
        assertFalse(PolicyEnforcer.isAppAllowed(p, true, "com.android.chrome")); // Shabbos mode
        assertTrue(p.allowAppInstalls);
        assertTrue(TamperRules.isProtectedScreen("Connection request: SuperVPN wants to set up a VPN connection"));
        assertFalse(TamperRules.isProtectedScreen("KosherGuard wants to set up a VPN connection"));
    }

    @Test
    public void heimishProfileStillUsesApprovedList() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.HEIMISH);
        assertFalse(PolicyEnforcer.isAppAllowed(p, false, "com.android.chrome"));
    }

    @Test
    public void carrierServersAlwaysResolve() {
        Policy p = new Policy();
        Profiles.apply(p, Profiles.KOSHER); // strictest: only trusted + always-allowed
        p.allowedDomains.add("3gppnetwork.org");
        DnsFilter f = new DnsFilter(p, cats());
        assertTrue(f.isAllowed("epdg.epc.mnc260.mcc310.pub.3gppnetwork.org")); // Wi-Fi calling
        assertTrue(f.isAllowed("ims.mnc480.mcc311.3gppnetwork.org"));
        assertTrue(java.util.Arrays.asList(FilterVpnService.TELEPHONY_BYPASS).contains("com.android.phone"));
    }

    private static String problems(CallCheck.Input in) {
        StringBuilder sb = new StringBuilder();
        for (CallCheck.Finding f : CallCheck.diagnose(in)) if (f.problem) sb.append(f.title).append('|');
        return sb.toString();
    }

    @Test
    public void callCheckFindsCarrierLockAndOtherCauses() {
        CallCheck.Input in = new CallCheck.Input();
        in.simState = CallCheck.SIM_NETWORK_LOCKED;
        assertTrue(problems(in).contains("locked to another carrier"));
        in.simState = CallCheck.SIM_READY;
        in.voice = CallCheck.SVC_EMERGENCY;
        assertTrue(problems(in).contains("Emergency calls only"));
        in.voice = CallCheck.SVC_IN;
        assertEquals("", problems(in));
        in.lockdownVpn = true;
        assertTrue(problems(in).contains("Block connections"));
        in.airplane = true;
        assertEquals("Airplane mode is on|", problems(in));
    }
}
