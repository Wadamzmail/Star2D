package com.star4droid.star2d.remote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.blocks.BlockCatalog;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.util.ArrayList;
import java.util.Locale;

/** JSON (what the AI writes) <-> BlockModel (what the visual editor shows and generates Java from). */
final class BlockBuilder {
    private BlockBuilder() {}

    private static final int MAX_DEPTH = 40;

    static BlockModel raw(String java) {
        BlockModel m = new BlockModel();
        BlockModel.Node root = BlockModel.newRoot();
        root.x = 40;
        root.y = 40;
        BlockModel.Node src = new BlockModel.Node();
        src.title = "addSource";
        src.code = "%1$s\n%2$s";
        src.fields.add(new BlockModel.Field("code", java));
        root.next = src;
        m.heads.add(root);
        return m;
    }

    static BlockModel build(JsonArray blocks) {
        BlockModel m = new BlockModel();
        BlockModel.Node root = BlockModel.newRoot();
        root.x = 40;
        root.y = 40;
        root.next = chain(blocks, "blocks", 0);
        m.heads.add(root);
        return m;
    }

    private static BlockModel.Node chain(JsonArray arr, String path, int depth) {
        if (depth > MAX_DEPTH) throw new Gl.ToolError("blocks are nested too deeply at " + path);
        BlockModel.Node first = null, last = null;
        if (arr == null) return null;
        for (int i = 0; i < arr.size(); i++) {
            BlockModel.Node n = one(arr.get(i), path + "[" + i + "]", depth);
            if (first == null) first = n;
            else last.next = n;
            last = n;
        }
        return first;
    }

    private static BlockModel.Node one(JsonElement e, String path, int depth) {
        if (e.isJsonPrimitive()) return javaNode(e.getAsString());
        if (!e.isJsonObject()) throw new Gl.ToolError(path + ": a block must be an object like {\"block\":\"title\",\"fields\":{...}}");
        JsonObject o = e.getAsJsonObject();
        if (!o.has("block") && o.has("java")) return javaNode(CoreTools.s(o, "java"));
        String title = CoreTools.s(o, "block");
        if (title.isEmpty()) throw new Gl.ToolError(path + ": missing \"block\" (the block title, see block_catalog)");
        BlockCatalog.Def d = BlockCatalog.find(title);
        if (d == null) d = findLoose(title);
        if (d == null) throw new Gl.ToolError(path + ": unknown block '" + title + "'." + suggest(title));
        BlockModel.Node n = d.create();
        // fields
        if (o.has("fields")) {
            JsonElement fe = o.get("fields");
            if (fe.isJsonArray()) {
                JsonArray fa = fe.getAsJsonArray();
                if (fa.size() > n.fields.size()) throw new Gl.ToolError(path + " (" + title + "): has " + n.fields.size() + " fields, got " + fa.size());
                for (int i = 0; i < fa.size(); i++) setField(n.fields.get(i), fa.get(i), path + ".fields[" + i + "]", depth);
            } else if (fe.isJsonObject()) {
                for (java.util.Map.Entry<String, JsonElement> en : fe.getAsJsonObject().entrySet()) {
                    BlockModel.Field f = null;
                    for (BlockModel.Field x : n.fields) if (x.label().equalsIgnoreCase(en.getKey()) || x.name.equalsIgnoreCase(en.getKey())) f = x;
                    if (f == null) {
                        ArrayList<String> labels = new ArrayList<>();
                        for (BlockModel.Field x : n.fields) labels.add(x.label());
                        throw new Gl.ToolError(path + " (" + title + "): no field '" + en.getKey() + "'. Fields: " + labels);
                    }
                    setField(f, en.getValue(), path + ".fields." + en.getKey(), depth);
                }
            }
        }
        boolean hasDo = o.has("do") && o.get("do").isJsonArray(), hasElse = o.has("else") && o.get("else").isJsonArray();
        if ((hasDo || hasElse) && !n.isIf)
            throw new Gl.ToolError(path + " (" + title + "): this block has no inner part, so 'do'/'else' are not allowed. Use a container block (if, while, repeat...).");
        if (hasDo) n.yes = chain(o.getAsJsonArray("do"), path + ".do", depth + 1);
        if (hasElse) {
            if (!n.hasElse()) throw new Gl.ToolError(path + " (" + title + "): this block has no 'else' part");
            n.no = chain(o.getAsJsonArray("else"), path + ".else", depth + 1);
        }
        return n;
    }

    private static BlockModel.Node javaNode(String code) {
        BlockModel.Node src = new BlockModel.Node();
        src.title = "addSource";
        src.code = "%1$s\n%2$s";
        src.fields.add(new BlockModel.Field("code", code));
        return src;
    }

    private static void setField(BlockModel.Field f, JsonElement v, String path, int depth) {
        if (v == null || v.isJsonNull()) return;
        if (v.isJsonObject()) {
            f.block = param(v.getAsJsonObject(), path, depth + 1);
            f.value = "";
        } else {
            f.value = text(v);
            f.block = null;
        }
    }

    private static String text(JsonElement v) {
        if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
            double d = v.getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < 1e9 ? String.valueOf((long) d) : String.valueOf(d);
        }
        return v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    private static BlockModel.Param param(JsonObject o, String path, int depth) {
        if (depth > MAX_DEPTH) throw new Gl.ToolError("value blocks are nested too deeply at " + path);
        String disp = CoreTools.s(o, "r");
        if (disp.isEmpty()) throw new Gl.ToolError(path + ": a value block looks like {\"r\":\"<text from block_catalog reporters>\",\"args\":[...]}");
        BlockCatalog.Reporter r = findReporter(disp);
        if (r == null) throw new Gl.ToolError(path + ": unknown value block '" + disp + "'." + suggestReporter(disp));
        BlockModel.Param p = new BlockModel.Param(r.template, r.display);
        JsonArray args = o.has("args") && o.get("args").isJsonArray() ? o.getAsJsonArray("args") : new JsonArray();
        if (args.size() != p.inner.size())
            throw new Gl.ToolError(path + ": '" + r.display + "' takes " + p.inner.size() + " argument(s), got " + args.size());
        for (int i = 0; i < args.size(); i++) setField(p.inner.get(i), args.get(i), path + ".args[" + i + "]", depth);
        return p;
    }

    // ------------------------------------------------------------------ lookup helpers

    private static BlockCatalog.Def findLoose(String title) {
        String t = title.trim().toLowerCase(Locale.ROOT);
        for (java.util.List<BlockCatalog.Def> l : BlockCatalog.CATEGORIES.values())
            for (BlockCatalog.Def d : l) if (d.title.toLowerCase(Locale.ROOT).equals(t)) return d;
        return null;
    }

    private static BlockCatalog.Reporter findReporter(String display) {
        String t = norm(display);
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (r.display.equals(display)) return r;
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (norm(r.display).equals(t)) return r;
        return null;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String suggest(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        ArrayList<String> hits = new ArrayList<>();
        for (java.util.List<BlockCatalog.Def> l : BlockCatalog.CATEGORIES.values())
            for (BlockCatalog.Def d : l) {
                String x = d.title.toLowerCase(Locale.ROOT);
                if (x.contains(t) || t.contains(x) || common(x, t) >= 4) hits.add(d.title);
                if (hits.size() >= 8) break;
            }
        return hits.isEmpty() ? " Call block_catalog (search=...) to find the right title." : " Did you mean: " + hits + "?";
    }

    private static String suggestReporter(String disp) {
        String t = norm(disp);
        ArrayList<String> hits = new ArrayList<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
            String x = norm(r.display);
            if (x.contains(t) || t.contains(x) || common(x, t) >= 5) hits.add(r.display);
            if (hits.size() >= 8) break;
        }
        return hits.isEmpty() ? " Call block_catalog (search=...) to see the value blocks." : " Did you mean: " + hits + "?";
    }

    /** Length of the longest common substring (small strings only). */
    private static int common(String a, String b) {
        int best = 0;
        for (int i = 0; i < a.length(); i++)
            for (int j = 0; j < b.length(); j++) {
                int k = 0;
                while (i + k < a.length() && j + k < b.length() && a.charAt(i + k) == b.charAt(j + k)) k++;
                if (k > best) best = k;
            }
        return best;
    }

    // ------------------------------------------------------------------ BlockModel -> JSON (events get)

    static JsonArray describe(BlockModel m) {
        BlockModel.Node root = m.root();
        if (root == null && !m.heads.isEmpty()) root = m.heads.get(0);
        JsonArray out = new JsonArray();
        if (root == null) return out;
        BlockModel.Node n = root.isRoot() ? root.next : root;
        int guard = 0;
        while (n != null && guard++ < 5000) {
            out.add(describeNode(n, 0));
            n = n.next;
        }
        return out;
    }

    private static JsonObject describeNode(BlockModel.Node n, int depth) {
        JsonObject o = new JsonObject();
        if ("addSource".equals(n.title) && n.fields.size() == 1) {
            o.addProperty("java", n.fields.get(0).code());
            return o;
        }
        o.addProperty("block", n.title);
        if (!n.fields.isEmpty()) {
            JsonObject f = new JsonObject();
            for (BlockModel.Field x : n.fields) f.add(x.label(), describeField(x));
            o.add("fields", f);
        }
        if (depth < MAX_DEPTH) {
            if (n.yes != null) o.add("do", chainOf(n.yes, depth + 1));
            if (n.no != null) o.add("else", chainOf(n.no, depth + 1));
        }
        return o;
    }

    private static JsonArray chainOf(BlockModel.Node first, int depth) {
        JsonArray a = new JsonArray();
        int guard = 0;
        for (BlockModel.Node n = first; n != null && guard++ < 5000; n = n.next) a.add(describeNode(n, depth));
        return a;
    }

    private static JsonElement describeField(BlockModel.Field f) {
        if (f.block == null) return new com.google.gson.JsonPrimitive(f.value);
        JsonObject o = new JsonObject();
        o.addProperty("r", f.block.display);
        JsonArray args = new JsonArray();
        for (BlockModel.Field x : f.block.inner) args.add(describeField(x));
        o.add("args", args);
        return o;
    }
}
