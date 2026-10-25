package com.star4droid.star2d.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;

/**
 * Hosts one editor panel at a time on top of the LibGDX canvas.
 * Portrait: a bottom sheet with a drag handle (resizable).
 * Landscape: a side sheet (right, or left for Arabic) that can also be resized.
 * The canvas stays visible and usable around the sheet.
 */
public class PanelHost extends FrameLayout {

    /** A screen shown inside the host. */
    public interface Panel {
        String title();
        int icon();
        /** Build (or return cached) content. */
        View view(Context c);
        /** Optional extra header buttons. */
        default View[] headerActions(Context c) { return new View[0]; }
        default void onShown() {}
        default void onHidden() {}
    }

    private final LinearLayout sheet;
    private final LinearLayout header;
    private final FrameLayout body;
    private final TextView titleView;
    private final ImageView titleIcon;
    private final LinearLayout actions;
    private Panel current;
    private float sizeFraction = -1f; // remembered sheet size (fraction of screen)
    private boolean expanded = false;
    private Runnable onVisibilityChanged;

    public PanelHost(Context c) {
        super(c);
        setVisibility(GONE);

        sheet = Ui.column(c);
        sheet.setBackground(Ui.rounded(Ui.SURFACE, Ui.dp(c, 14), Ui.BORDER, Ui.dp(c, 1)));
        sheet.setElevation(Ui.dp(c, 12));
        sheet.setClickable(true);

        // drag handle
        View handle = new View(c);
        handle.setBackground(Ui.rounded(Ui.TEXT_3, Ui.dp(c, 3)));
        LinearLayout handleWrap = Ui.row(c);
        handleWrap.setGravity(Gravity.CENTER);
        handleWrap.setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 3));
        handleWrap.addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4)));
        sheet.addView(handleWrap, Ui.matchWrap());
        attachResize(handleWrap);

        header = Ui.row(c);
        header.setPadding(Ui.dp(c, 12), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 6));
        titleIcon = Ui.smallIcon(c, R.drawable.ic_s2_info, Ui.ACCENT, 20);
        header.addView(titleIcon);
        titleView = Ui.title(c, "");
        titleView.setMaxLines(1);
        titleView.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        header.addView(titleView, Ui.weight(1));
        actions = Ui.row(c);
        header.addView(actions);
        ImageView expand = Ui.iconButton(c, R.drawable.ic_s2_fit, Tr.t("ui_expand", "Expand"), v -> toggleExpanded());
        header.addView(expand);
        ImageView close = Ui.iconButton(c, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> hide());
        com.star4droid.star2d.ui.tutorial.Tut.tag(close, "panel:close");
        header.addView(close);
        sheet.addView(header, Ui.matchWrap());
        attachResize(header);
        sheet.addView(Ui.divider(c));

        body = new FrameLayout(c);
        sheet.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        addView(sheet);
        layoutSheet();
    }

    public void setOnVisibilityChanged(Runnable r) {
        this.onVisibilityChanged = r;
    }

    public boolean isShowing(Panel p) {
        return getVisibility() == VISIBLE && current == p;
    }

    public Panel current() {
        return getVisibility() == VISIBLE ? current : null;
    }

    /** Show the panel, or hide it if it is already showing (toggle). */
    public void toggle(Panel p) {
        if (isShowing(p)) hide();
        else show(p);
    }

    public void show(Panel p) {
        Context c = getContext();
        if (current != null && current != p) current.onHidden();
        current = p;
        titleView.setText(p.title());
        titleIcon.setImageResource(p.icon() == 0 ? R.drawable.ic_s2_info : p.icon());
        actions.removeAllViews();
        for (View a : p.headerActions(c)) actions.addView(a);
        body.removeAllViews();
        View v = p.view(c);
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        body.addView(v, Ui.match());
        layoutSheet();
        setVisibility(VISIBLE);
        sheet.setAlpha(0f);
        sheet.setTranslationY(Ui.isLandscape(c) ? 0 : Ui.dp(c, 40));
        sheet.setTranslationX(Ui.isLandscape(c) ? (Ui.isRtl(c) ? -Ui.dp(c, 40) : Ui.dp(c, 40)) : 0);
        sheet.animate().alpha(1f).translationX(0).translationY(0).setDuration(170).start();
        p.onShown();
        if (onVisibilityChanged != null) onVisibilityChanged.run();
    }

    public void hide() {
        if (getVisibility() != VISIBLE) return;
        Panel p = current;
        setVisibility(GONE);
        body.removeAllViews();
        if (p != null) p.onHidden();
        if (onVisibilityChanged != null) onVisibilityChanged.run();
    }

    /** Re-run layout (e.g. after rotation or icon-size change). */
    public void refreshLayout() {
        layoutSheet();
    }

    private void toggleExpanded() {
        expanded = !expanded;
        layoutSheet();
    }

    private void layoutSheet() {
        Context c = getContext();
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        boolean land = Ui.isLandscape(c);
        FrameLayout.LayoutParams lp;
        int m = Ui.dp(c, 6);
        if (land) {
            float f = expanded ? 0.96f : (sizeFraction > 0 ? sizeFraction : 0.42f);
            lp = new FrameLayout.LayoutParams((int) (w * f), ViewGroup.LayoutParams.MATCH_PARENT);
            lp.gravity = Ui.isRtl(c) ? Gravity.START : Gravity.END;
            lp.setMargins(m, m, m, m);
        } else {
            float f = expanded ? 0.94f : (sizeFraction > 0 ? sizeFraction : 0.55f);
            lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (h * f));
            lp.gravity = Gravity.BOTTOM;
            lp.setMargins(m, 0, m, m);
        }
        sheet.setLayoutParams(lp);
    }

    /** Drag the handle/header to resize the sheet. */
    @SuppressLint("ClickableViewAccessibility")
    private void attachResize(View grip) {
        grip.setOnTouchListener(new OnTouchListener() {
            float start; int startSize;
            @Override public boolean onTouch(View v, MotionEvent e) {
                Context c = getContext();
                boolean land = Ui.isLandscape(c);
                int screen = land ? getResources().getDisplayMetrics().widthPixels : getResources().getDisplayMetrics().heightPixels;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        start = land ? e.getRawX() : e.getRawY();
                        startSize = land ? sheet.getWidth() : sheet.getHeight();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float d = (land ? e.getRawX() : e.getRawY()) - start;
                        if (land && !Ui.isRtl(c)) d = -d;
                        if (!land) d = -d;
                        int size = (int) Math.max(screen * 0.22f, Math.min(screen * 0.96f, startSize + d));
                        expanded = false;
                        sizeFraction = size / (float) screen;
                        layoutSheet();
                        return true;
                    case MotionEvent.ACTION_UP:
                        // swipe far down closes in portrait
                        if (!land && sizeFraction < 0.24f) { sizeFraction = 0.5f; hide(); }
                        return true;
                }
                return false;
            }
        });
    }

    // ---------------------------------------------------------------- small helpers for panels

    /** A titled row used in list panels: icon, title, subtitle, trailing views. */
    public static LinearLayout listRow(Context c, int icon, int iconColor, String title, String subtitle, View... trailing) {
        LinearLayout row = Ui.row(c);
        row.setPadding(Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
        row.setMinimumHeight(Ui.dp(c, 52));
        row.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
        if (icon != 0) {
            ImageView i = Ui.smallIcon(c, icon, iconColor, 22);
            row.addView(i);
        }
        LinearLayout texts = Ui.column(c);
        texts.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        TextView t = Ui.text(c, title, 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        texts.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Ui.text(c, subtitle, 12, Ui.TEXT_3);
            s.setMaxLines(2);
            texts.addView(s);
        }
        row.addView(texts, Ui.weight(1));
        for (View v : trailing) row.addView(v);
        return row;
    }
}
