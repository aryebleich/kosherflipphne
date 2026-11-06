package com.kosherflip.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * PIN-protected admin console, designed for D-pad / keypad use:
 * every screen is a vertical list, OK selects, BACK returns to the menu.
 */
public class AdminActivity extends Activity {
    private static final int REQ_VPN = 1;
    private boolean unlocked;
    private boolean inMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.pad(this);
        SetupActivity.disableSelfIfNotRom(this);
        if (PolicyEnforcer.isDeviceOwner(this)) applyQuietly(); // self-heal whenever the console opens
        if (!Pin.isSet(this)) showCreatePin();
        else showUnlock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Lock again whenever the console leaves the screen.
        unlocked = false;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (Pin.isSet(this)) showUnlock();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && unlocked && !inMenu) {
            showMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---- PIN ---------------------------------------------------------------

    private void showCreatePin() {
        inMenu = false;
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Welcome to KosherGuard"));
        col.addView(Ui.text(this, "Choose an admin PIN (4+ digits). Give it to the person who supervises this phone.", 16));
        EditText a = Ui.pin(this, "New PIN");
        EditText b = Ui.pin(this, "Repeat PIN");
        col.addView(a);
        col.addView(b);
        col.addView(Ui.button(this, "Save PIN", v -> {
            String p1 = a.getText().toString(), p2 = b.getText().toString();
            if (p1.length() < 4) toast("PIN must have at least 4 digits");
            else if (!p1.equals(p2)) toast("PINs do not match");
            else {
                Pin.set(this, p1);
                unlocked = true;
                showMenu();
            }
        }));
        setScroll(col);
        a.requestFocus();
    }

    private void showUnlock() {
        inMenu = false;
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "KosherGuard"));
        col.addView(Ui.text(this, status(), 15));
        EditText pin = Ui.pin(this, "Admin PIN");
        col.addView(pin);
        col.addView(Ui.button(this, "Unlock", v -> {
            long wait = Pin.lockedSeconds(this);
            if (wait > 0) {
                toast("Too many attempts. Try again in " + wait + "s");
            } else if (Pin.check(this, pin.getText().toString())) {
                unlocked = true;
                showMenu();
            } else {
                pin.setText("");
                toast("Wrong PIN");
            }
        }));
        setScroll(col);
        pin.requestFocus();
    }

    // ---- Menu --------------------------------------------------------------

    private final List<String> menuLabels = new ArrayList<>();
    private final List<Runnable> menuActions = new ArrayList<>();

    private void item(String label, Runnable action) {
        menuLabels.add(label);
        menuActions.add(action);
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "off";
    }

    private void showMenu() {
        inMenu = true;
        Policy p = Policy.load(this);
        int pending = Requests.list(this).size();
        menuLabels.clear();
        menuActions.clear();
        item("Status", this::showStatus);
        item("Profile: " + p.profile, this::chooseProfile);
        item("Requests" + (pending > 0 ? " (" + pending + " new)" : ""), this::showRequests);
        item("Insights & suggestions", this::showInsights);
        item("Allowed apps", this::showApps);
        item("Blocked categories (" + p.blockedCategories.size() + ")", () -> chooseCategories(true));
        item("Trusted categories (" + p.trustedCategories.size() + ")", () -> chooseCategories(false));
        item("Filter mode: " + (Policy.MODE_ALLOWLIST.equals(p.filterMode) ? "strict" : "standard"), this::toggleMode);
        item("Always-allowed websites", () -> editDomains(true));
        item("Blocked websites", () -> editDomains(false));
        item("Pictures in browser: " + p.imageMode, this::chooseImages);
        item("Video: " + (p.blockVideo ? "blocked" : "allowed"), () -> toggle(q -> q.blockVideo = !q.blockVideo));
        item("Browser: " + onOff(p.browserEnabled), () -> toggle(q -> q.browserEnabled = !q.browserEnabled));
        item("Screen filter (all apps): " + onOff(p.screenFilter), () -> toggle(q -> q.screenFilter = !q.screenFilter));
        item("Filter words", this::editWords);
        item("Talk-only (no texting): " + onOff(p.talkOnly), () -> toggle(q -> q.talkOnly = !q.talkOnly));
        item("Camera: " + onOff(p.cameraEnabled), () -> toggle(q -> q.cameraEnabled = !q.cameraEnabled));
        item("Hotspot block: " + onOff(p.blockTethering), () -> toggle(q -> q.blockTethering = !q.blockTethering));
        item("USB / Bluetooth / SD file transfer block: " + onOff(p.blockUsbTransfer),
                () -> toggle(q -> q.blockUsbTransfer = !q.blockUsbTransfer));
        item("Shabbos mode: " + p.shabbosMode, this::editShabbos);
        item("AI assistant: " + (p.aiEnabled ? p.aiProvider : "off"), this::editAi);
        item("Install / update KosherFlip apps", this::updateApps);
        item("T9 keyboard (English/Hebrew/Yiddish) as default: " + onOff(p.t9Keyboard),
                () -> toggle(q -> q.t9Keyboard = !q.t9Keyboard));
        item("App installs: " + (p.allowAppInstalls ? "ALLOWED" : "blocked"), this::toggleInstalls);
        item("USB debugging lock: " + onOff(p.lockUsbDebugging), this::toggleUsb);
        item("Apply now", this::applyNow);
        item("Change PIN", this::showCreatePin);
        item("Reset to defaults", () -> confirm("Reset all filter settings to defaults?", () -> {
            Policy.reset(this);
            applyNow();
        }));
        item("Remove KosherGuard", () -> confirm("Remove the filter and all restrictions from this phone?", () -> {
            PolicyEnforcer.release(this);
            toast("KosherGuard removed. You can now uninstall it.");
            showMenu();
        }));

        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, menuLabels));
        lv.setOnItemClickListener((parent, view, pos, id) -> menuActions.get(pos).run());
        setContentView(lv);
        lv.requestFocus();
    }

    private interface Edit {
        void apply(Policy p);
    }

    private void toggle(Edit e) {
        Policy p = Policy.load(this);
        e.apply(p);
        p.save(this);
        applyQuietly();
        showMenu();
    }

    /** Applies in the background and stays on the menu. */
    private void applyQuietly() {
        new Thread(() -> PolicyEnforcer.apply(this)).start();
    }

    private void updateApps() {
        inMenu = false;
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Installing / updating\u2026"));
        col.addView(Ui.text(this, "Downloading the latest KosherFlip apps. This can take a few minutes on a slow connection.", 15));
        setScroll(col);
        String url = Policy.load(this).updateUrl;
        new Thread(() -> {
            String report = Companions.installOrUpdate(this, url);
            runOnUiThread(() -> {
                LinearLayout c = Ui.column(this);
                c.addView(Ui.title(this, "Apps"));
                c.addView(Ui.text(this, report, 15));
                c.addView(Ui.button(this, "OK", v -> showMenu()));
                setScroll(c);
            });
        }).start();
    }

    private void chooseProfile() {
        String[] labels = new String[Profiles.NAMES.length];
        int checked = 0;
        Policy p = Policy.load(this);
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Profiles.NAMES[i][1];
            if (Profiles.NAMES[i][0].equals(p.profile)) checked = i;
        }
        new AlertDialog.Builder(this).setTitle("Profile")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    toggle(q -> Profiles.apply(q, Profiles.NAMES[which][0]));
                    toast("Profile set. You can still fine-tune every option.");
                }).show();
    }

    private void chooseCategories(boolean blocked) {
        Policy p = Policy.load(this);
        String[] labels = new String[Categories.NAMES.length];
        boolean[] checked = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Categories.NAMES[i][1];
            checked[i] = (blocked ? p.blockedCategories : p.trustedCategories).contains(Categories.NAMES[i][0]);
        }
        new AlertDialog.Builder(this)
                .setTitle(blocked ? "Block these categories" : "Always allow (and show pictures on)")
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Save", (d, w) -> toggle(q -> {
                    java.util.Set<String> set = new java.util.LinkedHashSet<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) set.add(Categories.NAMES[i][0]);
                    if (blocked) q.blockedCategories = set; else q.trustedCategories = set;
                }))
                .setNegativeButton("Cancel", null).show();
    }

    private void chooseImages() {
        String[] keys = {"none", "trusted", "blur", "all"};
        String[] labels = {"No pictures (text only, fastest)", "Pictures only on trusted sites (shopping, Torah, banks)",
                "Blur all pictures", "Show all pictures (screen filter still on)"};
        int checked = java.util.Arrays.asList(keys).indexOf(Policy.load(this).imageMode);
        new AlertDialog.Builder(this).setTitle("Pictures in the browser")
                .setSingleChoiceItems(labels, Math.max(0, checked), (d, which) -> {
                    d.dismiss();
                    toggle(q -> q.imageMode = keys[which]);
                }).show();
    }

    private void showRequests() {
        inMenu = false;
        List<org.json.JSONObject> reqs = Requests.list(this);
        List<String> labels = new ArrayList<>();
        java.text.DateFormat df = android.text.format.DateFormat.getDateFormat(this);
        for (org.json.JSONObject r : reqs) {
            String type = Requests.TYPE_APP.equals(r.optString("type")) ? "App" : "Website";
            String value = r.optString("value");
            if (Requests.TYPE_APP.equals(r.optString("type"))) value = PolicyEnforcer.label(this, value) + " (" + value + ")";
            String note = r.optString("note");
            labels.add(type + ": " + value + (note.isEmpty() ? "" : "\n\u201c" + note + "\u201d")
                    + "\n" + df.format(new java.util.Date(r.optLong("time"))));
        }
        if (labels.isEmpty()) labels.add("No requests. The user can ask for a blocked website from the browser's block page, or for an app from the app list.");
        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            if (reqs.isEmpty()) return;
            new AlertDialog.Builder(this).setMessage(labels.get(pos))
                    .setPositiveButton("Approve", (d, w) -> {
                        Requests.resolve(this, pos, true);
                        applyQuietly();
                        showRequests();
                    })
                    .setNegativeButton("Decline", (d, w) -> {
                        Requests.resolve(this, pos, false);
                        showRequests();
                    })
                    .setNeutralButton("Later", null).show();
        });
        setContentView(lv);
        lv.requestFocus();
    }

    private void showInsights() {
        inMenu = false;
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Most used sites"));
        StringBuilder sb = new StringBuilder();
        for (String[] s : Stats.top(this, false, 12)) sb.append(s[0]).append("  \u00b7 ").append(s[1]).append('\n');
        col.addView(Ui.text(this, sb.length() == 0 ? "No data yet." : sb.toString(), 15));
        col.addView(Ui.title(this, "Most blocked"));
        sb.setLength(0);
        for (String[] s : Stats.top(this, true, 12)) {
            String name = s[0].startsWith("app:") ? "screen in " + PolicyEnforcer.label(this, s[0].substring(4)) : s[0];
            sb.append(name).append("  \u00b7 ").append(s[1]).append('\n');
        }
        col.addView(Ui.text(this, sb.length() == 0 ? "Nothing blocked yet." : sb.toString(), 15));
        List<String[]> sugg = Stats.suggestions(this);
        if (!sugg.isEmpty()) {
            col.addView(Ui.title(this, "Suggestions"));
            col.addView(Ui.text(this, "Blocked sites the phone keeps trying. Allow one only if you know it is needed "
                    + "(for example a shopping or bank site's helper domain).", 14));
            for (String[] s : sugg) {
                col.addView(Ui.button(this, "Allow " + s[0] + " (" + s[1] + " tries)", v -> confirm("Always allow " + s[0] + "?", () -> {
                    Policy q = Policy.load(this);
                    q.allowedDomains.add(s[0]);
                    q.save(this);
                    applyQuietly();
                    showInsights();
                })));
            }
        }
        col.addView(Ui.button(this, "Clear statistics", v -> {
            Stats.clear(this);
            showInsights();
        }));
        col.addView(Ui.button(this, "Back", v -> showMenu()));
        setScroll(col);
    }

    private void editWords() {
        inMenu = false;
        Policy p = Policy.load(this);
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Filter words"));
        col.addView(Ui.text(this, "Extra words or phrases to block on screen, in the browser and in AI answers "
                + "(any language, one per line). Add \":3\" for a weak word that only blocks together with others. "
                + "A built-in list is always active.", 14));
        EditText e = Ui.multiline(this, Policy.joinLines(p.customWords));
        col.addView(e);
        col.addView(Ui.button(this, "Save", v -> {
            Policy q = Policy.load(this);
            q.customWords = Policy.parseLines(e.getText().toString().replace(", ", "\n"));
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, "Cancel", v -> showMenu()));
        setScroll(col);
    }

    private void editShabbos() {
        inMenu = false;
        Policy p = Policy.load(this);
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Shabbos & Yom Tov mode"));
        col.addView(Ui.text(this, "During Shabbos mode only the phone, contacts and clock stay open (for emergencies). "
                + "Auto: from candle lighting until nightfall, on Shabbos and Yom Tov, calculated for the location below. "
                + "Follow your community's luach for exact times.", 14));
        String[] modes = {"off", "auto", "on"};
        col.addView(Ui.button(this, "Mode: " + p.shabbosMode + " (press to change)", v -> {
            int i = (java.util.Arrays.asList(modes).indexOf(p.shabbosMode) + 1) % modes.length;
            Policy q = Policy.load(this);
            q.shabbosMode = modes[i];
            q.save(this);
            applyQuietly();
            editShabbos();
        }));
        EditText lat = new EditText(this), lon = new EditText(this);
        int num = android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED;
        lat.setInputType(num);
        lon.setInputType(num);
        lat.setHint("Latitude, e.g. 40.68");
        lon.setHint("Longitude, e.g. -73.94");
        if (p.latitude != 0 || p.longitude != 0) {
            lat.setText(String.valueOf(p.latitude));
            lon.setText(String.valueOf(p.longitude));
        }
        col.addView(lat);
        col.addView(lon);
        col.addView(Ui.button(this, "Use the phone's location", v -> {
            android.location.Location loc = lastLocation();
            if (loc == null) {
                toast("Location not available - type it in");
                return;
            }
            lat.setText(String.format(java.util.Locale.ROOT, "%.4f", loc.getLatitude()));
            lon.setText(String.format(java.util.Locale.ROOT, "%.4f", loc.getLongitude()));
        }));
        col.addView(Ui.button(this, "Israel (1 day Yom Tov): " + (p.inIsrael ? "yes" : "no"), v -> {
            Policy q = Policy.load(this);
            q.inIsrael = !q.inIsrael;
            q.save(this);
            editShabbos();
        }));
        int[] ends = {42, 50, 72};
        col.addView(Ui.button(this, "Candles " + p.candleMinutes + " min before sunset; ends " + p.endMinutes
                + " min after (press to change)", v -> {
            Policy q = Policy.load(this);
            int i = 0;
            while (i < ends.length && ends[i] != q.endMinutes) i++;
            q.endMinutes = ends[(i + 1) % ends.length];
            q.candleMinutes = q.endMinutes == 72 ? 40 : 18;
            q.save(this);
            editShabbos();
        }));
        col.addView(Ui.button(this, "Save location", v -> {
            Policy q = Policy.load(this);
            try {
                q.latitude = Double.parseDouble(lat.getText().toString().trim());
                q.longitude = Double.parseDouble(lon.getText().toString().trim());
            } catch (NumberFormatException ex) {
                toast("Enter numbers for latitude and longitude");
                return;
            }
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, "Back", v -> showMenu()));
        setScroll(col);
    }

    @SuppressWarnings("MissingPermission")
    private android.location.Location lastLocation() {
        android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
        if (lm == null) return null;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
            return null;
        }
        android.location.Location best = null;
        for (String prov : lm.getProviders(true)) {
            android.location.Location l = lm.getLastKnownLocation(prov);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        return best;
    }

    private String status() {
        boolean owner = PolicyEnforcer.isDeviceOwner(this);
        Policy p = Policy.load(this);
        return "Device owner: " + (owner ? "yes" : "NO") + "\n"
                + "Profile: " + Profiles.label(p.profile) + "\n"
                + "Filter mode: " + p.filterMode + "\n"
                + "Screen filter: " + onOff(p.screenFilter) + "\n"
                + "Shabbos mode now: " + (PolicyEnforcer.isShabbosNow(p) ? "yes" : "no") + "\n"
                + "Allowed apps: " + p.allowedApps.size() + "\n"
                + "App installs: " + (p.allowAppInstalls ? "ALLOWED" : "blocked") + "\n"
                + (owner ? "" : "\nTo enforce the filter run (from a computer):\n"
                + "adb shell dpm set-device-owner com.kosherflip.guard/.GuardAdminReceiver\n");
    }

    private void showStatus() {
        inMenu = false;
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Status"));
        col.addView(Ui.text(this, status(), 16));
        col.addView(Ui.button(this, "Back", v -> showMenu()));
        setScroll(col);
    }

    private void showApps() {
        inMenu = false;
        Policy p = Policy.load(this);
        List<String> pkgs = PolicyEnforcer.launcherPackages(this);
        pkgs.remove(getPackageName());
        List<String> labels = new ArrayList<>();
        for (String pkg : pkgs) labels.add(PolicyEnforcer.label(this, pkg) + "\n" + pkg);
        ListView lv = new ListView(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, labels));
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            boolean on = BuildConfigShim.AI.equals(pkg) ? p.aiEnabled
                    : BuildConfigShim.BROWSER.equals(pkg) ? p.browserEnabled : p.allowedApps.contains(pkg);
            lv.setItemChecked(i, on);
        }
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Policy q = Policy.load(this);
            String pkg = pkgs.get(pos);
            boolean on = lv.isItemChecked(pos);
            if (BuildConfigShim.AI.equals(pkg)) q.aiEnabled = on;
            else if (BuildConfigShim.BROWSER.equals(pkg)) q.browserEnabled = on;
            else if (on) q.allowedApps.add(pkg);
            else q.allowedApps.remove(pkg);
            q.save(this);
            PolicyEnforcer.apply(this);
        });
        LinearLayout col = Ui.column(this);
        col.addView(Ui.text(this, "Checked apps are visible. BACK when done.", 15));
        col.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(col);
        lv.requestFocus();
    }

    private void toggleMode() {
        Policy p = Policy.load(this);
        p.filterMode = Policy.MODE_BLOCKLIST.equals(p.filterMode) ? Policy.MODE_ALLOWLIST : Policy.MODE_BLOCKLIST;
        p.save(this);
        applyNow();
        toast(Policy.MODE_ALLOWLIST.equals(p.filterMode)
                ? "Strict: only always-allowed websites work"
                : "Standard: family-safe DNS + blocked list");
    }

    private void toggleInstalls() {
        Policy p = Policy.load(this);
        p.allowAppInstalls = !p.allowAppInstalls;
        p.save(this);
        applyNow();
        toast(p.allowAppInstalls ? "App installs ALLOWED - turn off when done" : "App installs blocked");
    }

    private void toggleUsb() {
        Policy p = Policy.load(this);
        p.lockUsbDebugging = !p.lockUsbDebugging;
        p.save(this);
        applyNow();
        toast(p.lockUsbDebugging ? "USB debugging locked" : "USB debugging allowed");
    }

    private void editDomains(boolean allowed) {
        inMenu = false;
        Policy p = Policy.load(this);
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, allowed ? "Always-allowed websites" : "Blocked websites"));
        col.addView(Ui.text(this, "One domain per line. \"example.com\" also covers its subdomains; "
                + "\"=example.com\" matches only that exact name.", 14));
        EditText e = Ui.multiline(this, Policy.joinLines(allowed ? p.allowedDomains : p.blockedDomains));
        col.addView(e);
        col.addView(Ui.button(this, "Save", v -> {
            Policy q = Policy.load(this);
            if (allowed) q.allowedDomains = Policy.parseLines(e.getText().toString());
            else q.blockedDomains = Policy.parseLines(e.getText().toString());
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, "Cancel", v -> showMenu()));
        setScroll(col);
    }

    private void editAi() {
        inMenu = false;
        Policy p = Policy.load(this);
        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "AI assistant"));
        col.addView(Ui.text(this, "\"Ask AI\" gives short, modest answers. Every answer also passes the word filter.\n\n"
                + "FREE: Google Gemini. Get a free key at aistudio.google.com (Get API key) and paste it below.\n"
                + "Also free: any OpenAI-compatible service with free models (e.g. Groq, OpenRouter) - enter its URL and model.\n"
                + "Paid: Claude (console.anthropic.com).\n\n"
                + "The key is passed to the app as a managed setting; the user cannot see or change it.", 14));
        String[] providers = {"gemini", "openai", "claude"};
        col.addView(Ui.button(this, "Provider: " + p.aiProvider + " (press to change)", v -> {
            Policy q = Policy.load(this);
            int i = (java.util.Arrays.asList(providers).indexOf(q.aiProvider) + 1) % providers.length;
            q.aiProvider = providers[i];
            q.save(this);
            editAi();
        }));
        EditText key = new EditText(this);
        key.setHint("API key");
        key.setText(p.aiApiKey);
        key.setSingleLine(true);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText model = new EditText(this);
        model.setHint("Model (blank = recommended free model)");
        model.setText(p.aiModel);
        model.setSingleLine(true);
        EditText url = new EditText(this);
        url.setHint("Base URL (OpenAI-compatible only), e.g. https://api.groq.com/openai/v1");
        url.setText(p.aiBaseUrl);
        url.setSingleLine(true);
        col.addView(key);
        col.addView(model);
        if ("openai".equals(p.aiProvider)) col.addView(url);
        col.addView(Ui.button(this, "Save", v -> {
            Policy q = Policy.load(this);
            q.aiApiKey = key.getText().toString().trim();
            q.aiModel = model.getText().toString().trim();
            q.aiBaseUrl = url.getText().toString().trim();
            if (!q.aiBaseUrl.isEmpty()) {
                try {
                    q.allowedDomains.add(android.net.Uri.parse(q.aiBaseUrl).getHost());
                } catch (Exception ignored) {
                }
            }
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, p.aiEnabled ? "Turn AI off" : "Turn AI on", v -> {
            Policy q = Policy.load(this);
            q.aiEnabled = !q.aiEnabled;
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, "Back", v -> showMenu()));
        setScroll(col);
    }

    private void applyNow() {
        Intent prep = VpnService.prepare(this);
        if (prep != null && !PolicyEnforcer.isDeviceOwner(this)) {
            startActivityForResult(prep, REQ_VPN);
        }
        new Thread(() -> {
            String report = PolicyEnforcer.apply(this);
            runOnUiThread(() -> {
                inMenu = false;
                LinearLayout col = Ui.column(this);
                col.addView(Ui.title(this, "Applied"));
                col.addView(Ui.text(this, report, 15));
                col.addView(Ui.button(this, "OK", v -> showMenu()));
                setScroll(col);
            });
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) PolicyEnforcer.startVpn(this);
    }

    private void confirm(String msg, Runnable yes) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> yes.run())
                .setNegativeButton("No", null)
                .show();
    }

    private void setScroll(View v) {
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        setContentView(sv);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
