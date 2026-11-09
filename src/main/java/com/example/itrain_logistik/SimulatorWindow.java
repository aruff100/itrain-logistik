package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * iTrain-Logistik-Simulator im Hauptprogramm (Anwender 12.10.2026, ersetzt die frühere eigenständige
 * Anwendung): arbeitet immer mit der in iTrain-Logistik geöffneten Datei. Links die Züge, rechts die Blöcke;
 * ein Zug wird per Drag&Drop auf die linke oder rechte Seite eines Blocks gezogen (gelbe Dreiecke = Einfahrt
 * von links bzw. rechts). Dann laufen nacheinander "Einfahrt", "Warten", "Abfahrt" und "Löschen", jeweils
 * so viele Sekunden, wie unter "Zeit-Vorgaben" eingestellt; je Zug protokolliert eine eigene Tabelle unten
 * die Vorgänge und verschwindet nach "Löschen". "Aufzeichnen" speichert die Drag&Drop-Vorgänge mit ihrem
 * Zeitpunkt ({@link SimulatorAblauf}); "Vorgang starten" zeigt sie zum Bearbeiten und spielt sie ab.
 * Die Trigger gehen direkt an den Echtzeit-Kern (über {@code sender}).
 */
public final class SimulatorWindow {

    private static SimulatorWindow offen;

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color GELB = Color.web("#f5c518");
    private static final SimulatorAktion[] FOLGE = {SimulatorAktion.EINFAHRT, SimulatorAktion.WARTEN,
            SimulatorAktion.ABFAHRT, SimulatorAktion.LOESCHEN};

    private final Stage stage;
    private final LogistikDocument document;
    private final Anlage anlage;
    private final Consumer<SimulatorTrigger> sender;
    private final List<String> zugNamen = new ArrayList<>();
    private final List<String> blockNamen = new ArrayList<>();
    private final ObservableList<String> bloecke = FXCollections.observableArrayList();
    private ListView<String> blockListe;
    private final VBox protokolle = new VBox(10);
    private final Label status = new Label();
    private final Map<String, Lauf> laeufe = new LinkedHashMap<>();
    /** Block -> Zug und aktueller Vorgang, für die Anzeige im Block. */
    private final Map<String, String> belegung = new LinkedHashMap<>();
    private final ToggleButton aufzeichnen = new ToggleButton("Aufzeichnen");
    private final Button stoppen = new Button("Ablauf stoppen");
    private final List<SimulatorAblauf.Schritt> aufnahme = new ArrayList<>();
    private long aufnahmeBeginn;
    private final List<PauseTransition> ablauf = new ArrayList<>();
    private long triggerNummer;

    /** Ein laufender Vorgang eines Zugs (Einfahrt bis Löschen) mit seiner Protokolltabelle. */
    private static final class Lauf {
        final String zug;
        final String block;
        final Timeline timeline = new Timeline();
        final ObservableList<String[]> zeilen = FXCollections.observableArrayList();
        VBox box;
        /** Zuletzt ausgeführter Vorgang (-1 = noch keiner). */
        int phase = -1;
        String richtung = "";

        Lauf(String zug, String block) {
            this.zug = zug;
            this.block = block;
        }
    }

    private SimulatorWindow(Stage owner, LogistikDocument document, Anlage anlage, Consumer<SimulatorTrigger> sender,
            Runnable onGeschlossen) {
        this.document = document;
        this.anlage = anlage;
        this.sender = sender;
        for (TrainEntry t : document.getSnapshot().trains()) {
            zugNamen.add(t.getName());
        }
        for (BlockEntry b : document.getSnapshot().blocks()) {
            blockNamen.add(b.getName());
        }
        bloecke.setAll(blockNamen);

        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("iTrain-Logistik-Simulator – " + (document.getLogistikFile() != null
                ? document.getLogistikFile().getName() : ""));

        BorderPane root = new BorderPane();
        root.setTop(werkzeuge());
        // Dreigeteilt (Anwender 12.10.2026): links Züge, Mitte Blöcke, rechts die Abläufe je Zug.
        protokolle.setPadding(new Insets(0, 8, 8, 8));
        ScrollPane ablaeufe = new ScrollPane(protokolle);
        ablaeufe.setFitToWidth(true);
        Label leer = new Label("Vorgänge erscheinen hier, je Zug eine Tabelle.");
        leer.setStyle("-fx-opacity: 0.7;");
        protokolle.getChildren().add(leer);
        Label ablaufTitel = new Label("Abläufe");
        ablaufTitel.setStyle("-fx-font-weight: bold;");
        VBox rechts = new VBox(6, ablaufTitel, ablaeufe);
        VBox.setVgrow(ablaeufe, Priority.ALWAYS);
        rechts.setPadding(new Insets(8, 0, 0, 0));
        ablaufTitel.setPadding(new Insets(0, 8, 0, 8));
        SplitPane mitte = new SplitPane(zugSeite(), blockSeite(), rechts);
        mitte.setDividerPositions(0.2, 0.6);
        root.setCenter(mitte);
        status.setPadding(new Insets(4, 8, 4, 8));
        status.setText("Zug von links auf die linke oder rechte Seite eines Blocks ziehen.");
        root.setBottom(status);

        Scene scene = new Scene(root, 1120, 760);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        WindowState.apply(stage, "simulator", 1120, 760);
        stage.setOnHidden(e -> {
            stoppeAlles();
            if (offen == this) {
                offen = null;
            }
            if (onGeschlossen != null) {
                onGeschlossen.run();
            }
        });
    }

    /** Öffnet das Fenster (bzw. holt es nach vorn) für die geöffnete Datei. */
    public static void zeige(Stage owner, LogistikDocument document, Anlage anlage, Consumer<SimulatorTrigger> sender,
            Runnable onGeschlossen) {
        if (offen != null) {
            offen.stage.toFront();
            return;
        }
        offen = new SimulatorWindow(owner, document, anlage, sender, onGeschlossen);
        offen.stage.show();
    }

    public static boolean istOffen() {
        return offen != null;
    }

    /** Schließt das Fenster, ohne die gewählte Quelle zu ändern (z. B. beim Wechsel der Datei). */
    public static void schliesseStill() {
        if (offen != null) {
            SimulatorWindow w = offen;
            offen = null;
            w.stage.setOnHidden(null);
            w.stoppeAlles();
            w.stage.close();
        }
    }

    // ------------------------------------------------------------------ Aufbau

    private Node werkzeuge() {
        String[] namen = {"Einfahrt", "Warten", "Abfahrt", "Löschen"};
        HBox zeiten = new HBox(6);
        zeiten.setAlignment(Pos.CENTER_LEFT);
        Label titel = new Label("Zeit-Vorgaben:");
        titel.setStyle("-fx-font-weight: bold;");
        zeiten.getChildren().add(titel);
        for (int i = 0; i < 4; i++) {
            int vorgang = i;
            Spinner<Integer> sp = new Spinner<>(1, 3600, AppSettings.getInstance().getSimulatorSekunden(i));
            sp.setEditable(true);
            sp.setPrefWidth(76);
            sp.valueProperty().addListener((o, a, b) -> {
                if (b != null) {
                    AppSettings.getInstance().setSimulatorSekunden(vorgang, b);
                }
            });
            zeiten.getChildren().addAll(new Label(namen[i]), sp, new Label("s"));
        }
        Circle punkt = new Circle(5, Color.web("#d03030"));
        aufzeichnen.setGraphic(punkt);
        aufzeichnen.setTooltip(Symbole.tip("Drag&Drop-Vorgänge mit ihrem Zeitpunkt aufzeichnen; erneuter Klick beendet und speichert"));
        aufzeichnen.selectedProperty().addListener((o, a, an) -> {
            if (an) {
                beginneAufnahme();
            } else {
                beendeAufnahme();
            }
        });
        Button starten = new Button("Vorgang starten…");
        starten.setTooltip(Symbole.tip("Aufgezeichneten Ablauf anzeigen, bearbeiten und abspielen"));
        starten.setOnAction(e -> ablaufBearbeiten());
        stoppen.setDisable(true);
        stoppen.setOnAction(e -> stoppeAblauf());
        ToolBar tb = new ToolBar(zeiten, new Separator(Orientation.VERTICAL), aufzeichnen, starten, stoppen);
        return tb;
    }

    private Node zugSeite() {
        ListView<String> liste = new ListView<>(FXCollections.observableArrayList(zugNamen));
        liste.setPlaceholder(new Label("Keine Züge in der Datei"));
        liste.setCellFactory(lv -> {
            ListCell<String> c = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(item + (laeufe.containsKey(item) ? "   (" + laeufe.get(item).block + ")" : ""));
                        Rectangle r = new Rectangle(18, 8, Color.WHITE);
                        r.setStroke(Color.web("#555555"));
                        setGraphic(r);
                    }
                }
            };
            c.setOnDragDetected(e -> {
                if (c.getItem() == null) {
                    return;
                }
                Dragboard db = c.startDragAndDrop(TransferMode.COPY);
                ClipboardContent cc = new ClipboardContent();
                cc.putString(c.getItem());
                db.setContent(cc);
                Label bild = new Label("🚆 " + c.getItem());
                bild.setStyle("-fx-background-color: #ffffff; -fx-border-color: #f5c518; -fx-padding: 3 6 3 6;");
                new Scene(new StackPane(bild));
                db.setDragView(bild.snapshot(new SnapshotParameters(), null));
                e.consume();
            });
            return c;
        });
        Label titel = new Label("Züge (auf einen Block ziehen)");
        titel.setStyle("-fx-font-weight: bold;");
        VBox v = new VBox(6, titel, liste);
        VBox.setVgrow(liste, Priority.ALWAYS);
        v.setPadding(new Insets(8));
        return v;
    }

    private Node blockSeite() {
        blockListe = new ListView<>(bloecke);
        blockListe.setPlaceholder(new Label("Keine Blöcke in der Datei"));
        blockListe.setCellFactory(lv -> new BlockZelle());
        Label titel = new Label("Blöcke (linke Hälfte = Einfahrt von links, rechte Hälfte = von rechts)");
        titel.setStyle("-fx-font-weight: bold;");
        VBox v = new VBox(6, titel, blockListe);
        VBox.setVgrow(blockListe, Priority.ALWAYS);
        v.setPadding(new Insets(8));
        return v;
    }

    /** Block wie in iTrain: Name, Gleisband, an beiden Enden ein gelbes Dreieck für die Einfahrseite. */
    private final class BlockZelle extends ListCell<String> {
        private final Label name = new Label();
        private final Label zusatz = new Label();
        private final StackPane linksZone = new StackPane();
        private final StackPane rechtsZone = new StackPane();
        private final Label richtungLinks = new Label();
        private final Label richtungRechts = new Label();
        private final Label zugLabel = new Label();
        private final VBox inhalt;

        BlockZelle() {
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            zusatz.setStyle("-fx-font-size: 10px; -fx-opacity: 0.75;");
            linksZone.getChildren().add(dreieck(true));
            rechtsZone.getChildren().add(dreieck(false));
            for (StackPane z : new StackPane[]{linksZone, rechtsZone}) {
                z.setMinSize(44, 34);
                z.setPrefSize(44, 34);
                z.setStyle(zonenStil(false));
            }
            // Gleisband als Region: passt sich der Breite an, ohne die Zelle aufzublähen.
            Region band = new Region();
            band.setStyle("-fx-background-color: #9aa3a8; -fx-border-color: #5f686d;");
            band.setMinHeight(12);
            band.setPrefHeight(12);
            band.setMaxHeight(12);
            band.setMinWidth(20);
            band.setPrefWidth(20);
            StackPane bandHalter = new StackPane(band, zugLabel);
            bandHalter.setMinWidth(40);
            bandHalter.setPrefWidth(40);
            HBox.setHgrow(bandHalter, Priority.ALWAYS);
            zugLabel.setStyle("-fx-background-color: white; -fx-border-color: #555555; -fx-padding: 0 6 0 6;"
                    + " -fx-font-size: 11px; -fx-text-fill: black;");
            HBox gleis = new HBox(4, linksZone, bandHalter, rechtsZone);
            gleis.setAlignment(Pos.CENTER);
            richtungLinks.setStyle("-fx-font-size: 10px;");
            richtungRechts.setStyle("-fx-font-size: 10px;");
            Region platz = new Region();
            HBox.setHgrow(platz, Priority.ALWAYS);
            HBox richtungen = new HBox(richtungLinks, platz, richtungRechts);
            name.setMinWidth(Region.USE_PREF_SIZE);
            zusatz.setMinWidth(0);
            HBox kopf = new HBox(8, name, zusatz);
            kopf.setAlignment(Pos.BASELINE_CENTER);
            inhalt = new VBox(2, kopf, gleis, richtungen);
            inhalt.setPadding(new Insets(4, 6, 4, 6));
            // Breite folgt der Zelle, damit das rechte Dreieck immer sichtbar bleibt.
            inhalt.setMinWidth(0);
            inhalt.prefWidthProperty().bind(widthProperty().subtract(14));
            setPrefWidth(0);
            inhalt.setStyle("-fx-background-color: #3c4246; -fx-background-radius: 4;");
            name.setStyle(name.getStyle() + " -fx-text-fill: white;");
            zusatz.setStyle(zusatz.getStyle() + " -fx-text-fill: white;");
            richtungLinks.setStyle(richtungLinks.getStyle() + " -fx-text-fill: #f5c518;");
            richtungRechts.setStyle(richtungRechts.getStyle() + " -fx-text-fill: #f5c518;");

            // Großzügiger Fang: die ganze Zelle, linke bzw. rechte Hälfte.
            setOnDragOver(e -> {
                if (getItem() != null && e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.COPY);
                    markiere(seite(e));
                }
                e.consume();
            });
            setOnDragExited(e -> markiere(null));
            setOnDragDropped(e -> {
                boolean ok = false;
                if (getItem() != null && e.getDragboard().hasString()) {
                    starte(e.getDragboard().getString(), getItem(), seite(e), true);
                    ok = true;
                }
                markiere(null);
                e.setDropCompleted(ok);
                e.consume();
            });
        }

        private String seite(DragEvent e) {
            return e.getX() < getWidth() / 2 ? SimulatorAblauf.LINKS : SimulatorAblauf.RECHTS;
        }

        private void markiere(String seite) {
            linksZone.setStyle(zonenStil(SimulatorAblauf.LINKS.equals(seite)));
            rechtsZone.setStyle(zonenStil(SimulatorAblauf.RECHTS.equals(seite)));
            inhalt.setStyle("-fx-background-color: " + (seite != null ? "#4f5a60" : "#3c4246")
                    + "; -fx-background-radius: 4;");
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(null);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item);
            zusatz.setText(stationText(item));
            String r0 = richtung(item, SimulatorAblauf.LINKS);
            String r1 = richtung(item, SimulatorAblauf.RECHTS);
            richtungLinks.setText(r0.isEmpty() ? "von links" : "von links → " + r0);
            richtungRechts.setText(r1.isEmpty() ? "von rechts" : r1 + " ← von rechts");
            String b = belegung.get(item);
            zugLabel.setText(b != null ? b : "");
            zugLabel.setVisible(b != null);
            setGraphic(inhalt);
        }
    }

    private static String zonenStil(boolean aktiv) {
        return aktiv ? "-fx-background-color: rgba(245,197,24,0.35); -fx-border-color: #f5c518; -fx-border-width: 2;"
                + " -fx-background-radius: 4; -fx-border-radius: 4;"
                : "-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2;";
    }

    /** Gelbes Dreieck: links zeigt es nach rechts (Einfahrt von links), rechts nach links. */
    private static Node dreieck(boolean nachRechts) {
        Polygon p = nachRechts ? new Polygon(0, 0, 22, 12, 0, 24) : new Polygon(22, 0, 0, 12, 22, 24);
        p.setFill(GELB);
        p.setStroke(Color.web("#8a6d00"));
        return p;
    }

    /** "Station · Bahnsteig/Ladegleis" für einen Block, sofern einem Gleis zugeordnet. */
    private String stationText(String block) {
        List<String> teile = new ArrayList<>();
        for (Anlage.GleisTreffer t : anlage.gleiseFuerBlock(block)) {
            teile.add(t.station().getAnzeigeName() + " (" + (t.gleis().getArt() == GleisArt.BAHNSTEIG
                    ? "Bahnsteig" : "Ladegleis") + ")");
        }
        return String.join(", ", teile);
    }

    /**
     * Fahrtrichtung für den Trigger: Einfahrt von links = erste am Gleis eingetragene Richtung, von rechts =
     * zweite; ohne eingetragene Richtungen leer (Bahnsteig ohne Richtungstrennung).
     */
    private String richtung(String block, String seite) {
        for (Anlage.GleisTreffer t : anlage.gleiseFuerBlock(block)) {
            Gleis g = t.gleis();
            List<String> r = g.getRichtungen();
            int i = SimulatorAblauf.LINKS.equals(seite) ? 0 : 1;
            if (r.size() > i) {
                return r.get(i);
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ Ablauf eines Zugs

    /** Startet Einfahrt → Warten → Abfahrt → Löschen für einen Zug in einem Block. */
    private void starte(String zug, String block, String seite, boolean vonHand) {
        if (!zugNamen.contains(zug) || !blockNamen.contains(block)) {
            status.setText("Unbekannt: Zug „" + zug + "“ oder Block „" + block + "“ – übersprungen.");
            return;
        }
        if (vonHand && aufzeichnen.isSelected()) {
            double t = (System.currentTimeMillis() - aufnahmeBeginn) / 1000.0;
            aufnahme.add(new SimulatorAblauf.Schritt(Math.round(t * 10) / 10.0, zug, block, seite));
            aufzeichnen.setText("Aufzeichnen (" + aufnahme.size() + ")");
        }
        Lauf alt = laeufe.remove(zug);
        if (alt != null) {
            alt.timeline.stop();
            protokolle.getChildren().remove(alt.box);
            belegung.remove(alt.block);
        }
        String richtung = richtung(block, seite);
        Lauf lauf = new Lauf(zug, block);
        lauf.box = protokollTabelle(lauf);
        protokolle.getChildren().removeIf(n -> n instanceof Label);
        protokolle.getChildren().add(lauf.box);
        laeufe.put(zug, lauf);

        double zeit = 0;
        for (int i = 0; i < FOLGE.length; i++) {
            SimulatorAktion a = FOLGE[i];
            lauf.timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(zeit),
                    e -> vorgang(lauf, a, richtung)));
            zeit += AppSettings.getInstance().getSimulatorSekunden(i);
        }
        lauf.timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(zeit), e -> beende(lauf)));
        lauf.timeline.play();
    }

    private void vorgang(Lauf lauf, SimulatorAktion a, String richtung) {
        lauf.phase = a.ordinal();
        lauf.richtung = richtung;
        String zeitStempel = LocalDateTime.now() + "#" + (++triggerNummer);
        SimulatorTrigger t = new SimulatorTrigger(zeitStempel, lauf.block, lauf.zug, a, richtung);
        if (sender != null) {
            sender.accept(t);
        }
        LocalTime modell = LaufendeUhr.aktuelleZeit();
        lauf.zeilen.add(new String[]{lauf.block, a.getLabel(), richtung, LocalTime.now().format(ZEIT),
                modell != null ? modell.format(ZEIT) : ""});
        belegung.put(lauf.block, lauf.zug + " · " + a.getLabel());
        status.setText(lauf.zug + ": " + a.getLabel() + " in " + lauf.block
                + (richtung.isEmpty() ? "" : " (Richtung " + richtung + ")"));
        blockListe.refresh();
    }

    private void beende(Lauf lauf) {
        if (laeufe.get(lauf.zug) == lauf) {
            laeufe.remove(lauf.zug);
        }
        protokolle.getChildren().remove(lauf.box);
        if ((lauf.zug + " · " + SimulatorAktion.LOESCHEN.getLabel()).equals(belegung.get(lauf.block))) {
            belegung.remove(lauf.block);
        }
        blockListe.refresh();
    }

    private VBox protokollTabelle(Lauf lauf) {
        TableView<String[]> t = new TableView<>(lauf.zeilen);
        Spalten.einrichten(t, "simulator.protokoll");
        String[] titel = {"Block", "Vorgang", "Richtung", "Zeit", "Modellzeit"};
        double[] breite = {110, 70, 80, 70, 76};
        for (int i = 0; i < titel.length; i++) {
            int k = i;
            TableColumn<String[], String> c = new TableColumn<>(titel[i]);
            c.setCellValueFactory(z -> new SimpleStringProperty(z.getValue()[k]));
            c.setPrefWidth(breite[i]);
            c.setSortable(false);
            t.getColumns().add(c);
        }
        t.setPrefHeight(150);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label(""));
        Label kopf = new Label("🚆 " + lauf.zug);
        kopf.setStyle("-fx-font-weight: bold;");
        VBox v = new VBox(3, kopf, t);
        return v;
    }

    private void stoppeAlles() {
        for (Lauf l : laeufe.values()) {
            l.timeline.stop();
            // Abgebrochener Vorgang (z. B. Fenster geschlossen): Zug nicht im Gleis stehen lassen.
            if (l.phase >= 0 && l.phase < SimulatorAktion.LOESCHEN.ordinal() && sender != null) {
                if (l.phase < SimulatorAktion.ABFAHRT.ordinal()) {
                    sender.accept(new SimulatorTrigger(LocalDateTime.now() + "#" + (++triggerNummer), l.block, l.zug,
                            SimulatorAktion.ABFAHRT, l.richtung));
                }
                sender.accept(new SimulatorTrigger(LocalDateTime.now() + "#" + (++triggerNummer), l.block, l.zug,
                        SimulatorAktion.LOESCHEN, l.richtung));
            }
        }
        laeufe.clear();
        stoppeAblauf();
    }

    // ------------------------------------------------------------------ Aufzeichnen und Abspielen

    private File ablaufDatei() {
        File f = document.getLogistikFile();
        return f != null ? SimulatorAblauf.dateiFuer(f) : null;
    }

    private void beginneAufnahme() {
        File f = ablaufDatei();
        if (f != null && f.isFile() && !SimulatorAblauf.laden(f).isEmpty()) {
            Alert frage = new Alert(Alert.AlertType.CONFIRMATION,
                    "Es gibt schon eine Aufzeichnung. Soll sie durch die neue ersetzt werden?",
                    DialogKnoepfe.ABBRUCH, ButtonType.YES);
            frage.initOwner(stage);
            frage.setHeaderText("Aufzeichnen");
            if (frage.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != ButtonType.YES) {
                aufzeichnen.setSelected(false);
                return;
            }
        }
        aufnahme.clear();
        aufnahmeBeginn = System.currentTimeMillis();
        aufzeichnen.setText("Aufzeichnen (0)");
        status.setText("Aufzeichnung läuft – jeder Drag&Drop wird mit seinem Zeitpunkt gespeichert.");
    }

    private void beendeAufnahme() {
        aufzeichnen.setText("Aufzeichnen");
        if (aufnahme.isEmpty()) {
            status.setText("Aufzeichnung beendet – nichts aufgezeichnet.");
            return;
        }
        File f = ablaufDatei();
        try {
            SimulatorAblauf.speichern(f, aufnahme);
            status.setText("Aufzeichnung gespeichert: " + aufnahme.size() + " Vorgänge (" + f.getName() + ").");
        } catch (Exception ex) {
            status.setText("Aufzeichnung konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    private void ablaufBearbeiten() {
        if (aufzeichnen.isSelected()) {
            aufzeichnen.setSelected(false);
        }
        File f = ablaufDatei();
        List<SimulatorAblauf.Schritt> schritte = SimulatorAblauf.laden(f);
        SimulatorAblaufDialog.Ergebnis erg = SimulatorAblaufDialog.zeige(stage, schritte, zugNamen, blockNamen);
        if (erg == null) {
            return;
        }
        try {
            SimulatorAblauf.speichern(f, erg.schritte());
        } catch (Exception ex) {
            status.setText("Ablauf konnte nicht gespeichert werden: " + ex.getMessage());
        }
        if (erg.starten()) {
            spieleAb(erg.schritte());
        } else {
            status.setText("Ablauf gespeichert: " + erg.schritte().size() + " Vorgänge.");
        }
    }

    private void spieleAb(List<SimulatorAblauf.Schritt> schritte) {
        stoppeAblauf();
        if (schritte.isEmpty()) {
            status.setText("Der Ablauf ist leer.");
            return;
        }
        double ende = 0;
        for (SimulatorAblauf.Schritt s : schritte) {
            PauseTransition p = new PauseTransition(Duration.seconds(s.getStart()));
            p.setOnFinished(e -> starte(s.getZug(), s.getBlock(), s.getSeite(), false));
            ablauf.add(p);
            ende = Math.max(ende, s.getStart());
        }
        PauseTransition fertig = new PauseTransition(Duration.seconds(ende + 0.1));
        fertig.setOnFinished(e -> {
            stoppen.setDisable(true);
            ablauf.clear();
            status.setText("Ablauf vollständig gestartet.");
        });
        ablauf.add(fertig);
        ablauf.forEach(PauseTransition::play);
        stoppen.setDisable(false);
        status.setText("Ablauf läuft: " + schritte.size() + " Vorgänge über " + Math.round(ende) + " s.");
    }

    private void stoppeAblauf() {
        ablauf.forEach(PauseTransition::stop);
        ablauf.clear();
        stoppen.setDisable(true);
    }
}
