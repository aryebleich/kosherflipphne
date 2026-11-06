package com.kosherflip.guard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.List;

/** "Check calls": finds out why calls don't work on this phone and opens the right settings. */
public class CallCheckActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SystemBars.pad(this);
        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.READ_PHONE_STATE}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        show();
    }

    private void show() {
        CallCheck.Input in = new CallCheck.Input();
        in.airplane = Settings.Global.getInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        if (tm != null) {
            in.simState = tm.getSimState();
            in.simCarrier = String.valueOf(tm.getSimOperatorName());
            in.networkCarrier = String.valueOf(tm.getNetworkOperatorName());
            if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    ServiceState ss = tm.getServiceState();
                    if (ss != null) in.voice = ss.getState();
                } catch (RuntimeException ignored) {
                }
            }
        }
        try {
            in.lockdownVpn = Settings.Secure.getInt(getContentResolver(), "always_on_vpn_lockdown", 0) == 1;
        } catch (RuntimeException ignored) {
        }
        PhoneApps.refresh(this);
        Policy p = Policy.load(this);
        for (String pkg : PhoneApps.callApps()) {
            if (PolicyEnforcer.isInstalled(this, pkg) && !PolicyEnforcer.isAppAllowed(p, false, pkg)) in.dialerAllowed = false;
        }

        LinearLayout col = Ui.column(this);
        col.addView(Ui.title(this, "Check calls"));
        List<CallCheck.Finding> found = CallCheck.diagnose(in);
        for (CallCheck.Finding f : found) {
            col.addView(Ui.text(this, (f.problem ? "✘ " : "✔ ") + f.title, 17));
            if (!f.fix.isEmpty()) col.addView(Ui.text(this, f.fix, 14));
        }
        col.addView(Ui.text(this, "KosherFlip never filters the phone app, calling service or picture messages.", 13));
        col.addView(Ui.button(this, "Mobile network settings (VoLTE, network type)", v -> open(
                new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS), new Intent(Settings.ACTION_WIRELESS_SETTINGS))));
        col.addView(Ui.button(this, "Access Point Names (APN)", v -> open(new Intent(Settings.ACTION_APN_SETTINGS))));
        col.addView(Ui.button(this, "Airplane mode", v -> open(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))));
        col.addView(Ui.button(this, "VPN settings", v -> open(new Intent(Settings.ACTION_VPN_SETTINGS))));
        col.addView(Ui.button(this, "Phone info menu (*#*#4636#*#*)", v -> {
            Toast.makeText(this, "If it doesn't open by itself, type *#*#4636#*#* on the keypad", Toast.LENGTH_LONG).show();
            open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*#*#4636#*#*"))));
        }));
        col.addView(Ui.button(this, "Show IMEI (*#06#)", v ->
                open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode("*#06#"))))));
        col.addView(Ui.button(this, "Check again", v -> show()));
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    private void open(Intent... options) {
        for (Intent i : options) {
            try {
                startActivity(i);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        Toast.makeText(this, "This screen isn't available on this phone", Toast.LENGTH_SHORT).show();
    }
}
