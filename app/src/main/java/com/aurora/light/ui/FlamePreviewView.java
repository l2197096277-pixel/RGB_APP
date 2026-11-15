package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 火焰灯颜色预览：随当前颜色平滑跳动的火焰。 */
public class FlamePreviewView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int flameColor = 0xFFFF9500;
    private float phase;

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
    }

    public void setFlameColor(int color) {
        this.flameColor = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        paint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Color.TRANSPARENT, (flameColor & 0x00FFFFFF) | 0x33000000,
                        (flameColor & 0x00FFFFFF) | 0xAA000000, flameColor},
                new float[]{0f, 0.25f, 0.68f, 1f}, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(0, h);
        for (int layer = 0; layer < 3; layer++) {
            float base = h * (0.58f + layer * 0.08f);
            float amp = h * (0.13f + layer * 0.04f);
            path.reset();
            path.moveTo(0, h);
            path.lineTo(0, base);
            for (int x = 0; x <= w; x += 8) {
                float t = (float) x / Math.max(1, w);
                double wave = Math.sin(t * Math.PI * 2 * (1.4 + layer * 0.4) + phase + layer)
                        + 0.45 * Math.sin(t * Math.PI * 6 + phase * 1.8 + layer);
                float y = (float) (base - amp * wave - h * 0.04);
                path.lineTo(x, y);
            }
            path.lineTo(w, h);
            path.close();
            paint.setAlpha(layer == 0 ? 120 : (layer == 1 ? 180 : 255));
            canvas.drawPath(path, paint);
        }
        phase += 0.08f;
        postInvalidateDelayed(33L);
    }
}
