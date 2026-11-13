package com.aurora.light.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.aurora.light.R;

/**
 * 主开关：多层光环 + 渐变扫光 + 中心玻璃圆盘 + 手绘电源符号。
 * 开启时有极缓慢的呼吸光晕，关闭时完全停止刷新以省电。
 */
public class PowerButtonView extends View {

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private boolean powered = true;
    private float progress = 1f;
    private int accentColor;
    private ValueAnimator animator;

    private float cx;
    private float cy;
    private float radius;
    private RadialGradient glowShader;
    private RadialGradient discShader;
    private SweepGradient sweepShader;

    public PowerButtonView(Context context) {
        super(context);
        init();
    }

    public PowerButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PowerButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        accentColor = getResources().getColor(R.color.mint, getContext().getTheme());
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(3.2f));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1f));
        ringPaint.setColor(0x1FFFFFFF);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(3.4f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        updateContentDescription();
    }

    public void setPowered(boolean on) {
        if (powered == on && Math.abs(progress - (on ? 1f : 0f)) < 0.01f) {
            return;
        }
        powered = on;
        if (animator != null) {
            animator.cancel();
        }
        float from = progress;
        float to = on ? 1f : 0f;
        animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(420L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
        updateContentDescription();
        invalidate();
    }

    public boolean isPowered() {
        return powered;
    }

    public void setAccentColor(int color) {
        if (accentColor == color) {
            return;
        }
        accentColor = color;
        if (radius > 0f) {
            buildShaders();
        }
        invalidate();
    }

    private void updateContentDescription() {
        setContentDescription(powered ? "关闭灯光" : "开启灯光");
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) / 2f - dp(5f);
        float ringRadius = radius * 0.78f;
        arcRect.set(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius);
        buildShaders();
    }

    private void buildShaders() {
        if (radius <= 0f) {
            return;
        }
        glowShader = new RadialGradient(
                cx, cy, radius * 0.98f,
                new int[]{
                        withAlpha(accentColor, 112),
                        withAlpha(accentColor, 36),
                        0x00000000
                },
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP);
        glowPaint.setShader(glowShader);

        discShader = new RadialGradient(
                cx, cy - radius * 0.10f, radius * 0.70f,
                new int[]{0x30FFFFFF, 0x12FFFFFF, 0x08FFFFFF},
                new float[]{0f, 0.62f, 1f},
                Shader.TileMode.CLAMP);
        discPaint.setShader(discShader);

        sweepShader = new SweepGradient(
                cx, cy,
                new int[]{accentColor, 0xFF8B7BFF, 0xFFFF7AC6, accentColor},
                new float[]{0f, 0.34f, 0.68f, 1f});
        Matrix matrix = new Matrix();
        matrix.setRotate(-90f, cx, cy);
        sweepShader.setLocalMatrix(matrix);
        arcPaint.setShader(sweepShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0f) {
            return;
        }
        float t = SystemClock.uptimeMillis() / 1000f;
        float pulse = powered
                ? 0.5f + 0.5f * (float) Math.sin(t * 1.35f)
                : 0f;

        // 外层呼吸光晕
        glowPaint.setAlpha((int) (powered ? 130 + 90 * pulse : 0));
        canvas.drawCircle(cx, cy, radius * 0.98f, glowPaint);

        // 轨道
        ringPaint.setAlpha(powered ? 210 : 120);
        canvas.drawCircle(cx, cy, radius * 0.78f, ringPaint);

        // 渐变扫光
        arcPaint.setAlpha((int) (255 * Math.min(1f, progress * 1.15f)));
        canvas.drawArc(arcRect, 120f, 300f * progress, false, arcPaint);

        // 中心玻璃圆盘
        canvas.drawCircle(cx, cy, radius * 0.66f, discPaint);
        ringPaint.setAlpha(120);
        canvas.drawCircle(cx, cy, radius * 0.66f, ringPaint);

        // 手绘电源符号
        float iconRadius = radius * 0.265f;
        RectF iconRect = new RectF(
                cx - iconRadius, cy - iconRadius,
                cx + iconRadius, cy + iconRadius);
        iconPaint.setColor(powered ? Color.WHITE : 0xFF8D96AF);
        iconPaint.setAlpha(powered ? 240 : 190);
        canvas.drawArc(iconRect, -60f, 300f, false, iconPaint);
        canvas.drawLine(cx, cy - iconRadius * 1.12f,
                cx, cy - iconRadius * 0.16f, iconPaint);

        if (powered && isShown()) {
            postInvalidateDelayed(33L);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().scaleX(0.965f).scaleY(0.965f).setDuration(120L).start();
                return true;
            case MotionEvent.ACTION_CANCEL:
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
                return true;
            case MotionEvent.ACTION_UP:
                animate().scaleX(1f).scaleY(1f).setDuration(180L).start();
                if (event.getX() >= 0 && event.getX() <= getWidth()
                        && event.getY() >= 0 && event.getY() <= getHeight()) {
                    performClick();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
        }
        super.onDetachedFromWindow();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
