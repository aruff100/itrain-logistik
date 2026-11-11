package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.StadtBereich;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mengen-Abschnitt für Güter (Tabelle Gut / Menge je Modellstunde, Zeile zum Hinzufügen, Knöpfe
 * "Vorschlag…" und "Als Vorschlag speichern…"). Genutzt im Dialog für Stadt/Dorf/Ländlich (Bedarf)
 * und für Gleise der Außenwelt (Schattenbahnhof: Angebot). Ein Vorschlag enthält auch die Personenwerte
 * (Passagiere je Modellstunde, Anteil 1. Klasse, Aussteigeanteil).
 * <p>
 * Eingebaut: Stadt (Grundbedarf), Dorf (50 %), Ländlich (10 %); eigene Vorschläge speichert
 * {@link AppSettings#getMengenVorschlaege()}.
 */
public final class MengenEditor {

    /** Ein Vorschlag; Personenwerte: je Modellstunde, Anteile 0..1. */
    public record Vorschlag(String name, double passagiere, double ersteKlasse, double aussteigen,
            Map<String, Double> gueter, boolean eigen) {
        @Override
        public String toString() {
            return name;
        }
    }

    private final Anlage anlage;
    private final ObservableList<Map.Entry<String, Double>> zeilen = FXCollections.observableArrayList();
    private final VBox view;
    private final HBox knoepfe;

    /**
     * @param personenLesen  liefert {Passagiere, Anteil 1. Klasse, Aussteigeanteil} oder null (ohne Personen)
     * @param personenSetzen übernimmt diese Werte aus einem Vorschlag (oder null)
     */
    public MengenEditor(Stage owner, Anlage anlage, Map<String, Double> start, String mengenTitel,
            Supplier<double[]> personenLesen, Consumer<double[]> personenSetzen) {
        this.anlage = anlage;
        start.forEach((g, m) -> zeilen.add(new AbstractMap.SimpleEntry<>(g, m)));
        TableView<Map.Entry<String, Double>> tabelle = new TableView<>(zeilen);
        Spalten.einrichten(tabelle, "mengen");
        tabelle.setPrefHeight(200);
        TableColumn<Map.Entry<String, Double>, Gut> symbol = new TableColumn<>("");
        symbol.setCellValueFactory(c -> new SimpleObjectProperty<>(anlage.gut(c.getValue().getKey())));
        symbol.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Gut item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : Symbole.gut(item, 16));
            }
        });
        symbol.setPrefWidth(32);
        symbol.setMinWidth(32);
        symbol.setMaxWidth(32);
        TableColumn<Map.Entry<String, Double>, String> gut = new TableColumn<>("Gut");
        gut.setCellValueFactory(c -> new SimpleStringProperty(anlage.gutName(c.getValue().getKey())));
        gut.setPrefWidth(160);
        TableColumn<Map.Entry<String, Double>, String> menge = new TableColumn<>(mengenTitel);
        menge.setCellValueFactory(c -> new SimpleStringProperty(ObjektDialoge.zahl(ganz(c.getValue().getValue()))));
        javafx.scene.control.Label mengeKopf = new javafx.scene.control.Label(mengenTitel);
        if (mengenTitel.contains("M/h")) {
            mengeKopf.setTooltip(new javafx.scene.control.Tooltip("M/h = je Modellstunde"));
        }
        menge.setText(null);
        menge.setGraphic(mengeKopf);
        menge.setPrefWidth(150);
        tabelle.getColumns().setAll(List.of(symbol, gut, menge));
        tabelle.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        ComboBox<Gut> gutBox = ObjektDialoge.gutCombo(anlage, false);
        // nur ganze Einheiten, mindestens 1 (Anwender 14.10.2026)
        TextField mengeFeld = LogistikFeldRow.ziffern(new TextField("1"), 4);
        tabelle.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> {
            if (b != null) {
                gutBox.setValue(anlage.gut(b.getKey()));
                mengeFeld.setText(ObjektDialoge.zahl(ganz(b.getValue())));
            }
        });
        Button hinzu = new Button("Hinzufügen/Ändern");
        hinzu.setOnAction(e -> {
            if (gutBox.getValue() == null) {
                return;
            }
            setze(gutBox.getValue().getId(), ganz(ObjektDialoge.parse(mengeFeld.getText(), 1)));
        });
        Button weg = new Button("Entfernen");
        weg.setOnAction(e -> zeilen.remove(tabelle.getSelectionModel().getSelectedItem()));

        Button vorschlag = new Button("Vorschlag…");
        vorschlag.setOnAction(e -> {
            Vorschlag v = waehle(owner, anlage);
            if (v != null) {
                zeilen.clear();
                v.gueter().forEach(this::setze);
                if (personenSetzen != null) {
                    personenSetzen.accept(new double[]{v.passagiere(), v.ersteKlasse(), v.aussteigen()});
                }
            }
        });
        Button speichern = new Button("Als Vorschlag speichern…");
        speichern.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog();
            if (owner != null) {
                d.initOwner(owner);
            }
            d.setTitle("Als Vorschlag speichern");
            d.setHeaderText("Die eingetragenen Personen- und Gütermengen als eigenen Vorschlag speichern.");
            d.setContentText("Name:");
            d.showAndWait().map(String::trim).filter(n -> !n.isEmpty()).ifPresent(n -> {
                double[] p = personenLesen != null ? personenLesen.get() : new double[]{0, 0.1, 0.3};
                speichere(new Vorschlag(n, p[0], p[1], p[2], getMengen(), true));
            });
        });
        view = new VBox(8, tabelle, new HBox(6, gutBox, mengeFeld, hinzu, weg));
        VBox.setVgrow(tabelle, Priority.ALWAYS);
        // Die Vorschlagsknöpfe gelten für Personen und Güter; der Aufrufer platziert sie (linksbündig darunter).
        knoepfe = new HBox(8, vorschlag, speichern);
    }

    private void setze(String gutId, double menge) {
        zeilen.removeIf(x -> x.getKey().equals(gutId));
        zeilen.add(new AbstractMap.SimpleEntry<>(gutId, ganz(menge)));
    }

    /** Ganze Einheiten, mindestens 1 (Anwender 14.10.2026: Bedarf fällt nie unter 1 Einheit). */
    static double ganz(double v) {
        return v > 0 ? Math.max(1, Math.round(v)) : 0;
    }

    public Node getView() {
        return view;
    }

    /** "Vorschlag…" und "Als Vorschlag speichern…" (für Personen und Güter). */
    public Node getKnoepfe() {
        return knoepfe;
    }

    public Map<String, Double> getMengen() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : zeilen) {
            m.put(e.getKey(), ganz(e.getValue()));
        }
        return m;
    }

    // ------------------------------------------------------------------ Vorschläge

    /** Eingebaute Vorschläge: Stadt (Grundbedarf), Dorf mit 50 %, Ländlich mit 10 % davon. */
    public static List<Vorschlag> eingebaut(Anlage a) {
        Map<String, Double> stadt = new LinkedHashMap<>();
        for (Gut g : a.getGueter().values()) {
            boolean stufe0 = List.of("fish", "meat", "vegetables", "bricks", "cement", "fuel", "planks")
                    .contains(g.getId());
            if (g.getStadtBereich() != null && stufe0) {
                stadt.put(g.getId(), g.getStadtBereich() == StadtBereich.GEWERBE ? 2.0 : 1.0);
            }
        }
        List<Vorschlag> l = new ArrayList<>();
        // Personen/Güter (Anwender 11.10.2026): Stadt 60/7, Dorf 30/4, Ländlich 6/2 - Güter in Reihenfolge
        // Fisch, Fleisch, Gemüse, Bretter, Zement, Treibstoff, Ziegel; Mengen Dorf 50 %, Ländlich 10 %.
        l.add(new Vorschlag("Vorschlag für eine Stadt", 60, 0.10, 0.5, erste(stadt, 7), false));
        l.add(new Vorschlag("Vorschlag für ein Dorf", 30, 0.10, 0.3, skaliert(erste(stadt, 4), 0.5), false));
        l.add(new Vorschlag("Vorschlag Ländlich", 6, 0.10, 0.2, skaliert(erste(stadt, 2), 0.1), false));
        return l;
    }

    private static Map<String, Double> erste(Map<String, Double> m, int n) {
        Map<String, Double> r = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (r.size() < n) {
                r.put(e.getKey(), e.getValue());
            }
        }
        return r;
    }

    private static Map<String, Double> skaliert(Map<String, Double> m, double f) {
        Map<String, Double> r = new LinkedHashMap<>();
        m.forEach((g, v) -> r.put(g, ganz(v * f)));
        return r;
    }

    /** Eigene, gespeicherte Vorschläge. Format je Zeile: Name TAB Passagiere TAB 1.Kl TAB Aussteigen TAB gut=menge,... */
    public static List<Vorschlag> eigene() {
        List<Vorschlag> l = new ArrayList<>();
        for (String zeile : AppSettings.getInstance().getMengenVorschlaege().split("\n")) {
            String[] t = zeile.split("\t", -1);
            if (t.length < 5 || t[0].isBlank()) {
                continue;
            }
            Map<String, Double> g = new LinkedHashMap<>();
            for (String paar : t[4].split(",")) {
                int i = paar.indexOf('=');
                if (i > 0) {
                    g.put(paar.substring(0, i), zahl(paar.substring(i + 1)));
                }
            }
            l.add(new Vorschlag(t[0], zahl(t[1]), zahl(t[2]), zahl(t[3]), g, true));
        }
        return l;
    }

    private static double zahl(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static void speicherAlle(List<Vorschlag> l) {
        StringBuilder sb = new StringBuilder();
        for (Vorschlag v : l) {
            StringBuilder g = new StringBuilder();
            v.gueter().forEach((k, m) -> g.append(g.length() == 0 ? "" : ",").append(k).append('=')
                    .append(String.format(Locale.ROOT, "%s", m)));
            sb.append(v.name().replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(v.passagiere()).append('\t').append(v.ersteKlasse()).append('\t')
                    .append(v.aussteigen()).append('\t').append(g).append('\n');
        }
        AppSettings.getInstance().setMengenVorschlaege(sb.toString());
    }

    private static void speichere(Vorschlag neu) {
        List<Vorschlag> l = eigene();
        l.removeIf(v -> v.name().equals(neu.name()));
        l.add(neu);
        speicherAlle(l);
        new Alert(Alert.AlertType.INFORMATION, "Vorschlag „" + neu.name() + "“ gespeichert.").showAndWait();
    }

    /** Auswahlliste "Vorschlag": eingebaute und eigene Vorschläge; eigene lassen sich löschen. */
    public static Vorschlag waehle(Stage owner, Anlage a) {
        ListView<Vorschlag> liste = new ListView<>();
        Runnable fuellen = () -> {
            liste.getItems().setAll(eingebaut(a));
            liste.getItems().addAll(eigene());
        };
        fuellen.run();
        liste.setPrefSize(380, 220);
        liste.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Vorschlag v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : (v.eigen() ? "★ " : "") + v.name() + "  ("
                        + ObjektDialoge.zahl(v.passagiere()) + " Passagiere/h, " + v.gueter().size() + " Güter)");
            }
        });
        liste.getSelectionModel().selectFirst();
        Button loeschen = new Button("Eigenen Vorschlag löschen");
        loeschen.setOnAction(e -> {
            Vorschlag v = liste.getSelectionModel().getSelectedItem();
            if (v != null && v.eigen()) {
                List<Vorschlag> l = eigene();
                l.removeIf(x -> x.name().equals(v.name()));
                speicherAlle(l);
                fuellen.run();
            }
        });
        Label hinweis = new Label("Übernimmt Personenwerte und Gütermengen (ersetzt die Tabelle). ★ = eigener Vorschlag.");
        hinweis.setWrapText(true);
        Dialog<ButtonType> d = new Dialog<>();
        Fenster.merken(d, "mengen"); // Größe merken (Anwender 22.10.2026)
        if (owner != null) {
            d.initOwner(owner);
            if (owner.getScene() != null) {
                d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
            }
        }
        d.setTitle("Vorschlag");
        VBox inhalt = new VBox(8, liste, hinweis, loeschen);
        inhalt.setPadding(new Insets(10));
        d.getDialogPane().setContent(inhalt);
        DialogKnoepfe.setze(d.getDialogPane());
        liste.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ((Button) d.getDialogPane().lookupButton(DialogKnoepfe.SPEICHERN)).fire();
            }
        });
        if (d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != DialogKnoepfe.SPEICHERN) {
            return null;
        }
        return liste.getSelectionModel().getSelectedItem();
    }
}
