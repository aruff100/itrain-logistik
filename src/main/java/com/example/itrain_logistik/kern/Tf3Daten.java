package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Grundbestand aus Transport Fever 3: 36 Gueter und 32 Industrien mit Rezepten und
 * Lagergroessen. Erzeugt aus analyse/tf3-spieldaten.json (ausgelesen aus der installierten
 * Spielfassung, siehe analyse/Transport-Fever-3-Spieldaten.md).
 * <p>
 * Abweichung von TF3: Duenger, Werkzeuge und Maschinen sind in TF3 nur Booster der
 * Rohstoffbetriebe (Produktion laeuft auch ohne sie). Laut Entscheidung des Anwenders vom
 * 08.10.2026 sind sie hier ein Pflicht-Eingang. Die Menge je Ausfuehrung ist abgeleitet:
 * Booster-Verbrauch pro Jahr (4 Ausfuehrungen der Booster-Regel) geteilt durch die
 * Produktionsausfuehrungen pro Jahr, gerundet, mindestens 1. Beispiel Bauernhof:
 * 4 x 17 Duenger / 11,4 = 6 Duenger je Ausfuehrung.
 */
public final class Tf3Daten {

    /** Gueter, die in TF3 als Booster der Rohstoffbetriebe dienen. */
    public static final Set<String> BOOSTER_GUETER = Set.of("fertilizer", "tools", "machines");

    private Tf3Daten() {
    }

    /** Gut-Ids für Post und Expressgut, Kapazität je Personenwagen (Anwender 27.10.2026). */
    public static final String POST = "mail";
    public static final String EXPRESSGUT = "express";
    public static final double POST_KAPAZITAET = 10;

    public static boolean imPersonenzug(String gutId) {
        return POST.equals(gutId) || EXPRESSGUT.equals(gutId);
    }

    /**
     * Post bzw. Expressgut je Modellstunde als Anteil der Abreisenden eines Orts (Annahme, nicht vom Anwender):
     * Stadt 10 % / 5 %, Dorf 5 % / 2 %, Ländlich 2 % / 1 %.
     */
    public static double postAnteil(ObjektArt art, String gutId) {
        boolean post = POST.equals(gutId);
        return switch (art) {
            case STADT -> post ? 0.10 : 0.05;
            case DORF -> post ? 0.05 : 0.02;
            case LAENDLICH -> post ? 0.02 : 0.01;
            default -> 0;
        };
    }

    public static List<Gut> gueter() {
        List<Gut> l = new ArrayList<>();
        gut(l, "grain", "Getreide", Ladeklasse.SCHUETTGUT, "#E6D191", GutStufe.ROHSTOFF, null);
        gut(l, "dyes", "Farbstoffe", Ladeklasse.FLUESSIGGUT, "#8A2691", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "fertilizer", "Dünger", Ladeklasse.SCHUETTGUT, "#4CC908", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "sawdust", "Sägespäne", Ladeklasse.SCHUETTGUT, "#FFFA8F", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "sand", "Sand", Ladeklasse.SCHUETTGUT, "#F0DBB8", GutStufe.ROHSTOFF, null);
        gut(l, "fish", "Fisch", Ladeklasse.STUECKGUT, "#4FB8C9", GutStufe.ROHSTOFF, StadtBereich.GEWERBE);
        gut(l, "meat", "Fleisch", Ladeklasse.STUECKGUT, "#9E0003", GutStufe.ZWISCHENPRODUKT, StadtBereich.GEWERBE);
        gut(l, "beverages", "Getränke", Ladeklasse.STUECKGUT, "#407817", GutStufe.ENDPRODUKT, StadtBereich.GEWERBE);
        gut(l, "vegetables", "Gemüse", Ladeklasse.STUECKGUT, "#26C9BF", GutStufe.ROHSTOFF, StadtBereich.GEWERBE);
        gut(l, "sheet_metal", "Blech", Ladeklasse.SPERRGUT, "#D4D4D4", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "steel", "Stahl", Ladeklasse.SPERRGUT, "#A3A3A3", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "logs", "Holz", Ladeklasse.SPERRGUT, "#784214", GutStufe.ROHSTOFF, null);
        gut(l, "planks", "Bretter", Ladeklasse.SPERRGUT, "#F0BD66", GutStufe.ZWISCHENPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "vehicles", "Fahrzeuge", Ladeklasse.SPERRGUT, "#0AD6FA", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "cement", "Zement", Ladeklasse.SCHUETTGUT, "#808080", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "machines", "Maschinen", Ladeklasse.SPERRGUT, "#A3A3A3", GutStufe.ZWISCHENPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "rubber", "Gummi", Ladeklasse.FLUESSIGGUT, "#1A1A1A", GutStufe.ROHSTOFF, null);
        gut(l, "crude_oil", "Rohöl", Ladeklasse.FLUESSIGGUT, "#0A0A0A", GutStufe.ROHSTOFF, null);
        gut(l, "chemicals", "Chemikalien", Ladeklasse.FLUESSIGGUT, "#FFFC54", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "fuel", "Treibstoff", Ladeklasse.FLUESSIGGUT, "#F0A347", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "iron_ore", "Eisenerz", Ladeklasse.SCHUETTGUT, "#F0874F", GutStufe.ROHSTOFF, null);
        gut(l, "coal", "Kohle", Ladeklasse.SCHUETTGUT, "#333333", GutStufe.ROHSTOFF, null);
        gut(l, "clay", "Lehm", Ladeklasse.SCHUETTGUT, "#F09C59", GutStufe.ROHSTOFF, null);
        gut(l, "stone", "Stein", Ladeklasse.SCHUETTGUT, "#707070", GutStufe.ROHSTOFF, null);
        gut(l, "wool", "Wolle", Ladeklasse.STUECKGUT, "#FFFAE0", GutStufe.ROHSTOFF, null);
        gut(l, "fabric", "Stoff", Ladeklasse.STUECKGUT, "#4F8A91", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "clothes", "Kleidung", Ladeklasse.STUECKGUT, "#FFFFFF", GutStufe.ENDPRODUKT, StadtBereich.GEWERBE);
        gut(l, "tinned_food", "Konserven", Ladeklasse.STUECKGUT, "#C2C2C2", GutStufe.ENDPRODUKT, StadtBereich.GEWERBE);
        gut(l, "plastic", "Kunststoff", Ladeklasse.STUECKGUT, "#C282DB", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "tools", "Werkzeuge", Ladeklasse.STUECKGUT, "#7DABC9", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "furniture", "Möbel", Ladeklasse.STUECKGUT, "#E38029", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "glass", "Glas", Ladeklasse.STUECKGUT, "#ABE0E3", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "bricks", "Ziegel", Ladeklasse.STUECKGUT, "#F0874F", GutStufe.ENDPRODUKT, StadtBereich.INDUSTRIE);
        gut(l, "paper", "Papier", Ladeklasse.STUECKGUT, "#DBDBDB", GutStufe.ZWISCHENPRODUKT, null);
        gut(l, "books", "Bücher", Ladeklasse.STUECKGUT, "#4F4012", GutStufe.ENDPRODUKT, StadtBereich.GEWERBE);
        gut(l, "tires", "Reifen", Ladeklasse.STUECKGUT, "#4F4F4F", GutStufe.ZWISCHENPRODUKT, null);
        // kein TF3-Gut: vom Anwender ergänzt (27.10.2026), Stückgut in Stück statt Gebinde
        gut(l, "suitcases", "Koffer", Ladeklasse.STUECKGUT, "#8B5A2B", GutStufe.ENDPRODUKT, null);
        l.get(l.size() - 1).setEinheit("Stück");
        // Post und Expressgut: entstehen in den Orten, fahren nur im Personenzug (Anwender 27.10.2026)
        gut(l, POST, "Post", Ladeklasse.STUECKGUT, "#F2C200", GutStufe.ENDPRODUKT, null);
        l.get(l.size() - 1).setEinheit("Stück");
        gut(l, EXPRESSGUT, "Expressgut", Ladeklasse.STUECKGUT, "#D9381E", GutStufe.ENDPRODUKT, null);
        l.get(l.size() - 1).setEinheit("Stück");
        return l;
    }

    public static List<BetriebVorlage> vorlagen() {
        List<BetriebVorlage> v = new ArrayList<>();
        vorlage(v, "brewery", "Brauerei", map("grain", 400, "glass", 200), map("beverages", 400),
                rezept(map("grain", 8, "glass", 3), map("beverages", 8)));
        vorlage(v, "bricks_works", "Ziegelei", map("clay", 800), map("bricks", 400),
                rezept(map("clay", 4), map("bricks", 4)));
        vorlage(v, "cement_plant", "Zementwerk", map("stone", 800), map("cement", 400),
                rezept(map("stone", 4), map("cement", 2)));
        vorlage(v, "chemical_plant", "Chemiefabrik", map("chemicals", 800), map("dyes", 300, "plastic", 700),
                rezept(map("chemicals", 6), map("dyes", 2, "plastic", 5)));
        vorlage(v, "clay_pit", "Lehmgrube", map("tools", 100), map("clay", 400),
                rezept(map("tools", 2), map("clay", 16)));
        vorlage(v, "coal_mine", "Kohlebergwerk", map("tools", 100), map("coal", 400),
                rezept(map("tools", 3), map("coal", 16)));
        vorlage(v, "cotton_farm", "Baumwollplantage", map("fertilizer", 100), map("wool", 300),
                rezept(map("fertilizer", 3), map("wool", 12)));
        vorlage(v, "farm", "Bauernhof", map("fertilizer", 100), map("vegetables", 200, "grain", 300),
                rezept(map("fertilizer", 6), map("vegetables", 16, "grain", 24)));
        vorlage(v, "fishing_grounds", "Fischzucht", map("tools", 100), map("fish", 300),
                rezept(map("tools", 2), map("fish", 12)));
        vorlage(v, "food_factory", "Konservenfabrik", map("fish", 400, "meat", 400, "sheet_metal", 200), map("tinned_food", 200),
                rezept(map("fish", 2, "sheet_metal", 1), map("tinned_food", 2)),
                rezept(map("meat", 2, "sheet_metal", 1), map("tinned_food", 2)));
        vorlage(v, "forest", "Holzfällerlager", map("machines", 100), map("logs", 300),
                rezept(map("machines", 2), map("logs", 8)));
        vorlage(v, "furniture_factory", "Möbelfabrik", map("planks", 800, "glass", 200), map("furniture", 400),
                rezept(map("planks", 4, "glass", 1), map("furniture", 2)));
        vorlage(v, "glass_works", "Glashütte", map("sand", 800), map("glass", 400),
                rezept(map("sand", 4), map("glass", 2)));
        vorlage(v, "iron_ore_mine", "Eisenerzbergwerk", map("machines", 100), map("iron_ore", 400),
                rezept(map("machines", 3), map("iron_ore", 16)));
        vorlage(v, "livestock_farm", "Viehzuchtbetrieb", map("grain", 800), map("meat", 400, "wool", 500),
                rezept(map("grain", 8), map("meat", 4, "wool", 5)));
        vorlage(v, "machine_factory", "Maschinenfabrik", map("steel", 400, "plastic", 200), map("machines", 400),
                rezept(map("steel", 7, "plastic", 4), map("machines", 7)));
        vorlage(v, "oil_platform", "Ölplattform", map("machines", 100), map("crude_oil", 400),
                rezept(map("machines", 1), map("crude_oil", 4)));
        vorlage(v, "oil_refinery", "Ölraffinerie", map("crude_oil", 800), map("fuel", 400, "chemicals", 800, "fertilizer", 300),
                rezept(map("crude_oil", 4), map("fuel", 2, "fertilizer", 1, "chemicals", 4)));
        vorlage(v, "oil_well", "Ölquelle", map("machines", 100), map("crude_oil", 600),
                rezept(map("machines", 1), map("crude_oil", 6)));
        vorlage(v, "paper_mill", "Papierfabrik", map("sawdust", 800), map("paper", 400),
                rezept(map("sawdust", 4), map("paper", 2)));
        vorlage(v, "printing_press", "Druckerei", map("paper", 400, "dyes", 200), map("books", 400),
                rezept(map("paper", 4, "dyes", 2), map("books", 4)));
        vorlage(v, "quarry", "Steinbruch", map("tools", 100), map("stone", 400),
                rezept(map("tools", 2), map("stone", 16)));
        vorlage(v, "rubber_farm", "Gummiplantage", map("fertilizer", 100), map("rubber", 400),
                rezept(map("fertilizer", 6), map("rubber", 32)));
        vorlage(v, "sand_excavator", "Sandbaggerschiff", map("machines", 100), map("sand", 400),
                rezept(map("machines", 1), map("sand", 8)));
        vorlage(v, "sand_pit", "Sandgrube", map("tools", 100), map("sand", 400),
                rezept(map("tools", 1), map("sand", 8)));
        vorlage(v, "saw_mill", "Sägewerk", map("logs", 400), map("planks", 800, "sawdust", 400),
                rezept(map("logs", 4), map("planks", 8, "sawdust", 4)));
        vorlage(v, "steel_mill", "Stahlwerk", map("iron_ore", 800, "coal", 800), map("steel", 400, "sheet_metal", 500),
                rezept(map("iron_ore", 6, "coal", 6), map("steel", 3, "sheet_metal", 4)));
        vorlage(v, "textile_factory", "Textilfabrik", map("fabric", 500, "dyes", 300), map("clothes", 400),
                rezept(map("fabric", 4, "dyes", 2), map("clothes", 3)));
        vorlage(v, "tire_factory", "Reifenfabrik", map("rubber", 800), map("tires", 400),
                rezept(map("rubber", 4), map("tires", 2)));
        vorlage(v, "tool_factory", "Werkzeugfabrik", map("planks", 500, "plastic", 300), map("tools", 700),
                rezept(map("planks", 4, "plastic", 2), map("tools", 5)));
        vorlage(v, "vehicle_factory", "Fahrzeugfabrik", map("tires", 400, "sheet_metal", 800, "machines", 400), map("vehicles", 400),
                rezept(map("tires", 1, "sheet_metal", 4, "machines", 1), map("vehicles", 1)));
        vorlage(v, "weaving_mill", "Weberei", map("wool", 500), map("fabric", 500),
                rezept(map("wool", 4), map("fabric", 4)));
        return Collections.unmodifiableList(v);
    }

    private static void gut(List<Gut> l, String id, String name, Ladeklasse klasse, String farbe, GutStufe stufe,
            StadtBereich bereich) {
        l.add(new Gut(id, name, klasse, farbe, stufe, bereich));
    }

    private static void vorlage(List<BetriebVorlage> v, String id, String name, Map<String, Integer> ein,
            Map<String, Integer> aus, Rezept... rezepte) {
        v.add(new BetriebVorlage(id, name, ein, aus, List.of(rezepte)));
    }

    private static Rezept rezept(Map<String, Integer> ein, Map<String, Integer> aus) {
        return new Rezept(ein, aus);
    }

    private static Map<String, Integer> map(Object... paare) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < paare.length; i += 2) {
            m.put((String) paare[i], (Integer) paare[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }
}
