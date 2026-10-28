package com.star4droid.star2d.remote;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

/**
 * Settings of the Claude remote control: on/off, the server, and the private secret that is the only
 * credential (it is part of the connector URL). Stored in plain preferences: it is just a random token.
 */
public final class RemoteSettings {
    private RemoteSettings() {}

    /** Server used when the user did not type one. Put the URL of your deployed `server/` here (https://....workers.dev). */
    public static final String DEFAULT_SERVER = "https://star2d.ans44029.workers.dev";

    private static final String PREFS = "star2d_remote";

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) {
        return p(c).getBoolean("enabled", false);
    }

    public static void setEnabled(Context c, boolean on) {
        p(c).edit().putBoolean("enabled", on).apply();
    }

    /** What the user typed (may be empty = use the default server). */
    public static String customServer(Context c) {
        return p(c).getString("server", "");
    }

    public static void setCustomServer(Context c, String url) {
        p(c).edit().putString("server", normalize(url)).apply();
    }

    /** The server in use, without a trailing slash; empty when none is configured. */
    public static String server(Context c) {
        String s = customServer(c);
        return s.isEmpty() ? normalize(DEFAULT_SERVER) : s;
    }

    public static boolean usingDefaultServer(Context c) {
        return customServer(c).isEmpty();
    }

    public static boolean hasServer(Context c) {
        return !server(c).isEmpty();
    }

    /** The private token of this phone (created on first use). */
    public static synchronized String secret(Context c) {
        String s = p(c).getString("secret", "");
        if (s.length() < 24) {
            byte[] b = new byte[30];
            new SecureRandom().nextBytes(b);
            s = Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            p(c).edit().putString("secret", s).apply();
        }
        return s;
    }

    /** A new secret: the old connector URL stops working. */
    public static synchronized void regenerateSecret(Context c) {
        p(c).edit().remove("secret").apply();
        secret(c);
    }

    /** The server API version this app speaks. The server keeps old versions working (/v1/, /v2/...); a server without this one needs an update. */
    public static final int API_VERSION = 1;

    public static String versionBase(Context c) {
        return server(c) + "/v" + API_VERSION;
    }

    public static String connectorUrl(Context c) {
        return versionBase(c) + "/mcp/" + secret(c);
    }

    public static String apiBase(Context c) {
        return versionBase(c) + "/api/" + secret(c);
    }

    public static String wsUrl(Context c) {
        String s = server(c);
        String ws = s.startsWith("https://") ? "wss://" + s.substring(8) : s.startsWith("http://") ? "ws://" + s.substring(7) : s;
        return ws + "/v" + API_VERSION + "/ws/" + secret(c);
    }

    public static String guideUrl(Context c) {
        return versionBase(c) + "/AI.md";
    }

    static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim();
        if (s.isEmpty()) return "";
        if (!s.contains("://")) s = "https://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
