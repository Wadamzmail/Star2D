package com.star4droid.star2d.remote;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.ui.Bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Cheap checks run after editing, so the AI learns about two classic traps immediately instead of after a failed test:
 * (1) a TEXT too narrow for its content wraps onto several lines, (2) a TEXT lying on top of a button swallows the touch.
 * All methods: GL thread.
 */
final class Warnings {
    private Warnings() {}

    private static BitmapFont font;
    static final String[] TOUCH_EVENTS = {"onClick", "onTouchStart", "onTouchEnd"};

    private static BitmapFont font() {
        if (font == null) font = new BitmapFont(Gdx.files.internal("files/default.fnt"));
        return font;
    }

    private static float f(LinkedHashMap<String, String> m, String k, float def) {
        String v = m.get(k);
        if (v == null || v.isEmpty()) return def;
        try {
            return Float.parseFloat(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean isText(LinkedHashMap<String, String> m) {
        return "TEXT".equalsIgnoreCase(String.valueOf(m.get("TYPE")));
    }

    /** Wrapping check for TEXT elements (all when names is null). The text is measured with the engine's default font. */
    static List<String> text(LibgdxEditor ed, Collection<String> names) {
        ArrayList<String> out = new ArrayList<>();
        try {
            for (LinkedHashMap<String, String> m : CoreTools.liveElements(ed)) {
                if (!isText(m)) continue;
                String name = m.get("name");
                if (names != null && !names.contains(name)) continue;
                String txt = m.get("Text") == null ? "" : m.get("Text");
                String ft = m.get("font");
                if (txt.isEmpty() || (ft != null && !ft.isEmpty())) continue; // custom fonts are not measured
                float fs = f(m, "Font Scale", 1f), w = f(m, "width", 0f), h = f(m, "height", 0f);
                if (w <= 0) continue;
                GlyphLayout gl = new GlyphLayout(font(), txt);
                float need = gl.width * fs, lineH = font().getLineHeight() * fs;
                int explicitLines = txt.split("\n", -1).length;
                if (need > w + 0.5f) {
                    int wraps = (int) Math.ceil(need / w);
                    out.add("TEXT '" + name + "' (\"" + shorten(txt) + "\", Font Scale " + trim(fs) + ") needs about " + Math.round(need + 6)
                            + " px of width but width is " + Math.round(w) + ": it will wrap onto ~" + wraps + " lines. Fix: set width >= " + Math.round(need + 6)
                            + " or Font Scale <= " + trim(fs * w / need * 0.95f) + ".");
                } else if (lineH * explicitLines > h + 0.5f && h > 0) {
                    out.add("TEXT '" + name + "' (\"" + shorten(txt) + "\") needs about " + Math.round(lineH * explicitLines) + " px of height but height is "
                            + Math.round(h) + " (it may be cut off or overlap what is below). Raise height or lower Font Scale.");
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** TEXT elements lying on top of an element that reacts to touch while the text itself does not. */
    static List<String> overlaps(LibgdxEditor ed, Project p, String scene) {
        ArrayList<String> out = new ArrayList<>();
        try {
            List<LinkedHashMap<String, String>> all = CoreTools.liveElements(ed);
            for (LinkedHashMap<String, String> t : all) {
                if (!isText(t)) continue;
                String tn = t.get("name");
                if (hasTouch(ed, p, scene, tn)) continue;
                float tx = f(t, "x", 0), ty = f(t, "y", 0), tw = f(t, "width", 0), th = f(t, "height", 0), tz = f(t, "z", 0);
                if (tw <= 0 || th <= 0) continue;
                for (LinkedHashMap<String, String> b : all) {
                    if (b == t || isText(b)) continue;
                    String bn = b.get("name");
                    float bz = f(b, "z", 0);
                    if (tz < bz) continue; // the other one is above the text: it gets the touch
                    float bw = f(b, "width", f(b, "radius", 0) * 2), bh = f(b, "height", f(b, "radius", 0) * 2);
                    if (bw <= 0 || bh <= 0) continue;
                    float bx = f(b, "x", 0), by = f(b, "y", 0);
                    float ix = Math.min(tx + tw, bx + bw) - Math.max(tx, bx), iy = Math.min(ty + th, by + bh) - Math.max(ty, by);
                    if (ix <= 0 || iy <= 0 || ix * iy < 0.3f * tw * th) continue;
                    if (!hasTouch(ed, p, scene, bn)) continue;
                    out.add("TEXT '" + tn + "' lies on top of '" + bn + "' (which has a touch event) and will swallow the touch. Fix: write the same event on the text too"
                            + " (events set with also_on:['" + tn + "']) or give the text a lower z than '" + bn + "'.");
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static boolean hasTouch(LibgdxEditor ed, Project p, String scene, String element) {
        try {
            String[] sb = LogicTools.bodyOf(p, ed, scene, element);
            for (String ev : TOUCH_EVENTS) if (LogicTools.hasCode(p.getEventPath(sb[0], sb[1], ev))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String shorten(String s) {
        s = s.replace("\n", "\\n");
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }

    private static String trim(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.2f", v);
    }
}
