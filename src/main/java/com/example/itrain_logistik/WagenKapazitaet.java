package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Ladeklasse;

/**
 * Anzeigetext fuer die "Kapazität"-Spalte der Zug-Zusammenstellung ({@link TrainsWindow}).
 * Personenwagen: Summe aus Sitzplaetzen 1. und 2. Klasse und Stehplaetzen ("20 Plätze").
 * Gueterwagen: Kapazitaet in Einheiten mit Ladeklasse ("15 Schüttgut"). Die Werte, mit denen
 * der Echtzeit-Kern rechnet, ermittelt {@link Fahrzeugdaten}.
 */
final class WagenKapazitaet {

    private WagenKapazitaet() {
    }

    static String anzeigeText(WagonEntry wagen) {
        WagenArt art = LogistikFelder.parseEnum(
                LogistikFelder.getValue(wagen.getCustomFields(), LogistikFelder.WAGENART), WagenArt.values());
        WagenArt.Feldsatz feldsatz = art == null ? null : art.getFeldsatz();
        if (feldsatz == WagenArt.Feldsatz.BEIDE) {
            String[] t = beides(wagen.getCustomFields());
            if (t[0].isEmpty()) {
                return "";
            }
            String[] z = t[0].split(" \\| ");
            String[] e = t[1].split(" \\| ");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < z.length; i++) {
                sb.append(i > 0 ? " | " : "").append(z[i]).append(' ').append(i < e.length ? e[i] : "");
            }
            return sb.toString();
        }
        if (feldsatz == WagenArt.Feldsatz.PERSONEN) {
            double summe = zahl(wagen, LogistikFelder.SITZPLAETZE_1) + zahl(wagen, LogistikFelder.SITZPLAETZE_2)
                    + zahl(wagen, LogistikFelder.STEHPLAETZE);
            return summe > 0 ? formatiert(summe) + " Plätze" : "";
        }
        if (feldsatz == WagenArt.Feldsatz.GUETER) {
            double wert = zahl(wagen, LogistikFelder.KAPAZITAET);
            Ladeklasse klasse = LogistikFelder.parseEnum(
                    LogistikFelder.getValue(wagen.getCustomFields(), LogistikFelder.LADEKLASSE), Ladeklasse.values());
            String text = wert > 0 ? formatiert(wert) : "";
            if (klasse != null) {
                text = (text + " " + klasse.getLabel()).trim();
            }
            return text;
        }
        return "";
    }

    /**
     * Kapazität und Einheit getrennt für die Spalten "Kapazität" und "Einheit" im Fenster Züge (Anwender 20.10.2026):
     * Güterwagen {"5000", "Liter (Flüssiggut)"}, Personenwagen {"20", "Plätze"}.
     */
    static String[] zahlUndEinheit(WagonEntry wagen) {
        WagenArt art = LogistikFelder.parseEnum(
                LogistikFelder.getValue(wagen.getCustomFields(), LogistikFelder.WAGENART), WagenArt.values());
        WagenArt.Feldsatz feldsatz = art == null ? null : art.getFeldsatz();
        if (feldsatz == WagenArt.Feldsatz.BEIDE) {
            return beides(wagen.getCustomFields());
        }
        if (feldsatz == WagenArt.Feldsatz.PERSONEN) {
            double summe = zahl(wagen, LogistikFelder.SITZPLAETZE_1) + zahl(wagen, LogistikFelder.SITZPLAETZE_2)
                    + zahl(wagen, LogistikFelder.STEHPLAETZE);
            return summe > 0 ? new String[]{formatiert(summe), "Plätze"} : new String[]{"", ""};
        }
        if (feldsatz == WagenArt.Feldsatz.GUETER) {
            double wert = zahl(wagen, LogistikFelder.KAPAZITAET);
            Ladeklasse klasse = LogistikFelder.parseEnum(
                    LogistikFelder.getValue(wagen.getCustomFields(), LogistikFelder.LADEKLASSE), Ladeklasse.values());
            String einheit = com.example.itrain_logistik.kern.Einheit.wagen(LogistikFelder.getValue(
                    wagen.getCustomFields(), LogistikFelder.KAPAZITAET_EINHEIT), klasse)
                    + (klasse != null ? " (" + klasse.getLabel() + ")" : "");
            return new String[]{wert > 0 ? formatiert(wert) : "", einheit};
        }
        return new String[]{"", ""};
    }

    /**
     * Kapazität und Einheit eines Triebzugs (Anwender 22.10.2026): Personen und/oder Güter, bei beidem durch
     * " | " getrennt ({"120 | 30", "Plätze | Tonnen (Schüttgut)"}); keine Angaben = leer.
     */
    static String[] zahlUndEinheitTriebzug(javafx.collections.ObservableList<CustomField> f) {
        if (!Fahrzeugdaten.istTriebzug(f)) {
            return new String[]{"", ""};
        }
        return zusammen(f, "true".equals(LogistikFelder.getValue(f, LogistikFelder.TRIEBZUG_PERSONEN)),
                "true".equals(LogistikFelder.getValue(f, LogistikFelder.TRIEBZUG_GUETER)));
    }

    /** Personen-/Gepäckwagen: Plätze und Laderaum wie ein Triebzug mit beidem. */
    static String[] beides(javafx.collections.ObservableList<CustomField> f) {
        return zusammen(f, true, true);
    }

    private static String[] zusammen(javafx.collections.ObservableList<CustomField> f, boolean mitPersonen,
            boolean mitGuetern) {
        java.util.List<String> zahlen = new java.util.ArrayList<>();
        java.util.List<String> einheiten = new java.util.ArrayList<>();
        if (mitPersonen) {
            double summe = Fahrzeugdaten.zahl(f, LogistikFelder.SITZPLAETZE_1) + Fahrzeugdaten.zahl(f, LogistikFelder.SITZPLAETZE_2)
                    + Fahrzeugdaten.zahl(f, LogistikFelder.STEHPLAETZE);
            if (summe > 0) {
                zahlen.add(formatiert(summe));
                einheiten.add("Plätze");
            }
        }
        if (mitGuetern) {
            double wert = Fahrzeugdaten.zahl(f, LogistikFelder.KAPAZITAET);
            Ladeklasse klasse = LogistikFelder.parseEnum(LogistikFelder.getValue(f, LogistikFelder.LADEKLASSE),
                    Ladeklasse.values());
            if (wert > 0) {
                zahlen.add(formatiert(wert));
                einheiten.add(com.example.itrain_logistik.kern.Einheit.wagen(
                        LogistikFelder.getValue(f, LogistikFelder.KAPAZITAET_EINHEIT), klasse)
                        + (klasse != null ? " (" + klasse.getLabel() + ")" : ""));
            }
        }
        return new String[]{String.join(" | ", zahlen), String.join(" | ", einheiten)};
    }

    private static double zahl(WagonEntry wagen, String feld) {
        String text = LogistikFelder.getValue(wagen.getCustomFields(), feld);
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(text.trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** Formatiert eine Zahl ohne unnoetige Nachkommastelle ("20" statt "20.0"). */
    static String formatiert(double wert) {
        if (wert == Math.rint(wert) && !Double.isInfinite(wert)) {
            return String.valueOf((long) wert);
        }
        return String.valueOf(Math.round(wert * 10) / 10.0);
    }
}
