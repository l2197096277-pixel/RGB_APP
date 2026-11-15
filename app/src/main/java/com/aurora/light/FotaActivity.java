package com.aurora.light;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bluetrum.fota.abota.ABOta;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 灯带固件 OTA。
 * 使用官方 AB FOTA 协议：FF12 服务，FF14 Notify，FF15 Write No Response。
 */
public class FotaActivity extends Activity implements ABOta.EventListener, FotaBleClient.Listener {

    public static final String EXTRA_ADDRESS = "fota_address";
    public static final String EXTRA_NAME = "fota_name";
    public static final String EXTRA_DEV_MODE = "fota_dev_mode";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ABOta abOta = new ABOta();
    private FotaBleClient ble;
    private SharedPreferences prefs;

    private TextView deviceView;
    private TextView currentView;
    private TextView targetView;
    private TextView percentView;
    private TextView statusView;
    private TextView actionView;
    private TextView closeView;
    private ProgressBar progressBar;

    private String address;
    private String deviceName;
    private FotaManifest manifest;
    private boolean manifestLoaded;
    private boolean versionLoaded;
    private boolean userConfirmed;
    private boolean startRequested;
    private boolean otaStarted;
    private boolean busy;
    private boolean success;
    private boolean developerMode;
    private boolean userDeclined;
    private boolean promptShown;
    private Dialog promptDialog;
    private int currentVersion = -1;
    private final StringBuilder logBuffer = new StringBuilder();
    private Dialog logDialog;
    private TextView logTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fota);
        prefs = getSharedPreferences("aurora_settings", MODE_PRIVATE);
        address = getIntent().getStringExtra(EXTRA_ADDRESS);
        deviceName = getIntent().getStringExtra(EXTRA_NAME);
        developerMode = getIntent().getBooleanExtra(EXTRA_DEV_MODE, false);
        if (address == null || address.trim().isEmpty()) {
            Toast.makeText(this, "缺少设备地址", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        deviceView = findViewById(R.id.fotaDevice);
        currentView = findViewById(R.id.fotaCurrent);
        targetView = findViewById(R.id.fotaTarget);
        percentView = findViewById(R.id.fotaPercent);
        statusView = findViewById(R.id.fotaStatus);
        actionView = findViewById(R.id.fotaAction);
        closeView = findViewById(R.id.fotaClose);
        progressBar = findViewById(R.id.fotaProgress);

        deviceView.setText(deviceName == null ? "灯带" : deviceName);
        int last = prefs.getInt("last_fw_version", 0);
        if (last > 0) {
            currentView.setText("当前固件：" + versionText(last));
        }
        actionView.setOnClickListener(v -> finish());
        TextView logButton = findViewById(R.id.fotaLog);
        logButton.setVisibility(developerMode ? View.VISIBLE : View.GONE);
        logButton.setOnClickListener(v -> showLogDialog());
        closeView.setOnClickListener(v -> {
            if (busy) {
                Toast.makeText(this, "升级过程中不能退出", Toast.LENGTH_SHORT).show();
            } else {
                finish();
            }
        });

        abOta.setSendCallback(this::sendOtaData);
        abOta.setEventListener(this);
        ble = new FotaBleClient(this, this);

        loadManifest();
        statusView.setText("正在连接灯带…");
        main.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                ble.connect(address);
            }
        }, 350L);
    }

    private void sendOtaData(byte[] data) {
        ble.send(data);
    }

    private void loadManifest() {
        new Thread(() -> {
            FotaManifest fetched = null;
            try {
                fetched = FotaManifest.fetch(BuildConfig.FOTA_URL);
            } catch (Exception ignored) {
            }
            final FotaManifest result = fetched;
            main.post(() -> {
                manifest = result;
                manifestLoaded = true;
                targetView.setText(result == null
                        ? "最新固件：暂无"
                        : "最新固件：" + versionText(result.versionCode));
                applyCheck();
            });
        }, "aurora-fota-manifest").start();
    }

    private void applyCheck() {
        if (userDeclined) {
            return;
        }
        if (!manifestLoaded || !versionLoaded) {
            return;
        }
        if (manifest == null) {
            statusView.setText("暂时没有可用的固件升级包。\n请稍后再试，或联系管理员。");
            showAction("返回");
            return;
        }
        if (!manifest.force && currentVersion >= manifest.versionCode) {
            statusView.setText("当前已是最新固件。");
            prefs.edit().putInt("last_fw_version", currentVersion)
                    .putBoolean("fw_update_available", false).apply();
            showAction("返回");
            return;
        }

        prefs.edit().putInt("last_fw_version", currentVersion)
                .putBoolean("fw_update_available", true).apply();
        appendLog("发现固件：当前 " + (currentVersion > 0 ? versionText(currentVersion) : "未知")
                + "，目标 " + manifest.versionName + "，force=" + manifest.force);
        showConfirmDialog();
    }

    private void showConfirmDialog() {
        if (promptShown || userDeclined) {
            return;
        }
        promptShown = true;
        String msg = "当前固件：" + (currentVersion > 0 ? versionText(currentVersion) : "未知")
                + "\n最新固件：" + (manifest.versionName == null || manifest.versionName.isEmpty()
                ? versionText(manifest.versionCode) : manifest.versionName)
                + "\n\n修复了已知问题\n\n是否升级？";
        showDialog("发现新固件", msg, "否", "是", () -> {
            showWarningDialog();
        }, () -> {
            userDeclined = true;
            busy = false;
            success = true;
            finish();
        });
    }

    private void showWarningDialog() {
        String msg = "升级过程中请保持：\n\n"
                + "· 手机与灯带距离接近\n"
                + "· 不要退出 App、锁屏或切后台\n"
                + "· 不要断开蓝牙\n"
                + "· 保持灯带电量充足\n\n"
                + "升级中断可能导致灯带无法启动。";
        showDialog("升级前请注意", msg, "退出", "了解", this::startDownload, this::finish);
    }

    private void startDownload() {
        if (busy) {
            return;
        }
        userConfirmed = true;
        busy = true;
        appendLog("开始下载：" + manifest.fileUrl);
        appendLog("目标版本：" + manifest.versionName + " / " + manifest.versionCode
                + " / force=" + manifest.force);
        closeView.setVisibility(View.GONE);
        progressBar.setProgress(0);
        percentView.setText("0%");
        statusView.setText("正在下载固件…");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        new Thread(() -> {
            try {
                byte[] data = download(manifest.fileUrl);
                String sha = sha256(data);
                if (manifest.sha256 != null && !manifest.sha256.trim().isEmpty()
                        && !sha.equalsIgnoreCase(manifest.sha256.trim())) {
                    throw new IllegalStateException("固件校验失败，文件可能不完整");
                }
                main.post(() -> {
                    statusView.setText("固件下载完成，正在准备升级…");
                    appendLog("下载完成，大小=" + data.length + "，SHA-256=" + sha);
                    abOta.setOtaFirmwareVersion(manifest.force ? 0xFFFF : manifest.versionCode);
                    appendLog("OTA 请求版本：" + (manifest.force ? "0xFFFF（强制）" : "0x" + Integer.toHexString(manifest.versionCode)));
                    abOta.setOtaData(data);
                    if (abOta.isReadyToUpdate() && !otaStarted) {
                        startOta();
                    }
                });
            } catch (Exception e) {
                fail(e.getMessage() == null ? "固件下载失败" : e.getMessage());
            }
        }, "aurora-fota-download").start();
    }

    private byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "AuroraLight/" + BuildConfig.VERSION_NAME);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("下载失败（HTTP " + connection.getResponseCode() + "）");
            }
            int total = connection.getContentLength();
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                long done = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int p = (int) (done * 100 / total);
                        main.post(() -> setProgress(p, "正在下载固件…"));
                    }
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private void startOta() {
        if (otaStarted) {
            return;
        }
        otaStarted = true;
        statusView.setText("正在升级灯带固件…");
        abOta.startOTA();
    }

    private void setProgress(int p, String status) {
        int value = Math.max(0, Math.min(100, p));
        progressBar.setProgress(value);
        percentView.setText(value + "%");
        if (status != null) {
            statusView.setText(status);
        }
    }

    private void showAction(String text) {
        actionView.setText(text);
        actionView.setVisibility(View.VISIBLE);
        closeView.setVisibility(View.VISIBLE);
        busy = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void fail(String message) {
        if (userDeclined || !userConfirmed) {
            busy = false;
            success = true;
            main.post(() -> {
                statusView.setText("已取消升级");
                showAction("返回");
            });
            return;
        }
        busy = false;
        success = false;
        main.post(() -> {
            appendLog("失败：" + message);
            setProgress(0, null);
            statusView.setText("升级失败：" + message
                    + "\n\n请保持灯带通电，靠近手机后重试。\n如果多次失败，请联系售后，不要反复断电。");
            closeView.setVisibility(View.VISIBLE);
            showAction("退出");
            if (ble != null) {
                ble.close();
            }
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    private void finishSuccess() {
        busy = false;
        success = true;
        setProgress(100, "升级成功\n\n请返回 App 重新连接蓝牙。");
        prefs.edit().putInt("last_fw_version", manifest.versionCode)
                .putBoolean("fw_update_available", false).apply();
        appendLog("升级成功，记录固件版本=" + manifest.versionCode);
        actionView.setText("返回并重新连接");
        actionView.setVisibility(View.VISIBLE);
        closeView.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        main.postDelayed(() -> {
            if (ble != null) {
                ble.close();
            }
        }, 1500);
    }

    private void finishWait() {
        busy = false;
        success = true;
        setProgress(100, "升级包已发送完成。\n设备正在解压并重启，请勿断电。\n\n完成后返回 App 重新连接。");
        prefs.edit().putInt("last_fw_version", manifest.versionCode)
                .putBoolean("fw_update_available", false).apply();
        appendLog("升级包发送完成，进入等待设备解压状态");
        actionView.setText("完成并返回");
        actionView.setVisibility(View.VISIBLE);
        closeView.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void showDialog(String title, String message, String noText, String yesText,
                            Runnable onYes, Runnable onNo) {
        if (promptDialog != null && promptDialog.isShowing()) {
            promptDialog.dismiss();
        }
        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        promptDialog = dialog;
        dialog.setContentView(R.layout.dialog_fota_prompt);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((d, keyCode, event) ->
                keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.62f;
            lp.gravity = android.view.Gravity.CENTER;
            window.setAttributes(lp);
        }
        TextView titleView = dialog.findViewById(R.id.fotaDialogTitle);
        TextView messageView = dialog.findViewById(R.id.fotaDialogMessage);
        TextView no = dialog.findViewById(R.id.fotaDialogNo);
        TextView yes = dialog.findViewById(R.id.fotaDialogYes);
        titleView.setText(title);
        messageView.setText(message);
        no.setText(noText);
        yes.setText(yesText);
        no.setOnClickListener(v -> {
            dialog.dismiss();
            if (onNo != null) onNo.run();
        });
        yes.setOnClickListener(v -> {
            dialog.dismiss();
            if (onYes != null) onYes.run();
        });
        dialog.show();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.88f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /* FotaBleClient.Listener */

    @Override
    public void onReady() {
        runOnUiThread(() -> {
            appendLog("BLE Ready，开始读取设备信息");
            statusView.setText("正在读取固件版本…");
            abOta.prepareToUpdate();
        });
    }

    @Override
    public void onData(byte[] data) {
        abOta.handleData(data);
    }

    @Override
    public void onSendComplete() {
        abOta.nextRun();
    }

    @Override
    public void onLog(String line) {
        appendLog(line);
    }

    @Override
    public void onDisconnected(String reason) {
        appendLog("断开：" + reason);
        if (userDeclined) {
            return;
        }
        if (busy && !success) {
            fail(reason == null ? "蓝牙连接已断开" : reason);
        }
    }

    @Override
    public void onError(String message) {
        appendLog("BLE 错误：" + message);
        if (userDeclined) {
            return;
        }
        if (promptShown && !userConfirmed) {
            // 还没确认升级时，BLE 断开/错误不能覆盖“发现新固件”提示，更不能显示升级失败。
            return;
        }
        if (busy) {
            fail(message);
        } else {
            runOnUiThread(() -> {
                statusView.setText(message);
                showAction("返回");
            });
        }
    }

    /* ABOta.EventListener */

    @Override
    public void onStatusChanged(int status, int value) {
        runOnUiThread(() -> {
            if (userDeclined) {
                return;
            }
            switch (status) {
                case ABOta.OTA_STATUS_READY:
                    appendLog("OTA Ready");
                    if (userConfirmed && !otaStarted && abOta.isReadyToUpdate()) {
                        startOta();
                    }
                    break;
                case ABOta.OTA_STATUS_START:
                    appendLog("OTA START");
                    busy = true;
                    setProgress(0, "正在升级灯带固件…");
                    break;
                case ABOta.OTA_STATUS_UPDATING:
                    setProgress(value, "正在升级灯带固件…");
                    break;
                case ABOta.OTA_STATUS_PAUSE:
                    appendLog("OTA PAUSE");
                    statusView.setText("升级已暂停，等待设备继续…");
                    break;
                case ABOta.OTA_STATUS_CONTINUE:
                    appendLog("OTA CONTINUE");
                    statusView.setText("设备已继续升级…");
                    break;
                case ABOta.OTA_STATUS_SUCCESS:
                    appendLog("OTA SUCCESS");
                    finishSuccess();
                    break;
                case ABOta.OTA_STATUS_WAIT_FINISH:
                    appendLog("OTA WAIT_FINISH");
                    finishWait();
                    break;
                case ABOta.OTA_STATUS_FAIL:
                    appendLog("OTA FAIL " + value);
                    fail(errorText(value));
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onReceiveVersion(int version) {
        runOnUiThread(() -> {
            currentVersion = version;
            versionLoaded = true;
            currentView.setText("当前固件：" + versionText(version));
            appendLog("当前固件：" + versionText(version) + " (0x" + Integer.toHexString(version) + ")");
            if (busy || userConfirmed || userDeclined) {
                return;
            }
            applyCheck();
        });
    }

    @Override
    public void onReceiveTwsInfo(boolean isTws, boolean isTwsConnected) {
    }

    @Override
    public void onReceiveChannel(boolean isLeftChannel) {
    }

    private String errorText(int code) {
        switch (code) {
            case ABOta.ERROR_CODE_SAME_FIRMWARE:
                return "当前固件与升级包相同";
            case ABOta.ERROR_CODE_KEY_MISMATCH:
                return "固件 KEY 不匹配，请联系管理员确认型号";
            case ABOta.ERROR_CODE_CRC_ERROR:
                return "固件校验错误，请重新下载";
            case ABOta.ERROR_CODE_DEVICE_REFUSED:
                return "设备拒绝升级";
            case ABOta.ERROR_CODE_TIMEOUT:
                return "等待设备回复超时";
            case ABOta.ERROR_CODE_TWS_DISCONNECTED:
                return "TWS 连接已断开";
            case ABOta.ERROR_CODE_DATA_READER_ERROR:
                return "固件文件读取失败";
            default:
                return "设备返回错误（" + code + "）";
        }
    }

    private static String versionText(int version) {
        int a = (version >> 12) & 0x0F;
        int b = (version >> 8) & 0x0F;
        int c = (version >> 4) & 0x0F;
        int d = version & 0x0F;
        return "v" + a + "." + b + "." + c + "." + d;
    }

    private void appendLog(String line) {
        String stamp = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        main.post(() -> {
            logBuffer.append(stamp).append("  ").append(line).append('\n');
            if (logBuffer.length() > 24000) {
                logBuffer.delete(0, logBuffer.length() - 18000);
            }
            if (logTextView != null) {
                logTextView.setText(logBuffer.toString());
                if (logDialog != null && logDialog.isShowing()) {
                    android.view.View scroll = logDialog.findViewById(R.id.fotaLogScroll);
                    if (scroll instanceof android.widget.ScrollView) {
                        ((android.widget.ScrollView) scroll).fullScroll(130);
                    }
                }
            }
        });
    }

    private void showLogDialog() {
        if (logDialog != null && logDialog.isShowing()) {
            return;
        }
        logDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        logDialog.setContentView(R.layout.dialog_fota_log);
        logDialog.setCanceledOnTouchOutside(true);
        logTextView = logDialog.findViewById(R.id.fotaLogText);
        logTextView.setText(logBuffer.toString());
        logDialog.findViewById(R.id.fotaLogClose).setOnClickListener(v -> {
            logDialog.dismiss();
            logTextView = null;
        });
        Window window = logDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.55f;
            lp.gravity = android.view.Gravity.CENTER;
            window.setAttributes(lp);
        }
        logDialog.show();
        if (window != null) {
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.94f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format(Locale.CHINA, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    @Override
    public void onBackPressed() {
        if (busy) {
            Toast.makeText(this, "升级正在进行，不能退出", Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (ble != null) {
            ble.close();
        }
        super.onDestroy();
    }
}
