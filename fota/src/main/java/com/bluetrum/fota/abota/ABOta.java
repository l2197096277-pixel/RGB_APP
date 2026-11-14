package com.bluetrum.fota.abota;

import androidx.annotation.NonNull;

import com.bluetrum.fota.manager.DataReader;
import com.bluetrum.fota.manager.OtaError;

public class ABOta implements ABOtaManager.EventListener {

    // OTA Error
//    public static final int ERROR_CODE_DEVICE_REPORT        = 0x1000;
    public static final int ERROR_CODE_DEVICE_REFUSED       = 0x1001;
//    public static final int ERROR_CODE_NO_OTA_DATA          = 0x1002;
    public static final int ERROR_CODE_TIMEOUT              = 0x1003;
    public static final int ERROR_CODE_TWS_DISCONNECTED     = 0x1004;
    public static final int ERROR_CODE_DATA_READER_ERROR    = 0x1005;
    public static final int ERROR_CODE_UNKNOWN              = 0x1100;
    // Device report error
    // 设备报告的错误在OtaError中不一定能覆盖全，这里直接输出，范围是一个字节
    public static final int ERROR_CODE_SAME_FIRMWARE        = 1;
    public static final int ERROR_CODE_KEY_MISMATCH         = 2;
    public static final int ERROR_CODE_CRC_ERROR            = 11;

    public static final int OTA_STATUS_READY        = 0;
    public static final int OTA_STATUS_START        = 1;
    public static final int OTA_STATUS_UPDATING     = 2;
    public static final int OTA_STATUS_PAUSE        = 3;
    public static final int OTA_STATUS_CONTINUE     = 4;
    public static final int OTA_STATUS_SUCCESS      = 5;
    public static final int OTA_STATUS_WAIT_FINISH  = 6; // Compressed FW file, wait for uncompressed and write
    public static final int OTA_STATUS_FAIL         = 7;

    public interface SendCallback {
        void sendData(byte[] data);
    }

    public interface EventListener {
        // OTA状态
        void onStatusChanged(int status, int value);

        // FOTA会在准备环境时获取设备的一些信息，可根据需要进行取舍
        void onReceiveVersion(int version);
        void onReceiveTwsInfo(boolean isTws, boolean isTwsConnected);
        void onReceiveChannel(boolean isLeftChannel);
    }

    private SendCallback sendCallback;
    private EventListener eventListener;

    private final ABOtaManager otaManager;

    public ABOta() {
        this.otaManager = new ABOtaManager(this);
    }

    public void setSendCallback(SendCallback sendCallback) {
        this.sendCallback = sendCallback;
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void prepareToUpdate() {
        otaManager.prepareToUpdate();
    }

    public void setOtaData(@NonNull byte[] otaData) {
        otaManager.setOtaData(otaData);
    }

    public void setOtaFirmwareVersion(int version) {
        otaManager.setOtaFirmwareVersion(version);
    }

    public boolean isReadyToUpdate() {
        return otaManager.isReadyToUpdate();
    }

    public void setDataReader(@NonNull DataReader dataReader) {
        otaManager.setDataReader(dataReader);
    }

    public void startOTA() {
        otaManager.startOTA();
    }

    public boolean isUpdating() {
        return otaManager.isUpdating();
    }

    private void handleTWSMessage() {
        if (eventListener != null) {
            eventListener.onReceiveTwsInfo(otaManager.isTwsDevice(), otaManager.isTwsConnected());
        }
    }

    private void notifyStatusChanged(int status, int value) {
        if (eventListener != null) {
            eventListener.onStatusChanged(status, value);
        }
    }

    public void handleData(byte[] data) {
        otaManager.processData(data);
    }

    public void nextRun() {
        otaManager.nextRun();
    }

    /* ABOtaManager.EventListener */

    @Override
    public void sendOtaData(byte[] data) {
        if (sendCallback != null) {
            sendCallback.sendData(data);
        }
    }

    @Override
    public void onOtaReady() {
        notifyStatusChanged(OTA_STATUS_READY, 0);
    }

    @Override
    public void onOtaStart() {
        notifyStatusChanged(OTA_STATUS_START, 0);
    }

    @Override
    public void onOtaProgress(int progress) {
        notifyStatusChanged(OTA_STATUS_UPDATING, progress);
    }

    @Override
    public void onOtaStop() {
        // Device event, never happen here
    }

    @Override
    public void onOtaFinish() {
        if (otaManager.isCompressedData()) {
            notifyStatusChanged(OTA_STATUS_WAIT_FINISH, 0);
        } else {
            notifyStatusChanged(OTA_STATUS_SUCCESS, 0);
        }
    }

    @Override
    public void onOtaPause() {
        notifyStatusChanged(OTA_STATUS_PAUSE, 0);
    }

    @Override
    public void onOtaContinue() {
        notifyStatusChanged(OTA_STATUS_CONTINUE, 0);
    }

    @Override
    public void onOtaError(OtaError error) {
        if (error == OtaError.REPORT_FROM_DEVICE) {
            int deviceErrorCode = OtaError.REPORT_FROM_DEVICE.getDeviceErrorCode();
            notifyStatusChanged(OTA_STATUS_FAIL, deviceErrorCode);
        } else {
            int errorCode;
            switch (error) {
                case REFUSED_BY_DEVICE:
                    errorCode = ERROR_CODE_DEVICE_REFUSED;
                    break;
                case TIMEOUT_RECEIVE_RESPONSE:
                    errorCode = ERROR_CODE_TIMEOUT;
                    break;
                case DATA_READER_ERROR:
                    errorCode = ERROR_CODE_DATA_READER_ERROR;
                    break;
                default:
                    errorCode = ERROR_CODE_UNKNOWN;
            }
            notifyStatusChanged(OTA_STATUS_FAIL, errorCode);
        }
    }

    @Override
    public void onTWSDisconnected() {
        notifyStatusChanged(OTA_STATUS_FAIL, ERROR_CODE_TWS_DISCONNECTED);
    }

    @Override
    public void onReceiveVersion(int version) {
        if (eventListener != null) {
            eventListener.onReceiveVersion(version);
        }
    }

    @Override
    public void onReceiveIsTWS(boolean isTWS) {
        handleTWSMessage();
    }

    @Override
    public void onReceiveTWSConnected(boolean connected) {
        handleTWSMessage();
    }

    @Override
    public void onReceiveChannel(boolean isLeftChannel) {
        if (eventListener != null) {
            eventListener.onReceiveChannel(isLeftChannel);
        }
    }
}
