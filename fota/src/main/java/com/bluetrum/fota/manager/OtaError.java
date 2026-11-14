package com.bluetrum.fota.manager;

public enum OtaError {

    /* 蓝牙相关错误 */
    // REMOVED

    /* 设备相关错误 */
    REPORT_FROM_DEVICE(2000, "有点问题，错误代码"),
    REFUSED_BY_DEVICE(2001, "设备拒绝了升级"),
    TIMEOUT_RECEIVE_RESPONSE(2002, "等待设备回复超时"),

    /* 数据文件相关错误 */
    DATA_READER_ERROR(3000, "DataReader错误");

    private final int errorCode;
    private final String description;

    private byte deviceErrorCode; // 设备上报的错误代码，仅用于REPORT_FROM_DEVICE

    OtaError(int errorCode, String description) {
        this.errorCode = errorCode;
        this.description = description;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    public byte getDeviceErrorCode() {
        return deviceErrorCode;
    }

    public void setDeviceErrorCode(byte deviceErrorCode) {
        this.deviceErrorCode = deviceErrorCode;
    }

}
