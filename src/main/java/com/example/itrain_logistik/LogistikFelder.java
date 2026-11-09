package com.example.itrain_logistik;

import javafx.collections.ObservableList;

/**
 * Feste Feldnamen fuer die strukturierten Logistik-Zusatzdaten von Wagen,
 * Lokomotiven und Bloecken (siehe {@link WagonLogistikPane},
 * {@link LocomotiveLogistikPane}, {@link BlockLogistikPane}) sowie kleine
 * Hilfsmethoden, um sie ueber die bereits vorhandene, generische
 * {@link CustomField}-Liste (und damit ohne jede Aenderung an
 * {@link LogistikDocument}s Lade-/Speicherlogik) zu lesen/schreiben. Jedes
 * strukturierte Feld ist technisch weiterhin ein ganz normales CustomField
 * mit festem Namen - nur die Bedienoberflaeche ist jetzt zweckgebunden statt
 * frei (Dropdown/Kontrollkaestchen statt Freitext).
 */
public final class LogistikFelder {

    public static final String WAGENART = "Wagenart";
    public static final String KAPAZITAET = "Kapazität";
    /** Einheit der Kapazität (z. B. Stück statt Gebinde), nur Anzeige; leer = Standard der Ladeklasse. */
    public static final String KAPAZITAET_EINHEIT = "Kapazität Einheit";
    /** Personenwagen kann Post mitnehmen ("true"), Anwender 27.10.2026. */
    public static final String KANN_POST = "Kann Post mitnehmen";
    /** Personenwagen kann Expressgut mitnehmen ("true"). */
    public static final String KANN_EXPRESSGUT = "Kann Expressgut mitnehmen";
    public static final String VORBILD_GEWICHT = "Vorbild-Gewicht/Ladegewicht";
    public static final String VORBILD_VOLUMEN = "Vorbild-Volumen";
    public static final String LADEGUT = "Ladegut";
    public static final String BEHAELTER = "Behälter";
    public static final String LADUNGSZUSTAND = "Ladungszustand";
    public static final String WAGENBAUART = "Wagenbauart";
    public static final String ZEIT_ENTLADEN = "Zeit für Entladen";
    public static final String ZEIT_BELADEN = "Zeit für Beladen";
    public static final String SITZPLAETZE_1 = "Sitzplätze 1. Klasse";
    public static final String SITZPLAETZE_2 = "Sitzplätze 2. Klasse";
    public static final String SITZPLAETZE_3 = "Sitzplätze 3. Klasse";
    public static final String SITZPLAETZE_4 = "Sitzplätze 4. Klasse";
    public static final String STEHPLAETZE = "Stehplätze";
    public static final String ANTRIEBSART = "Antriebsart";
    public static final String VERBRAUCH_100KM = "Verbrauch auf 100km (Vorbild)";
    public static final String TANKINHALT_LITER = "Tankinhalt Liter";
    public static final String WASSERVORRAT_M3 = "Wasservorrat Kubikmeter";
    public static final String KOHLEVORRAT_T = "Kohlevorrat Tonnen";
    public static final String KOHLEVERBRAUCH_KG_KM = "Kohleverbrauch kg/km";
    public static final String WASSERVERBRAUCH_L_KM = "Wasserverbrauch Liter/km";
    public static final String MASSSTAB = "Maßstab (1:x)";
    public static final String MODELLBAHN_FAKTOR = "Modellbahn-Faktor";
    /** Ladeklasse eines Gueterwagens fuer den Echtzeit-Kern ({@code kern.Ladeklasse}, Enum-Name). */
    public static final String LADEKLASSE = "Ladeklasse";
    /** Zahl der getrennt beladbaren Wagenladungen (+Cargo 2.4), Voreinstellung 1. */
    public static final String WAGENLADUNGEN = "Wagenladungen";
    /** Aktiv geschaltete Gueter eines Gueterwagens (Gut-Ids, durch Komma getrennt); leer = nach Ladeklasse. */
    public static final String LADEGUETER = "Ladegüter";
    /** Triebzug/Triebwagen: Lok, die selbst befördert (Anwender 22.10.2026), "true"/"false". */
    public static final String TRIEBZUG = "Triebzug";
    /** Triebzug befördert Personen ("true"): Sitzplätze, Stehplätze, Zeiten wie ein Personenwagen. */
    public static final String TRIEBZUG_PERSONEN = "Triebzug Personen";
    /** Triebzug befördert Güter ("true"): Ladeklasse, Kapazität usw. wie ein Güterwagen. */
    public static final String TRIEBZUG_GUETER = "Triebzug Güter";

    /** Alle oben benannten Feldnamen - fuer {@link CustomFieldsPane}, um sie dort nicht doppelt anzuzeigen. */
    public static final java.util.Set<String> STRUKTURIERTE_WAGEN_FELDER = java.util.Set.of(
            WAGENART, KAPAZITAET, VORBILD_GEWICHT, VORBILD_VOLUMEN, LADEGUT, BEHAELTER, LADUNGSZUSTAND,
            WAGENBAUART, ZEIT_ENTLADEN, ZEIT_BELADEN, SITZPLAETZE_1, SITZPLAETZE_2, SITZPLAETZE_3,
            SITZPLAETZE_4, STEHPLAETZE, LADEKLASSE, WAGENLADUNGEN, LADEGUETER, KAPAZITAET_EINHEIT, KANN_POST, KANN_EXPRESSGUT);
    public static final java.util.Set<String> STRUKTURIERTE_LOK_FELDER = java.util.Set.of(
            ANTRIEBSART, VERBRAUCH_100KM, TANKINHALT_LITER, WASSERVORRAT_M3, KOHLEVORRAT_T,
            KOHLEVERBRAUCH_KG_KM, WASSERVERBRAUCH_L_KM, MASSSTAB, MODELLBAHN_FAKTOR);
    public static final java.util.Set<String> STRUKTURIERTE_BLOCK_FELDER = java.util.Set.of();

    private LogistikFelder() {
    }

    public static CustomField find(ObservableList<CustomField> fields, String name) {
        for (CustomField f : fields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public static String getValue(ObservableList<CustomField> fields, String name) {
        CustomField f = find(fields, name);
        return f != null ? f.getValue() : "";
    }

    public static void setValue(ObservableList<CustomField> fields, String name, String value) {
        CustomField f = find(fields, name);
        if (f == null) {
            fields.add(new CustomField(name, value == null ? "" : value));
        } else {
            f.setValue(value == null ? "" : value);
        }
    }

    /** Sucht in {@code values} das Element, dessen Enum-Name dem gespeicherten Text entspricht - sonst null. */
    public static <E extends Enum<E>> E parseEnum(String stored, E[] values) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        for (E v : values) {
            if (v.name().equals(stored)) {
                return v;
            }
        }
        return null;
    }
}
