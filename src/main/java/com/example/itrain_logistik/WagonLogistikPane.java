package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Ladeklasse;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strukturierte Logistik-Zusatzdaten fuer einen Wagen: zuerst legt der
 * Anwender die {@link WagenArt} fest - beim Import aus der .tcdz wird sie,
 * wo moeglich, automatisch aus dem iTrain-Attribut {@code type} abgeleitet
 * (siehe {@link TcdzModelBuilder}, "Den Wagentyp kannst Du aus dem Feld
 * type= ablesen. Nur wenn es leer ist traegt der Anwender etwas ein.") -,
 * danach erscheint der zur {@link WagenArt.Feldsatz} passende Feldsatz aus
 * dem Projektauftrag. "Flüssig/Trocken" ist entfallen (ergibt sich aus Wagenart und
 * Ladeklasse); "Behälter" macht den Wagen zum Lade-Joker, der jedes Gut lädt.
 * <p>
 * Fuer den Echtzeit-Kern zaehlen bei Gueterwagen Ladeklasse, Kapazitaet (Einheiten) und
 * Wagenladungen (+Cargo 2.4: die Kapazitaet verteilt sich gleichmaessig auf die
 * Wagenladungen), bei Personenwagen Sitzplaetze 1. und 2. Klasse und Stehplaetze (zaehlen zur
 * 2. Klasse), siehe {@link Fahrzeugdaten}. Die fruehere Ladegut-Tabelle und die 3./4. Klasse
 * sind mit dem Echtzeit-Kern entfallen.
 * Persistiert wie gehabt ueber die generischen {@link CustomField}e des Wagens.
 */
public final class WagonLogistikPane extends VBox {

    private static final String ZEIT_HINWEIS = "0 Sekunden bedeutet keine Bewertung. Sonst: Modellsekunden, "
            + "die ein voller Wagen zum Beladen bzw. Entladen braucht (begrenzt das Umladen zusätzlich zum Gleis).";

    public WagonLogistikPane(ObservableList<CustomField> fields, Anlage anlage, Runnable onChanged) {
        this(fields, anlage, onChanged, null);
    }

    /** @param haken Haken hinter gespeicherten Feldern (Anwender 14.10.2026), null = ohne */
    public WagonLogistikPane(ObservableList<CustomField> fields, Anlage anlage, Runnable onChanged, Haken.Gruppe haken) {
        this(fields, anlage, onChanged, haken, false);
    }

    /**
     * @param triebzug Felder eines Triebzugs (Lok, die selbst befördert, Anwender 22.10.2026): statt der Wagenart
     *                 zwei Kästchen "Befördert Personen" und "Befördert Güter" (auch beides), die Zeiten für Ein-
     *                 und Ausladen gelten für beides und stehen einmal darunter
     */
    public WagonLogistikPane(ObservableList<CustomField> fields, Anlage anlage, Runnable onChanged, Haken.Gruppe haken,
            boolean triebzug) {
        this(fields, anlage, onChanged, haken, triebzug, false);
    }

    /**
     * @param wagenliste Wagenliste aus iTrain (anonymer Eintrag der Zusammenstellung, Anwender 10.11.2026): statt
     *                   der Wagenart nur die Wahl „Personen“ oder „Güter“; als Güter immer Lade-Joker
     */
    public WagonLogistikPane(ObservableList<CustomField> fields, Anlage anlage, Runnable onChanged, Haken.Gruppe haken,
            boolean triebzug, boolean wagenliste) {
        setSpacing(10);
        setPadding(new Insets(0, 0, 10, 0));

        ComboBox<WagenArt> artBox = new ComboBox<>(FXCollections.observableArrayList(
                wagenliste ? new WagenArt[]{WagenArt.PERSONEN, WagenArt.SONDER} : WagenArt.values()));
        artBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(WagenArt a) {
                return a == null ? "(unbestimmt)" : !wagenliste ? a.getLabel()
                        : a == WagenArt.PERSONEN ? "Personen" : "Güter";
            }

            @Override
            public WagenArt fromString(String s) {
                return null;
            }
        });
        WagenArt aktuelleArt = LogistikFelder.parseEnum(LogistikFelder.getValue(fields, LogistikFelder.WAGENART),
                WagenArt.values());
        artBox.setValue(wagenliste && aktuelleArt != null && aktuelleArt != WagenArt.PERSONEN ? WagenArt.SONDER
                : aktuelleArt);

        GridPane gueterGrid = new GridPane();
        gueterGrid.setHgap(10);
        gueterGrid.setVgap(8);
        javafx.scene.control.ComboBox<Ladeklasse> klasseBox = LogistikFeldRow.enumCombo(gueterGrid, 0, "Ladeklasse",
                fields, LogistikFelder.LADEKLASSE, Ladeklasse.gueterKlassen(), Ladeklasse::getLabel, onChanged);
        // Kapazität höchstens 4 Stellen ohne Dezimalen, bei Flüssiggut 5 (Liter, Anwender 22.10.2026); dahinter die
        // Einheit der Ladeklasse (Anwender 14.10.2026)
        javafx.scene.control.TextField kapFeld = LogistikFeldRow.ziffern(LogistikFeldRow.text(gueterGrid, 1,
                "Kapazität", fields, LogistikFelder.KAPAZITAET, onChanged),
                klasseBox.getValue() == Ladeklasse.FLUESSIGGUT ? 5 : 4);
        klasseBox.valueProperty().addListener((obs, a, b) ->
                LogistikFeldRow.ziffern(kapFeld, b == Ladeklasse.FLUESSIGGUT ? 5 : 4));
        // Einheit der Kapazität wählbar, z. B. Stück statt Gebinde (Anwender 27.10.2026); nur Einheiten mit
        // derselben Basis wie die Ladeklasse, deshalb ändert die Wahl nichts an der Rechnung.
        javafx.scene.control.ComboBox<String> kapEinheit = new javafx.scene.control.ComboBox<>();
        kapEinheit.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String e) {
                return e == null ? "" : com.example.itrain_logistik.kern.Einheit.mitBasis(e);
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        boolean[] fuellen = {false};
        Runnable kapEinheitZeigen = () -> {
            fuellen[0] = true;
            Ladeklasse k = klasseBox.getValue();
            kapEinheit.getItems().setAll(com.example.itrain_logistik.kern.Einheit.fuerWagen(k));
            kapEinheit.setValue(com.example.itrain_logistik.kern.Einheit.wagen(
                    LogistikFelder.getValue(fields, LogistikFelder.KAPAZITAET_EINHEIT), k));
            fuellen[0] = false;
        };
        kapEinheitZeigen.run();
        klasseBox.valueProperty().addListener((obs, a, b) -> kapEinheitZeigen.run());
        kapEinheit.valueProperty().addListener((obs, a, b) -> {
            if (fuellen[0] || b == null) {
                return;
            }
            String std = com.example.itrain_logistik.kern.Einheit.standard(klasseBox.getValue());
            LogistikFelder.setValue(fields, LogistikFelder.KAPAZITAET_EINHEIT, b.equals(std) ? "" : b);
            onChanged.run();
        });
        // Einheit direkt hinter dem Feld
        gueterGrid.getChildren().remove(kapFeld);
        javafx.scene.layout.HBox kapZeile = new javafx.scene.layout.HBox(6, kapFeld, kapEinheit);
        kapZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        gueterGrid.add(kapZeile, 1, 1);
        gueterGrid.add(hinweis("Wie viel der Wagen insgesamt aufnimmt, in der gewählten Einheit (Gebinde und Stück zählen gleich). "
                + "Hat ein Gut eine eigene Einheit (z. B. Brammen zu je 10), belegt jede Einheit entsprechend mehr. "
                + "Welche Kapazitäten gebraucht werden, zeigt Tabellen > Kapazitäten."), 0, 2, 2, 1);
        LogistikFeldRow.textWithDefault(gueterGrid, 3, "Wagenladungen", fields, LogistikFelder.WAGENLADUNGEN, "1", onChanged);
        FlowPane aktivSymbole = new FlowPane(3, 3);
        aktivSymbole.setPrefWrapLength(320);
        gueterGrid.add(aktivSymbole, 1, 4);
        LogistikFeldRow.text(gueterGrid, 5, "Vorbild-Gewicht/Ladegewicht", fields, LogistikFelder.VORBILD_GEWICHT, onChanged);
        LogistikFeldRow.text(gueterGrid, 6, "Vorbild-Volumen", fields, LogistikFelder.VORBILD_VOLUMEN, onChanged);
        gueterGrid.add(hinweis("Gewicht und Volumen sind nur zur Information und werden beim Laden nicht bewertet."),
                0, 7, 2, 1);
        CheckBox behaelter = new CheckBox("Lade-Joker: lädt jedes Gut");
        behaelter.setSelected(wagenliste || "true".equals(LogistikFelder.getValue(fields, LogistikFelder.BEHAELTER)));
        if (wagenliste) {
            // Wagenliste: immer Lade-Joker
            LogistikFelder.setValue(fields, LogistikFelder.BEHAELTER, "true");
            behaelter.setDisable(true);
        }
        gueterGrid.addRow(8, new Label("Behälter"), behaelter);
        LogistikFeldRow.enumCombo(gueterGrid, 10, "Offen/Geschlossen", fields, LogistikFelder.WAGENBAUART,
                WagenBauart.values(), WagenBauart::getLabel, onChanged);
        javafx.scene.control.TextField gEnt = LogistikFeldRow.textWithDefault(gueterGrid, 11,
                "Zeit für Entladen (Sekunden)", fields, LogistikFelder.ZEIT_ENTLADEN, "0", onChanged);
        javafx.scene.control.TextField gBel = LogistikFeldRow.textWithDefault(gueterGrid, 12,
                "Zeit für Beladen (Sekunden)", fields, LogistikFelder.ZEIT_BELADEN, "0", onChanged);
        gueterGrid.add(hinweis(ZEIT_HINWEIS), 0, 13, 2, 1);


        Label ladegutLabel = new Label("Güter, die dieser Wagen laden kann (Aktiv). Sind Güter aktiv, lädt der "
                + "Wagen nur diese; sonst alle Güter seiner Ladeklasse. Die Kapazität verteilt sich gleichmäßig "
                + "auf die Wagenladungen; nicht mischbare Güter (z.B. Flüssiggut) belegen eine Wagenladung allein.");
        ladegutLabel.setWrapText(true);
        VBox ladegutBox = gueterTabelle(fields, anlage, klasseBox, aktivSymbole, onChanged, haken);
        Label jokerHinweis = hinweis("Behälterwagen sind Lade-Joker: Sie laden jedes Gut, unabhängig von Ladeklasse "
                + "und aktiven Gütern, bis zur Kapazität; die Mischregel gilt nicht (jedes Gut in eigenen Behältern).");
        gueterGrid.add(jokerHinweis, 0, 9, 2, 1);
        Runnable jokerSync = () -> {
            boolean j = behaelter.isSelected();
            // Ladeklasse, Kapazität, Einheit und Güter bleiben wählbar; der Joker ignoriert sie nur. Wird der
            // Joker wieder gelöst, ist der Wagen wie vorher (Anwender 10.11.2026).
            jokerHinweis.setVisible(j);
            jokerHinweis.setManaged(j);
            aktivSymbole.setOpacity(j ? 0.4 : 1);
        };
        behaelter.selectedProperty().addListener((obs, alt, neu) -> {
            LogistikFelder.setValue(fields, LogistikFelder.BEHAELTER, String.valueOf(neu));
            jokerSync.run();
            onChanged.run();
        });
        jokerSync.run();
        GridPane personenGrid = new GridPane();
        personenGrid.setHgap(10);
        personenGrid.setVgap(8);
        // Epoche des Wagens (Anwender 11.11.2026): „Vorgabe“ = Epoche der Anlage; je nach Epoche gibt es Plätze der
        // 3. (Epoche I/II) und 4. Klasse (Epoche I). Im Kern zählen sie zur 2. Klasse.
        ComboBox<Integer> epocheBox = new ComboBox<>();
        for (int i = Epochen.MIN; i <= Epochen.MAX; i++) {
            epocheBox.getItems().add(i);
        }
        epocheBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer n) {
                return n == null ? "" : Epochen.epoche(n).name();
            }

            @Override
            public Integer fromString(String t) {
                return null;
            }
        });
        CheckBox epocheVorgabe = new CheckBox("Vorgabe");
        epocheVorgabe.setSelected(!"false".equals(LogistikFelder.getValue(fields, LogistikFelder.EPOCHE_VORGABE)));
        epocheBox.setValue(Epochen.vonWagen(fields));
        epocheBox.setDisable(epocheVorgabe.isSelected());
        javafx.scene.control.Tooltip.install(epocheVorgabe, new javafx.scene.control.Tooltip(
                "Angehakt: Epoche der Anlage (Voreinstellungen > Vorlagen). Abgehakt: eigene Epoche für diesen Wagen."));
        javafx.scene.layout.HBox epocheZeile = new javafx.scene.layout.HBox(10, epocheBox, epocheVorgabe);
        epocheZeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        personenGrid.addRow(0, new Label("Epoche"), epocheZeile);
        // Plätze und Zeiten höchstens 4 Stellen (Anwender 14.10.2026)
        LogistikFeldRow.ziffern(LogistikFeldRow.text(personenGrid, 1, "Sitzplätze 1. Klasse", fields,
                LogistikFelder.SITZPLAETZE_1, onChanged), 4);
        LogistikFeldRow.ziffern(LogistikFeldRow.text(personenGrid, 2, "Sitzplätze 2. Klasse", fields,
                LogistikFelder.SITZPLAETZE_2, onChanged), 4);
        LogistikFeldRow.ziffern(LogistikFeldRow.text(personenGrid, 3, "Sitzplätze 3. Klasse (zählen zur 2. Klasse)",
                fields, LogistikFelder.SITZPLAETZE_3, onChanged), 4);
        LogistikFeldRow.ziffern(LogistikFeldRow.text(personenGrid, 4, "Sitzplätze 4. Klasse (zählen zur 2. Klasse)",
                fields, LogistikFelder.SITZPLAETZE_4, onChanged), 4);
        LogistikFeldRow.ziffern(LogistikFeldRow.text(personenGrid, 5, "Stehplätze (zählen zur 2. Klasse)", fields,
                LogistikFelder.STEHPLAETZE, onChanged), 4);
        javafx.scene.control.TextField pEnt = LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(personenGrid, 6,
                "Zeit für Entladen (Sekunden)", fields, LogistikFelder.ZEIT_ENTLADEN, "0", onChanged), 4);
        javafx.scene.control.TextField pBel = LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(personenGrid, 7,
                "Zeit für Beladen (Sekunden)", fields, LogistikFelder.ZEIT_BELADEN, "0", onChanged), 4);
        personenGrid.add(hinweis(ZEIT_HINWEIS), 0, 8, 2, 1);
        // Post und Expressgut im Personenzug, je höchstens 10 Stück (Anwender 27.10.2026)
        LogistikFeldRow.check(personenGrid, 9, "Kann Post mitnehmen (" + (int) com.example.itrain_logistik.kern.Tf3Daten.POST_KAPAZITAET
                + " Postsäcke)", fields, LogistikFelder.KANN_POST, onChanged);
        LogistikFeldRow.check(personenGrid, 10, "Kann Expressgut mitnehmen (" + (int) com.example.itrain_logistik.kern.Tf3Daten.POST_KAPAZITAET
                + " Stück)", fields, LogistikFelder.KANN_EXPRESSGUT, onChanged);
        Runnable klassenZeigen = () -> {
            int k = Epochen.klassen(Epochen.vonWagen(fields));
            zeigeZeilen(personenGrid, k >= 3, 3);
            zeigeZeilen(personenGrid, k >= 4, 4);
        };
        epocheVorgabe.selectedProperty().addListener((o, x, an) -> {
            LogistikFelder.setValue(fields, LogistikFelder.EPOCHE_VORGABE, String.valueOf(an));
            if (!an && epocheBox.getValue() != null) {
                LogistikFelder.setValue(fields, LogistikFelder.EPOCHE, String.valueOf(epocheBox.getValue()));
            }
            epocheBox.setDisable(an);
            epocheBox.setValue(Epochen.vonWagen(fields));
            klassenZeigen.run();
            onChanged.run();
        });
        epocheBox.valueProperty().addListener((o, x, n) -> {
            if (n != null && !epocheVorgabe.isSelected()) {
                LogistikFelder.setValue(fields, LogistikFelder.EPOCHE, String.valueOf(n));
                klassenZeigen.run();
                onChanged.run();
            }
        });
        klassenZeigen.run();

        if (triebzug) {
            // Zeiten (und "Offen/Geschlossen") aus beiden Feldsätzen nehmen, Zeiten einmal gemeinsam darunter
            entferneZeilen(gueterGrid, 10, 11, 12, 13);
            entferneZeilen(personenGrid, 6, 7, 8);
            GridPane zeitGrid = new GridPane();
            zeitGrid.setHgap(10);
            zeitGrid.setVgap(8);
            LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(zeitGrid, 0, "Zeit für Entladen (Sekunden)", fields,
                    LogistikFelder.ZEIT_ENTLADEN, "0", onChanged), 4);
            LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(zeitGrid, 1, "Zeit für Beladen (Sekunden)", fields,
                    LogistikFelder.ZEIT_BELADEN, "0", onChanged), 4);
            zeitGrid.add(hinweis(ZEIT_HINWEIS.replace("ein voller Wagen", "der volle Triebzug")), 0, 2, 2, 1);
            CheckBox personen = kaestchen("Befördert Personen", fields, LogistikFelder.TRIEBZUG_PERSONEN);
            CheckBox gueter = kaestchen("Befördert Güter", fields, LogistikFelder.TRIEBZUG_GUETER);
            Runnable sichtbar = () -> {
                zeige(personenGrid, personen.isSelected());
                zeige(gueterGrid, gueter.isSelected());
                zeige(ladegutLabel, gueter.isSelected());
                zeige(ladegutBox, gueter.isSelected());
                zeige(zeitGrid, personen.isSelected() || gueter.isSelected());
            };
            for (CheckBox cb : List.of(personen, gueter)) {
                cb.selectedProperty().addListener((obs, alt, neu) -> {
                    sichtbar.run();
                    onChanged.run();
                });
            }
            sichtbar.run();
            getChildren().addAll(hinweis("Triebzug: befördert selbst – Personen, Güter oder beides. Die Angaben "
                    + "gelten für den ganzen Triebzug."), personen, gueter, personenGrid, gueterGrid, ladegutLabel,
                    ladegutBox, zeitGrid);
            VBox.setVgrow(ladegutBox, javafx.scene.layout.Priority.ALWAYS);
            if (haken != null) {
                Haken.aus(ladegutBox);
                haken.ueber(this);
            }
            return;
        }

        // Personen-/Gepäckwagen (Anwender 22.10.2026): beide Feldsätze, die Zeiten einmal gemeinsam darunter
        GridPane zeitGrid = new GridPane();
        zeitGrid.setHgap(10);
        zeitGrid.setVgap(8);
        javafx.scene.control.TextField zEnt = LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(zeitGrid, 0,
                "Zeit für Entladen (Sekunden)", fields, LogistikFelder.ZEIT_ENTLADEN, "0", onChanged), 4);
        javafx.scene.control.TextField zBel = LogistikFeldRow.ziffern(LogistikFeldRow.textWithDefault(zeitGrid, 1,
                "Zeit für Beladen (Sekunden)", fields, LogistikFelder.ZEIT_BELADEN, "0", onChanged), 4);
        zeitGrid.add(hinweis(ZEIT_HINWEIS + " Beim Personen-/Gepäckwagen gelten die Zeiten für Reisende und Gepäck."),
                0, 2, 2, 1);
        List<javafx.scene.control.TextField> zeitFelder = List.of(gEnt, gBel, pEnt, pBel, zEnt, zBel);
        Runnable sicht = () -> {
            WagenArt a = artBox.getValue();
            boolean beide = a != null && a.getFeldsatz() == WagenArt.Feldsatz.BEIDE;
            updateVisibility(a, gueterGrid, ladegutLabel, ladegutBox, personenGrid);
            zeigeZeilen(gueterGrid, !beide, 10, 11, 12, 13);
            zeigeZeilen(personenGrid, !beide, 6, 7, 8);
            zeige(zeitGrid, beide);
            // dieselben Felder stehen in mehreren Masken: beim Umschalten den aktuellen Wert zeigen
            for (javafx.scene.control.TextField t : zeitFelder) {
                String w = LogistikFelder.getValue(fields, t == gEnt || t == pEnt || t == zEnt
                        ? LogistikFelder.ZEIT_ENTLADEN : LogistikFelder.ZEIT_BELADEN);
                if (!w.isBlank() && !w.equals(t.getText())) {
                    t.setText(w);
                }
            }
        };
        sicht.run();

        artBox.valueProperty().addListener((obs, oldV, newV) -> {
            LogistikFelder.setValue(fields, LogistikFelder.WAGENART, newV == null ? "" : newV.name());
            sicht.run();
            onChanged.run();
        });

        getChildren().addAll(new Label(wagenliste ? "Wagenliste befördert" : "Wagenart"), artBox, personenGrid, gueterGrid, ladegutLabel, ladegutBox,
                zeitGrid);
        VBox.setVgrow(ladegutBox, javafx.scene.layout.Priority.ALWAYS);
        if (haken != null) {
            Haken.aus(ladegutBox); // die Tabelle hat ihre eigene Haken-Spalte
            haken.ueber(this);
        }
    }

    /** Kästchen, das ein Feld auf "true"/"false" setzt. */
    private static CheckBox kaestchen(String text, ObservableList<CustomField> fields, String feld) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected("true".equals(LogistikFelder.getValue(fields, feld)));
        cb.selectedProperty().addListener((obs, alt, neu) -> LogistikFelder.setValue(fields, feld, String.valueOf(neu)));
        return cb;
    }

    private static void zeige(javafx.scene.Node n, boolean an) {
        n.setVisible(an);
        n.setManaged(an);
    }

    private static void zeigeZeilen(GridPane g, boolean an, int... zeilen) {
        java.util.Set<Integer> z = new java.util.HashSet<>();
        for (int i : zeilen) {
            z.add(i);
        }
        for (javafx.scene.Node n : g.getChildren()) {
            if (z.contains(GridPane.getRowIndex(n) == null ? 0 : GridPane.getRowIndex(n))) {
                zeige(n, an);
            }
        }
    }

    private static void entferneZeilen(GridPane g, int... zeilen) {
        java.util.Set<Integer> weg = new java.util.HashSet<>();
        for (int z : zeilen) {
            weg.add(z);
        }
        g.getChildren().removeIf(n -> weg.contains(GridPane.getRowIndex(n) == null ? 0 : GridPane.getRowIndex(n)));
    }

    private static Label hinweis(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(420);
        l.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        return l;
    }

    /** Tabelle aller Gueter mit "Aktiv"-Kaestchen; aktive Gueter erscheinen als Symbole bei den Wagenladungen. */
    private static VBox gueterTabelle(ObservableList<CustomField> fields, Anlage anlage,
            javafx.scene.control.ComboBox<Ladeklasse> klasseBox, FlowPane aktivSymbole, Runnable onChanged,
            Haken.Gruppe haken) {
        VBox box = new VBox(6);
        if (anlage == null) {
            return box;
        }
        Set<String> aktiv = new LinkedHashSet<>();
        for (String id : LogistikFelder.getValue(fields, LogistikFelder.LADEGUETER).split(",")) {
            if (!id.isBlank()) {
                aktiv.add(id.trim());
            }
        }
        Runnable symboleZeigen = () -> {
            aktivSymbole.getChildren().clear();
            for (String id : aktiv) {
                Gut g = anlage.gut(id);
                if (g != null) {
                    aktivSymbole.getChildren().add(Symbole.gut(g, 18));
                }
            }
            if (aktiv.isEmpty()) {
                Ladeklasse k = klasseBox.getValue();
                aktivSymbole.getChildren().add(new Label(k == null ? "keine Güter aktiv"
                        : "alle Güter der Ladeklasse " + k.getLabel()));
            }
        };
        Runnable[] tabelleNeu = {() -> { }};
        List<Gut> alle = new ArrayList<>(anlage.getGueter().values());
        alle.sort(Comparator.comparing((Gut g) -> g.getLadeklasse().ordinal()).thenComparing(Gut::getName));
        ObservableList<Gut> zeilen = FXCollections.observableArrayList(alle);
        TableView<Gut> tabelle = new TableView<>(zeilen);
        Spalten.einrichten(tabelle, "wagen.gueter");
        tabelle.setPrefHeight(220);
        tabelle.setEditable(true);
        tabelleNeu[0] = () -> javafx.application.Platform.runLater(tabelle::refresh);
        TableColumn<Gut, Boolean> aktivSpalte = new TableColumn<>("Aktiv");
        aktivSpalte.setCellValueFactory(c -> {
            SimpleBooleanProperty p = new SimpleBooleanProperty(aktiv.contains(c.getValue().getId()));
            p.addListener((obs, alt, neu) -> {
                if (neu) {
                    aktiv.add(c.getValue().getId());
                } else {
                    aktiv.remove(c.getValue().getId());
                }
                LogistikFelder.setValue(fields, LogistikFelder.LADEGUETER, String.join(",", aktiv));
                symboleZeigen.run();
                onChanged.run();
                tabelleNeu[0].run();
            });
            return p;
        });
        aktivSpalte.setCellFactory(CheckBoxTableCell.forTableColumn(aktivSpalte));
        aktivSpalte.setEditable(true);
        aktivSpalte.setPrefWidth(70);
        aktivSpalte.setSortable(false);
        // Kopf „Aktiv“ mit Kästchen „alle“ für die angezeigten Güter (Anwender 27.10.2026)
        CheckBox alleAktiv = new CheckBox("alle");
        boolean[] stellen = {false};
        Runnable alleZeigen = () -> {
            stellen[0] = true;
            long an = zeilen.stream().filter(g -> aktiv.contains(g.getId())).count();
            alleAktiv.setIndeterminate(an > 0 && an < zeilen.size());
            alleAktiv.setSelected(!zeilen.isEmpty() && an == zeilen.size());
            stellen[0] = false;
        };
        alleAktiv.setOnAction(e -> {
            if (stellen[0]) {
                return;
            }
            boolean an = alleAktiv.isSelected();
            for (Gut g : zeilen) {
                if (an) {
                    aktiv.add(g.getId());
                } else {
                    aktiv.remove(g.getId());
                }
            }
            LogistikFelder.setValue(fields, LogistikFelder.LADEGUETER, String.join(",", aktiv));
            symboleZeigen.run();
            onChanged.run();
            tabelle.refresh();
            alleZeigen.run();
        });
        Label aktivTitel = new Label("Aktiv");
        aktivTitel.setStyle("-fx-font-weight: bold;");
        VBox aktivKopf = new VBox(2, aktivTitel, alleAktiv);
        aktivKopf.setAlignment(javafx.geometry.Pos.CENTER);
        aktivSpalte.setText("");
        aktivSpalte.setGraphic(aktivKopf);
        zeilen.addListener((javafx.collections.ListChangeListener<Gut>) c -> alleZeigen.run());
        tabelleNeu[0] = () -> javafx.application.Platform.runLater(() -> {
            tabelle.refresh();
            alleZeigen.run();
        });
        alleZeigen.run();
        TableColumn<Gut, Gut> symbolSpalte = new TableColumn<>("");
        symbolSpalte.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        symbolSpalte.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Gut item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : Symbole.gut(item, 16));
            }
        });
        symbolSpalte.setPrefWidth(30);
        TableColumn<Gut, String> nameSpalte = new TableColumn<>("Gut");
        nameSpalte.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        nameSpalte.setPrefWidth(140);
        TableColumn<Gut, String> klasseSpalte = new TableColumn<>("Ladeklasse");
        klasseSpalte.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLadeklasse().getLabel()));
        klasseSpalte.setPrefWidth(100);
        // Hinter "Ladeklasse" die Einheit des Guts, ohne leere Spalte (Anwender 14.10.2026)
        TableColumn<Gut, String> einheitSpalte = new TableColumn<>("Einheiten");
        einheitSpalte.setCellValueFactory(c -> new SimpleStringProperty(GueterKatalogWindow.einheitText(c.getValue())));
        einheitSpalte.setPrefWidth(130);
        tabelle.getColumns().setAll(List.of(aktivSpalte, symbolSpalte, nameSpalte, klasseSpalte, einheitSpalte));
        tabelle.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // Die Tabelle wächst mit der Fensterhöhe
        VBox.setVgrow(tabelle, javafx.scene.layout.Priority.ALWAYS);
        tabelle.setMinHeight(160);

        CheckBox nurKlasse = new CheckBox("Nur Güter der gewählten Ladeklasse zeigen");
        Runnable filtern = () -> {
            Ladeklasse k = klasseBox.getValue();
            zeilen.setAll(alle.stream().filter(g -> !nurKlasse.isSelected() || k == null || g.getLadeklasse() == k)
                    .toList());
        };
        nurKlasse.setSelected(true);
        nurKlasse.selectedProperty().addListener((obs, a, b) -> filtern.run());
        klasseBox.valueProperty().addListener((obs, a, b) -> {
            // Ladeklasse geändert: aktive Güter anderer Klassen passen nicht mehr (Anwender 14.10.2026)
            if (b != null && aktiv.removeIf(id -> anlage.gut(id) != null && anlage.gut(id).getLadeklasse() != b)) {
                LogistikFelder.setValue(fields, LogistikFelder.LADEGUETER, String.join(",", aktiv));
                onChanged.run();
            }
            filtern.run();
            symboleZeigen.run();
        });
        filtern.run();
        symboleZeigen.run();
        box.getChildren().addAll(nurKlasse, tabelle);
        VBox.setVgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private static void updateVisibility(WagenArt art, GridPane gueterGrid, Label ladegutLabel, VBox ladegutBox,
            GridPane personenGrid) {
        WagenArt.Feldsatz feldsatz = art == null ? null : art.getFeldsatz();
        boolean gueter = feldsatz == WagenArt.Feldsatz.GUETER || feldsatz == WagenArt.Feldsatz.BEIDE;
        boolean personen = feldsatz == WagenArt.Feldsatz.PERSONEN || feldsatz == WagenArt.Feldsatz.BEIDE;
        gueterGrid.setVisible(gueter);
        gueterGrid.setManaged(gueter);
        ladegutLabel.setVisible(gueter);
        ladegutLabel.setManaged(gueter);
        ladegutBox.setVisible(gueter);
        ladegutBox.setManaged(gueter);
        personenGrid.setVisible(personen);
        personenGrid.setManaged(personen);
    }
}
