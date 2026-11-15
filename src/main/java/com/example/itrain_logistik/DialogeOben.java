package com.example.itrain_logistik;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Alle Dialoge in der Größe veränderbar und immer zuoberst (Anwender 15.11.2026: unter macOS waren Dialoge fest und zu
 * klein; ein Anwender fand Dialoge hinter dem Hauptfenster). Beobachtet alle Fenster wie {@link Uebersetzung}:
 * jedes neue Dialogfenster (modal oder mit {@link DialogPane}) wird größenveränderbar, kommt nach vorn, und ohne
 * Besitzerfenster bleibt es über allen anderen Fenstern.
 */
final class DialogeOben {

    private DialogeOben() {
    }

    static void installieren() {
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Window w : c.getAddedSubList()) {
                        if (w instanceof Stage s) {
                            einrichten(s);
                        }
                    }
                }
            }
        });
    }

    private static void einrichten(Stage s) {
        boolean dialog = s.getModality() != Modality.NONE
                || s.getScene() != null && s.getScene().getRoot() instanceof DialogPane;
        if (!dialog) {
            return;
        }
        try {
            s.setResizable(true);
            if (s.getOwner() == null) {
                s.setAlwaysOnTop(true); // ohne Besitzer konnte er hinter das Hauptfenster rutschen
            }
        } catch (RuntimeException ex) {
            // gebundene Eigenschaft: Dialog hat seine Größe selbst festgelegt
        }
        Platform.runLater(() -> {
            if (s.isShowing()) {
                // Größe merken auch für alle Dialoge ohne eigenen Namen (Anwender 15.11.2026): je Titel und Kopfzeile
                if (s.getScene() != null && s.getScene().getRoot() instanceof DialogPane dp
                        && !dp.getProperties().containsKey(Fenster.GEMERKT)) {
                    String kopf = dp.getHeaderText() == null ? "" : dp.getHeaderText();
                    Fenster.verwalten(s, dp, "dialog2.auto." + s.getTitle() + "|"
                            + (kopf.length() > 80 ? kopf.substring(0, 80) : kopf));
                }
                s.toFront();
                s.requestFocus();
            }
        });
    }
}
