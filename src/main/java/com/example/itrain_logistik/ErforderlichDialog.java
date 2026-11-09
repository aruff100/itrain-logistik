package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.BetriebVorlage;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Kreislauf;
import com.example.itrain_logistik.kern.KreislaufPruefung;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Tf3Daten;
import com.example.itrain_logistik.kern.Uebergabe;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dialog "Zusätzlich erforderlich" (ersetzt den früheren Frage-für-Frage-Assistenten): Nach dem
 * Anlegen oder Ändern eines Betriebs zeigt er als Baum, welche Güter dem Betrieb noch fehlen
 * (Eingang ohne Lieferanten) bzw. wohin seine Erzeugnisse gehen könnten (Ausgang ohne Abnehmer).
 * Je Zeile wählt der Anwender: neuen Betrieb anlegen, "wird immer geliefert", Betrieb "liefert
 * immer, ohne auf der Anlage zu sein", Übergabe, Bedarf eines Orts oder offen lassen. Unter
 * einem neuen Betrieb erscheinen dessen eigene offene Enden. Erst "Speichern" ändert die
 * Anlage; danach werden die Mengen der neuen Verbindungen automatisch abgeglichen.
 */
public final class ErforderlichDialog {

    private static final int MAX_TIEFE = 6;

    private ErforderlichDialog() {
    }

    private enum Art {
        NEU, VIRTUELL, IMMER, UEBERGABE, ORT, OFFEN,
        /** vorhandene Erzeuger auf den Bedarf anheben (Mengenabgleich) */
        ANPASSEN,
        /** Reisende (Anwender 14.10.2026): neue Stationen mit gewählten Gleisen bzw. Ankommende unbegrenzt */
        SBH_NEU, BAHNHOF_FUER, HALT_FUER, NEUES_DORF, UNBEGRENZT,
        /** Ort mit einem vorhandenen Bahnhof/Haltepunkt verbinden (Anwender 20.10.2026) */
        VERBINDEN,
        /** Station der Art "Anderes" zum Bahnhof machen (Anwender 20.10.2026) */
        ALS_BAHNHOF
    }

    private record Option(Art art, BetriebVorlage vorlage, Ort ort, Station station) {
        Option(Art art, BetriebVorlage vorlage, Ort ort) {
            this(art, vorlage, ort, null);
        }

        boolean brauchtGleise() {
            return art == Art.SBH_NEU || art == Art.BAHNHOF_FUER || art == Art.HALT_FUER || art == Art.NEUES_DORF;
        }

        @Override
        public String toString() {
            return switch (art) {
                case NEU -> "Neuer Betrieb: " + vorlage.name();
                case VIRTUELL -> vorlage.name() + " – liefert immer, ohne auf der Anlage zu sein";
                case IMMER -> "Wird immer geliefert";
                case UEBERGABE -> "Übergabe (Außenwelt)";
                case ORT -> "Bedarf bei " + ort.getAnzeigeName();
                case OFFEN -> "Offen lassen";
                case ANPASSEN -> "Vorhandene Erzeuger anpassen (mehr herstellen)";
                case SBH_NEU -> "Neuer Schattenbahnhof (Gleise wählen)";
                case BAHNHOF_FUER -> "Neuer Bahnhof für " + ort.getAnzeigeName() + " (Gleise wählen)";
                case HALT_FUER -> "Neuer Haltepunkt für " + ort.getAnzeigeName() + " (Gleise wählen)";
                case NEUES_DORF -> "Neues Dorf mit Haltepunkt (Gleise wählen)";
                case UNBEGRENZT -> "Ankommende bei " + ort.getAnzeigeName() + " auf „Unbegrenzt“";
                case ALS_BAHNHOF -> "„" + station.getAnzeigeName() + "“ zum Bahnhof machen (bisher „Anderes“"
                        + (station.gleiseDerArt(com.example.itrain_logistik.kern.GleisArt.LADEGLEIS).isEmpty() ? ")"
                        : "; Ladegleise kommen in einen Güterbahnhof)");
                case VERBINDEN -> ort.getAnzeigeName() + " mit „" + station.getAnzeigeName() + "“ verbinden ("
                        + station.getArt().getLabel() + ", " + station.gleiseDerArt(
                                com.example.itrain_logistik.kern.GleisArt.BAHNSTEIG).size() + " Bahnsteige)";
            };
        }
    }

    /** Eine Zeile: ein Gut, das einem (vorhandenen oder geplanten) Betrieb fehlt oder übrig bleibt. */
    private static final class Knoten {
        /** Gut, oder null bei Reisenden. */
        final String gutId;
        final boolean ausgang;
        final int tiefe;
        /** Vorhandener Betrieb oder Ort (Wurzel) oder null, dann gehört die Zeile zum geplanten Elternknoten. */
        final AnlagenObjekt besitzer;
        /** Vorlagen auf dem Weg von der Wurzel (gegen Kreise). */
        final Set<String> weg;
        /** Reisende: fehlende Menge je Modellstunde (für einen neuen Schattenbahnhof). */
        final double menge;
        final ComboBox<Option> wahl = new ComboBox<>();
        final javafx.scene.control.MenuButton gleise = new javafx.scene.control.MenuButton("Gleise wählen…");
        final Set<String> gewaehlteGleise = new LinkedHashSet<>();
        final HBox ansicht;

        Knoten(Anlage a, String gutId, boolean ausgang, int tiefe, AnlagenObjekt besitzer, Set<String> weg) {
            this(a, gutId, ausgang, tiefe, besitzer, weg, optionen(a, gutId, ausgang, besitzer instanceof Ort), null, 0);
        }

        Knoten(Anlage a, String gutId, boolean ausgang, int tiefe, AnlagenObjekt besitzer, Set<String> weg,
                List<Option> optionen, String text, double menge) {
            this.gutId = gutId;
            this.ausgang = ausgang;
            this.tiefe = tiefe;
            this.besitzer = besitzer;
            this.weg = weg;
            this.menge = menge;
            wahl.getItems().setAll(optionen);
            // Vorgabe "Offen lassen": erst die Auswahl des Anwenders ändert etwas (Knopf "Weiter", 14.10.2026)
            wahl.setValue(optionen.stream().filter(o -> o.art() == Art.OFFEN).findFirst().orElse(optionen.get(0)));
            // Fehlt einem Eingang jeder Lieferant, ist der passende neue Betrieb vorgewählt (Anwender 31.10.2026):
            // der, dessen eigene Eingänge auf der Anlage am ehesten schon geliefert werden.
            // Nicht mehr vorgewählt (Anwender 09.11.2026: „fehlende Betriebe selbsttätig anlegen geht nicht“): der
            // passende neue Betrieb steht nur direkt unter „Offen lassen“, angelegt wird nur, was der Anwender wählt.
            if (!ausgang && gutId != null && tiefe <= 1 && optionen.stream().noneMatch(o -> o.art() == Art.ANPASSEN)) {
                Option beste = besterNeuerBetrieb(a, optionen, gutId);
                if (beste != null) {
                    java.util.List<Option> l = new java.util.ArrayList<>(wahl.getItems());
                    l.remove(beste);
                    int offen = l.indexOf(wahl.getValue());
                    Option offenLassen = wahl.getValue();
                    l.add(offen + 1, beste);
                    wahl.getItems().setAll(l);
                    wahl.setValue(offenLassen);
                }
            }
            wahl.setPrefWidth(640);
            // "Wird immer geliefert" mit dem farbigen Symbol des Guts
            wahl.setCellFactory(lv -> new OptionZelle(a, gutId));
            wahl.setButtonCell(new OptionZelle(a, gutId));
            Label beschriftung = new Label(text != null ? text : (ausgang ? "▲ " + a.gutName(gutId) + " geht an:"
                    : "▼ " + a.gutName(gutId) + " kommt von:"));
            beschriftung.setMinWidth(180);
            beschriftung.setWrapText(true);
            beschriftung.setMaxWidth(260);
            Region platz = new Region();
            HBox.setHgrow(platz, Priority.ALWAYS);
            // Gleisauswahl für neue Stationen: freie Blöcke der Anlage
            for (String b : a.freieBloecke()) {
                javafx.scene.control.CheckMenuItem mi = new javafx.scene.control.CheckMenuItem(b);
                mi.setOnAction(e -> {
                    if (mi.isSelected()) {
                        gewaehlteGleise.add(b);
                    } else {
                        gewaehlteGleise.remove(b);
                    }
                    gleise.setText(gewaehlteGleise.isEmpty() ? "Gleise wählen…" : "Gleise: " + String.join(", ",
                            gewaehlteGleise));
                });
                gleise.getItems().add(mi);
            }
            if (gleise.getItems().isEmpty()) {
                javafx.scene.control.MenuItem leer = new javafx.scene.control.MenuItem("Keine freien Blöcke");
                leer.setDisable(true);
                gleise.getItems().add(leer);
            }
            gleise.setMaxWidth(220);
            Runnable sichtbar = () -> {
                boolean b = wahl.getValue() != null && wahl.getValue().brauchtGleise();
                gleise.setVisible(b);
                gleise.setManaged(b);
            };
            wahl.valueProperty().addListener((obs, x, y) -> sichtbar.run());
            sichtbar.run();
            javafx.scene.Node symbol = gutId != null ? Symbole.gut(a.gut(gutId), 18)
                    : Symbole.person(18);
            ansicht = new HBox(8, symbol, beschriftung, platz, wahl, gleise);
            ansicht.setAlignment(Pos.CENTER_LEFT);
        }
    }

    /** Neuer Betrieb mit den wenigsten Eingängen ohne Lieferant auf der Anlage; bei Gleichstand der mit weniger Eingängen. */
    private static Option besterNeuerBetrieb(Anlage a, List<Option> optionen, String gutId) {
        Option beste = null;
        int[] bestWert = null;
        for (Option o : optionen) {
            if (o.art() != Art.NEU) {
                continue;
            }
            int fehlend = 0;
            for (String g : o.vorlage().eingangslager().keySet()) {
                if (Kreislauf.lieferanten(a, g, null).isEmpty() && !Kreislauf.uebergabeLiefert(a, g)) {
                    fehlend++;
                }
            }
            int[] wert = {fehlend, o.vorlage().eingangslager().size()};
            if (bestWert == null || wert[0] < bestWert[0] || wert[0] == bestWert[0] && wert[1] < bestWert[1]) {
                beste = o;
                bestWert = wert;
            }
        }
        return beste;
    }

    /** „ – Benötigt: Dünger · Liefert: Gemüse und Getreide“ (Anwender 31.10.2026). */
    static String rolle(Anlage a, BetriebVorlage v) {
        String ein = aufzaehlung(a, v.eingangslager().keySet());
        String aus = aufzaehlung(a, v.ausgangslager().keySet());
        return " – Benötigt: " + (ein.isEmpty() ? "nichts" : ein) + " · Liefert: " + (aus.isEmpty() ? "nichts" : aus);
    }

    private static String aufzaehlung(Anlage a, java.util.Collection<String> ids) {
        List<String> namen = ids.stream().map(a::gutName).toList();
        if (namen.size() <= 1) {
            return String.join("", namen);
        }
        return String.join(", ", namen.subList(0, namen.size() - 1)) + " und " + namen.get(namen.size() - 1);
    }

    private static final class OptionZelle extends javafx.scene.control.ListCell<Option> {
        private final Anlage a;
        private final String gutId;

        OptionZelle(Anlage a, String gutId) {
            this.a = a;
            this.gutId = gutId;
        }

        @Override
        protected void updateItem(Option item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.toString() + (item.vorlage() != null ? rolle(a, item.vorlage()) : ""));
                setTooltip(new javafx.scene.control.Tooltip(getText()));
                setGraphic(item.art() == Art.IMMER ? Symbole.gut(a.gut(gutId), 16) : null);
            }
        }
    }

    private static List<Option> optionen(Anlage a, String gutId, boolean ausgang, boolean fuerOrt) {
        List<Option> l = new ArrayList<>();
        if (!ausgang && !Kreislauf.lieferanten(a, gutId, null).isEmpty()) {
            l.add(new Option(Art.ANPASSEN, null, null)); // Lieferant da, aber zu wenig
        }
        List<BetriebVorlage> vorlagen = new ArrayList<>(Tf3Daten.vorlagen());
        vorlagen.sort((x, y) -> x.name().compareTo(y.name()));
        if (ausgang) {
            for (Ort o : a.orte()) {
                l.add(new Option(Art.ORT, null, o));
            }
            for (BetriebVorlage v : vorlagen) {
                if (v.eingangslager().containsKey(gutId)) {
                    l.add(new Option(Art.NEU, v, null));
                }
            }
            l.add(new Option(Art.UEBERGABE, null, null));
        } else {
            for (BetriebVorlage v : vorlagen) {
                if (v.ausgangslager().containsKey(gutId)) {
                    l.add(new Option(Art.NEU, v, null));
                }
            }
            if (!fuerOrt) {
                l.add(new Option(Art.IMMER, null, null));
            }
            for (BetriebVorlage v : vorlagen) {
                if (v.ausgangslager().containsKey(gutId)) {
                    l.add(new Option(Art.VIRTUELL, v, null));
                }
            }
            l.add(new Option(Art.UEBERGABE, null, null));
        }
        l.add(new Option(Art.OFFEN, null, null));
        return l;
    }

    /** Auswahl für eine Zeile mit Reisenden (Ort ohne Bahnsteig, ohne Ziel oder mit zu wenig Gegenstück). */
    private static List<Option> reisendeOptionen(Anlage a, Ort o, boolean eigenerBahnsteigFehlt, boolean abfahrt) {
        List<Option> l = new ArrayList<>();
        if (eigenerBahnsteigFehlt) {
            for (Station s : KreislaufPruefung.anderesMitBahnsteig(a, o)) {
                l.add(new Option(Art.ALS_BAHNHOF, null, o, s));
            }
            for (Station s : KreislaufPruefung.bahnhoefeZumVerbinden(a, o)) {
                l.add(new Option(Art.VERBINDEN, null, o, s));
            }
            l.add(new Option(Art.BAHNHOF_FUER, null, o));
            l.add(new Option(Art.HALT_FUER, null, o));
        } else {
            l.add(new Option(Art.SBH_NEU, null, null));
            for (Ort o2 : a.orte()) {
                if (o2 != o && !KreislaufPruefung.hatBahnsteig(a, o2)) {
                    l.add(new Option(Art.BAHNHOF_FUER, null, o2));
                    l.add(new Option(Art.HALT_FUER, null, o2));
                }
            }
            l.add(new Option(Art.NEUES_DORF, null, null));
            if (abfahrt) {
                for (Ort o2 : a.orte()) {
                    if (o2 != o && KreislaufPruefung.hatBahnsteig(a, o2) && !o2.isAnkommendUnbegrenzt()) {
                        l.add(new Option(Art.UNBEGRENZT, null, o2));
                    }
                }
            }
        }
        l.add(new Option(Art.OFFEN, null, null));
        return l;
    }

    /**
     * Vorgabe: Eingänge der ersten Ebene als neuer Betrieb, tiefer "wird immer geliefert";
     * Ausgänge gehen an den ersten Ort, sonst bleiben sie offen.
     */
    private static Option vorgabe(List<Option> l, boolean ausgang, int tiefe) {
        Art ziel = ausgang ? Art.ORT : (tiefe <= 1 ? Art.NEU : Art.IMMER);
        for (Option o : l) {
            if (o.art() == ziel) {
                return o;
            }
        }
        for (Option o : l) {
            if (o.art() == Art.OFFEN) {
                return o;
            }
        }
        return l.get(l.size() - 1);
    }

    /**
     * Zeigt den Dialog für die offenen Enden der Betriebe und übernimmt die Auswahl.
     *
     * @return true, wenn die Anlage geändert wurde
     */
    public static boolean zeige(Stage owner, Anlage a, List<Betrieb> betriebe) {
        return zeige(owner, a, betriebe, false);
    }

    /**
     * @param immerZeigen "Kreislauf prüfen": auch ohne offene Enden zeigen, mit der Tabelle aller Eingänge und
     *                    ihrer Lieferanten ("Geliefert von")
     */
    public static boolean zeige(Stage owner, Anlage a, List<Betrieb> betriebe, boolean immerZeigen) {
        TreeItem<Object> wurzel = new TreeItem<>("Wurzel");
        for (Betrieb b : betriebe) {
            List<Kreislauf.OffenesEnde> enden = Kreislauf.offeneEnden(a, b);
            if (enden.isEmpty()) {
                continue;
            }
            TreeItem<Object> betriebItem = betriebe.size() == 1 ? wurzel : new TreeItem<>(b.getAnzeigeName());
            if (betriebItem != wurzel) {
                betriebItem.setExpanded(true);
                wurzel.getChildren().add(betriebItem);
            }
            Set<String> weg = new LinkedHashSet<>();
            if (b.getVorlageId() != null) {
                weg.add(b.getVorlageId());
            }
            // oben, was benötigt wird (Eingänge), unten, was rausgeht (Ausgänge)
            enden = new ArrayList<>(enden);
            enden.sort((x, y) -> Boolean.compare(x.ausgang(), y.ausgang()));
            for (Kreislauf.OffenesEnde e : enden) {
                betriebItem.getChildren().add(item(a, new Knoten(a, e.gutId(), e.ausgang(), 1, b, weg)));
            }
        }
        // Bedarf der Orte ohne (ausreichenden) Lieferanten, Betriebseingänge mit zu wenig Erzeugung, Reisende
        KreislaufPruefung.Ergebnis ergebnis = KreislaufPruefung.pruefe(a);
        Map<AnlagenObjekt, TreeItem<Object>> gruppen = new java.util.LinkedHashMap<>();
        java.util.function.Function<AnlagenObjekt, TreeItem<Object>> gruppe = o -> gruppen.computeIfAbsent(o, x -> {
            TreeItem<Object> t = new TreeItem<>(x.getAnzeigeName());
            t.setExpanded(true);
            wurzel.getChildren().add(t);
            return t;
        });
        Set<String> schonImBaum = new java.util.HashSet<>();
        for (KreislaufPruefung.Zeile z : ergebnis.zeilen()) {
            if (z.ok()) {
                continue;
            }
            switch (z.abschnitt()) {
                case ORTSBEDARF -> {
                    if (!immerZeigen) {
                        continue;
                    }
                    gruppe.apply(z.objekt()).getChildren().add(item(a, new Knoten(a, z.gutId(), false, 1, z.objekt(),
                            new LinkedHashSet<>(), optionen(a, z.gutId(), false, true),
                            "▼ " + a.gutName(z.gutId()) + " " + KreislaufPruefung.zahl(z.menge()) + " M/h – " + z.fehlt()
                                    + ", kommt von:", 0)));
                }
                case EINGAENGE -> {
                    // ohne Lieferant steht der Eingang schon als offenes Ende im Baum; hier nur "zu wenig"
                    if (immerZeigen && !"kein Lieferant".equals(z.fehlt()) && schonImBaum.add(z.gutId())
                            && z.objekt() instanceof Betrieb bz && betriebe.contains(bz)) {
                        gruppe.apply(z.objekt()).getChildren().add(item(a, new Knoten(a, z.gutId(), false, 1,
                                z.objekt(), new LinkedHashSet<>(), optionen(a, z.gutId(), false, false),
                                "▼ " + a.gutName(z.gutId()) + " – " + z.fehlt() + ", kommt von:", 0)));
                    }
                }
                case ABFAHRT, ANKUNFT -> {
                    if (!immerZeigen || !(z.objekt() instanceof Ort o)) {
                        continue;
                    }
                    boolean ohne = !KreislaufPruefung.hatBahnsteig(a, o);
                    // fehlt schon bei den Abreisenden ein Gegenstück, hilft dieselbe Auswahl auch den Ankommenden
                    boolean abfahrtFehlt = ergebnis.abschnitt(KreislaufPruefung.Abschnitt.ABFAHRT).stream()
                            .anyMatch(x -> x.objekt() == o && !x.ok());
                    if (z.abschnitt() == KreislaufPruefung.Abschnitt.ANKUNFT && (ohne || abfahrtFehlt)) {
                        continue;
                    }
                    double fehlend = Double.isInfinite(z.menge()) ? o.getPassagiereProStunde() : z.menge();
                    gruppe.apply(o).getChildren().add(item(a, new Knoten(a, null,
                            z.abschnitt() == KreislaufPruefung.Abschnitt.ABFAHRT, 1, o, new LinkedHashSet<>(),
                            reisendeOptionen(a, o, ohne, z.abschnitt() == KreislaufPruefung.Abschnitt.ABFAHRT),
                            (z.abschnitt() == KreislaufPruefung.Abschnitt.ABFAHRT ? "▲ Abreisende " : "▼ Ankommende ")
                                    + KreislaufPruefung.zahl(z.menge()) + (Double.isInfinite(z.menge()) ? "" : " M/h")
                                    + " – " + z.fehlt() + ":", fehlend)));
                }
                default -> {
                }
            }
        }
        if (wurzel.getChildren().isEmpty() && !immerZeigen) {
            return false;
        }
        TreeView<Object> baum = new TreeView<>(wurzel);
        baum.setShowRoot(false);
        baum.setPrefSize(720, 420);
        baum.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof Knoten k) {
                    setText(null);
                    setGraphic(k.ansicht);
                } else {
                    setText(item.toString());
                    setGraphic(null);
                    setStyle("-fx-font-weight: bold;");
                }
            }
        });
        String fuer = betriebe.size() == 1 ? betriebe.get(0).getAnzeigeName() : "die Betriebe";
        Label kopf = new Label("Für " + fuer + " wird zusätzlich gebraucht bzw. bleibt übrig.\n"
                + "▼ Eingang ohne Lieferanten · ▲ Ausgang ohne Abnehmer. Unter einem neuen Betrieb stehen dessen "
                + "eigene offene Enden.\n„Wird immer geliefert“: das Gut steht dem Betrieb unbegrenzt zur Verfügung, "
                + "ohne Zug. Mengen werden nach dem Übernehmen automatisch abgeglichen.");
        kopf.setWrapText(true);
        kopf.setText(kopf.getText() + "\nAusgewähltes wird mit „Weiter“ übernommen; „Offen lassen“ ändert nichts.");
        VBox inhalt = new VBox(10);
        if (immerZeigen) {
            // Fünf Abschnitte mit Bedarf und Gegenstück (Anwender 14.10.2026)
            VBox tabellen = new VBox(8);
            for (KreislaufPruefung.Abschnitt ab : KreislaufPruefung.Abschnitt.values()) {
                Label titel = new Label(ab.getTitel());
                titel.setStyle("-fx-font-weight: bold;");
                tabellen.getChildren().addAll(titel, abschnittTabelle(a, ab, ergebnis.abschnitt(ab)));
            }
            javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(tabellen);
            sp.setFitToWidth(true);
            sp.setPrefViewportHeight(330);
            inhalt.getChildren().add(sp);
        }
        if (wurzel.getChildren().isEmpty()) {
            inhalt.getChildren().add(new Label("Keine offenen Enden: jeder Bedarf hat ein Gegenstück."));
        } else {
            inhalt.getChildren().addAll(kopf, baum);
            VBox.setVgrow(baum, Priority.ALWAYS);
        }
        inhalt.setPadding(new Insets(10));

        Dialog<ButtonType> d = new Dialog<>();

        Fenster.merken(d, "erforderlich"); // Größe merken (Anwender 22.10.2026)
        if (owner != null) {
            d.initOwner(owner);
        }
        d.setTitle("Zusätzlich erforderlich");
        d.setResizable(true);
        // Nur "Weiter" (Anwender 14.10.2026): übernimmt, was in den Zeilen ausgewählt ist (Vorgabe "Offen lassen")
        ButtonType uebernehmen = new ButtonType("Weiter", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(uebernehmen);
        d.getDialogPane().setContent(inhalt);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        d.showAndWait();
        Anwenden anwenden = new Anwenden(a);
        anwenden.alle(wurzel, null);
        return anwenden.geaendert;
    }

    /** Tabelle eines Prüf-Abschnitts: Objekt | Gut bzw. Reisende | M/h | Gegenstück | Hinweis (rot, wenn etwas fehlt). */
    private static javafx.scene.control.TableView<KreislaufPruefung.Zeile> abschnittTabelle(Anlage a,
            KreislaufPruefung.Abschnitt ab, List<KreislaufPruefung.Zeile> zeilen) {
        javafx.scene.control.TableView<KreislaufPruefung.Zeile> t = new javafx.scene.control.TableView<>(
                javafx.collections.FXCollections.observableArrayList(zeilen));
        Spalten.einrichten(t, "kreislauf." + ab.name().toLowerCase());
        javafx.scene.control.TableColumn<KreislaufPruefung.Zeile, String> obj = new javafx.scene.control.TableColumn<>(
                ab == KreislaufPruefung.Abschnitt.EINGAENGE || ab == KreislaufPruefung.Abschnitt.AUSGAENGE
                        ? "Betrieb" : "Ort");
        obj.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(z.getValue().objekt().getAnzeigeName()));
        obj.setPrefWidth(160);
        javafx.scene.control.TableColumn<KreislaufPruefung.Zeile, KreislaufPruefung.Zeile> gut =
                new javafx.scene.control.TableColumn<>(ab == KreislaufPruefung.Abschnitt.ABFAHRT
                        || ab == KreislaufPruefung.Abschnitt.ANKUNFT ? "Reisende" : "Gut");
        gut.setCellValueFactory(z -> new javafx.beans.property.SimpleObjectProperty<>(z.getValue()));
        gut.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(KreislaufPruefung.Zeile z, boolean empty) {
                super.updateItem(z, empty);
                if (empty || z == null) {
                    setText(null);
                    setGraphic(null);
                } else if (z.gutId() == null) {
                    setText(ab == KreislaufPruefung.Abschnitt.ABFAHRT ? "Abreisende" : "Ankommende");
                    setGraphic(Symbole.person(16));
                } else {
                    setText(a.gutName(z.gutId()));
                    setGraphic(Symbole.gut(a.gut(z.gutId()), 16));
                }
            }
        });
        gut.setPrefWidth(130);
        javafx.scene.control.TableColumn<KreislaufPruefung.Zeile, String> menge = new javafx.scene.control.TableColumn<>();
        Label mh = new Label("M/h");
        mh.setTooltip(new javafx.scene.control.Tooltip("je Modellstunde"));
        menge.setGraphic(mh);
        menge.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(
                KreislaufPruefung.zahl(z.getValue().menge())));
        menge.setPrefWidth(75);
        javafx.scene.control.TableColumn<KreislaufPruefung.Zeile, String> gegen = new javafx.scene.control.TableColumn<>(
                switch (ab) {
                    case EINGAENGE, ORTSBEDARF -> "Geliefert von";
                    case AUSGAENGE -> "Geht an";
                    case ABFAHRT -> "Gegenstück (nimmt auf)";
                    case ANKUNFT -> "Gegenstück (bringt)";
                });
        gegen.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(z.getValue().gegenstueck()));
        gegen.setPrefWidth(260);
        javafx.scene.control.TableColumn<KreislaufPruefung.Zeile, KreislaufPruefung.Zeile> hinweis =
                new javafx.scene.control.TableColumn<>("Prüfung");
        hinweis.setCellValueFactory(z -> new javafx.beans.property.SimpleObjectProperty<>(z.getValue()));
        hinweis.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(KreislaufPruefung.Zeile z, boolean empty) {
                super.updateItem(z, empty);
                if (empty || z == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(z.fehlt() != null ? z.fehlt() : "✔ in Ordnung");
                    setStyle(!z.ok() ? "-fx-text-fill: #cc3333; -fx-font-weight: bold;"
                            : z.fehlt() != null ? "-fx-text-fill: #c07000;" : "-fx-text-fill: #2e9e3e;");
                }
            }
        });
        hinweis.setPrefWidth(250);
        t.getColumns().addAll(List.of(obj, gut, menge, gegen, hinweis));
        t.setPlaceholder(new Label("Keine Einträge"));
        t.setPrefHeight(Math.min(220, 34 + 26 * Math.max(1, zeilen.size())));
        t.setMinHeight(t.getPrefHeight());
        t.setPrefWidth(900);
        return t;
    }

    /** Tabelle Betrieb | Gut | Geliefert von (rot, wenn kein Lieferant). */
    private static javafx.scene.control.TableView<String[]> eingangsTabelle(Anlage a, List<Betrieb> betriebe) {
        javafx.scene.control.TableView<String[]> t = new javafx.scene.control.TableView<>();
        Spalten.einrichten(t, "kreislauf.eingaenge");
        for (Betrieb b : betriebe) {
            for (String g : b.eingangsGueter()) {
                t.getItems().add(new String[]{b.getAnzeigeName() + (b.isVirtuell() ? " (nicht auf der Anlage)" : ""),
                        g, Kreislauf.geliefertVon(a, b, g)});
            }
        }
        javafx.scene.control.TableColumn<String[], String> betrieb = new javafx.scene.control.TableColumn<>("Betrieb");
        betrieb.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(z.getValue()[0]));
        betrieb.setPrefWidth(220);
        javafx.scene.control.TableColumn<String[], String> gut = new javafx.scene.control.TableColumn<>("Gut");
        gut.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(z.getValue()[1]));
        gut.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                setText(empty || id == null ? null : a.gutName(id));
                setGraphic(empty || id == null ? null : Symbole.gut(a.gut(id), 16));
            }
        });
        gut.setPrefWidth(150);
        javafx.scene.control.TableColumn<String[], String> von = new javafx.scene.control.TableColumn<>("Geliefert von");
        von.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(z.getValue()[2]));
        von.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                setStyle(!empty && text != null && text.startsWith("–") ? "-fx-text-fill: #cc3333; -fx-font-weight: bold;" : "");
            }
        });
        von.setPrefWidth(330);
        t.getColumns().addAll(List.of(betrieb, gut, von));
        t.setPlaceholder(new Label("Keine Betriebe mit Eingängen"));
        t.setPrefHeight(Math.min(300, 60 + 28 * Math.max(1, t.getItems().size())));
        t.setPrefWidth(720);
        return t;
    }

    private static TreeItem<Object> item(Anlage a, Knoten k) {
        TreeItem<Object> it = new TreeItem<>(k);
        it.setExpanded(true);
        kinderAufbauen(a, it, k);
        k.wahl.valueProperty().addListener((obs, alt, neu) -> kinderAufbauen(a, it, k));
        return it;
    }

    /** Unter einem neuen Betrieb: dessen offene Enden (ohne das verbindende Gut und ohne Kreise). */
    private static void kinderAufbauen(Anlage a, TreeItem<Object> it, Knoten k) {
        it.getChildren().clear();
        Option o = k.wahl.getValue();
        if (o == null || o.art() != Art.NEU || k.tiefe >= MAX_TIEFE || k.weg.contains(o.vorlage().id())) {
            return;
        }
        BetriebVorlage v = o.vorlage();
        Set<String> weg = new LinkedHashSet<>(k.weg);
        weg.add(v.id());
        for (String g : v.eingangslager().keySet()) {
            if (k.ausgang && g.equals(k.gutId)) {
                continue; // das verbindende Gut kommt vom Elternbetrieb
            }
            if (!Kreislauf.lieferanten(a, g, null).isEmpty() || Kreislauf.uebergabeLiefert(a, g)) {
                continue;
            }
            it.getChildren().add(item(a, new Knoten(a, g, false, k.tiefe + 1, null, weg)));
        }
        for (String g : v.ausgangslager().keySet()) {
            if (!k.ausgang && g.equals(k.gutId)) {
                continue; // geht an den Elternbetrieb
            }
            if (!Kreislauf.abnehmer(a, g, null).isEmpty() || Kreislauf.uebergabeNimmt(a, g)) {
                continue;
            }
            it.getChildren().add(item(a, new Knoten(a, g, true, k.tiefe + 1, null, weg)));
        }
    }

    /** Führt die Auswahl aus, von oben nach unten. */
    private static final class Anwenden {
        final Anlage a;
        final Map<String, Betrieb> neueJeVorlage = new HashMap<>();
        int versatz;
        boolean geaendert;

        Anwenden(Anlage a) {
            this.a = a;
        }

        void alle(TreeItem<Object> it, AnlagenObjekt eltern) {
            for (TreeItem<Object> kind : it.getChildren()) {
                if (kind.getValue() instanceof Knoten k) {
                    AnlagenObjekt besitzer = k.besitzer != null ? k.besitzer : eltern;
                    Betrieb neu = eine(k, besitzer);
                    alle(kind, neu);
                } else {
                    alle(kind, eltern);
                }
            }
        }

        private Betrieb eine(Knoten k, AnlagenObjekt b) {
            Option o = k.wahl.getValue();
            if (b == null || o == null) {
                return null;
            }
            switch (o.art()) {
                case ANPASSEN -> {
                    Kreislauf.erzeugerAnpassen(a, k.gutId);
                    geaendert = true;
                }
                case SBH_NEU -> {
                    Station s = neueStation(ObjektArt.SCHATTENBAHNHOF, "Schattenbahnhof", k, b);
                    int n = Math.max(1, s.getGleise().size());
                    for (Gleis g : s.getGleise()) {
                        g.setPassagiereProStunde(Math.max(1, Math.ceil(k.menge / n)));
                    }
                    geaendert = true;
                }
                case BAHNHOF_FUER, HALT_FUER -> {
                    Station s = neueStation(o.art() == Art.BAHNHOF_FUER ? ObjektArt.BAHNHOF : ObjektArt.HALTEPUNKT,
                            o.ort().getAnzeigeName() + (o.art() == Art.BAHNHOF_FUER ? " Bf" : " Hp"), k, o.ort());
                    s.getEinzugsbereich().add(o.ort().getId());
                    geaendert = true;
                }
                case NEUES_DORF -> {
                    Ort dorf = new Ort(null, ObjektArt.DORF, a.freierName("Dorf"));
                    a.platziereNeu(dorf, b.getX() + 260, b.getY() + 160);
                    Station s = neueStation(ObjektArt.HALTEPUNKT, dorf.getAnzeigeName() + " Hp", k, dorf);
                    s.getEinzugsbereich().add(dorf.getId());
                    geaendert = true;
                }
                case UNBEGRENZT -> {
                    o.ort().setAnkommendUnbegrenzt(true);
                    geaendert = true;
                }
                case ALS_BAHNHOF -> {
                    if (o.station().getArt() == ObjektArt.ANDERES) {
                        o.station().setArt(ObjektArt.BAHNHOF);
                        a.ladegleiseAuslagern(o.station());
                    }
                    geaendert = true;
                }
                case VERBINDEN -> {
                    if (!o.station().getEinzugsbereich().contains(o.ort().getId()) && a.ortErlaubt(o.station(), o.ort())) {
                        o.station().getEinzugsbereich().add(o.ort().getId());
                    }
                    geaendert = true;
                }
                case NEU, VIRTUELL -> {
                    String schluessel = o.vorlage().id() + (o.art() == Art.VIRTUELL ? "#v" : "");
                    Betrieb neu = neueJeVorlage.get(schluessel);
                    if (neu == null) {
                        versatz++;
                        double x = b.getX() + (k.ausgang ? 220 : -220);
                        if (b instanceof Ort) {
                            x = b.getX() + 240; // Lieferant für eine Stadt rechts daneben
                        }
                        double y = b.getY() + 40 + (versatz % 5) * 110;
                        neu = a.neuerBetrieb(o.vorlage(), Math.max(0, x), y);
                        a.platziereNeu(neu, Math.max(0, x), b.getY());
                        neu.setVirtuell(o.art() == Art.VIRTUELL);
                        neueJeVorlage.put(schluessel, neu);
                    }
                    geaendert = true;
                    if (o.art() == Art.NEU) {
                        if (k.ausgang) {
                            Kreislauf.verbraucherAnpassen(a, k.gutId);
                        } else {
                            Kreislauf.erzeugerAnpassen(a, k.gutId);
                        }
                        return neu;
                    }
                    return null;
                }
                case IMMER -> {
                    if (b instanceof Betrieb bb) {
                        bb.getImmerGeliefert().add(k.gutId);
                        geaendert = true;
                    }
                }
                case UEBERGABE -> {
                    Uebergabe u = uebergabe(b);
                    if (k.ausgang) {
                        u.getAnnahme().add(k.gutId);
                    } else {
                        u.getAngebot().add(k.gutId);
                    }
                    geaendert = true;
                }
                case ORT -> {
                    if (k.ausgang && b instanceof Betrieb bb) {
                        o.ort().getBedarf().merge(k.gutId, MengenEditor.ganz(bb.erzeugungProStunde(k.gutId)), Double::sum);
                        a.gueterbahnhofFuer(o.ort());
                        geaendert = true;
                    }
                }
                case OFFEN -> {
                    // nichts
                }
            }
            return null;
        }

        /** Neue Station mit den gewählten Blöcken als Bahnsteige, neben {@code nahe}. */
        private Station neueStation(ObjektArt art, String name, Knoten k, AnlagenObjekt nahe) {
            Station s = new Station(null, art, a.freierName(name));
            for (String block : k.gewaehlteGleise) {
                if (!a.freieBloecke().contains(block)) {
                    continue; // schon von einer anderen Zeile vergeben
                }
                Gleis g = new Gleis(null, GleisArt.BAHNSTEIG);
                g.setBlockName(block);
                g.setName(block);
                s.getGleise().add(g);
            }
            a.platziereNeu(s, nahe.getX() + 200, nahe.getY() + 140);
            return s;
        }

        private Uebergabe uebergabe(AnlagenObjekt nahe) {
            List<Uebergabe> vorhanden = a.uebergaben();
            if (!vorhanden.isEmpty()) {
                return vorhanden.get(0);
            }
            Uebergabe u = new Uebergabe(null, a.freierName("Übergabe"));
            u.setAnnahmeAlles(false);
            u.setX(nahe.getX() + 120);
            u.setY(nahe.getY() + 160);
            a.getObjekte().add(u);
            return u;
        }
    }
}
