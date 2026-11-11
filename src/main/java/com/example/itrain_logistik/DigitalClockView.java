package com.example.itrain_logistik;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Digitale Modellbahnuhr, rechtsbuendig im Ribbon (siehe Projektauftrag:
 * "rechtsbuendig im Ribbon, eine digitale Uhr") - und, in einer zweiten
 * Instanz, im eigenstaendigen {@link UhrWindow}. Zeigt nur noch an, was
 * {@link LaufendeUhr} (die EINE programmweite laufende Uhr) gerade
 * ausrechnet; haelt selbst keinen eigenen Zeitstand mehr (siehe dortigen
 * Klassenkommentar zum Grund: unterschiedliche Uhrzeiten zwischen Ribbon und
 * Uhr-Fenster).
 */
public final class DigitalClockView extends Label {

    private static final DateTimeFormatter WITH_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.GERMAN);
    private static final DateTimeFormatter WITHOUT_SECONDS = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN);

    private final Timeline timeline;
    private boolean showSeconds = false;

    public DigitalClockView() {
        getStyleClass().add("digital-clock");
        setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 16px; -fx-padding: 0 10 0 0;");
        timeline = new Timeline(new KeyFrame(Duration.millis(200), e -> tick()));
        timeline.setCycleCount(Animation.INDEFINITE);
        setVisible(false);
        setManaged(false);
    }

    /**
     * Setzt {@link LaufendeUhr} auf frisch aus der .tcdz gelesene Werte
     * zurueck und zeigt sie an - nur fuer die EINE Stelle gedacht, an der
     * wirklich neue Daten vorliegen (Ribbon-Uhr in MainController bei
     * Programmstart/Oeffnen/Neu anlegen/Abgleichen). Fuer alles andere (z.B.
     * das Uhr-Fenster) siehe {@link #folgeLaufenderUhr()}.
     */
    public void setClock(ClockData clock) {
        LaufendeUhr.setzen(clock);
        starteAnzeige();
    }

    /**
     * Zeigt die bereits laufende, gemeinsame {@link LaufendeUhr} an, OHNE
     * sie zurueckzusetzen - fuer sekundaere Ansichten wie {@link UhrWindow},
     * die keine eigene, moeglicherweise veraltete Zeitquelle mitbringen
     * duerfen.
     */
    public void folgeLaufenderUhr() {
        starteAnzeige();
    }

    private void starteAnzeige() {
        if (!LaufendeUhr.istAktiv()) {
            stop();
            return;
        }
        setVisible(true);
        setManaged(true);
        timeline.play();
        tick();
    }

    public void stop() {
        timeline.stop();
        setVisible(false);
        setManaged(false);
        setText("");
    }

    public void setShowSeconds(boolean showSeconds) {
        this.showSeconds = showSeconds;
        tick();
    }

    private void tick() {
        LocalTime zeit = LaufendeUhr.aktuelleZeit();
        if (zeit == null) {
            return;
        }
        setText(zeit.format(showSeconds ? WITH_SECONDS : WITHOUT_SECONDS));
    }
}
