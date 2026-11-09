package com.example.itrain_logistik;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Strukturierte Logistik-Zusatzdaten fuer eine Lokomotive: Antriebsart sowie
 * die davon abhaengigen Verbrauchsangaben (siehe Projektauftrag "Beim
 * Verbrauch, bei Diesel, Wasserstoff gibt es eben eine Information
 * 'Verbrauch l/100 km (Vorbild)' Da muss 'Tankinhalt Liter' als Datenfeld
 * dazu. Bei Elektrisch oder Batterie kein Verbrauch. Bei Dampflokomotiven
 * gibt es 'Wasservorrat Kubikmeter' und 'Kohlevorrat Tonnen'." sowie
 * spaeter ergaenzt "Bei den Dampflokomotiven muss dazu: Kohleverbrauch
 * kg/km und Wasserverbrauch Liter/km"): Diesel/Wasserstoff zeigen Verbrauch
 * auf 100km + Tankinhalt, Dampf zeigt Wasser-/Kohlevorrat UND die beiden
 * Verbrauchsraten Kohle/Wasser je km, Elektrisch/Batterie zeigen keines
 * dieser Felder. Die Zeilen bauen sich neu auf, sobald sich die Antriebsart
 * aendert. Maßstab und Modellbahn-Faktor sind von der Antriebsart
 * unabhaengig, stehen immer darunter und werden - solange fuer diese
 * Lokomotive noch kein eigener Wert gesetzt ist - mit der in den
 * Einstellungen hinterlegten Vorlage vorbelegt (siehe Projektauftrag "Den
 * Maßstab sowie den Modellbahn-Faktor setzen wir in den Einstellungen. Das
 * ist dann eine Vorlage für alle Daten, kann aber geändert werden.").
 * Persistiert wie gehabt ueber die generischen {@link CustomField}e der Lok
 * (siehe {@link LogistikFelder}).
 */
public final class LocomotiveLogistikPane extends VBox {

    private final ObservableList<CustomField> fields;
    private final Runnable onChanged;
    private final GridPane grid = new GridPane();

    public LocomotiveLogistikPane(ObservableList<CustomField> fields, Runnable onChanged) {
        this.fields = fields;
        this.onChanged = onChanged;
        setSpacing(10);
        setPadding(new Insets(0, 0, 10, 0));
        grid.setHgap(10);
        grid.setVgap(8);
        getChildren().addAll(new Label("Logistikdaten"), grid);
        rebuild();
    }

    private void rebuild() {
        grid.getChildren().clear();
        int row = 0;

        LogistikFeldRow.enumCombo(grid, row++, "Antriebsart", fields, LogistikFelder.ANTRIEBSART,
                LokAntriebsart.values(), LokAntriebsart::getLabel, () -> {
                    onChanged.run();
                    rebuild();
                });

        LokAntriebsart antrieb = LogistikFelder.parseEnum(
                LogistikFelder.getValue(fields, LogistikFelder.ANTRIEBSART), LokAntriebsart.values());
        if (antrieb == LokAntriebsart.DAMPF) {
            LogistikFeldRow.text(grid, row++, "Wasservorrat Kubikmeter", fields, LogistikFelder.WASSERVORRAT_M3, onChanged);
            LogistikFeldRow.text(grid, row++, "Kohlevorrat Tonnen", fields, LogistikFelder.KOHLEVORRAT_T, onChanged);
            LogistikFeldRow.text(grid, row++, "Kohleverbrauch kg/km", fields, LogistikFelder.KOHLEVERBRAUCH_KG_KM, onChanged);
            LogistikFeldRow.text(grid, row++, "Wasserverbrauch Liter/km", fields, LogistikFelder.WASSERVERBRAUCH_L_KM, onChanged);
        } else if (antrieb == LokAntriebsart.DIESEL || antrieb == LokAntriebsart.WASSERSTOFF) {
            LogistikFeldRow.text(grid, row++, "Verbrauch auf 100km (Vorbild)", fields, LogistikFelder.VERBRAUCH_100KM, onChanged);
            LogistikFeldRow.text(grid, row++, "Tankinhalt Liter", fields, LogistikFelder.TANKINHALT_LITER, onChanged);
        }
        // Elektrisch/Batterie (und noch unbestimmte Antriebsart): keine Verbrauchszeile.

        AppSettings settings = AppSettings.getInstance();
        LogistikFeldRow.textWithDefault(grid, row++, "Maßstab (1:x)", fields, LogistikFelder.MASSSTAB,
                settings.getDefaultMassstab(), onChanged);
        LogistikFeldRow.textWithDefault(grid, row, "Modellbahn-Faktor", fields, LogistikFelder.MODELLBAHN_FAKTOR,
                settings.getDefaultModellbahnFaktor(), onChanged);
    }
}
