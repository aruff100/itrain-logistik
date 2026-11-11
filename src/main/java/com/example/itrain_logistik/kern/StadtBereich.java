package com.example.itrain_logistik.kern;

/**
 * Bereich einer Stadt, der ein Gut nachfragt (TF3: Gewerbe oder Industrie, siehe Spieldaten Abschnitt 4; dazu
 * Bevölkerung, Anwender 14.10.2026).
 */
public enum StadtBereich {
    GEWERBE("Gewerbe"),
    INDUSTRIE("Industrie"),
    BEVOELKERUNG("Bevölkerung");

    private final String label;

    StadtBereich(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
