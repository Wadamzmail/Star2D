package com.star4droid.star2d.git.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Small looping illustrations for the "connect an AI" guide, drawn with canvas (no image assets). */
public final class GuideArt extends View {
    public static final int ACCOUNT = 0, WEBSITE = 1, PLUS = 2, MENU = 3, INSTALL_CLAUDE = 4, INSTALL_GPT = 5, ASK = 6, PULL = 7;

    private int kind;
    private float t;
    private String url = "claude.ai";
    private ValueAnimator anim;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;

    public GuideArt(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        tp.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setKind(int k, String url) {
        kind = k;
        if (url != null) this.url = url;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(4200);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            t = (Float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- helpers

    private static float seg(float x, float a, float b) {
        return Math.max(0f, Math.min(1f, (x - a) / (b - a)));
    }

    private static float ease(float x) {
        return x * x * (3f - 2f * x);
    }

    private static float pop(float x) { // overshoot 0..1
        float c = 1.70158f;
        x -= 1f;
        return x * x * ((c + 1f) * x + c) + 1f;
    }

    private void box(Canvas c, float l, float tp_, float rr, float b, float rad, int fill, int stroke) {
        r.set(l, tp_, rr, b);
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawRoundRect(r, rad, rad, p);
        if (stroke != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f * d);
            p.setColor(stroke);
            c.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void text(Canvas c, String s, float x, float y, float sp, int color, boolean bold, Paint.Align al) {
        tp.setTextSize(sp * d);
        tp.setColor(color);
        tp.setFakeBoldText(bold);
        tp.setTextAlign(al);
        c.drawText(s, x, y, tp);
    }

    private void finger(Canvas c, float x, float y, float press) {
        // tap ripple
        if (press > 0f) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(0x66FFFFFF);
            p.setAlpha((int) (140 * (1f - press)));
            c.drawCircle(x, y, (10f + 26f * press) * d, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0x33000000);
        c.drawCircle(x + 2 * d, y + 3 * d, 11 * d, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x, y, (press > 0f && press < 0.5f ? 9f : 11f) * d, p);
        p.setColor(0xFFCFD8DC);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * d);
        c.drawCircle(x, y, 11 * d, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void check(Canvas c, float cx, float cy, float rad, float k) {
        if (k <= 0f) return;
        float s = pop(k);
        p.setColor(Ui.SUCCESS);
        c.drawCircle(cx, cy, rad * s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        Path ph = new Path();
        ph.moveTo(cx - rad * 0.45f * s, cy);
        ph.lineTo(cx - rad * 0.1f * s, cy + rad * 0.38f * s);
        ph.lineTo(cx + rad * 0.5f * s, cy - rad * 0.34f * s);
        c.drawPath(ph, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void cross(Canvas c, float cx, float cy, float rad, float k) {
        if (k <= 0f) return;
        float s = pop(k);
        p.setColor(Ui.DANGER);
        c.drawCircle(cx, cy, rad * s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFFFFFFF);
        float q = rad * 0.38f * s;
        c.drawLine(cx - q, cy - q, cx + q, cy + q, p);
        c.drawLine(cx - q, cy + q, cx + q, cy - q, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        box(c, 0, 0, w, h, 14 * d, 0xFF1D2025, 0xFF2E333A);
        switch (kind) {
            case ACCOUNT: drawAccount(c, w, h); break;
            case WEBSITE: drawWebsite(c, w, h); break;
            case PLUS: drawPlus(c, w, h); break;
            case MENU: drawMenu(c, w, h); break;
            case INSTALL_CLAUDE: drawInstallClaude(c, w, h); break;
            case INSTALL_GPT: drawInstallGpt(c, w, h); break;
            case ASK: drawAsk(c, w, h); break;
            case PULL: drawPull(c, w, h); break;
        }
    }

    private void drawAccount(Canvas c, int w, int h) {
        float cx = w / 2f, cy = h / 2f - 4 * d;
        float k = ease(seg(t, 0f, 0.25f));
        float ring = (t * 2f) % 1f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setColor(0x552196F3);
        p.setAlpha((int) (110 * (1f - ring)));
        c.drawCircle(cx, cy, (34f + 26f * ring) * d, p);
        p.setStyle(Paint.Style.FILL);
        float s = 0.7f + 0.3f * pop(k);
        p.setColor(0xFF2B3037);
        c.drawCircle(cx, cy, 34 * d * s, p);
        p.setColor(0xFF90A4AE);
        c.drawCircle(cx, cy - 8 * d * s, 10 * d * s, p);
        r.set(cx - 18 * d * s, cy + 4 * d * s, cx + 18 * d * s, cy + 30 * d * s);
        c.drawArc(r, 180, 180, true, p);
        check(c, cx + 26 * d, cy + 22 * d, 11 * d, seg(t, 0.35f, 0.5f));
        text(c, "GitHub", cx, h - 12 * d, 13, 0xFFB0B6BF, true, Paint.Align.CENTER);
    }

    private void drawWebsite(Canvas c, int w, int h) {
        float l = 14 * d, rr = w - 14 * d, top = 12 * d, bot = h - 12 * d;
        box(c, l, top, rr, bot, 10 * d, 0xFF262B31, 0xFF3A4048);
        box(c, l + 8 * d, top + 8 * d, rr - 8 * d, top + 30 * d, 11 * d, 0xFF1A1D21, 0);
        int n = Math.min(url.length(), (int) (url.length() * seg(t, 0.05f, 0.45f)));
        text(c, url.substring(0, n) + (t % 0.1f < 0.05f && t < 0.5f ? "|" : ""), l + 18 * d, top + 24 * d, 12.5f, 0xFFE3E8EE, false, Paint.Align.LEFT);
        float k = ease(seg(t, 0.5f, 0.7f));
        box(c, l + 14 * d, bot - 50 * d, rr - 14 * d, bot - 14 * d, 16 * d, 0xFF303640, 0xFF4A515B);
        p.setColor(0xFF2196F3);
        p.setAlpha((int) (255 * k));
        c.drawCircle(l + 34 * d, bot - 32 * d, 11 * d, p);
        p.setAlpha(255);
        text(c, "+", l + 34 * d, bot - 26 * d, 18, 0xFFFFFFFF, true, Paint.Align.CENTER);
    }

    private void drawPlus(Canvas c, int w, int h) {
        float l = 18 * d, rr = w - 18 * d, cy = h / 2f;
        box(c, l, cy - 24 * d, rr, cy + 24 * d, 24 * d, 0xFF303640, 0xFF4A515B);
        text(c, "...", l + 70 * d, cy + 5 * d, 16, 0xFF7D8590, false, Paint.Align.LEFT);
        float pulse = (t * 2f) % 1f;
        float press = seg(t, 0.55f, 0.7f);
        float bx = l + 28 * d;
        p.setColor(0x332196F3);
        p.setAlpha((int) (120 * (1f - pulse)));
        c.drawCircle(bx, cy, (14f + 12f * pulse) * d, p);
        p.setAlpha(255);
        p.setColor(press > 0f && press < 1f ? 0xFF1976D2 : 0xFF2196F3);
        c.drawCircle(bx, cy, 15 * d, p);
        text(c, "+", bx, cy + 7 * d, 22, 0xFFFFFFFF, true, Paint.Align.CENTER);
        float fx = rr - 30 * d + (bx - (rr - 30 * d)) * ease(seg(t, 0.2f, 0.55f));
        float fy = cy + 40 * d - 36 * d * ease(seg(t, 0.2f, 0.55f));
        finger(c, fx, fy, seg(t, 0.55f, 0.85f));
    }

    private void drawMenu(Canvas c, int w, int h) {
        float l = 18 * d, rr = w - 18 * d;
        String[] rows = {Tr.t("guide_row_files", "Files"), "Plugins", Tr.t("guide_row_style", "Style")};
        float top = 10 * d, rowH = 30 * d;
        float k = ease(seg(t, 0f, 0.15f));
        box(c, l, top, rr - 30 * d, top + rowH * 3 + 10 * d, 12 * d, 0xFF2A3037, 0xFF4A515B);
        float hi = ease(seg(t, 0.25f, 0.5f));
        for (int i = 0; i < 3; i++) {
            float y = top + 5 * d + rowH * i;
            boolean target = i == 1;
            if (target) {
                p.setColor(0x552196F3);
                p.setAlpha((int) (255 * hi));
                r.set(l + 6 * d, y + 2 * d, rr - 36 * d, y + rowH - 2 * d);
                c.drawRoundRect(r, 8 * d, 8 * d, p);
                p.setAlpha(255);
            }
            p.setColor(target ? 0xFF64B5F6 : 0xFF5D6670);
            c.drawCircle(l + 22 * d, y + rowH / 2f, 6 * d, p);
            text(c, rows[i], l + 38 * d, y + rowH / 2f + 5 * d, 14, target && hi > 0.5f ? 0xFFFFFFFF : 0xFFB0B6BF, target, Paint.Align.LEFT);
        }
        float fy = top + 5 * d + rowH * 1.5f;
        float fx = rr - 40 * d - (rr - 40 * d - (l + 80 * d)) * ease(seg(t, 0.15f, 0.5f));
        finger(c, fx, fy + 28 * d * (1f - ease(seg(t, 0.15f, 0.5f))), seg(t, 0.55f, 0.85f));
    }

    private void card(Canvas c, float l, float top, float rr, float b, String title, boolean glow, float dim) {
        if (glow) {
            p.setColor(0x442196F3);
            r.set(l - 4 * d, top - 4 * d, rr + 4 * d, b + 4 * d);
            c.drawRoundRect(r, 16 * d, 16 * d, p);
        }
        box(c, l, top, rr, b, 12 * d, glow ? 0xFF2D3A4A : 0xFF2A3037, glow ? 0xFF2196F3 : 0xFF3A4048);
        p.setColor(0xFF455A64);
        c.drawCircle(l + 22 * d, (top + b) / 2f, 11 * d, p);
        text(c, "G", l + 22 * d, (top + b) / 2f + 5 * d, 14, 0xFFFFFFFF, true, Paint.Align.CENTER);
        text(c, title, l + 42 * d, (top + b) / 2f + 5 * d, 13.5f, 0xFFE3E8EE, true, Paint.Align.LEFT);
        if (dim > 0f) {
            p.setColor(0xFF1D2025);
            p.setAlpha((int) (150 * dim));
            r.set(l, top, rr, b);
            c.drawRoundRect(r, 12 * d, 12 * d, p);
            p.setAlpha(255);
        }
    }

    private void drawInstallClaude(Canvas c, int w, int h) {
        float l = 16 * d, rr = w - 16 * d;
        float h1 = h / 2f - 8 * d;
        float ok = seg(t, 0.2f, 0.4f), no = seg(t, 0.55f, 0.75f);
        card(c, l, 10 * d, rr - 34 * d, h1, "GitHub integration", true, 0f);
        card(c, l, h1 + 16 * d, rr - 34 * d, h - 10 * d, "GitHub", false, no);
        check(c, rr - 16 * d, (10 * d + h1) / 2f, 12 * d, ok);
        cross(c, rr - 16 * d, (h1 + 16 * d + h - 10 * d) / 2f, 12 * d, no);
    }

    private void drawInstallGpt(Canvas c, int w, int h) {
        float l = 18 * d, rr = w - 18 * d;
        card(c, l, 12 * d, rr, h / 2f + 6 * d, "GitHub", true, 0f);
        float press = seg(t, 0.35f, 0.5f);
        float bt = h / 2f + 16 * d, bb = h - 14 * d;
        boolean done = t > 0.5f;
        box(c, w / 2f - 70 * d, bt, w / 2f + 70 * d, bb, 18 * d, done ? Ui.SUCCESS : (press > 0f ? 0xFF1976D2 : 0xFF2196F3), 0);
        text(c, done ? Tr.t("guide_connected", "Connected") : Tr.t("guide_connect", "Connect"), w / 2f, (bt + bb) / 2f + 5 * d, 14, 0xFFFFFFFF, true, Paint.Align.CENTER);
        if (!done) finger(c, w / 2f + 20 * d + 60 * d * (1f - ease(seg(t, 0.1f, 0.35f))), (bt + bb) / 2f + 30 * d * (1f - ease(seg(t, 0.1f, 0.35f))), seg(t, 0.35f, 0.5f));
        else check(c, w / 2f + 82 * d, (bt + bb) / 2f, 11 * d, seg(t, 0.5f, 0.6f));
    }

    private void drawAsk(Canvas c, int w, int h) {
        float l = 14 * d, rr = w - 14 * d;
        // user bubble
        box(c, w * 0.18f, 12 * d, rr, 12 * d + 58 * d, 14 * d, 0xFF2D4A6B, 0);
        String[] lines = {Tr.t("guide_ask_1", "In my repo, add an enemy"), Tr.t("guide_ask_2", "to scene1 and make it jump")};
        float rev = seg(t, 0.05f, 0.4f);
        for (int i = 0; i < 2; i++) {
            String s = lines[i];
            float part = Math.max(0f, Math.min(1f, rev * 2f - i));
            int n = (int) (s.length() * part);
            text(c, s.substring(0, n), w * 0.18f + 12 * d, 12 * d + 24 * d + i * 20 * d, 12.5f, 0xFFFFFFFF, false, Paint.Align.LEFT);
        }
        // reply bubble with typing dots, then a "committed" line
        float rt = seg(t, 0.45f, 0.6f);
        if (rt > 0f) {
            box(c, l, 12 * d + 70 * d, w * 0.82f, h - 12 * d, 14 * d, 0xFF2A3037, 0xFF3A4048);
            if (t < 0.75f) {
                for (int i = 0; i < 3; i++) {
                    float bump = (float) Math.sin((t * 14f) - i * 0.9f) * 0.5f + 0.5f;
                    p.setColor(0xFF90A4AE);
                    c.drawCircle(l + 24 * d + i * 14 * d, (12 * d + 70 * d + h - 12 * d) / 2f - bump * 3 * d, 4 * d, p);
                }
            } else {
                text(c, "✓ " + Tr.t("guide_committed", "Pushed to your repo"), l + 14 * d, (12 * d + 70 * d + h - 12 * d) / 2f + 5 * d, 13, Ui.SUCCESS, true, Paint.Align.LEFT);
            }
        }
    }

    private void drawPull(Canvas c, int w, int h) {
        float cx = w / 2f;
        // phone
        box(c, cx - 62 * d, 8 * d, cx + 62 * d, h - 8 * d, 14 * d, 0xFF262B31, 0xFF4A515B);
        // scene preview that "refreshes"
        float refresh = seg(t, 0.55f, 0.8f);
        box(c, cx - 54 * d, 18 * d, cx + 54 * d, h - 52 * d, 8 * d, 0xFF1A1D21, 0);
        p.setColor(0xFF3A4048);
        c.drawRect(cx - 42 * d, h - 70 * d, cx + 42 * d, h - 62 * d, p);
        p.setColor(0xFF2196F3);
        c.drawRoundRect(new RectF(cx - 12 * d, h - 90 * d, cx + 12 * d, h - 70 * d), 4 * d, 4 * d, p);
        if (refresh > 0f) {
            float s = pop(refresh);
            p.setColor(0xFFEF5350);
            c.drawCircle(cx + 28 * d, h - 79 * d, 9 * d * s, p);
        }
        // pull button
        float press = seg(t, 0.35f, 0.5f);
        box(c, cx - 40 * d, h - 44 * d, cx + 40 * d, h - 16 * d, 14 * d, press > 0f && press < 1f ? 0xFF1976D2 : 0xFF2196F3, 0);
        text(c, "↓ " + Tr.t("git_pull", "Pull"), cx, h - 25 * d, 13.5f, 0xFFFFFFFF, true, Paint.Align.CENTER);
        // download arrows falling in
        float f = seg(t, 0.45f, 0.75f);
        for (int i = 0; i < 3; i++) {
            float k = (f * 1.5f - i * 0.25f);
            if (k <= 0f || k >= 1f) continue;
            p.setColor(0xFF64B5F6);
            p.setAlpha((int) (255 * (1f - Math.abs(k - 0.5f) * 2f)));
            float x = cx - 20 * d + i * 20 * d;
            c.drawRect(x - 2 * d, 18 * d + k * 60 * d, x + 2 * d, 30 * d + k * 60 * d, p);
            p.setAlpha(255);
        }
        finger(c, cx + 30 * d + 40 * d * (1f - ease(seg(t, 0.1f, 0.35f))), h - 20 * d + 24 * d * (1f - ease(seg(t, 0.1f, 0.35f))), seg(t, 0.35f, 0.55f));
    }
}
