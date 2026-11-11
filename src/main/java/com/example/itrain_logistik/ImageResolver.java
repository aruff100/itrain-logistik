package com.example.itrain_logistik;

import java.io.File;

/**
 * Loest die in der .tcdz relativ gespeicherten Bildpfade (z.B.
 * {@code "iTrain/images/Eigene/Dampf/BR91.png"}) zu tatsaechlichen Dateien
 * auf. iTrain speichert diese Pfade relativ zum ELTERNORDNER seines eigenen
 * Datenordners "iTrain" (verifiziert: bei einer echten Installation liegt
 * das referenzierte Bild unter {@code <Elternordner>\iTrain\images\...}) -
 * deshalb wird ausgehend vom "Anfangsordner fuer Layout-Dateien" (Reiter
 * "Pfade", ueblicherweise {@code <...>\iTrain\layouts}) die naechste
 * Vorfahren-Ebene namens "iTrain" gesucht und deren Elternordner als
 * Basis genommen, statt eine feste Ordnertiefe anzunehmen.
 * <p>
 * Dateiformat wird nicht anhand der Endung entschieden - {@link
 * javafx.scene.image.Image} erkennt PNG/JPEG/GIF/BMP selbst am Inhalt, das
 * deckt "mehrere Dateiformate" aus dem Projektauftrag bereits ab.
 */
public final class ImageResolver {

    private ImageResolver() {
    }

    /** Liefert die aufgeloeste Bilddatei, oder {@code null}, wenn sie nicht gefunden werden konnte. */
    public static File resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        File dataRoot = findDataRoot();
        if (dataRoot == null) {
            return null;
        }
        File resolved = new File(dataRoot, relativePath.replace('/', File.separatorChar));
        return resolved.isFile() ? resolved : null;
    }

    private static File findDataRoot() {
        File dir = new File(AppSettings.getInstance().getLayoutFilesDirectory());
        while (dir != null) {
            if ("iTrain".equalsIgnoreCase(dir.getName())) {
                return dir.getParentFile();
            }
            dir = dir.getParentFile();
        }
        return null;
    }
}
