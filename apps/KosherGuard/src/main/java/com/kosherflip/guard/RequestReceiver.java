package com.kosherflip.guard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Receives access requests from KosherBrowser, FlipLauncher and the block screen:
 *   action com.kosherflip.guard.REQUEST, extras "type" (site|app), "value", "note".
 */
public class RequestReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.kosherflip.guard.REQUEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        String type = Requests.TYPE_APP.equals(intent.getStringExtra("type")) ? Requests.TYPE_APP : Requests.TYPE_SITE;
        Requests.add(context, type, intent.getStringExtra("value"), intent.getStringExtra("note"));
        Toast.makeText(context, "Request sent to the phone's admin", Toast.LENGTH_SHORT).show();
    }
}
