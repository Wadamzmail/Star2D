package com.star4droid.star2d.CodeEditor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.star4droid.star2d.CodeEditor.smart.BuildError;
import com.star4droid.star2d.CodeEditor.smart.CompletionResult;
import com.star4droid.star2d.CodeEditor.smart.Hint;
import com.star4droid.star2d.CodeEditor.smart.Item;
import com.star4droid.star2d.CodeEditor.smart.Lexical;
import com.star4droid.star2d.ui.Tr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Everything "smart" about one code editor: completion, live error underlines, the parameter hint
 * bar and the error list. The analysis itself lives in {@link com.star4droid.star2d.CodeEditor.smart}.
 */
public final class SmartEditor {
    private final Activity activity;
    private final CodeEditor editor;
    private final String file;
    private final TextView hintBar;
    private final TextView errorBadge;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private volatile SmartDoc doc;
    private volatile int generation;
    private List<BuildError> errors = new ArrayList<>();
    private List<BuildError> pinned = new ArrayList<>();
    private Hint hint;
    private int hintShown;
    private boolean destroyed;

    public SmartEditor(Activity activity, CodeEditor editor, String file, TextView hintBar, TextView errorBadge) {
        this.activity = activity;
        this.editor = editor;
        this.file = file;
        this.hintBar = hintBar;
        this.errorBadge = errorBadge;
    }

    public void start() {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    doc = SmartDoc.open(file);
                    if (doc.project != null) doc.project.env.warmUp();
                } catch (Throwable ignored) {
                }
                ui.post(new Runnable() {
                    public void run() {
                        scheduleDiagnostics(50);
                    }
                });
            }
        });
        editor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            scheduleDiagnostics(900);
            scheduleHint();
        });
        editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> scheduleHint());
        if (hintBar != null) {
            hintBar.setOnClickListener(v -> {
                if (hint != null && hint.signatures.size() > 1) {
                    hintShown = (hintShown + 1) % hint.signatures.size();
                    renderHint();
                }
            });
        }
        if (errorBadge != null) errorBadge.setOnClickListener(v -> showErrorList());
    }

    public void destroy() {
        destroyed = true;
        ui.removeCallbacksAndMessages(null);
        worker.shutdownNow();
    }

    // ------------------------------------------------------------------------------ completion

    /** Called by the language on Sora's completion thread. */
    public void requireAutoComplete(ContentReference content, CharPosition pos, CompletionPublisher publisher) {
        long deadline = System.currentTimeMillis() + 5000;
        while (doc == null && System.currentTimeMillis() < deadline) {
            publisher.checkCancelled();
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                return;
            }
        }
        SmartDoc d = doc;
        if (d == null || d.project == null) return;
        String text = content.getReference().toString();
        int off = Math.min(pos.index, text.length());
        int ps = Lexical.identStart(text, off);
        String prefix = text.substring(ps, off);
        char prev = ps > 0 ? text.charAt(ps - 1) : 0;
        if (prefix.isEmpty() && prev != '.') return;
        if (prev == '.' && ps >= 2 && text.charAt(ps - 2) == '.') return;
        CompletionResult r = d.complete(text, off);
        publisher.checkCancelled();
        if (r.items.isEmpty()) return;
        final boolean canImport = d.canImport();
        ArrayList<CompletionItem> list = new ArrayList<>(r.items.size());
        int rank = 0;
        final com.star4droid.star2d.CodeEditor.smart.JavaAnalyzer an = d.project.analyzer;
        for (Item it : r.items) {
            list.add(new SmartCompletionItem(it, r.prefix.length(), canImport, rank++).onPick(new SmartCompletionItem.OnPick() {
                public void picked(Item item) {
                    an.recordPick(item.name);
                }
            }));
        }
        publisher.setComparator(new Comparator<CompletionItem>() {
            public int compare(CompletionItem a, CompletionItem b) {
                return a.sortText.compareTo(b.sortText);
            }
        });
        publisher.addItems(list);
    }

    // ----------------------------------------------------------------------------- diagnostics

    private final Runnable diagRun = new Runnable() {
        @Override
        public void run() {
            final SmartDoc d = doc;
            if (d == null || destroyed) return;
            final String text = editor.getText().toString();
            final int gen = ++generation;
            worker.execute(new Runnable() {
                public void run() {
                    List<BuildError> found;
                    try {
                        found = d.diagnose(text);
                    } catch (Throwable t) {
                        return;
                    }
                    if (gen != generation) return;
                    final List<BuildError> res = found;
                    ui.post(new Runnable() {
                        public void run() {
                            if (destroyed || gen != generation) return;
                            pinned.clear();
                            apply(res);
                        }
                    });
                }
            });
        }
    };

    private void scheduleDiagnostics(long delay) {
        ui.removeCallbacks(diagRun);
        ui.postDelayed(diagRun, delay);
    }

    private void apply(List<BuildError> list) {
        errors = new ArrayList<>(list);
        DiagnosticsContainer c = new DiagnosticsContainer();
        int len = editor.getText().length();
        long id = 0;
        int errs = 0;
        for (BuildError e : list) {
            if (e.start < 0 || e.start > len) continue;
            int end = Math.max(e.start + 1, Math.min(e.end, len));
            short sev = e.warning ? DiagnosticRegion.SEVERITY_WARNING : DiagnosticRegion.SEVERITY_ERROR;
            if (!e.warning) errs++;
            c.addDiagnostic(new DiagnosticRegion(e.start, end, sev, id++, new DiagnosticDetail(e.headline(), e.message, null, null)));
        }
        editor.setDiagnostics(c);
        if (errorBadge != null) {
            errorBadge.setVisibility(errs > 0 ? View.VISIBLE : View.GONE);
            errorBadge.setText(String.valueOf(errs));
        }
    }

    /** Errors reported by the compiler (e.g. from the console): show them right away. */
    public void showExternal(List<BuildError> list) {
        pinned = new ArrayList<>(list);
        apply(list);
    }

    /** One error pointed at by the console: underline it right away (live checking replaces it a moment later). */
    public void showExternalAt(int line, int column, int endColumn, String message) {
        int l = Math.max(0, Math.min(line - 1, editor.getText().getLineCount() - 1));
        BuildError e = new BuildError(file, line, column, message);
        e.start = editor.getText().getCharIndex(l, Math.min(Math.max(0, column - 1), editor.getText().getColumnCount(l)));
        e.end = e.start + Math.max(1, endColumn - column);
        ArrayList<BuildError> one = new ArrayList<>();
        one.add(e);
        showExternal(one);
    }

    private void showErrorList() {
        final List<BuildError> all = new ArrayList<>(errors);
        if (all.isEmpty()) return;
        List<String> rows = new ArrayList<>();
        for (BuildError e : all) rows.add(e.line + ":" + e.column + "  " + e.headline());
        new AlertDialog.Builder(activity)
                .setTitle(Tr.t("ui_errors", "Errors") + " (" + all.size() + ")")
                .setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, rows), (dlg, which) -> {
                    BuildError e = all.get(which);
                    goTo(e.line, e.column, e.column + Math.max(1, e.end - e.start));
                })
                .setNegativeButton(Tr.t("close", "Close"), null)
                .show();
    }

    /** Scroll to a position and select the range (1-based line / columns). */
    public void goTo(int line, int column, int endColumn) {
        int l = Math.max(0, Math.min(line - 1, editor.getText().getLineCount() - 1));
        int max = editor.getText().getColumnCount(l);
        int c = Math.max(0, Math.min(column - 1, max));
        int e = Math.max(c, Math.min(endColumn - 1, max));
        if (e <= c) e = Math.min(max, c + 1);
        editor.ensurePositionVisible(l, c);
        editor.setSelectionRegion(l, c, l, e);
    }

    // ----------------------------------------------------------------------------- param hint

    private final Runnable hintRun = new Runnable() {
        @Override
        public void run() {
            final SmartDoc d = doc;
            if (d == null || destroyed || hintBar == null) return;
            final String text = editor.getText().toString();
            final int off = editor.getCursor().getLeft();
            // cheap pre-check: only when the caret is inside an unclosed '('
            if (!insideCall(text, off)) {
                clearHint();
                return;
            }
            worker.execute(new Runnable() {
                public void run() {
                    Hint h = null;
                    try {
                        h = d.hint(text, off);
                    } catch (Throwable ignored) {
                    }
                    final Hint res = h;
                    ui.post(new Runnable() {
                        public void run() {
                            if (destroyed) return;
                            if (res == null || res.signatures.isEmpty()) clearHint();
                            else {
                                hint = res;
                                // best overload for the argument count so far
                                hintShown = 0;
                                for (int i = 0; i < res.signatures.size(); i++) {
                                    Item s = res.signatures.get(i);
                                    if (s.paramTypes.size() > res.active || s.varArgs) {
                                        hintShown = i;
                                        break;
                                    }
                                }
                                renderHint();
                            }
                        }
                    });
                }
            });
        }
    };

    private void scheduleHint() {
        if (hintBar == null) return;
        ui.removeCallbacks(hintRun);
        ui.postDelayed(hintRun, 220);
    }

    private static boolean insideCall(String text, int off) {
        int depth = 0;
        for (int i = Math.min(off, text.length()) - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')' || c == ']' || c == '}') depth++;
            else if (c == '(' || c == '[' || c == '{') {
                if (depth == 0) return c == '(';
                depth--;
            } else if (c == ';' && depth == 0) return false;
        }
        return false;
    }

    private void clearHint() {
        hint = null;
        if (hintBar != null) hintBar.setVisibility(View.GONE);
    }

    private void renderHint() {
        if (hint == null || hintBar == null) return;
        Item s = hint.signatures.get(Math.min(hintShown, hint.signatures.size() - 1));
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(s.kind == Item.Kind.CONSTRUCTOR ? "new " : "").append(hint.name).append('(');
        int active = hint.active;
        if (s.varArgs && active >= s.paramTypes.size()) active = s.paramTypes.size() - 1;
        for (int i = 0; i < s.paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            int a = sb.length();
            sb.append(s.paramTypes.get(i));
            String pn = i < s.paramNames.size() ? s.paramNames.get(i) : "";
            if (pn.length() > 0) sb.append(' ').append(pn);
            if (i == active) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(0xFFFFCA28), a, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        sb.append(')');
        if (hint.signatures.size() > 1) sb.append("   ").append(String.valueOf(Math.min(hintShown, hint.signatures.size() - 1) + 1)).append('/').append(String.valueOf(hint.signatures.size()));
        hintBar.setText(sb);
        hintBar.setVisibility(View.VISIBLE);
    }
}
