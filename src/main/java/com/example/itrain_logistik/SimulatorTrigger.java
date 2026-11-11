package com.example.itrain_logistik;

/**
 * Ein einzelnes, vom externen "iTrain-Logistik-Simulator" protokolliertes
 * Ereignis - entspricht einem {@code <trigger>}-Element aus dessen
 * "&lt;basisname&gt;.simulator-trigger.xml". "Ich druecke auf einen Block,
 * rechts klick welcher Zug, und dann ist es so als ob ein Trigger von
 * iTrain eingegangen waere" (Projektauftrag des Simulators).
 * <p>
 * {@code richtung} (optional, Attribut {@code richtung=}) ist die Fahrtrichtung des Zugs im
 * Block. Sie wird mit den am Gleis eingetragenen Richtungen verglichen (ohne Gross-/
 * Kleinschreibung). Fehlt sie, gilt der Bahnsteig ohne Richtungstrennung (Entscheidung des
 * Anwenders vom 08.10.2026).
 * <p>
 * {@code quelle} (optional, Attribut {@code quelle=}): "itrain", wenn das Ereignis aus iTrain stammt
 * (XTC, siehe {@link XtcVerbindung}), sonst leer.
 */
public record SimulatorTrigger(String zeit, String block, String zug, SimulatorAktion aktion, String richtung,
        String quelle) {

    /** Quelle eines Ereignisses aus iTrain (XTC). */
    public static final String QUELLE_ITRAIN = "itrain";

    /** Ohne Quelle (Simulator, aeltere Protokolle). */
    public SimulatorTrigger(String zeit, String block, String zug, SimulatorAktion aktion, String richtung) {
        this(zeit, block, zug, aktion, richtung, "");
    }

    /** Ohne Fahrtrichtung (aeltere Protokolle). */
    public SimulatorTrigger(String zeit, String block, String zug, SimulatorAktion aktion) {
        this(zeit, block, zug, aktion, "");
    }

    public boolean ausItrain() {
        return QUELLE_ITRAIN.equals(quelle);
    }
}
