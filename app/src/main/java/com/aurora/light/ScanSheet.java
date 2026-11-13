package com.aurora.light;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 底部连接面板：权限引导、扫描列表、连接状态。 */
public class ScanSheet {

    public interface Host {
        boolean hasBlePermission();

        void requestBlePermission();

        boolean isBleSupported();

        boolean isBleEnabled();

        void requestEnableBle();

        boolean isLocationReady();

        void requestEnableLocation();

        boolean startBleScan();

        void stopBleScan();

        void connectDevice(BleDevice device);

        void enterDemoFromSheet();

        void openAppSettings();

        void showToast(String message);
    }

    private enum PanelMode {PERMISSION, DENIED, BLUETOOTH_OFF, LOCATION_OFF, UNSUPPORTED}

    private final Activity activity;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, BleDevice> devices = new LinkedHashMap<>();

    private Dialog dialog;
    private LinearLayout permissionPanel;
    private LinearLayout scanPanel;
    private LinearLayout emptyState;
    private LinearLayout deviceList;
    private ScrollView deviceScroll;
    private ProgressBar scanProgress;
    private TextView scanState;
    private TextView permissionTitle;
    private TextView permissionBody;
    private TextView grantButton;
    private TextView settingsButton;
    private TextView rescanButton;

    private PanelMode panelMode = PanelMode.PERMISSION;
    private boolean scanning;
    private boolean connecting;
    private boolean showing;
    private boolean lastScanFailed;

    private final Runnable refreshListRunnable = new Runnable() {
        @Override
        public void run() {
            rebuildDeviceList();
            if (scanning) {
                scanState.setText(devices.isEmpty()
                        ? "正在扫描，请稍候…"
                        : "正在扫描 · 已发现 " + devices.size() + " 台");
            }
        }
    };

    public ScanSheet(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    public boolean isShowing() {
        return showing && dialog != null && dialog.isShowing();
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        // 上一次关闭可能停在“正在扫描”状态，重新打开时必须复位。
        scanning = false;
        connecting = false;
        lastScanFailed = false;
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(R.layout.dialog_scan);
        dialog.setOnDismissListener(d -> {
            showing = false;
            scanning = false;
            connecting = false;
            lastScanFailed = false;
            handler.removeCallbacksAndMessages(null);
            host.stopBleScan();
            dialog = null;
        });

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.64f);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.SheetAnimation);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.80f));
        }

        bindViews();
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.80f));
        }
        showing = true;
        refresh();
    }

    private void bindViews() {
        permissionPanel = dialog.findViewById(R.id.permissionPanel);
        scanPanel = dialog.findViewById(R.id.scanPanel);
        emptyState = dialog.findViewById(R.id.emptyState);
        deviceList = dialog.findViewById(R.id.deviceList);
        deviceScroll = dialog.findViewById(R.id.deviceScroll);
        scanProgress = dialog.findViewById(R.id.scanProgress);
        scanState = dialog.findViewById(R.id.scanState);
        permissionTitle = dialog.findViewById(R.id.permissionTitle);
        permissionBody = dialog.findViewById(R.id.permissionBody);
        grantButton = dialog.findViewById(R.id.grantButton);
        settingsButton = dialog.findViewById(R.id.settingsButton);
        rescanButton = dialog.findViewById(R.id.rescanButton);

        dialog.findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());
        dialog.findViewById(R.id.demoButton).setOnClickListener(v -> host.enterDemoFromSheet());
        rescanButton.setOnClickListener(v -> {
            if (host.hasBlePermission() && host.isBleEnabled() && host.isLocationReady()) {
                beginScan();
            } else {
                refresh();
            }
        });
        settingsButton.setOnClickListener(v -> host.openAppSettings());
        grantButton.setOnClickListener(v -> {
            switch (panelMode) {
                case BLUETOOTH_OFF:
                    host.requestEnableBle();
                    break;
                case LOCATION_OFF:
                    host.requestEnableLocation();
                    break;
                case UNSUPPORTED:
                    break;
                case DENIED:
                case PERMISSION:
                default:
                    host.requestBlePermission();
                    break;
            }
        });
    }

    /** 从系统设置/权限弹窗返回后重新检测，不重置已发现的设备。 */
    public void refresh() {
        if (!isShowing()) {
            return;
        }
        if (!host.isBleSupported()) {
            showPanel(PanelMode.UNSUPPORTED);
            return;
        }
        if (!host.hasBlePermission()) {
            showPanel(PanelMode.PERMISSION);
            return;
        }
        if (!host.isBleEnabled()) {
            showPanel(PanelMode.BLUETOOTH_OFF);
            return;
        }
        if (!host.isLocationReady()) {
            showPanel(PanelMode.LOCATION_OFF);
            return;
        }
        permissionPanel.setVisibility(View.GONE);
        scanPanel.setVisibility(View.VISIBLE);
        if (!scanning && !connecting) {
            beginScan();
        }
    }

    private void showPanel(PanelMode mode) {
        panelMode = mode;
        connecting = false;
        scanning = false;
        scanPanel.setVisibility(View.GONE);
        permissionPanel.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.GONE);
        grantButton.setVisibility(View.VISIBLE);
        switch (mode) {
            case BLUETOOTH_OFF:
                permissionTitle.setText(R.string.bt_off_title);
                permissionBody.setText(R.string.bt_off_body);
                grantButton.setText(R.string.bt_enable);
                break;
            case LOCATION_OFF:
                permissionTitle.setText("请开启定位服务");
                permissionBody.setText("Android 11 及以下系统要求打开定位开关，才能扫描低功耗蓝牙。位置信息不会被读取或保存。");
                grantButton.setText("打开定位设置");
                break;
            case UNSUPPORTED:
                permissionTitle.setText("此手机不支持低功耗蓝牙");
                permissionBody.setText("无法扫描 BLE 灯带。你仍然可以进入演示模式体验界面和控制逻辑。");
                grantButton.setVisibility(View.GONE);
                break;
            case DENIED:
                permissionTitle.setText("权限未授予");
                permissionBody.setText("需要「附近的设备」权限才能发现灯光。你可以在系统设置中开启，或先体验演示模式。");
                grantButton.setText("重新授权");
                settingsButton.setVisibility(View.VISIBLE);
                break;
            case PERMISSION:
            default:
                permissionTitle.setText(R.string.permission_title);
                permissionBody.setText(R.string.permission_body);
                grantButton.setText(R.string.permission_allow);
                settingsButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void beginScan() {
        // 先停掉可能仍在运行的旧扫描，避免 ALREADY_STARTED / 频率限制。
        host.stopBleScan();
        devices.clear();
        rebuildDeviceList();
        lastScanFailed = false;
        scanPanel.setVisibility(View.VISIBLE);
        permissionPanel.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        deviceScroll.setVisibility(View.GONE);
        scanProgress.setVisibility(View.VISIBLE);
        scanState.setTextColor(activity.getColor(R.color.text_secondary));
        scanState.setText(R.string.scanning);
        rescanButton.setEnabled(true);
        rescanButton.setAlpha(1f);
        scanning = true;
        boolean started = host.startBleScan();
        if (!started) {
            scanning = false;
            scanProgress.setVisibility(View.GONE);
            scanState.setTextColor(activity.getColor(R.color.danger));
            scanState.setText("无法启动扫描，请检查蓝牙与权限");
        }
    }

    public void addDevice(BleDevice device) {
        if (!isShowing() || device == null || connecting) {
            return;
        }
        BleDevice old = devices.get(device.address);
        if (old == null || device.rssi > old.rssi) {
            devices.put(device.address, device);
        }
        if (devices.isEmpty()) {
            return;
        }
        emptyState.setVisibility(View.GONE);
        deviceScroll.setVisibility(View.VISIBLE);
        handler.removeCallbacks(refreshListRunnable);
        handler.postDelayed(refreshListRunnable, 220L);
    }

    private void rebuildDeviceList() {
        if (deviceList == null) {
            return;
        }
        deviceList.removeAllViews();
        List<BleDevice> list = new ArrayList<>(devices.values());
        Collections.sort(list, (a, b) -> {
            if (a.looksLikeAurora() != b.looksLikeAurora()) {
                return a.looksLikeAurora() ? -1 : 1;
            }
            return Integer.compare(b.rssi, a.rssi);
        });
        for (BleDevice device : list) {
            deviceList.addView(createDeviceRow(device));
        }
        boolean hasDevices = !list.isEmpty();
        emptyState.setVisibility(hasDevices ? View.GONE : View.VISIBLE);
        deviceScroll.setVisibility(hasDevices ? View.VISIBLE : View.GONE);
    }

    private View createDeviceRow(BleDevice device) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(9), dp(11));
        row.setBackgroundResource(R.drawable.bg_device_row);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        FrameLayout iconBox = new FrameLayout(activity);
        iconBox.setBackgroundResource(R.drawable.bg_icon_circle);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconBox.setLayoutParams(iconParams);
        ImageView icon = new ImageView(activity);
        FrameLayout.LayoutParams iconImageParams =
                new FrameLayout.LayoutParams(dp(19), dp(19));
        iconImageParams.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconImageParams);
        icon.setImageResource(R.drawable.ic_bluetooth);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                activity.getColor(R.color.mint)));
        iconBox.addView(icon);
        row.addView(iconBox);

        LinearLayout middle = new LinearLayout(activity);
        middle.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        middleParams.setMarginStart(dp(11));
        middleParams.setMarginEnd(dp(8));
        middle.setLayoutParams(middleParams);

        LinearLayout nameRow = new LinearLayout(activity);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(activity);
        name.setText(device.displayName());
        name.setTextColor(activity.getColor(R.color.text_primary));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (device.looksLikeAurora()) {
            TextView tag = new TextView(activity);
            tag.setText(R.string.recommended);
            tag.setTextColor(activity.getColor(R.color.mint_soft));
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f);
            tag.setPadding(dp(6), dp(2), dp(6), dp(2));
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(0x2E5BF0D0);
            tagBg.setCornerRadius(dp(6));
            tag.setBackground(tagBg);
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tagParams.setMarginStart(dp(6));
            nameRow.addView(tag, tagParams);
        }
        middle.addView(nameRow);

        TextView address = new TextView(activity);
        address.setText(device.address + "  ·  " + device.signalText());
        address.setTextColor(activity.getColor(R.color.text_tertiary));
        address.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        address.setSingleLine(true);
        address.setPadding(0, dp(3), 0, 0);
        middle.addView(address);
        row.addView(middle);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(
                activity.getColor(R.color.text_tertiary)));
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        chevron.setLayoutParams(chevronParams);
        row.addView(chevron);

        row.setContentDescription("连接 " + device.displayName());
        row.setOnClickListener(v -> {
            if (connecting) {
                return;
            }
            showConnecting(device.displayName());
            host.connectDevice(device);
        });
        return row;
    }

    public void showConnecting(String name) {
        if (!isShowing()) {
            return;
        }
        connecting = true;
        scanning = false;
        scanPanel.setVisibility(View.VISIBLE);
        permissionPanel.setVisibility(View.GONE);
        scanProgress.setVisibility(View.VISIBLE);
        scanState.setTextColor(activity.getColor(R.color.text_secondary));
        scanState.setText(activity.getString(R.string.connecting_to, name));
        rescanButton.setEnabled(false);
        rescanButton.setAlpha(0.45f);
    }

    public void onConnecting(String name) {
        showConnecting(name);
    }

    public void onConnected() {
        dismiss();
    }

    public void onScanFinished() {
        if (!isShowing()) {
            return;
        }
        if (lastScanFailed) {
            return;
        }
        scanning = false;
        scanProgress.setVisibility(View.GONE);
        rescanButton.setEnabled(true);
        rescanButton.setAlpha(1f);
        if (devices.isEmpty()) {
            scanState.setTextColor(activity.getColor(R.color.text_secondary));
            scanState.setText(R.string.scan_none);
            emptyState.setVisibility(View.VISIBLE);
            deviceScroll.setVisibility(View.GONE);
        } else {
            scanState.setTextColor(activity.getColor(R.color.text_secondary));
            scanState.setText(activity.getString(R.string.scan_found, devices.size()));
        }
    }

    public void onScanError(String message) {
        if (!isShowing()) {
            return;
        }
        lastScanFailed = true;
        scanning = false;
        scanProgress.setVisibility(View.GONE);
        scanState.setTextColor(activity.getColor(R.color.danger));
        scanState.setText(message);
        rescanButton.setEnabled(true);
        rescanButton.setAlpha(1f);
        if (devices.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            deviceScroll.setVisibility(View.GONE);
        }
    }

    public void onConnectFailed(String message) {
        if (!isShowing()) {
            return;
        }
        connecting = false;
        scanning = false;
        scanProgress.setVisibility(View.GONE);
        scanState.setTextColor(activity.getColor(R.color.danger));
        scanState.setText(message);
        rescanButton.setEnabled(true);
        rescanButton.setAlpha(1f);
    }

    public void onPermissionResult(boolean granted) {
        if (!isShowing()) {
            return;
        }
        if (granted) {
            refresh();
        } else {
            showPanel(PanelMode.DENIED);
        }
    }

    public void onBluetoothResult(boolean enabled) {
        if (!isShowing()) {
            return;
        }
        if (enabled) {
            refresh();
        } else {
            showPanel(PanelMode.BLUETOOTH_OFF);
        }
    }

    public void dismiss() {
        scanning = false;
        connecting = false;
        lastScanFailed = false;
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        showing = false;
        handler.removeCallbacksAndMessages(null);
        host.stopBleScan();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
