package com.example.itrain_logistik;

/**
 * Der feste zentrale Knoten "Anlagen-Infrastruktur" sowie die 15 auf der
 * Palette der Ebene 1 ("Es gibt Logistikpunkte. Diese Logistikpunkte
 * definieren die Modellbahn.") verfuegbaren Logistikpunkt-Silhouetten.
 * Icons liefert {@link NetzIcons}. Ersetzt die fruehere, kleinere
 * Betriebsstellen-Palette vollstaendig (siehe Projektauftrag).
 */
public enum NetzKnotenTyp {
    ANLAGEN_INFRASTRUKTUR("Anlagen-Infrastruktur"),
    BAHNHOF("Bahnhof"),
    HALTEPUNKT("Haltepunkt"),
    ANSCHLIESSER("Anschließer"),
    FABRIK("Fabrik"),
    LAGER("Lager"),
    BETRIEBSWERK("Betriebswerk"),
    GUETERBAHNHOF("Güterbahnhof"),
    BEKOHLUNG("Bekohlung"),
    TANKSTELLE("Tankstelle"),
    FLUESSIGLAGER("Flüssiglager"),
    GASLAGER("Gas-Lager"),
    OFFENLAGER("Offen-Lager"),
    LAGERHALLE("Lagerhalle"),
    POST("Post"),
    PERSONENVERKEHR("Personenverkehr"),
    /** Schattenbahnhof (Außenwelt mit Blöcken), Symbol default-symbole/SCHATTENBAHNHOF.svg. */
    SCHATTENBAHNHOF("Schattenbahnhof"),
    /** Modul (Anwender 09.11.2026): Teil der Anlage, der auf einem anderen Rechner im Browser gezeigt wird. */
    MODUL("Modul"),
    /** Tab (Anwender 09.11.2026): eigener Reiter der Übersicht. */
    REITER("Tab");

    private final String label;

    NetzKnotenTyp(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Die 15 Palette-Elemente, ohne den festen "Anlagen-Infrastruktur"-Knoten. */
    public static NetzKnotenTyp[] paletteTypen() {
        return new NetzKnotenTyp[]{BAHNHOF, HALTEPUNKT, ANSCHLIESSER, FABRIK, LAGER, BETRIEBSWERK, GUETERBAHNHOF,
                BEKOHLUNG, TANKSTELLE, FLUESSIGLAGER, GASLAGER, OFFENLAGER, LAGERHALLE, POST, PERSONENVERKEHR};
    }
}
