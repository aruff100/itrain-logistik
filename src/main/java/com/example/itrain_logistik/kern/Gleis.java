package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ein Gleis einer Station = genau ein iTrain-Block (Konzept 6.1 und 6.2).
 * <p>
 * Ladegleis: {@link #getAusgang()} haelt Gueter, die auf Abholung warten (kommen aus dem
 * Ausgangslager der bedienten Betriebe, aus Lagern und Uebergaben), {@link #getEingang()} haelt
 * gerade abgeladene Gueter, die an die Abnehmer weiterfliessen. {@link #getKapazitaet()} gilt je
 * Gut und Richtung (Eingang/Ausgang).
 * <p>
 * Bahnsteig: {@link #getWartende()} je Fahrtrichtung zwei Werte (1. und 2. Klasse);
 * {@link #getKapazitaet()} gilt dann je Fahrtrichtung in Personen. Ohne eingetragene
 * Richtungen gibt es genau eine gemeinsame Warteschlange unter dem Schluessel {@link #OHNE_RICHTUNG}.
 */
public final class Gleis {

    public static final String OHNE_RICHTUNG = "";

    private final String id;
    private String name;
    private String blockName;
    private GleisArt art;
    private final List<String> richtungen = new ArrayList<>();
    private boolean laden = true;
    private boolean entladen = true;
    private boolean entladenErzwingen;
    /** Leer = alle Gueter. */
    private final Set<String> gueterAuswahl = new LinkedHashSet<>();
    private int kapazitaet;
    /** Einheiten bzw. Personen je Modellminute. */
    private double umladeRate;

    // --- Laufzeit ---
    private final Map<String, Double> ausgang = new LinkedHashMap<>();
    private final Map<String, Double> eingang = new LinkedHashMap<>();
    private final Map<String, double[]> wartende = new LinkedHashMap<>();
    private final Set<String> aktivLaden = new LinkedHashSet<>();
    private final Set<String> aktivEntladen = new LinkedHashSet<>();
    private String zugImGleis;
    private double letztEingestiegen;
    /** Heute hinzugekommen/abgegangen je Gut ("" = Personen): [zu, ab]; Laufzeit, nicht gespeichert. */
    private final Map<String, double[]> heute = new LinkedHashMap<>();
    /** Außenwelt (Schattenbahnhof): Personen je Modellstunde an diesem Bahnsteig, Anteil 1. Klasse. */
    private double passagiereProStunde;
    /**
     * Bahnsteig „Mit Koffer“ (Anwender 27.10.2026): {@link #KOFFER_ANTEIL} der Reisenden haben einen Koffer und
     * steigen nur in Züge mit Platz für Koffer (Gepäckwagen); Kofferreisende steigen nur an solchen Bahnsteigen aus.
     */
    private boolean mitKoffer;
    /** Davon wartende Reisende mit Koffer (Teil der Wartenden, über alle Richtungen). */
    private double kofferWartend;
    public static final double KOFFER_ANTEIL = 0.3;
    public static final String KOFFER_GUT = "suitcases";

    /**
     * Schattenbahnhof-Bahnsteig (Anwender 27.10.2026): nimmt Post/Expressgut an bzw. erzeugt sie je Modellstunde
     * (erzeugen nur bei „Reisende kommen von außerhalb“; sonst liefert der angeschlossene Ort).
     */
    private boolean postAnnehmen;
    private boolean expressAnnehmen;
    private double postProStunde;
    private double expressProStunde;

    public boolean nimmtAn(String gutId) {
        return Tf3Daten.POST.equals(gutId) ? postAnnehmen : Tf3Daten.EXPRESSGUT.equals(gutId) && expressAnnehmen;
    }

    public void setAnnehmen(String gutId, boolean v) {
        if (Tf3Daten.POST.equals(gutId)) {
            postAnnehmen = v;
        } else if (Tf3Daten.EXPRESSGUT.equals(gutId)) {
            expressAnnehmen = v;
        }
    }

    public double erzeugt(String gutId) {
        return Tf3Daten.POST.equals(gutId) ? postProStunde : Tf3Daten.EXPRESSGUT.equals(gutId) ? expressProStunde : 0;
    }

    public void setErzeugt(String gutId, double v) {
        if (Tf3Daten.POST.equals(gutId)) {
            postProStunde = Math.max(0, v);
        } else if (Tf3Daten.EXPRESSGUT.equals(gutId)) {
            expressProStunde = Math.max(0, v);
        }
    }

    public boolean isMitKoffer() {
        return mitKoffer;
    }

    public void setMitKoffer(boolean mitKoffer) {
        this.mitKoffer = mitKoffer;
        if (!mitKoffer) {
            kofferWartend = 0;
        }
    }

    public double getKofferWartend() {
        return kofferWartend;
    }

    public void setKofferWartend(double kofferWartend) {
        this.kofferWartend = Math.max(0, kofferWartend);
    }
    private double anteilErsteKlasse = 0.10;
    /** Außenwelt (Güterbahnhof am Schattenbahnhof): bereitgestellte Güter je Modellstunde. */
    private final Map<String, Double> angebot = new LinkedHashMap<>();
    private int heuteTag = -1;
    private double letztAusgestiegen;

    // --- Außenwelt: Abnahme je Modellstunde (Anwender 10.11.2026) ---
    /** Güter, die die Außenwelt hier abnimmt, je Modellstunde. Leer = alle Güter, unbegrenzt (wie bisher). */
    private final Map<String, Double> abnahme = new LinkedHashMap<>();
    /** Ankommende Personen je Modellstunde an diesem Bahnsteig; unter 0 = unbegrenzt. */
    private double ankommendProStunde = -1;
    /** Angenommene Stück Post/Expressgut je Modellstunde; unter 0 = unbegrenzt. */
    private double postAbnahmeProStunde = -1;
    private double expressAbnahmeProStunde = -1;
    /** Laufzeit: in dieser Modellstunde schon abgenommen je Schlüssel ("" = Personen). */
    private final Map<String, Double> abgenommenDieseStunde = new LinkedHashMap<>();
    private int abnahmeStunde = -1;

    public Map<String, Double> getAbnahme() {
        return abnahme;
    }

    public double getAnkommendProStunde() {
        return ankommendProStunde;
    }

    public void setAnkommendProStunde(double v) {
        this.ankommendProStunde = v < 0 ? -1 : v;
    }

    /** Grenze für Post bzw. Expressgut je Modellstunde; unter 0 = unbegrenzt. */
    public double abnahmeProStunde(String gutId) {
        return Tf3Daten.POST.equals(gutId) ? postAbnahmeProStunde
                : Tf3Daten.EXPRESSGUT.equals(gutId) ? expressAbnahmeProStunde : -1;
    }

    public void setAbnahmeProStunde(String gutId, double v) {
        if (Tf3Daten.POST.equals(gutId)) {
            postAbnahmeProStunde = v < 0 ? -1 : v;
        } else if (Tf3Daten.EXPRESSGUT.equals(gutId)) {
            expressAbnahmeProStunde = v < 0 ? -1 : v;
        }
    }

    /** Nimmt die Außenwelt an diesem Gleis das Gut überhaupt ab? (Güter: leere Tabelle = alle.) */
    public boolean nimmtAb(String gutId) {
        return abnahme.isEmpty() || abnahme.getOrDefault(gutId, 0.0) > 0;
    }

    /** Was die Außenwelt in dieser Modellstunde noch abnimmt; Schlüssel: Gut, Post, Expressgut oder "" = Personen. */
    public double abnahmeFrei(String schluessel, int modellStunde) {
        String k = schluessel == null ? "" : schluessel;
        double grenze;
        if (k.isEmpty()) {
            grenze = ankommendProStunde;
        } else if (Tf3Daten.POST.equals(k) || Tf3Daten.EXPRESSGUT.equals(k)) {
            grenze = abnahmeProStunde(k);
        } else {
            grenze = abnahme.isEmpty() ? -1 : abnahme.getOrDefault(k, 0.0);
        }
        if (grenze < 0) {
            return Double.POSITIVE_INFINITY;
        }
        if (modellStunde != abnahmeStunde) {
            abnahmeStunde = modellStunde;
            abgenommenDieseStunde.clear();
        }
        return Math.max(0, grenze - abgenommenDieseStunde.getOrDefault(k, 0.0));
    }

    void zaehleAbnahme(String schluessel, double menge, int modellStunde) {
        if (modellStunde != abnahmeStunde) {
            abnahmeStunde = modellStunde;
            abgenommenDieseStunde.clear();
        }
        abgenommenDieseStunde.merge(schluessel == null ? "" : schluessel, menge, Double::sum);
    }

    public Gleis(String id, GleisArt art) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.art = art;
        this.kapazitaet = art == GleisArt.BAHNSTEIG ? 200 : LADEGLEIS_VORGABE;
        this.umladeRate = UMLADE_VORGABE;
    }

    /** Vorgabe „Umladen je Modellminute“ für alle Gleise (Anwender 27.10.2026, vorher 30 bzw. 2). */
    public static final double UMLADE_VORGABE = 200;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAnzeigeName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return blockName != null && !blockName.isBlank() ? blockName : art.getLabel();
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public GleisArt getArt() {
        return art;
    }

    public void setArt(GleisArt art) {
        this.art = art;
    }

    public List<String> getRichtungen() {
        return richtungen;
    }

    /** Die Schluessel der Warteschlangen: die Richtungen oder, ohne Richtungen, {@link #OHNE_RICHTUNG}. */
    public List<String> warteschlangen() {
        return richtungen.isEmpty() ? List.of(OHNE_RICHTUNG) : richtungen;
    }

    public boolean isLaden() {
        return laden;
    }

    public void setLaden(boolean laden) {
        this.laden = laden;
    }

    public boolean isEntladen() {
        return entladen;
    }

    public void setEntladen(boolean entladen) {
        this.entladen = entladen;
    }

    public boolean isEntladenErzwingen() {
        return entladenErzwingen;
    }

    public void setEntladenErzwingen(boolean entladenErzwingen) {
        this.entladenErzwingen = entladenErzwingen;
    }

    public Set<String> getGueterAuswahl() {
        return gueterAuswahl;
    }

    public boolean erlaubt(String gutId) {
        return gueterAuswahl.isEmpty() || gueterAuswahl.contains(gutId);
    }

    /**
     * Vorgabe-Kapazität je Gut eines Ladegleises (Anwender 31.10.2026: Bahnhöfe halten wenig vor, mehr gehört in ein
     * Lager; vorher 45).
     */
    public static final int LADEGLEIS_VORGABE = 20;

    /** Kleinste Kapazität je Gut eines Ladegleises (Anwender 12.10.2026); mehr Vorrat nur über ein Lager. */
    public static final int MIN_KAPAZITAET_LADEGLEIS = 10;

    public int getKapazitaet() {
        return art == GleisArt.LADEGLEIS ? Math.max(MIN_KAPAZITAET_LADEGLEIS, kapazitaet) : kapazitaet;
    }

    public void setKapazitaet(int kapazitaet) {
        this.kapazitaet = Math.max(0, kapazitaet);
    }

    public double getUmladeRate() {
        return umladeRate;
    }

    public void setUmladeRate(double umladeRate) {
        this.umladeRate = Math.max(0.01, umladeRate);
    }

    public Map<String, Double> getAusgang() {
        return ausgang;
    }

    public Map<String, Double> getEingang() {
        return eingang;
    }

    public double getPassagiereProStunde() {
        return passagiereProStunde;
    }

    public void setPassagiereProStunde(double v) {
        this.passagiereProStunde = Math.max(0, v);
    }

    public double getAnteilErsteKlasse() {
        return anteilErsteKlasse;
    }

    public void setAnteilErsteKlasse(double v) {
        this.anteilErsteKlasse = Math.max(0, Math.min(1, v));
    }

    public Map<String, Double> getAngebot() {
        return angebot;
    }

    /** Zählt hinzukommende (zu = vom Zug abgeladen/ausgestiegen) bzw. abgehende Mengen des Modelltags. */
    void zaehle(String schluessel, boolean zu, double menge, int tag) {
        if (tag != heuteTag) {
            heute.clear();
            heuteTag = tag;
        }
        heute.computeIfAbsent(schluessel == null ? "" : schluessel, k -> new double[2])[zu ? 0 : 1] += menge;
    }

    /** Heute hinzugekommen/abgegangen je Gut ("" = Personen). */
    public Map<String, double[]> getHeute() {
        return heute;
    }

    public Map<String, double[]> getWartende() {
        return wartende;
    }

    public double[] wartendeIn(String richtung) {
        return wartende.computeIfAbsent(richtung == null ? OHNE_RICHTUNG : richtung, r -> new double[2]);
    }

    public double wartendeSumme(String richtung) {
        double[] w = wartende.get(richtung == null ? OHNE_RICHTUNG : richtung);
        return w == null ? 0 : w[0] + w[1];
    }

    public Set<String> getAktivLaden() {
        return aktivLaden;
    }

    public Set<String> getAktivEntladen() {
        return aktivEntladen;
    }

    public String getZugImGleis() {
        return zugImGleis;
    }

    void setZugImGleis(String zugImGleis) {
        this.zugImGleis = zugImGleis;
    }

    public double getLetztEingestiegen() {
        return letztEingestiegen;
    }

    public double getLetztAusgestiegen() {
        return letztAusgestiegen;
    }

    void setLetzteBewegung(double eingestiegen, double ausgestiegen) {
        this.letztEingestiegen = eingestiegen;
        this.letztAusgestiegen = ausgestiegen;
    }

    /**
     * Benennt eine Fahrtrichtung um (die Zielstation wurde umbenannt, Anwender 14.10.2026); die Wartenden
     * dieser Richtung bleiben erhalten. Gibt es die neue Richtung schon, werden beide zusammengelegt.
     */
    public boolean richtungUmbenennen(String alt, String neu) {
        int i = richtungen.indexOf(alt);
        if (i < 0 || alt.equals(neu)) {
            return false;
        }
        if (richtungen.contains(neu)) {
            richtungen.remove(i);
        } else {
            richtungen.set(i, neu);
        }
        double[] w = wartende.remove(alt);
        if (w != null) {
            double[] ziel = wartendeIn(neu);
            ziel[0] += w[0];
            ziel[1] += w[1];
        }
        return true;
    }

    /** Ordnet einen Wert aus einem Trigger einer eingetragenen Richtung zu (ohne Gross-/Kleinschreibung). */
    /**
     * Von welcher Seite ein Ladegleis bedient wird (Anwender 09.11.2026): null = beide, sonst die Fahrtrichtung im
     * Block laut iTrain („next“ bzw. „prev“). Fährt ein Zug anders ein, meldet die Logistik „Zug muss Kopf machen“.
     */
    private String bedienSeite;
    /**
     * Schattenbahnhof (Anwender 09.11.2026): an diesem Gleis wird auch ohne Halt, bei der Durchfahrt, geladen und
     * entladen bzw. steigen Reisende ein und aus. Vorgabe an; nur an der Außenwelt wirksam.
     */
    private boolean durchfahrt = true;

    public boolean isDurchfahrt() {
        return durchfahrt;
    }

    public void setDurchfahrt(boolean durchfahrt) {
        this.durchfahrt = durchfahrt;
    }

    public String getBedienSeite() {
        return bedienSeite;
    }

    public void setBedienSeite(String bedienSeite) {
        this.bedienSeite = bedienSeite == null || bedienSeite.isBlank() ? null : bedienSeite;
    }

    public String richtungFuer(String triggerRichtung) {
        if (richtungen.isEmpty()) {
            return OHNE_RICHTUNG;
        }
        if (triggerRichtung == null || triggerRichtung.isBlank()) {
            return null;
        }
        for (String r : richtungen) {
            if (r.equalsIgnoreCase(triggerRichtung.trim())) {
                return r;
            }
        }
        return null;
    }

    /** Leert Ausgangs-/Eingangsbestand und Warteschlangen des Gleises. */
    public void aufNull() {
        ausgang.clear();
        eingang.clear();
        wartende.clear();
        aktivLaden.clear();
        aktivEntladen.clear();
        letztEingestiegen = 0;
        letztAusgestiegen = 0;
    }
}
