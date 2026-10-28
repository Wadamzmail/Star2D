package com.star4droid.star2d.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.prefab.PrefabEditorSync;
import com.star4droid.star2d.prefab.PrefabOps;
import com.star4droid.star2d.prefab.PrefabStore;

import java.util.List;
import java.util.concurrent.Callable;

/** Android UI helpers for prefabs: the prefab browser, pickers and the instance operations (apply / revert / unpack / link). */
public final class PrefabUi {
    private PrefabUi() {}

    public static final int COLOR = 0xFF4FC3F7;
    public static final int ICON = R.drawable.ic_s2_widgets;

    public interface Pick { void on(String path); }
    public interface Done<T> { void on(T value); }

    /** "enemies/Slime" -> "Slime". */
    public static String shortName(String path) {
        if (path == null) return "";
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    static String message(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        return m == null || m.isEmpty() ? t.toString() : m;
    }

    // ================================================================ browser

    /** A navigable list of the project's prefabs (folders can be opened). */
    public static class Browser {
        private final Context c;
        private final String projectPath;
        private final boolean foldersOnly;
        private final Pick onPick;
        private final LinearLayout root;
        private final LinearLayout crumbs, list;
        private String folder = "";

        public Browser(Context c, String projectPath, boolean foldersOnly, Pick onPick) {
            this.c = c;
            this.projectPath = projectPath;
            this.foldersOnly = foldersOnly;
            this.onPick = onPick;
            root = Ui.column(c);
            HorizontalScrollView hs = new HorizontalScrollView(c);
            hs.setHorizontalScrollBarEnabled(false);
            crumbs = Ui.row(c);
            hs.addView(crumbs);
            root.addView(hs, Ui.matchWrap());
            list = Ui.column(c);
            root.addView(list, Ui.matchWrap());
            reload();
        }

        public View view() { return root; }

        /** Current folder ("" = the prefabs folder itself). */
        public String folder() { return folder; }

        public void open(String f) {
            folder = f == null ? "" : f;
            reload();
        }

        private void reload() {
            crumbs.removeAllViews();
            String[] parts = folder.isEmpty() ? new String[0] : folder.split("/");
            addCrumb(Tr.t("prefabs", "Prefabs"), "", parts.length == 0);
            StringBuilder acc = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (acc.length() > 0) acc.append('/');
                acc.append(parts[i]);
                crumbs.addView(Ui.text(c, "›", 14, Ui.TEXT_3));
                addCrumb(parts[i], acc.toString(), i == parts.length - 1);
            }
            list.removeAllViews();
            List<PrefabStore.Entry> es;
            try {
                es = PrefabStore.children(new Project(projectPath), folder);
            } catch (Throwable t) {
                es = new java.util.ArrayList<>();
            }
            int shown = 0;
            for (PrefabStore.Entry e : es) {
                if (foldersOnly && !e.isFolder) continue;
                shown++;
                LinearLayout row = PanelHost.listRow(c, e.isFolder ? R.drawable.ic_s2_folder : ICON, e.isFolder ? 0xFFFFCA28 : COLOR, e.name, null);
                final PrefabStore.Entry fe = e;
                row.setOnClickListener(v -> {
                    if (fe.isFolder) open(fe.path);
                    else if (onPick != null) onPick.on(fe.path);
                });
                list.addView(row, Ui.matchWrap());
            }
            if (shown == 0) {
                TextView t = Ui.text(c, foldersOnly ? Tr.t("prefab_no_folders", "No folders here.") : Tr.t("prefab_none", "No prefabs yet. Create one in Files, or use “Save as prefab” on an element."), 13, Ui.TEXT_3);
                t.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
                list.addView(t, Ui.matchWrap());
            }
        }

        private void addCrumb(String label, String target, boolean last) {
            TextView t = Ui.text(c, label, 13.5f, last ? Ui.TEXT : Ui.ACCENT);
            t.setTypeface(last ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
            t.setOnClickListener(v -> open(target));
            crumbs.addView(t);
        }
    }

    private static View framed(Context c, View content) {
        ScrollView sv = new ScrollView(c);
        sv.addView(content);
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), 0);
        int h = Math.min(Ui.dp(c, 380), (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f));
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        return box;
    }

    /** Dialog with the prefab list; calls back with the chosen prefab path. */
    public static void pickPrefab(EditorShell shell, String title, Pick onPick) {
        Context c = shell.activity();
        AlertDialog[] ref = new AlertDialog[1];
        Browser b = new Browser(c, shell.projectPath(), false, p -> {
            if (ref[0] != null) ref[0].dismiss();
            onPick.on(p);
        });
        ref[0] = Dialogs.custom(c, title, framed(c, b.view()), null, null);
    }

    /** Dialog to choose a folder inside prefabs/ ("" = the prefabs folder). */
    public static void pickFolder(EditorShell shell, String start, Pick onPick) {
        Context c = shell.activity();
        Browser b = new Browser(c, shell.projectPath(), true, null);
        b.open(start);
        Dialogs.custom(c, Tr.t("prefab_choose_folder", "Choose a folder"), framed(c, b.view()), Tr.t("prefab_use_folder", "Use this folder"), () -> onPick.on(b.folder()));
    }

    // ================================================================ running operations

    /** Runs prefab work (on the GL thread, with the open scene saved first and reloaded after). Errors are shown as a message. */
    public static <T> void run(EditorShell shell, String busyText, Callable<T> work, Done<T> done) {
        if (busyText != null) Ui.toast(shell.activity(), busyText);
        PrefabEditorSync.runAsync(work, o -> {
            if (o.error != null) {
                Ui.toast(shell.activity(), message(o.error));
                shell.refreshAll();
                return;
            }
            if (done != null) done.on(o.value);
            shell.refreshAll();
        });
    }

    /** Selects an element by name once the scene finished (re)loading. */
    public static void selectWhenReady(String name, int tries) {
        if (name == null || name.isEmpty() || tries <= 0) return;
        Bridge.query(() -> {
            LibgdxEditor ed = Bridge.editor();
            if (ed == null) return true;
            if (ed.isLoading()) return false;
            Actor a = Bridge.actor(name);
            if (a == null) return false;
            ed.selectActor(a);
            return true;
        }, done -> {
            if (!done) new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> selectWhenReady(name, tries - 1), 160);
        });
    }

    /** Add a prefab instance to the current scene, at the middle of what the camera shows. */
    public static void addToScene(EditorShell shell, String prefabPath) {
        run(shell, Tr.t("prefab_adding", "Adding prefab…"), () -> {
            LibgdxEditor ed = Bridge.editor();
            float x = ed.getCamera().position.x, y = ed.getCamera().position.y;
            return PrefabOps.instantiate(ed.getProject(), ed.getScene(), prefabPath, x, y, null);
        }, r -> {
            Ui.toast(shell.activity(), Tr.t("ui_added", "Added") + ": " + shortName(prefabPath));
            if (r != null) selectWhenReady(r.root, 40);
        });
    }

    // ---------------------------------------------------------------- instance operations

    public static void apply(EditorShell shell, String rootName, String prefabPath) {
        run(shell, Tr.t("prefab_applying", "Applying to prefab…"), () -> {
            LibgdxEditor ed = Bridge.editor();
            PrefabOps.apply(ed.getProject(), ed.getScene(), rootName);
            return null;
        }, v -> {
            Ui.toast(shell.activity(), Tr.t("prefab_applied", "Applied to prefab") + ": " + shortName(prefabPath));
            selectWhenReady(rootName, 40);
        });
    }

    public static void revert(EditorShell shell, String rootName) {
        Dialogs.confirm(shell.activity(), Tr.t("prefab_revert", "Revert"), Tr.t("prefab_revert_msg", "Discard the changes of this instance and take everything from the prefab?"), Tr.t("prefab_revert", "Revert"), () ->
                run(shell, Tr.t("prefab_reverting", "Reverting…"), () -> {
                    LibgdxEditor ed = Bridge.editor();
                    PrefabOps.revert(ed.getProject(), ed.getScene(), rootName);
                    return null;
                }, v -> {
                    Ui.toast(shell.activity(), Tr.t("prefab_reverted", "Reverted to the prefab"));
                    selectWhenReady(rootName, 40);
                }));
    }

    public static void unpack(EditorShell shell, String rootName) {
        Dialogs.confirm(shell.activity(), Tr.t("prefab_unpack", "Unpack"), Tr.t("prefab_unpack_msg", "Unlink this instance from its prefab? The elements stay in the scene as normal elements."), Tr.t("prefab_unpack", "Unpack"), () ->
                run(shell, null, () -> {
                    LibgdxEditor ed = Bridge.editor();
                    PrefabOps.unpack(ed.getProject(), ed.getScene(), rootName);
                    return null;
                }, v -> {
                    Ui.toast(shell.activity(), Tr.t("prefab_unpacked", "Unpacked"));
                    selectWhenReady(rootName, 40);
                }));
    }

    /** Replace the contents of a group by an instance of a prefab. */
    public static void link(EditorShell shell, String groupName) {
        pickPrefab(shell, Tr.t("prefab_link", "Link to prefab…"), path ->
                Dialogs.confirm(shell.activity(), Tr.t("prefab_link", "Link to prefab…"), Tr.t("prefab_link_msg", "The contents of this group will be replaced by the prefab.") + "\n" + path, Tr.t("ok", "OK"), () ->
                        run(shell, null, () -> {
                            LibgdxEditor ed = Bridge.editor();
                            PrefabOps.link(ed.getProject(), ed.getScene(), groupName, path);
                            return null;
                        }, v -> {
                            Ui.toast(shell.activity(), Tr.t("prefab_linked", "Linked to prefab") + ": " + shortName(path));
                            selectWhenReady(groupName, 40);
                        })));
    }

    /** Make a prefab from an element (and everything under it). */
    public static void saveAsPrefab(EditorShell shell, String elementName) {
        Context c = shell.activity();
        String[] folder = {""};
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 6), Ui.dp(c, 20), 0);
        EditText name = Ui.input(c, elementName, Tr.t("name", "Name"));
        box.addView(name, Ui.matchWrap());
        TextView folderText = Ui.text(c, "", 13, Ui.TEXT_2);
        Runnable showFolder = () -> folderText.setText(Tr.t("prefab_folder", "Folder") + ": " + (folder[0].isEmpty() ? "/" : "/" + folder[0]));
        showFolder.run();
        LinearLayout row = Ui.row(c);
        row.setPadding(0, Ui.dp(c, 10), 0, 0);
        row.addView(folderText, Ui.weight(1));
        row.addView(Ui.button(c, R.drawable.ic_s2_folder, Tr.t("prefab_choose_folder_short", "Choose"), false,
                v -> pickFolder(shell, folder[0], f -> { folder[0] = f; showFolder.run(); })));
        box.addView(row, Ui.matchWrap());
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(Tr.t("prefab_save_as", "Save as prefab"))
                .setView(box)
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .setNegativeButton(Tr.t("cancel", "Cancel"), null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty() || n.contains("/") || n.contains("\\")) {
                name.setError(Tr.t("ui_bad_char", "Not allowed"));
                return;
            }
            String path;
            try {
                path = PrefabStore.normalize((folder[0].isEmpty() ? "" : folder[0] + "/") + n);
            } catch (Exception e) {
                name.setError(message(e));
                return;
            }
            if (PrefabStore.exists(new Project(shell.projectPath()), path)) {
                name.setError(Tr.t("ui_name_used", "Name already used"));
                return;
            }
            d.dismiss();
            final String fp = path;
            run(shell, Tr.t("prefab_saving_as", "Creating prefab…"), () -> {
                LibgdxEditor ed = Bridge.editor();
                return PrefabOps.extract(ed.getProject(), ed.getScene(), elementName, fp);
            }, root -> {
                Ui.toast(c, Tr.t("prefab_created", "Prefab created") + ": " + fp);
                selectWhenReady(root, 40);
            });
        }));
        d.show();
    }
}
