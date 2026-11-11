package com.example.itrain_logistik;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Ein frei benanntes Datenfeld (Name/Wert), das der Anwender zusaetzlich zu
 * den aus der .tcdz gelesenen Angaben pflegen kann - siehe "Da kommen, auf
 * der rechten Seite, noch Datenfelder hinzu" im Projektauftrag. Bewusst
 * generisch (statt fest verdrahteter Logistik-Felder): welche Felder
 * gebraucht werden (Ladestelle, Zuladung, ...), ist noch offen und laesst
 * sich so ohne Codeaenderung ergaenzen.
 */
public final class CustomField {

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty value = new SimpleStringProperty("");

    public CustomField() {
    }

    public CustomField(String name, String value) {
        this.name.set(name == null ? "" : name);
        this.value.set(value == null ? "" : value);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        this.name.set(value);
    }

    public StringProperty valueProperty() {
        return value;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String v) {
        this.value.set(v);
    }
}
