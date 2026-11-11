package com.example.itrain_logistik;

import javafx.scene.paint.Color;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Erzeugt zur Laufzeit ein Stylesheet aus zwei frei gewaehlten Farben
 * (Hintergrund, Textfarbe). Wortgleich aus iTrain_import_export
 * uebernommen - reine UI-Hilfsklasse ohne Projektbezug.
 */
final class CustomColorTheme {

    private static final String ACCENT = "#3574c9";

    private CustomColorTheme() {
    }

    static String buildStylesheet(String backgroundWeb, String textWeb) {
        Color background = parse(backgroundWeb);
        Color text = parse(textWeb);
        if (background == null || text == null) {
            return null;
        }

        String bg = web(background);
        String txt = web(text);
        String surface = web(shift(background, 0.08));
        String surfaceStrong = web(shift(background, 0.16));
        String field = web(shift(background, 0.12));
        String fieldHover = web(shift(background, 0.20));
        String edgeLight = web(background.interpolate(Color.WHITE, 0.65));
        String edgeDark = web(background.interpolate(Color.BLACK, 0.55));

        String css = ".root {\n"
                + "  -fx-base: " + bg + ";\n"
                + "  -fx-background: " + bg + ";\n"
                + "  -fx-control-inner-background: " + field + ";\n"
                + "  -fx-control-inner-background-alt: " + field + ";\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "  -fx-accent: " + ACCENT + ";\n"
                + "  -fx-focus-color: " + ACCENT + ";\n"
                + "}\n"
                + ".label, .text, .check-box, .radio-button, .menu-item .label, .tab .tab-label {\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".menu-bar, .tool-bar {\n"
                + "  -fx-background-color: " + surface + ";\n"
                + "}\n"
                + ".menu-bar .label {\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".tab-pane .tab-header-area .tab {\n"
                + "  -fx-background-color: " + field + ";\n"
                + "}\n"
                + ".tab-pane .tab-header-area .tab:selected {\n"
                + "  -fx-background-color: " + surfaceStrong + ";\n"
                + "}\n"
                + ".table-view, .tree-view, .list-view {\n"
                + "  -fx-background-color: " + field + ";\n"
                + "  -fx-control-inner-background: " + field + ";\n"
                + "}\n"
                + ".table-view .column-header, .table-view .filler {\n"
                + "  -fx-background-color: " + surface + ";\n"
                + "}\n"
                + ".table-row-cell, .tree-cell, .list-cell {\n"
                + "  -fx-background-color: " + field + ";\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".table-row-cell:odd, .list-cell:odd {\n"
                + "  -fx-background-color: " + surface + ";\n"
                + "}\n"
                + ".table-row-cell:selected, .tree-cell:selected, .list-cell:selected {\n"
                + "  -fx-background-color: " + ACCENT + ";\n"
                + "}\n"
                + ".text-field, .text-area, .combo-box, .choice-box {\n"
                + "  -fx-background-color: " + field + ";\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".button {\n"
                + "  -fx-background-color: " + field + ";\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".button:hover {\n"
                + "  -fx-background-color: " + fieldHover + ";\n"
                + "}\n"
                + ".tool-bar .button {\n"
                + "  -fx-border-color: " + edgeLight + " " + edgeDark + " " + edgeDark + " " + edgeLight + ";\n"
                + "  -fx-border-width: 2;\n"
                + "  -fx-border-radius: 3;\n"
                + "  -fx-background-radius: 3;\n"
                + "}\n"
                + ".tool-bar .button:pressed {\n"
                + "  -fx-border-color: " + edgeDark + " " + edgeLight + " " + edgeLight + " " + edgeDark + ";\n"
                + "}\n"
                + ".split-pane, .split-pane-divider {\n"
                + "  -fx-background-color: " + bg + ";\n"
                + "}\n"
                + ".dialog-pane, .dialog-pane > .content, .dialog-pane > .button-bar, .dialog-pane > .header-panel {\n"
                + "  -fx-background-color: " + bg + ";\n"
                + "}\n"
                + ".dialog-pane > .content.label, .dialog-pane .header-panel .label {\n"
                + "  -fx-text-fill: " + txt + ";\n"
                + "}\n"
                + ".scroll-pane, .scroll-pane > .viewport {\n"
                + "  -fx-background-color: " + bg + ";\n"
                + "}\n";

        return "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    private static Color parse(String web) {
        if (web == null || web.isBlank()) {
            return null;
        }
        try {
            return Color.web(web);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Color shift(Color base, double amount) {
        double luminance = 0.299 * base.getRed() + 0.587 * base.getGreen() + 0.114 * base.getBlue();
        return luminance < 0.5 ? base.interpolate(Color.WHITE, amount) : base.interpolate(Color.BLACK, amount);
    }

    private static String web(Color color) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
