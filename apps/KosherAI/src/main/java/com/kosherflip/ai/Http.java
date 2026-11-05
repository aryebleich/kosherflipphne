package com.kosherflip.ai;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny JSON-over-HTTPS helper (keeps the app small: no extra libraries). */
final class Http {
    private Http() {}

    static final class Response {
        final int status;
        final String body;
        Response(int s, String b) { status = s; body = b; }
    }

    static Response postJson(String url, Map<String, String> headers, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(90_000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
        byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) {
            os.write(out);
        }
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String text = "";
        if (in != null) {
            try (InputStream is = in) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = is.read(b)) > 0) bo.write(b, 0, n);
                text = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        c.disconnect();
        return new Response(status, text);
    }

    static void check(Response r) throws Provider.FriendlyException {
        if (r.status == 401 || r.status == 403) {
            throw new Provider.FriendlyException("The AI key is not valid. Please ask the phone's admin to check it in KosherGuard.");
        }
        if (r.status == 429) {
            throw new Provider.FriendlyException("The free AI limit was reached for now. Please try again in a minute.");
        }
        if (r.status >= 400) {
            throw new Provider.FriendlyException("The assistant had a problem (" + r.status + "). Please try again.");
        }
    }
}
