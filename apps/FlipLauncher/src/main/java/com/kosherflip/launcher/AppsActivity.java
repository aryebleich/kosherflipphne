package com.kosherflip.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * App list for keypad navigation: up/down to move, OK to open,
 * or press 1-9 to open the numbered entry directly.
 * Apps hidden by KosherGuard never appear here because the system hides them.
 */
public class AppsActivity extends Activity {
    private final List<ResolveInfo> apps = new ArrayList<>();
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list = new ListView(this);
        list.setBackgroundColor(Color.BLACK);
        list.setAdapter(new Adapter());
        list.setOnItemClickListener((p, v, pos, id) -> launch(pos));
        setContentView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        ((BaseAdapter) list.getAdapter()).notifyDataSetChanged();
        list.requestFocus();
    }

    private void load() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
        apps.clear();
        for (ResolveInfo ri : found) {
            if (!getPackageName().equals(ri.activityInfo.packageName)) apps.add(ri);
        }
        Collator c = Collator.getInstance();
        Collections.sort(apps, (a, b) -> c.compare(
                String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
    }

    private void launch(int pos) {
        if (pos < 0 || pos >= apps.size()) return;
        ResolveInfo ri = apps.get(pos);
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode >= KeyEvent.KEYCODE_1 && keyCode <= KeyEvent.KEYCODE_9) {
            launch(keyCode - KeyEvent.KEYCODE_1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SOFT_RIGHT) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int p) { return apps.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Context c = AppsActivity.this;
            LinearLayout row = convert instanceof LinearLayout ? (LinearLayout) convert : null;
            ImageView icon;
            TextView text;
            if (row == null) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(6);
                row.setPadding(pad, pad, pad, pad);
                icon = new ImageView(c);
                text = new TextView(c);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                text.setTextColor(Color.WHITE);
                text.setPadding(dp(10), 0, 0, 0);
                row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                row.addView(text);
            } else {
                icon = (ImageView) row.getChildAt(0);
                text = (TextView) row.getChildAt(1);
            }
            PackageManager pm = getPackageManager();
            ResolveInfo ri = apps.get(pos);
            Drawable d = ri.loadIcon(pm);
            icon.setImageDrawable(d);
            String num = pos < 9 ? (pos + 1) + "  " : "    ";
            text.setText(num + ri.loadLabel(pm));
            return row;
        }
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
