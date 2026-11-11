package com.example.itrain_logistik;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Fenster- und Dialoggrößen bleiben, wie der Anwender sie eingestellt hat (Anwender 22.10.2026: "Merke Dir alle
 * Fenstergrößen oder Dialoggrößen").
 * <p>
 * {@link #szene} setzt beim Neuaufbau eines offenen Fensters nur den Inhalt neu, statt eine neue Szene mit
 * Standardgröße zu setzen – sonst sprang z. B. das Lokfenster nach jedem Speichern auf 760×480 zurück.
 * {@link #merken(Dialog, String)} stellt bei Dialogen die zuletzt benutzte Größe her und sichert sie beim Schließen
 * (Schlüssel "dialog.&lt;name&gt;" in {@link AppSettings}, wie {@link WindowState}).
 */
public final class Fenster {

    private Fenster() {
    }

    /** Erster Aufbau: neue Szene in Standardgröße mit Farbschema; danach nur neuer Inhalt, Größe bleibt. */
    public static void szene(Stage stage, Parent inhalt, double breite, double hoehe) {
        Scene vorhanden = stage.getScene();
        if (vorhanden != null) {
            vorhanden.setRoot(inhalt);
            return;
        }
        Scene scene = new Scene(inhalt, breite, hoehe);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
    }

    /** Größe eines Dialogs merken und beim nächsten Öffnen wiederherstellen. */
    public static void merken(Dialog<?> d, String name) {
        String schluessel = "dialog." + name;
        Window[] fenster = new Window[1];
        d.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, e -> {
            Window w = fensterVon(d);
            fenster[0] = w;
            if (w == null) {
                return;
            }
            double[] g = groesse(AppSettings.getInstance().getWindowState(schluessel));
            if (g != null) {
                w.setWidth(Math.max(w.getWidth() * 0.5, g[0]));
                w.setHeight(Math.max(w.getHeight() * 0.5, g[1]));
            }
            mindestens(d, w);
            // Unter Windows erschien ein neuer Dialog („Übergabe/Modul bearbeiten“) nur als schmaler Balken
            // (Anwender 09.11.2026); nach dem ersten Layout noch einmal prüfen.
            javafx.application.Platform.runLater(() -> mindestens(d, w));
            // und noch einmal, wenn alles gezeichnet ist (ScrollPane kennt ihre Vorzugshöhe erst mit Skin)
            javafx.animation.PauseTransition spaeter = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
            spaeter.setOnFinished(x -> mindestens(d, w));
            spaeter.play();
        });
        d.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_HIDDEN, e -> {
            // Beim Schließen kann der DialogPane schon aus seiner Szene gelöst sein (Fehler 09.11.2026 im Dialog
            // „Verbindung zu iTrain“): dann das beim Anzeigen gemerkte Fenster nehmen.
            Window w = fensterVon(d) != null ? fensterVon(d) : fenster[0];
            if (w != null && w.getWidth() > 50 && w.getHeight() > 50) {
                AppSettings.getInstance().setWindowState(schluessel, w.getWidth() + ";" + w.getHeight());
            }
        });
    }

    /** Dialog mindestens so groß wie sein Inhalt (höchstens Bildschirm), sonst neu bemessen und mittig. */
    private static void mindestens(Dialog<?> d, Window w) {
        if (w == null || !w.isShowing()) {
            return;
        }
        javafx.geometry.Rectangle2D schirm = javafx.stage.Screen.getPrimary().getVisualBounds();
        double pw = Math.min(schirm.getWidth(), Math.max(380, d.getDialogPane().prefWidth(-1) + 30));
        double ph = Math.min(schirm.getHeight() * 0.7, Math.max(180, d.getDialogPane().prefHeight(pw) + 50));
        boolean zuKlein = w.getWidth() < 300 || w.getHeight() < 140;
        boolean weg = javafx.stage.Screen.getScreensForRectangle(w.getX(), w.getY(), Math.max(1, w.getWidth()),
                Math.max(1, w.getHeight())).isEmpty();
        if (zuKlein) {
            w.setWidth(pw);
            w.setHeight(ph);
        } else {
            // Nie größer als der Inhalt öffnen (Anwender 10.11.2026: „auf den Inhalt verkleinern“); eine gemerkte
            // kleinere Größe bleibt.
            javafx.scene.Scene sc = d.getDialogPane().getScene();
            double randB = sc == null ? 16 : w.getWidth() - sc.getWidth();
            double randH = sc == null ? 40 : w.getHeight() - sc.getHeight();
            if (randB < 0 || randB > 40) {
                randB = 16; // Szene noch nicht auf Fenstergröße: übliche Rahmenbreite annehmen
            }
            if (randH < 0 || randH > 80) {
                randH = 40;
            }
            double bw = Math.min(schirm.getWidth(), d.getDialogPane().prefWidth(-1) + randB);
            if (w.getWidth() > bw + 1) {
                w.setWidth(Math.max(300, bw));
            }
            double bh = Math.min(schirm.getHeight(), d.getDialogPane().prefHeight(w.getWidth() - randB) + randH);
            if (w.getHeight() > bh + 1) {
                w.setHeight(Math.max(140, bh));
            }
        }
        if (zuKlein || weg) {
            w.centerOnScreen();
        }
    }

    private static Window fensterVon(Dialog<?> d) {
        javafx.scene.Scene sc = d.getDialogPane() == null ? null : d.getDialogPane().getScene();
        return sc == null ? null : sc.getWindow();
    }

    private static double[] groesse(String text) {
        if (text == null || !text.contains(";")) {
            return null;
        }
        try {
            String[] t = text.split(";");
            return new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
