package com.example.itrain_logistik;

import java.util.UUID;

/**
 * Frei platzierbares Text-Element auf der Ebene-1-Uebersicht
 * (Projektauftrag: "Im Hauptfenster muessen angeordnete Symbole mit der
 * Maus verschiebbar sein. Zusaetzlich koennen im Hauptfenster 'Texte'
 * angeordnet werden. Einzeilig, Mehrzeilig. Verschiebbar, mit oder ohne
 * Rahmen. Jeder Text kann in einer 'Formatierung' angepasst werden.", siehe
 * beigefuegtes Formatierung-Dialog-Bild). Reine Anzeige/Dekoration - hat
 * anders als {@link NetzKnoten} keinerlei Wirkung auf die Simulation.
 */
public final class NetzText {

    /** Siehe Formatierung-Dialog: Ausrichtung horizontal. */
    public enum Horizontal {
        LINKSBUENDIG("Linksbündig"), ZENTRIERT("Zentriert"), RECHTSBUENDIG("Rechtsbündig");

        private final String label;

        Horizontal(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Siehe Formatierung-Dialog: Ausrichtung vertikal. */
    public enum Vertikal {
        OBEN("Oben"), ZENTRIERT("Zentriert"), UNTEN("Unten");

        private final String label;

        Vertikal(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Der Screenshot zeigt nur das Dropdown "Textrichtung: Horizontal" ohne
     * weitere sichtbare Optionen - die uebrigen Werte sind mangels
     * genauerer Vorgabe angenommen (senkrechter Text, wie in anderen
     * Zeichenprogrammen ueblich) und sollten mit dem Anwender abgeglichen
     * werden.
     */
    public enum Textrichtung {
        HORIZONTAL("Horizontal"), SENKRECHT_AUFWAERTS("Senkrecht (aufwärts)"),
        SENKRECHT_ABWAERTS("Senkrecht (abwärts)");

        private final String label;

        Textrichtung(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private String id;
    private double x;
    private double y;
    private String text = "";
    private String schriftart = "Arial";
    private double schriftgroesse = 12;
    private boolean fett;
    private boolean kursiv;
    private Horizontal horizontal = Horizontal.LINKSBUENDIG;
    private Vertikal vertikal = Vertikal.OBEN;
    private Textrichtung textrichtung = Textrichtung.HORIZONTAL;
    /** Hex-Farbcode wie "#FFFFFF" - Vorgabe laut Screenshot: Weiss. */
    private String farbe = "#FFFFFF";
    private boolean rahmen;

    public NetzText() {
        this.id = UUID.randomUUID().toString();
    }

    public NetzText(String id, double x, double y) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.x = x;
        this.y = y;
    }

    public NetzText kopie() {
        NetzText k = new NetzText(id, x, y);
        k.text = text;
        k.schriftart = schriftart;
        k.schriftgroesse = schriftgroesse;
        k.fett = fett;
        k.kursiv = kursiv;
        k.horizontal = horizontal;
        k.vertikal = vertikal;
        k.textrichtung = textrichtung;
        k.farbe = farbe;
        k.rahmen = rahmen;
        return k;
    }

    public String getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getSchriftart() {
        return schriftart;
    }

    public void setSchriftart(String schriftart) {
        this.schriftart = (schriftart == null || schriftart.isBlank()) ? "Arial" : schriftart;
    }

    public double getSchriftgroesse() {
        return schriftgroesse;
    }

    public void setSchriftgroesse(double schriftgroesse) {
        this.schriftgroesse = schriftgroesse > 0 ? schriftgroesse : 12;
    }

    public boolean isFett() {
        return fett;
    }

    public void setFett(boolean fett) {
        this.fett = fett;
    }

    public boolean isKursiv() {
        return kursiv;
    }

    public void setKursiv(boolean kursiv) {
        this.kursiv = kursiv;
    }

    public Horizontal getHorizontal() {
        return horizontal;
    }

    public void setHorizontal(Horizontal horizontal) {
        this.horizontal = horizontal != null ? horizontal : Horizontal.LINKSBUENDIG;
    }

    public Vertikal getVertikal() {
        return vertikal;
    }

    public void setVertikal(Vertikal vertikal) {
        this.vertikal = vertikal != null ? vertikal : Vertikal.OBEN;
    }

    public Textrichtung getTextrichtung() {
        return textrichtung;
    }

    public void setTextrichtung(Textrichtung textrichtung) {
        this.textrichtung = textrichtung != null ? textrichtung : Textrichtung.HORIZONTAL;
    }

    public String getFarbe() {
        return farbe;
    }

    public void setFarbe(String farbe) {
        this.farbe = (farbe == null || farbe.isBlank()) ? "#FFFFFF" : farbe;
    }

    public boolean isRahmen() {
        return rahmen;
    }

    public void setRahmen(boolean rahmen) {
        this.rahmen = rahmen;
    }
}
