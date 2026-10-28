package com.star4droid.star2d.remote;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** The answer of a tool: MCP content blocks (text / image) and an error flag. */
public final class ToolResult {
    public final JsonArray content = new JsonArray();
    public boolean isError;

    public static ToolResult text(String s) {
        return new ToolResult().addText(s);
    }

    public static ToolResult error(String s) {
        ToolResult r = new ToolResult().addText(s);
        r.isError = true;
        return r;
    }

    /** The object as pretty-free JSON text (compact: it costs the model's context). */
    public static ToolResult json(Object o) {
        return text(new Gson().toJson(o));
    }

    public ToolResult addText(String s) {
        JsonObject b = new JsonObject();
        b.addProperty("type", "text");
        b.addProperty("text", s == null ? "" : s);
        content.add(b);
        return this;
    }

    public ToolResult addImage(String base64, String mime) {
        JsonObject b = new JsonObject();
        b.addProperty("type", "image");
        b.addProperty("data", base64);
        b.addProperty("mimeType", mime);
        content.add(b);
        return this;
    }

    @Override
    public String toString() {
        return content.toString();
    }
}
