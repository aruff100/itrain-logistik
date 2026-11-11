package com.example.itrain_logistik;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.InputStream;

/**
 * „Über iTrain-Logistik“ (Anwender 11.11.2026, wie in iTrain Import/Export): Programmsymbol und Name, Autor,
 * Kontakt-Link (öffnet das Mailprogramm), Version mit Build, Lizenz, Hinweis zu den Übersetzungen und ein Knopf mit
 * den Lizenzhinweisen der fremden Bestandteile.
 */
public final class UeberDialog {

    private UeberDialog() {
    }

    public static void zeige(Stage owner) {
        Label name = new Label("iTrain-Logistik");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        try (InputStream in = UeberDialog.class.getResourceAsStream("app-icon.png")) {
            if (in != null) {
                ImageView iv = new ImageView(new Image(in, 16, 16, true, true));
                name.setGraphic(iv);
                name.setContentDisplay(ContentDisplay.LEFT);
                name.setGraphicTextGap(6);
            }
        } catch (Exception ignored) {
            // ohne Symbol
        }
        Label autor = new Label("Andre Ruff");
        Hyperlink kontakt = new Hyperlink("Kontakt");
        kontakt.setOnAction(e -> DateienFenster.mailOeffnen(owner));
        Label version = new Label("Version " + AppInfo.getBuildNumber());
        Label lizenz = new Label("Copyright © 2026 Andre Ruff. Freie Software unter der GNU General Public License, "
                + "Version 3 (GPL-3.0). Ohne Gewährleistung.");
        lizenz.setWrapText(true);
        lizenz.setMaxWidth(340);
        lizenz.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Label uebersetzung = new Label("Übersetzungen: Englisch, Niederländisch, Französisch, Spanisch und "
                + "Italienisch mit KI-Unterstützung erstellt.");
        uebersetzung.setWrapText(true);
        uebersetzung.setMaxWidth(340);
        uebersetzung.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        Button details = new Button("Lizenzhinweise");
        details.setOnAction(e -> lizenzen(owner));
        VBox inhalt = new VBox(6, name, autor, kontakt, version, lizenz, uebersetzung, details);
        inhalt.setPadding(new Insets(15));

        Alert d = new Alert(Alert.AlertType.INFORMATION);
        d.initOwner(owner);
        d.setTitle("Über");
        d.setHeaderText(null);
        d.getDialogPane().setContent(inhalt);
        d.getButtonTypes().setAll(ButtonType.CLOSE);
        d.getDialogPane().sceneProperty().addListener((o, a, s) -> {
            if (s != null) {
                ThemeManager.apply(s, AppSettings.getInstance().getTheme());
            }
        });
        d.showAndWait();
    }

    private static void lizenzen(Stage owner) {
        TextArea t = new TextArea("""
                iTrain-Logistik
                Copyright (C) 2026 Andre Ruff
                GNU General Public License, Version 3 (GPL-3.0)
                https://www.gnu.org/licenses/gpl-3.0.html

                Enthaltene fremde Bestandteile
                ------------------------------
                QR-Code-Generator (Java), Version 1.8.0
                Copyright (c) Project Nayuki. MIT-Lizenz.
                https://www.nayuki.io/page/qr-code-generator-library
                Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
                associated documentation files, to deal in the Software without restriction, subject to the
                condition that the copyright notice and this permission notice shall be included in all copies or
                substantial portions of the Software. The Software is provided "as is", without warranty of any kind.

                Geräusche von freesound.org
                Creative Commons Zero (CC0 1.0), gemeinfrei. Quellen siehe geraeusche/Quellen.txt.

                OpenJFX (JavaFX)
                GNU General Public License, Version 2, mit Classpath Exception.
                https://openjdk.org/legal/gplv2+ce.html

                Java-Laufzeitumgebung (BellSoft Liberica JDK, OpenJDK)
                GNU General Public License, Version 2, mit Classpath Exception.
                https://bell-sw.com/
                """);
        t.setEditable(false);
        t.setStyle("-fx-font-family: 'monospaced'; -fx-font-size: 11px;");
        t.setPrefSize(680, 420);
        Dialog<Void> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Lizenzhinweise");
        d.setResizable(true);
        d.getDialogPane().setContent(t);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        d.showAndWait();
    }
}
