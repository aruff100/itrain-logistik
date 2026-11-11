package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlageXml;
import com.example.itrain_logistik.kern.Linie;
import javafx.collections.ObservableList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Die komplette Logistik-Anlage als gezippte {@code .logz}-Datei (gleiches
 * Prinzip wie {@code .tcdz}: ein ZIP-Archiv mit einer einzigen XML-Datei
 * darin, siehe {@link TcdzReader}) - siehe Projektauftrag "Alles was wir
 * hier machen muss in eine XML Datei geschrieben werden. .logz Dateien, zum
 * Schluss gezippt. Darin wird eine .tcdz Verbunden, aber nicht automatisch
 * geladen." Eine {@code .logz} entsteht entweder neu aus einer .tcdz
 * ({@link #neuAusTcdz}, uebernimmt Lokomotiven/Wagen/Bloecke/Zuege/
 * Blockpositionen vollstaendig - siehe Projektauftrag "Alles Daten die von
 * der verbundenen Datei in diesem Programm uebernommen werden sind in der
 * XML vorhanden ohne jedes mal die .tcdz laden zu muessen.") oder wird direkt
 * geoeffnet ({@link #openLogz}, liest ausschliesslich aus der .logz, ohne
 * die gekoppelte .tcdz anzufassen). {@link #abgleichen()} liest die
 * gekoppelte .tcdz erneut ein und gleicht ab, was neu dazugekommen oder
 * weggefallen ist (siehe Projektauftrag "Es gibt einen Knopf der die 'Daten'
 * mit der .tcdz abgleicht."), ohne eigene Datenfelder/Zug-Zusammenstellung
 * weiterhin vorhandener Eintraege zu verlieren.
 */
public final class LogistikDocument {

    private static final int MAX_BACKUPS = 10;
    private static final String LOGZ_ENTRY_NAME = "logistik.xml";

    private LogistikSnapshot snapshot;
    private File logistikFile;
    /**
     * Fahrzeugdatei (Anwender 09.11.2026): die zusätzlich ermittelten Daten der Lokomotiven, Wagen und Züge liegen
     * getrennt von den Logistik-Daten in {@code <Name der .tcdz>.fahrzeuge.xml} im Logistik-Ordner und werden von
     * allen Konfigurationen derselben iTrain-Anlage gemeinsam benutzt. null = Fahrzeuge stehen noch in der .logz.
     */
    private File fahrzeugFile;
    /** Beim Öffnen fehlte die Fahrzeugdatei (Meldung im Hauptfenster). */
    private boolean fahrzeugdateiFehlte;
    private boolean dirty;
    /**
     * Änderungszeit der .tcdz beim letzten Einlesen (Neu anlegen oder Abgleichen), in der .logz als
     * {@code tcdzStand}; 0 = unbekannt (ältere Dateien). Anwender 27.10.2026: beim Öffnen fragen, wenn die .tcdz
     * seither geändert wurde.
     */
    private long tcdzStand;

    /** Ist die verknüpfte .tcdz neuer als beim letzten Einlesen? (unbekannter Stand zählt als neuer) */
    public boolean tcdzGeaendert() {
        File t = snapshot != null ? snapshot.sourceFile() : null;
        return t != null && t.isFile() && t.lastModified() != tcdzStand;
    }
    private Anlage anlage;

    /** Andere .tcdz verknüpfen (Anwender 22.10.2026); gelesen wird sie erst beim nächsten Abgleich. */
    public void setTcdz(File tcdz) {
        if (snapshot == null || tcdz == null) {
            return;
        }
        LogistikSnapshot s = snapshot;
        snapshot = new LogistikSnapshot(tcdz, s.clock(), s.locomotives(), s.wagons(), s.blocks(), s.trains(),
                s.controlState(), s.routes(), s.stations());
        dirty = true;
    }

    public LogistikSnapshot getSnapshot() {
        return snapshot;
    }

    public File getLogistikFile() {
        return logistikFile;
    }

    public boolean isOpen() {
        return snapshot != null;
    }

    /** Die Logistik-Konfiguration der geoeffneten Anlage (Echtzeit-Kern, siehe {@link Anlage}). */
    public Anlage getAnlage() {
        if (anlage == null) {
            anlage = Anlage.neuMitTf3Guetern();
            uebernehmeLinien();
        }
        return anlage;
    }

    /** Bloecke und Linien der Anlage (Linien = Zugrouten aus der .tcdz, siehe {@link RouteEntry}). */
    private void uebernehmeLinien() {
        if (anlage == null || snapshot == null) {
            return;
        }
        anlage.getBloecke().clear();
        for (BlockEntry b : snapshot.blocks()) {
            anlage.getBloecke().add(b.getName());
        }
        anlage.getLinien().clear();
        for (RouteEntry r : snapshot.routes()) {
            anlage.getLinien().add(new Linie(r.name(), r.blockNamen()));
        }
        // In iTrain definierte Bahnhöfe mit ihren Gleisen an die Anlagen-Infrastruktur heften
        // (nur noch nicht verknüpfte; was der Anwender gelöscht hat, bleibt weg).
        if (anlage.isBahnhoefeEingefuegt()
                && !anlage.itrainBahnhoefeAnheften(bahnhofDaten(), bahnhofTypen(), false).isEmpty()) {
            dirty = true;
        }
        // Fahrtrichtungen, die noch den Namen einer inzwischen umbenannten Station tragen, nachziehen.
        if (anlage.veralteteRichtungenNachziehen() > 0) {
            dirty = true;
        }
        // Orte mit Güterbedarf am Personenbahnhof bekommen ihren Güterbahnhof (falls noch nicht da).
        anlage.schattenbahnhoefeUmstellen();
        // Schattenbahnhof nur über Gleise (Anwender 09.11.2026): angeschlossene Objekte lösen
        schattenbahnhofGeloest = anlage.schattenbahnhoefeBereinigen();
        if (!schattenbahnhofGeloest.isEmpty()) {
            dirty = true;
        }
        if (!anlage.gueterbahnhoefeErgaenzen().isEmpty()) {
            dirty = true;
        }
        if (!anlage.gueterObjekteZumGueterbahnhof().isEmpty()) {
            dirty = true;
        }
    }

    /** Beim Öffnen vom Schattenbahnhof gelöste Verbindungen (Anwender 09.11.2026). */
    private List<String> schattenbahnhofGeloest = List.of();

    public List<String> getSchattenbahnhofGeloest() {
        return schattenbahnhofGeloest;
    }

    /** iTrain-Bahnhöfe der geöffneten Datei (Name -> Blöcke in Dateireihenfolge). */
    public java.util.Map<String, List<String>> bahnhofDaten() {
        java.util.Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        if (snapshot != null) {
            for (StationEntry s : snapshot.stations()) {
                m.put(s.name(), s.bloecke());
            }
        }
        return m;
    }

    /** iTrain-Bahnhofstypen der geöffneten Datei (Name -> "passengers"/"cargo"/"shadow"/"other"). */
    public java.util.Map<String, String> bahnhofTypen() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        if (snapshot != null) {
            for (StationEntry s : snapshot.stations()) {
                if (s.typ() != null) {
                    m.put(s.name(), s.typ());
                }
            }
        }
        return m;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        dirty = true;
    }

    public File getFahrzeugFile() {
        return fahrzeugFile;
    }

    public boolean isFahrzeugdateiFehlte() {
        return fahrzeugdateiFehlte;
    }

    /** Gemeinsame Fahrzeugdatei einer iTrain-Anlage im Logistik-Ordner. */
    public static File standardFahrzeugdatei(File tcdz) {
        String pfad = tcdz.getPath();
        String name = pfad.substring(Math.max(pfad.lastIndexOf('/'), pfad.lastIndexOf('\\')) + 1);
        int punkt = name.lastIndexOf('.');
        return new File(AppSettings.getInstance().getLogistikDirectory(),
                (punkt > 0 ? name.substring(0, punkt) : name) + ".fahrzeuge.xml");
    }

    /** Fahrzeugdatei nur dieser Konfiguration (wenn die gemeinsame nicht übernommen werden soll). */
    private static File eigeneFahrzeugdatei(File logz) {
        String name = logz.getName();
        int punkt = name.lastIndexOf('.');
        String basis = punkt > 0 ? name.substring(0, punkt) : name;
        File f = new File(logz.getParentFile(), basis + ".fahrzeuge.xml");
        // nie eine vorhandene (z. B. die gemeinsame gleichen Namens) überschreiben
        for (int i = 2; f.exists(); i++) {
            f = new File(logz.getParentFile(), basis + "-" + i + ".fahrzeuge.xml");
        }
        return f;
    }

    public void close() {
        snapshot = null;
        logistikFile = null;
        fahrzeugFile = null;
        fahrzeugdateiFehlte = false;
        dirty = false;
        anlage = null;
    }

    /**
     * Legt eine neue {@code .logz} an, indem Lokomotiven/Wagen/Bloecke/
     * Zuege/Blockpositionen einmalig vollstaendig aus einer .tcdz/.tcd
     * uebernommen werden ("Import"). Schreibt noch nichts auf die Platte -
     * dafuer anschliessend {@link #save()} aufrufen.
     */
    public void neuAusTcdz(File tcdzFile, File targetLogzFile) throws Exception {
        neuAusTcdz(tcdzFile, targetLogzFile, true);
    }

    /**
     * Wie oben; {@code fahrzeugeUebernehmen}: gibt es schon eine Fahrzeugdatei dieser iTrain-Anlage, werden ihre
     * Daten übernommen und die Datei gemeinsam benutzt; sonst bekommt die Konfiguration eine eigene
     * (Anwender 09.11.2026, Frage „Fahrzeuge übernehmen?“).
     */
    public void neuAusTcdz(File tcdzFile, File targetLogzFile, boolean fahrzeugeUebernehmen) throws Exception {
        XmlNode root = TcdzReader.load(tcdzFile);
        snapshot = TcdzModelBuilder.build(tcdzFile, root);
        fahrzeugdateiFehlte = false;
        File gemeinsam = standardFahrzeugdatei(tcdzFile);
        if (gemeinsam.isFile() && fahrzeugeUebernehmen) {
            fahrzeugFile = gemeinsam;
            fahrzeugeMischen(gemeinsam);
        } else {
            fahrzeugFile = gemeinsam.isFile() ? eigeneFahrzeugdatei(targetLogzFile) : gemeinsam;
        }
        tcdzStand = tcdzFile.lastModified();
        anlage = Anlage.neuMitTf3Guetern();
        // Bahnhöfe erst beim ersten „Anlage bearbeiten“ einfügen (Anwender 22.10.2026)
        anlage.setBahnhoefeEingefuegt(false);
        // Glückwunsch beim ersten Personen- und Gütertransfer, nur bei neu angelegten Dateien (Anwender 22.10.2026)
        anlage.setErsterPersonenTransferOffen(true);
        anlage.setErsterGueterTransferOffen(true);
        uebernehmeLinien();
        logistikFile = targetLogzFile;
        dirty = true;
    }

    /** Oeffnet eine bestehende {@code .logz} - liest ausschliesslich aus ihr, ohne die .tcdz zu laden. */
    public void openLogz(File logzFile) throws Exception {
        Document document = readLogzXml(logzFile);
        loadFullSnapshotFromXml(document);
        logistikFile = logzFile;
        dirty = false;
        fahrzeugdateiFehlte = false;
        Element root = document.getDocumentElement();
        if (root.hasAttribute("fahrzeuge")) {
            File f = new File(root.getAttribute("fahrzeuge"));
            if (!f.isAbsolute()) {
                f = new File(logzFile.getParentFile(), f.getPath());
            }
            fahrzeugFile = f;
            if (f.isFile()) {
                Document fd = readXml(f);
                snapshot = new LogistikSnapshot(snapshot.sourceFile(), snapshot.clock(), readLocomotives(fd),
                        readWagons(fd), snapshot.blocks(), readTrains(fd), snapshot.controlState(), snapshot.routes(),
                        snapshot.stations());
            } else {
                // Fahrzeugdatei fehlt: Namen aus der .tcdz, eigene Daten leer (Meldung im Hauptfenster)
                fahrzeugdateiFehlte = true;
                File tcdz = snapshot.sourceFile();
                if (tcdz != null && tcdz.isFile()) {
                    LogistikSnapshot frisch = TcdzModelBuilder.build(tcdz, TcdzReader.load(tcdz));
                    snapshot = new LogistikSnapshot(tcdz, snapshot.clock(), frisch.locomotives(), frisch.wagons(),
                            snapshot.blocks(), frisch.trains(), snapshot.controlState(), snapshot.routes(),
                            snapshot.stations());
                }
            }
        } else {
            // ältere .logz mit den Fahrzeugen darin: beim nächsten Speichern in eine Fahrzeugdatei auslagern,
            // ohne eine schon vorhandene gemeinsame Datei zu überschreiben
            File tcdz = snapshot.sourceFile();
            File gemeinsam = tcdz != null ? standardFahrzeugdatei(tcdz) : null;
            fahrzeugFile = gemeinsam != null && !gemeinsam.isFile() ? gemeinsam : eigeneFahrzeugdatei(logzFile);
            dirty = true;
        }
    }

    /**
     * Datei > „Fahrzeuge importieren...“: übernimmt die eigenen Felder der Loks und Wagen und die eigene
     * Zusammenstellung der Züge aus einer Fahrzeugdatei, je nach Namen. Liefert die Zahl der gefundenen Einträge.
     */
    public int fahrzeugeImportieren(File datei) throws Exception {
        if (snapshot == null) {
            return 0;
        }
        int n = fahrzeugeMischen(datei);
        dirty = true;
        return n;
    }

    /** Datei > „Fahrzeuge exportieren...“: die Fahrzeugdaten in eine eigene Datei (Anwender 09.11.2026). */
    public void fahrzeugeExportieren(File ziel) throws IOException {
        if (snapshot != null) {
            schreibeFahrzeugdatei(ziel);
        }
    }

    private int fahrzeugeMischen(File datei) throws Exception {
        Document fd = readXml(datei);
        List<LocomotiveEntry> loks = readLocomotives(fd);
        List<WagonEntry> wagen = readWagons(fd);
        List<TrainEntry> zuege = readTrains(fd);
        int[] treffer = {0};
        Map<String, LocomotiveEntry> lokNamen = indexByName(loks, LocomotiveEntry::getName);
        for (LocomotiveEntry l : snapshot.locomotives()) {
            LocomotiveEntry alt = lokNamen.get(l.getName());
            if (alt != null) {
                l.getCustomFields().setAll(alt.getCustomFields());
                treffer[0]++;
            }
        }
        Map<String, WagonEntry> wagenNamen = indexByName(wagen, WagonEntry::getName);
        for (WagonEntry w : snapshot.wagons()) {
            WagonEntry alt = wagenNamen.get(w.getName());
            if (alt != null) {
                w.getCustomFields().setAll(alt.getCustomFields());
                treffer[0]++;
            }
        }
        Map<String, TrainEntry> zugNamen = indexByName(zuege, TrainEntry::getName);
        for (TrainEntry t : snapshot.trains()) {
            TrainEntry alt = zugNamen.get(t.getName());
            if (alt != null) {
                if (!gleich(alt.getComposition(), alt.getTcdzComposition())) {
                    t.getComposition().setAll(alt.getComposition());
                }
                treffer[0]++;
            }
        }
        return treffer[0];
    }

    private static Document readXml(File f) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document d = factory.newDocumentBuilder().parse(f);
        d.getDocumentElement().normalize();
        return d;
    }

    /**
     * Liest die gekoppelte .tcdz erneut ein und gleicht Lokomotiven/Wagen/
     * Bloecke/Zuege/Blockpositionen ab: neu hinzugekommene Eintraege werden
     * uebernommen, weggefallene entfernt, bei weiterhin vorhandenen bleiben
     * eigene Datenfelder bzw. die eigene Zug-Zusammenstellung erhalten
     * (siehe Projektauftrag "nur verifiziert ob etwas neues dazu gekommen
     * ist oder weggefallen"). Markiert das Dokument als geaendert -
     * anschliessend {@link #save()} aufrufen, damit der Abgleich auch in
     * der {@code .logz} landet.
     */
    public void abgleichen() throws Exception {
        if (snapshot == null) {
            return;
        }
        File tcdz = snapshot.sourceFile();
        if (tcdz == null) {
            throw new IOException("Keine gekoppelte .tcdz-Datei hinterlegt.");
        }
        if (!tcdz.isFile()) {
            throw new IOException("Gekoppelte Datei nicht gefunden: " + tcdz.getAbsolutePath());
        }
        XmlNode root = TcdzReader.load(tcdz);
        LogistikSnapshot frisch = TcdzModelBuilder.build(tcdz, root);
        tcdzStand = tcdz.lastModified();

        // Anwender 22.10.2026: in iTrain geänderte Züge kamen nicht an, weil vorhandene Einträge unverändert blieben.
        // Jetzt gilt der Stand aus der .tcdz, nur die eigenen Felder bzw. die eigene Zusammenstellung bleiben.
        abgleichGeaendert.clear();
        abgleichEigeneBehalten.clear();
        List<LocomotiveEntry> locomotives = mergeMitFeldern(snapshot.locomotives(), frisch.locomotives(),
                LocomotiveEntry::getName, LocomotiveEntry::getCustomFields);
        List<WagonEntry> wagons = mergeMitFeldern(snapshot.wagons(), frisch.wagons(), WagonEntry::getName,
                WagonEntry::getCustomFields);
        List<BlockEntry> blocks = mergeMitFeldern(snapshot.blocks(), frisch.blocks(), BlockEntry::getName,
                BlockEntry::getCustomFields);
        List<TrainEntry> trains = mergeZuege(snapshot.trains(), frisch.trains());

        // Blockpositionen (control-state) sind Laufzeitzustand - immer der frische Stand.
        snapshot = new LogistikSnapshot(tcdz, frisch.clock(), locomotives, wagons, blocks, trains,
                frisch.controlState(), frisch.routes(), frisch.stations());
        uebernehmeLinien();
        dirty = true;
    }

    /** Beim letzten Abgleich in iTrain geänderte Züge bzw. Züge, deren eigene Zusammenstellung behalten wurde. */
    private final List<String> abgleichGeaendert = new ArrayList<>();
    private final List<String> abgleichEigeneBehalten = new ArrayList<>();

    public List<String> getAbgleichGeaendert() {
        return List.copyOf(abgleichGeaendert);
    }

    public List<String> getAbgleichEigeneBehalten() {
        return List.copyOf(abgleichEigeneBehalten);
    }

    /** Frischer Eintrag aus der .tcdz, die eigenen Felder des bisherigen Eintrags gleichen Namens übernommen. */
    private static <T> List<T> mergeMitFeldern(List<T> alt, List<T> frisch, Function<T, String> nameFn,
            Function<T, javafx.collections.ObservableList<CustomField>> felder) {
        Map<String, T> altByName = indexByName(alt, nameFn);
        List<T> result = new ArrayList<>();
        for (T neu : frisch) {
            T vorhanden = altByName.get(nameFn.apply(neu));
            if (vorhanden != null) {
                felder.apply(neu).setAll(felder.apply(vorhanden));
            }
            result.add(neu);
        }
        return result;
    }

    /**
     * Zusammenstellung aus der .tcdz übernehmen. Hat der Anwender die eigene Zusammenstellung eines Zugs geändert
     * (weicht vom bisherigen iTrain-Stand ab), bleibt sie und der Zug wird gemeldet.
     */
    private List<TrainEntry> mergeZuege(List<TrainEntry> alt, List<TrainEntry> frisch) {
        Map<String, TrainEntry> altByName = indexByName(alt, TrainEntry::getName);
        List<TrainEntry> result = new ArrayList<>();
        for (TrainEntry neu : frisch) {
            TrainEntry vorhanden = altByName.get(neu.getName());
            if (vorhanden == null) {
                result.add(neu);
                continue;
            }
            boolean inItrainGeaendert = !gleich(vorhanden.getTcdzComposition(), neu.getTcdzComposition());
            boolean eigeneGeaendert = !gleich(vorhanden.getComposition(), vorhanden.getTcdzComposition());
            if (inItrainGeaendert) {
                abgleichGeaendert.add(neu.getName());
            }
            if (eigeneGeaendert) {
                if (inItrainGeaendert) {
                    abgleichEigeneBehalten.add(neu.getName());
                }
                neu.getComposition().setAll(vorhanden.getComposition());
            }
            result.add(neu);
        }
        return result;
    }

    private static String leer(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean gleich(List<TrainCompositionItem> a, List<TrainCompositionItem> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            TrainCompositionItem x = a.get(i);
            TrainCompositionItem y = b.get(i);
            if (x.getKind() != y.getKind() || !java.util.Objects.equals(x.getName(), y.getName())
                    || x.isMain() != y.isMain() || !leer(x.getDirection()).equals(leer(y.getDirection()))) {
                return false;
            }
        }
        return true;
    }

    /** Schreibt die komplette Anlage (alle uebernommenen Daten + eigene Felder) in die {@code .logz}, mit Sicherung des Vorgaengers. */
    public void save() throws IOException {
        if (snapshot == null || logistikFile == null) {
            return;
        }
        File dir = logistikFile.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Ordner fuer Logistik-Dateien konnte nicht angelegt werden: " + dir);
        }
        if (logistikFile.isFile()) {
            createBackup(logistikFile);
        }
        writeLogzFile(logistikFile);
        if (fahrzeugFile != null) {
            schreibeFahrzeugdatei(fahrzeugFile);
        }
        dirty = false;
    }

    /** Numerierte Sicherungen der Logistik-Datei, neueste zuerst (fuer "Backup laden..."). */
    public java.util.List<File> listBackups() {
        java.util.List<File> result = new java.util.ArrayList<>();
        if (logistikFile == null) {
            return result;
        }
        for (int i = 1; i <= MAX_BACKUPS; i++) {
            File backup = backupFile(logistikFile, i);
            if (backup.isFile()) {
                result.add(backup);
            }
        }
        return result;
    }

    public void restoreBackup(File backup) throws Exception {
        Document document = readLogzXml(backup);
        loadFullSnapshotFromXml(document);
        dirty = true;
    }

    private void createBackup(File current) throws IOException {
        // Generationen hochschieben (ältestes fliegt raus), dann Platz 1 belegen -
        // gleiches numeriertes Rotationsschema wie HelloController.createBackup
        // in iTrain_import_export, hier ohne "nur bei tatsächlicher Änderung"-
        // Sonderfall.
        File oldest = backupFile(current, MAX_BACKUPS);
        if (oldest.isFile()) {
            Files.delete(oldest.toPath());
        }
        for (int i = MAX_BACKUPS - 1; i >= 1; i--) {
            File from = backupFile(current, i);
            if (from.isFile()) {
                Files.move(from.toPath(), backupFile(current, i + 1).toPath());
            }
        }
        Files.copy(current.toPath(), backupFile(current, 1).toPath());
    }

    private File backupFile(File file, int generation) {
        return new File(file.getParentFile(), file.getName() + "." + generation + ".bak");
    }

    // --- .logz: ZIP-Archiv mit einer einzigen XML-Datei, siehe Klassenkommentar ---

    private static Document readLogzXml(File logzFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (ZipFile zip = new ZipFile(logzFile)) {
            ZipEntry entry = zip.getEntry(LOGZ_ENTRY_NAME);
            if (entry == null) {
                Enumeration<? extends ZipEntry> e = zip.entries();
                while (e.hasMoreElements()) {
                    ZipEntry candidate = e.nextElement();
                    if (candidate.getName().toLowerCase().endsWith(".xml")) {
                        entry = candidate;
                        break;
                    }
                }
            }
            if (entry == null) {
                throw new IOException("Im ZIP-Archiv \"" + logzFile.getName() + "\" wurde keine XML-Datei gefunden.");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                Document document = builder.parse(in);
                document.getDocumentElement().normalize();
                return document;
            }
        }
    }

    private void schreibeFahrzeugdatei(File ziel) throws IOException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<fahrzeuge");
        if (snapshot.sourceFile() != null) {
            xml.append(" tcdz=\"").append(escape(snapshot.sourceFile().getPath())).append("\"");
        }
        xml.append(">\n");
        appendFahrzeuge(xml);
        xml.append("</fahrzeuge>\n");
        File dir = ziel.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Ordner für die Fahrzeugdatei konnte nicht angelegt werden: " + dir);
        }
        File tmp = File.createTempFile("fahrzeuge", ".tmp", dir != null ? dir : new File("."));
        Files.writeString(tmp.toPath(), xml.toString(), StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeLogzFile(File target) throws IOException {
        String xml = buildOwnDataXml();
        File parent = target.getParentFile();
        File tmp = File.createTempFile("logistik", ".tmp", parent != null ? parent : new File("."));
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmp))) {
            zos.putNextEntry(new ZipEntry(LOGZ_ENTRY_NAME));
            zos.write(xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // --- eigene Datenfelder + vollstaendig uebernommene .tcdz-Daten: Ablage als eigenes XML-Format ---

    private String buildOwnDataXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<logistik");
        if (snapshot.sourceFile() != null) {
            xml.append(" tcdz=\"").append(escape(snapshot.sourceFile().getPath())).append("\"");
        }
        if (tcdzStand != 0) {
            xml.append(" tcdzStand=\"").append(tcdzStand).append("\"");
        }
        if (fahrzeugFile != null) {
            // im selben Ordner nur der Name, damit der Logistik-Ordner verschoben werden kann
            boolean nebenan = logistikFile != null && fahrzeugFile.getParentFile() != null
                    && fahrzeugFile.getParentFile().getAbsoluteFile().equals(logistikFile.getAbsoluteFile().getParentFile());
            xml.append(" fahrzeuge=\"").append(escape(nebenan ? fahrzeugFile.getName() : fahrzeugFile.getPath()))
                    .append("\"");
        }
        xml.append(">\n");

        if (fahrzeugFile == null) {
            appendFahrzeuge(xml);
        }
        appendRest(xml);
        appendSonstiges(xml);
        AnlageXml.schreibe(getAnlage(), xml);

        xml.append("</logistik>\n");
        return xml.toString();
    }

    private void appendFahrzeuge(StringBuilder xml) {
        xml.append("  <locomotives>\n");
        for (LocomotiveEntry loco : snapshot.locomotives()) {
            xml.append("    <locomotive name=\"").append(escape(loco.getName())).append("\"");
            appendOptionalAttr(xml, "type", loco.getType());
            appendOptionalAttr(xml, "image", loco.getImageFile());
            appendOptionalAttr(xml, "description", loco.getDescription());
            appendFieldsOrClose(xml, loco.getCustomFields(), "locomotive", "      ");
        }
        xml.append("  </locomotives>\n");

        xml.append("  <wagons>\n");
        for (WagonEntry wagon : snapshot.wagons()) {
            xml.append("    <wagon name=\"").append(escape(wagon.getName())).append("\"");
            appendOptionalAttr(xml, "type", wagon.getType());
            appendOptionalAttr(xml, "image", wagon.getImageFile());
            appendOptionalAttr(xml, "description", wagon.getDescription());
            appendFieldsOrClose(xml, wagon.getCustomFields(), "wagon", "      ");
        }
        xml.append("  </wagons>\n");
        appendZuege(xml);
    }

    private void appendRest(StringBuilder xml) {
        xml.append("  <blocks>\n");
        for (BlockEntry block : snapshot.blocks()) {
            xml.append("    <block name=\"").append(escape(block.getName())).append("\"");
            appendOptionalAttr(xml, "type", block.getType());
            appendOptionalAttr(xml, "wege", block.getWege().isEmpty() ? null : block.getWege());
            appendFieldsOrClose(xml, block.getCustomFields(), "block", "      ");
        }
        xml.append("  </blocks>\n");
    }

    private void appendZuege(StringBuilder xml) {
        xml.append("  <trains>\n");
        for (TrainEntry train : snapshot.trains()) {
            xml.append("    <train name=\"").append(escape(train.getName())).append("\">\n");
            schreibeZusammenstellung(xml, "composition", train.getComposition());
            // Stand aus iTrain merken, wenn die eigene Zusammenstellung abweicht – der Abgleich erkennt daran,
            // ob der Zug in iTrain oder hier geändert wurde (Anwender 22.10.2026)
            if (!gleich(train.getComposition(), train.getTcdzComposition())) {
                schreibeZusammenstellung(xml, "itrainComposition", train.getTcdzComposition());
            }
            xml.append("    </train>\n");
        }
        xml.append("  </trains>\n");
    }

    private void appendSonstiges(StringBuilder xml) {
        xml.append("  <controlstate>\n");
        for (ControlStateBlockEntry block : snapshot.controlState()) {
            xml.append("    <block name=\"").append(escape(block.name())).append("\"");
            appendOptionalAttr(xml, "train", block.train());
            xml.append("/>\n");
        }
        xml.append("  </controlstate>\n");

        xml.append("  <routes>\n");
        for (RouteEntry route : snapshot.routes()) {
            xml.append("    <route name=\"").append(escape(route.name())).append("\">\n");
            for (String block : route.blockNamen()) {
                xml.append("      <block name=\"").append(escape(block)).append("\"/>\n");
            }
            xml.append("    </route>\n");
        }
        xml.append("  </routes>\n");

        xml.append("  <stations>\n");
        for (StationEntry station : snapshot.stations()) {
            xml.append("    <station name=\"").append(escape(station.name())).append("\"");
            appendOptionalAttr(xml, "type", station.typ());
            xml.append(">\n");
            for (String block : station.bloecke()) {
                xml.append("      <block name=\"").append(escape(block)).append("\"/>\n");
            }
            xml.append("    </station>\n");
        }
        xml.append("  </stations>\n");
    }

    private static void appendOptionalAttr(StringBuilder xml, String name, String value) {
        if (value != null && !value.isBlank()) {
            xml.append(" ").append(name).append("=\"").append(escape(value)).append("\"");
        }
    }

    private static void appendFieldsOrClose(StringBuilder xml, Iterable<CustomField> fields, String tag, String indent) {
        boolean any = false;
        for (CustomField ignored : fields) {
            any = true;
            break;
        }
        if (!any) {
            xml.append("/>\n");
            return;
        }
        xml.append(">\n");
        appendFields(xml, fields, indent);
        xml.append(indent.substring(2)).append("</").append(tag).append(">\n");
    }

    private static void appendFields(StringBuilder xml, Iterable<CustomField> fields, String indent) {
        for (CustomField field : fields) {
            xml.append(indent).append("<field name=\"").append(escape(field.getName()))
                    .append("\" value=\"").append(escape(field.getValue())).append("\"/>\n");
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Baut {@link #snapshot} und {@link #anlage} vollstaendig aus einer geparsten {@code .logz}-XML auf. */
    private void loadFullSnapshotFromXml(Document document) {
        Element root = document.getDocumentElement();
        String coupledPath = root.hasAttribute("tcdz") ? root.getAttribute("tcdz") : null;
        try {
            tcdzStand = root.hasAttribute("tcdzStand") ? Long.parseLong(root.getAttribute("tcdzStand")) : 0;
        } catch (NumberFormatException ex) {
            tcdzStand = 0;
        }
        File coupledFile = (coupledPath != null && !coupledPath.isBlank()) ? new File(coupledPath) : null;

        List<LocomotiveEntry> locomotives = readLocomotives(document);
        List<WagonEntry> wagons = readWagons(document);
        List<BlockEntry> blocks = readBlocks(document);
        List<TrainEntry> trains = readTrains(document);
        List<ControlStateBlockEntry> controlState = readControlState(document);

        // Die Uhr ist kein Teil der fuenf zu uebernehmenden Bereiche (Lokomotiven/
        // Wagen/Bloecke/Zuege/Blockpositionen) und wird deshalb nicht mit in
        // der .logz gespeichert (sie waere sofort veraltet). Frueher blieb sie
        // dadurch bis zum naechsten manuellen "Abgleichen" leer/unsichtbar -
        // stattdessen wird sie beim Oeffnen jetzt direkt aus der gekoppelten
        // .tcdz nachgeladen (bestenfalls: schlaegt das fehl, z.B. weil die
        // Datei gerade nicht erreichbar ist, bleibt sie wie bisher leer).
        ClockData clock = ClockData.EMPTY;
        List<StationEntry> stations = readStations(document);
        if (coupledFile != null && coupledFile.isFile()) {
            try {
                LogistikSnapshot frisch = TcdzModelBuilder.build(coupledFile, TcdzReader.load(coupledFile));
                clock = frisch.clock();
                // Ältere .logz ohne <stations>: Bahnhöfe gleich aus der .tcdz nehmen.
                if (stations.isEmpty()) {
                    stations = frisch.stations();
                }
            } catch (Exception ex) {
                // Bewusst still - Uhr bleibt leer, bis "Abgleichen" manuell erfolgreich ist.
            }
        }
        List<RouteEntry> routes = readRoutes(document);
        snapshot = new LogistikSnapshot(coupledFile, clock, locomotives, wagons, blocks, trains, controlState, routes,
                stations);
        Element anlageElement = null;
        for (Element e : AnlageXml.kinder(document.getDocumentElement(), "anlage")) {
            anlageElement = e;
        }
        anlage = AnlageXml.lese(anlageElement);
        uebernehmeLinien();
    }

    private static List<LocomotiveEntry> readLocomotives(Document document) {
        List<LocomotiveEntry> result = new ArrayList<>();
        Element section = firstElement(document, "locomotives");
        if (section == null) {
            return result;
        }
        NodeList nodes = section.getElementsByTagName("locomotive");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            LocomotiveEntry loco = new LocomotiveEntry(el.getAttribute("name"),
                    el.hasAttribute("type") ? el.getAttribute("type") : null,
                    el.hasAttribute("image") ? el.getAttribute("image") : null,
                    el.hasAttribute("description") ? el.getAttribute("description") : null);
            readFieldsInto(el, loco.getCustomFields());
            result.add(loco);
        }
        return result;
    }

    private static List<WagonEntry> readWagons(Document document) {
        List<WagonEntry> result = new ArrayList<>();
        Element section = firstElement(document, "wagons");
        if (section == null) {
            return result;
        }
        NodeList nodes = section.getElementsByTagName("wagon");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            WagonEntry wagon = new WagonEntry(el.getAttribute("name"),
                    el.hasAttribute("type") ? el.getAttribute("type") : null,
                    el.hasAttribute("image") ? el.getAttribute("image") : null,
                    el.hasAttribute("description") ? el.getAttribute("description") : null);
            readFieldsInto(el, wagon.getCustomFields());
            result.add(wagon);
        }
        return result;
    }

    private static List<StationEntry> readStations(Document document) {
        List<StationEntry> result = new ArrayList<>();
        for (Element section : AnlageXml.kinder(document.getDocumentElement(), "stations")) {
            for (Element st : AnlageXml.kinder(section, "station")) {
                List<String> bloecke = new ArrayList<>();
                for (Element block : AnlageXml.kinder(st, "block")) {
                    bloecke.add(block.getAttribute("name"));
                }
                result.add(new StationEntry(st.getAttribute("name"),
                        st.hasAttribute("type") ? st.getAttribute("type") : null, bloecke));
            }
        }
        return result;
    }

    private static List<RouteEntry> readRoutes(Document document) {
        List<RouteEntry> result = new ArrayList<>();
        Element section = null;
        for (Element e : AnlageXml.kinder(document.getDocumentElement(), "routes")) {
            section = e;
        }
        if (section == null) {
            return result;
        }
        for (Element route : AnlageXml.kinder(section, "route")) {
            List<String> bloecke = new ArrayList<>();
            for (Element block : AnlageXml.kinder(route, "block")) {
                bloecke.add(block.getAttribute("name"));
            }
            result.add(new RouteEntry(route.getAttribute("name"), bloecke));
        }
        return result;
    }

    private static List<BlockEntry> readBlocks(Document document) {
        List<BlockEntry> result = new ArrayList<>();
        Element section = firstElement(document, "blocks");
        if (section == null) {
            return result;
        }
        NodeList nodes = section.getElementsByTagName("block");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            BlockEntry block = new BlockEntry(el.getAttribute("name"),
                    el.hasAttribute("type") ? el.getAttribute("type") : null);
            block.setWege(el.getAttribute("wege"));
            readFieldsInto(el, block.getCustomFields());
            result.add(block);
        }
        return result;
    }

    private static void schreibeZusammenstellung(StringBuilder xml, String element, List<TrainCompositionItem> items) {
        xml.append("      <").append(element).append(">\n");
        for (TrainCompositionItem item : items) {
            String tag = item.getKind() == TrainCompositionItem.Kind.LOCOMOTIVE ? "locomotive" : "wagon";
            xml.append("        <").append(tag).append(" name=\"").append(escape(item.getName())).append("\"");
            if (item.isMain()) {
                xml.append(" main=\"true\"");
            }
            if (item.getDirection() != null && !item.getDirection().isBlank()) {
                xml.append(" direction=\"").append(escape(item.getDirection())).append("\"");
            }
            xml.append("/>\n");
        }
        xml.append("      </").append(element).append(">\n");
    }

    /** Einträge einer Zusammenstellung (Kinder von composition bzw. itrainComposition). */
    private static List<TrainCompositionItem> leseZusammenstellung(Element section) {
        List<TrainCompositionItem> items = new ArrayList<>();
        NodeList nodes = section.getElementsByTagName("*");
        for (int c = 0; c < nodes.getLength(); c++) {
            Element item = (Element) nodes.item(c);
            if ("locomotive".equals(item.getTagName())) {
                items.add(new TrainCompositionItem(TrainCompositionItem.Kind.LOCOMOTIVE,
                        item.getAttribute("name"), "true".equals(item.getAttribute("main")),
                        item.getAttribute("direction")));
            } else if ("wagon".equals(item.getTagName())) {
                items.add(new TrainCompositionItem(TrainCompositionItem.Kind.WAGON,
                        item.getAttribute("name"), false, item.getAttribute("direction")));
            }
        }
        return items;
    }

    private static List<TrainEntry> readTrains(Document document) {
        List<TrainEntry> result = new ArrayList<>();
        Element section = firstElement(document, "trains");
        if (section == null) {
            return result;
        }
        NodeList trainNodes = section.getElementsByTagName("train");
        for (int i = 0; i < trainNodes.getLength(); i++) {
            Element trainEl = (Element) trainNodes.item(i);
            Element eigene = null;
            Element itrain = null;
            for (org.w3c.dom.Node n = trainEl.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    if ("composition".equals(e.getTagName())) {
                        eigene = e;
                    } else if ("itrainComposition".equals(e.getTagName())) {
                        itrain = e;
                    }
                }
            }
            List<TrainCompositionItem> items = eigene != null ? leseZusammenstellung(eigene) : List.of();
            // ältere Dateien: ohne eigenen iTrain-Stand gilt die gespeicherte Zusammenstellung als iTrain-Stand
            TrainEntry t = new TrainEntry(trainEl.getAttribute("name"),
                    itrain != null ? leseZusammenstellung(itrain) : items);
            t.getComposition().setAll(items);
            result.add(t);
        }
        return result;
    }

    private static List<ControlStateBlockEntry> readControlState(Document document) {
        List<ControlStateBlockEntry> result = new ArrayList<>();
        Element section = firstElement(document, "controlstate");
        if (section == null) {
            return result;
        }
        NodeList nodes = section.getElementsByTagName("block");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String train = el.hasAttribute("train") ? el.getAttribute("train") : null;
            result.add(new ControlStateBlockEntry(el.getAttribute("name"), train));
        }
        return result;
    }

    private static void readFieldsInto(Element el, ObservableList<CustomField> target) {
        NodeList fieldNodes = el.getElementsByTagName("field");
        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element fieldElement = (Element) fieldNodes.item(i);
            target.add(new CustomField(fieldElement.getAttribute("name"), fieldElement.getAttribute("value")));
        }
    }

    private static Element firstElement(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private static <T> Map<String, T> indexByName(Iterable<T> entries, Function<T, String> nameFn) {
        Map<String, T> map = new HashMap<>();
        for (T entry : entries) {
            map.put(nameFn.apply(entry), entry);
        }
        return map;
    }
}
