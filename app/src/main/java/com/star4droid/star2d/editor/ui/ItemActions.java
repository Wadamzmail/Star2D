package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.Hierarchy;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.Utils.Utils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

/**
 * Element (item) actions used by the elements list. Same logic as the LibGDX BodiesList
 * row menu, exposed so the Android UI can call it. Call on the GL thread.
 */
public final class ItemActions {
    private ItemActions() {}

    public static PropertySet<String, Object> props(Object item) {
        if (item instanceof PropertySet) return (PropertySet<String, Object>) item;
        return ((EditorItem) item).getPropertySet();
    }

    /** Remove an item (undoable), like the elements list "delete". */
    public static void delete(TestApp app, Actor actor) {
        if (com.star4droid.star2d.prefab.PrefabSession.isFixedRoot(app.getEditor(), actor)) {
            app.toast(com.star4droid.star2d.ui.Tr.t("prefab_root_locked", "The root of a prefab can't be deleted"));
            return;
        }
        LibgdxEditor ed = app.getEditor();
        ArrayList<Actor> kids = Hierarchy.descendantsOf(ed, actor.getName());
        if (kids.isEmpty()) {
            actor.remove();
            EditorAction.itemRemoved(ed, actor);
            if (ed.getSelectedActor() == actor) ed.selectActor(null);
        } else {
            // the element and everything inside it, one undo step (undo brings the parent back first)
            ArrayList<EditorAction> subs = new ArrayList<>();
            for (int i = kids.size() - 1; i >= 0; i--) {
                Actor k = kids.get(i);
                subs.add(EditorAction.itemRemovedQuiet(ed, k));
                k.remove();
                if (ed.getSelectedActor() == k) ed.selectActor(null);
            }
            subs.add(EditorAction.itemRemovedQuiet(ed, actor));
            actor.remove();
            if (ed.getSelectedActor() == actor) ed.selectActor(null);
            ed.pushUndoEvent(EditorAction.batch(ed, "delete " + actor.getName(), subs));
        }
        Hierarchy.relink(ed);
        if (app.getControlLayer() != null) app.getControlLayer().getBodiesList().update();
    }

    /** Duplicate an item with the next free name (Box1 -> Box2...). Returns the copy or null. */
    public static Actor duplicate(TestApp app, Actor source) throws Exception {
        LibgdxEditor editor = app.getEditor();
        if (com.star4droid.star2d.prefab.PrefabSession.isFixedRoot(editor, source)) {
            app.toast(com.star4droid.star2d.ui.Tr.t("prefab_root_locked_copy", "The root of a prefab can't be duplicated"));
            return null;
        }
        ArrayList<Actor> kids = Hierarchy.descendantsOf(editor, source.getName());
        if (!kids.isEmpty()) return duplicateTree(app, source, kids);
        Constructor<?> cc = source.getClass().getConstructor(LibgdxEditor.class);
        EditorItem i = (EditorItem) cc.newInstance(editor);
        PropertySet<String, Object> ps = new PropertySet<>();
        ps.putAll(props(source));
        // a copy of a prefab part is a local addition, not a second copy of the same prefab node
        String srcPid = ps.getString("pid");
        if (!srcPid.isEmpty() && !srcPid.equals("root")) {
            ps.put("pid", "");
            ps.put("pbase", "");
        }
        String name = BodiesList.getRealNameAndNum(props(source).getString("name"));
        String result = name.split(" ")[0];
        int num = Utils.getInt(name.split(" ")[1]);
        ArrayList<String> bodies = editor.getBodiesList();
        while (bodies.contains(result + num)) num++;
        ps.put("name", result + num);
        ((Actor) i).setName(result + num);
        i.setProperties(ps);
        i.update();
        editor.selectActor((Actor) i);
        EditorAction.itemAdded(editor, (Actor) i);
        return (Actor) i;
    }

    /** One copy of an element with a fresh unique name; it is added to the editor, nothing is selected or pushed. */
    private static Actor copyOne(LibgdxEditor editor, Actor source, String parentName) throws Exception {
        Constructor<?> cc = source.getClass().getConstructor(LibgdxEditor.class);
        EditorItem i = (EditorItem) cc.newInstance(editor);
        PropertySet<String, Object> ps = new PropertySet<>();
        ps.putAll(props(source));
        // a copy of a prefab part is a local addition, not a second copy of the same prefab node
        String srcPid = ps.getString("pid");
        if (!srcPid.isEmpty() && !srcPid.equals("root")) {
            ps.put("pid", "");
            ps.put("pbase", "");
        }
        String name = BodiesList.getRealNameAndNum(props(source).getString("name"));
        String result = name.split(" ")[0];
        int num = Utils.getInt(name.split(" ")[1]);
        ArrayList<String> bodies = editor.getBodiesList();
        while (bodies.contains(result + num)) num++;
        ps.put("name", result + num);
        if (parentName != null) ps.put("parent", parentName);
        ((Actor) i).setName(result + num);
        i.setProperties(ps);
        i.update();
        return (Actor) i;
    }

    /** Duplicates a group with everything inside it (fresh names, the copies keep their structure). */
    private static Actor duplicateTree(TestApp app, Actor source, ArrayList<Actor> kids) throws Exception {
        LibgdxEditor editor = app.getEditor();
        java.util.HashMap<String, String> names = new java.util.HashMap<>();
        ArrayList<EditorAction> subs = new ArrayList<>();
        Actor first = copyOne(editor, source, null);
        names.put(source.getName(), first.getName());
        subs.add(EditorAction.itemAddedQuiet(editor, first));
        for (Actor k : kids) { // parents come before their children
            String np = names.get(props(k).getString("parent"));
            Actor c = copyOne(editor, k, np == null ? props(k).getString("parent") : np);
            names.put(k.getName(), c.getName());
            subs.add(EditorAction.itemAddedQuiet(editor, c));
        }
        Hierarchy.relink(editor);
        editor.selectActor(first);
        editor.pushUndoEvent(EditorAction.batch(editor, "duplicate " + source.getName(), subs));
        if (app.getControlLayer() != null) app.getControlLayer().getBodiesList().update(true);
        return first;
    }

    /** Puts an element into a group (or out of every group when parent is empty). Position is kept. Returns an error or null. */
    public static String moveInto(TestApp app, String name, String parent) {
        return Hierarchy.setParent(app.getEditor(), name, parent == null ? "" : parent, true);
    }

    /**
     * A new empty group around an element: same place and size, the same parent as the element, and the element goes inside.
     * One undo step. Returns the new group or null.
     */
    public static Actor wrapInGroup(TestApp app, Actor target) {
        LibgdxEditor ed = app.getEditor();
        PropertySet<String, Object> tp = props(target);
        if (tp == null) return null;
        com.star4droid.star2d.editor.items.EditorGroupItem g = new com.star4droid.star2d.editor.items.EditorGroupItem(ed);
        g.setName(Hierarchy.freeName(ed, "Group"));
        g.setDefault();
        PropertySet<String, Object> gp = g.getPropertySet();
        gp.put("name", g.getName());
        gp.put("Script", g.getName());
        float w = Math.max(20f, target.getWidth()), h = Math.max(20f, target.getHeight());
        gp.put("width", w);
        gp.put("height", h);
        gp.put("x", target.getX() + target.getWidth() * 0.5f - w * 0.5f);
        gp.put("y", target.getY() + target.getHeight() * 0.5f - h * 0.5f);
        gp.put("z", tp.getString("z"));
        String oldParent = Hierarchy.parentOf(tp);
        gp.put("parent", oldParent);
        g.update();
        ArrayList<EditorAction> subs = new ArrayList<>();
        subs.add(EditorAction.itemAddedQuiet(ed, g));
        tp.put("parent", g.getName());
        EditorAction a = EditorAction.propertyChangedQuiet(ed, target.getName(), "parent", "parent", g.getName(), oldParent);
        if (a != null) subs.add(a);
        Hierarchy.relink(ed);
        ed.selectActor(g);
        ed.pushUndoEvent(EditorAction.batch(ed, "group " + target.getName(), subs));
        Hierarchy.refreshLists(ed);
        return g;
    }

    /** Removes a group but keeps what was inside: the children go to the group's own parent. One undo step. */
    public static void ungroup(TestApp app, Actor group) {
        LibgdxEditor ed = app.getEditor();
        PropertySet<String, Object> gp = props(group);
        if (gp == null) return;
        if (com.star4droid.star2d.prefab.PrefabSession.isFixedRoot(ed, group)) {
            app.toast(com.star4droid.star2d.ui.Tr.t("prefab_root_locked", "The root of a prefab can't be deleted"));
            return;
        }
        String up = Hierarchy.parentOf(gp);
        ArrayList<EditorAction> subs = new ArrayList<>();
        for (Actor k : Hierarchy.childrenOf(ed, group.getName())) {
            PropertySet<String, Object> kp = props(k);
            kp.put("parent", up);
            EditorAction a = EditorAction.propertyChangedQuiet(ed, k.getName(), "parent", "parent", up, group.getName());
            if (a != null) subs.add(a);
        }
        subs.add(EditorAction.itemRemovedQuiet(ed, group));
        group.remove();
        if (ed.getSelectedActor() == group) ed.selectActor(null);
        Hierarchy.relink(ed);
        ed.pushUndoEvent(EditorAction.batch(ed, "ungroup " + group.getName(), subs));
        Hierarchy.refreshLists(ed);
    }

    /** Toggle "lock" (undoable). */
    public static void toggleLock(TestApp app, Object item, String name) {
        String from = props(item).getString("lock");
        String to = from.equals("true") ? "false" : "true";
        props(item).put("lock", to);
        if (item instanceof EditorItem) ((EditorItem) item).update();
        app.getEditor().updateProperties();
        EditorAction a = EditorAction.propertiesChanged(app.getEditor(), name, "lock", new String[]{"lock", to}, new String[]{"lock", from});
        if (a != null) a.updateItemProperties().updateEditorProperties();
    }

    /** Toggle "Visible" (undoable). */
    public static void toggleVisible(TestApp app, Object item, String name) {
        String from = props(item).getString("Visible");
        String to = from.equals("true") ? "false" : "true";
        props(item).put("Visible", to);
        EditorAction a = EditorAction.propertiesChanged(app.getEditor(), name, "Visible", new String[]{"Visible", to}, new String[]{"Visible", from});
        if (a != null) a.updateItemProperties().updateEditorProperties();
        if (item instanceof EditorItem) ((EditorItem) item).update();
    }

    /** Rename an item (moves its body script too). Returns an error or null. */
    public static String rename(TestApp app, Actor actor, String newName) {
        app.getEditor().selectActor(actor);
        return PropertyEditing.applyString(app, "name", newName);
    }

    /** Move an item up/down in drawing order (z), undoable. */
    public static void changeZ(TestApp app, Actor actor, int delta) {
        LibgdxEditor editor = app.getEditor();
        editor.selectActor(actor);
        PropertySet<String, Object> ps = props(actor);
        float z = 0;
        try { z = Float.parseFloat(ps.getString("z")); } catch (Exception ignored) {}
        PropertyEditing.applyFloat(app, "z", Math.max(0, z + delta));
    }
}
