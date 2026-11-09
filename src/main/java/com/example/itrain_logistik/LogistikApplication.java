package com.example.itrain_logistik;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Einstiegspunkt der JavaFX-Anwendung - laedt {@code main-view.fxml}, setzt
 * Titel/Icon, installiert Farbschema und Fenster-/Dialogplatzierung
 * (gleiches Vorgehen wie {@code HelloApplication} in iTrain_import_export).
 */
public class LogistikApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        ThemeManager.install();
        // Sprache der Oberfläche (Anwender 09.11.2026): vor dem Laden der FXML, damit alles übersetzt wird
        Uebersetzung.laden(I18n.getInstance().getCurrentLanguage());
        Uebersetzung.installieren();

        FXMLLoader fxmlLoader = new FXMLLoader(LogistikApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());

        stage.setTitle("iTrain-Logistik");
        stage.getIcons().addAll(loadAppIcons());
        stage.setScene(scene);

        // "Wenn das Programm beendet wird, kommt eine Nachfrage ob die
        // Aenderungen gespeichert werden sollen." - gilt auch fuer den
        // Schliessen-Knopf (X) des Fensters, nicht nur fuer "Datei > Beenden".
        MainController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(controller::onWindowCloseRequested);

        DialogPlacement.install(stage);
        WindowState.apply(stage, "main", 1000, 700);

        stage.show();
    }

    private static Image[] loadAppIcons() {
        try (InputStream in = LogistikApplication.class.getResourceAsStream("app-icon.png")) {
            return in == null ? new Image[0] : new Image[]{new Image(in)};
        } catch (IOException ex) {
            return new Image[0];
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
