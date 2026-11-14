package com.bluetrum.fota.manager;

import androidx.annotation.NonNull;

import com.bluetrum.fota.log.Logger;
import com.bluetrum.fota.utils.CryptoUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

final class OtaDataReader implements DataReader {

    private static final String TAG = "OtaDataReader";

    private final byte[] otaData;

    public OtaDataReader(@NonNull byte[] otaData) {
        this.otaData = otaData;
    }

    @Override
    public void open() throws IOException {
        Logger.d(TAG, "open");
        // Do nothing
    }

    @Override
    public int getSize() throws IOException {
        Logger.d(TAG, "getSize: " + otaData.length);
        return otaData.length;
    }

    @NonNull
    @Override
    public byte[] getHash() throws IOException {
        Logger.d(TAG, "getHash");
        return getHash(otaData);
    }

    @Override
    public int read(int srcPos, byte[] dst, int dstPos, int length) throws IOException {
        Logger.d(TAG, String.format(Locale.US, "read: srcPos=%d, dstPos=%d, length=%d", srcPos, dstPos, length));
        System.arraycopy(otaData, srcPos, dst, dstPos, length);
        return length;
    }

    @Override
    public void close() throws IOException {
        Logger.d(TAG, "close");
        // Do nothing
    }

    /* Private */

    @NonNull
    private byte[] getHash(byte[] data) {
        byte[] md5 = CryptoUtils.getMD5(data);
        if (md5 != null) {
            byte[] hash = new byte[4];
            System.arraycopy(md5, 0, hash, 0, 4);
            return hash;
        } else {
            return new byte[]{ (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        }
    }
}
