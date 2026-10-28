package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * The animated background of the loading screen, in the style of the editor: dark grid, a soft accent glow,
 * slowly floating dots, and ripples + sparks wherever the finger touches. Pure Canvas, no assets.
 */
public class SplashView extends View {
    private static final int BG = 0xFF17191D, ACCENT = 0xFF2196F3;

    private static final class Dot { float x, y, vy, r, a, ph; }
    private static final class Ripple { float x, y; float t; }
    private static final class Spark { float x, y, vx, vy, life; }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private final Dot[] dots = new Dot[36];
    private final ArrayList<Ripple> ripples = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final float density;
    private float scroll, tilt, tiltTarget, time;
    private long last;
    private RadialGradient glow;
    private float lastTouchSpark;

    public SplashView(Context c) { this(c, null); }

    public SplashView(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < dots.length; i++) dots[i] = new Dot();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        glow = new RadialGradient(w / 2f, h / 2f, Math.max(w, h) * 0.55f, new int[]{0x302196F3, 0x00000000}, null, Shader.TileMode.CLAMP);
        for (Dot d : dots) reset(d, true, w, h);
    }

    private void reset(Dot d, boolean anywhere, int w, int h) {
        d.x = rnd.nextFloat() * w;
        d.y = anywhere ? rnd.nextFloat() * h : h + 10 * density;
        d.vy = (8 + rnd.nextFloat() * 22) * density;
        d.r = (1f + rnd.nextFloat() * 2.2f) * density;
        d.a = 0.15f + rnd.nextFloat() * 0.35f;
        d.ph = rnd.nextFloat() * 6.28f;
    }

    /** A burst of ripples and sparks at a point (touch, or from the logo when it is tapped). */
    public void burst(float x, float y) {
        Ripple r = new Ripple();
        r.x = x;
        r.y = y;
        ripples.add(r);
        for (int i = 0; i < 12; i++) {
            Spark s = new Spark();
            double ang = rnd.nextDouble() * Math.PI * 2;
            float sp = (60 + rnd.nextFloat() * 160) * density;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(ang) * sp;
            s.vy = (float) Math.sin(ang) * sp;
            s.life = 1f;
            sparks.add(s);
        }
        if (ripples.size() > 8) ripples.remove(0);
        if (sparks.size() > 160) for (int i = 0; i < 12; i++) sparks.remove(0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        tiltTarget = Math.max(-1f, Math.min(1f, (e.getX() - cx) / Math.max(1f, cx)));
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                burst(e.getX(), e.getY());
                lastTouchSpark = time;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (time - lastTouchSpark > 0.12f) {
                    lastTouchSpark = time;
                    Spark s = new Spark();
                    s.x = e.getX();
                    s.y = e.getY();
                    s.vx = (rnd.nextFloat() - 0.5f) * 40 * density;
                    s.vy = (rnd.nextFloat() - 0.5f) * 40 * density;
                    s.life = 1f;
                    sparks.add(s);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tiltTarget = 0;
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        last = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        time += dt;
        scroll += dt * 10 * density;
        tilt += (tiltTarget - tilt) * Math.min(1f, dt * 4f);

        c.drawColor(BG);
        if (glow != null) {
            paint.setShader(glow);
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }

        // grid, slowly drifting, shifting a little towards the finger
        float step = 24 * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, density * 0.6f));
        paint.setColor(0x16FFFFFF);
        float ox = (scroll + tilt * 10 * density) % step, oy = scroll % step;
        for (float x = -step + ox; x < w + step; x += step) c.drawLine(x, 0, x, h, paint);
        for (float y = -step + oy; y < h + step; y += step) c.drawLine(0, y, w, y, paint);

        // floating dots
        paint.setStyle(Paint.Style.FILL);
        for (Dot d : dots) {
            d.y -= d.vy * dt;
            if (d.y < -10 * density) reset(d, false, w, h);
            float tw = 0.6f + 0.4f * (float) Math.sin(time * 2f + d.ph);
            paint.setColor(((int) (d.a * tw * 255) << 24) | 0x6EB9F7);
            c.drawCircle(d.x + tilt * 6 * density * (d.r / density), d.y, d.r, paint);
        }

        // ripples
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * density);
        for (int i = ripples.size() - 1; i >= 0; i--) {
            Ripple r = ripples.get(i);
            r.t += dt;
            float p = r.t / 0.9f;
            if (p >= 1f) {
                ripples.remove(i);
                continue;
            }
            float ease = 1f - (1f - p) * (1f - p);
            paint.setColor(((int) ((1f - p) * 0.8f * 255) << 24) | 0x2196F3);
            c.drawCircle(r.x, r.y, (12 + 110 * ease) * density, paint);
        }

        // sparks
        paint.setStyle(Paint.Style.FILL);
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= dt * 1.4f;
            if (s.life <= 0) {
                sparks.remove(i);
                continue;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.vx *= 0.96f;
            s.vy *= 0.96f;
            paint.setColor(((int) (s.life * 255) << 24) | 0x90CAF9);
            c.drawCircle(s.x, s.y, 2.2f * density * s.life + density * 0.6f, paint);
        }
        postInvalidateOnAnimation();
    }
}
