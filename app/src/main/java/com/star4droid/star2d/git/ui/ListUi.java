package com.star4droid.star2d.git.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.ui.Ui;

/** Small building blocks shared by the git screens. */
final class ListUi {
    private ListUi() {}

    static LinearLayout.LayoutParams gap(Activity a) {
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 8);
        return lp;
    }

    /** A tappable two-line row card; {@code badge} may be null. */
    static LinearLayout row(Activity a, String title, String subtitle, String badge, int badgeColor, View.OnClickListener l) {
        LinearLayout c = Ui.row(a);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(Ui.dp(a, 14), Ui.dp(a, 11), Ui.dp(a, 14), Ui.dp(a, 11));
        c.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(a, 12)));
        LinearLayout t = Ui.column(a);
        TextView tt = Ui.text(a, title, 14.5f, Ui.TEXT);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        tt.setMaxLines(1);
        tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.addView(tt);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView st = Ui.text(a, subtitle, 12, Ui.TEXT_3);
            st.setMaxLines(2);
            st.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.addView(st);
        }
        c.addView(t, Ui.weight(1));
        if (badge != null) {
            TextView b = Ui.text(a, badge, 11.5f, badgeColor);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setPadding(Ui.dp(a, 8), 0, 0, 0);
            c.addView(b);
        }
        if (l != null) c.setOnClickListener(l);
        return c;
    }

    static TextView empty(Activity a, String text) {
        TextView t = Ui.text(a, text, 14, Ui.TEXT_3);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(a, 30), 0, Ui.dp(a, 30));
        return t;
    }
}
