package com.example.itrain_logistik.kern;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bereich der Übersicht (Anwender 09.11.2026): ein Tab oder ein Modul. Das Symbol hängt im ersten Reiter an der
 * Anlagen-Infrastruktur; zugleich hat der Bereich einen eigenen Reiter, in dem dasselbe Symbol (gleicher Name, eigene
 * Lage {@link #getInnenX()}/{@link #getInnenY()}) die Rolle der Anlagen-Infrastruktur übernimmt: Stationen, die mit
 * dem Bereich verbunden sind ({@link #getStationen()}), stehen samt Güterbahnhof, Orten, Betrieben und Lagern dort.
 */
public abstract class Bereich extends AnlagenObjekt {

    private final Set<String> stationen = new LinkedHashSet<>();
    private double innenX = 400;
    private double innenY = 60;

    protected Bereich(String id, ObjektArt art, String name) {
        super(id, art, name);
    }

    /** Ids der Stationen (Bahnhöfe, nicht die angedockten Güterbahnhöfe), die an diesem Bereich hängen. */
    public Set<String> getStationen() {
        return stationen;
    }

    /** Lage des Symbols im eigenen Reiter. */
    public double getInnenX() {
        return innenX;
    }

    public void setInnenX(double innenX) {
        this.innenX = innenX;
    }

    public double getInnenY() {
        return innenY;
    }

    public void setInnenY(double innenY) {
        this.innenY = innenY;
    }
}
