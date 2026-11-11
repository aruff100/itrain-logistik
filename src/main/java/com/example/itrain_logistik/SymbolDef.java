package com.example.itrain_logistik;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.File;

/**
 * Ein Symbol im Symbol-Katalog: entweder eine aus dem Symbolordner geladene
 * echte .svg-Datei ({@link Art#SVG}, der Regelfall - siehe Projektauftrag
 * "Die Symbole ... sind immer .svg"), eines der eingebauten
 * Ersatz-Piktogramme ({@link Art#ORIGINAL}, gezeichnet von {@link NetzIcons}
 * - nur als Sicherheitsnetz, falls die zugehoerige .svg-Datei fehlt oder
 * sich nicht lesen laesst) oder ein selbst angelegtes einfaches Rechteck
 * ({@link Art#RECHTECK} - siehe {@link SymbolRegistry}).
 */
public final class SymbolDef {

    public enum Art { SVG, ORIGINAL, RECHTECK }

    private final String id;
    private final String name;
    private final Art art;
    /**
     * Bei SVG: absoluter Pfad zur .svg-Datei im Symbolordner. Bei ORIGINAL:
     * der NetzIcons-Schluessel (auch als Fallback-Schluessel bei SVG
     * genutzt, falls die Datei nicht mehr lesbar ist). Bei RECHTECK: die
     * Farbe als Web-Hex.
     */
    private final String wert;
    /** Nur bei SVG: der NetzIcons-Fallback-Schluessel, falls die Datei fehlt/kaputt ist - sonst null. */
    private final String fallbackTyp;

    public SymbolDef(String id, String name, Art art, String wert) {
        this(id, name, art, wert, null);
    }

    public SymbolDef(String id, String name, Art art, String wert, String fallbackTyp) {
        this.id = id;
        this.name = name;
        this.art = art;
        this.wert = wert;
        this.fallbackTyp = fallbackTyp;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Art getArt() {
        return art;
    }

    public String getWert() {
        return wert;
    }

    public Group createIcon() {
        if (art == Art.SVG) {
            try {
                return SvgIconLoader.load(new File(wert));
            } catch (Exception ex) {
                // Datei fehlt/ist beschaedigt - Sicherheitsnetz statt Abbruch.
                return NetzIcons.forTyp(fallbackTyp != null ? fallbackTyp : "");
            }
        }
        if (art == Art.ORIGINAL) {
            return NetzIcons.forTyp(wert);
        }
        Rectangle rect = new Rectangle(6, 6, 36, 36);
        try {
            rect.setFill(Color.web(wert));
        } catch (Exception ex) {
            rect.setFill(NetzIcons.DEFAULT_COLOR);
        }
        return new Group(rect);
    }
}
