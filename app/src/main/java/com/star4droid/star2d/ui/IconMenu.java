package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/** Popup menu with an icon per item (Android PopupMenu hides icons). */
public final class IconMenu {
    private final Context c;
    private final ArrayList<Object[]> items = new ArrayList<>();

    public IconMenu(Context c) {
        this.c = c;
    }

    public IconMenu add(int icon, int color, String label, Runnable action) {
        items.add(new Object[]{icon, color, label, action});
        return this;
    }

    public IconMenu add(int icon, String label, Runnable action) {
        return add(icon, Ui.TEXT_2, label, action);
    }

    /** A thin separator line. */
    public IconMenu divider() {
        items.add(null);
        return this;
    }

    public void show(View anchor) {
        LinearLayout col = Ui.column(c);
        col.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        PopupWindow[] pw = new PopupWindow[1];
        for (Object[] it : items) {
            if (it == null) {
                View d = Ui.divider(c);
                col.addView(d, Ui.margins(new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(c, 0.7f))), c, 10, 4, 10, 4));
                continue;
            }
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10));
            row.setBackground(Ui.ripple(0, 0));
            ImageView iv = Ui.smallIcon(c, (Integer) it[0], (Integer) it[1], 20);
            row.addView(iv);
            TextView t = Ui.text(c, (String) it[2], 14.5f, Ui.TEXT);
            t.setPadding(Ui.dp(c, 14), 0, 0, 0);
            row.addView(t);
            Runnable r = (Runnable) it[3];
            row.setOnClickListener(v -> {
                pw[0].dismiss();
                if (r != null) r.run();
            });
            col.addView(row, Ui.matchWrap());
        }
        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        sv.setBackground(Ui.rounded(0xFF262A30, Ui.dp(c, 12), Ui.BORDER, 1));
        sv.setElevation(Ui.dp(c, 12));
        pw[0] = new PopupWindow(sv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw[0].setBackgroundDrawable(new ColorDrawable(0));
        pw[0].setOutsideTouchable(true);
        pw[0].setElevation(Ui.dp(c, 12));
        int maxH = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.7f);
        sv.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
        if (sv.getMeasuredHeight() >= maxH) pw[0].setHeight(maxH);
        pw[0].showAsDropDown(anchor, 0, Ui.dp(c, 4), Gravity.START);
    }
}
