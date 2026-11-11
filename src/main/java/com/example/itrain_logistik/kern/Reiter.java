package com.example.itrain_logistik.kern;

/**
 * Tab (Anwender 09.11.2026): teilt die Übersicht in Reiter. Anlegen erzeugt einen weiteren Reiter mit demselben
 * Symbol; „Mit Tab verbinden…“ hängt einen Bahnhof samt allem, was an ihm hängt, an den Tab und damit in dessen
 * Reiter. Anders als ein Modul ohne Pflicht-Streckengleis und ohne Browser-Seite.
 */
public final class Reiter extends Bereich {

    public Reiter(String id, String name) {
        super(id, ObjektArt.REITER, name);
    }
}
