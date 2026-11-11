package com.example.itrain_logistik.kern;

/** Stellung eines Guts in der Lieferkette - nur zur Sortierung und Anzeige. */
public enum GutStufe {
    ROHSTOFF("Rohstoff"),
    ZWISCHENPRODUKT("Zwischenprodukt"),
    ENDPRODUKT("Endprodukt");

    private final String label;

    GutStufe(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
