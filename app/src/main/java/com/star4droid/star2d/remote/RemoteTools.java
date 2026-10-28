package com.star4droid.star2d.remote;

import com.google.gson.JsonObject;
import com.star4droid.star2d.ui.Bridge;

/** Entry point of every call that arrives from the server. Runs on the client's worker thread. */
public final class RemoteTools {
    private RemoteTools() {}

    /** Where to read when a tool fails: one short line, so the AI loads that section instead of whole topics. */
    private static String docHint(String tool) {
        switch (tool) {
            case "elements": return "read_docs {topic:'elements', section:'<TYPE>'}";
            case "layout_check": return "read_docs {topic:'ui-input', section:'Designing UI'}";
            case "events": return "read_docs {topic:'events'} and {topic:'blocks'}";
            case "define_blocks": case "block_catalog": return "read_docs {topic:'blocks', section:'define_blocks'}";
            case "create_image": case "create_sound": case "import_asset": return "read_docs {topic:'assets', section:'paths'}";
            case "game_input": case "run_test": case "game_state": case "screenshot": return "read_docs {topic:'testing'}";
            case "build": case "run_game": return "read_docs {topic:'pitfalls'} and {search:'<error text>'}";
            case "scenes": return "read_docs {topic:'scenes'}";
            case "variables": return "read_docs {topic:'variables'}";
            case "joints": return "read_docs {topic:'joints'}";
            case "files": return "read_docs {topic:'overview'}";
            default: return null;
        }
    }

    public static ToolResult run(String tool, JsonObject args) {
        ToolResult r = dispatch(tool, args);
        try {
            String hint = docHint(tool);
            boolean failed = r.isError;
            if (!failed && r.content.size() > 0 && r.content.get(0).getAsJsonObject().has("text")) {
                String t = r.content.get(0).getAsJsonObject().get("text").getAsString();
                failed = t.contains("\"ok\":false");
            }
            if (failed && hint != null) r.addText("Docs: " + hint);
        } catch (Throwable ignored) {
        }
        return r;
    }

    private static ToolResult dispatch(String tool, JsonObject args) {
        if (args == null) args = new JsonObject();
        try {
            // everything but status/docs counts as "Claude is working" for the idle timer
            if (!tool.equals("status") && !tool.equals("read_docs")) RemoteSession.touch();
            switch (tool) {
                case "status": return CoreTools.status();
                case "read_docs": return CoreTools.readDocs(args);
                case "set_plan": return CoreTools.setPlan(args);
                case "update_task": return CoreTools.updateTask(args);
                case "finish_work": return CoreTools.finishWork(args);
            }
            // from here on a project must be open
            if (!projectOpen()) {
                return ToolResult.error("No project is open in the Star2D editor. Ask the user to open (or create) the project, keeping the editor on screen, then call status.");
            }
            switch (tool) {
                case "project_info": return CoreTools.projectInfo();
                case "scene_dump": return CoreTools.sceneDump(args);
                case "get_elements": return CoreTools.getElements(args);
                case "layout_check": return LayoutCheck.check(args);
                case "scenes": return EditTools.scenes(args);
                case "elements": return EditTools.elements(args);
                case "joints": return EditTools.joints(args);
                case "variables": return EditTools.variables(args);
                case "editor": return EditTools.editor(args);
                case "block_catalog": return LogicTools.blockCatalog(args);
                case "events": return LogicTools.events(args);
                case "define_blocks": return LogicTools.defineBlocks(args);
                case "files": return FileTools.files(args);
                case "create_image": return FileTools.createImage(args);
                case "create_sound": return FileTools.createSound(args);
                case "import_asset": return FileTools.importAsset(args);
                case "build": return GameTools.buildOrRun(false, args);
                case "run_game": return GameTools.buildOrRun(true, args);
                case "stop_game": return GameTools.stop();
                case "screenshot": return GameTools.screenshot(args);
                case "game_input": return GameTools.gameInput(args);
                case "game_state": return GameTools.gameState(args);
                case "run_test": return GameTools.runTest(args);
                case "game_logs": return GameTools.gameLogs(args);
                case "git": return GameTools.git(args);
                default: return ToolResult.error("Unknown tool '" + tool + "'. This app version does not have it.");
            }
        } catch (Gl.ToolError e) {
            return ToolResult.error(e.getMessage());
        } catch (Throwable t) {
            return ToolResult.error("Error: " + t);
        }
    }

    private static boolean projectOpen() {
        try {
            return Gl.run(Bridge::ready, 8_000);
        } catch (Throwable t) {
            return false;
        }
    }
}
