package com.aurora.light;

import java.util.UUID;

/**
 * 中科蓝讯 RGB 协议（App 端扩展版）。
 *
 * 帧格式：
 *   11 | CMD | LEN | DATA... | CHECKSUM
 * CHECKSUM = 前面所有字节相加后取低 8 位（sum & 0xFF）。
 *
 * 协议表里只定义了 01/02/03/04/05/08/10；
 * 本 App 补充了 06 速度、07 查询、0F 同步，作为扩展命令。
 */
public final class AuroraProtocol {

    /** 中科蓝讯 16 位服务 UUID 0xBBB0 的标准基础 UUID。 */
    public static final UUID SERVICE_UUID =
            UUID.fromString("0000BBB0-0000-1000-8000-00805F9B34FB");
    /** 手机 -> 灯带：写入控制指令（0xBBB1）。 */
    public static final UUID CONTROL_UUID =
            UUID.fromString("0000BBB1-0000-1000-8000-00805F9B34FB");
    /** 灯带 -> 手机：Notify 状态回报（0xBBB2）。 */
    public static final UUID STATUS_UUID =
            UUID.fromString("0000BBB2-0000-1000-8000-00805F9B34FB");
    /** 只读设备信息（0xBBB3），当前 App 不强制使用。 */
    public static final UUID INFO_UUID =
            UUID.fromString("0000BBB3-0000-1000-8000-00805F9B34FB");
    /** 标准 Client Characteristic Configuration Descriptor。 */
    public static final UUID CCCD_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");

    public static final int HEADER = 0x11;
    public static final int MAX_DATA = 32;
    public static final String DEVICE_PREFIX = "AuroraLight";

    // 协议表定义
    public static final int CMD_POWER = 0x01;
    public static final int CMD_MODE_RUNNING = 0x02; // 跑马灯
    public static final int CMD_MODE_BREATHE = 0x03; // 呼吸
    public static final int CMD_MODE_FLAME = 0x04;   // 火焰
    public static final int CMD_MODE_RHYTHM = 0x05;  // 律动
    public static final int CMD_EFFECT_OFF = 0x06;   // 关闭光效模式
    public static final int CMD_LED_COUNT_STEP = 0x07; // 亮灯数量 +1 / -1
    public static final int CMD_COLOR = 0x08;        // 颜色，0 为关闭颜色
    public static final int CMD_BRIGHTNESS = 0x10;
    public static final int CMD_ALL_OFF = 0x11;      // 一键关闭
    public static final int CMD_CUSTOM_LED = 0x12;   // 自定义单颗灯 11 12 01 x y
    public static final int CMD_SELF_TEST = 0xFF;    // 自检

    // App 补充的扩展命令（避开协议表已用命令）
    public static final int CMD_SPEED = 0x0A;        // 速度 0-100
    public static final int CMD_QUERY = 0x0B;        // 查询
    public static final int CMD_SYNC = 0x0F;         // 全量同步
    public static final int LED_COUNT_MAX = 255;

    // App 内部模式值
    public static final int MODE_STATIC = 0;
    public static final int MODE_FLOW = 1;
    public static final int MODE_BREATHE = 2;
    public static final int MODE_MUSIC = 3;
    public static final int MODE_CANDLE = 4;

    public static final String[] MODE_NAMES = {
            "静态", "流光跑马", "呼吸", "律动", "火焰"
    };

    /**
     * 协议表里的 9 个固定颜色，顺序对应颜色码 1-9。
     * 红、橙、黄、绿、青、蓝、紫、白、品红。
     */
    public static final int[] COLOR_RGB = {
            0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00,
            0xFF00FF00, 0xFF00FFFF, 0xFF0000FF,
            0xFF8000FF, 0xFFFFFFFF, 0xFFFF00FF
    };
    public static final String[] COLOR_NAMES = {
            "红", "橙", "黄", "绿", "青", "蓝", "紫", "白", "品红"
    };

    private AuroraProtocol() {
    }

    public static byte[] power(boolean on) {
        return frame(CMD_POWER, new byte[]{(byte) (on ? 1 : 0)});
    }

    public static byte[] brightness(int percent) {
        return frame(CMD_BRIGHTNESS, new byte[]{(byte) clamp(percent, 0, 100)});
    }

    /** colorIndex: 0=关闭颜色，1-9 对应 COLOR_RGB。 */
    public static byte[] color(int colorIndex) {
        return frame(CMD_COLOR, new byte[]{(byte) clamp(colorIndex, 0, 9)});
    }

    public static byte[] colorOff() {
        return color(0);
    }

    /** 亮灯数量 +1 / -1（协议表 0x07，相对命令）。 */
    public static byte[] ledCountStep(boolean increase) {
        return frame(CMD_LED_COUNT_STEP, new byte[]{(byte) (increase ? 1 : 0)});
    }

    /**
     * 自定义单颗灯：11 12 02 x y CS。
     * LEN=02；x=灯序号 0-255，y=1 亮 / 0 灭。
     */
    public static byte[] customLed(int index, boolean on) {
        byte[] f = new byte[6];
        f[0] = (byte) HEADER;
        f[1] = (byte) CMD_CUSTOM_LED;
        f[2] = 0x02;
        f[3] = (byte) clamp(index, 0, LED_COUNT_MAX);
        f[4] = (byte) (on ? 1 : 0);
        f[5] = (byte) checksum(f, 5);
        return f;
    }

    /** 退出自定义灯光：11 12 02 00 00 25。 */
    public static byte[] customExit() {
        return customLed(0, false);
    }

    /** App 模式值 1-4 映射到协议表里的 02/03/05/04。 */
    public static byte[] mode(int appMode) {
        return mode(appMode, true);
    }

    /** enabled=false 时发送该模式的关闭帧（DATA=00）。 */
    public static byte[] mode(int appMode, boolean enabled) {
        int command;
        switch (appMode) {
            case MODE_BREATHE:
                command = CMD_MODE_BREATHE;
                break;
            case MODE_MUSIC:
                command = CMD_MODE_RHYTHM;
                break;
            case MODE_CANDLE:
                command = CMD_MODE_FLAME;
                break;
            case MODE_FLOW:
            default:
                command = CMD_MODE_RUNNING;
                break;
        }
        return frame(command, new byte[]{(byte) (enabled ? 1 : 0)});
    }

    /** 关闭当前光效模式（协议表中的 0x06）。 */
    public static byte[] effectOff(boolean off) {
        return frame(CMD_EFFECT_OFF, new byte[]{(byte) (off ? 1 : 0)});
    }

    /** 一键关闭（协议表中的 0x11）。 */
    public static byte[] allOff() {
        return frame(CMD_ALL_OFF, new byte[]{0});
    }

    /** 自检（协议表中的 0xFF）。 */
    public static byte[] selfTest(boolean on) {
        return frame(CMD_SELF_TEST, new byte[]{(byte) (on ? 1 : 0)});
    }

    /** 扩展命令：速度 0-100。 */
    public static byte[] speed(int percent) {
        return frame(CMD_SPEED, new byte[]{(byte) clamp(percent, 0, 100)});
    }

    /** 扩展命令：查询当前状态。 */
    public static byte[] query() {
        return frame(CMD_QUERY, new byte[0]);
    }

    /**
     * 扩展命令：全量同步。
     * DATA = [power, appMode, colorIndex(0=off), brightness, speed, ledCount]
     */
    public static byte[] sync(AuroraState state) {
        return frame(CMD_SYNC, new byte[]{
                (byte) (state.power ? 1 : 0),
                (byte) clamp(state.mode, 0, 4),
                (byte) (state.colorOn ? colorIndex(state.color) : 0),
                (byte) clamp(state.brightness, 0, 100),
                (byte) clamp(state.speed, 0, 100),
                (byte) clamp(state.ledCount, 0, LED_COUNT_MAX)
        });
    }

    /** 返回 1-9 的颜色码；不精确匹配时返回最接近的协议颜色。 */
    public static int colorIndex(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int best = 1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < COLOR_RGB.length; i++) {
            int c = COLOR_RGB[i];
            int dr = r - ((c >> 16) & 0xFF);
            int dg = g - ((c >> 8) & 0xFF);
            int db = b - (c & 0xFF);
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i + 1;
            }
        }
        return best;
    }

    /** 16 进制颜色显示。 */
    public static String colorHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    public static byte[] frame(int command, byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        if (data.length > MAX_DATA) {
            throw new IllegalArgumentException("data too large");
        }
        byte[] out = new byte[4 + data.length];
        out[0] = (byte) HEADER;
        out[1] = (byte) command;
        out[2] = (byte) data.length;
        System.arraycopy(data, 0, out, 3, data.length);
        out[out.length - 1] = (byte) checksum(out, out.length - 1);
        return out;
    }

    /** 校验和：所有参与校验字节相加，取低 8 位。 */
    public static int checksum(byte[] data, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum = (sum + (data[i] & 0xFF)) & 0xFF;
        }
        return sum;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static byte[] hexToBytes(String hex) {
        String clean = hex.replace(" ", "").replace(":", "").replace("\n", "");
        if ((clean.length() & 1) != 0) {
            throw new IllegalArgumentException("hex length must be even");
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String bytesToHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }
}
