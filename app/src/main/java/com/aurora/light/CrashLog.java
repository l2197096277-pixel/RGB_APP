package com.aurora.light;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/** 保存最近一次启动崩溃，供错误页显示。 */
public final class CrashLog {

    private static final String PREFS = "aurora_crash";
    private static final String KEY = "last_stack";
    private static final String TAG = "AuroraCrash";

    private CrashLog() {
    }

    public static void save(Context context, Throwable throwable) {
        try {
            String stack = Log.getStackTraceString(throwable);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY, stack)
                    .commit();
        } catch (Throwable ignored) {
        }
    }

    public static String read(Context context) {
        try {
            SharedPreferences prefs =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return prefs.getString(KEY, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void clear(Context context) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY)
                    .commit();
        } catch (Throwable ignored) {
        }
    }
}
