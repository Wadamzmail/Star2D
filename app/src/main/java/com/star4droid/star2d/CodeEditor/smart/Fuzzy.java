package com.star4droid.star2d.CodeEditor.smart;

/** Camel-hump / subsequence matching: "gPos" matches "getPosition", "ArL" matches "ArrayList". */
public final class Fuzzy {
    private Fuzzy() {}

    /** @return -1 when the pattern doesn't match, otherwise a score (bigger is better). */
    public static int score(String pat, String s) {
        int m = pat.length(), n = s.length();
        if (m == 0) return 0;
        if (m > n) return -1;
        // fast paths: exact / prefix
        if (s.startsWith(pat)) return 1000 - (n - m);
        if (s.regionMatches(true, 0, pat, 0, m)) return 900 - (n - m);

        // quick reject: must be a (case-insensitive) subsequence
        int j = 0;
        for (int i = 0; i < n && j < m; i++) if (eq(pat.charAt(j), s.charAt(i))) j++;
        if (j < m) return -1;

        // DP: best[i][j] = best score matching pat[0..j) inside s[0..i)
        final int NEG = Integer.MIN_VALUE / 2;
        int[] prev = new int[m + 1], cur = new int[m + 1];
        boolean[] prevRun = new boolean[m + 1], curRun = new boolean[m + 1];
        for (int k = 0; k <= m; k++) prev[k] = k == 0 ? 0 : NEG;
        for (int i = 1; i <= n; i++) {
            char c = s.charAt(i - 1);
            boolean boundary = i == 1 || isBoundary(s.charAt(i - 2), c);
            cur[0] = 0;
            curRun[0] = false;
            for (int k = 1; k <= m; k++) {
                // skip s[i-1]
                int best = prev[k] == NEG ? NEG : prev[k] - (k == m || i == 1 ? 0 : 1);
                boolean run = false;
                if (eq(pat.charAt(k - 1), c) && prev[k - 1] != NEG) {
                    int sc = prev[k - 1] + 10;
                    if (pat.charAt(k - 1) == c) sc += 3;
                    if (boundary) sc += 18;
                    if (i == 1) sc += 12;
                    if (k > 1 && prevRun[k - 1]) sc += 8;
                    if (sc > best) {
                        best = sc;
                        run = true;
                    }
                }
                cur[k] = best;
                curRun[k] = run;
            }
            int[] t = prev; prev = cur; cur = t;
            boolean[] tr = prevRun; prevRun = curRun; curRun = tr;
        }
        int r = prev[m];
        if (r <= NEG / 2) return -1;
        return Math.max(1, r) - (n - m) / 4;
    }

    private static boolean eq(char a, char b) {
        return a == b || Character.toLowerCase(a) == Character.toLowerCase(b);
    }

    private static boolean isBoundary(char prev, char c) {
        if (prev == '_' || prev == '$' || prev == '.') return true;
        return Character.isLowerCase(prev) && Character.isUpperCase(c)
                || Character.isLetter(prev) != Character.isLetter(c);
    }
}
