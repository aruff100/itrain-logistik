package com.example.itrain_logistik;

import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uhr als eigenes Fenster (Anwender 11.11.2026): digital oder analog als Bahnhofsuhr, umschaltbar; die Anzeige wächst
 * mit dem Fenster (Schrift bzw. Zifferblatt füllen die Fläche). Rechtsklick bzw. Knopf ⚙: Analog/Digital, Sekunden,
 * Farben… (Hintergrund, Zifferblatt, Zeiger/Ziffern, Sekundenzeiger), Standardfarben. Gemerkt in {@link AppSettings}
 * (Schlüssel „uhr.*“). Die Zeit kommt aus der gemeinsamen {@link LaufendeUhr}; dieselbe Uhr gibt es im Browser
 * ({@code /uhr} der Browser Logistik, {@link #seite()}).
 */
public final class UhrWindow {

    /** Vorgaben: dunkler Hintergrund, weißes Zifferblatt, schwarze Zeiger, roter Sekundenzeiger. */
    static final Map<String, String> STANDARD = new LinkedHashMap<>();

    static {
        STANDARD.put("hintergrund", "#3a3d42");
        STANDARD.put("blatt", "#ffffff");
        STANDARD.put("zeiger", "#111111");
        STANDARD.put("sekunde", "#d42a2a");
        STANDARD.put("ziffern", "#ffffff");
    }

    private static Stage stage;
    private static AnimationTimer timer;

    private UhrWindow() {
    }

    static String farbe(String name) {
        return AppSettings.getInstance().getUhr("farbe." + name, STANDARD.get(name));
    }

    static boolean analog() {
        return "analog".equals(AppSettings.getInstance().getUhr("art", "digital"));
    }

    public static void show(Stage owner, boolean showSeconds) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            stage.requestFocus();
            return;
        }
        stage = new Stage();
        stage.setTitle("Uhr");
        Canvas c = new Canvas();
        Pane root = new Pane(c);
        c.widthProperty().bind(root.widthProperty());
        c.heightProperty().bind(root.heightProperty());
        Label zahnrad = new Label("⚙");
        zahnrad.setStyle("-fx-font-size: 16px; -fx-text-fill: rgba(255,255,255,0.55); -fx-cursor: hand;");
        zahnrad.layoutXProperty().bind(root.widthProperty().subtract(24));
        zahnrad.setLayoutY(4);
        root.getChildren().add(zahnrad);
        ContextMenu menue = menue();
        root.setOnContextMenuRequested(e -> menue.show(root, e.getScreenX(), e.getScreenY()));
        zahnrad.setOnMouseClicked(e -> menue.show(zahnrad, e.getScreenX(), e.getScreenY()));
        Fenster.szene(stage, root, 320, 200);
        WindowState.apply(stage, "uhr", 320, 200);
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                zeichne(c.getGraphicsContext2D(), c.getWidth(), c.getHeight());
            }
        };
        stage.setOnShown(e -> timer.start());
        stage.setOnHidden(e -> {
            timer.stop();
            stage = null;
        });
        stage.show();
    }

    private static ContextMenu menue() {
        AppSettings st = AppSettings.getInstance();
        ToggleGroup art = new ToggleGroup();
        RadioMenuItem digital = new RadioMenuItem("Digital");
        RadioMenuItem analog = new RadioMenuItem("Analog (Bahnhofsuhr)");
        digital.setToggleGroup(art);
        analog.setToggleGroup(art);
        (analog() ? analog : digital).setSelected(true);
        digital.setOnAction(e -> st.setUhr("art", "digital"));
        analog.setOnAction(e -> st.setUhr("art", "analog"));
        CheckMenuItem sek = new CheckMenuItem("Sekunden anzeigen");
        sek.setSelected(st.getShowClockSeconds());
        sek.setOnAction(e -> st.setShowClockSeconds(sek.isSelected()));
        MenuItem farben = new MenuItem("Farben...");
        farben.setOnAction(e -> farbenDialog());
        MenuItem standard = new MenuItem("Standardfarben");
        standard.setOnAction(e -> STANDARD.keySet().forEach(k -> st.setUhr("farbe." + k, STANDARD.get(k))));
        return new ContextMenu(digital, analog, MainController.trenn3d(), sek, MainController.trenn3d(), farben, standard);
    }

    private static void farbenDialog() {
        AppSettings st = AppSettings.getInstance();
        Dialog<ButtonType> d = new Dialog<>();
        d.initOwner(stage);
        d.setTitle("Farben der Uhr");
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        String[][] felder = {{"hintergrund", "Hintergrund"}, {"blatt", "Zifferblatt (analog)"},
                {"zeiger", "Zeiger und Striche (analog)"}, {"sekunde", "Sekundenzeiger (analog)"},
                {"ziffern", "Ziffern (digital)"}};
        int z = 0;
        for (String[] f : felder) {
            ColorPicker p = new ColorPicker(Color.web(farbe(f[0])));
            Symbole.ohneHex(p);
            // sofort wirksam, die Uhr läuft weiter
            p.valueProperty().addListener((o, a, b) -> st.setUhr("farbe." + f[0], hex(b)));
            g.addRow(z++, new Label(f[1]), p);
        }
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        d.show();
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
                Math.round(c.getBlue() * 255));
    }

    private static void zeichne(GraphicsContext g, double w, double h) {
        g.setFill(Color.web(farbe("hintergrund")));
        g.fillRect(0, 0, w, h);
        LocalTime t = LaufendeUhr.aktuelleZeit();
        boolean sek = AppSettings.getInstance().getShowClockSeconds();
        if (analog()) {
            analogZeichnen(g, w, h, t, sek);
        } else {
            String text = t == null ? "--:--" : t.format(java.time.format.DateTimeFormatter.ofPattern(sek ? "HH:mm:ss"
                    : "HH:mm"));
            // Schrift so groß, dass der Text die Fläche füllt (Anwender 11.11.2026)
            Text mass = new Text(sek ? "00:00:00" : "00:00");
            mass.setFont(Font.font("Consolas", FontWeight.BOLD, 100));
            double fb = mass.getLayoutBounds().getWidth();
            double fh = mass.getLayoutBounds().getHeight();
            double groesse = Math.max(8, 100 * Math.min(w * 0.9 / fb, h * 0.8 / fh));
            g.setFont(Font.font("Consolas", FontWeight.BOLD, groesse));
            g.setFill(Color.web(farbe("ziffern")));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(text, w / 2, h / 2);
        }
    }

    /** Bahnhofsuhr: Minutenstriche, kräftige Stundenbalken, Balkenzeiger, schlanker Sekundenzeiger. */
    static void analogZeichnen(GraphicsContext g, double w, double h, LocalTime t, boolean sek) {
        double r = Math.min(w, h) / 2 * 0.92;
        double cx = w / 2;
        double cy = h / 2;
        Color zeiger = Color.web(farbe("zeiger"));
        g.setFill(Color.web("#b9bcc0"));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        double ri = r * 0.93;
        g.setFill(Color.web(farbe("blatt")));
        g.fillOval(cx - ri, cy - ri, 2 * ri, 2 * ri);
        g.setStroke(zeiger);
        g.setLineCap(StrokeLineCap.BUTT);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6);
            boolean stunde = i % 5 == 0;
            double innen = stunde ? ri * 0.70 : ri * 0.86;
            g.setLineWidth(stunde ? ri * 0.065 : ri * 0.022);
            g.strokeLine(cx + Math.sin(a) * innen, cy - Math.cos(a) * innen, cx + Math.sin(a) * ri * 0.95,
                    cy - Math.cos(a) * ri * 0.95);
        }
        if (t == null) {
            return;
        }
        double s = t.getSecond() + t.getNano() / 1e9;
        double m = t.getMinute() + s / 60;
        double hh = t.getHour() % 12 + m / 60;
        zeiger(g, cx, cy, Math.toRadians(hh * 30), ri * 0.62, ri * 0.20, ri * 0.085, zeiger);
        zeiger(g, cx, cy, Math.toRadians(m * 6), ri * 0.90, ri * 0.20, ri * 0.065, zeiger);
        if (sek) {
            Color rot = Color.web(farbe("sekunde"));
            zeiger(g, cx, cy, Math.toRadians(s * 6), ri * 0.88, ri * 0.28, ri * 0.018, rot);
            g.setFill(rot);
            double k = ri * 0.035;
            g.fillOval(cx - k, cy - k, 2 * k, 2 * k);
        }
    }

    private static void zeiger(GraphicsContext g, double cx, double cy, double a, double lang, double hinten,
            double breite, Color f) {
        g.setStroke(f);
        g.setLineWidth(breite);
        g.setLineCap(StrokeLineCap.BUTT);
        g.strokeLine(cx - Math.sin(a) * hinten, cy + Math.cos(a) * hinten, cx + Math.sin(a) * lang,
                cy - Math.cos(a) * lang);
    }

    public static void closeIfOpen() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }

    // ------------------------------------------------------------------ Browser

    /** JSON für die Uhr im Browser: Sekunden des Tages, Faktor, Zeitpunkt, Darstellung und Farben. */
    static String json() {
        LocalTime t = LaufendeUhr.aktuelleZeit();
        StringBuilder b = new StringBuilder("{\"sek\":").append(t == null ? -1 : t.toNanoOfDay() / 1e9)
                .append(",\"faktor\":").append(LaufendeUhr.istAktiv() ? LaufendeUhr.faktor() : 0)
                .append(",\"art\":\"").append(analog() ? "analog" : "digital").append('"')
                .append(",\"sekunden\":").append(AppSettings.getInstance().getShowClockSeconds());
        for (String k : STANDARD.keySet()) {
            b.append(",\"").append(k).append("\":\"").append(farbe(k)).append('"');
        }
        return b.append('}').toString();
    }

    /** Seite der Uhr im Browser: Canvas füllt das Fenster; Antippen wechselt Analog/Digital (im Browser gemerkt). */
    static String seite() {
        return "<!doctype html><html lang=\"" + Uebersetzung.sprache() + "\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
                + Uebersetzung.tr("Uhr") + "</title><style>html,body{margin:0;height:100%;overflow:hidden}"
                + "canvas{display:block;width:100vw;height:100vh}</style></head><body><canvas id=\"c\"></canvas><script>"
                + "const cv=document.getElementById('c'),g=cv.getContext('2d');let d=null,stand=0,art=null;"
                + "try{art=localStorage.getItem('uhr.art')}catch(e){}"
                + "cv.addEventListener('click',()=>{art=((art||(d&&d.art))==='analog')?'digital':'analog';"
                + "try{localStorage.setItem('uhr.art',art)}catch(e){}});"
                + "async function holen(){try{const r=await fetch('/daten/uhr',{cache:'no-store'});d=await r.json();"
                + "stand=performance.now();}catch(e){}}holen();setInterval(holen,5000);"
                + "function zeiger(cx,cy,a,l,hi,b,f){g.strokeStyle=f;g.lineWidth=b;g.lineCap='butt';g.beginPath();"
                + "g.moveTo(cx-Math.sin(a)*hi,cy+Math.cos(a)*hi);g.lineTo(cx+Math.sin(a)*l,cy-Math.cos(a)*l);g.stroke();}"
                + "function bild(){const r=window.devicePixelRatio||1,w=innerWidth,h=innerHeight;"
                + "if(cv.width!==Math.round(w*r)){cv.width=Math.round(w*r);cv.height=Math.round(h*r);}"
                + "g.setTransform(r,0,0,r,0,0);if(!d){requestAnimationFrame(bild);return;}"
                + "g.fillStyle=d.hintergrund;g.fillRect(0,0,w,h);"
                + "let s=d.sek<0?null:(d.sek+(performance.now()-stand)/1000*d.faktor)%86400;"
                + "const a=art||d.art;"
                + "if(a==='analog'){const R=Math.min(w,h)/2*0.92,cx=w/2,cy=h/2,ri=R*0.93;"
                + "g.fillStyle='#b9bcc0';g.beginPath();g.arc(cx,cy,R,0,7);g.fill();g.fillStyle=d.blatt;g.beginPath();"
                + "g.arc(cx,cy,ri,0,7);g.fill();g.strokeStyle=d.zeiger;"
                + "for(let i=0;i<60;i++){const q=i*Math.PI/30,st=i%5===0,inn=st?ri*0.70:ri*0.86;g.lineWidth=st?ri*0.065:ri*0.022;"
                + "g.beginPath();g.moveTo(cx+Math.sin(q)*inn,cy-Math.cos(q)*inn);g.lineTo(cx+Math.sin(q)*ri*0.95,"
                + "cy-Math.cos(q)*ri*0.95);g.stroke();}"
                + "if(s!==null){const sec=s%60,m=(s/60)%60,hh=(s/3600)%12;"
                + "zeiger(cx,cy,hh*Math.PI/6,ri*0.62,ri*0.2,ri*0.085,d.zeiger);"
                + "zeiger(cx,cy,m*Math.PI/30,ri*0.9,ri*0.2,ri*0.065,d.zeiger);"
                + "if(d.sekunden){zeiger(cx,cy,sec*Math.PI/30,ri*0.88,ri*0.28,ri*0.018,d.sekunde);g.fillStyle=d.sekunde;"
                + "g.beginPath();g.arc(cx,cy,ri*0.035,0,7);g.fill();}}}"
                + "else{const z=n=>String(Math.floor(n)).padStart(2,'0');"
                + "const t=s===null?'--:--':z(s/3600)+':'+z((s/60)%60)+(d.sekunden?':'+z(s%60):'');"
                + "const muster=d.sekunden?'00:00:00':'00:00';g.font='bold 100px Consolas,monospace';"
                + "const fb=g.measureText(muster).width;const gr=Math.max(8,100*Math.min(w*0.9/fb,h*0.8/100));"
                + "g.font='bold '+gr+'px Consolas,monospace';g.fillStyle=d.ziffern;g.textAlign='center';"
                + "g.textBaseline='middle';g.fillText(t,w/2,h/2);}requestAnimationFrame(bild);}"
                + "requestAnimationFrame(bild);</script></body></html>";
    }
}
