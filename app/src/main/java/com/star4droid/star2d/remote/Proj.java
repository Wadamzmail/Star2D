package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;

import java.io.File;
import java.util.ArrayList;

/** Project-level helpers shared by the tools. Most need the GL thread. */
final class Proj {
    private Proj() {}

    /** The open project or a clear error for the AI. GL thread. */
    static Project project() {
        LibgdxEditor ed = Bridge.editor();
        if (ed == null) throw new Gl.ToolError("No project is open in the editor. Ask the user to open (or create) the project they want to work on, then try again.");
        return ed.getProject();
    }

    static LibgdxEditor editor() {
        LibgdxEditor ed = Bridge.editor();
        if (ed == null) throw new Gl.ToolError("No project is open in the editor.");
        return ed;
    }

    /** A path inside the project (never outside). Relative, with or without a leading slash. */
    static File safe(Project p, String rel) throws Exception {
        if (rel == null || rel.trim().isEmpty()) throw new Gl.ToolError("path is empty");
        File root = new File(p.getPath()).getCanonicalFile();
        File f = new File(root, rel.startsWith("/") ? rel.substring(1) : rel).getCanonicalFile();
        if (!f.getPath().equals(root.getPath()) && !f.getPath().startsWith(root.getPath() + File.separator))
            throw new Gl.ToolError("Path outside the project: " + rel);
        return f;
    }

    static String rel(Project p, File f) {
        // Compare canonical paths: on many phones the project path goes through a symlink (/sdcard, /storage/self...),
        // and a plain absolute-path comparison would never match.
        String root = canon(new File(p.getPath()));
        String a = canon(f);
        if (a.equals(root)) return "";
        return a.startsWith(root + "/") ? a.substring(root.length() + 1) : a;
    }

    private static String canon(File f) {
        try {
            return f.getCanonicalPath();
        } catch (Exception e) {
            return f.getAbsolutePath();
        }
    }

    /** All scene names (files of scenes/). */
    static ArrayList<String> scenes(Project p) {
        ArrayList<String> list = new ArrayList<>();
        FileHandle dir = Gdx.files.absolute(p.getScenesPath());
        if (dir.exists()) for (FileHandle f : dir.list()) if (!f.isDirectory() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(f.name())) list.add(f.name());
        java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    static String sceneOrDefault(String scene) {
        return scene == null || scene.isEmpty() ? editor().getScene() : scene;
    }

    /** Make {@code scene} the open one (loads it if it is not open yet) and wait until it finished loading. GL thread work + waits. */
    static void openScene(String scene) throws Exception {
        final String target = scene;
        boolean needs = Gl.run(() -> {
            LibgdxEditor ed = editor();
            if (ed.getScene().equals(target)) return false;
            if (!scenes(ed.getProject()).contains(target)) throw new Gl.ToolError("No scene named '" + target + "'. Scenes: " + scenes(ed.getProject()));
            Bridge.app().openSceneInNewEditor(target);
            return true;
        });
        if (needs) waitLoaded(20_000);
    }

    /** Wait (worker thread) until the open scene editor finished loading its elements. */
    static void waitLoaded(long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            boolean loading = Gl.run(() -> Bridge.editor() != null && Bridge.editor().isLoading());
            if (!loading) return;
            Gl.sleep(60);
        }
        throw new Gl.ToolError("The scene is still loading (very big scene?). Try again in a moment.");
    }
}
