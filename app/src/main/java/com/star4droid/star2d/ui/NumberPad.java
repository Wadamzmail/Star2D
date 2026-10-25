package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.tutorial.Tut;

/**
 * Number keypad dialog for number fields: digits, minus, dot, delete (⌫), clear (C), cancel and OK.
 * Side by side in landscape (display | keys), stacked in portrait.
 */
public final class NumberPad {
    private NumberPad() {}

    public interface OnValue { void on(String value); }

    /** True when the text is a plain number (or empty). */
    public static boolean isNumber(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.isEmpty()) return true;
        try {
            Float.parseFloat(s);
            return !s.endsWith("f") && !s.endsWith("d");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @param onKeyboard optional: shows an "abc" key that closes the pad to type text instead.
     */
    public static AlertDialog show(Context c, String title, String initial, OnValue ok, Runnable onKeyboard) {
        boolean land = Ui.isLandscape(c);
        StringBuilder val = new StringBuilder(initial == null ? "" : initial.trim());
        LinearLayout root = land ? Ui.row(c) : Ui.column(c);
        root.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 4));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // keypads keep their layout in RTL
        Tut.tag(root, "numpad");

        TextView display = Ui.text(c, val.length() == 0 ? "0" : val, 30, Ui.TEXT);
        display.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        display.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        display.setMaxLines(1);
        display.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        display.setBackground(Ui.rounded(0xFF121417, Ui.dp(c, 12), Ui.BORDER, 1));
        LinearLayout.LayoutParams dl = land ? new LinearLayout.LayoutParams(0, Ui.dp(c, 64), 1) : Ui.matchWrap();
        if (land) dl.setMarginEnd(Ui.dp(c, 12));
        else dl.bottomMargin = Ui.dp(c, 10);
        dl.gravity = Gravity.CENTER_VERTICAL;
        root.addView(display, dl);

        Runnable refresh = () -> display.setText(val.length() == 0 ? "0" : val);
        GridLayout g = new GridLayout(c);
        g.setColumnCount(4);
        String[] keys = {"7", "8", "9", "⌫", "4", "5", "6", "C", "1", "2", "3", "±", onKeyboard != null ? "abc" : "", "0", ".", "00"};
        int keyH = Ui.dp(c, land ? 44 : 54);
        for (String k : keys) {
            View key;
            if (k.equals("⌫")) {
                ImageView iv = Ui.smallIcon(c, R.drawable.ic_s2_backspace, Ui.TEXT, 24);
                iv.setScaleType(ImageView.ScaleType.CENTER);
                key = iv;
            } else {
                TextView t = Ui.text(c, k, k.length() > 1 ? 16 : 22, k.equals("C") ? 0xFFFF8A80 : Ui.TEXT);
                t.setGravity(Gravity.CENTER);
                t.setTypeface(Typeface.DEFAULT_BOLD);
                key = t;
            }
            boolean digit = k.matches("[0-9]+");
            key.setBackground(Ui.ripple(digit ? Ui.SURFACE_3 : Ui.SURFACE_2, Ui.dp(c, 12)));
            if (k.isEmpty()) key.setVisibility(View.INVISIBLE);
            key.setOnClickListener(v -> {
                switch (k) {
                    case "⌫":
                        if (val.length() > 0) val.deleteCharAt(val.length() - 1);
                        break;
                    case "C":
                        val.setLength(0);
                        break;
                    case "±":
                        if (val.length() > 0 && val.charAt(0) == '-') val.deleteCharAt(0);
                        else val.insert(0, '-');
                        break;
                    case ".":
                        if (val.indexOf(".") < 0) val.append(val.length() == 0 || val.toString().equals("-") ? "0." : ".");
                        break;
                    case "abc":
                        return;
                    default:
                        if (val.toString().equals("0")) val.setLength(0);
                        else if (val.toString().equals("-0")) val.setLength(1);
                        val.append(k);
                }
                refresh.run();
            });
            key.setContentDescription(k.equals("⌫") ? Tr.t("delete", "Delete") : (k.equals("C") ? Tr.t("ui_clear", "Clear") : k));
            Tut.tag(key, "numpad:" + k);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.height = keyH;
            lp.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
            g.addView(key, lp);
        }
        LinearLayout.LayoutParams gl = land ? new LinearLayout.LayoutParams(Ui.dp(c, 280), ViewGroup.LayoutParams.WRAP_CONTENT) : Ui.matchWrap();
        root.addView(g, gl);

        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(root)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        d.setOnShowListener(x -> {
            View okBtn = d.getButton(AlertDialog.BUTTON_POSITIVE);
            Tut.tag(okBtn, "numpad:ok");
            okBtn.setOnClickListener(v -> {
                String s = val.toString();
                if (s.equals("-") || s.equals(".")) s = "0";
                if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
                d.dismiss();
                ok.on(s.isEmpty() ? "0" : s);
            });
            if (onKeyboard != null) {
                View abc = Tut.find("numpad:abc");
                if (abc != null) abc.setOnClickListener(v -> {
                    d.dismiss();
                    onKeyboard.run();
                });
            }
        });
        if (c instanceof Activity && ((Activity) c).isFinishing()) return d;
        d.show();
        return d;
    }
}
