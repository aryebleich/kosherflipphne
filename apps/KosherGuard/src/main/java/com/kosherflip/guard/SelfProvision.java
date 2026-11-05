package com.kosherflip.guard;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * In the ROM build KosherGuard is a privileged, platform-signed app, which
 * lets it make itself device owner on first boot (before setup completes),
 * the same way an OEM setup wizard does. Uses system APIs by reflection so the
 * same APK also builds against the public SDK.
 */
final class SelfProvision {
    private static final String TAG = "KosherGuard";

    private SelfProvision() {}

    static boolean canSelfProvision(Context ctx) {
        return ctx.checkSelfPermission("android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS")
                == PackageManager.PERMISSION_GRANTED;
    }

    static boolean tryBecomeDeviceOwner(Context ctx) {
        DevicePolicyManager dpm = ctx.getSystemService(DevicePolicyManager.class);
        if (dpm == null) return false;
        if (dpm.isDeviceOwnerApp(ctx.getPackageName())) return true;
        if (!canSelfProvision(ctx)) return false;
        ComponentName admin = PolicyEnforcer.admin(ctx);
        try {
            Method setActiveAdmin = DevicePolicyManager.class.getMethod(
                    "setActiveAdmin", ComponentName.class, boolean.class);
            setActiveAdmin.invoke(dpm, admin, true);
        } catch (Throwable t) {
            Log.w(TAG, "setActiveAdmin", t);
        }
        int userId = Process.myUserHandle().hashCode(); // UserHandle.hashCode() == user id
        String[][] attempts = {{"setDeviceOwner", "int"}, {"setDeviceOwner", ""}};
        for (String[] a : attempts) {
            try {
                if ("int".equals(a[1])) {
                    Method m = DevicePolicyManager.class.getMethod(a[0], ComponentName.class, int.class);
                    m.invoke(dpm, admin, userId);
                } else {
                    Method m = DevicePolicyManager.class.getMethod(a[0], ComponentName.class);
                    m.invoke(dpm, admin);
                }
                if (dpm.isDeviceOwnerApp(ctx.getPackageName())) return true;
            } catch (Throwable t) {
                Log.w(TAG, a[0] + "(" + a[1] + ")", t);
            }
        }
        return dpm.isDeviceOwnerApp(ctx.getPackageName());
    }
}
