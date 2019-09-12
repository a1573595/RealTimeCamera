package com.a1573595.realtimecamera;

import android.util.Log;

public class Logger {
    private String tag;
    private boolean isDebug = BuildConfig.DEBUG;

    public Logger(Class<?> clazz) {
        tag = clazz.getSimpleName();
    }

    protected void d(String msg) {
        if(isDebug)
            Log.d(tag, msg);
    }

    protected void e(String msg) {
        if(isDebug)
            Log.e(tag, msg);
    }

    protected void i(String msg) {
        if(isDebug)
            Log.i(tag, msg);
    }

    protected void w(String msg) {
        if(isDebug)
            Log.w(tag, msg);
    }
}
