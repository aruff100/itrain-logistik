package com.example.itrain_logistik;

import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableView;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzung der Oberfläche (Anwender 09.11.2026: Deutsch, Englisch, Niederländisch, Französisch, Spanisch,
 * Italienisch).
 * <p>
 * Der Code bleibt deutsch. Ist eine andere Sprache gewählt, übersetzt diese Klasse jeden angezeigten Text, sobald er
 * gesetzt wird: Beschriftungen, Knöpfe, Menüs, Reiter, Spaltenköpfe, Tooltips, Fenstertitel, Platzhalter in
 * Eingabefeldern und Text-Knoten. Eingabefelder selbst (Namen, Zahlen) bleiben unberührt.
 * <p>
 * Wörterbuch je Sprache: Ressource {@code sprachen/<code>.txt}, Paare aus Zeilen {@code de: …} und
 * {@code <code>: …} ({@code \n} = Zeilenumbruch, {@code #} = Kommentar). Eine Datei gleichen Namens im Ordner
 * {@code <Logistik-Ordner>/sprachen/} geht vor (eigene Korrekturen). Deutsche Texte mit Platzhaltern {@code {0}},
 * {@code {1}} … sind Vorlagen: der Text wird als Ganzes erkannt, die eingesetzten Werte werden ihrerseits nachgeschlagen
 * (z. B. „Schüttgut“). Mehrzeilige Texte werden auch Zeile für Zeile übersetzt. Was nicht gefunden wird, bleibt
 * deutsch und landet in {@code <Logistik-Ordner>/sprachen/fehlt-<code>.txt}.
 */
public final class Uebersetzung {

    public static final List<String> SPRACHEN = List.of("de", "en", "nl", "fr", "es", "it");
    /** Style-Klasse: dieser Knoten und alles darunter wird nicht übersetzt (z. B. Dialogtexte mit eigener Datei). */
    public static final String NICHT_UEBERSETZEN = "nicht-uebersetzen";

    private static String sprache = "de";
    private static final Map<String, String> GENAU = new HashMap<>();
    private static final List<Vorlage> VORLAGEN = new ArrayList<>();
    /** Vorlagen je Wort (Vorfilter, damit nicht jede Vorlage gegen jeden Text läuft). */
    private static final Map<String, List<Vorlage>> JE_WORT = new HashMap<>();
    private static final Map<String, String> ZWISCHENSPEICHER = Collections.synchronizedMap(
            new LinkedHashMap<>(4096, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> e) {
                    return size() > 20000;
                }
            });
    private static final Set<String> FEHLT = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Map<Object, Boolean> BEOBACHTET = new WeakHashMap<>();
    private static boolean installiert;

    private static final Pattern WORT = Pattern.compile("[\\p{L}]{4,}");
    private static final Pattern NUR_ZEICHEN = Pattern.compile("[\\s\\d.,:;%+\\-–/()×·▲▼→←✔✘⚠|]*");

    private record Vorlage(Pattern muster, String ziel, int teile, int laenge) {
    }

    private Uebersetzung() {
    }

    public static String sprache() {
        return sprache;
    }

    public static boolean aktiv() {
        return !"de".equals(sprache);
    }

    /** Lädt das Wörterbuch der Sprache (vor dem Aufbau der Oberfläche aufrufen). */
    public static synchronized void laden(String code) {
        sprache = SPRACHEN.contains(code) ? code : "de";
        GENAU.clear();
        VORLAGEN.clear();
        JE_WORT.clear();
        ZWISCHENSPEICHER.clear();
        if (!aktiv()) {
            return;
        }
        try (InputStream in = Uebersetzung.class.getResourceAsStream("sprachen/" + sprache + ".txt")) {
            if (in != null) {
                lies(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ex) {
            System.err.println("Sprachdatei " + sprache + ": " + ex.getMessage());
        }
        Path eigen = eigenerOrdner().resolve(sprache + ".txt");
        if (Files.isRegularFile(eigen)) {
            try (BufferedReader r = Files.newBufferedReader(eigen, StandardCharsets.UTF_8)) {
                lies(r);
            } catch (IOException ex) {
                System.err.println("Eigene Sprachdatei " + eigen + ": " + ex.getMessage());
            }
        }
        VORLAGEN.sort((a, b) -> Integer.compare(b.laenge(), a.laenge()));
        for (Vorlage v : VORLAGEN) {
            String w = laengstesWort(v.muster().pattern());
            JE_WORT.computeIfAbsent(w, k -> new ArrayList<>()).add(v);
        }
    }

    static Path eigenerOrdner() {
        return Path.of(AppSettings.getInstance().getLogistikDirectory(), "sprachen");
    }

    private static void lies(BufferedReader r) throws IOException {
        String de = null;
        String praefix = sprache + ":";
        for (String zeile; (zeile = r.readLine()) != null; ) {
            if (zeile.startsWith("de:")) {
                de = ent(zeile.substring(3).strip());
            } else if (zeile.startsWith(praefix) && de != null) {
                String ziel = ent(zeile.substring(praefix.length()).strip());
                if (!ziel.isEmpty()) {
                    eintragen(de, ziel);
                }
                de = null;
            }
        }
    }

    private static String ent(String s) {
        return s.replace("\\n", "\n").replace("\\t", "\t");
    }

    private static void eintragen(String de, String ziel) {
        if (!de.contains("{0}")) {
            GENAU.put(de, ziel);
            return;
        }
        // Vorlage: Platzhalter als Gruppen, der Rest wörtlich
        StringBuilder rx = new StringBuilder();
        Matcher m = Pattern.compile("\\{(\\d)}").matcher(de);
        int pos = 0;
        int teile = 0;
        int[] reihenfolge = new int[10];
        while (m.find()) {
            rx.append(Pattern.quote(de.substring(pos, m.start())));
            rx.append("(.*?)");
            reihenfolge[teile++] = Integer.parseInt(m.group(1));
            pos = m.end();
        }
        rx.append(Pattern.quote(de.substring(pos)));
        // Ziel: {n} der Vorlage auf die Gruppennummer abbilden
        String z = ziel;
        for (int g = 0; g < teile; g++) {
            z = z.replace("{" + reihenfolge[g] + "}", "\u0001" + (g + 1) + "\u0002");
        }
        int literal = de.replaceAll("\\{\\d}", "").length();
        VORLAGEN.add(new Vorlage(Pattern.compile(rx.toString(), Pattern.DOTALL), z, teile, literal));
    }

    private static String laengstesWort(String muster) {
        String w = "";
        Matcher m = WORT.matcher(muster.replaceAll("\\\\[QE]", " "));
        while (m.find()) {
            if (m.group().length() > w.length()) {
                w = m.group();
            }
        }
        return w;
    }

    /** Übersetzt einen angezeigten Text; unbekannte Texte bleiben, wie sie sind. */
    public static String tr(String text) {
        if (!aktiv() || text == null || text.isEmpty()) {
            return text;
        }
        String c = ZWISCHENSPEICHER.get(text);
        if (c != null) {
            return c;
        }
        String r = uebersetze(text, true);
        ZWISCHENSPEICHER.put(text, r);
        if (r != null && !r.equals(text)) {
            ZWISCHENSPEICHER.put(r, r); // schon übersetzt: nicht noch einmal
        }
        return r;
    }

    /** Wie {@link #tr}, mit eingesetzten Werten an {0}, {1} … */
    public static String tr(String vorlage, Object... werte) {
        String t = tr(vorlage);
        for (int i = 0; i < werte.length; i++) {
            t = t.replace("{" + i + "}", String.valueOf(werte[i]));
        }
        return t;
    }

    /** Trennstellen für die Übersetzung in Stücken („Fischzucht (Betrieb)“, „Plätze | Gebinde (Stückgut)“). */
    private static final Pattern TRENNER = Pattern.compile(" +[·|–→←↔+/] +|\\(|\\)|, |: |\\s{2,}");

    /** Übersetzt die Stücke zwischen Trennern einzeln; null, wenn kein Stück bekannt ist. */
    private static String stueckweise(String kern) {
        Matcher m = TRENNER.matcher(kern);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        boolean etwas = false;
        while (true) {
            boolean f = m.find();
            int ende = f ? m.start() : kern.length();
            String teil = kern.substring(pos, ende);
            String s = teil.strip();
            String u = s.isEmpty() ? null : GENAU.get(s);
            if (u == null && !s.isEmpty()) {
                u = vorlage(s);
            }
            if (u != null) {
                etwas = true;
                sb.append(teil.replace(s, u));
            } else {
                sb.append(teil);
            }
            if (!f) {
                break;
            }
            sb.append(m.group());
            pos = m.end();
        }
        return etwas ? sb.toString() : null;
    }

    private static String uebersetze(String text, boolean zeilen) {
        // führende/folgende Leerzeichen erhalten
        int a = 0;
        int e = text.length();
        while (a < e && Character.isWhitespace(text.charAt(a))) {
            a++;
        }
        while (e > a && Character.isWhitespace(text.charAt(e - 1))) {
            e--;
        }
        String kern = text.substring(a, e);
        if (kern.isEmpty() || NUR_ZEICHEN.matcher(kern).matches()) {
            return text;
        }
        String r = GENAU.get(kern);
        if (r == null) {
            r = vorlage(kern);
        }
        if (r == null && zeilen && kern.indexOf('\n') >= 0) {
            String[] z = kern.split("\n", -1);
            StringBuilder sb = new StringBuilder();
            boolean etwas = false;
            for (int i = 0; i < z.length; i++) {
                String u = uebersetze(z[i], false);
                etwas |= !u.equals(z[i]);
                sb.append(i > 0 ? "\n" : "").append(u);
            }
            if (etwas) {
                r = sb.toString();
            }
        }
        if (r == null) {
            r = stueckweise(kern);
        }
        if (r == null) {
            if (WORT.matcher(kern).find()) {
                FEHLT.add(kern);
            }
            return text;
        }
        return text.substring(0, a) + r + text.substring(e);
    }

    private static String vorlage(String kern) {
        Matcher w = WORT.matcher(kern);
        Set<String> gesehen = new LinkedHashSet<>();
        while (w.find()) {
            if (!gesehen.add(w.group())) {
                continue;
            }
            List<Vorlage> l = JE_WORT.get(w.group());
            if (l == null) {
                continue;
            }
            for (Vorlage v : l) {
                Matcher m = v.muster().matcher(kern);
                if (m.matches() && !verschluckt(v, m)) {
                    String z = v.ziel();
                    for (int g = 1; g <= v.teile(); g++) {
                        String wert = m.group(g);
                        String u = GENAU.get(wert.strip());
                        z = z.replace("\u0001" + g + "\u0002", u != null ? wert.replace(wert.strip(), u) : wert);
                    }
                    return z;
                }
            }
        }
        // Vorlagen ohne Wort mit 4 Buchstaben
        for (Vorlage v : JE_WORT.getOrDefault("", List.of())) {
            Matcher m = v.muster().matcher(kern);
            if (m.matches()) {
                String z = v.ziel();
                for (int g = 1; g <= v.teile(); g++) {
                    z = z.replace("\u0001" + g + "\u0002", m.group(g));
                }
                return z;
            }
        }
        return null;
    }

    /**
     * Eine kurze Vorlage („Gleis {0}“) darf keinen zusammengesetzten Text verschlucken: enthält ein eingesetzter Wert
     * einen Trenner (· oder doppelte Leerzeichen), den die Vorlage selbst nicht hat, gilt sie nicht.
     */
    private static boolean verschluckt(Vorlage v, Matcher m) {
        String muster = v.muster().pattern();
        for (int g = 1; g <= v.teile(); g++) {
            String w = m.group(g);
            if ((w.contains("·") && !muster.contains("·")) || (w.contains("  ") && !muster.contains("  "))) {
                return true;
            }
        }
        return false;
    }

    /** Fehlende Übersetzungen dieser Sitzung in {@code fehlt-<code>.txt} schreiben (beim Beenden). */
    public static void fehlendeSpeichern() {
        if (!aktiv() || FEHLT.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(eigenerOrdner());
            Path f = eigenerOrdner().resolve("fehlt-" + sprache + ".txt");
            Set<String> alle = new LinkedHashSet<>();
            if (Files.isRegularFile(f)) {
                for (String z : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (z.startsWith("de: ")) {
                        alle.add(z.substring(4));
                    }
                }
            }
            synchronized (FEHLT) {
                for (String s : FEHLT) {
                    alle.add(s.replace("\n", "\\n"));
                }
            }
            StringBuilder sb = new StringBuilder("# Texte ohne Übersetzung (" + sprache
                    + "). Übersetzung in der Zeile darunter eintragen und die Paare nach " + sprache + ".txt im selben "
                    + "Ordner kopieren.\n");
            for (String s : alle) {
                sb.append("de: ").append(s).append('\n').append(sprache).append(": \n\n");
            }
            Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ex) {
            System.err.println("fehlt-" + sprache + ".txt: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ Oberfläche

    /** Beobachtet alle Fenster und übersetzt deren Texte (nur, wenn nicht Deutsch gewählt ist). */
    public static void installieren() {
        if (!aktiv() || installiert) {
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

    private static boolean neu(Object o) {
        synchronized (BEOBACHTET) {
            return BEOBACHTET.put(o, Boolean.TRUE) == null;
        }
    }

    private static void fenster(Window w) {
        if (!neu(w)) {
            return;
        }
        if (w instanceof Stage s) {
            eigenschaft(s.titleProperty());
        }
        if (w instanceof ContextMenu cm) {
            menuepunkte(cm.getItems());
        }
        if (w instanceof Tooltip t) {
            eigenschaft(t.textProperty());
        }
        szene(w.getScene());
        w.sceneProperty().addListener((o, alt, neu) -> szene(neu));
    }

    private static void szene(Scene s) {
        if (s == null || !neu(s)) {
            return;
        }
        knoten(s.getRoot());
        s.rootProperty().addListener((o, alt, neu) -> knoten(neu));
    }

    private static void eigenschaft(StringProperty p) {
        if (p == null || !neu(p)) {
            return;
        }
        setze(p);
        p.addListener((o, alt, neu) -> setze(p));
    }

    private static void setze(StringProperty p) {
        if (p.isBound()) {
            return;
        }
        String t = p.get();
        String u = tr(t);
        if (u != null && !u.equals(t)) {
            p.set(u);
        }
    }

    private static void menuepunkte(List<MenuItem> items) {
        for (MenuItem mi : items) {
            if (!neu(mi)) {
                continue;
            }
            eigenschaft(mi.textProperty());
            if (mi instanceof Menu m) {
                menuepunkte(m.getItems());
                m.getItems().addListener((ListChangeListener<MenuItem>) c -> menuepunkte(m.getItems()));
            }
        }
    }

    private static void spalten(List<? extends TableColumnBase<?, ?>> l) {
        for (TableColumnBase<?, ?> c : l) {
            if (neu(c)) {
                eigenschaft(c.textProperty());
            }
            spalten(c.getColumns());
        }
    }

    /** Text-Knoten im Inneren eines Steuerelements (Beschriftung mit „…“, Eingabefeld): nicht anfassen. */
    private static boolean innenText(Text t) {
        if (t.getClass().getName().endsWith("LabeledText")) {
            return true;
        }
        for (Node p = t.getParent(); p != null; p = p.getParent()) {
            if (p instanceof TextInputControl || p instanceof Labeled) {
                return true;
            }
            if (p instanceof javafx.scene.text.TextFlow) {
                return false;
            }
        }
        return false;
    }

    private static void knoten(Node n) {
        if (n == null || n.getStyleClass().contains(NICHT_UEBERSETZEN) || !neu(n)) {
            return;
        }
        if (n instanceof Labeled l) {
            eigenschaft(l.textProperty());
            if (l.getTooltip() != null) {
                eigenschaft(l.getTooltip().textProperty());
            }
            l.tooltipProperty().addListener((o, a, t) -> {
                if (t != null) {
                    eigenschaft(t.textProperty());
                }
            });
            if (l.getContextMenu() != null) {
                menuepunkte(l.getContextMenu().getItems());
            }
        } else if (n instanceof Text t && !innenText(t)) {
            eigenschaft(t.textProperty());
        } else if (n instanceof TextInputControl ti) {
            eigenschaft(ti.promptTextProperty());
        }
        if (n instanceof MenuBar mb) {
            for (Menu m : mb.getMenus()) {
                if (neu(m)) {
                    eigenschaft(m.textProperty());
                    menuepunkte(m.getItems());
                    m.getItems().addListener((ListChangeListener<MenuItem>) c -> menuepunkte(m.getItems()));
                }
            }
        }
        if (n instanceof MenuButton b) {
            menuepunkte(b.getItems());
            b.getItems().addListener((ListChangeListener<MenuItem>) c -> menuepunkte(b.getItems()));
        }
        if (n instanceof TabPane tp) {
            for (Tab tab : tp.getTabs()) {
                eigenschaft(tab.textProperty());
            }
            tp.getTabs().addListener((ListChangeListener<Tab>) c -> tp.getTabs().forEach(x -> eigenschaft(x.textProperty())));
        }
        if (n instanceof TableView<?> tv) {
            spalten(tv.getColumns());
            tv.getColumns().addListener((ListChangeListener<Object>) c -> spalten(tv.getColumns()));
        }
        if (n instanceof TreeTableView<?> tt) {
            spalten(tt.getColumns());
            tt.getColumns().addListener((ListChangeListener<Object>) c -> spalten(tt.getColumns()));
        }
        if (n instanceof javafx.scene.control.Control ctl && ctl.getTooltip() != null) {
            eigenschaft(ctl.getTooltip().textProperty());
        }
        if (n instanceof Parent p) {
            for (Node k : p.getChildrenUnmodifiable()) {
                knoten(k);
            }
            p.getChildrenUnmodifiable().addListener((ListChangeListener<Node>) c -> {
                while (c.next()) {
                    for (Node k : c.getAddedSubList()) {
                        knoten(k);
                    }
                }
            });
        }
    }
}
