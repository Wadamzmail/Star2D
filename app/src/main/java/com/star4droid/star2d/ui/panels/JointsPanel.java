package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.star4droid.star2d.Helpers.JointsHelper;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Joints of the current scene. Files are "joints/&lt;scene&gt;/&lt;name&gt;-&lt;JointType&gt;" with the same JSON
 * ([{name, value, code}]) the LibGDX joint dialog writes, so CodeGenerator reads them unchanged.
 */
public class JointsPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private ScrollView root;
    private LinearLayout list;

    private static final String[][] INFO = {
            {"RevoluteJoint", "Rotates around a point: wheels, doors, pendulums.", "دوران حول نقطة: عجلات، أبواب، بندول."},
            {"PrismaticJoint", "Slides along an axis: pistons, elevators.", "انزلاق على محور: مكابس، مصاعد."},
            {"DistanceJoint", "Keeps two bodies at a distance (stick / spring).", "يحافظ على مسافة بين جسمين (عصا / نابض)."},
            {"PulleyJoint", "Two bodies hanging on a pulley.", "جسمان معلقان على بكرة."},
            {"MouseJoint", "Drags a body toward a target point.", "يسحب الجسم نحو نقطة هدف."},
            {"WheelJoint", "Car wheel with suspension.", "عجلة سيارة مع نظام تعليق."},
            {"WeldJoint", "Glues two bodies together.", "يلصق جسمين معاً."},
            {"FrictionJoint", "Adds top-down friction between bodies.", "يضيف احتكاكاً بين الجسمين."},
            {"RopeJoint", "Maximum distance, like a rope.", "أقصى مسافة، مثل الحبل."},
            {"MotorJoint", "Moves a body relative to another.", "يحرّك جسماً بالنسبة لآخر."},
    };

    public JointsPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("joints", "Joints"); }
    @Override public int icon() { return R.drawable.ic_s2_link; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("addJoint", "Add joint"), v -> chooseType(c))};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        root = new ScrollView(c);
        list = Ui.column(c);
        list.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 16));
        root.addView(list);
        return root;
    }

    @Override public void onShown() { refresh(); }

    public void refresh() {
        if (list == null || !Bridge.ready()) return;
        Bridge.query(() -> {
            ArrayList<String> names = new ArrayList<>();
            FileHandle dir = Gdx.files.absolute(Bridge.editor().getProject().getJoints(Bridge.editor().getScene()));
            if (dir.exists()) for (FileHandle f : dir.list()) if (!f.isDirectory()) names.add(f.name());
            return names;
        }, names -> {
            Context c = list.getContext();
            list.removeAllViews();
            if (names.isEmpty()) {
                TextView t = Ui.text(c, Tr.t("ui_no_joints", "No joints yet. Joints connect two bodies (wheels, doors, ropes...). Tap + to add one."), 14, Ui.TEXT_3);
                t.setPadding(Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 10));
                list.addView(t);
            }
            for (String n : names) {
                String name = n.contains("-") ? n.substring(0, n.indexOf('-')) : n;
                String type = n.contains("-") ? n.substring(n.indexOf('-') + 1) : "";
                LinearLayout row = PanelHost.listRow(c, R.drawable.ic_s2_joint, 0xFF4DB6AC, name, type.replace("Joint", " Joint"));
                row.setOnClickListener(v -> edit(c, type, name, n));
                row.setOnLongClickListener(v -> { menu(c, v, n, type, name); return true; });
                list.addView(row);
            }
            LinearLayout add = Ui.button(c, R.drawable.ic_s2_add, Tr.t("addJoint", "Add joint"), true, v -> chooseType(c));
            list.addView(add, Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 10, 4, 0));
        });
    }

    private void menu(Context c, View anchor, String file, String type, String name) {
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(0, 1, 0, Tr.t("edit", "Edit"));
        m.getMenu().add(0, 2, 1, Tr.t("delete", "Delete"));
        m.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) edit(c, type, name, file);
            else Dialogs.confirm(c, Tr.t("delete", "Delete"), name + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"),
                    () -> Bridge.gl(() -> {
                        Gdx.files.absolute(Bridge.editor().getProject().getJoints(Bridge.editor().getScene()) + file).delete();
                        refreshJointsList();
                        com.star4droid.star2d.ui.Ui.ui(this::refresh);
                    }));
            return true;
        });
        m.show();
    }

    private static void refreshJointsList() {
        try { Bridge.controls().getJointsList().refresh(); } catch (Throwable ignored) {}
    }

    private void chooseType(Context c) {
        Bridge.query(() -> {
            JointsHelper.init();
            ArrayList<String> types = new ArrayList<>();
            for (HashMap<String, Object> h : JointsHelper.getJointsListMap()) {
                String j = h.get("joint").toString();
                if (!j.contains("GearJoint")) types.add(j);
            }
            return types;
        }, types -> {
            boolean ar = Ui.isRtl(c);
            String[] labels = new String[types.size()];
            for (int i = 0; i < labels.length; i++) {
                String t = types.get(i).replace("Def", "");
                String d = "";
                for (String[] in : INFO) if (in[0].equals(t)) d = ar ? in[2] : in[1];
                labels[i] = t + (d.isEmpty() ? "" : "\n   " + d);
            }
            Dialogs.choose(c, Tr.t("addJoint", "Add joint"), labels, i -> edit(c, types.get(i).replace("Def", ""), "", null));
        });
    }

    // ------------------------------------------------------------------ editor

    /** One editable field of the joint form. kind: body | vec2 | float | bool */
    private static class FieldRow {
        String name, kind;
        View view;
        EditText a, b;
        TextView body;
        SwitchCompat check;
        String bodyValue = "";

        String value() {
            switch (kind) {
                case "body": return bodyValue.isEmpty() ? null : bodyValue + ".getBody()";
                case "vec2":
                    try {
                        Float.parseFloat(a.getText().toString());
                        Float.parseFloat(b.getText().toString());
                        return "new Vector2(" + a.getText() + "f&&" + b.getText() + "f)";
                    } catch (Exception e) {
                        String s = a.getText().toString();
                        if (s.contains("(") || s.trim().isEmpty()) return null;
                        return s + ".getBody().getWorldCenter()";
                    }
                case "float":
                    try {
                        Float.parseFloat(a.getText().toString());
                        return a.getText().toString();
                    } catch (Exception e) {
                        return "0";
                    }
                default: return String.valueOf(check.isChecked());
            }
        }

        String code() {
            String v = value();
            switch (kind) {
                case "body": return v + ".getBody()";
                case "vec2": return "\t\t\t%1$s." + name + ".set(" + v.replace("&&", ",") + ");\n";
                case "float": return "\t\t\t%1$s." + name + "=" + v + "f;\n";
                default: return "\n\t\t\t%1$s." + name + "=" + v + ";\n";
            }
        }

        void set(Object v) {
            String s = String.valueOf(v);
            switch (kind) {
                case "body": bodyValue = s.replace(".getBody()", ""); body.setText(bodyValue); break;
                case "vec2":
                    if (s.contains("getWorldCenter()")) { a.setText(s.replace(".getBody().getWorldCenter()", "")); b.setText(""); return; }
                    String str = s.replace("f", "").replace("new Vector2(", "").replace(")", "");
                    String[] p = str.contains("&&") ? str.split("&&") : str.split(",");
                    if (p.length == 2) { a.setText(p[0].trim()); b.setText(p[1].trim()); }
                    break;
                case "float": a.setText(s.replace("f", "")); break;
                default: check.setChecked(s.equals("true"));
            }
        }
    }

    /** Open the add/edit form. file == null for a new joint. */
    private void edit(Context c, String type, String name, String file) {
        Bridge.query(() -> {
            JointsHelper.init();
            LinkedHashMap<String, String[]> fields = new LinkedHashMap<>(); // name -> {kind, default}
            String params = JointsHelper.get(type, "params"), types = JointsHelper.get(type, "types");
            ArrayList<String> lower = new ArrayList<>();
            if (params != null && types != null) {
                String[] names = params.split(","), ts = types.split(",");
                for (int i = 0; i < ts.length && i < names.length; i++) {
                    lower.add(names[i].toLowerCase());
                    String k = ts[i].toLowerCase();
                    fields.put(names[i], new String[]{k.equals("vector2") ? "vec2" : (k.equals("body") ? "body" : "float"),
                            k.equals("vector2") ? "new Vector2(0f&&0f)" : (k.equals("float") ? "0" : "")});
                }
            }
            Class<?> clazz = Class.forName("com.badlogic.gdx.physics.box2d.joints." + type + (type.endsWith("Def") ? "" : "Def"));
            Constructor<?> cc = clazz.getConstructor();
            Object def = cc.newInstance();
            for (Field f : clazz.getFields()) {
                if (Modifier.isFinal(f.getModifiers()) && !Vector2.class.isAssignableFrom(f.getType())) continue;
                if (lower.contains(f.getName().toLowerCase())) continue;
                Object dv = f.get(def);
                if (f.getType() == float.class || f.getType() == Float.class) fields.put(f.getName(), new String[]{"float", String.valueOf(dv)});
                else if (Vector2.class.isAssignableFrom(f.getType())) {
                    Vector2 vv = (Vector2) dv;
                    fields.put(f.getName(), new String[]{"vec2", "new Vector2(" + vv.x + "f&&" + vv.y + "f)"});
                } else if (f.getType() == boolean.class || f.getType() == Boolean.class) fields.put(f.getName(), new String[]{"bool", String.valueOf(dv)});
            }
            String saved = file == null ? "" : Gdx.files.absolute(Bridge.editor().getProject().getJoints(Bridge.editor().getScene()) + file).readString();
            return new Object[]{fields, saved, Bridge.editor().getBodiesList()};
        }, r -> showForm(c, type, name, file, (LinkedHashMap<String, String[]>) r[0], (String) r[1], (ArrayList<String>) r[2]));
    }

    private void showForm(Context c, String type, String name, String file, LinkedHashMap<String, String[]> fields, String saved, ArrayList<String> bodies) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), Ui.dp(c, 6));
        TextView nl = Ui.text(c, Tr.t("name", "Name"), 12.5f, Ui.TEXT_2);
        col.addView(nl);
        EditText nameField = Ui.input(c, name, "joint1");
        nameField.setEnabled(file == null);
        col.addView(nameField, Ui.matchWrap());

        ArrayList<FieldRow> rows = new ArrayList<>();
        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (java.util.Map.Entry<String, String[]> e : fields.entrySet()) {
            FieldRow fr = new FieldRow();
            fr.name = e.getKey();
            fr.kind = e.getValue()[0];
            TextView l = Ui.text(c, e.getKey(), 12.5f, Ui.TEXT_2);
            l.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4));
            switch (fr.kind) {
                case "body": {
                    col.addView(l);
                    fr.body = Ui.text(c, "—", 14, Ui.TEXT);
                    fr.body.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 10), Ui.dp(c, 9));
                    fr.body.setBackground(Ui.ripple(0xFF121417, Ui.dp(c, 7)));
                    fr.body.setOnClickListener(v -> Dialogs.choose(c, fr.name, bodies.toArray(new String[0]), i -> {
                        fr.bodyValue = bodies.get(i);
                        fr.body.setText(fr.bodyValue);
                    }));
                    if (!bodies.isEmpty() && file == null) {
                        int idx = Math.min(rows.size(), bodies.size() - 1);
                        fr.bodyValue = bodies.get(Math.max(0, idx));
                        fr.body.setText(fr.bodyValue);
                    }
                    col.addView(fr.body, Ui.matchWrap());
                    break;
                }
                case "vec2": {
                    col.addView(l);
                    LinearLayout r = Ui.row(c);
                    fr.a = Ui.input(c, "0", "x");
                    fr.b = Ui.input(c, "0", "y");
                    int num = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
                    fr.b.setInputType(num);
                    r.addView(fr.a, Ui.weight(1));
                    LinearLayout.LayoutParams bl = Ui.weight(1);
                    bl.setMarginStart(Ui.dp(c, 6));
                    r.addView(fr.b, bl);
                    TextView pick = Ui.text(c, Tr.t("ui_pick", "Pick"), 13, Ui.ACCENT);
                    pick.setTypeface(Typeface.DEFAULT_BOLD);
                    pick.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
                    pick.setOnClickListener(v -> pickPoint(c, dialogRef[0], fr));
                    r.addView(pick);
                    col.addView(r, Ui.matchWrap());
                    TextView hint = Ui.text(c, Tr.t("ui_vec_hint", "Numbers, or an element name in X to use its center"), 11, Ui.TEXT_3);
                    col.addView(hint);
                    break;
                }
                case "float": {
                    col.addView(l);
                    fr.a = Ui.input(c, "0", "0");
                    fr.a.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    col.addView(fr.a, Ui.matchWrap());
                    break;
                }
                default: {
                    fr.check = new SwitchCompat(c);
                    fr.check.setText(e.getKey());
                    fr.check.setTextColor(Ui.TEXT);
                    fr.check.setMinHeight(Ui.dp(c, 44));
                    col.addView(fr.check, Ui.matchWrap());
                }
            }
            if (!e.getValue()[1].isEmpty()) fr.set(e.getValue()[1]);
            rows.add(fr);
        }
        // restore saved values
        if (saved != null && !saved.isEmpty()) {
            try {
                ArrayList<HashMap<String, Object>> list = new Gson().fromJson(saved, new TypeToken<ArrayList<HashMap<String, Object>>>() {}.getType());
                for (HashMap<String, Object> h : list)
                    for (FieldRow fr : rows)
                        if (fr.name.equals(String.valueOf(h.get("name")))) fr.set(h.get("value"));
            } catch (Exception ignored) {}
        }

        ScrollView sv = new ScrollView(c);
        sv.addView(col);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle((file == null ? Tr.t("addJoint", "Add joint") : Tr.t("edit", "Edit")) + " · " + type)
                .setView(sv)
                .setPositiveButton(file == null ? Tr.t("ui_add", "Add") : Tr.t("save", "Save"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        dialogRef[0] = d;
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nm = nameField.getText().toString().trim();
            String err = Dialogs.checkName(nm);
            if (err != null) { nameField.setError(err); return; }
            ArrayList<LinkedHashMap<String, Object>> out = new ArrayList<>();
            for (FieldRow fr : rows) {
                String val = fr.value();
                if (val == null) {
                    Ui.toast(c, Tr.t("ui_fill_field", "Please fill") + ": " + fr.name);
                    return;
                }
                LinkedHashMap<String, Object> h = new LinkedHashMap<>();
                h.put("value", val);
                h.put("name", fr.name);
                h.put("code", fr.code());
                out.add(h);
            }
            String json = new Gson().toJson(out);
            String fileName = file != null ? file : nm + "-" + type.replace("Def", "");
            d.dismiss();
            Bridge.gl(() -> {
                Gdx.files.absolute(Bridge.editor().getProject().getJoints(Bridge.editor().getScene()) + fileName).writeString(json, false);
                refreshJointsList();
                Ui.ui(this::refresh);
            });
        }));
        d.show();
    }

    /** Hide the form, let the user tap the canvas, then fill x/y and show the form again. */
    private void pickPoint(Context c, AlertDialog form, FieldRow fr) {
        if (form != null) form.hide();
        shell.panels.hide();
        Ui.toast(c, Tr.t("ui_tap_canvas", "Tap a point on the canvas"));
        Bridge.gl(() -> Bridge.editor().setOnPick((x, y) -> Ui.ui(() -> {
            fr.a.setText(String.valueOf(x));
            fr.b.setText(String.valueOf(y));
            if (form != null) form.show();
            shell.panels.show(this);
        })));
    }
}
