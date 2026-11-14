package com.aurora.light;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** GitHub Pages 上的灯带固件清单 fota.json。 */
public final class FotaManifest {

    public final int versionCode;
    public final String versionName;
    public final String fileUrl;
    public final String sha256;
    public final String notes;
    public final boolean force;

    private FotaManifest(int versionCode, String versionName, String fileUrl,
                         String sha256, String notes, boolean force) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.fileUrl = fileUrl;
        this.sha256 = sha256;
        this.notes = notes;
        this.force = force;
    }

    public static FotaManifest fetch(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store");
        connection.setRequestProperty("Pragma", "no-cache");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "AuroraLight/" + BuildConfig.VERSION_NAME);
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                JSONObject json = new JSONObject(new String(out.toByteArray(), "UTF-8"));
                int version = json.optInt("versionCode", 0);
                String name = json.optString("versionName", "");
                String file = json.optString("fileUrl", "");
                if (version <= 0 || !file.startsWith("https://")) {
                    return null;
                }
                return new FotaManifest(version, name, file,
                        json.optString("sha256", ""),
                        json.optString("changelog", "修复了已知问题"),
                        json.optBoolean("force", false));
            }
        } finally {
            connection.disconnect();
        }
    }
}
