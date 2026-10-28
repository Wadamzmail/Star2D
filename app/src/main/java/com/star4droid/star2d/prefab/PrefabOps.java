package com.star4droid.star2d.prefab;

import com.star4droid.star2d.Helpers.editor.Project;

import java.util.ArrayList;
import java.util.List;

/** Prefab operations on scene files (instantiate, apply, revert, unpack, link, edit). File based; see PrefabEditorSync for the editor side. */
public final class PrefabOps {
    private PrefabOps() {}

    public static final class Result {
        public String root;
        public List<String> created = new ArrayList<>();
    }

    public static final class Info {
        public String prefabPath, rootName, pid;
        public boolean isRoot, missing;
        public int modifiedCount;
        public List<String> modified = new ArrayList<>();
    }

    public static Result instantiate(Project p, String scene, String prefabPath, float x, float y, String wantedName) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static String extract(Project p, String scene, String elementName, String prefabPath) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void apply(Project p, String scene, String rootName) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void revert(Project p, String scene, String rootName) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void unpack(Project p, String scene, String rootName) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void link(Project p, String scene, String groupName, String prefabPath) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static Info info(Project p, String scene, String elementName) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void syncAll(Project p, String prefabPath) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static String beginEdit(Project p, String prefabPath) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void commitEdit(Project p, String tempScene, String prefabPath) throws Exception { throw new UnsupportedOperationException("skeleton"); }
    public static void discardEdit(Project p, String tempScene) throws Exception { throw new UnsupportedOperationException("skeleton"); }
}
