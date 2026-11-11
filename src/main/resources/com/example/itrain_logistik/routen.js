// Routen-Anzeiger im Browser (Anwender 10.11.2026): dieselbe Darstellung wie RoutenAnzeigerWindow.java.
// Der Server liefert alle 250 ms den Zustand eines Zugs (/daten/<pfad>); gezeichnet wird hier.
// TX/T() (Übersetzungen) und PFAD setzt die Seite davor.
'use strict';
const cv = document.getElementById('flaeche'), g = cv.getContext('2d');
const F = {HIMMEL:'#eef3f8', BODEN:'#dfe6d4', SCHOTTER:'#b9b4a8', SCHWELLE:'#6b5a47', SCHIENE:'#4a4f57',
  AKTUELL:'#fff8d6', NACHBAR:'#f3f5f7', WEICHEN:'#dfe7f1', TEXT:'#1f2328', LEISE:'#5b6470', GRAU:'#b4bac2',
  GELB:'#f2c200', ROT:'#d42a2a', GRUEN:'#2e9e3e'};
const WEICHE_ERSATZ = 11;
let d = null, fehler = 0;
// weich nachgeführte Werte (wie in der Java-Fassung)
const z = {block:null, pos:0, mass:0, ueber:0, cmJeKmh:0.17, letztePos:0, letztePosZeit:0, letzteLuecke:0,
  letzteNamen:'', lueckeVor:0, lueckeVorNamen:'', freigabe:null, stopSeit:0, richtung:null, wechsel:false,
  spiegel:false, gewendet:false, bremste:false, laufweg:0, pZuletzt:0, gZuletzt:0, vorZiel:null, lVorAlt:0};
const BREMSBEGINN = {};
let ton = false;
try { ton = localStorage.getItem('ra.ton') === '1'; } catch (e) {}
const klang = {einsteigen:new Audio('/klang/einsteigen'), beladen:new Audio('/klang/beladen')};
const spielt = {einsteigen:false, beladen:false};
const tonKnopf = document.getElementById('ton');
function tonZeigen() { tonKnopf.textContent = ton ? '🔊 ' + T('Ton an') : '🔇 ' + T('Ton aus'); }
tonKnopf.onclick = () => { ton = !ton; try { localStorage.setItem('ra.ton', ton ? '1' : '0'); } catch (e) {}
  if (!ton) { for (const k in klang) { klang[k].pause(); spielt[k] = false; } } tonZeigen(); };
tonZeigen();
// Die Aufnahme spielt bis zum Ende, auch wenn der Vorgang vorher fertig ist (Anwender 10.11.2026);
// „Ton aus“ beendet sie sofort.
function vorgang(name, an) {
  if (an && ton && !spielt[name]) { spielt[name] = true; klang[name].currentTime = 0; klang[name].play().catch(() => {}); }
  if (!an) spielt[name] = false;
}

async function holen() {
  try {
    const r = await fetch('/daten/' + PFAD, {cache:'no-store'});
    if (!r.ok) throw new Error(r.status);
    d = await r.json(); d.empfangen = Date.now(); fehler = 0;
  } catch (e) { fehler++; }
  setTimeout(holen, 250);
}
holen();

function groesse() {
  const r = window.devicePixelRatio || 1, w = cv.clientWidth, h = cv.clientHeight;
  if (cv.width !== Math.round(w * r) || cv.height !== Math.round(h * r)) { cv.width = Math.round(w * r); cv.height = Math.round(h * r); }
  g.setTransform(r, 0, 0, r, 0, 0);
  return [w, h];
}
function schrift(px, fett) { g.font = (fett ? 'bold ' : '') + px + 'px system-ui, "Segoe UI", sans-serif'; }
function rr(x, y, w, h, r) { g.beginPath(); if (g.roundRect) g.roundRect(x, y, w, h, r); else g.rect(x, y, w, h); }
function kuerze(t, breite) {
  if (g.measureText(t).width <= breite) return t;
  let s = t;
  while (s.length > 2) { s = s.slice(0, -1); if (g.measureText(s + '…').width <= breite) break; }
  return s + '…';
}
// Text an der Stelle x der (vielleicht gespiegelten) Strecke, selbst nie gespiegelt
let W = 0;
function txt(t, x, y) {
  if (z.spiegelZeichnen) { g.save(); g.scale(-1, 1); g.translate(-W, 0); g.fillText(t, W - x, y); g.restore(); }
  else g.fillText(t, x, y);
}

let letzterTakt = 0;
function bild(jetztMs) {
  const dt = letzterTakt ? Math.min(0.1, (jetztMs - letzterTakt) / 1000) : 0; letzterTakt = jetztMs;
  const [w, h] = groesse(); W = w;
  g.fillStyle = F.HIMMEL; g.fillRect(0, 0, w, h);
  const gleisY = Math.round(Math.min(h * 0.66, h - 86)), zugH = Math.max(30, Math.min(64, h * 0.2));
  z.spiegelZeichnen = false;
  if (!d || fehler > 8) { gleis(w, gleisY, 0, 4); hinweis(w, fehler > 8 ? T('keine Verbindung zum Logistik-Rechner') : T('Lade…')); requestAnimationFrame(bild); return; }
  document.getElementById('name').textContent = d.zug;
  if (!d.verbunden || d.block == null) {
    gleis(w, gleisY, 0, 4); zug(d.bild, w * 0.55, gleisY, Math.min(w * 0.4, 260), zugH, -1);
    hinweis(w, !d.verbunden ? T('Keine Online-Daten: Dieser Zug ist nicht über XTC mit iTrain verbunden.') : T('Der Zug steht in iTrain in keinem Block.'));
    vorgang('einsteigen', false); vorgang('beladen', false);
    requestAnimationFrame(bild); return;
  }
  const jetzt = Date.now(), block = d.block;
  const laenge = d.laenge > 0 ? d.laenge : 100;
  // ohne Position: stehender Zug am Haltepunkt bzw. Blockende (wie im Fenster, Anwender 11.11.2026)
  const pos = d.pos >= 0 ? Math.min(laenge, d.pos) : d.tempo > 0.5 ? 0 : d.stop >= 0 ? Math.min(laenge, d.stop) : laenge;
  // die Wegangaben gelten nur, wenn sie zum gemeldeten Block und zur Richtung gehören (sonst kurz veraltet)
  const passt = d.s && d.s.block === block && d.s.richtung === d.richtung;
  const weg = passt ? d.s.weg : null, zur = passt ? d.s.zurueck : null;
  const luecke = weg && weg.namen ? weg.laenge : 0;

  if (block !== z.block) {
    const erster = z.block == null;
    z.block = block;
    z.pos = erster ? pos : Math.min(pos, Math.max(-z.letzteLuecke, z.ueber - z.letzteLuecke));
    z.lueckeVor = erster ? 0 : z.letzteLuecke; z.lueckeVorNamen = erster ? '' : z.letzteNamen;
    z.ueber = 0; z.freigabe = null; z.stopSeit = 0; z.letztePosZeit = 0;
    z.richtung = d.richtung; z.wechsel = false; z.gewendet = false;
  } else if (d.richtung && d.richtung !== z.richtung) {
    z.wechsel = !!z.richtung;
    if (z.wechsel) z.gewendet = true; // der Zug fährt immer nach rechts; gewendet wird sein Bild (Lok schiebt)
    z.richtung = d.richtung; z.pos = pos; z.ueber = 0; z.freigabe = null; z.stopSeit = 0; z.letztePosZeit = 0;
  }
  if (z.wechsel && d.tempo > 0.5) z.wechsel = false;
  if (passt) { z.letzteLuecke = luecke; z.letzteNamen = weg && weg.namen ? weg.namen : ''; }

  if (d.posZeit !== z.letztePosZeit) {
    const dtp = (d.posZeit - z.letztePosZeit) / 1000;
    if (z.letztePosZeit > 0 && dtp > 0.03 && dtp < 1.5 && pos > z.letztePos && pos < laenge - 0.3 && d.tempo > 3) {
      const r = (pos - z.letztePos) / dtp / d.tempo;
      if (r > 0.02 && r < 1.5) z.cmJeKmh += (r - z.cmJeKmh) * 0.12;
    }
    z.letztePosZeit = d.posZeit; z.letztePos = pos;
  }
  const amEnde = d.pos >= laenge - 0.3;
  if (amEnde && d.tempo > 0.5 && !d.wartet) z.ueber = Math.min(luecke + 1.5, z.ueber + z.cmJeKmh * d.tempo * dt);
  else if (!amEnde) z.ueber = 0;
  const zielPos = pos + z.ueber;
  const xr = Math.round(w * 0.55);
  const zielMass = z.spiegel ? Math.max(0.5, (xr - 24) / (laenge + luecke + Math.max(0, d.zugLaenge)))
                             : Math.max(0.5, (w * 0.45 - 24) / (laenge + luecke));
  if (z.mass <= 0) z.mass = zielMass;
  const vorher = z.pos;
  z.pos += (zielPos - z.pos) * Math.min(1, dt * 6);
  z.mass += (zielMass - z.mass) * Math.min(1, dt * 3);
  z.laufweg += Math.abs(z.pos - vorher);
  const s = z.mass;
  // Zuglänge im Maßstab des Blocks (Anwender 11.11.2026)
  const zugW = d.zugLaenge > 0 ? Math.max(24, Math.min(xr - 8, d.zugLaenge * s)) : Math.max(70, Math.min(w * 0.5, 180));
  const x0 = z.spiegel ? w - xr + zugW : xr;

  const endeVoraus = passt && !weg && d.s.endeVor;
  let endeHinten = false, vor = d.vorher, lVor = passt && d.s.lVor > 0 ? d.s.lVor : laenge;
  if (z.gewendet) {
    z.lueckeVor = zur && zur.namen ? zur.laenge : 0; z.lueckeVorNamen = zur && zur.namen ? zur.namen : '';
    endeHinten = passt && !zur && d.s.endeHin; vor = zur ? zur.ziel : null;
    lVor = zur && zur.zielLaenge > 0 ? zur.zielLaenge : laenge;
  }
  const nach = weg ? weg.ziel : null, lNach = weg && weg.zielLaenge > 0 ? weg.zielLaenge : laenge;
  const xA = x0 - z.pos * s, xE = xA + laenge * s, xV = xA - z.lueckeVor * s, xN = xE + luecke * s;

  g.save();
  if (z.spiegel) { g.translate(w, 0); g.scale(-1, 1); z.spiegelZeichnen = true; }
  const bandOben = 6, bandH = gleisY + 14 - bandOben;
  g.fillStyle = F.NACHBAR;
  if (!endeHinten) g.fillRect(xV - lVor * s, bandOben, lVor * s, bandH);
  if (!endeVoraus) g.fillRect(xN, bandOben, lNach * s, bandH);
  g.fillStyle = F.WEICHEN; g.fillRect(xV, bandOben, z.lueckeVor * s, bandH); g.fillRect(xE, bandOben, luecke * s, bandH);
  g.fillStyle = F.AKTUELL; g.fillRect(xA, bandOben, laenge * s, bandH);
  g.fillStyle = F.BODEN; g.fillRect(0, gleisY + 14, w, h - gleisY - 14);
  gleis(w, gleisY, xA, s);
  g.strokeStyle = F.LEISE; g.lineWidth = 1.2; g.setLineDash([5, 4]);
  for (const x of [endeHinten ? xA : xV - lVor * s, xV, xA, xE, xN, endeVoraus ? xE : xN + lNach * s]) {
    g.beginPath(); g.moveTo(x, bandOben); g.lineTo(x, gleisY + 10); g.stroke();
  }
  g.setLineDash([]);
  if (!endeHinten) blockName(w, xV - lVor * s, xV, bandOben, z.gewendet ? 'dahinter' : 'vorher', vor == null ? '–' : vor, false);
  blockName(w, xA, xE, bandOben, 'aktuell', block, true);
  if (!endeVoraus) blockName(w, xN, xN + lNach * s, bandOben, 'danach', nach == null ? '?' : nach, false);
  weichenName(w, xV, xA, bandOben, z.lueckeVorNamen);
  weichenName(w, xE, xN, bandOben, passt && weg && weg.namen ? weg.namen : '');

  // Signal: bleibt auf Fahrt, wenn es hinter der Zugspitze auf Halt fällt; Halt nach der Einfahrt erst nach 1,5 s
  const roh = d.signal || '';
  let anzeige;
  if (roh !== 'stop') { if (roh) z.freigabe = roh; z.stopSeit = 0; anzeige = roh; }
  else {
    if (!z.stopSeit) z.stopSeit = jetzt;
    const steht = d.tempo <= 0.5 || d.wartet;
    anzeige = steht ? 'stop' : z.freigabe ? z.freigabe : (jetzt - z.stopSeit > 1500 ? 'stop' : '');
  }
  const haelt = anzeige === 'stop' || d.wartet;
  let bremst = haelt || anzeige === 'restricted';
  const markenY = gleisY - zugH - 26, stop = d.stop;
  const bremstJetzt = d.tempo > 0.5 && d.wunsch < d.tempo - 0.5, merk = block + '|' + d.richtung;
  if (bremstJetzt && !z.bremste && d.pos >= 0) BREMSBEGINN[merk] = d.pos;
  z.bremste = bremstJetzt; bremst = bremst || bremstJetzt;
  {
    const bis = stop >= 0 ? stop : laenge, beginn = d.bremse >= 0 ? d.bremse : (BREMSBEGINN[merk] || 0);
    let von = Math.min(beginn, bis);
    if (bremst) von = Math.max(von, Math.min(bis, z.pos));
    const breite = (bis - von) * s;
    if (breite > 1.5) {
      g.fillStyle = bremst ? F.GELB : F.GRAU; rr(xA + von * s, markenY + 10, breite, 7, 4); g.fill();
      g.strokeStyle = 'rgba(0,0,0,0.25)'; g.lineWidth = 1; g.stroke();
    }
  }
  if (z.wechsel) {
    const a = Math.max(xA, 4), e = Math.min(xE, w - 4);
    if (e - a > 60) {
      const by = bandOben + 36;
      g.fillStyle = F.GRUEN; rr(a + 6, by, e - a - 12, 18, 9); g.fill();
      g.fillStyle = '#fff'; schrift(12, true); g.textAlign = 'center'; g.textBaseline = 'middle';
      txt(kuerze('⇄  ' + T('Richtungswechsel'), e - a - 24), (a + e) / 2, by + 9);
    }
  }
  if (stop >= 0) stoppSchild(xA + stop * s, markenY, gleisY, haelt);
  if (endeVoraus) prellbock(xE, gleisY, true); else signal(xE, markenY - 6, gleisY, anzeige);
  if (endeHinten) prellbock(xA, gleisY, false);
  g.restore(); z.spiegelZeichnen = false;

  zug(d.bild, xr, gleisY, zugW, zugH, d.tempo > 0.5 ? z.laufweg : -1, d.fahrt === 'backward');

  // Bremst / Halt erwarten unten rechts (wie im Fenster)
  const schilder = [];
  if (bremstJetzt) schilder.push(['▼ ' + T('Bremst'), '#f2c200']);
  const erw = d.erwartet || '';
  if (erw === 'stop' && anzeige !== 'stop') schilder.push(['◉ ' + T('Halt erwarten'), '#f2c200']);
  else if (erw === 'restricted' && anzeige !== 'restricted') schilder.push(['◉ ' + T('Langsamfahrt erwarten'), '#ffe9a8']);
  { let sx = w - 10; schrift(14, true); g.textAlign = 'center'; g.textBaseline = 'middle';
    for (const sc of schilder) { const sb = g.measureText(sc[0]).width + 22;
      g.fillStyle = sc[1]; rr(sx - sb, h - 34, sb, 26, 8); g.fill(); g.strokeStyle = 'rgba(0,0,0,0.45)'; g.lineWidth = 1.2; g.stroke();
      g.fillStyle = '#000'; g.fillText(sc[0], sx - sb / 2, h - 21); sx -= sb + 8; } }

  g.textAlign = 'center'; g.textBaseline = 'top'; g.fillStyle = F.TEXT; schrift(14, true);
  const posText = d.pos >= 0 ? Math.round(Math.max(0, Math.min(laenge, z.pos)) * 10) + ' mm' : '–';
  g.fillText(T('{0} mm > Position {1}').replace('{0}', Math.round(laenge * 10)).replace('{1}', posText)
    + (z.ueber > 0.5 && luecke > 0 ? '  ·  ' + T('Weichen') : ''), xr - zugW / 2, gleisY + 20);
  schrift(12, false); g.fillStyle = F.LEISE;
  let zl = Math.round(d.tempo) + ' km/h';
  if (d.zugLaenge > 0) zl += '  ·  ' + T('Zuglänge {0} mm').replace('{0}', Math.round(d.zugLaenge * 10));
  if (d.wartet && d.wGesamt > 0) zl += '  ·  ' + T('Halt noch {0} s').replace('{0}', Math.floor(Math.max(0, d.wGesamt - d.wVorbei) / 1000));
  if (d.route) zl += '  ·  ' + T('Route') + ': ' + d.route;
  if (d.ziel) zl += '  ·  ' + T('Ziel') + ': ' + d.ziel;
  g.fillText(zl, xr - zugW / 2, gleisY + 40);

  const u = d.u;
  if (u && u.p) z.pZuletzt = jetzt;
  if (u && u.g) z.gZuletzt = jetzt;
  const pAn = jetzt - z.pZuletzt < 3000, gAn = jetzt - z.gZuletzt < 3000;
  vorgang('einsteigen', pAn); vorgang('beladen', gAn);
  if (u && u.z.length) {
    const text = u.z.join('   ·   '); schrift(12.5, true);
    const tb = Math.min(w - 20, g.measureText(text).width + 20);
    const tx = Math.max(10 + tb / 2, Math.min(w - 10 - tb / 2, xr - zugW / 2)), ty = gleisY + 58, an = pAn || gAn;
    g.fillStyle = an ? '#ff8c00' : '#c9ced6'; rr(tx - tb / 2, ty, tb, 20, 10); g.fill();
    g.fillStyle = an ? '#fff' : F.TEXT; g.textAlign = 'center'; g.textBaseline = 'middle';
    g.fillText(kuerze(text, tb - 12), tx, ty + 10);
  }
  requestAnimationFrame(bild);
}
requestAnimationFrame(bild);

function hinweis(w, text) { g.textAlign = 'center'; g.textBaseline = 'top'; g.fillStyle = F.LEISE; schrift(13, false); g.fillText(text, w / 2, 14); }

function gleis(w, y, ursprung, s) {
  g.fillStyle = F.SCHOTTER; g.fillRect(0, y + 2, w, 12);
  const schritt = Math.max(9, 2.2 * s);
  let start = ursprung % schritt; if (start > 0) start -= schritt;
  g.fillStyle = F.SCHWELLE;
  for (let x = start; x < w; x += schritt) g.fillRect(x, y + 3, Math.max(3, schritt * 0.32), 8);
  g.fillStyle = F.SCHIENE; g.fillRect(0, y, w, 3);
  const mast = schritt * 9; let m0 = ursprung % mast; if (m0 > 0) m0 -= mast;
  g.strokeStyle = '#9aa3ad'; g.lineWidth = 1.5;
  for (let x = m0; x < w; x += mast) { g.beginPath(); g.moveTo(x, y + 16); g.lineTo(x, y + 30); g.moveTo(x - 4, y + 19); g.lineTo(x + 4, y + 19); g.stroke(); }
}
function blockName(w, von, bis, oben, rolle, name, aktuell) {
  const a = Math.max(von, 0), e = Math.min(bis, w); if (e - a < 40) return;
  const mitte = (a + e) / 2; g.textAlign = 'center'; g.textBaseline = 'top';
  schrift(10, false); g.fillStyle = F.LEISE; txt(T(rolle), mitte, oben + 2);
  schrift(aktuell ? 14 : 12.5, aktuell); g.fillStyle = aktuell ? F.TEXT : F.LEISE; txt(kuerze(name, e - a - 12), mitte, oben + 15);
}
function weichenName(w, von, bis, oben, namen) {
  const a = Math.max(von, 0), e = Math.min(bis, w); if (e - a < 26 || !namen) return;
  g.textAlign = 'center'; g.textBaseline = 'top'; schrift(10, false); g.fillStyle = F.LEISE;
  txt(kuerze(T('Weichen'), e - a - 4), (a + e) / 2, oben + 2);
  schrift(10.5, false); txt(kuerze(namen, e - a - 6), (a + e) / 2, oben + 16);
}
function stoppSchild(x, y, gleisY, benutzt) {
  g.strokeStyle = '#7a828c'; g.lineWidth = 1.5; g.beginPath(); g.moveTo(x, y + 8); g.lineTo(x, gleisY); g.stroke();
  g.beginPath();
  for (let k = 0; k < 8; k++) { const a = Math.PI / 8 + k * Math.PI / 4; g[k ? 'lineTo' : 'moveTo'](x + 11 * Math.cos(a), y + 11 * Math.sin(a)); }
  g.closePath(); g.fillStyle = benutzt ? F.ROT : F.GRAU; g.fill(); g.strokeStyle = '#fff'; g.lineWidth = 1.4; g.stroke();
  g.fillStyle = '#fff'; schrift(7.5, true); g.textAlign = 'center'; g.textBaseline = 'middle'; txt('STOP', x, y);
}
function signal(x, y, gleisY, stand) {
  g.strokeStyle = '#3d434b'; g.lineWidth = 2; g.beginPath(); g.moveTo(x, y + 30); g.lineTo(x, gleisY); g.stroke();
  g.fillStyle = '#23272e'; rr(x - 7, y - 4, 14, 36, 3); g.fill();
  const aus = '#4b525c';
  for (const [dy, an, f] of [[3, 'stop', F.ROT], [14, 'restricted', F.GELB], [25, 'go', F.GRUEN]]) {
    g.fillStyle = stand === an ? f : aus; g.beginPath(); g.arc(x, y + dy, 4, 0, 7); g.fill();
  }
}
function prellbock(x, gleisY, rechts) {
  const r = rechts ? 1 : -1;
  g.fillStyle = F.BODEN; if (rechts) g.fillRect(x + 12, gleisY - 1, 4000, 16); else g.fillRect(x - 4012, gleisY - 1, 4000, 16);
  g.strokeStyle = '#3d434b'; g.lineWidth = 3; g.beginPath();
  g.moveTo(x + r * 2, gleisY); g.lineTo(x + r * 2, gleisY - 20); g.moveTo(x + r * 12, gleisY); g.lineTo(x + r * 2, gleisY - 16); g.stroke();
  g.fillStyle = F.ROT; g.fillRect(x - 3, gleisY - 26, 10, 9); g.fillStyle = '#fff'; g.fillRect(x - 0.5, gleisY - 26, 3, 9);
  g.fillStyle = '#3d434b'; g.fillRect(x - 3, gleisY - 17.5, 10, 1.5);
}

// ---------------------------------------------------------------- Platzhalter-Züge
function zug(b, x, y, w, h, weg, rueck) {
  // Fahrzeuge in der Reihenfolge der Zusammenstellung; rückwärts läuft das letzte voraus, die Lok schiebt
  const fz = b && b.length ? b : [{a:'LOK', l:'DIESEL'}];
  const anteil = fz.map(f => f.a === 'TRIEBWAGEN' ? 2.4 : f.a === 'LOK' ? 1.15 : 1), summe = anteil.reduce((p, q) => p + q, 0);
  const luecke = 3; let nutz = w - luecke * (fz.length - 1);
  if (fz.length === 1) nutz = Math.min(w, h * (fz[0].a === 'TRIEBWAGEN' ? 6 : 2.6));
  let rechts = x;
  for (let n = 0; n < fz.length; n++) {
    const k = rueck ? fz.length - 1 - n : n, f = fz[k], fw = Math.max(8, nutz * anteil[k] / summe), fx = rechts - fw;
    g.save();
    if (rueck) { g.translate(2 * fx + fw, 0); g.scale(-1, 1); }
    if (f.a === 'LOK') lok(f.l, fx, y, fw, h, rueck ? -1 : weg);
    else if (f.a === 'TRIEBWAGEN') triebwagen(fx, y, fw, h);
    else if (f.a === 'PERSONEN') personenwagen(fx, y, fw, h);
    else gueterwagen(fx, y, fw, h, k);
    g.restore();
    if (n < fz.length - 1) { g.strokeStyle = F.SCHIENE; g.lineWidth = 2; g.beginPath(); g.moveTo(fx - luecke, y - h * 0.2); g.lineTo(fx, y - h * 0.2); g.stroke(); }
    rechts = fx - luecke;
  }
}
function oval(x, y, w, h) { g.beginPath(); g.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, 7); g.fill(); }
function raeder(x, y, w, r, n, felge) {
  for (let k = 0; k < n; k++) {
    const cx = n === 1 ? x + w / 2 : x + r + 3 + k * (w - 2 * r - 6) / (n - 1);
    g.fillStyle = '#1f2328'; oval(cx - r, y - 2 * r, 2 * r, 2 * r); g.fillStyle = felge; oval(cx - r * 0.55, y - r * 1.55, r * 1.1, r * 1.1);
  }
}
function fr(f, x, y, w, h, r) { g.fillStyle = f; rr(x, y, w, h, r); g.fill(); }
function poly(f, p) { g.fillStyle = f; g.beginPath(); p.forEach((q, i) => g[i ? 'lineTo' : 'moveTo'](q[0], q[1])); g.closePath(); g.fill(); }
function personenwagen(x, y, w, h) {
  const r = h * 0.13, unten = y - r * 1.5;
  fr('#2e8b3d', x, y - h, w, h - r * 1.5, 4); fr('#1f6a2c', x, y - h, w, h * 0.16, 4);
  const n = Math.max(2, Math.floor(w / (h * 0.42))), fb = w / (n * 1.5 + 0.5);
  for (let k = 0; k < n; k++) fr('#f7c948', x + fb * 0.5 + k * fb * 1.5, y - h * 0.74, fb, h * 0.3, 1.5);
  g.fillStyle = '#17501f'; g.fillRect(x + 2, unten - 3, w - 4, 3); raeder(x, y, w, r, 4, '#9aa3ad');
}
function gueterwagen(x, y, w, h, nr) {
  const r = h * 0.13, hoehe = h * 0.72, offen = nr % 3 === 1, kh = offen ? hoehe * 0.6 : hoehe;
  fr(offen ? '#7d8792' : '#9a5b2e', x, y - r * 1.5 - kh, w, kh, 2);
  g.strokeStyle = 'rgba(0,0,0,0.28)'; g.lineWidth = 1;
  const n = Math.max(3, Math.floor(w / 14));
  for (let k = 1; k < n; k++) { const lx = x + k * w / n; g.beginPath(); g.moveTo(lx, y - r * 1.5 - kh + 2); g.lineTo(lx, y - r * 1.5 - 2); g.stroke(); }
  if (offen) { g.fillStyle = '#3b3f45'; g.beginPath(); g.ellipse(x + w / 2, y - r * 1.5 - hoehe * 0.6, (w - 8) / 2, hoehe * 0.25, 0, Math.PI, 0); g.fill(); }
  raeder(x, y, w, r, 2, '#9aa3ad');
}
function triebwagen(x, y, w, h) {
  const r = h * 0.13, unten = y - r * 1.5, oben = y - h, nase = Math.min(h * 0.7, w * 0.18), hh = unten - oben;
  poly('#f3ead2', [[x + nase * 0.6, oben], [x + w - nase, oben], [x + w, oben + hh * 0.55], [x + w, unten], [x, unten], [x, oben + hh * 0.5]]);
  const band = oben + hh * 0.58; poly('#b3262b', [[x, band], [x + w, band], [x + w, unten], [x, unten]]);
  const n = Math.max(3, Math.floor((w - 2 * nase) / (h * 0.45))), fb = (w - 2 * nase) / (n * 1.4 + 0.4);
  for (let k = 0; k < n; k++) fr('#3d5a80', x + nase + fb * 0.4 + k * fb * 1.4, oben + hh * 0.2, fb, hh * 0.3, 1.5);
  poly('#3d5a80', [[x + w - nase * 0.9, oben + hh * 0.2], [x + w - nase * 0.35, oben + hh * 0.2], [x + w - nase * 0.05, oben + hh * 0.5], [x + w - nase * 0.9, oben + hh * 0.5]]);
  g.fillStyle = '#fff4b8'; oval(x + w - 6, band + 2, 5, 5); raeder(x, y, w, r, 6, '#9aa3ad');
}
function lok(art, x, y, w, h, weg) {
  const r = h * 0.13, unten = y - r * 1.5, oben = y - h;
  if (art === 'DAMPF') {
    const q = h * 0.2, u = y - q * 1.3;
    fr('#22262b', x, oben + h * 0.08, w * 0.3, u - oben - h * 0.08, 2.5); fr('#22262b', x + w * 0.27, oben + h * 0.3, w * 0.7, u - oben - h * 0.34, 7);
    g.fillRect(x + w * 0.78, oben + h * 0.08, w * 0.07, h * 0.26); fr('#22262b', x + w * 0.5, oben + h * 0.2, w * 0.1, h * 0.14, 3);
    fr('#f7c948', x + w * 0.06, oben + h * 0.18, w * 0.16, h * 0.2, 1.5);
    g.fillStyle = '#b3262b'; g.fillRect(x + 2, u - 4, w - 2, 4); poly('#b3262b', [[x + w, u - 8], [x + w + 6, u], [x + w, u]]);
    raeder(x + w * 0.02, y, w * 0.96, q, 4, '#c8323a');
    g.strokeStyle = '#9aa3ad'; g.lineWidth = 2; g.beginPath(); g.moveTo(x + w * 0.2, y - q); g.lineTo(x + w * 0.85, y - q); g.stroke();
    if (weg >= 0) for (let k = 0; k < 3; k++) {
      const p = ((weg * 0.25 + k * 0.33) % 1 + 1) % 1, gr = 6 + p * 12;
      g.fillStyle = 'rgba(153,161,171,' + (0.55 * (1 - p)) + ')'; oval(x + w * 0.815 - p * 34 - gr / 2, oben + h * 0.02 - p * 16 - gr / 2, gr, gr);
    }
  } else if (art === 'ELEKTRO') {
    fr('#2c5e9e', x, oben + h * 0.2, w, unten - oben - h * 0.2, 5); g.fillStyle = '#f3ead2'; g.fillRect(x, oben + h * 0.56, w, h * 0.06);
    fr('#cfe2f7', x + w * 0.06, oben + h * 0.28, w * 0.16, h * 0.2, 1.5); fr('#cfe2f7', x + w * 0.78, oben + h * 0.28, w * 0.16, h * 0.2, 1.5);
    g.strokeStyle = '#3d434b'; g.lineWidth = 1.6; const px = x + w * 0.5; g.beginPath();
    g.moveTo(px - w * 0.12, oben + h * 0.2); g.lineTo(px, oben + h * 0.06); g.lineTo(px + w * 0.12, oben + h * 0.2);
    g.moveTo(px - w * 0.14, oben + h * 0.05); g.lineTo(px + w * 0.14, oben + h * 0.05); g.stroke();
    g.fillStyle = '#fff4b8'; oval(x + w - 6, oben + h * 0.5, 5, 5); raeder(x, y, w, r, 4, '#9aa3ad');
  } else if (art === 'BATTERIE') {
    fr('#1f8a8a', x, oben + h * 0.22, w, unten - oben - h * 0.22, 5); fr('#cfe2f7', x + w * 0.74, oben + h * 0.3, w * 0.2, h * 0.2, 1.5);
    const bx = x + w * 0.16, by = oben + h * 0.36, bw = w * 0.34, bh = h * 0.26;
    fr('#fff', bx, by, bw, bh, 2); g.fillRect(bx + bw, by + bh * 0.3, 3, bh * 0.4); g.fillStyle = '#2e9e3e'; g.fillRect(bx + 2, by + 2, bw * 0.65, bh - 4);
    g.fillStyle = '#fff4b8'; oval(x + w - 6, oben + h * 0.52, 5, 5); raeder(x, y, w, r, 4, '#9aa3ad');
  } else {
    fr('#c8452b', x, oben + h * 0.3, w, unten - oben - h * 0.3, 3); fr('#c8452b', x + w * 0.36, oben + h * 0.06, w * 0.3, h * 0.4, 2.5);
    g.fillStyle = '#f3ead2'; g.fillRect(x, oben + h * 0.52, w, h * 0.07); fr('#3d5a80', x + w * 0.4, oben + h * 0.12, w * 0.22, h * 0.18, 1.5);
    g.fillStyle = '#2a2e34'; g.fillRect(x + 3, unten - 3, w - 6, 3); g.fillStyle = '#fff4b8'; oval(x + w - 6, oben + h * 0.38, 5, 5);
    raeder(x, y, w, r, 4, '#9aa3ad');
  }
}
