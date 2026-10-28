package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitException;
import com.star4droid.star2d.git.GitHubApi;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** The GitHub connection page: sign in with a device code or a personal access token, see / leave the account. */
public final class GitHubConnect {
    private GitHubConnect() {}

    private static String T(String k, String d) {
        return Tr.t(k, d);
    }

    /** Opens the page; {@code onChanged} runs whenever the connection state changes. */
    public static void show(final Activity a, final Runnable onChanged) {
        final Page p = new Page(a, onChanged);
        p.page = new FullPage(a, T("gh_title", "GitHub")).content(p.scroll).onClose(p::stop).show();
        p.render();
    }

    private static final class Page {
        final Activity a;
        final Runnable onChanged;
        final ScrollView scroll;
        final LinearLayout root;
        FullPage page;
        int mode; // 0 = device code, 1 = token
        volatile boolean polling;
        TextView successMark;

        Page(Activity a, Runnable onChanged) {
            this.a = a;
            this.onChanged = onChanged;
            scroll = new ScrollView(a);
            root = Ui.column(a);
            root.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 28));
            scroll.addView(root);
            scroll.setFillViewport(true);
        }

        void stop() {
            polling = false;
        }

        void changed() {
            if (onChanged != null) onChanged.run();
        }

        View card() {
            LinearLayout c = Ui.column(a);
            c.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(a, 14), Ui.BORDER, Ui.dp(a, 1)));
            c.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 14));
            return c;
        }

        LinearLayout.LayoutParams gap() {
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(a, 12);
            return lp;
        }

        void render() {
            polling = false;
            root.removeAllViews();
            boolean connected = GitHubSession.connected(a);
            if (connected) renderAccount();
            else renderSignIn();
            renderAiCard();
        }

        // ------------------------------------------------------------ signed in

        void renderAccount() {
            GitHubApi.User u = GitHubSession.user(a);
            LinearLayout c = (LinearLayout) card();
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView av = Ui.text(a, u == null || u.login.isEmpty() ? "G" : u.login.substring(0, 1).toUpperCase(), 22, 0xFFFFFFFF);
            av.setGravity(Gravity.CENTER);
            av.setTypeface(Typeface.DEFAULT_BOLD);
            av.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(a, 26)));
            row.addView(av, new LinearLayout.LayoutParams(Ui.dp(a, 52), Ui.dp(a, 52)));
            LinearLayout names = Ui.column(a);
            names.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 8), 0);
            TextView nm = Ui.text(a, u == null ? "GitHub" : (u.name == null || u.name.isEmpty() ? u.login : u.name), 17, Ui.TEXT);
            nm.setTypeface(Typeface.DEFAULT_BOLD);
            names.addView(nm);
            names.addView(Ui.text(a, u == null ? "" : "@" + u.login, 13.5f, Ui.TEXT_2));
            row.addView(names, Ui.weight(1));
            successMark = Ui.text(a, "✓", 16, 0xFFFFFFFF);
            successMark.setGravity(Gravity.CENTER);
            successMark.setBackground(Ui.rounded(Ui.SUCCESS, Ui.dp(a, 15)));
            row.addView(successMark, new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
            c.addView(row);
            TextView st = Ui.text(a, T("gh_connected_msg", "Connected. You can upload new projects, clone repositories and sync from the Git panel inside a project."), 13.5f, Ui.TEXT_2);
            st.setPadding(0, Ui.dp(a, 12), 0, 0);
            c.addView(st);
            LinearLayout actions = Ui.row(a);
            actions.setPadding(0, Ui.dp(a, 12), 0, 0);
            actions.addView(Ui.button(a, 0, T("gh_open_profile", "Open on GitHub"), false, v -> open("https://github.com/" + (u == null ? "" : u.login))));
            View out = Ui.button(a, 0, T("gh_sign_out", "Sign out"), false, v -> {
                GitHubSession.signOut(a);
                changed();
                render();
            });
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ol.setMarginStart(Ui.dp(a, 8));
            actions.addView(out, ol);
            c.addView(actions);
            root.addView(c, Ui.matchWrap());
            // pop-in
            successMark.setScaleX(0f);
            successMark.setScaleY(0f);
            successMark.animate().scaleX(1f).scaleY(1f).setStartDelay(150).setDuration(420).setInterpolator(new OvershootInterpolator(2.5f)).start();
        }

        // ------------------------------------------------------------ signed out

        void renderSignIn() {
            LinearLayout c = (LinearLayout) card();
            TextView h = Ui.text(a, T("gh_not_connected", "Not connected"), 17, Ui.TEXT);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            c.addView(h);
            TextView d = Ui.text(a, T("gh_intro", "Connect your GitHub account to back up projects, share them, clone repositories and let an AI edit your game."), 13.5f, Ui.TEXT_2);
            d.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 10));
            c.addView(d);

            LinearLayout tabs = Ui.row(a);
            tabs.setBackground(Ui.rounded(Ui.BG, Ui.dp(a, 12), Ui.BORDER, Ui.dp(a, 1)));
            tabs.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
            final TextView[] tv = new TextView[2];
            String[] names = {T("gh_tab_code", "Sign in with a code"), T("gh_tab_token", "Access token")};
            for (int i = 0; i < 2; i++) {
                final int idx = i;
                TextView t = Ui.text(a, names[i], 13.5f, Ui.TEXT);
                t.setGravity(Gravity.CENTER);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                t.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10));
                t.setBackground(mode == i ? Ui.rounded(Ui.ACCENT, Ui.dp(a, 9)) : null);
                t.setTextColor(mode == i ? 0xFFFFFFFF : Ui.TEXT_2);
                t.setOnClickListener(v -> {
                    mode = idx;
                    render();
                });
                tabs.addView(t, Ui.weight(1));
                tv[i] = t;
            }
            c.addView(tabs, Ui.matchWrap());
            LinearLayout body = Ui.column(a);
            body.setPadding(0, Ui.dp(a, 12), 0, 0);
            if (mode == 0) deviceBody(body);
            else tokenBody(body);
            c.addView(body);
            root.addView(c, Ui.matchWrap());
        }

        // ---- device flow

        void deviceBody(final LinearLayout body) {
            TextView t = Ui.text(a, T("gh_code_intro", "You will get a short code. Open GitHub, type the code and approve. Nothing to copy or paste."), 13.5f, Ui.TEXT_2);
            body.addView(t);
            final LinearLayout area = Ui.column(a);
            body.addView(area, gap());
            final View start = Ui.button(a, R.drawable.ic_s2_key, T("gh_get_code", "Get a code"), true, null);
            area.addView(start, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            start.setOnClickListener(v -> {
                area.removeAllViews();
                ProgressBar pb = new ProgressBar(a);
                pb.setIndeterminate(true);
                area.addView(pb, new LinearLayout.LayoutParams(Ui.dp(a, 32), Ui.dp(a, 32)));
                GitHubSession.async(GitHubApi::startDeviceFlow, (code, err) -> {
                    if (err != null || code == null) {
                        area.removeAllViews();
                        area.addView(errorText(err == null ? "?" : err.getMessage()));
                        area.addView(start);
                        return;
                    }
                    showCode(area, code);
                });
            });
        }

        View errorText(String msg) {
            TextView e = Ui.text(a, msg, 13, Ui.DANGER);
            e.setPadding(0, 0, 0, Ui.dp(a, 8));
            return e;
        }

        void showCode(final LinearLayout area, final GitHubApi.DeviceCode code) {
            area.removeAllViews();
            TextView label = Ui.text(a, T("gh_your_code", "Your code"), 12.5f, Ui.TEXT_3);
            label.setGravity(Gravity.CENTER);
            area.addView(label, Ui.matchWrap());
            final TextView big = Ui.text(a, code.userCode, 34, Ui.TEXT);
            big.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            big.setLetterSpacing(0.12f);
            big.setGravity(Gravity.CENTER);
            big.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));
            big.setBackground(Ui.rounded(Ui.BG, Ui.dp(a, 12), Ui.ACCENT, Ui.dp(a, 1)));
            big.setScaleX(0.6f);
            big.setScaleY(0.6f);
            big.setAlpha(0f);
            big.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(380).setInterpolator(new OvershootInterpolator(1.8f)).start();
            area.addView(big, Ui.matchWrap());
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, Ui.dp(a, 12), 0, 0);
            View copy = Ui.button(a, R.drawable.ic_s2_copy, T("copy", "Copy"), false, v -> {
                copyText(code.userCode);
                Ui.toast(a, T("gh_copied", "Code copied"));
            });
            View openGh = Ui.button(a, R.drawable.ic_s2_open_in_new, T("gh_open_github", "Open GitHub"), true, v -> {
                copyText(code.userCode);
                open(code.verificationUri);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Ui.dp(a, 8));
            row.addView(copy, lp);
            row.addView(openGh);
            area.addView(row, Ui.matchWrap());
            LinearLayout wait = Ui.row(a);
            wait.setGravity(Gravity.CENTER_VERTICAL);
            wait.setPadding(0, Ui.dp(a, 14), 0, 0);
            ProgressBar pb = new ProgressBar(a);
            pb.setIndeterminate(true);
            wait.addView(pb, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
            final TextView status = Ui.text(a, T("gh_waiting", "Waiting for you to approve on GitHub…"), 13.5f, Ui.TEXT_2);
            status.setPadding(Ui.dp(a, 10), 0, 0, 0);
            wait.addView(status);
            area.addView(wait, Ui.matchWrap());
            poll(code, status);
        }

        void poll(final GitHubApi.DeviceCode code, final TextView status) {
            polling = true;
            final long deadline = System.currentTimeMillis() + code.expiresIn * 1000L;
            GitHubSession.background(() -> {
                int interval = Math.max(5, code.interval);
                while (polling && System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(interval * 1000L);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (!polling) return;
                    GitHubApi.Poll r;
                    try {
                        r = GitHubApi.pollDeviceFlow(code.deviceCode);
                    } catch (GitException e) {
                        continue; // offline for a moment: keep trying until the code expires
                    }
                    if (r.state == GitHubApi.Poll.State.SLOW_DOWN) {
                        interval = Math.max(interval + 5, r.interval);
                    } else if (r.state == GitHubApi.Poll.State.DONE) {
                        polling = false;
                        finish(r.token);
                        return;
                    } else if (r.state == GitHubApi.Poll.State.DENIED || r.state == GitHubApi.Poll.State.EXPIRED || r.state == GitHubApi.Poll.State.ERROR) {
                        polling = false;
                        final String m = r.state == GitHubApi.Poll.State.DENIED ? T("gh_denied", "You cancelled on GitHub.") : T("gh_expired", "The code expired. Get a new one.");
                        a.runOnUiThread(() -> {
                            status.setText(m);
                            status.setTextColor(Ui.DANGER);
                        });
                        return;
                    }
                }
                if (polling) {
                    polling = false;
                    a.runOnUiThread(() -> {
                        status.setText(T("gh_expired", "The code expired. Get a new one."));
                        status.setTextColor(Ui.DANGER);
                    });
                }
            });
        }

        void finish(final String token) {
            GitHubSession.async(() -> new GitHubApi(token).user(), (user, err) -> {
                if (err != null || user == null) {
                    Ui.toast(a, err == null ? "?" : err.getMessage());
                    render();
                    return;
                }
                GitHubSession.save(a, token, user);
                changed();
                render();
            });
        }

        // ---- token

        void tokenBody(LinearLayout body) {
            body.addView(Ui.text(a, T("gh_token_intro", "Create an access token on GitHub and paste it here. Choose the “repo” permission (or, for a fine-grained token, read and write access to Contents and Administration for your repositories)."), 13.5f, Ui.TEXT_2));
            View create = Ui.button(a, R.drawable.ic_s2_open_in_new, T("gh_create_token", "Create a token on GitHub"), false,
                    v -> open("https://github.com/settings/tokens/new?scopes=repo&description=Star2D"));
            body.addView(create, gap());
            final EditText input = Ui.input(a, "", "ghp_…");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            body.addView(input, gap());
            final TextView err = Ui.text(a, "", 13, Ui.DANGER);
            body.addView(err);
            final View connect = Ui.button(a, R.drawable.ic_s2_check, T("gh_connect", "Connect"), true, null);
            connect.setOnClickListener(v -> {
                final String tk = input.getText().toString().trim();
                if (tk.isEmpty()) return;
                connect.setEnabled(false);
                err.setText("");
                GitHubSession.async(() -> new GitHubApi(tk).user(), (user, e) -> {
                    connect.setEnabled(true);
                    if (e != null || user == null) {
                        err.setText(e == null ? "?" : (e.kind == GitException.Kind.AUTH ? T("gh_bad_token", "GitHub did not accept this token.") : e.getMessage()));
                        return;
                    }
                    GitHubSession.save(a, tk, user);
                    changed();
                    render();
                });
            });
            body.addView(connect, gap());
        }

        // ------------------------------------------------------------ AI card

        void renderAiCard() {
            LinearLayout c = (LinearLayout) card();
            LinearLayout row = Ui.row(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.smallIcon(a, R.drawable.ic_s2_ai, Ui.ACCENT, 26), new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
            LinearLayout names = Ui.column(a);
            names.setPadding(Ui.dp(a, 12), 0, 0, 0);
            TextView t = Ui.text(a, T("gh_ai_title", "Edit with Claude or ChatGPT"), 15.5f, Ui.TEXT);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            names.addView(t);
            names.addView(Ui.text(a, T("gh_ai_sub", "An animated step-by-step guide."), 13, Ui.TEXT_2));
            row.addView(names, Ui.weight(1));
            c.addView(row);
            c.setOnClickListener(v -> AiGuide.show(a));
            c.setBackground(Ui.ripple(Ui.SURFACE, Ui.dp(a, 14)));
            root.addView(c, gap());
        }

        void open(String url) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        }

        void copyText(String s) {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", s));
        }
    }
}
