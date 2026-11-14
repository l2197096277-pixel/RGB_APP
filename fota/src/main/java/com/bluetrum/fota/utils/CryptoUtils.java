package com.bluetrum.fota.utils;

import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CryptoUtils {

    @Nullable
    public static byte[] getMD5(byte[] data) {
        try {
            // 使用MD5
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(data);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
