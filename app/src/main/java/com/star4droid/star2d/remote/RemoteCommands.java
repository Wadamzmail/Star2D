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

    /** The long order: connector, guide, repo, rules. The user adds their request after it. */
    public static String workCommand(Context c, String repoUrl) {
        String server = RemoteSettings.server(c);
        StringBuilder sb = new StringBuilder();
        sb.append("[Star2D work order]\n");
        sb.append("My Star2D game editor runs on my Android phone, and you control it through its connector.\n");
        sb.append("Connector (private link, do not repeat it): ").append(RemoteSettings.connectorUrl(c)).append("\n");
        sb.append("Read this guide first (rules and tool list): ").append(server).append("/AI.md\n");
        sb.append("Then call the tool read_docs (index first, then the topics you need) to learn the engine.\n");
        if (repoUrl != null && !repoUrl.isEmpty())
            sb.append("My repo: ").append(repoUrl).append(" (engine notes: ENGINE.md and .star2d/docs/index.md; if you cannot open the repo, read_docs has the same text)\n");
        sb.append("\nRules:\n");
        sb.append("1. Before your first tool call, tell me this sentence (in my language): \"")
                .append(Tr.t("rc_open_now", "Open the editor now and keep it open, Claude is working in the background"))
                .append("\". Then call status; if the app is not connected, call wait_for_app until it is.\n");
        sb.append("2. Everything (editing, building, running, testing) happens on my phone through the tools. Never compile, run or test the game in your own sandbox or server.\n");
        sb.append("3. Use visual blocks for logic (events with blocks). Create blocks or categories with define_blocks when needed. Raw Java only if no block can do it.\n");
        sb.append("4. Show a plan with set_plan, update it with update_task, verify with build / run_test / screenshot, and end with finish_work.\n");
        sb.append("5. Use the batch tools; every call counts against a shared daily limit.\n");
        sb.append("\nMy request:\n");
        return sb.toString();
    }

    /** The short one for any other AI. */
    public static String otherAiCommand(Context c) {
        String server = RemoteSettings.server(c);
        return "use this link to understand my project : " + server + "/AI.md\n"
                + "(plain HTTP API, private, do not share: POST " + RemoteSettings.apiBase(c) + "/<tool> with the JSON arguments as the body)\n";
    }
}
