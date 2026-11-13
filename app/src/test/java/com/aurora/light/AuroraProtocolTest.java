package com.aurora.light;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 中科蓝讯 RGB 协议测试（纯 JVM）。 */
public class AuroraProtocolTest {

    @Test
    public void powerFrames_matchProtocol() {
        assertEquals("11 01 01 01 14", AuroraProtocol.bytesToHex(AuroraProtocol.power(true)));
        assertEquals("11 01 01 00 13", AuroraProtocol.bytesToHex(AuroraProtocol.power(false)));
    }

    @Test
    public void brightnessAndColorFrames_matchProtocol() {
        assertEquals("11 10 01 50 72",
                AuroraProtocol.bytesToHex(AuroraProtocol.brightness(80)));
        assertEquals("11 08 01 01 1B",
                AuroraProtocol.bytesToHex(AuroraProtocol.color(1)));
        assertEquals("11 08 01 09 23",
                AuroraProtocol.bytesToHex(AuroraProtocol.color(9)));
    }

    @Test
    public void modeFrames_canBeOpenedAndClosed() {
        assertEquals("11 02 01 01 15",
                AuroraProtocol.bytesToHex(AuroraProtocol.mode(AuroraProtocol.MODE_FLOW)));
        assertEquals("11 03 01 00 15",
                AuroraProtocol.bytesToHex(AuroraProtocol.mode(AuroraProtocol.MODE_BREATHE, false)));
        assertEquals("11 05 01 01 18",
                AuroraProtocol.bytesToHex(AuroraProtocol.mode(AuroraProtocol.MODE_MUSIC)));
        assertEquals("11 04 01 01 17",
                AuroraProtocol.bytesToHex(AuroraProtocol.mode(AuroraProtocol.MODE_CANDLE)));
    }

    @Test
    public void effectOffAndAllOff_matchProtocol() {
        assertEquals("11 06 01 01 19",
                AuroraProtocol.bytesToHex(AuroraProtocol.effectOff(true)));
        assertEquals("11 11 01 00 23",
                AuroraProtocol.bytesToHex(AuroraProtocol.allOff()));
        assertEquals("11 FF 01 01 12",
                AuroraProtocol.bytesToHex(AuroraProtocol.selfTest(true)));
    }

    @Test
    public void speedAndQuery_areExtensions() {
        assertEquals("11 0A 01 3C 58",
                AuroraProtocol.bytesToHex(AuroraProtocol.speed(60)));
        assertEquals("11 0B 00 1C",
                AuroraProtocol.bytesToHex(AuroraProtocol.query()));
    }

    @Test
    public void ledCountCommands_matchProtocol() {
        assertEquals("11 07 01 01 1A",
                AuroraProtocol.bytesToHex(AuroraProtocol.ledCountStep(true)));
        assertEquals("11 07 01 00 19",
                AuroraProtocol.bytesToHex(AuroraProtocol.ledCountStep(false)));
        assertEquals("11 12 02 03 01 29",
                AuroraProtocol.bytesToHex(AuroraProtocol.customLed(3, true)));
        assertEquals("11 12 02 03 00 28",
                AuroraProtocol.bytesToHex(AuroraProtocol.customLed(3, false)));
        assertEquals("11 12 02 00 00 25",
                AuroraProtocol.bytesToHex(AuroraProtocol.customExit()));
        assertEquals("11 08 01 00 1A",
                AuroraProtocol.bytesToHex(AuroraProtocol.colorOff()));
    }

    @Test
    public void checksum_isSumOfPreviousBytes() {
        byte[] frame = AuroraProtocol.hexToBytes("11 01 01 01 14");
        assertEquals(0x14, AuroraProtocol.checksum(frame, frame.length - 1));
    }

    @Test
    public void colorIndex_mapsRgbToProtocolColor() {
        assertEquals(1, AuroraProtocol.colorIndex(0xFFFF0000));
        assertEquals(5, AuroraProtocol.colorIndex(0xFF00FFFF));
        assertEquals(8, AuroraProtocol.colorIndex(0xFFFFFFFF));
        assertEquals(9, AuroraProtocol.colorIndex(0xFFFF00FF));
    }

    @Test
    public void syncFrame_containsPowerModeColorBrightnessSpeedAndCount() {
        AuroraState state = new AuroraState();
        state.power = true;
        state.mode = AuroraProtocol.MODE_FLOW;
        state.color = 0xFF00FFFF;
        state.colorOn = true;
        state.brightness = 72;
        state.speed = 60;
        state.ledCount = 60;
        assertEquals("11 0F 06 01 01 05 48 3C 3C ED",
                AuroraProtocol.bytesToHex(AuroraProtocol.sync(state)));
    }
}
