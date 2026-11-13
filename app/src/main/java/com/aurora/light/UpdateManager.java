package com.aurora.light;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * GitHub Releases + Pages 更新检查。
 *
 * update.json:
 * {
 *   "versionCode": 10,
 *   "versionName": "1.0.9",
 *   "apkUrl": "https://github.com/.../AuroraLight-v1.0.9.apk",
 *   "sha256": "...",
 *   "changelog": "1. ...\n2. ...",
 *   "force": false
 * }
 */
public final class UpdateManager {

    private static File pendingApk;

    private UpdateManager() {
    }

    public static void checkOnLaunch(Activity activity) {
        String url = BuildConfig.UPDATE_URL;
        if (TextUtils.isEmpty(url) || url.contains("YOUR_GITHUB_USER")) {
            return;
        }
        new Thread(() -> {
            try {
                UpdateInfo info = fetch(url);
                if (info == null || info.versionCode <= BuildConfig.VERSION_CODE) {
                    return;
                }
                activity.runOnUiThread(() -> showUpdateDialog(activity, info));
            } catch (Exception ignored) {
                // 更新检查失败不打扰用户；下次启动再试。
            }
        }, "aurora-update-check").start();
    }

    private static UpdateInfo fetch(String manifestUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(manifestUrl).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AuroraLight/" + BuildConfig.VERSION_NAME);
        connection.setInstanceFollowRedirects(true);
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                return null;
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                byte[] data = readAll(in);
                JSONObject json = new JSONObject(new String(data, "UTF-8"));
                UpdateInfo info = new UpdateInfo();
                info.versionCode = json.optInt("versionCode", 0);
                info.versionName = json.optString("versionName", "");
                info.apkUrl = json.optString("apkUrl", "");
                info.sha256 = json.optString("sha256", "");
                info.changelog = json.optString("changelog", "修复问题并优化体验");
                info.force = json.optBoolean("force", false);
                if (info.versionCode <= 0 || !info.apkUrl.startsWith("https://")) {
                    return null;
                }
                return info;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void showUpdateDialog(Activity activity, UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle("发现新版本 " + info.versionName)
                .setMessage(info.changelog + "\n\n当前版本：" + BuildConfig.VERSION_NAME)
                .setPositiveButton("立即更新", (dialog, which) -> downloadAndInstall(activity, info))
                .setCancelable(!info.force);
        if (info.force) {
            builder.setNegativeButton("退出", (dialog, which) -> activity.finish());
        } else {
            builder.setNegativeButton("稍后", null);
        }
        builder.show();
    }

    private static void downloadAndInstall(Activity activity, UpdateInfo info) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setTitle("正在下载更新");
        progress.setMessage("请保持网络连接…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setCancelable(false);
        progress.setMax(100);
        progress.show();

        new Thread(() -> {
            File apk = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "AuroraLight-update.apk");
            try {
                download(info.apkUrl, apk, progress, activity);
                String actual = sha256(apk);
                if (!TextUtils.isEmpty(info.sha256)
                        && !actual.equalsIgnoreCase(info.sha256.trim())) {
                    throw new IllegalStateException("APK 校验失败，请重试");
                }
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    pendingApk = apk;
                    installOrRequestPermission(activity, apk);
                });
            } catch (Exception e) {
                if (apk.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                }
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(activity,
                            e.getMessage() == null ? "更新下载失败" : e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "aurora-update-download").start();
    }

    private static void download(String url, File target, ProgressDialog progress,
                                 Activity activity) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "AuroraLight/" + BuildConfig.VERSION_NAME);
        try {
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("下载失败（HTTP " + code + "）");
            }
            int total = connection.getContentLength();
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("无法创建下载目录");
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                long done = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        int p = (int) (done * 100 / total);
                        activity.runOnUiThread(() -> progress.setProgress(p));
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    public static void retryPendingInstall(Activity activity) {
        if (pendingApk != null && pendingApk.exists() && canInstall(activity)) {
            File apk = pendingApk;
            pendingApk = null;
            install(activity, apk);
        }
    }

    private static void installOrRequestPermission(Activity activity, File apk) {
        if (!canInstall(activity)) {
            Toast.makeText(activity,
                    "请允许安装未知应用，返回后会自动继续安装",
                    Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(activity, "请在系统设置中允许安装未知应用",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        install(activity, apk);
    }

    private static boolean canInstall(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return activity.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private static void install(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".fileprovider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "无法打开安装界面：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format(Locale.CHINA, "%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static final class UpdateInfo {
        int versionCode;
        String versionName;
        String apkUrl;
        String sha256;
        String changelog;
        boolean force;
    }
}
