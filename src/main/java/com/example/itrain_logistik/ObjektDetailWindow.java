package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.EchtzeitKern;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Rezept;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Statistik;
import com.example.itrain_logistik.kern.Uebergabe;
import com.example.itrain_logistik.kern.ZugZustand;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Detailfenster eines Objekts (Konzept Abschnitt 10.2), aufgebaut wie die TF-Fenster, ohne
 * Booster, Arbeiter und Ertrag: Betrieb (Produktion, Eingangs- und Ausgangsbestaende,
 * Gueterfluss), Station (Gleise mit Guetern und Wartenden, haltender Zug), Lager (Bestaende,
 * Eingehend/Ausgehend), Ort (Passagiere, Bedarf). Wird jede Sekunde neu aufgebaut.
 */
public final class ObjektDetailWindow {

    private static final Map<String, Stage> OFFEN = new HashMap<>();

    private ObjektDetailWindow() {
    }

    /** Öffnet das Detailfenster; liefert das neue Fenster bzw. null, wenn es schon offen war (nur nach vorn geholt). */
    public static Stage show(Stage owner, AnlagenObjekt o, Anlage anlage, EchtzeitKern kern,
            Supplier<List<BlockEntry>> bloecke, Runnable onChanged) {
        Stage vorhanden = OFFEN.get(o.getId());
        if (vorhanden != null && vorhanden.isShowing()) {
            vorhanden.toFront();
            return null;
        }
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.setTitle(o.getAnzeigeName());
        VBox inhalt = new VBox(8);
        inhalt.setPadding(new Insets(10));
        ScrollPane scroll = new ScrollPane(inhalt);
        scroll.setFitToWidth(true);
        Button bearbeiten = new Button("Bearbeiten…");
        bearbeiten.setOnAction(e -> {
            if (ObjektDialoge.bearbeite(stage, o, anlage, bloecke.get())) {
                stage.setTitle(o.getAnzeigeName());
                onChanged.run();
            }
        });
        HBox leiste = new HBox(8, bearbeiten);
        leiste.setAlignment(Pos.CENTER_LEFT);
        Runnable[] aufbauenRef = new Runnable[1];
        if (!(o instanceof com.example.itrain_logistik.kern.Infrastruktur)) {
            Button null0 = new Button("Auf 0 setzen");
            null0.setTooltip(new javafx.scene.control.Tooltip(
                    "Bestände, Wartende, offenen Bedarf und Statistik dieses Objekts auf 0 setzen"));
            null0.setOnAction(e -> {
                o.aufNull();
                onChanged.run();
                aufbauenRef[0].run();
            });
            javafx.scene.layout.Region platz = new javafx.scene.layout.Region();
            HBox.setHgrow(platz, javafx.scene.layout.Priority.ALWAYS);
            // Pausen-Schalter nur für dieses Objekt; der Rest der Anlage läuft weiter (Anwender 14.10.2026).
            javafx.scene.control.ToggleButton pause = new javafx.scene.control.ToggleButton();
            Runnable pauseZeigen = () -> {
                boolean p = o.isPausiert();
                pause.setText(p ? "▶ Start" : "❚❚ Pause");
                pause.setStyle(p ? "-fx-text-fill: #d03030; -fx-font-weight: bold;" : "");
                pause.setTooltip(new javafx.scene.control.Tooltip(p
                        ? "Dieses Objekt ist angehalten – Klick lässt es wieder laufen"
                        : "Nur dieses Objekt anhalten; alles andere läuft weiter"));
            };
            pause.setSelected(o.isPausiert());
            pauseZeigen.run();
            pause.setOnAction(e -> {
                o.setPausiert(pause.isSelected());
                pauseZeigen.run();
                onChanged.run();
            });
            leiste.getChildren().addAll(null0, platz,
                    AnlagenCanvas.tempoRegler(anlage, o::getTempo, o::setTempo, onChanged), pause);
            for (Node n : leiste.getChildren()) {
                if (n instanceof javafx.scene.control.Control c) {
                    c.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                }
            }
        }
        VBox root = new VBox(scroll, leiste);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        leiste.setPadding(new Insets(6, 10, 8, 10));
        // Nur ersetzen, wenn sich ein Text geändert hat - sonst gehen Tooltips und Kontextmenüs verloren.
        Runnable aufbauen = () -> {
            List<Node> neu = baue(o, anlage, kern, onChanged);
            VBox probe = new VBox();
            probe.getChildren().setAll(neu);
            String text = Symbole.texte(probe);
            if (!text.equals(inhalt.getUserData())) {
                inhalt.setUserData(text);
                inhalt.getChildren().setAll(List.copyOf(probe.getChildren()));
            }
        };
        aufbauenRef[0] = aufbauen;
        aufbauen.run();
        Timeline t = new Timeline(new KeyFrame(Duration.seconds(1), e -> aufbauen.run()));
        t.setCycleCount(Timeline.INDEFINITE);
        t.play();
        Scene scene = new Scene(root, 660, 640);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            t.stop();
            OFFEN.remove(o.getId());
        });
        // Größe merken, je Objektart (Anwender 22.10.2026)
        WindowState.apply(stage, "objektdetail." + o.getArt().name(), 660, 640);
        OFFEN.put(o.getId(), stage);
        stage.show();
        return stage;
    }

    public static void closeAll() {
        for (Stage s : List.copyOf(OFFEN.values())) {
            s.close();
        }
        OFFEN.clear();
    }

    /** Rechtsklick "Auf 0 setzen" auf eine Zeile/Zelle. */
    private static <N extends Node> N nullMenue(N n, Runnable setzen, Runnable geaendert) {
        n.setOnContextMenuRequested(e -> {
            javafx.scene.control.MenuItem mi = new javafx.scene.control.MenuItem("Auf 0 setzen");
            mi.setOnAction(x -> {
                setzen.run();
                geaendert.run();
            });
            new javafx.scene.control.ContextMenu(mi).show(n, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return n;
    }

    private static List<Node> baue(AnlagenObjekt o, Anlage a, EchtzeitKern kern, Runnable geaendert) {
        VBox v = new VBox(8);
        Label titel = new Label(o.getAnzeigeName().equals(o.getArt().getLabel()) ? o.getAnzeigeName()
                : o.getAnzeigeName() + "  (" + o.getArt().getLabel() + ")");
        titel.setFont(Font.font("System", FontWeight.BOLD, 16));
        v.getChildren().add(titel);
        if (o instanceof Betrieb b) {
            betrieb(v, b, a, kern, geaendert);
        } else if (o instanceof Station s) {
            station(v, s, a, geaendert);
        } else if (o instanceof Lager l) {
            lager(v, l, a, kern, geaendert);
        } else if (o instanceof Ort ort) {
            ort(v, ort, a, kern, geaendert);
        } else if (o instanceof Uebergabe u) {
            uebergabe(v, u, a, kern);
        } else if (o instanceof com.example.itrain_logistik.kern.Infrastruktur) {
            List<String> frei = a.freieBloecke();
            abschnitt(v, "Strecke: " + frei.size() + " von " + a.getBloecke().size() + " Blöcken ohne Gleis");
            Label liste = new Label(frei.isEmpty() ? "Alle Blöcke sind Gleisen zugeordnet." : String.join("\n", frei));
            v.getChildren().add(liste);
            v.getChildren().add(new Label("Zuordnen über \"Bearbeiten…\"."));
        }
        return List.of(v);
    }

    // ------------------------------------------------------------------ Betrieb

    private static void betrieb(VBox v, Betrieb b, Anlage a, EchtzeitKern kern, Runnable geaendert) {
        abschnitt(v, "Produktion");
        for (Rezept r : b.getRezepte()) {
            HBox zeile = new HBox(4);
            zeile.setAlignment(Pos.CENTER_LEFT);
            r.getEingang().forEach((g, m) -> zeile.getChildren().add(mengeSymbol(a.gut(g), m)));
            if (r.getEingang().isEmpty()) {
                zeile.getChildren().add(new Label("aus dem Nichts"));
            }
            zeile.getChildren().add(new Label("  →  "));
            r.getAusgang().forEach((g, m) -> zeile.getChildren().add(mengeSymbol(a.gut(g), m)));
            v.getChildren().add(zeile);
        }
        ProgressBar fortschritt = new ProgressBar(Math.min(1, b.getFortschritt()));
        fortschritt.setPrefWidth(300);
        v.getChildren().add(new HBox(8, new Label(ObjektDialoge.zahl(b.getRateProStunde()) + " Ausführungen/h"),
                fortschritt));
        Label zustand = new Label(b.getZustand());
        if (!b.getZustand().startsWith("Läuft")) {
            zustand.setStyle("-fx-text-fill: #cc3333;");
        }
        v.getChildren().add(zustand);
        if (b.isVirtuell()) {
            v.getChildren().add(new Label("Liefert immer, ohne auf der Anlage zu sein."));
        }
        abschnitt(v, "Eingangsbestände");
        v.getChildren().add(bestaende(a, b.getLagerEin(), b.getBestandEin(), b, geaendert));
        abschnitt(v, "Ausgangsbestände");
        v.getChildren().add(bestaende(a, b.getLagerAus(), b.getBestandAus(), null, geaendert));
        abschnitt(v, "Stationen");
        v.getChildren().add(new Label(stationen(a, b)));
        com.example.itrain_logistik.kern.Bewertung.Ergebnis bw = com.example.itrain_logistik.kern.Bewertung.betrieb(a, b);
        bewertungTabelle(v, a, bw, "Erfüllung");
        abschnitt(v, "Güterfluss je Modelltag");
        v.getChildren().add(diagramm(b.getStatistik(), kern.getTag(), "Produziert", "Verbraucht", "Versand"));
        bewertungStatistik(v, b, bw, kern, "Erfüllung");
    }

    // ------------------------------------------------------------------ Zufriedenheit / Erfüllung (11.11.2026)

    /** Objekte, deren Maßnahmen gerade aufgeklappt sind (bleibt beim sekündlichen Neuaufbau erhalten). */
    private static final java.util.Set<String> MASSNAHMEN_OFFEN = new java.util.HashSet<>();

    /** Farbiges Kästchen mit Prozentwert (rot/gelb/grün), Schrift lesbar. */
    private static Label prozent(double p, double groesse) {
        Label l = new Label(Math.round(p) + " %");
        String bg = com.example.itrain_logistik.kern.Bewertung.farbe(p);
        l.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 4; -fx-padding: 1 7 1 7; "
                + "-fx-font-weight: bold; -fx-font-size: " + groesse + "px; -fx-text-fill: "
                + (p >= 50 && p <= 85 ? "#1a1a1a" : "white") + ";");
        l.setMinWidth(Label.USE_PREF_SIZE);
        return l;
    }

    private static void bewertungTabelle(VBox v, Anlage a, com.example.itrain_logistik.kern.Bewertung.Ergebnis e, String name) {
        if (e.leer()) {
            return;
        }
        abschnitt(v, name);
        HBox kopf = new HBox(8, new Label(name), prozent(e.prozent(), 14));
        kopf.setAlignment(Pos.CENTER_LEFT);
        v.getChildren().add(kopf);
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(10);
        g.setVgap(4);
        Label k1 = new Label("Bewertung");
        Label k2 = new Label("Erfüllungsgrad");
        k1.setStyle("-fx-font-weight: bold;");
        k2.setStyle("-fx-font-weight: bold;");
        g.addRow(0, new Label(""), k1, k2);
        int r = 1;
        for (var p : e.posten()) {
            Node sym = p.gutId() == null ? Symbole.person(16) : Symbole.gut(a.gut(p.gutId()), 18);
            g.addRow(r++, sym, new Label(p.text()), prozent(p.prozent(), 12));
        }
        v.getChildren().add(g);
    }

    private static void bewertungStatistik(VBox v, AnlagenObjekt o, com.example.itrain_logistik.kern.Bewertung.Ergebnis e,
            EchtzeitKern kern, String name) {
        if (e.leer()) {
            return;
        }
        // Tagesmittel als Balken in der Ampelfarbe
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis(0, 100, 25);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setPrefHeight(170);
        chart.setLegendVisible(false);
        chart.setTitle(Uebersetzung.tr("{0} je Modelltag (%)", Uebersetzung.tr(name)));
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        int heute = kern.getTag();
        for (int tag = Math.max(0, heute - 9); tag <= heute; tag++) {
            double w = EchtzeitKern.bewertungAmTag(o.getStatistik(), tag);
            XYChart.Data<String, Number> d = new XYChart.Data<>("Tag " + (tag + 1), Math.max(0, Math.round(w)));
            String farbe = w < 0 ? "transparent" : com.example.itrain_logistik.kern.Bewertung.farbe(w);
            d.nodeProperty().addListener((obs, alt, n) -> {
                if (n != null) {
                    n.setStyle("-fx-bar-fill: " + farbe + ";");
                }
            });
            s.getData().add(d);
        }
        chart.getData().add(s);
        v.getChildren().add(chart);
        // Maßnahmen: gesperrt, solange der Wert über 50 % liegt (Anwender 11.11.2026)
        Button massnahmen = new Button("Maßnahmen");
        boolean gesperrt = e.prozent() > 50;
        massnahmen.setDisable(gesperrt);
        if (gesperrt) {
            MASSNAHMEN_OFFEN.remove(o.getId());
        }
        VBox text = new VBox(3);
        List<String> liste = e.massnahmen().isEmpty()
                ? List.of("Keine einzelne Ursache unter 50 % – Posten mit gelbem Wert zuerst verbessern.")
                : e.massnahmen();
        for (String m : liste) {
            Label l = new Label(m);
            l.setGraphic(new Label("•"));
            l.setWrapText(true);
            text.getChildren().add(l);
        }
        boolean offen = MASSNAHMEN_OFFEN.contains(o.getId());
        text.setVisible(offen);
        text.setManaged(offen);
        massnahmen.setOnAction(ev -> {
            boolean neu = !MASSNAHMEN_OFFEN.remove(o.getId());
            if (neu) {
                MASSNAHMEN_OFFEN.add(o.getId());
            }
            text.setVisible(neu);
            text.setManaged(neu);
        });
        Label hinweis = new Label(gesperrt ? "(erst unter 50 % nötig)" : "Was zu tun ist, um den Bedarf zu decken");
        hinweis.setStyle("-fx-opacity: 0.7;");
        HBox zeile = new HBox(8, massnahmen, hinweis);
        zeile.setAlignment(Pos.CENTER_LEFT);
        v.getChildren().addAll(zeile, text);
    }

    /** @param immer Betrieb, dessen "immer gelieferte" Eingänge als ∞ erscheinen (sonst null) */
    private static Node bestaende(Anlage a, Map<String, Integer> kap, Map<String, Double> bestand, Betrieb immer,
            Runnable geaendert) {
        FlowPane f = new FlowPane(8, 6);
        for (Map.Entry<String, Integer> e : kap.entrySet()) {
            boolean unbegrenzt = immer != null && a.immerGeliefert(immer, e.getKey());
            VBox zelle = new VBox(2, Symbole.gut(a.gut(e.getKey()), 22),
                    new Label(unbegrenzt ? "∞ von außerhalb geliefert"
                            : Symbole.zahl(bestand.getOrDefault(e.getKey(), 0.0)) + "/" + e.getValue()));
            zelle.setAlignment(Pos.CENTER);
            f.getChildren().add(nullMenue(zelle, () -> bestand.remove(e.getKey()), geaendert));
        }
        if (kap.isEmpty()) {
            f.getChildren().add(new Label("–"));
        }
        return f;
    }

    private static Node mengeSymbol(Gut g, int menge) {
        HBox h = new HBox(2, Symbole.gut(g, 18), new Label(String.valueOf(menge)));
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    // ------------------------------------------------------------------ Station

    private static void station(VBox v, Station s, Anlage a, Runnable geaendert) {
        richtungsStatistik(v, s);
        stationGleise(v, s, a, geaendert);
    }

    /** Statistik je Fahrtrichtung, heute (Anwender 09.11.2026): eingestiegene Reisende und geladene Güter. */
    private static void richtungsStatistik(VBox v, Station s) {
        java.util.List<String> zeilen = new java.util.ArrayList<>();
        int tag = s.getStatistik().getReihen().values().stream().mapToInt(m -> m.isEmpty() ? 0 : m.lastKey()).max().orElse(0);
        for (var e : s.getStatistik().getReihen().entrySet()) {
            String k = e.getKey();
            boolean pers = k.startsWith(com.example.itrain_logistik.kern.EchtzeitKern.RICHTUNG_REISENDE);
            boolean gut = k.startsWith(com.example.itrain_logistik.kern.EchtzeitKern.RICHTUNG_GUETER);
            if (!pers && !gut) {
                continue;
            }
            double heute = e.getValue().getOrDefault(tag, 0.0);
            double gesamt = e.getValue().values().stream().mapToDouble(Double::doubleValue).sum();
            String richtung = k.substring((pers ? com.example.itrain_logistik.kern.EchtzeitKern.RICHTUNG_REISENDE
                    : com.example.itrain_logistik.kern.EchtzeitKern.RICHTUNG_GUETER).length());
            zeilen.add((pers ? "Reisende" : "Güter") + " Richtung " + richtung + ": heute " + Symbole.zahl(heute)
                    + ", " + com.example.itrain_logistik.kern.Statistik.TAGE + " Tage " + Symbole.zahl(gesamt));
        }
        if (!zeilen.isEmpty()) {
            abschnitt(v, "Je Fahrtrichtung");
            for (String z : zeilen) {
                v.getChildren().add(new Label(z));
            }
        }
    }

    private static void stationGleise(VBox v, Station s, Anlage a, Runnable geaendert) {
        for (Gleis g : s.getGleise()) {
            abschnitt(v, g.getAnzeigeName() + "  ·  " + g.getArt().getLabel()
                    + (g.getBlockName() != null ? "  ·  Block " + g.getBlockName() : "  ·  kein Block"));
            if (g.getArt() == GleisArt.BAHNSTEIG) {
                for (String r : g.warteschlangen()) {
                    double[] w = g.wartendeIn(r);
                    v.getChildren().add(nullMenue(new HBox(6, Symbole.person(16), new Label(
                            (r.isEmpty() ? "Wartend" : "Richtung " + r) + ": " + Symbole.zahl(w[0]) + " (1. Kl.) + "
                                    + Symbole.zahl(w[1]) + " (2. Kl.) / " + g.getKapazitaet())),
                            () -> {
                                w[0] = 0;
                                w[1] = 0;
                            }, geaendert));
                }
            } else {
                TilePane raster = new TilePane(6, 6);
                raster.setPrefColumns(6);
                java.util.Set<String> gueter = new java.util.LinkedHashSet<>();
                if (g.getGueterAuswahl().isEmpty()) {
                    gueter.addAll(g.getAusgang().keySet());
                    gueter.addAll(g.getEingang().keySet());
                } else {
                    gueter.addAll(g.getGueterAuswahl());
                }
                for (String gutId : gueter) {
                    String text = "▼" + Symbole.zahl(g.getAusgang().getOrDefault(gutId, 0.0)) + " ▲"
                            + Symbole.zahl(g.getEingang().getOrDefault(gutId, 0.0));
                    VBox zelle = new VBox(2, Symbole.gut(a.gut(gutId), 24), new Label(text));
                    zelle.setAlignment(Pos.CENTER);
                    zelle.setPadding(new Insets(3));
                    zelle.setStyle("-fx-background-color: #3b5b8c; -fx-background-radius: 4;");
                    ((Label) zelle.getChildren().get(1)).setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
                    raster.getChildren().add(nullMenue(zelle, () -> {
                        g.getAusgang().remove(gutId);
                        g.getEingang().remove(gutId);
                    }, geaendert));
                }
                if (gueter.isEmpty()) {
                    raster.getChildren().add(new Label("keine Güter am Gleis"));
                }
                v.getChildren().add(raster);
                Label legende = new Label("▼ wartet auf Abholung · ▲ abgeladen, geht an die Abnehmer · Kapazität "
                        + g.getKapazitaet() + " je Gut"
                        + (g.isEntladenErzwingen() ? " · Entladen erzwingen" : ""));
                legende.setWrapText(true);
                v.getChildren().add(legende);
            }
            if (g.getZugImGleis() != null) {
                ZugZustand z = a.getZuege().get(g.getZugImGleis());
                if (z != null) {
                    v.getChildren().add(zugInfo(z, a));
                }
            }
        }
        if (s.getGleise().isEmpty()) {
            v.getChildren().add(new Label("Noch keine Gleise. Über \"Bearbeiten…\" Gleise mit iTrain-Blöcken anlegen."));
        }
        abschnitt(v, "Einzugsbereich");
        StringBuilder sb = new StringBuilder();
        for (AnlagenObjekt o : a.einzugsbereich(s)) {
            sb.append(o).append('\n');
        }
        v.getChildren().add(new Label(sb.length() == 0 ? "–" : sb.toString().trim()));
    }

    static Node zugInfo(ZugZustand z, Anlage a) {
        VBox box = new VBox(3);
        box.setPadding(new Insets(4));
        box.setStyle("-fx-border-color: #f5c518; -fx-border-radius: 4;");
        box.getChildren().add(new Label("Zug " + z.getName() + (z.getLinie() != null ? "  ·  Linie " + z.getLinie() : "")));
        FlowPane f = new FlowPane(4, 4);
        z.ladungJeGut().forEach((g, m) -> f.getChildren().add(Symbole.gutBadge(a.gut(g), Symbole.zahl(m),
                Symbole.Pfeil.KEINER)));
        if (z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() > 0) {
            double[] p = z.passagiereSumme();
            f.getChildren().add(Symbole.personBadge(Symbole.zahl(p[0] + p[1]) + "/"
                    + Symbole.zahl(z.getSitzplaetzeErste() + z.getSitzplaetzeZweite()), "Passagiere"));
        }
        if (f.getChildren().isEmpty()) {
            f.getChildren().add(new Label("leer"));
        }
        box.getChildren().add(f);
        return box;
    }

    // ------------------------------------------------------------------ Lager, Ort, Uebergabe

    private static void lager(VBox v, Lager l, Anlage a, EchtzeitKern kern, Runnable geaendert) {
        abschnitt(v, "Bestände  " + Symbole.zahl(l.summe()) + "/" + l.getKapazitaet());
        FlowPane f = new FlowPane(8, 6);
        l.getBestand().forEach((g, m) -> {
            VBox zelle = new VBox(2, Symbole.gut(a.gut(g), 22), new Label(Symbole.zahl(m)));
            zelle.setAlignment(Pos.CENTER);
            f.getChildren().add(nullMenue(zelle, () -> l.getBestand().remove(g), geaendert));
        });
        if (l.getBestand().isEmpty()) {
            f.getChildren().add(new Label("leer"));
        }
        v.getChildren().add(f);
        abschnitt(v, "Stationen");
        v.getChildren().add(new Label(stationen(a, l)));
        abschnitt(v, "Statistik je Modelltag");
        v.getChildren().add(diagramm(l.getStatistik(), kern.getTag(), "Eingehend", "Ausgehend"));
    }

    private static void ort(VBox v, Ort o, Anlage a, EchtzeitKern kern, Runnable geaendert) {
        Label kopf = new Label("Einwohner " + o.getEinwohner() + "  ·  Abreisende " + Symbole.zahl(o.getPassagiereProStunde())
                + " M/h  ·  Ankommende " + (o.isAnkommendUnbegrenzt() ? "unbegrenzt"
                : Symbole.zahl(o.getAnkommendProStunde()) + " M/h") + "  ·  1. Klasse "
                + Symbole.zahl(o.getAnteilErsteKlasse() * 100) + " %");
        kopf.setTooltip(new javafx.scene.control.Tooltip("M/h = je Modellstunde"));
        v.getChildren().add(kopf);
        abschnitt(v, "Reisende");
        v.getChildren().add(nullMenue(new HBox(6, Symbole.person(16), new Label("Wollen verreisen: "
                + Symbole.zahl(Math.floor(o.getReisende() + 1e-9)) + " (gehen alle "
                + Symbole.zahl(Ort.SAMMEL_SEKUNDEN / 60) + " Modellminuten zum Bahnhof)")),
                () -> o.setReisende(0), geaendert));
        if (o.lieferung(com.example.itrain_logistik.kern.Tf3Daten.POST) > 0
                || o.lieferung(com.example.itrain_logistik.kern.Tf3Daten.EXPRESSGUT) > 0) {
            v.getChildren().add(new Label("Liefert Post " + Symbole.zahl(o.lieferung(com.example.itrain_logistik.kern.Tf3Daten.POST))
                    + " M/h, Expressgut " + Symbole.zahl(o.lieferung(com.example.itrain_logistik.kern.Tf3Daten.EXPRESSGUT)) + " M/h"));
        }
        v.getChildren().add(new Label("Ankommend in dieser Modellstunde: " + Symbole.zahl(o.getAnkommendDieseStunde())));
        abschnitt(v, "Güterbedarf (offen / je Modellstunde)");
        FlowPane f = new FlowPane(8, 6);
        o.getBedarf().forEach((g, m) -> {
            VBox zelle = new VBox(2, Symbole.gut(a.gut(g), 22), new Label(
                    Symbole.zahl(o.getOffenerBedarf().getOrDefault(g, 0.0)) + " / " + ObjektDialoge.zahl(m)));
            zelle.setAlignment(Pos.CENTER);
            f.getChildren().add(nullMenue(zelle, () -> o.getOffenerBedarf().put(g, 0.0), geaendert));
        });
        if (o.getBedarf().isEmpty()) {
            f.getChildren().add(new Label("kein Bedarf eingetragen"));
        }
        v.getChildren().add(f);
        com.example.itrain_logistik.kern.Bewertung.Ergebnis bw = com.example.itrain_logistik.kern.Bewertung.ort(a, o);
        bewertungTabelle(v, a, bw, "Zufriedenheit");
        abschnitt(v, "Statistik je Modelltag");
        v.getChildren().add(diagramm(o.getStatistik(), kern.getTag(), "Abreisend", "Ankommend", "Versorgung"));
        bewertungStatistik(v, o, bw, kern, "Zufriedenheit");
    }

    private static void uebergabe(VBox v, Uebergabe u, Anlage a, EchtzeitKern kern) {
        abschnitt(v, "Liefert unbegrenzt");
        FlowPane f = new FlowPane(4, 4);
        for (String g : u.getAngebot()) {
            f.getChildren().add(Symbole.gutBadge(a.gut(g), a.gutName(g), Symbole.Pfeil.KEINER));
        }
        v.getChildren().add(f);
        abschnitt(v, "Nimmt ab");
        v.getChildren().add(new Label(u.isAnnahmeAlles() ? "alle Güter" : String.join(", ",
                u.getAnnahme().stream().map(a::gutName).toList())));
        abschnitt(v, "Statistik je Modelltag");
        v.getChildren().add(diagramm(u.getStatistik(), kern.getTag(), "Geliefert", "Abgenommen"));
    }

    // ------------------------------------------------------------------ Hilfen

    private static String stationen(Anlage a, AnlagenObjekt o) {
        List<Station> l = a.stationenFuer(o.getId());
        if (l.isEmpty()) {
            return "Keine Station – im Editor per Rechtsklick „Mit Station verbinden…“ zuordnen.";
        }
        return String.join(", ", l.stream().map(Station::getAnzeigeName).toList());
    }

    private static void abschnitt(VBox v, String titel) {
        Label l = new Label(titel);
        l.setFont(Font.font("System", FontWeight.BOLD, 13));
        v.getChildren().addAll(new Separator(), l);
    }

    /** Balken je Modelltag fuer die genannten Reihen (letzte 10 Tage bis heute). */
    private static Node diagramm(Statistik st, int heute, String... reihen) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setAnimated(false);
        chart.setPrefHeight(200);
        chart.setLegendVisible(true);
        for (String reihe : reihen) {
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(reihe);
            for (int tag = Math.max(0, heute - 9); tag <= heute; tag++) {
                s.getData().add(new XYChart.Data<>("Tag " + (tag + 1), Math.round(st.wert(reihe, tag))));
            }
            chart.getData().add(s);
        }
        return chart;
    }
}
