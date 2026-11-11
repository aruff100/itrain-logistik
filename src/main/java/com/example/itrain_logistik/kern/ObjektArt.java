package com.example.itrain_logistik.kern;

/**
 * Art eines Objekts auf der Anlage (Konzept Abschnitte 4.1 und 6.1). {@link #getSymbolSchluessel()}
 * verweist auf die vorhandenen Symbol-Schluessel der {@code SymbolRegistry} (NetzKnotenTyp bzw.
 * OrtTyp), damit eigene Symbole des Anwenders weiter gelten.
 */
public enum ObjektArt {
    STADT("Stadt", "STADT"),
    DORF("Dorf", "DORF"),
    LAENDLICH("Ländlich", "LAENDLICH"),
    BAHNHOF("Bahnhof", "BAHNHOF"),
    HALTEPUNKT("Haltepunkt", "HALTEPUNKT"),
    GUETERBAHNHOF("Güterbahnhof", "GUETERBAHNHOF"),
    ANSCHLUSS("Anschluss", "ANSCHLIESSER"),
    /**
     * Schattenbahnhof: Blöcke werden mit Gütern (Ladegleis, Güterauswahl = was bereitsteht) oder
     * Personen (Bahnsteig) belegt, ein Block auch mehrfach. Verhält sich wie die Außenwelt: stellt
     * unbegrenzt bereit und nimmt alles ab.
     */
    SCHATTENBAHNHOF("Schattenbahnhof", "SCHATTENBAHNHOF"),
    /**
     * iTrain-Bahnhof vom Typ "other" (z. B. Betriebswerk), Anwender 14.10.2026: noch ohne Bedeutung für die
     * Logistik, der Anwender trägt im Stationsdialog die eigentliche Art ein. Bis dahin halten Züge dort ohne
     * Ein-/Aussteigen und Umladen.
     */
    ANDERES("Anderes", "BETRIEBSWERK"),
    BETRIEB("Betrieb", "FABRIK"),
    LAGER("Lager", "LAGER"),
    UEBERGABE("Übergabe", "GUETERVERKEHR"),
    /**
     * Modul (Anwender 09.11.2026): hängt wie ein Bahnhof an der Anlagen-Infrastruktur, hat eigene Gleise (Blöcke)
     * und Stationen, die statt an der Infrastruktur an ihm hängen; kann im Browser auf einem anderen Rechner gezeigt
     * werden. Kein Güter- oder Personenaustausch.
     */
    MODUL("Modul", "MODUL"),
    /** Tab (Anwender 09.11.2026): eigener Reiter der Übersicht, Bahnhöfe hängen sich mit „Mit Tab verbinden…“ an. */
    REITER("Tab", "REITER"),
    /** Fester Mittelpunkt der Anlage; haelt alle Bloecke, die keinem Gleis zugeordnet sind (Strecke). */
    INFRASTRUKTUR("Anlagen-Infrastruktur", "ANLAGEN_INFRASTRUKTUR");

    private final String label;
    private final String symbolSchluessel;

    ObjektArt(String label, String symbolSchluessel) {
        this.label = label;
        this.symbolSchluessel = symbolSchluessel;
    }

    public String getLabel() {
        return label;
    }

    public String getSymbolSchluessel() {
        return symbolSchluessel;
    }

    public boolean istOrt() {
        return this == STADT || this == DORF || this == LAENDLICH;
    }

    public boolean istStation() {
        return this == BAHNHOF || this == HALTEPUNKT || this == GUETERBAHNHOF || this == ANSCHLUSS
                || this == SCHATTENBAHNHOF || this == ANDERES;
    }

    /**
     * Welche Gleisarten eine Stationsart haben darf (Anwenderentscheid 08.10.2026): Güterbahnhof
     * Ladegleise, Anschluss und Anderes beides. Bahnhof, Haltepunkt und Schattenbahnhof: beides wählbar, die
     * Ladegleise wandern beim Speichern in den angedockten Güterbahnhof ({@link Anlage#ladegleiseAuslagern},
     * Anwender 20.10.2026).
     */
    public boolean erlaubt(GleisArt gleisArt) {
        return switch (this) {
            case BAHNHOF, HALTEPUNKT, ANSCHLUSS, ANDERES -> true;
            // Schattenbahnhof: beides wählbar; Ladegleise wandern beim Speichern in den angedockten
            // Güterbahnhof (Anlage.schattenbahnhoefeUmstellen), Anwender 11.10.2026
            case SCHATTENBAHNHOF -> true;
            // Güterbahnhof: Ladegleise; Bahnsteige nur, wenn ein Ort mit Passagieren direkt an ihm hängt
            // (Frage nach Personengleisen beim Verbinden, Anwenderentscheid 11.10.2026).
            case GUETERBAHNHOF -> true;
            default -> false;
        };
    }
}
