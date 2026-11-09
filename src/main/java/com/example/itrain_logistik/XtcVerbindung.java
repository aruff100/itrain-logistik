package com.example.itrain_logistik;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Platform;

/**
 * Online-Verbindung zu iTrain über die XTC-Schnittstelle (Anwender 08.11.2026, Port 7128). XTC ist für Handregler
 * gedacht und nicht dokumentiert (Auskunft Xander, iTrain): eine TCP-Verbindung verfolgt genau einen Zug, nach
 * {@code <train name="…"/>} schickt iTrain den vollständigen Zustand und danach jede Änderung als eigene Zeile
 * (ohne Zugnamen). Ein zweites {@code <train>} auf derselben Verbindung wechselt den Zug (am 08.11.2026 an iTrain
 * 6.0.4-b300 geprüft). Deshalb hält diese Klasse je Zug eine eigene Verbindung.
 *
 * <p>Gesehene Meldungen: {@code <application name="iTrain" version="…"/>} und {@code <clock …/>} nach dem Verbinden;
 * {@code <block name length type direction="next|prev" stop …>} bei jedem neuen Block (leer = kein Block);
 * {@code <wait period="ms" passed="ms"/>} jede Sekunde während eines Halts, {@code <wait/>} am Ende;
 * {@code <route name state="active|finishing"/>}, {@code <info type="destination">Ziel</info>},
 * {@code <speed direction desired actual/>}, {@code <position>cm</position>}, {@code <signal …/>},
 * {@code <control>semi|automatic</control>}. Daraus entstehen Einfahrt, Warten und Abfahrt (einziger Weg der
 * Zugereignisse aus iTrain, Anwender 09.11.2026).
 *
 * <p>Alle Rückrufe laufen auf dem JavaFX-Anwendungsthread.
 */
public final class XtcVerbindung {

    /** Vorgabe-Port der XTC-Schnittstelle in iTrain. */
    public static final int PORT_VORGABE = 7128;

    /** Eine Zeile im Rx/Tx-Fenster: {@code rx} = von iTrain empfangen, sonst gesendet. */
    public record Zeile(long zeit, boolean rx, String zug, String text) {
    }

    /** Zugereignis wie aus einer iTrain-Aktion; {@code richtung} = "next"/"prev" laut iTrain. */
    public record Ereignis(String zug, SimulatorAktion aktion, String block, String richtung) {
    }

    /** Laufender Zustand eines Zugs laut XTC (nur Anzeige). */
    public static final class ZugInfo {
        private volatile String block;
        private volatile double blockLaenge = -1;
        private volatile double stop = -1;
        private volatile double position = -1;
        private volatile String richtung = "";
        private volatile long wartenGesamt;
        private volatile long wartenVorbei;
        private volatile boolean wartet;
        private volatile String route = "";
        private volatile String ziel = "";
        private volatile double tempo;
        private volatile long abfahrtZeit;
        private volatile boolean verbunden;

        public String getBlock() {
            return block;
        }

        public String getRichtung() {
            return richtung;
        }

        public String getRoute() {
            return route;
        }

        public String getZiel() {
            return ziel;
        }

        public boolean isWartet() {
            return wartet;
        }

        public long getWartenGesamt() {
            return wartenGesamt;
        }

        public long getWartenVorbei() {
            return wartenVorbei;
        }

        public boolean isVerbunden() {
            return verbunden;
        }

        /** Zentimeter bis zum Haltepunkt im Block, oder -1, wenn das nicht sinnvoll ist. */
        public double nochBisHalt() {
            if (block == null || stop < 0 || position < 0 || tempo <= 0.05 || wartet) {
                return -1;
            }
            double rest = stop - position;
            return rest >= 0 && (blockLaenge < 0 || rest <= blockLaenge) ? rest : -1;
        }

        /**
         * Kurzer Text für die Anzeige am Gleis (Anwender 08.11.2026): bei der Einfahrt „noch 12 cm“, während des
         * Halts die verbleibende Wartezeit, nach der Abfahrt einige Sekunden lang die Route.
         */
        public String anzeige() {
            if (wartet && wartenGesamt > 0) {
                long rest = Math.max(0, wartenGesamt - wartenVorbei) / 1000;
                return "Halt noch " + rest + " s von " + wartenGesamt / 1000 + " s";
            }
            if (abfahrtZeit > 0 && System.currentTimeMillis() - abfahrtZeit < ABFAHRT_ANZEIGE_MS && !route.isEmpty()) {
                return "→ " + route + (ziel.isEmpty() ? "" : " (Ziel " + ziel + ")");
            }
            double noch = nochBisHalt();
            return noch >= 0 ? "noch " + Math.round(noch) + " cm" : "";
        }
    }

    /** So lange zeigt die Anzeige nach der Abfahrt die Route (Annahme, nicht vom Anwender vorgegeben). */
    static final long ABFAHRT_ANZEIGE_MS = 8000;

    private final String rechner;
    private final int port;
    private final Consumer<Ereignis> aufEreignis;
    private final Consumer<Zeile> aufZeile;
    /** Modellzeit aus iTrain ({@code <clock hours minutes/>}), nach jedem Empfang; kann null sein. */
    private volatile Consumer<java.time.LocalTime> aufUhr;
    private final Map<String, ZugVerbindung> verbindungen = new ConcurrentHashMap<>();
    private final Map<String, ZugInfo> infos = new ConcurrentHashMap<>();
    private volatile boolean laeuft;
    private volatile String version = "";

    public XtcVerbindung(String rechner, int port, Consumer<Ereignis> aufEreignis, Consumer<Zeile> aufZeile) {
        this.rechner = rechner == null || rechner.isBlank() ? "localhost" : rechner.trim();
        this.port = port > 0 ? port : PORT_VORGABE;
        this.aufEreignis = aufEreignis;
        this.aufZeile = aufZeile;
    }

    public void setAufUhr(Consumer<java.time.LocalTime> aufUhr) {
        this.aufUhr = aufUhr;
    }

    public String getRechner() {
        return rechner;
    }

    public int getPort() {
        return port;
    }

    /** iTrain-Version aus der Begrüßung, leer solange keine Verbindung stand. */
    public String getVersion() {
        return version;
    }

    /** Startet je Zug eine Verbindung; wiederholt alle 10 s, solange iTrain nicht erreichbar ist. */
    public synchronized void starte(Collection<String> zuege) {
        laeuft = true;
        for (String zug : zuege) {
            if (zug != null && !zug.isBlank() && !verbindungen.containsKey(zug)) {
                ZugVerbindung v = new ZugVerbindung(zug);
                verbindungen.put(zug, v);
                infos.put(zug, v.info);
                Thread.ofVirtual().name("XTC " + zug).start(v::lauf);
            }
        }
    }

    public synchronized void stoppe() {
        laeuft = false;
        for (ZugVerbindung v : verbindungen.values()) {
            v.schliesse();
        }
        verbindungen.clear();
    }

    public boolean laeuft() {
        return laeuft;
    }

    /** Zahl der Züge mit stehender Verbindung. */
    public int verbundeneZuege() {
        int n = 0;
        for (ZugInfo i : infos.values()) {
            if (i.verbunden) {
                n++;
            }
        }
        return n;
    }

    public int zuegeGesamt() {
        return verbindungen.size();
    }

    public ZugInfo info(String zug) {
        return zug == null ? null : infos.get(zug);
    }

    // ------------------------------------------------------------------ Prüfen, ob iTrain läuft

    /** Ergebnis der Prüfung: erreichbar, iTrain-Version und die Züge laut iTrain. */
    public record Pruefung(boolean erreichbar, String version, List<String> zuege, String fehler) {
    }

    /**
     * Verbindet sich einmal, liest die Begrüßung und fragt mit {@code <trains/>} die Züge ab (Anwender 08.11.2026:
     * „Kannst Du ermitteln ob iTrain auf dem entfernten Computer läuft?“). Läuft im aufrufenden Thread.
     */
    public static Pruefung pruefe(String rechner, int port, int timeoutMs) {
        String host = rechner == null || rechner.isBlank() ? "localhost" : rechner.trim();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(400);
            s.getOutputStream().write("<trains/>\n".getBytes(StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            StringBuilder sb = new StringBuilder();
            byte[] puffer = new byte[8192];
            long ende = System.currentTimeMillis() + Math.max(1500, timeoutMs);
            while (System.currentTimeMillis() < ende && !sb.toString().contains("</trains>")) {
                try {
                    int n = s.getInputStream().read(puffer);
                    if (n < 0) {
                        break;
                    }
                    sb.append(new String(puffer, 0, n, StandardCharsets.UTF_8));
                } catch (java.net.SocketTimeoutException ex) {
                    // weiter warten
                }
            }
            String text = sb.toString();
            Matcher app = Pattern.compile("<application\\s+name=\"([^\"]*)\"\\s+version=\"([^\"]*)\"").matcher(text);
            String version = app.find() ? entities(app.group(1)) + " " + entities(app.group(2)) : "";
            List<String> zuege = new ArrayList<>();
            Matcher t = Pattern.compile("<train\\s+name=\"([^\"]*)\"").matcher(
                    text.contains("<trains") ? text.substring(text.indexOf("<trains")) : "");
            while (t.find()) {
                zuege.add(entities(t.group(1)));
            }
            if (version.isEmpty() && zuege.isEmpty()) {
                return new Pruefung(false, "", zuege, "Port " + port + " antwortet, aber nicht wie iTrain-XTC");
            }
            return new Pruefung(true, version, zuege, "");
        } catch (IOException ex) {
            return new Pruefung(false, "", List.of(), ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /**
     * Nur die Begrüßung lesen (für die Erkennung alle 10 s): iTrain-Version oder null, wenn auf dem Port kein
     * iTrain-XTC antwortet. Die Verbindung steht dabei nur Sekundenbruchteile.
     */
    public static String begruessung(String rechner, int port, int timeoutMs) {
        String host = rechner == null || rechner.isBlank() ? "localhost" : rechner.trim();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            byte[] puffer = new byte[512];
            StringBuilder sb = new StringBuilder();
            long ende = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < ende && sb.indexOf("/>") < 0) {
                int n = s.getInputStream().read(puffer);
                if (n < 0) {
                    break;
                }
                sb.append(new String(puffer, 0, n, StandardCharsets.UTF_8));
            }
            Matcher m = Pattern.compile("<application\\s+name=\"iTrain\"\\s+version=\"([^\"]*)\"").matcher(sb);
            return m.find() ? "iTrain " + m.group(1) : null;
        } catch (IOException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ eine Verbindung je Zug

    private final class ZugVerbindung {
        private final String zug;
        private final ZugInfo info = new ZugInfo();
        private volatile Socket socket;
        /** Block, für den schon WARTEN gemeldet wurde (sonst null). */
        private String wartenGemeldetIn;
        /** Block, für den die Abfahrt noch aussteht. */
        private String offenerBlock;

        ZugVerbindung(String zug) {
            this.zug = zug;
        }

        void lauf() {
            while (laeuft) {
                try (Socket s = new Socket()) {
                    socket = s;
                    s.connect(new InetSocketAddress(rechner, port), 2000);
                    s.setSoTimeout(0);
                    info.verbunden = true;
                    sende(s.getOutputStream(), "<application name=\"iTrain-Logistik\" version=\""
                            + AppInfo.getBuildNumber() + "\"/>");
                    sende(s.getOutputStream(), "<train name=\"" + escape(zug) + "\"/>");
                    lies(s.getInputStream());
                } catch (IOException ex) {
                    if (laeuft) {
                        zeile(true, "[keine Verbindung: " + ex.getMessage() + "]");
                    }
                } finally {
                    info.verbunden = false;
                    socket = null;
                }
                if (!laeuft) {
                    break;
                }
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }

        void schliesse() {
            Socket s = socket;
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ex) {
                    // egal
                }
            }
        }

        private void sende(OutputStream out, String text) throws IOException {
            out.write((text + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            zeile(false, text);
        }

        private void lies(InputStream in) throws IOException {
            byte[] puffer = new byte[8192];
            java.io.ByteArrayOutputStream rest = new java.io.ByteArrayOutputStream();
            int n;
            while (laeuft && (n = in.read(puffer)) >= 0) {
                for (int i = 0; i < n; i++) {
                    if (puffer[i] == '\n') {
                        String z = rest.toString(StandardCharsets.UTF_8).trim();
                        rest.reset();
                        if (!z.isEmpty()) {
                            zeile(true, z);
                            verarbeite(z);
                        }
                    } else {
                        rest.write(puffer[i]);
                    }
                }
            }
            zeile(true, "[Verbindung von iTrain beendet]");
        }

        private void zeile(boolean rx, String text) {
            Zeile z = new Zeile(System.currentTimeMillis(), rx, zug, text);
            Platform.runLater(() -> aufZeile.accept(z));
        }

        private void melde(SimulatorAktion aktion, String block) {
            Ereignis e = new Ereignis(zug, aktion, block, info.richtung);
            Platform.runLater(() -> aufEreignis.accept(e));
        }

        /** Wertet eine Zeile aus; Kinder von {@code <block>} (dessen Geschwindigkeitsgrenze) werden übergangen. */
        void verarbeite(String zeile) {
            Matcher m = TAG.matcher(zeile);
            int tiefe = 0;
            boolean inBlock = false;
            int blockTiefe = -1;
            while (m.find()) {
                boolean schliessend = !m.group(1).isEmpty();
                String name = m.group(2);
                boolean leer = !m.group(4).isEmpty();
                if (schliessend) {
                    tiefe--;
                    if (inBlock && tiefe == blockTiefe) {
                        inBlock = false;
                    }
                    continue;
                }
                Map<String, String> a = attribute(m.group(3));
                String text = leer ? "" : textNach(zeile, m.end());
                if (!inBlock) {
                    tag(name, a, text);
                }
                if (!leer) {
                    if ("block".equals(name)) {
                        inBlock = true;
                        blockTiefe = tiefe;
                    }
                    tiefe++;
                }
            }
        }

        private void tag(String name, Map<String, String> a, String text) {
            switch (name) {
                case "application" -> {
                    if ("iTrain".equals(a.get("name"))) {
                        version = "iTrain " + a.getOrDefault("version", "");
                    }
                }
                case "block" -> blockGemeldet(a);
                case "clock" -> {
                    Consumer<java.time.LocalTime> u = aufUhr;
                    if (u != null && a.containsKey("hours") && a.containsKey("minutes")) {
                        int h = (int) zahl(a.get("hours"), -1);
                        int m = (int) zahl(a.get("minutes"), -1);
                        if (h >= 0 && h < 24 && m >= 0 && m < 60) {
                            java.time.LocalTime t = java.time.LocalTime.of(h, m);
                            Platform.runLater(() -> u.accept(t));
                        }
                    }
                }
                case "wait" -> wartenGemeldet(a);
                case "position" -> info.position = zahl(text, -1);
                case "speed" -> {
                    if (a.containsKey("actual")) {
                        info.tempo = zahl(a.get("actual"), 0);
                    }
                }
                case "route" -> {
                    // „<route/>“ (Route beendet) behält den Namen für die Anzeige nach der Abfahrt
                    if (a.containsKey("name")) {
                        info.route = a.get("name");
                    }
                }
                case "info" -> {
                    if ("destination".equals(a.get("type"))) {
                        info.ziel = text;
                    }
                }
                default -> {
                    // signal, control, clock, functions, limit, type: nur im Rx/Tx-Fenster
                }
            }
        }

        private void blockGemeldet(Map<String, String> a) {
            String neu = a.get("name");
            if (neu != null && neu.equals(info.block)) {
                info.richtung = a.getOrDefault("direction", info.richtung);
                return; // erneuter Gesamtzustand, gleicher Block
            }
            if (offenerBlock != null) {
                abfahrt();
            }
            info.block = neu;
            info.blockLaenge = zahl(a.get("length"), -1);
            info.stop = zahl(a.get("stop"), -1);
            info.position = -1;
            info.richtung = a.getOrDefault("direction", "");
            if (neu != null) {
                offenerBlock = neu;
                melde(SimulatorAktion.EINFAHRT, neu);
            }
        }

        private void wartenGemeldet(Map<String, String> a) {
            long periode = (long) zahl(a.get("period"), 0);
            if (periode > 0) {
                info.wartenGesamt = periode;
                info.wartenVorbei = (long) zahl(a.get("passed"), 0);
                info.wartet = true;
                if (info.block != null && !info.block.equals(wartenGemeldetIn)) {
                    wartenGemeldetIn = info.block;
                    offenerBlock = info.block;
                    melde(SimulatorAktion.WARTEN, info.block);
                }
            } else if (info.wartet) {
                info.wartet = false;
                abfahrt();
            }
        }

        private void abfahrt() {
            if (offenerBlock != null) {
                melde(SimulatorAktion.ABFAHRT, offenerBlock);
                info.abfahrtZeit = System.currentTimeMillis();
            }
            offenerBlock = null;
            wartenGemeldetIn = null;
            info.wartet = false;
            info.wartenGesamt = 0;
            info.wartenVorbei = 0;
        }
    }

    // ------------------------------------------------------------------ kleiner XML-Zerleger für eine Zeile

    private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z_][\\w.-]*)((?:\\s+[\\w.-]+\\s*=\\s*\"[^\"]*\")*)\\s*(/?)>");
    private static final Pattern ATTR = Pattern.compile("([\\w.-]+)\\s*=\\s*\"([^\"]*)\"");

    static Map<String, String> attribute(String text) {
        Map<String, String> a = new java.util.LinkedHashMap<>();
        Matcher m = ATTR.matcher(text == null ? "" : text);
        while (m.find()) {
            a.put(m.group(1), entities(m.group(2)));
        }
        return a;
    }

    private static String textNach(String zeile, int ab) {
        int ende = zeile.indexOf('<', ab);
        return entities((ende < 0 ? zeile.substring(ab) : zeile.substring(ab, ende)).trim());
    }

    private static double zahl(String s, double sonst) {
        if (s == null || s.isBlank()) {
            return sonst;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            return sonst;
        }
    }

    /** XML-Entities auflösen ({@code &#223;} = ß kommt von iTrain so). */
    static String entities(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s;
        }
        Matcher m = Pattern.compile("&(#x[0-9A-Fa-f]+|#[0-9]+|amp|lt|gt|quot|apos);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String r = switch (e) {
                case "amp" -> "&";
                case "lt" -> "<";
                case "gt" -> ">";
                case "quot" -> "\"";
                case "apos" -> "'";
                default -> new String(Character.toChars(e.startsWith("#x")
                        ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1))));
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Zugname für {@code <train name="…"/>}: Umlaute als Zeichenreferenz (am 08.11.2026 blieb „Güter1“ in UTF-8
     * ohne Antwort; iTrain selbst schickt „G&#252;ter1“).
     */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (c > 126) {
                        sb.append("&#").append((int) c).append(';');
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
