package com.aurora.light;

import android.app.Application;
import android.os.Process;
import android.util.Log;

/** 兜底记录启动/运行崩溃，避免用户只看到闪退而没有任何信息。 */
public class AuroraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("AuroraCrash", "uncaught", throwable);
            CrashLog.save(this, throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }
}
