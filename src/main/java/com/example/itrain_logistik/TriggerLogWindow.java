package com.example.itrain_logistik;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * "Rx/Tx"-Fenster: listet jeden vom externen iTrain-Logistik-Simulator
 * empfangenen Trigger zeile fuer zeile auf (siehe {@link TriggerLog}),
 * erreichbar per Klick auf den Verbindungs-Kreis in der Statusleiste
 * (siehe {@code MainController#onOpenTriggerLog}). Die TableView ist direkt
 * an {@link TriggerLog#getEintraege()} gebunden und aktualisiert sich daher
 * von selbst, solange das Fenster offen bleibt - anders als die uebrigen
 * Datenfenster braucht dieses deshalb kein manuelles {@code refreshIfOpen}.
 */
public final class TriggerLogWindow {

    private static Stage stage;

    private TriggerLogWindow() {
    }

    public static void show(Stage owner, TriggerLog triggerLog) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Rx/Tx");
        stage.initOwner(owner);

        TableView<TriggerLog.Eintrag> table = new TableView<>(triggerLog.getEintraege());
        Spalten.einrichten(table, "rxtx");

        TableColumn<TriggerLog.Eintrag, String> zeitColumn = new TableColumn<>("Empfangen");
        zeitColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().empfangszeit()));
        zeitColumn.setPrefWidth(100);

        TableColumn<TriggerLog.Eintrag, String> quelleColumn = new TableColumn<>("Quelle");
        quelleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().quelle()));
        quelleColumn.setPrefWidth(80);

        TableColumn<TriggerLog.Eintrag, String> zeitFeldColumn = new TableColumn<>("Zeit (Trigger)");
        zeitFeldColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().trigger().zeit()));
        zeitFeldColumn.setPrefWidth(140);

        TableColumn<TriggerLog.Eintrag, String> blockColumn = new TableColumn<>("Block");
        blockColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().trigger().block()));
        blockColumn.setPrefWidth(160);

        TableColumn<TriggerLog.Eintrag, String> zugColumn = new TableColumn<>("Zug");
        zugColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().trigger().zug()));
        zugColumn.setPrefWidth(160);

        TableColumn<TriggerLog.Eintrag, String> aktionColumn = new TableColumn<>("Aktion");
        aktionColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(cell.getValue().trigger().aktion())));
        aktionColumn.setPrefWidth(100);

        TableColumn<TriggerLog.Eintrag, String> richtungColumn = new TableColumn<>("Richtung");
        richtungColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().trigger().richtung()));
        richtungColumn.setPrefWidth(100);

        table.getColumns().setAll(java.util.List.of(zeitColumn, quelleColumn, zeitFeldColumn, blockColumn, zugColumn, aktionColumn,
                richtungColumn));

        // Mehrfachauswahl mit Umschalt/Strg; Strg+C, Strg+A und Kontextmenü kopieren als Tabulator-Text
        // (mit Spaltenköpfen, z. B. für Excel oder eine Mail) – Anwender 22.10.2026
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setOnKeyPressed(e -> {
            if (KOPIEREN.match(e)) {
                kopieren(table);
                e.consume();
            }
        });
        MenuItem kopieren = new MenuItem("Kopieren");
        kopieren.setAccelerator(KOPIEREN);
        kopieren.setOnAction(e -> kopieren(table));
        MenuItem alle = new MenuItem("Alles auswählen");
        alle.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN));
        alle.setOnAction(e -> table.getSelectionModel().selectAll());
        table.setContextMenu(new ContextMenu(kopieren, alle));

        Button anhalten = new Button("Anhalten");
        Button weiter = new Button("Weiter");
        Button loeschen = new Button("Löschen");
        Label status = new Label();
        anhalten.setTooltip(new Tooltip("Die Liste bleibt stehen, damit man in Ruhe lesen und markieren kann. "
                + "Die Ereignisse werden trotzdem verarbeitet und bei „Weiter“ nachgetragen."));
        weiter.setTooltip(new Tooltip("Liste wieder mitlaufen lassen; was beim Anhalten ankam, wird oben eingefügt."));
        loeschen.setTooltip(new Tooltip("Liste leeren (nur die Anzeige; an der Logistik ändert sich nichts)."));
        Runnable zustand = () -> {
            boolean an = triggerLog.istAngehalten();
            anhalten.setDisable(an);
            weiter.setDisable(!an);
            status.setText(an ? "Angehalten – " + triggerLog.zurueckgehaltenAnzahl() + " neue Zeile(n) warten" : "");
        };
        anhalten.setOnAction(e -> {
            triggerLog.anhalten();
            zustand.run();
        });
        weiter.setOnAction(e -> {
            triggerLog.weiter();
            zustand.run();
        });
        loeschen.setOnAction(e -> {
            triggerLog.loeschen();
            zustand.run();
        });
        zustand.run();
        // Zähler der wartenden Zeilen auffrischen, solange angehalten ist
        javafx.animation.Timeline takt = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(1), e -> zustand.run()));
        takt.setCycleCount(javafx.animation.Animation.INDEFINITE);
        takt.play();

        HBox knoepfe = new HBox(8, anhalten, weiter, loeschen, status);
        knoepfe.setAlignment(Pos.CENTER_LEFT);
        knoepfe.setPadding(new Insets(6, 8, 6, 8));
        BorderPane root = new BorderPane(table);
        root.setTop(knoepfe);

        Fenster.szene(stage, root, 760, 480);
        WindowState.apply(stage, "triggerLog", 760, 480);
        stage.setOnHidden(e -> {
            takt.stop();
            stage = null;
        });
        stage.show();
    }

    private static final KeyCodeCombination KOPIEREN = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    /** Markierte Zeilen (in Anzeigereihenfolge) mit Spaltenköpfen als Tabulator-Text in die Zwischenablage. */
    private static void kopieren(TableView<TriggerLog.Eintrag> table) {
        java.util.List<Integer> zeilen = new java.util.ArrayList<>(table.getSelectionModel().getSelectedIndices());
        if (zeilen.isEmpty()) {
            return;
        }
        java.util.Collections.sort(zeilen);
        StringBuilder sb = new StringBuilder();
        java.util.List<TableColumn<TriggerLog.Eintrag, ?>> spalten = table.getVisibleLeafColumns();
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
        ClipboardContent inhalt = new ClipboardContent();
        inhalt.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(inhalt);
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
