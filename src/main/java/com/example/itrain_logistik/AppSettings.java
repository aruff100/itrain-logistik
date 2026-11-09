package com.example.itrain_logistik;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Dauerhaft gespeicherte Anwendungseinstellungen, ueber die Java-Preferences-
 * API abgelegt (unter Windows im Registry-Zweig
 * HKEY_CURRENT_USER\Software\JavaSoft\Prefs\com\example\itrain_logistik).
 * Gleiches Vorgehen wie {@code AppSettings} in iTrain_import_export, von dem
 * dieses Projekt laut Auftrag die Voreinstellungen-Struktur uebernimmt
 * (Reiter "Ansicht"/"Uhr"/"Pfade" - siehe SettingsDialog).
 */
public final class AppSettings {

    private static final String KEY_LOGISTIK_DIRECTORY = "logistikDirectory";
    private static final String KEY_LAYOUT_FILES_DIRECTORY = "layoutFilesDirectory";
    private static final String KEY_BACKUP_DIRECTORY = "backupDirectory";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SHOW_DATA_EDITOR = "showDataEditor";
    private static final String KEY_RECENT_FILES = "recentFiles";
    private static final String KEY_AUTO_UPDATE_CHECK = "autoUpdateCheck";
    private static final String KEY_PREFERRED_SCREEN = "preferredScreen";
    private static final String KEY_DIALOG_OFFSET = "dialogOffset";
    private static final String KEY_CUSTOM_COLOR_ACTIVE = "customColorActive";
    private static final String KEY_CUSTOM_BACKGROUND_COLOR = "customBackgroundColor";
    private static final String KEY_CUSTOM_TEXT_COLOR = "customTextColor";
    private static final String KEY_SHOW_CLOCK = "showClock";
    private static final String KEY_SHOW_CLOCK_SECONDS = "showClockSeconds";
    private static final String KEY_SYMBOL_DIRECTORY = "symbolDirectory";
    private static final String KEY_UPDATE_INTERVAL_LOKOMOTIVEN = "updateIntervalLokomotiven";
    private static final String KEY_UPDATE_INTERVAL_WAGEN = "updateIntervalWagen";
    private static final String KEY_UPDATE_INTERVAL_ZUEGE = "updateIntervalZuege";
    private static final String KEY_UPDATE_INTERVAL_BLOCKPOSITIONEN = "updateIntervalBlockpositionen";
    private static final String KEY_UPDATE_INTERVAL_BLOECKE = "updateIntervalBloecke";
    private static final String KEY_DEFAULT_MASSSTAB = "defaultMassstab";
    private static final String KEY_DEFAULT_MODELLBAHN_FAKTOR = "defaultModellbahnFaktor";
    private static final String KEY_EPOCHE = "epoche";

    /** Vorsatz fuer die gemerkte Fensterlage, je Fenster ein eigener Eintrag. */
    private static final String WINDOW_PREFIX = "window.";

    public static final String SCREEN_REMEMBER = "remember";

    /**
     * Vorbelegung des Pfads fuer Logistik-Dateien (Reiter "Pfade"): wie bei
     * {@link #defaultLayoutFilesDirectory()} zur Laufzeit aus {@code
     * user.home} gebildet, nicht mit einem festen Windows-Pfad ("Anwender")
     * vorverdrahtet - so passt die Vorbelegung auch unter macOS und Linux
     * (dort z.B. {@code /home/<user>/iTrain/logistic} bzw. {@code
     * /Users/<user>/iTrain/logistic}), nicht nur unter Windows.
     */
    public static String defaultLogistikDirectory() {
        return System.getProperty("user.home") + File.separator + "iTrain" + File.separator + "logistic";
    }

    /**
     * Vorbelegung des "Anfangsordner fuer Layout-Dateien": genau wie beim
     * Logistik-Pfad oben wird hier zur Laufzeit {@code user.home} +
     * "iTrain/layouts" gebildet - der Standard-Ordner, den iTrain selbst
     * fuer .tcd/.tcdz-Anlagendateien anlegt (verifiziert an einer echten
     * Installation: "<user.home>\iTrain\layouts" enthaelt dort die
     * Anlagendateien, "<user.home>\iTrain\images" die referenzierten
     * Lok-/Wagenbilder - siehe ImageResolver). So klappt die Vorbelegung auf
     * jedem Rechner (Windows, macOS, Linux) mit iTrain-Standardinstallation
     * automatisch, statt nur fuer ein bestimmtes Benutzerprofil.
     */
    public static String defaultLayoutFilesDirectory() {
        return System.getProperty("user.home") + File.separator + "iTrain" + File.separator + "layouts";
    }

    private static final int MAX_RECENT_FILES = 5;

    /**
     * Wert fuer "nur beim Start aktualisieren" bei den fuenf Zeitplan-
     * Einstellungen unten (Reiter "Aktualisierung") - dort sonst die Anzahl
     * Sekunden zwischen zwei automatischen Abgleichen mit der gekoppelten
     * .tcdz. Das eigentliche periodische Neueinlesen ist noch nicht
     * angebunden (siehe CLAUDE.md, Phase 2) - diese Einstellung ist bereits
     * vorbereitet, damit die Voreinstellungen-Oberflaeche dafuer steht.
     */
    public static final int UPDATE_ONLY_ON_START = 0;

    /** Auswahlmoeglichkeiten fuer die fuenf Zeitplan-Comboboxen, in Sekunden. */
    public static final int[] UPDATE_INTERVAL_OPTIONS_SECONDS = {0, 10, 30, 60, 300, 900};

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static AppSettings instance;

    private final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);

    private AppSettings() {
    }

    public static synchronized AppSettings getInstance() {
        if (instance == null) {
            instance = new AppSettings();
        }
        return instance;
    }

    /** Ordner, in dem die eigenen Logistik-Dateien abgelegt werden (Reiter "Pfade"). */
    public String getLogistikDirectory() {
        return prefs.get(KEY_LOGISTIK_DIRECTORY, defaultLogistikDirectory());
    }

    public void setLogistikDirectory(String path) {
        prefs.put(KEY_LOGISTIK_DIRECTORY, path);
    }

    /**
     * Startordner fuer den "Öffnen..."-Dialog (Reiter "Pfade"): der Ordner, in
     * dem die eigentlichen iTrain-Anlagendateien (.tcdz) liegen - siehe
     * Projektauftrag ("so dass ich direkt darauf zugreifen kann"). Dient
     * ausserdem als Ausgangspunkt fuer {@link ImageResolver}, um die in der
     * .tcdz relativ gespeicherten Bildpfade (z.B. "iTrain/images/...")
     * aufzuloesen.
     */
    public String getLayoutFilesDirectory() {
        return prefs.get(KEY_LAYOUT_FILES_DIRECTORY, defaultLayoutFilesDirectory());
    }

    public void setLayoutFilesDirectory(String path) {
        prefs.put(KEY_LAYOUT_FILES_DIRECTORY, path);
    }

    /** Ordner fuer die nummerierten Sicherungen der Logistik-Datei ("Backup laden..."). */
    public String getBackupDirectory() {
        String stored = prefs.get(KEY_BACKUP_DIRECTORY, null);
        return stored != null ? stored : getLogistikDirectory();
    }

    public void setBackupDirectory(String path) {
        prefs.put(KEY_BACKUP_DIRECTORY, path);
    }

    public String getTheme() {
        return prefs.get(KEY_THEME, THEME_LIGHT);
    }

    public void setTheme(String theme) {
        prefs.put(KEY_THEME, theme);
    }

    public boolean isCustomColorActive() {
        return prefs.getBoolean(KEY_CUSTOM_COLOR_ACTIVE, false);
    }

    public void setCustomColorActive(boolean active) {
        prefs.putBoolean(KEY_CUSTOM_COLOR_ACTIVE, active);
    }

    public String getCustomBackgroundColor() {
        return prefs.get(KEY_CUSTOM_BACKGROUND_COLOR, null);
    }

    public void setCustomBackgroundColor(String web) {
        prefs.put(KEY_CUSTOM_BACKGROUND_COLOR, web);
    }

    public String getCustomTextColor() {
        return prefs.get(KEY_CUSTOM_TEXT_COLOR, null);
    }

    public void setCustomTextColor(String web) {
        prefs.put(KEY_CUSTOM_TEXT_COLOR, web);
    }

    /** "Daten aendern": eigene Datenfelder je Lok/Wagen/Block direkt bearbeitbar machen. */
    public boolean getShowDataEditor() {
        return prefs.getBoolean(KEY_SHOW_DATA_EDITOR, true);
    }

    public void setShowDataEditor(boolean value) {
        prefs.putBoolean(KEY_SHOW_DATA_EDITOR, value);
    }

    /**
     * "Beim Start automatisch nach Updates suchen". Der eigentliche Abruf
     * (Versions-URL) ist fuer dieses erste Geruest noch nicht verdrahtet -
     * siehe TODO in UpdateChecker.
     */
    public boolean getAutoUpdateCheckEnabled() {
        return prefs.getBoolean(KEY_AUTO_UPDATE_CHECK, true);
    }

    public void setAutoUpdateCheckEnabled(boolean value) {
        prefs.putBoolean(KEY_AUTO_UPDATE_CHECK, value);
    }

    public boolean getShowClock() {
        return prefs.getBoolean(KEY_SHOW_CLOCK, true);
    }

    public void setShowClock(boolean value) {
        prefs.putBoolean(KEY_SHOW_CLOCK, value);
    }

    public boolean getShowClockSeconds() {
        return prefs.getBoolean(KEY_SHOW_CLOCK_SECONDS, false);
    }

    public void setShowClockSeconds(boolean value) {
        prefs.putBoolean(KEY_SHOW_CLOCK_SECONDS, value);
    }

    public String getPreferredScreen() {
        return prefs.get(KEY_PREFERRED_SCREEN, SCREEN_REMEMBER);
    }

    public void setPreferredScreen(String value) {
        prefs.put(KEY_PREFERRED_SCREEN, value);
    }

    public String getDialogOffset() {
        return prefs.get(KEY_DIALOG_OFFSET, null);
    }

    public void setDialogOffset(String value) {
        if (value == null) {
            prefs.remove(KEY_DIALOG_OFFSET);
        } else {
            prefs.put(KEY_DIALOG_OFFSET, value);
        }
    }

    public String getWindowState(String windowKey) {
        return prefs.get(WINDOW_PREFIX + windowKey, null);
    }

    public void setWindowState(String windowKey, String value) {
        prefs.put(WINDOW_PREFIX + windowKey, value);
    }

    /** Zuletzt geoeffnete .tcdz/.tcd-Dateien, neueste zuerst (Menue "Zuletzt verwendet..."). */
    public List<String> getRecentFiles() {
        String joined = prefs.get(KEY_RECENT_FILES, "");
        if (joined.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(joined.split("\\|")));
    }

    public void addRecentFile(String path) {
        List<String> files = getRecentFiles();
        files.remove(path);
        files.add(0, path);
        while (files.size() > MAX_RECENT_FILES) {
            files.remove(files.size() - 1);
        }
        prefs.put(KEY_RECENT_FILES, String.join("|", files));
    }

    /**
     * Ordner, in dem die .svg-Symboldateien liegen (Reiter "Pfade") - siehe
     * {@link SymbolRegistry}. Vorbelegung: Unterordner "symbole" im
     * Logistik-Ordner, damit beide zusammen an einer Stelle liegen.
     */
    public String getSymbolDirectory() {
        String stored = prefs.get(KEY_SYMBOL_DIRECTORY, null);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return getLogistikDirectory() + File.separator + "symbole";
    }

    public void setSymbolDirectory(String path) {
        prefs.put(KEY_SYMBOL_DIRECTORY, path);
    }

    /** Eigene Mengen-Vorschläge (siehe {@link MengenEditor}), eine Zeile je Vorschlag. */
    public String getMengenVorschlaege() {
        return prefs.get("mengenVorschlaege", "");
    }

    public void setMengenVorschlaege(String text) {
        prefs.put("mengenVorschlaege", text == null ? "" : text);
    }

    /** Wie lange (1-5 s) eine Verbindungslinie nach einer Übergabe als Lauflicht leuchtet. */
    public int getLauflichtSekunden() {
        return Math.max(1, Math.min(5, prefs.getInt("lauflichtSekunden", 3)));
    }

    public void setLauflichtSekunden(int sekunden) {
        prefs.putInt("lauflichtSekunden", Math.max(1, Math.min(5, sekunden)));
    }

    // ---- Anlage-Symbole: eigene Farbkombination, getrennt von der Programmfarbe (Anwender 12.10.2026)

    public static final String SYMBOL_FUELLUNG_STANDARD = "#ffffff";
    public static final String SYMBOL_SCHRIFT_STANDARD = "#1a1a1a";

    /** Füllfarbe der Symbol-Kästen auf der Anlage. */
    public String getSymbolFuellung() {
        return prefs.get("symbolFuellung", SYMBOL_FUELLUNG_STANDARD);
    }

    public void setSymbolFuellung(String web) {
        prefs.put("symbolFuellung", web);
    }

    /** Schriftfarbe in den Symbol-Kästen (Namen, Gleise, Hinweise; die dunklen Schilder bleiben weiß). */
    public String getSymbolSchrift() {
        return prefs.get("symbolSchrift", SYMBOL_SCHRIFT_STANDARD);
    }

    public void setSymbolSchrift(String web) {
        prefs.put("symbolSchrift", web);
    }

    // ---- Pfeil beim Ein-/Aussteigen (Anwender 22.10.2026: deutlicher, dicker schwarzer Rahmen, orange Füllung)

    public static final String PFEIL_FUELLUNG_STANDARD = "#ff8c00";
    public static final String PFEIL_RAHMEN_STANDARD = "#000000";

    public String getPfeilFuellung() {
        return prefs.get("pfeilFuellung", PFEIL_FUELLUNG_STANDARD);
    }

    public void setPfeilFuellung(String web) {
        prefs.put("pfeilFuellung", web);
    }

    public String getPfeilRahmen() {
        return prefs.get("pfeilRahmen", PFEIL_RAHMEN_STANDARD);
    }

    public void setPfeilRahmen(String web) {
        prefs.put("pfeilRahmen", web);
    }

    /** Symbole, Verbindungen und Schilder mit Schatten vom Hintergrund abheben. */
    public boolean isSymbol3d() {
        return prefs.getBoolean("symbol3d", true);
    }

    public void setSymbol3d(boolean an) {
        prefs.putBoolean("symbol3d", an);
    }

    // ---- Quelle der Zugereignisse (Menü Einstellungen: Simulator oder iTrain, höchstens eine)

    public static final String QUELLE_SIMULATOR = "SIMULATOR";
    public static final String QUELLE_ITRAIN = "ITRAIN";

    /** "SIMULATOR", "ITRAIN" oder "" (keine gewählt). */
    public String getTriggerQuelle() {
        return prefs.get("triggerQuelle", "");
    }

    public void setTriggerQuelle(String quelle) {
        prefs.put("triggerQuelle", quelle == null ? "" : quelle);
    }

    // ---- Verbindung zu iTrain (Voreinstellungen > iTrain, Anwender 14.10.2026)

    /** Rechner, auf dem iTrain läuft (Name oder IP-Adresse); "localhost" = dieser Rechner. */
    public String getItrainRechner() {
        return prefs.get("itrainRechner", "localhost");
    }

    public void setItrainRechner(String rechner) {
        prefs.put("itrainRechner", rechner == null || rechner.isBlank() ? "localhost" : rechner.trim());
    }

    /** Port des iTrain-Servers (Vorgabe 7120 laut Handbuch iTrain 6.0, Netzwerk > Server-Modus). */
    public int getItrainPort() {
        return prefs.getInt("itrainPort", 7120);
    }

    public void setItrainPort(int port) {
        prefs.putInt("itrainPort", Math.max(1, Math.min(65535, port)));
    }

    /** Port der Browser-Ansicht (Anwender 09.11.2026, Vorgabe 8090). */
    public int getWebPort() {
        return prefs.getInt("webPort", Webansicht.PORT_VORGABE);
    }

    public void setWebPort(int port) {
        prefs.putInt("webPort", Math.max(1, Math.min(65535, port)));
    }

    /** Browser-Ansicht eingeschaltet (Vorgabe an). */
    /**
     * Abgeschaltete Dialoge („Dialog nicht mehr anzeigen“, Anwender 09.11.2026): Schlüssel -&gt; Titel, gespeichert
     * als Zeilen „schluessel=Titel“. Voreinstellungen > Ansicht blendet sie wieder ein.
     */
    public java.util.Map<String, String> getAbgeschalteteDialoge() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String z : prefs.get("dialogeAus", "").split("\n")) {
            int i = z.indexOf('=');
            if (i > 0) {
                m.put(z.substring(0, i), z.substring(i + 1));
            }
        }
        return m;
    }

    public boolean isDialogAbgeschaltet(String schluessel) {
        return getAbgeschalteteDialoge().containsKey(schluessel);
    }

    public void setDialogAbgeschaltet(String schluessel, String titel, boolean aus) {
        java.util.Map<String, String> m = getAbgeschalteteDialoge();
        if (aus) {
            m.put(schluessel, titel == null ? schluessel : titel.replace("\n", " "));
        } else {
            m.remove(schluessel);
        }
        StringBuilder b = new StringBuilder();
        m.forEach((k, v) -> b.append(k).append('=').append(v).append('\n'));
        prefs.put("dialogeAus", b.toString());
    }

    public void alleDialogeEinblenden() {
        prefs.remove("dialogeAus");
    }

    /** Höchstzahl der Reiter (Tabs + Module) der Übersicht (Anwender 09.11.2026), Vorgabe 8. */
    public int getMaxReiter() {
        return prefs.getInt("maxReiter", 8);
    }

    public void setMaxReiter(int n) {
        prefs.putInt("maxReiter", Math.max(1, Math.min(50, n)));
    }

    /** In der Übersicht ausgeblendete Reiter (Ids der Tabs/Module); im Editor stehen immer alle. */
    public java.util.Set<String> getReiterAusgeblendet() {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        for (String t : prefs.get("reiterAus", "").split(",")) {
            if (!t.isBlank()) {
                s.add(t.trim());
            }
        }
        return s;
    }

    public void setReiterAusgeblendet(String id, boolean aus) {
        java.util.Set<String> s = getReiterAusgeblendet();
        if (aus) {
            s.add(id);
        } else {
            s.remove(id);
        }
        prefs.put("reiterAus", String.join(",", s));
    }

    /** Sprache der Oberfläche (de, en, nl, fr, es, it), null = Sprache des Systems. Gilt nach Neustart. */
    public String getSprache() {
        return prefs.get("sprache", null);
    }

    public void setSprache(String code) {
        prefs.put("sprache", code);
    }

    // ---- Geräusche (Anwender 09.11.2026, siehe Geraeusche)

    public Geraeusche.Modus getGeraeuscheModus() {
        try {
            return Geraeusche.Modus.valueOf(prefs.get("geraeuscheModus", Geraeusche.Modus.EINMAL.name()));
        } catch (IllegalArgumentException ex) {
            return Geraeusche.Modus.EINMAL;
        }
    }

    public void setGeraeuscheModus(Geraeusche.Modus m) {
        prefs.put("geraeuscheModus", m.name());
    }

    /** Lautstärke 0-100, Vorgabe 70. */
    public int getGeraeuscheLautstaerke() {
        return Math.max(0, Math.min(100, prefs.getInt("geraeuscheLautstaerke", 70)));
    }

    public void setGeraeuscheLautstaerke(int v) {
        prefs.putInt("geraeuscheLautstaerke", Math.max(0, Math.min(100, v)));
    }

    /** Ordner der Geräusche; Vorgabe Unterordner „Geräusche“ im Logistik-Ordner. */
    public String getGeraeuscheOrdner() {
        String stored = prefs.get("geraeuscheOrdner", null);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return getLogistikDirectory() + File.separator + "Geräusche";
    }

    public void setGeraeuscheOrdner(String path) {
        prefs.put("geraeuscheOrdner", path);
    }

    /** Geräusche auch in der Browser-Ansicht (beim Antippen eines Kastens), Vorgabe an. */
    public boolean isGeraeuscheImBrowser() {
        return prefs.getBoolean("geraeuscheBrowser", true);
    }

    public void setGeraeuscheImBrowser(boolean an) {
        prefs.putBoolean("geraeuscheBrowser", an);
    }

    /** Symbolnamen, deren mitgeliefertes Geräusch schon ausgepackt wurde (gelöschte kommen nicht wieder). */
    public java.util.Set<String> getGeraeuscheAusgepackt() {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        for (String t : prefs.get("geraeuscheAusgepackt", "").split("\\|")) {
            if (!t.isBlank()) {
                s.add(t);
            }
        }
        return s;
    }

    public void setGeraeuscheAusgepackt(java.util.Set<String> namen) {
        prefs.put("geraeuscheAusgepackt", String.join("|", namen));
    }

    /** Port des Zuganzeigers im Browser (Anwender 09.11.2026, Vorgabe 8091). */
    public int getAnzeigerWebPort() {
        return prefs.getInt("anzeigerWebPort", ZuganzeigerWeb.PORT_VORGABE);
    }

    public void setAnzeigerWebPort(int port) {
        prefs.putInt("anzeigerWebPort", Math.max(1, Math.min(65535, port)));
    }

    public boolean isAnzeigerWebAktiv() {
        return prefs.getBoolean("anzeigerWebAktiv", true);
    }

    public void setAnzeigerWebAktiv(boolean an) {
        prefs.putBoolean("anzeigerWebAktiv", an);
    }

    public boolean isWebAktiv() {
        return prefs.getBoolean("webAktiv", true);
    }

    public void setWebAktiv(boolean an) {
        prefs.putBoolean("webAktiv", an);
    }

    /** Port der XTC-Schnittstelle von iTrain (Anwender 08.11.2026, Vorgabe 7128). */
    public int getXtcPort() {
        return prefs.getInt("xtcPort", XtcVerbindung.PORT_VORGABE);
    }

    public void setXtcPort(int port) {
        prefs.putInt("xtcPort", Math.max(1, Math.min(65535, port)));
    }

    /** Zugereignisse kommen nur noch über XTC (Anwender 09.11.2026: Aktionen „Befehlszeile“ entfernt). */
    public boolean isXtcAktiv() {
        return true;
    }

    /** Zeit-Vorgaben des Simulators in Sekunden je Vorgang (0 = Einfahrt, 1 = Warten, 2 = Abfahrt, 3 = Löschen). */
    public int getSimulatorSekunden(int vorgang) {
        int[] standard = {5, 20, 5, 3};
        return Math.max(1, prefs.getInt("simulatorSekunden" + vorgang, standard[vorgang]));
    }

    public void setSimulatorSekunden(int vorgang, int sekunden) {
        prefs.putInt("simulatorSekunden" + vorgang, Math.max(1, sekunden));
    }

    public int getUpdateIntervalLokomotiven() {
        return prefs.getInt(KEY_UPDATE_INTERVAL_LOKOMOTIVEN, UPDATE_ONLY_ON_START);
    }

    public void setUpdateIntervalLokomotiven(int seconds) {
        prefs.putInt(KEY_UPDATE_INTERVAL_LOKOMOTIVEN, seconds);
    }

    public int getUpdateIntervalWagen() {
        return prefs.getInt(KEY_UPDATE_INTERVAL_WAGEN, UPDATE_ONLY_ON_START);
    }

    public void setUpdateIntervalWagen(int seconds) {
        prefs.putInt(KEY_UPDATE_INTERVAL_WAGEN, seconds);
    }

    public int getUpdateIntervalZuege() {
        return prefs.getInt(KEY_UPDATE_INTERVAL_ZUEGE, UPDATE_ONLY_ON_START);
    }

    public void setUpdateIntervalZuege(int seconds) {
        prefs.putInt(KEY_UPDATE_INTERVAL_ZUEGE, seconds);
    }

    public int getUpdateIntervalBlockpositionen() {
        return prefs.getInt(KEY_UPDATE_INTERVAL_BLOCKPOSITIONEN, UPDATE_ONLY_ON_START);
    }

    public void setUpdateIntervalBlockpositionen(int seconds) {
        prefs.putInt(KEY_UPDATE_INTERVAL_BLOCKPOSITIONEN, seconds);
    }

    public int getUpdateIntervalBloecke() {
        return prefs.getInt(KEY_UPDATE_INTERVAL_BLOECKE, UPDATE_ONLY_ON_START);
    }

    public void setUpdateIntervalBloecke(int seconds) {
        prefs.putInt(KEY_UPDATE_INTERVAL_BLOECKE, seconds);
    }

    /**
     * Vorlage fuer den Maßstab neuer Lokomotiven (Reiter "Vorlagen") - siehe
     * Projektauftrag "Den Maßstab sowie den Modellbahn-Faktor setzen wir in
     * den Einstellungen. Das ist dann eine Vorlage für alle Daten, kann aber
     * geändert werden." Wirkt nur, solange bei der einzelnen Lokomotive noch
     * kein eigener Wert gesetzt wurde (siehe {@link LogistikFeldRow#textWithDefault}).
     */
    public String getDefaultMassstab() {
        return prefs.get(KEY_DEFAULT_MASSSTAB, "");
    }

    public void setDefaultMassstab(String value) {
        prefs.put(KEY_DEFAULT_MASSSTAB, value == null ? "" : value);
    }

    /** Vorlage fuer den Modellbahn-Faktor neuer Lokomotiven - siehe {@link #getDefaultMassstab()}. */
    public String getDefaultModellbahnFaktor() {
        return prefs.get(KEY_DEFAULT_MODELLBAHN_FAKTOR, "");
    }

    public void setDefaultModellbahnFaktor(String value) {
        prefs.put(KEY_DEFAULT_MODELLBAHN_FAKTOR, value == null ? "" : value);
    }

    /**
     * Epoche (1-6) der Anlage - steuert, welche Reisenden-Klassen im
     * Personenverkehr vorkommen (seit dem Echtzeit-Kern nur noch Anzeige, es gibt nur
     * 1. und 2. Klasse). Vorbelegung Epoche 3 (mangels Vorgabe im
     * Projektauftrag als neutrale Mitte gewaehlt - Epoche 3-6 verhalten sich
     * ohnehin identisch: nur 1./2. Klasse).
     */
    public int getEpoche() {
        int wert = prefs.getInt(KEY_EPOCHE, 3);
        return wert < 1 ? 1 : Math.min(wert, 6);
    }

    public void setEpoche(int epoche) {
        prefs.putInt(KEY_EPOCHE, Math.max(1, Math.min(epoche, 6)));
    }

    // ------------------------------------------------------------ Spalten je Tabelle (Spalten.java)

    private static String spaltenSchluessel(String tabelle) {
        String k = "spalten." + tabelle;
        return k.length() <= Preferences.MAX_KEY_LENGTH ? k : "spalten." + Integer.toHexString(tabelle.hashCode());
    }

    /** Ausgeblendete Spalten einer Tabelle (Kennungen wie in {@link Spalten}). */
    public java.util.Set<String> getAusgeblendeteSpalten(String tabelle) {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        String v = prefs.get(spaltenSchluessel(tabelle), "");
        for (String t : v.split("\\|")) {
            if (!t.isBlank()) {
                s.add(t);
            }
        }
        return s;
    }

    public void setAusgeblendeteSpalten(String tabelle, java.util.Collection<String> aus) {
        if (aus.isEmpty()) {
            prefs.remove(spaltenSchluessel(tabelle));
        } else {
            prefs.put(spaltenSchluessel(tabelle), String.join("|", aus));
        }
    }

    /** Zoom des Zuganzeigers (0,5 bis 3, Vorgabe 1). */
    /** Raster in „Anlage bearbeiten“ mit Einrasten der Symbole (Anwender 27.10.2026). */
    public boolean isEditorRaster() {
        return prefs.getBoolean("editor.raster", true);
    }

    public void setEditorRaster(boolean an) {
        prefs.putBoolean("editor.raster", an);
    }

    /** Zuganzeiger: geeignete Züge im Text vorschlagen, je Anzeiger (Anwender 27.10.2026). */
    public boolean isAnzeigerZuegeVorschlagen(String art) {
        return prefs.getBoolean("anzeiger.zuege." + art, true);
    }

    public void setAnzeigerZuegeVorschlagen(String art, boolean an) {
        prefs.putBoolean("anzeiger.zuege." + art, an);
    }

    public double getAnzeigerZoom() {
        return Math.max(0.5, Math.min(3.0, prefs.getDouble("anzeiger.zoom", 1.0)));
    }

    public void setAnzeigerZoom(double zoom) {
        prefs.putDouble("anzeiger.zoom", zoom);
    }

    /** Tempo der Laufschrift im Zuganzeiger (0,25 bis 3, Vorgabe 1). */
    public double getAnzeigerTempo() {
        return Math.max(0.25, Math.min(3.0, prefs.getDouble("anzeiger.tempo", 1.0)));
    }

    public void setAnzeigerTempo(double tempo) {
        prefs.putDouble("anzeiger.tempo", tempo);
    }
}
