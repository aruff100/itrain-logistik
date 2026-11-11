module com.example.itrain_logistik {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    // Lesen der .tcdz-Dateien (ZIP mit einer .tcd-XML-Datei drin) und der
    // eigenen Logistik-Datendatei.
    requires java.xml;
    // AppSettings (Voreinstellungen dauerhaft ueber die Java-Preferences-API).
    requires java.prefs;
    // Handbuch/Hilfetexte im Standardprogramm oeffnen (Desktop.open),
    // Update-Check-Link im Browser oeffnen (Desktop.browse).
    requires java.desktop;
    // Eingebauter Webserver für die Browser-Ansicht der Module (Anwender 09.11.2026).
    requires jdk.httpserver;
    // Auf Update prüfen: GitHub-Releases abfragen (Anwender 11.11.2026).
    requires java.net.http;

    opens com.example.itrain_logistik to javafx.fxml;
    exports com.example.itrain_logistik;
    exports com.example.itrain_logistik.kern;
}
