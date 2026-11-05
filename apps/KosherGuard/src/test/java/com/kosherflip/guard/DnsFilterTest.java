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
}
