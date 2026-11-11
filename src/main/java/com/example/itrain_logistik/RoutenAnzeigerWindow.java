package com.example.itrain_logistik;

import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

/**
 * Routen-Anzeiger (Anwender 10.11.2026), angelehnt an die Zugverfolgung („Traffic-Control“) von iTrain: ein Zug
 * steht in der Mitte, dahinter läuft das Gleis mit dem vorherigen, dem aktuellen und dem nächsten Block vorbei.
 * Über dem Gleis stehen Bremsstrecke (gelber Balken), Haltepunkt (Stopp-Schild) und das Signal am Blockende;
 * unter dem Zug „Blocklänge &gt; Position“ in mm. Alle Werte kommen laufend über XTC ({@link XtcVerbindung.ZugInfo}).
 *
 * <p>Gezeichnet wird ein Platzhalter-Zug nach Art des Programmsymbols: Personenzug, Güterzug, Triebwagen oder eine
 * allein fahrende Lokomotive (Dampf, Diesel, Elektro, Batterie/Wasserstoff). Das Fenster hat keinen Besitzer und
 * lässt sich auf jeden Monitor ziehen.
 */
public final class RoutenAnzeigerWindow {

    /** Was hinter der Lok hängt bzw. ob der Zug ein Triebwagen ist. */
    public enum Art { LOK, TRIEBWAGEN, PERSONEN, GUETER }

    /** Antrieb der Lok für das Bild. */
    public enum Lok { DAMPF, DIESEL, ELEKTRO, BATTERIE }

    /** Ein Fahrzeug im Bild; {@code lok} nur bei {@link Art#LOK}. */
    public record Fahrzeug(Art art, Lok lok) {
    }

    /** Platzhalterbild eines Zugs: die Fahrzeuge in der Reihenfolge der iTrain-Zusammenstellung. */
    public record Bild(List<Fahrzeug> fahrzeuge) {
    }

    /**
     * Vorgänge am haltenden Zug: fertige Zeilen („▲ 5 ausgestiegen“, „Entladen ▲ Kohle“) und ob gerade Reisende
     * bzw. Güter bewegt werden (für Hervorhebung und Geräusch).
     */
    public record Umschlag(List<String> zeilen, boolean personenAktiv, boolean gueterAktiv) {
    }

    /** Woher der Anzeiger seine Daten bekommt (das Hauptfenster). */
    public interface Quelle {
        /** Alle Züge der geöffneten Datei. */
        List<String> zuege();

        /** Laufender Zustand laut XTC, null ohne Verbindung. */
        XtcVerbindung.ZugInfo info(String zug);

        /** Länge eines Blocks in cm, -1 = unbekannt. */
        double blockLaenge(String block);

        /**
         * Der Weg aus dem aktuellen Block zum nächsten: Zielblock und die Weichen dazwischen (leer, wenn die
         * Datei sie nicht kennt); null = unbekannt.
         */
        BlockEntry.Weg weg(String zug);

        /** Weg auf der Seite hinter dem Zug (nach einem Richtungswechsel im Block), null = keiner/unbekannt. */
        BlockEntry.Weg wegZurueck(String zug);

        /**
         * Ob das Gleis auf dieser Seite des Blocks endet (Prellbock): die Datei kennt die Wege der Blöcke, nennt
         * für diese Seite aber keinen Nachbarn. {@code voraus} = in Fahrtrichtung, sonst hinter dem Zug.
         */
        boolean gleisende(String zug, boolean voraus);

        /** Was am haltenden Zug gerade geschieht (Ein-/Aussteigen, Ent-/Beladen), null = nichts. */
        Umschlag umschlag(String zug);

        Bild bild(String zug);
    }

    /** Offene Anzeiger nach Nummer (Anwender 10.11.2026: mehrere Fenster, je eines für einen Zug). */
    private static final java.util.Map<Integer, RoutenAnzeigerWindow> OFFEN = new java.util.TreeMap<>();
    private int nummer;
    private Stage stage;

    private final Quelle quelle;
    private final Canvas flaeche = new Canvas();
    private final ComboBox<String> auswahl = new ComboBox<>();
    private AnimationTimer timer;

    // weich nachgeführte Werte
    private String gezeigterBlock;
    private double gezeigtePosition;
    private double gezeigterMassstab;
    private long letzterTakt;
    /** Laufweg in cm über alle Blöcke, nur für den Rauch der Dampflok. */
    private double laufweg;
    private boolean bremsteZuletzt;
    /** Angenommene Länge einer Weiche ohne Längenangabe in iTrain (cm). */
    private static final double WEICHE_ERSATZ = 11.0;
    /** Fahrt über die Weichen hinter dem Blockende, in cm (hochgerechnet). */
    private double ueber;
    /** cm/s je km/h, aus den Positionsmeldungen gelernt; Startwert für Spur N (1:160). */
    private double cmJeKmh = 0.17;
    private double letztePos;
    private long letztePosZeit;
    private double letzteLuecke;
    private String letzteLueckeNamen = "";
    private double lueckeVor;
    private String lueckeVorNamen = "";
    /** Letztes Fahrt-Signal in diesem Block (null = noch keines) und Beginn eines gemeldeten Halts. */
    private String freigabe;
    private long stopSeit;
    private String richtungImBlock;
    /** Ansicht gespiegelt: der Zug fährt nach einem Richtungswechsel nach links; bleibt bis zum nächsten Wechsel. */
    private boolean spiegel;
    /** In diesem Block wurde gewendet: hinter dem Zug liegt die andere Blockseite, nicht der Herkunftsblock. */
    private boolean gewendet;
    private boolean richtungswechsel;
    private long personenZuletzt;
    private long gueterZuletzt;
    /** Gelernter Beginn des Bremsens je „Block|Richtung“ in cm ab Einfahrt. */
    private static final java.util.Map<String, Double> BREMSBEGINN = new java.util.HashMap<>();

    private RoutenAnzeigerWindow(Quelle quelle) {
        this.quelle = quelle;
    }

    /**
     * Öffnet einen weiteren Routen-Anzeiger. Jedes Fenster merkt sich Lage und Größe unter seiner Nummer und
     * beginnt mit einem Zug, den noch kein anderer Anzeiger zeigt.
     */
    public static void show(Quelle quelle) {
        RoutenAnzeigerWindow w = new RoutenAnzeigerWindow(quelle);
        int nr = 1;
        while (OFFEN.containsKey(nr)) {
            nr++;
        }
        w.nummer = nr;
        Stage stage = new Stage();
        w.stage = stage;
        stage.setTitle("Routen-Anzeiger");
        OFFEN.put(nr, w);
        Fenster.szene(stage, w.aufbau(), 900, 320);
        WindowState.apply(stage, nr == 1 ? "routenanzeiger" : "routenanzeiger." + nr, 900, 320);
        if (nr > 1 && OFFEN.get(nr - 1) != null && Math.abs(stage.getX() - OFFEN.get(nr - 1).stage.getX()) < 2
                && Math.abs(stage.getY() - OFFEN.get(nr - 1).stage.getY()) < 2) {
            stage.setX(stage.getX() + 30);
            stage.setY(stage.getY() + 30); // nicht genau über dem vorigen Fenster öffnen
        }
        stage.setMinWidth(480);
        stage.setMinHeight(220);
        stage.setOnHidden(e -> {
            if (w.timer != null) {
                w.timer.stop();
            }
            Geraeusche.vorgangStopp(w.nummer);
            OFFEN.remove(w.nummer, w);
        });
        if (w.auswahl.getValue() != null) {
            stage.setTitle(Uebersetzung.tr("Routen-Anzeiger") + " – " + w.auswahl.getValue());
        }
        stage.show();
    }

    /** Schließt alle Anzeiger (Programmende, Datei geschlossen). */
    public static void schliesse() {
        for (RoutenAnzeigerWindow w : new java.util.ArrayList<>(OFFEN.values())) {
            w.stage.hide();
        }
    }

    public static boolean istOffen() {
        return !OFFEN.isEmpty();
    }

    private BorderPane aufbau() {
        auswahl.setPrefWidth(260);
        auswahl.setOnShowing(e -> zuegeFuellen());
        zuegeFuellen();
        auswahl.valueProperty().addListener((o, a, n) -> {
            gezeigterBlock = null;
            if (stage != null) {
                stage.setTitle(Uebersetzung.tr("Routen-Anzeiger") + (n == null ? "" : " – " + n));
            }
            personenZuletzt = 0;
            spiegel = false;
            gueterZuletzt = 0;
        });
        Label l = new Label("Zug:");
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        // Ton für Ein-/Aussteigen und Verladen, im Fenster schaltbar (Anwender 10.11.2026); gilt für alle Anzeiger
        javafx.scene.control.ToggleButton tonKnopf = new javafx.scene.control.ToggleButton();
        tonKnopf.setSelected(AppSettings.getInstance().isRoutenTon());
        Runnable tonText = () -> tonKnopf.setText(tonKnopf.isSelected() ? "🔊 " + Uebersetzung.tr("Ton an") : "🔇 " + Uebersetzung.tr("Ton aus"));
        tonText.run();
        tonKnopf.setOnAction(e -> {
            AppSettings.getInstance().setRoutenTon(tonKnopf.isSelected());
            tonText.run();
        });
        tonKnopf.setTooltip(Symbole.tip("Geräusche beim Ein-/Aussteigen und Verladen in den Routen-Anzeigern "
                + "ein- oder ausschalten"));
        HBox kopf = new HBox(8, l, auswahl, platz, tonKnopf);
        kopf.setAlignment(Pos.CENTER_LEFT);
        kopf.setPadding(new Insets(6, 10, 6, 10));

        Pane halter = new Pane(flaeche);
        flaeche.widthProperty().bind(halter.widthProperty());
        flaeche.heightProperty().bind(halter.heightProperty());
        halter.setMinSize(0, 0);

        BorderPane root = new BorderPane(halter);
        root.setTop(kopf);

        timer = new AnimationTimer() {
            @Override
            public void handle(long jetzt) {
                double dt = letzterTakt == 0 ? 0 : Math.min(0.1, (jetzt - letzterTakt) / 1e9);
                letzterTakt = jetzt;
                zeichne(dt);
            }
        };
        timer.start();
        return root;
    }

    private void zuegeFuellen() {
        List<String> z = quelle.zuege();
        if (!auswahl.getItems().equals(z)) {
            String bisher = auswahl.getValue();
            auswahl.getItems().setAll(z);
            if (bisher != null && z.contains(bisher)) {
                auswahl.setValue(bisher);
            } else if (!z.isEmpty()) {
                // bevorzugt ein Zug, der gerade in einem Block steht und den noch kein anderer Anzeiger zeigt
                java.util.Set<String> vergeben = new java.util.HashSet<>();
                for (RoutenAnzeigerWindow o : OFFEN.values()) {
                    if (o != this && o.auswahl.getValue() != null) {
                        vergeben.add(o.auswahl.getValue());
                    }
                }
                String erster = null;
                for (String n : z) {
                    XtcVerbindung.ZugInfo i = quelle.info(n);
                    if (i != null && i.getBlock() != null && !vergeben.contains(n)) {
                        erster = n;
                        break;
                    }
                }
                if (erster == null) {
                    erster = z.get(0);
                    for (String n : z) {
                        if (!vergeben.contains(n)) {
                            erster = n;
                            break;
                        }
                    }
                }
                auswahl.setValue(erster);
            }
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    private static final Color HIMMEL = Color.web("#eef3f8");
    private static final Color BODEN = Color.web("#dfe6d4");
    private static final Color SCHOTTER = Color.web("#b9b4a8");
    private static final Color SCHWELLE = Color.web("#6b5a47");
    private static final Color SCHIENE = Color.web("#4a4f57");
    private static final Color BAND_AKTUELL = Color.web("#fff8d6");
    private static final Color BAND_NACHBAR = Color.web("#f3f5f7");
    private static final Color BAND_WEICHEN = Color.web("#dfe7f1");
    private static final Color TEXT = Color.web("#1f2328");
    private static final Color TEXT_LEISE = Color.web("#5b6470");
    private static final Color GRAU = Color.web("#b4bac2");
    private static final Color GELB = Color.web("#f2c200");
    private static final Color ROT = Color.web("#d42a2a");
    private static final Color GRUEN = Color.web("#2e9e3e");

    private void zeichne(double dt) {
        double w = flaeche.getWidth();
        double h = flaeche.getHeight();
        if (w < 10 || h < 10) {
            return;
        }
        GraphicsContext g = flaeche.getGraphicsContext2D();
        g.setFill(HIMMEL);
        g.fillRect(0, 0, w, h);

        String zug = auswahl.getValue();
        XtcVerbindung.ZugInfo i = zug == null ? null : quelle.info(zug);
        double gleisY = Math.round(Math.min(h * 0.66, h - 86));
        double zugH = Math.max(30, Math.min(64, h * 0.2));

        if (zug == null) {
            hinweis(g, w, h, "Keine Datei geöffnet oder kein Zug vorhanden.");
            return;
        }
        if (i == null || !i.isVerbunden()) {
            gleis(g, w, gleisY, 0, 4);
            zug(g, quelle.bild(zug), w * 0.55, gleisY, Math.min(w * 0.4, 260), zugH, 0);
            hinweis(g, w, h, "Keine Online-Daten: Dieser Zug ist nicht über XTC mit iTrain verbunden.");
            return;
        }
        String block = i.getBlock();
        if (block == null) {
            gleis(g, w, gleisY, 0, 4);
            zug(g, quelle.bild(zug), w * 0.55, gleisY, Math.min(w * 0.4, 260), zugH, 0);
            hinweis(g, w, h, "Der Zug steht in iTrain in keinem Block.");
            return;
        }

        long jetzt = System.currentTimeMillis();
        double laenge = i.getBlockLaenge() > 0 ? i.getBlockLaenge() : 100;
        // Ohne gemeldete Position (z. B. stehender Zug, nur über die Abfrage bekannt) stand der Zug am Blockanfang und
        // damit sichtbar im vorherigen Block (Anwender 11.11.2026: „aktuell CS_B1 mit Prellbock, Zug im vorher“).
        // Ein stehender Zug steht dann am Haltepunkt bzw. am Blockende.
        double pos = i.getPosition() >= 0 ? Math.min(laenge, i.getPosition())
                : i.getTempo() > 0.5 ? 0 : i.getStop() >= 0 ? Math.min(laenge, i.getStop()) : laenge;

        // Weg zum nächsten Block: Weichenstraße mit Länge aus der .tcdz
        BlockEntry.Weg weg = quelle.weg(zug);
        double luecke = weg != null && !weg.weichen().isEmpty() ? weg.laenge(WEICHE_ERSATZ) : 0;

        if (!block.equals(gezeigterBlock)) {
            // neuer Block: die Zugspitze kommt aus der Weichenstraße (negativ = noch davor), der Maßstab gleitet nach
            boolean ersterBlock = gezeigterBlock == null;
            gezeigterBlock = block;
            gezeigtePosition = ersterBlock ? pos : Math.min(pos, Math.max(-letzteLuecke, ueber - letzteLuecke));
            lueckeVor = ersterBlock ? 0 : letzteLuecke;
            lueckeVorNamen = ersterBlock ? "" : letzteLueckeNamen;
            ueber = 0;
            freigabe = null;
            stopSeit = 0;
            letztePosZeit = 0;
            richtungImBlock = i.getRichtung();
            richtungswechsel = false;
            gewendet = false;
        } else if (!i.getRichtung().isEmpty() && !i.getRichtung().equals(richtungImBlock)) {
            // Richtungswechsel im Block (Anwender 10.11.2026): iTrain zählt die Position jetzt vom anderen Ende
            richtungswechsel = richtungImBlock != null && !richtungImBlock.isEmpty();
            if (richtungswechsel) {
                // die Strecke bleibt liegen, der Zug fährt zurück: Ansicht spiegeln statt die Blöcke zu tauschen
                // Der Zug fährt in der Anzeige immer nach rechts (Anwender 10.11.2026: sonst liefe er nach einem
                // Wechsel auf Rundstrecken dauerhaft nach links). Gewendet wird das Bild des Zugs: die Lok schiebt.
                gewendet = true;
            }
            richtungImBlock = i.getRichtung();
            gezeigtePosition = pos;
            ueber = 0;
            freigabe = null;
            stopSeit = 0;
            letztePosZeit = 0;
        }
        if (richtungswechsel && i.getTempo() > 0.5) {
            richtungswechsel = false; // der Zug fährt in die neue Richtung ab
        }
        letzteLuecke = luecke;
        letzteLueckeNamen = weg == null ? "" : namen(weg);

        // Verhältnis cm/s je km/h aus den Positionsmeldungen lernen (hängt vom Maßstab der Anlage ab)
        if (i.getPositionZeit() != letztePosZeit) {
            double dtp = (i.getPositionZeit() - letztePosZeit) / 1000.0;
            if (letztePosZeit > 0 && dtp > 0.03 && dtp < 1.5 && pos > letztePos && pos < laenge - 0.3 && i.getTempo() > 3) {
                double r = (pos - letztePos) / dtp / i.getTempo();
                if (r > 0.02 && r < 1.5) {
                    cmJeKmh += (r - cmJeKmh) * 0.12;
                }
            }
            letztePosZeit = i.getPositionZeit();
            letztePos = pos;
        }
        // Am Blockende bleibt die Position in iTrain stehen, bis der nächste Block meldet. Solange der Zug fährt,
        // läuft die Anzeige mit dem zuletzt gemessenen Tempo über die Weichen weiter (Anwender 10.11.2026).
        boolean amEnde = i.getPosition() >= laenge - 0.3;
        if (amEnde && i.getTempo() > 0.5 && !i.isWartet()) {
            ueber = Math.min(luecke + 1.5, ueber + cmJeKmh * i.getTempo() * dt);
        } else if (!amEnde) {
            ueber = 0;
        }
        double zielPos = pos + ueber;
        // der aktuelle Block mit der Weichenstraße dahinter passt immer ganz ins Bild: Zugspitze bei 55 %
        double xr = Math.round(w * 0.55); // rechtes Ende des Zugs auf dem Bildschirm, immer gleich
        double zielMass = spiegel
                ? Math.max(0.5, (xr - 24) / (laenge + luecke + Math.max(0, i.getZugLaenge())))
                : Math.max(0.5, (w * 0.45 - 24) / (laenge + luecke));
        if (gezeigterMassstab <= 0) {
            gezeigterMassstab = zielMass;
        }
        double vorher = gezeigtePosition;
        gezeigtePosition += (zielPos - gezeigtePosition) * Math.min(1, dt * 6);
        gezeigterMassstab += (zielMass - gezeigterMassstab) * Math.min(1, dt * 3);
        laufweg += Math.abs(gezeigtePosition - vorher);
        double s = gezeigterMassstab;
        // Zuglänge im selben Maßstab wie der Block (Anwender 11.11.2026); ohne Angabe von iTrain eine feste Breite
        double zugW = i.getZugLaenge() > 0 ? Math.max(24, Math.min(xr - 8, i.getZugLaenge() * s))
                : Math.max(70, Math.min(w * 0.5, 180));
        // Zugspitze in Fahrtrichtung. Gespiegelt (nach einem Richtungswechsel) fährt der Zug nach links: die Welt
        // wird gespiegelt gezeichnet, der Zug selbst bleibt an seinem Platz und behält seine Lage (Lok rechts).
        double x0 = spiegel ? w - xr + zugW : xr;

        // Was hinter dem Zug liegt: sonst der Block, aus dem er kam; nach dem Wenden die andere Seite des Blocks
        boolean endeVoraus = weg == null && quelle.gleisende(zug, true);
        boolean endeHinten = false;
        if (gewendet) {
            BlockEntry.Weg zurueck = quelle.wegZurueck(zug);
            lueckeVor = zurueck != null && !zurueck.weichen().isEmpty() ? zurueck.laenge(WEICHE_ERSATZ) : 0;
            lueckeVorNamen = zurueck == null ? "" : namen(zurueck);
            endeHinten = zurueck == null && quelle.gleisende(zug, false);
        }

        String vor = gewendet ? (quelle.wegZurueck(zug) == null ? null : quelle.wegZurueck(zug).ziel())
                : i.getVorherBlock();
        String nach = weg == null ? null : weg.ziel();
        double lVor = vor != null && quelle.blockLaenge(vor) > 0 ? quelle.blockLaenge(vor) : laenge;
        double lNach = nach != null && quelle.blockLaenge(nach) > 0 ? quelle.blockLaenge(nach) : laenge;
        double xA = x0 - gezeigtePosition * s;
        double xE = xA + laenge * s;
        double xV = xA - lueckeVor * s;   // Ende des vorherigen Blocks
        double xN = xE + luecke * s;      // Anfang des nächsten Blocks

        g.save();
        if (spiegel) {
            g.translate(w, 0);
            g.scale(-1, 1);
        }
        // Bänder: Blöcke und Weichenstraßen
        double bandOben = 6;
        double bandH = gleisY + 14 - bandOben;
        g.setFill(BAND_NACHBAR);
        if (!endeHinten) {
            g.fillRect(xV - lVor * s, bandOben, lVor * s, bandH);
        }
        if (!endeVoraus) {
            g.fillRect(xN, bandOben, lNach * s, bandH);
        }
        g.setFill(BAND_WEICHEN);
        g.fillRect(xV, bandOben, lueckeVor * s, bandH);
        g.fillRect(xE, bandOben, luecke * s, bandH);
        g.setFill(BAND_AKTUELL);
        g.fillRect(xA, bandOben, laenge * s, bandH);
        g.setFill(BODEN);
        g.fillRect(0, gleisY + 14, w, h - gleisY - 14);

        gleis(g, w, gleisY, xA, s);

        // Blockgrenzen
        g.setStroke(TEXT_LEISE);
        g.setLineWidth(1.2);
        g.setLineDashes(5, 4);
        for (double x : new double[] {endeHinten ? xA : xV - lVor * s, xV, xA, xE, xN, endeVoraus ? xE : xN + lNach * s}) {
            g.strokeLine(x, bandOben, x, gleisY + 10);
        }
        g.setLineDashes();

        // Namen
        if (!endeHinten) {
            blockName(g, w, xV - lVor * s, xV, bandOben, gewendet ? "dahinter" : "vorher", vor == null ? "–" : vor, false);
        }
        blockName(g, w, xA, xE, bandOben, "aktuell", block, true);
        if (!endeVoraus) {
            blockName(g, w, xN, xN + lNach * s, bandOben, "danach", nach == null ? "?" : nach, false);
        }
        weichenName(g, w, xV, xA, bandOben, lueckeVorNamen);
        weichenName(g, w, xE, xN, bandOben, letzteLueckeNamen);

        // Signal am Blockende. Fährt der Zug durch, fällt das Signal hinter der Zugspitze auf Halt, während die
        // Position noch am Blockende steht – das sähe aus wie eine Fahrt über Rot. Deshalb (Anwender 10.11.2026):
        // war im Block schon Fahrt gezeigt und der Zug rollt, bleibt die Fahrt stehen bis zum nächsten Block;
        // ein Halt gleich nach der Einfahrt gilt erst nach 1,5 s (iTrain meldet dort oft kurz Halt, dann Fahrt).
        String roh = i.getSignal() == null ? "" : i.getSignal();
        String anzeige;
        if (!"stop".equals(roh)) {
            if (!roh.isEmpty()) {
                freigabe = roh;
            }
            stopSeit = 0;
            anzeige = roh;
        } else {
            if (stopSeit == 0) {
                stopSeit = jetzt;
            }
            boolean steht = i.getTempo() <= 0.5 || i.isWartet();
            if (steht) {
                anzeige = "stop";
            } else if (freigabe != null) {
                anzeige = freigabe;
            } else {
                anzeige = jetzt - stopSeit > 1500 ? "stop" : "";
            }
        }

        // Marken über dem Zug: Bremsstrecke, Haltepunkt, Signal
        boolean haelt = "stop".equals(anzeige) || i.isWartet();
        boolean bremst = haelt || "restricted".equals(anzeige);
        double markenY = gleisY - zugH - 26;
        double stop = i.getStop();
        // Bremsstrecke: immer gezeichnet. Meldet iTrain keinen Bremspunkt (brake nur, wenn er im Block gesetzt ist),
        // beginnt der Balken dort, wo der Zug in diesem Block zuletzt zu bremsen begann (Wunschtempo unter dem
        // Tempo); beim ersten Mal am Blockanfang.
        boolean bremstJetzt = i.getTempo() > 0.5 && i.getWunschTempo() < i.getTempo() - 0.5;
        String merk = block + "|" + i.getRichtung();
        if (bremstJetzt && !bremsteZuletzt && i.getPosition() >= 0) {
            BREMSBEGINN.put(merk, i.getPosition());
        }
        bremsteZuletzt = bremstJetzt;
        bremst |= bremstJetzt;
        {
            double bis = stop >= 0 ? stop : laenge;
            double beginn = i.getBremse() >= 0 ? i.getBremse() : BREMSBEGINN.getOrDefault(merk, 0.0);
            double von = Math.min(beginn, bis);
            if (bremst) {
                // während des Bremsens schrumpft der Balken: nur noch der Rest von der Zugspitze bis zum Halt
                von = Math.max(von, Math.min(bis, gezeigtePosition));
            }
            double breite = (bis - von) * s;
            if (breite > 1.5) {
                g.setFill(bremst ? GELB : GRAU);
                g.fillRoundRect(xA + von * s, markenY + 10, breite, 7, 4, 4);
                g.setStroke(Color.color(0, 0, 0, 0.25));
                g.setLineWidth(1);
                g.strokeRoundRect(xA + von * s, markenY + 10, breite, 7, 4, 4);
            }
        }
        if (richtungswechsel) {
            double a = Math.max(xA, 4);
            double e = Math.min(xE, w - 4);
            if (e - a > 60) {
                double by = bandOben + 36;
                g.setFill(GRUEN);
                g.fillRoundRect(a + 6, by, e - a - 12, 18, 9, 9);
                g.setFill(Color.WHITE);
                g.setFont(Font.font("System", FontWeight.BOLD, 12));
                g.setTextAlign(TextAlignment.CENTER);
                g.setTextBaseline(VPos.CENTER);
                txt(g, kuerze(g, "⇄  " + Uebersetzung.tr("Richtungswechsel"), e - a - 24), (a + e) / 2, by + 9);
            }
        }
        if (stop >= 0) {
            stoppSchild(g, xA + stop * s, markenY, gleisY, haelt);
        }
        if (endeVoraus) {
            prellbock(g, xE, gleisY, true);
        } else {
            signal(g, xE, markenY - 6, gleisY, anzeige);
        }
        if (endeHinten) {
            prellbock(g, xA, gleisY, false);
        }
        g.restore();

        // Zug: immer an derselben Stelle und in derselben Lage; die Fahrtrichtung zeigt die laufende Strecke
        zug(g, quelle.bild(zug), xr, gleisY, zugW, zugH, i.getTempo() > 0.5 ? laufweg : -1,
                "backward".equals(i.getFahrtrichtung()));

        // Bremst / Halt erwarten (Anwender 11.11.2026): deutliche Schilder unten rechts
        java.util.List<String[]> schilder = new java.util.ArrayList<>();
        if (bremstJetzt) {
            schilder.add(new String[] {"▼ " + Uebersetzung.tr("Bremst"), "#f2c200", "#000000"});
        }
        String erwartet = i.getSignalErwartet() == null ? "" : i.getSignalErwartet();
        if ("stop".equals(erwartet) && !"stop".equals(anzeige)) {
            schilder.add(new String[] {"◉ " + Uebersetzung.tr("Halt erwarten"), "#f2c200", "#000000"});
        } else if ("restricted".equals(erwartet) && !"restricted".equals(anzeige)) {
            schilder.add(new String[] {"◉ " + Uebersetzung.tr("Langsamfahrt erwarten"), "#ffe9a8", "#000000"});
        }
        double sx = w - 10;
        g.setFont(Font.font("System", FontWeight.BOLD, 14));
        g.setTextBaseline(VPos.CENTER);
        g.setTextAlign(TextAlignment.CENTER);
        for (String[] sc : schilder) {
            javafx.scene.text.Text m = new javafx.scene.text.Text(sc[0]);
            m.setFont(g.getFont());
            double sb = m.getLayoutBounds().getWidth() + 22;
            g.setFill(Color.web(sc[1]));
            g.fillRoundRect(sx - sb, h - 34, sb, 26, 8, 8);
            g.setStroke(Color.color(0, 0, 0, 0.45));
            g.setLineWidth(1.2);
            g.strokeRoundRect(sx - sb, h - 34, sb, 26, 8, 8);
            g.setFill(Color.web(sc[2]));
            g.fillText(sc[0], sx - sb / 2, h - 21);
            sx -= sb + 8;
        }

        // Position unter dem Zug
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFill(TEXT);
        g.setFont(Font.font("System", FontWeight.BOLD, 14));
        String posText = i.getPosition() >= 0
                ? Math.round(Math.max(0, Math.min(laenge, gezeigtePosition)) * 10) + " mm" : "–";
        g.fillText(Uebersetzung.tr("{0} mm > Position {1}", Math.round(laenge * 10), posText)
                + (ueber > 0.5 && luecke > 0 ? "  ·  " + Uebersetzung.tr("Weichen") : ""), xr - zugW / 2, gleisY + 20);
        g.setFont(Font.font("System", 12));
        g.setFill(TEXT_LEISE);
        g.fillText(zeile(i), xr - zugW / 2, gleisY + 40);

        // Ein-/Aussteigen, Ent-/Beladen am haltenden Zug, mit Geräusch solange es läuft
        Umschlag u = quelle.umschlag(zug);
        if (u != null && u.personenAktiv()) {
            personenZuletzt = jetzt;
        }
        if (u != null && u.gueterAktiv()) {
            gueterZuletzt = jetzt;
        }
        boolean pAn = jetzt - personenZuletzt < 3000;
        boolean gAn = jetzt - gueterZuletzt < 3000;
        if (AppSettings.getInstance().isRoutenTon()) {
            Geraeusche.vorgang(Geraeusche.EINSTEIGEN, nummer, pAn);
            Geraeusche.vorgang(Geraeusche.BELADEN, nummer, gAn);
        } else {
            Geraeusche.vorgangStopp(nummer); // Ton aus: sofort still
        }
        if (u != null && !u.zeilen().isEmpty()) {
            String text = String.join("   ·   ", u.zeilen());
            g.setFont(Font.font("System", FontWeight.BOLD, 12.5));
            javafx.scene.text.Text mass = new javafx.scene.text.Text(text);
            mass.setFont(g.getFont());
            double tb = Math.min(w - 20, mass.getLayoutBounds().getWidth() + 20);
            double tx = Math.max(10 + tb / 2, Math.min(w - 10 - tb / 2, xr - zugW / 2));
            double ty = gleisY + 58;
            boolean an = pAn || gAn;
            g.setFill(an ? Color.web("#ff8c00") : Color.web("#c9ced6"));
            g.fillRoundRect(tx - tb / 2, ty, tb, 20, 10, 10);
            g.setFill(an ? Color.WHITE : TEXT);
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(kuerze(g, text, tb - 12), tx, ty + 10);
        }
    }

    /** Zweite Zeile unter dem Zug: Tempo, Halt, Route und Ziel. */
    private static String zeile(XtcVerbindung.ZugInfo i) {
        StringBuilder b = new StringBuilder();
        b.append(Math.round(i.getTempo())).append(" km/h");
        if (i.getZugLaenge() > 0) {
            b.append("  ·  ").append(Uebersetzung.tr("Zuglänge {0} mm", Math.round(i.getZugLaenge() * 10)));
        }
        if (i.isWartet() && i.getWartenGesamt() > 0) {
            long rest = Math.max(0, i.getWartenGesamt() - i.getWartenVorbei()) / 1000;
            b.append("  ·  ").append(Uebersetzung.tr("Halt noch {0} s", rest));
        }
        if (!i.getRoute().isEmpty()) {
            b.append("  ·  ").append(Uebersetzung.tr("Route")).append(": ").append(i.getRoute());
        }
        if (!i.getZiel().isEmpty()) {
            b.append("  ·  ").append(Uebersetzung.tr("Ziel")).append(": ").append(i.getZiel());
        }
        return b.toString();
    }

    private static void hinweis(GraphicsContext g, double w, double h, String text) {
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFill(TEXT_LEISE);
        g.setFont(Font.font("System", 13));
        g.fillText(Uebersetzung.tr(text), w / 2, 14);
    }

    /** Gleis über die ganze Breite; die Schwellen sitzen an festen Stellen der Strecke und laufen so mit. */
    private static void gleis(GraphicsContext g, double w, double y, double ursprung, double s) {
        g.setFill(SCHOTTER);
        g.fillRect(0, y + 2, w, 12);
        double schritt = Math.max(9, 2.2 * s);
        double start = ursprung % schritt;
        if (start > 0) {
            start -= schritt;
        }
        g.setFill(SCHWELLE);
        for (double x = start; x < w; x += schritt) {
            g.fillRect(x, y + 3, Math.max(3, schritt * 0.32), 8);
        }
        g.setFill(SCHIENE);
        g.fillRect(0, y, w, 3);
        // Masten am Streckenrand, alle paar Schwellen: machen die Bewegung sichtbar
        double mast = schritt * 9;
        double m0 = ursprung % mast;
        if (m0 > 0) {
            m0 -= mast;
        }
        g.setStroke(Color.web("#9aa3ad"));
        g.setLineWidth(1.5);
        for (double x = m0; x < w; x += mast) {
            g.strokeLine(x, y + 16, x, y + 30);
            g.strokeLine(x - 4, y + 19, x + 4, y + 19);
        }
    }

    private static void blockName(GraphicsContext g, double w, double von, double bis, double oben, String rolle,
            String name, boolean aktuell) {
        double a = Math.max(von, 0);
        double e = Math.min(bis, w);
        if (e - a < 40) {
            return;
        }
        double mitte = (a + e) / 2;
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFont(Font.font("System", 10));
        g.setFill(TEXT_LEISE);
        txt(g, Uebersetzung.tr(rolle), mitte, oben + 2);
        g.setFont(Font.font("System", aktuell ? FontWeight.BOLD : FontWeight.NORMAL, aktuell ? 14 : 12.5));
        g.setFill(aktuell ? TEXT : TEXT_LEISE);
        txt(g, kuerze(g, name, e - a - 12), mitte, oben + 15);
    }

    private static String namen(BlockEntry.Weg weg) {
        StringBuilder b = new StringBuilder();
        for (BlockEntry.Weiche x : weg.weichen()) {
            b.append(b.length() == 0 ? "" : ", ").append(x.name());
        }
        return b.toString();
    }

    /** Beschriftung einer Weichenstraße zwischen zwei Blöcken. */
    private static void weichenName(GraphicsContext g, double w, double von, double bis, double oben, String namen) {
        double a = Math.max(von, 0);
        double e = Math.min(bis, w);
        if (e - a < 26 || namen == null || namen.isEmpty()) {
            return;
        }
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.TOP);
        g.setFont(Font.font("System", 10));
        g.setFill(TEXT_LEISE);
        txt(g, kuerze(g, Uebersetzung.tr("Weichen"), e - a - 4), (a + e) / 2, oben + 2);
        g.setFont(Font.font("System", 10.5));
        txt(g, kuerze(g, namen, e - a - 6), (a + e) / 2, oben + 16);
    }

    private static String kuerze(GraphicsContext g, String text, double breite) {
        javafx.scene.text.Text t = new javafx.scene.text.Text(text);
        t.setFont(g.getFont());
        if (t.getLayoutBounds().getWidth() <= breite) {
            return text;
        }
        String s = text;
        while (s.length() > 2) {
            s = s.substring(0, s.length() - 1);
            t.setText(s + "…");
            if (t.getLayoutBounds().getWidth() <= breite) {
                break;
            }
        }
        return s + "…";
    }

    /** Text an der Stelle x der (vielleicht gespiegelten) Strecke, selbst aber nie gespiegelt. */
    private static void txt(GraphicsContext g, String text, double x, double y) {
        javafx.scene.transform.Affine t = g.getTransform();
        if (t.getMxx() < 0) {
            g.save();
            g.setTransform(new javafx.scene.transform.Affine());
            g.fillText(text, t.getMxx() * x + t.getTx(), y);
            g.restore();
        } else {
            g.fillText(text, x, y);
        }
    }

    /** Prellbock am Gleisende bei x; {@code rechts} = das Gleis endet in Zeichenrichtung rechts davon. */
    private static void prellbock(GraphicsContext g, double x, double gleisY, boolean rechts) {
        double r = rechts ? 1 : -1;
        // dahinter kein Gleis: Schotter und Schienen überdecken
        g.setFill(BODEN);
        if (rechts) {
            g.fillRect(x + 12, gleisY - 1, 4000, 16);
        } else {
            g.fillRect(x - 4012, gleisY - 1, 4000, 16);
        }
        g.setStroke(Color.web("#3d434b"));
        g.setLineWidth(3);
        g.strokeLine(x + r * 2, gleisY, x + r * 2, gleisY - 20);
        g.strokeLine(x + r * 12, gleisY, x + r * 2, gleisY - 16);
        g.setFill(ROT);
        g.fillRect(x - 3, gleisY - 26, 10 * 1.0, 9);
        g.setFill(Color.WHITE);
        g.fillRect(x - 0.5, gleisY - 26, 3, 9);
        g.setFill(Color.web("#3d434b"));
        g.fillRect(x - 3 + (rechts ? 0 : 0), gleisY - 17.5, 10, 1.5);
    }

    /** Achteckiges Stopp-Schild am Haltepunkt: rot, wenn der Zug dort hält, sonst grau. */
    private static void stoppSchild(GraphicsContext g, double x, double y, double gleisY, boolean benutzt) {
        g.setStroke(Color.web("#7a828c"));
        g.setLineWidth(1.5);
        g.strokeLine(x, y + 8, x, gleisY);
        double r = 11;
        double[] xs = new double[8];
        double[] ys = new double[8];
        for (int k = 0; k < 8; k++) {
            double w = Math.PI / 8 + k * Math.PI / 4;
            xs[k] = x + r * Math.cos(w);
            ys[k] = y + r * Math.sin(w);
        }
        g.setFill(benutzt ? ROT : GRAU);
        g.fillPolygon(xs, ys, 8);
        g.setStroke(Color.WHITE);
        g.setLineWidth(1.4);
        g.strokePolygon(xs, ys, 8);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.BOLD, 7.5));
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        txt(g, "STOP", x, y);
    }

    /** Lichtsignal am Blockende: grün = Fahrt, gelb = Langsamfahrt, rot = Halt, grau = unbekannt. */
    private static void signal(GraphicsContext g, double x, double y, double gleisY, String stand) {
        g.setStroke(Color.web("#3d434b"));
        g.setLineWidth(2);
        g.strokeLine(x, y + 30, x, gleisY);
        g.setFill(Color.web("#23272e"));
        g.fillRoundRect(x - 7, y - 4, 14, 36, 6, 6);
        Color aus = Color.web("#4b525c");
        lampe(g, x, y + 3, "stop".equals(stand) ? ROT : aus);
        lampe(g, x, y + 14, "restricted".equals(stand) ? GELB : aus);
        lampe(g, x, y + 25, "go".equals(stand) ? GRUEN : aus);
    }

    private static void lampe(GraphicsContext g, double x, double y, Color c) {
        g.setFill(c);
        g.fillOval(x - 4, y - 4, 8, 8);
    }

    // ------------------------------------------------------------------ Platzhalter-Züge

    /**
     * Zeichnet den Zug mit der Spitze bei {@code x} (Fahrt nach rechts), auf der Schiene {@code y}, {@code w} breit.
     * {@code weg} ≥ 0 = der Zug fährt (Rauch der Dampflok zieht nach hinten).
     */
    private static void zug(GraphicsContext g, Bild b, double x, double y, double w, double h, double weg) {
        zug(g, b, x, y, w, h, weg, false);
    }

    /**
     * Fahrzeuge in der Reihenfolge der Zusammenstellung (Anwender 10.11.2026). Vorwärts steht das erste Fahrzeug an
     * der Spitze (rechts) und alle schauen nach rechts; {@code rueckwaerts} (iTrain-Fahrtrichtung „backward“, z. B.
     * nach einem Richtungswechsel ohne Umsetzen) läuft das letzte Fahrzeug voraus, die Lok schiebt und schaut nach
     * hinten.
     */
    private static void zug(GraphicsContext g, Bild b, double x, double y, double w, double h, double weg,
            boolean rueckwaerts) {
        List<Fahrzeug> fz = b == null || b.fahrzeuge().isEmpty() ? List.of(new Fahrzeug(Art.LOK, Lok.DIESEL))
                : b.fahrzeuge();
        // Breiten: Lok bzw. Triebwagen etwas länger als ein Wagen; alles zusammen genau w
        double[] anteil = new double[fz.size()];
        double summe = 0;
        for (int k = 0; k < fz.size(); k++) {
            anteil[k] = fz.get(k).art() == Art.TRIEBWAGEN ? 2.4 : fz.get(k).art() == Art.LOK ? 1.15 : 1.0;
            summe += anteil[k];
        }
        double luecke = 3;
        double nutz = w - luecke * (fz.size() - 1);
        if (fz.size() == 1) {
            nutz = Math.min(w, h * (fz.get(0).art() == Art.TRIEBWAGEN ? 6 : 2.6));
        }
        double rechts = x;
        for (int n = 0; n < fz.size(); n++) {
            int k = rueckwaerts ? fz.size() - 1 - n : n;
            Fahrzeug f = fz.get(k);
            double fw = Math.max(8, nutz * anteil[k] / summe);
            double fx = rechts - fw;
            g.save();
            if (rueckwaerts) {
                g.translate(2 * fx + fw, 0);
                g.scale(-1, 1);
            }
            switch (f.art()) {
                case LOK -> lok(g, f.lok(), fx, y, fw, h, rueckwaerts ? -1 : weg);
                case TRIEBWAGEN -> triebwagen(g, fx, y, fw, h);
                case PERSONEN -> personenwagen(g, fx, y, fw, h);
                default -> gueterwagen(g, fx, y, fw, h, k);
            }
            g.restore();
            if (n < fz.size() - 1) {
                g.setStroke(SCHIENE);
                g.setLineWidth(2);
                g.strokeLine(fx - luecke, y - h * 0.2, fx, y - h * 0.2);
            }
            rechts = fx - luecke;
        }
    }

    private static void raeder(GraphicsContext g, double x, double y, double w, double r, int n, Color felge) {
        for (int k = 0; k < n; k++) {
            double cx = n == 1 ? x + w / 2 : x + r + 3 + k * (w - 2 * r - 6) / (n - 1);
            g.setFill(Color.web("#1f2328"));
            g.fillOval(cx - r, y - 2 * r, 2 * r, 2 * r);
            g.setFill(felge);
            g.fillOval(cx - r * 0.55, y - r * 1.55, r * 1.1, r * 1.1);
        }
    }

    /** Personenwagen wie im Programmsymbol: grün mit gelben Fenstern. */
    private static void personenwagen(GraphicsContext g, double x, double y, double w, double h) {
        double r = h * 0.13;
        double unten = y - r * 1.5;
        g.setFill(Color.web("#2e8b3d"));
        g.fillRoundRect(x, y - h, w, h - r * 1.5, 8, 8);
        g.setFill(Color.web("#1f6a2c"));
        g.fillRoundRect(x, y - h, w, h * 0.16, 8, 8);
        int n = Math.max(2, (int) (w / (h * 0.42)));
        double fb = w / (n * 1.5 + 0.5);
        g.setFill(Color.web("#f7c948"));
        for (int k = 0; k < n; k++) {
            g.fillRoundRect(x + fb * 0.5 + k * fb * 1.5, y - h * 0.74, fb, h * 0.3, 3, 3);
        }
        g.setFill(Color.web("#17501f"));
        g.fillRect(x + 2, unten - 3, w - 4, 3);
        raeder(g, x, y, w, r, 4, Color.web("#9aa3ad"));
    }

    private static void gueterwagen(GraphicsContext g, double x, double y, double w, double h, int nr) {
        double r = h * 0.13;
        double hoehe = h * 0.72;
        boolean offen = nr % 3 == 1;
        g.setFill(offen ? Color.web("#7d8792") : Color.web("#9a5b2e"));
        g.fillRoundRect(x, y - r * 1.5 - (offen ? hoehe * 0.6 : hoehe), w, offen ? hoehe * 0.6 : hoehe, 4, 4);
        g.setStroke(Color.color(0, 0, 0, 0.28));
        g.setLineWidth(1);
        int n = Math.max(3, (int) (w / 14));
        for (int k = 1; k < n; k++) {
            double lx = x + k * w / n;
            g.strokeLine(lx, y - r * 1.5 - (offen ? hoehe * 0.6 : hoehe) + 2, lx, y - r * 1.5 - 2);
        }
        if (offen) {
            g.setFill(Color.web("#3b3f45"));
            g.fillArc(x + 4, y - r * 1.5 - hoehe * 0.85, w - 8, hoehe * 0.5, 0, 180, javafx.scene.shape.ArcType.CHORD);
        }
        raeder(g, x, y, w, r, 2, Color.web("#9aa3ad"));
    }

    private static void triebwagen(GraphicsContext g, double x, double y, double w, double h) {
        double r = h * 0.13;
        double unten = y - r * 1.5;
        double oben = y - h;
        double nase = Math.min(h * 0.7, w * 0.18);
        double[] xs = {x + nase * 0.6, x + w - nase, x + w, x + w, x, x};
        double[] ys = {oben, oben, oben + (unten - oben) * 0.55, unten, unten, oben + (unten - oben) * 0.5};
        g.setFill(Color.web("#f3ead2"));
        g.fillPolygon(xs, ys, 6);
        g.setFill(Color.web("#b3262b"));
        double band = oben + (unten - oben) * 0.58;
        g.fillPolygon(new double[] {x, x + w, x + w, x}, new double[] {band, band, unten, unten}, 4);
        int n = Math.max(3, (int) ((w - 2 * nase) / (h * 0.45)));
        double fb = (w - 2 * nase) / (n * 1.4 + 0.4);
        g.setFill(Color.web("#3d5a80"));
        for (int k = 0; k < n; k++) {
            g.fillRoundRect(x + nase + fb * 0.4 + k * fb * 1.4, oben + (unten - oben) * 0.2, fb, (unten - oben) * 0.3, 3, 3);
        }
        // Führerstandsfenster vorn
        g.fillPolygon(new double[] {x + w - nase * 0.9, x + w - nase * 0.35, x + w - nase * 0.05, x + w - nase * 0.9},
                new double[] {oben + (unten - oben) * 0.2, oben + (unten - oben) * 0.2, oben + (unten - oben) * 0.5,
                        oben + (unten - oben) * 0.5}, 4);
        g.setFill(Color.web("#fff4b8"));
        g.fillOval(x + w - 6, band + 2, 5, 5);
        raeder(g, x, y, w, r, 6, Color.web("#9aa3ad"));
    }

    private static void lok(GraphicsContext g, Lok art, double x, double y, double w, double h, double weg) {
        double r = h * 0.13;
        double unten = y - r * 1.5;
        double oben = y - h;
        switch (art) {
            case DAMPF -> {
                double rr = h * 0.2;
                double u = y - rr * 1.3;
                // Führerhaus hinten, Kessel, Schornstein vorn
                g.setFill(Color.web("#22262b"));
                g.fillRoundRect(x, oben + h * 0.08, w * 0.3, u - oben - h * 0.08, 5, 5);
                g.fillRoundRect(x + w * 0.27, oben + h * 0.3, w * 0.7, u - oben - h * 0.34, 14, 14);
                g.fillRect(x + w * 0.78, oben + h * 0.08, w * 0.07, h * 0.26);
                g.fillRoundRect(x + w * 0.5, oben + h * 0.2, w * 0.1, h * 0.14, 6, 6);
                g.setFill(Color.web("#f7c948"));
                g.fillRoundRect(x + w * 0.06, oben + h * 0.18, w * 0.16, h * 0.2, 3, 3);
                g.setFill(Color.web("#b3262b"));
                g.fillRect(x + 2, u - 4, w - 2, 4);
                g.fillPolygon(new double[] {x + w, x + w + 6, x + w}, new double[] {u - 8, u, u}, 3);
                raeder(g, x + w * 0.02, y, w * 0.96, rr, 4, Color.web("#c8323a"));
                g.setStroke(Color.web("#9aa3ad"));
                g.setLineWidth(2);
                g.strokeLine(x + w * 0.2, y - rr, x + w * 0.85, y - rr);
                if (weg >= 0) {
                    for (int k = 0; k < 3; k++) {
                        double p = ((weg * 0.25 + k * 0.33) % 1 + 1) % 1;
                        double gr = 6 + p * 12;
                        g.setFill(Color.color(0.6, 0.63, 0.67, 0.55 * (1 - p)));
                        g.fillOval(x + w * 0.815 - p * 34 - gr / 2, oben + h * 0.02 - p * 16 - gr / 2, gr, gr);
                    }
                }
            }
            case DIESEL -> {
                g.setFill(Color.web("#c8452b"));
                g.fillRoundRect(x, oben + h * 0.3, w, unten - oben - h * 0.3, 6, 6);
                g.fillRoundRect(x + w * 0.36, oben + h * 0.06, w * 0.3, h * 0.4, 5, 5);
                g.setFill(Color.web("#f3ead2"));
                g.fillRect(x, oben + h * 0.52, w, h * 0.07);
                g.setFill(Color.web("#3d5a80"));
                g.fillRoundRect(x + w * 0.4, oben + h * 0.12, w * 0.22, h * 0.18, 3, 3);
                g.setFill(Color.web("#2a2e34"));
                g.fillRect(x + 3, unten - 3, w - 6, 3);
                g.setFill(Color.web("#fff4b8"));
                g.fillOval(x + w - 6, oben + h * 0.38, 5, 5);
                raeder(g, x, y, w, r, 4, Color.web("#9aa3ad"));
            }
            case ELEKTRO -> {
                g.setFill(Color.web("#2c5e9e"));
                g.fillRoundRect(x, oben + h * 0.2, w, unten - oben - h * 0.2, 10, 10);
                g.setFill(Color.web("#f3ead2"));
                g.fillRect(x, oben + h * 0.56, w, h * 0.06);
                g.setFill(Color.web("#cfe2f7"));
                g.fillRoundRect(x + w * 0.06, oben + h * 0.28, w * 0.16, h * 0.2, 3, 3);
                g.fillRoundRect(x + w * 0.78, oben + h * 0.28, w * 0.16, h * 0.2, 3, 3);
                // Stromabnehmer
                g.setStroke(Color.web("#3d434b"));
                g.setLineWidth(1.6);
                double px = x + w * 0.5;
                g.strokeLine(px - w * 0.12, oben + h * 0.2, px, oben + h * 0.06);
                g.strokeLine(px, oben + h * 0.06, px + w * 0.12, oben + h * 0.2);
                g.strokeLine(px - w * 0.14, oben + h * 0.05, px + w * 0.14, oben + h * 0.05);
                g.setFill(Color.web("#fff4b8"));
                g.fillOval(x + w - 6, oben + h * 0.5, 5, 5);
                raeder(g, x, y, w, r, 4, Color.web("#9aa3ad"));
            }
            case BATTERIE -> {
                g.setFill(Color.web("#1f8a8a"));
                g.fillRoundRect(x, oben + h * 0.22, w, unten - oben - h * 0.22, 10, 10);
                g.setFill(Color.web("#cfe2f7"));
                g.fillRoundRect(x + w * 0.74, oben + h * 0.3, w * 0.2, h * 0.2, 3, 3);
                // Batteriesymbol
                double bx = x + w * 0.16;
                double by = oben + h * 0.36;
                double bw = w * 0.34;
                double bh = h * 0.26;
                g.setFill(Color.WHITE);
                g.fillRoundRect(bx, by, bw, bh, 4, 4);
                g.fillRect(bx + bw, by + bh * 0.3, 3, bh * 0.4);
                g.setFill(Color.web("#2e9e3e"));
                g.fillRect(bx + 2, by + 2, bw * 0.65, bh - 4);
                g.setFill(Color.web("#fff4b8"));
                g.fillOval(x + w - 6, oben + h * 0.52, 5, 5);
                raeder(g, x, y, w, r, 4, Color.web("#9aa3ad"));
            }
            default -> {
            }
        }
    }
}
