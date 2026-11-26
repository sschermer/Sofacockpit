# Sofacockpit

Sofacockpit ist eine Android-App, die einen **TCL-Fernseher mit Google TV** und die **LG-Soundbar S95QR** in einer Fernbedienung zusammenführt. Die App richtet sich zunächst an den TCL 85C89L, der im Gerätemenü als 85C8L angezeigt wird.

## Bedienkonzept (ab v0.7.0)

Die drei Tabs **Start**, **TV** und **Sound** bleiben am oberen Bildschirmrand sichtbar. Kurzes Tippen öffnet den ersten Screen des jeweiligen Bereichs. Langes Drücken auf TV oder Sound öffnet ein Dropdown mit direkter Seitenauswahl. Die Pfeile an den beiden Tabs weisen auf das Schnellmenü hin. Die Wischgesten wechseln nur innerhalb der ausgewählten Kategorie; die Startseite hat keine Nachbarseiten. Die drei unteren Navigationspunkte sind in TV und Sound direkt anklickbar. Seit v0.9.1 wird in derselben Zeile der aktuelle Seitenname angezeigt. Es gibt kein kategorienübergreifendes Wischen oder Umlaufen am Ende einer Kategorie.

| Tab | Seite 1 | Seite 2 | Seite 3 |
| --- | --- | --- | --- |
| Start | Startbildschirm | – | – |
| TV | Fernbedienung | Quellen | Bild & System |
| Sound | Klangsteuerung | Klangprofile | Feinabstimmung |

Alle sechs Gerätescreens sind angelegt. Nicht unterstützte Funktionen sind als noch offen gekennzeichnet; sie senden keine simulierten Befehle. Die bisherigen funktionalen Screens und ihre Gerätesitzungen werden beim Navigieren nicht neu erstellt. Horizontale Lautstärke-/Kanalpegelregler erhalten Vorrang gegenüber dem Seitenwechsel; vertikales Scrollen bleibt möglich. Die tatsächliche Touch-Bedienung muss am Gerät geprüft werden.

### Startbildschirm

- **TV** mit Verbindungsstatus. Den Status antippen, um den Verbindungsdialog zu öffnen. Bei bestätigter Verbindung erscheinen Stumm- und Power-Symbole; Farbe basiert auf tatsächlich gemeldetem Status.
- **Streaming-Leiste** mit Netflix, Prime Video, Apple TV+, Disney+ und Dyn als Vorauswahl, vier sichtbaren Schnellwahltasten und horizontalem Scrollen. Plus öffnet den Dienstekatalog; langes Drücken bearbeitet, verschiebt oder entfernt einen Eintrag.
- **Lautstärke** mit − und + für einzelne TV-Tastenschritte sowie einer Anzeige des tatsächlich gemeldeten Pegels, sofern verfügbar. Direktes Setzen eines absoluten Wertes ist noch nicht unterstützt.
- **Soundbar** mit Ein/Aus, Night, DRC, Surround und Audioformat. Das Format wird aus der Soundbar-Meldung übernommen, nicht geschätzt.

Wischen auf der Streaming-Leiste scrollt die Dienste. Der Lautstärkepegel ist zunächst nur eine Anzeige, kein absoluter Regler. Die Startseite wechselt nicht durch horizontales Wischen in andere Kategorien.

### TV

- **Fernbedienung:** keine äußere Karte und keine feste 284-dp-Größe mehr. Das runde Steuerkreuz nutzt die verfügbare Breite, bei ausreichend Platz mit nur 12 dp seitlichem Rand. Zurück/Home, Wiedergabe/Pause und Spultasten darunter. Für kleine oder querformatige Displays bleibt Scrollen möglich. Eine kompakte Statusanzeige öffnet die Verbindung. Die Tastatur ist sichtbar als noch nicht implementiert gekennzeichnet; Mauszeiger später.
- **Quellen:** Android-Standardtasten für HDMI 1–4 und die Eingangsauswahl. Ab v1.0.1 kompakte 2×2-HDMI-Schnellwahl mit mindestens 48 dp statt 70 dp Tastenhöhe und 6 dp vertikalem Abstand. Auch die allgemeine TV-Eingangsauswahl hat mindestens 48 dp Höhe. Bei längeren Namen und großer Systemschrift dürfen die Tasten mitwachsen. HDMI-Bezeichnungen können lokal durch langes Drücken geändert werden; sie sind noch nicht Teil der Sicherungsdatei. App-Katalog aus eingebauten Diensten und der vorhandenen Favoritenliste, kein Inventar installierter Apps. Antippen sendet einen App-Link, langes Drücken verwaltet Favoriten. Eigene Apps werden derzeit über die Favoritenliste angelegt.
- **Bild & System:** Tastenbefehl zum Öffnen der TV-Einstellungen. Direkte Bildmodus-, Helligkeits-, Kontrast-, Timer- und Energieparameter noch nicht implementiert; kein erfundener Status. Standard-Keycodes entsprechen der [Android KeyEvent-Dokumentation](https://developer.android.com/reference/android/view/KeyEvent) und der [Remote-v2-Protokollreferenz](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto). Unterstützung der neuen Tasten am TCL und in einzelnen Apps muss am Gerät getestet werden.

### Sound (Tonmaschine)

- **Klangsteuerung:** Lautstärke, gemeldete Eingänge und Audioformat, Night Time/DRC/Surround, unterstützte Klangmodi, Power. Seit v0.9.1 stehen der Titel Klangmodus und der tatsächlich gemeldete aktuelle Modus in einer kompakten Zeile. Antippen des Modusknopfs öffnet das Dropdown mit den von der Soundbar unterstützten Modi; ein Häkchen markiert den gemeldeten Modus. Die drei Klangoptionen bleiben auf derselben Seite. Unbekannter Modus und Standby/Offline werden korrekt berücksichtigt. Kartenabstände sind kleiner; nur redundante normale Statusmeldungen werden ausgeblendet, Fehler- und Befehlsrückmeldungen bleiben sichtbar. Auf kleinen Displays und bei großer Systemschrift bleibt Scrollen möglich. Unbekannte Werte werden nicht als bestätigte Werte dargestellt. Direkter Soundbar-Mute-Befehl noch nicht implementiert.
- **Klangprofile:** bestehende lokale oder NAS-Profile bleiben im unveränderten Windows-kompatiblen Format. Antippen wählt ein Profil zur Übertragung, langes Drücken oder ⋮ öffnet Anwenden, Umbenennen, Kopieren, Überschreiben und Löschen. A–Z/Z–A-Sortierung. Kopieren/Umbenennen verwendet gespeicherte Werte und bei NAS dieselbe Schreibsperre wie bisher; kein Überschreiben fremder Profile. Einstellungen für den Profilspeicher sind über das Zahnrad erreichbar.
- **Feinabstimmung:** sieben gemeldete Kanalpegel mit Schieberegler und Plus/Minus gemäß gerätespezifischen Wertebereichen. Bass/Höhen werden aus den Antworten angezeigt; direkte Änderung, Neural:X und IMAX Enhanced noch nicht in der Android-Implementierung verifiziert.

Alle Sound-Seiten verwenden denselben vorhandenen MainViewModel. Die zentrale Verbindung und der verschlüsselte Konfigurationsimport bleiben erhalten. Gerätetests von Layout, neuen Befehlen, Touch-Gesten und NAS-Änderungen stehen aus.

## Themes (ab v0.8.0)

**Start kurz antippen** öffnet weiterhin die Startseite. **Start lange drücken → Design auswählen** öffnet die Theme-Galerie mit gerenderten Miniaturvorschauen und Kennzeichnung des ausgewählten Designs. Die Auswahl wird gespeichert und baut nur die Präsentationsoberfläche neu auf; der TV-Client, das Soundbar-ViewModel und die aktuelle Kategorie/Seite bleiben bestehen. Lokale Scrollpositionen und nicht abgeschlossene UI-Entwürfe können beim Wechsel zurückgesetzt werden. Keine Gerätebefehle oder neue Verbindungen durch den reinen Designwechsel.

Vorinstallierte Designs (alle als echte .sofatheme-Pakete in app/src/main/assets/themes):

- **Minimal Premium** – dezentes Graphit, abgerundete Flächen, grüner Akzent (Standard).
- **AV Konsole** – metallische Oberflächen, technische Konturen, kondensierte native Schrift.
- **High-Tech HUD** – kühle dunkle Flächen, angeschnittene Ecken, segmentierte Ringe.
- **Soft Industrial** – warme dunkle Neutraltöne, weiche Formen und gedämpftes Grün.
- **Executive Monochrome** – flache dunkle Flächen, reduzierte Konturen, grüner Unterstrich am aktiven Tab.

Der Theme-Wähler bietet **Theme importieren**, **Aktuelles Theme exportieren** und das Entfernen eigener Themes (langes Drücken auf deren Vorschau). Import wählt nicht automatisch; danach ein Theme antippen. IDs vorinstallierter Themes und bereits vorhandene IDs werden nicht überschrieben. Maximal zwölf importierte Themes. Die fünf Originaldateien sind auch im GitHub-Release enthalten. Die verschlüsselte .sofacfg speichert optional die ausgewählte Theme-ID; bei nicht vorhandenem eigenem Design wird Minimal Premium verwendet. Eigene Theme-Pakete separat importieren (idealerweise vor dem Konfigurationsimport). Ältere Konfigurationsdateien ohne Theme-ID ändern das aktuelle Design nicht. Geräteadressen und Zugangsdaten gehören nie in .sofatheme-Dateien.

Das Steuerkreuz ist in allen Designs eine zusammenhängende Kreisfläche mit vier über die Diagonalen getrennten Sektoren und mittigem OK. Jeder Sektor ist vollständig antippbar, nicht nur das Pfeilsymbol. Android-Haptik und separate virtuelle Accessibility-Knoten für die fünf Aktionen; horizontales Wischen bleibt im Pager möglich. Die Darstellung orientiert sich an den Designskizzen, nicht an eingebetteten Screenshots. Native Standarddialoge/Dateiauswahl behalten teilweise ihre Android-Systemoptik. Designwechsel, Gesten und tatsächliche Bedienung am Gerät sind noch zu testen.

### Eigenes Theme erstellen – Dateiformat v1

Die Endung .sofatheme bezeichnet einen ZIP-Container mit manifest.json und theme.json direkt im Stamm. Beispielpakete lassen sich exportieren, entpacken, ändern und erneut zippen; eigene, eindeutige ID vergeben. JSON muss UTF-8 sein. Es werden ausschließlich deklarative Daten gelesen, keine Skripte, kein HTML/SVG, keine Apps und keine externen URLs. Keine Extraktion der ZIP-Pfade.

manifest.json (alle Felder erforderlich):

```json
{
  "format": "sofacockpit-theme",
  "schema": 1,
  "id": "mein-design",
  "name": "Mein Design",
  "author": "Eigener Name",
  "version": "1.0"
}
```

theme.json (alle Felder erforderlich):

```json
{
  "background": "#101719",
  "panel": "#192326",
  "control": "#283235",
  "text": "#F2F5F9",
  "muted": "#B4C1C3",
  "accent": "#58D792",
  "selected": "#20392E",
  "border": "#455459",
  "dial": "#263332",
  "style": "soft",
  "font": "sans",
  "cornerDp": 14,
  "paddingDp": 12,
  "fontScale": 1.0,
  "centerRatio": 0.32
}
```

Erlaubte Werte: style flat / soft / metal / hud; font sans / condensed / mono; cornerDp 0–24, paddingDp 8–18, fontScale 0,90–1,12, centerRatio 0,28–0,40. Alle Farben als deckendes #RRGGBB. Dunkle Flächen mit mindestens 4,5:1 Grundkontrast zu Text, sekundärem Text und Akzent; Rot und Gelb für Fehler und Verbindungsvorgang sind vorgegeben. Kondensierte Schrift ist in nativen Views verfügbar; Compose nutzt in Version 1 SansSerif als Fallback. Die begrenzten Stiloptionen lassen keine Funktions-, Befehls- oder Layoutänderungen zu.

Optionale Dateien (jeweils PNG **oder** WebP, nicht beide):
- preview.png / preview.webp (bis 768 × 768 Pixel), sonst Live-Miniatur.
- textures/panel.png / textures/panel.webp (bis 512 × 512), dezentes Overlay für gemeinsame Panel- und Steuerkreuzflächen.
- icons/arrow.png / icons/arrow.webp (bis 128 × 128), nach oben zeigendes Pfeilsymbol, das für die anderen Richtungen gedreht wird.

Maximal 1 MiB Archivgröße, 1,5 MiB entpackte Daten, 16 KiB pro JSON. Nur erlaubte Pfade und Formate werden akzeptiert; fehlerhafte, zu große, inkompatible, doppelte oder kontrastarme Themes werden abgewiesen. Keine frei importierbaren Schriftdateien, beliebigen Icons oder zusätzlichen Layout-/Animationsdefinitionen in Schema 1.

## Software-Updates (ab v0.9.0)

**Start lange drücken → Software-Updates** öffnet die Update-Einstellungen. Das App-Menü enthält außerdem Design auswählen, Verbindungen und Über Sofacockpit. Kurzes Antippen von Start bleibt unverändert.

- Automatische Prüfung beim Kaltstart der App und per Pull-to-Refresh (standardmäßig an), keine Hintergrunddienste und keine regelmäßigen Netzwerkabfragen im Hintergrund.
- Kanal **Releases und Pre-Releases** (Standard für die Entwicklungsphase) oder **Nur stabile Releases**. Die reguläre GitHub-Releases-Liste wird seitenweise geprüft; Entwürfe sowie Releases ohne vollständig hochgeladene Sofacockpit.apk werden ignoriert. Gültige Tags sind sofacockpit-vMAJOR.MINOR.PATCH oder vMAJOR.MINOR.PATCH mit drei numerischen Komponenten. Die höchste passende Versionsnummer gewinnt, nicht das Veröffentlichungsdatum oder die Reihenfolge. GitHubs Boolean prerelease entscheidet über den Typ.
- Das Popup zeigt den Typ (gelbe Entwicklungswarnung bzw. stabiles Release), alte/neue Versionsnummer, Dateigröße und Release-Notizen als reinen Text.
- **Aktualisieren**: Download erst nach Zustimmung und ggf. Freigabe der Android-Installationsberechtigung für Sofacockpit. **Später** (oder Schließen): nächster Kaltstart. **Überspringen**: automatische Hinweise für diese und ältere Versionen im gewählten Kanal unterdrücken. Die manuelle Prüfung kann übersprungene Versionen wieder anbieten. Einstellungen bleiben lokal gespeichert.
- Bei fehlendem Internet, GitHub-Abfragelimit oder ungültiger Antwort bleibt die App nutzbar. Die automatische Prüfung unterbricht den Start nicht; die manuelle Prüfung meldet Fehler. Das Popup wird nur im Vordergrund gezeigt, nicht über andere Dialoge.

### Sicherheit und Installation

Es wird ohne Token ausschließlich das öffentliche Repository sschermer/Sofacockpit über HTTPS abgefragt. Downloads sind auf das exakte Release-Asset Sofacockpit.apk und die vorgesehenen GitHub-Download-Hosts begrenzt. Die Dateigröße wird kontrolliert (max. 128 MiB), die von GitHub bereitgestellte SHA-256-Prüfsumme falls vorhanden verglichen. Androids Paketmanager überprüft die APK-Informationen: Paketname de.sofacockpit.app, zur angebotenen Version passende versionName, strikt höherer versionCode und fest hinterlegtes Zertifikat (SHA-256 wie bei der CI-Signatur); auch die installierte App muss mit diesem Zertifikat signiert sein. Kein Downgrade, kein Update einer Debug-Signatur. Unvollständige und abgelehnte Downloads werden verworfen.

Die geprüfte APK liegt im privaten Cache, wird nur über einen eng begrenzten FileProvider mit Leseberechtigung an den Android-Installer übergeben und kann dort zusätzlich geprüft werden. Kein Umgehen der Installationsbestätigung, Play Protect oder Android-Richtlinien. Die Installation erfolgt als normales Paketupdate ohne Deinstallation; TV-Kopplung, Themes, NAS-Einstellungen und andere gespeicherte Einstellungen bleiben erhalten. Wenn Android ablehnt oder der Benutzer abbricht, bleibt die alte Version installiert. Bei einer Activity-Neuerstellung während Download oder Berechtigungsdialog kann ein erneutes Anstoßen erforderlich sein. Alte Update-Cache-Dateien werden bei weiteren Downloads nach zwei Tagen entfernt.

Die Release-Workflows müssen weiterhin monoton steigende versionCode-Werte, zur Tag-Version passende versionName und die feste Signatur verwenden. Pre-Releases werden wie bisher mit --prerelease veröffentlicht; für eine reguläre Freigabe dieses Flag entfernen. Die Update-Funktion wurde beim Wechsel von v0.9.0 auf v0.9.1 auf dem realen Gerät erfolgreich getestet. Das erste reguläre Release ist v1.0.0; die neue Pull-to-Refresh-Geste steht noch zum Gerätetest an.

## Pull-to-Refresh (v1.0.0)

Am oberen Ende der scrollbaren Seite nach unten ziehen und loslassen. Eine kurze Ladeanzeige bleibt bis zur Rückmeldung der Geräte sichtbar. Auf Start werden TV und Soundbar parallel geprüft, auf den TV- bzw. Sound-Seiten jeweils das passende Gerät. Alternativ Start lange drücken → Verbindungen aktualisieren (bezogen auf die aktuelle Kategorie).

- **TV:** Die vorhandene Remote-v2-Sitzung wird anhand einer frisch empfangenen gültigen Protokollnachricht bestätigt. Das Protokoll bietet hier keine gesonderte Statusabfrage; es wird kein Tastendruck als Test gesendet. Bleibt die Antwort aus, wird nach zehn Sekunden bzw. bei Verbindungsfehler einmal mit der vorhandenen Kopplung neu verbunden. Die Rückmeldung ist keine Garantie für alle TV-Statuswerte.
- **Soundbar:** Die vier bekannten Statusbereiche werden erneut abgefragt. Eine neue Antwort bestätigt die Verbindung; nur wenn alle vier Bereiche antworten, werden die Werte als aktualisiert gemeldet. Bleiben Antworten aus oder befindet sich die gewünschte Verbindung im Fehler-Backoff, wird einmal neu verbunden. Der normale Soundbar-Wiederverbindungsmechanismus bleibt erhalten.
- **Sicherheit:** Kein Einschaltbefehl, keine Neukopplung, kein Verbindungsversuch für bewusst getrennte Geräte, geänderte Adressen oder Geräte ohne zuvor gewünschte Verbindung in dieser App-Sitzung. Bestätigter Standby wird respektiert. Bei nicht gemeldetem Powerstatus kann ein normaler Netzwerkzugriff je nach Gerät wecken. Fehlende Netzwerkberechtigung wird gemeldet, nicht umgangen.
- **Software:** Parallel wird bei aktivierter automatischer Updateprüfung auf GitHub nach neueren Releases gesucht, auch ohne Geräteverbindung. Das bestehende Update-Popup erscheint nach der Geräteprüfung; Kanal, übersprungene Versionen, Zustimmung und APK-Prüfungen bleiben unverändert. Die Prüfung benötigt Internet; kein automatischer Download. Einstellungen unter Start lange drücken → Software-Updates.
- Kein Umbau der Kategorie- oder Swipe-Navigation. Normale vertikale Scrollbewegungen bleiben erhalten, horizontale Gesten und Schieberegler werden geschützt. Auf der Sound-Seite beginnt der Pull im scrollbaren Inhaltsbereich. Beim Verlassen des Vordergrunds wird eine noch ausstehende Refresh-Prüfung beendet, damit kein verzögerter Wiederverbindungsversuch startet. Die neue Geste muss noch auf einem realen Gerät getestet werden.

## Verbindung und Projektstand

Die Steuerung ist für das lokale Heimnetz geplant. Für den Fernseher ist der **Android TV Remote Service (Remote Protocol v2)** mit Kopplungscode implementiert. Die Schnittstelle ist nicht als offizielle Drittanbieter-API dokumentiert; Kompatibilität mit zukünftigen Service-Versionen kann nicht garantiert werden. Für die Soundbar soll Sofacockpit auf der bereits erprobten lokalen Steuerung der Tonmaschine aufbauen.

Das Android-Projekt verbindet die native Sofacockpit-Navigation mit dem Compose-Screen der Tonmaschine. Beim Öffnen ist standardmäßig der Startbildschirm sichtbar. Er zeigt auf der Tonmaschine-Seite und auf dem Startbildschirm denselben Verbindungszustand. Die Streaming-Leiste und der Lautstärkeregler behalten ihre eigenen Gesten. Der Nutzer hat die TV-Steuerverbindung und das Steuerkreuz mit v0.3.3 am TCL bestätigt. Seit v0.4.0 sind App-Link-Start, relative TV-Lautstärke, Stumm und Power implementiert; Gerätetests dieser neuen Funktionen stehen aus.

Mit der offiziellen Google-TV-App wurden am TCL bereits die Navigation und die Weitergabe von Lautstärkebefehlen an die LG-Soundbar getestet. Dabei war das Handy per WLAN und der Fernseher per LAN im selben Heimnetz verbunden. Das ist kein Test von Sofacockpit.

Die Soundbar-Seite verwendet den Android-Code aus der öffentlichen [Tonmaschine-Implementierung](https://github.com/sschermer/Soundbar-APP/tree/main/android). Die übernommenen Dateien behalten deren MIT-Lizenzhinweis in [TONMASCHINE_LICENSE.md](TONMASCHINE_LICENSE.md); Android-Abhängigkeiten und Hinweise sind in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dokumentiert. TV-Steuerkreuz am TCL bestätigt (v0.3.3); weitere TV- und Soundbar-Gerätetests stehen aus.

Zu prüfen sind insbesondere das Aufwecken des TV nach längerem Standby, das direkte Starten der Streaming-Apps, das Auslesen des Audioformats und das Auslesen und Setzen eines absoluten Lautstärkewerts für den Schieberegler.

### TV verbinden (ab v0.3.0)

1. Fernseher einschalten, Handy im gleichen Heimnetz (kein isoliertes Gast-WLAN) verbinden.
2. Auf dem Startbildschirm den TV-Status antippen oder auf der Fernbedienung den Verbindungsstatus antippen.
3. **Fernseher suchen** antippen und den gefundenen TV auswählen. Alternativ die lokale IP-Adresse manuell eingeben.
4. **Koppeln** wählen und den am TV angezeigten sechsstelligen Hex-Code eingeben (0–9, A–F). Anschließend den Code bestätigen.
5. Erst nach **Verbunden** und gemeldeter Tastenunterstützung sind die sieben Navigationstasten aktiv.
6. Später im selben Dialog **Verbinden** wählen. Bei einer geänderten IP kann die Adresse aktualisiert werden. Bei einem anderen TV oder geändertem Zertifikat bewusst **Neu koppeln** wählen.

Standardmäßig keine Suche oder Verbindung beim App-Start; seit v0.5.0 lässt sich die normale Verbindung zu bereits eingerichteten Geräten optional beim App-Start herstellen. Keine ADB-Verbindung, TV-Einstellungen oder zusätzlichen Apps nötig. Fehler bei Erreichbarkeit, Kopplung und Verbindungsabbruch werden angezeigt. Keine automatische TV-Neukopplung oder Wiederverbindung im Hintergrund. Seit v0.5.2 einmalige Wiederherstellung einer zuvor aktiven TV-Sitzung beim Zurückkehren in die laufende App. Das Senden einer Taste bestätigt nicht die Ausführung am Fernseher.

Technik: getrennte Java-Komponenten für UI, Netzwerksitzung und Protobuf-Wireformat. mDNS-Dienst `_androidtvremote2._tcp.`, Kopplung per TLS (6467), Steuerung per TLS (6466). Bis v0.4.1 wurde der private RSA-Schlüssel pro App-Installation nicht exportierbar im Android Keystore erzeugt. Seit v0.5.0 verwenden neue Kopplungen eine portable RSA-Identität, lokal verschlüsselt durch einen nicht exportierbaren AES-Schlüssel im Android Keystore. Nach bestätigtem Code wird unverändert der SHA-256-Fingerabdruck des TV-Zertifikats gespeichert und bei späteren Steuersitzungen geprüft. Der Code wird nicht dauerhaft gespeichert. Kein Cloud-Dienst und keine neue Laufzeitbibliothek. Nur lokale private/Link-Local-IP-Adressen werden unterstützt.

Protokollreferenzen (eigene Implementierung, keine importierten Quelltexte oder Bibliotheken): [androidtvremote2](https://github.com/tronikos/androidtvremote2), [Polo-Nachrichten](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/polo.proto), [Remote-Nachrichten](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto).

Der erste echte Test am TCL muss Gerätesuche, Code-Eingabe, Wiederverbindung, D-Pad, OK, Zurück, Home und Abbruch prüfen. TV-LAN und Handy-WLAN sollten im gleichen lokalen Netz funktionieren, sofern mDNS und TCP nicht durch Netzisolation blockiert werden.

### Fehlerbehebung in v0.3.1

Beim ersten TCL-Test funktionierte die mDNS-Suche, die TLS-Kopplung scheiterte jedoch vor erfolgreicher Code-Bestätigung. Die Client-Schlüsselerzeugung erlaubt jetzt zusätzlich DIGEST_NONE für den vom TLS-Stack bereits berechneten Digest (siehe [Android KeyGenParameterSpec.Builder.setDigests](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setDigests(java.lang.String...))). Neue Kopplungen verwenden einen neuen Keystore-Alias; frühere bestätigte Kopplungen behalten ihre Identität. TLS 1.2 und 1.3 sind zugelassen; Zertifikats-Pinning bleibt unverändert. Im Fehlerfall werden nun die Verbindungsphase und die konkrete Exception angezeigt. Die Korrektur muss noch am TV bestätigt werden.

### Fehlerbehebung in v0.3.2

Beim zweiten Gerätetest funktionierte nach Freigabe des lokalen Netzwerks die Gerätesuche wieder. Der TLS-Handshake am Kopplungsport scheiterte mit Conscrypt-Fehler 04000044 (RSA internal error). Derselbe Fehler ist in [Conscrypt #718](https://github.com/google/conscrypt/issues/718) dokumentiert; [#1201](https://github.com/google/conscrypt/issues/1201) beschreibt die benötigte Keystore-Autorisierung für den internen RSA-PSS-Signaturschritt. Neue Kopplungen verwenden deshalb v3 mit DIGEST_NONE, SIGN/VERIFY und ENCRYPTION_PADDING_NONE, ohne ENCRYPT- oder DECRYPT-Purpose. Der private Schlüssel bleibt nicht exportierbar. Ein lokaler Selbsttest prüft zusätzlich die rohe RSA-Private-Key-Operation, mit der Conscrypt die zuvor erzeugte PSS-Padding-Struktur signiert. Dies ist keine unverschlüsselte Datenübertragung; TLS 1.2/1.3, Kopplungscode und Zertifikats-Pinning bleiben bestehen. Ältere erfolgreiche Kopplungen behalten ihren Schlüssel. Bei NSD-Fehler 7 verweist der Dialog direkt auf die lokale Netzwerkberechtigung. Die Wirkung der Korrektur muss auf dem echten Gerät bestätigt werden.

### Fehlerbehebung in v0.3.3

Der TCL akzeptiert seit v0.3.2 den Kopplungscode; die gespeicherte Kopplung und der anschließend erreichte Protokoll-Timeout belegen, dass der TLS-Verbindungsaufbau zur Steuerung gelang. Die alte Steuersitzung forderte nur PING und KEY an, wartete aber zwingend auf RemoteStart. Nach Abgleich mit [androidtvremote2/remote.py](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py) wird jetzt auch POWER (Bit 32) als Statusfunktion angefordert; RemoteStart liefert zugleich den Einschaltzustand und die Startbestätigung. Es werden keine Power-Befehle automatisch gesendet. Navigation wird weiterhin erst nach bestätigtem RemoteStart und KEY-Freigabe aktiviert. Bei fehlender Startbestätigung enthält der Fehler die angebotene und ausgehandelte Bitmaske, Nachrichtenanzahlen und empfangene Feldnummern, jedoch keine Nutzdaten, PINs oder Schlüssel. Regressionstests prüfen die POWER-Aushandlung, Start im Standby und dass Pings allein keine Verbindung bestätigen. Die Änderung muss am TCL verifiziert werden.

### TV-Steuerung und Streaming (ab v0.4.0)

- Der Nutzer hat die Steuerverbindung und das Steuerkreuz in v0.3.3 am TCL bestätigt. Die sichere Kopplung und Tonmaschine bleiben unverändert.
- Remote v2 handelt zusätzlich VOLUME (64) und APP_LINK (512) aus. Power, Volume Up/Down und Volume Mute sind einzelne Android-Tastenbefehle. Power/Standby, Pegel und Stummschaltung werden nur anhand von TV-Meldungen angezeigt. Der Pegel ist vorerst eine reine Anzeige: keine erfundenen Werte und keine unbestätigte absolute Lautstärkeregelung. Nach tiefem Standby kann der TV unerreichbar werden.
- Die fünf Standarddienste sind Netflix, Prime Video, Apple TV+, Disney+ und Dyn. Die Leiste scrollt horizontal. Ein kurzes Tippen sendet den App-Link, langes Drücken öffnet Bearbeiten, Verschieben oder Entfernen; die Plus-Taste öffnet den Katalog oder eine eigene App (Name plus Link/Paketname). Die Reihenfolge wird lokal gespeichert. Auch eine leere Liste bleibt leer. Entfernen betrifft nur die Schnellwahltaste, nicht die Installation auf dem TV.
- Netflix, Prime Video, Apple TV und Disney+ verwenden dokumentierte Deep Links (siehe [Home Assistant Android TV Remote](https://www.home-assistant.io/integrations/androidtv_remote/) und [Deep-Link-Sammlung](https://community.home-assistant.io/t/android-tv-remote-app-links-deep-linking-guide/567921/)). Dyn nutzt versuchsweise den Paketnamen aus der [Play-Store-App](https://play.google.com/store/apps/details?id=sport.dyn.app.android.prod), umgewandelt in market://launch?id=… . Ob die TV-App dadurch direkt geöffnet wird, ist nicht bestätigt; möglicherweise öffnet sich lediglich der Store. Der Link ist bearbeitbar.
- Remote v2 bietet keine bekannte Abfrage aller installierten TV-Apps. Der Katalog ist deshalb keine automatische Geräteliste. Eigene Apps lassen sich manuell über HTTPS, unterstützte App-URI-Schemata oder Paketnamen ergänzen. App-Links werden ausschließlich nach bewusstem Tippen gesendet. Der TV kann einen Link ignorieren oder einen Browser/Store öffnen. Der Client meldet den Versand, nicht den erfolgreichen Start; es gibt keine verlässliche Rückmeldung über nicht installierte Apps. Keine ADB-Freigabe, keine zusätzliche TV-App und keine neuen Bibliotheken.
- Noch am Gerät zu testen: Power (auch Standby), Lautstärke +/− und Stumm samt Status, Start der fünf Dienste, Verwaltung der Schnellwahlliste. Keine dieser neuen Funktionen ist durch einen echten TV-Test bestätigt.

### Stabilität der TV-Steuersitzung (v0.4.1)

Der Nutzer meldete bei v0.4.0 einen Abbruch nach 10–20 s Benutzung. Die Diagnose (Configure=1, Active=1, Start=1, Ping=15, Felder 1/2/8/40/50) belegt eine vorher bestätigte Sitzung. Der bisherige TCP/TLS-Lese-Timeout von 16 s berücksichtigte ausgehende Steuerbefehle nicht. Laut [Protokollreferenz](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py) sendet der TV im Leerlauf etwa alle fünf Sekunden Pings und der Referenzclient berücksichtigt beide Verkehrsrichtungen.

Die App verwendet jetzt eine unabhängige Überwachung mit 20 s fester Aushandlungsfrist und 25 s Inaktivitätsfrist nach bestätigter Sitzung. Vollständig verarbeitete eingehende Frames und erfolgreich geschriebene ausgehende Frames gelten als Aktivität; die Frist für die Aushandlung wird durch Pings nicht verlängert. Der Watchdog schließt bei Ablauf den Socket vollständig, damit keine teilweise gelesenen Frames verloren gehen. TCP-Keepalive ist ergänzend aktiviert. Verbindungsabbruch und fehlende Bestätigung werden weiterhin angezeigt, es gibt keine automatische Wiederverbindung oder erneute Kopplung. Fünf zusätzliche JVM-Tests prüfen die Fristen. Die tatsächliche Wirkung am TCL ist noch zu prüfen; der genaue Auslöser des ausbleibenden Empfangs ist nicht zweifelsfrei belegt.

Nutzertests zur Energieeinstellung: In „Optimiert“ ließ sich der TCL über WLAN aus Sofacockpit einschalten, über LAN dagegen nicht; in „Erhöht“ funktionierte das Einschalten auch über LAN. Langzeit-Standby und ein separater LAN-Aufweckmechanismus sind noch nicht verifiziert. Für die Fehlerkorrektur werden weder Energiemodus noch Aufweckmechanismus verändert.

### Zentrale Verbindungen und Sicherung (ab v0.5.0)

Oben auf dem Startbildschirm steht **Verbindung** mit den jederzeit anklickbaren Tasten **TV** und **Sound**. Grün bedeutet bestätigte Gerätesitzung beziehungsweise gültige Soundbar-Protokollantwort, rot getrennt/Fehler, gelb Verbindungsaufbau. Beide Tasten öffnen dieselbe bildschirmfüllende Verbindungsseite. TV-Suche, IP-Eingabe, Kopplung, Trennen sowie die separat speicherbare Soundbar-IP sind dort erreichbar. „Beide Geräte verbinden“ startet die jeweiligen normalen Verbindungen. Die Tonmaschine-Einstellungen verweisen für die Verbindung auf dieselbe Seite.

„Beim App-Start automatisch verbinden“ ist standardmäßig aus. Bei Aktivierung werden nur bereits eingerichtete Geräte adressiert, keine Power-Befehle, Magic Packets oder automatische Kopplung ausgelöst. Einzelne Geräte können bereits durch einen normalen Netzwerkzugriff aufwachen; eine Garantie für unterbrechungsfreien Standby gibt es deshalb nicht. Falls der Fernseher dadurch unerwünscht aufwacht, die Option deaktivieren. Das Speichern einer Adresse löst keine Verbindung aus. Beim Import entscheidet die separate Bestätigungsoption über einen einmaligen Verbindungsaufbau.

Die verschlüsselte Datei **Sofacockpit.sofacfg** enthält TV-Adresse, Soundbar-Adresse, Streaming-Liste samt Reihenfolge und Links sowie die Auto-Verbindungsoption. Bei einer neuen/portablen TV-Kopplung enthält sie auch den privaten RSA-Client-Schlüssel, sein Zertifikat und den TV-Zertifikatspin. Alte, nicht exportierbare TV-Schlüssel können nicht ausgelesen werden; einmalige Neukopplung erforderlich. Ohne portable Identität ist nur ein Einstellungsbackup möglich. Bei der Wiederherstellung einer portablen Identität bleibt die TV-Kopplung erhalten, sofern der TV die Identität weiterhin akzeptiert und die Adresse stimmt. Import validiert Daten und Schlüssel vor der Übernahme, fordert Bestätigung an und trennt beide Geräte; er sendet keine TV-Steuerbefehle. Ein Einstellungsbackup ohne Identität erhält eine alte Kopplung nur bei identischer Adresse. NAS-Zugangsdaten und Klangprofile werden durch diese Sicherung nicht ersetzt.

Die Datei ist ausschließlich passwortverschlüsselt (AES-256-GCM, zufälliges Salt und IV, PBKDF2-HMAC-SHA256 mit 600.000 Iterationen, mindestens 12 Passwortzeichen). Die lokale portable RSA-Identität ist mit einem separaten nicht exportierbaren AES-GCM-Schlüssel im Android Keystore geschützt. Keine Klartextdatei oder private Zugangsdaten im Repository. Der TV-Schlüssel ist anders als der frühere hardwaregeschützte RSA-Schlüssel nach Entschlüsselung im App-Prozess verfügbar, weil die Portabilität sonst nicht möglich ist. Die Sicherung enthält eine nutzbare Kopplungsidentität; Datei und Passwort sicher getrennt aufbewahren und nicht weitergeben. Bei Verlust des Passworts muss neu gekoppelt werden. JVM-Tests prüfen Verschlüsselung, Manipulation/falsches Passwort, Größenlimits sowie die RSA-Zertifikatswiederherstellung. Die neue Kopplung und Wiederherstellung nach Deinstallation müssen am TCL noch bestätigt werden.

### Systemleisten (v0.5.1)

Unter Android 15+ erzwingt das Ziel-SDK 36 ein Edge-to-Edge-Layout. In v0.5.0 konnten die Verbindungstasten auf dem Startbildschirm dadurch in der Statusleiste liegen. Das Hauptfenster und die bildschirmfüllende Verbindungsseite berücksichtigen jetzt die tatsächlichen WindowInsets für Status-/Navigationsleisten, Displayausschnitte und Bildschirmtastatur einmalig am jeweiligen Wurzel-View. Die eingebettete Compose-Seite liegt ebenfalls innerhalb des sicheren Bereichs. Die bestehenden Klick-Handler und Netzwerkfunktionen bleiben unverändert; Bedienbarkeit auf dem Nutzergerät noch zu bestätigen.

### TV-Verbindung bei Rückkehr zur App (v0.5.2)

Beim Wechsel in den Android-Hintergrund wurde ein Verbindungsabbruch („Software caused connection abort“) beobachtet, während nach vollständigem App-Neustart die gespeicherte Kopplung wieder funktionierte. Der genaue Auslöser des Socketabbruchs ist nicht nachgewiesen. Der bisherige TV-Client schloss die Sitzung nicht bei onPause/onStop, bot aber keine Wiederverbindung bei onResume. Eine zuvor bestätigte TV-Sitzung (nicht zuletzt als Standby gemeldet) bleibt beim Pausieren zunächst unverändert bestehen. Wenn sie im Hintergrund oder innerhalb von 30 Sekunden nach Rückkehr durch einen Fehler beendet wird, versucht der Client einmalig, die vorhandene gepinnte TV-Identität am selben gespeicherten Host wiederzuverwenden. Eine gesunde Sitzung wird nicht ersetzt. Kein Wiederverbindungsversuch im Hintergrund, kein automatisches Koppeln, keine Power-Kommandos oder Wiederholungsschleifen. Manuelles Trennen, explizite Verbindungsversuche, Adressänderung und Import löschen die Wiederverbindungsabsicht. Die Auto-Verbindungsoption für einen neuen App-Start bleibt unverändert. Bei bestätigtem Standby kein solcher Wiederverbindungsversuch. JVM-Tests decken die Wiederverbindungsentscheidung ab; der konkrete Hintergrundwechsel muss auf dem Nutzergerät getestet werden. Auf dem Startbildschirm entfällt der Text „Ein/Standby“ neben den TV-Steuerungssymbolen.

### Automatisch verbinden nach Konfigurationsimport (v0.6.0)

Der Bestätigungsdialog für einen vollständig validierten Import enthält die standardmäßig aktivierte, abwählbare Option „TV und Soundbar nach dem Import automatisch verbinden“. Diese ist unabhängig von der persistenten Einstellung „Beim App-Start automatisch verbinden“. Erst nachdem sämtliche Einstellungen erfolgreich übernommen wurden, werden die vorhandene TV-Kopplung am importierten Host sowie die Soundbar-Adresse für einen einmaligen Verbindungsstart verwendet. Fehlt eine Adresse oder TV-Kopplung, wird das betreffende Gerät ausgelassen; die Soundbar kann trotzdem verbunden werden. Keine automatische Neukopplung. Fehlende lokale Netzwerkberechtigung wird gegebenenfalls angefragt, nach Freigabe werden die gespeicherten Adressen nochmals geprüft. Die normalen Verbindungsstatus zeigen bestätigte Verbindungen an. Keine Power- oder anderen Steuerbefehle; ein gewöhnlicher Netzwerkzugriff kann geräteabhängig aufwecken. Bei deaktivierter Option bleibt alles getrennt. Die Sicherungsdatei bleibt formatkompatibel mit v0.5.x. JVM-Tests prüfen die Verbindungsentscheidung, Touch-Navigation und Import-Wiederverbindung sind am echten Gerät zu testen.

### Dauerhafte APK-Signatur

Seit v0.5.0 veröffentlicht GitHub Actions eine Release-APK mit festem Anwendungspaket und aufsteigendem versionCode. Der private PKCS12-Schlüssel (Alias sofacockpit) und das Passwort liegen ausschließlich in den Repository Actions Secrets SOFACOCKPIT_KEYSTORE_BASE64 und SOFACOCKPIT_KEYSTORE_PASSWORD. Der Workflow dekodiert die Base64-Datei nur im temporären Runner-Verzeichnis, signiert die Release-APK und prüft vor der Veröffentlichung den fest hinterlegten öffentlichen SHA-256-Zertifikatsfingerabdruck 6bc8b1ff2f2f2bc6db4c56d4ffa53c080c05f48089dab7b66ccb7d74fa54ac83. Er bricht bei fehlenden Secrets oder einem abweichenden Zertifikat ab. Niemals den Schlüssel oder das Passwort ins Repository einchecken. Alte Debug-APKs mit anderer Signatur müssen einmalig ersetzt werden; danach kann jede höher versionierte APK mit derselben Signatur als Update installiert werden.

### Bauen

Das Android-Projekt verwendet Gradle, Kotlin und Jetpack Compose für den eingebetteten Tonmaschine-Screen. Mit Android SDK 36 und Gradle 9.6 lässt sich die Debug-APK mit `./gradlew :app:assembleDebug` bauen. Sie liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Der Debug-Build ist debug-signiert und nicht für eine Store-Veröffentlichung gedacht. Protokoll-Unit-Tests mit `./gradlew :app:testDebugUnitTest`. Der GitHub-Actions-Workflow führt diese Tests, den Release-APK-Build und die feste Signaturprüfung aus; die veröffentlichte APK heißt Sofacockpit.apk. Lokale Debug-Builds können abweichend signiert sein. Eine Deinstallation löscht lokale App-Daten und den lokalen Keystore; die verschlüsselte Sicherung ermöglicht die Wiederherstellung portabler TV-Identitäten.
