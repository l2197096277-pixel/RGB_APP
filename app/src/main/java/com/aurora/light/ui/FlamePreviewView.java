package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 焰火预览：用 fire field 算法生成向上、无规则的火焰，而不是横向波浪。
 */
public class FlamePreviewView extends View {

    private static final int COLS = 64;
    private static final int ROWS = 64;
    private static final int MAX_HEAT = 36;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Random random = new Random();
    private final int[] heat = new int[COLS * ROWS];
    private final int[] palette = new int[MAX_HEAT + 1];
    private final int[] pixels = new int[COLS * ROWS];
    private final android.graphics.Rect srcRect = new android.graphics.Rect();
    private final android.graphics.Rect dstRect = new android.graphics.Rect();
    private Bitmap bitmap;
    private int flameColor = 0xFFFF9500;
    private boolean seeded;

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
        bitmap = Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888);
        buildPalette();
        paint.setFilterBitmap(true);
    }

    public void setFlameColor(int color) {
        this.flameColor = color;
        buildPalette();
        invalidate();
    }

    private void buildPalette() {
        for (int i = 0; i <= MAX_HEAT; i++) {
            float t = i / (float) MAX_HEAT;
            if (t <= 0.03f) {
                palette[i] = Color.TRANSPARENT;
                continue;
            }
            // 低处深色/半透明，高处亮色，整体使用当前选择的颜色。
            int warm = blend(flameColor, 0xFFFFD27A, Math.max(0f, 0.55f - t));
            int hot = blend(warm, 0xFFFFFFFF, Math.max(0f, t - 0.55f) * 1.7f);
            int alpha = (int) (255 * Math.min(1f, t * 2.6f));
            palette[i] = (hot & 0x00FFFFFF) | (alpha << 24);
        }
    }

    private void seed() {
        for (int i = 0; i < heat.length; i++) heat[i] = 0;
        for (int x = 0; x < COLS; x++) {
            heat[(ROWS - 1) * COLS + x] = MAX_HEAT;
            if (random.nextBoolean()) heat[(ROWS - 2) * COLS + x] = MAX_HEAT - 2;
        }
        seeded = true;
    }

    private void updateFire() {
        for (int y = 1; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int src = y * COLS + x;
                int value = heat[src];
                if (value == 0) {
                    heat[src - COLS] = 0;
                    continue;
                }
                int decay = random.nextInt(4);
                int dstX = x + random.nextInt(3) - 1;
                if (dstX < 0) dstX = 0;
                if (dstX >= COLS) dstX = COLS - 1;
                int dst = (y - 1) * COLS + dstX;
                heat[dst] = Math.max(0, value - decay);
            }
        }
        for (int x = 0; x < COLS; x++) {
            heat[(ROWS - 1) * COLS + x] = MAX_HEAT;
            if (random.nextBoolean()) heat[(ROWS - 2) * COLS + x] = MAX_HEAT - 1;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (!seeded) seed();
        updateFire();
        for (int i = 0; i < heat.length; i++) {
            pixels[i] = palette[Math.min(MAX_HEAT, Math.max(0, heat[i]))];
        }
        bitmap.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS);

        // 火苗通常只占下半部分，裁掉顶部空行后铺满预览高度。
        int top = ROWS - 1;
        for (int y = 0; y < ROWS; y++) {
            boolean hasFire = false;
            for (int x = 0; x < COLS; x++) {
                if (heat[y * COLS + x] > 1) {
                    hasFire = true;
                    break;
                }
            }
            if (hasFire) {
                top = y;
                break;
            }
        }
        if (top >= ROWS - 2) top = ROWS - 2;
        srcRect.set(0, top, COLS, ROWS);
        dstRect.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
        postInvalidateDelayed(55L);
    }

    private static int blend(int from, int to, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f));
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f));
        int b = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * f));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
