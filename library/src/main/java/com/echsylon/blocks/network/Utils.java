package com.echsylon.blocks.network;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

class Utils {
    private static final String TAG = "NETWORK";

    static void closeSilently(Closeable closeable) {
        try {
            closeable.close();
        } catch (NullPointerException | IOException e) {
            // Consume the exception silently.
        }
    }

    static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }

    static boolean isEmpty(List<?> map) {
        return map == null || map.isEmpty();
    }

    static boolean notEmpty(String string) {
        return !isEmpty(string);
    }

    static boolean notEmpty(List<?> map) {
        return !isEmpty(map);
    }

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void info(String pattern, Object... args) {
        Log.i(TAG, String.format(pattern, args));
    }

    static void info(Throwable error) {
        Log.i(TAG, null, error);
    }

    static void info(Throwable error, String message) {
        Log.i(TAG, message, error);
    }

    static void info(Throwable error, String pattern, Object... args) {
        Log.i(TAG, String.format(pattern, args), error);
    }
}
