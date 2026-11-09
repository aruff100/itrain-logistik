package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Bereich;

/**
 * Das Symbol eines Bereichs (Tab/Modul) in dessen eigenem Reiter (Anwender 09.11.2026): dasselbe Objekt wie im ersten
 * Reiter – Name, Farben und alle Angaben kommen vom Bereich, nur die Lage ist eigen ({@link Bereich#getInnenX()}).
 * Umbenennen hier benennt den Bereich um und umgekehrt. Nicht Teil der Anlage, wird nicht gespeichert.
 */
public final class ReiterAnker extends AnlagenObjekt {

    private final Bereich bereich;

    public ReiterAnker(Bereich bereich) {
        super(bereich.getId() + "#reiter", bereich.getArt(), bereich.getName());
        this.bereich = bereich;
    }

    public Bereich getBereich() {
        return bereich;
    }

    @Override
    public String getName() {
        return bereich.getName();
    }

    @Override
    public void setName(String name) {
        bereich.setName(name);
    }

    @Override
    public String getAnzeigeName() {
        return bereich.getAnzeigeName();
    }

    @Override
    public double getX() {
        return bereich.getInnenX();
    }

    @Override
    public void setX(double x) {
        bereich.setInnenX(x);
    }

    @Override
    public double getY() {
        return bereich.getInnenY();
    }

    @Override
    public void setY(double y) {
        bereich.setInnenY(y);
    }

    @Override
    public String getFarbe() {
        return bereich.getFarbe();
    }

    @Override
    public void setFarbe(String farbe) {
        bereich.setFarbe(farbe);
    }

    @Override
    public String getFuellung() {
        return bereich.getFuellung();
    }

    @Override
    public void setFuellung(String f) {
        bereich.setFuellung(f);
    }

    @Override
    public String getSchrift() {
        return bereich.getSchrift();
    }

    @Override
    public void setSchrift(String s) {
        bereich.setSchrift(s);
    }

    @Override
    public double getLinienStaerke() {
        return bereich.getLinienStaerke();
    }
}
