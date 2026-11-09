package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.AnlagenObjekt;
import com.example.itrain_logistik.kern.Betrieb;
import com.example.itrain_logistik.kern.BetriebVorlage;
import com.example.itrain_logistik.kern.Tf3Daten;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Geräusche zu den Symbolen (Anwender 09.11.2026): Klick auf einen Kasten der Übersicht öffnet das Detailfenster und
 * spielt parallel ein Geräusch – einmal oder, je nach Voreinstellung, solange das Fenster offen ist.
 * <p>
 * Die Dateien liegen im Geräusche-Ordner ({@link AppSettings#getGeraeuscheOrdner()}); der Dateiname beginnt mit dem
 * Namen des Symbols: „Möbelfabrik - Holz sägen.mp3“, „Möbelfabrik 2.mp3“. Gesucht wird nach dem Namen des Objekts,
 * dann nach der Betriebsvorlage (Sägewerk, …) und zuletzt nach der Objektart (Stadt, Bahnhof, …); passen mehrere
 * Dateien, wird zufällig gewählt. Mitgeliefert werden nur CC0-Aufnahmen (Projektordner {@code geraeusche/}, Ressource
 * {@code geraeusche/zuordnung.txt}), die beim Start unter dem Symbolnamen ausgepackt werden.
 */
public final class Geraeusche {

    private static final System.Logger LOG = System.getLogger(Geraeusche.class.getName());
    static final String RESSOURCE = "geraeusche/";
    static final Set<String> ENDUNGEN = Set.of("mp3", "wav", "m4a", "aif", "aiff");

    public enum Modus {
        AUS("Aus"), EINMAL("Einmal beim Öffnen"), DAUER("Solange das Fenster offen ist");

        private final String text;

        Modus(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private static final Map<Stage, MediaPlayer> DAUERND = new LinkedHashMap<>();
    private static MediaPlayer einmal;
    private static final Random ZUFALL = new Random();
    private static List<Path> liste = List.of();
    private static long listeZeit;
    private static Path listeOrdner;

    /** Zeigt einmal je Programmstart den Hinweis, wenn ein Geräusch nicht abspielbar ist (setzt das Hauptfenster). */
    static java.util.function.BiConsumer<String, String> hinweis;
    private static boolean hinweisGezeigt;

    private Geraeusche() {
    }

    static boolean linux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    /**
     * Fehler beim Abspielen (Anwender 09.11.2026): einmal je Programmstart ein Hinweis. Unter Linux braucht JavaFX
     * für mp3 die ffmpeg-Bibliotheken des Systems (libavcodec/libavformat, 54–61) und einen Tonausgang; geprüft im
     * Container: ohne Tonausgang scheitert auch wav, mit ALSA-Null-Ausgang und libavcodec 60 gehen wav und mp3.
     */
    private static void fehler(Path p, Object grund) {
        LOG.log(System.Logger.Level.WARNING, "Geräusch nicht abspielbar: " + p + " – " + grund);
        if (hinweisGezeigt || hinweis == null) {
            return;
        }
        hinweisGezeigt = true;
        hinweis.accept(linux() ? "geraeusche_linux" : "geraeusche_fehler",
                p.getFileName() + "\u0000" + (grund == null ? "" : String.valueOf(grund)));
    }

    public static Path ordner() {
        return Path.of(AppSettings.getInstance().getGeraeuscheOrdner());
    }

    /**
     * Legt den Ordner an und packt die mitgelieferten Geräusche aus: je Symbolname einmal, nur wenn dort noch keine
     * Datei mit diesem Namen liegt. Was der Anwender löscht, kommt nicht wieder ({@link AppSettings#getGeraeuscheAusgepackt()}).
     */
    public static synchronized void auspacken(boolean alleWieder) {
        AppSettings einst = AppSettings.getInstance();
        Path ordner = ordner();
        try {
            Files.createDirectories(ordner);
            Set<String> erledigt = alleWieder ? new java.util.HashSet<>() : einst.getGeraeuscheAusgepackt();
            List<Path> vorhanden = dateien(ordner);
            for (String[] z : zuordnung()) {
                String name = z[0];
                if (erledigt.contains(name)) {
                    continue;
                }
                boolean da = vorhanden.stream().anyMatch(p -> schluessel(p).equalsIgnoreCase(name));
                if (!da) {
                    try (InputStream in = Geraeusche.class.getResourceAsStream(RESSOURCE + z[1])) {
                        if (in == null) {
                            continue;
                        }
                        String datei = name + (z[2].isBlank() ? "" : " - " + z[2]) + ".mp3";
                        Files.copy(in, ordner.resolve(datei), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                erledigt.add(name);
            }
            einst.setGeraeuscheAusgepackt(erledigt);
            try (InputStream in = Geraeusche.class.getResourceAsStream(RESSOURCE + "Quellen.txt")) {
                if (in != null) {
                    Files.copy(in, ordner.resolve("Quellen der mitgelieferten Geräusche.txt"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOG.log(System.Logger.Level.WARNING, "Geräusche nicht ausgepackt: " + ordner, ex);
        }
        listeZeit = 0;
    }

    /** Zeilen „Name = Datei | Beschreibung“ der Ressource zuordnung.txt. */
    private static List<String[]> zuordnung() throws IOException {
        List<String[]> l = new ArrayList<>();
        try (InputStream in = Geraeusche.class.getResourceAsStream(RESSOURCE + "zuordnung.txt")) {
            if (in == null) {
                return l;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String zeile; (zeile = r.readLine()) != null; ) {
                zeile = zeile.strip();
                int gleich = zeile.indexOf('=');
                if (zeile.isEmpty() || zeile.startsWith("#") || gleich < 0) {
                    continue;
                }
                String rest = zeile.substring(gleich + 1);
                int strich = rest.indexOf('|');
                l.add(new String[]{zeile.substring(0, gleich).strip(),
                        (strich < 0 ? rest : rest.substring(0, strich)).strip(),
                        strich < 0 ? "" : rest.substring(strich + 1).strip()});
            }
        }
        return l;
    }

    private static List<Path> dateien(Path ordner) {
        try (Stream<Path> s = Files.list(ordner)) {
            return s.filter(Files::isRegularFile).filter(p -> ENDUNGEN.contains(endung(p))).sorted().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    /** Dateiliste höchstens alle 3 s neu lesen (die Browser-Ansicht fragt jede Sekunde für alle Objekte). */
    private static synchronized List<Path> liste() {
        Path o = ordner();
        long jetzt = System.currentTimeMillis();
        if (!o.equals(listeOrdner) || jetzt - listeZeit > 3000) {
            liste = dateien(o);
            listeOrdner = o;
            listeZeit = jetzt;
        }
        return liste;
    }

    static String endung(Path p) {
        String n = p.getFileName().toString();
        int punkt = n.lastIndexOf('.');
        return punkt < 0 ? "" : n.substring(punkt + 1).toLowerCase(Locale.ROOT);
    }

    /** Symbolname einer Datei: vor „ - “, ohne Endung und ohne angehängte Nummer („Möbelfabrik 2“). */
    static String schluessel(Path p) {
        String n = p.getFileName().toString();
        int punkt = n.lastIndexOf('.');
        if (punkt > 0) {
            n = n.substring(0, punkt);
        }
        int strich = n.indexOf(" - ");
        if (strich > 0) {
            n = n.substring(0, strich);
        }
        return n.replaceAll("[\\s_]*\\d+$", "").strip();
    }

    /** Namen, unter denen für ein Objekt gesucht wird – in dieser Reihenfolge. */
    static List<String> kandidaten(AnlagenObjekt o) {
        List<String> k = new ArrayList<>();
        k.add(o.getAnzeigeName());
        if (o instanceof Betrieb b && b.getVorlageId() != null) {
            for (BetriebVorlage v : Tf3Daten.vorlagen()) {
                if (v.id().equals(b.getVorlageId())) {
                    k.add(v.name());
                }
            }
        }
        k.add(o.getArt().getLabel());
        return k;
    }

    /** Passende Datei (zufällig unter mehreren) oder null. */
    public static Path datei(AnlagenObjekt o) {
        List<Path> alle = liste();
        if (o == null || alle.isEmpty()) {
            return null;
        }
        for (String k : kandidaten(o)) {
            if (k == null || k.isBlank()) {
                continue;
            }
            String kl = k.strip().toLowerCase(Locale.ROOT);
            List<Path> treffer = new ArrayList<>();
            for (Path p : alle) {
                String s = schluessel(p).toLowerCase(Locale.ROOT);
                if (!s.isEmpty() && (kl.equals(s) || kl.startsWith(s + " "))) {
                    treffer.add(p);
                }
            }
            if (!treffer.isEmpty()) {
                return treffer.get(ZUFALL.nextInt(treffer.size()));
            }
        }
        return null;
    }

    /** Ob es für das Objekt ein Geräusch gibt (ohne Zufall, für die Browser-Ansicht). */
    public static boolean hat(AnlagenObjekt o) {
        return datei(o) != null;
    }

    /** Zum gerade geöffneten Detailfenster: je nach Voreinstellung einmal oder dauernd bis zum Schließen. */
    public static void zumFenster(AnlagenObjekt o, Stage fenster) {
        Modus m = AppSettings.getInstance().getGeraeuscheModus();
        if (m == Modus.AUS || fenster == null) {
            return;
        }
        Path p = datei(o);
        if (p == null) {
            return;
        }
        MediaPlayer mp = spieler(p);
        if (mp == null) {
            return;
        }
        if (m == Modus.DAUER) {
            mp.setCycleCount(MediaPlayer.INDEFINITE);
            stoppe(DAUERND.put(fenster, mp));
            fenster.addEventHandler(WindowEvent.WINDOW_HIDDEN, e -> stoppe(DAUERND.remove(fenster)));
        } else {
            stoppe(einmal);
            einmal = mp;
            mp.setOnEndOfMedia(() -> {
                if (einmal == mp) {
                    einmal = null;
                }
                stoppe(mp);
            });
        }
        mp.play();
    }

    /** Probehören (Voreinstellungen). */
    public static void probe(Path p) {
        stoppe(einmal);
        einmal = spieler(p);
        if (einmal != null) {
            einmal.play();
        }
    }

    private static MediaPlayer spieler(Path p) {
        try {
            MediaPlayer mp = new MediaPlayer(new Media(p.toUri().toString()));
            mp.setVolume(AppSettings.getInstance().getGeraeuscheLautstaerke() / 100.0);
            mp.setOnError(() -> fehler(p, mp.getError()));
            return mp;
        } catch (RuntimeException ex) {
            fehler(p, ex.getMessage());
            return null;
        }
    }

    private static void stoppe(MediaPlayer mp) {
        if (mp != null) {
            try {
                mp.stop();
                mp.dispose();
            } catch (RuntimeException ignoriert) {
                // schon freigegeben
            }
        }
    }

    /** Alles verstummen lassen (Modus „Aus“, Programmende). */
    public static void alleStoppen() {
        stoppe(einmal);
        einmal = null;
        for (MediaPlayer mp : DAUERND.values()) {
            stoppe(mp);
        }
        DAUERND.clear();
    }

    /** Lautstärke laufender Geräusche sofort anpassen. */
    public static void lautstaerkeAnwenden() {
        double v = AppSettings.getInstance().getGeraeuscheLautstaerke() / 100.0;
        if (einmal != null) {
            einmal.setVolume(v);
        }
        DAUERND.values().forEach(mp -> mp.setVolume(v));
    }

    /** MIME-Typ für die Browser-Ansicht. */
    static String mime(Path p) {
        return switch (endung(p)) {
            case "wav" -> "audio/wav";
            case "m4a" -> "audio/mp4";
            case "aif", "aiff" -> "audio/aiff";
            default -> "audio/mpeg";
        };
    }
}
