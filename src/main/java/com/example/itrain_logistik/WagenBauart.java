package com.example.itrain_logistik;

/** Offen/Geschlossen-Angabe eines Guetertransport-Wagens (Zusatzfeld, siehe {@link WagonLogistikPane}). */
public enum WagenBauart {
    OFFEN("Offen"),
    GESCHLOSSEN("Geschlossen");

    private final String label;

    WagenBauart(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
