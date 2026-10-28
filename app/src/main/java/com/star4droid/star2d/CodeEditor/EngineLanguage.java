package com.star4droid.star2d.CodeEditor;
import android.os.Bundle;
import android.view.View;

import com.star4droid.star2d.Helpers.Project;
import com.star4droid.star2d.Utils;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.util.ArrayList;
import org.eclipse.tm4e.languageconfiguration.internal.model.IndentationRules;

import java.lang.ref.WeakReference;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateSymbolPairMatch;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.SymbolPairMatch;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;

public class EngineLanguage extends EmptyLanguage {
	private final TextMateLanguage mTextMateLanguage;
	private final IndentationRules mIndentationRules;
	private final CodeEditor mEditor;
	private SmartEditor smart;
	
	// للتحكم في سلوك الإكمال التلقائي
	private boolean autoCompletionEnabled = true;
	
	public EngineLanguage(CodeEditor editor, SmartEditor smart) {
		mEditor = editor;
		this.smart = smart;
		
		String scope = GrammarRegistry.getInstance().loadGrammars("editor/languages.json").get(0).getScopeName();
		mTextMateLanguage = TextMateLanguage.create(scope, false);
		mIndentationRules = GrammarRegistry.getInstance().findLanguageConfiguration(scope).getIndentationRules();
		
		// تعطيل الإكمال التلقائي المدمج في TextMate لأننا نستخدم نظامنا الخاص
		mTextMateLanguage.setAutoCompleteEnabled(false);
		mTextMateLanguage.setTabSize(editor.getTabWidth());
		mTextMateLanguage.getSymbolPairs().setEnabled(true);
		
		// تحسين إعدادات المحرر للأداء
		configureEditorForPerformance(editor);
	}
	
	/**
	 * تحسين إعدادات المحرر للأداء الأفضل
	 */
	private void configureEditorForPerformance(CodeEditor editor) {
		// تفعيل التحديث التدريجي للأداء
		editor.setInterceptParentHorizontalScrollIfNeeded(true);
	}
	
	/**
	 * تمكين/تعطيل الإكمال التلقائي
	 */
	public void setAutoCompletionEnabled(boolean enabled) {
		this.autoCompletionEnabled = enabled;
	}
	
	public boolean isAutoCompletionEnabled() {
		return autoCompletionEnabled;
	}
	
	@Override
	public int getIndentAdvance(ContentReference content, int line, int column) {
		return getIndentAdvance(content.getLine(line).substring(0, column));
	}
	
	public int getIndentAdvance(String line) {
		return line.matches(mIndentationRules.increaseIndentPattern.pattern()) ? mEditor.getTabWidth() : 0;
	}
	
	private final NewlineHandler[] mNewlineHandlers = new NewlineHandler[]{new EndwiseNewlineHandler()};
	
	@Override
	public NewlineHandler[] getNewlineHandlers() {
		return mNewlineHandlers;
	}
	
	@Override
	public SymbolPairMatch getSymbolPairs() {
		return mTextMateLanguage.getSymbolPairs();
	}
	
	@Override
	public AnalyzeManager getAnalyzeManager() {
		return mTextMateLanguage.getAnalyzeManager();
	}
	
	@Override
	public void requireAutoComplete(ContentReference content, CharPosition position, CompletionPublisher publisher, Bundle extraArguments) {
		// فحص إذا كان الإكمال التلقائي مفعل
		if (!autoCompletionEnabled) {
			return;
		}
		
		try {
			if (smart != null) smart.requireAutoComplete(content, position, publisher);
		} catch (io.github.rosemoe.sora.lang.completion.CompletionCancelledException e) {
			throw e;
		} catch (Exception e) {
			// never break typing because of a completion problem
			android.util.Log.e("EngineLanguage", "Auto-completion error: " + e);
		}
	}
	
	public class EndwiseNewlineHandler implements NewlineHandler {
		private static final String ENDWISE_PATTERN = "^((?!(--)).)*(\\b(else|function|then|do|repeat)\\b((?!\\b(end|until)\\b).)*)$";
		
		private final StringBuilder mStringBuilder = new StringBuilder();
		
		@Override
		public boolean matchesRequirement(Content text, CharPosition position, Styles style) {
			String line = text.getLineString(position.line);
			String beforeText = line.substring(0, position.column);
			
			return beforeText.matches(ENDWISE_PATTERN);
		}
		
		@Override
		public NewlineHandleResult handleNewline(Content text, CharPosition position, Styles style, int tabSize) {
			return new NewlineHandleResult("", 0);
		}
		
	}
	
	public static String repeat(String str, int count) {
		if (str == null || str.isEmpty() || count <= 0) {
			return "";
		}
		
		StringBuilder sb = new StringBuilder(str.length() * count);
		for (int i = 0; i < count; i++) {
			sb.append(str);
		}
		
		return sb.toString();
	}
	
	public static boolean isOnlySpaces(String str) {
		if (str == null || str.isEmpty()) {
			return false;
		}
		
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return false;
			}
		}
		
		return true;
	}
	
	public static int leadingSpaceCount(String str) {
		int count = 0;
		int index = 0;
		
		while (index < str.length() && str.charAt(index) == ' ') {
			count++;
			index++;
		}
		
		return count;
	}
	
}
