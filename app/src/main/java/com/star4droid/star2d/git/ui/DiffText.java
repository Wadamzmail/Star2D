package com.star4droid.star2d.git.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.ui.Ui;

/** Unified diff text with green / red lines, scrollable both ways. */
public final class DiffText {
    private DiffText() {}

    public static CharSequence color(String diff) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String line : diff.split("\n", -1)) {
            int s = sb.length();
            sb.append(line).append('\n');
            int e = sb.length();
            int fg = Ui.TEXT_2, bg = 0;
            if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff ") || line.startsWith("index ")) {
                fg = Ui.TEXT_3;
            } else if (line.startsWith("@@")) {
                fg = 0xFF64B5F6;
                bg = 0x1A2196F3;
            } else if (line.startsWith("+")) {
                fg = 0xFF81C784;
                bg = 0x224CAF50;
            } else if (line.startsWith("-")) {
                fg = 0xFFE57373;
                bg = 0x22EF5350;
            }
            sb.setSpan(new ForegroundColorSpan(fg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (bg != 0) sb.setSpan(new BackgroundColorSpan(bg), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    public static ScrollView view(Context c, String diff) {
        TextView t = new TextView(c);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(11.5f);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 24));
        t.setText(color(diff));
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.addView(t, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView v = new ScrollView(c);
        v.addView(h, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        v.setFillViewport(true);
        return v;
    }
}
