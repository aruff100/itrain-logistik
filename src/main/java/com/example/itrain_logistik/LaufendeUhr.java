package com.example.itrain_logistik;

import java.time.LocalTime;

/**
 * "Die Uhr im Ribbon und die Uhr im Fenster zeigen unterschiedliche
 * Uhrzeiten an. Ca. 15 Min." (Projektauftrag) - Ursache: bisher hielt jede
 * {@link DigitalClockView}-Instanz (Ribbon UND das eigenstaendige
 * {@link UhrWindow}) ihren eigenen Zeitstand und liess ihn erst ab dem
 * Moment ihres jeweiligen {@code setClock()}-Aufrufs unabhaengig
 * weiterlaufen. Wurde das Uhr-Fenster erst einige reale Minuten nach dem
 * letzten Laden/Abgleichen geoeffnet, startete es wieder bei der im
 * .tcdz-Snapshot gespeicherten (inzwischen veralteten) Zeit, waehrend die
 * Ribbon-Uhr laengst weiter war - bei z.B. Faktor 5 ergaben schon 3 reale
 * Minuten Differenz rund 15 simulierte Minuten Abweichung.
 * <p>
 * Jetzt gibt es nur noch diese eine, programmweite laufende Uhr. Nur
 * {@link #setzen(ClockData)} (aufgerufen von {@link MainController} bei
 * Programmstart/Oeffnen/Neu anlegen/Abgleichen - also wenn wirklich frische
 * Daten aus der .tcdz vorliegen) setzt den Zeitstand zurueck. Jede
 * {@link DigitalClockView} - ob im Ribbon oder im Uhr-Fenster - fragt hier
 * nur noch {@link #aktuelleZeit()} ab, statt selbst zu ticken.
 */
final class LaufendeUhr {

    private static LocalTime currentTime;
    private static double factor = 1.0;
    private static LocalTime nachtBeginn;
    private static LocalTime nachtEnde;
    private static long lastTickNanos;
    private static boolean aktiv;
    /**
     * Fortlaufende Modellsekunden seit Programmstart (inklusive uebersprungener Nacht) und
     * laufender Modelltag - Zeitbasis des Echtzeit-Kerns (Konzept Abschnitt 2). Werden beim
     * Setzen der Uhr nicht zurueckgesetzt, damit Statistik und Kern stetig weiterlaufen.
     */
    private static double gesamtSimSekunden;
    private static int modellTag;

    private LaufendeUhr() {
    }

    /** Beschleunigungsfaktor der Uhr aus der .tcdz; NaN, solange keine Uhr läuft. */
    static double faktor() {
        return aktiv ? factor : Double.NaN;
    }

    /** Faktor als Text ("×10"), "–" ohne Uhr. */
    static String faktorText() {
        double f = faktor();
        if (Double.isNaN(f)) {
            return "–";
        }
        return "×" + (f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(f).replace('.', ','));
    }

    /** Setzt die laufende Uhr auf frisch aus der .tcdz gelesene Werte zurueck. */
    static void setzen(ClockData clock) {
        if (clock == null || clock.time() == null) {
            aktiv = false;
            return;
        }
        LocalTime zeit;
        try {
            zeit = LocalTime.parse(clock.time());
        } catch (Exception ex) {
            aktiv = false;
            return;
        }
        double neuerFaktor;
        try {
            neuerFaktor = clock.factor() != null ? Double.parseDouble(clock.factor()) : 1.0;
        } catch (NumberFormatException ex) {
            neuerFaktor = 1.0;
        }
        currentTime = zeit;
        factor = neuerFaktor;
        nachtBeginn = parseZeitOderNull(clock.end());
        nachtEnde = parseZeitOderNull(clock.start());
        if (nachtBeginn == null || nachtEnde == null || nachtBeginn.equals(nachtEnde)) {
            // Siehe DigitalClockView (Vorgaenger dieser Klasse) - ohne beide
            // Angaben oder bei start=end laesst sich keine sinnvolle Nacht
            // bestimmen.
            nachtBeginn = null;
            nachtEnde = null;
        }
        lastTickNanos = System.nanoTime();
        aktiv = true;
    }

    /**
     * Uhr auf die Modellzeit aus iTrain stellen, die über XTC kommt (Anwender 09.11.2026: in iTrain gestellt, die
     * Logistik zeigte nach „Abgleichen“ etwas anderes – die .tcdz auf diesem Rechner war nicht die von iTrain
     * gespeicherte). XTC meldet nur Stunde und Minute; deshalb wird nur gestellt, wenn die Uhr mehr als eine
     * Minute abweicht. Der Faktor bleibt.
     */
    static boolean angleichen(LocalTime itrain) {
        if (itrain == null) {
            return false;
        }
        if (!aktiv || currentTime == null) {
            currentTime = itrain;
            lastTickNanos = System.nanoTime();
            aktiv = true;
            return true;
        }
        LocalTime jetzt = aktuelleZeit();
        long diff = Math.abs(jetzt.toSecondOfDay() - itrain.toSecondOfDay());
        diff = Math.min(diff, 86400 - diff);
        if (diff <= 60) {
            return false;
        }
        currentTime = itrain;
        lastTickNanos = System.nanoTime();
        return true;
    }

    static void anhalten() {
        aktiv = false;
    }

    static boolean istAktiv() {
        return aktiv;
    }

    /**
     * Rollt die Uhr um die seit dem letzten Aufruf real vergangene Zeit (mal
     * Faktor) weiter und liefert den aktuellen Stand - null, solange keine
     * Uhr aktiv ist (siehe {@link #istAktiv()}).
     */
    static LocalTime aktuelleZeit() {
        if (!aktiv || currentTime == null) {
            return null;
        }
        long jetztNanos = System.nanoTime();
        double vergangeneSekundenSim = (jetztNanos - lastTickNanos) / 1_000_000_000.0 * factor;
        lastTickNanos = jetztNanos;
        if (vergangeneSekundenSim > 0) {
            LocalTime vorher = currentTime;
            currentTime = currentTime.plusNanos(Math.round(vergangeneSekundenSim * 1_000_000_000.0));
            if (istNacht(currentTime)) {
                currentTime = nachtEnde;
            }
            double sekunden = (currentTime.toNanoOfDay() - vorher.toNanoOfDay()) / 1_000_000_000.0;
            if (sekunden < 0) {
                sekunden += 86400;
                modellTag++;
            }
            gesamtSimSekunden += sekunden;
        }
        return currentTime;
    }

    /** Fortlaufende Modellsekunden, siehe Feldkommentar. Ruft dafuer {@link #aktuelleZeit()} auf. */
    static double gesamtSimSekunden() {
        aktuelleZeit();
        return gesamtSimSekunden;
    }

    static int modellTag() {
        return modellTag;
    }

    /** true, wenn zeit im (halboffenen) Intervall [nachtBeginn, nachtEnde) liegt - ueber Mitternacht hinweg. */
    private static boolean istNacht(LocalTime zeit) {
        if (nachtBeginn == null || nachtEnde == null) {
            return false;
        }
        if (nachtBeginn.isBefore(nachtEnde)) {
            return !zeit.isBefore(nachtBeginn) && zeit.isBefore(nachtEnde);
        }
        return !zeit.isBefore(nachtBeginn) || zeit.isBefore(nachtEnde);
    }

    private static LocalTime parseZeitOderNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }
}
