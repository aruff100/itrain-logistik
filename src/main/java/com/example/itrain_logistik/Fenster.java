package com.example.itrain_logistik;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Fenster- und Dialoggrößen bleiben, wie der Anwender sie eingestellt hat (Anwender 22.10.2026: "Merke Dir alle
 * Fenstergrößen oder Dialoggrößen").
 * <p>
 * {@link #szene} setzt beim Neuaufbau eines offenen Fensters nur den Inhalt neu, statt eine neue Szene mit
 * Standardgröße zu setzen – sonst sprang z. B. das Lokfenster nach jedem Speichern auf 760×480 zurück.
 * {@link #merken(Dialog, String)} stellt bei Dialogen die zuletzt benutzte Größe her und sichert sie beim Schließen
 * (Schlüssel "dialog.&lt;name&gt;" in {@link AppSettings}, wie {@link WindowState}).
 */
public final class Fenster {

    private Fenster() {
    }

    /** Erster Aufbau: neue Szene in Standardgröße mit Farbschema; danach nur neuer Inhalt, Größe bleibt. */
    public static void szene(Stage stage, Parent inhalt, double breite, double hoehe) {
        Scene vorhanden = stage.getScene();
        if (vorhanden != null) {
            vorhanden.setRoot(inhalt);
            return;
        }
        Scene scene = new Scene(inhalt, breite, hoehe);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
    }

    /** Kennzeichen am DialogPane: Größe wird über {@link #merken} verwaltet (dann nicht noch einmal automatisch). */
    static final String GEMERKT = "fenster.merken";

    /**
     * Größe eines Dialogs merken und beim nächsten Öffnen wiederherstellen (Anwender 15.11.2026): Hat der Anwender die
     * Größe geändert, öffnet der Dialog beim nächsten Mal (auch nach einem Neustart) genau so. Sonst öffnet er so groß,
     * dass alle Felder und Beschriftungen zu sehen sind (höchstens Bildschirmgröße); verkleinert wird nie.
     */
    public static void merken(Dialog<?> d, String name) {
        d.getDialogPane().getProperties().put(GEMERKT, name);
        Window[] fenster = new Window[1];
        d.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, e -> {
            fenster[0] = fensterVon(d);
            verwalten(fenster[0], d.getDialogPane(), "dialog2." + name);
        });
    }

    /**
     * Gemeinsame Größenregel für Dialoge ({@link #merken} und {@link DialogeOben} für alle übrigen): gemerkte
     * Anwendergröße genau herstellen, sonst auf den Inhalt vergrößern; Änderungen des Anwenders beim Schließen sichern.
     */
    static void verwalten(Window w, javafx.scene.layout.Region pane, String schluessel) {
        if (w == null || w.getProperties().containsKey(GEMERKT)) {
            return;
        }
        w.getProperties().put(GEMERKT, schluessel);
        double[] g = groesse(AppSettings.getInstance().getWindowState(schluessel));
        boolean[] vomAnwender = {false};
        if (g != null) {
            javafx.geometry.Rectangle2D schirm = bildschirm(w);
            w.setWidth(Math.min(schirm.getWidth(), Math.max(200, g[0])));
            w.setHeight(Math.min(schirm.getHeight(), Math.max(120, g[1])));
            vomAnwender[0] = true; // bleibt gemerkt
        } else {
            aufInhalt(w, pane);
            javafx.application.Platform.runLater(() -> aufInhalt(w, pane));
            javafx.animation.PauseTransition spaeter = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));
            spaeter.setOnFinished(x -> aufInhalt(w, pane));
            spaeter.play();
        }
        sichtbarMachen(w);
        // Erst nach dem Einpendeln beobachten: was sich danach ändert, hat der Anwender gezogen
        javafx.animation.PauseTransition ruhe = new javafx.animation.PauseTransition(javafx.util.Duration.millis(700));
        ruhe.setOnFinished(x -> {
            javafx.beans.value.ChangeListener<Number> l = (o, v1, v2) -> vomAnwender[0] = true;
            w.widthProperty().addListener(l);
            w.heightProperty().addListener(l);
        });
        ruhe.play();
        w.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> {
            if (vomAnwender[0] && w.getWidth() > 50 && w.getHeight() > 50) {
                AppSettings.getInstance().setWindowState(schluessel, w.getWidth() + ";" + w.getHeight());
            }
        });
    }

    /** Fenster so groß, dass der ganze Inhalt zu sehen ist (nur vergrößern, höchstens Bildschirm). */
    private static void aufInhalt(Window w, javafx.scene.layout.Region pane) {
        if (w == null || !w.isShowing()) {
            return;
        }
        javafx.geometry.Rectangle2D schirm = bildschirm(w);
        double altB = w.getWidth();
        double altH = w.getHeight();
        javafx.scene.Scene sc = pane.getScene();
        double randB = sc == null ? 16 : w.getWidth() - sc.getWidth();
        double randH = sc == null ? 40 : w.getHeight() - sc.getHeight();
        if (randB < 0 || randB > 40) {
            randB = 16;
        }
        if (randH < 0 || randH > 80) {
            randH = 40;
        }
        double bw = Math.min(schirm.getWidth(), Math.max(pane.prefWidth(-1), natur(pane, true)) + randB);
        if (w.getWidth() < bw - 1) {
            w.setWidth(bw);
        }
        double bh = Math.min(schirm.getHeight(), Math.max(pane.prefHeight(w.getWidth() - randB), natur(pane, false))
                + randH);
        if (w.getHeight() < bh - 1) {
            w.setHeight(bh);
        }
        // gewachsen: wieder mittig über dem Besitzerfenster (sonst ragt er rechts/unten heraus)
        if ((w.getWidth() != altB || w.getHeight() != altH) && w instanceof javafx.stage.Stage st) {
            DialogPlacement.neuPlatzieren(st);
        }
        sichtbarMachen(w);
    }

    /**
     * Bildschirm, auf dem das Fenster liegt (Mitte), sonst der seines Besitzers, sonst der Hauptbildschirm
     * (Anwender 15.11.2026: Dialoge erschienen auf einem anderen Bildschirm als die Logistik).
     */
    static javafx.geometry.Rectangle2D bildschirm(Window w) {
        Window bezug = w instanceof javafx.stage.Stage st && st.getOwner() != null ? st.getOwner() : w;
        for (Window x : new Window[]{bezug, w}) {
            if (x == null || Double.isNaN(x.getX())) {
                continue;
            }
            var l = javafx.stage.Screen.getScreensForRectangle(x.getX() + x.getWidth() / 2, x.getY() + x.getHeight() / 2, 1, 1);
            if (!l.isEmpty()) {
                return l.get(0).getVisualBounds();
            }
        }
        return javafx.stage.Screen.getPrimary().getVisualBounds();
    }

    /** Fenster mittig über seinem Besitzer (bzw. dessen Bildschirm). */
    private static void mittigUeberBesitzer(Window w) {
        Window o = w instanceof javafx.stage.Stage st ? st.getOwner() : null;
        if (o != null && o.isShowing()) {
            w.setX(o.getX() + (o.getWidth() - w.getWidth()) / 2);
            w.setY(o.getY() + Math.max(0, (o.getHeight() - w.getHeight()) / 3));
        }
    }

    /**
     * Natürliche Größe des Inhalts: bei einer rollenden Fläche bzw. Reitern die Größe dessen, was darin steht (deren
     * Vorzugsgröße ist sonst klein und der Dialog öffnete mit abgeschnittenen Beschriftungen).
     */
    private static double natur(javafx.scene.Node n, boolean breite) {
        if (n instanceof javafx.scene.control.DialogPane dp) {
            double kopf = 0;
            if (!breite) {
                for (javafx.scene.Node k : dp.getChildrenUnmodifiable()) {
                    if (k != dp.getContent() && k.isVisible()) {
                        kopf += k.prefHeight(-1);
                    }
                }
            }
            return dp.getContent() == null ? 0 : natur(dp.getContent(), breite) + kopf + 24;
        }
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            return natur(sp.getContent(), breite) + 20;
        }
        if (n instanceof javafx.scene.control.TabPane tp) {
            double m = 0;
            for (javafx.scene.control.Tab t : tp.getTabs()) {
                if (t.getContent() != null) {
                    m = Math.max(m, natur(t.getContent(), breite));
                }
            }
            return m + (breite ? 10 : 45);
        }
        if (n instanceof javafx.scene.layout.VBox v && !breite) {
            double h = v.getPadding().getTop() + v.getPadding().getBottom();
            for (javafx.scene.Node k : v.getChildren()) {
                if (k.isManaged()) {
                    h += natur(k, false) + v.getSpacing();
                }
            }
            return h;
        }
        if (n instanceof javafx.scene.layout.VBox v) {
            double b = 0;
            for (javafx.scene.Node k : v.getChildren()) {
                if (k.isManaged()) {
                    b = Math.max(b, natur(k, true));
                }
            }
            return b + v.getPadding().getLeft() + v.getPadding().getRight();
        }
        if (breite && n instanceof javafx.scene.layout.Region r && r.getMaxWidth() > 0
                && r.getMaxWidth() < Double.MAX_VALUE && r.getMaxWidth() != javafx.scene.layout.Region.USE_PREF_SIZE) {
            return Math.min(r.prefWidth(-1), r.getMaxWidth()); // umbrechende Texte mit Höchstbreite
        }
        return breite ? n.prefWidth(-1) : n.prefHeight(-1);
    }

    /** Liegt das Fenster außerhalb aller Bildschirme oder ragt es unten heraus, mittig stellen bzw. hochrücken. */
    private static void sichtbarMachen(Window w) {
        boolean weg = javafx.stage.Screen.getScreensForRectangle(w.getX(), w.getY(), Math.max(1, w.getWidth()),
                Math.max(1, w.getHeight())).isEmpty();
        if (weg) {
            mittigUeberBesitzer(w);
        }
        // nur innerhalb des eigenen Bildschirms (bzw. des Besitzers) zurechtrücken, nie auf den Hauptbildschirm
        javafx.geometry.Rectangle2D schirm = bildschirm(w);
        if (w.getWidth() > schirm.getWidth()) {
            w.setWidth(schirm.getWidth());
        }
        if (w.getHeight() > schirm.getHeight()) {
            w.setHeight(schirm.getHeight());
        }
        if (w.getX() < schirm.getMinX()) {
            w.setX(schirm.getMinX());
        }
        if (w.getY() < schirm.getMinY()) {
            w.setY(schirm.getMinY());
        }
        if (w.getY() + w.getHeight() > schirm.getMaxY()) {
            w.setY(Math.max(schirm.getMinY(), schirm.getMaxY() - w.getHeight()));
        }
        if (w.getX() + w.getWidth() > schirm.getMaxX()) {
            w.setX(Math.max(schirm.getMinX(), schirm.getMaxX() - w.getWidth()));
        }
    }

    private static Window fensterVon(Dialog<?> d) {
        javafx.scene.Scene sc = d.getDialogPane() == null ? null : d.getDialogPane().getScene();
        return sc == null ? null : sc.getWindow();
    }

    private static double[] groesse(String text) {
        if (text == null || !text.contains(";")) {
            return null;
        }
        try {
            String[] t = text.split(";");
            return new double[]{Double.parseDouble(t[0]), Double.parseDouble(t[1])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
