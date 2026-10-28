package com.star4droid.star2d.remote;

import android.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.ui.Bridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** File tools: files (batch), create_image, create_sound, import_asset. Worker thread (file IO), GL only to refresh. */
final class FileTools {
    private FileTools() {}

    private static final int MAX_READ = 60_000;
    private static final long MAX_WRITE = 2_000_000;
    private static final long MAX_DOWNLOAD = 25_000_000;

    private static Project project() throws Exception {
        return Gl.run(Proj::project);
    }

    /** Folders whose change must reach the open editor. */
    private static boolean touchesScene(String rel) {
        String r = rel.replace('\\', '/');
        return r.startsWith("scenes/") || r.startsWith("configs/") || r.startsWith("joints/") || r.startsWith("Events/") && r.endsWith("variables.json");
    }

    private static boolean touchesAssets(String rel) {
        String r = rel.replace('\\', '/');
        return r.startsWith("images/") || r.startsWith("sounds/") || r.startsWith("anims/") || r.startsWith("files/");
    }

    private static void guard(Project p, File f) throws Exception {
        File root = new File(p.getPath()).getCanonicalFile();
        String rel = Proj.rel(p, f);
        if (f.getCanonicalPath().equals(root.getPath())) throw new Gl.ToolError("The project root itself cannot be changed");
        if (rel.equals(".git") || rel.startsWith(".git/")) throw new Gl.ToolError("The .git folder is managed by the git tool");
    }

    /** Snapshot taken before changing files, applied after. */
    private static HashMap<String, String> before() throws Exception {
        return Gl.run(() -> {
            // unsaved edits of the open scene must be on disk before a reload
            if (Bridge.ready() && !Bridge.editor().isLoading()) Bridge.editor().getProject().save(Bridge.editor());
            return Bridge.app().openSceneFingerprints();
        });
    }

    private static String after(HashMap<String, String> before) throws Exception {
        return Gl.run(() -> Bridge.app().applyExternalChanges(before));
    }

    static ToolResult files(JsonObject a) throws Exception {
        final Project p = project();
        final JsonArray ops = EditTools.opsOf(a);
        boolean mutating = false;
        for (JsonElement e : ops) {
            String op = CoreTools.s(e.getAsJsonObject(), "op");
            if (op.equals("write") || op.equals("delete") || op.equals("move") || op.equals("copy") || op.equals("mkdir")) mutating = true;
        }
        HashMap<String, String> snap = mutating ? before() : null;
        JsonArray results = new JsonArray();
        boolean reload = false;
        for (JsonElement e : ops) {
            JsonObject o = e.getAsJsonObject();
            String op = CoreTools.s(o, "op"), path = CoreTools.s(o, "path");
            JsonObject r = new JsonObject();
            r.addProperty("op", op);
            r.addProperty("path", path);
            try {
                File f = Proj.safe(p, path.isEmpty() ? "." : path);
                switch (op) {
                    case "list": {
                        if (!f.isDirectory()) throw new Gl.ToolError("not a folder: " + path);
                        boolean rec = CoreTools.b(o, "recursive", false);
                        JsonArray ents = new JsonArray();
                        list(p, f, rec, ents, 400);
                        r.add("entries", ents);
                        break;
                    }
                    case "read": {
                        if (!f.isFile()) throw new Gl.ToolError("file not found: " + path);
                        long len = f.length();
                        if (looksBinary(f)) {
                            r.addProperty("binary", true);
                            r.addProperty("size", len);
                            break;
                        }
                        String txt = new String(readBytes(f, MAX_READ * 4L), StandardCharsets.UTF_8);
                        int off = Math.max(0, CoreTools.i(o, "offset", 0));
                        if (off > txt.length()) off = txt.length();
                        String part = txt.substring(off, Math.min(txt.length(), off + MAX_READ));
                        r.addProperty("content", part);
                        if (off + MAX_READ < txt.length() || len > MAX_READ * 4L) {
                            r.addProperty("truncated", true);
                            r.addProperty("next_offset", off + part.length());
                        }
                        break;
                    }
                    case "write": {
                        guard(p, f);
                        String content = CoreTools.s(o, "content");
                        if (content.length() > MAX_WRITE) throw new Gl.ToolError("content too large (max 2 MB); split it");
                        File parent = f.getParentFile();
                        if (parent != null) parent.mkdirs();
                        try (OutputStream out = new FileOutputStream(f)) {
                            out.write(content.getBytes(StandardCharsets.UTF_8));
                        }
                        r.addProperty("ok", true);
                        reload |= touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, f));
                        break;
                    }
                    case "mkdir":
                        guard(p, f);
                        f.mkdirs();
                        r.addProperty("ok", f.isDirectory());
                        break;
                    case "delete": {
                        guard(p, f);
                        if (!f.exists()) throw new Gl.ToolError("not found: " + path);
                        reload |= touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, f));
                        deleteRec(f);
                        r.addProperty("ok", true);
                        break;
                    }
                    case "move":
                    case "copy": {
                        String to = CoreTools.s(o, "to");
                        File t = Proj.safe(p, to);
                        guard(p, f);
                        guard(p, t);
                        if (!f.exists()) throw new Gl.ToolError("not found: " + path);
                        if (t.exists() && !CoreTools.b(o, "overwrite", true)) throw new Gl.ToolError("target exists: " + to);
                        copyRec(f, t);
                        if (op.equals("move")) deleteRec(f);
                        reload |= touchesScene(Proj.rel(p, t)) || touchesScene(Proj.rel(p, f)) || touchesAssets(Proj.rel(p, t)) || touchesAssets(Proj.rel(p, f));
                        r.addProperty("ok", true);
                        break;
                    }
                    default:
                        throw new Gl.ToolError("unknown op '" + op + "' (list|read|write|delete|move|copy|mkdir)");
                }
            } catch (Gl.ToolError t) {
                r.addProperty("error", t.getMessage());
            } catch (Exception t) {
                r.addProperty("error", String.valueOf(t));
            }
            results.add(r);
        }
        if (snap != null && reload) {
            String rep = after(snap);
            JsonObject r = new JsonObject();
            r.addProperty("editor", rep.isEmpty() ? "refreshed" : rep);
            results.add(r);
        }
        return ToolResult.text(results.toString());
    }

    private static void list(Project p, File dir, boolean rec, JsonArray out, int max) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        Arrays.sort(fs, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        for (File f : fs) {
            if (out.size() >= max) return;
            if (f.getName().equals(".git")) continue;
            JsonObject e = new JsonObject();
            e.addProperty("path", Proj.rel(p, f) + (f.isDirectory() ? "/" : ""));
            if (f.isFile()) e.addProperty("size", f.length());
            out.add(e);
            if (rec && f.isDirectory()) list(p, f, true, out, max);
        }
    }

    private static boolean looksBinary(File f) throws Exception {
        String n = f.getName().toLowerCase();
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".gif", ".wav", ".mp3", ".ogg", ".dex", ".jar", ".zip", ".ttf", ".otf", ".atlas.png"})
            if (n.endsWith(ext)) return true;
        byte[] head = readBytes(f, 512);
        for (byte b : head) if (b == 0) return true;
        return false;
    }

    static byte[] readBytes(File f, long max) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0 && bo.size() < max) bo.write(buf, 0, r);
            return bo.toByteArray();
        }
    }

    static void deleteRec(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) deleteRec(x);
        f.delete();
    }

    private static void copyRec(File from, File to) throws Exception {
        if (from.isDirectory()) {
            to.mkdirs();
            File[] c = from.listFiles();
            if (c != null) for (File x : c) copyRec(x, new File(to, x.getName()));
        } else {
            File parent = to.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
        }
    }

    // ================================================================== assets

    private static File assetTarget(Project p, String path, String... folders) throws Exception {
        File f = Proj.safe(p, path);
        guard(p, f);
        String rel = Proj.rel(p, f);
        boolean ok = false;
        for (String d : folders) if (rel.startsWith(d + "/")) ok = true;
        if (!ok) throw new Gl.ToolError("path must be inside " + Arrays.toString(folders) + " (e.g. " + folders[0] + "/name)");
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        return f;
    }

    private static String refreshAssets() throws Exception {
        return Gl.run(() -> Bridge.app().applyExternalChanges(Bridge.app().openSceneFingerprints()));
    }

    static ToolResult createImage(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        if (!path.toLowerCase().endsWith(".png")) path = path + ".png";
        File f = assetTarget(p, path, "images");
        byte[] png = ImageGen.png(a);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(png);
        }
        refreshAssets();
        JsonObject o = new JsonObject();
        o.addProperty("path", Proj.rel(p, f));
        o.addProperty("use_as", "/" + Proj.rel(p, f).substring("images/".length()));
        o.addProperty("size_bytes", png.length);
        o.addProperty("note", "Set an element's image property to the 'use_as' value.");
        return ToolResult.text(o.toString());
    }

    static ToolResult createSound(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        if (!path.toLowerCase().endsWith(".wav")) path = path + ".wav";
        File f = assetTarget(p, path, "sounds");
        byte[] wav = SoundGen.wav(a);
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(wav);
        }
        refreshAssets();
        JsonObject o = new JsonObject();
        o.addProperty("path", Proj.rel(p, f));
        o.addProperty("use_as", "/" + Proj.rel(p, f).substring("sounds/".length()));
        o.addProperty("size_bytes", wav.length);
        return ToolResult.text(o.toString());
    }

    private static final HashMap<String, HashMap<Integer, byte[]>> CHUNKS = new HashMap<>();
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).followRedirects(true).build();

    static ToolResult importAsset(JsonObject a) throws Exception {
        Project p = project();
        String path = CoreTools.s(a, "path");
        File f = assetTarget(p, path, "images", "sounds", "anims", "files", "scripts", "java");
        byte[] data;
        if (!CoreTools.s(a, "url").isEmpty()) {
            String url = CoreTools.s(a, "url");
            if (!url.startsWith("https://")) throw new Gl.ToolError("only https URLs are allowed");
            try (Response r = HTTP.newCall(new Request.Builder().url(url).build()).execute()) {
                if (!r.isSuccessful() || r.body() == null) throw new Gl.ToolError("download failed: HTTP " + r.code());
                long len = r.body().contentLength();
                if (len > MAX_DOWNLOAD) throw new Gl.ToolError("file too large (max 25 MB)");
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                try (InputStream in = r.body().byteStream()) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        bo.write(buf, 0, n);
                        if (bo.size() > MAX_DOWNLOAD) throw new Gl.ToolError("file too large (max 25 MB)");
                    }
                }
                data = bo.toByteArray();
            }
        } else if (!CoreTools.s(a, "base64").isEmpty()) {
            byte[] part = Base64.decode(CoreTools.s(a, "base64"), Base64.DEFAULT);
            int chunks = CoreTools.i(a, "chunks", 1), chunk = CoreTools.i(a, "chunk", 0);
            if (chunks <= 1) data = part;
            else {
                synchronized (CHUNKS) {
                    HashMap<Integer, byte[]> m = CHUNKS.get(f.getAbsolutePath());
                    if (m == null || chunk == 0) {
                        m = new HashMap<>();
                        CHUNKS.put(f.getAbsolutePath(), m);
                    }
                    m.put(chunk, part);
                    if (m.size() < chunks) return ToolResult.text("chunk " + chunk + " stored (" + m.size() + "/" + chunks + "). Send the rest.");
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    for (int i = 0; i < chunks; i++) {
                        byte[] b = m.get(i);
                        if (b == null) throw new Gl.ToolError("chunk " + i + " is missing");
                        bo.write(b);
                    }
                    CHUNKS.remove(f.getAbsolutePath());
                    data = bo.toByteArray();
                }
            }
        } else throw new Gl.ToolError("give url or base64");
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
        refreshAssets();
        return ToolResult.text("saved " + Proj.rel(p, f) + " (" + data.length + " bytes)");
    }
}
