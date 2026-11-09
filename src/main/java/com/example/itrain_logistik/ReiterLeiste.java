package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Bereich;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reiter der Übersicht bzw. von „Anlage bearbeiten“ (Anwender 09.11.2026): der erste Reiter zeigt die Anlage mit
 * Anlagen-Infrastruktur, jeder Tab und jedes Modul hat einen weiteren Reiter mit seinem Symbol als Anschluss. Reiter
 * (außer dem ersten) lassen sich als eigenes Fenster lösen und kehren beim Schließen zurück. Doppelklick auf den
 * Reitertitel benennt um – den Tab bzw. das Modul selbst, das Symbol folgt.
 */
final class ReiterLeiste {

    private final Anlage anlage;
    private final Function<Bereich, AnlagenCanvas> fabrik;
    private final boolean editor;
    private final Runnable geaendert;
    private final TabPane tabs = new TabPane();
    private final Tab hauptTab = new Tab();
    private final AnlagenCanvas haupt;
    /** Reiter je Bereich-Id (auch gelöste), mit Zeichenfläche. */
    private final Map<String, AnlagenCanvas> flaechen = new LinkedHashMap<>();
    private final Map<String, Tab> reiter = new LinkedHashMap<>();
    private final Map<String, Stage> geloest = new LinkedHashMap<>();

    /**
     * @param fabrik    baut die Zeichenfläche eines Reiters (null = erster Reiter)
     * @param editor    im Editor stehen alle Reiter, in der Übersicht nur die eingeblendeten
     * @param geaendert nach dem Umbenennen
     */
    ReiterLeiste(Anlage anlage, Function<Bereich, AnlagenCanvas> fabrik, boolean editor, Runnable geaendert) {
        this.anlage = anlage;
        this.fabrik = fabrik;
        this.editor = editor;
        this.geaendert = geaendert;
        haupt = fabrik.apply(null);
        hauptTab.setContent(haupt.getView());
        hauptTab.setClosable(false);
        titel(hauptTab, null);
        tabs.getTabs().add(hauptTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        abgleichen();
    }

    Node getView() {
        return tabs;
    }

    AnlagenCanvas getHaupt() {
        return haupt;
    }

    /** Zeichenfläche des gewählten Reiters. */
    AnlagenCanvas aktuell() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        if (t == null || t == hauptTab) {
            return haupt;
        }
        for (Map.Entry<String, Tab> e : reiter.entrySet()) {
            if (e.getValue() == t) {
                return flaechen.get(e.getKey());
            }
        }
        return haupt;
    }

    List<AnlagenCanvas> alle() {
        List<AnlagenCanvas> l = new ArrayList<>();
        l.add(haupt);
        l.addAll(flaechen.values());
        return l;
    }

    void aktualisiere() {
        alle().forEach(AnlagenCanvas::aktualisiere);
    }

    /** Alle Zeichenflächen neu aufbauen (z. B. nachdem ein Bahnhof in einen anderen Reiter gewechselt ist). */
    void alleNeu() {
        abgleichen();
        alle().forEach(AnlagenCanvas::neuAufbauen);
    }

    /**
     * Reiter an die Bereiche der Anlage angleichen: neue anhängen (ohne den Fokus zu wechseln, Anwender 09.11.2026),
     * gelöschte entfernen, Titel nachziehen.
     */
    void abgleichen() {
        java.util.Set<String> aus = editor ? java.util.Set.of() : AppSettings.getInstance().getReiterAusgeblendet();
        List<String> ids = new ArrayList<>();
        for (Bereich b : anlage.bereiche()) {
            if (!aus.contains(b.getId())) {
                ids.add(b.getId());
            }
        }
        for (String id : List.copyOf(flaechen.keySet())) {
            if (!ids.contains(id)) {
                entfernen(id);
            }
        }
        for (Bereich b : anlage.bereiche()) {
            if (!ids.contains(b.getId())) {
                continue;
            }
            if (!flaechen.containsKey(b.getId())) {
                AnlagenCanvas c = fabrik.apply(b);
                flaechen.put(b.getId(), c);
                Tab t = new Tab();
                t.setContent(c.getView());
                t.setClosable(false);
                reiter.put(b.getId(), t);
                tabs.getTabs().add(t); // ohne Fokus
            }
            titel(reiter.get(b.getId()), b);
            Stage st = geloest.get(b.getId());
            if (st != null) {
                st.setTitle(b.getAnzeigeName());
            }
        }
        titel(hauptTab, null);
    }

    private void entfernen(String id) {
        Stage st = geloest.remove(id);
        if (st != null) {
            st.setOnHidden(null);
            st.close();
        }
        Tab t = reiter.remove(id);
        if (t != null) {
            tabs.getTabs().remove(t);
        }
        AnlagenCanvas c = flaechen.remove(id);
        if (c != null) {
            c.dispose();
        }
    }

    /** Titel mit Kontextmenü: Umbenennen, als eigenes Fenster lösen (nicht der erste Reiter). */
    private void titel(Tab t, Bereich b) {
        String name = b != null ? b.getAnzeigeName() : anlage.getHauptReiterName();
        Label l = t.getGraphic() instanceof Label alt ? alt : new Label();
        l.setText(name);
        t.setText(null);
        t.setGraphic(l);
        ContextMenu m = new ContextMenu();
        MenuItem umbenennen = new MenuItem("Umbenennen…");
        umbenennen.setOnAction(e -> umbenennen(b));
        m.getItems().add(umbenennen);
        if (b != null) {
            MenuItem loesen = new MenuItem("Als eigenes Fenster lösen");
            loesen.setOnAction(e -> loesen(b));
            m.getItems().add(loesen);
            t.setTooltip(new javafx.scene.control.Tooltip((b instanceof com.example.itrain_logistik.kern.Modul
                    ? "Modul" : "Tab") + " „" + name + "“ – Rechtsklick: umbenennen oder als eigenes Fenster lösen"));
        }
        t.setContextMenu(m);
        l.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                umbenennen(b);
            }
        });
    }

    private void umbenennen(Bereich b) {
        String alt = b != null ? b.getAnzeigeName() : anlage.getHauptReiterName();
        TextInputDialog d = new TextInputDialog(alt);
        Window w = tabs.getScene() != null ? tabs.getScene().getWindow() : null;
        if (w != null) {
            d.initOwner(w);
        }
        d.setTitle("Reiter umbenennen");
        d.setHeaderText(b == null ? "Name des ersten Reiters" : "Neuer Name – gilt für den Reiter und das Symbol");
        d.showAndWait().map(String::trim).filter(n -> !n.isEmpty()).ifPresent(n -> {
            if (b != null) {
                b.setName(n);
            } else {
                anlage.setHauptReiterName(n);
            }
            alleNeu();
            if (geaendert != null) {
                geaendert.run();
            }
        });
    }

    /** Reiter als eigenes Fenster (wie ein zusätzliches Browserfenster); Schließen holt ihn zurück. */
    void loesen(Bereich b) {
        Tab t = reiter.get(b.getId());
        AnlagenCanvas c = flaechen.get(b.getId());
        if (t == null || c == null || geloest.containsKey(b.getId())) {
            Stage st = geloest.get(b.getId());
            if (st != null) {
                st.toFront();
            }
            return;
        }
        int stelle = tabs.getTabs().indexOf(t);
        tabs.getTabs().remove(t);
        Node inhalt = t.getContent();
        t.setContent(null);
        Stage st = new Stage();
        st.setTitle(b.getAnzeigeName());
        javafx.scene.layout.BorderPane root = new javafx.scene.layout.BorderPane((javafx.scene.Parent) inhalt);
        Scene sc = new Scene(root, 1000, 700);
        ThemeManager.apply(sc, AppSettings.getInstance().getTheme());
        st.setScene(sc);
        WindowState.apply(st, (editor ? "reiterEditor." : "reiter.") + b.getId(), 1000, 700);
        geloest.put(b.getId(), st);
        st.setOnHidden(e -> {
            geloest.remove(b.getId());
            root.setCenter(null);
            t.setContent(inhalt);
            if (reiter.containsValue(t)) {
                tabs.getTabs().add(Math.min(stelle, tabs.getTabs().size()), t);
            }
        });
        st.show();
    }

    void dispose() {
        for (Stage st : List.copyOf(geloest.values())) {
            st.setOnHidden(null);
            st.close();
        }
        geloest.clear();
        alle().forEach(AnlagenCanvas::dispose);
    }
}
