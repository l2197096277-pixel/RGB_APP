package com.bluetrum.fota.log;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Logger for module. logLevel use the same with {@link android.util.Log}.
 */
public class Logger {

    private static ILogger logger;
    private static int logLevel = Log.INFO;

    public static void setLogger(ILogger logger) {
        Logger.logger = logger;
    }

    public static void setLogLevel(int logLevel) {
        Logger.logLevel = logLevel;
    }

    public static void log(int logLevel, @Nullable String tag, @NonNull String message, @Nullable Throwable t) {
        if (logger != null && logLevel >= Logger.logLevel) {
            logger.log(logLevel, tag, message, t);
        }
    }

    public static void v(@Nullable String tag, @NonNull String msg) {
        log(Log.VERBOSE, tag, msg, null);
    }

    public static void v(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.VERBOSE, tag, msg, tr);
    }

    public static void d(@Nullable String tag, @NonNull String msg) {
        log(Log.DEBUG, tag, msg, null);
    }

    public static void d(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.DEBUG, tag, msg, tr);
    }

    public static void i(@Nullable String tag, @NonNull String msg) {
        log(Log.INFO, tag, msg, null);
    }

    public static void i(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.INFO, tag, msg, tr);
    }

    public static void w(@Nullable String tag, @NonNull String msg) {
        log(Log.WARN, tag, msg, null);
    }

    public static void w(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.WARN, tag, msg, tr);
    }

    public static void w(@Nullable String tag, @NonNull Throwable tr) {
        log(Log.WARN, tag, "", tr);
    }

    public static void e(@Nullable String tag, @NonNull String msg) {
        log(Log.ERROR, tag, msg, null);
    }

    public static void e(@Nullable String tag, @Nullable Throwable tr) {
        log(Log.ERROR, tag, "", tr);
    }

    public static void e(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.ERROR, tag, msg, tr);
    }

    public static void wtf(@Nullable String tag, @NonNull String msg) {
        log(Log.ASSERT, tag, msg, null);
    }

    public static void wtf(@Nullable String tag, @NonNull Throwable tr) {
        log(Log.ASSERT, tag, "", tr);
    }

    public static void wtf(@Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        log(Log.ASSERT, tag, msg, tr);
    }
}
