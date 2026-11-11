package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Linie = eine iTrain-Zugroute aus der .tcdz ({@code control-items/train-routes/route}),
 * Entscheidung des Anwenders vom 08.10.2026. {@link #getBlockNamen()} sind die Bloecke der
 * Route in ihrer Reihenfolge. Der genaue Aufbau einer Route in der .tcdz ist nicht an einer
 * echten Datei geprueft; gelesen werden alle Attributwerte unterhalb der Route, die einem
 * bekannten Blocknamen entsprechen (siehe TcdzModelBuilder).
 */
public final class Linie {

    private final String name;
    private final List<String> blockNamen = new ArrayList<>();

    public Linie(String name, List<String> blockNamen) {
        this.name = name;
        this.blockNamen.addAll(blockNamen);
    }

    public String getName() {
        return name;
    }

    public List<String> getBlockNamen() {
        return blockNamen;
    }
}
