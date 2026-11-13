package com.aurora.light.ui;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

import com.aurora.light.R;

/** 带薄荷→紫→粉横向渐变的标题文字。 */
public class GradientTextView extends TextView {

    public GradientTextView(Context context) {
        super(context);
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GradientTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void applyGradient() {
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        int mint = getResources().getColor(R.color.mint, getContext().getTheme());
        int violet = getResources().getColor(R.color.violet, getContext().getTheme());
        int pink = getResources().getColor(R.color.pink, getContext().getTheme());
        getPaint().setShader(new LinearGradient(
                0f, 0f, width, 0f,
                new int[]{mint, violet, pink},
                new float[]{0f, 0.58f, 1f},
                Shader.TileMode.CLAMP));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyGradient();
    }
}
