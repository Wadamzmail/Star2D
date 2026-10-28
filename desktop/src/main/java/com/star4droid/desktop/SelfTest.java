package com.star4droid.desktop;

import java.io.File;
import java.io.FileOutputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** --selftest: loads the engine classes, the native libraries and the resources WITHOUT opening a window (CI / packaging check). */
final class SelfTest {
    private static int failed = 0;

    private SelfTest() {}

    private static void check(String name, boolean ok, String info) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok || info == null ? "" : "  " + info));
        if (!ok) failed++;
    }

    interface Step { void run() throws Throwable; }

    private static void step(String name, Step s) {
        try {
            s.run();
            check(name, true, null);
        } catch (Throwable t) {
            check(name, false, t.toString());
        }
    }

    static int run() {
        System.out.println("star2d-desktop " + DesktopLauncher.VERSION + " | java " + System.getProperty("java.version")
                + " | " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        // 1. runtime classes (shared sources) and the backend
        String[] classes = {
                "com.star4droid.template.Items.StageImp", "com.star4droid.template.Items.GroupItem", "com.star4droid.template.Items.GroupFollower",
                "com.star4droid.template.Items.BoxBody", "com.star4droid.template.Items.TextItem", "com.star4droid.template.Utils.PlayerItem",
                "com.star4droid.template.Utils.PropertySet", "com.star4droid.template.Utils.ProjectAssetLoader", "com.star4droid.template.Utils.Utils",
                "com.star4droid.template.LoadingStage", "com.star4droid.star2d.ElementDefs.GroupDef", "com.star4droid.star2d.ElementDefs.BoxDef",
                "com.star4droid.star2d.Helpers.Project", "com.star4droid.star2d.Helpers.rtl.RtlController",
                "com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application", "com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator",
                "com.badlogic.gdx.physics.box2d.World", "box2dLight.RayHandler", "com.kotcrab.vis.ui.VisUI", "net.lingala.zip4j.ZipFile",
                "android.util.Log", "dalvik.system.DexClassLoader"};
        for (String c : classes) {
            step("class " + c, () -> Class.forName(c, false, SelfTest.class.getClassLoader()));
        }
        step("StageImp.getFromDex exists (loads game classes)", () ->
                Class.forName("com.star4droid.template.Items.StageImp").getMethod("getFromDex", String.class, String.class,
                        Class.forName("com.star4droid.template.Utils.ProjectAssetLoader"), Class.forName("com.star4droid.template.Utils.SpriteSheetLoader")));

        // 2. engine files the runtime reads with Gdx.files.internal
        for (String r : new String[]{"/images/logo.png", "/images/white.png", "/images/joyback.png", "/images/joybtn.png", "/files/default.fnt", "/files/default.png", "/files/font.fnt", "/files/smoke.p"})
            check("resource " + r, SelfTest.class.getResource(r) != null, "missing from the jar");

        // 3. natives of THIS os (no GL needed)
        step("natives: gdx", () -> com.badlogic.gdx.utils.GdxNativesLoader.load());
        step("natives: box2d world step", () -> {
            com.badlogic.gdx.physics.box2d.Box2D.init();
            com.badlogic.gdx.physics.box2d.World w = new com.badlogic.gdx.physics.box2d.World(new com.badlogic.gdx.math.Vector2(0, -10), true);
            com.badlogic.gdx.physics.box2d.BodyDef bd = new com.badlogic.gdx.physics.box2d.BodyDef();
            bd.type = com.badlogic.gdx.physics.box2d.BodyDef.BodyType.DynamicBody;
            com.badlogic.gdx.physics.box2d.Body b = w.createBody(bd);
            com.badlogic.gdx.physics.box2d.CircleShape cs = new com.badlogic.gdx.physics.box2d.CircleShape();
            cs.setRadius(1);
            b.createFixture(cs, 1);
            w.step(1 / 60f, 6, 2);
            if (b.getPosition().y >= 0) throw new IllegalStateException("body did not fall: " + b.getPosition());
            cs.dispose();
            w.dispose();
        });
        step("natives: freetype", () -> {
            com.badlogic.gdx.graphics.g2d.freetype.FreeType.Library l = com.badlogic.gdx.graphics.g2d.freetype.FreeType.initFreeType();
            if (l == null) throw new IllegalStateException("null library");
            l.dispose();
        });

        // 4. the DexClassLoader shim finds the game jar
        step("game jar loader (jar next to a fake dex path)", () -> {
            File dir = java.nio.file.Files.createTempDirectory("s2d-selftest").toFile();
            File jar = new File(dir, "scenes.jar");
            try (JarOutputStream o = new JarOutputStream(new FileOutputStream(jar))) {
                o.putNextEntry(new JarEntry("probe.txt"));
                o.write(1);
                o.closeEntry();
            }
            dalvik.system.DexClassLoader l = new dalvik.system.DexClassLoader(new File(dir, "scenes.dex").getPath(), null, null, SelfTest.class.getClassLoader());
            if (l.getResource("probe.txt") == null) throw new IllegalStateException("probe not found in the jar");
            Locator.deleteTree(dir);
        });

        // 5. window sizing sanity
        int[] p = WindowSizing.compute("portrait", 1280, 720, 1920, 1080);
        int[] l = WindowSizing.compute("", 1280, 720, 1920, 1080);
        check("window sizing portrait is tall / landscape is wide", p[1] > p[0] && l[0] > l[1], p[0] + "x" + p[1] + " / " + l[0] + "x" + l[1]);

        System.out.println(failed == 0 ? "SELFTEST OK" : "SELFTEST FAILED (" + failed + ")");
        return failed == 0 ? 0 : 1;
    }
}
