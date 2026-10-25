package com.star4droid.star2d.editor.ui.sub;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.utils.EditorAction;
import com.star4droid.template.Utils.Utils;

/**
 * The "commit a property value" logic for every property type, shared by the LibGDX
 * {@link EditorField} and the native Android properties panel so both behave identically
 * (same undo entries, same item refresh calls).
 * All methods must run on the GL thread and act on the editor's selected actor.
 * Methods returning String return null on success or an error message.
 */
public final class PropertyEditing {
    private PropertyEditing() {}

    private static PropertySet<String, Object> ps(LibgdxEditor editor) {
        return PropertySet.getPropertySet(editor.getSelectedActor());
    }

    /** type "script": value from the body-script selector ("name" or "scene/name"). */
    public static void applyScript(TestApp app, String field, String script) {
        try {
            if (script.startsWith("/") && script.length() > 1)
                script = script.substring(1);
            LibgdxEditor editor = app.getEditor();
            PropertySet<String, Object> ps = ps(editor);
            String old = ps.getString(field);
            ps.put(field, script);
            editor.getSaveState();
            EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, script}, new String[]{field, old});
        } catch (Exception ignored) {}
    }

    /** type "body": single body name, or "(a)(b)" for Collision. */
    public static String applyBody(TestApp app, String field, String value) {
        try {
            LibgdxEditor editor = app.getEditor();
            if (field.equals("parent")) {
                if (value.equals(""))
                    PropertySet.getPropertySet(editor.getSelectedActor()).setParent(null);
                if (!value.equals(""))
                    for (Actor actor : editor.getActors()) {
                        if (actor instanceof EditorItem && actor.getName().equals(value)) {
                            PropertySet set = PropertySet.getPropertySet(actor);
                            if (!PropertySet.getPropertySet(editor.getSelectedActor()).setParent(set))
                                return "Can't set as parent!";
                            break;
                        }
                    }
            }
            PropertySet<String, Object> ps = ps(editor);
            String old = ps.getString(field);
            ps.put(field, value);
            editor.getSaveState();
            if (field.equals("parent") && app.getControlLayer() != null)
                app.getControlLayer().getBodiesList().update(true);
            EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, value}, new String[]{field, old});
        } catch (Exception ignored) {}
        return null;
    }

    /** type "file" (file / font / map), for a path relative to the picker root (starts with "/"). Not for TTF/OTF fonts. */
    public static void applyFile(TestApp app, String field, String path) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> propertySet = ps(editor);
        String old = propertySet.getString(field);
        String stored = field.equals("font") ? path : path.replace("/", Utils.seperator);
        propertySet.put(field, stored);
        ((EditorItem) editor.getSelectedActor()).setProperties(propertySet);
        editor.getSaveState();
        EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, stored}, new String[]{field, old}).setItemProperties();
    }

    /** Root folder and extensions used by the "file" type picker for this field. */
    public static String fileRoot(LibgdxEditor editor, String field) {
        return field.equals("font") ? editor.getProject().getPath()
                : (field.toLowerCase().equals("map") ? editor.getProject().getImagesPath() : editor.getProject().get("files"));
    }

    public static String[] fileExtensions(String field) {
        return field.equals("font") ? new String[]{"s2df", "ttf", "otf"}
                : (field.toLowerCase().equals("map") ? new String[]{"tmx"} : new String[]{"p", "particle"});
    }

    /** TTF/OTF font: opens the (LibGDX) bitmap font creator, then stores the generated s2df font. */
    public static void applyTtfFont(TestApp app, String field, FileHandle fhandle, String path) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> propertySet = ps(editor);
        String old = propertySet.getString(field);
        app.getFileBrowser().getBitmapFontEditor().setData(fhandle.parent(), null).setOnSave((name, p) -> {
            name = (path.contains("/") ? path.substring(0, path.lastIndexOf("/")) : "") + "/" + name;
            while (name.contains("//")) name = name.replace("//", "/");
            propertySet.put(field, name);
            ((EditorItem) (editor.getSelectedActor())).setProperties(propertySet);
            editor.getSaveState();
            EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, name}, new String[]{field, old}).setItemProperties();
        }).setTTF("/" + fhandle.name()).show(app.getUiStage());
    }

    /** type "color": hex like "#FFFFFFFF" (LibGDX Color.toString style is also accepted). */
    public static void applyColor(TestApp app, String field, String value) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> p = ps(editor);
        String old = p.getString(field);
        p.put(field, value);
        ((EditorItem) (editor.getSelectedActor())).setProperties(p);
        editor.updateProperties();
        editor.getSaveState();
        EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, value}, new String[]{field, old}).updateEditorProperties().setItemProperties();
    }

    /** type "float". */
    public static void applyFloat(TestApp app, String field, float f) {
        try {
            LibgdxEditor editor = app.getEditor();
            PropertySet<String, Object> propertySet = ps(editor);
            String old = propertySet.getString(field);
            propertySet.put(field, f);
            Array<String> oldValues = new Array<>(), newValues = new Array<>();
            newValues.add(field);
            newValues.add(String.valueOf(f));
            oldValues.add(field);
            oldValues.add(old);
            String[][] mirrors = {{"radius", "Collider Radius"}, {"width", "Collider Width"}, {"height", "Collider Height"}};
            for (String[] m : mirrors) {
                if (field.equals(m[0]) && propertySet.containsKey(m[1])) {
                    oldValues.add(m[1]);
                    oldValues.add(propertySet.getString(m[1]));
                    propertySet.put(m[1], f);
                    newValues.add(m[1]);
                    newValues.add(String.valueOf(f));
                }
            }
            String[] oldArray = new String[oldValues.size], newArray = new String[newValues.size];
            for (int i = 0; i < oldValues.size; i++) {
                oldArray[i] = oldValues.get(i);
                newArray[i] = newValues.get(i);
            }
            EditorAction action = EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, newArray, oldArray);
            editor.updateProperties();
            editor.getSaveState();
            if (field.equals("tileX") || field.equals("tileY") || field.toLowerCase().contains("progress")) {
                ((EditorItem) (editor.getSelectedActor())).setProperties(propertySet);
                if (action != null) action.setItemProperties();
            } else {
                ((EditorItem) editor.getSelectedActor()).update();
                if (action != null) action.updateItemProperties();
            }
            editor.getSaveState();
        } catch (Exception ex) {
            Gdx.files.external("floatUndo.txt").writeString("error : " + Utils.getStackTraceString(ex) + "\n" + "_".repeat(15) + "\n", true);
        }
    }

    /** type "image": path relative to the images folder (starts with "/"). */
    public static void applyImage(TestApp app, String field, String path) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> propertySet = ps(editor);
        String old = propertySet.getString(field);
        propertySet.put(field, path.replace(Utils.seperator, "/"));
        String shown = propertySet.getString(field);
        ((EditorItem) (editor.getSelectedActor())).setProperties(propertySet);
        editor.getSaveState();
        EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, shown.replace("/", Utils.seperator)}, new String[]{field, old}).setItemProperties();
    }

    /** type "boolean": "true" / "false". */
    public static void applyBoolean(TestApp app, String field, String value) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> propertySet = ps(editor);
        String old = propertySet.getString(field);
        propertySet.put(field, value);
        EditorAction action = EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, value}, new String[]{field, old});
        if (field.equals("Visible")) {
            ((EditorItem) editor.getSelectedActor()).update();
            if (action != null) action.updateItemProperties();
        }
    }

    /** type "spinner". (Like the original, setItemProperties() is always flagged.) */
    public static void applySpinner(TestApp app, String field, String value) {
        LibgdxEditor editor = app.getEditor();
        PropertySet<String, Object> p = ps(editor);
        String old = p.getString(field);
        p.put(field, value);
        EditorAction action = EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, value}, new String[]{field, old});
        if (field.equals("Light Type") || field.equals("Shape"))
            ((EditorItem) (editor.getSelectedActor())).setProperties(ps(editor));
        if (action != null) action.setItemProperties();
        editor.getSaveState();
    }

    /** type "string" (name / Text). Returns an error message, or null when applied. */
    public static String applyString(TestApp app, String field, String value) {
        LibgdxEditor editor = app.getEditor();
        if (field.equals("name")) {
            if (editor.getBodiesList().contains(value))
                return "Name already used by another element!";
            for (char c : value.toCharArray())
                if (!EditorField.allowedChars.contains(String.valueOf(c)))
                    return "Use A-Z a-z 0-9 or _ , not allowed: " + c;
        }
        PropertySet<String, Object> ps = ps(editor);
        String scr = ps.getString("Script");
        if ((scr.contains("/") && scr.split("/")[0].equals(editor.getScene()) && scr.split("/")[1].equals(ps.getString("name")))
                || (field.equals("name") && scr.equals(ps.getString("name")) && !ps.getString("name").equals(value))) {
            ps.put("Script", value);
        }
        String old = ps.getString(field);
        try {
            if (field.equals("name")) {
                FileHandle newHandle = Gdx.files.absolute(editor.getProject().getBodyScriptPath(value, editor.getScene()));
                Gdx.files.absolute(editor.getProject().getBodyScriptPath(ps.getString("name"), editor.getScene())).moveTo(newHandle);
                newHandle.writeString(newHandle.readString().replace(old + "Script", value + "Script"), false);
                if (app.getControlLayer() != null) app.getControlLayer().getBodiesList().update(false);
            }
        } catch (Exception ignored) {}
        ps.put(field, value);
        ((EditorItem) editor.getSelectedActor()).update();
        editor.getSaveState();
        if (field.equals("name")) {
            editor.getSelectedActor().setName(value);
            EditorAction.itemRenamed(editor, old, value).updateEditorProperties();
        } else {
            EditorAction a = EditorAction.propertiesChanged(editor, editor.getSelectedActor().getName(), field, new String[]{field, value}, new String[]{field, old});
            if (a != null) a.updateItemProperties();
        }
        return null;
    }
}
