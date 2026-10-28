package com.star4droid.star2d.git;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.StashApplyCommand;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One project folder as a git repository (JGit, HTTPS only). Every call blocks: run it on a worker thread.
 * Pure Java, so the logic can be exercised on a desktop JVM against local remotes.
 */
public final class GitRepo implements AutoCloseable {

    /** What the editor never wants in a repository: build output, caches and files regenerated from the scenes. */
    public static final String DEFAULT_IGNORE =
            "# Star2D: generated or machine-local files\n"
                    + "/dex/\n"
                    + "/bin/\n"
                    + "/logs/\n"
                    + "/log/\n"
                    + "/java/javac.*\n"
                    + "/java/java.version\n"
                    + "*.dex\n"
                    + "*.class\n"
                    + "/java/classes.dex\n"
                    + "/Events/JavaChanges.json\n"
                    + "/Events/*/UndoRedo.json\n"
                    + "# scene code is regenerated from the scenes and blocks on every Run\n"
                    + "/java/com/star4droid/Game/*.java\n"
                    + ".DS_Store\n";

    public enum Kind {ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICT}

    public static final class Change {
        public String path;
        public Kind kind;

        Change(String p, Kind k) {
            path = p;
            kind = k;
        }
    }

    public static final class BranchInfo {
        public String name;
        public boolean current, remote, hasRemote;
        public int ahead, behind;
        public String tip;
    }

    public static final class CommitInfo {
        public String id, shortId, message, fullMessage, author, email;
        public long time;
        public List<String> parents = new ArrayList<>();
        public List<String> refs = new ArrayList<>();
        /** graph: lane of this commit, lanes passing straight through the row, lanes merging in from above, lanes the parents continue in */
        public int lane;
        public int[] through = new int[0], from = new int[0], to = new int[0];
    }

    public static final class FileChange {
        public String path, oldPath, type;
    }

    public enum Outcome {UP_TO_DATE, FAST_FORWARD, MERGED, CONFLICTS, NO_REMOTE_BRANCH, LOCAL_CHANGES, NOT_POSSIBLE}

    public static final class MergeOutcome {
        public Outcome outcome;
        public List<String> paths = new ArrayList<>();
        public String message = "";
    }

    /** A conflicted file split into plain text and conflicting hunks. */
    public static final class Hunk {
        public boolean conflict;
        public String text, ours, theirs;
        public String oursLabel = "", theirsLabel = "";
        /** chosen resolution: 0 = undecided, 1 = ours, 2 = theirs, 3 = both (ours then theirs), 4 = custom */
        public int choice;
        public String custom;
    }

    private final Repository repo;
    private final Git git;
    private CredentialsProvider creds;
    private String authorName = "Star2D", authorEmail = "star2d@users.noreply.github.com";

    private GitRepo(Repository repo, String token) {
        this.repo = repo;
        this.git = new Git(repo);
        setToken(token);
    }

    // ------------------------------------------------------------------ open / init / clone

    public static boolean isRepo(File dir) {
        return new File(dir, ".git").exists();
    }

    public static GitRepo open(File dir, String token) throws GitException {
        try {
            return new GitRepo(Git.open(dir).getRepository(), token);
        } catch (IOException e) {
            throw new GitException(GitException.Kind.NOT_FOUND, "Not a git repository: " + dir, e);
        }
    }

    /** New repository with the default ignore file; the first commit is left to the caller. */
    public static GitRepo init(File dir, String branch, String token) throws GitException {
        try {
            Git g = Git.init().setDirectory(dir).setInitialBranch(branch == null ? "main" : branch).call();
            GitRepo r = new GitRepo(g.getRepository(), token);
            r.ensureIgnore();
            return r;
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public static GitRepo clone(String url, File dir, String token, Progress progress) throws GitException {
        try {
            Git g = Git.cloneRepository().setURI(url).setDirectory(dir)
                    .setCredentialsProvider(provider(token))
                    .setProgressMonitor(monitor(progress == null ? Progress.NONE : progress))
                    .call();
            GitRepo r = new GitRepo(g.getRepository(), token);
            r.ensureIgnore();
            return r;
        } catch (GitAPIException e) {
            deleteQuietly(dir);
            throw wrap(e);
        }
    }

    private boolean closed;

    /** Releases the repository's files; calling it twice is harmless. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        git.close();
        repo.close();
    }

    public File dir() {
        return repo.getWorkTree();
    }

    public void setToken(String token) {
        creds = provider(token);
    }

    public void setIdentity(String name, String email) {
        if (name != null && !name.isEmpty()) authorName = name;
        if (email != null && !email.isEmpty()) authorEmail = email;
    }

    private static CredentialsProvider provider(String token) {
        return token == null || token.isEmpty() ? null : new UsernamePasswordCredentialsProvider("x-access-token", token);
    }

    /** Writes the default .gitignore when the project has none. */
    public void ensureIgnore() {
        File f = new File(dir(), ".gitignore");
        if (f.exists()) return;
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(DEFAULT_IGNORE.getBytes("UTF-8"));
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ remote

    public String remoteUrl() {
        return repo.getConfig().getString("remote", "origin", "url");
    }

    public boolean hasRemote() {
        String u = remoteUrl();
        return u != null && !u.isEmpty();
    }

    public void setRemote(String name, String url) throws GitException {
        try {
            StoredConfig c = repo.getConfig();
            c.setString("remote", name, "url", url);
            c.setString("remote", name, "fetch", "+refs/heads/*:refs/remotes/" + name + "/*");
            c.save();
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    public void removeRemote(String name) throws GitException {
        try {
            StoredConfig c = repo.getConfig();
            c.unsetSection("remote", name);
            c.save();
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    public String currentBranch() {
        try {
            return repo.getBranch();
        } catch (IOException e) {
            return "main";
        }
    }

    public boolean hasCommits() {
        try {
            return repo.resolve("HEAD") != null;
        } catch (IOException e) {
            return false;
        }
    }

    public RepositoryState state() {
        return repo.getRepositoryState();
    }

    public boolean isMerging() {
        RepositoryState s = repo.getRepositoryState();
        return s == RepositoryState.MERGING || s == RepositoryState.MERGING_RESOLVED;
    }

    // ------------------------------------------------------------------ status / commit

    public List<Change> status() throws GitException {
        try {
            Status s = git.status().call();
            TreeMap<String, Change> m = new TreeMap<>();
            for (String p : s.getUntracked()) m.put(p, new Change(p, Kind.UNTRACKED));
            for (String p : s.getAdded()) m.put(p, new Change(p, Kind.ADDED));
            for (String p : s.getChanged()) m.put(p, new Change(p, Kind.MODIFIED));
            for (String p : s.getModified()) m.put(p, new Change(p, Kind.MODIFIED));
            for (String p : s.getRemoved()) m.put(p, new Change(p, Kind.DELETED));
            for (String p : s.getMissing()) m.put(p, new Change(p, Kind.DELETED));
            for (String p : s.getConflicting()) m.put(p, new Change(p, Kind.CONFLICT));
            return new ArrayList<>(m.values());
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    /** Commits the given paths ({@code null} = everything). During a merge everything is committed. Returns the new commit id. */
    public String commit(String message, Collection<String> paths) throws GitException {
        try {
            PersonIdent who = new PersonIdent(authorName, authorEmail);
            if (paths == null || isMerging()) {
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                return git.commit().setMessage(message).setAuthor(who).setCommitter(who).call().getName();
            }
            if (paths.isEmpty()) throw new GitException(GitException.Kind.OTHER, "Nothing selected");
            org.eclipse.jgit.api.CommitCommand cc = git.commit().setMessage(message).setAuthor(who).setCommitter(who);
            for (String p : paths) {
                if (new File(dir(), p).exists()) git.add().addFilepattern(p).call();
                else git.rm().addFilepattern(p).setCached(true).call();
                cc.setOnly(p);
            }
            return cc.call().getName();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    /** Unified diff of a file against the last commit (the working copy for uncommitted changes). */
    public String diff(String path) throws GitException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(repo);
            df.setPathFilter(PathFilter.create(path));
            df.setContext(4);
            AbstractTreeIterator oldTree = headTree();
            List<DiffEntry> es = df.scan(oldTree, new FileTreeIterator(repo));
            df.format(es);
            df.flush();
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    private AbstractTreeIterator headTree() throws IOException {
        ObjectId head = repo.resolve("HEAD^{tree}");
        if (head == null) return new org.eclipse.jgit.treewalk.EmptyTreeIterator();
        CanonicalTreeParser p = new CanonicalTreeParser();
        try (org.eclipse.jgit.lib.ObjectReader r = repo.newObjectReader()) {
            p.reset(r, head);
        }
        return p;
    }

    /** Throws away the uncommitted changes of one file (a new file is deleted). */
    public void discard(String path) throws GitException {
        try {
            boolean tracked = repo.resolve("HEAD:" + path) != null;
            if (tracked) {
                git.reset().setRef("HEAD").addPath(path).call();
                git.checkout().addPath(path).call();
            } else {
                git.reset().addPath(path).call();
                new File(dir(), path).delete();
            }
        } catch (GitAPIException | IOException e) {
            throw wrapAny(e);
        }
    }

    public void discardAll() throws GitException {
        try {
            if (hasCommits()) git.reset().setMode(ResetCommand.ResetType.HARD).call();
            git.clean().setCleanDirectories(true).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ fetch / pull / push

    public void fetch(Progress progress) throws GitException {
        if (!hasRemote()) throw new GitException(GitException.Kind.NOT_FOUND, "No remote");
        try {
            git.fetch().setRemote("origin").setRemoveDeletedRefs(true).setCredentialsProvider(creds)
                    .setProgressMonitor(monitor(progress == null ? Progress.NONE : progress)).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    /** Fetches and merges the same-named remote branch. {@code fastForwardOnly} refuses a merge commit. */
    public MergeOutcome pull(boolean fastForwardOnly, Progress progress) throws GitException {
        fetch(progress);
        String branch = currentBranch();
        try {
            ObjectId remote = repo.resolve("refs/remotes/origin/" + branch);
            MergeOutcome o = new MergeOutcome();
            if (remote == null) {
                o.outcome = Outcome.NO_REMOTE_BRANCH;
                return o;
            }
            if (!hasCommits()) {
                // empty local repository: just take the remote branch
                git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/" + branch)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setForced(true).call();
                o.outcome = Outcome.FAST_FORWARD;
                return o;
            }
            return merge(remote, "origin/" + branch, fastForwardOnly);
        } catch (GitAPIException | IOException e) {
            throw wrapAny(e);
        }
    }

    public MergeOutcome merge(String branchName, boolean fastForwardOnly) throws GitException {
        try {
            ObjectId id = repo.resolve(branchName);
            if (id == null) throw new GitException(GitException.Kind.NOT_FOUND, "No such branch: " + branchName);
            return merge(id, branchName, fastForwardOnly);
        } catch (IOException e) {
            throw wrapAny(e);
        }
    }

    private MergeOutcome merge(ObjectId id, String name, boolean ffOnly) throws GitException {
        try {
            MergeResult r = git.merge().include(id)
                    .setFastForward(ffOnly ? MergeCommand.FastForwardMode.FF_ONLY : MergeCommand.FastForwardMode.FF)
                    .setCommit(true).setMessage("Merge " + name + " into " + currentBranch()).call();
            MergeOutcome o = new MergeOutcome();
            switch (r.getMergeStatus()) {
                case ALREADY_UP_TO_DATE: o.outcome = Outcome.UP_TO_DATE; break;
                case FAST_FORWARD: case FAST_FORWARD_SQUASHED: o.outcome = Outcome.FAST_FORWARD; break;
                case MERGED: case MERGED_SQUASHED: case MERGED_NOT_COMMITTED: o.outcome = Outcome.MERGED; break;
                case CONFLICTING:
                    o.outcome = Outcome.CONFLICTS;
                    if (r.getConflicts() != null) o.paths.addAll(r.getConflicts().keySet());
                    break;
                case CHECKOUT_CONFLICT:
                    o.outcome = Outcome.LOCAL_CHANGES;
                    if (r.getCheckoutConflicts() != null) o.paths.addAll(r.getCheckoutConflicts());
                    break;
                case FAILED:
                    o.outcome = Outcome.LOCAL_CHANGES;
                    if (r.getFailingPaths() != null) o.paths.addAll(r.getFailingPaths().keySet());
                    break;
                default: o.outcome = Outcome.NOT_POSSIBLE;
            }
            if (o.outcome == Outcome.NOT_POSSIBLE && ffOnly) o.message = "Fast-forward is not possible";
            return o;
        } catch (org.eclipse.jgit.api.errors.CheckoutConflictException e) {
            MergeOutcome o = new MergeOutcome();
            o.outcome = Outcome.LOCAL_CHANGES;
            o.paths.addAll(e.getConflictingPaths());
            return o;
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void abortMerge() throws GitException {
        try {
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    /** Pushes the current branch. A plain push the server refuses (remote has newer work) throws {@link GitException.Kind#REJECTED}. */
    public void push(boolean force, Progress progress) throws GitException {
        if (!hasRemote()) throw new GitException(GitException.Kind.NOT_FOUND, "No remote");
        String branch = currentBranch();
        try {
            Iterable<PushResult> rs = git.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/" + branch + ":refs/heads/" + branch))
                    .setForce(force).setCredentialsProvider(creds)
                    .setProgressMonitor(monitor(progress == null ? Progress.NONE : progress)).call();
            for (PushResult pr : rs) {
                for (RemoteRefUpdate u : pr.getRemoteUpdates()) {
                    switch (u.getStatus()) {
                        case OK: case UP_TO_DATE: break;
                        case REJECTED_NONFASTFORWARD: case REJECTED_REMOTE_CHANGED:
                            throw new GitException(GitException.Kind.REJECTED, "The remote has newer commits. Pull first, or force push.");
                        case REJECTED_OTHER_REASON: case REJECTED_NODELETE:
                            String m = u.getMessage() == null ? "Rejected" : u.getMessage();
                            throw new GitException(m.toLowerCase().contains("protected") || m.toLowerCase().contains("permission")
                                    ? GitException.Kind.AUTH : GitException.Kind.REJECTED, m);
                        default:
                            throw new GitException(GitException.Kind.OTHER, "Push failed: " + u.getStatus() + (u.getMessage() == null ? "" : " " + u.getMessage()));
                    }
                }
            }
            StoredConfig c = repo.getConfig();
            c.setString("branch", branch, "remote", "origin");
            c.setString("branch", branch, "merge", "refs/heads/" + branch);
            c.save();
        } catch (GitAPIException | IOException e) {
            throw wrapAny(e);
        }
    }

    // ------------------------------------------------------------------ branches / tags

    public List<BranchInfo> branches() throws GitException {
        try {
            List<BranchInfo> out = new ArrayList<>();
            String cur = currentBranch();
            Map<String, Boolean> local = new HashMap<>();
            for (Ref r : git.branchList().call()) {
                BranchInfo b = new BranchInfo();
                b.name = Repository.shortenRefName(r.getName());
                b.current = b.name.equals(cur);
                b.tip = r.getObjectId() == null ? "" : r.getObjectId().abbreviate(7).name();
                BranchTrackingStatus t = BranchTrackingStatus.of(repo, b.name);
                if (t != null) {
                    b.hasRemote = true;
                    b.ahead = t.getAheadCount();
                    b.behind = t.getBehindCount();
                }
                out.add(b);
                local.put(b.name, true);
            }
            for (Ref r : git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call()) {
                String n = Repository.shortenRefName(r.getName());
                if (n.endsWith("/HEAD")) continue;
                String shortName = n.contains("/") ? n.substring(n.indexOf('/') + 1) : n;
                if (local.containsKey(shortName)) continue;
                BranchInfo b = new BranchInfo();
                b.name = n;
                b.remote = true;
                b.tip = r.getObjectId() == null ? "" : r.getObjectId().abbreviate(7).name();
                out.add(b);
            }
            Collections.sort(out, new Comparator<BranchInfo>() {
                public int compare(BranchInfo a, BranchInfo b) {
                    if (a.current != b.current) return a.current ? -1 : 1;
                    if (a.remote != b.remote) return a.remote ? 1 : -1;
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            return out;
        } catch (GitAPIException | IOException e) {
            throw wrapAny(e);
        }
    }

    public void createBranch(String name, boolean checkout) throws GitException {
        try {
            if (!hasCommits()) throw new GitException(GitException.Kind.OTHER, "Make a first commit before creating branches");
            git.branchCreate().setName(name).call();
            if (checkout) checkout(name);
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    /** Switches branch; a remote-only name ("origin/x") creates a tracking branch. Uncommitted changes that would be lost stop it. */
    public void checkout(String name) throws GitException {
        try {
            if (name.startsWith("origin/")) {
                String local = name.substring(7);
                git.checkout().setCreateBranch(true).setName(local).setStartPoint(name)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).call();
            } else {
                git.checkout().setName(name).call();
            }
        } catch (org.eclipse.jgit.api.errors.CheckoutConflictException e) {
            throw new GitException(GitException.Kind.DIRTY, "Commit or stash your changes first (" + e.getConflictingPaths() + ")", e);
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void deleteBranch(String name, boolean force) throws GitException {
        try {
            git.branchDelete().setBranchNames(name).setForce(force).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void renameBranch(String from, String to) throws GitException {
        try {
            git.branchRename().setOldName(from).setNewName(to).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public List<String> tags() {
        List<String> out = new ArrayList<>(repo.getTags().keySet());
        Collections.sort(out);
        return out;
    }

    public void createTag(String name) throws GitException {
        try {
            git.tag().setName(name).setAnnotated(false).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void deleteTag(String name) throws GitException {
        try {
            git.tagDelete().setTags(name).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void pushTags(Progress progress) throws GitException {
        try {
            git.push().setRemote("origin").setPushTags().setCredentialsProvider(creds)
                    .setProgressMonitor(monitor(progress == null ? Progress.NONE : progress)).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ history

    /** The newest {@code limit} commits of all branches with the lane layout for drawing the tree. */
    public List<CommitInfo> log(int limit) throws GitException {
        try (RevWalk walk = new RevWalk(repo)) {
            for (Ref r : repo.getRefDatabase().getRefsByPrefix("refs/heads/")) walk.markStart(walk.parseCommit(r.getObjectId()));
            for (Ref r : repo.getRefDatabase().getRefsByPrefix("refs/remotes/")) {
                if (!r.getName().endsWith("/HEAD") && r.getObjectId() != null) walk.markStart(walk.parseCommit(r.getObjectId()));
            }
            walk.sort(RevSort.TOPO);
            walk.sort(RevSort.COMMIT_TIME_DESC, true);
            Map<String, List<String>> decorations = new HashMap<>();
            for (Ref r : repo.getRefDatabase().getRefs()) {
                if (r.getObjectId() == null) continue;
                String n = r.getName();
                if (n.endsWith("/HEAD") && !n.equals("HEAD")) continue;
                String label = n.equals("HEAD") ? null : Repository.shortenRefName(n);
                if (label == null) continue;
                ObjectId target = r.getPeeledObjectId() != null ? r.getPeeledObjectId() : r.getObjectId();
                List<String> l = decorations.get(target.getName());
                if (l == null) {
                    l = new ArrayList<>();
                    decorations.put(target.getName(), l);
                }
                l.add(label);
            }
            List<CommitInfo> out = new ArrayList<>();
            List<String> lanes = new ArrayList<>(); // commit id each lane is waiting for (null = free)
            for (RevCommit c : walk) {
                if (out.size() >= limit) break;
                CommitInfo ci = new CommitInfo();
                ci.id = c.getName();
                ci.shortId = c.abbreviate(7).name();
                ci.message = c.getShortMessage();
                ci.fullMessage = c.getFullMessage();
                PersonIdent a = c.getAuthorIdent();
                ci.author = a == null ? "" : a.getName();
                ci.email = a == null ? "" : a.getEmailAddress();
                ci.time = c.getCommitTime() * 1000L;
                for (RevCommit p : c.getParents()) ci.parents.add(p.getName());
                List<String> d = decorations.get(ci.id);
                if (d != null) ci.refs.addAll(d);
                // lanes waiting for this commit
                List<Integer> incoming = new ArrayList<>();
                for (int i = 0; i < lanes.size(); i++) if (ci.id.equals(lanes.get(i))) incoming.add(i);
                int lane;
                if (incoming.isEmpty()) {
                    lane = freeLane(lanes);
                } else {
                    lane = incoming.get(0);
                }
                List<Integer> through = new ArrayList<>();
                for (int i = 0; i < lanes.size(); i++) if (lanes.get(i) != null && !incoming.contains(i) && i != lane) through.add(i);
                ci.lane = lane;
                ci.from = toArray(incoming);
                ci.through = toArray(through);
                for (int i : incoming) lanes.set(i, null);
                // parents
                List<Integer> to = new ArrayList<>();
                for (int pi = 0; pi < ci.parents.size(); pi++) {
                    String pid = ci.parents.get(pi);
                    int existing = lanes.indexOf(pid);
                    int pl;
                    if (existing >= 0) {
                        pl = existing;
                    } else if (pi == 0) {
                        while (lanes.size() <= lane) lanes.add(null);
                        pl = lane;
                        lanes.set(pl, pid);
                    } else {
                        pl = freeLane(lanes);
                        lanes.set(pl, pid);
                    }
                    to.add(pl);
                }
                ci.to = toArray(to);
                while (!lanes.isEmpty() && lanes.get(lanes.size() - 1) == null) lanes.remove(lanes.size() - 1);
                out.add(ci);
            }
            return out;
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    private static int freeLane(List<String> lanes) {
        for (int i = 0; i < lanes.size(); i++) if (lanes.get(i) == null) return i;
        lanes.add(null);
        return lanes.size() - 1;
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) a[i] = l.get(i);
        return a;
    }

    /** Files a commit changed (against its first parent). */
    public List<FileChange> commitFiles(String commitId) throws GitException {
        try (RevWalk walk = new RevWalk(repo); DiffFormatter df = new DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDetectRenames(true);
            RevCommit c = walk.parseCommit(repo.resolve(commitId));
            AbstractTreeIterator parent = c.getParentCount() > 0 ? treeOf(walk.parseCommit(c.getParent(0))) : new org.eclipse.jgit.treewalk.EmptyTreeIterator();
            List<FileChange> out = new ArrayList<>();
            for (DiffEntry e : df.scan(parent, treeOf(c))) {
                FileChange f = new FileChange();
                f.type = e.getChangeType().name();
                f.path = e.getChangeType() == DiffEntry.ChangeType.DELETE ? e.getOldPath() : e.getNewPath();
                f.oldPath = e.getOldPath();
                out.add(f);
            }
            return out;
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    public String commitDiff(String commitId, String path) throws GitException {
        try (RevWalk walk = new RevWalk(repo); ByteArrayOutputStream out = new ByteArrayOutputStream(); DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(repo);
            df.setContext(4);
            if (path != null) df.setPathFilter(PathFilter.create(path));
            RevCommit c = walk.parseCommit(repo.resolve(commitId));
            AbstractTreeIterator parent = c.getParentCount() > 0 ? treeOf(walk.parseCommit(c.getParent(0))) : new org.eclipse.jgit.treewalk.EmptyTreeIterator();
            df.format(df.scan(parent, treeOf(c)));
            df.flush();
            return out.toString("UTF-8");
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    private AbstractTreeIterator treeOf(RevCommit c) throws IOException {
        CanonicalTreeParser p = new CanonicalTreeParser();
        try (org.eclipse.jgit.lib.ObjectReader r = repo.newObjectReader()) {
            p.reset(r, c.getTree());
        }
        return p;
    }

    /** New commit that undoes the given one. */
    public MergeOutcome revertCommit(String commitId) throws GitException {
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit c = walk.parseCommit(repo.resolve(commitId));
            RevCommit r = git.revert().include(c).call();
            MergeOutcome o = new MergeOutcome();
            o.outcome = r == null ? Outcome.CONFLICTS : Outcome.MERGED;
            if (r == null) o.paths.addAll(conflictPaths());
            return o;
        } catch (GitAPIException | IOException e) {
            throw wrapAny(e);
        }
    }

    /** Moves the current branch to a commit. mode: "soft", "mixed" or "hard" (hard throws away working changes). */
    public void resetTo(String commitId, String mode) throws GitException {
        try {
            ResetCommand.ResetType t = "hard".equals(mode) ? ResetCommand.ResetType.HARD
                    : "soft".equals(mode) ? ResetCommand.ResetType.SOFT : ResetCommand.ResetType.MIXED;
            git.reset().setRef(commitId).setMode(t).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ stash

    public boolean stash(String message) throws GitException {
        try {
            return git.stashCreate().setWorkingDirectoryMessage(message).setIncludeUntracked(true).call() != null;
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public List<String> stashes() throws GitException {
        try {
            List<String> out = new ArrayList<>();
            for (RevCommit c : git.stashList().call()) out.add(c.getShortMessage());
            return out;
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void stashApply(int index, boolean drop) throws GitException {
        try {
            git.stashApply().setStashRef("stash@{" + index + "}").call();
            if (drop) git.stashDrop().setStashRef(index).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    public void stashDrop(int index) throws GitException {
        try {
            git.stashDrop().setStashRef(index).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ conflicts

    public List<String> conflictPaths() throws GitException {
        try {
            return new ArrayList<>(new TreeMap<String, Object>(toMap(git.status().call().getConflicting())).keySet());
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    private static Map<String, Object> toMap(java.util.Set<String> s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String x : s) m.put(x, 1);
        return m;
    }

    /** Parses a file containing merge markers; a file without markers returns one plain hunk. */
    public List<Hunk> conflictHunks(String path) throws GitException {
        try {
            String text = new String(java.nio.file.Files.readAllBytes(new File(dir(), path).toPath()), Charset.forName("UTF-8"));
            return parseConflict(text);
        } catch (IOException e) {
            throw new GitException(GitException.Kind.OTHER, e.getMessage(), e);
        }
    }

    public static List<Hunk> parseConflict(String text) {
        List<Hunk> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder plain = new StringBuilder(), ours = new StringBuilder(), theirs = new StringBuilder();
        int state = 0; // 0 plain, 1 ours, 2 base, 3 theirs
        String oursLabel = "", theirsLabel = "";
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean last = i == lines.length - 1;
            String nl = last ? "" : "\n";
            if (state == 0 && l.startsWith("<<<<<<< ")) {
                if (plain.length() > 0) {
                    Hunk h = new Hunk();
                    h.text = plain.toString();
                    out.add(h);
                    plain.setLength(0);
                }
                ours.setLength(0);
                theirs.setLength(0);
                oursLabel = l.substring(8).trim();
                state = 1;
            } else if (state == 1 && l.startsWith("||||||| ")) {
                state = 2;
            } else if ((state == 1 || state == 2) && l.equals("=======")) {
                state = 3;
            } else if (state == 3 && l.startsWith(">>>>>>> ")) {
                theirsLabel = l.substring(8).trim();
                Hunk h = new Hunk();
                h.conflict = true;
                h.ours = ours.toString();
                h.theirs = theirs.toString();
                h.oursLabel = oursLabel;
                h.theirsLabel = theirsLabel;
                out.add(h);
                state = 0;
            } else if (state == 0) {
                plain.append(l).append(nl);
            } else if (state == 1) {
                ours.append(l).append("\n");
            } else if (state == 3) {
                theirs.append(l).append("\n");
            }
        }
        if (plain.length() > 0) {
            Hunk h = new Hunk();
            h.text = plain.toString();
            out.add(h);
        }
        return out;
    }

    /** Text of a resolved file from its hunks; undecided hunks keep both sides. */
    public static String assemble(List<Hunk> hunks) {
        StringBuilder sb = new StringBuilder();
        for (Hunk h : hunks) {
            if (!h.conflict) {
                sb.append(h.text);
                continue;
            }
            switch (h.choice) {
                case 1: sb.append(h.ours); break;
                case 2: sb.append(h.theirs); break;
                case 4: sb.append(h.custom == null ? "" : h.custom); break;
                default: sb.append(h.ours).append(h.theirs);
            }
        }
        return sb.toString();
    }

    /** Writes the resolved text and marks the file resolved. */
    public void resolve(String path, String content) throws GitException {
        try {
            try (FileOutputStream o = new FileOutputStream(new File(dir(), path))) {
                o.write(content.getBytes("UTF-8"));
            }
            git.add().addFilepattern(path).call();
        } catch (IOException | GitAPIException e) {
            throw wrapAny(e);
        }
    }

    /** Takes one whole side of a conflicted file ("ours" or "theirs"). */
    public void resolveSide(String path, boolean ours) throws GitException {
        try {
            git.checkout().setStage(ours ? CheckoutCommand.Stage.OURS : CheckoutCommand.Stage.THEIRS).addPath(path).call();
            git.add().addFilepattern(path).call();
        } catch (GitAPIException e) {
            throw wrap(e);
        }
    }

    // ------------------------------------------------------------------ files above GitHub's limits

    /** Changed files bigger than {@code limit} bytes. */
    public List<String> bigFiles(long limit) throws GitException {
        List<String> out = new ArrayList<>();
        for (Change c : status()) {
            if (c.kind == Kind.DELETED) continue;
            if (new File(dir(), c.path).length() > limit) out.add(c.path);
        }
        return out;
    }

    // ------------------------------------------------------------------ errors / progress

    private static ProgressMonitor monitor(final Progress p) {
        return new ProgressMonitor() {
            String task = "";
            int total, done;

            public void start(int totalTasks) {}

            public void beginTask(String title, int totalWork) {
                task = title;
                total = totalWork;
                done = 0;
                p.update(task, 0, total);
            }

            public void update(int completed) {
                done += completed;
                p.update(task, done, total);
            }

            public void endTask() {}

            public boolean isCancelled() {
                return p.cancelled();
            }

            // JGit 5.13 declares these on the interface
            public void showDuration(boolean enabled) {}
        };
    }

    private static GitException wrapAny(Exception e) {
        if (e instanceof GitAPIException) return wrap((GitAPIException) e);
        if (e instanceof GitException) return (GitException) e;
        return new GitException(GitException.Kind.OTHER, e.getMessage() == null ? e.toString() : e.getMessage(), e);
    }

    static GitException wrap(GitAPIException e) {
        String m = e.getMessage() == null ? e.toString() : e.getMessage();
        String low = m.toLowerCase();
        Throwable c = e.getCause();
        if (e instanceof TransportException || c instanceof java.net.UnknownHostException || c instanceof java.net.SocketException || c instanceof java.net.SocketTimeoutException) {
            if (low.contains("not authorized") || low.contains("authentication") || low.contains("401") || low.contains("403") || low.contains("auth"))
                return new GitException(GitException.Kind.AUTH, m, e);
            if (low.contains("not found") || low.contains("404")) return new GitException(GitException.Kind.NOT_FOUND, m, e);
            return new GitException(GitException.Kind.NETWORK, m, e);
        }
        if (e instanceof org.eclipse.jgit.api.errors.CheckoutConflictException) return new GitException(GitException.Kind.DIRTY, m, e);
        if (e instanceof org.eclipse.jgit.api.errors.RefNotFoundException) return new GitException(GitException.Kind.NOT_FOUND, m, e);
        return new GitException(GitException.Kind.OTHER, m, e);
    }

    private static void deleteQuietly(File f) {
        if (f == null || !f.exists()) return;
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) deleteQuietly(c);
        f.delete();
    }
}
