package com.star4droid.star2d.ui.blocks;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * All blocks shown in the palette: the original catalog (assets/java/nodes.java), the user's
 * custom nodes (Nodes/nodes.json), and the extra built-in blocks below. Reporter blocks
 * (values inside fields) come from the old parameter dialog, Nodes/blocks.json and extras.
 */
public final class BlockCatalog {
    private BlockCatalog() {}

    /** A statement block template. */
    public static class Def {
        public String category, title, code;
        public String[] fields;
        public String[] defaults;
        public boolean isIf;
        public int color;

        public BlockModel.Node create() {
            BlockModel.Node n = new BlockModel.Node();
            n.title = title;
            n.code = code;
            n.isIf = isIf;
            n.color = -1;
            for (int i = 0; i < fields.length; i++)
                n.fields.add(new BlockModel.Field(fields[i], defaults != null && i < defaults.length ? defaults[i] : ""));
            return n;
        }
    }

    /** A reporter (value) block template. */
    public static class Reporter {
        public String group, display, template;
        Reporter(String g, String d, String t) { group = g; display = d; template = t; }
    }

    public static final LinkedHashMap<String, ArrayList<Def>> CATEGORIES = new LinkedHashMap<>();
    public static final ArrayList<Reporter> REPORTERS = new ArrayList<>();
    private static final HashMap<String, Integer> CATEGORY_COLOR = new HashMap<>();
    private static boolean loaded = false;

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        CATEGORIES.clear();
        REPORTERS.clear();
        try (InputStream in = c.getAssets().open("java/nodes.java")) {
            byte[] b = readAll(in);
            parseDsl(new String(b, StandardCharsets.UTF_8), null);
        } catch (Exception ignored) {}
        addExtras();
        // user custom nodes / blocks (same files as the LibGDX node library)
        File base = c.getExternalFilesDir(null);
        if (base != null) {
            loadCustomNodes(new File(base, "Nodes/nodes.json"));
            loadCustomBlocks(new File(base, "Nodes/blocks.json"));
        }
        addReporters();
    }

    /** Reload user libraries (after editing them). */
    public static synchronized void reload(Context c) {
        loaded = false;
        load(c);
    }

    public static int colorOf(String category) {
        Integer col = CATEGORY_COLOR.get(category);
        if (col != null) return col;
        return 0xFF607D8B;
    }

    /** Category of a block title (for colors of loaded nodes). */
    public static String categoryOfTitle(String title) {
        for (java.util.Map.Entry<String, ArrayList<Def>> e : CATEGORIES.entrySet())
            for (Def d : e.getValue()) if (d.title.equals(title)) return e.getKey();
        return null;
    }

    public static Def find(String title) {
        for (ArrayList<Def> l : CATEGORIES.values()) for (Def d : l) if (d.title.equals(title)) return d;
        return null;
    }

    // ------------------------------------------------------------------ original DSL

    private static void parseDsl(String text, String forcedCategory) {
        String category = forcedCategory == null ? "main" : forcedCategory;
        String[] chunks = text.split("(?m)^\\s*split\\s*$");
        for (String chunk : chunks) {
            String[] lines = chunk.split("\n", -1);
            int color = 0;
            boolean hasColor = false;
            int i = 0;
            // leading lines: category markers and color
            for (; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("--")) { category = l.substring(2).trim(); continue; }
                if (l.startsWith("-color:")) {
                    try {
                        String hex = l.substring(l.indexOf('#') + 1, l.indexOf('•'));
                        color = 0xFF000000 | Integer.parseInt(hex, 16);
                        hasColor = true;
                    } catch (Exception ignored) {}
                    continue;
                }
                break;
            }
            if (!hasColor || i >= lines.length) continue;
            String header = lines[i].trim();
            int sep = -1;
            for (int k = i + 1; k < lines.length; k++) if (lines[k].trim().equals("<<=>>")) { sep = k; break; }
            if (sep < 0 || header.isEmpty()) continue;
            StringBuilder code = new StringBuilder();
            for (int k = sep + 1; k < lines.length; k++) {
                code.append(lines[k]);
                if (k < lines.length - 1) code.append("\n");
            }
            String codeStr = code.toString();
            // strip trailing empty line produced by the split
            while (codeStr.endsWith("\n")) codeStr = codeStr.substring(0, codeStr.length() - 1);
            String[] tokens = header.split("\\s+");
            Def d = new Def();
            d.category = category;
            d.isIf = tokens[0].contains("__star__if__");
            d.title = tokens[0].replace("__star__if__", "");
            d.fields = new String[tokens.length - 1];
            System.arraycopy(tokens, 1, d.fields, 0, d.fields.length);
            d.code = codeStr;
            d.color = color;
            add(d);
        }
    }

    private static void add(Def d) {
        ArrayList<Def> l = CATEGORIES.get(d.category);
        if (l == null) {
            l = new ArrayList<>();
            CATEGORIES.put(d.category, l);
            CATEGORY_COLOR.put(d.category, d.color != 0 ? d.color : 0xFF607D8B);
        }
        // a later definition with the same title replaces the earlier one (no duplicates in palette)
        for (int i = 0; i < l.size(); i++) if (l.get(i).title.equals(d.title)) { l.set(i, d); return; }
        l.add(d);
    }

    private static void def(String cat, int color, String title, String fields, String defaults, boolean isIf, String code) {
        Def d = new Def();
        d.category = cat;
        d.color = color;
        d.title = title;
        d.fields = fields.isEmpty() ? new String[0] : fields.split(" ");
        d.defaults = defaults == null || defaults.isEmpty() ? null : defaults.split("\\|", -1);
        d.isIf = isIf;
        d.code = code;
        add(d);
    }

    // ------------------------------------------------------------------ extra built-in blocks

    private static void addExtras() {
        int ctrl = 0xFFE6A100, vars = 0xFFE65100, motion = 0xFF2E7D32, looks = 0xFF7B1FA2, input = 0xFF1565C0, phys = 0xFF00838F, dbg = 0xFF6D4C41;
        CATEGORY_COLOR.put("main", 0xFF018253);

        // Control
        def("Control", ctrl, "repeatTimes", "times var", "10|i", true,
                "for(int %2$s = 0; %2$s < (int)(%1$s); %2$s++){\n%3$s\n}\n%5$s");
        def("Control", ctrl, "ifThen", "boolean(Boolean)", "", true, "if(%1$s){\n%2$s\n}\n%4$s");
        def("Control", ctrl, "breakLoop", "", "", false, "break;\n%1$s");
        def("Control", ctrl, "stopEvent", "", "", false, "if(true) return;\n%1$s");
        def("Control", ctrl, "waitSeconds", "seconds", "1", true,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s));\n%4$s");
        def("Control", ctrl, "everySeconds", "seconds", "1", true,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %2$s\n    }\n},(float)(%1$s),(float)(%1$s));\n%4$s");
        def("Control", ctrl, "comment", "text", "note", false, "// %1$s\n%2$s");
        def("Control", ctrl, "codeLine", "code", "", false, "%1$s\n%2$s");

        // Variables & data
        def("Variables", vars, "setVariable", "variable value", "score|0", false, "%1$s = %2$s;\n%3$s");
        def("Variables", vars, "changeVariable", "variable by", "score|1", false, "%1$s += %2$s;\n%3$s");
        def("Variables", vars, "newVariable", "type name value", "int|count|0", false, "%1$s %2$s = %3$s;\n%4$s");
        def("Variables", vars, "saveData", "key value", "best|score", false, "setValue(\"%1$s\",String.valueOf(%2$s));\n%3$s");

        // Motion
        def("Motion", motion, "moveBy", "body(Body) dx dy", "|1|0", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition().x+(float)(%2$s),%1$s.getBody().getPosition().y+(float)(%3$s),%1$s.getBody().getAngle());\n%4$s");
        def("Motion", motion, "goTo", "body(Body) x y", "|0|0", false,
                "%1$s.getBody().setTransform((float)(%2$s),(float)(%3$s),%1$s.getBody().getAngle());\n%4$s");
        def("Motion", motion, "goToBody", "body(Body) target(Body)", "", false,
                "%1$s.getBody().setTransform(%2$s.getBody().getPosition(),%1$s.getBody().getAngle());\n%3$s");
        def("Motion", motion, "setVelocityX", "body(Body) speed", "|5", false,
                "%1$s.getBody().setLinearVelocity((float)(%2$s),%1$s.getBody().getLinearVelocity().y);\n%3$s");
        def("Motion", motion, "setVelocityY", "body(Body) speed", "|5", false,
                "%1$s.getBody().setLinearVelocity(%1$s.getBody().getLinearVelocity().x,(float)(%2$s));\n%3$s");
        def("Motion", motion, "stopMoving", "body(Body)", "", false,
                "%1$s.getBody().setLinearVelocity(0,0);\n%1$s.getBody().setAngularVelocity(0);\n%2$s");
        def("Motion", motion, "jump", "body(Body) power", "|8", false,
                "%1$s.getBody().setLinearVelocity(%1$s.getBody().getLinearVelocity().x,(float)(%2$s));\n%3$s");
        def("Motion", motion, "rotateBy", "body(Body) degrees", "|15", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition(),%1$s.getBody().getAngle()+(float)Math.toRadians(%2$s));\n%3$s");
        def("Motion", motion, "lookAt", "body(Body) target(Body)", "", false,
                "%1$s.getBody().setTransform(%1$s.getBody().getPosition(),%2$s.angleTo(%1$s));\n%3$s");
        def("Motion", motion, "moveToward", "body(Body) target(Body) speed", "||3", false,
                "{\n    float __a = %2$s.angleTo(%1$s);\n    %1$s.getBody().setLinearVelocity((float)Math.cos(__a)*(float)(%3$s),(float)Math.sin(__a)*(float)(%3$s));\n}\n%4$s");
        def("Motion", motion, "moveWithJoystick", "body(Body) joystick(Body) speed", "||5", false,
                "%1$s.getBody().setLinearVelocity(%2$s.getJoyStickX()*(float)(%3$s),%2$s.getJoyStickY()*(float)(%3$s));\n%4$s");
        def("Motion", motion, "walkWithJoystick", "body(Body) joystick(Body) speed", "||5", false,
                "%1$s.getBody().setLinearVelocity(%2$s.getJoyStickX()*(float)(%3$s),%1$s.getBody().getLinearVelocity().y);\n%4$s");

        // Looks
        def("Looks", looks, "show", "body(Body)", "", false, "%1$s.getActor().setVisible(true);\n%2$s");
        def("Looks", looks, "hide", "body(Body)", "", false, "%1$s.getActor().setVisible(false);\n%2$s");
        def("Looks", looks, "setTint", "body(Body) color", "|FFFFFFFF", false,
                "%1$s.getActor().setColor(com.badlogic.gdx.graphics.Color.valueOf(\"%2$s\"));\n%3$s");
        def("Looks", looks, "setOpacity", "body(Body) alpha", "|0.5", false,
                "%1$s.getActor().getColor().a = (float)(%2$s);\n%3$s");
        def("Looks", looks, "flipX", "body(Body)", "", false, "%1$s.getActor().setScaleX(-%1$s.getActor().getScaleX());\n%2$s");
        def("Looks", looks, "bringToFront", "body(Body)", "", false, "%1$s.getActor().toFront();\n%2$s");
        def("Looks", looks, "sendToBack", "body(Body)", "", false, "%1$s.getActor().toBack();\n%2$s");
        def("Looks", looks, "showText", "ui(Body) text", "|\"Hello\"", false, "%1$s.setItemText(String.valueOf(%2$s));\n%3$s");

        // Input
        def("Input", input, "ifTouching", "", "", true, "if(Gdx.input.isTouched()){\n%1$s\n} else {\n%2$s\n}\n%3$s");
        def("Input", input, "ifJoystickMoved", "joystick(Body)", "", true,
                "if(%1$s.getJoyStickX()!=0 || %1$s.getJoyStickY()!=0){\n%2$s\n} else {\n%3$s\n}\n%4$s");

        // Physics extras
        def("Physics", phys, "setBodyType", "body(Body) type", "|DynamicBody", false,
                "%1$s.getBody().setType(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.%2$s);\n%3$s");
        def("Physics", phys, "setGravityScaleOf", "body(Body) scale", "|1", false, "%1$s.getBody().setGravityScale((float)(%2$s));\n%3$s");
        def("Physics", phys, "destroyAfter", "body(Body) seconds", "|2", false,
                "Timer.schedule(new Timer.Task(){\n    public void run(){\n        %1$s.destroy();\n    }\n},(float)(%2$s));\n%3$s");
        def("Physics", phys, "spawnCopy", "body(Body) var newName x y", "|bullet|\"bullet\"|0|0", false,
                "PlayerItem %2$s = %1$s.getClone(%3$s);\n%2$s.getBody().setTransform((float)(%4$s),(float)(%5$s),0);\n%6$s");

        // Debug
        def("Debug", dbg, "toastValue", "value", "score", false, "toast(String.valueOf(%1$s));\n%2$s");
        def("Debug", dbg, "logValue", "value", "score", false, "debug(String.valueOf(%1$s));\n%2$s");
    }

    // ------------------------------------------------------------------ reporters

    private static void addReporters() {
        // original parameter dialog groups (same templates)
        r("Operators", "___ + ___", "(%1$s + %2$s)");
        r("Operators", "___ - ___", "(%1$s - %2$s)");
        r("Operators", "___ * ___", "(%1$s * %2$s)");
        r("Operators", "___ / ___", "(%1$s / %2$s)");
        r("Operators", "___ == ___", "(%1$s == %2$s)");
        r("Operators", "___ != ___", "(%1$s != %2$s)");
        r("Operators", "___ < ___", "(%1$s < %2$s)");
        r("Operators", "___ > ___", "(%1$s > %2$s)");
        r("Operators", "___ <= ___", "(%1$s <= %2$s)");
        r("Operators", "___ >= ___", "(%1$s >= %2$s)");
        r("Convert", "to int ___", "((int)(%1$s))");
        r("Convert", "to float ___", "((float)(%1$s))");
        r("Convert", "to string ___", "String.valueOf(%1$s)");
        r("PlayerItem", "___ .getX()", "%1$s.getX()");
        r("PlayerItem", "___ .getY()", "%1$s.getY()");
        r("PlayerItem", "___ .getZIndex()", "%1$s.getZIndex()");
        r("PlayerItem", "___ .getBody()", "%1$s.getBody()");
        r("PlayerItem", "___ .getActor()", "%1$s.getActor()");
        r("Box2D Body", "___ .getLinearVelocity()", "%1$s.getLinearVelocity()");
        r("Box2D Body", "___ .getAngularVelocity()", "%1$s.getAngularVelocity()");
        r("Box2D Body", "___ .getPosition()", "%1$s.getPosition()");
        r("Box2D Body", "___ .getMass()", "%1$s.getMass()");
        r("Stage / Scene", "getWidth()", "getWidth()");
        r("Stage / Scene", "getHeight()", "getHeight()");
        r("Stage / Scene", "getRoot()", "getRoot()");
        r("Stage / Scene", "getActors()", "getActors()");
        r("Stage / Scene", "getCamera()", "getCamera()");
        // new
        r("Logic", "___ and ___", "(%1$s && %2$s)");
        r("Logic", "___ or ___", "(%1$s || %2$s)");
        r("Logic", "not ___", "(!(%1$s))");
        r("Logic", "___ touches ___", "checkCollision(%1$s,%2$s)");
        r("Logic", "screen is touched", "Gdx.input.isTouched()");
        r("Math", "random ___ to ___", "random((int)(%1$s),(int)(%2$s))");
        r("Math", "___ mod ___", "(%1$s %% %2$s)");
        r("Math", "abs ___", "Math.abs(%1$s)");
        r("Math", "min ___ , ___", "Math.min(%1$s,%2$s)");
        r("Math", "max ___ , ___", "Math.max(%1$s,%2$s)");
        r("Math", "round ___", "Math.round(%1$s)");
        r("Math", "floor ___", "(float)Math.floor(%1$s)");
        r("Math", "sqrt ___", "(float)Math.sqrt(%1$s)");
        r("Math", "___ ^ ___", "(float)Math.pow(%1$s,%2$s)");
        r("Math", "sin ___°", "(float)Math.sin(Math.toRadians(%1$s))");
        r("Math", "cos ___°", "(float)Math.cos(Math.toRadians(%1$s))");
        r("Text", "join ___ ___", "(String.valueOf(%1$s) + String.valueOf(%2$s))");
        r("Text", "length of ___", "String.valueOf(%1$s).length()");
        r("Elements", "x of ___", "%1$s.getBodyX()");
        r("Elements", "y of ___", "%1$s.getBodyY()");
        r("Elements", "speed x of ___", "%1$s.getBody().getLinearVelocity().x");
        r("Elements", "speed y of ___", "%1$s.getBody().getLinearVelocity().y");
        r("Elements", "angle of ___", "(float)Math.toDegrees(%1$s.getBody().getAngle())");
        r("Elements", "distance ___ to ___", "%1$s.distTo(%2$s)");
        r("Elements", "angle ___ to ___", "%1$s.angleTo(%2$s)");
        r("Elements", "name of ___", "%1$s.getName()");
        r("Elements", "width of ___", "%1$s.getActor().getWidth()");
        r("Elements", "height of ___", "%1$s.getActor().getHeight()");
        r("Elements", "is visible ___", "%1$s.getActor().isVisible()");
        r("Joystick", "joystick x ___", "%1$s.getJoyStickX()");
        r("Joystick", "joystick y ___", "%1$s.getJoyStickY()");
        r("Joystick", "power ___", "%1$s.getPower()");
        r("Joystick", "angle (deg) ___", "%1$s.getAngleDegrees()");
        r("Input", "touch x", "getTouch().x");
        r("Input", "touch y", "getTouch().y");
        r("Input", "frame time", "getDelta()");
        r("Input", "camera x", "getCameraX()");
        r("Input", "camera y", "getCameraY()");
        r("Data", "saved text ___", "getValue(\"%1$s\")");
        r("Data", "saved number ___", "getNumber(\"%1$s\")");
    }

    private static void r(String g, String d, String t) {
        REPORTERS.add(new Reporter(g, d, t));
    }

    // ------------------------------------------------------------------ user libraries

    private static void loadCustomNodes(File f) {
        if (!f.exists()) return;
        try {
            JsonArray sections = JsonParser.parseString(BlockModel.read(f)).getAsJsonArray();
            for (JsonElement se : sections) {
                JsonObject s = se.getAsJsonObject();
                String cat = "★ " + BlockModel.str(s, "name", "Custom");
                if (!s.has("nodes")) continue;
                for (JsonElement ne : s.getAsJsonArray("nodes")) {
                    JsonObject n = ne.getAsJsonObject();
                    Def d = new Def();
                    d.category = cat;
                    d.color = 0xFF5D4037;
                    String title = BlockModel.str(n, "title", "");
                    d.title = title.isEmpty() ? BlockModel.str(n, "name", "custom") : title;
                    d.code = BlockModel.str(n, "code", "%1$s");
                    try { d.isIf = n.get("isBooleanNode").getAsBoolean(); } catch (Exception e) { d.isIf = false; }
                    ArrayList<String> fs = new ArrayList<>();
                    if (n.has("fields"))
                        for (JsonElement fe : n.getAsJsonArray("fields")) fs.add(BlockModel.str(fe.getAsJsonObject(), "name", "value"));
                    d.fields = fs.toArray(new String[0]);
                    add(d);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void loadCustomBlocks(File f) {
        if (!f.exists()) return;
        try {
            JsonArray sections = JsonParser.parseString(BlockModel.read(f)).getAsJsonArray();
            for (JsonElement se : sections) {
                JsonObject s = se.getAsJsonObject();
                String g = "★ " + BlockModel.str(s, "name", "Custom");
                if (!s.has("blocks")) continue;
                for (JsonElement be : s.getAsJsonArray("blocks")) {
                    JsonObject b = be.getAsJsonObject();
                    r(g, BlockModel.str(b, "title", "___"), BlockModel.str(b, "code", "%1$s"));
                }
            }
        } catch (Exception ignored) {}
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int r;
        while ((r = in.read(b)) != -1) bo.write(b, 0, r);
        return bo.toByteArray();
    }
}
