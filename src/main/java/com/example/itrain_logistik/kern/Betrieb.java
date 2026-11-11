package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ein Betrieb (Rohstoff- oder Verarbeitungsbetrieb, Konzept 4.1 bis 4.3): Rezepte, Rate in
 * Ausfuehrungen je Modellstunde, Eingangs- und Ausgangslager mit Kapazitaet je Gut.
 * Bei mehreren Rezepten (z.B. Konservenfabrik: Fisch oder Fleisch, je mit Blech) laeuft jeweils
 * das erste Rezept, fuer das alle Eingaenge vorraetig sind und dessen Ausgaenge Platz haben.
 */
public final class Betrieb extends AnlagenObjekt {

    private String vorlageId;
    private final List<Rezept> rezepte = new ArrayList<>();
    private double rateProStunde = 1.0;
    /** Gut-Id -> Kapazitaet des Eingangslagers. */
    private final Map<String, Integer> lagerEin = new LinkedHashMap<>();
    /** Gut-Id -> Kapazitaet des Ausgangslagers. */
    private final Map<String, Integer> lagerAus = new LinkedHashMap<>();

    /** Eingangsgüter, die "immer geliefert" werden: stehen unbegrenzt zur Verfügung, ohne Zug. */
    private final Set<String> immerGeliefert = new LinkedHashSet<>();
    /**
     * "Liefert immer, ohne auf der Anlage zu sein": der Betrieb wird nicht gerechnet, seine
     * Ausgangsgüter gelten bei allen Verbrauchern als immer geliefert.
     */
    private boolean virtuell;
    /**
     * "Dynamisch" (Anwender 14.10.2026): die Erzeugung schwankt von Modellstunde zu Modellstunde zwischen 50 % und
     * 150 % der Rate; im Mittel bleibt sie gleich. Überschuss geht ans Gleis bzw. ins Lager.
     */
    private boolean dynamisch;

    // --- Laufzeit (wird mitgespeichert, damit Bestaende einen Neustart ueberleben) ---
    private final Map<String, Double> bestandEin = new LinkedHashMap<>();
    private final Map<String, Double> bestandAus = new LinkedHashMap<>();
    private double fortschritt;
    /**
     * Lieferung in Schüben (Anwender 27.10.2026): 0 = laufend; sonst sammelt der Betrieb seine Erzeugung und gibt sie
     * alle so viele Modellstunden auf einmal ab (z. B. 5 Gebinde Fisch alle 6 Stunden).
     */
    private double lieferAlleStunden;
    private final Map<String, Double> schub = new LinkedHashMap<>();
    /** Modellsekunden seit der letzten Lieferung (nur bei Schüben). */
    private double seitLieferung;
    private String zustand = "";

    public Betrieb(String id, String name) {
        super(id, ObjektArt.BETRIEB, name);
    }

    public String getVorlageId() {
        return vorlageId;
    }

    public void setVorlageId(String vorlageId) {
        this.vorlageId = vorlageId;
    }

    public List<Rezept> getRezepte() {
        return rezepte;
    }

    public Set<String> getImmerGeliefert() {
        return immerGeliefert;
    }

    public boolean isVirtuell() {
        return virtuell;
    }

    /**
     * "Produktion passt sich an" (Anwender 10.11.2026): je Modellstunde eine Stufe hoch, wenn die Eingangslager gut
     * gefüllt sind und der Ausgang abfließt, eine Stufe herunter, wenn Eingang fehlt oder der Ausgang voll ist.
     * Stufe 0..{@link #MAX_STUFE} = Rate und Lagerkapazitäten ×1..×4.
     */
    public static final int MAX_STUFE = 3;
    private boolean anpassen;
    private int stufe;
    /** Modellsekunden (mit Tempo gewichtet) seit der letzten Prüfung der Stufe. */
    private double seitAnpassung;

    public boolean isAnpassen() {
        return anpassen;
    }

    public void setAnpassen(boolean anpassen) {
        this.anpassen = anpassen;
        if (!anpassen) {
            stufe = 0;
        }
    }

    public int getStufe() {
        return anpassen ? stufe : 0;
    }

    public void setStufe(int stufe) {
        this.stufe = Math.max(0, Math.min(MAX_STUFE, stufe));
    }

    public double getSeitAnpassung() {
        return seitAnpassung;
    }

    public void setSeitAnpassung(double s) {
        this.seitAnpassung = Math.max(0, s);
    }

    /** ×1 ohne Anpassung, sonst ×(1 + Stufe): gilt für Rate und Lagerkapazitäten. */
    public int stufenFaktor() {
        return 1 + getStufe();
    }

    /** Kapazität des Eingangslagers in der aktuellen Stufe (0 = kein Eingangslager für das Gut). */
    public double kapazitaetEin(String gutId) {
        Integer kap = lagerEin.get(gutId);
        return kap == null ? 0 : (double) kap * stufenFaktor();
    }

    /** Kapazität des Ausgangslagers in der aktuellen Stufe (unbegrenzt ohne Eintrag). */
    public double kapazitaetAus(String gutId) {
        Integer kap = lagerAus.get(gutId);
        return kap == null ? Double.MAX_VALUE : (double) kap * stufenFaktor();
    }

    public boolean isDynamisch() {
        return dynamisch;
    }

    public void setDynamisch(boolean dynamisch) {
        this.dynamisch = dynamisch;
    }

    public void setVirtuell(boolean virtuell) {
        this.virtuell = virtuell;
    }

    public double getLieferAlleStunden() {
        return lieferAlleStunden;
    }

    public void setLieferAlleStunden(double stunden) {
        this.lieferAlleStunden = Math.max(0, stunden);
    }

    /** Erzeugt, aber noch nicht abgegeben (nur bei Lieferung in Schüben). */
    public Map<String, Double> getSchub() {
        return schub;
    }

    public double getSeitLieferung() {
        return seitLieferung;
    }

    public void setSeitLieferung(double s) {
        this.seitLieferung = Math.max(0, s);
    }

    public double getRateProStunde() {
        return rateProStunde;
    }

    public void setRateProStunde(double rateProStunde) {
        this.rateProStunde = Math.max(0, rateProStunde);
    }

    public Map<String, Integer> getLagerEin() {
        return lagerEin;
    }

    public Map<String, Integer> getLagerAus() {
        return lagerAus;
    }

    public Map<String, Double> getBestandEin() {
        return bestandEin;
    }

    public Map<String, Double> getBestandAus() {
        return bestandAus;
    }

    public double getFortschritt() {
        return fortschritt;
    }

    void setFortschritt(double fortschritt) {
        this.fortschritt = fortschritt;
    }

    public String getZustand() {
        return zustand;
    }

    void setZustand(String zustand) {
        this.zustand = zustand;
    }

    /** Alle Gueter, die irgendein Rezept verbraucht. */
    public List<String> eingangsGueter() {
        List<String> l = new ArrayList<>();
        for (Rezept r : rezepte) {
            for (String g : r.getEingang().keySet()) {
                if (!l.contains(g)) {
                    l.add(g);
                }
            }
        }
        return l;
    }

    /** Alle Gueter, die irgendein Rezept erzeugt. */
    public List<String> ausgangsGueter() {
        List<String> l = new ArrayList<>();
        for (Rezept r : rezepte) {
            for (String g : r.getAusgang().keySet()) {
                if (!l.contains(g)) {
                    l.add(g);
                }
            }
        }
        return l;
    }

    /** Erzeugung je Modellstunde fuer ein Gut, gerechnet mit dem ersten Rezept, das es erzeugt. */
    public double erzeugungProStunde(String gutId) {
        for (Rezept r : rezepte) {
            Integer m = r.getAusgang().get(gutId);
            if (m != null) {
                return m * rateProStunde;
            }
        }
        return 0;
    }

    /** Verbrauch je Modellstunde fuer ein Gut, gerechnet mit dem ersten Rezept, das es verbraucht. */
    public double verbrauchProStunde(String gutId) {
        for (Rezept r : rezepte) {
            Integer m = r.getEingang().get(gutId);
            if (m != null) {
                return m * rateProStunde;
            }
        }
        return 0;
    }

    /** Freier Platz im Eingangslager fuer ein Gut (0, wenn der Betrieb es nicht verbraucht). */
    public double freiEin(String gutId) {
        if (lagerEin.get(gutId) == null) {
            return 0;
        }
        return Math.max(0, kapazitaetEin(gutId) - bestandEin.getOrDefault(gutId, 0.0));
    }

    public double auslastung() {
        double kap = 0;
        double belegt = 0;
        for (Map.Entry<String, Integer> e : lagerAus.entrySet()) {
            kap += e.getValue();
            belegt += bestandAus.getOrDefault(e.getKey(), 0.0);
        }
        return kap <= 0 ? 0 : belegt / kap;
    }

    @Override
    public void aufNull() {
        super.aufNull();
        bestandEin.clear();
        bestandAus.clear();
        schub.clear();
        seitLieferung = 0;
        fortschritt = 0;
        zustand = "";
    }
}
