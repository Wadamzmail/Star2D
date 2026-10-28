package com.star4droid.star2d.remote;

import android.content.Context;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.blocks.BlockCatalog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The documentation the app serves to the AI (read_docs) and writes into every project (.star2d/docs).
 * Topic files live in assets/ai/docs/&lt;id&gt;.md. Two placeholders are expanded from the real engine data:
 * {{ELEMENT_TABLES}} (assets/items/*.json, types.json, spinners.json) and {{BLOCK_CATALOG}} (BlockCatalog).
 */
public final class RemoteDocs {
    private RemoteDocs() {}

    private static final String DIR = "ai/docs";

    /** Preferred order of the topics in the index; topics not listed come after, alphabetically. */
    private static final String[] ORDER = {
            "index", "overview", "scenes", "elements", "physics", "events", "blocks", "blocks-examples", "blocks-catalog",
            "variables", "ui-input", "assets", "joints", "java-api", "recipes", "recipes-2", "testing", "pitfalls"};

    // ------------------------------------------------------------------ public API

    /** The index topic (golden rules + topic list). Topics that exist but are not mentioned in it are appended. */
    public static String index(Context c) {
        String text = read(c, "index");
        if (text == null) return "The documentation index is missing from this build of the app.";
        StringBuilder extra = new StringBuilder();
        for (String[] t : topics(c)) {
            if (t[0].equals("index")) continue;
            if (!text.contains("`" + t[0] + "`")) extra.append("- `").append(t[0]).append("`: ").append(t[1]).append("\n");
        }
        if (extra.length() > 0) text = text + "\n\n## More topics\n" + extra;
        return expand(c, text);
    }

    /** One topic with the placeholders expanded; an unknown id returns an error text listing the valid ids. */
    public static String topic(Context c, String id) {
        String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (key.endsWith(".md")) key = key.substring(0, key.length() - 3);
        if (key.isEmpty() || key.equals("index")) return index(c);
        String text = null;
        for (String[] t : topics(c)) if (t[0].equals(key)) text = read(c, key);
        if (text == null) {
            StringBuilder ids = new StringBuilder();
            for (String[] t : topics(c)) ids.append(ids.length() == 0 ? "" : ", ").append(t[0]);
            return "Unknown topic '" + id + "'. Valid topics: " + ids + ". Call read_docs without a topic for the index.";
        }
        return expand(c, text);
    }

    /** {id, title} of every topic, index first. The title is the first Markdown heading of the file. */
    public static List<String[]> topics(Context c) {
        ArrayList<String> ids = new ArrayList<>();
        try {
            String[] names = c.getAssets().list(DIR);
            if (names != null)
                for (String n : names) if (n.endsWith(".md")) ids.add(n.substring(0, n.length() - 3));
        } catch (Exception ignored) {
        }
        Collections.sort(ids);
        final List<String> order = Arrays.asList(ORDER);
        Collections.sort(ids, (a, b) -> {
            int ia = order.indexOf(a), ib = order.indexOf(b);
            if (ia < 0) ia = 1000;
            if (ib < 0) ib = 1000;
            return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
        });
        ArrayList<String[]> out = new ArrayList<>();
        for (String id : ids) out.add(new String[]{id, titleOf(read(c, id), id)});
        return out;
    }

    // ------------------------------------------------------------------ element tables

    private static final String[][] TYPES = {
            {"BOX", "box.json", "Sprite / rectangle with a Box2D body (the workhorse: player, platforms, enemies, bullets, coins)"},
            {"CIRCLE", "circle.json", "Round body: radius instead of width/height (balls, coins, round enemies)"},
            {"CUSTOM", "custom.json", "Body with a polygon outline from Points (hollow ChainShape loop: use STATIC for terrain)"},
            {"TEXT", "text.json", "A text label (HUD, score, titles). type UI = fixed on screen"},
            {"PROGRESS", "progress.json", "A progress bar (health, loading). type UI = fixed on screen"},
            {"JOYSTICK", "joystick.json", "Touch joystick (read it with the joystick reporters). type UI = fixed on screen"},
            {"PARTICLE", "particle.json", "libGDX particle effect from a .p file in files/ (empty file = default smoke)"},
            {"CAMERA", "camera.json", "CAMERA element: in this version its settings are not applied; control the camera with the Camera blocks"},
            {"MAP", "map.json", "Tiled map (.tmx under images/), drawn only: it has no collision"},
            {"LIGHT point", "Lights/point.json", "Point light (needs a dark Ambient Light in the scene config to be visible)"},
            {"LIGHT cone", "Lights/cone.json", "Cone light"},
            {"LIGHT directional", "Lights/directional.json", "Directional light"},
    };

    private static String tablesCache;

    /** One Markdown table per element type: name | kind | default | allowed values. */
    public static synchronized String elementTables(Context c) {
        if (tablesCache != null) return tablesCache;
        String t = elementTables(path -> readAsset(c, path));
        if (!t.startsWith("(element tables could not")) tablesCache = t;
        return t;
    }

    /** Source of asset files (so the generators can also run outside Android, e.g. in tests). */
    interface Assets {
        String read(String path) throws Exception;
    }

    static String elementTables(Assets a) {
        StringBuilder sb = new StringBuilder();
        try {
            String defaults = a.read("items/default.json");
            Map<String, String> kinds = kinds(a);
            JsonObject spinners = JsonParser.parseString(a.read("spinners.json")).getAsJsonObject();
            for (String[] t : TYPES) {
                String typeName = t[0].startsWith("LIGHT") ? "LIGHT" : t[0];
                String own = "";
                try {
                    own = a.read("items/" + t[1]).trim();
                } catch (Exception ignored) {
                }
                JsonObject o = JsonParser.parseString(defaults.replace("--other--", own.isEmpty() ? "" : "," + own)).getAsJsonObject();
                sb.append("### ").append(t[0]).append("\n").append(t[2]).append("\n\n");
                if (t[0].startsWith("LIGHT"))
                    sb.append("Add it with {\"op\":\"add\",\"type\":\"LIGHT\",\"props\":{\"Light Type\":\"").append(t[0].substring(6)).append("\"}}.\n\n");
                else
                    sb.append("Add it with {\"op\":\"add\",\"type\":\"").append(typeName).append("\",\"name\":\"...\",\"props\":{...}}.\n\n");
                sb.append("| name | kind | default | allowed values |\n|---|---|---|---|\n");
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    String k = e.getKey();
                    String v = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString();
                    if (v.equals("ItemName")) v = "<element name>";
                    String kind = kinds.get(k);
                    if (kind == null && k.equals("Map")) kind = "File";
                    boolean internal = kind == null;
                    if (internal) kind = "internal";
                    sb.append("| ").append(esc(k)).append(" | ").append(kind).append(" | ").append(v.isEmpty() ? "(empty)" : esc(v))
                            .append(" | ").append(internal ? "leave as is (must not be empty)" : allowed(k, kind, spinners)).append(" |\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            return "(element tables could not be generated: " + e + ")\n";
        }
        return sb.toString();
    }

    private static Map<String, String> kinds(Assets a) throws Exception {
        HashMap<String, String> m = new HashMap<>();
        JsonObject o = JsonParser.parseString(a.read("types.json")).getAsJsonObject();
        for (Map.Entry<String, JsonElement> e : o.entrySet())
            for (String f : e.getValue().getAsString().split(",")) if (!f.trim().isEmpty()) m.put(f.trim(), e.getKey());
        return m;
    }

    private static String allowed(String key, String kind, JsonObject spinners) {
        if (spinners.has(key)) return spinners.get(key).getAsString().replace(",", " \\| ");
        switch (kind) {
            case "boolean": return "true \\| false";
            case "color": return "#RRGGBB or #RRGGBBAA";
            case "float": return "number";
            case "image": return "/name.png (relative to images/)";
            case "File": return key.equals("font") ? "project path of a .s2df font" : key.equals("Map") ? "/level.tmx (a .tmx under images/)" : "/effect.p (a particle file under files/)";
            case "string": return key.equals("name") ? "letters, digits, _" : "text";
            case "body": return key.equals("Collision") ? "e.g. (a)(b): names of elements that do not collide" : "element name";
            case "script": return "name of the event script";
            case "points": return "read-only in the elements tool";
            case "static": return "read-only (fixed when the element is added)";
            default: return "";
        }
    }

    private static String esc(String s) {
        return s.replace("|", "/").replace("\n", " ");
    }

    // ------------------------------------------------------------------ block catalog

    /** Short notes for blocks that do not do what their name suggests (verified against the block templates). */
    private static final HashMap<String, String> BLOCK_NOTES = new HashMap<>();
    private static final HashMap<String, String> REPORTER_NOTES = new HashMap<>();

    static {
        BLOCK_NOTES.put("saveValue", "BROKEN: generates a method that does not exist, use saveData");
        BLOCK_NOTES.put("setsplits", "BROKEN: generates a method that does not exist, use setWorldGravity");
        BLOCK_NOTES.put("setAlpha", "NO-OP (comment only), use setOpacity");
        BLOCK_NOTES.put("moveTowardBody", "NO-OP (comment only), use moveToward");
        BLOCK_NOTES.put("void", "only for custom script files, not for events");
        BLOCK_NOTES.put("cancelTimer", "only works in the same event that created the timer");
        BLOCK_NOTES.put("_DoAfter", "like waitSeconds but the timer is named; locals used inside must be final");
        BLOCK_NOTES.put("_repeatEvery", "like everySeconds but the timer is named");
        BLOCK_NOTES.put("try", "do = try part, else = catch part");
        BLOCK_NOTES.put("setX", "world units (px*0.25); prefer goTo");
        BLOCK_NOTES.put("setY", "world units (px*0.25); prefer goTo");
        BLOCK_NOTES.put("setAngle", "radians; prefer rotateBy");
        BLOCK_NOTES.put("setTransform", "world units (px*0.25), angle in radians; prefer goTo");
        BLOCK_NOTES.put("setLinearVelocity", "raw Box2D world units; prefer setVelocityX / setVelocityY");
        BLOCK_NOTES.put("setGravityScale", "value is raw Java: write 0.5f or 0, not 0.5");
        BLOCK_NOTES.put("setGravity", "raw Java floats (-9.8f, 0f); prefer setWorldGravity");
        BLOCK_NOTES.put("setCameraXY", "x,y in WORLD units (px*0.25), not px");
        BLOCK_NOTES.put("setCameraX", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setCameraY", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setCameraCenter", "WORLD units (px*0.25)");
        BLOCK_NOTES.put("setZoom", "bigger = closer; the default view is 4");
        BLOCK_NOTES.put("cameraFollow", "centers the camera once, it does not follow by itself: put it in OnStep");
        BLOCK_NOTES.put("copyBody", "clone of the element; prefer spawnCopy");
        BLOCK_NOTES.put("setText", "text is a Java expression: quote literals, e.g. \"Score: \" + score");
        BLOCK_NOTES.put("showText", "text is a Java expression (default \"Hello\" has quotes)");
        BLOCK_NOTES.put("Toast", "message is a Java expression: quote literals");
        BLOCK_NOTES.put("debug", "message is a Java expression: quote literals");
        BLOCK_NOTES.put("setProgress", "value is cast to int");
        BLOCK_NOTES.put("setVisibility", "visibility: true | false");
        BLOCK_NOTES.put("setTint", "color without quotes, e.g. FF0000FF (RRGGBBAA)");
        BLOCK_NOTES.put("setWorldGravity", "also sets physics to 1 step per frame: speeds in px/s become true px/s. Call first in OnCreate");
        BLOCK_NOTES.put("OpenScene", "scene name without quotes");
        BLOCK_NOTES.put("destroyAfter", "destroying the same element twice can crash: prefer the destroyLater helper (topic blocks)");
        BLOCK_NOTES.put("destroy", "never inside a collision event, never twice on one element: prefer destroyLater (topic blocks)");
        BLOCK_NOTES.put("destroyLater", "helper from topic blocks: safe delayed destroy, handles final");
        BLOCK_NOTES.put("spawnCopy", "clone of the element at x,y px; var = Java variable name, newName = Java expression (quoted)");
        BLOCK_NOTES.put("ifCollision", "same test as the reporter ___ touches ___");
        BLOCK_NOTES.put("setBodyType", "DynamicBody | StaticBody | KinematicBody");
        BLOCK_NOTES.put("setSpringFrequencyHz", "WheelJoint only");
        BLOCK_NOTES.put("setSpringDampingRatio", "WheelJoint only");
        BLOCK_NOTES.put("setMotorSpeed", "joint = the joint's name; Revolute/Prismatic/Wheel; radians per second for hinges");
        BLOCK_NOTES.put("setMaxMotorTorque", "joint = the joint's name; Revolute/Wheel");
        BLOCK_NOTES.put("saveData", "persistent; read it back with the reporter saved number ___ / saved text ___");

        REPORTER_NOTES.put("x of ___", "center x in px");
        REPORTER_NOTES.put("y of ___", "center y in px, y up");
        REPORTER_NOTES.put("speed x of ___", "px per simulated second");
        REPORTER_NOTES.put("speed y of ___", "px per simulated second");
        REPORTER_NOTES.put("angle of ___", "degrees");
        REPORTER_NOTES.put("distance ___ to ___", "px");
        REPORTER_NOTES.put("angle ___ to ___", "radians");
        REPORTER_NOTES.put("touch x", "px");
        REPORTER_NOTES.put("touch y", "px");
        REPORTER_NOTES.put("camera x", "px");
        REPORTER_NOTES.put("camera y", "px");
        REPORTER_NOTES.put("frame time", "seconds");
        REPORTER_NOTES.put("___ .getX()", "actor x in WORLD units (px*0.25), bottom-left");
        REPORTER_NOTES.put("___ .getY()", "actor y in WORLD units (px*0.25), bottom-left");
        REPORTER_NOTES.put("width of ___", "actor width in WORLD units for world elements");
        REPORTER_NOTES.put("height of ___", "actor height in WORLD units for world elements");
        REPORTER_NOTES.put("angle (deg) ___", "points OPPOSITE to the stick direction, avoid; use joystick x / y");
        REPORTER_NOTES.put("power ___", "squared distance of the stick");
        REPORTER_NOTES.put("___ equals ___", "compares the two values as text");
        REPORTER_NOTES.put("random ___ to ___", "integer, both ends included");
        REPORTER_NOTES.put("saved number ___", "key is a plain string without quotes");
        REPORTER_NOTES.put("saved text ___", "key is a plain string without quotes");
        REPORTER_NOTES.put("___ is named ___", "second slot is a plain name without quotes");
        REPORTER_NOTES.put("\"___\"", "a text literal; the slot is the plain text");
    }

    /** All statement blocks per category (one compact line each) and the value blocks (reporters) by group. */
    public static String blockCatalog(Context c) {
        Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        BlockCatalog.load(app);
        return blockCatalogText();
    }

    /** The text for the blocks currently loaded in {@link BlockCatalog}. */
    static String blockCatalogText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Statement blocks. Line format: `title(field, field:Type) [container] - defaults`. ")
                .append("Use the field name before the colon as the key in \"fields\". ")
                .append("[container] blocks take \"do\" (and \"else\" when listed).\n\n");
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append("### ").append(e.getKey()).append("\n");
            for (BlockCatalog.Def d : e.getValue()) {
                sb.append("- `").append(d.title).append("(");
                StringBuilder defs = new StringBuilder();
                for (int i = 0; i < d.fields.length; i++) {
                    String f = d.fields[i];
                    int a = f.indexOf('(');
                    String label = a > 0 ? f.substring(0, a) : f;
                    String type = a > 0 && f.lastIndexOf(')') > a ? f.substring(a + 1, f.lastIndexOf(')')) : "";
                    if (i > 0) sb.append(", ");
                    sb.append(label);
                    if (!type.isEmpty()) sb.append(":").append(type);
                    String dv = d.defaults != null && i < d.defaults.length ? d.defaults[i] : "";
                    if (!dv.isEmpty()) defs.append(defs.length() == 0 ? "" : ", ").append(label).append("=").append(dv);
                }
                sb.append(")");
                if (d.isIf) {
                    boolean hasElse = false;
                    try {
                        hasElse = d.create().hasElse();
                    } catch (Throwable ignored) {
                    }
                    if (d.title.equals("while")) hasElse = false;
                    sb.append(hasElse ? " [container, else]" : " [container]");
                }
                sb.append("`");
                if (defs.length() > 0) sb.append(" - defaults ").append(defs);
                String note = BLOCK_NOTES.get(d.title);
                if (note != null) sb.append(" (").append(note).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }
        sb.append("## Value blocks (reporters)\n\n");
        sb.append("Use as a field value: {\"r\":\"<exact text>\",\"args\":[...]}. One arg per ___ in order; args may be strings or nested reporters. ")
                .append("Tags: [bool] yields a boolean, [text] a String, otherwise a number or object.\n\n");
        LinkedHashMap<String, ArrayList<BlockCatalog.Reporter>> groups = new LinkedHashMap<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
            ArrayList<BlockCatalog.Reporter> l = groups.get(r.group);
            if (l == null) {
                l = new ArrayList<>();
                groups.put(r.group, l);
            }
            l.add(r);
        }
        for (Map.Entry<String, ArrayList<BlockCatalog.Reporter>> g : groups.entrySet()) {
            sb.append("### ").append(g.getKey()).append("\n");
            for (BlockCatalog.Reporter r : g.getValue()) {
                sb.append("- `").append(r.display).append("`");
                if (BlockCatalog.isBooleanTemplate(r.template)) sb.append(" [bool]");
                else if (BlockCatalog.isTextTemplate(r.template)) sb.append(" [text]");
                String note = REPORTER_NOTES.get(r.display);
                if (note != null) sb.append(" - ").append(note);
                sb.append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static String expand(Context c, String text) {
        if (text.contains("{{ELEMENT_TABLES}}")) text = text.replace("{{ELEMENT_TABLES}}", elementTables(c));
        if (text.contains("{{BLOCK_CATALOG}}")) text = text.replace("{{BLOCK_CATALOG}}", blockCatalog(c));
        return text;
    }

    private static String titleOf(String text, String id) {
        if (text != null)
            for (String line : text.split("\n", 12)) {
                String t = line.trim();
                if (t.startsWith("#")) return t.replaceFirst("^#+\\s*", "");
            }
        return id;
    }

    /** Raw topic file, or null. */
    private static String read(Context c, String id) {
        try {
            return readAsset(c, DIR + "/" + id + ".md");
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAsset(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) != -1) bo.write(b, 0, r);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
