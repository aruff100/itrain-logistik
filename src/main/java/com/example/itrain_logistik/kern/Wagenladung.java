package com.example.itrain_logistik.kern;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Eine Wagenladung nach +Cargo-Programmbeschreibung 2.4: Ladeklasse, Kapazitaet in Einheiten
 * und aktueller Inhalt. Nicht mischbare Gueter belegen eine Wagenladung allein.
 */
public final class Wagenladung {

    private final String wagenName;
    private final Ladeklasse klasse;
    private final double kapazitaet;
    /** Leer = alle Gueter der Ladeklasse. */
    private final Set<String> erlaubt = new LinkedHashSet<>();
    private final Map<String, Double> inhalt = new LinkedHashMap<>();
    /** Kapazität je Einheit der geladenen Güter (Anwender 14.10.2026), fehlend = 1. */
    private final Map<String, Double> faktor = new LinkedHashMap<>();
    private boolean exklusiv;
    /** Modellsekunden fuer eine volle Ladung; 0 = keine Bewertung (nur die Rate des Gleises zaehlt). */
    private final double zeitLaden;
    private final double zeitEntladen;
    /** Behälter-Wagen: nimmt jedes Gut auf, unabhängig von Ladeklasse und Auswahl (Lade-Joker). */
    private boolean joker;

    public Wagenladung(String wagenName, Ladeklasse klasse, double kapazitaet, Set<String> erlaubt) {
        this(wagenName, klasse, kapazitaet, erlaubt, 0, 0);
    }

    public Wagenladung(String wagenName, Ladeklasse klasse, double kapazitaet, Set<String> erlaubt,
            double zeitLaden, double zeitEntladen) {
        this.zeitLaden = Math.max(0, zeitLaden);
        this.zeitEntladen = Math.max(0, zeitEntladen);
        this.wagenName = wagenName;
        this.klasse = klasse;
        this.kapazitaet = kapazitaet;
        if (erlaubt != null) {
            this.erlaubt.addAll(erlaubt);
        }
    }

    public String getWagenName() {
        return wagenName;
    }

    /** Einheit, in der die Kapazität angezeigt wird (nur Anzeige, gleiche Basis wie die Ladeklasse). */
    private String einheit;

    public String getEinheit() {
        return Einheit.wagen(einheit, klasse);
    }

    public void setEinheit(String einheit) {
        this.einheit = einheit;
    }

    public Ladeklasse getKlasse() {
        return klasse;
    }

    public double getKapazitaet() {
        return kapazitaet;
    }

    public Set<String> getErlaubt() {
        return erlaubt;
    }

    public boolean isJoker() {
        return joker;
    }

    public void setJoker(boolean joker) {
        this.joker = joker;
    }

    public double getZeitLaden() {
        return zeitLaden;
    }

    public double getZeitEntladen() {
        return zeitEntladen;
    }

    /** Hoechstmenge, die in {@code dt} Modellsekunden geladen (bzw. entladen) werden kann. */
    public double grenze(boolean laden, double dt) {
        double zeit = laden ? zeitLaden : zeitEntladen;
        return zeit > 0 ? kapazitaet * dt / zeit : Double.POSITIVE_INFINITY;
    }

    public Map<String, Double> getInhalt() {
        return inhalt;
    }

    public double summe() {
        return inhalt.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /** Belegte Kapazität: Menge je Gut mal Kapazität je Einheit. */
    public double belegt() {
        double b = 0;
        for (Map.Entry<String, Double> e : inhalt.entrySet()) {
            b += e.getValue() * faktor.getOrDefault(e.getKey(), 1.0);
        }
        return b;
    }

    public void setFaktor(String gutId, double f) {
        faktor.put(gutId, Math.max(1, f));
    }

    public double frei() {
        return Math.max(0, kapazitaet - belegt());
    }

    /** Wie viel von {@code gut} diese Wagenladung noch aufnehmen kann. */
    public double freiFuer(Gut gut) {
        if (gut == null) {
            return 0;
        }
        // Mit aktiv geschalteten Guetern (Wagenliste) zaehlen nur diese, sonst die Ladeklasse.
        if (!joker && (!erlaubt.isEmpty() ? !erlaubt.contains(gut.getId()) : gut.getLadeklasse() != klasse)) {
            return 0;
        }
        boolean andereGueter = false;
        for (Map.Entry<String, Double> e : inhalt.entrySet()) {
            if (e.getValue() > 1e-9 && !e.getKey().equals(gut.getId())) {
                andereGueter = true;
                break;
            }
        }
        // Behälter (Joker): jedes Gut steckt in eigenen Behältern, die Mischregel gilt nicht.
        if (!joker && andereGueter && (!gut.isMischbar() || exklusiv)) {
            return 0;
        }
        // in Einheiten des Guts: eine Bramme zu 10 belegt 10 Kapazität
        return Math.floor(frei() / gut.getKapazitaetJeEinheit() + 1e-9);
    }

    /** Bucht {@code menge} ein und merkt sich, ob ein nicht mischbares Gut die Ladung belegt. */
    public void lade(Gut gut, double menge) {
        inhalt.merge(gut.getId(), menge, Double::sum);
        faktor.put(gut.getId(), gut.getKapazitaetJeEinheit());
        if (!gut.isMischbar() && !joker) {
            exklusiv = true;
        }
    }

    /** Bucht {@code menge} aus; eine leere Ladung ist wieder fuer alles frei. */
    public void entlade(String gutId, double menge) {
        double rest = inhalt.getOrDefault(gutId, 0.0) - menge;
        if (rest <= 1e-9) {
            inhalt.remove(gutId);
        } else {
            inhalt.put(gutId, rest);
        }
        if (summe() < 1e-9) {
            exklusiv = false;
        }
    }

    /** Leert die Wagenladung. */
    public void leeren() {
        inhalt.clear();
        exklusiv = false;
    }

    public boolean isExklusiv() {
        return exklusiv;
    }

    void setExklusiv(boolean exklusiv) {
        this.exklusiv = exklusiv;
    }
}
