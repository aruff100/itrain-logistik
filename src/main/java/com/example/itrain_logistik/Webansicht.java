package com.example.itrain_logistik;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import com.example.itrain_logistik.kern.Anlage;
import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.Gleis;
import com.example.itrain_logistik.kern.GleisArt;
import com.example.itrain_logistik.kern.Gut;
import com.example.itrain_logistik.kern.Lager;
import com.example.itrain_logistik.kern.Modul;
import com.example.itrain_logistik.kern.Ort;
import com.example.itrain_logistik.kern.Station;
import com.example.itrain_logistik.kern.ZugZustand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Browser-Ansicht (Anwender 09.11.2026): ein kleiner Webserver im Hauptprogramm zeigt die Module – und die ganze
 * Anlage – nur lesend im Browser, auf einem anderen Rechner, Handy oder Tablet im Heimnetz. Je Bahnhof ein Reiter
 * mit Güterbahnhof, Orten, Betrieben und Lagern, dazu ein Reiter „Züge“ mit allen Zügen. Ohne Zusatzbibliothek
 * ({@code jdk.httpserver}). Die Daten baut {@link #aktualisiere} jede Sekunde auf dem JavaFX-Thread als JSON; die
 * Anfragen liefern nur diesen Stand aus.
 *
 * <p>Seiten: {@code /} (Übersicht der Module), {@code /modul/<pfad>} (ein Modul), {@code /modul/alle} (ganze
 * Anlage), {@code /daten/<pfad>} (JSON).
 */
public final class Webansicht {

    public static final int PORT_VORGABE = 8090;

    private static HttpServer server;
    private static int laufenderPort;
    private static volatile Map<String, String> daten = Map.of();
    private static volatile Map<String, String> titel = Map.of();
    /** Objekt-Id → Objekt mit Geräusch (Anwender 09.11.2026); die Datei wählt {@link Geraeusche#datei} je Abruf. */
    private static volatile Map<String, AnlagenObjekt> klaenge = Map.of();
    private static Map<String, AnlagenObjekt> klaengeNeu = new java.util.HashMap<>();

    private Webansicht() {
    }

    /** Pfad eines Moduls in der Adresse: Name klein, ohne Sonderzeichen. */
    public static String pfad(Modul m) {
        String s = m.getName() == null ? "" : m.getName().toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? m.getId().substring(0, 8) : s;
    }

    /** Adresse dieses Rechners im Heimnetz (erste private IPv4-Adresse einer aktiven Netzwerkkarte). */
    public static String eigeneAdresse() {
        try {
            for (java.net.NetworkInterface n : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!n.isUp() || n.isLoopback() || n.isVirtual()) {
                    continue;
                }
                for (java.net.InetAddress ad : java.util.Collections.list(n.getInetAddresses())) {
                    if (ad instanceof java.net.Inet4Address && ad.isSiteLocalAddress()) {
                        return ad.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException ex) {
            // weiter unten
        }
        return "localhost";
    }

    /** Name dieses Rechners (z. B. „BUERO3“), sonst null. */
    public static String rechnername() {
        try {
            String n = java.net.InetAddress.getLocalHost().getHostName();
            return n == null || n.isBlank() || n.equals("localhost") ? null : n;
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    /** Adresse der Startseite, z. B. http://192.168.0.165:8090/ */
    public static String startAdresse() {
        return "http://" + eigeneAdresse() + ":" + AppSettings.getInstance().getWebPort() + "/";
    }

    public static synchronized boolean laeuft() {
        return server != null;
    }

    public static synchronized int getPort() {
        return laufenderPort;
    }

    /** Startet den Server (oder lässt ihn laufen, wenn er schon auf dem Port läuft). */
    public static synchronized void starte(int port) throws IOException {
        if (server != null && laufenderPort == port) {
            return;
        }
        stoppe();
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", Webansicht::anfrage);
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

    // ------------------------------------------------------------------ Daten (JavaFX-Thread)

    /** Baut den Stand aller Module und der ganzen Anlage als JSON (vom Hauptfenster jede Sekunde aufgerufen). */
    public static void aktualisiere(Anlage a, String modellzeit) {
        if (a == null || !laeuft()) {
            return;
        }
        Map<String, String> d = new LinkedHashMap<>();
        Map<String, String> t = new LinkedHashMap<>();
        klaengeNeu = new java.util.HashMap<>();
        List<Station> alle = new ArrayList<>();
        for (Station s : a.stationen()) {
            if (a.gehoertZu(s) == null) {
                alle.add(s);
            }
        }
        d.put("alle", json(a, Uebersetzung.tr("Ganze Anlage"), alle, modellzeit));
        t.put("alle", Uebersetzung.tr("Ganze Anlage"));
        for (Modul m : a.module()) {
            if (!m.isImBrowser()) {
                continue;
            }
            List<Station> st = new ArrayList<>();
            for (AnlagenObjekt o : a.modulInhalt(m)) {
                if (o instanceof Station s && a.gehoertZu(s) == null) {
                    st.add(s);
                }
            }
            d.put(pfad(m), json(a, m.getAnzeigeName(), st, modellzeit));
            t.put(pfad(m), m.getAnzeigeName());
        }
        daten = d;
        titel = t;
        klaenge = klaengeNeu;
    }

    private static String json(Anlage a, String name, List<Station> bahnhoefe, String zeit) {
        StringBuilder j = new StringBuilder("{\"name\":").append(q(name)).append(",\"zeit\":").append(q(zeit))
                .append(",\"reiter\":[");
        boolean erster = true;
        List<String> zuegeImModul = new ArrayList<>();
        for (Station b : bahnhoefe) {
            j.append(erster ? "" : ",");
            erster = false;
            j.append("{\"name\":").append(q(b.getAnzeigeName())).append(",\"kaesten\":[");
            List<AnlagenObjekt> kaesten = new ArrayList<>();
            kaesten.add(b);
            for (Station s : a.stationen()) {
                if (a.gehoertZu(s) == b) {
                    kaesten.add(s);
                }
            }
            for (AnlagenObjekt o : new ArrayList<>(kaesten)) {
                if (o instanceof Station s) {
                    for (String id : s.getEinzugsbereich()) {
                        AnlagenObjekt x = a.objekt(id);
                        if (x != null && !kaesten.contains(x)) {
                            kaesten.add(x);
                        }
                    }
                    for (Gleis g : s.getGleise()) {
                        if (g.getZugImGleis() != null && !zuegeImModul.contains(g.getZugImGleis())) {
                            zuegeImModul.add(g.getZugImGleis());
                        }
                    }
                }
            }
            boolean k1 = true;
            for (AnlagenObjekt o : kaesten) {
                j.append(k1 ? "" : ",");
                k1 = false;
                kasten(a, o, j);
            }
            j.append("]}");
        }
        j.append("],\"zuege\":[");
        boolean z1 = true;
        for (ZugZustand z : a.getZuege().values()) {
            j.append(z1 ? "" : ",");
            z1 = false;
            double[] p = z.passagiereSumme();
            j.append("{\"name\":").append(q(z.getName())).append(",\"block\":").append(q(z.getBlock()))
                    .append(",\"station\":").append(q(z.getStation() != null ? z.getStation().getAnzeigeName() : ""))
                    .append(",\"halt\":").append(z.isHaltend())
                    .append(",\"route\":").append(q(z.getLinie()))
                    .append(",\"hier\":").append(zuegeImModul.contains(z.getName()))
                    .append(",\"reisende\":").append(Math.round(p[0] + p[1]))
                    .append(",\"plaetze\":").append(Math.round(z.getSitzplaetzeErste() + z.getSitzplaetzeZweite()))
                    .append(",\"ladung\":");
            mengen(a, z.ladungJeGut(), j);
            j.append("}");
        }
        return j.append("]}").toString();
    }

    private static void kasten(Anlage a, AnlagenObjekt o, StringBuilder j) {
        j.append("{\"name\":").append(q(o.getAnzeigeName())).append(",\"art\":").append(q(Uebersetzung.tr(o.getArt().getLabel())));
        // Geräusch beim Antippen (Anwender 09.11.2026)
        if (AppSettings.getInstance().isGeraeuscheImBrowser() && Geraeusche.hat(o)) {
            klaengeNeu.put(o.getId(), o);
            j.append(",\"klang\":").append(q(o.getId()));
        }
        if (o instanceof Station s) {
            j.append(",\"gleise\":[");
            boolean g1 = true;
            for (Gleis g : s.getGleise()) {
                j.append(g1 ? "" : ",");
                g1 = false;
                double w = 0;
                for (double[] x : g.getWartende().values()) {
                    w += x[0] + x[1];
                }
                j.append("{\"name\":").append(q(g.getAnzeigeName()))
                        .append(",\"bahnsteig\":").append(g.getArt() == GleisArt.BAHNSTEIG)
                        .append(",\"wartend\":").append(Math.round(w))
                        .append(",\"zug\":").append(q(g.getZugImGleis()))
                        .append(",\"bereit\":");
                mengen(a, g.getAusgang(), j);
                zugAmGleis(a, g, j);
                j.append("}");
            }
            j.append("]");
        } else if (o instanceof Ort ort) {
            j.append(",\"reisende\":").append(Math.round(ort.getReisende())).append(",\"bedarf\":");
            mengen(a, ort.getOffenerBedarf(), j);
        } else if (o instanceof Betrieb b) {
            j.append(",\"eingang\":");
            mengen(a, b.getBestandEin(), j);
            j.append(",\"ausgang\":");
            mengen(a, b.getBestandAus(), j);
            j.append(",\"rezepte\":[");
            boolean r1 = true;
            for (com.example.itrain_logistik.kern.Rezept r : b.getRezepte()) {
                j.append(r1 ? "" : ",");
                r1 = false;
                j.append("{\"ein\":");
                mengen(a, ganz(r.getEingang()), j);
                j.append(",\"aus\":");
                mengen(a, ganz(r.getAusgang()), j);
                j.append("}");
            }
            j.append("]");
        } else if (o instanceof Lager l) {
            j.append(",\"bestand\":");
            mengen(a, l.getBestand(), j);
        }
        j.append("}");
    }

    /**
     * Zug am Gleis (Anwender 09.11.2026): Wartezeit bzw. Restweg aus der Online-Verbindung, ob er hält, und was
     * gerade passiert – am Bahnsteig Aus-/Einsteigen (Zahl in diesem Halt), am Ladegleis Ent-/Einladen je Gut
     * (aktiv = im letzten Schritt bewegt).
     */
    private static void zugAmGleis(Anlage a, Gleis g, StringBuilder j) {
        String name = g.getZugImGleis();
        ZugZustand z = name != null ? a.getZuege().get(name) : null;
        if (z == null) {
            return;
        }
        String info = AnlagenCanvas.zugZusatz.apply(name);
        j.append(",\"info\":").append(q(info == null ? "" : Uebersetzung.tr(info))).append(",\"haelt\":")
                .append(z.isHaltend() || Boolean.TRUE.equals(AnlagenCanvas.zugSteht.apply(name)));
        String r = g.richtungFuer(z.getRichtung());
        j.append(",\"richtung\":").append(q(r == null || r.equals(Gleis.OHNE_RICHTUNG) ? "" : r));
        if (g.getArt() == GleisArt.BAHNSTEIG) {
            j.append(",\"aus\":").append((long) Math.floor(g.getLetztAusgestiegen() + 1e-9))
                    .append(",\"ein\":").append((long) Math.floor(g.getLetztEingestiegen() + 1e-9));
        } else {
            j.append(",\"entladen\":");
            namen(a, z.entladenImHalt(), g.getAktivEntladen(), j);
            j.append(",\"geladen\":");
            namen(a, z.geladenImHalt(), g.getAktivLaden(), j);
        }
    }

    private static void namen(Anlage a, java.util.Set<String> ids, java.util.Set<String> aktiv, StringBuilder j) {
        j.append("[");
        boolean e = true;
        java.util.Set<String> alle = new java.util.LinkedHashSet<>(ids);
        alle.addAll(aktiv);
        for (String id : alle) {
            Gut gut = a.getGueter().get(id);
            j.append(e ? "" : ",");
            e = false;
            j.append("{\"gut\":").append(q(gut != null ? Uebersetzung.tr(gut.getName()) : id))
                    .append(",\"farbe\":").append(q(gut != null ? gut.getFarbe() : "#808080"))
                    .append(",\"aktiv\":").append(aktiv.contains(id)).append("}");
        }
        j.append("]");
    }

    private static Map<String, Double> ganz(Map<String, Integer> m) {
        Map<String, Double> d = new LinkedHashMap<>();
        m.forEach((k, v) -> d.put(k, v.doubleValue()));
        return d;
    }

    private static void mengen(Anlage a, Map<String, Double> m, StringBuilder j) {
        j.append("[");
        boolean e = true;
        for (Map.Entry<String, Double> x : m.entrySet()) {
            long n = (long) Math.floor(x.getValue() + 1e-9);
            if (n <= 0) {
                continue;
            }
            Gut g = a.getGueter().get(x.getKey());
            j.append(e ? "" : ",");
            e = false;
            j.append("{\"gut\":").append(q(g != null ? Uebersetzung.tr(g.getName()) : x.getKey()))
                    .append(",\"farbe\":").append(q(g != null ? g.getFarbe() : "#808080"))
                    .append(",\"menge\":").append(n).append("}");
        }
        j.append("]");
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '<' -> b.append("\\u003c");
                default -> {
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    // ------------------------------------------------------------------ HTTP

    /** Geräte, die in den letzten Sekunden Daten abgefragt haben: Adresse → Zeitpunkt (Anwender 10.11.2026). */
    private static final java.util.Map<String, Long> GERAETE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Adressen der Geräte, deren Browser die Seite gerade offen hat (letzte Abfrage höchstens 10 s her). */
    public static java.util.List<String> geraete() {
        long grenze = System.currentTimeMillis() - 10_000;
        GERAETE.values().removeIf(t -> t < grenze);
        return new java.util.ArrayList<>(new java.util.TreeSet<>(GERAETE.keySet()));
    }

    private static void anfrage(HttpExchange ex) throws IOException {
        if (ex.getRequestURI().getPath().startsWith("/daten") && ex.getRemoteAddress() != null
                && ex.getRemoteAddress().getAddress() != null) {
            GERAETE.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
        }
        try (ex) {
            String pfad = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (!"GET".equals(ex.getRequestMethod())) {
                senden(ex, 405, "text/plain", "Nur lesen");
            } else if (pfad.equals("/daten/uhr")) {
                // Uhr im Browser (Anwender 11.11.2026)
                senden(ex, 200, "application/json", UhrWindow.json());
            } else if (pfad.equals("/uhr")) {
                senden(ex, 200, "text/html", UhrWindow.seite());
            } else if (pfad.startsWith("/daten/anzeiger/")) {
                // Modul-Anzeiger (Anwender 11.11.2026): /daten/anzeiger/<modul>[/<block>]
                String[] t = pfad.substring("/daten/anzeiger/".length()).split("/", 2);
                String j = ModulAnzeiger.json(t[0], t.length > 1 ? t[1] : null);
                senden(ex, j == null ? 404 : 200, "application/json", j == null ? "{}" : j);
            } else if (pfad.startsWith("/anzeiger/")) {
                String[] t = pfad.substring("/anzeiger/".length()).split("/", 2);
                ModulAnzeiger.Bereich b = ModulAnzeiger.bereiche().get(t[0]);
                if (b == null) {
                    senden(ex, 404, "text/html", seite(Uebersetzung.tr("Nicht gefunden"), "<p>"
                            + html(Uebersetzung.tr("Kein Modul „{0}“ (oder nicht für den Browser freigegeben).", t[0]))
                            + " <a href=\"/\">" + html(Uebersetzung.tr("Zur Übersicht")) + "</a></p>"));
                } else {
                    senden(ex, 200, "text/html", ModulAnzeiger.seite(t[0], t.length > 1 ? t[1] : null, b.titel()));
                }
            } else if (pfad.startsWith("/daten/")) {
                String inhalt = daten.get(pfad.substring("/daten/".length()));
                if (inhalt == null) {
                    senden(ex, 404, "application/json", "{}");
                } else {
                    senden(ex, 200, "application/json", inhalt);
                }
            } else if (pfad.startsWith("/klang/")) {
                AnlagenObjekt o = klaenge.get(pfad.substring("/klang/".length()));
                java.nio.file.Path datei = o != null ? Geraeusche.datei(o) : null;
                if (datei == null || !java.nio.file.Files.isRegularFile(datei)) {
                    senden(ex, 404, "text/plain", Uebersetzung.tr("Kein Geräusch"));
                } else {
                    byte[] b = java.nio.file.Files.readAllBytes(datei);
                    ex.getResponseHeaders().set("Content-Type", Geraeusche.mime(datei));
                    ex.getResponseHeaders().set("Cache-Control", "no-store");
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream out = ex.getResponseBody()) {
                        out.write(b);
                    }
                }
            } else if (pfad.startsWith("/modul/")) {
                String key = pfad.substring("/modul/".length());
                if (!daten.containsKey(key)) {
                    senden(ex, 404, "text/html", seite(Uebersetzung.tr("Nicht gefunden"), "<p>" + html(Uebersetzung.tr("Kein Modul „{0}“ (oder nicht für den Browser freigegeben).", key))
                            + " <a href=\"/\">" + html(Uebersetzung.tr("Zur Übersicht")) + "</a></p>"));
                } else {
                    senden(ex, 200, "text/html", modulSeite(key, titel.getOrDefault(key, key)));
                }
            } else if (pfad.equals("/")) {
                StringBuilder l = new StringBuilder("<ul class=\"liste\">");
                for (Map.Entry<String, String> e : titel.entrySet()) {
                    l.append("<li><a href=\"/modul/").append(html(e.getKey())).append("\">").append(html(e.getValue()))
                            .append("</a></li>");
                }
                l.append("</ul>");
                // Modul-Anzeiger (Anwender 11.11.2026)
                l.append("<h2>").append(html(Uebersetzung.tr("Modul-Anzeiger"))).append("</h2><ul class=\"liste\">");
                for (ModulAnzeiger.Bereich b : ModulAnzeiger.bereiche().values()) {
                    l.append("<li><a href=\"/anzeiger/").append(html(b.schluessel())).append("\">").append(html(b.titel()))
                            .append("</a></li>");
                }
                l.append("</ul>");
                // Uhr (Anwender 11.11.2026)
                l.append("<h2>").append(html(Uebersetzung.tr("Uhr"))).append("</h2><ul class=\"liste\"><li><a href=\"/uhr\">")
                        .append(html(Uebersetzung.tr("Uhr"))).append("</a></li></ul>");
                senden(ex, 200, "text/html", seite("iTrain-Logistik", "<div class=\"start\"><h1>iTrain-Logistik</h1>"
                        + l + "</div>"));
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

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String STIL = """
            :root{--grund:#f4f5f7;--flaeche:#fff;--kasten:#fbfcfe;--rand:#c9d3e0;--rand2:#9fb3cc;--text:#1d2330;--leise:#5b6575;
             --kopf:#1f3b63;--reiter:#dbe7f5;--linie:#eef1f5;--hier:#fff8d6;--zug:#fff3b0}
            body.dunkel{--grund:#1b1f26;--flaeche:#232831;--kasten:#2a303a;--rand:#3c4554;--rand2:#4c5a70;--text:#e6e9ee;--leise:#9aa4b2;
             --kopf:#10233d;--reiter:#2f3a4a;--linie:#353d4a;--hier:#4a4325;--zug:#5c5320}
            *{box-sizing:border-box}
            body{font-family:system-ui,Segoe UI,Roboto,sans-serif;margin:0;background:var(--grund);color:var(--text)}
            header{background:var(--kopf);color:#fff;padding:8px 12px;display:flex;flex-wrap:wrap;gap:8px;justify-content:space-between;align-items:center}
            header h1{font-size:18px;margin:0}
            header .links,header .rechts{display:flex;flex-wrap:wrap;align-items:center;gap:10px}
            .zurueck,.schalter{color:#fff;text-decoration:none;font-size:14px;border:1px solid #8fa8c8;border-radius:6px;padding:4px 10px;background:#2c4f80;cursor:pointer}
            .schalter.an{background:#4f7fbf}
            .reiter{display:flex;flex-wrap:wrap;gap:6px;padding:10px 12px 0}
            .reiter button{border:1px solid var(--rand2);background:var(--reiter);color:var(--text);border-radius:8px 8px 0 0;padding:8px 12px;font-size:15px;cursor:pointer}
            .reiter button.an{background:var(--flaeche);font-weight:600;border-bottom-color:var(--flaeche)}
            main{background:var(--flaeche);margin:0 12px 12px;border:1px solid var(--rand2);padding:12px;display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:12px}
            .kasten{border:1px solid var(--rand);border-radius:10px;padding:10px;background:var(--kasten);text-align:center}
            .kasten.klingt{cursor:pointer}.kasten.klingt:active{filter:brightness(.93)}.note{font-size:13px}
            .kasten h2{font-size:16px;margin:0 0 2px}.art{font-size:12px;color:var(--leise);margin-bottom:6px}
            .unter{font-size:12px;color:var(--leise);margin-top:8px;font-weight:600}
            .zeile{display:flex;flex-wrap:wrap;justify-content:center;align-items:center;gap:6px;padding:3px 0;border-top:1px solid var(--linie);font-size:14px}
            .zeile .name{font-weight:600}
            .gut{display:inline-block;border-radius:4px;padding:1px 6px;margin:1px;font-size:12px;border:1px solid rgba(0,0,0,.25)}
            .zug{background:var(--zug);border:1px solid #c9a830;border-radius:4px;padding:0 4px}
            .rezept{font-size:13px;padding:3px 0;border-top:1px dashed var(--linie)}
            .zugzeile{display:flex;flex-wrap:wrap;justify-content:center;align-items:center;gap:6px;padding:4px;margin:2px 0 4px;border:2px solid #e0c040;border-radius:6px;font-size:13px}
            .info{font-weight:600}.tun{white-space:nowrap}
            .gut.aktiv{outline:2px solid #ff8c00;animation:blink 1s infinite}
            @keyframes blink{50%{opacity:.45}}
            .tabelle{grid-column:1/-1;overflow-x:auto}
            table{border-collapse:collapse;width:100%;table-layout:fixed}td,th{border-bottom:1px solid var(--linie);padding:6px;text-align:center;font-size:14px;overflow:hidden;text-overflow:ellipsis}
            th{position:relative;user-select:none}
            th .greifer{position:absolute;right:0;top:0;bottom:0;width:8px;cursor:col-resize;border-right:2px solid var(--rand2)}
            tr.hier{background:var(--hier)}.leer{color:var(--leise)}.liste{font-size:18px;line-height:2;padding:0 16px;list-style:none;text-align:center}
            .start{text-align:center;padding:24px 12px}.start h1{margin:0 0 12px}
            .karten{display:none;grid-column:1/-1;gap:8px}
            .karte{border:1px solid var(--rand);border-radius:10px;padding:8px;background:var(--kasten);text-align:center}
            .karte.hier{background:var(--hier)}.karte b{font-size:15px}
            @media (max-width:640px){.tabelle{display:none}.karten{display:grid}main{grid-template-columns:1fr;margin:0 6px 6px;padding:8px}}
            a{color:inherit}
            """;

    private static String seite(String titel, String inhalt) {
        return "<!doctype html><html lang=\"" + Uebersetzung.sprache() + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\"><title>" + html(titel) + "</title><style>" + STIL
                + "</style></head><body><script>try{if(localStorage.getItem('dunkel')==='1')"
                + "document.body.classList.add('dunkel')}catch(x){}</script>" + inhalt + "</body></html>";
    }

    /**
     * Seite eines Moduls (Anwender 09.11.2026): Zurück-Knopf, Schalter Hell/Dunkel und „Rezept anzeigen“ (je
     * Betrachter im Browser gemerkt), Reiter je Bahnhof, Reiter „Züge“ als Tabelle mit verstellbaren Spaltenbreiten
     * (Breiten im Browser gemerkt) bzw. hochkant auf dem Handy als Karten. Alles mittig.
     */
    /** Texte, die das Skript der Modulseite zeigt (übersetzt über T('…'), Anwender 09.11.2026). */
    private static final List<String> JS_WOERTER = List.of("hält", "Einfahrt", "ausgestiegen", "eingestiegen",
            "Ausladen", "Einladen", "Antippen: Geräusch", "Bereit zum Transport", "Reisende", "Bedarf", "Eingang",
            "Herstellung", "Rezept", "ohne Eingang", "Eingelagert", "Zug", "Ladung", "Station", "Route", "Block",
            "Hell", "Dunkel", "Ton an", "Ton aus", "Rezept ausblenden", "Rezept anzeigen", "Züge");

    /** Wörterbuch für das Skript einer Seite: {@code const TX={…};function T(s){…}} in der eingestellten Sprache. */
    static String woerter(List<String> deutsch) {
        StringBuilder b = new StringBuilder("const TX={");
        boolean erst = true;
        for (String d : deutsch) {
            String t = Uebersetzung.tr(d);
            if (t == null || t.equals(d)) {
                continue;
            }
            b.append(erst ? "" : ",").append(q(d)).append(':').append(q(t));
            erst = false;
        }
        return b.append("};function T(s){return TX[s]||s}\n").toString();
    }

    private static String modulSeite(String key, String name) {
        String js = """
                const KEY=%s;let reiter=0,stand=null;
                function lies(k,v){try{const x=localStorage.getItem(k);return x===null?v:x}catch(e){return v}}
                function schreib(k,v){try{localStorage.setItem(k,v)}catch(e){}}
                let rezept=lies('rezept','0')==='1';
                function hell(f){const h=String(f).replace('#','');if(h.length<6)return false;const r=parseInt(h.substr(0,2),16),gr=parseInt(h.substr(2,2),16),b=parseInt(h.substr(4,2),16);return 0.299*r+0.587*gr+0.114*b>150}
                function g(l){return l.length?l.map(x=>`<span class="gut" style="background:${x.farbe};color:${hell(x.farbe)?'#1d2330':'#fff'}">${e(x.gut)} ${x.menge}</span>`).join(''):'<span class="leer">–</span>'}
                function e(s){return String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
                function schild(l,pfeil){return l.map(x=>`<span class="gut ${x.aktiv?'aktiv':''}" style="background:${x.farbe};color:${hell(x.farbe)?'#1d2330':'#fff'}">${pfeil} ${e(x.gut)}</span>`).join('')}
                function zug(gl){if(!gl.zug)return '';let h=`<div class="zugzeile"><span class="zug">🚆 ${e(gl.zug)}</span>${gl.richtung?`<span>→ ${e(gl.richtung)}</span>`:''}`;
                 if(gl.info)h+=`<span class="info">⏱ ${e(gl.info)}</span>`;else if(gl.haelt)h+=`<span class="info">${T('hält')}</span>`;else h+=`<span class="info">${T('Einfahrt')}</span>`;
                 if(gl.bahnsteig){if(gl.aus)h+=`<span class="tun">▲ ${gl.aus} ${T('ausgestiegen')}</span>`;if(gl.ein)h+=`<span class="tun">▼ ${gl.ein} ${T('eingestiegen')}</span>`}
                 else{if(gl.entladen&&gl.entladen.length)h+=`<span class="tun">${T('Ausladen')} ${schild(gl.entladen,'▲')}</span>`;if(gl.geladen&&gl.geladen.length)h+=`<span class="tun">${T('Einladen')} ${schild(gl.geladen,'▼')}</span>`}
                 return h+'</div>'}
                let ton=lies('ton','1')==='1',klang=null;
                function spiele(id){if(!ton||!id)return;try{if(klang){klang.pause();klang=null}klang=new Audio('/klang/'+encodeURIComponent(id));klang.play().catch(()=>{})}catch(x){}}
                function kasten(k){let h=`<div class="kasten${k.klang?' klingt':''}"${k.klang?` onclick="spiele('${e(k.klang)}')" title="${T('Antippen: Geräusch')}"`:''}><h2>${e(k.name)}${k.klang&&ton?' <span class="note">🔊</span>':''}</h2><div class="art">${e(k.art)}</div>`;
                 if(k.gleise){const bs=k.gleise.filter(x=>x.bahnsteig),lg=k.gleise.filter(x=>!x.bahnsteig);
                  for(const gl of bs)h+=`<div class="zeile"><span class="name">${e(gl.name)}</span><span>👤 ${gl.wartend}</span></div>${zug(gl)}`;
                  if(lg.length)h+=`<div class="unter">${T('Bereit zum Transport')}</div>`;
                  for(const gl of lg)h+=`<div class="zeile"><span class="name">${e(gl.name)}</span><span>${g(gl.bereit)}</span></div>${zug(gl)}`}
                 if(k.reisende!==undefined)h+=`<div class="zeile"><span class="name">${T('Reisende')}</span><span>${k.reisende}</span></div><div class="zeile"><span class="name">${T('Bedarf')}</span><span>${g(k.bedarf)}</span></div>`;
                 if(k.eingang){h+=`<div class="zeile"><span class="name">${T('Eingang')}</span><span>${g(k.eingang)}</span></div><div class="zeile"><span class="name">${T('Herstellung')}</span><span>${g(k.ausgang)}</span></div>`;
                  if(rezept&&k.rezepte&&k.rezepte.length){h+=`<div class="unter">${T('Rezept')}</div>`;for(const r of k.rezepte)h+=`<div class="rezept">${r.ein.length?g(r.ein):`<span class="leer">${T('ohne Eingang')}</span>`} → ${g(r.aus)}</div>`}}
                 if(k.bestand)h+=`<div class="zeile"><span class="name">${T('Eingelagert')}</span><span>${g(k.bestand)}</span></div>`;
                 return h+'</div>'}
                const SP=['Zug','Reisende','Ladung','Station','Route','Block'].map(T);
                function breiten(){try{return JSON.parse(lies('spalten','null'))||[16,12,22,16,18,16]}catch(x){return [16,12,22,16,18,16]}}
                function zeile(z){return [e(z.name),z.plaetze?'👤 '+z.reisende+'/'+z.plaetze:'–',g(z.ladung),e(z.station),e(z.route),e(z.block)+(z.halt?' ('+T('hält')+')':'')]}
                function zuege(){const b=breiten();
                 let t='<div class="tabelle"><table><colgroup>'+b.map(x=>`<col style="width:${x}%%">`).join('')+'</colgroup><tr>'+SP.map((s,i)=>`<th>${s}${i<SP.length-1?`<span class="greifer" data-i="${i}"></span>`:''}</th>`).join('')+'</tr>';
                 t+=stand.zuege.map(z=>`<tr class="${z.hier?'hier':''}">`+zeile(z).map(x=>`<td>${x}</td>`).join('')+'</tr>').join('')+'</table></div>';
                 t+='<div class="karten">'+stand.zuege.map(z=>{const c=zeile(z);return `<div class="karte ${z.hier?'hier':''}"><b>${c[0]}</b><div class="zeile">${c[1]} ${c[2]}</div>${[c[3],c[4],c[5]].filter(x=>x).length?`<div class="zeile">${[c[3],c[4],c[5]].filter(x=>x).join(' · ')}</div>`:''}</div>`}).join('')+'</div>';
                 return t}
                function greifer(){document.querySelectorAll('.greifer').forEach(gr=>{gr.onpointerdown=ev=>{ev.preventDefault();const i=+gr.dataset.i,tab=gr.closest('table'),w=tab.offsetWidth,b=breiten(),x0=ev.clientX,a0=b[i],n0=b[i+1];
                  const cols=tab.querySelectorAll('col');gr.setPointerCapture(ev.pointerId);
                  gr.onpointermove=m=>{let d=(m.clientX-x0)/w*100;d=Math.max(8-a0,Math.min(n0-8,d));b[i]=a0+d;b[i+1]=n0-d;cols[i].style.width=b[i]+'%%';cols[i+1].style.width=b[i+1]+'%%'};
                  gr.onpointerup=()=>{gr.onpointermove=null;schreib('spalten',JSON.stringify(b))}}})}
                let ziehen=false;document.addEventListener('pointerdown',ev=>{ziehen=!!ev.target.closest('.greifer')});document.addEventListener('pointerup',()=>{ziehen=false});
                function knoepfe(){const d=document.body.classList.contains('dunkel');const kd=document.getElementById('dunkel');kd.textContent=d?'☀ '+T('Hell'):'☾ '+T('Dunkel');
                 const kt=document.getElementById('ton');kt.classList.toggle('an',ton);kt.textContent=ton?'🔊 '+T('Ton an'):'🔇 '+T('Ton aus');
                 const kr=document.getElementById('rezept');kr.classList.toggle('an',rezept);kr.textContent=rezept?T('Rezept ausblenden'):T('Rezept anzeigen')}
                function zeigen(){knoepfe();if(!stand||ziehen)return;const r=document.getElementById('reiter');const n=stand.reiter.length;
                 r.innerHTML=stand.reiter.map((x,i)=>`<button class="${i==reiter?'an':''}" onclick="reiter=${i};zeigen()">${e(x.name)}</button>`).join('')+`<button class="${reiter==n?'an':''}" onclick="reiter=${n};zeigen()">${T('Züge')}</button>`;
                 const m=document.getElementById('inhalt');
                 if(reiter<n)m.innerHTML=stand.reiter[reiter].kaesten.map(kasten).join('');else{m.innerHTML=zuege();greifer()}
                 document.getElementById('zeit').textContent=stand.zeit}
                document.getElementById('dunkel').onclick=()=>{const d=document.body.classList.toggle('dunkel');schreib('dunkel',d?'1':'0');knoepfe()};
                document.getElementById('ton').onclick=()=>{ton=!ton;schreib('ton',ton?'1':'0');if(!ton&&klang){klang.pause();klang=null}zeigen()};
                document.getElementById('rezept').onclick=()=>{rezept=!rezept;schreib('rezept',rezept?'1':'0');zeigen()};
                async function laden(){try{const r=await fetch('/daten/'+KEY,{cache:'no-store'});if(r.ok){stand=await r.json();if(reiter>stand.reiter.length)reiter=0;zeigen()}}catch(x){}}
                knoepfe();laden();setInterval(laden,2000);
                """.formatted(q(key));
        // Zurück (Anwender 09.11.2026): vom Modul zur ganzen Anlage, von der ganzen Anlage zur Übersicht
        String zurueck = "alle".equals(key) ? "<a class=\"zurueck\" href=\"/\">&#8592; " + html(Uebersetzung.tr("Übersicht")) + "</a>"
                : "<a class=\"zurueck\" href=\"/modul/alle\">&#8592; " + html(Uebersetzung.tr("Ganze Anlage")) + "</a>";
        return seite(name, "<header><span class=\"links\">" + zurueck + "<h1>" + html(name)
                + "</h1></span><span class=\"rechts\"><button class=\"schalter\" id=\"ton\"></button>"
                + "<button class=\"schalter\" id=\"rezept\"></button>"
                + "<button class=\"schalter\" id=\"dunkel\"></button><span id=\"zeit\"></span></span></header>"
                + "<div class=\"reiter\" id=\"reiter\"></div><main id=\"inhalt\"><p class=\"leer\">" + html(Uebersetzung.tr("Lade…")) + "</p></main>"
                + "<script>" + woerter(JS_WOERTER) + js + "</script>");
    }
}
