package com.star4droid.star2d.git.ui;

import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.List;

/** Branches: switch, create, merge into the current one, rename, delete. */
public final class BranchesPage {
    private final GitContext g;
    private FullPage page;
    private LinearLayout body;

    private static String T(String k, String d) { return Tr.t(k, d); }

    public static void show(GitContext g) {
        new BranchesPage(g).open();
    }

    private BranchesPage(GitContext g) {
        this.g = g;
    }

    private void open() {
        ScrollView sv = new ScrollView(g.a);
        body = Ui.column(g.a);
        int p = Ui.dp(g.a, 14);
        body.setPadding(p, p, p, p);
        sv.addView(body);
        page = new FullPage(g.a, T("git_branches", "Branches")).content(sv)
                .addAction(R.drawable.ic_s2_add, T("git_new_branch", "New branch"), v -> create()).show();
        load();
    }

    private void load() {
        g.work(GitRepo::branches, this::render);
    }

    private void render(List<GitRepo.BranchInfo> list) {
        body.removeAllViews();
        body.addView(Ui.text(g.a, T("git_branches_intro",
                "A branch is a separate line of work. Try ideas on a new branch and merge it back when it works."), 13, Ui.TEXT_2));
        for (final GitRepo.BranchInfo b : list) {
            String sub = b.remote ? T("git_on_github", "On GitHub only")
                    : (b.hasRemote ? "↑" + b.ahead + "  ↓" + b.behind + "   " : "") + b.tip;
            String badge = b.current ? "● " + T("git_current", "Current") : null;
            body.addView(ListUi.row(g.a, b.name, sub, badge, Ui.SUCCESS, v -> options(b)), ListUi.gap(g.a));
        }
    }

    private void create() {
        final EditText n = Ui.input(g.a, "", "feature-1");
        LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 8), Ui.dp(g.a, 20), 0);
        col.addView(n);
        new AlertDialog.Builder(g.a).setTitle(T("git_new_branch", "New branch")).setView(col)
                .setPositiveButton(T("git_create_switch", "Create and switch"), (d, w) -> {
                    final String name = n.getText().toString().trim().replace(' ', '-');
                    if (name.isEmpty()) return;
                    g.work(r -> {
                        r.createBranch(name, true);
                        return true;
                    }, ok -> {
                        Snack.show(T("git_done", "Done"));
                        g.onRefresh.run();
                        load();
                    });
                }).setNegativeButton(T("cancel", "Cancel"), null).show();
    }

    private void options(final GitRepo.BranchInfo b) {
        java.util.ArrayList<String> items = new java.util.ArrayList<>();
        final java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        if (!b.current) {
            items.add(T("git_switch", "Switch to this branch"));
            ids.add(0);
            items.add(T("git_merge_into", "Merge it into the current branch"));
            ids.add(1);
        }
        if (!b.remote) {
            items.add(T("git_rename", "Rename"));
            ids.add(2);
            if (!b.current) {
                items.add(T("git_delete", "Delete"));
                ids.add(3);
            }
        }
        if (items.isEmpty()) return;
        new AlertDialog.Builder(g.a).setTitle(b.name).setItems(items.toArray(new String[0]), (d, which) -> {
            switch (ids.get(which)) {
                case 0:
                    g.work(r -> {
                        r.checkout(b.name);
                        return true;
                    }, ok -> {
                        Snack.show(T("git_switched", "Switched"));
                        g.onFilesChanged.run();
                        g.onRefresh.run();
                        load();
                    });
                    break;
                case 1:
                    g.work(r -> r.merge(b.name, false), o -> {
                        PullResult.report(g, o);
                        if (PullResult.changedFiles(o)) g.onFilesChanged.run();
                        g.onRefresh.run();
                        load();
                    });
                    break;
                case 2:
                    rename(b);
                    break;
                default:
                    Dialogs.confirm(g.a, T("git_delete", "Delete"), b.name, T("git_delete", "Delete"), () ->
                            g.work(r -> {
                                try {
                                    r.deleteBranch(b.name, false);
                                } catch (Exception e) {
                                    r.deleteBranch(b.name, true);
                                }
                                return true;
                            }, ok -> load()));
            }
        }).show();
    }

    private void rename(final GitRepo.BranchInfo b) {
        final EditText n = Ui.input(g.a, b.name, "");
        LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 8), Ui.dp(g.a, 20), 0);
        col.addView(n);
        new AlertDialog.Builder(g.a).setTitle(T("git_rename", "Rename")).setView(col)
                .setPositiveButton(T("ok", "OK"), (d, w) -> {
                    final String name = n.getText().toString().trim().replace(' ', '-');
                    if (name.isEmpty() || name.equals(b.name)) return;
                    g.work(r -> {
                        r.renameBranch(b.name, name);
                        return true;
                    }, ok -> {
                        g.onRefresh.run();
                        load();
                    });
                }).setNegativeButton(T("cancel", "Cancel"), null).show();
    }
}
