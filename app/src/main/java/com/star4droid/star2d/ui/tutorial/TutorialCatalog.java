package com.star4droid.star2d.ui.tutorial;

import android.app.Activity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.star2d.ui.CollectionsDialog;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.blocks.BlockModel;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Lessons shown on the home screen, and the project each lesson starts with. */
public final class TutorialCatalog {
    private TutorialCatalog() {}

    public static class Info {
        public String id, title, tag, description;
    }

    public interface OnReady { void on(File projectDir); }

    public static List<Info> all(Activity a) {
        ArrayList<Info> out = new ArrayList<>();
        for (Lesson l : Lessons.all()) {
            Info i = new Info();
            i.id = l.id;
            i.title = l.title;
            i.tag = l.tag;
            i.description = l.desc;
            out.add(i);
        }
        return out;
    }

    /** Project folder name for a lesson; a number is added when it already exists. */
    static String freeName(File projectsDir, String base) {
        String name = base;
        int n = 2;
        while (new File(projectsDir, name).exists()) name = base + (n++);
        return name;
    }

    public static void start(Activity a, String id, File projectsDir, OnReady ready) {
        Lesson l = Lessons.find(id);
        if (l == null) return;
        String base = "Lesson_" + Character.toUpperCase(id.charAt(0)) + id.substring(1);
        File dir = new File(projectsDir, freeName(projectsDir, base));
        Snack.show(Tr.t("home_creating", "Creating project..."));
        new Thread(() -> {
            try {
                create(a, l, dir);
                a.runOnUiThread(() -> {
                    Tutorial.begin(l, dir);
                    ready.on(dir);
                });
            } catch (Exception e) {
                Snack.show(Tr.t("ui_failed", "Failed") + ": " + e);
            }
        }).start();
    }

    static void create(Activity a, Lesson l, File dir) throws Exception {
        for (String p : new String[]{"sounds", "anims", "images", "files", "icon", "scenes", "configs"}) new File(dir, p).mkdirs();
        // config: orientation + screen size (long side first, as the runtime expects)
        JsonObject cfg;
        try (InputStream in = a.getAssets().open("scene.json")) {
            byte[] b = new byte[in.available()];
            int r = in.read(b);
            cfg = JsonParser.parseString(new String(b, 0, r, "UTF-8")).getAsJsonObject();
        } catch (Exception e) {
            cfg = new JsonObject();
        }
        cfg.addProperty("or", l.landscape ? "" : "portrait");
        cfg.addProperty("logicWidth", "1280");
        cfg.addProperty("logicHeight", "720");
        cfg.addProperty("sceneColor", l.landscape ? "#81D4FA" : "#1B2A3A");
        BlockModel.write(new File(dir, "configs/scene1.json"), cfg.toString());
        if (l.sceneJson != null) BlockModel.write(new File(dir, "scenes/scene1"), l.sceneJson);
        File images = new File(dir, "images");
        for (String f : l.startImages) CollectionsDialog.copy(a, f, images);
    }
}
