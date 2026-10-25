package com.star4droid.star2d.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.ControlLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridge from the Android UI thread to the LibGDX editor (GL thread).
 * UI code never touches actors directly: it posts work with {@link #gl} and gets
 * plain-data snapshots back on the UI thread with {@link #query}.
 */
public final class Bridge {
    private Bridge() {}

    public interface Work<T> { T run() throws Exception; }
    public interface Result<T> { void on(T value); }

    public static TestApp app() { return TestApp.getCurrentApp(); }

    public static LibgdxEditor editor() {
        TestApp a = app();
        return a == null ? null : a.getEditor();
    }

    public static ControlLayer controls() {
        TestApp a = app();
        return a == null ? null : a.getControlLayer();
    }

    public static boolean ready() {
        return Gdx.app != null && app() != null && editor() != null && controls() != null;
    }

    /** Run on the GL thread; errors are reported as a toast instead of crashing. */
    public static void gl(Runnable r) {
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                report(t);
            }
        });
    }

    /** Compute on the GL thread, deliver the result on the UI thread. */
    public static <T> void query(Work<T> work, Result<T> result) {
        if (Gdx.app == null) return;
        Gdx.app.postRunnable(() -> {
            T v;
            try {
                v = work.run();
            } catch (Throwable t) {
                report(t);
                return;
            }
            final T value = v;
            Ui.ui(() -> result.on(value));
        });
    }

    private static void report(Throwable t) {
        try {
            Gdx.files.external("logs/native_ui_errors.txt").writeString(android.util.Log.getStackTraceString(t) + "\n----\n", true);
        } catch (Throwable ignored) {}
        if (com.star4droid.star2d.evo.star2dApp.getContext() != null)
            Ui.toast(com.star4droid.star2d.evo.star2dApp.getContext(), "Error: " + t);
    }

    // ---------------------------------------------------------------- snapshots (call on GL thread)

    /** Plain copy of a property set (all values as strings). */
    public static LinkedHashMap<String, String> snapshot(PropertySet<String, Object> ps) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        if (ps == null) return m;
        for (Map.Entry<String, Object> e : ps.entrySet())
            m.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
        return m;
    }

    /** One row of the elements list. */
    public static class ItemInfo {
        public String name, type, parent;
        public boolean visible, locked, selected, isLight;
        public int depth;
    }

    /** Elements in display order (roots then their children), on the GL thread. */
    public static ArrayList<ItemInfo> items() {
        ArrayList<ItemInfo> out = new ArrayList<>();
        LibgdxEditor ed = editor();
        if (ed == null) return out;
        Actor sel = ed.getSelectedActor();
        HashMap<String, PropertySet<String, Object>> byName = new HashMap<>();
        ArrayList<PropertySet<String, Object>> order = new ArrayList<>();
        HashMap<String, String> types = new HashMap<>();
        for (Actor a : ed.getActors()) {
            if (!(a instanceof EditorItem)) continue;
            PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
            if (ps == null || !ps.containsKey("name")) continue;
            byName.put(ps.getString("name"), ps);
            order.add(ps);
            String t;
            try { t = ((EditorItem) a).getTypeName(); } catch (Throwable e) { t = a.getClass().getSimpleName(); }
            types.put(ps.getString("name"), t);
        }
        // sort by z like the editor does
        java.util.Collections.sort(order, (x, y) -> Float.compare(num(x.getString("z")), num(y.getString("z"))));
        for (PropertySet<String, Object> ps : order) {
            if (!ps.getString("parent").equals("") && byName.containsKey(ps.getString("parent"))) continue;
            addTree(ps, 0, order, types, sel, out, new java.util.HashSet<>());
        }
        return out;
    }

    private static void addTree(PropertySet<String, Object> ps, int depth, ArrayList<PropertySet<String, Object>> all,
                                HashMap<String, String> types, Actor sel, ArrayList<ItemInfo> out, java.util.HashSet<String> seen) {
        String name = ps.getString("name");
        if (!seen.add(name)) return;
        ItemInfo i = new ItemInfo();
        i.name = name;
        i.type = types.get(name);
        i.parent = ps.getString("parent");
        i.visible = !"false".equals(ps.getString("Visible"));
        i.locked = "true".equals(ps.getString("lock"));
        i.selected = sel != null && name.equals(sel.getName());
        i.isLight = i.type != null && i.type.toLowerCase().contains("light");
        i.depth = depth;
        out.add(i);
        for (PropertySet<String, Object> c : all)
            if (name.equals(c.getString("parent"))) addTree(c, depth + 1, all, types, sel, out, seen);
    }

    public static float num(String s) {
        try { return Float.parseFloat(s); } catch (Exception e) { return 0; }
    }

    /** Find an editor item by name (GL thread). */
    public static Actor actor(String name) {
        LibgdxEditor ed = editor();
        if (ed == null) return null;
        for (Actor a : ed.getActors())
            if (a instanceof EditorItem && name.equals(a.getName())) return a;
        return null;
    }
}
