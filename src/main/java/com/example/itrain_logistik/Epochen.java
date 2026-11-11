package com.example.itrain_logistik;

import java.util.List;

/**
 * Epochen der Modellbahn und die Wagenklassen des deutschen Personenverkehrs (Texte vom Anwender, 11.11.2026).
 * Angezeigt unter Voreinstellungen > Vorlagen; die Klassen steuern die Platzfelder der Wagen
 * (Epoche I: 1.–4. Klasse, II: 1.–3., ab III: 1. und 2.). Im Echtzeit-Kern zählen die 3. und 4. Klasse zur 2. Klasse
 * (Entscheidung des Anwenders 11.11.2026: „nur bei den Wagen“).
 */
public final class Epochen {

    public static final int MIN = 1;
    public static final int MAX = 7;

    public record Epoche(int nr, String name, String zeit, String klassen, String besonderheiten, String sonder) { }

    public static final String EINLEITUNG = "Die Einteilung der Wagenklassen im deutschen Eisenbahnverkehr änderte sich "
            + "im Laufe der Epochengeschichte drastisch. Entscheidend für den Wandel war die europäische Klassenreform "
            + "von 1956, die das System von drei (bzw. vier) auf zwei Kernklassen reduzierte.";

    public static final List<Epoche> ALLE = List.of(
            new Epoche(1, "Epoche I", "ca. 1835 – 1920", "1., 2., 3. & 4. Klasse (sowie „Salonklasse“)",
                    "Die Länderbahnen (z. B. K.P.E.V., K.Bay.Sts.B.) führten vier Klassen. Die 4. Klasse (ab 1852 in "
                            + "Preußen) bot Stehplätze oder einfache Holzbänke für Arbeiter und Personen mit Vieh/Gepäck.",
                    "Salonklasse / Fürstenwagen: Luxuswagen für Adlige und Staatsgäste außerhalb des regulären "
                            + "Tarifgefüges. Luxuszüge der CIWL mit eigenen Luxus-Kategorien. In der 4. Klasse teils "
                            + "keine Bestuhlung (Stehplatz- und Traglastenbereiche)."),
            new Epoche(2, "Epoche II", "ca. 1920 – 1945", "1., 2. & 3. Klasse (4. Kl. bis 1928)",
                    "Die Deutsche Reichsbahn schaffte 1928 die 4. Klasse ab. Im Fernverkehr gab es die 1. bis "
                            + "3. Klasse; Eil- und Personenzüge führten meist 2. und 3. Klasse.",
                    "Salonklasse / Fürstenwagen; Luxuszüge der CIWL wie der Orient-Express oder Rheingold 1928 (reine "
                            + "1. Klasse oder 1./2. Klasse). Bis 1928 4. Klasse teils ohne Bestuhlung."),
            new Epoche(3, "Epoche III", "ca. 1945 – 1970", "1. & 2. Klasse (ab 3. Juni 1956)",
                    "Klassenreform 1956: Die alte Polsterklasse (1. Kl.) entfiel, die alte 2. Kl. wurde zur neuen "
                            + "1. Klasse, und die Holzklasse (3. Kl.) wurde zur neuen 2. Klasse aufgewertet.",
                    "Luxuszüge der CIWL mit eigenen Luxus-Kategorien. Vor dem 3. Juni 1956 noch 1., 2. und 3. Klasse."),
            new Epoche(4, "Epoche IV", "ca. 1970 – 1990", "1. & 2. Klasse",
                    "Einführung des TEE (Trans-Europ-Express) und des rein erstklassigen Intercity-Netzes (1971). Erst "
                            + "ab 1979 führte der IC unter dem Motto „Jede Stunde, jede Klasse“ auch die 2. Klasse.", ""),
            new Epoche(5, "Epoche V", "ca. 1990 – 2006", "1. & 2. Klasse",
                    "Zusammenführung von DB und DR zur Deutschen Bahn AG (1994), Zeitalter des ICE. Die "
                            + "Klasseneinteilung bleibt strikt bei 1. und 2. Klasse.", ""),
            new Epoche(6, "Epoche VI", "ca. 2007 – 2025", "1. & 2. Klasse",
                    "Verfeinerung des Tarifsystems im Fernverkehr. Einführung von Zusatzleistungen in der 1. Klasse "
                            + "(Am-Platz-Service, Lounge-Zugang).", ""),
            new Epoche(7, "Epoche VII", "ab ca. 2026", "1. & 2. Klasse (Standardisierung)",
                    "Gegenwarts-Epoche: einheitlicher Zweiklassen-Standard im Fern- und Regionalverkehr (ICE, IC, RE, "
                            + "RB) sowie bei privaten EVU (z. B. Flixtrain). Nicht in der NEM 806 genormt.", ""));

    private Epochen() { }

    public static Epoche epoche(int nr) {
        return ALLE.get(Math.max(MIN, Math.min(MAX, nr)) - 1);
    }

    /** Höchste Wagenklasse der Epoche: 4 (I), 3 (II), sonst 2. */
    public static int klassen(int nr) {
        return nr <= 1 ? 4 : nr == 2 ? 3 : 2;
    }

    public static String roemisch(int nr) {
        return epoche(nr).name().substring("Epoche ".length());
    }

    /** Epoche eines Wagens: eigene, wenn „Vorgabe“ abgewählt ist, sonst die der Anlage (Voreinstellungen). */
    public static int vonWagen(javafx.collections.ObservableList<CustomField> f) {
        if (!"false".equals(LogistikFelder.getValue(f, LogistikFelder.EPOCHE_VORGABE))) {
            return AppSettings.getInstance().getEpoche();
        }
        try {
            return Math.max(MIN, Math.min(MAX, Integer.parseInt(LogistikFelder.getValue(f, LogistikFelder.EPOCHE).trim())));
        } catch (NumberFormatException e) {
            return AppSettings.getInstance().getEpoche();
        }
    }

    /** Plätze der 3. und 4. Klasse, soweit die Epoche des Wagens sie kennt (zählen im Kern zur 2. Klasse). */
    public static double untereKlassen(javafx.collections.ObservableList<CustomField> f) {
        int k = klassen(vonWagen(f));
        double s = 0;
        if (k >= 3) {
            s += Fahrzeugdaten.zahl(f, LogistikFelder.SITZPLAETZE_3);
        }
        if (k >= 4) {
            s += Fahrzeugdaten.zahl(f, LogistikFelder.SITZPLAETZE_4);
        }
        return s;
    }
}
