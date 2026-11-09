package com.example.itrain_logistik.kern;

import java.util.List;

/**
 * Was ein Zug transportieren kann, ermittelt aus seiner Zusammenstellung und den
 * Wagen-Logistikfeldern (Konzept Abschnitt 7): leere Wagenladungen und Sitzplaetze je Klasse
 * (Stehplaetze zaehlen zur 2. Klasse). {@link #signatur()} aendert sich, sobald sich die
 * Zusammenstellung oder ein Wagenwert aendert.
 */
public record ZugAusstattung(List<Wagenladung> wagenladungen, double sitzplaetzeErste, double sitzplaetzeZweite,
        double personenJeSekundeEin, double personenJeSekundeAus, String signatur) {

    public static final ZugAusstattung LEER = new ZugAusstattung(List.of(), 0, 0,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "");

    public boolean hatPlaetze() {
        return sitzplaetzeErste + sitzplaetzeZweite > 0;
    }
}
