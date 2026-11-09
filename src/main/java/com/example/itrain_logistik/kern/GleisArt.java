package com.example.itrain_logistik.kern;

/** Bahnsteig (Passagiere) oder Ladegleis (Gueter), Konzept 6.1. */
public enum GleisArt {
    BAHNSTEIG("Bahnsteig"),
    LADEGLEIS("Ladegleis");

    private final String label;

    GleisArt(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
