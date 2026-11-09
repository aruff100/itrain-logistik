package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Eine Lokomotive: {@code name}/{@code typ} (XML-Attribute {@code name}/
 * {@code type}), {@code image} (Kindelement {@code <image file="...">}) und
 * {@code description} (Kindelement {@code <description>...</description>})
 * aus der .tcdz, dazu frei erweiterbare eigene {@link CustomField}e. Die
 * Beschreibung ist ein reines Anzeigefeld aus der .tcdz (siehe
 * {@link LocomotivesWindow}) - der iTrain-{@code type} selbst wird nicht
 * mehr direkt angezeigt, sondern beim Import in das eigene Datenfeld
 * "Antriebsart" uebernommen (siehe {@link TcdzModelBuilder}).
 */
public final class LocomotiveEntry {

    private final String name;
    private final String type;
    private final String imageFile;
    private final String description;
    private final ObservableList<CustomField> customFields = FXCollections.observableArrayList();

    public LocomotiveEntry(String name, String type, String imageFile, String description) {
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
