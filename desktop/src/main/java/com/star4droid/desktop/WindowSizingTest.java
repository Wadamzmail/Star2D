package com.star4droid.desktop;

/** Plain main() test (gradle :desktop:windowSizingTest). Exit code 1 when something is wrong. */
public final class WindowSizingTest {
    private static int failed = 0;

    private static void check(String name, boolean ok, String info) {
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  " + info));
        if (!ok) failed++;
    }

    private static void case_(String name, String or, float lw, float lh, int mw, int mh) {
        int[] s = WindowSizing.compute(or, lw, lh, mw, mh);
        float[] v = WindowSizing.viewportSize(or, lw, lh);
        String info = name + " -> " + s[0] + "x" + s[1] + " (viewport " + v[0] + "x" + v[1] + ", monitor " + mw + "x" + mh + ")";
        check(name + " same shape as the game viewport", (v[0] > v[1]) == (s[0] > s[1]), info);
        if (mw <= 0 || mh <= 0) { mw = 1280; mh = 720; }   // unknown monitor: the launcher assumes 1280x720
        float want = v[0] / v[1], got = (float) s[0] / s[1];
        check(name + " aspect ratio kept", Math.abs(want - got) / want < 0.01f, info);
        check(name + " fits 85%", s[0] <= mw * 0.85f + 1 && s[1] <= mh * 0.85f + 1, info);
        System.out.println("     " + info);
    }

    public static void main(String[] a) {
        case_("landscape 1280x720 on 1920x1080", "", 1280, 720, 1920, 1080);
        case_("landscape 'landscape' on 1366x768", "landscape", 1280, 720, 1366, 768);
        case_("portrait 1280/720 on 1920x1080", "portrait", 1280, 720, 1920, 1080);
        case_("portrait 1560/720 on 1366x768", "portrait", 1560, 720, 1366, 768);
        case_("portrait on 4K", "portrait", 1280, 720, 3840, 2160);
        for (String[] c : new String[][]{{"portrait", "1280", "720"}, {"", "1280", "720"}}) {
            int[] s = WindowSizing.compute(c[0], Float.parseFloat(c[1]), Float.parseFloat(c[2]), 1920, 1080);
            check("normal " + (c[0].isEmpty() ? "landscape" : "portrait") + " project is " + (c[0].isEmpty() ? "wide" : "tall"), c[0].isEmpty() ? s[0] > s[1] : s[1] > s[0], s[0] + "x" + s[1]);
        }
        case_("landscape on 4K (small game scaled up)", "", 640, 360, 3840, 2160);
        case_("portrait on a portrait monitor", "portrait", 1280, 720, 1080, 1920);
        case_("landscape on a portrait monitor", "", 1280, 720, 1080, 1920);
        case_("unknown monitor", "portrait", 1280, 720, 0, 0);
        case_("missing logic size (defaults)", "portrait", 0, 0, 1920, 1080);

        int[] p = WindowSizing.compute("portrait", 1280, 720, 1920, 1080);
        check("portrait 1280x720 on 1080p is 1:1 up to 85%", p[1] <= 918 && p[0] < p[1], p[0] + "x" + p[1]);
        int[] l = WindowSizing.compute("", 1280, 720, 1920, 1080);
        check("landscape 1280x720 on 1080p stays 1280x720", l[0] == 1280 && l[1] == 720, l[0] + "x" + l[1]);
        float[] v = WindowSizing.viewportSize("portrait", 1280, 720);
        check("portrait swaps like StageImp.init (width=short, height=long)", v[0] == 720 && v[1] == 1280, v[0] + "x" + v[1]);

        System.out.println(failed == 0 ? "\nALL OK" : "\n" + failed + " FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
