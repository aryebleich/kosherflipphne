package com.kosherflip.ai;

import android.app.Activity;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keypad friendly chat: type with the number keys (T9), press OK/ENTER to send,
 * left soft key / MENU starts a new conversation.
 */
public class ChatActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Assistant assistant;
    private String apiKey;
    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText input;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        scroll = new ScrollView(this);
        transcript = new LinearLayout(this);
        transcript.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(transcript);
        scroll.setFocusable(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        input = new EditText(this);
        input.setHint("Ask a question, press OK");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                send();
                return true;
            }
            return false;
        });
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String key = resolveKey();
        if (key == null) {
            input.setEnabled(false);
            transcript.removeAllViews();
            add("The AI assistant is not set up yet.\n\nThe phone's admin can turn it on in "
                    + "KosherGuard → AI assistant.", false);
            return;
        }
        input.setEnabled(true);
        if (!key.equals(apiKey)) {
            apiKey = key;
            assistant = new Assistant(key);
            transcript.removeAllViews();
            add("Shalom! Ask me anything — I keep answers short and clean.", false);
        }
        input.requestFocus();
    }

    /** Key from KosherGuard's managed configuration; if the phone is unmanaged, from local settings. */
    private String resolveKey() {
        RestrictionsManager rm = getSystemService(RestrictionsManager.class);
        Bundle r = rm != null ? rm.getApplicationRestrictions() : null;
        if (r != null && r.getBoolean("managed", false)) {
            String k = r.getString("api_key", "");
            return k == null || k.trim().isEmpty() ? null : k.trim();
        }
        SharedPreferences sp = getSharedPreferences("ai", Context.MODE_PRIVATE);
        String k = sp.getString("api_key", "");
        if (k.isEmpty()) {
            promptForLocalKey(sp);
            return null;
        }
        return k;
    }

    private void promptForLocalKey(SharedPreferences sp) {
        EditText e = new EditText(this);
        e.setHint("sk-ant-...");
        e.setSingleLine(true);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Claude API key")
                .setMessage("This phone is not managed by KosherGuard. Enter a Claude API key to use the assistant.")
                .setView(e)
                .setPositiveButton("Save", (d, w) -> {
                    sp.edit().putString("api_key", e.getText().toString().trim()).apply();
                    onResume();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && input.hasFocus()) {
            send();
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SOFT_LEFT) && assistant != null) {
            assistant.reset();
            transcript.removeAllViews();
            add("New conversation.", false);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && input.hasFocus()) {
            scroll.smoothScrollBy(0, -scroll.getHeight() / 2);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && input.hasFocus()) {
            scroll.smoothScrollBy(0, scroll.getHeight() / 2);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void send() {
        String q = input.getText().toString().trim();
        if (q.isEmpty() || busy || assistant == null) return;
        busy = true;
        input.setText("");
        add(q, true);
        TextView pending = add("…", false);
        Assistant a = assistant;
        worker.execute(() -> {
            String answer = a.ask(q);
            runOnUiThread(() -> {
                pending.setText(answer);
                busy = false;
                scrollToEnd();
            });
        });
    }

    private TextView add(String text, boolean fromUser) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tv.setTextColor(fromUser ? 0xFF9AD0FF : Color.WHITE);
        if (fromUser) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextIsSelectable(false);
        int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        tv.setPadding(pad, pad, pad, pad);
        transcript.addView(tv);
        scrollToEnd();
        return tv;
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
