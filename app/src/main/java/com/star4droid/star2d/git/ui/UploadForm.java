package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.git.GitHubSession;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** "Upload to GitHub" option of the new-project dialog: name, description and visibility, or a prompt to connect first. */
public final class UploadForm {
    private final Activity a;
    private final CheckBox check;
    private final LinearLayout box;
    private EditText name, desc;
    private boolean nameEdited;
    private boolean isPrivate = true;
    private String projectName = "";

    private static String T(String k, String d) {
        return Tr.t(k, d);
    }

    public UploadForm(Activity a, LinearLayout parent, String defaultName) {
        this(a, parent, defaultName, false);
    }

    /** {@code embedded}: always on, without the checkbox (used by "create repository" in the Git panel). */
    public UploadForm(Activity a, LinearLayout parent, String defaultName, boolean embedded) {
        this.a = a;
        this.projectName = defaultName;
        check = new CheckBox(a);
        check.setText(T("gh_upload_check", "Upload to GitHub"));
        check.setTextColor(Ui.TEXT);
        check.setTextSize(15);
        check.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), 0, Ui.dp(a, 8));
        LinearLayout.LayoutParams cp = Ui.matchWrap();
        cp.topMargin = Ui.dp(a, 10);
        parent.addView(check, cp);
        box = Ui.column(a);
        box.setVisibility(View.GONE);
        parent.addView(box, Ui.matchWrap());
        check.setOnCheckedChangeListener((b, on) -> {
            if (on) {
                render();
                box.setAlpha(0f);
                box.setVisibility(View.VISIBLE);
                box.animate().alpha(1f).setDuration(220).start();
            } else {
                box.setVisibility(View.GONE);
            }
        });
        if (embedded) {
            check.setVisibility(View.GONE);
            check.setChecked(true);
        }
    }

    /** Keep the repository name in step with the project name until the user edits it. */
    public void projectNameChanged(String n) {
        projectName = n;
        if (name != null && !nameEdited) name.setText(sanitize(n));
    }

    public boolean enabled() {
        return check.isChecked();
    }

    public String repoName() {
        return name == null ? "" : name.getText().toString().trim();
    }

    public String description() {
        return desc == null ? "" : desc.getText().toString().trim();
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public boolean connected() {
        return GitHubSession.connected(a);
    }

    /** Error text for the current input or null. */
    public String validate() {
        if (!enabled()) return null;
        if (!connected()) return T("gh_need_connect", "Connect your GitHub account first.");
        String n = repoName();
        if (n.isEmpty() || !n.matches("[A-Za-z0-9._-]+")) {
            if (name != null) name.setError(T("gh_repo_name_rule", "Letters, numbers, - _ . only"));
            return T("gh_repo_name_rule", "Letters, numbers, - _ . only");
        }
        return null;
    }

    public static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    private void render() {
        box.removeAllViews();
        if (!connected()) {
            LinearLayout warn = Ui.column(a);
            warn.setBackground(Ui.rounded(0x22FFB300, Ui.dp(a, 12), 0x66FFB300, Ui.dp(a, 1)));
            warn.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 12));
            TextView t = Ui.text(a, T("gh_not_connected_warn", "You are not connected to GitHub."), 14, Ui.WARNING);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            warn.addView(t);
            warn.addView(Ui.text(a, T("gh_not_connected_hint", "Connect your account to upload this project."), 13, Ui.TEXT_2));
            View b = Ui.button(a, R.drawable.ic_s2_git, T("gh_connect_github", "Connect to GitHub"), true, v -> GitHubConnect.show(a, this::render));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = Ui.dp(a, 8);
            warn.addView(b, bp);
            box.addView(warn, Ui.matchWrap());
            return;
        }
        box.addView(Ui.sectionLabel(a, T("gh_repo_name", "Repository name")));
        name = Ui.input(a, sanitize(projectName), "MyGame");
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable s) {
                if (name.hasFocus()) nameEdited = true;
            }
        });
        box.addView(name, Ui.matchWrap());
        box.addView(Ui.sectionLabel(a, T("gh_repo_desc", "Description (optional)")));
        desc = Ui.input(a, "", "");
        box.addView(desc, Ui.matchWrap());
        box.addView(Ui.sectionLabel(a, T("gh_visibility", "Who can see it")));
        LinearLayout row = Ui.row(a);
        final LinearLayout[] cards = new LinearLayout[2];
        String[][] opt = {{"🔒", T("gh_private", "Private"), T("gh_private_d", "Only you (and people you invite)")},
                {"🌐", T("gh_public", "Public"), T("gh_public_d", "Anyone on the internet")}};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            LinearLayout c = Ui.column(a);
            c.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
            c.setBackground(Ui.toggleBg(a));
            c.addView(Ui.text(a, opt[i][0] + "  " + opt[i][1], 14.5f, Ui.TEXT));
            TextView d = Ui.text(a, opt[i][2], 11.5f, Ui.TEXT_3);
            d.setPadding(0, Ui.dp(a, 2), 0, 0);
            c.addView(d);
            c.setOnClickListener(v -> {
                isPrivate = idx == 0;
                for (int k = 0; k < 2; k++) cards[k].setSelected((k == 0) == isPrivate);
            });
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.setMargins(Ui.dp(a, 3), 0, Ui.dp(a, 3), 0);
            row.addView(c, lp);
            cards[i] = c;
        }
        cards[isPrivate ? 0 : 1].setSelected(true);
        box.addView(row, Ui.matchWrap());
    }
}
