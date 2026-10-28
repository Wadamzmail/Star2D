package com.star4droid.desktop;

/**
 * Window size of a game, a pure function (no libgdx, easy to test).
 *
 * The game is drawn in a FitViewport of the project's logic size (see StageImp.init): in the config logicWidth is the long
 * side and logicHeight the short side, and a PORTRAIT game swaps them when it is used. So a portrait project gets a
 * tall window and a landscape one a wide window, with the same aspect ratio as on the phone (the viewport letterboxes
 * when the user resizes the window).
 */
public final class WindowSizing {
    private WindowSizing() {}

    /** Share of the monitor the window may use. */
    public static final float MAX_SHARE = 0.85f;
    /** A game smaller than this share of the monitor (in its limiting direction) is scaled up to it. */
    public static final float MIN_SHARE = 0.5f;

    public static boolean isLandscape(String or) {
        return or == null || or.isEmpty() || or.equals("landscape");
    }

    /**
     * The viewport size StageImp uses: the raw config values, swapped when the project is portrait.
     * (0 values fall back to the runtime's defaults 720 x 1560.)
     */
    public static float[] viewportSize(String or, float logicLong, float logicShort) {
        float w = logicLong <= 0 ? 720 : logicLong;
        float h = logicShort <= 0 ? 1560 : logicShort;
        if (!isLandscape(or)) {
            float t = h;
            h = w;
            w = t;
        }
        return new float[]{w, h};
    }

    /**
     * @param or          config "or": "" / "landscape" = landscape, "portrait" = portrait
     * @param logicLong   config logicWidth (the long side)
     * @param logicShort  config logicHeight (the short side)
     * @return {windowWidth, windowHeight} in pixels, aspect ratio of the game, at most 85 % of the monitor
     */
    public static int[] compute(String or, float logicLong, float logicShort, int monitorW, int monitorH) {
        float[] v = viewportSize(or, logicLong, logicShort);
        float w = v[0], h = v[1];
        if (monitorW <= 0 || monitorH <= 0) {
            monitorW = 1280;
            monitorH = 720;
        }
        float maxW = monitorW * MAX_SHARE, maxH = monitorH * MAX_SHARE;
        float fit = Math.min(maxW / w, maxH / h);             // biggest scale that fits
        float scale = Math.min(1f, fit);                      // never bigger than the logic size...
        float minScale = Math.min(fit, Math.min(monitorW * MIN_SHARE / w, monitorH * MIN_SHARE / h));
        if (scale < minScale) scale = minScale;               // ...unless it would look tiny
        int ww = Math.max(1, Math.round(w * scale));
        int hh = Math.max(1, Math.round(h * scale));
        return new int[]{ww, hh};
    }
}
