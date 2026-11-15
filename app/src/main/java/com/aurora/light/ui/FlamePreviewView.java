package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** 焰火颜色预览：向上的、不规则的火苗/火星动画。 */
public class FlamePreviewView extends View {

    private static final int PARTICLES = 52;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float[] x = new float[PARTICLES];
    private final float[] y = new float[PARTICLES];
    private final float[] speed = new float[PARTICLES];
    private final float[] size = new float[PARTICLES];
    private final float[] life = new float[PARTICLES];
    private final float[] drift = new float[PARTICLES];
    private int flameColor = 0xFFFF9500;
    private float time;
    private boolean ready;

    public FlamePreviewView(Context context) {
        super(context);
        init();
    }

    public FlamePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
    }

    public void setFlameColor(int color) {
        this.flameColor = color;
        invalidate();
    }

    private void seedParticles(int w, int h) {
        for (int i = 0; i < PARTICLES; i++) {
            resetParticle(i, w, h, true);
        }
        ready = true;
    }

    private void resetParticle(int index, int w, int h, boolean initial) {
        x[index] = w * (0.24f + random.nextFloat() * 0.52f);
        y[index] = h * (initial ? random.nextFloat() : (0.82f + random.nextFloat() * 0.16f));
        speed[index] = h * (0.010f + random.nextFloat() * 0.016f);
        size[index] = h * (0.025f + random.nextFloat() * 0.055f);
        life[index] = 0.55f + random.nextFloat() * 0.45f;
        drift[index] = w * (0.006f + random.nextFloat() * 0.012f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        seedParticles(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (!ready) seedParticles(w, h);

        int base = flameColor;
        int glowColor = (base & 0x00FFFFFF) | 0x22000000;
        paint.setShader(new RadialGradient(w * 0.5f, h * 1.02f, h * 0.86f,
                new int[]{glowColor, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        time += 0.12f;
        for (int i = 0; i < PARTICLES; i++) {
            y[i] -= speed[i];
            x[i] += (float) Math.sin(time * 0.9f + i * 1.7f) * drift[i] * 0.16f;
            life[i] -= 0.012f;
            if (life[i] <= 0f || y[i] < -size[i]) {
                resetParticle(i, w, h, false);
            }
            float alpha = Math.max(0f, Math.min(1f, life[i]));
            float radius = size[i] * (0.55f + alpha * 0.8f);
            int color = blend(base, 0xFFFFFFFF, 0.12f + 0.45f * alpha);
            paint.setColor((color & 0x00FFFFFF) | (int) (alpha * 220) << 24);
            canvas.drawCircle(x[i], y[i], radius, paint);
            glow.setColor((base & 0x00FFFFFF) | (int) (alpha * 52) << 24);
            canvas.drawCircle(x[i], y[i], radius * 1.8f, glow);
        }
        postInvalidateDelayed(33L);
    }

    private static int blend(int from, int to, float f) {
        int a = (int) ((from >>> 24) + (((to >>> 24) - (from >>> 24)) * f));
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
