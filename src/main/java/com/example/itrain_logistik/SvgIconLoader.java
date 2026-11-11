package com.example.itrain_logistik;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Affine;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest eine einfache, flache .svg-Datei (Piktogramm-Stil: {@code <path>},
 * {@code <rect>}, {@code <circle>}, {@code <ellipse>}, {@code <line>},
 * {@code <polygon>}, {@code <polyline>}, beliebig tief in {@code <g>}
 * verschachtelt) direkt in einen JavaFX-{@link Group}-Baum ein - ohne externe
 * Bibliothek. JavaFX kennt die SVG-Pfad-Mini-Sprache bereits nativ ueber
 * {@link SVGPath#setContent(String)}, das deckt zusammen mit den einfachen
 * Grundformen den ueberwiegenden Teil normaler, flacher Vektor-Piktogramme ab
 * (siehe Projektauftrag "Die Symbole ... sind immer .svg"). NICHT
 * unterstuetzt: Farbverlaeufe, eingebettete Rasterbilder, CSS-Selektoren
 * jenseits von style="fill:...". Fuer solche Faelle liesse sich spaeter eine
 * externe SVG-Bibliothek ergaenzen - fuer Piktogramme reicht dieser schlanke,
 * dependency-freie Ansatz (wichtig fuer jlink/jpackage auf drei Plattformen
 * ohne zusaetzliche native/nicht-modulare Abhaengigkeiten).
 * <p>
 * Alle Formen landen FLACH als direkte Kinder der zurueckgegebenen
 * {@link Group} (kein verschachteltes {@code <g>} als eigene Unter-Group),
 * damit {@link NetzIcons#applyColor(Group, Color)} sie weiterhin einfach
 * einfaerben kann; ein Transform aus einem {@code <g transform="...">} wird
 * dafuer direkt in die Koordinaten der Kind-Formen "eingebrannt". Die ganze
 * Group wird anschliessend anhand von viewBox/width/height auf ein
 * einheitliches 48x48-Raster skaliert, damit sie sich wie die eingebauten
 * Piktogramme verhaelt.
 */
public final class SvgIconLoader {

    private static final double TARGET_SIZE = 48.0;
    private static final Pattern NUMBER = Pattern.compile("-?[0-9]*\\.?[0-9]+(?:[eE]-?[0-9]+)?");

    private SvgIconLoader() {
    }

    public static Group load(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        return build(document);
    }

    public static Group load(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(in);
        return build(document);
    }

    private static Group build(Document document) {
        Element root = document.getDocumentElement();
        List<Shape> shapes = new ArrayList<>();
        walk(root, shapes, "#000000");

        Group group = new Group(shapes.toArray(new Shape[0]));

        double[] box = viewBoxOf(root);
        if (box != null && box[2] > 0 && box[3] > 0) {
            double scale = TARGET_SIZE / Math.max(box[2], box[3]);
            // Eine einzelne Affine-Matrix statt zweier einzelner Transforms
            // (Scale + Translate) in der transforms-Liste: bei zweien haengt
            // das Ergebnis von der - leicht zu verwechselnden - Reihenfolge
            // ab, in der JavaFX sie kombiniert. Hier ist die Zielabbildung
            // fest verdrahtet: x' = scale*(x-box0), y' = scale*(y-box1), das
            // ist unabhaengig von jeder Kombinationsreihenfolge immer
            // korrekt (siehe Projektauftrag zum Ang_Infra.svg-Fehler: "Das
            // Bild ist nicht im Rahmen" - bei viewBox-Ursprung (0,0), wie bei
            // allen bisherigen Symbolen, ist das ohnehin identisch zu vorher).
            group.getTransforms().add(new Affine(
                    scale, 0, -scale * box[0],
                    0, scale, -scale * box[1]));
        }
        return group;
    }

    /** {@code viewBox="minX minY width height"}, sonst Ersatz ueber width/height, sonst null (keine Skalierung). */
    private static double[] viewBoxOf(Element root) {
        String viewBox = root.getAttribute("viewBox");
        if (viewBox != null && !viewBox.isBlank()) {
            List<Double> nums = numbers(viewBox);
            if (nums.size() == 4) {
                return new double[]{nums.get(0), nums.get(1), nums.get(2), nums.get(3)};
            }
        }
        double w = parseLength(root.getAttribute("width"));
        double h = parseLength(root.getAttribute("height"));
        if (w > 0 && h > 0) {
            return new double[]{0, 0, w, h};
        }
        return null;
    }

    private static void walk(Node parent, List<Shape> out, String inheritedFill) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) child;
            String fill = resolveFill(el, inheritedFill);
            String tag = el.getNodeName();
            switch (tag) {
                case "g", "svg" -> walk(el, out, fill);
                case "path" -> {
                    String d = el.getAttribute("d");
                    if (!d.isBlank()) {
                        SVGPath path = new SVGPath();
                        try {
                            path.setContent(d);
                            if ("evenodd".equalsIgnoreCase(el.getAttribute("fill-rule").trim())) {
                                path.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
                            }
                            out.add(colored(path, fill));
                        } catch (Exception ignored) {
                            // ungueltiger Pfad in der Datei - einfach auslassen statt abzubrechen
                        }
                    }
                }
                case "rect" -> {
                    double x = parseLength(el.getAttribute("x"));
                    double y = parseLength(el.getAttribute("y"));
                    double w = parseLength(el.getAttribute("width"));
                    double h = parseLength(el.getAttribute("height"));
                    if (w > 0 && h > 0) {
                        out.add(colored(new Rectangle(x, y, w, h), fill));
                    }
                }
                case "circle" -> {
                    double cx = parseLength(el.getAttribute("cx"));
                    double cy = parseLength(el.getAttribute("cy"));
                    double r = parseLength(el.getAttribute("r"));
                    if (r > 0) {
                        out.add(colored(new Circle(cx, cy, r), fill));
                    }
                }
                case "ellipse" -> {
                    double cx = parseLength(el.getAttribute("cx"));
                    double cy = parseLength(el.getAttribute("cy"));
                    double rx = parseLength(el.getAttribute("rx"));
                    double ry = parseLength(el.getAttribute("ry"));
                    if (rx > 0 && ry > 0) {
                        out.add(colored(new Ellipse(cx, cy, rx, ry), fill));
                    }
                }
                case "line" -> {
                    double x1 = parseLength(el.getAttribute("x1"));
                    double y1 = parseLength(el.getAttribute("y1"));
                    double x2 = parseLength(el.getAttribute("x2"));
                    double y2 = parseLength(el.getAttribute("y2"));
                    out.add(colored(new Line(x1, y1, x2, y2), fill));
                }
                case "polygon" -> {
                    double[] pts = pointsOf(el.getAttribute("points"));
                    if (pts.length >= 6) {
                        out.add(colored(new Polygon(pts), fill));
                    }
                }
                case "polyline" -> {
                    double[] pts = pointsOf(el.getAttribute("points"));
                    if (pts.length >= 4) {
                        out.add(colored(new Polyline(pts), fill));
                    }
                }
                default -> {
                    // Unbekanntes/nicht unterstuetztes Element (z.B. <defs>, <linearGradient>,
                    // <image>) - einfach ignorieren statt die ganze Datei abzulehnen.
                }
            }
        }
    }

    private static Shape colored(Shape shape, String fillHex) {
        try {
            shape.setFill(Color.web(fillHex));
        } catch (Exception ex) {
            shape.setFill(Color.BLACK);
        }
        return shape;
    }

    /** {@code fill="#rrggbb"} bzw. {@code style="fill:#rrggbb;..."}, sonst vom Elternelement geerbt. */
    private static String resolveFill(Element el, String inherited) {
        String fill = el.getAttribute("fill");
        if (fill != null && !fill.isBlank() && !"inherit".equalsIgnoreCase(fill)) {
            return fill;
        }
        String style = el.getAttribute("style");
        if (style != null && !style.isBlank()) {
            for (String part : style.split(";")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2 && kv[0].trim().equalsIgnoreCase("fill")) {
                    String value = kv[1].trim();
                    if (!value.isBlank() && !"none".equalsIgnoreCase(value)) {
                        return value;
                    }
                }
            }
        }
        return inherited;
    }

    private static double[] pointsOf(String raw) {
        List<Double> nums = numbers(raw);
        double[] result = new double[nums.size()];
        for (int i = 0; i < nums.size(); i++) {
            result[i] = nums.get(i);
        }
        return result;
    }

    private static List<Double> numbers(String raw) {
        List<Double> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        Matcher m = NUMBER.matcher(raw);
        while (m.find()) {
            result.add(Double.parseDouble(m.group()));
        }
        return result;
    }

    private static double parseLength(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        Matcher m = NUMBER.matcher(raw);
        if (m.find()) {
            return Double.parseDouble(m.group());
        }
        return 0;
    }
}
