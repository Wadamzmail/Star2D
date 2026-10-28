package com.star4droid.star2d.git.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import com.star4droid.star2d.git.GitRepo;
import com.star4droid.star2d.ui.Ui;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The commit tree: lanes on one side, message / author / branch labels next to them. Draws only the visible rows. */
public class HistoryView extends View {
    public interface OnCommit { void on(GitRepo.CommitInfo c); }

    private static final int[] PALETTE = {0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726, 0xFFAB47BC, 0xFFEF5350, 0xFF26C6DA, 0xFFFFEE58, 0xFF8D6E63};

    private List<GitRepo.CommitInfo> commits = new ArrayList<>();
    private OnCommit onCommit;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint msg = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint chip = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sel = new Paint();
    private final int rowH, laneW, pad;
    private int lanes = 1;
    private int selected = -1;
    private final Path path = new Path();
    private final Rect clip = new Rect();
    private final DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    private final boolean rtl;

    public HistoryView(Context c) {
        super(c);
        rtl = Ui.isRtl(c);
        rowH = Ui.dp(c, 58);
        laneW = Ui.dp(c, 18);
        pad = Ui.dp(c, 12);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(c, 2));
        line.setStrokeCap(Paint.Cap.ROUND);
        msg.setColor(Ui.TEXT);
        msg.setTextSize(Ui.dp(c, 14.5f));
        sub.setColor(Ui.TEXT_3);
        sub.setTextSize(Ui.dp(c, 11.5f));
        chip.setColor(Ui.TEXT);
        chip.setTextSize(Ui.dp(c, 10.5f));
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        sel.setColor(Ui.ACCENT_SOFT);
        setClickable(true);
    }

    public void setCommits(List<GitRepo.CommitInfo> list) {
        commits = list;
        lanes = 1;
        for (GitRepo.CommitInfo ci : list) {
            lanes = Math.max(lanes, ci.lane + 1);
            for (int l : ci.through) lanes = Math.max(lanes, l + 1);
            for (int l : ci.from) lanes = Math.max(lanes, l + 1);
            for (int l : ci.to) lanes = Math.max(lanes, l + 1);
        }
        lanes = Math.min(lanes, 8);
        requestLayout();
        invalidate();
    }

    public void setOnCommit(OnCommit l) {
        onCommit = l;
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.max(commits.size() * rowH, Ui.dp(getContext(), 80)));
    }

    private float laneX(int lane) {
        float x = pad + laneW * (Math.min(lane, 7) + 0.5f);
        return rtl ? getWidth() - x : x;
    }

    private int color(int lane) {
        return PALETTE[Math.abs(lane) % PALETTE.length];
    }

    private void seg(Canvas c, float x1, float y1, float x2, float y2, int col) {
        line.setColor(col);
        if (x1 == x2) {
            c.drawLine(x1, y1, x2, y2, line);
        } else {
            path.reset();
            path.moveTo(x1, y1);
            float my = (y1 + y2) / 2f;
            path.cubicTo(x1, my, x2, my, x2, y2);
            c.drawPath(path, line);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!c.getClipBounds(clip)) return;
        int first = Math.max(0, clip.top / rowH), last = Math.min(commits.size() - 1, clip.bottom / rowH);
        float textStart = pad * 2 + laneW * lanes;
        float textW = getWidth() - textStart - pad;
        for (int i = first; i <= last; i++) {
            GitRepo.CommitInfo ci = commits.get(i);
            float top = i * rowH, mid = top + rowH / 2f, bot = top + rowH;
            if (i == selected) c.drawRect(0, top, getWidth(), bot, sel);
            for (int l : ci.through) seg(c, laneX(l), top, laneX(l), bot, color(l));
            for (int l : ci.from) seg(c, laneX(l), top, laneX(ci.lane), mid, color(l));
            for (int l : ci.to) seg(c, laneX(ci.lane), mid, laneX(l), bot, color(l));
            dot.setColor(color(ci.lane));
            float r = Ui.dp(getContext(), ci.parents.size() > 1 ? 5 : 4.5f);
            c.drawCircle(laneX(ci.lane), mid, r, dot);
            if (ci.parents.size() > 1) {
                dot.setColor(Ui.SURFACE);
                c.drawCircle(laneX(ci.lane), mid, r - Ui.dp(getContext(), 2), dot);
            }
            // branch / tag chips
            float x = rtl ? getWidth() - textStart : textStart;
            float y1 = top + rowH * 0.36f;
            float cursor = 0;
            for (String ref : ci.refs) {
                String label = TextUtils.ellipsize(ref, chip, textW * 0.4f, TextUtils.TruncateAt.END).toString();
                float tw = chip.measureText(label), cw = tw + Ui.dp(getContext(), 10);
                if (cursor + cw > textW * 0.7f) break;
                chipBg.setColor(ref.startsWith("origin/") ? 0x55607D8B : 0x552196F3);
                float left = rtl ? x - cursor - cw : x + cursor;
                c.drawRoundRect(left, y1 - Ui.dp(getContext(), 11), left + cw, y1 + Ui.dp(getContext(), 4), Ui.dp(getContext(), 7), Ui.dp(getContext(), 7), chipBg);
                c.drawText(label, left + Ui.dp(getContext(), 5), y1, chip);
                cursor += cw + Ui.dp(getContext(), 4);
            }
            float mw = textW - cursor;
            String m = TextUtils.ellipsize(ci.message, msg, Math.max(mw, textW * 0.3f), TextUtils.TruncateAt.END).toString();
            float mx = rtl ? x - cursor - msg.measureText(m) : x + cursor;
            if (cursor == 0 && rtl) mx = x - msg.measureText(m);
            c.drawText(m, mx, y1, msg);
            String s = ci.author + " · " + df.format(new Date(ci.time)) + " · " + ci.shortId;
            s = TextUtils.ellipsize(s, sub, textW, TextUtils.TruncateAt.END).toString();
            float sx = rtl ? x - sub.measureText(s) : x;
            c.drawText(s, sx, top + rowH * 0.74f, sub);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int i = (int) (e.getY() / rowH);
            if (i >= 0 && i < commits.size()) {
                selected = i;
                invalidate();
                performClick();
                if (onCommit != null) onCommit.on(commits.get(i));
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
