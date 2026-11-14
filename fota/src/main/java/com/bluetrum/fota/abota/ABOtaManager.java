package com.bluetrum.fota.abota;

import androidx.annotation.NonNull;

import com.bluetrum.fota.manager.OtaManager;

public class ABOtaManager extends OtaManager {

    public interface EventListener extends OtaManager.EventListener {
        void sendOtaData(byte[] data);
    }

    private final EventListener eventListener;

    /**
     * 构造器
     *
     * @param eventListener 事件监听器{@link EventListener}
     */
    public ABOtaManager(@NonNull EventListener eventListener) {
        super(eventListener);
        this.eventListener = eventListener;
    }

    @Override
    protected void btSendData(byte[] data) {
        // 如果来了暂停，就先不要发了
        if (isUpdatePause) return;

        eventListener.sendOtaData(data);
    }

    public void nextRun() {
        runDataSend();
    }
}
