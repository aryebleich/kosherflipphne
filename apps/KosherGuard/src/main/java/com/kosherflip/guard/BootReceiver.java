package com.kosherflip.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-applies the policy at boot and whenever an app is installed or updated. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                // ROM build with a third-party setup wizard (e.g. Google's): take device
                // ownership at first boot, before setup finishes and accounts are added.
                if (SelfProvision.canSelfProvision(context) && !PolicyEnforcer.isDeviceOwner(context)) {
                    SelfProvision.tryBecomeDeviceOwner(context);
                }
                PolicyEnforcer.apply(context);
            } finally {
                pr.finish();
            }
        }).start();
    }
}
