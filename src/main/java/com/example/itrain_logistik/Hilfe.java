package com.example.itrain_logistik;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hilfe aus zwei Teilen (Anwender 11.11.2026, Vorschlag „F1 plus Handbuch mit Ankern“):
 * F1 zeigt in jedem Fenster einen kurzen Hilfetext (texte/Hilfetexte.txt, Abschnitt nach
 * Fenstertitel); „Im Handbuch lesen“ springt an die passende Stelle des Handbuchs auf
 * itrainplus.de. Hilfe > Hilfe zeigt alle Abschnitte, Hilfe > Handbuch öffnet das Handbuch.
 * Eine eigene Fassung „Hilfetexte.txt“ im Logistik-Ordner hat Vorrang (zum Bearbeiten).
 */
public final class Hilfe {

    /** Handbuch auf der Webseite (bisher nur deutsch; Englisch folgt, Anwender 11.11.2026). */
    public static final String HANDBUCH_BASIS = "https://itrainplus.de/logistik-daten/Manual_Handbuch/";
    public static final String HANDBUCH_HTML = HANDBUCH_BASIS + "iTrain-Logistik-Handbuch.html";
    public static final String HANDBUCH_PDF = HANDBUCH_BASIS + "iTrain-Logistik-Handbuch.pdf";

    record Abschnitt(String schluessel, String titel, List<String> fenster, String anker, String text) { }

    private static List<Abschnitt> abschnitte;
    private static final Map<Window, Boolean> BEOBACHTET = new WeakHashMap<>();
    private static boolean installiert;
    private static Stage hilfeFenster;
    private static ListView<Abschnitt> liste;

    private Hilfe() { }

    // ------------------------------------------------------------------ Texte

    static synchronized List<Abschnitt> abschnitte() {
        if (abschnitte == null) {
            abschnitte = lesen();
        }
        return abschnitte;
    }

    private static List<Abschnitt> lesen() {
        List<Abschnitt> l = new ArrayList<>();
        try (InputStream in = quelle()) {
            if (in == null) {
                return l;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String schl = null, titel = "", anker = "";
            List<String> fenster = new ArrayList<>();
            StringBuilder text = null;
            String z;
            while ((z = r.readLine()) != null) {
                if (text == null && z.startsWith("#")) {
                    continue;
                }
                if (z.startsWith("=== ") && z.trim().endsWith("===")) {
                    if (schl != null) {
                        l.add(new Abschnitt(schl, titel, fenster, anker, text == null ? "" : text.toString().strip()));
                    }
                    schl = z.trim().replace("=", "").trim();
                    titel = "";
                    anker = "";
                    fenster = new ArrayList<>();
                    text = null;
                } else if (text != null) {
                    text.append(z).append('\n');
                } else if (z.startsWith("TITEL:")) {
                    titel = z.substring(6).trim();
                } else if (z.startsWith("ANKER:")) {
                    anker = z.substring(6).trim();
                } else if (z.startsWith("FENSTER:")) {
                    for (String f : z.substring(8).split("\\|")) {
                        if (!f.isBlank()) {
                            fenster.add(f.trim());
                        }
                    }
                } else if (z.startsWith("TEXT:")) {
                    text = new StringBuilder();
                }
            }
            if (schl != null) {
                l.add(new Abschnitt(schl, titel, fenster, anker, text == null ? "" : text.toString().strip()));
            }
        } catch (Exception e) {
            System.err.println("Hilfetexte: " + e.getMessage());
        }
        return l;
    }

    private static InputStream quelle() throws Exception {
        try {
            File eigen = new File(AppSettings.getInstance().getLogistikDirectory(), "Hilfetexte.txt");
            if (eigen.isFile()) {
                return new FileInputStream(eigen);
            }
        } catch (Exception ignore) {
            // kein Logistik-Ordner: eingebaute Fassung
        }
        return Hilfe.class.getResourceAsStream("Hilfetexte.txt");
    }

    /** Abschnitt zum Fenstertitel; „hauptfenster“, wenn nichts passt. */
    static Abschnitt fuerTitel(String titel) {
        String t = titel == null ? "" : titel.strip();
        for (Abschnitt a : abschnitte()) {
            for (String f : a.fenster()) {
                if (t.startsWith(f) || t.startsWith(Uebersetzung.tr(f))) {
                    return a;
                }
            }
        }
        return abschnitt("hauptfenster");
    }

    static Abschnitt abschnitt(String schluessel) {
        for (Abschnitt a : abschnitte()) {
            if (a.schluessel().equals(schluessel)) {
                return a;
            }
        }
        return abschnitte().isEmpty() ? null : abschnitte().get(0);
    }

    // ------------------------------------------------------------------ F1

    /** F1 in allen Fenstern (wie Uebersetzung.installieren). */
    public static void installieren() {
        if (installiert) {
            return;
        }
        installiert = true;
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) {
                for (Window w : c.getAddedSubList()) {
                    fenster(w);
                }
            }
        });
        for (Window w : List.copyOf(Window.getWindows())) {
            fenster(w);
        }
    }

    private static void fenster(Window w) {
        if (BEOBACHTET.put(w, Boolean.TRUE) != null) {
            return;
        }
        w.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F1) {
                e.consume();
                String titel = w instanceof Stage s ? s.getTitle() : "";
                if (w == hilfeFenster) {
                    return;
                }
                zeige(fuerTitel(titel), w);
            }
        });
    }

    // ------------------------------------------------------------------ Fenster

    /** Hilfe > Hilfe: alle Abschnitte, der zum Hauptfenster vorgewählt. */
    public static void uebersicht(Window owner) {
        zeige(abschnitt("hauptfenster"), owner);
    }

    /** Hilfe > Handbuch: Handbuch auf der Webseite. */
    public static void handbuch() {
        UpdatePruefung.oeffne(HANDBUCH_HTML);
    }

    static String handbuchLink(Abschnitt a) {
        return a == null || a.anker().isBlank() ? HANDBUCH_HTML : HANDBUCH_HTML + "#" + a.anker();
    }

    private static void zeige(Abschnitt a, Window owner) {
        if (hilfeFenster == null) {
            bauen(owner);
        }
        if (a != null) {
            liste.getSelectionModel().select(a);
            liste.scrollTo(a);
        }
        hilfeFenster.show();
        hilfeFenster.toFront();
    }

    private static void bauen(Window owner) {
        hilfeFenster = new Stage();
        hilfeFenster.setTitle(Uebersetzung.tr("Hilfe"));
        if (owner != null) {
            hilfeFenster.getIcons().setAll(owner instanceof Stage s ? s.getIcons() : List.of());
        }
        liste = new ListView<>();
        // Hauptfenster oben, danach in der Reihenfolge der Datei
        List<Abschnitt> reihe = new ArrayList<>(abschnitte());
        Abschnitt haupt = abschnitt("hauptfenster");
        if (haupt != null && reihe.remove(haupt)) {
            reihe.add(0, haupt);
        }
        liste.getItems().setAll(reihe);
        liste.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Abschnitt a, boolean leer) {
                super.updateItem(a, leer);
                setText(leer || a == null ? null : Uebersetzung.tr(a.titel()));
            }
        });
        liste.setMinWidth(170);

        Label titel = new Label();
        titel.setFont(Font.font(null, FontWeight.BOLD, 16));
        TextFlow fluss = new TextFlow();
        fluss.setLineSpacing(3);
        fluss.setPadding(new Insets(4, 8, 8, 2));
        ScrollPane sp = new ScrollPane(fluss);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        Button imHandbuch = new Button(Uebersetzung.tr("Im Handbuch lesen"));
        Button pdf = new Button(Uebersetzung.tr("Handbuch (PDF)"));
        Button zu = new Button(Uebersetzung.tr("Schließen"));
        imHandbuch.setOnAction(e -> UpdatePruefung.oeffne(handbuchLink(liste.getSelectionModel().getSelectedItem())));
        pdf.setOnAction(e -> UpdatePruefung.oeffne(HANDBUCH_PDF));
        zu.setOnAction(e -> hilfeFenster.hide());
        zu.setCancelButton(true);
        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox knoepfe = new HBox(8, imHandbuch, pdf, luecke, zu);
        knoepfe.setAlignment(Pos.CENTER_LEFT);

        Label f1 = new Label(Uebersetzung.tr("F1 zeigt in jedem Fenster die passende Hilfe."));
        f1.setStyle("-fx-opacity: 0.7;");
        VBox rechts = new VBox(8, titel, sp, f1, knoepfe);
        rechts.setPadding(new Insets(10));

        liste.getSelectionModel().selectedItemProperty().addListener((o, alt, a) -> {
            titel.setText(a == null ? "" : Uebersetzung.tr(a.titel()));
            fluss.getChildren().setAll(a == null ? List.of() : formatiert(a.text()));
        });

        SplitPane split = new SplitPane(liste, rechts);
        split.setDividerPositions(0.28);
        BorderPane root = new BorderPane(split);
        Scene sc = new Scene(root, 760, 460);
        if (owner != null && owner.getScene() != null) {
            sc.getStylesheets().setAll(owner.getScene().getStylesheets());
        }
        hilfeFenster.setScene(sc);
        WindowState.apply(hilfeFenster, "hilfe", 760, 460);
    }

    /** **fett**, Zeilen mit "- " als Aufzählung, Leerzeile als Absatz. */
    static List<Text> formatiert(String roh) {
        List<Text> l = new ArrayList<>();
        String[] zeilen = roh.split("\n", -1);
        StringBuilder absatz = new StringBuilder();
        for (int i = 0; i < zeilen.length; i++) {
            String z = zeilen[i].strip();
            if (z.startsWith("- ")) {
                flush(absatz, l);
                inline("  •  " + z.substring(2) + "\n", l);
            } else if (z.isEmpty()) {
                flush(absatz, l);
                if (!l.isEmpty()) {
                    l.add(new Text("\n"));
                }
            } else {
                if (absatz.length() > 0) {
                    absatz.append(' ');
                }
                absatz.append(z);
            }
        }
        flush(absatz, l);
        return l;
    }

    private static void flush(StringBuilder absatz, List<Text> l) {
        if (absatz.length() > 0) {
            inline(absatz + "\n", l);
            absatz.setLength(0);
        }
    }

    private static void inline(String s, List<Text> l) {
        String[] teile = s.split("\\*\\*", -1);
        for (int i = 0; i < teile.length; i++) {
            if (teile[i].isEmpty()) {
                continue;
            }
            Text t = new Text(teile[i]);
            t.getStyleClass().add("hilfe-text");
            t.setStyle("-fx-fill: -fx-text-base-color;");
            if (i % 2 == 1) {
                t.setFont(Font.font(null, FontWeight.BOLD, Font.getDefault().getSize() + 1));
            } else {
                t.setFont(Font.font(Font.getDefault().getSize() + 1));
            }
            l.add(t);
        }
    }
}
