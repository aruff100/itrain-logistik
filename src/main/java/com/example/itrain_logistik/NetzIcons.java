package com.example.itrain_logistik;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * Einfache, selbst gezeichnete Silhouetten-Icons (Rechtecke/Vielecke) fuer
 * die Elemente des Logistik-Netzes - dienen als Sicherheitsnetz, falls im
 * Symbolordner keine gleichnamige .svg-Datei liegt (siehe
 * {@link SymbolRegistry}, {@link SvgIconLoader}). Jedes Icon ist eine kleine
 * {@link Group} aus JavaFX-Formen in einem 48x48-Raster, deren Fuellfarbe
 * sich per {@link #applyColor} zentral setzen laesst (siehe Rechtsklick-
 * Farbwahl in {@link NetzCanvas}).
 */
public final class NetzIcons {

    public static final Color DEFAULT_COLOR = Color.web("#5b7fa6");

    private NetzIcons() {
    }

    public static Group forTyp(String typ) {
        Group group = switch (typ) {
            case "ANLAGEN_INFRASTRUKTUR" -> anlagenInfrastruktur();
            case "BAHNHOF" -> bahnhof();
            case "HALTEPUNKT" -> haltepunkt();
            case "ANSCHLIESSER" -> anschliesser();
            case "FABRIK" -> fabrik();
            case "LAGER" -> lager();
            case "BETRIEBSWERK" -> betriebswerk();
            case "GUETERBAHNHOF" -> gueterbahnhof();
            case "SCHATTENBAHNHOF" -> schattenbahnhof();
            case "BEKOHLUNG" -> bekohlung();
            case "TANKSTELLE" -> tankstelle();
            case "FLUESSIGLAGER" -> fluessiglager();
            case "GASLAGER" -> gaslager();
            case "OFFENLAGER" -> offenlager();
            case "LAGERHALLE" -> lagerhalle();
            case "POST" -> post();
            case "PERSONENVERKEHR" -> personenverkehr();
            case "STADT" -> stadt();
            case "DORF" -> dorf();
            case "LAENDLICH" -> laendlich();
            case "LOKSTATION" -> lokstation();
            case "GUETERVERKEHR" -> gueterverkehr();
            case "GUETER" -> gueter();
            case "ROHSTOFFE" -> rohstoffe();
            case "FLUESSIGKEITEN" -> fluessigkeiten();
            case "STUECKGUT" -> stueckgut();
            case "OFFENE_GUETER" -> offeneGueter();
            case "PRODUKTION" -> produktion();
            case "WANDLUNG" -> wandlung();
            case "ANKOMMENDE_PERSONEN" -> ankommendePersonen();
            case "ABREISENDE_PERSONEN" -> abreisendePersonen();
            case "ANKOMMENDE_GUETER" -> ankommendeGueter();
            case "ABGEHENDE_GUETER" -> abgehendeGueter();
            case "KAPAZITAETEN" -> kapazitaeten();
            case "MODUL" -> svg("MODUL");
            case "REITER" -> svg("REITER");
            default -> unbekannt();
        };
        applyColor(group, DEFAULT_COLOR);
        return group;
    }

    /** Anzeigename fuer einen Ebene-1- oder Ebene-2-Typ (Enum-Name als Schluessel). */
    public static String labelFor(String typ) {
        try {
            return NetzKnotenTyp.valueOf(typ).getLabel();
        } catch (IllegalArgumentException ignored) {
            // kein Ebene-1-Typ
        }
        try {
            return OrtTyp.valueOf(typ).getLabel();
        } catch (IllegalArgumentException ignored) {
            // kein Ebene-2-Typ
        }
        return typ;
    }

    /**
     * Setzt die Fuellfarbe aller Formen im Icon (Rechtsklick "Farbe..." in
     * NetzCanvas) - steigt dafuer auch in verschachtelte Groups ab, damit das
     * auch fuer aus .svg geladene Icons mit {@code <g>}-Verschachtelung
     * funktioniert (siehe {@link SvgIconLoader}, das selbst zwar flach
     * baut, aber zukunftssicher).
     */
    public static void applyColor(Group icon, Color color) {
        for (var node : icon.getChildren()) {
            if (node instanceof Shape shape) {
                shape.setFill(color);
            } else if (node instanceof Group nested) {
                applyColor(nested, color);
            }
        }
    }

    private static Rectangle rect(double x, double y, double w, double h) {
        return new Rectangle(x, y, w, h);
    }

    private static Polygon poly(double... points) {
        return new Polygon(points);
    }

    /**
     * Zahnrad mit Kreispfeilen und Leiterbahnen (nach Vorlage des Anwenders, 08.10.2026) aus
     * default-symbole/ANLAGEN_INFRASTRUKTUR.svg; nur wenn das nicht lesbar ist, das alte Kreuz.
     */
    private static Group svg(String key) {
        try (java.io.InputStream in = NetzIcons.class.getResourceAsStream("default-symbole/" + key + ".svg")) {
            if (in != null) {
                return SvgIconLoader.load(in);
            }
        } catch (Exception ignored) {
            // Rueckfall unten
        }
        return unbekannt();
    }

    private static Group anlagenInfrastruktur() {
        try (java.io.InputStream in = NetzIcons.class.getResourceAsStream("default-symbole/ANLAGEN_INFRASTRUKTUR.svg")) {
            if (in != null) {
                return SvgIconLoader.load(in);
            }
        } catch (Exception ignored) {
            // Rueckfall unten
        }
        return new Group(
                rect(20, 20, 8, 8),
                rect(20, 2, 8, 6), rect(22, 8, 4, 12),
                rect(20, 40, 8, 6), rect(22, 28, 4, 12),
                rect(2, 20, 6, 8), rect(8, 22, 12, 4),
                rect(40, 20, 6, 8), rect(28, 22, 12, 4));
    }

    private static Group bahnhof() {
        return new Group(
                rect(8, 20, 32, 24),
                poly(4, 20, 24, 6, 44, 20));
    }

    private static Group haltepunkt() {
        return new Group(
                rect(22, 16, 4, 28),
                poly(8, 16, 40, 16, 34, 6, 14, 6));
    }

    private static Group anschliesser() {
        return new Group(
                rect(6, 24, 36, 20),
                rect(30, 6, 8, 18),
                poly(6, 24, 6, 14, 16, 24));
    }

    private static Group fabrik() {
        return new Group(
                rect(6, 26, 36, 18),
                rect(10, 10, 5, 18),
                rect(20, 4, 5, 24),
                rect(30, 14, 5, 14));
    }

    private static Group betriebswerk() {
        return new Group(
                rect(10, 22, 28, 22),
                poly(8, 22, 40, 22, 35, 11, 13, 11),
                poly(14, 11, 34, 11, 30, 4, 18, 4));
    }

    private static Group tankstelle() {
        return new Group(
                rect(14, 10, 16, 30),
                rect(11, 5, 22, 6),
                rect(30, 16, 8, 4),
                rect(36, 16, 4, 16));
    }

    private static Group lokstation() {
        return new Group(
                rect(6, 18, 30, 14),
                rect(26, 8, 14, 10),
                rect(10, 32, 6, 6),
                rect(26, 32, 6, 6));
    }

    private static Group schattenbahnhof() {
        javafx.scene.shape.SVGPath portal = new javafx.scene.shape.SVGPath();
        portal.setContent("M3 45 L3 22 A21 21 0 0 1 45 22 L45 45 Z M11 45 L11 24 A13 13 0 0 1 37 24 L37 45 Z");
        portal.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        return new Group(portal, rect(14, 41, 20, 2), rect(16, 36, 16, 2), rect(18, 31, 12, 2), rect(2, 45, 44, 2));
    }

    private static Group gueterbahnhof() {
        return new Group(
                rect(4, 28, 16, 16),
                rect(17, 18, 16, 26),
                rect(30, 30, 14, 14));
    }

    private static Group bekohlung() {
        return new Group(
                poly(6, 44, 24, 14, 42, 44),
                rect(19, 20, 4, 4),
                rect(26, 26, 4, 4));
    }

    private static Group personenverkehr() {
        return new Group(
                rect(18, 6, 12, 12),
                poly(10, 44, 16, 20, 32, 20, 38, 44));
    }

    private static Group gueterverkehr() {
        return new Group(
                rect(6, 16, 36, 20),
                rect(10, 36, 6, 6), rect(32, 36, 6, 6));
    }

    private static Group fluessiglager() {
        return new Group(
                rect(8, 14, 14, 30),
                rect(26, 14, 14, 30),
                rect(6, 42, 36, 3));
    }

    private static Group gaslager() {
        return new Group(
                poly(24, 6, 34, 10, 40, 18, 40, 30, 34, 38, 24, 42, 14, 38, 8, 30, 8, 18, 14, 10),
                rect(20, 42, 8, 4));
    }

    private static Group offenlager() {
        return new Group(
                poly(6, 20, 42, 20, 36, 44, 12, 44),
                rect(14, 26, 6, 6), rect(24, 26, 6, 6), rect(34, 26, 6, 6));
    }

    private static Group lagerhalle() {
        return new Group(
                rect(6, 18, 36, 26),
                poly(4, 18, 24, 4, 44, 18),
                rect(12, 30, 6, 14), rect(21, 30, 6, 14), rect(30, 30, 6, 14));
    }

    private static Group rohstoffe() {
        return new Group(
                poly(6, 40, 16, 16, 26, 40),
                poly(22, 40, 32, 20, 42, 40));
    }

    private static Group fluessigkeiten() {
        return new Group(
                poly(24, 6, 38, 30, 34, 42, 14, 42, 10, 30));
    }

    private static Group post() {
        return new Group(
                rect(6, 12, 36, 24),
                poly(6, 12, 24, 28, 42, 12));
    }

    private static Group stueckgut() {
        return new Group(
                rect(10, 10, 20, 20),
                rect(24, 20, 16, 16));
    }

    private static Group offeneGueter() {
        return new Group(poly(6, 18, 42, 18, 36, 42, 12, 42));
    }

    private static Group produktion() {
        return new Group(
                rect(8, 26, 32, 18),
                rect(14, 8, 6, 18), rect(24, 4, 6, 22), rect(34, 12, 6, 14));
    }

    private static Group wandlung() {
        return new Group(
                poly(6, 14, 26, 14, 26, 8, 40, 18, 26, 28, 26, 22, 6, 22),
                poly(42, 26, 22, 26, 22, 18, 8, 30, 22, 42, 22, 34, 42, 34));
    }

    private static Group unbekannt() {
        return new Group(rect(14, 14, 20, 20));
    }

    private static Group lager() {
        return new Group(
                rect(8, 20, 32, 24),
                poly(4, 20, 24, 8, 44, 20),
                rect(12, 26, 24, 3),
                rect(12, 32, 24, 3),
                rect(12, 38, 24, 3));
    }

    private static Group gueter() {
        return new Group(
                rect(8, 22, 16, 16),
                rect(24, 14, 16, 24));
    }

    private static Group stadt() {
        return new Group(
                rect(6, 20, 8, 24),
                rect(16, 10, 8, 34),
                rect(26, 16, 8, 28),
                rect(36, 24, 8, 20));
    }

    private static Group dorf() {
        return new Group(
                rect(8, 26, 12, 16), poly(6, 26, 14, 16, 22, 26),
                rect(26, 30, 12, 12), poly(24, 30, 32, 22, 40, 30));
    }

    private static Group laendlich() {
        return new Group(
                poly(24, 6, 34, 26, 14, 26), rect(22, 26, 4, 14),
                rect(4, 42, 10, 4), rect(34, 42, 10, 4));
    }

    private static Group ankommendePersonen() {
        return new Group(
                rect(18, 6, 12, 12),
                poly(10, 44, 16, 22, 32, 22, 38, 44),
                poly(2, 16, 10, 16, 10, 10, 16, 16, 10, 22, 10, 16));
    }

    private static Group abreisendePersonen() {
        return new Group(
                rect(18, 6, 12, 12),
                poly(10, 44, 16, 22, 32, 22, 38, 44),
                poly(46, 16, 38, 16, 38, 10, 32, 16, 38, 22, 38, 16));
    }

    private static Group ankommendeGueter() {
        return new Group(
                rect(10, 18, 30, 18),
                poly(2, 12, 10, 12, 10, 6, 16, 12, 10, 18, 10, 12));
    }

    private static Group abgehendeGueter() {
        return new Group(
                rect(8, 18, 30, 18),
                poly(46, 12, 38, 12, 38, 6, 32, 12, 38, 18, 38, 12));
    }

    private static Group kapazitaeten() {
        return new Group(
                rect(8, 30, 8, 14),
                rect(20, 20, 8, 24),
                rect(32, 10, 8, 34));
    }
}
