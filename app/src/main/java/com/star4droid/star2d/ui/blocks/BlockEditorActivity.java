package com.star4droid.star2d.ui.blocks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.IconMenu;
import com.star4droid.star2d.ui.Snack;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;
import com.star4droid.star2d.ui.tutorial.Tut;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native block editor for visual scripts (.visual + generated .java), replacing the LibGDX
 * node editor. Drag blocks from the palette (hold or swipe toward the canvas), tap a field to
 * type a value or drop value blocks into it, (!) for help. The palette can be resized and the
 * whole editor mirrored (right to left) per project.
 */
public class BlockEditorActivity extends AppCompatActivity implements BlockCanvas.Listener {

    private static String pendingFile, pendingHints;
    public static BlockEditorActivity current;

    /** Open the editor for a .visual file. Always handled natively. */
    public static boolean open(Activity a, String file, String hints) {
        pendingFile = file;
        pendingHints = hints == null ? "" : hints;
        a.runOnUiThread(() -> a.startActivity(new Intent(a, BlockEditorActivity.class)));
        return true;
    }

    private static final String VALUES_CAT = "ƒ values";

    private File file;
    private String eventName, projectKey;
    private boolean rtl, mirror;
    private BlockModel model;
    private BlockCanvas canvas;
    private final LinkedHashMap<String, ArrayList<String>> hints = new LinkedHashMap<>();
    private final ArrayList<String> variables = new ArrayList<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean changed = false;
    private ImageView undoBtn, redoBtn;
    private String currentCategory;
    private final ArrayList<Object> paletteItems = new ArrayList<>();
    private RecyclerView paletteList;
    private LinearLayout categoryRow, selectionBar, paletteBox, blocksPane, valuePane;
    private EditText search;
    private boolean paletteOpen = true;
    private FrameLayout rootFrame;
    private TextView ghost;
    private View handle;

    // value editing (field tapped)
    private BlockModel.Node editNode;
    private BlockModel.Field editField;
    private EditText valueInput;
    private String valueGroup;
    private final ArrayList<Object> valueItems = new ArrayList<>();
    private RecyclerView valueList;
    private LinearLayout valueGroups;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (EngineSettings.get() == null) EngineSettings.init(this);
        Tr.init(this);
        rtl = Ui.isRtl(this);
        if (pendingFile == null) {
            finish();
            return;
        }
        current = this;
        file = new File(pendingFile);
        eventName = file.getName().replace(".visual", "");
        String p = file.getAbsolutePath();
        int ev = p.indexOf("/Events/");
        projectKey = ev > 0 ? p.substring(0, ev) : file.getParent();
        mirror = Ui.prefs(this).getBoolean("blocks_mirror:" + projectKey, rtl);
        parseHints(pendingHints);
        loadVariables();
        BlockCatalog.load(this);
        model = BlockModel.load(file);
        canvas = new BlockCanvas(this, this, rtl);
        canvas.setMirror(mirror);
        canvas.setModel(model, BlockInfo.label("First >", rtl) + " · " + eventName);
        if (!BlockCatalog.CATEGORIES.isEmpty()) currentCategory = BlockCatalog.CATEGORIES.keySet().iterator().next();
        build();
        canvas.post(canvas::home);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    /** For tutorials. */
    public BlockCanvas canvas() { return canvas; }
    public BlockModel model() { return model; }

    // =============================================================== data

    private void parseHints(String all) {
        hints.clear();
        String section = null;
        for (String line : all.split("\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("- ")) {
                section = l.substring(2).trim();
                if (!hints.containsKey(section)) hints.put(section, new ArrayList<>());
            } else if (section != null) hints.get(section).add(l);
        }
        try (java.io.InputStream in = getAssets().open("java/hints.java")) {
            String txt = new String(BlockCatalog.readAll(in), "UTF-8");
            String sec = null;
            for (String line : txt.split("\n")) {
                String l = line.trim();
                if (l.isEmpty()) continue;
                if (l.startsWith("- ")) {
                    sec = "ƒ " + l.substring(2).trim();
                    hints.put(sec, new ArrayList<>());
                } else if (sec != null) hints.get(sec).add(l);
            }
        } catch (Exception ignored) {}
    }

    private void loadVariables() {
        variables.clear();
        String p = file.getAbsolutePath();
        int i = p.indexOf("/Events/");
        if (i < 0) return;
        String root = p.substring(0, i);
        String rest = p.substring(i + 8);
        String scene = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        File vf = new File(root + "/Events/" + scene + "/public/variables.json");
        try {
            if (vf.exists()) {
                ArrayList<VarModel> l = new Gson().fromJson(BlockModel.read(vf), new TypeToken<ArrayList<VarModel>>() {}.getType());
                if (l != null) for (VarModel m : l) variables.add(m.getName());
            }
        } catch (Exception ignored) {}
    }

    // =============================================================== layout

    private void build() {
        Context c = this;
        boolean land = Ui.isLandscape(c);
        rootFrame = new FrameLayout(c);
        rootFrame.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(c);
        root.setLayoutDirection(mirror ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        rootFrame.addView(root, Ui.match());

        // top bar
        LinearLayout top = Ui.row(c);
        top.setBackgroundColor(0xFF181A1E);
        top.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), v -> onBackPressed());
        back.setScaleX(mirror ? -1 : 1);
        Tut.tag(back, "blocks:back");
        top.addView(back);
        LinearLayout titles = Ui.column(c);
        titles.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        TextView t = Ui.text(c, Tr.t(eventName), 15, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setMaxLines(1);
        titles.addView(t);
        TextView sub = Ui.text(c, Tr.t("visualScripting", "Blocks"), 11.5f, Ui.TEXT_3);
        titles.addView(sub);
        top.addView(titles, Ui.weight(1));
        undoBtn = Ui.iconButton(c, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> undo());
        redoBtn = Ui.iconButton(c, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> redo());
        top.addView(undoBtn);
        top.addView(redoBtn);
        ImageView flip = Ui.iconButton(c, R.drawable.ic_s2_flip, Tr.t("blk_mirror", "Mirror blocks (right to left)"), v -> toggleMirror());
        flip.setImageTintList(ColorStateList.valueOf(mirror ? Ui.ACCENT : Ui.TEXT_2));
        top.addView(flip);
        top.addView(Ui.iconButton(c, R.drawable.ic_s2_code, Tr.t("ui_show_code", "Show code"), v ->
                Dialogs.longText(c, Tr.t("ui_generated_code", "Generated code"), model.exportCode())));
        ImageView pal = Ui.iconButton(c, R.drawable.ic_s2_blocks, Tr.t("ui_palette", "Blocks palette"), v -> togglePalette());
        Tut.tag(pal, "blocks:palette");
        top.addView(pal);
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save(false));
        save.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(save, "blocks:save");
        top.addView(save);
        root.addView(top, Ui.matchWrap());

        // body
        LinearLayout body = land ? Ui.row(c) : Ui.column(c);
        View palette = buildPalette(c);
        FrameLayout canvasBox = new FrameLayout(c);
        if (canvas.getParent() != null) ((ViewGroup) canvas.getParent()).removeView(canvas);
        canvasBox.addView(canvas, Ui.match());
        Tut.tag(canvas, "blocks:canvas");
        LinearLayout zoom = Ui.column(c);
        zoom.setBackground(Ui.rounded(0xCC202328, Ui.dp(c, 10), Ui.BORDER, 1));
        zoom.addView(Ui.iconButton(c, R.drawable.ic_s2_add, "Zoom in", v -> canvas.zoomBy(1.2f)));
        TextView minus = Ui.text(c, "—", 18, Ui.TEXT);
        minus.setGravity(Gravity.CENTER);
        int s = Ui.dp(c, Ui.buttonDp(c));
        minus.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        minus.setOnClickListener(v -> canvas.zoomBy(1 / 1.2f));
        zoom.addView(minus);
        zoom.addView(Ui.iconButton(c, R.drawable.ic_s2_home, Tr.t("ui_go_start", "Go to start"), v -> canvas.home()));
        FrameLayout.LayoutParams zl = new FrameLayout.LayoutParams(-2, -2);
        zl.gravity = Gravity.TOP | Gravity.END;
        zl.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), 0);
        canvasBox.addView(zoom, zl);

        selectionBar = Ui.row(c);
        selectionBar.setBackground(Ui.rounded(0xF0202328, Ui.dp(c, 14), Ui.BORDER, 1));
        selectionBar.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        selectionBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sl.bottomMargin = Ui.dp(c, 12);
        canvasBox.addView(selectionBar, sl);

        handle = buildHandle(c, land);
        if (land) {
            int w = Ui.prefs(c).getInt("blocks_pal_w", Ui.dp(c, 280));
            body.addView(palette, new LinearLayout.LayoutParams(clampW(w), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(handle, new LinearLayout.LayoutParams(Ui.dp(c, 14), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(canvasBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            int h = Ui.prefs(c).getInt("blocks_pal_h", (int) (getResources().getDisplayMetrics().heightPixels * 0.38f));
            body.addView(canvasBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            body.addView(handle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 16)));
            body.addView(palette, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, clampH(h)));
        }
        palette.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        handle.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ghost = Ui.text(c, "", 13, 0xFFFFFFFF);
        ghost.setTypeface(Typeface.DEFAULT_BOLD);
        ghost.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        ghost.setVisibility(View.GONE);
        ghost.setElevation(Ui.dp(c, 12));
        rootFrame.addView(ghost, new FrameLayout.LayoutParams(-2, -2));

        setContentView(rootFrame);
        refreshUndo();
        if (editField != null) showValuePane();
    }

    private int clampH(int h) {
        int sh = getResources().getDisplayMetrics().heightPixels;
        return Math.max(Ui.dp(this, 150), Math.min(h, (int) (sh * 0.75f)));
    }

    private int clampW(int w) {
        int sw = getResources().getDisplayMetrics().widthPixels;
        return Math.max(Ui.dp(this, 200), Math.min(w, (int) (sw * 0.7f)));
    }

    /** Grip between the canvas and the palette: drag to resize the palette. */
    @SuppressLint("ClickableViewAccessibility")
    private View buildHandle(Context c, boolean land) {
        FrameLayout h = new FrameLayout(c);
        h.setBackgroundColor(0xFF1C1F24);
        View grip = new View(c);
        grip.setBackground(Ui.rounded(0xFF5A626D, Ui.dp(c, 3)));
        FrameLayout.LayoutParams gl = land ? new FrameLayout.LayoutParams(Ui.dp(c, 4), Ui.dp(c, 40)) : new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4));
        gl.gravity = Gravity.CENTER;
        h.addView(grip, gl);
        h.setContentDescription(Tr.t("blk_resize", "Drag to resize the palette"));
        Tut.tag(h, "blocks:handle");
        final float[] start = new float[2];
        h.setOnTouchListener((v, e) -> {
            ViewGroup.LayoutParams lp = paletteBox.getLayoutParams();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = land ? e.getRawX() : e.getRawY();
                    start[1] = land ? lp.width : lp.height;
                    grip.setBackground(Ui.rounded(Ui.ACCENT, Ui.dp(c, 3)));
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (land) {
                        float dx = e.getRawX() - start[0];
                        if (mirror) dx = -dx;
                        lp.width = clampW((int) (start[1] + dx));
                    } else {
                        lp.height = clampH((int) (start[1] - (e.getRawY() - start[0])));
                    }
                    paletteBox.setLayoutParams(lp);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    grip.setBackground(Ui.rounded(0xFF5A626D, Ui.dp(c, 3)));
                    Ui.prefs(c).edit().putInt(land ? "blocks_pal_w" : "blocks_pal_h", land ? lp.width : lp.height).apply();
                    return true;
            }
            return true;
        });
        return h;
    }

    private void togglePalette() {
        paletteOpen = !paletteOpen;
        paletteBox.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        handle.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
    }

    private void toggleMirror() {
        commitValue();
        mirror = !mirror;
        Ui.prefs(this).edit().putBoolean("blocks_mirror:" + projectKey, mirror).apply();
        canvas.setMirror(mirror);
        build();
        Snack.show(mirror ? Tr.t("blk_mirror_on", "Blocks: right to left") : Tr.t("blk_mirror_off", "Blocks: left to right"));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        commitValue();
        build();
    }

    // =============================================================== palette

    private View buildPalette(Context c) {
        paletteBox = Ui.column(c);
        paletteBox.setBackgroundColor(0xFF1C1F24);
        blocksPane = Ui.column(c);
        blocksPane.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);
        search = Ui.input(c, "", Tr.t("ui_search_blocks", "Search blocks… (e.g. jump, sound)"));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { fillPalette(); }
        });
        blocksPane.addView(search, Ui.matchWrap());
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        categoryRow = Ui.row(c);
        categoryRow.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        hs.addView(categoryRow);
        blocksPane.addView(hs, Ui.matchWrap());
        ArrayList<String> cats = new ArrayList<>(BlockCatalog.CATEGORIES.keySet());
        cats.add(1 < cats.size() ? 1 : cats.size(), VALUES_CAT);
        for (String cat : cats) {
            String name = cat.equals(VALUES_CAT) ? "ƒ " + Tr.t("blk_values", "Values") : BlockInfo.category(cat, rtl);
            TextView chip = Ui.text(c, name, 12.5f, Ui.TEXT);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            chip.setTag(cat);
            Tut.tag(chip, "blocks:cat:" + cat);
            chip.setOnClickListener(v -> {
                currentCategory = cat;
                search.setText("");
                fillPalette();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 5));
            categoryRow.addView(chip, lp);
        }
        TextView hint = Ui.text(c, Tr.t("blk_palette_hint", "Tap to add · hold, then drag onto the canvas · (!) for help"), 11, Ui.TEXT_3);
        hint.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
        blocksPane.addView(hint);
        paletteList = new RecyclerView(c);
        paletteList.setLayoutManager(new LinearLayoutManager(c));
        paletteList.setAdapter(new PaletteAdapter(paletteItems));
        blocksPane.addView(paletteList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        paletteBox.addView(blocksPane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        valuePane = Ui.column(c);
        valuePane.setVisibility(View.GONE);
        paletteBox.addView(valuePane, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillPalette();
        return paletteBox;
    }

    private void fillPalette() {
        paletteItems.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ArrayList<BlockCatalog.Def>> e : BlockCatalog.CATEGORIES.entrySet()) {
            for (BlockCatalog.Def d : e.getValue()) {
                if (q.isEmpty()) {
                    if (e.getKey().equals(currentCategory)) paletteItems.add(d);
                } else {
                    String hay = (d.title + " " + BlockInfo.label(d.title, true) + " " + BlockInfo.label(d.title, false)).toLowerCase(Locale.ROOT);
                    if (hay.contains(q)) paletteItems.add(d);
                }
            }
        }
        if (VALUES_CAT.equals(currentCategory) || !q.isEmpty()) {
            String lastGroup = null;
            for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
                if (!q.isEmpty() && !(r.display + " " + r.group).toLowerCase(Locale.ROOT).contains(q)) continue;
                if (!r.group.equals(lastGroup)) {
                    paletteItems.add(r.group);
                    lastGroup = r.group;
                }
                paletteItems.add(r);
            }
        }
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            TextView chip = (TextView) categoryRow.getChildAt(i);
            String cat = (String) chip.getTag();
            boolean sel = q.isEmpty() && cat.equals(currentCategory);
            int col = cat.equals(VALUES_CAT) ? 0xFF00838F : BlockCatalog.colorOf(cat);
            chip.setBackground(sel ? Ui.rounded(col, Ui.dp(this, 14)) : Ui.rounded(0x00000000, Ui.dp(this, 14), col, Ui.dp(this, 1.5f)));
        }
        if (paletteList != null && paletteList.getAdapter() != null) paletteList.getAdapter().notifyDataSetChanged();
    }

    /** Value block background shapes (same as on the canvas). */
    static class ShapeBg extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final int shape;
        final float r;

        ShapeBg(int color, int shape, float r) {
            p.setColor(color);
            this.shape = shape;
            this.r = r;
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            float h = b.height();
            if (shape == BlockCanvas.SHAPE_HEX) {
                float k = h * 0.42f;
                Path path = new Path();
                path.moveTo(b.left, b.centerY());
                path.lineTo(b.left + k, b.top);
                path.lineTo(b.right - k, b.top);
                path.lineTo(b.right, b.centerY());
                path.lineTo(b.right - k, b.bottom);
                path.lineTo(b.left + k, b.bottom);
                path.close();
                c.drawPath(path, p);
            } else {
                float rr = shape == BlockCanvas.SHAPE_PILL ? h / 2 : r;
                c.drawRoundRect(b.left, b.top, b.right, b.bottom, rr, rr, p);
            }
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static int shapeOf(BlockCatalog.Reporter r) {
        return BlockCatalog.isBooleanTemplate(r.template) ? BlockCanvas.SHAPE_HEX
                : (BlockCatalog.isTextTemplate(r.template) ? BlockCanvas.SHAPE_RECT : BlockCanvas.SHAPE_PILL);
    }

    static String reporterText(BlockCatalog.Reporter r) {
        return r.display.replace("___", " ◯ ").replace("  ", " ").trim();
    }

    /** Palette rows: statement blocks (Def), value blocks (Reporter) and group headers (String). */
    private class PaletteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final ArrayList<Object> items;

        PaletteAdapter(ArrayList<Object> items) { this.items = items; }

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            return o instanceof String ? 2 : (o instanceof BlockCatalog.Reporter ? 1 : 0);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            if (viewType == 2) {
                TextView h = Ui.text(c, "", 11.5f, Ui.TEXT_3);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setPadding(Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 2));
                h.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(h) {};
            }
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 2), Ui.dp(c, 3), Ui.dp(c, 2), Ui.dp(c, 3));
            TextView block = Ui.text(c, "", 13, 0xFFFFFFFF);
            block.setTypeface(Typeface.DEFAULT_BOLD);
            block.setPadding(Ui.dp(c, viewType == 1 ? 16 : 12), Ui.dp(c, 9), Ui.dp(c, viewType == 1 ? 16 : 12), Ui.dp(c, 9));
            block.setMaxLines(2);
            if (viewType == 1) row.addView(block, new LinearLayout.LayoutParams(-2, -2));
            else row.addView(block, Ui.weight(1));
            if (viewType == 1) row.addView(new View(c), Ui.weight(1));
            TextView info = Ui.text(c, "!", 15, Ui.TEXT);
            info.setTypeface(Typeface.DEFAULT_BOLD);
            info.setGravity(Gravity.CENTER);
            info.setBackground(Ui.rounded(Ui.SURFACE_3, Ui.dp(c, 16)));
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32));
            il.setMarginStart(Ui.dp(c, 6));
            row.addView(info, il);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(row) {};
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            Object o = items.get(pos);
            Context c = h.itemView.getContext();
            if (o instanceof String) {
                ((TextView) h.itemView).setText(groupName((String) o));
                return;
            }
            LinearLayout row = (LinearLayout) h.itemView;
            TextView block = (TextView) row.getChildAt(0);
            TextView info = (TextView) row.getChildAt(row.getChildCount() - 1);
            if (o instanceof BlockCatalog.Reporter) {
                BlockCatalog.Reporter r = (BlockCatalog.Reporter) o;
                block.setText(reporterText(r));
                block.setBackground(new ShapeBg(BlockCatalog.groupColor(r.group), shapeOf(r), Ui.dp(c, 6)));
                info.setOnClickListener(v -> showReporterInfo(r));
                block.setOnClickListener(v -> putReporter(r));
                block.setOnTouchListener(new PaletteDrag(null, r));
                Tut.tag(block, "blocks:value:" + r.display);
                return;
            }
            BlockCatalog.Def d = (BlockCatalog.Def) o;
            StringBuilder sb = new StringBuilder(BlockInfo.label(d.title, rtl));
            for (String f : d.fields) sb.append("  [").append(BlockInfo.field(BlockModel.Field.labelOf(f), rtl)).append("]");
            if (d.isIf) sb.append("  ⤵");
            block.setText(sb.toString());
            int col = BlockCatalog.colorOf(d.category);
            block.setBackground(Ui.rounded(col, Ui.dp(c, 8)));
            info.setOnClickListener(v -> showInfo(d.title, d));
            block.setOnClickListener(v -> addBlock(d));
            block.setOnTouchListener(new PaletteDrag(d, null));
            Tut.tag(block, "blocks:def:" + d.title);
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private String groupName(String g) {
        return Tr.t("blk_group_" + g.toLowerCase(Locale.ROOT).replace(" / ", "_").replace(' ', '_'), g);
    }

    /**
     * Drag from the palette onto the canvas: hold the block briefly, or (side palette) swipe it
     * sideways toward the canvas. A normal vertical swipe still scrolls the list.
     */
    private class PaletteDrag implements View.OnTouchListener {
        final BlockCatalog.Def def;
        final BlockCatalog.Reporter rep;
        float downX, downY, lastX, lastY;
        boolean dragging, armed;
        View view;
        final Runnable hold = () -> {
            if (armed && !dragging && view != null) start(view);
        };

        PaletteDrag(BlockCatalog.Def d, BlockCatalog.Reporter r) {
            def = d;
            rep = r;
        }

        private float[] toCanvas(float rx, float ry) {
            int[] loc = new int[2];
            canvas.getLocationOnScreen(loc);
            return new float[]{rx - loc[0], ry - loc[1]};
        }

        private void start(View v) {
            dragging = true;
            armed = false;
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
            v.setPressed(false);
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            float[] cp = toCanvas(lastX, lastY);
            if (def != null) canvas.beginExternalDrag(def.create(), cp[0], cp[1]);
            else canvas.beginExternalParamDrag(new BlockModel.Param(rep.template, rep.display), cp[0], cp[1]);
            ghost.setText(((TextView) v).getText());
            ghost.setBackground(def != null ? Ui.rounded(BlockCatalog.colorOf(def.category), Ui.dp(v.getContext(), 8))
                    : new ShapeBg(BlockCatalog.groupColor(rep.group), shapeOf(rep), Ui.dp(v.getContext(), 6)));
            moveGhost();
        }

        private void moveGhost() {
            int[] cl = new int[2], rl = new int[2];
            canvas.getLocationOnScreen(cl);
            rootFrame.getLocationOnScreen(rl);
            boolean inCanvas = lastX >= cl[0] && lastX <= cl[0] + canvas.getWidth() && lastY >= cl[1] && lastY <= cl[1] + canvas.getHeight();
            ghost.setVisibility(inCanvas ? View.GONE : View.VISIBLE);
            ghost.setX(lastX - rl[0] - Ui.dp(ghost.getContext(), 20));
            ghost.setY(lastY - rl[1] - Ui.dp(ghost.getContext(), 50));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            lastX = e.getRawX();
            lastY = e.getRawY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view = v;
                    downX = lastX;
                    downY = lastY;
                    dragging = false;
                    armed = true;
                    handler.postDelayed(hold, 220);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) {
                        float dx = lastX - downX, dy = lastY - downY;
                        float slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                            armed = false;
                            handler.removeCallbacks(hold);
                            boolean land = Ui.isLandscape(BlockEditorActivity.this);
                            // side palette: a sideways swipe toward the canvas starts the drag at once
                            if (land && Math.abs(dx) > Math.abs(dy) * 1.3f) start(v);
                        }
                        return dragging;
                    }
                    float[] cp = toCanvas(lastX, lastY);
                    canvas.externalMove(cp[0], cp[1]);
                    moveGhost();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    armed = false;
                    handler.removeCallbacks(hold);
                    if (dragging) {
                        float[] up = toCanvas(lastX, lastY);
                        canvas.externalUp(up[0], up[1], e.getActionMasked() == MotionEvent.ACTION_CANCEL);
                        dragging = false;
                        ghost.setVisibility(View.GONE);
                        v.setPressed(false);
                        return true;
                    }
                    return false;
            }
            return dragging;
        }
    }

    private void addBlock(BlockCatalog.Def d) {
        canvas.insert(d.create());
        Snack.show(BlockInfo.label(d.title, rtl) + " ✓");
    }

    // =============================================================== value pane (a field is being edited)

    @Override
    public void onFieldTap(BlockModel.Node node, BlockModel.Field field) {
        commitValue();
        editNode = node;
        editField = field;
        canvas.setActiveField(field);
        onSelect(node);
        if (!paletteOpen) togglePalette();
        showValuePane();
    }

    private void closeValuePane() {
        commitValue();
        editField = null;
        editNode = null;
        canvas.setActiveField(null);
        hideKeyboard();
        if (valuePane != null) {
            valuePane.setVisibility(View.GONE);
            blocksPane.setVisibility(View.VISIBLE);
        }
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
        }
    }

    /** Apply the typed value to the field being edited (one undo step). */
    private void commitValue() {
        if (editField == null || valueInput == null) return;
        String nv = valueInput.getText().toString();
        if (nv.equals(editField.value)) return;
        beforeChange();
        editField.value = nv;
        if (!nv.isEmpty()) editField.block = null; // a typed value replaces the value block
        canvas.changed();
        afterChange();
    }

    private boolean isCodeField(BlockModel.Node node, BlockModel.Field f) {
        return f.label().equals("code") || (node != null && (node.title.equals("addSource") || node.title.equals("codeLine")));
    }

    private void showValuePane() {
        Context c = this;
        BlockModel.Field field = editField;
        if (field == null || valuePane == null) return;
        blocksPane.setVisibility(View.GONE);
        valuePane.setVisibility(View.VISIBLE);
        valuePane.removeAllViews();
        valuePane.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);
        boolean code = isCodeField(editNode, field);
        String label = field.name.equals("___") ? Tr.t("blk_value", "Value") : BlockInfo.field(field.label(), rtl);

        // header
        LinearLayout head = Ui.row(c);
        head.addView(Ui.smallIcon(c, R.drawable.ic_s2_keyboard, Ui.ACCENT, 18));
        TextView title = Ui.text(c, label + (field.type().isEmpty() ? "" : "  ·  " + field.type()), 13.5f, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        title.setMaxLines(1);
        head.addView(title, Ui.weight(1));
        ImageView close = Ui.iconButton(c, R.drawable.ic_s2_close, Tr.t("close", "Close"), v -> closeValuePane());
        Tut.tag(close, "blocks:value_close");
        head.addView(close);
        valuePane.addView(head, Ui.matchWrap());

        // typed value
        LinearLayout in = Ui.row(c);
        valueInput = Ui.input(c, field.value, field.block != null ? "ƒ " + field.block.text() : label);
        Tut.tag(valueInput, "blocks:value_input");
        if (code) {
            valueInput.setSingleLine(false);
            valueInput.setMinLines(2);
            valueInput.setMaxLines(5);
            valueInput.setTypeface(Typeface.MONOSPACE);
            valueInput.setGravity(Gravity.TOP | Gravity.START);
            valueInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        } else {
            valueInput.setSingleLine(true);
            valueInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
            valueInput.setOnEditorActionListener((v, a, ev) -> {
                commitValue();
                hideKeyboard();
                return true;
            });
        }
        in.addView(valueInput, Ui.weight(1));
        ImageView ok = Ui.iconButton(c, R.drawable.ic_s2_check, Tr.t("ok", "OK"), v -> {
            commitValue();
            hideKeyboard();
        });
        ok.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(ok, "blocks:value_ok");
        LinearLayout.LayoutParams okl = new LinearLayout.LayoutParams(-2, -2);
        okl.setMarginStart(Ui.dp(c, 6));
        in.addView(ok, okl);
        valuePane.addView(in, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 0));

        if (field.block != null) {
            LinearLayout br = Ui.row(c);
            TextView bt = Ui.text(c, "ƒ " + field.block.text(), 12.5f, 0xFF80DEEA);
            bt.setMaxLines(1);
            br.addView(bt, Ui.weight(1));
            TextView rm = Ui.text(c, Tr.t("blk_remove_value", "Remove value block"), 12.5f, 0xFFFF8A80);
            rm.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            rm.setBackground(Ui.ripple(0, Ui.dp(c, 12)));
            rm.setOnClickListener(v -> {
                beforeChange();
                editField.block = null;
                canvas.changed();
                afterChange();
                showValuePane();
            });
            br.addView(rm);
            valuePane.addView(br, Ui.margins(Ui.matchWrap(), c, 2, 4, 0, 0));
        }

        // quick values (elements, variables, images…)
        LinkedHashMap<String, ArrayList<String>> sugg = suggestionsFor(field.type(), field.label());
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(c);
        chips.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 2));
        for (Map.Entry<String, ArrayList<String>> e : sugg.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            TextView g = Ui.text(c, e.getKey() + ":", 11, Ui.TEXT_3);
            g.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
            chips.addView(g);
            for (String v : e.getValue()) {
                TextView ch = Ui.text(c, v, 12.5f, Ui.TEXT);
                ch.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
                ch.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 14)));
                ch.setOnClickListener(x -> {
                    String cur = valueInput.getText().toString();
                    boolean append = v.startsWith(".") || v.endsWith("(") || code;
                    valueInput.setText(append ? cur + v : v);
                    valueInput.setSelection(valueInput.getText().length());
                    if (!append) commitValue();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginEnd(Ui.dp(c, 5));
                chips.addView(ch, lp);
            }
        }
        hs.addView(chips);
        if (chips.getChildCount() > 0) valuePane.addView(hs, Ui.matchWrap());

        // value blocks: tap = put in this field, hold + drag = drop onto any field
        TextView vh = Ui.text(c, Tr.t("blk_values_hint", "Value blocks: tap to put it here, or hold and drag it onto any field"), 11, Ui.TEXT_3);
        vh.setPadding(Ui.dp(c, 4), Ui.dp(c, 6), 0, Ui.dp(c, 2));
        valuePane.addView(vh);
        HorizontalScrollView gs = new HorizontalScrollView(c);
        gs.setHorizontalScrollBarEnabled(false);
        valueGroups = Ui.row(c);
        valueGroups.setPadding(0, Ui.dp(c, 3), 0, Ui.dp(c, 3));
        ArrayList<String> groups = new ArrayList<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (!groups.contains(r.group)) groups.add(r.group);
        if (valueGroup == null || !groups.contains(valueGroup)) valueGroup = preferredGroup(field, groups);
        for (String g : groups) {
            TextView chip = Ui.text(c, groupName(g), 12, Ui.TEXT);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
            chip.setTag(g);
            chip.setOnClickListener(v -> {
                valueGroup = g;
                fillValues();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 5));
            valueGroups.addView(chip, lp);
        }
        gs.addView(valueGroups);
        valuePane.addView(gs, Ui.matchWrap());
        valueList = new RecyclerView(c);
        valueList.setLayoutManager(new LinearLayoutManager(c));
        valueList.setAdapter(new PaletteAdapter(valueItems));
        valuePane.addView(valueList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillValues();
    }

    private String preferredGroup(BlockModel.Field f, ArrayList<String> groups) {
        String t = f.type().toLowerCase(Locale.ROOT);
        String want = t.equals("boolean") ? "Logic" : (t.equals("body") ? "Elements" : "Operators");
        if (groups.contains(want)) return want;
        return groups.isEmpty() ? null : groups.get(0);
    }

    private void fillValues() {
        valueItems.clear();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) if (r.group.equals(valueGroup)) valueItems.add(r);
        for (int i = 0; i < valueGroups.getChildCount(); i++) {
            TextView chip = (TextView) valueGroups.getChildAt(i);
            String g = (String) chip.getTag();
            int col = BlockCatalog.groupColor(g);
            chip.setBackground(g.equals(valueGroup) ? Ui.rounded(col, Ui.dp(this, 14)) : Ui.rounded(0, Ui.dp(this, 14), col, Ui.dp(this, 1.5f)));
        }
        if (valueList.getAdapter() != null) valueList.getAdapter().notifyDataSetChanged();
    }

    /** Tap on a value block: put it into the field being edited (or tell how to use it). */
    private void putReporter(BlockCatalog.Reporter r) {
        if (editField == null) {
            Snack.show(Tr.t("blk_tap_field_first", "Tap a field in a block first, or hold and drag this value onto a field."));
            return;
        }
        beforeChange();
        BlockModel.Param p = new BlockModel.Param(r.template, r.display);
        editField.block = p;
        editField.value = "";
        canvas.changed();
        afterChange();
        // continue with the first empty inner field
        if (!p.inner.isEmpty()) {
            editField = p.inner.get(0);
            canvas.setActiveField(editField);
        }
        showValuePane();
    }

    private void showReporterInfo(BlockCatalog.Reporter r) {
        TextView t = Ui.text(this, Tr.t("blk_value_info", "A value block: it gives a value to the field it is dropped in. Empty circles are inner fields you can fill or nest more value blocks into.")
                + "\n\n" + Tr.t("ui_generated_code", "Generated code") + ":\n" + r.template, 13.5f, Ui.TEXT);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        t.setTextIsSelectable(true);
        new AlertDialog.Builder(this).setTitle("ƒ " + reporterText(r)).setView(t)
                .setPositiveButton(Tr.t("ok", "OK"), null).show();
    }

    // =============================================================== canvas callbacks

    @Override
    public void onInfo(BlockModel.Node node) {
        showInfo(node.title, BlockCatalog.find(node.title));
    }

    private void showInfo(String title, BlockCatalog.Def def) {
        String info = BlockInfo.info(title, rtl);
        StringBuilder sb = new StringBuilder();
        sb.append(info == null ? Tr.t("blk_custom", "Custom block.") : info);
        if (def != null && def.fields.length > 0) {
            sb.append("\n\n").append(Tr.t("blk_fields", "Fields")).append(": ");
            for (int i = 0; i < def.fields.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(BlockInfo.field(BlockModel.Field.labelOf(def.fields[i]), rtl));
            }
        }
        if (def != null && def.isIf) sb.append("\n\n").append(Tr.t("blk_c_block", "Put blocks inside its opening. Blocks under it run after it."));
        String code = def != null ? def.code : null;
        if (code == null) {
            BlockModel.Node sel = canvas.selected();
            if (sel != null && sel.title.equals(title)) code = sel.code;
        }
        TextView t = Ui.text(this, sb.toString(), 14, Ui.TEXT);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), 0);
        LinearLayout box = Ui.column(this);
        box.addView(t);
        if (code != null) {
            TextView ct = Ui.text(this, Tr.t("ui_generated_code", "Generated code") + ":\n" + code, 12, Ui.TEXT_3);
            ct.setTypeface(Typeface.MONOSPACE);
            ct.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), 0);
            ct.setTextIsSelectable(true);
            box.addView(ct);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog.Builder b = new AlertDialog.Builder(this).setTitle("ⓘ  " + BlockInfo.label(title, rtl)).setView(sv)
                .setPositiveButton(Tr.t("ok", "OK"), null);
        if (def != null) b.setNeutralButton(Tr.t("ui_add", "Add"), (d, w) -> addBlock(def));
        b.show();
    }

    @Override
    public void onSelect(BlockModel.Node node) {
        selectionBar.removeAllViews();
        if (node == null) {
            selectionBar.setVisibility(View.GONE);
            if (editField != null) closeValuePane();
            return;
        }
        Context c = this;
        TextView name = Ui.text(c, node.isRoot() ? BlockInfo.label("First >", rtl) : BlockInfo.label(node.title, rtl), 13, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        name.setMaxWidth(Ui.dp(c, 140));
        name.setMaxLines(1);
        selectionBar.addView(name);
        selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_info, Tr.t("blk_info", "Info"), v -> onInfo(node)));
        if (!node.isRoot()) {
            selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), v -> canvas.duplicate(node)));
            ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> {
                canvas.deleteSingle(node);
                onSelect(null);
            });
            del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
            selectionBar.addView(del);
        }
        selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_after", "Add after"), v -> {
            closeValuePane();
            if (!paletteOpen) togglePalette();
        }));
        selectionBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLongPress(BlockModel.Node node, float rawX, float rawY) {
        onSelect(node);
        IconMenu m = new IconMenu(this);
        m.add(R.drawable.ic_s2_info, Ui.ACCENT, Tr.t("blk_info", "Info"), () -> onInfo(node));
        if (!node.isRoot()) {
            m.add(R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), () -> canvas.duplicate(node));
            m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("delete", "Delete"), () -> {
                canvas.deleteSingle(node);
                onSelect(null);
            });
        }
        m.add(R.drawable.ic_s2_code, 0xFFAB47BC, Tr.t("ui_show_code", "Show code"), () -> {
            BlockModel tmp = new BlockModel();
            BlockModel.Node r = BlockModel.newRoot();
            BlockModel.Node copy = node.copySingle();
            if (node.yes != null) copy.yes = node.yes.copyChain();
            if (node.no != null) copy.no = node.no.copyChain();
            r.next = node.isRoot() ? node.next : copy;
            tmp.heads.add(r);
            Dialogs.longText(this, BlockInfo.label(node.title, rtl), tmp.exportCode());
        });
        m.showAt(rootFrame, rawX, rawY);
    }

    @Override
    public void onDropHint() {
        Snack.show(Tr.t("blk_drop_hint", "Drop value blocks onto a field (the white slots in a block)."));
    }

    @Override
    public void beforeChange() {
        undo.push(model.toJson());
        if (undo.size() > 60) undo.removeLast();
        redo.clear();
    }

    @Override
    public void afterChange() {
        changed = true;
        refreshUndo();
    }

    private void refreshUndo() {
        if (undoBtn == null) return;
        undoBtn.setAlpha(undo.isEmpty() ? 0.35f : 1f);
        redoBtn.setAlpha(redo.isEmpty() ? 0.35f : 1f);
    }

    private void restore(String json) {
        try {
            editField = null;
            editNode = null;
            if (valuePane != null) {
                valuePane.setVisibility(View.GONE);
                blocksPane.setVisibility(View.VISIBLE);
            }
            File tmp = File.createTempFile("blocks", ".visual", getCacheDir());
            BlockModel.write(tmp, json);
            BlockModel m = BlockModel.load(tmp);
            tmp.delete();
            // keep positions as they are on screen (load() normalizes file coordinates)
            model = m;
            canvas.setModel(model, BlockInfo.label("First >", rtl) + " · " + eventName);
            onSelect(null);
        } catch (Exception e) {
            Snack.show(e.toString());
        }
    }

    private void undo() {
        commitValue();
        if (undo.isEmpty()) return;
        redo.push(model.toJson());
        restore(undo.pop());
        changed = true;
        refreshUndo();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(model.toJson());
        restore(redo.pop());
        changed = true;
        refreshUndo();
    }

    private LinkedHashMap<String, ArrayList<String>> suggestionsFor(String type, String label) {
        LinkedHashMap<String, ArrayList<String>> out = new LinkedHashMap<>();
        String t = type.toLowerCase(Locale.ROOT);
        String l = label.toLowerCase(Locale.ROOT);
        ArrayList<String> items = hints.containsKey("Items") ? hints.get("Items") : new ArrayList<>();
        String elements = Tr.t("blk_elements", "Elements"), vars = Tr.t("blk_variables", "Variables");
        if (t.equals("body") || l.equals("joystick") || l.equals("target") || l.equals("ui")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("current");
            s.add("body1");
            s.add("body2");
            s.addAll(items);
            out.put(elements, s);
        } else if (t.equals("boolean")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("true");
            s.add("false");
            out.put(Tr.t("blk_values", "Values"), s);
            out.put(vars, variables);
        } else if (t.equals("image")) out.put(Tr.t("blk_images", "Images"), hints.get("Images"));
        else if (t.equals("scene")) out.put(Tr.t("scenes", "Scenes"), hints.get("Scenes"));
        else if (t.equals("sound")) out.put(Tr.t("blk_sounds", "Sounds"), hints.get("Sounds"));
        else if (t.equals("animation")) out.put(Tr.t("blk_animations", "Animations"), hints.get("Animations"));
        else if (l.equals("joint")) out.put(Tr.t("blk_joints", "Joints"), hints.get("Joints"));
        else if (l.equals("type") && !t.isEmpty()) { /* nothing */ } else {
            out.put(vars, variables);
            out.put(elements, items);
            for (Map.Entry<String, ArrayList<String>> e : hints.entrySet())
                if (e.getKey().startsWith("ƒ")) out.put(e.getKey(), e.getValue());
            if (l.equals("type")) {
                ArrayList<String> s = new ArrayList<>();
                for (String x : new String[]{"int", "float", "String", "boolean", "DynamicBody", "StaticBody", "KinematicBody"}) s.add(x);
                out.put(Tr.t("blk_types", "Types"), s);
            }
        }
        java.util.Iterator<Map.Entry<String, ArrayList<String>>> it = out.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() == null) it.remove();
        return out;
    }

    // =============================================================== save / exit

    private void save(boolean thenFinish) {
        commitValue();
        try {
            model.save(file);
            changed = false;
            Snack.show(Tr.t("saved", "Saved") + " ✓");
            if (thenFinish) finish();
        } catch (Exception e) {
            Dialogs.longText(this, "Save error", e.toString());
        }
    }

    /** Used by tutorials to know whether the script was saved. */
    public boolean hasUnsavedChanges() { return changed; }

    @Override
    public void onBackPressed() {
        if (canvas != null && canvas.isDragging()) return;
        if (editField != null) {
            closeValuePane();
            return;
        }
        if (!changed) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(Tr.t("ui_unsaved", "Unsaved changes"))
                .setMessage(Tr.t("ui_save_before_exit", "Save your blocks before closing?"))
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> save(true))
                .setNegativeButton(Tr.t("ui_discard", "Discard"), (d, w) -> finish())
                .setNeutralButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }
}
