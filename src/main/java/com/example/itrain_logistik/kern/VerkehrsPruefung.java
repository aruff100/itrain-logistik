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
            String[] fz = fahrzeuge(a, z, zuege);
            boolean ok = z.ok() && fz[1] == null;
            String fehlt = z.fehlt() != null && z.gegenstueck() != null && z.gegenstueck().contains(z.fehlt())
                    ? null : z.fehlt();
            String hinweis = fehlt != null ? fehlt : fz[1];
            if (fehlt != null && fz[1] != null) {
                hinweis = fehlt + "; " + fz[1];
            }
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
        if (bester == null) {
            return new String[]{"–", personen ? "kein Zug mit Plätzen" : "kein Zug kann „" + a.gutName(z.gutId())
                    + "“ laden (Wagen ergänzen oder Lade-Joker)"};
        }
        String einheit = personen ? "Plätze" : gut.getEinheit();
        String text = anzahl + (anzahl == 1 ? " Zug" : " Züge") + ", größter: " + bester.getName() + " ("
                + KreislaufPruefung.zahl(max) + " " + einheit + (bester.hatJoker() && !personen ? ", Joker" : "") + ")";
        if (Double.isInfinite(z.menge()) || z.menge() <= 0) {
            return new String[]{text, null};
        }
        double fahrten = z.menge() / max;
        text += " – " + (fahrten <= 1 ? "1 Fahrt je Stunde reicht"
                : KreislaufPruefung.zahl(Math.ceil(fahrten)) + " Fahrten je Stunde");
        String problem = fahrten > FAHRTEN_GRENZE ? "mehr als " + KreislaufPruefung.zahl(FAHRTEN_GRENZE)
                + " Fahrten je Modellstunde nötig – Menge senken oder größere Züge" : null;
        return new String[]{text, problem};
    }
}
