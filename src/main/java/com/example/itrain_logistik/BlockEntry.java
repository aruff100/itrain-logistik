package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** Ein Block (Gleisabschnitt): {@code name} und {@code type} aus der .tcdz. */
public final class BlockEntry {

    private final String name;
    private final String type;
    private final ObservableList<CustomField> customFields = FXCollections.observableArrayList();

    public BlockEntry(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public ObservableList<CustomField> getCustomFields() {
        return customFields;
    }

    /** Ein Weg aus dem Block zu einem Nachbarblock: Seite „prev“/„next“, die Weichen dazwischen, der Zielblock. */
    public record Weg(String seite, java.util.List<Weiche> weichen, String ziel, boolean bevorzugt) {
        /** Länge der Weichenstraße in cm; Weichen ohne Längenangabe zählen mit {@code ersatz}. */
        public double laenge(double ersatz) {
            double l = 0;
            for (Weiche w : weichen) {
                l += w.laenge() > 0 ? w.laenge() : ersatz;
            }
            return l;
        }
    }

    /** Eine Weiche auf einem Weg, Länge in cm (-1 = in iTrain nicht angegeben). */
    public record Weiche(String name, double laenge) {
    }

    private String wege = "";

    /**
     * Wege zu den Nachbarblöcken, wie sie die .tcdz unter {@code <prev>/<next>/<side-block>} beschreibt
     * (Anwender 10.11.2026, Routen-Anzeiger). Als Text gespeichert: Wege durch „ ;; “ getrennt, je Weg
     * „seite | *?Ziel | Weiche~Länge | …“ (* = in iTrain bevorzugt).
     */
    public String getWege() {
        return wege;
    }

    public void setWege(String wege) {
        this.wege = wege == null ? "" : wege;
        wegListe = null;
    }

    private java.util.List<Weg> wegListe;

    public java.util.List<Weg> wege() {
        if (wegListe == null) {
            java.util.List<Weg> l = new java.util.ArrayList<>();
            for (String w : wege.split(" ;; ")) {
                String[] t = w.split(" \\| ");
                if (t.length < 2) {
                    continue;
                }
                java.util.List<Weiche> ws = new java.util.ArrayList<>();
                for (int i = 2; i < t.length; i++) {
                    int k = t[i].lastIndexOf('~');
                    double len = -1;
                    try {
                        len = k >= 0 ? Double.parseDouble(t[i].substring(k + 1)) : -1;
                    } catch (NumberFormatException ex) {
                        // ohne Länge
                    }
                    ws.add(new Weiche(k >= 0 ? t[i].substring(0, k) : t[i], len));
                }
                boolean bev = t[1].startsWith("*");
                l.add(new Weg(t[0], ws, bev ? t[1].substring(1) : t[1], bev));
            }
            wegListe = l;
        }
        return wegListe;
    }
}
