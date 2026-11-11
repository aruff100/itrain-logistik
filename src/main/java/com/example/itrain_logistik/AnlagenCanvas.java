package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlageXml;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.EchtzeitKern;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Infrastruktur;
import com.example.itrain_logistik.kern.Modul;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Ladeklasse;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Uebergabe;
import com.example.itrain_logistik.kern.Wagenladung;
import com.example.itrain_logistik.kern.ZugZustand;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Zeichenflaeche der Anlage (Konzept Abschnitt 10.1): Symbole fuer Staedte, Doerfer,
 * Stationen, Betriebe, Lager und Uebergaben, Linien vom Station zu ihrem Einzugsbereich und an
 * jedem Symbol Schilder (Badges) mit den aktuellen Werten aus dem Echtzeit-Kern.
 * <p>
 * Zwei Betriebsarten: Im Editor ({@code interaktiv = true}) gibt es links eine Palette zum
 * Anlegen, Symbole lassen sich ziehen, Rechtsklick „Mit Station verbinden…“ nimmt
 * es in den Einzugsbereich auf (nochmal = entfernen), Doppelklick bearbeitet, Rechtsklick
 * bietet Bearbeiten/Farbe/Loeschen. In der Uebersicht des Hauptfensters
 * ({@code interaktiv = false}) oeffnet ein Klick das Detailfenster.
 * {@link #aktualisiere()} baut nur die Schilder neu und wird vom Hauptfenster jede Sekunde
 * aufgerufen.
 */
public final class AnlagenCanvas {

    private static final double ZOOM_MIN = 0.3;
    private static final double ZOOM_MAX = 2.5;

    private final Anlage anlage;
    private final EchtzeitKern kern;
    private final boolean interaktiv;
    private final Consumer<AnlagenObjekt> onOeffnen;
    private final Runnable onGeaendert;
    private final BiConsumer<ObjektArt, Point2D> onNeu;

    private final Pane contentPane = new Pane();
    private final Group linienEbene = new Group();
    private final Map<AnlagenObjekt, VBox> ansichten = new IdentityHashMap<>();
    private final Map<AnlagenObjekt, VBox> schilderBoxen = new IdentityHashMap<>();
    private final Map<AnlagenObjekt, StackPane> iconHalter = new IdentityHashMap<>();
    private final Map<NetzText, VBox> textAnsichten = new IdentityHashMap<>();
    private Slider zoomRegler;
    private Runnable onTempoGeaendert;
    private ScrollPane scroll;
    private Line verbindungsVorschau;
    private int neuZaehler;

    /**
     * @param onNeu nur im Editor: legt ein Objekt der gewaehlten Art an (Dialoge, Vorlagen,
     *              Kreislauf-Assistent) und ruft danach {@link #neuAufbauen()} auf
     */
    public AnlagenCanvas(Anlage anlage, EchtzeitKern kern, boolean interaktiv, Consumer<AnlagenObjekt> onOeffnen,
            Runnable onGeaendert, BiConsumer<ObjektArt, Point2D> onNeu) {
        this(anlage, kern, interaktiv, onOeffnen, onGeaendert, onNeu, null);
    }

    /**
     * Zeichenfläche eines Reiters (Anwender 09.11.2026): {@code bereich} null = erster Reiter (Anlagen-Infrastruktur
     * mit allen Tab- und Modul-Symbolen), sonst der Reiter eines Tabs/Moduls mit dessen Symbol als Anschluss.
     */
    public AnlagenCanvas(Anlage anlage, EchtzeitKern kern, boolean interaktiv, Consumer<AnlagenObjekt> onOeffnen,
            Runnable onGeaendert, BiConsumer<ObjektArt, Point2D> onNeu,
            com.example.itrain_logistik.kern.Bereich bereich) {
        this.bereich = bereich;
        this.anker = bereich != null ? new ReiterAnker(bereich) : null;
        this.anlage = anlage;
        this.kern = kern;
        this.interaktiv = interaktiv;
        this.onOeffnen = onOeffnen;
        this.onGeaendert = onGeaendert;
        this.onNeu = onNeu;
        ALLE.put(this, Boolean.TRUE);
        if (interaktiv) {
            flaecheZiehenEinrichten();
        }
        neuAufbauen();
        if (kern != null) {
            kern.addFlussBeobachter(beobachter);
        }
        lauflicht.start();
    }

    /** Reiter dieser Zeichenfläche (null = erster Reiter) und das Bereichs-Symbol darin. */
    private final com.example.itrain_logistik.kern.Bereich bereich;
    private final ReiterAnker anker;

    public com.example.itrain_logistik.kern.Bereich getBereich() {
        return bereich;
    }

    /** Objekte dieses Reiters: im ersten alles ohne Bereich, sonst das Symbol des Bereichs und sein Inhalt. */
    private List<AnlagenObjekt> sichtbar() {
        if (bereich == null) {
            return anlage.inBereich(null);
        }
        List<AnlagenObjekt> l = new ArrayList<>();
        l.add(anker);
        l.addAll(anlage.inBereich(bereich));
        return l;
    }

    /** Das wirkliche Objekt hinter einem Anker (für Dialoge, Detailfenster). */
    static AnlagenObjekt echt(AnlagenObjekt o) {
        return o instanceof ReiterAnker a ? a.getBereich() : o;
    }

    /** Alle offenen Zeichenflächen, damit geänderte Symbol-Farben sofort überall gelten. */
    private static final Map<AnlagenCanvas, Boolean> ALLE = new java.util.WeakHashMap<>();

    /** Nach einer Änderung der Symbol-Darstellung in den Voreinstellungen. */
    public static void alleNeuZeichnen() {
        for (AnlagenCanvas c : List.copyOf(ALLE.keySet())) {
            c.neuAufbauen();
        }
    }

    /** Schatten für den 3D-Eindruck (Kästen, Verbindungen). */
    private static javafx.scene.effect.DropShadow schatten(double versatz, double radius) {
        javafx.scene.effect.DropShadow d = new javafx.scene.effect.DropShadow(radius, versatz, versatz,
                Color.rgb(0, 0, 0, 0.45));
        return d;
    }

    /** Stylesheet für die Schrift in den Symbol-Kästen (Parent-Stylesheets haben Vorrang vor dem Theme). */
    private static String schriftStylesheet(AnlagenObjekt o) {
        String css = ".label { -fx-text-fill: " + schriftFarbe(o) + "; }";
        return "data:text/css;base64," + java.util.Base64.getEncoder()
                .encodeToString(css.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Beendet Animation und Kern-Beobachtung (vor dem Verwerfen der Zeichenfläche aufrufen). */
    public void dispose() {
        ALLE.remove(this);
        lauflicht.stop();
        if (kern != null) {
            kern.removeFlussBeobachter(beobachter);
        }
    }

    /** Übersicht: wird nach einer Änderung am Tempo-Regler aufgerufen. */
    public void setOnTempoGeaendert(Runnable r) {
        this.onTempoGeaendert = r;
    }

    public BorderPane getView() {
        BorderPane root = new BorderPane();
        if (interaktiv) {
            root.setLeft(palette());
        }
        ScrollPane scroll = new ScrollPane(new Group(contentPane));
        this.scroll = scroll;
        if (interaktiv) {
            richteAblegenEin();
            richteHintergrundMenueEin();
            aktiverEditor = new java.lang.ref.WeakReference<>(this);
        }
        scroll.setPannable(!interaktiv);
        scroll.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                setZoom(contentPane.getScaleX() + (e.getDeltaY() > 0 ? 0.1 : -0.1));
                e.consume();
            }
        });
        root.setCenter(scroll);
        root.setBottom(statusLeiste());
        // Beim Oeffnen steht die Anlagen-Infrastruktur in der Mitte des sichtbaren Bereichs.
        if (interaktiv) {
            javafx.application.Platform.runLater(() -> zentriereAufInfrastruktur(scroll));
        } else {
            // Übersicht: bei jeder Größenänderung (Andocken, Trennlinie verschieben, Fenstergröße)
            // wird die ganze Anlage in den sichtbaren Bereich eingepasst.
            javafx.animation.PauseTransition ruhe = new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));
            ruhe.setOnFinished(e -> einpassen());
            // Auf die Größe der ScrollPane hören, nicht auf den sichtbaren Bereich: der wird kleiner, sobald eine
            // Bildlaufleiste erscheint – Einpassen ließ sie wieder verschwinden, der Bereich wuchs, neu einpassen …
            // Die Übersicht „pulsierte“ dann ständig (Anwender 22.10.2026).
            javafx.beans.value.ChangeListener<Number> groesse = (obs, alt, neu) -> {
                if (alt == null || Math.abs(alt.doubleValue() - neu.doubleValue()) > 1) {
                    ruhe.playFromStart();
                }
            };
            scroll.widthProperty().addListener(groesse);
            scroll.heightProperty().addListener(groesse);
            javafx.application.Platform.runLater(this::einpassen);
        }
        return root;
    }

    /** Platz für eine Bildlaufleiste beim Einpassen. */
    private static final double LEISTE = 16;

    /** Zoom so, dass alle Symbole und Texte in den sichtbaren Bereich passen, und dorthin rollen. */
    public void einpassen() {
        if (scroll == null) {
            return;
        }
        // Fläche ohne Bildlaufleisten, abzüglich ihrer Breite: so ändert das Einpassen die Leisten nicht mehr.
        javafx.geometry.Insets ins = scroll.getInsets();
        double vw = scroll.getWidth() - ins.getLeft() - ins.getRight() - LEISTE;
        double vh = scroll.getHeight() - ins.getTop() - ins.getBottom() - LEISTE;
        javafx.geometry.Bounds b = null;
        for (Node n : contentPane.getChildren()) {
            if (n == linienEbene || !n.isVisible()) {
                continue;
            }
            javafx.geometry.Bounds nb = n.getBoundsInParent();
            b = b == null ? nb : new javafx.geometry.BoundingBox(Math.min(b.getMinX(), nb.getMinX()),
                    Math.min(b.getMinY(), nb.getMinY()),
                    Math.max(b.getMaxX(), nb.getMaxX()) - Math.min(b.getMinX(), nb.getMinX()),
                    Math.max(b.getMaxY(), nb.getMaxY()) - Math.min(b.getMinY(), nb.getMinY()));
        }
        if (b == null || vw < 20 || vh < 20) {
            return;
        }
        double rand = 30;
        double z = Math.min(vw / (b.getWidth() + 2 * rand), vh / (b.getHeight() + 2 * rand));
        setZoom(Math.min(1.5, z));
        javafx.geometry.Bounds ziel = b;
        javafx.application.Platform.runLater(() -> {
            javafx.geometry.Bounds inGruppe = contentPane.localToParent(ziel);
            javafx.geometry.Bounds gruppe = contentPane.getParent().getLayoutBounds();
            double sw = scroll.getViewportBounds().getWidth();
            double sh = scroll.getViewportBounds().getHeight();
            if (gruppe.getWidth() > sw) {
                double h = (inGruppe.getMinX() - gruppe.getMinX() - (sw - inGruppe.getWidth()) / 2)
                        / (gruppe.getWidth() - sw);
                scroll.setHvalue(Math.max(0, Math.min(1, h)));
            }
            if (gruppe.getHeight() > sh) {
                double v = (inGruppe.getMinY() - gruppe.getMinY() - (sh - inGruppe.getHeight()) / 2)
                        / (gruppe.getHeight() - sh);
                scroll.setVvalue(Math.max(0, Math.min(1, v)));
            }
        });
    }

    private void zentriereAufInfrastruktur(ScrollPane scroll) {
        AnlagenObjekt mitte = anlage.infrastruktur();
        double breite = contentPane.getBoundsInParent().getWidth();
        double hoehe = contentPane.getBoundsInParent().getHeight();
        double sichtB = scroll.getViewportBounds().getWidth();
        double sichtH = scroll.getViewportBounds().getHeight();
        if (breite > sichtB) {
            scroll.setHvalue(Math.max(0, Math.min(1, (mitte.getX() + 30 - sichtB / 2) / (breite - sichtB))));
        }
        if (hoehe > sichtH) {
            scroll.setVvalue(Math.max(0, Math.min(1, (mitte.getY() + 40 - sichtH / 2) / (hoehe - sichtH))));
        }
    }

    /** Baut alle Symbole, Texte und Verbindungen neu auf (nach Anlegen/Loeschen). */
    public void neuAufbauen() {
        contentPane.getChildren().clear();
        ansichten.clear();
        schilderBoxen.clear();
        iconHalter.clear();
        textAnsichten.clear();
        flaecheSetzen();
        contentPane.getChildren().addAll(rahmenEbene, linienEbene);
        rasterSetzen();
        paletteStil();
        linienEbene.setEffect(AppSettings.getInstance().isSymbol3d() ? schatten(3, 6) : null);
        if (bereich == null) {
            for (NetzText t : anlage.getTexte()) {
                fuegeTextHinzu(t); // Texte nur im ersten Reiter
            }
        }
        for (AnlagenObjekt o : sichtbar()) {
            fuegeObjektHinzu(o);
        }
        zeichneVerbindungen();
        aktualisiere();
        javafx.application.Platform.runLater(this::breitenAngleichen);
    }

    // ------------------------------------------------------------------ Fläche mit Rahmen (Anwender 27.10.2026)

    private static final double FLAECHE_VORGABE_B = 1800;
    private static final double FLAECHE_VORGABE_H = 1100;
    /** Breite des silbernen Lineals oben und links. */
    private static final double LINEAL = 12;
    private final Group rahmenEbene = new Group();
    private double flaecheB = FLAECHE_VORGABE_B;
    private double flaecheH = FLAECHE_VORGABE_H;

    /** Größe der Zeichenfläche aus der Anlage, immer volle Rasterkästchen; mindestens so groß wie die Objekte. */
    private void flaecheSetzen() {
        double b = anlage.getFlaecheBreite() > 0 ? anlage.getFlaecheBreite() : FLAECHE_VORGABE_B;
        double h = anlage.getFlaecheHoehe() > 0 ? anlage.getFlaecheHoehe() : FLAECHE_VORGABE_H;
        for (AnlagenObjekt o : sichtbar()) {
            b = Math.max(b, o.getX() + Math.max(o.getBreite(), 240) + RASTER);
            h = Math.max(h, o.getY() + 200);
        }
        groesse(aufRaster(b), aufRaster(h));
    }

    private static double aufRaster(double v) {
        return Math.ceil(v / RASTER) * RASTER;
    }

    private void groesse(double b, double h) {
        flaecheB = b;
        flaecheH = h;
        contentPane.setMinSize(b, h);
        contentPane.setPrefSize(b, h);
        contentPane.setMaxSize(b, h);
        rahmenZeichnen();
    }

    /**
     * Wie in iTrain: silberner Rahmen um die Fläche, oben und links ein Lineal mit Strichen je Rasterkästchen, rechts
     * und unten schließt ein Strich ab; unten rechts ein Griff zum Vergrößern/Verkleinern. Nur im Editor.
     */
    private void rahmenZeichnen() {
        rahmenEbene.getChildren().clear();
        if (!interaktiv) {
            return;
        }
        Color silber = Color.web("#c8c8c8");
        Color strich = Color.web("#555555");
        javafx.scene.shape.Rectangle oben = new javafx.scene.shape.Rectangle(0, 0, flaecheB, LINEAL);
        javafx.scene.shape.Rectangle links = new javafx.scene.shape.Rectangle(0, 0, LINEAL, flaecheH);
        javafx.scene.shape.Rectangle rechts = new javafx.scene.shape.Rectangle(flaecheB - 4, 0, 4, flaecheH);
        javafx.scene.shape.Rectangle unten = new javafx.scene.shape.Rectangle(0, flaecheH - 4, flaecheB, 4);
        for (javafx.scene.shape.Rectangle r : List.of(oben, links, rechts, unten)) {
            r.setFill(silber);
        }
        rahmenEbene.getChildren().addAll(oben, links, rechts, unten);
        for (double x = RASTER; x < flaecheB - 4; x += RASTER) {
            boolean lang = Math.round(x / RASTER) % 5 == 0;
            Line t = new Line(x, LINEAL, x, LINEAL - (lang ? 7 : 3));
            t.setStroke(strich);
            rahmenEbene.getChildren().add(t);
        }
        for (double y = RASTER; y < flaecheH - 4; y += RASTER) {
            boolean lang = Math.round(y / RASTER) % 5 == 0;
            Line t = new Line(LINEAL, y, LINEAL - (lang ? 7 : 3), y);
            t.setStroke(strich);
            rahmenEbene.getChildren().add(t);
        }
        javafx.scene.shape.Rectangle aussen = new javafx.scene.shape.Rectangle(0.5, 0.5, flaecheB - 1, flaecheH - 1);
        aussen.setFill(null);
        aussen.setStroke(Color.web("#8a8a8a"));
        javafx.scene.shape.Rectangle griff = new javafx.scene.shape.Rectangle(flaecheB - 14, flaecheH - 14, 14, 14);
        griff.setFill(silber);
        griff.setStroke(strich);
        rahmenEbene.getChildren().addAll(aussen, griff);
        rahmenEbene.setMouseTransparent(true);
    }

    /** Am rechten bzw. unteren Rand (oder Griff) ziehen ändert die Größe der Fläche in Rasterschritten. */
    private void flaecheZiehenEinrichten() {
        final double ZONE = 10;
        int[] modus = {0}; // 1 = rechts, 2 = unten, 3 = Ecke
        contentPane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            if (e.getTarget() != contentPane) {
                return;
            }
            boolean r = e.getX() >= flaecheB - ZONE;
            boolean u = e.getY() >= flaecheH - ZONE;
            contentPane.setCursor(r && u ? javafx.scene.Cursor.SE_RESIZE : r ? javafx.scene.Cursor.E_RESIZE
                    : u ? javafx.scene.Cursor.S_RESIZE : javafx.scene.Cursor.DEFAULT);
        });
        contentPane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            modus[0] = 0;
            if (e.getTarget() != contentPane || e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            boolean r = e.getX() >= flaecheB - ZONE;
            boolean u = e.getY() >= flaecheH - ZONE;
            modus[0] = r && u ? 3 : r ? 1 : u ? 2 : 0;
        });
        contentPane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
            if (modus[0] == 0) {
                return;
            }
            double minB = 400;
            double minH = 300;
            for (VBox b : ansichten.values()) {
                minB = Math.max(minB, b.getLayoutX() + b.getWidth() + RASTER);
                minH = Math.max(minH, b.getLayoutY() + b.getHeight() + RASTER);
            }
            double b = (modus[0] & 1) != 0 ? Math.max(aufRaster(minB), Math.round(e.getX() / RASTER) * RASTER) : flaecheB;
            double h = modus[0] >= 2 ? Math.max(aufRaster(minH), Math.round(e.getY() / RASTER) * RASTER) : flaecheH;
            if (b != flaecheB || h != flaecheH) {
                groesse(b, h);
            }
            e.consume();
        });
        contentPane.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            if (modus[0] != 0) {
                modus[0] = 0;
                anlage.setFlaeche(flaecheB, flaecheH);
                geaendert();
            }
        });
    }

    // ------------------------------------------------------------------ Raster (Anwender 27.10.2026)

    /** Rasterweite in Pixeln der Zeichenfläche. */
    static final double RASTER = 20;

    private static javafx.scene.image.Image rasterKachel;

    private static boolean rasterAn(boolean interaktiv) {
        return interaktiv && AppSettings.getInstance().isEditorRaster();
    }

    /** Nur im Editor: Linien im Abstand {@link #RASTER} als wiederholte Kachel, jede fünfte etwas kräftiger. */
    private void rasterSetzen() {
        if (!rasterAn(interaktiv)) {
            contentPane.setBackground(null);
            return;
        }
        if (rasterKachel == null) {
            int n = (int) RASTER * 5;
            javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage(n, n);
            javafx.scene.image.PixelWriter pw = img.getPixelWriter();
            Color fein = Color.rgb(128, 128, 128, 0.32);
            Color grob = Color.rgb(128, 128, 128, 0.6);
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    boolean linieX = i % (int) RASTER == 0;
                    boolean linieY = j % (int) RASTER == 0;
                    if (i == 0 || j == 0) {
                        pw.setColor(i, j, grob);
                    } else if (linieX || linieY) {
                        pw.setColor(i, j, fein);
                    } else {
                        pw.setColor(i, j, Color.TRANSPARENT);
                    }
                }
            }
            rasterKachel = img;
        }
        contentPane.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundImage(
                rasterKachel, javafx.scene.layout.BackgroundRepeat.REPEAT, javafx.scene.layout.BackgroundRepeat.REPEAT,
                javafx.scene.layout.BackgroundPosition.DEFAULT, javafx.scene.layout.BackgroundSize.DEFAULT)));
    }

    /** Wert auf das Raster runden (nur im Editor mit eingeschaltetem Raster). */
    private double einrasten(double v) {
        return rasterAn(interaktiv) ? Math.max(0, Math.round(v / RASTER) * RASTER) : v;
    }

    /**
     * Bahnhof mindestens so breit wie sein Güterbahnhof (Anwender 27.10.2026): nach dem Anlegen eines Gbf durch das
     * Programm (`breiteAngleichen`) und solange der Bahnhof automatische Breite hat. Hat der Anwender die Breite
     * gezogen, bleibt sie. Sind die Kästen noch nicht ausgemessen (Fenster noch nicht sichtbar), später erneut.
     */
    private void breitenAngleichen() {
        breitenAngleichen(0);
    }

    private void breitenAngleichen(int versuch) {
        boolean geaendert = false;
        boolean nochmal = false;
        for (Station gbf : anlage.stationen()) {
            Station bahnhof = anlage.gehoertZu(gbf);
            if (bahnhof == null || !(bahnhof.isBreiteAngleichen() || bahnhof.getBreite() <= 0)) {
                continue;
            }
            VBox bb = ansichten.get(bahnhof);
            VBox gb = ansichten.get(gbf);
            if (bb == null || gb == null) {
                continue;
            }
            if (bb.getScene() == null || bb.getWidth() <= 0 || gb.getWidth() <= 0) {
                nochmal = true;
                continue;
            }
            double wb = bb.getWidth();
            double wg = gb.getWidth();
            if (wg > wb + 0.5) {
                bahnhof.setBreite(Math.ceil(wg));
                bb.setPrefWidth(Math.ceil(wg));
                geaendert = true;
            }
            if (interaktiv && bahnhof.isBreiteAngleichen()) {
                bahnhof.setBreiteAngleichen(false);
                geaendert = true;
            }
        }
        if (geaendert) {
            zeichneVerbindungen();
            javafx.application.Platform.runLater(this::zeichneVerbindungen);
            if (interaktiv) {
                geaendert();
            }
        }
        if (nochmal && versuch < 20) {
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
            p.setOnFinished(e -> breitenAngleichen(versuch + 1));
            p.play();
        }
    }

    /** Erneuert nur die Schilder (Werte aus dem Echtzeit-Kern). */
    public void aktualisiere() {
        // Nur neu bauen, wenn sich etwas geändert hat - sonst verschwinden die Tooltips beim Überfahren.
        for (Map.Entry<AnlagenObjekt, VBox> e : schilderBoxen.entrySet()) {
            VBox neu = new VBox();
            neu.getChildren().setAll(schilder(e.getKey()));
            if (e.getKey() instanceof Betrieb) {
                // In der Ellipse stehen die Überschriften mittig (Anwender 31.10.2026)
                for (Node n : neu.getChildren()) {
                    if (n instanceof Label lb) {
                        lb.setAlignment(Pos.CENTER);
                        lb.setTextAlignment(TextAlignment.CENTER);
                        lb.setWrapText(true);
                        lb.setMaxWidth(BETRIEB_INHALT_BREITE + 10);
                    } else if (n instanceof FlowPane fp) {
                        // schmaler als bei Stationen, damit die Ellipse kleiner bleibt als ein Bahnhof
                        fp.setPrefWrapLength(BETRIEB_INHALT_BREITE);
                        fp.setMaxWidth(BETRIEB_INHALT_BREITE + 10);
                    }
                }
            }
            String text = Symbole.texte(neu);
            if (!text.equals(e.getValue().getUserData())) {
                e.getValue().setUserData(text);
                e.getValue().getChildren().setAll(List.copyOf(neu.getChildren()));
            }
        }
    }

    // ------------------------------------------------------------------ Objekte

    private void fuegeObjektHinzu(AnlagenObjekt o) {
        StackPane iconHalter = new StackPane(icon(o));
        iconHalter.setPrefSize(48, 48);
        iconHalter.setMaxSize(48, 48);
        Label name = new Label(o.getAnzeigeName());
        name.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        VBox schilder = new VBox(2);
        schilder.setAlignment(Pos.TOP_CENTER);
        VBox box = new VBox(2, name, iconHalter, schilder);
        if (o.getBreite() > 0) {
            box.setPrefWidth(o.getBreite()); // vom Anwender gezogen; die Mindestbreite bestimmt der Inhalt
        }
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(3));
        box.setLayoutX(o.getX());
        box.setLayoutY(o.getY());
        rahmen(o, box);
        ansichten.put(o, box);
        schilderBoxen.put(o, schilder);
        this.iconHalter.put(o, iconHalter);
        // Verbindungslinien haengen an der Mitte des Symbols; die steht erst nach dem Layout fest.
        // Nicht mitten im Layout neu zeichnen, sondern gesammelt danach (Anwender 31.10.2026: beim Ziehen am Rand
        // einer Ellipse wurde die Fläche leer – Größenänderung während des Layouts löste ein erneutes Zeichnen aus).
        box.widthProperty().addListener((obs, alt, neu) -> verbindungenSpaeter());
        box.heightProperty().addListener((obs, alt, neu) -> verbindungenSpaeter());

        if (interaktiv) {
            richteZiehenEin(o, box);
            ContextMenu menu = new ContextMenu();
            MenuItem bearbeiten = new MenuItem("Bearbeiten…");
            bearbeiten.setOnAction(e -> onOeffnen.accept(echt(o)));
            MenuItem farbe = new MenuItem("Farbe…");
            farbe.setOnAction(e -> waehleFarbe(o, iconHalter));
            menu.getItems().addAll(bearbeiten, farbe);
            if (o instanceof Station st && anlage.gehoertZu(st) == null) {
                // Bahnhof an einen Tab bzw. ein Modul hängen (Anwender 09.11.2026)
                MenuItem tab = new MenuItem("Mit Tab verbinden…");
                tab.setOnAction(e -> mitBereichVerbinden(st, box, false));
                MenuItem mod = new MenuItem("Mit Modul verbinden…");
                mod.setOnAction(e -> mitBereichVerbinden(st, box, true));
                menu.getItems().addAll(tab, mod);
            }
            if (!(o instanceof Infrastruktur) && !(o instanceof ReiterAnker)) {
                MenuItem kopieren = new MenuItem("Kopieren");
                kopieren.setOnAction(e -> kopiere(o));
                menu.getItems().add(kopieren);
            }
            if (!(o instanceof Station) && !(o instanceof Infrastruktur) && !(o instanceof com.example.itrain_logistik.kern.Bereich) && !(o instanceof ReiterAnker)) {
                // Sichtbare Verbindung zu einem Bahnhof/Gueterbahnhof (= Einzugsbereich).
                MenuItem verbinden = new MenuItem("Mit Station verbinden…");
                verbinden.setOnAction(e -> verbindeMitStationen(o, box));
                menu.getItems().add(verbinden);
            }
            if (!(o instanceof Infrastruktur) && !(o instanceof ReiterAnker)) {
                MenuItem loeschen = new MenuItem("Löschen");
                loeschen.setOnAction(e -> {
                    anlage.entferne(o);
                    neuAufbauen();
                    geaendert();
                });
                menu.getItems().add(loeschen);
            }
            box.setOnContextMenuRequested(e -> menu.show(box, e.getScreenX(), e.getScreenY()));
        } else {
            box.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    onOeffnen.accept(echt(o));
                }
            });
        }
        contentPane.getChildren().add(box);
    }

    private Group icon(AnlagenObjekt o) {
        Group icon = SymbolRegistry.getInstance().symbolFuer(o.getArt().getSymbolSchluessel()).createIcon();
        if (o.getFarbe() != null && !o.getFarbe().isBlank()) {
            NetzIcons.applyColor(icon, Symbole.farbe(o.getFarbe(), NetzIcons.DEFAULT_COLOR));
        }
        return icon;
    }

    /** Hervorhebung neu eingefügter, noch nicht verschobener Objekte. */
    private static final String NEU_FARBE = "#ff9800";

    /**
     * Abgerundeter Rahmen in der Symbolfarbe um Name, Symbol und Schilder. Neu eingefügte Objekte
     * bekommen einen orangen, gestrichelten Rahmen mit getöntem Hintergrund, bis sie verschoben werden.
     */
    private void rahmen(AnlagenObjekt o, VBox box) {
        AppSettings einst = AppSettings.getInstance();
        // Kasten deckend gefüllt (eigene Farbe in den Voreinstellungen), neue Objekte orange getönt.
        String fuellung = o.isNeu()
                ? Symbole.hex(Symbole.farbe(fuellungsFarbe(o), Color.WHITE).interpolate(Color.web(NEU_FARBE), 0.18))
                : fuellungsFarbe(o);
        String stil = o.isNeu()
                ? "-fx-border-color: " + NEU_FARBE + "; -fx-border-width: 3; -fx-border-style: segments(8, 5); "
                : "-fx-border-color: " + Symbole.hex(symbolFarbe(o)) + "; -fx-border-width: 2; ";
        stil += "-fx-background-color: " + fuellung + "; ";
        stil += form(o, box) + (interaktiv ? "" : " -fx-cursor: hand;");
        box.setStyle(stil);
        box.getStylesheets().setAll(schriftStylesheet(o));
        box.setEffect(einst.isSymbol3d() ? schatten(4, 10) : null);
        if (o.isNeu()) {
            javafx.scene.control.Tooltip.install(box, new javafx.scene.control.Tooltip(
                    "Neu eingefügt – an die gewünschte Stelle ziehen"));
        }
    }

    /** Breite des Inhalts eines Betriebs (Schilder brechen danach um). */
    private static final double BETRIEB_INHALT_BREITE = 120;

    /** Formen der Orte zum Vergleich (Anwender 31.10.2026: Vorschlag gewünscht). */
    enum OrtForm { HAUS, ACHTECK, RECHTECK }

    static OrtForm ortForm = OrtForm.HAUS;

    /**
     * Form des Kastens (Anwender 31.10.2026): Stationen, Lager, Übergaben und die Infrastruktur bleiben Rechtecke mit
     * abgerundeten Ecken, Betriebe sind Ellipsen, Orte (Stadt/Dorf/Ländlich) haben ein Giebeldach. Der Innenabstand
     * hält den Inhalt innerhalb der Form.
     */
    private static String form(AnlagenObjekt o, VBox box) {
        if (o instanceof Betrieb) {
            // Ellipse: der Inhalt muss im eingeschriebenen Rechteck liegen (≈ 15 % Rand je Seite). Der Rand folgt der
            // Größe; bei automatischer Breite pendelt sich das in wenigen Schritten ein.
            if (box.getProperties().putIfAbsent("ellipse", Boolean.TRUE) == null) {
                Runnable rand = () -> {
                    double h = Math.max(10, Math.round(box.getWidth() * 0.13));
                    double v = Math.max(8, Math.round(box.getHeight() * 0.12));
                    Insets alt = box.getPadding();
                    if (Math.abs(alt.getLeft() - h) > 1 || Math.abs(alt.getTop() - v) > 1) {
                        box.setPadding(new Insets(v, h, v, h));
                    }
                };
                box.widthProperty().addListener((obs, a, b) -> rand.run());
                box.heightProperty().addListener((obs, a, b) -> rand.run());
            }
            if (box.getPadding().getLeft() < 10) {
                box.setPadding(new Insets(12, 22, 12, 22));
            }
            // Schmaler als der Inhalt wird die Ellipse nicht (sonst würden Name und Schilder abgeschnitten)
            box.setMinWidth(BETRIEB_INHALT_BREITE + 2 * 18);
            return "-fx-border-radius: 50%; -fx-background-radius: 50%;";
        }
        if (o instanceof Lager) {
            // Lager als Achteck (Anwender 31.10.2026)
            box.setPadding(new Insets(8, 12, 8, 12));
            return "-fx-shape: \"M12 0 L88 0 L100 12 L100 88 L88 100 L12 100 L0 88 L0 12 Z\";";
        }
        if (o instanceof Ort) {
            switch (ortForm) {
                case HAUS -> {
                    box.setPadding(new Insets(20, 6, 6, 6));
                    return "-fx-shape: \"M0 22 L50 0 L100 22 L100 100 L0 100 Z\";";
                }
                case ACHTECK -> {
                    box.setPadding(new Insets(8, 12, 8, 12));
                    return "-fx-shape: \"M12 0 L88 0 L100 12 L100 88 L88 100 L12 100 L0 88 L0 12 Z\";";
                }
                default -> {
                }
            }
        }
        box.setPadding(new Insets(3));
        return "-fx-border-radius: 8; -fx-background-radius: 8;";
    }

    /** Füllung des Kastens: je Objekt (Rechtsklick „Farbe…“), sonst Standard (Anwender 27.10.2026). */
    static String fuellungsFarbe(AnlagenObjekt o) {
        return o.getFuellung() != null && !o.getFuellung().isBlank() ? o.getFuellung()
                : AppSettings.SYMBOL_FUELLUNG_STANDARD;
    }

    /** Schriftfarbe im Kasten: je Objekt, sonst Standard. */
    static String schriftFarbe(AnlagenObjekt o) {
        return o.getSchrift() != null && !o.getSchrift().isBlank() ? o.getSchrift()
                : AppSettings.SYMBOL_SCHRIFT_STANDARD;
    }

    /** Farbe des Symbols: eigene Farbe, sonst die erste Füllfarbe des Symbols. */
    static Color symbolFarbe(AnlagenObjekt o) {
        if (o.getFarbe() != null && !o.getFarbe().isBlank()) {
            return Symbole.farbe(o.getFarbe(), NetzIcons.DEFAULT_COLOR);
        }
        Color c = ersteFuellung(SymbolRegistry.getInstance().symbolFuer(o.getArt().getSymbolSchluessel()).createIcon());
        return c != null ? c : NetzIcons.DEFAULT_COLOR;
    }

    private static Color ersteFuellung(Group g) {
        for (Node n : g.getChildren()) {
            if (n instanceof javafx.scene.shape.Shape sh && sh.getFill() instanceof Color c
                    && c.getOpacity() > 0.5 && !(c.getRed() > 0.95 && c.getGreen() > 0.95 && c.getBlue() > 0.95)) {
                return c;
            } else if (n instanceof Group inner) {
                Color c = ersteFuellung(inner);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Editor: Ziehen verschiebt frei, mit Umschalt rastet die Verbindung zum nächsten Partner gerade ein (90°); Ziehen am rechten Rand
     * ändert die Breite (Mindestbreite und -höhe ergeben sich aus dem Inhalt). Den Einzugsbereich ordnet man über
     * Rechtsklick „Mit Station verbinden…“ zu (Anwender 12.10.2026, Ziehen mit Taste entfallen).
     */
    private void richteZiehenEin(AnlagenObjekt o, VBox box) {
        double[] start = new double[2];
        double[] breiteStart = new double[2];
        boolean[] gezogen = {false};
        boolean[] verbinden = {false};
        boolean[] breiteZiehen = {false};
        final double RAND = 7;
        box.setOnMouseMoved(e -> box.setCursor(e.getX() >= box.getWidth() - RAND ? javafx.scene.Cursor.H_RESIZE
                : javafx.scene.Cursor.DEFAULT));
        box.setOnMouseExited(e -> box.setCursor(javafx.scene.Cursor.DEFAULT));
        box.setOnMousePressed(e -> {
            Point2D p = contentPane.sceneToLocal(e.getSceneX(), e.getSceneY());
            zuletztGewaehlt = o;
            verbinden[0] = false;
            breiteZiehen[0] = false;
            if (e.getButton() == MouseButton.PRIMARY && e.getX() >= box.getWidth() - RAND) {
                breiteZiehen[0] = true;
                breiteStart[0] = box.getWidth();
                breiteStart[1] = p.getX();
            } else {
                start[0] = p.getX() - box.getLayoutX();
                start[1] = p.getY() - box.getLayoutY();
            }
            gezogen[0] = false;
            e.consume();
        });
        box.setOnMouseDragged(e -> {
            Point2D p = contentPane.sceneToLocal(e.getSceneX(), e.getSceneY());
            if (verbinden[0]) {
                verbindungsVorschau.setEndX(p.getX());
                verbindungsVorschau.setEndY(p.getY());
            } else if (breiteZiehen[0]) {
                double min = box.minWidth(-1);
                double w = Math.max(min, breiteStart[0] + p.getX() - breiteStart[1]);
                if (rasterAn(interaktiv)) {
                    // rechter Rand auf dem Raster (Anwender 27.10.2026)
                    double rechts = einrasten(box.getLayoutX() + w);
                    w = rechts - box.getLayoutX() >= min ? rechts - box.getLayoutX() : w;
                }
                box.setPrefWidth(w);
                o.setBreite(w);
                gezogen[0] = true;
                zeichneVerbindungen();
            } else {
                double x = Math.max(0, p.getX() - start[0]);
                double y = Math.max(0, p.getY() - start[1]);
                if (e.isShiftDown()) {
                    // Verbindung zum nächsten verbundenen Objekt gerade (senkrecht oder waagrecht) ausrichten
                    double[] xy = geradeAusrichten(o, box, x, y);
                    x = Math.max(0, xy[0]);
                    y = Math.max(0, xy[1]);
                } else {
                    // auf das Raster einrasten (Anwender 27.10.2026); Umschalt richtet stattdessen gerade aus
                    x = einrasten(x);
                    y = einrasten(y);
                }
                if (Math.abs(x - box.getLayoutX()) > 1 || Math.abs(y - box.getLayoutY()) > 1) {
                    gezogen[0] = true;
                }
                box.setLayoutX(x);
                box.setLayoutY(y);
                o.setX(x);
                o.setY(y);
                zeichneVerbindungen();
            }
            e.consume();
        });
        box.setOnMouseReleased(e -> {
            if (verbinden[0]) {
                linienEbene.getChildren().remove(verbindungsVorschau);
                verbindungsVorschau = null;
                Point2D p = contentPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                AnlagenObjekt ziel = objektAn(p, o);
                if (ziel != null) {
                    schalteEinzugsbereich(o, ziel);
                }
            } else if (gezogen[0]) {
                if (o.isNeu() && !breiteZiehen[0]) {
                    o.setNeu(false);
                    rahmen(o, box);
                }
                geaendert();
            } else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() >= 2) {
                onOeffnen.accept(o);
            }
            breiteZiehen[0] = false;
            e.consume();
        });
    }

    /**
     * Umschalt beim Verschieben (Anwender 12.10.2026): Der Kasten rastet so ein, dass seine Verbindung zum nächsten
     * verbundenen Objekt eine Gerade im rechten Winkel ist – liegt der Partner eher darüber/darunter, gleiche Mitte
     * waagrecht (senkrechte Linie), sonst gleiche Mitte senkrecht (waagrechte Linie).
     */
    private double[] geradeAusrichten(AnlagenObjekt o, VBox box, double x, double y) {
        List<AnlagenObjekt> partner = new ArrayList<>();
        if (o instanceof Station st) {
            Station bahnhof = anlage.gehoertZu(st);
            com.example.itrain_logistik.kern.Bereich modul = anlage.bereichFuer(st);
            partner.add(bahnhof != null ? bahnhof : modul != null ? (modul == bereich ? anker : modul)
                    : anlage.infrastruktur());
            for (String id : st.getEinzugsbereich()) {
                AnlagenObjekt p = anlage.objekt(id);
                if (p != null) {
                    partner.add(p);
                }
            }
            for (Station andere : anlage.stationen()) {
                if (anlage.gehoertZu(andere) == st) {
                    partner.add(andere);
                }
            }
        } else if (o instanceof Infrastruktur) {
            for (Station andere : anlage.stationen()) {
                if (anlage.gehoertZu(andere) == null && anlage.bereichFuer(andere) == null) {
                    partner.add(andere);
                }
            }
            partner.addAll(anlage.bereiche());
        } else if (o instanceof com.example.itrain_logistik.kern.Bereich m) {
            partner.add(anlage.infrastruktur());
        } else if (o instanceof ReiterAnker ra) {
            for (Station andere : anlage.stationen()) {
                if (anlage.gehoertZu(andere) == null && anlage.bereichFuer(andere) == ra.getBereich()) {
                    partner.add(andere);
                }
            }
        } else {
            partner.addAll(anlage.stationenFuer(o.getId()));
            if (partner.isEmpty()) {
                partner.add(anlage.infrastruktur());
            }
        }
        double w = box.getWidth();
        double h = box.getHeight();
        double cx = x + w / 2;
        double cy = y + h / 2;
        // Unter allen Partnern und beiden Richtungen die Lage, die am wenigsten verschoben werden muss.
        double[] best = null;
        double bestWeg = Double.MAX_VALUE;
        for (AnlagenObjekt p : partner) {
            VBox pb = p != null && p != o ? ansichten.get(p) : null;
            if (pb == null) {
                continue;
            }
            double px = pb.getLayoutX() + pb.getWidth() / 2;
            double py = pb.getLayoutY() + pb.getHeight() / 2;
            double senkrecht = Math.abs(cx - px);   // gleiche Mitte waagrecht -> senkrechte Linie
            double waagrecht = Math.abs(cy - py);   // gleiche Mitte senkrecht -> waagrechte Linie
            if (senkrecht < bestWeg) {
                bestWeg = senkrecht;
                best = new double[]{px - w / 2, y};
            }
            if (waagrecht < bestWeg) {
                bestWeg = waagrecht;
                best = new double[]{x, py - h / 2};
            }
        }
        return best != null ? best : new double[]{x, y};
    }

    /** Station + Objekt: Einzugsbereich umschalten (Richtung des Ziehens egal). */
    private void schalteEinzugsbereich(AnlagenObjekt a, AnlagenObjekt b) {
        Station station = a instanceof Station s ? s : (b instanceof Station s2 ? s2 : null);
        AnlagenObjekt anderes = station == a ? b : a;
        if (station == null || anderes instanceof Station || anderes instanceof Infrastruktur) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Eine Verbindung verbindet eine Station mit einem Ort, Betrieb, Lager oder einer Übergabe "
                            + "(Einzugsbereich). Zwei Stationen lassen sich nicht verbinden.").showAndWait();
            return;
        }
        station.schalteEinzugsbereich(anderes.getId());
        int anzahl = anlage.getObjekte().size();
        if (anderes instanceof Ort ort && station.bedient(ort.getId())) {
            ObjektDialoge.nachVerbinden(fenster(), anlage, ort, station);
        }
        ObjektDialoge.gueterZumGbfMelden(fenster(), anlage);
        if (anlage.getObjekte().size() != anzahl) {
            neuAufbauen();
        }
        zeichneVerbindungen();
        aktualisiere();
        geaendert();
    }

    private Stage fenster() {
        return contentPane.getScene() != null ? (Stage) contentPane.getScene().getWindow() : null;
    }

    private AnlagenObjekt objektAn(Point2D p, AnlagenObjekt ausser) {
        for (Map.Entry<AnlagenObjekt, VBox> e : ansichten.entrySet()) {
            if (e.getKey() != ausser && e.getValue().getBoundsInParent().contains(p)) {
                return e.getKey();
            }
        }
        return null;
    }

    /**
     * Rechtsklick „Farbe…“ (Anwender 27.10.2026, vorher teils in den Voreinstellungen): Farbe des Symbols und der
     * Verbindung, Füllung und Schrift des Kastens, je Objekt gespeichert; „Standard“ setzt alle drei zurück.
     */
    private void waehleFarbe(AnlagenObjekt o, StackPane iconHalter) {
        javafx.scene.control.Dialog<javafx.scene.control.ButtonType> d = new javafx.scene.control.Dialog<>();
        d.setTitle("Farbe");
        d.setHeaderText("„" + o.getAnzeigeName() + "“");
        if (contentPane.getScene() != null) {
            d.initOwner(contentPane.getScene().getWindow());
        }
        DialogKnoepfe.setze(d.getDialogPane());
        boolean[] symbolStandard = {o.getFarbe() == null || o.getFarbe().isBlank()};
        ColorPicker symbol = Symbole.ohneHex(new ColorPicker(symbolFarbe(o)));
        ColorPicker fuellung = Symbole.ohneHex(new ColorPicker(Symbole.farbe(fuellungsFarbe(o), Color.WHITE)));
        ColorPicker schrift = Symbole.ohneHex(new ColorPicker(Symbole.farbe(schriftFarbe(o), Color.BLACK)));
        symbol.setOnAction(e -> symbolStandard[0] = false);
        javafx.scene.control.Button standard = new javafx.scene.control.Button("Standard");
        standard.setOnAction(e -> {
            Color vorgabe = ersteFuellung(SymbolRegistry.getInstance().symbolFuer(o.getArt().getSymbolSchluessel())
                    .createIcon());
            symbol.setValue(vorgabe != null ? vorgabe : NetzIcons.DEFAULT_COLOR);
            symbolStandard[0] = true;
            fuellung.setValue(Color.web(AppSettings.SYMBOL_FUELLUNG_STANDARD));
            schrift.setValue(Color.web(AppSettings.SYMBOL_SCHRIFT_STANDARD));
        });
        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.addRow(0, new javafx.scene.control.Label("Farbe des Symbols und der Verbindung"), symbol);
        g.addRow(1, new javafx.scene.control.Label("Füllung"), fuellung);
        g.addRow(2, new javafx.scene.control.Label("Schrift"), schrift);
        g.add(standard, 1, 3);
        d.getDialogPane().setContent(g);
        if (d.showAndWait().orElse(null) != DialogKnoepfe.SPEICHERN) {
            return;
        }
        o.setFarbe(symbolStandard[0] ? null : Symbole.hex(symbol.getValue()));
        String f = Symbole.hex(fuellung.getValue());
        String sch = Symbole.hex(schrift.getValue());
        o.setFuellung(f.equalsIgnoreCase(AppSettings.SYMBOL_FUELLUNG_STANDARD) ? null : f);
        o.setSchrift(sch.equalsIgnoreCase(AppSettings.SYMBOL_SCHRIFT_STANDARD) ? null : sch);
        iconHalter.getChildren().setAll(icon(o));
        VBox box = ansichten.get(o);
        if (box != null) {
            rahmen(o, box);
        }
        zeichneVerbindungen();
        geaendert();
    }

    /** Auswahl der Stationen, die ein Ort/Betrieb/Lager/eine Uebergabe bedienen (Einzugsbereich). */
    /**
     * „Mit Tab verbinden…“ / „Mit Modul verbinden…“ (Anwender 09.11.2026): die Verbindung des Bahnhofs zur
     * Anlagen-Infrastruktur geht an den Tab bzw. das Modul; der Bahnhof steht danach samt Güterbahnhof, Orten,
     * Betrieben und Lagern in dessen Reiter. „Anlagen-Infrastruktur“ holt ihn zurück in den ersten Reiter.
     */
    private void mitBereichVerbinden(Station st, VBox box, boolean modul) {
        List<com.example.itrain_logistik.kern.Bereich> ziele = new ArrayList<>();
        for (com.example.itrain_logistik.kern.Bereich b : anlage.bereiche()) {
            if ((b instanceof Modul) == modul) {
                ziele.add(b);
            }
        }
        Stage owner = box.getScene() != null ? (Stage) box.getScene().getWindow() : null;
        if (ziele.isEmpty()) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Es gibt noch kein " + (modul ? "Modul" : "Tab") + ". Bitte zuerst aus der Palette „"
                            + (modul ? "Modul" : "Tab") + "“ im ersten Reiter anlegen; er hängt an der Anlagen-Infrastruktur.");
            a.initOwner(owner);
            a.setHeaderText(modul ? "Kein Modul" : "Kein Tab");
            a.showAndWait();
            return;
        }
        List<String> namen = new ArrayList<>();
        String infra = "Anlagen-Infrastruktur (" + anlage.getHauptReiterName() + ")";
        namen.add(infra);
        ziele.forEach(b -> namen.add(b.getAnzeigeName()));
        com.example.itrain_logistik.kern.Bereich jetzt = anlage.bereichFuer(st);
        javafx.scene.control.ChoiceDialog<String> d = new javafx.scene.control.ChoiceDialog<>(
                jetzt != null && ziele.contains(jetzt) ? jetzt.getAnzeigeName() : namen.get(namen.size() > 1 ? 1 : 0),
                namen);
        d.initOwner(owner);
        d.setTitle(modul ? "Mit Modul verbinden" : "Mit Tab verbinden");
        d.setHeaderText("„" + st.getAnzeigeName() + "“ hängt dann am " + (modul ? "Modul" : "Tab")
                + " statt an der Anlagen-Infrastruktur.\nGüterbahnhof, Orte, Betriebe und Lager gehen mit in dessen Reiter.");
        String w = d.showAndWait().orElse(null);
        if (w == null) {
            return;
        }
        com.example.itrain_logistik.kern.Bereich ziel = w.equals(infra) ? null : ziele.get(namen.indexOf(w) - 1);
        anlage.bereichZuordnen(st, ziel);
        for (AnlagenCanvas c : List.copyOf(ALLE.keySet())) {
            if (c.anlage == anlage) {
                c.neuAufbauen();
            }
        }
        geaendert();
    }

    private void verbindeMitStationen(AnlagenObjekt o, VBox box) {
        List<Station> stationen = anlage.stationen();
        if (stationen.isEmpty()) {
            new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Es gibt noch keinen Bahnhof, Haltepunkt, Güterbahnhof oder Anschluss.").showAndWait();
            return;
        }
        VBox liste = new VBox(4);
        List<javafx.scene.control.CheckBox> boxen = new ArrayList<>();
        for (Station s : stationen) {
            javafx.scene.control.CheckBox cb = new javafx.scene.control.CheckBox(s.getAnzeigeName() + " ("
                    + s.getArt().getLabel() + ")");
            cb.setSelected(s.bedient(o.getId()));
            cb.setUserData(s);
            // Schattenbahnhof samt Gbf: keine weiteren Symbole, alles über Gleise (Anwender 09.11.2026)
            // Betriebe und Übergaben dürfen an ihn (Anwender 11.11.2026)
            if (anlage.istAussenwelt(s) && !cb.isSelected() && !(o instanceof Betrieb || o instanceof Uebergabe)) {
                cb.setDisable(true);
                cb.setText(cb.getText() + " – nur über Gleise (Angebot/Reisende je Gleis)");
            }
            // je Bahnhof nur ein Ort (Anwender 27.10.2026)
            else if (o instanceof Ort ort && !cb.isSelected() && !anlage.ortErlaubt(s, ort)) {
                cb.setDisable(true);
                cb.setText(cb.getText() + " – schon mit „" + anlage.ortAnStation(s).getAnzeigeName() + "“ verbunden");
            }
            boxen.add(cb);
            liste.getChildren().add(cb);
        }
        Stage owner = box.getScene() != null ? (Stage) box.getScene().getWindow() : null;
        if (!ObjektDialoge.zeige(owner, o.getAnzeigeName() + " ist angeschlossen an", liste)) {
            return;
        }
        int anzahl = anlage.getObjekte().size();
        for (javafx.scene.control.CheckBox cb : boxen) {
            Station s = (Station) cb.getUserData();
            if (cb.isSelected() != s.bedient(o.getId())) {
                s.schalteEinzugsbereich(o.getId());
                if (o instanceof Ort ort && s.bedient(o.getId())) {
                    ObjektDialoge.nachVerbinden(owner, anlage, ort, s);
                }
            }
        }
        ObjektDialoge.gueterZumGbfMelden(owner, anlage);
        if (anlage.getObjekte().size() != anzahl) {
            neuAufbauen();
        }
        zeichneVerbindungen();
        aktualisiere();
        geaendert();
    }

    // ------------------------------------------------------------------ Verbindungen mit Lauflicht

    private static final Color GRUEN = Color.web("#22c55e");
    /** Verbindung Bahnhof – zugehöriger Güterbahnhof (Anwender 27.10.2026). */
    private static final Color SILBER = Color.web("#c0c0c0");
    /** Stufen der Linienstärke im Rechtsklick-Menü einer Verbindung. */
    private static final int[] STAERKEN = {1, 3, 5, 8};
    private static final Color ROT = Color.web("#ef4444");
    /** Laufgeschwindigkeit eines Segments in Pixeln der Zeichenfläche je Sekunde. */
    private static final double TEMPO_PX = 45;

    /** Laufzustand einer Spur (bleibt beim Neuzeichnen erhalten): Lauflicht aktiv bis, Phase in Segmenten. */
    private static final class SpurZustand {
        long bis;
        double phase;
        boolean an;
        /** Angesammelte Bruchteile; erst eine ganze Einheit startet das Lauflicht. */
        double rest;
    }

    /** Geometrie einer Spur: Start -> Ende in Laufrichtung, Segment-Linien (an/aus). */
    private static final class Spur {
        final SpurZustand zustand;
        final double x1;
        final double y1;
        final double x2;
        final double y2;
        final Line[] segmente;

        Spur(SpurZustand zustand, double x1, double y1, double x2, double y2, Line[] segmente) {
            this.zustand = zustand;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.segmente = segmente;
        }

        double laenge() {
            return Math.hypot(x2 - x1, y2 - y1);
        }
    }

    /** Je Verbindung (Schlüssel "stationId|andereId") die grüne (zur Station) und rote Spur (von ihr weg). */
    private final Map<String, Spur[]> spuren = new java.util.HashMap<>();
    private final Map<String, SpurZustand> spurZustaende = new java.util.HashMap<>();
    private long letzterFrame;

    private final EchtzeitKern.FlussBeobachter beobachter = this::fluss;

    private final javafx.animation.AnimationTimer lauflicht = new javafx.animation.AnimationTimer() {
        @Override
        public void handle(long jetzt) {
            double dt = letzterFrame == 0 ? 0 : Math.min(0.1, (jetzt - letzterFrame) / 1e9);
            letzterFrame = jetzt;
            for (Spur[] paar : spuren.values()) {
                for (Spur sp : paar) {
                    bewege(sp, dt);
                }
            }
        }
    };

    private static String schluessel(AnlagenObjekt station, AnlagenObjekt anderes) {
        return station.getId() + "|" + anderes.getId();
    }

    /**
     * Meldung des Kerns: Güter/Personen bewegen sich zwischen Station und Objekt. Zur Station hin
     * (Bahnhof, Güterbahnhof, Ladegleis) läuft je Einheit ein grünes Segment, von ihr weg ein rotes.
     * Hat die Station keine eigene Linie zum Objekt, aber gehört sie zu einem Bahnhof, der eine hat,
     * läuft es über den Bahnhof.
     */
    private void fluss(AnlagenObjekt von, AnlagenObjekt nach, String gutId, double menge) {
        Station station;
        AnlagenObjekt anderes;
        boolean zurStation;
        if (nach instanceof Station sn && !(von instanceof Station)) {
            station = sn;
            anderes = von;
            zurStation = true;
        } else if (von instanceof Station sv && !(nach instanceof Station)) {
            station = sv;
            anderes = nach;
            zurStation = false;
        } else {
            return;
        }
        // Über die Anlagen-Infrastruktur gibt es keinen Austausch, nur über Fahrten (Anwender 27.10.2026)
        if (anderes instanceof Infrastruktur) {
            return;
        }
        if (spuren.containsKey(schluessel(station, anderes))) {
            einreihen(schluessel(station, anderes), zurStation, menge);
            return;
        }
        Station bahnhof = anlage.gehoertZu(station);
        if (bahnhof != null && spuren.containsKey(schluessel(bahnhof, anderes))) {
            einreihen(schluessel(bahnhof, anderes), zurStation, menge);
            einreihen(schluessel(station, bahnhof), zurStation, menge);
        }
    }

    /**
     * Jede Übergabe lässt die Spur für die eingestellte Zeit (Voreinstellungen, 1-5 s) als Lauflicht
     * leuchten; weitere Übergaben verlängern es.
     */
    private void einreihen(String key, boolean gruen, double menge) {
        Spur[] paar = spuren.get(key);
        if (paar == null || menge <= 0) {
            return;
        }
        SpurZustand z = paar[gruen ? 0 : 1].zustand;
        z.rest += menge;
        long jetzt = System.nanoTime();
        // Läuft es gerade (oder ist es eben erst ausgegangen, 1 s Pause), wird es nicht verlängert.
        if (z.rest >= 1 && jetzt > z.bis + 1_000_000_000L) {
            z.rest = 0;
            z.phase = 0;
            z.bis = jetzt + AppSettings.getInstance().getLauflichtSekunden() * 1_000_000_000L;
        }
    }

    /** Ein Animationsschritt: jedes dritte Segment leuchtet und wandert in Laufrichtung (Lauflicht). */
    private void bewege(Spur sp, double dt) {
        SpurZustand z = sp.zustand;
        int n = sp.segmente.length;
        boolean aktiv = System.nanoTime() < z.bis;
        if (!aktiv) {
            if (z.an) {
                for (Line seg : sp.segmente) {
                    seg.setVisible(false);
                }
                z.an = false;
            }
            return;
        }
        z.an = true;
        z.phase += dt * TEMPO_PX / Math.max(1, sp.laenge() / n);
        int versatz = (int) Math.floor(z.phase) % 3;
        for (int i = 0; i < n; i++) {
            sp.segmente[i].setVisible(Math.floorMod(i - versatz, 3) == 0);
        }
    }

    private boolean verbindungenGeplant;
    private boolean zeichneGerade;

    /** Verbindungen nach dem laufenden Layout einmal neu zeichnen (mehrere Anstöße werden zusammengefasst). */
    private void verbindungenSpaeter() {
        if (verbindungenGeplant) {
            return;
        }
        verbindungenGeplant = true;
        javafx.application.Platform.runLater(() -> {
            verbindungenGeplant = false;
            zeichneVerbindungen();
        });
    }

    private void zeichneVerbindungen() {
        if (zeichneGerade) {
            verbindungenSpaeter(); // kein Neuzeichnen, während schon gezeichnet wird
            return;
        }
        zeichneGerade = true;
        try {
            zeichneVerbindungenJetzt();
        } finally {
            zeichneGerade = false;
        }
    }

    private void zeichneVerbindungenJetzt() {
        // gemessene Kastengrößen für die Platzsuche neuer Objekte (Anwender 09.11.2026)
        ansichten.forEach((o, b) -> {
            if (b.getWidth() > 0 && b.getHeight() > 0) {
                Anlage.merkeMasse(o.getId(), b.getWidth(), b.getHeight());
            }
        });
        linienEbene.getChildren().clear();
        spuren.clear();
        // Stern: alles haengt an der Anlagen-Infrastruktur - Stationen direkt (Doppellinie),
        // Objekte ohne Station gestrichelt (noch nirgends angeschlossen). Ein Güterbahnhof, der zu
        // einem Personenbahnhof gehört, hängt an diesem.
        // im Reiter eines Tabs/Moduls übernimmt dessen Symbol die Rolle der Anlagen-Infrastruktur (Anwender 09.11.2026)
        AnlagenObjekt mitte = bereich != null ? anker : anlage.infrastruktur();
        VBox mitteBox = ansichten.get(mitte);
        for (AnlagenObjekt o : anlage.getObjekte()) {
            VBox b = ansichten.get(o);
            if (o == mitte || b == null || mitteBox == null) {
                continue;
            }
            if (o instanceof Station st) {
                Station bahnhof = anlage.gehoertZu(st);
                VBox bb = bahnhof != null ? ansichten.get(bahnhof) : null;
                com.example.itrain_logistik.kern.Bereich modul = bahnhof == null ? anlage.bereichFuer(st) : null;
                VBox mb = modul == null ? null : modul == bereich ? mitteBox : ansichten.get(modul);
                if (bb != null) {
                    doppellinie(st, b, bahnhof, bb, st, true);
                } else if (mb != null) {
                    infrastrukturLinie(st, b, mb); // Station hängt am Tab/Modul (Anwender 09.11.2026)
                } else {
                    infrastrukturLinie(st, b, mitteBox);
                }
            } else if (o instanceof com.example.itrain_logistik.kern.Bereich) {
                infrastrukturLinie(o, b, mitteBox);
            } else if (anlage.stationenFuer(o.getId()).isEmpty()) {
                einfacheLinie(mitteBox, b, o);
            }
        }
        for (Station s : anlage.stationen()) {
            VBox von = ansichten.get(s);
            if (von == null) {
                continue;
            }
            Station bahnhof = anlage.gehoertZu(s);
            for (String id : s.getEinzugsbereich()) {
                AnlagenObjekt o = anlage.objekt(id);
                VBox nach = o != null ? ansichten.get(o) : null;
                if (nach == null) {
                    continue;
                }
                // Orte, die auch der zugehörige Bahnhof bedient, erreicht der Güterbahnhof über ihn.
                if (bahnhof != null && bahnhof.bedient(id)) {
                    continue;
                }
                doppellinie(s, von, o, nach, o, false);
            }
        }
    }

    private static javafx.scene.shape.Rectangle abschluss(double x, double y, VBox box, double breite, Color farbe) {
        double lang = breite * 2.2;
        double kurz = breite * 1.6;
        double bx = box.getLayoutX();
        double bw = box.getWidth() > 0 ? box.getWidth() : 120;
        boolean senkrechteKante = Math.abs(x - bx) < 1 || Math.abs(x - (bx + bw)) < 1;
        double rw = senkrechteKante ? kurz : lang;
        double rh = senkrechteKante ? lang : kurz;
        javafx.scene.shape.Rectangle r = new javafx.scene.shape.Rectangle(x - rw / 2, y - rh / 2, rw, rh);
        r.setFill(farbe);
        r.setStroke(null);
        r.setMouseTransparent(true);
        return r;
    }

    /**
     * Station – Anlagen-Infrastruktur: ein einfacher Strich ohne Lauflicht (Anwender 27.10.2026), denn darüber findet
     * kein Austausch statt; Reisende und Güter wechseln nur mit den Zügen.
     */
    private void infrastrukturLinie(AnlagenObjekt st, VBox stBox, VBox mitteBox) {
        double[] p = andockPunkte(stBox, mitteBox);
        double w = Math.max(1.5, st.getLinienStaerke() * 0.6);
        Color farbe = symbolFarbe(st);
        Line l = new Line(p[0], p[1], p[2], p[3]);
        l.setStrokeWidth(w);
        l.setStroke(farbe);
        l.setMouseTransparent(true);
        linienEbene.getChildren().addAll(l, abschluss(p[0], p[1], stBox, w * 1.6, farbe),
                abschluss(p[2], p[3], mitteBox, w * 1.6, farbe));
        if (interaktiv) {
            Line treffer = new Line(p[0], p[1], p[2], p[3]);
            treffer.setStrokeWidth(12);
            treffer.setStroke(Color.TRANSPARENT);
            javafx.scene.control.Tooltip.install(treffer, new javafx.scene.control.Tooltip(
                    st.getAnzeigeName() + " gehört zur Anlage · kein Austausch, Reisende und Güter fahren mit Zügen"));
            linienEbene.getChildren().add(treffer);
        }
    }

    /** Gestrichelte Einzellinie (Objekt noch an keiner Station). */
    private void einfacheLinie(VBox a, VBox b, AnlagenObjekt o) {
        double[] p = andockPunkte(a, b);
        Line l = new Line(p[0], p[1], p[2], p[3]);
        l.setStrokeWidth(o.getLinienStaerke());
        l.setStroke(symbolFarbe(o));
        l.setOpacity(0.6);
        l.getStrokeDashArray().addAll(8.0, 6.0);
        l.setMouseTransparent(true);
        linienEbene.getChildren().add(l);
    }

    /**
     * Doppellinie zwischen Station (Seite A) und anderem Objekt (Seite B) in Farbe und Stärke von
     * {@code farbeVon}: eine Spur für grüne Segmente (B -> A, zur Station), eine für rote (A -> B).
     * Die Spuren sind in Segmente geteilt, deren Zahl mit Länge und Zoom wächst und deren Größe der
     * Linienstärke folgt.
     */
    private void doppellinie(Station station, VBox aBox, AnlagenObjekt anderes, VBox bBox, AnlagenObjekt farbeVon,
                             boolean bahnhofGbf) {
        double[] p = andockPunkte(aBox, bBox);
        double ax = p[0];
        double ay = p[1];
        double bx = p[2];
        double by = p[3];
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 4) {
            return;
        }
        // Bahnhof – zugehöriger Güterbahnhof: immer breiteste Stufe, Silber mit dünnem schwarzem Rand
        // (Anwender 27.10.2026); das Lauflicht bleibt.
        double w = bahnhofGbf ? STAERKEN[STAERKEN.length - 1] : farbeVon.getLinienStaerke();
        double luecke = w * 0.6 + 1.5;
        double nx = -(by - ay) / len * (w + luecke) / 2;
        double ny = (bx - ax) / len * (w + luecke) / 2;
        Color farbe = bahnhofGbf ? SILBER : symbolFarbe(farbeVon);
        Group g = new Group();
        g.setMouseTransparent(true);
        String key = schluessel(station, anderes);
        Spur[] paar = new Spur[2];
        for (int i = 0; i < 2; i++) {
            double ox = i == 0 ? nx : -nx;
            double oy = i == 0 ? ny : -ny;
            if (bahnhofGbf) {
                Line rand = new Line(ax + ox, ay + oy, bx + ox, by + oy);
                rand.setStrokeWidth(w + 2);
                rand.setStroke(Color.BLACK);
                rand.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
                g.getChildren().add(rand);
            }
            Line spurLinie = new Line(ax + ox, ay + oy, bx + ox, by + oy);
            spurLinie.setStrokeWidth(w);
            spurLinie.setStroke(farbe);
            spurLinie.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            g.getChildren().add(spurLinie);
            // Grün läuft von B zur Station (A), Rot von der Station weg.
            double sx = i == 0 ? bx + ox : ax + ox;
            double sy = i == 0 ? by + oy : ay + oy;
            double ex = i == 0 ? ax + ox : bx + ox;
            double ey = i == 0 ? ay + oy : by + oy;
            double zoom = Math.max(0.2, contentPane.getScaleX());
            double segLaenge = Math.max(w * 3, 18 / zoom);
            int n = Math.max(1, (int) Math.floor(len / segLaenge));
            Line[] segmente = new Line[n];
            for (int k = 0; k < n; k++) {
                double t0 = (k + 0.2) / n;
                double t1 = (k + 0.8) / n;
                Line seg = new Line(sx + (ex - sx) * t0, sy + (ey - sy) * t0, sx + (ex - sx) * t1, sy + (ey - sy) * t1);
                // etwas breiter als die Spur, damit das Segment deutlich zu sehen ist
                seg.setStrokeWidth(Math.max(2.5, w + 1.5));
                seg.setStroke(i == 0 ? GRUEN : ROT);
                seg.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.BUTT);
                seg.setVisible(false);
                segmente[k] = seg;
                g.getChildren().add(seg);
            }
            SpurZustand z = spurZustaende.computeIfAbsent(key + (i == 0 ? "|g" : "|r"), x -> new SpurZustand());
            paar[i] = new Spur(z, sx, sy, ex, ey, segmente);
        }
        spuren.put(key, paar);
        // Rechteckiger, deckender Abschluss an beiden Enden in der Linienfarbe (Anwender 11.10.2026),
        // mit der langen Seite entlang der Rahmenkante.
        double breite = 2 * w + luecke;
        g.getChildren().add(abschluss(ax, ay, aBox, breite, farbe));
        g.getChildren().add(abschluss(bx, by, bBox, breite, farbe));
        if (bahnhofGbf) {
            for (Node n : g.getChildren().subList(g.getChildren().size() - 2, g.getChildren().size())) {
                ((javafx.scene.shape.Rectangle) n).setStroke(Color.BLACK);
                ((javafx.scene.shape.Rectangle) n).setStrokeWidth(1);
            }
        }
        linienEbene.getChildren().add(g);
        if (interaktiv && bahnhofGbf) {
            Line treffer = new Line(ax, ay, bx, by);
            treffer.setStrokeWidth(2 * w + luecke + 8);
            treffer.setStroke(Color.TRANSPARENT);
            javafx.scene.control.Tooltip.install(treffer, new javafx.scene.control.Tooltip(
                    "Verbindung " + anderes.getAnzeigeName() + " – zugehöriger " + station.getAnzeigeName()
                            + " · grün: zum Güterbahnhof · rot: von ihm weg"));
            linienEbene.getChildren().add(treffer);
        } else if (interaktiv) {
            Line treffer = new Line(ax, ay, bx, by);
            treffer.setStrokeWidth(Math.max(12, 2 * w + luecke + 8));
            treffer.setStroke(Color.TRANSPARENT);
            treffer.setStyle("-fx-cursor: hand;");
            treffer.setOnContextMenuRequested(e -> {
                ContextMenu menu = new ContextMenu();
                for (int staerke : STAERKEN) {
                    javafx.scene.control.RadioMenuItem mi = new javafx.scene.control.RadioMenuItem();
                    mi.setGraphic(linienMuster(staerke, farbe));
                    mi.setSelected(Math.abs(farbeVon.getLinienStaerke() - staerke) < 0.01);
                    mi.setOnAction(ev -> {
                        farbeVon.setLinienStaerke(staerke);
                        zeichneVerbindungen();
                        geaendert();
                    });
                    menu.getItems().add(mi);
                }
                menu.show(treffer, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            javafx.scene.control.Tooltip.install(treffer, new javafx.scene.control.Tooltip(
                    "Verbindung " + station.getAnzeigeName() + " – " + anderes.getAnzeigeName()
                            + " · grün: zur Station · rot: von der Station weg · Rechtsklick: Linienstärke"));
            linienEbene.getChildren().add(treffer);
        }
    }

    /** Kurzes Beispiel einer Doppellinie in der gewählten Stärke (Menü "Linienstärke"). */
    private static Node linienMuster(double w, Color farbe) {
        double luecke = w * 0.6 + 1.5;
        double d = (w + luecke) / 2;
        Line a = new Line(0, 0, 60, 0);
        Line b = new Line(0, 2 * d, 60, 2 * d);
        for (Line l : List.of(a, b)) {
            l.setStrokeWidth(w);
            l.setStroke(farbe);
            l.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        }
        Line gruen = new Line(10, 0, 18, 0);
        gruen.setStrokeWidth(Math.max(1, w * 0.8));
        gruen.setStroke(GRUEN);
        Line rot = new Line(36, 2 * d, 44, 2 * d);
        rot.setStrokeWidth(Math.max(1, w * 0.8));
        rot.setStroke(ROT);
        Group g = new Group(a, b, gruen, rot);
        StackPane halter = new StackPane(g);
        halter.setPadding(new Insets(4, 6, 4, 6));
        return halter;
    }

    /**
     * Je Rahmen 9 Andockpunkte (Ecken, Seitenmitten, Mitte); die Linie verbindet das nächstgelegene
     * Paar und dockt beim Verschieben automatisch um. Ergebnis: x1, y1, x2, y2.
     */
    static double[] andockPunkte(Region a, Region b) {
        double[][] pa = punkte(a);
        double[][] pb = punkte(b);
        double[] best = null;
        double min = Double.MAX_VALUE;
        for (double[] p : pa) {
            for (double[] q : pb) {
                double d = (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]);
                if (d < min - 1e-6) {
                    min = d;
                    best = new double[]{p[0], p[1], q[0], q[1]};
                }
            }
        }
        return best;
    }

    private static double[][] punkte(Region r) {
        double w = r.getWidth() > 0 ? r.getWidth() : 120;
        double h = r.getHeight() > 0 ? r.getHeight() : 90;
        double x = r.getLayoutX();
        double y = r.getLayoutY();
        // Nur die Mitten der vier Seiten (Anwender 11.10.2026)
        return new double[][]{{x + w / 2, y}, {x + w, y + h / 2}, {x + w / 2, y + h}, {x, y + h / 2}};
    }

    // ------------------------------------------------------------------ Schilder

    private List<Node> schilder(AnlagenObjekt o) {
        List<Node> l = new ArrayList<>();
        if (o.isPausiert()) {
            // Pause nur dieses Objekts (Schalter im Detailfenster), Anwender 14.10.2026
            Label p = new Label("❚❚ pausiert");
            p.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-color: #d03030;"
                    + " -fx-background-radius: 4; -fx-padding: 1 6 1 6;");
            p.setMinWidth(Region.USE_PREF_SIZE);
            javafx.scene.control.Tooltip.install(p, Symbole.tip("Nur dieses Objekt ist angehalten; alles andere läuft weiter. "
                    + "Fortsetzen im Detailfenster mit „▶ Start“."));
            HBox zeile = new HBox(p);
            zeile.setAlignment(Pos.CENTER);
            l.add(zeile);
        }
        if (echt(o) instanceof com.example.itrain_logistik.kern.Reiter r) {
            l.add(Symbole.badge(new Label(""), "Bahnhöfe: " + r.getStationen().size(), Symbole.Pfeil.KEINER,
                    "Bahnhöfe, die an diesem Tab hängen (mit ihren Güterbahnhöfen, Orten, Betrieben, Lagern)"));
            l.add(Symbole.badge(new Label(""), o instanceof ReiterAnker ? "↖ " + anlage.getHauptReiterName()
                    : "Reiter „" + r.getAnzeigeName() + "“", Symbole.Pfeil.KEINER,
                    o instanceof ReiterAnker ? "Dieses Symbol hängt im ersten Reiter an der Anlagen-Infrastruktur"
                            : "Eigener Reiter mit den angehängten Bahnhöfen"));
        }
        if (echt(o) instanceof Modul m) {
            int st = 0;
            for (Station s : anlage.stationen()) {
                if (anlage.gehoertZu(s) == null && anlage.modulFuer(s) == m) {
                    st++;
                }
            }
            l.add(Symbole.badge(new Label(""), "Stationen: " + st, Symbole.Pfeil.KEINER,
                    "Stationen, die an diesem Modul hängen (mit ihren Güterbahnhöfen, Orten, Betrieben, Lagern)"));
            l.add(Symbole.badge(new Label(""), "Blöcke: " + m.getBloecke().size(), Symbole.Pfeil.KEINER,
                    "Strecken-Blöcke des Moduls"));
            l.add(Symbole.badge(new Label(""), m.isImBrowser() ? "Browser: /modul/" + Webansicht.pfad(m) : "Browser: aus",
                    Symbole.Pfeil.KEINER, "Anzeige im Browser auf einem anderen Rechner, Handy oder Tablet"));
        }
        if (o instanceof Infrastruktur) {
            // Blockzählung (Anwender 20.10.2026): alle Blöcke der .tcdz, davon in Bahnhöfen (einem Gleis einer
            // Station zugeordnet) und Strecke (keinem Gleis zugeordnet).
            int alle = anlage.getBloecke().size();
            int frei = anlage.freieBloecke().size();
            l.add(Symbole.badge(new Label(""), "Anzahl Blöcke: " + alle, Symbole.Pfeil.KEINER,
                    "Alle Blöcke der .tcdz"));
            l.add(Symbole.badge(new Label(""), "In Bahnhöfen: " + (alle - frei), Symbole.Pfeil.KEINER,
                    "Blöcke, die einem Gleis einer Station zugeordnet sind"));
            l.add(Symbole.badge(new Label(""), "Strecke: " + frei, Symbole.Pfeil.KEINER,
                    "Blöcke, die keinem Gleis einer Station zugeordnet sind"));
        } else if (o instanceof Station s) {
            // Abschnitte mit Überschrift und Trennstrich (Anwender 12.10.2026)
            List<Gleis> bahnsteige = s.gleiseDerArt(GleisArt.BAHNSTEIG);
            List<Gleis> ladegleise = s.gleiseDerArt(GleisArt.LADEGLEIS);
            if (!bahnsteige.isEmpty()) {
                List<Node> zeilen = new ArrayList<>();
                for (Gleis g : bahnsteige) {
                    zeilen.add(gleisZeile(s, g));
                }
                abschnitt(l, "Personen", zeilen);
            }
            if (!ladegleise.isEmpty()) {
                List<Node> zeilen = new ArrayList<>();
                for (Gleis g : ladegleise) {
                    zeilen.add(gleisZeile(s, g));
                }
                abschnitt(l, "Güter", zeilen);
            }
            if (s.getGleise().isEmpty() && interaktiv) {
                l.add(hinweis("keine Gleise"));
            }
            // Den Bedarf der Stadt zeigt die Stadt selbst; der Güterbahnhof nicht noch einmal (Anwender 27.10.2026).
            Node transport = herstellung(s);
            if (transport != null) {
                abschnitt(l, "Güter zum Transport", List.of(transport));
            }
            List<String> erschoepft = kapazitaetErschoepft(s);
            if (!erschoepft.isEmpty()) {
                Label z = hinweis("Lager hinzufügen. Die Kapazitäten am Bahnhof sind erschöpft");
                z.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #cc3333;");
                z.setWrapText(true);
                z.setMaxWidth(260);
                javafx.scene.control.Tooltip.install(z, Symbole.tip("Zu wenig Platz an den Ladegleisen für: "
                        + String.join(", ", erschoepft) + ".\nEin Lager an diesem Bahnhof nimmt auf, was die Gleise "
                        + "nicht halten können, und gibt es nach und nach weiter."));
                l.add(z);
            }
            // Zug am Ladegleis wird seine Ladung nicht los (Anwender 10.11.2026: Dünger, Bauernhof voll)
            for (String text : nichtEntladbar(s)) {
                Label z = hinweis(text);
                z.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #cc3333;");
                z.setWrapText(true);
                z.setMaxWidth(260);
                l.add(z);
            }
        } else if (o instanceof Betrieb b) {
            FlowPane bedarf = flow();
            for (String gutId : b.eingangsGueter()) {
                bedarf.getChildren().add(Symbole.gutBadge(anlage.gut(gutId), anlage.immerGeliefert(b, gutId) ? "∞"
                        : Symbole.zahl(b.getBestandEin().getOrDefault(gutId, 0.0)), Symbole.Pfeil.KEINER));
            }
            if (!bedarf.getChildren().isEmpty()) {
                abschnitt(l, "Güter Bedarf", List.of(bedarf));
            }
            FlowPane herstellung = flow();
            for (String gutId : b.ausgangsGueter()) {
                herstellung.getChildren().add(Symbole.gutBadge(anlage.gut(gutId),
                        Symbole.zahl(b.getBestandAus().getOrDefault(gutId, 0.0)), Symbole.Pfeil.KEINER));
            }
            if (!herstellung.getChildren().isEmpty()) {
                abschnitt(l, "Güter Herstellung", List.of(herstellung));
            }
            com.example.itrain_logistik.kern.Bewertung.Ergebnis bw = com.example.itrain_logistik.kern.Bewertung.betrieb(anlage, b);
            if (!bw.leer()) {
                FlowPane f = flow();
                f.getChildren().add(Symbole.bewertungBadge(bw.prozent(), false,
                        "Erfüllung: Anlieferung der Eingänge und Abholung der Herstellung – Klick zeigt die Bewertung"));
                abschnitt(l, "Erfüllung", List.of(f));
            }
            if (b.isAnpassen()) {
                Label st = hinweis("Produktion ×" + b.stufenFaktor());
                st.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #1f7a3a;");
                l.add(st);
            }
            if (b.getZustand() != null && !b.getZustand().isEmpty() && !b.getZustand().startsWith("Läuft")) {
                Label z = hinweis(b.getZustand());
                z.setStyle("-fx-font-size: 10px; -fx-text-fill: #cc3333;");
                l.add(z);
            }
        } else if (o instanceof Ort ort) {
            // Ohne Passagieraufkommen keine Personen-Schilder. Wartende gibt es nur am Bahnhof; der Ort zeigt
            // die Reisenden, die noch zum Bahnhof gehen (Anwender 27.10.2026).
            if (ort.getPassagiereProStunde() > 0) {
                FlowPane f = flow();
                f.getChildren().add(Symbole.personBadge(Symbole.zahl(Math.floor(ort.getReisende() + 1e-9)),
                        reisendeText(ort)));
                Label ankunft = new Label("↘");
                ankunft.setStyle(Symbole.textStil("white", 11));
                f.getChildren().add(Symbole.badge(ankunft, "Ank. " + Symbole.zahl(ort.getAnkommendDieseStunde()),
                        Symbole.Pfeil.KEINER, "Ankommende Passagiere in dieser Modellstunde"));
                Node zuf = zufriedenheitSchild(ort);
                if (zuf != null) {
                    f.getChildren().add(zuf);
                }
                abschnitt(l, "Personen", List.of(f));
            } else {
                Node zuf = zufriedenheitSchild(ort);
                if (zuf != null) {
                    FlowPane f = flow();
                    f.getChildren().add(zuf);
                    abschnitt(l, "Zufriedenheit", List.of(f));
                }
            }
            FlowPane bedarf = flow();
            for (Map.Entry<String, Double> e : ort.getBedarf().entrySet()) {
                if (e.getValue() > 0) {
                    bedarf.getChildren().add(Symbole.gutBadge(anlage.gut(e.getKey()),
                            Symbole.zahl(ort.getOffenerBedarf().getOrDefault(e.getKey(), 0.0))
                                    + (ort.bedarfStufe(e.getKey()) > 0 ? " ×" + (1 + ort.bedarfStufe(e.getKey())) : ""),
                            Symbole.Pfeil.KEINER));
                }
            }
            if (!bedarf.getChildren().isEmpty()) {
                List<Node> teile = new ArrayList<>(List.of(bedarf));
                if (anlage.gueterStationenFuer(ort.getId()).isEmpty()) {
                    Label z = hinweis("kein Güterbahnhof – Güter kommen nicht an");
                    z.setStyle("-fx-font-size: 10px; -fx-text-fill: #cc3333;");
                    teile.add(z);
                }
                abschnitt(l, "Güter Bedarf", teile);
            }
            Map<String, Double> weg = ort.getVernichtetHeute(kern != null ? kern.getTag() : -1);
            if (!weg.isEmpty()) {
                FlowPane f = flow();
                weg.forEach((g, m) -> {
                    if (m > 0.01) {
                        f.getChildren().add(Symbole.gutBadge(anlage.gut(g), Symbole.bestand(m), Symbole.Pfeil.KEINER));
                    }
                });
                if (!f.getChildren().isEmpty()) {
                    abschnitt(l, "Überschuss vernichtet (heute)", List.of(f));
                }
            }
        } else if (o instanceof Lager lager) {
            // Nur die Güter, die wirklich eingelagert sind.
            FlowPane f = flow();
            for (Map.Entry<String, Double> e : lager.getBestand().entrySet()) {
                if (e.getValue() > 0.01) {
                    f.getChildren().add(Symbole.gutBadge(anlage.gut(e.getKey()), Symbole.bestand(e.getValue()),
                            Symbole.Pfeil.KEINER));
                }
            }
            if (f.getChildren().isEmpty()) {
                f.getChildren().add(hinweis("leer"));
            }
            abschnitt(l, "Eingelagert " + Symbole.bestand(lager.summe()) + "/" + lager.getKapazitaet(), List.of(f));
            lagerHinweis(l, "Güter können nicht verbraucht werden", lager.getUnverbrauchbar(), lager);
            lagerHinweis(l, "Güter zurück zum Empfänger, bitte abholen", lager.getZurueck(), null);
        } else if (o instanceof Uebergabe u) {
            FlowPane herstellung = flow();
            for (String g : u.getAngebot()) {
                herstellung.getChildren().add(Symbole.gutBadge(anlage.gut(g), "∞", Symbole.Pfeil.KEINER));
            }
            if (!herstellung.getChildren().isEmpty()) {
                abschnitt(l, "Güter Herstellung", List.of(herstellung));
            }
            FlowPane bedarf = flow();
            if (u.isAnnahmeAlles()) {
                bedarf.getChildren().add(hinweis("nimmt alles ab"));
            } else {
                for (String g : u.getAnnahme()) {
                    bedarf.getChildren().add(Symbole.gutBadge(anlage.gut(g), "∞", Symbole.Pfeil.KEINER));
                }
            }
            if (!bedarf.getChildren().isEmpty()) {
                abschnitt(l, "Güter Bedarf", List.of(bedarf));
            }
        }
        return l;
    }

    /**
     * Güter, die an der Station zur Abholung bereitliegen (Summe über die Ladegleise) bzw. von Betrieben,
     * Übergaben oder dem Schattenbahnhof dort bereitgestellt werden; Kasten mit ▼, solange geladen wird.
     */
    private Node herstellung(Station s) {
        List<Gleis> ladegleise = s.gleiseDerArt(GleisArt.LADEGLEIS);
        if (ladegleise.isEmpty()) {
            return null;
        }
        Map<String, Double> bereit = new java.util.LinkedHashMap<>();
        Map<String, Double> platz = new java.util.HashMap<>();
        Set<String> laedt = new java.util.HashSet<>();
        for (Gleis g : ladegleise) {
            g.getAngebot().keySet().forEach(id -> bereit.putIfAbsent(id, 0.0));
            g.getAusgang().forEach((id, m) -> bereit.merge(id, m, Double::sum));
            laedt.addAll(g.getAktivLaden());
        }
        for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
            if (o instanceof Betrieb b) {
                b.ausgangsGueter().forEach(id -> bereit.putIfAbsent(id, 0.0));
            } else if (o instanceof Uebergabe u) {
                u.getAngebot().forEach(id -> bereit.putIfAbsent(id, 0.0));
            }
        }
        if (bereit.isEmpty()) {
            return null;
        }
        FlowPane f = flow();
        f.setPrefWrapLength(330);
        f.setMaxWidth(340);
        // Menge, die zur Abholung bereitsteht / Lagerkapazität am Gbf: je Gut die Kapazität der Ladegleise, die es
        // annehmen (Anwender 27.10.2026). Ein angeschlossenes Lager zeigt seine Kapazität selbst.
        for (String id : bereit.keySet()) {
            for (Gleis g : ladegleise) {
                if (g.getGueterAuswahl().isEmpty() || g.getGueterAuswahl().contains(id)) {
                    platz.merge(id, (double) g.getKapazitaet(), Double::sum);
                }
            }
        }
        bereit.forEach((g, m) -> {
            Node b = Symbole.gutBadge(anlage.gut(g), Symbole.zahl(Math.floor(m + 1e-6))
                    + (platz.containsKey(g) ? "/" + Symbole.zahl(platz.get(g)) : ""),
                    laedt.contains(g) ? Symbole.Pfeil.LADEN : Symbole.Pfeil.LEER);
            javafx.scene.control.Tooltip.install(b, Symbole.tip(anlage.gutName(g)
                    + ": bereit zum Transport / Lagerkapazität am Güterbahnhof"));
            f.getChildren().add(b);
        });
        return f;
    }

    /**
     * Güter, für die die Ladegleise einer Station nicht reichen, solange kein Lager angeschlossen ist (Anwender
     * 31.10.2026, nur Güter): ein Gleis ist mit Herstellung voll und es wartet noch mehr, oder der offene Bedarf eines
     * angeschlossenen Orts ist größer als der Platz an den Gleisen.
     */
    private List<String> kapazitaetErschoepft(Station s) {
        List<Gleis> ladegleise = s.gleiseDerArt(GleisArt.LADEGLEIS);
        if (ladegleise.isEmpty() || anlage.istAussenwelt(s)) {
            return List.of();
        }
        List<AnlagenObjekt> bereich = anlage.einzugsbereich(s);
        if (bereich.stream().anyMatch(o -> o instanceof Lager)) {
            return List.of();
        }
        Map<String, Boolean> gueter = new java.util.LinkedHashMap<>();
        for (AnlagenObjekt o : bereich) {
            if (o instanceof Betrieb b) {
                for (String g : b.ausgangsGueter()) {
                    double platz = 0;
                    double liegt = 0;
                    for (Gleis gl : ladegleise) {
                        if (gl.erlaubt(g)) {
                            platz += gl.getKapazitaet();
                            liegt += gl.getAusgang().getOrDefault(g, 0.0);
                        }
                    }
                    if (platz > 0 && liegt >= platz - 1e-6 && b.getBestandAus().getOrDefault(g, 0.0) >= 1) {
                        gueter.put(g, true);
                    }
                }
            } else if (o instanceof Ort ort) {
                for (Map.Entry<String, Double> e : ort.getOffenerBedarf().entrySet()) {
                    double platz = 0;
                    for (Gleis gl : ladegleise) {
                        if (gl.erlaubt(e.getKey()) && gl.isEntladen()) {
                            platz += gl.getKapazitaet();
                        }
                    }
                    if (platz > 0 && e.getValue() > platz + 1e-6) {
                        gueter.put(e.getKey(), true);
                    }
                }
            }
        }
        return gueter.keySet().stream().map(anlage::gutName).toList();
    }

    /**
     * Hinweise zu Zügen an den Ladegleisen der Station, die Güter an Bord haben, die hier nicht abgeladen werden:
     * weil die Abnehmer voll sind (dann hilft ein Lager) oder weil es an diesem Bahnhof keinen Abnehmer gibt.
     */
    private List<String> nichtEntladbar(Station s) {
        if (kern == null || anlage.istAussenwelt(s)) {
            return List.of();
        }
        boolean lager = anlage.einzugsbereich(s).stream().anyMatch(o -> o instanceof Lager);
        List<String> voll = new ArrayList<>();
        List<String> ohne = new ArrayList<>();
        for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
            ZugZustand z = g.getZugImGleis() != null ? anlage.getZuege().get(g.getZugImGleis()) : null;
            if (z == null || !g.isEntladen()) {
                continue;
            }
            for (Map.Entry<String, Double> e : z.ladungJeGut().entrySet()) {
                String gutId = e.getKey();
                if (e.getValue() < 1 || !g.erlaubt(gutId) || g.isEntladenErzwingen()) {
                    continue;
                }
                // Was der Zug hier geladen hat bzw. was dieser Bahnhof selbst verschickt, soll gar nicht hier
                // entladen werden (Anwender 10.11.2026: Möbel der Möbelfabrik am CS Gbf).
                if (z.getGeladenIn().get(gutId) == s
                        || g.getAusgang().containsKey(gutId) || anlage.einzugsbereich(s).stream()
                                .anyMatch(o -> o instanceof Betrieb b && b.ausgangsGueter().contains(gutId))) {
                    continue;
                }
                String name = anlage.gutName(gutId);
                if (!kern.hatAbnehmer(s, gutId)) {
                    if (!ohne.contains(name)) {
                        ohne.add(name);
                    }
                } else if (kern.aufnahme(s, gutId) - g.getEingang().getOrDefault(gutId, 0.0) < 1 && !voll.contains(name)) {
                    voll.add(name);
                }
            }
        }
        List<String> texte = new ArrayList<>();
        if (!voll.isEmpty()) {
            texte.add((lager ? "Lager voll. " : "Lager hinzufügen. ") + String.join(", ", voll)
                    + " kann nicht entladen werden, die Abnehmer sind voll");
        }
        if (!ohne.isEmpty()) {
            texte.add("Kein Abnehmer an diesem Bahnhof für: " + String.join(", ", ohne));
        }
        return texte;
    }

    /** Fragt je Gut ohne Verbraucher, ob es zum Verladen bereitgestellt werden soll (Abbruch / OK). */
    private void bereitstellenFragen(Lager lager, Set<String> gueter) {
        javafx.scene.control.ButtonType ok = new javafx.scene.control.ButtonType("OK",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        for (String g : List.copyOf(gueter)) {
            List<String> wo = new ArrayList<>();
            for (Betrieb b : anlage.betriebe()) {
                if (b.eingangsGueter().contains(g) && !b.getImmerGeliefert().contains(g)) {
                    wo.add("„" + b.getAnzeigeName() + "“");
                }
            }
            for (Ort o : anlage.orte()) {
                if (o.getBedarf().getOrDefault(g, 0.0) > 0) {
                    wo.add("„" + o.getAnzeigeName() + "“");
                }
            }
            String text = wo.isEmpty() ? anlage.gutName(g) + " wird auf der Anlage nirgends benötigt.\nTrotzdem zum Verladen bereitstellen?"
                    : anlage.gutName(g) + " wird in " + String.join(", ", wo) + " benötigt.\nZum Verladen bereitstellen?";
            javafx.scene.control.Alert frage = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.CONFIRMATION, text, DialogKnoepfe.ABBRUCH, ok);
            frage.setHeaderText(lager.getAnzeigeName() + ": " + anlage.gutName(g));
            frage.setGraphic(Symbole.gut(anlage.gut(g), 28));
            if (fenster() != null) {
                frage.initOwner(fenster());
            }
            Node leiste = frage.getDialogPane().lookup(".button-bar");
            if (leiste instanceof javafx.scene.control.ButtonBar bar) {
                bar.setButtonOrder(javafx.scene.control.ButtonBar.BUTTON_ORDER_NONE);
            }
            if (frage.showAndWait().orElse(DialogKnoepfe.ABBRUCH) == ok) {
                lager.getBereitgestellt().add(g);
                lager.getUnverbrauchbar().remove(g);
                geaendert();
            }
        }
        aktualisiere();
    }

    /** Roter Hinweis im Lager mit den betroffenen Gütern (Anwender 12.10.2026). */
    private void lagerHinweis(List<Node> l, String text, Set<String> gueter, Lager lager) {
        if (gueter.isEmpty()) {
            return;
        }
        Label z = hinweis(text);
        z.setStyle("-fx-font-size: 10px; -fx-text-fill: #cc3333; -fx-font-weight: bold;");
        z.setWrapText(true);
        if (lager != null) {
            // Klick auf den Hinweis: wer braucht das Gut, zum Verladen bereitstellen? (Anwender 12.10.2026)
            z.setStyle(z.getStyle() + " -fx-underline: true; -fx-cursor: hand;");
            javafx.scene.control.Tooltip.install(z, Symbole.tip("Klicken: Gut zum Verladen bereitstellen"));
            z.setOnMousePressed(javafx.scene.input.MouseEvent::consume);
            z.setOnMouseClicked(e -> {
                e.consume();
                bereitstellenFragen(lager, gueter);
            });
        }
        FlowPane f = flow();
        for (String g : gueter) {
            f.getChildren().add(Symbole.gut(anlage.gut(g), 14));
        }
        l.add(z);
        l.add(f);
    }

    /**
     * Abschnitt eines Symbols: Überschrift links ("Personen", "Güter Bedarf", "Güter Herstellung" …), darunter
     * der Inhalt; vor jedem weiteren Abschnitt ein Trennstrich (Anwender 12.10.2026).
     */
    private static void abschnitt(List<Node> l, String titel, List<Node> inhalt) {
        if (!l.isEmpty()) {
            Separator strich = new Separator();
            strich.setPadding(new Insets(3, 0, 1, 0));
            l.add(strich);
        }
        Label t = new Label(titel);
        t.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-opacity: 0.7;");
        t.setMaxWidth(Double.MAX_VALUE);
        t.setAlignment(Pos.CENTER_LEFT);
        l.add(t);
        l.addAll(inhalt);
    }

    /** Tooltip der Reisenden eines Orts: gehen gesammelt zu den Bahnhöfen, die den Ort bedienen. */
    /** Schild „Zufriedenheit“ eines Orts (Anwender 11.11.2026), null ohne Bedarf und Reisende. */
    private Node zufriedenheitSchild(Ort ort) {
        com.example.itrain_logistik.kern.Bewertung.Ergebnis e = com.example.itrain_logistik.kern.Bewertung.ort(anlage, ort);
        return e.leer() ? null : Symbole.bewertungBadge(e.prozent(), true,
                "Zufriedenheit: Anlieferung des Bedarfs und Abholung der Reisenden – Klick zeigt die Bewertung");
    }

    private String reisendeText(Ort ort) {
        java.util.Set<String> bahnhoefe = new LinkedHashSet<>();
        for (Station s : anlage.stationenFuer(ort.getId())) {
            if (!s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()) {
                bahnhoefe.add(s.getAnzeigeName());
            }
        }
        String text = "Reisende: wollen verreisen und gehen alle "
                + Symbole.zahl(Ort.SAMMEL_SEKUNDEN / 60) + " Modellminuten gesammelt zum Bahnhof (grünes Lauflicht)";
        return bahnhoefe.isEmpty() ? text + "\nKein Bahnhof mit Bahnsteig angeschlossen"
                : text + "\nBahnhof: " + String.join(", ", bahnhoefe);
    }

    /** Breite der Namensspalte einer Station: längster Gleisname, damit die Werte aller Zeilen fluchten. */
    private static double nameBreite(Station s) {
        double max = 0;
        for (Gleis g : s.getGleise()) {
            javafx.scene.text.Text t = new javafx.scene.text.Text(g.getAnzeigeName());
            t.setFont(Font.font(10));
            max = Math.max(max, t.getLayoutBounds().getWidth());
        }
        return Math.ceil(max) + 6;
    }

    /**
     * Eine Gleiszeile (Anwender 12.10.2026): links bündig der Blockname, in der Mitte die Wartenden bzw. die
     * Güter am Gleis. Steht ein Zug am Gleis, umrahmt ein gelber Rahmen die Wartenden zusammen mit dem Zug
     * (Name, Belegung/Ladung, blinkende Dreiecke) in derselben Zeile; der Kasten ändert dabei seine Größe nie,
     * was nicht passt, wird abgeschnitten (Anwender 14.10.2026).
     */
    private Node gleisZeile(Station s, Gleis g) {
        double breite = nameBreite(s);
        Label name = new Label(g.getAnzeigeName());
        name.setStyle("-fx-font-size: 10px; -fx-opacity: 0.85;");
        name.setMinWidth(breite);
        name.setPrefWidth(breite);
        name.setAlignment(Pos.CENTER_LEFT);
        List<Node> werte = new ArrayList<>();
        // Schattenbahnhof wie ein normaler Bahnhof (Anwender 27.10.2026): Wartende, Zug am Gleis, Ein-/Aussteigen,
        // Güterverladung; bei Reisenden von außerhalb zusätzlich die Menge je Modellstunde.
        boolean aussenwelt = false;
        if (g.getArt() == GleisArt.BAHNSTEIG && anlage.reisendeVonAussen(s)) {
            for (String r : g.warteschlangen()) {
                werte.add(Symbole.personBadge((r.isEmpty() ? "" : "→" + r + " ") + Symbole.zahl(g.wartendeSumme(r))
                        + klassen(g.wartendeIn(r)), (r.isEmpty() ? "Wartende Personen" : "Wartende Personen Richtung " + r)
                        + " (1. Klasse / 2. Klasse)"));
            }
            werte.add(Symbole.personBadge(Symbole.zahl(g.getPassagiereProStunde()) + "/h",
                    "Personen je Modellstunde von außerhalb"));
        } else if (g.getArt() == GleisArt.BAHNSTEIG) {
            for (String r : g.warteschlangen()) {
                werte.add(Symbole.personBadge((r.isEmpty() ? "" : "→" + r + " ") + Symbole.zahl(g.wartendeSumme(r))
                        + klassen(g.wartendeIn(r)), (r.isEmpty() ? "Wartende Personen" : "Wartende Personen Richtung " + r)
                        + " (1. Klasse / 2. Klasse)"));
            }
        } else {
            // Nur die Symbole der zugeordneten bzw. am Gleis liegenden Güter, ohne Zahlen und Dreiecke
            // (Anwender 12.10.2026); Mengen und Lade-Dreiecke stehen in den Abschnitten darunter.
            // Die Symbole bedeuten: an diesem Gleis werden diese Güter angenommen und verladen (Güterauswahl);
            // ohne Auswahl alle. Was zur Abholung bereitsteht, zeigt der Abschnitt "Güter zum Transport".
            java.util.Set<String> gezeigt = new java.util.LinkedHashSet<>(g.getGueterAuswahl());
            gezeigt.addAll(g.getAngebot().keySet()); // Schattenbahnhof: was er bereitstellt
            for (String gutId : gezeigt) {
                Node symbol = Symbole.gut(anlage.gut(gutId), 14);
                javafx.scene.control.Tooltip.install(symbol, Symbole.tip("An diesem Gleis angenommen und verladen: "
                        + anlage.gutName(gutId)));
                werte.add(symbol);
            }
        }
        // Bahnsteig „Mit Koffer“: wartende Kofferreisende als Koffer-Symbol mit Zahl (Anwender 27.10.2026)
        java.util.function.Supplier<Node> koffer = () -> {
            Node b = Symbole.gutBadge(anlage.gut(Gleis.KOFFER_GUT), Symbole.zahl(Math.floor(g.getKofferWartend() + 1e-6)),
                    Symbole.Pfeil.KEINER);
            javafx.scene.control.Tooltip.install(b, Symbole.tip("Mit Koffer: davon warten so viele mit Koffer "
                    + "(brauchen einen Gepäckwagen im Zug)"));
            return b;
        };
        boolean kofferZeigen = g.getArt() == GleisArt.BAHNSTEIG && g.isMitKoffer() && anlage.gut(Gleis.KOFFER_GUT) != null;
        if (kofferZeigen) {
            werte.add(koffer.get());
        }
        // Post und Expressgut, die am Bahnsteig auf einen Personenzug warten (Anwender 27.10.2026)
        if (g.getArt() == GleisArt.BAHNSTEIG) {
            for (String gutId : List.of(com.example.itrain_logistik.kern.Tf3Daten.POST,
                    com.example.itrain_logistik.kern.Tf3Daten.EXPRESSGUT)) {
                double n = Math.floor(g.getAusgang().getOrDefault(gutId, 0.0) + 1e-6);
                if (n >= 1 && anlage.gut(gutId) != null) {
                    werte.add(Symbole.gutBadge(anlage.gut(gutId), Symbole.zahl(n), Symbole.Pfeil.KEINER));
                }
            }
        }
        ZugZustand zug = g.getZugImGleis() != null && kern != null ? anlage.getZuege().get(g.getZugImGleis()) : null;
        HBox platzhalter = null;
        if (zug != null && g.getArt() == GleisArt.BAHNSTEIG && !aussenwelt) {
            // Mit Zug am Bahnsteig nur noch Personensymbol und Summe der Wartenden; wo die Richtung stand,
            // steht jetzt der Zug (Anwender 14.10.2026). Die Richtungen stehen im Tooltip.
            double summe = 0;
            double[] kl = new double[2];
            List<String> teile = new ArrayList<>();
            for (String r : g.warteschlangen()) {
                summe += g.wartendeSumme(r);
                kl[0] += g.wartendeIn(r)[0];
                kl[1] += g.wartendeIn(r)[1];
                teile.add((r.isEmpty() ? "" : "→" + r + " ") + Symbole.zahl(g.wartendeSumme(r)));
            }
            // Platzhalter mit der bisherigen Anzeige (unsichtbar): hält die Breite, damit der Kasten gleich bleibt.
            platzhalter = new HBox(4);
            platzhalter.getChildren().addAll(werte);
            platzhalter.setPadding(new Insets(1, 4, 1, 4));
            platzhalter.setStyle("-fx-border-color: transparent; -fx-border-width: 1.5;");
            platzhalter.setMaxWidth(Region.USE_PREF_SIZE);
            platzhalter.setVisible(false);
            werte.clear();
            werte.add(Symbole.personBadge(Symbole.zahl(summe) + klassen(kl), "Wartende Personen (1. Klasse / 2. Klasse): "
                    + String.join(", ", teile)));
            if (kofferZeigen) {
                werte.add(koffer.get());
            }
        }
        HBox mitte = new HBox(4);
        mitte.setAlignment(Pos.CENTER_LEFT);
        mitte.getChildren().addAll(werte);
        // Gleicher (ggf. unsichtbarer) Rahmen in allen Zeilen, damit die Wartenden genau untereinander stehen.
        mitte.setPadding(new Insets(1, 4, 1, 4));
        if (zug != null) {
            // Der Zug nimmt nur den Platz, den der Kasten ohnehin hat: er trägt nichts zur Breite bei und wird
            // bei Bedarf abgeschnitten; mehr Platz gibt es durch Verbreitern des Kastens (Anwender 14.10.2026).
            HBox zugBox = new HBox(4);
            zugBox.setAlignment(Pos.CENTER_LEFT);
            zugBox.getChildren().addAll(zugTeile(zug, g));
            for (Node n : zugBox.getChildren()) {
                if (n instanceof Region r) {
                    r.setMinWidth(Region.USE_PREF_SIZE); // nicht stauchen, sondern rechts abschneiden
                }
            }
            zugBox.setMinWidth(0);
            zugBox.setPrefWidth(0);
            zugBox.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(zugBox, Priority.ALWAYS);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(zugBox.widthProperty());
            clip.heightProperty().bind(zugBox.heightProperty());
            zugBox.setClip(clip);
            mitte.getChildren().add(zugBox);
            mitte.setMaxWidth(Double.MAX_VALUE);
            mitte.setStyle("-fx-border-color: #f5c518; -fx-border-width: 1.5; -fx-border-radius: 4;");
        } else {
            mitte.setMaxWidth(Region.USE_PREF_SIZE);
            mitte.setStyle("-fx-border-color: transparent; -fx-border-width: 1.5;");
        }
        StackPane mitteHalter = platzhalter != null ? new StackPane(platzhalter, mitte) : new StackPane(mitte);
        if (g.getArt() == GleisArt.LADEGLEIS) {
            // Platz für den Zug mit seinen Gütern und Lade-/Entladepfeilen (Anwender 27.10.2026)
            mitteHalter.setMinWidth(LADEGLEIS_ZUG_BREITE);
        }
        mitteHalter.setAlignment(Pos.CENTER_LEFT);
        mitteHalter.setPadding(new Insets(0, 0, 0, 8));
        HBox.setHgrow(mitteHalter, Priority.ALWAYS);
        HBox zeile = new HBox(6, name, mitteHalter);
        zeile.setAlignment(Pos.CENTER_LEFT);
        zeile.setMaxWidth(Double.MAX_VALUE);
        if (zug != null && kern != null) {
            // Rechtsklick auf ein Gleis mit Zug: sofort umladen (Anwender 10.11.2026)
            zeile.setOnContextMenuRequested(e -> {
                ContextMenu menu = new ContextMenu();
                MenuItem jetzt = new MenuItem(g.getArt() == GleisArt.BAHNSTEIG ? "Einsteigen/Aussteigen"
                        : "Entladen/Beladen");
                jetzt.setOnAction(x -> {
                    kern.manuellUmladen(g);
                    aktualisiere();
                });
                menu.getItems().add(jetzt);
                menu.show(zeile, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }

        // Schattenbahnhof und kombinierte Stationen (Personen und Güter): heute hinzugekommen/abgegangen je Gleis
        boolean kombiniert = !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty() && !s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty();
        if (kombiniert && !g.getHeute().isEmpty()) {
            Map<String, double[]> heute = new java.util.LinkedHashMap<>();
            if (aussenwelt) {
                if (g.getArt() == GleisArt.BAHNSTEIG) {
                    heute.put("", new double[2]);
                } else {
                    g.getAngebot().keySet().forEach(id -> heute.put(id, new double[2]));
                }
            }
            heute.putAll(g.getHeute());
            HBox zuAb = new HBox(4);
            zuAb.setAlignment(Pos.CENTER);
            Label titel = new Label("heute:");
            titel.setStyle("-fx-font-size: 9px; -fx-opacity: 0.8;");
            zuAb.getChildren().add(titel);
            for (Map.Entry<String, double[]> e : heute.entrySet()) {
                String text = "+" + Symbole.zahl(e.getValue()[0]) + " −" + Symbole.zahl(e.getValue()[1]);
                zuAb.getChildren().add(e.getKey().isEmpty()
                        ? Symbole.personBadge(text, "Heute angekommen/ausgestiegen (+) und abgefahren/eingestiegen (−)")
                        : Symbole.gutBadge(anlage.gut(e.getKey()), text, Symbole.Pfeil.KEINER));
            }
            Region einzug = new Region();
            einzug.setMinWidth(breite);
            StackPane zuAbHalter = new StackPane(zuAb);
            zuAbHalter.setAlignment(Pos.CENTER_LEFT);
            zuAbHalter.setPadding(new Insets(0, 0, 0, 13.5));
            zuAb.setMaxWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(zuAbHalter, Priority.ALWAYS);
            HBox zweite = new HBox(6, einzug, zuAbHalter);
            zweite.setMaxWidth(Double.MAX_VALUE);
            VBox v = new VBox(1, zeile, zweite);
            v.setMaxWidth(Double.MAX_VALUE);
            return v;
        }
        return zeile;
    }

    /** Mindestbreite der Werte eines Ladegleises, damit ein haltender Zug seine Güter zeigen kann. */
    private static final double LADEGLEIS_ZUG_BREITE = 230;

    /** Wartende je Klasse als Zusatz „ (1/2)“, z. B. „ (3/20)“ (Anwender 27.10.2026). */
    private static String klassen(double[] w) {
        return w == null ? "" : " (" + Symbole.zahl(Math.floor(w[0] + 1e-6)) + "/" + Symbole.zahl(Math.floor(w[1] + 1e-6)) + ")";
    }

    /** Teile des Zugs am Gleis (Konzept 10.1): Name, Belegung bzw. Ladung und die blinkenden Dreiecke. */
    /**
     * Zusatz hinter dem Zugnamen am Gleis aus der Online-Verbindung (Anwender 08.11.2026): „noch 12 cm“ bei der
     * Einfahrt, „Halt noch 40 s“ beim Warten, kurz nach der Abfahrt die Route. Setzt das Hauptfenster.
     */
    public static java.util.function.Function<String, String> zugZusatz = n -> "";
    /** Steht der Zug (TRUE), fährt er (FALSE) oder ist das nicht bekannt (null, z. B. Simulator)? */
    public static java.util.function.Function<String, Boolean> zugSteht = n -> null;

    /** Zusatz hinter der Ladung eines Zugs am Gleis: nichts im Halt, sonst „steht“ bzw. „fährt ein“. */
    static String fahrText(ZugZustand z) {
        if (z.isHaltend()) {
            return "";
        }
        if (z.isAbgefahren()) {
            return " · Abfahrt";
        }
        return Boolean.TRUE.equals(zugSteht.apply(z.getName())) ? " · steht" : " · fährt ein";
    }

    private List<Node> zugTeile(ZugZustand z, Gleis g) {
        List<Node> f = new ArrayList<>();
        Label name = new Label("🚆 " + z.getName());
        name.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        f.add(name);
        // Fahrtrichtung am Gleis (Anwender 09.11.2026)
        String richtung = g.richtungFuer(z.getRichtung());
        if (richtung != null && !richtung.isEmpty() && !richtung.equals(Gleis.OHNE_RICHTUNG)) {
            Label r = new Label("→ " + richtung);
            r.setStyle("-fx-font-size: 9px;");
            f.add(r);
        }
        String zusatz = zugZusatz.apply(z.getName());
        if (zusatz != null && !zusatz.isEmpty()) {
            Label x = new Label(zusatz);
            x.setStyle("-fx-font-size: 9px; -fx-text-fill: #1f7a3a; -fx-font-weight: bold;");
            f.add(x);
        }
        if (g.getArt() == GleisArt.BAHNSTEIG) {
            double[] p = z.passagiereSumme();
            f.add(Symbole.personBadge(Symbole.zahl(p[0] + p[1]) + "/"
                    + Symbole.zahl(z.getSitzplaetzeErste() + z.getSitzplaetzeZweite()), "Passagiere im Zug"));
            // Je ausgestiegener/eingestiegener Person blinkt ein gelbes Dreieck.
            int aus = neueEinheiten(g.getId() + "|aus", g.getLetztAusgestiegen());
            int ein = neueEinheiten(g.getId() + "|ein", g.getLetztEingestiegen());
            if (aus > 0) {
                f.add(Symbole.blinkPersonen(true, aus, "Aussteigende Passagiere"));
            }
            if (ein > 0) {
                f.add(Symbole.blinkPersonen(false, ein, "Einsteigende Passagiere"));
            }
        } else {
            // Bei der Einfahrt nur die Symbole der Ladung; hält der Zug, je Gut Menge im Zug mit Pfeil
            // ▲ = wird entladen, ▼ = wird geladen (Anwender 27.10.2026).
            if (!z.isHaltend()) {
                for (String gutId : z.ladungJeGut().keySet()) {
                    f.add(Symbole.gut(anlage.gut(gutId), 14));
                }
            } else {
                // Pfeil für den ganzen Halt (nicht nur im Schritt, in dem gerade eine Einheit bewegt wurde)
                Set<String> entladen = new java.util.HashSet<>(z.entladenImHalt());
                entladen.addAll(g.getAktivEntladen());
                Set<String> geladen = new java.util.HashSet<>(z.geladenImHalt());
                geladen.addAll(g.getAktivLaden());
                Map<String, Double> ladung = new java.util.LinkedHashMap<>(z.ladungJeGut());
                entladen.forEach(id -> ladung.putIfAbsent(id, 0.0));
                geladen.forEach(id -> ladung.putIfAbsent(id, 0.0));
                ladung.forEach((gutId, m) -> {
                    Symbole.Pfeil pfeil = entladen.contains(gutId) ? Symbole.Pfeil.ENTLADEN
                            : geladen.contains(gutId) ? Symbole.Pfeil.LADEN : Symbole.Pfeil.KEINER;
                    // Mit Pfeil die in diesem Halt entladene bzw. geladene Menge, ohne Pfeil die Menge im Zug
                    // (Anwender 10.11.2026: „Gl 0 ▲“ sagte nichts)
                    double zeigen = pfeil == Symbole.Pfeil.KEINER ? m : z.bewegtImHalt(gutId);
                    f.add(Symbole.gutBadge(anlage.gut(gutId), Symbole.zahl(Math.floor(zeigen + 1e-6)), pfeil));
                });
            }
            // Gesamt: Ladung / Kapazität aller Wagen
            f.add(hinweis((z.ladungJeGut().isEmpty() ? "leer, " : "")
                    + Symbole.zahl(z.gueterLadung()) + "/" + Symbole.zahl(z.gueterKapazitaet())
                    + (z.hatJoker() ? " (Joker)" : "")
                    + (z.getWagenladungen().isEmpty() && z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() <= 0
                    ? " – keine Güterwagen erkannt" : "")
                    + fahrText(z)));
        }
        return f;
    }

    /** Letzte angezeigte Zählerstände je Gleis (Passagiere) bzw. Gleis/Zug/Gut (Ladung), für die Dreiecke. */
    private final Map<String, Double> zuletzt = new java.util.HashMap<>();

    /** Neu hinzugekommene ganze Einheiten eines je Halt von 0 aufsteigenden Zählers. */
    private int neueEinheiten(String schluessel, double wert) {
        Double vorher = zuletzt.put(schluessel, wert);
        if (vorher == null) {
            return 0;
        }
        double basis = wert + 1e-9 < vorher ? 0 : vorher; // neuer Halt: Zähler beginnt bei 0
        return (int) (Math.floor(wert + 1e-9) - Math.floor(basis + 1e-9));
    }

    /** Veränderung in ganzen Einheiten (positiv = mehr, negativ = weniger). */
    private int aenderungEinheiten(String schluessel, double wert) {
        Double vorher = zuletzt.put(schluessel, wert);
        if (vorher == null) {
            return 0;
        }
        return (int) (Math.floor(wert + 1e-9) - Math.floor(vorher + 1e-9));
    }

    private static FlowPane flow() {
        FlowPane f = new FlowPane(3, 2);
        f.setAlignment(Pos.CENTER);
        f.setPrefWrapLength(260);
        f.setMaxWidth(270);
        return f;
    }

    private static Label legendeText(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px;");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    private static Label hinweis(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 10px;");
        return l;
    }

    // ------------------------------------------------------------------ Texte

    private void fuegeTextHinzu(NetzText t) {
        Label label = new Label();
        textStil(label, t);
        VBox box = new VBox(label);
        box.setPadding(new Insets(4));
        if (t.isRahmen()) {
            box.setStyle("-fx-border-color: #999999; -fx-border-width: 1;");
        }
        box.setLayoutX(t.getX());
        box.setLayoutY(t.getY());
        if (interaktiv) {
            double[] start = new double[2];
            boolean[] gezogen = {false};
            box.setOnMousePressed(e -> {
                Point2D p = contentPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                start[0] = p.getX() - box.getLayoutX();
                start[1] = p.getY() - box.getLayoutY();
                gezogen[0] = false;
                e.consume();
            });
            box.setOnMouseDragged(e -> {
                Point2D p = contentPane.sceneToLocal(e.getSceneX(), e.getSceneY());
                box.setLayoutX(Math.max(0, p.getX() - start[0]));
                box.setLayoutY(Math.max(0, p.getY() - start[1]));
                t.setX(box.getLayoutX());
                t.setY(box.getLayoutY());
                gezogen[0] = true;
                e.consume();
            });
            box.setOnMouseReleased(e -> {
                if (gezogen[0]) {
                    geaendert();
                } else if (e.getClickCount() >= 2) {
                    formatiere(box, label, t);
                }
                e.consume();
            });
            ContextMenu menu = new ContextMenu();
            MenuItem format = new MenuItem("Formatierung…");
            format.setOnAction(e -> formatiere(box, label, t));
            MenuItem loeschen = new MenuItem("Löschen");
            loeschen.setOnAction(e -> {
                anlage.getTexte().remove(t);
                contentPane.getChildren().remove(box);
                geaendert();
            });
            menu.getItems().addAll(format, loeschen);
            box.setOnContextMenuRequested(e -> menu.show(box, e.getScreenX(), e.getScreenY()));
        }
        textAnsichten.put(t, box);
        contentPane.getChildren().add(box);
    }

    private void formatiere(VBox box, Label label, NetzText t) {
        Stage owner = box.getScene() != null ? (Stage) box.getScene().getWindow() : null;
        if (FormatierungDialog.bearbeiten(owner, t)) {
            textStil(label, t);
            box.setStyle(t.isRahmen() ? "-fx-border-color: #999999; -fx-border-width: 1;" : "");
            geaendert();
        }
    }

    private static void textStil(Label label, NetzText t) {
        label.setText(t.getText());
        label.setFont(Font.font(t.getSchriftart(), t.isFett() ? FontWeight.BOLD : FontWeight.NORMAL,
                t.isKursiv() ? FontPosture.ITALIC : FontPosture.REGULAR, t.getSchriftgroesse()));
        // Inline, damit das Theme eine gewaehlte Textfarbe nicht ueberschreibt. Die Vorgabe Weiss
        // (#FFFFFF) bleibt dem Theme ueberlassen, sonst waere sie im hellen Theme unsichtbar.
        String farbe = Symbole.hex(Symbole.farbe(t.getFarbe(), Color.WHITE));
        label.setStyle("#FFFFFF".equalsIgnoreCase(farbe) ? "" : "-fx-text-fill: " + farbe + ";");
        label.setTextAlignment(switch (t.getHorizontal()) {
            case ZENTRIERT -> TextAlignment.CENTER;
            case RECHTSBUENDIG -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
        });
        label.setRotate(switch (t.getTextrichtung()) {
            case SENKRECHT_AUFWAERTS -> -90;
            case SENKRECHT_ABWAERTS -> 90;
            default -> 0;
        });
    }

    // ------------------------------------------------------------------ Palette, Zoom

    /** Einträge der Palette: immer weiß hinterlegt, Schrift schwarz, 3D nach den Voreinstellungen (27.10.2026). */
    private final List<VBox> paletteEintraege = new ArrayList<>();

    private void paletteStil() {
        boolean d3 = AppSettings.getInstance().isSymbol3d();
        for (VBox e : paletteEintraege) {
            e.setStyle("-fx-background-color: #ffffff; -fx-background-radius: 4; -fx-border-color: #9a9a9a;"
                    + " -fx-border-radius: 4; -fx-cursor: hand;");
            e.setEffect(d3 ? schatten(3, 6) : null);
        }
    }

    private Node palette() {
        VBox p = new VBox(6);
        p.setPadding(new Insets(8));
        p.getChildren().add(new Label("Anlegen:"));
        for (ObjektArt art : ObjektArt.values()) {
            if (art == ObjektArt.INFRASTRUKTUR || art == ObjektArt.ANDERES) {
                continue; // Infrastruktur gibt es genau einmal; "Anderes" entsteht nur aus iTrain-Bahnhöfen
            }
            Group icon = SymbolRegistry.getInstance().symbolFuer(art.getSymbolSchluessel()).createIcon();
            StackPane halter = new StackPane(icon);
            halter.setPrefSize(36, 36);
            Label name = new Label(art.getLabel());
            name.setStyle("-fx-font-size: 10px; -fx-text-fill: #000000;");
            VBox eintrag = new VBox(2, halter, name);
            eintrag.setAlignment(Pos.CENTER);
            eintrag.setPadding(new Insets(3));
            paletteEintraege.add(eintrag);
            eintrag.setOnMouseClicked(e -> {
                if (e.isStillSincePress()) {
                    onNeu.accept(art, sichtMitte());
                }
            });
            ziehbar(eintrag, "anlage:" + art.name());
            p.getChildren().add(eintrag);
        }
        p.getChildren().add(new Separator());
        Label text = new Label("T");
        text.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #000000;");
        Label textName = new Label("Text");
        textName.setStyle("-fx-text-fill: #000000;");
        VBox textEintrag = new VBox(2, text, textName);
        textEintrag.setAlignment(Pos.CENTER);
        paletteEintraege.add(textEintrag);
        paletteStil();
        textEintrag.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                neuerText(sichtMitte());
            }
        });
        ziehbar(textEintrag, "anlage:TEXT");
        p.getChildren().add(textEintrag);
        ScrollPane scroll = new ScrollPane(p);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(110);
        Button klappen = new Button("◀");
        klappen.setMaxHeight(Double.MAX_VALUE);
        klappen.setOnAction(e -> {
            boolean sichtbar = scroll.isVisible();
            scroll.setVisible(!sichtbar);
            scroll.setManaged(!sichtbar);
            klappen.setText(sichtbar ? "▶" : "◀");
        });
        return new HBox(klappen, scroll);
    }

    /** Mitte des sichtbaren Ausschnitts in Koordinaten der Zeichenfläche (dort erscheint Neues). */
    private Point2D sichtMitte() {
        double versatz = (neuZaehler++ % 6) * 18;
        if (scroll == null || scroll.getScene() == null) {
            return new Point2D(200 + versatz, 200 + versatz);
        }
        javafx.geometry.Bounds v = scroll.getViewportBounds();
        Point2D szene = scroll.localToScene(v.getWidth() / 2, v.getHeight() / 2);
        Point2D p = contentPane.sceneToLocal(szene);
        return new Point2D(einrasten(Math.max(0, p.getX() - 60 + versatz)),
                einrasten(Math.max(0, p.getY() - 40 + versatz)));
    }

    /** Palette-Eintrag per Ziehen auf die Zeichenfläche ablegen. */
    private static void ziehbar(Node eintrag, String inhalt) {
        eintrag.setOnDragDetected(e -> {
            javafx.scene.input.Dragboard db = eintrag.startDragAndDrop(javafx.scene.input.TransferMode.COPY);
            javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
            c.putString(inhalt);
            db.setContent(c);
            db.setDragView(eintrag.snapshot(null, null));
            e.consume();
        });
    }

    // ------------------------------------------------------------------ Kopieren / Einfügen (Anwender 09.11.2026)

    /** Zuletzt angeklicktes Symbol im Editor (für Bearbeiten > Kopieren). */
    private AnlagenObjekt zuletztGewaehlt;

    /** Der offene Editor (für Bearbeiten > Kopieren/Einfügen im Hauptfenster), sonst null. */
    private static java.lang.ref.WeakReference<AnlagenCanvas> aktiverEditor = new java.lang.ref.WeakReference<>(null);

    /** Öffnet die Voreinstellungen (setzt das Hauptfenster); Rechtsklick auf den Hintergrund im Editor. */
    public static java.util.function.Consumer<javafx.stage.Window> voreinstellungen = w -> { };

    /** Editor, dessen Fenster gerade offen ist, oder null. */
    public static AnlagenCanvas offenerEditor() {
        AnlagenCanvas c = aktiverEditor.get();
        return c != null && c.contentPane.getScene() != null && c.contentPane.getScene().getWindow() != null
                && c.contentPane.getScene().getWindow().isShowing() ? c : null;
    }

    /** Bearbeiten > Kopieren: das zuletzt angeklickte Symbol; false, wenn keins gewählt ist. */
    public boolean kopiereGewaehltes() {
        if (zuletztGewaehlt == null || zuletztGewaehlt instanceof Infrastruktur
                || !anlage.getObjekte().contains(zuletztGewaehlt)) {
            return false;
        }
        kopiere(zuletztGewaehlt);
        return true;
    }

    private void kopiere(AnlagenObjekt o) {
        if (o instanceof ReiterAnker || o instanceof Infrastruktur) {
            return;
        }
        javafx.scene.input.ClipboardContent c = new javafx.scene.input.ClipboardContent();
        c.putString(AnlageXml.objektText(o));
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
    }

    /** Bearbeiten > Einfügen: in der Mitte der Sicht. */
    public boolean fuegeEinInMitte() {
        return fuegeEin(sichtMitte());
    }

    /**
     * Fügt das Symbol aus der Zwischenablage an der Stelle ein (als Kopie mit „(Kopie)“ im Namen, ohne Gleise,
     * Einzugsbereich und Bestände, orange markiert wie jedes neue Objekt). false, wenn kein Symbol drin ist.
     */
    public boolean fuegeEin(Point2D p) {
        javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
        AnlagenObjekt o = cb.hasString() ? AnlageXml.objektAusText(cb.getString()) : null;
        if (o == null) {
            return false;
        }
        o.setName(o.getName() + " (Kopie)");
        o.setX(einrasten(Math.max(0, p.getX())));
        o.setY(einrasten(Math.max(0, p.getY())));
        o.setNeu(true);
        if (bereich != null && (o instanceof com.example.itrain_logistik.kern.Bereich)) {
            return false; // Tabs/Module nur im ersten Reiter (sie hängen an der Anlagen-Infrastruktur)
        }
        o.setReiterId(bereich != null ? bereich.getId() : null);
        anlage.getObjekte().add(o);
        if (o instanceof Station st && bereich != null) {
            bereich.getStationen().add(st.getId());
        }
        neuAufbauen();
        geaendert();
        return true;
    }

    /** Rechtsklick auf den freien Hintergrund im Editor: „Einfügen“, Trennstrich, „Voreinstellungen“. */
    private void richteHintergrundMenueEin() {
        contentPane.setOnContextMenuRequested(e -> {
            for (javafx.scene.Node n = e.getTarget() instanceof javafx.scene.Node t ? t : null; n != null
                    && n != contentPane; n = n.getParent()) {
                if (ansichten.containsValue(n) || textAnsichten.containsValue(n)) {
                    return; // Symbol oder Text hat sein eigenes Menü
                }
            }
            if (e.isConsumed()) {
                return;
            }
            Point2D p = new Point2D(e.getX(), e.getY());
            ContextMenu menu = new ContextMenu();
            MenuItem einfuegen = new MenuItem("Einfügen");
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            einfuegen.setDisable(!cb.hasString() || !cb.getString().startsWith(AnlageXml.KOPIE_KENNUNG));
            einfuegen.setOnAction(x -> fuegeEin(p));
            MenuItem vorein = new MenuItem("Voreinstellungen");
            vorein.setOnAction(x -> voreinstellungen.accept(contentPane.getScene().getWindow()));
            menu.getItems().addAll(einfuegen, new javafx.scene.control.SeparatorMenuItem(), vorein);
            menu.show(contentPane, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    private void richteAblegenEin() {
        contentPane.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && e.getDragboard().getString().startsWith("anlage:")) {
                e.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            e.consume();
        });
        contentPane.setOnDragDropped(e -> {
            String s = e.getDragboard().getString();
            boolean ok = s != null && s.startsWith("anlage:");
            if (ok) {
                Point2D p = new Point2D(einrasten(Math.max(0, e.getX() - 40)), einrasten(Math.max(0, e.getY() - 30)));
                String art = s.substring("anlage:".length());
                // Erst nach dem Ablegen bearbeiten, sonst blockiert der Dialog das Ziehen.
                javafx.application.Platform.runLater(() -> {
                    if ("TEXT".equals(art)) {
                        neuerText(p);
                    } else {
                        onNeu.accept(ObjektArt.valueOf(art), p);
                    }
                });
            }
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    /** Legt einen Text an der Stelle an und öffnet sofort die Formatierung; Abbrechen fügt nichts ein. */
    private void neuerText(Point2D p) {
        NetzText t = new NetzText(null, p.getX(), p.getY());
        t.setText("Text");
        anlage.getTexte().add(t);
        fuegeTextHinzu(t);
        VBox box = textAnsichten.get(t);
        Stage owner = box.getScene() != null ? (Stage) box.getScene().getWindow() : null;
        if (!FormatierungDialog.bearbeiten(owner, t) || t.getText() == null || t.getText().isBlank()) {
            anlage.getTexte().remove(t);
            contentPane.getChildren().remove(box);
            textAnsichten.remove(t);
            return;
        }
        if (box.getChildren().get(0) instanceof Label label) {
            textStil(label, t);
            box.setStyle(t.isRahmen() ? "-fx-border-color: #999999; -fx-border-width: 1;" : "");
        }
        geaendert();
    }

    /** Pfeile der Legende in der Statusleiste, Farben wie in den Voreinstellungen. */
    private final StackPane legendePfeilAus = new StackPane();
    private final StackPane legendePfeilEin = new StackPane();

    private final StackPane legendeGutAus = new StackPane();
    private final StackPane legendeGutEin = new StackPane();

    private void legendePfeileZeichnen() {
        legendePfeilAus.getChildren().setAll(Symbole.pfeilPersonen(true, 11));
        legendePfeilEin.getChildren().setAll(Symbole.pfeilPersonen(false, 11));
        legendeGutAus.getChildren().setAll(Symbole.pfeilGueter(true, 10));
        legendeGutEin.getChildren().setAll(Symbole.pfeilGueter(false, 10));
    }

    /** Nach einer Farbänderung in den Voreinstellungen: Legenden-Pfeile aller Ansichten neu. */
    public static void pfeileNeuZeichnen() {
        for (AnlagenCanvas c : List.copyOf(ALLE.keySet())) {
            c.legendePfeileZeichnen();
        }
    }

    private Node statusLeiste() {
        legendePfeileZeichnen();
        zoomRegler = new Slider(ZOOM_MIN, ZOOM_MAX, 1.0);
        zoomRegler.setPrefWidth(140);
        zoomRegler.valueProperty().addListener((obs, a, b) -> setZoom(b.doubleValue()));
        Region hinweis;
        if (interaktiv) {
            Label l = new Label("Ziehen ordnet an (mit Umschalt rastet die Verbindung gerade im 90°-Winkel ein) · rechten Rand ziehen = Breite · Anschluss: Rechtsklick „Mit Station verbinden…“ · "
                    + "Doppelklick bearbeitet · Rechtsklick: Farbe/Löschen, auf einer Linie: Linienstärke · "
                    + "Palette: klicken oder auf die Fläche ziehen");
            l.setStyle("-fx-font-size: 11px;");
            hinweis = l;
        } else {
            HBox legende = new HBox(4,
                    legendeText("Klick auf ein Symbol öffnet die Details ·"),
                    legendePfeilAus, legendeText("Aussteigende Passagiere ·"),
                    legendePfeilEin, legendeText("Einsteigende Passagiere ·"),
                    legendeGutAus, legendeText("Entladen ·"), legendeGutEin, legendeText("Beladen"));
            legende.setAlignment(Pos.CENTER_LEFT);
            javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(legende.widthProperty());
            clip.heightProperty().bind(legende.heightProperty());
            legende.setClip(clip);
            hinweis = legende;
        }
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        HBox zeile = new HBox(8, hinweis, platz);
        if (!interaktiv) {
            Button passend = new Button("Einpassen");
            passend.setTooltip(new javafx.scene.control.Tooltip("Ganze Anlage in den sichtbaren Bereich zoomen"));
            passend.setOnAction(e -> einpassen());
            zeile.getChildren().add(passend);
        }
        if (!interaktiv) {
            zeile.getChildren().addAll(tempoRegler(anlage, anlage::getTempo, anlage::setTempo, onTempoGeaendert),
                    new Separator(javafx.geometry.Orientation.VERTICAL));
        }
        zeile.getChildren().addAll(new Label("Zoom:"), zoomRegler);
        // Bei wenig Platz kürzt sich nur der Hinweistext, Regler und Knöpfe bleiben lesbar.
        hinweis.setMinWidth(0);
        for (Node n : zeile.getChildren()) {
            if (n != hinweis && n != platz && n instanceof Region r) {
                r.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
        zeile.setAlignment(Pos.CENTER_LEFT);
        zeile.setPadding(new Insets(4, 8, 4, 8));
        return new VBox(new Separator(), zeile);
    }

    /**
     * Regler "Langsamer – Schneller" (Stufen -4..+4, Mitte = ×1, keine Pause, siehe
     * {@link Anlage#tempoFaktor(int)}); auch im Detailfenster je Objekt verwendet, dort mit Pausen-Schalter.
     */
    static HBox tempoRegler(Anlage anlage, java.util.function.IntSupplier lesen, java.util.function.IntConsumer setzen,
            Runnable danach) {
        Slider regler = new Slider(Anlage.TEMPO_MIN, Anlage.TEMPO_MAX, lesen.getAsInt());
        regler.setMajorTickUnit(1);
        regler.setMinorTickCount(0);
        regler.setSnapToTicks(true);
        regler.setShowTickMarks(true);
        regler.setPrefWidth(140);
        Label wert = new Label(Anlage.tempoText(lesen.getAsInt()));
        wert.setMinWidth(40);
        regler.valueProperty().addListener((obs, a, b) -> {
            int stufe = (int) Math.round(b.doubleValue());
            if (stufe != lesen.getAsInt()) {
                setzen.accept(stufe);
                wert.setText(Anlage.tempoText(stufe));
                if (danach != null) {
                    danach.run();
                }
            }
        });
        javafx.scene.control.Tooltip.install(regler, new javafx.scene.control.Tooltip(
                "Erzeugung und Bereitstellung: links langsamer (×0,75 … ×0,1), Mitte ×1, "
                        + "rechts schneller (×1,5 … ×4). Angehalten wird mit dem Knopf Pause."));
        Label l = new Label("Langsamer");
        Label r = new Label("Schneller");
        l.setStyle("-fx-font-size: 11px;");
        r.setStyle("-fx-font-size: 11px;");
        for (Label x : List.of(l, r, wert)) {
            x.setMinWidth(Region.USE_PREF_SIZE);
        }
        // Deutliche Mittelstellung ×1 (Anwender 10.11.2026): Marke hinter dem Regler, Wert fett in der Mitte,
        // Doppelklick stellt auf ×1 zurück.
        Region marke = new Region();
        marke.setMinSize(4, 20);
        marke.setPrefSize(4, 20);
        marke.setMaxSize(4, 20);
        marke.setStyle("-fx-background-color: #e07800; -fx-background-radius: 2;");
        marke.setMouseTransparent(true);
        StackPane mitMarke = new StackPane(marke, regler);
        Runnable mitteZeigen = () -> wert.setStyle(Math.round(regler.getValue()) == 0
                ? "-fx-font-weight: bold; -fx-text-fill: #e07800;" : "");
        regler.valueProperty().addListener((obs, a, b) -> mitteZeigen.run());
        mitteZeigen.run();
        regler.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                regler.setValue(0);
            }
        });
        HBox h = new HBox(6, l, mitMarke, r, wert);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private void setZoom(double wert) {
        double z = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, wert));
        boolean anders = Math.abs(contentPane.getScaleX() - z) > 0.01;
        contentPane.setScaleX(z);
        contentPane.setScaleY(z);
        if (anders) {
            zeichneVerbindungen(); // Segmentzahl folgt dem Zoom
        }
        if (zoomRegler != null && Math.abs(zoomRegler.getValue() - z) > 0.001) {
            zoomRegler.setValue(z);
        }
    }

    private void geaendert() {
        if (onGeaendert != null) {
            onGeaendert.run();
        }
    }
}
