package com.star4droid.star2d.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.star4droid.star2d.Helpers.EngineSettings;

/**
 * Shared look &amp; helpers for the native Android editor UI (colors, sizes, buttons, inputs).
 * Everything is built in code so panels stay light and fast.
 */
public final class Ui {
    private Ui() {}

    // ---- palette (dark, matches the projects home screen) ----
    public static final int BG = 0xFF17191D;
    public static final int SURFACE = 0xFF202328;
    public static final int SURFACE_2 = 0xFF292D33;
    public static final int SURFACE_3 = 0xFF333840;
    public static final int BORDER = 0xFF3A4048;
    public static final int ACCENT = 0xFF2196F3;
    public static final int ACCENT_DARK = 0xFF1976D2;
    public static final int ACCENT_SOFT = 0x332196F3;
    public static final int TEXT = 0xFFF2F4F7;
    public static final int TEXT_2 = 0xFFB0B6BF;
    public static final int TEXT_3 = 0xFF7D8590;
    public static final int DANGER = 0xFFEF5350;
    public static final int SUCCESS = 0xFF4CAF50;
    public static final int WARNING = 0xFFFFB300;

    public static final String PREF_ICON_SIZE = "icon_size";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ------------------------------------------------------------ prefs

    public static SharedPreferences prefs(Context c) {
        if (EngineSettings.get() == null) EngineSettings.init(c);
        return EngineSettings.get();
    }

    /** "small" | "medium" | "large" */
    public static String iconSize(Context c) {
        return prefs(c).getString(PREF_ICON_SIZE, "medium");
    }

    /** Icon glyph size in dp for the current icon-size setting. */
    public static int iconDp(Context c) {
        switch (iconSize(c)) {
            case "small": return 19;
            case "large": return 28;
            default: return 23;
        }
    }

    /** Touch target size in dp for toolbar buttons. */
    public static int buttonDp(Context c) {
        switch (iconSize(c)) {
            case "small": return 36;
            case "large": return 52;
            default: return 44;
        }
    }

    public static boolean isRtl(Context c) {
        return "ar".equals(prefs(c).getString("lang", "en"));
    }

    // ------------------------------------------------------------ units

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static boolean isLandscape(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels > c.getResources().getDisplayMetrics().heightPixels;
    }

    // ------------------------------------------------------------ drawables

    public static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable rounded(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = rounded(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    /** Background with a ripple on press. */
    public static Drawable ripple(int color, float radiusPx) {
        GradientDrawable mask = rounded(Color.WHITE, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), color == 0 ? null : rounded(color, radiusPx), mask);
    }

    /** Selected / normal state background for toggle buttons. */
    public static Drawable toggleBg(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_selected}, rounded(ACCENT_SOFT, dp(c, 8), ACCENT, dp(c, 1)));
        s.addState(new int[]{}, rounded(0x00000000, dp(c, 8)));
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), s, rounded(Color.WHITE, dp(c, 8)));
    }

    // ------------------------------------------------------------ widgets

    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView sectionLabel(Context c, CharSequence s) {
        TextView t = text(c, s.toString().toUpperCase(), 12, TEXT_3);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.06f);
        t.setPadding(dp(c, 4), dp(c, 14), dp(c, 4), dp(c, 6));
        return t;
    }

    /** Tintable vector icon button sized by the icon-size setting. */
    public static ImageView iconButton(Context c, int drawable, CharSequence tooltip, View.OnClickListener l) {
        ImageView b = new ImageView(c);
        Drawable d = ContextCompat.getDrawable(c, drawable);
        b.setImageDrawable(d);
        b.setImageTintList(ColorStateList.valueOf(TEXT));
        int size = dp(c, buttonDp(c));
        int pad = (size - dp(c, iconDp(c))) / 2;
        b.setPadding(pad, pad, pad, pad);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setBackground(toggleBg(c));
        if (tooltip != null) {
            b.setContentDescription(tooltip);
            if (android.os.Build.VERSION.SDK_INT >= 26) b.setTooltipText(tooltip);
            b.setOnLongClickListener(v -> { toast(c, tooltip.toString()); return true; });
        }
        b.setOnClickListener(l);
        return b;
    }

    public static ImageView smallIcon(Context c, int drawable, int color, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageDrawable(ContextCompat.getDrawable(c, drawable));
        i.setImageTintList(ColorStateList.valueOf(color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    /** Filled (primary) or outlined button with optional leading icon. */
    public static LinearLayout button(Context c, int icon, CharSequence label, boolean primary, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        int h = dp(c, 38);
        b.setMinimumHeight(h);
        b.setPadding(dp(c, 12), 0, dp(c, 14), 0);
        b.setBackground(primary
                ? new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(ACCENT, dp(c, 8)), null)
                : new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(SURFACE_2, dp(c, 8), BORDER, dp(c, 1)), null));
        if (icon != 0) {
            ImageView i = smallIcon(c, icon, TEXT, 18);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) i.getLayoutParams();
            lp.setMarginEnd(dp(c, 6));
            b.addView(i);
        }
        TextView t = text(c, label, 14, TEXT);
        t.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setMaxLines(1);
        b.addView(t);
        b.setClickable(true);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, h));
        return b;
    }

    public static EditText input(Context c, String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setTextColor(TEXT);
        e.setHintTextColor(TEXT_3);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setBackground(rounded(0xFF121417, dp(c, 7), BORDER, dp(c, 1)));
        e.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
        return e;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f))));
        return v;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, Context c, int l, int t, int r, int b) {
        lp.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return lp;
    }

    // ------------------------------------------------------------ threads / toast

    public static void ui(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else MAIN.post(r);
    }

    /** Short message (swipeable snackbar on the current screen; falls back to a toast). */
    public static void toast(Context c, String msg) {
        ui(() -> {
            if (Snack.activity() != null) Snack.show(msg);
            else Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }
}
