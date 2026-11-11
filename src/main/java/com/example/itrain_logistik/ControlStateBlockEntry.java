package com.example.itrain_logistik;

/**
 * Belegungszustand eines Blocks aus {@code <control-state><blocks>} in der
 * .tcdz: {@code name} des Blocks und - falls gerade belegt - {@code train},
 * der Name des dort stehenden Zuges (sonst {@code null}).
 */
public record ControlStateBlockEntry(String name, String train) {
}
