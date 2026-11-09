package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Station;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verkehrs-Assistent, Schritt „Einrichtung Bahnhöfe“ (Anwender 27.10.2026, vorher Einrichtung nach dem Import):
 * je iTrain-Bahnhof Name, Art und je Block Bahnsteig, Ladegleis oder „Vorerst nichts“. „Weiter“ bleibt gesperrt,
 * solange ein Bahnhof die Art „Anderes“ hat; dann blinkt „Art einstellen!“. Bahnsteig und Ladegleis im selben
 * Bahnhof ergeben einen Personen- und einen angedockten Güterbahnhof ({@link Anlage#bahnhofEinstellen}).
 * Funktioniert für noch nicht eingebaute Bahnhöfe (Vorgaben) und für schon vorhandene Stationen.
 */
final class BahnhoefeDialog {

    private static final List<ObjektArt> ARTEN = List.of(ObjektArt.BAHNHOF, ObjektArt.HALTEPUNKT,
            ObjektArt.GUETERBAHNHOF, ObjektArt.SCHATTENBAHNHOF, ObjektArt.ANSCHLUSS, ObjektArt.ANDERES);
    static final String BAHNSTEIG = "Bahnsteig";
    static final String LADEGLEIS = "Ladegleis";
    static final String NICHTS = "Vorerst nichts";
    /** Bahnsteig und Ladegleis zugleich (Anwender 09.11.2026, v. a. Schattenbahnhof). */
    static final String BEIDE = "Bahnsteig und Ladegleis";
    static final String AUF_ANLAGE = "Auf der Anlage";
    static final String NEUES_MODUL = "Neues Modul…";

    /** Ergebnis je Bahnhof; {@code station} null = noch nicht eingebaut. Wert null in {@code wahl} = vorerst nichts. */
    record Ergebnis(String itrainName, Station station, String name, ObjektArt art, Map<String, GleisArt> wahl,
                    String modul, Set<String> beide) {
        boolean mitBahnsteig() {
            return wahl.containsValue(GleisArt.BAHNSTEIG) || !beide.isEmpty();
        }

        boolean mitLadegleis() {
            return wahl.containsValue(GleisArt.LADEGLEIS) || !beide.isEmpty();
        }
    }

    private record Zeile(String itrainName, Station station, TextField name, ComboBox<ObjektArt> art,
                         Map<String, ChoiceBox<String>> gleise, ComboBox<String> lage) {
    }

    private BahnhoefeDialog() {
    }

    /**
     * @param daten iTrain-Bahnhof -> Blöcke, {@code typen} iTrain-Bahnhof -> iTrain-Typ
     * @return die Einstellungen oder null (Abbruch)
     */
    static List<Ergebnis> zeige(Window owner, Anlage anlage, Map<String, List<String>> daten, Map<String, String> typen) {
        if (daten.isEmpty()) {
            return null;
        }
        Texte.Abschnitt kopfText = Texte.dialog("assistent_bahnhoefe");
        Node kopf = Texte.darstellen(kopfText.text(), 760);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setPadding(new Insets(6, 0, 6, 0));
        grid.addRow(0, fett("Bahnhof in iTrain"), fett("Name und Art"), fett("Gleise"));
        List<Zeile> zeilen = new ArrayList<>();
        // „Ist dieser Bahnhof auf der Anlage oder ein Modul?“ (Anwender 09.11.2026): gemeinsame Liste für alle Zeilen
        javafx.collections.ObservableList<String> lagen = FXCollections.observableArrayList(AUF_ANLAGE);
        anlage.module().forEach(m -> lagen.add(m.getAnzeigeName()));
        lagen.add(NEUES_MODUL);
        List<Runnable> pruefer = new ArrayList<>();
        Runnable[] allesPruefen = new Runnable[1];
        int r = 1;
        for (Map.Entry<String, List<String>> e : daten.entrySet()) {
            String itrain = e.getKey();
            Station s = anlage.stationFuerItrain(itrain);
            if (s != null && !itrain.equals(s.getItrainName())) {
                s = null;
            }
            Station gbf = s != null ? anlage.angedockterGueterbahnhof(s) : null;
            Anlage.BahnhofVorgabe alt = s == null ? anlage.getBahnhofVorgaben().get(itrain) : null;

            TextField name = new TextField(s != null ? s.getAnzeigeName()
                    : alt != null && alt.name() != null ? alt.name() : itrain);
            name.setPrefColumnCount(16);
            ComboBox<ObjektArt> art = new ComboBox<>(FXCollections.observableArrayList(ARTEN));
            art.setConverter(new StringConverter<>() {
                @Override
                public String toString(ObjektArt a) {
                    return a == null ? "" : a.getLabel();
                }

                @Override
                public ObjektArt fromString(String x) {
                    return null;
                }
            });
            art.setValue(s != null ? s.getArt() : alt != null && alt.art() != null ? alt.art()
                    : Anlage.artFuerItrainTyp(typen.get(itrain)));
            Label warnung = new Label("Art einstellen!");
            warnung.setStyle("-fx-text-fill: #d03030; -fx-font-weight: bold;");
            FadeTransition blinken = new FadeTransition(Duration.millis(450), warnung);
            blinken.setFromValue(1.0);
            blinken.setToValue(0.15);
            blinken.setAutoReverse(true);
            blinken.setCycleCount(Animation.INDEFINITE);
            Label hinweis = new Label();
            hinweis.setWrapText(true);
            hinweis.setMaxWidth(230);
            hinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");

            FlowPane gleise = new FlowPane(12, 6);
            gleise.setPrefWrapLength(430);
            Map<String, ChoiceBox<String>> wahl = new LinkedHashMap<>();
            Set<String> bloecke = new LinkedHashSet<>(e.getValue());
            if (s != null) {
                s.getGleise().forEach(g -> bloecke.add(g.getBlockName()));
            }
            for (String block : bloecke) {
                ChoiceBox<String> cb = new ChoiceBox<>(FXCollections.observableArrayList(BAHNSTEIG, LADEGLEIS, BEIDE,
                        NICHTS));
                cb.setValue(beide(s, gbf, alt, block) ? BEIDE : text(aktuell(s, gbf, alt, block)));
                wahl.put(block, cb);
                HBox g = new HBox(4, new Label(block), cb);
                g.setAlignment(Pos.CENTER_LEFT);
                gleise.getChildren().add(g);
            }
            Runnable pruefen = () -> {
                boolean anderes = art.getValue() == ObjektArt.ANDERES || art.getValue() == null;
                warnung.setVisible(anderes);
                warnung.setManaged(anderes);
                if (anderes) {
                    blinken.play();
                } else {
                    blinken.stop();
                    warnung.setOpacity(1);
                }
                boolean steig = wahl.values().stream().anyMatch(c -> BAHNSTEIG.equals(c.getValue())
                        || BEIDE.equals(c.getValue()));
                boolean lade = wahl.values().stream().anyMatch(c -> LADEGLEIS.equals(c.getValue())
                        || BEIDE.equals(c.getValue()));
                ObjektArt a = art.getValue();
                if (steig && lade && (a == ObjektArt.BAHNHOF || a == ObjektArt.HALTEPUNKT
                        || a == ObjektArt.SCHATTENBAHNHOF)) {
                    hinweis.setText("Bahnsteige und Ladegleise: Es entstehen ein " + a.getLabel()
                            + " und ein angedockter Güterbahnhof.");
                } else if (lade && !steig && (a == ObjektArt.BAHNHOF || a == ObjektArt.HALTEPUNKT)) {
                    hinweis.setText("Nur Ladegleise: Sie kommen in einen angedockten Güterbahnhof.");
                } else {
                    hinweis.setText("");
                }
                hinweis.setVisible(!hinweis.getText().isEmpty());
                hinweis.setManaged(hinweis.isVisible());
            };
            art.valueProperty().addListener((o, a1, a2) -> {
                // Wechsel auf Güterbahnhof: gewählte Bahnsteige werden Ladegleise
                if (a2 == ObjektArt.GUETERBAHNHOF) {
                    wahl.values().forEach(c -> {
                        if (BAHNSTEIG.equals(c.getValue())) {
                            c.setValue(LADEGLEIS);
                        }
                    });
                }
                allesPruefen[0].run();
            });
            for (ChoiceBox<String> cb : wahl.values()) {
                cb.valueProperty().addListener((o, v1, v2) -> {
                    // Bahnsteig im Güterbahnhof: der Bahnhof wird Personenbahnhof, Ladegleise kommen in einen Gbf
                    if ((BAHNSTEIG.equals(v2) || BEIDE.equals(v2)) && art.getValue() == ObjektArt.GUETERBAHNHOF) {
                        art.setValue(ObjektArt.BAHNHOF);
                    }
                    allesPruefen[0].run();
                });
            }
            pruefer.add(pruefen);

            Label titel = new Label(itrain + (typen.get(itrain) == null ? "" : "\niTrain: "
                    + Anlage.itrainTypText(typen.get(itrain))) + (s != null ? "\n(schon eingebaut)" : ""));
            titel.setMinWidth(Region.USE_PREF_SIZE);
            ComboBox<String> lage = new ComboBox<>(lagen);
            com.example.itrain_logistik.kern.Modul modulJetzt = s != null ? anlage.modulFuer(s) : null;
            lage.setValue(modulJetzt != null ? modulJetzt.getAnzeigeName() : AUF_ANLAGE);
            lage.setMaxWidth(Double.MAX_VALUE);
            lage.valueProperty().addListener((o, alt2, neu2) -> {
                if (!NEUES_MODUL.equals(neu2)) {
                    return;
                }
                javafx.scene.control.TextInputDialog t = new javafx.scene.control.TextInputDialog(
                        "Modul " + (lagen.size() - 1));
                t.initOwner(lage.getScene() != null ? lage.getScene().getWindow() : owner);
                t.setTitle("Neues Modul");
                t.setHeaderText("Name des Moduls, zu dem „" + name.getText() + "“ gehört");
                String n = t.showAndWait().map(String::trim).orElse("");
                javafx.application.Platform.runLater(() -> {
                    if (n.isEmpty() || n.equals(AUF_ANLAGE) || n.equals(NEUES_MODUL)) {
                        lage.setValue(alt2);
                        return;
                    }
                    if (!lagen.contains(n)) {
                        lagen.add(lagen.size() - 1, n);
                    }
                    lage.setValue(n);
                });
            });
            Label lageText = new Label("Ist dieser Bahnhof auf der Anlage oder ein Modul?");
            lageText.setStyle("-fx-font-size: 11px;");
            lageText.setWrapText(true);
            lageText.setMaxWidth(250);
            VBox mitte = new VBox(5, name, art, warnung, hinweis, lageText, lage);
            grid.add(titel, 0, r);
            grid.add(mitte, 1, r);
            grid.add(gleise, 2, r);
            GridPane.setValignment(titel, VPos.TOP);
            GridPane.setValignment(mitte, VPos.TOP);
            r++;
            grid.add(new Separator(), 0, r, 3, 1);
            r++;
            zeilen.add(new Zeile(itrain, s, name, art, wahl, lage));
        }
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(520, 110.0 * daten.size() + 40));
        VBox inhalt = new VBox(12, kopf, scroll);
        inhalt.setPadding(new Insets(10));

        Dialog<ButtonType> d = new Dialog<>();
        Fenster.merken(d, "bahnhoefe");
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent – Einrichtung Bahnhöfe");
        d.setHeaderText(kopfText.titel());
        ButtonType abbruch = new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType weiter = new ButtonType("Weiter", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(abbruch, weiter);
        d.getDialogPane().setContent(inhalt);
        d.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        Node weiterKnopf = d.getDialogPane().lookupButton(weiter);
        allesPruefen[0] = () -> {
            pruefer.forEach(Runnable::run);
            boolean offen = zeilen.stream().anyMatch(z -> z.art().getValue() == null
                    || z.art().getValue() == ObjektArt.ANDERES);
            weiterKnopf.setDisable(offen);
        };
        allesPruefen[0].run();
        if (d.showAndWait().orElse(abbruch) != weiter) {
            return null;
        }
        List<Ergebnis> erg = new ArrayList<>();
        for (Zeile z : zeilen) {
            Map<String, GleisArt> w = new LinkedHashMap<>();
            Set<String> bd = new LinkedHashSet<>();
            z.gleise().forEach((block, cb) -> {
                if (BEIDE.equals(cb.getValue())) {
                    bd.add(block);
                    w.put(block, GleisArt.BAHNSTEIG);
                } else {
                    w.put(block, BAHNSTEIG.equals(cb.getValue()) ? GleisArt.BAHNSTEIG
                            : LADEGLEIS.equals(cb.getValue()) ? GleisArt.LADEGLEIS : null);
                }
            });
            String l = z.lage().getValue();
            erg.add(new Ergebnis(z.itrainName(), z.station(), z.name().getText().trim(), z.art().getValue(), w,
                    l == null || AUF_ANLAGE.equals(l) || NEUES_MODUL.equals(l) ? null : l, bd));
        }
        return erg;
    }

    /** Gleisart eines Blocks: vorhandene Station samt Gbf, sonst Vorgabe, sonst „Vorerst nichts“. */
    private static GleisArt aktuell(Station s, Station gbf, Anlage.BahnhofVorgabe alt, String block) {
        for (Station st : new Station[]{s, gbf}) {
            if (st == null) {
                continue;
            }
            for (Gleis g : st.getGleise()) {
                if (block.equals(g.getBlockName())) {
                    return g.getArt();
                }
            }
        }
        if (s != null) {
            return null;
        }
        return alt != null && alt.bahnsteige() != null ? alt.artFuer(block) : null;
    }

    /** Ist der Block schon Bahnsteig und Ladegleis (Station + Gbf) bzw. in der Vorgabe in beiden Listen? */
    private static boolean beide(Station s, Station gbf, Anlage.BahnhofVorgabe alt, String block) {
        if (s != null) {
            boolean steig = false;
            boolean lade = false;
            for (Station st : new Station[]{s, gbf}) {
                if (st == null) {
                    continue;
                }
                for (Gleis g : st.getGleise()) {
                    if (block.equals(g.getBlockName())) {
                        steig |= g.getArt() == GleisArt.BAHNSTEIG;
                        lade |= g.getArt() == GleisArt.LADEGLEIS;
                    }
                }
            }
            return steig && lade;
        }
        return alt != null && alt.ladegleise().contains(block) && alt.bahnsteige() != null
                && alt.bahnsteige().contains(block);
    }

    private static String text(GleisArt a) {
        return a == GleisArt.BAHNSTEIG ? BAHNSTEIG : a == GleisArt.LADEGLEIS ? LADEGLEIS : NICHTS;
    }

    private static Label fett(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }
}
