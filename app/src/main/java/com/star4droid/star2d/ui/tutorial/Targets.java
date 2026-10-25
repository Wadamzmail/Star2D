package com.star4droid.star2d.ui.tutorial;

import android.app.Activity;
import android.graphics.RectF;
import android.opengl.GLSurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.blocks.BlockCanvas;
import com.star4droid.star2d.ui.blocks.BlockEditorActivity;
import com.star4droid.star2d.ui.blocks.BlockModel;

/** Finders for lesson steps: views by tag, elements on the editor canvas, blocks on the block canvas. */
public final class Targets {
    private Targets() {}

    /** Screen rectangle of a view; flips (scaleX = -1 on RTL arrows) and scales are applied properly. */
    static RectF screenRect(View v) {
        RectF r = new RectF(0, 0, v.getWidth(), v.getHeight());
        v.getMatrix().mapRect(r);
        r.offset(v.getLeft(), v.getTop());
        if (v.getParent() instanceof View) {
            View p = (View) v.getParent();
            int[] l = new int[2];
            p.getLocationOnScreen(l);
            r.offset(l[0] - p.getScrollX(), l[1] - p.getScrollY());
        } else {
            int[] l = new int[2];
            v.getLocationOnScreen(l);
            r.offsetTo(l[0], l[1]);
        }
        return r;
    }

    /** A tagged view (see {@link Tut#tag}). */
    public static Step.Finder view(String tag) {
        return () -> {
            View v = Tut.find(tag);
            if (v == null || v.getWidth() == 0) return null;
            return new Step.Found(screenRect(v), v.getRootView(), v);
        };
    }

    /** The first finder that finds something. */
    public static Step.Finder first(Step.Finder... fs) {
        return () -> {
            for (Step.Finder f : fs) {
                if (f == null) continue;
                Step.Found r = f.find();
                if (r != null) return r;
            }
            return null;
        };
    }

    /** Both views together (e.g. a text box and its OK button). */
    public static Step.Finder union(String a, String b) {
        return () -> {
            Step.Found fa = view(a).find(), fb = view(b).find();
            if (fa == null) return fb;
            if (fb == null) return fa;
            RectF r = new RectF(fa.r);
            r.union(fb.r);
            return new Step.Found(r, fa.root, fa.view);
        };
    }

    /** All the tagged views that exist, together. */
    public static Step.Finder unionOf(String... tags) {
        return () -> {
            Step.Found out = null;
            for (String t : tags) {
                Step.Found f = view(t).find();
                if (f == null) continue;
                if (out == null) out = new Step.Found(new RectF(f.r), f.root, f.view);
                else if (f.root == out.root) out.r.union(f.r);
            }
            return out;
        };
    }

    // ---------------------------------------------------------------- editor canvas (LibGDX)

    private static View glView(Activity a) {
        if (a == null) return null;
        return findGl(a.getWindow().getDecorView());
    }

    private static View findGl(View v) {
        if (v instanceof GLSurfaceView) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findGl(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** An element on the scene canvas (from the last GL snapshot). */
    public static Step.Finder element(String name) {
        return () -> {
            Activity a = Tutorial.activity();
            View gl = glView(a);
            if (gl == null || !gl.isShown()) return null;
            for (Bridge.ElemState e : Tutorial.elements()) {
                if (!e.name.equals(name) || e.r - e.l < 2) continue;
                RectF g = screenRect(gl);
                RectF r = new RectF(g.left + e.l, g.top + e.t, g.left + e.r, g.top + e.b);
                // keep a reasonable size and inside the GL view
                if (r.width() < 40) r.inset(-(40 - r.width()) / 2, 0);
                if (r.height() < 40) r.inset(0, -(40 - r.height()) / 2);
                if (!r.intersect(g)) return null;
                return new Step.Found(r, a.getWindow().getDecorView(), null);
            }
            return null;
        };
    }

    // ---------------------------------------------------------------- block canvas

    interface CanvasRect { RectF get(BlockCanvas c, BlockModel model); }

    private static Step.Finder blocks(CanvasRect cr) {
        return () -> {
            BlockEditorActivity b = BlockEditorActivity.current;
            if (b == null || b.isFinishing() || b.canvas() == null) return null;
            BlockCanvas c = b.canvas();
            if (!c.isShown() || c.getWidth() == 0) return null;
            RectF v = cr.get(c, b.model());
            if (v == null) return null;
            if (!c.isDragging()) c.reveal(v);
            v = cr.get(c, b.model());
            if (v == null) return null;
            RectF s = screenRect(c);
            RectF r = new RectF(v.left + s.left, v.top + s.top, v.right + s.left, v.bottom + s.top);
            if (!r.intersect(s)) return null;
            return new Step.Found(r, b.getWindow().getDecorView(), null);
        };
    }

    public static Step.Finder block(String title) {
        return blocks((c, m) -> c.viewRectOfNode(c.findNode(title)));
    }

    public static Step.Finder field(String title, int index) {
        return blocks((c, m) -> c.viewRectOfField(c.findNode(title), index));
    }

    /** A field inside value blocks of a block field (path = inner slot indexes). */
    public static Step.Finder path(String title, int index, int... path) {
        return blocks((c, m) -> path.length == 0 ? c.viewRectOfField(c.findNode(title), index) : c.viewRectOfPath(c.findNode(title), index, path));
    }

    /** An event parameter chip (current, body2…) on the first block. */
    public static Step.Finder rootParam(String name) {
        return blocks((c, m) -> c.viewRectOfRootParam(name));
    }

    /** Under the last block of the root stack. */
    public static Step.Finder dropAfterRoot() {
        return blocks((c, m) -> m == null ? null : c.viewDropAfter(m.root()));
    }

    /** Inside a C-block ("then" = 1, "else" = 2). */
    public static Step.Finder mouth(String title, int slot) {
        return blocks((c, m) -> c.viewMouth(c.findNode(title), slot));
    }
}
