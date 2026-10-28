package com.star4droid.star2d.CodeEditor.smart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remembers where each user-written piece (an event's blocks / code, a script) was pasted into a
 * generated scene file, so a compiler error on that file can be traced back to the block or the
 * line the user actually wrote. It works with comment markers instead of line numbers because the
 * generated source is re-formatted afterwards:
 *
 * <pre>
 *   /*@s:3*&#47;  ...  /*@b0*&#47; stmt  /*@b1*&#47; stmt ... /*@e:3*&#47;
 * </pre>
 *
 * Pure Java; independent of the compiler that reported the error (javac, ecj, live checker).
 */
public final class SourceTrace {
    /** One pasted piece. */
    public static final class Segment {
        public int id;
        /** The file the user edits: .code / .java / script file. */
        public String file;
        /** Block file when the piece came from blocks (and the generated code matched them), else null. */
        public String visual;
        /** What was pasted (original text, before formatting). */
        public String text = "";
        /** "scene1 › box › onClick" */
        public String origin = "";
        /** First/last line (1-based) of the piece in the final generated text. */
        public int startLine, endLine;
    }

    private static final class Mark {
        final int off;
        final char kind; // s e b
        final int id;

        Mark(int off, char kind, int id) {
            this.off = off;
            this.kind = kind;
            this.id = id;
        }
    }

    private static final Pattern MARK = Pattern.compile("/\\*@([seb])[:]?(\\d+)\\*/");

    public final String sceneFile;
    private final List<Segment> segments = new ArrayList<>();
    private final List<Mark> marks = new ArrayList<>();
    private String finalText = "";

    public SourceTrace(String sceneFile) {
        this.sceneFile = sceneFile;
    }

    public synchronized Segment add(String file, String visual, String text, String origin) {
        Segment s = new Segment();
        s.id = segments.size();
        s.file = file;
        s.visual = visual;
        s.text = text == null ? "" : text;
        s.origin = origin == null ? "" : origin;
        segments.add(s);
        return s;
    }

    /** Text to paste into the generated source for this segment. */
    public static String wrap(Segment s, String body) {
        return "/*@s:" + s.id + "*/\n" + body + "\n/*@e:" + s.id + "*/\n";
    }

    /** Call with the final text that is written to disk; indexes the markers. */
    public synchronized void finish(String text) {
        finalText = text == null ? "" : text;
        marks.clear();
        Matcher m = MARK.matcher(finalText);
        int[] ls = Lexical.lineStarts(finalText);
        while (m.find()) {
            char k = m.group(1).charAt(0);
            int id = Integer.parseInt(m.group(2));
            marks.add(new Mark(m.start(), k, id));
            if (id >= 0 && id < segments.size() && k != 'b') {
                Segment s = segments.get(id);
                int line = lineOf(ls, m.start());
                if (k == 's') s.startLine = line;
                else s.endLine = line;
            }
        }
    }

    private static int lineOf(int[] starts, int off) {
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= off) lo = mid;
            else hi = mid - 1;
        }
        return lo + 1;
    }

    /** Offset of the start of a 1-based line in the final text. */
    private int offsetOfLine(int line) {
        int[] ls = Lexical.lineStarts(finalText);
        if (line < 1) return 0;
        if (line > ls.length) return finalText.length();
        return ls[line - 1];
    }

    /** Fill the target fields of {@code e} (an error of the generated file this trace belongs to). */
    public synchronized BuildError resolve(BuildError e) {
        BuildError r = e;
        int off = e.start >= 0 ? e.start : offsetOfLine(e.line) + Math.max(0, e.column - 1);
        // walk the markers up to the error
        ArrayList<Integer> stack = new ArrayList<>();
        int block = -1;
        HashMap<Integer, Integer> lastBlock = new HashMap<>();
        for (Mark m : marks) {
            if (m.off > off) break;
            if (m.kind == 's') {
                stack.add(m.id);
            } else if (m.kind == 'e') {
                int i = stack.lastIndexOf(m.id);
                if (i >= 0) {
                    while (stack.size() > i) stack.remove(stack.size() - 1);
                }
            } else if (m.kind == 'b') {
                if (!stack.isEmpty()) lastBlock.put(stack.get(stack.size() - 1), m.id);
            }
        }
        if (stack.isEmpty()) {
            r.target = BuildError.TARGET_NONE;
            return r;
        }
        Segment seg = segments.get(stack.get(stack.size() - 1));
        r.origin = seg.origin;
        Integer b = lastBlock.get(seg.id);
        if (seg.visual != null && b != null) {
            r.target = BuildError.TARGET_BLOCK;
            r.targetFile = seg.visual;
            r.blockIndex = b;
            return r;
        }
        r.target = BuildError.TARGET_JAVA;
        r.targetFile = seg.file;
        // find the same line inside the original text
        int[] ls = Lexical.lineStarts(finalText);
        int errLine = lineOf(ls, off);
        int ts = offsetOfLine(errLine), te = Lexical.lineEnd(finalText, ts);
        String errText = norm(finalText.substring(ts, Math.min(te, finalText.length())));
        String[] src = seg.text.split("\n", -1);
        int expected = Math.max(0, errLine - seg.startLine - 1);
        int best = -1, bestDist = Integer.MAX_VALUE;
        if (errText.length() > 1) {
            for (int i = 0; i < src.length; i++) {
                String n = norm(src[i]);
                if (n.length() == 0) continue;
                if (n.equals(errText) || (errText.length() > 6 && (n.contains(errText) || errText.contains(n)))) {
                    int d = Math.abs(i - expected);
                    if (d < bestDist) {
                        bestDist = d;
                        best = i;
                    }
                }
            }
        }
        if (best < 0) best = Math.min(expected, Math.max(0, src.length - 1));
        r.targetLine = best + 1;
        String line = src.length > best ? src[best] : "";
        int first = 0;
        while (first < line.length() && Character.isWhitespace(line.charAt(first))) first++;
        r.targetColumn = first + 1;
        r.targetEndColumn = Math.max(first + 2, line.length() + 1);
        return r;
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        // drop our own markers
        return MARK.matcher(sb).replaceAll("");
    }

    // ---------------------------------------------------------------- registry (last generation)

    private static final Map<String, SourceTrace> LAST = Collections.synchronizedMap(new HashMap<String, SourceTrace>());

    public static void remember(SourceTrace t) {
        LAST.put(t.sceneFile, t);
    }

    public static SourceTrace of(String sceneFile) {
        return LAST.get(sceneFile);
    }

    /** Resolve all errors that belong to a generated scene file. Others get a plain Java target. */
    public static void resolveAll(List<BuildError> errors) {
        for (BuildError e : errors) {
            SourceTrace t = e.file == null ? null : LAST.get(e.file);
            if (t != null) t.resolve(e);
            if (e.target == BuildError.TARGET_NONE && e.file != null) {
                e.target = BuildError.TARGET_JAVA;
                e.targetFile = e.file;
                e.targetLine = e.line;
                e.targetColumn = e.column;
            }
        }
    }
}
