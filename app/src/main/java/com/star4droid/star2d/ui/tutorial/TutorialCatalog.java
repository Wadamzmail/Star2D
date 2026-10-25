package com.star4droid.star2d.ui.tutorial;

import android.app.Activity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Placeholder; filled by the tutorial system. */
public final class TutorialCatalog {
    public static class Info { public String id, title, tag, description; }
    public interface OnReady { void on(File projectDir); }
    public static List<Info> all(Activity a) { return new ArrayList<>(); }
    public static void start(Activity a, String id, File projectsDir, OnReady ready) {}
}
