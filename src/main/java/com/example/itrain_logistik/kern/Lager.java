package com.example.itrain_logistik.kern;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lager an einer Station (Konzept 4.1, TF3-Lagerhaus, +Cargo "Speicher"). Nimmt Gueter auf,
 * solange Platz ist, und bietet sie zum Verladen an. Keine Erzeugung, kein Verbrauch.
 */
public final class Lager extends AnlagenObjekt {

    private int kapazitaet = 100;
    /** Leer = alle Gueter-Ladeklassen. */
    private final Set<Ladeklasse> klassen = EnumSet.noneOf(Ladeklasse.class);
    private final Map<String, Double> bestand = new LinkedHashMap<>();

    public Lager(String id, String name) {
        super(id, ObjektArt.LAGER, name);
    }

    public int getKapazitaet() {
        return kapazitaet;
    }

    public void setKapazitaet(int kapazitaet) {
        this.kapazitaet = Math.max(0, kapazitaet);
    }

    public Set<Ladeklasse> getKlassen() {
        return klassen;
    }

    public Map<String, Double> getBestand() {
        return bestand;
    }

    public double summe() {
        return bestand.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double frei() {
        return Math.max(0, kapazitaet - summe());
    }

    public boolean nimmt(Gut gut) {
        return gut != null && gut.getLadeklasse() != Ladeklasse.PASSAGIERE
                && (klassen.isEmpty() || klassen.contains(gut.getLadeklasse()));
    }

    /** Laufzeit: seit wann (Modellsekunden des Kerns) ein Gut im Lager ohne Verbraucher liegt. */
    private final Map<String, Double> unverbrauchbarSeit = new LinkedHashMap<>();
    /** Laufzeit: Güter ohne Verbraucher bzw. solche, die nach 2 Modellstunden zur Abholung zurückgehen. */
    private final java.util.Set<String> unverbrauchbar = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> zurueck = new java.util.LinkedHashSet<>();

    public Map<String, Double> getUnverbrauchbarSeit() {
        return unverbrauchbarSeit;
    }

    public java.util.Set<String> getUnverbrauchbar() {
        return unverbrauchbar;
    }

    public java.util.Set<String> getZurueck() {
        return zurueck;
    }

    /** Vom Anwender zum Verladen freigegebene Güter ohne Verbraucher (sofort ans Ladegleis). */
    private final java.util.Set<String> bereitgestellt = new java.util.LinkedHashSet<>();

    public java.util.Set<String> getBereitgestellt() {
        return bereitgestellt;
    }

    @Override
    public void aufNull() {
        super.aufNull();
        unverbrauchbarSeit.clear();
        unverbrauchbar.clear();
        zurueck.clear();
        bereitgestellt.clear();
        bestand.clear();
    }
}
