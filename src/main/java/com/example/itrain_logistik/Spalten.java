package com.example.itrain_logistik;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spalten ein- und ausblenden (Anwender 16.10.2026): Ein Rechtsklick auf die Kopfzeile einer Tabelle öffnet ein
 * Menü mit allen Spalten zum An- und Abhaken. Welche Spalten ausgeblendet sind, wird je Tabelle unter einem festen
 * Schlüssel in den Einstellungen gespeichert ({@link AppSettings#getAusgeblendeteSpalten}) und beim nächsten Öffnen
 * wieder angewendet. Mindestens eine Spalte bleibt immer sichtbar.
 *
 * <p>Spalten werden über ihre Überschrift erkannt, Spalten ohne Überschrift (z. B. Symbole) über ihre Position
 * ("#2"). Spalten, die erst nach {@link #einrichten} hinzukommen, werden automatisch mit erfasst.
 */
public final class Spalten {

    private static final String SCHLUESSEL = "spalten.schluessel";

    private Spalten() {
    }

    /** Richtet die Spaltenauswahl für eine Tabelle ein; {@code schluessel} muss je Tabelle eindeutig und fest sein. */
    public static <T extends TableView<?>> T einrichten(T tabelle, String schluessel) {
        if (tabelle.getProperties().containsKey(SCHLUESSEL)) {
            return tabelle;
        }
        tabelle.getProperties().put(SCHLUESSEL, schluessel);
        Runnable neu = () -> anwenden(tabelle, schluessel);
        tabelle.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) c -> neu.run());
        neu.run();
        return tabelle;
    }

    /**
     * Sucht in einem fertig gebauten Inhalt alle Tabellen und richtet sie ein (Schlüssel = Präfix + laufende Nummer
     * in der Reihenfolge des Aufbaus). Schon eingerichtete Tabellen behalten ihren Schlüssel.
     */
    public static void alleIn(Node wurzel, String praefix) {
        List<TableView<?>> l = new ArrayList<>();
        sammle(wurzel, l);
        for (int i = 0; i < l.size(); i++) {
            einrichten(l.get(i), l.size() == 1 ? praefix : praefix + "." + (i + 1));
        }
    }

    private static void sammle(Node n, List<TableView<?>> l) {
        if (n == null) {
            return;
        }
        if (n instanceof TableView<?> t) {
            l.add(t);
            return;
        }
        if (n instanceof ScrollPane sp) {
            sammle(sp.getContent(), l);
        } else if (n instanceof TitledPane tp) {
            sammle(tp.getContent(), l);
        } else if (n instanceof SplitPane sp) {
            sp.getItems().forEach(k -> sammle(k, l));
        } else if (n instanceof TabPane tp) {
            tp.getTabs().forEach(t -> sammle(t.getContent(), l));
        } else if (n instanceof Parent p) {
            p.getChildrenUnmodifiable().forEach(k -> sammle(k, l));
        }
    }

    static String kennung(TableColumn<?, ?> c, int index) {
        String t = c.getText();
        return t == null || t.isBlank() ? "#" + index : t.trim();
    }

    private static String anzeige(TableColumn<?, ?> c, int index) {
        String t = c.getText();
        return t == null || t.isBlank() ? "(Spalte " + (index + 1) + ", Symbol)" : t.trim();
    }

    private static void anwenden(TableView<?> tabelle, String schluessel) {
        List<? extends TableColumn<?, ?>> spalten = new ArrayList<>(tabelle.getColumns());
        Set<String> aus = AppSettings.getInstance().getAusgeblendeteSpalten(schluessel);
        ContextMenu menu = new ContextMenu();
        List<CheckMenuItem> punkte = new ArrayList<>();
        for (int i = 0; i < spalten.size(); i++) {
            TableColumn<?, ?> c = spalten.get(i);
            String id = kennung(c, i);
            if (aus.contains(id)) {
                c.setVisible(false);
            }
            CheckMenuItem m = new CheckMenuItem(anzeige(c, i));
            m.setSelected(c.isVisible());
            m.selectedProperty().addListener((obs, alt, an) -> {
                if (!an && spalten.stream().filter(TableColumn::isVisible).count() <= 1) {
                    m.setSelected(true); // die letzte sichtbare Spalte bleibt
                    return;
                }
                c.setVisible(an);
                speichern(spalten, schluessel);
            });
            c.visibleProperty().addListener((obs, alt, an) -> m.setSelected(an));
            punkte.add(m);
        }
        menu.getItems().add(titel("Spalten zeigen"));
        menu.getItems().addAll(punkte);
        MenuItem alle = new MenuItem("Alle Spalten zeigen");
        alle.setOnAction(e -> punkte.forEach(p -> p.setSelected(true)));
        menu.getItems().addAll(new SeparatorMenuItem(), alle);
        for (TableColumn<?, ?> c : spalten) {
            c.setContextMenu(menu);
        }
    }

    private static MenuItem titel(String text) {
        MenuItem m = new MenuItem(text);
        m.setDisable(true);
        m.setStyle("-fx-font-weight: bold; -fx-opacity: 1;");
        return m;
    }

    private static void speichern(List<? extends TableColumn<?, ?>> spalten, String schluessel) {
        Set<String> aus = new LinkedHashSet<>();
        for (int i = 0; i < spalten.size(); i++) {
            if (!spalten.get(i).isVisible()) {
                aus.add(kennung(spalten.get(i), i));
            }
        }
        AppSettings.getInstance().setAusgeblendeteSpalten(schluessel, aus);
    }
}
