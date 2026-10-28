package com.star4droid.star2d.prefab;

import java.util.concurrent.Callable;

/** Runs prefab file work around the open editor: saves the open scene first, reloads what changed afterwards. */
public final class PrefabEditorSync {
    private PrefabEditorSync() {}

    /** Result of a sync run. */
    public static final class Outcome<T> {
        public T value;
        public String report = "";
        public Throwable error;
    }

    public interface Callback<T> { void on(Outcome<T> outcome); }

    /** From a worker thread (not GL, not UI): blocks until done. */
    public static <T> Outcome<T> runBlocking(Callable<T> work) {
        throw new UnsupportedOperationException("skeleton");
    }

    /** From any thread: work + reload on the GL thread, the callback on the UI thread. */
    public static <T> void runAsync(Callable<T> work, Callback<T> cb) {
        throw new UnsupportedOperationException("skeleton");
    }

    /** Already on the GL thread. */
    public static <T> Outcome<T> runNow(Callable<T> work) {
        throw new UnsupportedOperationException("skeleton");
    }
}
