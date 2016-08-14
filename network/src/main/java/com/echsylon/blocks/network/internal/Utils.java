package com.echsylon.blocks.network.internal;

import java.io.Closeable;
import java.io.IOException;

class Utils {

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

    static boolean notEmpty(String string) {
        return !isEmpty(string);
    }

}
