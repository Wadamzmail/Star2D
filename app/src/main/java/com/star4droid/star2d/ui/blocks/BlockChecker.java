package com.star4droid.star2d.ui.blocks;

import com.star4droid.star2d.CodeEditor.SmartProject;
import com.star4droid.star2d.CodeEditor.smart.BuildError;
import com.star4droid.star2d.CodeEditor.smart.SourceTrace;
import com.star4droid.star2d.Helpers.CodeGenerator;
import com.star4droid.star2d.Helpers.editor.Project;
import com.star4droid.star2d.Helpers.PropertySet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compiler check of one saved block script: the scene's Java is generated (with block markers), checked
 * with the bundled javac and each error is traced back to the block that produced it. Blocking, run it
 * on a background thread.
 */
public final class BlockChecker {
    private BlockChecker() {}

    public static final class Result {
        /** Block index (position in {@link BlockModel#allNodes()}) -> first error message of that block. */
        public final LinkedHashMap<Integer, String> byBlock = new LinkedHashMap<>();
        public final ArrayList<BuildError> errors = new ArrayList<>();

        public boolean hasErrors() {
            return !byBlock.isEmpty();
        }
    }

    /** Scene name of an Events/&lt;scene&gt;/... path, or null. */
    public static String sceneOf(String visualFile) {
        int i = visualFile.indexOf("/Events/");
        if (i < 0) return null;
        String rest = visualFile.substring(i + 8);
        int j = rest.indexOf('/');
        return j > 0 ? rest.substring(0, j) : null;
    }

    public static Result check(String visualFile, BlockModel model) {
        Result r = new Result();
        try {
            String root = SmartProject.rootOf(visualFile);
            String scene = sceneOf(visualFile);
            if (root == null || scene == null) return r;
            Project project = new Project(root);
            ArrayList<PropertySet<String, Object>> props = CodeGenerator.loadProperties(project, scene);
            String code = CodeGenerator.buildWithOverrides(project, scene, props, new HashMap<String, String>());
            SourceTrace trace = CodeGenerator.lastTrace();
            if (trace == null || code == null) return r;
            String path = project.getCodesPath(scene);
            SmartProject sp = SmartProject.get(root);
            ArrayList<BuildError> errs = new ArrayList<>(sp.analyzer.diagnose(path, code, null));
            String want = new File(visualFile).getAbsolutePath();
            for (BuildError e : errs) {
                if (e.warning) continue;
                BuildError t = trace.resolve(e.copy());
                if (t.target == BuildError.TARGET_BLOCK && t.targetFile != null
                        && new File(t.targetFile).getAbsolutePath().equals(want)) {
                    if (model != null) {
                        BlockModel.Node n = model.nodeAt(t.blockIndex);
                        if (n != null) t.blockTitle = n.title;
                    }
                    r.errors.add(t);
                    if (!r.byBlock.containsKey(t.blockIndex)) r.byBlock.put(t.blockIndex, t.message);
                }
            }
            if (model != null) {
                for (Integer i : model.formatFailures) {
                    if (!r.byBlock.containsKey(i)) r.byBlock.put(i, "Invalid block code");
                }
            }
        } catch (Throwable ignored) {
        }
        return r;
    }
}
