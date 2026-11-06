package com.kosherflip.guard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;

/**
 * Replaces AOSP's "Provision" app in the ROM build. Runs once at first boot:
 * becomes device owner, applies the policy, marks setup complete and then
 * disables itself so the normal launcher takes over.
 */
public class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.pad(this);
        TextView tv = Ui.text(this, "KosherFlip\nSetting up the filter…", 22);
        tv.setGravity(android.view.Gravity.CENTER);
        setContentView(tv);

        if (!SelfProvision.canSelfProvision(this)) {
            // Normal (adb) install: we are not the setup wizard.
            disableSelf(this);
            finish();
            return;
        }
        new Thread(() -> {
            boolean owner = SelfProvision.tryBecomeDeviceOwner(this);
            Log.i("KosherGuard", "first boot, device owner=" + owner);
            if (owner) PolicyEnforcer.apply(this);
            try {
                Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
                Settings.Secure.putInt(getContentResolver(), "user_setup_complete", 1);
            } catch (SecurityException e) {
                Log.w("KosherGuard", "cannot mark setup complete", e);
            }
            runOnUiThread(() -> {
                disableSelf(this);
                Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(home);
                finish();
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        // Setup cannot be skipped.
    }

    static void disableSelfIfNotRom(Context ctx) {
        if (!SelfProvision.canSelfProvision(ctx)) disableSelf(ctx);
    }

    static void disableSelf(Context ctx) {
        try {
            ctx.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(ctx, SetupActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
        }
    }
}
