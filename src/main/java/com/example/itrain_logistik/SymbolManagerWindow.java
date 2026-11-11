package com.example.itrain_logistik;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.TextFieldTableCell;
import com.example.itrain_logistik.kern.ObjektArt;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.StringConverter;


/**
 * Verwaltung des Symbol-Katalogs: Tabelle Symbol/Name/Beschreibung (editierbar)/"Verwendet für"
 * (welche Objektarten der Anlage es zeigen), eigene Farbflächen, Zuweisung des markierten Symbols an
 * eine Objektart ("Symbol zuweisen") und Rücknahme ("Standard").
 */
public final class SymbolManagerWindow {

    private static Stage stage;

    private SymbolManagerWindow() {
    }

    public static void show(Stage owner, String vorausgewaehlterTyp, Runnable onApplied) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Symbole verwalten");
        stage.initOwner(owner);

        SymbolRegistry registry = SymbolRegistry.getInstance();

        TableView<SymbolDef> table = new TableView<>();
        Spalten.einrichten(table, "symbole");
        TableColumn<SymbolDef, SymbolDef> previewCol = new TableColumn<>("Symbol");
        previewCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        previewCol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(SymbolDef item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    StackPane holder = new StackPane(item.createIcon());
                    holder.setPrefSize(32, 32);
                    setGraphic(holder);
                }
            }
        });
        previewCol.setPrefWidth(64);

        // Welche Objektarten der Anlage dieses Symbol gerade zeigen (ersetzt die fruehere Spalte "Art").
        TableColumn<SymbolDef, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                registry.istNeuUndUnzugewiesen(c.getValue()) ? c.getValue().getName() + "  (neu)" : c.getValue().getName()));
        nameCol.setPrefWidth(160);

        TableColumn<SymbolDef, String> beschreibungCol = new TableColumn<>("Beschreibung");
        beschreibungCol.setCellValueFactory(c -> new SimpleStringProperty(registry.beschreibung(c.getValue())));
        beschreibungCol.setCellFactory(TextFieldTableCell.forTableColumn());
        beschreibungCol.setOnEditCommit(e -> {
            registry.setBeschreibung(e.getRowValue(), e.getNewValue());
            table.refresh();
        });
        beschreibungCol.setEditable(true);
        beschreibungCol.setPrefWidth(300);

        TableColumn<SymbolDef, String> verwendetCol = new TableColumn<>("Verwendet für");
        verwendetCol.setCellValueFactory(c -> new SimpleStringProperty(verwendetFuer(registry, c.getValue())));
        verwendetCol.setPrefWidth(180);

        table.setEditable(true);
        table.getColumns().add(previewCol);
        table.getColumns().add(nameCol);
        table.getColumns().add(beschreibungCol);
        table.getColumns().add(verwendetCol);
        table.getItems().addAll(benutzte(registry));
        table.getItems().addAll(registry.getZusaetzlicheSymbole());

        // Nur die Objektarten, die es auf der Anlage gibt.
        ComboBox<ObjektArt> funktionBox = new ComboBox<>();
        funktionBox.getItems().addAll(ObjektArt.values());
        funktionBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ObjektArt a) {
                return a == null ? "" : a.getLabel();
            }

            @Override
            public ObjektArt fromString(String s) {
                return null;
            }
        });
        Label aktuellLabel = new Label();
        Runnable zeigeAktuell = () -> {
            ObjektArt a = funktionBox.getValue();
            if (a == null) {
                aktuellLabel.setText("");
                return;
            }
            SymbolDef aktuell = registry.symbolFuer(a.getSymbolSchluessel());
            aktuellLabel.setText("zeigt zurzeit: " + (aktuell != null ? aktuell.getName() : "–")
                    + (registry.hatEigeneZuweisung(a.getSymbolSchluessel()) ? " (geändert)" : " (Standard)"));
        };
        funktionBox.valueProperty().addListener((o, alt, neu) -> {
            zeigeAktuell.run();
            if (neu != null) {
                SymbolDef aktuell = registry.symbolFuer(neu.getSymbolSchluessel());
                if (aktuell != null) {
                    table.getSelectionModel().select(aktuell);
                    table.scrollTo(aktuell);
                }
            }
        });
        if (vorausgewaehlterTyp != null) {
            for (ObjektArt a : ObjektArt.values()) {
                if (a.getSymbolSchluessel().equals(vorausgewaehlterTyp)) {
                    funktionBox.setValue(a);
                }
            }
        }
        funktionBox.setPromptText("Objektart wählen");

        Button neuButton = new Button("Neue Farbfläche...");
        neuButton.setTooltip(Symbole.tip("Legt ein Symbol an, das nur aus einem farbigen Quadrat besteht (Platzhalter ohne eigene .svg-Datei)."));
        neuButton.setOnAction(e -> {
            TextInputDialog nameDialog = new TextInputDialog("Neues Symbol");
            nameDialog.setTitle("Neues Symbol");
            nameDialog.setHeaderText(null);
            nameDialog.setContentText("Name:");
            nameDialog.showAndWait().ifPresent(name -> {
                ColorPicker picker = Symbole.ohneHex(new ColorPicker(NetzIcons.DEFAULT_COLOR));
                Dialog<Color> colorDialog = new Dialog<>();
                colorDialog.setTitle("Farbe für \"" + name + "\"");
                DialogKnoepfe.setze(colorDialog.getDialogPane());
                VBox content = new VBox(10, picker);
                content.setPadding(new Insets(10));
                colorDialog.getDialogPane().setContent(content);
                colorDialog.setResultConverter(bt -> bt == DialogKnoepfe.SPEICHERN ? picker.getValue() : null);
                colorDialog.showAndWait().ifPresent(color -> {
                    SymbolDef neu = registry.addRechteckSymbol(name, toHex(color));
                    table.getItems().add(neu);
                    table.getSelectionModel().select(neu);
                });
            });
        });

        Button loeschenButton = new Button("Löschen");
        loeschenButton.setTooltip(Symbole.tip("Löscht eine selbst angelegte Farbfläche. .svg-Dateien bitte im Symbolordner entfernen."));
        loeschenButton.setOnAction(e -> {
            SymbolDef selected = table.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getArt() != SymbolDef.Art.ORIGINAL) {
                registry.removeSymbol(selected);
                table.getItems().remove(selected);
            }
        });

        Button anwendenButton = new Button("Symbol zuweisen");
        anwendenButton.setTooltip(Symbole.tip("Die gewählte Objektart zeigt auf der Anlage künftig das markierte Symbol."));
        anwendenButton.setOnAction(e -> {
            SymbolDef selected = table.getSelectionModel().getSelectedItem();
            ObjektArt art = funktionBox.getValue();
            if (selected != null && art != null) {
                registry.zuweisen(art.getSymbolSchluessel(), selected);
                angewendet(table, zeigeAktuell, onApplied);
            }
        });

        Button standardButton = new Button("Standard");
        standardButton.setTooltip(Symbole.tip("Die gewählte Objektart zeigt wieder ihr eigenes Symbol."));
        standardButton.setOnAction(e -> {
            ObjektArt art = funktionBox.getValue();
            if (art != null) {
                registry.standardWiederherstellen(art.getSymbolSchluessel());
                angewendet(table, zeigeAktuell, onApplied);
            }
        });

        Button neuEinlesenButton = new Button("Ordner neu einlesen");
        neuEinlesenButton.setTooltip(Symbole.tip("Liest den Symbolordner (Voreinstellungen > Pfade) erneut, z. B. nach dem Hinzufügen eigener .svg-Dateien."));
        neuEinlesenButton.setOnAction(e -> {
            registry.scanSymbolOrdner();
            table.getItems().setAll(benutzte(registry));
            table.getItems().addAll(registry.getZusaetzlicheSymbole());
            table.refresh();
            if (onApplied != null) {
                onApplied.run();
            }
        });

        HBox tabellenButtons = new HBox(8, neuButton, loeschenButton, neuEinlesenButton);
        tabellenButtons.setPadding(new Insets(8, 8, 4, 8));

        HBox zuweisungsBox = new HBox(8, new Label("Objektart:"), funktionBox, anwendenButton, standardButton, aktuellLabel);
        zuweisungsBox.setPadding(new Insets(8));
        zuweisungsBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(table, tabellenButtons, new Separator(), zuweisungsBox);
        VBox.setVgrow(table, Priority.ALWAYS);

        Fenster.szene(stage, root, 820, 560);
        if (!Boolean.TRUE.equals(stage.getProperties().get("groesse"))) {
            stage.getProperties().put("groesse", true);
            WindowState.apply(stage, "symbolmanager", 820, 560); // Anwender 22.10.2026
        }
        stage.setOnHidden(e -> stage = null);
        stage.show();
    }

    private static void angewendet(TableView<SymbolDef> table, Runnable zeigeAktuell, Runnable onApplied) {
        table.refresh();
        zeigeAktuell.run();
        if (onApplied != null) {
            onApplied.run();
        }
    }

    /**
     * Eingebaute Symbole, die eine Objektart der Anlage zeigt (oder die einer Objektart zugewiesen sind);
     * die alten Palettensymbole (Bekohlung, Tankstelle, Post ...) blendet das Fenster aus (Anwender 12.10.2026).
     */
    private static java.util.List<SymbolDef> benutzte(SymbolRegistry registry) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (ObjektArt a : ObjektArt.values()) {
            ids.add(a.getSymbolSchluessel());
            SymbolDef s = registry.symbolFuer(a.getSymbolSchluessel());
            if (s != null) {
                ids.add(s.getId());
            }
        }
        java.util.List<SymbolDef> l = new java.util.ArrayList<>();
        for (SymbolDef s : registry.getOriginalSymbole()) {
            if (ids.contains(s.getId())) {
                l.add(s);
            }
        }
        return l;
    }

    private static String verwendetFuer(SymbolRegistry registry, SymbolDef def) {
        StringBuilder sb = new StringBuilder();
        for (ObjektArt a : ObjektArt.values()) {
            SymbolDef s = registry.symbolFuer(a.getSymbolSchluessel());
            if (s != null && s.getId().equals(def.getId())) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(a.getLabel());
            }
        }
        return sb.isEmpty() ? "–" : sb.toString();
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
