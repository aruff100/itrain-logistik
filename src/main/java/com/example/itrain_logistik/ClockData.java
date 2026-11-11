package com.example.itrain_logistik;

/**
 * Die Modellbahnuhr aus der .tcdz (Element {@code <clock>}), z.B.
 * {@code <clock day="thursday" time="18:05:10"><configuration factor="1"
 * start="05:00:00" end="01:00:00"/></clock>}. "timer" aus dem Projektauftrag
 * entspricht dem Attribut {@code time}.
 */
public record ClockData(String day, String time, String factor, String start, String end) {

    public static final ClockData EMPTY = new ClockData(null, null, null, null, null);
}
