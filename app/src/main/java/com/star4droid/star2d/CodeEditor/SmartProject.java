package com.star4droid.star2d.CodeEditor;

import android.content.Context;

import com.star4droid.star2d.CodeEditor.smart.JavaAnalyzer;
import com.star4droid.star2d.CodeEditor.smart.JavaEnv;
import com.star4droid.star2d.Helpers.FileUtil;
import com.star4droid.star2d.evo.star2dApp;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One analyzer per project, created on first use and kept for the process. */
public final class SmartProject {
    private static final Map<String, SmartProject> CACHE = new HashMap<>();

    public final String root;
    public final JavaEnv env;
    public final JavaAnalyzer analyzer;

    private SmartProject(String root, JavaEnv env) {
        this.root = root;
        this.env = env;
        this.analyzer = new JavaAnalyzer(env);
    }

    /** The project folder a file belongs to (the folder that contains Events/, scripts/ or java/), or null. */
    public static String rootOf(String file) {
        if (file == null) return null;
        for (String marker : new String[]{"/Events/", "/scripts/", "/java/"}) {
            int i = file.indexOf(marker);
            if (i > 0) return file.substring(0, i);
        }
        return null;
    }

    public static synchronized SmartProject get(String root) {
        SmartProject p = CACHE.get(root);
        if (p == null) {
            File java = new File(root, "java");
            p = new SmartProject(root, new JavaEnv(libs(), java, "7"));
            CACHE.put(root, p);
        }
        return p;
    }

    /** Throw away the cached class index (project files changed a lot). */
    public static synchronized void forget(String root) {
        SmartProject p = CACHE.remove(root);
        if (p != null) p.env.refresh();
    }

    private static boolean extractedThisRun;

    /** cp.jar and addition.jar, the same jars the compiler uses. Extracted from the assets when missing. */
    static List<File> libs() {
        ArrayList<File> out = new ArrayList<>();
        try {
            Context c = star2dApp.getContext();
            File bin = new File(FileUtil.getPackageDataDir(c), "bin");
            bin.mkdirs();
            File cp = new File(bin, "cp.jar"), add = new File(bin, "addition.jar");
            if (!cp.exists() || cp.length() == 0) copyAsset(c, "cp.zip", cp);
            if (!extractedThisRun || !add.exists() || add.length() == 0) {
                copyAsset(c, "java/game.zip", add);
                extractedThisRun = true;
            }
            out.add(cp);
            out.add(add);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void copyAsset(Context c, String asset, File to) throws Exception {
        try (InputStream in = c.getAssets().open(asset); OutputStream o = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
    }
}
