package com.star4droid.star2d.remote;

import android.os.Handler;
import android.os.Looper;

import com.badlogic.gdx.Gdx;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Run code on the GL thread (the editor and the game live there) or the UI thread, and wait for it. */
final class Gl {
    private Gl() {}

    /** A tool failed with a message meant for the AI. */
    static final class ToolError extends RuntimeException {
        ToolError(String m) {
            super(m);
        }
    }

    interface Work<T> {
        T run() throws Exception;
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());

    /** Blocks the calling (worker) thread until {@code w} ran on the GL thread. */
    static <T> T run(Work<T> w) throws Exception {
        return run(w, 30_000);
    }

    static <T> T run(Work<T> w, long timeoutMs) throws Exception {
        if (Gdx.app == null) throw new ToolError("The editor is not ready yet (no graphics). Ask the user to open the project in the editor.");
        final AtomicReference<Object> out = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try {
                out.set(w.run());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(timeoutMs, TimeUnit.MILLISECONDS))
            throw new ToolError("The editor did not answer in time (is the app on screen?).");
        if (err.get() != null) {
            Throwable t = err.get();
            if (t instanceof Exception) throw (Exception) t;
            throw new RuntimeException(t);
        }
        @SuppressWarnings("unchecked") T r = (T) out.get();
        return r;
    }

    /** Like {@link #run} for UI-thread work. */
    static <T> T ui(Callable<T> c) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return c.call();
        final AtomicReference<Object> out = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        UI.post(() -> {
            try {
                out.set(c.call());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) throw new ToolError("The app UI did not answer in time.");
        if (err.get() != null) {
            Throwable t = err.get();
            if (t instanceof Exception) throw (Exception) t;
            throw new RuntimeException(t);
        }
        @SuppressWarnings("unchecked") T r = (T) out.get();
        return r;
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
