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
        /** Die Wartezeit ist abgelaufen, der Zug steht aber noch im Block (Halt läuft weiter). */
        private volatile boolean wartezeitVorbei;
        private volatile String route = "";
        private volatile String ziel = "";
        private volatile double tempo;
        private volatile long abfahrtZeit;
        private volatile boolean verbunden;
        // Für den Routen-Anzeiger (Anwender 10.11.2026)
        private volatile double bremse = -1;
        private volatile String blockTyp = "";
        private volatile String signal = "";
        private volatile String signalErwartet = "";
        private volatile double zugLaenge = -1;
        private volatile double wunschTempo;
        private volatile String fahrtrichtung = "";
        private volatile String steuerung = "";
        private volatile String vorherBlock;
        private volatile long positionZeit;

        /** Länge des aktuellen Blocks in cm, -1 = unbekannt. */
        public double getBlockLaenge() {
            return blockLaenge;
        }

        /** Haltepunkt im Block in cm ab Einfahrt, -1 = keiner gemeldet. */
        public double getStop() {
            return stop;
        }

        /** Beginn des Bremsens im Block in cm ab Einfahrt, -1 = nicht gemeldet. */
        public double getBremse() {
            return bremse;
        }

        /** Position der Zugspitze im Block in cm ab Einfahrt, -1 = unbekannt. */
        public double getPosition() {
            return position;
        }

        /** Zeitpunkt der letzten Positionsmeldung (ms). */
        public long getPositionZeit() {
            return positionZeit;
        }

        public double getTempo() {
            return tempo;
        }

        public double getWunschTempo() {
            return wunschTempo;
        }

        /** iTrain-Blocktyp: station, siding, free, none … */
        public String getBlockTyp() {
            return blockTyp;
        }

        /** Signal am Blockende: go, restricted, stop; leer = unbekannt. */
        public String getSignal() {
            return signal;
        }

        public String getSignalErwartet() {
            return signalErwartet;
        }

        /** Zuglänge in cm laut iTrain, -1 = unbekannt. */
        public double getZugLaenge() {
            return zugLaenge;
        }

        /** forward / backward laut iTrain. */
        public String getFahrtrichtung() {
            return fahrtrichtung;
        }

        /** manual / semi / automatic. */
        public String getSteuerung() {
            return steuerung;
        }

        /** Der Block, aus dem der Zug in den aktuellen eingefahren ist, sonst null. */
        public String getVorherBlock() {
            return vorherBlock;
        }

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
            if (wartezeitVorbei && tempo <= 0.05) {
                return "Wartezeit vorbei, steht";
            }
            double noch = nochBisHalt();
            return noch >= 0 ? "noch " + Math.round(noch) + " cm" : "";
        }
    }

    /** Pause zwischen zwei Runden der Zustandsabfrage stehender Züge (ms). */
    static final int ABFRAGE_MS = 2000;

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
    /** Gelernte Blocklängen in cm (aus allen Zugmeldungen), für Nachbarblöcke im Routen-Anzeiger. */
    private final Map<String, Double> blockLaengen = new ConcurrentHashMap<>();
    /** Gelernte Blockfolgen je Zug: „Zug|Block“ → der Block, in den der Zug danach gefahren ist. */
    private final Map<String, String> nachfolger = new ConcurrentHashMap<>();
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
        if (!abfrageLaeuft) {
            abfrageLaeuft = true;
            Thread.ofVirtual().name("XTC Abfrage").start(this::abfrage);
        }
    }

    private volatile boolean abfrageLaeuft;

    /**
     * Zustandsabfrage stehender Züge (Anwender 10.11.2026): iTrain meldet über XTC einen neuen Block nur, solange
     * die Verbindung des Zugs Änderungen aus der Fahrt bekommt; wird ein stehender Zug in iTrain von Hand in einen
     * Block gesetzt (z. B. nach einem Kontaktproblem), kommt auf seiner Verbindung nichts, und ein erneutes
     * {@code <train name>} für denselben Zug bleibt ohne Antwort (an iTrain 6.0.4-b300 geprüft). Eine eigene
     * Verbindung wählt deshalb reihum jeden stehenden Zug an – beim Wechsel des Zugs schickt iTrain den
     * Gesamtzustand. Weicht dessen Block vom bekannten ab, wird die Zeile wie eine Meldung des Zugs verarbeitet
     * (Einfahrt/Abfahrt wie sonst). Im Rx/Tx-Fenster erscheinen nur solche Änderungen.
     */
    private void abfrage() {
        Pattern block = Pattern.compile("<block\\s+name=\"([^\"]*)\"");
        try {
            while (laeuft) {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(rechner, port), 2000);
                    s.setSoTimeout(150);
                    OutputStream out = s.getOutputStream();
                    InputStream in = s.getInputStream();
                    byte[] puffer = new byte[16384];
                    while (laeuft) {
                        for (ZugVerbindung v : new ArrayList<>(verbindungen.values())) {
                            ZugInfo i = v.info;
                            if (!laeuft || !i.verbunden || (i.block != null && (i.tempo > 0.05 || i.wartet))) {
                                continue; // fahrende und planmäßig haltende Züge melden sich selbst
                            }
                            out.write(("<train name=\"" + escape(v.zug) + "\"/>\n").getBytes(StandardCharsets.UTF_8));
                            out.flush();
                            StringBuilder sb = new StringBuilder();
                            String kopf = "<train name=\"" + escape(v.zug) + "\" ";
                            String zustand = null;
                            long bis = System.currentTimeMillis() + 800;
                            while (zustand == null && System.currentTimeMillis() < bis) {
                                int n;
                                try {
                                    n = in.read(puffer);
                                } catch (java.net.SocketTimeoutException still) {
                                    continue;
                                }
                                if (n < 0) {
                                    throw new IOException("beendet");
                                }
                                sb.append(new String(puffer, 0, n, StandardCharsets.UTF_8));
                                for (String z : sb.toString().split("\n")) {
                                    if (z.startsWith(kopf) && z.contains("</train>") && z.contains("<control>")) {
                                        zustand = z.trim();
                                    }
                                }
                            }
                            if (zustand == null) {
                                continue;
                            }
                            Matcher m = block.matcher(zustand);
                            String jetzt = m.find() ? entities(m.group(1)) : null;
                            if (!java.util.Objects.equals(jetzt, i.block) && !(i.tempo > 0.05 || i.wartet)) {
                                v.zeile(true, zustand);
                                v.verarbeite(zustand);
                            }
                        }
                        Thread.sleep(ABFRAGE_MS);
                    }
                } catch (IOException ex) {
                    // iTrain nicht erreichbar: später erneut
                }
                if (laeuft) {
                    Thread.sleep(10_000);
                }
            }
        } catch (InterruptedException ex) {
            // beendet
        } finally {
            abfrageLaeuft = false;
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

    /** Länge eines Blocks in cm, soweit iTrain sie schon gemeldet hat, sonst -1. */
    public double blockLaenge(String block) {
        Double d = block == null ? null : blockLaengen.get(block);
        return d == null ? -1 : d;
    }

    /** Der Block, in den dieser Zug zuletzt nach {@code block} gefahren ist, sonst null. */
    public String gelernterNachfolger(String zug, String block) {
        return zug == null || block == null ? null : nachfolger.get(zug + "|" + block);
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
        synchronized void verarbeite(String zeile) {
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
                case "position" -> {
                    info.position = zahl(text, -1);
                    info.positionZeit = System.currentTimeMillis();
                }
                case "speed" -> {
                    if (a.containsKey("actual")) {
                        info.tempo = zahl(a.get("actual"), 0);
                        if (info.wartezeitVorbei && info.tempo > 0.05) {
                            abfahrt(); // fährt nach dem Halt an
                        }
                    }
                    if (a.containsKey("desired")) {
                        info.wunschTempo = zahl(a.get("desired"), 0);
                    }
                    if (a.containsKey("direction")) {
                        info.fahrtrichtung = a.get("direction");
                    }
                }
                case "signal" -> {
                    info.signal = a.getOrDefault("current", "");
                    info.signalErwartet = a.getOrDefault("expected", "");
                }
                case "control" -> info.steuerung = text == null ? "" : text.trim();
                case "train" -> {
                    if (a.containsKey("length")) {
                        info.zugLaenge = zahl(a.get("length"), -1);
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
                // erneuter Gesamtzustand oder Richtungswechsel im selben Block: Marken neu, kein neues Ereignis
                info.richtung = a.getOrDefault("direction", info.richtung);
                info.stop = zahl(a.get("stop"), info.stop);
                info.bremse = zahl(a.get("brake"), -1);
                return;
            }
            if (neu != null && info.block != null) {
                nachfolger.put(zug + "|" + info.block, neu);
            }
            info.vorherBlock = neu == null ? null : info.block;
            String alt = info.block;
            if (offenerBlock != null) {
                abfahrt();
            }
            if (neu == null && alt != null) {
                melde(SimulatorAktion.LOESCHEN, alt); // Zug in iTrain vom Gleis genommen
            }
            info.block = neu;
            info.blockLaenge = zahl(a.get("length"), -1);
            info.stop = zahl(a.get("stop"), -1);
            info.bremse = zahl(a.get("brake"), -1);
            info.blockTyp = a.getOrDefault("type", "");
            if (neu != null && info.blockLaenge > 0) {
                blockLaengen.put(neu, info.blockLaenge);
            }
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
                info.wartezeitVorbei = false;
                if (info.block != null && !info.block.equals(wartenGemeldetIn)) {
                    wartenGemeldetIn = info.block;
                    offenerBlock = info.block;
                    melde(SimulatorAktion.WARTEN, info.block);
                }
            } else if (info.wartet) {
                // Ende der Wartezeit ist noch keine Abfahrt (Anwender 10.11.2026): iTrain meldet auch kurze
                // Wartezeiten (2,5 s), und am Routenende bleibt der Zug stehen. Der Halt – und damit das Umladen –
                // läuft weiter, bis der Zug wirklich anfährt oder den Block wechselt.
                info.wartet = false;
                info.wartezeitVorbei = true;
                if (info.tempo > 0.05) {
                    abfahrt();
                }
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
            info.wartezeitVorbei = false;
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
