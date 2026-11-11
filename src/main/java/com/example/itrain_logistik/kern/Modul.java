package com.example.itrain_logistik.kern;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Modul (Anwender 09.11.2026): ein Teil der Anlage, der wie ein Bahnhof an der Anlagen-Infrastruktur hängt. Ihm
 * gehören Gleise (iTrain-Blöcke der Strecke im Modul) und Stationen; die Stationen hängen dann nicht mehr an der
 * Infrastruktur, sondern am Modul (mit allem, was an ihnen hängt). Ein Modul kann im Browser gezeigt werden, auf
 * einem anderen Rechner oder auf Handy/Tablet – nur lesend, die Daten bleiben auf dem Hauptrechner.
 * Für den Kern ist es nur Gliederung: kein Austausch von Gütern oder Reisenden.
 */
public final class Modul extends Bereich {

    private final Set<String> bloecke = new LinkedHashSet<>();
    private boolean imBrowser = true;

    public Modul(String id, String name) {
        super(id, ObjektArt.MODUL, name);
    }

    /** Blöcke (Strecke) des Moduls, die keinem Gleis einer Station gehören. */
    public Set<String> getBloecke() {
        return bloecke;
    }

    /** Im Browser zeigen (Seite /modul/&lt;Name&gt; des eingebauten Webservers). */
    public boolean isImBrowser() {
        return imBrowser;
    }

    public void setImBrowser(boolean imBrowser) {
        this.imBrowser = imBrowser;
    }
}
