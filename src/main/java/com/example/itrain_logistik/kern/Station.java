package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Bahnhof, Haltepunkt, Gueterbahnhof oder Anschluss (Konzept 6.1). Der Einzugsbereich
 * ({@link #getEinzugsbereich()}) ersetzt den Radius aus TF: Ids der Orte, Betriebe, Lager und
 * Uebergaben, die diese Station bedient.
 */
public final class Station extends AnlagenObjekt {

    private final List<Gleis> gleise = new ArrayList<>();
    private final List<String> einzugsbereich = new ArrayList<>();
    /**
     * Schattenbahnhof(-Gbf): Güter, die hier nicht verschwinden, sondern zum Weitertransport bereitgestellt werden
     * („Transport über Schattenbahnhof“, Anwender 31.10.2026).
     */
    private final java.util.Set<String> umlagern = new java.util.LinkedHashSet<>();
    /** Name des in iTrain definierten Bahnhofs, aus dem diese Station angeheftet wurde (sonst null). */
    private String itrainName;
    /**
     * Id der Station, zu der dieser (Güter-)Bahnhof gehört (Güterbahnhof neben einem Personenbahnhof);
     * gezeichnet wird er mit einer Linie zu dieser Station statt zu den Orten.
     */
    private String gehoertZu;

    public Station(String id, ObjektArt art, String name) {
        super(id, art, name);
        if (!art.istStation()) {
            throw new IllegalArgumentException("Keine Stationsart: " + art);
        }
    }

    /**
     * Nach dem Andocken eines Güterbahnhofs durch das Programm: die Übersicht macht den Bahnhof einmal mindestens so
     * breit wie den Güterbahnhof (Anwender 27.10.2026); danach ändert der Anwender die Breite wie gewohnt.
     */
    private boolean breiteAngleichen;

    /**
     * Schattenbahnhof: Reisende kommen aus angeschlossenen Orten (wie an einem normalen Bahnhof) statt von
     * außerhalb (Mengen je Gleis), Anwender 27.10.2026.
     */
    private boolean reisendeAusOrt;

    public boolean isReisendeAusOrt() {
        return reisendeAusOrt;
    }

    public void setReisendeAusOrt(boolean reisendeAusOrt) {
        this.reisendeAusOrt = reisendeAusOrt;
    }

    public boolean isBreiteAngleichen() {
        return breiteAngleichen;
    }

    public void setBreiteAngleichen(boolean breiteAngleichen) {
        this.breiteAngleichen = breiteAngleichen;
    }

    public String getGehoertZu() {
        return gehoertZu;
    }

    public void setGehoertZu(String gehoertZu) {
        this.gehoertZu = gehoertZu == null || gehoertZu.isBlank() ? null : gehoertZu;
    }

    public String getItrainName() {
        return itrainName;
    }

    public void setItrainName(String itrainName) {
        this.itrainName = itrainName == null || itrainName.isBlank() ? null : itrainName;
    }

    public List<Gleis> getGleise() {
        return gleise;
    }

    public java.util.Set<String> getUmlagern() {
        return umlagern;
    }

    public List<String> getEinzugsbereich() {
        return einzugsbereich;
    }

    public boolean bedient(String objektId) {
        return einzugsbereich.contains(objektId);
    }

    public void schalteEinzugsbereich(String objektId) {
        if (objektId == null || objektId.equals(getId())) {
            return;
        }
        if (!einzugsbereich.remove(objektId)) {
            einzugsbereich.add(objektId);
        }
    }

    public List<Gleis> gleiseDerArt(GleisArt art) {
        List<Gleis> l = new ArrayList<>();
        for (Gleis g : gleise) {
            if (g.getArt() == art) {
                l.add(g);
            }
        }
        return l;
    }

    @Override
    public void aufNull() {
        super.aufNull();
        for (Gleis g : gleise) {
            g.aufNull();
        }
    }
}
