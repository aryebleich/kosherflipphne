package com.kosherflip.guard;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * QR-code / zero-touch setup (Android 10+): the setup wizard downloads
 * KosherGuard, asks it which mode it wants (fully managed), then lets it
 * apply its policy before setup finishes.
 */
public final class ProvisioningActivities {
    private ProvisioningActivities() {}

    /** android.app.action.GET_PROVISIONING_MODE */
    public static class Mode extends Activity {
        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            Intent r = new Intent();
            // DevicePolicyManager.EXTRA_PROVISIONING_MODE / PROVISIONING_MODE_FULLY_MANAGED_DEVICE (API 31)
            r.putExtra("android.app.extra.PROVISIONING_MODE", 1);
            setResult(RESULT_OK, r);
            finish();
        }
    }

    /** android.app.action.ADMIN_POLICY_COMPLIANCE (also PROVISIONING_SUCCESSFUL on older Android) */
    public static class Compliance extends Activity {
        @Override
        protected void onCreate(Bundle b) {
            super.onCreate(b);
            setContentView(Ui.text(this, "KosherFlip\nSetting up the filter and installing apps…", 20));
            new Thread(() -> {
                SetupActivity.disableSelf(this);
                PolicyEnforcer.apply(this);
                Companions.installOrUpdate(this, null);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            }).start();
        }
    }
}
