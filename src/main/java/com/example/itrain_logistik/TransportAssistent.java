package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.BetriebVorlage;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Kreislauf;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Linie;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Tf3Daten;
import com.example.itrain_logistik.kern.Uebergabe;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * „Transport festlegen“ als Assistent in Schritten (Anwender 31.10.2026): 1. Was (Gut oder Reisende) – wer stellt es
 * her, wer braucht es; 2. Herkunft (vorhandener Betrieb, neuer Betrieb nach Rezept, Lager, Schattenbahnhof);
 * 3. Verbrauch und Menge; 4. Transport von / über / nach zwischen Bahnhöfen mit Wahl der Ladegleise „Zur Lieferung
 * auswählen“ (direkte Verbindung = eine iTrain-Zugroute fährt beide Bahnhöfe an); 5. Zusammenfassung. Erst
 * „Übernehmen“ ändert die Anlage.
 */
final class TransportAssistent {

    private static final String REISENDE = "Reisende";
    private static final String NICHT_AUF_ANLAGE = "Nicht auf der Anlage (liefert bzw. nimmt automatisch)";
    private static final String DIREKT = "– direkt –";

    /** Herkunft bzw. Verbrauch: vorhandenes Objekt, neuer Betrieb nach Vorlage oder Schattenbahnhof (beide null). */
    private record Wahl(String text, AnlagenObjekt objekt, BetriebVorlage vorlage, boolean schatten) {
        @Override
        public String toString() {
            return text;
        }
    }

    private final Stage owner;
    private final Anlage a;
    private final Runnable geaendert;

    private final ComboBox<String> gut = new ComboBox<>();
    private final Map<String, String> gutIds = new LinkedHashMap<>();
    private final ComboBox<Wahl> herkunft = new ComboBox<>();
    private final ComboBox<String> herkunftBahnhof = new ComboBox<>();
    private final ComboBox<Wahl> verbrauch = new ComboBox<>();
    private final ComboBox<String> verbrauchBahnhof = new ComboBox<>();
    private final Spinner<Integer> menge = new Spinner<>(1, 9999, 5);
    private final ComboBox<String> von = new ComboBox<>();
    private final ComboBox<String> ueber = new ComboBox<>();
    private final ComboBox<String> nach = new ComboBox<>();
    private final Map<String, Station> stationen = new LinkedHashMap<>();
    private final Map<Gleis, CheckBox> gleisWahl = new LinkedHashMap<>();
    private final VBox gleisBox = new VBox(4);
    private final Label verbindung = new Label();

    TransportAssistent(Stage owner, Anlage a, Runnable geaendert) {
        this.owner = owner;
        this.a = a;
        this.geaendert = geaendert;
    }

    private String gutId() {
        return gutIds.get(gut.getValue());
    }

    private boolean reisende() {
        return REISENDE.equals(gut.getValue());
    }

    // ================================================================ Ablauf

    boolean zeige() {
        gutIds.put(REISENDE, null);
        a.getGueter().values().stream().sorted(java.util.Comparator.comparing(Gut::getName))
                .forEach(g -> gutIds.put(g.getName(), g.getId()));
        gut.getItems().setAll(gutIds.keySet());
        for (ComboBox<?> c : List.of(gut, herkunft, herkunftBahnhof, verbrauch, verbrauchBahnhof, von, ueber, nach)) {
            c.setMaxWidth(Double.MAX_VALUE);
            c.setPrefWidth(460);
        }
        menge.setEditable(true);

        Label titel = new Label();
        titel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        VBox seite = new VBox(10);
        seite.setPrefSize(640, 400);
        List<java.util.function.Supplier<Node>> seiten = List.of(this::seiteGut, this::seiteHerkunft,
                this::seiteVerbrauch, this::seiteTransport, this::seiteZusammenfassung);
        List<String> titelListe = List.of("1. Was wird transportiert?", "2. Woher kommt es?",
                "3. Wer verbraucht es, wie viel?", "4. Transport zwischen den Bahnhöfen", "5. Zusammenfassung");
        int[] nr = {0};
        Button zurueck = new Button("< Zurück");
        Button weiter = new Button("Weiter >");
        Label fehlt = new Label();
        fehlt.setStyle("-fx-text-fill: #d04040;");
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        HBox navi = new HBox(8, fehlt, platz, zurueck, weiter);
        navi.setAlignment(Pos.CENTER_RIGHT);
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent – Transport festlegen");
        d.setResizable(true);
        ButtonType ok = new ButtonType("Übernehmen", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE), ok);
        Node okKnopf = d.getDialogPane().lookupButton(ok);
        Runnable zeigen = () -> {
            titel.setText(titelListe.get(nr[0]));
            seite.getChildren().setAll(seiten.get(nr[0]).get());
            zurueck.setDisable(nr[0] == 0);
            weiter.setDisable(nr[0] == seiten.size() - 1);
            okKnopf.setDisable(nr[0] != seiten.size() - 1);
            fehlt.setText("");
        };
        zurueck.setOnAction(e -> {
            nr[0]--;
            zeigen.run();
        });
        weiter.setOnAction(e -> {
            String grund = fehltNoch(nr[0]);
            if (grund != null) {
                fehlt.setText(grund);
                return;
            }
            nr[0]++;
            zeigen.run();
        });
        VBox inhalt = new VBox(10, titel, new Separator(), seite, new Separator(), navi);
        inhalt.setPadding(new Insets(6));
        d.getDialogPane().setContent(inhalt);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        gut.setValue(REISENDE);
        zeigen.run();
        if (d.showAndWait().orElse(null) != ok) {
            return false;
        }
        List<String> offen = uebernehmen();
        geaendert.run();
        if (!offen.isEmpty()) {
            Texte.zeige(owner, "Verkehrs-Assistent", new Texte.Abschnitt("Noch zu erledigen",
                    "- " + String.join("\n- ", offen)), null, "OK");
        }
        return true;
    }

    /** Was in einem Schritt noch fehlt (null = weiter möglich). */
    private String fehltNoch(int schritt) {
        return switch (schritt) {
            case 0 -> gut.getValue() == null ? "Bitte Reisende oder ein Gut wählen." : null;
            case 1 -> herkunft.getValue() == null ? "Bitte wählen, woher es kommt." : null;
            case 2 -> verbrauch.getValue() == null ? "Bitte wählen, wer es verbraucht." : null;
            case 3 -> von.getValue() == null || nach.getValue() == null ? "Bitte „Transport von“ und „Transport nach“ wählen."
                    : von.getValue().equals(nach.getValue()) ? "Von und nach sind derselbe Bahnhof – kein Transport nötig."
                    : null;
            default -> null;
        };
    }

    // ================================================================ 1. Was

    private Node seiteGut() {
        VBox info = new VBox(4);
        Runnable zeigen = () -> {
            info.getChildren().clear();
            if (reisende()) {
                info.getChildren().add(text("Reisende kommen aus Stadt, Dorf oder Ländlich (über deren Bahnhof) oder "
                        + "von außerhalb über den Schattenbahnhof und fahren zu einem anderen Bahnhof."));
                return;
            }
            String g = gutId();
            List<String> her = hersteller(g);
            List<String> brauch = new ArrayList<>();
            for (AnlagenObjekt o : Kreislauf.abnehmer(a, g, null)) {
                brauch.add(o.getAnzeigeName());
            }
            info.getChildren().add(fett("Hergestellt von: " + (her.isEmpty() ? "noch nirgends" : String.join(", ", her))));
            info.getChildren().add(fett("Benötigt von: " + (brauch.isEmpty() ? "noch nirgends" : String.join(", ", brauch))));
            if (brauch.isEmpty()) {
                info.getChildren().add(rot("Bedarf muss eingestellt werden – das geht im Schritt 3."));
            }
            List<String> vorlagen = new ArrayList<>();
            for (BetriebVorlage v : Tf3Daten.vorlagen()) {
                if (v.ausgangslager().containsKey(g)) {
                    vorlagen.add(v.name());
                }
            }
            if (!vorlagen.isEmpty()) {
                info.getChildren().add(text("Rezepte, die es herstellen: " + String.join(", ", vorlagen)));
            }
        };
        gut.setOnAction(e -> zeigen.run());
        zeigen.run();
        return new VBox(10, zeile("Was:", gut), info);
    }

    // ================================================================ 2. Woher

    private Node seiteHerkunft() {
        List<Wahl> l = new ArrayList<>();
        String g = gutId();
        if (reisende()) {
            for (Ort o : a.orte()) {
                if (!a.stationenFuer(o.getId()).isEmpty()) {
                    l.add(new Wahl("Stadt/Dorf/Ländlich: " + o.getAnzeigeName(), o, null, false));
                }
            }
        } else {
            for (Betrieb b : a.betriebe()) {
                if (b.ausgangsGueter().contains(g)) {
                    l.add(new Wahl("Vorhanden: " + b.getAnzeigeName() + bahnhofText(b), b, null, false));
                }
            }
            for (Lager lg : a.lager()) {
                double n = lg.getBestand().getOrDefault(g, 0.0);
                if (n >= 1) {
                    l.add(new Wahl("Aus Lager: " + lg.getAnzeigeName() + " (" + Symbole.zahl(Math.floor(n)) + " vorrätig)",
                            lg, null, false));
                }
            }
            for (BetriebVorlage v : vorlagenSortiert()) {
                if (v.ausgangslager().containsKey(g)) {
                    l.add(new Wahl("Neuer Betrieb: " + v.name() + ErforderlichDialog.rolle(a, v), null, v, false));
                }
            }
        }
        l.add(new Wahl("Vom Schattenbahnhof (kommt von außerhalb der Anlage)", null, null, true));
        Wahl alt = herkunft.getValue();
        herkunft.getItems().setAll(l);
        herkunft.setValue(l.contains(alt) ? alt : null);
        VBox info = new VBox(4);
        HBox bahnhofZeile = zeile("Liegt am Bahnhof:", herkunftBahnhof);
        Runnable zeigen = () -> {
            info.getChildren().clear();
            Wahl w = herkunft.getValue();
            boolean neu = w != null && w.vorlage() != null;
            bahnhofZeile.setVisible(neu);
            bahnhofZeile.setManaged(neu);
            if (neu) {
                bahnhofAuswahl(herkunftBahnhof, true);
                info.getChildren().add(fett("Rezept: " + ErforderlichDialog.rolle(a, w.vorlage()).substring(3)));
                for (String e : w.vorlage().eingangslager().keySet()) {
                    List<String> her = hersteller(e);
                    info.getChildren().add(her.isEmpty()
                            ? rot("  " + a.gutName(e) + ": noch kein Lieferant – nach dem Übernehmen in „Kreislauf "
                            + "prüfen“ ergänzen")
                            : text("  " + a.gutName(e) + ": kommt von " + String.join(", ", her)));
                }
            } else if (w != null && w.objekt() instanceof Betrieb b && b.isVirtuell()) {
                info.getChildren().add(text("„" + b.getAnzeigeName() + "“ liefert, ohne auf der Anlage zu sein."));
            }
        };
        herkunft.setOnAction(e -> zeigen.run());
        zeigen.run();
        return new VBox(10, zeile("Herkunft:", herkunft), bahnhofZeile, info);
    }

    // ================================================================ 3. Verbrauch

    private Node seiteVerbrauch() {
        List<Wahl> l = new ArrayList<>();
        String g = gutId();
        if (reisende()) {
            for (Ort o : a.orte()) {
                if (!a.stationenFuer(o.getId()).isEmpty() && (herkunft.getValue() == null
                        || herkunft.getValue().objekt() != o)) {
                    l.add(new Wahl("Stadt/Dorf/Ländlich: " + o.getAnzeigeName(), o, null, false));
                }
            }
        } else {
            for (AnlagenObjekt o : Kreislauf.abnehmer(a, g, null)) {
                if (o instanceof Station) {
                    continue;
                }
                l.add(new Wahl("Vorhanden: " + o.getAnzeigeName() + bahnhofText(o), o, null, false));
            }
            for (Ort o : a.orte()) {
                if (o.getBedarf().getOrDefault(g, 0.0) <= 0) {
                    l.add(new Wahl("Bedarf eintragen bei: " + o.getAnzeigeName() + bahnhofText(o), o, null, false));
                }
            }
            for (Lager lg : a.lager()) {
                if (lg.nimmt(a.gut(g)) && (herkunft.getValue() == null || herkunft.getValue().objekt() != lg)) {
                    l.add(new Wahl("Einlagern in: " + lg.getAnzeigeName() + bahnhofText(lg), lg, null, false));
                }
            }
            for (BetriebVorlage v : vorlagenSortiert()) {
                if (v.eingangslager().containsKey(g)) {
                    l.add(new Wahl("Neuer Betrieb: " + v.name() + ErforderlichDialog.rolle(a, v), null, v, false));
                }
            }
        }
        l.add(new Wahl("Schattenbahnhof (verlässt die Anlage)", null, null, true));
        Wahl alt = verbrauch.getValue();
        verbrauch.getItems().setAll(l);
        verbrauch.setValue(l.contains(alt) ? alt : null);
        HBox bahnhofZeile = zeile("Liegt am Bahnhof:", verbrauchBahnhof);
        Runnable zeigen = () -> {
            boolean neu = verbrauch.getValue() != null && verbrauch.getValue().vorlage() != null;
            bahnhofZeile.setVisible(neu);
            bahnhofZeile.setManaged(neu);
            if (neu) {
                bahnhofAuswahl(verbrauchBahnhof, true);
            }
        };
        verbrauch.setOnAction(e -> zeigen.run());
        zeigen.run();
        return new VBox(10, zeile("Verbraucher:", verbrauch), bahnhofZeile,
                zeile(reisende() ? "Reisende je Modellstunde:" : "Menge je Modellstunde:", menge));
    }

    // ================================================================ 4. Transport

    private Node seiteTransport() {
        stationen.clear();
        List<String> vonListe = bahnhoefeFuer(herkunft.getValue(), herkunftBahnhof.getValue());
        List<String> nachListe = bahnhoefeFuer(verbrauch.getValue(), verbrauchBahnhof.getValue());
        setze(von, vonListe);
        setze(nach, nachListe);
        VBox hinweise = new VBox(4);
        if (vonListe.isEmpty()) {
            hinweise.getChildren().add(rot("Die Herkunft hängt an keinem Bahnhof mit " + gleisArtText() + " – bitte "
                    + "zuerst mit einem Bahnhof verbinden („Mit Station verbinden…“)."));
        }
        if (nachListe.isEmpty()) {
            hinweise.getChildren().add(rot("Der Verbraucher hängt an keinem Bahnhof mit " + gleisArtText() + " – bitte "
                    + "zuerst mit einem Bahnhof verbinden."));
        }
        verbindung.setWrapText(true);
        Runnable pruefen = () -> {
            verbindungPruefen();
            gleiseZeigen();
        };
        von.setOnAction(e -> pruefen.run());
        nach.setOnAction(e -> pruefen.run());
        ueber.setOnAction(e -> gleiseZeigen());
        pruefen.run();
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(gleisBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(170);
        VBox v = new VBox(8, zeile("Transport von:", von), zeile("Transport über:", ueber),
                zeile("Transport nach:", nach), verbindung, hinweise);
        if (!reisende()) {
            v.getChildren().addAll(fett("Zur Lieferung auswählen (Ladegleise):"), sp);
        }
        return v;
    }

    private String gleisArtText() {
        return reisende() ? "Bahnsteig" : "Ladegleis";
    }

    /** Prüft über die iTrain-Zugrouten, ob es eine direkte Verbindung gibt; sonst Bahnhöfe dazwischen anbieten. */
    private void verbindungPruefen() {
        Station s1 = stationen.get(von.getValue());
        Station s2 = stationen.get(nach.getValue());
        List<String> l = new ArrayList<>();
        l.add(DIREKT);
        if (s1 == null || s2 == null) {
            ueber.getItems().setAll(l);
            ueber.setValue(DIREKT);
            verbindung.setText("");
            return;
        }
        List<Linie> linien = a.getLinien();
        String direkt = route(s1, s2);
        List<String> zwischen = new ArrayList<>();
        for (Station x : a.stationen()) {
            if (x == s1 || x == s2 || verbund(x).contains(s1) || verbund(x).contains(s2)
                    || (!reisende() && x.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty())
                    || (reisende() && x.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty())) {
                continue;
            }
            if (linien.isEmpty() || route(s1, x) != null && route(x, s2) != null) {
                String name = x.getAnzeigeName() + (a.istAussenwelt(x) ? " (Schattenbahnhof, lagert um)" : "");
                stationen.put(name, x);
                if (a.istAussenwelt(x)) {
                    zwischen.add(0, name);
                } else {
                    zwischen.add(name);
                }
            }
        }
        if (!reisende()) {
            l.addAll(zwischen);
        }
        String alt = ueber.getValue();
        ueber.getItems().setAll(l);
        if (linien.isEmpty()) {
            verbindung.setText("In iTrain sind keine Zugrouten angelegt – die Verbindung kann nicht geprüft werden.");
            ueber.setValue(l.contains(alt) ? alt : DIREKT);
        } else if (direkt != null) {
            verbindung.setText("✔ Direkte Verbindung über die Zugroute „" + direkt + "“.");
            verbindung.setStyle("-fx-text-fill: #2e8b2e;");
            ueber.setValue(l.contains(alt) ? alt : DIREKT);
        } else if (!zwischen.isEmpty() && !reisende()) {
            verbindung.setText("Keine Zugroute fährt beide Bahnhöfe an. Umsteigen über einen Bahnhof dazwischen ist "
                    + "möglich (oben gewählt).");
            verbindung.setStyle("-fx-text-fill: #d07a00;");
            ueber.setValue(l.contains(alt) && !DIREKT.equals(alt) ? alt : zwischen.get(0));
        } else {
            verbindung.setText("Keine Zugroute verbindet die beiden Bahnhöfe – bitte in iTrain eine Zugroute anlegen.");
            verbindung.setStyle("-fx-text-fill: #d04040;");
            ueber.setValue(DIREKT);
        }
    }

    /** Name einer Zugroute, die beide Stationen (samt Bahnhof/Güterbahnhof) anfährt, sonst null. */
    private String route(Station s1, Station s2) {
        Set<String> b1 = bloecke(s1);
        Set<String> b2 = bloecke(s2);
        for (Linie l : a.getLinien()) {
            boolean e1 = l.getBlockNamen().stream().anyMatch(b1::contains);
            boolean e2 = l.getBlockNamen().stream().anyMatch(b2::contains);
            if (e1 && e2) {
                return l.getName();
            }
        }
        return null;
    }

    private List<Station> verbund(Station s) {
        List<Station> l = new ArrayList<>(List.of(s));
        Station p = a.gehoertZu(s);
        if (p != null) {
            l.add(p);
        }
        Station g = a.angedockterGueterbahnhof(s);
        if (g != null) {
            l.add(g);
        }
        return l;
    }

    private Set<String> bloecke(Station s) {
        Set<String> b = new LinkedHashSet<>();
        for (Station x : verbund(s)) {
            for (Gleis g : x.getGleise()) {
                if (g.getBlockName() != null) {
                    b.add(g.getBlockName());
                }
            }
        }
        return b;
    }

    /** Kästchen je Ladegleis an „von“, „über“ und „nach“; vorgewählt sind Gleise, die das Gut schon annehmen. */
    private void gleiseZeigen() {
        gleisBox.getChildren().clear();
        gleisWahl.clear();
        if (reisende()) {
            return;
        }
        String g = gutId();
        Map<String, Station> teile = new LinkedHashMap<>();
        teile.put("Laden in", stationen.get(von.getValue()));
        if (ueber.getValue() != null && !DIREKT.equals(ueber.getValue())) {
            teile.put("Umladen in", stationen.get(ueber.getValue()));
        }
        teile.put("Entladen in", stationen.get(nach.getValue()));
        teile.forEach((was, s) -> {
            if (s == null) {
                return;
            }
            Station mitLade = mitLadegleisen(s);
            gleisBox.getChildren().add(fett(was + " „" + mitLade.getAnzeigeName() + "“:"));
            List<Gleis> lade = mitLade.gleiseDerArt(GleisArt.LADEGLEIS);
            if (lade.isEmpty()) {
                gleisBox.getChildren().add(rot("  keine Ladegleise – im Stationsdialog ein Gleis als Ladegleis wählen"));
            }
            boolean einer = lade.stream().anyMatch(gl -> gl.erlaubt(g));
            for (Gleis gl : lade) {
                CheckBox cb = new CheckBox(gl.getAnzeigeName() + (gl.getGueterAuswahl().isEmpty() ? " (alle Güter)" : ""));
                cb.setSelected(gl.erlaubt(g) || !einer && gl == lade.get(0));
                gleisWahl.put(gl, cb);
                cb.setPadding(new Insets(0, 0, 0, 16));
                gleisBox.getChildren().add(cb);
            }
        });
    }

    private Station mitLadegleisen(Station s) {
        if (s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty()) {
            Station gbf = a.angedockterGueterbahnhof(s);
            if (gbf != null) {
                return gbf;
            }
        }
        return s;
    }

    /** Bahnhöfe, an denen Herkunft bzw. Verbraucher hängt (mit Bahnsteig bzw. Ladegleis). */
    private List<String> bahnhoefeFuer(Wahl w, String neuerBahnhof) {
        List<String> l = new ArrayList<>();
        if (w == null) {
            return l;
        }
        List<Station> st = new ArrayList<>();
        if (w.schatten()) {
            for (Station s : a.stationen()) {
                if (a.istAussenwelt(s) && (reisende() ? !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()
                        : !s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty())) {
                    st.add(s);
                }
            }
        } else if (w.vorlage() != null) {
            Station s = stationen.get(neuerBahnhof);
            if (s == null) {
                for (Station x : a.stationen()) {
                    if (x.getAnzeigeName().equals(neuerBahnhof)) {
                        s = x;
                    }
                }
            }
            if (s != null) {
                st.add(s);
            }
        } else {
            st.addAll(reisende() ? a.stationenFuer(w.objekt().getId()) : a.gueterStationenFuer(w.objekt().getId()));
            if (reisende()) {
                st.removeIf(s -> s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty());
            }
        }
        for (Station s : st) {
            stationen.put(s.getAnzeigeName(), s);
            l.add(s.getAnzeigeName());
        }
        return l;
    }

    /** Auswahl der Bahnhöfe mit Ladegleisen (bzw. mit angedocktem Güterbahnhof) für einen neuen Betrieb. */
    private void bahnhofAuswahl(ComboBox<String> c, boolean nichtAufAnlage) {
        List<String> l = new ArrayList<>();
        // alle Bahnhöfe außer dem Schattenbahnhof (Anwender 09.11.2026); fehlt ein Ladegleis, fragt „Übernehmen“
        for (Station s : a.stationenFuerBetrieb()) {
            l.add(s.getAnzeigeName());
        }
        if (nichtAufAnlage) {
            l.add(NICHT_AUF_ANLAGE);
        }
        String alt = c.getValue();
        c.getItems().setAll(l);
        c.setValue(l.contains(alt) ? alt : l.get(0));
    }

    // ================================================================ 5. Zusammenfassung

    private Node seiteZusammenfassung() {
        VBox v = new VBox(4);
        for (String s : plan()) {
            v.getChildren().add(text("• " + s));
        }
        v.getChildren().add(new Separator());
        v.getChildren().add(text("„Übernehmen“ trägt das in die Anlage ein. Was danach noch fehlt, zeigt der Assistent an."));
        return v;
    }

    /** Beschreibung dessen, was „Übernehmen“ tut. */
    private List<String> plan() {
        List<String> l = new ArrayList<>();
        String was = gut.getValue();
        int m = menge.getValue();
        Wahl h = herkunft.getValue();
        Wahl z = verbrauch.getValue();
        l.add(m + " " + (reisende() ? "Reisende" : was) + " je Modellstunde");
        if (h.schatten()) {
            l.add("Herkunft: Schattenbahnhof stellt " + m + " je Modellstunde bereit");
        } else if (h.vorlage() != null) {
            l.add("Neuer Betrieb „" + h.vorlage().name() + "“ " + (NICHT_AUF_ANLAGE.equals(herkunftBahnhof.getValue())
                    ? "(nicht auf der Anlage)" : "am Bahnhof „" + herkunftBahnhof.getValue() + "“"));
        } else {
            l.add("Herkunft: " + h.objekt().getAnzeigeName());
        }
        if (z.schatten()) {
            l.add("Verbrauch: Schattenbahnhof nimmt es ab");
        } else if (z.vorlage() != null) {
            l.add("Neuer Betrieb „" + z.vorlage().name() + "“ " + (NICHT_AUF_ANLAGE.equals(verbrauchBahnhof.getValue())
                    ? "(nicht auf der Anlage)" : "am Bahnhof „" + verbrauchBahnhof.getValue() + "“"));
        } else if (z.objekt() instanceof Ort o && !reisende()) {
            l.add("Bedarf bei „" + o.getAnzeigeName() + "“: " + m + " je Modellstunde");
        } else {
            l.add("Verbrauch: " + z.objekt().getAnzeigeName());
        }
        l.add("Transport von „" + von.getValue() + "“" + (DIREKT.equals(ueber.getValue()) || ueber.getValue() == null
                ? "" : " über „" + ueber.getValue() + "“") + " nach „" + nach.getValue() + "“");
        List<String> gl = new ArrayList<>();
        gleisWahl.forEach((g, cb) -> {
            if (cb.isSelected()) {
                gl.add(g.getAnzeigeName());
            }
        });
        if (!gl.isEmpty()) {
            l.add("Ladegleise für " + was + ": " + String.join(", ", gl));
        }
        return l;
    }

    // ================================================================ Übernehmen

    private List<String> uebernehmen() {
        List<String> offen = new ArrayList<>();
        String g = gutId();
        int m = menge.getValue();
        Wahl h = herkunft.getValue();
        Wahl z = verbrauch.getValue();
        Station vonSt = stationen.get(von.getValue());
        Station nachSt = stationen.get(nach.getValue());
        // Herkunft
        if (h.schatten()) {
            if (reisende()) {
                for (Gleis gl : vonSt.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                    gl.setPassagiereProStunde(Math.max(gl.getPassagiereProStunde(), m));
                    break;
                }
            } else {
                Gleis gl = ersteGewaehlte(mitLadegleisen(vonSt));
                if (gl != null) {
                    gl.getAngebot().merge(g, (double) m, Math::max);
                }
            }
        } else if (h.vorlage() != null) {
            Betrieb b = neuerBetrieb(h.vorlage(), herkunftBahnhof.getValue());
            raten(b, g, m, true);
            if (b.isVirtuell() && vonSt == null) {
                offen.add("„" + b.getAnzeigeName() + "“ liefert automatisch, ohne Zug.");
            }
        } else if (h.objekt() instanceof Ort o) {
            o.setPassagiereProStunde(Math.max(o.getPassagiereProStunde(), m));
        } else if (h.objekt() instanceof Betrieb b) {
            raten(b, g, m, true);
        }
        // Verbrauch
        if (z.vorlage() != null) {
            Betrieb b = neuerBetrieb(z.vorlage(), verbrauchBahnhof.getValue());
            raten(b, g, m, false);
        } else if (z.objekt() instanceof Ort o) {
            if (reisende()) {
                if (!o.isAnkommendUnbegrenzt()) {
                    o.setAnkommendProStunde(Math.max(o.getAnkommendProStunde(), m));
                }
            } else {
                o.getBedarf().merge(g, (double) m, Math::max);
            }
        }
        // Gleise: Gut erlauben, Laden bzw. Entladen einschalten
        Station ueberSt = ueber.getValue() == null || DIREKT.equals(ueber.getValue()) ? null : stationen.get(ueber.getValue());
        gleisWahl.forEach((gl, cb) -> {
            if (!cb.isSelected()) {
                return;
            }
            if (!gl.getGueterAuswahl().isEmpty()) {
                gl.getGueterAuswahl().add(g);
            }
        });
        if (vonSt != null) {
            setzeLadenEntladen(mitLadegleisen(vonSt), true, false);
        }
        if (nachSt != null) {
            setzeLadenEntladen(mitLadegleisen(nachSt), false, true);
        }
        if (ueberSt != null) {
            Station u = mitLadegleisen(ueberSt);
            setzeLadenEntladen(u, true, true);
            if (a.istAussenwelt(u)) {
                u.getUmlagern().add(g);
            } else if (a.einzugsbereich(u).stream().noneMatch(o -> o instanceof Lager)) {
                offen.add("Am Bahnhof „" + u.getAnzeigeName() + "“ wird ein Lager gebraucht, damit " + gut.getValue()
                        + " dort auf den Weitertransport warten kann.");
            }
        }
        if (!reisende()) {
            if (Kreislauf.abnehmer(a, g, null).isEmpty() && !z.schatten() && !(z.objekt() instanceof Lager)) {
                offen.add("Für " + gut.getValue() + " ist noch kein Bedarf eingestellt.");
            }
        }
        return offen;
    }

    private void setzeLadenEntladen(Station s, boolean laden, boolean entladen) {
        if (s == null || reisende()) {
            return;
        }
        gleisWahl.forEach((gl, cb) -> {
            if (cb.isSelected() && s.getGleise().contains(gl)) {
                if (laden) {
                    gl.setLaden(true);
                }
                if (entladen) {
                    gl.setEntladen(true);
                }
            }
        });
    }

    private Gleis ersteGewaehlte(Station s) {
        for (Map.Entry<Gleis, CheckBox> e : gleisWahl.entrySet()) {
            if (e.getValue().isSelected() && s.getGleise().contains(e.getKey())) {
                return e.getKey();
            }
        }
        List<Gleis> lade = s.gleiseDerArt(GleisArt.LADEGLEIS);
        return lade.isEmpty() ? null : lade.get(0);
    }

    /** Legt einen Betrieb an einem Bahnhof an (oder „nicht auf der Anlage“). */
    private Betrieb neuerBetrieb(BetriebVorlage v, String bahnhof) {
        Station s = null;
        for (Station x : a.stationen()) {
            if (x.getAnzeigeName().equals(bahnhof)) {
                s = x;
            }
        }
        if (s != null) {
            s = VerkehrsAssistent.ladegleisSicherstellen(owner, a, s);
        }
        Betrieb b = a.neuerBetrieb(v, 0, 0);
        if (s != null) {
            a.platziereNeu(b, s.getX() + Anlage.RASTER_X, s.getY());
            s.getEinzugsbereich().add(b.getId());
        } else {
            a.platziereNeu(b, a.infrastruktur().getX() + Anlage.RASTER_X, a.infrastruktur().getY() + Anlage.RASTER_Y);
            b.setVirtuell(true);
        }
        return b;
    }

    /** Ausführungen je Modellstunde so, dass die Menge hergestellt bzw. verbraucht wird. */
    private static void raten(Betrieb b, String gutId, int m, boolean ausgang) {
        if (gutId == null) {
            return;
        }
        int jeLauf = b.getRezepte().stream().map(r -> ausgang ? r.getAusgang() : r.getEingang())
                .filter(x -> x.containsKey(gutId)).mapToInt(x -> x.get(gutId)).findFirst().orElse(0);
        if (jeLauf > 0) {
            b.setRateProStunde(Math.max(b.getRateProStunde(), Math.ceil((double) m / jeLauf)));
        }
    }

    // ================================================================ Hilfen

    private List<String> hersteller(String g) {
        List<String> l = new ArrayList<>();
        for (Betrieb b : a.betriebe()) {
            if (b.ausgangsGueter().contains(g)) {
                l.add(b.getAnzeigeName());
            }
        }
        for (Uebergabe u : a.uebergaben()) {
            if (u.getAngebot().contains(g)) {
                l.add(u.getAnzeigeName());
            }
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s) && s.getGleise().stream().anyMatch(x -> x.getAngebot().getOrDefault(g, 0.0) > 0)) {
                l.add(s.getAnzeigeName());
            }
        }
        return l;
    }

    private String bahnhofText(AnlagenObjekt o) {
        List<String> n = new ArrayList<>();
        for (Station s : reisende() ? a.stationenFuer(o.getId()) : a.gueterStationenFuer(o.getId())) {
            n.add(s.getAnzeigeName());
        }
        return n.isEmpty() ? " (an keinem Bahnhof)" : " (an " + String.join(", ", n) + ")";
    }

    private static List<BetriebVorlage> vorlagenSortiert() {
        List<BetriebVorlage> l = new ArrayList<>(Tf3Daten.vorlagen());
        l.sort(java.util.Comparator.comparing(BetriebVorlage::name));
        return l;
    }

    private static void setze(ComboBox<String> c, List<String> werte) {
        String alt = c.getValue();
        c.getItems().setAll(werte);
        c.setValue(werte.contains(alt) ? alt : werte.isEmpty() ? null : werte.get(0));
    }

    private static HBox zeile(String text, Node feld) {
        Label l = new Label(text);
        l.setMinWidth(170);
        HBox h = new HBox(8, l, feld);
        h.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(feld, Priority.ALWAYS);
        return h;
    }

    private static Label text(String s) {
        Label l = new Label(s);
        l.setWrapText(true);
        return l;
    }

    private static Label fett(String s) {
        Label l = text(s);
        l.setStyle("-fx-font-weight: bold;");
        return l;
    }

    private static Label rot(String s) {
        Label l = text(s);
        l.setStyle("-fx-text-fill: #d04040; -fx-font-weight: bold;");
        return l;
    }
}
