package com.star4droid.star2d.prefab;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        LibgdxEditor before = null;
        View view = null;
        try {
            if (editor) {
                LibgdxEditor ed = Bridge.editor();
                before = ed;
                if (!ed.isLoading()) ed.getProject().save(ed);
                snap = Bridge.app().openSceneFingerprints();
                if (mutating) view = View.of(ed);
            }
            out.value = work.call();
        } catch (Throwable t) {
            out.error = t;
        }
        if (editor && mutating) {
            try {
                // Only marker keys changed in the open scene (creating a prefab, apply, unpack): patch the live items,
                // nothing reloads, camera and selection stay. Anything else reloads the scene and puts camera and selection back.
                if (before != null && !before.isLoading() && patchInPlace(before, snap)) {
                    out.report = Bridge.app().applyExternalChanges(snap);
                } else {
                    out.report = Bridge.app().applyExternalChanges(snap);
                    if (view != null) view.restoreLater(before);
                }
            } catch (Throwable t) {
                if (out.error == null) out.error = t;
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- keeping the view

    /** Camera and selection of the open scene, to put back after a reload. */
    private static final class View {
        String scene, selected;
        float x, y, zoom;

        static View of(LibgdxEditor ed) {
            View v = new View();
            try {
                v.scene = ed.getScene();
                Actor sel = ed.getSelectedActor();
                v.selected = sel == null ? null : sel.getName();
                OrthographicCamera c = (OrthographicCamera) ed.getCamera();
                v.x = c.position.x;
                v.y = c.position.y;
                v.zoom = c.zoom;
            } catch (Throwable ignored) {
                return null;
            }
            return v;
        }

        /** After the reload the open editor is a new one: when it shows the same scene, camera and selection come back once it loaded. */
        void restoreLater(LibgdxEditor old) {
            try {
                LibgdxEditor fresh = Bridge.editor();
                if (fresh == null || fresh == old || !scene.equals(fresh.getScene())) return;
                fresh.whenLoaded(() -> {
                    try {
                        OrthographicCamera c = (OrthographicCamera) fresh.getCamera();
                        c.position.x = x;
                        c.position.y = y;
                        c.zoom = zoom;
                        c.update();
                        if (selected != null) {
                            Actor a = Bridge.actor(selected);
                            if (a != null) fresh.selectActor(a);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- in-place patch

    /** The only keys the prefab operations change without touching how an element looks or behaves in the editor. */
    private static final Set<String> PATCHABLE = new HashSet<>(Arrays.asList("prefab", "pid", "pbase", "Script"));

    /**
     * The scene file was changed by prefab work. When it holds exactly the live elements and differs only in
     * {@link #PATCHABLE} keys, the live property sets take those values, the scene is saved, and its fingerprint is
     * brought up to date (so the reload step sees the open scene as unchanged). Returns false when anything else differs.
     */
    private static boolean patchInPlace(LibgdxEditor ed, HashMap<String, String> snap) {
        try {
            String scene = ed.getScene();
            File f = new File(ed.getProject().getScenesPath() + scene);
            String text = f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
            if (text.trim().isEmpty()) return false;
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonArray()) return false;
            HashMap<String, JsonObject> inFile = new HashMap<>();
            for (JsonElement e : parsed.getAsJsonArray()) {
                if (!e.isJsonObject()) return false;
                String n = PrefabFiles.str(e.getAsJsonObject(), "name");
                if (n == null || inFile.put(n, e.getAsJsonObject()) != null) return false;
            }
            HashMap<String, PropertySet<String, Object>> live = new HashMap<>();
            for (Actor a : ed.getActors()) {
                if (!(a instanceof EditorItem)) continue;
                PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
                if (ps == null || !ps.containsKey("name")) continue;
                if (live.put(ps.getString("name"), ps) != null) return false;
            }
            if (live.size() != inFile.size() || !live.keySet().equals(inFile.keySet())) return false;
            // first look, then touch: nothing changes unless everything is patchable
            ArrayList<Object[]> changes = new ArrayList<>();
            for (Map.Entry<String, PropertySet<String, Object>> e : live.entrySet()) {
                PropertySet<String, Object> ps = e.getValue();
                JsonObject fo = inFile.get(e.getKey());
                HashSet<String> keys = new HashSet<>(fo.keySet());
                keys.addAll(ps.keySet());
                for (String k : keys) {
                    String fv = PrefabFiles.str(fo, k);
                    Object lo = ps.get(k);
                    String lv = lo == null ? null : String.valueOf(lo);
                    if (PrefabFiles.eq(fv, lv)) continue;
                    if (!PATCHABLE.contains(k)) return false;
                    changes.add(new Object[]{ps, k, fv});
                }
            }
            for (Object[] c : changes) {
                @SuppressWarnings("unchecked") PropertySet<String, Object> ps = (PropertySet<String, Object>) c[0];
                if (c[2] == null || ((String) c[2]).isEmpty()) ps.put((String) c[1], "");
                else ps.put((String) c[1], c[2]);
            }
            if (!changes.isEmpty()) {
                ed.getProject().save(ed);
                if (snap != null) snap.put(scene, Bridge.app().sceneFingerprint(scene));
            } else if (snap != null) {
                // nothing differs in the open scene (e.g. only other scenes or code changed): it is current as it is
                snap.put(scene, Bridge.app().sceneFingerprint(scene));
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
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
