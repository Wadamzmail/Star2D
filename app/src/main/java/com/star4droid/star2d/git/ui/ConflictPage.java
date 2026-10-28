package com.star4droid.star2d.git.ui;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.List;

/** Resolving a merge: files in conflict, then part by part (mine / GitHub's / both / edit), then finishing the merge. */
public final class ConflictPage {
    private final GitContext g;
    private FullPage page;
    private LinearLayout body;

    private static String T(String k, String d) { return Tr.t(k, d); }

    public static void show(GitContext g) {
        new ConflictPage(g).open();
    }

    private ConflictPage(GitContext g) {
        this.g = g;
    }

    private void open() {
        ScrollView sv = new ScrollView(g.a);
        body = Ui.column(g.a);
        int p = Ui.dp(g.a, 16);
        body.setPadding(p, p, p, p);
        sv.addView(body);
        page = new FullPage(g.a, T("git_resolve", "Resolve conflicts")).content(sv).show();
        reload();
    }

    private void reload() {
        g.work(GitRepo::conflictPaths, this::render);
    }

    private void render(final List<String> files) {
        body.removeAllViews();
        TextView intro = Ui.text(g.a, T("git_conflict_intro",
                "The same file was changed here and on GitHub. Choose what to keep for each file, then finish the merge."), 13.5f, Ui.TEXT_2);
        intro.setPadding(0, 0, 0, Ui.dp(g.a, 10));
        body.addView(intro);
        if (files.isEmpty()) {
            TextView ok = Ui.text(g.a, "✓ " + T("git_all_resolved", "Everything is resolved."), 16, Ui.SUCCESS);
            ok.setTypeface(Typeface.DEFAULT_BOLD);
            body.addView(ok);
            View fin = Ui.button(g.a, R.drawable.ic_s2_merge, T("git_finish_merge", "Finish merge"), true, v -> finish());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(g.a, 12);
            body.addView(fin, lp);
        }
        for (final String f : files) body.addView(fileCard(f), cardParams());
        View abort = Ui.button(g.a, 0, T("git_abort_merge", "Cancel the merge"), false, v ->
                Dialogs.confirm(g.a, T("git_abort_merge", "Cancel the merge"),
                        T("git_abort_q", "Go back to how things were before the pull?"), T("ok", "OK"), () ->
                                g.work(r -> {
                                    r.abortMerge();
                                    return true;
                                }, ok -> {
                                    page.close();
                                    g.onFilesChanged.run();
                                    g.onRefresh.run();
                                })));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = Ui.dp(g.a, 18);
        body.addView(abort, ap);
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(g.a, 10);
        return lp;
    }

    private View fileCard(final String f) {
        LinearLayout c = Ui.column(g.a);
        c.setPadding(Ui.dp(g.a, 14), Ui.dp(g.a, 12), Ui.dp(g.a, 14), Ui.dp(g.a, 12));
        c.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(g.a, 12), 0x66FFB300, Ui.dp(g.a, 1)));
        TextView n = Ui.text(g.a, "⚠  " + f, 14, Ui.TEXT);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(n);
        LinearLayout row = Ui.row(g.a);
        row.setPadding(0, Ui.dp(g.a, 8), 0, 0);
        View mine = Ui.button(g.a, 0, T("git_keep_mine", "Keep mine"), false, v -> side(f, true));
        View theirs = Ui.button(g.a, 0, T("git_keep_theirs", "Take GitHub's"), false, v -> side(f, false));
        View parts = Ui.button(g.a, 0, T("git_review_parts", "Review parts"), true, v -> hunks(f));
        for (View b : new View[]{mine, theirs, parts}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(g.a, 38), 1);
            lp.setMargins(Ui.dp(g.a, 2), 0, Ui.dp(g.a, 2), 0);
            row.addView(b, lp);
        }
        c.addView(row);
        return c;
    }

    private void side(final String f, final boolean ours) {
        g.work(r -> {
            r.resolveSide(f, ours);
            return true;
        }, ok -> reload());
    }

    private void finish() {
        final EditText msg = Ui.input(g.a, T("git_merge_msg_default", "Merge changes from GitHub"), "");
        LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 8), Ui.dp(g.a, 20), 0);
        col.addView(msg);
        new AlertDialog.Builder(g.a).setTitle(T("git_finish_merge", "Finish merge")).setView(col)
                .setPositiveButton(T("ok", "OK"), (d, w) -> g.work(r -> r.commit(msg.getText().toString().trim().isEmpty() ? "Merge" : msg.getText().toString().trim(), null), id -> {
                    Snack.show(T("git_merged", "Merged"));
                    page.close();
                    g.onFilesChanged.run();
                    g.onRefresh.run();
                }))
                .setNegativeButton(T("cancel", "Cancel"), null).show();
    }

    // ---------------------------------------------------------------- one file, part by part

    private void hunks(final String f) {
        g.work(r -> r.conflictHunks(f), list -> new Hunks(f, list).open());
    }

    private final class Hunks {
        final String file;
        final List<GitRepo.Hunk> list;
        FullPage p;
        LinearLayout box;

        Hunks(String file, List<GitRepo.Hunk> list) {
            this.file = file;
            this.list = list;
        }

        void open() {
            LinearLayout root = Ui.column(g.a);
            ScrollView sv = new ScrollView(g.a);
            box = Ui.column(g.a);
            int pad = Ui.dp(g.a, 14);
            box.setPadding(pad, pad, pad, pad);
            sv.addView(box);
            root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            View save = Ui.button(g.a, R.drawable.ic_s2_commit, T("git_save_file", "Save this file"), true, v -> save());
            LinearLayout bar = Ui.row(g.a);
            bar.setGravity(Gravity.CENTER);
            bar.setPadding(pad, Ui.dp(g.a, 8), pad, Ui.dp(g.a, 8));
            bar.setBackgroundColor(Ui.SURFACE);
            bar.addView(save);
            root.addView(bar, Ui.matchWrap());
            p = new FullPage(g.a, file).content(root).show();
            draw();
        }

        void draw() {
            box.removeAllViews();
            int n = 0;
            for (final GitRepo.Hunk h : list) {
                if (!h.conflict) continue;
                n++;
                box.addView(card(h, n), cardParams());
            }
            if (n == 0) box.addView(Ui.text(g.a, T("git_no_markers", "No conflicting parts found in this file."), 14, Ui.TEXT_3));
        }

        View card(final GitRepo.Hunk h, int idx) {
            LinearLayout c = Ui.column(g.a);
            c.setPadding(Ui.dp(g.a, 12), Ui.dp(g.a, 10), Ui.dp(g.a, 12), Ui.dp(g.a, 12));
            c.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(g.a, 12)));
            TextView t = Ui.text(g.a, T("git_part", "Part") + " " + idx + "  ·  " + status(h), 13, h.choice == 0 ? Ui.WARNING : Ui.SUCCESS);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(t);
            LinearLayout sides = Ui.isLandscape(g.a) ? Ui.row(g.a) : Ui.column(g.a);
            sides.addView(side(T("git_mine", "Mine"), h.ours, 0x224CAF50, h.choice == 1 || h.choice == 3), sideParams());
            sides.addView(side(T("git_theirs", "GitHub's"), h.theirs, 0x222196F3, h.choice == 2 || h.choice == 3), sideParams());
            c.addView(sides, Ui.matchWrap());
            LinearLayout row = Ui.row(g.a);
            row.setPadding(0, Ui.dp(g.a, 8), 0, 0);
            String[] names = {T("git_mine", "Mine"), T("git_theirs", "GitHub's"), T("git_both", "Both"), T("git_edit", "Edit")};
            for (int i = 0; i < 4; i++) {
                final int choice = i + 1;
                LinearLayout b = Ui.button(g.a, 0, names[i], h.choice == choice, v -> {
                    if (choice == 4) edit(h);
                    else {
                        h.choice = choice;
                        draw();
                    }
                });
                b.setPadding(Ui.dp(g.a, 4), 0, Ui.dp(g.a, 4), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(g.a, 38), 1);
                lp.setMargins(Ui.dp(g.a, 2), 0, Ui.dp(g.a, 2), 0);
                row.addView(b, lp);
            }
            c.addView(row);
            return c;
        }

        LinearLayout.LayoutParams sideParams() {
            LinearLayout.LayoutParams lp = Ui.isLandscape(g.a) ? Ui.weight(1) : Ui.matchWrap();
            lp.topMargin = Ui.dp(g.a, 6);
            lp.setMarginStart(Ui.dp(g.a, 2));
            lp.setMarginEnd(Ui.dp(g.a, 2));
            return lp;
        }

        View side(String label, String text, int bg, boolean chosen) {
            LinearLayout b = Ui.column(g.a);
            b.setPadding(Ui.dp(g.a, 8), Ui.dp(g.a, 6), Ui.dp(g.a, 8), Ui.dp(g.a, 8));
            b.setBackground(Ui.rounded(bg, Ui.dp(g.a, 8), chosen ? Ui.ACCENT : 0x00000000, Ui.dp(g.a, 2)));
            TextView l = Ui.text(g.a, label, 11.5f, Ui.TEXT_2);
            l.setTypeface(Typeface.DEFAULT_BOLD);
            b.addView(l);
            TextView v = Ui.text(g.a, text.isEmpty() ? T("git_empty_part", "(nothing)") : text, 12, Ui.TEXT);
            v.setTypeface(Typeface.MONOSPACE);
            v.setMaxLines(12);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.addView(v);
            return b;
        }

        String status(GitRepo.Hunk h) {
            switch (h.choice) {
                case 1: return T("git_mine", "Mine");
                case 2: return T("git_theirs", "GitHub's");
                case 3: return T("git_both", "Both");
                case 4: return T("git_edit", "Edit");
                default: return T("git_undecided", "Choose one");
            }
        }

        void edit(final GitRepo.Hunk h) {
            final EditText e = new EditText(g.a);
            e.setTypeface(Typeface.MONOSPACE);
            e.setTextSize(12);
            e.setText(h.custom != null ? h.custom : h.ours + h.theirs);
            e.setMinLines(6);
            e.setGravity(Gravity.TOP);
            e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            LinearLayout col = Ui.column(g.a);
            col.setPadding(Ui.dp(g.a, 16), Ui.dp(g.a, 8), Ui.dp(g.a, 16), 0);
            col.addView(e);
            ScrollView sv = new ScrollView(g.a);
            sv.addView(col);
            new AlertDialog.Builder(g.a).setTitle(T("git_edit", "Edit")).setView(sv)
                    .setPositiveButton(T("ok", "OK"), (d, w) -> {
                        String s = e.getText().toString();
                        h.custom = s.isEmpty() || s.endsWith("\n") ? s : s + "\n";
                        h.choice = 4;
                        draw();
                    })
                    .setNegativeButton(T("cancel", "Cancel"), null).show();
        }

        void save() {
            for (GitRepo.Hunk h : list) {
                if (h.conflict && h.choice == 0) {
                    Snack.show(T("git_choose_all", "Choose what to keep for every part first."));
                    return;
                }
            }
            final String text = GitRepo.assemble(list);
            g.work(r -> {
                r.resolve(file, text);
                return true;
            }, ok -> {
                p.close();
                reload();
            });
        }
    }
}
