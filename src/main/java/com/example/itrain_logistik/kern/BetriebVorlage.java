package com.example.itrain_logistik.kern;

import java.util.List;
import java.util.Map;

/**
 * Vorlage fuer einen Betrieb aus Transport Fever 3 (32 Industrien, siehe {@link Tf3Daten}).
 * Lagerkapazitaeten sind die TF3-Werte; beim Anlegen werden sie mit dem Mengenmassstab der
 * Anlage ({@link Anlage#getMengenMassstab()}) skaliert.
 */
public record BetriebVorlage(String id, String name, Map<String, Integer> eingangslager,
        Map<String, Integer> ausgangslager, List<Rezept> rezepte) {

    public boolean istRohstoffbetrieb() {
        // Rohstoffbetriebe haben in TF3 nur den (hier verpflichtenden) Booster als Eingang.
        return eingangslager.size() <= 1 && rezepte.size() == 1 && rezepte.get(0).getEingang().size() <= 1
                && ausgangslager.keySet().stream().noneMatch(eingangslager::containsKey)
                && Tf3Daten.BOOSTER_GUETER.containsAll(eingangslager.keySet());
    }

    @Override
    public String toString() {
        return name;
    }
}
