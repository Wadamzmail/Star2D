package com.star4droid.star2d.Views;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.star4droid.star2d.Helpers.EngineSettings;
import com.star4droid.star2d.MainActivity;
import com.star4droid.star2d.editor.ui.sub.EditorField;
import com.star4droid.star2d.evo.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native Android home screen (Projects / Templates / Community / Settings).
 * It is placed on top of the LibGDX editor surface and hidden while a project is open.
 */
public class ProjectsHomeView extends FrameLayout {

    /** Actions that need the hosting activity / the LibGDX app. */
    public interface Host {
        /** Open the project located at the given absolute path. */
        void openProject(File projectDir);
        void exportProject(File projectDir);
        void backupProject(File projectDir);
        void importProject();
        void openDonate();
    }

    private static final String[] LANG_NAMES = {"English", "العربيه", "Français", "Português", "Русский", "Español"};
    private static final String[] LANG_CODES = {"en", "ar", "fr", "br", "ru", "es"};
    private static final String[] COMPILERS = {"javac", "ecj"};

    private final Activity activity;
    private final Host host;
    private final File projectsDir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView[] tabs;
    private View[] pages;
    private LinearLayout projectsList;
    private TextView projectsCount, projectsEmpty;
    private EditText search;

    public ProjectsHomeView(Activity activity, Host host) {
        super(activity);
        this.activity = activity;
        this.host = host;
        this.projectsDir = new File(activity.getFilesDir(), "projects");
        LayoutInflater.from(activity).inflate(R.layout.home_screen, this, true);
        setupTabs();
        setupProjects();
        setupTemplates();
        setupCommunity();
        setupSettings();
        selectTab(0);
        refresh();
        askLanguageOnFirstRun();
    }

    // ---------------------------------------------------------------- tabs

    private void setupTabs() {
        tabs = new TextView[]{
                findViewById(R.id.tab_projects), findViewById(R.id.tab_templates),
                findViewById(R.id.tab_community), findViewById(R.id.tab_settings)};
        pages = new View[]{
                findViewById(R.id.page_projects), findViewById(R.id.page_templates),
                findViewById(R.id.page_community), findViewById(R.id.page_settings)};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index));
        }
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            tabs[i].setBackgroundResource(selected ? R.drawable.home_tab_selected : 0);
            tabs[i].setTextColor(color(selected ? R.color.home_text : R.color.home_text_secondary));
            tabs[i].setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            pages[i].setVisibility(selected ? VISIBLE : GONE);
        }
    }

    // ------------------------------------------------------------ projects

    private void setupProjects() {
        projectsList = findViewById(R.id.projects_list);
        projectsCount = findViewById(R.id.projects_count);
        projectsEmpty = findViewById(R.id.projects_empty);
        search = findViewById(R.id.projects_search);
        findViewById(R.id.btn_new_project).setOnClickListener(v -> createNewProject());
        findViewById(R.id.btn_open).setOnClickListener(v -> host.importProject());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
    }

    /** Reload the projects list (safe to call from any thread). */
    public void refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui.post(this::refresh);
            return;
        }
        File[] files = projectsDir.listFiles(File::isDirectory);
        List<File> all = files == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        projectsCount.setText(all.size() + " project(s) in workspace");

        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        projectsList.removeAllViews();
        SimpleDateFormat format = new SimpleDateFormat("MMM d, yyyy HH:mm", Locale.ENGLISH);
        LayoutInflater inflater = LayoutInflater.from(activity);
        int shown = 0;
        for (File project : all) {
            if (!project.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
            View row = inflater.inflate(R.layout.home_item_project, projectsList, false);
            ((TextView) row.findViewById(R.id.project_name)).setText(project.getName());
            ((TextView) row.findViewById(R.id.project_date)).setText(format.format(new Date(project.lastModified())));
            row.setOnClickListener(v -> openProject(project));
            View menu = row.findViewById(R.id.project_menu);
            menu.setOnClickListener(v -> showProjectMenu(project, menu));
            projectsList.addView(row);
            shown++;
        }
        projectsEmpty.setText(all.isEmpty() ? "No projects yet" : "No matching projects");
        projectsEmpty.setVisibility(shown == 0 ? VISIBLE : GONE);
    }

    private void openProject(File project) {
        createDefaultPaths(project);
        host.openProject(project);
    }

    private void showProjectMenu(File project, View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, "Open");
        menu.getMenu().add(0, 2, 1, "Export");
        menu.getMenu().add(0, 3, 2, "Backup");
        menu.getMenu().add(0, 4, 3, "Delete");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: openProject(project); break;
                case 2: host.exportProject(project); break;
                case 3: host.backupProject(project); break;
                case 4: confirmDelete(project); break;
            }
            return true;
        });
        menu.show();
    }

    private void confirmDelete(File project) {
        new AlertDialog.Builder(activity)
                .setTitle("Delete project")
                .setMessage("Are you sure you want to delete \"" + project.getName() + "\"? This can't be undone.")
                .setPositiveButton("Delete", (d, w) -> io.execute(() -> {
                    boolean ok = deleteRecursive(project);
                    ui.post(() -> {
                        toast(ok ? "Project deleted." : "Failed to delete project.");
                        refresh();
                    });
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewProject() {
        askProjectName("New project", "NewProject", name -> {
            File dir = new File(projectsDir, name);
            if (!dir.mkdirs()) {
                toast("Couldn't create the project folder.");
                return;
            }
            createDefaultPaths(dir);
            refresh();
            host.openProject(dir);
        });
    }

    // ----------------------------------------------------------- templates

    private void setupTemplates() {
        LinearLayout list = findViewById(R.id.templates_list);
        LayoutInflater inflater = LayoutInflater.from(activity);
        AssetManager assets = activity.getAssets();
        List<String> examples = new ArrayList<>();
        try {
            String[] names = assets.list("files/examples");
            if (names != null)
                for (String n : names)
                    if (!n.endsWith(".txt")) examples.add(n);
        } catch (Exception ignored) {}

        // Known templates first, in the designed order; any other example after them.
        String[] order = {"StarValley", "platformer", "CarsExample", "Paul", "example1"};
        List<String> sorted = new ArrayList<>();
        for (String o : order) if (examples.remove(o)) sorted.add(o);
        sorted.addAll(examples);

        for (String folder : sorted) {
            String[] info = templateInfo(folder);
            View card = inflater.inflate(R.layout.home_item_template, list, false);
            ((TextView) card.findViewById(R.id.template_title)).setText(info[0]);
            ((TextView) card.findViewById(R.id.template_tag)).setText(info[1]);
            ((TextView) card.findViewById(R.id.template_desc)).setText(info[2]);
            card.findViewById(R.id.template_use).setOnClickListener(v -> useTemplate(folder));
            list.addView(card);
        }
    }

    /** {title, tag, description} for an example folder. */
    private String[] templateInfo(String folder) {
        switch (folder) {
            case "StarValley":
                return new String[]{"Star Valley RPG", "RPG / Adventure",
                        "Drive your vehicle with on-screen joystick, collect collectibles, Day/Night lighting system and compass navigation."};
            case "platformer":
                return new String[]{"2D Platformer", "Action / Platformer",
                        "Classic 2D platformer starter kit with jump physics, obstacle colliders, and level mechanics."};
            case "CarsExample":
                return new String[]{"Vehicle Physics", "Simulation / Physics",
                        "2D car steering simulation utilizing Box2D joint physics, friction, and suspension tuning."};
            case "Paul":
                return new String[]{"Character Demo", "Platformer",
                        "Animated player character controller demonstrating state machine actions and sprite animations."};
            case "example1":
                return new String[]{"2D Starter Core", "Starter",
                        "Clean empty scene with 2D camera viewport, physics world, gravity and joints ready to build on."};
            default:
                return new String[]{folder, "Example", readAssetText("files/examples/" + folder + ".txt")};
        }
    }

    private void useTemplate(String folder) {
        askProjectName("Use template", folder, name -> {
            File target = new File(projectsDir, name);
            toast("Creating project...");
            io.execute(() -> {
                try {
                    copyAssetDir(activity.getAssets(), "files/examples/" + folder, target);
                    createDefaultPaths(target);
                    ui.post(() -> {
                        toast("Project created: " + name);
                        search.setText("");
                        selectTab(0);
                        refresh();
                    });
                } catch (Exception | Error e) {
                    deleteRecursive(target);
                    ui.post(() -> {
                        toast("Failed to create project: " + e);
                        refresh();
                    });
                }
            });
        });
    }

    // ----------------------------------------------------------- community

    private void setupCommunity() {
        LinearLayout list = findViewById(R.id.community_list);
        findViewById(R.id.btn_donate).setOnClickListener(v -> host.openDonate());
        String[][] links = {
                {"Facebook Group", "Join the Star2D developers community on Facebook", "https://facebook.com/groups/995354408402399/"},
                {"WhatsApp Group", "Live chat with developers and get quick assistance", "https://chat.whatsapp.com/Bxu3kM0b1oE9UL0iZhti4E?mode=ems_copy_c"},
                {"Telegram Channel", "News, releases, asset packs, and updates", "https://t.me/+pE4VREnP04s5NDNk"},
                {"Discord Community", "Discuss game dev, share scripts, and collaborate", "https://discord.gg/9gxPUTEP"},
                {"GitHub Repository", "Star2D Engine open source code and issue tracking", "https://github.com/star4droid/Star2D/"},
                {"YouTube Channel", "Game engine tutorials, guides, and feature showcases", "https://youtube.com/@star4droid?si=wNDqN_fbRIzf3KSQ"},
        };
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (String[] link : links) {
            View row = inflater.inflate(R.layout.home_item_community, list, false);
            ((TextView) row.findViewById(R.id.community_title)).setText(link[0]);
            ((TextView) row.findViewById(R.id.community_desc)).setText(link[1]);
            row.findViewById(R.id.community_join).setOnClickListener(v -> openUrl(link[2]));
            list.addView(row);
        }
    }

    private void openUrl(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("No app found to open the link.");
        }
    }

    // ------------------------------------------------------------ settings

    private void setupSettings() {
        SharedPreferences prefs = prefs();

        Spinner language = findViewById(R.id.setting_language);
        language.setAdapter(spinnerAdapter(LANG_NAMES));
        String currentLang = prefs.getString("lang", "en");
        language.setSelection(Math.max(0, Arrays.asList(LANG_CODES).indexOf(currentLang)));
        language.setOnItemSelectedListener(new SimpleSelection(position -> {
            String code = LANG_CODES[position];
            if (code.equals(prefs().getString("lang", "en"))) return;
            prefs().edit().putString("lang", code).putBoolean("langSelected", true).apply();
            askRestart();
        }));

        Spinner compiler = findViewById(R.id.setting_compiler);
        compiler.setAdapter(spinnerAdapter(COMPILERS));
        compiler.setSelection(Math.max(0, Arrays.asList(COMPILERS).indexOf(prefs.getString("compiler", "javac"))));
        compiler.setOnItemSelectedListener(new SimpleSelection(position ->
                prefs().edit().putString("compiler", COMPILERS[position]).apply()));

        bindSwitch(R.id.setting_completion, "Auto Completion");
        bindSwitch(R.id.setting_autosave, "AutoSave");
        bindSwitch(R.id.setting_undo, "SaveUndoRedo");
    }

    private void bindSwitch(int id, String key) {
        SwitchCompat s = findViewById(id);
        int[][] states = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(states, new int[]{0xFFFFFFFF, 0xFFBDBDBD}));
        s.setTrackTintList(new ColorStateList(states, new int[]{color(R.color.home_accent), 0xFF555555}));
        s.setChecked(prefs().getBoolean(key, true));
        s.setOnCheckedChangeListener((b, checked) -> prefs().edit().putBoolean(key, checked).apply());
    }

    private ArrayAdapter<String> spinnerAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, R.layout.home_spinner_item, items);
        adapter.setDropDownViewResource(R.layout.home_spinner_item);
        return adapter;
    }

    private void askRestart() {
        new AlertDialog.Builder(activity)
                .setTitle("Restart required")
                .setMessage("The new language will be applied after restarting Star2D.")
                .setPositiveButton("Restart now", (d, w) -> restartApp())
                .setNegativeButton("Later", null)
                .show();
    }

    private void askLanguageOnFirstRun() {
        if (prefs().getBoolean("langSelected", false)) return;
        new AlertDialog.Builder(activity)
                .setTitle("Choose language")
                .setCancelable(false)
                .setItems(LANG_NAMES, (d, which) -> {
                    String code = LANG_CODES[which];
                    boolean changed = !code.equals(prefs().getString("lang", "en"));
                    prefs().edit().putString("lang", code).putBoolean("langSelected", true).apply();
                    if (changed) restartApp();
                })
                .show();
    }

    private void restartApp() {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    // ------------------------------------------------------------- helpers

    private interface NameCallback { void onName(String name); }

    private void askProjectName(String title, String defaultName, NameCallback callback) {
        EditText input = new EditText(activity);
        input.setText(defaultName);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.selectAll();
        FrameLayout container = new FrameLayout(activity);
        int pad = dp(20);
        container.setPadding(pad, dp(8), pad, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage("Project name (A-Z, a-z, 0-9, _):")
                .setView(container)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim().replace(" ", "_");
            if (name.isEmpty()) {
                input.setError("Enter a name");
                return;
            }
            for (char c : name.toCharArray()) {
                if (!EditorField.allowedChars.contains(String.valueOf(c))) {
                    input.setError("Not allowed: " + c);
                    return;
                }
            }
            if (new File(projectsDir, name).exists()) {
                input.setError("A project with this name already exists");
                return;
            }
            dialog.dismiss();
            callback.onName(name);
        }));
        dialog.show();
    }

    private static void createDefaultPaths(File project) {
        for (String path : new String[]{"sounds", "anims", "images", "files", "icon"}) {
            File child = new File(project, path);
            if (!child.exists()) child.mkdirs();
        }
    }

    private static void copyAssetDir(AssetManager assets, String assetPath, File target) throws Exception {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            // A file (asset folders are never empty in the APK).
            File parent = target.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = assets.open(assetPath); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            return;
        }
        target.mkdirs();
        for (String child : children)
            copyAssetDir(assets, assetPath + "/" + child, new File(target, child));
    }

    private static boolean deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children) deleteRecursive(child);
        return file.delete() || !file.exists();
    }

    private String readAssetText(String path) {
        try (InputStream in = activity.getAssets().open(path)) {
            byte[] data = new byte[in.available()];
            int read = in.read(data);
            return new String(data, 0, Math.max(read, 0), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private SharedPreferences prefs() {
        if (EngineSettings.get() == null) EngineSettings.init(activity);
        return EngineSettings.get();
    }

    private int color(int id) {
        return ContextCompat.getColor(activity, id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public void toast(String message) {
        ui.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    /** Spinner listener that ignores the initial callback fired by setSelection. */
    private static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        interface OnPick { void pick(int position); }
        private final OnPick onPick;
        private boolean first = true;
        SimpleSelection(OnPick onPick) { this.onPick = onPick; }
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (first) { first = false; return; }
            onPick.pick(position);
        }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
