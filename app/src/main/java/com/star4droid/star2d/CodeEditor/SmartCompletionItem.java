package com.star4droid.star2d.CodeEditor;

import com.star4droid.star2d.CodeEditor.smart.Item;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionItemKind;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

/** A completion row. Choosing it replaces the typed prefix, adds the import and puts the caret where it belongs. */
public final class SmartCompletionItem extends CompletionItem {
    /** Told which item was picked (used to rank it higher next time). */
    public interface OnPick { void picked(Item item); }

    private final Item item;
    private final boolean canImport;
    private OnPick onPick;

    public SmartCompletionItem onPick(OnPick p) {
        onPick = p;
        return this;
    }

    public SmartCompletionItem(Item item, int prefixLength, boolean canImport, int rank) {
        super(item.label, describe(item));
        this.item = item;
        this.canImport = canImport;
        this.prefixLength = prefixLength;
        this.kind = kindOf(item.kind);
        this.deprecated = item.deprecated;
        this.sortText = String.format(java.util.Locale.US, "%05d", rank);
        this.filterText = item.name;
    }

    private static String describe(Item it) {
        String d = it.detail == null ? "" : it.detail;
        String o = it.owner == null ? "" : it.owner;
        if (it.importFqn != null && o.length() > 0) return d.length() > 0 ? d + " · " + o : o;
        if (d.length() > 0 && o.length() > 0 && it.kind != Item.Kind.CLASS) return d + " · " + o;
        return d.length() > 0 ? d : o;
    }

    private static CompletionItemKind kindOf(Item.Kind k) {
        switch (k) {
            case LOCAL:
            case PARAM:
                return CompletionItemKind.Variable;
            case FIELD:
                return CompletionItemKind.Field;
            case CONSTANT:
                return CompletionItemKind.Constant;
            case METHOD:
            case OVERRIDE:
                return CompletionItemKind.Method;
            case CONSTRUCTOR:
                return CompletionItemKind.Constructor;
            case CLASS:
                return CompletionItemKind.Class;
            case INTERFACE:
                return CompletionItemKind.Interface;
            case ENUM:
                return CompletionItemKind.Enum;
            case PACKAGE:
                return CompletionItemKind.Module;
            case SNIPPET:
                return CompletionItemKind.Snippet;
            case TYPE_PARAM:
                return CompletionItemKind.TypeParameter;
            default:
                return CompletionItemKind.Keyword;
        }
    }

    public Item item() {
        return item;
    }

    @Override
    public void performCompletion(CodeEditor editor, Content text, int line, int column) {
        if (onPick != null) {
            try { onPick.picked(item); } catch (Throwable ignored) {}
        }
        String insert = item.insert;
        boolean importIt = item.importFqn != null && canImport;
        if (item.importFqn != null && !canImport) insert = item.importFqn; // can't add an import line here: use the full name
        String lineText = text.getLineString(line);
        char next = column < lineText.length() ? lineText.charAt(column) : 0;
        // editing an existing call: keep its brackets
        if ((item.kind == Item.Kind.METHOD) && next == '(') insert = item.name;
        if (item.kind == Item.Kind.CLASS && next == '(' ) insert = item.insert;
        // indent continuation lines like the current line
        int ind = 0;
        while (ind < lineText.length() && (lineText.charAt(ind) == ' ' || lineText.charAt(ind) == '\t')) ind++;
        String indent = lineText.substring(0, ind);
        if (insert.indexOf('\n') >= 0) insert = insert.replace("\n", "\n" + indent);
        int caret = insert.indexOf("$0");
        if (caret >= 0) insert = insert.substring(0, caret) + insert.substring(caret + 2);

        int start = Math.max(0, column - prefixLength);
        text.beginBatchEdit();
        try {
            text.replace(line, start, line, column, insert);
            // caret position inside the inserted text
            int target = caret >= 0 ? caret : insert.length();
            int cl = line, cc = start;
            for (int i = 0; i < target; i++) {
                if (insert.charAt(i) == '\n') {
                    cl++;
                    cc = 0;
                } else cc++;
            }
            int added = importIt ? addImport(text, item.importFqn) : 0;
            editor.setSelection(cl + added, cc);
        } finally {
            text.endBatchEdit();
        }
    }

    /** @return number of lines inserted above the caret */
    private static int addImport(Content text, String fqn) {
        String line = "import " + fqn + ";";
        int last = -1, pkg = -1;
        int n = text.getLineCount();
        for (int i = 0; i < n; i++) {
            String s = text.getLineString(i).trim();
            if (s.equals(line)) return 0;
            if (s.startsWith("import ")) last = i;
            else if (s.startsWith("package ")) pkg = i;
            else if (s.length() > 0 && !s.startsWith("//") && !s.startsWith("/*") && !s.startsWith("*") && last < 0 && pkg < 0) break;
        }
        // same package: nothing to import
        if (last >= 0) {
            text.insert(last + 1, 0, line + "\n");
            return 1;
        }
        if (pkg >= 0) {
            text.insert(pkg + 1, 0, "\n" + line + "\n");
            return 2;
        }
        text.insert(0, 0, line + "\n\n");
        return 2;
    }
}
