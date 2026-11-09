package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;

/**
 * "Ob ein Trigger kommt oder nicht musst DU anzeigen. Ich kann das so nicht
 * sehen. [...] Mit Druck auf dieses Symbol oeffnet sich ein Rx/Tx Fenster
 * das alle Trigger zeile fuer zeile auflistet." (Projektauftrag) -
 * sitzungslebensdauer-langes Protokoll aller vom externen
 * iTrain-Logistik-Simulator empfangenen Trigger, unabhaengig von deren
 * Aktion (nicht nur WARTEN) und unabhaengig von der Verarbeitungs-
 * Deduplizierung in {@link PersonenverkehrSimulator} - hier ist gerade
 * interessant, ueber welchen Weg (XTC oder eingebauter Simulator) ein
 * Trigger ankam. Auf
 * {@value #MAX_EINTRAEGE} Eintraege gedeckelt (neueste zuerst), aeltere
 * fallen automatisch heraus.
 */
final class TriggerLog {

    static final int MAX_EINTRAEGE = 500;

    record Eintrag(String empfangszeit, String quelle, SimulatorTrigger trigger) {
    }

    private final ObservableList<Eintrag> eintraege = FXCollections.observableArrayList();
    /** Während "Anhalten" im Rx/Tx-Fenster gesammelt (Anwender 22.10.2026), bei "Weiter" vorn eingefügt. */
    private final java.util.ArrayDeque<Eintrag> zurueckgehalten = new java.util.ArrayDeque<>();
    private boolean angehalten;

    ObservableList<Eintrag> getEintraege() {
        return eintraege;
    }

    /** {@code quelle}: "Pipe" (Live-TCP) oder "Datei" (Protokolldatei-Poll). */
    void hinzufuegen(SimulatorTrigger trigger, String quelle) {
        if (trigger == null) {
            return;
        }
        Eintrag e = new Eintrag(LocalTime.now().withNano(0).toString(), quelle, trigger);
        if (angehalten) {
            // nur die Anzeige steht; verarbeitet wird das Ereignis trotzdem
            zurueckgehalten.addFirst(e);
            while (zurueckgehalten.size() > MAX_EINTRAEGE) {
                zurueckgehalten.removeLast();
            }
            return;
        }
        eintraege.add(0, e);
        kuerzen();
    }

    boolean istAngehalten() {
        return angehalten;
    }

    int zurueckgehaltenAnzahl() {
        return zurueckgehalten.size();
    }

    void anhalten() {
        angehalten = true;
    }

    void weiter() {
        angehalten = false;
        eintraege.addAll(0, zurueckgehalten);
        zurueckgehalten.clear();
        kuerzen();
    }

    void loeschen() {
        eintraege.clear();
        zurueckgehalten.clear();
    }

    private void kuerzen() {
        if (eintraege.size() > MAX_EINTRAEGE) {
            eintraege.remove(MAX_EINTRAEGE, eintraege.size());
        }
    }
}
