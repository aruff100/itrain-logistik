package com.example.itrain_logistik;

/**
 * Die drei Aktionsarten, die der externe "iTrain-Logistik-Simulator"
 * (eigenstaendiges Schwesterprojekt, siehe dessen TriggerProtokoll.java) in
 * die Protokolldatei "&lt;basisname&gt;.simulator-trigger.xml" schreibt.
 * Eigenstaendige Kopie von dessen TriggerAktion-Enum, da beide Projekte
 * keine gemeinsame Bibliothek teilen.
 */
public enum SimulatorAktion {
    EINFAHRT,
    WARTEN,
    ABFAHRT,
    /** Zug verlässt die Anlage bzw. wird aus dem Block entfernt (Ende des Vorgangs im Simulator). */
    LOESCHEN;

    /** Anzeigename ("Einfahrt", "Warten", "Abfahrt", "Löschen"). */
    public String getLabel() {
        return switch (this) {
            case EINFAHRT -> "Einfahrt";
            case WARTEN -> "Warten";
            case ABFAHRT -> "Abfahrt";
            case LOESCHEN -> "Löschen";
        };
    }
}
