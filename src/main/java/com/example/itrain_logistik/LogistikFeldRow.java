package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.util.StringConverter;

import java.util.function.Function;

/**
 * Kleine, wiederverwendbare Zeilen-Bauer fuer die strukturierten Logistik-
 * Zusatzdaten-Panels ({@link WagonLogistikPane}, {@link LocomotiveLogistikPane},
 * {@link BlockLogistikPane}): je eine Zeile Label + Eingabefeld in einer
 * {@link GridPane}, direkt an ein {@link CustomField} in der uebergebenen
 * Liste gebunden (ueber {@link LogistikFelder}). Freitext wird erst beim
 * Verlassen des Feldes uebernommen (nicht bei jedem Tastendruck), Auswahl-
 * und Kontrollkaestchen sofort.
 */
final class LogistikFeldRow {

    private LogistikFeldRow() {
    }

    static TextField text(GridPane grid, int row, String label, ObservableList<CustomField> fields, String key,
            Runnable onChanged) {
        TextField field = new TextField(LogistikFelder.getValue(fields, key));
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                LogistikFelder.setValue(fields, key, field.getText());
                onChanged.run();
            }
        });
        grid.addRow(row, new Label(label), field);
        return field;
    }

    /** Nur Ziffern, höchstens {@code stellen} Stellen (Anwender 14.10.2026: Plätze und Zeiten 4 Stellen). */
    static TextField ziffern(TextField field, int stellen) {
        field.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0," + stellen + "}") ? c : null));
        field.setPrefColumnCount(stellen + 1);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }

    /**
     * Wie {@link #text}, zeigt aber, solange fuer dieses Feld noch kein
     * eigener Wert hinterlegt ist, {@code vorlagenWert} als Vorbelegung an
     * (siehe Projektauftrag "Den Maßstab sowie den Modellbahn-Faktor setzen
     * wir in den Einstellungen. Das ist dann eine Vorlage für alle Daten,
     * kann aber geändert werden."). Solange der Anwender das Feld nicht
     * verlaesst, bleibt in den eigenen Daten weiterhin nichts hinterlegt -
     * die Vorlage wirkt also je Eintrag, bis er einmal individuell
     * bearbeitet wird.
     */
    static TextField textWithDefault(GridPane grid, int row, String label, ObservableList<CustomField> fields, String key,
            String vorlagenWert, Runnable onChanged) {
        String eigenerWert = LogistikFelder.getValue(fields, key);
        String anzeige = !eigenerWert.isBlank() ? eigenerWert : (vorlagenWert == null ? "" : vorlagenWert);
        TextField field = new TextField(anzeige);
        field.getProperties().put(Haken.GESPEICHERT, eigenerWert); // Vorbelegung zählt nicht als gespeichert
        if (eigenerWert.isBlank() && !anzeige.isBlank()) {
            // Vorbelegung in die Arbeitskopie, damit "Speichern" sie wirklich übernimmt
            LogistikFelder.setValue(fields, key, anzeige);
        }
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                LogistikFelder.setValue(fields, key, field.getText());
                onChanged.run();
            }
        });
        grid.addRow(row, new Label(label), field);
        return field;
    }

    static void check(GridPane grid, int row, String label, ObservableList<CustomField> fields, String key,
            Runnable onChanged) {
        CheckBox box = new CheckBox();
        box.setSelected("true".equals(LogistikFelder.getValue(fields, key)));
        box.selectedProperty().addListener((obs, oldV, newV) -> {
            LogistikFelder.setValue(fields, key, String.valueOf(newV));
            onChanged.run();
        });
        grid.addRow(row, new Label(label), box);
    }

    static <E extends Enum<E>> ComboBox<E> enumCombo(GridPane grid, int row, String label,
            ObservableList<CustomField> fields, String key, E[] values, Function<E, String> labelFn,
            Runnable onChanged) {
        ComboBox<E> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(E e) {
                return e == null ? "" : labelFn.apply(e);
            }

            @Override
            public E fromString(String s) {
                return null;
            }
        });
        box.setValue(LogistikFelder.parseEnum(LogistikFelder.getValue(fields, key), values));
        box.valueProperty().addListener((obs, oldV, newV) -> {
            LogistikFelder.setValue(fields, key, newV == null ? "" : newV.name());
            onChanged.run();
        });
        grid.addRow(row, new Label(label), box);
        return box;
    }
}
