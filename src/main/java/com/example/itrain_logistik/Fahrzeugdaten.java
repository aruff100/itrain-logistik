package com.example.itrain_logistik;

import com.example.itrain_logistik.kern.Ladeklasse;
import com.example.itrain_logistik.kern.Tf3Daten;
import com.example.itrain_logistik.kern.Wagenladung;
import com.example.itrain_logistik.kern.ZugAusstattung;

import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ermittelt fuer den Echtzeit-Kern, was ein Zug transportieren kann (Konzept Abschnitt 7):
 * aus der eigenen Zusammenstellung des Zugs ({@link TrainEntry#getComposition()}) und den
 * Logistikfeldern der Wagen ({@link LogistikFelder}). Der Zugname aus dem Trigger wird zuerst
 * als iTrain-Zug gesucht, dann als fuehrende Lok eines Zugs.
 * <p>
 * Gueterwagen: Ladeklasse, Kapazitaet, Wagenladungen und aktiv geschaltete Gueter; die
 * Kapazitaet verteilt sich gleichmaessig auf die Wagenladungen. Sind Gueter aktiv geschaltet,
 * laedt der Wagen nur diese (auch ueber Ladeklassen hinweg). Ohne Ladeklasse und ohne aktive
 * Gueter traegt ein Wagen nichts. Personenwagen: Sitzplaetze 1. Klasse, Sitzplaetze 2. Klasse
 * plus Stehplaetze. Zeit fuer Beladen/Entladen (Modellsekunden fuer einen vollen Wagen) begrenzt
 * das Umladen zusaetzlich; 0 = keine Bewertung.
 * Triebzuege (Anwender 22.10.2026): eine Lok mit dem Feld {@link LogistikFelder#TRIEBZUG} befoerdert
 * selbst Personen ({@link LogistikFelder#TRIEBZUG_PERSONEN}) und/oder Gueter
 * ({@link LogistikFelder#TRIEBZUG_GUETER}) mit denselben Feldern wie ein Wagen.
 */
public final class Fahrzeugdaten {

    private final Supplier<LogistikSnapshot> snapshot;
    /** Ladeklasse eines Guts (fuer Wagen ohne eigene Ladeklasse, aber mit aktiven Guetern). */
    private final java.util.function.Function<String, Ladeklasse> gutKlasse;

    public Fahrzeugdaten(Supplier<LogistikSnapshot> snapshot) {
        this(snapshot, null);
    }

    public Fahrzeugdaten(Supplier<LogistikSnapshot> snapshot, java.util.function.Function<String, Ladeklasse> gutKlasse) {
        this.snapshot = snapshot;
        this.gutKlasse = gutKlasse;
    }

    public ZugAusstattung fuer(String zugName) {
        LogistikSnapshot s = snapshot.get();
        if (s == null || zugName == null) {
            return ZugAusstattung.LEER;
        }
        TrainEntry zug = findeZug(s, zugName);
        if (zug == null) {
            return ZugAusstattung.LEER;
        }
        Sammler sam = new Sammler();
        for (TrainCompositionItem item : zug.getComposition()) {
            if (item.getKind() == TrainCompositionItem.Kind.LOCOMOTIVE) {
                // Triebzug/Triebwagen (Anwender 22.10.2026): in iTrain eine Lok, befördert aber selbst Personen
                // und/oder Güter – mit denselben Feldern wie ein Wagen.
                LocomotiveEntry lok = findeLok(s, item.getName());
                if (lok != null && istTriebzug(lok.getCustomFields())) {
                    ObservableList<CustomField> f = lok.getCustomFields();
                    if (ja(f, LogistikFelder.TRIEBZUG_PERSONEN)) {
                        sam.personen(lok.getName(), f);
                    }
                    if (ja(f, LogistikFelder.TRIEBZUG_GUETER)) {
                        sam.gueter(lok.getName(), f);
                    }
                }
                continue;
            }
            if (item.getKind() != TrainCompositionItem.Kind.WAGON) {
                continue;
            }
            WagonEntry wagen = findeWagen(s, item.getName());
            if (wagen == null) {
                continue;
            }
            WagenArt art = LogistikFelder.parseEnum(
                    LogistikFelder.getValue(wagen.getCustomFields(), LogistikFelder.WAGENART), WagenArt.values());
            if (art != null && art.getFeldsatz() == WagenArt.Feldsatz.PERSONEN) {
                sam.personen(wagen.getName(), wagen.getCustomFields());
            } else if (art != null && art.getFeldsatz() == WagenArt.Feldsatz.BEIDE) {
                // Personen-/Gepäckwagen: Plätze und Laderaum
                sam.personen(wagen.getName(), wagen.getCustomFields());
                sam.gueter(wagen.getName(), wagen.getCustomFields());
            } else {
                sam.gueter(wagen.getName(), wagen.getCustomFields());
            }
        }
        return new ZugAusstattung(sam.ladungen, sam.erste, sam.zweite,
                sam.einUnbegrenzt || sam.einJeSekunde <= 0 ? Double.POSITIVE_INFINITY : sam.einJeSekunde,
                sam.ausUnbegrenzt || sam.ausJeSekunde <= 0 ? Double.POSITIVE_INFINITY : sam.ausJeSekunde,
                sam.signatur.toString());
    }

    /** Ist eine Lok als Triebzug eingetragen (befördert selbst)? */
    public static boolean istTriebzug(ObservableList<CustomField> f) {
        return ja(f, LogistikFelder.TRIEBZUG);
    }

    private static boolean ja(ObservableList<CustomField> f, String feld) {
        return "true".equals(LogistikFelder.getValue(f, feld));
    }

    /** Sammelt Plätze, Wagenladungen und Umladeraten eines Zugs. */
    private final class Sammler {
        final List<Wagenladung> ladungen = new ArrayList<>();
        double erste;
        double zweite;
        double einJeSekunde;
        double ausJeSekunde;
        boolean einUnbegrenzt;
        boolean ausUnbegrenzt;
        final StringBuilder signatur = new StringBuilder();

        /** Personenfelder: Sitzplätze 1./2. Klasse, Stehplätze (2. Klasse), Zeiten für alle Plätze. */
        void personen(String name, ObservableList<CustomField> f) {
            double e = zahl(f, LogistikFelder.SITZPLAETZE_1);
            // 3./4. Klasse (Epoche I/II, Anwender 11.11.2026) zählen im Kern zur 2. Klasse
            double z = zahl(f, LogistikFelder.SITZPLAETZE_2) + zahl(f, LogistikFelder.STEHPLAETZE)
                    + Epochen.untereKlassen(f);
            erste += e;
            zweite += z;
            // Zeit fuer Ein-/Aussteigen: alle Plaetze des Wagens in dieser Zeit; 0 = keine Bewertung.
            double tEin = zahl(f, LogistikFelder.ZEIT_BELADEN);
            double tAus = zahl(f, LogistikFelder.ZEIT_ENTLADEN);
            if (tEin > 0) {
                einJeSekunde += (e + z) / tEin;
            } else {
                einUnbegrenzt = true;
            }
            if (tAus > 0) {
                ausJeSekunde += (e + z) / tAus;
            } else {
                ausUnbegrenzt = true;
            }
            signatur.append("P:").append(name).append(':').append(e).append('/').append(z).append(';');
            // Post und Expressgut im Personenwagen: je eine eigene Wagenladung mit 10 Stück (Anwender 27.10.2026)
            for (String[] p : new String[][]{{LogistikFelder.KANN_POST, Tf3Daten.POST},
                    {LogistikFelder.KANN_EXPRESSGUT, Tf3Daten.EXPRESSGUT}}) {
                if ("true".equals(LogistikFelder.getValue(f, p[0]))) {
                    Wagenladung w = new Wagenladung(name, Ladeklasse.STUECKGUT, Tf3Daten.POST_KAPAZITAET,
                            java.util.Set.of(p[1]), 0, 0);
                    w.setEinheit("Stück");
                    ladungen.add(w);
                    signatur.append(p[1]).append(';');
                }
            }
        }

        /** Güterfelder: Ladeklasse, Kapazität, Wagenladungen, aktive Güter, Behälter (Joker), Zeiten. */
        void gueter(String name, ObservableList<CustomField> f) {
            Ladeklasse klasse = LogistikFelder.parseEnum(
                    LogistikFelder.getValue(f, LogistikFelder.LADEKLASSE), Ladeklasse.values());
            java.util.Set<String> erlaubt = ladegueter(f);
            // Behälter = Lade-Joker: lädt jedes Gut, Ladeklasse und aktive Güter zählen nicht.
            boolean joker = ja(f, LogistikFelder.BEHAELTER);
            if (joker) {
                erlaubt = java.util.Set.of();
                if (klasse == null) {
                    klasse = Ladeklasse.STUECKGUT;
                }
            }
            if (klasse == null && !erlaubt.isEmpty() && gutKlasse != null) {
                klasse = gutKlasse.apply(erlaubt.iterator().next());
            }
            double kapazitaet = zahl(f, LogistikFelder.KAPAZITAET);
            if (klasse == null || kapazitaet <= 0) {
                return;
            }
            int anzahl = Math.max(1, (int) Math.round(zahl(f, LogistikFelder.WAGENLADUNGEN)));
            double tLaden = zahl(f, LogistikFelder.ZEIT_BELADEN);
            double tEntladen = zahl(f, LogistikFelder.ZEIT_ENTLADEN);
            for (int i = 0; i < anzahl; i++) {
                Wagenladung w = new Wagenladung(name, klasse, kapazitaet / anzahl, erlaubt, tLaden, tEntladen);
                w.setJoker(joker);
                w.setEinheit(LogistikFelder.getValue(f, LogistikFelder.KAPAZITAET_EINHEIT));
                ladungen.add(w);
            }
            signatur.append("G:").append(name).append(':').append(klasse).append(':')
                    .append(kapazitaet).append('x').append(anzahl).append(':').append(erlaubt)
                    .append(':').append(tLaden).append('/').append(tEntladen).append(joker ? ":J" : "").append(':')
                    .append(LogistikFelder.getValue(f, LogistikFelder.KAPAZITAET_EINHEIT)).append(';');
        }
    }

    /** Aktiv geschaltete Gueter eines Wagens (Feld {@link LogistikFelder#LADEGUETER}). */
    public static java.util.Set<String> ladegueter(WagonEntry wagen) {
        return ladegueter(wagen.getCustomFields());
    }

    /** Aktiv geschaltete Gueter aus den Feldern eines Wagens oder Triebzugs. */
    public static java.util.Set<String> ladegueter(ObservableList<CustomField> f) {
        java.util.Set<String> s = new java.util.LinkedHashSet<>();
        for (String id : LogistikFelder.getValue(f, LogistikFelder.LADEGUETER).split(",")) {
            if (!id.isBlank()) {
                s.add(id.trim());
            }
        }
        return s;
    }

    static TrainEntry findeZug(LogistikSnapshot s, String name) {
        for (TrainEntry t : s.trains()) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        for (TrainEntry t : s.trains()) {
            for (TrainCompositionItem item : t.getComposition()) {
                if (item.getKind() == TrainCompositionItem.Kind.LOCOMOTIVE && item.isMain()
                        && item.getName().equals(name)) {
                    return t;
                }
            }
        }
        return null;
    }

    private static WagonEntry findeWagen(LogistikSnapshot s, String name) {
        for (WagonEntry w : s.wagons()) {
            if (w.getName().equals(name)) {
                return w;
            }
        }
        return null;
    }

    static LocomotiveEntry findeLok(LogistikSnapshot s, String name) {
        for (LocomotiveEntry l : s.locomotives()) {
            if (l.getName().equals(name)) {
                return l;
            }
        }
        return null;
    }

    static double zahl(ObservableList<CustomField> f, String feld) {
        String text = LogistikFelder.getValue(f, feld);
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
