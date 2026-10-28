package com.star4droid.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.star4droid.template.Items.StageImp;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Desktop launcher of an exported Star2D game (Windows / Linux).
 *
 *   run.sh                         -> game/project.zip (or game/project/) next to the launcher
 *   run.sh --project <folder|zip>  -> any project
 *   run.sh --demo                  -> built-in demo scene (checks orientation / window sizing)
 *   run.sh --selftest              -> loads classes + natives, no window (CI)
 *
 * Window = shape of the project's screen: config "or" (portrait / landscape) and logicWidth / logicHeight.
 */
public final class DesktopLauncher {
    public static final String VERSION = "1";

    private DesktopLauncher() {}

    static final String USAGE =
            "Star2D desktop " + VERSION + "\n"
                    + "Usage: run.sh [--project <folder|project.zip>] [--scene scene1] [--game-jar <file>] [--data-dir <dir>]\n"
                    + "              [--fullscreen] [--exit-after <seconds>] | --demo [--demo-orientation portrait|landscape]\n"
                    + "              [--demo-logic LONGxSHORT] | --selftest | --version\n"
                    + "Without options it runs game/project.zip with game/game.jar next to this launcher.";

    public static void main(String[] argv) {
        Args a = Args.parse(argv);
        if (a.error != null) {
            System.err.println(a.error + "\n" + USAGE);
            System.exit(2);
        }
        if (a.version) {
            System.out.println("star2d-desktop " + VERSION);
            return;
        }
        if (a.help) {
            System.out.println(USAGE);
            return;
        }
        if (a.selftest) {
            System.exit(SelfTest.run());
            return;
        }
        File dataDir = a.dataDir != null ? a.dataDir : Locator.defaultDataDir(Locator.appRoot().getName());
        try {
            if (a.demo) {
                runDemo(a, dataDir);
                return;
            }
            File source = Locator.findProjectSource(a);
            if (source == null) {
                System.err.println("No project found.\n" + USAGE);
                System.exit(2);
                return;
            }
            runGame(a, source, dataDir);
        } catch (Throwable t) {
            fail(dataDir, t);
        }
    }

    // ------------------------------------------------------------------ game

    private static void runGame(Args a, File source, File dataDir) throws Exception {
        File project = Locator.resolveProject(source, dataDir);
        File jar = Locator.findGameJar(a, project);
        if (jar == null) throw new IllegalStateException("game.jar not found. " + GameCode.help());
        GameCode.setJar(jar);

        // orientation + logic size of the scene, exactly the keys StageImp.init reads
        File cfg = new File(project, "configs/" + a.scene + ".json");
        String or = "";
        float lw = 0, lh = 0;
        if (cfg.isFile()) {
            JsonObject o = JsonParser.parseString(new String(java.nio.file.Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            or = str(o, "or");
            lw = num(o, "logicWidth");
            lh = num(o, "logicHeight");
        }
        int[] monitor = monitorSize();
        int[] size = WindowSizing.compute(or, lw, lh, monitor[0], monitor[1]);

        StageImp stage = StageImp.getFromDex(project.getAbsolutePath(), a.scene, null, null);

        Lwjgl3ApplicationConfiguration cfgApp = baseConfig(a, dataDir, project.getName(), size);
        File icon = Locator.findIcon(project);
        if (icon != null) cfgApp.setWindowIcon(Files.FileType.Absolute, icon.getAbsolutePath());
        System.out.println("Star2D: " + project.getName() + " scene=" + a.scene + " or=" + (or.isEmpty() ? "landscape" : or)
                + " logic=" + lw + "x" + lh + " window=" + size[0] + "x" + size[1] + " monitor=" + monitor[0] + "x" + monitor[1]);
        new Lwjgl3Application(new Guard(stage, a.exitAfterSeconds, dataDir), cfgApp);
    }

    // ------------------------------------------------------------------ demo

    private static void runDemo(Args a, File dataDir) {
        int[] monitor = monitorSize();
        int[] size = WindowSizing.compute(a.demoOrientation, a.demoLong, a.demoShort, monitor[0], monitor[1]);
        float[] view = WindowSizing.viewportSize(a.demoOrientation, a.demoLong, a.demoShort);
        Lwjgl3ApplicationConfiguration cfg = baseConfig(a, dataDir, "Star2D demo", size);
        new Lwjgl3Application(new Guard(new DemoApp(a.demoOrientation, view[0], view[1]), a.exitAfterSeconds, dataDir), cfg);
    }

    // ------------------------------------------------------------------ common

    private static Lwjgl3ApplicationConfiguration baseConfig(Args a, File dataDir, String title, int[] size) {
        Lwjgl3ApplicationConfiguration c = new Lwjgl3ApplicationConfiguration();
        c.setTitle(title);
        c.setWindowedMode(size[0], size[1]);
        c.setResizable(true);                       // FitViewport letterboxes: the game keeps its shape
        c.setWindowSizeLimits(160, 160, -1, -1);
        c.useVsync(true);
        c.setForegroundFPS(60);
        c.setIdleFPS(15);
        c.setBackBufferConfig(8, 8, 8, 8, 16, 0, 0);
        c.setPreferencesConfig(new File(dataDir, "prefs").getAbsolutePath(), Files.FileType.Absolute);
        if (a.fullscreen) c.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        return c;
    }

    /** Size of the primary monitor in pixels; 1280x720 when GLFW cannot tell (headless). */
    static int[] monitorSize() {
        try {
            com.badlogic.gdx.Graphics.DisplayMode m = Lwjgl3ApplicationConfiguration.getDisplayMode();
            if (m != null && m.width > 0 && m.height > 0) return new int[]{m.width, m.height};
        } catch (Throwable ignored) {
        }
        return new int[]{1280, 720};
    }

    private static String str(JsonObject o, String k) {
        try {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static float num(JsonObject o, String k) {
        try {
            return Float.parseFloat(str(o, k));
        } catch (Exception e) {
            return 0;
        }
    }

    static void fail(File dataDir, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        System.err.println(sw);
        try {
            dataDir.mkdirs();
            java.nio.file.Files.write(new File(dataDir, "error.txt").toPath(), sw.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        System.exit(1);
    }

    /** Delegates to the game; optional auto exit (smoke tests) and error log. */
    private static final class Guard implements ApplicationListener {
        private final ApplicationListener inner;
        private final float exitAfter;
        private final File dataDir;
        private float t;

        Guard(ApplicationListener inner, float exitAfter, File dataDir) {
            this.inner = inner;
            this.exitAfter = exitAfter;
            this.dataDir = dataDir;
        }

        @Override public void create() { run(inner::create); }
        @Override public void resize(int w, int h) { inner.resize(w, h); }

        @Override
        public void render() {
            run(inner::render);
            if (exitAfter >= 0) {
                t += Gdx.graphics.getDeltaTime();
                if (t >= exitAfter) Gdx.app.exit();
            }
        }

        @Override public void pause() { inner.pause(); }
        @Override public void resume() { inner.resume(); }
        @Override public void dispose() { inner.dispose(); }

        private void run(Runnable r) {
            try {
                r.run();
            } catch (Throwable e) {
                fail(dataDir, e);
            }
        }
    }
}
