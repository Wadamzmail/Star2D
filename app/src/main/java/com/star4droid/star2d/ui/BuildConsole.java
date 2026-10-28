package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.CodeEditor.smart.BuildError;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Bottom console with the errors of the last build: one card per error with an "Open file" button
 * (blocks open the block editor at the block, Java opens the code editor at the line). Scrolls, has Clear / Close.
 */
public class BuildConsole extends LinearLayout {

    public interface Opener {
        void open(BuildError e);
    }

    private final ArrayList<BuildError> errors = new ArrayList<>();
    private final LinearLayout list;
    private final TextView title;
    private final ScrollView scroll;
    private Opener opener;
    private Runnable onVisibilityChange;
    private String extraText;

    public BuildConsole(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 14), Ui.BORDER, Ui.dp(c, 1)));
        setElevation(Ui.dp(c, 12));
        setVisibility(GONE);
        setClickable(true); // swallow touches so they don't reach the canvas below

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        title = Ui.text(c, "", 15, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(title, Ui.weight(1));
        head.addView(Ui.button(c, 0, Tr.t("console_clear", "Clear"), false, v -> clear()));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(Ui.dp(c, 6), 1);
        head.addView(new View(c), gap);
        head.addView(Ui.button(c, 0, "✕", false, v -> hide()));
        addView(head, Ui.matchWrap());

        View line = new View(c);
        line.setBackgroundColor(Ui.BORDER);
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        scroll = new ScrollView(c);
        scroll.setFillViewport(false);
        list = Ui.column(c);
        list.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10), Ui.dp(c, 10));
        scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void setOpener(Opener o) {
        opener = o;
    }

    public void setOnVisibilityChange(Runnable r) {
        onVisibilityChange = r;
    }

    public int count() {
        return errors.size() + (extraText != null ? 1 : 0);
    }

    /** Replace the list with these errors and show the console. */
    public void show(List<BuildError> es, String plainText) {
        errors.clear();
        if (es != null) errors.addAll(es);
        extraText = errors.isEmpty() && plainText != null && !plainText.trim().isEmpty() ? plainText : null;
        rebuild();
        if (count() > 0) reveal();
    }

    public void reveal() {
        setVisibility(VISIBLE);
        scroll.post(() -> scroll.scrollTo(0, 0));
        if (onVisibilityChange != null) onVisibilityChange.run();
    }

    public void hide() {
        setVisibility(GONE);
        if (onVisibilityChange != null) onVisibilityChange.run();
    }

    public void clear() {
        errors.clear();
        extraText = null;
        rebuild();
        hide();
    }

    /** Rows are laid out differently in portrait and landscape; call again after rotation. */
    public void rebuild() {
        Context c = getContext();
        boolean land = Ui.isLandscape(c);
        title.setText(Tr.t("console_title", "Errors") + "  (" + count() + ")");
        list.removeAllViews();
        for (BuildError e : errors) list.addView(row(c, e, land), rowParams(c));
        if (extraText != null) {
            TextView t = Ui.text(c, extraText, 12.5f, Ui.TEXT);
            t.setTypeface(Typeface.MONOSPACE);
            t.setTextIsSelectable(true);
            t.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
            t.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), 0x55EF5350, Ui.dp(c, 1)));
            list.addView(t, rowParams(c));
        }
    }

    private LayoutParams rowParams(Context c) {
        LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(c, 8);
        return lp;
    }

    private View row(Context c, final BuildError e, boolean land) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(land ? HORIZONTAL : VERTICAL);
        card.setGravity(land ? Gravity.CENTER_VERTICAL : Gravity.START);
        card.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), 0x55EF5350, Ui.dp(c, 1)));

        LinearLayout text = Ui.column(c);
        String where;
        if (e.target == BuildError.TARGET_BLOCK) {
            where = Tr.t("console_block", "Block") + ": " + (e.blockTitle == null || e.blockTitle.isEmpty() ? "#" + e.blockIndex : e.blockTitle);
        } else {
            String f = e.target == BuildError.TARGET_JAVA && e.targetFile != null ? e.targetFile : e.file;
            int line = e.target == BuildError.TARGET_JAVA ? e.targetLine : e.line;
            where = (f == null ? "" : new File(f).getName()) + ":" + line;
        }
        TextView w = Ui.text(c, where, 13, Ui.DANGER);
        w.setTypeface(Typeface.DEFAULT_BOLD);
        w.setSingleLine(true);
        w.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(w, Ui.matchWrap());
        if (e.origin != null && !e.origin.isEmpty()) {
            TextView o = Ui.text(c, e.origin, 11.5f, Ui.TEXT_3);
            o.setSingleLine(true);
            o.setEllipsize(TextUtils.TruncateAt.END);
            text.addView(o, Ui.matchWrap());
        }
        TextView m = Ui.text(c, e.message, 13, Ui.TEXT);
        m.setTextIsSelectable(true);
        LayoutParams mp = Ui.matchWrap();
        mp.topMargin = Ui.dp(c, 4);
        text.addView(m, mp);

        if (land) {
            card.addView(text, Ui.weight(1));
        } else {
            card.addView(text, Ui.matchWrap());
        }
        if (e.target != BuildError.TARGET_NONE) {
            View b = Ui.button(c, 0, Tr.t("console_open", "Open file"), true, v -> {
                if (opener != null) opener.open(e);
            });
            LayoutParams bp = land ? new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    : new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (land) bp.setMarginStart(Ui.dp(c, 12));
            else bp.topMargin = Ui.dp(c, 8);
            card.addView(b, bp);
        }
        return card;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int max = MeasureSpec.getSize(h);
        float frac = Ui.isLandscape(getContext()) ? 0.62f : 0.5f;
        int cap = max > 0 ? (int) (max * frac) : Integer.MAX_VALUE;
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(Math.min(cap, Integer.MAX_VALUE >> 3), MeasureSpec.AT_MOST));
    }
}
