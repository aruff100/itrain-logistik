package com.example.itrain_logistik;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Routen-Anzeiger im Browser (Anwender 10.11.2026): eigener kleiner Webserver wie {@link ZuganzeigerWeb}, eigener
 * Port. {@code /} listet die Züge, {@code /zug/<pfad>} zeigt den Anzeiger eines Zugs (gezeichnet im Browser von
 * {@code routen.js}, dieselbe Darstellung wie {@link RoutenAnzeigerWindow}), {@code /daten/<pfad>} liefert den
 * Zustand als JSON, {@code /klang/einsteigen|beladen} die Geräusche. Nur GET, nur lesend.
 *
 * <p>Die laufenden Werte (Block, Position, Tempo, Signal) liest der Server bei jeder Anfrage direkt aus
 * {@link XtcVerbindung.ZugInfo}; Wege, Zugbild und die Vorgänge am Zug bereitet das Hauptfenster auf dem
 * JavaFX-Thread vor ({@link #aktualisiere}).
 */
public final class RoutenWeb {

    public static final int PORT_VORGABE = 8092;

    private static HttpServer server;
    private static int laufenderPort;

    /** Ein Zug: Name, laufender Zustand und das vorbereitete JSON-Stück „s“/„bild“/„u“. */
    private record Zug(String name, XtcVerbindung.ZugInfo info, String fest) {
    }

    private static volatile Map<String, Zug> zuege = Map.of();

    private static final Map<String, Long> GERAETE = new java.util.concurrent.ConcurrentHashMap<>();

    private RoutenWeb() {
    }

    /** Adressen der Geräte, deren Browser einen Anzeiger gerade offen hat. */
    public static List<String> geraete() {
        long grenze = System.currentTimeMillis() - 10_000;
        GERAETE.values().removeIf(t -> t < grenze);
        return new java.util.ArrayList<>(new java.util.TreeSet<>(GERAETE.keySet()));
    }

    public static String startAdresse() {
        return "http://" + Webansicht.eigeneAdresse() + ":" + AppSettings.getInstance().getRoutenWebPort() + "/";
    }

    public static synchronized boolean laeuft() {
        return server != null;
    }

    public static synchronized void starte(int port) throws IOException {
        if (server != null && laufenderPort == port) {
            return;
        }
        stoppe();
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", RoutenWeb::anfrage);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.start();
        server = s;
        laufenderPort = port;
    }

    public static synchronized void stoppe() {
        if (server != null) {
            server.stop(0);
            server = null;
            laufenderPort = 0;
        }
    }

    /** Pfad eines Zugs in der Adresse (wie bei den Bahnhöfen des Zuganzeigers). */
    static String pfad(String zug) {
        return ZuganzeigerWeb.pfad(zug, "zug");
    }

    /** Pfad → Zugname, in der Reihenfolge der Datei (für Menüs und den Einstelldialog). */
    public static Map<String, String> zugPfade() {
        Map<String, String> m = new LinkedHashMap<>();
        zuege.forEach((p, z) -> m.put(p, z.name()));
        return m;
    }

    // ------------------------------------------------------------------ Daten (JavaFX-Thread)

    /** Bereitet je Zug Wege, Bild und Vorgänge vor; {@code quelle} null = keine Datei offen. */
    public static void aktualisiere(RoutenAnzeigerWindow.Quelle quelle) {
        Map<String, Zug> neu = new LinkedHashMap<>();
        if (quelle != null) {
            for (String name : quelle.zuege()) {
                String p = pfad(name);
                while (neu.containsKey(p)) {
                    p = p + "-2";
                }
                XtcVerbindung.ZugInfo i = quelle.info(name);
                StringBuilder j = new StringBuilder();
                RoutenAnzeigerWindow.Bild b = quelle.bild(name);
                j.append("\"bild\":[");
                for (int k = 0; k < b.fahrzeuge().size(); k++) {
                    RoutenAnzeigerWindow.Fahrzeug f = b.fahrzeuge().get(k);
                    j.append(k > 0 ? "," : "").append("{\"a\":\"").append(f.art()).append("\",\"l\":\"")
                            .append(f.lok() == null ? "" : f.lok()).append("\"}");
                }
                j.append(']');
                if (i != null && i.getBlock() != null) {
                    BlockEntry.Weg weg = quelle.weg(name);
                    BlockEntry.Weg zur = quelle.wegZurueck(name);
                    j.append(",\"s\":{\"block\":").append(q(i.getBlock())).append(",\"richtung\":").append(q(i.getRichtung()))
                            .append(",\"weg\":").append(weg(weg, quelle)).append(",\"zurueck\":").append(weg(zur, quelle))
                            .append(",\"endeVor\":").append(weg == null && quelle.gleisende(name, true))
                            .append(",\"endeHin\":").append(zur == null && quelle.gleisende(name, false))
                            .append(",\"lVor\":").append(zahl(quelle.blockLaenge(i.getVorherBlock()))).append('}');
                }
                RoutenAnzeigerWindow.Umschlag u = quelle.umschlag(name);
                if (u != null) {
                    j.append(",\"u\":{\"p\":").append(u.personenAktiv()).append(",\"g\":").append(u.gueterAktiv()).append(",\"z\":[");
                    for (int k = 0; k < u.zeilen().size(); k++) {
                        j.append(k > 0 ? "," : "").append(q(u.zeilen().get(k)));
                    }
                    j.append("]}");
                }
                neu.put(p, new Zug(name, i, j.toString()));
            }
        }
        zuege = neu;
    }

    private static String weg(BlockEntry.Weg w, RoutenAnzeigerWindow.Quelle quelle) {
        if (w == null) {
            return "null";
        }
        StringBuilder n = new StringBuilder();
        for (BlockEntry.Weiche x : w.weichen()) {
            n.append(n.length() == 0 ? "" : ", ").append(x.name());
        }
        return "{\"ziel\":" + q(w.ziel()) + ",\"namen\":" + q(n.toString()) + ",\"laenge\":"
                + zahl(w.weichen().isEmpty() ? 0 : w.laenge(11.0)) + ",\"zielLaenge\":" + zahl(quelle.blockLaenge(w.ziel()))
                + "}";
    }

    private static String zahl(double d) {
        return Double.isFinite(d) ? String.valueOf(Math.round(d * 100) / 100.0) : "-1";
    }

    static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("");
                case '<' -> b.append("\\u003c");
                default -> b.append(c < 0x20 ? " " : String.valueOf(c));
            }
        }
        return b.append('"').toString();
    }

    /** Zustand eines Zugs: die laufenden Werte von jetzt, dazu das vorbereitete Stück. */
    private static String json(Zug z) {
        XtcVerbindung.ZugInfo i = z.info();
        StringBuilder j = new StringBuilder("{\"zug\":").append(q(z.name()));
        if (i == null || !i.isVerbunden()) {
            j.append(",\"verbunden\":false");
        } else {
            j.append(",\"verbunden\":true,\"block\":").append(q(i.getBlock()))
                    .append(",\"laenge\":").append(zahl(i.getBlockLaenge()))
                    .append(",\"pos\":").append(zahl(i.getPosition()))
                    .append(",\"posZeit\":").append(i.getPositionZeit())
                    .append(",\"tempo\":").append(zahl(i.getTempo()))
                    .append(",\"wunsch\":").append(zahl(i.getWunschTempo()))
                    .append(",\"richtung\":").append(q(i.getRichtung()))
                    .append(",\"stop\":").append(zahl(i.getStop()))
                    .append(",\"bremse\":").append(zahl(i.getBremse()))
                    .append(",\"signal\":").append(q(i.getSignal()))
                    .append(",\"erwartet\":").append(q(i.getSignalErwartet()))
                    .append(",\"wartet\":").append(i.isWartet())
                    .append(",\"wGesamt\":").append(i.getWartenGesamt())
                    .append(",\"wVorbei\":").append(i.getWartenVorbei())
                    .append(",\"route\":").append(q(i.getRoute()))
                    .append(",\"ziel\":").append(q(i.getZiel()))
                    .append(",\"zugLaenge\":").append(zahl(i.getZugLaenge()))
                    .append(",\"vorher\":").append(q(i.getVorherBlock()))
                    .append(",\"fahrt\":").append(q(i.getFahrtrichtung()));
        }
        return j.append(',').append(z.fest()).append('}').toString();
    }

    // ------------------------------------------------------------------ Anfragen

    private static void anfrage(HttpExchange ex) throws IOException {
        try (ex) {
            String pfad = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (!"GET".equals(ex.getRequestMethod())) {
                senden(ex, 405, "text/plain", Uebersetzung.tr("Nur lesen"));
                return;
            }
            if (pfad.startsWith("/daten/")) {
                if (ex.getRemoteAddress() != null && ex.getRemoteAddress().getAddress() != null) {
                    GERAETE.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
                }
                Zug z = zuege.get(pfad.substring(7));
                senden(ex, z == null ? 404 : 200, "application/json", z == null ? "{}" : json(z));
            } else if (pfad.startsWith("/zug/")) {
                Zug z = zuege.get(pfad.substring(5));
                if (z == null) {
                    senden(ex, 404, "text/plain", Uebersetzung.tr("Kein Zug unter dieser Adresse"));
                } else {
                    senden(ex, 200, "text/html", seite(pfad.substring(5), z.name()));
                }
            } else if (pfad.equals("/routen.js")) {
                senden(ex, 200, "text/javascript", ressource("routen.js"));
            } else if (pfad.startsWith("/klang/")) {
                Path p = Geraeusche.vorgangDatei("/klang/einsteigen".equals(pfad) ? Geraeusche.EINSTEIGEN
                        : Geraeusche.BELADEN);
                if (p == null) {
                    senden(ex, 404, "text/plain", Uebersetzung.tr("Kein Geräusch"));
                } else {
                    byte[] b = Files.readAllBytes(p);
                    ex.getResponseHeaders().set("Content-Type", Geraeusche.mime(p));
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream o = ex.getResponseBody()) {
                        o.write(b);
                    }
                }
            } else if (pfad.equals("/")) {
                senden(ex, 200, "text/html", start());
            } else {
                senden(ex, 404, "text/plain", Uebersetzung.tr("Nicht gefunden"));
            }
        }
    }

    private static void senden(HttpExchange ex, int code, String typ, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", typ + "; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    private static String ressource(String name) throws IOException {
        try (InputStream in = RoutenWeb.class.getResourceAsStream(name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String html(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String t(String s) {
        return html(Uebersetzung.tr(s));
    }

    private static final String KOPF = "<!doctype html><html lang=\"%LANG%\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>%TITEL%</title><style>"
            + "html,body{margin:0;height:100%;font-family:system-ui,'Segoe UI',sans-serif;background:#eef3f8;color:#1f2328}"
            + "a{color:#1d4f91}";

    /** Startseite: alle Züge der Datei. */
    private static String start() {
        StringBuilder b = new StringBuilder(KOPF.replace("%LANG%", Uebersetzung.sprache()).replace("%TITEL%", t("Routen-Anzeiger")));
        b.append(".start{max-width:520px;margin:0 auto;padding:22px 14px}h1{font-size:24px;margin:6px 0 2px}"
                + "p{color:#5b6470;margin:0 0 16px}.zug{display:block;background:#fff;border:1px solid #d5dbe3;"
                + "border-radius:10px;padding:12px 14px;margin:8px 0;text-decoration:none;font-weight:600}"
                + ".zug span{float:right;font-weight:400;color:#5b6470}</style></head><body><div class=\"start\"><h1>")
                .append(t("Routen-Anzeiger")).append("</h1><p>iTrain-Logistik · ").append(t("Zug wählen")).append("</p>");
        Map<String, Zug> z = zuege;
        if (z.isEmpty()) {
            b.append("<p>").append(t("Keine Datei geöffnet oder kein Zug vorhanden.")).append("</p>");
        }
        z.forEach((p, zug) -> {
            XtcVerbindung.ZugInfo i = zug.info();
            b.append("<a class=\"zug\" href=\"/zug/").append(html(p)).append("\">").append(html(zug.name()))
                    .append("<span>").append(html(i != null && i.getBlock() != null ? i.getBlock() : "")).append("</span></a>");
        });
        return b.append("</div></body></html>").toString();
    }

    /** Texte, die das Skript zeigt (übersetzt über T('…')). */
    private static final List<String> JS_WOERTER = List.of("Ton an", "Ton aus", "keine Verbindung zum Logistik-Rechner",
            "Lade…", "Keine Online-Daten: Dieser Zug ist nicht über XTC mit iTrain verbunden.",
            "Der Zug steht in iTrain in keinem Block.", "Richtungswechsel", "{0} mm > Position {1}", "Weichen",
            "Halt noch {0} s", "Route", "Ziel", "dahinter", "vorher", "aktuell", "danach", "Bremst", "Halt erwarten",
            "Langsamfahrt erwarten", "Zuglänge {0} mm");

    private static String seite(String pfad, String zug) {
        return KOPF.replace("%LANG%", Uebersetzung.sprache()).replace("%TITEL%", t("Routen-Anzeiger") + " – " + html(zug))
                + "body{display:flex;flex-direction:column}#kopf{display:flex;align-items:center;gap:12px;padding:6px 10px;"
                + "background:#fff;border-bottom:1px solid #d5dbe3}#name{font-weight:700;font-size:16px;flex:1}"
                + "#kopf button{border:1px solid #c5ccd6;background:#f3e3c3;border-radius:8px;padding:5px 10px;font:inherit;"
                + "cursor:pointer}#flaeche{flex:1;width:100%;min-height:200px;display:block}"
                + "</style></head><body><div id=\"kopf\"><a href=\"/\">&#8592; " + t("Züge") + "</a><span id=\"name\">"
                + html(zug) + "</span><button id=\"ton\"></button><button id=\"voll\">⛶ " + t("Vollbild") + "</button></div>"
                + "<canvas id=\"flaeche\"></canvas><script>" + Webansicht.woerter(JS_WOERTER) + "const PFAD=" + q(pfad)
                + ";document.getElementById('voll').onclick=()=>{if(document.fullscreenElement)document.exitFullscreen();"
                + "else document.documentElement.requestFullscreen&&document.documentElement.requestFullscreen()};"
                + "</script><script src=\"/routen.js\"></script></body></html>";
    }
}
