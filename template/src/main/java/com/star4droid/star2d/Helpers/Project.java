package com.star4droid.star2d.Helpers;

import java.util.ArrayList;

/**
 * Slim runtime copy of the editor's Project (paths of a project folder only, no editing code).
 * The runtime needs only the path helpers; the editor's full class (app module) has the same methods and more.
 * If the runtime starts to use another Project method, add it here too (template build fails to compile otherwise).
 */
public class Project {
    private String path;

    public Project(String p) {
        path = p;
    }

    public String getPath() {
        return path;
    }

    public String get(String name) {
        return path + "/" + name + "/";
    }

    public String getBodiesScripts(String scene) {
        return path + "/java/com/star4droid/Game/Scripts/" + scene + "/";
    }

    public String getBodyScriptPath(String body, String scene) {
        return getBodiesScripts(scene) + body + "Script.java";
    }

    public String getCodesPath(String scene) {
        return path + "/java/com/star4droid/Game/" + scene.toLowerCase() + ".java";
    }

    public String getScriptsPath(String scene) {
        return path + "/scripts/" + scene + "/";
    }

    public ArrayList<String> getSceneList(String scene) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add(getScenesPath() + scene);
        arrayList.add(getConfig(scene));
        arrayList.add(path + "/Events/" + scene);
        arrayList.add(getJoints(scene));
        arrayList.add(getCodesPath(scene));
        arrayList.add(getScriptsPath(scene));
        arrayList.add(getBodiesScripts(scene));
        return arrayList;
    }

    ArrayList<String> importantList = new ArrayList<>();

    public ArrayList<String> getImportantList() {
        if (importantList.size() > 0) return importantList;
        importantList = new ArrayList<>();
        importantList.add(get("anims").toLowerCase());
        importantList.add(get("dex").toLowerCase());
        importantList.add(get("configs").toLowerCase());
        importantList.add(get("files").toLowerCase());
        importantList.add(get("scenes").toLowerCase());
        return importantList;
    }

    public String getEventPath(String scene, String body, String event) {
        if (body.equals("")) return path + "/Events/" + scene + "/public/" + event;
        return path + "/Events/" + scene + "/private/" + body + "/" + event;
    }

    public String getConfig(String scene) {
        return path + "/configs/" + scene + ".json";
    }

    public String getJoints(String scene) {
        return path + "/joints/" + scene + "/";
    }

    public String getDex() {
        return path + "/dex/scenes.dex";
    }

    public String getName() {
        return new java.io.File(path).getName();
    }

    public String getScenesPath() {
        return path + "/scenes/";
    }

    public String getImagesPath() {
        return path + "/images/";
    }
}
