package com.aurora.light.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aurora.light.R;

/** 光效模式卡片。 */
public class ModeTileView extends LinearLayout {

    private final FrameLayout iconBox;
    private final ImageView icon;
    private final TextView title;
    private final TextView subtitle;
    private int mode;

    public ModeTileView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        setClickable(true);
        setFocusable(true);
        setBackgroundResource(R.drawable.bg_mode_tile);

        iconBox = new FrameLayout(context);
        LayoutParams boxParams = new LayoutParams(dp(34), dp(34));
        iconBox.setLayoutParams(boxParams);
        iconBox.setBackgroundResource(R.drawable.bg_mode_icon);

        icon = new ImageView(context);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        iconParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconParams);
        icon.setImageTintList(colorStateList(R.color.mode_icon));
        iconBox.addView(icon);
        addView(iconBox);

        title = new TextView(context);
        LayoutParams titleParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(6);
        title.setLayoutParams(titleParams);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(colorStateList(R.color.mode_title));
        addView(title);

        subtitle = new TextView(context);
        LayoutParams subParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(1);
        subtitle.setLayoutParams(subParams);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        subtitle.setTextColor(colorStateList(R.color.mode_subtitle));
        addView(subtitle);

        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                animate().scaleX(0.965f).scaleY(0.965f).setDuration(100L).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
            }
            return false;
        });
    }

    public void bind(int mode, String titleText, String subtitleText, int iconRes) {
        this.mode = mode;
        title.setText(titleText);
        subtitle.setText(subtitleText);
        icon.setImageResource(iconRes);
        setContentDescription(titleText + "，" + subtitleText);
    }

    public int getMode() {
        return mode;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        iconBox.setSelected(selected);
        icon.setSelected(selected);
        title.setSelected(selected);
        subtitle.setSelected(selected);
    }

    private ColorStateList colorStateList(int res) {
        return getResources().getColorStateList(res, getContext().getTheme());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
