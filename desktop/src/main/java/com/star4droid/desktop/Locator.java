package com.star4droid.desktop;

import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Finds / unpacks the project the same way the Android LoadingScreen does: project.zip (password-protected) is extracted
 * to "<data>/game/" and the project folder is the (first) folder inside it.
 */
final class Locator {
    static final String ZIP_PASSWORD = "tulsgskdiensl626__Xxmoishs";   // same as ExportThread / LoadingScreen

    private Locator() {}

    /** Folder that contains game/, lib/ ... (the distribution root). The jar sits in lib/ or next to run.sh. */
    static File appRoot() {
        try {
            File jar = new File(DesktopLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jar.isFile() ? jar.getParentFile() : jar;
            if (dir != null && dir.getName().equals("lib") && dir.getParentFile() != null) return dir.getParentFile();
            return dir == null ? new File(".") : dir;
        } catch (Exception e) {
            return new File(".");
        }
    }

    /** Default user data dir of this game: %APPDATA%/Star2D/<id> (Windows), $XDG_DATA_HOME or ~/.local/share/star2d/<id> (Linux). */
    static File defaultDataDir(String id) {
        String os = System.getProperty("os.name", "").toLowerCase();
        File base;
        if (os.contains("win") && System.getenv("APPDATA") != null) base = new File(System.getenv("APPDATA"), "Star2D");
        else if (System.getenv("XDG_DATA_HOME") != null && !System.getenv("XDG_DATA_HOME").isEmpty()) base = new File(System.getenv("XDG_DATA_HOME"), "star2d");
        else base = new File(System.getProperty("user.home", "."), ".local/share/star2d");
        return new File(base, id.replaceAll("[^A-Za-z0-9._-]", "_"));
    }

    /** project path given on the command line, or the one shipped next to the launcher; null if none. */
    static File findProjectSource(Args a) {
        if (a.project != null) return new File(a.project);
        File root = appRoot();
        File[] candidates = {
                new File(root, "game/project.zip"), new File(root, "game/project"),
                new File(root, "project.zip"), new File("game/project.zip"), new File("project.zip")};
        for (File c : candidates) if (c.exists()) return c;
        return null;
    }

    /** @return the project folder (contains configs/, scenes/, images/ ...) */
    static File resolveProject(File source, File dataDir) throws IOException {
        if (source.isDirectory()) return normalizeFolder(source);
        if (!source.isFile()) throw new IOException("Project not found: " + source);
        File game = new File(dataDir, "game");
        File sig = new File(dataDir, "game.sig");
        String want = source.length() + ":" + source.lastModified();
        String have = sig.isFile() ? new String(Files.readAllBytes(sig.toPath()), StandardCharsets.UTF_8).trim() : "";
        if (!want.equals(have) || !game.isDirectory()) {
            deleteTree(game);
            if (!game.mkdirs() && !game.isDirectory()) throw new IOException("Cannot create " + game);
            try {
                ZipFile zip = new ZipFile(source);
                if (zip.isEncrypted()) zip.setPassword(ZIP_PASSWORD.toCharArray());
                zip.extractAll(game.getAbsolutePath());
            } catch (Exception e) {
                throw new IOException("Cannot extract " + source + ": " + e, e);
            }
            try (FileOutputStream o = new FileOutputStream(sig)) {
                o.write(want.getBytes(StandardCharsets.UTF_8));
            }
        }
        return normalizeFolder(game);
    }

    /** The Android code takes listFiles()[0] of the extracted folder; be a bit smarter: the folder that has configs/. */
    private static File normalizeFolder(File f) throws IOException {
        if (new File(f, "configs").isDirectory()) return f;
        File[] kids = f.listFiles(File::isDirectory);
        if (kids != null) {
            for (File k : kids) if (new File(k, "configs").isDirectory()) return k;
            if (kids.length > 0) return kids[0];
        }
        throw new IOException("This is not a Star2D project (no configs/ folder): " + f);
    }

    static File findGameJar(Args a, File project) {
        if (a.gameJar != null) return a.gameJar;
        File root = appRoot();
        File[] candidates = {
                new File(root, "game/game.jar"), new File(project, "dex/scenes.jar"), new File(project, "dex/game.jar"),
                new File(project.getParentFile(), "game.jar"), new File("game/game.jar"), new File("game.jar")};
        for (File c : candidates) if (c.isFile()) return c;
        return null;
    }

    static File findIcon(File project) {
        File root = appRoot();
        File[] candidates = {new File(root, "game/icon.png"), new File(root, "icon.png"), new File(project, "icon/icon.png")};
        for (File c : candidates) if (c.isFile()) return c;
        File dir = new File(project, "icon");
        File[] pngs = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        return pngs != null && pngs.length > 0 ? pngs[0] : null;
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
