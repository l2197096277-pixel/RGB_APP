package com.aurora.light;

/** 扫描到的一台 BLE 设备。 */
public class BleDevice {
    public final String address;
    public final String name;
    public final int rssi;
    public final boolean advertisesAuroraService;
    public final boolean rgbProduct;

    public BleDevice(String address, String name, int rssi, boolean advertisesAuroraService) {
        this(address, name, rssi, advertisesAuroraService, false);
    }

    public BleDevice(String address, String name, int rssi,
                     boolean advertisesAuroraService, boolean rgbProduct) {
        this.address = address;
        this.name = name;
        this.rssi = rssi;
        this.advertisesAuroraService = advertisesAuroraService;
        this.rgbProduct = rgbProduct;
    }

    public String displayName() {
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return "未命名设备";
    }

    public boolean looksLikeAurora() {
        return rgbProduct
                || advertisesAuroraService
                || displayName().startsWith(AuroraProtocol.DEVICE_PREFIX);
    }

    public boolean isRgbProduct() {
        return rgbProduct;
    }

    public String signalText() {
        if (rssi >= -55) {
            return "信号强";
        }
        if (rssi >= -72) {
            return "信号良好";
        }
        return "信号较弱";
    }
}
