package com.star4droid.star2d.remote;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * The phone's side of the connection: one WebSocket to the Star2D Remote server (server/ in the repo).
 * It lives only while the editor is on screen (no foreground service): the AI is told to ask the user to keep
 * the editor open. Calls from the AI arrive as {"t":"call","id","tool","args"}; the answer goes back as {"t":"res",...}.
 */
public final class RemoteClient {
    public enum State { OFF, CONNECTING, CONNECTED, ERROR }

    public interface StateListener {
        void onRemoteState(State s, String detail);
    }

    private static RemoteClient inst;

    public static synchronized RemoteClient get() {
        if (inst == null) inst = new RemoteClient();
        return inst;
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<StateListener> listeners = new CopyOnWriteArrayList<>();
    // tools run one after the other, in the order the AI sent them
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "remote-tools");
        t.setDaemon(true);
        return t;
    });
    private final OkHttpClient http = new OkHttpClient.Builder()
            .pingInterval(25, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build();

    private WeakReference<Activity> activity = new WeakReference<>(null);
    private WebSocket socket;
    private boolean wanted;
    private int attempt;
    private State state = State.OFF;
    private String detail = "";
    private int generation;
    private final Runnable reconnect = this::connect;

    private RemoteClient() {}

    public Activity activity() {
        return activity.get();
    }

    public synchronized State state() {
        return state;
    }

    public synchronized String detail() {
        return detail;
    }

    public void addListener(StateListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(StateListener l) {
        listeners.remove(l);
    }

    private void setState(State s, String d) {
        synchronized (this) {
            state = s;
            detail = d == null ? "" : d;
        }
        RemoteSession.setConnected(s == State.CONNECTED);
        main.post(() -> {
            for (StateListener l : listeners) l.onRemoteState(s, detail);
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /** The editor is on screen: connect if the feature is on. */
    public synchronized void onForeground(Activity a) {
        activity = new WeakReference<>(a);
        if (!RemoteSettings.enabled(a) || !RemoteSettings.hasServer(a)) {
            stop();
            return;
        }
        wanted = true;
        if (socket == null) {
            attempt = 0;
            connect();
        }
    }

    /** The editor left the screen (Android would kill the socket soon anyway). */
    public synchronized void onBackground(Activity a) {
        if (activity.get() == a) stop();
    }

    /** Settings changed (on/off, server, new secret): reconnect from scratch. */
    public synchronized void restart() {
        Activity a = activity.get();
        stop();
        if (a != null) onForeground(a);
    }

    public synchronized void stop() {
        wanted = false;
        generation++;
        main.removeCallbacks(reconnect);
        if (socket != null) {
            try {
                socket.close(1000, "bye");
            } catch (Exception ignored) {
            }
            socket = null;
        }
        setState(State.OFF, "");
    }

    private synchronized void connect() {
        Activity a = activity.get();
        if (!wanted || a == null) return;
        final int gen = ++generation;
        setState(State.CONNECTING, "");
        final Request req;
        final Request check;
        try {
            req = new Request.Builder().url(RemoteSettings.wsUrl(a)).build();
            check = new Request.Builder().url(RemoteSettings.server(a) + "/version").build();
        } catch (Exception e) {
            setState(State.ERROR, "bad server URL");
            return;
        }
        // first ask the server which API versions it speaks: an old server (no /version, or without our version) needs an update
        http.newCall(check).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                openSocket(gen, a, req); // offline or unreachable: the normal retry logic reports it
            }

            @Override
            public void onResponse(okhttp3.Call call, Response r) {
                boolean ok = false;
                try (Response rr = r) {
                    if (rr.isSuccessful() && rr.body() != null) {
                        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(rr.body().string()).getAsJsonObject();
                        for (com.google.gson.JsonElement v : o.getAsJsonArray("versions"))
                            if (v.getAsInt() == RemoteSettings.API_VERSION) ok = true;
                    }
                } catch (Exception ignored) {
                }
                if (ok) openSocket(gen, a, req);
                else serverOutdated(gen);
            }
        });
    }

    /** The server does not speak this app's API version: say so, and look again slowly (the owner may update it). */
    public static final String SERVER_OUTDATED = "server_outdated";

    private synchronized void serverOutdated(int gen) {
        if (gen != generation || !wanted) return;
        setState(State.ERROR, SERVER_OUTDATED);
        main.postDelayed(reconnect, 60_000);
    }

    private synchronized void openSocket(int gen, Activity a, Request req) {
        if (gen != generation || !wanted) return;
        socket = http.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                if (gen != generation) return;
                attempt = 0;
                setState(State.CONNECTED, "");
                JsonObject hello = new JsonObject();
                hello.addProperty("t", "hello");
                hello.addProperty("app", "Star2D");
                hello.addProperty("ver", versionName(a));
                hello.addProperty("device", Build.MANUFACTURER + " " + Build.MODEL);
                ws.send(hello.toString());
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                if (gen != generation) return;
                handle(ws, text);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                lost(gen, ws, "closed");
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                lost(gen, ws, response != null ? "HTTP " + response.code() : String.valueOf(t.getMessage()));
            }
        });
    }

    private synchronized void lost(int gen, WebSocket ws, String why) {
        if (gen != generation || ws != socket) return;
        socket = null;
        if (!wanted) return;
        setState(State.ERROR, why);
        long delay = Math.min(30_000, 1000L << Math.min(5, attempt++));
        main.postDelayed(reconnect, delay);
    }

    private static String versionName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ calls

    private void handle(final WebSocket ws, String text) {
        JsonObject m;
        try {
            m = JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String t = m.has("t") ? m.get("t").getAsString() : "";
        if (!t.equals("call")) return; // "welcome" and others need no action
        final String id = m.get("id").getAsString();
        final String tool = m.get("tool").getAsString();
        final JsonObject args = m.has("args") && m.get("args").isJsonObject() ? m.getAsJsonObject("args") : new JsonObject();
        worker.execute(() -> {
            ToolResult r;
            try {
                r = RemoteTools.run(tool, args);
            } catch (Throwable e) {
                r = ToolResult.error("Error in " + tool + ": " + e);
            }
            JsonObject out = new JsonObject();
            out.addProperty("t", "res");
            out.addProperty("id", id);
            out.add("content", r.content);
            if (r.isError) out.addProperty("isError", true);
            String s = out.toString();
            if (s.length() > 950_000) {
                out = new JsonObject();
                out.addProperty("t", "res");
                out.addProperty("id", id);
                out.add("content", ToolResult.error("result too large for the connection (" + s.length() + " bytes): ask for less").content);
                out.addProperty("isError", true);
                s = out.toString();
            }
            ws.send(s);
        });
    }
}
