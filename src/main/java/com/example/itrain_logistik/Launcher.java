package com.example.itrain_logistik;

import javafx.application.Application;

/**
 * Eigener Einstiegspunkt statt {@link LogistikApplication} direkt zu starten
 * - gleiches Vorgehen wie im Schwesterprojekt iTrain_import_export
 * (Launcher.java dort), noetig, weil manche JavaFX/jlink-Kombinationen einen
 * Start ueber eine Klasse verlangen, die selbst NICHT von
 * {@code Application} erbt.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(LogistikApplication.class, args);
    }
}
