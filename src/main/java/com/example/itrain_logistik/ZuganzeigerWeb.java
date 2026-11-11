package com.example.itrain_logistik;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.example.itrain_logistik.kern.Fahrthinweise;
import com.example.itrain_logistik.kern.Gut;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Zuganzeiger im Browser (Anwender 09.11.2026): dieselben zwei Anzeiger wie {@link ZuganzeigerWindow}
 * (Personenverkehr, Güterverkehr) als Webseite auf einem eigenen Port (Vorgabe 8091), getrennt von der
 * {@link Webansicht}. Gleiche Farben, Laufschrift nur bei zu langem Text, grünes Kästchen, wenn eine Fahrt erledigt
 * ist, keine doppelten Zeilen, Regler Langsamer/Schneller und Zoom, „Geeignete Züge vorschlagen“ – die Einstellungen
 * merkt sich der Browser (localStorage), nicht das Programm. Die Hinweise baut {@link #aktualisiere} auf dem
 * JavaFX-Thread; die Seiten fragen sie alle 3 s ab.
 *
 * <p>Seiten: {@code /} (Auswahl), {@code /personen}, {@code /gueter}, {@code /daten/personen|gueter} (JSON).
 */
public final class ZuganzeigerWeb {

    public static final int PORT_VORGABE = 8091;

    private static HttpServer server;
    private static int laufenderPort;
    private static volatile Map<ZuganzeigerWindow.Art, String> daten = Map.of();

    /** Bahnhof für den Anzeiger je Bahnhof: Anzeigename und Ids des Verbunds (Bahnhof samt Gbf). */
    record Bahnhof(String name, java.util.Set<String> ids) {
    }

    /** Pfad -&gt; Bahnhof (Anzeiger je Bahnhof, Anwender 09.11.2026). */
    private static volatile Map<String, Bahnhof> bahnhoefe = Map.of();

    /** Pfad eines Bahnhofs in der Adresse: Name klein, Umlaute ausgeschrieben, sonst Bindestriche. */
    static String pfad(String name, String id) {
        String s = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? id.substring(0, Math.min(8, id.length())) : s;
    }

    /** Die Bahnhöfe mit ihren Pfaden (für den Einstelldialog). */
    public static Map<String, String> bahnhofPfade() {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        bahnhoefe.forEach((p, b) -> m.put(p, b.name()));
        return m;
    }

    private ZuganzeigerWeb() {
    }

    static String pfad(ZuganzeigerWindow.Art art) {
        return art == ZuganzeigerWindow.Art.PERSONEN ? "personen" : "gueter";
    }

    public static String startAdresse() {
        return "http://" + Webansicht.eigeneAdresse() + ":" + AppSettings.getInstance().getAnzeigerWebPort() + "/";
    }

    public static synchronized boolean laeuft() {
        return server != null;
    }

    public static synchronized void starte(int port) throws IOException {
        if (server != null && laufenderPort == port) {
            return;
        }
        stoppe();
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", ZuganzeigerWeb::anfrage);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.start();
        server = s;
        laufenderPort = port;
    }

    public static synchronized void stoppe() {
        if (server != null) {
            server.stop(0);
            server = null;
            laufenderPort = 0;
        }
    }

    // ------------------------------------------------------------------ Daten (JavaFX-Thread)

    /** Baut die Hinweise beider Anzeiger als JSON; {@code hinweise} null = keine Datei offen. */
    public static void aktualisiere(List<Fahrthinweise.Hinweis> hinweise, Function<String, Gut> gutVon, String uhr,
            com.example.itrain_logistik.kern.Anlage anlage) {
        Map<String, Bahnhof> bf = new java.util.LinkedHashMap<>();
        if (anlage != null) {
            for (com.example.itrain_logistik.kern.Station s : anlage.bahnhoefeFuerAnzeiger()) {
                String p = pfad(s.getAnzeigeName(), s.getId());
                while (bf.containsKey(p)) {
                    p = p + "-2";
                }
                bf.put(p, new Bahnhof(s.getAnzeigeName(), anlage.verbund(s)));
            }
        }
        bahnhoefe = bf;
        Map<ZuganzeigerWindow.Art, String> neu = new EnumMap<>(ZuganzeigerWindow.Art.class);
        for (ZuganzeigerWindow.Art art : ZuganzeigerWindow.Art.values()) {
            StringBuilder j = new StringBuilder("{\"uhr\":").append(q(uhr)).append(",\"hinweise\":[");
            boolean erster = true;
            for (Fahrthinweise.Hinweis h : hinweise == null ? List.<Fahrthinweise.Hinweis>of() : hinweise) {
                if (!art.passt(h)) {
                    continue;
                }
                Gut g = h.gutId() != null && gutVon != null ? gutVon.apply(h.gutId()) : null;
                j.append(erster ? "" : ",").append("{\"key\":").append(q(ZuganzeigerWindow.schluessel(h)))
                        .append(",\"personen\":").append(h.gutId() == null)
                        .append(",\"name\":").append(q(Uebersetzung.tr(g != null ? g.getName() : h.gutId() == null ? "Reisende" : h.kuerzel())))
                        .append(",\"kuerzel\":").append(q(g != null ? g.getKuerzel() : h.kuerzel()))
                        .append(",\"farbe\":").append(q(g != null ? g.getFarbe() : "#808080"))
                        .append(",\"menge\":").append(h.menge())
                        .append(",\"text\":").append(q(h.text()))
                        .append(",\"ohne\":").append(q(h.textOhneZuege()))
                        .append(",\"st\":[");
                boolean e1 = true;
                for (String id : h.stationen()) {
                    j.append(e1 ? "" : ",").append(q(id));
                    e1 = false;
                }
                j.append("]}");
                erster = false;
            }
            neu.put(art, j.append("]}").toString());
        }
        daten = neu;
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '<' -> b.append("\\u003c");
                default -> {
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    // ------------------------------------------------------------------ HTTP

    /** Geräte, die in den letzten Sekunden Daten abgefragt haben: Adresse → Zeitpunkt (Anwender 10.11.2026). */
    private static final java.util.Map<String, Long> GERAETE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Adressen der Geräte, deren Browser die Seite gerade offen hat (letzte Abfrage höchstens 10 s her). */
    public static java.util.List<String> geraete() {
        long grenze = System.currentTimeMillis() - 10_000;
        GERAETE.values().removeIf(t -> t < grenze);
        return new java.util.ArrayList<>(new java.util.TreeSet<>(GERAETE.keySet()));
    }

    private static void anfrage(HttpExchange ex) throws IOException {
        if (ex.getRequestURI().getPath().startsWith("/daten") && ex.getRemoteAddress() != null
                && ex.getRemoteAddress().getAddress() != null) {
            GERAETE.put(ex.getRemoteAddress().getAddress().getHostAddress(), System.currentTimeMillis());
        }
        try (ex) {
            String pfad = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            if (!"GET".equals(ex.getRequestMethod())) {
                senden(ex, 405, "text/plain", Uebersetzung.tr("Nur lesen"));
                return;
            }
            for (ZuganzeigerWindow.Art art : ZuganzeigerWindow.Art.values()) {
                if (pfad.equals("/daten/" + pfad(art))) {
                    senden(ex, 200, "application/json", daten.getOrDefault(art, "{\"uhr\":\"\",\"hinweise\":[]}"));
                    return;
                }
                if (pfad.equals("/" + pfad(art))) {
                    senden(ex, 200, "text/html", anzeiger(art, null));
                    return;
                }
                if (pfad.startsWith("/" + pfad(art) + "/")) {
                    Bahnhof b = bahnhoefe.get(pfad.substring(pfad(art).length() + 2));
                    if (b == null) {
                        senden(ex, 404, "text/plain", Uebersetzung.tr("Kein Bahnhof unter dieser Adresse"));
                    } else {
                        senden(ex, 200, "text/html", anzeiger(art, b));
                    }
                    return;
                }
            }
            if (pfad.equals("/")) {
                senden(ex, 200, "text/html", start());
            } else {
                senden(ex, 404, "text/plain", Uebersetzung.tr("Nicht gefunden"));
            }
        }
    }

    private static void senden(HttpExchange ex, int code, String typ, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", typ + "; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    private static String bfIds(Bahnhof b) {
        if (b == null) {
            return "null";
        }
        StringBuilder j = new StringBuilder("[");
        for (String id : b.ids()) {
            j.append(j.length() > 1 ? "," : "").append(q(id));
        }
        return j.append(']').toString();
    }

    private static String start() {
        ZuganzeigerWindow.Art p = ZuganzeigerWindow.Art.PERSONEN;
        ZuganzeigerWindow.Art g = ZuganzeigerWindow.Art.GUETER;
        return """
                <!doctype html><html lang="%s"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>
                body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                 font-family:Arial,Helvetica,sans-serif;background:linear-gradient(%s,%s 30%%,%s);color:#fff}
                .start{text-align:center;padding:16px}
                h1{font-size:34px;margin:0 0 6px}
                p{color:#c9d3f2;margin:0 0 24px}
                a{display:block;margin:12px auto;max-width:360px;padding:16px 20px;border-radius:8px;color:#16307a;
                 background:#e8ebd6;font-weight:bold;font-size:20px;text-decoration:none}
                a.g{color:#22428f}
                h2{font-size:20px;margin:28px 0 4px}
                .bf{display:flex;align-items:center;justify-content:center;gap:10px;margin:6px auto;max-width:520px}
                .bf span{flex:1;text-align:left;font-weight:bold}
                .bf a{display:inline-block;margin:0;padding:8px 12px;font-size:15px}
                </style></head><body><div class="start"><h1>%s</h1><p>iTrain-Logistik · %s</p>
                <a href="/personen">%s</a><a class="g" href="/gueter">%s</a>%s</div></body></html>
                """.formatted(Uebersetzung.sprache(), t("Zuganzeiger"), p.oben, p.blau, g.unten, t("Zuganzeiger"),
                t("Nächste Fahrten"), t("Personenverkehr"), t("Güterverkehr"), bahnhofListe());
    }

    private static String bahnhofListe() {
        if (bahnhoefe.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder("<h2>" + t("Je Bahnhof") + "</h2>");
        bahnhoefe.forEach((p, bf) -> b.append("<div class=\"bf\"><span>").append(html(bf.name()))
                .append("</span><a href=\"/personen/").append(html(p)).append("\">" + t("Personen") + "</a><a class=\"g\" href=\"/gueter/")
                .append(html(p)).append("\">" + t("Güter") + "</a></div>"));
        return b.toString();
    }

    private static String t(String s) {
        return html(Uebersetzung.tr(s));
    }

    /** Texte, die das Skript des Anzeigers zeigt (übersetzt über T('…')). */
    private static final List<String> JS_WOERTER = List.of("Zurzeit sind keine Fahrten nötig.", "1 Hinweis",
            "{0} Hinweise", "keine Verbindung zum Logistik-Rechner");

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String anzeiger(ZuganzeigerWindow.Art art, Bahnhof bahnhof) {
        String css = """
                :root{--blau:%s;--dunkel:%s;--oben:%s;--unten:%s;--linie:%s;--hell:%s;--gruen:%s;--s:22px}
                *{box-sizing:border-box}
                html,body{margin:0;height:100%%;overflow:hidden}
                body{font-family:Arial,Helvetica,sans-serif;color:#fff;display:flex;flex-direction:column;
                 background:linear-gradient(to bottom,var(--oben),var(--blau) 30%%,var(--unten))}
                #kopf{display:flex;align-items:baseline;gap:14px;padding:8px 14px 2px}
                #titel{font-weight:bold;font-size:calc(var(--s)*1.9);white-space:nowrap}
                #titelEn{font-size:calc(var(--s)*1.2);white-space:nowrap;overflow:hidden;text-overflow:clip}
                #luecke{flex:1}
                #uhr{font-weight:bold;font-size:calc(var(--s)*1.3);background:var(--dunkel);padding:2px 10px}
                #spalten{display:flex;gap:8px;padding:0 8px 2px;font-size:calc(var(--s)*.62)}
                #spalten .g{width:calc(var(--s)*7.5)} #spalten .m{width:calc(var(--s)*2.6);text-align:right}
                #zeilen{flex:1;min-height:0;overflow:hidden;padding:0 8px}
                .zeile{display:flex;align-items:center;gap:8px;height:calc(var(--s)*2.1);border-bottom:2px solid var(--dunkel);
                 font-weight:bold;font-size:var(--s)}
                .kasten{display:flex;align-items:center;gap:calc(var(--s)*.3);width:calc(var(--s)*7.5);flex:none;
                 height:calc(var(--s)*1.45);padding:0 calc(var(--s)*.3) 0 calc(var(--s)*.2);background:var(--hell);
                 color:var(--blau);font-size:calc(var(--s)*.85);overflow:hidden;white-space:nowrap;transition:background .2s}
                .kasten.leer{visibility:hidden}
                .kasten.gruen{background:var(--gruen)}
                .sym{flex:none;width:calc(var(--s)*1.05);height:calc(var(--s)*1.05);border-radius:calc(var(--s)*.35);
                 display:flex;align-items:center;justify-content:center;font-size:calc(var(--s)*.5)}
                .sym svg{width:100%%;height:100%%}
                .menge{width:calc(var(--s)*2.6);flex:none;text-align:right}
                .lauf{flex:1;position:relative;height:100%%;overflow:hidden}
                .lauf span{position:absolute;top:50%%;transform:translate(0,-50%%);white-space:nowrap;will-change:left}
                #fuss{display:flex;flex-wrap:wrap;align-items:center;gap:8px 14px;padding:4px 12px;background:var(--dunkel);
                 border-top:1px solid var(--linie);color:#c9d3f2;font-size:14px}
                #fuss .mitte{flex:1}
                #fuss label{display:flex;align-items:center;gap:6px;white-space:nowrap}
                #fuss input[type=range]{width:140px}
                #fuss a,#fuss button{color:#c9d3f2;background:transparent;border:1px solid var(--linie);border-radius:5px;
                 padding:2px 8px;font-size:14px;text-decoration:none;cursor:pointer}
                @media (max-width:640px){#titelEn{display:none} #titel{font-size:calc(var(--s)*1.3)} #uhr{font-size:var(--s)}
                 #spalten span:last-child{white-space:nowrap;overflow:hidden} #fuss input[type=range]{width:90px}}
                """.formatted(art.blau, art.dunkel, art.oben, art.unten, art.linie, ZuganzeigerWindow.TAFEL_HELL,
                ZuganzeigerWindow.ERLEDIGT_GRUEN);
        String js = """
                const ART='%s', BF=%s, TEMPO=70, STEHZEIT=8, GRUEN=1.5, SCHRIFT=22;
                function lies(k,v){try{const x=localStorage.getItem('za.'+k);return x===null?v:JSON.parse(x)}catch(e){return v}}
                function merke(k,v){try{localStorage.setItem('za.'+k,JSON.stringify(v))}catch(e){}}
                let tempo=lies('tempo',1), zoom=lies('zoom',1), zuege=lies('zuege.'+ART,true);
                let hinweise=[], naechster=0, zeilen=[], erstesMal=true;
                const box=document.getElementById('zeilen');
                const PERSON='<svg viewBox="0 0 10 10"><circle cx="5" cy="2" r="1.8" fill="#f5c518"/><rect x="3" y="4.2" width="4" height="5.5" rx="1" fill="#f5c518"/></svg>';
                function hell(f){const m=/^#?([0-9a-f]{6})$/i.exec(f||'');if(!m)return false;const n=parseInt(m[1],16);
                  return (0.299*(n>>16)+0.587*((n>>8)&255)+0.114*(n&255))>153}
                function esc(s){return String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
                function txt(h){return zuege?h.text:h.ohne}
                function neueZeile(){
                  const el=document.createElement('div');el.className='zeile';
                  el.innerHTML='<div class="kasten leer"><div class="sym"></div><span class="name"></span></div><div class="menge"></div><div class="lauf"><span></span></div>';
                  const z={el,kasten:el.querySelector('.kasten'),sym:el.querySelector('.sym'),name:el.querySelector('.name'),
                    menge:el.querySelector('.menge'),lauf:el.querySelector('.lauf'),text:el.querySelector('.lauf span'),
                    akt:null,x:8,laeuft:false,steh:0,gruen:0};
                  box.appendChild(el);return z}
                function zuLang(z){return z.text.offsetWidth>z.lauf.clientWidth-8}
                function setzeX(z){z.text.style.left=z.x+'px'}
                function waehle(z){
                  if(!hinweise.length)return null;
                  const belegt=new Set(zeilen.filter(o=>o!==z&&o.akt).map(o=>o.akt.key));
                  for(let i=0;i<hinweise.length;i++){const h=hinweise[(naechster+i)%%hinweise.length];
                    if(!belegt.has(h.key)){naechster=(naechster+i+1)%%hinweise.length;return h}}
                  return null}
                function zeige(z,h){
                  z.akt=h;z.steh=0;z.kasten.classList.remove('gruen');
                  if(!h){z.kasten.classList.add('leer');z.sym.innerHTML='';z.sym.style.background='';z.name.textContent='';
                    z.menge.textContent='';z.text.textContent=(zeilen.indexOf(z)===0&&!hinweise.length)?T('Zurzeit sind keine Fahrten nötig.'):'';}
                  else{z.kasten.classList.remove('leer');
                    if(h.personen){z.sym.innerHTML=PERSON;z.sym.style.background='';z.sym.style.color=''}
                    else{z.sym.innerHTML=esc(h.kuerzel);z.sym.style.background=h.farbe;z.sym.style.color=hell(h.farbe)?'#000':'#fff'}
                    z.name.textContent=h.name;z.menge.textContent=h.menge;z.text.textContent=txt(h)}
                  z.laeuft=zuLang(z);z.x=z.laeuft?Math.max(z.lauf.clientWidth,1):8;setzeX(z)}
                function anpassen(){
                  document.documentElement.style.setProperty('--s',(SCHRIFT*zoom)+'px');
                  const hoehe=SCHRIFT*zoom*2.1, n=Math.max(1,Math.floor(box.clientHeight/hoehe));
                  while(zeilen.length<n){const z=neueZeile();zeilen.push(z);zeige(z,waehle(z))}
                  while(zeilen.length>n){const z=zeilen.pop();z.el.remove()}
                  for(const z of zeilen){const v=z.laeuft;z.laeuft=zuLang(z);if(!z.laeuft||!v)z.x=8;setzeX(z)}}
                async function abfragen(){
                  try{const r=await fetch('/daten/'+ART,{cache:'no-store'});const d=await r.json();
                    document.getElementById('uhr').textContent=d.uhr||'';
                    hinweise=(d.hinweise||[]).filter(h=>!BF||(h.st||[]).some(i=>BF.includes(i)));const neu=new Map(hinweise.map(h=>[h.key,h]));
                    document.getElementById('anzahl').textContent=hinweise.length===1?T('1 Hinweis'):T('{0} Hinweise').replace('{0}',hinweise.length);
                    for(const z of zeilen){
                      if(z.gruen>0)continue;
                      if(z.akt&&!neu.has(z.akt.key)){if(erstesMal)zeige(z,waehle(z));else{z.gruen=GRUEN;z.kasten.classList.add('gruen')}}
                      else if(z.akt){const h=neu.get(z.akt.key);z.akt=h;z.menge.textContent=h.menge;
                        if(z.text.textContent!==txt(h)){z.text.textContent=txt(h);const v=z.laeuft;z.laeuft=zuLang(z);if(!z.laeuft||!v){z.x=8;setzeX(z)}}}
                      else zeige(z,waehle(z))}
                    erstesMal=false;
                    document.getElementById('fehler').textContent='';
                  }catch(e){document.getElementById('fehler').textContent='⚠ '+T('keine Verbindung zum Logistik-Rechner')}}
                let vorher=performance.now();
                function schritt(t){
                  const dt=Math.min(0.2,(t-vorher)/1000);vorher=t;
                  for(const z of zeilen){
                    if(z.gruen>0){z.gruen-=dt;if(z.gruen<=0)zeige(z,waehle(z));continue}
                    if(z.laeuft){z.x-=TEMPO*zoom*tempo*dt;if(z.x<-z.text.offsetWidth)zeige(z,waehle(z));else setzeX(z)}
                    else if(z.akt){z.steh+=dt*tempo;if(z.steh>STEHZEIT&&hinweise.length>zeilen.length)zeige(z,waehle(z))}}
                  requestAnimationFrame(schritt)}
                const rt=document.getElementById('tempo'),rz=document.getElementById('zoom'),cz=document.getElementById('zuege');
                rt.value=tempo;rz.value=zoom;cz.checked=zuege;
                rt.oninput=()=>{tempo=+rt.value;merke('tempo',tempo)};
                rz.oninput=()=>{zoom=+rz.value;merke('zoom',zoom);anpassen()};
                cz.onchange=()=>{zuege=cz.checked;merke('zuege.'+ART,zuege);
                  for(const z of zeilen)if(z.akt&&z.gruen<=0){z.text.textContent=txt(z.akt);z.laeuft=zuLang(z);z.x=z.laeuft?Math.max(z.lauf.clientWidth,1):8;setzeX(z)}};
                document.getElementById('vollbild').onclick=()=>{const d=document.documentElement;
                  if(document.fullscreenElement)document.exitFullscreen();else if(d.requestFullscreen)d.requestFullscreen()};
                addEventListener('resize',anpassen);
                anpassen();abfragen();setInterval(abfragen,3000);requestAnimationFrame(schritt);
                """.formatted(pfad(art), bfIds(bahnhof));
        return """
                <!doctype html><html lang="%s"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>%s</style></head><body>
                <div id="kopf"><span id="titel">%s</span><span id="titelEn">%s</span><span id="luecke"></span><span id="uhr"></span></div>
                <div id="spalten"><span class="g">%s</span><span class="m">%s</span><span>%s</span></div>
                <div id="zeilen"></div>
                <div id="fuss"><span id="anzahl"></span><span id="fehler" style="color:#ffb3a7"></span>
                <label><input type="checkbox" id="zuege"> %s</label><span class="mitte"></span>
                <label>%s <input type="range" id="tempo" min="0.25" max="3" step="0.05"> %s</label>
                <label>%s <input type="range" id="zoom" min="0.5" max="3" step="0.05"></label>
                <button id="vollbild" title="%s">⛶ %s</button><a href="/">%s</a></div>
                <script>%s</script></body></html>
                """.formatted(Uebersetzung.sprache(), t(art.fenster) + (bahnhof != null ? " – " + html(bahnhof.name()) : ""), css,
                bahnhof != null ? html(bahnhof.name()) : t(art.titel),
                bahnhof != null ? t(art == ZuganzeigerWindow.Art.PERSONEN ? "Personenverkehr" : "Güterverkehr")
                        + " · Next trips" : t(art.titelEn),
                t("Gut"), t("Menge"), t("Hinweis  ·  Ziel  ·  Abholung  ·  Zug"), t("Geeignete Züge vorschlagen"),
                t("Langsamer"), t("Schneller"), t("Zoom"), t("Vollbild an/aus"), t("Vollbild"), t("Anzeiger"),
                Webansicht.woerter(JS_WOERTER) + js);
    }
}
