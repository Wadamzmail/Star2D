package com.star4droid.star2d.ui.blocks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;

/**
 * Scratch-like block workspace drawn on one canvas (fast with many blocks).
 * Stacks are linked nodes; boolean nodes are C-blocks with "then" / "else" mouths.
 */
@SuppressLint("ViewConstructor")
public class BlockCanvas extends View {

    public interface Listener {
        void onFieldTap(BlockModel.Node node, BlockModel.Field field);
        void onInfo(BlockModel.Node node);
        void onSelect(BlockModel.Node node);
        void onLongPress(BlockModel.Node node, float screenX, float screenY);
        /** Structure changed (for undo + dirty flag). Called before the change is applied. */
        void beforeChange();
        void afterChange();
    }

    private BlockModel model;
    private final Listener listener;
    private final boolean rtl;
    private String rootLabel = "";

    // view transform
    private float scale = 1f, tx, ty;
    private final float d; // density

    // layout
    private boolean dirty = true;
    private final ArrayList<Box> boxes = new ArrayList<>();
    private final ArrayList<Target> targets = new ArrayList<>();
    private final IdentityHashMap<BlockModel.Node, Object[]> parentOf = new IdentityHashMap<>(); // node -> {parent, slot}
    private final HashMap<String, Integer> colorCache = new HashMap<>();

    // dragging
    private BlockModel.Node drag; // head of the dragged chain
    private float dragX, dragY, grabDX, grabDY;
    private final ArrayList<Box> dragBoxes = new ArrayList<>();
    private Target hover;
    private boolean overTrash;
    private BlockModel.Node selected;

    // paints
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();

    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;

    // sizes (world px)
    private final float ROW, PAD, CHIP_H, INDENT, MOUTH_MIN, BAR, GAP, MAXW, RADIUS, INFO_R;

    static class Chip { BlockModel.Field field; RectF r = new RectF(); String label; boolean empty; boolean param; }

    static class Box {
        BlockModel.Node node;
        RectF header = new RectF();
        RectF all = new RectF();
        RectF yesMouth, elseBar, noMouth, bottom;
        RectF info = new RectF();
        String label;
        ArrayList<Chip> chips = new ArrayList<>();
        ArrayList<float[]> textRows = new ArrayList<>(); // {x, y} of the label
        int color;
    }

    static class Target {
        BlockModel.Node parent; // null for "before head"
        int slot; // 0 next, 1 yes, 2 no, -1 before head
        BlockModel.Node head;
        float x, y;
    }

    public BlockCanvas(Context c, Listener listener, boolean rtl) {
        super(c);
        this.listener = listener;
        this.rtl = rtl;
        d = c.getResources().getDisplayMetrics().density;
        ROW = 34 * d; PAD = 8 * d; CHIP_H = 24 * d; INDENT = 18 * d; MOUTH_MIN = 26 * d; BAR = 20 * d;
        GAP = 2 * d; MAXW = 320 * d; RADIUS = 7 * d; INFO_R = 8 * d;
        text.setColor(Color.WHITE);
        text.setTextSize(13.5f * d);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        chipText.setTextSize(12.5f * d);
        chipText.setColor(0xFF1B1B1B);
        stroke.setStyle(Paint.Style.STROKE);
        grid.setColor(0x14FFFFFF);
        grid.setStrokeWidth(1);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector det) {
                float ns = Math.max(0.3f, Math.min(2.5f, scale * det.getScaleFactor()));
                float fx = det.getFocusX(), fy = det.getFocusY();
                tx = fx - (fx - tx) * (ns / scale);
                ty = fy - (fy - ty) * (ns / scale);
                scale = ns;
                invalidate();
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onSingleTapUp(MotionEvent e) { tap(e.getX(), e.getY()); return true; }
            @Override public void onLongPress(MotionEvent e) {
                if (drag != null) return;
                Box b = boxAt(wx(e.getX()), wy(e.getY()));
                if (b != null) {
                    selected = b.node;
                    invalidate();
                    listener.onLongPress(b.node, e.getRawX(), e.getRawY());
                }
            }
        });
        tx = 12 * d;
        ty = 12 * d;
    }

    public void setModel(BlockModel m, String rootLabel) {
        this.model = m;
        this.rootLabel = rootLabel;
        selected = null;
        relayout();
    }

    public BlockModel model() { return model; }
    public BlockModel.Node selected() { return selected; }
    public void select(BlockModel.Node n) { selected = n; invalidate(); }

    public void relayout() {
        dirty = true;
        invalidate();
    }

    public float zoom() { return scale; }

    public void zoomBy(float f) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float ns = Math.max(0.3f, Math.min(2.5f, scale * f));
        tx = cx - (cx - tx) * (ns / scale);
        ty = cy - (cy - ty) * (ns / scale);
        scale = ns;
        invalidate();
    }

    /** Scroll so the root stack is visible at the top-left. */
    public void home() {
        BlockModel.Node r = model == null ? null : model.root();
        BlockModel.Node h = r;
        if (model != null && !model.heads.isEmpty()) h = model.heads.get(0);
        if (h != null) {
            tx = 12 * d - h.x * scale;
            ty = 12 * d - h.y * scale;
        }
        invalidate();
    }

    // ================================================================ coords

    private float wx(float sx) { return (sx - tx) / scale; }
    private float wy(float sy) { return (sy - ty) / scale; }

    /** World point at the center of the visible area (for new blocks). */
    public float[] centerWorld() {
        return new float[]{wx(getWidth() * 0.3f), wy(getHeight() * 0.3f)};
    }

    // ================================================================ layout

    private int colorFor(BlockModel.Node n) {
        if (n.isRoot()) return 0xFFFFAB00;
        Integer c = colorCache.get(n.title);
        if (c != null) return c;
        String cat = BlockCatalog.categoryOfTitle(n.title);
        int col;
        if (cat != null) col = BlockCatalog.colorOf(cat);
        else if (n.color != -1 && n.color != 0 && (n.color & 0xFF000000) == 0xFF000000) col = n.color;
        else col = 0xFF546E7A;
        colorCache.put(n.title, col);
        return col;
    }

    private void doLayout() {
        boxes.clear();
        targets.clear();
        parentOf.clear();
        if (model == null) return;
        for (BlockModel.Node h : model.heads) {
            Target t = new Target();
            t.slot = -1;
            t.head = h;
            t.x = h.x;
            t.y = h.y;
            if (!h.isRoot()) targets.add(t);
            layoutChain(h, h.x, h.y, null, -1, boxes, true);
        }
        dirty = false;
    }

    /** Lays out a chain; returns bottom y. */
    private float layoutChain(BlockModel.Node n, float x, float y, BlockModel.Node parent, int slot, ArrayList<Box> out, boolean addTargets) {
        BlockModel.Node prev = parent;
        int prevSlot = slot;
        float cy = y;
        while (n != null) {
            if (prev != null) parentOf.put(n, new Object[]{prev, prevSlot});
            Box b = layoutBlock(n, x, cy, out, addTargets);
            cy = b.all.bottom + GAP;
            if (addTargets) {
                Target t = new Target();
                t.parent = n;
                t.slot = 0;
                t.x = x;
                t.y = b.all.bottom;
                targets.add(t);
            }
            prev = n;
            prevSlot = 0;
            n = n.next;
        }
        return cy;
    }

    private Box layoutBlock(BlockModel.Node n, float x, float y, ArrayList<Box> out, boolean addTargets) {
        Box b = new Box();
        b.node = n;
        b.color = colorFor(n);
        b.label = n.isRoot() ? rootLabel : BlockInfo.label(n.title, rtl);
        out.add(b);
        // header rows: label then chips, wrapping at MAXW
        float labelW = text.measureText(b.label);
        float rowX = x + PAD + labelW + PAD, rowY = y;
        float width = PAD + labelW + PAD;
        int rows = 1;
        b.textRows.add(new float[]{x + PAD, y});
        for (BlockModel.Field f : n.fields) {
            Chip c = new Chip();
            c.field = f;
            String lbl = BlockInfo.field(f.label(), rtl);
            if (f.block != null) {
                c.param = true;
                c.label = "ƒ " + f.block.text();
            } else if (f.value.isEmpty()) {
                c.empty = true;
                c.label = lbl;
            } else c.label = lbl + ": " + f.value.replace('\n', ' ');
            c.label = TextUtils.ellipsize(c.label, new android.text.TextPaint(chipText), 170 * d, TextUtils.TruncateAt.END).toString();
            float cw = chipText.measureText(c.label) + 16 * d;
            if (rowX + cw - x > MAXW && rowX > x + PAD + labelW + PAD + 1) {
                rows++;
                rowY += ROW;
                rowX = x + PAD;
            }
            c.r.set(rowX, rowY + (ROW - CHIP_H) / 2, rowX + cw, rowY + (ROW + CHIP_H) / 2);
            rowX += cw + 6 * d;
            width = Math.max(width, rowX - x + PAD);
            b.chips.add(c);
        }
        width = Math.max(width + 2 * INFO_R + 4 * d, 120 * d);
        float headerH = rows * ROW;
        b.header.set(x, y, x + width, y + headerH);
        b.info.set(x + width - 2 * INFO_R - 6 * d, y + 5 * d, x + width - 6 * d, y + 5 * d + 2 * INFO_R);
        float bottom = y + headerH;
        float maxW = width;
        if (n.isIf) {
            float my = bottom;
            int startYes = out.size();
            float innerBottom = n.yes != null ? layoutChain(n.yes, x + INDENT, my, n, 1, out, addTargets) - GAP : my;
            if (addTargets) addSlotTarget(n, 1, x + INDENT, my);
            float yh = Math.max(MOUTH_MIN, innerBottom - my);
            b.yesMouth = new RectF(x + INDENT, my, x + width, my + yh);
            bottom = my + yh;
            maxW = Math.max(maxW, rightOf(out, startYes) - x);
            if (n.hasElse()) {
                b.elseBar = new RectF(x, bottom, x + width, bottom + BAR);
                bottom += BAR;
                float ny = bottom;
                int startNo = out.size();
                float nb = n.no != null ? layoutChain(n.no, x + INDENT, ny, n, 2, out, addTargets) - GAP : ny;
                if (addTargets) addSlotTarget(n, 2, x + INDENT, ny);
                float nh = Math.max(MOUTH_MIN, nb - ny);
                b.noMouth = new RectF(x + INDENT, ny, x + width, ny + nh);
                bottom = ny + nh;
                maxW = Math.max(maxW, rightOf(out, startNo) - x);
            }
            b.bottom = new RectF(x, bottom, x + width, bottom + BAR * 0.7f);
            bottom += BAR * 0.7f;
        }
        b.all.set(x, y, x + Math.max(width, maxW), bottom);
        return b;
    }

    private static float rightOf(ArrayList<Box> out, int from) {
        float r = 0;
        for (int i = from; i < out.size(); i++) r = Math.max(r, out.get(i).all.right);
        return r;
    }

    private void addSlotTarget(BlockModel.Node n, int slot, float x, float y) {
        Target t = new Target();
        t.parent = n;
        t.slot = slot;
        t.x = x;
        t.y = y;
        targets.add(t);
    }

    // ================================================================ draw

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF15181C);
        if (dirty) doLayout();
        // grid
        float step = 24 * d * scale;
        if (step > 6) {
            float ox = ((tx % step) + step) % step, oy = ((ty % step) + step) % step;
            for (float gx = ox; gx < getWidth(); gx += step) c.drawLine(gx, 0, gx, getHeight(), grid);
            for (float gy = oy; gy < getHeight(); gy += step) c.drawLine(0, gy, getWidth(), gy, grid);
        }
        c.save();
        c.translate(tx, ty);
        c.scale(scale, scale);
        float l = wx(0), t = wy(0), r = wx(getWidth()), btm = wy(getHeight());
        for (Box b : boxes) {
            if (b.all.right < l || b.all.left > r || b.all.bottom < t || b.all.top > btm) continue;
            drawBox(c, b, false);
        }
        // drop indicator
        if (drag != null && hover != null && !overTrash) {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(4 * d);
            stroke.setPathEffect(null);
            c.drawLine(hover.x, hover.y, hover.x + 110 * d, hover.y, stroke);
        }
        // dragged chain
        if (drag != null) {
            for (Box b : dragBoxes) drawBox(c, b, true);
        }
        c.restore();
        if (drag != null) drawTrash(c);
    }

    private void drawBox(Canvas c, Box b, boolean ghost) {
        int col = b.color;
        fill.setColor(ghost ? (col & 0x00FFFFFF) | 0xD0000000 : col);
        boolean root = b.node.isRoot();
        // header (hat for root)
        if (root) {
            Path p = new Path();
            RectF h = b.header;
            p.moveTo(h.left, h.top + 10 * d);
            p.quadTo(h.left + 40 * d, h.top - 8 * d, h.left + 90 * d, h.top + 4 * d);
            p.lineTo(h.right - RADIUS, h.top + 4 * d);
            p.quadTo(h.right, h.top + 4 * d, h.right, h.top + 4 * d + RADIUS);
            p.lineTo(h.right, h.bottom - RADIUS);
            p.quadTo(h.right, h.bottom, h.right - RADIUS, h.bottom);
            p.lineTo(h.left + RADIUS, h.bottom);
            p.quadTo(h.left, h.bottom, h.left, h.bottom - RADIUS);
            p.close();
            c.drawPath(p, fill);
        } else c.drawRoundRect(b.header, RADIUS, RADIUS, fill);
        if (b.node.isIf) {
            // spine + bars
            c.drawRect(b.header.left, b.header.bottom - RADIUS, b.header.left + INDENT, b.bottom.bottom - RADIUS, fill);
            if (b.elseBar != null) c.drawRect(b.elseBar, fill);
            c.drawRoundRect(b.bottom, RADIUS, RADIUS, fill);
            text.setTextSize(11.5f * d);
            text.setAlpha(200);
            if (b.elseBar != null)
                c.drawText(rtl ? "وإلا" : "else", b.elseBar.left + PAD, b.elseBar.centerY() + 4 * d, text);
            text.setAlpha(255);
            text.setTextSize(13.5f * d);
            // empty mouths hint
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.2f * d);
            stroke.setColor(0x33FFFFFF);
            stroke.setPathEffect(new DashPathEffect(new float[]{6 * d, 5 * d}, 0));
            if (b.node.yes == null && b.yesMouth != null)
                c.drawRoundRect(new RectF(b.yesMouth.left + 4 * d, b.yesMouth.top + 4 * d, b.yesMouth.left + 120 * d, b.yesMouth.bottom - 4 * d), RADIUS, RADIUS, stroke);
            if (b.node.no == null && b.noMouth != null)
                c.drawRoundRect(new RectF(b.noMouth.left + 4 * d, b.noMouth.top + 4 * d, b.noMouth.left + 120 * d, b.noMouth.bottom - 4 * d), RADIUS, RADIUS, stroke);
            stroke.setPathEffect(null);
        }
        // selection outline
        if (b.node == selected && !ghost) {
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(2.5f * d);
            c.drawRoundRect(b.header, RADIUS, RADIUS, stroke);
        }
        // label
        float[] tp = b.textRows.get(0);
        c.drawText(b.label, tp[0], tp[1] + ROW / 2 + 5 * d, text);
        // chips
        for (Chip ch : b.chips) {
            fill.setColor(ch.param ? 0xFF263238 : (ch.empty ? 0x66FFFFFF : 0xF2FFFFFF));
            c.drawRoundRect(ch.r, CHIP_H / 2, CHIP_H / 2, fill);
            chipText.setColor(ch.param ? 0xFF80DEEA : (ch.empty ? 0xFF2B2B2B : 0xFF1B1B1B));
            c.drawText(ch.label, ch.r.left + 8 * d, ch.r.centerY() + 4.5f * d, chipText);
        }
        // (!) info button
        if (!ghost) {
            fill.setColor(0x40000000);
            c.drawCircle(b.info.centerX(), b.info.centerY(), INFO_R, fill);
            text.setTextSize(12 * d);
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText("!", b.info.centerX(), b.info.centerY() + 4.5f * d, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTextSize(13.5f * d);
        }
    }

    private RectF trashRect() {
        float s = 64 * d;
        return new RectF(getWidth() - s - 16 * d, getHeight() - s - 16 * d, getWidth() - 16 * d, getHeight() - 16 * d);
    }

    private void drawTrash(Canvas c) {
        RectF r = trashRect();
        fill.setColor(overTrash ? 0xFFE53935 : 0xAA37474F);
        c.drawRoundRect(r, 16 * d, 16 * d, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(24 * d);
        c.drawText("🗑", r.centerX(), r.centerY() + 9 * d, text);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(13.5f * d);
    }

    // ================================================================ hit testing

    private Box boxAt(float x, float y) {
        for (int i = boxes.size() - 1; i >= 0; i--) {
            Box b = boxes.get(i);
            if (b.header.contains(x, y)) return b;
            if (b.node.isIf && (inRect(b.elseBar, x, y) || inRect(b.bottom, x, y)
                    || (x >= b.header.left && x <= b.header.left + INDENT && y >= b.header.top && y <= b.all.bottom))) return b;
        }
        return null;
    }

    private static boolean inRect(RectF r, float x, float y) {
        return r != null && r.contains(x, y);
    }

    private void tap(float sx, float sy) {
        float x = wx(sx), y = wy(sy);
        Box b = boxAt(x, y);
        if (b == null) {
            selected = null;
            invalidate();
            listener.onSelect(null);
            return;
        }
        RectF inf = new RectF(b.info);
        inf.inset(-6 * d, -6 * d);
        if (inf.contains(x, y)) {
            listener.onInfo(b.node);
            return;
        }
        for (Chip ch : b.chips) {
            RectF r = new RectF(ch.r);
            r.inset(-3 * d, -4 * d);
            if (r.contains(x, y)) {
                selected = b.node;
                invalidate();
                listener.onFieldTap(b.node, ch.field);
                return;
            }
        }
        selected = b.node;
        invalidate();
        listener.onSelect(b.node);
    }

    // ================================================================ touch

    private float lastX, lastY, downX, downY;
    private boolean panning, pendingDrag;
    private Box downBox;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (drag == null) gestures.onTouchEvent(e);
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                downX = lastX = x;
                downY = lastY = y;
                if (dirty) doLayout();
                downBox = drag == null ? boxAt(wx(x), wy(y)) : null;
                pendingDrag = downBox != null;
                panning = !pendingDrag && drag == null;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                pendingDrag = false;
                panning = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() > 1) {
                    // two-finger pan alongside pinch
                    lastX = x;
                    lastY = y;
                    return true;
                }
                if (drag != null) {
                    moveDrag(x, y);
                    return true;
                }
                float slop = 10 * d;
                if (pendingDrag && (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop)) {
                    startDrag(downBox, x, y);
                    pendingDrag = false;
                    return true;
                }
                if (panning) {
                    tx += x - lastX;
                    ty += y - lastY;
                    invalidate();
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drag != null) endDrag(e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                pendingDrag = false;
                panning = false;
                return true;
        }
        return true;
    }

    private void startDrag(Box b, float sx, float sy) {
        BlockModel.Node n = b.node;
        listener.beforeChange();
        // detach
        boolean isHead = model.heads.contains(n);
        if (isHead) {
            model.heads.remove(n);
        } else {
            Object[] p = parentOf.get(n);
            if (p != null) {
                BlockModel.Node par = (BlockModel.Node) p[0];
                int slot = (Integer) p[1];
                if (slot == 0) par.next = null;
                else if (slot == 1) par.yes = null;
                else par.no = null;
            }
        }
        drag = n;
        grabDX = wx(sx) - b.header.left;
        grabDY = wy(sy) - b.header.top;
        dirty = true;
        moveDrag(sx, sy);
    }

    /** Start dragging a new chain coming from the palette (screen coords of this view). */
    public void beginExternalDrag(BlockModel.Node chain, float sx, float sy) {
        listener.beforeChange();
        drag = chain;
        grabDX = 20 * d;
        grabDY = 14 * d;
        dirty = true;
        moveDrag(sx, sy);
    }

    public boolean isDragging() { return drag != null; }

    /** For drags forwarded from outside the canvas. */
    public void externalMove(float sx, float sy) { if (drag != null) moveDrag(sx, sy); }
    public void externalUp(float sx, float sy) { if (drag != null) { moveDrag(sx, sy); endDrag(false); } }

    private void moveDrag(float sx, float sy) {
        dragX = wx(sx) - grabDX;
        dragY = wy(sy) - grabDY;
        if (dirty) doLayout();
        dragBoxes.clear();
        layoutChainForDrag();
        overTrash = trashRect().contains(sx, sy) && !drag.isRoot();
        hover = null;
        if (!drag.isRoot() && !overTrash) {
            float best = 44 * d / scale;
            for (Target t : targets) {
                float dd = (float) Math.hypot(t.x - dragX, t.y - dragY);
                if (dd < best) { best = dd; hover = t; }
            }
        }
        // auto scroll near edges
        float edge = 36 * d;
        if (sx < edge) tx += 12 * d; else if (sx > getWidth() - edge) tx -= 12 * d;
        if (sy < edge) ty += 12 * d; else if (sy > getHeight() - edge) ty -= 12 * d;
        invalidate();
    }

    private void layoutChainForDrag() {
        ArrayList<Box> tmp = new ArrayList<>();
        IdentityHashMap<BlockModel.Node, Object[]> saved = new IdentityHashMap<>(parentOf);
        ArrayList<Target> savedTargets = new ArrayList<>(targets);
        layoutChain(drag, dragX, dragY, null, -1, tmp, false);
        parentOf.clear();
        parentOf.putAll(saved);
        targets.clear();
        targets.addAll(savedTargets);
        dragBoxes.addAll(tmp);
    }

    private void endDrag(boolean cancel) {
        BlockModel.Node chain = drag;
        drag = null;
        if (overTrash && !chain.isRoot()) {
            if (selected != null) selected = null;
        } else if (hover != null && !chain.isRoot()) {
            BlockModel.Node tail = chain.tail();
            if (hover.slot == -1) {
                int idx = model.heads.indexOf(hover.head);
                tail.next = hover.head;
                chain.x = hover.head.x;
                chain.y = hover.head.y - 0;
                if (idx >= 0) model.heads.set(idx, chain); else model.heads.add(chain);
            } else {
                BlockModel.Node p = hover.parent;
                BlockModel.Node old = hover.slot == 0 ? p.next : (hover.slot == 1 ? p.yes : p.no);
                tail.next = old;
                if (hover.slot == 0) p.next = chain;
                else if (hover.slot == 1) p.yes = chain;
                else p.no = chain;
            }
        } else {
            chain.x = dragX;
            chain.y = dragY;
            if (chain.isRoot()) model.heads.add(0, chain); else model.heads.add(chain);
        }
        hover = null;
        overTrash = false;
        dragBoxes.clear();
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    // ================================================================ editing helpers (used by the editor)

    /** Insert a chain after the selected block, or at the end of the root stack. */
    public void insert(BlockModel.Node chain) {
        listener.beforeChange();
        BlockModel.Node anchor = selected;
        if (anchor != null) {
            BlockModel.Node tail = chain.tail();
            tail.next = anchor.next;
            anchor.next = chain;
        } else {
            BlockModel.Node root = model.root();
            if (root != null) root.tail().next = chain;
            else {
                float[] w = centerWorld();
                chain.x = w[0];
                chain.y = w[1];
                model.heads.add(chain);
            }
        }
        selected = chain;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    /** Remove a single block (keeps the blocks after it, and lifts its inner blocks out). */
    public void deleteSingle(BlockModel.Node n) {
        if (n.isRoot()) return;
        listener.beforeChange();
        if (dirty) doLayout();
        // collect inner blocks to keep them
        BlockModel.Node replacement = n.next;
        if (n.yes != null || n.no != null) {
            BlockModel.Node inner = null;
            for (BlockModel.Node part : new BlockModel.Node[]{n.yes, n.no}) {
                if (part == null) continue;
                if (inner == null) inner = part; else inner.tail().next = part;
            }
            if (inner != null) {
                inner.tail().next = n.next;
                replacement = inner;
            }
        }
        Object[] p = parentOf.get(n);
        if (p != null) {
            BlockModel.Node par = (BlockModel.Node) p[0];
            int slot = (Integer) p[1];
            if (slot == 0) par.next = replacement;
            else if (slot == 1) par.yes = replacement;
            else par.no = replacement;
        } else {
            int idx = model.heads.indexOf(n);
            if (idx >= 0) {
                if (replacement != null) {
                    replacement.x = n.x;
                    replacement.y = n.y;
                    model.heads.set(idx, replacement);
                } else model.heads.remove(idx);
            }
        }
        if (selected == n) selected = null;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    /** Duplicate one block (with its inner blocks) right after itself. */
    public void duplicate(BlockModel.Node n) {
        if (n.isRoot()) return;
        listener.beforeChange();
        BlockModel.Node copy = n.copySingle();
        if (n.yes != null) copy.yes = n.yes.copyChain();
        if (n.no != null) copy.no = n.no.copyChain();
        copy.next = n.next;
        n.next = copy;
        selected = copy;
        dirty = true;
        invalidate();
        listener.afterChange();
    }

    public void changed() {
        dirty = true;
        invalidate();
    }
}
