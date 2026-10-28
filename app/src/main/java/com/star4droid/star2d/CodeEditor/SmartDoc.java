package com.star4droid.star2d.CodeEditor;

import com.star4droid.star2d.CodeEditor.smart.BuildError;
import com.star4droid.star2d.CodeEditor.smart.CompletionResult;
import com.star4droid.star2d.CodeEditor.smart.Hint;
import com.star4droid.star2d.CodeEditor.smart.Lexical;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.PropertySet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the analyzer sees for the file open in the code editor.
 *
 * <p>Full classes ({@code .java} under the project's java folder) are analyzed as they are. Pieces
 * that are pasted into the generated scene (event code, scene scripts) can't be analyzed alone, so
 * the editor text is placed inside the real generated scene source (everything around it comes
 * from the project) and offsets are shifted by the length of the code before it.
 */
public final class SmartDoc {
    public final String file;
    public final SmartProject project;
    private String prefix, suffix, docPath;

    private SmartDoc(String file, SmartProject project) {
        this.file = file;
        this.project = project;
    }

    public boolean regionMode() {
        return prefix != null;
    }

    /** Can imports be added to the text being edited? (not for code pasted into a method) */
    public boolean canImport() {
        return !regionMode();
    }

    /** Opens the document. Takes a moment in region mode (generates the scene), so call it off the UI thread. */
    public static SmartDoc open(String file) {
        String root = SmartProject.rootOf(file);
        if (root == null) return new SmartDoc(file, null);
        SmartDoc d = new SmartDoc(file, SmartProject.get(root));
        d.docPath = file;
        try {
            String scene = sceneOf(root, file);
            if (scene != null) d.buildRegion(root, scene);
        } catch (Throwable t) {
            d.prefix = d.suffix = null;
            d.docPath = file;
        }
        return d;
    }

    /** Scene a piece belongs to: Events/&lt;scene&gt;/... or scripts/&lt;scene&gt;/... */
    private static String sceneOf(String root, String file) {
        for (String dir : new String[]{"/Events/", "/scripts/"}) {
            String start = root + dir;
            if (file.startsWith(start)) {
                String rest = file.substring(start.length());
                int i = rest.indexOf('/');
                if (i > 0) return rest.substring(0, i);
            }
        }
        return null;
    }

    private void buildRegion(String root, String scene) {
        com.star4droid.star2d.Helpers.editor.Project p = new com.star4droid.star2d.Helpers.editor.Project(root);
        ArrayList<PropertySet<String, Object>> props = CodeGenerator.loadProperties(p, scene);
        HashMap<String, String> ov = new HashMap<>();
        ov.put(file, " ");
        String gen = CodeGenerator.buildWithOverrides(p, scene, props, ov);
        Matcher m = Pattern.compile("/\\*@s:(\\d+)\\*/\n \n/\\*@e:\\1\\*/\n").matcher(gen);
        if (!m.find()) return;
        int bodyStart = m.start() + ("/*@s:" + m.group(1) + "*/\n").length();
        prefix = gen.substring(0, bodyStart);
        suffix = gen.substring(bodyStart + 1);
        docPath = p.getCodesPath(scene);
    }

    public String docText(String editorText) {
        return regionMode() ? prefix + editorText + suffix : editorText;
    }

    public int toDoc(int editorOffset) {
        return regionMode() ? prefix.length() + editorOffset : editorOffset;
    }

    /** Package the file declares (so classes of the same package need no import). */
    public CompletionResult complete(String editorText, int offset) {
        if (project == null) return CompletionResult.EMPTY;
        project.env.allowImports = canImport();
        return project.analyzer.complete(docPath, docText(editorText), toDoc(offset));
    }

    public Hint hint(String editorText, int offset) {
        if (project == null) return null;
        return project.analyzer.hint(docPath, docText(editorText), toDoc(offset));
    }

    /** Errors inside the edited text, in editor coordinates. */
    public List<BuildError> diagnose(String editorText) {
        ArrayList<BuildError> out = new ArrayList<>();
        if (project == null) return out;
        String doc = docText(editorText);
        List<BuildError> all = project.analyzer.diagnose(docPath, doc, null);
        int from = regionMode() ? prefix.length() : 0, to = regionMode() ? prefix.length() + editorText.length() : editorText.length();
        for (BuildError e : all) {
            if (e.file == null || !new File(e.file).getAbsolutePath().equals(new File(docPath).getAbsolutePath())) continue;
            if (e.start < from || e.start > to) continue;
            BuildError c = e.copy();
            c.start = e.start - from;
            c.end = Math.min(Math.max(e.end - from, c.start + 1), editorText.length());
            c.line = Lexical.lineOf(editorText, c.start);
            c.column = c.start - Lexical.lineStart(editorText, c.start) + 1;
            c.file = file;
            out.add(c);
        }
        return out;
    }
}
