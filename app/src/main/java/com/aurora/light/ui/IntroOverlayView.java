package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * 启动时的 RGB 灯带进入动画：三团彩色柔光 + 一条亮起的灯带 + AURORA 字样。
 * 约 0.9 秒，结束后由 Activity 淡出移除。
 */
public class IntroOverlayView extends View {

    private final Paint bgPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long start = SystemClock.uptimeMillis();
    private float w;
    private float h;

    public IntroOverlayView(Context context) {
        super(context);
        setClickable(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        super.onSizeChanged(width, height, oldw, oldh);
        w = width;
        h = height;
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF05070F, 0xFF0A1020, 0xFF05070F},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        barPaint.setShader(new LinearGradient(
                w * 0.12f, 0, w * 0.88f, 0,
                new int[]{0xFF5BF0D0, 0xFF8B7BFF, 0xFFFF7AC6},
                null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (w <= 0 || h <= 0) {
            return;
        }
        float elapsed = (SystemClock.uptimeMillis() - start) / 1250f;
        float p = Math.min(1f, elapsed);
        float ease = 1f - (1f - p) * (1f - p);

        canvas.drawRect(0, 0, w, h, bgPaint);

        float cx = w / 2f;
        float cy = h * 0.47f;
        float radius = w * (0.40f + 0.05f * ease);
        drawGlow(canvas, w * (0.20f + 0.12f * ease), h * 0.26f, radius, 0x605BF0D0);
        drawGlow(canvas, w * (0.82f - 0.10f * ease), h * 0.32f, radius * 0.9f, 0x608B7BFF);
        drawGlow(canvas, w * 0.52f, h * 0.76f, radius * 0.8f, 0x50FF7AC6);

        // 灯带：从中心向两边点亮
        float barW = w * 0.62f * ease;
        float barH = dp(7f);
        float top = cy + w * 0.20f;
        barPaint.setAlpha((int) (255 * Math.min(1f, ease * 1.4f)));
        canvas.drawRoundRect(cx - barW / 2f, top, cx + barW / 2f, top + barH,
                barH / 2f, barH / 2f, barPaint);

        // 灯带上的 7 个光点
        for (int i = 0; i < 7; i++) {
            float x = cx - barW / 2f + barW * i / 6f;
            glowPaint.setShader(new RadialGradient(x, top + barH / 2f, dp(14f),
                    new int[]{0xCCFFFFFF, 0x22FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, top + barH / 2f, dp(14f), glowPaint);
        }

        textPaint.setTextSize(w * 0.072f);
        textPaint.setAlpha((int) (255 * Math.min(1f, ease * 1.8f)));
        canvas.drawText("AURORA", cx, cy - w * 0.10f, textPaint);
        textPaint.setTextSize(w * 0.025f);
        textPaint.setAlpha((int) (190 * Math.min(1f, ease * 1.8f)));
        canvas.drawText("R G B   L I G H T", cx, cy + w * 0.02f, textPaint);

        if (p < 1f) {
            postInvalidateOnAnimation();
        }
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius, int color) {
        glowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{color, color & 0x00FFFFFF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
