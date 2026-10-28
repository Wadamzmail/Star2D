package com.star4droid.star2d.CodeEditor.smart;

import java.util.ArrayList;
import java.util.List;

public final class CompletionResult {
    /** The identifier part the user already typed (replaced when an item is chosen). */
    public String prefix = "";
    /** True after a ".", false for a name in scope. */
    public boolean member;
    public final List<Item> items = new ArrayList<>();

    public static final CompletionResult EMPTY = new CompletionResult();
}
