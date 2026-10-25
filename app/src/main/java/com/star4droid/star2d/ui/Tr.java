package com.star4droid.star2d.ui;

import android.content.Context;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Android-side translations. Reads the same i18n/*.properties files used by the LibGDX
 * editor (Lang.getTrans) so both UIs share one set of strings.
 */
public final class Tr {
    private static Properties current, fallback;
    private static String loadedLang = null;

    private Tr() {}

    public static synchronized void init(Context c) {
        String lang = Ui.prefs(c).getString("lang", "en");
        if (lang.equals(loadedLang) && current != null) return;
        fallback = load(c, "i18n/strings.properties");
        current = load(c, fileFor(lang));
        loadedLang = lang;
    }

    private static String fileFor(String lang) {
        switch (lang) {
            case "ar": return "i18n/strings_ar_SD.properties";
            case "fr": return "i18n/strings_fr_US.properties";
            case "br": return "i18n/strings_br_US.properties";
            case "ru": return "i18n/strings_ru_US.properties";
            case "es": return "i18n/strings_es_US.properties";
            default: return "i18n/strings_en_GB.properties";
        }
    }

    private static Properties load(Context c, String path) {
        Properties p = new Properties();
        try (InputStream in = c.getAssets().open(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        return p;
    }

    /** Translation for key, or the given English default when missing. */
    public static String t(String key, String def) {
        String v = current == null ? null : current.getProperty(key);
        if (v == null && fallback != null) v = fallback.getProperty(key);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    /** Same key normalisation as Lang.getTrans ("On Body Update" -> "onBodyUpdate"). */
    public static String t(String key) {
        if (key == null || key.isEmpty()) return "";
        String k = key.replace(" ", "");
        k = Character.toLowerCase(k.charAt(0)) + k.substring(1);
        String v = t(k, null);
        if (v == null) v = t(key, null);
        return v == null ? key : v;
    }
}
