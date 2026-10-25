package com.star4droid.star2d.editor.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
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
        actor.remove();
        EditorAction.itemRemoved(app.getEditor(), actor);
        if (app.getEditor().getSelectedActor() == actor) app.getEditor().selectActor(null);
        if (app.getControlLayer() != null) app.getControlLayer().getBodiesList().update();
    }

    /** Duplicate an item with the next free name (Box1 -> Box2...). Returns the copy or null. */
    public static Actor duplicate(TestApp app, Actor source) throws Exception {
        LibgdxEditor editor = app.getEditor();
        Constructor<?> cc = source.getClass().getConstructor(LibgdxEditor.class);
        EditorItem i = (EditorItem) cc.newInstance(editor);
        PropertySet<String, Object> ps = new PropertySet<>();
        ps.putAll(props(source));
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
