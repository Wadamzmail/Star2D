package com.star4droid.star2d.git.ui;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.git.GitException;
import com.star4droid.star2d.git.GitHubApi;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.git.ProjectGit;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.List;

/** Smaller git dialogs: stash, tags, create / connect / fork repositories, visibility, force push. */
public final class GitDialogs {
    private GitDialogs() {}

    private static String T(String k, String d) { return Tr.t(k, d); }

    public static void openUrl(GitContext g, String url) {
        try {
            g.a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Snack.show(url);
        }
    }

    // ------------------------------------------------------------------ stash

    public static void stash(final GitContext g) {
        g.work(r -> r.stashes(), list -> {
            LinearLayout col = Ui.column(g.a);
            col.setPadding(Ui.dp(g.a, 16), Ui.dp(g.a, 6), Ui.dp(g.a, 16), 0);
            TextView info = Ui.text(g.a, T("git_stash_intro",
                    "Stash puts your unsaved changes aside so the project goes back to the last commit. You can bring them back later."), 13, Ui.TEXT_2);
            col.addView(info);
            final AlertDialog[] d = new AlertDialog[1];
            if (list.isEmpty()) col.addView(ListUi.empty(g.a, T("git_stash_none", "Nothing stashed")));
            for (int i = 0; i < list.size(); i++) {
                final int idx = i;
                col.addView(ListUi.row(g.a, list.get(i), null, T("git_restore", "Restore"), Ui.ACCENT, v -> {
                    d[0].dismiss();
                    g.work(r -> {
                        r.stashApply(idx, true);
                        return true;
                    }, ok -> {
                        Snack.show(T("git_done", "Done"));
                        g.onFilesChanged.run();
                        g.onRefresh.run();
                    });
                }), ListUi.gap(g.a));
            }
            ScrollView sv = new ScrollView(g.a);
            sv.addView(col);
            d[0] = new AlertDialog.Builder(g.a).setTitle(T("git_stash", "Stash")).setView(sv)
                    .setPositiveButton(T("git_stash_now", "Stash my changes"), (x, w) -> g.work(r -> r.stash("Stash " + new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.ENGLISH).format(new java.util.Date())), done -> {
                        Snack.show(done ? T("git_done", "Done") : T("git_nothing_to_stash", "No changes to stash"));
                        g.onFilesChanged.run();
                        g.onRefresh.run();
                    }))
                    .setNegativeButton(T("close", "Close"), null).create();
            d[0].show();
        });
    }

    // ------------------------------------------------------------------ tags

    public static void tags(final GitContext g) {
        g.work(r -> r.tags(), list -> {
            LinearLayout col = Ui.column(g.a);
            col.setPadding(Ui.dp(g.a, 16), Ui.dp(g.a, 6), Ui.dp(g.a, 16), 0);
            col.addView(Ui.text(g.a, T("git_tags_intro", "A tag marks the current version, for example v1.0."), 13, Ui.TEXT_2));
            if (list.isEmpty()) col.addView(ListUi.empty(g.a, T("git_tags_none", "No tags yet")));
            for (final String t : list) {
                col.addView(ListUi.row(g.a, t, null, T("git_delete", "Delete"), Ui.DANGER, v ->
                        Dialogs.confirm(g.a, T("git_delete", "Delete"), t, T("git_delete", "Delete"), () ->
                                g.work(r -> {
                                    r.deleteTag(t);
                                    return true;
                                }, ok -> tags(g)))), ListUi.gap(g.a));
            }
            final EditText n = Ui.input(g.a, "", "v1.0");
            LinearLayout.LayoutParams lp = ListUi.gap(g.a);
            col.addView(n, lp);
            ScrollView sv = new ScrollView(g.a);
            sv.addView(col);
            new AlertDialog.Builder(g.a).setTitle(T("git_tags", "Tags")).setView(sv)
                    .setPositiveButton(T("git_add_tag", "Add tag & push tags"), (x, w) -> {
                        final String name = n.getText().toString().trim().replace(' ', '-');
                        g.workWithProgress(T("git_pushing", "Pushing..."), false, (r, p) -> {
                            if (!name.isEmpty()) r.createTag(name);
                            r.pushTags(p);
                            return true;
                        }, ok -> Snack.show(T("git_done", "Done")));
                    })
                    .setNegativeButton(T("close", "Close"), null).show();
        });
    }

    // ------------------------------------------------------------------ repositories

    /** Create a repository on GitHub for this project and connect it. */
    public static void createRepo(final GitContext g, final Runnable done) {
        if (!GitHubSession.connected(g.a)) {
            GitHubConnect.show(g.a, done);
            return;
        }
        LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 6), Ui.dp(g.a, 20), 0);
        final UploadForm form = new UploadForm(g.a, col, g.dir.getName(), true);
        ScrollView sv = new ScrollView(g.a);
        sv.addView(col);
        final AlertDialog d = new AlertDialog.Builder(g.a).setTitle(T("git_create_repo", "Create repository on GitHub")).setView(sv)
                .setPositiveButton(T("home_create", "Create"), null)
                .setNegativeButton(T("cancel", "Cancel"), null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String err = form.validate();
            if (err != null) return;
            d.dismiss();
            final String name = form.repoName(), desc = form.description();
            final boolean priv = form.isPrivate();
            g.workWithProgress(T("gh_uploading", "Uploading to GitHub"), false, (r, p) -> {
                r.close();
                ProjectGit.publish(g.a, g.dir, name, desc, priv, p);
                return true;
            }, ok -> {
                Snack.show(T("gh_uploaded", "Uploaded to GitHub"));
                g.onRefresh.run();
                if (done != null) done.run();
            });
        }));
        d.show();
    }

    /** Connect the project to a repository that already exists (your own, or any you can read). */
    public static void connectExisting(final GitContext g, final Runnable done) {
        if (!GitHubSession.connected(g.a)) {
            GitHubConnect.show(g.a, done);
            return;
        }
        CloneDialog.pick(g.a, T("git_connect_existing", "Connect an existing repository"), (owner, name) ->
                g.workWithProgress(T("git_connecting", "Connecting..."), true, (r, p) -> {
                    r.close();
                    return ProjectGit.connectExisting(g.a, g.dir, owner, name, p);
                }, mode -> {
                    if (mode.equals("pull")) {
                        g.workWithProgress(T("git_pulling", "Getting changes..."), true, (r, p) -> r.pull(false, p), o -> {
                            PullResult.report(g, o);
                            if (PullResult.changedFiles(o)) g.onFilesChanged.run();
                            g.onRefresh.run();
                            if (done != null) done.run();
                        });
                    } else {
                        Snack.show(T("git_connected_empty", "Connected. Press Push to upload your project."));
                        g.onRefresh.run();
                        if (done != null) done.run();
                    }
                }));
    }

    public static void disconnect(final GitContext g) {
        Dialogs.confirm(g.a, T("git_disconnect", "Disconnect from GitHub repository"),
                T("git_disconnect_q", "The project stays on this phone with its history. Nothing is deleted on GitHub."), T("ok", "OK"), () ->
                        g.work(r -> {
                            r.removeRemote("origin");
                            return true;
                        }, ok -> g.onRefresh.run()));
    }

    // ------------------------------------------------------------------ visibility

    public static void visibility(final GitContext g, final GitHubApi.Repo repo, final Runnable done) {
        final boolean toPrivate = !repo.isPrivate;
        String msg = toPrivate ? T("git_make_private_q", "Only you and people you invite will be able to see this repository.")
                : T("git_make_public_q", "Anyone on the internet will be able to see all files and the whole history of this repository.");
        Dialogs.confirm(g.a, toPrivate ? T("git_make_private", "Make private") : T("git_make_public", "Make public"), msg, T("ok", "OK"), () ->
                GitHubSession.async(() -> GitHubSession.api(g.a).setPrivate(repo.owner, repo.name, toPrivate), (res, err) -> {
                    if (err != null) g.error(err);
                    else {
                        Snack.show(T("git_done", "Done"));
                        if (done != null) done.run();
                    }
                }));
    }

    // ------------------------------------------------------------------ push

    public static void forcePush(final GitContext g, final Runnable done) {
        Dialogs.confirm(g.a, T("git_force_push", "Force push"),
                T("git_force_q", "This REPLACES what is on GitHub with your version. Commits that exist only on GitHub will be lost. Use it only if you are sure."),
                T("git_force_push", "Force push"), () ->
                        g.workWithProgress(T("git_pushing", "Pushing..."), true, (r, p) -> {
                            r.push(true, p);
                            return true;
                        }, ok -> {
                            Snack.show(T("git_pushed", "Pushed"));
                            if (done != null) done.run();
                        }));
    }

    // ------------------------------------------------------------------ fork / pull request

    /** No write access: fork the repository into the user's account, point "origin" at the fork and keep the original as "upstream". */
    public static void fork(final GitContext g, final GitHubApi.Repo original, final Runnable done) {
        Dialogs.confirm(g.a, T("git_fork", "Make my own copy (fork)"),
                T("git_fork_q", "You cannot change this repository directly. A fork is your own copy on GitHub where you can push, and then ask the owner to accept your changes."),
                T("ok", "OK"), () -> {
                    final BusyDialog busy = new BusyDialog(g.a, T("git_forking", "Creating your copy..."), false).show();
                    GitHubSession.async(() -> {
                        GitHubApi api = GitHubSession.api(g.a);
                        GitHubApi.Repo f = api.fork(original.owner, original.name);
                        // GitHub needs a moment before the copy can be used
                        for (int i = 0; i < 20; i++) {
                            try {
                                GitHubApi.Repo chk = api.repo(f.owner, f.name);
                                if (!chk.empty || chk.size > 0 || i > 6) break;
                            } catch (GitException ignored) {
                            }
                            Thread.sleep(1500);
                        }
                        try (GitRepo r = ProjectGit.open(g.a, g.dir)) {
                            r.setRemote("upstream", original.cloneUrl);
                            r.setRemote("origin", f.cloneUrl);
                        }
                        return f;
                    }, (res, err) -> {
                        busy.dismiss();
                        if (err != null) g.error(err);
                        else {
                            Snack.show(T("git_forked", "Done. Your changes will now be pushed to your copy."));
                            g.onRefresh.run();
                            if (done != null) done.run();
                        }
                    });
                });
    }

    /** Ask the owner of the original repository to accept the changes of the fork. */
    public static void pullRequest(final GitContext g, final GitHubApi.Repo fork, final String branch) {
        if (fork.parent == null || fork.parent.isEmpty() || !fork.parent.contains("/")) {
            Snack.show(T("git_no_parent", "This repository is not a fork."));
            return;
        }
        final String[] parent = fork.parent.split("/");
        LinearLayout col = Ui.column(g.a);
        col.setPadding(Ui.dp(g.a, 20), Ui.dp(g.a, 6), Ui.dp(g.a, 20), 0);
        col.addView(Ui.sectionLabel(g.a, T("git_pr_title", "Title")));
        final EditText title = Ui.input(g.a, T("git_pr_default", "My changes"), "");
        col.addView(title);
        col.addView(Ui.sectionLabel(g.a, T("git_pr_body", "Describe your changes (optional)")));
        final EditText body = Ui.input(g.a, "", "");
        col.addView(body);
        new AlertDialog.Builder(g.a).setTitle(T("git_pull_request", "Pull request") + " → " + fork.parent).setView(col)
                .setPositiveButton(T("git_send", "Send"), (d, w) ->
                        GitHubSession.async(() -> {
                            GitHubApi api = GitHubSession.api(g.a);
                            GitHubApi.Repo up = api.repo(parent[0], parent[1]);
                            return api.createPullRequest(parent[0], parent[1], title.getText().toString().trim(),
                                    body.getText().toString().trim(), fork.owner + ":" + branch, up.defaultBranch);
                        }, (url, err) -> {
                            if (err != null) g.error(err);
                            else {
                                Snack.show(T("git_pr_sent", "Pull request sent"));
                                if (url != null && !url.isEmpty()) openUrl(g, url);
                            }
                        }))
                .setNegativeButton(T("cancel", "Cancel"), null).show();
    }
}
