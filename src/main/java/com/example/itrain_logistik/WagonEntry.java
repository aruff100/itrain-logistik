package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Ein Wagen: {@code name} und {@code image} aus der .tcdz (Projektauftrag),
 * zusaetzlich das dort ebenfalls vorhandene Attribut {@code type} (z.B.
 * "coach", "g") - aus dem beim Import die {@link WagenArt} abgeleitet wird
 * (siehe {@link TcdzModelBuilder}) - sowie {@code description} (Kindelement
 * {@code <description>...</description>}, reines Anzeigefeld). Dazu frei
 * erweiterbare eigene {@link CustomField}e. Ladeklasse, Kapazitaet und Wagenladungen fuer den
 * Echtzeit-Kern stehen ebenfalls in diesen Feldern (siehe {@link LogistikFelder} und
 * {@link Fahrzeugdaten}).
 */
public final class WagonEntry {

    private final String name;
    private final String type;
    private final String imageFile;
    private final String description;
    private final ObservableList<CustomField> customFields = FXCollections.observableArrayList();

    public WagonEntry(String name, String type, String imageFile, String description) {
        this.name = name;
        this.type = type;
        this.imageFile = imageFile;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getImageFile() {
        return imageFile;
    }

    public String getDescription() {
        return description;
    }

    public ObservableList<CustomField> getCustomFields() {
        return customFields;
    }
}
