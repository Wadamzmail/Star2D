package com.star4droid.star2d.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.star4droid.star2d.Helpers.PropertySet;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.utils.EditorAction;

import java.util.LinkedHashMap;

/**
 * Scene light settings (Android). Writes the keys the game runtime reads (StageImp.setupLight:
 * Enable Blur, Blur Number, Enable Culling, Gamma Correction, Enable Shadows, Use Diffuse Light,
 * Ambient Light) and keeps the old dialog's keys in sync. One undo entry per save.
 */
public final class LightSettings {
    private LightSettings() {}

    private static final String[][] SWITCHES = {
            {"Enable Blur", "Enable Blur", "ui_light_blur", "Soft (blurred) light edges"},
            {"Enable Shadows", "Shadows", "ui_light_shadows", "Bodies cast shadows"},
            {"Enable Culling", "Culling", "ui_light_culling", "Skip lights outside the screen (faster)"},
            {"Use Diffuse Light", "Use Diffuse Light", "ui_light_diffuse", "Diffuse light (softer, less contrast)"},
            {"Gamma Correction", "Gamma Correction", "ui_light_gamma", "Gamma correction"},
    };

    public static void show(Activity a) {
        Bridge.query(() -> {
            LibgdxEditor ed = Bridge.editor();
            PropertySet<String, Object> cfg = ed.getConfig();
            LinkedHashMap<String, String> v = Bridge.snapshot(cfg);
            v.put("__old", cfg.toString());
            return v;
        }, v -> build(a, v));
    }

    private static void build(Activity a, LinkedHashMap<String, String> v) {
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 20), Ui.dp(a, 6), Ui.dp(a, 20), 0);
        TextView hint = Ui.text(a, Tr.t("ui_light_hint", "These settings apply to every light in this scene. Add lights with the + button."), 12.5f, Ui.TEXT_3);
        col.addView(hint);
        SwitchCompat[] sw = new SwitchCompat[SWITCHES.length];
        for (int i = 0; i < SWITCHES.length; i++) {
            String[] s = SWITCHES[i];
            sw[i] = new SwitchCompat(a);
            sw[i].setText(Tr.t(s[2], s[3]));
            sw[i].setTextColor(Ui.TEXT);
            sw[i].setTextSize(14.5f);
            sw[i].setMinHeight(Ui.dp(a, 48));
            String cur = v.containsKey(s[0]) ? v.get(s[0]) : v.get(s[1]);
            sw[i].setChecked("true".equals(cur));
            col.addView(sw[i], Ui.matchWrap());
        }
        col.addView(Ui.sectionLabel(a, Tr.t("ui_light_blur_num", "Blur passes")));
        EditText blur = Ui.input(a, v.containsKey("Blur Number") && !v.get("Blur Number").isEmpty() ? v.get("Blur Number") : (v.containsKey("blur_number") ? v.get("blur_number") : "1"), "1");
        blur.setInputType(InputType.TYPE_CLASS_NUMBER);
        col.addView(blur, Ui.matchWrap());

        col.addView(Ui.sectionLabel(a, Tr.t("ui_light_ambient", "Ambient light (darkness of the scene)")));
        final String[] ambient = {v.containsKey("Ambient Light") && !v.get("Ambient Light").isEmpty() ? v.get("Ambient Light") : "FFFFFFFF"};
        LinearLayout row = Ui.row(a);
        View swatch = new View(a);
        swatch.setBackground(Ui.rounded(ColorPickerDialog.parse(ambient[0]), Ui.dp(a, 6), Ui.BORDER, 1));
        row.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 30)));
        TextView hex = Ui.text(a, ambient[0], 14, Ui.TEXT);
        hex.setTypeface(Typeface.MONOSPACE);
        hex.setPadding(Ui.dp(a, 10), 0, 0, 0);
        row.addView(hex, Ui.weight(1));
        row.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
        row.setBackground(Ui.ripple(0xFF121417, Ui.dp(a, 7)));
        row.setOnClickListener(x -> ColorPickerDialog.show(a, Tr.t("ui_light_ambient", "Ambient light"), ambient[0], null, c -> {
            if (c == null) return;
            ambient[0] = c;
            hex.setText(c);
            swatch.setBackground(Ui.rounded(ColorPickerDialog.parse(c), Ui.dp(a, 6), Ui.BORDER, 1));
        }));
        col.addView(row, Ui.matchWrap());
        TextView tip = Ui.text(a, Tr.t("ui_light_ambient_tip", "Dark colors make the night darker; white = no darkness."), 11.5f, Ui.TEXT_3);
        tip.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        col.addView(tip);

        ScrollView s = new ScrollView(a);
        s.addView(col);
        new AlertDialog.Builder(a)
                .setTitle(Tr.t("lightSettings", "Light settings"))
                .setView(s)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> {
                    boolean[] on = new boolean[SWITCHES.length];
                    for (int i = 0; i < on.length; i++) on[i] = sw[i].isChecked();
                    String blurN = blur.getText().toString().trim().isEmpty() ? "1" : blur.getText().toString().trim();
                    String amb = ambient[0];
                    String old = v.get("__old");
                    Bridge.gl(() -> {
                        LibgdxEditor ed = Bridge.editor();
                        PropertySet<String, Object> cfg = ed.getConfig();
                        for (int i = 0; i < SWITCHES.length; i++) {
                            cfg.put(SWITCHES[i][0], String.valueOf(on[i]));
                            cfg.put(SWITCHES[i][1], String.valueOf(on[i])); // legacy key
                        }
                        cfg.put("Blur Number", blurN);
                        cfg.put("blur_number", blurN);
                        cfg.put("Ambient Light", amb);
                        cfg.put("ambient_light", amb);
                        ed.saveConfig();
                        ed.setupLight();
                        EditorAction.sceneConfigChanged(ed, old, cfg.toString());
                    });
                    Snack.show(Tr.t("saved", "Saved"));
                })
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .show();
    }
}
