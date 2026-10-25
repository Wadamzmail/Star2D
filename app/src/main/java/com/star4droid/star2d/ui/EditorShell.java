package com.star4droid.star2d.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.TestApp;
import com.star4droid.star2d.editor.ui.ControlLayer;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.tutorial.Tut;
import com.star4droid.star2d.ui.panels.AddItemPanel;
import com.star4droid.star2d.ui.panels.AiPanel;
import com.star4droid.star2d.ui.panels.ElementsPanel;
import com.star4droid.star2d.ui.panels.EventsPanel;
import com.star4droid.star2d.ui.panels.FilesPanel;
import com.star4droid.star2d.ui.panels.JointsPanel;
import com.star4droid.star2d.ui.panels.PropertiesPanel;
import com.star4droid.star2d.ui.panels.SettingsPanel;
import com.star4droid.star2d.ui.panels.VariablesPanel;

import java.util.ArrayList;

/**
 * Native Android editor UI placed on top of the LibGDX scene canvas.
 * Only the canvas itself stays LibGDX; every bar, panel and dialog is Android.
 * The shell hides itself while the game runs.
 */
public class EditorShell extends FrameLayout implements ControlLayer.NativeUi, TestApp.NativeHooks {

    /** Things only the activity can do. */
    public interface Host {
        void openJava(String path);
        /** Import files (system picker) into a folder, then call done. */
        void importFiles(String targetDir, Runnable done);
        /** Open a visual script in the block editor; false = use the legacy editor. */
        boolean openBlocks(String file, String hints);
    }

    private final Activity activity;
    private final Host host;
    public final PanelHost panels;
    private final FrameLayout chrome;

    public final ElementsPanel elements;
    public final PropertiesPanel properties;
    public final EventsPanel events;
    public final JointsPanel joints;
    public final VariablesPanel variables;
    public final FilesPanel files;
    public final AiPanel ai;
    public final SettingsPanel settings;
    public final AddItemPanel addItem;

    private ImageView undoBtn, redoBtn, lockBtn;
    private final ArrayList<ImageView> toolButtons = new ArrayList<>();
    private final ArrayList<ImageView> railButtons = new ArrayList<>();
    private final ArrayList<PanelHost.Panel> railPanels = new ArrayList<>();
    private LinearLayout tabsRow, axisGroup;
    private TextView titleView, indexingChip;
    private String tool = "grid", axis = "none";
    private boolean playing = false, projectOpen = false;
    private AlertDialog playDialog;
    private boolean indexing = false;
    /** The editor shell of the open editor activity (for tutorials). */
    public static EditorShell current;
    private TextView playStatus;
    private ProgressBar playProgress;

    public EditorShell(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        Tr.init(activity);
        setLayoutDirection(Ui.isRtl(activity) ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);

        chrome = new FrameLayout(activity);
        addView(chrome, Ui.match());
        panels = new PanelHost(activity);
        addView(panels, Ui.match());
        panels.setOnVisibilityChanged(this::updateRailSelection);

        elements = new ElementsPanel(this);
        properties = new PropertiesPanel(this);
        events = new EventsPanel(this);
        joints = new JointsPanel(this);
        variables = new VariablesPanel(this);
        files = new FilesPanel(this);
        ai = new AiPanel(this);
        settings = new SettingsPanel(this);
        addItem = new AddItemPanel(this);

        current = this;
        ControlLayer.nativeUi = this;
        TestApp.nativeHooks = this;
        setVisibility(GONE);
        build();
    }

    public Activity activity() { return activity; }
    public Host host() { return host; }

    // =============================================================== layout

    /** Rebuild all bars (rotation, icon-size or language change). */
    public void build() {
        Context c = activity;
        chrome.removeAllViews();
        toolButtons.clear();
        railButtons.clear();
        railPanels.clear();
        boolean land = Ui.isLandscape(c);

        LinearLayout root = Ui.column(c);
        chrome.addView(root, Ui.match());

        root.addView(buildTopBar(c, land), Ui.matchWrap());
        if (!land) {
            LinearLayout tools = bar(c);
            tools.setBackgroundColor(0xEC1C1F24);
            addSceneTools(c, tools);
            root.addView(wrapScroll(c, tools, false), Ui.matchWrap());
            root.addView(buildTabsBar(c), Ui.matchWrap());
        }

        // middle: canvas is visible through here; rails float on the sides
        FrameLayout middle = new FrameLayout(c);
        root.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout rail = buildRail(c);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        rlp.setMarginEnd(Ui.dp(c, 6));
        middle.addView(wrapScroll(c, rail, true), rlp);

        if (land) {
            LinearLayout tools = buildTools(c, true);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            tlp.setMarginStart(Ui.dp(c, 6));
            middle.addView(wrapScroll(c, tools, true), tlp);
        } else {
            root.addView(wrapScroll(c, buildTools(c, false), false), Ui.matchWrap());
        }

        indexingChip = Ui.text(c, "  " + Tr.t("indexing", "Indexing files...") + "  ", 12, Ui.TEXT_2);
        indexingChip.setBackground(Ui.rounded(0xCC202328, Ui.dp(c, 12), Ui.BORDER, 1));
        indexingChip.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        indexingChip.setVisibility(GONE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = Ui.dp(c, 8);
        middle.addView(indexingChip, ilp);

        panels.refreshLayout();
        refreshTabs();
        updateToolSelection();
        updateRailSelection();
        refreshUndoRedo();
    }

    private View wrapScroll(Context c, LinearLayout content, boolean vertical) {
        ViewGroup scroll = vertical ? new ScrollView(c) : new HorizontalScrollView(c);
        if (scroll instanceof ScrollView) ((ScrollView) scroll).setVerticalScrollBarEnabled(false);
        else ((HorizontalScrollView) scroll).setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout bar(Context c) {
        LinearLayout b = Ui.row(c);
        b.setBackgroundColor(0xF0181A1E);
        b.setPadding(Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3));
        b.setClickable(true);
        return b;
    }

    private LinearLayout buildTopBar(Context c, boolean land) {
        LinearLayout top = bar(c);
        ImageView back = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("exit", "Exit"), v -> exitProject());
        back.setScaleX(Ui.isRtl(c) ? -1 : 1);
        Tut.tag(back, "top:exit");
        top.addView(back);

        LinearLayout titleBox = Ui.column(c);
        titleBox.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0);
        titleView = Ui.text(c, "", 15, Ui.TEXT);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setMaxLines(1);
        titleBox.addView(titleView);
        if (land) {
            // tabs + scene tools inline (scrollable) in landscape
            top.addView(titleBox);
            LinearLayout inline = Ui.row(c);
            tabsRow = Ui.row(c);
            inline.addView(tabsRow);
            inline.addView(gap(c, false));
            addSceneTools(c, inline);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(inline);
            top.addView(hs, Ui.weight(1));
        } else {
            top.addView(titleBox, Ui.weight(1));
        }
        undoBtn = Ui.iconButton(c, R.drawable.ic_s2_undo, Tr.t("undo", "Undo"), v -> Bridge.gl(() -> Bridge.controls().actionUndo()));
        redoBtn = Ui.iconButton(c, R.drawable.ic_s2_redo, Tr.t("redo", "Redo"), v -> Bridge.gl(() -> Bridge.controls().actionRedo()));
        Tut.tag(undoBtn, "top:undo");
        Tut.tag(redoBtn, "top:redo");
        top.addView(undoBtn);
        top.addView(redoBtn);
        ImageView save = Ui.iconButton(c, R.drawable.ic_s2_save, Tr.t("save", "Save"), v -> save());
        Tut.tag(save, "top:save");
        top.addView(save);

        // Play: accent pill
        ImageView play = Ui.iconButton(c, R.drawable.ic_s2_play, Tr.t("ui_play", "Play"), v -> play());
        play.setBackground(Ui.ripple(Ui.SUCCESS, Ui.dp(c, 10)));
        LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) play.getLayoutParams();
        plp.setMargins(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        Tut.tag(play, "top:play");
        top.addView(play);
        return top;
    }

    /** Scene tools as icon buttons: color, lights, orientation, logs, settings, help. */
    private void addSceneTools(Context c, LinearLayout into) {
        ImageView color = Ui.iconButton(c, R.drawable.ic_s2_palette, Tr.t("ui_scene_color", "Scene background color"), v -> sceneColor());
        color.setImageTintList(ColorStateList.valueOf(0xFFFFB74D));
        Tut.tag(color, "tool:color");
        into.addView(color);
        ImageView light = Ui.iconButton(c, R.drawable.ic_s2_light, Tr.t("lightSettings", "Light settings"), v -> LightSettings.show(activity));
        light.setImageTintList(ColorStateList.valueOf(0xFFFFEE58));
        into.addView(light);
        ImageView rot = Ui.iconButton(c, R.drawable.ic_s2_screen_rotation, Tr.t("changeOrienation", "Switch portrait / landscape"), v ->
                Dialogs.confirm(activity, Tr.t("changeOrienation", "Change orientation"),
                        Tr.t("editorRestartNote", "The scenes will be saved and reopened."), null,
                        () -> Bridge.gl(() -> Bridge.controls().actionToggleOrientation())));
        into.addView(rot);
        into.addView(Ui.iconButton(c, R.drawable.ic_s2_logs, Tr.t("ui_logs", "Logs"), v -> { files.openLogs(); panels.show(files); }));
        ImageView set = Ui.iconButton(c, R.drawable.ic_s2_settings, Tr.t("settings", "Settings"), v -> panels.show(settings));
        Tut.tag(set, "tool:settings");
        into.addView(set);
        ImageView coll = Ui.iconButton(c, R.drawable.ic_s2_collections, Tr.t("ui_collections", "Collections (free assets)"), v ->
                Bridge.query(() -> Bridge.editor().getProject().getPath(), p -> CollectionsDialog.show(activity, new java.io.File(p), files::refreshList)));
        coll.setImageTintList(ColorStateList.valueOf(0xFF81C784));
        Tut.tag(coll, "tool:collections");
        into.addView(coll);
        into.addView(Ui.iconButton(c, R.drawable.ic_s2_help, Tr.t("ui_help", "Help"), v -> Help.show(activity)));
    }

    private LinearLayout buildTabsBar(Context c) {
        LinearLayout tabs = Ui.row(c);
        tabs.setBackgroundColor(0xE6141619);
        tabs.setPadding(Ui.dp(c, 6), Ui.dp(c, 3), Ui.dp(c, 6), Ui.dp(c, 3));
        tabs.setClickable(true);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        tabsRow = Ui.row(c);
        hs.addView(tabsRow);
        tabs.addView(hs, Ui.weight(1));
        return tabs;
    }

    private LinearLayout buildRail(Context c) {
        LinearLayout rail = Ui.column(c);
        rail.setBackground(Ui.rounded(0xE6181A1E, Ui.dp(c, 12), Ui.BORDER, 1));
        rail.setPadding(Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4));
        rail.setClickable(true);
        addRail(c, rail, elements);
        addRail(c, rail, properties);
        addRail(c, rail, events);
        addRail(c, rail, joints);
        addRail(c, rail, variables);
        addRail(c, rail, files);
        addRail(c, rail, ai);
        return rail;
    }

    private void addRail(Context c, LinearLayout rail, PanelHost.Panel p) {
        ImageView b = Ui.iconButton(c, p.icon(), p.title(), v -> togglePanel(p));
        Tut.tag(b, "rail:" + p.getClass().getSimpleName().replace("Panel", "").toLowerCase());
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) b.getLayoutParams();
        lp.setMargins(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        rail.addView(b);
        railButtons.add(b);
        railPanels.add(p);
    }

    private LinearLayout buildTools(Context c, boolean vertical) {
        LinearLayout t = vertical ? Ui.column(c) : Ui.row(c);
        if (vertical) {
            t.setBackground(Ui.rounded(0xE6181A1E, Ui.dp(c, 12), Ui.BORDER, 1));
            t.setPadding(Ui.dp(c, 3), Ui.dp(c, 4), Ui.dp(c, 3), Ui.dp(c, 4));
        } else {
            t.setBackgroundColor(0xF0181A1E);
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6), Ui.dp(c, 6));
            t.setGravity(Gravity.CENTER_VERTICAL);
        }
        t.setClickable(true);

        // add (accent)
        ImageView add = Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_element", "Add element"), v -> panels.toggle(addItem));
        add.setBackground(Ui.ripple(Ui.ACCENT, Ui.dp(c, 10)));
        Tut.tag(add, "tool:add");
        t.addView(add);
        t.addView(gap(c, vertical));

        addTool(c, t, "grid", R.drawable.ic_s2_touch, Tr.t("ui_tool_pan", "Select / pan camera"));
        addTool(c, t, "move", R.drawable.ic_s2_move, Tr.t("ui_tool_move", "Move"));
        addTool(c, t, "scale", R.drawable.ic_s2_scale, Tr.t("ui_tool_scale", "Resize"));
        addTool(c, t, "rotate", R.drawable.ic_s2_rotate, Tr.t("ui_tool_rotate", "Rotate"));

        // axis lock (only meaningful for move/scale)
        axisGroup = vertical ? Ui.column(c) : Ui.row(c);
        TextView axisBtn = Ui.text(c, axisLabel(), 13, Ui.TEXT);
        axisBtn.setTypeface(Typeface.DEFAULT_BOLD);
        axisBtn.setGravity(Gravity.CENTER);
        int size = Ui.dp(c, Ui.buttonDp(c));
        axisBtn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        axisBtn.setBackground(Ui.toggleBg(c));
        axisBtn.setOnClickListener(v -> {
            axis = axis.equals("none") ? "x" : (axis.equals("x") ? "y" : "none");
            axisBtn.setText(axisLabel());
            axisBtn.setSelected(!axis.equals("none"));
            Bridge.gl(() -> Bridge.controls().actionSetAxisLock(axis));
            Ui.toast(c, axis.equals("none") ? Tr.t("noLock", "Free movement") : (axis.equals("x") ? Tr.t("xLock", "Lock X") : Tr.t("yLock", "Lock Y")));
        });
        axisBtn.setOnLongClickListener(v -> { Ui.toast(c, Tr.t("ui_axis_hint", "Axis lock: free / X / Y")); return true; });
        axisGroup.addView(axisBtn);
        t.addView(axisGroup);

        t.addView(gap(c, vertical));
        t.addView(Ui.iconButton(c, R.drawable.ic_s2_copy, Tr.t("ui_duplicate", "Duplicate"), v -> duplicateSelected()));
        lockBtn = Ui.iconButton(c, R.drawable.ic_s2_lock_open, Tr.t("ui_lock", "Lock / unlock"), v -> toggleLock());
        t.addView(lockBtn);
        ImageView del = Ui.iconButton(c, R.drawable.ic_s2_delete, Tr.t("delete", "Delete"), v -> deleteSelected());
        del.setImageTintList(ColorStateList.valueOf(0xFFFF8A80));
        t.addView(del);
        t.addView(Ui.iconButton(c, R.drawable.ic_s2_camera, Tr.t("ui_center_camera", "Center camera"), v -> Bridge.gl(() -> Bridge.editor().centerCamera())));
        return t;
    }

    private View gap(Context c, boolean vertical) {
        View v = new View(c);
        v.setBackgroundColor(Ui.BORDER);
        int s = Ui.dp(c, 1);
        LinearLayout.LayoutParams lp = vertical ? new LinearLayout.LayoutParams(Ui.dp(c, 24), s) : new LinearLayout.LayoutParams(s, Ui.dp(c, 24));
        if (vertical) lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        else lp.setMargins(Ui.dp(c, 5), 0, Ui.dp(c, 5), 0);
        lp.gravity = Gravity.CENTER;
        v.setLayoutParams(lp);
        return v;
    }

    private String axisLabel() {
        return axis.equals("none") ? "XY" : axis.toUpperCase();
    }

    private void addTool(Context c, LinearLayout t, String mode, int icon, String label) {
        ImageView b = Ui.iconButton(c, icon, label, v -> setTool(mode));
        b.setTag(mode);
        Tut.tag(b, "tool:" + mode);
        t.addView(b);
        toolButtons.add(b);
    }

    private void setTool(String mode) {
        Bridge.query(() -> Bridge.controls().actionSetTouchMode(mode), ok -> {
            tool = ok ? mode : "grid";
            if (!ok) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
            updateToolSelection();
        });
    }

    private void updateToolSelection() {
        for (ImageView b : toolButtons) b.setSelected(tool.equals(b.getTag()));
        if (axisGroup != null) axisGroup.setAlpha(tool.equals("move") || tool.equals("scale") ? 1f : 0.35f);
    }

    private void updateRailSelection() {
        PanelHost.Panel cur = panels.current();
        for (int i = 0; i < railButtons.size(); i++) railButtons.get(i).setSelected(railPanels.get(i) == cur);
    }

    public void togglePanel(PanelHost.Panel p) {
        panels.toggle(p);
    }

    // =============================================================== scene tabs

    public void refreshTabs() {
        if (tabsRow == null || !Bridge.ready()) return;
        Bridge.query(() -> {
            ArrayList<String> names = new ArrayList<>();
            TestApp app = Bridge.app();
            for (LibgdxEditor e : app.editors) names.add(e.getScene());
            names.add("\u0000" + app.getEditor().getScene());
            return names;
        }, names -> {
            Context c = activity;
            tabsRow.removeAllViews();
            String current = names.remove(names.size() - 1).substring(1);
            if (titleView != null) titleView.setText(current);
            for (int i = 0; i < names.size(); i++) {
                final int index = i;
                String n = names.get(i);
                boolean sel = n.equals(current);
                LinearLayout chip = Ui.row(c);
                chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
                chip.setBackground(sel ? Ui.rounded(Ui.ACCENT, Ui.dp(c, 16)) : Ui.rounded(Ui.SURFACE_2, Ui.dp(c, 16), Ui.BORDER, 1));
                ImageView ic = Ui.smallIcon(c, R.drawable.ic_s2_scenes, Ui.TEXT, 15);
                chip.addView(ic);
                TextView t = Ui.text(c, n, 13, Ui.TEXT);
                t.setPadding(Ui.dp(c, 5), 0, Ui.dp(c, 2), 0);
                if (sel) t.setTypeface(Typeface.DEFAULT_BOLD);
                chip.addView(t);
                if (names.size() > 1) {
                    ImageView x = Ui.smallIcon(c, R.drawable.ic_s2_close, Ui.TEXT_2, 16);
                    x.setPadding(Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3), Ui.dp(c, 3));
                    LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
                    x.setLayoutParams(xl);
                    x.setOnClickListener(v -> Bridge.query(() -> Bridge.controls().actionCloseSceneTab(index), ok -> {
                        if (!ok) Ui.toast(c, Tr.t("closeAllScenes", "You can't close all scenes"));
                    }));
                    chip.addView(x);
                } else chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6));
                chip.setOnClickListener(v -> Bridge.gl(() -> Bridge.app().setCurrentEditor(index)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 32));
                lp.setMarginEnd(Ui.dp(c, 6));
                tabsRow.addView(chip, lp);
            }
            // scenes menu
            LinearLayout menuChip = Ui.row(c);
            menuChip.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 10), 0);
            menuChip.setBackground(Ui.ripple(Ui.SURFACE_2, Ui.dp(c, 16)));
            menuChip.addView(Ui.smallIcon(c, R.drawable.ic_s2_add, Ui.TEXT, 16));
            TextView mt = Ui.text(c, Tr.t("scenes", "Scenes"), 13, Ui.TEXT);
            mt.setPadding(Ui.dp(c, 4), 0, 0, 0);
            menuChip.addView(mt);
            menuChip.setOnClickListener(v -> showScenesMenu(menuChip));
            tabsRow.addView(menuChip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(c, 32)));
        });
    }

    private void showScenesMenu(View anchor) {
        Bridge.query(() -> Bridge.controls().listScenes(), scenes -> {
            String cur = titleView.getText().toString();
            IconMenu m = new IconMenu(activity);
            m.add(R.drawable.ic_s2_add, Ui.SUCCESS, Tr.t("newScene", "New scene"), () -> sceneDialog(ControlLayer.SceneAction.CREATE, "Scene"));
            m.add(R.drawable.ic_s2_copy, Ui.ACCENT, Tr.t("copyScene", "Copy scene"), () -> sceneDialog(ControlLayer.SceneAction.COPY, cur + "_copy"));
            m.add(R.drawable.ic_s2_edit, 0xFFFFB74D, Tr.t("renameScene", "Rename scene"), () -> {
                if (cur.equalsIgnoreCase("scene1")) Ui.toast(activity, Tr.t("renameMainScene", "The main scene can't be renamed"));
                else sceneDialog(ControlLayer.SceneAction.RENAME, cur);
            });
            m.add(R.drawable.ic_s2_delete, 0xFFFF8A80, Tr.t("deleteScene", "Delete scene"), () -> {
                if (cur.equalsIgnoreCase("scene1")) Ui.toast(activity, Tr.t("deleteMainScene", "The main scene can't be deleted"));
                else Dialogs.confirm(activity, Tr.t("deleteScene", "Delete scene"), Tr.t("areYouSure", "Are you sure?") + "\n" + cur, Tr.t("delete", "Delete"),
                        () -> Bridge.query(() -> Bridge.controls().actionDeleteCurrentScene(), err -> { if (err != null) Ui.toast(activity, err); }));
            });
            m.divider();
            for (String name : scenes)
                m.add(name.equals(cur) ? R.drawable.ic_s2_check : R.drawable.ic_s2_scenes, name.equals(cur) ? Ui.ACCENT : Ui.TEXT_2,
                        Tr.t("openScene", "Open") + ": " + name, () -> Bridge.gl(() -> Bridge.controls().actionOpenScene(name)));
            m.show(anchor);
        });
    }

    private void sceneDialog(ControlLayer.SceneAction action, String initial) {
        String title = action == ControlLayer.SceneAction.CREATE ? Tr.t("newScene", "New scene")
                : action == ControlLayer.SceneAction.COPY ? Tr.t("copyScene", "Copy scene") : Tr.t("renameScene", "Rename scene");
        Dialogs.input(activity, title, Tr.t("name", "Name") + " (A-Z, 0-9, _)", initial, Dialogs::checkName,
                name -> Bridge.query(() -> Bridge.controls().applySceneAction(name, action), err -> {
                    if (err != null) {
                        Ui.toast(activity, err);
                        sceneDialog(action, name);
                    }
                }));
    }

    // =============================================================== actions

    private void exitProject() {
        Dialogs.confirm(activity, Tr.t("exit", "Exit"), Tr.t("exitConfirm", "Close this project and go back?"), Tr.t("exit", "Exit"), () -> {
            panels.hide();
            Bridge.gl(() -> Bridge.app().closeProject());
        });
    }

    public void save() {
        Bridge.gl(() -> {
            Bridge.controls().actionSave();
            Ui.toast(activity, Tr.t("saved", "Saved"));
        });
    }

    private void duplicateSelected() {
        Bridge.query(() -> {
            com.badlogic.gdx.scenes.scene2d.Actor sel = Bridge.editor().getSelectedActor();
            if (sel == null) return false;
            com.star4droid.star2d.editor.ui.ItemActions.duplicate(Bridge.app(), sel);
            return true;
        }, ok -> { if (!ok) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first")); else elements.refresh(); });
    }

    private void toggleLock() {
        Bridge.query(() -> Bridge.controls().actionToggleLock(), locked -> {
            if (locked == null) Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
            else {
                updateLockIcon(locked);
                Ui.toast(activity, locked ? Tr.t("ui_locked", "Locked") : Tr.t("ui_unlocked", "Unlocked"));
            }
        });
    }

    private void updateLockIcon(boolean locked) {
        if (lockBtn == null) return;
        lockBtn.setImageResource(locked ? R.drawable.ic_s2_lock : R.drawable.ic_s2_lock_open);
        lockBtn.setSelected(locked);
    }

    private void deleteSelected() {
        Bridge.query(() -> {
            com.badlogic.gdx.scenes.scene2d.Actor sel = Bridge.editor().getSelectedActor();
            return sel == null ? null : sel.getName();
        }, name -> {
            if (name == null) {
                Ui.toast(activity, Tr.t("ui_select_first", "Select an element first"));
                return;
            }
            Dialogs.confirm(activity, Tr.t("delete", "Delete"), Tr.t("areYouSure", "Are you sure?") + "\n" + name,
                    Tr.t("delete", "Delete"), () -> Bridge.gl(() -> Bridge.controls().actionDeleteSelected()));
        });
    }

    private void sceneColor() {
        Bridge.query(() -> {
            Bridge.controls().beginSceneColor();
            return Bridge.controls().getSceneColorHex();
        }, hex -> ColorPickerDialog.show(activity, Tr.t("ui_scene_color", "Scene background color"), hex,
                preview -> Bridge.gl(() -> Bridge.controls().previewSceneColor(preview)),
                done -> Bridge.gl(() -> {
                    if (done == null) Bridge.controls().cancelSceneColor();
                    else {
                        Bridge.controls().previewSceneColor(done);
                        Bridge.controls().finishSceneColor();
                    }
                })));
    }

    // =============================================================== play

    public void play() {
        panels.hide();
        Context c = activity;
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 8));
        playProgress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        playProgress.setIndeterminate(true);
        box.addView(playProgress, Ui.matchWrap());
        playStatus = Ui.text(c, Tr.t("generatingCode", "Generating code..."), 13, Ui.TEXT_2);
        playStatus.setPadding(0, Ui.dp(c, 10), 0, 0);
        playStatus.setTypeface(Typeface.MONOSPACE);
        playStatus.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(c);
        sv.addView(playStatus);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playDialog = new AlertDialog.Builder(c)
                .setTitle(Tr.t("compiler", "Building your game"))
                .setView(box)
                .setCancelable(false)
                .create();
        playDialog.show();
        Bridge.gl(() -> Bridge.controls().actionPlay());
    }

    @Override
    public void onPlayStatus(int state, String text) {
        Ui.ui(() -> {
            if (playDialog == null) return;
            switch (state) {
                case 0:
                    if (text != null && !text.isEmpty()) playStatus.setText(text);
                    break;
                case 1:
                    playStatus.setText(text);
                    playStatus.setTextColor(0xFFFF8A80);
                    playProgress.setVisibility(GONE);
                    playDialog.setTitle(Tr.t("ui_build_failed", "Build failed"));
                    break;
                case 2:
                    if (playProgress.getVisibility() == GONE || playStatus.getCurrentTextColor() == 0xFFFF8A80) {
                        // error: let the user read / copy it
                        final String err = playStatus.getText().toString();
                        playDialog.setButton(AlertDialog.BUTTON_POSITIVE, Tr.t("close", "Close"), (d, w) -> d.dismiss());
                        playDialog.setButton(AlertDialog.BUTTON_NEUTRAL, Tr.t("copy", "Copy"), (d, w) -> Dialogs.copy(activity, err));
                        playDialog.setButton(AlertDialog.BUTTON_NEGATIVE, Tr.t("ui_ask_ai", "Ask AI to fix"), (d, w) -> {
                            panels.show(ai);
                            ai.askToFix(err);
                        });
                        playDialog.dismiss();
                        AlertDialog d = new AlertDialog.Builder(activity)
                                .setTitle(Tr.t("ui_build_failed", "Build failed"))
                                .setMessage(err)
                                .setPositiveButton(Tr.t("close", "Close"), null)
                                .setNeutralButton(Tr.t("copy", "Copy"), (x, w) -> Dialogs.copy(activity, err))
                                .setNegativeButton(Tr.t("ui_ask_ai", "Ask AI to fix"), (x, w) -> {
                                    panels.show(ai);
                                    ai.askToFix(err);
                                })
                                .create();
                        d.show();
                        playDialog = null;
                    }
                    break;
                case 3:
                    playDialog.dismiss();
                    playDialog = null;
                    break;
            }
        });
    }

    @Override
    public void onPlayStateChanged(boolean isPlaying) {
        Ui.ui(() -> {
            playing = isPlaying;
            if (playDialog != null && isPlaying) {
                playDialog.dismiss();
                playDialog = null;
            }
            updateVisibility();
            if (!isPlaying) refreshAll();
        });
    }

    // =============================================================== hooks from the editor

    @Override
    public void onSelectionChanged() {
        Ui.ui(() -> {
            elements.onEditorChanged();
            properties.onSelectionChanged();
            events.onSelectionChanged();
            Bridge.query(() -> {
                com.badlogic.gdx.scenes.scene2d.Actor a = Bridge.editor().getSelectedActor();
                return a != null && "true".equals(com.star4droid.star2d.Helpers.PropertySet.getPropertySet(a).getString("lock"));
            }, this::updateLockIcon);
        });
    }

    @Override
    public void onUndoRedoChanged() {
        Ui.ui(() -> {
            refreshUndoRedo();
            properties.onSelectionChanged();
            elements.onEditorChanged();
        });
    }

    private void refreshUndoRedo() {
        if (!Bridge.ready() || undoBtn == null) return;
        Bridge.query(() -> new boolean[]{Bridge.editor().canUndo(), Bridge.editor().canRedo()}, r -> {
            undoBtn.setAlpha(r[0] ? 1f : 0.35f);
            redoBtn.setAlpha(r[1] ? 1f : 0.35f);
        });
    }

    @Override
    public void onScenesChanged() {
        Ui.ui(this::refreshAll);
    }

    @Override
    public void onIndexing(boolean indexing) {
        Ui.ui(() -> {
            this.indexing = indexing;
            if (indexingChip != null) indexingChip.setVisibility(indexing ? VISIBLE : GONE);
        });
    }

    @Override
    public void onShowText(String title, String text) {
        Ui.ui(() -> Dialogs.longText(activity, title, text));
    }

    @Override
    public void onEditorChanged() {
        Ui.ui(() -> {
            projectOpen = true;
            updateVisibility();
            refreshAll();
            // keep the chosen tool for the new editor
            Bridge.gl(() -> Bridge.controls().actionSetTouchMode(tool));
        });
    }

    @Override
    public boolean openVisual(String file, String hints) {
        if (!host.openBlocks(file, hints)) return false;
        return true;
    }

    @Override
    public void onBackKey() {
        Ui.ui(() -> {
            if (!onBack()) exitProject();
        });
    }

    @Override
    public void showText(String text) {
        Ui.ui(() -> {
            AlertDialog d = Dialogs.longText(activity, Tr.t("ui_runtime_error", "Game error"), text);
            d.setButton(AlertDialog.BUTTON_NEGATIVE, Tr.t("ui_ask_ai", "Ask AI to fix"), (x, w) -> {
                panels.show(ai);
                ai.askToFix(text);
            });
        });
    }

    /** Called by the activity when the project is closed. */
    public void onProjectClosed() {
        projectOpen = false;
        panels.hide();
        updateVisibility();
    }

    public void refreshAll() {
        refreshTabs();
        refreshUndoRedo();
        elements.onEditorChanged();
        properties.onSelectionChanged();
        events.onSelectionChanged();
        joints.refresh();
        variables.refresh();
        files.onEditorChanged();
    }

    private void updateVisibility() {
        setVisibility(projectOpen && !playing ? VISIBLE : GONE);
    }

    public boolean isPlaying() { return playing; }
    public boolean isProjectOpen() { return projectOpen; }
    public boolean isIndexing() { return indexing; }
    public boolean isBuilding() { return playDialog != null; }

    /** Back key: close the panel first. Returns true when consumed. */
    public boolean onBack() {
        if (getVisibility() != VISIBLE) return false;
        if (com.star4droid.star2d.ui.tutorial.Tutorial.onBack(activity)) return true;
        if (panels.current() != null) {
            if (panels.current() instanceof FilesPanel && files.goUp()) return true;
            panels.hide();
            return true;
        }
        return false;
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        post(this::build);
    }

    /** True when the Gdx context is alive. */
    public static boolean gdxReady() {
        return Gdx.app != null;
    }
}
