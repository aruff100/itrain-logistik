package com.example.itrain_logistik;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Baut aus dem generischen {@link XmlNode}-Baum einer .tcdz (siehe
 * {@link TcdzReader}) die im Projektauftrag genannten Bereiche als
 * eigene, typisierte Modelle: clock, locomotives, wagons, blocks, trains,
 * control-state.
 * <p>
 * Tag-/Attributnamen anhand einer echten iTrain-6-Anlagendatei
 * verifiziert (Scheuerfeld_FX.tcdz): {@code <clock day="..." time="...">}
 * mit Kind {@code <configuration factor="..." start="..." end="..."/>};
 * {@code <locomotive name="..." type="...">} bzw. {@code <wagon name="..."
 * type="...">}, je mit Kind {@code <image file="..."/>} und optionalem Kind
 * {@code <description>...</description>}; {@code <block name="..." .../>};
 * {@code <train name="..."><composition>...}; sowie {@code
 * <control-state><blocks><block name="..."><train name="..."/>}.
 * <p>
 * An zwei echten Beispiel-Anlagen (Scheuerfeld_FX.tcdz,
 * Scheuerfeld_mc2_604.tcdz) wurden fuer {@code type} bei Lokomotiven nur
 * "steam"/"diesel" beobachtet, bei Wagen "coach" (alle Personenwagen) sowie
 * die Gueter-Einzelbuchstaben "e"/"g"/"i"/"t"/"u"/"z" - siehe
 * {@link #antriebsartFuerTcdzTyp} und {@link #wagenartFuerTcdzTyp}.
 */
public final class TcdzModelBuilder {

    private TcdzModelBuilder() {
    }

    public static LogistikSnapshot build(File sourceFile, XmlNode root) {
        ClockData clock = readClock(root.findChild("clock"));

        XmlNode controlItems = root.findChild("control-items");
        List<LocomotiveEntry> locomotives = new ArrayList<>();
        List<WagonEntry> wagons = new ArrayList<>();
        List<BlockEntry> blocks = new ArrayList<>();
        List<TrainEntry> trains = new ArrayList<>();
        if (controlItems != null) {
            XmlNode locomotivesNode = controlItems.findChild("locomotives");
            if (locomotivesNode != null) {
                for (XmlNode loco : locomotivesNode.getChildren()) {
                    LocomotiveEntry entry = new LocomotiveEntry(
                            loco.getAttribute("name"), loco.getAttribute("type"), imageFile(loco), description(loco));
                    voreinstellenAntriebsart(entry);
                    locomotives.add(entry);
                }
            }
            XmlNode wagonsNode = controlItems.findChild("wagons");
            if (wagonsNode != null) {
                for (XmlNode wagon : wagonsNode.getChildren()) {
                    WagonEntry entry = new WagonEntry(
                            wagon.getAttribute("name"), wagon.getAttribute("type"), imageFile(wagon), description(wagon));
                    voreinstellenWagenart(entry);
                    wagons.add(entry);
                }
            }
            XmlNode blocksNode = controlItems.findChild("blocks");
            if (blocksNode != null) {
                java.util.Map<String, Double> weichenLaenge = new java.util.HashMap<>();
                sammleWeichenLaengen(root, weichenLaenge);
                for (XmlNode block : blocksNode.getChildren()) {
                    BlockEntry be = new BlockEntry(block.getAttribute("name"), block.getAttribute("type"));
                    be.setWege(wege(block, weichenLaenge));
                    blocks.add(be);
                }
            }
            XmlNode trainsNode = controlItems.findChild("trains");
            if (trainsNode != null) {
                for (XmlNode train : trainsNode.getChildren()) {
                    trains.add(new TrainEntry(train.getAttribute("name"), readComposition(train, wagons)));
                }
            }
        }

        List<ControlStateBlockEntry> controlState = new ArrayList<>();
        XmlNode controlStateNode = root.findChild("control-state");
        if (controlStateNode != null) {
            XmlNode stateBlocks = controlStateNode.findChild("blocks");
            if (stateBlocks != null) {
                for (XmlNode block : stateBlocks.getChildren()) {
                    XmlNode trainNode = block.findChild("train");
                    controlState.add(new ControlStateBlockEntry(
                            block.getAttribute("name"), trainNode != null ? trainNode.getAttribute("name") : null));
                }
            }
        }

        List<RouteEntry> routes = new ArrayList<>();
        if (controlItems != null) {
            XmlNode routesNode = controlItems.findChild("train-routes");
            if (routesNode != null) {
                java.util.Set<String> blockNamen = new java.util.HashSet<>();
                for (BlockEntry b : blocks) {
                    blockNamen.add(b.getName());
                }
                for (XmlNode route : routesNode.getChildren()) {
                    List<String> bloecke = new ArrayList<>();
                    sammleBloecke(route, blockNamen, bloecke);
                    routes.add(new RouteEntry(route.getAttribute("name"), bloecke));
                }
            }
        }

        // In iTrain definierte Bahnhöfe (control-items/stations/station/blocks/block), geprüft an
        // Buero_N_Anlage.tcdz (11.10.2026).
        List<StationEntry> stations = new ArrayList<>();
        if (controlItems != null) {
            XmlNode stationsNode = controlItems.findChild("stations");
            if (stationsNode != null) {
                for (XmlNode station : stationsNode.getChildren()) {
                    List<String> bloecke = new ArrayList<>();
                    XmlNode blocksNode = station.findChild("blocks");
                    if (blocksNode != null) {
                        for (XmlNode b : blocksNode.getChildren()) {
                            String n = b.getAttribute("name");
                            if (n != null && !n.isBlank() && !bloecke.contains(n)) {
                                bloecke.add(n);
                            }
                        }
                    }
                    String name = station.getAttribute("name");
                    if (name != null && !name.isBlank()) {
                        stations.add(new StationEntry(name, station.getAttribute("type"), bloecke));
                    }
                }
            }
        }

        return new LogistikSnapshot(sourceFile, clock, locomotives, wagons, blocks, trains, controlState, routes,
                stations);
    }

    /**
     * Linien = iTrain-Zugrouten (Entscheidung des Anwenders vom 08.10.2026). Der innere Aufbau
     * einer {@code <route>} ist an keiner echten .tcdz geprueft - deshalb werden ohne Annahme
     * ueber Tag- oder Attributnamen alle Attributwerte unterhalb der Route gesammelt, die einem
     * bekannten Blocknamen entsprechen (Reihenfolge des Auftretens, ohne Doppelte). Der Name
     * der Route selbst wird uebersprungen.
     */
    private static void sammleBloecke(XmlNode knoten, java.util.Set<String> blockNamen, List<String> ziel) {
        for (XmlNode kind : knoten.getChildren()) {
            for (String wert : kind.getAttributes().values()) {
                if (blockNamen.contains(wert) && !ziel.contains(wert)) {
                    ziel.add(wert);
                }
            }
            sammleBloecke(kind, blockNamen, ziel);
        }
    }

    /** Längen der Weichen in cm: jedes {@code <turnout name>} mit Kind {@code <length>} (an Buero_N_Anlage geprüft). */
    private static void sammleWeichenLaengen(XmlNode knoten, java.util.Map<String, Double> ziel) {
        for (XmlNode k : knoten.getChildren()) {
            if ("turnout".equals(k.getTagName()) && k.getAttribute("name") != null) {
                XmlNode l = k.findChild("length");
                if (l != null && l.getTextContent() != null) {
                    try {
                        ziel.put(k.getAttribute("name"), Double.parseDouble(l.getTextContent().trim()));
                    } catch (NumberFormatException ex) {
                        // ohne Länge
                    }
                }
            }
            sammleWeichenLaengen(k, ziel);
        }
    }

    /**
     * Wege eines Blocks zu seinen Nachbarn (Anwender 10.11.2026): {@code <prev>} und {@code <next>} enthalten unter
     * {@code <side-block>} verschachtelte {@code <turnout name>} und am Ende {@code <block name choice>}.
     * Format siehe {@link BlockEntry#getWege()}.
     */
    private static String wege(XmlNode block, java.util.Map<String, Double> weichenLaenge) {
        List<String> wege = new ArrayList<>();
        for (String seite : new String[] {"prev", "next"}) {
            XmlNode s = block.findChild(seite);
            XmlNode sb = s == null ? null : s.findChild("side-block");
            if (sb != null) {
                wegeSammeln(sb, seite, new ArrayList<>(), weichenLaenge, wege);
            }
        }
        return String.join(" ;; ", wege);
    }

    private static void wegeSammeln(XmlNode knoten, String seite, List<String> weichen,
            java.util.Map<String, Double> weichenLaenge, List<String> ziel) {
        for (XmlNode k : knoten.getChildren()) {
            String name = k.getAttribute("name");
            if (name == null || name.isBlank()) {
                continue;
            }
            if ("block".equals(k.getTagName())) {
                StringBuilder b = new StringBuilder(seite).append(" | ")
                        .append("preferred".equals(k.getAttribute("choice")) ? "*" : "").append(name);
                for (String w : weichen) {
                    b.append(" | ").append(w);
                }
                ziel.add(b.toString());
            } else {
                List<String> weiter = new ArrayList<>(weichen);
                // dieselbe Weiche kann zweimal im Weg stehen (DKW): nur einmal zählen
                String eintrag = name + "~" + weichenLaenge.getOrDefault(name, -1.0);
                if (!weiter.contains(eintrag)) {
                    weiter.add(eintrag);
                }
                wegeSammeln(k, seite, weiter, weichenLaenge, ziel);
            }
        }
    }

    private static ClockData readClock(XmlNode clockNode) {
        if (clockNode == null) {
            return ClockData.EMPTY;
        }
        XmlNode configuration = clockNode.findChild("configuration");
        return new ClockData(
                clockNode.getAttribute("day"),
                clockNode.getAttribute("time"),
                configuration != null ? configuration.getAttribute("factor") : null,
                configuration != null ? configuration.getAttribute("start") : null,
                configuration != null ? configuration.getAttribute("end") : null);
    }

    /**
     * "Beim Import der Lokomotiven kannst Du die Antriebsart bei itrain
     * abschauen das ist 'type='" - uebernimmt, wo eindeutig erkennbar, das
     * iTrain-Attribut {@code type} als {@link LokAntriebsart}-Vorschlag in
     * das gleichnamige eigene Datenfeld. An echten iTrain-Dateien wurden
     * bisher nur "steam"/"diesel" beobachtet (siehe VehicleFieldsDialog im
     * Schwesterprojekt iTrain_import_export); "electric"/"battery"/
     * "hydrogen" sind zusaetzlich vorgesehen, falls iTrain sie andernorts
     * verwendet. Bleibt {@code type} leer oder ist der Wert nicht bekannt,
     * bleibt das Feld unbelegt - "Sollte nichts eingetragen sein waehlt der
     * Anwender selbst."
     */
    private static void voreinstellenAntriebsart(LocomotiveEntry entry) {
        LokAntriebsart antrieb = antriebsartFuerTcdzTyp(entry.getType());
        if (antrieb != null) {
            LogistikFelder.setValue(entry.getCustomFields(), LogistikFelder.ANTRIEBSART, antrieb.name());
        }
    }

    private static LokAntriebsart antriebsartFuerTcdzTyp(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toLowerCase()) {
            case "steam" -> LokAntriebsart.DAMPF;
            case "diesel" -> LokAntriebsart.DIESEL;
            case "electric" -> LokAntriebsart.ELEKTRISCH;
            case "battery" -> LokAntriebsart.BATTERIE;
            case "hydrogen", "fuel-cell", "fuelcell" -> LokAntriebsart.WASSERSTOFF;
            default -> null;
        };
    }

    /**
     * "Den Wagentyp kannst Du aus dem Feld type= ablesen. Nur wenn es leer
     * ist traegt der Anwender etwas ein." - uebernimmt, wo erkennbar, das
     * iTrain-Attribut {@code type} als {@link WagenArt}-Vorschlag. Gesichert
     * an echten .tcdz-Dateien sind "coach" (alle Personenwagen - ohne
     * erkennbare Unterscheidung nach Speise-/Salon-/Panorama-/Schlafwagen,
     * daher zunaechst der allgemeine Wert {@link WagenArt#PERSONEN}) sowie
     * die Gueter-Einzelbuchstaben e/g/i/t/u/z. Die uebrigen
     * UIC-Gattungsbuchstaben (f/h/k/l/o/r/s) sind nach dem Schema E-Z
     * plausibel ergaenzt, an den beiden mitgelieferten Beispieldateien
     * jedoch nicht bestaetigt - siehe Klassenkommentar. Bleibt {@code type}
     * leer oder ist er nicht zuordenbar, bleibt das Feld unbelegt.
     */
    private static void voreinstellenWagenart(WagonEntry entry) {
        WagenArt art = wagenartFuerTcdzTyp(entry.getType());
        if (art != null) {
            LogistikFelder.setValue(entry.getCustomFields(), LogistikFelder.WAGENART, art.name());
        }
    }

    private static WagenArt wagenartFuerTcdzTyp(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toLowerCase()) {
            case "coach" -> WagenArt.PERSONEN;
            case "e" -> WagenArt.OFFEN;
            case "f" -> WagenArt.OFFEN_SONDER;
            case "g" -> WagenArt.GEDECKT;
            case "h" -> WagenArt.GEDECKT_SONDER;
            case "i" -> WagenArt.KUEHL;
            case "k" -> WagenArt.FLACH_EINZELACHS;
            case "l" -> WagenArt.FLACH_EINZELACHS_SONDER;
            case "o" -> WagenArt.OFFEN_MEHRZWECK;
            case "r" -> WagenArt.FLACH_DREHGESTELL;
            case "s" -> WagenArt.FLACH_DREHGESTELL_SONDER;
            case "t" -> WagenArt.OEFFNUNGSFAEHIGES_DACH;
            case "u" -> WagenArt.SONDER;
            case "z" -> WagenArt.KESSEL;
            default -> null;
        };
    }

    private static String imageFile(XmlNode entry) {
        XmlNode image = entry.findChild("image");
        return image != null ? image.getAttribute("file") : null;
    }

    /** Liest das optionale Kindelement {@code <description>...</description>} als reinen Text. */
    private static String description(XmlNode entry) {
        XmlNode description = entry.findChild("description");
        return description != null ? description.getTextContent() : null;
    }

    /**
     * Liest die Zusammenstellung. Eine „Wagenliste“ (anonymer Eintrag {@code <item length>} ohne eigenen Wagen)
     * wird als eigener Wagen „Wagenliste &lt;Zug&gt;“ vom Typ {@link WagonEntry#TYP_WAGENLISTE} geführt, damit sie
     * in Zug und Wagenfenster erscheint und Plätze bzw. Kapazität bekommen kann (Anwender 10.11.2026).
     */
    private static List<TrainCompositionItem> readComposition(XmlNode trainNode, List<WagonEntry> wagons) {
        int listen = 0;
        List<TrainCompositionItem> items = new ArrayList<>();
        XmlNode composition = trainNode.findChild("composition");
        if (composition == null) {
            return items;
        }
        for (XmlNode item : composition.getChildren()) {
            if ("locomotive".equals(item.getTagName())) {
                items.add(new TrainCompositionItem(TrainCompositionItem.Kind.LOCOMOTIVE,
                        item.getAttribute("name"), "true".equals(item.getAttribute("main")),
                        item.getAttribute("direction")));
            } else if ("wagon".equals(item.getTagName())) {
                items.add(new TrainCompositionItem(TrainCompositionItem.Kind.WAGON,
                        item.getAttribute("name"), false, item.getAttribute("direction")));
            } else if ("item".equals(item.getTagName())) {
                listen++;
                String name = "Wagenliste " + trainNode.getAttribute("name") + (listen > 1 ? " " + listen : "");
                String laenge = item.getAttribute("length");
                if (wagons.stream().noneMatch(w -> name.equals(w.getName()))) {
                    WagonEntry liste = new WagonEntry(name, WagonEntry.TYP_WAGENLISTE, null,
                            laenge == null || laenge.isBlank() ? null : laenge + " cm");
                    LogistikFelder.setValue(liste.getCustomFields(), LogistikFelder.BEHAELTER, "true");
                    wagons.add(liste);
                }
                items.add(new TrainCompositionItem(TrainCompositionItem.Kind.WAGON, name, false, null));
            }
        }
        return items;
    }
}
