package com.example.itrain_logistik.kern;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein Rezept eines Betriebs: Eingangsmengen und Ausgangsmengen je Ausfuehrung (Konzept
 * Abschnitt 4.2). Ein Rohstoffbetrieb ohne Pflicht-Eingang hat ein leeres
 * {@link #getEingang()}. Schluessel sind {@link Gut#getId()}s.
 */
public final class Rezept {

    private final Map<String, Integer> eingang = new LinkedHashMap<>();
    private final Map<String, Integer> ausgang = new LinkedHashMap<>();

    public Rezept() {
    }

    public Rezept(Map<String, Integer> eingang, Map<String, Integer> ausgang) {
        this.eingang.putAll(eingang);
        this.ausgang.putAll(ausgang);
    }

    public Map<String, Integer> getEingang() {
        return eingang;
    }

    public Map<String, Integer> getAusgang() {
        return ausgang;
    }

    public Rezept kopie() {
        return new Rezept(eingang, ausgang);
    }

    /** Lesbare Form, z.B. "2 Fisch + 1 Blech → 2 Konserven". */
    public String text(Anlage anlage) {
        return teil(eingang, anlage, "aus dem Nichts") + " → " + teil(ausgang, anlage, "nichts");
    }

    private static String teil(Map<String, Integer> mengen, Anlage anlage, String leer) {
        if (mengen.isEmpty()) {
            return leer;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : mengen.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(e.getValue()).append(' ').append(anlage.gutName(e.getKey()));
        }
        return sb.toString();
    }
}
