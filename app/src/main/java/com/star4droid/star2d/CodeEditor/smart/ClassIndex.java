package com.star4droid.star2d.CodeEditor.smart;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Names of the classes on the compile classpath (jar entry names, nothing is parsed) plus the
 * project's own source files. Built once in the background, then cached.
 */
public final class ClassIndex {
    /** simple name -> fully qualified names */
    private final Map<String, List<String>> bySimple = new HashMap<>();
    /** package -> simple names of its classes */
    private final Map<String, Set<String>> classesOf = new HashMap<>();
    /** package -> direct sub packages (last segment) */
    private final Map<String, Set<String>> subPackages = new HashMap<>();
    private final Set<String> projectFqns = new HashSet<>();

    private static final String[] NOISE = {
            "sun.", "libcore.", "dalvik.", "com.android.internal.", "org.apache.harmony.", "com.sun.", "junit.",
            "java.awt.", "javax.swing.", "javax.imageio.", "javax.print.", "javax.sound.", "org.omg.", "javax.accessibility."};

    public ClassIndex(List<File> jars, File sourceRoot) {
        for (File j : jars) addJar(j);
        if (sourceRoot != null) addSources(sourceRoot, "", 0);
    }

    private void addJar(File f) {
        if (f == null || !f.isFile()) return;
        ZipFile z = null;
        try {
            z = new ZipFile(f);
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName();
                if (!name.endsWith(".class") || name.indexOf('$') >= 0) continue;
                String fqn = name.substring(0, name.length() - 6).replace('/', '.');
                if (fqn.endsWith("package-info") || fqn.endsWith("module-info")) continue;
                add(fqn, false);
            }
        } catch (Exception ignored) {
        } finally {
            try { if (z != null) z.close(); } catch (Exception ignored) {}
        }
    }

    private void addSources(File dir, String pkg, int depth) {
        if (depth > 12) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            if (f.isDirectory()) addSources(f, pkg.isEmpty() ? n : pkg + "." + n, depth + 1);
            else if (n.endsWith(".java") && !pkg.isEmpty()) {
                add(pkg + "." + n.substring(0, n.length() - 5), true);
            }
        }
    }

    private void add(String fqn, boolean project) {
        int dot = fqn.lastIndexOf('.');
        if (dot < 0) return;
        String pkg = fqn.substring(0, dot), simple = fqn.substring(dot + 1);
        if (!project) for (String n : NOISE) if (fqn.startsWith(n)) return;
        List<String> l = bySimple.get(simple);
        if (l == null) bySimple.put(simple, l = new ArrayList<>(2));
        if (!l.contains(fqn)) l.add(fqn);
        Set<String> c = classesOf.get(pkg);
        if (c == null) classesOf.put(pkg, c = new TreeSet<>());
        c.add(simple);
        if (project) projectFqns.add(fqn);
        // package chain
        String p = pkg;
        while (true) {
            int d = p.lastIndexOf('.');
            String parent = d < 0 ? "" : p.substring(0, d), seg = d < 0 ? p : p.substring(d + 1);
            Set<String> s = subPackages.get(parent);
            if (s == null) subPackages.put(parent, s = new TreeSet<>());
            if (!s.add(seg)) break;
            if (d < 0) break;
            p = parent;
        }
    }

    public Set<String> simpleNames() {
        return bySimple.keySet();
    }

    public List<String> fqnsOf(String simple) {
        List<String> l = bySimple.get(simple);
        return l == null ? Collections.<String>emptyList() : l;
    }

    public Set<String> classesIn(String pkg) {
        Set<String> s = classesOf.get(pkg);
        return s == null ? Collections.<String>emptySet() : s;
    }

    public Set<String> subPackagesOf(String pkg) {
        Set<String> s = subPackages.get(pkg);
        return s == null ? Collections.<String>emptySet() : s;
    }

    public boolean isPackage(String pkg) {
        return classesOf.containsKey(pkg) || subPackages.containsKey(pkg);
    }

    public boolean isProject(String fqn) {
        return projectFqns.contains(fqn);
    }

    /** Relative importance of a class when several are offered. Higher = more likely wanted. */
    public static int priority(String fqn) {
        if (fqn.startsWith("com.star4droid.")) return 30;
        if (fqn.startsWith("com.badlogic.gdx.math.") || fqn.startsWith("com.badlogic.gdx.physics.box2d.")) return 26;
        if (fqn.startsWith("com.badlogic.")) return 22;
        if (fqn.startsWith("box2dLight.")) return 20;
        if (fqn.startsWith("java.lang.") || fqn.startsWith("java.util.") && fqn.indexOf('.', 10) < 0) return 18;
        if (fqn.startsWith("java.io.") || fqn.startsWith("java.math.") || fqn.startsWith("java.text.")) return 12;
        if (fqn.startsWith("android.")) return 8;
        if (fqn.startsWith("java.") || fqn.startsWith("javax.")) return 4;
        return 0;
    }
}
