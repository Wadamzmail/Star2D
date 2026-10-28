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
 * Cheap check run after editing, so the AI learns immediately that a TEXT too narrow for its content wraps onto several lines.
 * All methods: GL thread.
 */
final class Warnings {
    private Warnings() {}

    private static BitmapFont font;

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

    private static String shorten(String s) {
        s = s.replace("\n", "\\n");
        return s.length() > 18 ? s.substring(0, 17) + "…" : s;
    }

    private static String trim(float v) {
        return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.2f", v);
    }
}
