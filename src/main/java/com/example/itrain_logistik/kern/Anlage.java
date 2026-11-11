package com.example.itrain_logistik.kern;

import com.example.itrain_logistik.NetzText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die komplette Logistik-Konfiguration einer Anlage: Gueterkatalog, Objekte (Orte, Stationen
 * mit Gleisen, Betriebe, Lager, Uebergaben), Texte der Uebersicht und die Linien aus iTrain.
 * Ersetzt das fruehere LogistikNetz (Ebene 1 bis 3). Gespeichert in der .logz, siehe
 * {@link AnlageXml}.
 */
public final class Anlage {

    private final Map<String, Gut> gueter = new LinkedHashMap<>();
    private final List<AnlagenObjekt> objekte = new ArrayList<>();
    private final List<NetzText> texte = new ArrayList<>();
    private final List<Linie> linien = new ArrayList<>();
    /** Alle Blocknamen der .tcdz (Laufzeit, wird beim Laden aus dem Snapshot gesetzt). */
    private final List<String> bloecke = new ArrayList<>();
    /** Laufzeitzustand der Zuege (Ladung, Passagiere), Schluessel = Zugname aus iTrain. */
    private final Map<String, ZugZustand> zuege = new LinkedHashMap<>();
    /** Faktor fuer die Lagergroessen der Vorlagen beim Anlegen eines Betriebs (Konzept 4.3). */
    private double mengenMassstab = 0.1;
    /** Name des ersten Reiters der Übersicht (Anwender 09.11.2026: Reiter frei benennbar). */
    private String hauptReiterName = "Anlage";

    public String getHauptReiterName() {
        return hauptReiterName;
    }

    public void setHauptReiterName(String n) {
        hauptReiterName = n == null || n.isBlank() ? "Anlage" : n.trim();
    }

    /** Größe der Zeichenfläche in „Anlage bearbeiten“ (0 = Vorgabe), Anwender 27.10.2026. */
    private double flaecheBreite;
    private double flaecheHoehe;

    public double getFlaecheBreite() {
        return flaecheBreite;
    }

    public double getFlaecheHoehe() {
        return flaecheHoehe;
    }

    public void setFlaeche(double breite, double hoehe) {
        this.flaecheBreite = Math.max(0, breite);
        this.flaecheHoehe = Math.max(0, hoehe);
    }
    /** iTrain-Bahnhöfe, deren angeheftete Station der Anwender gelöscht hat (nicht wieder anheften). */
    private final java.util.Set<String> itrainIgnoriert = new java.util.LinkedHashSet<>();
    /**
     * Sind die iTrain-Bahnhöfe schon in die Anlage eingefügt? Nach einem neuen Import nicht: sie erscheinen erst beim
     * ersten „Anlage bearbeiten“ (Anwender 22.10.2026). Ältere Dateien gelten als eingefügt.
     */
    private boolean bahnhoefeEingefuegt = true;
    /**
     * Glückwunsch-Dialog beim ersten erfolgreichen Transfer (Anwender 22.10.2026): nur nach „Neu aus .tcdz“ offen,
     * nach dem ersten Personen- bzw. Gütertransfer erledigt; ältere Dateien gelten als erledigt.
     */
    private boolean ersterPersonenTransferOffen;
    private boolean ersterGueterTransferOffen;

    /**
     * Vorgabe aus dem Einrichtungs-Dialog „Bahnhöfe“: Art und Blöcke, die Ladegleis werden. Seit dem
     * Verkehrs-Assistenten (Anwender 27.10.2026) auch Name und Bahnsteige; Blöcke in keiner der beiden Mengen
     * sind „Vorerst nichts“. {@code bahnsteige == null}: alte Vorgabe, alle übrigen Blöcke sind Bahnsteige.
     */
    public record BahnhofVorgabe(ObjektArt art, java.util.Set<String> ladegleise, String name,
                                 java.util.Set<String> bahnsteige) {
        public BahnhofVorgabe(ObjektArt art, java.util.Set<String> ladegleise) {
            this(art, ladegleise, null, null);
        }

        /** Gleisart eines Blocks nach dieser Vorgabe; null = vorerst nichts. */
        public GleisArt artFuer(String block) {
            if (ladegleise.contains(block)) {
                return GleisArt.LADEGLEIS;
            }
            return bahnsteige == null || bahnsteige.contains(block) ? GleisArt.BAHNSTEIG : null;
        }
    }

    private final Map<String, BahnhofVorgabe> bahnhofVorgaben = new java.util.LinkedHashMap<>();

    public static final int TEMPO_MIN = -4;
    public static final int TEMPO_MAX = 4;
    /** Globale Tempo-Stufe (Schieberegler im Hauptfenster): 0 = Pause, 1 = normal. */
    private int tempo;

    /**
     * Faktor einer Tempo-Stufe (Anwender 14.10.2026: der Regler hat keine Pause mehr, Mitte = ×1):
     * -4..-1 = 0,1 / 0,25 / 0,5 / 0,75 (langsamer), 0 = ×1, 1..4 = 1,5 / 2 / 3 / 4 (schneller).
     * Angehalten wird über den Knopf im Ribbon (alles) bzw. {@link AnlagenObjekt#isPausiert()} (ein Objekt).
     */
    public static double tempoFaktor(int stufe) {
        return switch (stufe) {
            case -4 -> 0.1;
            case -3 -> 0.25;
            case -2 -> 0.5;
            case -1 -> 0.75;
            case 1 -> 1.5;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 1;
        };
    }

    public static String tempoText(int stufe) {
        return "×" + String.valueOf(tempoFaktor(stufe)).replace('.', ',').replaceAll(",0$", "");
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        this.tempo = Math.max(TEMPO_MIN, Math.min(TEMPO_MAX, tempo));
    }

    /** Wirksamer Faktor für Erzeugung/Bereitstellung eines Objekts: global × Objekt. */
    public double faktor(AnlagenObjekt o) {
        if (o != null && o.isPausiert()) {
            return 0;
        }
        return tempoFaktor(tempo) * (o != null ? tempoFaktor(o.getTempo()) : 1);
    }

    /**
     * Rechnet eine Tempo-Stufe aus .logz-Dateien vor dem 14.10.2026 um (Attribut "tempo": 0 = Pause,
     * 1 = normal, 2..4 = ×1,5/×2/×4) in die heutige Stufe (Attribut "tempoStufe", 0 = ×1).
     */
    public static int alteTempoStufe(int alt) {
        return switch (alt) {
            case 0, 1 -> 0;
            case 2 -> 1;
            case 3 -> 2;
            case 4 -> 4;
            default -> alt;
        };
    }

    /**
     * Steht ein Eingangsgut einem Betrieb immer zur Verfügung (vom Anwender als "wird immer
     * geliefert" markiert oder von einem virtuellen Betrieb erzeugt)?
     */
    public boolean immerGeliefert(Betrieb b, String gutId) {
        // Ein Betrieb außerhalb der Anlage bekommt seine Eingänge immer (Anwender 12.10.2026).
        if (b.isVirtuell() || b.getImmerGeliefert().contains(gutId)) {
            return true;
        }
        for (Betrieb v : betriebe()) {
            if (virtuellOhneStation(v) && v.ausgangsGueter().contains(gutId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Betrieb "nicht auf der Anlage" ohne angeschlossene Station: seine Ausgänge stehen allen Verbrauchern
     * automatisch zur Verfügung. Mit Station stellt er dagegen wirklich her und liefert dort an.
     */
    public boolean virtuellOhneStation(Betrieb v) {
        return v.isVirtuell() && stationenFuer(v.getId()).isEmpty();
    }

    /** Setzt alle Laufzeitwerte (Bestände, Warteschlangen, Zugladungen, Statistik) auf 0. */
    public void allesAufNull() {
        for (AnlagenObjekt o : objekte) {
            o.aufNull();
        }
        for (ZugZustand z : zuege.values()) {
            z.aufNull();
        }
    }

    /**
     * Übernimmt Güter, Objekte, Texte und Einstellungen einer anderen Anlage (Abbruch im Editor);
     * Blöcke, Linien und Zugzustände bleiben, Halte werden auf die neuen Objekte umgehängt.
     */
    public void uebernehmeVon(Anlage andere) {
        flaecheBreite = andere.flaecheBreite;
        flaecheHoehe = andere.flaecheHoehe;
        hauptReiterName = andere.hauptReiterName;
        gueter.clear();
        gueter.putAll(andere.gueter);
        geloeschteGueter.clear();
        geloeschteGueter.addAll(andere.geloeschteGueter);
        objekte.clear();
        objekte.addAll(andere.objekte);
        texte.clear();
        texte.addAll(andere.texte);
        mengenMassstab = andere.mengenMassstab;
        tempo = andere.tempo;
        itrainIgnoriert.clear();
        itrainIgnoriert.addAll(andere.itrainIgnoriert);
        bahnhoefeEingefuegt = andere.bahnhoefeEingefuegt;
        ersterPersonenTransferOffen = andere.ersterPersonenTransferOffen;
        ersterGueterTransferOffen = andere.ersterGueterTransferOffen;
        bahnhofVorgaben.clear();
        bahnhofVorgaben.putAll(andere.bahnhofVorgaben);
        for (ZugZustand z : zuege.values()) {
            z.halteNeuZuordnen(this);
        }
    }

    public static Anlage neuMitTf3Guetern() {
        Anlage a = new Anlage();
        for (Gut g : Tf3Daten.gueter()) {
            a.gueter.put(g.getId(), g);
        }
        a.infrastruktur();
        return a;
    }

    /** Der feste Mittelpunkt; wird bei Bedarf angelegt (Mitte der Zeichenflaeche). */
    public Infrastruktur infrastruktur() {
        for (AnlagenObjekt o : objekte) {
            if (o instanceof Infrastruktur i) {
                return i;
            }
        }
        Infrastruktur i = new Infrastruktur(null);
        i.setX(820);
        i.setY(480);
        objekte.add(0, i);
        return i;
    }

    public List<String> getBloecke() {
        return bloecke;
    }

    /** Alle Module (Anwender 09.11.2026). */
    public List<Modul> module() {
        List<Modul> l = new ArrayList<>();
        for (AnlagenObjekt o : objekte) {
            if (o instanceof Modul m) {
                l.add(m);
            }
        }
        return l;
    }

    /** Modul, an dem die Station hängt; ein Güterbahnhof gehört zum Modul seines Bahnhofs. Sonst null. */
    public Modul modulFuer(Station s) {
        return bereichFuer(s) instanceof Modul m ? m : null;
    }

    /** Alle Bereiche (Tabs und Module) in der Reihenfolge der Anlage (Anwender 09.11.2026). */
    public List<Bereich> bereiche() {
        List<Bereich> l = new ArrayList<>();
        for (AnlagenObjekt o : objekte) {
            if (o instanceof Bereich b) {
                l.add(b);
            }
        }
        return l;
    }

    /** Bereich (Tab/Modul), an dem die Station hängt; ein Güterbahnhof gehört zu dem seines Bahnhofs. Sonst null. */
    public Bereich bereichFuer(Station s) {
        Station bahnhof = gehoertZu(s);
        String id = (bahnhof != null ? bahnhof : s).getId();
        for (Bereich b : bereiche()) {
            if (b.getStationen().contains(id)) {
                return b;
            }
        }
        return null;
    }

    /**
     * Reiter, in dem ein Objekt steht (Anwender 09.11.2026): Station über {@link #bereichFuer}; Orte, Betriebe, Lager,
     * Übergaben beim ersten verbundenen Bahnhof; sonst der Reiter, in dem sie angelegt wurden. Infrastruktur und die
     * Bereichs-Symbole selbst stehen im ersten Reiter (null).
     */
    public Bereich bereichVon(AnlagenObjekt o) {
        if (o == null || o instanceof Bereich || o instanceof Infrastruktur) {
            return null;
        }
        if (o instanceof Station s) {
            return bereichFuer(s);
        }
        for (Station s : stationenFuer(o.getId())) {
            return bereichFuer(s);
        }
        return o.getReiterId() != null && objekt(o.getReiterId()) instanceof Bereich b ? b : null;
    }

    /** Objekte eines Reiters; {@code b} null = erster Reiter (mit Infrastruktur und allen Bereichs-Symbolen). */
    public List<AnlagenObjekt> inBereich(Bereich b) {
        List<AnlagenObjekt> l = new ArrayList<>();
        for (AnlagenObjekt o : objekte) {
            if (bereichVon(o) == b) {
                l.add(o);
            }
        }
        return l;
    }

    /**
     * Hängt einen Bahnhof an einen Bereich (Tab/Modul) bzw. mit null zurück an die Anlagen-Infrastruktur
     * („Mit Tab verbinden…“, „Mit Modul verbinden…“, Anwender 09.11.2026). Der angedockte Güterbahnhof und alles im
     * Einzugsbereich gehen mit.
     */
    public void bereichZuordnen(Station s, Bereich ziel) {
        Station bahnhof = gehoertZu(s) != null ? gehoertZu(s) : s;
        for (Bereich b : bereiche()) {
            b.getStationen().remove(bahnhof.getId());
        }
        if (ziel != null) {
            if (ziel.getStationen().isEmpty()) {
                // erstes Mitglied: Symbol im Reiter links neben den Bahnhof setzen
                ziel.setInnenX(Math.max(20, bahnhof.getX() - 320));
                ziel.setInnenY(bahnhof.getY());
            }
            ziel.getStationen().add(bahnhof.getId());
        }
    }

    /**
     * Ordnet eine Station einem Modul zu (Verkehrs-Assistent, Anwender 09.11.2026: „Ist dieser Bahnhof auf der
     * Anlage oder ein Modul?“). {@code modulName} null = auf der Anlage (aus allen Modulen lösen); ein unbekannter
     * Name legt das Modul neu an.
     *
     * @return das Modul oder null
     */
    public Modul modulZuordnen(Station s, String modulName) {
        Station bahnhof = gehoertZu(s) != null ? gehoertZu(s) : s;
        for (Bereich m : bereiche()) {
            m.getStationen().remove(bahnhof.getId());
        }
        if (modulName == null || modulName.isBlank()) {
            return null;
        }
        Modul ziel = null;
        for (Modul m : module()) {
            if (modulName.equals(m.getAnzeigeName())) {
                ziel = m;
                break;
            }
        }
        if (ziel == null) {
            ziel = new Modul(null, modulName.trim());
            AnlagenObjekt mitte = infrastruktur();
            platziereNeu(ziel, mitte != null ? mitte.getX() : 400, mitte != null ? mitte.getY() - RASTER_Y : 200);
        }
        ziel.getStationen().add(bahnhof.getId());
        return ziel;
    }

    /**
     * Alles, was zum Modul gehört: seine Stationen, deren angedockte Güterbahnhöfe und die Objekte in ihrem
     * Einzugsbereich (Orte, Betriebe, Lager, Übergaben).
     */
    public List<AnlagenObjekt> modulInhalt(Modul m) {
        java.util.LinkedHashSet<AnlagenObjekt> inhalt = new java.util.LinkedHashSet<>();
        for (Station s : stationen()) {
            if (modulFuer(s) == m) {
                inhalt.add(s);
            }
        }
        for (Station s : new ArrayList<>(stationenVon(inhalt))) {
            for (String id : s.getEinzugsbereich()) {
                AnlagenObjekt o = objekt(id);
                if (o != null) {
                    inhalt.add(o);
                }
            }
        }
        return new ArrayList<>(inhalt);
    }

    private static List<Station> stationenVon(java.util.Collection<AnlagenObjekt> objekte) {
        List<Station> l = new ArrayList<>();
        for (AnlagenObjekt o : objekte) {
            if (o instanceof Station s) {
                l.add(s);
            }
        }
        return l;
    }

    /** Bloecke, die keinem Gleis und keinem Modul zugeordnet sind (Strecke), in der Reihenfolge der .tcdz. */
    public List<String> freieBloecke() {
        java.util.Set<String> belegt = new java.util.HashSet<>();
        for (Modul m : module()) {
            belegt.addAll(m.getBloecke());
        }
        for (Station s : stationen()) {
            for (Gleis g : s.getGleise()) {
                if (g.getBlockName() != null) {
                    belegt.add(g.getBlockName());
                }
            }
        }
        List<String> l = new ArrayList<>();
        for (String b : bloecke) {
            if (!belegt.contains(b)) {
                l.add(b);
            }
        }
        return l;
    }

    /** Vom Anwender gelöschte Güter des Grundbestands; werden beim Laden nicht wieder ergänzt (27.10.2026). */
    private final Set<String> geloeschteGueter = new java.util.LinkedHashSet<>();

    public Set<String> getGeloeschteGueter() {
        return geloeschteGueter;
    }

    /** Fehlende Güter des Grundbestands ergänzen (neue Güter wie „Koffer“), außer selbst gelöschte. */
    public int grundbestandErgaenzen() {
        int n = 0;
        for (Gut g : Tf3Daten.gueter()) {
            if (!gueter.containsKey(g.getId()) && !geloeschteGueter.contains(g.getId())) {
                gueter.put(g.getId(), g);
                n++;
            }
        }
        return n;
    }

    public Map<String, Gut> getGueter() {
        return gueter;
    }

    public Gut gut(String id) {
        return id == null ? null : gueter.get(id);
    }

    public String gutName(String id) {
        Gut g = gut(id);
        return g != null ? g.getName() : id;
    }

    public List<AnlagenObjekt> getObjekte() {
        return objekte;
    }

    public List<NetzText> getTexte() {
        return texte;
    }

    public List<Linie> getLinien() {
        return linien;
    }

    public Map<String, ZugZustand> getZuege() {
        return zuege;
    }

    public Linie linie(String name) {
        for (Linie l : linien) {
            if (l.getName().equals(name)) {
                return l;
            }
        }
        return null;
    }

    public double getMengenMassstab() {
        return mengenMassstab;
    }

    public void setMengenMassstab(double mengenMassstab) {
        this.mengenMassstab = mengenMassstab > 0 ? mengenMassstab : 1;
    }

    public AnlagenObjekt objekt(String id) {
        for (AnlagenObjekt o : objekte) {
            if (o.getId().equals(id)) {
                return o;
            }
        }
        return null;
    }

    public <T extends AnlagenObjekt> List<T> alle(Class<T> typ) {
        List<T> l = new ArrayList<>();
        for (AnlagenObjekt o : objekte) {
            if (typ.isInstance(o)) {
                l.add(typ.cast(o));
            }
        }
        return l;
    }

    public List<Station> stationen() {
        return alle(Station.class);
    }

    public List<Betrieb> betriebe() {
        return alle(Betrieb.class);
    }

    public List<Ort> orte() {
        return alle(Ort.class);
    }

    public List<Lager> lager() {
        return alle(Lager.class);
    }

    public List<Uebergabe> uebergaben() {
        return alle(Uebergabe.class);
    }

    /** Stationen, deren Einzugsbereich das Objekt enthaelt. */
    public List<Station> stationenFuer(String objektId) {
        List<Station> l = new ArrayList<>();
        for (Station s : stationen()) {
            if (s.bedient(objektId)) {
                l.add(s);
            }
        }
        return l;
    }

    /** Objekte im Einzugsbereich einer Station. */
    public List<AnlagenObjekt> einzugsbereich(Station station) {
        List<AnlagenObjekt> l = new ArrayList<>();
        for (String id : station.getEinzugsbereich()) {
            AnlagenObjekt o = objekt(id);
            if (o != null) {
                l.add(o);
            }
        }
        return l;
    }

    /** Erster Ort im Einzugsbereich einer Station (fuer Passagiere), sonst null. */
    public Ort ortVon(Station station) {
        for (AnlagenObjekt o : einzugsbereich(station)) {
            if (o instanceof Ort ort) {
                return ort;
            }
        }
        return null;
    }

    public record GleisTreffer(Station station, Gleis gleis) {
    }

    /** Das erste Gleis, dem ein iTrain-Block zugeordnet ist, sonst null. */
    public GleisTreffer gleisFuerBlock(String blockName) {
        List<GleisTreffer> l = gleiseFuerBlock(blockName);
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Alle Gleise eines Blocks: hoechstens ein Bahnsteig und ein Ladegleis (ein Bahnhofsgleis kann
     * zugleich Ladegleis eines Gueterbahnhofs sein).
     */
    public List<GleisTreffer> gleiseFuerBlock(String blockName) {
        List<GleisTreffer> l = new ArrayList<>();
        if (blockName == null) {
            return l;
        }
        for (Station s : stationen()) {
            for (Gleis g : s.getGleise()) {
                if (blockName.equals(g.getBlockName())) {
                    l.add(new GleisTreffer(s, g));
                }
            }
        }
        return l;
    }

    /** Ist der Block schon einem Gleis dieser Art zugeordnet (ausser {@code ausser})? */
    public boolean blockBelegt(String blockName, GleisArt art, Gleis ausser) {
        for (GleisTreffer t : gleiseFuerBlock(blockName)) {
            if (t.gleis() != ausser && t.gleis().getArt() == art) {
                return true;
            }
        }
        return false;
    }

    public java.util.Set<String> getItrainIgnoriert() {
        return itrainIgnoriert;
    }

    /** Die Station, die zu einem iTrain-Bahnhof gehört (verknüpft oder gleichnamig), sonst null. */
    public Station stationFuerItrain(String name) {
        for (Station s : stationen()) {
            if (name.equals(s.getItrainName())) {
                return s;
            }
        }
        for (Station s : stationen()) {
            if (s.getItrainName() == null && name.equalsIgnoreCase(s.getName())) {
                return s;
            }
        }
        return null;
    }

    /**
     * Heftet die in iTrain definierten Bahnhöfe an die Anlagen-Infrastruktur: je Bahnhof eine
     * Station (Art Bahnhof) mit einem Bahnsteig je Block (Gleisname = Blockname). Eine gleichnamige
     * vorhandene Station wird verknüpft und um fehlende Blöcke ergänzt. Blöcke, die schon ein
     * Bahnsteig einer anderen Station sind, werden übersprungen.
     *
     * @param daten     Bahnhofsname -> Blöcke
     * @param erzwingen auch vom Anwender gelöschte Bahnhöfe wieder anheften und bei schon verknüpften
     *                  Stationen fehlende Blöcke ergänzen
     * @return neu angelegte oder geänderte Stationen
     */
    public List<Station> itrainBahnhoefeAnheften(Map<String, List<String>> daten, boolean erzwingen) {
        return itrainBahnhoefeAnheften(daten, Map.of(), erzwingen);
    }

    public boolean isErsterPersonenTransferOffen() {
        return ersterPersonenTransferOffen;
    }

    public void setErsterPersonenTransferOffen(boolean offen) {
        this.ersterPersonenTransferOffen = offen;
    }

    public boolean isErsterGueterTransferOffen() {
        return ersterGueterTransferOffen;
    }

    public void setErsterGueterTransferOffen(boolean offen) {
        this.ersterGueterTransferOffen = offen;
    }

    public boolean isBahnhoefeEingefuegt() {
        return bahnhoefeEingefuegt;
    }

    public void setBahnhoefeEingefuegt(boolean eingefuegt) {
        this.bahnhoefeEingefuegt = eingefuegt;
    }

    /** iTrain-Bahnhofsname -> Vorgabe (nur bis zum Einfügen). */
    public Map<String, BahnhofVorgabe> getBahnhofVorgaben() {
        return bahnhofVorgaben;
    }

    /**
     * Fügt die iTrain-Bahnhöfe ein, falls das noch nicht geschehen ist (erstes „Anlage bearbeiten“, Anwender
     * 22.10.2026): heftet sie an, setzt Art und Ladegleise aus den Vorgaben des Einrichtungs-Dialogs und dockt für
     * Ladegleise an Bahnhof/Haltepunkt/Schattenbahnhof sofort den Güterbahnhof an.
     *
     * @return die eingefügten Stationen (leer, wenn schon eingefügt)
     */
    public List<Station> bahnhoefeEinfuegen(Map<String, List<String>> daten, Map<String, String> typen) {
        if (bahnhoefeEingefuegt) {
            return List.of();
        }
        bahnhoefeEingefuegt = true;
        List<Station> neu = itrainBahnhoefeAnheften(daten, typen, false);
        for (Station s : List.copyOf(neu)) {
            BahnhofVorgabe v = s.getItrainName() == null ? null : bahnhofVorgaben.get(s.getItrainName());
            if (v == null) {
                continue;
            }
            java.util.Map<String, GleisArt> wahl = new java.util.LinkedHashMap<>();
            java.util.Set<String> beide = new java.util.LinkedHashSet<>();
            for (Gleis g : s.getGleise()) {
                String b = g.getBlockName();
                wahl.put(b, v.artFuer(b));
                if (v.ladegleise().contains(b) && v.bahnsteige() != null && v.bahnsteige().contains(b)) {
                    beide.add(b);
                }
            }
            Station gbf = bahnhofEinstellen(s, v.name(), v.art(), wahl, beide);
            if (gbf != null && !neu.contains(gbf)) {
                neu.add(gbf);
            }
        }
        bahnhofVorgaben.clear();
        return neu;
    }

    /**
     * Art einer neu anzulegenden Station nach dem iTrain-Bahnhofstyp. iTrain 6.0.4 kennt fünf Typen
     * (itrain.jar, Enum mit SHADOW, PASSENGERS, CARGO, SHED, OTHER; Anwender 22.10.2026): shadow = Schattenbahnhof,
     * passengers = Personenbahnhof → Bahnhof, cargo = Güterbahnhof, shed = Schuppen und other = Sonstige → Anderes
     * (dort kein Ein-/Aussteigen und kein Umladen, bis der Anwender die Art wählt). Ohne Typ: Bahnhof.
     */
    public static ObjektArt artFuerItrainTyp(String typ) {
        if (typ == null) {
            return ObjektArt.BAHNHOF;
        }
        return switch (typ) {
            case "shadow" -> ObjektArt.SCHATTENBAHNHOF;
            case "cargo" -> ObjektArt.GUETERBAHNHOF;
            case "shed", "other" -> ObjektArt.ANDERES;
            default -> ObjektArt.BAHNHOF; // "passengers" und Unbekanntes
        };
    }

    /** Deutsche Bezeichnung eines iTrain-Bahnhofstyps wie in iTrain (Bahnhofs-Editor). */
    public static String itrainTypText(String typ) {
        if (typ == null) {
            return "";
        }
        return switch (typ) {
            case "shadow" -> "Schattenbahnhof";
            case "passengers" -> "Personenbahnhof";
            case "cargo" -> "Güterbahnhof";
            case "shed" -> "Schuppen";
            case "other" -> "Sonstige";
            default -> typ;
        };
    }

    /**
     * Wie {@link #itrainBahnhoefeAnheften(Map, boolean)}; neue Stationen bekommen die Art aus dem iTrain-Typ
     * ({@code typen}: Bahnhofsname -> Typ aus der .tcdz). Schon verknüpfte Stationen behalten ihre Art.
     */
    public List<Station> itrainBahnhoefeAnheften(Map<String, List<String>> daten, Map<String, String> typen,
                                                 boolean erzwingen) {
        List<Station> geaendert = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : daten.entrySet()) {
            String name = e.getKey();
            if (!erzwingen && itrainIgnoriert.contains(name)) {
                continue;
            }
            Station s = stationFuerItrain(name);
            boolean neu = false;
            boolean verknuepft = s != null && name.equals(s.getItrainName());
            if (s == null) {
                s = new Station(null, artFuerItrainTyp(typen.get(name)), freierName(name));
                Infrastruktur mitte = infrastruktur();
                platziereNeu(s, mitte.getX(), Math.max(10, mitte.getY() - RASTER_Y));
                neu = true;
            }
            if (verknuepft && !erzwingen) {
                continue;
            }
            s.setItrainName(name);
            itrainIgnoriert.remove(name);
            boolean geaendertHier = neu || !verknuepft;
            // Güterbahnhof: Ladegleise (er erlaubt zwar auch Bahnsteige, Anwender 22.10.2026)
            GleisArt art = s.getArt() != ObjektArt.GUETERBAHNHOF && s.getArt().erlaubt(GleisArt.BAHNSTEIG)
                    ? GleisArt.BAHNSTEIG : GleisArt.LADEGLEIS;
            for (String block : e.getValue()) {
                boolean schonDa = s.getGleise().stream().anyMatch(g -> block.equals(g.getBlockName()));
                if (schonDa || blockBelegt(block, art, null)) {
                    continue;
                }
                Gleis g = new Gleis(null, art);
                g.setBlockName(block);
                g.setName(block);
                s.getGleise().add(g);
                geaendertHier = true;
            }
            if (geaendertHier) {
                geaendert.add(s);
            }
        }
        return geaendert;
    }

    /**
     * Eine Station wurde umbenannt (Anwender 14.10.2026): Fahrtrichtungen der Bahnsteige, die ihren alten
     * Anzeigenamen tragen, samt Wartenden und laufenden Halten auf den neuen Namen umstellen.
     *
     * @return Zahl der geänderten Gleise
     */
    public int stationUmbenannt(String alt, String neu) {
        if (alt == null || neu == null || alt.equals(neu)) {
            return 0;
        }
        int n = 0;
        for (Station s : stationen()) {
            for (Gleis g : s.getGleise()) {
                if (g.richtungUmbenennen(alt, neu)) {
                    n++;
                }
            }
        }
        for (ZugZustand z : zuege.values()) {
            z.richtungUmbenennen(alt, neu);
        }
        return n;
    }

    /**
     * Beim Öffnen: Fahrtrichtungen, die weder eine Station noch ein Block sind, aber der Vorgabename einer Art
     * (z. B. "Schattenbahnhof") – gibt es genau eine Station dieser Art, war sie gemeint und wurde umbenannt,
     * bevor Umbenennungen nachgezogen wurden.
     */
    public int veralteteRichtungenNachziehen() {
        Set<String> bekannt = new HashSet<>(bloecke);
        stationen().forEach(s -> bekannt.add(s.getAnzeigeName()));
        int n = 0;
        for (ObjektArt art : ObjektArt.values()) {
            if (bekannt.contains(art.getLabel())) {
                continue;
            }
            List<Station> dieser = stationen().stream().filter(s -> s.getArt() == art).toList();
            if (dieser.size() == 1) {
                n += stationUmbenannt(art.getLabel(), dieser.get(0).getAnzeigeName());
            }
        }
        return n;
    }

    public void entferne(AnlagenObjekt objekt) {
        if (objekt instanceof Infrastruktur) {
            return;
        }
        if (objekt instanceof Station st && st.getItrainName() != null) {
            itrainIgnoriert.add(st.getItrainName());
        }
        for (Station s : stationen()) {
            if (objekt.getId().equals(s.getGehoertZu())) {
                s.setGehoertZu(null);
            }
        }
        objekte.remove(objekt);
        for (Station s : stationen()) {
            s.getEinzugsbereich().remove(objekt.getId());
        }
        for (Bereich m : bereiche()) {
            m.getStationen().remove(objekt.getId());
        }
        if (objekt instanceof Bereich b) {
            for (AnlagenObjekt o : objekte) {
                if (b.getId().equals(o.getReiterId())) {
                    o.setReiterId(null); // zurück in den ersten Reiter
                }
            }
        }
    }

    /** Legt einen Betrieb nach Vorlage an; Lagergroessen werden mit dem Mengenmassstab skaliert. */
    public Betrieb neuerBetrieb(BetriebVorlage vorlage, double x, double y) {
        Betrieb b = new Betrieb(null, freierName(vorlage.name()));
        b.setVorlageId(vorlage.id());
        for (Rezept r : vorlage.rezepte()) {
            b.getRezepte().add(r.kopie());
        }
        vorlage.eingangslager().forEach((g, k) -> b.getLagerEin().put(g, skaliert(k)));
        vorlage.ausgangslager().forEach((g, k) -> b.getLagerAus().put(g, skaliert(k)));
        b.setX(x);
        b.setY(y);
        objekte.add(b);
        return b;
    }

    private int skaliert(int kapazitaet) {
        return Math.max(10, (int) Math.round(kapazitaet * mengenMassstab));
    }

    public static final double RASTER_X = 240;
    public static final double RASTER_Y = 170;

    /** Zuletzt gemessene Größe je Objekt-Id (Breite, Höhe), gesetzt von der Zeichenfläche. */
    private static final Map<String, double[]> MASSE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void merkeMasse(String id, double breite, double hoehe) {
        MASSE.put(id, new double[]{breite, hoehe});
    }

    /** Abstand zwischen Kästen bei der Platzsuche. */
    private static final double LUECKE = 40;

    /** Größe eines Objekts: gemessen, sonst gezogene Breite bzw. Schätzung nach Art. */
    private double[] masse(AnlagenObjekt o) {
        double[] m = o != null ? MASSE.get(o.getId()) : null;
        if (m != null) {
            return m;
        }
        double b = 220;
        double h = 160;
        if (o instanceof Station) {
            b = 320;
            h = 180;
        } else if (o instanceof Ort) {
            b = 300;
            h = 230;
        } else if (o instanceof Betrieb) {
            b = 180;
            h = 170;
        }
        if (o != null && o.getBreite() > 0) {
            b = o.getBreite();
        }
        return new double[]{b, h};
    }

    /**
     * Freier Platz für ein neues Objekt möglichst nahe bei (x, y), ohne dass sich Kästen überdecken (Anwender
     * 09.11.2026: „neue Symbole nicht übereinander, je nach Verbindung nebeneinander“). Gesucht wird im 20-px-Raster
     * mit echten (gemessenen) Kastengrößen; bevorzugt in derselben Zeile rechts daneben, dann darunter/darüber.
     */
    public double[] freiePosition(double x, double y) {
        return freiePosition(x, y, null);
    }

    public double[] freiePosition(double x, double y, AnlagenObjekt neu) {
        double[] m = masse(neu);
        double maxX = Math.max(400, (flaecheBreite > 0 ? flaecheBreite : 1800) - m[0] - 10);
        double maxY = Math.max(300, (flaecheHoehe > 0 ? flaecheHoehe : 1100) - m[1] - 10);
        double x0 = Math.round(Math.max(10, Math.min(maxX, x)) / 20) * 20;
        double y0 = Math.round(Math.max(10, Math.min(maxY, y)) / 20) * 20;
        double bestK = Double.MAX_VALUE;
        double[] best = null;
        for (double py = 10; py <= maxY; py += 20) {
            for (double px = 10; px <= maxX; px += 20) {
                double dx = px - x0;
                double dy = py - y0;
                // nebeneinander bevorzugt: senkrecht dreifach, links etwas teurer als rechts
                double k = Math.abs(dx) * (dx < 0 ? 1.3 : 1) + Math.abs(dy) * 3;
                if (k >= bestK) {
                    continue;
                }
                if (platzFrei(px, py, m, neu)) {
                    bestK = k;
                    best = new double[]{px, py};
                }
            }
        }
        if (best == null) {
            // Fläche voll: rechts neben alles setzen
            double rechts = 10;
            for (AnlagenObjekt o : objekte) {
                if (o != neu) {
                    rechts = Math.max(rechts, o.getX() + masse(o)[0] + LUECKE);
                }
            }
            return new double[]{rechts, y0};
        }
        return best;
    }

    private boolean platzFrei(double x, double y, double[] m, AnlagenObjekt ausser) {
        Bereich reiter = ausser != null ? bereichVon(ausser) : null;
        for (AnlagenObjekt o : objekte) {
            if (o == ausser || bereichVon(o) != reiter) {
                continue; // nur Objekte im selben Reiter (Anwender 09.11.2026)
            }
            double[] om = masse(o);
            if (x < o.getX() + om[0] + LUECKE && o.getX() < x + m[0] + LUECKE
                    && y < o.getY() + om[1] + LUECKE && o.getY() < y + m[1] + LUECKE) {
                return false;
            }
        }
        return true;
    }

    /** Legt das Objekt an einer freien Stelle nahe (x, y) an und markiert es als neu. */
    public void platziereNeu(AnlagenObjekt o, double x, double y) {
        double[] p = freiePosition(x, y, o);
        o.setX(p[0]);
        o.setY(p[1]);
        o.setNeu(true);
        if (!objekte.contains(o)) {
            objekte.add(o);
        }
    }

    /** Stationen mit Ladegleisen, die das Objekt bedienen (Güter kommen nur darüber). */
    public List<Station> gueterStationenFuer(String objektId) {
        List<Station> l = new ArrayList<>();
        for (Station s : stationenFuer(objektId)) {
            if (!s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty() || s.getArt() == ObjektArt.GUETERBAHNHOF) {
                l.add(s);
            }
        }
        return l;
    }

    /**
     * Legt für einen Ort mit Güterbedarf einen Güterbahnhof an (verbunden mit dem Ort), falls ihn
     * noch keine Station mit Ladegleisen bedient. Gleise ordnet der Anwender danach zu.
     *
     * @return der neue Güterbahnhof oder null
     */
    public Station gueterbahnhofFuer(Ort ort) {
        boolean bedarf = ort.getBedarf().values().stream().anyMatch(v -> v > 0);
        if (!bedarf || !gueterStationenFuer(ort.getId()).isEmpty()) {
            return null;
        }
        // Erst wenn der Ort an einem Personenbahnhof hängt; der Güterbahnhof gehört dann zu diesem.
        Station bahnhof = null;
        for (Station s : stationenFuer(ort.getId())) {
            if (s.getArt() == ObjektArt.BAHNHOF) {
                bahnhof = s;
                break;
            }
        }
        if (bahnhof == null) {
            return null;
        }
        // Hat der Bahnhof schon einen Güterbahnhof, wird der Ort nur dort angeschlossen.
        for (Station s : stationen()) {
            if (bahnhof.getId().equals(s.getGehoertZu())) {
                if (!s.bedient(ort.getId())) {
                    s.getEinzugsbereich().add(ort.getId());
                }
                return null;
            }
        }
        Station gbf = new Station(null, ObjektArt.GUETERBAHNHOF, freierName(bahnhof.getAnzeigeName() + " Gbf"));
        gbf.setGehoertZu(bahnhof.getId());
        bahnhof.setBreiteAngleichen(true);
        gbf.getEinzugsbereich().add(ort.getId());
        platziereNeu(gbf, bahnhof.getX(), bahnhof.getY() + RASTER_Y);
        return gbf;
    }

    /**
     * Prüft alle Orte: Wer Güter braucht, an einem Personenbahnhof hängt und noch keinen Güterbahnhof
     * hat, bekommt einen (ohne Rückfrage, z.B. beim Öffnen einer Datei); er übernimmt die Gleise des
     * Bahnhofs als Ladegleise.
     *
     * @return neu angelegte Güterbahnhöfe
     */
    public List<Station> gueterbahnhoefeErgaenzen() {
        List<Station> neu = new ArrayList<>();
        for (Ort o : orte()) {
            Station gbf = gueterbahnhofFuer(o);
            if (gbf != null) {
                Station bahnhof = gehoertZu(gbf);
                if (bahnhof != null) {
                    for (Gleis g : bahnhof.getGleise()) {
                        String b = g.getBlockName();
                        if (b != null && !blockBelegt(b, GleisArt.LADEGLEIS, null)) {
                            Gleis lg = new Gleis(null, GleisArt.LADEGLEIS);
                            lg.setBlockName(b);
                            lg.setName(b);
                            gbf.getGleise().add(lg);
                        }
                    }
                }
                neu.add(gbf);
            }
        }
        return neu;
    }

    /**
     * Schattenbahnhof samt angedocktem Gbf (Anwender 09.11.2026): keine weiteren Symbole – Orte, Betriebe, Lager und
     * Übergaben werden gelöst, „Reisende aus angeschlossenem Ort“ entfällt. Reisende und Güter kommen je Gleis von
     * außerhalb.
     *
     * @return Texte „Objekt – Schattenbahnhof“ der gelösten Verbindungen
     */
    public List<String> schattenbahnhoefeBereinigen() {
        List<String> geloest = new ArrayList<>();
        for (Station s : stationen()) {
            if (!istAussenwelt(s)) {
                continue;
            }
            s.setReisendeAusOrt(false);
            for (String id : List.copyOf(s.getEinzugsbereich())) {
                AnlagenObjekt o = objekt(id);
                // Betriebe und Übergaben bleiben (Anwender 11.11.2026); Orte und Lager nur über Gleise
                if (o instanceof Betrieb || o instanceof Uebergabe) {
                    continue;
                }
                geloest.add((o != null ? o.getAnzeigeName() : id) + " – " + s.getAnzeigeName());
                s.getEinzugsbereich().remove(id);
            }
        }
        return geloest;
    }

    /**
     * Betriebe, Lager und Übergaben brauchen Ladegleise (Anwender 27.10.2026: eine Weberei am Hauptbahnhof bekam die
     * Wolle aus dem Lager am Hauptbahnhof Gbf nicht). Hängen sie an einem Bahnhof oder Haltepunkt ohne Ladegleise,
     * werden sie mit dessen Güterbahnhof verbunden; fehlt er, wird er angedockt.
     *
     * @return Namen der umgehängten Objekte mit dem Güterbahnhof („Weberei → Hauptbahnhof Gbf“)
     */
    public List<String> gueterObjekteZumGueterbahnhof() {
        List<String> umgehaengt = new ArrayList<>();
        for (Station s : List.copyOf(stationen())) {
            if (s.getArt() != ObjektArt.BAHNHOF && s.getArt() != ObjektArt.HALTEPUNKT
                    && s.getArt() != ObjektArt.SCHATTENBAHNHOF // Anwender 11.11.2026: Betriebe am SBf
                    || !s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty()) {
                continue;
            }
            List<String> gueterObjekte = new ArrayList<>();
            for (String id : s.getEinzugsbereich()) {
                AnlagenObjekt o = objekt(id);
                if (o instanceof Betrieb || o instanceof Lager || o instanceof Uebergabe) {
                    gueterObjekte.add(id);
                }
            }
            if (gueterObjekte.isEmpty()) {
                continue;
            }
            Station gbf = gueterbahnhofAndocken(s);
            for (String id : gueterObjekte) {
                s.getEinzugsbereich().remove(id);
                if (!gbf.bedient(id)) {
                    gbf.getEinzugsbereich().add(id);
                }
                umgehaengt.add(objekt(id).getAnzeigeName() + " → " + gbf.getAnzeigeName());
            }
        }
        return umgehaengt;
    }

    /**
     * Außenwelt: Schattenbahnhof selbst oder ein an ihn angedockter Güterbahnhof. Stellt Personen und
     * Güter in den je Gleis eingestellten Mengen bereit und nimmt alles ab.
     */
    public boolean istAussenwelt(Station s) {
        if (s.getArt() == ObjektArt.SCHATTENBAHNHOF) {
            return true;
        }
        Station p = gehoertZu(s);
        return p != null && p.getArt() == ObjektArt.SCHATTENBAHNHOF;
    }

    /**
     * Kommen und gehen die Reisenden dieses Schattenbahnhofs von bzw. nach außerhalb (Mengen je Gleis, alle steigen
     * aus)? Nein, wenn beim Schattenbahnhof „aus angeschlossenem Ort“ gewählt ist: dann wie ein normaler Bahnhof.
     */
    public boolean reisendeVonAussen(Station s) {
        if (!istAussenwelt(s)) {
            return false;
        }
        Station sb = s.getArt() == ObjektArt.SCHATTENBAHNHOF ? s : gehoertZu(s);
        return sb == null || !sb.isReisendeAusOrt();
    }

    /** Der an eine Station angedockte Güterbahnhof (oder null). */
    /**
     * Der Ort, mit dem eine Station verbunden ist; zählt den zugehörigen Bahnhof bzw. angedockten Güterbahnhof mit.
     * Je Bahnhof gibt es höchstens einen Ort (Anwender 27.10.2026): alles bezieht sich auf diese Verbindung.
     */
    public Ort ortAnStation(Station s) {
        List<Station> verbund = new ArrayList<>();
        verbund.add(s);
        Station p = gehoertZu(s);
        if (p != null) {
            verbund.add(p);
        }
        Station g = angedockterGueterbahnhof(s);
        if (g != null) {
            verbund.add(g);
        }
        for (Station x : verbund) {
            for (String id : x.getEinzugsbereich()) {
                if (objekt(id) instanceof Ort o) {
                    return o;
                }
            }
        }
        return null;
    }

    /** Darf der Ort mit der Station verbunden werden? Nur, wenn dort noch kein anderer Ort hängt. */
    public boolean ortErlaubt(Station s, Ort o) {
        Ort vorhanden = ortAnStation(s);
        return vorhanden == null || vorhanden == o;
    }

    public Station angedockterGueterbahnhof(Station s) {
        for (Station x : stationen()) {
            if (s.getId().equals(x.getGehoertZu())) {
                return x;
            }
        }
        return null;
    }

    /**
     * Dockt an einen (Schatten-)Bahnhof einen Güterbahnhof an, falls noch keiner da ist; er bekommt die
     * Blöcke der Station als Ladegleise.
     */
    public Station gueterbahnhofAndocken(Station s) {
        Station gbf = angedockterGueterbahnhof(s);
        if (gbf != null) {
            return gbf;
        }
        gbf = new Station(null, ObjektArt.GUETERBAHNHOF, freierName(s.getAnzeigeName() + " Gbf"));
        gbf.setGehoertZu(s.getId());
        s.setBreiteAngleichen(true);
        platziereNeu(gbf, s.getX(), s.getY() + RASTER_Y);
        for (Gleis g : s.getGleise()) {
            String b = g.getBlockName();
            if (b != null && !blockBelegt(b, GleisArt.LADEGLEIS, null)) {
                Gleis lg = new Gleis(null, GleisArt.LADEGLEIS);
                lg.setBlockName(b);
                lg.setName(b);
                gbf.getGleise().add(lg);
            }
        }
        return gbf;
    }

    /**
     * Ältere Schattenbahnhöfe mit Ladegleisen: die Ladegleise wandern in einen angedockten
     * Güterbahnhof; ihre Güterauswahl wird zum Angebot (10 Einheiten je Modellstunde).
     */
    public void schattenbahnhoefeUmstellen() {
        for (Station s : stationen()) {
            if (s.getArt() == ObjektArt.SCHATTENBAHNHOF) {
                ladegleiseAuslagern(s);
            }
        }
    }

    /**
     * Ladegleise eines Bahnhofs, Haltepunkts oder Schattenbahnhofs wandern in den angedockten Güterbahnhof
     * (Anwender 20.10.2026: wer im Bahnhof ein Gleis von Bahnsteig auf Ladegleis umstellt, bekommt zusätzlich einen
     * Güterbahnhof, der die Ladegleise aufnimmt). Fehlt der Güterbahnhof, wird er angelegt; bei einem Bahnhof oder
     * Haltepunkt übernimmt er dessen Einzugsbereich, damit die Güter dieselben Orte und Betriebe erreichen.
     *
     * @return der Güterbahnhof, der die Gleise aufgenommen hat, oder null (nichts zu tun)
     */
    public Station ladegleiseAuslagern(Station s) {
        ObjektArt art = s.getArt();
        if (art != ObjektArt.SCHATTENBAHNHOF && art != ObjektArt.BAHNHOF && art != ObjektArt.HALTEPUNKT) {
            return null;
        }
        List<Gleis> lade = s.gleiseDerArt(GleisArt.LADEGLEIS);
        if (lade.isEmpty()) {
            return null;
        }
        s.getGleise().removeAll(lade);
        Station gbf = angedockterGueterbahnhof(s);
        if (gbf == null) {
            gbf = new Station(null, ObjektArt.GUETERBAHNHOF, freierName(s.getAnzeigeName() + " Gbf"));
            gbf.setGehoertZu(s.getId());
            s.setBreiteAngleichen(true);
            platziereNeu(gbf, s.getX(), s.getY() + RASTER_Y);
            if (art != ObjektArt.SCHATTENBAHNHOF) {
                gbf.getEinzugsbereich().addAll(s.getEinzugsbereich());
            }
        }
        for (Gleis g : lade) {
            if (art == ObjektArt.SCHATTENBAHNHOF && g.getAngebot().isEmpty()) {
                g.getGueterAuswahl().forEach(id -> g.getAngebot().put(id, 10.0));
            }
            gbf.getGleise().add(g);
        }
        return gbf;
    }

    /**
     * Stellt einen Bahnhof ein (Verkehrs-Assistent, Anwender 27.10.2026): Name, Art und je Block die Gleisart
     * ({@code null} = „Vorerst nichts“: das Gleis wird entfernt, der Block ist wieder frei). Gleise im angedockten
     * Güterbahnhof zählen mit; Ladegleise eines Bahnhofs, Haltepunkts oder Schattenbahnhofs wandern danach in den
     * Güterbahnhof, der bei Bedarf angelegt wird (Bahnsteig und Ladegleis = Personen- und Güterbahnhof).
     *
     * @return der Güterbahnhof mit den Ladegleisen oder null
     */
    public Station bahnhofEinstellen(Station s, String name, ObjektArt art, Map<String, GleisArt> wahl) {
        return bahnhofEinstellen(s, name, art, wahl, java.util.Set.of());
    }

    /**
     * Wie oben; Blöcke in {@code beide} sind zugleich Bahnsteig und Ladegleis (Anwender 09.11.2026, v. a. im
     * Schattenbahnhof): der Bahnsteig bleibt in der Station, das Ladegleis kommt in den angedockten Güterbahnhof.
     */
    public Station bahnhofEinstellen(Station s, String name, ObjektArt art, Map<String, GleisArt> wahl,
            java.util.Set<String> beide) {
        if (name != null && !name.isBlank() && !name.trim().equals(s.getAnzeigeName())) {
            String alt = s.getAnzeigeName();
            s.setName(name.trim());
            stationUmbenannt(alt, s.getAnzeigeName());
        }
        if (art != null && art.istStation()) {
            s.setArt(art);
        }
        Station gbf = angedockterGueterbahnhof(s);
        java.util.Set<String> bloecke = new java.util.LinkedHashSet<>(wahl.keySet());
        bloecke.addAll(beide);
        for (String block : bloecke) {
            java.util.Set<GleisArt> soll = java.util.EnumSet.noneOf(GleisArt.class);
            if (beide.contains(block)) {
                soll.add(GleisArt.BAHNSTEIG);
                soll.add(GleisArt.LADEGLEIS);
            } else if (wahl.get(block) != null) {
                soll.add(wahl.get(block));
            }
            // vorhandene Gleise dieses Blocks in Station und Gbf einsammeln
            List<Gleis> vorhanden = new ArrayList<>();
            for (Station x : new Station[]{s, gbf}) {
                if (x == null) {
                    continue;
                }
                for (Gleis g : List.copyOf(x.getGleise())) {
                    if (block.equals(g.getBlockName())) {
                        vorhanden.add(g);
                        x.getGleise().remove(g);
                    }
                }
            }
            for (GleisArt ga : soll) {
                Gleis g = null;
                for (Gleis v : vorhanden) {
                    if (v.getArt() == ga) {
                        g = v;
                        break;
                    }
                }
                if (g == null && !vorhanden.isEmpty() && soll.size() == 1) {
                    g = vorhanden.get(0);
                    g.setArt(ga);
                }
                if (g == null) {
                    g = new Gleis(null, ga);
                    g.setBlockName(block);
                    g.setName(block);
                }
                vorhanden.remove(g);
                // Güterbahnhof: Ladegleise direkt hinein; sonst erst in die Station, ladegleiseAuslagern verschiebt
                if (ga == GleisArt.LADEGLEIS && gbf != null && s.getArt() != ObjektArt.GUETERBAHNHOF) {
                    gbf.getGleise().add(g);
                } else {
                    s.getGleise().add(g);
                }
            }
        }
        Station neuGbf = ladegleiseAuslagern(s);
        return neuGbf != null ? neuGbf : angedockterGueterbahnhof(s);
    }

    private static Gleis gleisMitBlock(Station s, String block) {
        for (Gleis g : s.getGleise()) {
            if (block.equals(g.getBlockName())) {
                return g;
            }
        }
        return null;
    }

    /**
     * Stationen, an denen ein Betrieb liegen kann (Anwender 09.11.2026: „einen neuen Betrieb kann ich überall
     * andocken“): alle außer Schattenbahnhof samt Gbf (dort nur Gleise) und „Anderes“; ein Bahnhof mit angedocktem
     * Güterbahnhof erscheint als dieser Güterbahnhof. Stationen mit Ladegleisen zuerst.
     */
    public List<Station> stationenFuerBetrieb() {
        List<Station> l = new ArrayList<>();
        for (Station s : stationen()) {
            if (istAussenwelt(s) || s.getArt() == ObjektArt.ANDERES || angedockterGueterbahnhof(s) != null) {
                continue;
            }
            l.add(s);
        }
        l.sort(java.util.Comparator.comparing((Station s) -> s.gleiseDerArt(GleisArt.LADEGLEIS).isEmpty() ? 1 : 0));
        return l;
    }

    /** Die Station, deren Ladegleise ein Betrieb an {@code s} nutzt: der angedockte Gbf, sonst {@code s}. */
    public Station gueterStation(Station s) {
        Station g = angedockterGueterbahnhof(s);
        return g != null ? g : s;
    }

    /** Blöcke, die an {@code s} Ladegleis werden können: Gleise des Bahnhofsverbunds und freie Blöcke. */
    public List<String> moeglicheLadegleise(Station s) {
        java.util.LinkedHashSet<String> b = new java.util.LinkedHashSet<>();
        for (Station x : new Station[]{s, gehoertZu(s), angedockterGueterbahnhof(s)}) {
            if (x != null) {
                x.getGleise().forEach(g -> b.add(g.getBlockName()));
            }
        }
        b.addAll(freieBloecke());
        b.removeIf(n -> n == null || blockBelegt(n, GleisArt.LADEGLEIS, null));
        return new ArrayList<>(b);
    }

    /**
     * Richtet einen Block als Ladegleis an {@code s} ein; bei Bahnhof/Haltepunkt kommt er in den (bei Bedarf neu
     * angedockten) Güterbahnhof.
     *
     * @return die Station mit dem neuen Ladegleis
     */
    public Station ladegleisEinrichten(Station s, String block) {
        Gleis g = new Gleis(null, GleisArt.LADEGLEIS);
        g.setBlockName(block);
        g.setName(block);
        Station ziel = gueterStation(s);
        if (ziel != s || (s.getArt() != ObjektArt.BAHNHOF && s.getArt() != ObjektArt.HALTEPUNKT)) {
            ziel.getGleise().add(g);
            return ziel;
        }
        s.getGleise().add(g);
        Station gbf = ladegleiseAuslagern(s);
        return gbf != null ? gbf : s;
    }

    /**
     * Bahnhöfe für die Zuganzeiger je Bahnhof (Anwender 09.11.2026): alle Stationen außer angedockten
     * Güterbahnhöfen (die gehören zum Bahnhof) und „Anderes“.
     */
    public List<Station> bahnhoefeFuerAnzeiger() {
        List<Station> l = new ArrayList<>();
        for (Station s : stationen()) {
            if (gehoertZu(s) == null && s.getArt() != ObjektArt.ANDERES) {
                l.add(s);
            }
        }
        l.sort(java.util.Comparator.comparing(Station::getAnzeigeName, String.CASE_INSENSITIVE_ORDER));
        return l;
    }

    /** Ids des Bahnhofsverbunds: die Station, ihr angedockter Güterbahnhof bzw. ihr Bahnhof. */
    public java.util.Set<String> verbund(Station s) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        ids.add(s.getId());
        Station g = angedockterGueterbahnhof(s);
        if (g != null) {
            ids.add(g.getId());
        }
        Station p = gehoertZu(s);
        if (p != null) {
            ids.add(p.getId());
        }
        return ids;
    }

    /** Der zugehörige Personenbahnhof eines Güterbahnhofs (oder null). */
    public Station gehoertZu(Station s) {
        return s.getGehoertZu() != null && objekt(s.getGehoertZu()) instanceof Station p ? p : null;
    }

    /** Eindeutiger Name: "Sägewerk", "Sägewerk 2", ... */
    public String freierName(String basis) {
        String name = basis;
        int n = 2;
        while (nameVergeben(name)) {
            name = basis + " " + n++;
        }
        return name;
    }

    private boolean nameVergeben(String name) {
        for (AnlagenObjekt o : objekte) {
            if (name.equals(o.getName())) {
                return true;
            }
        }
        return false;
    }
}
