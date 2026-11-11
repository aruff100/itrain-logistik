package com.example.itrain_logistik;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Dialog-, Hilfe- und Handbuchtexte aus bearbeitbaren Textdateien (Anwender 27.10.2026). Die Dateien liegen im
 * Projektordner {@code texte/} (Format wie die Hilfetexte von iTrain_import_export: {@code === schluessel ===},
 * {@code TITEL:}, {@code TEXT:}, {@code **fett**}, "- " für Aufzählungen, Leerzeile = neuer Absatz, # = Kommentar)
 * und werden beim Bauen als Ressource {@code com/example/itrain_logistik/<Datei>} eingebunden.
 * Platzhalter {@code {name}} setzt der Aufrufer ein.
 */
final class Texte {

    /** Ein Abschnitt: Überschrift und Fließtext. */
    record Abschnitt(String titel, String text) {
    }

    static final String DIALOGE = "Dialogtexte.txt";

    private static final Map<String, Map<String, Abschnitt>> CACHE = new java.util.HashMap<>();

    private Texte() {
    }

    /** Abschnitt einer Textdatei; fehlt er, ein Platzhalter mit dem Schlüssel (damit der Fehler sichtbar wird). */
    static Abschnitt abschnitt(String datei, String schluessel, Object... werte) {
        Abschnitt a = null;
        if (Uebersetzung.aktiv()) {
            // Dialogtexte_<sprache>.txt, sonst Deutsch (Anwender 09.11.2026)
            a = laden(datei.replace(".txt", "_" + Uebersetzung.sprache() + ".txt")).get(schluessel);
        }
        if (a == null) {
            a = laden(datei).get(schluessel);
        }
        if (a == null) {
            return new Abschnitt("[" + schluessel + "]", "Text „" + schluessel + "“ fehlt in " + datei + ".");
        }
        return new Abschnitt(einsetzen(a.titel(), werte), einsetzen(a.text(), werte));
    }

    static Abschnitt dialog(String schluessel, Object... werte) {
        return abschnitt(DIALOGE, schluessel, werte);
    }

    /** Werte als Paare: "offen", 3, "alle", 7 → {offen}=3, {alle}=7. */
    private static String einsetzen(String s, Object... werte) {
        String r = s;
        for (int i = 0; i + 1 < werte.length; i += 2) {
            r = r.replace("{" + werte[i] + "}", String.valueOf(werte[i + 1]));
        }
        return r;
    }

    static synchronized Map<String, Abschnitt> laden(String datei) {
        return CACHE.computeIfAbsent(datei, d -> {
            try (InputStream in = Texte.class.getResourceAsStream(d)) {
                return in == null ? Map.of() : lesen(in);
            } catch (IOException ex) {
                return Map.of();
            }
        });
    }

    static Map<String, Abschnitt> lesen(InputStream in) throws IOException {
        Map<String, Abschnitt> m = new LinkedHashMap<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String schluessel = null;
        String titel = "";
        StringBuilder text = null;
        String zeile;
        while ((zeile = r.readLine()) != null) {
            zeile = zeile.replace("﻿", "");
            if (zeile.startsWith("#")) {
                continue;
            }
            String t = zeile.trim();
            if (t.startsWith("===") && t.endsWith("===") && t.length() > 6) {
                if (schluessel != null) {
                    m.put(schluessel, new Abschnitt(titel, text == null ? "" : text.toString().strip()));
                }
                schluessel = t.substring(3, t.length() - 3).trim();
                titel = "";
                text = null;
            } else if (schluessel != null && text == null && t.startsWith("TITEL:")) {
                titel = t.substring(6).trim();
            } else if (schluessel != null && text == null && t.equals("TEXT:")) {
                text = new StringBuilder();
            } else if (text != null) {
                text.append(zeile).append('\n');
            }
        }
        if (schluessel != null) {
            m.put(schluessel, new Abschnitt(titel, text == null ? "" : text.toString().strip()));
        }
        return m;
    }

    /** Text als formatierter Fließtext (fett, Aufzählungen, Absätze). */
    static Node darstellen(String text, double breite) {
        VBox absaetze = new VBox(10);
        absaetze.setMinWidth(0);
        for (String absatz : text.split("\\n\\s*\\n")) {
            TextFlow f = new TextFlow();
            f.getStyleClass().add(Uebersetzung.NICHT_UEBERSETZEN); // Dialogtexte haben eigene Sprachdateien
            // Startbreite; danach folgt der Umbruch der Fensterbreite (Anwender 27.10.2026)
            f.setPrefWidth(breite);
            f.setMinWidth(0);
            f.setMaxWidth(Double.MAX_VALUE);
            String[] zeilen = absatz.split("\\n");
            for (int i = 0; i < zeilen.length; i++) {
                String z = zeilen[i];
                boolean punkt = z.startsWith("- ");
                if (punkt) {
                    z = "•  " + z.substring(2);
                }
                zeileEinfuegen(f, z);
                if (i < zeilen.length - 1) {
                    f.getChildren().add(new Text("\n"));
                }
            }
            absaetze.getChildren().add(f);
        }
        return absaetze;
    }

    private static void zeileEinfuegen(TextFlow f, String z) {
        String[] teile = z.split("\\*\\*", -1);
        for (int i = 0; i < teile.length; i++) {
            if (teile[i].isEmpty()) {
                continue;
            }
            Text t = new Text(teile[i]);
            t.setFont(Font.font(null, i % 2 == 1 ? FontWeight.BOLD : FontWeight.NORMAL, 13.5));
            t.setStyle("-fx-fill: -fx-text-base-color;"); // folgt dem Farbschema (hell/dunkel)
            f.getChildren().add(t);
        }
    }

    /**
     * Dialog mit einem Abschnitt als Inhalt. {@code knoepfe}: links der Abbruch-Knopf, rechts der OK-Knopf (Text
     * null = nur ein Knopf).
     *
     * @return true, wenn der OK-Knopf gewählt wurde
     */
    static boolean zeige(Window owner, String fensterTitel, Abschnitt a, String abbruch, String ok) {
        return zeige(owner, fensterTitel, a, abbruch, ok, null);
    }

    /**
     * Wie {@link #zeige(Window, String, Abschnitt, String, String)}, mit Kästchen „Dialog nicht mehr anzeigen“
     * (Anwender 09.11.2026), wenn {@code abschalten} gesetzt ist. Ist der Dialog abgeschaltet, erscheint er nicht und
     * es gilt der OK-Knopf. Wieder einblenden: Voreinstellungen > Ansicht.
     */
    static boolean zeige(Window owner, String fensterTitel, Abschnitt a, String abbruch, String ok, String abschalten) {
        List<String> k = new java.util.ArrayList<>();
        k.add(abbruch);
        k.add(ok);
        return waehle(owner, fensterTitel, a, abschalten, k) == 1;
    }

    /**
     * Dialog mit beliebig vielen Knöpfen: {@code knoepfe[0]} ist der Abbruch-Knopf links (null = keiner), die übrigen
     * stehen rechts in dieser Reihenfolge; der letzte ist der Standard (Return, und bei abgeschaltetem Dialog).
     *
     * @return Index des gewählten Knopfs; 0 = Abbruch bzw. Fenster geschlossen
     */
    static int waehle(Window owner, String fensterTitel, Abschnitt a, String abschalten, List<String> knoepfe) {
        AppSettings st = AppSettings.getInstance();
        if (abschalten != null && st.isDialogAbgeschaltet(abschalten)) {
            return knoepfe.size() - 1;
        }
        Dialog<ButtonType> d = new Dialog<>();
        if (owner != null) {
            d.initOwner(owner);
        }
        d.setTitle(fensterTitel);
        d.setHeaderText(a.titel().isBlank() ? null : a.titel());
        ScrollPane sp = new ScrollPane(darstellen(a.text(), 600));
        sp.setFitToWidth(true);
        sp.setPrefViewportWidth(620);
        sp.setPrefViewportHeight(Math.min(560, 60 + a.text().length() / 3.2));
        sp.setPadding(new Insets(6));
        sp.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        javafx.scene.control.CheckBox aus = new javafx.scene.control.CheckBox("Dialog nicht mehr anzeigen");
        if (abschalten != null) {
            VBox inhalt = new VBox(10, sp, aus);
            VBox.setVgrow(sp, javafx.scene.layout.Priority.ALWAYS);
            d.getDialogPane().setContent(inhalt);
        } else {
            d.getDialogPane().setContent(sp);
        }
        List<ButtonType> typen = new java.util.ArrayList<>();
        for (int i = 0; i < knoepfe.size(); i++) {
            String t = knoepfe.get(i);
            if (t == null) {
                typen.add(null);
                continue;
            }
            ButtonBar.ButtonData art = i == 0 ? ButtonBar.ButtonData.CANCEL_CLOSE
                    : i == knoepfe.size() - 1 ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.OTHER;
            typen.add(new ButtonType(t, art));
        }
        d.getDialogPane().getButtonTypes().setAll(typen.stream().filter(java.util.Objects::nonNull).toList());
        d.setResizable(true);
        Fenster.merken(d, "text." + fensterTitel);
        if (owner != null && owner.getScene() != null) {
            d.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        Optional<ButtonType> r = d.showAndWait();
        int wahl = r.isPresent() ? typen.indexOf(r.get()) : 0;
        if (wahl < 0) {
            wahl = 0;
        }
        if (abschalten != null && aus.isSelected() && wahl != 0) {
            st.setDialogAbgeschaltet(abschalten, fensterTitel + (a.titel().isBlank() ? "" : " – " + a.titel()), true);
        }
        return wahl;
    }
}
