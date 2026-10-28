package com.star4droid.star2d.CodeEditor.smart;

/**
 * One compiler/analyzer message in a compiler-independent form (javac, ecj and the live checker
 * all produce this). Pure Java: no Android classes.
 */
public final class BuildError {
    /** What the error points at once it has been traced back. */
    public static final int TARGET_NONE = 0, TARGET_JAVA = 1, TARGET_BLOCK = 2;

    public String file;          // absolute path of the file the compiler reported
    public int line = 1;         // 1-based
    public int column = 1;       // 1-based
    public int start = -1;       // character offsets in that file (end exclusive), -1 when unknown
    public int end = -1;
    public String message = "";
    public boolean warning;

    // ---- trace result (see SourceTrace)
    public int target = TARGET_NONE;
    /** File to open for TARGET_JAVA / the .visual file for TARGET_BLOCK. */
    public String targetFile;
    /** TARGET_JAVA: 1-based line inside targetFile (editor coordinates). */
    public int targetLine = 1;
    public int targetColumn = 1;
    public int targetEndColumn = -1;
    /** TARGET_BLOCK: index of the block in the model's traversal order and its title (sanity check). */
    public int blockIndex = -1;
    public String blockTitle = "";
    /** Short human readable origin, e.g. "scene1 › box › onClick". */
    public String origin = "";

    public BuildError() {}

    public BuildError(String file, int line, int column, String message) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.message = message == null ? "" : message;
    }

    public BuildError copy() {
        BuildError e = new BuildError(file, line, column, message);
        e.start = start;
        e.end = end;
        e.warning = warning;
        e.target = target;
        e.targetFile = targetFile;
        e.targetLine = targetLine;
        e.targetColumn = targetColumn;
        e.targetEndColumn = targetEndColumn;
        e.blockIndex = blockIndex;
        e.blockTitle = blockTitle;
        e.origin = origin;
        return e;
    }

    /** First line of the message (javac messages often continue with a code excerpt). */
    public String headline() {
        int i = message.indexOf('\n');
        return (i < 0 ? message : message.substring(0, i)).trim();
    }

    @Override
    public String toString() {
        return (warning ? "warning " : "error ") + file + ":" + line + ":" + column + " " + headline();
    }
}
