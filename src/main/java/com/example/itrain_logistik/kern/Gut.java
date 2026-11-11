package com.example.itrain_logistik.kern;

/**
 * Ein Ladegut des Gueterkatalogs einer Anlage (Konzept Abschnitt 3.1). Grundbestand sind die
 * 36 Gueter aus Transport Fever 3 (siehe {@link Tf3Daten}); der Anwender kann eigene ergaenzen,
 * umbenennen und loeschen. Die {@link #getId() id} bleibt beim Umbenennen gleich, damit Rezepte,
 * Bestaende und Wagen ihre Zuordnung behalten.
 */
public final class Gut {

    private final String id;
    private String name;
    private Ladeklasse ladeklasse;
    private String farbe;
    private GutStufe stufe;
    private StadtBereich stadtBereich;
    /** Einheit aus {@link Einheit#ALLE}; null = Standard der Ladeklasse. */
    private String einheit;
    /** Kapazität des Wagens (in der Standardeinheit der Ladeklasse), die eine Einheit belegt; 1 = Standard. */
    private double kapazitaetJeEinheit = 1;
    /** null = Voreinstellung der Ladeklasse ({@link Ladeklasse#isMischbar()}). */
    private Boolean mischbar;

    public Gut(String id, String name, Ladeklasse ladeklasse, String farbe, GutStufe stufe, StadtBereich stadtBereich) {
        this.id = id;
        this.name = name;
        this.ladeklasse = ladeklasse;
        this.farbe = farbe;
        this.stufe = stufe;
        this.stadtBereich = stadtBereich;
    }

    public Gut kopie() {
        Gut k = new Gut(id, name, ladeklasse, farbe, stufe, stadtBereich);
        k.einheit = einheit;
        k.kapazitaetJeEinheit = kapazitaetJeEinheit;
        k.mischbar = mischbar;
        return k;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Ladeklasse getLadeklasse() {
        return ladeklasse;
    }

    public void setLadeklasse(Ladeklasse ladeklasse) {
        this.ladeklasse = ladeklasse;
    }

    public String getFarbe() {
        return farbe;
    }

    public void setFarbe(String farbe) {
        this.farbe = farbe;
    }

    public GutStufe getStufe() {
        return stufe;
    }

    public void setStufe(GutStufe stufe) {
        this.stufe = stufe;
    }

    public StadtBereich getStadtBereich() {
        return stadtBereich;
    }

    public void setStadtBereich(StadtBereich stadtBereich) {
        this.stadtBereich = stadtBereich;
    }

    /** Einheit des Guts; ohne eigene Angabe die Standardeinheit der Ladeklasse. */
    public String getEinheit() {
        return Einheit.gueltig(einheit, ladeklasse);
    }

    /** Eigene Einheit (null = Standard der Ladeklasse). */
    public String getEigeneEinheit() {
        return einheit != null && Einheit.ALLE.contains(einheit) ? einheit : null;
    }

    public void setEinheit(String einheit) {
        this.einheit = einheit == null || einheit.isBlank() || einheit.equals(Einheit.standard(ladeklasse))
                ? null : einheit;
    }

    /** Wie viel Wagenkapazität eine Einheit belegt (1 bei der Standardeinheit der Ladeklasse). */
    public double getKapazitaetJeEinheit() {
        return getEigeneEinheit() == null ? 1 : Math.max(1, kapazitaetJeEinheit);
    }

    public void setKapazitaetJeEinheit(double kapazitaetJeEinheit) {
        this.kapazitaetJeEinheit = Math.max(1, kapazitaetJeEinheit);
    }

    public Boolean getMischbarEinstellung() {
        return mischbar;
    }

    public void setMischbarEinstellung(Boolean mischbar) {
        this.mischbar = mischbar;
    }

    /** Darf dieses Gut zusammen mit anderen Guetern derselben Klasse in einer Wagenladung liegen? */
    public boolean isMischbar() {
        return mischbar != null ? mischbar : ladeklasse.isMischbar();
    }

    /** Zwei Buchstaben fuer das Gut-Symbol (z.B. "Ho" fuer Holz). */
    public String getKuerzel() {
        String n = name == null ? "?" : name.trim();
        if (n.isEmpty()) {
            return "?";
        }
        return n.length() == 1 ? n : n.substring(0, 2);
    }

    @Override
    public String toString() {
        return name;
    }
}
