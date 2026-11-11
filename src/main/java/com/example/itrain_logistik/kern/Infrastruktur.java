package com.example.itrain_logistik.kern;

/**
 * Die Anlagen-Infrastruktur: genau ein fester Knoten in der Mitte der Uebersicht, mit dem alles
 * verbunden ist. Sie "enthaelt" alle iTrain-Bloecke, die keinem Gleis einer Station zugeordnet
 * sind ({@link Anlage#freieBloecke()}); sobald ein Block einem Gleis zugeordnet wird, verschwindet
 * er hier. Im besten Fall bleiben nur Streckenbloecke uebrig. Nicht loeschbar.
 */
public final class Infrastruktur extends AnlagenObjekt {

    public Infrastruktur(String id) {
        super(id, ObjektArt.INFRASTRUKTUR, "Anlagen-Infrastruktur");
    }
}
