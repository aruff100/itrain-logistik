package com.example.itrain_logistik;

import java.util.List;

/**
 * Ein in iTrain definierter Bahnhof ({@code control-items/stations/station}) mit seinen Blöcken in
 * der Reihenfolge der Datei. Grundlage für das Anheften an die Anlagen-Infrastruktur
 * ({@link com.example.itrain_logistik.kern.Anlage#itrainBahnhoefeAnheften}).
 */
public record StationEntry(String name, String typ, List<String> bloecke) {
}
