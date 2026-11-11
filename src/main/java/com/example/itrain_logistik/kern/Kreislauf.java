package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kreislaufpruefung und Mengenabgleich (Konzept Abschnitt 5). Rechnet mit den eingestellten
 * Raten je Modellstunde; bei Betrieben mit mehreren Rezepten zaehlt jeweils das erste Rezept,
 * das ein Gut erzeugt bzw. verbraucht.
 */
public final class Kreislauf {

    public enum Ampel {
        ROT("Mangel"),
        GELB("Überschuss"),
        GRUEN("Ausgeglichen"),
        GRAU("Ausgleich über Übergabe");

        private final String label;

        Ampel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Bilanz eines Guts je Modellstunde. */
    public record Bilanz(Gut gut, double erzeugung, double verbrauch, boolean unbegrenzteQuelle,
            boolean unbegrenzteSenke, int lagerplatz, Ampel ampel) {
    }

    /** Ein offenes Ende eines Betriebs: ein Ausgang ohne Abnehmer oder ein Eingang ohne Lieferant. */
    public record OffenesEnde(Betrieb betrieb, String gutId, boolean ausgang) {
    }

    private Kreislauf() {
    }

    public static List<Bilanz> bilanz(Anlage a) {
        Map<String, double[]> werte = new LinkedHashMap<>();
        Map<String, boolean[]> unbegrenzt = new LinkedHashMap<>();
        for (Gut g : a.getGueter().values()) {
            werte.put(g.getId(), new double[2]);
            unbegrenzt.put(g.getId(), new boolean[2]);
        }
        for (Betrieb b : a.betriebe()) {
            if (b.isVirtuell()) {
                // Liefert immer, ohne auf der Anlage zu sein: unbegrenzte Quelle seiner Ausgänge.
                for (String g : b.ausgangsGueter()) {
                    werte.computeIfAbsent(g, x -> new double[2]);
                    unbegrenzt.computeIfAbsent(g, x -> new boolean[2])[0] = true;
                }
                continue;
            }
            for (String g : b.ausgangsGueter()) {
                werte.computeIfAbsent(g, x -> new double[2])[0] += b.erzeugungProStunde(g);
            }
            for (String g : b.eingangsGueter()) {
                if (!b.getImmerGeliefert().contains(g)) {
                    werte.computeIfAbsent(g, x -> new double[2])[1] += b.verbrauchProStunde(g);
                }
            }
        }
        for (Ort o : a.orte()) {
            o.getBedarf().forEach((g, m) -> werte.computeIfAbsent(g, x -> new double[2])[1] += m);
        }
        for (Uebergabe u : a.uebergaben()) {
            for (String g : u.getAngebot()) {
                unbegrenzt.computeIfAbsent(g, x -> new boolean[2])[0] = true;
            }
            if (u.isAnnahmeAlles()) {
                unbegrenzt.values().forEach(f -> f[1] = true);
            } else {
                for (String g : u.getAnnahme()) {
                    unbegrenzt.computeIfAbsent(g, x -> new boolean[2])[1] = true;
                }
            }
        }
        List<Bilanz> l = new ArrayList<>();
        for (Map.Entry<String, double[]> e : werte.entrySet()) {
            Gut gut = a.gut(e.getKey());
            if (gut == null) {
                continue;
            }
            double erz = e.getValue()[0];
            double ver = e.getValue()[1];
            boolean[] u = unbegrenzt.getOrDefault(e.getKey(), new boolean[2]);
            u = new boolean[]{u[0] || uebergabeLiefert(a, e.getKey()), u[1] || uebergabeNimmt(a, e.getKey())};
            if (erz <= 0 && ver <= 0 && !u[0]) {
                continue; // Gut kommt in der Anlage nicht vor
            }
            int lagerplatz = 0;
            for (Lager lager : a.lager()) {
                if (lager.nimmt(gut)) {
                    lagerplatz += lager.getKapazitaet();
                }
            }
            Ampel ampel;
            if (ver > erz + 1e-6) {
                ampel = u[0] ? Ampel.GRAU : Ampel.ROT;
            } else if (erz > ver + 1e-6) {
                ampel = u[1] ? Ampel.GRAU : Ampel.GELB;
            } else {
                ampel = Ampel.GRUEN;
            }
            l.add(new Bilanz(gut, erz, ver, u[0], u[1], lagerplatz, ampel));
        }
        return l;
    }

    /** Ausgaenge ohne Abnehmer und Eingaenge ohne Lieferant eines Betriebs. */
    public static List<OffenesEnde> offeneEnden(Anlage a, Betrieb b) {
        List<OffenesEnde> l = new ArrayList<>();
        if (b.isVirtuell()) {
            return l;
        }
        for (String g : b.ausgangsGueter()) {
            if (abnehmer(a, g, b).isEmpty() && !uebergabeNimmt(a, g)) {
                l.add(new OffenesEnde(b, g, true));
            }
        }
        for (String g : b.eingangsGueter()) {
            if (lieferanten(a, g, b).isEmpty() && !uebergabeLiefert(a, g) && !a.immerGeliefert(b, g)) {
                l.add(new OffenesEnde(b, g, false));
            }
        }
        return l;
    }

    public static List<OffenesEnde> alleOffenenEnden(Anlage a) {
        List<OffenesEnde> l = new ArrayList<>();
        for (Betrieb b : a.betriebe()) {
            l.addAll(offeneEnden(a, b));
        }
        return l;
    }

    /**
     * Woher ein Eingangsgut eines Betriebs kommt (Anwender 12.10.2026): "Automatisch" (wird immer geliefert bzw.
     * von einem Betrieb, der nicht auf der Anlage ist), sonst die Namen der Hersteller, Übergaben und
     * Schattenbahnhöfe; ohne Lieferant "– kein Lieferant".
     */
    public static String geliefertVon(Anlage a, Betrieb b, String gutId) {
        return geliefertVon(a, b, gutId, b.getImmerGeliefert().contains(gutId));
    }

    /** Wie {@link #geliefertVon(Anlage, Betrieb, String)}, mit "wird immer geliefert" aus einem offenen Dialog. */
    public static String geliefertVon(Anlage a, Betrieb b, String gutId, boolean immer) {
        return geliefertVon(a, b, gutId, immer, b.isVirtuell());
    }

    /** Mit „Liefert immer, ohne auf der Anlage zu sein“ aus einem offenen Dialog. */
    public static String geliefertVon(Anlage a, Betrieb b, String gutId, boolean immer, boolean virtuell) {
        List<String> namen = new ArrayList<>();
        for (Betrieb h : a.betriebe()) {
            if (h != b && h.ausgangsGueter().contains(gutId)) {
                namen.add(a.virtuellOhneStation(h) ? "Automatisch (" + h.getAnzeigeName() + ", nicht auf der Anlage)"
                        : h.getAnzeigeName() + (h.isVirtuell() ? " (nicht auf der Anlage, liefert an "
                        + String.join(", ", a.stationenFuer(h.getId()).stream().map(Station::getAnzeigeName).toList())
                        + ")" : ""));
            }
        }
        for (Uebergabe u : a.uebergaben()) {
            if (u.getAngebot().contains(gutId)) {
                namen.add(u.getAnzeigeName());
            }
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s)) {
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.isLaden() && g.getAngebot().getOrDefault(gutId, 0.0) > 0) {
                        namen.add(s.getAnzeigeName() + " (" + g.getAnzeigeName() + ")");
                    }
                }
            }
        }
        // Auch bei „automatisch“ nennen, wer liefern könnte (Anwender 27.10.2026: „Kann geliefert werden von: …“).
        String koennen = namen.isEmpty() ? "" : " – kann geliefert werden von: " + String.join(", ", namen);
        if (immer) {
            return "Automatisch (wird von außerhalb geliefert)" + koennen;
        }
        if (virtuell) {
            return "Automatisch (liefert immer, ohne auf der Anlage zu sein)" + koennen;
        }
        return namen.isEmpty() ? "– kein Lieferant" : String.join(", ", namen);
    }

    /**
     * Wohin ein Ausgangsgut eines Betriebs geht (Anwender 14.10.2026, Spalte "Geliefert von" bei Ausgängen):
     * "an" und die Namen der verbrauchenden Betriebe, Orte mit Bedarf, Übergaben mit Annahme und
     * Schattenbahnhöfe mit Entladegleis für das Gut; ohne Abnehmer "– kein Abnehmer".
     */
    public static String geliefertAn(Anlage a, Betrieb b, String gutId) {
        List<String> namen = new ArrayList<>();
        for (AnlagenObjekt o : abnehmer(a, gutId, b)) {
            namen.add(o.getAnzeigeName());
        }
        for (Uebergabe u : a.uebergaben()) {
            if (u.nimmt(gutId)) {
                namen.add(u.getAnzeigeName());
            }
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s)) {
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.isEntladen() && g.erlaubt(gutId)) {
                        namen.add(s.getAnzeigeName() + " (" + g.getAnzeigeName() + ")");
                    }
                }
            }
        }
        return namen.isEmpty() ? "– kein Abnehmer" : "an " + String.join(", ", namen);
    }

    /** Betriebe und Orte, die ein Gut verbrauchen (ohne {@code ausser}). */
    public static List<AnlagenObjekt> abnehmer(Anlage a, String gutId, AnlagenObjekt ausser) {
        List<AnlagenObjekt> l = new ArrayList<>();
        for (Betrieb b : a.betriebe()) {
            if (b != ausser && !b.isVirtuell() && b.eingangsGueter().contains(gutId)
                    && !b.getImmerGeliefert().contains(gutId)) {
                l.add(b);
            }
        }
        for (Ort o : a.orte()) {
            if (o != ausser && o.getBedarf().getOrDefault(gutId, 0.0) > 0) {
                l.add(o);
            }
        }
        return l;
    }

    /** Betriebe, die ein Gut erzeugen (ohne {@code ausser}). */
    public static List<Betrieb> lieferanten(Anlage a, String gutId, AnlagenObjekt ausser) {
        List<Betrieb> l = new ArrayList<>();
        for (Betrieb b : a.betriebe()) {
            if (b != ausser && b.ausgangsGueter().contains(gutId)) {
                l.add(b);
            }
        }
        return l;
    }

    /** Nimmt eine Übergabe oder ein Schattenbahnhof (Ladegleis, das Entladen erlaubt) das Gut ab? */
    public static boolean uebergabeNimmt(Anlage a, String gutId) {
        if (a.uebergaben().stream().anyMatch(u -> u.nimmt(gutId))) {
            return true;
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s)) {
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.isEntladen() && g.erlaubt(gutId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Liefert eine Übergabe oder ein Schattenbahnhof (Güterauswahl eines Ladegleises) das Gut? */
    public static boolean uebergabeLiefert(Anlage a, String gutId) {
        if (a.uebergaben().stream().anyMatch(u -> u.getAngebot().contains(gutId))) {
            return true;
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s)) {
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.isLaden() && g.getAngebot().getOrDefault(gutId, 0.0) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Gesamtverbrauch eines Guts je Modellstunde (alle Betriebe und Orte). */
    public static double verbrauch(Anlage a, String gutId) {
        double summe = 0;
        for (Betrieb b : a.betriebe()) {
            if (!b.isVirtuell() && !b.getImmerGeliefert().contains(gutId)) {
                summe += b.verbrauchProStunde(gutId);
            }
        }
        for (Ort o : a.orte()) {
            summe += o.getBedarf().getOrDefault(gutId, 0.0);
        }
        return summe;
    }

    /** Gesamterzeugung eines Guts je Modellstunde. */
    public static double erzeugung(Anlage a, String gutId) {
        double summe = 0;
        for (Betrieb b : a.betriebe()) {
            if (!b.isVirtuell()) {
                summe += b.erzeugungProStunde(gutId);
            }
        }
        return summe;
    }

    /**
     * Erzeuger anpassen (Konzept 5.2): setzt die Rate der Erzeuger eines Guts so, dass die
     * Erzeugung dem Verbrauch entspricht. Mehrere Erzeuger werden im Verhaeltnis ihrer bisherigen
     * Erzeugung angepasst (bei bisher 0 zu gleichen Teilen).
     */
    public static void erzeugerAnpassen(Anlage a, String gutId) {
        List<Betrieb> erzeuger = lieferanten(a, gutId, null);
        erzeuger.removeIf(Betrieb::isVirtuell);
        if (erzeuger.isEmpty()) {
            return;
        }
        double ziel = verbrauch(a, gutId);
        double bisher = erzeugung(a, gutId);
        for (Betrieb b : erzeuger) {
            double menge = mengeJeAusfuehrung(b, gutId, true);
            if (menge <= 0) {
                continue;
            }
            double anteil = bisher > 0 ? b.erzeugungProStunde(gutId) / bisher : 1.0 / erzeuger.size();
            // aufrunden, damit die Erzeugung den Bedarf sicher deckt
            b.setRateProStunde(Math.ceil(ziel * anteil / menge * 100.0 - 1e-6) / 100.0);
        }
    }

    /** Verbraucher anpassen: setzt die Rate der verbrauchenden Betriebe auf die vorhandene Erzeugung. */
    public static void verbraucherAnpassen(Anlage a, String gutId) {
        List<Betrieb> verbraucher = new ArrayList<>();
        for (AnlagenObjekt o : abnehmer(a, gutId, null)) {
            if (o instanceof Betrieb b) {
                verbraucher.add(b);
            }
        }
        if (verbraucher.isEmpty()) {
            return;
        }
        double ortsBedarf = 0;
        for (Ort o : a.orte()) {
            ortsBedarf += o.getBedarf().getOrDefault(gutId, 0.0);
        }
        double verfuegbar = Math.max(0, erzeugung(a, gutId) - ortsBedarf);
        double bisher = 0;
        for (Betrieb b : verbraucher) {
            bisher += b.verbrauchProStunde(gutId);
        }
        for (Betrieb b : verbraucher) {
            double menge = mengeJeAusfuehrung(b, gutId, false);
            if (menge <= 0) {
                continue;
            }
            double anteil = bisher > 0 ? b.verbrauchProStunde(gutId) / bisher : 1.0 / verbraucher.size();
            b.setRateProStunde(runde(verfuegbar * anteil / menge));
        }
    }

    private static double mengeJeAusfuehrung(Betrieb b, String gutId, boolean ausgang) {
        for (Rezept r : b.getRezepte()) {
            Integer m = ausgang ? r.getAusgang().get(gutId) : r.getEingang().get(gutId);
            if (m != null) {
                return m;
            }
        }
        return 0;
    }

    private static double runde(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
