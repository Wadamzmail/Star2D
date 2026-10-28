package com.star4droid.star2d.git;

import android.content.Context;

import java.io.File;

/** Project-level git actions that need the signed-in account (token, identity); the plumbing is in {@link GitRepo}. */
public final class ProjectGit {
    private ProjectGit() {}

    private static boolean prepared;

    /** JGit looks for ~/.gitconfig: give it a writable home on Android before it first runs. */
    public static synchronized void prepare(Context c) {
        if (prepared) return;
        prepared = true;
        String h = System.getProperty("user.home");
        if (h == null || h.isEmpty() || !new File(h).canWrite()) System.setProperty("user.home", c.getFilesDir().getAbsolutePath());
    }

    /** The project's repository, created on first use. */
    public static GitRepo open(Context c, File dir) throws GitException {
        prepare(c);
        GitRepo r = GitRepo.isRepo(dir) ? GitRepo.open(dir, GitHubSession.token(c)) : GitRepo.init(dir, "main", GitHubSession.token(c));
        r.ensureIgnore();
        GitHubApi.User u = GitHubSession.user(c);
        if (u != null) r.setIdentity(u.name == null || u.name.isEmpty() ? u.login : u.name, u.email());
        return r;
    }

    /** Creates the GitHub repository, commits the whole project and pushes it. Returns the new repository. */
    public static GitHubApi.Repo publish(Context c, File dir, String name, String description, boolean isPrivate, Progress p) throws GitException {
        GitHubApi api = GitHubSession.api(c);
        p.update("GitHub", 0, 0);
        GitHubApi.Repo repo = api.createRepo(name, description, isPrivate);
        try (GitRepo r = open(c, dir)) {
            r.setRemote("origin", repo.cloneUrl);
            if (!r.hasCommits() || !r.status().isEmpty()) r.commit("Initial commit", null);
            r.push(false, p);
        }
        return repo;
    }

    /**
     * Points the project at an existing repository. Returns "linked" when GitHub has nothing yet (a push will fill it) or
     * "pull" when it already has work that should now be pulled and merged with the local files.
     */
    public static String connectExisting(Context c, File dir, String owner, String name, Progress p) throws GitException {
        GitHubApi.Repo repo = GitHubSession.api(c).repo(owner, name);
        try (GitRepo r = open(c, dir)) {
            r.setRemote("origin", repo.cloneUrl);
            r.fetch(p);
            boolean remoteHasBranch = false;
            for (GitRepo.BranchInfo b : r.branches()) if (b.remote) remoteHasBranch = true;
            if (!remoteHasBranch) return "linked";
            if (!r.status().isEmpty()) r.commit("Save project before connecting to GitHub", null);
            // the local branch may be called differently from GitHub's default branch (main / master)
            String cur = r.currentBranch();
            boolean hasCur = false, hasDefault = false;
            for (GitRepo.BranchInfo b : r.branches()) {
                if (b.remote && b.name.equals("origin/" + cur)) hasCur = true;
                if (b.remote && b.name.equals("origin/" + repo.defaultBranch)) hasDefault = true;
            }
            if (!hasCur && hasDefault && r.hasCommits()) r.renameBranch(cur, repo.defaultBranch);
            return "pull";
        }
    }
}
