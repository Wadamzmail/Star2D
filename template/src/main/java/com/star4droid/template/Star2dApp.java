package com.star4droid.template;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** Application of an exported game: on a crash, opens DebugActivity with the stack trace (and keeps the default handler). */
public class Star2dApp extends Application {
    private static Thread.UncaughtExceptionHandler previous;

    @Override
    public void onCreate() {
        super.onCreate();
        if (previous == null) previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    Intent intent = new Intent(getApplicationContext(), DebugActivity.class);
                    intent.putExtra("error", Log.getStackTraceString(throwable));
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            getApplicationContext().startActivity(intent);
                        }
                    }, 1500);
                } catch (Throwable ignored) {
                }
                if (previous != null) previous.uncaughtException(thread, throwable);
            }
        });
    }
}
