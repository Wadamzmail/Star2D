package com.star4droid.star2d.ui.ai;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.star4droid.star2d.ui.Dialogs;
import com.star4droid.star2d.ui.Tr;
import com.star4droid.star2d.ui.Ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small Markdown renderer for assistant replies (no external library). */
public final class Markdown {
    private Markdown() {}

    public interface CodeAction {
        /** Called for "Save to project" on a code block. */
        void onSave(String lang, String code);
    }

    /** Render text into the given column (clears it). star2d-actions blocks are skipped. */
    public static void render(Context c, LinearLayout out, String md, CodeAction action) {
        out.removeAllViews();
        String[] lines = md.replace("\r", "").split("\n", -1);
        StringBuilder para = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flush(c, out, para);
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append("\n");
                    i++;
                }
                if (lang.equals("star2d-actions")) continue;
                String cs = code.toString();
                if (cs.endsWith("\n")) cs = cs.substring(0, cs.length() - 1);
                out.addView(codeBlock(c, lang, cs, action));
                continue;
            }
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                flush(c, out, para);
                StringBuilder table = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith("|")) {
                    String row = lines[i].trim();
                    if (!row.matches("\\|?\\s*:?-{2,}.*")) table.append(row).append("\n");
                    i++;
                }
                i--;
                TextView t = Ui.text(c, table.toString().trim(), 12, Ui.TEXT);
                t.setTypeface(Typeface.MONOSPACE);
                HorizontalScrollView hs = new HorizontalScrollView(c);
                hs.addView(t);
                out.addView(hs, Ui.margins(Ui.matchWrap(), c, 0, 4, 0, 4));
                continue;
            }
            if (trimmed.isEmpty()) {
                flush(c, out, para);
                continue;
            }
            if (trimmed.startsWith("#")) {
                flush(c, out, para);
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') level++;
                TextView h = Ui.text(c, inline(trimmed.substring(level).trim()), level <= 1 ? 18 : (level == 2 ? 16.5f : 15), Ui.TEXT);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 2));
                out.addView(h);
                continue;
            }
            if (trimmed.matches("^([-*+]|\\d+[.)])\\s+.*")) {
                flush(c, out, para);
                boolean num = Character.isDigit(trimmed.charAt(0));
                String bullet = num ? trimmed.substring(0, trimmed.indexOf(num ? (trimmed.contains(".") && trimmed.indexOf('.') < 4 ? "." : ")") : " ") + 1) : "•";
                String body = trimmed.replaceFirst("^([-*+]|\\d+[.)])\\s+", "");
                int indent = line.length() - line.replaceAll("^\\s+", "").length();
                LinearLayout row = Ui.row(c);
                row.setGravity(android.view.Gravity.TOP);
                row.setPadding(Ui.dp(c, 4 + Math.min(indent, 8) * 4), Ui.dp(c, 1), 0, Ui.dp(c, 1));
                TextView b = Ui.text(c, bullet, 14, Ui.ACCENT);
                b.setPadding(0, 0, Ui.dp(c, 6), 0);
                row.addView(b);
                TextView t = Ui.text(c, inline(body), 14, Ui.TEXT);
                t.setMovementMethod(LinkMovementMethod.getInstance());
                t.setLineSpacing(0, 1.12f);
                row.addView(t, Ui.weight(1));
                out.addView(row);
                continue;
            }
            if (trimmed.startsWith(">")) {
                flush(c, out, para);
                TextView q = Ui.text(c, inline(trimmed.substring(1).trim()), 13.5f, Ui.TEXT_2);
                q.setTypeface(Typeface.defaultFromStyle(Typeface.ITALIC));
                q.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), 0, Ui.dp(c, 4));
                q.setBackground(Ui.rounded(0x22FFFFFF, Ui.dp(c, 4)));
                out.addView(q, Ui.margins(Ui.matchWrap(), c, 0, 2, 0, 2));
                continue;
            }
            if (para.length() > 0) para.append("\n");
            para.append(line);
        }
        flush(c, out, para);
    }

    private static void flush(Context c, LinearLayout out, StringBuilder para) {
        if (para.length() == 0) return;
        TextView t = Ui.text(c, inline(para.toString()), 14, Ui.TEXT);
        t.setLineSpacing(0, 1.15f);
        t.setTextIsSelectable(true);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setPadding(0, Ui.dp(c, 2), 0, Ui.dp(c, 2));
        out.addView(t);
        para.setLength(0);
    }

    private static final Pattern INLINE = Pattern.compile("`([^`]+)`|\\*\\*([^*]+)\\*\\*|__([^_]+)__|\\*([^*\\s][^*]*)\\*|\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

    public static CharSequence inline(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher m = INLINE.matcher(s);
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            int start = sb.length();
            if (m.group(1) != null) {
                sb.append(m.group(1));
                sb.setSpan(new TypefaceSpan("monospace"), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new BackgroundColorSpan(0x33FFFFFF), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(0xFFFFCC80), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null || m.group(3) != null) {
                sb.append(m.group(2) != null ? m.group(2) : m.group(3));
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(4) != null) {
                sb.append(m.group(4));
                sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(5) != null) {
                sb.append(m.group(5));
                sb.setSpan(new URLSpan(m.group(6)), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        sb.append(s.substring(last));
        return sb;
    }

    private static View codeBlock(Context c, String lang, String code, CodeAction action) {
        LinearLayout box = Ui.column(c);
        box.setBackground(Ui.rounded(0xFF0F1114, Ui.dp(c, 8), Ui.BORDER, 1));
        LinearLayout head = Ui.row(c);
        head.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 4), Ui.dp(c, 4));
        TextView l = Ui.text(c, lang.isEmpty() ? "code" : lang, 11.5f, Ui.TEXT_3);
        head.addView(l, Ui.weight(1));
        TextView copy = Ui.text(c, Tr.t("copy", "Copy"), 12, Ui.ACCENT);
        copy.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
        copy.setOnClickListener(v -> Dialogs.copy(c, code));
        head.addView(copy);
        if (action != null && (lang.equalsIgnoreCase("java") || code.contains("class ") || lang.isEmpty())) {
            TextView save = Ui.text(c, Tr.t("ui_save_to_project", "Save to project"), 12, Ui.SUCCESS);
            save.setPadding(Ui.dp(c, 8), Ui.dp(c, 4), Ui.dp(c, 8), Ui.dp(c, 4));
            save.setOnClickListener(v -> action.onSave(lang, code));
            head.addView(save);
        }
        box.addView(head);
        TextView t = Ui.text(c, highlight(code), 12.5f, 0xFFE6EDF3);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(c, 10), Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 10));
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.addView(t);
        box.addView(hs);
        LinearLayout.LayoutParams lp = Ui.margins(Ui.matchWrap(), c, 0, 6, 0, 6);
        box.setLayoutParams(lp);
        return box;
    }

    private static final Pattern KW = Pattern.compile("\\b(public|private|protected|class|extends|implements|void|int|float|double|boolean|String|new|return|if|else|for|while|true|false|null|import|package|static|final|this|break|continue)\\b");
    private static final Pattern STR = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"");
    private static final Pattern COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern NUM = Pattern.compile("\\b\\d+(\\.\\d+)?f?\\b");

    /** Very light Java syntax coloring. */
    static CharSequence highlight(String code) {
        SpannableStringBuilder sb = new SpannableStringBuilder(code);
        color(sb, NUM.matcher(code), 0xFFB5CEA8);
        color(sb, KW.matcher(code), 0xFF569CD6);
        color(sb, STR.matcher(code), 0xFFCE9178);
        color(sb, COMMENT.matcher(code), 0xFF6A9955);
        return sb;
    }

    private static void color(SpannableStringBuilder sb, Matcher m, int col) {
        while (m.find()) sb.setSpan(new ForegroundColorSpan(col), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
