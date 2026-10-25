package com.star4droid.star2d.ui.tutorial;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.HashMap;

/** Registry of named views the tutorial engine can point at ("top:play", "rail:elements"...). */
public final class Tut {
    private Tut() {}

    private static final HashMap<String, WeakReference<View>> views = new HashMap<>();
    private static final java.util.WeakHashMap<View, String> ids = new java.util.WeakHashMap<>();

    public static void tag(View v, String id) {
        if (v == null || id == null) return;
        synchronized (views) {
            // a recycled list row: forget the id it had before
            String old = ids.get(v);
            if (old != null && !old.equals(id)) {
                WeakReference<View> r = views.get(old);
                if (r != null && r.get() == v) views.remove(old);
            }
            ids.put(v, id);
            views.put(id, new WeakReference<>(v));
        }
    }

    /** Returns the attached, visible view registered under id, or null. */
    public static View find(String id) {
        synchronized (views) {
            WeakReference<View> r = views.get(id);
            View v = r == null ? null : r.get();
            if (v == null || !v.isAttachedToWindow() || !v.isShown() || !id.equals(ids.get(v))) return null;
            return v;
        }
    }
}
