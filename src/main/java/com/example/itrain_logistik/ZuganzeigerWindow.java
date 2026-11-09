package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Fahrthinweise;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Zuganzeiger (Anwender 16.10.2026): frei positionierbares Fenster im Stil einer DB-Abfahrtstafel (blau, weiße
 * Schrift), das die nächsten nötigen Fahrten als Laufschrift zeigt ({@link Fahrthinweise}). Jede Zeile hat links
 * ein helles Kästchen mit dem Gut (bzw. "Pers") und die Menge, rechts läuft der Hinweis nach links aus dem Anzeiger
 * heraus; danach übernimmt die Zeile den nächsten Hinweis. Vorgabe 4 Zeilen; wird das Fenster größer, kommen
 * Zeilen hinzu. Der Zoom-Regler unten vergrößert Schrift und Zeilen. Das Fenster hat keinen Besitzer, lässt sich
 * also auf jeden Bildschirm ziehen; Lage, Größe und Zoom werden gemerkt.
 * <p>
 * Zwei Anzeiger (Anwender 22.10.2026): einer für den Personenverkehr, einer für den Güterverkehr (etwas helleres
 * Blau). Laufschrift nur, wenn die Zeile nicht in den Anzeiger passt; sonst steht sie still und wechselt nach
 * einer Weile, falls es mehr Hinweise als Zeilen gibt. Verschwindet ein angezeigter Hinweis, weil die Fahrt
 * erledigt ist, wird das helle Kästchen kurz grün, bevor die Zeile den nächsten Hinweis übernimmt.
 */
public final class ZuganzeigerWindow {

    static final String BLAU = "#16307a";
    static final String BLAU_DUNKEL = "#0d1d4f";
    static final String TAFEL_HELL = "#e8ebd6";
    static final String ERLEDIGT_GRUEN = "#5fd36f";
    private static final double SCHRIFT = 22;
    private static final double TEMPO = 70; // Pixel je Sekunde bei Zoom 1 und Tempo 1
    /** Stehende Zeile: so viele Sekunden (bei Tempo 1), bevor sie wechselt, wenn mehr Hinweise als Zeilen da sind. */
    private static final double STEHZEIT = 8;
    private static final double GRUEN_SEKUNDEN = 1.5;

    /** Personen- oder Güterverkehr: Titel, Fensterschlüssel und Farbton. */
    public enum Art {
        PERSONEN("Zuganzeiger Personenverkehr", "Nächste Fahrten", "Personenverkehr · Next trips",
                "zuganzeiger", "#16307a", "#0d1d4f", "#1b388c", "#12286a", "#2c4699"),
        GUETER("Zuganzeiger Güterverkehr", "Nächste Fahrten", "Güterverkehr · Next trips",
                "zuganzeiger.gueter", "#22428f", "#152a63", "#2a4ba2", "#1b367e", "#3a58ab");

        final String fenster;
        final String titel;
        final String titelEn;
        final String schluessel;
        final String blau;
        final String dunkel;
        final String oben;
        final String unten;
        final String linie;

        Art(String fenster, String titel, String titelEn, String schluessel, String blau, String dunkel, String oben,
                String unten, String linie) {
            this.fenster = fenster;
            this.titel = titel;
            this.titelEn = titelEn;
            this.schluessel = schluessel;
            this.blau = blau;
            this.dunkel = dunkel;
            this.oben = oben;
            this.unten = unten;
            this.linie = linie;
        }

        boolean passt(Fahrthinweise.Hinweis h) {
            return (this == PERSONEN) == (h.gutId() == null);
        }
    }

    /** Offene Anzeiger: Schlüssel Art bzw. Art + "|" + Station-Id (Anzeiger je Bahnhof, Anwender 09.11.2026). */
    private static final java.util.Map<String, ZuganzeigerWindow> OFFEN = new java.util.LinkedHashMap<>();

    /** Bahnhofsverbund (Ids) des Anzeigers je Bahnhof; null = alle Bahnhöfe. */
    private Supplier<java.util.Set<String>> verbund;

    private final Art art;
    private Stage stage;
    private final Supplier<List<Fahrthinweise.Hinweis>> quelle;
    private final VBox zeilenBox = new VBox();
    private final List<Zeile> zeilen = new ArrayList<>();
    private final Label uhr = new Label();
    private final Label titel = new Label();
    private final Label titelEn = new Label();
    private final Label kopfGut = new Label("Gut");
    private final Label kopfMenge = new Label("Menge");
    private final Label kopfHinweis = new Label("Hinweis  ·  Ziel  ·  Abholung  ·  Zug");
    private final Label anzahl = new Label();
    private List<Fahrthinweise.Hinweis> hinweise = List.of();
    private int naechster;
    private double zoom;
    /** Geeignete Züge im Text nennen (Statusleiste, Anwender 27.10.2026). */
    private boolean zuegeVorschlagen;
    private double tempo;
    private long letzteAbfrage;
    private AnimationTimer timer;

    private final java.util.function.Function<String, com.example.itrain_logistik.kern.Gut> gutVon;

    private ZuganzeigerWindow(Art art, Supplier<List<Fahrthinweise.Hinweis>> quelle,
            java.util.function.Function<String, com.example.itrain_logistik.kern.Gut> gutVon) {
        this.art = art;
        this.zuegeVorschlagen = AppSettings.getInstance().isAnzeigerZuegeVorschlagen(art.name());
        this.quelle = quelle;
        this.gutVon = gutVon;
        titel.setText(art.titel);
        titelEn.setText(art.titelEn);
    }

    /**
     * "Alles auf 0 setzen" im Hauptprogramm: der Anzeiger leert sofort alle Zeilen (auch laufende Texte) und fragt
     * die Hinweise neu ab.
     */
    public static void leeren() {
        for (ZuganzeigerWindow inhalt : List.copyOf(OFFEN.values())) {
            inhalt.hinweise = List.of();
            inhalt.naechster = 0;
            inhalt.letzteAbfrage = 0;
            inhalt.anzahl.setText("0 Hinweise");
            for (Zeile z : inhalt.zeilen) {
                z.gruen = 0;
                z.naechsterHinweis();
            }
        }
    }

    public static void show(Art art, Supplier<List<Fahrthinweise.Hinweis>> quelle,
            java.util.function.Function<String, com.example.itrain_logistik.kern.Gut> gutVon) {
        showFuer(art, null, null, null, quelle, gutVon);
    }

    /**
     * Zuganzeiger je Bahnhof (Anwender 09.11.2026): nur Reisende, die an diesem Bahnhof warten, und Güter, die dort
     * abgeholt oder angeliefert werden (Bahnhof samt angedocktem Güterbahnhof). {@code stationId} null = alle.
     */
    public static void showFuer(Art art, String stationId, String stationName, Supplier<java.util.Set<String>> verbund,
            Supplier<List<Fahrthinweise.Hinweis>> quelle,
            java.util.function.Function<String, com.example.itrain_logistik.kern.Gut> gutVon) {
        String schluessel = stationId == null ? art.name() : art.name() + "|" + stationId;
        ZuganzeigerWindow da = OFFEN.get(schluessel);
        if (da != null && da.stage != null && da.stage.isShowing()) {
            da.stage.toFront();
            da.stage.requestFocus();
            return;
        }
        ZuganzeigerWindow inhalt = new ZuganzeigerWindow(art, quelle, gutVon);
        inhalt.verbund = verbund;
        if (stationName != null) {
            inhalt.titel.setText(stationName);
            inhalt.titelEn.setText(art == Art.PERSONEN ? "Personenverkehr · Next trips" : "Güterverkehr · Next trips");
        }
        Stage stage = new Stage();
        inhalt.stage = stage;
        stage.setTitle(art.fenster + (stationName != null ? " – " + stationName : ""));
        stage.setScene(new Scene(inhalt.aufbauen(), 1000, 295));
        WindowState.apply(stage, art.schluessel + (stationId != null ? ".bf." + stationId : ""), 1000, 295);
        stage.setOnHidden(e -> {
            if (inhalt.timer != null) {
                inhalt.timer.stop();
            }
            OFFEN.remove(schluessel, inhalt);
        });
        OFFEN.put(schluessel, inhalt);
        stage.show();
        inhalt.start();
    }

    public static void closeIfOpen() {
        for (ZuganzeigerWindow z : List.copyOf(OFFEN.values())) {
            if (z.stage != null) {
                z.stage.close();
            }
        }
    }

    public static boolean istOffen() {
        return OFFEN.values().stream().anyMatch(z -> z.stage != null && z.stage.isShowing());
    }

    // ------------------------------------------------------------------ Aufbau

    private VBox aufbauen() {
        zoom = AppSettings.getInstance().getAnzeigerZoom();
        farbe(titel, "white");
        titel.setMinWidth(Region.USE_PREF_SIZE);
        farbe(titelEn, "white");
        // erst Hintergrund, dann Schriftfarbe: setStyle ersetzt den ganzen Stil (Anwender 09.11.2026: Uhr dunkel auf dunkel)
        uhr.setStyle("-fx-background-color: " + art.dunkel + "; -fx-padding: 2 10 2 10;");
        farbe(uhr, "white");
        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox kopf = new HBox(14, titel, titelEn, luecke, uhr);
        kopf.setAlignment(Pos.BASELINE_LEFT);
        kopf.setPadding(new Insets(8, 14, 2, 14));

        farbe(kopfGut, "white");
        farbe(kopfMenge, "white");
        kopfMenge.setAlignment(Pos.CENTER_RIGHT);
        farbe(kopfHinweis, "white");
        HBox spalten = new HBox(8, kopfGut, kopfMenge, kopfHinweis);
        spalten.setPadding(new Insets(0, 8, 2, 8));

        zeilenBox.setPadding(new Insets(0, 8, 0, 8));
        zeilenBox.setMinHeight(0);
        Rectangle rahmen = new Rectangle();
        rahmen.widthProperty().bind(zeilenBox.widthProperty());
        rahmen.heightProperty().bind(zeilenBox.heightProperty());
        zeilenBox.setClip(rahmen);
        VBox.setVgrow(zeilenBox, Priority.ALWAYS);
        zeilenBox.heightProperty().addListener((o, a, b) -> zeilenAnpassen());
        zeilenBox.widthProperty().addListener((o, a, b) -> zeilen.forEach(Zeile::breiteGeaendert));

        // Statusleiste (Anwender 16.10.2026): Tempo der Laufschrift und Zoom
        tempo = AppSettings.getInstance().getAnzeigerTempo();
        Slider tempoRegler = new Slider(0.25, 3.0, tempo);
        tempoRegler.setPrefWidth(160);
        tempoRegler.valueProperty().addListener((o, a, b) -> {
            tempo = b.doubleValue();
            AppSettings.getInstance().setAnzeigerTempo(tempo);
        });
        Slider regler = new Slider(0.5, 3.0, zoom);
        regler.setPrefWidth(160);
        regler.valueProperty().addListener((o, a, b) -> {
            zoom = b.doubleValue();
            AppSettings.getInstance().setAnzeigerZoom(zoom);
            schriftAnpassen();
        });
        Label langsamer = statusText("Langsamer");
        Label schneller = statusText("Schneller");
        Label zoomText = statusText("Zoom");
        // Statusleiste: Zugvorschläge an/aus (Anwender 27.10.2026)
        javafx.scene.control.CheckBox vorschlagen = new javafx.scene.control.CheckBox("Geeignete Züge vorschlagen");
        vorschlagen.setSelected(zuegeVorschlagen);
        vorschlagen.setStyle("-fx-text-fill: #c9d3f2;");
        vorschlagen.setMinWidth(Region.USE_PREF_SIZE);
        vorschlagen.selectedProperty().addListener((o, a, b) -> {
            zuegeVorschlagen = b;
            AppSettings.getInstance().setAnzeigerZuegeVorschlagen(art.name(), b);
            for (Zeile z : zeilen) {
                z.textNeu();
            }
        });
        anzahl.setMinWidth(Region.USE_PREF_SIZE);
        farbe(anzahl, "#c9d3f2");
        Region mitte = new Region();
        HBox.setHgrow(mitte, Priority.ALWAYS);
        HBox fuss = new HBox(8, anzahl, new Region() {
            {
                setMinWidth(18);
            }
        }, vorschlagen, mitte, langsamer, tempoRegler, schneller, new Region() {
            {
                setMinWidth(18);
            }
        }, zoomText, regler);
        fuss.setAlignment(Pos.CENTER_LEFT);
        fuss.setPadding(new Insets(4, 12, 4, 12));
        fuss.setStyle("-fx-background-color: " + art.dunkel + "; -fx-border-color: " + art.linie
                + " transparent transparent transparent;"
                + " -fx-border-width: 1 0 0 0;");
        fuss.setMinHeight(Region.USE_PREF_SIZE);

        VBox root = new VBox(kopf, spalten, zeilenBox, fuss);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, " + art.oben + ", " + art.blau + " 30%, "
                + art.unten + ");");
        schriftAnpassen();
        return root;
    }

    private String anzeigeText(Fahrthinweise.Hinweis h) {
        return zuegeVorschlagen ? h.text() : h.textOhneZuege();
    }

    /** Schriftfarbe per Inline-Stil: die Farbschemata (Stylesheets) überschreiben setTextFill. */
    private static void farbe(Label l, String farbe) {
        String st = l.getStyle() == null ? "" : l.getStyle().replaceAll("-fx-text-fill:[^;]*;", "");
        l.setStyle(st + "-fx-text-fill: " + farbe + ";");
    }

    private static Label statusText(String t) {
        Label l = new Label(t);
        l.setMinWidth(Region.USE_PREF_SIZE);
        farbe(l, "#c9d3f2");
        return l;
    }

    private void schriftAnpassen() {
        double s = SCHRIFT * zoom;
        titel.setFont(Font.font("Arial", FontWeight.BOLD, s * 1.9));
        titelEn.setFont(Font.font("Arial", FontWeight.NORMAL, s * 1.2));
        uhr.setFont(Font.font("Arial", FontWeight.BOLD, s * 1.3));
        for (Label k : List.of(kopfGut, kopfMenge, kopfHinweis)) {
            k.setFont(Font.font("Arial", s * 0.62));
        }
        kopfGut.setMinWidth(s * 7.5);
        kopfGut.setPrefWidth(s * 7.5);
        kopfMenge.setMinWidth(s * 2.6);
        kopfMenge.setPrefWidth(s * 2.6);
        zeilen.forEach(Zeile::schrift);
        zeilenAnpassen();
    }

    private double zeilenHoehe() {
        return SCHRIFT * zoom * 2.1;
    }

    /** So viele Zeilen, wie in die Höhe passen (mindestens eine). */
    private void zeilenAnpassen() {
        double h = zeilenBox.getHeight();
        int soll = h <= 0 ? 4 : Math.max(1, (int) Math.floor(h / (zeilenHoehe() + 2)));
        while (zeilen.size() < soll) {
            Zeile z = new Zeile();
            zeilen.add(z);
            zeilenBox.getChildren().add(z.box);
            z.naechsterHinweis();
        }
        while (zeilen.size() > soll) {
            Zeile z = zeilen.remove(zeilen.size() - 1);
            zeilenBox.getChildren().remove(z.box);
        }
    }

    private void start() {
        hinweise = abfragen();
        zeilen.forEach(Zeile::naechsterHinweis);
        timer = new AnimationTimer() {
            private long vorher;

            @Override
            public void handle(long jetzt) {
                double dt = vorher == 0 ? 0 : (jetzt - vorher) / 1e9;
                vorher = jetzt;
                if (jetzt - letzteAbfrage > 3_000_000_000L) {
                    letzteAbfrage = jetzt;
                    hinweise = abfragen();
                    anzahl.setText(hinweise.size() == 1 ? "1 Hinweis" : hinweise.size() + " Hinweise");
                    for (Zeile z : zeilen) {
                        z.abgleichen();
                    }
                    java.time.LocalTime t = LaufendeUhr.aktuelleZeit();
                    uhr.setText(t == null ? "--:--" : String.format("%02d:%02d", t.getHour(), t.getMinute()));
                }
                for (Zeile z : zeilen) {
                    z.schieben(dt);
                }
            }
        };
        timer.start();
    }

    private List<Fahrthinweise.Hinweis> abfragen() {
        try {
            List<Fahrthinweise.Hinweis> l = quelle.get();
            java.util.Set<String> ids = verbund != null ? verbund.get() : null;
            return l == null ? List.of() : l.stream().filter(art::passt)
                    .filter(h -> ids == null || h.betrifft(ids)).toList();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    /**
     * Erkennt einen Hinweis über Auffrischungen hinweg: Gut (bzw. Reisende), Ziel und Station der Abholung (ohne
     * Gleis, das sich mit den Mengen ändern kann).
     */
    static String schluessel(Fahrthinweise.Hinweis h) {
        String ab = h.abholung() == null ? "" : h.abholung();
        int komma = ab.indexOf(", ");
        return (h.gutId() == null ? "P" : h.gutId()) + "|" + h.ziel() + "|" + (komma > 0 ? ab.substring(0, komma) : ab);
    }

    // ------------------------------------------------------------------ eine Zeile

    private final class Zeile {
        final HBox box = new HBox();
        final Label gut = new Label();
        final StackPane symbol = new StackPane();
        final HBox gutKasten = new HBox(symbol, gut);
        final Label menge = new Label();
        final Pane lauf = new Pane();
        final Text text = new Text();
        final Rectangle clip = new Rectangle();
        double x;

        Zeile() {
            farbe(gut, art.blau);
            gutKasten.setStyle("-fx-background-color: " + TAFEL_HELL + ";");
            gutKasten.setAlignment(Pos.CENTER_LEFT);
            gut.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
            gut.setMinWidth(0);
            farbe(menge, "white");
            menge.setAlignment(Pos.CENTER_RIGHT);
            text.setFill(Color.WHITE);
            lauf.getChildren().add(text);
            lauf.setClip(clip);
            clip.widthProperty().bind(lauf.widthProperty());
            clip.heightProperty().bind(lauf.heightProperty());
            HBox.setHgrow(lauf, Priority.ALWAYS);
            box.getChildren().addAll(gutKasten, menge, lauf);
            box.setAlignment(Pos.CENTER_LEFT);
            box.setSpacing(8);
            box.setStyle("-fx-border-color: transparent transparent " + art.dunkel + " transparent; "
                    + "-fx-border-width: 0 0 2 0;");
            schrift();
        }

        void schrift() {
            double s = SCHRIFT * zoom;
            Font f = Font.font("Arial", FontWeight.BOLD, s);
            gut.setFont(Font.font("Arial", FontWeight.BOLD, s * 0.85));
            menge.setFont(f);
            text.setFont(f);
            double hoehe = zeilenHoehe();
            box.setMinHeight(hoehe);
            box.setPrefHeight(hoehe);
            box.setMaxHeight(hoehe);
            gutKasten.setPadding(new Insets(0, s * 0.3, 0, s * 0.2));
            gutKasten.setSpacing(s * 0.3);
            gutKasten.setMinWidth(s * 7.5);
            gutKasten.setPrefWidth(s * 7.5);
            gutKasten.setMaxWidth(s * 7.5);
            gutKasten.setMinHeight(s * 1.45);
            gutKasten.setMaxHeight(s * 1.45);
            symbolSetzen();
            menge.setMinWidth(s * 2.6);
            menge.setPrefWidth(s * 2.6);
            text.setY(hoehe / 2 + s * 0.35);
        }

        void breiteGeaendert() {
            boolean vorher = laeuft;
            laeuft = zuLang();
            if (laeuft && !vorher) {
                x = 8;
            } else if (!laeuft) {
                x = 8;
            } else if (x > lauf.getWidth()) {
                x = lauf.getWidth();
            }
            text.setX(x);
        }

        /** Passt der Text nicht in die Zeile, läuft er (Anwender 22.10.2026). */
        boolean zuLang() {
            return lauf.getWidth() > 0 && text.getLayoutBounds().getWidth() > lauf.getWidth() - 16;
        }

        /** Hinweis, der gerade in dieser Zeile steht (null = leer). */
        Fahrthinweise.Hinweis aktuell;
        boolean laeuft;
        double steh;
        /** Restzeit der grünen Markierung (erledigt), 0 = keine. */
        double gruen;

        /**
         * Nach dem Abfragen: steht der Hinweis noch in der Liste, Menge und Text auffrischen; fehlt er, ist die Fahrt
         * erledigt – Kästchen kurz grün, danach der nächste Hinweis.
         */
        void abgleichen() {
            if (aktuell == null || gruen > 0) {
                return;
            }
            Fahrthinweise.Hinweis neu = hinweise.stream().filter(h -> schluessel(h).equals(schluessel(aktuell)))
                    .findFirst().orElse(null);
            if (neu == null) {
                gruen = GRUEN_SEKUNDEN;
                gutKasten.setStyle("-fx-background-color: " + ERLEDIGT_GRUEN + ";");
                return;
            }
            if (!Uebersetzung.tr(anzeigeText(neu)).equals(text.getText()) || neu.menge() != aktuell.menge()) {
                aktuell = neu;
                menge.setText(String.valueOf(neu.menge()));
                text.setText(anzeigeText(neu));
                if (!laeuft) {
                    laeuft = zuLang();
                    x = laeuft ? lauf.getWidth() : 8;
                    text.setX(x);
                }
            }
        }

        String gutId;
        boolean personen;

        /** Text nach Umschalten der Zugvorschläge sofort neu setzen. */
        void textNeu() {
            if (aktuell != null && gruen <= 0) {
                text.setText(anzeigeText(aktuell));
                laeuft = zuLang();
                x = laeuft ? Math.max(lauf.getWidth(), 1) : 8;
                text.setX(x);
            }
        }

        /** Farbiges Feld des Guts (wie in der Anlagen-Übersicht) bzw. Personensymbol im hellen Kasten. */
        void symbolSetzen() {
            double g = SCHRIFT * zoom * 1.05;
            com.example.itrain_logistik.kern.Gut gt = gutId != null && gutVon != null ? gutVon.apply(gutId) : null;
            if (gt != null) {
                symbol.getChildren().setAll(Symbole.gut(gt, g));
            } else if (personen) {
                symbol.getChildren().setAll(Symbole.person(g));
            } else {
                symbol.getChildren().clear();
            }
        }

        void naechsterHinweis() {
            gutKasten.setStyle("-fx-background-color: " + TAFEL_HELL + ";");
            steh = 0;
            Fahrthinweise.Hinweis h = waehle();
            aktuell = h;
            if (h == null) {
                gutId = null;
                personen = false;
                symbolSetzen();
                gut.setText("");
                gutKasten.setOpacity(0); // leere Zeile ohne hellen Kasten
                menge.setText("");
                text.setText(zeilen.indexOf(this) == 0 && hinweise.isEmpty() ? "Zurzeit sind keine Fahrten nötig." : "");
            } else {
                gutId = h.gutId();
                personen = h.gutId() == null;
                gutKasten.setOpacity(1);
                symbolSetzen();
                com.example.itrain_logistik.kern.Gut gt = gutId != null && gutVon != null ? gutVon.apply(gutId) : null;
                gut.setText(gt != null ? gt.getName() : personen ? "Reisende" : h.kuerzel());
                menge.setText(String.valueOf(h.menge()));
                text.setText(anzeigeText(h));
            }
            laeuft = zuLang();
            x = laeuft ? Math.max(lauf.getWidth(), 1) : 8;
            text.setX(x);
        }

        /** Nächster Hinweis, der nicht schon in einer anderen Zeile steht (sonst null). */
        private Fahrthinweise.Hinweis waehle() {
            if (hinweise.isEmpty()) {
                return null;
            }
            java.util.Set<String> belegt = new java.util.HashSet<>();
            for (Zeile z : zeilen) {
                if (z != this && z.aktuell != null) {
                    belegt.add(schluessel(z.aktuell));
                }
            }
            for (int i = 0; i < hinweise.size(); i++) {
                Fahrthinweise.Hinweis h = hinweise.get(naechster % hinweise.size());
                naechster = (naechster + 1) % hinweise.size();
                if (!belegt.contains(schluessel(h))) {
                    return h;
                }
            }
            return null;
        }

        void schieben(double dt) {
            if (gruen > 0) {
                gruen -= dt;
                if (gruen <= 0) {
                    gruen = 0;
                    naechsterHinweis();
                }
                return;
            }
            if (aktuell == null) {
                if (!hinweise.isEmpty() && waehleMoeglich()) {
                    naechsterHinweis();
                } else {
                    text.setX(8); // ruhender Hinweis bzw. leere Zeile
                }
                return;
            }
            if (!laeuft) {
                // steht still; wechseln nur, wenn mehr Hinweise als Zeilen da sind
                steh += dt * tempo;
                if (steh > STEHZEIT && hinweise.size() > zeilen.size()) {
                    naechsterHinweis();
                }
                return;
            }
            x -= TEMPO * zoom * tempo * dt;
            text.setX(x);
            if (x + text.getLayoutBounds().getWidth() < 0) {
                naechsterHinweis();
            }
        }

        /** Gibt es einen Hinweis, den keine Zeile zeigt? */
        private boolean waehleMoeglich() {
            java.util.Set<String> belegt = new java.util.HashSet<>();
            for (Zeile z : zeilen) {
                if (z.aktuell != null) {
                    belegt.add(schluessel(z.aktuell));
                }
            }
            return hinweise.stream().anyMatch(h -> !belegt.contains(schluessel(h)));
        }
    }
}
