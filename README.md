# Sofacockpit

Sofacockpit ist eine Android-App, die einen **TCL-Fernseher mit Google TV** und die **LG-Soundbar S95QR** in einer Fernbedienung zusammenführt. Die App richtet sich zunächst an den TCL 85C89L, der im Gerätemenü als 85C8L angezeigt wird.

## Bedienkonzept

Die App öffnet den **Startbildschirm** in der Mitte. Von dort sind die beiden Detailseiten mit einem Wisch erreichbar.

| Geste vom Startbildschirm | Seite |
| --- | --- |
| Nach rechts wischen | **Tonmaschine** für die erweiterten Soundbar-Einstellungen |
| Nach links wischen | **TV-Steuerkreuz** mit vier Richtungen, OK, Zurück und Home |

### Startbildschirm

- **TV** mit Verbindungsstatus. Den Status antippen, um den Verbindungsdialog zu öffnen. Bei bestätigter Verbindung erscheinen Stumm- und Power-Symbole; Farbe basiert auf tatsächlich gemeldetem Status.
- **Streaming-Leiste** mit Netflix, Prime Video, Apple TV+, Disney+ und Dyn als Vorauswahl, vier sichtbaren Schnellwahltasten und horizontalem Scrollen. Plus öffnet den Dienstekatalog; langes Drücken bearbeitet, verschiebt oder entfernt einen Eintrag.
- **Lautstärke** mit − und + für einzelne TV-Tastenschritte sowie einer Anzeige des tatsächlich gemeldeten Pegels, sofern verfügbar. Direktes Setzen eines absoluten Wertes ist noch nicht unterstützt.
- **Soundbar** mit Ein/Aus, Night, DRC, Surround und Audioformat. Das Format wird aus der Soundbar-Meldung übernommen, nicht geschätzt.

Wischen auf der Streaming-Leiste scrollt die Dienste. Der Lautstärkepegel ist zunächst nur eine Anzeige, kein absoluter Regler. Ein horizontaler Wisch auf der übrigen Fläche wechselt die Seite.

### Tonmaschine

Die linke Seite enthält den Android-Screen aus der bestehenden Tonmaschine mit Verbindungseinstellungen, Status, Klangmodi, Lautsprecherpegeln, Profilen, Nachtmodus, DRC, Surround und gemeldetem Audioformat. Die Tonmaschine wird erst verbunden, wenn du in ihren Einstellungen eine IP-Adresse eingibst und **Verbinden** auswählst. Die Wiederverwendung der bestehenden Android-Implementierung ist durch den Quellcode belegt; ein Test an der LG S95QR steht noch aus.

Der rechte D-Pad-Bereich nutzt eine große, runde Navigation mit OK sowie Zurück und Home. Die D-Pad-Tasten sind erst nach einer vom TV bestätigten Steuersitzung mit gemeldeter Tastenunterstützung aktiv.

## Verbindung und Projektstand

Die Steuerung ist für das lokale Heimnetz geplant. Für den Fernseher ist der **Android TV Remote Service (Remote Protocol v2)** mit Kopplungscode implementiert. Die Schnittstelle ist nicht als offizielle Drittanbieter-API dokumentiert; Kompatibilität mit zukünftigen Service-Versionen kann nicht garantiert werden. Für die Soundbar soll Sofacockpit auf der bereits erprobten lokalen Steuerung der Tonmaschine aufbauen.

Das Android-Projekt verbindet die native Sofacockpit-Navigation mit dem Compose-Screen der Tonmaschine. Beim Öffnen ist der Startbildschirm in der Mitte sichtbar. Er zeigt auf der Tonmaschine-Seite und auf dem Startbildschirm denselben Verbindungszustand. Die Streaming-Leiste und der Lautstärkeregler behalten ihre eigenen Gesten. Der Nutzer hat die TV-Steuerverbindung und das Steuerkreuz mit v0.3.3 am TCL bestätigt. Seit v0.4.0 sind App-Link-Start, relative TV-Lautstärke, Stumm und Power implementiert; Gerätetests dieser neuen Funktionen stehen aus.

Mit der offiziellen Google-TV-App wurden am TCL bereits die Navigation und die Weitergabe von Lautstärkebefehlen an die LG-Soundbar getestet. Dabei war das Handy per WLAN und der Fernseher per LAN im selben Heimnetz verbunden. Das ist kein Test von Sofacockpit.

Die Soundbar-Seite verwendet den Android-Code aus der öffentlichen [Tonmaschine-Implementierung](https://github.com/sschermer/Soundbar-APP/tree/main/android). Die übernommenen Dateien behalten deren MIT-Lizenzhinweis in [TONMASCHINE_LICENSE.md](TONMASCHINE_LICENSE.md); Android-Abhängigkeiten und Hinweise sind in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dokumentiert. TV-Steuerkreuz am TCL bestätigt (v0.3.3); weitere TV- und Soundbar-Gerätetests stehen aus.

Zu prüfen sind insbesondere das Aufwecken des TV nach längerem Standby, das direkte Starten der Streaming-Apps, das Auslesen des Audioformats und das Auslesen und Setzen eines absoluten Lautstärkewerts für den Schieberegler.

### TV verbinden (ab v0.3.0)

1. Fernseher einschalten, Handy im gleichen Heimnetz (kein isoliertes Gast-WLAN) verbinden.
2. Auf dem Startbildschirm den TV-Status antippen oder auf der Steuerkreuz-Seite **TV-Verbindung** wählen.
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

„Beim App-Start automatisch verbinden“ ist standardmäßig aus. Bei Aktivierung werden nur bereits eingerichtete Geräte adressiert, keine Power-Befehle, Magic Packets oder automatische Kopplung ausgelöst. Einzelne Geräte können bereits durch einen normalen Netzwerkzugriff aufwachen; eine Garantie für unterbrechungsfreien Standby gibt es deshalb nicht. Falls der Fernseher dadurch unerwünscht aufwacht, die Option deaktivieren. Import und Speichern einer Adresse lösen keine Verbindung aus.

Die verschlüsselte Datei **Sofacockpit.sofacfg** enthält TV-Adresse, Soundbar-Adresse, Streaming-Liste samt Reihenfolge und Links sowie die Auto-Verbindungsoption. Bei einer neuen/portablen TV-Kopplung enthält sie auch den privaten RSA-Client-Schlüssel, sein Zertifikat und den TV-Zertifikatspin. Alte, nicht exportierbare TV-Schlüssel können nicht ausgelesen werden; einmalige Neukopplung erforderlich. Ohne portable Identität ist nur ein Einstellungsbackup möglich. Bei der Wiederherstellung einer portablen Identität bleibt die TV-Kopplung erhalten, sofern der TV die Identität weiterhin akzeptiert und die Adresse stimmt. Import validiert Daten und Schlüssel vor der Übernahme, fordert Bestätigung an und trennt beide Geräte; er sendet keine TV-Befehle. Ein Einstellungsbackup ohne Identität erhält eine alte Kopplung nur bei identischer Adresse. NAS-Zugangsdaten und Klangprofile werden durch diese Sicherung nicht ersetzt.

Die Datei ist ausschließlich passwortverschlüsselt (AES-256-GCM, zufälliges Salt und IV, PBKDF2-HMAC-SHA256 mit 600.000 Iterationen, mindestens 12 Passwortzeichen). Die lokale portable RSA-Identität ist mit einem separaten nicht exportierbaren AES-GCM-Schlüssel im Android Keystore geschützt. Keine Klartextdatei oder private Zugangsdaten im Repository. Der TV-Schlüssel ist anders als der frühere hardwaregeschützte RSA-Schlüssel nach Entschlüsselung im App-Prozess verfügbar, weil die Portabilität sonst nicht möglich ist. Die Sicherung enthält eine nutzbare Kopplungsidentität; Datei und Passwort sicher getrennt aufbewahren und nicht weitergeben. Bei Verlust des Passworts muss neu gekoppelt werden. JVM-Tests prüfen Verschlüsselung, Manipulation/falsches Passwort, Größenlimits sowie die RSA-Zertifikatswiederherstellung. Die neue Kopplung und Wiederherstellung nach Deinstallation müssen am TCL noch bestätigt werden.

### Systemleisten (v0.5.1)

Unter Android 15+ erzwingt das Ziel-SDK 36 ein Edge-to-Edge-Layout. In v0.5.0 konnten die Verbindungstasten auf dem Startbildschirm dadurch in der Statusleiste liegen. Das Hauptfenster und die bildschirmfüllende Verbindungsseite berücksichtigen jetzt die tatsächlichen WindowInsets für Status-/Navigationsleisten, Displayausschnitte und Bildschirmtastatur einmalig am jeweiligen Wurzel-View. Die eingebettete Compose-Seite liegt ebenfalls innerhalb des sicheren Bereichs. Die bestehenden Klick-Handler und Netzwerkfunktionen bleiben unverändert; Bedienbarkeit auf dem Nutzergerät noch zu bestätigen.

### TV-Verbindung bei Rückkehr zur App (v0.5.2)

Beim Wechsel in den Android-Hintergrund wurde ein Verbindungsabbruch („Software caused connection abort“) beobachtet, während nach vollständigem App-Neustart die gespeicherte Kopplung wieder funktionierte. Der genaue Auslöser des Socketabbruchs ist nicht nachgewiesen. Der bisherige TV-Client schloss die Sitzung nicht bei onPause/onStop, bot aber keine Wiederverbindung bei onResume. Eine zuvor bestätigte TV-Sitzung (nicht zuletzt als Standby gemeldet) bleibt beim Pausieren zunächst unverändert bestehen. Wenn sie im Hintergrund oder innerhalb von 30 Sekunden nach Rückkehr durch einen Fehler beendet wird, versucht der Client einmalig, die vorhandene gepinnte TV-Identität am selben gespeicherten Host wiederzuverwenden. Eine gesunde Sitzung wird nicht ersetzt. Kein Wiederverbindungsversuch im Hintergrund, kein automatisches Koppeln, keine Power-Kommandos oder Wiederholungsschleifen. Manuelles Trennen, explizite Verbindungsversuche, Adressänderung und Import löschen die Wiederverbindungsabsicht. Die Auto-Verbindungsoption für einen neuen App-Start bleibt unverändert. Bei bestätigtem Standby kein solcher Wiederverbindungsversuch. JVM-Tests decken die Wiederverbindungsentscheidung ab; der konkrete Hintergrundwechsel muss auf dem Nutzergerät getestet werden. Auf dem Startbildschirm entfällt der Text „Ein/Standby“ neben den TV-Steuerungssymbolen.

### Dauerhafte APK-Signatur

Seit v0.5.0 veröffentlicht GitHub Actions eine Release-APK mit festem Anwendungspaket und aufsteigendem versionCode. Der private PKCS12-Schlüssel (Alias sofacockpit) und das Passwort liegen ausschließlich in den Repository Actions Secrets SOFACOCKPIT_KEYSTORE_BASE64 und SOFACOCKPIT_KEYSTORE_PASSWORD. Der Workflow dekodiert die Base64-Datei nur im temporären Runner-Verzeichnis, signiert die Release-APK und prüft vor der Veröffentlichung den fest hinterlegten öffentlichen SHA-256-Zertifikatsfingerabdruck 6bc8b1ff2f2f2bc6db4c56d4ffa53c080c05f48089dab7b66ccb7d74fa54ac83. Er bricht bei fehlenden Secrets oder einem abweichenden Zertifikat ab. Niemals den Schlüssel oder das Passwort ins Repository einchecken. Alte Debug-APKs mit anderer Signatur müssen einmalig ersetzt werden; danach kann jede höher versionierte APK mit derselben Signatur als Update installiert werden.

### Bauen

Das Android-Projekt verwendet Gradle, Kotlin und Jetpack Compose für den eingebetteten Tonmaschine-Screen. Mit Android SDK 36 und Gradle 9.6 lässt sich die Debug-APK mit `./gradlew :app:assembleDebug` bauen. Sie liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Der Debug-Build ist debug-signiert und nicht für eine Store-Veröffentlichung gedacht. Protokoll-Unit-Tests mit `./gradlew :app:testDebugUnitTest`. Der GitHub-Actions-Workflow führt diese Tests, den Release-APK-Build und die feste Signaturprüfung aus; die veröffentlichte APK heißt Sofacockpit.apk. Lokale Debug-Builds können abweichend signiert sein. Eine Deinstallation löscht lokale App-Daten und den lokalen Keystore; die verschlüsselte Sicherung ermöglicht die Wiederherstellung portabler TV-Identitäten.
