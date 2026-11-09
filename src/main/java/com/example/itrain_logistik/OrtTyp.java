package com.example.itrain_logistik;

/**
 * Die drei Siedlungsarten der Ebene 2 (innerhalb eines Logistikpunkts aus
 * Ebene 1): Stadt, Dorf, Ländlich - mehrfach an einen Logistikpunkt andockbar
 * (siehe Projektauftrag "Links sind dann wieder Symbole, zur Auswahl, Stadt,
 * Dorf, Ländlich. Diese Symbole können mehrfach angedockt werden."). Ein
 * Klick auf eines dieser Symbole oeffnet dessen Ladegut-/Personentabelle
 * (Ebene 3, siehe {@link LadegutZuweisung}).
 */
public enum OrtTyp {
    STADT("Stadt"),
    DORF("Dorf"),
    LAENDLICH("Ländlich");

    private final String label;

    OrtTyp(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
