package com.star4droid.star2d.CodeEditor.smart;

import java.util.ArrayList;
import java.util.List;

/** Parameter hint for the call being typed. */
public final class Hint {
    public String name = "";
    /** Index of the argument the caret is in (0-based). */
    public int active;
    public final List<Item> signatures = new ArrayList<>();
}
