package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Gut;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Kleine Bausteine fuer die Anzeige im Stil der Transport-Fever-Bildschirmfotos (Konzept
 * Abschnitt 10.1): Gut-Symbole (Farbe des Guts mit zwei Buchstaben), ein Personen-Symbol und
 * dunkle Schilder (Badges) mit Symbol und Zahl. Pfeil nach oben = wird entladen, Pfeil nach
 * unten = wird geladen, jeweils in einem schwarz/weissen Quadrat ueber dem Schild.
 */
public final class Symbole {

    /** KEINER = ohne Kasten, LEER = leerer Kasten (bereit), ENTLADEN/LADEN = Kasten mit schwarzem Dreieck. */
    public enum Pfeil { KEINER, LEER, ENTLADEN, LADEN }

    private static final String BADGE_STIL = "-fx-background-color: #1f2a44; -fx-background-radius: 4;"
            + " -fx-border-color: #4a5a80; -fx-border-radius: 4; -fx-border-width: 1;";

    private Symbole() {
    }

    /** Tooltip, der schnell erscheint (die Schilder werden laufend aktualisiert). */
    public static Tooltip tip(String text) {
        Tooltip t = new Tooltip(text);
        t.setShowDelay(javafx.util.Duration.millis(200));
        t.setShowDuration(javafx.util.Duration.seconds(20));
        return t;
    }

    /** Alle Texte eines Knotens (Labels) und seiner Kinder - um unveränderte Schilder nicht neu zu bauen. */
    public static String texte(javafx.scene.Node n) {
        StringBuilder sb = new StringBuilder();
        sammle(n, sb);
        return sb.toString();
    }

    private static void sammle(javafx.scene.Node n, StringBuilder sb) {
        // Blinkende Dreiecke tragen eine laufende Nummer als Id: jede neue Veränderung baut das Schild neu.
        if (n.getId() != null && n.getId().startsWith("blink")) {
            sb.append(n.getId()).append(';');
        }
        if (n instanceof javafx.scene.control.Labeled l) {
            sb.append(l.getText()).append('|').append(l.getStyle()).append(';');
        }
        if (n instanceof javafx.scene.Parent p) {
            sb.append('(');
            for (javafx.scene.Node k : p.getChildrenUnmodifiable()) {
                sammle(k, sb);
            }
            sb.append(')');
        }
    }

    /** Gut-Symbol: abgerundetes Quadrat in der Farbe des Guts mit Kuerzel. */
    public static Node gut(Gut gut, double groesse) {
        Color farbe = farbe(gut != null ? gut.getFarbe() : null, Color.GRAY);
        Rectangle r = new Rectangle(groesse, groesse);
        r.setArcWidth(groesse / 3);
        r.setArcHeight(groesse / 3);
        r.setFill(farbe);
        r.setStroke(farbe.darker());
        Label l = new Label(gut != null ? gut.getKuerzel() : "?");
        // Inline-Stil statt setTextFill: die Theme-Stylesheets (Hell/Dunkel) setzen fuer Labels eine
        // Textfarbe, die Setter-Werte ueberschreibt - Inline-Stile haben Vorrang.
        l.setStyle(textStil(farbe.getBrightness() > 0.6 ? "black" : "white", groesse * 0.5));
        StackPane p = new StackPane(r, l);
        p.setMinSize(groesse, groesse);
        p.setMaxSize(groesse, groesse);
        if (gut != null) {
            Tooltip.install(p, tip(gut.getLadeklasse().getLabel() + ": " + gut.getName()));
        }
        return p;
    }

    /** Gelb der Passagiere (wie in TF). */
    public static final Color GELB = Color.web("#f5c518");

    private static long blinkNummer;

    /** Gefülltes Dreieck: nach oben = aussteigen/entladen, nach unten = einsteigen/beladen. */
    public static Node dreieck(boolean hoch, Color farbe, double groesse) {
        javafx.scene.shape.Polygon d = hoch
                ? new javafx.scene.shape.Polygon(groesse / 2, 0, groesse, groesse, 0, groesse)
                : new javafx.scene.shape.Polygon(0, 0, groesse, 0, groesse / 2, groesse);
        d.setFill(farbe);
        d.setStroke(farbe.darker().darker());
        d.setStrokeWidth(0.8);
        return d;
    }

    /**
     * Dreieck, das je Veränderung (ganze Person bzw. ganze Einheit) einmal blinkt, höchstens sechsmal
     * innerhalb von knapp einer Sekunde (dem Takt der Anzeige).
     */
    public static Node blinkDreieck(boolean hoch, Color farbe, int anzahl, String tooltip) {
        StackPane p = new StackPane(dreieck(hoch, farbe, 12));
        p.setId("blink" + (++blinkNummer));
        p.setPadding(new Insets(0, 1, 0, 1));
        Tooltip.install(p, tip(tooltip));
        int n = Math.max(1, Math.min(anzahl, 6));
        javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(900.0 / (2 * n)), p);
        f.setFromValue(1.0);
        f.setToValue(0.15);
        f.setAutoReverse(true);
        f.setCycleCount(2 * n);
        f.play();
        return p;
    }

    /**
     * Pfeil beim Ein-/Aussteigen von Personen (Anwender 22.10.2026): größer, mit dickem Rahmen; Farben aus den
     * Voreinstellungen (Standard orange Füllung, schwarzer Rahmen).
     */
    public static Node blinkPersonen(boolean hoch, int anzahl, String tooltip) {
        StackPane p = new StackPane(pfeilPersonen(hoch, 15));
        p.setId("blink" + (++blinkNummer));
        p.setPadding(new Insets(0, 2, 0, 2));
        Tooltip.install(p, tip(tooltip));
        int n = Math.max(1, Math.min(anzahl, 6));
        javafx.animation.FadeTransition f = new javafx.animation.FadeTransition(
                javafx.util.Duration.millis(900.0 / (2 * n)), p);
        f.setFromValue(1.0);
        f.setToValue(0.25);
        f.setAutoReverse(true);
        f.setCycleCount(2 * n);
        f.play();
        return p;
    }

    /**
     * Ruhender Pfeil für Ein-/Aussteigen in den Farben der Voreinstellungen (auch für die Legende in der
     * Statusleiste, Anwender 22.10.2026); Rahmen wächst mit der Größe (15 px = 2,2 px).
     */
    public static Node pfeilPersonen(boolean hoch, double g) {
        AppSettings st = AppSettings.getInstance();
        Color fuellung = farbe(st.getPfeilFuellung(), Color.web(AppSettings.PFEIL_FUELLUNG_STANDARD));
        Color rahmen = farbe(st.getPfeilRahmen(), Color.BLACK);
        javafx.scene.shape.Polygon d = hoch
                ? new javafx.scene.shape.Polygon(g / 2, 0, g, g, 0, g)
                : new javafx.scene.shape.Polygon(0, 0, g, 0, g / 2, g);
        d.setFill(fuellung);
        d.setStroke(rahmen);
        d.setStrokeWidth(Math.max(1.2, g * 2.2 / 15));
        d.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        return d;
    }

    /** Personen-Symbol (gelb wie in TF). */
    public static Node person(double groesse) {
        Circle kopf = new Circle(groesse * 0.18);
        kopf.setCenterX(groesse / 2);
        kopf.setCenterY(groesse * 0.2);
        Rectangle koerper = new Rectangle(groesse * 0.3, groesse * 0.42, groesse * 0.4, groesse * 0.55);
        koerper.setArcWidth(groesse * 0.2);
        koerper.setArcHeight(groesse * 0.2);
        kopf.setFill(GELB);
        koerper.setFill(GELB);
        Group g = new Group(new Rectangle(groesse, groesse, Color.TRANSPARENT), kopf, koerper);
        return g;
    }

    /** Dunkles Schild mit Symbol und Text, optional mit Pfeil-Quadrat darueber. */
    public static Node badge(Node symbol, String text, Pfeil pfeil, String tooltip) {
        Label l = new Label(text);
        l.setStyle(textStil("white", 11));
        HBox schild = new HBox(4, symbol, l);
        schild.setAlignment(Pos.CENTER_LEFT);
        schild.setPadding(new Insets(1, 5, 1, 3));
        schild.setStyle(BADGE_STIL + (AppSettings.getInstance().isSymbol3d()
                ? " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 4, 0, 1.5, 1.5);" : ""));
        if (tooltip != null) {
            Tooltip.install(schild, tip(tooltip));
        }
        if (pfeil == null || pfeil == Pfeil.KEINER) {
            return schild;
        }
        // Kasten rechts neben dem Schild: leer, beim Umladen mit schwarzem Dreieck (Anwender 12.10.2026).
        Node inhalt = pfeil == Pfeil.LEER ? null : dreieck(pfeil == Pfeil.ENTLADEN, Color.BLACK, 9);
        StackPane k = kasten(inhalt);
        Tooltip.install(k, tip(pfeil == Pfeil.ENTLADEN ? "wird entladen" : pfeil == Pfeil.LADEN ? "wird geladen"
                : "zurzeit kein Umladen"));
        HBox h = new HBox(2, schild, k);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** Kleiner weißer Kasten mit schwarzem Rand für die Lade-Dreiecke; {@code inhalt} darf null sein (leer). */
    public static StackPane kasten(Node inhalt) {
        StackPane k = new StackPane();
        if (inhalt != null) {
            k.getChildren().add(inhalt);
        }
        k.setMinSize(16, 16);
        k.setPrefSize(16, 16);
        k.setMaxSize(16, 16);
        k.setStyle("-fx-background-color: white; -fx-border-color: black; -fx-border-width: 1;");
        return k;
    }

    public static Node gutBadge(Gut gut, String text, Pfeil pfeil) {
        return badge(gut(gut, 14), text, pfeil, gut != null ? gut.getLadeklasse().getLabel() + ": " + gut.getName() : null);
    }

    public static Node personBadge(String text, String tooltip) {
        return badge(person(14), text, Pfeil.KEINER, tooltip);
    }

    /** Inline-Stil fuer Text auf Symbolen und Schildern (unabhaengig vom Theme). */
    public static String textStil(String farbe, double groesse) {
        return String.format(java.util.Locale.ROOT, "-fx-text-fill: %s; -fx-font-weight: bold; -fx-font-size: %.1fpx;",
                farbe, groesse);
    }

    /** Ganze Zahl ohne Nachkommastellen ("19"), wie in TF. */
    /** Farbwähler ohne Hex-Angabe/Farbnamen neben dem Farbfeld (Anwender 14.10.2026). */
    public static javafx.scene.control.ColorPicker farbwahl(Color farbe) {
        return ohneHex(new javafx.scene.control.ColorPicker(farbe));
    }

    public static javafx.scene.control.ColorPicker ohneHex(javafx.scene.control.ColorPicker p) {
        p.setStyle("-fx-color-label-visible: false;");
        return p;
    }

    public static String zahl(double v) {
        return String.valueOf(Math.round(v));
    }

    /**
     * Ganze Einheiten fuer Anzeigen (Anwender 16.10.2026: "Einheiten unter 1 gibt es nicht"): ein Bruchteil
     * zaehlt als volle Einheit, 0,1 wird also 1; negative Werte und 0 bleiben 0.
     */
    public static String ganz(double v) {
        if (Double.isNaN(v) || v <= 1e-9) {
            return "0";
        }
        if (Double.isInfinite(v)) {
            return "∞";
        }
        return String.valueOf((long) Math.ceil(v - 1e-6));
    }

    /**
     * Bestände und offene Mengen (Anwender 16.10.2026): nur volle Einheiten, ein Rest unter 1 zählt noch nicht
     * (offener Bedarf 0,3 zeigt 0, nicht 1; geliefert wird ohnehin erst ab einer ganzen Einheit).
     */
    public static String bestand(double v) {
        if (Double.isNaN(v) || v < 1 - 1e-6) {
            return "0";
        }
        if (Double.isInfinite(v)) {
            return "∞";
        }
        return String.valueOf((long) Math.floor(v + 1e-6));
    }

    public static String zahl1(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf(Math.round(v));
        }
        return String.format(java.util.Locale.GERMANY, "%.1f", v);
    }

    public static Color farbe(String hex, Color standard) {
        if (hex == null || hex.isBlank()) {
            return standard;
        }
        try {
            return Color.web(hex);
        } catch (IllegalArgumentException ex) {
            return standard;
        }
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }
}
