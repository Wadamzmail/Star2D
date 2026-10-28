package com.star4droid.star2d.prefab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The prefabs/ folder of a project: files, folders, naming. Prefab paths are relative to prefabs/,
 * use '/', have no extension ("enemies/Slime" = prefabs/enemies/Slime.prefab).
 */
public final class PrefabStore {
    private PrefabStore() {}

    public static final String EXT = ".prefab";
    public static final String DIR = "prefabs";
    /** Scenes made while a prefab is open in the editor. */
    public static final String TEMP_PREFIX = "__prefab_";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One row of a listing: a prefab or a folder. */
    public static final class Entry {
        public String path;   // relative, no extension
        public String name;   // last segment
        public boolean isFolder;
        Entry(String path, boolean folder) {
            this.path = path;
            this.isFolder = folder;
            int i = path.lastIndexOf('/');
            this.name = i < 0 ? path : path.substring(i + 1);
        }
    }

    /** Scenes whose name starts with "__" are internal and hidden from every scene list. */
    public static boolean isTempScene(String scene) {
        return scene != null && scene.startsWith("__");
    }

    public static String tempSceneFor(String prefabPath) {
        return TEMP_PREFIX + PrefabFiles.ident(normalize(prefabPath).replace('/', '_'));
    }

    // ---------------------------------------------------------------- public API (Project)

    public static File dir(Project p) { return dir(p.getPath()); }
    public static List<String> list(Project p) { return list(p.getPath()); }
    public static List<Entry> listTree(Project p) { return listTree(p.getPath()); }
    public static List<Entry> children(Project p, String folder) { return children(p.getPath(), folder); }
    public static File file(Project p, String path) { return file(p.getPath(), path); }
    public static boolean exists(Project p, String path) { return exists(p.getPath(), path); }
    public static PrefabModel load(Project p, String path) throws Exception { return load(p.getPath(), path); }
    public static void save(Project p, String path, PrefabModel m) throws Exception { save(p.getPath(), path, m); }
    public static String create(Project p, String path) throws Exception { return create(p.getPath(), path); }
    public static void createFolder(Project p, String path) throws Exception { createFolder(p.getPath(), path); }
    public static void delete(Project p, String path) throws Exception { delete(p.getPath(), path); }
    public static void rename(Project p, String from, String to) throws Exception { rename(p.getPath(), from, to); }
    public static String duplicate(Project p, String from, String to) throws Exception { return duplicate(p.getPath(), from, to); }

    // ---------------------------------------------------------------- implementation (project root path)

    /** Clean relative path: '/', no extension, no empty or dangerous segments. Throws IllegalArgumentException when unusable. */
    public static String normalize(String path) {
        if (path == null) throw new IllegalArgumentException("Empty prefab path");
        String s = path.trim().replace('\\', '/');
        if (s.endsWith(EXT)) s = s.substring(0, s.length() - EXT.length());
        StringBuilder out = new StringBuilder();
        for (String seg : s.split("/")) {
            String t = seg.trim();
            if (t.isEmpty() || t.equals(".")) continue;
            if (t.equals("..")) throw new IllegalArgumentException("'..' is not allowed in a prefab path");
            for (char c : t.toCharArray())
                if ("\\:*?\"<>|".indexOf(c) >= 0 || c < 32) throw new IllegalArgumentException("Character '" + c + "' is not allowed in a prefab name");
            if (out.length() > 0) out.append('/');
            out.append(t);
        }
        if (out.length() == 0) throw new IllegalArgumentException("Empty prefab path");
        return out.toString();
    }

    static File dir(String root) { return new File(root, DIR); }

    static File file(String root, String path) { return new File(dir(root), normalize(path) + EXT); }

    static boolean exists(String root, String path) {
        try {
            return file(root, path).isFile();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static List<String> list(String root) {
        ArrayList<String> out = new ArrayList<>();
        collect(dir(root), "", out, null);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static List<Entry> listTree(String root) {
        ArrayList<Entry> out = new ArrayList<>();
        walk(dir(root), "", out);
        return out;
    }

    private static void walk(File d, String rel, List<Entry> out) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        java.util.Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : fs) {
            if (f.isDirectory()) {
                out.add(new Entry(rel + f.getName(), true));
                walk(f, rel + f.getName() + "/", out);
            } else if (f.getName().endsWith(EXT)) {
                out.add(new Entry(rel + f.getName().substring(0, f.getName().length() - EXT.length()), false));
            }
        }
    }

    /** Direct children of a folder ("" = prefabs/ itself): folders first, then prefabs, each sorted. */
    static List<Entry> children(String root, String folder) {
        ArrayList<Entry> folders = new ArrayList<>(), files = new ArrayList<>();
        String rel = folder == null || folder.trim().isEmpty() ? "" : normalize(folder) + "/";
        File[] fs = new File(dir(root), rel).listFiles();
        if (fs != null) {
            java.util.Arrays.sort(fs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : fs) {
                if (f.isDirectory()) folders.add(new Entry(rel + f.getName(), true));
                else if (f.getName().endsWith(EXT)) files.add(new Entry(rel + f.getName().substring(0, f.getName().length() - EXT.length()), false));
            }
        }
        folders.addAll(files);
        return folders;
    }

    private static void collect(File d, String rel, List<String> out, Object unused) {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collect(f, rel + f.getName() + "/", out, null);
            else if (f.getName().endsWith(EXT)) out.add(rel + f.getName().substring(0, f.getName().length() - EXT.length()));
        }
    }

    static PrefabModel load(String root, String path) throws Exception {
        File f = file(root, path);
        String t = PrefabFiles.read(f);
        if (t == null) throw new IOException("No prefab named '" + normalize(path) + "'");
        PrefabModel m = GSON.fromJson(t, PrefabModel.class);
        if (m == null || m.nodes == null || m.nodes.isEmpty()) throw new IOException("Prefab '" + normalize(path) + "' is empty or damaged");
        // tolerate hand edits: the root is always first and has the pid "root"
        PrefabModel.Node r = m.byPid(PrefabModel.ROOT);
        if (r == null) {
            r = m.nodes.get(0);
            r.pid = PrefabModel.ROOT;
            r.parentPid = "";
        } else if (m.nodes.get(0) != r) {
            m.nodes.remove(r);
            m.nodes.add(0, r);
        }
        for (PrefabModel.Node n : m.nodes) {
            if (n.props == null) n.props = new JsonObject();
            if (n.events == null) n.events = new java.util.LinkedHashMap<>();
            if (n.parentPid == null) n.parentPid = n == r ? "" : PrefabModel.ROOT;
            if (n.name == null) n.name = n.pid;
        }
        return m;
    }

    static void save(String root, String path, PrefabModel m) throws Exception {
        PrefabFiles.write(file(root, path), GSON.toJson(m));
    }

    /** The default properties of a new, empty prefab root (a GROUP). */
    static JsonObject emptyRootProps() {
        JsonObject o = new JsonObject();
        o.addProperty("TYPE", "GROUP");
        o.addProperty("x", "0");
        o.addProperty("y", "0");
        o.addProperty("z", "0");
        o.addProperty("width", "100");
        o.addProperty("height", "100");
        o.addProperty("rotation", "0");
        o.addProperty("lock", "false");
        o.addProperty("Visible", "true");
        o.addProperty("Version", "1.0");
        return o;
    }

    static String create(String root, String path) throws Exception {
        String n = normalize(path);
        if (file(root, n).exists()) throw new IOException("A prefab named '" + n + "' already exists");
        PrefabModel m = new PrefabModel();
        PrefabModel.Node r = new PrefabModel.Node();
        r.pid = PrefabModel.ROOT;
        r.name = n.substring(n.lastIndexOf('/') + 1);
        r.props = emptyRootProps();
        m.nodes.add(r);
        save(root, n, m);
        return n;
    }

    static void createFolder(String root, String path) throws Exception {
        File d = new File(dir(root), normalize(path));
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Cannot create folder " + path);
    }

    /** Deletes a prefab file, or a folder with everything in it. Instances in scenes keep working as unlinked copies. */
    static void delete(String root, String path) throws Exception {
        String n = normalize(path);
        File f = file(root, n);
        if (f.isFile()) {
            if (!f.delete()) throw new IOException("Cannot delete " + n);
            return;
        }
        File d = new File(dir(root), n);
        if (d.isDirectory()) {
            PrefabFiles.deleteTree(d);
            return;
        }
        throw new IOException("No prefab or folder named '" + n + "'");
    }

    /** Rename or move a prefab (or a folder); instances in every scene are re-pointed. */
    static void rename(String root, String from, String to) throws Exception {
        String a = normalize(from), b = normalize(to);
        if (a.equals(b)) return;
        File fa = file(root, a), fb = file(root, b);
        File da = new File(dir(root), a), db = new File(dir(root), b);
        boolean isFile = fa.isFile();
        if (!isFile && !da.isDirectory()) throw new IOException("No prefab or folder named '" + a + "'");
        if (isFile ? fb.exists() : db.exists()) throw new IOException("'" + b + "' already exists");
        File target = isFile ? fb : db;
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
        if (!(isFile ? fa : da).renameTo(target)) throw new IOException("Cannot rename '" + a + "'");
        repoint(root, a, b, !isFile);
    }

    static String duplicate(String root, String from, String to) throws Exception {
        String a = normalize(from), b = normalize(to);
        PrefabModel m = load(root, a);
        if (file(root, b).exists()) throw new IOException("A prefab named '" + b + "' already exists");
        m.root().name = b.substring(b.lastIndexOf('/') + 1);
        save(root, b, m);
        return b;
    }

    /** Instances in all scenes: prefab path a becomes b (or a folder prefix). */
    private static void repoint(String root, String a, String b, boolean folder) {
        for (String sc : PrefabFiles.scenes(root)) {
            try {
                JsonArray arr = PrefabFiles.readScene(root, sc);
                boolean changed = false;
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    String pf = PrefabFiles.str(o, "prefab");
                    if (pf == null || pf.isEmpty()) continue;
                    if (!folder && pf.equals(a)) {
                        o.addProperty("prefab", b);
                        changed = true;
                    } else if (folder && pf.startsWith(a + "/")) {
                        o.addProperty("prefab", b + pf.substring(a.length()));
                        changed = true;
                    }
                }
                if (changed) PrefabFiles.writeScene(root, sc, arr);
            } catch (Exception ignored) {
            }
        }
    }
}
