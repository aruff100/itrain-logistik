package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.Einheit;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.GutStufe;
import com.example.itrain_logistik.kern.Ladeklasse;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Rezept;
import com.example.itrain_logistik.kern.StadtBereich;
import com.example.itrain_logistik.kern.Tf3Daten;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Map;

/**
 * Gueterkatalog der Anlage (Konzept Abschnitt 3.1, ersetzt den frueheren globalen
 * Ladegut-Katalog): Grundbestand sind die 36 Gueter aus TF3; eigene Gueter lassen sich
 * ergaenzen, umbenennen und loeschen. Ladeklasse und "Mischbar" bestimmen, welche
 * Wagenladungen ein Gut aufnehmen (+Cargo 2.4).
 */
public final class GueterKatalogWindow {

    private static Stage stage;

    private GueterKatalogWindow() {
    }

    public static void show(Stage owner, Anlage a, Runnable onChanged) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle("Güter");
        // Tabelle statt Liste: Gut | Ladeklasse | Einheit (Anwender 14.10.2026)
        TableView<Gut> liste = new TableView<>(FXCollections.observableArrayList(a.getGueter().values()));
        Spalten.einrichten(liste, "gueterkatalog");
        TableColumn<Gut, Gut> gutSpalte = new TableColumn<>("Gut");
        gutSpalte.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        gutSpalte.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Gut item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setGraphic(empty || item == null ? null : Symbole.gut(item, 16));
            }
        });
        gutSpalte.setPrefWidth(150);
        TableColumn<Gut, String> klasseSpalte = new TableColumn<>("Ladeklasse");
        klasseSpalte.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().getLadeklasse().getLabel()));
        klasseSpalte.setPrefWidth(90);
        TableColumn<Gut, String> einheitSpalte = new TableColumn<>("Einheit");
        einheitSpalte.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(einheitText(c.getValue())));
        einheitSpalte.setPrefWidth(120);
        liste.getColumns().setAll(java.util.List.of(gutSpalte, klasseSpalte, einheitSpalte));
        liste.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox detail = new VBox(10);
        detail.setPadding(new Insets(10));
        liste.getSelectionModel().selectedItemProperty().addListener((obs, alt, g) -> {
            detail.getChildren().clear();
            if (g != null) {
                detail.getChildren().add(formular(g, a, liste, onChanged));
            }
        });
        Button neu = new Button("Neues Gut…");
        neu.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog();
            d.initOwner(stage);
            d.setTitle("Neues Gut");
            d.setHeaderText("Name des neuen Guts");
            d.showAndWait().ifPresent(name -> {
                if (name.isBlank()) {
                    return;
                }
                String id = "eigen_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                Gut g = new Gut(id, name.trim(), Ladeklasse.STUECKGUT, "#808080", GutStufe.ZWISCHENPRODUKT, null);
                a.getGueter().put(id, g);
                liste.getItems().add(g);
                liste.getSelectionModel().select(g);
                onChanged.run();
            });
        });
        Button loeschen = new Button("Löschen");
        loeschen.setOnAction(e -> {
            Gut g = liste.getSelectionModel().getSelectedItem();
            if (g == null) {
                return;
            }
            String verwendet = verwendung(a, g.getId());
            if (verwendet != null) {
                new Alert(Alert.AlertType.WARNING, g.getName() + " wird noch verwendet: " + verwendet).showAndWait();
                return;
            }
            a.getGueter().remove(g.getId());
            a.getGeloeschteGueter().add(g.getId());
            liste.getItems().remove(g);
            onChanged.run();
        });
        Button tf3 = new Button("Grundbestand ergänzen");
        tf3.setOnAction(e -> {
            int n = 0;
            for (Gut g : Tf3Daten.gueter()) {
                if (!a.getGueter().containsKey(g.getId())) {
                    a.getGeloeschteGueter().remove(g.getId());
                    a.getGueter().put(g.getId(), g);
                    liste.getItems().add(g);
                    n++;
                }
            }
            new Alert(Alert.AlertType.INFORMATION, n + " Güter ergänzt.").showAndWait();
            if (n > 0) {
                onChanged.run();
            }
        });
        VBox links = new VBox(6, liste, new HBox(6, neu, loeschen), tf3);
        links.setPadding(new Insets(6));
        javafx.scene.layout.VBox.setVgrow(liste, javafx.scene.layout.Priority.ALWAYS);
        SplitPane split = new SplitPane(links, detail);
        split.setDividerPositions(0.5);
        Fenster.szene(stage, split, 760, 520);
        WindowState.apply(stage, "gueterKatalog", 760, 520);
        stage.setOnHidden(e -> stage = null);
        stage.show();
        liste.getSelectionModel().selectFirst();
    }

    /** "Gebinde" bzw. "Brammen (10 Stück)" bei eigener Einheit mit Kapazität je Einheit. */
    static String einheitText(Gut g) {
        String e = g.getEinheit();
        if (g.getEigeneEinheit() != null && g.getKapazitaetJeEinheit() != 1) {
            return e + " (" + Symbole.zahl(g.getKapazitaetJeEinheit()) + " "
                    + Einheit.standard(g.getLadeklasse()) + ")";
        }
        return e;
    }

    private static VBox formular(Gut g, Anlage a, TableView<Gut> liste, Runnable onChanged) {
        TextField name = new TextField(g.getName());
        ComboBox<Ladeklasse> klasse = new ComboBox<>(FXCollections.observableArrayList(Ladeklasse.gueterKlassen()));
        klasse.setConverter(ObjektDialoge.konverter(Ladeklasse::getLabel));
        klasse.setValue(g.getLadeklasse());
        ColorPicker farbe = Symbole.farbwahl(Symbole.farbe(g.getFarbe(), javafx.scene.paint.Color.GRAY));
        ComboBox<GutStufe> stufe = new ComboBox<>(FXCollections.observableArrayList(GutStufe.values()));
        stufe.setConverter(ObjektDialoge.konverter(GutStufe::getLabel));
        stufe.setValue(g.getStufe());
        ComboBox<StadtBereich> bereich = new ComboBox<>(FXCollections.observableArrayList(StadtBereich.values()));
        bereich.getItems().add(0, null);
        bereich.setConverter(ObjektDialoge.konverter(StadtBereich::getLabel));
        bereich.setValue(g.getStadtBereich());
        // Einheit als Auswahl (Anwender 14.10.2026); Standard folgt der Ladeklasse
        ComboBox<String> einheit = new ComboBox<>(FXCollections.observableArrayList(Einheit.ALLE));
        einheit.setValue(g.getEinheit());
        TextField jeEinheit = new TextField(Symbole.zahl(g.getKapazitaetJeEinheit()));
        LogistikFeldRow.ziffern(jeEinheit, 4);
        javafx.scene.control.Label jeEinheitHinweis = new javafx.scene.control.Label();
        jeEinheitHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        jeEinheitHinweis.setWrapText(true);
        jeEinheitHinweis.setMaxWidth(320);
        Runnable einheitRegel = () -> {
            Ladeklasse k = klasse.getValue() != null ? klasse.getValue() : g.getLadeklasse();
            String std = Einheit.standard(k);
            boolean eigen = einheit.getValue() != null && !einheit.getValue().equals(std);
            jeEinheit.setDisable(!eigen);
            if (!eigen) {
                jeEinheit.setText("1");
            }
            jeEinheitHinweis.setText(eigen
                    ? "Wie viel Kapazität eines " + k.getLabel() + "-Wagens (in " + Einheit.mitBasis(std) + ") eine Einheit "
                    + einheit.getValue() + " belegt. Beispiel: 10 – ein Wagen mit Kapazität 20 lädt 2."
                    : "Standardeinheit der Ladeklasse " + k.getLabel() + ": " + Einheit.mitBasis(std) + ".");
        };
        klasse.valueProperty().addListener((obs, alt, neu) -> {
            // stand die Standardeinheit der alten Klasse, wechselt sie mit
            if (alt != null && neu != null && Einheit.standard(alt).equals(einheit.getValue())) {
                einheit.setValue(Einheit.standard(neu));
            }
            einheitRegel.run();
        });
        einheit.valueProperty().addListener((obs, alt, neu) -> einheitRegel.run());
        einheitRegel.run();
        ComboBox<String> mischbar = new ComboBox<>(FXCollections.observableArrayList(
                "Voreinstellung der Ladeklasse", "ja", "nein"));
        mischbar.setValue(g.getMischbarEinstellung() == null ? "Voreinstellung der Ladeklasse"
                : g.getMischbarEinstellung() ? "ja" : "nein");
        GridPane grid = ObjektDialoge.grid();
        ObjektDialoge.zeile(grid, 0, "Name", name);
        ObjektDialoge.zeile(grid, 1, "Ladeklasse", klasse);
        ObjektDialoge.zeile(grid, 2, "Farbe", farbe);
        ObjektDialoge.zeile(grid, 3, "Stufe", stufe);
        ObjektDialoge.zeile(grid, 4, "Bedarf", bereich);
        ObjektDialoge.zeile(grid, 5, "Einheit", einheit);
        ObjektDialoge.zeile(grid, 6, "Kapazität je Einheit", jeEinheit);
        grid.add(jeEinheitHinweis, 1, 7);
        ObjektDialoge.zeile(grid, 8, "Mischbar in einer Wagenladung", mischbar);
        Haken.Gruppe haken = new Haken.Gruppe().ueber(grid); // Anwender 14.10.2026
        Button abbruch = new Button("Abbruch");
        abbruch.setOnAction(e -> {
            // Eingaben verwerfen: Felder wieder auf den gespeicherten Stand
            name.setText(g.getName());
            klasse.setValue(g.getLadeklasse());
            farbe.setValue(Symbole.farbe(g.getFarbe(), javafx.scene.paint.Color.GRAY));
            stufe.setValue(g.getStufe());
            bereich.setValue(g.getStadtBereich());
            einheit.setValue(g.getEinheit());
            jeEinheit.setText(Symbole.zahl(g.getKapazitaetJeEinheit()));
            mischbar.setValue(g.getMischbarEinstellung() == null ? "Voreinstellung der Ladeklasse"
                    : g.getMischbarEinstellung() ? "ja" : "nein");
        });
        Button speichern = new Button("Speichern");
        speichern.setOnAction(e -> {
            g.setName(name.getText().trim());
            g.setLadeklasse(klasse.getValue() != null ? klasse.getValue() : g.getLadeklasse());
            g.setFarbe(Symbole.hex(farbe.getValue()));
            g.setStufe(stufe.getValue());
            g.setStadtBereich(bereich.getValue());
            g.setEinheit(einheit.getValue());
            try {
                g.setKapazitaetJeEinheit(jeEinheit.getText().isBlank() ? 1 : Double.parseDouble(jeEinheit.getText()));
            } catch (NumberFormatException ex) {
                g.setKapazitaetJeEinheit(1);
            }
            g.setMischbarEinstellung("ja".equals(mischbar.getValue()) ? Boolean.TRUE
                    : "nein".equals(mischbar.getValue()) ? Boolean.FALSE : null);
            liste.refresh();
            onChanged.run();
            haken.bestaetigen();
        });
        VBox v = new VBox(10, Symbole.gut(g, 32), grid, new HBox(8, abbruch, speichern));
        String verwendet = verwendung(a, g.getId());
        if (verwendet != null) {
            v.getChildren().add(new javafx.scene.control.Label("Verwendet: " + verwendet));
        }
        return v;
    }

    /** Wo ein Gut verwendet wird (Rezepte, Bedarf), sonst null. */
    private static String verwendung(Anlage a, String gutId) {
        StringBuilder sb = new StringBuilder();
        for (Betrieb b : a.betriebe()) {
            for (Rezept r : b.getRezepte()) {
                if (r.getEingang().containsKey(gutId) || r.getAusgang().containsKey(gutId)) {
                    sb.append(sb.length() > 0 ? ", " : "").append(b.getAnzeigeName());
                    break;
                }
            }
        }
        for (Ort o : a.orte()) {
            for (Map.Entry<String, Double> e : o.getBedarf().entrySet()) {
                if (e.getKey().equals(gutId)) {
                    sb.append(sb.length() > 0 ? ", " : "").append(o.getAnzeigeName());
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
