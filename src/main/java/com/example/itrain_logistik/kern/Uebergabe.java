package com.example.itrain_logistik.kern;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Verbindung zur Aussenwelt, z.B. Schattenbahnhof (Konzept 4.1, +Cargo "Unbegrenzte
 * Verteilung"): liefert die Gueter aus {@link #getAngebot()} unbegrenzt und nimmt die Gueter
 * aus {@link #getAnnahme()} unbegrenzt ab. Ist {@link #isPassagiereAussteigen()} gesetzt,
 * steigen an Bahnsteigen dieser Station alle ankommenden Passagiere aus.
 */
public final class Uebergabe extends AnlagenObjekt {

    private final Set<String> angebot = new LinkedHashSet<>();
    /** Gueter, die abgenommen werden; mit {@link #annahmeAlles} jedes Gut. */
    private final Set<String> annahme = new LinkedHashSet<>();
    private boolean annahmeAlles;
    private boolean passagiereAussteigen = true;

    public Uebergabe(String id, String name) {
        super(id, ObjektArt.UEBERGABE, name);
    }

    public Set<String> getAngebot() {
        return angebot;
    }

    public Set<String> getAnnahme() {
        return annahme;
    }

    public boolean isAnnahmeAlles() {
        return annahmeAlles;
    }

    public void setAnnahmeAlles(boolean annahmeAlles) {
        this.annahmeAlles = annahmeAlles;
    }

    public boolean nimmt(String gutId) {
        return annahmeAlles || annahme.contains(gutId);
    }

    public boolean isPassagiereAussteigen() {
        return passagiereAussteigen;
    }

    public void setPassagiereAussteigen(boolean passagiereAussteigen) {
        this.passagiereAussteigen = passagiereAussteigen;
    }
}
