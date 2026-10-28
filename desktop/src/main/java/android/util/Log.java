package android.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Desktop shim: the shared runtime sources call android.util.Log (PropertySet, GroupFollower).
 * Only what they use; prints to stderr. Compiled into the desktop module only, never into the Android template.
 */
public final class Log {
    private Log() {}

    public static int v(String tag, String msg) { return print("V", tag, msg, null); }
    public static int d(String tag, String msg) { return print("D", tag, msg, null); }
    public static int i(String tag, String msg) { return print("I", tag, msg, null); }
    public static int w(String tag, String msg) { return print("W", tag, msg, null); }
    public static int w(String tag, Throwable tr) { return print("W", tag, "", tr); }
    public static int e(String tag, String msg) { return print("E", tag, msg, null); }
    public static int e(String tag, String msg, Throwable tr) { return print("E", tag, msg, tr); }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static int print(String level, String tag, String msg, Throwable tr) {
        System.err.println(level + "/" + tag + ": " + msg + (tr == null ? "" : "\n" + getStackTraceString(tr)));
        return 0;
    }
}
