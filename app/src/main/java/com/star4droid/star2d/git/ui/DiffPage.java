package com.star4droid.star2d.git.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Shows what changed in one file (working tree or a commit). */
public final class DiffPage {
    private DiffPage() {}

    public static void show(final GitContext g, final String title, final GitContext.RepoJob<String> job) {
        final FrameLayout box = new FrameLayout(g.a);
        final ProgressBar bar = new ProgressBar(g.a);
        box.addView(bar, new FrameLayout.LayoutParams(Ui.dp(g.a, 40), Ui.dp(g.a, 40), android.view.Gravity.CENTER));
        new FullPage(g.a, title).content(box).show();
        g.work(job, diff -> {
            box.removeAllViews();
            if (diff == null || diff.trim().isEmpty()) {
                TextView t = Ui.text(g.a, Tr.t("git_no_diff", "No textual changes to show."), 14, Ui.TEXT_3);
                t.setGravity(android.view.Gravity.CENTER);
                box.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                box.addView(DiffText.view(g.a, diff), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        });
    }
}
