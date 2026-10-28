package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.prefab.PrefabEditorSync;
import com.star4droid.star2d.prefab.PrefabOps;
import com.star4droid.star2d.prefab.PrefabStore;

/** The `prefabs` tool: create, add to the scene, apply / revert / unpack / link, and manage the prefabs folder. */
final class PrefabTools {
    private PrefabTools() {}

    private static JsonObject res(String op, boolean ok, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("op", op);
        o.addProperty("ok", ok);
        if (msg != null && !msg.isEmpty()) o.addProperty("msg", msg);
        return o;
    }

    static ToolResult prefabs(JsonObject a) throws Exception {
        final JsonArray ops = EditTools.opsOf(a);
        final JsonArray results = new JsonArray();
        PrefabEditorSync.Outcome<Void> out = PrefabEditorSync.runBlocking(() -> {
            Project p = Proj.project();
            for (JsonElement e : ops) {
                String op = "?";
                try {
                    JsonObject o = e.getAsJsonObject();
                    op = CoreTools.s(o, "op");
                    results.add(one(p, op, o));
                } catch (Throwable t) {
                    results.add(res(op, false, t.getMessage() != null ? t.getMessage() : String.valueOf(t)));
                }
            }
            return null;
        });
        if (out.error != null) throw new Gl.ToolError(out.errorMessage());
        JsonObject all = new JsonObject();
        all.add("results", results);
        if (!out.report.isEmpty()) all.addProperty("editor", out.report);
        return ToolResult.text(all.toString());
    }

    private static String need(JsonObject o, String key) {
        String v = CoreTools.s(o, key);
        if (v.isEmpty()) throw new Gl.ToolError("'" + key + "' is required");
        return v;
    }

    private static float f(JsonObject o, String k, float def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : (float) e.getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    private static JsonObject one(Project p, String op, JsonObject o) throws Exception {
        String scene = Proj.sceneOrDefault(CoreTools.s(o, "scene"));
        switch (op) {
            case "list": {
                JsonObject r = res(op, true, null);
                JsonArray arr = new JsonArray();
                for (String path : PrefabStore.list(p)) arr.add(path);
                r.add("prefabs", arr);
                JsonObject inst = new JsonObject();
                for (String[] i : PrefabOps.instances(p)) {
                    JsonArray l = inst.has(i[0]) ? inst.getAsJsonArray(i[0]) : new JsonArray();
                    l.add(i[1] + ":" + i[2]);
                    inst.add(i[0], l);
                }
                r.add("instances", inst);
                return r;
            }
            case "create": {
                String path = need(o, "path");
                String from = CoreTools.s(o, "from_element");
                if (from.isEmpty()) {
                    String n = PrefabStore.create(p, path);
                    return res(op, true, "created empty prefab '" + n + "' (a GROUP root). Add it to a scene with op add, put elements under the instance, then op apply.");
                }
                String root = PrefabOps.extract(p, scene, from, path);
                JsonObject r = res(op, true, "prefab '" + PrefabStore.normalize(path) + "' made from '" + from + "'; the element is now its instance (root: " + root + ")");
                r.addProperty("instance", root);
                return r;
            }
            case "add": {
                String path = need(o, "path");
                PrefabOps.Result r0 = PrefabOps.instantiate(p, scene, path, f(o, "x", 0), f(o, "y", 0), CoreTools.s(o, "name"));
                JsonObject r = res(op, true, null);
                r.addProperty("root", r0.root);
                JsonArray c = new JsonArray();
                for (String n : r0.created) c.add(n);
                r.add("created", c);
                return r;
            }
            case "apply":
                PrefabOps.apply(p, scene, need(o, "element"));
                return res(op, true, "saved into the prefab and updated every other instance");
            case "revert":
                PrefabOps.revert(p, scene, need(o, "element"));
                return res(op, true, "instance restored to the prefab");
            case "unpack":
                PrefabOps.unpack(p, scene, need(o, "element"));
                return res(op, true, "now plain elements");
            case "link":
                PrefabOps.link(p, scene, need(o, "element"), need(o, "path"));
                return res(op, true, "the element and everything under it were replaced by an instance of the prefab");
            case "info": {
                PrefabOps.Info i = PrefabOps.info(p, scene, need(o, "element"));
                JsonObject r = res(op, true, i == null ? "not part of a prefab instance" : null);
                if (i != null) {
                    r.addProperty("prefab", i.prefabPath);
                    r.addProperty("instance", i.rootName);
                    r.addProperty("pid", i.pid);
                    r.addProperty("missing", i.missing);
                    r.addProperty("overrides", i.modifiedCount);
                    JsonArray m = new JsonArray();
                    for (String s : i.modified) m.add(s);
                    r.add("modified", m);
                }
                return r;
            }
            case "delete":
                PrefabStore.delete(p, need(o, "path"));
                return res(op, true, "deleted; instances in scenes stay as copies that no longer follow a prefab");
            case "rename":
                PrefabStore.rename(p, need(o, "path"), need(o, "to"));
                return res(op, true, "renamed; instances were re-pointed");
            case "duplicate":
                return res(op, true, "created '" + PrefabStore.duplicate(p, need(o, "path"), need(o, "to")) + "'");
            case "mkdir":
                PrefabStore.createFolder(p, need(o, "path"));
                return res(op, true, null);
            default:
                return res(op, false, "unknown op (list|create|add|apply|revert|unpack|link|info|delete|rename|duplicate|mkdir)");
        }
    }
}
