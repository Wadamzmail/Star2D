package com.star4droid.star2d.CodeEditor.smart;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.source.util.JavacTask;

/**
 * The compiler the analyzer talks to: the javac that ships with the app (the same one the Run
 * button uses), pointed at the same class path ({@code cp.jar} + {@code addition.jar}) and at the
 * project's {@code java} folder as source path. One instance is kept for the whole editor session.
 */
public final class JavaEnv {
    public final List<File> libs;
    public final File sourceRoot;
    /** "7" by default (the project's javac.version). */
    public final String level;
    /** false when text is edited that can't carry import lines (event code inlined in a method). */
    public boolean allowImports = true;
    /** Don't report errors on lines with lambdas / method references: the bundled javac (Java 7) can't parse them. */
    public boolean lenientLambda = true;

    private JavaCompiler compiler;
    private StandardJavaFileManager fm;
    private volatile ClassIndex index;

    public JavaEnv(List<File> libs, File sourceRoot, String level) {
        this.libs = new ArrayList<>(libs);
        this.sourceRoot = sourceRoot;
        this.level = level == null || level.length() == 0 ? "7" : level;
    }

    /** Lazy: reading the jar entry names takes a moment. */
    public ClassIndex index() {
        ClassIndex i = index;
        if (i == null) {
            synchronized (this) {
                if (index == null) index = new ClassIndex(libs, sourceRoot);
                i = index;
            }
        }
        return i;
    }

    public void warmUp() {
        index();
    }

    /** Forget cached files (call when the project's sources changed on disk). */
    public synchronized void refresh() {
        try {
            if (fm != null) fm.close();
        } catch (Exception ignored) {
        }
        fm = null;
        index = null;
    }

    private static JavaCompiler createCompiler() {
        try {
            return com.sun.tools.javac.api.JavacTool.create();
        } catch (Throwable t) {
            return javax.tools.ToolProvider.getSystemJavaCompiler();
        }
    }

    /** A new compilation over the given sources. Callers synchronize through {@link JavaAnalyzer}. */
    synchronized JavacTask task(List<JavaFileObject> units, DiagnosticListener<JavaFileObject> listener) throws Exception {
        if (compiler == null) compiler = createCompiler();
        if (fm == null) {
            fm = compiler.getStandardFileManager(null, null, null);
            if (!libs.isEmpty()) {
                try {
                    fm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, libs);
                } catch (Throwable ignored) {
                }
                fm.setLocation(StandardLocation.CLASS_PATH, libs);
            }
        }
        List<String> opt = new ArrayList<>();
        opt.add("-proc:none");
        opt.add("-implicit:none");
        opt.add("-source");
        opt.add(level);
        opt.add("-target");
        opt.add(level);
        opt.add("-nowarn");
        opt.add("-XDdiags=compact");
        return (JavacTask) compiler.getTask(null, fm, listener, opt, null, units);
    }

    // ------------------------------------------------------------------ project sources

    private static final class Cached {
        long modified, length;
        String text;
    }

    private final java.util.HashMap<String, Cached> sourceCache = new java.util.HashMap<>();

    /**
     * Every .java file of the project folder as a compilation unit, like the real build passes them
     * (the package line decides the class name, not the folder), except {@code exceptPath}.
     */
    synchronized List<JavaFileObject> projectUnits(String exceptPath) {
        List<JavaFileObject> out = new ArrayList<>();
        if (sourceRoot == null || !sourceRoot.isDirectory()) return out;
        collect(sourceRoot, exceptPath, out, 0);
        return out;
    }

    private void collect(File dir, String except, List<JavaFileObject> out, int depth) {
        if (depth > 14 || out.size() > 400) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                collect(f, except, out, depth + 1);
            } else if (f.getName().endsWith(".java")) {
                String p = f.getAbsolutePath();
                if (p.equals(except)) continue;
                Cached c = sourceCache.get(p);
                if (c == null || c.modified != f.lastModified() || c.length != f.length()) {
                    c = new Cached();
                    c.modified = f.lastModified();
                    c.length = f.length();
                    c.text = readText(f);
                    sourceCache.put(p, c);
                }
                out.add(new Src(p, c.text));
            }
        }
    }

    private static String readText(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            return new String(b, 0, off, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** In-memory source file. */
    public static JavaFileObject source(String path, String text) {
        return new Src(path, text);
    }

    private static final class Src extends SimpleJavaFileObject {
        private final String text;

        Src(String path, String text) {
            super(new File(path).toURI(), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
