package com.example.itrain_logistik;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Laedt uebersetzte Bezeichner aus {@code translations.properties}
 * (Format je Zeile: {@code schluessel.sprachcode=Text}). Gleiches Vorgehen
 * wie in iTrain_import_export, hier zunaechst nur fuer Deutsch/Englisch -
 * {@link #LANGUAGE_CODES} um weitere Sprachcodes ergaenzen und passende
 * Zeilen (und ein Flaggen-Icon unter {@code flags/<code>.png}) in
 * translations.properties nachtragen, dann erscheint die Sprache automatisch
 * im Voreinstellungen-Dropdown.
 */
public final class I18n {

    private static final String EXTERNAL_FILE_NAME = "translations.properties";

    public static final List<String> LANGUAGE_CODES = Uebersetzung.SPRACHEN;

    private static final Map<String, String> NATIVE_NAMES = new LinkedHashMap<>();
    static {
        NATIVE_NAMES.put("de", "Deutsch");
        NATIVE_NAMES.put("en", "English");
        NATIVE_NAMES.put("nl", "Nederlands");
        NATIVE_NAMES.put("fr", "Français");
        NATIVE_NAMES.put("es", "Español");
        NATIVE_NAMES.put("it", "Italiano");
    }

    private static I18n instance;

    private final Map<String, Map<String, String>> table = new HashMap<>();
    private final List<Runnable> languageChangeListeners = new ArrayList<>();
    private String currentLanguage = "de";

    private I18n() {
        load();
        // Gewählte Sprache (Voreinstellungen), sonst die des Systems (Anwender 09.11.2026)
        String gewaehlt = AppSettings.getInstance().getSprache();
        String systemLanguage = Locale.getDefault().getLanguage();
        if (gewaehlt != null && LANGUAGE_CODES.contains(gewaehlt)) {
            currentLanguage = gewaehlt;
        } else if (LANGUAGE_CODES.contains(systemLanguage)) {
            currentLanguage = systemLanguage;
        }
    }

    public static synchronized I18n getInstance() {
        if (instance == null) {
            instance = new I18n();
        }
        return instance;
    }

    private void load() {
        File external = new File(EXTERNAL_FILE_NAME);
        try (InputStream in = external.isFile()
                ? new FileInputStream(external)
                : I18n.class.getResourceAsStream(EXTERNAL_FILE_NAME)) {
            if (in == null) {
                System.err.println("translations.properties wurde weder im Arbeitsverzeichnis "
                        + "noch als Ressource gefunden - es werden nur die Schluessel angezeigt.");
                return;
            }
            Properties props = new Properties();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String propertyKey : props.stringPropertyNames()) {
                int lastDot = propertyKey.lastIndexOf('.');
                if (lastDot < 0) {
                    continue;
                }
                String baseKey = propertyKey.substring(0, lastDot);
                String lang = propertyKey.substring(lastDot + 1);
                if (!LANGUAGE_CODES.contains(lang)) {
                    continue;
                }
                table.computeIfAbsent(baseKey, k -> new HashMap<>()).put(lang, props.getProperty(propertyKey));
            }
        } catch (IOException ex) {
            System.err.println("Fehler beim Laden von translations.properties: " + ex.getMessage());
        }
    }

    public String t(String key, Object... args) {
        Map<String, String> variants = table.get(key);
        String template = null;
        if (variants != null) {
            template = variants.get(currentLanguage);
            if (template == null) {
                // ohne Eintrag in translations.properties: deutscher Text, den die Übersetzungsschicht übersetzt
                template = Uebersetzung.tr(variants.get("de"));
            }
        }
        if (template == null) {
            return key;
        }
        return substitute(template, args);
    }

    private static String substitute(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(String code) {
        if (!LANGUAGE_CODES.contains(code) || code.equals(currentLanguage)) {
            return;
        }
        currentLanguage = code;
        for (Runnable listener : new ArrayList<>(languageChangeListeners)) {
            listener.run();
        }
    }

    public void addLanguageChangeListener(Runnable listener) {
        languageChangeListeners.add(listener);
    }

    public void removeLanguageChangeListener(Runnable listener) {
        languageChangeListeners.remove(listener);
    }

    public String getNativeName(String code) {
        return NATIVE_NAMES.getOrDefault(code, code);
    }

    public String getFlagResourcePath(String code) {
        return "flags/" + code + ".png";
    }
}
