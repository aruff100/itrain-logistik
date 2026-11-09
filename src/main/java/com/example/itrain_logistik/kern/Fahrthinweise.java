package com.example.itrain_logistik.kern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Nächste nötige Fahrten für den Zuganzeiger (Anwender 16.10.2026), allein aus dem laufenden Zustand der Anlage:
 *
 * <ul>
 * <li>Ein Ort braucht ein Gut (offener Bedarf ab 1 Einheit) oder einem Betrieb geht ein Eingangsgut aus (Bestand
 * unter einer Modellstunde Verbrauch, Platz im Eingangslager): Ziel ist ein Ladegleis einer Station, die den Ort bzw.
 * Betrieb bedient und das Gut entladen darf.</li>
 * <li>Abholort ist ein Ladegleis, an dem das Gut zur Abholung bereitsteht, sonst ein Schattenbahnhof-Ladegleis mit
 * Angebot für dieses Gut.</li>
 * <li>Geeignet sind alle Züge, deren Wagen (oder Triebwagen) das Gut laden dürfen, auch Behälterwagen (Lade-Joker);
 * zuerst die, deren Linie Abhol- und Zielgleis fährt, dann die, die gerade am Abholgleis stehen.</li>
 * <li>Zusätzlich Reisende, die an einem Bahnsteig warten, mit Zügen, die Plätze haben.</li>
 * </ul>
 *
 * Mengen sind ganze Einheiten; Bruchteile zählen als volle Einheit.
 */
public final class Fahrthinweise {

    /** Ein Hinweis; {@code gutId} null = Reisende. */
    public record Hinweis(String gutId, String kuerzel, int menge, String ziel, String abholung, List<String> zuege,
            String text, java.util.Set<String> stationen) {

        public Hinweis(String gutId, String kuerzel, int menge, String ziel, String abholung, List<String> zuege,
                String text) {
            this(gutId, kuerzel, menge, ziel, abholung, zuege, text, java.util.Set.of());
        }

        /**
         * Betrifft der Hinweis einen dieser Bahnhöfe (Ids)? Zuganzeiger je Bahnhof (Anwender 09.11.2026): Reisende,
         * die dort warten; Güter, die dort abgeholt oder angeliefert werden.
         */
        public boolean betrifft(java.util.Collection<String> ids) {
            for (String id : ids) {
                if (stationen.contains(id)) {
                    return true;
                }
            }
            return false;
        }

        /** Text ohne den Schluss „– geeignet: Züge …“ (Zuganzeiger ohne Zugvorschläge, Anwender 27.10.2026). */
        public String textOhneZuege() {
            int i = text.lastIndexOf(" – ");
            return i > 0 ? text.substring(0, i) : text;
        }
    }

    private record Quelle(Station station, Gleis gleis, double menge, boolean unbegrenzt) {
        String name() {
            return station.getAnzeigeName() + ", " + Fahrthinweise.gleis(gleis);
        }
    }

    private Fahrthinweise() {
    }

    public static List<Hinweis> ermitteln(Anlage a, Collection<ZugZustand> zuege) {
        List<Hinweis> l = new ArrayList<>();
        // --- Güterbedarf der Orte
        for (Ort o : a.orte()) {
            if (o.isPausiert()) {
                continue;
            }
            o.getOffenerBedarf().forEach((g, m) -> {
                int menge = ganz(m - unterwegsZu(a, o.getId(), g));
                if (menge >= 1) {
                    l.add(gueterHinweis(a, zuege, g, menge, o, o.getAnzeigeName()));
                }
            });
        }
        // --- Eingänge der Betriebe
        for (Betrieb b : a.betriebe()) {
            if (b.isVirtuell() || b.isPausiert()) {
                continue;
            }
            for (String g : b.eingangsGueter()) {
                if (a.immerGeliefert(b, g)) {
                    continue;
                }
                double bestand = b.getBestandEin().getOrDefault(g, 0.0) + unterwegsZu(a, b.getId(), g);
                double stunde = b.verbrauchProStunde(g);
                if (bestand >= stunde) {
                    continue;
                }
                int menge = (int) Math.min(Math.floor(b.freiEin(g)), ganz(stunde - bestand));
                if (menge >= 1) {
                    l.add(gueterHinweis(a, zuege, g, menge, b, b.getAnzeigeName()));
                }
            }
        }
        // --- Reisende (je Station und Richtung zusammengefasst)
        for (Station s : a.stationen()) {
            java.util.Map<String, double[]> jeRichtung = new java.util.LinkedHashMap<>();
            java.util.Map<String, String> gleisName = new java.util.LinkedHashMap<>();
            for (Gleis gl : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                for (String richtung : gl.warteschlangen()) {
                    double n = gl.wartendeSumme(richtung);
                    if (n <= 0) {
                        continue;
                    }
                    String r = richtung == null ? "" : richtung;
                    double[] v = jeRichtung.computeIfAbsent(r, k -> new double[2]);
                    v[0] += n;
                    if (n > v[1]) {
                        v[1] = n;
                        gleisName.put(r, gleis(gl));
                    }
                }
            }
            for (var e : jeRichtung.entrySet()) {
                int n = (int) Math.floor(e.getValue()[0] + 1e-6);
                if (n < 1) {
                    continue;
                }
                String gl = gleisName.get(e.getKey());
                // Alle Züge mit Plätzen, auch Triebwagen (Anwender 22.10.2026: SVT17/SVT137 fehlten, weil nur
                // die ersten zwei genannt wurden); zuerst die, deren Linie die Station fährt, dann nach Plätzen.
                Set<String> bloecke = new HashSet<>();
                for (Gleis b : s.gleiseDerArt(GleisArt.BAHNSTEIG)) {
                    bloecke.add(b.getBlockName());
                }
                List<ZugZustand> kandidaten = new ArrayList<>();
                for (ZugZustand z : zuege) {
                    if (z.getSitzplaetzeErste() + z.getSitzplaetzeZweite() > 0) {
                        kandidaten.add(z);
                    }
                }
                // Fahrtrichtung (Anwender 09.11.2026): Züge, die schon in Richtung der Wartenden fahren, zuerst
                String richtung = e.getKey();
                kandidaten.sort(Comparator.comparingInt((ZugZustand z) -> -(punkteStation(a, z, bloecke)
                                + (richtungPasst(z, richtung) ? 4 : 0)))
                        .thenComparingDouble(z -> -(z.getSitzplaetzeErste() + z.getSitzplaetzeZweite())));
                List<String> passend = new ArrayList<>();
                for (ZugZustand z : kandidaten) {
                    passend.add(z.getName() + (richtungPasst(z, richtung) ? " (" + t("Richtung {0}", t(richtung)) + ")" : ""));
                }
                String wohin = e.getKey().isBlank() ? "" : " " + t("Richtung {0}", t(e.getKey()));
                // Die Menge steht in einer eigenen Spalte, im Text nicht noch einmal (Anwender 22.10.2026)
                // Text gleich in der eingestellten Sprache (Browser-Zuganzeiger, Anwender 09.11.2026); " – " vor den Zügen bleibt
                String text = t("Reisende warten in {0}{1} (meiste an {2}) – {3}", t(s.getAnzeigeName()), wohin, gl,
                        zugText(passend, "Personenzug"));
                l.add(new Hinweis(null, "Pers", n, e.getKey(), s.getAnzeigeName() + ", " + gl, passend, text,
                        java.util.Set.of(s.getId())));
            }
        }
        // zuerst Güter mit Abholort, dann Güter ohne, zuletzt Reisende; jeweils nach Menge
        l.sort(Comparator.comparingInt((Hinweis h) -> h.gutId() == null ? 2 : h.abholung() == null ? 1 : 0)
                .thenComparingInt(h -> -h.menge()));
        return l;
    }

    private static Hinweis gueterHinweis(Anlage a, Collection<ZugZustand> zuege, String g, int menge,
            AnlagenObjekt ziel, String zielName) {
        Gut gut = a.gut(g);
        String name = gut != null ? gut.getName() : g;
        String kuerzel = gut != null && gut.getKuerzel() != null ? gut.getKuerzel() : name;
        String einheit = gut != null ? gut.getEinheit() : "";
        // Zielgleis: Ladegleis einer Station, die das Ziel bedient und das Gut entladen darf
        Station zielStation = null;
        Gleis zielGleis = null;
        for (Station s : a.gueterStationenFuer(ziel.getId())) {
            for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                if (gl.isEntladen() && gl.erlaubt(g)) {
                    zielStation = s;
                    zielGleis = gl;
                    break;
                }
            }
            if (zielGleis != null) {
                break;
            }
        }
        Quelle q = quelle(a, g, zielStation);
        List<String> passend = new ArrayList<>();
        List<ZugZustand> kandidaten = new ArrayList<>();
        for (ZugZustand z : zuege) {
            if (gut != null && z.kapazitaetFuer(gut) >= 1) {
                kandidaten.add(z);
            }
        }
        String quellBlock = q != null ? q.gleis().getBlockName() : null;
        String zielBlock = zielGleis != null ? zielGleis.getBlockName() : null;
        // Fahrtrichtung (Anwender 09.11.2026): fährt der Zug schon Richtung Zielstation (bzw. deren Bahnhof)?
        String zielRichtung = zielStation == null ? null
                : (a.gehoertZu(zielStation) != null ? a.gehoertZu(zielStation) : zielStation).getAnzeigeName();
        Station zs = zielStation;
        kandidaten.sort(Comparator.comparingInt((ZugZustand z) -> -(punkte(a, z, quellBlock, zielBlock)
                + (richtungPasst(z, zielRichtung) || (zs != null && richtungPasst(z, zs.getAnzeigeName())) ? 3 : 0))));
        // alle geeigneten Züge; Behälterwagen (Lade-Joker) laden jedes Gut und werden gekennzeichnet
        for (ZugZustand z : kandidaten) {
            boolean richtungOk = richtungPasst(z, zielRichtung) || (zs != null && richtungPasst(z, zs.getAnzeigeName()));
            passend.add(z.getName() + (z.hatJoker() ? " (" + t("Joker") + ")" : "")
                    + (richtungOk ? " (" + t("Richtung {0}", t(zielRichtung)) + ")" : ""));
        }
        StringBuilder t = new StringBuilder();
        // Die Menge steht in einer eigenen Spalte (Anwender 22.10.2026); die Einheit bleibt im Text
        t.append(t("{0} in {1} benötigt", t(name) + (einheit.isBlank() ? "" : " (" + t(einheit) + ")"), t(zielName)));
        if (zielGleis != null && zielStation != null) {
            t.append(" (").append(t("anliefern: {0}, {1}", t(zielStation.getAnzeigeName()), gleis(zielGleis))).append(')');
        } else {
            t.append(" (").append(t("kein Ladegleis zum Entladen")).append(')');
        }
        if (q != null) {
            t.append(" – ").append(t("abholen in {0}", t(q.name())));
            if (!q.unbegrenzt()) {
                t.append(" (").append(t("{0} bereit", (long) Math.floor(q.menge() + 1e-6))).append(')');
            }
        } else {
            t.append(" – ").append(t("noch nirgends zur Abholung bereit"));
        }
        t.append(" – ").append(zugText(passend, "Zug mit passenden Wagen"));
        java.util.Set<String> st = new java.util.HashSet<>();
        if (zielStation != null) {
            st.add(zielStation.getId());
        }
        if (q != null) {
            st.add(q.station().getId());
        }
        return new Hinweis(g, kuerzel, menge, zielName, q != null ? q.name() : null, passend, t.toString(), st);
    }

    private static String zugText(List<String> zuege, String art) {
        if (zuege.isEmpty()) {
            return t("kein {0} vorhanden", t(art));
        }
        return t(zuege.size() == 1 ? "geeignet: Zug {0}" : "geeignet: Züge {0}", String.join(", ", zuege));
    }

    /** Fährt der Zug laut letzter Einfahrt in diese Richtung (Name der Zielstation)? */
    private static boolean richtungPasst(ZugZustand z, String richtung) {
        return richtung != null && !richtung.isBlank() && z.getRichtung() != null
                && z.getRichtung().trim().equalsIgnoreCase(richtung.trim());
    }

    /** Rangfolge für Reisende: Zug steht an der Station (3), seine Linie fährt einen ihrer Bahnsteige (2). */
    private static int punkteStation(Anlage a, ZugZustand z, Set<String> bloecke) {
        int p = 0;
        if (z.getBlock() != null && bloecke.contains(z.getBlock())) {
            p += 3;
        }
        Linie l = z.getLinie() != null ? a.linie(z.getLinie()) : null;
        if (l != null && l.getBlockNamen().stream().anyMatch(bloecke::contains)) {
            p += 2;
        }
        return p;
    }

    /** Bester Abholort: Ladegleis mit der größten bereitstehenden Menge, sonst Schattenbahnhof mit Angebot. */
    private static Quelle quelle(Anlage a, String g, Station ausser) {
        Quelle beste = null;
        for (Station s : a.stationen()) {
            if (s == ausser) {
                continue;
            }
            for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                double m = gl.getAusgang().getOrDefault(g, 0.0);
                if (m >= 1 && (beste == null || beste.unbegrenzt() || m > beste.menge())) {
                    beste = new Quelle(s, gl, m, false);
                }
            }
        }
        if (beste != null) {
            return beste;
        }
        for (Station s : a.stationen()) {
            if (s == ausser || !a.istAussenwelt(s)) {
                continue;
            }
            for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                if (gl.isLaden() && gl.getAngebot().getOrDefault(g, 0.0) > 0) {
                    return new Quelle(s, gl, Double.POSITIVE_INFINITY, true);
                }
            }
            // angedockter Güterbahnhof eines Schattenbahnhofs
            Station gbf = a.angedockterGueterbahnhof(s);
            if (gbf != null && gbf != ausser) {
                for (Gleis gl : gbf.gleiseDerArt(GleisArt.LADEGLEIS)) {
                    if (gl.isLaden() && gl.getAngebot().getOrDefault(g, 0.0) > 0) {
                        return new Quelle(gbf, gl, Double.POSITIVE_INFINITY, true);
                    }
                }
            }
        }
        return null;
    }

    /** Rangfolge eines Zugs: Linie fährt Abhol- und Zielgleis (4/2 Punkte), steht am Abholgleis (3). */
    private static int punkte(Anlage a, ZugZustand z, String quellBlock, String zielBlock) {
        int p = 0;
        Linie l = z.getLinie() != null ? a.linie(z.getLinie()) : null;
        Set<String> bloecke = l != null ? new HashSet<>(l.getBlockNamen()) : Set.of();
        if (quellBlock != null && bloecke.contains(quellBlock)) {
            p += 2;
        }
        if (zielBlock != null && bloecke.contains(zielBlock)) {
            p += 2;
        }
        if (quellBlock != null && quellBlock.equals(z.getBlock())) {
            p += 3;
        }
        return p;
    }

    /** Was schon in einem Zug zum Ziel unterwegs ist, wird hier nicht erkannt (keine Fahrpläne) – daher 0. */
    private static double unterwegsZu(Anlage a, String zielId, String g) {
        // Bereits abgeladene, noch nicht verteilte Ware an den Gleisen der Zielstationen zählt als geliefert.
        double m = 0;
        Set<Gleis> gesehen = new LinkedHashSet<>();
        for (Station s : a.gueterStationenFuer(zielId)) {
            for (Gleis gl : s.gleiseDerArt(GleisArt.LADEGLEIS)) {
                if (gesehen.add(gl)) {
                    m += gl.getEingang().getOrDefault(g, 0.0);
                }
            }
        }
        return m;
    }

    /** "Gleis 2" bzw. Gleisname, ohne doppeltes "Gleis Gleis 1". */
    static String gleis(Gleis g) {
        String n = g.getAnzeigeName();
        return n.toLowerCase().startsWith("gleis") ? n : t("Gleis") + " " + n;
    }

    /** Text in der eingestellten Sprache (Werte werden eingesetzt, nicht übersetzt). */
    private static String t(String text, Object... werte) {
        return com.example.itrain_logistik.Uebersetzung.tr(text, werte);
    }

    /** Volle Einheiten: geliefert wird erst ab einer ganzen Einheit, ein Rest unter 1 zählt noch nicht. */
    private static int ganz(double v) {
        return v < 1 - 1e-6 ? 0 : (int) Math.floor(v + 1e-6);
    }
}
