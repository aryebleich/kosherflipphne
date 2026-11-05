package com.kosherflip.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** The on-screen pointer for keypad phones without a touch screen. */
final class CursorView extends View {
    float x, y;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float size;

    CursorView(Context c) {
        super(c);
        size = 22 * c.getResources().getDisplayMetrics().density;
        fill.setColor(Color.WHITE);
        stroke.setColor(Color.BLACK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * c.getResources().getDisplayMetrics().density);
        setFocusable(false);
        setClickable(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (x == 0 && y == 0) {
            x = w / 2f;
            y = h / 3f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        arrow.reset();
        arrow.moveTo(x, y);
        arrow.lineTo(x, y + size);
        arrow.lineTo(x + size * 0.28f, y + size * 0.75f);
        arrow.lineTo(x + size * 0.5f, y + size * 1.1f);
        arrow.lineTo(x + size * 0.62f, y + size * 1.02f);
        arrow.lineTo(x + size * 0.42f, y + size * 0.68f);
        arrow.lineTo(x + size * 0.75f, y + size * 0.68f);
        arrow.close();
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, stroke);
    }
}
