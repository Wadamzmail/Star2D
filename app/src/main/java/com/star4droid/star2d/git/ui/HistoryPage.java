package com.star4droid.star2d.git.ui;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Commit history as a tree; tap a commit for its files, differences, revert and reset. */
public final class HistoryPage {
    private final GitContext g;
    private FullPage page;
    private HistoryView tree;
    private ProgressBar bar;

    private static String T(String k, String d) { return Tr.t(k, d); }

    public static void show(GitContext g) {
        new HistoryPage(g).open();
    }

    private HistoryPage(GitContext g) {
        this.g = g;
    }

    private void open() {
        LinearLayout root = Ui.column(g.a);
        bar = new ProgressBar(g.a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(g.a, 3)));
        tree = new HistoryView(g.a);
        ScrollView sv = new ScrollView(g.a);
        sv.addView(tree, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        tree.setOnCommit(this::details);
        page = new FullPage(g.a, T("git_history", "History")).content(root).show();
        load();
    }

    private void load() {
        bar.setVisibility(View.VISIBLE);
        g.work(r -> r.log(300), list -> {
            bar.setVisibility(View.INVISIBLE);
            tree.setCommits(list);
        });
    }

    private void details(final GitRepo.CommitInfo c) {
        final LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 6), Ui.dp(g.a, 20), 0);
        TextView m = Ui.text(g.a, c.fullMessage.trim(), 14.5f, Ui.TEXT);
        col.addView(m);
        TextView meta = Ui.text(g.a, c.author + " <" + c.email + ">\n"
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(c.time)) + "\n" + c.id, 11.5f, Ui.TEXT_3);
        meta.setPadding(0, Ui.dp(g.a, 6), 0, Ui.dp(g.a, 8));
        meta.setTextIsSelectable(true);
        col.addView(meta);
        col.addView(Ui.sectionLabel(g.a, T("git_files_changed", "Changed files")));
        final LinearLayout files = Ui.column(g.a);
        files.addView(Ui.text(g.a, "...", 13, Ui.TEXT_3));
        col.addView(files);
        ScrollView sv = new ScrollView(g.a);
        sv.addView(col);
        final AlertDialog d = new AlertDialog.Builder(g.a).setView(sv)
                .setPositiveButton(T("close", "Close"), null)
                .setNeutralButton(T("git_more", "More..."), null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            actions(c, d);
        }));
        d.show();
        g.work(r -> r.commitFiles(c.id), list -> {
            files.removeAllViews();
            if (list.isEmpty()) files.addView(Ui.text(g.a, T("git_no_files", "No file changes"), 13, Ui.TEXT_3));
            for (final GitRepo.FileChange f : list) {
                TextView t = Ui.text(g.a, symbol(f.type) + "  " + f.path, 13, Ui.TEXT_2);
                t.setPadding(0, Ui.dp(g.a, 7), 0, Ui.dp(g.a, 7));
                t.setBackground(Ui.ripple(0x00000000, Ui.dp(g.a, 6)));
                t.setOnClickListener(v -> DiffPage.show(g, f.path, rr -> rr.commitDiff(c.id, f.path)));
                files.addView(t);
            }
        });
    }

    private static String symbol(String t) {
        if (t == null) return "•";
        switch (t) {
            case "ADD": return "＋";
            case "DELETE": return "－";
            case "RENAME": return "↪";
            default: return "•";
        }
    }

    private void actions(final GitRepo.CommitInfo c, final AlertDialog parent) {
        String[] items = {T("git_revert", "Undo this commit (revert)"), T("git_reset_soft", "Go back here, keep my changes"),
                T("git_reset_hard", "Go back here, discard later changes"), T("git_copy_id", "Copy commit id")};
        new AlertDialog.Builder(g.a).setItems(items, (dd, which) -> {
            if (which == 3) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) g.a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("commit", c.id));
                Snack.show(T("gh_copied", "Copied"));
                return;
            }
            final String msg = which == 0 ? T("git_revert_q", "Create a new commit that undoes this one?")
                    : which == 1 ? T("git_reset_soft_q", "Move back to this commit. Your files stay as they are and show up as changes.")
                    : T("git_reset_hard_q", "Move back to this commit and DELETE every change made after it. This cannot be undone.");
            Dialogs.confirm(g.a, T("git_are_you_sure", "Are you sure?"), msg, T("ok", "OK"), () -> {
                if (which == 0) {
                    g.work(r -> r.revertCommit(c.id), o -> {
                        parent.dismiss();
                        PullResult.report(g, o);
                        if (PullResult.changedFiles(o)) g.onFilesChanged.run();
                        g.onRefresh.run();
                        load();
                    });
                } else {
                    final String mode = which == 1 ? "mixed" : "hard";
                    g.work(r -> {
                        r.resetTo(c.id, mode);
                        return true;
                    }, ok -> {
                        parent.dismiss();
                        Snack.show(T("git_done", "Done"));
                        g.onFilesChanged.run();
                        g.onRefresh.run();
                        load();
                    });
                }
            });
        }).show();
    }
}
