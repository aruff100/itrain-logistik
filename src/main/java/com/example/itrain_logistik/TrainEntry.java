package com.example.itrain_logistik;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Ein Zug: {@code name} aus der .tcdz, dazu die dort hinterlegte
 * {@code composition} als unveraenderliche Referenz ({@link #tcdzComposition})
 * und eine eigene, bearbeitbare Zusammenstellung ({@link #composition()}) -
 * siehe Projektauftrag: "composition ... mit den eigenen Daten, da diese
 * erweitert werden". Die eigene Liste greift beim Aufbau auf
 * {@link LocomotiveEntry}/{@link WagonEntry} zu und startet als Kopie der
 * .tcdz-Zusammenstellung, kann danach aber frei ergaenzt/umgestellt werden.
 */
public final class TrainEntry {

    private final String name;
    private final List<TrainCompositionItem> tcdzComposition;
    private final ObservableList<TrainCompositionItem> composition = FXCollections.observableArrayList();

    public TrainEntry(String name, List<TrainCompositionItem> tcdzComposition) {
        this.name = name;
        this.tcdzComposition = List.copyOf(tcdzComposition);
        this.composition.addAll(tcdzComposition);
    }

    public String getName() {
        return name;
    }

    /** Die in der .tcdz gespeicherte Zusammenstellung, unveraendert - als Vergleichsbasis. */
    public List<TrainCompositionItem> getTcdzComposition() {
        return tcdzComposition;
    }

    /** Die eigene, erweiterbare Zusammenstellung (siehe Klassenkommentar). */
    public ObservableList<TrainCompositionItem> getComposition() {
        return composition;
    }
}
