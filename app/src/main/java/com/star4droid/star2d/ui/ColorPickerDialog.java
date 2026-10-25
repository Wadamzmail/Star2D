package com.star4droid.star2d.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

/**
 * HSV color picker. Values use the engine format "RRGGBBAA" (as LibGDX Color.toString()).
 */
public final class ColorPickerDialog {
    private ColorPickerDialog() {}

    public interface Preview { void on(String rrggbbaa); }
    /** Called with the final color, or null when cancelled. */
    public interface Done { void on(String rrggbbaa); }

    public static String toHex(int argb) {
        return String.format("%02X%02X%02X%02X", Color.red(argb), Color.green(argb), Color.blue(argb), Color.alpha(argb));
    }

    public static int parse(String value) {
        try {
            String v = value.trim().replace("#", "");
            if (v.length() == 6) v = v + "FF";
            long l = Long.parseLong(v, 16);
            int r = (int) ((l >> 24) & 0xFF), g = (int) ((l >> 16) & 0xFF), b = (int) ((l >> 8) & 0xFF), a = (int) (l & 0xFF);
            return Color.argb(a, r, g, b);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    private static final int[] SWATCHES = {
            0xFFFFFFFF, 0xFF000000, 0xFF263238, 0xFF607D8B, 0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7,
            0xFF3F51B5, 0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFFCDDC39,
            0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800, 0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF87CEEB, 0xFF1B5E20};

    public static void show(Context c, String title, String initial, Preview preview, Done done) {
        final float[] hsv = new float[3];
        final int[] alpha = {255};
        int start = parse(initial);
        Color.colorToHSV(start, hsv);
        alpha[0] = Color.alpha(start);

        LinearLayout root = Ui.column(c);
        int p = Ui.dp(c, 16);
        root.setPadding(p, Ui.dp(c, 8), p, 0);

        View swatch = new View(c);
        EditText hex = Ui.input(c, toHex(start), "RRGGBBAA");
        SvView sv = new SvView(c);
        HueView hue = new HueView(c);
        AlphaView av = new AlphaView(c);
        final boolean[] fromText = {false};

        Runnable changed = () -> {
            int col = Color.HSVToColor(alpha[0], hsv);
            swatch.setBackground(Ui.rounded(col, Ui.dp(c, 8), Ui.BORDER, 1));
            if (!fromText[0]) hex.setText(toHex(col));
            sv.setHue(hsv[0]);
            av.setColor(Color.HSVToColor(hsv));
            if (preview != null) preview.on(toHex(col));
        };
        sv.listener = (s, v) -> { hsv[1] = s; hsv[2] = v; changed.run(); };
        hue.listener = h -> { hsv[0] = h; changed.run(); };
        av.listener = a -> { alpha[0] = a; changed.run(); };
        sv.set(hsv[1], hsv[2]);
        hue.set(hsv[0]);
        av.set(alpha[0]);

        LinearLayout top = Ui.row(c);
        top.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 36)));
        LinearLayout.LayoutParams hl = Ui.weight(1);
        hl.setMarginStart(Ui.dp(c, 10));
        top.addView(hex, hl);
        root.addView(top, Ui.matchWrap());
        root.addView(sv, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 170)), c, 0, 10, 0, 0));
        root.addView(hue, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 26)), c, 0, 10, 0, 0));
        root.addView(av, Ui.margins(new LinearLayout.LayoutParams(-1, Ui.dp(c, 26)), c, 0, 8, 0, 0));

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(8);
        for (int col : SWATCHES) {
            View s = new View(c);
            s.setBackground(Ui.rounded(col, Ui.dp(c, 6), Ui.BORDER, 1));
            GridLayout.LayoutParams gl = new GridLayout.LayoutParams();
            gl.width = Ui.dp(c, 28);
            gl.height = Ui.dp(c, 28);
            gl.setMargins(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
            s.setOnClickListener(v -> {
                Color.colorToHSV(col, hsv);
                sv.set(hsv[1], hsv[2]);
                hue.set(hsv[0]);
                changed.run();
            });
            grid.addView(s, gl);
        }
        root.addView(grid, Ui.margins(Ui.matchWrap(), c, 0, 10, 0, 0));

        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) {
                String v = s.toString().replace("#", "");
                if (v.length() != 6 && v.length() != 8) return;
                if (v.equalsIgnoreCase(toHex(Color.HSVToColor(alpha[0], hsv)))) return;
                int col = parse(v);
                Color.colorToHSV(col, hsv);
                alpha[0] = Color.alpha(col);
                sv.set(hsv[1], hsv[2]);
                hue.set(hsv[0]);
                av.set(alpha[0]);
                fromText[0] = true;
                changed.run();
                fromText[0] = false;
            }
        });
        changed.run();

        final boolean[] finished = {false};
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(title)
                .setView(root)
                .setPositiveButton(Tr.t("ok", "OK"), (x, w) -> {
                    finished[0] = true;
                    done.on(toHex(Color.HSVToColor(alpha[0], hsv)));
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        d.setOnDismissListener(x -> { if (!finished[0]) done.on(null); });
        d.show();
    }

    // ------------------------------------------------------------- views

    interface SV { void on(float s, float v); }
    interface F { void on(float f); }
    interface I { void on(int i); }

    @SuppressLint("ViewConstructor")
    static class SvView extends View {
        SV listener;
        float s, v, hue;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        SvView(Context c) { super(c); }
        void setHue(float h) { hue = h; invalidate(); }
        void set(float s, float v) { this.s = s; this.v = v; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            RectF r = new RectF(0, 0, w, h);
            p.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE, Color.HSVToColor(new float[]{hue, 1, 1}), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, 12, 12, p);
            p.setShader(new LinearGradient(0, 0, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(r, 12, 12, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(Color.WHITE);
            canvas.drawCircle(s * w, (1 - v) * h, 16, p);
            p.setStyle(Paint.Style.FILL);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            s = clamp(e.getX() / getWidth());
            v = 1 - clamp(e.getY() / getHeight());
            invalidate();
            if (listener != null) listener.on(s, v);
            return true;
        }
    }

    @SuppressLint("ViewConstructor")
    static class HueView extends View {
        F listener;
        float hue;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        HueView(Context c) { super(c); }
        void set(float h) { hue = h; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            int[] cols = new int[7];
            for (int i = 0; i < 7; i++) cols[i] = Color.HSVToColor(new float[]{i * 60f, 1, 1});
            p.setShader(new LinearGradient(0, 0, w, 0, cols, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            p.setShader(null);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            canvas.drawCircle(hue / 360f * w, h / 2, h / 2 - 3, p);
            p.setStyle(Paint.Style.FILL);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            hue = clamp(e.getX() / getWidth()) * 359.9f;
            invalidate();
            if (listener != null) listener.on(hue);
            return true;
        }
    }

    @SuppressLint("ViewConstructor")
    static class AlphaView extends View {
        I listener;
        int alpha = 255, color = Color.WHITE;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        AlphaView(Context c) { super(c); }
        void set(int a) { alpha = a; invalidate(); }
        void setColor(int c) { color = c; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            // checker
            float cell = h / 2;
            for (int i = 0; i * cell < w; i++)
                for (int j = 0; j < 2; j++) {
                    p.setColor(((i + j) % 2 == 0) ? 0xFF555555 : 0xFF888888);
                    canvas.drawRect(i * cell, j * cell, Math.min(w, (i + 1) * cell), (j + 1) * cell, p);
                }
            p.setShader(new LinearGradient(0, 0, w, 0, color & 0x00FFFFFF, color | 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, p);
            p.setShader(null);
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            canvas.drawCircle(alpha / 255f * w, h / 2, h / 2 - 3, p);
            p.setStyle(Paint.Style.FILL);
        }
        @SuppressLint("ClickableViewAccessibility")
        @Override public boolean onTouchEvent(MotionEvent e) {
            getParent().requestDisallowInterceptTouchEvent(true);
            alpha = Math.round(clamp(e.getX() / getWidth()) * 255);
            invalidate();
            if (listener != null) listener.on(alpha);
            return true;
        }
    }

    static float clamp(float f) {
        return Math.max(0, Math.min(1, f));
    }
}
