package com.example.itrain_logistik.kern;

/**
 * Ladeklasse eines Guts (Transport Fever 3, siehe analyse/Transport-Fever-3-Spieldaten.md,
 * Abschnitt 1). Bestimmt, welche Wagenladungen und Lager ein Gut aufnehmen koennen.
 * "Mischbar" ist die Voreinstellung: Stueckgut darf
 * gemischt in einer Wagenladung liegen, Fluessigkeiten nicht. Fuer Schuettgut ist "nein" ein
 * Vorschlag aus dem Konzept (analyse/Konzept-Echtzeitkern.md, Abschnitt 3.2).
 */
public enum Ladeklasse {
    SCHUETTGUT("Schüttgut", "#FECA17", false),
    SPERRGUT("Sperrgut", "#006C3A", true),
    STUECKGUT("Stückgut", "#013C5E", true),
    FLUESSIGGUT("Flüssiggut", "#9AA4AA", false),
    PASSAGIERE("Passagiere", "#1A3399", false);

    private final String label;
    private final String farbe;
    private final boolean mischbar;

    Ladeklasse(String label, String farbe, boolean mischbar) {
        this.label = label;
        this.farbe = farbe;
        this.mischbar = mischbar;
    }

    public String getLabel() {
        return label;
    }

    public String getFarbe() {
        return farbe;
    }

    public boolean isMischbar() {
        return mischbar;
    }

    /** Die vier Gueter-Ladeklassen ohne Passagiere (fuer Wagen- und Lager-Auswahl). */
    public static Ladeklasse[] gueterKlassen() {
        return new Ladeklasse[]{SCHUETTGUT, SPERRGUT, STUECKGUT, FLUESSIGGUT};
    }
}
