package com.example.itrain_logistik;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;

/**
 * Verwaltet den Symbol-Katalog: fuer jede bekannte "Funktion" (Ebene-1-/
 * Ebene-2-Typ-Schluessel wie "BAHNHOF") wird bevorzugt eine gleichnamige
 * .svg-Datei aus dem in den Voreinstellungen eingestellten Symbolordner
 * geladen ({@link SymbolDef.Art#SVG}, siehe {@link #scanSymbolOrdner()}) -
 * fehlt sie, dient ein eingebautes Ersatz-Piktogramm aus {@link NetzIcons}
 * als Sicherheitsnetz ({@link SymbolDef.Art#ORIGINAL}). Beim allerersten
 * Start wird dafuer ein mitgeliefertes Standard-Symbolpaket in den
 * Symbolordner kopiert (Ressourcen unter {@code default-symbole/}), damit
 * von Anfang an echte .svg-Dateien vorliegen (siehe Projektauftrag "Die
 * Symbole ... sind immer .svg").
 * <p>
 * Legt der Anwender eine .svg-Datei in den Symbolordner, deren Dateiname
 * (ohne Endung) zu keiner bekannten Funktion passt, erscheint sie beim
 * naechsten Einlesen als "Zusaetzliches" Symbol - der Anwender muss ihr
 * dann im Symbol-Manager eine Funktion zuweisen (siehe
 * {@link #zuweisen(String, SymbolDef)}). Selbst angelegte einfache
 * Rechteck-Symbole ({@link SymbolDef.Art#RECHTECK}) bleiben zusaetzlich
 * moeglich und werden - anders als die .svg-Dateien, die schon durch ihre
 * blosse Anwesenheit im Ordner "persistiert" sind - ueber die
 * Java-Preferences-API abgelegt, wie {@code AppSettings}.
 */
public final class SymbolRegistry {

    /** Zusaetzliche, frei zuweisbare Original-Symbole (Schluessel -&gt; Anzeigename). */
    private static final Map<String, String> ZUSATZ_ORIGINALE = new LinkedHashMap<>();

    static {
        ZUSATZ_ORIGINALE.put("ROHSTOFFE", "Rohstoffe");
        ZUSATZ_ORIGINALE.put("FLUESSIGKEITEN", "Flüssigkeiten");
        ZUSATZ_ORIGINALE.put("STUECKGUT", "Stückgut");
        ZUSATZ_ORIGINALE.put("OFFENE_GUETER", "Offene Güter");
        ZUSATZ_ORIGINALE.put("PRODUKTION", "Produktion");
        ZUSATZ_ORIGINALE.put("WANDLUNG", "Wandlung");
        ZUSATZ_ORIGINALE.put("GUETER", "Güter");
        ZUSATZ_ORIGINALE.put("GUETERVERKEHR", "Güterverkehr");
        ZUSATZ_ORIGINALE.put("ANKOMMENDE_PERSONEN", "Ankommende Personen");
        ZUSATZ_ORIGINALE.put("ABREISENDE_PERSONEN", "Abreisende Personen");
        ZUSATZ_ORIGINALE.put("ANKOMMENDE_GUETER", "Ankommende Güter");
        ZUSATZ_ORIGINALE.put("ABGEHENDE_GUETER", "Abgehende Güter");
        ZUSATZ_ORIGINALE.put("KAPAZITAETEN", "Kapazitäten");
    }

    private static final String KEY_CUSTOM_IDS = "customIds";
    private static final String PREFIX_CUSTOM = "custom.";
    private static final String PREFIX_ZUORDNUNG = "zuordnung.";

    private static SymbolRegistry instance;

    private final Preferences prefs = Preferences.userRoot().node("/com/example/itrain_logistik/netzSymbole");

    private final Map<String, SymbolDef> katalog = new LinkedHashMap<>();
    private final Map<String, String> zuordnung = new LinkedHashMap<>();

    private SymbolRegistry() {
        for (NetzKnotenTyp t : NetzKnotenTyp.values()) {
            katalog.put(t.name(), new SymbolDef(t.name(), t.getLabel(), SymbolDef.Art.ORIGINAL, t.name()));
        }
        for (OrtTyp t : OrtTyp.values()) {
            katalog.put(t.name(), new SymbolDef(t.name(), t.getLabel(), SymbolDef.Art.ORIGINAL, t.name()));
        }
        for (Map.Entry<String, String> e : ZUSATZ_ORIGINALE.entrySet()) {
            katalog.putIfAbsent(e.getKey(), new SymbolDef(e.getKey(), e.getValue(), SymbolDef.Art.ORIGINAL, e.getKey()));
        }
        loadCustomSymbols();
        loadZuordnungen();
        infrastrukturZuordnungBereinigen();
        scanSymbolOrdner();
    }

    /**
     * Einmalig: Eine Zuordnung der Anlagen-Infrastruktur auf das Symbol einer anderen eingebauten Funktion
     * (z. B. Bahnhof) entstand fast sicher versehentlich ueber "Zur Anwendung" (dort war die
     * Anlagen-Infrastruktur vorausgewaehlt) und wird entfernt (11.10.2026).
     */
    private void infrastrukturZuordnungBereinigen() {
        if (prefs.getBoolean("bereinigt.infrastruktur", false)) {
            return;
        }
        String id = zuordnung.get("ANLAGEN_INFRASTRUKTUR");
        if (id != null && !id.equals("ANLAGEN_INFRASTRUKTUR") && allKnownKeys().contains(id)) {
            standardWiederherstellen("ANLAGEN_INFRASTRUKTUR");
        }
        prefs.putBoolean("bereinigt.infrastruktur", true);
    }

    /** Entfernt eine eigene Zuweisung: die Funktion zeigt wieder ihr eigenes Symbol. */
    public void standardWiederherstellen(String typ) {
        zuordnung.remove(typ);
        prefs.remove(PREFIX_ZUORDNUNG + typ);
    }

    /** true, wenn der Funktion ein anderes als ihr eigenes Symbol zugewiesen ist. */
    public boolean hatEigeneZuweisung(String typ) {
        String id = zuordnung.get(typ);
        return id != null && !id.equals(typ);
    }

    private static final String PREFIX_BESCHREIBUNG = "beschreibung.";

    private static final Map<String, String> STANDARD_BESCHREIBUNG = Map.ofEntries(
            Map.entry("ANLAGEN_INFRASTRUKTUR", "Fester Mittelpunkt, an dem alle Stationen hängen"),
            Map.entry("BAHNHOF", "Personenbahnhof, nur Bahnsteige"),
            Map.entry("HALTEPUNKT", "Kleine Station, Bahnsteige und Ladegleise"),
            Map.entry("GUETERBAHNHOF", "Station mit Ladegleisen, auch zu einem Bahnhof gehörend"),
            Map.entry("ANSCHLIESSER", "Gleisanschluss eines Betriebs, Bahnsteige und Ladegleise"),
            Map.entry("SCHATTENBAHNHOF", "Außenwelt: stellt Personen und Güter bereit und nimmt alles ab"),
            Map.entry("FABRIK", "Betrieb mit Rezepten (Produktion, Wandlung)"),
            Map.entry("LAGER", "Lager für Güter"),
            Map.entry("GUETERVERKEHR", "Übergabe: Güter kommen von außerhalb oder gehen hinaus"),
            Map.entry("STADT", "Ort mit Passagieren und Güterbedarf"),
            Map.entry("DORF", "Ort mit Passagieren und Güterbedarf"),
            Map.entry("LAENDLICH", "Ländlicher Ort mit wenig Aufkommen"));

    /** Beschreibung eines Symbols: vom Anwender eingetragen, sonst die mitgelieferte Vorgabe. */
    public String beschreibung(SymbolDef def) {
        String eigene = prefs.get(PREFIX_BESCHREIBUNG + def.getId(), null);
        if (eigene != null) {
            return eigene;
        }
        String vorgabe = STANDARD_BESCHREIBUNG.get(def.getId());
        if (vorgabe != null) {
            return vorgabe;
        }
        if (def.getArt() == SymbolDef.Art.RECHTECK) {
            return "Eigene Farbfläche";
        }
        if (def.getArt() == SymbolDef.Art.SVG && !allKnownKeys().contains(def.getId())) {
            return "Eigene Datei " + new File(def.getWert()).getName();
        }
        return "Eingebautes Symbol";
    }

    public void setBeschreibung(SymbolDef def, String text) {
        if (text == null || text.isBlank()) {
            prefs.remove(PREFIX_BESCHREIBUNG + def.getId());
        } else {
            prefs.put(PREFIX_BESCHREIBUNG + def.getId(), text.strip());
        }
    }

    public static synchronized SymbolRegistry getInstance() {
        if (instance == null) {
            instance = new SymbolRegistry();
        }
        return instance;
    }

    /**
     * Liest den Symbolordner (Voreinstellungen, Reiter "Pfade") erneut ein:
     * installiert beim allerersten Mal das mitgelieferte Standardpaket fuer
     * noch fehlende bekannte Funktionen, ersetzt dann fuer jede gefundene
     * .svg-Datei, deren Name zu einer bekannten Funktion passt, deren
     * Katalogeintrag durch die echte Datei, und nimmt jede uebrige .svg-Datei
     * als neues "Zusaetzliches" Symbol auf. Wird beim Start aufgerufen
     * (siehe {@link MainController}) und laesst sich ueber den Knopf
     * "Ordner neu einlesen" im Symbol-Manager jederzeit wiederholen, falls
     * waehrend der Laufzeit Dateien hinzukommen.
     */
    public synchronized void scanSymbolOrdner() {
        File symbolDir = new File(AppSettings.getInstance().getSymbolDirectory());
        if (!symbolDir.isDirectory()) {
            return;
        }
        ensureDefaultsInstalled(symbolDir);

        File[] files = symbolDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".svg"));
        if (files == null) {
            return;
        }
        Set<String> known = allKnownKeys();
        for (File file : files) {
            String baseName = file.getName();
            int dot = baseName.lastIndexOf('.');
            if (dot > 0) {
                baseName = baseName.substring(0, dot);
            }
            String upper = baseName.toUpperCase(Locale.ROOT);
            if (known.contains(upper)) {
                katalog.put(upper, new SymbolDef(upper, labelFor(upper), SymbolDef.Art.SVG, file.getAbsolutePath(), upper));
            } else {
                String id = "svg-" + baseName;
                SymbolDef vorhanden = katalog.get(id);
                String name = vorhanden != null ? vorhanden.getName() : baseName;
                katalog.put(id, new SymbolDef(id, name, SymbolDef.Art.SVG, file.getAbsolutePath(), null));
            }
        }
    }

    private void ensureDefaultsInstalled(File symbolDir) {
        for (String key : allKnownKeys()) {
            File target = new File(symbolDir, key + ".svg");
            if (target.exists() && !istVeralteteVorgabe(key, target)) {
                continue;
            }
            try (InputStream in = SymbolRegistry.class.getResourceAsStream("default-symbole/" + key + ".svg")) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Best-effort - ohne Datei bleibt vorerst der ORIGINAL-Fallback (NetzIcons) aktiv.
            }
        }
    }

    /**
     * Mitgelieferte Symbole, die durch eine neuere Vorgabe ersetzt wurden: Schluessel -&gt;
     * MD5 der alten mitgelieferten Datei. Nur wenn die Datei im Symbolordner noch genau dieser
     * alten Vorgabe entspricht (also nicht vom Anwender geaendert wurde), wird sie ersetzt.
     */
    private static final Map<String, String> VERALTETE_VORGABEN = Map.of(
            "ANLAGEN_INFRASTRUKTUR", "3563af3df431116e7f5f14004965c782",
            // Lamellen hatten die Füllfarbe der Halle und waren unsichtbar (sah aus wie der Bahnhof).
            "LAGER", "870bff03de7bc91a239b66977b0aac95");

    private static boolean istVeralteteVorgabe(String key, File datei) {
        String alt = VERALTETE_VORGABEN.get(key);
        if (alt == null) {
            return false;
        }
        try {
            byte[] inhalt = Files.readAllBytes(datei.toPath());
            byte[] md5 = java.security.MessageDigest.getInstance("MD5").digest(inhalt);
            StringBuilder hex = new StringBuilder();
            for (byte b : md5) {
                hex.append(String.format("%02x", b));
            }
            return alt.equals(hex.toString());
        } catch (Exception ex) {
            return false;
        }
    }

    private static Set<String> allKnownKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (NetzKnotenTyp t : NetzKnotenTyp.values()) {
            keys.add(t.name());
        }
        for (OrtTyp t : OrtTyp.values()) {
            keys.add(t.name());
        }
        keys.addAll(ZUSATZ_ORIGINALE.keySet());
        return keys;
    }

    private static String labelFor(String key) {
        try {
            return NetzKnotenTyp.valueOf(key).getLabel();
        } catch (IllegalArgumentException ignored) {
            // kein Ebene-1-Typ
        }
        try {
            return OrtTyp.valueOf(key).getLabel();
        } catch (IllegalArgumentException ignored) {
            // kein Ebene-2-Typ
        }
        return ZUSATZ_ORIGINALE.getOrDefault(key, key);
    }

    /** Symbole zu bekannten Funktionen (Ebene-1-/Ebene-2-Typen + Zusatz-Originale) - ob als .svg oder als Ersatz-Piktogramm. */
    public List<SymbolDef> getOriginalSymbole() {
        Set<String> known = allKnownKeys();
        List<SymbolDef> result = new ArrayList<>();
        for (SymbolDef s : katalog.values()) {
            if (known.contains(s.getId())) {
                result.add(s);
            }
        }
        return result;
    }

    /** Alles Weitere: selbst angelegte Rechtecke sowie neu entdeckte, noch nicht zugeordnete .svg-Dateien. */
    public List<SymbolDef> getZusaetzlicheSymbole() {
        Set<String> known = allKnownKeys();
        List<SymbolDef> result = new ArrayList<>();
        for (SymbolDef s : katalog.values()) {
            if (!known.contains(s.getId())) {
                result.add(s);
            }
        }
        return result;
    }

    /** true, wenn dieses Symbol eine vom Anwender hinzugefuegte .svg-Datei ist, die noch keiner Funktion zugewiesen wurde. */
    public boolean istNeuUndUnzugewiesen(SymbolDef def) {
        if (def.getArt() != SymbolDef.Art.SVG || allKnownKeys().contains(def.getId())) {
            return false;
        }
        return !zuordnung.containsValue(def.getId());
    }

    public SymbolDef addRechteckSymbol(String name, String farbeHex) {
        String id = "custom-" + UUID.randomUUID();
        SymbolDef def = new SymbolDef(id, name, SymbolDef.Art.RECHTECK, farbeHex);
        katalog.put(id, def);
        saveCustomSymbols();
        return def;
    }

    /**
     * Entfernt ein selbst angelegtes Rechteck-Symbol aus dem Katalog. Fuer
     * .svg-Dateien wirkungslos - die werden ueber den Symbolordner selbst
     * verwaltet (Datei loeschen/entfernen, dann "Ordner neu einlesen"), nicht
     * ueber diesen Knopf, damit hier nie versehentlich eine Anwenderdatei
     * geloescht wird.
     */
    public void removeSymbol(SymbolDef def) {
        if (def.getArt() != SymbolDef.Art.RECHTECK) {
            return;
        }
        katalog.remove(def.getId());
        prefs.remove(PREFIX_BESCHREIBUNG + def.getId());
        for (Map.Entry<String, String> e : zuordnung.entrySet()) {
            if (e.getValue().equals(def.getId())) {
                prefs.remove(PREFIX_ZUORDNUNG + e.getKey());
            }
        }
        zuordnung.values().removeIf(v -> v.equals(def.getId()));
        saveCustomSymbols();
        saveZuordnungen();
    }

    /** Das aktuell einer Funktion (Typ-Schluessel wie "BAHNHOF") zugewiesene Symbol. */
    public SymbolDef symbolFuer(String typ) {
        String symbolId = zuordnung.getOrDefault(typ, typ);
        SymbolDef def = katalog.get(symbolId);
        return def != null ? def : katalog.get(typ);
    }

    public void zuweisen(String typ, SymbolDef symbol) {
        zuordnung.put(typ, symbol.getId());
        saveZuordnungen();
    }

    private void loadCustomSymbols() {
        String ids = prefs.get(KEY_CUSTOM_IDS, "");
        if (ids.isBlank()) {
            return;
        }
        for (String id : ids.split(";")) {
            if (id.isBlank()) {
                continue;
            }
            String stored = prefs.get(PREFIX_CUSTOM + id, "");
            int sep = stored.indexOf('|');
            if (sep < 0) {
                continue;
            }
            katalog.put(id, new SymbolDef(id, stored.substring(0, sep), SymbolDef.Art.RECHTECK, stored.substring(sep + 1)));
        }
    }

    private void saveCustomSymbols() {
        List<String> ids = new ArrayList<>();
        for (SymbolDef s : katalog.values()) {
            if (s.getArt() == SymbolDef.Art.RECHTECK) {
                ids.add(s.getId());
                prefs.put(PREFIX_CUSTOM + s.getId(), s.getName() + "|" + s.getWert());
            }
        }
        prefs.put(KEY_CUSTOM_IDS, String.join(";", ids));
    }

    private void loadZuordnungen() {
        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(PREFIX_ZUORDNUNG)) {
                    String value = prefs.get(key, null);
                    if (value != null) {
                        zuordnung.put(key.substring(PREFIX_ZUORDNUNG.length()), value);
                    }
                }
            }
        } catch (Exception ignored) {
            // Preferences nicht lesbar - Standardzuordnung (Original je Typ) bleibt bestehen.
        }
    }

    private void saveZuordnungen() {
        for (Map.Entry<String, String> e : zuordnung.entrySet()) {
            prefs.put(PREFIX_ZUORDNUNG + e.getKey(), e.getValue());
        }
    }
}
