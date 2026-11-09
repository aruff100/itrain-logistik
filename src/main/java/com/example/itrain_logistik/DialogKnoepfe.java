package com.example.itrain_logistik;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;

/**
 * Einheitliche Knöpfe aller Bearbeitungsdialoge (Anwender 12.10.2026): links "Abbruch", rechts "Speichern",
 * unabhängig von der Knopfreihenfolge des Betriebssystems.
 */
public final class DialogKnoepfe {

    public static final ButtonType ABBRUCH = new ButtonType("Abbruch", ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final ButtonType SPEICHERN = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);

    private DialogKnoepfe() {
    }

    /** Setzt "Abbruch" und "Speichern" (in dieser Reihenfolge) als einzige Knöpfe des Dialogs. */
    public static void setze(DialogPane pane) {
        pane.getButtonTypes().setAll(ABBRUCH, SPEICHERN);
        Node bar = pane.lookup(".button-bar");
        if (bar instanceof ButtonBar b) {
            b.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
        }
    }
}
