package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** 可点选的颜色圆点，选中后显示白色描边和外发光。 */
public class ColorDotView extends View {

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int color = 0xFF5BF0D0;
    private boolean selected;

    public ColorDotView(Context context) {
        super(context);
        init();
    }

    public ColorDotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2f));
        ringPaint.setColor(0xFFFFFFFF);
        highlightPaint.setColor(0x4DFFFFFF);
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(100L).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
            }
            return false;
        });
    }

    public void setDotColor(int color) {
        this.color = color;
        invalidate();
    }

    public int getDotColor() {
        return color;
    }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        invalidate();
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) / 2f - dp(5f);
        if (radius <= 0f) {
            return;
        }

        if (selected) {
            glowPaint.setColor((color & 0x00FFFFFF) | 0x4D000000);
            canvas.drawCircle(cx, cy, radius + dp(4f), glowPaint);
            canvas.drawCircle(cx, cy, radius + dp(2f), ringPaint);
        }

        dotPaint.setColor(color);
        canvas.drawCircle(cx, cy, radius, dotPaint);
        canvas.drawCircle(cx - radius * 0.32f, cy - radius * 0.34f,
                radius * 0.26f, highlightPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
