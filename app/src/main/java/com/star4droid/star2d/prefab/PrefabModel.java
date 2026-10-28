package com.star4droid.star2d.prefab;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The content of a .prefab file: a tree of nodes (the first one is the fixed root, pid "root").
 * Plain data, Gson-serializable. Node positions are offsets from the root.
 */
public final class PrefabModel {
    public static final String ROOT = "root";

    public int version = 1;
    public List<Node> nodes = new ArrayList<>();

    public static final class Node {
        /** Stable id of the node inside the prefab ("root", "n1", "n2"...). */
        public String pid;
        /** pid of the parent node, "" for the root. */
        public String parentPid = "";
        /** Short name (instances are named {@code <instance>_<name>}). */
        public String name;
        /** Element properties (JSON values as stored in scenes). x/y/z are offsets from the root. */
        public JsonObject props = new JsonObject();
        /** event name to (extension to text): extensions are java, visual, code. */
        public Map<String, Map<String, String>> events = new LinkedHashMap<>();
        /** The element's body script (ItemScript java), with placeholders for its name and scene; null when none. */
        public String script;
    }

    public Node root() {
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    public Node byPid(String pid) {
        if (pid == null) return null;
        for (Node n : nodes) if (pid.equals(n.pid)) return n;
        return null;
    }

    /** Next free pid ("n1", "n2"...). */
    public String nextPid() {
        int max = 0;
        for (Node n : nodes) {
            if (n.pid != null && n.pid.length() > 1 && n.pid.charAt(0) == 'n') {
                try {
                    max = Math.max(max, Integer.parseInt(n.pid.substring(1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "n" + (max + 1);
    }

    /** Direct children of a node, in order. */
    public List<Node> childrenOf(String pid) {
        ArrayList<Node> out = new ArrayList<>();
        for (Node n : nodes) if (pid.equals(n.parentPid)) out.add(n);
        return out;
    }
}
