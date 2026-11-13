/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.animation.TimeInterpolator
 *  android.annotation.TargetApi
 *  android.app.Activity
 *  android.content.ClipData
 *  android.content.ClipboardManager
 *  android.content.Context
 *  android.content.Intent
 *  android.content.SharedPreferences
 *  android.graphics.Rect
 *  android.graphics.Typeface
 *  android.graphics.drawable.Drawable
 *  android.graphics.drawable.GradientDrawable
 *  android.location.LocationManager
 *  android.net.Uri
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.SystemClock
 *  android.util.Log
 *  android.view.MotionEvent
 *  android.view.View
 *  android.view.View$OnClickListener
 *  android.view.ViewGroup$LayoutParams
 *  android.view.Window
 *  android.view.animation.DecelerateInterpolator
 *  android.widget.Button
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.GridLayout
 *  android.widget.GridLayout$LayoutParams
 *  android.widget.LinearLayout
 *  android.widget.LinearLayout$LayoutParams
 *  android.widget.ScrollView
 *  android.widget.SeekBar
 *  android.widget.SeekBar$OnSeekBarChangeListener
 *  android.widget.TextView
 *  android.widget.Toast
 *  android.widget.ViewFlipper
 *  com.aurora.light.R$color
 *  com.aurora.light.R$drawable
 *  com.aurora.light.R$id
 *  com.aurora.light.R$layout
 *  com.aurora.light.R$string
 */
package com.aurora.light;

import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import com.aurora.light.AuroraBleManager;
import com.aurora.light.AuroraProtocol;
import com.aurora.light.AuroraState;
import com.aurora.light.BleDevice;
import com.aurora.light.CrashLog;
import com.aurora.light.R;
import com.aurora.light.ScanSheet;
import com.aurora.light.ui.ColorDotView;
import com.aurora.light.ui.IntroOverlayView;
import com.aurora.light.ui.ModeTileView;
import com.aurora.light.ui.PowerButtonView;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

public class MainActivity
extends Activity
implements AuroraBleManager.Listener,
ScanSheet.Host {
    private static final int REQ_BLE_PERMISSIONS = 1001;
    private static final int REQ_ENABLE_BLUETOOTH = 1002;
    private static final int[] PRESET_COLORS = AuroraProtocol.COLOR_RGB;
    private static final String[] COLOR_NAMES = AuroraProtocol.COLOR_NAMES;
    private static final int[] MODE_VALUES = new int[]{1, 2, 3, 4};
    private static final String[] MODE_TITLES = new String[]{"流光跑马", "呼吸", "律动", "火焰"};
    private static final String[] MODE_SUBTITLES = new String[]{"色彩流动", "轻柔明暗", "节奏跳动", "暖光摇曳"};
    private static final int[] MODE_ICONS = new int[]{R.drawable.ic_sparkle, R.drawable.ic_breathe, R.drawable.ic_music, R.drawable.ic_flame};
    private final AuroraState state = new AuroraState();
    private final ModeTileView[] modeTiles = new ModeTileView[4];
    private final ColorDotView[] colorDots = new ColorDotView[PRESET_COLORS.length];
    private SharedPreferences prefs;
    private AuroraBleManager ble;
    private ScanSheet scanSheet;
    private LinkState linkState = LinkState.DISCONNECTED;
    private boolean demoMode;
    private boolean brightnessDragging;
    private long lastDisconnectedHint;
    private long lastBrightnessPreview;
    private int activeMode;
    private int currentPage;
    private float touchDownX;
    private float touchDownY;
    private boolean swipeCandidate;
    private boolean autoConnectTried;
    private Toast toast;
    private PowerButtonView powerButton;
    private SeekBar brightnessSeek;
    private TextView brightnessLabel;
    private TextView powerLabel;
    private TextView statusText;
    private TextView connectButton;
    private TextView deviceName;
    private TextView hexLabel;
    private TextView demoHint;
    private TextView oneKeyOff;
    private TextView effectHint;
    private TextView versionLabel;
    private LinearLayout changelogHistoryList;
    private TextView changelogToggle;
    private TextView changelogCurrentVersion;
    private boolean changelogExpanded;
    private EditText ledCountInput;
    private TextView ledCountMinus;
    private TextView ledCountPlus;
    private TextView selfTestButton;
    private TextView customLedButton;
    private LinearLayout customPanel;
    private EditText customCountInput;
    private TextView customGenerate;
    private TextView customClear;
    private TextView customClose;
    private TextView customStatus;
    private GridLayout customGrid;
    private LinearLayout customColorRow;
    private final ColorDotView[] customColorDots = new ColorDotView[PRESET_COLORS.length];
    private int customTotal;
    private final SparseBooleanArray customSelected = new SparseBooleanArray();
    private boolean selfTestOn;
    private TextView[] speedChips;
    private LinearLayout statusChip;
    private View statusDot;
    private LinearLayout colorRow;
    private ViewFlipper pager;
    private TextView tabControl;
    private TextView tabEffects;
    private TextView tabProfile;
    private ScrollView devLogScroll;
    private LinearLayout devPanel;
    private TextView devLog;
    private boolean developerMode;
    private final ArrayDeque<String> devLines = new ArrayDeque();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA);

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String previousCrash = CrashLog.read((Context)this);
        if (previousCrash != null) {
            this.showCrashScreen(previousCrash);
            return;
        }
        try {
            this.startApp();
        }
        catch (Throwable t) {
            Log.e((String)"AuroraStartup", (String)"startup failed", (Throwable)t);
            CrashLog.save((Context)this, t);
            this.showCrashScreen(Log.getStackTraceString((Throwable)t));
        }
    }

    private void startApp() {
        this.prefs = this.getSharedPreferences("aurora_settings", 0);
        this.loadState();
        if (!this.state.power) {
            this.state.mode = 0;
            this.state.colorOn = false;
        }
        int n = this.activeMode = this.state.power ? this.state.mode : 0;
        if (this.activeMode != 0) {
            this.state.colorOn = false;
        }
        this.setupWindow();
        this.setContentView(R.layout.activity_main);
        this.bindViews();
        this.applyInsets();
        this.buildModeTiles();
        this.buildColorDots();
        this.buildCustomColorDots();
        this.buildChangelogHistory();
        this.bindActions();
        this.ble = new AuroraBleManager((Context)this, this);
        this.scanSheet = new ScanSheet(this, this);
        this.setPage(0);
        this.renderState();
        this.setLinkState(LinkState.DISCONNECTED, null);
        this.animateEntrance();
        this.showIntroAnimation();
        this.pager.postDelayed(this::tryAutoConnect, 1350L);
        this.pager.postDelayed(() -> UpdateManager.checkOnLaunch(this), 1800L);
    }

    private void tryAutoConnect() {
        if (this.autoConnectTried || this.ble == null || this.ble.isReady()
                || this.linkState == LinkState.CONNECTING) {
            return;
        }
        String address = this.prefs.getString("last_device_address", "");
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (!this.ble.hasPermission() || !this.isBleEnabled()) {
            return;
        }
        this.autoConnectTried = true;
        String name = this.prefs.getString("last_device", "幻彩灯");
        this.setLinkState(LinkState.CONNECTING, name);
        this.toast("正在自动连接上次设备…");
        this.ble.connectAddress(address, name);
    }

    private void showIntroAnimation() {
        FrameLayout root = (FrameLayout)this.findViewById(R.id.root);
        IntroOverlayView intro = new IntroOverlayView((Context)this);
        root.addView((View)intro, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        intro.setAlpha(1.0f);
        intro.animate().alpha(0.0f).setStartDelay(950L).setDuration(300L).withEndAction(() -> root.removeView((View)intro)).start();
    }

    private void showCrashScreen(String stack) {
        try {
            ScrollView scroll = new ScrollView((Context)this);
            scroll.setBackgroundColor(-16447729);
            scroll.setFillViewport(true);
            LinearLayout box = new LinearLayout((Context)this);
            box.setOrientation(1);
            box.setPadding(this.dp(20), this.dp(34), this.dp(20), this.dp(24));
            TextView title = new TextView((Context)this);
            title.setText((CharSequence)"幻彩灯控启动失败");
            title.setTextColor(-1);
            title.setTextSize(22.0f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            TextView hint = new TextView((Context)this);
            hint.setText((CharSequence)"请把下面的错误信息截图发给我，我会直接修。");
            hint.setTextColor(-6642237);
            hint.setTextSize(13.0f);
            hint.setPadding(0, this.dp(10), 0, this.dp(12));
            TextView body = new TextView((Context)this);
            body.setText((CharSequence)(stack == null ? "未知错误" : stack));
            body.setTextColor(-4669223);
            body.setTextSize(10.0f);
            body.setTypeface(Typeface.MONOSPACE);
            body.setTextIsSelectable(true);
            body.setPadding(0, this.dp(8), 0, this.dp(16));
            Button copy = new Button((Context)this);
            copy.setText((CharSequence)"复制错误信息");
            copy.setOnClickListener(v -> {
                ClipboardManager manager = (ClipboardManager)this.getSystemService("clipboard");
                if (manager != null) {
                    manager.setPrimaryClip(ClipData.newPlainText((CharSequence)"Aurora crash", (CharSequence)(stack == null ? "未知错误" : stack)));
                    this.toast("错误信息已复制");
                }
            });
            Button retry = new Button((Context)this);
            retry.setText((CharSequence)"清除记录并重试");
            retry.setOnClickListener(v -> {
                CrashLog.clear((Context)this);
                this.recreate();
            });
            box.addView((View)title);
            box.addView((View)hint);
            box.addView((View)body);
            box.addView((View)copy);
            box.addView((View)retry);
            scroll.addView((View)box);
            this.setContentView((View)scroll);
        }
        catch (Throwable fallbackError) {
            Log.e((String)"AuroraStartup", (String)"showCrashScreen failed", (Throwable)fallbackError);
        }
    }

    private void setupWindow() {
        Window window = this.getWindow();
        window.setStatusBarColor(0);
        window.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 30) {
            Api30.setupWindow(window);
        } else {
            window.getDecorView().setSystemUiVisibility(1792);
        }
    }

    private void bindViews() {
        this.powerButton = (PowerButtonView)this.findViewById(R.id.powerButton);
        this.brightnessSeek = (SeekBar)this.findViewById(R.id.brightnessSeek);
        this.brightnessLabel = (TextView)this.findViewById(R.id.brightnessLabel);
        this.powerLabel = (TextView)this.findViewById(R.id.powerLabel);
        this.statusText = (TextView)this.findViewById(R.id.statusText);
        this.statusChip = (LinearLayout)this.findViewById(R.id.statusChip);
        this.statusDot = this.findViewById(R.id.statusDot);
        this.connectButton = (TextView)this.findViewById(R.id.connectButton);
        this.deviceName = (TextView)this.findViewById(R.id.deviceName);
        this.hexLabel = (TextView)this.findViewById(R.id.hexLabel);
        this.demoHint = (TextView)this.findViewById(R.id.demoHint);
        this.colorRow = (LinearLayout)this.findViewById(R.id.colorRow);
        this.oneKeyOff = (TextView)this.findViewById(R.id.oneKeyOff);
        this.effectHint = (TextView)this.findViewById(R.id.effectHint);
        this.versionLabel = (TextView)this.findViewById(R.id.versionLabel);
        this.changelogHistoryList = (LinearLayout)this.findViewById(R.id.changelogHistoryList);
        this.changelogToggle = (TextView)this.findViewById(R.id.changelogToggle);
        this.changelogCurrentVersion = (TextView)this.findViewById(R.id.changelogCurrentVersion);
        this.ledCountInput = (EditText)this.findViewById(R.id.ledCountInput);
        this.ledCountMinus = (TextView)this.findViewById(R.id.ledCountMinus);
        this.ledCountPlus = (TextView)this.findViewById(R.id.ledCountPlus);
        this.selfTestButton = (TextView)this.findViewById(R.id.selfTestButton);
        this.customLedButton = (TextView)this.findViewById(R.id.customLedButton);
        this.customPanel = (LinearLayout)this.findViewById(R.id.customPanel);
        this.customCountInput = (EditText)this.findViewById(R.id.customCountInput);
        this.customGenerate = (TextView)this.findViewById(R.id.customGenerate);
        this.customClear = (TextView)this.findViewById(R.id.customClear);
        this.customClose = (TextView)this.findViewById(R.id.customClose);
        this.customStatus = (TextView)this.findViewById(R.id.customStatus);
        this.customGrid = (GridLayout)this.findViewById(R.id.customGrid);
        this.customColorRow = (LinearLayout)this.findViewById(R.id.customColorRow);
        this.pager = (ViewFlipper)this.findViewById(R.id.pager);
        this.tabControl = (TextView)this.findViewById(R.id.tabControl);
        this.tabEffects = (TextView)this.findViewById(R.id.tabEffects);
        this.tabProfile = (TextView)this.findViewById(R.id.tabProfile);
        this.devPanel = (LinearLayout)this.findViewById(R.id.devPanel);
        this.devLog = (TextView)this.findViewById(R.id.devLog);
        this.devLogScroll = (ScrollView)this.findViewById(R.id.devLogScroll);
        this.speedChips = new TextView[]{(TextView)this.findViewById(R.id.speedSlow), (TextView)this.findViewById(R.id.speedMid), (TextView)this.findViewById(R.id.speedFast)};
    }

    private void applyInsets() {
        View root = this.findViewById(R.id.root);
        int baseLeft = root.getPaddingLeft();
        int baseTop = root.getPaddingTop();
        int baseRight = root.getPaddingRight();
        int baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(baseLeft, baseTop + top, baseRight, baseBottom + bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void animateEntrance() {
        this.pager.setAlpha(0.0f);
        this.pager.setTranslationY((float)this.dp(18));
        this.pager.animate().alpha(1.0f).translationY(0.0f).setStartDelay(80L).setDuration(780L).setInterpolator((TimeInterpolator)new DecelerateInterpolator()).start();
    }

    private void buildModeTiles() {
        GridLayout grid = (GridLayout)this.findViewById(R.id.modeGrid);
        grid.removeAllViews();
        for (int i = 0; i < MODE_VALUES.length; ++i) {
            ModeTileView tile = new ModeTileView((Context)this);
            tile.bind(MODE_VALUES[i], MODE_TITLES[i], MODE_SUBTITLES[i], MODE_ICONS[i]);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = this.dp(88);
            params.columnSpec = GridLayout.spec((int)(i % 2), (float)1.0f);
            params.rowSpec = GridLayout.spec((int)(i / 2));
            params.setMargins(this.dp(5), this.dp(5), this.dp(5), this.dp(5));
            tile.setLayoutParams((ViewGroup.LayoutParams)params);
            tile.setOnClickListener(v -> this.selectMode(tile.getMode(), true));
            grid.addView((View)tile);
            this.modeTiles[i] = tile;
        }
    }

    private void buildColorDots() {
        this.colorRow.removeAllViews();
        for (int i = 0; i < PRESET_COLORS.length; ++i) {
            ColorDotView dot = new ColorDotView((Context)this);
            int presetColor = PRESET_COLORS[i];
            String colorName = COLOR_NAMES[i];
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, this.dp(46), 1.0f);
            dot.setLayoutParams((ViewGroup.LayoutParams)params);
            dot.setDotColor(presetColor);
            dot.setContentDescription("选择" + colorName);
            dot.setOnClickListener(v -> this.selectColor(presetColor, colorName));
            this.colorRow.addView((View)dot);
            this.colorDots[i] = dot;
        }
    }

    private void buildCustomColorDots() {
        this.customColorRow.removeAllViews();
        for (int i = 0; i < PRESET_COLORS.length; ++i) {
            ColorDotView dot = new ColorDotView((Context)this);
            int presetColor = PRESET_COLORS[i];
            String colorName = COLOR_NAMES[i];
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, this.dp(32), 1.0f);
            dot.setLayoutParams((ViewGroup.LayoutParams)params);
            dot.setDotColor(presetColor);
            dot.setContentDescription("自定义灯光选择" + colorName);
            dot.setOnClickListener(v -> this.selectCustomColor(presetColor, colorName));
            this.customColorRow.addView((View)dot);
            this.customColorDots[i] = dot;
        }
    }

    private void renderCustomColors() {
        for (ColorDotView dot : this.customColorDots) {
            if (dot == null) continue;
            dot.setSelected(this.state.colorOn && dot.getDotColor() == this.state.color);
        }
    }

    private void selectCustomColor(int color, String name) {
        if (!this.ensureControl("选择颜色")) {
            return;
        }
        this.activeMode = 0;
        this.state.mode = 0;
        if (this.state.colorOn && this.state.color == color) {
            this.state.colorOn = false;
            this.renderState();
            this.saveState();
            this.dispatch(AuroraProtocol.colorOff(), "关闭颜色");
            this.toast("已关闭灯光颜色");
            return;
        }
        this.state.color = color;
        this.state.colorOn = true;
        this.powerButton.setAccentColor(color);
        this.renderState();
        this.saveState();
        this.dispatch(AuroraProtocol.color(AuroraProtocol.colorIndex(color)), "颜色 · " + name);
        this.toast("已选择颜色：" + name);
    }

    private void bindActions() {
        this.powerButton.setOnClickListener(v -> {
            if (!this.ensureConnected("开关灯光")) {
                return;
            }
            v.performHapticFeedback(4);
            this.togglePower();
        });
        this.brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || !((MainActivity)MainActivity.this).state.power) {
                    return;
                }
                ((MainActivity)MainActivity.this).state.brightness = progress;
                MainActivity.this.brightnessLabel.setText((CharSequence)String.format(Locale.CHINA, "%d%%", progress));
                long now = SystemClock.elapsedRealtime();
                if (now - MainActivity.this.lastBrightnessPreview >= 120L) {
                    MainActivity.this.lastBrightnessPreview = now;
                    if (!MainActivity.this.demoMode && MainActivity.this.ble != null && MainActivity.this.ble.isReady()) {
                        MainActivity.this.ble.send(AuroraProtocol.brightness(progress));
                    }
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                MainActivity.this.brightnessDragging = true;
                MainActivity.this.lastBrightnessPreview = 0L;
                if (!MainActivity.this.isControllable()) {
                    MainActivity.this.toast("请先连接蓝牙，再调节亮度");
                } else if (!((MainActivity)MainActivity.this).state.power) {
                    MainActivity.this.toast("灯光未开启，请先打开总开关再调节亮度");
                }
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                MainActivity.this.brightnessDragging = false;
                if (!((MainActivity)MainActivity.this).state.power) {
                    return;
                }
                ((MainActivity)MainActivity.this).state.brightness = seekBar.getProgress();
                MainActivity.this.dispatch(AuroraProtocol.brightness(((MainActivity)MainActivity.this).state.brightness), "亮度 " + ((MainActivity)MainActivity.this).state.brightness + "%");
                MainActivity.this.saveState();
            }
        });
        this.connectButton.setOnClickListener(v -> this.onConnectButton());
        this.demoHint.setOnClickListener(v -> this.enterDemoMode());
        this.statusChip.setOnClickListener(v -> {
            if (this.linkState == LinkState.DISCONNECTED) {
                this.openScanSheet();
            } else if (this.linkState == LinkState.CONNECTED) {
                this.toast("已连接 · " + this.deviceName.getText());
            } else if (this.linkState == LinkState.DEMO) {
                this.toast("当前为演示模式，操作仅在本机模拟");
            } else {
                this.toast("正在连接，请稍候");
            }
        });
        View.OnClickListener speedClick = v -> {
            if (!this.ensureControl("调节速度")) {
                return;
            }
            int speed = v.getId() == R.id.speedSlow ? 30 : (v.getId() == R.id.speedFast ? 90 : 60);
            this.state.speed = speed;
            this.renderSpeed();
            this.dispatch(AuroraProtocol.speed(speed), "速度 " + speed + "%");
            this.saveState();
        };
        for (TextView chip : this.speedChips) {
            chip.setOnClickListener(speedClick);
        }
        this.ledCountMinus.setOnClickListener(v -> this.changeLedCount(-1));
        this.ledCountPlus.setOnClickListener(v -> this.changeLedCount(1));
        this.ledCountInput.setOnClickListener(v -> this.ledCountInput.selectAll());
        this.ledCountInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                this.applyLedCountInput();
                this.ledCountInput.clearFocus();
                return true;
            }
            return false;
        });
        this.ledCountInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                this.applyLedCountInput();
            }
        });
        this.selfTestButton.setOnClickListener(v -> this.toggleSelfTest());
        this.customLedButton.setOnClickListener(v -> this.openCustomPanel());
        this.customGenerate.setOnClickListener(v -> this.generateCustomGrid());
        this.customClear.setOnClickListener(v -> this.clearCustomSetup(true));
        this.customClose.setOnClickListener(v -> this.closeCustomPanel());
        this.oneKeyOff.setOnClickListener(v -> this.oneKeyOff());
        this.tabControl.setOnClickListener(v -> this.setPage(0));
        this.tabEffects.setOnClickListener(v -> this.setPage(1));
        this.tabProfile.setOnClickListener(v -> this.setPage(2));
        this.versionLabel.setOnLongClickListener(v -> {
            v.performHapticFeedback(0);
            this.toggleDeveloperMode();
            return true;
        });
        this.findViewById(R.id.changelogHeader).setOnClickListener(v -> this.toggleChangelog());
        this.changelogToggle.setOnClickListener(v -> this.toggleChangelog());
        this.findViewById(R.id.devClear).setOnClickListener(v -> {
            this.devLines.clear();
            this.devLog.setText((CharSequence)"等待发送…");
        });
    }

    private void togglePower() {
        if (this.state.power) {
            this.state.power = false;
            this.state.colorOn = false;
            this.activeMode = 0;
            this.state.mode = 0;
            this.selfTestOn = false;
            this.dispatch(AuroraProtocol.power(false), "关灯");
            this.renderState();
            this.saveState();
            this.toast("灯光已关闭");
        } else {
            this.state.power = true;
            if (this.state.brightness <= 0) {
                this.state.brightness = 72;
            }
            this.dispatch(AuroraProtocol.power(true), "开灯");
            this.renderState();
            this.saveState();
            if (!this.demoMode && this.ble != null && this.ble.isReady()) {
                this.ble.sendBurstDelayed(AuroraProtocol.brightness(this.state.brightness), 3, 100L, 500L);
            }
            this.toast("灯光已开启");
        }
    }

    private void oneKeyOff() {
        if (!this.ensureConnected("一键关闭")) {
            return;
        }
        if (!this.state.power) {
            this.toast("灯光已经是关闭状态");
            return;
        }
        this.dispatch(AuroraProtocol.allOff(), "一键关闭");
        this.state.power = false;
        this.state.colorOn = false;
        this.activeMode = 0;
        this.state.mode = 0;
        this.selfTestOn = false;
        this.renderState();
        this.saveState();
        this.toast("已一键关闭所有灯光和光效");
    }

    private boolean isControllable() {
        return this.demoMode || this.linkState == LinkState.CONNECTED && this.ble != null && this.ble.isReady();
    }

    private boolean ensureConnected(String action) {
        if (this.demoMode) {
            return true;
        }
        if (this.linkState != LinkState.CONNECTED || this.ble == null || !this.ble.isReady()) {
            this.toast("请先连接蓝牙，再" + action);
            return false;
        }
        return true;
    }

    private boolean ensureControl(String action) {
        if (!this.ensureConnected(action)) {
            return false;
        }
        if (!this.state.power) {
            this.toast("灯光未开启，请先打开总开关再" + action);
            return false;
        }
        return true;
    }

    private void changeLedCount(int delta) {
        if (!this.ensureControl("调节亮灯数量")) {
            return;
        }
        int next = AuroraProtocol.clamp(this.state.ledCount + delta, 0, 255);
        if (next == this.state.ledCount) {
            this.toast("已经到达边界：" + next);
            return;
        }
        this.state.ledCount = next;
        this.ledCountInput.clearFocus();
        this.updateLedCountLabel();
        if (this.demoMode) {
            this.toast("演示模式 · 亮灯数量 " + next);
        } else if (this.ble != null && this.ble.isReady()) {
            // 0x07 是相对命令，直接发 1 次；重复 3 次会多加/多减 3 颗。
            this.ble.send(AuroraProtocol.ledCountStep(delta > 0));
        } else {
            this.hintNotConnected();
        }
        this.saveState();
    }

    private void updateLedCountLabel() {
        if (this.ledCountInput != null && !this.ledCountInput.hasFocus()) {
            this.ledCountInput.setText((CharSequence)String.valueOf(this.state.ledCount));
        }
    }

    private void applyLedCountInput() {
        if (this.ledCountInput == null) {
            return;
        }
        String raw = this.ledCountInput.getText() == null
                ? "" : this.ledCountInput.getText().toString().trim();
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            this.updateLedCountLabel();
            return;
        }
        value = AuroraProtocol.clamp(value, 0, 255);
        if (value == this.state.ledCount) {
            this.updateLedCountLabel();
            return;
        }
        if (!this.ensureControl("设置亮灯数量")) {
            this.updateLedCountLabel();
            return;
        }
        this.state.ledCount = value;
        this.updateLedCountLabel();
        this.saveState();
        this.dispatch(AuroraProtocol.ledCountSet(value), "亮灯数量 " + value);
        this.toast("亮灯数量已设置为 " + value);
    }

    private void toggleSelfTest() {
        if (!this.ensureControl("运行自检")) {
            return;
        }
        this.selfTestOn = !this.selfTestOn;
        this.renderState();
        this.dispatch(AuroraProtocol.selfTest(this.selfTestOn), this.selfTestOn ? "开启自检" : "关闭自检");
        this.toast(this.selfTestOn ? "已发送自检" : "已关闭自检");
    }

    private void openCustomPanel() {
        if (!this.ensureControl("进入自定义灯光")) {
            return;
        }
        if (this.activeMode != 0) {
            int oldMode = this.activeMode;
            this.activeMode = 0;
            this.state.mode = 0;
            if (this.ble != null && this.ble.isReady()) {
                this.ble.sendBurst(AuroraProtocol.mode(oldMode, false), 3, 100L);
            }
        }
        this.renderState();
        this.customPanel.setVisibility(0);
        this.customCountInput.setText((CharSequence)(this.customTotal > 0 ? String.valueOf(this.customTotal) : ""));
        if (this.customTotal > 0) {
            this.buildCustomGrid();
        } else {
            this.customGrid.removeAllViews();
            this.customStatus.setText((CharSequence)"先输入产品灯珠总数，再点“生成”");
            this.customCountInput.requestFocus();
        }
    }

    private void closeCustomPanel() {
        this.customPanel.setVisibility(8);
        // 退出会发送关闭码，因此清空高亮；产品灯珠总数保留到下次重连。
        this.customSelected.clear();
        if (this.demoMode) {
            this.toast("演示模式 · 已退出自定义灯光");
            return;
        }
        if (this.ble != null && this.ble.isReady()) {
            // 排在所有尚未发完的单灯指令之后，避免关闭码先到。
            this.ble.sendOrderedBurst(AuroraProtocol.customExit());
        } else {
            this.hintNotConnected();
        }
    }

    private void generateCustomGrid() {
        String raw = this.customCountInput.getText() == null ? "" : this.customCountInput.getText().toString().trim();
        int total;
        try {
            total = Integer.parseInt(raw);
        }
        catch (NumberFormatException e) {
            this.toast("请输入 1–255 之间的灯珠数量");
            return;
        }
        if (total < 1 || total > 255) {
            this.toast("灯珠数量需要在 1–255 之间");
            return;
        }
        this.customTotal = total;
        this.customSelected.clear();
        this.customCountInput.clearFocus();
        this.buildCustomGrid();
        this.toast("已生成 " + total + " 颗灯");
    }

    private void buildCustomGrid() {
        this.customGrid.removeAllViews();
        int screenWidth = this.getResources().getDisplayMetrics().widthPixels;
        int cellWidth = (screenWidth - this.dp(32) - this.dp(32)) / 4;
        cellWidth = Math.max(this.dp(64), cellWidth);
        for (int i = 1; i <= this.customTotal; ++i) {
            final int index = i;
            final TextView button = new TextView((Context)this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellWidth;
            params.height = this.dp(58);
            params.setMargins(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
            button.setLayoutParams((ViewGroup.LayoutParams)params);
            button.setGravity(17);
            button.setText((CharSequence)String.format(Locale.CHINA, "%02d", i));
            button.setTextSize(13.0f);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setClickable(true);
            button.setFocusable(true);
            button.setPadding(this.dp(4), this.dp(4), this.dp(4), this.dp(4));
            button.setSelected(this.customSelected.get(i));
            this.styleCustomLed(button, i, this.customSelected.get(i));
            button.setContentDescription("第 " + i + " 颗灯");
            button.setOnClickListener(v -> this.toggleCustomLed(index, button));
            this.customGrid.addView((View)button);
        }
        this.updateCustomStatus();
    }

    private void toggleCustomLed(int index, TextView button) {
        if (!this.ensureControl("控制自定义灯光")) {
            return;
        }
        boolean on = !this.customSelected.get(index);
        if (on) {
            this.customSelected.put(index, true);
        } else {
            this.customSelected.delete(index);
        }
        button.setSelected(on);
        this.styleCustomLed(button, index, on);
        this.updateCustomStatus();
        if (this.demoMode) {
            this.toast("演示模式 · 第 " + index + " 颗" + (on ? "点亮" : "关闭"));
            return;
        }
        if (this.ble != null && this.ble.isReady()) {
            this.ble.sendOrderedBurst(AuroraProtocol.customLed(index, on));
        } else {
            this.hintNotConnected();
        }
    }

    private void clearCustomSetup(boolean sendClose) {
        this.customTotal = 0;
        this.customSelected.clear();
        this.customGrid.removeAllViews();
        this.customCountInput.setText((CharSequence)"");
        this.customStatus.setText((CharSequence)"已清除，请重新输入灯珠总数");
        if (sendClose && !this.demoMode && this.ble != null && this.ble.isReady()) {
            this.ble.sendOrderedBurst(AuroraProtocol.customExit());
        }
    }

    private void resetCustomState() {
        this.customTotal = 0;
        this.customSelected.clear();
        this.customGrid.removeAllViews();
        this.customCountInput.setText((CharSequence)"");
        this.customPanel.setVisibility(8);
        this.customStatus.setText((CharSequence)"先输入产品灯珠总数，再点“生成”");
    }

    private void updateCustomStatus() {
        if (this.customTotal <= 0) {
            return;
        }
        this.customStatus.setText((CharSequence)String.format(Locale.CHINA,
                "共 %d 颗，已亮 %d 颗 · 点击切换亮/灭", this.customTotal, this.customSelected.size()));
    }

    private void buildChangelogHistory() {
        this.versionLabel.setText((CharSequence)("v" + BuildConfig.VERSION_NAME));
        this.changelogCurrentVersion.setText((CharSequence)("v" + BuildConfig.VERSION_NAME + " · 2026-11-14 01:30"));
        this.changelogHistoryList.removeAllViews();
        String[][] history = {
                {"v1.1.0", "2026-11-14"},
                {"v1.0.9", "2026-11-13"},
                {"v1.0.8", "2026-11-08"},
                {"v1.0.7", "2026-11-08"},
                {"v1.0.6", "2026-11-08"},
                {"v1.0.5", "2026-11-08"},
                {"v1.0.4", "2026-11-08"},
                {"v1.0.3", "2026-11-08"},
                {"v1.0.2", "2026-11-08"},
                {"v1.0.1", "2026-11-08"},
                {"v1.0.0", "2026-11-08"}
        };
        for (int i = 0; i < history.length; i++) {
            LinearLayout row = new LinearLayout((Context)this);
            row.setOrientation(1);
            row.setPadding(0, this.dp(12), 0, this.dp(12));

            TextView version = new TextView((Context)this);
            version.setText((CharSequence)(history[i][0] + " · " + history[i][1]));
            version.setTextColor(this.getColor(R.color.mint_soft));
            version.setTextSize(12.0f);
            version.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

            TextView body = new TextView((Context)this);
            body.setText((CharSequence)"修复了已知问题");
            body.setTextColor(this.getColor(R.color.text_secondary));
            body.setTextSize(11.0f);
            body.setPadding(0, this.dp(5), 0, 0);

            row.addView((View)version);
            row.addView((View)body);
            this.changelogHistoryList.addView((View)row);

            if (i < history.length - 1) {
                View divider = new View((Context)this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.dp(1));
                lp.topMargin = this.dp(2);
                divider.setLayoutParams((ViewGroup.LayoutParams)lp);
                divider.setBackgroundColor(0x1AFFFFFF);
                this.changelogHistoryList.addView(divider);
            }
        }
    }

    private void toggleChangelog() {
        this.changelogExpanded = !this.changelogExpanded;
        this.changelogHistoryList.setVisibility(this.changelogExpanded ? 0 : 8);
        this.changelogToggle.setText((CharSequence)(this.changelogExpanded ? "收起历史" : "查看历史"));
    }

    private void styleCustomLed(TextView button, int index, boolean on) {
        float hue = (index * 360.0f / Math.max(1, this.customTotal)) % 360.0f;
        int accent = Color.HSVToColor(new float[]{hue, 0.72f, 1.0f});
        int normalStart = this.blendColor(0xFF10182A, accent, 0.22f);
        int normalEnd = this.blendColor(0xFF070B14, accent, 0.12f);
        int selectedStart = this.blendColor(accent, 0xFFFFFFFF, 0.16f);
        int selectedEnd = this.blendColor(accent, 0xFF6B5BFF, 0.42f);

        GradientDrawable background = new GradientDrawable(
                on ? GradientDrawable.Orientation.TL_BR : GradientDrawable.Orientation.LEFT_RIGHT,
                on ? new int[]{selectedStart, selectedEnd} : new int[]{normalStart, normalEnd});
        background.setCornerRadius(this.dp(15));
        background.setStroke(this.dp(on ? 2 : 1),
                on ? accent : (this.blendColor(accent, 0xFF2A3248, 0.5f) & 0x00FFFFFF) | 0x66000000);
        button.setBackground((Drawable)background);
        button.setTextColor(on ? Color.WHITE : this.blendColor(0xFFC5CEE6, accent, 0.30f));
        button.setElevation(this.dp(on ? 6 : 1));
    }

    private int blendColor(int from, int to, float amount) {
        float f = Math.max(0.0f, Math.min(1.0f, amount));
        int a = (int)((from >>> 24) + (((to >>> 24) - (from >>> 24)) * f));
        int r = (int)(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f));
        int g = (int)(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f));
        int b = (int)((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void toggleDeveloperMode() {
        this.developerMode = !this.developerMode;
        this.updateDevPanelVisibility();
        if (this.developerMode && this.currentPage == 2) {
            this.renderDevLog();
            this.toast("开发者模式已开启");
        } else if (!this.developerMode) {
            this.toast("已退出开发者模式");
        }
    }

    private void updateDevPanelVisibility() {
        boolean show = this.developerMode && this.currentPage == 2;
        this.devPanel.setVisibility(show ? 0 : 8);
    }

    private void appendDevLog(String line) {
        this.devLines.addLast(line);
        while (this.devLines.size() > 120) {
            this.devLines.removeFirst();
        }
        if (this.developerMode && this.currentPage == 2) {
            this.renderDevLog();
            this.devLogScroll.post(() -> this.devLogScroll.fullScroll(130));
        }
    }

    private void renderDevLog() {
        if (this.devLines.isEmpty()) {
            this.devLog.setText((CharSequence)"等待发送…");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : this.devLines) {
            sb.append(line).append('\n');
        }
        this.devLog.setText((CharSequence)sb.toString().trim());
    }

    private void setPage(int page) {
        this.currentPage = Math.max(0, Math.min(2, page));
        this.pager.setDisplayedChild(this.currentPage);
        int selectedColor = this.getColor(R.color.mint);
        int normalColor = this.getColor(R.color.text_secondary);
        this.tabControl.setTextColor(this.currentPage == 0 ? selectedColor : normalColor);
        this.tabEffects.setTextColor(this.currentPage == 1 ? selectedColor : normalColor);
        this.tabProfile.setTextColor(this.currentPage == 2 ? selectedColor : normalColor);
        this.tabControl.setAlpha(this.currentPage == 0 ? 1.0f : 0.72f);
        this.tabEffects.setAlpha(this.currentPage == 1 ? 1.0f : 0.72f);
        this.tabProfile.setAlpha(this.currentPage == 2 ? 1.0f : 0.72f);
        this.updateDevPanelVisibility();
        if (this.developerMode && this.currentPage == 2) {
            this.renderDevLog();
        }
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == 0) {
            this.touchDownX = event.getRawX();
            this.touchDownY = event.getRawY();
            this.swipeCandidate = !this.isPointInside((View)this.brightnessSeek, this.touchDownX, this.touchDownY);
        } else if (event.getActionMasked() == 1 && this.swipeCandidate) {
            float dx = event.getRawX() - this.touchDownX;
            float dy = event.getRawY() - this.touchDownY;
            if (Math.abs(dx) > (float)this.dp(55) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                this.setPage(this.currentPage + (dx < 0.0f ? 1 : -1));
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean isPointInside(View view, float x, float y) {
        if (view == null || !view.isShown()) {
            return false;
        }
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.contains((int)x, (int)y);
    }

    public void onBackPressed() {
        if (this.customPanel.getVisibility() == 0) {
            this.closeCustomPanel();
        } else if (this.currentPage != 0) {
            this.setPage(0);
        } else {
            super.onBackPressed();
        }
    }

    private void selectMode(int mode, boolean send) {
        if (!this.ensureControl("切换光效")) {
            return;
        }
        if (this.activeMode == mode) {
            this.activeMode = 0;
            this.state.mode = 0;
            this.renderState();
            this.saveState();
            this.dispatch(AuroraProtocol.mode(mode, false), "关闭光效");
            this.toast("已关闭「" + this.modeName(mode) + "」");
            return;
        }
        int previous = this.activeMode;
        this.activeMode = mode;
        this.state.mode = mode;
        this.state.colorOn = false;
        this.renderState();
        this.saveState();
        if (this.demoMode) {
            this.toast("演示模式 · 已模拟 " + this.modeName(mode));
            return;
        }
        if (this.ble != null && this.ble.isReady()) {
            if (previous != 0) {
                this.ble.sendBurst(AuroraProtocol.mode(previous, false), 3, 100L);
                this.ble.sendBurstDelayed(AuroraProtocol.mode(mode, true), 3, 100L, 600L);
            } else {
                this.ble.sendBurst(AuroraProtocol.mode(mode, true), 3, 100L);
            }
            this.toast("已开启「" + this.modeName(mode) + "」");
        } else {
            this.hintNotConnected();
        }
    }

    private void selectColor(int color2, String name) {
        if (!this.ensureControl("选择颜色")) {
            return;
        }
        if (this.activeMode != 0) {
            this.toast("「" + this.modeName(this.activeMode) + "」正在运行，请先关闭光效再选择颜色");
            return;
        }
        if (this.state.colorOn && this.state.color == color2) {
            this.state.colorOn = false;
            this.renderState();
            this.saveState();
            this.dispatch(AuroraProtocol.colorOff(), "关闭颜色");
            this.toast("已关闭灯光颜色");
            return;
        }
        this.state.color = color2;
        this.state.colorOn = true;
        this.state.mode = 0;
        this.powerButton.setAccentColor(color2);
        this.renderState();
        this.saveState();
        this.dispatch(AuroraProtocol.color(AuroraProtocol.colorIndex(color2)), "颜色 · " + name);
        this.toast("已选择颜色：" + name);
    }

    private void onConnectButton() {
        if (this.linkState == LinkState.CONNECTED) {
            this.ble.disconnect();
            this.demoMode = false;
            this.setLinkState(LinkState.DISCONNECTED, null);
            this.toast("已断开连接");
        } else if (this.linkState == LinkState.DEMO) {
            this.demoMode = false;
            this.setLinkState(LinkState.DISCONNECTED, null);
            this.toast("已退出演示模式");
        } else if (this.linkState == LinkState.CONNECTING) {
            this.ble.disconnect();
            this.setLinkState(LinkState.DISCONNECTED, null);
            this.toast("已取消连接");
        } else {
            this.openScanSheet();
        }
    }

    private void openScanSheet() {
        if (this.ble == null) {
            return;
        }
        if (!this.ble.isBleSupported()) {
            this.toast("这台手机不支持低功耗蓝牙");
            return;
        }
        this.scanSheet.show();
    }

    private void enterDemoMode() {
        if (this.ble != null) {
            this.ble.disconnect();
        }
        this.demoMode = true;
        this.setLinkState(LinkState.DEMO, "演示灯光");
        this.toast("已进入演示模式，可自由体验所有控制");
    }

    private void dispatch(byte[] frame, String label) {
        if (this.demoMode) {
            this.toast("演示模式 · 已模拟" + label);
            return;
        }
        if (this.ble != null && this.ble.isReady()) {
            this.ble.sendBurst(frame, 3, 100L);
        } else {
            this.hintNotConnected();
        }
    }

    private void hintNotConnected() {
        long now = System.currentTimeMillis();
        if (now - this.lastDisconnectedHint > 2500L) {
            this.lastDisconnectedHint = now;
            this.toast("尚未连接 · 连接后会自动同步当前设置");
        }
    }

    private void syncState() {
        if (this.ble != null && this.ble.isReady()) {
            this.ble.sendBurst(AuroraProtocol.sync(this.state), 3, 100L);
        }
    }

    private void renderState() {
        boolean connected = this.isControllable();
        boolean on = this.state.power;
        boolean enabled = connected && on;
        this.powerButton.setPowered(connected && on);
        this.powerButton.setAccentColor(this.state.color);
        this.renderPowerText();
        this.brightnessSeek.setEnabled(enabled);
        this.brightnessSeek.setAlpha(enabled ? 1.0f : 0.35f);
        this.brightnessSeek.setProgress(enabled ? this.state.brightness : 0);
        this.brightnessLabel.setText((CharSequence)String.format(Locale.CHINA, "%d%%", enabled ? this.state.brightness : 0));
        this.brightnessLabel.setTextColor(this.getColor(enabled ? R.color.mint_soft : R.color.text_tertiary));
        this.oneKeyOff.setAlpha(connected ? 1.0f : 0.45f);
        this.ledCountMinus.setAlpha(enabled ? 1.0f : 0.45f);
        this.ledCountPlus.setAlpha(enabled ? 1.0f : 0.45f);
        this.ledCountInput.setAlpha(enabled ? 1.0f : 0.45f);
        this.ledCountInput.setEnabled(enabled);
        this.updateLedCountLabel();
        this.selfTestButton.setSelected(this.selfTestOn && connected && on);
        this.selfTestButton.setAlpha(!connected ? 0.35f : (on ? 1.0f : 0.45f));
        this.selfTestButton.setTextColor(this.getColor(this.selfTestOn && connected && on ? R.color.mint_soft : R.color.text_secondary));
        this.hexLabel.setText((CharSequence)AuroraProtocol.colorHex(this.state.color));
        this.renderModes();
        this.renderColors();
        this.renderCustomColors();
        this.renderSpeed();
    }

    private void renderPowerText() {
        boolean connected = this.isControllable();
        if (!connected) {
            this.powerLabel.setText((CharSequence)"灯光未连接");
            this.powerLabel.setTextColor(this.getColor(R.color.text_tertiary));
            return;
        }
        this.powerLabel.setText(this.state.power ? R.string.power_on : R.string.power_off);
        this.powerLabel.setTextColor(this.getColor(this.state.power ? R.color.text_primary : R.color.text_tertiary));
    }

    private void renderModes() {
        boolean connected = this.isControllable();
        boolean on = this.state.power;
        for (ModeTileView tile : this.modeTiles) {
            if (tile == null) continue;
            tile.setSelected(connected && on && tile.getMode() == this.activeMode);
            tile.setAlpha(!connected ? 0.35f : (on ? 1.0f : 0.45f));
        }
    }

    private void renderColors() {
        boolean connected = this.isControllable();
        boolean available = connected && this.state.power && this.activeMode == 0;
        for (ColorDotView dot : this.colorDots) {
            if (dot == null) continue;
            dot.setSelected(available && this.state.colorOn && dot.getDotColor() == this.state.color);
        }
        this.colorRow.setAlpha(!connected ? 0.35f : (available ? 1.0f : 0.55f));
        this.hexLabel.setText((CharSequence)AuroraProtocol.colorHex(this.state.color));
        if (!connected) {
            this.effectHint.setText((CharSequence)"请先连接蓝牙");
        } else if (!this.state.power) {
            this.effectHint.setText((CharSequence)"请先开启灯光");
        } else if (this.activeMode != 0) {
            this.effectHint.setText((CharSequence)("「" + this.modeName(this.activeMode) + "」运行中，关闭光效后可选颜色"));
        } else if (this.state.colorOn) {
            this.effectHint.setText((CharSequence)"再次点击当前颜色可关闭颜色");
        } else {
            this.effectHint.setText((CharSequence)"光效已关闭，可选择颜色");
        }
    }

    private void renderSpeed() {
        boolean connected = this.isControllable();
        boolean on = this.state.power;
        boolean slow = this.state.speed < 45;
        boolean mid = this.state.speed >= 45 && this.state.speed < 75;
        boolean fast = this.state.speed >= 75;
        this.speedChips[0].setSelected(connected && on && slow);
        this.speedChips[1].setSelected(connected && on && mid);
        this.speedChips[2].setSelected(connected && on && fast);
        for (TextView chip : this.speedChips) {
            chip.setAlpha(!connected ? 0.35f : (on ? 1.0f : 0.45f));
        }
    }

    private void setLinkState(LinkState newState, String name) {
        String text;
        int dotColor;
        int textColor;
        int stroke;
        int fill;
        this.linkState = newState;
        switch (newState) {
            case CONNECTING: {
                fill = 445348863;
                stroke = 1720417279;
                textColor = -3159553;
                dotColor = -7635969;
                text = this.getString(R.string.status_connecting);
                break;
            }
            case CONNECTED: {
                fill = 442233040;
                stroke = 1717301456;
                textColor = this.getColor(R.color.mint);
                dotColor = this.getColor(R.color.mint);
                text = this.getString(R.string.status_on);
                break;
            }
            case DEMO: {
                fill = 452969579;
                stroke = 1728037995;
                textColor = this.getColor(R.color.amber);
                dotColor = this.getColor(R.color.amber);
                text = this.getString(R.string.status_demo);
                break;
            }
            default: {
                fill = 0x14FFFFFF;
                stroke = 0x22FFFFFF;
                textColor = this.getColor(R.color.text_secondary);
                dotColor = -10852992;
                text = this.getString(R.string.status_off);
            }
        }
        this.statusText.setText((CharSequence)text);
        this.statusText.setTextColor(textColor);
        this.setRoundedBackground((View)this.statusChip, fill, stroke, this.dp(100));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(1);
        dot.setColor(dotColor);
        this.statusDot.setBackground((Drawable)dot);
        if (newState == LinkState.CONNECTED) {
            this.connectButton.setText(R.string.connected_tap_disconnect);
            this.connectButton.setBackgroundResource(R.drawable.bg_connected_button);
            this.connectButton.setTextColor(this.getColor(R.color.mint));
            this.tintButtonIcon(this.getColor(R.color.mint));
            this.demoHint.setVisibility(8);
            this.deviceName.setText((CharSequence)(name == null ? "幻彩灯" : name));
        } else if (newState == LinkState.DEMO) {
            this.connectButton.setText(R.string.demo_exit);
            this.connectButton.setBackgroundResource(R.drawable.bg_demo_button);
            this.connectButton.setTextColor(this.getColor(R.color.amber));
            this.tintButtonIcon(this.getColor(R.color.amber));
            this.demoHint.setVisibility(8);
            this.deviceName.setText((CharSequence)"演示灯光");
        } else if (newState == LinkState.CONNECTING) {
            this.connectButton.setText((CharSequence)"正在连接…");
            this.connectButton.setBackgroundResource(R.drawable.bg_secondary_button);
            this.connectButton.setTextColor(-3159553);
            this.tintButtonIcon(-3159553);
            this.demoHint.setVisibility(8);
            this.deviceName.setText((CharSequence)(name == null ? "幻彩灯" : name));
        } else {
            this.connectButton.setText(R.string.connect);
            this.connectButton.setBackgroundResource(R.drawable.bg_primary_button);
            this.connectButton.setTextColor(-16379879);
            this.tintButtonIcon(-16379879);
            this.demoHint.setVisibility(0);
            this.deviceName.setText(R.string.light_name);
        }
        this.renderState();
    }

    private void tintButtonIcon(int color2) {
        Drawable[] drawables = this.connectButton.getCompoundDrawablesRelative();
        if (drawables[0] != null) {
            drawables[0].setTint(color2);
        }
    }

    private void setRoundedBackground(View view, int fill, int stroke, int radiusPx) {
        GradientDrawable drawable2 = new GradientDrawable();
        drawable2.setShape(0);
        drawable2.setColor(fill);
        drawable2.setCornerRadius((float)radiusPx);
        drawable2.setStroke(this.dp(1), stroke);
        view.setBackground((Drawable)drawable2);
    }

    private String modeName(int mode) {
        if (mode >= 0 && mode < AuroraProtocol.MODE_NAMES.length) {
            return AuroraProtocol.MODE_NAMES[mode];
        }
        return "光效";
    }

    private void saveState() {
        this.prefs.edit().putBoolean("power", this.state.power).putInt("brightness", this.state.brightness).putInt("mode", this.state.mode).putInt("speed", this.state.speed).putInt("color", this.state.color).putBoolean("color_on", this.state.colorOn).putInt("led_count_v2", this.state.ledCount).apply();
    }

    private void loadState() {
        this.state.power = this.prefs.getBoolean("power", false);
        this.state.brightness = MainActivity.clamp(this.prefs.getInt("brightness", 72), 0, 100);
        this.state.mode = MainActivity.clamp(this.prefs.getInt("mode", 1), 0, 4);
        this.state.speed = MainActivity.clamp(this.prefs.getInt("speed", 60), 0, 100);
        this.state.color = this.prefs.getInt("color", -16711681) | 0xFF000000;
        this.state.colorOn = this.prefs.getBoolean("color_on", true);
        this.state.ledCount = MainActivity.clamp(this.prefs.getInt("led_count_v2", 1), 0, 255);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void onScanResult(BleDevice device) {
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.addDevice(device);
        }
    }

    @Override
    public void onScanFinished() {
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onScanFinished();
        }
    }

    @Override
    public void onScanError(String message) {
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onScanError(message);
        } else {
            this.toast(message);
        }
    }

    @Override
    public void onBleConnected(String name) {
        this.setLinkState(LinkState.CONNECTING, name);
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onConnecting(name);
        }
    }

    @Override
    public void onBleReady() {
        this.demoMode = false;
        this.resetCustomState();
        this.selfTestOn = false;
        // 连接成功不自动开灯：强制回到关闭状态，由用户自己按总开关。
        this.state.power = false;
        this.activeMode = 0;
        this.state.mode = 0;
        this.renderState();
        this.saveState();
        this.setLinkState(LinkState.CONNECTED, this.ble.currentName());
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onConnected();
        }
        this.prefs.edit().putString("last_device", this.ble.currentName())
                .putString("last_device_address", this.ble.currentAddress()).apply();
        this.toast("已连接 · " + this.ble.currentName());
        this.syncState();
    }

    @Override
    public void onBleDisconnected(String reason) {
        if (this.demoMode) {
            return;
        }
        this.setLinkState(LinkState.DISCONNECTED, null);
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onConnectFailed(reason == null ? "连接已断开" : reason);
        }
        this.toast(reason == null ? "连接已断开" : reason);
    }

    @Override
    public void onBleError(String message) {
        if (this.ble != null && this.ble.isReady()) {
            this.toast(message);
            return;
        }
        if (!this.demoMode) {
            this.setLinkState(LinkState.DISCONNECTED, null);
        }
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.onConnectFailed(message);
        }
        this.toast(message);
    }

    @Override
    public void onFrameSent(String hex, int command) {
        this.appendDevLog(this.timeFormat.format(new Date()) + "  TX " + this.commandName(command) + "  " + hex);
    }

    private String commandName(int command) {
        switch (command) {
            case 1: {
                return "POWER";
            }
            case 2: {
                return "RUNNING";
            }
            case 3: {
                return "BREATHE";
            }
            case 4: {
                return "FLAME";
            }
            case 5: {
                return "RHYTHM";
            }
            case 10: {
                return "SPEED";
            }
            case 11: {
                return "QUERY";
            }
            case 6: {
                return "EFFECT_OFF";
            }
            case 17: {
                return "ALL_OFF";
            }
            case 255: {
                return "SELFTEST";
            }
            case 7: {
                return "LED_STEP";
            }
            case 18: {
                return "CUSTOM_LED";
            }
            case 8: {
                return "COLOR";
            }
            case 16: {
                return "BRIGHT";
            }
            case 15: {
                return "SYNC";
            }
        }
        return String.format(Locale.CHINA, "0x%02X", command);
    }

    @Override
    public boolean hasBlePermission() {
        return this.ble != null && this.ble.hasPermission();
    }

    @Override
    public void requestBlePermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            this.requestPermissions(new String[]{"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"}, 1001);
        } else {
            this.requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION"}, 1001);
        }
    }

    @Override
    public boolean isBleSupported() {
        return this.ble != null && this.ble.isBleSupported();
    }

    @Override
    public boolean isBleEnabled() {
        return this.ble != null && this.ble.isBluetoothEnabled();
    }

    @Override
    public void requestEnableBle() {
        try {
            Intent intent = new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE");
            this.startActivityForResult(intent, 1002);
        }
        catch (Exception e) {
            this.toast("无法打开蓝牙设置，请手动开启蓝牙");
        }
    }

    @Override
    public boolean isLocationReady() {
        if (Build.VERSION.SDK_INT >= 31) {
            return true;
        }
        LocationManager manager = (LocationManager)this.getSystemService("location");
        if (manager == null) {
            return true;
        }
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return manager.isLocationEnabled();
            }
            return manager.isProviderEnabled("gps") || manager.isProviderEnabled("network");
        }
        catch (Exception e) {
            return true;
        }
    }

    @Override
    public void requestEnableLocation() {
        try {
            this.startActivity(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"));
        }
        catch (Exception e) {
            this.toast("请手动在系统设置中开启定位服务");
        }
    }

    @Override
    public boolean startBleScan() {
        return this.ble != null && this.ble.startScan();
    }

    @Override
    public void stopBleScan() {
        if (this.ble != null) {
            this.ble.stopScan(false);
        }
    }

    @Override
    public void connectDevice(BleDevice device) {
        if (this.ble == null) {
            return;
        }
        this.ble.connect(device);
    }

    @Override
    public void enterDemoFromSheet() {
        if (this.scanSheet != null) {
            this.scanSheet.dismiss();
        }
        this.enterDemoMode();
    }

    @Override
    public void openAppSettings() {
        try {
            Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse((String)("package:" + this.getPackageName())));
            this.startActivity(intent);
        }
        catch (Exception e) {
            this.toast("请手动打开系统设置授予权限");
        }
    }

    @Override
    public void showToast(String message) {
        this.toast(message);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1001) {
            return;
        }
        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result == 0) continue;
            granted = false;
            break;
        }
        if (this.scanSheet != null) {
            this.scanSheet.onPermissionResult(granted);
        }
        if (granted) {
            this.autoConnectTried = false;
            this.pager.postDelayed(this::tryAutoConnect, 300L);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1002 && this.scanSheet != null) {
            this.scanSheet.onBluetoothResult(resultCode == -1);
        }
    }

    protected void onResume() {
        super.onResume();
        if (this.scanSheet != null && this.scanSheet.isShowing()) {
            this.scanSheet.refresh();
        }
        this.pager.postDelayed(this::tryAutoConnect, 500L);
        UpdateManager.retryPendingInstall(this);
    }

    protected void onPause() {
        super.onPause();
        this.saveState();
    }

    protected void onDestroy() {
        if (this.scanSheet != null) {
            this.scanSheet.dismiss();
        }
        if (this.ble != null) {
            this.ble.close();
        }
        super.onDestroy();
    }

    private void toast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (this.toast != null) {
            this.toast.cancel();
        }
        this.toast = Toast.makeText((Context)this, (CharSequence)message, (int)0);
        this.toast.setGravity(80, 0, this.dp(72));
        this.toast.show();
    }

    private int dp(int value) {
        return Math.round((float)value * this.getResources().getDisplayMetrics().density);
    }

    private static enum LinkState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DEMO;

    }

    @TargetApi(value=30)
    private static final class Api30 {
        private Api30() {
        }

        static void setupWindow(Window window) {
            window.setDecorFitsSystemWindows(false);
        }
    }
}



