package com.example.itrain_logistik;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * "Fuer die Uhr als Eigenstaendiges Fenster hinzu. Setze einen Knopf in
 * Ribbon 'Uhr' Und unter Funktionen." (Projektauftrag) - eigenstaendiges
 * Fenster mit der digitalen Uhr, zusaetzlich zur im Ribbon eingebetteten
 * {@link DigitalClockView}. Bekommt eine eigene DigitalClockView-Instanz (ein
 * JavaFX-Node kann nicht gleichzeitig in zwei Szenen stehen), folgt darueber
 * aber nur der EINEN gemeinsamen {@link LaufendeUhr} (siehe dortigen
 * Klassenkommentar: "Die Uhr im Ribbon und die Uhr im Fenster zeigen
 * unterschiedliche Uhrzeiten an" - Ursache war, dass dieses Fenster frueher
 * beim Oeffnen einen eigenen, moeglicherweise laengst veralteten Zeitstand
 * neu gestartet hat). Braucht deshalb auch kein manuelles refreshIfOpen nach
 * "Abgleichen" mehr - {@link LaufendeUhr} wird zentral in MainController
 * zurueckgesetzt, dieses Fenster liest sie nur noch laufend ab.
 */
public final class UhrWindow {

    private static Stage stage;
    private static DigitalClockView clockView;

    private UhrWindow() {
    }

    public static void show(Stage owner, boolean showSeconds) {
        if (stage != null && stage.isShowing()) {
            clockView.setShowSeconds(showSeconds);
            clockView.folgeLaufenderUhr();
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Uhr");
        stage.initOwner(owner);

        clockView = new DigitalClockView();
        clockView.setStyle(clockView.getStyle() + "-fx-font-size: 48px; -fx-padding: 0;");
        clockView.setShowSeconds(showSeconds);
        clockView.folgeLaufenderUhr();

        StackPane root = new StackPane(clockView);
        root.setAlignment(Pos.CENTER);
        Fenster.szene(stage, root, 320, 160);
        WindowState.apply(stage, "uhr", 320, 160);
        stage.setOnHidden(e -> {
            stage = null;
            clockView = null;
        });
        stage.show();
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
            clockView = null;
        }
    }
}
