package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.sub.PropertyEditing;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.ColorPickerDialog;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Properties of the selected element, grouped in collapsible sections (same schema files as the
 * LibGDX properties window: map.json, types.json, spinners.json). Every change goes through
 * {@link PropertyEditing} so undo/redo and item refresh behave exactly as before.
 */
public class PropertiesPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout content;
    private TextView header;

    private static LinkedHashMap<String, String[]> sections;
    private static HashMap<String, String> typeOf;
    private static HashMap<String, String[]> spinners;

    private String selectedName = null;
    private LinkedHashMap<String, String> values = new LinkedHashMap<>();
    private final HashMap<String, View> editors = new HashMap<>();
    private final HashSet<String> collapsed = new HashSet<>();
    private String fieldsSignature = "";
    private final Runnable poll = this::pollValues;

    public PropertiesPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("properties", "Properties"); }
    @Override public int icon() { return R.drawable.ic_s2_properties; }

    private static void loadSchema(Context c) {
        if (sections != null) return;
        sections = new LinkedHashMap<>();
        typeOf = new HashMap<>();
        spinners = new HashMap<>();
        try {
            Map<String, String> map = readJson(c, "map.json");
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (e.getKey().equals("static")) continue;
                ArrayList<String> fields = new ArrayList<>();
                for (String f : e.getValue().split(",")) if (!f.trim().isEmpty()) fields.add(f.trim());
                sections.put(e.getKey(), fields.toArray(new String[0]));
            }
            Map<String, String> types = readJson(c, "types.json");
            for (Map.Entry<String, String> e : types.entrySet())
                for (String f : e.getValue().split(",")) if (!f.trim().isEmpty()) typeOf.put(f.trim(), e.getKey());
            Map<String, String> sp = readJson(c, "spinners.json");
            for (Map.Entry<String, String> e : sp.entrySet()) spinners.put(e.getKey(), e.getValue().split(","));
        } catch (Exception e) {
            Ui.toast(c, "schema: " + e);
        }
    }

    private static Map<String, String> readJson(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open(name)) {
            byte[] b = new byte[in.available()];
            int r = in.read(b);
            String s = new String(b, 0, r, "UTF-8");
            return new Gson().fromJson(s, new TypeToken<LinkedHashMap<String, String>>() {}.getType());
        }
    }

    @Override
    public View view(Context c) {
        loadSchema(c);
        if (root != null) return root;
        root = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 20));
        header = Ui.text(c, "", 13, Ui.TEXT_3);
        header.setPadding(Ui.dp(c, 4), Ui.dp(c, 2), 0, Ui.dp(c, 4));
        col.addView(header);
        content = Ui.column(c);
        col.addView(content, Ui.matchWrap());
        root.addView(col);
        return root;
    }

    @Override public void onShown() {
        onSelectionChanged();
        root.removeCallbacks(poll);
        root.postDelayed(poll, 500);
    }

    @Override public void onHidden() {
        if (root != null) root.removeCallbacks(poll);
    }

    public void onSelectionChanged() {
        if (!shell.panels.isShowing(this) || !Bridge.ready()) return;
        Bridge.query(this::snapshot, this::render);
    }

    /** While visible, refresh values (e.g. after dragging on the canvas). */
    private void pollValues() {
        if (root == null || !shell.panels.isShowing(this)) return;
        if (Bridge.ready()) Bridge.query(this::snapshot, snap -> {
            if (snap == null || selectedName == null || fieldsSignature.isEmpty() || !selectedName.equals(snap[0])) render(snap);
            else updateValues((LinkedHashMap<String, String>) snap[1]);
        });
        root.postDelayed(poll, 450);
    }

    /** [name, values, type] or null when nothing is selected. GL thread. */
    private Object[] snapshot() {
        Actor a = Bridge.editor().getSelectedActor();
        if (a == null) return null;
        PropertySet<String, Object> ps = PropertySet.getPropertySet(a);
        String type = "";
        try { type = ((com.star4droid.star2d.editor.items.EditorItem) a).getTypeName(); } catch (Throwable ignored) {}
        return new Object[]{a.getName(), Bridge.snapshot(ps), type};
    }

    private void render(Object[] snap) {
        if (content == null) return;
        Context c = content.getContext();
        if (snap == null) {
            selectedName = null;
            fieldsSignature = "";
            content.removeAllViews();
            editors.clear();
            header.setText("");
            TextView t = Ui.text(c, Tr.t("ui_select_hint", "Select an element on the canvas or in the Elements list to edit its properties."), 14, Ui.TEXT_3);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(c, 10), Ui.dp(c, 30), Ui.dp(c, 10), 0);
            content.addView(t);
            return;
        }
        String name = (String) snap[0];
        LinkedHashMap<String, String> v = (LinkedHashMap<String, String>) snap[1];
        String sig = name + "|" + android.text.TextUtils.join(",", v.keySet());
        if (sig.equals(fieldsSignature)) {
            updateValues(v);
            return;
        }
        fieldsSignature = sig;
        selectedName = name;
        values = v;
        editors.clear();
        content.removeAllViews();
        String type = (String) snap[2];
        header.setText(Tr.t("ui_type_" + (type == null ? "" : type.toLowerCase()), type) + " · " + name);

        for (Map.Entry<String, String[]> sec : sections.entrySet()) {
            ArrayList<String> present = new ArrayList<>();
            for (String f : sec.getValue()) if (v.containsKey(f)) present.add(f);
            if (present.isEmpty()) continue;
            addSection(c, sec.getKey(), present);
        }
        // fields not covered by the schema (keep them editable as text)
        ArrayList<String> extra = new ArrayList<>();
        HashSet<String> known = new HashSet<>();
        for (String[] fs : sections.values()) for (String f : fs) known.add(f);
        for (String k : v.keySet())
            if (!known.contains(k) && typeOf.containsKey(k) && !"static".equals(typeOf.get(k))) extra.add(k);
        if (!extra.isEmpty()) addSection(c, "Other", extra);
    }

    private void addSection(Context c, String title, ArrayList<String> fields) {
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 10), Ui.BORDER, 1));
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 4);
        content.addView(card, lp);

        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
        head.setBackground(Ui.ripple(0, Ui.dp(c, 10)));
        TextView t = Ui.text(c, Tr.t(title), 14, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(t, Ui.weight(1));
        ImageView arrow = Ui.smallIcon(c, collapsed.contains(title) ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more, Ui.TEXT_2, 20);
        head.addView(arrow);
        card.addView(head);

        LinearLayout body = Ui.column(c);
        body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), Ui.dp(c, 8));
        body.setVisibility(collapsed.contains(title) ? View.GONE : View.VISIBLE);
        card.addView(body);
        head.setOnClickListener(x -> {
            boolean col = body.getVisibility() == View.VISIBLE;
            body.setVisibility(col ? View.GONE : View.VISIBLE);
            arrow.setImageResource(col ? R.drawable.ic_s2_chevron_right : R.drawable.ic_s2_expand_more);
            if (col) collapsed.add(title); else collapsed.remove(title);
        });

        // pair x/y style floats on one row
        for (int i = 0; i < fields.size(); i++) {
            String f = fields.get(i);
            String type = typeOf.get(f);
            if ("float".equals(type) && i + 1 < fields.size() && "float".equals(typeOf.get(fields.get(i + 1))) && isPair(f, fields.get(i + 1))) {
                LinearLayout pair = Ui.row(c);
                pair.addView(field(c, f, type), Ui.weight(1));
                View sp = new View(c);
                pair.addView(sp, new LinearLayout.LayoutParams(Ui.dp(c, 10), 1));
                pair.addView(field(c, fields.get(i + 1), type), Ui.weight(1));
                body.addView(pair, Ui.matchWrap());
                i++;
            } else body.addView(field(c, f, type), Ui.matchWrap());
        }
    }

    private boolean isPair(String a, String b) {
        String[][] pairs = {{"x", "y"}, {"width", "height"}, {"Scale X", "Scale Y"}, {"Offset X", "Offset Y"}, {"ColliderX", "ColliderY"},
                {"Collider Width", "Collider Height"}, {"tileX", "tileY"}, {"friction", "restitution"}, {"Progress", "Max"}};
        for (String[] p : pairs) if (p[0].equals(a) && p[1].equals(b)) return true;
        return false;
    }

    private View label(Context c, String f) {
        TextView l = Ui.text(c, Tr.t(f), 12.5f, Ui.TEXT_2);
        l.setPadding(Ui.dp(c, 2), Ui.dp(c, 8), 0, Ui.dp(c, 4));
        String tip = PropertyTips.tip(f, Ui.isRtl(c));
        if (tip != null) {
            l.setText(Tr.t(f) + "  ⓘ");
            l.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle(Tr.t(f)).setMessage(tip).setPositiveButton(Tr.t("ok", "OK"), null).show());
        }
        return l;
    }

    private View field(Context c, String f, String type) {
        View v = fieldIn(c, f, type);
        com.star4droid.star2d.ui.tutorial.Tut.tag(v, "prop:" + f);
        View ed = editors.get(f);
        if (ed != null && ed != v) com.star4droid.star2d.ui.tutorial.Tut.tag(ed, "propedit:" + f);
        return v;
    }

    private View fieldIn(Context c, String f, String type) {
        LinearLayout box = Ui.column(c);
        if (type == null) type = "string";
        String val = values.get(f);
        if (val == null) val = "";
        switch (type) {
            case "boolean": {
                SwitchCompat s = new SwitchCompat(c);
                s.setText(Tr.t(f));
                s.setTextColor(Ui.TEXT);
                s.setTextSize(14);
                s.setChecked("true".equals(val));
                s.setMinHeight(Ui.dp(c, 44));
                s.setOnCheckedChangeListener((b, on) -> {
                    if (on == "true".equals(values.get(f))) return;
                    values.put(f, String.valueOf(on));
                    commit(() -> PropertyEditing.applyBoolean(Bridge.app(), f, String.valueOf(on)));
                });
                editors.put(f, s);
                String tip = PropertyTips.tip(f, Ui.isRtl(c));
                if (tip != null) s.setOnLongClickListener(v -> { Ui.toast(c, tip); return true; });
                return s;
            }
            case "float":
            case "string": {
                box.addView(label(c, f));
                EditText e = Ui.input(c, display(type, val), "");
                if (type.equals("float"))
                    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                else if (f.equals("Text")) {
                    e.setSingleLine(false);
                    e.setMaxLines(4);
                    e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                }
                e.setImeOptions(EditorInfo.IME_ACTION_DONE);
                final String ft = type;
                Runnable apply = () -> {
                    String nv = e.getText().toString();
                    String old = values.get(f);
                    if (nv.equals(display(ft, old == null ? "" : old))) return;
                    if (ft.equals("float")) {
                        float fl;
                        try { fl = Float.parseFloat(nv.trim()); } catch (Exception ex) {
                            e.setError(Tr.t("ui_number", "Enter a number"));
                            return;
                        }
                        values.put(f, String.valueOf(fl));
                        commit(() -> PropertyEditing.applyFloat(Bridge.app(), f, fl));
                    } else {
                        values.put(f, nv);
                        Bridge.query(() -> PropertyEditing.applyString(Bridge.app(), f, nv), err -> {
                            if (err != null) {
                                Ui.toast(c, err);
                                values.put(f, old);
                                e.setText(old);
                            } else if (f.equals("name")) {
                                selectedName = nv;
                                shell.elements.refresh();
                            }
                        });
                    }
                };
                e.setOnEditorActionListener((tv, id, ev) -> { apply.run(); e.clearFocus(); return false; });
                e.setOnFocusChangeListener((v, has) -> { if (!has) apply.run(); });
                box.addView(e, Ui.matchWrap());
                editors.put(f, e);
                return box;
            }
            case "spinner": {
                box.addView(label(c, f));
                TextView t = chooser(c, val);
                t.setOnClickListener(v -> {
                    String[] opts = spinners.get(f);
                    if (opts == null) return;
                    com.star4droid.star2d.ui.IconMenu m = new com.star4droid.star2d.ui.IconMenu(c);
                    for (String o : opts) {
                        boolean cur = o.equals(values.get(f));
                        m.add(cur ? R.drawable.ic_s2_check : 0, Ui.ACCENT, Tr.t(o, o), () -> {
                            String nv = o;
                            if (nv.equals(values.get(f))) return;
                            values.put(f, nv);
                            t.setText(Tr.t(nv, nv));
                            commit(() -> PropertyEditing.applySpinner(Bridge.app(), f, nv));
                            if (f.equals("Shape") || f.equals("Light Type")) {
                                fieldsSignature = "";
                                t.postDelayed(this::onSelectionChanged, 250);
                            }
                        }).tagLast("menu:" + o);
                    }
                    m.show(t);
                });
                t.setText(Tr.t(val, val));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "color": {
                box.addView(label(c, f));
                LinearLayout r = Ui.row(c);
                View sw = new View(c);
                sw.setBackground(Ui.rounded(ColorPickerDialog.parse(val), Ui.dp(c, 6), Ui.BORDER, 1));
                r.addView(sw, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 30)));
                TextView t = Ui.text(c, val, 14, Ui.TEXT);
                t.setTypeface(Typeface.MONOSPACE);
                t.setPadding(Ui.dp(c, 10), 0, 0, 0);
                r.addView(t, Ui.weight(1));
                r.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                r.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
                r.setOnClickListener(v -> ColorPickerDialog.show(c, Tr.t(f), values.get(f), null, hex -> {
                    if (hex == null) return;
                    values.put(f, hex);
                    t.setText(hex);
                    sw.setBackground(Ui.rounded(ColorPickerDialog.parse(hex), Ui.dp(c, 6), Ui.BORDER, 1));
                    commit(() -> PropertyEditing.applyColor(Bridge.app(), f, hex));
                }));
                box.addView(r, Ui.matchWrap());
                editors.put(f, r);
                return box;
            }
            case "image": {
                box.addView(label(c, f));
                LinearLayout r = Ui.row(c);
                r.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
                r.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
                ImageView iv = Ui.smallIcon(c, R.drawable.ic_s2_image, Ui.TEXT_3, 40);
                r.addView(iv);
                TextView t = Ui.text(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val, 13.5f, Ui.TEXT);
                t.setPadding(Ui.dp(c, 10), 0, 0, 0);
                t.setMaxLines(2);
                r.addView(t, Ui.weight(1));
                Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs -> {
                    File img = new File(imgs + values.get(f));
                    if (img.isFile()) FilePickerDialog.thumb(iv, img, Ui.dp(c, 40));
                });
                r.setOnClickListener(v -> Bridge.query(() -> Bridge.editor().getProject().getImagesPath(), imgs ->
                        FilePickerDialog.show(c, Tr.t(f), imgs, new String[]{"jpg", "png", "jpeg"}, true, (file, rel) -> {
                            values.put(f, rel);
                            t.setText(rel);
                            FilePickerDialog.thumb(iv, file, Ui.dp(c, 40));
                            commit(() -> PropertyEditing.applyImage(Bridge.app(), f, rel));
                        })));
                box.addView(r, Ui.matchWrap());
                editors.put(f, r);
                return box;
            }
            case "File": {
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val.replace(com.star4droid.template.Utils.Utils.seperator, "/"));
                t.setOnClickListener(v -> Bridge.query(() -> PropertyEditing.fileRoot(Bridge.editor(), f), rootPath ->
                        FilePickerDialog.show(c, Tr.t(f), rootPath, PropertyEditing.fileExtensions(f), false, (file, rel) -> {
                            String low = rel.toLowerCase();
                            if (f.equals("font") && (low.endsWith(".ttf") || low.endsWith(".otf"))) {
                                Dialogs.confirm(c, "TTF/OTF", Tr.t("ui_ttf_note", "TTF/OTF fonts are converted to a bitmap font (s2df). Create it now?"), null, () -> {
                                    shell.panels.hide();
                                    Bridge.gl(() -> PropertyEditing.applyTtfFont(Bridge.app(), f, Gdx.files.absolute(file.getAbsolutePath()), rel));
                                });
                                return;
                            }
                            values.put(f, rel);
                            t.setText(rel);
                            commit(() -> PropertyEditing.applyFile(Bridge.app(), f, rel));
                        })));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "body": {
                box.addView(label(c, f));
                boolean multi = f.equals("Collision");
                TextView t = chooser(c, val.isEmpty() ? "—" : val);
                t.setOnClickListener(v -> Bridge.query(() -> Bridge.editor().getBodiesList(), bodies -> {
                    bodies.remove(selectedName);
                    if (multi) pickMany(c, f, bodies, t);
                    else {
                        String[] opts = new String[bodies.size() + 1];
                        opts[0] = "— " + Tr.t("ui_none", "None");
                        for (int i = 0; i < bodies.size(); i++) opts[i + 1] = bodies.get(i);
                        Dialogs.choose(c, Tr.t(f), opts, i -> {
                            String nv = i == 0 ? "" : bodies.get(i - 1);
                            Bridge.query(() -> PropertyEditing.applyBody(Bridge.app(), f, nv), err -> {
                                if (err != null) { Ui.toast(c, err); return; }
                                values.put(f, nv);
                                t.setText(nv.isEmpty() ? "—" : nv);
                                if (f.equals("parent")) shell.elements.refresh();
                            });
                        });
                    }
                }));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "script": {
                box.addView(label(c, f));
                TextView t = chooser(c, val.isEmpty() ? "[" + Tr.t("choose", "choose") + "]" : val);
                t.setOnClickListener(v -> pickScript(c, f, t, null));
                box.addView(t, Ui.matchWrap());
                editors.put(f, t);
                return box;
            }
            case "points": {
                box.addView(label(c, f));
                LinearLayout b = Ui.button(c, R.drawable.ic_s2_collider, Tr.t("customColliderTitle", "Edit collider shape"), false, v -> {
                    shell.panels.hide();
                    Bridge.gl(() -> {
                        TestApp app = Bridge.app();
                        app.getControlLayer().getCustomColliderEditor().show(app.getUiStage());
                    });
                });
                box.addView(b);
                return box;
            }
            default: {
                box.addView(label(c, f));
                TextView t = Ui.text(c, val, 14, Ui.TEXT_3);
                t.setPadding(Ui.dp(c, 4), 0, 0, Ui.dp(c, 4));
                box.addView(t);
                return box;
            }
        }
    }

    private TextView chooser(Context c, String text) {
        TextView t = Ui.text(c, text, 14, Ui.TEXT);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
        t.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_s2_expand_more, 0);
        t.setMaxLines(2);
        return t;
    }

    private void pickMany(Context c, String f, ArrayList<String> bodies, TextView t) {
        String cur = values.get(f) == null ? "" : values.get(f);
        String[] names = bodies.toArray(new String[0]);
        boolean[] checked = new boolean[names.length];
        for (int i = 0; i < names.length; i++) checked[i] = cur.contains("(" + names[i] + ")");
        new AlertDialog.Builder(c)
                .setTitle(Tr.t(f))
                .setMultiChoiceItems(names, checked, (d, w, on) -> checked[w] = on)
                .setPositiveButton(Tr.t("ok", "OK"), (d, w) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < names.length; i++) if (checked[i]) sb.append("(").append(names[i]).append(")");
                    String nv = sb.toString();
                    values.put(f, nv);
                    t.setText(nv.isEmpty() ? "—" : nv);
                    commit(() -> PropertyEditing.applyBody(Bridge.app(), f, nv));
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }

    /** Body-script selector: bodies of the current scene, or "scene/body" from another scene. */
    private void pickScript(Context c, String f, TextView t, String sceneToShow) {
        Bridge.query(() -> {
            String scene = Bridge.editor().getScene();
            String target = sceneToShow == null ? scene : sceneToShow;
            ArrayList<String> bodies = new ArrayList<>();
            if (target.equals(scene)) bodies.addAll(Bridge.editor().getBodiesList());
            else {
                ArrayList<com.star4droid.template.Utils.PropertySet<String, Object>> sets = new Gson().fromJson(
                        Gdx.files.absolute(Bridge.editor().getProject().getScenesPath() + target).readString(),
                        new TypeToken<ArrayList<com.star4droid.template.Utils.PropertySet<String, Object>>>() {}.getType());
                if (sets != null) for (com.star4droid.template.Utils.PropertySet<String, Object> ps : sets) bodies.add(ps.getString("name"));
            }
            ArrayList<String> scenes = new ArrayList<>();
            for (FileHandle fh : Gdx.files.absolute(Bridge.editor().getProject().getScenesPath()).list())
                if (!fh.isDirectory() && !fh.name().equals(target)) scenes.add(fh.name());
            return new Object[]{scene, target, bodies, scenes};
        }, r -> {
            String scene = (String) r[0], target = (String) r[1];
            ArrayList<String> bodies = (ArrayList<String>) r[2], scenes = (ArrayList<String>) r[3];
            ArrayList<String> opts = new ArrayList<>(bodies);
            for (String s : scenes) opts.add("📁 " + Tr.t("ui_other_scene", "Other scene") + ": " + s);
            Dialogs.choose(c, Tr.t("selectScript", "Select script") + (target.equals(scene) ? "" : " (" + target + ")"), opts.toArray(new String[0]), i -> {
                if (i >= bodies.size()) {
                    pickScript(c, f, t, scenes.get(i - bodies.size()));
                    return;
                }
                String v = target.equals(scene) ? bodies.get(i) : target + "/" + bodies.get(i);
                values.put(f, v);
                t.setText(v);
                commit(() -> PropertyEditing.applyScript(Bridge.app(), f, v));
            });
        });
    }

    private static String display(String type, String v) {
        if (!"float".equals(type)) return v;
        try {
            float f = Float.parseFloat(v);
            if (f == Math.rint(f) && Math.abs(f) < 1e7) return String.valueOf((long) f);
            return String.valueOf(Math.round(f * 1000f) / 1000f);
        } catch (Exception e) {
            return v;
        }
    }

    private void commit(Runnable glWork) {
        Bridge.gl(glWork);
    }

    /** Update visible values without rebuilding (skips the field being edited). */
    private void updateValues(LinkedHashMap<String, String> nv) {
        for (Map.Entry<String, String> e : nv.entrySet()) {
            String f = e.getKey(), v = e.getValue();
            if (v.equals(values.get(f))) continue;
            values.put(f, v);
            View ed = editors.get(f);
            if (ed == null) continue;
            String type = typeOf.get(f);
            if (ed instanceof EditText) {
                if (!ed.hasFocus()) ((EditText) ed).setText(display(type, v));
            } else if (ed instanceof SwitchCompat) {
                ((SwitchCompat) ed).setChecked("true".equals(v));
            } else if (ed instanceof TextView && !"image".equals(type) && !"color".equals(type)) {
                ((TextView) ed).setText(v.isEmpty() ? "—" : ("spinner".equals(type) ? Tr.t(v, v) : v));
            } else {
                // complex rows: rebuild on next selection refresh
                fieldsSignature = "";
            }
        }
    }

    public static ViewGroup.LayoutParams wrap() {
        return new ViewGroup.LayoutParams(-2, -2);
    }
}
