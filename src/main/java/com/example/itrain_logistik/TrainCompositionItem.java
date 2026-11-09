package com.example.itrain_logistik;

/**
 * Ein Eintrag in der Zusammenstellung eines Zuges (Kindelement von
 * {@code <composition>} in der .tcdz): entweder eine Lokomotive
 * ({@code kind == LOCOMOTIVE}, mit {@code main="true"} fuer die
 * fuehrende Lok) oder ein Wagen ({@code kind == WAGON}), jeweils per
 * Name auf einen Eintrag in {@link LogistikSnapshot#locomotives()} bzw.
 * {@link LogistikSnapshot#wagons()} verweisend.
 */
public final class TrainCompositionItem {

    public enum Kind { LOCOMOTIVE, WAGON }

    private final Kind kind;
    private final String name;
    private final boolean main;
    private final String direction;

    public TrainCompositionItem(Kind kind, String name, boolean main, String direction) {
        this.kind = kind;
        this.name = name;
        this.main = main;
        this.direction = direction;
    }

    public Kind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public boolean isMain() {
        return main;
    }

    public String getDirection() {
        return direction;
    }
}
