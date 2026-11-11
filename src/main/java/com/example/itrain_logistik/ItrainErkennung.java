package com.example.itrain_logistik;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Locale;
import java.util.Optional;

/**
 * Prüft beim Start, ob iTrain läuft (Anwender 14.10.2026). Läuft iTrain-Logistik auf demselben Rechner,
 * wird die Prozessliste durchsucht; zusätzlich (und für einen anderen Rechner ausschließlich) wird
 * versucht, den Netzwerk-Port des iTrain-Servers zu erreichen (Voreinstellungen > iTrain, Vorgabe 7120,
 * Handbuch iTrain 6.0, Kapitel Netzwerk > Server-Modus; nur in der Pro-Edition).
 *
 * <p>Die Prüfung meldet nur, ob iTrain gefunden wurde. Eine Online-Übermittlung gibt es erst mit dem
 * Protokoll, das der Anwender noch bekommt.
 */
public final class ItrainErkennung {

    /**
     * Ergebnis: gefunden und wie (Text für Tooltip/Statusleiste); {@code server} = der iTrain-Server antwortet auf
     * dem eingestellten Port (Anwender 22.10.2026: iTrain lief, nur der Server-Modus war aus).
     */
    public record Ergebnis(boolean laeuft, String wie, boolean server) {
        public Ergebnis(boolean laeuft, String wie) {
            this(laeuft, wie, false);
        }
    }

    private ItrainErkennung() {
    }

    /**
     * Zuerst die XTC-Schnittstelle (Anwender 08.11.2026: Port aus den Einstellungen, Vorgabe 7128; antwortet sie,
     * läuft iTrain und die Online-Verbindung geht), dann der iTrain-Server, zuletzt die Prozessliste.
     */
    public static Ergebnis pruefe(String rechner, int port) {
        String host = rechner == null || rechner.isBlank() ? "localhost" : rechner.trim();
        int xtcPort = AppSettings.getInstance().getXtcPort();
        String version = XtcVerbindung.begruessung(host, xtcPort, 800);
        if (version != null) {
            return new Ergebnis(true, version + " erreichbar über XTC unter " + host + ":" + xtcPort, true);
        }
        boolean lokal = istLokal(host);
        boolean server = port > 0 && portErreichbar(host, port, 800);
        if (server) {
            return new Ergebnis(true, "iTrain-Server erreichbar unter " + host + ":" + port
                    + ", aber XTC antwortet nicht auf Port " + xtcPort, false);
        }
        if (lokal) {
            Optional<String> prozess = lokalerProzess();
            if (prozess.isPresent()) {
                return new Ergebnis(true, "iTrain läuft auf diesem Rechner (" + prozess.get()
                        + "), XTC antwortet nicht auf Port " + xtcPort, false);
            }
        }
        return new Ergebnis(false, lokal
                ? "iTrain läuft nicht auf diesem Rechner (XTC-Port " + xtcPort + " antwortet nicht)"
                : "iTrain unter " + host + " nicht erreichbar (XTC-Port " + xtcPort + " antwortet nicht; läuft iTrain? "
                        + "Adresse richtig? Windows-Firewall auf dem iTrain-Rechner muss Port " + xtcPort
                        + " eingehend erlauben)");
    }

    /** Sucht einen laufenden iTrain-Prozess (nicht iTrain-Logistik und nicht die eigenen Hilfsprogramme). */
    public static Optional<String> lokalerProzess() {
        long selbst = ProcessHandle.current().pid();
        return ProcessHandle.allProcesses()
                .filter(p -> p.pid() != selbst)
                .filter(p -> istItrain(p.info().command().orElse(""), beschreibung(p)))
                .map(p -> p.info().command().orElse("PID " + p.pid()))
                .findFirst();
    }

    private static String beschreibung(ProcessHandle p) {
        ProcessHandle.Info info = p.info();
        StringBuilder sb = new StringBuilder(info.command().orElse(""));
        info.arguments().ifPresent(args -> {
            for (String a : args) {
                sb.append(' ').append(a);
            }
        });
        return sb.toString();
    }

    /**
     * iTrain = ein Programm "iTrain…" (z. B. iTrain.exe) oder eine Java-Anwendung mit iTrain-Jar bzw.
     * Klassen von Berros; ausgenommen alle Programme dieser Projektfamilie.
     */
    static boolean istItrain(String programm, String befehl) {
        if (befehl == null || befehl.isBlank()) {
            return false;
        }
        String s = befehl.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (s.contains("logistik") || s.contains("import_export") || s.contains("import-export")
                || s.contains("cargo")) {
            return false;
        }
        String pfad = (programm == null ? "" : programm).toLowerCase(Locale.ROOT).replace('\\', '/');
        String datei = pfad.substring(pfad.lastIndexOf('/') + 1);
        if (datei.startsWith("itrain")) {
            return true;
        }
        // Windows (Anwender 22.10.2026): iTrain läuft als ".../iTrain/jre/bin/javaw.exe"; die Argumente eines
        // fremden Prozesses liefert Windows meist nicht, deshalb zählt der Ordner.
        boolean java = datei.startsWith("java");
        if (java && (pfad.contains("/itrain/jre/") || pfad.contains("/itrain/bin/"))) {
            return true;
        }
        return s.contains("itrain.jar") || s.contains("/itrain/lib/") || s.contains("eu.berros")
                || s.contains("nl.berros") || s.contains("/itrain ") && s.contains("java");
    }

    static boolean istLokal(String host) {
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
            return true;
        }
        try {
            InetAddress a = InetAddress.getByName(host);
            return a.isLoopbackAddress() || NetworkInterface.getByInetAddress(a) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    static boolean portErreichbar(String host, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
