package com.star4droid.star2d.ui.panels;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.badlogic.gdx.Gdx;
import com.star4droid.star2d.evo.R;
import com.star4droid.star2d.ui.Bridge;
import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.EditorShell;
import com.star4droid.star2d.ui.FilePickerDialog;
import com.star4droid.star2d.ui.PanelHost;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Project assets browser (Unity-like): folder tree (landscape) or breadcrumb (portrait),
 * grid/list view with image thumbnails, search, create, import, rename, duplicate, delete.
 */
public class FilesPanel implements PanelHost.Panel {
    private final EditorShell shell;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root, tree, crumbs;
    private RecyclerView grid;
    private TextView empty;
    private EditText search;
    private File rootDir, dir;
    private boolean gridMode = true, logsMode = false;
    private String projectPath;
    private final ArrayList<File> items = new ArrayList<>();
    private final Adapter adapter = new Adapter();

    public FilesPanel(EditorShell shell) {
        this.shell = shell;
        gridMode = Ui.prefs(shell.activity()).getBoolean("files_grid", true);
    }

    @Override public String title() { return logsMode ? Tr.t("ui_logs", "Logs") : Tr.t("ui_files", "Files"); }
    @Override public int icon() { return R.drawable.ic_s2_folder; }

    @Override
    public View[] headerActions(Context c) {
        ImageView view = Ui.iconButton(c, gridMode ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid, Tr.t("ui_view", "Grid / list"), null);
        view.setOnClickListener(v -> {
            gridMode = !gridMode;
            Ui.prefs(c).edit().putBoolean("files_grid", gridMode).apply();
            view.setImageResource(gridMode ? R.drawable.ic_s2_view_list : R.drawable.ic_s2_view_grid);
            applyLayout(c);
        });
        ImageView create = Ui.iconButton(c, R.drawable.ic_s2_add, Tr.t("create", "Create"), null);
        create.setOnClickListener(v -> createMenu(c, create));
        ImageView imp = Ui.iconButton(c, R.drawable.ic_s2_upload, Tr.t("import", "Import"), v -> {
            if (dir == null) return;
            shell.host().importFiles(dir.getAbsolutePath(), this::reload);
        });
        com.star4droid.star2d.ui.tutorial.Tut.tag(imp, "files:import");
        ImageView col = Ui.iconButton(c, R.drawable.ic_s2_collections, Tr.t("ui_collections", "Collections (free assets)"), v -> {
            if (projectPath != null) com.star4droid.star2d.ui.CollectionsDialog.show(shell.activity(), new File(projectPath), this::reload);
        });
        com.star4droid.star2d.ui.tutorial.Tut.tag(col, "files:collections");
        return new View[]{col, imp, create, view};
    }

    public void onEditorChanged() {
        if (!Bridge.ready()) return;
        Bridge.query(() -> Bridge.editor().getProject().getPath(), p -> {
            if (p.equals(projectPath)) return;
            projectPath = p;
            logsMode = false;
            rootDir = new File(p);
            dir = rootDir;
            if (shell.panels.isShowing(this)) reload();
        });
    }

    /** Open a folder of the project (e.g. "images"). */
    public void openFolder(String rel) {
        if (projectPath == null) return;
        logsMode = false;
        rootDir = new File(projectPath);
        File f = new File(rootDir, rel);
        f.mkdirs();
        dir = f;
        if (root != null) reload();
    }

    public void refreshList() {
        if (root != null && dir != null) reload();
    }

    /** Show the engine logs folder (compile errors etc.). */
    public void openLogs() {
        File logs = shell.activity().getExternalFilesDir("logs");
        if (logs == null) return;
        logs.mkdirs();
        logsMode = true;
        rootDir = logs;
        dir = logs;
        if (root != null) reload();
    }

    /** Back key: go to parent folder. Returns false at root. */
    public boolean goUp() {
        if (dir == null || rootDir == null || dir.equals(rootDir)) {
            if (logsMode && projectPath != null) {
                logsMode = false;
                rootDir = new File(projectPath);
                dir = rootDir;
                reload();
                return true;
            }
            return false;
        }
        dir = dir.getParentFile();
        reload();
        return true;
    }

    @Override
    public View view(Context c) {
        if (root != null) {
            applyLayout(c);
            return root;
        }
        root = Ui.column(c);
        root.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), 0);

        LinearLayout bar = Ui.row(c);
        ImageView up = Ui.iconButton(c, R.drawable.ic_s2_arrow_back, Tr.t("back", "Up"), v -> goUp());
        up.setScaleX(Ui.isRtl(c) ? -1 : 1);
        bar.addView(up);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        crumbs = Ui.row(c);
        hs.addView(crumbs);
        bar.addView(hs, Ui.weight(1));
        root.addView(bar, Ui.matchWrap());

        search = Ui.input(c, "", Tr.t("ui_search", "Search..."));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { reload(); }
        });
        root.addView(search, Ui.margins(Ui.matchWrap(), c, 2, 2, 2, 6));

        LinearLayout body = Ui.row(c);
        body.setGravity(Gravity.TOP);
        ScrollView ts = new ScrollView(c);
        tree = Ui.column(c);
        tree.setPadding(0, 0, Ui.dp(c, 6), 0);
        ts.addView(tree);
        body.addView(ts, new LinearLayout.LayoutParams(Ui.dp(c, 160), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout right = Ui.column(c);
        empty = Ui.text(c, Tr.t("ui_empty_folder", "This folder is empty. Use Import to add images, sounds and more."), 13, Ui.TEXT_3);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10), 0);
        right.addView(empty, Ui.matchWrap());
        grid = new RecyclerView(c);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        right.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        body.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applyLayout(c);
        return root;
    }

    private void applyLayout(Context c) {
        if (grid == null) return;
        boolean land = Ui.isLandscape(c);
        ((View) tree.getParent()).setVisibility(land ? View.VISIBLE : View.GONE);
        int w = c.getResources().getDisplayMetrics().widthPixels;
        int panelW = land ? (int) (w * 0.42f) - Ui.dp(c, 170) : w;
        int span = gridMode ? Math.max(3, panelW / Ui.dp(c, 92)) : 1;
        grid.setLayoutManager(new GridLayoutManager(c, span));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onShown() {
        if (rootDir == null) onEditorChanged();
        reload();
    }

    public void reload() {
        if (root == null || dir == null) return;
        Context c = root.getContext();
        if (!dir.exists()) dir = rootDir;
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        items.clear();
        if (q.isEmpty()) {
            File[] l = dir.listFiles();
            if (l != null) items.addAll(Arrays.asList(l));
        } else collect(dir, q, items, 0);
        java.util.Collections.sort(items, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        // breadcrumb
        crumbs.removeAllViews();
        ArrayList<File> chain = new ArrayList<>();
        for (File f = dir; f != null; f = f.getParentFile()) {
            chain.add(0, f);
            if (f.equals(rootDir)) break;
        }
        for (int i = 0; i < chain.size(); i++) {
            File f = chain.get(i);
            TextView t = Ui.text(c, i == 0 ? (logsMode ? "logs" : Tr.t("ui_project", "Project")) : f.getName(), 13.5f, i == chain.size() - 1 ? Ui.TEXT : Ui.ACCENT);
            t.setTypeface(i == chain.size() - 1 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
            t.setOnClickListener(v -> { dir = f; reload(); });
            crumbs.addView(t);
            if (i < chain.size() - 1) crumbs.addView(Ui.text(c, "›", 14, Ui.TEXT_3));
        }
        buildTree(c);
    }

    private void collect(File d, String q, ArrayList<File> out, int depth) {
        File[] l = d.listFiles();
        if (l == null || depth > 6 || out.size() > 300) return;
        for (File f : l) {
            if (f.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(f);
            if (f.isDirectory()) collect(f, q, out, depth + 1);
        }
    }

    private void buildTree(Context c) {
        if (tree == null || ((View) tree.getParent()).getVisibility() != View.VISIBLE) return;
        tree.removeAllViews();
        addTreeNode(c, rootDir, 0);
    }

    private void addTreeNode(Context c, File f, int depth) {
        boolean open = dir.getAbsolutePath().startsWith(f.getAbsolutePath());
        boolean current = dir.equals(f);
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, 4 + depth * 12), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6));
        r.setBackground(current ? Ui.rounded(Ui.ACCENT_SOFT, Ui.dp(c, 6)) : Ui.ripple(0, Ui.dp(c, 6)));
        r.addView(Ui.smallIcon(c, R.drawable.ic_s2_folder, folderColor(f.getName()), 16));
        TextView t = Ui.text(c, depth == 0 ? (logsMode ? "logs" : Tr.t("ui_project", "Project")) : f.getName(), 13, current ? Ui.TEXT : Ui.TEXT_2);
        t.setMaxLines(1);
        t.setPadding(Ui.dp(c, 6), 0, 0, 0);
        r.addView(t);
        r.setOnClickListener(v -> { dir = f; reload(); });
        tree.addView(r, Ui.matchWrap());
        if (!open && depth > 0) return;
        File[] l = f.listFiles(File::isDirectory);
        if (l == null) return;
        Arrays.sort(l, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File s : l) addTreeNode(c, s, depth + 1);
    }

    // ------------------------------------------------------------ icons

    static int folderColor(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "images": return 0xFF66BB6A;
            case "sounds": return 0xFFFFA726;
            case "anims": return 0xFFAB47BC;
            case "java": return 0xFF42A5F5;
            case "scenes": return 0xFF26C6DA;
            case "files": return 0xFF8D6E63;
            case "events": return 0xFFFFCA28;
        }
        return 0xFFFFCA28;
    }

    static int fileIcon(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (f.isDirectory()) return R.drawable.ic_s2_folder;
        if (FilePickerDialog.isImage(n)) return R.drawable.ic_s2_image;
        if (n.endsWith(".java") || n.endsWith(".code") || n.endsWith(".json") || n.endsWith(".visual") || n.endsWith(".xml")) return R.drawable.ic_s2_code;
        if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".ogg")) return R.drawable.ic_s2_sound;
        if (n.endsWith(".ttf") || n.endsWith(".otf") || n.endsWith(".s2df") || n.endsWith(".fnt")) return R.drawable.ic_s2_font;
        if (n.endsWith(".tmx") || n.endsWith(".tsx")) return R.drawable.ic_s2_map;
        if (n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".dex")) return R.drawable.ic_s2_zip;
        if (n.endsWith(".p") || n.endsWith(".particle")) return R.drawable.ic_s2_particle;
        if (n.endsWith(".txt") || n.endsWith(".log")) return R.drawable.ic_s2_file;
        return R.drawable.ic_s2_file;
    }

    static int fileColor(File f) {
        if (f.isDirectory()) return folderColor(f.getName());
        int i = fileIcon(f);
        if (i == R.drawable.ic_s2_code) return 0xFF42A5F5;
        if (i == R.drawable.ic_s2_sound) return 0xFFFFA726;
        if (i == R.drawable.ic_s2_font) return 0xFFEC407A;
        if (i == R.drawable.ic_s2_map) return 0xFF8D6E63;
        if (i == R.drawable.ic_s2_particle) return 0xFFEF5350;
        return Ui.TEXT_2;
    }

    // ------------------------------------------------------------ actions

    private void open(Context c, File f) {
        if (f.isDirectory()) {
            dir = f;
            search.setText("");
            reload();
            return;
        }
        String n = f.getName().toLowerCase(Locale.ROOT);
        String parent = f.getParentFile() == null ? "" : f.getParentFile().getName().toLowerCase(Locale.ROOT);
        if (!logsMode && parent.equals("anims") && f.getParentFile().getParentFile() != null
                && f.getParentFile().getParentFile().getAbsolutePath().equals(rootDir.getAbsolutePath())) {
            shell.panels.hide();
            Bridge.gl(() -> Bridge.app().getFileBrowser().openAnimationEditor(Gdx.files.absolute(f.getAbsolutePath())));
        } else if (n.endsWith(".s2df")) {
            shell.panels.hide();
            Bridge.gl(() -> Bridge.app().getFileBrowser().openFontEditor(Gdx.files.absolute(f.getParent()), Gdx.files.absolute(f.getAbsolutePath())));
        } else if (n.endsWith(".java") && !logsMode) {
            shell.host().openJava(f.getAbsolutePath());
        } else if (FilePickerDialog.isImage(n)) {
            imagePreview(c, f);
        } else if (f.length() < 400 * 1024) {
            textEditor(c, f);
        } else info(c, f);
    }

    private void imagePreview(Context c, File f) {
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), 0);
        ImageView iv = new ImageView(c);
        iv.setAdjustViewBounds(true);
        iv.setBackground(Ui.rounded(0xFF101214, Ui.dp(c, 8)));
        col.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(c, 240)));
        FilePickerDialog.thumb(iv, f, Ui.dp(c, 320));
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        TextView info = Ui.text(c, o.outWidth + " × " + o.outHeight + " px · " + size(f.length()) + "\n" + relative(f), 12.5f, Ui.TEXT_2);
        info.setPadding(0, Ui.dp(c, 8), 0, 0);
        col.addView(info);
        AlertDialog d = new AlertDialog.Builder(c)
                .setTitle(f.getName())
                .setView(col)
                .setPositiveButton(Tr.t("close", "Close"), null)
                .setNeutralButton(Tr.t("ui_copy_path", "Copy path"), (x, w) -> Dialogs.copy(c, imagePath(f)))
                .setNegativeButton(Tr.t("ui_more", "More"), (x, w) -> menu(c, grid, f))
                .create();
        d.show();
    }

    /** Path as used by setImage / properties ("/x.png" relative to images). */
    private String imagePath(File f) {
        String imgs = new File(rootDir, "images").getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(imgs) ? p.substring(imgs.length()) : relative(f);
    }

    private String relative(File f) {
        String r = f.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
        return r.isEmpty() ? "/" : r;
    }

    private void textEditor(Context c, File f) {
        String text;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int r = in.read(b);
            text = new String(b, 0, Math.max(0, r), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Ui.toast(c, e.toString());
            return;
        }
        EditText e = Ui.input(c, text, "");
        e.setSingleLine(false);
        e.setTypeface(Typeface.MONOSPACE);
        e.setTextSize(12.5f);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setHorizontallyScrolling(true);
        e.setMinLines(10);
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), 0);
        box.addView(e, new LinearLayout.LayoutParams(-1, (int) (c.getResources().getDisplayMetrics().heightPixels * 0.55f)));
        new AlertDialog.Builder(c)
                .setTitle(f.getName())
                .setView(box)
                .setPositiveButton(Tr.t("save", "Save"), (d, w) -> {
                    try (OutputStream out = new FileOutputStream(f)) {
                        out.write(e.getText().toString().getBytes(StandardCharsets.UTF_8));
                        Ui.toast(c, Tr.t("saved", "Saved"));
                    } catch (Exception ex) {
                        Ui.toast(c, ex.toString());
                    }
                })
                .setNegativeButton(Tr.t("close", "Close"), null)
                .setNeutralButton(Tr.t("copy", "Copy"), (d, w) -> Dialogs.copy(c, e.getText().toString()))
                .show();
    }

    private void info(Context c, File f) {
        new AlertDialog.Builder(c)
                .setTitle(f.getName())
                .setMessage(relative(f) + "\n" + size(f.length()) + "\n" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH).format(new Date(f.lastModified())))
                .setPositiveButton(Tr.t("ok", "OK"), null)
                .show();
    }

    private void menu(Context c, View anchor, File f) {
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(0, 1, 0, Tr.t("open", "Open"));
        m.getMenu().add(0, 2, 1, Tr.t("rename", "Rename"));
        m.getMenu().add(0, 3, 2, Tr.t("ui_duplicate", "Duplicate"));
        m.getMenu().add(0, 4, 3, Tr.t("ui_copy_path", "Copy path"));
        if (!f.isDirectory()) m.getMenu().add(0, 6, 4, Tr.t("ui_share", "Share"));
        m.getMenu().add(0, 5, 5, Tr.t("delete", "Delete"));
        m.setOnMenuItemClickListener(it -> {
            switch (it.getItemId()) {
                case 1: open(c, f); break;
                case 2:
                    Dialogs.input(c, Tr.t("rename", "Rename"), Tr.t("name", "Name"), f.getName(), v -> {
                        if (v.trim().isEmpty() || v.contains("/")) return Tr.t("ui_bad_char", "Not allowed");
                        if (new File(f.getParentFile(), v).exists()) return Tr.t("ui_name_used", "Name already used");
                        return null;
                    }, v -> {
                        if (!f.renameTo(new File(f.getParentFile(), v.trim()))) Ui.toast(c, Tr.t("ui_failed", "Failed"));
                        reload();
                    });
                    break;
                case 3:
                    io.execute(() -> {
                        try {
                            String base = f.getName(), ext = "";
                            int dot = base.lastIndexOf('.');
                            if (dot > 0 && !f.isDirectory()) { ext = base.substring(dot); base = base.substring(0, dot); }
                            File t = new File(f.getParentFile(), base + "_copy" + ext);
                            int i = 2;
                            while (t.exists()) t = new File(f.getParentFile(), base + "_copy" + (i++) + ext);
                            copy(f, t);
                        } catch (Exception e) {
                            Ui.toast(c, e.toString());
                        }
                        Ui.ui(this::reload);
                    });
                    break;
                case 4: Dialogs.copy(c, FilePickerDialog.isImage(f.getName()) ? imagePath(f) : relative(f)); break;
                case 5:
                    Dialogs.confirm(c, Tr.t("delete", "Delete"), f.getName() + "\n" + Tr.t("areYouSure", "Are you sure?"), Tr.t("delete", "Delete"), () -> {
                        deleteRec(f);
                        reload();
                    });
                    break;
                case 6:
                    try {
                        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
                        Intent s = new Intent(Intent.ACTION_SEND).setType("*/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        c.startActivity(Intent.createChooser(s, f.getName()));
                    } catch (Exception e) {
                        Ui.toast(c, e.toString());
                    }
                    break;
            }
            return true;
        });
        m.show();
    }

    private void createMenu(Context c, View anchor) {
        PopupMenu m = new PopupMenu(c, anchor);
        m.getMenu().add(0, 1, 0, Tr.t("folder", "Folder"));
        m.getMenu().add(0, 2, 1, Tr.t("file", "File"));
        m.getMenu().add(0, 3, 2, Tr.t("ui_animation", "Animation (in anims)"));
        m.getMenu().add(0, 4, 3, Tr.t("bitmapFont", "Bitmap font"));
        m.setOnMenuItemClickListener(it -> {
            int id = it.getItemId();
            if (id == 4) {
                shell.panels.hide();
                File d = dir;
                Bridge.gl(() -> Bridge.app().getFileBrowser().openFontEditor(Gdx.files.absolute(d.getAbsolutePath()), null));
                return true;
            }
            File target = id == 3 ? new File(rootDir, "anims") : dir;
            Dialogs.input(c, it.getTitle().toString(), Tr.t("name", "Name"), id == 1 ? "folder" : (id == 3 ? "walk" : "file.txt"), v -> {
                if (v.trim().isEmpty() || v.contains("/")) return Tr.t("ui_bad_char", "Not allowed");
                if (new File(target, v).exists()) return Tr.t("ui_name_used", "Name already used");
                return null;
            }, v -> {
                File f = new File(target, v.trim());
                try {
                    if (id == 1) f.mkdirs();
                    else {
                        target.mkdirs();
                        try (OutputStream o = new FileOutputStream(f)) { o.write(id == 3 ? "[]".getBytes() : new byte[0]); }
                    }
                } catch (Exception e) {
                    Ui.toast(c, e.toString());
                }
                if (id == 3) {
                    dir = target;
                    open(c, f);
                }
                reload();
            });
            return true;
        });
        m.show();
    }

    public static void copy(File from, File to) throws Exception {
        if (from.isDirectory()) {
            to.mkdirs();
            File[] l = from.listFiles();
            if (l != null) for (File f : l) copy(f, new File(to, f.getName()));
            return;
        }
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) != -1) out.write(b, 0, r);
        }
    }

    static void deleteRec(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File s : l) deleteRec(s);
        f.delete();
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.ENGLISH, "%.1f KB", b / 1024f);
        return String.format(Locale.ENGLISH, "%.1f MB", b / 1048576f);
    }

    // ------------------------------------------------------------ adapter

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemViewType(int p) { return gridMode ? 1 : 0; }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int type) {
            Context c = p.getContext();
            LinearLayout cell = type == 1 ? Ui.column(c) : Ui.row(c);
            cell.setGravity(type == 1 ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
            cell.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
            cell.setBackground(Ui.ripple(0, Ui.dp(c, 8)));
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int s = Ui.dp(c, type == 1 ? 54 : 30);
            cell.addView(iv, new LinearLayout.LayoutParams(s, s));
            LinearLayout texts = Ui.column(c);
            TextView name = Ui.text(c, "", type == 1 ? 11.5f : 14, Ui.TEXT);
            name.setMaxLines(2);
            name.setGravity(type == 1 ? Gravity.CENTER : Gravity.START);
            TextView sub = Ui.text(c, "", 11, Ui.TEXT_3);
            texts.addView(name);
            if (type == 0) texts.addView(sub);
            texts.setPadding(type == 1 ? 0 : Ui.dp(c, 10), type == 1 ? Ui.dp(c, 4) : 0, 0, 0);
            cell.addView(texts, type == 1 ? new LinearLayout.LayoutParams(-1, -2) : Ui.weight(1));
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(cell) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
            File f = items.get(pos);
            Context c = h.itemView.getContext();
            LinearLayout cell = (LinearLayout) h.itemView;
            ImageView iv = (ImageView) cell.getChildAt(0);
            LinearLayout texts = (LinearLayout) cell.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setText(f.getName());
            com.star4droid.star2d.ui.tutorial.Tut.tag(cell, "file:" + f.getName());
            if (texts.getChildCount() > 1) {
                String sub = f.isDirectory() ? "" : size(f.length());
                if (!search.getText().toString().isEmpty()) sub = relative(f.getParentFile()) + (sub.isEmpty() ? "" : " · " + sub);
                ((TextView) texts.getChildAt(1)).setText(sub);
            }
            iv.setTag(null);
            iv.setImageResource(fileIcon(f));
            iv.setImageTintList(ColorStateList.valueOf(fileColor(f)));
            if (!f.isDirectory() && FilePickerDialog.isImage(f.getName())) FilePickerDialog.thumb(iv, f, Ui.dp(c, 64));
            cell.setOnClickListener(v -> open(c, f));
            cell.setOnLongClickListener(v -> { menu(c, v, f); return true; });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
