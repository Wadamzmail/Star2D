package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.editor.ui.ItemActions;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.ArrayList;
import java.util.Locale;

/** Scene hierarchy: select, show/hide, lock, rename, duplicate, reorder, delete. */
public class ElementsPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private View root;
    private RecyclerView list;
    private TextView empty;
    private EditText search;
    private final ArrayList<Bridge.ItemInfo> all = new ArrayList<>();
    private final ArrayList<Bridge.ItemInfo> shown = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private boolean pending;

    public ElementsPanel(EditorShell shell) {
        this.shell = shell;
    }

    @Override public String title() { return Tr.t("bodiesList", "Elements"); }
    @Override public int icon() { return R.drawable.ic_s2_layers; }

    @Override
    public View[] headerActions(Context c) {
        return new View[]{Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("ui_add_element", "Add element"), v -> shell.panels.show(shell.addItem))};
    }

    @Override
    public View view(Context c) {
        if (root != null) return root;
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8), 0);
        search = Ui.input(c, "", Tr.t("ui_search", "Search..."));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { filter(); }
        });
        col.addView(search, Ui.matchWrap());
        empty = Ui.text(c, Tr.t("ui_no_elements", "No elements yet.\nTap + to add your first one."), 14, Ui.TEXT_3);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, Ui.dp(c, 30), 0, 0);
        col.addView(empty, Ui.matchWrap());
        list = new RecyclerView(c);
        list.setLayoutManager(new LinearLayoutManager(c));
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root = col;
        return root;
    }

    @Override public void onShown() { refresh(); }

    /** Selection / undo / scene changed. */
    public void onEditorChanged() {
        if (shell.panels.isShowing(this)) refresh();
    }

    public void refresh() {
        if (!Bridge.ready() || pending) return;
        pending = true;
        Bridge.query(Bridge::items, items -> {
            pending = false;
            all.clear();
            all.addAll(items);
            filter();
        });
    }

    private void filter() {
        if (list == null) return;
        String q = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        shown.clear();
        for (Bridge.ItemInfo i : all)
            if (q.isEmpty() || i.name.toLowerCase(Locale.ROOT).contains(q)) shown.add(i);
        adapter.notifyDataSetChanged();
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
    }

    public static int typeIcon(String type) {
        if (type == null) return R.drawable.ic_s2_square;
        switch (type) {
            case "Box": return R.drawable.ic_s2_square;
            case "Circle": return R.drawable.ic_s2_circle;
            case "Text": return R.drawable.ic_s2_text;
            case "Joystick": return R.drawable.ic_s2_gamepad;
            case "Progress": return R.drawable.ic_s2_progress;
            case "Custom": return R.drawable.ic_s2_collider;
            case "Particle": return R.drawable.ic_s2_particle;
            case "Camera": return R.drawable.ic_s2_videocam;
            case "Map": return R.drawable.ic_s2_map;
            case "Light": return R.drawable.ic_s2_light;
        }
        return R.drawable.ic_s2_square;
    }

    public static int typeColor(String type) {
        if (type == null) return Ui.ACCENT;
        switch (type) {
            case "Circle": return 0xFF26C6DA;
            case "Text": return 0xFFFFCA28;
            case "Joystick": return 0xFFAB47BC;
            case "Progress": return 0xFF66BB6A;
            case "Custom": return 0xFFFF7043;
            case "Particle": return 0xFFEC407A;
            case "Camera": return 0xFF78909C;
            case "Map": return 0xFF8D6E63;
            case "Light": return 0xFFFFEE58;
        }
        return Ui.ACCENT;
    }

    private void select(String name) {
        Bridge.gl(() -> {
            Actor a = Bridge.actor(name);
            if (a != null) Bridge.editor().selectActor(a);
        });
    }

    private void menu(View anchor, Bridge.ItemInfo info) {
        Context c = anchor.getContext();
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(0, 1, 0, Tr.t("properties", "Properties"));
        m.getMenu().add(0, 2, 1, Tr.t("events", "Events"));
        m.getMenu().add(0, 3, 2, Tr.t("rename", "Rename"));
        m.getMenu().add(0, 4, 3, Tr.t("ui_duplicate", "Duplicate"));
        m.getMenu().add(0, 5, 4, Tr.t("ui_bring_forward", "Bring forward"));
        m.getMenu().add(0, 6, 5, Tr.t("ui_send_backward", "Send backward"));
        m.getMenu().add(0, 7, 6, Tr.t("delete", "Delete"));
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: select(info.name); shell.panels.show(shell.properties); break;
                case 2: select(info.name); shell.panels.show(shell.events); break;
                case 3:
                    Dialogs.input(c, Tr.t("rename", "Rename"), Tr.t("name", "Name"), info.name, Dialogs::checkName, v ->
                            Bridge.query(() -> {
                                Actor a = Bridge.actor(info.name);
                                return a == null ? "?" : ItemActions.rename(Bridge.app(), a, v);
                            }, err -> { if (err != null) Ui.toast(c, err); refresh(); }));
                    break;
                case 4:
                    Bridge.gl(() -> {
                        try { ItemActions.duplicate(Bridge.app(), Bridge.actor(info.name)); } catch (Exception e) { throw new RuntimeException(e); }
                    });
                    refreshLater();
                    break;
                case 5: Bridge.gl(() -> ItemActions.changeZ(Bridge.app(), Bridge.actor(info.name), 1)); refreshLater(); break;
                case 6: Bridge.gl(() -> ItemActions.changeZ(Bridge.app(), Bridge.actor(info.name), -1)); refreshLater(); break;
                case 7:
                    Dialogs.confirm(c, Tr.t("delete", "Delete"), Tr.t("areYouSure", "Are you sure?") + "\n" + info.name, Tr.t("delete", "Delete"),
                            () -> Bridge.gl(() -> {
                                Actor a = Bridge.actor(info.name);
                                if (a != null) ItemActions.delete(Bridge.app(), a);
                            }));
                    refreshLater();
                    break;
            }
            return true;
        });
        m.show();
    }

    private void refreshLater() {
        if (list != null) list.postDelayed(this::refresh, 150);
    }

    // ------------------------------------------------------------------ adapter

    private class Holder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final ImageView icon, eye, lock, more;
        final TextView name, type;
        final View indent;
        Holder(LinearLayout row, View indent, ImageView icon, TextView name, TextView type, ImageView eye, ImageView lock, ImageView more) {
            super(row);
            this.row = row; this.indent = indent; this.icon = icon; this.name = name; this.type = type;
            this.eye = eye; this.lock = lock; this.more = more;
        }
    }

    private class Adapter extends RecyclerView.Adapter<Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            LinearLayout row = Ui.row(c);
            row.setPadding(Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 2), Ui.dp(c, 4));
            row.setMinimumHeight(Ui.dp(c, 48));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View indent = new View(c);
            row.addView(indent, new LinearLayout.LayoutParams(0, 1));
            ImageView icon = Ui.smallIcon(c, R.drawable.ic_s2_square, Ui.ACCENT, 22);
            icon.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 7)));
            icon.setPadding(Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5), Ui.dp(c, 5));
            icon.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 32), Ui.dp(c, 32)));
            row.addView(icon);
            LinearLayout texts = Ui.column(c);
            texts.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 4), 0);
            TextView name = Ui.text(c, "", 15, Ui.TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(1);
            TextView type = Ui.text(c, "", 11.5f, Ui.TEXT_3);
            texts.addView(name);
            texts.addView(type);
            row.addView(texts, Ui.weight(1));
            ImageView eye = small(c, R.drawable.ic_s2_visible);
            ImageView lock = small(c, R.drawable.ic_s2_lock_open);
            ImageView more = small(c, R.drawable.ic_s2_more);
            row.addView(eye);
            row.addView(lock);
            row.addView(more);
            return new Holder(row, indent, icon, name, type, eye, lock, more);
        }

        private ImageView small(Context c, int res) {
            ImageView i = Ui.smallIcon(c, res, Ui.TEXT_2, 20);
            int s = Ui.dp(c, 38);
            i.setLayoutParams(new LinearLayout.LayoutParams(s, s));
            int p = Ui.dp(c, 9);
            i.setPadding(p, p, p, p);
            i.setBackground(Ui.ripple(0, Ui.dp(c, 19)));
            return i;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Bridge.ItemInfo i = shown.get(position);
            Context c = h.row.getContext();
            h.indent.getLayoutParams().width = Ui.dp(c, 16 * i.depth);
            h.indent.requestLayout();
            h.icon.setImageResource(typeIcon(i.type));
            h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(typeColor(i.type)));
            h.name.setText(i.name);
            h.name.setAlpha(i.visible ? 1f : 0.5f);
            String typeLabel = i.type == null ? "" : Tr.t("ui_type_" + i.type.toLowerCase(Locale.ROOT), i.type);
            h.type.setText(i.depth > 0 ? "↳ " + i.parent + " · " + typeLabel : typeLabel);
            h.row.setBackground(i.selected ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 8), Ui.ACCENT, Ui.dp(c, 1)) : Ui.ripple(0, Ui.dp(c, 8)));
            h.eye.setImageResource(i.visible ? R.drawable.ic_s2_visible : R.drawable.ic_s2_visible_off);
            h.lock.setImageResource(i.locked ? R.drawable.ic_s2_lock : R.drawable.ic_s2_lock_open);
            h.lock.setImageTintList(android.content.res.ColorStateList.valueOf(i.locked ? Ui.WARNING : Ui.TEXT_3));
            h.row.setOnClickListener(v -> select(i.name));
            h.row.setOnLongClickListener(v -> { menu(h.more, i); return true; });
            h.more.setOnClickListener(v -> menu(h.more, i));
            h.eye.setOnClickListener(v -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(i.name);
                    if (a != null) ItemActions.toggleVisible(Bridge.app(), a, i.name);
                });
                refreshLater();
            });
            h.lock.setOnClickListener(v -> {
                Bridge.gl(() -> {
                    Actor a = Bridge.actor(i.name);
                    if (a != null) ItemActions.toggleLock(Bridge.app(), a, i.name);
                });
                refreshLater();
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
