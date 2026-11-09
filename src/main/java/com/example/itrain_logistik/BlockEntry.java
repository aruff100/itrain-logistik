package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Ein Block (Gleisabschnitt): {@code name} und {@code type} aus der .tcdz. */
public final class BlockEntry {

    private final String name;
    private final String type;
    private final ObservableList<CustomField> customFields = FXCollections.observableArrayList();

    public BlockEntry(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public ObservableList<CustomField> getCustomFields() {
        return customFields;
    }
}
