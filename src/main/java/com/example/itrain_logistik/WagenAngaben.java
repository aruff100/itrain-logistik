package com.example.itrain_logistik;

import java.util.ArrayList;
import java.util.List;

/**
 * "Angaben kopieren" / "Einfügen" im Wagenfenster (Anwender 14.10.2026): alle Logistik-Angaben eines Wagens als
 * Text in der Zwischenablage, eine Angabe je Zeile ({@code Name=Wert}), mit Kennzeile davor.
 */
final class WagenAngaben {

    static final String KENNUNG = "iTrain-Logistik Wagen-Angaben";

    private WagenAngaben() {
    }

    static String alsText(List<CustomField> felder) {
        StringBuilder sb = new StringBuilder(KENNUNG).append('\n');
        for (CustomField f : felder) {
            if (f.getName() == null || f.getName().isBlank()) {
                continue;
            }
            sb.append(f.getName().replace("\n", " ").replace("=", " ")).append('=')
                    .append(f.getValue() == null ? "" : f.getValue().replace("\n", " ")).append('\n');
        }
        return sb.toString();
    }

    /** Liest Angaben aus dem Text; null, wenn es keine Wagen-Angaben sind. */
    static List<CustomField> ausText(String text) {
        if (text == null || !text.startsWith(KENNUNG)) {
            return null;
        }
        List<CustomField> l = new ArrayList<>();
        for (String zeile : text.split("\\R")) {
            int i = zeile.indexOf('=');
            if (i > 0) {
                l.add(new CustomField(zeile.substring(0, i), zeile.substring(i + 1)));
            }
        }
        return l;
    }
}
