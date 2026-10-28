package com.star4droid.star2d.git.ui;

import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;

/** Turns the outcome of a pull / merge into a message, and opens the conflict screen when needed. */
final class PullResult {
    private PullResult() {}

    /** True when the files on disk are different now (the open project has to be reloaded). */
    static boolean changedFiles(GitRepo.MergeOutcome o) {
        return o.outcome == GitRepo.Outcome.FAST_FORWARD || o.outcome == GitRepo.Outcome.MERGED;
    }

    static void report(GitContext g, GitRepo.MergeOutcome o) {
        switch (o.outcome) {
            case UP_TO_DATE:
                Snack.show(Tr.t("git_up_to_date", "Already up to date"));
                break;
            case FAST_FORWARD:
                Snack.show(Tr.t("git_updated", "Updated from GitHub"));
                break;
            case MERGED:
                Snack.show(Tr.t("git_merged", "Merged"));
                break;
            case CONFLICTS:
                Snack.show(Tr.t("git_err_conflict", "Conflicts"));
                ConflictPage.show(g);
                break;
            case NO_REMOTE_BRANCH:
                Snack.show(Tr.t("git_no_remote_branch", "This branch is not on GitHub yet. Push it first."));
                break;
            case LOCAL_CHANGES:
                new androidx.appcompat.app.AlertDialog.Builder(g.a)
                        .setTitle(Tr.t("git_err_dirty", "You have unsaved changes"))
                        .setMessage(Tr.t("git_dirty_pull", "Your changes would be overwritten by this update. Commit them first, or put them aside with Stash."))
                        .setPositiveButton(Tr.t("ok", "OK"), null).show();
                break;
            default:
                new androidx.appcompat.app.AlertDialog.Builder(g.a)
                        .setTitle(Tr.t("git_cannot_merge", "Cannot merge automatically"))
                        .setMessage(o.message == null ? "" : o.message)
                        .setPositiveButton(Tr.t("ok", "OK"), null).show();
        }
    }
}
