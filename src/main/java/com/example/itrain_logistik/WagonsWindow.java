package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * Eigenes Fenster "Wagen": Liste links (Name + Beschreibung aus der .tcdz,
 * analog zu {@link LocomotivesWindow}), rechts image, Wagenart-abhaengige
 * Logistikdaten ({@link WagonLogistikPane}) und Ladegut-Tabelle. Bearbeitet
 * wird - wie bei den Lokomotiven - auf einer Arbeitskopie: erst "Speichern"
 * uebernimmt sie in den eigentlichen Wagen, "Zurück" verwirft sie. Die frei
 * benennbare Zusatzfelder-Tabelle entfaellt hier ebenfalls (siehe
 * Projektauftrag "Bei Wagen die 'Eigene Datenfelder' auch komplett weg.").
 */
public final class WagonsWindow {

    private static Stage stage;

    private WagonsWindow() {
    }

    public static void show(Stage owner, List<WagonEntry> wagons, com.example.itrain_logistik.kern.Anlage anlage,
            Runnable onDataChanged) {
        if (stage != null && stage.isShowing()) {
            rebuild(wagons, anlage, onDataChanged);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Wagen");
        stage.initOwner(owner);
        rebuild(wagons, anlage, onDataChanged);
        WindowState.apply(stage, "wagons", 900, 640);
        stage.show();
    }

    private static void rebuild(List<WagonEntry> wagons, com.example.itrain_logistik.kern.Anlage anlage,
            Runnable onDataChanged) {
        ListView<WagonEntry> list = new ListView<>(FXCollections.observableArrayList(wagons));
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(WagonEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String beschreibung = item.getDescription();
                setText(beschreibung != null && !beschreibung.isBlank()
                        ? item.getName() + "  –  " + beschreibung
                        : item.getName());
            }
        });

        VBox detail = new VBox(10);
        detail.setPadding(new Insets(10));
        detail.setPrefWidth(480);
        Label placeholder = new Label("Wagen links auswählen.");
        detail.getChildren().add(placeholder);

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            detail.getChildren().clear();
            if (newV == null) {
                detail.getChildren().add(placeholder);
                return;
            }

            // Arbeitskopie: Bearbeitung wirkt sich erst mit "Speichern" auf den
            // eigentlichen Wagen aus (siehe Klassenkommentar).
            ObservableList<CustomField> stagingFields = FXCollections.observableArrayList();
            for (CustomField f : newV.getCustomFields()) {
                stagingFields.add(new CustomField(f.getName(), f.getValue()));
            }

            Label name = new Label("Name: " + newV.getName());
            // Grüne Haken hinter gespeicherten Feldern (Anwender 14.10.2026)
            Haken.Gruppe[] haken = {new Haken.Gruppe()};
            VBox paneHalter = new VBox(new WagonLogistikPane(stagingFields, anlage, () -> { }, haken[0]));
            VBox.setVgrow(paneHalter, javafx.scene.layout.Priority.ALWAYS);
            VBox.setVgrow(paneHalter.getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);

            Button zurueckButton = new Button("Zurück");
            zurueckButton.setOnAction(e -> list.getSelectionModel().clearSelection());
            Button speichernButton = new Button("Speichern");
            speichernButton.setOnAction(e -> {
                // gespeichert wird eine Kopie, damit die Arbeitskopie getrennt bleibt
                List<CustomField> kopie = new java.util.ArrayList<>();
                stagingFields.forEach(f -> kopie.add(new CustomField(f.getName(), f.getValue())));
                newV.getCustomFields().setAll(kopie);
                onDataChanged.run();
                haken[0].bestaetigen();
            });
            // Angaben eines Wagens in die Zwischenablage und beim nächsten Wagen einfügen (Anwender 14.10.2026)
            Button kopieren = new Button("Angaben kopieren");
            kopieren.setTooltip(new javafx.scene.control.Tooltip(
                    "Alle Angaben dieses Wagens (Wagenart, Plätze, Kapazität, Güter, Zeiten) in die Zwischenablage"));
            kopieren.setOnAction(e -> {
                javafx.scene.input.ClipboardContent inhalt = new javafx.scene.input.ClipboardContent();
                inhalt.putString(WagenAngaben.alsText(stagingFields));
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(inhalt);
            });
            Button einfuegen = new Button("Einfügen");
            einfuegen.setTooltip(new javafx.scene.control.Tooltip(
                    "Angaben aus der Zwischenablage übernehmen; erst „Speichern“ übernimmt sie in den Wagen"));
            einfuegen.setOnAction(e -> {
                String text = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                List<CustomField> neu = WagenAngaben.ausText(text);
                if (neu == null) {
                    javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION,
                            "In der Zwischenablage stehen keine Wagen-Angaben. Bitte zuerst bei einem Wagen "
                                    + "„Angaben kopieren“ drücken.");
                    a.initOwner(stage);
                    a.setHeaderText(null);
                    a.showAndWait();
                    return;
                }
                stagingFields.setAll(neu);
                haken[0] = new Haken.Gruppe();
                paneHalter.getChildren().setAll(new WagonLogistikPane(stagingFields, anlage, () -> { }, haken[0]));
                VBox.setVgrow(paneHalter.getChildren().get(0), javafx.scene.layout.Priority.ALWAYS);
                haken[0].allesUngespeichert();
            });
            HBox buttons = new HBox(8, zurueckButton, speichernButton, new javafx.scene.control.Separator(
                    javafx.geometry.Orientation.VERTICAL), kopieren, einfuegen);
            buttons.setAlignment(Pos.CENTER_LEFT);
            buttons.setPadding(new Insets(8, 0, 0, 0));

            if (newV.getDescription() != null && !newV.getDescription().isBlank()) {
                detail.getChildren().addAll(name, new Label("Beschreibung: " + newV.getDescription()),
                        imageView(newV.getImageFile()), paneHalter, buttons);
            } else {
                detail.getChildren().addAll(name, imageView(newV.getImageFile()), paneHalter, buttons);
            }
        });

        javafx.scene.control.ScrollPane detailScroll = new javafx.scene.control.ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        // Inhalt füllt die Fensterhöhe, damit die Gütertabelle mitwächst (Anwender 14.10.2026)
        detailScroll.setFitToHeight(true);
        detail.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE); // nie stauchen, sonst Bildlauf
        SplitPane split = new SplitPane(list, detailScroll);
        split.setDividerPositions(0.35);

        Fenster.szene(stage, split, 900, 640);
        if (!wagons.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
    }

    /**
     * Zeigt das ueber {@link ImageResolver} aufgeloeste Wagen-Bild - ohne den
     * Pfad als Text (siehe Projektauftrag: "den Pfad musst Du nicht
     * anzeigen").
     */
    private static javafx.scene.Node imageView(String relativePath) {
        File file = ImageResolver.resolve(relativePath);
        if (file == null) {
            Label hint = new Label(relativePath != null
                    ? "Bild nicht gefunden (Anfangsordner für Layout-Dateien prüfen)."
                    : "Kein Bild hinterlegt.");
            hint.setWrapText(true);
            return hint;
        }
        ImageView view = new ImageView(new Image(file.toURI().toString()));
        view.setPreserveRatio(true);
        view.setFitWidth(240);
        view.setFitHeight(160);
        return view;
    }

    public static void refreshIfOpen(java.util.List<WagonEntry> wagons, com.example.itrain_logistik.kern.Anlage anlage,
            Runnable onDataChanged) {
        if (stage != null && stage.isShowing()) {
            rebuild(wagons, anlage, onDataChanged);
        }
    }

    /** Einmal ausführen, sobald das Fenster geschlossen wird (Einrichtungs-Ablauf, Anwender 22.10.2026). */
    public static void beimSchliessen(Runnable r) {
        if (stage == null || !stage.isShowing()) {
            r.run();
            return;
        }
        javafx.event.EventHandler<javafx.stage.WindowEvent>[] h = new javafx.event.EventHandler[1];
        h[0] = e -> {
            stage.removeEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, h[0]);
            r.run();
        };
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, h[0]);
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
