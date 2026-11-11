package com.example.itrain_logistik.kern;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tageswerte je Reihe (z.B. "Produziert", "Verbraucht") ueber die letzten {@link #TAGE}
 * Modelltage (Konzept Abschnitt 2). Laufzeitwert, wird nicht gespeichert.
 */
public final class Statistik {

    public static final int TAGE = 14;

    private final Map<String, TreeMap<Integer, Double>> reihen = new LinkedHashMap<>();

    public void addiere(String reihe, int tag, double menge) {
        if (menge == 0) {
            return;
        }
        TreeMap<Integer, Double> werte = reihen.computeIfAbsent(reihe, r -> new TreeMap<>());
        werte.merge(tag, menge, Double::sum);
        while (werte.size() > TAGE) {
            werte.pollFirstEntry();
        }
    }

    public void leeren() {
        reihen.clear();
    }

    public Map<String, TreeMap<Integer, Double>> getReihen() {
        return reihen;
    }

    public double wert(String reihe, int tag) {
        TreeMap<Integer, Double> werte = reihen.get(reihe);
        return werte == null ? 0 : werte.getOrDefault(tag, 0.0);
    }
}
