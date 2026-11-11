package com.example.itrain_logistik;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * „Auf Update prüfen“ (Anwender 11.11.2026): Das Programm liegt auf GitHub; die neueste Veröffentlichung liefert die
 * GitHub-API ({@code releases/latest}: {@code tag_name}, {@code html_url}, {@code body}). Verglichen wird der Tag
 * (z. B. „v1.1“) mit {@link AppInfo#getVersion()}. Heruntergeladen wird nichts; „Zur Download-Seite“ öffnet die
 * Release-Seite im Browser. Beim Start prüft das Programm still, wenn Voreinstellungen > Allgemein „Beim Start nach
 * Updates suchen“ gesetzt ist, und meldet sich nur bei einer neuen Version.
 */
public final class UpdatePruefung {

    static final String REPO = "aruff100/itrain-logistik";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    static final String RELEASES = "https://github.com/" + REPO + "/releases";
    private static final Duration ZEIT = Duration.ofSeconds(8);

    private UpdatePruefung() {
    }

    /** Ergebnis: ok = Antwort gelesen; neuer = Version auf GitHub ist neuer. */
    public record Ergebnis(boolean ok, boolean neuer, String aktuell, String neueste, String seite, String notizen,
            String fehler) {
    }

    /** Prüft im Hintergrund und liefert das Ergebnis auf dem JavaFX-Thread. */
    public static void pruefe(Consumer<Ergebnis> fertig) {
        Thread.ofVirtual().start(() -> {
            Ergebnis e = jetzt();
            Platform.runLater(() -> fertig.accept(e));
        });
    }

    /** Menü Hilfe > „Auf Update prüfen“: zeigt immer ein Ergebnis. */
    public static void vomMenue(Stage owner) {
        pruefe(e -> zeige(owner, e, true));
    }

    /** Beim Start: nur melden, wenn es eine neue Version gibt. */
    public static void beimStart(Stage owner) {
        if (AppSettings.getInstance().getAutoUpdateCheckEnabled()) {
            pruefe(e -> zeige(owner, e, false));
        }
    }

    private static void zeige(Stage owner, Ergebnis e, boolean immer) {
        if (!e.neuer() && !immer) {
            return;
        }
        Alert a;
        if (!e.ok()) {
            a = new Alert(Alert.AlertType.WARNING, "Die Prüfung war nicht möglich: " + e.fehler()
                    + "\nDie Veröffentlichungen stehen unter " + RELEASES);
            a.setHeaderText("Auf Update prüfen");
        } else if (e.neuer()) {
            ButtonType seite = new ButtonType("Zur Download-Seite", ButtonBar.ButtonData.OK_DONE);
            ButtonType spaeter = new ButtonType("Später", ButtonBar.ButtonData.CANCEL_CLOSE);
            a = new Alert(Alert.AlertType.INFORMATION, "Installiert: " + e.aktuell() + "\nNeu: " + e.neueste()
                    + (e.notizen() == null || e.notizen().isBlank() ? "" : "\n\n" + kurz(e.notizen())),
                    spaeter, seite);
            a.setHeaderText("Eine neue Version von iTrain-Logistik ist verfügbar");
            a.initOwner(owner);
            a.setTitle("Auf Update prüfen");
            if (a.showAndWait().orElse(spaeter) == seite) {
                oeffne(e.seite());
            }
            return;
        } else {
            a = new Alert(Alert.AlertType.INFORMATION, "Installiert ist " + e.aktuell() + ", die neueste "
                    + "Veröffentlichung ist " + e.neueste() + ".");
            a.setHeaderText("iTrain-Logistik ist auf dem neuesten Stand");
        }
        a.initOwner(owner);
        a.setTitle("Auf Update prüfen");
        a.showAndWait();
    }

    private static Ergebnis jetzt() {
        String aktuell = AppInfo.getVersion();
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(ZEIT)
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest r = HttpRequest.newBuilder(URI.create(API)).timeout(ZEIT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "iTrain-Logistik/" + aktuell).GET().build();
            HttpResponse<String> antwort = c.send(r, HttpResponse.BodyHandlers.ofString());
            if (antwort.statusCode() == 404) {
                return new Ergebnis(false, false, aktuell, null, RELEASES, null,
                        "noch keine öffentliche Veröffentlichung gefunden");
            }
            if (antwort.statusCode() != 200) {
                return new Ergebnis(false, false, aktuell, null, RELEASES, null, "HTTP " + antwort.statusCode());
            }
            String tag = feld(antwort.body(), "tag_name");
            String seite = feld(antwort.body(), "html_url");
            if (tag == null) {
                return new Ergebnis(false, false, aktuell, null, RELEASES, null, "unbekanntes Antwortformat");
            }
            return new Ergebnis(true, vergleiche(tag, aktuell) > 0, aktuell, tag, seite != null ? seite : RELEASES,
                    feld(antwort.body(), "body"), null);
        } catch (Exception ex) {
            return new Ergebnis(false, false, aktuell, null, RELEASES, null,
                    ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    static void oeffne(String url) {
        Thread.ofVirtual().start(() -> {
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
                // Adresse steht in der Meldung
            }
        });
    }

    private static String kurz(String s) {
        return s.length() > 600 ? s.substring(0, 600) + " …" : s;
    }

    /** Einfacher Wert eines Text-Felds aus flachem JSON (erstes Vorkommen). */
    static String feld(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(1).replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Vergleicht „v1.12“ mit „1.9“ nach Zahlenteilen. */
    static int vergleiche(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("\\.");
        String[] y = b.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? zahl(x[i]) : 0;
            int q = i < y.length ? zahl(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    private static int zahl(String s) {
        Matcher m = Pattern.compile("\\d+").matcher(s);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }
}
