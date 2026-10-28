package com.star4droid.star2d.git;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** The small part of the GitHub REST API the editor needs (no git protocol here, see {@link GitRepo}). */
public final class GitHubApi {
    /** OAuth App client id used for the device flow (a public value, no secret is needed). */
    public static final String CLIENT_ID = "Ov23liT7W1qae30HY2Ux";
    public static final String SCOPE = "repo";

    private static final String API = "https://api.github.com";

    public static final class User {
        public String login, name, avatarUrl;
        public long id;

        /** noreply address, so commits don't expose a private email. */
        public String email() {
            return id + "+" + login + "@users.noreply.github.com";
        }
    }

    public static final class Repo {
        public String owner, name, fullName, description, cloneUrl, htmlUrl, defaultBranch, parent;
        public boolean isPrivate, fork, canPush, canAdmin, empty;
        public long size;
        public String updatedAt;
    }

    public static final class DeviceCode {
        public String deviceCode, userCode, verificationUri;
        public int interval = 5, expiresIn = 900;
    }

    /** Result of one device-flow poll. */
    public static final class Poll {
        public enum State {PENDING, SLOW_DOWN, DONE, EXPIRED, DENIED, ERROR}

        public State state;
        public String token, message;
        public int interval;
    }

    private final String token;

    public GitHubApi(String token) {
        this.token = token;
    }

    // ------------------------------------------------------------------ device flow

    public static DeviceCode startDeviceFlow() throws GitException {
        JsonObject o = post("https://github.com/login/device/code", "client_id=" + enc(CLIENT_ID) + "&scope=" + enc(SCOPE), null);
        if (o.has("error")) throw new GitException(GitException.Kind.AUTH, str(o, "error_description", str(o, "error", "error")));
        DeviceCode d = new DeviceCode();
        d.deviceCode = str(o, "device_code", "");
        d.userCode = str(o, "user_code", "");
        d.verificationUri = str(o, "verification_uri", "https://github.com/login/device");
        d.interval = o.has("interval") ? o.get("interval").getAsInt() : 5;
        d.expiresIn = o.has("expires_in") ? o.get("expires_in").getAsInt() : 900;
        return d;
    }

    public static Poll pollDeviceFlow(String deviceCode) throws GitException {
        JsonObject o = post("https://github.com/login/oauth/access_token",
                "client_id=" + enc(CLIENT_ID) + "&device_code=" + enc(deviceCode)
                        + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"), null);
        Poll p = new Poll();
        if (o.has("access_token")) {
            p.state = Poll.State.DONE;
            p.token = o.get("access_token").getAsString();
            return p;
        }
        String err = str(o, "error", "");
        p.message = str(o, "error_description", err);
        p.interval = o.has("interval") ? o.get("interval").getAsInt() : 0;
        switch (err) {
            case "authorization_pending": p.state = Poll.State.PENDING; break;
            case "slow_down": p.state = Poll.State.SLOW_DOWN; break;
            case "expired_token": p.state = Poll.State.EXPIRED; break;
            case "access_denied": p.state = Poll.State.DENIED; break;
            default: p.state = Poll.State.ERROR;
        }
        return p;
    }

    // ------------------------------------------------------------------ account / repositories

    public User user() throws GitException {
        JsonObject o = get("/user").getAsJsonObject();
        User u = new User();
        u.login = str(o, "login", "");
        u.name = str(o, "name", u.login);
        u.avatarUrl = str(o, "avatar_url", "");
        u.id = o.has("id") ? o.get("id").getAsLong() : 0;
        return u;
    }

    /** One page (up to 100) of the repositories the user can reach, most recently pushed first. */
    public List<Repo> repos(int page) throws GitException {
        JsonArray a = get("/user/repos?per_page=100&sort=pushed&affiliation=owner,collaborator,organization_member&page=" + page).getAsJsonArray();
        List<Repo> out = new ArrayList<>();
        for (JsonElement e : a) out.add(repoOf(e.getAsJsonObject()));
        return out;
    }

    public Repo repo(String owner, String name) throws GitException {
        return repoOf(get("/repos/" + enc(owner) + "/" + enc(name)).getAsJsonObject());
    }

    public Repo createRepo(String name, String description, boolean isPrivate) throws GitException {
        JsonObject b = new JsonObject();
        b.addProperty("name", name);
        b.addProperty("description", description == null ? "" : description);
        b.addProperty("private", isPrivate);
        b.addProperty("auto_init", false);
        return repoOf(send("POST", "/user/repos", b.toString()).getAsJsonObject());
    }

    public Repo setPrivate(String owner, String name, boolean isPrivate) throws GitException {
        JsonObject b = new JsonObject();
        b.addProperty("private", isPrivate);
        return repoOf(send("PATCH", "/repos/" + enc(owner) + "/" + enc(name), b.toString()).getAsJsonObject());
    }

    /** Forks into the user's account; the fork may take a few seconds to become usable. */
    public Repo fork(String owner, String name) throws GitException {
        return repoOf(send("POST", "/repos/" + enc(owner) + "/" + enc(name) + "/forks", "{}").getAsJsonObject());
    }

    /** Opens a pull request; returns its web address. */
    public String createPullRequest(String owner, String name, String title, String body, String head, String base) throws GitException {
        JsonObject b = new JsonObject();
        b.addProperty("title", title);
        b.addProperty("body", body == null ? "" : body);
        b.addProperty("head", head);
        b.addProperty("base", base);
        return str(send("POST", "/repos/" + enc(owner) + "/" + enc(name) + "/pulls", b.toString()).getAsJsonObject(), "html_url", "");
    }

    /** True when a repository with this name already exists for the user. */
    public boolean exists(String owner, String name) {
        try {
            repo(owner, name);
            return true;
        } catch (GitException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ urls

    /** {owner, name} from "https://github.com/o/n(.git)", "git@github.com:o/n.git" or "o/n"; null when it isn't one. */
    public static String[] parseRepoUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".git")) u = u.substring(0, u.length() - 4);
        String rest;
        int i = u.indexOf("github.com");
        if (i >= 0) {
            rest = u.substring(i + "github.com".length());
            if (rest.startsWith("/") || rest.startsWith(":")) rest = rest.substring(1);
        } else if (u.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            rest = u;
        } else {
            return null;
        }
        String[] p = rest.split("/");
        if (p.length < 2 || p[0].isEmpty() || p[1].isEmpty()) return null;
        return new String[]{p[0], p[1]};
    }

    public static String cloneUrl(String owner, String name) {
        return "https://github.com/" + owner + "/" + name + ".git";
    }

    // ------------------------------------------------------------------ plumbing

    private static Repo repoOf(JsonObject o) {
        Repo r = new Repo();
        r.name = str(o, "name", "");
        r.fullName = str(o, "full_name", r.name);
        r.owner = o.has("owner") && o.get("owner").isJsonObject() ? str(o.getAsJsonObject("owner"), "login", "") : "";
        r.description = str(o, "description", "");
        r.cloneUrl = str(o, "clone_url", "");
        r.htmlUrl = str(o, "html_url", "");
        r.defaultBranch = str(o, "default_branch", "main");
        r.isPrivate = o.has("private") && o.get("private").getAsBoolean();
        r.fork = o.has("fork") && o.get("fork").getAsBoolean();
        r.size = o.has("size") ? o.get("size").getAsLong() : 0;
        r.empty = r.size == 0;
        r.updatedAt = str(o, "pushed_at", str(o, "updated_at", ""));
        if (o.has("permissions") && o.get("permissions").isJsonObject()) {
            JsonObject p = o.getAsJsonObject("permissions");
            r.canPush = p.has("push") && p.get("push").getAsBoolean();
            r.canAdmin = p.has("admin") && p.get("admin").getAsBoolean();
        }
        if (o.has("parent") && o.get("parent").isJsonObject()) r.parent = str(o.getAsJsonObject("parent"), "full_name", null);
        return r;
    }

    private static String str(JsonObject o, String k, String def) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return def;
        return o.get(k).getAsString();
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private JsonElement get(String path) throws GitException {
        return send("GET", path, null);
    }

    private JsonElement send(String method, String path, String body) throws GitException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(path.startsWith("http") ? path : API + path).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestMethod(method.equals("PATCH") ? "POST" : method);
            if (method.equals("PATCH")) c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            c.setRequestProperty("User-Agent", "Star2D-Editor");
            if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.getBytes("UTF-8"));
                }
            }
            int code = c.getResponseCode();
            String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (code >= 400) throw error(code, text);
            if (text.isEmpty()) return new JsonObject();
            return JsonParser.parseString(text);
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException(GitException.Kind.NETWORK, e.getMessage() == null ? e.toString() : e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Form post for the OAuth endpoints (they answer JSON when asked to). */
    private static JsonObject post(String url, String form, String unused) throws GitException {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("User-Agent", "Star2D-Editor");
            try (OutputStream os = c.getOutputStream()) {
                os.write(form.getBytes("UTF-8"));
            }
            int code = c.getResponseCode();
            String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
            if (text.isEmpty()) throw new GitException(GitException.Kind.NETWORK, "HTTP " + code);
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException(GitException.Kind.NETWORK, e.getMessage() == null ? e.toString() : e.getMessage(), e);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static GitException error(int code, String text) {
        String msg = text;
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            msg = str(o, "message", text);
            if (o.has("errors") && o.get("errors").isJsonArray()) {
                for (JsonElement e : o.getAsJsonArray("errors")) {
                    if (e.isJsonObject()) msg += " (" + str(e.getAsJsonObject(), "message", str(e.getAsJsonObject(), "code", "")) + ")";
                }
            }
        } catch (Exception ignored) {
        }
        GitException.Kind k = code == 401 || code == 403 ? GitException.Kind.AUTH
                : code == 404 ? GitException.Kind.NOT_FOUND : GitException.Kind.OTHER;
        return new GitException(k, msg);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        in.close();
        return out.toString("UTF-8");
    }

}
