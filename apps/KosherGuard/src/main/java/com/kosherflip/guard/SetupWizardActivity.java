package com.kosherflip.guard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-only setup: no QR code, no computer. A checklist; each step opens the
 * right Android screen, and the list ticks itself off when you come back.
 * Tamper protection is paused while setup is unfinished.
 */
public class SetupWizardActivity extends Activity {
    private static final int REQ_VPN = 1, REQ_ADMIN = 2, REQ_HOME = 3;

    private final List<String> labels = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();
    private ListView list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SystemBars.pad(this);
        list = new ListView(this);
        list.setOnItemClickListener((p, v, pos, id) -> actions.get(pos).run());
        setContentView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Standard.setupDone(this)) Maintenance.start(this, 30);
        refresh();
    }

    private void step(boolean done, String title, String hint, Runnable action) {
        labels.add((done ? "✔  " : "○  ") + title + (done || hint == null ? "" : "\n     " + hint));
        actions.add(action);
    }

    private void refresh() {
        labels.clear();
        actions.clear();
        labels.add("KosherFlip setup – do each step, then come back here.\n"
                + "(Full protection with a computer or QR code is optional: see the guide.)");
        actions.add(() -> { });

        boolean pin = Pin.isSet(this);
        step(pin, "1. Admin PIN", "for the person who supervises the phone", this::createPin);
        boolean profile = Standard.prefs(this).getBoolean("profile", false);
        step(profile, "2. Profile: " + Profiles.label(Policy.load(this).profile), "Filtered (all apps) / Kosher / Heimish / Open", this::chooseProfile);
        boolean canInstall = Standard.canInstall(this);
        boolean apps = Standard.appsInstalled(this);
        if (!apps) {
            step(canInstall, "3. Allow KosherGuard to install the KosherFlip apps",
                    "turn on \"Allow from this source\", then press Back", this::allowInstalls);
        }
        step(apps, "4. Install the KosherFlip apps", "launcher, browser, AI, T9 keyboard (needs internet)",
                this::installApps);
        boolean vpn = Standard.vpnReady(this);
        step(vpn, "5. Turn on the website filter", "press OK / Allow on the next screen", this::vpn);
        boolean guard = Standard.screenGuardOn(this);
        step(guard, "6. Turn on the screen filter", restrictedHint(), this::accessibility);
        boolean admin = Standard.adminActive(this);
        step(admin, "7. Protect KosherGuard from removal", "press Activate", this::activateAdmin);
        boolean home = Standard.homeIsOurs(this);
        step(home, "8. Make Flip Home the home screen", "choose Flip Home, then Always", this::setHome);
        if (Standard.hasKeypad(this) && PolicyEnforcer.isInstalled(this, BuildConfigShim.KEYS)) {
            step(Standard.keyboardIsOurs(this), "9. Yiddish/Hebrew T9 keyboard",
                    "turn on KosherKeys, then choose it", this::keyboard);
        }
        boolean always = Standard.alwaysOnConfirmed(this);
        step(always, "10. Make the website filter always on",
                "gear next to KosherGuard → Always-on VPN + Block connections without VPN", this::alwaysOn);
        boolean ready = pin && vpn && guard && admin;
        step(Standard.setupDone(this), "Finish – lock the phone", ready ? "protection starts now"
                : "do steps 1, 5, 6 and 7 first", () -> finishSetup(ready));
        labels.add("Share KosherFlip with another phone (Bluetooth / Nearby Share)");
        actions.add(() -> startActivity(ShareProvider.shareIntent(this)));

        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        list.requestFocus();
    }

    private String restrictedHint() {
        if (Build.VERSION.SDK_INT >= 33) {
            return "Settings → Accessibility → KosherGuard screen filter → On.\n"
                    + "     If it is greyed out: App info → ⋮ → Allow restricted settings, then try again";
        }
        return "Settings → Accessibility → KosherGuard screen filter → On";
    }

    // ---- steps ----------------------------------------------------------------

    private void createPin() {
        if (Pin.isSet(this) && Standard.setupDone(this)) return;
        LinearLayout col = Ui.column(this);
        EditText a = Ui.pin(this, "New PIN (4+ digits)");
        EditText c = Ui.pin(this, "Repeat PIN");
        col.addView(a);
        col.addView(c);
        new AlertDialog.Builder(this).setTitle("Admin PIN").setView(col)
                .setPositiveButton("Save", (d, w) -> {
                    String p1 = a.getText().toString();
                    if (p1.length() < 4 || !p1.equals(c.getText().toString())) {
                        toast("PINs must match and have 4+ digits");
                    } else {
                        Pin.set(this, p1);
                    }
                    refresh();
                }).setNegativeButton("Cancel", null).show();
    }

    private void chooseProfile() {
        String[] names = new String[Profiles.NAMES.length];
        for (int i = 0; i < names.length; i++) names[i] = Profiles.NAMES[i][1];
        new AlertDialog.Builder(this).setTitle("Profile").setItems(names, (d, which) -> {
            Policy p = Policy.load(this);
            Profiles.apply(p, Profiles.NAMES[which][0]);
            p.save(this);
            Standard.prefs(this).edit().putBoolean("profile", true).apply();
            refresh();
        }).show();
    }

    private void allowInstalls() {
        open(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
    }

    private void installApps() {
        if (!Standard.canInstall(this)) {
            allowInstalls();
            return;
        }
        toast("Downloading… press Install on each app when asked");
        new Thread(() -> {
            String r = Companions.installOrUpdate(this, Policy.load(this).updateUrl);
            runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("KosherFlip apps").setMessage(r
                    + "\nNo internet? Install the APK files you received (Bluetooth/Nearby) from the Files app.")
                    .setPositiveButton("OK", (d, w) -> refresh()).show());
        }).start();
    }

    private void vpn() {
        Intent i = VpnService.prepare(this);
        if (i == null) {
            PolicyEnforcer.startVpn(this);
            refresh();
        } else {
            startActivityForResult(i, REQ_VPN);
        }
    }

    private void accessibility() {
        toast("Find \"KosherGuard screen filter\" and turn it On, then press Back");
        open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void activateAdmin() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PolicyEnforcer.admin(this))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Stops KosherGuard from being uninstalled without the admin PIN.");
        try {
            startActivityForResult(i, REQ_ADMIN);
        } catch (ActivityNotFoundException e) {
            toast("Not available on this phone");
        }
    }

    private void setHome() {
        if (!PolicyEnforcer.isInstalled(this, BuildConfigShim.LAUNCHER)) {
            toast("Install the KosherFlip apps first (step 4)");
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        if (!open(new Intent(Settings.ACTION_HOME_SETTINGS))) {
            open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void keyboard() {
        new AlertDialog.Builder(this).setMessage("1. Turn on \"KosherKeys T9\" on the next screen and press Back.\n"
                + "2. Then choose KosherKeys from the list.")
                .setPositiveButton("Turn on", (d, w) -> open(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)))
                .setNeutralButton("Choose", (d, w) -> {
                    android.view.inputmethod.InputMethodManager imm =
                            getSystemService(android.view.inputmethod.InputMethodManager.class);
                    if (imm != null) imm.showInputMethodPicker();
                }).show();
    }

    private void alwaysOn() {
        new AlertDialog.Builder(this).setTitle("Always-on filter")
                .setMessage("On the next screen press the gear ⚙ next to KosherGuard, turn on "
                        + "\"Always-on VPN\" and \"Block connections without VPN\", then press Back.")
                .setPositiveButton("Open VPN settings", (d, w) -> open(new Intent(Settings.ACTION_VPN_SETTINGS)))
                .setNeutralButton("I did it", (d, w) -> {
                    Standard.setAlwaysOnConfirmed(this);
                    refresh();
                }).show();
    }

    private void finishSetup(boolean ready) {
        if (!ready) {
            toast("Please finish steps 1, 5, 6 and 7 first");
            return;
        }
        Standard.setSetupDone(this, true);
        Maintenance.end(this);
        new Thread(() -> PolicyEnforcer.apply(this)).start();
        toast("KosherFlip protection is on");
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open(home);
        finish();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VPN && res == RESULT_OK) PolicyEnforcer.startVpn(this);
        refresh();
    }

    private boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("This screen isn't available on this phone");
            return false;
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
