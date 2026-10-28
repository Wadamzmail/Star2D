package com.star4droid.star2d.remote;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the AI is doing right now: the plan (tasks + progress) and whether the user's editing is locked.
 * Thread-safe; listeners are always called on the UI thread.
 */
public final class RemoteSession {
    private RemoteSession() {}

    public static final class Task {
        public final String id;
        public String title;
        /** pending | running | done | failed */
        public String status = "pending";
        public int progress = -1;
        public String note = "";

        Task(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public interface Listener {
        void onSessionChanged();
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final long IDLE_UNLOCK_MS = 60_000;
    /** While the connection is lost the user stays locked this long, waiting for the AI to come back. */
    private static final long DISCONNECT_GRACE_MS = 5 * 60_000;

    private static boolean working;
    private static String title = "";
    private static String summary = "";
    private static final List<Task> TASKS = new ArrayList<>();
    private static long lastActivity;
    private static boolean gameBusy;
    private static boolean connected;
    /** After finish_work or "take control" later calls do not lock again until a new plan is set. */
    private static boolean released;
    private static Runnable idleCheck;

    public static void addListener(Listener l) {
        if (!LISTENERS.contains(l)) LISTENERS.add(l);
    }

    public static void removeListener(Listener l) {
        LISTENERS.remove(l);
    }

    private static void changed() {
        UI.post(() -> {
            for (Listener l : LISTENERS) l.onSessionChanged();
        });
    }

    // ------------------------------------------------------------------ state

    public static synchronized boolean isWorking() {
        return working;
    }

    /** True while the AI works: the editor ignores edits (looking around is allowed). */
    public static synchronized boolean isLocked() {
        return working;
    }

    public static synchronized boolean isConnected() {
        return connected;
    }

    public static synchronized String title() {
        return title;
    }

    public static synchronized String summary() {
        return summary;
    }

    public static synchronized List<Task> tasks() {
        ArrayList<Task> copy = new ArrayList<>();
        for (Task t : TASKS) {
            Task c = new Task(t.id, t.title);
            c.status = t.status;
            c.progress = t.progress;
            c.note = t.note;
            copy.add(c);
        }
        return copy;
    }

    /** The AI works but the connection dropped: the lock stays, the overlay says "waiting". */
    public static synchronized boolean isReconnecting() {
        return working && !connected;
    }

    static void setConnected(boolean on) {
        synchronized (RemoteSession.class) {
            if (connected == on) return;
            connected = on;
            // a dropped connection does NOT release the user: the AI usually comes back within seconds
            if (on) lastActivity = System.currentTimeMillis();
        }
        if (isWorking()) scheduleIdleCheck();
        changed();
    }

    // ------------------------------------------------------------------ AI side

    /** Every tool call that changes something (or the plan) starts / refreshes the work session. */
    static void touch() {
        boolean was;
        synchronized (RemoteSession.class) {
            lastActivity = System.currentTimeMillis();
            if (released) return;
            was = working;
            working = true;
        }
        scheduleIdleCheck();
        if (!was) changed();
    }

    static void setPlan(String planTitle, List<String[]> tasks) {
        synchronized (RemoteSession.class) {
            title = planTitle == null ? "" : planTitle;
            summary = "";
            released = false;
            TASKS.clear();
            for (String[] t : tasks) TASKS.add(new Task(t[0], t[1]));
        }
        touch();
        changed();
    }

    /** @return false when the task id is unknown */
    static boolean updateTask(String id, String status, int progress, String note) {
        boolean found = false;
        synchronized (RemoteSession.class) {
            for (Task t : TASKS) {
                if (!t.id.equals(id)) continue;
                found = true;
                if (status != null && !status.isEmpty()) t.status = status;
                if (progress >= 0) t.progress = Math.min(100, progress);
                if ("done".equals(t.status)) t.progress = 100;
                if (note != null) t.note = note;
            }
        }
        touch();
        changed();
        return found;
    }

    static void finish(String text) {
        synchronized (RemoteSession.class) {
            summary = text == null ? "" : text;
            working = false;
            released = true;
            for (Task t : TASKS) if ("running".equals(t.status)) t.status = "done";
        }
        changed();
    }

    /** The user pressed "take control". */
    public static void takeControl() {
        synchronized (RemoteSession.class) {
            released = true;
            if (!working) return;
            working = false;
            summary = "";
        }
        changed();
    }

    /** Remove the finished plan card. */
    public static void clearPlan() {
        synchronized (RemoteSession.class) {
            if (working) return;
            TASKS.clear();
            title = "";
            summary = "";
        }
        changed();
    }

    private static void endLocked() {
        working = false;
    }

    private static void scheduleIdleCheck() {
        UI.removeCallbacks(idleCheck == null ? (idleCheck = RemoteSession::checkIdle) : idleCheck);
        UI.postDelayed(idleCheck, (connected ? IDLE_UNLOCK_MS : DISCONNECT_GRACE_MS) + 500);
    }

    private static void checkIdle() {
        boolean release;
        synchronized (RemoteSession.class) {
            long limit = connected ? IDLE_UNLOCK_MS : DISCONNECT_GRACE_MS;
            release = working && !gameBusy && System.currentTimeMillis() - lastActivity >= limit;
            if (release) working = false;
        }
        if (release) changed();
        else if (isWorking()) scheduleIdleCheck();
    }

    /** While a test/game runs for the AI the editor stays locked even if no tool call arrives. */
    static void setGameBusy(boolean busy) {
        synchronized (RemoteSession.class) {
            gameBusy = busy;
            if (!busy) lastActivity = System.currentTimeMillis();
        }
    }
}
