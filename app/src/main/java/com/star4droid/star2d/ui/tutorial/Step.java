package com.star4droid.star2d.ui.tutorial;

import android.graphics.RectF;
import android.view.View;

/** One step of an interactive lesson. */
public class Step {
    public interface Cond { boolean ok(); }

    /** Where to point: a rectangle on the screen, the root view that receives the touches, and the view (if any). */
    public static class Found {
        public RectF r;
        public View root, view;

        public Found(RectF r, View root, View view) {
            this.r = r;
            this.root = root;
            this.view = view;
        }
    }

    public interface Finder { Found find(); }

    public String text;
    public Finder target, target2;
    public Cond done;
    public Runnable enter, auto, tick;
    /** Touches go everywhere (no blocking), e.g. while playing the game. */
    public boolean free;
    /** The back key works normally in this step. */
    public boolean allowBack;
    /** Show a drag gesture from target to target2. */
    public boolean drag;

    public Step(String text) { this.text = text; }

    public Step at(String tag) { target = Targets.view(tag); return this; }
    public Step at(Finder f) { target = f; return this; }
    public Step dragTo(Finder f) { target2 = f; drag = true; return this; }
    public Step until(Cond c) { done = c; return this; }
    public Step onEnter(Runnable r) { enter = r; return this; }
    public Step auto(Runnable r) { auto = r; return this; }
    public Step onTick(Runnable r) { tick = r; return this; }
    public Step free() { free = true; return this; }
    public Step back() { allowBack = true; return this; }

    public boolean isInfo() { return done == null; }
}
