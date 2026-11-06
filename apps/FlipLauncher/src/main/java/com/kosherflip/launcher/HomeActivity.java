package com.kosherflip.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.icu.text.DateFormat;
import android.icu.util.HebrewCalendar;
import android.icu.util.ULocale;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.Locale;

/**
 * Feature-phone style home screen for flip phones:
 *  - big clock, civil date and Hebrew date
 *  - OK / left soft key: all apps;  right soft key: contacts
 *  - number keys start dialing;  CALL opens the dialer
 *  - D-pad shortcuts: up = messages, down = contacts, left = camera, right = AI assistant
 */
public class HomeActivity extends Activity {
    private TextView clock, date, hebrew, zmanim, status;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SystemBars.pad(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);

        status = label(14, Color.LTGRAY);
        status.setGravity(Gravity.START);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        clock = label(52, Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        date = label(18, Color.WHITE);
        hebrew = label(18, 0xFFE0C060);
        center.addView(clock);
        center.addView(date);
        zmanim = label(15, 0xFFB0B0B0);
        center.addView(hebrew);
        center.addView(zmanim);
        root.addView(center, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout soft = new LinearLayout(this);
        TextView left = label(16, Color.WHITE);
        left.setText("Menu");
        left.setGravity(Gravity.START);
        TextView right = label(16, Color.WHITE);
        right.setText("Contacts");
        right.setGravity(Gravity.END);
        soft.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        soft.addView(right, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(soft, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(tick, f);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(tick);
    }

    @Override
    public void onBackPressed() {
        // Home has nowhere to go back to.
    }

    private void refresh() {
        Date now = new Date();
        clock.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        date.setText(android.text.format.DateFormat.format("EEEE, d MMMM yyyy", now));
        HebrewCalendar hc = new HebrewCalendar();
        hc.setTime(now);
        hebrew.setText(DateFormat.getDateInstance(hc, DateFormat.LONG, ULocale.ENGLISH).format(now));
        status.setText(statusLine());
        zmanim.setText(zmanimLine());
    }

    /** Sunrise / shkiah for today; on Friday, candle lighting. Uses the last known location. */
    private String zmanimLine() {
        android.location.Location loc = lastLocation();
        if (loc == null) return "";
        java.time.ZoneId z = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(z);
        double lat = loc.getLatitude(), lon = loc.getLongitude();
        java.time.ZonedDateTime rise = com.kosherflip.common.Zmanim.sunrise(today, lat, lon, z);
        java.time.ZonedDateTime set = com.kosherflip.common.Zmanim.sunset(today, lat, lon, z);
        if (rise == null || set == null) return "";
        java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Sunrise ").append(tf.format(Date.from(rise.toInstant())))
          .append("  \u00b7  Shkiah ").append(tf.format(Date.from(set.toInstant())));
        if (today.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) {
            sb.append("\nCandle lighting ").append(tf.format(Date.from(set.minusMinutes(18).toInstant())));
        }
        return sb.toString();
    }

    @SuppressWarnings("MissingPermission")
    private android.location.Location lastLocation() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return null;
        android.location.LocationManager lm = getSystemService(android.location.LocationManager.class);
        if (lm == null) return null;
        android.location.Location best = null;
        for (String p : lm.getProviders(true)) {
            android.location.Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
        }
        return best;
    }

    private String statusLine() {
        StringBuilder sb = new StringBuilder();
        TelephonyManager tm = getSystemService(TelephonyManager.class);
        if (tm != null) {
            String op = tm.getNetworkOperatorName();
            sb.append(op == null || op.isEmpty() ? "No service" : op);
        }
        BatteryManager bm = getSystemService(BatteryManager.class);
        if (bm != null) {
            int pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (pct > 0) sb.append("   ").append(pct).append('%');
        }
        return sb.toString();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0) return super.onKeyDown(keyCode, event);
        char digit = digitFor(keyCode);
        if (digit != 0) {
            open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(String.valueOf(digit)))));
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SOFT_LEFT:
                startActivity(new Intent(this, AppsActivity.class));
                return true;
            case KeyEvent.KEYCODE_SOFT_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                open(new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI));
                return true;
            case KeyEvent.KEYCODE_CALL:
                open(new Intent(Intent.ACTION_DIAL));
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING));
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_CAMERA:
                open(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                Intent ai = getPackageManager().getLaunchIntentForPackage("com.kosherflip.ai");
                if (ai != null) open(ai);
                else startActivity(new Intent(this, AppsActivity.class));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    static char digitFor(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
        }
        if (keyCode == KeyEvent.KEYCODE_STAR) return '*';
        if (keyCode == KeyEvent.KEYCODE_POUND) return '#';
        return 0;
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Not available", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView label(int sp, int color) {
        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setTextLocale(Locale.getDefault());
        return tv;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
