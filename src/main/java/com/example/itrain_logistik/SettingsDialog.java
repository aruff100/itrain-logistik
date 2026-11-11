package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * "Voreinstellungen" mit drei Reitern - Ansicht, Uhr, Pfade - erreichbar
 * ueber das Menue "Einstellungen". Struktur (Reiter "Ansicht"/"Pfade")
 * wortgetreu von {@code SettingsDialog} in iTrain_import_export
 * uebernommen, wie im Projektauftrag verlangt ("Das Projekt uebernimmt die
 * Voreinstellungen von iTrain_import_export"); der Reiter "Uhr" ist neu
 * (digitale Uhr im Ribbon, siehe {@link DigitalClockView}).
 */
public final class SettingsDialog {

    private SettingsDialog() {
    }

    /** Verknüpfte .tcdz der offenen Konfiguration (setzt das Hauptfenster), für den Reiter Vorlagen. */
    public static java.util.function.Supplier<java.io.File> tcdzQuelle = () -> null;
    /** Geöffnete Anlage für den Reiter „Reiter“ (Anwender 09.11.2026) und Rückmeldung nach Änderungen. */
    public static java.util.function.Supplier<com.example.itrain_logistik.kern.Anlage> anlageQuelle = () -> null;
    public static Runnable reiterGeaendert = () -> { };

    /** Werte aus der iTrain-Datei: Maßstab, Spurweite, Uhr-Faktor, Anzeigefaktor (null, wenn nicht lesbar). */
    static java.util.Map<String, String> itrainWerte(java.io.File tcdz) {
        if (tcdz == null || !tcdz.isFile()) {
            return null;
        }
        String text;
        try {
            if (tcdz.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".tcd")) {
                text = java.nio.file.Files.readString(tcdz.toPath());
            } else {
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(tcdz)) {
                    java.util.zip.ZipEntry e = z.entries().nextElement();
                    text = new String(z.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ex) {
            return null;
        }
        java.util.Map<String, String> w = new java.util.LinkedHashMap<>();
        java.util.function.BiConsumer<String, String> such = (name, regex) -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
            if (m.find()) {
                w.put(name, m.group(1));
            }
        };
        such.accept("massstab", "<settings>\\s*<scale>([0-9.]+)</scale>");
        such.accept("spurweite", "<train[^>]*gauge=\"([^\"]+)\"");
        such.accept("uhrfaktor", "<clock[^>]*>\\s*<configuration[^>]*factor=\"([^\"]+)\"");
        such.accept("anzeigefaktor", "<speeds[^>]*factor=\"([^\"]+)\"");
        return w;
    }

    public static void showPreferences(Stage owner, Runnable onClockSettingsChanged) {
        I18n i18n = I18n.getInstance();
        AppSettings settings = AppSettings.getInstance();

        Dialog<Void> dialog = new Dialog<>();

        Fenster.merken(dialog, "voreinstellungen"); // Größe merken (Anwender 22.10.2026)
        dialog.initOwner(owner);
        dialog.setTitle(i18n.t("menu.preferences"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab viewTab = new Tab(i18n.t("menu.settingsView"), buildViewContent(owner, settings, i18n));
        Tab clockTab = new Tab(i18n.t("menu.settingsClock"), buildClockContent(settings, i18n, onClockSettingsChanged));
        Tab pathsTab = new Tab(i18n.t("menu.settingsPaths"), buildPathsContent(owner, settings, i18n));
        Tab updatesTab = new Tab(i18n.t("menu.settingsUpdates"), buildUpdatesContent(settings, i18n));
        Tab templatesTab = new Tab(i18n.t("menu.settingsTemplates"), buildTemplatesContent(settings, i18n));
        Tab itrainTab = new Tab("iTrain", buildItrainContent(settings));
        Tab reiterTab = new Tab("Reiter", buildReiterContent(settings));
        // Geräusche: eigenes Fenster unter „Logistik Daten“ (Anwender 11.11.2026)
        tabPane.getTabs().addAll(viewTab, clockTab, pathsTab, updatesTab, templatesTab, itrainTab, reiterTab);

        // Jeder Reiter rollt, wenn der Inhalt höher ist als das Fenster (Anwender 11.11.2026: unten abgeschnitten)
        for (Tab t : tabPane.getTabs()) {
            javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(t.getContent());
            sp.setFitToWidth(true);
            sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
            sp.setStyle("-fx-background-color: transparent;");
            t.setContent(sp);
        }
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().setPrefSize(860, 560);
        applyThemeOnceShown(dialog, settings);
        dialog.showAndWait();
    }

    /**
     * Reiter der Übersicht (Anwender 09.11.2026): Höchstzahl der Reiter (Tabs + Module) und je Reiter Name und „In der
     * Übersicht zeigen“. Der erste Reiter ist immer sichtbar; „Anlage bearbeiten“ zeigt immer alle.
     */
    /**
     * Geräusche (Anwender 09.11.2026): Aus / einmal beim Öffnen / solange das Fenster offen ist, Lautstärke, Ordner,
     * Browser, Liste der Dateien mit Probehören, mitgelieferte wiederherstellen.
     */
    private static javafx.scene.Node buildGeraeuscheContent(Stage owner, AppSettings settings) {
        ComboBox<Geraeusche.Modus> modus = new ComboBox<>(FXCollections.observableArrayList(Geraeusche.Modus.values()));
        modus.setValue(settings.getGeraeuscheModus());
        modus.valueProperty().addListener((o, a, b) -> {
            if (b != null) {
                settings.setGeraeuscheModus(b);
                if (b == Geraeusche.Modus.AUS) {
                    Geraeusche.alleStoppen();
                }
            }
        });
        javafx.scene.control.Slider laut = new javafx.scene.control.Slider(0, 100, settings.getGeraeuscheLautstaerke());
        laut.setPrefWidth(220);
        Label lautText = new Label(settings.getGeraeuscheLautstaerke() + " %");
        laut.valueProperty().addListener((o, a, b) -> {
            settings.setGeraeuscheLautstaerke((int) Math.round(b.doubleValue()));
            lautText.setText(settings.getGeraeuscheLautstaerke() + " %");
            Geraeusche.lautstaerkeAnwenden();
        });
        CheckBox browser = new CheckBox("Auch in „Browser Logistik“ beim Antippen eines Kastens (einmal)");
        browser.setSelected(settings.isGeraeuscheImBrowser());
        browser.selectedProperty().addListener((o, a, b) -> settings.setGeraeuscheImBrowser(b));

        javafx.scene.control.ListView<java.nio.file.Path> liste = new javafx.scene.control.ListView<>();
        liste.setPrefHeight(200);
        liste.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(java.nio.file.Path p, boolean leer) {
                super.updateItem(p, leer);
                setText(leer || p == null ? null : p.getFileName().toString());
            }
        });
        Runnable fuellen = () -> {
            java.util.List<java.nio.file.Path> l = new java.util.ArrayList<>();
            try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.list(Geraeusche.ordner())) {
                st.filter(p -> Geraeusche.ENDUNGEN.contains(Geraeusche.endung(p))).sorted().forEach(l::add);
            } catch (java.io.IOException | RuntimeException ex) {
                // Ordner fehlt noch
            }
            liste.getItems().setAll(l);
        };
        fuellen.run();
        Button probe = new Button("▶ Probehören");
        probe.disableProperty().bind(liste.getSelectionModel().selectedItemProperty().isNull());
        probe.setOnAction(e -> Geraeusche.probe(liste.getSelectionModel().getSelectedItem()));
        liste.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 2 && liste.getSelectionModel().getSelectedItem() != null) {
                Geraeusche.probe(liste.getSelectionModel().getSelectedItem());
            }
        });
        Button stopp = new Button("■ Stopp");
        stopp.setOnAction(e -> Geraeusche.alleStoppen());

        Label pfad = new Label(settings.getGeraeuscheOrdner());
        Button waehlen = new Button("Durchsuchen...");
        waehlen.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Ordner der Geräusche");
            File initial = new File(settings.getGeraeuscheOrdner());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setGeraeuscheOrdner(chosen.getAbsolutePath());
                pfad.setText(chosen.getAbsolutePath());
                Geraeusche.auspacken(false);
                fuellen.run();
            }
        });
        Button oeffnen = new Button("Ordner öffnen");
        oeffnen.setOnAction(e -> {
            try {
                java.nio.file.Files.createDirectories(Geraeusche.ordner());
                java.awt.Desktop.getDesktop().open(Geraeusche.ordner().toFile());
            } catch (Exception ex) {
                new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING, "Ordner lässt sich nicht öffnen: " + ex.getMessage()).showAndWait();
            }
        });
        Button wieder = new Button("Mitgelieferte wiederherstellen");
        wieder.setTooltip(new javafx.scene.control.Tooltip(
                "Legt fehlende mitgelieferte Geräusche (CC0) wieder in den Ordner; eigene Dateien bleiben"));
        wieder.setOnAction(e -> {
            Geraeusche.auspacken(true);
            fuellen.run();
        });
        Button neu = new Button("Neu einlesen");
        neu.setOnAction(e -> fuellen.run());

        Label regel = new Label("Der Dateiname beginnt mit dem Namen des Symbols, z. B. „Möbelfabrik - Holz sägen.mp3“ "
                + "oder „Möbelfabrik 2.mp3“. Gesucht wird nach dem Namen des Objekts, dann nach dem Betriebstyp "
                + "(Sägewerk …) und der Art (Stadt, Bahnhof …); bei mehreren Dateien zufällig. Formate mp3, wav, m4a, aiff. "
                + "Mitgeliefert sind nur gemeinfreie Aufnahmen (CC0).");
        regel.setWrapText(true);
        regel.setMaxWidth(560);
        Label linuxHinweis = new Label("Linux: Für mp3 braucht das Programm die ffmpeg-Bibliotheken des Systems "
                + "(z. B. „sudo apt install ffmpeg“) und einen Tonausgang; wav geht auch ohne ffmpeg.");
        linuxHinweis.setWrapText(true);
        linuxHinweis.setMaxWidth(560);
        linuxHinweis.setStyle("-fx-text-fill: #b05a00;");
        linuxHinweis.setVisible(Geraeusche.linux());
        linuxHinweis.setManaged(Geraeusche.linux());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Beim Klick auf ein Symbol"), modus);
        grid.addRow(1, new Label("Lautstärke"), new HBox(8, laut, lautText));
        grid.add(browser, 1, 2);
        grid.addRow(3, new Label("Ordner"), pathRow(pfad, waehlen, oeffnen));
        Label dateien = new Label("Dateien");
        GridPane.setValignment(dateien, javafx.geometry.VPos.TOP);
        grid.add(dateien, 0, 4);
        javafx.scene.layout.VBox listeBox = new javafx.scene.layout.VBox(6, liste, new HBox(8, probe, stopp, neu, wieder));
        javafx.scene.layout.VBox.setVgrow(liste, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(listeBox, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(listeBox, javafx.scene.layout.Priority.ALWAYS);
        grid.add(listeBox, 1, 4);
        grid.add(regel, 1, 5);
        grid.add(linuxHinweis, 1, 6);
        return grid;
    }

    private static Stage geraeuscheFenster;

    /** Logistik Daten > „Geräusche...“ (Anwender 11.11.2026): eigenes Fenster, Größe veränderbar, Liste wächst mit. */
    public static void zeigeGeraeusche(Stage owner) {
        if (geraeuscheFenster != null && geraeuscheFenster.isShowing()) {
            geraeuscheFenster.toFront();
            return;
        }
        AppSettings settings = AppSettings.getInstance();
        Stage st = new Stage();
        st.initOwner(owner);
        st.setTitle("Geräusche");
        javafx.scene.Node inhalt = buildGeraeuscheContent(owner, settings);
        Button zu = new Button("Schließen");
        zu.setCancelButton(true);
        zu.setOnAction(e -> st.close());
        HBox unten = new HBox(zu);
        unten.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        unten.setPadding(new Insets(0, 15, 12, 15));
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(inhalt, unten);
        javafx.scene.layout.VBox.setVgrow(inhalt, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.Scene sc = new javafx.scene.Scene(root, 820, 560);
        ThemeManager.apply(sc, settings.getTheme());
        st.setScene(sc);
        WindowState.apply(st, "geraeusche", 820, 560);
        st.setOnHidden(e -> geraeuscheFenster = null);
        geraeuscheFenster = st;
        st.show();
    }

    private static javafx.scene.Node buildReiterContent(AppSettings settings) {
        javafx.scene.control.Spinner<Integer> max = new javafx.scene.control.Spinner<>(1, 50, settings.getMaxReiter());
        max.setEditable(true);
        max.setPrefWidth(90);
        max.valueProperty().addListener((o, a, b) -> {
            if (b != null) {
                settings.setMaxReiter(b);
            }
        });
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Höchstzahl der Reiter"), new HBox(8, max,
                new Label("Tabs und Module zusammen, ohne den ersten Reiter")));
        com.example.itrain_logistik.kern.Anlage a = anlageQuelle.get();
        if (a == null) {
            grid.add(new Label("Keine Konfiguration geöffnet."), 0, 1, 2, 1);
            return grid;
        }
        grid.add(new Label("Reiter"), 0, 1);
        javafx.scene.layout.VBox liste = new javafx.scene.layout.VBox(6);
        TextField haupt = new TextField(a.getHauptReiterName());
        haupt.setPrefColumnCount(18);
        haupt.focusedProperty().addListener((o, x, fokus) -> {
            if (!fokus && !haupt.getText().trim().equals(a.getHauptReiterName())) {
                a.setHauptReiterName(haupt.getText());
                reiterGeaendert.run();
            }
        });
        liste.getChildren().add(new HBox(8, haupt, new Label("(erster Reiter, immer sichtbar)")));
        java.util.Set<String> aus = settings.getReiterAusgeblendet();
        for (com.example.itrain_logistik.kern.Bereich b : a.bereiche()) {
            TextField name = new TextField(b.getAnzeigeName());
            name.setPrefColumnCount(18);
            name.focusedProperty().addListener((o, x, fokus) -> {
                if (!fokus && !name.getText().isBlank() && !name.getText().trim().equals(b.getAnzeigeName())) {
                    b.setName(name.getText().trim());
                    reiterGeaendert.run();
                }
            });
            CheckBox zeigen = new CheckBox("In der Übersicht zeigen");
            zeigen.setSelected(!aus.contains(b.getId()));
            zeigen.selectedProperty().addListener((o, x, y) -> {
                settings.setReiterAusgeblendet(b.getId(), !y);
                reiterGeaendert.run();
            });
            Label art = new Label(b instanceof com.example.itrain_logistik.kern.Modul ? "Modul" : "Tab");
            art.setMinWidth(50);
            HBox z = new HBox(8, art, name, zeigen);
            z.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            liste.getChildren().add(z);
        }
        if (a.bereiche().isEmpty()) {
            liste.getChildren().add(new Label("Noch keine Tabs oder Module – in „Anlage bearbeiten“ aus der Palette anlegen."));
        }
        javafx.scene.control.ScrollPane sc = new javafx.scene.control.ScrollPane(liste);
        sc.setFitToWidth(true);
        sc.setPrefViewportHeight(260);
        grid.add(sc, 1, 1);
        return grid;
    }

    /** Hinweis in der neu gewählten Sprache (die Oberfläche selbst wechselt erst nach dem Neustart). */
    private static final java.util.Map<String, String> NEUSTART = java.util.Map.of(
            "de", "Die Sprache wechselt beim nächsten Start von iTrain-Logistik.",
            "en", "The language will change the next time iTrain-Logistik starts.",
            "nl", "De taal verandert bij de volgende start van iTrain-Logistik.",
            "fr", "La langue changera au prochain démarrage d'iTrain-Logistik.",
            "es", "El idioma cambiará la próxima vez que se inicie iTrain-Logistik.",
            "it", "La lingua cambierà al prossimo avvio di iTrain-Logistik.");

    private static javafx.scene.Node buildViewContent(Stage owner, AppSettings settings, I18n i18n) {
        ComboBox<String> languageCombo = new ComboBox<>(FXCollections.observableArrayList(I18n.LANGUAGE_CODES));
        languageCombo.setValue(i18n.getCurrentLanguage());
        languageCombo.setCellFactory(list -> new LanguageListCell());
        languageCombo.setButtonCell(new LanguageListCell());
        languageCombo.valueProperty().addListener((obs, oldCode, newCode) -> {
            if (newCode != null && !newCode.equals(oldCode)) {
                // Die ganze Oberfläche wird beim Start übersetzt (Uebersetzung) – daher erst nach einem Neustart
                settings.setSprache(newCode);
                javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION, NEUSTART.getOrDefault(newCode, NEUSTART.get("en")));
                a.initOwner(owner);
                a.setHeaderText(i18n.getNativeName(newCode));
                a.show();
            }
        });

        Button customColorButton = new Button(i18n.t("settings.customColor"));
        customColorButton.setOnAction(e -> CustomColorDialog.show(owner));

        CheckBox showDataEditorBox = new CheckBox();
        showDataEditorBox.setSelected(settings.getShowDataEditor());
        showDataEditorBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setShowDataEditor(newV));

        CheckBox autoUpdateCheckBox = new CheckBox();
        autoUpdateCheckBox.setSelected(settings.getAutoUpdateCheckEnabled());
        autoUpdateCheckBox.selectedProperty().addListener((obs, oldV, newV) -> settings.setAutoUpdateCheckEnabled(newV));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.language")), languageCombo);
        grid.addRow(1, new Label(i18n.t("settings.theme")), customColorButton);
        grid.addRow(2, new Label(i18n.t("settings.screen")), buildScreenChooser(settings, i18n));
        grid.addRow(3, new Label(i18n.t("settings.showDataEditor")), showDataEditorBox);
        // Abgeschaltete Dialoge wieder einblenden (Anwender 09.11.2026)
        Button dialogeEin = new Button();
        Label dialogeText = new Label();
        dialogeText.setWrapText(true);
        dialogeText.setMaxWidth(420);
        Runnable dialogeZeigen = () -> {
            java.util.Map<String, String> aus = settings.getAbgeschalteteDialoge();
            dialogeEin.setText("Alle wieder anzeigen (" + aus.size() + ")");
            dialogeEin.setDisable(aus.isEmpty());
            dialogeText.setText(aus.isEmpty() ? "Keine Dialoge abgeschaltet." : String.join("\n", aus.values()));
        };
        dialogeEin.setOnAction(e -> {
            settings.alleDialogeEinblenden();
            dialogeZeigen.run();
        });
        MainController.pastell(dialogeEin, "#d5f5e3");
        dialogeZeigen.run();
                grid.addRow(4, new Label(i18n.t("settings.autoUpdateCheck")), autoUpdateCheckBox);
        ComboBox<Integer> lauflicht = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        lauflicht.setValue(settings.getLauflichtSekunden());
        lauflicht.valueProperty().addListener((obs, a, b) -> {
            if (b != null) {
                settings.setLauflichtSekunden(b);
            }
        });
        grid.addRow(5, new Label("Lauflicht auf den Verbindungen (Sekunden)"), new HBox(8, lauflicht,
                new Label("nach jeder Übergabe von Gütern oder Personen")));

        // Füllung und Schrift der Kästen stehen seit 27.10.2026 je Objekt im Rechtsklick „Farbe…“ der Anlage.
        CheckBox raeumlich = new CheckBox("Tiefe: 3D (Schatten)");
        raeumlich.setSelected(settings.isSymbol3d());
        raeumlich.selectedProperty().addListener((obs, a, b) -> {
            settings.setSymbol3d(b);
            AnlagenCanvas.alleNeuZeichnen();
        });
        grid.addRow(6, new Label("Symbole auf der Anlage"), new HBox(16, raeumlich,
                new Label("Farben: Rechtsklick auf ein Symbol, „Farbe…“")));

        // Pfeil beim Ein-/Aussteigen (Anwender 22.10.2026)
        javafx.scene.control.ColorPicker pfeilFuellung = Symbole.farbwahl(Symbole.farbe(settings.getPfeilFuellung(),
                javafx.scene.paint.Color.web(AppSettings.PFEIL_FUELLUNG_STANDARD)));
        javafx.scene.control.ColorPicker pfeilRahmen = Symbole.farbwahl(Symbole.farbe(settings.getPfeilRahmen(),
                javafx.scene.paint.Color.BLACK));
        javafx.scene.layout.StackPane pfeilProbe = new javafx.scene.layout.StackPane();
        Runnable probe = () -> pfeilProbe.getChildren().setAll(Symbole.blinkPersonen(true, 1, "Beispiel"));
        pfeilFuellung.valueProperty().addListener((obs, a, b) -> {
            settings.setPfeilFuellung(Symbole.hex(b));
            probe.run();
            AnlagenCanvas.pfeileNeuZeichnen();
        });
        pfeilRahmen.valueProperty().addListener((obs, a, b) -> {
            settings.setPfeilRahmen(Symbole.hex(b));
            probe.run();
            AnlagenCanvas.pfeileNeuZeichnen();
        });
        Button pfeilStandard = new Button("Standard");
        pfeilStandard.setOnAction(e -> {
            pfeilFuellung.setValue(javafx.scene.paint.Color.web(AppSettings.PFEIL_FUELLUNG_STANDARD));
            pfeilRahmen.setValue(javafx.scene.paint.Color.web(AppSettings.PFEIL_RAHMEN_STANDARD));
        });
        probe.run();
        HBox pfeilZeile = new HBox(16, paar(pfeilFuellung, "Füllung"), paar(pfeilRahmen, "Rahmen"), pfeilStandard,
                pfeilProbe);
        pfeilZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        for (javafx.scene.Node n : pfeilZeile.getChildren()) {
            if (n instanceof javafx.scene.layout.Region r) {
                r.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
        }
        grid.addRow(7, new Label("Pfeil Ein-/Aussteigen"), pfeilZeile);
        // Pfeil beim Be-/Entladen der Güter (Anwender 11.11.2026), wie der Pfeil der Personen
        javafx.scene.control.ColorPicker gutFuellung = Symbole.farbwahl(Symbole.farbe(settings.getPfeilGueterFuellung(),
                javafx.scene.paint.Color.BLACK));
        javafx.scene.control.ColorPicker gutRahmen = Symbole.farbwahl(Symbole.farbe(settings.getPfeilGueterRahmen(),
                javafx.scene.paint.Color.BLACK));
        javafx.scene.layout.StackPane gutProbe = new javafx.scene.layout.StackPane();
        Runnable gutProbeZeigen = () -> gutProbe.getChildren().setAll(new HBox(4,
                Symbole.gutBadge(null, "12", Symbole.Pfeil.ENTLADEN), Symbole.gutBadge(null, "5", Symbole.Pfeil.LADEN)));
        gutFuellung.valueProperty().addListener((obs, a, b) -> {
            settings.setPfeilGueterFuellung(Symbole.hex(b));
            gutProbeZeigen.run();
            AnlagenCanvas.pfeileNeuZeichnen();
            AnlagenCanvas.alleNeuZeichnen();
        });
        gutRahmen.valueProperty().addListener((obs, a, b) -> {
            settings.setPfeilGueterRahmen(Symbole.hex(b));
            gutProbeZeigen.run();
            AnlagenCanvas.pfeileNeuZeichnen();
            AnlagenCanvas.alleNeuZeichnen();
        });
        Button gutStandard = new Button("Standard");
        gutStandard.setOnAction(e -> {
            gutFuellung.setValue(javafx.scene.paint.Color.web(AppSettings.PFEIL_GUETER_FUELLUNG_STANDARD));
            gutRahmen.setValue(javafx.scene.paint.Color.web(AppSettings.PFEIL_GUETER_RAHMEN_STANDARD));
        });
        gutProbeZeigen.run();
        HBox gutZeile = new HBox(16, paar(gutFuellung, "Füllung"), paar(gutRahmen, "Rahmen"), gutStandard, gutProbe);
        gutZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        for (javafx.scene.Node n : gutZeile.getChildren()) {
            if (n instanceof javafx.scene.layout.Region r) {
                r.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
        }
        grid.addRow(8, new Label("Pfeil Be-/Entladen (Güter)"), gutZeile);
        // Raster im Editor (Anwender 27.10.2026)
        CheckBox raster = new CheckBox("Raster anzeigen, Symbole rasten darauf ein");
        raster.setSelected(settings.isEditorRaster());
        raster.selectedProperty().addListener((obs, a, b) -> {
            settings.setEditorRaster(b);
            AnlagenCanvas.alleNeuZeichnen();
        });
        grid.addRow(9, new Label("Anlage bearbeiten"), raster);
        grid.addRow(10, new Label("Abgeschaltete Dialoge"), new javafx.scene.layout.VBox(4, dialogeEin, dialogeText));
        return grid;
    }

    /** Auswahlbox mit ihrer Erklärung direkt rechts daneben. */
    private static HBox paar(javafx.scene.control.Control box, String text) {
        Label l = new Label(text);
        l.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        box.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox h = new HBox(5, box, l);
        h.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        h.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        return h;
    }

    private static javafx.scene.Node buildClockContent(AppSettings settings, I18n i18n, Runnable onChanged) {
        CheckBox showClockBox = new CheckBox();
        showClockBox.setSelected(settings.getShowClock());
        showClockBox.selectedProperty().addListener((obs, oldV, newV) -> {
            settings.setShowClock(newV);
            onChanged.run();
        });

        CheckBox showSecondsBox = new CheckBox();
        showSecondsBox.setSelected(settings.getShowClockSeconds());
        showSecondsBox.setDisable(!settings.getShowClock());
        showClockBox.selectedProperty().addListener((obs, oldV, newV) -> showSecondsBox.setDisable(!newV));
        showSecondsBox.selectedProperty().addListener((obs, oldV, newV) -> {
            settings.setShowClockSeconds(newV);
            onChanged.run();
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.showClock")), showClockBox);
        grid.addRow(1, new Label(i18n.t("settings.showClockSeconds")), showSecondsBox);
        // Faktor der Modelluhr aus der .tcdz (Anwender 14.10.2026), nur anzeigen
        Label faktor = new Label(LaufendeUhr.faktorText());
        faktor.setStyle("-fx-font-weight: bold;");
        Label faktorHinweis = new Label("Kann nur in iTrain verändert werden.");
        faktorHinweis.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        grid.addRow(2, new Label("Faktor der Modelluhr"), new javafx.scene.layout.VBox(2, faktor, faktorHinweis));

        Label hint = new Label(i18n.t("settings.clockHint"));
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, 3, 2, 1);
        return grid;
    }

    private static javafx.scene.Node buildPathsContent(Stage owner, AppSettings settings, I18n i18n) {
        Label layoutPathLabel = new Label(settings.getLayoutFilesDirectory());
        Button layoutBrowseButton = new Button(i18n.t("settings.browse"));
        layoutBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.layoutPath"));
            File initial = new File(settings.getLayoutFilesDirectory());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setLayoutFilesDirectory(chosen.getAbsolutePath());
                layoutPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox layoutRow = pathRow(layoutPathLabel, layoutBrowseButton);

        Label logistikPathLabel = new Label(settings.getLogistikDirectory());
        Button logistikBrowseButton = new Button(i18n.t("settings.browse"));
        logistikBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.logistikPath"));
            File initial = new File(settings.getLogistikDirectory());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setLogistikDirectory(chosen.getAbsolutePath());
                logistikPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox logistikRow = pathRow(logistikPathLabel, logistikBrowseButton);

        Label symbolPathLabel = new Label(settings.getSymbolDirectory());
        Button symbolBrowseButton = new Button(i18n.t("settings.browse"));
        symbolBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(i18n.t("settings.symbolPath"));
            File initial = new File(settings.getSymbolDirectory());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                settings.setSymbolDirectory(chosen.getAbsolutePath());
                symbolPathLabel.setText(chosen.getAbsolutePath());
            }
        });
        HBox symbolRow = pathRow(symbolPathLabel, symbolBrowseButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        // Layout-Ordner zuerst: Startpunkt fuer "Datei > Öffnen..." UND
        // Grundlage fuer die Bildauflösung (siehe ImageResolver) - also die
        // Angabe, die man als Erstes braucht.
        grid.addRow(0, new Label(i18n.t("settings.layoutPath")), layoutRow);
        grid.addRow(1, new Label(i18n.t("settings.logistikPath")), logistikRow);
        grid.addRow(2, new Label(i18n.t("settings.symbolPath")), symbolRow);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setHgrow(Priority.NEVER);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    /**
     * Reiter "Aktualisierung": je eine Zeitplan-Combobox fuer die fuenf aus
     * der gekoppelten .tcdz entnommenen Informationsarten (Lokomotiven,
     * Wagen, Zuege, Blockpositionen, Bloecke) - siehe
     * {@link AppSettings#UPDATE_INTERVAL_OPTIONS_SECONDS}. Das eigentliche
     * periodische Neueinlesen ist noch nicht angebunden (Phase 2), diese
     * Einstellungen werden aber bereits gespeichert.
     */
    /** Reiter "iTrain": wo iTrain läuft (dieser oder ein anderer Rechner) und Port des iTrain-Servers. */
    /**
     * Reiter „iTrain“ (Anwender 09.11.2026: alles auf XTC statt iTrain-Server): Rechner mit iTrain, XTC-Port,
     * Online-Empfang, „Jetzt prüfen“ mit Begrüßung und Zugliste. Gleiche Werte wie Einstellungen > „Verbindung zu
     * iTrain...“.
     */
    private static javafx.scene.Node buildItrainContent(AppSettings settings) {
        javafx.scene.control.TextField rechner = new javafx.scene.control.TextField(settings.getItrainRechner());
        rechner.setPrefColumnCount(22);
        rechner.setPromptText("localhost oder z. B. 192.168.0.171");
        rechner.textProperty().addListener((obs, a, b) -> settings.setItrainRechner(b == null || b.isBlank() ? "localhost" : b.trim()));
        javafx.scene.control.Spinner<Integer> port = new javafx.scene.control.Spinner<>(1, 65535, settings.getXtcPort());
        port.setEditable(true);
        port.setPrefWidth(110);
        port.valueProperty().addListener((obs, a, b) -> {
            if (b != null) {
                settings.setXtcPort(b);
            }
        });
        Button pruefen = new Button("Jetzt prüfen");
        MainController.pastell(pruefen, "#d5f5e3");
        Label ergebnis = new Label();
        ergebnis.setWrapText(true);
        ergebnis.setMaxWidth(560);
        pruefen.setOnAction(e -> {
            pruefen.setDisable(true);
            ergebnis.setText("Prüfe …");
            String host = settings.getItrainRechner();
            int p = settings.getXtcPort();
            Thread.ofVirtual().start(() -> {
                XtcVerbindung.Pruefung r = XtcVerbindung.pruefe(host, p, 2500);
                javafx.application.Platform.runLater(() -> {
                    pruefen.setDisable(false);
                    ergebnis.setText(r.erreichbar() ? "✔ " + r.version() + " auf " + host + ":" + p + ", " + r.zuege().size()
                            + " Züge" : "✘ Keine Antwort von iTrain-XTC auf " + host + ":" + p + " (" + r.fehler() + ")");
                    ergebnis.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (r.erreichbar() ? "#2e9e3e" : "#d03030") + ";");
                });
            });
        });
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label("Rechner mit iTrain (Name oder IP-Adresse)"), rechner);
        grid.addRow(1, new Label("XTC-Port (Vorgabe " + XtcVerbindung.PORT_VORGABE + ")"), port);
        grid.addRow(3, pruefen, ergebnis);
        Label hint = new Label("Über die XTC-Schnittstelle meldet iTrain je Zug Einfahrt, Warten mit Wartezeit und Abfahrt "
                + "sowie die Modellzeit. \"localhost\" = iTrain läuft auf diesem Rechner. Läuft iTrain auf einem anderen "
                + "Rechner, muss dessen Windows-Firewall den XTC-Port eingehend erlauben. Änderungen gelten nach dem "
                + "Schließen der Voreinstellungen.");
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, 4, 2, 1);
        return grid;
    }

    private static javafx.scene.Node buildUpdatesContent(AppSettings settings, I18n i18n) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));

        grid.addRow(0, new Label(i18n.t("settings.updateLocomotives")),
                buildIntervalCombo(settings.getUpdateIntervalLokomotiven(), settings::setUpdateIntervalLokomotiven, i18n));
        grid.addRow(1, new Label(i18n.t("settings.updateWagons")),
                buildIntervalCombo(settings.getUpdateIntervalWagen(), settings::setUpdateIntervalWagen, i18n));
        grid.addRow(2, new Label(i18n.t("settings.updateTrains")),
                buildIntervalCombo(settings.getUpdateIntervalZuege(), settings::setUpdateIntervalZuege, i18n));
        grid.addRow(3, new Label(i18n.t("settings.updateBlockPositions")),
                buildIntervalCombo(settings.getUpdateIntervalBlockpositionen(), settings::setUpdateIntervalBlockpositionen, i18n));
        grid.addRow(4, new Label(i18n.t("settings.updateBlocks")),
                buildIntervalCombo(settings.getUpdateIntervalBloecke(), settings::setUpdateIntervalBloecke, i18n));

        Label hint = new Label(i18n.t("settings.updatesHint"));
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, 5, 2, 1);
        return grid;
    }

    private static ComboBox<Integer> buildIntervalCombo(int currentSeconds, IntConsumer onChange, I18n i18n) {
        ComboBox<Integer> combo = new ComboBox<>();
        for (int seconds : AppSettings.UPDATE_INTERVAL_OPTIONS_SECONDS) {
            combo.getItems().add(seconds);
        }
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer seconds) {
                return seconds == null ? "" : intervalLabel(seconds, i18n);
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        combo.setValue(currentSeconds);
        combo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                onChange.accept(newV);
            }
        });
        return combo;
    }

    private static String intervalLabel(int seconds, I18n i18n) {
        return switch (seconds) {
            case 10 -> i18n.t("settings.updateInterval10");
            case 30 -> i18n.t("settings.updateInterval30");
            case 60 -> i18n.t("settings.updateInterval60");
            case 300 -> i18n.t("settings.updateInterval300");
            case 900 -> i18n.t("settings.updateInterval900");
            default -> i18n.t("settings.updateIntervalOnlyStart");
        };
    }

    /**
     * Reiter "Vorlagen": Maßstab und Modellbahn-Faktor, die neuen
     * Lokomotiven vorbelegt werden, solange dort noch kein eigener Wert
     * gesetzt wurde (siehe Projektauftrag "Den Maßstab sowie den
     * Modellbahn-Faktor setzen wir in den Einstellungen. Das ist dann eine
     * Vorlage für alle Daten, kann aber geändert werden." - siehe
     * {@link LocomotiveLogistikPane}).
     */
    private static javafx.scene.Node buildTemplatesContent(AppSettings settings, I18n i18n) {
        javafx.scene.control.TextField massstabField = new javafx.scene.control.TextField(settings.getDefaultMassstab());
        massstabField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                settings.setDefaultMassstab(massstabField.getText());
            }
        });

        javafx.scene.control.TextField faktorField = new javafx.scene.control.TextField(settings.getDefaultModellbahnFaktor());
        faktorField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                settings.setDefaultModellbahnFaktor(faktorField.getText());
            }
        });

        // Werte aus der iTrain-Datei (Anwender 09.11.2026): Maßstab wird übernommen, der Rest angezeigt.
        Label ausItrain = new Label();
        ausItrain.setWrapText(true);
        ausItrain.setMaxWidth(560);
        java.io.File tcdz = tcdzQuelle.get();
        java.util.Map<String, String> iw = itrainWerte(tcdz);
        if (iw == null) {
            ausItrain.setText("Keine iTrain-Datei geöffnet bzw. nicht lesbar – Werte bitte von Hand eintragen.");
        } else {
            String m = iw.get("massstab");
            if (m != null) {
                String ganz = m.endsWith(".0") ? m.substring(0, m.length() - 2) : m;
                massstabField.setText(ganz);
                settings.setDefaultMassstab(ganz);
            }
            ausItrain.setText("Aus der iTrain-Datei „" + tcdz.getName() + "“: Maßstab 1:" + (m == null ? "?" : m.replace(".0", ""))
                    + (iw.containsKey("spurweite") ? " · Spurweite " + iw.get("spurweite").toUpperCase(java.util.Locale.ROOT) : "")
                    + (iw.containsKey("uhrfaktor") ? " · Uhr-Faktor " + iw.get("uhrfaktor") : "")
                    + (iw.containsKey("anzeigefaktor") ? " · Anzeigefaktor (Signale) " + iw.get("anzeigefaktor").replace(".0", "") : "")
                    + ". Der Maßstab ist eingetragen. Eine Epoche steht nicht in der iTrain-Datei.");
            ausItrain.setStyle("-fx-text-fill: #2e7d32;");
        }

        // Epoche der Anlage. Seit dem Echtzeit-Kern gibt es nur 1. und 2. Klasse;
        // der Anteil der 1. Klasse wird je Ort eingestellt (kern.Ort).
        ComboBox<Integer> epocheBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 7));
        epocheBox.setValue(settings.getEpoche());
        epocheBox.setCellFactory(list -> epocheCell());
        epocheBox.setButtonCell(epocheCell());
        // Informationsfeld zur Epoche (Anwender 11.11.2026): vier Spalten, nicht editierbar, folgt der Auswahl
        GridPane epocheInfo = new GridPane();
        epocheInfo.setHgap(0);
        epocheInfo.setVgap(0);
        epocheInfo.setStyle("-fx-border-color: -fx-box-border; -fx-border-width: 1;");
        String[] kopf = {"Epoche", "Zeitabschnitt", "Vorhandene Wagenklassen", "Besonderheiten & historische Meilensteine"};
        double[] breite = {80, 110, 150, 300};
        Label[] werte = new Label[4];
        for (int i = 0; i < 4; i++) {
            Label k = new Label(kopf[i]);
            k.setWrapText(true);
            k.setStyle("-fx-font-weight: bold; -fx-padding: 4 6 4 6; -fx-background-color: derive(-fx-base, -6%);");
            k.setMaxWidth(Double.MAX_VALUE);
            k.setMaxHeight(Double.MAX_VALUE);
            werte[i] = new Label();
            werte[i].setWrapText(true);
            werte[i].setStyle("-fx-padding: 4 6 4 6;");
            werte[i].setAlignment(javafx.geometry.Pos.TOP_LEFT);
            werte[i].setMaxHeight(Double.MAX_VALUE);
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints(breite[i]);
            cc.setPrefWidth(breite[i]);
            cc.setHgrow(i == 3 ? javafx.scene.layout.Priority.ALWAYS : javafx.scene.layout.Priority.NEVER);
            epocheInfo.getColumnConstraints().add(cc);
            epocheInfo.add(k, i, 0);
            epocheInfo.add(werte[i], i, 1);
        }
        Label epocheSonder = new Label();
        epocheSonder.setWrapText(true);
        epocheSonder.setMaxWidth(640);
        epocheSonder.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        Label epocheEinleitung = new Label(Epochen.EINLEITUNG);
        epocheEinleitung.setWrapText(true);
        epocheEinleitung.setMaxWidth(640);
        epocheEinleitung.setStyle("-fx-font-size: 11px; -fx-opacity: 0.85;");
        java.util.function.IntConsumer epocheZeigen = nr -> {
            Epochen.Epoche e = Epochen.epoche(nr);
            werte[0].setText(e.name());
            werte[1].setText(e.zeit());
            werte[2].setText(e.klassen());
            werte[3].setText(e.besonderheiten());
            epocheSonder.setText(e.sonder().isEmpty() ? "" : Uebersetzung.tr("Sonderformen") + ": " + e.sonder());
            epocheSonder.setVisible(!e.sonder().isEmpty());
            epocheSonder.setManaged(!e.sonder().isEmpty());
        };
        epocheZeigen.accept(settings.getEpoche());
        epocheBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                settings.setEpoche(newV);
                epocheZeigen.accept(newV);
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(15));
        grid.addRow(0, new Label(i18n.t("settings.templateMassstab")), massstabField);
        grid.addRow(1, new Label(i18n.t("settings.templateModellbahnFaktor")), faktorField);
        grid.addRow(2, new Label(i18n.t("settings.epoche")), epocheBox);
        javafx.scene.layout.VBox epocheTeil = new javafx.scene.layout.VBox(6, epocheEinleitung, epocheInfo, epocheSonder);
        grid.add(epocheTeil, 0, 3, 2, 1);

        Label hint = new Label(i18n.t("settings.templatesHint"));
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, 4, 2, 1);

        Label epocheHint = new Label(i18n.t("settings.epocheHint"));
        epocheHint.setWrapText(true);
        epocheHint.setMaxWidth(560);
        grid.add(epocheHint, 0, 5, 2, 1);
        grid.add(ausItrain, 0, 6, 2, 1);
        return grid;
    }

    private static javafx.scene.control.ListCell<Integer> epocheCell() {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Epochen.epoche(item).name());
            }
        };
    }

    private static javafx.scene.Node buildScreenChooser(AppSettings settings, I18n i18n) {
        List<Screen> screens = Screen.getScreens();
        List<String> options = new ArrayList<>();
        options.add(AppSettings.SCREEN_REMEMBER);
        for (int i = 0; i < screens.size(); i++) {
            options.add(String.valueOf(i));
        }

        ComboBox<String> combo = new ComboBox<>(FXCollections.observableArrayList(options));
        combo.setCellFactory(list -> screenCell(screens, i18n));
        combo.setButtonCell(screenCell(screens, i18n));
        String current = settings.getPreferredScreen();
        combo.setValue(options.contains(current) ? current : AppSettings.SCREEN_REMEMBER);
        combo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                settings.setPreferredScreen(newV);
            }
        });
        if (screens.size() < 2) {
            combo.setDisable(true);
            Label hint = new Label(i18n.t("settings.screenSingle"));
            hint.setStyle("-fx-opacity: 0.8;");
            HBox row = new HBox(10, combo, hint);
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }
        return combo;
    }

    private static ListCell<String> screenCell(List<Screen> screens, I18n i18n) {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                if (AppSettings.SCREEN_REMEMBER.equals(item)) {
                    setText(i18n.t("settings.screenRemember"));
                    return;
                }
                int index = Integer.parseInt(item);
                Rectangle2D bounds = screens.get(index).getBounds();
                setText(i18n.t("settings.screenNumber", index + 1,
                        (int) bounds.getWidth(), (int) bounds.getHeight()));
            }
        };
    }

    private static void applyThemeOnceShown(Dialog<?> dialog, AppSettings settings) {
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) ->
                ThemeManager.apply(newScene, settings.getTheme()));
    }

    private static HBox pathRow(Label pathLabel, Button browseButton) {
        return pathRow(pathLabel, browseButton, null);
    }

    private static HBox pathRow(Label pathLabel, Button browseButton, Button extraButton) {
        pathLabel.setWrapText(true);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        HBox row = extraButton == null
                ? new HBox(10, pathLabel, browseButton)
                : new HBox(10, pathLabel, browseButton, extraButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
