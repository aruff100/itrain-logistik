package com.example.itrain_logistik;

/**
 * Die Wagenart bestimmt, welcher Feldsatz in {@link WagonLogistikPane}
 * angezeigt wird (Gueter- oder Personenwagen-Felder) - siehe Projektauftrag
 * "Den Wagentyp kannst Du aus dem Feld type= ablesen. Nur wenn es leer ist
 * traegt der Anwender etwas ein. Siehe Bilder fuer die moeglichen Typen. Dazu
 * Tiertransport, Zirkus." Die konkreten Werte sind den beiden vom Anwender
 * mitgelieferten Bildschirmfotos der iTrain-Wagentyp-Legende nachgebildet:
 * eine Personengruppe (Unbekannt/Personen/Speise/Salon/Panorama/Schlaf/
 * Gepaeck/Post) und die Gueterwagen-Gattungsbuchstaben E-Z nach dem
 * UIC-Gattungsbuchstaben-System, ergaenzt um die beiden zusaetzlich
 * gewuenschten Sonderarten Tiertransport/Zirkus.
 * <p>
 * Die Ableitung aus dem iTrain-Attribut {@code type} (siehe
 * {@link TcdzModelBuilder}) ist nur fuer die an echten .tcdz-Dateien
 * bestaetigten Werte "coach" (-&gt; {@link #PERSONEN}) sowie die
 * Gueter-Einzelbuchstaben "e"/"g"/"i"/"t"/"u"/"z" gesichert; die uebrigen
 * Gattungsbuchstaben (f/h/k/l/o/r/s) sind nach dem allgemeinen
 * UIC-Schema plausibel, an keiner der beiden mitgelieferten Beispieldateien
 * jedoch tatsaechlich beobachtet worden - siehe Klassenkommentar in
 * {@link TcdzModelBuilder}.
 */
public enum WagenArt {
    UNBEKANNT("Unbekannt", Feldsatz.KEIN),
    PERSONEN("Personen", Feldsatz.PERSONEN),
    SPEISE("Speisewagen", Feldsatz.PERSONEN),
    SALON("Salonwagen", Feldsatz.PERSONEN),
    PANORAMA("Panoramawagen", Feldsatz.PERSONEN),
    SCHLAF("Schlafwagen", Feldsatz.PERSONEN),
    GEPAECK("Gepäckwagen", Feldsatz.GUETER),
    /** Kombinierter Personen-/Gepäckwagen (Anwender 22.10.2026): Plätze und Laderaum, wie ein Triebzug mit beidem. */
    PERSONEN_GEPAECK("Personen-/Gepäckwagen", Feldsatz.BEIDE),
    POST("Postwagen", Feldsatz.GUETER),
    OFFEN("Offener Wagen (Regelbauart)", Feldsatz.GUETER),
    OFFEN_SONDER("Offener Wagen (Sonderbauart)", Feldsatz.GUETER),
    GEDECKT("Gedeckter Wagen (Regelbauart)", Feldsatz.GUETER),
    GEDECKT_SONDER("Gedeckter Wagen (Sonderbauart)", Feldsatz.GUETER),
    KUEHL("Kühlwagen", Feldsatz.GUETER),
    FLACH_EINZELACHS("Flachwagen, Einzelachsen (Regelbauart)", Feldsatz.GUETER),
    FLACH_EINZELACHS_SONDER("Flachwagen, Einzelachsen (Sonderbauart)", Feldsatz.GUETER),
    OFFEN_MEHRZWECK("Offener Wagen für Mehrzweckverwendung", Feldsatz.GUETER),
    FLACH_DREHGESTELL("Flachwagen, Drehgestell (Regelbauart)", Feldsatz.GUETER),
    FLACH_DREHGESTELL_SONDER("Flachwagen, Drehgestell (Sonderbauart)", Feldsatz.GUETER),
    OEFFNUNGSFAEHIGES_DACH("Wagen mit öffnungsfähigem Dach", Feldsatz.GUETER),
    SONDER("Sonderwagen", Feldsatz.GUETER),
    KESSEL("Kesselwagen", Feldsatz.GUETER),
    TIERTRANSPORT("Tiertransport", Feldsatz.GUETER),
    ZIRKUS("Zirkus", Feldsatz.GUETER);

    /** Welcher Feldsatz in {@link WagonLogistikPane} fuer diese Wagenart angezeigt wird. */
    public enum Feldsatz {
        GUETER, PERSONEN, BEIDE, KEIN
    }

    private final String label;
    private final Feldsatz feldsatz;

    WagenArt(String label, Feldsatz feldsatz) {
        this.label = label;
        this.feldsatz = feldsatz;
    }

    public String getLabel() {
        return label;
    }

    public Feldsatz getFeldsatz() {
        return feldsatz;
    }
}
