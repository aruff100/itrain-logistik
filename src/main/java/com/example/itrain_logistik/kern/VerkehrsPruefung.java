package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Prüfung des Verkehrs-Assistenten (Anwender 27.10.2026), aufbauend auf {@link KreislaufPruefung}: je Bedarf,
 * Herstellung und Reiseaufkommen ein Satz („10 Reisende wollen je Modellstunde aus Stadt abfahren“), das Gegenstück
 * auf der Anlage und ob die Fahrzeuge das schaffen.
 * <ol>
 * <li>Kann überhaupt ein Zug das befördern (Plätze bzw. Wagen, die das Gut laden dürfen, auch Lade-Joker)?</li>
 * <li>Wie viele Fahrten je Modellstunde braucht der größte passende Zug? Mehr als {@value #FAHRTEN_GRENZE} gilt
 * als kaum zu schaffen.</li>
 * <li>Gibt es ein Gegenstück (Lieferant, Abnehmer, Ziel, Herkunft)?</li>
 * </ol>
 */
public final class VerkehrsPruefung {

    /** Mehr Fahrten je Modellstunde gelten als kaum machbar (Annahme). */
    public static final double FAHRTEN_GRENZE = 2.0;

    /** Eine Zeile der Tabelle. {@code ok} = alles machbar; sonst steht in {@code hinweis}, was fehlt. */
    public record Zeile(String was, String gegenstueck, String fahrzeuge, boolean ok, String hinweis,
                        KreislaufPruefung.Zeile quelle) {
    }

    private VerkehrsPruefung() {
    }

    public static List<Zeile> pruefe(Anlage a, Collection<ZugZustand> zuege) {
        return pruefe(a, zuege, Double.NaN);
    }

    /**
     * @param uhrFaktor Beschleunigung der Modelluhr (Modellstunden je realer Stunde); NaN = unbekannt (dann 1)
     */
    public static List<Zeile> pruefe(Anlage a, Collection<ZugZustand> zuege, double uhrFaktor) {
        KreislaufPruefung.Ergebnis k = KreislaufPruefung.pruefe(a);
        List<Zeile> l = new ArrayList<>();
        for (KreislaufPruefung.Zeile z : k.zeilen()) {
            String name = z.objekt().getAnzeigeName();
            String menge = KreislaufPruefung.zahl(z.menge());
            String was;
            switch (z.abschnitt()) {
                case ABFAHRT -> was = menge + " Reisende wollen je Modellstunde aus „" + name + "“ abfahren.";
                case ANKUNFT -> was = Double.isInfinite(z.menge())
                        ? "„" + name + "“ nimmt beliebig viele ankommende Reisende auf."
                        : "„" + name + "“ nimmt je Modellstunde " + menge + " ankommende Reisende auf.";
                case ORTSBEDARF -> was = mengeGut(a, z) + " sollen je Modellstunde nach „" + name
                        + "“ geliefert werden.";
                case EINGAENGE -> was = "„" + name + "“ braucht je Modellstunde " + mengeGut(a, z) + ".";
                case AUSGAENGE -> was = Double.isInfinite(z.menge())
                        ? "„" + name + "“ liefert " + gutName(a, z) + " unbegrenzt."
                        : "„" + name + "“ stellt je Modellstunde " + mengeGut(a, z) + " her" + schubText(z) + ".";
                default -> was = name;
            }
            Partner p = partner(a, z);
            String[] fz = p.ohneZug() ? new String[]{"– (kein Zug nötig)", null} : fahrzeuge(a, z, zuege);
            // Route (Anwender 15.11.2026): ohne iTrain-Zugroute zwischen den Bahnhöfen ist der Verkehr nicht fertig
            String route = p.ohneZug() || p.stationen().isEmpty() || routeVorhanden(a, z.objekt(), p.stationen()) ? null
                    : "Bisher keine iTrain-Route von den Gleisen von „" + z.objekt().getAnzeigeName() + "“ nach "
                    + p.name();
            boolean ok = z.ok() && fz[1] == null && route == null;
            String fehlt = z.fehlt() != null && z.gegenstueck() != null && z.gegenstueck().contains(z.fehlt())
                    ? null : z.fehlt();
            List<String> teile = new ArrayList<>();
            if (fehlt != null) {
                teile.add(fehlt);
            }
            if (fz[1] != null) {
                teile.add(fz[1]);
            }
            if (route != null) {
                teile.add(route);
            }
            if (teile.isEmpty() && z.fehlt() != null) {
                teile.add(z.fehlt()); // steht schon im Gegenstück, trotzdem als Hinweis nennen
            }
            String hinweis = ok ? zusammenfassung(a, z, p, fz, uhrFaktor) : String.join("; ", teile);
            l.add(new Zeile(was, z.gegenstueck(), fz[0], ok, hinweis, z));
        }
        kofferZeilen(a, zuege, l);
        return l;
    }

    /** Bahnsteige „Mit Koffer“: Gegenstück (anderer Bahnhof mit Koffer) und Züge mit Gepäckwagen (Anwender 27.10.2026). */
    private static void kofferZeilen(Anlage a, Collection<ZugZustand> zuege, List<Zeile> l) {
        Gut koffer = a.gut(Gleis.KOFFER_GUT);
        for (Station s : a.stationen()) {
            if (s.gleiseDerArt(GleisArt.BAHNSTEIG).stream().noneMatch(Gleis::isMitKoffer)) {
                continue;
            }
            List<String> gegen = new ArrayList<>();
            for (Station x : a.stationen()) {
                if (x != s && x.gleiseDerArt(GleisArt.BAHNSTEIG).stream().anyMatch(Gleis::isMitKoffer)) {
                    gegen.add(x.getAnzeigeName());
                }
            }
            List<String> zn = new ArrayList<>();
            for (ZugZustand z : zuege) {
                if (koffer != null && z.kapazitaetFuer(koffer) >= 1
                        && z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() > 0) {
                    zn.add(z.getName() + " (" + KreislaufPruefung.zahl(z.kapazitaetFuer(koffer)) + " Koffer)");
                }
            }
            String hinweis = gegen.isEmpty() ? "kein anderer Bahnhof mit Bahnsteig „Mit Koffer“"
                    : zn.isEmpty() ? "kein Personenzug mit Gepäckwagen (Koffer als Ladegut aktivieren)" : null;
            l.add(new Zeile(KreislaufPruefung.zahl(Gleis.KOFFER_ANTEIL * 100) + " % der Reisenden in „"
                    + s.getAnzeigeName() + "“ haben einen Koffer.",
                    gegen.isEmpty() ? "– kein Gegenstück" : "aussteigen in " + String.join(", ", gegen),
                    zn.isEmpty() ? "–" : String.join(", ", zn), hinweis == null, hinweis, null));
        }
    }

    private static String gutName(Anlage a, KreislaufPruefung.Zeile z) {
        return a.gutName(z.gutId());
    }

    private static String mengeGut(Anlage a, KreislaufPruefung.Zeile z) {
        Gut g = a.gut(z.gutId());
        String einheit = g != null ? g.getEinheit() + " " : "";
        return KreislaufPruefung.zahl(z.menge()) + " " + einheit + a.gutName(z.gutId());
    }

    private static String schubText(KreislaufPruefung.Zeile z) {
        if (z.objekt() instanceof Betrieb b && b.getLieferAlleStunden() > 0) {
            return " (abgegeben alle " + KreislaufPruefung.zahl(b.getLieferAlleStunden()) + " Stunden: "
                    + KreislaufPruefung.zahl(z.menge() * b.getLieferAlleStunden()) + " auf einmal)";
        }
        return "";
    }

    /** {Text zu den Fahrzeugen, Problem oder null}. */
    private static String[] fahrzeuge(Anlage a, KreislaufPruefung.Zeile z, Collection<ZugZustand> zuege) {
        boolean personen = z.gutId() == null;
        Gut gut = personen ? null : a.gut(z.gutId());
        ZugZustand bester = null;
        double max = 0;
        int anzahl = 0;
        for (ZugZustand zz : zuege) {
            double k = personen ? zz.getSitzplaetzeErste() + zz.getSitzplaetzeZweite()
                    : gut != null ? zz.kapazitaetFuer(gut) : 0;
            if (k >= 1) {
                anzahl++;
                if (k > max) {
                    max = k;
                    bester = zz;
                }
            }
        }
        letzterZug = null;
        letzteKapazitaet = 0;
        if (bester == null) {
            return new String[]{"–", personen ? "Es fehlt noch ein Zug: kein Zug mit Plätzen"
                    : "Es fehlt noch ein Zug: kein Zug kann „" + a.gutName(z.gutId())
                    + "“ laden (Wagen ergänzen oder Lade-Joker)"};
        }
        String einheit = personen ? "Plätze" : gut.getEinheit();
        letzterZug = bester.getName();
        letzteKapazitaet = max;
        String text = anzahl + (anzahl == 1 ? " Zug" : " Züge") + ", größter: " + bester.getName() + " ("
                + KreislaufPruefung.zahl(max) + " " + einheit + (bester.hatJoker() && !personen ? ", Joker" : "") + ")";
        if (Double.isInfinite(z.menge()) || z.menge() <= 0) {
            return new String[]{text, null};
        }
        double fahrten = z.menge() / max;
        text += " – " + (fahrten <= 1 ? "1 Fahrt je Stunde reicht"
                : KreislaufPruefung.zahl(Math.ceil(fahrten)) + " Fahrten je Stunde");
        String problem = fahrten > FAHRTEN_GRENZE ? "Kapazitäten müssen ergänzt werden: mehr als "
                + KreislaufPruefung.zahl(FAHRTEN_GRENZE)
                + " Fahrten je Modellstunde nötig – Menge senken oder größere Züge" : null;
        return new String[]{text, problem};
    }

    /** Größter passender Zug der letzten {@link #fahrzeuge}-Abfrage (nur innerhalb von {@link #pruefe}, FX-Thread). */
    private static String letzterZug;
    private static double letzteKapazitaet;

    /**
     * Gegenstück einer Zeile: Name (für den Text), Stationen, an denen es hängt, und ob gar kein Zug nötig ist
     * (wird von außerhalb geliefert, Betrieb nicht auf der Anlage ohne Station, Übergabe ohne Station).
     */
    private record Partner(String name, List<Station> stationen, boolean ohneZug) {
    }

    private static Partner partner(Anlage a, KreislaufPruefung.Zeile z) {
        String g = z.gutId();
        List<AnlagenObjekt> objekte = new ArrayList<>();
        switch (z.abschnitt()) {
            case EINGAENGE, ORTSBEDARF -> {
                if (z.objekt() instanceof Betrieb b && (b.isVirtuell() || b.getImmerGeliefert().contains(g))) {
                    return new Partner("außerhalb", List.of(), true);
                }
                objekte.addAll(Kreislauf.lieferanten(a, g, z.objekt()));
                for (Uebergabe u : a.uebergaben()) {
                    if (u.getAngebot().contains(g)) {
                        objekte.add(u);
                    }
                }
            }
            case AUSGAENGE -> objekte.addAll(Kreislauf.abnehmer(a, g, z.objekt()));
            case ABFAHRT, ANKUNFT -> {
                for (Ort o : a.orte()) {
                    if (o != z.objekt() && KreislaufPruefung.hatBahnsteig(a, o)) {
                        objekte.add(o);
                    }
                }
            }
            default -> {
            }
        }
        List<Station> st = new ArrayList<>();
        List<String> namen = new ArrayList<>();
        boolean ohneStation = false;
        for (AnlagenObjekt o : objekte) {
            List<Station> s = a.stationenFuer(o.getId());
            if (s.isEmpty()) {
                ohneStation = true;
            }
            for (Station x : s) {
                if (!st.contains(x)) {
                    st.add(x);
                }
            }
            namen.add(o.getAnzeigeName());
        }
        // Außenwelt: Schattenbahnhöfe mit Angebot bzw. Abnahme des Guts oder mit Bahnsteig
        for (Station x : a.stationen()) {
            if (!a.istAussenwelt(x) || st.contains(x)) {
                continue;
            }
            boolean passt = false;
            for (Gleis gl : x.getGleise()) {
                if (g == null ? gl.getArt() == GleisArt.BAHNSTEIG
                        : gl.getArt() == GleisArt.LADEGLEIS && (z.abschnitt() == KreislaufPruefung.Abschnitt.AUSGAENGE
                        ? gl.isEntladen() && gl.erlaubt(g) : gl.getAngebot().getOrDefault(g, 0.0) > 0)) {
                    passt = true;
                }
            }
            if (passt) {
                st.add(x);
                namen.add(x.getAnzeigeName());
            }
        }
        if (z.objekt() instanceof Ort o && g == null) {
            for (Station x : KreislaufPruefung.rundfahrt(a, o)) {
                st.add(x);
                namen.add(x.getAnzeigeName() + " (Rundfahrt)");
            }
        }
        boolean ohneZug = st.isEmpty() && ohneStation;
        return new Partner(namen.isEmpty() ? "–" : "„" + String.join("“ oder „", namen) + "“", st, ohneZug);
    }

    /** Gibt es eine iTrain-Zugroute, die einen Block des eigenen Bahnhofs und einen des Gegenstücks enthält? */
    private static boolean routeVorhanden(Anlage a, AnlagenObjekt objekt, List<Station> ziele) {
        List<Station> eigene = objekt instanceof Station s ? List.of(s) : a.stationenFuer(objekt.getId());
        if (eigene.isEmpty()) {
            return true; // ohne eigenen Bahnhof meldet das schon die Kreislaufprüfung
        }
        java.util.Set<String> b1 = new java.util.HashSet<>();
        java.util.Set<String> ids1 = new java.util.HashSet<>();
        for (Station s : eigene) {
            for (String id : a.verbund(s)) {
                ids1.add(id);
                if (a.objekt(id) instanceof Station x) {
                    x.getGleise().forEach(gl -> b1.add(gl.getBlockName()));
                }
            }
        }
        for (Station ziel : ziele) {
            java.util.Set<String> b2 = new java.util.HashSet<>();
            for (String id : a.verbund(ziel)) {
                if (a.objekt(id) instanceof Station x) {
                    x.getGleise().forEach(gl -> b2.add(gl.getBlockName()));
                }
            }
            for (Linie l : a.getLinien()) {
                if (l.getBlockNamen().stream().anyMatch(b1::contains) && l.getBlockNamen().stream().anyMatch(b2::contains)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Zusammenfassung für eine fertige Zeile (Anwender 15.11.2026), umgerechnet auf eine reale Stunde mit dem
     * Uhr-Faktor: „Vom Sägewerk mit Zug XY müssen 24 Stück Bretter in einer realen Stunde angeliefert werden.“
     */
    private static String zusammenfassung(Anlage a, KreislaufPruefung.Zeile z, Partner p, String[] fz, double faktor) {
        if (p.ohneZug()) {
            return "Wird ohne Zug geliefert (von außerhalb bzw. Betrieb nicht auf der Anlage) – fertig.";
        }
        boolean faktorBekannt = !Double.isNaN(faktor) && faktor > 0;
        double f = faktorBekannt ? faktor : 1;
        String uhr = faktorBekannt ? "Uhr-Faktor ×" + KreislaufPruefung.zahl(f) : "Uhr-Faktor unbekannt (×1 gerechnet)";
        String zug = letzterZug == null ? "?" : letzterZug;
        if (Double.isInfinite(z.menge()) || z.menge() <= 0) {
            return "Gegenstück " + p.name() + ", mit Zug „" + zug + "“ – fertig, Menge unbegrenzt.";
        }
        double real = Math.ceil(z.menge() * f);
        double n = letzteKapazitaet >= 1 ? Math.max(1, Math.ceil(real / letzteKapazitaet)) : 0;
        String fahrten = n == 0 ? "" : n == 1 ? " · etwa 1 Fahrt" : " · etwa " + KreislaufPruefung.zahl(n) + " Fahrten";
        String gut = z.gutId() == null ? "Reisende" : (a.gut(z.gutId()) != null ? a.gut(z.gutId()).getEinheit() + " " : "")
                + a.gutName(z.gutId());
        String menge = KreislaufPruefung.zahl(real) + " " + gut;
        // Teile mit „ · “ getrennt, damit die Übersetzung sie einzeln findet
        String satz = switch (z.abschnitt()) {
            case EINGAENGE, ORTSBEDARF -> "Von " + p.name() + " müssen mit Zug „" + zug + "“ in einer realen Stunde "
                    + menge + " angeliefert werden";
            case AUSGAENGE -> "Nach " + p.name() + " müssen mit Zug „" + zug + "“ in einer realen Stunde " + menge
                    + " abgefahren werden";
            case ABFAHRT -> "Nach " + p.name() + " fahren mit Zug „" + zug + "“ in einer realen Stunde " + menge;
            case ANKUNFT -> "Von " + p.name() + " kommen mit Zug „" + zug + "“ in einer realen Stunde " + menge;
            default -> "fertig";
        };
        return satz + fahrten + " · " + uhr;
    }
}
