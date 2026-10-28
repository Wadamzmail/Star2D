package com.star4droid.star2d.prefab;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.items.EditorItem;

/**
 * Editor-side rules for prefab editing (a prefab is edited in a hidden temp scene, see PrefabOps.beginEdit):
 * its root can't be removed, and everything added gets the root as parent. GL thread.
 */
public final class PrefabSession {
    private PrefabSession() {}

    public static boolean isTemp(LibgdxEditor ed) {
        return ed != null && PrefabStore.isTempScene(ed.getScene());
    }

    public static boolean isRoot(Actor a) {
        if (!(a instanceof EditorItem)) return false;
        PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
        return ps != null && PrefabModel.ROOT.equals(ps.getString("pid"));
    }

    /** True when this actor is the fixed root of the prefab being edited (can't be deleted, copied or unparented). */
    public static boolean isFixedRoot(LibgdxEditor ed, Actor a) {
        return isTemp(ed) && isRoot(a);
    }

    public static Actor findRoot(LibgdxEditor ed) {
        if (ed == null) return null;
        for (Actor a : ed.getActors()) if (isRoot(a)) return a;
        return null;
    }

    /** Called after an element was added in the editor: inside a prefab it belongs under the root. */
    public static void afterAdd(LibgdxEditor ed, Actor a) {
        try {
            if (!isTemp(ed) || !(a instanceof EditorItem) || isRoot(a)) return;
            PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
            if (ps == null || !ps.containsKey("parent") || !ps.getString("parent").isEmpty()) return;
            Actor root = findRoot(ed);
            if (root == null) return;
            PropertySet<String, Object> rps = PropertySet.getPropertySet(root);
            if (ps.setParent(rps)) ps.put("parent", root.getName());
        } catch (Throwable ignored) {
        }
    }
}
