package com.star4droid.star2d.ui.blocks;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visual script model, 100% compatible with the LibGDX node editor files (.visual) and its
 * code export (NodeSerializer.exportCode): same JSON keys, string values, entry node rules,
 * placeholder order (fields, [true, false], next) and 4-space indentation.
 */
public class BlockModel {

    public static final String ROOT_TITLE = "First >";

    /** A field of a node, optionally holding a nested reporter (parameter) block. */
    public static class Field {
        public String name;
        public String value = "";
        public Param block;

        public Field(String name, String value) {
            this.name = name;
            this.value = value == null ? "" : value;
        }

        public String code() {
            return block != null ? block.code() : value;
        }

        public Field copy() {
            Field f = new Field(name, value);
            if (block != null) f.block = block.copy();
            return f;
        }

        /** Label shown to the user: "body(Body)" -> "body". */
        public String label() {
            return labelOf(name);
        }

        /** Type token: "body(Body)" -> "Body", "x" -> "". */
        public String type() {
            int a = name.indexOf('('), b = name.lastIndexOf(')');
            return a >= 0 && b > a ? name.substring(a + 1, b) : "";
        }

        public static String labelOf(String name) {
            int a = name.indexOf('(');
            return a > 0 ? name.substring(0, a) : name;
        }
    }

    /** A reporter block inside a field: String.format(template, inner values). */
    public static class Param {
        public String template, display;
        public final ArrayList<Field> inner = new ArrayList<>();

        public Param(String template, String display) {
            this.template = template;
            this.display = display;
            int n = count(display, "___");
            for (int i = 0; i < n; i++) inner.add(new Field("___", ""));
        }

        public String code() {
            Object[] args = new Object[inner.size()];
            for (int i = 0; i < inner.size(); i++) args[i] = inner.get(i).code();
            try {
                return String.format(template, args);
            } catch (Exception e) {
                return "";
            }
        }

        /** Human text with inner values filled in. */
        public String text() {
            String s = display;
            for (Field f : inner) {
                String v = f.block != null ? "(" + f.block.text() + ")" : (f.value.isEmpty() ? "…" : f.value);
                int i = s.indexOf("___");
                if (i < 0) break;
                s = s.substring(0, i) + v + s.substring(i + 3);
            }
            return s;
        }

        public Param copy() {
            Param p = new Param(template, display);
            p.inner.clear();
            for (Field f : inner) p.inner.add(f.copy());
            return p;
        }

        static int count(String s, String sub) {
            int c = 0, i = 0;
            while ((i = s.indexOf(sub, i)) >= 0) { c++; i += sub.length(); }
            return c;
        }
    }

    /** One block (node). */
    public static class Node {
        public String title, code;
        public boolean isIf, deletable = true;
        public int color = -1;
        public final ArrayList<Field> fields = new ArrayList<>();
        public Node next, yes, no;
        public float x, y; // canvas position (only meaningful for stack heads)

        public boolean isRoot() { return !deletable; }

        /** True when the template uses the "false / else" placeholder. */
        public boolean hasElse() {
            if (!isIf) return false;
            return code != null && code.contains("%" + (fields.size() + 2) + "$s");
        }

        /** Deep copy of this node and everything connected after/inside it. */
        public Node copyChain() {
            Node n = copySingle();
            if (yes != null) n.yes = yes.copyChain();
            if (no != null) n.no = no.copyChain();
            if (next != null) n.next = next.copyChain();
            return n;
        }

        public Node copySingle() {
            Node n = new Node();
            n.title = title;
            n.code = code;
            n.isIf = isIf;
            n.deletable = true;
            n.color = color;
            for (Field f : fields) n.fields.add(f.copy());
            return n;
        }

        public Node tail() {
            Node t = this;
            while (t.next != null) t = t.next;
            return t;
        }

        public int countAll() {
            int c = 1;
            if (yes != null) c += yes.countAll();
            if (no != null) c += no.countAll();
            if (next != null) c += next.countAll();
            return c;
        }
    }

    /** Top-level stacks (the first one contains the root node). */
    public final ArrayList<Node> heads = new ArrayList<>();

    public Node root() {
        for (Node h : heads) {
            Node r = findRoot(h, new HashSet<>());
            if (r != null) return r;
        }
        return null;
    }

    private static Node findRoot(Node n, Set<Node> seen) {
        if (n == null || !seen.add(n)) return null;
        if (n.isRoot()) return n;
        Node r = findRoot(n.yes, seen);
        if (r == null) r = findRoot(n.no, seen);
        if (r == null) r = findRoot(n.next, seen);
        return r;
    }

    public static Node newRoot() {
        Node r = new Node();
        r.title = ROOT_TITLE;
        r.code = "%1$s";
        r.deletable = false;
        return r;
    }

    // ===================================================================== load

    public static BlockModel load(File file) {
        BlockModel m = new BlockModel();
        try {
            if (file.exists() && file.length() > 0) {
                String text = read(file);
                JsonElement root = JsonParser.parseString(text);
                if (root.isJsonArray() && root.getAsJsonArray().size() > 0) {
                    m.fromJson(root.getAsJsonArray());
                }
            }
        } catch (Exception e) {
            m.heads.clear();
        }
        if (m.root() == null) {
            Node r = newRoot();
            r.x = 40;
            r.y = 40;
            m.heads.add(0, r);
        }
        return m;
    }

    private void fromJson(JsonArray arr) {
        LinkedHashMap<String, Node> byId = new LinkedHashMap<>();
        ArrayList<JsonObject> objs = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            Node n = new Node();
            n.title = str(o, "title", "");
            if (!o.has("code")) throw new RuntimeException("Code Not Found!!");
            n.code = str(o, "code", "");
            n.deletable = str(o, "close", "true").equals("true");
            n.isIf = str(o, "else", "false").equals("true");
            try { n.color = o.get("color").getAsInt(); } catch (Exception ignored) {}
            n.x = num(str(o, "x", "0"));
            n.y = num(str(o, "y", "0"));
            try {
                if (o.has("nf")) {
                    JsonElement nf = o.get("nf");
                    JsonArray fa = nf.isJsonArray() ? nf.getAsJsonArray() : JsonParser.parseString(nf.getAsString()).getAsJsonArray();
                    for (JsonElement fe : fa) n.fields.add(fieldFrom(fe.getAsJsonObject()));
                }
            } catch (Exception ignored) {}
            byId.put(str(o, "id", String.valueOf(byId.size())), n);
            objs.add(o);
        }
        HashSet<Node> hasIncoming = new HashSet<>();
        int i = 0;
        for (Map.Entry<String, Node> en : byId.entrySet()) {
            JsonObject o = objs.get(i++);
            Node n = en.getValue();
            if (n.isIf) {
                n.yes = link(byId, str(o, "next", "null"));
                n.no = link(byId, str(o, "else_id", ""));
                n.next = link(byId, str(o, "next_id", ""));
            } else {
                n.next = link(byId, str(o, "next", "null"));
            }
            for (Node c : new Node[]{n.yes, n.no, n.next}) if (c != null) hasIncoming.add(c);
        }
        // Break cycles / shared children (each node may appear once in a tree)
        HashSet<Node> placed = new HashSet<>();
        // heads: root first, then nodes without incoming links, in file order
        Node root = null;
        for (Node n : byId.values()) if (!n.deletable) { root = n; break; }
        ArrayList<Node> order = new ArrayList<>();
        if (root != null) order.add(root);
        for (Node n : byId.values()) if (n != root && !hasIncoming.contains(n)) order.add(n);
        for (Node n : byId.values()) if (!order.contains(n)) order.add(n); // leftovers (cycles)
        for (Node h : order) {
            if (placed.contains(h)) continue;
            claim(h, placed);
            heads.add(h);
        }
        normalizePositions();
    }

    /** Mark the tree as placed and cut links to nodes already used elsewhere. */
    private static void claim(Node n, HashSet<Node> placed) {
        if (n == null) return;
        placed.add(n);
        if (n.yes != null) { if (placed.contains(n.yes)) n.yes = null; else claim(n.yes, placed); }
        if (n.no != null) { if (placed.contains(n.no)) n.no = null; else claim(n.no, placed); }
        if (n.next != null) { if (placed.contains(n.next)) n.next = null; else claim(n.next, placed); }
    }

    /** Files store LibGDX coordinates (y up); convert to canvas (y down) and spread overlapping heads. */
    private void normalizePositions() {
        if (heads.isEmpty()) return;
        float minX = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Node h : heads) {
            minX = Math.min(minX, h.x);
            maxY = Math.max(maxY, h.y);
        }
        for (Node h : heads) {
            h.x = (h.x - minX) * 0.6f + 40;
            h.y = (maxY - h.y) * 0.6f + 40;
        }
    }

    private static Node link(Map<String, Node> byId, String id) {
        if (id == null || id.isEmpty() || id.equals("null")) return null;
        return byId.get(id);
    }

    private static Field fieldFrom(JsonObject fo) {
        Field f = new Field(fo.has("name") ? fo.get("name").getAsString() : "", fo.has("value") ? fo.get("value").getAsString() : "");
        if (fo.has("block") && fo.get("block").isJsonObject()) {
            JsonObject b = fo.getAsJsonObject("block");
            Param p = new Param(b.get("template").getAsString(), b.get("displayTemplate").getAsString());
            if (b.has("innerFields")) {
                JsonArray inner = b.getAsJsonArray("innerFields");
                for (int i = 0; i < inner.size() && i < p.inner.size(); i++)
                    p.inner.set(i, fieldFrom(inner.get(i).getAsJsonObject()));
            }
            f.block = p;
        }
        return f;
    }

    // ===================================================================== save

    /** Ordered list of all nodes (heads in order, depth first). Root gets id 0. */
    private ArrayList<Node> allNodes() {
        ArrayList<Node> out = new ArrayList<>();
        Set<Node> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Node r = root();
        if (r != null) collect(r, out, seen);
        for (Node h : heads) collect(h, out, seen);
        return out;
    }

    private static void collect(Node n, ArrayList<Node> out, Set<Node> seen) {
        if (n == null || !seen.add(n)) return;
        out.add(n);
        collect(n.yes, out, seen);
        collect(n.no, out, seen);
        collect(n.next, out, seen);
    }

    public String toJson() {
        // assign canvas positions to every node so the LibGDX editor shows a readable layout
        HashMap<Node, float[]> pos = new HashMap<>();
        for (Node h : heads) placeForSave(h, h.x, h.y, pos, new HashSet<>());
        ArrayList<Node> nodes = allNodes();
        IdentityHashMap<Node, String> ids = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) ids.put(nodes.get(i), String.valueOf(i));
        Gson gson = new Gson();
        JsonArray arr = new JsonArray();
        for (Node n : nodes) {
            JsonObject o = new JsonObject();
            o.addProperty("id", ids.get(n));
            o.addProperty("title", n.title);
            float[] p = pos.get(n);
            float x = p == null ? n.x : p[0], y = p == null ? n.y : p[1];
            o.addProperty("x", String.valueOf(x / 0.6f));
            o.addProperty("y", String.valueOf(-y / 0.6f));
            o.addProperty("else", String.valueOf(n.isIf));
            if (n.isIf) {
                o.addProperty("next", n.yes != null ? ids.get(n.yes) : "null");
                o.addProperty("next_id", n.next != null ? ids.get(n.next) : "null");
                o.addProperty("else_id", n.no != null ? ids.get(n.no) : "");
            } else {
                o.addProperty("next", n.next != null ? ids.get(n.next) : "null");
                o.addProperty("next_id", "null");
                o.addProperty("else_id", "");
            }
            JsonArray fa = new JsonArray();
            for (Field f : n.fields) fa.add(fieldJson(f));
            o.addProperty("nf", gson.toJson(fa));
            o.addProperty("code", n.code);
            o.addProperty("color", n.color);
            o.addProperty("close", n.deletable ? "true" : "false");
            arr.add(o);
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr);
    }

    private static void placeForSave(Node n, float x, float y, HashMap<Node, float[]> pos, HashSet<Node> seen) {
        float cy = y;
        Node cur = n;
        while (cur != null && seen.add(cur)) {
            pos.put(cur, new float[]{x, cy});
            cy += 140;
            if (cur.yes != null) { placeForSave(cur.yes, x + 260, cy, pos, seen); cy += 140 * Math.min(4, cur.yes.countAll()); }
            if (cur.no != null) { placeForSave(cur.no, x + 260, cy, pos, seen); cy += 140 * Math.min(4, cur.no.countAll()); }
            cur = cur.next;
        }
    }

    private static JsonObject fieldJson(Field f) {
        JsonObject o = new JsonObject();
        o.addProperty("name", f.name);
        o.addProperty("value", f.value);
        if (f.block != null) {
            JsonObject b = new JsonObject();
            b.addProperty("template", f.block.template);
            b.addProperty("displayTemplate", f.block.display);
            JsonArray inner = new JsonArray();
            for (Field i : f.block.inner) inner.add(fieldJson(i));
            b.add("innerFields", inner);
            o.add("block", b);
        }
        return o;
    }

    // ===================================================================== code export (same as NodeSerializer)

    public String exportCode() {
        return exportCode(false);
    }

    /** Indexes of blocks whose code template could not be formatted in the last {@link #exportCode(boolean)}. */
    public final ArrayList<Integer> formatFailures = new ArrayList<>();

    /**
     * @param markers when true every block's code is preceded by a {@code /&#42;@b<index>&#42;/} comment, where
     *                index is the block's position in {@link #allNodes()}. Used to trace compiler errors back to blocks.
     */
    public String exportCode(boolean markers) {
        formatFailures.clear();
        Node entry = root();
        if (entry == null) {
            // fallback: first head (no incoming links by construction)
            entry = heads.isEmpty() ? null : heads.get(0);
        }
        IdentityHashMap<Node, Integer> ids = null;
        if (markers) {
            ids = new IdentityHashMap<>();
            ArrayList<Node> all = allNodes();
            for (int i = 0; i < all.size(); i++) ids.put(all.get(i), i);
        }
        StringBuilder sb = new StringBuilder();
        if (entry != null) gen(entry, sb, 0, java.util.Collections.newSetFromMap(new IdentityHashMap<>()), ids, formatFailures);
        return sb.toString();
    }

    /** The block with this index in {@link #allNodes()}, or null. */
    public Node nodeAt(int index) {
        ArrayList<Node> all = allNodes();
        return index >= 0 && index < all.size() ? all.get(index) : null;
    }

    private static void gen(Node node, StringBuilder code, int indent, Set<Node> visited, IdentityHashMap<Node, Integer> ids, ArrayList<Integer> failures) {
        if (node == null || visited.contains(node)) return;
        visited.add(node);
        StringBuilder ind = new StringBuilder();
        for (int i = 0; i < indent; i++) ind.append("    ");
        String indentStr = ind.toString();
        String template = node.code;
        if (template == null || template.isEmpty()) throw new RuntimeException("no code found!!");
        List<Object> args = new ArrayList<>();
        for (Field f : node.fields) args.add(f.code());
        String trueCode = "", falseCode = "", nextCode = "";
        if (node.yes != null) {
            StringBuilder b = new StringBuilder();
            gen(node.yes, b, indent + 1, visited, ids, failures);
            trueCode = b.toString();
        }
        if (node.no != null) {
            StringBuilder b = new StringBuilder();
            gen(node.no, b, indent + 1, visited, ids, failures);
            falseCode = b.toString();
        }
        if (node.next != null) {
            StringBuilder b = new StringBuilder();
            gen(node.next, b, indent, visited, ids, failures);
            nextCode = b.toString();
        }
        if (node.isIf) {
            args.add(trueCode);
            args.add(falseCode);
        }
        args.add(nextCode);
        String formatted = "";
        try {
            formatted = String.format(template, args.toArray());
        } catch (Exception ignored) {
            Integer id = ids == null ? null : ids.get(node);
            if (id != null) failures.add(id);
        }
        code.append(indentStr);
        if (ids != null) {
            Integer id = ids.get(node);
            if (id != null) code.append("/*@b").append(id).append("*/");
        }
        code.append(formatted.replace("\n", "\n" + indentStr));
    }

    /** Write the .visual and the sibling .java (like the old Save button). */
    public void save(File visualFile) throws Exception {
        write(visualFile, toJson());
        String p = visualFile.getAbsolutePath();
        File java = new File(p.substring(0, p.lastIndexOf('.')) + ".java");
        write(java, exportCode());
    }

    // ===================================================================== helpers

    static String str(JsonObject o, String k, String def) {
        try {
            JsonElement e = o.get(k);
            return e == null || e.isJsonNull() ? def : e.getAsString();
        } catch (Exception ex) {
            return def;
        }
    }

    static float num(String s) {
        try { return Float.parseFloat(s); } catch (Exception e) { return 0; }
    }

    public static String read(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, r;
            while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
            return new String(b, 0, off, StandardCharsets.UTF_8);
        }
    }

    public static void write(File f, String s) throws Exception {
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
