package com.aurora.light.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 音乐律动子页的动态频谱条，跟随 FFT 跳动。 */
public class MusicBarsView extends View {

    private static final int COUNT = 28;
    private final float[] levels = new float[COUNT];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private LinearGradient gradient;

    public MusicBarsView(Context context) {
        super(context);
        init();
    }

    public MusicBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setLevels(float[] values) {
        if (values == null) {
            clear();
            return;
        }
        for (int i = 0; i < COUNT; i++) {
            float target = i < values.length ? values[i] : 0f;
            levels[i] = Math.max(target, levels[i] * 0.72f);
        }
        invalidate();
    }

    public void clear() {
        for (int i = 0; i < COUNT; i++) levels[i] *= 0.55f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        gradient = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (gradient == null) {
            gradient = new LinearGradient(0, 0, w, 0,
                    new int[]{0xFFFF7A7A, 0xFFFFC46B, 0xFF5BF0D0, 0xFF7B8CFF},
                    null, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
        }
        float gap = w * 0.012f;
        float barWidth = (w - gap * (COUNT - 1)) / COUNT;
        if (barWidth < 1f) barWidth = 1f;
        for (int i = 0; i < COUNT; i++) {
            float value = Math.max(0.05f, Math.min(1f, levels[i]));
            float barHeight = Math.max(h * 0.08f, h * value);
            float left = i * (barWidth + gap);
            float top = h - barHeight;
            rect.set(left, top, left + barWidth, h);
            canvas.drawRoundRect(rect, barWidth / 2f, barWidth / 2f, paint);
        }
        postInvalidateDelayed(40L);
    }
}
