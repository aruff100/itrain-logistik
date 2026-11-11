package com.example.itrain_logistik;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Stellt Dateien zum Weitergeben zusammen (Anwender 11.11.2026): Hilfe > „Mit der KI prüfen…“ (Prompt, Logistik-,
 * Fahrzeug- und iTrain-Datei als lesbares XML) und Hilfe > „Kontakt…“ (Mail an den Autor mit den Dateien der
 * Konfiguration). Die Dateien liegen in einem eigenen Ordner unter dem Logistik-Ordner; das Fenster zeigt sie und
 * lässt sie per Drag &amp; Drop in den Chat der KI bzw. in die E-Mail ziehen.
 */
public final class DateienFenster {

    /** Kontaktadresse des Autors (wie in iTrain Import/Export). */
    static final String KONTAKT = "aruff@allesruff.de";
    static final String PROMPT_VERSION = "1.0";

    private DateienFenster() {
    }

    /** Hilfe > „Mit der KI prüfen…“. */
    public static void kiPruefen(Stage owner, LogistikDocument doc, Runnable speichern) {
        if (!vorbereitet(owner, doc, speichern, "Mit der KI prüfen")) {
            return;
        }
        try {
            File ordner = ordner("KI-Pruefung", doc);
            List<File> dateien = new ArrayList<>();
            String prompt = prompt();
            File p = new File(ordner, "iTrain-Logistik_KI-Pruefung_Prompt_v" + PROMPT_VERSION + ".txt");
            Files.writeString(p.toPath(), prompt, StandardCharsets.UTF_8);
            dateien.add(p);
            File logz = doc.getLogistikFile();
            File lx = new File(ordner, basis(logz.getName()) + ".logistik.xml");
            if (entpacke(logz, null, lx)) {
                dateien.add(lx);
            }
            File fz = doc.getFahrzeugFile();
            if (fz != null && fz.isFile()) {
                dateien.add(kopiere(fz, ordner));
            }
            File tcdz = doc.getSnapshot() != null ? doc.getSnapshot().sourceFile() : null;
            if (tcdz != null && tcdz.isFile()) {
                File tx = new File(ordner, basis(tcdz.getName()) + ".tcd.xml");
                if (entpacke(tcdz, ".tcd", tx)) {
                    dateien.add(tx);
                } else {
                    dateien.add(kopiere(tcdz, ordner));
                }
            }
            zeige(owner, "Mit der KI prüfen", "Diese Dateien in den Chat der KI ziehen",
                    "Starten Sie einen neuen Chat (z. B. claude.ai). Ziehen Sie alle Dateien mit der Maus in den Chat "
                            + "und schreiben Sie dazu: „Bitte nach der Prompt-Datei vorgehen.“ Die KI prüft, ob die "
                            + "Logistik vollständig und mit der iTrain-Anlage verknüpft ist, fragt nach der Sprache und "
                            + "am Ende, ob ein Dokument daraus werden soll. Die iTrain-Datei wird dabei nicht nach dem "
                            + "iTrain-Handbuch geprüft, nur auf die Verknüpfung.",
                    ordner, dateien, prompt, null);
        } catch (IOException ex) {
            fehler(owner, ex);
        }
    }

    /** Hilfe > „Kontakt…“: Mailprogramm mit der Adresse des Autors öffnen und die Dateien zum Anhängen zeigen. */
    public static void kontakt(Stage owner, LogistikDocument doc, Runnable speichern) {
        List<File> dateien = new ArrayList<>();
        File ordner = null;
        try {
            if (doc.isOpen() && vorbereitet(owner, doc, speichern, "Kontakt")) {
                ordner = ordner("Kontakt", doc);
                dateien.add(kopiere(doc.getLogistikFile(), ordner));
                File fz = doc.getFahrzeugFile();
                if (fz != null && fz.isFile()) {
                    dateien.add(kopiere(fz, ordner));
                }
                File tcdz = doc.getSnapshot() != null ? doc.getSnapshot().sourceFile() : null;
                if (tcdz != null && tcdz.isFile()) {
                    dateien.add(kopiere(tcdz, ordner));
                }
            } else {
                ordner = ordner("Kontakt", null);
            }
            File info = new File(ordner, "Systeminfo.txt");
            Files.writeString(info.toPath(), systeminfo(), StandardCharsets.UTF_8);
            dateien.add(info);
        } catch (IOException ex) {
            fehler(owner, ex);
            return;
        }
        Runnable mail = () -> mailOeffnen(owner);
        mail.run();
        zeige(owner, "Kontakt", "Bei Fragen zum Inhalt bitte diese Dateien mit senden",
                "Ihr E-Mail-Programm öffnet eine neue Nachricht an " + KONTAKT + ". Ziehen Sie die Dateien mit der "
                        + "Maus in die Nachricht (als Anhang). Öffnet sich kein E-Mail-Programm, schreiben Sie an "
                        + KONTAKT + " und hängen Sie die Dateien aus dem Ordner an.",
                ordner, dateien, null, mail);
    }

    /** Öffnet eine neue E-Mail an den Autor (mailto, Standard-Mailprogramm). */
    static void mailOeffnen(Stage owner) {
        try {
            String betreff = URLEncoder.encode("iTrain-Logistik Version " + AppInfo.getVersion(),
                    StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create("mailto:" + KONTAKT + "?subject=" + betreff);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                new Thread(() -> {
                    try {
                        Desktop.getDesktop().mail(uri);
                    } catch (Exception ignored) {
                        // Hinweis steht im Fenster
                    }
                }, "mailto").start();
            }
        } catch (Exception ignored) {
            // Hinweis steht im Fenster
        }
    }

    // ------------------------------------------------------------------ Fenster

    private static void zeige(Stage owner, String titel, String kopf, String text, File ordner, List<File> dateien,
            String prompt, Runnable mail) {
        Stage st = new Stage();
        st.setTitle(titel);
        if (owner != null) {
            st.initOwner(owner);
        }
        Label k = new Label(kopf);
        k.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label t = new Label(text);
        t.setWrapText(true);
        ListView<File> liste = new ListView<>();
        liste.getItems().setAll(dateien);
        liste.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        liste.setPrefHeight(Math.min(260, 34 * dateien.size() + 8));
        liste.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File f, boolean empty) {
                super.updateItem(f, empty);
                if (empty || f == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label sym = new Label(f.getName().endsWith(".txt") ? "📝" : "📄");
                    sym.setStyle("-fx-font-size: 16px;");
                    setGraphic(sym);
                    setText(f.getName() + "   (" + groesse(f.length()) + ")");
                }
            }
        });
        // ziehen: markierte Dateien, ohne Markierung alle
        liste.setOnDragDetected(e -> {
            List<File> l = new ArrayList<>(liste.getSelectionModel().getSelectedItems());
            ziehen(liste.startDragAndDrop(TransferMode.COPY), l.isEmpty() ? dateien : l);
            e.consume();
        });
        Label griff = new Label("⇲  Hier anfassen und alle Dateien auf einmal hinüberziehen");
        griff.setMaxWidth(Double.MAX_VALUE);
        griff.setAlignment(Pos.CENTER);
        griff.setPadding(new Insets(14));
        griff.setStyle("-fx-border-color: #6a8fc4; -fx-border-style: dashed; -fx-border-width: 2; "
                + "-fx-border-radius: 6; -fx-background-color: #e8f0fb; -fx-background-radius: 6; "
                + "-fx-text-fill: #16307a; -fx-font-weight: bold; -fx-cursor: open-hand;");
        griff.setOnDragDetected(e -> {
            ziehen(griff.startDragAndDrop(TransferMode.COPY), dateien);
            e.consume();
        });
        Label ort = new Label("Ordner: " + ordner.getAbsolutePath());
        ort.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        ort.setWrapText(true);
        Button oeffnen = new Button("Ordner öffnen");
        MainController.pastell(oeffnen, "#e2d9f3");
        oeffnen.setOnAction(e -> oeffne(ordner));
        HBox knoepfe = new HBox(8, oeffnen);
        if (prompt != null) {
            Button kopieren = new Button("Prompt kopieren");
            MainController.pastell(kopieren, "#d6efc7");
            kopieren.setOnAction(e -> {
                ClipboardContent c = new ClipboardContent();
                c.putString(prompt);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(c);
                kopieren.setText("Prompt kopiert ✔");
            });
            knoepfe.getChildren().add(kopieren);
        }
        if (mail != null) {
            Button m = new Button("E-Mail öffnen");
            MainController.pastell(m, "#d9eef0");
            m.setOnAction(e -> mail.run());
            knoepfe.getChildren().add(m);
        }
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        Button zu = new Button("Schließen");
        zu.setDefaultButton(true);
        zu.setOnAction(e -> st.close());
        knoepfe.getChildren().addAll(platz, zu);
        VBox root = new VBox(10, k, t, griff, liste, ort, knoepfe);
        root.setPadding(new Insets(14));
        VBox.setVgrow(liste, Priority.ALWAYS);
        Scene sc = new Scene(root, 620, 470);
        ThemeManager.apply(sc, AppSettings.getInstance().getTheme());
        st.setScene(sc);
        WindowState.apply(st, "dateien." + titel, 620, 470);
        // über dem Hauptfenster bleiben, damit man aus ihm in den Browser bzw. die Mail ziehen kann
        st.setAlwaysOnTop(true);
        st.show();
    }

    private static void ziehen(Dragboard db, List<File> dateien) {
        ClipboardContent c = new ClipboardContent();
        c.putFiles(new ArrayList<>(dateien));
        db.setContent(c);
    }

    // ------------------------------------------------------------------ Dateien

    /** Datei offen und gespeichert? Fragt nach dem Speichern, wenn sie geändert ist. */
    private static boolean vorbereitet(Stage owner, LogistikDocument doc, Runnable speichern, String titel) {
        if (!doc.isOpen() || doc.getLogistikFile() == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Bitte zuerst eine Logistik-Datei (.logz) öffnen.");
            a.initOwner(owner);
            a.setTitle(titel);
            a.setHeaderText(null);
            a.showAndWait();
            return false;
        }
        if (doc.isDirty()) {
            ButtonType ja = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
            ButtonType nein = new ButtonType("Ohne Änderungen", ButtonBar.ButtonData.NO);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Die Logistik-Datei hat ungespeicherte Änderungen. "
                    + "Zusammengestellt wird der gespeicherte Stand.", nein, ja);
            a.initOwner(owner);
            a.setTitle(titel);
            a.setHeaderText("Vorher speichern?");
            if (a.showAndWait().orElse(nein) == ja) {
                speichern.run();
            }
        }
        return true;
    }

    private static File ordner(String art, LogistikDocument doc) throws IOException {
        String name = (doc != null && doc.getLogistikFile() != null ? basis(doc.getLogistikFile().getName()) + "_"
                : "") + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        File o = new File(new File(AppSettings.getInstance().getLogistikDirectory(), art), name);
        Files.createDirectories(o.toPath());
        return o;
    }

    private static File kopiere(File quelle, File ordner) throws IOException {
        File ziel = new File(ordner, quelle.getName());
        Files.copy(quelle.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return ziel;
    }

    /**
     * Schreibt den ersten Eintrag eines ZIP-Archivs (mit der Endung {@code endung}, sonst den ersten überhaupt) nach
     * {@code ziel}. Ist die Datei kein ZIP, wird sie unverändert kopiert.
     */
    static boolean entpacke(File zip, String endung, File ziel) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                if (!e.isDirectory() && (endung == null || e.getName().toLowerCase().endsWith(endung))) {
                    Files.copy(in, ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            }
        } catch (IOException ignored) {
            // kein ZIP
        }
        try {
            Files.copy(zip.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /** Der Prompt aus den mitgelieferten Texten; eine Datei gleichen Namens im Logistik-Ordner geht vor. */
    static String prompt() throws IOException {
        File eigen = new File(AppSettings.getInstance().getLogistikDirectory(), "KI-Pruefung_Prompt.txt");
        if (eigen.isFile()) {
            return Files.readString(eigen.toPath(), StandardCharsets.UTF_8);
        }
        try (InputStream in = DateienFenster.class.getResourceAsStream("KI-Pruefung_Prompt.txt")) {
            if (in == null) {
                throw new IOException("Prompt KI-Pruefung_Prompt.txt fehlt im Programm");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String systeminfo() {
        return "iTrain-Logistik " + AppInfo.getBuildNumber() + "\n"
                + "Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n"
                + "JavaFX " + System.getProperty("javafx.runtime.version", "?") + "\n"
                + "Betriebssystem " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch") + "\n"
                + "Sprache " + AppSettings.getInstance().getSprache() + "\n"
                + "Erstellt " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "\n";
    }

    static void oeffne(File ordner) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().open(ordner);
            } catch (Exception ignored) {
                // Pfad steht im Fenster
            }
        }, "ordner").start();
    }

    private static String basis(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    private static String groesse(long b) {
        return b < 1024 ? b + " Byte" : b < 1024 * 1024 ? (b / 1024) + " KB"
                : String.format("%.1f MB", b / 1024.0 / 1024.0);
    }

    private static void fehler(Stage owner, Exception ex) {
        Alert a = new Alert(Alert.AlertType.ERROR, "Dateien konnten nicht zusammengestellt werden: " + ex.getMessage());
        a.initOwner(owner);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
