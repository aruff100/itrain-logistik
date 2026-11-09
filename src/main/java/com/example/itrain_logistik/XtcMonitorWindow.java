package com.example.itrain_logistik;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/**
 * Rx/Tx-Fenster der Online-Verbindung (Anwender 08.11.2026, „ähnlich wie bei import/export“): jede Zeile, die
 * iTrain-Logistik an iTrain schickt (TX) oder von dort bekommt (RX), roh mit Zeit und Zug. Mit dem Simulator als
 * Quelle stehen hier die Ereignisse des Simulators (TX, Zug, Aktion). Geöffnet mit Strg + Klick auf die Quelle in
 * der Statusleiste. Die Zeilen werden auch bei geschlossenem Fenster gesammelt (höchstens {@link #MAX}).
 */
public final class XtcMonitorWindow {

    static final int MAX = 5000;
    private static final ObservableList<XtcVerbindung.Zeile> ZEILEN = FXCollections.observableArrayList();
    private static final List<XtcVerbindung.Zeile> ZURUECK = new ArrayList<>();
    private static final ObservableList<String> ZUEGE = FXCollections.observableArrayList(ALLE());
    private static boolean angehalten;
    private static Stage stage;
    private static Label status;

    private XtcMonitorWindow() {
    }

    private static String ALLE() {
        return "Alle Züge";
    }

    /** Neue Zeile (JavaFX-Thread). */
    public static void hinzufuegen(XtcVerbindung.Zeile z) {
        if (z.zug() != null && !ZUEGE.contains(z.zug())) {
            ZUEGE.add(z.zug());
        }
        if (angehalten) {
            ZURUECK.add(z);
            if (ZURUECK.size() > MAX) {
                ZURUECK.remove(0);
            }
            zeigeStatus();
            return;
        }
        ZEILEN.add(z);
        if (ZEILEN.size() > MAX) {
            ZEILEN.remove(0, ZEILEN.size() - MAX);
        }
    }

    public static boolean istOffen() {
        return stage != null && stage.isShowing();
    }

    public static void show(Stage owner, String titel) {
        if (istOffen()) {
            stage.setTitle(titel);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle(titel);
        stage.initOwner(owner);

        ComboBox<String> zug = new ComboBox<>(ZUEGE);
        zug.setValue(ALLE());
        CheckBox bewegung = new CheckBox("Position und Tempo ausblenden");
        bewegung.setSelected(true);
        bewegung.setTooltip(new Tooltip("<position> und <speed actual> kommen mehrmals je Sekunde; "
                + "ausgeblendet bleiben Block, Warten, Route, Signal und Info übersichtlich."));
        CheckBox warten = new CheckBox("Wartezeit-Zähler ausblenden");
        warten.setTooltip(new Tooltip("<wait period passed> kommt während eines Halts jede Sekunde."));

        FilteredList<XtcVerbindung.Zeile> gefiltert = new FilteredList<>(ZEILEN);
        Runnable filter = () -> gefiltert.setPredicate(z -> {
            if (!ALLE().equals(zug.getValue()) && zug.getValue() != null && !zug.getValue().equals(z.zug())) {
                return false;
            }
            String t = z.text();
            if (bewegung.isSelected() && (t.startsWith("<position") || t.startsWith("<speed actual"))) {
                return false;
            }
            return !(warten.isSelected() && t.startsWith("<wait period"));
        });
        zug.valueProperty().addListener((o, a, b) -> filter.run());
        bewegung.selectedProperty().addListener((o, a, b) -> filter.run());
        warten.selectedProperty().addListener((o, a, b) -> filter.run());
        filter.run();

        TableView<XtcVerbindung.Zeile> table = new TableView<>(gefiltert);
        Spalten.einrichten(table, "xtc");
        SimpleDateFormat uhr = new SimpleDateFormat("HH:mm:ss.SSS");
        TableColumn<XtcVerbindung.Zeile, String> zeit = new TableColumn<>("Zeit");
        zeit.setCellValueFactory(c -> new SimpleStringProperty(uhr.format(new Date(c.getValue().zeit()))));
        zeit.setPrefWidth(95);
        TableColumn<XtcVerbindung.Zeile, String> richtung = new TableColumn<>("Rx/Tx");
        richtung.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().rx() ? "RX" : "TX"));
        richtung.setPrefWidth(50);
        richtung.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean leer) {
                super.updateItem(s, leer);
                setText(leer ? null : s);
                setStyle(leer || s == null ? "" : "-fx-font-weight: bold; -fx-text-fill: "
                        + ("RX".equals(s) ? "#2e9e3e;" : "#2f6fd0;"));
            }
        });
        TableColumn<XtcVerbindung.Zeile, String> zugSpalte = new TableColumn<>("Zug");
        zugSpalte.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().zug()));
        zugSpalte.setPrefWidth(120);
        TableColumn<XtcVerbindung.Zeile, String> inhalt = new TableColumn<>("Inhalt");
        inhalt.setCellValueFactory(c -> new SimpleStringProperty(XtcVerbindung.entities(c.getValue().text())));
        inhalt.setPrefWidth(620);
        inhalt.setStyle("-fx-font-family: 'Consolas', 'Menlo', 'DejaVu Sans Mono', monospace;");
        table.getColumns().setAll(List.of(zeit, richtung, zugSpalte, inhalt));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        KeyCodeCombination kopierenTaste = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
        table.setOnKeyPressed(e -> {
            if (kopierenTaste.match(e)) {
                kopieren(table);
                e.consume();
            }
        });
        MenuItem kopieren = new MenuItem("Kopieren");
        kopieren.setAccelerator(kopierenTaste);
        kopieren.setOnAction(e -> kopieren(table));
        table.setContextMenu(new ContextMenu(kopieren));
        // mitlaufen: ans Ende rollen, wenn die letzte Zeile sichtbar war
        gefiltert.addListener((javafx.collections.ListChangeListener<XtcVerbindung.Zeile>) c -> {
            if (!gefiltert.isEmpty() && !angehalten) {
                table.scrollTo(gefiltert.size() - 1);
            }
        });

        Button anhalten = new Button("Anhalten");
        Button weiter = new Button("Weiter");
        Button loeschen = new Button("Löschen");
        for (Button b : List.of(anhalten, weiter, loeschen)) {
            MainController.pastell(b, "#d6eaf8");
        }
        status = new Label();
        Runnable zustand = () -> {
            anhalten.setDisable(angehalten);
            weiter.setDisable(!angehalten);
            zeigeStatus();
        };
        anhalten.setOnAction(e -> {
            angehalten = true;
            zustand.run();
        });
        weiter.setOnAction(e -> {
            angehalten = false;
            ZEILEN.addAll(ZURUECK);
            ZURUECK.clear();
            if (ZEILEN.size() > MAX) {
                ZEILEN.remove(0, ZEILEN.size() - MAX);
            }
            zustand.run();
        });
        loeschen.setOnAction(e -> {
            ZEILEN.clear();
            ZURUECK.clear();
            zustand.run();
        });
        zustand.run();

        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox oben = new HBox(8, anhalten, weiter, loeschen, status, luecke, new Label("Zug:"), zug, bewegung, warten);
        oben.setAlignment(Pos.CENTER_LEFT);
        oben.setPadding(new Insets(6, 8, 6, 8));
        BorderPane root = new BorderPane(table);
        root.setTop(oben);
        Fenster.szene(stage, root, 1000, 520);
        WindowState.apply(stage, "xtcMonitor", 1000, 520);
        stage.setOnHidden(e -> {
            stage = null;
            status = null;
        });
        stage.show();
        if (!gefiltert.isEmpty()) {
            table.scrollTo(gefiltert.size() - 1);
        }
    }

    private static void zeigeStatus() {
        if (status != null) {
            status.setText(angehalten ? "Angehalten – " + ZURUECK.size() + " neue Zeile(n) warten" : "");
        }
    }

    private static void kopieren(TableView<XtcVerbindung.Zeile> table) {
        List<Integer> zeilen = new ArrayList<>(table.getSelectionModel().getSelectedIndices());
        if (zeilen.isEmpty()) {
            return;
        }
        java.util.Collections.sort(zeilen);
        StringBuilder sb = new StringBuilder();
        List<TableColumn<XtcVerbindung.Zeile, ?>> spalten = table.getVisibleLeafColumns();
        for (int i = 0; i < spalten.size(); i++) {
            sb.append(i > 0 ? "\t" : "").append(spalten.get(i).getText());
        }
        sb.append(System.lineSeparator());
        for (int z : zeilen) {
            for (int i = 0; i < spalten.size(); i++) {
                Object wert = spalten.get(i).getCellData(z);
                sb.append(i > 0 ? "\t" : "").append(wert == null ? "" : wert);
            }
            sb.append(System.lineSeparator());
        }
        ClipboardContent c = new ClipboardContent();
        c.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(c);
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
