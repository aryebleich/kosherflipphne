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
        SetupActivity.disableSelfIfNotRom(this);
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

    private static final String[] MENU = {
            "Status",
            "Allowed apps",
            "Filter mode",
            "Always-allowed websites",
            "Blocked websites",
            "AI assistant",
            "App installs",
            "USB debugging lock",
            "Apply now",
            "Change PIN",
            "Reset to defaults",
            "Remove KosherGuard",
    };

    private void showMenu() {
        inMenu = true;
        ListView lv = new ListView(this);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, MENU));
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            switch (pos) {
                case 0: showStatus(); break;
                case 1: showApps(); break;
                case 2: toggleMode(); break;
                case 3: editDomains(true); break;
                case 4: editDomains(false); break;
                case 5: editAi(); break;
                case 6: toggleInstalls(); break;
                case 7: toggleUsb(); break;
                case 8: applyNow(); break;
                case 9: showCreatePin(); break;
                case 10: confirm("Reset all filter settings to defaults?", () -> {
                    Policy.reset(this);
                    applyNow();
                }); break;
                case 11: confirm("Remove the filter and all restrictions from this phone?", () -> {
                    PolicyEnforcer.release(this);
                    toast("KosherGuard removed. You can now uninstall it.");
                    showMenu();
                }); break;
                default: break;
            }
        });
        setContentView(lv);
        lv.requestFocus();
    }

    private String status() {
        boolean owner = PolicyEnforcer.isDeviceOwner(this);
        Policy p = Policy.load(this);
        return "Device owner: " + (owner ? "yes" : "NO") + "\n"
                + "Filter mode: " + p.filterMode + "\n"
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
            boolean on = BuildConfigShim.AI.equals(pkg) ? p.aiEnabled : p.allowedApps.contains(pkg);
            lv.setItemChecked(i, on);
        }
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            Policy q = Policy.load(this);
            String pkg = pkgs.get(pos);
            boolean on = lv.isItemChecked(pos);
            if (BuildConfigShim.AI.equals(pkg)) q.aiEnabled = on;
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
        col.addView(Ui.text(this, "KosherAI answers questions with a modest, filtered assistant. "
                + "Enter a Claude API key (from console.anthropic.com). It is passed to KosherAI "
                + "as a managed setting and the user cannot see or change it.", 14));
        EditText key = new EditText(this);
        key.setHint("sk-ant-...");
        key.setText(p.aiApiKey);
        key.setSingleLine(true);
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        col.addView(key);
        col.addView(Ui.button(this, p.aiEnabled ? "Disable AI app" : "Enable AI app", v -> {
            Policy q = Policy.load(this);
            q.aiEnabled = !q.aiEnabled;
            q.save(this);
            applyNow();
        }));
        col.addView(Ui.button(this, "Save key", v -> {
            Policy q = Policy.load(this);
            q.aiApiKey = key.getText().toString().trim();
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
