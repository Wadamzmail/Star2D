package com.star4droid.desktop;

import java.io.File;

/** Tiny command line parser (no dependencies). */
final class Args {
    String project;            // folder or project.zip
    String scene = "scene1";
    File gameJar;
    File dataDir;
    boolean demo, selftest, version, help, fullscreen;
    String demoOrientation = "";   // "" = landscape, "portrait"
    float demoLong = 1280, demoShort = 720;
    float exitAfterSeconds = -1;
    String error;

    static Args parse(String[] a) {
        Args r = new Args();
        for (int i = 0; i < a.length; i++) {
            String s = a[i];
            switch (s) {
                case "--project": r.project = next(a, ++i, r); break;
                case "--scene": r.scene = next(a, ++i, r); break;
                case "--game-jar": { String v = next(a, ++i, r); if (v != null) r.gameJar = new File(v); break; }
                case "--data-dir": { String v = next(a, ++i, r); if (v != null) r.dataDir = new File(v); break; }
                case "--demo": r.demo = true; break;
                case "--demo-orientation": r.demoOrientation = "portrait".equalsIgnoreCase(next(a, ++i, r)) ? "portrait" : ""; break;
                case "--demo-logic": {
                    String v = next(a, ++i, r);
                    try {
                        String[] p = v.toLowerCase().split("x");
                        r.demoLong = Float.parseFloat(p[0]);
                        r.demoShort = Float.parseFloat(p[1]);
                    } catch (Exception e) {
                        r.error = "--demo-logic expects LONGxSHORT, e.g. 1280x720";
                    }
                    break;
                }
                case "--exit-after": try { r.exitAfterSeconds = Float.parseFloat(next(a, ++i, r)); } catch (Exception e) { r.error = "--exit-after expects seconds"; } break;
                case "--fullscreen": r.fullscreen = true; break;
                case "--selftest": r.selftest = true; break;
                case "--version": r.version = true; break;
                case "--help": case "-h": case "/?": r.help = true; break;
                default:
                    if (!s.startsWith("--") && r.project == null) r.project = s;   // run.sh MyGame.zip
                    else r.error = "Unknown option: " + s;
            }
        }
        return r;
    }

    private static String next(String[] a, int i, Args r) {
        if (i >= a.length) {
            r.error = "Missing value after " + a[i - 1];
            return null;
        }
        return a[i];
    }
}
