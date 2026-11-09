package com.example.itrain_logistik;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Grüner Haken hinter gespeicherten Feldern (Anwender 14.10.2026): Ein Feld zeigt den Haken, solange sein Wert
 * vorhanden ist und dem gespeicherten Stand entspricht. Ändert der Anwender etwas, verschwindet der Haken; nach
 * "Speichern" ({@link Gruppe#bestaetigen()}) erscheint er wieder. Beim Öffnen gilt der geladene Wert als
 * gespeichert, so sieht man sofort, welche Daten vorhanden sind.
 *
 * <p>{@link Gruppe#ueber(Node)} durchsucht einen fertig gebauten Inhalt nach Eingabefeldern (Textfelder,
 * Auswahllisten, Kästchen, Drehfelder) und setzt hinter jedes einen Haken; Inhalte von Tabellen und Listen
 * werden nicht angefasst. Ein Feld lässt sich mit {@link #aus(Node)} ausnehmen.
 */
public final class Haken {

    private static final String AUS = "haken.aus";
    private static final String ERLEDIGT = "haken.erledigt";
    /** Gespeicherter Wert, wenn das Feld nur eine Vorbelegung zeigt (dann kein Haken, bis gespeichert). */
    public static final String GESPEICHERT = "haken.gespeichert";

    private Haken() {
    }

    /** Nimmt ein Feld (oder einen ganzen Bereich) von den Haken aus, z. B. Such- oder Auswahlhilfen. */
    public static <T extends Node> T aus(T node) {
        node.getProperties().put(AUS, Boolean.TRUE);
        return node;
    }

    /** Das Haken-Symbol selbst (grün, fester Platz, damit nichts springt). */
    static Label symbol() {
        Label l = new Label("✔");
        l.setStyle("-fx-text-fill: #2e9e3e; -fx-font-weight: bold; -fx-font-size: 13px;");
        l.setMinWidth(16);
        l.setPrefWidth(16);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    /** Alle Haken eines Fensters bzw. Dialogs. */
    public static final class Gruppe {

        private final List<Eintrag> eintraege = new ArrayList<>();
        private final List<Runnable> beimBestaetigen = new ArrayList<>();
        private final List<Runnable> beimUngespeichert = new ArrayList<>();

        private static final class Eintrag {
            final Supplier<String> wert;
            final Label haken;
            String gespeichert;

            Eintrag(Supplier<String> wert, Label haken) {
                this.wert = wert;
                this.haken = haken;
                this.gespeichert = wert.get();
            }

            void zeigen() {
                String jetzt = wert.get();
                boolean an = jetzt != null && !jetzt.isBlank() && Objects.equals(jetzt, gespeichert);
                haken.setOpacity(an ? 1 : 0);
            }
        }

        /** Durchsucht {@code wurzel} und versieht alle Eingabefelder mit einem Haken. */
        public Gruppe ueber(Node wurzel) {
            durchsuche(wurzel);
            return this;
        }

        /** Zusätzliche Aktion bei {@link #allesUngespeichert()}. */
        public void beimUngespeichert(Runnable r) {
            beimUngespeichert.add(r);
        }

        /** Zusätzliche Aktion beim Bestätigen (z. B. Haken-Spalte einer Tabelle neu zeichnen). */
        public void beimBestaetigen(Runnable r) {
            beimBestaetigen.add(r);
        }

        /** Nach "Einfügen" o. ä.: nichts gilt als gespeichert, bis "Speichern" gedrückt wird. */
        public void allesUngespeichert() {
            for (Eintrag e : eintraege) {
                e.gespeichert = null;
                e.zeigen();
            }
            beimUngespeichert.forEach(Runnable::run);
        }

        /** "Speichern": alle aktuellen Werte gelten als gespeichert, die Haken erscheinen. */
        public void bestaetigen() {
            for (Eintrag e : eintraege) {
                e.gespeichert = e.wert.get();
                e.zeigen();
            }
            beimBestaetigen.forEach(Runnable::run);
        }

        /** Haken für ein einzelnes, schon platziertes Feld (ersetzt es im Elternknoten durch Feld + Haken). */
        public void feld(Control c) {
            Supplier<String> wert = wertVon(c);
            if (wert == null || c.getProperties().containsKey(ERLEDIGT)) {
                return;
            }
            Parent eltern = c.getParent();
            if (!(eltern instanceof Pane pane)) {
                return;
            }
            c.getProperties().put(ERLEDIGT, Boolean.TRUE);
            Label h = symbol();
            Eintrag e = new Eintrag(wert, h);
            Object vorgabe = c.getProperties().get(GESPEICHERT);
            if (vorgabe != null) {
                e.gespeichert = String.valueOf(vorgabe);
            }
            eintraege.add(e);
            beobachte(c, e::zeigen);
            e.zeigen();
            HBox huelle = new HBox(4);
            huelle.setAlignment(Pos.CENTER_LEFT);
            huelle.setFillHeight(false);
            huelle.visibleProperty().bind(c.visibleProperty());
            huelle.managedProperty().bind(c.managedProperty());
            int index = pane.getChildren().indexOf(c);
            uebertrageLayout(c, huelle, pane);
            pane.getChildren().set(index, huelle);
            huelle.getChildren().addAll(c, h);
            if (c instanceof TextInputControl || c instanceof ComboBoxBase<?> || c instanceof Spinner<?>) {
                HBox.setHgrow(c, Priority.ALWAYS);
            }
        }

        private void durchsuche(Node n) {
            if (n == null || n.getProperties().containsKey(AUS)) {
                return;
            }
            if (n instanceof TextInputControl t && !t.isEditable()) {
                return; // reine Anzeige
            }
            if (n instanceof CheckBox || n instanceof TextInputControl || n instanceof ComboBoxBase<?>
                    || n instanceof Spinner<?>) {
                feld((Control) n);
                return;
            }
            if (n instanceof ScrollPane sp) {
                durchsuche(sp.getContent());
            } else if (n instanceof TitledPane tp) {
                durchsuche(tp.getContent());
            } else if (n instanceof javafx.scene.control.SplitPane sp) {
                new ArrayList<>(sp.getItems()).forEach(this::durchsuche);
            } else if (n instanceof javafx.scene.control.TabPane tp) {
                tp.getTabs().forEach(t -> durchsuche(t.getContent()));
            } else if (n instanceof Pane p) {
                for (Node k : new ArrayList<>(p.getChildren())) {
                    durchsuche(k);
                }
            }
            // andere Controls (Tabellen, Listen, Knöpfe) nicht betreten
        }

        private static Supplier<String> wertVon(Control c) {
            if (c instanceof TextInputControl t) {
                return t::getText;
            }
            if (c instanceof CheckBox cb) {
                // nur ein angehaktes Kästchen ist "vorhandene Angabe"
                return () -> cb.isSelected() ? "true" : "";
            }
            if (c instanceof ComboBoxBase<?> cb) {
                return () -> cb.getValue() == null ? "" : String.valueOf(cb.getValue());
            }
            if (c instanceof Spinner<?> s) {
                return () -> s.getValue() == null ? "" : String.valueOf(s.getValue());
            }
            return null;
        }

        private static void beobachte(Control c, Runnable r) {
            List<ObservableValue<?>> quellen = new ArrayList<>();
            if (c instanceof TextInputControl t) {
                quellen.add(t.textProperty());
            } else if (c instanceof CheckBox cb) {
                quellen.add(cb.selectedProperty());
            } else if (c instanceof ComboBoxBase<?> cb) {
                quellen.add(cb.valueProperty());
            } else if (c instanceof Spinner<?> s) {
                quellen.add(s.valueProperty());
            }
            quellen.forEach(q -> q.addListener((obs, a, b) -> r.run()));
        }

        private static void uebertrageLayout(Node von, Node nach, Pane eltern) {
            if (eltern instanceof GridPane) {
                Integer spalte = GridPane.getColumnIndex(von);
                Integer zeile = GridPane.getRowIndex(von);
                Integer spaltenBreite = GridPane.getColumnSpan(von);
                Integer zeilenHoehe = GridPane.getRowSpan(von);
                GridPane.setConstraints(nach, spalte == null ? 0 : spalte, zeile == null ? 0 : zeile,
                        spaltenBreite == null ? 1 : spaltenBreite, zeilenHoehe == null ? 1 : zeilenHoehe);
                GridPane.setHgrow(nach, GridPane.getHgrow(von));
                GridPane.setVgrow(nach, GridPane.getVgrow(von));
                GridPane.setHalignment(nach, GridPane.getHalignment(von));
                GridPane.setValignment(nach, GridPane.getValignment(von));
                GridPane.setFillWidth(nach, GridPane.isFillWidth(von));
                Insets m = GridPane.getMargin(von);
                if (m != null) {
                    GridPane.setMargin(nach, m);
                }
                GridPane.clearConstraints(von);
            } else if (eltern instanceof HBox) {
                HBox.setHgrow(nach, HBox.getHgrow(von));
                HBox.setMargin(nach, HBox.getMargin(von));
            } else if (eltern instanceof VBox) {
                VBox.setVgrow(nach, VBox.getVgrow(von));
                VBox.setMargin(nach, VBox.getMargin(von));
            } else if (eltern instanceof FlowPane) {
                FlowPane.setMargin(nach, FlowPane.getMargin(von));
            } else if (eltern instanceof StackPane) {
                StackPane.setAlignment(nach, StackPane.getAlignment(von));
                StackPane.setMargin(nach, StackPane.getMargin(von));
            }
            if (von instanceof Control c && c.getMaxWidth() == Double.MAX_VALUE) {
                ((HBox) nach).setMaxWidth(Double.MAX_VALUE);
            }
        }
    }
}
