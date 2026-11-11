package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;

/**
 * "Jeder Text kann in einer 'Formatierung' angepasst werden, siehe Bild."
 * (Projektauftrag) - bildet den vom Anwender mitgelieferten Screenshot
 * moeglichst wortgetreu nach: Schrift (Name/Groesse/Fett/Kursiv),
 * Ausrichtung (Horizontal/Vertikal), Textrichtung, Farbe, Vorschau,
 * OK/Abbrechen. Schreibt bei OK direkt in das uebergebene {@link NetzText}
 * zurueck; liefert true, wenn der Anwender mit OK bestaetigt hat.
 */
final class FormatierungDialog {

    /** Kleine, feste Farbpalette - der Screenshot zeigt nur "Weiß" als gewaehlten Wert, keine vollstaendige Liste. */
    private static final String[][] FARBEN = {
            {"Weiß", "#FFFFFF"}, {"Schwarz", "#000000"}, {"Rot", "#CC2222"}, {"Grün", "#2F8F4E"},
            {"Blau", "#2255AA"}, {"Gelb", "#D4B106"}, {"Grau", "#888888"}, {"Orange", "#D07A22"},
    };

    private FormatierungDialog() {
    }

    static boolean bearbeiten(Stage owner, NetzText text) {
        Dialog<ButtonType> dialog = new Dialog<>();
        Fenster.merken(dialog, "formatierung"); // Größe merken (Anwender 22.10.2026)
        dialog.initOwner(owner);
        dialog.setTitle("Formatierung");
        DialogKnoepfe.setze(dialog.getDialogPane());
        dialog.getDialogPane().setContent(buildContent(dialog, text));
        applyThemeOnceShown(dialog);

        return dialog.showAndWait().filter(bt -> bt == DialogKnoepfe.SPEICHERN).isPresent();
    }

    private static javafx.scene.Node buildContent(Dialog<ButtonType> dialog, NetzText text) {
        TextArea inhalt = new TextArea(text.getText());
        inhalt.setPrefRowCount(4);
        inhalt.setWrapText(true);

        ComboBox<String> schriftName = new ComboBox<>(FXCollections.observableArrayList(Font.getFamilies()));
        schriftName.setValue(text.getSchriftart());
        schriftName.setEditable(false);

        Spinner<Integer> schriftGroesse = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                6, 96, (int) Math.round(text.getSchriftgroesse())));
        schriftGroesse.setEditable(true);
        schriftGroesse.setPrefWidth(70);

        CheckBox fett = new CheckBox("Fett");
        fett.setSelected(text.isFett());
        CheckBox kursiv = new CheckBox("Kursiv");
        kursiv.setSelected(text.isKursiv());

        ToggleGroup horizontalGruppe = new ToggleGroup();
        RadioButton linksbuendig = radio("Linksbündig", horizontalGruppe, NetzText.Horizontal.LINKSBUENDIG);
        RadioButton hZentriert = radio("Zentriert", horizontalGruppe, NetzText.Horizontal.ZENTRIERT);
        RadioButton rechtsbuendig = radio("Rechtsbündig", horizontalGruppe, NetzText.Horizontal.RECHTSBUENDIG);
        waehleToggle(horizontalGruppe, text.getHorizontal());

        ToggleGroup vertikalGruppe = new ToggleGroup();
        RadioButton oben = radio("Oben", vertikalGruppe, NetzText.Vertikal.OBEN);
        RadioButton vZentriert = radio("Zentriert", vertikalGruppe, NetzText.Vertikal.ZENTRIERT);
        RadioButton unten = radio("Unten", vertikalGruppe, NetzText.Vertikal.UNTEN);
        waehleToggle(vertikalGruppe, text.getVertikal());

        ComboBox<NetzText.Textrichtung> textrichtung = new ComboBox<>(
                FXCollections.observableArrayList(NetzText.Textrichtung.values()));
        textrichtung.setValue(text.getTextrichtung());
        textrichtung.setCellFactory(list -> textrichtungZelle());
        textrichtung.setButtonCell(textrichtungZelle());

        ComboBox<String> farbe = new ComboBox<>(FXCollections.observableArrayList(namen()));
        farbe.setValue(namenFuerHex(text.getFarbe()));

        CheckBox rahmen = new CheckBox("Rahmen anzeigen");
        rahmen.setSelected(text.isRahmen());

        StackPane vorschauFlaeche = new StackPane();
        vorschauFlaeche.setPrefSize(220, 70);
        vorschauFlaeche.setStyle("-fx-background-color: #2b2b2b; -fx-background-radius: 4;");
        Label vorschauText = new Label("ABCdef123");
        vorschauFlaeche.getChildren().add(vorschauText);

        Runnable aktualisiereVorschau = () -> {
            vorschauText.setText(inhalt.getText().isBlank() ? "ABCdef123" : inhalt.getText());
            FontWeight gewicht = fett.isSelected() ? FontWeight.BOLD : FontWeight.NORMAL;
            FontPosture haltung = kursiv.isSelected() ? FontPosture.ITALIC : FontPosture.REGULAR;
            vorschauText.setFont(Font.font(schriftName.getValue(), gewicht, haltung, schriftGroesse.getValue()));
            vorschauText.setTextFill(Color.web(hexFuerNamen(farbe.getValue())));
            vorschauText.setStyle(rahmen.isSelected() ? "-fx-border-color: #cccccc; -fx-border-width: 1;" : "");
        };
        inhalt.textProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        schriftName.valueProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        schriftGroesse.valueProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        fett.selectedProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        kursiv.selectedProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        farbe.valueProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        rahmen.selectedProperty().addListener((obs, o, n) -> aktualisiereVorschau.run());
        aktualisiereVorschau.run();

        GridPane schriftGrid = new GridPane();
        schriftGrid.setHgap(8);
        schriftGrid.setVgap(6);
        schriftGrid.add(new Label("Name:"), 0, 0);
        schriftGrid.add(schriftName, 1, 0);
        schriftGrid.add(new Label("Größe:"), 2, 0);
        schriftGrid.add(schriftGroesse, 3, 0);
        schriftGrid.add(new HBox(10, fett, kursiv), 0, 1, 4, 1);
        VBox schriftBox = titledBox("Schrift", schriftGrid);

        HBox horizontalZeile = new HBox(10, linksbuendig, hZentriert, rechtsbuendig);
        HBox vertikalZeile = new HBox(10, oben, vZentriert, unten);
        GridPane ausrichtungGrid = new GridPane();
        ausrichtungGrid.setHgap(8);
        ausrichtungGrid.setVgap(6);
        ausrichtungGrid.add(new Label("Horizontal:"), 0, 0);
        ausrichtungGrid.add(horizontalZeile, 1, 0);
        ausrichtungGrid.add(new Label("Vertikal:"), 0, 1);
        ausrichtungGrid.add(vertikalZeile, 1, 1);
        VBox ausrichtungBox = titledBox("Ausrichtung", ausrichtungGrid);

        HBox textrichtungZeile = new HBox(8, new Label("Textrichtung:"), textrichtung);
        textrichtungZeile.setAlignment(Pos.CENTER_LEFT);

        HBox farbeZeile = new HBox(8, new Label("Farbe:"), farbe, rahmen);
        farbeZeile.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, new Label("Text:"), inhalt, schriftBox, ausrichtungBox,
                textrichtungZeile, farbeZeile, new Label("Vorschau:"), vorschauFlaeche);
        root.setPadding(new Insets(10));
        root.setPrefWidth(420);

        dialog.setResultConverter(bt -> {
            if (bt == DialogKnoepfe.SPEICHERN) {
                text.setText(inhalt.getText());
                text.setSchriftart(schriftName.getValue());
                text.setSchriftgroesse(schriftGroesse.getValue());
                text.setFett(fett.isSelected());
                text.setKursiv(kursiv.isSelected());
                text.setHorizontal(gewaehlt(horizontalGruppe, NetzText.Horizontal.LINKSBUENDIG));
                text.setVertikal(gewaehlt(vertikalGruppe, NetzText.Vertikal.OBEN));
                text.setTextrichtung(textrichtung.getValue());
                text.setFarbe(hexFuerNamen(farbe.getValue()));
                text.setRahmen(rahmen.isSelected());
            }
            return bt;
        });

        return root;
    }

    private static VBox titledBox(String titel, javafx.scene.Node inhalt) {
        Label titelLabel = new Label(titel);
        titelLabel.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(4, titelLabel, inhalt);
        return box;
    }

    private static RadioButton radio(String text, ToggleGroup gruppe, Object userData) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(gruppe);
        rb.setUserData(userData);
        return rb;
    }

    private static void waehleToggle(ToggleGroup gruppe, Object wert) {
        for (Toggle t : gruppe.getToggles()) {
            if (t.getUserData() == wert) {
                gruppe.selectToggle(t);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E> E gewaehlt(ToggleGroup gruppe, E fallback) {
        Toggle gewaehlt = gruppe.getSelectedToggle();
        return gewaehlt != null ? (E) gewaehlt.getUserData() : fallback;
    }

    private static javafx.scene.control.ListCell<NetzText.Textrichtung> textrichtungZelle() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(NetzText.Textrichtung item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getLabel());
            }
        };
    }

    private static List<String> namen() {
        List<String> namen = new java.util.ArrayList<>();
        for (String[] f : FARBEN) {
            namen.add(f[0]);
        }
        return namen;
    }

    private static String hexFuerNamen(String name) {
        for (String[] f : FARBEN) {
            if (f[0].equals(name)) {
                return f[1];
            }
        }
        return "#FFFFFF";
    }

    private static String namenFuerHex(String hex) {
        for (String[] f : FARBEN) {
            if (f[1].equalsIgnoreCase(hex)) {
                return f[0];
            }
        }
        return "Weiß";
    }

    private static void applyThemeOnceShown(Dialog<?> dialog) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, AppSettings.getInstance().getTheme()));
    }
}
