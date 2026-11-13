package com.aurora.light;

/** App 与灯带共享的一份灯光状态。 */
public class AuroraState {

    public boolean power = false;
    public int brightness = 72;
    public int mode = AuroraProtocol.MODE_FLOW;
    public int speed = 60;
    public int color = 0xFF00FFFF;
    public int ledCount = 1;
    public boolean colorOn = true;

    public AuroraState copy() {
        AuroraState state = new AuroraState();
        state.power = power;
        state.brightness = brightness;
        state.mode = mode;
        state.speed = speed;
        state.color = color;
        state.ledCount = ledCount;
        state.colorOn = colorOn;
        return state;
    }
}
