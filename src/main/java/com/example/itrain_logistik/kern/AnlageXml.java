package com.example.itrain_logistik.kern;

import com.example.itrain_logistik.NetzText;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Speichert und laedt die {@link Anlage} als Abschnitt {@code <anlage>} der logistik.xml in der
 * .logz. Neben der Konfiguration werden auch die Bestaende (Betriebe, Lager, Gleise, wartende
 * Passagiere, Zugladungen) gespeichert, damit ein Neustart den Stand nicht verliert.
 */
public final class AnlageXml {

    private AnlageXml() {
    }

    // ================================================================ Schreiben

    public static void schreibe(Anlage a, StringBuilder x) {
        x.append("  <anlage massstab=\"").append(zahl(a.getMengenMassstab()))
                .append(a.getFlaecheBreite() > 0 ? "\" flaecheBreite=\"" + zahl(a.getFlaecheBreite())
                        + "\" flaecheHoehe=\"" + zahl(a.getFlaecheHoehe()) : "")
                .append("Anlage".equals(a.getHauptReiterName()) ? "" : "\" hauptReiter=\"" + esc(a.getHauptReiterName()))
                .append("\" tempoStufe=\"").append(a.getTempo())
                .append("\" umladeVorgabe=\"").append(zahl(Gleis.UMLADE_VORGABE))
                .append("\" ladegleisVorgabe=\"").append(Gleis.LADEGLEIS_VORGABE)
                .append(a.isBahnhoefeEingefuegt() ? "" : "\" bahnhoefeEingefuegt=\"false")
                .append(a.isErsterPersonenTransferOffen() ? "\" ersterPersonenTransfer=\"offen" : "")
                .append(a.isErsterGueterTransferOffen() ? "\" ersterGueterTransfer=\"offen" : "")
                .append("\">\n");
        x.append("    <gueter>\n");
        for (Gut g : a.getGueter().values()) {
            x.append("      <gut");
            attr(x, "id", g.getId());
            attr(x, "name", g.getName());
            attr(x, "klasse", g.getLadeklasse().name());
            attr(x, "farbe", g.getFarbe());
            attr(x, "stufe", g.getStufe() != null ? g.getStufe().name() : null);
            attr(x, "bereich", g.getStadtBereich() != null ? g.getStadtBereich().name() : null);
            attr(x, "einheit", g.getEigeneEinheit());
            if (g.getEigeneEinheit() != null && g.getKapazitaetJeEinheit() != 1) {
                attr(x, "jeEinheit", zahl(g.getKapazitaetJeEinheit()));
            }
            if (g.getMischbarEinstellung() != null) {
                attr(x, "mischbar", String.valueOf(g.getMischbarEinstellung()));
            }
            x.append("/>\n");
        }
        for (String id : a.getGeloeschteGueter()) {
            x.append("      <geloescht");
            attr(x, "id", id);
            x.append("/>\n");
        }
        x.append("    </gueter>\n");
        x.append("    <objekte>\n");
        for (AnlagenObjekt o : a.getObjekte()) {
            schreibeObjekt(o, x);
        }
        x.append("    </objekte>\n");
        if (!a.getTexte().isEmpty()) {
            x.append("    <texte>\n");
            for (NetzText t : a.getTexte()) {
                schreibeText(t, x);
            }
            x.append("    </texte>\n");
        }
        x.append("    <zuege>\n");
        for (ZugZustand z : a.getZuege().values()) {
            schreibeZug(z, x);
        }
        x.append("    </zuege>\n");
        for (String n : a.getItrainIgnoriert()) {
            x.append("    <itrainIgnoriert");
            attr(x, "name", n);
            x.append("/>\n");
        }
        for (var e : a.getBahnhofVorgaben().entrySet()) {
            x.append("    <bahnhofVorgabe");
            attr(x, "name", e.getKey());
            attr(x, "art", e.getValue().art() == null ? "" : e.getValue().art().name());
            if (e.getValue().name() != null) {
                attr(x, "neuerName", e.getValue().name());
            }
            if (e.getValue().bahnsteige() != null) {
                attr(x, "mitBahnsteigen", "true");
            }
            x.append(">\n");
            for (String b : e.getValue().ladegleise()) {
                x.append("      <ladegleis");
                attr(x, "block", b);
                x.append("/>\n");
            }
            if (e.getValue().bahnsteige() != null) {
                for (String b : e.getValue().bahnsteige()) {
                    x.append("      <bahnsteig");
                    attr(x, "block", b);
                    x.append("/>\n");
                }
            }
            x.append("    </bahnhofVorgabe>\n");
        }
        x.append("  </anlage>\n");
    }

    private static void schreibeObjekt(AnlagenObjekt o, StringBuilder x) {
        if (o instanceof Ort ort) {
            schreibeOrt(ort, x);
        } else if (o instanceof Station s) {
            schreibeStation(s, x);
        } else if (o instanceof Betrieb b) {
            schreibeBetrieb(b, x);
        } else if (o instanceof Lager l) {
            schreibeLager(l, x);
        } else if (o instanceof Uebergabe u) {
            schreibeUebergabe(u, x);
        } else if (o instanceof Reiter r) {
            kopf(r, "reiter", x);
            x.append(">\n");
            for (String id : r.getStationen()) {
                x.append("        <station");
                attr(x, "id", id);
                x.append("/>\n");
            }
            x.append("      </reiter>\n");
        } else if (o instanceof Modul m) {
            kopf(m, "modul", x);
            if (!m.isImBrowser()) {
                attr(x, "browser", "false");
            }
            x.append(">\n");
            for (String b : m.getBloecke()) {
                x.append("        <block");
                attr(x, "name", b);
                x.append("/>\n");
            }
            for (String id : m.getStationen()) {
                x.append("        <station");
                attr(x, "id", id);
                x.append("/>\n");
            }
            x.append("      </modul>\n");
        } else if (o instanceof Infrastruktur i) {
            kopf(i, "infrastruktur", x);
            x.append("/>\n");
        }
    }

    /** Kennzeile der Zwischenablage für kopierte Symbole (Anwender 09.11.2026, Bearbeiten > Kopieren). */
    public static final String KOPIE_KENNUNG = "iTrain-Logistik-Symbol";

    /** Ein Objekt als Text für die Zwischenablage: Kennzeile, dann das Objekt wie in der .logz. */
    public static String objektText(AnlagenObjekt o) {
        StringBuilder x = new StringBuilder(KOPIE_KENNUNG).append("\n<objekte>\n");
        schreibeObjekt(o, x);
        return x.append("</objekte>\n").toString();
    }

    /**
     * Liest ein Objekt aus der Zwischenablage mit neuer Id (sonst null). Verknüpfungen, die nur einmal vorkommen
     * dürfen, entfallen: Gleise (iTrain-Blöcke), Einzugsbereich, „gehört zu“ und iTrain-Bahnhof einer Station;
     * Bestände und Wartende beginnen bei 0.
     */
    public static AnlagenObjekt objektAusText(String text) {
        if (text == null || !text.startsWith(KOPIE_KENNUNG)) {
            return null;
        }
        try {
            javax.xml.parsers.DocumentBuilder b = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder();
            org.w3c.dom.Document d = b.parse(new org.xml.sax.InputSource(
                    new java.io.StringReader(text.substring(KOPIE_KENNUNG.length()).trim())));
            NodeList liste = d.getDocumentElement().getChildNodes();
            for (int i = 0; i < liste.getLength(); i++) {
                if (liste.item(i) instanceof Element e && !"infrastruktur".equals(e.getTagName())) {
                    e.setAttribute("id", java.util.UUID.randomUUID().toString());
                    AnlagenObjekt o = leseObjekt(e);
                    if (o == null) {
                        return null;
                    }
                    if (o instanceof Station s) {
                        s.getGleise().clear();
                        s.getEinzugsbereich().clear();
                        s.setGehoertZu(null);
                        s.setItrainName(null);
                    } else if (o instanceof Lager l) {
                        l.getBestand().clear();
                        l.getUnverbrauchbarSeit().clear();
                    } else if (o instanceof Betrieb bt) {
                        bt.getBestandEin().clear();
                        bt.getBestandAus().clear();
                        bt.getSchub().clear();
                    } else if (o instanceof Ort ort) {
                        ort.getOffenerBedarf().replaceAll((k, v) -> 0.0);
                        ort.setReisende(0);
                    } else if (o instanceof Modul m) {
                        m.getBloecke().clear(); // ein Block gehört nur einem Modul
                        m.getStationen().clear();
                    } else if (o instanceof Reiter r) {
                        r.getStationen().clear();
                    }
                    o.setPausiert(false);
                    return o;
                }
            }
        } catch (Exception ex) {
            return null;
        }
        return null;
    }

    private static void kopf(AnlagenObjekt o, String tag, StringBuilder x) {
        x.append("      <").append(tag);
        attr(x, "id", o.getId());
        attr(x, "art", o.getArt().name());
        attr(x, "name", o.getName());
        attr(x, "x", zahl(o.getX()));
        attr(x, "y", zahl(o.getY()));
        attr(x, "farbe", o.getFarbe());
        attr(x, "fuellung", o.getFuellung());
        attr(x, "schrift", o.getSchrift());
        if (o.getTempo() != 0) {
            attr(x, "tempoStufe", String.valueOf(o.getTempo()));
        }
        if (o.isPausiert()) {
            attr(x, "pausiert", "true");
        }
        if (o.isNeu()) {
            attr(x, "neu", "true");
        }
        if (o.getBreite() > 0) {
            attr(x, "breite", zahl(o.getBreite()));
        }
        if (o.getLinienStaerke() != 3) {
            attr(x, "linie", zahl(o.getLinienStaerke()));
        }
        if (o.getReiterId() != null) {
            attr(x, "reiter", o.getReiterId());
        }
        if (o instanceof Bereich b) {
            attr(x, "innenX", zahl(b.getInnenX()));
            attr(x, "innenY", zahl(b.getInnenY()));
        }
    }

    private static void schreibeOrt(Ort o, StringBuilder x) {
        kopf(o, "ort", x);
        attr(x, "einwohner", String.valueOf(o.getEinwohner()));
        attr(x, "passagiere", zahl(o.getPassagiereProStunde()));
        attr(x, "ersteKlasse", zahl(o.getAnteilErsteKlasse()));
        attr(x, "aussteigen", zahl(o.getAussteigeAnteil()));
        attr(x, "ankommend", zahl(o.getAnkommendProStunde()));
        attr(x, "ankommendUnbegrenzt", String.valueOf(o.isAnkommendUnbegrenzt()));
        attr(x, "reisende", zahl(o.getReisende()));
        attr(x, "liefertPost", String.valueOf(o.isLiefertPost()));
        if (o.postRoh() >= 0) {
            attr(x, "post", zahl(o.postRoh()));
        }
        attr(x, "liefertExpressgut", String.valueOf(o.isLiefertExpressgut()));
        if (o.isBedarfWaechst()) {
            attr(x, "bedarfWaechst", "true");
            attr(x, "seitAnpassung", zahl(o.getSeitAnpassung()));
        }
        if (o.expressRoh() >= 0) {
            attr(x, "express", zahl(o.expressRoh()));
        }
        x.append(">\n");
        for (Map.Entry<String, Double> e : o.getBedarf().entrySet()) {
            x.append("        <bedarf");
            attr(x, "gut", e.getKey());
            attr(x, "menge", zahl(e.getValue()));
            attr(x, "offen", zahl(o.getOffenerBedarf().getOrDefault(e.getKey(), 0.0)));
            if (o.bedarfStufe(e.getKey()) > 0) {
                attr(x, "stufe", String.valueOf(o.bedarfStufe(e.getKey())));
            }
            x.append("/>\n");
        }
        x.append("      </ort>\n");
    }

    private static void schreibeStation(Station s, StringBuilder x) {
        kopf(s, "station", x);
        attr(x, "itrain", s.getItrainName());
        attr(x, "gehoertZu", s.getGehoertZu());
        if (s.isBreiteAngleichen()) {
            attr(x, "breiteAngleichen", "true");
        }
        if (s.isReisendeAusOrt()) {
            attr(x, "reisendeAusOrt", "true");
        }
        x.append(">\n");
        for (String id : s.getEinzugsbereich()) {
            x.append("        <einzug");
            attr(x, "id", id);
            x.append("/>\n");
        }
        for (String gut : s.getUmlagern()) {
            x.append("        <umlagern");
            attr(x, "gut", gut);
            x.append("/>\n");
        }
        for (Gleis g : s.getGleise()) {
            x.append("        <gleis");
            attr(x, "id", g.getId());
            attr(x, "name", g.getName());
            attr(x, "block", g.getBlockName());
            attr(x, "art", g.getArt().name());
            attr(x, "laden", String.valueOf(g.isLaden()));
            attr(x, "entladen", String.valueOf(g.isEntladen()));
            attr(x, "erzwingen", String.valueOf(g.isEntladenErzwingen()));
            attr(x, "bedienSeite", g.getBedienSeite());
            if (!g.isDurchfahrt()) {
                attr(x, "durchfahrt", "false");
            }
            attr(x, "kapazitaet", String.valueOf(g.getKapazitaet()));
            attr(x, "rate", zahl(g.getUmladeRate()));
            if (g.isMitKoffer()) {
                attr(x, "mitKoffer", "true");
                attr(x, "kofferWartend", zahl(g.getKofferWartend()));
            }
            for (String[] pe : new String[][]{{Tf3Daten.POST, "post"}, {Tf3Daten.EXPRESSGUT, "express"}}) {
                if (g.nimmtAn(pe[0])) {
                    attr(x, pe[1] + "Annehmen", "true");
                }
                if (g.erzeugt(pe[0]) > 0) {
                    attr(x, pe[1] + "ProStunde", zahl(g.erzeugt(pe[0])));
                }
                if (g.abnahmeProStunde(pe[0]) >= 0) {
                    attr(x, pe[1] + "AbnahmeProStunde", zahl(g.abnahmeProStunde(pe[0])));
                }
            }
            if (g.getPassagiereProStunde() > 0) {
                attr(x, "proStunde", zahl(g.getPassagiereProStunde()));
                attr(x, "ersteKlasse", zahl(g.getAnteilErsteKlasse()));
            }
            if (g.getAnkommendProStunde() >= 0) {
                attr(x, "ankommendProStunde", zahl(g.getAnkommendProStunde()));
            }
            x.append(">\n");
            for (Map.Entry<String, Double> e : g.getAbnahme().entrySet()) {
                x.append("          <abnahme");
                attr(x, "gut", e.getKey());
                attr(x, "menge", zahl(e.getValue()));
                x.append("/>\n");
            }
            for (Map.Entry<String, Double> e : g.getAngebot().entrySet()) {
                x.append("          <angebot");
                attr(x, "gut", e.getKey());
                attr(x, "menge", zahl(e.getValue()));
                x.append("/>\n");
            }
            for (String r : g.getRichtungen()) {
                x.append("          <richtung");
                attr(x, "name", r);
                x.append("/>\n");
            }
            for (String gut : g.getGueterAuswahl()) {
                x.append("          <auswahl");
                attr(x, "gut", gut);
                x.append("/>\n");
            }
            mengen(x, "bestandaus", g.getAusgang(), "          ");
            mengen(x, "bestandein", g.getEingang(), "          ");
            for (Map.Entry<String, double[]> e : g.getWartende().entrySet()) {
                x.append("          <wartend");
                attr(x, "richtung", e.getKey());
                attr(x, "erste", zahl(e.getValue()[0]));
                attr(x, "zweite", zahl(e.getValue()[1]));
                x.append("/>\n");
            }
            x.append("        </gleis>\n");
        }
        x.append("      </station>\n");
    }

    private static void schreibeBetrieb(Betrieb b, StringBuilder x) {
        kopf(b, "betrieb", x);
        attr(x, "vorlage", b.getVorlageId());
        attr(x, "rate", zahl(b.getRateProStunde()));
        attr(x, "fortschritt", zahl(b.getFortschritt()));
        if (b.isVirtuell()) {
            attr(x, "virtuell", "true");
        }
        if (b.isDynamisch()) {
            attr(x, "dynamisch", "true");
        }
        if (b.isAnpassen()) {
            attr(x, "anpassen", "true");
            attr(x, "stufe", String.valueOf(b.getStufe()));
            attr(x, "seitAnpassung", zahl(b.getSeitAnpassung()));
        }
        if (b.getLieferAlleStunden() > 0) {
            attr(x, "lieferAlleStunden", zahl(b.getLieferAlleStunden()));
            attr(x, "seitLieferung", zahl(b.getSeitLieferung()));
        }
        x.append(">\n");
        for (Map.Entry<String, Double> s : b.getSchub().entrySet()) {
            x.append("        <schub");
            attr(x, "gut", s.getKey());
            attr(x, "menge", zahl(s.getValue()));
            x.append("/>\n");
        }
        for (String g : b.getImmerGeliefert()) {
            x.append("        <immer");
            attr(x, "gut", g);
            x.append("/>\n");
        }
        for (Rezept r : b.getRezepte()) {
            x.append("        <rezept>\n");
            r.getEingang().forEach((g, m) -> x.append("          <ein gut=\"").append(esc(g))
                    .append("\" menge=\"").append(m).append("\"/>\n"));
            r.getAusgang().forEach((g, m) -> x.append("          <aus gut=\"").append(esc(g))
                    .append("\" menge=\"").append(m).append("\"/>\n"));
            x.append("        </rezept>\n");
        }
        lager(x, "lagerein", b.getLagerEin(), b.getBestandEin());
        lager(x, "lageraus", b.getLagerAus(), b.getBestandAus());
        x.append("      </betrieb>\n");
    }

    private static void lager(StringBuilder x, String tag, Map<String, Integer> kap, Map<String, Double> bestand) {
        for (Map.Entry<String, Integer> e : kap.entrySet()) {
            x.append("        <").append(tag);
            attr(x, "gut", e.getKey());
            attr(x, "kapazitaet", String.valueOf(e.getValue()));
            attr(x, "bestand", zahl(bestand.getOrDefault(e.getKey(), 0.0)));
            x.append("/>\n");
        }
    }

    private static void schreibeLager(Lager l, StringBuilder x) {
        kopf(l, "lager", x);
        attr(x, "kapazitaet", String.valueOf(l.getKapazitaet()));
        x.append(">\n");
        for (Ladeklasse k : l.getKlassen()) {
            x.append("        <klasse");
            attr(x, "name", k.name());
            x.append("/>\n");
        }
        mengen(x, "bestand", l.getBestand(), "        ");
        x.append("      </lager>\n");
    }

    private static void schreibeUebergabe(Uebergabe u, StringBuilder x) {
        kopf(u, "uebergabe", x);
        attr(x, "annahmeAlles", String.valueOf(u.isAnnahmeAlles()));
        attr(x, "passagiereAussteigen", String.valueOf(u.isPassagiereAussteigen()));
        x.append(">\n");
        for (String g : u.getAngebot()) {
            x.append("        <angebot");
            attr(x, "gut", g);
            x.append("/>\n");
        }
        for (String g : u.getAnnahme()) {
            x.append("        <annahme");
            attr(x, "gut", g);
            x.append("/>\n");
        }
        x.append("      </uebergabe>\n");
    }

    private static void schreibeZug(ZugZustand z, StringBuilder x) {
        x.append("      <zug");
        attr(x, "name", z.getName());
        attr(x, "linie", z.getLinie());
        attr(x, "block", z.getBlock());
        attr(x, "richtung", z.getRichtung());
        attr(x, "signatur", z.getSignatur());
        if (z.getKofferReisende() > 0) {
            attr(x, "kofferReisende", zahl(z.getKofferReisende()));
        }
        attr(x, "erste", zahl(z.getSitzplaetzeErste()));
        attr(x, "zweite", zahl(z.getSitzplaetzeZweite()));
        attr(x, "letzteStation", z.getLetzteStationId());
        x.append(">\n");
        for (Wagenladung w : z.getWagenladungen()) {
            x.append("        <ladung");
            attr(x, "wagen", w.getWagenName());
            attr(x, "klasse", w.getKlasse().name());
            attr(x, "kapazitaet", zahl(w.getKapazitaet()));
            attr(x, "exklusiv", String.valueOf(w.isExklusiv()));
            attr(x, "zeitLaden", zahl(w.getZeitLaden()));
            attr(x, "zeitEntladen", zahl(w.getZeitEntladen()));
            if (w.isJoker()) {
                attr(x, "joker", "true");
            }
            if (!w.getErlaubt().isEmpty()) {
                attr(x, "erlaubt", String.join(",", w.getErlaubt()));
            }
            x.append(">\n");
            mengen(x, "inhalt", w.getInhalt(), "          ");
            x.append("        </ladung>\n");
        }
        for (Map.Entry<String, double[]> e : z.getPassagiere().entrySet()) {
            x.append("        <passagiere");
            attr(x, "station", e.getKey());
            attr(x, "erste", zahl(e.getValue()[0]));
            attr(x, "zweite", zahl(e.getValue()[1]));
            x.append("/>\n");
        }
        x.append("      </zug>\n");
    }

    private static void schreibeText(NetzText t, StringBuilder x) {
        x.append("      <text id=\"").append(esc(t.getId()))
                .append("\" x=\"").append(zahl(t.getX()))
                .append("\" y=\"").append(zahl(t.getY()))
                .append("\" schriftart=\"").append(esc(t.getSchriftart()))
                .append("\" schriftgroesse=\"").append(zahl(t.getSchriftgroesse()))
                .append("\" fett=\"").append(t.isFett())
                .append("\" kursiv=\"").append(t.isKursiv())
                .append("\" horizontal=\"").append(t.getHorizontal().name())
                .append("\" vertikal=\"").append(t.getVertikal().name())
                .append("\" textrichtung=\"").append(t.getTextrichtung().name())
                .append("\" farbe=\"").append(esc(t.getFarbe()))
                .append("\" rahmen=\"").append(t.isRahmen())
                .append("\">").append(esc(t.getText())).append("</text>\n");
    }

    private static void mengen(StringBuilder x, String tag, Map<String, Double> m, String einzug) {
        for (Map.Entry<String, Double> e : m.entrySet()) {
            if (e.getValue() == null || Math.abs(e.getValue()) < 1e-9) {
                continue;
            }
            x.append(einzug).append("<").append(tag);
            attr(x, "gut", e.getKey());
            attr(x, "menge", zahl(e.getValue()));
            x.append("/>\n");
        }
    }

    private static void attr(StringBuilder x, String name, String wert) {
        if (wert != null) {
            x.append(' ').append(name).append("=\"").append(esc(wert)).append('"');
        }
    }

    private static String zahl(double v) {
        // Auf 4 Nachkommastellen runden; ganze Zahlen ohne Nachkommastellen (stabil beim Neuladen).
        double r = Math.round(v * 10000.0) / 10000.0;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) {
            return String.valueOf((long) r);
        }
        return java.math.BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
    }

    private static String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Tiefe Kopie über XML (Editor: Stand beim Öffnen für "Abbruch"). */
    public static Anlage kopie(Anlage a) {
        StringBuilder x = new StringBuilder("<logistik>\n");
        schreibe(a, x);
        x.append("</logistik>\n");
        try {
            var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(x.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return lese(kinder(doc.getDocumentElement(), "anlage").get(0));
        } catch (Exception ex) {
            throw new IllegalStateException("Kopie der Anlage fehlgeschlagen", ex);
        }
    }

    // ================================================================ Lesen

    /** Liest den Abschnitt {@code <anlage>}; ohne ihn entsteht eine neue Anlage mit den TF3-Guetern. */
    public static Anlage lese(Element anlageElement) {
        if (anlageElement == null) {
            return Anlage.neuMitTf3Guetern();
        }
        Anlage a = new Anlage();
        a.setMengenMassstab(dbl(anlageElement, "massstab", 0.1));
        a.setFlaeche(dbl(anlageElement, "flaecheBreite", 0), dbl(anlageElement, "flaecheHoehe", 0));
        if (anlageElement.hasAttribute("hauptReiter")) {
            a.setHauptReiterName(anlageElement.getAttribute("hauptReiter"));
        }
        a.setTempo(anlageElement.hasAttribute("tempoStufe") ? (int) dbl(anlageElement, "tempoStufe", 0)
                : Anlage.alteTempoStufe((int) dbl(anlageElement, "tempo", 1)));
        for (Element gueter : kinder(anlageElement, "gueter")) {
            for (Element e : kinder(gueter, "gut")) {
                Ladeklasse k = enumWert(Ladeklasse.class, e.getAttribute("klasse"), Ladeklasse.STUECKGUT);
                Gut g = new Gut(e.getAttribute("id"), e.getAttribute("name"), k, txt(e, "farbe", "#808080"),
                        enumWert(GutStufe.class, e.getAttribute("stufe"), null),
                        enumWert(StadtBereich.class, e.getAttribute("bereich"), null));
                if (e.hasAttribute("einheit")) {
                    g.setEinheit(e.getAttribute("einheit"));
                }
                g.setKapazitaetJeEinheit(dbl(e, "jeEinheit", 1));
                if (e.hasAttribute("mischbar")) {
                    g.setMischbarEinstellung(Boolean.parseBoolean(e.getAttribute("mischbar")));
                }
                a.getGueter().put(g.getId(), g);
            }
        }
        for (Element gueter : kinder(anlageElement, "gueter")) {
            for (Element e : kinder(gueter, "geloescht")) {
                a.getGeloeschteGueter().add(e.getAttribute("id"));
            }
        }
        // neue Güter des Grundbestands (z. B. Koffer, Anwender 27.10.2026) kommen auch in ältere Dateien
        a.grundbestandErgaenzen();
        for (Element objekte : kinder(anlageElement, "objekte")) {
            NodeList liste = objekte.getChildNodes();
            for (int i = 0; i < liste.getLength(); i++) {
                if (!(liste.item(i) instanceof Element e)) {
                    continue;
                }
                AnlagenObjekt o = leseObjekt(e);
                if (o != null) {
                    a.getObjekte().add(o);
                }
            }
        }
        a.infrastruktur();
        for (Element texte : kinder(anlageElement, "texte")) {
            for (Element t : kinder(texte, "text")) {
                a.getTexte().add(leseText(t));
            }
        }
        for (Element ig : kinder(anlageElement, "itrainIgnoriert")) {
            a.getItrainIgnoriert().add(ig.getAttribute("name"));
        }
        a.setBahnhoefeEingefuegt(!"false".equals(anlageElement.getAttribute("bahnhoefeEingefuegt")));
        a.setErsterPersonenTransferOffen("offen".equals(anlageElement.getAttribute("ersterPersonenTransfer")));
        a.setErsterGueterTransferOffen("offen".equals(anlageElement.getAttribute("ersterGueterTransfer")));
        for (Element v : kinder(anlageElement, "bahnhofVorgabe")) {
            java.util.Set<String> lade = new java.util.LinkedHashSet<>();
            for (Element l : kinder(v, "ladegleis")) {
                lade.add(l.getAttribute("block"));
            }
            ObjektArt art = v.getAttribute("art").isBlank() ? null : enumWert(ObjektArt.class, v.getAttribute("art"), null);
            java.util.Set<String> steige = null;
            if ("true".equals(v.getAttribute("mitBahnsteigen"))) {
                steige = new java.util.LinkedHashSet<>();
                for (Element l : kinder(v, "bahnsteig")) {
                    steige.add(l.getAttribute("block"));
                }
            }
            String neuerName = v.hasAttribute("neuerName") ? v.getAttribute("neuerName") : null;
            a.getBahnhofVorgaben().put(v.getAttribute("name"), new Anlage.BahnhofVorgabe(art, lade, neuerName, steige));
        }
        for (Element zuege : kinder(anlageElement, "zuege")) {
            for (Element z : kinder(zuege, "zug")) {
                ZugZustand zz = leseZug(z);
                for (Wagenladung w : zz.getWagenladungen()) {
                    for (String id : w.getInhalt().keySet()) {
                        Gut gut = a.gut(id);
                        if (gut != null) {
                            w.setFaktor(id, gut.getKapazitaetJeEinheit());
                        }
                    }
                }
                a.getZuege().put(zz.getName(), zz);
            }
        }
        // Einmalig: Ladegleise der Bahnhöfe (nicht der Außenwelt) halten höchstens die neue Vorgabe; mehr gehört in
        // ein Lager (Anwender 31.10.2026). Danach kann der Anwender jeden Wert ändern.
        if (!anlageElement.hasAttribute("ladegleisVorgabe")) {
            for (Station s : a.stationen()) {
                if (a.istAussenwelt(s)) {
                    continue;
                }
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.getKapazitaet() > Gleis.LADEGLEIS_VORGABE) {
                        g.setKapazitaet(Gleis.LADEGLEIS_VORGABE);
                    }
                }
            }
        }
        // Einmalig: ältere Dateien bekommen an allen Gleisen die neue Umladevorgabe (Anwender 27.10.2026);
        // höhere Werte bleiben, danach kann der Anwender jeden Wert ändern.
        if (!anlageElement.hasAttribute("umladeVorgabe")) {
            for (Station s : a.stationen()) {
                for (Gleis g : s.getGleise()) {
                    if (g.getUmladeRate() < Gleis.UMLADE_VORGABE) {
                        g.setUmladeRate(Gleis.UMLADE_VORGABE);
                    }
                }
            }
        }
        return a;
    }

    /** Ein Objekt aus seinem Element samt gemeinsamer Attribute (Name, Lage, Farben, Tempo …). */
    private static AnlagenObjekt leseObjekt(Element e) {
        AnlagenObjekt o = switch (e.getTagName()) {
            case "ort" -> leseOrt(e);
            case "station" -> leseStation(e);
            case "betrieb" -> leseBetrieb(e);
            case "lager" -> leseLager(e);
            case "uebergabe" -> leseUebergabe(e);
            case "infrastruktur" -> new Infrastruktur(e.getAttribute("id"));
            case "modul" -> leseModul(e);
            case "reiter" -> {
                Reiter r = new Reiter(e.getAttribute("id"), e.getAttribute("name"));
                for (Element st : kinder(e, "station")) {
                    r.getStationen().add(st.getAttribute("id"));
                }
                yield r;
            }
            default -> null;
        };
        if (o != null) {
            o.setName(e.getAttribute("name"));
            o.setX(dbl(e, "x", 40));
            o.setY(dbl(e, "y", 40));
            if (e.hasAttribute("farbe")) {
                o.setFarbe(e.getAttribute("farbe"));
            }
            if (e.hasAttribute("fuellung")) {
                o.setFuellung(e.getAttribute("fuellung"));
            }
            if (e.hasAttribute("schrift")) {
                o.setSchrift(e.getAttribute("schrift"));
            }
            if (e.hasAttribute("tempoStufe")) {
                o.setTempo((int) dbl(e, "tempoStufe", 0));
            } else {
                // alte Datei: Stufe 0 war Pause und wird zum Pausen-Schalter des Objekts
                int alt = (int) dbl(e, "tempo", 1);
                o.setTempo(Anlage.alteTempoStufe(alt));
                o.setPausiert(alt == 0);
            }
            o.setPausiert(o.isPausiert() || "true".equals(e.getAttribute("pausiert")));
            o.setLinienStaerke(dbl(e, "linie", 3));
            o.setBreite(dbl(e, "breite", 0));
            o.setNeu("true".equals(e.getAttribute("neu")));
            if (e.hasAttribute("reiter")) {
                o.setReiterId(e.getAttribute("reiter"));
            }
            if (o instanceof Bereich b) {
                b.setInnenX(dbl(e, "innenX", 400));
                b.setInnenY(dbl(e, "innenY", 60));
            }
        }
        return o;
    }

    private static Modul leseModul(Element e) {
        Modul m = new Modul(e.getAttribute("id"), e.getAttribute("name"));
        m.setImBrowser(!"false".equals(e.getAttribute("browser")));
        for (Element b : kinder(e, "block")) {
            m.getBloecke().add(b.getAttribute("name"));
        }
        for (Element s : kinder(e, "station")) {
            m.getStationen().add(s.getAttribute("id"));
        }
        return m;
    }

    private static Ort leseOrt(Element e) {
        ObjektArt art = enumWert(ObjektArt.class, e.getAttribute("art"), ObjektArt.STADT);
        Ort o = new Ort(e.getAttribute("id"), art.istOrt() ? art : ObjektArt.STADT, e.getAttribute("name"));
        o.setEinwohner((int) dbl(e, "einwohner", o.getEinwohner()));
        o.setPassagiereProStunde(dbl(e, "passagiere", o.getPassagiereProStunde()));
        o.setAnteilErsteKlasse(dbl(e, "ersteKlasse", o.getAnteilErsteKlasse()));
        o.setAussteigeAnteil(dbl(e, "aussteigen", o.getAussteigeAnteil()));
        o.setAnkommendProStunde(dbl(e, "ankommend", 0));
        o.setAnkommendUnbegrenzt(!"false".equals(e.getAttribute("ankommendUnbegrenzt")));
        o.setReisende(dbl(e, "reisende", 0));
        o.setLiefertPost(!"false".equals(e.getAttribute("liefertPost")));
        o.setLiefertExpressgut(!"false".equals(e.getAttribute("liefertExpressgut")));
        o.setPostProStunde(dbl(e, "post", -1));
        o.setExpressProStunde(dbl(e, "express", -1));
        o.setBedarfWaechst("true".equals(e.getAttribute("bedarfWaechst")));
        o.setSeitAnpassung(dbl(e, "seitAnpassung", 0));
        for (Element b : kinder(e, "bedarf")) {
            o.setBedarfStufe(b.getAttribute("gut"), (int) dbl(b, "stufe", 0));
            // Bedarf nur in ganzen Einheiten, mindestens 1 (Anwender 14.10.2026)
            double menge = dbl(b, "menge", 0);
            o.getBedarf().put(b.getAttribute("gut"), menge > 0 ? Math.max(1, Math.round(menge)) : 0.0);
            o.getOffenerBedarf().put(b.getAttribute("gut"), dbl(b, "offen", 0));
        }
        return o;
    }

    private static Station leseStation(Element e) {
        ObjektArt art = enumWert(ObjektArt.class, e.getAttribute("art"), ObjektArt.BAHNHOF);
        Station s = new Station(e.getAttribute("id"), art.istStation() ? art : ObjektArt.BAHNHOF, e.getAttribute("name"));
        if (e.hasAttribute("itrain")) {
            s.setItrainName(e.getAttribute("itrain"));
        }
        if (e.hasAttribute("gehoertZu")) {
            s.setGehoertZu(e.getAttribute("gehoertZu"));
        }
        s.setBreiteAngleichen("true".equals(e.getAttribute("breiteAngleichen")));
        s.setReisendeAusOrt("true".equals(e.getAttribute("reisendeAusOrt")));
        for (Element z : kinder(e, "einzug")) {
            s.getEinzugsbereich().add(z.getAttribute("id"));
        }
        for (Element z : kinder(e, "umlagern")) {
            s.getUmlagern().add(z.getAttribute("gut"));
        }
        for (Element ge : kinder(e, "gleis")) {
            Gleis g = new Gleis(ge.getAttribute("id"), enumWert(GleisArt.class, ge.getAttribute("art"), GleisArt.LADEGLEIS));
            // Ältere Schattenbahnhöfe ohne Mengenangabe: 30 Personen je Modellstunde je Bahnsteig.
            double proStundeVorgabe = s.getArt() == ObjektArt.SCHATTENBAHNHOF && g.getArt() == GleisArt.BAHNSTEIG ? 30 : 0;
            g.setPassagiereProStunde(dbl(ge, "proStunde", proStundeVorgabe));
            g.setAnteilErsteKlasse(dbl(ge, "ersteKlasse", 0.1));
            g.setMitKoffer("true".equals(ge.getAttribute("mitKoffer")));
            g.setKofferWartend(dbl(ge, "kofferWartend", 0));
            for (String[] pe : new String[][]{{Tf3Daten.POST, "post"}, {Tf3Daten.EXPRESSGUT, "express"}}) {
                g.setAnnehmen(pe[0], "true".equals(ge.getAttribute(pe[1] + "Annehmen")));
                g.setErzeugt(pe[0], dbl(ge, pe[1] + "ProStunde", 0));
                g.setAbnahmeProStunde(pe[0], dbl(ge, pe[1] + "AbnahmeProStunde", -1));
            }
            g.setAnkommendProStunde(dbl(ge, "ankommendProStunde", -1));
            for (Element m : kinder(ge, "abnahme")) {
                g.getAbnahme().put(m.getAttribute("gut"), dbl(m, "menge", 0));
            }
            for (Element m : kinder(ge, "angebot")) {
                g.getAngebot().put(m.getAttribute("gut"), dbl(m, "menge", 0));
            }
            if (ge.hasAttribute("name")) {
                g.setName(ge.getAttribute("name"));
            }
            if (ge.hasAttribute("block")) {
                g.setBlockName(ge.getAttribute("block"));
            }
            g.setLaden(!"false".equals(ge.getAttribute("laden")));
            g.setEntladen(!"false".equals(ge.getAttribute("entladen")));
            g.setEntladenErzwingen("true".equals(ge.getAttribute("erzwingen")));
            g.setBedienSeite(ge.hasAttribute("bedienSeite") ? ge.getAttribute("bedienSeite") : null);
            g.setDurchfahrt(!"false".equals(ge.getAttribute("durchfahrt")));
            g.setKapazitaet((int) dbl(ge, "kapazitaet", g.getKapazitaet()));
            g.setUmladeRate(dbl(ge, "rate", g.getUmladeRate()));
            for (Element r : kinder(ge, "richtung")) {
                g.getRichtungen().add(r.getAttribute("name"));
            }
            for (Element r : kinder(ge, "auswahl")) {
                g.getGueterAuswahl().add(r.getAttribute("gut"));
            }
            leseMengen(ge, "bestandaus", g.getAusgang());
            leseMengen(ge, "bestandein", g.getEingang());
            for (Element w : kinder(ge, "wartend")) {
                double[] werte = g.wartendeIn(w.getAttribute("richtung"));
                werte[0] = dbl(w, "erste", 0);
                werte[1] = dbl(w, "zweite", 0);
            }
            s.getGleise().add(g);
        }
        return s;
    }

    private static Betrieb leseBetrieb(Element e) {
        Betrieb b = new Betrieb(e.getAttribute("id"), e.getAttribute("name"));
        if (e.hasAttribute("vorlage")) {
            b.setVorlageId(e.getAttribute("vorlage"));
        }
        b.setRateProStunde(dbl(e, "rate", 1));
        b.setFortschritt(dbl(e, "fortschritt", 0));
        b.setVirtuell("true".equals(e.getAttribute("virtuell")));
        b.setDynamisch("true".equals(e.getAttribute("dynamisch")));
        b.setAnpassen("true".equals(e.getAttribute("anpassen")));
        b.setStufe((int) dbl(e, "stufe", 0));
        b.setSeitAnpassung(dbl(e, "seitAnpassung", 0));
        b.setLieferAlleStunden(dbl(e, "lieferAlleStunden", 0));
        b.setSeitLieferung(dbl(e, "seitLieferung", 0));
        for (Element s : kinder(e, "schub")) {
            b.getSchub().put(s.getAttribute("gut"), dbl(s, "menge", 0));
        }
        for (Element m : kinder(e, "immer")) {
            b.getImmerGeliefert().add(m.getAttribute("gut"));
        }
        for (Element r : kinder(e, "rezept")) {
            Rezept rezept = new Rezept();
            for (Element m : kinder(r, "ein")) {
                rezept.getEingang().put(m.getAttribute("gut"), (int) dbl(m, "menge", 1));
            }
            for (Element m : kinder(r, "aus")) {
                rezept.getAusgang().put(m.getAttribute("gut"), (int) dbl(m, "menge", 1));
            }
            b.getRezepte().add(rezept);
        }
        for (Element l : kinder(e, "lagerein")) {
            b.getLagerEin().put(l.getAttribute("gut"), (int) dbl(l, "kapazitaet", 0));
            b.getBestandEin().put(l.getAttribute("gut"), dbl(l, "bestand", 0));
        }
        for (Element l : kinder(e, "lageraus")) {
            b.getLagerAus().put(l.getAttribute("gut"), (int) dbl(l, "kapazitaet", 0));
            b.getBestandAus().put(l.getAttribute("gut"), dbl(l, "bestand", 0));
        }
        return b;
    }

    private static Lager leseLager(Element e) {
        Lager l = new Lager(e.getAttribute("id"), e.getAttribute("name"));
        l.setKapazitaet((int) dbl(e, "kapazitaet", 100));
        for (Element k : kinder(e, "klasse")) {
            Ladeklasse kl = enumWert(Ladeklasse.class, k.getAttribute("name"), null);
            if (kl != null) {
                l.getKlassen().add(kl);
            }
        }
        leseMengen(e, "bestand", l.getBestand());
        return l;
    }

    private static Uebergabe leseUebergabe(Element e) {
        Uebergabe u = new Uebergabe(e.getAttribute("id"), e.getAttribute("name"));
        u.setAnnahmeAlles(!"false".equals(e.getAttribute("annahmeAlles")));
        u.setPassagiereAussteigen(!"false".equals(e.getAttribute("passagiereAussteigen")));
        for (Element g : kinder(e, "angebot")) {
            u.getAngebot().add(g.getAttribute("gut"));
        }
        for (Element g : kinder(e, "annahme")) {
            u.getAnnahme().add(g.getAttribute("gut"));
        }
        return u;
    }

    private static ZugZustand leseZug(Element e) {
        ZugZustand z = new ZugZustand(e.getAttribute("name"));
        z.setKofferReisende(dbl(e, "kofferReisende", 0));
        if (e.hasAttribute("linie")) {
            z.setLinie(e.getAttribute("linie"));
        }
        if (e.hasAttribute("block")) {
            z.setBlock(e.getAttribute("block"));
        }
        if (e.hasAttribute("richtung")) {
            z.setRichtung(e.getAttribute("richtung"));
        }
        if (e.hasAttribute("letzteStation")) {
            z.setLetzteStationId(e.getAttribute("letzteStation"));
        }
        z.setSignaturRoh(e.getAttribute("signatur"));
        z.setSitzplaetze(dbl(e, "erste", 0), dbl(e, "zweite", 0));
        for (Element l : kinder(e, "ladung")) {
            Wagenladung w = new Wagenladung(l.getAttribute("wagen"),
                    enumWert(Ladeklasse.class, l.getAttribute("klasse"), Ladeklasse.STUECKGUT),
                    dbl(l, "kapazitaet", 0), l.hasAttribute("erlaubt") && !l.getAttribute("erlaubt").isBlank()
                    ? new java.util.LinkedHashSet<>(java.util.List.of(l.getAttribute("erlaubt").split(","))) : null,
                    dbl(l, "zeitLaden", 0), dbl(l, "zeitEntladen", 0));
            leseMengen(l, "inhalt", w.getInhalt());
            w.setExklusiv("true".equals(l.getAttribute("exklusiv")));
            w.setJoker("true".equals(l.getAttribute("joker")));
            z.getWagenladungen().add(w);
        }
        for (Element p : kinder(e, "passagiere")) {
            z.getPassagiere().put(p.getAttribute("station"), new double[]{dbl(p, "erste", 0), dbl(p, "zweite", 0)});
        }
        return z;
    }

    private static NetzText leseText(Element e) {
        NetzText t = new NetzText(e.getAttribute("id"), dbl(e, "x", 0), dbl(e, "y", 0));
        t.setText(e.getTextContent());
        if (e.hasAttribute("schriftart")) {
            t.setSchriftart(e.getAttribute("schriftart"));
        }
        if (e.hasAttribute("schriftgroesse")) {
            t.setSchriftgroesse(dbl(e, "schriftgroesse", 14));
        }
        t.setFett("true".equals(e.getAttribute("fett")));
        t.setKursiv("true".equals(e.getAttribute("kursiv")));
        NetzText.Horizontal h = enumWert(NetzText.Horizontal.class, e.getAttribute("horizontal"), null);
        if (h != null) {
            t.setHorizontal(h);
        }
        NetzText.Vertikal v = enumWert(NetzText.Vertikal.class, e.getAttribute("vertikal"), null);
        if (v != null) {
            t.setVertikal(v);
        }
        NetzText.Textrichtung r = enumWert(NetzText.Textrichtung.class, e.getAttribute("textrichtung"), null);
        if (r != null) {
            t.setTextrichtung(r);
        }
        if (e.hasAttribute("farbe")) {
            t.setFarbe(e.getAttribute("farbe"));
        }
        t.setRahmen("true".equals(e.getAttribute("rahmen")));
        return t;
    }

    private static void leseMengen(Element eltern, String tag, Map<String, Double> ziel) {
        for (Element m : kinder(eltern, tag)) {
            ziel.put(m.getAttribute("gut"), dbl(m, "menge", 0));
        }
    }

    /** Direkte Kindelemente mit dem Namen {@code tag}. */
    public static List<Element> kinder(Element eltern, String tag) {
        List<Element> l = new ArrayList<>();
        NodeList liste = eltern.getChildNodes();
        for (int i = 0; i < liste.getLength(); i++) {
            Node n = liste.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                l.add((Element) n);
            }
        }
        return l;
    }

    private static double dbl(Element e, String attr, double standard) {
        if (!e.hasAttribute(attr)) {
            return standard;
        }
        try {
            return Double.parseDouble(e.getAttribute(attr).replace(',', '.'));
        } catch (NumberFormatException ex) {
            return standard;
        }
    }

    private static String txt(Element e, String attr, String standard) {
        return e.hasAttribute(attr) ? e.getAttribute(attr) : standard;
    }

    private static <E extends Enum<E>> E enumWert(Class<E> typ, String wert, E standard) {
        if (wert == null || wert.isBlank()) {
            return standard;
        }
        try {
            return Enum.valueOf(typ, wert);
        } catch (IllegalArgumentException ex) {
            return standard;
        }
    }
}
