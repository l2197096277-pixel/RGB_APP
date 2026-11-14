package com.aurora.light;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

/** 独立的 FOTA BLE 连接：FF12 / FF14 Notify / FF15 Write No Response。 */
@SuppressLint("MissingPermission")
public class FotaBleClient {

    public interface Listener {
        void onReady();

        void onData(byte[] data);

        void onSendComplete();

        void onDisconnected(String reason);

        void onError(String message);
    }

    private static final UUID SERVICE = UUID.fromString("0000ff12-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY = UUID.fromString("0000ff14-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE = UUID.fromString("0000ff15-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic notifyChar;
    private BluetoothGattCharacteristic writeChar;
    private boolean ready;
    private int mtu = 23;

    public FotaBleClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isReady() {
        return ready;
    }

    public int maxPacketSize() {
        return Math.max(20, mtu - 3);
    }

    public void connect(String address) {
        BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            listener.onError("蓝牙不可用");
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(address);
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
            if (gatt == null) {
                listener.onError("无法建立 OTA 连接");
            }
        } catch (Exception e) {
            listener.onError("OTA 连接失败：" + e.getMessage());
        }
    }

    public void close() {
        ready = false;
        notifyChar = null;
        writeChar = null;
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (Exception ignored) {
            }
            try {
                gatt.close();
            } catch (Exception ignored) {
            }
            gatt = null;
        }
    }

    public void send(byte[] data) {
        if (!ready || gatt == null || writeChar == null || data == null) {
            return;
        }
        try {
            writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            writeChar.setValue(data);
            gatt.writeCharacteristic(writeChar);
        } catch (Exception e) {
            listener.onError("OTA 发送失败：" + e.getMessage());
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false;
                listener.onDisconnected(status == BluetoothGatt.GATT_SUCCESS
                        ? "设备已断开" : "OTA 连接中断（" + status + "）");
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onError("无法读取 OTA 服务");
                return;
            }
            BluetoothGattService service = g.getService(SERVICE);
            if (service == null) {
                listener.onError("设备没有 FF12 OTA 服务，固件可能不支持空中升级");
                return;
            }
            notifyChar = service.getCharacteristic(NOTIFY);
            writeChar = service.getCharacteristic(WRITE);
            if (notifyChar == null || writeChar == null) {
                listener.onError("设备缺少 FF14/FF15 OTA 特征值");
                return;
            }
            try {
                g.setCharacteristicNotification(notifyChar, true);
                BluetoothGattDescriptor descriptor = notifyChar.getDescriptor(CCCD);
                if (descriptor == null) {
                    listener.onError("FF14 缺少 CCCD，无法接收 OTA 状态");
                    return;
                }
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                g.writeDescriptor(descriptor);
            } catch (Exception e) {
                listener.onError("订阅 OTA 状态失败：" + e.getMessage());
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    g.requestMtu(517);
                } catch (Exception e) {
                    ready = true;
                    listener.onReady();
                }
            } else {
                listener.onError("订阅 OTA 状态失败");
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int newMtu, int status) {
            mtu = Math.max(23, newMtu);
            ready = true;
            listener.onReady();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            if (ch.getValue() != null) {
                listener.onData(ch.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                listener.onSendComplete();
            } else {
                listener.onError("OTA 数据写入失败（" + status + "）");
            }
        }
    };
}
