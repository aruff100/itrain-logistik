package com.example.itrain_logistik;

/**
 * Antriebsart einer Lokomotive (Zusatzfeld, siehe {@link LocomotiveLogistikPane}) -
 * steuert dort, welche Verbrauchsfelder angezeigt werden: Diesel/Wasserstoff
 * zeigen "Verbrauch auf 100km (Vorbild)" und "Tankinhalt Liter", Dampf zeigt
 * "Wasservorrat Kubikmeter" und "Kohlevorrat Tonnen", Elektrisch/Batterie
 * zeigen gar kein Verbrauchsfeld (siehe Projektauftrag "Bei Elektrisch oder
 * Batterie kein Verbrauch"). Wird beim Import aus der .tcdz, wo moeglich,
 * automatisch aus deren {@code type}-Attribut abgeleitet (siehe
 * {@link TcdzModelBuilder}) - bleibt sie unbestimmt, waehlt der Anwender
 * selbst.
 */
public enum LokAntriebsart {
    DIESEL("Diesel"),
    WASSERSTOFF("Wasserstoff"),
    ELEKTRISCH("Elektrisch"),
    BATTERIE("Batterie"),
    DAMPF("Dampf");

    private final String label;

    LokAntriebsart(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
