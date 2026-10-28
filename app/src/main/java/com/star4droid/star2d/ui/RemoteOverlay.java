package com.star4droid.star2d.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.remote.RemoteClient;
import com.star4droid.star2d.remote.RemoteSession;

import java.util.List;

/**
 * What the user sees while an AI works: a small "working" pill with a Take-control button, and the plan card
 * (tasks, progress per task, a check mark when done). It never blocks touches outside its own views, so the
 * camera of the scene stays usable.
 */
public final class RemoteOverlay extends FrameLayout implements RemoteSession.Listener, RemoteClient.StateListener {
    private final Activity activity;
    private final LinearLayout pill;
    private final TextView pillText;
    private final View pillDot;
    private final LinearLayout card;
    private final LinearLayout cardBody;
    private final TextView cardTitle, cardCount, summary;
    private final ProgressBar overall;
    private final ScrollView taskScroll;
    private final LinearLayout taskList;
    private final ImageView collapse;
    private final View dismiss;
    private boolean collapsed;
    private ObjectAnimator pulse;

    public RemoteOverlay(Activity activity) {
        super(activity);
        this.activity = activity;
        Context c = activity;
        setClickable(false);

        // ------------------------------------------------ working pill
        pill = Ui.row(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(Ui.rounded(0xF01B2A3D, Ui.dp(c, 22), Ui.ACCENT, Ui.dp(c, 1)));
        pill.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        pillDot = new View(c);
        pillDot.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(c, 5)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
        dlp.setMarginEnd(Ui.dp(c, 8));
        pill.addView(pillDot, dlp);
        LinearLayout texts = Ui.column(c);
        pillText = Ui.text(c, Tr.t("rc_working", "Claude is working…"), 13, Ui.TEXT);
        pillText.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(pillText);
        TextView sub = Ui.text(c, Tr.t("rc_keep_open_short", "Keep the editor open on screen"), 11, Ui.TEXT_2);
        texts.addView(sub);
        pill.addView(texts);
        TextView take = Ui.text(c, Tr.t("rc_take_control", "Take control"), 12, Ui.TEXT);
        take.setTypeface(Typeface.DEFAULT_BOLD);
        take.setGravity(Gravity.CENTER);
        take.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
        take.setBackground(Ui.ripple(0xFF2D3A4E, Ui.dp(c, 16)));
        take.setOnClickListener(v -> RemoteSession.takeControl());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Ui.dp(c, 10));
        pill.addView(take, tlp);
        pill.setClickable(true);
        pill.setVisibility(GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        plp.topMargin = Ui.dp(c, 6);
        addView(pill, plp);

        // ------------------------------------------------ plan card
        card = Ui.column(c);
        card.setBackground(Ui.rounded(0xF2171B21, Ui.dp(c, 16), 0xFF2F3742, Ui.dp(c, 1)));
        card.setClickable(true);
        card.setVisibility(GONE);

        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6));
        ImageView icon = Ui.smallIcon(c, R.drawable.ic_s2_plan, Ui.ACCENT, 20);
        head.addView(icon);
        LinearLayout titles = Ui.column(c);
        titles.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        TextView label = Ui.text(c, Tr.t("rc_plan", "Plan"), 10.5f, Ui.TEXT_3);
        label.setAllCaps(true);
        label.setLetterSpacing(0.08f);
        titles.addView(label);
        cardTitle = Ui.text(c, "", 14.5f, Ui.TEXT);
        cardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cardTitle.setMaxLines(2);
        titles.addView(cardTitle);
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        cardCount = Ui.text(c, "", 12.5f, Ui.TEXT_2);
        cardCount.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(cardCount);
        collapse = Ui.iconButton(c, R.drawable.ic_s2_expand_more, Tr.t("rc_collapse", "Collapse"), v -> {
            collapsed = !collapsed;
            render();
        });
        head.addView(collapse);
        card.addView(head, Ui.matchWrap());

        overall = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        overall.setMax(1000);
        overall.getProgressDrawable().setColorFilter(Ui.SUCCESS, android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3));
        olp.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 4));
        card.addView(overall, olp);

        cardBody = Ui.column(c);
        taskScroll = new ScrollView(c);
        taskScroll.setVerticalScrollBarEnabled(false);
        taskList = Ui.column(c);
        taskList.setPadding(Ui.dp(c, 8), Ui.dp(c, 2), Ui.dp(c, 8), Ui.dp(c, 4));
        taskScroll.addView(taskList);
        cardBody.addView(taskScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        summary = Ui.text(c, "", 12.5f, Ui.TEXT_2);
        summary.setPadding(Ui.dp(c, 14), Ui.dp(c, 4), Ui.dp(c, 14), Ui.dp(c, 6));
        cardBody.addView(summary, Ui.matchWrap());
        dismiss = Ui.button(c, R.drawable.ic_s2_check, Tr.t("rc_dismiss", "Dismiss"), false, v -> RemoteSession.clearPlan());
        LinearLayout.LayoutParams dsl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dsl.gravity = Gravity.END;
        dsl.setMargins(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 10));
        cardBody.addView(dismiss, dsl);
        card.addView(cardBody, Ui.matchWrap());

        boolean land = Ui.isLandscape(c);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(land ? Ui.dp(c, 330) : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, land ? (Gravity.BOTTOM | Gravity.START) : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        clp.setMargins(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), land ? Ui.dp(c, 10) : Ui.dp(c, 66));
        addView(card, clp);

        RemoteSession.addListener(this);
        RemoteClient.get().addListener(this);
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        RemoteSession.removeListener(this);
        RemoteClient.get().removeListener(this);
        if (pulse != null) pulse.cancel();
    }

    @Override
    public void onSessionChanged() {
        render();
    }

    @Override
    public void onRemoteState(RemoteClient.State s, String detail) {
        // the screen must stay on while an AI can send commands
        try {
            if (s == RemoteClient.State.CONNECTED) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Throwable ignored) {
        }
        render();
    }

    private void render() {
        Context c = getContext();
        boolean locked = RemoteSession.isLocked();
        List<RemoteSession.Task> tasks = RemoteSession.tasks();

        pill.setVisibility(locked ? VISIBLE : GONE);
        if (locked) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofFloat(pillDot, "alpha", 1f, 0.25f);
                pulse.setDuration(700);
                pulse.setRepeatMode(ObjectAnimator.REVERSE);
                pulse.setRepeatCount(ObjectAnimator.INFINITE);
            }
            if (!pulse.isStarted()) pulse.start();
        } else if (pulse != null) {
            pulse.cancel();
            pillDot.setAlpha(1f);
        }

        boolean show = !tasks.isEmpty() || !RemoteSession.title().isEmpty();
        if (card.getVisibility() != (show ? VISIBLE : GONE)) {
            card.setVisibility(show ? VISIBLE : GONE);
            if (show) {
                card.setAlpha(0f);
                card.setTranslationY(Ui.dp(c, 24));
                card.animate().alpha(1f).translationY(0).setDuration(220).start();
            }
        }
        if (!show) return;

        int done = 0;
        for (RemoteSession.Task t : tasks) if ("done".equals(t.status)) done++;
        cardTitle.setText(RemoteSession.title());
        cardCount.setText(done + "/" + tasks.size());
        float frac = tasks.isEmpty() ? 0 : 0;
        for (RemoteSession.Task t : tasks) frac += "done".equals(t.status) ? 1f : ("running".equals(t.status) && t.progress >= 0 ? t.progress / 100f : 0f);
        overall.setProgress(tasks.isEmpty() ? 0 : Math.round(1000f * frac / tasks.size()));

        collapse.setImageResource(collapsed ? R.drawable.ic_s2_expand_less : R.drawable.ic_s2_expand_more);
        cardBody.setVisibility(collapsed ? GONE : VISIBLE);
        overall.setVisibility(VISIBLE);

        // the list may grow tall: cap it so the scene stays visible
        int max = Ui.dp(c, Ui.isLandscape(c) ? 150 : 230);
        ViewGroup.LayoutParams sp = taskScroll.getLayoutParams();
        sp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        taskScroll.setLayoutParams(sp);
        taskScroll.post(() -> {
            if (taskScroll.getChildAt(0).getHeight() > max) {
                ViewGroup.LayoutParams p = taskScroll.getLayoutParams();
                p.height = max;
                taskScroll.setLayoutParams(p);
            }
        });

        taskList.removeAllViews();
        for (RemoteSession.Task t : tasks) taskList.addView(row(c, t), Ui.matchWrap());

        String sum = RemoteSession.summary();
        boolean finished = !locked;
        summary.setText(sum);
        summary.setVisibility(finished && !sum.isEmpty() ? VISIBLE : GONE);
        dismiss.setVisibility(finished ? VISIBLE : GONE);
    }

    private View row(Context c, RemoteSession.Task t) {
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, 6), Ui.dp(c, 5), Ui.dp(c, 6), Ui.dp(c, 5));
        r.setGravity(Gravity.TOP);

        FrameLayout mark = new FrameLayout(c);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
        mlp.setMarginEnd(Ui.dp(c, 8));
        boolean done = "done".equals(t.status), running = "running".equals(t.status), failed = "failed".equals(t.status);
        if (done || failed) {
            TextView e = new TextView(c);
            e.setText(done ? "✅" : "❌");
            e.setTextSize(15);
            e.setGravity(Gravity.CENTER);
            mark.addView(e, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (done) {
                e.setScaleX(0.4f);
                e.setScaleY(0.4f);
                e.animate().scaleX(1f).scaleY(1f).setDuration(260).start();
            }
        } else if (running) {
            ProgressBar pb = new ProgressBar(c);
            pb.setIndeterminate(true);
            pb.getIndeterminateDrawable().setColorFilter(Ui.ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(c, 18), Ui.dp(c, 18), Gravity.CENTER);
            mark.addView(pb, plp);
        } else {
            View ring = new View(c);
            ring.setBackground(Ui.rounded(0, Ui.dp(c, 8), Ui.TEXT_3, Ui.dp(c, 2)));
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(Ui.dp(c, 16), Ui.dp(c, 16), Gravity.CENTER);
            mark.addView(ring, rlp);
        }
        r.addView(mark, mlp);

        LinearLayout body = Ui.column(c);
        TextView title = Ui.text(c, t.title, 13.5f, done ? Ui.TEXT_2 : (running ? Ui.TEXT : (failed ? 0xFFFF8A80 : Ui.TEXT_2)));
        if (running) title.setTypeface(Typeface.DEFAULT_BOLD);
        body.addView(title);
        if (t.note != null && !t.note.isEmpty()) {
            TextView n = Ui.text(c, t.note, 11.5f, Ui.TEXT_3);
            body.addView(n);
        }
        if (running && t.progress >= 0) {
            ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(t.progress);
            pb.getProgressDrawable().setColorFilter(Ui.ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3));
            plp.topMargin = Ui.dp(c, 4);
            body.addView(pb, plp);
        }
        r.addView(body, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (running && t.progress >= 0) {
            TextView pct = Ui.text(c, t.progress + "%", 12, Ui.ACCENT);
            pct.setTypeface(Typeface.DEFAULT_BOLD);
            pct.setPadding(Ui.dp(c, 8), 0, 0, 0);
            r.addView(pct);
        }
        return r;
    }
}
