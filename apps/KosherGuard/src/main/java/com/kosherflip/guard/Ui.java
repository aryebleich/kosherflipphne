package com.kosherflip.guard;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for big, keypad-friendly views on small flip phone screens. */
final class Ui {
    private Ui() {}

    static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static TextView text(Context c, String s, int sp) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        return tv;
    }

    static TextView title(Context c, String s) {
        TextView tv = text(c, s, 20);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    static Button button(Context c, String s, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setOnClickListener(l);
        b.setFocusable(true);
        return b;
    }

    static EditText pin(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        e.setSingleLine(true);
        return e;
    }

    static EditText multiline(Context c, String value) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setMinLines(4);
        return e;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        return l;
    }
}
