package com.kosherflip.guard;

import java.nio.charset.StandardCharsets;

/**
 * Minimal IPv4/UDP/DNS helpers for the DNS-only VPN tunnel.
 * Only what the filter needs: read the query name and build replies.
 */
final class DnsPacket {
    static final int IP_HEADER_MIN = 20;
    static final int UDP_HEADER = 8;

    final byte[] raw;
    final int length;
    final int ipHeaderLen;
    final byte[] srcIp = new byte[4];
    final byte[] dstIp = new byte[4];
    final int srcPort;
    final int dstPort;
    final int dnsOffset;
    final int dnsLength;

    private DnsPacket(byte[] raw, int length, int ihl, int srcPort, int dstPort) {
        this.raw = raw;
        this.length = length;
        this.ipHeaderLen = ihl;
        System.arraycopy(raw, 12, srcIp, 0, 4);
        System.arraycopy(raw, 16, dstIp, 0, 4);
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.dnsOffset = ihl + UDP_HEADER;
        this.dnsLength = length - dnsOffset;
    }

    /** Returns a parsed packet if it is an IPv4 UDP datagram to port 53, else null. */
    static DnsPacket parse(byte[] buf, int len) {
        if (len < IP_HEADER_MIN + UDP_HEADER + 12) return null;
        int version = (buf[0] >> 4) & 0xF;
        if (version != 4) return null;
        int ihl = (buf[0] & 0xF) * 4;
        if (ihl < IP_HEADER_MIN || len < ihl + UDP_HEADER + 12) return null;
        int proto = buf[9] & 0xFF;
        if (proto != 17) return null;
        int srcPort = u16(buf, ihl);
        int dstPort = u16(buf, ihl + 2);
        if (dstPort != 53) return null;
        byte[] copy = new byte[len];
        System.arraycopy(buf, 0, copy, 0, len);
        return new DnsPacket(copy, len, ihl, srcPort, dstPort);
    }

    byte[] dnsPayload() {
        byte[] out = new byte[dnsLength];
        System.arraycopy(raw, dnsOffset, out, 0, dnsLength);
        return out;
    }

    /** Reads the first question's name, or null if malformed. */
    String queryName() {
        int qd = u16(raw, dnsOffset + 4);
        if (qd < 1) return null;
        StringBuilder sb = new StringBuilder();
        int p = dnsOffset + 12;
        int end = length;
        int guard = 0;
        while (p < end && guard++ < 128) {
            int l = raw[p] & 0xFF;
            if (l == 0) return sb.toString();
            if ((l & 0xC0) != 0) return null; // compression is not used in questions
            p++;
            if (p + l > end) return null;
            if (sb.length() > 0) sb.append('.');
            sb.append(new String(raw, p, l, StandardCharsets.US_ASCII));
            p += l;
        }
        return null;
    }

    /** Offset (relative to DNS payload) just after the first question, or -1. */
    private int questionEnd() {
        int p = dnsOffset + 12;
        while (p < length) {
            int l = raw[p] & 0xFF;
            if (l == 0) {
                p += 1 + 4; // terminator + QTYPE + QCLASS
                return p <= length ? p - dnsOffset : -1;
            }
            if ((l & 0xC0) != 0) return -1;
            p += 1 + l;
        }
        return -1;
    }

    /** Builds an NXDOMAIN reply for a blocked name. */
    byte[] blockedReply() {
        int qEnd = questionEnd();
        if (qEnd < 0) qEnd = 12;
        byte[] dns = new byte[qEnd];
        System.arraycopy(raw, dnsOffset, dns, 0, qEnd);
        int rd = dns[2] & 0x01;
        dns[2] = (byte) (0x80 | rd);   // QR=1, opcode=0, AA=0, TC=0, RD copied
        dns[3] = (byte) (0x80 | 0x03); // RA=1, RCODE=3 (NXDOMAIN)
        dns[4] = 0; dns[5] = (byte) (qEnd > 12 ? 1 : 0); // QDCOUNT
        dns[6] = 0; dns[7] = 0;   // ANCOUNT
        dns[8] = 0; dns[9] = 0;   // NSCOUNT
        dns[10] = 0; dns[11] = 0; // ARCOUNT (drops EDNS)
        return reply(dns, dns.length);
    }

    /** Wraps a DNS answer in IPv4/UDP addressed back to the original sender. */
    byte[] reply(byte[] dns, int dnsLen) {
        int total = IP_HEADER_MIN + UDP_HEADER + dnsLen;
        byte[] out = new byte[total];
        out[0] = 0x45;
        out[1] = 0;
        put16(out, 2, total);
        put16(out, 4, 0);       // identification
        put16(out, 6, 0x4000);  // don't fragment
        out[8] = 64;            // TTL
        out[9] = 17;            // UDP
        System.arraycopy(dstIp, 0, out, 12, 4); // swap addresses
        System.arraycopy(srcIp, 0, out, 16, 4);
        put16(out, 10, ipChecksum(out, 0, IP_HEADER_MIN));
        int u = IP_HEADER_MIN;
        put16(out, u, dstPort);
        put16(out, u + 2, srcPort);
        put16(out, u + 4, UDP_HEADER + dnsLen);
        put16(out, u + 6, 0); // checksum optional for IPv4
        System.arraycopy(dns, 0, out, u + UDP_HEADER, dnsLen);
        return out;
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static void put16(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }

    static int ipChecksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = off; i < off + len; i += 2) {
            if (i == off + 10) continue; // checksum field itself
            sum += u16(b, i);
        }
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }
}
