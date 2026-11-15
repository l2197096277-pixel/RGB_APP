package com.aurora.light;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import com.aurora.light.ui.FlamePreviewView;
import com.aurora.light.ui.MusicBarsView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.text.SimpleDateFormat;

/**
 * LS 两页主界面 + LS 幻彩灯控制子页 + 音乐律动子页。
 * BLE/OTA 仍复用原有 AuroraBleManager、FotaActivity。
 */
public class HomeActivity extends Activity
        implements AuroraBleManager.Listener, ScanSheet.Host {

    private static final int REQ_BLE = 1001;
    private static final int REQ_ENABLE_BT = 1002;
    private static final int REQ_MUSIC = 3001;
    private static final int REQ_AUDIO = 3002;

    private static final int MODE_NONE = -1;
    private static final int MODE_SELF_TEST = 1;
    private static final int MODE_RUNNING = 2;
    private static final int MODE_BREATHE = 3;
    private static final int MODE_FLAME = 4;
    private static final int MODE_MUSIC = 5;
    private static final int MODE_STEADY = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private SharedPreferences prefs;
    private AuroraBleManager ble;
    private ScanSheet scanSheet;

    private View homePage;
    private View profilePage;
    private View controlPage;
    private View musicPage;
    private View bottomNav;
    private View introOverlay;
    private TextView introTitle;
    private TextView introTagline;
    private View introLine;

    private LinearLayout myDevicesList;
    private TextView myDevicesEmpty;
    private View connectCard;
    private TextView connectIcon;
    private TextView connectTitle;
    private TextView connectSubtitle;

    private TextView versionLabel;
    private TextView currentFirmware;
    private View firmwareDot;
    private View checkFirmwareRow;
    private TextView changelogCurrentVersion;
    private TextView changelogToggle;
    private LinearLayout changelogHistoryList;
    private View devLogRow;
    private TextView devLogCount;
    private final StringBuilder devLogBuffer = new StringBuilder();
    private Dialog devLogDialog;
    private TextView devLogText;
    private boolean developerMode;
    private boolean changelogExpanded;
    private final Handler devLongPress = new Handler(Looper.getMainLooper());

    private View backControl;
    private TextView controlStatus;
    private Switch powerSwitch;
    private TextView powerHint;
    private TextView modeSelfTest;
    private TextView modeRunning;
    private TextView modeBreathe;
    private TextView modeFlame;
    private TextView modeMusic;
    private TextView modeSteady;
    private View colorSection;
    private LinearLayout colorRow;
    private FlamePreviewView flamePreview;
    private View countSection;
    private EditText countInput;
    private View brightnessSection;
    private SeekBar brightnessSeek;
    private TextView brightnessValue;
    private EditText ledCountInput;
    private TextView ledCountApply;
    private TextView ledCountStatus;

    private View backMusic;
    private TextView currentMusic;
    private TextView musicState;
    private TextView musicPlay;
    private TextView musicPrev;
    private TextView musicNext;
    private Switch reactiveSwitch;
    private TextView reactiveHint;
    private SeekBar sensitivitySeek;
    private TextView sensitivityValue;
    private TextView addMusic;
    private LinearLayout musicList;
    private TextView musicEmpty;
    private View exitMusicMode;
    private MusicBarsView musicBars;

    private boolean powerOn;
    private int activeMode = MODE_NONE;
    private int selectedColor = 1;
    private int steadyCount = 1;
    private int brightnessPercent = 100;
    private long lastBrightnessSend;
    private int actualLedCount;
    private boolean ledCountArmed;
    private boolean autoConnectPending;
    private boolean returningFromFota;
    private boolean suppressDisconnectToast;
    private boolean suppressConnectedToast;
    private boolean openControlOnReady;
    private boolean suppressBleErrorAfterFota;
    private String pendingConnectAddress;
    private String pendingConnectName;
    private boolean fallbackScanning;
    private boolean fallbackAttempted;

    private final List<DeviceRecord> deviceRecords = new ArrayList<>();
    private final List<MusicTrack> musicTracks = new ArrayList<>();
    private MediaPlayer player;
    private Visualizer visualizer;
    private int currentTrackIndex = -1;
    private boolean reactive;
    private float sensitivity = 1.6f;
    private double bassAverage;
    private double previousBass;
    private long lastBeatAt;
    private int lastBeatColor;

    private static final String PREFS = "ls_prefs";
    private static final String KEY_DEVICES = "ls_devices";
    private static final String KEY_MUSIC = "ls_music";
    private static final String KEY_LAST_ADDRESS = "last_device_address";
    private static final String KEY_LAST_NAME = "last_device_name";
    private static final String KEY_LAST_FW = "last_fw_version";

    private static class DeviceRecord {
        String address;
        String name;
    }

    private static class MusicTrack {
        String uri;
        String name;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setupWindow();
        bindViews();
        initHome();
        initProfile();
        initControl();
        initMusic();
        initBle();
        loadDevices();
        loadMusic();
        startIntro();
        handler.postDelayed(this::tryAutoConnect, 750L);
        UpdateManager.checkOnLaunch(this);
    }

    private void setupWindow() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.ls_bg));
        window.setNavigationBarColor(getColor(R.color.ls_bg));
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void bindViews() {
        homePage = findViewById(R.id.homePage);
        profilePage = findViewById(R.id.profilePage);
        controlPage = findViewById(R.id.controlPage);
        musicPage = findViewById(R.id.musicPage);
        bottomNav = findViewById(R.id.bottomNav);
        introOverlay = findViewById(R.id.introOverlay);
        introTitle = findViewById(R.id.introTitle);
        introTagline = findViewById(R.id.introTagline);
        introLine = findViewById(R.id.introLine);

        myDevicesList = findViewById(R.id.myDevicesList);
        myDevicesEmpty = findViewById(R.id.myDevicesEmpty);
        connectCard = findViewById(R.id.connectCard);
        connectIcon = findViewById(R.id.connectIcon);
        connectTitle = findViewById(R.id.connectTitle);
        connectSubtitle = findViewById(R.id.connectSubtitle);

        versionLabel = findViewById(R.id.versionLabel);
        currentFirmware = findViewById(R.id.currentFirmware);
        firmwareDot = findViewById(R.id.firmwareDot);
        checkFirmwareRow = findViewById(R.id.checkFirmwareRow);
        changelogCurrentVersion = findViewById(R.id.changelogCurrentVersion);
        changelogToggle = findViewById(R.id.changelogToggle);
        changelogHistoryList = findViewById(R.id.changelogHistoryList);
        devLogRow = findViewById(R.id.devLogRow);
        devLogCount = findViewById(R.id.devLogCount);

        backControl = findViewById(R.id.backControl);
        controlStatus = findViewById(R.id.controlStatus);
        powerSwitch = findViewById(R.id.powerSwitch);
        powerHint = findViewById(R.id.powerHint);
        modeSelfTest = findViewById(R.id.modeSelfTest);
        modeRunning = findViewById(R.id.modeRunning);
        modeBreathe = findViewById(R.id.modeBreathe);
        modeFlame = findViewById(R.id.modeFlame);
        modeMusic = findViewById(R.id.modeMusic);
        modeSteady = findViewById(R.id.modeSteady);
        colorSection = findViewById(R.id.colorSection);
        colorRow = findViewById(R.id.colorRow);
        flamePreview = findViewById(R.id.flamePreview);
        countSection = findViewById(R.id.countSection);
        countInput = findViewById(R.id.countInput);
        brightnessSection = findViewById(R.id.brightnessSection);
        brightnessSeek = findViewById(R.id.brightnessSeek);
        brightnessValue = findViewById(R.id.brightnessValue);
        ledCountInput = findViewById(R.id.ledCountInput);
        ledCountApply = findViewById(R.id.ledCountApply);
        ledCountStatus = findViewById(R.id.ledCountStatus);

        backMusic = findViewById(R.id.backMusic);
        currentMusic = findViewById(R.id.currentMusic);
        musicState = findViewById(R.id.musicState);
        musicPlay = findViewById(R.id.musicPlay);
        musicPrev = findViewById(R.id.musicPrev);
        musicNext = findViewById(R.id.musicNext);
        reactiveSwitch = findViewById(R.id.reactiveSwitch);
        reactiveHint = findViewById(R.id.reactiveHint);
        sensitivitySeek = findViewById(R.id.sensitivitySeek);
        sensitivityValue = findViewById(R.id.sensitivityValue);
        addMusic = findViewById(R.id.addMusic);
        musicList = findViewById(R.id.musicList);
        musicEmpty = findViewById(R.id.musicEmpty);
        exitMusicMode = findViewById(R.id.exitMusicMode);
        musicBars = findViewById(R.id.musicBars);
    }

    private void initHome() {
        findViewById(R.id.navHome).setOnClickListener(v -> showHomePage());
        findViewById(R.id.navProfile).setOnClickListener(v -> showProfilePage());
        connectCard.setOnClickListener(v -> {
            if (ble != null && ble.isReady()) {
                ble.disconnect();
                refreshConnectionCard();
                renderDevices();
                showHomePage();
                toast("已断开连接");
            } else {
                showScan();
            }
        });
        refreshConnectionCard();
    }

    private void initProfile() {
        versionLabel.setText("v" + BuildConfig.VERSION_NAME);
        changelogCurrentVersion.setText("v" + BuildConfig.VERSION_NAME + " · 2026-11-15");
        buildChangelog();
        versionLabel.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                devLongPress.removeCallbacksAndMessages(null);
                devLongPress.postDelayed(() -> {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    developerMode = !developerMode;
                    if (developerMode) {
                        devLogBuffer.setLength(0);
                        appendDevLog("开发者模式已开启，开始记录");
                    } else {
                        devLogBuffer.setLength(0);
                    }
                    updateChangelogVisibility();
                    toast(developerMode ? "开发者模式已开启" : "已退出开发者模式");
                }, 5_000L);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                devLongPress.removeCallbacksAndMessages(null);
            }
            return false;
        });
        changelogToggle.setOnClickListener(v -> {
            if (!developerMode) return;
            changelogExpanded = !changelogExpanded;
            updateChangelogVisibility();
        });
        devLogRow.setOnClickListener(v -> showDevLog());
        checkFirmwareRow.setOnClickListener(v -> checkFirmwareUpdate());
        updateFirmwareUi();
        checkFirmwareAvailability();
    }

    private void buildChangelog() {
        changelogHistoryList.removeAllViews();
        String[][] history = {
                {"v1.1.4", "2026-11-15"}, {"v1.1.3", "2026-11-15"},
                {"v1.1.2", "2026-11-15"}, {"v1.1.1", "2026-11-14"},
                {"v1.1.0", "2026-11-14"}, {"v1.0.9", "2026-11-13"},
                {"v1.0.0", "2026-11-08"}
        };
        for (int i = 0; i < history.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            TextView title = new TextView(this);
            title.setText(history[i][0] + " · " + history[i][1]);
            title.setTextColor(getColor(R.color.ls_accent));
            title.setTextSize(12);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            TextView body = new TextView(this);
            body.setText("修复了已知问题");
            body.setTextColor(getColor(R.color.ls_secondary));
            body.setTextSize(11);
            body.setPadding(0, dp(4), 0, 0);
            row.addView(title);
            row.addView(body);
            changelogHistoryList.addView(row);
            if (i < history.length - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
                divider.setBackgroundColor(getColor(R.color.ls_stroke));
                changelogHistoryList.addView(divider);
            }
        }
        updateChangelogVisibility();
    }

    private void updateChangelogVisibility() {
        changelogToggle.setVisibility(developerMode ? View.VISIBLE : View.GONE);
        if (!developerMode) changelogExpanded = false;
        changelogHistoryList.setVisibility(developerMode && changelogExpanded ? View.VISIBLE : View.GONE);
        changelogToggle.setText(changelogExpanded ? "收起历史" : "查看历史");
        devLogRow.setVisibility(developerMode ? View.VISIBLE : View.GONE);
        if (developerMode) {
            devLogCount.setText(devLogBuffer.length() == 0
                    ? "开发者模式 · 等待操作"
                    : "本次记录 " + lineCount(devLogBuffer) + " 行 · 点击查看");
        }
    }

    private static int lineCount(CharSequence text) {
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private void appendDevLog(String message) {
        if (!developerMode) return;
        String line = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date())
                + "  " + message;
        runOnUiThread(() -> {
            devLogBuffer.append(line).append('\n');
            if (devLogBuffer.length() > 24000) {
                devLogBuffer.delete(0, devLogBuffer.length() - 18000);
            }
            updateChangelogVisibility();
            if (devLogText != null) devLogText.setText(devLogBuffer.toString());
        });
    }

    private void showDevLog() {
        if (!developerMode) return;
        if (devLogDialog != null && devLogDialog.isShowing()) return;
        devLogDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        devLogDialog.setContentView(R.layout.dialog_dev_log);
        devLogDialog.setCanceledOnTouchOutside(true);
        devLogText = devLogDialog.findViewById(R.id.devLogText);
        devLogText.setText(devLogBuffer.length() == 0 ? "暂无日志" : devLogBuffer.toString());
        devLogDialog.findViewById(R.id.devLogClose).setOnClickListener(v -> {
            devLogDialog.dismiss();
            devLogText = null;
        });
        devLogDialog.findViewById(R.id.devLogClear).setOnClickListener(v -> {
            devLogBuffer.setLength(0);
            devLogText.setText("已清空");
            updateChangelogVisibility();
        });
        Window window = devLogDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.52f;
            lp.gravity = Gravity.CENTER;
            window.setAttributes(lp);
        }
        devLogDialog.show();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void initControl() {
        backControl.setOnClickListener(v -> exitControl());
        powerSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !ledCountArmed) {
                powerSwitch.setChecked(false);
                toast("请先设置实际灯珠数量，再开启灯光");
                ledCountInput.requestFocus();
                return;
            }
            if (!isChecked && activeMode == MODE_MUSIC) stopMusic();
            powerOn = isChecked;
            if (!isChecked) {
                ledCountArmed = false;
            }
            if (ble != null && ble.isReady()) {
                sendModeFrame(AuroraProtocol.lsPower(isChecked), 3);
            }
            if (!isChecked) {
                activeMode = MODE_NONE;
            }
            appendDevLog(isChecked ? "总开关：开" : "总开关：关");
            updateControlUi();
        });
        ledCountApply.setOnClickListener(v -> applyLedCount());
        ledCountInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (ledCountArmed) {
                    ledCountArmed = false;
                    updateControlUi();
                }
            }
        });
        modeSelfTest.setOnClickListener(v -> confirmSelfTest());
        modeRunning.setOnClickListener(v -> toggleMode(MODE_RUNNING));
        modeBreathe.setOnClickListener(v -> toggleMode(MODE_BREATHE));
        modeFlame.setOnClickListener(v -> toggleMode(MODE_FLAME));
        modeMusic.setOnClickListener(v -> toggleMode(MODE_MUSIC));
        modeSteady.setOnClickListener(v -> toggleMode(MODE_STEADY));

        buildColorRow();

        countInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyCount();
                v.clearFocus();
                return true;
            }
            return false;
        });
        countInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) applyCount();
        });

        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 20) {
                    seekBar.setProgress(20);
                    return;
                }
                brightnessPercent = progress;
                brightnessValue.setText(brightnessPercent + "%");
                if (fromUser && powerOn && activeMode == MODE_STEADY
                        && ble != null && ble.isReady()) {
                    long now = System.currentTimeMillis();
                    if (now - lastBrightnessSend >= 70L) {
                        lastBrightnessSend = now;
                        ble.sendReactive(AuroraProtocol.lsSteady(true, steadyCount,
                                selectedColor, protocolBrightness(brightnessPercent)));
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (powerOn && activeMode == MODE_STEADY && ble != null && ble.isReady()) {
                    ble.send(AuroraProtocol.lsSteady(true, steadyCount,
                            selectedColor, protocolBrightness(brightnessPercent)));
                }
            }
        });
        updateControlUi();
    }

    private void buildColorRow() {
        colorRow.removeAllViews();
        for (int i = 0; i < AuroraProtocol.LS_COLOR_RGB.length; i++) {
            final int colorIndex = i + 1;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(42), -2);
            itemParams.setMarginEnd(dp(6));
            item.setLayoutParams(itemParams);
            item.setOnClickListener(v -> selectColor(colorIndex));

            TextView dot = new TextView(this);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(32), dp(32));
            dot.setLayoutParams(dotParams);
            dot.setGravity(Gravity.CENTER);
            dot.setTextSize(9);
            dot.setTypeface(Typeface.DEFAULT_BOLD);

            TextView label = new TextView(this);
            label.setText(AuroraProtocol.LS_COLOR_NAMES[i]);
            label.setTextColor(getColor(R.color.ls_secondary));
            label.setTextSize(9);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);

            item.addView(dot);
            item.addView(label);
            colorRow.addView(item);
        }
        updateColorRow();
    }

    private void updateColorRow() {
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            LinearLayout item = (LinearLayout) colorRow.getChildAt(i);
            TextView dot = (TextView) item.getChildAt(0);
            int index = i + 1;
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(AuroraProtocol.LS_COLOR_RGB[i]);
            bg.setStroke(index == selectedColor ? dp(3) : dp(1),
                    index == selectedColor ? getColor(R.color.ls_text) : getColor(R.color.ls_stroke));
            dot.setBackground(bg);
            dot.setText(index == selectedColor ? "✓" : "");
            dot.setTextColor(index == 1 ? getColor(R.color.ls_text) : Color.WHITE);
        }
    }

    private void selectColor(int colorIndex) {
        selectedColor = colorIndex;
        updateColorRow();
        if (flamePreview != null && activeMode == MODE_FLAME) {
            flamePreview.setFlameColor(AuroraProtocol.LS_COLOR_RGB[selectedColor - 1]);
        }
        if (powerOn && (activeMode == MODE_RUNNING || activeMode == MODE_BREATHE
                || activeMode == MODE_FLAME || activeMode == MODE_STEADY)) {
            sendCurrentMode();
        }
    }

    private void initMusic() {
        backMusic.setOnClickListener(v -> exitMusicMode());
        musicPlay.setOnClickListener(v -> togglePlay());
        musicPrev.setOnClickListener(v -> playPrevious());
        musicNext.setOnClickListener(v -> playNext());
        addMusic.setOnClickListener(v -> pickMusic());
        exitMusicMode.setOnClickListener(v -> exitMusicMode());
        reactiveSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!hasAudioPermission()) {
                    reactiveSwitch.setChecked(false);
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                    return;
                }
                reactive = true;
                if (player == null && !musicTracks.isEmpty()) playTrack(0);
                ensureVisualizer();
            } else {
                reactive = false;
                releaseVisualizer();
                musicState.setText("鼓点律动已关闭");
            }
        });
        sensitivitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sensitivity = progress / 10f;
                sensitivityValue.setText(String.format(Locale.CHINA, "%.1f", sensitivity));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        reactiveHint.setText("需要录音权限，仅用于本地分析");
    }

    private void initBle() {
        ble = new AuroraBleManager(this, this);
        scanSheet = new ScanSheet(this, this);
    }

    private void startIntro() {
        introOverlay.setVisibility(View.VISIBLE);
        introTitle.setAlpha(0f);
        introTitle.setScaleX(0.92f);
        introTitle.setScaleY(0.92f);
        introLine.setScaleX(0f);
        introTagline.setAlpha(0f);
        introTagline.setTranslationY(dp(10));
        introTitle.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700L).setStartDelay(120L).start();
        introLine.animate().scaleX(1f).setDuration(550L).setStartDelay(520L).start();
        introTagline.animate().alpha(1f).translationY(0f)
                .setDuration(650L).setStartDelay(650L).start();
        handler.postDelayed(this::hideIntro, 2700L);
        introOverlay.setOnClickListener(v -> hideIntro());
    }

    private void hideIntro() {
        if (introOverlay.getVisibility() != View.VISIBLE) return;
        introOverlay.animate().alpha(0f).setDuration(350L)
                .withEndAction(() -> {
                    introOverlay.setVisibility(View.GONE);
                    introOverlay.setAlpha(1f);
                }).start();
    }

    private void showHomePage() {
        homePage.setVisibility(View.VISIBLE);
        profilePage.setVisibility(View.GONE);
        controlPage.setVisibility(View.GONE);
        musicPage.setVisibility(View.GONE);
        bottomNav.setVisibility(View.VISIBLE);
        updateNav(0);
        loadDevices();
        refreshConnectionCard();
    }

    private void refreshConnectionCard() {
        if (connectTitle == null) return;
        boolean connected = ble != null && ble.isReady();
        if (connected) {
            connectIcon.setText("✓");
            connectIcon.setTextColor(Color.WHITE);
            connectTitle.setText("LS 幻彩灯");
            connectSubtitle.setText("已连接 · 点击断开");
        } else {
            connectIcon.setText("+");
            connectIcon.setTextColor(Color.WHITE);
            connectTitle.setText("连接设备");
            connectSubtitle.setText("扫描附近的 LS 幻彩灯");
        }
    }

    private void showProfilePage() {
        homePage.setVisibility(View.GONE);
        profilePage.setVisibility(View.VISIBLE);
        controlPage.setVisibility(View.GONE);
        musicPage.setVisibility(View.GONE);
        bottomNav.setVisibility(View.VISIBLE);
        updateNav(1);
        updateFirmwareUi();
    }

    private void showControlPage() {
        homePage.setVisibility(View.GONE);
        profilePage.setVisibility(View.GONE);
        controlPage.setVisibility(View.VISIBLE);
        musicPage.setVisibility(View.GONE);
        bottomNav.setVisibility(View.GONE);
        powerOn = false;
        ledCountArmed = false;
        actualLedCount = 0;
        if (ledCountInput != null) ledCountInput.setText("");
        if (ledCountStatus != null) {
            ledCountStatus.setText("开灯前必须先设置，关灯后才能修改");
        }
        powerSwitch.setChecked(false);
        activeMode = MODE_NONE;
        updateControlUi();
    }

    private void showMusicPage() {
        homePage.setVisibility(View.GONE);
        profilePage.setVisibility(View.GONE);
        controlPage.setVisibility(View.GONE);
        musicPage.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);
        refreshMusicList();
    }

    private void showControlFromMusic() {
        musicPage.setVisibility(View.GONE);
        controlPage.setVisibility(View.VISIBLE);
        updateControlUi();
    }

    private void updateNav(int index) {
        TextView home = findViewById(R.id.navHomeText);
        TextView profile = findViewById(R.id.navProfileText);
        View homeDot = findViewById(R.id.navHomeDot);
        View profileDot = findViewById(R.id.navProfileDot);
        home.setTextColor(getColor(index == 0 ? R.color.ls_accent : R.color.ls_secondary));
        profile.setTextColor(getColor(index == 1 ? R.color.ls_accent : R.color.ls_secondary));
        homeDot.setVisibility(index == 0 ? View.VISIBLE : View.INVISIBLE);
        profileDot.setVisibility(index == 1 ? View.VISIBLE : View.INVISIBLE);
    }

    private void confirmSelfTest() {
        if (!powerOn) {
            toast("请先打开灯光总开关");
            return;
        }
        if (activeMode == MODE_SELF_TEST) {
            toggleMode(MODE_SELF_TEST);
            return;
        }
        showLsConfirm("开始自检？", "确认后灯带会执行自检效果。", "开始自检",
                () -> toggleMode(MODE_SELF_TEST));
    }

    private void showLsConfirm(String title, String message, String confirmText, Runnable action) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(R.layout.dialog_ls_confirm);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.5f;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        TextView titleView = dialog.findViewById(R.id.lsDialogTitle);
        TextView messageView = dialog.findViewById(R.id.lsDialogMessage);
        TextView cancel = dialog.findViewById(R.id.lsDialogCancel);
        TextView confirm = dialog.findViewById(R.id.lsDialogConfirm);
        titleView.setText(title);
        messageView.setText(message);
        confirm.setText(confirmText);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        dialog.show();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void toggleMode(int mode) {        if (!powerOn) {
            toast("请先打开灯光总开关");
            return;
        }
        if (mode == MODE_MUSIC) {
            if (activeMode != MODE_MUSIC) {
                activeMode = MODE_MUSIC;
                sendModeFrame(AuroraProtocol.lsMusic(true, 1, 255), 3);
                appendDevLog("模式开启：音乐律动");
            }
            updateControlUi();
            showMusicPage();
            return;
        }
        if (activeMode == mode) {
            sendModeFrame(modeFrame(mode, false), 3);
            activeMode = MODE_NONE;
            appendDevLog("模式关闭：" + modeName(mode));
        } else {
            activeMode = mode;
            sendModeFrame(modeFrame(mode, true), 3);
            appendDevLog("模式开启：" + modeName(mode));
        }
        updateControlUi();
    }

    private String modeName(int mode) {
        switch (mode) {
            case MODE_SELF_TEST: return "自检";
            case MODE_RUNNING: return "跑马灯";
            case MODE_BREATHE: return "呼吸";
            case MODE_FLAME: return "焰火";
            case MODE_MUSIC: return "音乐律动";
            case MODE_STEADY: return "常亮";
            default: return "无";
        }
    }

    private byte[] modeFrame(int mode, boolean on) {
        switch (mode) {
            case MODE_SELF_TEST:
                return AuroraProtocol.lsSelfTest(on);
            case MODE_RUNNING:
                return AuroraProtocol.lsRunning(on, selectedColor);
            case MODE_BREATHE:
                return AuroraProtocol.lsBreathe(on, selectedColor);
            case MODE_FLAME:
                return AuroraProtocol.lsFlame(on, selectedColor);
            case MODE_STEADY:
                return AuroraProtocol.lsSteady(on, steadyCount, selectedColor,
                        protocolBrightness(brightnessPercent));
            case MODE_MUSIC:
            default:
                return AuroraProtocol.lsMusic(on, selectedColor, 255);
        }
    }

    private void sendCurrentMode() {
        if (!powerOn || activeMode == MODE_NONE || ble == null || !ble.isReady()) return;
        if (activeMode == MODE_MUSIC) return;
        sendModeFrame(modeFrame(activeMode, true), 3);
    }

    private void sendModeFrame(byte[] frame, int times) {
        if (ble == null || !ble.isReady()) return;
        if (times <= 1) {
            ble.sendReactive(frame);
        } else {
            ble.sendBurst(frame, times, 100L);
        }
    }

    private void applyLedCount() {
        if (powerOn) {
            toast("请先关闭灯光，再修改灯珠数量");
            return;
        }
        String text = ledCountInput.getText().toString().trim();
        if (text.isEmpty()) {
            toast("请输入实际灯珠数量");
            ledCountInput.requestFocus();
            return;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            toast("灯珠数量格式不正确");
            return;
        }
        if (value < 1 || value > 255) {
            toast("灯珠数量范围为 1–255");
            return;
        }
        actualLedCount = value;
        ledCountArmed = true;
        prefs.edit().putInt("ls_led_count", value).apply();
        sendModeFrame(AuroraProtocol.lsLedCount(value), 3);
        if (steadyCount > actualLedCount) steadyCount = actualLedCount;
        countInput.setText(String.valueOf(steadyCount));
        ledCountStatus.setText("已设置 " + value + " 颗，可以开灯");
        appendDevLog("设置实际灯珠数量：" + value);
        updateControlUi();
        toast("灯珠数量已设置");
    }

    private void applyCount() {
        String text = countInput.getText().toString().trim();
        if (text.isEmpty()) {
            countInput.setText(String.valueOf(steadyCount));
            return;
        }
        try {
            int value = Integer.parseInt(text);
            int max = actualLedCount > 0 ? actualLedCount : 255;
            steadyCount = Math.max(1, Math.min(max, value));
        } catch (NumberFormatException e) {
            steadyCount = 1;
        }
        countInput.setText(String.valueOf(steadyCount));
        if (activeMode == MODE_STEADY) sendCurrentMode();
    }

    private int protocolBrightness(int percent) {
        int p = Math.max(20, Math.min(100, percent));
        return Math.round(51f + (p - 20) * 204f / 80f);
    }

    private void updateControlUi() {
        if (modeSelfTest == null) return;
        int[] ids = {R.id.modeSelfTest, R.id.modeRunning, R.id.modeBreathe,
                R.id.modeFlame, R.id.modeMusic, R.id.modeSteady};
        int[] modes = {MODE_SELF_TEST, MODE_RUNNING, MODE_BREATHE,
                MODE_FLAME, MODE_MUSIC, MODE_STEADY};
        for (int i = 0; i < ids.length; i++) {
            TextView view = findViewById(ids[i]);
            boolean selected = powerOn && activeMode == modes[i];
            view.setBackgroundResource(selected ? R.drawable.bg_ls_mode_selected : R.drawable.bg_ls_mode);
            view.setTextColor(getColor(selected ? R.color.ls_accent : R.color.ls_text));
            view.setAlpha(powerOn ? 1f : 0.45f);
        }
        boolean showColor = powerOn && (activeMode == MODE_RUNNING
                || activeMode == MODE_BREATHE
                || activeMode == MODE_FLAME || activeMode == MODE_STEADY);
        colorSection.setVisibility(showColor ? View.VISIBLE : View.GONE);
        flamePreview.setVisibility(powerOn && activeMode == MODE_FLAME
                ? View.VISIBLE : View.GONE);
        if (flamePreview.getVisibility() == View.VISIBLE) {
            flamePreview.setFlameColor(AuroraProtocol.LS_COLOR_RGB[selectedColor - 1]);
        }
        boolean showSteady = powerOn && activeMode == MODE_STEADY;
        countSection.setVisibility(showSteady ? View.VISIBLE : View.GONE);
        brightnessSection.setVisibility(showSteady ? View.VISIBLE : View.GONE);
        ledCountInput.setEnabled(!powerOn);
        ledCountApply.setEnabled(!powerOn);
        ledCountApply.setAlpha(powerOn ? 0.4f : 1f);
        if (powerOn) {
            ledCountStatus.setText("灯光已开启，关闭后才能修改数量");
        } else if (ledCountArmed) {
            ledCountStatus.setText("已设置 " + actualLedCount + " 颗，可以开灯");
        } else {
            ledCountStatus.setText("开灯前必须先设置，关灯后才能修改");
        }
        powerHint.setText(powerOn ? "灯光已开启，可使用光效" : "关闭时其他功能不可用");
        controlStatus.setText(ble != null && ble.isReady() ? "已连接" : "未连接");
        if (showSteady) {
            countInput.setText(String.valueOf(steadyCount));
            brightnessSeek.setProgress(brightnessPercent);
            brightnessValue.setText(brightnessPercent + "%");
        }
    }

    @Override
    public void onBleConnected(String name) {
        controlStatus.setText("已连接");
    }

    @Override
    public void onBleReady() {
        autoConnectPending = false;
        returningFromFota = false;
        saveDevice(ble.currentAddress(), "LS 幻彩灯");
        prefs.edit().putString(KEY_LAST_ADDRESS, ble.currentAddress())
                .putString(KEY_LAST_NAME, "LS 幻彩灯").apply();
        appendDevLog("已连接：" + ble.currentAddress());
        if (!suppressConnectedToast) {
            toast("已连接");
        }
        suppressConnectedToast = false;
        suppressDisconnectToast = false;
        suppressBleErrorAfterFota = false;
        pendingConnectAddress = null;
        pendingConnectName = null;
        fallbackScanning = false;
        fallbackAttempted = false;
        refreshConnectionCard();
        if (openControlOnReady) {
            openControlOnReady = false;
            showControlPage();
        } else {
            openControlOnReady = false;
            // 连接成功只更新状态，不强制跳回主页；用户当前在哪个页面就保持。
            refreshConnectionCard();
            loadDevices();
        }
        updateFirmwareUi();
        if (scanSheet != null && scanSheet.isShowing()) scanSheet.onConnected();
    }

    @Override
    public void onBleDisconnected(String reason) {
        stopMusic();
        powerOn = false;
        activeMode = MODE_NONE;
        openControlOnReady = false;
        fallbackScanning = false;
        pendingConnectAddress = null;
        pendingConnectName = null;
        controlStatus.setText("未连接");
        appendDevLog("已断开：" + (reason == null ? "" : reason));
        boolean silent = suppressDisconnectToast;
        suppressDisconnectToast = false;
        if (!silent) {
            toast("蓝牙已断开");
        }
        refreshConnectionCard();
        showHomePage();
    }

    @Override
    public void onBleError(String message) {
        if (ble != null && ble.isReady()) {
            toast(message);
            return;
        }
        controlStatus.setText("未连接");
        if (suppressBleErrorAfterFota) {
            suppressBleErrorAfterFota = false;
            openControlOnReady = false;
            refreshConnectionCard();
            return;
        }
        if (!fallbackAttempted && !TextUtils.isEmpty(pendingConnectName)) {
            startFallbackScan();
            return;
        }
        openControlOnReady = false;
        pendingConnectAddress = null;
        pendingConnectName = null;
        fallbackScanning = false;
        refreshConnectionCard();
        if (scanSheet != null && scanSheet.isShowing()) {
            scanSheet.onConnectFailed(message);
        }
        toast(TextUtils.isEmpty(message) ? "蓝牙已断开" : message);
    }

    @Override
    public void onFrameSent(String hex, int command) {
        if (developerMode) {
            appendDevLog("TX  " + hex);
        }
    }

    @Override
    public void onScanResult(BleDevice device) {
        if (fallbackScanning && device != null) {
            boolean sameAddress = !TextUtils.isEmpty(pendingConnectAddress)
                    && normalizeAddress(pendingConnectAddress).equals(normalizeAddress(device.address));
            boolean sameName = !TextUtils.isEmpty(pendingConnectName)
                    && pendingConnectName.equalsIgnoreCase(device.displayName());
            if (sameAddress || sameName || device.isRgbProduct()) {
                fallbackScanning = false;
                if (ble != null) ble.stopScan(false);
                toast("已重新找到设备，正在连接…");
                ble.connect(device);
                return;
            }
        }
        if (scanSheet != null) scanSheet.addDevice(device);
    }

    @Override
    public void onScanFinished() {
        if (fallbackScanning) {
            fallbackScanning = false;
            openControlOnReady = false;
            pendingConnectAddress = null;
            pendingConnectName = null;
            toast("没有重新找到设备，请手动扫描连接");
        }
        if (scanSheet != null) scanSheet.onScanFinished();
    }

    @Override
    public void onScanError(String message) {
        if (fallbackScanning) {
            fallbackScanning = false;
            openControlOnReady = false;
            pendingConnectAddress = null;
            pendingConnectName = null;
            toast(TextUtils.isEmpty(message) ? "扫描失败，请重试" : message);
        }
        if (scanSheet != null) scanSheet.onScanError(message);
    }

    private void startFallbackScan() {
        fallbackAttempted = true;
        fallbackScanning = true;
        toast("直连失败，正在重新搜索设备…");
        if (ble == null || !ble.startScan()) {
            fallbackScanning = false;
            openControlOnReady = false;
            pendingConnectName = null;
            toast("无法重新扫描，请手动点击连接");
        }
    }

    private void connectHistoryDevice(DeviceRecord d) {
        if (d == null) return;
        if (scanSheet != null && scanSheet.isShowing()) scanSheet.dismiss();
        pendingConnectAddress = d.address;
        pendingConnectName = d.name;
        fallbackAttempted = false;
        fallbackScanning = false;
        openControlOnReady = true;
        toast("正在连接…");
        if (ble != null) ble.connectAddress(d.address, d.name);
    }

    private void tryAutoConnect() {
        if (ble == null || ble.isReady()) return;
        String address = prefs.getString(KEY_LAST_ADDRESS, "");
        if (TextUtils.isEmpty(address)) return;
        if (!hasBlePermission() || !isBleEnabled()) return;
        autoConnectPending = true;
        pendingConnectAddress = address;
        pendingConnectName = prefs.getString(KEY_LAST_NAME, "LS 幻彩灯");
        fallbackAttempted = false;
        fallbackScanning = false;
        ble.connectAddress(address, pendingConnectName);
    }

    private void showScan() {
        if (scanSheet == null) scanSheet = new ScanSheet(this, this);
        openControlOnReady = false;
        scanSheet.show();
        scanSheet.refresh();
    }

    private void loadDevices() {
        if (myDevicesList == null) return;
        deviceRecords.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_DEVICES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                DeviceRecord d = new DeviceRecord();
                d.address = o.optString("address");
                d.name = o.optString("name", "LS 幻彩灯");
                if (!TextUtils.isEmpty(d.address)) deviceRecords.add(d);
            }
        } catch (Exception ignored) {
        }
        // 兼容旧版本可能写入的重复记录。
        for (int i = deviceRecords.size() - 1; i >= 0; i--) {
            for (int j = 0; j < i; j++) {
                DeviceRecord left = deviceRecords.get(j);
                DeviceRecord right = deviceRecords.get(i);
                boolean sameAddress = normalizeAddress(left.address)
                        .equals(normalizeAddress(right.address));
                boolean sameName = !TextUtils.isEmpty(left.name)
                        && left.name.equalsIgnoreCase(right.name);
                if (sameAddress || sameName) {
                    // 旧记录保留位置，但地址更新为较新的那条，避免地址变化后直连旧地址。
                    deviceRecords.get(j).address = right.address;
                    deviceRecords.get(j).name = right.name;
                    deviceRecords.remove(i);
                    break;
                }
            }
        }
        saveDeviceJson();
        renderDevices();
    }

    private void saveDevice(String address, String name) {
        if (TextUtils.isEmpty(address)) return;
        for (DeviceRecord d : deviceRecords) {
            boolean sameAddress = normalizeAddress(address).equals(normalizeAddress(d.address));
            boolean sameName = !TextUtils.isEmpty(name) && name.equalsIgnoreCase(d.name);
            if (sameAddress || sameName) {
                d.address = address;
                d.name = name;
                saveDeviceJson();
                renderDevices();
                return;
            }
        }
        DeviceRecord d = new DeviceRecord();
        d.address = address;
        d.name = name;
        deviceRecords.add(0, d);
        while (deviceRecords.size() > 12) deviceRecords.remove(deviceRecords.size() - 1);
        saveDeviceJson();
        renderDevices();
    }

    private static String normalizeAddress(String address) {
        if (address == null) return "";
        return address.replace(":", "").replace("-", "").trim().toUpperCase(Locale.US);
    }

    private void saveDeviceJson() {
        JSONArray array = new JSONArray();
        for (DeviceRecord d : deviceRecords) {
            JSONObject o = new JSONObject();
            try {
                o.put("address", d.address);
                o.put("name", d.name);
                array.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply();
    }

    private void renderDevices() {
        myDevicesList.removeAllViews();
        myDevicesEmpty.setVisibility(deviceRecords.isEmpty() ? View.VISIBLE : View.GONE);
        for (DeviceRecord d : deviceRecords) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_ls_card);
            row.setPadding(dp(16), dp(13), dp(10), dp(13));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            TextView name = new TextView(this);
            name.setText(d.name);
            name.setTextColor(getColor(R.color.ls_text));
            name.setTextSize(13);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            TextView addr = new TextView(this);
            addr.setText(d.address);
            addr.setTextColor(getColor(R.color.ls_tertiary));
            addr.setTextSize(9);
            addr.setPadding(0, dp(3), 0, 0);
            boolean current = ble != null && ble.isReady()
                    && d.address.equalsIgnoreCase(ble.currentAddress());
            TextView state = new TextView(this);
            state.setText(current ? "已连接" : "未连接");
            state.setTextColor(getColor(current ? R.color.ls_mint : R.color.ls_tertiary));
            state.setTextSize(9);
            state.setPadding(0, dp(4), 0, 0);
            info.addView(name);
            info.addView(addr);
            info.addView(state);
            row.addView(info);

            TextView action = new TextView(this);
            action.setText(current ? "断开" : "连接");
            action.setTextColor(getColor(current ? R.color.ls_danger : R.color.ls_accent));
            action.setTextSize(9);
            action.setPadding(dp(8), dp(6), dp(8), dp(6));
            action.setOnClickListener(v -> {
                if (current) {
                    ble.disconnect();
                    refreshConnectionCard();
                    renderDevices();
                    toast("已断开连接");
                } else {
                    connectHistoryDevice(d);
                }
            });
            row.addView(action);

            TextView delete = new TextView(this);
            delete.setText("删除");
            delete.setTextColor(getColor(R.color.ls_tertiary));
            delete.setTextSize(9);
            delete.setPadding(dp(8), dp(6), dp(8), dp(6));
            delete.setOnClickListener(v -> deleteDevice(d));
            row.addView(delete);

            row.setOnClickListener(v -> {
                if (ble != null && ble.isReady()
                        && d.address.equalsIgnoreCase(ble.currentAddress())) {
                    showControlPage();
                } else {
                    connectHistoryDevice(d);
                }
            });
            myDevicesList.addView(row);
        }
    }

    private void deleteDevice(DeviceRecord d) {
        deviceRecords.remove(d);
        saveDeviceJson();
        if (d.address.equals(prefs.getString(KEY_LAST_ADDRESS, ""))) {
            prefs.edit().remove(KEY_LAST_ADDRESS).remove(KEY_LAST_NAME).apply();
        }
        if (ble != null && ble.isReady() && d.address.equalsIgnoreCase(ble.currentAddress())) {
            ble.disconnect();
            refreshConnectionCard();
        }
        renderDevices();
    }

    private void checkFirmwareUpdate() {
        if (ble == null || !ble.isReady()) {
            toast("请先连接灯带");
            return;
        }
        String address = ble.currentAddress();
        if (TextUtils.isEmpty(address)) return;
        autoConnectPending = true;
        returningFromFota = true;
        suppressDisconnectToast = true;
        suppressConnectedToast = true;
        suppressBleErrorAfterFota = true;
        ble.disconnect();
        Intent intent = new Intent(this, FotaActivity.class);
        intent.putExtra(FotaActivity.EXTRA_ADDRESS, address);
        intent.putExtra(FotaActivity.EXTRA_NAME, "LS 幻彩灯");
        intent.putExtra(FotaActivity.EXTRA_DEV_MODE, developerMode);
        startActivity(intent);
    }

    private void updateFirmwareUi() {
        int version = prefs.getInt(KEY_LAST_FW, 0);
        if (version == 0xFFFF) {
            prefs.edit().putInt(KEY_LAST_FW, 0).apply();
            version = 0;
        }
        currentFirmware.setText(version > 0
                ? "当前固件：" + firmwareVersion(version) : "当前固件：未读取");
        firmwareDot.setVisibility(prefs.getBoolean("fw_update_available", false)
                ? View.VISIBLE : View.GONE);
    }

    private void checkFirmwareAvailability() {
        new Thread(() -> {
            FotaManifest manifest = null;
            try {
                manifest = FotaManifest.fetch(BuildConfig.FOTA_URL);
            } catch (Exception ignored) {
            }
            final FotaManifest result = manifest;
            if (result == null) return;
            runOnUiThread(() -> {
                int known = prefs.getInt(KEY_LAST_FW, 0);
                boolean available = result.force || known == 0 || result.versionCode > known;
                prefs.edit().putBoolean("fw_update_available", available).apply();
                updateFirmwareUi();
            });
        }, "ls-fota-check").start();
    }

    private static String firmwareVersion(int version) {
        return "v" + ((version >> 12) & 0xF) + "." + ((version >> 8) & 0xF)
                + "." + ((version >> 4) & 0xF) + "." + (version & 0xF);
    }

    /* ===== Music ===== */

    private void pickMusic() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_MUSIC);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ENABLE_BT) {
            if (scanSheet != null) scanSheet.onBluetoothResult(resultCode == RESULT_OK);
            return;
        }
        if (requestCode == REQ_MUSIC && resultCode == RESULT_OK && data != null) {
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            for (Uri uri : uris) addTrack(uri);
            saveMusic();
            refreshMusicList();
        }
    }

    private void addTrack(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        for (MusicTrack t : musicTracks) {
            if (uri.toString().equals(t.uri)) return;
        }
        String name = queryName(uri);
        MusicTrack track = new MusicTrack();
        track.uri = uri.toString();
        track.name = name;
        musicTracks.add(track);
    }

    private String queryName(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int index = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = c.getString(index);
            }
        } catch (Exception ignored) {
        }
        if (TextUtils.isEmpty(name)) {
            try {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                r.setDataSource(this, uri);
                name = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                r.release();
            } catch (Exception ignored) {
            }
        }
        return TextUtils.isEmpty(name) ? "未命名音乐" : name;
    }

    private void loadMusic() {
        musicTracks.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_MUSIC, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                MusicTrack t = new MusicTrack();
                t.uri = o.optString("uri");
                t.name = o.optString("name", "未命名音乐");
                if (!TextUtils.isEmpty(t.uri)) musicTracks.add(t);
            }
        } catch (Exception ignored) {
        }
        refreshMusicList();
    }

    private void saveMusic() {
        JSONArray array = new JSONArray();
        for (MusicTrack t : musicTracks) {
            JSONObject o = new JSONObject();
            try {
                o.put("uri", t.uri);
                o.put("name", t.name);
                array.put(o);
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(KEY_MUSIC, array.toString()).apply();
    }

    private void refreshMusicList() {
        if (musicList == null) return;
        musicList.removeAllViews();
        musicEmpty.setVisibility(musicTracks.isEmpty() ? View.VISIBLE : View.GONE);
        for (int i = 0; i < musicTracks.size(); i++) {
            final int index = i;
            MusicTrack t = musicTracks.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_ls_card);
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(7);
            row.setLayoutParams(lp);
            TextView name = new TextView(this);
            name.setText(t.name);
            name.setTextColor(getColor(R.color.ls_text));
            name.setTextSize(12);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            TextView del = new TextView(this);
            del.setText("删除");
            del.setTextColor(getColor(R.color.ls_tertiary));
            del.setTextSize(9);
            del.setPadding(dp(10), dp(6), 0, dp(6));
            del.setOnClickListener(v -> confirmDeleteTrack(index));
            row.addView(name);
            row.addView(del);
            row.setOnClickListener(v -> playTrack(index));
            musicList.addView(row);
        }
    }

    private void confirmDeleteTrack(int index) {
        if (index < 0 || index >= musicTracks.size()) return;
        MusicTrack track = musicTracks.get(index);
        showLsConfirm("删除音乐？", "确定从列表删除“" + track.name + "”吗？", "删除",
                () -> {
                    musicTracks.remove(track);
                    if (currentTrackIndex == index) {
                        stopMusic();
                    } else if (currentTrackIndex > index) {
                        currentTrackIndex--;
                    }
                    saveMusic();
                    refreshMusicList();
                });
    }

    private void playTrack(int index) {
        if (index < 0 || index >= musicTracks.size()) return;
        stopPlayerOnly();
        currentTrackIndex = index;
        MusicTrack track = musicTracks.get(index);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, Uri.parse(track.uri));
            player.setOnPreparedListener(mp -> {
                mp.start();
                musicPlay.setText("暂停");
                musicState.setText(reactive ? "播放中 · 律动中" : "播放中");
                currentMusic.setText(track.name);
                if (hasAudioPermission()) ensureVisualizer();
            });
            player.setOnCompletionListener(mp -> playNext());
            player.setOnErrorListener((mp, what, extra) -> {
                toast("音乐播放失败");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            toast("音乐文件无法播放");
            stopPlayerOnly();
        }
        currentMusic.setText(track.name);
        musicState.setText("正在准备…");
    }

    private void togglePlay() {
        if (player == null) {
            if (!musicTracks.isEmpty()) playTrack(currentTrackIndex >= 0 ? currentTrackIndex : 0);
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                musicPlay.setText("播放");
                musicState.setText("已暂停");
            } else {
                player.start();
                musicPlay.setText("暂停");
                musicState.setText(reactive ? "播放中 · 律动中" : "播放中");
                if (reactive) ensureVisualizer();
            }
        } catch (Exception ignored) {
        }
    }

    private void playPrevious() {
        if (musicTracks.isEmpty()) return;
        int next = currentTrackIndex <= 0 ? musicTracks.size() - 1 : currentTrackIndex - 1;
        playTrack(next);
    }

    private void playNext() {
        if (musicTracks.isEmpty()) return;
        int next = currentTrackIndex < 0 || currentTrackIndex >= musicTracks.size() - 1
                ? 0 : currentTrackIndex + 1;
        playTrack(next);
    }

    private void ensureVisualizer() {
        if (player == null) return;
        if (!hasAudioPermission()) {
            return;
        }
        releaseVisualizer();
        try {
            visualizer = new Visualizer(player.getAudioSessionId());
            visualizer.setCaptureSize(1024);
            bassAverage = 0;
            previousBass = 0;
            lastBeatAt = 0;
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) { }
                @Override public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                    onFft(fft);
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
        } catch (Exception e) {
            reactive = false;
            reactiveSwitch.setChecked(false);
            toast("音频分析不可用");
        }
    }

    private void onFft(byte[] fft) {
        if (fft == null || fft.length < 8) return;
        double bass = 0;
        int limit = Math.min(fft.length, 48);
        float[] bars = new float[28];
        float maxBar = 0.01f;
        for (int i = 2; i + 1 < limit; i += 2) {
            double re = fft[i];
            double im = fft[i + 1];
            double magnitude = Math.sqrt(re * re + im * im);
            bass += magnitude;
            if (i / 2 - 1 < bars.length) {
                int index = i / 2 - 1;
                bars[index] = (float) magnitude;
                maxBar = Math.max(maxBar, bars[index]);
            }
        }
        if (musicBars != null) {
            for (int i = 0; i < bars.length; i++) bars[i] /= maxBar;
            musicBars.setLevels(bars);
        }
        long now = System.currentTimeMillis();
        if (bassAverage <= 0.01) {
            bassAverage = bass;
            previousBass = bass;
            return;
        }
        // 用较慢的基线 + 瞬时上升量判断鼓点，避免开头几下之后阈值被抬高。
        double threshold = bassAverage * (0.92 + sensitivity * 0.14);
        boolean rising = bass > previousBass * 1.03 || bass > bassAverage * 1.10;
        if (bass > threshold && rising && now - lastBeatAt > 150L) {
            lastBeatAt = now;
            if (reactive) handler.post(this::sendBeatFrame);
        }
        previousBass = bass;
        bassAverage = bassAverage * 0.97 + bass * 0.03;
        if (now - lastBeatAt > 3000L) {
            bassAverage = bass;
        }
    }

    private void sendBeatFrame() {
        if (!reactive || !powerOn || activeMode != MODE_MUSIC
                || ble == null || !ble.isReady()) return;
        int color = 1 + random.nextInt(9);
        if (color == lastBeatColor) color = color % 9 + 1;
        lastBeatColor = color;
        int brightness = 50 + random.nextInt(206);
        ble.sendReactive(AuroraProtocol.lsMusic(true, color, brightness));
        musicState.setText("鼓点律动中 · " + AuroraProtocol.LS_COLOR_NAMES[color - 1]);
    }

    private void releaseVisualizer() {
        if (visualizer != null) {
            try {
                visualizer.setEnabled(false);
                visualizer.release();
            } catch (Exception ignored) {
            }
            visualizer = null;
        }
    }

    private void stopPlayerOnly() {
        releaseVisualizer();
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        musicPlay.setText("播放");
    }

    private void stopMusic() {
        reactive = false;
        if (reactiveSwitch != null) reactiveSwitch.setChecked(false);
        stopPlayerOnly();
        currentTrackIndex = -1;
        if (musicState != null) musicState.setText("鼓点律动已关闭");
        if (musicBars != null) musicBars.clear();
    }

    private void exitMusicMode() {
        if (ble != null && ble.isReady()) {
            sendModeFrame(AuroraProtocol.lsMusic(false, selectedColor, 255), 3);
        }
        activeMode = MODE_NONE;
        stopMusic();
        showControlFromMusic();
        updateControlUi();
    }

    private void exitControl() {
        if (ble != null && ble.isReady()) {
            sendModeFrame(AuroraProtocol.lsPower(false), 3);
        }
        powerOn = false;
        activeMode = MODE_NONE;
        stopMusic();
        showHomePage();
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /* ===== ScanSheet.Host ===== */

    @Override
    public boolean hasBlePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void requestBlePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT}, REQ_BLE);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_BLE);
        }
    }

    @Override
    public boolean isBleSupported() {
        return ble != null && ble.isBleSupported();
    }

    @Override
    public boolean isBleEnabled() {
        return ble != null && ble.isBluetoothEnabled();
    }

    @Override
    public void requestEnableBle() {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE_BT);
    }

    @Override
    public boolean isLocationReady() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true;
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    @Override
    public void requestEnableLocation() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean startBleScan() {
        return ble != null && ble.startScan();
    }

    @Override
    public void stopBleScan() {
        if (ble != null) ble.stopScan(false);
    }

    @Override
    public void connectDevice(BleDevice device) {
        if (device == null) return;
        openControlOnReady = false;
        if (scanSheet != null) scanSheet.showConnecting(device.displayName());
        ble.connect(device);
    }

    @Override
    public void enterDemoFromSheet() {
        if (scanSheet != null) scanSheet.dismiss();
        toast("当前版本不提供演示模式");
    }

    @Override
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    public void showToast(String message) {
        toast(message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (scanSheet != null) scanSheet.onPermissionResult(granted);
        } else if (requestCode == REQ_AUDIO) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                reactive = true;
                reactiveSwitch.setChecked(true);
                if (player == null && !musicTracks.isEmpty()) playTrack(0);
                ensureVisualizer();
            } else {
                reactive = false;
                reactiveSwitch.setChecked(false);
                toast("没有录音权限，无法使用鼓点律动");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateManager.retryPendingInstall(this);
        if (returningFromFota && !ble.isReady()) {
            returningFromFota = false;
            handler.postDelayed(this::tryAutoConnect, 500L);
        }
    }

    @Override
    protected void onDestroy() {
        devLongPress.removeCallbacksAndMessages(null);
        handler.removeCallbacksAndMessages(null);
        stopMusic();
        if (scanSheet != null) scanSheet.dismiss();
        if (ble != null) ble.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (musicPage.getVisibility() == View.VISIBLE) {
            exitMusicMode();
        } else if (controlPage.getVisibility() == View.VISIBLE) {
            exitControl();
        } else if (profilePage.getVisibility() == View.VISIBLE) {
            showHomePage();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        if (TextUtils.isEmpty(message)) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
