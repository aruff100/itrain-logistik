package com.example.itrain_logistik;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import com.example.itrain_logistik.kern.EchtzeitKern;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Verdrahtet das Hauptfenster: Menüzeile, Ribbon-Werkzeugleiste mit
 * digitaler Uhr, Öffnen/Speichern der .tcdz samt eigener Logistik-Datei
 * ({@link LogistikDocument}) und das Öffnen der vier/fünf Zusatzfenster
 * (Lokomotiven/Wagen/Blöcke/Züge/Belegung). Struktur an
 * {@code HelloController} in iTrain_import_export angelehnt.
 */
public final class MainController {

    @FXML private Menu recentFilesMenu;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem closeFileMenuItem;
    @FXML private MenuItem loadBackupMenuItem;

    @FXML private ToolBar ribbonToolBar;
    @FXML private Button openToolButton;
    @FXML private Button saveToolButton;
    @FXML private Button preferencesToolButton;

    @FXML private DigitalClockView digitalClock;
    @FXML private Circle tcdzKreis;
    @FXML private Label tcdzLabel;
    @FXML private Label browserLogistikStatus;
    @FXML private Label browserZuganzeigerStatus;
    @FXML private Label browserRoutenStatus;
    @FXML private javafx.scene.control.SeparatorMenuItem trennVerbindung;
    @FXML private Label statusLabel;
    @FXML private Circle verbindungsKreis;
    @FXML private javafx.scene.control.CheckMenuItem simulatorMenuItem;
    @FXML private javafx.scene.control.CheckMenuItem itrainMenuItem;
    @FXML private Label quelleLabel;
    @FXML private StackPane centerPane;
    @FXML private Menu ansichtenMenu;
    @FXML private javafx.scene.control.MenuButton ansichtenToolButton;
    /** In iTrain definierte Bahnhöfe (control-items/stations), angeheftet an die Anlagen-Infrastruktur. */
    @FXML private javafx.scene.control.MenuButton bahnhoefeToolButton;
    @FXML private Button austauschToolButton;
    @FXML private Button logistikNetzToolButton;
    @FXML private javafx.scene.control.MenuButton anzeigerToolButton;
    @FXML private Menu anzeigerMenu;
    @FXML private Button allesAufNullToolButton;
    @FXML private Label austauschLabel;
    @FXML private javafx.scene.layout.StackPane itrainSymbol;
    /** Ergebnis der iTrain-Prüfung beim Start; null = noch nicht geprüft. */
    private ItrainErkennung.Ergebnis itrainStatus;
    /** Läuft eine Online-Übermittlung mit iTrain? Bis das Protokoll da ist immer false. */
    private boolean itrainOnline;
    /** Online-Verbindung über XTC (Anwender 08.11.2026), null solange keine läuft. */
    private XtcVerbindung xtc;
    /** Züge, die ein Ladegleis von der falschen Seite befahren haben (bis zur Abfahrt). */
    private final java.util.Set<String> kopfMachen = new java.util.HashSet<>();
    @FXML
    private javafx.scene.control.Menu editMenu;
    @FXML
    private MenuItem kopierenMenuItem;
    @FXML
    private MenuItem einfuegenMenuItem;
    private long xtcZaehler;
    /** Verhindert, dass sich Hintergrundprüfungen überholen (z. B. bei langsamem Netz). */
    private final java.util.concurrent.atomic.AtomicBoolean itrainPruefungLaeuft =
            new java.util.concurrent.atomic.AtomicBoolean();
    private javafx.animation.Timeline itrainUeberwachung;
    /** Andock-Bereich um die Übersicht (Ansichten = Tabellen oben/unten/links/rechts). */
    private AnsichtenDock dock;
    @FXML private ImageView startImage;

    /**
     * Die rein lesende Anlagen-Uebersicht im centerPane (siehe
     * {@link #refreshUebersicht()}) - null, solange keine Datei offen ist.
     * Wird bei jedem Refresh entfernt und neu aufgebaut.
     */
    private javafx.scene.Node uebersichtView;

    private final LogistikDocument document = new LogistikDocument();

    private final TriggerLog triggerLog = new TriggerLog();
    /** Wagenladungen und Plaetze je Zug aus Zusammenstellung und Wagenfeldern. */
    private final Fahrzeugdaten fahrzeugdaten = new Fahrzeugdaten(
            () -> document.isOpen() ? document.getSnapshot() : null,
            id -> {
                com.example.itrain_logistik.kern.Gut g = document.isOpen() ? document.getAnlage().gut(id) : null;
                return g != null ? g.getLadeklasse() : null;
            });
    /** Der Echtzeit-Kern (siehe analyse/Konzept-Echtzeitkern.md). */
    private final EchtzeitKern kern = new EchtzeitKern(fahrzeugdaten::fuer);
    private Timeline kernTimeline;
    /** Programm wird gerade beendet: keine Rückfragen mehr aus sich schließenden Fenstern. */
    private boolean beendetWird;
    private int kernTakte;
    /** Takt (Sekunde) der letzten automatischen Aktualisierung des Züge-Fensters. */
    private int letzterZuegeTakt;
    private AnlagenCanvas uebersichtCanvas;
    /** Reiter der Übersicht (Anwender 09.11.2026): erster Reiter + je Tab/Modul ein weiterer. */
    private ReiterLeiste uebersichtReiter;

    @FXML
    private void initialize() {
        setToolIcon(openToolButton, "icons/open-icon.png");
        setToolIcon(saveToolButton, "icons/save-icon.png");
        setToolIcon(preferencesToolButton, "icons/settings-icon.png");
        // „Zusätzlich erforderlich“ prüft auch die Transportmittel (Anwender 11.11.2026)
        ErforderlichDialog.setFahrzeuge(new ErforderlichDialog.Fahrzeuge() {
            @Override
            public java.util.List<com.example.itrain_logistik.kern.ZugZustand> zuege() {
                return zugZustaende();
            }

            @Override
            public java.util.List<WagonEntry> wagen() {
                return document.isOpen() ? document.getSnapshot().wagons() : java.util.List.of();
            }

            @Override
            public java.util.List<TrainEntry> zugListe() {
                return document.isOpen() ? document.getSnapshot().trains() : java.util.List.of();
            }

            @Override
            public void geaendert() {
                document.markDirty();
                WagonsWindow.refreshIfOpen(document.getSnapshot().wagons(), document.getAnlage(), document::markDirty);
            }

            @Override
            public void wagenFenster() {
                WagonsWindow.show(stage(), document.getSnapshot().wagons(), document.getAnlage(), document::markDirty);
            }
        });
        // Mitgelieferte Geräusche in den Geräusche-Ordner (Anwender 09.11.2026)
        Thread.ofVirtual().start(() -> Geraeusche.auspacken(false));
        // Geräusch nicht abspielbar (z. B. Linux ohne ffmpeg-Bibliotheken): einmal je Start erklären
        Geraeusche.hinweis = (schluessel, teile) -> Platform.runLater(() -> {
            String[] t = teile.split("\u0000", 2);
            Texte.zeige(stage(), "Geräusche", Texte.dialog(schluessel, "datei", t[0], "fehler",
                    t.length > 1 ? t[1] : ""), null, "OK", schluessel);
        });

        // Die Mitte wird zum Andock-Bereich; die Übersicht bleibt dessen Hauptinhalt.
        javafx.scene.layout.BorderPane huelle = (javafx.scene.layout.BorderPane) centerPane.getParent();
        huelle.setCenter(null);
        dock = new AnsichtenDock(null, centerPane);
        huelle.setCenter(dock.getView());
        ansichtenMenu.setOnShowing(e -> fuelleAnsichten(ansichtenMenu.getItems()));
        ansichtenToolButton.setOnShowing(e -> fuelleAnsichten(ansichtenToolButton.getItems()));
        // Menü und Ribbon „Anzeiger“ (Anwender 11.11.2026): beim Öffnen neu gebaut (Bahnhöfe, Module, Browser an/aus)
        anzeigerMenu.setOnShowing(e -> anzeigerMenue(anzeigerMenu.getItems()));
        anzeigerToolButton.getItems().add(new MenuItem("…"));
        anzeigerToolButton.setOnShowing(e -> anzeigerMenue(anzeigerToolButton.getItems()));
        anzeigerMenue(anzeigerMenu.getItems());
        fuelleAnsichten(ansichtenMenu.getItems());
        fuelleAnsichten(ansichtenToolButton.getItems());
        bahnhoefeToolButton.setOnShowing(e -> fuelleBahnhoefe());
        ribbonTooltips();
        kern.addTransferBeobachter(this::transferGemeldet);
        AnlagenEditorWindow.setAssistent(fenster -> {
            if (document.isOpen()) {
                VerkehrsAssistent.starte(fenster, document, this::nachAssistent, false, this::zugZustaende);
            }
        });
        // Hauptknöpfe farblich getrennt, Pastellfarben (Anwender 20.10.2026)
        pastell(logistikNetzToolButton, "#cfe2f7");
        pastell(ansichtenToolButton, "#d6efc7");
        pastell(bahnhoefeToolButton, "#fbe3bd");
        pastell(austauschToolButton, "#c6ece6");
        // Zuganzeiger in Pastellblau wie die Anzeiger, Schrift im Blau der Tafel (Anwender 09.11.2026)
        pastell(anzeigerToolButton, "#c9d7f4", "#16307a");
        for (javafx.scene.control.SeparatorMenuItem t : java.util.List.of(trennVerbindung)) {
            trenn3d(t);
        }
        browserStatusEinrichten();
        pastell(allesAufNullToolButton, "#f5cccc");
        zeigeAustausch();
        fuelleBahnhoefe();
        String quelle = AppSettings.getInstance().getTriggerQuelle();
        simulatorMenuItem.setSelected(AppSettings.QUELLE_SIMULATOR.equals(quelle));
        itrainMenuItem.setSelected(AppSettings.QUELLE_ITRAIN.equals(quelle));
        zeigeQuelle();

        initStartImage();
        refreshRecentFilesMenu();
        autoLoadLastFile();
        updateDocumentState();
        applyClockSettings(true);
        startKern();
        // Kopieren/Einfügen nur, solange „Anlage bearbeiten“ offen ist (Anwender 09.11.2026)
        editMenu.setOnShowing(e -> {
            boolean editor = AnlagenCanvas.offenerEditor() != null;
            kopierenMenuItem.setDisable(!editor);
            einfuegenMenuItem.setDisable(!editor);
        });
        AnlagenCanvas.voreinstellungen = w -> {
            SettingsDialog.showPreferences(w instanceof Stage st ? st : stage(), this::applyClockSettings);
            xtcNeuStarten();
            webansichtStarten();
            zeigeQuelle();
        };
        SettingsDialog.tcdzQuelle = () -> document.isOpen() ? document.getSnapshot().sourceFile() : null;
        SettingsDialog.anlageQuelle = () -> document.isOpen() ? document.getAnlage() : null;
        SettingsDialog.reiterGeaendert = () -> {
            if (document.isOpen()) {
                document.markDirty();
                if (uebersichtReiter != null) {
                    uebersichtReiter.alleNeu();
                }
                AnlagenEditorWindow.neuZeichnenWennOffen();
            }
        };
        AnlagenCanvas.zugZusatz = n -> {
            XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(n) : null;
            String text = i != null ? i.anzeige() : "";
            if (kopfMachen.contains(n)) {
                text = "⚠ Kopf machen" + (text.isEmpty() ? "" : " · " + text);
            }
            return text;
        };
        AnlagenCanvas.zugSteht = n -> {
            XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(n) : null;
            return i == null ? null : i.getTempo() <= 0;
        };
        xtcNeuStarten();
        webansichtStarten();
        zeigeItrainVerbindung();
        // Erst nach dem Anzeigen des Hauptfensters prüfen, ob iTrain läuft (Anwender 14.10.2026).
        Platform.runLater(this::pruefeItrainBeimStart);
        // Bei jedem Start nach einer neuen Version suchen, wenn eingeschaltet (Anwender 11.11.2026)
        Platform.runLater(() -> UpdatePruefung.beimStart(stage()));
        ensureLogistikDirectoryExists();
        ensureSymbolDirectoryExists();
        // Erzwingt die erstmalige Konstruktion von SymbolRegistry (kopiert bei Bedarf
        // das mitgelieferte Standard-Symbolpaket und liest den Symbolordner ein) schon
        // beim Programmstart, statt erst beim ersten Oeffnen des Symbol-Managers.
        SymbolRegistry.getInstance();
    }

    /**
     * "Lade beim Start die letzte gespeicherte .logz Datei" - oeffnet beim
     * Programmstart automatisch die zuletzt geoeffnete/gespeicherte Datei
     * (Kopf der "Zuletzt verwendet"-Liste), sofern sie noch existiert.
     * Schlaegt das Oeffnen fehl (Datei geloescht/verschoben/beschaedigt),
     * bleibt das Programm einfach ohne geoeffnete Datei stehen - keine
     * Fehlermeldung direkt beim Start, um den Programmstart nicht zu stoeren.
     */
    /**
     * Beim Laden einer .logz (Anwender 11.11.2026): fehlt noch etwas (Lieferanten, Abnehmer, Reisende, Wagen), zeigt
     * „Zusätzlich erforderlich“ sich über dem Hauptfenster; abschaltbar im Fenster („Hinweise beim Start zeigen“).
     */
    private void erforderlichBeimLaden() {
        if (!AppSettings.getInstance().isErforderlichBeimStart()) {
            return;
        }
        javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(1200));
        // showAndWait ist in einer Animation nicht erlaubt: erst danach öffnen
        p.setOnFinished(e -> Platform.runLater(() -> {
            if (!document.isOpen() || stage() == null || !stage().isShowing()
                    || !ErforderlichDialog.hatOffenes(document.getAnlage())) {
                return;
            }
            if (ErforderlichDialog.zeigeAlles(stage(), document.getAnlage(), true)) {
                document.markDirty();
                refreshUebersicht();
            }
        }));
        p.play();
    }

    private void autoLoadLastFile() {
        List<String> recent = AppSettings.getInstance().getRecentFiles();
        if (recent.isEmpty()) {
            return;
        }
        File letzte = new File(recent.get(0));
        if (!letzte.isFile()) {
            return;
        }
        try {
            document.openLogz(letzte);
            Platform.runLater(this::fahrzeugdateiMelden);
            erforderlichBeimLaden();
            statusLabel.setText("Automatisch geladen: " + letzte.getName()
                    + "  (" + document.getSnapshot().locomotives().size() + " Loks, "
                    + document.getSnapshot().wagons().size() + " Wagen, "
                    + document.getSnapshot().blocks().size() + " Blöcke, "
                    + document.getSnapshot().trains().size() + " Züge)");
        } catch (Exception ex) {
            // Bewusst still - siehe Methodenkommentar.
        }
    }

    /**
     * "Lege beim Start den Ordner für logistics an, wenn nicht schon
     * vorhanden" - legt den in den Voreinstellungen (Reiter "Pfade")
     * eingestellten Logistik-Ordner an, falls er noch fehlt, damit er beim
     * ersten "Speichern" sicher existiert und man ihn schon vorher im
     * Explorer/Finder sehen kann. Best-effort: schlaegt das Anlegen fehl
     * (z.B. fehlende Rechte), wird das erst beim tatsaechlichen Speichern
     * als Fehlermeldung sichtbar, nicht schon beim Start.
     */
    private void ensureLogistikDirectoryExists() {
        File dir = new File(AppSettings.getInstance().getLogistikDirectory());
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    /** Wie {@link #ensureLogistikDirectoryExists()}, fuer den Symbolordner (Reiter "Pfade"). */
    private void ensureSymbolDirectoryExists() {
        File dir = new File(AppSettings.getInstance().getSymbolDirectory());
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    private void setToolIcon(Button button, String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                ImageView view = new ImageView(new Image(in));
                view.setFitWidth(20);
                view.setFitHeight(20);
                view.setPreserveRatio(true);
                button.setGraphic(view);
            }
        } catch (Exception ignored) {
            // Kein Icon gefunden - der Knopf bleibt beschriftet leer, nicht
            // weiter schlimm fuer dieses erste Geruest.
        }
    }

    /** Zeigt das Programmsymbol (Personenwagen) in der Mitte, solange keine Datei geladen ist. */
    private void initStartImage() {
        try (InputStream in = getClass().getResourceAsStream("app-icon.png")) {
            if (in != null) {
                startImage.setImage(new Image(in));
            }
        } catch (Exception ignored) {
            // ohne Bild faehrt das Programm trotzdem hoch
        }
        startImage.fitWidthProperty().bind(centerPane.widthProperty().multiply(0.5));
        startImage.fitHeightProperty().bind(centerPane.heightProperty().multiply(0.5));
    }

    private Stage stage() {
        return (Stage) ribbonToolBar.getScene().getWindow();
    }

    @FXML
    private void onOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Logistik-Datei öffnen");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("iTrain-Logistik-Dateien (*.logz)", "*.logz"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        File logistikDir = new File(AppSettings.getInstance().getLogistikDirectory());
        if (logistikDir.isDirectory()) {
            chooser.setInitialDirectory(logistikDir);
        }
        File chosen = chooser.showOpenDialog(stage());
        if (chosen == null) {
            return;
        }
        openLogzFile(chosen);
    }

    /**
     * "Darin wird eine .tcdz Verbunden" - legt aus einer gewaehlten .tcdz/
     * .tcd eine neue, gezippte .logz an (Speicherort wird direkt mit
     * abgefragt) und speichert sie sofort, damit sie danach ganz normal
     * ueber "Öffnen..." wieder geladen werden kann, ohne die .tcdz erneut
     * anzufassen.
     */
    @FXML
    private void onNewFromTcdz() {
        // Grundzüge vor der Einrichtung (Anwender 27.10.2026; Text in texte/Dialogtexte.txt, „grundzuege“)
        if (!Texte.zeige(stage(), "Neue Konfiguration", Texte.dialog("grundzuege"), "Abbruch", "Weiter", "grundzuege")) {
            return;
        }
        FileChooser tcdzChooser = new FileChooser();
        tcdzChooser.setTitle("iTrain-Anlagendatei wählen");
        tcdzChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("iTrain-Dateien (*.tcdz, *.tcd)", "*.tcdz", "*.tcd"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        File layoutDir = new File(AppSettings.getInstance().getLayoutFilesDirectory());
        if (layoutDir.isDirectory()) {
            tcdzChooser.setInitialDirectory(layoutDir);
        }
        File tcdzFile = tcdzChooser.showOpenDialog(stage());
        if (tcdzFile == null) {
            return;
        }

        FileChooser logzChooser = new FileChooser();
        logzChooser.setTitle("Neue Logistik-Datei speichern unter...");
        logzChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("iTrain-Logistik-Dateien (*.logz)", "*.logz"));
        File logistikDir = new File(AppSettings.getInstance().getLogistikDirectory());
        if (logistikDir.isDirectory()) {
            logzChooser.setInitialDirectory(logistikDir);
        }
        logzChooser.setInitialFileName(stripExtension(tcdzFile.getName()) + ".logz");
        File logzFile = logzChooser.showSaveDialog(stage());
        if (logzFile == null) {
            return;
        }
        if (!logzFile.getName().toLowerCase().endsWith(".logz")) {
            logzFile = new File(logzFile.getParentFile(), logzFile.getName() + ".logz");
        }

        // Fahrzeuge übernehmen? (Anwender 09.11.2026): gibt es zu dieser iTrain-Anlage schon eine Fahrzeugdatei,
        // kann die neue Konfiguration sie mitbenutzen.
        boolean uebernehmen = true;
        File gemeinsam = LogistikDocument.standardFahrzeugdatei(tcdzFile);
        if (gemeinsam.isFile()) {
            ButtonType ja = new ButtonType("Übernehmen", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
            ButtonType nein = new ButtonType("Nicht übernehmen", javafx.scene.control.ButtonBar.ButtonData.NO);
            Alert frage = new Alert(Alert.AlertType.CONFIRMATION, "Zu „" + tcdzFile.getName()
                    + "“ gibt es schon Fahrzeugdaten (" + gemeinsam.getName() + ": Angaben zu Lokomotiven, Wagen und "
                    + "Zügen).\n\n„Übernehmen“: diese Konfiguration benutzt sie mit; Änderungen gelten auch für die "
                    + "anderen Konfigurationen dieser Anlage.\n„Nicht übernehmen“: eigene, leere Fahrzeugdaten.",
                    nein, ja);
            frage.initOwner(stage());
            frage.setTitle("Neue Konfiguration");
            frage.setHeaderText("Fahrzeuge übernehmen?");
            uebernehmen = frage.showAndWait().orElse(nein) == ja;
        }
        try {
            document.neuAusTcdz(tcdzFile, logzFile, uebernehmen);
            document.save();
            AppSettings.getInstance().addRecentFile(logzFile.getAbsolutePath());
            refreshRecentFilesMenu();
            updateDocumentState();
            applyClockSettings(true);
            statusLabel.setText("Neu angelegt aus " + tcdzFile.getName() + ": " + logzFile.getName()
                    + "  (" + document.getSnapshot().locomotives().size() + " Loks, "
                    + document.getSnapshot().wagons().size() + " Wagen, "
                    + document.getSnapshot().blocks().size() + " Blöcke, "
                    + document.getSnapshot().trains().size() + " Züge)");
            Platform.runLater(this::einrichtungsDialog);
        } catch (Exception ex) {
            showError("Konnte nicht aus .tcdz angelegt werden", ex);
        }
    }

    /**
     * Einrichtung nach "Neu aus .tcdz" (Anwender 14.10.2026): Bahnhöfe usw. erscheinen wie bisher sofort; der
     * Dialog bittet, zuerst alle Wagen zu ergänzen, und öffnet auf Wunsch das Wagenfenster.
     */
    private void einrichtungsDialog() {
        if (!document.isOpen()) {
            return;
        }
        List<WagonEntry> wagen = document.getSnapshot().wagons();
        long offen = wagen.stream().filter(w -> !wagenVollstaendig(w)).count();
        if (wagen.isEmpty()) {
            Texte.zeige(stage(), "Einrichtung", Texte.dialog("einrichtung_keine_wagen"), null, "OK");
            einrichtungTriebzuege();
            return;
        }
        // „Wagen importieren“ (Anwender 09.11.2026): Angaben aus einer vorhandenen *.fahrzeuge.xml übernehmen
        int wahl = Texte.waehle(stage(), "Einrichtung", Texte.dialog("einrichtung_wagen", "offen", offen, "alle",
                wagen.size()), null, java.util.Arrays.asList("Später", "Wagen importieren...", "Wagen ergänzen"));
        if (wahl == 1) {
            // danach erneut fragen, mit der neuen Zahl unvollständiger Wagen
            onFahrzeugeImportierenErgebnis();
            Platform.runLater(this::einrichtungsDialog);
        } else if (wahl == 2) {
            WagonsWindow.show(stage(), document.getSnapshot().wagons(), document.getAnlage(), document::markDirty);
            WagonsWindow.beimSchliessen(() -> Platform.runLater(this::einrichtungTriebzuege));
        } else {
            einrichtungTriebzuege();
        }
    }

    /** Einrichtung, Schritt 2 (Anwender 22.10.2026): gibt es Triebzüge? Dann die Lokomotiven zeigen. */
    private void einrichtungTriebzuege() {
        if (!document.isOpen()) {
            return;
        }
        if (document.getSnapshot().locomotives().isEmpty()) {
            einrichtungFertig();
            return;
        }
        if (Texte.zeige(stage(), "Einrichtung", Texte.dialog("einrichtung_triebzuege"), "Nein",
                "Ja, Lokomotiven zeigen")) {
            LocomotivesWindow.show(stage(), document.getSnapshot().locomotives(), document.getAnlage(),
                    document::markDirty);
            LocomotivesWindow.beimSchliessen(() -> Platform.runLater(this::einrichtungFertig));
        } else {
            einrichtungFertig();
        }
    }

    /**
     * Ende der Einrichtung nach dem Import: nur Fahrzeuge. Die Bahnhöfe kommen erst über „Anlage bearbeiten“ und
     * den Assistenten (Anwender 27.10.2026).
     */
    private void einrichtungFertig() {
        statusLabel.setText(Texte.dialog("einrichtung_fertig").text());
    }

    /**
     * Beim „Anlage bearbeiten“, solange die iTrain-Bahnhöfe noch nicht eingebaut sind (Anwender 27.10.2026): der
     * Verkehrs-Assistent mit „n Bahnhöfe vorhanden“ (Abbruch/Weiter).
     */
    private void bahnhoefeAssistent() {
        VerkehrsAssistent.starte(stage(), document, this::nachAssistent, true, this::zugZustaende);
    }

    /** Laufzeitzustand aller Züge der .tcdz mit ihrer Ausstattung (Plätze, Wagenladungen). */
    private java.util.List<com.example.itrain_logistik.kern.ZugZustand> zugZustaende() {
        java.util.List<com.example.itrain_logistik.kern.ZugZustand> l = new java.util.ArrayList<>();
        if (document.isOpen()) {
            for (TrainEntry t : document.getSnapshot().trains()) {
                l.add(com.example.itrain_logistik.kern.ZugZustand.nurAusstattung(t.getName(),
                        fahrzeugdaten.fuer(t.getName()), document.getAnlage()));
            }
        }
        return l;
    }

    private void nachAssistent() {
        document.markDirty();
        refreshUebersicht();
        AnlagenEditorWindow.neuZeichnenWennOffen();
    }

    /** Menü Funktionen > Verkehrs-Assistent: jederzeit, um neue Verkehrswege zu erschließen. */
    @FXML
    private void onVerkehrsAssistent() {
        if (!requireOpenDocument()) {
            return;
        }
        VerkehrsAssistent.starte(stage(), document, this::nachAssistent, false, this::zugZustaende);
    }

    /** Fügt die iTrain-Bahnhöfe ein, falls noch nicht geschehen (erstes „Anlage bearbeiten“ bzw. Menü Bahnhöfe). */
    private void bahnhoefeEinfuegen() {
        com.example.itrain_logistik.kern.Anlage a = document.getAnlage();
        if (a.isBahnhoefeEingefuegt()) {
            return;
        }
        int n = a.bahnhoefeEinfuegen(document.bahnhofDaten(), document.bahnhofTypen()).size();
        document.markDirty();
        refreshUebersicht();
        statusLabel.setText(n + " Station(en) aus den iTrain-Bahnhöfen eingefügt.");
    }

    /** Wagenart gesetzt und – je nach Art – Sitzplätze bzw. Kapazität (oder Lade-Joker) eingetragen. */
    private static boolean wagenVollstaendig(WagonEntry w) {
        WagenArt art = LogistikFelder.parseEnum(LogistikFelder.getValue(w.getCustomFields(), LogistikFelder.WAGENART),
                WagenArt.values());
        if (art == null || art.getFeldsatz() == null) {
            return false;
        }
        java.util.function.Function<String, Double> zahl = k -> {
            try {
                return Double.parseDouble(LogistikFelder.getValue(w.getCustomFields(), k).replace(',', '.'));
            } catch (NumberFormatException ex) {
                return 0.0;
            }
        };
        return switch (art.getFeldsatz()) {
            case PERSONEN -> zahl.apply(LogistikFelder.SITZPLAETZE_1) + zahl.apply(LogistikFelder.SITZPLAETZE_2)
                    + zahl.apply(LogistikFelder.STEHPLAETZE) > 0;
            case GUETER -> zahl.apply(LogistikFelder.KAPAZITAET) > 0;
            case BEIDE -> zahl.apply(LogistikFelder.SITZPLAETZE_1) + zahl.apply(LogistikFelder.SITZPLAETZE_2)
                    + zahl.apply(LogistikFelder.STEHPLAETZE) > 0 && zahl.apply(LogistikFelder.KAPAZITAET) > 0;
            default -> true;
        };
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void openLogzFile(File file) {
        try {
            document.openLogz(file);
            AppSettings.getInstance().addRecentFile(file.getAbsolutePath());
            refreshRecentFilesMenu();
            updateDocumentState();
            applyClockSettings(true);
            statusLabel.setText("Geöffnet: " + file.getName()
                    + "  (" + document.getSnapshot().locomotives().size() + " Loks, "
                    + document.getSnapshot().wagons().size() + " Wagen, "
                    + document.getSnapshot().blocks().size() + " Blöcke, "
                    + document.getSnapshot().trains().size() + " Züge)");
            fahrzeugdateiMelden();
            erforderlichBeimLaden();
            if (!document.getSchattenbahnhofGeloest().isEmpty()) {
                statusLabel.setText("Schattenbahnhof nur noch über Gleise: Verbindung gelöst – "
                        + String.join("; ", document.getSchattenbahnhofGeloest())
                        + ". Reisende und Güter je Gleis im Schattenbahnhof einstellen.");
            }
            // Anwender 27.10.2026: wurde die .tcdz seit dem letzten Einlesen geändert, gleich anbieten
            if (document.tcdzGeaendert()) {
                Platform.runLater(() -> {
                    File t = document.getSnapshot().sourceFile();
                    if (Texte.zeige(stage(), "Datei öffnen", Texte.dialog("tcdz_geaendert", "datei",
                            t.getName()), "Später", "Jetzt abgleichen")) {
                        onAbgleichen();
                    }
                });
            }
        } catch (Exception ex) {
            showError("Datei konnte nicht geöffnet werden", ex);
        }
    }

    @FXML
    private void onSave() {
        if (!document.isOpen()) {
            return;
        }
        try {
            document.save();
            statusLabel.setText("Gespeichert: " + document.getLogistikFile().getAbsolutePath());
        } catch (Exception ex) {
            showError("Konnte nicht gespeichert werden", ex);
        }
    }

    /**
     * "Es gibt einen Knopf der die 'Daten' mit der .tcdz abgleicht." - liest
     * die gekoppelte .tcdz erneut ein, uebernimmt neu hinzugekommene
     * Lokomotiven/Wagen/Bloecke/Zuege/Blockpositionen und entfernt
     * weggefallene, ohne eigene Datenfelder zu verlieren. Speichert den
     * Abgleich direkt in die .logz.
     */
    @FXML
    private void onAbgleichen() {
        if (!requireOpenDocument()) {
            return;
        }
        try {
            document.abgleichen();
            document.save();
            updateDocumentState();
            // applyClockSettings(true) setzt hier auch die programmweite
            // LaufendeUhr zurueck - ein offenes Uhr-Fenster braucht deshalb
            // kein eigenes refreshIfOpen mehr, es liest sie laufend ab
            // (siehe LaufendeUhr-Klassenkommentar).
            applyClockSettings(true);
            // "Blöcke, Uhr, und Wagen, Lokomotiven und Züge werden bei der
            // Logistik aktualisiert" - auch bereits offene Fenster aktualisieren,
            // nicht nur den Dokument-Zustand (sonst zeigt ein offenes Fenster
            // weiter den Stand von vor dem Abgleichen).
            LocomotivesWindow.refreshIfOpen(document.getSnapshot().locomotives(), document::markDirty);
            WagonsWindow.refreshIfOpen(document.getSnapshot().wagons(), document.getAnlage(), document::markDirty);
            BlocksWindow.refreshIfOpen(document.getSnapshot().blocks(), document.getAnlage(), document::markDirty);
            TrainsWindow.refreshIfOpen(document.getSnapshot(), document::markDirty, kern);
            ControlStateWindow.refreshIfOpen(document.getSnapshot().controlState());
            statusLabel.setText("Abgeglichen: " + document.getSnapshot().locomotives().size() + " Loks, "
                    + document.getSnapshot().wagons().size() + " Wagen, "
                    + document.getSnapshot().blocks().size() + " Blöcke, "
                    + document.getSnapshot().trains().size() + " Züge, Uhr-Faktor " + LaufendeUhr.faktorText()
                    + " (so wie zuletzt in iTrain gespeichert) aus " + document.getSnapshot().sourceFile()
                    + (document.getAbgleichGeaendert().isEmpty() ? " – keine Zugänderung"
                    : " – in iTrain geändert: " + String.join(", ", document.getAbgleichGeaendert()))
                    + (document.getAbgleichEigeneBehalten().isEmpty() ? ""
                    : " (eigene Zusammenstellung behalten: " + String.join(", ", document.getAbgleichEigeneBehalten())
                    + ")"));
        } catch (Exception ex) {
            showError("Konnte nicht mit der .tcdz abgeglichen werden", ex);
        }
    }

    @FXML
    private void onCloseFile() {
        document.close();
        LocomotivesWindow.closeIfOpen();
        WagonsWindow.closeIfOpen();
        BlocksWindow.closeIfOpen();
        TrainsWindow.closeIfOpen();
        ControlStateWindow.closeIfOpen();
        UhrWindow.closeIfOpen();
        ZuganzeigerWindow.closeIfOpen();
        TriggerLogWindow.closeIfOpen();
        AnlagenEditorWindow.closeIfOpen();
        if (dock != null) {
            dock.alleSchliessen();
        }
        GueterKatalogWindow.closeIfOpen();
        ObjektDetailWindow.closeAll();
        updateDocumentState();
        digitalClock.stop();
        LaufendeUhr.anhalten();
        statusLabel.setText("Datei geschlossen.");
    }

    @FXML
    private void onLoadBackup() {
        if (!document.isOpen()) {
            return;
        }
        List<File> backups = document.listBackups();
        if (backups.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "Für diese Datei liegt noch keine Sicherung vor.").showAndWait();
            return;
        }
        List<String> names = backups.stream().map(File::getName).toList();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle("Backup laden");
        dialog.setHeaderText(null);
        dialog.setContentText("Sicherung:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(chosenName -> backups.stream()
                .filter(f -> f.getName().equals(chosenName))
                .findFirst()
                .ifPresent(this::restoreBackup));
    }

    private void restoreBackup(File backup) {
        try {
            document.restoreBackup(backup);
            updateDocumentState();
            statusLabel.setText("Backup geladen: " + backup.getName());
        } catch (Exception ex) {
            showError("Backup konnte nicht geladen werden", ex);
        }
    }

    @FXML
    private void onExit() {
        if (confirmSaveBeforeExit()) {
            beenden();
        }
    }

    /**
     * Programmende (Anwender 16.10.2026: "Wenn das Programm beendet wird, müssen alle Fenster mitgeschlossen
     * werden"): schließt alle übrigen Fenster - auch die ohne Besitzer wie Zuganzeiger, Simulator und
     * herausgelöste Ansichten -, damit sie ihre Lage speichern, und beendet dann JavaFX.
     */
    private void beenden() {
        beendetWird = true;
        // Simulator still schließen: die Quelle bleibt eingestellt, keine iTrain-Prüfung mit Dialog
        SimulatorWindow.schliesseStill();
        xtcStoppen();
        Webansicht.stoppe();
        ZuganzeigerWeb.stoppe();
        RoutenWeb.stoppe();
        Geraeusche.alleStoppen();
        Uebersetzung.fehlendeSpeichern();
        if (kernTimeline != null) {
            kernTimeline.stop();
        }
        Stage haupt = stage();
        for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
            if (w != haupt && w.isShowing()) {
                try {
                    w.hide();
                } catch (RuntimeException ex) {
                    // ein Fenster, das sich nicht schließen lässt, darf das Beenden nicht aufhalten
                }
            }
        }
        if (haupt != null) {
            haupt.hide();
        }
        Platform.exit();
    }

    /**
     * Wird vom Schliessen-Knopf (X) des Hauptfensters aufgerufen (siehe
     * {@link LogistikApplication}). Fragt bei ungespeicherten Aenderungen
     * nach, ob gespeichert werden soll - bei "Abbrechen" bleibt das Fenster
     * offen.
     */
    public void onWindowCloseRequested(WindowEvent event) {
        if (!confirmSaveBeforeExit()) {
            event.consume();
        } else {
            beenden();
        }
    }

    /**
     * "Wenn das Programm beendet wird, kommt eine Nachfrage ob die
     * Aenderungen gespeichert werden sollen. Bei Ja wird die vorhandene
     * Datei ueberschrieben, ohne Datei Dialog." Liefert {@code true}, wenn
     * das Programm jetzt beendet werden darf (keine Aenderungen, "Nein" oder
     * erfolgreich gespeichert), {@code false} bei "Abbrechen" oder einem
     * Fehler beim Speichern.
     */
    private boolean confirmSaveBeforeExit() {
        if (!document.isOpen() || !document.isDirty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Änderungen speichern?");
        alert.setHeaderText(null);
        alert.setContentText("Es gibt ungespeicherte Änderungen. Vor dem Beenden speichern?");
        ButtonType ja = new ButtonType("Ja");
        ButtonType nein = new ButtonType("Nein");
        alert.getButtonTypes().setAll(ja, nein, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return false;
        }
        if (result.get() == ja) {
            try {
                document.save();
            } catch (Exception ex) {
                showError("Konnte nicht gespeichert werden", ex);
                return false;
            }
        }
        return true;
    }

    @FXML
    private void onCenterWindows() {
        WindowState.centerAll(stage());
        DialogPlacement.resetOffset();
    }

    @FXML
    private void onPreferences() {
        SettingsDialog.showPreferences(stage(), this::applyClockSettings);
        // Reiter iTrain (XTC) und Port der Browser-Ansicht wirken nach dem Schließen (Anwender 09.11.2026)
        xtcNeuStarten();
        webansichtStarten();
        zeigeQuelle();
    }

    @FXML
    private void onOpenLocomotives() {
        if (!requireOpenDocument()) {
            return;
        }
        LocomotivesWindow.show(stage(), document.getSnapshot().locomotives(), document.getAnlage(), document::markDirty);
    }

    @FXML
    private void onOpenWagons() {
        if (!requireOpenDocument()) {
            return;
        }
        WagonsWindow.show(stage(), document.getSnapshot().wagons(), document.getAnlage(), document::markDirty);
    }

    @FXML
    private void onOpenBlocks() {
        if (!requireOpenDocument()) {
            return;
        }
        BlocksWindow.show(stage(), document.getSnapshot().blocks(), document.getAnlage(), document::markDirty);
    }

    @FXML
    private void onOpenTrains() {
        if (!requireOpenDocument()) {
            return;
        }
        TrainsWindow.show(stage(), document.getSnapshot(), document::markDirty, kern);
    }

    @FXML
    private void onOpenControlState() {
        if (!requireOpenDocument()) {
            return;
        }
        ControlStateWindow.show(stage(), document.getSnapshot().controlState());
    }

    /**
     * Erster erfolgreicher Transfer nach „Neu aus .tcdz“ (Anwender 22.10.2026): Glückwunsch-Dialog, je einmal für
     * Personen und Güter; danach nie wieder für diese Datei.
     */
    private void transferGemeldet(String gutId, com.example.itrain_logistik.kern.Station von,
            com.example.itrain_logistik.kern.Station nach, double menge, String zug) {
        com.example.itrain_logistik.kern.Anlage a = kern.getAnlage();
        if (a == null || !document.isOpen()) {
            return;
        }
        String vonText = von != null ? "von " + bahnhofText(von) + " " : "";
        String zugText = zug != null && !zug.isBlank() ? " mit „" + zug + "“" : "";
        if (gutId == null && a.isErsterPersonenTransferOffen()) {
            a.setErsterPersonenTransferOffen(false);
            document.markDirty();
            String text = "Der erste Transfer " + vonText + "zu " + bahnhofText(nach) + " wurde erfolgreich absolviert.";
            String detail = Symbole.zahl(menge) + (menge == 1 ? " Reisender ist" : " Reisende sind") + zugText
                    + " angekommen.";
            Platform.runLater(() -> GlueckwunschDialog.zeige(stage(), "Glückwunsch!", text, detail));
        } else if (gutId != null && a.isErsterGueterTransferOffen()) {
            a.setErsterGueterTransferOffen(false);
            document.markDirty();
            com.example.itrain_logistik.kern.Gut gut = a.gut(gutId);
            String gutName = gut != null ? gut.getName() : gutId;
            String text = "Der erste Gütertransport " + vonText + "zu " + bahnhofText(nach)
                    + " wurde erfolgreich absolviert.";
            String detail = Symbole.zahl(menge) + " " + (gut != null
                    ? gut.getEinheit() + " " : "")
                    + gutName + zugText + " entladen.";
            Platform.runLater(() -> GlueckwunschDialog.zeige(stage(), "Glückwunsch!", text, detail));
        }
    }

    /** "Bahnhof „Scheuerfeld Hbf“" – Art und Name der Station aus der Logistik. */
    private static String bahnhofText(com.example.itrain_logistik.kern.Station s) {
        return s.getArt().getLabel() + " „" + s.getAnzeigeName() + "“";
    }

    /** Zuganzeiger Personenverkehr: nächste nötige Fahrten (Anwender 16.10.2026, getrennt 22.10.2026). */
    @FXML
    private void onOpenZuganzeiger() {
        zuganzeiger(ZuganzeigerWindow.Art.PERSONEN);
    }

    /** Zuganzeiger Güterverkehr (Anwender 22.10.2026). */
    @FXML
    private void onOpenZuganzeigerGueter() {
        zuganzeiger(ZuganzeigerWindow.Art.GUETER);
    }

    private void zuganzeiger(ZuganzeigerWindow.Art art) {
        if (!requireOpenDocument()) {
            return;
        }
        ZuganzeigerWindow.show(art, this::fahrthinweise, id -> document.isOpen() ? document.getAnlage().gut(id) : null);
    }

    /** Je Bahnhof ein Untermenü „Personenverkehr“/„Güterverkehr“ (Zuganzeiger je Bahnhof, Anwender 09.11.2026). */
    private java.util.List<javafx.scene.control.MenuItem> zuganzeigerJeBahnhof() {
        java.util.List<javafx.scene.control.MenuItem> l = new java.util.ArrayList<>();
        if (!document.isOpen()) {
            return l;
        }
        for (com.example.itrain_logistik.kern.Station s : document.getAnlage().bahnhoefeFuerAnzeiger()) {
            Menu m = new Menu(s.getAnzeigeName());
            for (ZuganzeigerWindow.Art art : ZuganzeigerWindow.Art.values()) {
                javafx.scene.control.MenuItem i = new javafx.scene.control.MenuItem(
                        art == ZuganzeigerWindow.Art.PERSONEN ? "Personenverkehr" : "Güterverkehr");
                String id = s.getId();
                i.setOnAction(e -> zuganzeigerBahnhof(art, id));
                m.getItems().add(i);
            }
            l.add(m);
        }
        return l;
    }

    private void zuganzeigerBahnhof(ZuganzeigerWindow.Art art, String stationId) {
        if (!requireOpenDocument()) {
            return;
        }
        if (!(document.getAnlage().objekt(stationId) instanceof com.example.itrain_logistik.kern.Station s)) {
            return;
        }
        ZuganzeigerWindow.showFuer(art, stationId, s.getAnzeigeName(), () -> {
            if (!document.isOpen() || !(document.getAnlage().objekt(stationId)
                    instanceof com.example.itrain_logistik.kern.Station st)) {
                return java.util.Set.of();
            }
            return document.getAnlage().verbund(st);
        }, this::fahrthinweise, id -> document.isOpen() ? document.getAnlage().gut(id) : null);
    }

    /** Hinweise für die Zuganzeiger (Fenster und Browser). */
    private java.util.List<com.example.itrain_logistik.kern.Fahrthinweise.Hinweis> fahrthinweise() {
        if (!document.isOpen()) {
            return java.util.List.of();
        }
        java.util.List<com.example.itrain_logistik.kern.ZugZustand> zuege = new java.util.ArrayList<>();
        for (TrainEntry t : document.getSnapshot().trains()) {
            zuege.add(kern.zug(t.getName()));
        }
        return com.example.itrain_logistik.kern.Fahrthinweise.ermitteln(kern.getAnlage(), zuege);
    }

    @FXML
    private void onOpenUhr() {
        if (!requireOpenDocument()) {
            return;
        }
        UhrWindow.show(stage(), AppSettings.getInstance().getShowClockSeconds());
    }

    /**
     * "Mit Druck auf dieses Symbol oeffnet sich ein Rx/Tx Fenster das alle
     * Trigger zeile fuer zeile auflistet." - bewusst ohne requireOpenDocument,
     * das Protokoll ist unabhaengig vom geoeffneten Dokument.
     */
    @FXML
    private void onOpenTriggerLog(javafx.scene.input.MouseEvent e) {
        if (e != null && (e.isControlDown() || e.isShortcutDown())) {
            // Strg + Klick: Rx/Tx der Verbindung (Anwender 08.11.2026), nur mit gewählter Quelle
            String q = AppSettings.getInstance().getTriggerQuelle();
            if (AppSettings.QUELLE_ITRAIN.equals(q)) {
                XtcMonitorWindow.show(stage(), "Rx/Tx iTrain (XTC)" + (xtc != null
                        ? " – " + xtc.getRechner() + ":" + xtc.getPort() : " – keine Verbindung"));
            } else if (AppSettings.QUELLE_SIMULATOR.equals(q)) {
                XtcMonitorWindow.show(stage(), "Rx/Tx Simulator");
            } else {
                statusLabel.setText("Rx/Tx gibt es erst, wenn unter Einstellungen „iTrain“ oder „Simulator“ gewählt ist.");
            }
            return;
        }
        TriggerLogWindow.show(stage(), triggerLog);
    }

    @FXML
    private void onAustauschUmschalten() {
        kern.setAngehalten(!kern.isAngehalten());
        zeigeAustausch();
        statusLabel.setText(kern.isAngehalten() ? "Güter-/Personenaustausch angehalten."
                : "Güter-/Personenaustausch gestartet.");
    }

    /**
     * Knopf hinter "Bahnhöfe": zeigt Pause, solange der Austausch läuft, sonst Start; die Statusleiste
     * zeigt den Zustand dauerhaft (Anwender 12.10.2026).
     */
    private void zeigeAustausch() {
        boolean an = !kern.isAngehalten();
        javafx.scene.Node symbol;
        if (an) {
            javafx.scene.shape.Rectangle l = new javafx.scene.shape.Rectangle(0, 0, 4, 12);
            javafx.scene.shape.Rectangle r = new javafx.scene.shape.Rectangle(7, 0, 4, 12);
            l.setFill(Color.web("#d08000"));
            r.setFill(Color.web("#d08000"));
            symbol = new javafx.scene.Group(l, r);
        } else {
            javafx.scene.shape.Polygon p = new javafx.scene.shape.Polygon(0, 0, 11, 6, 0, 12);
            p.setFill(Color.web("#2e9e3e"));
            symbol = p;
        }
        austauschToolButton.setGraphic(symbol);
        austauschToolButton.setText(an ? "Pause" : "Start");
        austauschToolButton.setTooltip(Symbole.tip(an
                ? "Gesamten Güter- und Personenaustausch anhalten"
                : "Gesamten Güter- und Personenaustausch starten"));
        zeigeAustauschText();
    }

    /** Erklärung zu jedem Ribbon-Knopf (Anwender 22.10.2026: bei Uhr und „Alles auf 0 setzen“ fehlte sie). */
    private void ribbonTooltips() {
        java.util.Map<String, String> texte = java.util.Map.ofEntries(
                java.util.Map.entry("openToolButton", "Logistik-Datei (.logz) öffnen"),
                java.util.Map.entry("saveToolButton", "Logistik-Datei speichern"),
                java.util.Map.entry("abgleichenToolButton", "Mit der .tcdz abgleichen: Loks, Wagen, Blöcke, Züge und "
                        + "Zugrouten neu aus iTrain lesen"),
                java.util.Map.entry("preferencesToolButton", "Voreinstellungen"),
                java.util.Map.entry("logistikNetzToolButton", "Anlage bearbeiten: Orte, Bahnhöfe, Betriebe, Lager "
                        + "anlegen und verbinden"),
                java.util.Map.entry("ansichtenToolButton", "Tabellen öffnen oder im Hauptfenster andocken"),
                java.util.Map.entry("bahnhoefeToolButton", "In iTrain definierte Bahnhöfe anheften oder öffnen"),
                java.util.Map.entry("anzeigerToolButton", "Alle Anzeiger: Zuganzeiger, Routen-Anzeiger, "
                        + "Modul-Anzeiger und Uhr im Fenster oder im Browser – auf diesem oder einem anderen Gerät, mit "
                        + "QR-Code"),
                java.util.Map.entry("uhrToolButton", "Modelluhr in einem eigenen Fenster zeigen (Zeit und Faktor "
                        + "aus iTrain)"),
                java.util.Map.entry("zuganzeigerToolButton", "Zuganzeiger für Personen- oder Güterverkehr: die "
                        + "nächsten nötigen Fahrten als Abfahrtstafel, auf jeden Monitor ziehbar"),
                java.util.Map.entry("browserLogistikToolButton", "Browser Logistik: Anlage und Module im Browser "
                        + "auf diesem Rechner öffnen; „Auf anderem Gerät“ zeigt Adressen, Port und QR-Code"),
                java.util.Map.entry("browserZuganzeigerToolButton", "Browser Zuganzeiger: Abfahrtstafeln im Browser "
                        + "auf diesem Rechner öffnen, auch je Bahnhof; „Auf anderem Gerät“ zeigt Adressen, Port und "
                        + "QR-Code"),
                java.util.Map.entry("verknuepftToolButton", "Zeigt, mit welcher iTrain-Datei (.tcdz) diese "
                        + "Logistik-Datei verknüpft ist und ob sie gefunden wird"),
                java.util.Map.entry("routenAnzeigerToolButton", "Routen-Anzeiger: ein Zug mit vorherigem, aktuellem "
                        + "und nächstem Block, Position, Bremsstrecke, Haltepunkt und Signal – als Fenster (auch "
                        + "mehrere) oder im Browser"),
                java.util.Map.entry("itrainDatenToolButton", "Lokomotiven, Wagen, Blöcke, Züge, Belegung, Symbole "
                        + "und Güter"),
                java.util.Map.entry("allesAufNullToolButton", "Alle Bestände, Wartenden, Ladungen und den offenen "
                        + "Bedarf auf 0 setzen (die Anlage selbst bleibt)"));
        for (javafx.scene.Node n : ribbonToolBar.getItems()) {
            String t = n.getId() == null ? null : texte.get(n.getId());
            if (t != null && n instanceof javafx.scene.control.Control c) {
                c.setTooltip(Symbole.tip(t));
            }
        }
    }

    /**
     * Pastellfarbe für einen Ribbon-Knopf; dunkle Schrift, damit er in beiden Themes lesbar bleibt. Inline-Stil, weil
     * die Theme-Stylesheets sonst Hintergrund und Schrift überschreiben; beim Überfahren etwas dunkler.
     */
    static void pastell(javafx.scene.control.ButtonBase b, String farbe) {
        pastell(b, farbe, "#1f2328");
    }

    /** Pastellknopf mit eigener Schriftfarbe (z. B. Zuganzeiger: hellblau mit dunkelblauer Schrift). */
    static void pastell(javafx.scene.control.ButtonBase b, String farbe, String schrift) {
        Color c = Color.web(farbe);
        String normal = stil(c, schrift);
        String hover = stil(c.deriveColor(0, 1.15, 0.92, 1), schrift);
        b.setStyle(normal);
        b.hoverProperty().addListener((obs, alt, an) -> b.setStyle(an ? hover : normal));
        // Die Beschriftung eines MenuButton ist ein eigenes Label; das dunkle Theme färbt ".label" hell und
        // überschreibt die geerbte Schriftfarbe. Ein Stylesheet am Knopf selbst hat Vorrang vor dem der Szene.
        b.getStyleClass().add("pastell");
        b.getStylesheets().add("data:text/css;base64," + java.util.Base64.getEncoder().encodeToString(
                // nur direkte Kinder: der Tooltip hängt in CSS am Knopf, seine Schrift sonst schwarz auf dunkel
                (".pastell, .pastell > .label { -fx-text-fill: " + schrift + "; }"
                        + " .pastell > .arrow-button > .arrow { -fx-background-color: " + schrift + "; }")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Unter Windows reichte das Stylesheet nicht (Anwender 20.10.2026: Schrift blieb weiß). Deshalb zusätzlich
        // die inneren Beschriftungen des Skins direkt per Inline-Stil färben; Inline-Stile schlagen jedes Stylesheet.
        Runnable innen = () -> {
            for (javafx.scene.Node n : b.lookupAll(".label")) {
                if (n != b) {
                    n.setStyle("-fx-text-fill: " + schrift + ";");
                }
            }
            for (javafx.scene.Node n : b.lookupAll(".arrow")) {
                n.setStyle("-fx-background-color: " + schrift + ";");
            }
        };
        b.skinProperty().addListener((obs, alt, neu) -> javafx.application.Platform.runLater(innen));
        b.sceneProperty().addListener((obs, alt, neu) -> javafx.application.Platform.runLater(innen));
        b.textProperty().addListener((obs, alt, neu) -> javafx.application.Platform.runLater(innen));
        javafx.application.Platform.runLater(innen);
    }

    private static String stil(Color c, String schrift) {
        String hex = String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
        return "-fx-background-color: " + hex + "; -fx-text-fill: " + schrift + "; -fx-mark-color: " + schrift + ";"
                + " -fx-border-color: derive(" + hex + ", 25%) derive(" + hex + ", -40%) derive(" + hex
                + ", -40%) derive(" + hex + ", 25%); -fx-border-width: 2; -fx-border-radius: 3;"
                + " -fx-background-radius: 3;";
    }

    /** Statusleiste: Austausch läuft/angehalten (der Tempo-Regler hat keine Pause mehr, Anwender 14.10.2026). */
    private void zeigeAustauschText() {
        if (austauschLabel == null) {
            return;
        }
        boolean an = !kern.isAngehalten();
        String text = an ? "▶ Austausch läuft" : "❚❚ Austausch angehalten";
        if (!Uebersetzung.tr(text).equals(austauschLabel.getText())) {
            austauschLabel.setText(text);
            austauschLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (an ? "#2e9e3e" : "#d03030") + ";");
        }
    }

    /**
     * "Alles auf 0 setzen": löscht keine Daten, setzt aber alle Bestände, Warteschlangen, offenen
     * Bedarfe, Zugladungen und Statistiken auf 0; Erzeugung und Bereitstellung beginnen neu.
     */
    @FXML
    private void onAllesAufNull() {
        if (!requireOpenDocument()) {
            return;
        }
        Alert frage = new Alert(Alert.AlertType.CONFIRMATION,
                "Alle Bestände, wartenden Passagiere, offenen Bedarfe, Zugladungen und Statistiken werden auf 0 "
                        + "gesetzt. Objekte, Gleise und Einstellungen bleiben erhalten.",
                ButtonType.YES, ButtonType.NO);
        frage.initOwner(stage());
        frage.setHeaderText("Alles auf 0 setzen?");
        if (frage.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        document.getAnlage().allesAufNull();
        ZuganzeigerWindow.leeren();
        document.markDirty();
        refreshUebersicht();
        statusLabel.setText("Alles auf 0 gesetzt.");
    }

    @FXML
    private void onOpenLogistikNetz() {
        if (!requireOpenDocument()) {
            return;
        }
        bahnhoefeAssistent(); // Bahnhöfe erst jetzt, über den Assistenten (Anwender 27.10.2026)
        AnlagenEditorWindow.show(stage(), document.getAnlage(), kern, () -> document.getSnapshot().blocks(),
                () -> {
                    document.markDirty();
                    refreshUebersicht();
                },
                this::onSave);
    }

    @FXML
    private void onOpenSymbolManager() {
        SymbolManagerWindow.show(stage(), null, this::refreshUebersicht);
    }

    @FXML
    private void onGeraeusche() {
        SettingsDialog.zeigeGeraeusche(stage());
    }

    @FXML
    private void onOpenLadegutKatalog() {
        if (!requireOpenDocument()) {
            return;
        }
        GueterKatalogWindow.show(stage(), document.getAnlage(), () -> {
            document.markDirty();
            refreshUebersicht();
        });
    }

    /**
     * Menü "Ansichten" und Ribbon-Knopf: je Tabelle ein Eintrag (Häkchen = offen). Klick öffnet die
     * Ansicht als Fenster bzw. schließt sie; andocken durch Ziehen ans Hauptfenster.
     */
    /**
     * Ribbon "Bahnhöfe": die in der .tcdz definierten iTrain-Bahnhöfe mit ihren Blöcken. ✓ = an die
     * Anlagen-Infrastruktur angeheftet (Klick öffnet die Station), sonst heftet ein Klick sie an.
     */
    private void fuelleBahnhoefe() {
        java.util.List<MenuItem> l = bahnhoefeToolButton.getItems();
        l.clear();
        if (!document.isOpen()) {
            bahnhoefeToolButton.setText("Bahnhöfe");
            MenuItem leer = new MenuItem("Keine Datei geöffnet");
            leer.setDisable(true);
            l.add(leer);
            return;
        }
        com.example.itrain_logistik.kern.Anlage a = document.getAnlage();
        java.util.Map<String, java.util.List<String>> daten = document.bahnhofDaten();
        bahnhoefeToolButton.setText("Bahnhöfe (" + daten.size() + ")");
        if (daten.isEmpty()) {
            MenuItem leer = new MenuItem("In der .tcdz sind keine Bahnhöfe definiert");
            leer.setDisable(true);
            l.add(leer);
            return;
        }
        for (java.util.Map.Entry<String, java.util.List<String>> e : daten.entrySet()) {
            com.example.itrain_logistik.kern.Station st = a.stationFuerItrain(e.getKey());
            boolean angeheftet = st != null && e.getKey().equals(st.getItrainName());
            MenuItem mi = new MenuItem((angeheftet ? "✓ " : "＋ ") + e.getKey() + "  (" + e.getValue().size()
                    + " Blöcke: " + String.join(", ", e.getValue()) + ")");
            mi.setOnAction(ev -> {
                if (angeheftet) {
                    ObjektDetailWindow.show(stage(), st, a, kern, () -> document.getSnapshot().blocks(), () -> {
                        document.markDirty();
                        refreshUebersicht();
                    });
                } else {
                    bahnhoefeEinfuegen();
                    a.itrainBahnhoefeAnheften(java.util.Map.of(e.getKey(), e.getValue()), document.bahnhofTypen(), true);
                    document.markDirty();
                    refreshUebersicht();
                    statusLabel.setText("Bahnhof „" + e.getKey() + "“ an die Anlagen-Infrastruktur angeheftet.");
                }
            });
            l.add(mi);
        }
        l.add(new javafx.scene.control.SeparatorMenuItem());
        MenuItem alle = new MenuItem("Alle anheften und fehlende Gleise ergänzen");
        alle.setOnAction(ev -> {
            bahnhoefeEinfuegen();
            int n = a.itrainBahnhoefeAnheften(daten, document.bahnhofTypen(), true).size();
            document.markDirty();
            refreshUebersicht();
            statusLabel.setText(n == 0 ? "Alle Bahnhöfe sind bereits vollständig angeheftet."
                    : n + " Bahnhof/Bahnhöfe angeheftet oder ergänzt.");
        });
        l.add(alle);
    }

    private void fuelleAnsichten(java.util.List<MenuItem> ziel) {
        ziel.clear();
        for (TabellenWindow.Ansicht v : TabellenWindow.Ansicht.values()) {
            javafx.scene.control.CheckMenuItem mi = new javafx.scene.control.CheckMenuItem(v.getTitel());
            mi.setSelected(dock != null && dock.istOffen(v));
            mi.setOnAction(e -> {
                if (!requireOpenDocument()) {
                    mi.setSelected(false);
                    return;
                }
                if (dock.istOffen(v) && !mi.isSelected()) {
                    dock.schliesse(v);
                } else {
                    dock.zeige(v);
                }
            });
            ziel.add(mi);
        }
        ziel.add(new javafx.scene.control.SeparatorMenuItem());
        for (AnsichtenDock.Seite s : AnsichtenDock.Seite.values()) {
            Menu m = new Menu("Andocken " + s.getLabel());
            for (TabellenWindow.Ansicht v : TabellenWindow.Ansicht.values()) {
                MenuItem mi = new MenuItem(v.getTitel());
                mi.setOnAction(e -> {
                    if (requireOpenDocument()) {
                        dock.andocken(v, s);
                    }
                });
                m.getItems().add(mi);
            }
            ziel.add(m);
        }
    }

    private boolean requireOpenDocument() {
        if (document.isOpen()) {
            return true;
        }
        new Alert(Alert.AlertType.INFORMATION,
                "Bitte zuerst über \"Datei > Öffnen...\" eine .logz-Datei laden oder über "
                        + "\"Datei > Neu aus .tcdz importieren...\" neu anlegen.")
                .showAndWait();
        return false;
    }

    @FXML
    private void onHelp() {
        Hilfe.uebersicht(stage());
    }

    @FXML
    private void onOpenManual() {
        Hilfe.handbuch();
    }

    /** Hilfe > „Auf Update prüfen“ (Anwender 11.11.2026): neueste Veröffentlichung auf GitHub. */
    @FXML
    private void onCheckForUpdate() {
        UpdatePruefung.vomMenue(stage());
    }

    /** Hilfe > „Mit der KI prüfen…“ (Anwender 11.11.2026). */
    @FXML
    private void onKiPruefen() {
        DateienFenster.kiPruefen(stage(), document, this::onSave);
    }

    /** Hilfe > „Kontakt…“ (Anwender 11.11.2026): Mail an den Autor, Dateien zum Anhängen. */
    @FXML
    private void onKontakt() {
        DateienFenster.kontakt(stage(), document, this::onSave);
    }

    /** Hilfe > „Über“ (Anwender 11.11.2026: wie in iTrain Import/Export). */
    @FXML
    private void onAbout() {
        UeberDialog.zeige(stage());
    }

    /**
     * Taktgeber des Echtzeit-Kerns: jede Sekunde wird der Kern um die seither vergangene
     * Modellzeit weitergerechnet (LaufendeUhr), alle zwei Sekunden zusaetzlich die
     * Trigger-Protokolldatei des iTrain-Logistik-Simulators gelesen (Rueckfallweg, falls die
     * TCP-Pipe nicht erreichbar ist; doppelte Trigger verwirft der Kern). Danach werden die
     * Schilder der Uebersicht und ein offenes Zuege-Fenster aufgefrischt.
     */
    private void startKern() {
        kernTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> kernTakt()));
        kernTimeline.setCycleCount(Timeline.INDEFINITE);
        kernTimeline.play();
    }

    private void kernTakt() {
        aktualisiereVerbindungsAnzeige();
        zeigeBrowserStatus();
        if (RoutenWeb.laeuft()) {
            RoutenWeb.aktualisiere(document.isOpen() ? routenQuelle : null);
        }
        if (++tcdzTakte % 10 == 0) {
            zeigeTcdzMarker();
        }
        if (xtc != null) {
            zeigeQuelle(); // Zahl der verbundenen Züge
        }
        // Modul-Anzeiger (Fenster und Browser), Anwender 11.11.2026
        ModulAnzeiger.aktualisiere(document.isOpen() ? document.getAnlage() : null,
                zug -> xtc != null ? xtc.info(zug) : null);
        if (!document.isOpen()) {
            return;
        }
        kernTakte++;
        zeigeAustauschText();
        java.time.LocalTime zeit = LaufendeUhr.aktuelleZeit();
        int tag = LaufendeUhr.modellTag();
        for (String m = kern.naechsteMeldung(); m != null; m = kern.naechsteMeldung()) {
            statusLabel.setText("⚠ " + m);
        }
        if (document.isOpen()) {
            // ohne laufende Uhr ist zeit null (beim Start gefunden, 09.11.2026)
            Webansicht.aktualisiere(document.getAnlage(), Uebersetzung.tr("Tag") + " " + tag + ", "
                    + (zeit != null ? zeit.withNano(0).toString() : "--:--"));
        }
        // Zuganzeiger im Browser (Anwender 09.11.2026): alle 2 Takte, die Seiten fragen alle 3 s
        if (ZuganzeigerWeb.laeuft() && kernTakte % 2 == 0) {
            ZuganzeigerWeb.aktualisiere(document.isOpen() ? fahrthinweise() : null,
                    id -> document.isOpen() ? document.getAnlage().gut(id) : null,
                    zeit != null ? String.format("%02d:%02d", zeit.getHour(), zeit.getMinute()) : "",
                    document.isOpen() ? document.getAnlage() : null);
        }
        int stunde = tag * 24 + (zeit != null ? zeit.getHour() : 0);
        kern.tick(LaufendeUhr.gesamtSimSekunden(), tag, stunde,
                zeit != null ? zeit.withNano(0).toString() : "");
        if (uebersichtReiter != null) {
            uebersichtReiter.aktualisiere();
        }
        // Züge-Fenster nur im eingestellten Takt neu aufbauen (Voreinstellungen > Aktualisierung > Züge;
        // "nur beim Start" = nie automatisch), sonst flackert es.
        int zuegeSekunden = AppSettings.getInstance().getUpdateIntervalZuege();
        if (zuegeSekunden > 0 && kernTakte - letzterZuegeTakt >= zuegeSekunden) {
            letzterZuegeTakt = kernTakte;
            TrainsWindow.refreshIfOpen(document.getSnapshot(), document::markDirty, kern);
        }
    }

    /**
     * "Unter in der Statusleiste zeigst Du die Verbindung mit einem gruenen
     * Kreis an. iTrain-Logistik mit iTrain-Logistik-Simulator. Gibt es keine
     * Verbindung bleibt es rot." - laeuft unabhaengig davon, ob gerade ein
     * Dokument offen ist, deshalb vor dem fruehen Rueckgabepunkt oben in
     * {@link #kernTakt()}.
     */
    private void aktualisiereVerbindungsAnzeige() {
        if (verbindungsKreis != null) {
            boolean simulatorOffen = simulatorMenuItem != null && simulatorMenuItem.isSelected()
                    && SimulatorWindow.istOffen();
            boolean xtcVerbunden = xtc != null && xtc.verbundeneZuege() > 0;
            verbindungsKreis.setFill(simulatorOffen || xtcVerbunden ? Color.LIMEGREEN : Color.RED);
        }
    }

    private void applyClockSettings() {
        applyClockSettings(false);
    }

    /**
     * @param neueUhrDaten true, wenn seit dem letzten Aufruf frisch aus der
     *     .tcdz gelesene Uhrdaten vorliegen (Programmstart/Oeffnen/Neu
     *     anlegen/Abgleichen) - dann wird die programmweite
     *     {@link LaufendeUhr} neu gesetzt. false bei einer reinen
     *     Anzeige-Einstellungsaenderung (Einstellungen-Dialog): dort darf
     *     die laufende Uhr NICHT zurueckgesetzt werden, sonst springt sie
     *     bei jedem Oeffnen der Einstellungen auf den (dann schon
     *     veralteten) Snapshot-Stand zurueck - siehe Projektauftrag "Die Uhr
     *     im Ribbon und die Uhr im Fenster zeigen unterschiedliche
     *     Uhrzeiten an."
     */
    private void applyClockSettings(boolean neueUhrDaten) {
        AppSettings settings = AppSettings.getInstance();
        digitalClock.setShowSeconds(settings.getShowClockSeconds());
        if (neueUhrDaten) {
            if (document.isOpen()) {
                LaufendeUhr.setzen(document.getSnapshot().clock());
            } else {
                LaufendeUhr.anhalten();
            }
        }
        if (document.isOpen() && settings.getShowClock()) {
            digitalClock.folgeLaufenderUhr();
        } else {
            digitalClock.stop();
        }
    }

    /**
     * Verknüpfte iTrain-Datei (Anwender 10.11.2026): statt der Kopfzeile unter dem Ribbon steht der Name der
     * Logistik-Datei im Fenstertitel; ob die .tcdz gefunden wird, zeigt der Marker „iTrain-Datei“ in der Statusleiste
     * (grün/rot). Einzelheiten im Dialog „Verknüpfte Datei“.
     */
    private void zeigeDateinamen() {
        boolean open = document.isOpen() && document.getLogistikFile() != null;
        Stage st = ribbonToolBar.getScene() != null ? (Stage) ribbonToolBar.getScene().getWindow() : null;
        if (st != null) {
            st.setTitle(open ? "iTrain-Logistik – " + document.getLogistikFile().getName() : "iTrain-Logistik");
        }
        zeigeTcdzMarker();
    }

    private File verknuepfteTcdz() {
        return document.isOpen() && document.getSnapshot() != null ? document.getSnapshot().sourceFile() : null;
    }

    /** Statusleiste: grün = verknüpfte .tcdz gefunden, rot = nicht gefunden, grau = keine Datei offen. */
    private void zeigeTcdzMarker() {
        if (tcdzKreis == null) {
            return;
        }
        if (!document.isOpen()) {
            tcdzKreis.setFill(Color.GRAY);
            javafx.scene.control.Tooltip.install(tcdzKreis.getParent(), Symbole.tip("Keine Datei geladen."));
            return;
        }
        File tcdz = verknuepfteTcdz();
        // die Datei kann auf einem Netz- oder Cloud-Laufwerk liegen: nicht auf dem Anwendungsthread prüfen
        Thread.ofVirtual().start(() -> {
            boolean da = tcdz != null && tcdz.isFile();
            Platform.runLater(() -> {
                tcdzKreis.setFill(da ? Color.LIMEGREEN : Color.RED);
                String text = "Verknüpfte iTrain-Datei: " + (tcdz == null ? "–" : tcdz.getAbsolutePath()) + "\n"
                        + (da ? "Gefunden." : "Nicht gefunden.") + "\nKlick: Dialog „Verknüpfte Datei“";
                if (!text.equals(tcdzMarkerText)) {
                    tcdzMarkerText = text;
                    javafx.scene.control.Tooltip.install(tcdzKreis.getParent(), Symbole.tip(text));
                }
            });
        });
    }

    private String tcdzMarkerText;
    private int tcdzTakte;

    /**
     * Ribbon „Verknüpfte Datei“ und Klick auf den Marker: zeigt Logistik-, iTrain- und Fahrzeugdatei; der Pfad der
     * iTrain-Datei ist grün, wenn sie gefunden wird, sonst rot. „Aktualisieren“ prüft erneut und gleicht ab.
     */
    @FXML
    private void onVerknuepfteDatei() {
        if (!requireOpenDocument()) {
            return;
        }
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage());
        d.setTitle("Verknüpfte Datei");
        d.setHeaderText("Mit dieser iTrain-Datei ist die Logistik-Datei verknüpft");
        Label logz = new Label();
        Label tcdz = new Label();
        Label zustand = new Label();
        Label fahrzeuge = new Label();
        for (Label l : java.util.List.of(logz, tcdz, zustand, fahrzeuge)) {
            l.setWrapText(true);
            l.setMaxWidth(620);
        }
        Runnable zeigen = () -> {
            File t = verknuepfteTcdz();
            boolean da = t != null && t.isFile();
            String farbe = da ? "#2e9e3e" : "#d03030";
            logz.setText(document.getLogistikFile() != null ? document.getLogistikFile().getAbsolutePath() : "–");
            tcdz.setText(t == null ? "–" : t.getAbsolutePath());
            tcdz.setStyle("-fx-font-weight: bold; -fx-text-fill: " + farbe + ";");
            zustand.setText(da ? Uebersetzung.tr("Gefunden, zuletzt geändert {0}", java.time.format.DateTimeFormatter
                    .ofPattern("dd.MM.yyyy HH:mm").format(java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(t.lastModified()), java.time.ZoneId.systemDefault())))
                    : "Nicht gefunden");
            zustand.setStyle("-fx-font-weight: bold; -fx-text-fill: " + farbe + ";");
            fahrzeuge.setText(document.getFahrzeugFile() != null ? document.getFahrzeugFile().getAbsolutePath() : "–");
            zeigeTcdzMarker();
        };
        zeigen.run();
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.addRow(0, new Label("Logistik-Datei:"), logz);
        g.addRow(1, new Label("iTrain-Datei:"), tcdz);
        g.addRow(2, new Label("Zustand:"), zustand);
        g.addRow(3, new Label("Fahrzeugdatei:"), fahrzeuge);
        Button aktualisieren = new Button("Aktualisieren");
        aktualisieren.setTooltip(Symbole.tip("Prüft erneut, ob die iTrain-Datei gefunden wird, und gleicht die "
                + "Logistik mit ihr ab (Loks, Wagen, Blöcke, Züge, Zugrouten)"));
        aktualisieren.setOnAction(e -> {
            zeigen.run();
            File t = verknuepfteTcdz();
            if (t != null && t.isFile()) {
                onAbgleichen();
                zeigen.run();
            } else {
                statusLabel.setText("Die verknüpfte iTrain-Datei wurde nicht gefunden – nichts abgeglichen.");
            }
        });
        Button andere = new Button("Andere Datei wählen…");
        andere.setOnAction(e -> {
            onTcdzVerknuepfen();
            zeigen.run();
        });
        pastell(aktualisieren, "#d6efc7");
        pastell(andere, "#cfe2f7");
        javafx.scene.layout.HBox knoepfe = new javafx.scene.layout.HBox(10, aktualisieren, andere);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(14, g, knoepfe);
        inhalt.setPadding(new javafx.geometry.Insets(6, 4, 0, 4));
        d.getDialogPane().setContent(inhalt);
        d.getDialogPane().getButtonTypes().add(new ButtonType("Schließen", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        Fenster.merken(d, "verknuepfteDatei");
        d.showAndWait();
    }

    /** Menü-Trennstrich mit 3D-Wirkung (dunkle über heller Linie, Anwender 10.11.2026). */
    static void trenn3d(javafx.scene.control.SeparatorMenuItem t) {
        if (t == null) {
            return;
        }
        javafx.scene.layout.Region dunkel = new javafx.scene.layout.Region();
        dunkel.setStyle("-fx-background-color: rgba(0,0,0,0.38); -fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
        javafx.scene.layout.Region hell = new javafx.scene.layout.Region();
        hell.setStyle("-fx-background-color: rgba(255,255,255,0.85); -fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
        javafx.scene.layout.VBox v = new javafx.scene.layout.VBox(dunkel, hell);
        v.setPadding(new javafx.geometry.Insets(3, 0, 3, 0));
        v.setMinWidth(120);
        t.setContent(v);
    }

    static javafx.scene.control.SeparatorMenuItem trenn3d() {
        javafx.scene.control.SeparatorMenuItem t = new javafx.scene.control.SeparatorMenuItem();
        trenn3d(t);
        return t;
    }

    // ------------------------------------------------------------------ Browser-Seiten in der Statusleiste

    private final javafx.scene.control.Tooltip browserLogistikTip = Symbole.tip("");
    private final javafx.scene.control.Tooltip browserZuganzeigerTip = Symbole.tip("");
    private final javafx.scene.control.Tooltip browserRoutenTip = Symbole.tip("");

    private void browserStatusEinrichten() {
        if (browserLogistikStatus == null) {
            return;
        }
        browserLogistikStatus.setTooltip(browserLogistikTip);
        browserZuganzeigerStatus.setTooltip(browserZuganzeigerTip);
        browserLogistikStatus.setStyle("-fx-cursor: hand;");
        browserZuganzeigerStatus.setStyle("-fx-cursor: hand;");
        browserLogistikStatus.setOnMouseClicked(e -> onBrowserAnsicht());
        browserZuganzeigerStatus.setOnMouseClicked(e -> onZuganzeigerBrowser());
        browserRoutenStatus.setTooltip(browserRoutenTip);
        browserRoutenStatus.setStyle("-fx-cursor: hand;");
        browserRoutenStatus.setOnMouseClicked(e -> onRoutenBrowser());
        zeigeBrowserStatus();
    }

    /**
     * Statusleiste links (Anwender 10.11.2026): „Browser Logistik: n“ und „Browser Zuganzeiger: n“ – n = Geräte, die
     * die Seiten gerade offen haben. Beim Überfahren stehen die Adressen der Seiten und der Geräte.
     */
    private void zeigeBrowserStatus() {
        if (browserLogistikStatus == null) {
            return;
        }
        AppSettings st = AppSettings.getInstance();
        browserZeile(browserLogistikStatus, browserLogistikTip, "Browser Logistik", Webansicht.laeuft(),
                Webansicht.geraete(), st.getWebPort(), java.util.List.of("/", "/modul/alle"));
        browserZeile(browserZuganzeigerStatus, browserZuganzeigerTip, "Browser Zuganzeiger", ZuganzeigerWeb.laeuft(),
                ZuganzeigerWeb.geraete(), st.getAnzeigerWebPort(), java.util.List.of("/", "/personen", "/gueter"));
        browserZeile(browserRoutenStatus, browserRoutenTip, "Browser Routen-Anzeiger", RoutenWeb.laeuft(),
                RoutenWeb.geraete(), st.getRoutenWebPort(), java.util.List.of("/"));
    }

    private void browserZeile(Label l, javafx.scene.control.Tooltip tip, String name, boolean an,
            java.util.List<String> geraete, int port, java.util.List<String> seiten) {
        String text = Uebersetzung.tr(name) + ": " + (an ? String.valueOf(geraete.size()) : Uebersetzung.tr("aus"));
        if (!text.equals(l.getText())) {
            l.setText(text);
        }
        StringBuilder b = new StringBuilder();
        if (!an) {
            b.append(Uebersetzung.tr("Ausgeschaltet. Klick öffnet die Einstellungen."));
        } else {
            String rechner = Webansicht.rechnername();
            b.append(Uebersetzung.tr("Adressen")).append(":\n");
            for (String sx : seiten) {
                b.append("http://").append(Webansicht.eigeneAdresse()).append(':').append(port).append(sx).append('\n');
            }
            if (rechner != null) {
                b.append("http://").append(rechner).append(':').append(port).append("/\n");
            }
            b.append("http://localhost:").append(port).append("/ (").append(Uebersetzung.tr("dieser Rechner")).append(")\n\n");
            b.append(Uebersetzung.tr("Verbundene Geräte")).append(": ").append(geraete.size());
            for (String ge : geraete) {
                b.append('\n').append(ge);
            }
        }
        if (!b.toString().equals(tip.getText())) {
            tip.setText(b.toString());
        }
    }

    /** Funktionen > „Logistik im Browser“: öffnet die Übersicht im Browser dieses Rechners. */
    @FXML
    private void onLogistikImBrowser() {
        if (!Webansicht.laeuft()) {
            statusLabel.setText("Browser Logistik ist ausgeschaltet – hier einschalten.");
            onBrowserAnsicht();
            return;
        }
        getHostServicesSicher("http://localhost:" + AppSettings.getInstance().getWebPort() + "/");
    }

    /** Funktionen > „Zuganzeiger im Browser“: öffnet die Auswahl der Anzeiger im Browser dieses Rechners. */
    @FXML
    private void onZuganzeigerImBrowser() {
        if (!ZuganzeigerWeb.laeuft()) {
            statusLabel.setText("Browser Zuganzeiger ist ausgeschaltet – hier einschalten.");
            onZuganzeigerBrowser();
            return;
        }
        getHostServicesSicher("http://localhost:" + AppSettings.getInstance().getAnzeigerWebPort() + "/");
    }

    // ------------------------------------------------------------------ Routen-Anzeiger (Anwender 10.11.2026)

    /** Modul-Anzeiger als Fenster für ein Modul (Anwender 11.11.2026: Auswahl über das Menü, nicht im Fenster). */
    private void onModulAnzeiger(String schluessel) {
        if (!requireOpenDocument()) {
            return;
        }
        ModulAnzeiger.aktualisiere(document.getAnlage(), zug -> xtc != null ? xtc.info(zug) : null);
        ModulAnzeiger.zeigeFenster(schluessel);
    }

    /** Untermenü „Modul-Anzeiger“: je Modul ein Eintrag (Fenster bzw. Seite im Browser dieses Rechners). */
    private Menu modulAnzeigerMenue(boolean browser) {
        Menu m = new Menu("Modul-Anzeiger");
        if (document.isOpen()) {
            ModulAnzeiger.aktualisiere(document.getAnlage(), zug -> xtc != null ? xtc.info(zug) : null);
            for (ModulAnzeiger.Bereich b : ModulAnzeiger.bereiche().values()) {
                MenuItem i = new MenuItem(b.titel());
                if (browser) {
                    i.setDisable(!Webansicht.laeuft());
                    i.setOnAction(e -> getHostServicesSicher("http://localhost:" + AppSettings.getInstance().getWebPort()
                            + "/anzeiger/" + b.schluessel()));
                } else {
                    i.setOnAction(e -> onModulAnzeiger(b.schluessel()));
                }
                m.getItems().add(i);
            }
        }
        if (m.getItems().isEmpty()) {
            MenuItem leer = new MenuItem("Keine Module");
            leer.setDisable(true);
            m.getItems().add(leer);
        }
        return m;
    }

    @FXML
    private void onRoutenAnzeiger() {
        if (!requireOpenDocument()) {
            return;
        }
        RoutenAnzeigerWindow.show(routenQuelle);
    }

    /** Daten für die Routen-Anzeiger (Fenster und Browser). */
    private final RoutenAnzeigerWindow.Quelle routenQuelle = new RoutenAnzeigerWindow.Quelle() {
            @Override
            public java.util.List<String> zuege() {
                java.util.List<String> l = new java.util.ArrayList<>();
                if (document.isOpen()) {
                    for (TrainEntry t : document.getSnapshot().trains()) {
                        l.add(t.getName());
                    }
                }
                return l;
            }

            @Override
            public XtcVerbindung.ZugInfo info(String zug) {
                return xtc != null ? xtc.info(zug) : null;
            }

            @Override
            public double blockLaenge(String block) {
                return xtc != null ? xtc.blockLaenge(block) : -1;
            }

            @Override
            public BlockEntry.Weg weg(String zug) {
                return wegFuer(zug, true);
            }

            @Override
            public BlockEntry.Weg wegZurueck(String zug) {
                return wegFuer(zug, false);
            }

            @Override
            public boolean gleisende(String zug, boolean voraus) {
                XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(zug) : null;
                if (i == null || i.getBlock() == null || !document.isOpen() || i.getRichtung().isEmpty()) {
                    return false;
                }
                String seite = voraus ? i.getRichtung() : ("next".equals(i.getRichtung()) ? "prev" : "next");
                boolean bekannt = false;
                boolean nachbar = false;
                for (BlockEntry b : document.getSnapshot().blocks()) {
                    bekannt |= !b.wege().isEmpty();
                    if (i.getBlock().equals(b.getName())) {
                        for (BlockEntry.Weg w : b.wege()) {
                            nachbar |= w.seite().equals(seite);
                        }
                    }
                }
                return bekannt && !nachbar;
            }

            @Override
            public RoutenAnzeigerWindow.Umschlag umschlag(String zug) {
                return umschlagFuer(zug);
            }

            @Override
            public RoutenAnzeigerWindow.Bild bild(String zug) {
                return zugBild(zug);
            }
        };

    /**
     * Der Block nach dem aktuellen: zuerst, was der Zug an dieser Stelle zuletzt wirklich gefahren ist, sonst der
     * Nachfolger in der iTrain-Zugroute (Reihenfolge der Blöcke in der .tcdz, am Ende wieder von vorn).
     */
    private String naechsterBlockFuer(String zug) {
        XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(zug) : null;
        if (i == null || i.getBlock() == null || !document.isOpen()) {
            return null;
        }
        String gelernt = xtc.gelernterNachfolger(zug, i.getBlock());
        if (gelernt != null) {
            return gelernt;
        }
        for (com.example.itrain_logistik.kern.Linie l : document.getAnlage().getLinien()) {
            if (l.getName() != null && l.getName().equals(i.getRoute())) {
                java.util.List<String> b = l.getBlockNamen();
                int k = b.indexOf(i.getBlock());
                if (k >= 0 && b.size() > 1) {
                    String n = b.get((k + 1) % b.size());
                    return n.equals(i.getVorherBlock()) && k > 0 ? b.get(k - 1) : n;
                }
            }
        }
        return null;
    }

    /**
     * Weg des Zugs aus seinem Block zum nächsten (Anwender 10.11.2026): die .tcdz nennt je Block und Seite die
     * Nachbarblöcke mit den Weichen dazwischen ({@link BlockEntry#wege()}, nach „Abgleichen“ vorhanden). Gewählt
     * wird der Weg zur zuletzt gefahrenen Folge, sonst zu einem Block der Zugroute, sonst der in iTrain bevorzugte.
     * Ohne diese Angaben nur der Zielblock aus {@link #naechsterBlockFuer}.
     */
    private BlockEntry.Weg wegFuer(String zug, boolean voraus) {
        XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(zug) : null;
        if (i == null || i.getBlock() == null || !document.isOpen()) {
            return null;
        }
        String seite = voraus ? i.getRichtung() : ("next".equals(i.getRichtung()) ? "prev" : "next");
        java.util.List<BlockEntry.Weg> moeglich = new java.util.ArrayList<>();
        boolean bekannt = false;
        for (BlockEntry b : document.getSnapshot().blocks()) {
            bekannt |= !b.wege().isEmpty();
            if (i.getBlock().equals(b.getName())) {
                for (BlockEntry.Weg w : b.wege()) {
                    if (w.seite().equals(seite)) {
                        moeglich.add(w);
                    }
                }
            }
        }
        if (!voraus) {
            // hinter dem Zug: der in iTrain bevorzugte Nachbar dieser Seite
            for (BlockEntry.Weg w : moeglich) {
                if (w.bevorzugt()) {
                    return w;
                }
            }
            return moeglich.isEmpty() ? null : moeglich.get(0);
        }
        String vermutet = naechsterBlockFuer(zug);
        if (moeglich.isEmpty()) {
            // kennt die Datei die Wege, endet das Gleis hier (Prellbock); sonst nur die Vermutung
            return bekannt || vermutet == null ? null
                    : new BlockEntry.Weg(i.getRichtung(), java.util.List.of(), vermutet, false);
        }
        for (BlockEntry.Weg w : moeglich) {
            if (w.ziel().equals(vermutet)) {
                return w;
            }
        }
        java.util.List<String> route = java.util.List.of();
        for (com.example.itrain_logistik.kern.Linie l : document.getAnlage().getLinien()) {
            if (l.getName() != null && l.getName().equals(i.getRoute())) {
                route = l.getBlockNamen();
            }
        }
        for (BlockEntry.Weg w : moeglich) {
            if (route.contains(w.ziel()) && !w.ziel().equals(i.getVorherBlock())) {
                return w;
            }
        }
        for (BlockEntry.Weg w : moeglich) {
            if (w.bevorzugt()) {
                return w;
            }
        }
        return moeglich.get(0);
    }

    /** Je Zug: Summe der in diesem Halt ein- und ausgestiegenen Reisenden und wann sie sich zuletzt geändert hat. */
    private final java.util.Map<String, double[]> personenStand = new java.util.HashMap<>();

    /**
     * Vorgänge am haltenden Zug für den Routen-Anzeiger (Anwender 10.11.2026): am Bahnsteig „▲ n ausgestiegen“ /
     * „▼ n eingestiegen“ (Zahlen dieses Halts), am Ladegleis „Entladen ▲ Güter“ / „Beladen ▼ Güter“. Aktiv = in den
     * letzten 2,5 s hat sich die Personenzahl geändert bzw. im letzten Schritt wurde ein Gut bewegt.
     */
    private RoutenAnzeigerWindow.Umschlag umschlagFuer(String zug) {
        if (!document.isOpen()) {
            return null;
        }
        com.example.itrain_logistik.kern.Anlage a = document.getAnlage();
        com.example.itrain_logistik.kern.ZugZustand z = a.getZuege().get(zug);
        if (z == null || z.getHalte().isEmpty()) {
            personenStand.remove(zug);
            return null;
        }
        java.util.List<String> zeilen = new java.util.ArrayList<>();
        boolean pAktiv = false;
        boolean gAktiv = false;
        long aus = 0;
        long ein = 0;
        boolean bahnsteig = false;
        for (com.example.itrain_logistik.kern.ZugZustand.Halt h : z.getHalte()) {
            com.example.itrain_logistik.kern.Gleis g = h.gleis();
            if (g == null) {
                continue;
            }
            if (g.getArt() == com.example.itrain_logistik.kern.GleisArt.BAHNSTEIG) {
                bahnsteig = true;
                aus += (long) Math.floor(g.getLetztAusgestiegen() + 1e-9);
                ein += (long) Math.floor(g.getLetztEingestiegen() + 1e-9);
            } else {
                String ent = gutNamen(a, z.entladenImHalt());
                String lad = gutNamen(a, z.geladenImHalt());
                if (!ent.isEmpty()) {
                    zeilen.add(Uebersetzung.tr("Entladen ▲ {0}", ent));
                }
                if (!lad.isEmpty()) {
                    zeilen.add(Uebersetzung.tr("Beladen ▼ {0}", lad));
                }
                gAktiv |= !g.getAktivEntladen().isEmpty() || !g.getAktivLaden().isEmpty();
            }
        }
        if (bahnsteig) {
            if (aus > 0) {
                zeilen.add(0, Uebersetzung.tr("▲ {0} ausgestiegen", aus));
            }
            if (ein > 0) {
                zeilen.add(aus > 0 ? 1 : 0, Uebersetzung.tr("▼ {0} eingestiegen", ein));
            }
            double[] st = personenStand.computeIfAbsent(zug, k -> new double[] {0, 0});
            if (st[0] != aus + ein) {
                st[0] = aus + ein;
                st[1] = System.currentTimeMillis();
            }
            pAktiv = aus + ein > 0 && System.currentTimeMillis() - st[1] < 2500;
        }
        return new RoutenAnzeigerWindow.Umschlag(zeilen, pAktiv, gAktiv);
    }

    private static String gutNamen(com.example.itrain_logistik.kern.Anlage a, java.util.Set<String> ids) {
        StringBuilder b = new StringBuilder();
        for (String id : ids) {
            com.example.itrain_logistik.kern.Gut gut = a.gut(id);
            b.append(b.length() == 0 ? "" : ", ").append(Uebersetzung.tr(gut != null ? gut.getName() : id));
        }
        return b.toString();
    }

    /**
     * Platzhalterbild eines Zugs: die Fahrzeuge in der Reihenfolge der Zusammenstellung (in iTrain vorn beginnend).
     * Lok = Antrieb aus Feld „Antriebsart“ bzw. iTrain-Typ, mit Feld „Triebzug“ ein Triebwagen; Wagen mit Plätzen =
     * Personenwagen, sonst Güterwagen.
     */
    private RoutenAnzeigerWindow.Bild zugBild(String zug) {
        java.util.List<RoutenAnzeigerWindow.Fahrzeug> fz = new java.util.ArrayList<>();
        if (!document.isOpen()) {
            return new RoutenAnzeigerWindow.Bild(fz);
        }
        for (TrainEntry t : document.getSnapshot().trains()) {
            if (!t.getName().equals(zug)) {
                continue;
            }
            for (TrainCompositionItem it : t.getComposition()) {
                if (it.getKind() == TrainCompositionItem.Kind.LOCOMOTIVE) {
                    RoutenAnzeigerWindow.Lok lok = RoutenAnzeigerWindow.Lok.DIESEL;
                    boolean triebzug = false;
                    for (LocomotiveEntry le : document.getSnapshot().locomotives()) {
                        if (le.getName().equals(it.getName())) {
                            String a = LogistikFelder.getValue(le.getCustomFields(), LogistikFelder.ANTRIEBSART);
                            String typ = (a == null || a.isBlank() ? String.valueOf(le.getType()) : a).toLowerCase();
                            if (typ.contains("dampf") || typ.contains("steam")) {
                                lok = RoutenAnzeigerWindow.Lok.DAMPF;
                            } else if (typ.contains("elektr") || typ.contains("electric")) {
                                lok = RoutenAnzeigerWindow.Lok.ELEKTRO;
                            } else if (typ.contains("batterie") || typ.contains("battery") || typ.contains("wasserstoff")
                                    || typ.contains("hydrogen")) {
                                lok = RoutenAnzeigerWindow.Lok.BATTERIE;
                            }
                            String tz = LogistikFelder.getValue(le.getCustomFields(), LogistikFelder.TRIEBZUG);
                            triebzug = tz != null && !tz.isBlank() && !"false".equalsIgnoreCase(tz)
                                    && !"nein".equalsIgnoreCase(tz);
                        }
                    }
                    fz.add(new RoutenAnzeigerWindow.Fahrzeug(triebzug ? RoutenAnzeigerWindow.Art.TRIEBWAGEN
                            : RoutenAnzeigerWindow.Art.LOK, lok));
                } else {
                    boolean sitze = false;
                    for (WagonEntry we : document.getSnapshot().wagons()) {
                        if (we.getName().equals(it.getName())) {
                            for (String f : java.util.List.of(LogistikFelder.SITZPLAETZE_1, LogistikFelder.SITZPLAETZE_2,
                                    LogistikFelder.STEHPLAETZE)) {
                                String v = LogistikFelder.getValue(we.getCustomFields(), f);
                                sitze |= v != null && !v.isBlank() && !"0".equals(v.trim());
                            }
                        }
                    }
                    fz.add(new RoutenAnzeigerWindow.Fahrzeug(sitze ? RoutenAnzeigerWindow.Art.PERSONEN
                            : RoutenAnzeigerWindow.Art.GUETER, null));
                }
            }
        }
        return new RoutenAnzeigerWindow.Bild(fz);
    }

    /** Datei > „Fahrzeuge importieren...“ (Anwender 09.11.2026): eigene Fahrzeugdaten aus einer Fahrzeugdatei. */
    @FXML
    private void onFahrzeugeImportieren() {
        onFahrzeugeImportierenErgebnis();
    }

    /** Fahrzeuge importieren; true, wenn eine Datei übernommen wurde. */
    private boolean onFahrzeugeImportierenErgebnis() {
        if (!document.isOpen()) {
            statusLabel.setText("Bitte zuerst eine Konfiguration öffnen.");
            return false;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Fahrzeuge importieren");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fahrzeugdaten (*.fahrzeuge.xml)", "*.fahrzeuge.xml"),
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        File dir = new File(AppSettings.getInstance().getLogistikDirectory());
        if (dir.isDirectory()) {
            fc.setInitialDirectory(dir);
        }
        File f = fc.showOpenDialog(stage());
        if (f == null) {
            return false;
        }
        try {
            int n = document.fahrzeugeImportieren(f);
            document.save();
            updateDocumentState();
            statusLabel.setText("Fahrzeuge importiert aus " + f.getName() + ": " + n
                    + " Lokomotiven/Wagen/Züge mit gleichem Namen übernommen, gespeichert in "
                    + document.getFahrzeugFile().getName() + ".");
            return true;
        } catch (Exception ex) {
            showError("Fahrzeuge konnten nicht importiert werden", ex);
            return false;
        }
    }

    /**
     * Datei > „Fahrzeuge exportieren...“ (Anwender 09.11.2026): schreibt die eigenen Angaben zu Loks, Wagen und
     * Zügen in eine wählbare Fahrzeugdatei (Vorschlag im Logistik-Ordner), z. B. für eine andere Anlage.
     */
    @FXML
    private void onFahrzeugeExportieren() {
        if (!document.isOpen()) {
            statusLabel.setText("Bitte zuerst eine Konfiguration öffnen.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Fahrzeuge exportieren");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fahrzeugdaten (*.fahrzeuge.xml)", "*.fahrzeuge.xml"));
        File dir = new File(AppSettings.getInstance().getLogistikDirectory());
        if (dir.isDirectory()) {
            fc.setInitialDirectory(dir);
        }
        String basis = document.getLogistikFile() != null ? stripExtension(document.getLogistikFile().getName()) : "Fahrzeuge";
        fc.setInitialFileName(basis + "-Export.fahrzeuge.xml");
        File f = fc.showSaveDialog(stage());
        if (f == null) {
            return;
        }
        if (!f.getName().toLowerCase().endsWith(".fahrzeuge.xml")) {
            f = new File(f.getParentFile(), f.getName().replaceAll("(?i)\\.xml$", "") + ".fahrzeuge.xml");
        }
        try {
            document.fahrzeugeExportieren(f);
            statusLabel.setText("Fahrzeuge exportiert: " + f.getAbsolutePath());
        } catch (Exception ex) {
            showError("Fahrzeuge konnten nicht exportiert werden", ex);
        }
    }

    /** Bearbeiten > Kopieren: das zuletzt angeklickte Symbol im Fenster „Anlage bearbeiten“. */
    @FXML
    private void onKopieren() {
        AnlagenCanvas c = AnlagenCanvas.offenerEditor();
        if (c == null) {
            statusLabel.setText("Kopieren geht im Fenster „Anlage bearbeiten“: Symbol anklicken, dann Kopieren.");
        } else if (!c.kopiereGewaehltes()) {
            statusLabel.setText("Bitte zuerst im Fenster „Anlage bearbeiten“ ein Symbol anklicken.");
        } else {
            statusLabel.setText("Symbol kopiert.");
        }
    }

    /** Bearbeiten > Einfügen: Symbol aus der Zwischenablage in der Mitte der Sicht des Editors. */
    @FXML
    private void onEinfuegen() {
        AnlagenCanvas c = AnlagenCanvas.offenerEditor();
        if (c == null) {
            statusLabel.setText("Einfügen geht im Fenster „Anlage bearbeiten“.");
        } else if (!c.fuegeEinInMitte()) {
            statusLabel.setText("In der Zwischenablage ist kein kopiertes Symbol.");
        }
    }

    /** Datei > Verknüpfte .tcdz ändern: andere Anlagendatei wählen, auf Wunsch gleich abgleichen. */
    @FXML
    private void onTcdzVerknuepfen() {
        if (!requireOpenDocument()) {
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("iTrain-Anlagendatei verknüpfen");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("iTrain-Anlage", "*.tcdz", "*.tcd"));
        File bisher = document.getSnapshot().sourceFile();
        if (bisher != null && bisher.getParentFile() != null && bisher.getParentFile().isDirectory()) {
            fc.setInitialDirectory(bisher.getParentFile());
            fc.setInitialFileName(bisher.getName());
        }
        File neu = fc.showOpenDialog(stage());
        if (neu == null) {
            return;
        }
        document.setTcdz(neu);
        zeigeDateinamen();
        ButtonType jetzt = new ButtonType("Jetzt abgleichen", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType spaeter = new ButtonType("Später", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Verknüpft mit " + neu.getAbsolutePath()
                + ".\n\nDaten aus dieser Datei jetzt übernehmen?", spaeter, jetzt);
        a.initOwner(stage());
        a.setTitle("iTrain-Logistik");
        a.setHeaderText("Verknüpfte .tcdz geändert");
        if (a.showAndWait().orElse(spaeter) == jetzt) {
            onAbgleichen();
        }
    }

    /** Fehlt die Fahrzeugdatei einer Konfiguration, darauf hinweisen (Anwender 09.11.2026). */
    private void fahrzeugdateiMelden() {
        if (document.isOpen() && document.isFahrzeugdateiFehlte()) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Die Fahrzeugdatei dieser Konfiguration wurde nicht gefunden:\n"
                    + document.getFahrzeugFile().getAbsolutePath() + "\n\nLokomotiven, Wagen und Züge kommen jetzt ohne "
                    + "eigene Angaben aus der iTrain-Datei. Mit Datei > „Fahrzeuge importieren...“ lassen sich die "
                    + "Angaben aus einer anderen Fahrzeugdatei holen.");
            a.initOwner(stage());
            a.setTitle("iTrain-Logistik");
            a.setHeaderText("Fahrzeugdaten fehlen");
            a.show();
        }
    }

    private void updateDocumentState() {
        boolean open = document.isOpen();
        saveMenuItem.setDisable(!open);
        closeFileMenuItem.setDisable(!open);
        loadBackupMenuItem.setDisable(!open);
        zeigeDateinamen();
        startImage.setVisible(!open);
        kern.setAnlage(open ? document.getAnlage() : null);
        refreshUebersicht();
        // Der Simulator arbeitet immer mit der geöffneten Datei: bei jedem Wechsel neu öffnen.
        SimulatorWindow.schliesseStill();
        if (open && simulatorMenuItem != null && simulatorMenuItem.isSelected()) {
            Platform.runLater(this::oeffneSimulator);
        }
        xtcNeuStarten();
        zeigeQuelle();
    }

    // ------------------------------------------------------------------ Verbindung zu iTrain

    /**
     * Start (Anwender 14.10.2026): läuft iTrain nicht, erscheint "iTrain noch nicht gestartet, Logistik ist auf
     * Pause" mit "Weiter", danach ist der Austausch angehalten; läuft iTrain, startet die Logistik direkt.
     * Gesucht wird auf diesem Rechner bzw. am iTrain-Server (Voreinstellungen > iTrain).
     */
    private void pruefeItrainBeimStart() {
        AppSettings st = AppSettings.getInstance();
        if (AppSettings.QUELLE_SIMULATOR.equals(st.getTriggerQuelle())) {
            // Mit dem Simulator keine Startprüfung (Anwender 14.10.2026); das Symbol wird trotzdem gepflegt.
            itrainStatus = ItrainErkennung.pruefe(st.getItrainRechner(), st.getItrainPort());
            zeigeItrainVerbindung();
            starteItrainUeberwachung();
            return;
        }
        pruefeItrain();
    }

    /**
     * Prüft, ob iTrain läuft; wenn nicht: "iTrain noch nicht gestartet, Logistik ist auf Pause" mit "Weiter",
     * danach Pause; sonst läuft die Logistik. Beim Start (ohne Simulator), beim Beenden des Simulators und beim
     * Umschalten auf iTrain (Anwender 14.10.2026).
     */
    /** Weg zum Server-Modus in iTrain 6 (deutsche Oberfläche, itrain.jar 6.0.4; Handbuch Kapitel Netzwerk). */
    private static final String SERVER_WEG = "iTrain → Voreinstellungen → Modus → Server";
    private static final String SERVER_HINWEIS = "iTrain läuft, aber die XTC-Schnittstelle antwortet nicht. "
            + "Ohne XTC kommen keine Zugereignisse online an.\n\nPrüfen: Einstellungen → Verbindung zu iTrain "
            + "(Adresse und XTC-Port, Vorgabe 7128); läuft iTrain auf einem anderen Rechner, muss dessen "
            + "Firewall den Port eingehend erlauben.";

    private void pruefeItrain() {
        if (beendetWird) {
            return;
        }
        AppSettings st = AppSettings.getInstance();
        itrainStatus = ItrainErkennung.pruefe(st.getItrainRechner(), st.getItrainPort());
        zeigeItrainVerbindung();
        starteItrainUeberwachung();
        ButtonType weiter = new ButtonType("Weiter", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        if (itrainStatus.laeuft()) {
            kern.setAngehalten(false);
            zeigeAustausch();
            statusLabel.setText(itrainStatus.wie() + " – Logistik gestartet.");
            if (!itrainStatus.server()) {
                // iTrain läuft, aber XTC antwortet nicht: ohne XTC kommen keine Zugereignisse an.
                Alert s = new Alert(Alert.AlertType.INFORMATION, SERVER_HINWEIS, weiter);
                s.initOwner(stage());
                s.setTitle("iTrain-Logistik");
                s.setHeaderText("XTC antwortet nicht");
                s.showAndWait();
            }
            return;
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION, "iTrain wurde nicht gefunden, Logistik ist auf Pause.\n\n"
                + "Läuft iTrain bereits, bitte Adresse und XTC-Port unter Einstellungen → Verbindung zu iTrain "
                + "prüfen (dort auch „Verbindung prüfen“).", weiter);
        a.setHeaderText("iTrain noch nicht gestartet");
        a.initOwner(stage());
        a.setTitle("iTrain-Logistik");
        a.showAndWait();
        kern.setAngehalten(true);
        zeigeAustausch();
        statusLabel.setText(itrainStatus.wie() + " – Logistik ist auf Pause.");
    }

    /**
     * Prüft alle 10 Sekunden im Hintergrund erneut, ob iTrain läuft (Anwender 14.10.2026), und schaltet das
     * Verbindungssymbol um. Die Pause wird dabei nicht verändert, die bedient der Anwender.
     */
    private void starteItrainUeberwachung() {
        if (itrainUeberwachung != null) {
            return;
        }
        itrainUeberwachung = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(10), e -> pruefeItrainImHintergrund()));
        itrainUeberwachung.setCycleCount(javafx.animation.Animation.INDEFINITE);
        itrainUeberwachung.play();
    }

    private void pruefeItrainImHintergrund() {
        if (!itrainPruefungLaeuft.compareAndSet(false, true)) {
            return;
        }
        String rechner = AppSettings.getInstance().getItrainRechner();
        int port = AppSettings.getInstance().getItrainPort();
        // Stehen die XTC-Verbindungen der Züge, läuft iTrain: keine zusätzliche Probe (jede Probe ist eine
        // kurze XTC-Verbindung mehr, die iTrain anzeigt).
        if (xtc != null && xtc.verbundeneZuege() > 0) {
            itrainPruefungLaeuft.set(false);
            String text = (xtc.getVersion().isEmpty() ? "iTrain" : xtc.getVersion()) + " verbunden über XTC unter "
                    + xtc.getRechner() + ":" + xtc.getPort();
            if (itrainStatus == null || !text.equals(itrainStatus.wie())) {
                itrainStatus = new ItrainErkennung.Ergebnis(true, text, true);
                itrainOnline = true;
                zeigeItrainVerbindung();
            }
            return;
        }
        Thread.ofVirtual().name("iTrain-Pruefung").start(() -> {
            ItrainErkennung.Ergebnis r;
            try {
                r = ItrainErkennung.pruefe(rechner, port);
            } catch (RuntimeException ex) {
                r = new ItrainErkennung.Ergebnis(false, "iTrain-Prüfung fehlgeschlagen: " + ex.getMessage());
            }
            ItrainErkennung.Ergebnis ergebnis = r;
            Platform.runLater(() -> {
                itrainPruefungLaeuft.set(false);
                boolean vorher = itrainStatus != null && itrainStatus.laeuft();
                boolean textAnders = itrainStatus == null || !itrainStatus.wie().equals(ergebnis.wie());
                itrainStatus = ergebnis;
                if (textAnders) {
                    zeigeItrainVerbindung();
                }
                if (vorher != ergebnis.laeuft()) {
                    statusLabel.setText(ergebnis.laeuft() ? ergebnis.wie() + "." : "iTrain nicht mehr gefunden.");
                }
            });
        });
    }

    /**
     * Verbindungssymbol rechts in der Statusleiste: zwei verbundene Rechner, grün wenn iTrain gefunden, sonst
     * grau; solange keine Online-Übermittlung läuft, ein rotes Kreuz darüber.
     */
    private void zeigeItrainVerbindung() {
        if (itrainSymbol == null) {
            return;
        }
        boolean gefunden = itrainStatus != null && itrainStatus.laeuft();
        Color farbe = gefunden ? Color.web("#2e9e3e") : Color.GRAY;
        javafx.scene.shape.Rectangle links = new javafx.scene.shape.Rectangle(0, 3, 7, 6);
        javafx.scene.shape.Rectangle rechts = new javafx.scene.shape.Rectangle(15, 3, 7, 6);
        for (javafx.scene.shape.Rectangle r : List.of(links, rechts)) {
            r.setFill(Color.TRANSPARENT);
            r.setStroke(farbe);
            r.setStrokeWidth(1.6);
        }
        javafx.scene.shape.Line kabel = new javafx.scene.shape.Line(7, 6, 15, 6);
        kabel.setStroke(farbe);
        kabel.setStrokeWidth(1.6);
        javafx.scene.shape.Line fussL = new javafx.scene.shape.Line(2, 11, 5, 11);
        javafx.scene.shape.Line fussR = new javafx.scene.shape.Line(17, 11, 20, 11);
        fussL.setStroke(farbe);
        fussR.setStroke(farbe);
        javafx.scene.Group bild = new javafx.scene.Group(links, rechts, kabel, fussL, fussR);
        if (!itrainOnline) {
            javafx.scene.shape.Line x1 = new javafx.scene.shape.Line(3, 0, 19, 13);
            javafx.scene.shape.Line x2 = new javafx.scene.shape.Line(19, 0, 3, 13);
            for (javafx.scene.shape.Line x : List.of(x1, x2)) {
                x.setStroke(Color.web("#d03030"));
                x.setStrokeWidth(2.4);
            }
            bild.getChildren().addAll(x1, x2);
        }
        itrainSymbol.getChildren().setAll(bild);
        String text = (itrainStatus == null ? "iTrain wird gesucht …" : itrainStatus.wie())
                + (xtc != null && xtc.verbundeneZuege() > 0 ? "\nZugereignisse kommen online über XTC an ("
                        + xtc.verbundeneZuege() + " Züge verbunden)"
                : "\nNoch keine Zugverbindung über XTC");
        javafx.scene.control.Tooltip.install(itrainSymbol, Symbole.tip(text));
    }

    // ------------------------------------------------------------------ Quelle der Zugereignisse

    /** Menü Einstellungen > Simulator: schließt iTrain aus, öffnet das Simulatorfenster. */
    @FXML
    private void onQuelleSimulator() {
        if (simulatorMenuItem.isSelected()) {
            itrainMenuItem.setSelected(false);
            AppSettings.getInstance().setTriggerQuelle(AppSettings.QUELLE_SIMULATOR);
            xtcNeuStarten();
            oeffneSimulator();
        } else {
            AppSettings.getInstance().setTriggerQuelle("");
            SimulatorWindow.schliesseStill();
            zeigeQuelle();
            Platform.runLater(this::pruefeItrain); // Simulator beendet: jetzt auf iTrain prüfen
            return;
        }
        zeigeQuelle();
    }

    /** Menü Einstellungen > iTrain: schließt den Simulator aus; die Anbindung an iTrain folgt später. */
    @FXML
    private void onQuelleItrain() {
        if (itrainMenuItem.isSelected()) {
            simulatorMenuItem.setSelected(false);
            SimulatorWindow.schliesseStill();
            AppSettings.getInstance().setTriggerQuelle(AppSettings.QUELLE_ITRAIN);
            xtcNeuStarten();
            if (AppSettings.getInstance().isXtcAktiv()) {
                statusLabel.setText("iTrain gewählt: Zugereignisse online über XTC (" + AppSettings.getInstance()
                        .getItrainRechner() + ":" + AppSettings.getInstance().getXtcPort()
                        + "). Strg + Klick auf die Quelle zeigt Rx/Tx.");
                zeigeQuelle();
                Platform.runLater(this::pruefeItrain);
                return;
            }
        } else {
            AppSettings.getInstance().setTriggerQuelle("");
            xtcNeuStarten();
        }
        zeigeQuelle();
    }

    private void oeffneSimulator() {
        if (!document.isOpen()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Es ist keine Datei geöffnet. Der Simulator arbeitet immer mit der Datei, die in "
                            + "iTrain-Logistik geöffnet ist. Bitte zuerst eine Datei öffnen; danach öffnet sich "
                            + "der Simulator von selbst.");
            a.initOwner(stage());
            a.setHeaderText("Simulator");
            a.showAndWait();
            return;
        }
        SimulatorWindow.zeige(stage(), document, document.getAnlage(), this::simulatorTrigger, () -> {
            // Fenster vom Anwender geschlossen: keine Quelle mehr gewählt, jetzt auf iTrain prüfen.
            simulatorMenuItem.setSelected(false);
            AppSettings.getInstance().setTriggerQuelle("");
            zeigeQuelle();
            Platform.runLater(this::pruefeItrain);
        });
        zeigeQuelle();
    }

    /**
     * Zugereignis aus iTrain (XTC, Anwender 09.11.2026: einziger Weg). Ist der
     * Simulator als Quelle gewählt, bleibt es unberücksichtigt, damit sich beide nicht vermischen; sonst geht es an
     * den Kern. Das erste Ereignis nimmt das rote Kreuz vom Verbindungssymbol.
     */
    private void itrainEreignis(SimulatorTrigger trigger, String weg) {
        if (!document.isOpen()
                || AppSettings.QUELLE_SIMULATOR.equals(AppSettings.getInstance().getTriggerQuelle())) {
            return;
        }
        if (kern.verarbeite(trigger)) {
            triggerLog.hinzufuegen(trigger, weg);
            hinweisAnderes(trigger);
            if (!itrainOnline) {
                itrainOnline = true;
                zeigeItrainVerbindung();
                zeigeQuelle();
            }
            if (uebersichtReiter != null) {
                uebersichtReiter.aktualisiere();
            }
        }
    }

    /**
     * Hält ein Zug an Gleisen, die nur zu Stationen der Art „Anderes“ gehören, steigt dort niemand ein oder aus
     * (Anwender 22.10.2026: „trotz Warten niemand“). Die Statusleiste sagt dann, woran es liegt.
     */
    private void hinweisAnderes(SimulatorTrigger t) {
        if (t.aktion() != SimulatorAktion.WARTEN || t.block() == null) {
            return;
        }
        List<com.example.itrain_logistik.kern.Anlage.GleisTreffer> treffer = document.getAnlage().gleiseFuerBlock(t.block());
        if (!treffer.isEmpty() && treffer.stream().allMatch(
                g -> g.station().getArt() == com.example.itrain_logistik.kern.ObjektArt.ANDERES)) {
            statusLabel.setText("„" + t.zug() + "“ hält in „" + treffer.get(0).station().getAnzeigeName()
                    + "“: Art „Anderes“, daher kein Ein- und Aussteigen. Im Stationsdialog die Art wählen "
                    + "(z. B. Bahnhof).");
        }
    }

    /** Trigger aus dem eingebauten Simulator: direkt an den Kern, ins Protokoll und auf die Übersicht. */
    private void simulatorTrigger(SimulatorTrigger trigger) {
        if (!document.isOpen()) {
            return;
        }
        XtcMonitorWindow.hinzufuegen(new XtcVerbindung.Zeile(System.currentTimeMillis(), false, trigger.zug(),
                "Simulator: " + trigger.aktion().getLabel() + " " + trigger.block()
                        + (trigger.richtung() == null || trigger.richtung().isBlank() ? "" : " → " + trigger.richtung())));
        if (kern.verarbeite(trigger)) {
            triggerLog.hinzufuegen(trigger, "Simulator");
            hinweisAnderes(trigger);
            if (uebersichtReiter != null) {
                uebersichtReiter.aktualisiere();
            }
        }
    }

    // ------------------------------------------------------------------ Online-Verbindung XTC (Anwender 08.11.2026)

    /** Startet bzw. stoppt die Browser-Ansicht nach den Einstellungen (Anwender 09.11.2026). */
    private void webansichtStarten() {
        AppSettings st = AppSettings.getInstance();
        zuganzeigerWebStarten();
        if (!st.isWebAktiv()) {
            Webansicht.stoppe();
            return;
        }
        try {
            Webansicht.starte(st.getWebPort());
        } catch (java.io.IOException ex) {
            statusLabel.setText("Browser Logistik konnte Port " + st.getWebPort() + " nicht öffnen (" + ex.getMessage()
                    + "). Funktionen → Logistik auf anderem Gerät: anderen Port wählen.");
        }
    }

    /** Menü Einstellungen > „Browser-Ansicht...“: an/aus, Port, Adressen der Module zum Öffnen. */
    @FXML
    private void onBrowserAnsicht() {
        AppSettings st = AppSettings.getInstance();
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage());
        d.setTitle("Logistik auf anderem Gerät");
        d.setHeaderText("Anlage und Module im Browser zeigen (nur lesend, im Heimnetz)");
        DialogKnoepfe.setze(d.getDialogPane());
        javafx.scene.control.CheckBox an = new javafx.scene.control.CheckBox("Browser Logistik eingeschaltet");
        an.setSelected(st.isWebAktiv());
        javafx.scene.control.Spinner<Integer> port = new javafx.scene.control.Spinner<>(1, 65535, st.getWebPort());
        port.setEditable(true);
        javafx.scene.layout.VBox links = new javafx.scene.layout.VBox(4);
        // QR-Code zum Abfotografieren mit dem Handy (Anwender 09.11.2026); „QR“ hinter jeder Adresse zeigt ihren Code
        javafx.scene.image.ImageView qr = new javafx.scene.image.ImageView();
        Label qrText = new Label();
        qrText.setWrapText(true);
        qrText.setMaxWidth(220);
        String[] qrTitel = {"Übersicht"};
        java.util.function.Consumer<String> zeigeQr = url -> {
            qr.setImage(qrBild(url, 220));
            qrText.setText(url);
        };
        java.util.function.BiConsumer<String, String> link = (text, url) -> {
            javafx.scene.control.Hyperlink h = new javafx.scene.control.Hyperlink(text + "  –  " + url);
            h.setOnAction(e -> getHostServicesSicher(url));
            javafx.scene.control.Button q = new javafx.scene.control.Button("QR");
            q.setOnAction(e -> {
                qrTitel[0] = text;
                zeigeQr.accept(url);
            });
            javafx.scene.layout.HBox zeile = new javafx.scene.layout.HBox(6, q, h);
            zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            links.getChildren().add(zeile);
        };
        String basis = "http://" + Webansicht.eigeneAdresse() + ":" + st.getWebPort();
        link.accept("Übersicht", basis + "/");
        link.accept("Ganze Anlage", basis + "/modul/alle");
        if (document.isOpen()) {
            for (com.example.itrain_logistik.kern.Modul m : document.getAnlage().module()) {
                if (m.isImBrowser()) {
                    link.accept(m.getAnzeigeName(), basis + "/modul/" + Webansicht.pfad(m));
                }
            }
        }
        // Modul-Anzeiger je Modul und je Block (Anwender 11.11.2026), QR zum Ausdrucken am Block
        if (document.isOpen()) {
            ModulAnzeiger.aktualisiere(document.getAnlage(), zug -> xtc != null ? xtc.info(zug) : null);
            links.getChildren().add(new Label("Modul-Anzeiger:"));
            for (ModulAnzeiger.Bereich b : ModulAnzeiger.bereiche().values()) {
                link.accept(Uebersetzung.tr("Modul-Anzeiger") + " " + b.titel(), basis + "/anzeiger/" + b.schluessel());
                for (String bl : b.bloecke()) {
                    link.accept(b.titel() + " · " + bl, basis + "/anzeiger/" + b.schluessel() + "/"
                            + java.net.URLEncoder.encode(bl, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
                }
            }
        }
        String rechner = Webansicht.rechnername();
        if (rechner != null) {
            String n = rechner.toLowerCase(java.util.Locale.ROOT);
            links.getChildren().add(new Label("Über den Rechnernamen (geht, wenn Router bzw. Gerät den Namen kennt):"));
            link.accept("Rechnername", "http://" + n + ":" + st.getWebPort() + "/");
            link.accept("Rechnername (mDNS)", "http://" + n + ".local:" + st.getWebPort() + "/");
        }
        Label hinweis = new Label("Auf dem anderen Rechner, Handy oder Tablet diese Adresse im Browser öffnen. Die "
                + "Windows-Firewall dieses Rechners muss den Port eingehend erlauben; beim ersten Start fragt Windows "
                + "meist selbst nach. Module legst du in „Anlage bearbeiten“ an (Palette „Modul“).");
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(520);
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(an, 0, 0, 2, 1);
        g.add(new Label("Port:"), 0, 1);
        g.add(port, 1, 1);
        zeigeQr.accept(basis + "/");
        // QR-Code drucken (Anwender 11.11.2026): Aushang an der Ausstellungsanlage
        javafx.scene.control.Button qrDrucken = new javafx.scene.control.Button("Drucken…");
        pastell(qrDrucken, "#e2d9f3");
        qrDrucken.setOnAction(e -> QrDruck.drucken(stage(), d.getTitle().replace(" auf anderem Gerät", ""),
                qrTitel[0], qrText.getText()));
        javafx.scene.layout.VBox qrBox = new javafx.scene.layout.VBox(4, qr, qrText, qrDrucken);
        qrBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        // viele Module und Blöcke: Liste rollt (Anwender 11.11.2026, Modul-Anzeiger je Block)
        javafx.scene.control.ScrollPane linksRollen = new javafx.scene.control.ScrollPane(links);
        linksRollen.setFitToWidth(true);
        linksRollen.setPrefViewportHeight(Math.min(380, 30 * links.getChildren().size() + 10));
        linksRollen.setPrefViewportWidth(520);
        linksRollen.setStyle("-fx-background-color: transparent;");
        javafx.scene.layout.HBox mitte = new javafx.scene.layout.HBox(16, linksRollen, qrBox);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(10, g, mitte, hinweis);
        inhalt.setPadding(new javafx.geometry.Insets(10));
        d.getDialogPane().setContent(inhalt);
        if (d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != DialogKnoepfe.SPEICHERN) {
            return;
        }
        st.setWebAktiv(an.isSelected());
        st.setWebPort(port.getValue());
        webansichtStarten();
        zeigeBrowserStatus();
    }

    /** Startet bzw. stoppt den Zuganzeiger im Browser (eigener Port, Anwender 09.11.2026). */
    private void zuganzeigerWebStarten() {
        AppSettings st = AppSettings.getInstance();
        routenWebStarten();
        if (!st.isAnzeigerWebAktiv()) {
            ZuganzeigerWeb.stoppe();
            return;
        }
        try {
            ZuganzeigerWeb.starte(st.getAnzeigerWebPort());
        } catch (java.io.IOException ex) {
            statusLabel.setText("Browser Zuganzeiger konnte Port " + st.getAnzeigerWebPort() + " nicht öffnen ("
                    + ex.getMessage() + "). Funktionen → Zuganzeiger auf anderem Gerät: anderen Port wählen.");
        }
    }

    /** Startet bzw. stoppt den Routen-Anzeiger im Browser (eigener Port, Anwender 10.11.2026). */
    private void routenWebStarten() {
        AppSettings st = AppSettings.getInstance();
        if (!st.isRoutenWebAktiv()) {
            RoutenWeb.stoppe();
            return;
        }
        try {
            RoutenWeb.starte(st.getRoutenWebPort());
            RoutenWeb.aktualisiere(document.isOpen() ? routenQuelle : null);
        } catch (java.io.IOException ex) {
            statusLabel.setText("Browser Routen-Anzeiger konnte Port " + st.getRoutenWebPort() + " nicht öffnen ("
                    + ex.getMessage() + "). Funktionen → Routen-Anzeiger auf anderem Gerät: anderen Port wählen.");
        }
    }

    /** Funktionen > „Routen-Anzeiger im Browser“: öffnet die Zugauswahl im Browser dieses Rechners. */
    @FXML
    private void onRoutenImBrowser() {
        if (!RoutenWeb.laeuft()) {
            statusLabel.setText("Browser Routen-Anzeiger ist ausgeschaltet – hier einschalten.");
            onRoutenBrowser();
            return;
        }
        getHostServicesSicher("http://localhost:" + AppSettings.getInstance().getRoutenWebPort() + "/");
    }

    /** Funktionen > „Routen-Anzeiger auf anderem Gerät...“: an/aus, Port, Adressen je Zug mit QR-Code. */
    @FXML
    private void onRoutenBrowser() {
        AppSettings st = AppSettings.getInstance();
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage());
        d.setTitle("Routen-Anzeiger auf anderem Gerät");
        d.setHeaderText("Routen-Anzeiger je Zug auf Handy, Tablet oder anderem Rechner");
        DialogKnoepfe.setze(d.getDialogPane());
        javafx.scene.control.CheckBox an = new javafx.scene.control.CheckBox("Browser Routen-Anzeiger eingeschaltet");
        an.setSelected(st.isRoutenWebAktiv());
        javafx.scene.control.Spinner<Integer> port = new javafx.scene.control.Spinner<>(1, 65535, st.getRoutenWebPort());
        port.setEditable(true);
        javafx.scene.layout.VBox links = new javafx.scene.layout.VBox(4);
        javafx.scene.image.ImageView qr = new javafx.scene.image.ImageView();
        Label qrText = new Label();
        qrText.setWrapText(true);
        qrText.setMaxWidth(220);
        String[] qrTitel = {"Übersicht"};
        java.util.function.Consumer<String> zeigeQr = url -> {
            qr.setImage(qrBild(url, 220));
            qrText.setText(url);
        };
        java.util.function.BiConsumer<String, String> link = (text, url) -> {
            javafx.scene.control.Hyperlink h = new javafx.scene.control.Hyperlink(text + "  –  " + url);
            h.setOnAction(e -> getHostServicesSicher(url));
            javafx.scene.control.Button q = new javafx.scene.control.Button("QR");
            q.setOnAction(e -> {
                qrTitel[0] = text;
                zeigeQr.accept(url);
            });
            javafx.scene.layout.HBox zeile = new javafx.scene.layout.HBox(6, q, h);
            zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            links.getChildren().add(zeile);
        };
        String basis = "http://" + Webansicht.eigeneAdresse() + ":" + st.getRoutenWebPort();
        link.accept("Zug wählen", basis + "/");
        RoutenWeb.aktualisiere(document.isOpen() ? routenQuelle : null);
        for (java.util.Map.Entry<String, String> e : RoutenWeb.zugPfade().entrySet()) {
            link.accept(e.getValue(), basis + "/zug/" + e.getKey());
        }
        String rechner = Webansicht.rechnername();
        if (rechner != null) {
            String n = rechner.toLowerCase(java.util.Locale.ROOT);
            links.getChildren().add(new Label("Über den Rechnernamen (geht, wenn Router bzw. Gerät den Namen kennt):"));
            link.accept("Rechnername", "http://" + n + ":" + st.getRoutenWebPort() + "/");
            link.accept("Rechnername (mDNS)", "http://" + n + ".local:" + st.getRoutenWebPort() + "/");
        }
        Label hinweis = new Label("Eigener Port, getrennt von Browser Logistik und Browser Zuganzeiger. Jedes Gerät "
                + "zeigt einen Zug; den Ton schaltet es oben im Anzeiger selbst ein (merkt sich der Browser), "
                + "„Vollbild“ für ein Tablet. Die Windows-Firewall dieses Rechners muss den Port eingehend erlauben.");
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(520);
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(an, 0, 0, 2, 1);
        g.add(new Label("Port:"), 0, 1);
        g.add(new javafx.scene.layout.HBox(8, port, new Label("(Vorgabe " + RoutenWeb.PORT_VORGABE + ")")), 1, 1);
        zeigeQr.accept(basis + "/");
        // QR-Code drucken (Anwender 11.11.2026): Aushang an der Ausstellungsanlage
        javafx.scene.control.Button qrDrucken = new javafx.scene.control.Button("Drucken…");
        pastell(qrDrucken, "#e2d9f3");
        qrDrucken.setOnAction(e -> QrDruck.drucken(stage(), d.getTitle().replace(" auf anderem Gerät", ""),
                qrTitel[0], qrText.getText()));
        javafx.scene.layout.VBox qrBox = new javafx.scene.layout.VBox(4, qr, qrText, qrDrucken);
        qrBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        javafx.scene.control.ScrollPane linksRollen = new javafx.scene.control.ScrollPane(links);
        linksRollen.setFitToWidth(true);
        linksRollen.setPrefViewportHeight(Math.min(320, 30.0 * links.getChildren().size() + 10));
        linksRollen.setPrefViewportWidth(470);
        javafx.scene.layout.HBox mitte = new javafx.scene.layout.HBox(16, linksRollen, qrBox);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(10, g, mitte, hinweis);
        inhalt.setPadding(new javafx.geometry.Insets(10));
        d.getDialogPane().setContent(inhalt);
        Fenster.merken(d, "routenBrowser");
        if (d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != DialogKnoepfe.SPEICHERN) {
            return;
        }
        if (an.isSelected() && ((st.isWebAktiv() && port.getValue() == st.getWebPort())
                || (st.isAnzeigerWebAktiv() && port.getValue() == st.getAnzeigerWebPort()))) {
            statusLabel.setText("Port " + port.getValue() + " ist schon vergeben – bitte einen anderen wählen.");
            return;
        }
        st.setRoutenWebAktiv(an.isSelected());
        st.setRoutenWebPort(port.getValue());
        routenWebStarten();
        zeigeBrowserStatus();
    }

    /** Menü Einstellungen > „Zuganzeiger im Browser...“: an/aus, Port, Adressen mit QR-Code. */
    @FXML
    private void onZuganzeigerBrowser() {
        AppSettings st = AppSettings.getInstance();
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage());
        d.setTitle("Zuganzeiger auf anderem Gerät");
        d.setHeaderText("Zuganzeiger Personen- und Güterverkehr auf Handy, Tablet oder anderem Rechner");
        DialogKnoepfe.setze(d.getDialogPane());
        javafx.scene.control.CheckBox an = new javafx.scene.control.CheckBox("Browser Zuganzeiger eingeschaltet");
        an.setSelected(st.isAnzeigerWebAktiv());
        javafx.scene.control.Spinner<Integer> port = new javafx.scene.control.Spinner<>(1, 65535,
                st.getAnzeigerWebPort());
        port.setEditable(true);
        javafx.scene.layout.VBox links = new javafx.scene.layout.VBox(4);
        javafx.scene.image.ImageView qr = new javafx.scene.image.ImageView();
        Label qrText = new Label();
        qrText.setWrapText(true);
        qrText.setMaxWidth(220);
        String[] qrTitel = {"Übersicht"};
        java.util.function.Consumer<String> zeigeQr = url -> {
            qr.setImage(qrBild(url, 220));
            qrText.setText(url);
        };
        java.util.function.BiConsumer<String, String> link = (text, url) -> {
            javafx.scene.control.Hyperlink h = new javafx.scene.control.Hyperlink(text + "  –  " + url);
            h.setOnAction(e -> getHostServicesSicher(url));
            javafx.scene.control.Button q = new javafx.scene.control.Button("QR");
            q.setOnAction(e -> {
                qrTitel[0] = text;
                zeigeQr.accept(url);
            });
            javafx.scene.layout.HBox zeile = new javafx.scene.layout.HBox(6, q, h);
            zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            links.getChildren().add(zeile);
        };
        String basis = "http://" + Webansicht.eigeneAdresse() + ":" + st.getAnzeigerWebPort();
        link.accept("Auswahl", basis + "/");
        link.accept("Personenverkehr", basis + "/personen");
        link.accept("Güterverkehr", basis + "/gueter");
        // je Bahnhof (Anwender 09.11.2026)
        for (java.util.Map.Entry<String, String> e : ZuganzeigerWeb.bahnhofPfade().entrySet()) {
            link.accept(e.getValue() + " Personen", basis + "/personen/" + e.getKey());
            link.accept(e.getValue() + " Güter", basis + "/gueter/" + e.getKey());
        }
        String rechner = Webansicht.rechnername();
        if (rechner != null) {
            String n = rechner.toLowerCase(java.util.Locale.ROOT);
            links.getChildren().add(new Label("Über den Rechnernamen (geht, wenn Router bzw. Gerät den Namen kennt):"));
            link.accept("Rechnername", "http://" + n + ":" + st.getAnzeigerWebPort() + "/");
            link.accept("Rechnername (mDNS)", "http://" + n + ".local:" + st.getAnzeigerWebPort() + "/");
        }
        Label hinweis = new Label("Eigener Port, getrennt von Browser Logistik. Tempo, Zoom und „Geeignete Züge "
                + "vorschlagen“ stellt jedes Gerät unten im Anzeiger selbst ein (merkt sich der Browser), „Vollbild“ "
                + "für ein Tablet als Tafel. Die Windows-Firewall dieses Rechners muss den Port eingehend erlauben.");
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(520);
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(an, 0, 0, 2, 1);
        g.add(new Label("Port:"), 0, 1);
        g.add(new javafx.scene.layout.HBox(8, port, new Label("(Vorgabe " + ZuganzeigerWeb.PORT_VORGABE
                + ", Browser Logistik " + st.getWebPort() + ")")), 1, 1);
        zeigeQr.accept(basis + "/");
        // QR-Code drucken (Anwender 11.11.2026): Aushang an der Ausstellungsanlage
        javafx.scene.control.Button qrDrucken = new javafx.scene.control.Button("Drucken…");
        pastell(qrDrucken, "#e2d9f3");
        qrDrucken.setOnAction(e -> QrDruck.drucken(stage(), d.getTitle().replace(" auf anderem Gerät", ""),
                qrTitel[0], qrText.getText()));
        javafx.scene.layout.VBox qrBox = new javafx.scene.layout.VBox(4, qr, qrText, qrDrucken);
        qrBox.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        javafx.scene.control.ScrollPane linksRollen = new javafx.scene.control.ScrollPane(links);
        linksRollen.setFitToWidth(true);
        linksRollen.setPrefViewportHeight(Math.min(320, 30.0 * links.getChildren().size() + 10));
        linksRollen.setPrefViewportWidth(470);
        javafx.scene.layout.HBox mitte = new javafx.scene.layout.HBox(16, linksRollen, qrBox);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(10, g, mitte, hinweis);
        inhalt.setPadding(new javafx.geometry.Insets(10));
        d.getDialogPane().setContent(inhalt);
        Fenster.merken(d, "zuganzeigerBrowser");
        if (d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != DialogKnoepfe.SPEICHERN) {
            return;
        }
        if (an.isSelected() && st.isWebAktiv() && port.getValue() == st.getWebPort()) {
            statusLabel.setText("Port " + port.getValue() + " gehört schon Browser Logistik – bitte einen anderen wählen.");
            return;
        }
        st.setAnzeigerWebAktiv(an.isSelected());
        st.setAnzeigerWebPort(port.getValue());
        zuganzeigerWebStarten();
        zeigeBrowserStatus();
    }

    /**
     * Ausklappmenü der Ribbon-Knöpfe „Browser Logistik“ und „Browser Zuganzeiger“ (Anwender 09.11.2026): Seiten im
     * Browser öffnen, darunter „Einstellungen…“ (Port, QR-Code).
     */
    /**
     * Menü bzw. Ribbon „Anzeiger“ (Anwender 11.11.2026): „Anzeiger im Fenster“, „Anzeiger im Browser“ mit „Auf diesem
     * Gerät“ und „Auf anderem Gerät“, darunter die Uhr; 3D-Trennstriche zwischen den Gruppen.
     */
    private void anzeigerMenue(javafx.collections.ObservableList<MenuItem> ziel) {
        AppSettings st = AppSettings.getInstance();
        java.util.function.BiFunction<String, Runnable, MenuItem> eintrag = (text, aktion) -> {
            MenuItem i = new MenuItem(text);
            i.setOnAction(e -> aktion.run());
            return i;
        };
        // im Fenster
        Menu fenster = new Menu("Anzeiger im Fenster");
        Menu jeBahnhof = new Menu("Zuganzeiger je Bahnhof");
        java.util.List<MenuItem> bf = zuganzeigerJeBahnhof();
        if (bf.isEmpty()) {
            MenuItem leer = new MenuItem("Keine Bahnhöfe");
            leer.setDisable(true);
            bf.add(leer);
        }
        jeBahnhof.getItems().setAll(bf);
        fenster.getItems().addAll(eintrag.apply("Zuganzeiger Personenverkehr", this::onOpenZuganzeiger),
                eintrag.apply("Zuganzeiger Güterverkehr", this::onOpenZuganzeigerGueter), jeBahnhof,
                eintrag.apply("Routen-Anzeiger", this::onRoutenAnzeiger),
                modulAnzeigerMenue(false));
        // im Browser, auf diesem Gerät
        Menu browser = new Menu("Anzeiger im Browser");
        Menu hier = new Menu("Auf diesem Gerät");
        String logistik = "http://localhost:" + st.getWebPort();
        String za = "http://localhost:" + st.getAnzeigerWebPort();
        String ra = "http://localhost:" + st.getRoutenWebPort();
        java.util.function.BiFunction<String, String, MenuItem> seite = (text, url) -> {
            MenuItem i = new MenuItem(text);
            boolean an = url.startsWith(za) ? ZuganzeigerWeb.laeuft() : url.startsWith(ra) ? RoutenWeb.laeuft()
                    : Webansicht.laeuft();
            i.setDisable(!an);
            i.setOnAction(e -> getHostServicesSicher(url));
            return i;
        };
        hier.getItems().addAll(seite.apply("Logistik (Übersicht)", logistik + "/"),
                seite.apply("Logistik ganze Anlage", logistik + "/modul/alle"));
        if (document.isOpen()) {
            for (com.example.itrain_logistik.kern.Modul m : document.getAnlage().module()) {
                if (m.isImBrowser()) {
                    hier.getItems().add(seite.apply("Logistik Modul „" + m.getAnzeigeName() + "“", logistik + "/modul/"
                            + Webansicht.pfad(m)));
                }
            }
        }
        hier.getItems().addAll(trenn3d(), seite.apply("Zuganzeiger Personenverkehr", za + "/personen"),
                seite.apply("Zuganzeiger Güterverkehr", za + "/gueter"), seite.apply("Zuganzeiger Auswahl", za + "/"));
        java.util.Map<String, String> pfade = ZuganzeigerWeb.bahnhofPfade();
        if (!pfade.isEmpty()) {
            Menu je = new Menu("Zuganzeiger je Bahnhof");
            pfade.forEach((pfad, name) -> {
                Menu m = new Menu(name);
                m.getItems().addAll(seite.apply("Personenverkehr", za + "/personen/" + pfad),
                        seite.apply("Güterverkehr", za + "/gueter/" + pfad));
                je.getItems().add(m);
            });
            hier.getItems().add(je);
        }
        hier.getItems().addAll(seite.apply("Routen-Anzeiger", ra + "/"),
                modulAnzeigerMenue(true), trenn3d(),
                seite.apply("Uhr", logistik + "/uhr"));
        // im Browser, auf anderem Gerät (Adressen, QR-Code, Drucken)
        Menu anderes = new Menu("Auf anderem Gerät");
        anderes.getItems().addAll(eintrag.apply("Logistik auf anderem Gerät...", this::onBrowserAnsicht),
                eintrag.apply("Zuganzeiger auf anderem Gerät...", this::onZuganzeigerBrowser),
                eintrag.apply("Routen-Anzeiger auf anderem Gerät...", this::onRoutenBrowser),
                eintrag.apply("Modul-Anzeiger auf anderem Gerät...", this::onBrowserAnsicht), trenn3d(),
                eintrag.apply("Uhr auf anderem Gerät...", this::onUhrBrowser));
        browser.getItems().addAll(hier, trenn3d(), anderes);
        ziel.setAll(fenster, trenn3d(), browser, trenn3d(), eintrag.apply("Uhr", this::onOpenUhr));
    }

    /** „Uhr auf anderem Gerät...“ (Anwender 11.11.2026): Adresse der Uhr im Browser mit QR-Code und Druck. */
    private void onUhrBrowser() {
        if (!Webansicht.laeuft()) {
            statusLabel.setText("Browser Logistik ist ausgeschaltet – hier einschalten.");
            onBrowserAnsicht();
            return;
        }
        String url = "http://" + Webansicht.eigeneAdresse() + ":" + AppSettings.getInstance().getWebPort() + "/uhr";
        javafx.scene.control.Dialog<ButtonType> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage());
        d.setTitle("Uhr auf anderem Gerät");
        d.setHeaderText("Die Modelluhr im Browser – Antippen wechselt zwischen analog und digital");
        javafx.scene.image.ImageView qr = new javafx.scene.image.ImageView(qrBild(url, 240));
        javafx.scene.control.Hyperlink h = new javafx.scene.control.Hyperlink(url);
        h.setOnAction(e -> getHostServicesSicher(url));
        javafx.scene.control.Button drucken = new javafx.scene.control.Button("Drucken…");
        pastell(drucken, "#e2d9f3");
        drucken.setOnAction(e -> QrDruck.drucken(stage(), Uebersetzung.tr("Uhr"), "", url));
        Label hinweis = new Label("Darstellung und Farben wie im Fenster der Uhr (Rechtsklick bzw. ⚙).");
        hinweis.setWrapText(true);
        javafx.scene.layout.VBox v = new javafx.scene.layout.VBox(8, h, qr, drucken, hinweis);
        v.setAlignment(javafx.geometry.Pos.CENTER);
        d.getDialogPane().setContent(v);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        d.showAndWait();
    }

    private void browserMenue(javafx.scene.control.MenuButton knopf, boolean anzeiger) {
        AppSettings st = AppSettings.getInstance();
        knopf.getItems().clear();
        boolean an = anzeiger ? ZuganzeigerWeb.laeuft() : Webansicht.laeuft();
        // die Einträge öffnen die Seiten im Browser dieses Rechners (Anwender 10.11.2026)
        String basis = "http://localhost:" + (anzeiger ? st.getAnzeigerWebPort() : st.getWebPort());
        java.util.function.BiConsumer<String, String> link = (text, url) -> {
            MenuItem i = new MenuItem(text);
            i.setDisable(!an);
            i.setOnAction(e -> getHostServicesSicher(url));
            knopf.getItems().add(i);
        };
        if (!an) {
            MenuItem aus = new MenuItem(anzeiger ? "Browser Zuganzeiger ist ausgeschaltet" : "Browser Logistik ist ausgeschaltet");
            aus.setDisable(true);
            knopf.getItems().add(aus);
        }
        if (anzeiger) {
            link.accept("Auswahl öffnen", basis + "/");
            link.accept("Personenverkehr", basis + "/personen");
            link.accept("Güterverkehr", basis + "/gueter");
            java.util.Map<String, String> bf = ZuganzeigerWeb.bahnhofPfade();
            if (!bf.isEmpty()) {
                Menu je = new Menu("Je Bahnhof");
                je.setDisable(!an);
                bf.forEach((pfad, name) -> {
                    Menu m = new Menu(name);
                    MenuItem p = new MenuItem("Personenverkehr");
                    p.setOnAction(e -> getHostServicesSicher(basis + "/personen/" + pfad));
                    MenuItem g = new MenuItem("Güterverkehr");
                    g.setOnAction(e -> getHostServicesSicher(basis + "/gueter/" + pfad));
                    m.getItems().addAll(p, g);
                    je.getItems().add(m);
                });
                knopf.getItems().add(je);
            }
        } else {
            link.accept("Übersicht öffnen", basis + "/");
            link.accept("Ganze Anlage", basis + "/modul/alle");
            if (document.isOpen()) {
                for (com.example.itrain_logistik.kern.Modul m : document.getAnlage().module()) {
                    if (m.isImBrowser()) {
                        link.accept("Modul „" + m.getAnzeigeName() + "“", basis + "/modul/" + Webansicht.pfad(m));
                    }
                }
            }
        }
        knopf.getItems().add(trenn3d());
        MenuItem einst = new MenuItem(anzeiger ? "Zuganzeiger auf anderem Gerät..." : "Logistik auf anderem Gerät...");
        einst.setOnAction(e -> {
            if (anzeiger) {
                onZuganzeigerBrowser();
            } else {
                onBrowserAnsicht();
            }
        });
        knopf.getItems().add(einst);
    }

    /** QR-Code als Bild (schwarz auf weiß, mit Rand), Fehlerkorrektur mittel. */
    static javafx.scene.image.Image qrBild(String text, int groesse) {
        com.example.itrain_logistik.qr.QrCode q = com.example.itrain_logistik.qr.QrCode.encodeText(text,
                com.example.itrain_logistik.qr.QrCode.Ecc.MEDIUM);
        int rand = 4;
        int module = q.size + 2 * rand;
        int skala = Math.max(1, groesse / module);
        int px = module * skala;
        javafx.scene.image.WritableImage bild = new javafx.scene.image.WritableImage(px, px);
        javafx.scene.image.PixelWriter w = bild.getPixelWriter();
        for (int y = 0; y < px; y++) {
            for (int x = 0; x < px; x++) {
                int mx = x / skala - rand;
                int my = y / skala - rand;
                boolean schwarz = mx >= 0 && my >= 0 && mx < q.size && my < q.size && q.getModule(mx, my);
                w.setArgb(x, y, schwarz ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bild;
    }

    private void getHostServicesSicher(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            statusLabel.setText("Browser konnte nicht geöffnet werden: " + url);
        }
    }

    /** Menü Einstellungen > „Verbindung zu iTrain…“. */
    @FXML
    private void onItrainVerbindung() {
        if (ItrainVerbindungDialog.zeige(stage())) {
            xtcNeuStarten();
            zeigeQuelle();
            Platform.runLater(this::pruefeItrain);
        }
    }

    /**
     * Startet die XTC-Verbindungen neu: nur mit geöffneter Datei, Quelle iTrain und „online über XTC“; je Zug der
     * .tcdz eine Verbindung (die Zugnamen kommen aus der Datei, Anwender 08.11.2026).
     */
    private void xtcNeuStarten() {
        xtcStoppen();
        AppSettings st = AppSettings.getInstance();
        if (!document.isOpen() || !AppSettings.QUELLE_ITRAIN.equals(st.getTriggerQuelle()) || !st.isXtcAktiv()) {
            return;
        }
        List<String> zuege = new java.util.ArrayList<>();
        for (TrainEntry t : document.getSnapshot().trains()) {
            zuege.add(t.getName());
        }
        xtc = new XtcVerbindung(st.getItrainRechner(), st.getXtcPort(), this::xtcEreignis, XtcMonitorWindow::hinzufuegen);
        // Modellzeit kommt live aus iTrain (Anwender 09.11.2026), nicht nur aus der zuletzt gespeicherten .tcdz
        xtc.setAufUhr(t -> {
            if (LaufendeUhr.angleichen(t)) {
                statusLabel.setText("Uhr nach iTrain gestellt: " + t);
            }
        });
        xtc.starte(zuege);
    }

    private void xtcStoppen() {
        if (xtc != null) {
            xtc.stoppe();
            xtc = null;
        }
    }

    /**
     * Ereignis aus XTC an den Kern. Richtung: iTrain meldet die Fahrtrichtung im
     * Block als „next“/„prev“; „next“ = erste am Gleis eingetragene Fahrtrichtung, „prev“ = zweite (wie im
     * Simulator links/rechts; Annahme, vom Anwender an der Anlage zu prüfen).
     */
    private void xtcEreignis(XtcVerbindung.Ereignis e) {
        if (RoutenWeb.laeuft()) {
            RoutenWeb.aktualisiere(document.isOpen() ? routenQuelle : null); // neuer Block: Wege sofort
        }
        if (xtc == null || !document.isOpen()) {
            return;
        }
        String richtung = "";
        if (e.richtung() != null && !e.richtung().isBlank()) {
            for (com.example.itrain_logistik.kern.Anlage.GleisTreffer t : document.getAnlage().gleiseFuerBlock(e.block())) {
                List<String> r = t.gleis().getRichtungen();
                int i = "next".equals(e.richtung()) ? 0 : 1;
                if (r.size() > i) {
                    richtung = r.get(i);
                    break;
                }
            }
        }
        // Bedienseite der Ladegleise (Anwender 09.11.2026): von der anderen Seite muss der Zug Kopf machen
        if (e.aktion() == SimulatorAktion.EINFAHRT) {
            kopfMachen.remove(e.zug());
            for (com.example.itrain_logistik.kern.Anlage.GleisTreffer gt : document.getAnlage().gleiseFuerBlock(e.block())) {
                String seite = gt.gleis().getBedienSeite();
                if (seite != null && e.richtung() != null && !e.richtung().isBlank() && !seite.equals(e.richtung())
                        && gt.gleis().getArt() != com.example.itrain_logistik.kern.GleisArt.BAHNSTEIG) {
                    kopfMachen.add(e.zug());
                    kern.hinweis("„" + e.zug() + "“ fährt in „" + gt.gleis().getAnzeigeName() + "“ ("
                            + gt.station().getAnzeigeName() + ") von der falschen Seite ein – Zug muss Kopf machen.");
                }
            }
        } else if (e.aktion() == SimulatorAktion.ABFAHRT) {
            kopfMachen.remove(e.zug());
        }
        SimulatorTrigger t = new SimulatorTrigger("xtc-" + System.currentTimeMillis() + "-" + (++xtcZaehler),
                e.block(), e.zug(), e.aktion(), richtung, SimulatorTrigger.QUELLE_ITRAIN);
        itrainEreignis(t, "XTC");
        if (e.aktion() == SimulatorAktion.ABFAHRT) {
            // Die neue Route meldet iTrain erst kurz nach dem Ende des Halts: nach 3 s in der Statusleiste zeigen.
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(Duration.seconds(3));
            p.setOnFinished(x -> {
                XtcVerbindung.ZugInfo i = xtc != null ? xtc.info(e.zug()) : null;
                if (i != null && !i.getRoute().isEmpty()) {
                    statusLabel.setText("„" + e.zug() + "“ fährt ab aus „" + e.block() + "“ – Route „" + i.getRoute()
                            + "“" + (i.getZiel().isEmpty() ? "" : ", Ziel „" + i.getZiel() + "“") + ".");
                }
            });
            p.play();
        }
    }

    /** Statusleiste: welche Quelle gewählt ist. */
    private void zeigeQuelle() {
        if (quelleLabel == null) {
            return;
        }
        String q = AppSettings.getInstance().getTriggerQuelle();
        if (AppSettings.QUELLE_SIMULATOR.equals(q)) {
            quelleLabel.setText("Quelle: Simulator" + (SimulatorWindow.istOffen() ? "" : " (keine Datei offen)"));
        } else if (AppSettings.QUELLE_ITRAIN.equals(q)) {
            if (xtc != null) {
                quelleLabel.setText("Quelle: iTrain (XTC " + xtc.getRechner() + ", " + xtc.verbundeneZuege() + "/"
                        + xtc.zuegeGesamt() + " Züge verbunden)");
            } else {
                quelleLabel.setText("Quelle: iTrain (XTC nicht verbunden)");
            }
        } else {
            quelleLabel.setText("Keine Quelle gewählt");
        }
        aktualisiereVerbindungsAnzeige();
    }

    /**
     * Uebersicht im Hauptfenster (Konzept Abschnitt 10.1): die Anlage mit Symbolen und
     * Schildern aus dem Echtzeit-Kern, nur lesend. Ein Klick oeffnet das Detailfenster.
     * Wird nach jeder Aenderung (Datei geoeffnet/geschlossen, Editor) neu aufgebaut; die
     * Schilder frischt {@link #kernTakt()} jede Sekunde auf.
     */
    private void refreshUebersicht() {
        if (uebersichtView != null) {
            centerPane.getChildren().remove(uebersichtView);
            uebersichtView = null;
        }
        if (uebersichtReiter != null) {
            uebersichtReiter.dispose();
        }
        uebersichtCanvas = null;
        uebersichtReiter = null;
        if (!document.isOpen()) {
            return;
        }
        uebersichtReiter = new ReiterLeiste(document.getAnlage(), bereich -> {
            // Klick auf einen Kasten der Übersicht: Detailfenster und parallel das Geräusch (Anwender 09.11.2026)
            AnlagenCanvas c = new AnlagenCanvas(document.getAnlage(), kern, false,
                    o -> {
                        // Modul: der im Modul-Anzeiger gewählte Ton bei Einfahrt (Anwender 11.11.2026)
                        if (o instanceof com.example.itrain_logistik.kern.Modul m) {
                            Signalton.spielen(AppSettings.getInstance().getModulTon(Webansicht.pfad(m)));
                        }
                        Geraeusche.zumFenster(o, ObjektDetailWindow.show(stage(), o, document.getAnlage(), kern,
                                () -> document.getSnapshot().blocks(), () -> {
                                    document.markDirty();
                                    refreshUebersicht();
                                }));
                    },
                    null, null, bereich);
            c.setOnTempoGeaendert(document::markDirty);
            return c;
        }, false, document::markDirty);
        uebersichtCanvas = uebersichtReiter.getHaupt();
        uebersichtView = uebersichtReiter.getView();
        dock.setDaten(document.getAnlage(), kern, document::getSnapshot, document::markDirty);
        TabellenWindow.zugOeffnen = name -> {
            TrainsWindow.waehle(name);
            onOpenTrains();
        };
        TabellenWindow.objektOeffnen = o -> ObjektDetailWindow.show(stage(), o, document.getAnlage(), kern,
                () -> document.getSnapshot().blocks(), () -> {
                    document.markDirty();
                    refreshUebersicht();
                });
        fuelleBahnhoefe();
        centerPane.getChildren().add(uebersichtView);
    }

    private void refreshRecentFilesMenu() {
        recentFilesMenu.getItems().clear();
        List<String> recent = AppSettings.getInstance().getRecentFiles();
        if (recent.isEmpty()) {
            MenuItem empty = new MenuItem("(keine)");
            empty.setDisable(true);
            recentFilesMenu.getItems().add(empty);
            return;
        }
        for (String path : recent) {
            MenuItem item = new MenuItem(path);
            item.setOnAction(e -> openLogzFile(new File(path)));
            recentFilesMenu.getItems().add(item);
        }
    }

    private void showError(String header, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(header);
        alert.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        alert.showAndWait();
    }
}
