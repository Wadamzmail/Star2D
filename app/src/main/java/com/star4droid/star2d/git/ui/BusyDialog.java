package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.git.Progress;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** A "please wait" dialog with a progress bar and an optional Cancel; also usable as a {@link Progress}. */
public final class BusyDialog implements Progress {
    private final Activity a;
    private final AlertDialog dialog;
    private final TextView text;
    private final ProgressBar bar;
    private volatile boolean cancelled;

    public BusyDialog(Activity a, String title, boolean cancellable) {
        this.a = a;
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 22), Ui.dp(a, 10), Ui.dp(a, 22), Ui.dp(a, 4));
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        text = Ui.text(a, "", 13.5f, Ui.TEXT_2);
        text.setPadding(0, 0, 0, Ui.dp(a, 10));
        col.addView(text, Ui.matchWrap());
        bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        col.addView(bar, Ui.matchWrap());
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(title).setView(col).setCancelable(false);
        if (cancellable) b.setNegativeButton(Tr.t("cancel", "Cancel"), (d, w) -> cancelled = true);
        dialog = b.create();
    }

    public BusyDialog show() {
        dialog.show();
        return this;
    }

    public void dismiss() {
        a.runOnUiThread(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        });
    }

    public void message(final String m) {
        a.runOnUiThread(() -> text.setText(m));
    }

    @Override
    public void update(final String task, final int done, final int total) {
        a.runOnUiThread(() -> {
            text.setText(task);
            if (total > 0) {
                bar.setIndeterminate(false);
                bar.setMax(total);
                bar.setProgress(Math.min(done, total));
            } else {
                bar.setIndeterminate(true);
            }
        });
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }
}
