package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

/** 下载进度动态圆环。 */
public class DownloadPulseView extends View {

    private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int progress;
    private float phase;
    private int colorA = 0xFFFF7A7A;
    private int colorB = 0xFF4F7CFF;
    private int colorC = 0xFF5BF0D0;

    public DownloadPulseView(Context context) {
        super(context);
        init();
    }

    public DownloadPulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        background.setStyle(Paint.Style.STROKE);
        background.setStrokeWidth(dp(7));
        background.setColor(0xFFE7EEF7);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(dp(7));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setProgress(int value) {
        progress = Math.max(0, Math.min(100, value));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dp(9);
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(rect, 0, 360, false, background);
        progressPaint.setShader(new SweepGradient(cx, cy,
                new int[]{colorA, colorC, colorB, colorA}, null));
        canvas.drawArc(rect, -90 + phase, Math.max(4f, progress * 3.6f), false, progressPaint);
        double angle = Math.toRadians(-90 + phase + Math.max(4f, progress * 3.6f));
        float dx = (float) (cx + Math.cos(angle) * radius);
        float dy = (float) (cy + Math.sin(angle) * radius);
        dotPaint.setColor(0xFFFFFFFF);
        dotPaint.setShadowLayer(dp(8), 0, 0, colorB);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        canvas.drawCircle(dx, dy, dp(5), dotPaint);
        phase += 1.2f;
        postInvalidateDelayed(16L);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
