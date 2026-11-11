package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Zufriedenheit der Orte und Erfüllung der Betriebe (Anwender 11.11.2026). Gerechnet aus dem aktuellen Zustand,
 * nicht aus der Geschichte:
 * <ul>
 * <li>Ort, je Gut mit Bedarf: offener Bedarf in Stunden; 0 Stunden offen = 100 %, ab {@link #STUNDEN_OFFEN} = 0 %
 * (dieselbe Grenze, ab der „Bedarf wächst“ wieder sinkt).</li>
 * <li>Ort, Reisende: Wartende im Ort und an seinen Bahnsteigen; ab zwei Stunden Aufkommen = 0 %.</li>
 * <li>Betrieb, je Eingang: Bestand reicht eine Modellstunde = 100 % (immer geliefert: 100 %).</li>
 * <li>Betrieb, je Ausgang: Lager bis halb voll = 100 %, voll = 0 % (die Herstellung steht).</li>
 * </ul>
 * Gesamtwert = Mittel der Posten. Farben: unter 50 % rot, 50–85 % gelb, ab 86 % grün.
 */
public final class Bewertung {

    public static final double STUNDEN_OFFEN = 6;
    public static final double STUNDEN_REISENDE = 2;

    /** Ein Posten der Tabelle: Gut (null = Reisende), Text, Erfüllungsgrad 0..100. */
    public record Posten(String gutId, String text, double prozent) { }

    /** Gesamtwert (0..100, negativ = nichts zu bewerten), Posten und Maßnahmen. */
    public record Ergebnis(double prozent, List<Posten> posten, List<String> massnahmen) {
        public boolean leer() {
            return prozent < 0;
        }
    }

    private Bewertung() { }

    /** Farbe für einen Prozentwert: rot, gelb (dunkel, lesbar), grün. */
    public static String farbe(double p) {
        return p < 50 ? "#e53935" : p <= 85 ? "#f9c80e" : "#43a047";
    }

    private static double grad(double x) {
        return Math.max(0, Math.min(100, x * 100));
    }

    // ------------------------------------------------------------------ Ort

    public static Ergebnis ort(Anlage a, Ort o) {
        List<Posten> posten = new ArrayList<>();
        List<String> mass = new ArrayList<>();
        for (var e : o.getBedarf().entrySet()) {
            String g = e.getKey();
            double d = o.bedarfAktuell(g);
            if (d <= 0 || a.gut(g) == null) {
                continue;
            }
            double offen = o.getOffenerBedarf().getOrDefault(g, 0.0);
            double p = grad(1 - offen / (STUNDEN_OFFEN * d));
            String n = a.gutName(g);
            String text = p > 85 ? "Anlieferung " + n + " entspricht der Vorgabe"
                    : p >= 50 ? "Anlieferung " + n + " etwas zu gering" : "Zu wenig " + n + ", mehr anliefern";
            posten.add(new Posten(g, text, p));
            if (p < 50) {
                mass.add(gutMassnahme(a, g, d, o));
            }
        }
        if (o.getPassagiereProStunde() > 0 && KreislaufPruefung.hatBahnsteig(a, o)) {
            double w = o.getReisende();
            Set<String> bahnsteige = new LinkedHashSet<>();
            for (Station s : a.stationenFuer(o.getId())) {
                for (Gleis gl : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                    if (bahnsteige.add(gl.getId())) {
                        for (double[] x : gl.getWartende().values()) {
                            w += x[0] + x[1];
                        }
                    }
                }
            }
            double p = grad(1 - w / (STUNDEN_REISENDE * o.getPassagiereProStunde()));
            String text = p > 85 ? "Reisende werden abgeholt" : p >= 50 ? "Reisende warten, öfter abholen"
                    : "Zu viele Reisende warten, mehr Züge";
            posten.add(new Posten(null, text, p));
            if (p < 50) {
                List<String> st = a.stationenFuer(o.getId()).stream().map(Station::getAnzeigeName).toList();
                mass.add("Mehr Züge mit Sitzplätzen in " + String.join(", ", st) + " halten lassen ("
                        + Math.round(w) + " Reisende warten, " + Math.round(o.getPassagiereProStunde())
                        + " je Modellstunde)");
            }
        }
        return ergebnis(posten, mass);
    }

    // ------------------------------------------------------------------ Betrieb

    public static Ergebnis betrieb(Anlage a, Betrieb b) {
        List<Posten> posten = new ArrayList<>();
        List<String> mass = new ArrayList<>();
        if (b.isVirtuell() && a.virtuellOhneStation(b)) {
            return new Ergebnis(-1, posten, mass);
        }
        for (String g : b.eingangsGueter()) {
            double v = b.verbrauchProStunde(g);
            if (v <= 0 || a.gut(g) == null) {
                continue;
            }
            String n = a.gutName(g);
            if (b.isVirtuell() || a.immerGeliefert(b, g)) {
                posten.add(new Posten(g, "Anlieferung " + n + ": wird von außerhalb geliefert", 100));
                continue;
            }
            double p = grad(b.getBestandEin().getOrDefault(g, 0.0) / v);
            String text = p > 85 ? "Anlieferung " + n + " entspricht der Vorgabe"
                    : p >= 50 ? n + " wird knapp" : "Zu wenig " + n + ", mehr anliefern";
            posten.add(new Posten(g, text, p));
            if (p < 50) {
                mass.add(gutMassnahme(a, g, v, b));
            }
        }
        for (String g : b.ausgangsGueter()) {
            double kap = b.kapazitaetAus(g);
            if (a.gut(g) == null || kap <= 0) {
                continue;
            }
            String n = a.gutName(g);
            double fuell = b.getBestandAus().getOrDefault(g, 0.0) / kap;
            double p = grad(fuell <= 0.5 ? 1 : (1 - fuell) / 0.5);
            String text = p > 85 ? "Herstellung " + n + " läuft, wird abgeholt"
                    : p >= 50 ? n + " staut sich, öfter abholen" : "Lager " + n + " voll, Herstellung steht – mehr abholen";
            posten.add(new Posten(g, text, p));
            if (p < 50) {
                List<AnlagenObjekt> abn = new ArrayList<>(Kreislauf.abnehmer(a, g, b));
                for (Station s : a.stationen()) {
                    if (a.istAussenwelt(s) && !abn.contains(s)) {
                        for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                            if (gl.isEntladen() && gl.erlaubt(g) && (gl.getAbnahme().isEmpty()
                                    || gl.getAbnahme().containsKey(g))) {
                                abn.add(s);
                                break;
                            }
                        }
                    }
                }
                mass.add(abn.isEmpty() ? "Für " + n + " gibt es keinen Abnehmer – Abnehmer anlegen (Zusätzlich erforderlich)"
                        : "Mehr " + n + " abholen: mehr Züge von " + stationen(a, b) + " zu "
                        + String.join(", ", abn.stream().map(AnlagenObjekt::getAnzeigeName).limit(3).toList()));
            }
            // Bedarf der Abnehmer größer als die Herstellung?
            double bedarf = bedarfVon(a, g);
            double erz = b.erzeugungProStunde(g);
            if (erz > 0 && bedarf > erz * 1.05 && hersteller(a, g).size() <= 1) {
                mass.add("Produktion erhöhen, um den Bedarf zu decken: " + n + " gebraucht " + Math.round(bedarf)
                        + " M/h, hergestellt " + Math.round(erz) + " M/h");
            }
        }
        return ergebnis(posten, mass);
    }

    // ------------------------------------------------------------------ Hilfen

    private static Ergebnis ergebnis(List<Posten> posten, List<String> mass) {
        if (posten.isEmpty()) {
            return new Ergebnis(-1, posten, mass);
        }
        double s = 0;
        for (Posten p : posten) {
            s += p.prozent();
        }
        return new Ergebnis(s / posten.size(), posten, mass);
    }

    /** Liegt das Gut irgendwo bereit (oder gibt es eine unbegrenzte Quelle), fehlen Züge; sonst Produktion. */
    private static String gutMassnahme(Anlage a, String g, double jeStunde, AnlagenObjekt ziel) {
        String n = a.gutName(g);
        double bereit = 0;
        boolean unbegrenzt = false;
        for (Betrieb x : a.betriebe()) {
            if (x != ziel) {
                bereit += x.getBestandAus().getOrDefault(g, 0.0);
            }
        }
        for (Lager l : a.lager()) {
            bereit += l.getBestand().getOrDefault(g, 0.0);
        }
        for (Station s : a.stationen()) {
            for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                if (!a.stationenFuer(ziel.getId()).contains(s)) {
                    bereit += gl.getAusgang().getOrDefault(g, 0.0);
                }
                if (a.istAussenwelt(s) && gl.getAngebot().getOrDefault(g, 0.0) > 0) {
                    unbegrenzt = true;
                }
            }
        }
        for (Uebergabe u : a.uebergaben()) {
            if (u.getAngebot().contains(g)) {
                unbegrenzt = true;
            }
        }
        String nach = stationen(a, ziel);
        if (unbegrenzt || bereit >= Math.max(1, jeStunde)) {
            return "Mehr " + n + " anliefern: mehr Züge nach " + nach + (unbegrenzt ? ""
                    : " (" + Math.round(bereit) + " stehen bereit)");
        }
        List<Betrieb> h = hersteller(a, g);
        if (h.isEmpty()) {
            return "Für " + n + " gibt es keinen Hersteller – Lieferant anlegen (Zusätzlich erforderlich)";
        }
        return "Mehr Produktion: " + String.join(", ", h.stream().map(Betrieb::getAnzeigeName).limit(3).toList())
                + " erhöhen, um den Bedarf an " + n + " zu decken";
    }

    private static List<Betrieb> hersteller(Anlage a, String g) {
        return a.betriebe().stream().filter(x -> x.ausgangsGueter().contains(g)).toList();
    }

    private static double bedarfVon(Anlage a, String g) {
        double s = 0;
        for (Ort o : a.orte()) {
            s += o.bedarfAktuell(g);
        }
        for (Betrieb x : a.betriebe()) {
            if (!a.immerGeliefert(x, g) && !x.isVirtuell()) {
                s += x.verbrauchProStunde(g);
            }
        }
        return s;
    }

    private static String stationen(Anlage a, AnlagenObjekt o) {
        List<Station> l = a.stationenFuer(o.getId());
        return l.isEmpty() ? o.getAnzeigeName() : String.join(", ", l.stream().map(Station::getAnzeigeName).toList());
    }
}
