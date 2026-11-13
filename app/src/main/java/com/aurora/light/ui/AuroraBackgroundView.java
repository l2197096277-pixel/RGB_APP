package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 深色极光背景：三团缓慢漂移的柔光 + 细微星点 + 底部渐暗。
 * 仅在可见时以约 30fps 刷新，退到后台自动停止。
 */
public class AuroraBackgroundView extends View {

    private static final int STAR_COUNT = 46;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix[] blobMatrices = new Matrix[4];
    private final RadialGradient[] blobShaders = new RadialGradient[4];
    private final float[] blobRadiusFactor = {1f, 0.92f, 0.88f, 0.72f};
    private final int[] blobColors = {
            0x605BF0D0, 0x5C8B7BFF, 0x46FF7AC6, 0x3D59C2FF
    };

    private float width;
    private float height;
    private float[] starX;
    private float[] starY;
    private float[] starSize;
    private float[] starPhase;

    public AuroraBackgroundView(Context context) {
        super(context);
        init();
    }

    public AuroraBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AuroraBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        starPaint.setColor(Color.WHITE);
        for (int i = 0; i < blobMatrices.length; i++) {
            blobMatrices[i] = new Matrix();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;

        basePaint.setShader(new LinearGradient(
                0f, 0f, 0f, h,
                new int[]{0xFF05070F, 0xFF090D1A, 0xFF05070F},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));

        float max = Math.max(w, h);
        for (int i = 0; i < blobColors.length; i++) {
            blobShaders[i] = new RadialGradient(
                    0f, 0f, max * 0.56f * blobRadiusFactor[i],
                    new int[]{blobColors[i], (blobColors[i] & 0x00FFFFFF)},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP);
        }

        vignettePaint.setShader(new LinearGradient(
                0f, h * 0.68f, 0f, h,
                new int[]{0x0005070F, 0xCC05070F},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP));

        Random random = new Random(20261108L);
        starX = new float[STAR_COUNT];
        starY = new float[STAR_COUNT];
        starSize = new float[STAR_COUNT];
        starPhase = new float[STAR_COUNT];
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextFloat() * w;
            starY[i] = random.nextFloat() * h * 0.78f;
            starSize[i] = dp(0.7f) + random.nextFloat() * dp(1.1f);
            starPhase[i] = random.nextFloat() * 6.283f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = width;
        float h = height;
        if (w <= 0f || h <= 0f) {
            return;
        }

        canvas.drawRect(0f, 0f, w, h, basePaint);

        float t = SystemClock.uptimeMillis() / 1000f;
        float max = Math.max(w, h);
        float radius = max * 0.56f;

        drawBlob(canvas, t, 0,
                w * (0.14f + 0.07f * (float) Math.sin(t * 0.17f)),
                h * (0.10f + 0.06f * (float) Math.cos(t * 0.13f)),
                radius);
        drawBlob(canvas, t, 1,
                w * (0.92f + 0.07f * (float) Math.cos(t * 0.15f + 1.2f)),
                h * (0.26f + 0.07f * (float) Math.sin(t * 0.11f + 0.4f)),
                radius * 0.92f);
        drawBlob(canvas, t, 2,
                w * (0.40f + 0.09f * (float) Math.sin(t * 0.10f + 2.2f)),
                h * (0.80f + 0.05f * (float) Math.cos(t * 0.14f + 1.0f)),
                radius * 0.88f);
        drawBlob(canvas, t, 3,
                w * (0.78f + 0.08f * (float) Math.sin(t * 0.12f + 4.0f)),
                h * (0.58f + 0.08f * (float) Math.cos(t * 0.09f + 2.0f)),
                radius * 0.72f);

        // 星点
        for (int i = 0; i < STAR_COUNT; i++) {
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f
                    * (float) Math.sin(t * 0.65f + starPhase[i]));
            int alpha = (int) (42 * twinkle);
            starPaint.setAlpha(alpha);
            canvas.drawCircle(starX[i], starY[i], starSize[i], starPaint);
        }

        canvas.drawRect(0f, h * 0.68f, w, h, vignettePaint);

        if (isShown()) {
            postInvalidateDelayed(33L);
        }
    }

    private void drawBlob(Canvas canvas, float t, int index, float cx, float cy, float radius) {
        if (blobShaders[index] == null) {
            return;
        }
        Matrix matrix = blobMatrices[index];
        matrix.reset();
        matrix.setTranslate(cx, cy);
        blobShaders[index].setLocalMatrix(matrix);
        blobPaint.setShader(blobShaders[index]);
        canvas.drawCircle(cx, cy, radius, blobPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
