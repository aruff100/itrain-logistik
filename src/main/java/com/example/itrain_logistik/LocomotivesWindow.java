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
 * Eigenes Fenster "Lokomotiven": Liste links (Name + Beschreibung aus der
 * .tcdz - siehe Projektauftrag "Du hast in der Tabelle in Klammern den type
 * stehen. Da soll er weg, fuege dafuer die Beschreibung der originalen .tcdz
 * in die Tabelle als zweites Feld ein."), rechts die aus der .tcdz gelesenen
 * Basisangaben (name/typ/image) plus die strukturierten Logistikdaten
 * ({@link LocomotiveLogistikPane}, dort auch die Antriebsart - der iTrain-
 * {@code type} selbst wird beim Import automatisch dorthin uebernommen,
 * siehe {@link TcdzModelBuilder}). Bearbeitet wird auf einer Arbeitskopie
 * der Datenfelder - erst "Speichern" uebernimmt sie in die eigentliche
 * Lokomotive, "Zurück" verwirft sie (siehe Projektauftrag "Am unteren Rand
 * kommt 'Zurück' und 'Speichern' als Knöpfe."). Die frei benennbare
 * Zusatzfelder-Tabelle entfaellt hier bewusst (siehe Projektauftrag "Die
 * Tabelle für neue Datenfelder kann entfallen.") - anders als bei Wagen/
 * Blöcken, wo {@link CustomFieldsPane} weiterhin genutzt wird.
 */
public final class LocomotivesWindow {

    private static Stage stage;

    private LocomotivesWindow() {
    }

    private static com.example.itrain_logistik.kern.Anlage anlage;

    public static void show(Stage owner, List<LocomotiveEntry> locomotives, Runnable onDataChanged) {
        show(owner, locomotives, null, onDataChanged);
    }

    /** @param anlage für die Güterauswahl eines Triebzugs (darf null sein) */
    public static void show(Stage owner, List<LocomotiveEntry> locomotives, com.example.itrain_logistik.kern.Anlage anlage,
            Runnable onDataChanged) {
        LocomotivesWindow.anlage = anlage;
        if (stage != null && stage.isShowing()) {
            rebuild(locomotives, onDataChanged);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Lokomotiven");
        stage.initOwner(owner);
        rebuild(locomotives, onDataChanged);
        WindowState.apply(stage, "locomotives", 760, 480);
        stage.show();
    }

    private static void rebuild(List<LocomotiveEntry> locomotives, Runnable onDataChanged) {
        ListView<LocomotiveEntry> list = new ListView<>(FXCollections.observableArrayList(locomotives));
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(LocomotiveEntry item, boolean empty) {
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
        detail.setPrefWidth(320);
        Label placeholder = new Label("Lokomotive links auswählen.");
        detail.getChildren().add(placeholder);

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            detail.getChildren().clear();
            if (newV == null) {
                detail.getChildren().add(placeholder);
                return;
            }

            // Arbeitskopie: Bearbeitung wirkt sich erst mit "Speichern" auf die
            // eigentliche Lokomotive aus (siehe Klassenkommentar).
            ObservableList<CustomField> staging = FXCollections.observableArrayList();
            for (CustomField f : newV.getCustomFields()) {
                staging.add(new CustomField(f.getName(), f.getValue()));
            }

            Label name = new Label("Name: " + newV.getName());
            LocomotiveLogistikPane logistikPane = new LocomotiveLogistikPane(staging, () -> { });
            Haken.Gruppe haken = new Haken.Gruppe().ueber(logistikPane); // Anwender 14.10.2026
            // Triebzug/Triebwagen (Anwender 22.10.2026): in iTrain eine Lok, befördert aber selbst Personen
            // und/oder Güter; dann dieselben Angaben wie bei einem Wagen.
            javafx.scene.control.CheckBox triebzug = new javafx.scene.control.CheckBox(
                    "Triebzug / Triebwagen: befördert selbst Personen oder Güter");
            triebzug.setSelected(Fahrzeugdaten.istTriebzug(staging));
            VBox triebzugHalter = new VBox();
            Runnable triebzugZeigen = () -> {
                if (triebzug.isSelected()) {
                    triebzugHalter.getChildren().setAll(new WagonLogistikPane(staging, anlage, () -> { }, haken, true));
                } else {
                    triebzugHalter.getChildren().clear();
                }
            };
            triebzug.selectedProperty().addListener((o2, alt, an) -> {
                LogistikFelder.setValue(staging, LogistikFelder.TRIEBZUG, String.valueOf(an));
                triebzugZeigen.run();
            });
            triebzugZeigen.run();
            haken.ueber(triebzug);

            Button zurueckButton = new Button("Zurück");
            zurueckButton.setOnAction(e -> list.getSelectionModel().clearSelection());
            Button speichernButton = new Button("Speichern");
            speichernButton.setOnAction(e -> {
                List<CustomField> kopie = new java.util.ArrayList<>();
                staging.forEach(f -> kopie.add(new CustomField(f.getName(), f.getValue())));
                newV.getCustomFields().setAll(kopie);
                onDataChanged.run();
                haken.bestaetigen();
            });
            HBox buttons = new HBox(8, zurueckButton, speichernButton);
            buttons.setAlignment(Pos.CENTER_LEFT);
            buttons.setPadding(new Insets(8, 0, 0, 0));

            if (newV.getDescription() != null && !newV.getDescription().isBlank()) {
                detail.getChildren().addAll(name, new Label("Beschreibung: " + newV.getDescription()),
                        imageView(newV.getImageFile()), logistikPane, triebzug, triebzugHalter, buttons);
            } else {
                detail.getChildren().addAll(name, imageView(newV.getImageFile()), logistikPane, triebzug,
                        triebzugHalter, buttons);
            }
        });

        // Mit den Triebzug-Angaben wird die rechte Seite lang: rollbar (Anwender 22.10.2026)
        javafx.scene.control.ScrollPane rechts = new javafx.scene.control.ScrollPane(detail);
        rechts.setFitToWidth(true);
        SplitPane split = new SplitPane(list, rechts);
        split.setDividerPositions(0.4);

        Fenster.szene(stage, split, 760, 480);
        if (!locomotives.isEmpty()) {
            list.getSelectionModel().selectFirst();
        }
    }

    /**
     * Zeigt das ueber {@link ImageResolver} aufgeloeste Lok-Bild - ohne den
     * Pfad als Text (siehe Projektauftrag: "den Pfad musst Du nicht
     * anzeigen"). Ohne aufloesbares Bild erscheint stattdessen ein kurzer
     * Hinweis, damit klar ist, warum nichts zu sehen ist.
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

    public static void refreshIfOpen(java.util.List<LocomotiveEntry> locomotives, Runnable onDataChanged) {
        if (stage != null && stage.isShowing()) {
            rebuild(locomotives, onDataChanged);
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
