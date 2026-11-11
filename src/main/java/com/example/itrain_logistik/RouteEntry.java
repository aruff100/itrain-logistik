package com.example.itrain_logistik;

import java.util.List;

/**
 * Eine iTrain-Zugroute ({@code control-items/train-routes/route}) mit den Bloecken, die sie
 * beruehrt, in der Reihenfolge ihres Auftretens. Grundlage der Linien im Echtzeit-Kern.
 */
public record RouteEntry(String name, List<String> blockNamen) {
}
