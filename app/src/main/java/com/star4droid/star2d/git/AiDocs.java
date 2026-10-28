package com.star4droid.star2d.git;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.ai.AiContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Writes the two Markdown files that let an AI assistant (Claude, ChatGPT...) understand the repository:
 * ENGINE.md (how a Star2D project works, fixed) and STATE.md (what is in this project right now, regenerated).
 */
public final class AiDocs {
    private AiDocs() {}

    public static final String ENGINE = "ENGINE.md", STATE = "STATE.md";
    /** Where the editor leaves the result of the last build for STATE.md. */
    public static final String BUILD_LOG = ".star2d/last-build.txt";

    public static void generate(File dir, boolean engine) throws IOException {
        if (engine) write(new File(dir, ENGINE), engineText(dir.getName()));
        write(new File(dir, STATE), stateText(dir));
    }

    /** Called before a commit when the repository already has the docs. */
    public static boolean enabled(File dir) {
        return new File(dir, ENGINE).exists();
    }

    private static void write(File f, String text) throws IOException {
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(text.getBytes("UTF-8"));
        }
    }

    // ------------------------------------------------------------------ ENGINE.md

    static String engineText(String project) {
        String g = AiContext.ENGINE_GUIDE;
        int a = g.indexOf("## How a Star2D project works");
        int b = g.indexOf("## Taking actions in the editor");
        String guide = a >= 0 ? g.substring(a, b > a ? b : g.length()) : g;
        StringBuilder sb = new StringBuilder();
        sb.append("# Star2D project: ").append(project).append("\n\n");
        sb.append("This repository is a game made with the **Star2D** engine editor (Android, LibGDX + Box2D).\n");
        sb.append("It is edited on a phone: the person using the editor can ask you (an AI assistant) to change the game, ")
                .append("then taps **Pull** in the editor to receive your commits.\n\n");
        sb.append("**Read `STATE.md` first**: it lists the scenes, elements, events with code and the last build errors as of the last commit.\n\n");
        sb.append(guide.replace("\n- ", "\n- ")).append("\n");
        sb.append("## How to change this repository (rules for AI assistants)\n");
        sb.append("1. Work directly on the files below and commit to the same branch the person uses (normally `main`). Small, focused commits with a clear message.\n");
        sb.append("2. **Scenes** (`scenes/<scene>`) are a JSON array, one object per element. To add an element copy an existing object of the same kind, ")
                .append("then change `name` (must be unique, letters/digits/underscore, it becomes a field in the generated class), `x`, `y`, `width`, `height` and `image`. ")
                .append("Keep every other key. Positions are in the scene's logic units (see `configs/<scene>.json`: `or` is `portrait` or empty for landscape).\n");
        sb.append("3. **Events**: the build uses `Events/<scene>/public/<Event>.java` and `Events/<scene>/private/<element>/<event>.java` (plain Java statements, no class or method header). ")
                .append("Each may have a `.visual` twin used by the block editor. **If you edit a `.java` event, delete its `.visual` twin** so the two cannot disagree.\n");
        sb.append("4. **Custom scripts** are `scripts/<name>.java` (members pasted into the scene class); advanced body/scene scripts live under `java/com/star4droid/Game/Scripts/...` and `.../SceneScript/...`.\n");
        sb.append("5. **Assets**: images in `images/`, sounds in `sounds/`, animations in `anims/`. Refer to an image as `/name.png` (relative to `images/`). Do not commit files larger than 50 MB.\n");
        sb.append("6. **Never edit** generated or machine-local files (they are in `.gitignore`): `java/com/star4droid/Game/*.java`, `dex/`, `bin/`, `logs/`, `Events/JavaChanges.json`, `Events/*/UndoRedo.json`.\n");
        sb.append("7. Do not edit `STATE.md`; the editor regenerates it. Do not rename scenes or elements unless asked: events and joints refer to them by name.\n");
        sb.append("8. After your change the person pulls it in the editor, which reloads the scene and builds the game. If the build fails the errors appear in `STATE.md` after their next commit; ask them to commit and read it.\n\n");
        sb.append("## Folder map\n");
        sb.append("```\nscenes/            one JSON file per scene\nconfigs/           per-scene settings (orientation, size, colors)\nEvents/            visual scripts per scene and element (.java + .visual)\njoints/            physics joints per scene\nscripts/           custom Java members\njava/              advanced scripts (Game/Scripts, Game/SceneScript)\nimages/ sounds/ anims/ files/   assets\nICON/ icon/        app icon\nENGINE.md STATE.md  these documents\n```\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ STATE.md

    static String stateText(File dir) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Project state: ").append(dir.getName()).append("\n\n");
        sb.append("_Generated by the Star2D editor on ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date()))
                .append(". Do not edit; see `ENGINE.md` for how the project works._\n\n");
        File[] scenes = new File(dir, "scenes").listFiles();
        if (scenes == null || scenes.length == 0) {
            sb.append("No scenes yet.\n\n");
        } else {
            Arrays.sort(scenes);
            for (File s : scenes) {
                if (!s.isFile()) continue;
                String name = s.getName();
                sb.append("## Scene `").append(name).append("`").append(orientation(dir)).append("\n\n");
                try {
                    String json = new String(Files.readAllBytes(s.toPath()), Charset.forName("UTF-8"));
                    JsonElement root = JsonParser.parseString(json);
                    if (root.isJsonArray()) elements(sb, root.getAsJsonArray());
                } catch (Exception e) {
                    sb.append("(could not read: ").append(e.getMessage()).append(")\n");
                }
                events(sb, new File(dir, "Events/" + name));
                File vars = new File(dir, "Events/" + name + "/public/variables.json");
                if (vars.isFile()) sb.append("\nVariables: `").append(trim(read(vars), 800)).append("`\n");
                sb.append("\n");
            }
        }
        sb.append("## Assets\n\n");
        sb.append("- images: ").append(names(new File(dir, "images"), "", 60)).append("\n");
        sb.append("- sounds: ").append(names(new File(dir, "sounds"), "", 30)).append("\n");
        sb.append("- anims: ").append(names(new File(dir, "anims"), "", 30)).append("\n");
        sb.append("- scripts: ").append(names(new File(dir, "scripts"), "", 30)).append("\n\n");
        sb.append("## Last build\n\n");
        File bl = new File(dir, BUILD_LOG);
        if (bl.isFile()) sb.append("```\n").append(trim(read(bl), 4000)).append("\n```\n");
        else sb.append("No build result recorded yet.\n");
        return sb.toString();
    }

    private static String orientation(File dir) {
        try {
            JsonObject o = JsonParser.parseString(read(new File(dir, "configs/scene1.json"))).getAsJsonObject();
            String or = o.has("or") ? o.get("or").getAsString() : "";
            return or.equals("portrait") ? " (portrait)" : " (landscape)";
        } catch (Exception e) {
            return "";
        }
    }

    private static void elements(StringBuilder sb, JsonArray arr) {
        sb.append("| name | kind | type | x, y | size | image | script |\n|---|---|---|---|---|---|---|\n");
        int n = 0;
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) continue;
            if (++n > 150) {
                sb.append("| … | | | | | | |\n");
                break;
            }
            JsonObject o = e.getAsJsonObject();
            sb.append("| ").append(cell(o, "name")).append(" | ").append(cell(o, "TYPE")).append(" | ").append(cell(o, "type"))
                    .append(" | ").append(num(o, "x")).append(", ").append(num(o, "y"))
                    .append(" | ").append(num(o, "width")).append("×").append(num(o, "height"))
                    .append(" | ").append(cell(o, "image")).append(" | ").append(cell(o, "Script")).append(" |\n");
        }
        sb.append("\n").append(n).append(" element(s).\n");
    }

    private static void events(StringBuilder sb, File root) {
        StringBuilder ev = new StringBuilder();
        collect(root, "Events/", ev, 0);
        sb.append("\nEvents with code: ").append(ev.length() == 0 ? "none" : ev.toString()).append("\n");
    }

    private static void collect(File d, String prefix, StringBuilder out, int depth) {
        File[] l = d.listFiles();
        if (l == null || depth > 4) return;
        Arrays.sort(l);
        for (File f : l) {
            if (f.isDirectory()) collect(f, prefix + f.getName() + "/", out, depth + 1);
            else if (f.getName().endsWith(".java") && f.length() > 0) out.append("`").append(prefix.substring(7)).append(f.getName()).append("` ");
        }
    }

    private static String names(File d, String prefix, int max) {
        StringBuilder sb = new StringBuilder();
        int[] c = {0};
        list(d, prefix, sb, c, max);
        return sb.length() == 0 ? "-" : sb.toString().trim();
    }

    private static void list(File d, String prefix, StringBuilder sb, int[] c, int max) {
        File[] l = d.listFiles();
        if (l == null) return;
        Arrays.sort(l);
        for (File f : l) {
            if (c[0] >= max) {
                if (c[0] == max) sb.append("… ");
                c[0]++;
                return;
            }
            if (f.isDirectory()) list(f, prefix + f.getName() + "/", sb, c, max);
            else {
                sb.append("`").append(prefix).append(f.getName()).append("` ");
                c[0]++;
            }
        }
    }

    private static String cell(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString().replace("|", "/").replace("\n", " ") : "";
    }

    private static String num(JsonObject o, String k) {
        String v = cell(o, k);
        try {
            float f = Float.parseFloat(v);
            return f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(Math.round(f * 100) / 100f);
        } catch (Exception e) {
            return v;
        }
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), Charset.forName("UTF-8"));
        } catch (IOException e) {
            return "";
        }
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
