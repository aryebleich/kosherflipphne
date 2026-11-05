package com.kosherflip.guard;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public class GuardAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        SetupActivity.disableSelf(context);
        PolicyEnforcer.apply(context);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        PolicyEnforcer.apply(context);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Removing KosherGuard turns off the filter. An admin PIN is required.";
    }
}
