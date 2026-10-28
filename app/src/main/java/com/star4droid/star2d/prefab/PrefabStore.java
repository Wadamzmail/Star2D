package com.star4droid.star2d.prefab;

import com.star4droid.star2d.Helpers.editor.Project;

import java.io.File;
import java.util.List;

/** The prefabs/ folder of a project: files, folders, naming. */
public final class PrefabStore {
    private PrefabStore() {}

    public static final String TEMP_PREFIX = "__prefab_";

    public static final class Entry {
        public String path;
        public String name;
        public boolean isFolder;
    }

    public static boolean isTempScene(String scene) { return scene != null && scene.startsWith("__"); }
    public static File dir(Project p) { throw new UnsupportedOperationException("skeleton"); }
    public static List<String> list(Project p) { throw new UnsupportedOperationException("skeleton"); }
    public static List<Entry> listTree(Project p) { throw new UnsupportedOperationException("skeleton"); }
    public static List<Entry> children(Project p, String folder) { throw new UnsupportedOperationException("skeleton"); }
    public static File file(Project p, String path) { throw new UnsupportedOperationException("skeleton"); }
    public static boolean exists(Project p, String path) { throw new UnsupportedOperationException("skeleton"); }
    public static PrefabModel load(Project p, String path) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void save(Project p, String path, PrefabModel m) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static String create(Project p, String path) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void createFolder(Project p, String path) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void delete(Project p, String path) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void rename(Project p, String from, String to) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static String duplicate(Project p, String from, String to) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static String normalize(String path) { throw new UnsupportedOperationException("skeleton"); }
}
