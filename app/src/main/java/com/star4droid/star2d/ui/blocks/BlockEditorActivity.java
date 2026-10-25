package com.star4droid.star2d.ui.blocks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Native block editor for visual scripts (.visual + generated .java), replacing the LibGDX
 * node editor. Drag blocks from the palette, tap fields to edit, (!) for help.
 */
public class BlockEditorActivity extends AppCompatActivity implements BlockCanvas.Listener {

    private static String pendingFile, pendingHints;

    /** Open the editor for a .visual file. Always handled natively. */
    public static boolean open(Activity a, String file, String hints) {
        pendingFile = file;
        pendingHints = hints == null ? "" : hints;
        a.runOnUiThread(() -> a.startActivity(new Intent(a, BlockEditorActivity.class)));
        return true;
    }

    private File file;
    private String eventName;
    private boolean rtl;
    private BlockModel model;
    private BlockCanvas canvas;
    private final LinkedHashMap<String, ArrayList<String>> hints = new LinkedHashMap<>();
    private final ArrayList<String> variables = new ArrayList<>();
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private boolean changed = false;
    private ImageView undoBtn, redoBtn;
    private String currentCategory;
    private final ArrayList<BlockCatalog.Def> paletteItems = new ArrayList<>();
    private RecyclerView paletteList;
    private LinearLayout categoryRow, selectionBar;
    private EditText search;
    private boolean paletteOpen = true;

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
        file = new File(pendingFile);
        eventName = file.getName().replace(".visual", "");
        parseHints(pendingHints);
        loadVariables();
        BlockCatalog.load(this);
        model = BlockModel.load(file);
        canvas = new BlockCanvas(this, this, rtl);
        canvas.setModel(model, BlockInfo.label("First >", rtl) + " · " + eventName);
        if (!BlockCatalog.CATEGORIES.isEmpty()) currentCategory = BlockCatalog.CATEGORIES.keySet().iterator().next();
        build();
        canvas.post(canvas::home);
    }

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
        // engine functions (assets/java/hints.java)
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
        LinearLayout root = Ui.column(c);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        // top bar
        LinearLayout top = Ui.row(c);
        top.setBackgroundColor(0xFF181A1E);
        top.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("close", "Close"), v -> onBackPressed());
        back.setScaleX(rtl ? -1 : 1);
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
        top.addView(Ui.iconButton(c, R.drawable.ic_s2_code, Tr.t("ui_show_code", "Show code"), v ->
                Dialogs.longText(c, Tr.t("ui_generated_code", "Generated code"), model.exportCode())));
        top.addView(Ui.iconButton(c, R.drawable.ic_s2_blocks, Tr.t("ui_palette", "Blocks palette"), v -> togglePalette()));
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save(false));
        save.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        top.addView(save);
        root.addView(top, Ui.matchWrap());

        // body
        LinearLayout body = land ? Ui.row(c) : Ui.column(c);
        View palette = buildPalette(c, land);
        FrameLayout canvasBox = new FrameLayout(c);
        if (canvas.getParent() != null) ((ViewGroup) canvas.getParent()).removeView(canvas);
        canvasBox.addView(canvas, Ui.match());
        // zoom buttons + selection bar overlay
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
        zl.setMargins(0, Ui.dp(c, 8), Ui.dp(c, 8), 0);
        canvasBox.addView(zoom, zl);

        selectionBar = Ui.row(c);
        selectionBar.setBackground(Ui.rounded(0xF0202328, Ui.dp(c, 14), Ui.BORDER, 1));
        selectionBar.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4), Ui.dp(c, 2));
        selectionBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2);
        sl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sl.bottomMargin = Ui.dp(c, 12);
        canvasBox.addView(selectionBar, sl);

        if (land) {
            body.addView(palette, new LinearLayout.LayoutParams(Ui.dp(c, 270), ViewGroup.LayoutParams.MATCH_PARENT));
            body.addView(canvasBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } else {
            body.addView(canvasBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            body.addView(palette, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.36f)));
        }
        palette.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        refreshUndo();
    }

    private void togglePalette() {
        paletteOpen = !paletteOpen;
        View p = (View) paletteList.getParent();
        p.setVisibility(paletteOpen ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        build();
    }

    // =============================================================== palette

    private View buildPalette(Context c, boolean land) {
        LinearLayout p = Ui.column(c);
        p.setBackgroundColor(0xFF1C1F24);
        p.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), 0);
        search = Ui.input(c, "", Tr.t("ui_search_blocks", "Search blocks… (e.g. jump, sound)"));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { fillPalette(); }
        });
        p.addView(search, Ui.matchWrap());
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        categoryRow = Ui.row(c);
        categoryRow.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        hs.addView(categoryRow);
        p.addView(hs, Ui.matchWrap());
        for (String cat : BlockCatalog.CATEGORIES.keySet()) {
            TextView chip = Ui.text(c, BlockInfo.category(cat, rtl), 12.5f, Ui.TEXT);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
            chip.setTag(cat);
            chip.setOnClickListener(v -> {
                currentCategory = cat;
                search.setText("");
                fillPalette();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(c, 5));
            categoryRow.addView(chip, lp);
        }
        TextView hint = Ui.text(c, Tr.t("ui_palette_hint", "Tap to add · drag onto the canvas · (!) for help"), 11, Ui.TEXT_3);
        hint.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
        p.addView(hint);
        paletteList = new RecyclerView(c);
        paletteList.setLayoutManager(new LinearLayoutManager(c));
        paletteList.setAdapter(new PaletteAdapter());
        p.addView(paletteList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        fillPalette();
        return p;
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
        for (int i = 0; i < categoryRow.getChildCount(); i++) {
            TextView chip = (TextView) categoryRow.getChildAt(i);
            String cat = (String) chip.getTag();
            boolean sel = q.isEmpty() && cat.equals(currentCategory);
            int col = BlockCatalog.colorOf(cat);
            chip.setBackground(sel ? Ui.rounded(col, Ui.dp(this, 14)) : Ui.rounded(0x00000000, Ui.dp(this, 14), col, Ui.dp(this, 1.5f)));
        }
        if (paletteList != null && paletteList.getAdapter() != null) paletteList.getAdapter().notifyDataSetChanged();
    }

    private class PaletteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 2), Ui.dp(c, 3), Ui.dp(c, 2), Ui.dp(c, 3));
            TextView block = Ui.text(c, "", 13, 0xFFFFFFFF);
            block.setTypeface(Typeface.DEFAULT_BOLD);
            block.setPadding(Ui.dp(c, 12), Ui.dp(c, 9), Ui.dp(c, 12), Ui.dp(c, 9));
            block.setMaxLines(2);
            row.addView(block, Ui.weight(1));
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
            BlockCatalog.Def d = paletteItems.get(pos);
            LinearLayout row = (LinearLayout) h.itemView;
            TextView block = (TextView) row.getChildAt(0);
            TextView info = (TextView) row.getChildAt(1);
            StringBuilder sb = new StringBuilder(BlockInfo.label(d.title, rtl));
            for (String f : d.fields) sb.append("  [").append(BlockInfo.field(BlockModel.Field.labelOf(f), rtl)).append("]");
            if (d.isIf) sb.append("  ⤵");
            block.setText(sb.toString());
            int col = BlockCatalog.colorOf(d.category);
            block.setBackground(Ui.rounded(col, Ui.dp(row.getContext(), 8)));
            info.setOnClickListener(v -> showInfo(d.title, d));
            block.setOnClickListener(v -> addBlock(d));
            block.setOnTouchListener(new PaletteDrag(d));
        }

        @Override
        public int getItemCount() { return paletteItems.size(); }
    }

    /** Drag a palette block onto the canvas. */
    private class PaletteDrag implements View.OnTouchListener {
        final BlockCatalog.Def def;
        float downX, downY;
        boolean dragging;

        PaletteDrag(BlockCatalog.Def d) { def = d; }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            int[] loc = new int[2];
            canvas.getLocationOnScreen(loc);
            float cx = e.getRawX() - loc[0], cy = e.getRawY() - loc[1];
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging) {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        boolean land = Ui.isLandscape(BlockEditorActivity.this);
                        // start when the finger moves toward the canvas (sideways in landscape, up in portrait)
                        boolean toward = land ? Math.abs(dx) > ViewConfiguration.get(v.getContext()).getScaledTouchSlop() * 1.5f && Math.abs(dx) > Math.abs(dy)
                                : -dy > ViewConfiguration.get(v.getContext()).getScaledTouchSlop() * 1.5f && Math.abs(dy) > Math.abs(dx);
                        if (toward) {
                            dragging = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            v.cancelPendingInputEvents();
                            canvas.beginExternalDrag(def.create(), cx, cy);
                        }
                        return dragging;
                    }
                    canvas.externalMove(cx, cy);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        canvas.externalUp(cx, cy);
                        dragging = false;
                        return true;
                    }
                    return false;
            }
            return false;
        }
    }

    private void addBlock(BlockCatalog.Def d) {
        canvas.insert(d.create());
        Ui.toast(this, BlockInfo.label(d.title, rtl) + " ✓");
    }

    // =============================================================== canvas callbacks

    @Override
    public void onFieldTap(BlockModel.Node node, BlockModel.Field field) {
        editField(node, field, () -> {
            beforeChangeFinished();
            canvas.changed();
        });
    }

    private void beforeChangeFinished() {
        changed = true;
        refreshUndo();
    }

    @Override
    public void onInfo(BlockModel.Node node) {
        showInfo(node.title, BlockCatalog.find(node.title));
    }

    private void showInfo(String title, BlockCatalog.Def def) {
        String info = BlockInfo.info(title, rtl);
        StringBuilder sb = new StringBuilder();
        sb.append(info == null ? (rtl ? "بلوك مخصص." : "Custom block.") : info);
        if (def != null && def.fields.length > 0) {
            sb.append("\n\n").append(rtl ? "الحقول: " : "Fields: ");
            for (int i = 0; i < def.fields.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(BlockInfo.field(BlockModel.Field.labelOf(def.fields[i]), rtl));
            }
        }
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
            TextView ct = Ui.text(this, (rtl ? "الكود المولّد:\n" : "Generated code:\n") + code, 12, Ui.TEXT_3);
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
            return;
        }
        Context c = this;
        TextView name = Ui.text(c, BlockInfo.label(node.title, rtl), 13, Ui.TEXT);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        name.setMaxWidth(Ui.dp(c, 140));
        name.setMaxLines(1);
        selectionBar.addView(name);
        selectionBar.addView(Ui.iconButton(c, R.drawable.ic_s2_info, "Info", v -> onInfo(node)));
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
            if (!paletteOpen) togglePalette();
            search.requestFocus();
        }));
        selectionBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onLongPress(BlockModel.Node node, float screenX, float screenY) {
        onSelect(node);
        PopupMenu m = new PopupMenu(this, selectionBar);
        m.getMenu().add(0, 1, 0, "ⓘ " + (rtl ? "معلومات" : "Info"));
        if (!node.isRoot()) {
            m.getMenu().add(0, 2, 1, Tr.t("ui_duplicate", "Duplicate"));
            m.getMenu().add(0, 3, 2, Tr.t("delete", "Delete"));
        }
        m.getMenu().add(0, 4, 3, Tr.t("ui_show_code", "Show code"));
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: onInfo(node); break;
                case 2: canvas.duplicate(node); break;
                case 3: canvas.deleteSingle(node); onSelect(null); break;
                case 4: {
                    BlockModel tmp = new BlockModel();
                    BlockModel.Node r = BlockModel.newRoot();
                    BlockModel.Node copy = node.copySingle();
                    if (node.yes != null) copy.yes = node.yes.copyChain();
                    if (node.no != null) copy.no = node.no.copyChain();
                    r.next = node.isRoot() ? node.next : copy;
                    tmp.heads.add(r);
                    Dialogs.longText(this, BlockInfo.label(node.title, rtl), tmp.exportCode());
                    break;
                }
            }
            return true;
        });
        m.show();
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
            File tmp = File.createTempFile("blocks", ".visual", getCacheDir());
            BlockModel.write(tmp, json);
            model = BlockModel.load(tmp);
            tmp.delete();
            canvas.setModel(model, BlockInfo.label("First >", rtl) + " · " + eventName);
            onSelect(null);
        } catch (Exception e) {
            Ui.toast(this, e.toString());
        }
    }

    private void undo() {
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

    // =============================================================== field editor

    private interface Done { void run(); }

    private void editField(BlockModel.Node node, BlockModel.Field field, Done done) {
        if (field.block != null && !field.block.inner.isEmpty()) {
            editParam(field.block, done);
            return;
        }
        Context c = this;
        String type = field.type();
        String label = BlockInfo.field(field.label(), rtl);
        boolean code = field.label().equals("code") || node.title.equals("addSource") || node.title.equals("codeLine");
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 16), Ui.dp(c, 4), Ui.dp(c, 16), 0);

        if (field.block != null) {
            TextView bt = Ui.text(c, "ƒ " + field.block.text(), 14, 0xFF80DEEA);
            bt.setTypeface(Typeface.DEFAULT_BOLD);
            bt.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
            col.addView(bt);
        }
        EditText input = Ui.input(c, field.value, label);
        if (code) {
            input.setSingleLine(false);
            input.setMinLines(4);
            input.setTypeface(Typeface.MONOSPACE);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        col.addView(input, Ui.matchWrap());

        // suggestion chips
        LinkedHashMap<String, ArrayList<String>> sugg = suggestionsFor(type, field.label());
        LinearLayout chipsBox = Ui.column(c);
        for (Map.Entry<String, ArrayList<String>> e : sugg.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            TextView h = Ui.text(c, e.getKey(), 11.5f, Ui.TEXT_3);
            h.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 3));
            chipsBox.addView(h);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = Ui.row(c);
            for (String v : e.getValue()) {
                TextView ch = Ui.text(c, v, 13, Ui.TEXT);
                ch.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
                ch.setBackground(Ui.ripple(Ui.SURFACE_3, Ui.dp(c, 14)));
                ch.setOnClickListener(x -> {
                    String cur = input.getText().toString();
                    boolean append = v.startsWith(".") || v.endsWith("(") || code;
                    input.setText(append ? cur + v : v);
                    input.setSelection(input.getText().length());
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.setMarginEnd(Ui.dp(c, 6));
                row.addView(ch, lp);
            }
            hs.addView(row);
            chipsBox.addView(hs);
        }
        col.addView(chipsBox);
        TextView tip = Ui.text(c, rtl ? "النص يحتاج علامات تنصيص \"...\"، والأرقام والمتغيرات بدونها." : "Text needs quotes \"...\"; numbers and variables don't.", 11.5f, Ui.TEXT_3);
        tip.setPadding(0, Ui.dp(c, 10), 0, 0);
        if (!code && type.isEmpty()) col.addView(tip);

        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(label)
                .setView(sv)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .setNeutralButton(field.block != null ? (rtl ? "إزالة البلوك" : "Remove block") : (rtl ? "ƒ بلوك قيمة" : "ƒ Value block"), null)
                .create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nv = input.getText().toString();
                if (!nv.equals(field.value)) {
                    beforeChange();
                    field.value = nv;
                    done.run();
                }
                d.dismiss();
            });
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                if (field.block != null) {
                    beforeChange();
                    field.block = null;
                    done.run();
                    d.dismiss();
                } else {
                    d.dismiss();
                    pickReporter(r -> {
                        beforeChange();
                        field.block = new BlockModel.Param(r.template, r.display);
                        done.run();
                        if (!field.block.inner.isEmpty()) editParam(field.block, done);
                    });
                }
            });
        });
        d.show();
    }

    /** Edit the inner fields of a reporter block. */
    private void editParam(BlockModel.Param p, Done done) {
        String[] items = new String[p.inner.size() + 2];
        for (int i = 0; i < p.inner.size(); i++) {
            BlockModel.Field f = p.inner.get(i);
            items[i] = (i + 1) + ") " + (f.block != null ? "ƒ " + f.block.text() : (f.value.isEmpty() ? "…" : f.value));
        }
        items[p.inner.size()] = rtl ? "✓ تم" : "✓ Done";
        items[p.inner.size() + 1] = rtl ? "✕ إزالة البلوك" : "✕ Remove block";
        new AlertDialog.Builder(this)
                .setTitle("ƒ " + p.text())
                .setItems(items, (d, w) -> {
                    if (w < p.inner.size()) {
                        BlockModel.Field f = p.inner.get(w);
                        BlockModel.Node fake = new BlockModel.Node();
                        fake.title = "";
                        editField(fake, f, () -> {
                            done.run();
                            editParam(p, done);
                        });
                    } else if (w == p.inner.size() + 1) {
                        // remove: handled by caller field (find it)
                        removeParam(p);
                        done.run();
                    }
                })
                .show();
    }

    private void removeParam(BlockModel.Param p) {
        beforeChange();
        ArrayList<BlockModel.Node> all = new ArrayList<>();
        for (BlockModel.Node h : model.heads) collectAll(h, all);
        for (BlockModel.Node n : all) for (BlockModel.Field f : n.fields) if (clearParam(f, p)) return;
    }

    private boolean clearParam(BlockModel.Field f, BlockModel.Param p) {
        if (f.block == null) return false;
        if (f.block == p) { f.block = null; return true; }
        for (BlockModel.Field i : f.block.inner) if (clearParam(i, p)) return true;
        return false;
    }

    private static void collectAll(BlockModel.Node n, ArrayList<BlockModel.Node> out) {
        while (n != null) {
            out.add(n);
            collectAll(n.yes, out);
            collectAll(n.no, out);
            n = n.next;
        }
    }

    private interface OnReporter { void on(BlockCatalog.Reporter r); }

    private void pickReporter(OnReporter cb) {
        LinkedHashMap<String, ArrayList<BlockCatalog.Reporter>> groups = new LinkedHashMap<>();
        for (BlockCatalog.Reporter r : BlockCatalog.REPORTERS) {
            if (!groups.containsKey(r.group)) groups.put(r.group, new ArrayList<>());
            groups.get(r.group).add(r);
        }
        String[] names = groups.keySet().toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(rtl ? "بلوكات القيم" : "Value blocks")
                .setItems(names, (d, w) -> {
                    ArrayList<BlockCatalog.Reporter> l = groups.get(names[w]);
                    String[] items = new String[l.size()];
                    for (int i = 0; i < items.length; i++) items[i] = l.get(i).display;
                    new AlertDialog.Builder(this).setTitle(names[w]).setItems(items, (d2, w2) -> cb.on(l.get(w2))).show();
                })
                .show();
    }

    private LinkedHashMap<String, ArrayList<String>> suggestionsFor(String type, String label) {
        LinkedHashMap<String, ArrayList<String>> out = new LinkedHashMap<>();
        String t = type.toLowerCase(Locale.ROOT);
        String l = label.toLowerCase(Locale.ROOT);
        ArrayList<String> items = hints.containsKey("Items") ? hints.get("Items") : new ArrayList<>();
        if (t.equals("body") || l.equals("joystick") || l.equals("target")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("current");
            s.add("body1");
            s.add("body2");
            s.addAll(items);
            out.put(rtl ? "العناصر" : "Elements", s);
        } else if (t.equals("boolean")) {
            ArrayList<String> s = new ArrayList<>();
            s.add("true");
            s.add("false");
            out.put(rtl ? "قيم" : "Values", s);
            out.put(rtl ? "المتغيرات" : "Variables", variables);
        } else if (t.equals("image")) out.put(rtl ? "الصور" : "Images", hints.get("Images"));
        else if (t.equals("scene")) out.put(rtl ? "المشاهد" : "Scenes", hints.get("Scenes"));
        else if (t.equals("sound")) out.put(rtl ? "الأصوات" : "Sounds", hints.get("Sounds"));
        else if (t.equals("animation")) out.put(rtl ? "الحركات" : "Animations", hints.get("Animations"));
        else if (l.equals("joint")) out.put(rtl ? "الروابط" : "Joints", hints.get("Joints"));
        else if (l.equals("type") && !t.isEmpty()) { /* nothing */ } else {
            out.put(rtl ? "المتغيرات" : "Variables", variables);
            out.put(rtl ? "العناصر" : "Elements", items);
            for (Map.Entry<String, ArrayList<String>> e : hints.entrySet())
                if (e.getKey().startsWith("ƒ")) out.put(e.getKey(), e.getValue());
            if (l.equals("type")) {
                ArrayList<String> s = new ArrayList<>();
                for (String x : new String[]{"int", "float", "String", "boolean", "DynamicBody", "StaticBody", "KinematicBody"}) s.add(x);
                out.put(rtl ? "أنواع" : "Types", s);
            }
        }
        java.util.Iterator<Map.Entry<String, ArrayList<String>>> it = out.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() == null) it.remove();
        return out;
    }

    // =============================================================== save / exit

    private void save(boolean thenFinish) {
        try {
            model.save(file);
            changed = false;
            Ui.toast(this, Tr.t("saved", "Saved") + " ✓");
            if (thenFinish) finish();
        } catch (Exception e) {
            Dialogs.longText(this, "Save error", e.toString());
        }
    }

    @Override
    public void onBackPressed() {
        if (canvas != null && canvas.isDragging()) return;
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
