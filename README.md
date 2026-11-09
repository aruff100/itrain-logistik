# iTrain-Logistik

Logistik für die Modellbahnsteuerung [iTrain](https://www.berros.eu/): Reisende und Güter, die die Züge auf deiner Anlage von Bahnhof zu Bahnhof bringen – in Echtzeit, während iTrain fährt.

*English: iTrain-Logistik adds passengers and freight to an [iTrain](https://www.berros.eu/) model railway layout. It reads the iTrain layout file, keeps its own logistics data, and follows the trains live over iTrain's XTC interface. The user interface is available in German, English, Dutch, French, Spanish and Italian.*

## Was das Programm macht

iTrain kennt Gleisplan, Blöcke, Bahnhöfe und Züge. iTrain-Logistik ergänzt, was dort fehlt: Orte mit Reisenden, Betriebe mit Rezepten (Holz → Bretter → Möbel), Lager, Schattenbahnhöfe als Verbindung zur Außenwelt und die Ladefähigkeit jedes Wagens. Hält ein Zug an einem Bahnsteig oder Ladegleis, steigen Reisende ein und aus, Güter werden geladen und entladen.

- **Nur lesend gegenüber iTrain:** Die iTrain-Datei (`.tcdz`) wird nie verändert. Die eigenen Daten stehen in einer eigenen Datei (`.logz`) und in einer Fahrzeugdatei (`<Anlage>.fahrzeuge.xml`).
- **Online über XTC:** iTrain meldet über seine XTC-Schnittstelle (Port 7128) für jeden Zug Einfahrt, Halt mit Wartezeit, Abfahrt, Fahrtrichtung und Modellzeit.
- **Eingebauter Simulator:** Fahrten lassen sich ohne laufendes iTrain per Drag & Drop durchspielen, aufzeichnen und abspielen.
- **Verkehrs-Assistent:** Er richtet Bahnhöfe und Gleise ein, legt fest, was von wo nach wo gefahren wird, und prüft, ob jede Nachfrage einen Lieferanten und jeder Reisende ein Ziel hat.
- **Zuganzeiger:** Eine Abfahrtstafel zeigt, wo Reisende warten und welche Güter wohin müssen, samt geeigneten Zügen. Es gibt sie als Fenster und im Browser, auch je Bahnhof.
- **Browser Logistik:** Die Anlage lässt sich im Browser ansehen, auch auf Handy oder Tablet. Es gibt eine Seite je Modul und einen QR-Code zum Öffnen.
- **Tabs und Module:** Große Anlagen und Modulanlagen lassen sich auf mehrere Reiter aufteilen.
- **Geräusche:** Ein Klick auf ein Symbol spielt ein passendes Geräusch ab. Die mitgelieferten Geräusche sind gemeinfrei (CC0), eigene lassen sich hinzufügen.
- **Sprachen:** Die Oberfläche gibt es auf Deutsch, Englisch, Niederländisch, Französisch, Spanisch und Italienisch.

Das Handbuch liegt im Ordner [`handbuch`](handbuch).

## Voraussetzungen

- iTrain 6 mit erreichbarer XTC-Schnittstelle, für den Online-Betrieb. Ohne iTrain funktionieren der Simulator und die Einrichtung.
- Windows, macOS oder Linux.
- Zum Bauen ein JDK 25. Gradle lädt Liberica JDK 25 (BellSoft) beim ersten Build selbst herunter.
- Unter Linux brauchen mp3-Geräusche die ffmpeg-Bibliotheken des Systems (libavcodec, libavformat). Das Programm weist darauf hin, wenn sie fehlen.

## Herunterladen

Fertige Installer für Windows (.msi), macOS (.dmg) und Linux (.deb) gibt es unter [Releases](../../releases). Sie bringen ihre eigene Java-Laufzeit mit.

## Bauen und starten

```
./gradlew run          # Programm starten
./gradlew build        # kompilieren und verpacken
./gradlew jlink        # eigenständiges Laufzeit-Abbild
./gradlew jpackage     # Installer für das laufende Betriebssystem (.msi, .dmg, .deb)
```

Unter Windows verwendet man `gradlew.bat` statt `./gradlew`.

## Erste Schritte

1. Datei → **Neue Konfiguration...** und die iTrain-Datei (`.tcdz`) wählen.
2. Die Ladefähigkeit der Wagen und Triebwagen ergänzen. Das Programm fragt danach.
3. **Anlage bearbeiten** öffnen. Der Verkehrs-Assistent führt durch Bahnhöfe, Gleise, Orte und Betriebe.
4. Einstellungen → **iTrain Verbindung**: den Rechner eintragen, auf dem iTrain läuft, und den XTC-Port (Vorgabe 7128).

## Aufbau des Projekts

| Ordner | Inhalt |
|---|---|
| `src/main/java` | Programmcode (JavaFX), Echtzeit-Kern im Paket `kern` |
| `src/main/resources` | Oberfläche, Symbole, Flaggen, Stylesheets |
| `sprachen` | Wörterbücher der Oberfläche (`de:` / `<code>:` je Text) |
| `texte` | Dialog- und Hilfetexte, je Sprache eine Datei |
| `geraeusche` | Mitgelieferte Geräusche (CC0) mit Quellenangaben |
| `handbuch` | Handbuch (ODT) |
| `packaging` | Programmsymbole für die Installer |

Fehlt eine Übersetzung, bleibt der deutsche Text stehen. Er wird beim Beenden in `<Logistik-Ordner>/sprachen/fehlt-<code>.txt` gesammelt. Eine Datei `<Logistik-Ordner>/sprachen/<code>.txt` geht der mitgelieferten vor. So lassen sich Übersetzungen ohne neues Bauen verbessern.

## Lizenz

Copyright © 2026 Andre Ruff

iTrain-Logistik ist freie Software: Du kannst es unter den Bedingungen der GNU General Public License, Version 3, wie von der Free Software Foundation veröffentlicht, weitergeben und/oder ändern. Der vollständige Text steht in [`LICENSE`](LICENSE).

Das Programm wird in der Hoffnung verbreitet, dass es nützlich ist, aber **ohne jede Gewährleistung**, sogar ohne die implizite Gewährleistung der Marktreife oder der Eignung für einen bestimmten Zweck.

## Fremde Bestandteile

- **QR-Code-Generator** von Project Nayuki, MIT-Lizenz, in `src/main/java/.../qr/` (Lizenztext in den Quelldateien).
- **Geräusche** von freesound.org, alle CC0 1.0 (gemeinfrei). Die Urheber stehen in [`geraeusche/Quellen.txt`](geraeusche/Quellen.txt).
- **OpenJFX (JavaFX)** und **Liberica JDK**, GPL v2 mit Classpath Exception. Sie werden beim Bauen geladen und sind im Laufzeit-Abbild bzw. Installer enthalten.

iTrain ist ein Produkt von Berros. iTrain-Logistik ist ein unabhängiges Projekt und wird von Berros weder entwickelt noch unterstützt.
