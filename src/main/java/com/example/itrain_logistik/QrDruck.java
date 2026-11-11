package com.example.itrain_logistik;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * Druckbarer Aushang mit QR-Code (Anwender 11.11.2026): Ein Ausdruck hängt an der Ausstellungsanlage, ein Besucher
 * scannt ihn mit dem Handy und sieht den Zuganzeiger, Routen-Anzeiger, Modul-Anzeiger oder die Logistik im Browser.
 * Vorschau mit „Drucken…“ (Druckerauswahl des Systems, auch „Als PDF drucken“) und „Als Bild speichern…“ (PNG).
 */
public final class QrDruck {

    private QrDruck() {
    }

    /**
     * @param art    Art des Anzeigers, z. B. „Routen-Anzeiger“ (Überschrift)
     * @param was    was die Adresse zeigt, z. B. der Zug oder das Modul
     * @param url    die Adresse im QR-Code
     */
    public static void drucken(Stage owner, String art, String was, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        Stage st = new Stage();
        st.initOwner(owner);
        st.setTitle(Uebersetzung.tr("QR-Code drucken"));
        Node blatt = blatt(art, was, url);
        Button drucken = new Button(Uebersetzung.tr("Drucken…"));
        MainController.pastell(drucken, "#d6efc7");
        drucken.setDefaultButton(true);
        drucken.setOnAction(e -> {
            if (drucke(st, blatt(art, was, url))) {
                st.close();
            }
        });
        Button bild = new Button(Uebersetzung.tr("Als Bild speichern…"));
        MainController.pastell(bild, "#e2d9f3");
        bild.setOnAction(e -> speichereBild(st, blatt(art, was, url), was));
        Button zu = new Button(Uebersetzung.tr("Schließen"));
        zu.setOnAction(e -> st.close());
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        HBox knoepfe = new HBox(8, bild, platz, zu, drucken);
        knoepfe.setPadding(new Insets(10));
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(blatt);
        sp.setFitToWidth(true);
        VBox root = new VBox(sp, knoepfe);
        VBox.setVgrow(sp, Priority.ALWAYS);
        Scene sc = new Scene(root, 560, 760);
        st.setScene(sc);
        st.show();
    }

    /** Das Blatt: Überschrift, Bezeichnung, großer QR-Code, Adresse und Anleitung; schwarz auf weiß. */
    static VBox blatt(String art, String was, String url) {
        Label kopf = new Label("iTrain-Logistik");
        kopf.setStyle("-fx-font-size: 14px; -fx-text-fill: #555555;");
        Label titel = new Label(art);
        titel.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: black;");
        titel.setWrapText(true);
        Label name = new Label(was == null ? "" : was);
        name.setStyle("-fx-font-size: 24px; -fx-text-fill: #16307a; -fx-font-weight: bold;");
        name.setWrapText(true);
        ImageView qr = new ImageView(MainController.qrBild(url, 900));
        qr.setFitWidth(340);
        qr.setFitHeight(340);
        qr.setSmooth(false);
        Label adresse = new Label(url);
        adresse.setStyle("-fx-font-size: 13px; -fx-font-family: monospace; -fx-text-fill: black;");
        Label anleitung = new Label(Uebersetzung.tr("Mit der Handy-Kamera scannen und den Link öffnen. Das Handy "
                + "muss im WLAN der Anlage angemeldet sein."));
        anleitung.setWrapText(true);
        anleitung.setMaxWidth(420);
        anleitung.setStyle("-fx-font-size: 14px; -fx-text-fill: black; -fx-text-alignment: center;");
        anleitung.setAlignment(Pos.CENTER);
        VBox v = new VBox(14, kopf, titel, name, qr, adresse, anleitung);
        v.setAlignment(Pos.TOP_CENTER);
        v.setPadding(new Insets(30));
        v.setStyle("-fx-background-color: white;");
        v.setPrefWidth(500);
        return v;
    }

    private static boolean drucke(Stage owner, VBox blatt) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, Uebersetzung.tr("Kein Drucker gefunden. "
                    + "Mit „Als Bild speichern…“ lässt sich der Aushang als Bild ablegen und drucken."));
            a.initOwner(owner);
            a.showAndWait();
            return false;
        }
        if (!job.showPrintDialog(owner)) {
            job.cancelJob();
            return false;
        }
        Printer p = job.getPrinter();
        PageLayout layout = p.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT);
        // zum Messen kurz in eine Szene
        new Scene(new VBox(blatt));
        blatt.applyCss();
        blatt.layout();
        double b = blatt.prefWidth(-1);
        double h = blatt.prefHeight(b);
        double f = Math.min(layout.getPrintableWidth() / b, layout.getPrintableHeight() / h);
        blatt.getTransforms().add(new Scale(f, f));
        boolean ok = job.printPage(layout, blatt);
        if (ok) {
            job.endJob();
        } else {
            job.cancelJob();
        }
        return ok;
    }

    private static void speichereBild(Stage owner, VBox blatt, String name) {
        FileChooser fc = new FileChooser();
        fc.setTitle(Uebersetzung.tr("Als Bild speichern…"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        fc.setInitialFileName("QR " + (name == null || name.isBlank() ? "Anzeiger" : name.replaceAll("[\\\\/:*?\"<>|]",
                "_")) + ".png");
        File f = fc.showSaveDialog(owner);
        if (f == null) {
            return;
        }
        new Scene(new VBox(blatt));
        blatt.applyCss();
        blatt.layout();
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.WHITE);
        sp.setTransform(new Scale(2, 2));
        WritableImage img = blatt.snapshot(sp, null);
        try {
            int w = (int) img.getWidth();
            int h = (int) img.getHeight();
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
            }
            javax.imageio.ImageIO.write(bi, "png", f);
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage());
            a.initOwner(owner);
            a.showAndWait();
        }
    }
}
