package com.example.itrain_logistik;

/** Fluessig/Trocken-Angabe eines Guetertransport-Wagens (Zusatzfeld, siehe {@link WagonLogistikPane}). */
public enum LadungsZustand {
    FLUESSIG("Flüssig"),
    TROCKEN("Trocken");

    private final String label;

    LadungsZustand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
