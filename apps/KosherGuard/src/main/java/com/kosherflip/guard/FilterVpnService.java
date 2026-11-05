package com.kosherflip.guard;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A local, DNS-only VPN. Every app's DNS queries are sent to a virtual DNS
 * server inside the tunnel; blocked names get NXDOMAIN, allowed names are
 * forwarded to a family-safe upstream resolver. No other traffic enters the
 * tunnel, so there is no speed or battery cost for normal data.
 *
 * KosherGuard (as device owner) sets this as the always-on VPN and forbids
 * the user from changing VPN or Private DNS settings.
 */
public class FilterVpnService extends VpnService {
    private static final String TAG = "KosherGuardVpn";
    static final String ACTION_RELOAD = "com.kosherflip.guard.RELOAD";
    private static final String VIRTUAL_DNS = "10.111.222.1";
    private static final String TUN_ADDR = "10.111.222.2";

    private ParcelFileDescriptor tun;
    private Thread reader;
    private ExecutorService pool;
    private volatile DnsFilter filter;
    private volatile List<String> upstream;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadPolicy();
        if (!running) start();
        return START_STICKY;
    }

    private void loadPolicy() {
        Policy p = Policy.load(this);
        filter = new DnsFilter(p);
        upstream = p.upstreamDns;
    }

    private synchronized void start() {
        try {
            Builder b = new Builder()
                    .setSession("KosherGuard filter")
                    .addAddress(TUN_ADDR, 32)
                    .addDnsServer(VIRTUAL_DNS)
                    .addRoute(VIRTUAL_DNS, 32)
                    .setBlocking(true);
            try {
                b.addDisallowedApplication(getPackageName());
            } catch (Exception ignored) {
            }
            tun = b.establish();
        } catch (Exception e) {
            Log.e(TAG, "establish failed", e);
            tun = null;
        }
        if (tun == null) {
            stopSelf();
            return;
        }
        running = true;
        pool = Executors.newFixedThreadPool(4);
        reader = new Thread(this::loop, "dns-tun");
        reader.start();
    }

    private void loop() {
        FileInputStream in = new FileInputStream(tun.getFileDescriptor());
        FileOutputStream out = new FileOutputStream(tun.getFileDescriptor());
        byte[] buf = new byte[32767];
        while (running) {
            int n;
            try {
                n = in.read(buf);
            } catch (IOException e) {
                break;
            }
            if (n <= 0) continue;
            DnsPacket pkt = DnsPacket.parse(buf, n);
            if (pkt == null) continue;
            pool.execute(() -> handle(pkt, out));
        }
    }

    private void handle(DnsPacket pkt, FileOutputStream out) {
        String name = pkt.queryName();
        byte[] reply;
        if (name == null || !filter.isAllowed(name)) {
            Log.i(TAG, "blocked " + name);
            reply = pkt.blockedReply();
        } else {
            reply = forward(pkt);
            if (reply == null) return; // let the client retry
        }
        synchronized (this) {
            try {
                out.write(reply);
            } catch (IOException e) {
                Log.w(TAG, "write failed", e);
            }
        }
    }

    private byte[] forward(DnsPacket pkt) {
        byte[] query = pkt.dnsPayload();
        for (String server : upstream) {
            try (DatagramSocket s = new DatagramSocket()) {
                protect(s);
                s.setSoTimeout(4000);
                InetAddress addr = InetAddress.getByName(server);
                s.send(new DatagramPacket(query, query.length, addr, 53));
                byte[] resp = new byte[4096];
                DatagramPacket dp = new DatagramPacket(resp, resp.length);
                s.receive(dp);
                return pkt.reply(resp, dp.getLength());
            } catch (IOException e) {
                Log.w(TAG, "upstream " + server + " failed: " + e.getMessage());
            }
        }
        return null;
    }

    @Override
    public void onRevoke() {
        stop();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private synchronized void stop() {
        running = false;
        if (pool != null) pool.shutdownNow();
        try {
            if (tun != null) tun.close();
        } catch (IOException ignored) {
        }
        tun = null;
    }
}
