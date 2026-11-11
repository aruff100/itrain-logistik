package com.example.itrain_logistik;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;

/**
 * Warnton des Modul-Anzeigers, wenn ein Zug in den Block einfährt (Anwender 11.11.2026, Ausstellungen:
 * ein Handy liegt am Modul und klingelt). Die Töne werden berechnet, keine Tondateien:
 * Klingel (elektrische Wecker-Klingel), Glocke (drei Glockenschläge wie ein Läutewerk), Hupe (Zweiklang-Typhon).
 * Dieselben Rezepte stehen als JavaScript in {@link #js()} für die Browser-Seite.
 */
public final class Signalton {

    public static final String AUS = "aus";
    public static final String KLINGEL = "klingel";
    public static final String GLOCKE = "glocke";
    public static final String HUPE = "hupe";
    public static final List<String> ARTEN = List.of(AUS, KLINGEL, GLOCKE, HUPE);

    private static final float RATE = 44100f;

    private Signalton() { }

    /** Anzeigename (deutsch, wird übersetzt). */
    public static String name(String art) {
        return switch (art == null ? AUS : art) {
            case KLINGEL -> "Klingel";
            case GLOCKE -> "Glocke";
            case HUPE -> "Hupe";
            default -> "Ton aus";
        };
    }

    /** Spielt den Ton in einem eigenen Faden (blockiert die Oberfläche nicht). */
    public static void spielen(String art) {
        if (art == null || AUS.equals(art)) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                byte[] pcm = pcm(art);
                AudioFormat f = new AudioFormat(RATE, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(f)) {
                    line.open(f);
                    line.start();
                    line.write(pcm, 0, pcm.length);
                    line.drain();
                }
            } catch (Exception e) {
                System.err.println("Signalton: " + e.getMessage());
            }
        }, "Signalton");
        t.setDaemon(true);
        t.start();
    }

    /** 16-Bit-PCM, mono, 44,1 kHz. */
    static byte[] pcm(String art) {
        double dauer = switch (art) {
            case KLINGEL -> 1.6;
            case GLOCKE -> 3.2;
            default -> 1.7;
        };
        int n = (int) (dauer * RATE);
        double[] s = new double[n];
        switch (art) {
            case KLINGEL -> {
                // Klöppel schlägt 18-mal je Sekunde an eine kleine Schale (Teiltöne 2000/2900/4100 Hz)
                for (double t0 = 0; t0 < dauer - 0.15; t0 += 1 / 18.0) {
                    schlag(s, t0, new double[]{2000, 2900, 4100}, new double[]{1, 0.6, 0.3}, 0.09, 0.5);
                }
            }
            case GLOCKE -> {
                // drei Glockenschläge, unharmonische Teiltöne einer Glocke
                for (int i = 0; i < 3; i++) {
                    schlag(s, i * 0.9, new double[]{620, 1240, 1480, 1860, 2600},
                            new double[]{1, 0.55, 0.4, 0.3, 0.15}, 1.4, 0.55);
                }
            }
            default -> {
                // Zweiklang-Hupe: zwei Stöße, je zwei Sägezähne (ca. 370 und 466 Hz), weich ein- und ausgeblendet
                hupe(s, 0.0, 0.55, 370);
                hupe(s, 0.75, 0.9, 466);
            }
        }
        double max = 1e-9;
        for (double v : s) {
            max = Math.max(max, Math.abs(v));
        }
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int v = (int) (s[i] / max * 0.8 * 32767);
            b[2 * i] = (byte) v;
            b[2 * i + 1] = (byte) (v >> 8);
        }
        return b;
    }

    private static void schlag(double[] s, double t0, double[] f, double[] a, double abkling, double lautst) {
        int start = (int) (t0 * RATE);
        int laenge = (int) (abkling * 5 * RATE);
        for (int i = 0; i < laenge && start + i < s.length; i++) {
            double t = i / RATE;
            double h = Math.exp(-t / abkling) * Math.min(1, t / 0.002);
            double v = 0;
            for (int k = 0; k < f.length; k++) {
                v += a[k] * Math.sin(2 * Math.PI * f[k] * t) * Math.exp(-t * k * 0.8);
            }
            s[start + i] += v * h * lautst;
        }
    }

    private static void hupe(double[] s, double t0, double dauer, double f) {
        int start = (int) (t0 * RATE);
        int laenge = (int) (dauer * RATE);
        double tief = 0;
        for (int i = 0; i < laenge && start + i < s.length; i++) {
            double t = i / RATE;
            double h = Math.min(1, t / 0.04) * Math.min(1, (dauer - t) / 0.08);
            double v = saege(f * t) + 0.8 * saege(f * 1.26 * t);
            tief += (v - tief) * 0.12; // einfacher Tiefpass, nimmt die Schärfe
            s[start + i] += tief * h;
        }
    }

    private static double saege(double phase) {
        return 2 * (phase - Math.floor(phase + 0.5));
    }

    /**
     * JavaScript für die Browser-Seite: Funktion {@code signalton(art)} mit WebAudio. Der Browser lässt Töne erst nach
     * einem Antippen zu; die Seite ruft deshalb {@code tonFreigeben()} beim Tippen auf den Ton-Knopf auf.
     */
    static String js() {
        return "let AC=null;function tonFreigeben(){try{if(!AC)AC=new (window.AudioContext||window.webkitAudioContext)();"
                + "if(AC.state==='suspended')AC.resume();}catch(x){}}"
                + "function schlag(t0,fs,as,ab,l){const g=AC.createGain();g.connect(AC.destination);"
                + "g.gain.setValueAtTime(0,t0);g.gain.linearRampToValueAtTime(l,t0+0.002);"
                + "g.gain.exponentialRampToValueAtTime(0.0001,t0+ab*5);"
                + "fs.forEach((f,k)=>{const o=AC.createOscillator();const kg=AC.createGain();o.frequency.value=f;"
                + "kg.gain.setValueAtTime(as[k],t0);kg.gain.exponentialRampToValueAtTime(0.0001,t0+ab*5/(1+k*0.8));"
                + "o.connect(kg);kg.connect(g);o.start(t0);o.stop(t0+ab*5);});}"
                + "function hupe(t0,d,f){const g=AC.createGain();const lp=AC.createBiquadFilter();lp.type='lowpass';"
                + "lp.frequency.value=1400;lp.connect(g);g.connect(AC.destination);g.gain.setValueAtTime(0,t0);"
                + "g.gain.linearRampToValueAtTime(0.25,t0+0.04);g.gain.setValueAtTime(0.25,t0+d-0.08);"
                + "g.gain.linearRampToValueAtTime(0,t0+d);[f,f*1.26].forEach(x=>{const o=AC.createOscillator();"
                + "o.type='sawtooth';o.frequency.value=x;o.connect(lp);o.start(t0);o.stop(t0+d);});}"
                + "function signalton(art){if(!AC||art==='aus')return;if(AC.state==='suspended')AC.resume();"
                + "const t=AC.currentTime+0.05;"
                + "if(art==='klingel'){for(let x=0;x<1.45;x+=1/18)schlag(t+x,[2000,2900,4100],[1,0.6,0.3],0.09,0.12);}"
                + "else if(art==='glocke'){for(let i=0;i<3;i++)schlag(t+i*0.9,[620,1240,1480,1860,2600],"
                + "[1,0.55,0.4,0.3,0.15],1.4,0.3);}"
                + "else if(art==='hupe'){hupe(t,0.55,370);hupe(t+0.75,0.9,466);}}";
    }
}
