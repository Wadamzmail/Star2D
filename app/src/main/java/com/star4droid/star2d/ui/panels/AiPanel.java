package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

/** Placeholder; replaced by the full assistant. */
public class AiPanel implements PanelHost.Panel {
    private final EditorShell shell;
    public AiPanel(EditorShell shell) { this.shell = shell; }
    @Override public String title() { return Tr.t("ui_ai", "AI assistant"); }
    @Override public int icon() { return R.drawable.ic_s2_ai; }
    @Override public View view(Context c) { return Ui.text(c, "AI", 14, Ui.TEXT); }
    public void askToFix(String error) {}
    public void showSettings() {}
}
