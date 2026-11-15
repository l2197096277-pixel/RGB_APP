package com.aurora.light;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * BLE 主机（Central）实现：扫描、连接、发现服务、订阅 Notify、串行写指令。
 * 所有对外回调都切回主线程，界面层不必处理 Binder 线程。
 */
public class AuroraBleManager {

    private static final String TAG = "AuroraBle";
    private static final long SCAN_TIMEOUT_MS = 8_000L;
    private static final long WRITE_TIMEOUT_MS = 2_000L;

    public interface Listener {
        void onScanResult(BleDevice device);

        void onScanFinished();

        void onScanError(String message);

        void onBleConnected(String name);

        void onBleReady();

        void onBleDisconnected(String reason);

        void onBleError(String message);

        /** 一帧真正写入 Control 特征值后回调，用于开发者模式显示。 */
        void onFrameSent(String hex, int command);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> pendingWrites = new ArrayDeque<>();
    private final Queue<byte[]> orderedBursts = new ArrayDeque<>();
    private boolean orderedSending;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic controlCharacteristic;
    private BluetoothGattCharacteristic statusCharacteristic;
    private boolean controlNoResponse;

    private boolean scanning;
    private boolean connecting;
    private volatile boolean ready;
    private boolean everReady;
    private volatile boolean writing;
    private int writeRetries;
    private boolean manualDisconnect;

    private final Runnable productReadTimeout = () -> fail(
            "\u8bbe\u5907\u578b\u53f7\u6821\u9a8c\u8d85\u65f6");

    private String currentName = "幻彩灯";
    private String currentAddress = "";

    private final Runnable scanTimeout = new Runnable() {
        @Override
        public void run() {
            finishScan(true);
        }
    };

    private final Runnable writeTimeout = new Runnable() {
        @Override
        public void run() {
            if (writing) {
                writing = false;
                pollWrite();
                postError("指令发送超时，请重试");
                drainWrites();
            }
        }
    };

    public AuroraBleManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        ensureAdapter();
    }

    private BluetoothAdapter ensureAdapter() {
        if (adapter != null) {
            return adapter;
        }
        BluetoothManager manager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) {
            try {
                adapter = manager.getAdapter();
            } catch (SecurityException e) {
                // 部分 Android 12+ / 国产 ROM 在未授予“附近的设备”时会在这里抛异常，
                // 不能让它阻断 App 启动；授权后这里会再次成功取到 adapter。
                Log.w(TAG, "BluetoothAdapter unavailable before permission", e);
                adapter = null;
            }
        }
        return adapter;
    }

    public boolean isBleSupported() {
        // 是否支持 BLE 是硬件能力，不应依赖当前是否已经拿到 adapter。
        return context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE) || ensureAdapter() != null;
    }

    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return check(Manifest.permission.BLUETOOTH_SCAN)
                    && check(Manifest.permission.BLUETOOTH_CONNECT);
        }
        return check(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public boolean isBluetoothEnabled() {
        try {
            BluetoothAdapter current = ensureAdapter();
            return current != null && current.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    public boolean isReady() {
        return ready;
    }

    public String currentName() {
        return currentName;
    }

    public String currentAddress() {
        return currentAddress;
    }

    private boolean check(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public boolean startScan() {
        BluetoothAdapter current = ensureAdapter();
        if (!hasPermission() || current == null || !current.isEnabled()) {
            return false;
        }
        stopScan(false);
        try {
            scanner = current.getBluetoothLeScanner();
            if (scanner == null) {
                return false;
            }
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .setReportDelay(0L)
                    .build();
            scanning = true;
            scanner.startScan(null, settings, scanCallback);
            handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
            return true;
        } catch (SecurityException e) {
            scanning = false;
            postError("蓝牙权限已失效，请在系统设置中重新授权");
            return false;
        } catch (RuntimeException e) {
            scanning = false;
            Log.w(TAG, "startScan failed", e);
            postError("无法启动蓝牙扫描，请重试");
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stopScan(boolean notifyFinished) {
        handler.removeCallbacks(scanTimeout);
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "stopScan", e);
            }
        }
        scanning = false;
        if (notifyFinished) {
            post(() -> listener.onScanFinished());
        }
    }

    @SuppressLint("MissingPermission")
    private void finishScan(boolean notifyFinished) {
        handler.removeCallbacks(scanTimeout);
        if (scanner != null && scanning) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException | IllegalStateException e) {
                Log.w(TAG, "finishScan", e);
            }
        }
        scanning = false;
        if (notifyFinished) {
            post(() -> listener.onScanFinished());
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(BleDevice device) {
        BluetoothAdapter current = ensureAdapter();
        if (device == null || current == null) {
            postError("未找到设备");
            return;
        }
        finishScan(false);
        closeGatt();
        manualDisconnect = false;
        everReady = false;
        ready = false;
        connecting = true;
        writing = false;
        clearWrites();
        currentName = device.displayName();
        currentAddress = device.address;
        try {
            BluetoothDevice remote = current.getRemoteDevice(device.address);
            gatt = remote.connectGatt(context, false, gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                connecting = false;
                postError("无法建立连接，请重试");
            }
        } catch (SecurityException e) {
            connecting = false;
            postError("缺少蓝牙连接权限");
        } catch (IllegalArgumentException e) {
            connecting = false;
            postError("设备地址无效");
        }
    }

    /** 用上次保存的地址直接连接，不需要重新扫描。 */
    public void connectAddress(String address, String name) {
        if (address == null || address.trim().isEmpty()) {
            postError("上次设备地址为空");
            return;
        }
        connect(new BleDevice(address, name, 0, false));
    }

    public boolean send(byte[] frame) {
        if (!ready || gatt == null || controlCharacteristic == null) {
            return false;
        }
        byte[] copy = frame.clone();
        synchronized (pendingWrites) {
            pendingWrites.offer(copy);
        }
        drainWrites();
        return true;
    }

    /** 音乐律动单次发送：不重复 3 次，由调用方做节流。 */
    public boolean sendReactive(byte[] frame) {
        return send(frame);
    }

    /**
     * 同一条命令连续发送 times 次，间隔 intervalMs。
     * 协议要求“间隔 100ms 发送 5 次”，由 MainActivity 调用。
     */
    public void sendBurst(byte[] frame, int times, long intervalMs) {
        if (frame == null || times <= 0) {
            return;
        }
        for (int i = 0; i < times; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (!ready) {
                    if (index == 0) {
                        postError("尚未连接，指令未发送");
                    }
                    return;
                }
                send(frame);
            }, Math.max(0L, intervalMs) * i);
        }
    }

    /** 延迟一段时间后再开始一组重复发送，用于“旧模式关闭 → 新模式开启”的顺序。 */
    public void sendBurstDelayed(byte[] frame, int times, long intervalMs, long delayMs) {
        handler.postDelayed(() -> sendBurst(frame, times, intervalMs), Math.max(0L, delayMs));
    }

    /**
     * 自定义灯光的严格串行队列：一组 3 次发完后才发下一组，快速点击也不会跳过。
     */
    public void sendOrderedBurst(byte[] frame) {
        if (frame == null) {
            return;
        }
        orderedBursts.add(frame.clone());
        pumpOrderedBurst();
    }

    private void pumpOrderedBurst() {
        if (orderedSending || orderedBursts.isEmpty()) {
            return;
        }
        if (!ready) {
            orderedBursts.clear();
            return;
        }
        final byte[] frame = orderedBursts.poll();
        orderedSending = true;
        for (int i = 0; i < 3; i++) {
            handler.postDelayed(() -> {
                if (ready) {
                    send(frame);
                }
            }, i * 100L);
        }
        handler.postDelayed(() -> {
            orderedSending = false;
            pumpOrderedBurst();
        }, 380L);
    }

    @SuppressLint("MissingPermission")
    private void drainWrites() {
        if (!ready || writing || gatt == null || controlCharacteristic == null) {
            return;
        }
        byte[] frame;
        synchronized (pendingWrites) {
            frame = pendingWrites.peek();
        }
        if (frame == null) {
            return;
        }
        writing = true;
        try {
            boolean noResponse = controlNoResponse
                    || (controlCharacteristic.getProperties()
                    & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
            controlCharacteristic.setWriteType(noResponse
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            controlCharacteristic.setValue(frame);
            boolean accepted = gatt.writeCharacteristic(controlCharacteristic);
            if (!accepted) {
                writing = false;
                if (writeRetries < 2) {
                    writeRetries++;
                    handler.postDelayed(this::drainWrites, 80L);
                    return;
                }
                writeRetries = 0;
                synchronized (pendingWrites) {
                    pendingWrites.poll();
                }
                postError("写入失败，请重试");
                handler.postDelayed(this::drainWrites, 80L);
                return;
            }
            writeRetries = 0;
            handler.removeCallbacks(writeTimeout);
            if (noResponse) {
                // No Response 写不一定有 onCharacteristicWrite 回调，主动推进队列。
                handler.postDelayed(this::completeWrite, 30L);
            } else {
                handler.postDelayed(writeTimeout, WRITE_TIMEOUT_MS);
            }
            listener.onFrameSent(AuroraProtocol.bytesToHex(frame), frame[1] & 0xFF);
        } catch (SecurityException e) {
            writing = false;
            synchronized (pendingWrites) {
                pendingWrites.poll();
            }
            postError("蓝牙权限已失效");
        }
    }

    private void completeWrite() {
        if (!writing) {
            return;
        }
        handler.removeCallbacks(writeTimeout);
        writing = false;
        writeRetries = 0;
        pollWrite();
        handler.postDelayed(this::drainWrites, 20L);
    }

    public void disconnect() {
        manualDisconnect = true;
        finishScan(false);
        ready = false;
        connecting = false;
        everReady = false;
        writing = false;
        writeRetries = 0;
        clearWrites();
        orderedBursts.clear();
        orderedSending = false;
        closeGatt();
    }

    public void close() {
        disconnect();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressLint("MissingPermission")
    private void closeGatt() {
        handler.removeCallbacks(writeTimeout);
        handler.removeCallbacks(productReadTimeout);
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (SecurityException ignored) {
            }
            try {
                gatt.close();
            } catch (SecurityException ignored) {
            }
            gatt = null;
        }
        controlCharacteristic = null;
        statusCharacteristic = null;
        controlNoResponse = false;
        ready = false;
    }

    @SuppressLint("MissingPermission")
    private void fail(String message) {
        manualDisconnect = true;
        ready = false;
        connecting = false;
        everReady = false;
        writing = false;
        writeRetries = 0;
        clearWrites();
        orderedBursts.clear();
        orderedSending = false;
        closeGatt();
        post(() -> listener.onBleError(message));
    }

    private void markReady() {
        if (gatt == null || ready) {
            return;
        }
        ready = true;
        connecting = false;
        everReady = true;
        post(() -> listener.onBleReady());
        post(this::drainWrites);
    }

    @SuppressLint("MissingPermission")
    private void subscribeStatus(BluetoothGatt g) {
        if (statusCharacteristic == null) {
            return;
        }
        // FF02 是正常控制的状态通知。App 当前不解析它，但订阅后设备才可能回报状态。
        try {
            g.setCharacteristicNotification(statusCharacteristic, true);
            BluetoothGattDescriptor descriptor =
                    statusCharacteristic.getDescriptor(AuroraProtocol.CCCD_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "subscribe FF02 status failed", e);
        }
    }

    private void post(Runnable runnable) {
        handler.post(runnable);
    }

    private void clearWrites() {
        synchronized (pendingWrites) {
            pendingWrites.clear();
        }
    }

    private void pollWrite() {
        synchronized (pendingWrites) {
            pendingWrites.poll();
        }
    }

    @SuppressLint("MissingPermission")
    private void requestMtu(BluetoothGatt gatt) {
        try {
            if (gatt != null) {
                gatt.requestMtu(64);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void postError(String message) {
        post(() -> listener.onBleError(message));
    }

    private static boolean containsFf01Marker(byte[] data) {
        if (data == null) {
            return false;
        }
        for (int i = 0; i + 1 < data.length; i++) {
            int a = data[i] & 0xFF;
            int b = data[i + 1] & 0xFF;
            if ((a == 0xFF && b == 0x01) || (a == 0x01 && b == 0xFF)) {
                return true;
            }
        }
        return false;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) {
                return;
            }
            BluetoothDevice device = result.getDevice();
            String name = null;
            boolean advertises = false;
            boolean rgbProduct = false;
            try {
                name = device.getName();
                ScanRecord record = result.getScanRecord();
                if (name == null && record != null) {
                    name = record.getDeviceName();
                }
                if (record != null) {
                    List<ParcelUuid> uuids = record.getServiceUuids();
                    if (uuids != null) {
                        for (ParcelUuid uuid : uuids) {
                            UUID serviceUuid = uuid.getUuid();
                            if (AuroraProtocol.SERVICE_UUID.equals(serviceUuid)
                                    || AuroraProtocol.LEGACY_SERVICE_UUID.equals(serviceUuid)) {
                                advertises = true;
                            }
                            if (AuroraProtocol.CONTROL_UUID.equals(serviceUuid)
                                    || AuroraProtocol.STATUS_UUID.equals(serviceUuid)
                                    || AuroraProtocol.INFO_UUID.equals(serviceUuid)) {
                                rgbProduct = true;
                            }
                        }
                    }
                    SparseArray<byte[]> manufacturer = record.getManufacturerSpecificData();
                    if (manufacturer != null) {
                        for (int i = 0; i < manufacturer.size(); i++) {
                            if (containsFf01Marker(manufacturer.valueAt(i))) {
                                rgbProduct = true;
                                break;
                            }
                        }
                    }
                    Map<ParcelUuid, byte[]> serviceData = record.getServiceData();
                    if (serviceData != null) {
                        for (ParcelUuid uuid : serviceData.keySet()) {
                            UUID dataUuid = uuid.getUuid();
                            if (AuroraProtocol.CONTROL_UUID.equals(dataUuid)
                                    || AuroraProtocol.STATUS_UUID.equals(dataUuid)
                                    || AuroraProtocol.INFO_UUID.equals(dataUuid)
                                    || AuroraProtocol.SERVICE_UUID.equals(dataUuid)) {
                                rgbProduct = true;
                                break;
                            }
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.w(TAG, "read scan result", e);
            }
            BleDevice bleDevice = new BleDevice(device.getAddress(), name,
                    result.getRssi(), advertises, rgbProduct);
            post(() -> listener.onScanResult(bleDevice));
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) {
                return;
            }
            for (ScanResult result : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "scan failed: " + errorCode);
            scanning = false;
            handler.removeCallbacks(scanTimeout);
            post(() -> {
                listener.onScanError("蓝牙扫描失败（错误码 " + errorCode + "）");
                listener.onScanFinished();
            });
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (g != gatt) {
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                post(() -> listener.onBleConnected(currentName));
                try {
                    g.discoverServices();
                } catch (SecurityException e) {
                    fail("缺少蓝牙连接权限");
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                boolean wasReady = ready;
                boolean manual = manualDisconnect;
                ready = false;
                connecting = false;
                writing = false;
                clearWrites();
                controlCharacteristic = null;
                statusCharacteristic = null;
                controlNoResponse = false;
                handler.removeCallbacks(writeTimeout);
                handler.removeCallbacks(productReadTimeout);
                if (wasReady) {
                    everReady = false;
                }
                if (gatt == g) {
                    try {
                        g.close();
                    } catch (SecurityException ignored) {
                    }
                    gatt = null;
                }
                if (manual) {
                    return;
                }
                if (wasReady) {
                    post(() -> listener.onBleDisconnected(
                            status == BluetoothGatt.GATT_SUCCESS ? null : "连接异常结束"));
                } else {
                    fail("连接失败（状态码 " + status + "），请靠近设备后重试");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (g != gatt) {
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("读取设备服务失败，请重试");
                return;
            }
            BluetoothGattService service = g.getService(AuroraProtocol.SERVICE_UUID);
            if (service == null) {
                fail("该设备没有 FF12 服务：请确认固件已启用 FF12 控制/OTA");
                return;
            }
            controlCharacteristic = service.getCharacteristic(AuroraProtocol.CONTROL_UUID);
            if (controlCharacteristic == null) {
                controlCharacteristic = service.getCharacteristic(AuroraProtocol.INFO_UUID);
            }
            if (controlCharacteristic == null) {
                fail("设备固件不完整：缺少 FF01/FF03 控制特征值");
                return;
            }
            controlNoResponse = (controlCharacteristic.getProperties()
                    & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0;
            statusCharacteristic = service.getCharacteristic(AuroraProtocol.STATUS_UUID);
            // 普通控制只写 FF01；FF02 的 CCCD 写会占用 GATT 操作，
            // 紧接同步指令时会导致 writeCharacteristic 返回 false（“写入失败”误报）。
            if (statusCharacteristic == null) {
                fail("\u8bbe\u5907\u7f3a\u5c11 FF02 \u4ea7\u54c1\u4fe1\u606f\u7279\u5f81\u503c");
                return;
            }
            try {
                boolean started = g.readCharacteristic(statusCharacteristic);
                if (!started) {
                    fail("\u8bbe\u5907\u578b\u53f7\u6821\u9a8c\u5931\u8d25");
                    return;
                }
                handler.postDelayed(productReadTimeout, 3000L);
            } catch (SecurityException e) {
                fail("\u7f3a\u5c11\u84dd\u7259\u8bfb\u53d6\u6743\u9650");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (g != gatt || characteristic == null) {
                return;
            }
            if (statusCharacteristic == null
                    || !statusCharacteristic.getUuid().equals(characteristic.getUuid())) {
                return;
            }
            handler.removeCallbacks(productReadTimeout);
            byte[] value = characteristic.getValue();
            if (status != BluetoothGatt.GATT_SUCCESS || value == null || value.length < 2) {
                fail("\u8bbe\u5907\u578b\u53f7\u6821\u9a8c\u5931\u8d25");
                return;
            }
            int productId = (value[0] & 0xFF) | ((value[1] & 0xFF) << 8);
            if (productId != AuroraProtocol.LS_PRODUCT_ID) {
                fail("\u8bbe\u5907\u578b\u53f7\u4e0d\u5339\u914d");
                return;
            }
            markReady();
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            Log.d(TAG, "MTU=" + mtu + " status=" + status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (g != gatt) {
                return;
            }
            if (characteristic == null
                    || controlCharacteristic == null
                    || !controlCharacteristic.getUuid().equals(characteristic.getUuid())) {
                return;
            }
            final int writeStatus = status;
            post(() -> {
                if (writeStatus != BluetoothGatt.GATT_SUCCESS) {
                    postError("指令写入失败（" + writeStatus + "）");
                }
                completeWrite();
            });
        }
    };
}
