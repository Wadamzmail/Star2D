package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.remote.RemoteClient;
import com.star4droid.star2d.remote.RemoteCommands;
import com.star4droid.star2d.remote.RemoteSettings;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;

/** "Claude" panel: connect an AI to this editor (switch, server, private link, copyable work orders). */
public class RemotePanel implements PanelHost.Panel, RemoteClient.StateListener {
    private final EditorShell shell;
    private ScrollView root;
    private View dot;
    private TextView stateText, detailText, linkText, serverNote;
    private EditText serverInput;
    private SwitchCompat enable;
    private boolean updating;

    public RemotePanel(EditorShell shell) {
        this.shell = shell;
        RemoteClient.get().addListener(this);
    }

    @Override public String title() { return Tr.t("rc_title", "Claude (remote)"); }
    @Override public int icon() { return R.drawable.ic_s2_remote; }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        root = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 24));
        root.addView(col);

        // ---- status + switch
        LinearLayout card = card(c, col);
        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        dot = new View(c);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(c, 10), Ui.dp(c, 10));
        dlp.setMarginEnd(Ui.dp(c, 8));
        head.addView(dot, dlp);
        stateText = Ui.text(c, "", 15, Ui.TEXT);
        stateText.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(stateText, Ui.weight(1));
        card.addView(head, Ui.matchWrap());
        detailText = Ui.text(c, "", 12, Ui.TEXT_3);
        detailText.setPadding(0, Ui.dp(c, 4), 0, 0);
        card.addView(detailText, Ui.matchWrap());

        enable = new SwitchCompat(c);
        enable.setText(Tr.t("rc_enable", "Let an AI control this editor"));
        enable.setTextColor(Ui.TEXT);
        enable.setMinHeight(Ui.dp(c, 44));
        enable.setChecked(RemoteSettings.enabled(c));
        enable.setOnCheckedChangeListener((b, on) -> {
            if (updating) return;
            if (on && !RemoteSettings.hasServer(c)) {
                Ui.toast(c, Tr.t("rc_no_server", "Type the server link first"));
                updating = true;
                enable.setChecked(false);
                updating = false;
                return;
            }
            RemoteSettings.setEnabled(c, on);
            RemoteClient.get().restart();
            refresh();
        });
        card.addView(enable, Ui.matchWrap());
        TextView keep = Ui.text(c, Tr.t("rc_keep_open", "Keep the editor open on screen while the AI works (split-screen next to the chat is fine)."), 12, Ui.TEXT_3);
        card.addView(keep, Ui.matchWrap());

        // ---- server
        col.addView(Ui.sectionLabel(c, Tr.t("rc_server", "Server")));
        LinearLayout sc = card(c, col);
        serverInput = Ui.input(c, RemoteSettings.customServer(c), Tr.t("rc_server_hint", "https://your-worker.workers.dev"));
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setSingleLine(true);
        sc.addView(serverInput, Ui.matchWrap());
        serverNote = Ui.text(c, "", 12, Ui.TEXT_3);
        serverNote.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 6));
        sc.addView(serverNote, Ui.matchWrap());
        sc.addView(Ui.button(c, R.drawable.ic_s2_check, Tr.t("save", "Save"), false, v -> {
            RemoteSettings.setCustomServer(c, serverInput.getText().toString());
            RemoteClient.get().restart();
            Ui.toast(c, Tr.t("saved", "Saved"));
            refresh();
        }));

        // ---- copy commands
        col.addView(Ui.sectionLabel(c, Tr.t("rc_commands", "Commands to copy")));
        LinearLayout cc = card(c, col);
        TextView how = Ui.text(c, Tr.t("rc_commands_hint", "Paste the work order into Claude (or any AI chat) and write what you want after it."), 12.5f, Ui.TEXT_2);
        how.setPadding(0, 0, 0, Ui.dp(c, 8));
        cc.addView(how, Ui.matchWrap());
        LinearLayout b1 = Ui.button(c, R.drawable.ic_s2_copy, Tr.t("rc_copy_work", "Copy work command for Claude"), true, v -> copyWork(c));
        b1.setLayoutParams(Ui.matchWrap());
        cc.addView(b1);
        LinearLayout b2 = Ui.button(c, R.drawable.ic_s2_copy, Tr.t("rc_copy_ai", "Copy command for another AI"), false, v -> copyOther(c));
        b2.setLayoutParams(Ui.margins(Ui.matchWrap(), c, 0, 8, 0, 0));
        cc.addView(b2);

        // ---- private link
        col.addView(Ui.sectionLabel(c, Tr.t("rc_connector", "Connector link (private)")));
        LinearLayout lc = card(c, col);
        linkText = Ui.text(c, "", 12, Ui.TEXT_2);
        linkText.setTypeface(Typeface.MONOSPACE);
        lc.addView(linkText, Ui.matchWrap());
        TextView warn = Ui.text(c, Tr.t("rc_link_warn", "Anyone with this link can control your editor. Do not share it."), 12, Ui.WARNING);
        warn.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        lc.addView(warn, Ui.matchWrap());
        LinearLayout lrow = Ui.row(c);
        LinearLayout copy = Ui.button(c, R.drawable.ic_s2_copy, Tr.t("rc_copy_link", "Copy link"), false, v -> {
            if (!RemoteSettings.hasServer(c)) {
                Ui.toast(c, Tr.t("rc_no_server", "Type the server link first"));
                return;
            }
            Dialogs.copy(c, RemoteSettings.connectorUrl(c));
        });
        lrow.addView(copy);
        LinearLayout regen = Ui.button(c, R.drawable.ic_s2_refresh, Tr.t("rc_regen", "New link"), false, v ->
                Dialogs.confirm(c, Tr.t("rc_regen", "New link"), Tr.t("rc_regen_q", "The old link stops working. Continue?"), null, () -> {
                    RemoteSettings.regenerateSecret(c);
                    RemoteClient.get().restart();
                    refresh();
                }));
        ((LinearLayout.LayoutParams) regen.getLayoutParams()).setMarginStart(Ui.dp(c, 8));
        lrow.addView(regen);
        lc.addView(lrow);

        // ---- how it works
        col.addView(Ui.sectionLabel(c, Tr.t("rc_how", "How it works")));
        LinearLayout hc = card(c, col);
        String[] steps = {
                Tr.t("rc_how1", "1. In Claude: Settings → Connectors → Add custom connector → paste the connector link."),
                Tr.t("rc_how2", "2. Turn the switch on here and keep this editor open."),
                Tr.t("rc_how3", "3. Paste the work command in a Claude chat, add your request, and watch the plan appear in the editor."),
                Tr.t("rc_how4", "While the AI works you can move the camera and browse files; press “Take control” any time."),
        };
        for (String s : steps) {
            TextView t = Ui.text(c, s, 12.5f, Ui.TEXT_2);
            t.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 4));
            hc.addView(t, Ui.matchWrap());
        }
        refresh();
        return root;
    }

    private LinearLayout card(Context c, LinearLayout parent) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 12), Ui.BORDER, Ui.dp(c, 1)));
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 12));
        parent.addView(card, Ui.margins(Ui.matchWrap(), c, 0, 0, 0, 4));
        return card;
    }

    private void copyWork(Context c) {
        if (!RemoteSettings.hasServer(c)) {
            Ui.toast(c, Tr.t("rc_no_server", "Type the server link first"));
            return;
        }
        if (!Bridge.ready()) {
            Dialogs.copy(c, RemoteCommands.workCommand(c, ""));
            return;
        }
        Bridge.query(() -> Bridge.editor().getProject().getPath(), path -> {
            String repo = RemoteCommands.repoUrl(new File(path));
            Dialogs.copy(c, RemoteCommands.workCommand(c, repo));
        });
    }

    private void copyOther(Context c) {
        if (!RemoteSettings.hasServer(c)) {
            Ui.toast(c, Tr.t("rc_no_server", "Type the server link first"));
            return;
        }
        Dialogs.copy(c, RemoteCommands.otherAiCommand(c));
    }

    @Override
    public void onRemoteState(RemoteClient.State s, String detail) {
        refresh();
    }

    @Override public void onShown() { refresh(); }

    public void refresh() {
        if (root == null) return;
        Context c = root.getContext();
        RemoteClient.State st = RemoteClient.get().state();
        int color;
        String text;
        switch (st) {
            case CONNECTED: color = Ui.SUCCESS; text = Tr.t("rc_st_connected", "Connected"); break;
            case CONNECTING: color = Ui.WARNING; text = Tr.t("rc_st_connecting", "Connecting…"); break;
            case ERROR: color = Ui.DANGER;
                text = RemoteClient.SERVER_OUTDATED.equals(RemoteClient.get().detail())
                        ? Tr.t("rc_server_outdated", "The server needs an update") : Tr.t("rc_st_error", "Cannot connect");
                break;
            default: color = Ui.TEXT_3; text = RemoteSettings.enabled(c) ? Tr.t("rc_st_waiting", "Waiting for the editor to be on screen") : Tr.t("rc_st_off", "Off");
        }
        dot.setBackground(Ui.rounded(color, Ui.dp(c, 5)));
        stateText.setText(text);
        String d = RemoteClient.get().detail();
        if (RemoteClient.SERVER_OUTDATED.equals(d))
            d = Tr.t("rc_server_outdated_hint", "This server does not support the API version this app uses. Update the server (deploy the latest server/ folder) or use the default server.");
        detailText.setText(d);
        detailText.setVisibility(d.isEmpty() ? View.GONE : View.VISIBLE);
        updating = true;
        enable.setChecked(RemoteSettings.enabled(c));
        updating = false;
        serverNote.setText(RemoteSettings.usingDefaultServer(c)
                ? (RemoteSettings.hasServer(c) ? Tr.t("rc_server_default", "Using the default server") + ": " + RemoteSettings.server(c) : Tr.t("rc_server_none", "No server yet: deploy the server/ folder of the repo to Cloudflare (free) and paste its link here."))
                : Tr.t("rc_server_custom", "Using your server"));
        if (RemoteSettings.hasServer(c)) {
            String link = RemoteSettings.connectorUrl(c);
            String secret = RemoteSettings.secret(c);
            linkText.setText(link.replace(secret, secret.substring(0, 4) + "…" + secret.substring(secret.length() - 3)));
        } else linkText.setText("—");
    }
}
