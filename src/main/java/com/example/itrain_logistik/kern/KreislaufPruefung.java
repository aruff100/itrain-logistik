package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Kreislauf prüfen" mit Mengen (Anwender 14.10.2026): Zu jedem Bedarf (Eingang eines Betriebs, Bedarf eines Orts,
 * abreisende und ankommende Reisende) und jedem Angebot (Ausgang eines Betriebs) wird das Gegenstück gesucht, das
 * den Kreislauf schließt, je Modellstunde (M/h). Gezählt werden fehlende Lieferanten, fehlende Abnehmer und ob
 * überhaupt Reisende möglich sind.
 */
public final class KreislaufPruefung {

    public enum Abschnitt {
        EINGAENGE("Eingänge der Betriebe – woher sie kommen"),
        AUSGAENGE("Betriebe, die Ware liefern – wo wird es gebraucht"),
        ORTSBEDARF("Orte, die Ware benötigen – wer liefert an"),
        ABFAHRT("Passagiere, die abfahren – wer nimmt sie auf"),
        ANKUNFT("Passagiere, die ankommen – wer bringt sie");

        private final String titel;

        Abschnitt(String titel) {
            this.titel = titel;
        }

        public String getTitel() {
            return titel;
        }
    }

    /**
     * Eine Zeile: Objekt, Gut (null = Personen), Menge M/h (unendlich = unbegrenzt), Gegenstück, in Ordnung?
     * und ggf. was fehlt.
     */
    public record Zeile(Abschnitt abschnitt, AnlagenObjekt objekt, String gutId, double menge, String gegenstueck,
            boolean ok, String fehlt) {
    }

    /** Ergebnis der Prüfung. */
    public record Ergebnis(List<Zeile> zeilen, int fehlendeLieferanten, int fehlendeAbnehmer, List<String> reisende) {

        public boolean allesOk() {
            return fehlendeLieferanten == 0 && fehlendeAbnehmer == 0 && reisende.isEmpty();
        }

        public List<Zeile> abschnitt(Abschnitt a) {
            return zeilen.stream().filter(z -> z.abschnitt() == a).toList();
        }
    }

    private KreislaufPruefung() {
    }

    public static Ergebnis pruefe(Anlage a) {
        List<Zeile> l = new ArrayList<>();
        int lieferanten = 0;
        int abnehmer = 0;
        List<String> reisende = new ArrayList<>();

        // --- Güter
        for (Betrieb b : a.betriebe()) {
            if (b.isVirtuell()) {
                continue; // Eingänge immer vorhanden
            }
            for (String g : b.eingangsGueter()) {
                double m = b.verbrauchProStunde(g);
                if (a.immerGeliefert(b, g)) {
                    l.add(new Zeile(Abschnitt.EINGAENGE, b, g, m, Kreislauf.geliefertVon(a, b, g), true, null));
                    continue;
                }
                String fehlt = fehltLieferung(a, g);
                if (fehlt != null) {
                    lieferanten++;
                }
                l.add(new Zeile(Abschnitt.EINGAENGE, b, g, m, Kreislauf.geliefertVon(a, b, g), fehlt == null, fehlt));
            }
        }
        for (Betrieb b : a.betriebe()) {
            for (String g : b.ausgangsGueter()) {
                double m = b.isVirtuell() ? Double.POSITIVE_INFINITY : b.erzeugungProStunde(g);
                boolean hat = !Kreislauf.abnehmer(a, g, b).isEmpty() || Kreislauf.uebergabeNimmt(a, g);
                String fehlt = null;
                if (!hat) {
                    fehlt = "kein Abnehmer";
                    if (!b.isVirtuell()) {
                        abnehmer++;
                    }
                } else if (!b.isVirtuell() && !Kreislauf.uebergabeNimmt(a, g)) {
                    double ueber = Kreislauf.erzeugung(a, g) - Kreislauf.verbrauch(a, g);
                    if (ueber > 1e-6) {
                        fehlt = "Überschuss " + zahl(ueber) + " M/h (wird gelagert)";
                    }
                }
                l.add(new Zeile(Abschnitt.AUSGAENGE, b, g, m, Kreislauf.geliefertAn(a, b, g),
                        hat || b.isVirtuell(), fehlt));
            }
        }
        for (Ort o : a.orte()) {
            for (var e : o.getBedarf().entrySet()) {
                if (e.getValue() <= 0) {
                    continue;
                }
                String fehlt = fehltLieferung(a, e.getKey());
                if (fehlt != null) {
                    lieferanten++;
                }
                l.add(new Zeile(Abschnitt.ORTSBEDARF, o, e.getKey(), e.getValue(), lieferantenText(a, e.getKey()),
                        fehlt == null, fehlt));
            }
        }

        // --- Reisende
        for (Ort o : a.orte()) {
            boolean eigener = hatBahnsteig(a, o);
            if (o.getPassagiereProStunde() > 0) {
                double aufnahme = 0;
                List<String> ziele = new ArrayList<>();
                for (Ort o2 : a.orte()) {
                    if (o2 != o && hatBahnsteig(a, o2) && !gleicheStation(a, o, o2)) {
                        aufnahme += o2.isAnkommendUnbegrenzt() ? Double.POSITIVE_INFINITY : o2.getAnkommendProStunde();
                        ziele.add(o2.getAnzeigeName() + " (" + (o2.isAnkommendUnbegrenzt() ? "unbegrenzt"
                                : zahl(o2.getAnkommendProStunde()) + " M/h") + ")");
                    }
                }
                for (Station s : schattenbahnhoefeMitBahnsteig(a)) {
                    aufnahme = Double.POSITIVE_INFINITY;
                    ziele.add(s.getAnzeigeName() + " (unbegrenzt)");
                }
                String fehlt = null;
                if (!eigener) {
                    fehlt = ohneBahnsteigText(a, o);
                } else if (ziele.isEmpty()) {
                    fehlt = "kein Ziel – weiterer Bahnhof oder Schattenbahnhof nötig";
                } else if (aufnahme + 1e-6 < o.getPassagiereProStunde()) {
                    fehlt = "Ziele nehmen nur " + zahl(aufnahme) + " M/h auf";
                }
                if (fehlt != null) {
                    reisende.add(o.getAnzeigeName() + ": " + fehlt);
                }
                l.add(new Zeile(Abschnitt.ABFAHRT, o, null, o.getPassagiereProStunde(),
                        ziele.isEmpty() ? "– niemand" : "nach " + String.join(", ", ziele), fehlt == null, fehlt));
            }
            if (eigener && (o.isAnkommendUnbegrenzt() || o.getAnkommendProStunde() > 0)) {
                double angebot = 0;
                List<String> quellen = new ArrayList<>();
                for (Ort o2 : a.orte()) {
                    if (o2 != o && hatBahnsteig(a, o2) && !gleicheStation(a, o, o2) && o2.getPassagiereProStunde() > 0) {
                        angebot += o2.getPassagiereProStunde();
                        quellen.add(o2.getAnzeigeName() + " (" + zahl(o2.getPassagiereProStunde()) + " M/h)");
                    }
                }
                for (Station s : schattenbahnhoefeMitBahnsteig(a)) {
                    double m = s.gleiseDerArt(GleisArt.BAHNSTEIG).stream().mapToDouble(Gleis::getPassagiereProStunde)
                            .sum();
                    if (m > 0) {
                        angebot += m;
                        quellen.add(s.getAnzeigeName() + " (" + zahl(m) + " M/h)");
                    }
                }
                String fehlt = null;
                if (o.isAnkommendUnbegrenzt()) {
                    if (quellen.isEmpty()) {
                        fehlt = "niemand bringt Reisende – weiterer Bahnhof oder Schattenbahnhof nötig";
                    }
                } else if (angebot + 1e-6 < o.getAnkommendProStunde()) {
                    fehlt = quellen.isEmpty() ? "niemand bringt Reisende – weiterer Bahnhof oder Schattenbahnhof nötig"
                            : "es kommen nur " + zahl(angebot) + " M/h";
                }
                if (fehlt != null && o.getPassagiereProStunde() <= 0) {
                    reisende.add(o.getAnzeigeName() + ": " + fehlt);
                }
                l.add(new Zeile(Abschnitt.ANKUNFT, o, null,
                        o.isAnkommendUnbegrenzt() ? Double.POSITIVE_INFINITY : o.getAnkommendProStunde(),
                        quellen.isEmpty() ? "– niemand" : "von " + String.join(", ", quellen), fehlt == null, fehlt));
            }
        }
        return new Ergebnis(l, lieferanten, abnehmer, reisende);
    }

    /** null, wenn das Gut ausreichend geliefert wird; sonst was fehlt. */
    static String fehltLieferung(Anlage a, String gutId) {
        boolean unbegrenzt = Kreislauf.uebergabeLiefert(a, gutId);
        for (Betrieb b : a.betriebe()) {
            if (b.isVirtuell() && b.ausgangsGueter().contains(gutId)) {
                unbegrenzt = true;
            }
        }
        if (unbegrenzt) {
            return null;
        }
        if (Kreislauf.lieferanten(a, gutId, null).isEmpty()) {
            return "kein Lieferant";
        }
        double fehlt = Kreislauf.verbrauch(a, gutId) - Kreislauf.erzeugung(a, gutId);
        return fehlt > 1e-6 ? "es fehlen " + zahl(fehlt) + " M/h" : null;
    }

    /** Lieferanten eines Guts als Text (Hersteller, Übergaben, Schattenbahnhöfe). */
    static String lieferantenText(Anlage a, String gutId) {
        Set<String> n = new LinkedHashSet<>();
        for (Betrieb b : Kreislauf.lieferanten(a, gutId, null)) {
            n.add(b.getAnzeigeName() + (b.isVirtuell() ? " (nicht auf der Anlage)"
                    : " (" + zahl(b.erzeugungProStunde(gutId)) + " M/h)"));
        }
        for (Uebergabe u : a.uebergaben()) {
            if (u.getAngebot().contains(gutId)) {
                n.add(u.getAnzeigeName());
            }
        }
        for (Station s : a.stationen()) {
            if (a.istAussenwelt(s)) {
                for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (g.isLaden() && g.getAngebot().getOrDefault(gutId, 0.0) > 0) {
                        n.add(s.getAnzeigeName());
                    }
                }
            }
        }
        return n.isEmpty() ? "– kein Lieferant" : String.join(", ", n);
    }

    /** Hat der Ort eine Station mit Bahnsteig? */
    public static boolean hatBahnsteig(Anlage a, Ort o) {
        return a.stationenFuer(o.getId()).stream().anyMatch(s -> !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()
                && s.getArt() != ObjektArt.ANDERES);
    }

    /**
     * Warum ein Ort keinen Bahnsteig hat (Anwender 20.10.2026: "Ich habe einen Hauptbahnhof und einen Nebenbahnhof,
     * trotzdem kein Bahnsteig"): Reisende entstehen nur an Stationen, deren Einzugsbereich den Ort enthält. Der Text
     * nennt, womit der Ort verbunden ist und welcher vorhandene Bahnhof in Frage kommt.
     */
    public static String ohneBahnsteigText(Anlage a, Ort o) {
        List<Station> verbunden = a.stationenFuer(o.getId());
        // häufigster Fall bei frisch importierten Anlagen: iTrain-Bahnhof vom Typ "other" = Art "Anderes"
        for (Station s : verbunden) {
            if (s.getArt() == ObjektArt.ANDERES && !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()) {
                return "„" + s.getAnzeigeName() + "“ hat noch die Art „Anderes“ (aus iTrain „other“) – dort halten Züge "
                        + "ohne Ein- und Aussteigen; im Stationsdialog Art „Bahnhof“ oder „Haltepunkt“ wählen";
            }
        }
        String vorschlag = bahnhoefeZumVerbinden(a, o).stream().findFirst()
                .map(s -> " – mit „" + s.getAnzeigeName() + "“ verbinden").orElse(" – Bahnhof oder Haltepunkt anlegen");
        if (verbunden.isEmpty()) {
            return o.getAnzeigeName() + " ist mit keinem Bahnhof verbunden" + vorschlag;
        }
        List<String> namen = new ArrayList<>();
        for (Station s : verbunden) {
            namen.add(s.getAnzeigeName() + (s.getArt() == ObjektArt.ANDERES ? " (Art „Anderes“)"
                    : s.getArt() == ObjektArt.GUETERBAHNHOF ? " (Güterbahnhof)" : " (ohne Bahnsteig)"));
        }
        return "nur mit " + String.join(", ", namen) + " verbunden" + vorschlag;
    }

    /**
     * Vorhandene Bahnhöfe und Haltepunkte mit Bahnsteig, mit denen ein Ort noch nicht verbunden ist; zuerst der
     * Bahnhof, zu dem ein schon verbundener Güterbahnhof gehört.
     */
    public static List<Station> bahnhoefeZumVerbinden(Anlage a, Ort o) {
        List<Station> verbunden = a.stationenFuer(o.getId());
        List<Station> l = new ArrayList<>();
        for (Station s : verbunden) {
            Station p = a.gehoertZu(s);
            if (p != null && !verbunden.contains(p) && !l.contains(p) && personenStation(a, p) && a.ortErlaubt(p, o)) {
                l.add(p);
            }
        }
        for (Station s : a.stationen()) {
            if (!verbunden.contains(s) && !l.contains(s) && personenStation(a, s) && a.ortErlaubt(s, o)) {
                l.add(s);
            }
        }
        return l;
    }

    /** Verbundene Stationen der Art "Anderes" mit Bahnsteigen: werden sie Bahnhof, hat der Ort seinen Bahnsteig. */
    public static List<Station> anderesMitBahnsteig(Anlage a, Ort o) {
        return a.stationenFuer(o.getId()).stream().filter(s -> s.getArt() == ObjektArt.ANDERES
                && !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()).toList();
    }

    private static boolean personenStation(Anlage a, Station s) {
        // Schattenbahnhof mit Reisenden aus angeschlossenem Ort zählt wie ein Bahnhof (Anwender 27.10.2026)
        boolean bahnhof = (s.getArt() == ObjektArt.BAHNHOF || s.getArt() == ObjektArt.HALTEPUNKT) && !a.istAussenwelt(s)
                || s.getArt() == ObjektArt.SCHATTENBAHNHOF && !a.reisendeVonAussen(s);
        return bahnhof && !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty();
    }

    /** Bedienen zwei Orte nur dieselben Stationen (dann kann man nicht zwischen ihnen reisen)? */
    private static boolean gleicheStation(Anlage a, Ort o1, Ort o2) {
        Set<String> s1 = new LinkedHashSet<>();
        a.stationenFuer(o1.getId()).forEach(s -> s1.add(s.getId()));
        Set<String> s2 = new LinkedHashSet<>();
        a.stationenFuer(o2.getId()).forEach(s -> s2.add(s.getId()));
        return s1.equals(s2);
    }

    public static List<Station> schattenbahnhoefeMitBahnsteig(Anlage a) {
        return a.stationen().stream().filter(s -> a.reisendeVonAussen(s) && !s.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty())
                .toList();
    }

    public static String zahl(double v) {
        if (Double.isInfinite(v)) {
            return "unbegrenzt";
        }
        if (Math.abs(v - Math.rint(v)) < 1e-6) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(java.util.Locale.GERMANY, "%.1f", v);
    }
}
