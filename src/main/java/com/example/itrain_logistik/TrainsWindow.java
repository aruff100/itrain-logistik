package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.example.itrain_logistik.kern.EchtzeitKern;
import com.example.itrain_logistik.kern.Wagenladung;
import com.example.itrain_logistik.kern.ZugZustand;

import java.util.List;
import java.util.Optional;

/**
 * Eigenes Fenster "Züge": Liste links (name), rechts die Zusammenstellung
 * (composition). Greift dabei - wie im Projektauftrag verlangt - auf die
 * bekannten {@link LocomotiveEntry}/{@link WagonEntry}-Namen zu (Auswahl
 * beim Hinzufuegen) und stellt der .tcdz-Zusammenstellung die eigene,
 * erweiterbare gegenueber: weicht sie ab, erscheint ein Hinweis.
 * <p>
 * Unter der Zusammenstellung zeigt das Fenster den Laufzeitzustand des Zugs aus dem
 * Echtzeit-Kern: Standort, Ladung je Wagenladung und Passagiere je Klasse. Damit ein
 * periodischer Refresh die Auswahl nicht verliert, merkt sich das Fenster den zuletzt
 * ausgewaehlten Zugnamen selbst.
 */
public final class TrainsWindow {

    private static Stage stage;
    private static String zuletztAusgewaehlterZug;

    private TrainsWindow() {
    }

    /** Merkt einen Zug vor, der beim nächsten {@link #show} ausgewählt wird (Doppelklick in "Fahrzeuge"). */
    public static void waehle(String zugName) {
        zuletztAusgewaehlterZug = zugName;
    }

    public static void show(Stage owner, LogistikSnapshot snapshot, Runnable onDataChanged,
            EchtzeitKern kern) {
        if (stage != null && stage.isShowing()) {
            rebuild(snapshot, onDataChanged, kern);
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Züge");
        stage.initOwner(owner);
        rebuild(snapshot, onDataChanged, kern);
        WindowState.apply(stage, "trains", 820, 520);
        stage.show();
    }

    private static void rebuild(LogistikSnapshot snapshot, Runnable onDataChanged,
            EchtzeitKern kern) {
        List<TrainEntry> trains = snapshot.trains();
        ListView<TrainEntry> list = new ListView<>(FXCollections.observableArrayList(trains));
        list.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(TrainEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        VBox detail = new VBox(10);
        detail.setPadding(new Insets(10));
        detail.setPrefWidth(420);
        Label placeholder = new Label("Zug links auswählen.");
        detail.getChildren().add(placeholder);

        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            detail.getChildren().clear();
            zuletztAusgewaehlterZug = newV == null ? null : newV.getName();
            if (newV == null) {
                detail.getChildren().add(placeholder);
                return;
            }
            detail.getChildren().add(buildCompositionEditor(newV, snapshot, onDataChanged, kern));
        });

        SplitPane split = new SplitPane(list, detail);
        split.setDividerPositions(0.35);

        Fenster.szene(stage, split, 820, 520);
        // Alle 2 Sekunden neu aufgebaut (siehe Klassenkommentar) - die zuletzt
        // ausgewaehlte Zeile bleibt dabei erhalten, statt bei jedem Refresh auf
        // den ersten Zug zurueckzuspringen.
        TrainEntry auswahl = trains.stream()
                .filter(t -> t.getName().equals(zuletztAusgewaehlterZug))
                .findFirst()
                .orElse(trains.isEmpty() ? null : trains.get(0));
        if (auswahl != null) {
            list.getSelectionModel().select(auswahl);
        }
    }

    /**
     * Liefert "<Kapazität> <Ladegut>" (z.B. "20 Personen") fuer einen Wagen in
     * der Zusammenstellung, ermittelt ueber dessen Namen in snapshot.wagons().
     * Fuer Lokomotiven oder unbekannte/nicht gepflegte Wagen bleibt das Feld leer.
     */
    private static String[] kapazitaet(TrainCompositionItem item, LogistikSnapshot snapshot) {
        if (item.getKind() == TrainCompositionItem.Kind.LOCOMOTIVE) {
            LocomotiveEntry lok = Fahrzeugdaten.findeLok(snapshot, item.getName());
            return lok == null ? new String[]{"", ""} : WagenKapazitaet.zahlUndEinheitTriebzug(lok.getCustomFields());
        }
        if (item.getKind() != TrainCompositionItem.Kind.WAGON) {
            return new String[]{"", ""};
        }
        WagonEntry wagon = snapshot.wagons().stream()
                .filter(w -> w.getName().equals(item.getName()))
                .findFirst()
                .orElse(null);
        if (wagon == null) {
            return new String[]{"", ""};
        }
        return WagenKapazitaet.zahlUndEinheit(wagon);
    }

    private static VBox buildCompositionEditor(TrainEntry train, LogistikSnapshot snapshot, Runnable onDataChanged,
            EchtzeitKern kern) {
        Label name = new Label("Name: " + train.getName());

        boolean differsFromTcdz = !sameNames(train.getComposition(), train.getTcdzComposition());
        Label diffHint = new Label(differsFromTcdz
                ? "Weicht von der Zusammenstellung in der .tcdz ab (eigene, erweiterte Daten)."
                : "Entspricht der Zusammenstellung in der .tcdz.");
        diffHint.setWrapText(true);
        diffHint.setStyle("-fx-opacity: 0.85;");

        ObservableList<TrainCompositionItem> composition = train.getComposition();
        TableView<TrainCompositionItem> table = new TableView<>(composition);
        Spalten.einrichten(table, "zug.zusammenstellung");
        table.setPrefHeight(240);

        TableColumn<TrainCompositionItem, String> kindColumn = new TableColumn<>("Art");
        kindColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getKind() == TrainCompositionItem.Kind.LOCOMOTIVE ? "Lok"
                        : snapshot.wagons().stream().anyMatch(w -> w.isWagenliste()
                                && w.getName().equals(cell.getValue().getName())) ? "Wagenliste" : "Wagen"));
        kindColumn.setPrefWidth(90);

        TableColumn<TrainCompositionItem, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(150);

        TableColumn<TrainCompositionItem, String> mainColumn = new TableColumn<>("Führend");
        mainColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().isMain() ? "ja" : ""));
        mainColumn.setPrefWidth(60);

        TableColumn<TrainCompositionItem, String> directionColumn = new TableColumn<>("Richtung");
        directionColumn.setCellValueFactory(new PropertyValueFactory<>("direction"));
        directionColumn.setPrefWidth(70);

        // Kapazität als Zahl, dahinter die Einheit (Anwender 20.10.2026)
        TableColumn<TrainCompositionItem, String> kapazitaetColumn = new TableColumn<>("Kapazität");
        kapazitaetColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                kapazitaet(cell.getValue(), snapshot)[0]));
        kapazitaetColumn.setStyle("-fx-alignment: CENTER-RIGHT;");
        kapazitaetColumn.setPrefWidth(80);

        TableColumn<TrainCompositionItem, String> einheitColumn = new TableColumn<>("Einheit");
        einheitColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                kapazitaet(cell.getValue(), snapshot)[1]));
        einheitColumn.setPrefWidth(140);

        table.getColumns().setAll(java.util.List.of(kindColumn, nameColumn, mainColumn, directionColumn, kapazitaetColumn,
                einheitColumn));

        Button addLocoButton = new Button("Lok hinzufügen…");
        addLocoButton.setOnAction(e -> pickAndAdd(snapshot.locomotives().stream().map(LocomotiveEntry::getName).toList(),
                "Lokomotive hinzufügen", composition, TrainCompositionItem.Kind.LOCOMOTIVE, onDataChanged));

        Button addWagonButton = new Button("Wagen hinzufügen…");
        addWagonButton.setOnAction(e -> pickAndAdd(snapshot.wagons().stream().map(WagonEntry::getName).toList(),
                "Wagen hinzufügen", composition, TrainCompositionItem.Kind.WAGON, onDataChanged));

        Button removeButton = new Button("Entfernen");
        removeButton.setOnAction(e -> {
            TrainCompositionItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                composition.remove(selected);
                onDataChanged.run();
            }
        });

        Button resetButton = new Button("Auf .tcdz zurücksetzen");
        resetButton.setOnAction(e -> {
            composition.setAll(train.getTcdzComposition());
            onDataChanged.run();
        });

        HBox buttons = new HBox(8, addLocoButton, addWagonButton, removeButton, resetButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, name, diffHint, table, buttons, zustandText(train, kern));
        return box;
    }

    /** Laufzeitzustand aus dem Echtzeit-Kern (Standort, Ladung, Passagiere). */
    private static Label zustandText(TrainEntry train, EchtzeitKern kern) {
        Label label = new Label();
        label.setWrapText(true);
        if (kern == null || kern.getAnlage() == null) {
            return label;
        }
        ZugZustand z = kern.getAnlage().getZuege().get(train.getName());
        if (z == null) {
            label.setText("Noch kein Ereignis von diesem Zug empfangen.");
            return label;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Block: ").append(z.getBlock() == null ? "-" : z.getBlock());
        if (z.isHaltend() && z.getStation() != null) {
            sb.append("  (hält in ").append(z.getStation().getAnzeigeName()).append(")");
        }
        if (z.getLinie() != null) {
            sb.append("\nLinie: ").append(z.getLinie());
        }
        for (Wagenladung w : z.getWagenladungen()) {
            sb.append("\n").append(w.getWagenName()).append(" (").append(w.getKlasse().getLabel()).append("): ");
            if (w.getInhalt().isEmpty()) {
                sb.append("leer");
            }
            w.getInhalt().forEach((g, m) -> sb.append(WagenKapazitaet.formatiert(m)).append(' ')
                    .append(kern.getAnlage().gutName(g)).append("  "));
            sb.append(" / ").append(WagenKapazitaet.formatiert(w.getKapazitaet())).append(' ')
                    .append(w.getEinheit());
        }
        if (z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() > 0) {
            double[] p = z.passagiereSumme();
            sb.append("\nPassagiere 1. Klasse: ").append(WagenKapazitaet.formatiert(p[0])).append(" / ")
                    .append(WagenKapazitaet.formatiert(z.getSitzplaetzeErste()))
                    .append(",  2. Klasse: ").append(WagenKapazitaet.formatiert(p[1])).append(" / ")
                    .append(WagenKapazitaet.formatiert(z.getSitzplaetzeZweite()));
        }
        label.setText(sb.toString());
        return label;
    }

    private static void pickAndAdd(List<String> names, String title, ObservableList<TrainCompositionItem> composition,
            TrainCompositionItem.Kind kind, Runnable onDataChanged) {
        if (names.isEmpty()) {
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(names.get(0), names);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText("Name:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(chosenName -> {
            composition.add(new TrainCompositionItem(kind, chosenName, false, null));
            onDataChanged.run();
        });
    }

    private static boolean sameNames(List<TrainCompositionItem> a, List<TrainCompositionItem> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getName().equals(b.get(i).getName()) || a.get(i).getKind() != b.get(i).getKind()) {
                return false;
            }
        }
        return true;
    }

    public static void refreshIfOpen(LogistikSnapshot snapshot, Runnable onDataChanged,
            EchtzeitKern kern) {
        if (stage != null && stage.isShowing()) {
            rebuild(snapshot, onDataChanged, kern);
        }
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
        zuletztAusgewaehlterZug = null;
    }
}
