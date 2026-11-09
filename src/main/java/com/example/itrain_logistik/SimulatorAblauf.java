package com.example.itrain_logistik;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aufgezeichneter Ablauf des Simulators (Knopf "Aufzeichnen"): je Drag&Drop ein {@link Schritt} mit dem
 * Zeitpunkt seit Beginn der Aufzeichnung. Gespeichert neben der .logz als
 * {@code <basisname>.simulator-ablauf.xml}; "Vorgang starten" zeigt, bearbeitet und spielt ihn ab.
 */
public final class SimulatorAblauf {

    /** Ein Vorgang: Start (Sekunden nach Ablaufbeginn), Zug, Block und Einfahrseite ("links"/"rechts"). */
    public static final class Schritt {
        private double start;
        private String zug;
        private String block;
        private String seite;

        public Schritt(double start, String zug, String block, String seite) {
            this.start = start;
            this.zug = zug;
            this.block = block;
            this.seite = seite;
        }

        public Schritt kopie() {
            return new Schritt(start, zug, block, seite);
        }

        public double getStart() {
            return start;
        }

        public void setStart(double start) {
            this.start = Math.max(0, start);
        }

        public String getZug() {
            return zug;
        }

        public void setZug(String zug) {
            this.zug = zug;
        }

        public String getBlock() {
            return block;
        }

        public void setBlock(String block) {
            this.block = block;
        }

        public String getSeite() {
            return seite;
        }

        public void setSeite(String seite) {
            this.seite = seite;
        }
    }

    public static final String LINKS = "links";
    public static final String RECHTS = "rechts";

    private SimulatorAblauf() {
    }

    public static File dateiFuer(File logistikDatei) {
        String name = logistikDatei.getName();
        int punkt = name.lastIndexOf('.');
        String basis = punkt > 0 ? name.substring(0, punkt) : name;
        return new File(logistikDatei.getParentFile(), basis + ".simulator-ablauf.xml");
    }

    public static List<Schritt> laden(File datei) {
        List<Schritt> l = new ArrayList<>();
        if (datei == null || !datei.isFile()) {
            return l;
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setExpandEntityReferences(false);
            Document d = f.newDocumentBuilder().parse(datei);
            NodeList nodes = d.getElementsByTagName("vorgang");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                double start;
                try {
                    start = Double.parseDouble(e.getAttribute("start"));
                } catch (NumberFormatException ex) {
                    start = 0;
                }
                String seite = RECHTS.equals(e.getAttribute("seite")) ? RECHTS : LINKS;
                l.add(new Schritt(start, e.getAttribute("zug"), e.getAttribute("block"), seite));
            }
        } catch (Exception ex) {
            // beschädigte Datei: leerer Ablauf
        }
        return l;
    }

    public static void speichern(File datei, List<Schritt> schritte) throws IOException {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<simulator-ablauf>\n");
        for (Schritt s : schritte) {
            sb.append("  <vorgang start=\"").append(String.format(Locale.ROOT, "%.1f", s.getStart()))
                    .append("\" zug=\"").append(esc(s.getZug()))
                    .append("\" block=\"").append(esc(s.getBlock()))
                    .append("\" seite=\"").append(esc(s.getSeite())).append("\"/>\n");
        }
        sb.append("</simulator-ablauf>\n");
        Files.writeString(datei.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
