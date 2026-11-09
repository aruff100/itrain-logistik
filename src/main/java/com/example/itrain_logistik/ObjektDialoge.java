package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Infrastruktur;
import com.example.itrain_logistik.kern.Ladeklasse;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Rezept;
import com.example.itrain_logistik.kern.StadtBereich;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Uebergabe;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import com.example.itrain_logistik.kern.Tf3Daten;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bearbeitungsdialoge fuer die Objekte der Anlage (Ort, Station mit Gleisen, Betrieb, Lager,
 * Uebergabe). Aenderungen werden erst mit "Speichern" uebernommen.
 */
public final class ObjektDialoge {

    private ObjektDialoge() {
    }

    /** @return true, wenn mit OK geaendert wurde */
    public static boolean bearbeite(Stage owner, AnlagenObjekt o, Anlage a, List<BlockEntry> bloecke) {
        if (o instanceof Ort ort) {
            if (!ort(owner, ort, a)) {
                return false;
            }
            // Güter kommen nur über Ladegleise: hängt der Ort schon an einem Personenbahnhof, bekommt
            // dieser jetzt seinen Güterbahnhof (sonst erst beim Verbinden mit einer Station).
            for (Station s : a.stationenFuer(ort.getId())) {
                if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.BAHNHOF) {
                    nachVerbinden(owner, a, ort, s);
                    break;
                }
            }
            return true;
        }
        if (o instanceof Station s) {
            return station(owner, s, a, bloecke);
        }
        if (o instanceof Betrieb b) {
            return betrieb(owner, b, a);
        }
        if (o instanceof Lager l) {
            return lager(owner, l);
        }
        if (o instanceof Uebergabe u) {
            return uebergabe(owner, u, a);
        }
        if (o instanceof Infrastruktur) {
            return infrastruktur(owner, a, bloecke);
        }
        if (o instanceof com.example.itrain_logistik.kern.Modul m) {
            return modul(owner, m, a);
        }
        if (o instanceof com.example.itrain_logistik.kern.Reiter r) {
            return reiter(owner, r, a);
        }
        return false;
    }

    // ================================================================ Modul (Anwender 09.11.2026)

    /**
     * Name, Anzeige im Browser (mit Adresse), Stationen des Moduls (nur solche, die noch an keinem anderen Modul
     * hängen; Güterbahnhöfe gehen mit ihrem Bahnhof) und Strecken-Blöcke (freie und eigene).
     */
    private static boolean modul(Stage owner, com.example.itrain_logistik.kern.Modul m, Anlage a) {
        TextField name = new TextField(m.getAnzeigeName());
        CheckBox browser = new CheckBox("Im Browser zeigen (nur lesend, auf einem anderen Rechner, Handy oder Tablet)");
        browser.setSelected(m.isImBrowser());
        Label adresse = new Label();
        adresse.setWrapText(true);
        Runnable zeigeAdresse = () -> {
            com.example.itrain_logistik.kern.Modul probe = new com.example.itrain_logistik.kern.Modul(m.getId(),
                    name.getText().trim());
            adresse.setText(browser.isSelected() ? "Adresse: http://" + Webansicht.eigeneAdresse()
                    + ":" + AppSettings.getInstance().getWebPort() + "/modul/" + Webansicht.pfad(probe) : "");
        };
        name.textProperty().addListener((o, x, y) -> zeigeAdresse.run());
        browser.selectedProperty().addListener((o, x, y) -> zeigeAdresse.run());
        zeigeAdresse.run();

        VBox stationen = new VBox(3);
        List<CheckBox> stBoxen = new ArrayList<>();
        for (Station s : a.stationen()) {
            if (a.gehoertZu(s) != null) {
                continue;
            }
            com.example.itrain_logistik.kern.Bereich anderes = a.bereichFuer(s);
            if (anderes != null && anderes != m) {
                continue; // hängt an einem anderen Tab/Modul
            }
            CheckBox cb = new CheckBox(s.getAnzeigeName());
            cb.setUserData(s.getId());
            cb.setSelected(anderes == m);
            stBoxen.add(cb);
            stationen.getChildren().add(cb);
        }
        if (stBoxen.isEmpty()) {
            stationen.getChildren().add(new Label("Keine freie Station."));
        }
        VBox bloecke = new VBox(3);
        List<CheckBox> blBoxen = new ArrayList<>();
        java.util.Set<String> kandidaten = new LinkedHashSet<>(m.getBloecke());
        kandidaten.addAll(a.freieBloecke());
        for (String b : kandidaten) {
            CheckBox cb = new CheckBox(b);
            cb.setSelected(m.getBloecke().contains(b));
            blBoxen.add(cb);
            bloecke.getChildren().add(cb);
        }
        if (blBoxen.isEmpty()) {
            bloecke.getChildren().add(new Label("Keine freien Blöcke (alle gehören Gleisen oder anderen Modulen)."));
        }
        // Kompakt (Anwender 09.11.2026: „nach unten sehr groß“): Stationen und Strecke nebeneinander
        ScrollPane stScroll = new ScrollPane(stationen);
        stScroll.setPrefSize(240, 150);
        ScrollPane blScroll = new ScrollPane(bloecke);
        blScroll.setPrefSize(240, 150);
        Label stTitel = new Label("Stationen (mit Gbf, Orten, Betrieben, Lagern)");
        Label blTitel = new Label("Strecke: Gleis von Modul zu Modul (mindestens eins)");
        blTitel.setTooltip(new javafx.scene.control.Tooltip("Pflicht (Anwender 09.11.2026): das Gleis, das von "
                + "diesem Modul zum nächsten führt. Ohne Gleis lässt sich das Modul nicht speichern."));
        javafx.beans.property.BooleanProperty ohneGleis = new javafx.beans.property.SimpleBooleanProperty();
        Runnable gleisPruefen = () -> ohneGleis.set(blBoxen.stream().noneMatch(CheckBox::isSelected));
        blBoxen.forEach(cb -> cb.selectedProperty().addListener((o, x, y) -> gleisPruefen.run()));
        gleisPruefen.run();
        Label fehlt = new Label("Bitte mindestens ein Streckengleis wählen.");
        fehlt.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
        fehlt.visibleProperty().bind(ohneGleis);
        fehlt.managedProperty().bind(ohneGleis);
        stTitel.setTooltip(new javafx.scene.control.Tooltip(
                "Hängen am Modul statt an der Anlagen-Infrastruktur; Güterbahnhof, Orte, Betriebe und Lager gehen mit."));
        javafx.scene.layout.HBox listen = new javafx.scene.layout.HBox(12, new VBox(4, stTitel, stScroll),
                new VBox(4, blTitel, blScroll));
        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        zeile(grid, 1, "", new VBox(4, browser, adresse));
        zeile(grid, 2, "", listen);
        zeile(grid, 3, "", fehlt);
        if (!zeige(owner, "Modul bearbeiten", grid, ohneGleis)) {
            return false;
        }
        m.setName(name.getText().trim());
        m.setImBrowser(browser.isSelected());
        m.getStationen().clear();
        for (CheckBox cb : stBoxen) {
            if (cb.isSelected() && a.objekt((String) cb.getUserData()) instanceof Station st) {
                a.bereichZuordnen(st, m);
            }
        }
        m.getBloecke().clear();
        for (CheckBox cb : blBoxen) {
            if (cb.isSelected()) {
                m.getBloecke().add(cb.getText());
            }
        }
        return true;
    }

    // ================================================================ Tab (Anwender 09.11.2026)

    /** Name und Bahnhöfe des Tabs (Bahnhöfe, die an keinem anderen Tab/Modul hängen). */
    private static boolean reiter(Stage owner, com.example.itrain_logistik.kern.Reiter r, Anlage a) {
        TextField name = new TextField(r.getAnzeigeName());
        VBox stationen = new VBox(3);
        List<CheckBox> boxen = new ArrayList<>();
        for (Station s : a.stationen()) {
            if (a.gehoertZu(s) != null) {
                continue;
            }
            com.example.itrain_logistik.kern.Bereich anderes = a.bereichFuer(s);
            if (anderes != null && anderes != r) {
                continue;
            }
            CheckBox cb = new CheckBox(s.getAnzeigeName());
            cb.setUserData(s.getId());
            cb.setSelected(anderes == r);
            boxen.add(cb);
            stationen.getChildren().add(cb);
        }
        if (boxen.isEmpty()) {
            stationen.getChildren().add(new Label("Kein freier Bahnhof."));
        }
        ScrollPane sc = new ScrollPane(stationen);
        sc.setPrefSize(300, 160);
        Label hinweis = new Label("Angehakte Bahnhöfe hängen am Tab statt an der Anlagen-Infrastruktur und stehen mit "
                + "Güterbahnhof, Orten, Betrieben und Lagern in seinem Reiter. Ebenso per Rechtsklick auf einen Bahnhof "
                + "„Mit Tab verbinden…“. Der Name gilt für Symbol und Reiter.");
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(420);
        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        zeile(grid, 1, "Bahnhöfe", sc);
        zeile(grid, 2, "", hinweis);
        if (!zeige(owner, "Tab bearbeiten", grid)) {
            return false;
        }
        r.setName(name.getText().trim());
        for (CheckBox cb : boxen) {
            if (a.objekt((String) cb.getUserData()) instanceof Station st) {
                if (cb.isSelected()) {
                    a.bereichZuordnen(st, r);
                } else if (a.bereichFuer(st) == r) {
                    a.bereichZuordnen(st, null);
                }
            }
        }
        return true;
    }

    // ================================================================ Anlagen-Infrastruktur

    /**
     * Tabelle aller Bloecke, die keinem Gleis zugeordnet sind (Strecke). Ausgewaehlte Bloecke
     * lassen sich direkt als Gleis einer Station zuordnen; sie verschwinden dann aus der Liste.
     */
    private static boolean infrastruktur(Stage owner, Anlage a, List<BlockEntry> bloecke) {
        boolean[] geaendert = {false};
        ObservableList<BlockEntry> frei = FXCollections.observableArrayList();
        Runnable fuellen = () -> {
            java.util.Set<String> freieNamen = new java.util.HashSet<>(a.freieBloecke());
            frei.setAll(bloecke.stream().filter(b -> freieNamen.contains(b.getName())).toList());
        };
        fuellen.run();
        TableView<BlockEntry> tabelle = new TableView<>(frei);
        Spalten.einrichten(tabelle, "blockauswahl");
        tabelle.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        tabelle.setPrefSize(420, 320);
        tabelle.getColumns().add(spalte("Block", 220, BlockEntry::getName));
        tabelle.getColumns().add(spalte("Typ (iTrain)", 140, b -> b.getType() == null ? "" : b.getType()));
        Label zaehler = new Label();
        Runnable zaehlen = () -> zaehler.setText(frei.size() + " von " + bloecke.size()
                + " Blöcken sind keinem Gleis zugeordnet (Strecke).");
        zaehlen.run();

        ComboBox<Station> station = new ComboBox<>(FXCollections.observableArrayList(a.stationen()));
        station.setConverter(konverter(s -> s.getAnzeigeName() + " (" + s.getArt().getLabel() + ")"));
        station.setPromptText("Station");
        ComboBox<GleisArt> art = new ComboBox<>(FXCollections.observableArrayList(GleisArt.values()));
        art.setConverter(konverter(GleisArt::getLabel));
        art.setValue(GleisArt.LADEGLEIS);
        Label artHinweis = new Label("Ladegleise eines Bahnhofs, Haltepunkts oder Schattenbahnhofs kommen in "
                + "dessen Güterbahnhof (wird bei Bedarf angelegt)");
        artHinweis.setStyle("-fx-font-size: 11px;");
        station.valueProperty().addListener((obs, alt, neu) -> {
            if (neu != null && !neu.getArt().erlaubt(art.getValue())) {
                art.setValue(neu.getArt().erlaubt(GleisArt.BAHNSTEIG) ? GleisArt.BAHNSTEIG : GleisArt.LADEGLEIS);
            }
        });
        Button zuordnen = new Button("Als Gleis zuordnen");
        zuordnen.setOnAction(e -> {
            Station s = station.getValue();
            if (s == null || tabelle.getSelectionModel().getSelectedItems().isEmpty()) {
                return;
            }
            if (!s.getArt().erlaubt(art.getValue())) {
                new Alert(Alert.AlertType.WARNING, s.getArt().getLabel() + " hat keine "
                        + art.getValue().getLabel() + "e.").showAndWait();
                return;
            }
            for (BlockEntry b : List.copyOf(tabelle.getSelectionModel().getSelectedItems())) {
                Gleis g = new Gleis(null, art.getValue());
                g.setBlockName(b.getName());
                g.setName(b.getName());
                s.getGleise().add(g);
            }
            a.ladegleiseAuslagern(s);
            geaendert[0] = true;
            fuellen.run();
            zaehlen.run();
        });
        if (a.stationen().isEmpty()) {
            zuordnen.setDisable(true);
            station.setPromptText("erst eine Station anlegen");
        }
        VBox inhalt = new VBox(8, zaehler, tabelle,
                new HBox(6, new Label("Ausgewählte Blöcke an"), station, art, zuordnen), artHinweis,
                new Label("Gleisname, Fahrtrichtungen und Kapazität danach in der Station einstellen."));
        zeige(owner, "Anlagen-Infrastruktur", inhalt);
        return geaendert[0];
    }

    // ================================================================ Ort

    /** Senkrechte, vertiefte ("3D") Trennlinie zwischen zwei Abschnitten. */
    static Region linie3d() {
        Region r = new Region();
        r.setMinWidth(4);
        r.setPrefWidth(4);
        r.setMaxWidth(4);
        r.setStyle("-fx-border-color: transparent derive(-fx-base, 45%) transparent derive(-fx-base, -45%); "
                + "-fx-border-width: 0 2 0 2;");
        return r;
    }

    private static Label ueberschrift(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    /**
     * Stadt/Dorf/Ländlich: breites Fenster, links Personen, rechts Güter, dazwischen eine 3D-Linie;
     * "Vorschlag…" übernimmt Stadt/Dorf/Ländlich oder eigene gespeicherte Mengen.
     */
    private static boolean ort(Stage owner, Ort o, Anlage a) {
        TextField name = new TextField(o.getAnzeigeName());
        TextField einwohner = new TextField(String.valueOf(o.getEinwohner()));
        TextField passagiere = LogistikFeldRow.ziffern(new TextField(zahl(Math.round(o.getPassagiereProStunde()))), 4);
        TextField ankommend = LogistikFeldRow.ziffern(new TextField(zahl(o.getAnkommendProStunde())), 4);
        CheckBox unbegrenzt = new CheckBox("Unbegrenzt");
        unbegrenzt.setSelected(o.isAnkommendUnbegrenzt());
        ankommend.disableProperty().bind(unbegrenzt.selectedProperty());
        HBox ankommendZeile = new HBox(8, ankommend, unbegrenzt);
        ankommendZeile.setAlignment(Pos.CENTER_LEFT);
        TextField erste = new TextField(zahl(o.getAnteilErsteKlasse() * 100));
        TextField aussteigen = new TextField(zahl(o.getAussteigeAnteil() * 100));

        MengenEditor gueter = new MengenEditor(owner, a, o.getBedarf(), "Bedarf M/h",
                () -> new double[]{parse(passagiere.getText(), 0), parse(erste.getText(), 10) / 100.0,
                        parse(aussteigen.getText(), 30) / 100.0},
                v -> {
                    passagiere.setText(zahl(v[0]));
                    erste.setText(zahl(v[1] * 100));
                    aussteigen.setText(zahl(v[2] * 100));
                });

        GridPane kopf = grid();
        zeile(kopf, 0, "Name", name);
        zeile(kopf, 1, "Art", new Label(o.getArt().getLabel()));

        GridPane personen = grid();
        zeile(personen, 0, "Einwohner", einwohner);
        zeile(personen, 1, "Abreisende M/h", passagiere);
        zeile(personen, 2, "Ankommende Reisende M/h", ankommendZeile);
        zeile(personen, 3, "Anteil 1. Klasse (%)", erste);
        zeile(personen, 4, "Aussteigeanteil ankommender\nPassagiere (%)", aussteigen);
        Label personenHinweis = new Label("M/h = je Modellstunde. 0 Abreisende = keine Personen, sie erscheinen dann "
                + "auch nicht im Symbol. Ankommende: so viele steigen hier je Modellstunde höchstens aus; "
                + "„Unbegrenzt“ = beliebig viele. „Kreislauf prüfen“ sucht für beide Werte ein Gegenstück.");
        personenHinweis.setWrapText(true);
        personenHinweis.setMaxWidth(320);
        personenHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        // Post und Expressgut entstehen im Ort selbst, ohne Produktionsstätte (Anwender 27.10.2026).
        CheckBox liefertPost = new CheckBox("Liefert Post");
        liefertPost.setSelected(o.isLiefertPost());
        TextField post = new TextField(zahl(o.getPostProStunde()));
        CheckBox liefertExpress = new CheckBox("Liefert Expressgut");
        liefertExpress.setSelected(o.isLiefertExpressgut());
        TextField express = new TextField(zahl(o.getExpressProStunde()));
        post.disableProperty().bind(liefertPost.selectedProperty().not());
        express.disableProperty().bind(liefertExpress.selectedProperty().not());
        post.setPrefColumnCount(5);
        express.setPrefColumnCount(5);
        GridPane postGrid = grid();
        postGrid.addRow(0, liefertPost, post, new Label("Stück M/h"));
        postGrid.addRow(1, liefertExpress, express, new Label("Stück M/h"));
        Label postHinweis = new Label("Vorgabe nach Größe (Stadt, Dorf, Ländlich) als Anteil der Abreisenden. "
                + "Wird am Bahnsteig in Personenzüge mit Wagen „Kann Post/Expressgut mitnehmen“ geladen.");
        postHinweis.setWrapText(true);
        postHinweis.setMaxWidth(320);
        postHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        VBox links = new VBox(10, ueberschrift("Personen"), personen, personenHinweis,
                ueberschrift("Post und Expressgut"), postGrid, postHinweis);
        links.setMinWidth(330);
        VBox rechts = new VBox(10, ueberschrift("Güter (Bedarf)"), gueter.getView());
        VBox.setVgrow(gueter.getView(), Priority.ALWAYS);
        HBox.setHgrow(rechts, Priority.ALWAYS);
        rechts.setPrefWidth(470);
        HBox teile = new HBox(14, links, linie3d(), rechts);
        // Vorschläge gelten für Personen und Güter: linksbündig unter beiden Abschnitten
        VBox inhalt = new VBox(12, kopf, new Separator(), teile, new Separator(), gueter.getKnoepfe());
        VBox.setVgrow(teile, Priority.ALWAYS);
        if (!zeige(owner, o.getArt().getLabel() + " bearbeiten", inhalt)) {
            return false;
        }
        o.setName(name.getText().trim());
        o.setEinwohner((int) parse(einwohner.getText(), o.getEinwohner()));
        o.setPassagiereProStunde(Math.round(parse(passagiere.getText(), o.getPassagiereProStunde())));
        o.setAnkommendProStunde(parse(ankommend.getText(), 0));
        o.setAnkommendUnbegrenzt(unbegrenzt.isSelected());
        o.setAnteilErsteKlasse(parse(erste.getText(), 10) / 100.0);
        o.setAussteigeAnteil(parse(aussteigen.getText(), 30) / 100.0);
        o.setLiefertPost(liefertPost.isSelected());
        o.setLiefertExpressgut(liefertExpress.isSelected());
        o.setPostProStunde(-1);
        o.setExpressProStunde(-1);
        double p = Math.max(0, Math.round(parse(post.getText(), o.getPostProStunde())));
        double ex = Math.max(0, Math.round(parse(express.getText(), o.getExpressProStunde())));
        // Gleich der Vorgabe = bleibt an die Größe/Abreisenden gekoppelt.
        o.setPostProStunde(p == o.vorgabe(Tf3Daten.POST) ? -1 : p);
        o.setExpressProStunde(ex == o.vorgabe(Tf3Daten.EXPRESSGUT) ? -1 : ex);
        o.getBedarf().clear();
        o.getBedarf().putAll(gueter.getMengen());
        o.getOffenerBedarf().keySet().retainAll(o.getBedarf().keySet());
        return true;
    }

    // ================================================================ Station

    private static boolean station(Stage owner, Station s, Anlage a, List<BlockEntry> bloecke) {
        TextField name = new TextField(s.getAnzeigeName());
        ObservableList<Gleis> gleise = FXCollections.observableArrayList(s.getGleise());
        TableView<Gleis> tabelle = new TableView<>(gleise);
        Spalten.einrichten(tabelle, "station.gleise");
        tabelle.setPrefHeight(200);
        tabelle.getColumns().add(spalte("Gleis", 120, Gleis::getAnzeigeName));
        tabelle.getColumns().add(spalte("Block", 140, g -> g.getBlockName() == null ? "" : g.getBlockName()));
        tabelle.getColumns().add(spalte("Art", 80, g -> g.getArt().getLabel()));
        tabelle.getColumns().add(spalte("Richtungen", 140, g -> String.join(", ", g.getRichtungen())));
        tabelle.getColumns().add(spalte("Laden/Entladen", 100, g -> (g.isLaden() ? "L" : "-") + "/"
                + (g.isEntladen() ? "E" : "-") + (g.isEntladenErzwingen() ? " erzw." : "")));
        tabelle.getColumns().add(spalte("Kapazität", 70, g -> String.valueOf(g.getKapazitaet())));
        tabelle.getColumns().add(spalte("Güter", 120, g -> g.getGueterAuswahl().isEmpty() ? "alle"
                : g.getGueterAuswahl().size() + " ausgewählt"));

        Button neu = new Button("Gleis hinzufügen…");
        neu.setOnAction(e -> {
            GleisArt art = s.getArt().erlaubt(GleisArt.BAHNSTEIG) ? GleisArt.BAHNSTEIG : GleisArt.LADEGLEIS;
            Gleis g = new Gleis(null, art);
            if (gleis(owner, g, s, a, bloecke, gleise)) {
                gleise.add(g);
            }
        });
        Button bearbeiten = new Button("Bearbeiten…");
        bearbeiten.setOnAction(e -> {
            Gleis g = tabelle.getSelectionModel().getSelectedItem();
            if (g != null && gleis(owner, g, s, a, bloecke, gleise)) {
                tabelle.refresh();
            }
        });
        Button weg = new Button("Entfernen");
        weg.setOnAction(e -> gleise.remove(tabelle.getSelectionModel().getSelectedItem()));

        // Einzugsbereich als Ankreuzliste
        List<CheckBox> einzug = new ArrayList<>();
        VBox einzugBox = new VBox(4);
        // Nur, was noch frei ist oder schon an dieser Station bzw. ihrem Bahnhof/Güterbahnhof hängt; was an einer
        // anderen Station angeschlossen ist, erscheint nicht (Anwender 27.10.2026).
        java.util.Set<Station> verbund = new java.util.HashSet<>();
        verbund.add(s);
        if (a.gehoertZu(s) != null) {
            verbund.add(a.gehoertZu(s));
        }
        if (a.angedockterGueterbahnhof(s) != null) {
            verbund.add(a.angedockterGueterbahnhof(s));
        }
        for (AnlagenObjekt o : a.getObjekte()) {
            if (o instanceof Station || o instanceof Infrastruktur) {
                continue;
            }
            if (!s.bedient(o.getId()) && a.stationenFuer(o.getId()).stream().anyMatch(x -> !verbund.contains(x))) {
                continue;
            }
            CheckBox cb = new CheckBox(o.toString());
            cb.setUserData(o.getId());
            cb.setSelected(s.bedient(o.getId()));
            Ort amVerbund = a.ortAnStation(s);
            if (o instanceof Ort ort && !cb.isSelected() && amVerbund != null && amVerbund != ort
                    && !s.bedient(amVerbund.getId())) {
                // zugehöriger Bahnhof bzw. Güterbahnhof hat schon einen Ort (Anwender 27.10.2026)
                cb.setDisable(true);
                cb.setText(cb.getText() + " – hier gilt „" + amVerbund.getAnzeigeName() + "“");
            }
            einzug.add(cb);
            einzugBox.getChildren().add(cb);
        }
        // Je Bahnhof höchstens ein Ort (Anwender 27.10.2026): ein angekreuzter Ort nimmt die anderen heraus.
        for (CheckBox cb : einzug) {
            if (a.objekt((String) cb.getUserData()) instanceof Ort) {
                cb.selectedProperty().addListener((obs, x, an) -> {
                    if (an) {
                        for (CheckBox andere : einzug) {
                            if (andere != cb && a.objekt((String) andere.getUserData()) instanceof Ort) {
                                andere.setSelected(false);
                            }
                        }
                    }
                });
            }
        }
        ScrollPane einzugScroll = new ScrollPane(einzugBox);
        einzugScroll.setPrefHeight(140);
        einzugScroll.setFitToWidth(true);
        // Die Liste wächst mit der Fenstergröße.
        VBox.setVgrow(einzugScroll, javafx.scene.layout.Priority.ALWAYS);

        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        ComboBox<com.example.itrain_logistik.kern.ObjektArt> artWahl = null;
        if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.ANDERES) {
            // iTrain-Bahnhof vom Typ "other": die eigentliche Art trägt der Anwender ein (14.10.2026)
            artWahl = new ComboBox<>(FXCollections.observableArrayList(
                    com.example.itrain_logistik.kern.ObjektArt.ANDERES, com.example.itrain_logistik.kern.ObjektArt.BAHNHOF,
                    com.example.itrain_logistik.kern.ObjektArt.HALTEPUNKT,
                    com.example.itrain_logistik.kern.ObjektArt.GUETERBAHNHOF,
                    com.example.itrain_logistik.kern.ObjektArt.ANSCHLUSS,
                    com.example.itrain_logistik.kern.ObjektArt.SCHATTENBAHNHOF));
            artWahl.setValue(s.getArt());
            artWahl.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(com.example.itrain_logistik.kern.ObjektArt x) {
                    return x == null ? "" : x == com.example.itrain_logistik.kern.ObjektArt.ANDERES
                            ? "Anderes – bitte Art wählen" : x.getLabel();
                }

                @Override
                public com.example.itrain_logistik.kern.ObjektArt fromString(String t) {
                    return null;
                }
            });
            Label hinweisArt = new Label("In iTrain ein sonstiger Bahnhof (z. B. Betriebswerk). Solange „Anderes“ "
                    + "gewählt ist, halten Züge hier ohne Ein-/Aussteigen und ohne Umladen.");
            hinweisArt.setWrapText(true);
            hinweisArt.setMaxWidth(420);
            zeile(grid, 1, "Art", new VBox(4, artWahl, hinweisArt));
        } else {
            zeile(grid, 1, "Art", new Label(s.getArt().getLabel()));
        }
        final ComboBox<com.example.itrain_logistik.kern.ObjektArt> artWahlFinal = artWahl;
        VBox inhalt = new VBox(10, grid, new Label("Gleise (je Gleis genau ein iTrain-Block)"), tabelle,
                new HBox(6, neu, bearbeiten, weg),
                new Label("Einzugsbereich (Orte, Betriebe, Lager, Übergaben, die diese Station bedient)"),
                einzugScroll);
        CheckBox gbfAndocken = new CheckBox();
        if (a.istAussenwelt(s)) {
            // Schattenbahnhof (Anwender 09.11.2026): kein weiteres Symbol außer dem angedockten Gbf; Reisende und
            // Güter kommen je Gleis von außerhalb, geladen/entladen wird schon bei der Durchfahrt.
            int i = inhalt.getChildren().size();
            inhalt.getChildren().remove(i - 2, i); // kein Einzugsbereich
        }
        if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.SCHATTENBAHNHOF) {
            Label sh = new Label("Schattenbahnhof: Reisende und Güter kommen von außerhalb in den Schattenbahnhof – je "
                    + "Gleis eingestellt (Bahnsteig: Reisende je Modellstunde, Ladegleis: Angebot je Gut). Ein Gleis "
                    + "kann Bahnsteig und Ladegleis zugleich sein (zweimal mit demselben Block anlegen). Ladegleise "
                    + "kommen beim Speichern in einen angedockten Güterbahnhof. Angeschlossen werden keine Orte, "
                    + "Betriebe oder Lager. Geladen und entladen wird schon bei der Durchfahrt, ohne Halt.");
            sh.setWrapText(true);
            sh.setMaxWidth(520);
            inhalt.getChildren().add(1, sh);
            if (a.angedockterGueterbahnhof(s) == null) {
                gbfAndocken.setText("Güter: Güterbahnhof andocken (mit denselben Blöcken als Ladegleise)");
                inhalt.getChildren().add(2, gbfAndocken);
            }
        }
        if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.BAHNHOF
                || s.getArt() == com.example.itrain_logistik.kern.ObjektArt.HALTEPUNKT) {
            Station gbf = a.angedockterGueterbahnhof(s);
            Label lh = new Label("Gleise der Art Ladegleis (Güter) kommen beim Speichern in den Güterbahnhof "
                    + (gbf != null ? "„" + gbf.getAnzeigeName() + "“" : "„" + s.getAnzeigeName()
                            + " Gbf“ (wird angelegt und bedient dieselben Orte und Betriebe)") + ".");
            lh.setWrapText(true);
            lh.setMaxWidth(520);
            lh.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
            inhalt.getChildren().add(1, lh);
        }
        if (!zeige(owner, s.getArt().getLabel() + " bearbeiten", inhalt)) {
            return false;
        }
        String alterName = s.getAnzeigeName();
        if (artWahlFinal != null && artWahlFinal.getValue() != null && artWahlFinal.getValue() != s.getArt()) {
            s.setArt(artWahlFinal.getValue());
            // Gleise, die die neue Art nicht erlaubt, auf die erlaubte Gleisart umstellen
            for (Gleis g : gleise) {
                if (!s.getArt().erlaubt(g.getArt())) {
                    g.setArt(g.getArt() == GleisArt.BAHNSTEIG ? GleisArt.LADEGLEIS : GleisArt.BAHNSTEIG);
                }
            }
        }
        s.setName(name.getText().trim());
        // Fahrtrichtungen anderer Bahnsteige, die diese Station nennen, ziehen mit (Anwender 14.10.2026).
        a.stationUmbenannt(alterName, s.getAnzeigeName());
        s.getGleise().clear();
        s.getGleise().addAll(gleise);
        s.setReisendeAusOrt(false);
        if (gbfAndocken.isSelected()) {
            a.gueterbahnhofAndocken(s);
        }
        List<String> vorher = new ArrayList<>(s.getEinzugsbereich());
        s.getEinzugsbereich().clear();
        for (CheckBox cb : einzug) {
            if (cb.isSelected()) {
                s.getEinzugsbereich().add((String) cb.getUserData());
            }
        }
        // Bahnhof, Haltepunkt, Schattenbahnhof: Ladegleise wandern in den angedockten Güterbahnhof (wird bei Bedarf
        // angelegt und übernimmt beim Bahnhof/Haltepunkt den Einzugsbereich), Anwender 11. und 20.10.2026.
        a.ladegleiseAuslagern(s);
        for (String id : List.copyOf(s.getEinzugsbereich())) {
            if (!vorher.contains(id) && a.objekt(id) instanceof Ort ort) {
                nachVerbinden(owner, a, ort, s);
            }
        }
        gueterZumGbfMelden(owner, a);
        return true;
    }

    /**
     * Hängt Betriebe, Lager und Übergaben eines Bahnhofs ohne Ladegleise an dessen Güterbahnhof und sagt es dem
     * Anwender (Anwender 27.10.2026: Güter fließen nur über Ladegleise).
     */
    public static void gueterZumGbfMelden(Stage owner, Anlage a) {
        List<String> umgehaengt = a.gueterObjekteZumGueterbahnhof();
        if (umgehaengt.isEmpty()) {
            return;
        }
        Alert info = new Alert(Alert.AlertType.INFORMATION, "Güter fließen nur über Ladegleise. Deshalb mit dem "
                + "Güterbahnhof verbunden:\n" + String.join("\n", umgehaengt));
        info.setHeaderText(null);
        info.initOwner(owner);
        info.showAndWait();
    }

    /**
     * Nach dem Verbinden eines Orts mit einer Station (Anwenderentscheid 11.10.2026):
     * <ul>
     * <li>Personenbahnhof und der Ort hat Güterbedarf: der Bahnhof bekommt einen Güterbahnhof (eigenes
     * Symbol, gehört zum Bahnhof, nicht zum Ort) bzw. der Ort wird an dessen schon vorhandenen
     * angeschlossen; gefragt wird nach den Ladegleisen.</li>
     * <li>Güterbahnhof und der Ort hat Passagiere: kein neues Symbol, gefragt wird nach den
     * Personengleisen (Bahnsteige am Güterbahnhof).</li>
     * </ul>
     *
     * @return true, wenn sich etwas geändert hat
     */
    public static boolean nachVerbinden(Stage owner, Anlage a, Ort ort, Station s) {
        if (!s.bedient(ort.getId())) {
            return false;
        }
        if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.BAHNHOF) {
            Station gbf = a.gueterbahnhofFuer(ort);
            if (gbf == null) {
                return true; // ggf. an vorhandenen Güterbahnhof angeschlossen
            }
            List<String> kandidaten = new ArrayList<>();
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null && !kandidaten.contains(g.getBlockName())) {
                    kandidaten.add(g.getBlockName());
                }
            }
            kandidaten.addAll(a.freieBloecke());
            vorgewaehlt.clear();
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null) {
                    vorgewaehlt.add(g.getBlockName());
                }
            }
            frageGleise(owner, a, gbf, GleisArt.LADEGLEIS, kandidaten, "Güterbahnhof „" + gbf.getAnzeigeName()
                    + "“ angelegt", ort.getAnzeigeName() + " braucht Güter. Der Güterbahnhof gehört zu "
                    + s.getAnzeigeName() + ". Welche Blöcke sind Ladegleise? (Er darf dieselben Blöcke wie der "
                    + "Bahnhof nutzen.)");
            return true;
        }
        if (s.getArt() == com.example.itrain_logistik.kern.ObjektArt.GUETERBAHNHOF
                && ort.getPassagiereProStunde() > 0 && s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()) {
            List<String> kandidaten = new ArrayList<>();
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null && !kandidaten.contains(g.getBlockName())) {
                    kandidaten.add(g.getBlockName());
                }
            }
            kandidaten.addAll(a.freieBloecke());
            return frageGleise(owner, a, s, GleisArt.BAHNSTEIG, kandidaten, "Personengleise",
                    ort.getAnzeigeName() + " hat Passagiere. Welche Blöcke von " + s.getAnzeigeName()
                            + " sind Personengleise (Bahnsteige)? Keine Auswahl = keine Personen.");
        }
        return false;
    }

    /** Vorauswahl für {@link #frageGleise}: die Blöcke des zugehörigen Personenbahnhofs. */
    private static final Set<String> vorgewaehlt = new LinkedHashSet<>();

    /** Fragt per Häkchen, welche Blöcke Gleise der Art {@code art} der Station werden. */
    private static boolean frageGleise(Stage owner, Anlage a, Station s, GleisArt art, List<String> kandidaten,
            String titel, String text) {
        Label hinweis = new Label(text);
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(460);
        VBox liste = new VBox(4);
        List<CheckBox> boxen = new ArrayList<>();
        for (String b : kandidaten) {
            boolean schonDa = s.getGleise().stream().anyMatch(g -> b.equals(g.getBlockName()) && g.getArt() == art);
            if (schonDa || a.blockBelegt(b, art, null)) {
                continue;
            }
            CheckBox cb = new CheckBox(b);
            cb.setUserData(b);
            cb.setSelected(art == GleisArt.LADEGLEIS && vorgewaehlt.contains(b));
            boxen.add(cb);
            liste.getChildren().add(cb);
        }
        if (boxen.isEmpty()) {
            liste.getChildren().add(new Label("Keine freien Blöcke – Gleise später in der Station zuordnen."));
        }
        ScrollPane sp = new ScrollPane(liste);
        sp.setPrefHeight(220);
        sp.setFitToWidth(true);
        if (!zeige(owner, titel, new VBox(10, hinweis, sp))) {
            return false;
        }
        boolean geaendert = false;
        for (CheckBox cb : boxen) {
            if (cb.isSelected()) {
                Gleis g = new Gleis(null, art);
                g.setBlockName((String) cb.getUserData());
                g.setName((String) cb.getUserData());
                s.getGleise().add(g);
                geaendert = true;
            }
        }
        return geaendert;
    }

    private static boolean gleis(Stage owner, Gleis g, Station s, Anlage a, List<BlockEntry> bloecke,
            List<Gleis> gleiseDieserStation) {
        TextField name = new TextField(g.getName() == null ? "" : g.getName());
        // Freie Bloecke: nicht schon einem anderen Gleis DERSELBEN Art zugeordnet. Ein Block kann
        // zugleich Bahnsteig (Bahnhof) und Ladegleis (Gueterbahnhof) sein.
        Map<String, Set<GleisArt>> belegt = new LinkedHashMap<>();
        for (Station andere : a.stationen()) {
            for (Gleis x : andere == s ? gleiseDieserStation : andere.getGleise()) {
                // Im Schattenbahnhof darf ein Block mehrfach belegt werden (Güter und Personen, mehrere Güter).
                boolean mehrfach = s.getArt() == com.example.itrain_logistik.kern.ObjektArt.SCHATTENBAHNHOF && andere == s;
                if (x != g && x.getBlockName() != null && !mehrfach) {
                    belegt.computeIfAbsent(x.getBlockName(), k -> java.util.EnumSet.noneOf(GleisArt.class)).add(x.getArt());
                }
            }
        }
        ObservableList<String> frei = FXCollections.observableArrayList();
        ComboBox<String> block = new ComboBox<>(frei);
        block.setEditable(true);
        block.setValue(g.getBlockName());
        // Name zunächst wie der Block, bis der Anwender ihn selbst ändert.
        if (name.getText().isBlank() && g.getBlockName() != null) {
            name.setText(g.getBlockName());
        }
        block.valueProperty().addListener((obs, alt, neu) -> {
            String n = name.getText();
            if (neu != null && (n == null || n.isBlank() || n.equals(alt))) {
                name.setText(neu);
            }
        });
        ComboBox<GleisArt> art = new ComboBox<>();
        for (GleisArt ga : GleisArt.values()) {
            if (s.getArt().erlaubt(ga)) {
                art.getItems().add(ga);
            }
        }
        art.setConverter(konverter(GleisArt::getLabel));
        art.setValue(g.getArt());
        Runnable freieFuellen = () -> {
            String gewaehlt = block.getValue();
            List<String> l = new ArrayList<>();
            for (BlockEntry b : bloecke) {
                if (!belegt.getOrDefault(b.getName(), Set.of()).contains(art.getValue())) {
                    l.add(b.getName());
                }
            }
            frei.setAll(l);
            block.setValue(gewaehlt);
        };
        freieFuellen.run();
        art.valueProperty().addListener((obs, x, y) -> freieFuellen.run());
        // Fahrtrichtungen: bevorzugt definierte Symbole (Stationen), danach bekannte Blöcke.
        List<String> richtungsListe = new ArrayList<>(g.getRichtungen());
        javafx.scene.layout.FlowPane richtungChips = new javafx.scene.layout.FlowPane(6, 4);
        richtungChips.setPrefWrapLength(330);
        ComboBox<String> richtungWahl = new ComboBox<>();
        richtungWahl.setEditable(true);
        richtungWahl.setPromptText("Symbol oder Block wählen");
        richtungWahl.setPrefWidth(240);
        for (Station andere : a.stationen()) {
            if (andere != s && !richtungWahl.getItems().contains(andere.getAnzeigeName())) {
                richtungWahl.getItems().add(andere.getAnzeigeName());
            }
        }
        for (BlockEntry b : bloecke) {
            if (!richtungWahl.getItems().contains(b.getName())) {
                richtungWahl.getItems().add(b.getName());
            }
        }
        java.util.Set<String> symbolNamen = new java.util.HashSet<>();
        a.stationen().forEach(x -> symbolNamen.add(x.getAnzeigeName()));
        richtungWahl.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (symbolNamen.contains(item) ? "◆ " : "▭ Block ") + item);
            }
        });
        Runnable chipsZeigen = new Runnable() {
            @Override
            public void run() {
                richtungChips.getChildren().clear();
                for (String r : List.copyOf(richtungsListe)) {
                    Button chip = new Button((symbolNamen.contains(r) ? "◆ " : "▭ ") + r + "  ✕");
                    chip.setStyle("-fx-background-radius: 10; -fx-padding: 1 8 1 8;");
                    chip.setOnAction(e -> {
                        richtungsListe.remove(r);
                        run();
                    });
                    richtungChips.getChildren().add(chip);
                }
                if (richtungsListe.isEmpty()) {
                    richtungChips.getChildren().add(new Label("keine (keine Trennung nach Richtung)"));
                }
            }
        };
        chipsZeigen.run();
        Button richtungPlus = new Button("Hinzufügen");
        richtungPlus.setOnAction(e -> {
            String r = richtungWahl.getEditor().getText();
            if (r == null || r.isBlank()) {
                r = richtungWahl.getValue();
            }
            if (r != null && !r.isBlank() && !richtungsListe.contains(r.trim())) {
                richtungsListe.add(r.trim());
                chipsZeigen.run();
            }
            javafx.application.Platform.runLater(() -> {
                richtungWahl.getEditor().clear();
                richtungWahl.setValue(null);
            });
        });
        // Auswahl aus der Liste wird sofort übernommen (kein extra Klick auf "Hinzufügen" nötig).
        richtungWahl.setOnAction(e -> {
            String v = richtungWahl.getValue();
            if (v != null && !v.isBlank() && richtungWahl.getItems().contains(v) && !richtungsListe.contains(v)) {
                richtungsListe.add(v);
                chipsZeigen.run();
                javafx.application.Platform.runLater(() -> {
                    richtungWahl.getEditor().clear();
                    richtungWahl.setValue(null);
                });
            }
        });
        CheckBox laden = new CheckBox();
        laden.setSelected(g.isLaden());
        CheckBox entladen = new CheckBox();
        entladen.setSelected(g.isEntladen());
        CheckBox erzwingen = new CheckBox("Entladen aller Güter erzwingen (auch ohne Abnehmer)");
        erzwingen.setSelected(g.isEntladenErzwingen());
        // Bedienseite eines Ladegleises (Anwender 09.11.2026): fährt ein Zug anders ein, muss er Kopf machen
        ComboBox<String> bedien = new ComboBox<>(FXCollections.observableArrayList(
                "Von beiden Seiten", "Nur Einfahrt vorwärts (iTrain „next“)", "Nur Einfahrt rückwärts (iTrain „prev“)"));
        bedien.getSelectionModel().select("next".equals(g.getBedienSeite()) ? 1 : "prev".equals(g.getBedienSeite()) ? 2 : 0);
        Label bedienLabel = new Label("Bedienung des Ladegleises (Richtung kommt über XTC):");
        TextField kapazitaet = new TextField(String.valueOf(g.getKapazitaet()));
        TextField rate = new TextField(zahl(g.getUmladeRate()));
        Set<String> auswahl = new LinkedHashSet<>(g.getGueterAuswahl());
        // Gewählte Güter als farbige Symbole in einem vertieften Feld (mindestens zwei Zeilen hoch).
        javafx.scene.layout.FlowPane auswahlFeld = new javafx.scene.layout.FlowPane(4, 4);
        auswahlFeld.setPrefWrapLength(320);
        auswahlFeld.setMinHeight(62);
        auswahlFeld.setPadding(new Insets(5));
        auswahlFeld.setStyle("-fx-background-color: derive(-fx-control-inner-background, -4%); "
                + "-fx-border-color: derive(-fx-base, -35%) derive(-fx-base, 25%) derive(-fx-base, 25%) derive(-fx-base, -35%); "
                + "-fx-border-width: 2; -fx-effect: innershadow(gaussian, rgba(0,0,0,0.35), 6, 0, 1, 1);");
        Runnable auswahlZeigen = () -> {
            auswahlFeld.getChildren().clear();
            for (String id : auswahl) {
                Node sym = Symbole.gut(a.gut(id), 22);
                auswahlFeld.getChildren().add(sym);
            }
            if (auswahl.isEmpty()) {
                auswahlFeld.getChildren().add(new Label("alle Güter"));
            }
        };
        auswahlZeigen.run();
        Button auswahlKnopf = new Button("Güter wählen…");
        auswahlKnopf.setOnAction(e -> {
            Set<String> neu = gueterWaehlen(owner, a, auswahl, "Güter an diesem Gleis (keins = alle)");
            if (neu != null) {
                auswahl.clear();
                auswahl.addAll(neu);
                auswahlZeigen.run();
            }
        });
        VBox auswahlBox = new VBox(6, auswahlFeld, auswahlKnopf);
        Label kapHinweis = new Label();
        Label auswahlLabel = new Label("Güterauswahl");
        // Mit Koffer (Anwender 27.10.2026): 30 % der Reisenden haben einen Koffer, Gegenstück nötig
        CheckBox mitKoffer = new CheckBox("Mit Koffer (" + Math.round(Gleis.KOFFER_ANTEIL * 100)
                + " % der Reisenden haben einen Koffer und brauchen einen Gepäckwagen im Zug)");
        mitKoffer.setSelected(g.isMitKoffer());
        Label kofferHinweis = new Label();
        kofferHinweis.setWrapText(true);
        kofferHinweis.setStyle("-fx-text-fill: #d07a00;");
        Runnable kofferPruefen = () -> {
            boolean gegenstueck = false;
            for (Station x : a.stationen()) {
                if (x != s && x != a.gehoertZu(s) && x != a.angedockterGueterbahnhof(s)) {
                    for (Gleis gl : x.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                        gegenstueck |= gl.isMitKoffer();
                    }
                }
            }
            boolean zeigen = mitKoffer.isSelected() && !gegenstueck && art.getValue() == GleisArt.BAHNSTEIG;
            kofferHinweis.setText(zeigen ? "Gegenstück fehlt: Ein anderer Bahnhof oder der Schattenbahnhof muss "
                    + "ebenfalls einen Bahnsteig „Mit Koffer“ haben, sonst können Kofferreisende nirgends aussteigen."
                    : "");
            kofferHinweis.setVisible(zeigen);
            kofferHinweis.setManaged(zeigen);
        };
        mitKoffer.selectedProperty().addListener((obs, x, y) -> kofferPruefen.run());
        Runnable hinweis = () -> {
            boolean personen = art.getValue() == GleisArt.BAHNSTEIG;
            kapHinweis.setText(personen
                    ? "Kapazität = Wartende je Richtung, Umladen = Personen je Modellminute"
                    : "Kapazität = Einheiten je Gut (Abholung bzw. Anlieferung), Umladen = Einheiten je Modellminute");
            // Personen: Zu-/Aussteigen, Güter: Laden/Entladen
            laden.setText(personen ? "Zusteigen erlaubt" : "Laden erlaubt");
            entladen.setText(personen ? "Aussteigen erlaubt" : "Entladen erlaubt");
            for (Node n : List.of(erzwingen, auswahlBox, auswahlLabel, bedien, bedienLabel)) {
                n.setVisible(!personen);
                n.setManaged(!personen);
            }
            mitKoffer.setVisible(personen);
            mitKoffer.setManaged(personen);
            kofferPruefen.run();
        };
        art.valueProperty().addListener((obs, x, y) -> hinweis.run());
        hinweis.run();

        GridPane grid = grid();
        zeile(grid, 0, "Name (optional)", name);
        zeile(grid, 1, "iTrain-Block", block);
        zeile(grid, 2, "Gleisart", art);
        zeile(grid, 3, "Fahrtrichtungen", new VBox(6, richtungChips, new HBox(6, richtungWahl, richtungPlus)));
        zeile(grid, 4, "", new VBox(4, laden, entladen, erzwingen, mitKoffer, kofferHinweis, bedienLabel, bedien));
        zeile(grid, 5, "Kapazität", kapazitaet);
        zeile(grid, 6, "Umladen je Modellminute", rate);
        grid.add(auswahlLabel, 0, 7);
        GridPane.setValignment(auswahlLabel, javafx.geometry.VPos.TOP);
        grid.add(auswahlBox, 1, 7);
        kapHinweis.setWrapText(true);
        VBox inhalt = new VBox(8, grid, kapHinweis);
        inhalt.setPrefWidth(560);
        // Außenwelt (Schattenbahnhof bzw. sein angedockter Güterbahnhof): Mengen wie bei einer Stadt.
        boolean aussenwelt = a.istAussenwelt(s);
        // Schattenbahnhof (Anwender 09.11.2026): ein Gleis kann Bahnsteig und Ladegleis zugleich sein; das Ladegleis
        // steht im angedockten Güterbahnhof. Dazu je Gleis: Laden/Entladen, Ein-/Aussteigen auch ohne Halt.
        Station sbf = s.getArt() == com.example.itrain_logistik.kern.ObjektArt.SCHATTENBAHNHOF ? s : a.gehoertZu(s);
        Station sbfGbf = sbf != null ? a.angedockterGueterbahnhof(sbf) : null;
        java.util.function.Function<GleisArt, Gleis> partnerVon = ga -> {
            String b = block.getValue() == null ? g.getBlockName() : block.getValue().trim();
            if (b == null || b.isBlank()) {
                return null;
            }
            List<List<Gleis>> orte = new ArrayList<>();
            orte.add(gleiseDieserStation);
            if (sbf != null && sbf != s) {
                orte.add(sbf.getGleise());
            }
            if (sbfGbf != null && sbfGbf != s) {
                orte.add(sbfGbf.getGleise());
            }
            for (List<Gleis> l : orte) {
                for (Gleis x : l) {
                    if (x != g && b.equals(x.getBlockName()) && x.getArt() == ga) {
                        return x;
                    }
                }
            }
            return null;
        };
        CheckBox beide = new CheckBox("Bahnsteig und Ladegleis (beides an diesem Gleis)");
        CheckBox durchfahrt = new CheckBox("Laden/Entladen bzw. Ein-/Aussteigen auch ohne Halt (bei der Durchfahrt)");
        durchfahrt.setSelected(g.isDurchfahrt());
        if (aussenwelt) {
            GleisArt andere = g.getArt() == GleisArt.BAHNSTEIG ? GleisArt.LADEGLEIS : GleisArt.BAHNSTEIG;
            beide.setSelected(partnerVon.apply(andere) != null);
            beide.setTooltip(new javafx.scene.control.Tooltip("Das Ladegleis steht im angedockten Güterbahnhof, "
                    + "der Bahnsteig im Schattenbahnhof – derselbe iTrain-Block."));
            grid.add(new VBox(4, beide, durchfahrt), 1, 8);
            // nicht nach „Güterbahnhof“ umschalten lassen, wenn beides gewählt ist
            art.disableProperty().bind(beide.selectedProperty());
        }
        // Personenwerte bei „beides“ vom Bahnsteig-Partner im Schattenbahnhof
        Gleis steigPartner = aussenwelt && g.getArt() == GleisArt.LADEGLEIS ? partnerVon.apply(GleisArt.BAHNSTEIG) : null;
        Gleis steigQuelle = steigPartner != null ? steigPartner : g;
        TextField proStunde = LogistikFeldRow.ziffern(new TextField(zahl(Math.round(steigQuelle.getPassagiereProStunde()))), 4);
        TextField ersteKl = new TextField(zahl(steigQuelle.getAnteilErsteKlasse() * 100));
        // Angebot des Ladegleises: bei „beides“ das des Ladegleis-Partners im Gbf
        Gleis ladePartner = aussenwelt && g.getArt() == GleisArt.BAHNSTEIG ? partnerVon.apply(GleisArt.LADEGLEIS) : null;
        MengenEditor angebot = new MengenEditor(owner, a, ladePartner != null ? ladePartner.getAngebot()
                : g.getAngebot(), "Angebot M/h", null, null);
        Map<String, Object[]> postWerte = new java.util.LinkedHashMap<>();
        if (aussenwelt) {
            GridPane pg = grid();
            zeile(pg, 0, "Abreisende M/h", proStunde);
            zeile(pg, 1, "Anteil 1. Klasse (%)", ersteKl);
            Button pVorschlag = new Button("Vorschlag…");
            pVorschlag.setOnAction(e -> {
                MengenEditor.Vorschlag v = MengenEditor.waehle(owner, a);
                if (v != null) {
                    proStunde.setText(zahl(v.passagiere()));
                    ersteKl.setText(zahl(v.ersteKlasse() * 100));
                }
            });
            VBox personenTeil = new VBox(8, ueberschrift("Außenwelt: Personen an diesem Bahnsteig"), pg, pVorschlag);
            // Post und Expressgut je Bahnsteig (Anwender 27.10.2026)
            GridPane postGrid = grid();
            postGrid.addRow(0, new Label(""), new Label("Nimmt an"), new Label("Erzeugt Stück M/h"));
            int zeileNr = 1;
            for (String gutId : List.of(Tf3Daten.POST, Tf3Daten.EXPRESSGUT)) {
                CheckBox an = new CheckBox();
                an.setSelected(steigQuelle.nimmtAn(gutId));
                TextField erz = LogistikFeldRow.ziffern(new TextField(zahl(Math.round(steigQuelle.erzeugt(gutId)))), 3);
                erz.setPrefColumnCount(4);
                erz.setDisable(!a.reisendeVonAussen(s));
                postGrid.addRow(zeileNr++, new Label(Tf3Daten.POST.equals(gutId) ? "Post" : "Expressgut"), an, erz);
                postWerte.put(gutId, new Object[]{an, erz});
            }
            Label postHinweis = new Label(a.reisendeVonAussen(s)
                    ? "Angenommene Post/Expressgut verlässt die Anlage. Erzeugt wird nur, wenn Zusteigen erlaubt ist."
                    : "Erzeugt wird hier vom angeschlossenen Ort (Reisende aus Stadt/Dorf/Ländlich).");
            postHinweis.setWrapText(true);
            postHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
            VBox postTeil = new VBox(8, ueberschrift("Post und Expressgut an diesem Bahnsteig"), postGrid, postHinweis);
            VBox gueterTeil = new VBox(8, ueberschrift("Außenwelt: Güter, die hier bereitstehen"), angebot.getView(),
                    angebot.getKnoepfe());
            // nebeneinander statt untereinander (Anwender 09.11.2026: Fenster zu groß)
            VBox linksTeil = new VBox(10, personenTeil, postTeil);
            HBox teile = new HBox(16, linksTeil, gueterTeil);
            inhalt.getChildren().addAll(new Separator(), teile);
            Runnable umschalten = () -> {
                boolean personen = art.getValue() == GleisArt.BAHNSTEIG || beide.isSelected();
                boolean gueterSicht = art.getValue() == GleisArt.LADEGLEIS || beide.isSelected();
                boolean aussen = personen && a.reisendeVonAussen(s);
                personenTeil.setVisible(aussen);
                personenTeil.setManaged(aussen);
                postTeil.setVisible(personen);
                postTeil.setManaged(personen);
                linksTeil.setVisible(personen);
                linksTeil.setManaged(personen);
                gueterTeil.setVisible(gueterSicht);
                gueterTeil.setManaged(gueterSicht);
                // Die Außenwelt nimmt alle Güter ab: keine Güterauswahl.
                auswahlBox.setVisible(false);
                auswahlBox.setManaged(false);
                auswahlLabel.setVisible(false);
                auswahlLabel.setManaged(false);
            };
            art.valueProperty().addListener((obs, x, y) -> umschalten.run());
            beide.selectedProperty().addListener((obs, x, y) -> umschalten.run());
            umschalten.run();
        }
        // Höhe begrenzen und rollen; die gemerkte Fenstergröße gilt (Anwender 09.11.2026: zu groß)
        ScrollPane rollen = new ScrollPane(inhalt);
        rollen.setFitToWidth(true);
        rollen.setPrefViewportHeight(470);
        rollen.setPrefViewportWidth(aussenwelt ? 820 : 600);
        rollen.setStyle("-fx-background-color: transparent;");
        if (!zeige(owner, "Gleis bearbeiten", rollen)) {
            return false;
        }
        String blockName = block.getValue() == null || block.getValue().isBlank() ? null : block.getValue().trim();
        if (blockName != null && belegt.getOrDefault(blockName, Set.of()).contains(art.getValue())) {
            new Alert(Alert.AlertType.WARNING, "Der Block \"" + blockName + "\" ist schon einem anderen "
                    + art.getValue().getLabel() + " zugeordnet. Ein Block kann höchstens ein Bahnsteig und ein "
                    + "Ladegleis sein.").showAndWait();
            return false;
        }
        g.setName(name.getText().isBlank() ? null : name.getText().trim());
        g.setBlockName(blockName);
        if (art.getValue() != null && art.getValue() != g.getArt()) {
            g.setArt(art.getValue());
            g.getAusgang().clear();
            g.getEingang().clear();
            g.getWartende().clear();
        }
        // Noch eingetippter, aber nicht hinzugefügter Text zählt mit.
        String offen = richtungWahl.getEditor().getText();
        if (offen != null && !offen.isBlank() && !richtungsListe.contains(offen.trim())) {
            richtungsListe.add(offen.trim());
        }
        g.getRichtungen().clear();
        g.getRichtungen().addAll(richtungsListe);
        g.getWartende().keySet().removeIf(r -> !g.warteschlangen().contains(r));
        g.setMitKoffer(mitKoffer.isSelected() && art.getValue() == GleisArt.BAHNSTEIG);
        g.setLaden(laden.isSelected());
        g.setEntladen(entladen.isSelected());
        g.setEntladenErzwingen(erzwingen.isSelected());
        int bs = bedien.getSelectionModel().getSelectedIndex();
        g.setBedienSeite(bs == 1 ? "next" : bs == 2 ? "prev" : null);
        g.setKapazitaet((int) parse(kapazitaet.getText(), g.getKapazitaet()));
        g.setUmladeRate(parse(rate.getText(), g.getUmladeRate()));
        g.getGueterAuswahl().clear();
        g.getGueterAuswahl().addAll(auswahl);
        if (aussenwelt) {
            g.setDurchfahrt(durchfahrt.isSelected());
            GleisArt andere = g.getArt() == GleisArt.BAHNSTEIG ? GleisArt.LADEGLEIS : GleisArt.BAHNSTEIG;
            Gleis partner = partnerVon.apply(andere);
            if (beide.isSelected() && blockName != null) {
                if (partner == null) {
                    partner = new Gleis(null, andere);
                    partner.setBlockName(blockName);
                    partner.setName(g.getName());
                    if (s == sbf || andere == GleisArt.LADEGLEIS) {
                        gleiseDieserStation.add(partner); // Ladegleis wandert beim Speichern in den Gbf
                    } else {
                        sbf.getGleise().add(partner); // Gleis im Gbf: Bahnsteig gehört in den Schattenbahnhof
                    }
                }
                partner.setDurchfahrt(durchfahrt.isSelected());
                partner.setLaden(laden.isSelected());
                partner.setEntladen(entladen.isSelected());
            } else if (partner != null) {
                // „beides“ abgewählt: das andere Gleis desselben Blocks entfällt
                if (!gleiseDieserStation.remove(partner)) {
                    if (sbf != null) {
                        sbf.getGleise().remove(partner);
                    }
                    if (sbfGbf != null) {
                        sbfGbf.getGleise().remove(partner);
                    }
                }
                partner = null;
            }
            Gleis steig = g.getArt() == GleisArt.BAHNSTEIG ? g : partner;
            Gleis lade = g.getArt() == GleisArt.LADEGLEIS ? g : partner;
            if (steig != null) {
                postWerte.forEach((gutId, w) -> {
                    steig.setAnnehmen(gutId, ((CheckBox) w[0]).isSelected());
                    steig.setErzeugt(gutId, Math.round(parse(((TextField) w[1]).getText(), steig.erzeugt(gutId))));
                });
                steig.setPassagiereProStunde(parse(proStunde.getText(), steig.getPassagiereProStunde()));
                steig.setAnteilErsteKlasse(parse(ersteKl.getText(), 10) / 100.0);
            }
            if (lade != null) {
                lade.getAngebot().clear();
                lade.getAngebot().putAll(angebot.getMengen());
                lade.getGueterAuswahl().clear();
            }
        }
        return true;
    }

    // ================================================================ Betrieb

    /** Eine Rezeptzeile in der Tabelle. */
    private record RezeptZeile(int rezept, boolean eingang, String gut, int menge) {
    }

    private static boolean betrieb(Stage owner, Betrieb b, Anlage a) {
        TextField name = new TextField(b.getAnzeigeName());
        TextField rate = new TextField(zahl(b.getRateProStunde()));

        ObservableList<RezeptZeile> zeilen = FXCollections.observableArrayList();
        for (int i = 0; i < b.getRezepte().size(); i++) {
            Rezept r = b.getRezepte().get(i);
            final int nr = i + 1;
            r.getEingang().forEach((g, m) -> zeilen.add(new RezeptZeile(nr, true, g, m)));
            r.getAusgang().forEach((g, m) -> zeilen.add(new RezeptZeile(nr, false, g, m)));
        }
        TableView<RezeptZeile> rezepte = new TableView<>(zeilen);
        Spalten.einrichten(rezepte, "betrieb.rezepte");
        rezepte.setPrefHeight(170);
        rezepte.getColumns().add(spalte("Rezept", 60, z -> String.valueOf(z.rezept())));
        rezepte.getColumns().add(spalte("", 80, z -> z.eingang() ? "Eingang" : "Ausgang"));
        rezepte.getColumns().add(spalte("Gut", 160, z -> a.gutName(z.gut())));
        rezepte.getColumns().add(spalte("Menge je Ausführung", 140, z -> String.valueOf(z.menge())));
        // "Wird immer geliefert" wird im Dialog geändert, die Spalte folgt sofort (Anwender 12.10.2026).
        Set<String> immer = new LinkedHashSet<>(b.getImmerGeliefert());
        boolean[] virtuellAn = {b.isVirtuell()};
        TableColumn<RezeptZeile, String> geliefertVon = spalte("Geliefert von", 260, z -> z.eingang()
                ? com.example.itrain_logistik.kern.Kreislauf.geliefertVon(a, b, z.gut(), immer.contains(z.gut()),
                        virtuellAn[0])
                : com.example.itrain_logistik.kern.Kreislauf.geliefertAn(a, b, z.gut()));
        geliefertVon.setCellFactory(c -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String text, boolean empty) {
                super.updateItem(text, empty);
                setText(empty ? null : text);
                setStyle(!empty && text != null && text.startsWith("–") ? "-fx-text-fill: #cc3333; -fx-font-weight: bold;" : "");
            }
        });
        rezepte.getColumns().add(geliefertVon);

        TextField nr = new TextField("1");
        nr.setPrefColumnCount(2);
        ComboBox<String> richtung = new ComboBox<>(FXCollections.observableArrayList("Eingang", "Ausgang"));
        richtung.setValue("Ausgang");
        ComboBox<Gut> gutBox = gutCombo(a, false);
        TextField menge = new TextField("1");
        menge.setPrefColumnCount(3);
        Button hinzu = new Button("Hinzufügen");
        hinzu.setOnAction(e -> {
            if (gutBox.getValue() == null) {
                return;
            }
            int n = Math.max(1, (int) parse(nr.getText(), 1));
            boolean ein = "Eingang".equals(richtung.getValue());
            zeilen.removeIf(z -> z.rezept() == n && z.eingang() == ein && z.gut().equals(gutBox.getValue().getId()));
            zeilen.add(new RezeptZeile(n, ein, gutBox.getValue().getId(), Math.max(1, (int) parse(menge.getText(), 1))));
        });
        Button weg = new Button("Entfernen");
        weg.setOnAction(e -> zeilen.remove(rezepte.getSelectionModel().getSelectedItem()));

        // Lagerkapazitaeten je Gut
        Map<String, TextField> kapEin = new LinkedHashMap<>();
        Map<String, TextField> kapAus = new LinkedHashMap<>();
        VBox lagerBox = new VBox(4);
        Runnable lagerAufbauen = () -> {
            lagerBox.getChildren().clear();
            Set<String> ein = new LinkedHashSet<>();
            Set<String> aus = new LinkedHashSet<>();
            for (RezeptZeile z : zeilen) {
                (z.eingang() ? ein : aus).add(z.gut());
            }
            GridPane g = grid();
            int zeile = 0;
            for (String gut : ein) {
                TextField f = kapEin.computeIfAbsent(gut, x -> new TextField(String.valueOf(
                        b.getLagerEin().getOrDefault(x, 50))));
                f.setPrefColumnCount(5);
                zeile(g, zeile++, "Eingangslager " + a.gutName(gut), new HBox(6, f, new Label("Bestand "
                        + Symbole.zahl(b.getBestandEin().getOrDefault(gut, 0.0)))));
            }
            for (String gut : aus) {
                TextField f = kapAus.computeIfAbsent(gut, x -> new TextField(String.valueOf(
                        b.getLagerAus().getOrDefault(x, 50))));
                f.setPrefColumnCount(5);
                zeile(g, zeile++, "Ausgangslager " + a.gutName(gut), new HBox(6, f, new Label("Bestand "
                        + Symbole.zahl(b.getBestandAus().getOrDefault(gut, 0.0)))));
            }
            lagerBox.getChildren().add(g);
        };
        zeilen.addListener((javafx.collections.ListChangeListener<RezeptZeile>) c -> lagerAufbauen.run());
        lagerAufbauen.run();

        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        zeile(grid, 1, "Vorlage", new Label(b.getVorlageId() == null ? "eigener Betrieb" : b.getVorlageId()));
        zeile(grid, 2, "Ausführungen M/h", rate);
        Label hinweis = new Label("Mehrere Rezepte (Nummer 1, 2, …) sind Alternativen: es läuft das erste, "
                + "für das alle Eingänge vorrätig sind.");
        hinweis.setWrapText(true);
        CheckBox virtuell = new CheckBox("Liefert immer, ohne auf der Anlage zu sein");
        virtuell.setSelected(b.isVirtuell());
        virtuell.setTooltip(new javafx.scene.control.Tooltip("Der Betrieb wird nicht gerechnet; seine Ausgangsgüter "
                + "stehen allen Verbrauchern unbegrenzt zur Verfügung, ohne Zug."));
        CheckBox dynamisch = new CheckBox("Dynamisch: mal mehr, mal weniger herstellen");
        dynamisch.setSelected(b.isDynamisch());
        dynamisch.setTooltip(new javafx.scene.control.Tooltip("Die Erzeugung schwankt je Modellstunde zwischen 50 % und "
                + "150 % der Rate (im Mittel gleich). Überschuss wird am Gleis bzw. im Lager gehalten und nach Bedarf "
                + "weitergegeben."));
        // Lieferung in Schüben (Anwender 27.10.2026)
        TextField schubStunden = new TextField(b.getLieferAlleStunden() > 0 ? zahl(b.getLieferAlleStunden()) : "0");
        schubStunden.setPrefColumnCount(4);
        Label schubText = new Label("Stunden (0 = laufend)");
        HBox schubZeile = new HBox(6, new Label("Liefert alle"), schubStunden, schubText);
        schubZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.control.Tooltip.install(schubZeile, new javafx.scene.control.Tooltip("Der Betrieb sammelt, was er "
                + "herstellt, und gibt es alle so viele Modellstunden auf einmal ab – z. B. 5 Gebinde Fisch alle 6 "
                + "Stunden. Ein Lager gibt es danach Stück für Stück weiter."));
        javafx.scene.layout.FlowPane immerText = new javafx.scene.layout.FlowPane(6, 4);
        immerText.setPrefWrapLength(320);
        Button immerKnopf = new Button("Güter wählen…");
        // „Liefert immer, ohne auf der Anlage zu sein“ heißt: alle Eingänge werden immer geliefert. Das steht dann
        // auch hier, statt eines leeren Felds (Anwender 27.10.2026: Viehzuchtbetrieb aus dem Verkehrs-Assistenten).
        Runnable immerZeigen = () -> {
            immerText.getChildren().clear();
            Set<String> gezeigt = new LinkedHashSet<>(immer);
            if (virtuellAn[0]) {
                for (RezeptZeile z : zeilen) {
                    if (z.eingang()) {
                        gezeigt.add(z.gut());
                    }
                }
            }
            for (String g : gezeigt) {
                immerText.getChildren().add(Symbole.gutBadge(a.gut(g), a.gutName(g), Symbole.Pfeil.KEINER));
            }
            if (virtuellAn[0]) {
                Label grund = new Label("(alle Eingänge – unten „Liefert immer“ angehakt)");
                grund.setMinWidth(Region.USE_PREF_SIZE);
                grund.setTooltip(new javafx.scene.control.Tooltip("„Liefert immer, ohne auf der Anlage zu sein“ ist "
                        + "angehakt: alle Eingänge werden immer geliefert. Haken entfernen, um einzelne Güter zu wählen."));
                grund.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
                immerText.getChildren().add(grund);
            } else if (gezeigt.isEmpty()) {
                immerText.getChildren().add(new Label("–"));
            }
            immerKnopf.setDisable(virtuellAn[0]);
            rezepte.refresh();
        };
        immerZeigen.run();
        virtuell.selectedProperty().addListener((obs, alt, neu) -> {
            virtuellAn[0] = neu;
            immerZeigen.run();
        });
        zeilen.addListener((javafx.collections.ListChangeListener<RezeptZeile>) c -> immerZeigen.run());
        immerKnopf.setOnAction(e -> {
            Set<String> neu = gueterWaehlen(owner, a, immer, "Eingangsgüter, die immer geliefert werden");
            if (neu != null) {
                immer.clear();
                immer.addAll(neu);
                immerZeigen.run();
            }
        });
        zeile(grid, 3, "Wird immer geliefert", new HBox(8, immerText, immerKnopf));
        ScrollPane lagerScroll = new ScrollPane(lagerBox);
        lagerScroll.setPrefHeight(150);
        VBox inhalt = new VBox(8, grid, new Label("Rezepte"), rezepte,
                new HBox(6, new Label("Nr."), nr, richtung, gutBox, menge, hinzu, weg), hinweis,
                new Label("Lagerkapazitäten (Einheiten)"), lagerScroll, new Separator(), schubZeile, dynamisch, virtuell);
        if (!zeige(owner, "Betrieb bearbeiten", inhalt)) {
            return false;
        }
        b.setName(name.getText().trim());
        b.setRateProStunde(parse(rate.getText(), b.getRateProStunde()));
        b.setVirtuell(virtuell.isSelected());
        b.setDynamisch(dynamisch.isSelected());
        b.setLieferAlleStunden(parse(schubStunden.getText(), b.getLieferAlleStunden()));
        b.getImmerGeliefert().clear();
        b.getImmerGeliefert().addAll(immer);
        Map<Integer, Rezept> neu = new java.util.TreeMap<>();
        for (RezeptZeile z : zeilen) {
            Rezept r = neu.computeIfAbsent(z.rezept(), x -> new Rezept());
            (z.eingang() ? r.getEingang() : r.getAusgang()).put(z.gut(), z.menge());
        }
        b.getRezepte().clear();
        b.getRezepte().addAll(neu.values());
        Set<String> ein = new LinkedHashSet<>(b.eingangsGueter());
        Set<String> aus = new LinkedHashSet<>(b.ausgangsGueter());
        b.getLagerEin().keySet().retainAll(ein);
        b.getLagerAus().keySet().retainAll(aus);
        for (String g : ein) {
            TextField f = kapEin.get(g);
            b.getLagerEin().put(g, (int) parse(f != null ? f.getText() : "50", 50));
        }
        for (String g : aus) {
            TextField f = kapAus.get(g);
            b.getLagerAus().put(g, (int) parse(f != null ? f.getText() : "50", 50));
        }
        b.getImmerGeliefert().retainAll(ein);
        b.getBestandEin().keySet().retainAll(ein);
        b.getBestandAus().keySet().retainAll(aus);
        return true;
    }

    // ================================================================ Lager, Uebergabe

    private static boolean lager(Stage owner, Lager l) {
        TextField name = new TextField(l.getAnzeigeName());
        TextField kap = new TextField(String.valueOf(l.getKapazitaet()));
        List<CheckBox> klassen = new ArrayList<>();
        VBox klassenBox = new VBox(4);
        for (Ladeklasse k : Ladeklasse.gueterKlassen()) {
            CheckBox cb = new CheckBox(k.getLabel());
            cb.setUserData(k);
            cb.setSelected(l.getKlassen().contains(k));
            klassen.add(cb);
            klassenBox.getChildren().add(cb);
        }
        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        zeile(grid, 1, "Kapazität gesamt (Einheiten)", kap);
        zeile(grid, 2, "Ladeklassen (keine = alle)", klassenBox);
        if (!zeige(owner, "Lager bearbeiten", grid)) {
            return false;
        }
        l.setName(name.getText().trim());
        l.setKapazitaet((int) parse(kap.getText(), l.getKapazitaet()));
        l.getKlassen().clear();
        for (CheckBox cb : klassen) {
            if (cb.isSelected()) {
                l.getKlassen().add((Ladeklasse) cb.getUserData());
            }
        }
        return true;
    }

    private static boolean uebergabe(Stage owner, Uebergabe u, Anlage a) {
        TextField name = new TextField(u.getAnzeigeName());
        Set<String> angebot = new LinkedHashSet<>(u.getAngebot());
        Set<String> annahme = new LinkedHashSet<>(u.getAnnahme());
        Label angebotText = new Label(namen(a, angebot));
        angebotText.setWrapText(true);
        Button angebotKnopf = new Button("Angebot wählen…");
        angebotKnopf.setOnAction(e -> {
            Set<String> neu = gueterWaehlen(owner, a, angebot, "Güter, die die Übergabe unbegrenzt liefert");
            if (neu != null) {
                angebot.clear();
                angebot.addAll(neu);
                angebotText.setText(namen(a, angebot));
            }
        });
        CheckBox alles = new CheckBox("Nimmt jedes Gut ab");
        alles.setSelected(u.isAnnahmeAlles());
        Label annahmeText = new Label(namen(a, annahme));
        annahmeText.setWrapText(true);
        Button annahmeKnopf = new Button("Annahme wählen…");
        annahmeKnopf.disableProperty().bind(alles.selectedProperty());
        annahmeKnopf.setOnAction(e -> {
            Set<String> neu = gueterWaehlen(owner, a, annahme, "Güter, die die Übergabe unbegrenzt abnimmt");
            if (neu != null) {
                annahme.clear();
                annahme.addAll(neu);
                annahmeText.setText(namen(a, annahme));
            }
        });
        CheckBox passagiere = new CheckBox("An Bahnsteigen dieser Station steigen alle ankommenden Passagiere aus");
        passagiere.setSelected(u.isPassagiereAussteigen());
        GridPane grid = grid();
        zeile(grid, 0, "Name", name);
        zeile(grid, 1, "Angebot", new VBox(4, angebotText, angebotKnopf));
        zeile(grid, 2, "Annahme", new VBox(4, alles, annahmeText, annahmeKnopf));
        zeile(grid, 3, "", passagiere);
        if (!zeige(owner, "Übergabe bearbeiten", grid)) {
            return false;
        }
        u.setName(name.getText().trim());
        u.getAngebot().clear();
        u.getAngebot().addAll(angebot);
        u.getAnnahme().clear();
        u.getAnnahme().addAll(annahme);
        u.setAnnahmeAlles(alles.isSelected());
        u.setPassagiereAussteigen(passagiere.isSelected());
        return true;
    }

    // ================================================================ Hilfen

    /** Mehrfachauswahl von Guetern; null bei Abbruch. */
    public static Set<String> gueterWaehlen(Stage owner, Anlage a, Set<String> vorauswahl, String titel) {
        VBox liste = new VBox(3);
        List<CheckBox> boxen = new ArrayList<>();
        List<Gut> sortiert = new ArrayList<>(a.getGueter().values());
        sortiert.sort(Comparator.comparing((Gut g) -> g.getLadeklasse().ordinal()).thenComparing(Gut::getName));
        Ladeklasse letzte = null;
        for (Gut g : sortiert) {
            if (g.getLadeklasse() != letzte) {
                letzte = g.getLadeklasse();
                Label k = new Label(letzte.getLabel());
                k.setStyle("-fx-font-weight: bold;");
                liste.getChildren().add(k);
            }
            CheckBox cb = new CheckBox(g.getName());
            cb.setGraphic(Symbole.gut(g, 14));
            cb.setUserData(g.getId());
            cb.setSelected(vorauswahl.contains(g.getId()));
            boxen.add(cb);
            liste.getChildren().add(cb);
        }
        ScrollPane scroll = new ScrollPane(liste);
        scroll.setPrefSize(320, 420);
        if (!zeige(owner, titel, scroll)) {
            return null;
        }
        Set<String> ergebnis = new LinkedHashSet<>();
        for (CheckBox cb : boxen) {
            if (cb.isSelected()) {
                ergebnis.add((String) cb.getUserData());
            }
        }
        return ergebnis;
    }

    public static ComboBox<Gut> gutCombo(Anlage a, boolean mitLeer) {
        List<Gut> sortiert = new ArrayList<>(a.getGueter().values());
        sortiert.sort(Comparator.comparing(Gut::getName));
        ComboBox<Gut> box = new ComboBox<>(FXCollections.observableArrayList(sortiert));
        if (mitLeer) {
            box.getItems().add(0, null);
        }
        box.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Gut item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
                setGraphic(empty || item == null ? null : Symbole.gut(item, 14));
            }
        });
        box.setButtonCell(box.getCellFactory().call(null));
        box.setPrefWidth(170);
        return box;
    }

    private static String namen(Anlage a, Set<String> ids) {
        if (ids.isEmpty()) {
            return "keine";
        }
        List<String> l = new ArrayList<>();
        for (String id : ids) {
            l.add(a.gutName(id));
        }
        return String.join(", ", l);
    }

    static <T> TableColumn<T, String> spalte(String titel, double breite, java.util.function.Function<T, String> wert) {
        TableColumn<T, String> c = new TableColumn<>(titel);
        c.setCellValueFactory(d -> new SimpleStringProperty(wert.apply(d.getValue())));
        c.setPrefWidth(breite);
        return c;
    }

    static <E> StringConverter<E> konverter(java.util.function.Function<E, String> f) {
        return new StringConverter<>() {
            @Override
            public String toString(E e) {
                return e == null ? "" : f.apply(e);
            }

            @Override
            public E fromString(String s) {
                return null;
            }
        };
    }

    static boolean zeige(Stage owner, String titel, Node inhalt) {
        return zeige(owner, titel, inhalt, null);
    }

    /** Wie oben; solange {@code gesperrt} wahr ist, bleibt „Speichern“ gesperrt (z. B. Modul ohne Streckengleis). */
    static boolean zeige(Stage owner, String titel, Node inhalt, javafx.beans.value.ObservableBooleanValue gesperrt) {
        Dialog<ButtonType> d = new Dialog<>();
        Fenster.merken(d, titel); // Größe merken (Anwender 22.10.2026)
        d.setTitle(titel);
        if (owner != null) {
            d.initOwner(owner);
        }
        DialogKnoepfe.setze(d.getDialogPane());
        if (gesperrt != null) {
            Node sp = d.getDialogPane().lookupButton(DialogKnoepfe.SPEICHERN);
            if (sp != null) {
                sp.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(gesperrt::getValue, gesperrt));
            }
        }
        // Grüne Haken hinter vorhandenen, gespeicherten Angaben (Anwender 14.10.2026)
        new Haken.Gruppe().ueber(inhalt);
        VBox rahmen = new VBox(inhalt);
        VBox.setVgrow(inhalt, javafx.scene.layout.Priority.ALWAYS);
        rahmen.setPadding(new Insets(10));
        d.getDialogPane().setContent(rahmen);
        d.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        // Nach unten mitwachsen (Anwender 20.10.2026): Blendet der Dialog Teile ein (z. B. Gleisart wechseln im
        // Gleisdialog), wird das Fenster so hoch wie der Inhalt, höchstens so hoch wie der Bildschirm.
        boolean[] geplant = {false};
        Runnable anpassen = () -> {
            geplant[0] = false;
            if (d.getDialogPane().getScene() == null) {
                return;
            }
            javafx.stage.Window w = d.getDialogPane().getScene().getWindow();
            if (w == null || !w.isShowing()) {
                return;
            }
            double rand = w.getHeight() - d.getDialogPane().getScene().getHeight();
            double soll = d.getDialogPane().prefHeight(d.getDialogPane().getWidth()) + rand;
            javafx.geometry.Rectangle2D schirm = javafx.stage.Screen.getScreensForRectangle(w.getX(), w.getY(),
                    Math.max(1, w.getWidth()), Math.max(1, w.getHeight())).stream().findFirst()
                    .orElse(javafx.stage.Screen.getPrimary()).getVisualBounds();
            if (soll > w.getHeight() + 1) {
                double hoehe = Math.min(soll, schirm.getHeight());
                if (w.getY() + hoehe > schirm.getMaxY()) {
                    w.setY(Math.max(schirm.getMinY(), schirm.getMaxY() - hoehe)); // reicht unten nicht: nach oben schieben
                }
                w.setHeight(hoehe);
            }
        };
        rahmen.needsLayoutProperty().addListener((obs, alt, neu) -> {
            if (neu && !geplant[0]) {
                geplant[0] = true;
                javafx.application.Platform.runLater(anpassen);
            }
        });
        d.setOnShown(e -> javafx.application.Platform.runLater(anpassen));
        return d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) == DialogKnoepfe.SPEICHERN;
    }

    static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        return g;
    }

    static void zeile(GridPane g, int zeile, String label, Node feld) {
        Label l = new Label(label);
        if (label.contains("M/h")) {
            // M/h = Modellstunde (Anwender 14.10.2026)
            javafx.scene.control.Tooltip.install(l, Symbole.tip("M/h = je Modellstunde"));
        }
        GridPane.setValignment(l, javafx.geometry.VPos.TOP);
        g.add(l, 0, zeile);
        g.add(feld, 1, zeile);
        GridPane.setHalignment(feld, javafx.geometry.HPos.LEFT);
    }

    static double parse(String text, double standard) {
        if (text == null || text.isBlank()) {
            return standard;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return standard;
        }
    }

    static String zahl(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.GERMANY, "%.2f", v);
    }

    /** Ausrichtung fuer kleine Knopfzeilen. */
    static HBox knoepfe(Node... n) {
        HBox h = new HBox(6, n);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }
}
