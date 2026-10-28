package com.star4droid.star2d.git.ui;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.git.GitException;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.git.ProjectGit;
import com.star4droid.star2d.ui.Tr;

import java.io.File;

/** What every git screen of one open project shares: where it lives, how to run work and how to tell the editor. */
public final class GitContext {
    public interface RepoJob<T> { T run(GitRepo r) throws Exception; }
    public interface Result<T> { void on(T value); }
    public interface Saver { void save(Runnable next); }

    public final Activity a;
    public final File dir;
    /** Files on disk were replaced (pull, reset, checkout...): reload the open project. */
    public Runnable onFilesChanged = () -> {};
    /** Anything git related changed: redraw the panel. */
    public Runnable onRefresh = () -> {};
    /** Writes the open scenes to disk before git looks at them. */
    public Saver saver = Runnable::run;

    public GitContext(Activity a, File dir) {
        this.a = a;
        this.dir = dir;
    }

    static String T(String k, String d) { return Tr.t(k, d); }

    /** Saves the editor, then runs {@code job} on a worker with the repository open; the result comes back on the UI thread. */
    public <T> void work(final RepoJob<T> job, final Result<T> ok) {
        work(job, ok, true);
    }

    /** While a merge is open the files hold conflict markers: saving the editor would overwrite them. */
    private boolean merging() {
        return new File(dir, ".git/MERGE_HEAD").exists();
    }

    private void save(Runnable next) {
        if (merging()) next.run();
        else saver.save(next);
    }

    public <T> void work(final RepoJob<T> job, final Result<T> ok, final boolean showErrors) {
        save(() -> GitHubSession.async(() -> {
            try (GitRepo r = ProjectGit.open(a, dir)) {
                return job.run(r);
            }
        }, (res, err) -> {
            if (err != null) {
                if (showErrors) error(err);
                return;
            }
            if (ok != null) ok.on(res);
        }));
    }

    /** Same, with a progress dialog (cancellable when the job supports it). */
    public <T> void workWithProgress(final String title, final boolean cancellable, final ProgressJob<T> job, final Result<T> ok) {
        save(() -> {
            final BusyDialog busy = new BusyDialog(a, title, cancellable).show();
            GitHubSession.async(() -> {
                try (GitRepo r = ProjectGit.open(a, dir)) {
                    return job.run(r, busy);
                }
            }, (res, err) -> {
                busy.dismiss();
                if (err != null) {
                    if (!busy.cancelled()) error(err);
                    return;
                }
                if (ok != null) ok.on(res);
            });
        });
    }

    public interface ProgressJob<T> { T run(GitRepo r, com.star4droid.star2d.git.Progress p) throws Exception; }

    public void error(GitException e) {
        String title, hint;
        switch (e.kind) {
            case AUTH:
                title = T("git_err_auth", "Not allowed");
                hint = T("git_err_auth_h", "Check that you are connected to GitHub and have write access to this repository.");
                break;
            case NETWORK:
                title = T("git_err_net", "No connection");
                hint = T("git_err_net_h", "Check your internet connection and try again.");
                break;
            case REJECTED:
                title = T("git_err_rejected", "GitHub has newer changes");
                hint = T("git_err_rejected_h", "Pull first, then push again. Force push replaces what is on GitHub.");
                break;
            case CONFLICT:
                title = T("git_err_conflict", "Conflicts");
                hint = T("git_err_conflict_h", "Some files were changed on both sides. Resolve them from the Git panel.");
                break;
            case NOT_FOUND:
                title = T("git_err_notfound", "Not found");
                hint = T("git_err_notfound_h", "The repository or branch does not exist, or you cannot see it.");
                break;
            case DIRTY:
                title = T("git_err_dirty", "You have unsaved changes");
                hint = T("git_err_dirty_h", "Commit or stash your changes first.");
                break;
            default:
                title = T("git_err_other", "Git error");
                hint = "";
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        new AlertDialog.Builder(a).setTitle(title)
                .setMessage((hint.isEmpty() ? "" : hint + "\n\n") + msg)
                .setPositiveButton(T("ok", "OK"), null).show();
    }
}
