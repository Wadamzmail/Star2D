package com.star4droid.star2d.prefab;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Ui;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs prefab file work around the open editor: the open scene is saved first (so the files are current),
 * then the work runs, then the editor reloads exactly what changed (the open scene in place, other
 * changed tabs are closed). The whole thing runs on the GL thread, the files are small.
 */
public final class PrefabEditorSync {
    private PrefabEditorSync() {}

    /** Result of a run: the value, a short note from the editor reload ("2 tab(s) closed"...), or the error. */
    public static final class Outcome<T> {
        public T value;
        public String report = "";
        public Throwable error;

        public boolean ok() { return error == null; }

        public String errorMessage() {
            return error == null ? "" : (error.getMessage() != null ? error.getMessage() : error.toString());
        }
    }

    public interface Callback<T> { void on(Outcome<T> outcome); }

    /** Already on the GL thread. Changing work: save, work, reload. */
    public static <T> Outcome<T> runNow(Callable<T> work) {
        return runNow(work, true);
    }

    /** Already on the GL thread. {@code mutating=false}: only saves the open scene (so reads see the live state) and does not reload. */
    public static <T> Outcome<T> runNow(Callable<T> work, boolean mutating) {
        Outcome<T> out = new Outcome<>();
        boolean editor = Bridge.app() != null && Bridge.editor() != null;
        HashMap<String, String> snap = null;
        try {
            if (editor) {
                LibgdxEditor ed = Bridge.editor();
                if (!ed.isLoading()) ed.getProject().save(ed);
                snap = Bridge.app().openSceneFingerprints();
            }
            out.value = work.call();
        } catch (Throwable t) {
            out.error = t;
        }
        if (editor && mutating) {
            try {
                out.report = Bridge.app().applyExternalChanges(snap);
            } catch (Throwable t) {
                if (out.error == null) out.error = t;
            }
        }
        return out;
    }

    /** From a worker thread (not the GL or UI thread): blocks until the work and the reload are done. */
    public static <T> Outcome<T> runBlocking(Callable<T> work) {
        return runBlocking(work, true);
    }

    public static <T> Outcome<T> runBlocking(Callable<T> work, boolean mutating) {
        if (Gdx.app == null) {
            Outcome<T> o = new Outcome<>();
            try {
                o.value = work.call();
            } catch (Throwable t) {
                o.error = t;
            }
            return o;
        }
        final Outcome<T>[] box = new Outcome[1];
        final CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try {
                box[0] = runNow(work, mutating);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(30, TimeUnit.SECONDS)) {
                Outcome<T> o = new Outcome<>();
                o.error = new IllegalStateException("The editor did not answer in time");
                return o;
            }
        } catch (InterruptedException e) {
            Outcome<T> o = new Outcome<>();
            o.error = e;
            return o;
        }
        return box[0];
    }

    /** From any thread (usually the UI thread): the work runs on the GL thread, the callback on the UI thread. */
    public static <T> void runAsync(Callable<T> work, Callback<T> cb) {
        runAsync(work, true, cb);
    }

    public static <T> void runAsync(Callable<T> work, boolean mutating, Callback<T> cb) {
        if (Gdx.app == null) {
            Outcome<T> o = new Outcome<>();
            o.error = new IllegalStateException("The editor is not ready yet");
            if (cb != null) cb.on(o);
            return;
        }
        Gdx.app.postRunnable(() -> {
            Outcome<T> o = runNow(work, mutating);
            if (cb != null) Ui.ui(() -> cb.on(o));
        });
    }
}
