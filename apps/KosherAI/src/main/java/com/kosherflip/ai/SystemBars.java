package com.kosherflip.ai;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * Android 15+ draws apps edge-to-edge. Keep our content clear of the status
 * bar, navigation bar, display cutout and on-screen keyboard.
 */
final class SystemBars {
    private SystemBars() {}

    static void pad(Activity a) {
        if (Build.VERSION.SDK_INT < 35) return; // older Android already lays out below the bars
        View content = a.findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets i = in.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return in;
        });
        content.requestApplyInsets();
    }
}
