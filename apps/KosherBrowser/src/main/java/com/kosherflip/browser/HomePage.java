package com.kosherflip.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The start page: search, the user's own most-visited sites (learned on the
 * phone), and quick links for shopping, Torah and everyday services.
 */
final class HomePage {
    private static final String[][] SHOPPING = {
            {"Amazon", "https://www.amazon.com"}, {"Walmart", "https://www.walmart.com"},
            {"Target", "https://www.target.com"}, {"Costco", "https://www.costco.com"},
            {"eBay", "https://www.ebay.com"}, {"B&H", "https://www.bhphotovideo.com"},
    };
    private static final String[][] JEWISH = {
            {"Chabad.org", "https://www.chabad.org"}, {"Sefaria", "https://www.sefaria.org"},
            {"OU Kosher", "https://oukosher.org"}, {"Hebcal Zmanim", "https://www.hebcal.com/zmanim"},
            {"YU Torah", "https://www.yutorah.org"}, {"Aish", "https://aish.com"},
    };
    private static final String[][] EVERYDAY = {
            {"Gmail", "https://mail.google.com"}, {"Maps", "https://maps.google.com"},
            {"Weather", "https://weather.gov"}, {"USPS", "https://www.usps.com"},
            {"UPS", "https://www.ups.com"}, {"Uber", "https://m.uber.com"},
    };

    private HomePage() {}

    /** Counts visits per site so the home page shows what this user actually uses. */
    static void recordVisit(Context ctx, String url) {
        String h = UrlRules.host(url);
        if (h.isEmpty()) return;
        if (h.startsWith("www.")) h = h.substring(4);
        SharedPreferences sp = ctx.getSharedPreferences("visits", Context.MODE_PRIVATE);
        sp.edit().putInt(h, sp.getInt(h, 0) + 1).apply();
    }

    static List<String> topSites(Context ctx, int n) {
        SharedPreferences sp = ctx.getSharedPreferences("visits", Context.MODE_PRIVATE);
        List<Map.Entry<String, ?>> all = new ArrayList<>(sp.getAll().entrySet());
        all.sort((a, b) -> ((Integer) b.getValue()) - ((Integer) a.getValue()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) {
            if ((Integer) all.get(i).getValue() >= 2) out.add(all.get(i).getKey());
        }
        return out;
    }

    static String html(Context ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>")
          .append("<style>body{font-family:sans-serif;background:#111;color:#eee;margin:8px}")
          .append("h2{font-size:15px;color:#e0c060;margin:14px 0 6px}a{display:block;color:#9ad0ff;font-size:18px;")
          .append("padding:9px 6px;border-bottom:1px solid #333;text-decoration:none}a:focus{background:#234;outline:none}")
          .append("form{display:flex}input{flex:1;font-size:18px;padding:8px}button{font-size:16px}</style></head><body>")
          .append("<form action='").append(UrlRules.SEARCH.substring(0, UrlRules.SEARCH.indexOf('?')))
          .append("'><input type=hidden name=safe value=active><input name=q placeholder='Search (filtered)' autofocus>")
          .append("<button>Go</button></form>");
        List<String> mine = topSites(ctx, 6);
        if (!mine.isEmpty()) {
            sb.append("<h2>Your sites</h2>");
            for (String h : mine) link(sb, h, "https://" + h);
        }
        section(sb, "Shopping", SHOPPING);
        section(sb, "Torah & Jewish life", JEWISH);
        section(sb, "Everyday", EVERYDAY);
        sb.append("<p style='color:#777;font-size:12px'>Filtered by KosherGuard. Keys: 5 pointer · 2/8 scroll · "
                + "4 back · 6 forward · 0 address · 1 home · * menu</p></body></html>");
        return sb.toString();
    }

    private static void section(StringBuilder sb, String title, String[][] links) {
        sb.append("<h2>").append(title).append("</h2>");
        for (String[] l : links) link(sb, l[0], l[1]);
    }

    private static void link(StringBuilder sb, String label, String url) {
        sb.append("<a href='").append(Html.escapeHtml(url)).append("'>").append(Html.escapeHtml(label)).append("</a>");
    }

    static String blocked(String host, String reason, boolean canRequest) {
        String h = Html.escapeHtml(host == null ? "" : host);
        return "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;background:#111;color:#eee;text-align:center;padding:24px 10px}"
                + "h1{font-size:22px}p{color:#bbb}button{font-size:18px;padding:10px 16px;margin:6px}</style></head><body>"
                + "<div style='font-size:48px'>⛔</div><h1>This page is blocked</h1><p>" + h + "</p>"
                + "<p>" + Html.escapeHtml(reason) + "</p>"
                + (canRequest ? "<button autofocus onclick='KF.request()'>Request access</button>" : "")
                + "<button onclick='history.back()'>Go back</button></body></html>";
    }
}
