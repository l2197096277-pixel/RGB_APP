package com.bluetrum.fota.log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface ILogger {
    void log(int logLevel, @Nullable String tag, @NonNull String message, @Nullable Throwable t);
}
