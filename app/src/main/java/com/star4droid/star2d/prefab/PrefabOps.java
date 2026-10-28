package com.star4droid.star2d.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.Helpers.editor.Project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prefab operations on scene files. Everything works on the files of the project (scenes/, Events/,
 * java/.../Scripts/, prefabs/); the editor is told through {@link PrefabEditorSync}.
 *
 * <p>Instance marking, on the elements of a scene:
 * <ul>
 * <li>{@code prefab} - only on the instance root: the prefab path ("enemies/Slime")</li>
 * <li>{@code pid} - on every element of the instance: its node in the prefab ("root", "n1"...)</li>
 * <li>{@code pbase} - JSON snapshot of the prefab node as of the last sync (the common ancestor of a 3-way merge)</li>
 * </ul>
 * An element's own value differing from its pbase is an <i>override</i>; sync keeps overrides and
 * updates everything else. Position, size and rotation are never overrides.
 */
public final class PrefabOps {
    private PrefabOps() {}

    public static final String K_PREFAB = "prefab", K_PID = "pid", K_BASE = "pbase";

    /** Never compared, never counted as an override (and never copied as a plain property). */
    static final Set<String> IGNORED = new HashSet<>(Arrays.asList(
            "x", "y", "z", "width", "height", "radius", "rotation", "name", "parent", "pid", "pbase", "prefab", "Script", "lock",
            "Collider Width", "Collider Height"));
    /** Follow the prefab only while the instance still has the prefab's value (but are not shown as overrides). */
    static final String[] GEOM = {"width", "height", "radius", "rotation", "Collider Width", "Collider Height"};
    /** Not stored as plain properties of a node. */
    private static final Set<String> NOT_STORED = new HashSet<>(Arrays.asList("name", "parent", "pid", "pbase", "prefab", "Script"));

    public static final class Result {
        public String root;
        public List<String> created = new ArrayList<>();
    }

    public static final class Info {
        public String prefabPath, rootName, pid;
        public boolean isRoot, missing;
        /** Overrides in the whole instance. */
        public int modifiedCount;
        /** Overrides ("name.prop", "name.event:onClick", "name.script", "+name" added, "-name" removed), at most 60. */
        public List<String> modified = new ArrayList<>();
        /** Only those of the element asked about. */
        public List<String> elementModified = new ArrayList<>();
    }

    // ================================================================ public API (Project)

    public static Result instantiate(Project p, String scene, String prefabPath, float x, float y, String wantedName) throws Exception {
        return instantiate(p.getPath(), scene, prefabPath, x, y, wantedName);
    }
    public static String extract(Project p, String scene, String elementName, String prefabPath) throws Exception {
        return extract(p.getPath(), scene, elementName, prefabPath);
    }
    public static void apply(Project p, String scene, String rootName) throws Exception { apply(p.getPath(), scene, rootName); }
    public static void revert(Project p, String scene, String rootName) throws Exception { revert(p.getPath(), scene, rootName); }
    public static void unpack(Project p, String scene, String rootName) throws Exception { unpack(p.getPath(), scene, rootName); }
    public static void link(Project p, String scene, String groupName, String prefabPath) throws Exception { link(p.getPath(), scene, groupName, prefabPath); }
    public static Info info(Project p, String scene, String elementName) throws Exception { return info(p.getPath(), scene, elementName); }
    /** Every instance root in the project: {prefabPath, scene, elementName}. */
    public static List<String[]> instances(Project p) { return instances(p.getPath()); }
    public static void syncAll(Project p, String prefabPath) throws Exception { syncAll(p.getPath(), prefabPath); }
    public static String beginEdit(Project p, String prefabPath) throws Exception { return beginEdit(p.getPath(), prefabPath); }
    public static void commitEdit(Project p, String tempScene, String prefabPath) throws Exception { commitEdit(p.getPath(), tempScene, prefabPath); }
    public static void discardEdit(Project p, String tempScene) throws Exception { discardEdit(p.getPath(), tempScene); }

    // ================================================================ document helpers

    private static JsonObject find(JsonArray arr, String name) {
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (name.equals(PrefabFiles.str(o, "name"))) return o;
        }
        return null;
    }

    private static String nm(JsonObject o) { return PrefabFiles.strOr(o, "name", ""); }
    private static String pidOf(JsonObject o) { return PrefabFiles.strOr(o, K_PID, ""); }

    private static Map<String, List<JsonObject>> kidsIndex(JsonArray arr) {
        HashMap<String, List<JsonObject>> m = new HashMap<>();
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            String par = PrefabFiles.strOr(o, "parent", "");
            if (par.isEmpty()) continue;
            List<JsonObject> l = m.get(par);
            if (l == null) m.put(par, l = new ArrayList<>());
            l.add(o);
        }
        return m;
    }

    /**
     * The element and everything below it (parent chains), the element first, then breadth first.
     * Instances of OTHER prefabs below it (elements carrying a prefab key) and what is under them are not part of it.
     */
    private static List<JsonObject> members(JsonArray arr, JsonObject rootEl) {
        return collect(arr, rootEl, true);
    }

    /** Everything below the element, nested instances included. */
    private static List<JsonObject> membersAll(JsonArray arr, JsonObject rootEl) {
        return collect(arr, rootEl, false);
    }

    private static List<JsonObject> collect(JsonArray arr, JsonObject rootEl, boolean skipNested) {
        ArrayList<JsonObject> out = new ArrayList<>();
        Map<String, List<JsonObject>> kids = kidsIndex(arr);
        Set<JsonObject> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        out.add(rootEl);
        seen.add(rootEl);
        for (int i = 0; i < out.size(); i++) {
            List<JsonObject> k = kids.get(nm(out.get(i)));
            if (k == null) continue;
            for (JsonObject c : k) {
                if (skipNested && !PrefabFiles.strOr(c, K_PREFAB, "").isEmpty()) continue;
                if (seen.add(c)) out.add(c);
            }
        }
        return out;
    }

    private static void remove(JsonArray arr, JsonObject el) {
        for (int i = 0; i < arr.size(); i++)
            if (arr.get(i) == el) {
                arr.remove(i);
                return;
            }
    }

    private static double maxZ(JsonArray arr) {
        double z = 0;
        boolean any = false;
        for (JsonElement e : arr) {
            double v = PrefabFiles.num(PrefabFiles.str(e.getAsJsonObject(), "z"), Double.NaN);
            if (!Double.isNaN(v) && (!any || v > z)) {
                z = v;
                any = true;
            }
        }
        return any ? z : -1;
    }

    private static String baseName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    // ================================================================ base snapshot (pbase)

    /** What a prefab node looked like at the last sync. */
    static final class Base {
        final LinkedHashMap<String, String> p = new LinkedHashMap<>(), g = new LinkedHashMap<>(), e = new LinkedHashMap<>();
        String s = "", bp = "";

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            JsonObject jp = new JsonObject(), jg = new JsonObject(), je = new JsonObject();
            for (Map.Entry<String, String> x : p.entrySet()) jp.addProperty(x.getKey(), x.getValue());
            for (Map.Entry<String, String> x : g.entrySet()) jg.addProperty(x.getKey(), x.getValue());
            for (Map.Entry<String, String> x : e.entrySet()) je.addProperty(x.getKey(), x.getValue());
            o.add("p", jp);
            o.add("g", jg);
            o.add("e", je);
            o.addProperty("s", s);
            o.addProperty("bp", bp);
            return o;
        }
    }

    private static Base baseOf(PrefabModel.Node n) {
        Base b = new Base();
        for (Map.Entry<String, JsonElement> e : n.props.entrySet()) {
            String k = e.getKey();
            if (IGNORED.contains(k)) {
                if (k.equals("x") || k.equals("y") || Arrays.asList(GEOM).contains(k)) b.g.put(k, valueOf(e.getValue()));
                continue;
            }
            b.p.put(k, valueOf(e.getValue()));
        }
        for (Map.Entry<String, Map<String, String>> e : n.events.entrySet()) {
            String h = PrefabFiles.eventHash(e.getValue());
            if (!h.isEmpty()) b.e.put(e.getKey(), h);
        }
        b.s = n.script == null ? "" : PrefabFiles.hash(n.script);
        b.bp = n.parentPid == null ? "" : n.parentPid;
        return b;
    }

    private static String valueOf(JsonElement e) {
        return e == null || e.isJsonNull() ? "" : e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static Base readBase(JsonObject el) {
        String t = PrefabFiles.str(el, K_BASE);
        if (t == null || t.isEmpty()) return null;
        try {
            JsonObject o = JsonParser.parseString(t).getAsJsonObject();
            Base b = new Base();
            fill(b.p, o.getAsJsonObject("p"));
            fill(b.g, o.getAsJsonObject("g"));
            fill(b.e, o.getAsJsonObject("e"));
            if (o.has("s")) b.s = o.get("s").getAsString();
            if (o.has("bp")) b.bp = o.get("bp").getAsString();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static void fill(Map<String, String> m, JsonObject o) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), valueOf(e.getValue()));
    }

    // ================================================================ capture (instance or edit scene -> model)

    /**
     * Builds the prefab model from the subtree below {@code rootEl}. Elements get pids (written into the
     * elements) and take their scripts into their own name (Script = name) so a prefab never points to
     * another element's or scene's script. The scene array is modified, the caller writes it.
     */
    private static PrefabModel capture(String root, String scene, JsonArray arr, JsonObject rootEl, PrefabModel old) throws IOException {
        List<JsonObject> mem = members(arr, rootEl);
        // pids: keep existing ones, new ones continue after the highest in use
        Set<String> used = new HashSet<>();
        int max = 0;
        List<String> sources = new ArrayList<>();
        for (JsonObject m : mem) sources.add(pidOf(m));
        if (old != null) for (PrefabModel.Node n : old.nodes) sources.add(n.pid);
        for (String s : sources) {
            if (s != null && s.length() > 1 && s.charAt(0) == 'n') {
                try {
                    max = Math.max(max, Integer.parseInt(s.substring(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Map<JsonObject, String> pidMap = new java.util.IdentityHashMap<>();
        pidMap.put(rootEl, PrefabModel.ROOT);
        used.add(PrefabModel.ROOT);
        for (JsonObject m : mem) {
            if (m == rootEl) continue;
            String pid = pidOf(m);
            if (pid.isEmpty() || pid.equals(PrefabModel.ROOT) || !used.add(pid)) {
                do {
                    pid = "n" + (++max);
                } while (!used.add(pid));
            }
            pidMap.put(m, pid);
        }
        String rootName = nm(rootEl);
        double rx = PrefabFiles.num(PrefabFiles.str(rootEl, "x"), 0), ry = PrefabFiles.num(PrefabFiles.str(rootEl, "y"), 0), rz = PrefabFiles.num(PrefabFiles.str(rootEl, "z"), 0);
        Map<String, String> pidByName = new HashMap<>();
        for (JsonObject m : mem) pidByName.put(nm(m), pidMap.get(m));

        PrefabModel model = new PrefabModel();
        for (JsonObject m : mem) {
            PrefabModel.Node n = new PrefabModel.Node();
            n.pid = pidMap.get(m);
            boolean isRoot = m == rootEl;
            n.parentPid = isRoot ? "" : pidByName.getOrDefault(PrefabFiles.strOr(m, "parent", ""), PrefabModel.ROOT);
            String full = nm(m);
            n.name = isRoot ? full : (full.startsWith(rootName + "_") && full.length() > rootName.length() + 1 ? full.substring(rootName.length() + 1) : full);
            for (Map.Entry<String, JsonElement> e : m.entrySet()) {
                if (NOT_STORED.contains(e.getKey())) continue;
                n.props.add(e.getKey(), e.getValue().deepCopy());
            }
            if (isRoot) {
                n.props.addProperty("x", "0");
                n.props.addProperty("y", "0");
                n.props.addProperty("z", "0");
            } else {
                n.props.addProperty("x", PrefabFiles.fmt(PrefabFiles.num(PrefabFiles.str(m, "x"), 0) - rx));
                n.props.addProperty("y", PrefabFiles.fmt(PrefabFiles.num(PrefabFiles.str(m, "y"), 0) - ry));
                n.props.addProperty("z", PrefabFiles.fmt(PrefabFiles.num(PrefabFiles.str(m, "z"), 0) - rz));
            }
            // code: from wherever the element's Script points, kept under the element's own name afterwards
            String[] b = PrefabFiles.bodyOf(m, scene);
            String own = full;
            Map<String, Map<String, String>> ev = PrefabFiles.readEvents(root, b[0], b[1]);
            String script = PrefabFiles.readScript(root, b[0], b[1]);
            n.events.putAll(ev);
            n.script = script;
            if (!(b[0].equals(scene) && b[1].equals(own))) {
                PrefabFiles.clearEvents(root, scene, own);
                for (Map.Entry<String, Map<String, String>> e : ev.entrySet()) PrefabFiles.writeEvent(root, scene, own, e.getKey(), e.getValue());
                PrefabFiles.writeScript(root, scene, own, script);
                m.addProperty("Script", own);
            }
            model.nodes.add(n);
        }
        return model;
    }

    /** Puts pid and pbase on every member and the prefab key on the root. */
    private static void markInstance(List<JsonObject> mem, JsonObject rootEl, PrefabModel model, String path, Map<String, String> pidsByName) {
        // pids were written to a model only; re-read from the model by element name order
        for (int i = 0; i < mem.size(); i++) {
            JsonObject m = mem.get(i);
            PrefabModel.Node n = model.nodes.get(i);
            m.addProperty(K_PID, n.pid);
            m.addProperty(K_BASE, baseOf(n).toJson().toString());
            if (m == rootEl) m.addProperty(K_PREFAB, path);
            else m.remove(K_PREFAB);
        }
    }

    // ================================================================ sync (model -> instance, 3-way)

    private static final class Report {
        boolean changed;
        final List<String> created = new ArrayList<>();
    }

    /** Update one existing element from its prefab node. */
    private static void update(String root, String scene, JsonArray arr, JsonObject el, PrefabModel.Node node, boolean isRoot, double rx, double ry, boolean force, Report rep) throws IOException {
        Base b = readBase(el);
        boolean known = b != null;

        // plain properties
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String k : node.props.keySet()) keys.add(k);
        if (known) keys.addAll(b.p.keySet());
        for (String k : keys) {
            if (IGNORED.contains(k)) continue;
            String cur = PrefabFiles.str(el, k);
            String nv = valueOf(node.props.get(k));
            boolean has = node.props.has(k);
            boolean unmodified = force || (known && PrefabFiles.eq(cur, b.p.get(k)));
            if (!unmodified) continue;
            if (!has) {
                if (el.has(k) && !k.equals("TYPE")) el.remove(k);
            } else if (!PrefabFiles.eq(cur, nv) || !el.has(k)) {
                el.add(k, node.props.get(k).deepCopy());
            }
        }

        // geometry follows the prefab while the instance still has the prefab's value; revert leaves it alone
        if (!force && known) {
            for (String k : GEOM) {
                if (isRoot && k.equals("rotation")) continue;
                if (!node.props.has(k)) continue;
                if (PrefabFiles.eq(PrefabFiles.str(el, k), b.g.get(k))) {
                    if (!PrefabFiles.eq(PrefabFiles.str(el, k), valueOf(node.props.get(k)))) el.add(k, node.props.get(k).deepCopy());
                }
            }
            if (!isRoot) {
                for (String k : new String[]{"x", "y"}) {
                    if (!node.props.has(k)) continue;
                    double base = k.equals("x") ? rx : ry;
                    double curOff = PrefabFiles.num(PrefabFiles.str(el, k), 0) - base;
                    if (PrefabFiles.eq(PrefabFiles.fmt(curOff), b.g.get(k))) {
                        double nv = PrefabFiles.num(valueOf(node.props.get(k)), 0);
                        PrefabFiles.setNum(el, k, base + nv);
                    }
                }
            }
        }

        // structure: the prefab moved this node under another one
        // (done by the caller, it needs the pid -> element map)

        // code, only when the element keeps it under its own name
        String name = nm(el);
        if (PrefabFiles.ownsScript(el, scene)) {
            Map<String, Map<String, String>> cur = PrefabFiles.readEvents(root, scene, name);
            LinkedHashSet<String> evs = new LinkedHashSet<>(node.events.keySet());
            if (known) evs.addAll(b.e.keySet());
            evs.addAll(cur.keySet());
            for (String ev : evs) {
                String ch = PrefabFiles.eventHash(cur.get(ev));
                String bh = known ? (b.e.containsKey(ev) ? b.e.get(ev) : "") : null;
                boolean unmodified = force || (known && ch.equals(bh));
                if (!unmodified) continue;
                Map<String, String> nf = node.events.get(ev);
                if (!ch.equals(PrefabFiles.eventHash(nf))) PrefabFiles.writeEvent(root, scene, name, ev, nf);
            }
            String cs = PrefabFiles.readScript(root, scene, name);
            String ch = cs == null ? "" : PrefabFiles.hash(cs);
            boolean unmodified = force || (known && ch.equals(b.s));
            String ns = node.script == null ? "" : PrefabFiles.hash(node.script);
            if (unmodified && !ch.equals(ns)) PrefabFiles.writeScript(root, scene, name, node.script);
        }
        el.addProperty(K_PID, node.pid);
        el.addProperty(K_BASE, baseOf(node).toJson().toString());
    }

    private static JsonObject create(String root, String scene, JsonArray arr, PrefabModel.Node node, String rootName, JsonObject parentEl, double rx, double ry, double rz, Report rep) throws IOException {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, JsonElement> e : node.props.entrySet()) o.add(e.getKey(), e.getValue().deepCopy());
        String name = PrefabFiles.unique(PrefabFiles.ident(rootName + "_" + node.name), PrefabFiles.names(arr));
        o.addProperty("name", name);
        o.addProperty("parent", nm(parentEl));
        o.addProperty(K_PID, node.pid);
        o.addProperty("x", PrefabFiles.fmt(rx + PrefabFiles.num(valueOf(node.props.get("x")), 0)));
        o.addProperty("y", PrefabFiles.fmt(ry + PrefabFiles.num(valueOf(node.props.get("y")), 0)));
        o.addProperty("z", PrefabFiles.fmt(rz + PrefabFiles.num(valueOf(node.props.get("z")), 0)));
        o.addProperty("Script", name);
        PrefabFiles.deleteBody(root, scene, name); // leftovers of an old element with this name
        for (Map.Entry<String, Map<String, String>> e : node.events.entrySet()) PrefabFiles.writeEvent(root, scene, name, e.getKey(), e.getValue());
        PrefabFiles.writeScript(root, scene, name, node.script);
        o.addProperty(K_BASE, baseOf(node).toJson().toString());
        arr.add(o);
        rep.created.add(name);
        rep.changed = true;
        return o;
    }

    /**
     * Brings one instance up to date with its prefab. {@code force}: instance := prefab (overrides lost,
     * code replaced); otherwise overrides are kept.
     */
    private static void sync(String root, String scene, JsonArray arr, JsonObject rootEl, PrefabModel model, boolean force, Report rep) throws IOException {
        String before = arr.toString();
        List<JsonObject> mem = members(arr, rootEl);
        Map<String, JsonObject> byPid = new HashMap<>();
        for (JsonObject m : mem) {
            String pid = pidOf(m);
            if (!pid.isEmpty() && !byPid.containsKey(pid)) byPid.put(pid, m);
        }
        if (pidOf(rootEl).isEmpty()) rootEl.addProperty(K_PID, PrefabModel.ROOT);
        byPid.put(PrefabModel.ROOT, rootEl);
        String rootName = nm(rootEl);
        double rx = PrefabFiles.num(PrefabFiles.str(rootEl, "x"), 0), ry = PrefabFiles.num(PrefabFiles.str(rootEl, "y"), 0), rz = PrefabFiles.num(PrefabFiles.str(rootEl, "z"), 0);

        List<PrefabModel.Node> pending = new ArrayList<>(model.nodes);
        Set<String> inModel = new HashSet<>();
        for (PrefabModel.Node n : model.nodes) inModel.add(n.pid);
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (Iterator<PrefabModel.Node> it = pending.iterator(); it.hasNext(); ) {
                PrefabModel.Node n = it.next();
                boolean isRoot = PrefabModel.ROOT.equals(n.pid);
                JsonObject el = byPid.get(n.pid);
                JsonObject parentEl = null;
                if (!isRoot) {
                    parentEl = byPid.get(n.parentPid == null || n.parentPid.isEmpty() ? PrefabModel.ROOT : n.parentPid);
                    if (parentEl == null) {
                        if (inModel.contains(n.parentPid)) continue; // its parent is created first
                        parentEl = rootEl;
                    }
                }
                if (el == null) {
                    el = create(root, scene, arr, n, rootName, parentEl, rx, ry, rz, rep);
                    byPid.put(n.pid, el);
                } else {
                    Base b = readBase(el);
                    update(root, scene, arr, el, n, isRoot, rx, ry, force, rep);
                    if (!isRoot && parentEl != null && b != null) {
                        // the prefab moved this node under another node
                        String newParentPid = n.parentPid == null || n.parentPid.isEmpty() ? PrefabModel.ROOT : n.parentPid;
                        String oldParentPid = b.bp.isEmpty() ? PrefabModel.ROOT : b.bp;
                        if (!newParentPid.equals(oldParentPid) && !nm(parentEl).equals(PrefabFiles.str(el, "parent")) && !isUnder(arr, parentEl, el))
                            el.addProperty("parent", nm(parentEl));
                    }
                }
                it.remove();
                progress = true;
            }
        }
        // nodes that were removed from the prefab
        for (JsonObject m : new ArrayList<>(mem)) {
            String pid = pidOf(m);
            if (m == rootEl || pid.isEmpty() || inModel.contains(pid)) continue;
            deleteElement(root, scene, arr, m);
        }
        rep.changed |= !before.equals(arr.toString());
    }

    private static boolean isUnder(JsonArray arr, JsonObject maybeChild, JsonObject ancestor) {
        Set<String> seen = new HashSet<>();
        JsonObject cur = maybeChild;
        while (cur != null && seen.add(nm(cur))) {
            if (cur == ancestor) return true;
            cur = find(arr, PrefabFiles.strOr(cur, "parent", ""));
        }
        return false;
    }

    /** Remove an element; its children move up to its parent. Its own code is deleted. */
    private static void deleteElement(String root, String scene, JsonArray arr, JsonObject el) {
        String name = nm(el), parent = PrefabFiles.strOr(el, "parent", "");
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (o != el && name.equals(PrefabFiles.str(o, "parent"))) o.addProperty("parent", parent);
        }
        if (PrefabFiles.ownsScript(el, scene)) PrefabFiles.deleteBody(root, scene, name);
        remove(arr, el);
    }

    // ================================================================ operations

    static Result instantiate(String root, String scene, String prefabPath, float x, float y, String wantedName) throws Exception {
        return instantiateInto(root, scene, PrefabFiles.readScene(root, scene), prefabPath, x, y, wantedName, "", null, true);
    }

    private static Result instantiateInto(String root, String scene, JsonArray arr, String prefabPath, double x, double y, String wantedName, String parent, Double z, boolean write) throws Exception {
        String path = PrefabStore.normalize(prefabPath);
        PrefabModel model = PrefabStore.load(root, path);
        PrefabModel.Node rn = model.root();
        String name = PrefabFiles.unique(PrefabFiles.ident(wantedName == null || wantedName.trim().isEmpty() ? baseName(path) : wantedName.trim()), PrefabFiles.names(arr));
        JsonObject el = new JsonObject();
        for (Map.Entry<String, JsonElement> e : rn.props.entrySet()) el.add(e.getKey(), e.getValue().deepCopy());
        el.addProperty("name", name);
        el.addProperty("parent", parent == null ? "" : parent);
        el.addProperty("x", PrefabFiles.fmt(x));
        el.addProperty("y", PrefabFiles.fmt(y));
        el.addProperty("z", PrefabFiles.fmt(z != null ? z : maxZ(arr) + 1));
        el.addProperty("Script", name);
        el.addProperty(K_PID, PrefabModel.ROOT);
        el.addProperty(K_PREFAB, path);
        PrefabFiles.deleteBody(root, scene, name);
        arr.add(el);
        Report rep = new Report();
        sync(root, scene, arr, el, model, true, rep); // brings the root's code and creates all children
        Result r = new Result();
        r.root = name;
        r.created.add(name);
        r.created.addAll(rep.created);
        if (write) PrefabFiles.writeScene(root, scene, arr);
        return r;
    }

    static String extract(String root, String scene, String elementName, String prefabPath) throws Exception {
        String path = PrefabStore.normalize(prefabPath);
        if (PrefabStore.exists(root, path)) throw new IOException("A prefab named '" + path + "' already exists");
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject el = find(arr, elementName);
        if (el == null) throw new IOException("No element named '" + elementName + "' in scene '" + scene + "'");
        for (JsonObject m : membersAll(arr, el))
            if (!pidOf(m).isEmpty() || !PrefabFiles.strOr(m, K_PREFAB, "").isEmpty())
                throw new IOException("'" + nm(m) + "' already belongs to a prefab instance. Unpack it first.");
        JsonObject rootEl = el;
        if (!"GROUP".equalsIgnoreCase(PrefabFiles.strOr(el, "TYPE", ""))) {
            // wrap it in a new group at the element's place
            JsonObject g = new JsonObject();
            for (Map.Entry<String, JsonElement> e : PrefabStore.emptyRootProps().entrySet()) g.add(e.getKey(), e.getValue());
            String gname = PrefabFiles.unique(PrefabFiles.ident(baseName(path)), PrefabFiles.names(arr));
            g.addProperty("name", gname);
            g.addProperty("parent", PrefabFiles.strOr(el, "parent", ""));
            g.addProperty("x", PrefabFiles.strOr(el, "x", "0"));
            g.addProperty("y", PrefabFiles.strOr(el, "y", "0"));
            g.addProperty("z", PrefabFiles.strOr(el, "z", "0"));
            g.addProperty("width", PrefabFiles.strOr(el, "width", "100"));
            g.addProperty("height", PrefabFiles.strOr(el, "height", "100"));
            g.addProperty("Script", gname);
            el.addProperty("parent", gname);
            // the group goes right before the element so the draw order stays
            JsonArray out = new JsonArray();
            for (JsonElement e : arr) {
                if (e.getAsJsonObject() == el) out.add(g);
                out.add(e);
            }
            arr = out;
            rootEl = g;
        }
        PrefabModel model = capture(root, scene, arr, rootEl, null);
        PrefabStore.save(root, path, model);
        markInstance(members(arr, rootEl), rootEl, model, path, null);
        PrefabFiles.writeScene(root, scene, arr);
        return nm(rootEl);
    }

    static void apply(String root, String scene, String rootName) throws Exception {
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject rootEl = find(arr, rootName);
        if (rootEl == null) throw new IOException("No element named '" + rootName + "' in scene '" + scene + "'");
        String path = PrefabFiles.strOr(rootEl, K_PREFAB, "");
        if (path.isEmpty()) throw new IOException("'" + rootName + "' is not the root of a prefab instance");
        PrefabModel old = PrefabStore.exists(root, path) ? PrefabStore.load(root, path) : null;
        PrefabModel model = capture(root, scene, arr, rootEl, old);
        PrefabStore.save(root, path, model);
        markInstance(members(arr, rootEl), rootEl, model, path, null);
        PrefabFiles.writeScene(root, scene, arr);
        syncAll(root, path);
    }

    static void revert(String root, String scene, String rootName) throws Exception {
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject rootEl = find(arr, rootName);
        if (rootEl == null) throw new IOException("No element named '" + rootName + "' in scene '" + scene + "'");
        String path = PrefabFiles.strOr(rootEl, K_PREFAB, "");
        if (path.isEmpty()) throw new IOException("'" + rootName + "' is not the root of a prefab instance");
        PrefabModel model = PrefabStore.load(root, path);
        // local additions go
        for (JsonObject m : members(arr, rootEl)) if (m != rootEl && pidOf(m).isEmpty()) deleteElement(root, scene, arr, m);
        sync(root, scene, arr, rootEl, model, true, new Report());
        PrefabFiles.writeScene(root, scene, arr);
    }

    static void unpack(String root, String scene, String rootName) throws Exception {
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject rootEl = find(arr, rootName);
        if (rootEl == null) throw new IOException("No element named '" + rootName + "' in scene '" + scene + "'");
        for (JsonObject m : members(arr, rootEl)) {
            m.remove(K_PREFAB);
            m.remove(K_PID);
            m.remove(K_BASE);
        }
        PrefabFiles.writeScene(root, scene, arr);
    }

    static void link(String root, String scene, String groupName, String prefabPath) throws Exception {
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject g = find(arr, groupName);
        if (g == null) throw new IOException("No element named '" + groupName + "' in scene '" + scene + "'");
        if (!pidOf(g).isEmpty()) throw new IOException("'" + groupName + "' is already part of a prefab instance. Unpack it first.");
        PrefabStore.normalize(prefabPath);
        if (!PrefabStore.exists(root, prefabPath)) throw new IOException("No prefab named '" + prefabPath + "'");
        double x = PrefabFiles.num(PrefabFiles.str(g, "x"), 0), y = PrefabFiles.num(PrefabFiles.str(g, "y"), 0), z = PrefabFiles.num(PrefabFiles.str(g, "z"), 0);
        String parent = PrefabFiles.strOr(g, "parent", "");
        List<JsonObject> mem = membersAll(arr, g);
        // a copy of the array in case the prefab turns out to be unreadable: nothing is deleted before it loads
        PrefabStore.load(root, prefabPath);
        for (int i = mem.size() - 1; i >= 0; i--) {
            JsonObject m = mem.get(i);
            if (PrefabFiles.ownsScript(m, scene)) PrefabFiles.deleteBody(root, scene, nm(m));
            remove(arr, m);
        }
        instantiateInto(root, scene, arr, prefabPath, x, y, groupName, parent, z, true);
    }

    static void syncAll(String root, String prefabPath) throws Exception {
        String path = PrefabStore.normalize(prefabPath);
        if (!PrefabStore.exists(root, path)) return;
        PrefabModel model = PrefabStore.load(root, path);
        for (String sc : PrefabFiles.scenes(root)) {
            JsonArray arr;
            try {
                arr = PrefabFiles.readScene(root, sc);
            } catch (Exception e) {
                continue;
            }
            boolean changed = false;
            List<JsonObject> roots = new ArrayList<>();
            for (JsonElement e : arr) if (path.equals(PrefabFiles.str(e.getAsJsonObject(), K_PREFAB))) roots.add(e.getAsJsonObject());
            for (JsonObject r : roots) {
                Report rep = new Report();
                sync(root, sc, arr, r, model, false, rep);
                changed |= rep.changed;
            }
            if (changed) PrefabFiles.writeScene(root, sc, arr);
        }
    }

    static List<String[]> instances(String root) {
        ArrayList<String[]> out = new ArrayList<>();
        for (String sc : PrefabFiles.scenes(root)) {
            try {
                for (JsonElement e : PrefabFiles.readScene(root, sc)) {
                    String pf = PrefabFiles.str(e.getAsJsonObject(), K_PREFAB);
                    if (pf != null && !pf.isEmpty()) out.add(new String[]{pf, sc, nm(e.getAsJsonObject())});
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    static Info info(String root, String scene, String elementName) throws Exception {
        JsonArray arr = PrefabFiles.readScene(root, scene);
        JsonObject el = find(arr, elementName);
        if (el == null) return null;
        // the instance root is the nearest ancestor (or the element itself) carrying the prefab key
        JsonObject rootEl = null, cur = el;
        Set<String> seen = new HashSet<>();
        while (cur != null && seen.add(nm(cur))) {
            if (!PrefabFiles.strOr(cur, K_PREFAB, "").isEmpty()) {
                rootEl = cur;
                break;
            }
            cur = find(arr, PrefabFiles.strOr(cur, "parent", ""));
        }
        if (rootEl == null) return null;
        Info in = new Info();
        in.prefabPath = PrefabFiles.str(rootEl, K_PREFAB);
        in.rootName = nm(rootEl);
        in.pid = pidOf(el);
        in.isRoot = el == rootEl;
        PrefabModel model = PrefabStore.exists(root, in.prefabPath) ? PrefabStore.load(root, in.prefabPath) : null;
        in.missing = model == null;
        Set<String> present = new HashSet<>();
        for (JsonObject m : members(arr, rootEl)) {
            String mn = nm(m), pid = pidOf(m);
            List<String> mine = new ArrayList<>();
            if (pid.isEmpty()) {
                mine.add("+" + mn);
            } else {
                present.add(pid);
                Base b = readBase(m);
                if (b != null) {
                    LinkedHashSet<String> keys = new LinkedHashSet<>(b.p.keySet());
                    for (String k : m.keySet()) keys.add(k);
                    for (String k : keys) {
                        if (IGNORED.contains(k) || NOT_STORED.contains(k)) continue;
                        if (!PrefabFiles.eq(PrefabFiles.str(m, k), b.p.get(k))) mine.add(mn + "." + k);
                    }
                    if (PrefabFiles.ownsScript(m, scene)) {
                        Map<String, Map<String, String>> ev = PrefabFiles.readEvents(root, scene, mn);
                        LinkedHashSet<String> evs = new LinkedHashSet<>(b.e.keySet());
                        evs.addAll(ev.keySet());
                        for (String e : evs)
                            if (!PrefabFiles.eventHash(ev.get(e)).equals(b.e.containsKey(e) ? b.e.get(e) : "")) mine.add(mn + ".event:" + e);
                        String cs = PrefabFiles.readScript(root, scene, mn);
                        if (!(cs == null ? "" : PrefabFiles.hash(cs)).equals(b.s)) mine.add(mn + ".script");
                    }
                }
            }
            in.modifiedCount += mine.size();
            for (String s : mine) if (in.modified.size() < 60) in.modified.add(s);
            if (m == el) in.elementModified.addAll(mine);
        }
        if (model != null)
            for (PrefabModel.Node n : model.nodes)
                if (!present.contains(n.pid)) {
                    in.modifiedCount++;
                    if (in.modified.size() < 60) in.modified.add("-" + n.name);
                }
        return in;
    }

    // ================================================================ editing a prefab in a temporary scene

    static String beginEdit(String root, String prefabPath) throws Exception {
        String path = PrefabStore.normalize(prefabPath);
        PrefabModel model = PrefabStore.load(root, path);
        String tmp = PrefabStore.tempSceneFor(path);
        for (File f : PrefabFiles.sceneParts(root, tmp)) PrefabFiles.deleteTree(f);

        // scene settings: those of an existing scene (same size and background), else defaults
        double lw = 640, lh = 360;
        String cfg = "{\"sceneColor\":\"#263238\",\"Ambient Light\":\"#FFFFFF\",\"or\":\"portrait\"}";
        for (String sc : PrefabFiles.scenes(root)) {
            String c = PrefabFiles.read(PrefabFiles.configFile(root, sc));
            if (c == null || c.trim().isEmpty()) continue;
            try {
                JsonObject o = JsonParser.parseString(c).getAsJsonObject();
                cfg = c;
                lw = PrefabFiles.num(PrefabFiles.str(o, "logicWidth"), lw);
                lh = PrefabFiles.num(PrefabFiles.str(o, "logicHeight"), lh);
                break;
            } catch (Exception ignored) {
            }
        }
        PrefabFiles.write(PrefabFiles.configFile(root, tmp), cfg);

        JsonArray arr = new JsonArray();
        Map<String, JsonObject> byPid = new HashMap<>();
        PrefabModel.Node rn = model.root();
        double rw = PrefabFiles.num(valueOf(rn.props.get("width")), 100), rh = PrefabFiles.num(valueOf(rn.props.get("height")), 100);
        double cx = lw / 2 - rw / 2, cy = lh / 2 - rh / 2;
        Set<String> taken = new HashSet<>();
        List<PrefabModel.Node> order = new ArrayList<>(model.nodes);
        for (PrefabModel.Node n : order) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, JsonElement> e : n.props.entrySet()) o.add(e.getKey(), e.getValue().deepCopy());
            boolean isRoot = n == rn;
            String name = PrefabFiles.unique(PrefabFiles.ident(isRoot ? baseName(path) : n.name), taken);
            taken.add(name);
            o.addProperty("name", name);
            o.addProperty("parent", "");
            o.addProperty("x", PrefabFiles.fmt(cx + (isRoot ? 0 : PrefabFiles.num(valueOf(n.props.get("x")), 0))));
            o.addProperty("y", PrefabFiles.fmt(cy + (isRoot ? 0 : PrefabFiles.num(valueOf(n.props.get("y")), 0))));
            o.addProperty("z", PrefabFiles.fmt(isRoot ? 0 : PrefabFiles.num(valueOf(n.props.get("z")), 0)));
            o.addProperty("Script", name);
            o.addProperty(K_PID, n.pid);
            PrefabFiles.clearEvents(root, tmp, name);
            for (Map.Entry<String, Map<String, String>> e : n.events.entrySet()) PrefabFiles.writeEvent(root, tmp, name, e.getKey(), e.getValue());
            PrefabFiles.writeScript(root, tmp, name, n.script);
            byPid.put(n.pid, o);
            arr.add(o);
        }
        for (PrefabModel.Node n : order) {
            if (n == rn) continue;
            JsonObject par = byPid.get(n.parentPid == null || n.parentPid.isEmpty() ? PrefabModel.ROOT : n.parentPid);
            if (par == null) par = byPid.get(PrefabModel.ROOT);
            byPid.get(n.pid).addProperty("parent", nm(par));
        }
        PrefabFiles.writeScene(root, tmp, arr);
        return tmp;
    }

    static void commitEdit(String root, String tempScene, String prefabPath) throws Exception {
        if (!PrefabStore.isTempScene(tempScene)) throw new IOException("'" + tempScene + "' is not a prefab editing scene");
        String path = PrefabStore.normalize(prefabPath);
        JsonArray arr = PrefabFiles.readScene(root, tempScene);
        JsonObject rootEl = null;
        for (JsonElement e : arr) if (PrefabModel.ROOT.equals(pidOf(e.getAsJsonObject()))) rootEl = e.getAsJsonObject();
        if (rootEl == null) throw new IOException("The root of the prefab is missing; the prefab was not saved");
        // everything must live under the root: loose elements are adopted by it
        Set<JsonObject> under = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        under.addAll(members(arr, rootEl));
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if (!under.contains(o)) o.addProperty("parent", nm(rootEl));
        }
        rootEl.addProperty("parent", "");
        // an element that sits in itself (cycle) cannot be reached from the root: members() above covered the rest
        PrefabModel old = PrefabStore.exists(root, path) ? PrefabStore.load(root, path) : null;
        PrefabModel model = capture(root, tempScene, arr, rootEl, old);
        PrefabStore.save(root, path, model);
        discardEdit(root, tempScene);
        syncAll(root, path);
    }

    static void discardEdit(String root, String tempScene) throws Exception {
        if (!PrefabStore.isTempScene(tempScene)) throw new IOException("'" + tempScene + "' is not a prefab editing scene");
        for (File f : PrefabFiles.sceneParts(root, tempScene)) PrefabFiles.deleteTree(f);
    }
}
