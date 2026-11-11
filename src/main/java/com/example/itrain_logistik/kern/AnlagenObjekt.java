package com.example.itrain_logistik.kern;

import java.util.UUID;

/** Gemeinsame Basis aller Objekte der Anlage: Name, Art, Lage in der Uebersicht, Farbe. */
public abstract class AnlagenObjekt {

    private final String id;
    private ObjektArt art;
    private String name;
    private double x;
    private double y;
    private String farbe;
    private String fuellung;
    private String schrift;
    private final Statistik statistik = new Statistik();
    /** Tempo-Stufe dieses Objekts, siehe {@link Anlage#tempoFaktor(int)}; Vorgabe 0 = ×1. */
    private int tempo;
    /** Nur dieses Objekt angehalten (Pausen-Schalter im Detailfenster, Anwender 14.10.2026). */
    private boolean pausiert;
    /** Stärke der Verbindungslinie zu diesem Objekt im Editor/Übersicht in Pixel. */
    private double linienStaerke = 3;
    /** Vom Anwender im Editor gezogene Breite des Symbolkastens; 0 = automatisch. */
    private double breite;
    /** Neu eingefügt und noch nicht verschoben: wird farbig hervorgehoben. */
    private boolean neu;
    /**
     * Reiter (Id eines {@link Bereich}), in dem das Objekt angelegt wurde; zählt nur, solange es an keiner Station
     * hängt (sonst gilt der Reiter der Station). null = erster Reiter.
     */
    private String reiterId;

    protected AnlagenObjekt(String id, ObjektArt art, String name) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.art = art;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public ObjektArt getArt() {
        return art;
    }

    /**
     * Ändert die Art, nur zwischen Stationsarten (Station "Anderes" bekommt ihre eigentliche Art,
     * Anwender 14.10.2026).
     */
    public void setArt(ObjektArt neu) {
        if (neu == null || !art.istStation() || !neu.istStation()) {
            throw new IllegalArgumentException("Art nur zwischen Stationsarten änderbar");
        }
        this.art = neu;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAnzeigeName() {
        return name != null && !name.isBlank() ? name : art.getLabel();
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getFarbe() {
        return farbe;
    }

    /** Füllung des Kastens auf der Anlage (null = Standard), Anwender 27.10.2026. */
    public String getFuellung() {
        return fuellung;
    }

    public void setFuellung(String fuellung) {
        this.fuellung = fuellung;
    }

    /** Schriftfarbe im Kasten (null = Standard). */
    public String getSchrift() {
        return schrift;
    }

    public void setSchrift(String schrift) {
        this.schrift = schrift;
    }

    public void setFarbe(String farbe) {
        this.farbe = farbe;
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        this.tempo = Math.max(Anlage.TEMPO_MIN, Math.min(Anlage.TEMPO_MAX, tempo));
    }

    public boolean isPausiert() {
        return pausiert;
    }

    public void setPausiert(boolean pausiert) {
        this.pausiert = pausiert;
    }

    public String getReiterId() {
        return reiterId;
    }

    public void setReiterId(String reiterId) {
        this.reiterId = reiterId;
    }

    public boolean isNeu() {
        return neu;
    }

    public void setNeu(boolean neu) {
        this.neu = neu;
    }

    public double getLinienStaerke() {
        return linienStaerke;
    }

    public double getBreite() {
        return breite;
    }

    public void setBreite(double breite) {
        this.breite = Math.max(0, breite);
    }

    public void setLinienStaerke(double linienStaerke) {
        this.linienStaerke = linienStaerke > 0 ? linienStaerke : 3;
    }

    /** Setzt die Laufzeitwerte dieses Objekts auf 0 (Bestände, Warteschlangen, Statistik). */
    public void aufNull() {
        statistik.leeren();
    }

    public Statistik getStatistik() {
        return statistik;
    }

    @Override
    public String toString() {
        return getAnzeigeName() + " (" + art.getLabel() + ")";
    }
}
