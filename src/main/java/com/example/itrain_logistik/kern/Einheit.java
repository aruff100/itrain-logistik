package com.example.itrain_logistik.kern;

import java.util.List;

/**
 * Einheiten der Güter (Anwender 14.10.2026). Jede Ladeklasse hat eine Standardeinheit, in der auch die Kapazität
 * der Wagen zählt: Schüttgut = Tonnen, Sperrgut = Stück, Stückgut = Gebinde, Flüssiggut = Liter. Coils, Brammen,
 * Pakete, Gebinde und Langgut zählen als Stück, Raummeter als m³. Wählt der Anwender bei einem Gut eine andere als
 * die Standardeinheit, gibt "Kapazität je Einheit" an, wie viele Kapazitätseinheiten des Wagens eine Einheit belegt
 * (z. B. Stahl in Brammen zu je 10: ein Sperrgutwagen mit 20 fasst 2 Brammen).
 */
public final class Einheit {

    public static final List<String> ALLE = List.of("Tonnen", "Stück", "Liter", "Raummeter", "Coils", "Brammen",
            "Pakete", "Gebinde", "Langgut");

    private Einheit() {
    }

    /** Standardeinheit einer Ladeklasse (zugleich Einheit der Wagenkapazität). */
    public static String standard(Ladeklasse k) {
        if (k == null) {
            return "Stück";
        }
        return switch (k) {
            case SCHUETTGUT -> "Tonnen";
            case SPERRGUT -> "Stück";
            case STUECKGUT -> "Gebinde";
            case FLUESSIGGUT -> "Liter";
            default -> "Stück";
        };
    }

    /** Worin eine Einheit gezählt wird: Coils, Brammen, Pakete, Gebinde, Langgut = Stück, Raummeter = m³. */
    public static String basis(String einheit) {
        if (einheit == null) {
            return "Stück";
        }
        return switch (einheit) {
            case "Coils", "Brammen", "Pakete", "Gebinde", "Langgut" -> "Stück";
            case "Raummeter" -> "m³";
            default -> einheit;
        };
    }

    /** Anzeige "Gebinde (Stück)" bzw. "Tonnen". */
    public static String mitBasis(String einheit) {
        String b = basis(einheit);
        return b.equals(einheit) ? einheit : einheit + " (" + b + ")";
    }

    /**
     * Einheiten, in denen die Kapazität eines Wagens dieser Ladeklasse angegeben werden kann (gleiche Basis wie die
     * Standardeinheit, z. B. Stückgut: Gebinde, Stück, Pakete …), Standard zuerst (Anwender 27.10.2026).
     */
    public static List<String> fuerWagen(Ladeklasse k) {
        String std = standard(k);
        List<String> l = new java.util.ArrayList<>();
        l.add(std);
        for (String e : ALLE) {
            if (!e.equals(std) && basis(e).equals(basis(std))) {
                l.add(e);
            }
        }
        return l;
    }

    /** Gewählte Kapazitätseinheit eines Wagens, wenn sie zur Ladeklasse passt, sonst die Standardeinheit. */
    public static String wagen(String gewaehlt, Ladeklasse k) {
        return gewaehlt != null && fuerWagen(k).contains(gewaehlt) ? gewaehlt : standard(k);
    }

    /** Ältere Dateien: freier Text "Einheit" oder unbekannt = Standard der Ladeklasse. */
    public static String gueltig(String einheit, Ladeklasse k) {
        return einheit != null && ALLE.contains(einheit) ? einheit : standard(k);
    }
}
