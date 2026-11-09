package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlageXml;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.BetriebVorlage;
import com.example.itrain_logistik.kern.EchtzeitKern;
import com.example.itrain_logistik.kern.Kreislauf;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.ObjektArt;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.Tf3Daten;
import com.example.itrain_logistik.kern.Uebergabe;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Anlagen-Editor ("Anlage bearbeiten"): ersetzt das fruehere "Übersicht ändern" mit seinen
 * Ebenen 1 bis 3. Hier legt der Anwender Staedte, Doerfer, Stationen mit Gleisen (je ein
 * iTrain-Block), Betriebe nach Vorlage, Lager und Uebergaben an und verbindet Stationen mit
 * ihrem Einzugsbereich. Beim Anlegen oder Aendern eines Betriebs zeigt
 * {@link ErforderlichDialog} ("Zusätzlich erforderlich"), was noch fehlt. "Speichern" schreibt die
 * .logz, "Abbruch" stellt den Stand beim Öffnen bzw. letzten Speichern wieder her.
 */
public final class AnlagenEditorWindow {

    private static Stage stage;
    private static AnlagenCanvas offenerCanvas;
    /** Verkehrs-Assistent (vom Hauptfenster gesetzt, Anwender 27.10.2026): bekommt das Editorfenster als Besitzer. */
    private static java.util.function.Consumer<Stage> assistent;

    static void setAssistent(java.util.function.Consumer<Stage> a) {
        assistent = a;
    }

    /** Nach Änderungen außerhalb (z. B. Assistent aus dem Menü) die offene Zeichenfläche neu aufbauen. */
    static void neuZeichnenWennOffen() {
        if (offeneLeiste != null) {
            offeneLeiste.alleNeu();
        }
    }

    private static ReiterLeiste offeneLeiste;

    private AnlagenEditorWindow() {
    }

    /**
     * @param onSpeichern speichert die .logz (Knopf "Speichern"); der Editor arbeitet direkt auf der
     *                    Anlage, "Abbruch" stellt den Stand beim Öffnen wieder her
     */
    public static void show(Stage owner, Anlage anlage, EchtzeitKern kern, Supplier<List<BlockEntry>> bloecke,
            Runnable onChanged, Runnable onSpeichern) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage = new Stage();
        stage.setTitle("Anlage bearbeiten");
        stage.initOwner(owner);
        // Orte mit Güterbedarf am Personenbahnhof bekommen ihren Güterbahnhof, falls er noch fehlt.
        if (!anlage.gueterbahnhoefeErgaenzen().isEmpty() | !anlage.gueterObjekteZumGueterbahnhof().isEmpty()) {
            onChanged.run();
        }
        Anlage[] stand = {AnlageXml.kopie(anlage)};
        boolean[] geaendertSeitStand = {false};
        ReiterLeiste[] leiste0 = new ReiterLeiste[1];
        Runnable geaendert = () -> {
            geaendertSeitStand[0] = true;
            onChanged.run();
        };
        // Reiter je Tab/Modul (Anwender 09.11.2026): jede Zeichenfläche legt Neues in ihrem Reiter an
        java.util.function.Function<com.example.itrain_logistik.kern.Bereich, AnlagenCanvas> fabrik = bereich -> {
            AnlagenCanvas[] c = new AnlagenCanvas[1];
            c[0] = new AnlagenCanvas(anlage, kern, true,
                    o -> {
                        if (bearbeite(o, anlage, bloecke)) {
                            leiste0[0].alleNeu();
                            geaendert.run();
                        }
                    },
                    () -> {
                        geaendert.run();
                        if (leiste0[0] != null) {
                            leiste0[0].abgleichen();
                        }
                    },
                    (art, punkt) -> {
                        if (lege(art, punkt, anlage, bloecke, bereich)) {
                            leiste0[0].alleNeu();
                            geaendert.run();
                        }
                    }, bereich);
            return c[0];
        };
        leiste0[0] = new ReiterLeiste(anlage, fabrik, true, geaendert);
        ReiterLeiste reiter = leiste0[0];
        AnlagenCanvas[] canvas = {reiter.getHaupt()};
        ((javafx.scene.control.TabPane) reiter.getView()).getSelectionModel().selectedItemProperty().addListener(
                (o, a, b) -> offenerCanvas = reiter.aktuell());

        Button speichern = new Button("Speichern und Schließen");
        speichern.setDefaultButton(true);
        speichern.setOnAction(e -> {
            onSpeichern.run();
            stand[0] = AnlageXml.kopie(anlage);
            geaendertSeitStand[0] = false;
            stage.close();
        });
        Button abbruch = new Button("Abbruch");
        abbruch.setOnAction(e -> {
            if (geaendertSeitStand[0]) {
                Alert frage = new Alert(Alert.AlertType.CONFIRMATION,
                        "Alle Änderungen seit dem Öffnen bzw. dem letzten Speichern verwerfen?",
                        ButtonType.YES, ButtonType.NO);
                frage.initOwner(stage);
                frage.setHeaderText("Abbruch");
                if (frage.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                    return;
                }
                anlage.uebernehmeVon(stand[0]);
                geaendertSeitStand[0] = false;
                onChanged.run();
            }
            stage.close();
        });

        Button pruefen = new Button("Kreislauf prüfen…");
        pruefen.setOnAction(e -> {
            boolean neu = ErforderlichDialog.zeige(stage, anlage, new ArrayList<>(anlage.betriebe()), true);
            for (Ort o : anlage.orte()) {
                for (Station s : anlage.stationenFuer(o.getId())) {
                    if (s.getArt() == ObjektArt.BAHNHOF) {
                        neu |= ObjektDialoge.nachVerbinden(stage, anlage, o, s);
                        break;
                    }
                }
            }
            if (neu) {
                reiter.alleNeu();
                geaendert.run();
            }
            // Ergebnis (Anwender 14.10.2026): Titel "Kreislauf prüfen", Kopf "Ergebnis der Prüfung"
            com.example.itrain_logistik.kern.KreislaufPruefung.Ergebnis erg =
                    com.example.itrain_logistik.kern.KreislaufPruefung.pruefe(anlage);
            StringBuilder text = new StringBuilder();
            if (erg.allesOk()) {
                text.append("Alle Betriebe haben Lieferanten und Abnehmer.");
            } else {
                if (erg.fehlendeLieferanten() > 0) {
                    text.append("Es fehlen ").append(erg.fehlendeLieferanten()).append(" Lieferanten.\n");
                }
                if (erg.fehlendeAbnehmer() > 0) {
                    text.append("Es fehlen ").append(erg.fehlendeAbnehmer()).append(" Abnehmer.\n");
                }
                if (!erg.reisende().isEmpty()) {
                    text.append("Keine Reisenden möglich, weiterer Bahnhof nötig:\n");
                    erg.reisende().forEach(r -> text.append("  • ").append(r).append("\n"));
                }
            }
            Alert ergebnis = new Alert(erg.allesOk() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    text.toString().trim());
            ergebnis.initOwner(stage);
            ergebnis.setTitle("Kreislauf prüfen");
            ergebnis.setHeaderText("Ergebnis der Prüfung");
            ergebnis.showAndWait();
        });
        Button assistentKnopf = new Button("Verkehrs-Assistent…");
        assistentKnopf.setTooltip(new javafx.scene.control.Tooltip("Verkehr Schritt für Schritt aufbauen: Bahnhöfe, "
                + "Gleise, Umgebung, Lieferketten"));
        assistentKnopf.setOnAction(e -> {
            if (assistent != null) {
                assistent.accept(stage);
                reiter.alleNeu();
                geaendert.run();
            }
        });
        Button massstab = new Button("Lagergrößen…");
        massstab.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog(ObjektDialoge.zahl(anlage.getMengenMassstab()));
            d.initOwner(stage);
            d.setTitle("Faktor für die Lagergrößen");
            d.setHeaderText("Faktor für die Lagergrößen beim Anlegen eines Betriebs.\n"
                    + "Beispiel 0,1: Das Sägewerk-Eingangslager hat 40 statt 400 Einheiten.\n"
                    + "Gilt für neu angelegte Betriebe.");
            d.showAndWait().ifPresent(t -> {
                anlage.setMengenMassstab(ObjektDialoge.parse(t, anlage.getMengenMassstab()));
                geaendert.run();
            });
        });
        Label hinweis = new Label("Gleise ordnen Sie in der Station zu (Doppelklick). "
                + "Einzugsbereich: Rechtsklick auf das Objekt, „Mit Station verbinden…“.");
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        // Pastellfarben wie die Knöpfe im Ribbon des Hauptfensters (Anwender 27.10.2026)
        MainController.pastell(assistentKnopf, "#d6efc7");
        MainController.pastell(pruefen, "#fbe3bd");
        MainController.pastell(massstab, "#e2d9f3");
        ToolBar leiste = new ToolBar(assistentKnopf, pruefen, massstab, hinweis);
        offenerCanvas = canvas[0];
        offeneLeiste = reiter;

        // "Abbruch" und "Speichern und Schließen" unten über den Reitern
        HBox knopfLeiste = new HBox(8, platz, abbruch, speichern);
        knopfLeiste.setPadding(new Insets(6, 10, 6, 10));
        BorderPane root = new BorderPane(reiter.getView());
        root.setBottom(new javafx.scene.layout.VBox(new javafx.scene.control.Separator(), knopfLeiste));
        root.setTop(leiste);
        root.setPadding(new Insets(0));
        Scene scene = new Scene(root, 1200, 800);
        ThemeManager.apply(scene, AppSettings.getInstance().getTheme());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            if (!geaendertSeitStand[0]) {
                return;
            }
            ButtonType ja = new ButtonType("Speichern", javafx.scene.control.ButtonBar.ButtonData.YES);
            ButtonType nein = new ButtonType("Verwerfen", javafx.scene.control.ButtonBar.ButtonData.NO);
            Alert frage = new Alert(Alert.AlertType.CONFIRMATION, "Die Anlage wurde geändert.", ja, nein,
                    ButtonType.CANCEL);
            frage.initOwner(stage);
            frage.setHeaderText("Änderungen speichern?");
            ButtonType antwort = frage.showAndWait().orElse(ButtonType.CANCEL);
            if (antwort == ja) {
                onSpeichern.run();
            } else if (antwort == nein) {
                anlage.uebernehmeVon(stand[0]);
                onChanged.run();
            } else {
                e.consume();
            }
        });
        stage.setOnHidden(e -> {
            reiter.dispose();
            offenerCanvas = null;
            offeneLeiste = null;
            stage = null;
        });
        // Nach setOnHidden, damit die Fensterlage beim Schließen weiter gespeichert wird.
        WindowState.apply(stage, "anlagenEditor", 1200, 800);
        stage.show();
    }

    private static boolean bearbeite(AnlagenObjekt o, Anlage anlage, Supplier<List<BlockEntry>> bloecke) {
        boolean geaendert = ObjektDialoge.bearbeite(stage, o, anlage, bloecke.get());
        if (geaendert && o instanceof Betrieb b) {
            ErforderlichDialog.zeige(stage, anlage, List.of(b));
        }
        return geaendert;
    }

    private static boolean lege(ObjektArt art, Point2D punkt, Anlage anlage, Supplier<List<BlockEntry>> bloecke,
            com.example.itrain_logistik.kern.Bereich bereich) {
        // Tabs und Module hängen an der Anlagen-Infrastruktur, also nur im ersten Reiter (Anwender 09.11.2026)
        if (art == ObjektArt.MODUL || art == ObjektArt.REITER) {
            int max = AppSettings.getInstance().getMaxReiter();
            if (anlage.bereiche().size() >= max) {
                Alert a = new Alert(Alert.AlertType.INFORMATION, "Höchstens " + max + " Reiter (Tabs und Module). "
                        + "Die Zahl lässt sich in den Voreinstellungen > Reiter ändern.");
                a.initOwner(stage);
                a.setHeaderText("Keine weiteren Reiter");
                a.showAndWait();
                return false;
            }
            if (bereich != null) {
                Alert a = new Alert(Alert.AlertType.INFORMATION, (art == ObjektArt.MODUL ? "Ein Modul" : "Ein Tab")
                        + " hängt an der Anlagen-Infrastruktur und wird deshalb im ersten Reiter „"
                        + anlage.getHauptReiterName() + "“ angelegt.");
                a.initOwner(stage);
                a.setHeaderText(null);
                a.showAndWait();
                punkt = new Point2D(anlage.infrastruktur().getX() + 260, anlage.infrastruktur().getY());
                bereich = null;
            }
        }
        AnlagenObjekt neu;
        if (art.istOrt()) {
            neu = new Ort(null, art, anlage.freierName(art.getLabel()));
        } else if (art.istStation()) {
            neu = new Station(null, art, anlage.freierName(art.getLabel()));
        } else if (art == ObjektArt.LAGER) {
            neu = new Lager(null, anlage.freierName("Lager"));
        } else if (art == ObjektArt.UEBERGABE) {
            neu = new Uebergabe(null, anlage.freierName("Übergabe"));
        } else if (art == ObjektArt.MODUL) {
            neu = new com.example.itrain_logistik.kern.Modul(null, anlage.freierName("Modul"));
        } else if (art == ObjektArt.REITER) {
            neu = new com.example.itrain_logistik.kern.Reiter(null, anlage.freierName("Tab"));
        } else {
            return legeBetrieb(punkt, anlage, bloecke, bereich);
        }
        neu.setReiterId(bereich != null ? bereich.getId() : null);
        if (neu instanceof Station st && bereich != null) {
            bereich.getStationen().add(st.getId()); // im Reiter angelegt: hängt am Tab/Modul
        }
        anlage.platziereNeu(neu, punkt.getX(), punkt.getY());
        boolean ok = ObjektDialoge.bearbeite(stage, neu, anlage, bloecke.get());
        // Modul ohne Streckengleis geht nicht (Anwender 09.11.2026): Abbruch beim Anlegen entfernt es wieder
        if (!ok && neu instanceof com.example.itrain_logistik.kern.Modul) {
            anlage.entferne(neu);
        }
        return true;
    }

    private static boolean legeBetrieb(Point2D punkt, Anlage anlage, Supplier<List<BlockEntry>> bloecke,
            com.example.itrain_logistik.kern.Bereich bereich) {
        List<String> namen = new ArrayList<>();
        List<BetriebVorlage> vorlagen = new ArrayList<>(Tf3Daten.vorlagen());
        vorlagen.sort((x, y) -> x.name().compareTo(y.name()));
        for (BetriebVorlage v : vorlagen) {
            namen.add(v.name());
        }
        String eigener = "Eigener Betrieb (ohne Vorlage)";
        namen.add(eigener);
        // Auswahl mit Angabe, was der Betrieb braucht und liefert und ob das jemand abnimmt (Anwender 31.10.2026)
        javafx.scene.control.Dialog<String> d = new javafx.scene.control.Dialog<>();
        d.initOwner(stage);
        d.setTitle("Neuer Betrieb");
        d.setHeaderText("Vorlage wählen.\n"
                + "Rohstoffbetriebe brauchen Dünger, Werkzeuge oder Maschinen als Eingang.\n"
                + "Danach zeigt „Zusätzlich erforderlich“, was der Betrieb noch braucht.");
        javafx.scene.control.ComboBox<String> auswahl = new javafx.scene.control.ComboBox<>(
                javafx.collections.FXCollections.observableArrayList(namen));
        auswahl.setValue(namen.get(0));
        auswahl.setMaxWidth(Double.MAX_VALUE);
        Label benoetigt = new Label();
        Label liefert = new Label();
        javafx.scene.layout.VBox abnehmerZeilen = new javafx.scene.layout.VBox(2);
        for (Label l : List.of(benoetigt, liefert)) {
            l.setWrapText(true);
        }
        Runnable zeigen = () -> {
            abnehmerZeilen.getChildren().clear();
            int i = namen.indexOf(auswahl.getValue());
            if (i < 0 || i >= vorlagen.size()) {
                benoetigt.setText("Eigener Betrieb: Eingänge und Ausgänge stellst Du danach selbst ein.");
                liefert.setText("");
                return;
            }
            BetriebVorlage v = vorlagen.get(i);
            benoetigt.setText("Benötigt: " + aufzaehlung(anlage, v.eingangslager().keySet()));
            liefert.setText("Liefert: " + aufzaehlung(anlage, v.ausgangslager().keySet()));
            for (String g : v.ausgangslager().keySet()) {
                List<String> wer = new ArrayList<>();
                for (AnlagenObjekt o : Kreislauf.abnehmer(anlage, g, null)) {
                    wer.add(o.getAnzeigeName());
                }
                if (wer.isEmpty() && Kreislauf.uebergabeNimmt(anlage, g)) {
                    wer.add("Übergabe/Schattenbahnhof");
                }
                Label z = new Label("  " + anlage.gutName(g) + ": " + (wer.isEmpty()
                        ? "Bedarf muss eingestellt werden" : "wird benötigt von " + String.join(", ", wer)));
                z.setWrapText(true);
                z.setStyle(wer.isEmpty() ? "-fx-text-fill: #d04040; -fx-font-weight: bold;" : "");
                abnehmerZeilen.getChildren().add(z);
            }
        };
        auswahl.valueProperty().addListener((obs, alt, neu) -> zeigen.run());
        zeigen.run();
        HBox zeile = new HBox(8, new Label("Vorlage:"), auswahl);
        zeile.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(auswahl, Priority.ALWAYS);
        javafx.scene.layout.VBox inhalt = new javafx.scene.layout.VBox(8, zeile, new javafx.scene.control.Separator(),
                benoetigt, liefert, abnehmerZeilen);
        inhalt.setPadding(new Insets(10));
        inhalt.setPrefWidth(560);
        d.getDialogPane().setContent(inhalt);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(b -> b == ButtonType.OK ? auswahl.getValue() : null);
        Optional<String> wahl = d.showAndWait();
        if (wahl.isEmpty()) {
            return false;
        }
        Betrieb b;
        if (wahl.get().equals(eigener)) {
            b = new Betrieb(null, anlage.freierName("Betrieb"));
            b.setReiterId(bereich != null ? bereich.getId() : null);
            anlage.platziereNeu(b, punkt.getX(), punkt.getY());
            ObjektDialoge.bearbeite(stage, b, anlage, bloecke.get());
        } else {
            BetriebVorlage v = vorlagen.get(namen.indexOf(wahl.get()));
            b = anlage.neuerBetrieb(v, punkt.getX(), punkt.getY());
            b.setReiterId(bereich != null ? bereich.getId() : null);
            anlage.platziereNeu(b, punkt.getX(), punkt.getY());
        }
        ErforderlichDialog.zeige(stage, anlage, List.of(b));
        return true;
    }

    /** „Dünger, Werkzeuge und Maschinen“ bzw. „nichts“. */
    private static String aufzaehlung(Anlage anlage, java.util.Collection<String> ids) {
        List<String> n = ids.stream().map(anlage::gutName).toList();
        if (n.isEmpty()) {
            return "nichts";
        }
        return n.size() == 1 ? n.get(0) : String.join(", ", n.subList(0, n.size() - 1)) + " und " + n.get(n.size() - 1);
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }
}
