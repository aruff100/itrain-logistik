package com.example.itrain_logistik;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * Menü Einstellungen > „Verbindung zu iTrain…“ (Anwender 08.11.2026): „iTrain läuft auf diesem Computer“ oder
 * „iTrain läuft auf einem anderen Computer“ mit Adresse, Port der XTC-Schnittstelle und „Verbindung prüfen“
 * (Begrüßung und Zugliste von iTrain). Gespeichert in {@link AppSettings} (Rechner wie bisher
 * {@code itrainRechner}, gilt auch für die Server-Prüfung auf Port 7120).
 *
 * @return true, wenn gespeichert wurde
 */
public final class ItrainVerbindungDialog {

    private ItrainVerbindungDialog() {
    }

    /** Inhalt (Dialog und Voreinstellungen > iTrain Verbindung) und das Speichern der Werte. */
    record Inhalt(javafx.scene.Node knoten, Runnable speichern) {
    }

    public static boolean zeige(Window owner) {
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(owner);
        d.setTitle("Verbindung zu iTrain");
        d.setHeaderText("Wo läuft iTrain?");
        DialogKnoepfe.setze(d.getDialogPane());
        Inhalt i = inhalt(false);
        d.getDialogPane().setContent(i.knoten());
        Fenster.merken(d, "itrainVerbindung");
        if (d.showAndWait().orElse(DialogKnoepfe.ABBRUCH) != DialogKnoepfe.SPEICHERN) {
            return false;
        }
        i.speichern().run();
        return true;
    }

    /**
     * Gemeinsamer Inhalt (Anwender 15.11.2026: Voreinstellungen > „iTrain Verbindung“ zeigt dasselbe wie der Dialog).
     *
     * @param sofort true = jede Änderung gleich speichern (Voreinstellungen), false = erst über {@link Inhalt#speichern}
     */
    static Inhalt inhalt(boolean sofort) {
        AppSettings st = AppSettings.getInstance();
        ToggleGroup gruppe = new ToggleGroup();
        RadioButton hier = new RadioButton("iTrain läuft auf diesem Computer");
        RadioButton anders = new RadioButton("iTrain läuft auf einem anderen Computer");
        hier.setToggleGroup(gruppe);
        anders.setToggleGroup(gruppe);
        String rechner = st.getItrainRechner();
        boolean lokal = rechner == null || rechner.isBlank() || ItrainErkennung.istLokal(rechner.trim());
        TextField adresse = new TextField(lokal ? "" : rechner);
        adresse.setPromptText("z. B. 192.168.0.171 oder Rechnername");
        adresse.setPrefColumnCount(18);
        (lokal ? hier : anders).setSelected(true);
        adresse.disableProperty().bind(hier.selectedProperty());

        Spinner<Integer> port = new Spinner<>(1, 65535, st.getXtcPort());
        port.setEditable(true);
        port.setPrefWidth(110);

        Label ergebnis = new Label();
        ergebnis.setWrapText(true);
        ergebnis.setMaxWidth(520);
        Button pruefen = new Button("Verbindung prüfen");
        MainController.pastell(pruefen, "#d5f5e3");
        pruefen.setOnAction(e -> {
            String host = hier.isSelected() ? "localhost" : adresse.getText().trim();
            if (host.isEmpty()) {
                ergebnis.setStyle("-fx-text-fill: #c0392b;");
                ergebnis.setText("Bitte die Adresse des iTrain-Computers eingeben.");
                return;
            }
            int p = port.getValue();
            pruefen.setDisable(true);
            ergebnis.setStyle("");
            ergebnis.setText("Prüfe " + host + ":" + p + " …");
            Thread.ofVirtual().name("XTC-Pruefung").start(() -> {
                XtcVerbindung.Pruefung r = XtcVerbindung.pruefe(host, p, 2500);
                Platform.runLater(() -> {
                    pruefen.setDisable(false);
                    if (r.erreichbar()) {
                        ergebnis.setStyle("-fx-text-fill: #2e9e3e;");
                        ergebnis.setText("✔ " + r.version() + " läuft auf " + host + " (XML-TrainControl-Port " + p + "), "
                                + r.zuege().size() + " Züge: " + String.join(", ", r.zuege()));
                    } else {
                        ergebnis.setStyle("-fx-text-fill: #c0392b;");
                        ergebnis.setText("✘ Keine Antwort von iTrain auf " + host + ":" + p + " (" + r.fehler() + ").\n"
                                + "Läuft iTrain dort? Ist die Windows-Firewall auf dem iTrain-Computer für Port " + p
                                + " eingehend offen? Stimmen Adresse und Port?");
                    }
                });
            });
        });

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.add(hier, 0, 0, 2, 1);
        g.add(anders, 0, 1, 2, 1);
        g.add(new Label("Adresse:"), 0, 2);
        g.add(adresse, 1, 2);
        g.add(new Label("XML-TrainControl-Port:"), 0, 3);
        g.add(new HBox(8, port, new Label("(Vorgabe " + XtcVerbindung.PORT_VORGABE + ")")), 1, 3);
        GridPane.setMargin(adresse, new Insets(0, 0, 0, 0));
        Label hinweis = new Label("Über XML TrainControl meldet iTrain je Zug Einfahrt in einen Block, Warten mit Wartezeit und "
                + "Abfahrt sowie die Modellzeit. Läuft iTrain auf einem anderen Rechner, muss dessen Firewall den XML-TrainControl-Port "
                + "eingehend erlauben.");
        hinweis.setWrapText(true);
        hinweis.setMaxWidth(520);
        hinweis.setStyle("-fx-opacity: 0.8;");
        VBox inhalt = new VBox(10, g, new HBox(8, pruefen), ergebnis, hinweis);
        inhalt.setPadding(new Insets(15));
        Runnable speichern = () -> {
            String host = hier.isSelected() || adresse.getText().isBlank() ? "localhost" : adresse.getText().trim();
            st.setItrainRechner(host);
            st.setXtcPort(port.getValue());
        };
        if (sofort) {
            hier.selectedProperty().addListener((o, a, b) -> speichern.run());
            adresse.textProperty().addListener((o, a, b) -> speichern.run());
            port.valueProperty().addListener((o, a, b) -> speichern.run());
            Label gilt = new Label("Änderungen gelten nach dem Schließen der Voreinstellungen.");
            gilt.setStyle("-fx-opacity: 0.8;");
            inhalt.getChildren().add(gilt);
        }
        return new Inhalt(inhalt, speichern);
    }
}
