package com.star4droid.desktop;

import java.io.File;
import java.io.FileInputStream;

/**
 * Where the compiled game classes (Java bytecode, produced by the phone's compiler BEFORE dexing) are.
 * Contract (see docs/DESKTOP.md): one jar, "game.jar", containing com.star4droid.Game.&lt;scene lower case&gt; (extends StageImp)
 * and every script/helper class of the project, compiled with --release 8 against the runtime classes.
 */
public final class GameCode {
    private GameCode() {}

    private static volatile File explicitJar;

    public static void setJar(File jar) { explicitJar = jar; }

    public static File getJar() { return explicitJar; }

    /** @param dexPath what Project.getDex() says (".../dex/scenes.dex"): the Android path, only used as a hint */
    public static File resolveJar(String dexPath) {
        File e = explicitJar;
        if (e != null && e.isFile()) return e;
        if (dexPath != null) {
            File f = new File(dexPath);
            File dir = f.getParentFile();
            if (dir != null) {
                File sibling = new File(dir, "scenes.jar");
                if (sibling.isFile()) return sibling;
                File game = new File(dir, "game.jar");
                if (game.isFile()) return game;
            }
            if (isZip(f)) return f;
        }
        return null;
    }

    static boolean isZip(File f) {
        if (f == null || !f.isFile()) return false;
        try (FileInputStream in = new FileInputStream(f)) {
            return in.read() == 'P' && in.read() == 'K';
        } catch (Exception ex) {
            return false;
        }
    }

    public static String help() {
        return "Put the compiled classes of the game in game/game.jar next to the launcher, or pass --game-jar <file>.";
    }
}
