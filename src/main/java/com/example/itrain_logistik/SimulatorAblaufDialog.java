package com.example.itrain_logistik;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * "Vorgang starten": Übersicht des aufgezeichneten Ablaufs als Tabelle (Start in Sekunden, Zug, Block,
 * Einfahrseite). Rechts Aufwärts/Abwärts, Entfernen/Einfügen/Anhängen und Ausfüllen (wie im iTrain-Editor);
 * auf der Tabelle löscht "Entf" die markierten Zeilen, "Tab" springt zur nächsten Zelle und bearbeitet sie,
 * "Return" bearbeitet bzw. übernimmt. Unten "Abbruch", "Speichern" und "Start".
 */
public final class SimulatorAblaufDialog {

    /** Ergebnis: der bearbeitete Ablauf und ob er gleich abgespielt werden soll. */
    public record Ergebnis(List<SimulatorAblauf.Schritt> schritte, boolean starten) {
    }

    private static final ButtonType START = new ButtonType("Start", ButtonBar.ButtonData.APPLY);

    private SimulatorAblaufDialog() {
    }

    public static Ergebnis zeige(Window owner, List<SimulatorAblauf.Schritt> vorhanden, List<String> zuege,
            List<String> bloecke) {
        ObservableList<SimulatorAblauf.Schritt> zeilen = FXCollections.observableArrayList();
        for (SimulatorAblauf.Schritt s : vorhanden) {
            zeilen.add(s.kopie());
        }
        TableView<SimulatorAblauf.Schritt> tabelle = new TableView<>(zeilen);
        Spalten.einrichten(tabelle, "simulator.ablauf");
        tabelle.setEditable(true);
        tabelle.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tabelle.setPlaceholder(new Label("Noch nichts aufgezeichnet – „Anhängen“ fügt einen Vorgang hinzu."));

        TableColumn<SimulatorAblauf.Schritt, String> nr = new TableColumn<>("Nr.");
        nr.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(zeilen.indexOf(c.getValue()) + 1)));
        nr.setEditable(false);
        nr.setSortable(false);
        nr.setPrefWidth(44);

        TableColumn<SimulatorAblauf.Schritt, String> start = textSpalte("Start (s)",
                s -> String.format(Locale.GERMANY, "%.1f", s.getStart()),
                (s, v) -> {
                    try {
                        s.setStart(Double.parseDouble(v.trim().replace(',', '.')));
                    } catch (NumberFormatException ex) {
                        // ungültige Eingabe: alter Wert bleibt
                    }
                });
        start.setPrefWidth(80);
        TableColumn<SimulatorAblauf.Schritt, String> zug = auswahlSpalte("Zug", zuege,
                SimulatorAblauf.Schritt::getZug, SimulatorAblauf.Schritt::setZug);
        zug.setPrefWidth(170);
        TableColumn<SimulatorAblauf.Schritt, String> block = auswahlSpalte("Block", bloecke,
                SimulatorAblauf.Schritt::getBlock, SimulatorAblauf.Schritt::setBlock);
        block.setPrefWidth(170);
        TableColumn<SimulatorAblauf.Schritt, String> seite = auswahlSpalte("Einfahrt von",
                List.of(SimulatorAblauf.LINKS, SimulatorAblauf.RECHTS),
                SimulatorAblauf.Schritt::getSeite, SimulatorAblauf.Schritt::setSeite);
        seite.setPrefWidth(100);
        tabelle.getColumns().addAll(List.of(nr, start, zug, block, seite));
        List<TableColumn<SimulatorAblauf.Schritt, String>> bearbeitbar = List.of(start, zug, block, seite);

        Runnable entfernen = () -> {
            List<SimulatorAblauf.Schritt> weg = new ArrayList<>(tabelle.getSelectionModel().getSelectedItems());
            int erste = tabelle.getSelectionModel().getSelectedIndex();
            zeilen.removeAll(weg);
            if (!zeilen.isEmpty()) {
                tabelle.getSelectionModel().clearAndSelect(Math.max(0, Math.min(erste, zeilen.size() - 1)));
            }
            tabelle.refresh();
        };

        // Tastatur: Entf, Tab, Return
        tabelle.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            boolean bearbeitet = tabelle.getEditingCell() != null;
            if (e.getCode() == KeyCode.DELETE && !bearbeitet) {
                entfernen.run();
                e.consume();
            } else if (e.getCode() == KeyCode.TAB) {
                if (bearbeitet && tabelle.getProperties().get("uebernehmen") instanceof Runnable r) {
                    r.run();
                }
                naechsteZelle(tabelle, bearbeitbar, e.isShiftDown());
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && !bearbeitet) {
                TablePosition<SimulatorAblauf.Schritt, ?> pos = tabelle.getFocusModel().getFocusedCell();
                if (pos != null && pos.getRow() >= 0) {
                    @SuppressWarnings("unchecked")
                    TableColumn<SimulatorAblauf.Schritt, ?> spalte = pos.getTableColumn() != null
                            && bearbeitbar.contains(pos.getTableColumn()) ? pos.getTableColumn() : start;
                    tabelle.edit(pos.getRow(), spalte);
                    e.consume();
                }
            }
        });

        Button auf = new Button("Aufwärts");
        auf.setOnAction(e -> verschiebe(tabelle, zeilen, -1));
        Button ab = new Button("Abwärts");
        ab.setOnAction(e -> verschiebe(tabelle, zeilen, 1));
        Button entf = new Button("Entfernen");
        entf.setOnAction(e -> entfernen.run());
        Button einf = new Button("Einfügen");
        einf.setOnAction(e -> {
            int i = Math.max(0, tabelle.getSelectionModel().getSelectedIndex());
            SimulatorAblauf.Schritt vorlage = zeilen.isEmpty() ? null : zeilen.get(Math.min(i, zeilen.size() - 1));
            zeilen.add(Math.min(i, zeilen.size()), neuerSchritt(vorlage, zuege, bloecke, 0));
            tabelle.getSelectionModel().clearAndSelect(Math.min(i, zeilen.size() - 1));
            tabelle.refresh();
        });
        Button anh = new Button("Anhängen");
        anh.setOnAction(e -> {
            SimulatorAblauf.Schritt letzter = zeilen.isEmpty() ? null : zeilen.get(zeilen.size() - 1);
            zeilen.add(neuerSchritt(letzter, zuege, bloecke, 10));
            tabelle.getSelectionModel().clearAndSelect(zeilen.size() - 1);
            tabelle.scrollTo(zeilen.size() - 1);
            tabelle.refresh();
        });
        Button fuellen = new Button("Ausfüllen");
        fuellen.setTooltip(Symbole.tip("Überträgt den Wert der ersten markierten Zeile in der gewählten Spalte auf alle markierten Zeilen"));
        fuellen.setOnAction(e -> ausfuellen(tabelle, bearbeitbar));
        for (Button b : List.of(auf, ab, entf, einf, anh, fuellen)) {
            b.setMaxWidth(Double.MAX_VALUE);
        }
        VBox knoepfe = new VBox(4, auf, ab, abstand(), entf, einf, anh, abstand(), fuellen);
        knoepfe.setPadding(new Insets(0, 0, 0, 10));
        knoepfe.setPrefWidth(110);

        BorderPane inhalt = new BorderPane(tabelle);
        inhalt.setRight(knoepfe);
        Label hilfe = new Label("Start = Sekunden nach Beginn des Ablaufs. Entf löscht, Tab springt weiter, Return bearbeitet.");
        hilfe.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        BorderPane.setMargin(hilfe, new Insets(6, 0, 0, 0));
        inhalt.setBottom(hilfe);
        inhalt.setPrefSize(720, 420);

        Dialog<ButtonType> d = new Dialog<>();

        Fenster.merken(d, "simulatorablauf"); // Größe merken (Anwender 22.10.2026)
        if (owner != null) {
            d.initOwner(owner);
        }
        d.setTitle("Vorgang starten – aufgezeichneter Ablauf");
        d.setResizable(true);
        d.getDialogPane().setContent(inhalt);
        DialogKnoepfe.setze(d.getDialogPane());
        d.getDialogPane().getButtonTypes().add(START);
        ButtonType antwort = d.showAndWait().orElse(DialogKnoepfe.ABBRUCH);
        if (antwort != DialogKnoepfe.SPEICHERN && antwort != START) {
            return null;
        }
        return new Ergebnis(new ArrayList<>(zeilen), antwort == START);
    }

    private static Region abstand() {
        Region r = new Region();
        r.setMinHeight(14);
        VBox.setVgrow(r, Priority.NEVER);
        return r;
    }

    private static SimulatorAblauf.Schritt neuerSchritt(SimulatorAblauf.Schritt vorlage, List<String> zuege,
            List<String> bloecke, double plus) {
        if (vorlage != null) {
            return new SimulatorAblauf.Schritt(vorlage.getStart() + plus, vorlage.getZug(), vorlage.getBlock(),
                    vorlage.getSeite());
        }
        return new SimulatorAblauf.Schritt(0, zuege.isEmpty() ? "" : zuege.get(0),
                bloecke.isEmpty() ? "" : bloecke.get(0), SimulatorAblauf.LINKS);
    }

    private static void verschiebe(TableView<SimulatorAblauf.Schritt> t, ObservableList<SimulatorAblauf.Schritt> zeilen,
            int richtung) {
        int i = t.getSelectionModel().getSelectedIndex();
        int j = i + richtung;
        if (i < 0 || j < 0 || j >= zeilen.size()) {
            return;
        }
        SimulatorAblauf.Schritt s = zeilen.remove(i);
        zeilen.add(j, s);
        t.getSelectionModel().clearAndSelect(j);
        t.refresh();
    }

    @SuppressWarnings("unchecked")
    private static void ausfuellen(TableView<SimulatorAblauf.Schritt> t,
            List<TableColumn<SimulatorAblauf.Schritt, String>> spalten) {
        List<Integer> zeilen = new ArrayList<>(t.getSelectionModel().getSelectedIndices());
        TablePosition<SimulatorAblauf.Schritt, ?> pos = t.getFocusModel().getFocusedCell();
        if (zeilen.size() < 2 || pos == null || !spalten.contains(pos.getTableColumn())) {
            return;
        }
        zeilen.sort(Integer::compareTo);
        TableColumn<SimulatorAblauf.Schritt, String> sp = (TableColumn<SimulatorAblauf.Schritt, String>) pos.getTableColumn();
        Object setzer = sp.getProperties().get("setzer");
        Object leser = sp.getProperties().get("leser");
        if (!(setzer instanceof BiConsumer<?, ?>) || !(leser instanceof Function<?, ?>)) {
            return;
        }
        BiConsumer<SimulatorAblauf.Schritt, String> set = (BiConsumer<SimulatorAblauf.Schritt, String>) setzer;
        Function<SimulatorAblauf.Schritt, String> get = (Function<SimulatorAblauf.Schritt, String>) leser;
        String wert = get.apply(t.getItems().get(zeilen.get(0)));
        for (int i : zeilen) {
            set.accept(t.getItems().get(i), wert);
        }
        t.refresh();
    }

    private static void naechsteZelle(TableView<SimulatorAblauf.Schritt> t,
            List<TableColumn<SimulatorAblauf.Schritt, String>> spalten, boolean rueckwaerts) {
        if (t.getItems().isEmpty()) {
            return;
        }
        TablePosition<SimulatorAblauf.Schritt, ?> pos = t.getFocusModel().getFocusedCell();
        int zeile = pos != null && pos.getRow() >= 0 ? pos.getRow() : 0;
        int spalte = pos != null ? spalten.indexOf(pos.getTableColumn()) : -1;
        spalte += rueckwaerts ? -1 : 1;
        if (spalte >= spalten.size()) {
            spalte = 0;
            zeile = Math.min(zeile + 1, t.getItems().size() - 1);
        } else if (spalte < 0) {
            spalte = spalten.size() - 1;
            zeile = Math.max(0, zeile - 1);
        }
        int z = zeile;
        TableColumn<SimulatorAblauf.Schritt, String> c = spalten.get(spalte);
        Platform.runLater(() -> {
            t.getSelectionModel().clearAndSelect(z, c);
            t.getFocusModel().focus(z, c);
            t.edit(z, c);
        });
    }

    /** Spalte mit Textfeld, das bei Return, Tab und Fokusverlust übernimmt. */
    private static TableColumn<SimulatorAblauf.Schritt, String> textSpalte(String titel,
            Function<SimulatorAblauf.Schritt, String> leser, BiConsumer<SimulatorAblauf.Schritt, String> setzer) {
        TableColumn<SimulatorAblauf.Schritt, String> c = new TableColumn<>(titel);
        c.setSortable(false);
        c.getProperties().put("leser", leser);
        c.getProperties().put("setzer", setzer);
        c.setCellValueFactory(z -> new SimpleStringProperty(leser.apply(z.getValue())));
        c.setCellFactory(sp -> new TableCell<>() {
            private TextField feld;

            @Override
            public void startEdit() {
                super.startEdit();
                if (!isEditing()) {
                    return;
                }
                feld = new TextField(getItem());
                Runnable uebernehmen = () -> {
                    if (isEditing() && getTableRow() != null && getTableRow().getItem() != null) {
                        setzer.accept(getTableRow().getItem(), feld.getText());
                        commitEdit(leser.apply(getTableRow().getItem()));
                    }
                };
                getTableView().getProperties().put("uebernehmen", uebernehmen);
                feld.setOnAction(e -> uebernehmen.run());
                feld.focusedProperty().addListener((o, a, b) -> {
                    if (!b) {
                        uebernehmen.run();
                    }
                });
                feld.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                });
                setText(null);
                setGraphic(feld);
                feld.selectAll();
                feld.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void commitEdit(String neu) {
                super.commitEdit(neu);
                getTableView().getProperties().remove("uebernehmen");
                setText(neu);
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
            }
        });
        return c;
    }

    /** Spalte mit Auswahlliste (Zug, Block, Seite); übernimmt sofort bei der Auswahl. */
    private static TableColumn<SimulatorAblauf.Schritt, String> auswahlSpalte(String titel, List<String> werte,
            Function<SimulatorAblauf.Schritt, String> leser, BiConsumer<SimulatorAblauf.Schritt, String> setzer) {
        TableColumn<SimulatorAblauf.Schritt, String> c = new TableColumn<>(titel);
        c.setSortable(false);
        c.getProperties().put("leser", leser);
        c.getProperties().put("setzer", setzer);
        c.setCellValueFactory(z -> new SimpleObjectProperty<>(leser.apply(z.getValue())));
        c.setCellFactory(sp -> new TableCell<>() {
            private ComboBox<String> box;

            @Override
            public void startEdit() {
                super.startEdit();
                if (!isEditing()) {
                    return;
                }
                box = new ComboBox<>(FXCollections.observableArrayList(werte));
                box.setValue(getItem());
                box.setMaxWidth(Double.MAX_VALUE);
                Runnable uebernehmen = () -> {
                    if (isEditing() && getTableRow() != null && getTableRow().getItem() != null
                            && box.getValue() != null) {
                        setzer.accept(getTableRow().getItem(), box.getValue());
                        commitEdit(box.getValue());
                    }
                };
                getTableView().getProperties().put("uebernehmen", uebernehmen);
                box.setOnAction(e -> uebernehmen.run());
                box.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEdit();
                    }
                });
                setText(null);
                setGraphic(box);
                box.requestFocus();
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void commitEdit(String neu) {
                super.commitEdit(neu);
                getTableView().getProperties().remove("uebernehmen");
                setText(neu);
                setGraphic(null);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
            }
        });
        return c;
    }
}
