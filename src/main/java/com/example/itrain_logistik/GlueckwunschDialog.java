package com.example.itrain_logistik;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Glückwunsch beim ersten erfolgreichen Personen- bzw. Gütertransfer einer neu aus der .tcdz angelegten Datei
 * (Anwender 22.10.2026). Klatschende Hände, Sterne und Konfetti sind selbst gezeichnet (JavaFX-Formen), also ohne
 * fremde Bilder und ohne Lizenzfragen.
 */
final class GlueckwunschDialog {

    private static final Color HAUT = Color.web("#ffcc4d");
    private static final Color HAUT_RAND = Color.web("#c98a1b");
    private static final Color[] KONFETTI = {Color.web("#ef4444"), Color.web("#f59e0b"), Color.web("#22c55e"),
            Color.web("#3b82f6"), Color.web("#a855f7"), Color.web("#ec4899"), Color.web("#14b8a6")};

    private GlueckwunschDialog() {
    }

    /**
     * Zeigt den Dialog, ohne zu blockieren (wird aus dem Takt des Kerns aufgerufen).
     *
     * @param ueberschrift z. B. "Glückwunsch!"
     * @param text         Haupttext
     * @param detail       kleinere Zeile darunter (darf leer sein)
     */
    static void zeige(Window owner, String ueberschrift, String text, String detail) {
        Stage st = new Stage();
        if (owner != null) {
            st.initOwner(owner);
        }
        st.initModality(Modality.NONE);
        st.setTitle("iTrain-Logistik – " + ueberschrift);

        Label kopf = new Label(ueberschrift);
        kopf.setFont(Font.font("Arial", FontWeight.BOLD, 34));
        kopf.setStyle("-fx-text-fill: #0d1d4f;");
        Label haupt = new Label(text);
        haupt.setWrapText(true);
        haupt.setTextAlignment(TextAlignment.CENTER);
        haupt.setMaxWidth(470);
        haupt.setFont(Font.font("Arial", FontWeight.BOLD, 17));
        haupt.setStyle("-fx-text-fill: #1f2937;");
        Label klein = new Label(detail == null ? "" : detail);
        klein.setWrapText(true);
        klein.setTextAlignment(TextAlignment.CENTER);
        klein.setMaxWidth(470);
        klein.setFont(Font.font("Arial", 13));
        klein.setStyle("-fx-text-fill: #374151;");
        klein.setVisible(detail != null && !detail.isBlank());
        klein.setManaged(klein.isVisible());

        Node haende = klatschendeHaende();
        HBox kopfZeile = new HBox(18, stern(16), haende, stern(16));
        kopfZeile.setAlignment(Pos.CENTER);

        Button weiter = new Button("Weiter");
        weiter.setDefaultButton(true);
        weiter.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #16307a; "
                + "-fx-text-fill: white; -fx-background-radius: 6; -fx-padding: 6 26 6 26;");
        weiter.setOnAction(e -> st.close());

        VBox inhalt = new VBox(12, kopfZeile, kopf, haupt, klein, weiter);
        inhalt.setAlignment(Pos.CENTER);
        inhalt.setPadding(new Insets(22, 30, 22, 30));

        Pane konfetti = new Pane();
        konfetti.setMouseTransparent(true);
        StackPane wurzel = new StackPane(inhalt, konfetti);
        wurzel.setStyle("-fx-background-color: linear-gradient(to bottom, #fff7d6, #ffe4a8);"
                + " -fx-border-color: #f59e0b; -fx-border-width: 4;");
        Scene scene = new Scene(wurzel, 560, 400);
        st.setScene(scene);
        st.setResizable(false);

        AnimationTimer regen = konfettiRegen(konfetti);
        st.setOnShown(e -> regen.start());
        st.setOnHidden(e -> regen.stop());
        st.show();
        weiter.requestFocus();
    }

    // ------------------------------------------------------------------ Hände

    /** Zwei Hände, die gegeneinander klatschen, mit kurzen Strichen beim Treffen. */
    private static Node klatschendeHaende() {
        Group links = hand(false);
        Group rechts = hand(true);
        links.setTranslateX(-26);
        rechts.setTranslateX(26);
        Group striche = new Group();
        for (int i = 0; i < 3; i++) {
            double w = Math.toRadians(-60 + i * 60);
            Line l = new Line(Math.sin(w) * 44, -Math.cos(w) * 44 - 10, Math.sin(w) * 58, -Math.cos(w) * 58 - 10);
            l.setStroke(Color.web("#f59e0b"));
            l.setStrokeWidth(4);
            l.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            striche.getChildren().add(l);
        }
        striche.setOpacity(0);
        Group alles = new Group(striche, links, rechts);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(links.rotateProperty(), -22), new KeyValue(rechts.rotateProperty(), 22),
                        new KeyValue(links.translateXProperty(), -30), new KeyValue(rechts.translateXProperty(), 30),
                        new KeyValue(striche.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(links.rotateProperty(), -4), new KeyValue(rechts.rotateProperty(), 4),
                        new KeyValue(links.translateXProperty(), -15), new KeyValue(rechts.translateXProperty(), 15),
                        new KeyValue(striche.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(230), new KeyValue(striche.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(420),
                        new KeyValue(links.rotateProperty(), -22), new KeyValue(rechts.rotateProperty(), 22),
                        new KeyValue(links.translateXProperty(), -30), new KeyValue(rechts.translateXProperty(), 30),
                        new KeyValue(striche.opacityProperty(), 0)));
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
        StackPane p = new StackPane(alles);
        p.setMinSize(150, 120);
        p.setPrefSize(150, 120);
        return p;
    }

    /** Eine Hand (Handfläche, vier Finger, Daumen) im Stil eines Emojis; {@code rechts} = gespiegelt. */
    private static Group hand(boolean rechts) {
        List<Shape> teile = new ArrayList<>();
        Ellipse flaeche = new Ellipse(0, 12, 20, 25);
        teile.add(flaeche);
        double[][] finger = {{-15, -30, 8, 26}, {-6, -38, 8, 32}, {3, -36, 8, 30}, {11, -28, 8, 24}};
        for (double[] f : finger) {
            Rectangle r = new Rectangle(f[0], f[1], f[2], f[3] + 14);
            r.setArcWidth(8);
            r.setArcHeight(8);
            teile.add(r);
        }
        Ellipse daumen = new Ellipse(-21, 8, 6, 14);
        daumen.setRotate(-35);
        teile.add(daumen);
        Group g = new Group();
        for (Shape s : teile) {
            s.setFill(HAUT);
            s.setStroke(HAUT_RAND);
            s.setStrokeWidth(1.6);
            g.getChildren().add(s);
        }
        if (rechts) {
            g.setScaleX(-1);
        }
        return g;
    }

    private static Node stern(double r) {
        double[] p = new double[20];
        for (int i = 0; i < 10; i++) {
            double w = Math.PI / 5 * i - Math.PI / 2;
            double rr = i % 2 == 0 ? r : r * 0.45;
            p[2 * i] = Math.cos(w) * rr;
            p[2 * i + 1] = Math.sin(w) * rr;
        }
        Polygon s = new Polygon(p);
        s.setFill(Color.web("#fbbf24"));
        s.setStroke(Color.web("#b45309"));
        s.setStrokeWidth(1.4);
        Timeline t = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(s.rotateProperty(), 0)),
                new KeyFrame(Duration.seconds(3), new KeyValue(s.rotateProperty(), 360)));
        t.setCycleCount(Animation.INDEFINITE);
        t.play();
        return s;
    }

    // ------------------------------------------------------------------ Konfetti

    private static final class Schnipsel {
        final Rectangle form;
        double vy;
        double vx;
        double dreh;

        Schnipsel(Rectangle form) {
            this.form = form;
        }
    }

    private static AnimationTimer konfettiRegen(Pane flaeche) {
        Random zufall = new Random();
        List<Schnipsel> alle = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            Schnipsel s = new Schnipsel(neu(zufall));
            s.form.setLayoutX(zufall.nextDouble() * 560);
            s.form.setLayoutY(-zufall.nextDouble() * 400);
            s.vy = 50 + zufall.nextDouble() * 70;
            s.vx = -20 + zufall.nextDouble() * 40;
            s.dreh = -200 + zufall.nextDouble() * 400;
            alle.add(s);
            flaeche.getChildren().add(s.form);
        }
        return new AnimationTimer() {
            private long vorher;
            private double gesamt;

            @Override
            public void handle(long jetzt) {
                double dt = vorher == 0 ? 0 : Math.min(0.05, (jetzt - vorher) / 1e9);
                vorher = jetzt;
                gesamt += dt;
                for (Schnipsel s : alle) {
                    s.form.setLayoutY(s.form.getLayoutY() + s.vy * dt);
                    s.form.setLayoutX(s.form.getLayoutX() + s.vx * dt + Math.sin(gesamt * 3 + s.dreh) * 0.4);
                    s.form.setRotate(s.form.getRotate() + s.dreh * dt);
                    if (s.form.getLayoutY() > 410) {
                        // nach 6 Sekunden kein neues Konfetti mehr: es rieselt aus
                        if (gesamt < 6) {
                            s.form.setLayoutY(-15);
                            s.form.setLayoutX(zufall.nextDouble() * 560);
                        } else {
                            s.form.setVisible(false);
                        }
                    }
                }
            }
        };
    }

    private static Rectangle neu(Random zufall) {
        Rectangle r = new Rectangle(5 + zufall.nextDouble() * 6, 8 + zufall.nextDouble() * 8);
        r.setFill(KONFETTI[zufall.nextInt(KONFETTI.length)]);
        r.setArcWidth(2);
        r.setArcHeight(2);
        return r;
    }
}
