package com.star4droid.star2d.CodeEditor.smart;

/** Cheap text helpers that don't need a parser. */
public final class Lexical {
    private Lexical() {}

    public static final int CODE = 0, LINE_COMMENT = 1, BLOCK_COMMENT = 2, STRING = 3, CHAR = 4;

    /** Lexical state at {@code upto} (0 = code, otherwise comment / string / char literal). */
    public static int stateAt(CharSequence t, int upto) {
        int st = CODE;
        int n = Math.min(upto, t.length());
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            char d = i + 1 < t.length() ? t.charAt(i + 1) : 0;
            switch (st) {
                case CODE:
                    if (c == '/' && d == '/') { st = LINE_COMMENT; i++; }
                    else if (c == '/' && d == '*') { st = BLOCK_COMMENT; i++; }
                    else if (c == '"') st = STRING;
                    else if (c == '\'') st = CHAR;
                    break;
                case LINE_COMMENT:
                    if (c == '\n') st = CODE;
                    break;
                case BLOCK_COMMENT:
                    if (c == '*' && d == '/') { st = CODE; i++; }
                    break;
                case STRING:
                    if (c == '\\') i++;
                    else if (c == '"' || c == '\n') st = CODE;
                    break;
                case CHAR:
                    if (c == '\\') i++;
                    else if (c == '\'' || c == '\n') st = CODE;
                    break;
            }
        }
        return st;
    }

    public static boolean isIdent(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    /** Start of the identifier that ends at {@code off}. */
    public static int identStart(CharSequence t, int off) {
        int i = off;
        while (i > 0 && isIdent(t.charAt(i - 1))) i--;
        return i;
    }

    public static int identEnd(CharSequence t, int off) {
        int i = off;
        while (i < t.length() && isIdent(t.charAt(i))) i++;
        return i;
    }

    /** Index of the previous non-whitespace character before {@code off} (exclusive), or -1. */
    public static int prevNonSpace(CharSequence t, int off) {
        int i = off - 1;
        while (i >= 0 && Character.isWhitespace(t.charAt(i))) i--;
        return i;
    }

    public static int nextNonSpace(CharSequence t, int off) {
        int i = off;
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        return i;
    }

    public static int lineStart(CharSequence t, int off) {
        int i = Math.min(off, t.length());
        while (i > 0 && t.charAt(i - 1) != '\n') i--;
        return i;
    }

    public static int lineEnd(CharSequence t, int off) {
        int i = Math.max(0, off);
        while (i < t.length() && t.charAt(i) != '\n') i++;
        return i;
    }

    /** 1-based line of an offset. */
    public static int lineOf(CharSequence t, int off) {
        int line = 1;
        int n = Math.min(off, t.length());
        for (int i = 0; i < n; i++) if (t.charAt(i) == '\n') line++;
        return line;
    }

    /** Offsets of the starts of all lines (index = 0-based line). */
    public static int[] lineStarts(CharSequence t) {
        int count = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') count++;
        int[] r = new int[count];
        int k = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') r[k++] = i + 1;
        return r;
    }

    /** Replace everything except line breaks by spaces in [from, to). Keeps all offsets and lines. */
    public static void blank(StringBuilder sb, int from, int to) {
        for (int i = Math.max(0, from); i < Math.min(to, sb.length()); i++) {
            char c = sb.charAt(i);
            if (c != '\n' && c != '\r') sb.setCharAt(i, ' ');
        }
    }

    /** Remove the java package prefix of every type name: java.util.List&lt;java.lang.String&gt; -> List&lt;String&gt;. */
    public static String simpleTypes(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        int i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isJavaIdentifierStart(c)) {
                int j = i;
                int lastDot = -1;
                while (j < n && (Character.isJavaIdentifierPart(s.charAt(j)) || (s.charAt(j) == '.' && j + 1 < n && Character.isJavaIdentifierStart(s.charAt(j + 1))))) {
                    if (s.charAt(j) == '.') lastDot = j;
                    j++;
                }
                String word = s.substring(i, j);
                // keep "a.b.Outer.Inner" as "Outer.Inner": drop only lower-case package segments
                String[] seg = word.split("\\.");
                int firstType = 0;
                while (firstType < seg.length - 1 && seg[firstType].length() > 0 && Character.isLowerCase(seg[firstType].charAt(0))) firstType++;
                StringBuilder w = new StringBuilder();
                for (int k = firstType; k < seg.length; k++) {
                    if (w.length() > 0) w.append('.');
                    w.append(seg[k]);
                }
                out.append(w);
                i = j;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
