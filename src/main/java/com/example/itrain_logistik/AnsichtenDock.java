package com.example.itrain_logistik;

import com.example.itrain_logistik.TabellenWindow.Ansicht;
import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.EchtzeitKern;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ansichten (Tabellen) als eigenständige Fenster, die sich im Hauptfenster andocken lassen:
 * oben (unter dem Ribbon), unten, links oder rechts. Angedockt wird nur über das Menü „Ansichten“
 * („Andocken links/oben/…“), Anwender 20.10.2026; das Andocken durch Ziehen an den Rand ist entfernt.
 * Angedockte Ansichten haben in der Kopfzeile „Lösen“ (wieder Fenster) und „Schließen“. Die Trennlinien (mit drei Punkten)
 * stellen die Breite bzw. Höhe ein. Mehrere Ansichten am selben Rand liegen nebeneinander bzw.
 * untereinander.
 * <p>
 * Aufbau: senkrechter SplitPane [oben?, Mitte, unten?], Mitte = waagerechter SplitPane
 * [links?, Hauptinhalt, rechts?]; jeder Rand ist selbst ein SplitPane mit seinen Ansichten.
 * Seiten und Trennlinien merkt sich {@link AppSettings} (Schlüssel "dock.*").
 */
public final class AnsichtenDock {

    public enum Seite {
        OBEN("oben"), UNTEN("unten"), LINKS("links"), RECHTS("rechts");

        private final String label;

        Seite(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }


    private static final String CSS = """
            .dock-split > .split-pane-divider { -fx-padding: 0 4 0 4; -fx-background-color: derive(-fx-base, -15%); }
            .dock-split:vertical > .split-pane-divider { -fx-padding: 4 0 4 0; }
            .dock-split > .split-pane-divider > .horizontal-grabber {
                -fx-shape: "M0,2 A2,2 0 1,1 4,2 A2,2 0 1,1 0,2 Z M0,10 A2,2 0 1,1 4,10 A2,2 0 1,1 0,10 Z M0,18 A2,2 0 1,1 4,18 A2,2 0 1,1 0,18 Z";
                -fx-padding: 12 3 12 3; -fx-background-color: -fx-text-base-color; -fx-opacity: 0.65; -fx-scale-shape: true; }
            .dock-split > .split-pane-divider > .vertical-grabber {
                -fx-shape: "M2,0 A2,2 0 1,1 2,4 A2,2 0 1,1 2,0 Z M10,0 A2,2 0 1,1 10,4 A2,2 0 1,1 10,0 Z M18,0 A2,2 0 1,1 18,4 A2,2 0 1,1 18,0 Z";
                -fx-padding: 3 12 3 12; -fx-background-color: -fx-text-base-color; -fx-opacity: 0.65; -fx-scale-shape: true; }
            .dock-kopf { -fx-background-color: derive(-fx-base, -12%); -fx-padding: 2 4 2 8; }
            .dock-kopf .button { -fx-padding: 0 6 0 6; }
            """;

    /** Eine geöffnete Ansicht: Inhalt, Aktualisierer und wo sie gerade ist. */
    private final class Offen {
        final Ansicht ansicht;
        Node inhalt;
        final List<Runnable> akt = new ArrayList<>();
        Stage fenster;
        BorderPane panel;
        Seite seite;

        Offen(Ansicht ansicht) {
            this.ansicht = ansicht;
        }
    }

    private final Stage hauptfenster;
    private final StackPane wurzel = new StackPane();
    private final SplitPane senkrecht = new SplitPane();
    private final SplitPane waagerecht = new SplitPane();
    private final Map<Seite, SplitPane> raender = new EnumMap<>(Seite.class);
    private final Map<Ansicht, Offen> offen = new EnumMap<>(Ansicht.class);
    private final Node hauptinhalt;
    private final Timeline takt;

    private Anlage anlage;
    private EchtzeitKern kern;
    private Supplier<LogistikSnapshot> snapshot;
    private Runnable onGeaendert;

    /**
     * @param hauptinhalt bisherige Mitte des Hauptfensters (Übersicht); wird zur Mitte des Docks
     */
    public AnsichtenDock(Stage hauptfensterOderNull, Node hauptinhalt) {
        this.hauptfenster = hauptfensterOderNull;
        this.hauptinhalt = hauptinhalt;
        for (Seite s : Seite.values()) {
            SplitPane sp = new SplitPane();
            sp.getStyleClass().add("dock-split");
            sp.setOrientation(s == Seite.LINKS || s == Seite.RECHTS ? Orientation.VERTICAL : Orientation.HORIZONTAL);
            raender.put(s, sp);
        }
        senkrecht.setOrientation(Orientation.VERTICAL);
        senkrecht.getStyleClass().add("dock-split");
        waagerecht.getStyleClass().add("dock-split");
        wurzel.getStylesheets().add("data:text/css;base64,"
                + java.util.Base64.getEncoder().encodeToString(CSS.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        wurzel.getChildren().add(senkrecht);
        aufbauen();
        takt = new Timeline(new KeyFrame(Duration.seconds(2), e -> aktualisiere()));
        takt.setCycleCount(Timeline.INDEFINITE);
        takt.play();
    }

    public Parent getView() {
        return wurzel;
    }

    private Stage haupt() {
        return hauptfenster != null ? hauptfenster
                : wurzel.getScene() != null ? (Stage) wurzel.getScene().getWindow() : null;
    }

    /**
     * Neue Daten (Datei geöffnet). Bei einer anderen Anlage werden offene Ansichten neu aufgebaut,
     * beim ersten Mal die gemerkten Ansichten wieder geöffnet bzw. angedockt.
     */
    public void setDaten(Anlage a, EchtzeitKern k, Supplier<LogistikSnapshot> s, Runnable onGeaendert) {
        boolean erstes = this.anlage == null;
        boolean anders = this.anlage != a;
        this.anlage = a;
        this.kern = k;
        this.snapshot = s;
        this.onGeaendert = onGeaendert;
        if (erstes) {
            for (Ansicht v : Ansicht.values()) {
                String ort = AppSettings.getInstance().getWindowState("dock." + v.name());
                if (ort == null || ort.isBlank() || "ZU".equals(ort)) {
                    continue;
                }
                if ("FREI".equals(ort)) {
                    zeige(v);
                } else {
                    try {
                        andocken(v, Seite.valueOf(ort));
                    } catch (IllegalArgumentException ignored) {
                        // unbekannter Wert
                    }
                }
            }
        } else if (anders) {
            for (Offen o : offen.values()) {
                Node neu = baueInhalt(o);
                if (o.panel != null) {
                    o.panel.setCenter(neu);
                } else if (o.fenster != null) {
                    ((BorderPane) o.fenster.getScene().getRoot()).setCenter(neu);
                }
            }
        }
        aktualisiere();
    }

    /** Datei geschlossen: alle Ansichten schließen, ohne die gemerkten Plätze zu vergessen. */
    public void alleSchliessen() {
        for (Offen o : List.copyOf(offen.values())) {
            schliesse(o, false);
        }
        anlage = null;
    }

    public void schliesse(Ansicht v) {
        Offen o = offen.get(v);
        if (o != null) {
            schliesse(o, true);
        }
    }

    public boolean istOffen(Ansicht v) {
        return offen.containsKey(v);
    }

    /** Öffnet die Ansicht als Fenster bzw. holt sie nach vorn (angedockt: nichts zu tun). */
    public void zeige(Ansicht v) {
        if (anlage == null) {
            return;
        }
        Offen o = offen.get(v);
        if (o == null) {
            o = new Offen(v);
            offen.put(v, o);
            baueInhalt(o);
            alsFenster(o, null);
        } else if (o.fenster != null) {
            o.fenster.toFront();
        }
        aktualisiere();
    }

    /** Öffnet die Ansicht gleich angedockt (oder dockt eine offene um). */
    public void andocken(Ansicht v, Seite seite) {
        if (anlage == null) {
            return;
        }
        Offen o = offen.computeIfAbsent(v, Offen::new);
        if (o.inhalt == null) {
            baueInhalt(o);
        }
        andocken(o, seite);
        aktualisiere();
    }

    private Node baueInhalt(Offen o) {
        o.akt.clear();
        o.inhalt = TabellenWindow.inhalt(o.ansicht, anlage, kern, snapshot, o.akt);
        return o.inhalt;
    }

    private void aktualisiere() {
        for (Offen o : offen.values()) {
            o.akt.forEach(Runnable::run);
        }
    }

    // ------------------------------------------------------------------ Fenster

    private void alsFenster(Offen o, Point2D bildschirm) {
        Stage st = new Stage();
        st.initOwner(haupt());
        st.setTitle(o.ansicht.getTitel());
        // Andocken nur über das Menü „Ansichten“ (Anwender 20.10.2026)
        BorderPane root = new BorderPane(o.inhalt);
        Scene scene = new Scene(root, 900, 420);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        st.setScene(scene);
        WindowState.apply(st, "ansicht." + o.ansicht.name(), 900, 420);
        if (bildschirm != null) {
            st.setX(bildschirm.getX() - 120);
            st.setY(bildschirm.getY() - 15);
        }
        o.fenster = st;
        o.panel = null;
        o.seite = null;
        merke(o.ansicht, "FREI");
        st.setOnCloseRequest(e -> schliesse(o, true));
        st.show();
    }

    private static Button knopf(String text, String tip, Runnable r) {
        Button b = new Button(text);
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        b.setOnAction(e -> r.run());
        return b;
    }

    // ------------------------------------------------------------------ Andocken

    private void andocken(Offen o, Seite seite) {
        if (o.fenster != null) {
            Stage st = o.fenster;
            o.fenster = null;
            ((BorderPane) st.getScene().getRoot()).setCenter(null);
            st.setOnCloseRequest(null);
            st.close();
        }
        if (o.panel != null) {
            raender.get(o.seite).getItems().remove(o.panel);
        }
        o.panel = panel(o);
        o.seite = seite;
        raender.get(seite).getItems().add(o.panel);
        merke(o.ansicht, seite.name());
        aufbauen();
    }

    /** Kopfzeile (zum Ziehen) mit Titel, "Lösen" und "Schließen" über dem Inhalt. */
    private BorderPane panel(Offen o) {
        Label titel = new Label(o.ansicht.getTitel());
        titel.setStyle("-fx-font-weight: bold;");
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        Button loesen = knopf("⇱", "Als eigenes Fenster lösen", () -> loesen(o, null));
        Button zu = knopf("✕", "Schließen", () -> schliesse(o, true));
        HBox kopf = new HBox(6, titel, platz, loesen, zu);
        kopf.setAlignment(Pos.CENTER_LEFT);
        kopf.getStyleClass().add("dock-kopf");
        // kein Ziehen mehr (Anwender 20.10.2026): umdocken über das Menü „Ansichten“, lösen über ⇱
        BorderPane p = new BorderPane(o.inhalt);
        p.setTop(kopf);
        p.setMinSize(80, 60);
        return p;
    }

    private void loesen(Offen o, Point2D bildschirm) {
        if (o.panel != null) {
            raender.get(o.seite).getItems().remove(o.panel);
            o.panel.setCenter(null);
            o.panel = null;
        }
        aufbauen();
        alsFenster(o, bildschirm);
    }

    private void schliesse(Offen o, boolean merken) {
        if (o.panel != null) {
            raender.get(o.seite).getItems().remove(o.panel);
            o.panel = null;
            aufbauen();
        }
        if (o.fenster != null) {
            Stage st = o.fenster;
            o.fenster = null;
            st.close();
        }
        offen.remove(o.ansicht);
        if (merken) {
            merke(o.ansicht, "ZU");
        }
    }

    private static void merke(Ansicht v, String ort) {
        AppSettings.getInstance().setWindowState("dock." + v.name(), ort);
    }

    /** Setzt die SplitPanes aus den belegten Rändern neu zusammen; Trennlinien bleiben, wo sie waren. */
    private void aufbauen() {
        List<Node> mitte = new ArrayList<>();
        if (!raender.get(Seite.LINKS).getItems().isEmpty()) {
            mitte.add(raender.get(Seite.LINKS));
        }
        mitte.add(hauptinhalt);
        if (!raender.get(Seite.RECHTS).getItems().isEmpty()) {
            mitte.add(raender.get(Seite.RECHTS));
        }
        List<Node> spalte = new ArrayList<>();
        if (!raender.get(Seite.OBEN).getItems().isEmpty()) {
            spalte.add(raender.get(Seite.OBEN));
        }
        spalte.add(waagerecht);
        if (!raender.get(Seite.UNTEN).getItems().isEmpty()) {
            spalte.add(raender.get(Seite.UNTEN));
        }
        if (!waagerecht.getItems().equals(mitte)) {
            waagerecht.getItems().setAll(mitte);
        }
        if (!senkrecht.getItems().equals(spalte)) {
            senkrecht.getItems().setAll(spalte);
        }
        for (Seite s : Seite.values()) {
            SplitPane.setResizableWithParent(raender.get(s), false);
        }
        setzeTrennlinien();
    }

    private void setzeTrennlinien() {
        boolean l = waagerecht.getItems().contains(raender.get(Seite.LINKS));
        boolean r = waagerecht.getItems().contains(raender.get(Seite.RECHTS));
        boolean o = senkrecht.getItems().contains(raender.get(Seite.OBEN));
        boolean u = senkrecht.getItems().contains(raender.get(Seite.UNTEN));
        double pl = pos("links", 0.25);
        double pr = pos("rechts", 0.75);
        double po = pos("oben", 0.30);
        double pu = pos("unten", 0.65);
        if (l && r) {
            waagerecht.setDividerPositions(pl, pr);
        } else if (l) {
            waagerecht.setDividerPositions(pl);
        } else if (r) {
            waagerecht.setDividerPositions(pr);
        }
        if (o && u) {
            senkrecht.setDividerPositions(po, pu);
        } else if (o) {
            senkrecht.setDividerPositions(po);
        } else if (u) {
            senkrecht.setDividerPositions(pu);
        }
        // Änderungen der Trennlinien merken
        for (SplitPane.Divider d : waagerecht.getDividers()) {
            int i = waagerecht.getDividers().indexOf(d);
            String key = (i == 0 && l) ? "links" : "rechts";
            d.positionProperty().addListener((obs, a, b) -> merkePos(key, b.doubleValue()));
        }
        for (SplitPane.Divider d : senkrecht.getDividers()) {
            int i = senkrecht.getDividers().indexOf(d);
            String key = (i == 0 && o) ? "oben" : "unten";
            d.positionProperty().addListener((obs, a, b) -> merkePos(key, b.doubleValue()));
        }
    }

    private static double pos(String key, double standard) {
        String s = AppSettings.getInstance().getWindowState("dock.pos." + key);
        try {
            double v = s == null ? standard : Double.parseDouble(s);
            return v > 0.05 && v < 0.95 ? v : standard;
        } catch (NumberFormatException ex) {
            return standard;
        }
    }

    private static void merkePos(String key, double v) {
        if (v > 0.05 && v < 0.95) {
            AppSettings.getInstance().setWindowState("dock.pos." + key, String.valueOf(v));
        }
    }
}
