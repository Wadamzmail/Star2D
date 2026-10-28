package com.star4droid.star2d.Helpers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.squareup.javapoet.*;
import com.palantir.javaformat.java.Formatter;
import com.star4droid.star2d.Items.*;
import com.star4droid.star2d.CodeEditor.smart.SourceTrace;
import com.badlogic.gdx.files.FileHandle;
import com.star4droid.star2d.editor.LibgdxEditor;
import com.star4droid.star2d.editor.Utils;
import com.star4droid.star2d.editor.items.EditorItem;
import com.star4droid.star2d.editor.ui.variables.VarModel;
import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * CodeGenerator class is responsible for generating Java code for game elements
 * based on the editor configuration. It creates Player.java files with all necessary
 * components, properties, and event handlers.
 * 
 * This implementation preserves the original functionality while using JavaPoet for
 * code structure and Palantir Java Format for code formatting.
 This made by Manus AI, I don't understand it : )
 sorry ...
 */
public class CodeGenerator {
    // TODO : make this class code more clear (done with JavaPoet and Palantir Java Format 😊)
    
    // Placeholder for script imports in the template
    private static final String script_import = "//import .....script_here;";
    
    /**
     * Interface for code generation callback
     */
    public interface GenerateListener {
        /**
         * Called when code generation is complete
         * @param s The generated code
         */
        void onGenerate(String s);
    }

    // Template strings for code generation - preserved from original implementation
    private static final String defualtItemCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s \n\t\t\n" + // => properites init...
        "\t\t\t%2$s_def.elementEvents = new ElementEvent(){\n" +
        "%4$s \n\t\t};\n" +
        "\t%2$s = (PlayerItem)(%2$s_def.build(this));\n";
    
    private static final String defaultLightCode = 
        "\n\t\t%1$s %2$s_def = new %1$s();\n" + //%1 => item def from ElementsDef class. 2 => name
        "%3$s \n\t\t\n" + // => properites init...
        "\t%2$s = (%2$s_def.build(this));\n";
    
    private static final String defualtJointCode =
        "\n\t\t\t%1$sDef %2$s_Def = new %1$sDef(); %3$s \n"; //e.g = WheelJointDef wj = new WheelJointDef();
    
    private static final String JointInit =
        "\n\t\t\t%1$s_Def.initialize(%2$s);\n" +
        "\t%1$s = (%3$s)(this.world.createJoint(%1$s_Def));\n"+
        "\taddJoint(\"%1$s\",%1$s);";
    
    private static final String fixZIndexDefault =
        "\n\t\t %1$s.getActor().setZIndex((int)(%1$s_def.z));\n";

    /**
     * Generate code for the current editor state
     * 
     * @param editor The LibgdxEditor instance
     * @param generateListener Callback for when generation is complete
     */
    public static void generateFor(LibgdxEditor editor, final GenerateListener generateListener) {
        // Collect property sets from all editor items
        ArrayList<PropertySet<String, Object>> properties = new ArrayList<>();
        for (Actor actor : editor.getActors()) {
            if (actor instanceof EditorItem) {
                PropertySet<String, Object> propertySet = ((EditorItem)actor).getPropertySet();
                properties.add(propertySet);
            }
        }
        
        // Generate code using the collected properties
        generateFor(editor.getProject(), editor.getScene(), properties, generateListener);
    }
    
    public interface AllListener {
        /** {@code all} has the Java of every scene (already written to the project); {@code changed} tells whether any file differs from before. */
        void onGenerate(ArrayList<SceneCode> all, boolean changed);
    }

    /**
     * Generate and write the Java of ALL scenes of the editor's project (the open one from its live items),
     * off the GL thread; the listener runs on the GL thread.
     */
    public static void generateAllFor(LibgdxEditor editor, final AllListener listener) {
        final ArrayList<PropertySet<String, Object>> properties = new ArrayList<>();
        for (Actor actor : editor.getActors())
            if (actor instanceof EditorItem) properties.add(((EditorItem) actor).getPropertySet());
        final com.star4droid.star2d.Helpers.editor.Project project = editor.getProject();
        final String scene = editor.getScene();
        new Thread(() -> {
            ArrayList<SceneCode> all = null;
            boolean changed = false;
            try {
                all = buildAll(project, scene, properties);
                for (SceneCode sc : all) {
                    FileHandle f = Gdx.files.absolute(sc.path);
                    if (!f.exists() || !f.readString().equals(sc.code)) {
                        changed = true;
                        f.writeString(sc.code, false);
                    }
                }
            } catch (Throwable t) {
                Gdx.files.external("logs/code generation.txt").writeString(Utils.getStackTraceString(t), false);
            }
            final ArrayList<SceneCode> res = all;
            final boolean ch = changed;
            if (res != null) Gdx.app.postRunnable(() -> listener.onGenerate(res, ch));
        }).start();
    }

    /**
     * Generate code for a specific scene with provided properties
     * 
     * @param project The project containing the scene
     * @param scene The scene name
     * @param properties List of property sets for all items
     * @param generateListener Callback for when generation is complete
     */
    public static void generateFor(com.star4droid.star2d.Helpers.editor.Project project, 
                                  String scene, 
                                  ArrayList<PropertySet<String, Object>> properties, 
                                  final GenerateListener generateListener) {
        // Run code generation in a separate thread to avoid blocking the UI
        new Thread(() -> {
            String result = null;
            try {
                result = build(project, scene, properties);
            } catch (Throwable t) {
                Gdx.files.external("logs/code generation.txt").writeString(Utils.getStackTraceString(t), false);
            }
            final String code = result;
            if (generateListener != null && code != null)
                Gdx.app.postRunnable(() -> generateListener.onGenerate(code));
        }).start();
    }

    /**
     * Generate the Java of one scene synchronously (no UI thread needed). User-written pieces are
     * wrapped in marker comments and recorded in a {@link SourceTrace} so compiler errors can be
     * traced back to the block / line (see {@link SourceTrace#of(String)}).
     */
    public static String build(final com.star4droid.star2d.Helpers.editor.Project project,
                               final String scene,
                               final ArrayList<PropertySet<String, Object>> properties) {
        return build(project, scene, properties, true);
    }

    /** File path -> text to use instead of the file's content (only for the generating thread). Used by the editor. */
    private static final ThreadLocal<Map<String, String>> OVERRIDES = new ThreadLocal<>();

    /** Like {@link #build(com.star4droid.star2d.Helpers.editor.Project, String, ArrayList)} but with some files replaced by the given text and no re-formatting, so offsets inside the replaced text stay exact. */
    public static String buildWithOverrides(com.star4droid.star2d.Helpers.editor.Project project, String scene,
                                            ArrayList<PropertySet<String, Object>> properties, Map<String, String> overrides) {
        OVERRIDES.set(overrides);
        try {
            return build(project, scene, properties, false);
        } finally {
            OVERRIDES.remove();
        }
    }

    private static final ThreadLocal<SourceTrace> LAST_TRACE = new ThreadLocal<>();

    /** Trace of the last {@code build*} call made on this thread. */
    public static SourceTrace lastTrace() {
        return LAST_TRACE.get();
    }

    private static String fileText(String path) {
        Map<String, String> o = OVERRIDES.get();
        if (o != null && o.containsKey(path)) return o.get(path);
        return null;
    }

    public static String build(final com.star4droid.star2d.Helpers.editor.Project project,
                               final String scene,
                               final ArrayList<PropertySet<String, Object>> properties,
                               final boolean format) {
        final SourceTrace trace = new SourceTrace(project.getCodesPath(scene));
        {
            {
                // Initialize code building variables - preserved from original implementation
                String itemsCode = "";
                String fixZ = "";
                String addChilds = "";
                String vars = "";
                String lightsVar = "";
                String jointVars = "";
				final ArrayList<String> neededScenes = new ArrayList<>();
				neededScenes.add(scene);
                final StringBuilder scriptBuilder = new StringBuilder("");
                boolean thereIsScript = false;
                
                // Collect and process all script files for this scene
                ArrayList<String> scriptsList = new ArrayList<>();
                FileUtil.listDir(project.getScriptsPath(scene), scriptsList);
                
                for (String path : scriptsList) {
                    if (path.endsWith(".java") || path.endsWith(".code")) {
                        String body = fileText(path) != null ? fileText(path) : FileUtil.readFile(path);
                        SourceTrace.Segment seg = trace.add(path, null, body, scene + " › " + new java.io.File(path).getName());
                        scriptBuilder.append(SourceTrace.wrap(seg, body));
                    }
                }
                
                // Process joints - preserved from original implementation
                StringBuilder joint = new StringBuilder("");
                ArrayList<String> joints = new ArrayList<>();
                FileUtil.listDir(project.getJoints(scene), joints);
                
                for (String path : joints) {
                    String p = Gdx.files.absolute(path).file().getName();
                    
                    if (p.contains("-")) {
                        int shouldSkip = JointsHelper.get(p.split("-")[1], "params").split(",").length;
                        jointVars += p.split("-")[1] + " " + p.split("-")[0] + ";\n";
                        String initilaizer = "";
                        StringBuilder init = new StringBuilder("");
                        StringBuilder fieldsInit = new StringBuilder("");
                        
                        // Parse joint properties from JSON
                        ArrayList<HashMap<String, Object>> fields = new Gson().fromJson(
                            FileUtil.readFile(path),
                            new TypeToken<ArrayList<HashMap<String, Object>>>(){}.getType()
                        );
                        
                        float worldScale = com.star4droid.template.Items.StageImp.WORLD_SCALE;
                        // Process joint properties
                        for (HashMap<String, Object> hash : fields) {
                            if (shouldSkip > 0) {
                                // Skip initialization vars (constructor)
                                initilaizer += (initilaizer.equals("") ? "" : ",") + 
                                              hash.get("value").toString().replace("&&", ",").replace("f,","f*"+worldScale+"f,").replace("f)","f*"+worldScale+"f)");
                                shouldSkip--;
                                continue;
                            }
                            
                            fieldsInit.append(String.format(
                                hash.get("code").toString().replace("ff", "f").replace("f,","f*"+worldScale+"f,").replace("f)","f*"+worldScale+"f)"),
                                p.split("-")[0] + "_Def"
                            ));
                        }
                        
                        init.append(fieldsInit);
                        
                        init.append(String.format(
                            JointInit,
                            p.split("-")[0],
                            initilaizer,
                            p.split("-")[1]
                        ));
                        
                        joint.append(String.format(
                            defualtJointCode,
                            p.split("-")[1],
                            p.split("-")[0],
                            init.toString()
                        ));
                        
                    }
                }
                
                // Load default event handlers template
                String defEvents = Gdx.files.internal("java/events.java").readString();
                
                // Process each item's properties
                for (PropertySet<String, Object> propertySet : properties) {
                    boolean isLight = propertySet.getString("TYPE").equals("LIGHT");
                    String[] eventsList = {
                        "onClick", "onTouchStart", "onTouchEnd", "OnBodyCreated", 
                        "OnBodyUpdate", "onBodyCollided", "onBodyCollideEnd", ""
                    };
                    
                    try {
                        String defType = getType(propertySet.getString("TYPE"));
                        String initCode = "";
                        String name = propertySet.getString("name");
                        String script = propertySet.getString("Script");
						String[] scriptSceneList = script.contains("/") ? script.split("/") : null;
                        String scriptScene = scriptSceneList == null ? scene : scriptSceneList[0];
						if(scriptSceneList!=null && !neededScenes.contains(scriptScene))
							neededScenes.add(scriptScene);
						if(scriptSceneList!=null)
							script = scriptSceneList[1];//.substring(0,scriptSceneList[1].lastIndexOf("Script"));
						//Gdx.files.external("logs/script.txt").writeString(String.format("script : %1$s, scene : %2$s, path : %3$s",script,scriptScene,project.getBodyScriptPath(script,scriptScene))+"\n",true);
                        // Load event handlers for non-light objects
                        if (!isLight) {
                            for (int ev = 0; ev < eventsList.length - 1; ev++) {
                                eventsList[ev] = readTraced(trace, project, scriptScene, script, eventsList[ev], scene + " › " + name + " › " + eventsList[ev]);
                            }
                        }
                        
                        // Set name getter for the last event handler
                        eventsList[7] = "return \"" + name + "\";";
                        
                        // Format event handlers
                        String events = String.format(defEvents, (Object[])eventsList);
                        
                        // Create instance of the item definition class for property comparison
                        Class<?> cls = Class.forName("com.star4droid.star2d.ElementDefs." + defType);
                        Object ins = cls.getConstructor().newInstance();
                        
                        // Process each property
                        for (Map.Entry<String, Object> entry : propertySet.entrySet()) {
                            String propertyKey = entry.getKey();
                            
                            // Skip certain properties
                            if ("(TYPE)(Shape)(lock)(parent)(prefab)(pid)(pbase)".contains("(" + propertyKey + ")")) {
                                continue;
                            }
                            if (propertyKey.equals("lock")) {
                                continue;
                            }
                            
                            // Get field from definition class
                            java.lang.reflect.Field field = cls.getField(propertyKey.replace(" ", "_"));
                            String fieldType = field.getType().getName().replace("java.lang.", "");
                            
                            // Skip unsupported field types
                            if (!"float_String_boolean_Vector2".contains(fieldType)) {
                                continue;
                            }
                            
                            // Skip if value is the same as default
                            if (fieldType.equals("float") && 
                                field.getFloat(ins) == propertySet.getFloat(propertyKey)) {
                                continue;
                            }
                            if (fieldType.equals("boolean") && 
                                (field.getBoolean(ins) == propertySet.getString(propertyKey).equals("true"))) {
                                continue;
                            }
                            if (fieldType.equals("String") && 
                                field.get(ins).toString().equals(propertySet.getString(propertyKey))) {
                                continue;
                            }
                            
                            // Format property initialization based on type
                            //String qu1 = fieldType.equals("String") ? "\"" : "";
                            String qu2 = fieldType.equals("float") ? "f" : "";
                            
                            // Add property initialization code
                            // replace (") from string by (\")
                            initCode = (initCode.equals("") ? "" : (initCode + "\n")) + 
                                      "\t\t\t" + name + "_def." + propertyKey.replace(" ", "_") + 
                                      "=" + (fieldType.equals("String") ? new Gson().toJson(entry.getValue().toString()) : entry.getValue().toString()) + qu2 + ";";
                        }
                        
                        // Generate item code using appropriate template
                        itemsCode = itemsCode + (itemsCode.equals("") ? "" : "\n") + 
                                   String.format(
                                       isLight ? defaultLightCode : defualtItemCode,
                                       defType,
                                       name,
                                       initCode,
                                       events
                                   );
                        
                        // Add Z-index fixing code for non-light objects
                        if (!isLight) {
                            fixZ = fixZ + String.format(fixZIndexDefault, name);
                        }

                        // A TEXT / PROGRESS that reacts to no touch lets the touch pass to what lies below it (label on a button).
                        if (!isLight) {
                            String tp = propertySet.getString("TYPE");
                            if (("TEXT".equals(tp) || "PROGRESS".equals(tp)) && !reactsToTouch(project, eventsList, script, scriptScene)) {
                                fixZ = fixZ + "\n" + name + ".getActor().setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);\n";
                            }
                        }
                        
                        // Handle parent-child relationships
                        // (the "parent" name is the truth; the PropertySet link is only a fallback for sets without it)
                        if (!isLight) {
                            String parentOfItem = propertySet.containsKey("parent") ? propertySet.getString("parent")
                                    : (propertySet.getParent() != null ? propertySet.getParent().getString("name") : "");
                            if (!parentOfItem.equals("") && !parentOfItem.equals(name)) {
                                boolean parentOk = false;
                                for (PropertySet<String, Object> other : properties) {
                                    if (parentOfItem.equals(other.getString("name")) && !other.getString("TYPE").equals("LIGHT")) {
                                        parentOk = true;
                                        break;
                                    }
                                }
                                if (parentOk)
                                    addChilds += parentOfItem + ".addChild(" + name + ");\n";
                            }
                        }
                        
                        // Add script attachment if needed
                        if (!FileUtil.readFile(project.getBodyScriptPath(script, scriptScene)).equals("")) {
                            itemsCode += String.format(
                                "\n%2$s.setScript(new com.star4droid.Game.Scripts.%3$s.%1$sScript().setItem(%2$s).setStage(this));\n",
                                script,
                                name,
								scriptScene.toLowerCase()
                            );
                            thereIsScript = true;
                        }
                        
                        // Add variable declarations
                        if (!propertySet.getString("TYPE").contains("LIGHT")) {
                            vars += (vars.equals("") ? "PlayerItem " : ",") + name;
                        } else {
                            lightsVar += (lightsVar.equals("") ? "Light " : ",") + name;
                        }
                        
                    } catch (Exception ex) {
                        // Add error comment for failed items
                        itemsCode = itemsCode + "\n// Error in item at position: " + 
                                   propertySet.getString("name") + 
                                   ", type: " + propertySet.getString("TYPE") + 
                                   ", \n/* Error: \n" + Utils.getStackTraceString(ex) + "\n */";
                    }
                }
                
                // Process light attachments
                for (PropertySet<String, Object> propertySet : properties) {
                    if (propertySet.getString("TYPE").equals("LIGHT") && 
                        !propertySet.getString("attach To").isEmpty()) {
                        
                        itemsCode += "\n" + propertySet.getString("name") + 
                                    ".attachToBody(" + propertySet.getString("attach To") + 
                                    ".getBody());\n";
                    }
                }
                
                // Finalize item code
                itemsCode += "\n" + fixZ + (addChilds.equals("")?"":("\n"+addChilds)) + joint.toString() + readTraced(trace, project, scene, "", "OnCreate", scene + " › OnCreate");
                
                // Finalize variable declarations
                if (!vars.equals("")) {
                    vars += ";";
                }
                if (!lightsVar.equals("")) {
                    lightsVar += ";";
                }
                String sceneScript = FileUtil.isExistFile(project.getSceneScript(scene)) ? ("\nsetScript(new com.star4droid.game.SceneScript."+scene.toLowerCase()+"Script().setStage(this));") : "";
                // Prepare final code and variables
                final String code = itemsCode + sceneScript;
                final String userVarsJsonPath = project.getVariables(scene);
                final StringBuilder userVars = new StringBuilder();
                try {
                    String json = Gdx.files.absolute(userVarsJsonPath).readString();
                    java.util.ArrayList<VarModel> varModelList = new Gson().fromJson(json, new TypeToken<ArrayList<VarModel>>() {}.getType());
                    varModelList.forEach(model -> userVars.append(model.getCode()).append("\n"));
                } catch(Error | Exception ex){}
                final String variables = userVars.toString() + vars + "\n" + jointVars + "\n" + lightsVar;
                final boolean replaceImportOfTheScript = (!thereIsScript);
                
                // Load Player.java template
                String playerCode = Gdx.files.internal("java/Player.java").readString();
                String onPause = readTraced(trace, project, scene, "", "OnPause", scene + " › OnPause"),
                        onResume = readTraced(trace, project, scene, "", "OnResume", scene + " › OnResume"),
                        onStep = readTraced(trace, project, scene, "", "OnStep", scene + " › OnStep"),
                        onCollStart = readTraced(trace, project, scene, "", "onCollisionStart", scene + " › onCollisionStart"),
                        onCollEnd = readTraced(trace, project, scene, "", "onCollisionEnd", scene + " › onCollisionEnd");
                String codeResult = String.format(
                        playerCode,
                        scene.toLowerCase(),
                        variables,
                        code,
                        onPause,
                        onResume,
                        onStep,
                        onCollStart,
                        onCollEnd,
                        scriptBuilder.toString());
                // Format the code using Palantir Java Format (returns the input when it fails)
                String finalCode = format ? formatWithPalantir(codeResult) : codeResult;
                trace.finish(finalCode);
                LAST_TRACE.set(trace);
                if (format) SourceTrace.remember(trace);
                return finalCode;
            }
        }
    }

    /** Generated Java of one scene. */
    public static final class SceneCode {
        public String scene, path, code;
    }

    /** Names of all scenes of the project (files in the scenes folder). */
    public static ArrayList<String> sceneNames(com.star4droid.star2d.Helpers.editor.Project project) {
        ArrayList<String> out = new ArrayList<>();
        java.io.File[] fs = new java.io.File(project.getScenesPath()).listFiles();
        if (fs != null) for (java.io.File f : fs) if (f.isFile() && !com.star4droid.star2d.prefab.PrefabStore.isTempScene(f.getName())) out.add(f.getName());
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** True when the element has code for onClick / onTouchStart / onTouchEnd (events or a Java script that mentions them). */
    private static boolean reactsToTouch(com.star4droid.star2d.Helpers.editor.Project project, String[] events, String script, String scriptScene) {
        for (int i = 0; i < 3; i++) {
            String e = events[i] == null ? "" : events[i].replaceAll("(?s)/\\*.*?\\*/|//[^\n]*", "").trim();
            if (!e.isEmpty()) return true;
        }
        try {
            String code = FileUtil.readFile(project.getBodyScriptPath(script, scriptScene));
            if (code != null && (code.contains("touchStart") || code.contains("touchEnd") || code.contains("click"))) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** The saved items of a scene (what the scene editor wrote with Save), parents re-linked. */
    public static ArrayList<PropertySet<String, Object>> loadProperties(com.star4droid.star2d.Helpers.editor.Project project, String scene) {
        ArrayList<PropertySet<String, Object>> list = new ArrayList<>();
        try {
            String json = FileUtil.readFile(project.getScenesPath() + scene);
            if (json == null || json.trim().isEmpty()) return list;
            ArrayList<PropertySet<String, Object>> loaded = new Gson().fromJson(json,
                    new TypeToken<ArrayList<PropertySet<String, Object>>>() {}.getType());
            if (loaded == null) return list;
            HashMap<String, PropertySet<String, Object>> byName = new HashMap<>();
            for (PropertySet<String, Object> ps : loaded) {
                ps.init();
                byName.put(ps.getString("name"), ps);
            }
            for (PropertySet<String, Object> ps : loaded) {
                String parent = ps.getString("parent");
                if (parent != null && !parent.isEmpty() && byName.containsKey(parent)) ps.setParent(byName.get(parent));
            }
            list.addAll(loaded);
        } catch (Throwable ignored) {
        }
        return list;
    }

    /**
     * Java for every scene of the project: the scene being edited ({@code liveScene}, with its
     * unsaved {@code liveProperties}) plus all the others from their saved files. Synchronous.
     */
    public static ArrayList<SceneCode> buildAll(com.star4droid.star2d.Helpers.editor.Project project,
                                                String liveScene, ArrayList<PropertySet<String, Object>> liveProperties) {
        ArrayList<SceneCode> out = new ArrayList<>();
        ArrayList<String> names = sceneNames(project);
        if (liveScene != null && !names.contains(liveScene)) names.add(liveScene);
        for (String scene : names) {
            SceneCode sc = new SceneCode();
            sc.scene = scene;
            sc.path = project.getCodesPath(scene);
            ArrayList<PropertySet<String, Object>> props = scene.equals(liveScene) && liveProperties != null
                    ? liveProperties : loadProperties(project, scene);
            try {
                sc.code = build(project, scene, props);
            } catch (Throwable t) {
                Gdx.files.external("logs/code generation.txt").writeString(Utils.getStackTraceString(t), false);
                continue;
            }
            out.add(sc);
        }
        return out;
    }

    /**
     * Event code of a piece (a body's event, or a public event when {@code body} is empty), wrapped in
     * trace markers. Block files get one marker per block when their generated code still matches the .java.
     */
    private static String readTraced(SourceTrace trace, com.star4droid.star2d.Helpers.editor.Project project,
                                     String scene, String body, String event, String origin) {
        String base = project.getEventPath(scene, body, event);
        StringBuilder out = new StringBuilder();
        String javaText = "", codeText = "";
        try {
            FileHandle jf = Gdx.files.absolute(base + ".java");
            if (fileText(base + ".java") != null) javaText = fileText(base + ".java");
            else if (jf.exists()) javaText = jf.readString();
        } catch (Exception ignored) {
        }
        if (!body.equals("")) {
            try {
                FileHandle cf = Gdx.files.absolute(base + ".code");
                if (fileText(base + ".code") != null) codeText = fileText(base + ".code");
                else if (cf.exists()) codeText = cf.readString();
            } catch (Exception ignored) {
            }
        }
        if (javaText.length() > 0) {
            String visual = null, text = javaText;
            java.io.File vf = new java.io.File(base + ".visual");
            if (vf.exists()) {
                try {
                    com.star4droid.star2d.ui.blocks.BlockModel m = com.star4droid.star2d.ui.blocks.BlockModel.load(vf);
                    if (m.exportCode(false).trim().equals(javaText.trim())) {
                        text = m.exportCode(true);
                        visual = vf.getPath();
                    }
                } catch (Throwable ignored) {
                }
            }
            SourceTrace.Segment seg = trace.add(base + ".java", visual, javaText, origin);
            out.append(SourceTrace.wrap(seg, text)).append(body.equals("") ? "" : "\n");
        }
        if (codeText.length() > 0) {
            SourceTrace.Segment seg = trace.add(base + ".code", null, codeText, origin);
            out.append(SourceTrace.wrap(seg, codeText));
        }
        return out.toString();
    }

    /**
     * Format Java code using Palantir Java Format
     * 
     * @param code The unformatted Java code
     * @return The formatted Java code
     */
    private static String formatWithPalantir(String code) {
        //return code;
        // there's bug :
        // Runtime.version() isn't available method 
        // check :
        //https://www.google.com/url?sa=t&source=web&rct=j&opi=89978449&url=https://github.com/apache/lucene/issues/13078&ved=2ahUKEwiN5ZSW98iMAxU-G9AFHX0iGR8Qjjh6BAgrEAE&usg=AOvVaw1Rl2I5YdQvlP6gj3QblZ6h
        try {
            Formatter formatter = Formatter.createFormatter(JavaFormatterOptions.defaultOptions());
            return formatter.formatSource(code);
        } catch (Error | Exception e) {
            System.err.println("Error formatting code: " + e.getMessage());
            Gdx.files.external("logs/code format.txt").writeString(com.star4droid.template.Utils.Utils.getStackTraceString(e),false);
            // Return original code if formatting fails
            return code;
        }
    }
    
    /**
     * Convert editor item type to definition class name
     * 
     * @param type The editor item type
     * @return The corresponding definition class name
     */
    public static String getType(String type) {
        switch (type) {
            case "BOX": return "BoxDef";
            case "CUSTOM": return "CustomDef";
            case "JOYSTICK": return "JoyStickDef";
            case "TEXT": return "TextDef";
            case "CIRCLE": return "CircleDef";
            case "PROGRESS": return "ProgressDef";
            case "LIGHT": return "LightDef";
            case "PARTICLE": return "ParticleDef";
            case "CAMERA": return "CameraDef";
            case "MAP": return "MapDef";
            case "GROUP": return "GroupDef";
            default: return "Unknown";
        }
    }
}
