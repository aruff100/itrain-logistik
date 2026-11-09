package com.example.itrain_logistik.kern;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stadt, Dorf oder Laendlich (Konzept 4.1, 4.4 und 8). Erzeugt Passagiere in 1. und 2. Klasse
 * und verbraucht Gueter nach Bedarf. {@link #getAussteigeAnteil()} ist der Anteil ankommender
 * Passagiere, die an Bahnhoefen dieses Orts aussteigen (Post-Prinzip, Konzept 8.2).
 */
public final class Ort extends AnlagenObjekt {

    private int einwohner;
    private double passagiereProStunde;
    private double anteilErsteKlasse = 0.10;
    private double aussteigeAnteil;
    /**
     * Ankommende Reisende je Modellstunde (Anwender 14.10.2026): so viele steigen hier höchstens aus und so viele
     * muss ein Gegenstück anliefern; {@link #ankommendUnbegrenzt} = beliebig viele.
     */
    private double ankommendProStunde;
    private boolean ankommendUnbegrenzt = true;
    /** Gut-Id -> Bedarf in Einheiten je Modellstunde. */
    private final Map<String, Double> bedarf = new LinkedHashMap<>();

    // --- Laufzeit ---
    /** Gut-Id -> noch offener Bedarf (waechst mit der Zeit, sinkt mit jeder Lieferung). */
    private final Map<String, Double> offenerBedarf = new LinkedHashMap<>();
    private double ankommendDieseStunde;
    private int stundeDesZaehlers = -1;
    private double passagierRest;
    /**
     * Reisende (Anwender 27.10.2026): Personen, die verreisen wollen und noch im Ort sind. Sie gehen gesammelt
     * alle {@link #SAMMEL_SEKUNDEN} Modellsekunden zum Bahnhof (grünes Lauflicht) und werden erst dort zu Wartenden.
     */
    private double reisende;
    private double seitGang;
    /** Abstand der Gänge zum Bahnhof in Modellsekunden (Annahme: 10 Modellminuten). */
    public static final double SAMMEL_SEKUNDEN = 600;

    /** Post und Expressgut je Modellstunde (Anwender 27.10.2026); negativ = Vorgabe nach Größe. */
    private boolean liefertPost = true;
    private boolean liefertExpressgut = true;
    private double postProStunde = -1;
    private double expressProStunde = -1;

    public Ort(String id, ObjektArt art, String name) {
        super(id, art, name);
        switch (art) {
            case STADT -> {
                einwohner = 1500;
                passagiereProStunde = 60;
                aussteigeAnteil = 0.5;
            }
            case DORF -> {
                einwohner = 400;
                passagiereProStunde = 20;
                aussteigeAnteil = 0.3;
            }
            default -> {
                einwohner = 100;
                passagiereProStunde = 6;
                aussteigeAnteil = 0.2;
            }
        }
    }

    public int getEinwohner() {
        return einwohner;
    }

    public void setEinwohner(int einwohner) {
        this.einwohner = einwohner;
    }

    public double getPassagiereProStunde() {
        return passagiereProStunde;
    }

    public void setPassagiereProStunde(double passagiereProStunde) {
        this.passagiereProStunde = passagiereProStunde;
    }

    public double getAnkommendProStunde() {
        return ankommendProStunde;
    }

    public void setAnkommendProStunde(double v) {
        this.ankommendProStunde = Math.max(0, Math.round(v));
    }

    public boolean isAnkommendUnbegrenzt() {
        return ankommendUnbegrenzt;
    }

    public void setAnkommendUnbegrenzt(boolean ankommendUnbegrenzt) {
        this.ankommendUnbegrenzt = ankommendUnbegrenzt;
    }

    /** Wie viele in dieser Modellstunde noch aussteigen dürfen (unendlich bei "Unbegrenzt"). */
    public double ankommendFrei(int modellStunde) {
        if (ankommendUnbegrenzt) {
            return Double.POSITIVE_INFINITY;
        }
        pruefeStundenwechsel(modellStunde);
        return Math.max(0, ankommendProStunde - ankommendDieseStunde);
    }

    public double getAnteilErsteKlasse() {
        return anteilErsteKlasse;
    }

    public void setAnteilErsteKlasse(double anteilErsteKlasse) {
        this.anteilErsteKlasse = Math.max(0, Math.min(1, anteilErsteKlasse));
    }

    public double getAussteigeAnteil() {
        return aussteigeAnteil;
    }

    public void setAussteigeAnteil(double aussteigeAnteil) {
        this.aussteigeAnteil = Math.max(0, Math.min(1, aussteigeAnteil));
    }

    public Map<String, Double> getBedarf() {
        return bedarf;
    }

    public Map<String, Double> getOffenerBedarf() {
        return offenerBedarf;
    }

    public double getAnkommendDieseStunde() {
        return ankommendDieseStunde;
    }

    void zaehleAnkommend(double anzahl, int modellStunde) {
        if (modellStunde != stundeDesZaehlers) {
            stundeDesZaehlers = modellStunde;
            ankommendDieseStunde = 0;
        }
        ankommendDieseStunde += anzahl;
    }

    void pruefeStundenwechsel(int modellStunde) {
        if (modellStunde != stundeDesZaehlers) {
            stundeDesZaehlers = modellStunde;
            ankommendDieseStunde = 0;
        }
    }

    public double getReisende() {
        return reisende;
    }

    public void setReisende(double reisende) {
        this.reisende = Math.max(0, reisende);
    }

    double getSeitGang() {
        return seitGang;
    }

    void setSeitGang(double seitGang) {
        this.seitGang = seitGang;
    }

    public boolean isLiefertPost() {
        return liefertPost;
    }

    public void setLiefertPost(boolean liefertPost) {
        this.liefertPost = liefertPost;
    }

    public boolean isLiefertExpressgut() {
        return liefertExpressgut;
    }

    public void setLiefertExpressgut(boolean liefertExpressgut) {
        this.liefertExpressgut = liefertExpressgut;
    }

    /** Post je Modellstunde (ganze Stück); ohne eigenen Wert die Vorgabe nach Größe. */
    public double getPostProStunde() {
        return postProStunde < 0 ? vorgabe(Tf3Daten.POST) : postProStunde;
    }

    public void setPostProStunde(double v) {
        this.postProStunde = v;
    }

    public double getExpressProStunde() {
        return expressProStunde < 0 ? vorgabe(Tf3Daten.EXPRESSGUT) : expressProStunde;
    }

    public void setExpressProStunde(double v) {
        this.expressProStunde = v;
    }

    /** Eigene Werte (negativ = Vorgabe nach Größe), nur zum Speichern. */
    double postRoh() {
        return postProStunde;
    }

    double expressRoh() {
        return expressProStunde;
    }

    /** Menge je Modellstunde, die der Ort tatsächlich liefert (0 = liefert nicht). */
    public double lieferung(String gutId) {
        if (Tf3Daten.POST.equals(gutId)) {
            return liefertPost ? getPostProStunde() : 0;
        }
        if (Tf3Daten.EXPRESSGUT.equals(gutId)) {
            return liefertExpressgut ? getExpressProStunde() : 0;
        }
        return 0;
    }

    /** Vorgabe nach Größe: Anteil der Abreisenden (Tf3Daten.postAnteil), mindestens 1 Stück je Modellstunde. */
    public double vorgabe(String gutId) {
        return Math.max(1, Math.ceil(passagiereProStunde * Tf3Daten.postAnteil(getArt(), gutId) - 1e-9));
    }

    double getPassagierRest() {
        return passagierRest;
    }

    void setPassagierRest(double passagierRest) {
        this.passagierRest = passagierRest;
    }

    /** Heute vernichtete Überschüsse je Gut (angeliefert über den Bedarf hinaus, Anwender 12.10.2026). */
    private final Map<String, Double> vernichtet = new LinkedHashMap<>();
    private int vernichtetTag = -1;

    public void vernichte(String gutId, double menge, int tag) {
        if (tag != vernichtetTag) {
            vernichtet.clear();
            vernichtetTag = tag;
        }
        vernichtet.merge(gutId, menge, Double::sum);
        getStatistik().addiere("Vernichtet", tag, menge);
    }

    /** Heute vernichtete Mengen je Gut (leer an einem neuen Modelltag). */
    public Map<String, Double> getVernichtetHeute(int tag) {
        if (tag != vernichtetTag) {
            return Map.of();
        }
        return vernichtet;
    }

    @Override
    public void aufNull() {
        super.aufNull();
        vernichtet.clear();
        offenerBedarf.replaceAll((k, v) -> 0.0);
        ankommendDieseStunde = 0;
        passagierRest = 0;
        reisende = 0;
        seitGang = 0;
    }
}
