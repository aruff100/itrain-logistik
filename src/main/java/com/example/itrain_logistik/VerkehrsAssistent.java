package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.BetriebVorlage;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Tf3Daten;
import com.example.itrain_logistik.kern.Uebergabe;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Verkehrs-Assistent (Anwender 27.10.2026): baut den Verkehr Schritt für Schritt auf und lässt sich jederzeit über
 * „Anlage bearbeiten“ bzw. das Menü aufrufen. Ablauf:
 * <ol>
 * <li>Sind die iTrain-Bahnhöfe noch nicht eingebaut: „n Bahnhöfe vorhanden“ (Abbruch/Weiter).</li>
 * <li>Einrichtung Bahnhöfe ({@link BahnhoefeDialog}): Name, Art, je Gleis Bahnsteig/Ladegleis/Vorerst nichts.</li>
 * <li>Je Bahnhof mit neuen Bahnsteigen „Personenverkehr aktiviert, bitte Umgebung hinzufügen“ (Stadt, Dorf,
 * Ländlich), je Bahnhof mit neuen Ladegleisen „Güterverkehr aktiviert“ (dazu Betrieb, Lager, Übergabe); danach das
 * gewohnte Bearbeiten-Fenster.</li>
 * <li>Prüfung ({@link com.example.itrain_logistik.kern.VerkehrsPruefung}) mit Tabelle, „Lücken schließen“
 * (Lieferkette, Schattenbahnhof, Überschuss) und „Transport festlegen“.</li>
 * </ol>
 */
final class VerkehrsAssistent {

    private final Stage owner;
    private final LogistikDocument doc;
    private final Runnable geaendert;
    private final java.util.function.Supplier<List<com.example.itrain_logistik.kern.ZugZustand>> zuege;
    /** Lücken, die der Anwender in diesem Durchlauf auf „Später“ gestellt hat (nicht noch einmal fragen). */
    private final Set<String> spaeter = new java.util.HashSet<>();

    private VerkehrsAssistent(Stage owner, LogistikDocument doc, Runnable geaendert,
                              java.util.function.Supplier<List<com.example.itrain_logistik.kern.ZugZustand>> zuege) {
        this.owner = owner;
        this.doc = doc;
        this.geaendert = geaendert;
        this.zuege = zuege;
    }

    /**
     * Startet den Assistenten.
     *
     * @param nurWennNeu true beim „Anlage bearbeiten“: nur, solange die iTrain-Bahnhöfe noch nicht eingebaut sind
     * @return true, wenn etwas geändert wurde
     */
    static boolean starte(Stage owner, LogistikDocument doc, Runnable geaendert, boolean nurWennNeu,
                          java.util.function.Supplier<List<com.example.itrain_logistik.kern.ZugZustand>> zuege) {
        return new VerkehrsAssistent(owner, doc, geaendert, zuege).ablauf(nurWennNeu);
    }

    private Anlage anlage() {
        return doc.getAnlage();
    }

    private boolean ablauf(boolean nurWennNeu) {
        Anlage a = anlage();
        Map<String, List<String>> daten = doc.bahnhofDaten();
        boolean neu = !a.isBahnhoefeEingefuegt();
        if (nurWennNeu && (!neu || daten.isEmpty())) {
            return false;
        }
        if (neu && !daten.isEmpty() && !Texte.zeige(owner, "Verkehrs-Assistent",
                Texte.dialog("assistent_bahnhoefe_vorhanden", "anzahl", daten.size()), "Abbruch", "Weiter")) {
            return false;
        }
        if (!neu) {
            List<ButtonType> k = new ArrayList<>();
            ButtonType abbruch = new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType bahnhoefe = knopf("Bahnhöfe und Gleise", k);
            ButtonType transport = knopf("Transport festlegen", k);
            ButtonType pruefen = knopf("Anlage prüfen", k);
            k.add(abbruch);
            ButtonType w = frage(Texte.dialog("assistent_start"), k, abbruch);
            if (w == transport) {
                if (transport()) {
                    pruefung();
                    return true;
                }
                return false;
            }
            if (w == pruefen) {
                pruefung();
                return true;
            }
            if (w != bahnhoefe) {
                return false;
            }
        }
        List<Station> mitNeuenBahnsteigen = new ArrayList<>();
        List<Station> mitNeuenLadegleisen = new ArrayList<>();
        if (!daten.isEmpty()) {
            List<BahnhoefeDialog.Ergebnis> erg = BahnhoefeDialog.zeige(owner, a, daten, doc.bahnhofTypen());
            if (erg == null) {
                return false;
            }
            uebernehmen(erg, daten, mitNeuenBahnsteigen, mitNeuenLadegleisen);
            geaendert.run();
        }
        for (Station s : mitNeuenBahnsteigen) {
            umgebung(s, true);
        }
        for (Station s : mitNeuenLadegleisen) {
            umgebung(s, false);
        }
        geaendert.run();
        pruefung();
        return true;
    }

    // ------------------------------------------------------------------ Schritt 2: Prüfung

    /**
     * Tabelle „was soll passieren, Gegenstück, Züge“ (Anwender 09.11.2026): der Anwender markiert eine Zeile mit ⚠,
     * „Weiter“ behandelt nur diese; danach erscheint die Tabelle neu berechnet, sodass die nächste Zeile schon den
     * neuen Stand kennt (z. B. „Fischzucht vorhanden, Menge erhöhen“ statt eines zweiten Betriebs).
     */
    private void pruefung() {
        String zuletzt = null;
        for (int runde = 0; runde < 200; runde++) {
            List<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile> zeilen =
                    com.example.itrain_logistik.kern.VerkehrsPruefung.pruefe(anlage(), zuege.get());
            if (zeilen.isEmpty()) {
                return;
            }
            Texte.Abschnitt t = Texte.dialog("assistent_pruefung");
            javafx.scene.control.TableView<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile> tab =
                    new javafx.scene.control.TableView<>(javafx.collections.FXCollections.observableArrayList(zeilen));
            tab.getColumns().add(spalte("", 34, z -> z.ok() ? "✓" : "⚠"));
            tab.getColumns().add(spalte("Was soll passieren", 330, z -> z.was()));
            tab.getColumns().add(spalte("Gegenstück", 230, z -> z.gegenstueck()));
            tab.getColumns().add(spalte("Züge", 260, z -> z.fahrzeuge()));
            tab.getColumns().add(spalte("Hinweis", 260, z -> z.hinweis() == null ? "" : z.hinweis()));
            tab.setColumnResizePolicy(javafx.scene.control.TableView.UNCONSTRAINED_RESIZE_POLICY);
            tab.setPrefHeight(360);
            tab.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.SINGLE);
            Spalten.einrichten(tab, "assistent.pruefung");
            Label auswahlText = new Label();
            auswahlText.setWrapText(true);
            long offen = zeilen.stream().filter(z -> !z.ok()).count();
            javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(10, Texte.darstellen(t.text(), 900), tab,
                    auswahlText);
            inhalt.setPadding(new Insets(8));
            javafx.scene.layout.VBox.setVgrow(tab, javafx.scene.layout.Priority.ALWAYS);
            Dialog<ButtonType> d = new Dialog<>();
            d.initOwner(owner);
            d.setTitle("Verkehrs-Assistent – Prüfung");
            d.setHeaderText(t.titel());
            d.getDialogPane().setContent(inhalt);
            ButtonType schliessen = new ButtonType("Schließen", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType weiter = new ButtonType("Weiter", ButtonBar.ButtonData.OK_DONE);
            d.getDialogPane().getButtonTypes().setAll(schliessen, weiter);
            javafx.scene.Node weiterKnopf = d.getDialogPane().lookupButton(weiter);
            Runnable auswahl = () -> {
                var z = tab.getSelectionModel().getSelectedItem();
                boolean geht = z != null && !z.ok();
                weiterKnopf.setDisable(!geht);
                auswahlText.setText(offen == 0 ? "Alles vorhanden – nichts zu ergänzen."
                        : z == null ? offen + (offen == 1 ? " Zeile mit ⚠. Bitte eine markieren, dann „Weiter“."
                        : " Zeilen mit ⚠. Bitte eine markieren, dann „Weiter“.")
                        : z.ok() ? "Diese Zeile ist in Ordnung (✓). Bitte eine Zeile mit ⚠ markieren."
                        : "„Weiter“ ergänzt: " + z.was() + (z.hinweis() != null ? " (" + z.hinweis() + ")" : ""));
            };
            tab.getSelectionModel().selectedItemProperty().addListener((o, a1, a2) -> auswahl.run());
            tab.setRowFactory(tv -> {
                javafx.scene.control.TableRow<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile> r =
                        new javafx.scene.control.TableRow<>();
                r.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && !r.isEmpty() && !r.getItem().ok()) {
                        ((javafx.scene.control.Button) weiterKnopf).fire();
                    }
                });
                return r;
            });
            auswahl.run();
            // die zuletzt bearbeitete Zeile sichtbar halten (nicht markieren: der Anwender wählt selbst)
            if (zuletzt != null) {
                for (int i = 0; i < zeilen.size(); i++) {
                    if (zuletzt.equals(schluessel(zeilen.get(i)))) {
                        tab.scrollTo(Math.max(0, i - 2));
                        break;
                    }
                }
            }
            d.setResizable(true);
            Fenster.merken(d, "assistent.pruefung");
            if (owner.getScene() != null) {
                d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
            }
            var gewaehlt = tab.getSelectionModel().selectedItemProperty();
            if (d.showAndWait().orElse(schliessen) != weiter || gewaehlt.get() == null) {
                return;
            }
            com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile z = gewaehlt.get();
            zuletzt = schluessel(z);
            spaeter.remove(zuletzt); // ausdrücklich gewählt: auch nach „Später“ wieder fragen
            lueckeSchliessen(z);
            geaendert.run();
        }
    }

    private static String schluessel(com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile z) {
        if (z.quelle() == null) {
            return "frei|" + z.was();
        }
        return z.quelle().abschnitt() + "|" + z.quelle().objekt().getId() + "|" + z.quelle().gutId();
    }

    private static javafx.scene.control.TableColumn<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile, String>
            spalte(String titel, double breite,
                   java.util.function.Function<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile, String> wert) {
        javafx.scene.control.TableColumn<com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile, String> c =
                new javafx.scene.control.TableColumn<>(titel);
        c.setPrefWidth(breite);
        c.setCellValueFactory(z -> new javafx.beans.property.SimpleStringProperty(wert.apply(z.getValue())));
        c.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            private final javafx.scene.text.Text text = new javafx.scene.text.Text();

            {
                text.wrappingWidthProperty().bind(col.widthProperty().subtract(10));
                text.setStyle("-fx-fill: -fx-text-base-color;");
                setGraphic(text);
                setPrefHeight(javafx.scene.control.Control.USE_COMPUTED_SIZE);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                text.setText(empty || item == null ? "" : item);
                if ("⚠".equals(item)) {
                    text.setStyle("-fx-fill: #d07a00; -fx-font-weight: bold;");
                } else if ("✓".equals(item)) {
                    text.setStyle("-fx-fill: #2e9d3f; -fx-font-weight: bold;");
                } else {
                    text.setStyle("-fx-fill: -fx-text-base-color;");
                }
            }
        });
        return c;
    }

    // ------------------------------------------------------------------ Schritt 3: Lücken schließen

    private void lueckeSchliessen(com.example.itrain_logistik.kern.VerkehrsPruefung.Zeile z) {
        com.example.itrain_logistik.kern.KreislaufPruefung.Zeile q = z.quelle();
        if (q == null) {
            // z. B. Koffer: nur Hinweis, Abhilfe im Gleisdialog bzw. Wagenfenster
            spaeter.add(schluessel(z));
            return;
        }
        String fehlt = q.fehlt() == null ? "" : q.fehlt();
        boolean erledigt = switch (q.abschnitt()) {
            case ORTSBEDARF, EINGAENGE -> fehlt.startsWith("kein Lieferant") || fehlt.startsWith("es fehlen")
                    ? lieferkette(q) : false;
            case ABFAHRT, ANKUNFT -> fehlt.contains("niemand") || fehlt.contains("kein Ziel")
                    ? schattenbahnhof("Reisende aus bzw. nach „" + q.objekt().getAnzeigeName() + "“", null, 0) : false;
            case AUSGAENGE -> fehlt.startsWith("kein Abnehmer")
                    ? schattenbahnhof(anlage().gutName(q.gutId()) + " aus „" + q.objekt().getAnzeigeName() + "“",
                    q.gutId(), -1)
                    : fehlt.startsWith("Überschuss") && ueberschuss(q);
        };
        if (!erledigt) {
            spaeter.add(schluessel(z));
        }
    }

    /** Menge eines Guts je Ausführung (erstes Rezept, das es herstellt), mindestens 1. */
    private static double jeLauf(Betrieb b, String g) {
        for (com.example.itrain_logistik.kern.Rezept r : b.getRezepte()) {
            Number n = r.getAusgang().get(g);
            if (n != null && n.doubleValue() > 0) {
                return n.doubleValue();
            }
        }
        return 1;
    }

    /** Bedarf ohne (ausreichenden) Lieferanten: Betrieb anlegen oder vom Schattenbahnhof. */
    private boolean lieferkette(com.example.itrain_logistik.kern.KreislaufPruefung.Zeile q) {
        Anlage a = anlage();
        String g = q.gutId();
        com.example.itrain_logistik.kern.Gut gut = a.gut(g);
        String mengeText = com.example.itrain_logistik.kern.KreislaufPruefung.zahl(q.menge()) + " "
                + (gut != null ? gut.getEinheit() + " " : "") + a.gutName(g);
        BetriebVorlage vorlage = null;
        for (BetriebVorlage v : Tf3Daten.vorlagen()) {
            if (v.rezepte().stream().anyMatch(r -> r.getAusgang().containsKey(g))) {
                vorlage = v;
                break;
            }
        }
        String ziel = q.objekt().getAnzeigeName();
        // Gibt es schon einen Hersteller (Anwender 09.11.2026: „Fischzucht vorhanden, Menge erhöhen“)?
        Betrieb vorhanden = null;
        for (Betrieb b : a.betriebe()) {
            if (b.ausgangsGueter().contains(g) && !b.isVirtuell()) {
                vorhanden = b;
                break;
            }
        }
        double fehlt = com.example.itrain_logistik.kern.Kreislauf.verbrauch(a, g)
                - com.example.itrain_logistik.kern.Kreislauf.erzeugung(a, g);
        List<ButtonType> k = new ArrayList<>();
        ButtonType spaet = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType schatten = knopf("Vom Schattenbahnhof", k);
        ButtonType erhoehen = vorhanden != null && fehlt > 1e-6
                ? knopf("„" + vorhanden.getAnzeigeName() + "“ erhöhen", k) : null;
        ButtonType anlegen = vorlage != null ? knopf(vorhanden != null ? "Weiteren Betrieb anlegen" : "Betrieb anlegen", k)
                : null;
        k.add(spaet);
        Texte.Abschnitt t;
        if (vorhanden != null) {
            double jeLaufV = jeLauf(vorhanden, g);
            double neueRate = vorhanden.getRateProStunde() + Math.ceil(Math.max(0, fehlt) / Math.max(1, jeLaufV));
            t = Texte.dialog("lieferkette_vorhanden", "menge", mengeText, "ziel", ziel, "betrieb",
                    vorhanden.getAnzeigeName(), "herstellung", com.example.itrain_logistik.kern.KreislaufPruefung.zahl(
                            com.example.itrain_logistik.kern.Kreislauf.erzeugung(a, g)) + " " + a.gutName(g),
                    "fehlt", com.example.itrain_logistik.kern.KreislaufPruefung.zahl(Math.max(0, fehlt)),
                    "rate", com.example.itrain_logistik.kern.KreislaufPruefung.zahl(vorhanden.getRateProStunde()),
                    "neu", com.example.itrain_logistik.kern.KreislaufPruefung.zahl(neueRate));
            if (t.text().isBlank()) {
                t = new Texte.Abschnitt("Lieferkette für „" + ziel + "“", "„" + vorhanden.getAnzeigeName()
                        + "“ stellt " + a.gutName(g) + " schon her; es fehlen "
                        + com.example.itrain_logistik.kern.KreislaufPruefung.zahl(Math.max(0, fehlt)) + " je Modellstunde.");
            }
        } else {
            t = vorlage != null
                    ? Texte.dialog("lieferkette_betrieb", "menge", mengeText, "ziel", ziel, "betrieb", vorlage.name())
                    : Texte.dialog("lieferkette_ohne_vorlage", "menge", mengeText, "ziel", ziel);
        }
        ButtonType w = frage(t, k, spaet);
        if (w == schatten) {
            return schattenbahnhof(a.gutName(g) + " für „" + ziel + "“", g, q.menge());
        }
        if (w != null && w == erhoehen) {
            double jeLaufV = jeLauf(vorhanden, g);
            vorhanden.setRateProStunde(vorhanden.getRateProStunde() + Math.ceil(fehlt / Math.max(1, jeLaufV)));
            // Ziel muss das Gut entladen dürfen
            Station zs = a.gueterStationenFuer(q.objekt().getId()).stream().findFirst().orElse(null);
            if (zs != null) {
                gutErlauben(zs, g);
            }
            geaendert.run();
            return true;
        }
        if (w != anlegen) {
            return false;
        }
        Station zielStation = a.gueterStationenFuer(q.objekt().getId()).stream().findFirst().orElse(null);
        Betrieb b = a.neuerBetrieb(vorlage, 0, 0);
        double jeLauf = vorlage.rezepte().stream().filter(r -> r.getAusgang().containsKey(g))
                .mapToDouble(r -> r.getAusgang().get(g)).findFirst().orElse(1);
        b.setRateProStunde(Math.max(b.getRateProStunde(), Math.ceil(q.menge() / Math.max(1, jeLauf))));
        Station bei = bahnhofFuerBetrieb(b, zielStation);
        a.platziereNeu(b, bei != null ? bei.getX() + Anlage.RASTER_X : a.infrastruktur().getX() + Anlage.RASTER_X,
                bei != null ? bei.getY() : a.infrastruktur().getY() + Anlage.RASTER_Y);
        if (bei != null) {
            bei.getEinzugsbereich().add(b.getId());
            gutErlauben(bei, g);
        } else {
            b.setVirtuell(true);
        }
        if (zielStation != null) {
            gutErlauben(zielStation, g);
        }
        geaendert.run();
        return true;
    }

    /**
     * Auswahl des Bahnhofs, an dem ein neuer Betrieb liegt; null = nicht auf der Anlage. Alle Bahnhöfe der Anlage
     * außer dem Schattenbahnhof (Anwender 09.11.2026); hat der gewählte noch kein Ladegleis, wird eines eingerichtet.
     */
    private Station bahnhofFuerBetrieb(Betrieb b, Station ausser) {
        Anlage a = anlage();
        // alle Bahnhöfe, auch der des Abnehmers (dann ohne Zugfahrt, ortsnah); der kommt zuletzt
        List<Station> kandidaten = new ArrayList<>(a.stationenFuerBetrieb());
        if (ausser != null) {
            Station z = a.gueterStation(ausser);
            if (kandidaten.remove(z)) {
                kandidaten.add(z);
            }
        }
        if (kandidaten.isEmpty()) {
            return null;
        }
        List<String> namen = new ArrayList<>();
        kandidaten.forEach(s -> namen.add(s.getAnzeigeName()
                + (s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty() ? "  (noch ohne Ladegleis)" : "")));
        String nicht = "Nicht auf der Anlage (liefert immer)";
        namen.add(nicht);
        Texte.Abschnitt t = Texte.dialog("lieferkette_ort", "betrieb", b.getAnzeigeName());
        String w = auswahl(t, "Bahnhof:", namen);
        if (w == null || w.equals(nicht)) {
            return null;
        }
        Station s = kandidaten.get(namen.indexOf(w));
        return ladegleisSicherstellen(owner, a, s);
    }

    /**
     * Hat die Station (bzw. ihr Gbf) kein Ladegleis, fragt der Assistent, welcher Block Ladegleis wird (Gleise des
     * Bahnhofs oder freie Blöcke), und richtet es ein.
     *
     * @return die Station mit Ladegleisen (bei Bahnhof/Haltepunkt der Gbf); ohne Wahl die Station selbst
     */
    static Station ladegleisSicherstellen(javafx.stage.Window owner, Anlage a, Station s) {
        Station g = a.gueterStation(s);
        if (!g.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty()) {
            return g;
        }
        List<String> bloecke = a.moeglicheLadegleise(s);
        if (bloecke.isEmpty()) {
            Alert info = new Alert(Alert.AlertType.INFORMATION, "„" + s.getAnzeigeName() + "“ hat noch kein Ladegleis, "
                    + "und es ist kein freier Block da. Bitte später im Stationsdialog ein Gleis als Ladegleis "
                    + "einrichten.", ButtonType.OK);
            info.initOwner(owner);
            info.setHeaderText("Kein Ladegleis");
            info.showAndWait();
            return s;
        }
        javafx.scene.control.ComboBox<String> c = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(bloecke));
        c.setValue(bloecke.get(0));
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Ladegleis einrichten");
        d.setHeaderText("„" + s.getAnzeigeName() + "“ hat noch kein Ladegleis");
        Label text = new Label("Welches Gleis soll Ladegleis werden? Ein Bahnsteig kann zugleich Ladegleis sein. "
                + (s.getArt() == ObjektArt.BAHNHOF || s.getArt() == ObjektArt.HALTEPUNKT
                ? "Das Ladegleis kommt in einen angedockten Güterbahnhof." : ""));
        text.setWrapText(true);
        text.setMaxWidth(440);
        javafx.scene.layout.HBox zeile = new javafx.scene.layout.HBox(10, new Label("Gleis:"), c);
        zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        d.getDialogPane().setContent(new javafx.scene.layout.VBox(12, text, zeile));
        d.getDialogPane().getButtonTypes().setAll(new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE),
                ButtonType.OK);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        if (d.showAndWait().orElse(null) != ButtonType.OK || c.getValue() == null) {
            return g;
        }
        return a.ladegleisEinrichten(s, c.getValue());
    }

    /** Auswahl mit umbrechendem Erklärtext; null = Abbruch. */
    private String auswahl(Texte.Abschnitt t, String beschriftung, List<String> werte) {
        javafx.scene.control.ComboBox<String> c = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(werte));
        c.setValue(werte.get(0));
        c.setPrefWidth(320);
        javafx.scene.layout.HBox zeile = new javafx.scene.layout.HBox(10, new javafx.scene.control.Label(beschriftung), c);
        zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent");
        d.setHeaderText(t.titel());
        d.getDialogPane().setContent(new javafx.scene.layout.VBox(14, Texte.darstellen(t.text(), 480), zeile));
        d.getDialogPane().getButtonTypes().setAll(new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE),
                ButtonType.OK);
        d.setResizable(true);
        if (owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return d.showAndWait().orElse(null) == ButtonType.OK ? c.getValue() : null;
    }

    /** Hat ein Ladegleis der Station eine Güterauswahl ohne das Gut, kommt es dazu. */
    private void gutErlauben(Station s, String g) {
        Station ziel = s;
        Station gbf = anlage().angedockterGueterbahnhof(s);
        if (s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty() && gbf != null) {
            ziel = gbf;
        }
        for (com.example.itrain_logistik.kern.Gleis gl : ziel.gleiseDerArt(GleisArt.LADEGLEIS)) {
            if (gl.erlaubt(g)) {
                return;
            }
        }
        ziel.gleiseDerArt(GleisArt.LADEGLEIS).stream().findFirst().ifPresent(gl -> gl.getGueterAuswahl().add(g));
    }

    /**
     * Kein Gegenstück: Schattenbahnhof nutzen bzw. vorschlagen. {@code gut} null = Reisende; {@code menge} &gt; 0 =
     * Angebot des Guts je Modellstunde, -1 = der Schattenbahnhof nimmt das Gut ab.
     */
    private boolean schattenbahnhof(String was, String gut, double menge) {
        Anlage a = anlage();
        Station sb = null;
        for (Station s : a.stationen()) {
            if (s.getArt() == ObjektArt.SCHATTENBAHNHOF) {
                sb = s;
                break;
            }
        }
        if (sb == null) {
            List<String> frei = a.freieBloecke();
            List<ButtonType> k = new ArrayList<>();
            ButtonType spaet = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType anlegen = frei.isEmpty() ? null : knopf("Schattenbahnhof anlegen", k);
            k.add(spaet);
            ButtonType w = frage(Texte.dialog("schattenbahnhof_vorschlag", "was", was, "bloecke",
                    frei.isEmpty() ? "keine – bitte in iTrain anlegen, dann „Abgleichen“ und den Assistenten erneut "
                            + "starten" : String.join(", ", frei)), k, spaet);
            if (w != anlegen) {
                return false;
            }
            sb = schattenbahnhofAnlegen(frei, gut == null);
            if (sb == null) {
                return false;
            }
        }
        if (gut == null) {
            if (sb.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()) {
                return false;
            }
        } else {
            Station gbf = a.angedockterGueterbahnhof(sb);
            Station mitLade = gbf != null ? gbf : sb;
            List<com.example.itrain_logistik.kern.Gleis> lade = mitLade.gleiseDerArt(GleisArt.LADEGLEIS);
            if (lade.isEmpty()) {
                return false;
            }
            com.example.itrain_logistik.kern.Gleis gl = lade.get(0);
            gl.getGueterAuswahl().add(gut);
            if (menge > 0) {
                gl.getAngebot().merge(gut, Math.ceil(menge), Math::max);
            }
        }
        geaendert.run();
        return true;
    }

    /** Neuer Schattenbahnhof aus freien Blöcken: der Anwender wählt je Block Bahnsteig, Ladegleis oder nichts. */
    private Station schattenbahnhofAnlegen(List<String> frei, boolean personen) {
        Anlage a = anlage();
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(10);
        g.setVgap(6);
        Map<String, javafx.scene.control.ChoiceBox<String>> wahl = new LinkedHashMap<>();
        int i = 0;
        for (String b : frei) {
            javafx.scene.control.ChoiceBox<String> cb = new javafx.scene.control.ChoiceBox<>(
                    javafx.collections.FXCollections.observableArrayList(BahnhoefeDialog.BAHNSTEIG,
                            BahnhoefeDialog.LADEGLEIS, BahnhoefeDialog.BEIDE, BahnhoefeDialog.NICHTS));
            cb.setValue(BahnhoefeDialog.NICHTS);
            g.addRow(i++, new javafx.scene.control.Label(b), cb);
            wahl.put(b, cb);
        }
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent");
        d.setHeaderText("Schattenbahnhof anlegen");
        ScrollPane sp = new ScrollPane(g);
        sp.setPrefViewportHeight(Math.min(360, 32.0 * frei.size() + 10));
        // „Ist dieser Bahnhof auf der Anlage oder ein Modul?“ (Anwender 09.11.2026)
        javafx.scene.control.ComboBox<String> lage = new javafx.scene.control.ComboBox<>();
        lage.getItems().add(BahnhoefeDialog.AUF_ANLAGE);
        a.module().forEach(m -> lage.getItems().add(m.getAnzeigeName()));
        lage.setEditable(true);
        lage.setValue(BahnhoefeDialog.AUF_ANLAGE);
        lage.setPromptText("Auf der Anlage oder Name eines (neuen) Moduls");
        javafx.scene.layout.HBox lageZeile = new javafx.scene.layout.HBox(8,
                new Label("Ist dieser Bahnhof auf der Anlage oder ein Modul?"), lage);
        lageZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(10, lageZeile, Texte.darstellen(
                "Wählen Sie die Blöcke, die in iTrain zum Schattenbahnhof gehören, und je Block, ob dort "
                        + (personen ? "Reisende ein- und aussteigen (**Bahnsteig**)" : "Güter geladen werden (**Ladegleis**)")
                        + ". Am besten ein Gleis je Fahrtrichtung.", 460), sp);
        d.getDialogPane().setContent(inhalt);
        d.setResizable(true);
        ButtonType ok = new ButtonType("Anlegen", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().setAll(new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE), ok);
        javafx.scene.Node okKnopf = d.getDialogPane().lookupButton(ok);
        Runnable pruefen = () -> okKnopf.setDisable(wahl.values().stream()
                .allMatch(cb -> BahnhoefeDialog.NICHTS.equals(cb.getValue())));
        wahl.values().forEach(cb -> cb.valueProperty().addListener((o, x, y) -> pruefen.run()));
        pruefen.run();
        if (d.showAndWait().orElse(null) != ok) {
            return null;
        }
        Station sb = new Station(null, ObjektArt.SCHATTENBAHNHOF, a.freierName("Schattenbahnhof"));
        a.platziereNeu(sb, a.infrastruktur().getX() - Anlage.RASTER_X, a.infrastruktur().getY());
        Map<String, GleisArt> w = new LinkedHashMap<>();
        Set<String> beide = new LinkedHashSet<>();
        wahl.forEach((b, cb) -> {
            if (BahnhoefeDialog.BEIDE.equals(cb.getValue())) {
                beide.add(b);
            }
            w.put(b, BahnhoefeDialog.BAHNSTEIG.equals(cb.getValue()) || BahnhoefeDialog.BEIDE.equals(cb.getValue())
                    ? GleisArt.BAHNSTEIG : BahnhoefeDialog.LADEGLEIS.equals(cb.getValue()) ? GleisArt.LADEGLEIS : null);
        });
        a.bahnhofEinstellen(sb, null, ObjektArt.SCHATTENBAHNHOF, w, beide);
        String l = lage.getEditor() != null ? lage.getEditor().getText() : lage.getValue();
        a.modulZuordnen(sb, l == null || l.isBlank() || BahnhoefeDialog.AUF_ANLAGE.equals(l.trim()) ? null : l.trim());
        geaendert.run();
        return sb;
    }

    /** Überschuss eines Betriebs: Lager beim Abnehmer anlegen oder dessen Verbrauch erhöhen. */
    private boolean ueberschuss(com.example.itrain_logistik.kern.KreislaufPruefung.Zeile q) {
        Anlage a = anlage();
        String g = q.gutId();
        double ueber = com.example.itrain_logistik.kern.Kreislauf.erzeugung(a, g)
                - com.example.itrain_logistik.kern.Kreislauf.verbrauch(a, g);
        if (ueber <= 0) {
            return true;
        }
        Ort ort = null;
        for (Ort o : a.orte()) {
            if (o.getBedarf().getOrDefault(g, 0.0) > 0) {
                ort = o;
                break;
            }
        }
        com.example.itrain_logistik.kern.Gut gut = a.gut(g);
        String mengeText = com.example.itrain_logistik.kern.KreislaufPruefung.zahl(ueber) + " "
                + (gut != null ? gut.getEinheit() + " " : "") + a.gutName(g);
        List<ButtonType> k = new ArrayList<>();
        ButtonType spaet = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType mehr = ort != null ? knopf("Verbrauch erhöhen", k) : null;
        ButtonType lagerK = knopf("Lager anlegen", k);
        k.add(spaet);
        ButtonType w = frage(Texte.dialog("ueberschuss", "menge", mengeText, "betrieb",
                q.objekt().getAnzeigeName(), "ort", ort != null ? ort.getAnzeigeName() : "der Abnehmer"), k, spaet);
        if (w == mehr && ort != null) {
            ort.getBedarf().merge(g, Math.ceil(ueber), Double::sum);
            geaendert.run();
            return true;
        }
        if (w == lagerK) {
            Station bei = ort != null ? a.gueterStationenFuer(ort.getId()).stream().findFirst().orElse(null) : null;
            if (bei == null) {
                bei = a.stationenFuer(q.objekt().getId()).stream().findFirst().orElse(null);
            }
            Lager l = new Lager(null, a.freierName("Lager"));
            a.platziereNeu(l, bei != null ? bei.getX() : a.infrastruktur().getX(),
                    (bei != null ? bei.getY() : a.infrastruktur().getY()) + Anlage.RASTER_Y);
            if (bei != null) {
                bei.getEinzugsbereich().add(l.getId());
            }
            geaendert.run();
            ObjektDialoge.bearbeite(owner, l, a, doc.getSnapshot().blocks());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Von wo wird was wohin transportiert


    /** Eintrag „Gut X von A nach B, n je Modellstunde“; legt Bedarf, Herstellung und Ladegleis-Freigaben an. */
    private boolean transport() {
        // Assistent in Schritten (Anwender 31.10.2026), siehe TransportAssistent.
        return new TransportAssistent(owner, anlage(), geaendert).zeige();
    }

    /** Frage mit eigenem Knopf-Satz; liefert den gewählten Knopf. */
    private ButtonType frage(Texte.Abschnitt t, List<ButtonType> knoepfe, ButtonType standard) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent");
        d.setHeaderText(t.titel());
        ScrollPane sp = new ScrollPane(Texte.darstellen(t.text(), 560));
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(580);
        sp.setPrefViewportHeight(Math.min(380, 30 + t.text().length() / 4.5));
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().setAll(knoepfe);
        d.setResizable(true);
        if (owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        return d.showAndWait().orElse(standard);
    }

    /** Bahnhöfe einbauen bzw. umstellen; merkt sich, wo Bahnsteige bzw. Ladegleise neu dazugekommen sind. */
    private void uebernehmen(List<BahnhoefeDialog.Ergebnis> erg, Map<String, List<String>> daten,
                             List<Station> neueSteige, List<Station> neueLade) {
        Anlage a = anlage();
        Map<String, boolean[]> vorher = new LinkedHashMap<>();
        for (BahnhoefeDialog.Ergebnis e : erg) {
            Station s = e.station();
            Station gbf = s != null ? a.angedockterGueterbahnhof(s) : null;
            boolean steig = s != null && !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty();
            boolean lade = s != null && (!s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty()
                    || gbf != null && !gbf.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty());
            vorher.put(e.itrainName(), new boolean[]{steig, lade});
        }
        if (!a.isBahnhoefeEingefuegt()) {
            for (BahnhoefeDialog.Ergebnis e : erg) {
                Set<String> lade = new LinkedHashSet<>();
                Set<String> steige = new LinkedHashSet<>();
                e.wahl().forEach((b, art) -> {
                    if (art == GleisArt.LADEGLEIS) {
                        lade.add(b);
                    } else if (art == GleisArt.BAHNSTEIG) {
                        steige.add(b);
                    }
                });
                lade.addAll(e.beide()); // Bahnsteig und Ladegleis: in beiden Listen
                a.getBahnhofVorgaben().put(e.itrainName(), new Anlage.BahnhofVorgabe(e.art(), lade, e.name(), steige));
            }
            a.bahnhoefeEinfuegen(daten, doc.bahnhofTypen());
        } else {
            for (BahnhoefeDialog.Ergebnis e : erg) {
                Station s = e.station();
                if (s == null) {
                    // in iTrain neu hinzugekommener Bahnhof: jetzt anheften
                    a.itrainBahnhoefeAnheften(Map.of(e.itrainName(), daten.getOrDefault(e.itrainName(), List.of())),
                            doc.bahnhofTypen(), true);
                    s = a.stationFuerItrain(e.itrainName());
                }
                if (s != null) {
                    a.bahnhofEinstellen(s, e.name(), e.art(), e.wahl(), e.beide());
                }
            }
        }
        for (BahnhoefeDialog.Ergebnis e : erg) {
            Station s = a.stationFuerItrain(e.itrainName());
            if (s == null) {
                continue;
            }
            // Auf der Anlage oder Modul (Anwender 09.11.2026)
            a.modulZuordnen(s, e.modul());
            boolean[] v = vorher.get(e.itrainName());
            if (e.mitBahnsteig() && !v[0] && s.getArt() != ObjektArt.SCHATTENBAHNHOF) {
                neueSteige.add(s);
            }
            if (e.mitLadegleis() && !v[1] && s.getArt() != ObjektArt.SCHATTENBAHNHOF) {
                Station gbf = a.angedockterGueterbahnhof(s);
                neueLade.add(gbf != null ? gbf : s);
            }
        }
    }

    // ------------------------------------------------------------------ Umgebung

    /** „Personen-/Güterverkehr aktiviert, bitte Umgebung hinzufügen“ für eine Station. */
    private void umgebung(Station s, boolean personen) {
        Anlage a = anlage();
        // Je Bahnhof höchstens ein Ort (Anwender 27.10.2026): hängt schon einer am Bahnhof bzw. Güterbahnhof,
        // gilt er auch hier.
        Ort ortVorhanden = a.ortAnStation(s);
        if (ortVorhanden != null) {
            if (!s.bedient(ortVorhanden.getId()) && !personen && a.gehoertZu(s) != null) {
                s.getEinzugsbereich().add(ortVorhanden.getId());
                geaendert.run();
            }
            if (personen) {
                return;
            }
        }
        boolean ortMoeglich = ortVorhanden == null;
        Texte.Abschnitt t = Texte.dialog(personen ? "umgebung_personen" : "umgebung_gueter", "bahnhof",
                s.getAnzeigeName());
        List<ButtonType> knoepfe = new ArrayList<>();
        ButtonType stadt = ortMoeglich ? knopf("Stadt", knoepfe) : null;
        ButtonType dorf = ortMoeglich ? knopf("Dorf", knoepfe) : null;
        ButtonType land = ortMoeglich ? knopf("Ländlich", knoepfe) : null;
        ButtonType betrieb = personen ? null : knopf("Betrieb", knoepfe);
        ButtonType lager = personen ? null : knopf("Lager", knoepfe);
        ButtonType uebergabe = personen ? null : knopf("Übergabe", knoepfe);
        List<AnlagenObjekt> vorhanden = new ArrayList<>();
        for (AnlagenObjekt o : a.getObjekte()) {
            boolean passt = personen ? o instanceof Ort
                    : o instanceof Ort || o instanceof Betrieb || o instanceof Lager || o instanceof Uebergabe;
            if (passt && !s.bedient(o.getId()) && (!(o instanceof Ort) || ortMoeglich)) {
                vorhanden.add(o);
            }
        }
        ButtonType vorh = vorhanden.isEmpty() ? null
                : knopf(personen ? "Vorhandenen Ort wählen" : "Vorhandenes wählen", knoepfe);
        ButtonType spaeter = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
        knoepfe.add(spaeter);

        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verkehrs-Assistent");
        d.setHeaderText(t.titel());
        ScrollPane sp = new ScrollPane(Texte.darstellen(t.text(), 560));
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(580);
        sp.setPrefViewportHeight(240);
        sp.setPadding(new Insets(6));
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().setAll(knoepfe);
        d.setResizable(true);
        Fenster.merken(d, "assistent.umgebung");
        if (owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        ButtonType wahl = d.showAndWait().orElse(spaeter);
        AnlagenObjekt neu = null;
        if (wahl != null && (wahl == stadt || wahl == dorf || wahl == land)) {
            ObjektArt art = wahl == stadt ? ObjektArt.STADT : wahl == dorf ? ObjektArt.DORF : ObjektArt.LAENDLICH;
            neu = new Ort(null, art, a.freierName(art.getLabel()));
        } else if (wahl == lager) {
            neu = new Lager(null, a.freierName("Lager"));
        } else if (wahl == uebergabe) {
            neu = new Uebergabe(null, a.freierName("Übergabe"));
        } else if (wahl == betrieb) {
            neu = betriebWaehlen(s);
            if (neu == null) {
                return;
            }
        } else if (wahl == vorh) {
            AnlagenObjekt o = vorhandenWaehlen(vorhanden, personen);
            if (o != null && !s.bedient(o.getId())) {
                s.getEinzugsbereich().add(o.getId());
            }
            return;
        } else {
            return;
        }
        if (!a.getObjekte().contains(neu)) {
            a.platziereNeu(neu, s.getX() + Anlage.RASTER_X, s.getY());
        }
        s.getEinzugsbereich().add(neu.getId());
        geaendert.run();
        ObjektDialoge.bearbeite(owner, neu, a, doc.getSnapshot().blocks());
    }

    private static ButtonType knopf(String text, List<ButtonType> liste) {
        ButtonType b = new ButtonType(text, ButtonBar.ButtonData.OTHER);
        liste.add(b);
        return b;
    }

    private AnlagenObjekt vorhandenWaehlen(List<AnlagenObjekt> liste, boolean personen) {
        List<String> namen = new ArrayList<>();
        for (AnlagenObjekt o : liste) {
            namen.add(o.getAnzeigeName() + " (" + o.getArt().getLabel() + ")");
        }
        ChoiceDialog<String> c = new ChoiceDialog<>(namen.get(0), namen);
        c.initOwner(owner);
        c.setTitle("Verkehrs-Assistent");
        c.setHeaderText(personen ? "Welcher Ort?" : "Welcher Ort, Betrieb, welches Lager oder welche Übergabe?");
        c.setContentText("Auswahl:");
        Optional<String> w = c.showAndWait();
        return w.map(n -> liste.get(namen.indexOf(n))).orElse(null);
    }

    /** Betrieb nach Vorlage (wie in der Palette) oder eigener Betrieb. */
    private Betrieb betriebWaehlen(Station s) {
        Anlage a = anlage();
        List<BetriebVorlage> vorlagen = new ArrayList<>(Tf3Daten.vorlagen());
        vorlagen.sort((x, y) -> x.name().compareTo(y.name()));
        List<String> namen = new ArrayList<>();
        vorlagen.forEach(v -> namen.add(v.name()));
        String eigener = "Eigener Betrieb (ohne Vorlage)";
        namen.add(eigener);
        ChoiceDialog<String> c = new ChoiceDialog<>(namen.get(0), namen);
        c.initOwner(owner);
        c.setTitle("Verkehrs-Assistent");
        c.setHeaderText("Welcher Betrieb liegt an den Ladegleisen von „" + s.getAnzeigeName() + "“?");
        c.setContentText("Vorlage:");
        Optional<String> w = c.showAndWait();
        if (w.isEmpty()) {
            return null;
        }
        double x = s.getX() + Anlage.RASTER_X;
        double y = s.getY();
        Betrieb b;
        if (w.get().equals(eigener)) {
            b = new Betrieb(null, a.freierName("Betrieb"));
            a.platziereNeu(b, x, y);
        } else {
            b = a.neuerBetrieb(vorlagen.get(namen.indexOf(w.get())), x, y);
            a.platziereNeu(b, x, y);
        }
        return b;
    }
}
