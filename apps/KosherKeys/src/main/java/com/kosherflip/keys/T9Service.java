package com.kosherflip.keys;

import android.content.res.Configuration;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;

/**
 * Multi-tap T9 keyboard for keypad phones: English, Hebrew and Yiddish.
 *   2-9  letters (press again to cycle)     0  space (hold: 0)
 *   1    punctuation                        #  next mode: abc Abc ABC אבג ייִד 123
 *   *    (hold) symbols are on 1            any key held: its digit
 * A small strip shows the current mode. Number fields always get digits.
 */
public class T9Service extends InputMethodService {
    private static final long COMMIT_DELAY_MS = 900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private T9Layout.Mode mode = T9Layout.Mode.CAPS_FIRST;
    private int lastKey = -1;
    private int presses;
    private boolean numericField;
    private TextView strip;

    private final Runnable commit = this::commitComposing;

    @Override
    public boolean onEvaluateInputViewShown() {
        // Only a mode strip on keypad phones; touch-only phones keep their normal keyboard.
        Configuration c = getResources().getConfiguration();
        return c.keyboard == Configuration.KEYBOARD_12KEY || super.onEvaluateInputViewShown();
    }

    @Override
    public View onCreateInputView() {
        strip = new TextView(this);
        strip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        strip.setTextColor(Color.WHITE);
        strip.setBackgroundColor(0xFF202020);
        int p = (int) (4 * getResources().getDisplayMetrics().density);
        strip.setPadding(p * 2, p, p * 2, p);
        updateStrip();
        return strip;
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        numericField = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        lastKey = -1;
        presses = 0;
        updateStrip();
    }

    @Override
    public void onFinishInput() {
        commitComposing();
        super.onFinishInput();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || numericField) return super.onKeyDown(keyCode, event);
        int digit = digit(keyCode);

        if (digit >= 0) {
            if (event.getRepeatCount() > 0) {
                // Long press: replace the letter with the digit itself.
                if (event.getRepeatCount() == 1) {
                    handler.removeCallbacks(commit);
                    ic.setComposingText(String.valueOf(digit), 1);
                    commitComposing();
                }
                return true;
            }
            if (mode == T9Layout.Mode.NUMBERS) {
                commitComposing();
                ic.commitText(String.valueOf(digit), 1);
                return true;
            }
            if (digit == lastKey) {
                presses++;
            } else {
                commitComposing();
                lastKey = digit;
                presses = 1;
            }
            ic.setComposingText(T9Layout.letter(mode, digit, presses, shouldCapitalize(ic)), 1);
            handler.removeCallbacks(commit);
            handler.postDelayed(commit, COMMIT_DELAY_MS);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_POUND) {
            commitComposing();
            mode = mode.next();
            updateStrip();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL && lastKey >= 0) {
            // Delete the letter being composed.
            handler.removeCallbacks(commit);
            ic.setComposingText("", 1);
            ic.finishComposingText();
            lastKey = -1;
            presses = 0;
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            commitComposing();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!numericField && (digit(keyCode) >= 0 || keyCode == KeyEvent.KEYCODE_POUND)) return true;
        return super.onKeyUp(keyCode, event);
    }

    private boolean shouldCapitalize(InputConnection ic) {
        if (mode != T9Layout.Mode.CAPS_FIRST) return false;
        EditorInfo ei = getCurrentInputEditorInfo();
        int caps = ei == null ? 0 : ic.getCursorCapsMode(ei.inputType);
        if (caps != 0) return true;
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        if (before == null || before.length() == 0) return true;
        String b = before.toString().trim();
        return b.isEmpty() || b.endsWith(".") || b.endsWith("?") || b.endsWith("!");
    }

    private void commitComposing() {
        handler.removeCallbacks(commit);
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && lastKey >= 0) ic.finishComposingText();
        lastKey = -1;
        presses = 0;
    }

    private void updateStrip() {
        if (strip == null) return;
        strip.setText(numericField ? "123" : mode.label + "    # mode · 0 space · 1 .,?! · hold = digit");
        strip.setTextDirection(mode.isRtl() ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    static int digit(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) return keyCode - KeyEvent.KEYCODE_0;
        return -1;
    }
}
