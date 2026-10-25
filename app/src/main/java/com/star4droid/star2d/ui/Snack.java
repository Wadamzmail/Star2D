package com.star4droid.star2d.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Snackbar replacing toasts: appears at the bottom of the current screen, auto-hides,
 * and can be swiped sideways to dismiss. Optional action button.
 */
public final class Snack {
    private Snack() {}

    private static WeakReference<Activity> current = new WeakReference<>(null);
    private static WeakReference<View> shown = new WeakReference<>(null);
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable hideTask;

    /** Track the resumed activity (call once from Application.onCreate). */
    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { current = new WeakReference<>(a); }
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityResumed(Activity a) { current = new WeakReference<>(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (current.get() == a) current = new WeakReference<>(null); }
        });
    }

    public static Activity activity() {
        return current.get();
    }

    public static void show(String text) {
        show(text, null, null);
    }

    public static void show(String text, String action, Runnable onAction) {
        H.post(() -> {
            Activity a = current.get();
            if (a == null || a.isFinishing()) return;
            showIn(a, text, action, onAction);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void showIn(Activity a, String text, String action, Runnable onAction) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null) return;
        View old = shown.get();
        if (old != null && old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        if (hideTask != null) H.removeCallbacks(hideTask);

        LinearLayout bar = Ui.row(a);
        bar.setBackground(Ui.rounded(0xF2323842, Ui.dp(a, 12), 0x33FFFFFF, 1));
        bar.setElevation(Ui.dp(a, 10));
        bar.setPadding(Ui.dp(a, 16), Ui.dp(a, 11), Ui.dp(a, 10), Ui.dp(a, 11));
        bar.setMinimumHeight(Ui.dp(a, 48));
        TextView t = Ui.text(a, text, 14, Ui.TEXT);
        t.setMaxLines(4);
        bar.addView(t, Ui.weight(1));
        if (action != null) {
            TextView b = Ui.text(a, action, 14, 0xFF64B5F6);
            b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            b.setPadding(Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
            b.setOnClickListener(v -> {
                dismiss(bar, 0);
                if (onAction != null) onAction.run();
            });
            bar.addView(b);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.min(content.getWidth() > 0 ? content.getWidth() - Ui.dp(a, 24) : ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 560)), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Ui.dp(a, 18);
        content.addView(bar, lp);
        shown = new WeakReference<>(bar);

        // swipe sideways to dismiss
        bar.setOnTouchListener(new View.OnTouchListener() {
            float sx;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: sx = e.getRawX(); if (hideTask != null) H.removeCallbacks(hideTask); return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx;
                        v.setTranslationX(dx);
                        v.setAlpha(Math.max(0.2f, 1f - Math.abs(dx) / v.getWidth()));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float d = e.getRawX() - sx;
                        if (Math.abs(d) > v.getWidth() * 0.3f) dismiss(v, d > 0 ? 1 : -1);
                        else {
                            v.animate().translationX(0).alpha(1).setDuration(150).start();
                            scheduleHide(v);
                        }
                        return true;
                }
                return false;
            }
        });
        bar.setTranslationY(Ui.dp(a, 40));
        bar.setAlpha(0);
        bar.animate().translationY(0).alpha(1).setDuration(180).start();
        scheduleHide(bar);
    }

    private static void scheduleHide(View v) {
        hideTask = () -> dismiss(v, 0);
        H.postDelayed(hideTask, 3200);
    }

    private static void dismiss(View v, int dir) {
        if (v.getParent() == null) return;
        v.animate().translationX(dir * v.getWidth()).translationY(dir == 0 ? Ui.dp(v.getContext(), 30) : 0).alpha(0).setDuration(170)
                .withEndAction(() -> { if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v); }).start();
    }
}
