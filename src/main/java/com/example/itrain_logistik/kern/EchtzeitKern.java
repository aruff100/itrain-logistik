package com.example.itrain_logistik.kern;

import com.example.itrain_logistik.SimulatorTrigger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Der Echtzeit-Kern (Konzept analyse/Konzept-Echtzeitkern.md, Abschnitte 2 bis 9). Rechnet
 * fortlaufend nach der iTrain-Modellzeit in Schritten von hoechstens einer Modellminute:
 * <ol>
 *   <li>Umladen an Gleisen, an denen gerade ein Zug haelt (Abschnitt 9),</li>
 *   <li>Produktion der Betriebe nach Rezept und Rate (Abschnitt 4.3),</li>
 *   <li>Fluss Ausgangslager / Lager / Uebergabe zum Ladegleis und vom Ladegleis zu den
 *       Abnehmern (Abschnitt 6.3),</li>
 *   <li>Passagiere und Gueterbedarf der Orte (Abschnitte 4.4 und 8.1).</li>
 * </ol>
 * Zugereignisse ({@link #verarbeite(SimulatorTrigger)}) kommen vom iTrain-Logistik-Simulator
 * bzw. spaeter von iTrain. Alles laeuft auf dem JavaFX-Anwendungsthread.
 */
public final class EchtzeitKern {

    private static final double EPS = 1e-6;

    /**
     * Güter bewegen sich nur in vollen Einheiten (Anwender 14.10.2026): eine 0,3 oder 0,4 gibt es nicht.
     * Bruchteile entstehen nur intern als Guthaben (Umladezeit, Mengen je Modellstunde) und werden erst
     * ab einer ganzen Einheit bewegt.
     */
    static double ganz(double v) {
        return v >= Double.MAX_VALUE ? v : Math.floor(v + EPS);
    }

    /** Guthaben fürs Umladen je Gleis und Zug ("gleisId|zug") bzw. je Wagen; Rest je Schattenbahnhof-Gleis und Gut. */
    private final Map<String, Double> umladeGuthaben = new java.util.HashMap<>();
    private final Map<Wagenladung, Double> wagenGuthaben = new java.util.IdentityHashMap<>();
    private final Map<String, Double> angebotsRest = new java.util.HashMap<>();
    /** Personen nur ganz (Anwender 14.10.2026): Bruchteile je Warteschlange ("gleisId|richtung") und Anteil 1. Klasse. */
    private final Map<String, Double> personenRest = new java.util.HashMap<>();
    private final Map<String, Double> ersteRest = new java.util.HashMap<>();

    /**
     * Stellt ganze Personen in eine Warteschlange: {@code menge} (mit Bruchteil) wird je Schlange gesammelt,
     * höchstens bis zum freien Platz, und nach dem Anteil 1. Klasse ganzzahlig aufgeteilt.
     *
     * @return Zahl der hinzugekommenen Personen
     */
    private double stellePersonenEin(Gleis g, String richtung, double[] w, double menge, double anteilErste) {
        String schluessel = g.getId() + "|" + richtung;
        double guthaben = Math.min(menge + 1, personenRest.getOrDefault(schluessel, 0.0) + menge);
        double platz = ganz(g.getKapazitaet() - w[0] - w[1]);
        double m = Math.max(0, ganz(Math.min(guthaben, platz)));
        personenRest.put(schluessel, guthaben - m);
        if (m <= 0) {
            return 0;
        }
        double erste = ersteRest.getOrDefault(schluessel, 0.0) + m * anteilErste;
        double e1 = Math.min(m, ganz(erste));
        ersteRest.put(schluessel, erste - e1);
        w[0] += e1;
        w[1] += m - e1;
        if (g.isMitKoffer()) {
            g.setKofferWartend(g.getKofferWartend() + m * Gleis.KOFFER_ANTEIL);
        }
        return m;
    }

    // ---------------------------------------------------------------- Post und Expressgut (Anwender 27.10.2026)

    private final Map<String, Double> postRest = new java.util.HashMap<>();
    /** Nächster Bahnsteig je Ort und Gut, damit Post reihum auf alle Bahnsteige verteilt wird. */
    private final Map<String, Integer> postReihum = new java.util.HashMap<>();

    /** Orte geben Post und Expressgut je nach Art (Stadt/Dorf/Ländlich) an die Bahnsteige ihrer Stationen. */
    private void postErzeugen(Ort o, double dt) {
        double faktor = anlage.faktor(o);
        if (faktor <= 0 || (o.lieferung(Tf3Daten.POST) <= 0 && o.lieferung(Tf3Daten.EXPRESSGUT) <= 0)) {
            return;
        }
        List<Gleis> bahnsteige = new ArrayList<>();
        for (Station s : anlage.stationenFuer(o.getId())) {
            if (anlage.reisendeVonAussen(s)) {
                continue;
            }
            for (Gleis g : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                if (g.isLaden()) {
                    bahnsteige.add(g);
                }
            }
        }
        if (bahnsteige.isEmpty()) {
            return;
        }
        for (String gutId : List.of(Tf3Daten.POST, Tf3Daten.EXPRESSGUT)) {
            if (anlage.gut(gutId) == null || o.lieferung(gutId) <= 0) {
                continue;
            }
            String k = o.getId() + "|" + gutId;
            double r = postRest.getOrDefault(k, 0.0) + o.lieferung(gutId) * faktor * dt / 3600.0;
            int i = 0;
            int start = postReihum.getOrDefault(k, 0);
            while (r >= 1 - EPS && i < bahnsteige.size() * 50) {
                Gleis g = bahnsteige.get((start + i++) % bahnsteige.size());
                postReihum.put(k, (start + i) % bahnsteige.size());
                if (g.getAusgang().getOrDefault(gutId, 0.0) + 1 <= Math.max(Tf3Daten.POST_KAPAZITAET, g.getKapazitaet())) {
                    g.getAusgang().merge(gutId, 1.0, Double::sum);
                    r -= 1;
                } else if (i >= bahnsteige.size()) {
                    break; // alle Bahnsteige voll
                }
            }
            postRest.put(k, Math.min(r, 1));
        }
    }

    /** Am Bahnsteig: Post/Expressgut aus anderen Stationen ausladen, wartende Post einladen (nur Personenzug). */
    private void postUmladen(ZugZustand z, Station s, Gleis g) {
        for (String gutId : List.of(Tf3Daten.POST, Tf3Daten.EXPRESSGUT)) {
            Gut gut = anlage.gut(gutId);
            if (gut == null) {
                continue;
            }
            Station von = z.getGeladenIn().get(gutId);
            // Schattenbahnhof: nur Bahnsteige, die das Gut annehmen (Anwender 27.10.2026)
            boolean annehmen = !anlage.istAussenwelt(s) || g.nimmtAn(gutId);
            if (g.isEntladen() && von != s && annehmen) {
                double m = 0;
                boolean aussen = anlage.istAussenwelt(s);
                double grenze = aussen ? Math.floor(g.abnahmeFrei(gutId, stunde) + 1e-6) : Double.POSITIVE_INFINITY;
                for (Wagenladung w : z.getWagenladungen()) {
                    double x = Math.min(w.getInhalt().getOrDefault(gutId, 0.0), grenze - m);
                    if (x > EPS) {
                        w.entlade(gutId, x);
                        m += x;
                    }
                }
                if (aussen && m > EPS) {
                    g.zaehleAbnahme(gutId, m, stunde);
                }
                if (m > EPS) {
                    Ort ort = anlage.ortVon(s);
                    if (ort != null) {
                        ort.getStatistik().addiere(gutId.equals(Tf3Daten.POST) ? "Post empfangen" : "Expressgut empfangen",
                                tag, m);
                        fluss(s, ort, gutId, m);
                    }
                    g.zaehle(gutId, true, m, tag);
                    transfer(gutId, von, s, m, z.getName());
                    z.getGeladenIn().remove(gutId);
                }
            }
            double bereit = Math.floor(g.getAusgang().getOrDefault(gutId, 0.0) + EPS);
            // Nur in Personenzüge (Zug mit Sitzplätzen), nicht in einen Güterzug am selben Block (Anwender 27.10.2026)
            boolean personenzug = z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() > 0;
            if (g.isLaden() && bereit >= 1 && personenzug) {
                double m = 0;
                for (Wagenladung w : z.getWagenladungen()) {
                    double x = Math.min(bereit - m, w.freiFuer(gut));
                    if (x >= 1) {
                        w.lade(gut, x);
                        m += x;
                    }
                }
                if (m > 0) {
                    g.getAusgang().merge(gutId, -m, Double::sum);
                    z.getGeladenIn().put(gutId, s);
                    g.zaehle(gutId, false, m, tag);
                    Ort ort = anlage.ortVon(s);
                    if (ort != null) {
                        fluss(ort, s, gutId, m);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Koffer (Anwender 27.10.2026)

    private double kofferGeladen(ZugZustand z) {
        double n = 0;
        for (Wagenladung w : z.getWagenladungen()) {
            n += w.getInhalt().getOrDefault(Gleis.KOFFER_GUT, 0.0);
        }
        return n;
    }

    private double kofferFrei(ZugZustand z) {
        Gut k = anlage.gut(Gleis.KOFFER_GUT);
        double n = 0;
        for (Wagenladung w : z.getWagenladungen()) {
            n += w.freiFuer(k);
        }
        return n;
    }

    /** Koffer im Gepäckwagen auf ganze {@code floor(kofferReisende)} Stück bringen. */
    private void kofferAngleichen(ZugZustand z) {
        Gut k = anlage.gut(Gleis.KOFFER_GUT);
        if (k == null) {
            return;
        }
        double soll = Math.floor(z.getKofferReisende() + 1e-6);
        double ist = kofferGeladen(z);
        for (Wagenladung w : z.getWagenladungen()) {
            if (ist < soll) {
                double m = Math.min(soll - ist, w.freiFuer(k));
                if (m > 0) {
                    w.lade(k, m);
                    ist += m;
                }
            } else if (ist > soll) {
                double m = Math.min(ist - soll, w.getInhalt().getOrDefault(Gleis.KOFFER_GUT, 0.0));
                if (m > 0) {
                    w.entlade(Gleis.KOFFER_GUT, m);
                    ist -= m;
                }
            }
        }
    }
    /** Laengster Rueckstand, der nach einer Pause nachgerechnet wird (Modellsekunden). */
    private static final double MAX_NACHHOLEN = 6 * 3600;

    public record Ereignis(String zeit, String text) {
    }

    private Anlage anlage;
    private final Function<String, ZugAusstattung> ausstattung;
    private double letzteSimSekunden = -1;
    /** Gesamter Güter-/Personenaustausch angehalten (Knopf im Ribbon); Zugereignisse werden weiter erfasst. */
    private boolean angehalten;

    public boolean isAngehalten() {
        return angehalten;
    }

    public void setAngehalten(boolean angehalten) {
        this.angehalten = angehalten;
    }
    private int tag;
    private int stunde;
    private final Set<String> verarbeiteteTrigger = new HashSet<>();
    private final Deque<String> triggerReihenfolge = new ArrayDeque<>();
    private final Deque<Ereignis> ereignisse = new ArrayDeque<>();
    private String modellZeitText = "";

    /**
     * @param ausstattung liefert zu einem Zugnamen aus iTrain die Wagenladungen und Sitzplaetze
     *                    (aus Zusammenstellung und Wagenfeldern, siehe Fahrzeugdaten im Hauptpaket)
     */
    /**
     * Beobachter für die Anzeige: meldet jede Bewegung von Gütern (gutId) oder Personen (gutId null)
     * zwischen einer Station und einem Objekt ihres Einzugsbereichs bzw. zwischen Zug (Anlagen-
     * Infrastruktur) und Station. Mengen sind Einheiten bzw. Personen.
     */
    public interface FlussBeobachter {
        void fluss(AnlagenObjekt von, AnlagenObjekt nach, String gutId, double menge);
    }

    private final List<FlussBeobachter> beobachter = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Erfolgreicher Transfer (Anwender 22.10.2026): Reisende steigen an einer anderen Station aus, als sie
     * eingestiegen sind ({@code gutId} null), bzw. ein Gut wird an einer anderen Station entladen, als es geladen
     * wurde. {@code von} ist null, wenn die Herkunft nicht bekannt ist (z. B. vor dem Programmstart geladen).
     */
    public interface TransferBeobachter {
        void transfer(String gutId, Station von, Station nach, double menge, String zug);
    }

    private final List<TransferBeobachter> transferBeobachter = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addTransferBeobachter(TransferBeobachter b) {
        transferBeobachter.add(b);
    }

    private void transfer(String gutId, Station von, Station nach, double menge, String zug) {
        if (menge > EPS && nach != null && von != nach) {
            for (TransferBeobachter b : transferBeobachter) {
                b.transfer(gutId, von, nach, menge, zug);
            }
        }
    }

    public void addFlussBeobachter(FlussBeobachter b) {
        beobachter.add(b);
    }

    public void removeFlussBeobachter(FlussBeobachter b) {
        beobachter.remove(b);
    }

    private void fluss(AnlagenObjekt von, AnlagenObjekt nach, String gutId, double menge) {
        if (menge > EPS && von != null && nach != null) {
            for (FlussBeobachter b : beobachter) {
                b.fluss(von, nach, gutId, menge);
            }
        }
    }

    public EchtzeitKern(Function<String, ZugAusstattung> ausstattung) {
        this.ausstattung = ausstattung;
    }

    public void setAnlage(Anlage anlage) {
        this.anlage = anlage;
        this.letzteSimSekunden = -1;
        verarbeiteteTrigger.clear();
        triggerReihenfolge.clear();
        umladeGuthaben.clear();
        wagenGuthaben.clear();
        angebotsRest.clear();
        personenRest.clear();
        ersteRest.clear();
        if (anlage != null) {
            ganzeEinheiten(anlage);
            // Gespeicherte Zuege kennen nach dem Laden kein Gleis mehr (Objektbezug), Halt beenden.
            for (ZugZustand z : anlage.getZuege().values()) {
                z.setHaltend(false);
                z.getHalte().clear();
            }
        }
    }

    /** Bestände aus älteren Dateien (mit Bruchteilen) auf volle Einheiten runden. */
    static void ganzeEinheiten(Anlage a) {
        java.util.function.Consumer<Map<String, Double>> runde = m -> {
            m.replaceAll((k, v) -> (double) Math.round(v));
            m.values().removeIf(v -> v <= 0);
        };
        for (Betrieb b : a.betriebe()) {
            runde.accept(b.getBestandEin());
            runde.accept(b.getBestandAus());
        }
        for (Lager l : a.lager()) {
            runde.accept(l.getBestand());
        }
        for (Station s : a.stationen()) {
            for (Gleis g : s.getGleise()) {
                runde.accept(g.getAusgang());
                runde.accept(g.getEingang());
            }
        }
        for (ZugZustand z : a.getZuege().values()) {
            for (Wagenladung w : z.getWagenladungen()) {
                runde.accept(w.getInhalt());
            }
            for (double[] p : z.getPassagiere().values()) {
                p[0] = Math.round(p[0]);
                p[1] = Math.round(p[1]);
            }
            z.getPassagiere().values().removeIf(p -> p[0] + p[1] <= 0);
        }
        for (Station s : a.stationen()) {
            for (Gleis g : s.getGleise()) {
                for (double[] w : g.getWartende().values()) {
                    w[0] = Math.round(w[0]);
                    w[1] = Math.round(w[1]);
                }
            }
        }
    }

    public Anlage getAnlage() {
        return anlage;
    }

    public int getTag() {
        return tag;
    }

    public List<Ereignis> getEreignisse() {
        return new ArrayList<>(ereignisse);
    }

    // ------------------------------------------------------------------ Zeit

    /**
     * @param simSekundenGesamt fortlaufende Modellsekunden seit Programmstart (LaufendeUhr)
     * @param modellTag         laufender Modelltag (fuer Statistik)
     * @param modellStunde      laufende Modellstunde (Tag * 24 + Stunde), fuer "Ankommend"
     * @param zeitText          aktuelle Modellzeit fuer das Ereignisprotokoll
     */
    public void tick(double simSekundenGesamt, int modellTag, int modellStunde, String zeitText) {
        this.tag = modellTag;
        this.stunde = modellStunde;
        this.modellZeitText = zeitText == null ? "" : zeitText;
        if (anlage == null) {
            return;
        }
        if (angehalten) {
            // Angehalten: die Modellzeit läuft weiter, wird aber nicht nachgeholt.
            letzteSimSekunden = simSekundenGesamt;
            return;
        }
        if (letzteSimSekunden < 0 || simSekundenGesamt < letzteSimSekunden) {
            letzteSimSekunden = simSekundenGesamt;
            return;
        }
        double delta = Math.min(simSekundenGesamt - letzteSimSekunden, MAX_NACHHOLEN);
        letzteSimSekunden = simSekundenGesamt;
        while (delta > EPS) {
            double dt = Math.min(60, delta);
            schritt(dt);
            delta -= dt;
        }
    }

    /** Seit dem Start gerechnete Modellsekunden (für Wartezeiten wie die 2 Stunden im Lager). */
    private double laufzeit;

    /** Ein Rechenschritt ueber {@code dt} Modellsekunden. */
    public void schritt(double dt) {
        laufzeit += dt;
        haelteNachholen();
        for (Station s : anlage.stationen()) {
            for (Gleis g : s.getGleise()) {
                g.getAktivLaden().clear();
                g.getAktivEntladen().clear();
            }
        }
        for (ZugZustand z : anlage.getZuege().values()) {
            if (z.isHaltend()) {
                for (ZugZustand.Halt h : z.getHalte()) {
                    umladen(z, h, dt);
                }
            }
        }
        for (Betrieb b : anlage.betriebe()) {
            produziere(b, dt);
        }
        for (Station s : anlage.stationen()) {
            versorgeOrtsnah(s, dt);
        }
        for (Betrieb b : anlage.betriebe()) {
            gibAusgangAnGleise(b);
        }
        for (Uebergabe u : anlage.uebergaben()) {
            fuelleAngebot(u);
        }
        for (Station s : anlage.stationen()) {
            if (anlage.istAussenwelt(s)) {
                fuelleSchattenbahnhof(s, dt);
            }
        }
        for (Lager l : anlage.lager()) {
            bieteLagerAn(l);
        }
        for (Station s : anlage.stationen()) {
            for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                verteileEingang(s, g);
            }
        }
        for (Ort o : anlage.orte()) {
            aktualisiereOrt(o, dt);
        }
        bewerten(dt);
    }

    /** Statistik-Reihen der Zufriedenheit/Erfüllung (Anwender 11.11.2026): Tagesmittel = Σ / t. */
    public static final String BEWERTUNG_SUMME = "Bewertung Σ";
    public static final String BEWERTUNG_ZEIT = "Bewertung t";

    private void bewerten(double dt) {
        int t = getTag();
        for (Ort o : anlage.orte()) {
            Bewertung.Ergebnis e = Bewertung.ort(anlage, o);
            if (!e.leer()) {
                o.getStatistik().addiere(BEWERTUNG_SUMME, t, e.prozent() * dt);
                o.getStatistik().addiere(BEWERTUNG_ZEIT, t, dt);
            }
        }
        for (Betrieb b : anlage.betriebe()) {
            Bewertung.Ergebnis e = Bewertung.betrieb(anlage, b);
            if (!e.leer()) {
                b.getStatistik().addiere(BEWERTUNG_SUMME, t, e.prozent() * dt);
                b.getStatistik().addiere(BEWERTUNG_ZEIT, t, dt);
            }
        }
    }

    /** Tagesmittel der Bewertung (0..100) oder -1, wenn an dem Tag nichts gezählt wurde. */
    public static double bewertungAmTag(Statistik st, int tag) {
        double z = st.wert(BEWERTUNG_ZEIT, tag);
        return z <= 0 ? -1 : st.wert(BEWERTUNG_SUMME, tag) / z;
    }

    // ------------------------------------------------------------------ Produktion

    private void produziere(Betrieb b, double dt) {
        if (b.getRezepte().isEmpty()) {
            b.setZustand("Kein Rezept");
            return;
        }
        if (anlage.virtuellOhneStation(b)) {
            b.setZustand("Liefert immer (nicht auf der Anlage)");
            return;
        }
        if (b.getRateProStunde() <= 0) {
            b.setZustand("Angehalten (Rate 0)");
            return;
        }
        double faktor = anlage.faktor(b);
        if (faktor <= 0) {
            b.setZustand("Pause");
            return;
        }
        if ("Pause".equals(b.getZustand())) {
            b.setZustand("");
        }
        passeAn(b, faktor * dt);
        if (b.isDynamisch()) {
            faktor *= schwankung(b);
        }
        b.setFortschritt(b.getFortschritt() + b.getRateProStunde() * b.stufenFaktor() * faktor * dt / 3600.0);
        boolean gelaufen = false;
        while (b.getFortschritt() >= 1) {
            Rezept r = ausfuehrbaresRezept(b);
            if (r == null) {
                b.setFortschritt(1);
                b.setZustand(grundDesStillstands(b));
                return;
            }
            double verbraucht = 0;
            for (Map.Entry<String, Integer> e : r.getEingang().entrySet()) {
                if (!anlage.immerGeliefert(b, e.getKey())) {
                    b.getBestandEin().merge(e.getKey(), (double) -e.getValue(), Double::sum);
                }
                verbraucht += e.getValue();
            }
            double produziert = 0;
            for (Map.Entry<String, Integer> e : r.getAusgang().entrySet()) {
                // Lieferung in Schüben: erst sammeln, abgegeben wird alle x Stunden auf einmal
                (b.getLieferAlleStunden() > 0 ? b.getSchub() : b.getBestandAus())
                        .merge(e.getKey(), (double) e.getValue(), Double::sum);
                produziert += e.getValue();
            }
            b.getStatistik().addiere("Verbraucht", tag, verbraucht);
            b.getStatistik().addiere("Produziert", tag, produziert);
            b.setFortschritt(b.getFortschritt() - 1);
            gelaufen = true;
        }
        if (b.getLieferAlleStunden() > 0) {
            schubAbgeben(b, dt);
        }
        if (gelaufen || b.getZustand().isEmpty() || !b.getZustand().startsWith("Läuft")) {
            Rezept r = ausfuehrbaresRezept(b);
            b.setZustand(r != null ? "Läuft" : grundDesStillstands(b));
        }
    }

    /**
     * „Produktion passt sich an“ (Anwender 10.11.2026): je Modellstunde – gewichtet mit dem Regler
     * Langsamer/Schneller – höchstens eine Stufe. Hoch, wenn jedes Eingangslager zu mindestens 80 % gefüllt ist und
     * kein Ausgangslager über die Hälfte belegt ist (der Ausgang fließt ab); herunter, wenn keine Ausführung möglich
     * ist (Eingang fehlt oder Ausgang voll).
     */
    private void passeAn(Betrieb b, double effektiv) {
        if (!b.isAnpassen()) {
            return;
        }
        b.setSeitAnpassung(b.getSeitAnpassung() + effektiv);
        if (b.getSeitAnpassung() < 3600) {
            return;
        }
        b.setSeitAnpassung(b.getSeitAnpassung() - 3600);
        boolean einVoll = true;
        boolean ausFrei = true;
        for (Rezept r : b.getRezepte()) {
            for (String g : r.getEingang().keySet()) {
                if (!anlage.immerGeliefert(b, g)
                        && b.getBestandEin().getOrDefault(g, 0.0) < 0.8 * b.kapazitaetEin(g) - EPS) {
                    einVoll = false;
                }
            }
            for (String g : r.getAusgang().keySet()) {
                double kap = b.kapazitaetAus(g);
                if (kap < Double.MAX_VALUE && b.getBestandAus().getOrDefault(g, 0.0) > 0.5 * kap + EPS) {
                    ausFrei = false;
                }
            }
        }
        int alt = b.getStufe();
        if (einVoll && ausFrei && alt < Betrieb.MAX_STUFE) {
            b.setStufe(alt + 1);
        } else if (ausfuehrbaresRezept(b) == null && alt > 0) {
            b.setStufe(alt - 1);
        }
        if (b.getStufe() != alt) {
            protokolliere(b.getAnzeigeName() + ": Produktion ×" + b.stufenFaktor()
                    + (b.getStufe() > alt ? " (erhöht)" : " (gesenkt)"));
        }
    }

    /**
     * Lieferung in Schüben (Anwender 27.10.2026): alle {@link Betrieb#getLieferAlleStunden()} Modellstunden geht das
     * Gesammelte auf einmal ins Ausgangslager (von dort wie gewohnt ans Ladegleis bzw. ins Lager).
     */
    private void schubAbgeben(Betrieb b, double dt) {
        double takt = b.getLieferAlleStunden() * 3600.0;
        b.setSeitLieferung(b.getSeitLieferung() + dt);
        if (b.getSeitLieferung() + EPS < takt) {
            return;
        }
        b.setSeitLieferung(b.getSeitLieferung() - takt);
        b.getSchub().forEach((g, m) -> b.getBestandAus().merge(g, m, Double::sum));
        b.getSchub().clear();
    }

    /**
     * Schwankung eines dynamischen Betriebs: je Modelltag und -stunde ein fester Wert zwischen 0,5 und 1,5
     * (aus Betrieb, Tag und Stunde abgeleitet, also wiederholbar), im Mittel 1.
     */
    private double schwankung(Betrieb b) {
        long saat = b.getId().hashCode() * 31L + tag * 24L + stunde;
        java.util.Random r = new java.util.Random(saat);
        return 0.5 + r.nextDouble();
    }

    /** Bestand eines Eingangsguts; unendlich, wenn es immer geliefert wird. */
    private double vorrat(Betrieb b, String gutId) {
        return anlage.immerGeliefert(b, gutId) ? Double.POSITIVE_INFINITY : b.getBestandEin().getOrDefault(gutId, 0.0);
    }

    /** Freier Platz im Eingangslager; 0 für Güter, die immer geliefert werden. */
    private double frei(Betrieb b, String gutId) {
        return anlage.immerGeliefert(b, gutId) ? 0 : b.freiEin(gutId);
    }

    private Rezept ausfuehrbaresRezept(Betrieb b) {
        for (Rezept r : b.getRezepte()) {
            boolean eingangOk = true;
            for (Map.Entry<String, Integer> e : r.getEingang().entrySet()) {
                if (vorrat(b, e.getKey()) + EPS < e.getValue()) {
                    eingangOk = false;
                    break;
                }
            }
            if (!eingangOk) {
                continue;
            }
            boolean platzOk = true;
            for (Map.Entry<String, Integer> e : r.getAusgang().entrySet()) {
                double kap = b.kapazitaetAus(e.getKey());
                double belegt = b.getBestandAus().getOrDefault(e.getKey(), 0.0)
                        + b.getSchub().getOrDefault(e.getKey(), 0.0);
                if (belegt + e.getValue() > kap + EPS) {
                    platzOk = false;
                    break;
                }
            }
            if (platzOk) {
                return r;
            }
        }
        return null;
    }

    private String grundDesStillstands(Betrieb b) {
        Rezept r = b.getRezepte().get(0);
        List<String> fehlend = new ArrayList<>();
        for (Rezept kandidat : b.getRezepte()) {
            for (Map.Entry<String, Integer> e : kandidat.getEingang().entrySet()) {
                if (vorrat(b, e.getKey()) + EPS < e.getValue()) {
                    String n = anlage.gutName(e.getKey());
                    if (!fehlend.contains(n)) {
                        fehlend.add(n);
                    }
                }
            }
        }
        boolean eingangIrgendwoOk = false;
        for (Rezept kandidat : b.getRezepte()) {
            boolean ok = kandidat.getEingang().entrySet().stream()
                    .allMatch(e -> vorrat(b, e.getKey()) + EPS >= e.getValue());
            if (ok) {
                eingangIrgendwoOk = true;
                r = kandidat;
                break;
            }
        }
        if (!eingangIrgendwoOk) {
            return "Wartet auf Eingang: " + String.join(", ", fehlend);
        }
        for (Map.Entry<String, Integer> e : r.getAusgang().entrySet()) {
            double kap = b.kapazitaetAus(e.getKey());
            if (b.getBestandAus().getOrDefault(e.getKey(), 0.0) + e.getValue() > kap + EPS) {
                return "Ausgangslager voll: " + anlage.gutName(e.getKey());
            }
        }
        return "Wartet";
    }

    // ------------------------------------------------------------------ Gueterfluss

    /** Ladegleise der Stationen, die das Objekt bedienen, an denen {@code gutId} geladen werden darf. */
    private List<Anlage.GleisTreffer> ladeGleiseFuer(AnlagenObjekt o, String gutId) {
        List<Anlage.GleisTreffer> l = new ArrayList<>();
        for (Station s : anlage.stationenFuer(o.getId())) {
            for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                if (g.isLaden() && g.erlaubt(gutId)) {
                    l.add(new Anlage.GleisTreffer(s, g));
                }
            }
        }
        return l;
    }

    /**
     * Platz für ein Gut an einem Ladegleis: die im Gleisdialog eingetragene Kapazität (je Gut, mindestens
     * {@link Gleis#MIN_KAPAZITAET_LADEGLEIS}); mehr Vorrat nur über ein Lager (Anwender 12.10.2026).
     */
    private double platzAmGleis(Station s, Gleis g, String gutId) {
        return Math.max(0, g.getKapazitaet() - g.getAusgang().getOrDefault(gutId, 0.0));
    }

    private void gibAusgangAnGleise(Betrieb b) {
        for (Map.Entry<String, Double> e : b.getBestandAus().entrySet()) {
            double rest = e.getValue();
            if (rest <= EPS) {
                continue;
            }
            double vorher = rest;
            // Gleichmäßig auf alle angeschlossenen Ladegleise verteilen, Einheit für Einheit reihum (Anwender
            // 27.10.2026: ein Betrieb an zwei Bahnhöfen belieferte bisher nur den ersten, bis dessen Gleis voll war).
            // Reihum je Bahnhof (jeder Bahnhof gleich viel), innerhalb des Bahnhofs reihum über seine Gleise.
            Map<Station, List<Anlage.GleisTreffer>> jeStation = new java.util.LinkedHashMap<>();
            for (Anlage.GleisTreffer t : ladeGleiseFuer(b, e.getKey())) {
                jeStation.computeIfAbsent(t.station(), k -> new ArrayList<>()).add(t);
            }
            Map<Station, Integer> naechstes = new java.util.HashMap<>();
            Map<Anlage.GleisTreffer, Double> gegeben = new java.util.LinkedHashMap<>();
            boolean weiter = true;
            while (rest >= 1 - EPS && weiter) {
                weiter = false;
                for (Map.Entry<Station, List<Anlage.GleisTreffer>> st : jeStation.entrySet()) {
                    if (rest < 1 - EPS) {
                        break;
                    }
                    List<Anlage.GleisTreffer> gl = st.getValue();
                    int start = naechstes.getOrDefault(st.getKey(), 0);
                    for (int i = 0; i < gl.size(); i++) {
                        Anlage.GleisTreffer t = gl.get((start + i) % gl.size());
                        double schon = gegeben.getOrDefault(t, 0.0);
                        if (platzAmGleis(t.station(), t.gleis(), e.getKey()) - schon >= 1 - EPS) {
                            gegeben.put(t, schon + 1);
                            rest -= 1;
                            weiter = true;
                            naechstes.put(st.getKey(), (start + i + 1) % gl.size());
                            break;
                        }
                    }
                }
            }
            for (Map.Entry<Anlage.GleisTreffer, Double> gt : gegeben.entrySet()) {
                gt.getKey().gleis().getAusgang().merge(e.getKey(), gt.getValue(), Double::sum);
                fluss(b, gt.getKey().station(), e.getKey(), gt.getValue());
            }
            // Gleise voll: der Rest geht in ein angeschlossenes Lager (Anwender 31.10.2026: Lager stärker einbinden,
            // die Bahnhöfe halten nur wenig vor). Das Lager stellt es wieder ans Gleis, sobald dort Platz ist.
            Gut gut = anlage.gut(e.getKey());
            for (Station s : anlage.stationenFuer(b.getId())) {
                for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                    if (rest < 1 - EPS) {
                        break;
                    }
                    if (o instanceof Lager l && l.nimmt(gut)) {
                        double m = ganz(Math.min(rest, l.frei()));
                        if (m > EPS) {
                            l.getBestand().merge(e.getKey(), m, Double::sum);
                            l.getStatistik().addiere("Eingehend", tag, m);
                            fluss(b, s, e.getKey(), m);
                            fluss(s, l, e.getKey(), m);
                            rest -= m;
                        }
                    }
                }
            }
            e.setValue(rest);
            b.getStatistik().addiere("Versand", tag, vorher - rest);
        }
    }

    private void fuelleAngebot(Uebergabe u) {
        double faktor = Math.min(1, anlage.faktor(u));
        if (faktor <= 0) {
            return;
        }
        for (String gutId : u.getAngebot()) {
            for (Anlage.GleisTreffer t : ladeGleiseFuer(u, gutId)) {
                Gleis g = t.gleis();
                double frei = ganz(platzAmGleis(t.station(), g, gutId));
                double platz = frei >= 1 ? Math.max(1, ganz(frei * faktor)) : 0;
                if (platz > EPS) {
                    g.getAusgang().merge(gutId, platz, Double::sum);
                    u.getStatistik().addiere("Geliefert", tag, platz);
                    fluss(u, t.station(), gutId, platz);
                }
            }
        }
    }

    /**
     * Schattenbahnhof = Außenwelt: Ladegleise halten die gewählten Güter bis zur Kapazität bereit,
     * Bahnsteige halten wartende Personen bereit (10 % 1. Klasse); abgeladene Güter verschwinden.
     */
    private void fuelleSchattenbahnhof(Station s, double dt) {
        double faktor = anlage.faktor(s);
        if (faktor <= 0) {
            return;
        }
        double bereit = 0;
        for (Gleis g : s.getGleise()) {
            if (g.getArt() == GleisArt.LADEGLEIS) {
                if (!g.isLaden()) {
                    continue;
                }
                // Je Gut die eingestellte Menge je Modellstunde, höchstens bis 10 je Gut an der Station.
                for (Map.Entry<String, Double> e : g.getAngebot().entrySet()) {
                    double platz = ganz(platzAmGleis(s, g, e.getKey()));
                    String schluessel = g.getId() + "|" + e.getKey();
                    // Bruchteile sammeln (höchstens eine Modellstunde), bewegt werden nur volle Einheiten
                    double guthaben = Math.min(Math.max(1, e.getValue()),
                            angebotsRest.getOrDefault(schluessel, 0.0) + e.getValue() * faktor * dt / 3600.0);
                    double m = ganz(Math.min(platz, guthaben));
                    angebotsRest.put(schluessel, guthaben - m);
                    if (m > EPS) {
                        g.getAusgang().merge(e.getKey(), m, Double::sum);
                        bereit += m;
                    }
                }
            } else if (anlage.reisendeVonAussen(s) && g.isLaden()) {
                // Post/Expressgut von außerhalb (Anwender 27.10.2026), ganze Stück bis zur Gleiskapazität
                for (String gutId : List.of(Tf3Daten.POST, Tf3Daten.EXPRESSGUT)) {
                    double rate = g.erzeugt(gutId);
                    if (rate <= 0 || anlage.gut(gutId) == null) {
                        continue;
                    }
                    String schluessel = g.getId() + "|" + gutId;
                    double guthaben = Math.min(Math.max(1, rate),
                            angebotsRest.getOrDefault(schluessel, 0.0) + rate * faktor * dt / 3600.0);
                    double platz = Math.max(Tf3Daten.POST_KAPAZITAET, g.getKapazitaet())
                            - g.getAusgang().getOrDefault(gutId, 0.0);
                    double m = ganz(Math.max(0, Math.min(platz, guthaben)));
                    angebotsRest.put(schluessel, guthaben - m);
                    if (m > EPS) {
                        g.getAusgang().merge(gutId, m, Double::sum);
                        bereit += m;
                    }
                }
            }
            if (g.getArt() == GleisArt.BAHNSTEIG && g.getPassagiereProStunde() > 0 && anlage.reisendeVonAussen(s)) {
                List<String> schlangen = g.warteschlangen();
                double jeSchlange = g.getPassagiereProStunde() * faktor * dt / 3600.0 / Math.max(1, schlangen.size());
                for (String r : schlangen) {
                    bereit += stellePersonenEin(g, r, g.wartendeIn(r), jeSchlange, g.getAnteilErsteKlasse());
                }
            }
        }
        s.getStatistik().addiere("Bereitgestellt", tag, bereit);
    }

    private void bieteLagerAn(Lager l) {
        if (anlage.faktor(l) <= 0) {
            return;
        }
        l.getUnverbrauchbar().clear();
        // "zurück, bitte abholen" bleibt stehen, bis das Gut auch am Gleis abgeholt ist
        l.getZurueck().removeIf(gutId -> l.getBestand().getOrDefault(gutId, 0.0) <= EPS
                && ladeGleiseFuer(l, gutId).stream().mapToDouble(t -> t.gleis().getAusgang().getOrDefault(gutId, 0.0))
                .sum() <= EPS);
        for (Map.Entry<String, Double> e : l.getBestand().entrySet()) {
            double rest = e.getValue();
            if (rest <= EPS) {
                l.getUnverbrauchbarSeit().remove(e.getKey());
                l.getBereitgestellt().remove(e.getKey());
                continue;
            }
            // Für einen Verbraucher am Lager eingelagert: nicht zur Abholung anbieten. Hat das Gut weder
            // Verbraucher noch Erzeuger am Lager, wartet es 2 Modellstunden und geht dann zur Abholung zurück
            // (Anwender 12.10.2026).
            if (lagerHatVerbraucher(l, e.getKey())) {
                l.getUnverbrauchbarSeit().remove(e.getKey());
                continue;
            }
            if (!lagerHatErzeuger(l, e.getKey()) && !l.getBereitgestellt().contains(e.getKey())) {
                double seit = l.getUnverbrauchbarSeit().computeIfAbsent(e.getKey(), k -> laufzeit);
                if (laufzeit - seit < 2 * 3600) {
                    l.getUnverbrauchbar().add(e.getKey());
                    continue;
                }
                l.getZurueck().add(e.getKey());
            }
            double vorher = rest;
            for (Anlage.GleisTreffer t : ladeGleiseFuer(l, e.getKey())) {
                Gleis g = t.gleis();
                // Nur so viel ans Gleis, wie dort gerade fehlt; das Lager bleibt der Puffer.
                double platz = platzAmGleis(t.station(), g, e.getKey());
                double m = ganz(Math.min(rest, platz));
                if (m > EPS) {
                    g.getAusgang().merge(e.getKey(), m, Double::sum);
                    rest -= m;
                    fluss(l, t.station(), e.getKey(), m);
                }
            }
            e.setValue(rest);
            l.getStatistik().addiere("Ausgehend", tag, vorher - rest);
        }
        l.getBestand().values().removeIf(v -> v <= EPS);
    }

    /**
     * Versorgung ohne Zug innerhalb eines Einzugsbereichs (+Cargo-Programmbeschreibung 2.5:
     * "Es werden keine Zuege benoetigt, wenn am selben Ort Gueter produziert und sofort wieder
     * verbraucht werden."): Betriebe und Orte einer Station bekommen ihre Eingaenge direkt aus
     * Ausgangslagern anderer Betriebe, aus Lagern, aus Uebergaben und aus Guetern, die an den
     * Ladegleisen dieser Station auf Abholung warten.
     */
    private void versorgeOrtsnah(Station s, double dt) {
        // Güter nur über Stationen mit Ladegleisen (ein Bahnhof mit nur Bahnsteigen bringt Personen).
        if (s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty()) {
            return;
        }
        List<AnlagenObjekt> bereich = anlage.einzugsbereich(s);
        for (AnlagenObjekt verbraucher : bereich) {
            if (verbraucher instanceof Betrieb b) {
                for (String gutId : b.eingangsGueter()) {
                    double bedarf = frei(b, gutId);
                    double m = entnehmeOrtsnah(s, bereich, verbraucher, gutId, bedarf);
                    if (m > EPS) {
                        fluss(s, b, gutId, m);
                        b.getBestandEin().merge(gutId, m, Double::sum);
                        b.getStatistik().addiere("Angeliefert", tag, m);
                    }
                }
            } else if (verbraucher instanceof Ort ort) {
                for (Map.Entry<String, Double> e : ort.getOffenerBedarf().entrySet()) {
                    // Aus Lager, Betrieben und Gleisen sofort bis zur Aufnahmegrenze (offener Bedarf),
                    // Anwender 12.10.2026 (vorher im Takt des Bedarfs, das war zu langsam).
                    double m = entnehmeOrtsnah(s, bereich, verbraucher, e.getKey(), e.getValue());
                    if (m > EPS) {
                        fluss(s, ort, e.getKey(), m);
                        e.setValue(e.getValue() - m);
                        ort.getStatistik().addiere("Versorgung", tag, m);
                    }
                }
            }
        }
    }

    private double entnehmeOrtsnah(Station s, List<AnlagenObjekt> bereich, AnlagenObjekt verbraucher, String gutId,
            double menge) {
        menge = ganz(menge);
        double rest = menge;
        for (AnlagenObjekt quelle : bereich) {
            if (rest <= EPS) {
                break;
            }
            if (quelle == verbraucher) {
                continue;
            }
            if (quelle instanceof Betrieb b) {
                double vorhanden = b.getBestandAus().getOrDefault(gutId, 0.0);
                double m = ganz(Math.min(rest, vorhanden));
                if (m > EPS) {
                    b.getBestandAus().put(gutId, vorhanden - m);
                    b.getStatistik().addiere("Versand", tag, m);
                    rest -= m;
                    fluss(b, s, gutId, m);
                }
            } else if (quelle instanceof Lager l) {
                double vorhanden = l.getBestand().getOrDefault(gutId, 0.0);
                double m = ganz(Math.min(rest, vorhanden));
                if (m > EPS) {
                    l.getBestand().put(gutId, vorhanden - m);
                    l.getStatistik().addiere("Ausgehend", tag, m);
                    rest -= m;
                    fluss(l, s, gutId, m);
                }
            } else if (quelle instanceof Uebergabe u && u.getAngebot().contains(gutId) && anlage.faktor(u) > 0) {
                u.getStatistik().addiere("Geliefert", tag, rest);
                fluss(u, s, gutId, rest);
                rest = 0;
            }
        }
        for (Gleis g : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
            if (rest <= EPS) {
                break;
            }
            double vorhanden = g.getAusgang().getOrDefault(gutId, 0.0);
            double m = ganz(Math.min(rest, vorhanden));
            if (m > EPS) {
                g.getAusgang().put(gutId, vorhanden - m);
                rest -= m;
            }
        }
        return menge - rest;
    }

    /** Wie viel die Abnehmer einer Station von einem Gut noch aufnehmen koennen (unendlich bei Uebergabe). */
    public double aufnahme(Station s, String gutId) {
        if (anlage.istAussenwelt(s)) {
            return Double.POSITIVE_INFINITY;
        }
        Gut gut = anlage.gut(gutId);
        double summe = 0;
        for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
            if (o instanceof Betrieb b) {
                summe += frei(b, gutId);
            } else if (o instanceof Ort ort) {
                summe += ort.getOffenerBedarf().getOrDefault(gutId, 0.0);
            } else if (o instanceof Lager l && l.nimmt(gut)) {
                summe += l.frei();
            } else if (o instanceof Uebergabe u && u.nimmt(gutId)) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return summe;
    }

    /** Hat die Station ueberhaupt einen Abnehmer fuer das Gut (unabhaengig von freiem Platz)? */
    public boolean hatAbnehmer(Station s, String gutId) {
        if (anlage.istAussenwelt(s)) {
            return true;
        }
        Gut gut = anlage.gut(gutId);
        for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
            if (o instanceof Betrieb b && b.getLagerEin().containsKey(gutId) && !anlage.immerGeliefert(b, gutId)) {
                return true;
            }
            if (o instanceof Ort ort && ort.getBedarf().getOrDefault(gutId, 0.0) > 0) {
                return true;
            }
            if (o instanceof Lager l && l.nimmt(gut)) {
                return true;
            }
            if (o instanceof Uebergabe u && u.nimmt(gutId)) {
                return true;
            }
        }
        return false;
    }

    /** Braucht ein Ort oder Betrieb an einer Station dieses Lagers das Gut? */
    private boolean lagerHatVerbraucher(Lager l, String gutId) {
        for (Station s : anlage.stationenFuer(l.getId())) {
            for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                if (o instanceof Ort ort && ort.getBedarf().getOrDefault(gutId, 0.0) > 0) {
                    return true;
                }
                if (o instanceof Betrieb b && !b.isVirtuell() && b.eingangsGueter().contains(gutId)
                        && !anlage.immerGeliefert(b, gutId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Erzeugt ein Betrieb oder liefert eine Übergabe an einer Station dieses Lagers das Gut (Pufferlager)? */
    private boolean lagerHatErzeuger(Lager l, String gutId) {
        for (Station s : anlage.stationenFuer(l.getId())) {
            for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                if (o instanceof Betrieb b && b.ausgangsGueter().contains(gutId)) {
                    return true;
                }
                if (o instanceof Uebergabe u && u.getAngebot().contains(gutId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Erster Ort im Einzugsbereich, der das Gut braucht, sonst null. */
    private Ort ortMitBedarf(Station s, String gutId) {
        for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
            if (o instanceof Ort ort && ort.getBedarf().getOrDefault(gutId, 0.0) > 0) {
                return ort;
            }
        }
        return null;
    }

    /**
     * Abgeladene Güter sofort verbringen (Anwender 12.10.2026): ist ein Lager angeschlossen, das das Gut nimmt,
     * zuerst dorthin (es gibt im Takt des Bedarfs an die Stadt ab); sonst bzw. danach an Betriebe und den offenen
     * Bedarf der Orte, dann an Übergaben. Was eine Stadt braucht, aber niemand mehr aufnehmen kann, wird vernichtet
     * und bei der Stadt angezeigt.
     */
    private void verteileEingang(Station s, Gleis g) {
        if (anlage.istAussenwelt(s)) {
            // „Transport über Schattenbahnhof“: diese Güter warten am Gleis auf den Weitertransport
            // (Anwender 31.10.2026), alles andere verlässt die Anlage.
            double summe = 0;
            for (Map.Entry<String, Double> e : g.getEingang().entrySet()) {
                // angeschlossene Betriebe zuerst (Anwender 11.11.2026), der Rest verlässt die Anlage
                for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                    if (o instanceof Betrieb b && e.getValue() > EPS) {
                        double m = ganz(Math.min(e.getValue(), frei(b, e.getKey())));
                        if (m > EPS) {
                            fluss(s, b, e.getKey(), m);
                            b.getBestandEin().merge(e.getKey(), m, Double::sum);
                            b.getStatistik().addiere("Angeliefert", tag, m);
                            e.setValue(e.getValue() - m);
                        }
                    }
                }
                if (s.getUmlagern().contains(e.getKey())) {
                    g.getAusgang().merge(e.getKey(), e.getValue(), Double::sum);
                    s.getStatistik().addiere("Umgelagert", tag, e.getValue());
                } else {
                    summe += e.getValue();
                }
            }
            s.getStatistik().addiere("Abgenommen", tag, summe);
            g.getEingang().clear();
            return;
        }
        for (Map.Entry<String, Double> e : g.getEingang().entrySet()) {
            String gutId = e.getKey();
            Gut gut = anlage.gut(gutId);
            double rest = e.getValue();
            for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                if (rest > EPS && o instanceof Lager l && l.nimmt(gut)) {
                    double m = ganz(Math.min(rest, l.frei()));
                    if (m > EPS) {
                        l.getBestand().merge(gutId, m, Double::sum);
                        l.getStatistik().addiere("Eingehend", tag, m);
                        rest -= m;
                        fluss(s, l, gutId, m);
                    }
                }
            }
            for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                if (rest <= EPS) {
                    break;
                }
                double m = 0;
                if (o instanceof Betrieb b) {
                    m = ganz(Math.min(rest, frei(b, gutId)));
                    if (m > EPS) {
                        fluss(s, b, gutId, m);
                        b.getBestandEin().merge(gutId, m, Double::sum);
                        b.getStatistik().addiere("Angeliefert", tag, m);
                    }
                } else if (o instanceof Ort ort) {
                    double offen = ort.getOffenerBedarf().getOrDefault(gutId, 0.0);
                    m = ganz(Math.min(rest, offen));
                    if (m > EPS) {
                        fluss(s, ort, gutId, m);
                        ort.getOffenerBedarf().put(gutId, offen - m);
                        ort.getStatistik().addiere("Versorgung", tag, m);
                    }
                }
                rest -= Math.max(0, m);
            }
            for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
                if (rest <= EPS) {
                    break;
                }
                if (o instanceof Uebergabe u && u.nimmt(gutId)) {
                    u.getStatistik().addiere("Abgenommen", tag, rest);
                    fluss(s, u, gutId, rest);
                    rest = 0;
                }
            }
            Ort abnehmer = ortMitBedarf(s, gutId);
            if (rest > EPS && abnehmer != null) {
                abnehmer.vernichte(gutId, rest, tag);
                rest = 0;
            }
            e.setValue(Math.max(0, rest));
        }
        g.getEingang().values().removeIf(v -> v <= EPS);
    }

    // ------------------------------------------------------------------ Orte

    private void aktualisiereOrt(Ort o, double dt) {
        o.pruefeStundenwechsel(stunde);
        postErzeugen(o, dt);
        double faktor = anlage.faktor(o);
        boolean stufePruefen = false;
        if (o.isBedarfWaechst()) {
            o.setSeitAnpassung(o.getSeitAnpassung() + faktor * dt);
            if (o.getSeitAnpassung() >= 3600) {
                o.setSeitAnpassung(o.getSeitAnpassung() - 3600);
                stufePruefen = true;
            }
        }
        for (Map.Entry<String, Double> e : o.getBedarf().entrySet()) {
            double proStunde = o.bedarfAktuell(e.getKey());
            if (proStunde <= 0) {
                continue;
            }
            if (stufePruefen) {
                // Bedarf wächst mit der Versorgung (Anwender 10.11.2026): gedeckt = weniger als eine halbe Stunde
                // offen → eine Stufe hoch; mehr als sechs Stunden offen → eine Stufe herunter.
                double offenJetzt = o.getOffenerBedarf().getOrDefault(e.getKey(), 0.0);
                int alt = o.bedarfStufe(e.getKey());
                if (offenJetzt < 0.5 * proStunde && alt < Ort.MAX_STUFE) {
                    o.setBedarfStufe(e.getKey(), alt + 1);
                } else if (offenJetzt > 6 * proStunde && alt > 0) {
                    o.setBedarfStufe(e.getKey(), alt - 1);
                }
                if (o.bedarfStufe(e.getKey()) != alt) {
                    protokolliere(o.getAnzeigeName() + ": Bedarf " + anlage.gutName(e.getKey()) + " ×"
                            + (1 + o.bedarfStufe(e.getKey())) + (o.bedarfStufe(e.getKey()) > alt ? " (gestiegen)"
                            : " (gesunken)"));
                    proStunde = o.bedarfAktuell(e.getKey());
                }
            }
            double obergrenze = proStunde * 24;
            double zuwachs = proStunde * faktor * dt / 3600.0;
            double offen = o.getOffenerBedarf().getOrDefault(e.getKey(), 0.0) + zuwachs;
            o.getOffenerBedarf().put(e.getKey(), Math.min(obergrenze, offen));
            o.getStatistik().addiere("Nachfrage", tag, zuwachs);
        }
        // Passagiere entstehen und gehen zu den Bahnsteigen der Stationen, die den Ort bedienen.
        List<double[]> schlangen = new ArrayList<>();
        List<String> richtungDerSchlangen = new ArrayList<>();
        List<Gleis> gleiseDerSchlangen = new ArrayList<>();
        List<Double> stationsFaktor = new ArrayList<>();
        List<Station> stationDerSchlangen = new ArrayList<>();
        for (Station s : anlage.stationenFuer(o.getId())) {
            for (Gleis g : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                for (String r : g.warteschlangen()) {
                    schlangen.add(g.wartendeIn(r));
                    richtungDerSchlangen.add(r);
                    gleiseDerSchlangen.add(g);
                    stationsFaktor.add(s.isPausiert() ? 0 : Anlage.tempoFaktor(s.getTempo()));
                    stationDerSchlangen.add(s);
                }
            }
        }
        if (o.getPassagiereProStunde() <= 0) {
            return;
        }
        // Reisende sammeln sich im Ort (höchstens eine Modellstunde) und gehen alle 10 Modellminuten
        // gesammelt zum Bahnhof; erst dort werden sie zu Wartenden (Anwender 27.10.2026).
        o.setReisende(Math.min(Math.max(1, o.getPassagiereProStunde()),
                o.getReisende() + o.getPassagiereProStunde() * faktor * dt / 3600.0));
        o.setSeitGang(o.getSeitGang() + dt);
        if (o.getSeitGang() < Ort.SAMMEL_SEKUNDEN || schlangen.isEmpty()) {
            return;
        }
        o.setSeitGang(0);
        List<Integer> offen = new ArrayList<>();
        for (int i = 0; i < schlangen.size(); i++) {
            if (stationsFaktor.get(i) > 0) {
                offen.add(i);
            }
        }
        int n = (int) Math.floor(o.getReisende() + EPS);
        if (n <= 0 || offen.isEmpty()) {
            return;
        }
        Map<Station, Double> jeStation = new java.util.LinkedHashMap<>();
        int start = gangReihum.getOrDefault(o.getId(), 0);
        double erzeugt = 0;
        for (int k = 0; k < offen.size(); k++) {
            int i = offen.get((start + k) % offen.size());
            int anteil = n / offen.size() + (k < n % offen.size() ? 1 : 0);
            if (anteil <= 0) {
                continue;
            }
            Gleis g = gleiseDerSchlangen.get(i);
            String schl = g.getId() + "|" + richtungDerSchlangen.get(i);
            personenRest.remove(schl);
            double m = stellePersonenEin(g, richtungDerSchlangen.get(i), schlangen.get(i), anteil, o.getAnteilErsteKlasse());
            personenRest.remove(schl);
            if (m > 0) {
                erzeugt += m;
                jeStation.merge(stationDerSchlangen.get(i), m, Double::sum);
            }
        }
        gangReihum.put(o.getId(), (start + 1) % offen.size());
        o.setReisende(o.getReisende() - erzeugt);
        jeStation.forEach((s, m) -> fluss(o, s, null, m));
        o.getStatistik().addiere("Abreisend", tag, erzeugt);
    }

    /** Erste Warteschlange des nächsten Gangs je Ort, damit ein Rest nicht immer an dieselbe geht. */
    private final Map<String, Integer> gangReihum = new java.util.HashMap<>();

    // ------------------------------------------------------------------ Zuege

    /**
     * Verarbeitet ein Zugereignis. Doppelte Ereignisse (Pipe und Protokolldatei) werden
     * ignoriert.
     *
     * @return true, wenn das Ereignis neu war
     */
    public boolean verarbeite(SimulatorTrigger t) {
        if (anlage == null || t == null || t.zug() == null || t.zug().isBlank() || t.aktion() == null) {
            return false;
        }
        String schluessel = t.zeit() + "|" + t.block() + "|" + t.zug() + "|" + t.aktion() + "|" + t.richtung();
        if (!verarbeiteteTrigger.add(schluessel)) {
            return false;
        }
        triggerReihenfolge.addLast(schluessel);
        while (triggerReihenfolge.size() > 5000) {
            verarbeiteteTrigger.remove(triggerReihenfolge.removeFirst());
        }
        ZugZustand z = zug(t.zug());
        switch (t.aktion()) {
            case EINFAHRT -> {
                if (z.isHaltend()) {
                    pruefeUnvollstaendig(z);
                    beendeHalt(z);
                }
                z.setBlock(t.block());
                z.setAbgefahren(false);
                z.setRichtung(t.richtung());
                ermittleLinie(z, t.block());
                zeigeAmGleis(z, t.block());
                durchfahrt(z, t.block());
            }
            case WARTEN -> {
                if (z.isHaltend() && t.block() != null && !t.block().equals(z.getBlock())) {
                    beendeHalt(z);
                }
                z.setBlock(t.block());
                z.setAbgefahren(false);
                if (t.richtung() != null && !t.richtung().isBlank()) {
                    z.setRichtung(t.richtung());
                }
                ermittleLinie(z, t.block());
                zeigeAmGleis(z, t.block());
                List<Anlage.GleisTreffer> treffer = anlage.gleiseFuerBlock(t.block());
                if (!treffer.isEmpty() && !z.isHaltend()) {
                    beginneHalt(z, treffer);
                }
            }
            case ABFAHRT -> {
                if (z.isHaltend()) {
                    pruefeUnvollstaendig(z);
                    beendeHalt(z);
                }
                z.setAbgefahren(true);
                // Der Zug bleibt am Gleis sichtbar, bis er in einen anderen Block einfährt oder gelöscht wird
                // (Anwender 10.11.2026: DB_CAR stand nach dem Ende der Wartezeit weiter in CS_B1, war aber weg).
            }
            case LOESCHEN -> {
                if (z.isHaltend()) {
                    beendeHalt(z);
                }
                zeigeAmGleis(z, null);
                z.setBlock(null);
            }
        }
        return true;
    }

    /**
     * Schon bei der Einfahrt steht der Zug (mit Ladung und Kapazität) an den Gleisen seines Blocks,
     * damit die Anzeige ihn zeigt, bevor er hält; {@code block == null} räumt alle Gleise.
     */
    private void zeigeAmGleis(ZugZustand z, String block) {
        for (Station s : anlage.stationen()) {
            for (Gleis g : s.getGleise()) {
                if (z.getName().equals(g.getZugImGleis()) && (block == null || !block.equals(g.getBlockName()))) {
                    g.setZugImGleis(null);
                }
            }
        }
        if (block != null) {
            for (Anlage.GleisTreffer t : anlage.gleiseFuerBlock(block)) {
                t.gleis().setZugImGleis(z.getName());
            }
        }
    }

    /** Zustand eines Zugs; Wagenladungen und Plaetze werden bei jeder Abfrage mit der Zusammenstellung abgeglichen. */
    public ZugZustand zug(String name) {
        ZugZustand z = anlage.getZuege().computeIfAbsent(name, ZugZustand::new);
        ZugAusstattung a = ausstattung.apply(name);
        z.uebernehme(a != null ? a : ZugAusstattung.LEER, anlage);
        return z;
    }

    private void ermittleLinie(ZugZustand z, String block) {
        if (z.getLinie() != null) {
            Linie bisher = anlage.linie(z.getLinie());
            if (bisher != null && bisher.getBlockNamen().contains(block)) {
                return;
            }
        }
        for (Linie l : anlage.getLinien()) {
            if (l.getBlockNamen().contains(block)) {
                z.setLinie(l.getName());
                return;
            }
        }
    }

    /**
     * Ein Zug hält (WARTEN), aber an keinem Gleis wird umgeladen – etwa weil die Station noch „Anderes“ war. Wird
     * die Art währenddessen geändert, beginnt das Ein-/Aussteigen jetzt, ohne auf das nächste WARTEN zu warten
     * (Anwender 22.10.2026).
     */
    private void haelteNachholen() {
        for (ZugZustand z : anlage.getZuege().values()) {
            if (!z.isHaltend() || !z.getHalte().isEmpty() || z.getBlock() == null) {
                continue;
            }
            List<Anlage.GleisTreffer> treffer = anlage.gleiseFuerBlock(z.getBlock());
            if (treffer.stream().anyMatch(t -> t.station().getArt() != ObjektArt.ANDERES)) {
                beginneHalt(z, treffer);
            }
        }
    }

    /**
     * Schattenbahnhof: schon bei der Durchfahrt wird geladen und entladen, ohne Warten (Anwender 09.11.2026 – nur
     * dort; z. B. Stämme von einem gedachten Holzfällerlager außerhalb an Gleis 2). Der Zug lädt bei der Einfahrt
     * auf einmal, was Gleis, Wagen und Angebot hergeben, Reisende steigen ebenso auf einmal ein und aus. Hält er
     * danach doch, läuft der Halt wie sonst weiter.
     */
    private void durchfahrt(ZugZustand z, String block) {
        if (block == null) {
            return;
        }
        List<Anlage.GleisTreffer> treffer = new ArrayList<>();
        for (Anlage.GleisTreffer t : anlage.gleiseFuerBlock(block)) {
            if (anlage.istAussenwelt(t.station()) && t.gleis().isDurchfahrt()) {
                treffer.add(t); // je Gleis wählbar (Anwender 09.11.2026)
            }
        }
        if (treffer.isEmpty()) {
            return;
        }
        beginneHalt(z, treffer);
        if (z.getHalte().isEmpty()) {
            z.setHaltend(false);
            return;
        }
        // reichlich Zeit auf einmal: alles, was Gleis und Zug hergeben (mehrere Runden für Aus- vor Einsteigen)
        for (int runde = 0; runde < 4; runde++) {
            for (ZugZustand.Halt h : List.copyOf(z.getHalte())) {
                umladen(z, h, 3600);
            }
        }
        protokolliere(z.getName() + " durchfährt " + treffer.get(0).station().getAnzeigeName() + ", "
                + treffer.get(0).gleis().getAnzeigeName() + " (Laden/Entladen bei Durchfahrt)");
        beendeHalt(z);
        zeigeAmGleis(z, block);
    }

    /**
     * Manuelles Umladen (Anwender 10.11.2026): Steht ein Zug an einem Gleis (gelber Rahmen), lädt er auf Wunsch
     * sofort, was Gleis und Zug hergeben – am Bahnsteig Aus- und Einsteigen, am Ladegleis Ent- und Beladen. So
     * lässt sich Ware, die nach dem Halt angeliefert wurde, noch vor der Abfahrt einladen.
     *
     * @return false, wenn der Zug nicht (mehr) an diesem Gleis steht oder dort nicht umgeladen wird
     */
    public boolean manuellUmladen(Gleis g) {
        if (anlage == null || g == null || g.getZugImGleis() == null) {
            return false;
        }
        ZugZustand z = zug(g.getZugImGleis());
        Anlage.GleisTreffer treffer = null;
        for (Anlage.GleisTreffer t : anlage.gleiseFuerBlock(g.getBlockName())) {
            if (t.gleis() == g) {
                treffer = t;
            }
        }
        if (treffer == null) {
            return false;
        }
        boolean lief = z.isHaltend() && z.getHalte().stream().anyMatch(h -> h.gleis() == g);
        if (!lief) {
            if (z.isHaltend()) {
                return false; // hält gerade anderswo
            }
            beginneHalt(z, List.of(treffer));
            if (z.getHalte().isEmpty()) {
                z.setHaltend(false);
                return false;
            }
        }
        for (int runde = 0; runde < 4; runde++) {
            for (ZugZustand.Halt h : List.copyOf(z.getHalte())) {
                if (h.gleis() == g) {
                    umladen(z, h, 3600);
                }
            }
        }
        protokolliere(z.getName() + ": manuell " + (g.getArt() == GleisArt.BAHNSTEIG ? "ein-/ausgestiegen"
                : "ent-/beladen") + " an " + g.getAnzeigeName());
        if (!lief) {
            beendeHalt(z);
            zeigeAmGleis(z, g.getBlockName());
        }
        return true;
    }

    private void beginneHalt(ZugZustand z, List<Anlage.GleisTreffer> alle) {
        z.setHaltend(true);
        z.getHalte().clear();
        z.getEntladenImHalt().clear();
        z.getGeladenImHalt().clear();
        z.getBewegtImHalt().clear();
        z.getAussteigenPlan().clear();
        for (Anlage.GleisTreffer t : alle) {
            if (t.station().getArt() == ObjektArt.ANDERES) {
                continue; // Art noch nicht eingetragen: kein Ein-/Aussteigen, kein Umladen
            }
            beginneHaltAm(z, t);
        }
    }

    private void beginneHaltAm(ZugZustand z, Anlage.GleisTreffer treffer) {
        Gleis g = treffer.gleis();
        Station s = treffer.station();
        String richtung = g.richtungFuer(z.getRichtung());
        z.getHalte().add(new ZugZustand.Halt(s, g, richtung));
        g.setZugImGleis(z.getName());
        g.setLetzteBewegung(0, 0);
        if (g.getArt() == GleisArt.BAHNSTEIG) {
            // Wer schon eine andere Station als seine Einstiegsstation erreicht hat, gilt als "unterwegs
            // gewesen" und darf überall aussteigen, auch wieder an der Einstiegsstation (Pendelverkehr auf
            // der Modellbahn, Anwender 11.10.2026).
            double[] unterwegs = null;
            for (java.util.Iterator<Map.Entry<String, double[]>> it = z.getPassagiere().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, double[]> e = it.next();
                if (!e.getKey().equals(s.getId()) && !e.getKey().equals(ZugZustand.UNTERWEGS)) {
                    if (unterwegs == null) {
                        unterwegs = new double[2];
                    }
                    unterwegs[0] += e.getValue()[0];
                    unterwegs[1] += e.getValue()[1];
                    it.remove();
                }
            }
            if (unterwegs != null) {
                double[] u = z.getPassagiere().computeIfAbsent(ZugZustand.UNTERWEGS, x -> new double[2]);
                u[0] += unterwegs[0];
                u[1] += unterwegs[1];
            }
            double anteil = aussteigeAnteil(s);
            for (Map.Entry<String, double[]> e : z.getPassagiere().entrySet()) {
                if (e.getKey().equals(s.getId()) && !einzigerPersonenbahnhof(s)) {
                    continue; // Post-Prinzip: nicht dort aussteigen, wo eingestiegen
                }
                double[] p = e.getValue();
                // ganze Personen (Anwender 14.10.2026)
                z.getAussteigenPlan().put(e.getKey(), new double[]{Math.round(p[0] * anteil), Math.round(p[1] * anteil)});
            }
        }
        protokolliere(z.getName() + " hält in " + s.getAnzeigeName() + ", " + g.getAnzeigeName()
                + (richtung != null && !richtung.isEmpty() ? " (Richtung " + richtung + ")" : ""));
    }

    /**
     * Rundfahrt (Anwender 27.10.2026): gibt es außer dieser Station keine andere mit Bahnsteigen (auch keinen
     * Schattenbahnhof), dürfen Reisende nach einer Runde wieder am Einstiegsbahnhof aussteigen.
     */
    private boolean einzigerPersonenbahnhof(Station s) {
        for (Station o : anlage.stationen()) {
            if (o != s && o.getArt() != ObjektArt.ANDERES && !o.gleiseDerArt(GleisArt.BAHNSTEIG).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private double aussteigeAnteil(Station s) {
        if (anlage.reisendeVonAussen(s)) {
            return 1.0;
        }
        for (AnlagenObjekt o : anlage.einzugsbereich(s)) {
            if (o instanceof Uebergabe u && u.isPassagiereAussteigen()) {
                return 1.0;
            }
        }
        Ort ort = anlage.ortVon(s);
        return ort != null ? ort.getAussteigeAnteil() : 0.0;
    }

    /** Statistik-Reihen je Fahrtrichtung (Anwender 09.11.2026): Präfix + Richtung (Name der Zielstation). */
    public static final String RICHTUNG_REISENDE = "Eingestiegen Richtung ";
    public static final String RICHTUNG_GUETER = "Geladen Richtung ";

    /** Meldungen für die Statusleiste (Anwender 08.11.2026: „Wartezeit zu kurz …“), abgeholt mit {@link #naechsteMeldung()}. */
    private final java.util.ArrayDeque<String> meldungen = new java.util.ArrayDeque<>();

    /** Nächste Meldung oder null. */
    public String naechsteMeldung() {
        return meldungen.pollFirst();
    }

    /**
     * Bei der Abfahrt: war das Umladen noch nicht fertig, reichte die Wartezeit nicht (Anwender 08.11.2026).
     * Reisende: Aussteigeplan nicht leer, oder es warten noch Reisende in Fahrtrichtung und im Zug sind Plätze frei.
     * Güter: im letzten Schritt wurde noch geladen oder entladen.
     */
    private void pruefeUnvollstaendig(ZugZustand z) {
        boolean reisende = false;
        boolean gueter = false;
        String ort = null;
        for (ZugZustand.Halt h : z.getHalte()) {
            Gleis g = h.gleis();
            if (g.getArt() == GleisArt.BAHNSTEIG) {
                if (!z.hatPlaetzeOderPassagiere()) {
                    continue;
                }
                boolean offen = g.isEntladen() && !z.getAussteigenPlan().isEmpty();
                if (!offen && g.isLaden()) {
                    double[] an = z.passagiereSumme();
                    boolean frei = an[0] < z.getSitzplaetzeErste() - 0.5 || an[1] < z.getSitzplaetzeZweite() - 0.5;
                    double wartend = 0;
                    for (String r : h.richtungAmGleis() != null ? List.of(h.richtungAmGleis()) : g.warteschlangen()) {
                        wartend += g.wartendeSumme(r);
                    }
                    offen = frei && wartend >= 1;
                }
                if (offen) {
                    reisende = true;
                    ort = h.station().getAnzeigeName();
                }
            } else if (!g.getAktivLaden().isEmpty() || !g.getAktivEntladen().isEmpty()) {
                gueter = true;
                ort = h.station().getAnzeigeName();
            }
        }
        if (reisende) {
            melde("„" + z.getName() + "“ in „" + ort
                    + "“: Wartezeit reicht nicht aus, um alle Reisenden einsteigen/aussteigen zu lassen.");
        }
        if (gueter) {
            melde("„" + z.getName() + "“ in „" + ort + "“: Wartezeit zu kurz, um alles zu laden.");
        }
    }

    /** Hinweis von außen (z. B. falsche Einfahrtsseite) ins Ereignisprotokoll und in die Statusleiste. */
    public void hinweis(String text) {
        melde(text);
    }

    private void melde(String text) {
        protokolliere(text);
        meldungen.addLast(text);
        while (meldungen.size() > 20) {
            meldungen.removeFirst();
        }
    }

    private void beendeHalt(ZugZustand z) {
        for (ZugZustand.Halt h : z.getHalte()) {
            Gleis g = h.gleis();
            // Die Anzeige am Gleis bleibt (räumt erst zeigeAmGleis bei neuem Block bzw. LOESCHEN)
            g.getAktivLaden().clear();
            g.getAktivEntladen().clear();
            protokolliere(z.getName() + " fährt ab von " + g.getAnzeigeName()
                    + (g.getArt() == GleisArt.BAHNSTEIG
                    ? " (aus " + rund(g.getLetztAusgestiegen()) + ", ein " + rund(g.getLetztEingestiegen()) + ")"
                    : ""));
        }
        if (z.getStation() != null) {
            z.setLetzteStationId(z.getStation().getId());
        }
        z.setHaltend(false);
        z.getHalte().clear();
        z.getAussteigenPlan().clear();
        z.getEntladenImHalt().clear();
        z.getGeladenImHalt().clear();
        z.getBewegtImHalt().clear();
        umladeGuthaben.keySet().removeIf(k -> k.endsWith("|" + z.getName()));
        z.getWagenladungen().forEach(wagenGuthaben::remove);
    }

    private void umladen(ZugZustand z, ZugZustand.Halt h, double dt) {
        Gleis g = h.gleis();
        Station s = h.station();
        double budget = g.getUmladeRate() * dt / 60.0;
        if (g.getArt() == GleisArt.BAHNSTEIG) {
            umladenPassagiere(z, h, s, g, budget, dt);
            postUmladen(z, s, g);
        } else {
            umladenGueter(z, s, g, budget, dt);
        }
    }

    private void umladenPassagiere(ZugZustand z, ZugZustand.Halt h, Station s, Gleis g, double budgetJeSchritt,
            double dt) {
        // Personen steigen nur ganz ein und aus (Anwender 14.10.2026): Umladezeit als Guthaben sammeln.
        String schluessel = "P|" + g.getId() + "|" + z.getName();
        double budgetGleis = Math.min(budgetJeSchritt + 1, umladeGuthaben.getOrDefault(schluessel, 0.0) + budgetJeSchritt);
        // Wagenzeiten ebenso als Guthaben (je Zug, Aus- und Einsteigen getrennt)
        String ausSchluessel = "PA|" + z.getName();
        String einSchluessel = "PE|" + z.getName();
        double aus = guthaben(ausSchluessel, z.getPersonenJeSekundeAus() * dt);
        double ein = guthaben(einSchluessel, z.getPersonenJeSekundeEin() * dt);
        double[] bewegt = umladenPassagiereGanz(z, h, s, g, budgetGleis, aus, ein);
        umladeGuthaben.put(schluessel, Math.max(0, budgetGleis - bewegt[0] - bewegt[1]));
        if (!Double.isInfinite(aus)) {
            umladeGuthaben.put(ausSchluessel, Math.max(0, aus - bewegt[0]));
        }
        if (!Double.isInfinite(ein)) {
            umladeGuthaben.put(einSchluessel, Math.max(0, ein - bewegt[1]));
        }
    }

    /** Angesammeltes Umlade-Guthaben (höchstens ein Schritt plus eine Einheit); unendlich bleibt unendlich. */
    private double guthaben(String schluessel, double jeSchritt) {
        if (Double.isInfinite(jeSchritt)) {
            return jeSchritt;
        }
        return Math.min(jeSchritt + 1, umladeGuthaben.getOrDefault(schluessel, 0.0) + jeSchritt);
    }

    /** Ein-/Aussteigen ganzer Personen; Rückgabe: {ausgestiegen, eingestiegen}. */
    private double[] umladenPassagiereGanz(ZugZustand z, ZugZustand.Halt h, Station s, Gleis g, double budgetGleis,
            double ausJeSchritt, double einJeSchritt) {
        // Wagenzeiten (Entladen/Beladen > 0) begrenzen zusaetzlich zur Rate des Bahnsteigs.
        double budget = Math.min(budgetGleis, ausJeSchritt);
        // Ankommende Reisende des Orts je Modellstunde begrenzen das Aussteigen (außer "Unbegrenzt")
        Ort zielOrt = anlage.ortVon(s);
        if (zielOrt != null && !anlage.reisendeVonAussen(s)) {
            budget = Math.min(budget, zielOrt.ankommendFrei(stunde));
        }
        boolean aussenSteig = anlage.istAussenwelt(s);
        if (aussenSteig) {
            // Außenwelt: ankommende Personen je Modellstunde an diesem Bahnsteig (Anwender 10.11.2026)
            budget = Math.min(budget, Math.floor(g.abnahmeFrei(null, stunde) + 1e-6));
        }
        if (!z.hatPlaetzeOderPassagiere()) {
            return new double[2]; // Gueterzug am Bahnsteig: nichts
        }
        // 1. Aussteigen (nur wenn am Bahnsteig erlaubt). Reisende mit Koffer steigen nur an Bahnsteigen „Mit Koffer“
        // aus (Anwender 27.10.2026).
        double anBordVorher = z.passagiereSumme()[0] + z.passagiereSumme()[1];
        if (!g.isMitKoffer() && z.getKofferReisende() > EPS) {
            budget = Math.min(budget, Math.max(0, Math.floor(anBordVorher - z.getKofferReisende() + 1e-6)));
        }
        double ausgestiegen = 0;
        for (Map.Entry<String, double[]> e : g.isEntladen() ? z.getAussteigenPlan().entrySet()
                : java.util.Set.<Map.Entry<String, double[]>>of()) {
            double[] plan = e.getValue();
            double[] anBord = z.getPassagiere().get(e.getKey());
            if (anBord == null) {
                continue;
            }
            for (int k = 0; k < 2 && budget > EPS; k++) {
                double m = ganz(Math.min(budget, Math.min(plan[k], anBord[k])));
                if (m > EPS) {
                    plan[k] -= m;
                    anBord[k] -= m;
                    budget -= m;
                    ausgestiegen += m;
                }
            }
        }
        z.getAussteigenPlan().values().removeIf(p -> p[0] + p[1] <= EPS);
        z.getPassagiere().values().removeIf(p -> p[0] + p[1] <= EPS);
        if (ausgestiegen > 0 && g.isMitKoffer() && anBordVorher > EPS) {
            double ka = Math.min(z.getKofferReisende(), ausgestiegen * z.getKofferReisende() / anBordVorher);
            z.setKofferReisende(z.getKofferReisende() - ka);
        }
        if (z.passagiereSumme()[0] + z.passagiereSumme()[1] <= EPS) {
            z.setKofferReisende(0);
        }
        kofferAngleichen(z);
        if (ausgestiegen > 0) {
            Ort ort = anlage.ortVon(s);
            if (ort != null) {
                ort.zaehleAnkommend(ausgestiegen, stunde);
                ort.getStatistik().addiere("Ankommend", tag, ausgestiegen);
                fluss(s, ort, null, ausgestiegen);
            }
            fluss(anlage.infrastruktur(), s, null, ausgestiegen);
            transfer(null, z.getEinstiegPersonen(), s, ausgestiegen, z.getName());
            g.zaehle(null, true, ausgestiegen, tag);
            if (aussenSteig) {
                g.zaehleAbnahme(null, ausgestiegen, stunde);
            }
            s.getStatistik().addiere("Ausgestiegen", tag, ausgestiegen);
        }
        if ((!z.getAussteigenPlan().isEmpty() && g.isEntladen()) || budget <= EPS || !g.isLaden()) {
            g.setLetzteBewegung(g.getLetztEingestiegen(), g.getLetztAusgestiegen() + ausgestiegen);
            return new double[]{ausgestiegen, 0};
        }
        budget = Math.min(budgetGleis - ausgestiegen, einJeSchritt);
        // 2. Einsteigen, getrennt nach Klasse, nur bis zu den freien Plaetzen
        double[] anBordSumme = z.passagiereSumme();
        double[] frei = {Math.max(0, z.getSitzplaetzeErste() - anBordSumme[0]),
                Math.max(0, z.getSitzplaetzeZweite() - anBordSumme[1])};
        List<String> schlangen = h.richtungAmGleis() != null ? List.of(h.richtungAmGleis()) : g.warteschlangen();
        // Mit Koffer: nur so viele Kofferreisende, wie der Zug Koffer aufnehmen kann (Gepäckwagen).
        double kofferHier = 0;
        double wartendHier = 0;
        double kofferErlaubt = 0;
        if (g.isMitKoffer()) {
            double alle = 0;
            for (String r : g.warteschlangen()) {
                alle += g.wartendeSumme(r);
            }
            for (String r : schlangen) {
                wartendHier += g.wartendeSumme(r);
            }
            double k = Math.min(g.getKofferWartend(), alle);
            kofferHier = alle > EPS ? k * wartendHier / alle : 0;
            kofferErlaubt = Math.min(kofferHier, kofferFrei(z)
                    + Math.max(0, kofferGeladen(z) - z.getKofferReisende()));
            budget = Math.min(budget, Math.floor(wartendHier - kofferHier + kofferErlaubt + 1e-6));
        }
        double eingestiegen = 0;
        for (String r : schlangen) {
            double[] w = g.wartendeIn(r);
            for (int k = 0; k < 2; k++) {
                double m = ganz(Math.min(budget, Math.min(frei[k], w[k])));
                if (m > EPS) {
                    w[k] -= m;
                    frei[k] -= m;
                    budget -= m;
                    eingestiegen += m;
                    if (r != null && !r.isEmpty()) {
                        // Statistik je Richtung (Anwender 09.11.2026)
                        s.getStatistik().addiere(RICHTUNG_REISENDE + r, tag, m);
                    }
                    z.getPassagiere().computeIfAbsent(s.getId(), x -> new double[2])[k] += m;
                }
            }
        }
        if (eingestiegen > 0 && g.isMitKoffer() && wartendHier > EPS) {
            double kb = Math.min(kofferErlaubt, eingestiegen * kofferHier / wartendHier);
            kb = Math.max(kb, eingestiegen - (wartendHier - kofferHier));
            kb = Math.max(0, Math.min(kb, kofferHier));
            g.setKofferWartend(g.getKofferWartend() - kb);
            z.setKofferReisende(z.getKofferReisende() + kb);
            kofferAngleichen(z);
        }
        if (eingestiegen > 0) {
            s.getStatistik().addiere("Eingestiegen", tag, eingestiegen);
            fluss(s, anlage.infrastruktur(), null, eingestiegen);
            z.setEinstiegPersonen(s);
            g.zaehle(null, false, eingestiegen, tag);
        }
        g.setLetzteBewegung(g.getLetztEingestiegen() + eingestiegen, g.getLetztAusgestiegen() + ausgestiegen);
        return new double[]{ausgestiegen, eingestiegen};
    }

    private void umladenGueter(ZugZustand z, Station s, Gleis g, double budgetJeSchritt, double dt) {
        if (z.getWagenladungen().isEmpty()) {
            return; // Personenzug am Ladegleis: nichts
        }
        // Umladezeit als Guthaben sammeln; bewegt werden nur volle Einheiten (Anwender 14.10.2026).
        String schluessel = g.getId() + "|" + z.getName();
        double budget = Math.min(budgetJeSchritt + 1, umladeGuthaben.getOrDefault(schluessel, 0.0) + budgetJeSchritt);
        Map<Wagenladung, Double> rest = new java.util.IdentityHashMap<>();
        try {
            umladenGueterGanz(z, s, g, budget, dt, rest);
        } finally {
            umladeGuthaben.put(schluessel, Math.max(0, umladeGuthabenRest));
            rest.forEach(wagenGuthaben::put);
        }
    }

    /** Rest des Gleis-Guthabens nach {@link #umladenGueterGanz}. */
    private double umladeGuthabenRest;

    /** Guthaben eines Wagens: angesammelt bis höchstens ein Schritt plus eine Einheit. */
    private double wagenGrenze(Map<Wagenladung, Double> rest, Wagenladung w, boolean laden, double dt) {
        return rest.computeIfAbsent(w, x -> {
            double jeSchritt = x.grenze(laden, dt);
            if (Double.isInfinite(jeSchritt)) {
                return jeSchritt;
            }
            return Math.min(jeSchritt + 1, wagenGuthaben.getOrDefault(x, 0.0) + jeSchritt);
        });
    }

    /**
     * Bietet diese Außenwelt-Station (Schattenbahnhof bzw. sein Güterbahnhof) das Gut selbst an? Dann bleibt es im
     * Zug: Er brächte sonst die eigene Ware zurück, sie verschwände, und im selben Halt lüde er sie nicht wieder
     * (Anwender 10.11.2026).
     */
    private boolean bietetAussenweltAn(Station s, String gutId) {
        if (!anlage.istAussenwelt(s)) {
            return false;
        }
        for (Gleis x : s.getGleise()) {
            if (x.getAngebot().getOrDefault(gutId, 0.0) > 0) {
                return true;
            }
        }
        return false;
    }

    private void umladenGueterGanz(ZugZustand z, Station s, Gleis g, double budget, double dt,
            Map<Wagenladung, Double> rest) {
        umladeGuthabenRest = budget;
        // 1. Entladen
        boolean nochEntladbar = false;
        if (g.isEntladen()) {
            for (Wagenladung w : z.getWagenladungen()) {
                for (String gutId : new ArrayList<>(w.getInhalt().keySet())) {
                    if (!g.erlaubt(gutId) || z.getGeladenImHalt().contains(gutId) || bietetAussenweltAn(s, gutId)
                            || Gleis.KOFFER_GUT.equals(gutId) && z.getKofferReisende() > EPS
                            || Tf3Daten.imPersonenzug(gutId)) {
                        continue;
                    }
                    double imGleis = g.getEingang().getOrDefault(gutId, 0.0);
                    double platz = g.getKapazitaet() - imGleis;
                    // Braucht eine angeschlossene Stadt das Gut, wird alles entladen; Überschuss wird vernichtet.
                    double annahme = g.isEntladenErzwingen() || ortMitBedarf(s, gutId) != null ? platz
                            : Math.min(platz, aufnahme(s, gutId) - imGleis);
                    boolean aussen = anlage.istAussenwelt(s);
                    if (aussen && !g.isEntladenErzwingen()) {
                        // Außenwelt nimmt nur die eingetragenen Güter ab, je Modellstunde begrenzt (10.11.2026)
                        annahme = Math.min(annahme, g.abnahmeFrei(gutId, stunde));
                    }
                    double vorhanden = w.getInhalt().getOrDefault(gutId, 0.0);
                    double wagenGrenze = wagenGrenze(rest, w, false, dt);
                    double m = ganz(Math.min(Math.min(budget, wagenGrenze), Math.min(vorhanden, annahme)));
                    if (m > EPS) {
                        w.entlade(gutId, m);
                        rest.put(w, wagenGrenze - m);
                        g.getEingang().merge(gutId, m, Double::sum);
                        g.getAktivEntladen().add(gutId);
                        z.getEntladenImHalt().add(gutId);
                        z.getBewegtImHalt().merge(gutId, m, Double::sum);
                        budget -= m;
                        umladeGuthabenRest = budget;
                        s.getStatistik().addiere("Entladen", tag, m);
                        fluss(anlage.infrastruktur(), s, gutId, m);
                        transfer(gutId, z.getGeladenIn().get(gutId), s, m, z.getName());
                        g.zaehle(gutId, true, m, tag);
                        if (aussen) {
                            g.zaehleAbnahme(gutId, m, stunde);
                        }
                    }
                    if (w.getInhalt().getOrDefault(gutId, 0.0) > EPS && ganz(annahme - m) >= 1) {
                        nochEntladbar = true;
                    }
                }
            }
        }
        if (nochEntladbar || budget <= EPS || !g.isLaden()) {
            return;
        }
        // 2. Laden (Wagen-Guthaben fürs Laden getrennt vom Entladen)
        rest.forEach(wagenGuthaben::put);
        rest.clear();
        for (Map.Entry<String, Double> e : g.getAusgang().entrySet()) {
            String gutId = e.getKey();
            if (z.getEntladenImHalt().contains(gutId) || e.getValue() <= EPS || !g.erlaubt(gutId)
                    || Tf3Daten.imPersonenzug(gutId)) {
                continue;
            }
            Gut gut = anlage.gut(gutId);
            for (Wagenladung w : z.getWagenladungen()) {
                if (budget <= EPS || e.getValue() <= EPS) {
                    break;
                }
                double wagenGrenze = wagenGrenze(rest, w, true, dt);
                double m = ganz(Math.min(Math.min(budget, wagenGrenze), Math.min(e.getValue(), w.freiFuer(gut))));
                if (m > EPS) {
                    w.lade(gut, m);
                    rest.put(w, wagenGrenze - m);
                    e.setValue(e.getValue() - m);
                    g.getAktivLaden().add(gutId);
                    z.getGeladenImHalt().add(gutId);
                    z.getBewegtImHalt().merge(gutId, m, Double::sum);
                    budget -= m;
                    umladeGuthabenRest = budget;
                    s.getStatistik().addiere("Geladen", tag, m);
                    String richtung = g.richtungFuer(z.getRichtung());
                    if (richtung != null && !richtung.isEmpty()) {
                        s.getStatistik().addiere(RICHTUNG_GUETER + richtung, tag, m);
                    }
                    fluss(s, anlage.infrastruktur(), gutId, m);
                    z.getGeladenIn().put(gutId, s);
                    g.zaehle(gutId, false, m, tag);
                }
            }
        }
        g.getAusgang().values().removeIf(v -> v <= EPS);
    }

    // ------------------------------------------------------------------ Hilfen

    private void protokolliere(String text) {
        ereignisse.addFirst(new Ereignis(modellZeitText, text));
        while (ereignisse.size() > 300) {
            ereignisse.removeLast();
        }
    }

    private static String rund(double v) {
        return String.valueOf(Math.round(v));
    }

    /** Fuer Tests und die Gesamttabelle: Umladen von Hand (Konzept 9.3, +Cargo Cargo-Manager). */
    public double ladeVonHand(ZugZustand z, Gleis g, String gutId, double menge) {
        Gut gut = anlage.gut(gutId);
        double rest = ganz(Math.min(menge, g.getAusgang().getOrDefault(gutId, 0.0)));
        double geladen = 0;
        for (Wagenladung w : z.getWagenladungen()) {
            double m = ganz(Math.min(rest, w.freiFuer(gut)));
            if (m > EPS) {
                w.lade(gut, m);
                rest -= m;
                geladen += m;
            }
        }
        g.getAusgang().merge(gutId, -geladen, Double::sum);
        g.getAusgang().values().removeIf(v -> v <= EPS);
        return geladen;
    }

    public double entladeVonHand(ZugZustand z, Gleis g, String gutId, double menge) {
        double rest = ganz(menge);
        double entladen = 0;
        for (Wagenladung w : z.getWagenladungen()) {
            double m = ganz(Math.min(rest, w.getInhalt().getOrDefault(gutId, 0.0)));
            if (m > EPS) {
                w.entlade(gutId, m);
                rest -= m;
                entladen += m;
            }
        }
        g.getEingang().merge(gutId, entladen, Double::sum);
        return entladen;
    }
}
