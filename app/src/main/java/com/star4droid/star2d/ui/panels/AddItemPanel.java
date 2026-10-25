package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** "Add element" sheet with a described card per element type. */
public class AddItemPanel implements PanelHost.Panel {
    private final EditorShell shell;

    public AddItemPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("ui_add_element", "Add element"); }
    @Override public int icon() { return R.drawable.ic_s2_add; }

    // {translation key, english name, type for icon, english description, arabic description}
    private static final String[][] BODIES = {
            {"boxBody", "Box", "Box", "A rectangle with physics. Players, walls, platforms.", "مستطيل بفيزياء: لاعب، جدار، منصة."},
            {"circleBody", "Circle", "Circle", "A round body: balls, wheels, coins.", "جسم دائري: كرة، عجلة، عملة."},
            {"textItem", "Text", "Text", "Show text: score, titles, messages.", "عرض نص: النقاط، العناوين، الرسائل."},
            {"joystickItem", "Joystick", "Joystick", "On-screen joystick to control the player.", "عصا تحكم على الشاشة لتحريك اللاعب."},
            {"progressBar", "Progress bar", "Progress", "Health bars, loading bars, timers.", "شريط الصحة أو التحميل أو المؤقت."},
            {"customBody", "Custom shape", "Custom", "Body with a custom collider shape (polygon).", "جسم بشكل تصادم مخصص (مضلع)."},
            {"particleEffect", "Particles", "Particle", "Fire, smoke, sparks, rain effects.", "تأثيرات: نار، دخان، شرر، مطر."},
            {"camera", "Camera", "Camera", "Controls what part of the world is visible.", "تتحكم في الجزء الظاهر من العالم."},
            {"tiledMap", "Tiled map", "Map", "Load a level made with Tiled (.tmx).", "تحميل مرحلة مصممة ببرنامج Tiled."},
    };
    private static final String[][] LIGHTS = {
            {"pointLight", "Point light", "Light", "Light that shines in all directions.", "ضوء ينير في كل الاتجاهات."},
            {"directionalLight", "Directional light", "Light", "Sun-like light from one direction.", "ضوء كالشمس من اتجاه واحد."},
            {"coneLight", "Cone light", "Light", "Flashlight / spotlight cone.", "ضوء مخروطي كالكشاف."},
    };

    @Override
    public View view(Context c) {
        boolean ar = Ui.isRtl(c);
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 12));
        col.addView(Ui.sectionLabel(c, Tr.t("newBody", "Elements")));
        col.addView(grid(c, BODIES, ar, false));
        col.addView(Ui.sectionLabel(c, Tr.t("newLight", "Lights")));
        col.addView(grid(c, LIGHTS, ar, true));
        LinearLayout settings = Ui.button(c, R.drawable.ic_s2_settings, Tr.t("lightSettings", "Light settings"), false,
                v -> { shell.panels.hide(); com.star4droid.star2d.ui.LightSettings.show(shell.activity()); });
        LinearLayout.LayoutParams lp = Ui.margins(new LinearLayout.LayoutParams(-2, Ui.dp(c, 38)), c, 4, 8, 4, 0);
        col.addView(settings, lp);
        ScrollView s = new ScrollView(c);
        s.addView(col);
        return s;
    }

    private GridLayout grid(Context c, String[][] items, boolean ar, boolean lights) {
        GridLayout g = new GridLayout(c);
        int cols = Ui.isLandscape(c) ? 2 : 2;
        g.setColumnCount(cols);
        for (int i = 0; i < items.length; i++) {
            final int pos = i;
            String[] it = items[i];
            LinearLayout card = Ui.row(c);
            card.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 8), Ui.dp(c, 10));
            card.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 10)));
            card.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ic = Ui.smallIcon(c, ElementsPanel.typeIcon(it[2]), ElementsPanel.typeColor(it[2]), 26);
            card.addView(ic);
            LinearLayout texts = Ui.column(c);
            texts.setPadding(Ui.dp(c, 10), 0, 0, 0);
            TextView name = Ui.text(c, Tr.t(it[0], it[1]), 14, Ui.TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            TextView d = Ui.text(c, Tr.t("ui_add_desc_" + it[0], ar ? it[4] : it[3]), 11.5f, Ui.TEXT_3);
            d.setMaxLines(3);
            texts.addView(name);
            texts.addView(d);
            card.addView(texts, Ui.weight(1));
            com.star4droid.star2d.ui.tutorial.Tut.tag(card, lights ? "add:light" + pos : "add:" + it[2].toLowerCase());
            card.setOnClickListener(v -> {
                shell.panels.hide();
                Bridge.gl(() -> {
                    if (lights) Bridge.controls().actionAddLight(pos);
                    else Bridge.controls().actionAddBody(pos);
                });
                Ui.toast(c, Tr.t("ui_added", "Added") + ": " + Tr.t(it[0], it[1]));
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            lp.width = 0;
            lp.setMargins(Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
            g.addView(card, lp);
        }
        g.setLayoutParams(Ui.matchWrap());
        return g;
    }

    static ColorStateList tint(int c) { return ColorStateList.valueOf(c); }
}
