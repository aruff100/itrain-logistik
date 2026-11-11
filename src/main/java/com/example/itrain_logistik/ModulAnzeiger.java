package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Modul;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.ZugZustand;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Modul-Anzeiger (Anwender 11.11.2026): je Block deutlich, welcher Zug mit welcher Ladung gerade darin steht bzw.
 * fährt, und wohin er fährt – dunkelgrün mit weißer Schrift, zu lange Zeilen als Laufschrift. Da in einem Block nur
 * ein Zug sein kann, steht alles ausführlich in mehreren Zeilen. Als Fenster und im Browser (Seite
 * {@code /anzeiger/<modul>[/<block>]} der Browser Logistik, mit QR-Code zum Ausdrucken), damit man an einer
 * Ausstellungsanlage mit dem Handy am Block mitsehen kann.
 * <p>
 * Die Daten baut {@link #aktualisiere} jede Sekunde auf dem JavaFX-Thread (aus dem Kern und, falls verbunden, XTC);
 * Webserver und Fenster lesen nur den fertigen Stand.
 */
public final class ModulAnzeiger {

    /** Hintergrund und Schrift (Anwender: dunkelgrün, weiß). */
    static final String GRUEN = "#0d4a26";
    static final String GRUEN_HELL = "#1b6b3a";
    static final String SCHRIFT = "#ffffff";
    static final String LEISE = "#b9dcc4";

    /** Ein Block mit seinem Zug (zug null = leer). */
    public record Eintrag(String block, String ort, String zug, List<String> zeilen, String ziel, String status) {
    }

    /** Ein Modul bzw. „alle“: Schlüssel (Pfad), Titel, Blöcke. */
    public record Bereich(String schluessel, String titel, List<String> bloecke) {
    }

    private static volatile Map<String, Bereich> bereiche = Map.of();
    private static volatile Map<String, Eintrag> eintraege = Map.of();

    private ModulAnzeiger() {
    }

    public static Map<String, Bereich> bereiche() {
        return bereiche;
    }

    public static Eintrag eintrag(String block) {
        return eintraege.get(block);
    }

    /** Baut den Stand neu (FX-Thread). */
    public static void aktualisiere(Anlage a, Function<String, XtcVerbindung.ZugInfo> xtc) {
        if (a == null) {
            bereiche = Map.of();
            eintraege = Map.of();
            return;
        }
        Map<String, Bereich> b = new LinkedHashMap<>();
        Set<String> alle = new LinkedHashSet<>();
        for (Modul m : a.module()) {
            // Nur die Streckengleise des Moduls (Anwender 11.11.2026: festgelegt in „Anlage bearbeiten“ per
            // Rechtsklick auf das Modul); die Gleise der Bahnhöfe im Modul gehören nicht in den Modul-Anzeiger.
            Set<String> bl = new LinkedHashSet<>(m.getBloecke());
            alle.addAll(bl);
            b.put(Webansicht.pfad(m), new Bereich(Webansicht.pfad(m), m.getAnzeigeName(), new ArrayList<>(bl)));
        }
        for (Station s : a.stationen()) {
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null) {
                    alle.add(g.getBlockName());
                }
            }
        }
        // „Ganze Anlage“ entfällt (Anwender 11.11.2026): der Modul-Anzeiger gilt nur für Module.

        // Zug je Block: XTC geht vor, sonst der Kern
        Map<String, ZugZustand> imBlock = new LinkedHashMap<>();
        for (ZugZustand z : a.getZuege().values()) {
            XtcVerbindung.ZugInfo i = xtc.apply(z.getName());
            String block = i != null && i.isVerbunden() && i.getBlock() != null ? i.getBlock() : z.getBlock();
            if (block != null) {
                imBlock.put(block, z);
            }
        }
        Map<String, Eintrag> e = new LinkedHashMap<>();
        for (String block : alle) {
            e.put(block, eintrag(a, block, imBlock.get(block), xtc));
        }
        bereiche = b;
        eintraege = e;
    }

    private static Eintrag eintrag(Anlage a, String block, ZugZustand z, Function<String, XtcVerbindung.ZugInfo> xtc) {
        List<String> orte = new ArrayList<>();
        for (Anlage.GleisTreffer t : a.gleiseFuerBlock(block)) {
            if (!orte.contains(t.station().getAnzeigeName())) {
                orte.add(t.station().getAnzeigeName());
            }
        }
        String ort = orte.isEmpty() ? Uebersetzung.tr("Strecke") : String.join(" / ", orte);
        if (z == null) {
            return new Eintrag(block, ort, null, List.of(), null, Uebersetzung.tr("Kein Zug im Block"));
        }
        List<String> zeilen = new ArrayList<>();
        double[] p = z.passagiereSumme();
        double plaetze = z.getSitzplaetzeErste() + z.getSitzplaetzeZweite();
        if (p[0] + p[1] >= 1) {
            zeilen.add(Uebersetzung.tr("Reisende: {0} (1. Klasse {1}, 2. Klasse {2}) von {3} Plätzen",
                    Math.round(p[0] + p[1]), Math.round(p[0]), Math.round(p[1]), Math.round(plaetze)));
        }
        for (Map.Entry<String, Double> l : z.ladungJeGut().entrySet()) {
            if (l.getValue() < 1) {
                continue;
            }
            Gut g = a.gut(l.getKey());
            zeilen.add(a.gutName(l.getKey()) + ": " + Symbole.zahl(Math.floor(l.getValue() + 1e-9))
                    + (g != null ? " " + Uebersetzung.tr(g.getEinheit()) : ""));
        }
        if (zeilen.isEmpty()) {
            zeilen.add(plaetze > 0 ? Uebersetzung.tr("Leer, {0} Plätze", Math.round(plaetze))
                    : z.getWagenladungen().isEmpty() ? Uebersetzung.tr("Ohne Ladung") : Uebersetzung.tr("Leer"));
        }
        XtcVerbindung.ZugInfo i = xtc.apply(z.getName());
        String ziel = null;
        if (i != null && i.getZiel() != null && !i.getZiel().isBlank()) {
            ziel = i.getZiel();
            for (Anlage.GleisTreffer t : a.gleiseFuerBlock(ziel)) {
                ziel = ziel + " – " + t.station().getAnzeigeName();
                break;
            }
        }
        String route = i != null && i.getRoute() != null && !i.getRoute().isBlank() ? i.getRoute() : z.getLinie();
        if (ziel == null && route != null && !route.isBlank()) {
            ziel = Uebersetzung.tr("Route {0}", route);
        } else if (ziel != null && route != null && !route.isBlank()) {
            ziel = ziel + " (" + Uebersetzung.tr("Route {0}", route) + ")";
        }
        String status;
        if (i != null && i.isWartet()) {
            long rest = Math.max(0, i.getWartenGesamt() - i.getWartenVorbei()) / 1000;
            status = i.getWartenGesamt() > 0 ? Uebersetzung.tr("Hält, noch {0} s", rest) : Uebersetzung.tr("Hält");
        } else if (i != null && i.getTempo() > 0.5) {
            status = Uebersetzung.tr("Fährt, {0} km/h", Math.round(i.getTempo()));
        } else if (i != null && i.isVerbunden()) {
            status = Uebersetzung.tr("Steht");
        } else {
            status = "";
        }
        return new Eintrag(block, ort, z.getName(), zeilen, ziel, status);
    }

    // ------------------------------------------------------------------ JSON für den Browser

    /** JSON eines Bereichs (alle Blöcke) bzw. eines Blocks: {@code {"titel":…,"bloecke":[…]}}; null = unbekannt. */
    static String json(String schluessel, String nurBlock) {
        Bereich b = bereiche.get(schluessel);
        if (b == null) {
            return null;
        }
        StringBuilder j = new StringBuilder("{\"titel\":").append(q(b.titel())).append(",\"bloecke\":[");
        boolean erst = true;
        for (String block : b.bloecke()) {
            if (nurBlock != null && !nurBlock.equals(block)) {
                continue;
            }
            Eintrag e = eintraege.get(block);
            if (e == null) {
                continue;
            }
            j.append(erst ? "" : ",").append("{\"block\":").append(q(e.block())).append(",\"ort\":").append(q(e.ort()))
                    .append(",\"zug\":").append(q(e.zug())).append(",\"ziel\":").append(q(e.ziel()))
                    .append(",\"status\":").append(q(e.status())).append(",\"zeilen\":[");
            for (int k = 0; k < e.zeilen().size(); k++) {
                j.append(k == 0 ? "" : ",").append(q(e.zeilen().get(k)));
            }
            j.append("]}");
            erst = false;
        }
        return j.append("]}").toString();
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '<' -> b.append("\\u003c");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /** Die Seite im Browser: dunkelgrün, je Block ein Feld, Laufschrift für zu lange Zeilen, Abfrage jede Sekunde. */
    static String seite(String schluessel, String block, String titel) {
        String daten = "/daten/anzeiger/" + schluessel + (block != null ? "/" + block : "");
        return "<!doctype html><html lang=\"" + Uebersetzung.sprache() + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + html(Uebersetzung.tr("Modul-Anzeiger")) + " – " + html(block != null ? block : titel)
                + "</title><style>"
                + "html,body{margin:0;background:" + GRUEN + ";color:" + SCHRIFT + ";font-family:system-ui,'Segoe UI',"
                + "sans-serif}"
                + "header{padding:10px 16px;font-size:15px;color:" + LEISE + ";display:flex;justify-content:space-between}"
                + "#liste{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:12px;padding:0 12px 16px}"
                + ".feld{background:" + GRUEN_HELL + ";border-radius:10px;padding:12px 16px;border:2px solid #2f8a52;"
                + "overflow:hidden}"
                + ".einzeln{font-size:150%}"
                + ".kopf{display:flex;justify-content:space-between;color:" + LEISE + ";font-size:.95em;gap:8px}"
                + ".zug{font-size:2em;font-weight:700;margin:4px 0}"
                + ".zeile{font-size:1.15em;white-space:nowrap;overflow:hidden;line-height:1.5}"
                + ".zeile span{display:inline-block}"
                + ".lauf span{animation:lauf var(--d,10s) linear infinite;padding-left:100%}"
                + "@keyframes lauf{from{transform:translateX(0)}to{transform:translateX(-100%)}}"
                + ".ziel{font-size:1.3em;font-weight:700;margin-top:6px}"
                + ".status{color:" + LEISE + ";margin-top:4px}"
                + ".leer{opacity:.55}"
                + "a{color:" + LEISE + "}"
                + "#ton{background:transparent;color:" + SCHRIFT + ";border:1px solid " + LEISE + ";border-radius:16px;"
                + "padding:4px 12px;font-size:14px;cursor:pointer}#ton.frei{background:#2f8a52}"
                + "#tonhinweis{padding:0 16px 8px;color:#ffd27a;font-size:14px;display:none}"
                + "</style></head><body><header><span id=\"titel\">" + html(titel) + "</span>"
                + "<span><button id=\"ton\" type=\"button\"></button> <span id=\"uhr\"></span></span>"
                + "</header><div id=\"tonhinweis\">" + html(Uebersetzung.tr("Antippen, um den Ton einzuschalten"))
                + "</div><div id=\"liste\"" + (block != null ? " class=\"einzeln\"" : "") + "></div><script>"
                + Webansicht.woerter(List.of("Ziel", "keine Verbindung zum Logistik-Rechner", "Ton aus", "Klingel",
                        "Glocke", "Hupe"))
                + Signalton.js()
                // Warnton bei Einfahrt (Anwender 11.11.2026): Knopf wechselt Ton aus → Klingel → Glocke → Hupe
                + "const ARTEN=['aus','klingel','glocke','hupe'],NAMEN={aus:'🔇 '+T('Ton aus'),klingel:'🔔 '+T('Klingel'),"
                + "glocke:'🛎 '+T('Glocke'),hupe:'📯 '+T('Hupe')};let ton='aus';try{ton=localStorage.getItem('ma.ton')||'aus'}"
                + "catch(x){}const tk=document.getElementById('ton');function tonZeigen(){tk.textContent=NAMEN[ton];"
                + "const frei=AC&&AC.state==='running';tk.classList.toggle('frei',ton!=='aus'&&frei);"
                + "document.getElementById('tonhinweis').style.display=(ton!=='aus'&&!frei)?'block':'none';}"
                + "tk.onclick=()=>{tonFreigeben();ton=ARTEN[(ARTEN.indexOf(ton)+1)%ARTEN.length];"
                + "try{localStorage.setItem('ma.ton',ton)}catch(x){}signalton(ton);setTimeout(tonZeigen,200);tonZeigen();};"
                + "document.getElementById('tonhinweis').onclick=()=>{tonFreigeben();setTimeout(tonZeigen,200);};"
                + "tonZeigen();let vorher=null;"
                + "function einfahrt(d){const jetzt={};for(const b of d.bloecke)jetzt[b.block]=b.zug||'';"
                + "if(vorher){for(const k in jetzt){if(jetzt[k]&&jetzt[k]!==vorher[k]){signalton(ton);break;}}}vorher=jetzt;}"
                + "const URL_DATEN=" + q(daten) + ";let alt='';"
                + "function e(s){return String(s==null?'':s).replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))}"
                + "function zeile(t,k){return '<div class=\"zeile '+(k||'')+'\"><span>'+e(t)+'</span></div>'}"
                + "async function holen(){try{const r=await fetch(URL_DATEN,{cache:'no-store'});const d=await r.json();"
                + "document.getElementById('titel').textContent=d.titel;einfahrt(d);const j=JSON.stringify(d);if(j!==alt){alt=j;"
                + "let h='';for(const b of d.bloecke){h+='<div class=\"feld'+(b.zug?'':' leer')+'\"><div class=\"kopf\">"
                + "<span>'+e(b.block)+'</span><span>'+e(b.ort)+'</span></div>';"
                + "if(b.zug){h+='<div class=\"zug\">'+e(b.zug)+'</div>';for(const z of b.zeilen)h+=zeile(z);"
                + "if(b.ziel)h+='<div class=\"ziel zeile\"><span>'+e(T('Ziel')+': '+b.ziel)+'</span></div>';}"
                + "if(b.status)h+='<div class=\"status\">'+e(b.status)+'</div>';h+='</div>';}"
                + "document.getElementById('liste').innerHTML=h;"
                + "for(const z of document.querySelectorAll('.zeile')){const s=z.firstChild;if(s.scrollWidth>z.clientWidth){"
                + "z.classList.add('lauf');z.style.setProperty('--d',Math.max(6,(s.scrollWidth+z.clientWidth)/60)+'s');}}}"
                + "}catch(x){document.getElementById('titel').textContent=T('keine Verbindung zum Logistik-Rechner');}"
                + "document.getElementById('uhr').textContent=new Date().toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});}"
                + "holen();setInterval(holen,1000);"
                + "</script></body></html>";
    }

    private static String html(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------ Fenster

    private static final List<Stage> OFFEN = new ArrayList<>();

    /**
     * Öffnet ein Fenster des Modul-Anzeigers für ein Modul (Anwender 11.11.2026: keine Auswahl von Modul und Block im
     * Fenster mehr; gezeigt werden alle Streckengleise des Moduls, der Block steht in jedem Feld). Oben rechts nur der
     * Ton bei Einfahrt.
     */
    public static void zeigeFenster(String schluessel) {
        Stage st = new Stage();
        Bereich start = bereiche.get(schluessel);
        st.setTitle(Uebersetzung.tr("Modul-Anzeiger") + " – " + (start == null ? schluessel : start.titel()));
        // Warnton bei Einfahrt (Anwender 11.11.2026: Ausstellung, Handy/Rechner am Modul), je Modul gemerkt; ein Klick
        // auf das Modul in der Übersicht spielt ihn ebenfalls.
        ComboBox<String> ton = new ComboBox<>();
        ton.getItems().setAll(Signalton.ARTEN);
        ton.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String a) {
                return a == null ? "" : Uebersetzung.tr(Signalton.name(a));
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        ton.setValue(AppSettings.getInstance().getModulTon(schluessel));
        ton.valueProperty().addListener((o, x, y) -> {
            AppSettings.getInstance().setModulTon(schluessel, y);
            Signalton.spielen(y);
        });
        ton.setTooltip(new javafx.scene.control.Tooltip(Uebersetzung.tr("Ton bei Einfahrt eines Zugs in den Block")));
        Label titel = new Label(start == null ? "" : start.titel());
        titel.setStyle("-fx-text-fill: " + LEISE + "; -fx-font-size: 14px;");
        Region luecke = new Region();
        HBox.setHgrow(luecke, Priority.ALWAYS);
        HBox kopf = new HBox(8, titel, luecke, ton);
        kopf.setAlignment(Pos.CENTER_LEFT);
        kopf.setPadding(new Insets(8, 12, 8, 12));
        VBox liste = new VBox(10);
        liste.setPadding(new Insets(0, 12, 12, 12));
        liste.setStyle("-fx-background-color: " + GRUEN + ";");
        ScrollPane sp = new ScrollPane(liste);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: " + GRUEN + "; -fx-background-color: " + GRUEN + ";");
        VBox root = new VBox(kopf, sp);
        VBox.setVgrow(sp, Priority.ALWAYS);
        root.setStyle("-fx-background-color: " + GRUEN + ";");
        Scene sc = new Scene(root, 520, 420);
        st.setScene(sc);
        WindowState.apply(st, "modulanzeiger." + schluessel, 520, 420);

        List<Label> lauf = new ArrayList<>();
        String[] stand = {""};
        @SuppressWarnings("unchecked")
        java.util.Map<String, String>[] zuege = new java.util.Map[1];
        Runnable neu = () -> {
            Bereich b = bereiche.get(schluessel);
            List<Eintrag> l = new ArrayList<>();
            if (b != null) {
                titel.setText(b.titel());
                st.setTitle(Uebersetzung.tr("Modul-Anzeiger") + " – " + b.titel());
                for (String bl : b.bloecke()) {
                    Eintrag e = eintraege.get(bl);
                    if (e != null) {
                        l.add(e);
                    }
                }
            }
            // Einfahrt: ein Block bekommt einen (anderen) Zug → Warnton
            java.util.Map<String, String> jetzt = new java.util.HashMap<>();
            for (Eintrag e : l) {
                jetzt.put(e.block(), e.zug() == null ? "" : e.zug());
            }
            if (zuege[0] != null && jetzt.keySet().equals(zuege[0].keySet())) {
                for (var en : jetzt.entrySet()) {
                    if (!en.getValue().isEmpty() && !en.getValue().equals(zuege[0].get(en.getKey()))) {
                        Signalton.spielen(ton.getValue());
                        break;
                    }
                }
            }
            zuege[0] = jetzt;
            String s = l + "|" + sc.getWidth();
            if (s.equals(stand[0])) {
                return;
            }
            stand[0] = s;
            lauf.clear();
            liste.getChildren().clear();
            double f = l.size() == 1 ? 1.5 : 1.0;
            for (Eintrag e : l) {
                liste.getChildren().add(feld(e, f, lauf, sc.getWidth() - 50));
            }
            if (l.isEmpty()) {
                Label leer = new Label(b == null ? Uebersetzung.tr("Modul nicht gefunden.")
                        : Uebersetzung.tr("Keine Streckengleise – in „Anlage bearbeiten“ per Rechtsklick auf das Modul festlegen."));
                leer.setWrapText(true);
                leer.setStyle("-fx-text-fill: " + LEISE + "; -fx-font-size: 16px;");
                liste.getChildren().add(leer);
            }
        };
        Timeline takt = new Timeline(new KeyFrame(Duration.seconds(1), e -> neu.run()));
        takt.setCycleCount(Timeline.INDEFINITE);
        // Laufschrift: zu breite Zeilen wandern nach links
        AnimationTimer laufen = new AnimationTimer() {
            private long letzt;

            @Override
            public void handle(long now) {
                double dt = letzt == 0 ? 0 : (now - letzt) / 1e9;
                letzt = now;
                for (Label l : lauf) {
                    Pane rahmen = (Pane) l.getParent();
                    double b = l.prefWidth(-1);
                    double r = rahmen.getWidth();
                    if (b <= r) {
                        l.setTranslateX(0);
                        continue;
                    }
                    double x = l.getTranslateX() - 60 * dt;
                    if (x < -b) {
                        x = r;
                    }
                    l.setTranslateX(x);
                }
            }
        };
        st.setOnShown(e -> {
            neu.run();
            takt.play();
            laufen.start();
        });
        st.setOnHidden(e -> {
            takt.stop();
            laufen.stop();
            OFFEN.remove(st);
        });
        OFFEN.add(st);
        st.show();
    }

    /** Feld eines Blocks: Kopf (Block, Ort), Zug groß, Zeilen, Ziel, Zustand. */
    private static Node feld(Eintrag e, double f, List<Label> lauf, double breite) {
        Label block = text(e.block(), 13 * f, false, LEISE);
        Label ort = text(e.ort(), 13 * f, false, LEISE);
        Region platz = new Region();
        HBox.setHgrow(platz, Priority.ALWAYS);
        VBox v = new VBox(2, new HBox(8, block, platz, ort));
        if (e.zug() != null) {
            v.getChildren().add(text(e.zug(), 26 * f, true, SCHRIFT));
            for (String z : e.zeilen()) {
                v.getChildren().add(laufzeile(z, 16 * f, false, lauf));
            }
            if (e.ziel() != null) {
                v.getChildren().add(laufzeile(Uebersetzung.tr("Ziel") + ": " + e.ziel(), 18 * f, true, lauf));
            }
        }
        if (e.status() != null && !e.status().isEmpty()) {
            v.getChildren().add(text(e.status(), 13 * f, false, LEISE));
        }
        v.setPadding(new Insets(10, 14, 10, 14));
        v.setStyle("-fx-background-color: " + GRUEN_HELL + "; -fx-background-radius: 10; -fx-border-color: #2f8a52; "
                + "-fx-border-width: 2; -fx-border-radius: 10;" + (e.zug() == null ? " -fx-opacity: 0.6;" : ""));
        v.setMaxWidth(Double.MAX_VALUE);
        return v;
    }

    private static Label text(String t, double groesse, boolean fett, String farbe) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: " + farbe + "; -fx-font-size: " + groesse + "px;" + (fett ? " -fx-font-weight: bold;"
                : ""));
        return l;
    }

    /** Zeile in einem abgeschnittenen Rahmen; ist sie zu breit, läuft sie (siehe AnimationTimer). */
    private static Node laufzeile(String t, double groesse, boolean fett, List<Label> lauf) {
        Label l = text(t, groesse, fett, SCHRIFT);
        l.setMinWidth(Region.USE_PREF_SIZE);
        Pane rahmen = new Pane(l);
        rahmen.setMinWidth(0);
        rahmen.setPrefHeight(groesse * 1.45);
        rahmen.setMinHeight(groesse * 1.45);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(rahmen.widthProperty());
        clip.heightProperty().bind(rahmen.heightProperty());
        rahmen.setClip(clip);
        lauf.add(l);
        return rahmen;
    }
}
