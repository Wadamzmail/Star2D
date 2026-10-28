package com.star4droid.star2d.remote;

import android.content.Context;

import com.star4droid.star2d.ui.Tr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/** The two texts the user can copy into an AI chat. */
public final class RemoteCommands {
    private RemoteCommands() {}

    /** https://github.com/owner/repo of the project (no credentials), or "" when it has no remote. */
    public static String repoUrl(File projectDir) {
        if (projectDir == null) return "";
        File cfg = new File(projectDir, ".git/config");
        if (!cfg.exists()) return "";
        try (BufferedReader r = new BufferedReader(new FileReader(cfg))) {
            String line;
            boolean inOrigin = false;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (t.startsWith("[")) inOrigin = t.startsWith("[remote \"origin\"");
                else if (inOrigin && t.startsWith("url")) {
                    String u = t.substring(t.indexOf('=') + 1).trim();
                    // drop credentials: https://user:token@host/...
                    int at = u.indexOf('@');
                    int scheme = u.indexOf("://");
                    if (scheme > 0 && at > scheme) u = u.substring(0, scheme + 3) + u.substring(at + 1);
                    if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
                    return u;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** Two lines for Claude: the guide link and the request. All rules come from the server (connector instructions and AI.md). */
    public static String workCommand(Context c, String repoUrl) {
        return "Star2D: " + RemoteSettings.guideUrl(c) + "\nMy request: ";
    }

    /** The short one for any other AI. */
    public static String otherAiCommand(Context c) {
        return "use this link to understand my project : " + RemoteSettings.guideUrl(c) + "\n"
                + "(plain HTTP API, private, do not share: POST " + RemoteSettings.apiBase(c) + "/<tool> with the JSON arguments as the body)\n";
    }
}
