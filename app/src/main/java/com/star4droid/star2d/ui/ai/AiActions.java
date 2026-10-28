package com.star4droid.star2d.ui.ai;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and runs the `star2d-actions` blocks the assistant emits. Execution runs on the GL thread. */
public final class AiActions {
    private AiActions() {}

    public static final Pattern BLOCK = Pattern.compile("```star2d-actions\\s*\\n([\\s\\S]*?)```");

    public static JsonArray parse(String text) {
        Matcher m = BLOCK.matcher(text);
        JsonArray all = new JsonArray();
        while (m.find()) {
            try {
                JsonElement e = JsonParser.parseString(m.group(1).trim());
                if (e.isJsonArray()) all.addAll(e.getAsJsonArray());
                else if (e.isJsonObject()) all.add(e);
            } catch (Exception ignored) {}
        }
        return all;
    }

    public static boolean isReadOnly(JsonObject a) {
        String t = s(a, "action");
        return t.equals("read_file") || t.equals("list_files") || t.equals("get_element");
    }

    /** One-line human description for the confirmation card. */
    public static String describe(JsonObject a, boolean ar) {
        String t = s(a, "action");
        switch (t) {
            case "read_file": return (ar ? "📄 قراءة " : "📄 Read ") + s(a, "path");
            case "list_files": return (ar ? "📁 عرض ملفات " : "📁 List ") + s(a, "path");
            case "get_element": return (ar ? "🔍 خصائص " : "🔍 Inspect ") + s(a, "name");
            case "set_event_code": return (ar ? "⚡ كتابة الحدث " : "⚡ Set event ") + s(a, "event") + (s(a, "element").isEmpty() ? (ar ? " (المشهد)" : " (scene)") : " · " + s(a, "element"));
            case "add_element": return (ar ? "➕ إضافة " : "➕ Add ") + s(a, "type") + " " + s(a, "name");
            case "set_property": return (ar ? "⚙ " : "⚙ ") + s(a, "element") + "." + s(a, "property") + " = " + s(a, "value");
            case "delete_element": return (ar ? "🗑 حذف " : "🗑 Delete ") + s(a, "element");
            case "add_variable": return (ar ? "𝑥 متغير " : "𝑥 Variable ") + s(a, "type") + " " + s(a, "name") + " = " + s(a, "value");
            case "write_file": return (ar ? "💾 كتابة ملف " : "💾 Write ") + s(a, "path");
        }
        return "? " + t;
    }

    /** Run one action on the GL thread; returns a short result text. */
    public static String run(JsonObject a) {
        try {
            TestApp app = Bridge.app();
            LibgdxEditor ed = Bridge.editor();
            Project p = ed.getProject();
            String t = s(a, "action");
            switch (t) {
                case "read_file": {
                    File f = safe(p, s(a, "path"));
                    if (!f.exists()) return "File not found: " + s(a, "path");
                    String txt = Gdx.files.absolute(f.getAbsolutePath()).readString();
                    return "Content of " + s(a, "path") + ":\n```\n" + AiContext.trim(txt, 12000) + "\n```";
                }
                case "list_files": {
                    File f = safe(p, s(a, "path"));
                    if (!f.isDirectory()) return "Not a folder: " + s(a, "path");
                    StringBuilder sb = new StringBuilder("Files in " + s(a, "path") + ":\n");
                    File[] l = f.listFiles();
                    if (l != null) for (File x : l) sb.append(x.isDirectory() ? "[dir] " : "- ").append(x.getName()).append("\n");
                    return sb.toString();
                }
                case "get_element": {
                    Actor ac = Bridge.actor(s(a, "name"));
                    if (ac == null) return "No element named " + s(a, "name");
                    return "Properties of " + s(a, "name") + ": " + new Gson().toJson(Bridge.snapshot(PropertySet.getPropertySet(ac)));
                }
                case "set_event_code": {
                    String event = s(a, "event"), el = s(a, "element"), code = s(a, "code");
                    String scene = ed.getScene(), body = "";
                    if (!el.isEmpty()) {
                        Actor ac = Bridge.actor(el);
                        if (ac == null) return "No element named " + el;
                        PropertySet ps = PropertySet.getPropertySet(ac);
                        body = ps.getString(ps.containsKey("Script") ? "Script" : "name");
                        if (body.contains("/")) {
                            String[] v = body.split("/");
                            scene = v[0];
                            body = v[1];
                        }
                    }
                    String path = p.getEventPath(scene, body, event);
                    BlockModel m = new BlockModel();
                    BlockModel.Node root = BlockModel.newRoot();
                    root.x = 40;
                    root.y = 40;
                    BlockModel.Node src = new BlockModel.Node();
                    src.title = "addSource";
                    src.code = "%1$s\n%2$s";
                    src.fields.add(new BlockModel.Field("code", code));
                    root.next = src;
                    m.heads.add(root);
                    m.save(new File(path + ".visual"));
                    return "Event " + event + " written.";
                }
                case "add_element": {
                    String type = s(a, "type").toLowerCase(Locale.ROOT).replace(" ", "");
                    int light = type.contains("point") ? 0 : type.contains("directional") ? 1 : type.contains("cone") ? 2 : -1;
                    if (light >= 0) app.getControlLayer().actionAddLight(light);
                    else {
                        String[] kinds = {"box", "circle", "text", "joystick", "progress", "custom", "particle", "camera", "map", "group"};
                        int pos = 0;
                        for (int i = 0; i < kinds.length; i++) if (type.contains(kinds[i])) pos = i;
                        if (type.contains("tiled")) pos = 8;
                        app.getControlLayer().actionAddBody(pos);
                    }
                    StringBuilder res = new StringBuilder("Added " + s(a, "type"));
                    String name = s(a, "name");
                    if (!name.isEmpty()) {
                        String err = PropertyEditing.applyString(app, "name", name);
                        res.append(err == null ? " named " + name : " (rename failed: " + err + ")");
                    }
                    if (a.has("properties") && a.get("properties").isJsonObject())
                        for (Map.Entry<String, JsonElement> e : a.getAsJsonObject("properties").entrySet()) {
                            String err = applyProperty(app, e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString());
                            if (err != null) res.append("; ").append(e.getKey()).append(": ").append(err);
                        }
                    return res.toString();
                }
                case "set_property": {
                    Actor ac = Bridge.actor(s(a, "element"));
                    if (ac == null) return "No element named " + s(a, "element");
                    ed.selectActor(ac);
                    String err = applyProperty(app, s(a, "property"), s(a, "value"));
                    return err == null ? "Set " + s(a, "property") : err;
                }
                case "delete_element": {
                    Actor ac = Bridge.actor(s(a, "element"));
                    if (ac == null) return "No element named " + s(a, "element");
                    ItemActions.delete(app, ac);
                    return "Deleted " + s(a, "element");
                }
                case "add_variable": {
                    FileHandle fh = Gdx.files.absolute(p.getVariables(ed.getScene()));
                    ArrayList<VarModel> list = null;
                    if (fh.exists()) try { list = new Gson().fromJson(fh.readString(), new TypeToken<ArrayList<VarModel>>() {}.getType()); } catch (Exception ignored) {}
                    if (list == null) list = new ArrayList<>();
                    for (VarModel v : list) if (v.getName().equals(s(a, "name"))) return "Variable already exists: " + s(a, "name");
                    list.add(new VarModel().setType(s(a, "type").isEmpty() ? "int" : s(a, "type")).setName(s(a, "name")).setValue(s(a, "value")));
                    fh.writeString(new Gson().toJson(list), false);
                    try { app.getControlLayer().getVarsItem().setFileHandle(fh); } catch (Throwable ignored) {}
                    return "Variable " + s(a, "name") + " added";
                }
                case "write_file": {
                    File f = safe(p, s(a, "path"));
                    Gdx.files.absolute(f.getAbsolutePath()).writeString(s(a, "content"), false);
                    return "Wrote " + s(a, "path");
                }
            }
            return "Unknown action " + t;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private static HashMap<String, String> types;

    /** Apply a property by its schema type (GL thread, acts on the selected element). */
    public static String applyProperty(TestApp app, String field, String value) {
        if (types == null) {
            types = new HashMap<>();
            try {
                JsonObject o = JsonParser.parseString(Gdx.files.internal("types.json").readString()).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet())
                    for (String f : e.getValue().getAsString().split(",")) if (!f.trim().isEmpty()) types.put(f.trim(), e.getKey());
            } catch (Exception ignored) {}
        }
        if (app.getEditor().getSelectedActor() == null) return "nothing selected";
        String type = types.get(field);
        if (type == null) type = "spinner";
        switch (type) {
            case "float":
                try { PropertyEditing.applyFloat(app, field, Float.parseFloat(value)); } catch (NumberFormatException e) { return "not a number"; }
                return null;
            case "boolean": PropertyEditing.applyBoolean(app, field, String.valueOf(Boolean.parseBoolean(value))); return null;
            case "string": return PropertyEditing.applyString(app, field, value);
            case "color": PropertyEditing.applyColor(app, field, value.replace("#", "")); return null;
            case "image": PropertyEditing.applyImage(app, field, value.startsWith("/") ? value : "/" + value); return null;
            case "File": PropertyEditing.applyFile(app, field, value.startsWith("/") ? value : "/" + value); return null;
            case "body": return PropertyEditing.applyBody(app, field, value);
            case "script": PropertyEditing.applyScript(app, field, value); return null;
            case "static": case "points": return "read-only here";
            default: PropertyEditing.applySpinner(app, field, value); return null;
        }
    }

    private static File safe(Project p, String rel) throws Exception {
        File root = new File(p.getPath()).getCanonicalFile();
        File f = new File(root, rel.startsWith("/") ? rel.substring(1) : rel).getCanonicalFile();
        if (!f.getPath().startsWith(root.getPath())) throw new Exception("Path outside the project: " + rel);
        return f;
    }

    static String s(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? "" : (e.isJsonPrimitive() ? e.getAsString() : e.toString());
        } catch (Exception ex) {
            return "";
        }
    }
}
