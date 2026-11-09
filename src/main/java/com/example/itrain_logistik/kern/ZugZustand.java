package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Laufzeitzustand eines Zugs: Standort, Halt, Ladung je Wagenladung und Passagiere je
 * Einstiegsstation (Post-Prinzip, Konzept 8.2). Wird mit der Anlage gespeichert, damit
 * Ladungen einen Neustart ueberleben.
 */
public final class ZugZustand {

    private final String name;
    private String block;
    private String richtung;
    private boolean haltend;
    /**
     * Gleise, an denen der Zug gerade haelt. Ein Block kann zugleich Bahnsteig eines Bahnhofs und
     * Ladegleis eines Gueterbahnhofs sein, deshalb bis zu zwei Halte gleichzeitig.
     */
    private final List<Halt> halte = new ArrayList<>();

    /** Ein Halt: Station, Gleis und zugeordnete Richtung (null = unbekannt, dann alle Warteschlangen). */
    public record Halt(Station station, Gleis gleis, String richtungAmGleis) {
    }
    private String linie;
    private String letzteStationId;
    private final List<Wagenladung> wagenladungen = new ArrayList<>();
    private double sitzplaetzeErste;
    private double sitzplaetzeZweite;
    /** Personen je Modellsekunde aus den Wagenzeiten (unendlich = keine Bewertung). */
    private double personenJeSekundeEin = Double.POSITIVE_INFINITY;
    private double personenJeSekundeAus = Double.POSITIVE_INFINITY;
    private String signatur = "";
    /** Einstiegsstation-Id -> [1. Klasse, 2. Klasse]. */
    private final Map<String, double[]> passagiere = new LinkedHashMap<>();
    /** Reisende mit Koffer an Bord (Teil der Passagiere), Anwender 27.10.2026. */
    private double kofferReisende;

    public double getKofferReisende() {
        return kofferReisende;
    }

    public void setKofferReisende(double kofferReisende) {
        this.kofferReisende = Math.max(0, kofferReisende);
    }
    /** Wo zuletzt Reisende eingestiegen sind bzw. je Gut geladen wurde (nur Laufzeit, für die Transfermeldung). */
    private Station einstiegPersonen;
    private final Map<String, Station> geladenIn = new java.util.HashMap<>();
    /** Waehrend eines Halts: noch auszusteigende Passagiere je Einstiegsstation. */
    private final Map<String, double[]> aussteigenPlan = new LinkedHashMap<>();
    /** Waehrend eines Halts an diesem Gleis abgeladene Gueter (werden nicht sofort wieder geladen). */
    private final Set<String> entladenImHalt = new LinkedHashSet<>();
    /** Waehrend eines Halts geladene Gueter (werden im selben Halt nicht wieder abgeladen). */
    private final Set<String> geladenImHalt = new LinkedHashSet<>();

    public ZugZustand(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getBlock() {
        return block;
    }

    void setBlock(String block) {
        this.block = block;
    }

    public String getRichtung() {
        return richtung;
    }

    void setRichtung(String richtung) {
        this.richtung = richtung;
    }

    public boolean isHaltend() {
        return haltend;
    }

    void setHaltend(boolean haltend) {
        this.haltend = haltend;
    }

    public List<Halt> getHalte() {
        return halte;
    }

    /** Erste Station des aktuellen Halts (zur Anzeige), sonst null. */
    public Station getStation() {
        return halte.isEmpty() ? null : halte.get(0).station();
    }

    public String getLinie() {
        return linie;
    }

    public void setLinie(String linie) {
        this.linie = linie;
    }

    public String getLetzteStationId() {
        return letzteStationId;
    }

    void setLetzteStationId(String letzteStationId) {
        this.letzteStationId = letzteStationId;
    }

    public List<Wagenladung> getWagenladungen() {
        return wagenladungen;
    }

    public double getSitzplaetzeErste() {
        return sitzplaetzeErste;
    }

    public double getSitzplaetzeZweite() {
        return sitzplaetzeZweite;
    }

    double getPersonenJeSekundeEin() {
        return personenJeSekundeEin;
    }

    double getPersonenJeSekundeAus() {
        return personenJeSekundeAus;
    }

    public String getSignatur() {
        return signatur;
    }

    /**
     * Schlüssel in {@link #getPassagiere()} für Fahrgäste, deren Zug nach ihrem Einstieg schon an einer
     * anderen Station gehalten hat: Sie dürfen an jeder Station aussteigen, auch an ihrer Einstiegsstation.
     */
    public static final String UNTERWEGS = "*";

    /** Fahrgäste an Bord je Einstiegsstation (Id) bzw. {@link #UNTERWEGS}: [1. Klasse, 2. Klasse]. */
    public Station getEinstiegPersonen() {
        return einstiegPersonen;
    }

    public void setEinstiegPersonen(Station s) {
        this.einstiegPersonen = s;
    }

    public Map<String, Station> getGeladenIn() {
        return geladenIn;
    }

    public Map<String, double[]> getPassagiere() {
        return passagiere;
    }

    Map<String, double[]> getAussteigenPlan() {
        return aussteigenPlan;
    }

    /** Güter, die in diesem Halt entladen bzw. geladen wurden (nur lesen, für die Anzeige). */
    public Set<String> entladenImHalt() {
        return java.util.Collections.unmodifiableSet(getEntladenImHalt());
    }

    public Set<String> geladenImHalt() {
        return java.util.Collections.unmodifiableSet(getGeladenImHalt());
    }

    Set<String> getEntladenImHalt() {
        return entladenImHalt;
    }

    Set<String> getGeladenImHalt() {
        return geladenImHalt;
    }

    /**
     * Uebernimmt eine neue Ausstattung. Vorhandene Ladung wird so weit wie moeglich in die neuen
     * Wagenladungen umgebucht; was keinen Platz mehr findet, geht verloren.
     */
    /** Neuer, leerer Zustand nur mit der Ausstattung (Plätze, Wagen) – für Prüfungen ohne laufenden Kern. */
    public static ZugZustand nurAusstattung(String name, ZugAusstattung a, Anlage anlage) {
        ZugZustand z = new ZugZustand(name);
        z.uebernehme(a != null ? a : ZugAusstattung.LEER, anlage);
        return z;
    }

    void uebernehme(ZugAusstattung a, Anlage anlage) {
        personenJeSekundeEin = a.personenJeSekundeEin();
        personenJeSekundeAus = a.personenJeSekundeAus();
        if (a.signatur().equals(signatur) && wagenladungen.size() == a.wagenladungen().size()) {
            sitzplaetzeErste = a.sitzplaetzeErste();
            sitzplaetzeZweite = a.sitzplaetzeZweite();
            return;
        }
        Map<String, Double> alteLadung = new LinkedHashMap<>();
        for (Wagenladung w : wagenladungen) {
            w.getInhalt().forEach((g, m) -> alteLadung.merge(g, m, Double::sum));
        }
        wagenladungen.clear();
        wagenladungen.addAll(a.wagenladungen());
        for (Map.Entry<String, Double> e : alteLadung.entrySet()) {
            Gut gut = anlage.gut(e.getKey());
            double rest = e.getValue();
            for (Wagenladung w : wagenladungen) {
                if (rest <= 1e-9 || gut == null) {
                    break;
                }
                double m = Math.min(rest, w.freiFuer(gut));
                if (m > 0) {
                    w.lade(gut, m);
                    rest -= m;
                }
            }
        }
        sitzplaetzeErste = a.sitzplaetzeErste();
        sitzplaetzeZweite = a.sitzplaetzeZweite();
        signatur = a.signatur();
    }

    void setSignaturRoh(String signatur) {
        this.signatur = signatur;
    }

    void setSitzplaetze(double erste, double zweite) {
        this.sitzplaetzeErste = erste;
        this.sitzplaetzeZweite = zweite;
    }

    /** Summe der Ladung je Gut. */
    public Map<String, Double> ladungJeGut() {
        Map<String, Double> m = new LinkedHashMap<>();
        for (Wagenladung w : wagenladungen) {
            w.getInhalt().forEach((g, v) -> m.merge(g, v, Double::sum));
        }
        return m;
    }

    /** Kapazitaet je Ladeklasse. */
    public Map<Ladeklasse, Double> kapazitaetJeKlasse() {
        Map<Ladeklasse, Double> m = new LinkedHashMap<>();
        for (Wagenladung w : wagenladungen) {
            m.merge(w.getKlasse(), w.getKapazitaet(), Double::sum);
        }
        return m;
    }

    /** Kapazität aller Wagenladungen, die das Gut laden dürfen (Joker, aktive Güter oder Ladeklasse). */
    public double kapazitaetFuer(Gut gut) {
        double k = 0;
        for (Wagenladung w : wagenladungen) {
            boolean passt = gut != null && (w.isJoker() || (!w.getErlaubt().isEmpty()
                    ? w.getErlaubt().contains(gut.getId()) : w.getKlasse() == gut.getLadeklasse()));
            k += passt ? w.getKapazitaet() : 0;
        }
        return k;
    }

    public boolean hatJoker() {
        return wagenladungen.stream().anyMatch(Wagenladung::isJoker);
    }

    public double gueterKapazitaet() {
        return wagenladungen.stream().mapToDouble(Wagenladung::getKapazitaet).sum();
    }

    public double gueterLadung() {
        return wagenladungen.stream().mapToDouble(Wagenladung::belegt).sum();
    }

    /** Personenzug (Plaetze) oder noch Passagiere an Bord? */
    public boolean hatPlaetzeOderPassagiere() {
        return sitzplaetzeErste + sitzplaetzeZweite > 0 || !passagiere.isEmpty();
    }

    public double[] passagiereSumme() {
        double[] s = new double[2];
        for (double[] p : passagiere.values()) {
            s[0] += p[0];
            s[1] += p[1];
        }
        return s;
    }

    /** Leert Ladung und Passagiere (Halt und Standort bleiben). */
    public void aufNull() {
        for (Wagenladung w : wagenladungen) {
            w.leeren();
        }
        passagiere.clear();
        aussteigenPlan.clear();
        entladenImHalt.clear();
        geladenImHalt.clear();
    }

    /** Fahrtrichtung umbenannt (Station umbenannt): laufende Halte und Trigger-Richtung nachziehen. */
    void richtungUmbenennen(String alt, String neu) {
        if (alt.equalsIgnoreCase(richtung == null ? "" : richtung)) {
            richtung = neu;
        }
        List<Halt> l = new ArrayList<>();
        for (Halt h : halte) {
            l.add(alt.equals(h.richtungAmGleis()) ? new Halt(h.station(), h.gleis(), neu) : h);
        }
        halte.clear();
        halte.addAll(l);
    }

    /** Hängt die Halte nach einem Austausch der Objekte (Editor-Abbruch) auf die neuen Objekte um. */
    void halteNeuZuordnen(Anlage a) {
        List<Halt> neu = new ArrayList<>();
        for (Halt h : halte) {
            if (a.objekt(h.station().getId()) instanceof Station s) {
                for (Gleis g : s.getGleise()) {
                    if (g.getId().equals(h.gleis().getId())) {
                        neu.add(new Halt(s, g, h.richtungAmGleis()));
                    }
                }
            }
        }
        halte.clear();
        halte.addAll(neu);
    }
}
