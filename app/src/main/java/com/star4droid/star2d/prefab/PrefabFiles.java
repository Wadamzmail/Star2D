package com.star4droid.star2d.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain java.io helpers for prefab work: project layout, scene files, element events and scripts. No Android. */
final class PrefabFiles {
    private PrefabFiles() {}

    static final String[] EXT = {"java", "visual", "code"};

    // ---------------------------------------------------------------- layout (mirrors Helpers.editor.Project)

    static File scenesDir(String root) { return new File(root, "scenes"); }
    static File sceneFile(String root, String scene) { return new File(root, "scenes/" + scene); }
    static File configFile(String root, String scene) { return new File(root, "configs/" + scene + ".json"); }
    static File eventsDir(String root, String scene) { return new File(root, "Events/" + scene); }
    static File bodyEventsDir(String root, String scene, String body) { return new File(root, "Events/" + scene + "/private/" + body); }
    static File scriptFile(String root, String scene, String body) { return new File(root, "java/com/star4droid/Game/Scripts/" + scene + "/" + body + "Script.java"); }

    /** Everything a scene owns (see Project.getSceneList). */
    static List<File> sceneParts(String root, String scene) {
        ArrayList<File> l = new ArrayList<>();
        l.add(sceneFile(root, scene));
        l.add(configFile(root, scene));
        l.add(eventsDir(root, scene));
        l.add(new File(root, "joints/" + scene));
        l.add(new File(root, "java/com/star4droid/Game/" + scene.toLowerCase() + ".java"));
        l.add(new File(root, "scripts/" + scene));
        l.add(new File(root, "java/com/star4droid/Game/SceneScript/" + scene.toLowerCase() + "Script.java"));
        l.add(new File(root, "java/com/star4droid/Game/Scripts/" + scene));
        return l;
    }

    // ---------------------------------------------------------------- text IO

    static String read(File f) {
        try {
            if (!f.isFile()) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** Atomic: write a temp file next to the target, then move it over. */
    static void write(File f, String text) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("Cannot create " + dir);
        File tmp = new File(dir, f.getName() + ".tmp" + System.nanoTime());
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((s == null ? "" : s).hashCode());
        }
    }

    // ---------------------------------------------------------------- scenes

    /** The scene's elements as objects. Empty when the file is missing or empty. */
    static JsonArray readScene(String root, String scene) throws Exception {
        String t = read(sceneFile(root, scene));
        if (t == null || t.trim().isEmpty()) return new JsonArray();
        JsonElement e = JsonParser.parseString(t);
        if (!e.isJsonArray()) throw new IOException("Scene '" + scene + "' is not a list of elements");
        JsonArray out = new JsonArray();
        for (JsonElement x : e.getAsJsonArray()) if (x.isJsonObject()) out.add(x);
        return out;
    }

    static void writeScene(String root, String scene, JsonArray arr) throws Exception {
        write(sceneFile(root, scene), arr.size() == 0 ? "" : arr.toString());
    }

    /** All real scene names (no temp scenes). */
    static List<String> scenes(String root) {
        ArrayList<String> l = new ArrayList<>();
        File[] fs = scenesDir(root).listFiles();
        if (fs != null) for (File f : fs) if (f.isFile() && !PrefabStore.isTempScene(f.getName()) && !f.getName().contains(".tmp")) l.add(f.getName());
        java.util.Collections.sort(l, String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    // ---------------------------------------------------------------- property helpers

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) return null;
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    static String strOr(JsonObject o, String k, String def) {
        String s = str(o, k);
        return s == null ? def : s;
    }

    static double num(String s, double def) {
        if (s == null) return def;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e12) return String.valueOf((long) d);
        String s = String.valueOf((float) d);
        return s;
    }

    /** Equal as the editor sees it: same text, or the same number. Missing counts as "". */
    static boolean eq(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (a.equals(b)) return true;
        try {
            return Math.abs(Double.parseDouble(a) - Double.parseDouble(b)) < 1e-4;
        } catch (Exception e) {
            return false;
        }
    }

    /** Set a number keeping the type the file already used (JSON number or string). */
    static void setNum(JsonObject o, String k, double v) {
        JsonElement cur = o.get(k);
        if (cur != null && cur.isJsonPrimitive() && cur.getAsJsonPrimitive().isNumber()) {
            if (v == Math.rint(v) && Math.abs(v) < 1e12) o.addProperty(k, (long) v);
            else o.addProperty(k, (float) v);
        } else o.addProperty(k, fmt(v));
    }

    // ---------------------------------------------------------------- names

    static String ident(String s) {
        if (s == null) s = "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(Character.isLetterOrDigit(c) && c < 128 || c == '_' ? c : '_');
        String r = sb.toString();
        if (r.isEmpty()) r = "Prefab";
        if (Character.isDigit(r.charAt(0))) r = "_" + r;
        return r;
    }

    static String unique(String base, java.util.Set<String> taken) {
        String n = base;
        int i = 2;
        while (taken.contains(n)) n = base + (i++);
        return n;
    }

    // ---------------------------------------------------------------- events and scripts

    /** [scene, body] where the element's events live (Script prop: "name", "scene/name" or empty = own name). */
    static String[] bodyOf(JsonObject el, String scene) {
        String name = strOr(el, "name", "");
        String script = strOr(el, "Script", "");
        if (script.isEmpty()) script = name;
        if (script.contains("/")) {
            String[] v = script.split("/", 2);
            return new String[]{v[0], v[1]};
        }
        return new String[]{scene, script};
    }

    /** Does the element keep its code under its own name in its own scene? */
    static boolean ownsScript(JsonObject el, String scene) {
        String[] b = bodyOf(el, scene);
        return b[0].equals(scene) && b[1].equals(strOr(el, "name", ""));
    }

    /** event to (ext to text) for every event of a body that has any text. */
    static Map<String, Map<String, String>> readEvents(String root, String scene, String body) {
        LinkedHashMap<String, Map<String, String>> out = new LinkedHashMap<>();
        File[] fs = bodyEventsDir(root, scene, body).listFiles();
        if (fs == null) return out;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            if (!f.isFile()) continue;
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            if (dot <= 0) continue;
            String ext = n.substring(dot + 1), ev = n.substring(0, dot);
            if (!ext.equals("java") && !ext.equals("visual") && !ext.equals("code")) continue;
            String t = read(f);
            if (t == null || t.trim().isEmpty()) continue;
            Map<String, String> m = out.get(ev);
            if (m == null) out.put(ev, m = new LinkedHashMap<>());
            m.put(ext, t);
        }
        return out;
    }

    static String eventHash(Map<String, String> files) {
        if (files == null || files.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String e : EXT) sb.append(files.containsKey(e) ? files.get(e) : "").append('\u0000');
        return hash(sb.toString());
    }

    /** Remove every event file of the body. */
    static void clearEvents(String root, String scene, String body) {
        deleteTree(bodyEventsDir(root, scene, body));
    }

    static void clearEvent(String root, String scene, String body, String ev) {
        for (String e : EXT) new File(bodyEventsDir(root, scene, body), ev + "." + e).delete();
    }

    static void writeEvent(String root, String scene, String body, String ev, Map<String, String> files) throws IOException {
        clearEvent(root, scene, body, ev);
        if (files == null) return;
        for (Map.Entry<String, String> e : files.entrySet())
            if (e.getValue() != null && !e.getValue().isEmpty()) write(new File(bodyEventsDir(root, scene, body), ev + "." + e.getKey()), e.getValue());
    }

    // ---- body script (ItemScript java) with placeholders so it can move between names and scenes

    private static final String P_BODY = "__PBODY__";
    private static final String P_SCENE = "__PSCENE__";
    private static final String P_SCENE_LOWER = "__PSCENE_L__";
    private static final Pattern PACKAGE = Pattern.compile("(package\\s+com\\.star4droid\\.Game\\.Scripts\\.)([A-Za-z0-9_]+)(\\s*;)");

    static String readScript(String root, String scene, String body) {
        String t = read(scriptFile(root, scene, body));
        if (t == null || t.trim().isEmpty()) return null;
        Matcher m = PACKAGE.matcher(t);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String seg = m.group(2);
            String ph = seg.equals(scene) ? P_SCENE : seg.equals(scene.toLowerCase()) ? P_SCENE_LOWER : seg;
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + ph + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("\\b" + Pattern.quote(body) + "Script\\b", P_BODY + "Script");
    }

    static void writeScript(String root, String scene, String body, String normalized) throws IOException {
        File f = scriptFile(root, scene, body);
        if (normalized == null) {
            f.delete();
            return;
        }
        String t = normalized.replace(P_SCENE_LOWER, scene.toLowerCase()).replace(P_SCENE, scene).replace(P_BODY, body);
        write(f, t);
    }

    /** Copy everything one body owns to another place (events + script). */
    static void copyBody(String root, String fromScene, String fromBody, String toScene, String toBody) throws IOException {
        if (fromScene.equals(toScene) && fromBody.equals(toBody)) return;
        clearEvents(root, toScene, toBody);
        for (Map.Entry<String, Map<String, String>> e : readEvents(root, fromScene, fromBody).entrySet())
            writeEvent(root, toScene, toBody, e.getKey(), e.getValue());
        writeScript(root, toScene, toBody, readScript(root, fromScene, fromBody));
    }

    static void deleteBody(String root, String scene, String body) {
        deleteTree(bodyEventsDir(root, scene, body));
        scriptFile(root, scene, body).delete();
    }

    static TreeSet<String> names(JsonArray arr) {
        TreeSet<String> s = new TreeSet<>();
        for (JsonElement e : arr) {
            String n = str(e.getAsJsonObject(), "name");
            if (n != null) s.add(n);
        }
        return s;
    }
}
