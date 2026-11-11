package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.List;

/**
 * Eigenes Fenster "Belegung": Blöcke mit dem gerade dort stehenden Zug -
 * gelesen aus {@code <control-state><blocks>} in der .tcdz (siehe
 * {@link ControlStateBlockEntry}). Reine Anzeige (Momentaufnahme beim
 * Öffnen), keine eigenen Zusatzfelder - dafür gibt es das Blöcke-Fenster.
 */
public final class ControlStateWindow {

    private static Stage stage;

    private ControlStateWindow() {
    }

    public static void show(Stage owner, List<ControlStateBlockEntry> entries) {
        if (stage != null && stage.isShowing()) {
            rebuild(entries);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Belegung");
        stage.initOwner(owner);
        rebuild(entries);
        WindowState.apply(stage, "controlState", 520, 480);
        stage.show();
    }

    private static void rebuild(List<ControlStateBlockEntry> entries) {
        TableView<ControlStateBlockEntry> table = new TableView<>(FXCollections.observableArrayList(entries));
        Spalten.einrichten(table, "belegung");

        // ControlStateBlockEntry ist ein Record (Zugriff ueber name()/train(),
        // nicht getName()/getTrain()) - PropertyValueFactory erwartet aber
        // JavaBean-Zugriffsmethoden und scheitert bei Records zur Laufzeit
        // ("Cannot read from unreadable property"). Deshalb hier wie bei der
        // Spalte "Zug" eine eigene Zugriffsfunktion statt PropertyValueFactory.
        TableColumn<ControlStateBlockEntry, String> nameColumn = new TableColumn<>("Block");
        nameColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().name()));
        nameColumn.setPrefWidth(260);

        TableColumn<ControlStateBlockEntry, String> trainColumn = new TableColumn<>("Zug");
        trainColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().train() != null ? cell.getValue().train() : ""));
        trainColumn.setPrefWidth(220);

        table.getColumns().setAll(java.util.List.of(nameColumn, trainColumn));

        Fenster.szene(stage, table, 520, 480);
    }

    public static void refreshIfOpen(java.util.List<ControlStateBlockEntry> entries) {
        if (stage != null && stage.isShowing()) {
            rebuild(entries);
        }
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
