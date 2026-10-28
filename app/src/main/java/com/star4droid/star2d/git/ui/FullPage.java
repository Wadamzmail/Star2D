package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** A full-screen page with a back arrow and a title (works in both orientations, keeps clear of bars and keyboard). */
public final class FullPage {
    private final Dialog dialog;
    private final TextView title;
    private final FrameLayout body;
    private final LinearLayout actions;
    private Runnable onClose;

    public FullPage(Activity a, String titleText) {
        dialog = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = Ui.column(a);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(Ui.isRtl(a) ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.SURFACE);
        bar.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4));
        ImageView back = Ui.iconButton(a, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), v -> close());
        bar.addView(back);
        title = Ui.text(a, titleText, 17, Ui.TEXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        bar.addView(title, Ui.weight(1));
        actions = Ui.row(a);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(actions);
        root.addView(bar, Ui.matchWrap());

        body = new FrameLayout(a);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Ui.BG));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setStatusBarColor(Ui.SURFACE);
            w.setNavigationBarColor(Ui.BG);
        }
        dialog.setOnDismissListener(d -> {
            if (onClose != null) onClose.run();
        });
    }

    public FullPage content(View v) {
        body.removeAllViews();
        body.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return this;
    }

    public FullPage title(String t) {
        title.setText(t);
        return this;
    }

    public FullPage addAction(int icon, String tip, View.OnClickListener l) {
        actions.addView(Ui.iconButton(dialog.getContext(), icon, tip, l));
        return this;
    }

    public FullPage onClose(Runnable r) {
        onClose = r;
        return this;
    }

    public FullPage show() {
        dialog.show();
        return this;
    }

    public void close() {
        dialog.dismiss();
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public Dialog dialog() {
        return dialog;
    }

    static int color(int c) {
        return Color.argb(Color.alpha(c), Color.red(c), Color.green(c), Color.blue(c));
    }
}
