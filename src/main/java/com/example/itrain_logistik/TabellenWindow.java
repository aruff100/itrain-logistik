package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.EchtzeitKern;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Kreislauf;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Linie;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.ZugZustand;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tabellen im Stil der TF-Statistikfenster (Konzept Abschnitt 10.3): Linien, Fahrzeuge,
 * Stationen, Lager, Staedte, Industrien, dazu die Gesamttabelle nach dem Vorbild des
 * Güterübersicht (mit Umladen von Hand), die Kreislauf-Bilanz und das
 * Ereignisprotokoll. Ignoriert werden Booster, Arbeitsplaetze, Ertrag, Alter, Zufriedenheit,
 * Takt und Rate. Jede Tabelle ist eine eigene Ansicht ({@link Ansicht}); Fenster, Andocken und den
 * Aktualisierungstakt verwaltet {@link AnsichtenDock}.
 */
public final class TabellenWindow {

    /** Die einzelnen Tabellen; jede ist eine eigene Ansicht (Fenster oder angedockt, siehe {@link AnsichtenDock}). */
    public enum Ansicht {
        LINIEN("Routen"),
        FAHRZEUGE("Fahrzeuge"),
        STATIONEN("Stationen"),
        LAGER("Lager"),
        STAEDTE("Städte"),
        INDUSTRIEN("Industrien"),
        GESAMT("Gesamt (Güterübersicht)"),
        KREISLAUF("Kreislauf"),
        KAPAZITAETEN("Kapazitäten"),
        EREIGNISSE("Ereignisse");

        private final String titel;

        Ansicht(String titel) {
            this.titel = titel;
        }

        public String getTitel() {
            return titel;
        }
    }

    private TabellenWindow() {
    }

    /**
     * Baut den Inhalt einer Ansicht. Die Aktualisierer werden in {@code akt} eingetragen und
     * vom Aufrufer im Takt ausgeführt (die Sortierung bleibt dabei erhalten).
     */
    public static Node inhalt(Ansicht ansicht, Anlage a, EchtzeitKern kern, Supplier<LogistikSnapshot> snapshot,
            List<Runnable> akt) {
        Node n = switch (ansicht) {
            case LINIEN -> linien(a, akt);
            case FAHRZEUGE -> fahrzeuge(a, kern, snapshot, akt);
            case STATIONEN -> stationen(a, akt);
            case LAGER -> lager(a, akt);
            case STAEDTE -> staedte(a, kern, akt);
            case INDUSTRIEN -> industrien(a, kern, akt);
            case GESAMT -> gesamt(a, kern, akt);
            case KREISLAUF -> kreislauf(a, akt);
            case KAPAZITAETEN -> kapazitaeten(a, kern, snapshot, akt);
            case EREIGNISSE -> ereignisse(kern, akt);
        };
        // Spalten per Rechtsklick auf die Kopfzeile ein-/ausblenden, je Ansicht gespeichert (Anwender 16.10.2026)
        Spalten.alleIn(n, "ansicht." + ansicht.name().toLowerCase());
        return n;
    }

    // ================================================================ Zeilen und Hilfen

    /** Öffnet das Fenster "Züge" mit dem genannten Zug (gesetzt vom Hauptfenster). */
    public static java.util.function.Consumer<String> zugOeffnen;
    /** Öffnet das Detailfenster eines Objekts (gesetzt vom Hauptfenster). */
    public static java.util.function.Consumer<AnlagenObjekt> objektOeffnen;

    private static <R> TableView<R> tabelle(Supplier<List<R>> zeilen, List<Runnable> aktualisierer) {
        TableView<R> t = new TableView<>();
        // Doppelklick auf eine Zeile öffnet das zugehörige Fenster (Anwender 20.10.2026): Zug → "Züge",
        // Station, Ort, Betrieb, Lager → Detailfenster.
        t.setRowFactory(tv -> {
            javafx.scene.control.TableRow<R> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() != 2 || row.isEmpty() || e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                Object item = row.getItem();
                if (item instanceof ZugZustand z && zugOeffnen != null) {
                    zugOeffnen.accept(z.getName());
                } else if (item instanceof AnlagenObjekt o && objektOeffnen != null) {
                    objektOeffnen.accept(o);
                }
            });
            return row;
        });
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        aktualisierer.add(() -> {
            R auswahl = t.getSelectionModel().getSelectedItem();
            t.getItems().setAll(zeilen.get());
            t.sort();
            if (auswahl != null && t.getItems().contains(auswahl)) {
                t.getSelectionModel().select(auswahl);
            }
        });
        return t;
    }

    private static <R> TableColumn<R, String> text(String titel, double breite, Function<R, String> f) {
        TableColumn<R, String> c = new TableColumn<>(titel);
        c.setCellValueFactory(d -> new SimpleStringProperty(f.apply(d.getValue())));
        c.setPrefWidth(breite);
        return c;
    }

    /** Zahlenspalte (sortiert numerisch). */
    private static <R> TableColumn<R, Number> zahl(String titel, double breite, Function<R, Number> f) {
        TableColumn<R, Number> c = new TableColumn<>(titel);
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(f.apply(d.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Symbole.ganz(item.doubleValue()));
            }
        });
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        c.setPrefWidth(breite);
        return c;
    }

    private static <R> TableColumn<R, Number> prozent(String titel, Function<R, Number> f) {
        TableColumn<R, Number> c = zahl(titel, 80, f);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Math.round(item.doubleValue() * 100) + " %");
            }
        });
        return c;
    }

    /** Spalte mit Gut-Symbolen. */
    private static <R> TableColumn<R, List<String>> symbole(String titel, double breite, Anlage a,
            Function<R, List<String>> f) {
        TableColumn<R, List<String>> c = new TableColumn<>(titel);
        c.setCellValueFactory(d -> new SimpleObjectProperty<>(f.apply(d.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(List<String> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                FlowPane p = new FlowPane(2, 2);
                for (String g : item) {
                    p.getChildren().add(Symbole.gut(a.gut(g), 14));
                }
                setGraphic(p);
            }
        });
        c.setSortable(false);
        c.setPrefWidth(breite);
        return c;
    }

    private static double quote(double wert, double kapazitaet) {
        return kapazitaet <= 0 ? 0 : wert / kapazitaet;
    }

    private static List<ZugZustand> zuegeAuf(Anlage a, Linie l) {
        List<ZugZustand> z = new ArrayList<>();
        for (ZugZustand zug : a.getZuege().values()) {
            if (l.getName().equals(zug.getLinie())) {
                z.add(zug);
            }
        }
        return z;
    }

    private static List<String> gueterVon(List<ZugZustand> zuege) {
        Set<String> s = new LinkedHashSet<>();
        for (ZugZustand z : zuege) {
            s.addAll(z.ladungJeGut().keySet());
        }
        return new ArrayList<>(s);
    }

    private static double ladung(List<ZugZustand> zuege) {
        double s = 0;
        for (ZugZustand z : zuege) {
            double[] p = z.passagiereSumme();
            s += z.gueterLadung() + p[0] + p[1];
        }
        return s;
    }

    private static double kapazitaet(List<ZugZustand> zuege) {
        double s = 0;
        for (ZugZustand z : zuege) {
            s += z.gueterKapazitaet() + z.getSitzplaetzeErste() + z.getSitzplaetzeZweite();
        }
        return s;
    }

    private static List<String> linienAn(Anlage a, Station s) {
        List<String> l = new ArrayList<>();
        for (Linie linie : a.getLinien()) {
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null && linie.getBlockNamen().contains(g.getBlockName())) {
                    l.add(linie.getName());
                    break;
                }
            }
        }
        return l;
    }

    // ================================================================ Linien

    private static Node linien(Anlage a, List<Runnable> akt) {
        TableView<Linie> t = tabelle(() -> new ArrayList<>(a.getLinien()), akt);
        t.getColumns().add(text("Name", 200, Linie::getName));
        t.getColumns().add(text("Züge", 160, l -> String.join(", ", zuegeAuf(a, l).stream().map(ZugZustand::getName).toList())));
        t.getColumns().add(symbole("Güter", 140, a, l -> gueterVon(zuegeAuf(a, l))));
        t.getColumns().add(zahl("Im Zug", 80, l -> ladung(zuegeAuf(a, l))));
        t.getColumns().add(zahl("Kapazität", 80, l -> kapazitaet(zuegeAuf(a, l))));
        t.getColumns().add(prozent("Auslastung", l -> quote(ladung(zuegeAuf(a, l)), kapazitaet(zuegeAuf(a, l)))));
        t.getColumns().add(text("Halte", 300, l -> {
            List<String> halte = new ArrayList<>();
            for (String b : l.getBlockNamen()) {
                Anlage.GleisTreffer tr = a.gleisFuerBlock(b);
                if (tr != null && !halte.contains(tr.station().getAnzeigeName())) {
                    halte.add(tr.station().getAnzeigeName());
                }
            }
            return String.join(" – ", halte);
        }));
        Label hinweis = new Label("Routen sind die Zugrouten aus der .tcdz. Ein Zug gehört zu der Route, die "
                + "den Block enthält, in dem er zuletzt gemeldet wurde.");
        hinweis.setWrapText(true);
        return mitHinweis(t, hinweis);
    }

    // ================================================================ Fahrzeuge

    private static Node fahrzeuge(Anlage a, EchtzeitKern kern, Supplier<LogistikSnapshot> snapshot, List<Runnable> akt) {
        TableView<ZugZustand> t = tabelle(() -> {
            LogistikSnapshot s = snapshot.get();
            if (s != null) {
                for (TrainEntry zug : s.trains()) {
                    kern.zug(zug.getName());
                }
            }
            return new ArrayList<>(a.getZuege().values());
        }, akt);
        t.getColumns().add(text("Name", 160, ZugZustand::getName));
        // „Route“ = Zugroute aus iTrain (intern Linie), Anwender 27.10.2026
        t.getColumns().add(text("Route", 160, z -> z.getLinie() == null ? "" : z.getLinie()));
        t.getColumns().add(text("Block", 140, z -> (z.getBlock() == null ? "" : z.getBlock())
                + (z.isHaltend() ? " (hält)" : "")));
        t.getColumns().add(reisende());
        t.getColumns().add(symbole("Güter", 120, a, z -> new ArrayList<>(z.ladungJeGut().keySet())));
        t.getColumns().add(zahl("Im Zug", 80, z -> ladung(List.of(z))));
        t.getColumns().add(kapazitaetAufklappbar(t));
        t.getColumns().add(prozent("Auslastung", z -> quote(ladung(List.of(z)), kapazitaet(List.of(z)))));
        t.getColumns().add(text("Ausstattung", 260, z -> z.getWagenladungen().size() + " Wagenladungen"
                + (z.hatJoker() ? " (Joker)" : "") + ", "
                + Symbole.zahl(z.getSitzplaetzeErste()) + "/" + Symbole.zahl(z.getSitzplaetzeZweite()) + " Plätze"));
        return t;
    }

    /** Züge, deren Kapazität in der Ansicht Fahrzeuge aufgeklappt ist (bleibt über die Aktualisierung erhalten). */
    private static final Set<String> AUFGEKLAPPT = new java.util.HashSet<>();

    /**
     * Kapazität als Ausklappbox (Anwender 20.10.2026): statt der Summe aus Litern, Tonnen und Plätzen ("5085") ein
     * Pfeil ▼; ein Klick klappt die einzelnen Kapazitäten mit Einheit auf ("5000 Liter – Kesselwagen"). Hat ein Zug
     * nur eine Kapazität, steht sie gleich da. Sortiert wird weiter nach der Summe.
     */
    private static TableColumn<ZugZustand, ZugZustand> kapazitaetAufklappbar(TableView<ZugZustand> t) {
        TableColumn<ZugZustand, ZugZustand> c = new TableColumn<>("Kapazität");
        c.setPrefWidth(190);
        c.setCellValueFactory(z -> new SimpleObjectProperty<>(z.getValue()));
        c.setComparator(Comparator.comparingDouble(z -> kapazitaet(List.of(z))));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ZugZustand z, boolean empty) {
                super.updateItem(z, empty);
                setText(null);
                if (empty || z == null) {
                    setGraphic(null);
                    return;
                }
                List<String> teile = kapazitaetTeile(z);
                if (teile.isEmpty()) {
                    setGraphic(null);
                    return;
                }
                if (teile.size() == 1) {
                    setGraphic(new Label(teile.get(0)));
                    return;
                }
                boolean auf = AUFGEKLAPPT.contains(z.getName());
                Button pfeil = new Button(auf ? "▲" : "▼");
                pfeil.setFocusTraversable(false);
                pfeil.setStyle("-fx-padding: 0 5 0 5; -fx-font-size: 10px;");
                pfeil.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // sonst wird ▼ bei schmaler Spalte zu "…"
                pfeil.setTooltip(Symbole.tip(auf ? "Zuklappen" : "Einzelne Kapazitäten mit Einheit zeigen"));
                pfeil.setOnAction(e -> {
                    if (!AUFGEKLAPPT.remove(z.getName())) {
                        AUFGEKLAPPT.add(z.getName());
                    }
                    t.refresh();
                });
                Label kopf = new Label(teile.size() + " Kapazitäten");
                kopf.setStyle("-fx-opacity: 0.75;");
                HBox zeile = new HBox(6, pfeil, kopf);
                zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                if (!auf) {
                    setGraphic(zeile);
                    return;
                }
                VBox box = new VBox(1, zeile);
                for (String teil : teile) {
                    Label l = new Label(teil);
                    l.setPadding(new Insets(0, 0, 0, 22));
                    box.getChildren().add(l);
                }
                setGraphic(box);
            }
        });
        return c;
    }

    /** Einzelne Kapazitäten eines Zugs mit Einheit: je Wagenladung, dann Sitzplätze je Klasse. */
    static List<String> kapazitaetTeile(ZugZustand z) {
        List<String> l = new ArrayList<>();
        for (com.example.itrain_logistik.kern.Wagenladung w : z.getWagenladungen()) {
            if (w.getKapazitaet() <= 0) {
                continue;
            }
            l.add(Symbole.ganz(w.getKapazitaet()) + " "
                    + w.getEinheit() + " – " + w.getWagenName()
                    + (w.isJoker() ? " (Joker)" : ""));
        }
        if (z.getSitzplaetzeErste() > 0) {
            l.add(Symbole.ganz(z.getSitzplaetzeErste()) + " Plätze 1. Klasse");
        }
        if (z.getSitzplaetzeZweite() > 0) {
            l.add(Symbole.ganz(z.getSitzplaetzeZweite()) + " Plätze 2. Klasse");
        }
        return l;
    }

    /**
     * Reisende an Bord bei Personenzügen (Anwender 12.10.2026): gelbes Personen-Symbol, dann 1. / 2. Klasse;
     * leer bei Zügen ohne Sitzplätze und ohne Fahrgäste.
     */
    private static TableColumn<ZugZustand, ZugZustand> reisende() {
        TableColumn<ZugZustand, ZugZustand> c = new TableColumn<>();
        Label kopf = new Label("Reisende 1/2");
        kopf.setTooltip(Symbole.tip("Reisende an Bord: 1. Klasse / 2. Klasse"));
        c.setGraphic(kopf);
        c.setPrefWidth(110);
        c.setSortable(false);
        c.setCellValueFactory(z -> new javafx.beans.property.SimpleObjectProperty<>(z.getValue()));
        c.setCellFactory(sp -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(ZugZustand z, boolean empty) {
                super.updateItem(z, empty);
                double[] p = empty || z == null ? null : z.passagiereSumme();
                if (p == null || (z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() <= 0 && p[0] + p[1] < 0.5)) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(Symbole.person(14));
                setText(Symbole.zahl(p[0]) + " / " + Symbole.zahl(p[1]));
            }
        });
        return c;
    }

    // ================================================================ Stationen

    private static Node stationen(Anlage a, List<Runnable> akt) {
        TableView<Station> t = tabelle(a::stationen, akt);
        t.getColumns().add(text("Name", 180, Station::getAnzeigeName));
        t.getColumns().add(text("Stadt", 140, s -> {
            Ort o = a.ortVon(s);
            return o == null ? "" : o.getAnzeigeName();
        }));
        t.getColumns().add(text("Art", 90, s -> {
            boolean p = !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty();
            boolean g = !s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty();
            return p && g ? "Gemischt" : p ? "Passagiere" : g ? "Güter" : "–";
        }));
        t.getColumns().add(text("Gleise/Blöcke", 220, s -> String.join(", ", s.getGleise().stream()
                .map(g -> g.getAnzeigeName() + (g.getBlockName() != null && !g.getBlockName().equals(g.getAnzeigeName())
                        ? " (" + g.getBlockName() + ")" : "")).toList())));
        t.getColumns().add(text("Wartende/Kapazität", 130, s -> {
            double w = 0;
            double k = 0;
            for (Gleis g : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                for (String r : g.warteschlangen()) {
                    w += g.wartendeSumme(r);
                    k += g.getKapazitaet();
                }
            }
            return k > 0 ? Symbole.zahl(w) + "/" + Symbole.zahl(k) : "";
        }));
        t.getColumns().add(text("Routen", 200, s -> String.join(", ", linienAn(a, s))));
        return t;
    }

    // ================================================================ Lager

    private static Node lager(Anlage a, List<Runnable> akt) {
        TableView<Lager> t = tabelle(a::lager, akt);
        t.getColumns().add(text("Name", 180, Lager::getAnzeigeName));
        t.getColumns().add(text("Station", 160, l -> String.join(", ",
                a.stationenFuer(l.getId()).stream().map(Station::getAnzeigeName).toList())));
        t.getColumns().add(symbole("Bestände", 200, a, l -> new ArrayList<>(l.getBestand().keySet())));
        t.getColumns().add(zahl("Im Lager", 90, Lager::summe));
        t.getColumns().add(zahl("Kapazität", 90, l -> l.getKapazitaet()));
        t.getColumns().add(prozent("Auslastung", l -> quote(l.summe(), l.getKapazitaet())));
        return t;
    }

    // ================================================================ Staedte

    private static Node staedte(Anlage a, EchtzeitKern kern, List<Runnable> akt) {
        TableView<Ort> t = tabelle(a::orte, akt);
        t.getColumns().add(text("Name", 180, Ort::getAnzeigeName));
        t.getColumns().add(text("Größe", 90, o -> o.getArt().getLabel()));
        t.getColumns().add(zahl("Einwohner", 90, Ort::getEinwohner));
        t.getColumns().add(zahl("Wartend", 80, o -> {
            double w = 0;
            for (Station s : a.stationenFuer(o.getId())) {
                for (Gleis g : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                    for (String r : g.warteschlangen()) {
                        w += g.wartendeSumme(r);
                    }
                }
            }
            return w;
        }));
        t.getColumns().add(zahl("Ankommend/h", 100, Ort::getAnkommendDieseStunde));
        t.getColumns().add(symbole("Güter", 180, a, o -> new ArrayList<>(o.getBedarf().keySet())));
        t.getColumns().add(zahl("Versorgung heute", 120, o -> o.getStatistik().wert("Versorgung", kern.getTag())));
        t.getColumns().add(zahl("Nachfrage heute", 120, o -> o.getStatistik().wert("Nachfrage", kern.getTag())));
        return t;
    }

    // ================================================================ Industrien

    private static Node industrien(Anlage a, EchtzeitKern kern, List<Runnable> akt) {
        TableView<Betrieb> t = tabelle(a::betriebe, akt);
        t.getColumns().add(text("Name", 180, Betrieb::getAnzeigeName));
        t.getColumns().add(text("Station", 140, b -> String.join(", ",
                a.stationenFuer(b.getId()).stream().map(Station::getAnzeigeName).toList())));
        t.getColumns().add(symbole("Bedarf", 110, a, Betrieb::eingangsGueter));
        t.getColumns().add(symbole("Produkt", 110, a, Betrieb::ausgangsGueter));
        t.getColumns().add(zahl("Eingang", 80, b -> b.getBestandEin().values().stream().mapToDouble(Double::doubleValue).sum()));
        t.getColumns().add(zahl("Ausgang", 80, b -> b.getBestandAus().values().stream().mapToDouble(Double::doubleValue).sum()));
        t.getColumns().add(prozent("Auslastung", Betrieb::auslastung));
        t.getColumns().add(zahl("Rate/h", 70, Betrieb::getRateProStunde));
        t.getColumns().add(text("Zustand", 200, Betrieb::getZustand));
        t.getColumns().add(zahl("Versand heute", 100, b -> b.getStatistik().wert("Versand", kern.getTag())));
        Label hinweis = new Label("Auslastung = Füllstand der Ausgangslager. Rate/h = Ausführungen des Rezepts "
                + "je Modellstunde.");
        return mitHinweis(t, hinweis);
    }

    // ================================================================ Gesamt (Güterübersicht)

    /** Eine Zeile der Gesamttabelle. */
    private record GesamtZeile(String name, String gutId, double verfuegbar, double benoetigt, String status,
            Station station, Gleis gleis, ZugZustand zug) {
    }

    private static Node gesamt(Anlage a, EchtzeitKern kern, List<Runnable> akt) {
        TreeTableView<GesamtZeile> t = new TreeTableView<>();
        t.setShowRoot(false);
        t.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TreeTableColumn<GesamtZeile, String> name = new TreeTableColumn<>("Station / Gleis / Zug");
        name.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValue().name()));
        name.setPrefWidth(260);
        TreeTableColumn<GesamtZeile, String> gut = new TreeTableColumn<>("Gut");
        gut.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValue().gutId() == null ? ""
                : a.gutName(d.getValue().getValue().gutId())));
        gut.setPrefWidth(140);
        TreeTableColumn<GesamtZeile, String> verf = new TreeTableColumn<>("Verfügbar");
        verf.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValue().gutId() == null ? ""
                : Symbole.zahl(d.getValue().getValue().verfuegbar())));
        TreeTableColumn<GesamtZeile, String> ben = new TreeTableColumn<>("Benötigt / frei");
        ben.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValue().gutId() == null ? ""
                : Double.isInfinite(d.getValue().getValue().benoetigt()) ? "∞"
                : Symbole.zahl(d.getValue().getValue().benoetigt())));
        TreeTableColumn<GesamtZeile, String> status = new TreeTableColumn<>("Status");
        status.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getValue().status()));
        status.setCellFactory(c -> new javafx.scene.control.TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                String farbe = item == null ? "" : item.startsWith("rot") ? "#cc3333"
                        : item.startsWith("gelb") ? "#c9a400" : item.startsWith("grün") ? "#2f8f4e" : "";
                setStyle(farbe.isEmpty() ? "" : "-fx-text-fill: " + farbe + "; -fx-font-weight: bold;");
            }
        });
        t.getColumns().setAll(List.of(name, gut, verf, ben, status));

        Set<String> aufgeklappt = new LinkedHashSet<>();
        akt.add(() -> {
            TreeItem<GesamtZeile> wurzel = new TreeItem<>(new GesamtZeile("", null, 0, 0, "", null, null, null));
            for (Station s : a.stationen()) {
                TreeItem<GesamtZeile> st = knoten(new GesamtZeile(s.getAnzeigeName(), null, 0, 0, "", s, null, null),
                        aufgeklappt);
                boolean stationRot = false;
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    TreeItem<GesamtZeile> gl = knoten(new GesamtZeile(g.getAnzeigeName(), null, 0, 0, "", s, g, null),
                            aufgeklappt);
                    Set<String> gueter = new LinkedHashSet<>(g.getAusgang().keySet());
                    for (AnlagenObjekt o : a.einzugsbereich(s)) {
                        if (o instanceof Betrieb b) {
                            gueter.addAll(b.eingangsGueter());
                        } else if (o instanceof Ort ort) {
                            gueter.addAll(ort.getBedarf().keySet());
                        }
                    }
                    for (String gutId : gueter) {
                        if (!g.erlaubt(gutId)) {
                            continue;
                        }
                        double verfuegbar = g.getAusgang().getOrDefault(gutId, 0.0);
                        double benoetigt = kern.aufnahme(s, gutId);
                        String stat;
                        if (verfuegbar > 0.5) {
                            stat = "rot – Abholung nötig";
                        } else if (benoetigt > 0.5 && !Double.isInfinite(benoetigt)) {
                            boolean imLager = a.lager().stream().anyMatch(l -> l.getBestand().getOrDefault(gutId, 0.0) > 0.5);
                            stat = imLager ? "gelb – Anlieferung aus Lager möglich" : "rot – Anlieferung nötig";
                        } else {
                            stat = "grün";
                        }
                        stationRot |= stat.startsWith("rot");
                        gl.getChildren().add(new TreeItem<>(new GesamtZeile("", gutId, verfuegbar, benoetigt, stat, s, g, null)));
                    }
                    ZugZustand z = g.getZugImGleis() != null ? a.getZuege().get(g.getZugImGleis()) : null;
                    if (z != null) {
                        TreeItem<GesamtZeile> zug = knoten(new GesamtZeile("Zug " + z.getName(), null, 0, 0, "hält", s, g, z),
                                aufgeklappt);
                        for (Map.Entry<String, Double> e : z.ladungJeGut().entrySet()) {
                            zug.getChildren().add(new TreeItem<>(new GesamtZeile("", e.getKey(), e.getValue(),
                                    kern.aufnahme(s, e.getKey()), "", s, g, z)));
                        }
                        gl.getChildren().add(zug);
                    }
                    st.getChildren().add(gl);
                }
                st.setValue(new GesamtZeile(s.getAnzeigeName(), null, 0, 0, stationRot ? "rot" : "grün", s, null, null));
                if (!st.getChildren().isEmpty()) {
                    wurzel.getChildren().add(st);
                }
            }
            t.setRoot(wurzel);
        });

        Button laden = new Button("Ausgewähltes Gut in den haltenden Zug laden…");
        laden.setOnAction(e -> {
            TreeItem<GesamtZeile> sel = t.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue().gutId() == null || sel.getValue().zug() != null) {
                return;
            }
            Gleis g = sel.getValue().gleis();
            ZugZustand z = g.getZugImGleis() != null ? a.getZuege().get(g.getZugImGleis()) : null;
            if (z == null) {
                return;
            }
            Double menge = mengeFragen("Laden", sel.getValue().verfuegbar());
            if (menge != null) {
                kern.ladeVonHand(z, g, sel.getValue().gutId(), menge);
            }
        });
        Button entladen = new Button("Ausgewähltes Gut aus dem Zug entladen…");
        entladen.setOnAction(e -> {
            TreeItem<GesamtZeile> sel = t.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue().gutId() == null || sel.getValue().zug() == null) {
                return;
            }
            Double menge = mengeFragen("Entladen", sel.getValue().verfuegbar());
            if (menge != null) {
                kern.entladeVonHand(sel.getValue().zug(), sel.getValue().gleis(), sel.getValue().gutId(), menge);
            }
        });
        Label hinweis = new Label("Rot: Transport nötig · Gelb: über ein Lager lösbar · Grün: nichts offen. "
                + "Umladen von Hand ist zum Testen gedacht.");
        hinweis.setWrapText(true);
        VBox box = new VBox(6, t, new HBox(6, laden, entladen), hinweis);
        box.setPadding(new Insets(6));
        javafx.scene.layout.VBox.setVgrow(t, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private static TreeItem<GesamtZeile> knoten(GesamtZeile z, Set<String> aufgeklappt) {
        TreeItem<GesamtZeile> item = new TreeItem<>(z);
        String schluessel = z.name() + "|" + (z.station() != null ? z.station().getId() : "")
                + "|" + (z.gleis() != null ? z.gleis().getId() : "");
        item.setExpanded(!aufgeklappt.contains("zu:" + schluessel));
        item.expandedProperty().addListener((obs, alt, neu) -> {
            if (neu) {
                aufgeklappt.remove("zu:" + schluessel);
            } else {
                aufgeklappt.add("zu:" + schluessel);
            }
        });
        return item;
    }

    private static Double mengeFragen(String titel, double vorschlag) {
        TextInputDialog d = new TextInputDialog(Symbole.zahl(vorschlag));
        d.setTitle(titel);
        d.setHeaderText("Menge (Einheiten)");
        return d.showAndWait().map(s -> ObjektDialoge.parse(s, vorschlag)).orElse(null);
    }

    // ================================================================ Kreislauf

    private static Node kreislauf(Anlage a, List<Runnable> akt) {
        TableView<Kreislauf.Bilanz> t = tabelle(() -> {
            List<Kreislauf.Bilanz> l = new ArrayList<>(Kreislauf.bilanz(a));
            l.sort(Comparator.comparing((Kreislauf.Bilanz b) -> b.ampel().ordinal()));
            return l;
        }, akt);
        TableColumn<Kreislauf.Bilanz, List<String>> sym = symbole("", 30, a, b -> List.of(b.gut().getId()));
        sym.setMaxWidth(36);
        t.getColumns().add(sym);
        t.getColumns().add(text("Gut", 140, b -> b.gut().getName()));
        t.getColumns().add(zahl("Erzeugung/h", 100, Kreislauf.Bilanz::erzeugung));
        t.getColumns().add(zahl("Verbrauch/h", 100, Kreislauf.Bilanz::verbrauch));
        t.getColumns().add(zahl("Lagerplatz", 90, Kreislauf.Bilanz::lagerplatz));
        TableColumn<Kreislauf.Bilanz, String> ampel = text("Bilanz", 160, b -> b.ampel().getLabel());
        ampel.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                Kreislauf.Bilanz b = empty || getTableRow() == null ? null : getTableRow().getItem();
                String farbe = b == null ? "" : switch (b.ampel()) {
                    case ROT -> "#cc3333";
                    case GELB -> "#c9a400";
                    case GRUEN -> "#2f8f4e";
                    case GRAU -> "#777777";
                };
                setStyle(farbe.isEmpty() ? "" : "-fx-text-fill: " + farbe + "; -fx-font-weight: bold;");
            }
        });
        t.getColumns().add(ampel);
        Label offen = new Label();
        offen.setWrapText(true);
        akt.add(() -> {
            List<String> l = new ArrayList<>();
            for (Kreislauf.OffenesEnde e : Kreislauf.alleOffenenEnden(a)) {
                l.add(e.betrieb().getAnzeigeName() + ": " + a.gutName(e.gutId())
                        + (e.ausgang() ? " ohne Abnehmer" : " ohne Lieferant"));
            }
            offen.setText(l.isEmpty() ? "Keine offenen Enden." : "Offene Enden: " + String.join(" · ", l));
        });
        return mitHinweis(t, offen);
    }

    // ================================================================ Kapazitaeten

    /** Bedarf und vorhandene Transportkapazitaet je Ladeklasse bzw. Gut. */
    private record KapZeile(String name, Node symbol, double bedarfJeStunde, double kapazitaetJeFahrt, int fahrzeuge) {
        double fahrten() {
            return kapazitaetJeFahrt <= 0 ? Double.NaN : bedarfJeStunde / kapazitaetJeFahrt;
        }
    }

    private static Node kapazitaeten(Anlage a, EchtzeitKern kern, Supplier<LogistikSnapshot> snapshot,
            List<Runnable> akt) {
        Supplier<List<ZugZustand>> zuege = () -> {
            LogistikSnapshot s = snapshot.get();
            List<ZugZustand> l = new ArrayList<>();
            if (s != null) {
                for (TrainEntry t : s.trains()) {
                    l.add(kern.zug(t.getName()));
                }
            }
            return l;
        };
        TableView<KapZeile> jeKlasse = tabelle(() -> {
            List<ZugZustand> alle = zuege.get();
            List<KapZeile> l = new ArrayList<>();
            for (com.example.itrain_logistik.kern.Ladeklasse k : com.example.itrain_logistik.kern.Ladeklasse.gueterKlassen()) {
                double bedarf = 0;
                for (com.example.itrain_logistik.kern.Gut g : a.getGueter().values()) {
                    if (g.getLadeklasse() == k) {
                        bedarf += transportBedarf(a, g.getId());
                    }
                }
                double kap = 0;
                int n = 0;
                for (ZugZustand z : alle) {
                    double zk = 0;
                    for (com.example.itrain_logistik.kern.Wagenladung w : z.getWagenladungen()) {
                        zk += w.isJoker() || w.getKlasse() == k ? w.getKapazitaet() : 0;
                    }
                    kap += zk;
                    n += zk > 0 ? 1 : 0;
                }
                if (bedarf > 0 || kap > 0) {
                    l.add(new KapZeile(k.getLabel(), null, bedarf, kap, n));
                }
            }
            double passagiere = 0;
            for (Ort o : a.orte()) {
                passagiere += o.getPassagiereProStunde();
            }
            double plaetze = 0;
            int n = 0;
            for (ZugZustand z : alle) {
                double p = z.getSitzplaetzeErste() + z.getSitzplaetzeZweite();
                plaetze += p;
                n += p > 0 ? 1 : 0;
            }
            if (passagiere > 0 || plaetze > 0) {
                l.add(new KapZeile("Passagiere", null, passagiere, plaetze, n));
            }
            return l;
        }, akt);
        jeKlasse.getColumns().add(text("Ladeklasse", 140, KapZeile::name));
        jeKlasse.getColumns().add(zahl("Bedarf je Modellstunde", 160, KapZeile::bedarfJeStunde));
        jeKlasse.getColumns().add(zahl("Kapazität aller Züge je Fahrt", 190, KapZeile::kapazitaetJeFahrt));
        jeKlasse.getColumns().add(zahl("Züge", 60, KapZeile::fahrzeuge));
        jeKlasse.getColumns().add(text("Nötige volle Fahrten je Modellstunde", 230, z ->
                Double.isNaN(z.fahrten()) ? "kein Zug kann das laden" : Symbole.ganz(z.fahrten())));
        jeKlasse.setPrefHeight(200);

        TableView<KapZeile> jeGut = tabelle(() -> {
            List<ZugZustand> alle = zuege.get();
            List<KapZeile> l = new ArrayList<>();
            for (com.example.itrain_logistik.kern.Gut g : a.getGueter().values()) {
                double bedarf = transportBedarf(a, g.getId());
                if (bedarf <= 0) {
                    continue;
                }
                double kap = 0;
                int n = 0;
                for (ZugZustand z : alle) {
                    double zk = 0;
                    for (com.example.itrain_logistik.kern.Wagenladung w : z.getWagenladungen()) {
                        boolean passt = w.isJoker() || (!w.getErlaubt().isEmpty() ? w.getErlaubt().contains(g.getId())
                                : w.getKlasse() == g.getLadeklasse());
                        zk += passt ? w.getKapazitaet() : 0;
                    }
                    kap += zk;
                    n += zk > 0 ? 1 : 0;
                }
                l.add(new KapZeile(g.getName(), Symbole.gut(g, 14), bedarf, kap, n));
            }
            return l;
        }, akt);
        TableColumn<KapZeile, Node> sym = new TableColumn<>("");
        sym.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().symbol()));
        sym.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Node item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : item);
            }
        });
        sym.setMaxWidth(36);
        jeGut.getColumns().add(sym);
        jeGut.getColumns().add(text("Gut", 140, KapZeile::name));
        jeGut.getColumns().add(zahl("Bedarf je Modellstunde", 160, KapZeile::bedarfJeStunde));
        jeGut.getColumns().add(zahl("Kapazität passender Wagen je Fahrt", 220, KapZeile::kapazitaetJeFahrt));
        jeGut.getColumns().add(zahl("Züge", 60, KapZeile::fahrzeuge));
        jeGut.getColumns().add(text("Nötige volle Fahrten je Modellstunde", 230, z ->
                Double.isNaN(z.fahrten()) ? "kein Zug kann das laden" : Symbole.ganz(z.fahrten())));
        Label hinweis = new Label("Bedarf = was je Modellstunde bewegt werden muss: das Größere aus Erzeugung und "
                + "Verbrauch eines Guts, bei Passagieren das Aufkommen aller Orte. Kapazität = Summe der Wagen aller Züge "
                + "(Felder Kapazität, Wagenladungen, aktive Güter bzw. Sitz- und Stehplätze). Ein Gut zählt nur bei "
                + "Wagen, die es laden dürfen; Behälterwagen (Lade-Joker) zählen überall. Güter, die ohne Zug im selben Einzugsbereich verbraucht werden, sind "
                + "hier noch mitgezählt.");
        hinweis.setWrapText(true);
        hinweis.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox v = new VBox(6, new Label("Je Ladeklasse"), jeKlasse, new Label("Je Gut"), jeGut, hinweis);
        v.setPadding(new Insets(6));
        javafx.scene.layout.VBox.setVgrow(jeGut, javafx.scene.layout.Priority.ALWAYS);
        return v;
    }

    private static double transportBedarf(Anlage a, String gutId) {
        return Math.max(Kreislauf.erzeugung(a, gutId), Kreislauf.verbrauch(a, gutId));
    }

    // ================================================================ Ereignisse

    private static Node ereignisse(EchtzeitKern kern, List<Runnable> akt) {
        TableView<EchtzeitKern.Ereignis> t = tabelle(kern::getEreignisse, akt);
        t.getColumns().add(text("Modellzeit", 100, EchtzeitKern.Ereignis::zeit));
        t.getColumns().add(text("Ereignis", 700, EchtzeitKern.Ereignis::text));
        return t;
    }

    private static Node mitHinweis(Node tabelle, Label hinweis) {
        hinweis.setWrapText(true);
        hinweis.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox v = new VBox(6, tabelle, hinweis);
        v.setPadding(new Insets(6));
        javafx.scene.layout.VBox.setVgrow(tabelle, javafx.scene.layout.Priority.ALWAYS);
        return v;
    }
}
