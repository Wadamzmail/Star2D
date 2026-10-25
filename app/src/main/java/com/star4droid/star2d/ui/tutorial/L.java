package com.star4droid.star2d.ui.tutorial;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Adapters.VisualScriptingDialog;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.blocks.BlockEditorActivity;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;

/** Conditions and "do it for me" actions used by the lessons. */
final class L {
    private L() {}

    // ================================================================ editor state

    static EditorShell shell() { return EditorShell.current; }

    static boolean panelIs(PanelHost.Panel p) {
        EditorShell s = shell();
        return s != null && p != null && s.panels.current() == p;
    }

    static boolean noPanel() {
        EditorShell s = shell();
        return s != null && s.panels.current() == null;
    }

    static Bridge.ElemState elem(String name) {
        for (Bridge.ElemState e : Tutorial.elements()) if (e.name.equals(name)) return e;
        return null;
    }

    static int count(String type) {
        int n = 0;
        for (Bridge.ElemState e : Tutorial.elements()) if (type.equals(e.type)) n++;
        return n;
    }

    static String selected() {
        for (Bridge.ElemState e : Tutorial.elements()) if (e.selected) return e.name;
        return null;
    }

    static String prop(String name, String key) {
        Bridge.ElemState e = elem(name);
        if (e == null || e.props == null) return null;
        return e.props.get(key);
    }

    static boolean propIs(String name, String key, String value) {
        String v = prop(name, key);
        if (v == null) return false;
        if (v.equals(value)) return true;
        try {
            return Math.abs(Float.parseFloat(v) - Float.parseFloat(value)) < 0.6f;
        } catch (Exception e) {
            return v.equalsIgnoreCase(value);
        }
    }

    static boolean playing() {
        EditorShell s = shell();
        return s != null && s.isPlaying();
    }

    static boolean varExists(String name) {
        File p = Tutorial.project();
        if (p == null) return false;
        File f = new File(p, "Events/scene1/public/variables.json");
        try {
            return f.exists() && BlockModel.read(f).contains("\"" + name + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================ editor actions (GL thread)

    static void select(String name) {
        Bridge.gl(() -> {
            Actor a = Bridge.actor(name);
            if (a != null) Bridge.editor().selectActor(a);
        });
    }

    static void addBody(int pos) {
        EditorShell s = shell();
        if (s != null) s.panels.hide();
        Bridge.gl(() -> Bridge.controls().actionAddBody(pos));
    }

    static void renameSelected(String name) {
        Bridge.gl(() -> {
            Actor a = Bridge.editor().getSelectedActor();
            if (a != null) ItemActions.rename(Bridge.app(), a, name);
            Ui.ui(L::refreshPanels);
        });
    }

    /** Set a property of an element like the Properties panel does. */
    static void setProp(String name, String key, String value) {
        Bridge.gl(() -> {
            Actor a = Bridge.actor(name);
            if (a == null) return;
            Bridge.editor().selectActor(a);
            switch (key) {
                case "image":
                    PropertyEditing.applyImage(Bridge.app(), key, value);
                    break;
                case "type":
                case "Shape":
                    PropertyEditing.applySpinner(Bridge.app(), key, value);
                    break;
                case "Fixed Rotation":
                case "isSensor":
                case "Bullet":
                case "Visible":
                    PropertyEditing.applyBoolean(Bridge.app(), key, value);
                    break;
                case "Text":
                    PropertyEditing.applyString(Bridge.app(), key, value);
                    break;
                default:
                    PropertyEditing.applyFloat(Bridge.app(), key, Float.parseFloat(value));
            }
            Ui.ui(L::refreshPanels);
        });
    }

    static void refreshPanels() {
        EditorShell s = shell();
        if (s == null) return;
        s.properties.onSelectionChanged();
        s.elements.refresh();
    }

    static void show(PanelHost.Panel p) {
        EditorShell s = shell();
        if (s != null && p != null) s.panels.show(p);
    }

    static void hidePanel() {
        EditorShell s = shell();
        if (s != null) s.panels.hide();
    }

    /** Open an element event in the block editor. */
    static void openEvent(String name, String event) {
        Bridge.gl(() -> {
            Actor a = Bridge.actor(name);
            if (a != null) Bridge.editor().selectActor(a);
            Ui.ui(() -> Ui.ui(() -> VisualScriptingDialog.showFor(event, true, false)));
        });
    }

    static void openSceneEvent(String event) {
        VisualScriptingDialog.showFor(event, false, false);
    }

    // ================================================================ block editor

    static BlockEditorActivity blocks() {
        BlockEditorActivity b = BlockEditorActivity.current;
        return b == null || b.isFinishing() ? null : b;
    }

    static boolean blocksClosed() {
        return blocks() == null;
    }

    static boolean blocksOpen(String element, String event) {
        BlockEditorActivity b = blocks();
        if (b == null || b.file() == null) return false;
        String p = b.file().getAbsolutePath();
        return element == null ? p.endsWith("/public/" + event + ".visual") : p.endsWith("/private/" + element + "/" + event + ".visual");
    }

    static BlockModel.Node node(String title) {
        BlockEditorActivity b = blocks();
        return b == null ? null : b.canvas().findNode(title);
    }

    /** True when a block is connected to the root stack (directly or nested). */
    static boolean inRoot(String title) {
        BlockEditorActivity b = blocks();
        if (b == null) return false;
        BlockModel.Node r = b.model().root();
        return r != null && reach(r, title);
    }

    private static boolean reach(BlockModel.Node n, String title) {
        while (n != null) {
            if (title.equals(n.title)) return true;
            if (reach(n.yes, title) || reach(n.no, title)) return true;
            n = n.next;
        }
        return false;
    }

    /** True when a block is inside a C-block opening. */
    static boolean inside(String parent, int slot, String title) {
        BlockModel.Node p = node(parent);
        if (p == null) return false;
        return reach(slot == 2 ? p.no : p.yes, title);
    }

    static BlockModel.Field field(String title, int index, int... path) {
        BlockModel.Node n = node(title);
        if (n == null || index >= n.fields.size()) return null;
        BlockModel.Field f = n.fields.get(index);
        for (int i : path) {
            if (f.block == null || i >= f.block.inner.size()) return null;
            f = f.block.inner.get(i);
        }
        return f;
    }

    static boolean fieldIs(String title, int index, String value, int... path) {
        BlockModel.Field f = field(title, index, path);
        if (f == null || f.block != null) return false;
        String v = f.value.trim();
        if (v.equals(value)) return true;
        try {
            return Float.parseFloat(v) == Float.parseFloat(value);
        } catch (Exception e) {
            return v.replace("\"", "").equalsIgnoreCase(value.replace("\"", ""));
        }
    }

    static boolean fieldHasBlock(String title, int index, String display, int... path) {
        BlockModel.Field f = field(title, index, path);
        return f != null && f.block != null && f.block.display.equals(display);
    }

    static boolean editing(String title, int index, int... path) {
        BlockEditorActivity b = blocks();
        BlockModel.Field f = field(title, index, path);
        return b != null && f != null && b.editingField() == f;
    }

    static void setField(String title, int index, String value, int... path) {
        BlockEditorActivity b = blocks();
        if (b != null) b.tutorialSetField(field(title, index, path), value);
    }

    static void setBlock(String title, int index, String display, int... path) {
        BlockEditorActivity b = blocks();
        if (b != null) b.tutorialSetParam(field(title, index, path), display);
    }

    static void insert(String title) {
        BlockEditorActivity b = blocks();
        if (b != null) b.tutorialInsert(title, null, 0);
    }

    static void insertInside(String parent, int slot, String title) {
        BlockEditorActivity b = blocks();
        if (b != null) b.tutorialInsert(title, node(parent), slot);
    }

    static boolean saved() {
        BlockEditorActivity b = blocks();
        return b != null && !b.hasUnsavedChanges() && b.file().exists();
    }
}
