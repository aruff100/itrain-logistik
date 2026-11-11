package com.example.itrain_logistik;

import java.io.File;
import java.util.List;

/**
 * Alles, was {@link TcdzModelBuilder} aus einer geoeffneten .tcdz gelesen
 * hat: die fuenf im Projektauftrag genannten Bereiche (clock, locomotives,
 * wagons, trains, control-state) sowie zusaetzlich blocks (fuer das
 * Bloecke-Fenster und als Nachschlagewerk fuer control-state). Rein lesend -
 * iTrain-Logistik schreibt nie in die Quelldatei zurueck.
 */
public record LogistikSnapshot(
        File sourceFile,
        ClockData clock,
        List<LocomotiveEntry> locomotives,
        List<WagonEntry> wagons,
        List<BlockEntry> blocks,
        List<TrainEntry> trains,
        List<ControlStateBlockEntry> controlState,
        List<RouteEntry> routes,
        List<StationEntry> stations
) {
    /** Ohne Bahnhöfe (ältere Aufrufer, Tests). */
    public LogistikSnapshot(File sourceFile, ClockData clock, List<LocomotiveEntry> locomotives,
            List<WagonEntry> wagons, List<BlockEntry> blocks, List<TrainEntry> trains,
            List<ControlStateBlockEntry> controlState, List<RouteEntry> routes) {
        this(sourceFile, clock, locomotives, wagons, blocks, trains, controlState, routes, List.of());
    }
}
