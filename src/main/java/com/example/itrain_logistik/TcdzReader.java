package com.example.itrain_logistik;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Liest iTrain-.tcd/.tcdz-Dateien lesend in einen generischen
 * {@link XmlNode}-Baum ein - gleiches Lade-Vorgehen wie
 * {@code TcdDocument.load} in iTrain_import_export, hier ohne die
 * Speicher-Seite: iTrain-Logistik schreibt niemals in die .tcdz zurueck,
 * sondern nur in die eigene Logistik-Datei (siehe {@link LogistikDocument}).
 */
public final class TcdzReader {

    private TcdzReader() {
    }

    public static XmlNode load(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document document;
        if (isZipFile(file)) {
            try (ZipFile zipFile = new ZipFile(file)) {
                List<ZipEntry> entries = new ArrayList<>();
                Enumeration<? extends ZipEntry> e = zipFile.entries();
                ZipEntry tcdEntry = null;
                while (e.hasMoreElements()) {
                    ZipEntry entry = e.nextElement();
                    entries.add(entry);
                    if (entry.getName().toLowerCase().endsWith(".tcd")) {
                        tcdEntry = entry;
                    }
                }
                if (tcdEntry == null && entries.size() == 1) {
                    tcdEntry = entries.get(0);
                }
                if (tcdEntry == null) {
                    throw new IOException("Im ZIP-Archiv \"" + file.getName() + "\" wurde keine .tcd-Datei gefunden.");
                }
                try (InputStream in = zipFile.getInputStream(tcdEntry)) {
                    document = builder.parse(in);
                }
            }
        } else {
            document = builder.parse(file);
        }
        document.getDocumentElement().normalize();
        return toXmlNode(document.getDocumentElement());
    }

    private static boolean isZipFile(File file) {
        String lowerName = file.getName().toLowerCase();
        if (lowerName.endsWith(".tcdz")) {
            return true;
        }
        if (lowerName.endsWith(".tcd")) {
            return false;
        }
        return looksLikeZip(file);
    }

    private static boolean looksLikeZip(File file) {
        byte[] header = new byte[2];
        try (InputStream in = new FileInputStream(file)) {
            if (in.read(header) < 2) {
                return false;
            }
        } catch (IOException ex) {
            return false;
        }
        return header[0] == 0x50 && header[1] == 0x4B;
    }

    private static XmlNode toXmlNode(Element element) {
        XmlNode node = new XmlNode(element.getTagName());

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            node.setAttribute(attr.getName(), attr.getValue());
        }

        StringBuilder text = null;
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                node.getChildren().add(toXmlNode((Element) child));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String value = ((Text) child).getData();
                if (!value.isBlank()) {
                    if (text == null) {
                        text = new StringBuilder();
                    }
                    text.append(value);
                }
            }
        }
        if (text != null) {
            node.setTextContent(text.toString().trim());
        }
        return node;
    }
}
