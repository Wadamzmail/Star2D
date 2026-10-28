package com.star4droid.star2d.CodeEditor.smart;

import java.util.ArrayList;
import java.util.List;

/** One completion candidate. Pure data, safe to cache and to hand to the UI thread. */
public final class Item {
    public enum Kind { LOCAL, PARAM, FIELD, CONSTANT, METHOD, CONSTRUCTOR, CLASS, INTERFACE, ENUM, PACKAGE, KEYWORD, SNIPPET, OVERRIDE, TYPE_PARAM }

    public Kind kind = Kind.KEYWORD;
    /** What the user sees in the list ("getBody()", "Vector2"). */
    public String label = "";
    /** Text matched against what the user typed. */
    public String name = "";
    /** Text inserted. May contain {@code $0} (caret position) and \n (indent is added by the editor). */
    public String insert = "";
    /** Right hand side: return type / variable type. */
    public String detail = "";
    /** Second line: declaring class or package. */
    public String owner = "";
    /** Fully qualified class that must be imported when this item is used (null = nothing to import). */
    public String importFqn;
    public boolean deprecated;
    public boolean isStatic;

    // method details (parameter hints)
    public String returnType = "";
    public List<String> paramTypes = new ArrayList<>();
    public List<String> paramNames = new ArrayList<>();
    public boolean varArgs;

    /** Set while gathering (closeness of the declaring class, class priority). */
    public int base;
    /** Higher first. Filled by the ranking step. */
    public int score;

    public String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(');
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i));
            String pn = i < paramNames.size() ? paramNames.get(i) : "";
            if (pn.length() > 0) sb.append(' ').append(pn);
        }
        return sb.append(')').toString();
    }
}
