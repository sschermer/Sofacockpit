# Sofacockpit

Sofacockpit ist eine geplante Android-App, die einen **TCL-Fernseher mit Google TV** und die **LG-Soundbar S95QR** in einer Fernbedienung zusammenführt. Die App richtet sich zunächst an den TCL 85C89L, der im Gerätemenü als 85C8L angezeigt wird.

## Bedienkonzept

Die App öffnet den **Startbildschirm** in der Mitte. Von dort sind die beiden Detailseiten mit einem Wisch erreichbar.

| Geste vom Startbildschirm | Seite |
| --- | --- |
| Nach rechts wischen | **Tonmaschine** für die erweiterten Soundbar-Einstellungen |
| Nach links wischen | **TV-Steuerkreuz** mit vier Richtungen, OK, Zurück und Home |

### Startbildschirm

- **TV** mit Verbindungsstatus. Den Status antippen, um den Verbindungsdialog zu öffnen. Stumm- und Power-Symbole folgen in einer späteren Version.
- **Streaming-Leiste** mit vier Beispiel-Schnellwahltasten und horizontalem Scrollen. TV-App-Start und Dienstoptionen sind noch nicht implementiert.
- **Lautstärke** mit Schieberegler und Tasten für einzelne Schritte mit − und +; die TV-Steuerung dafür ist noch nicht integriert.
- **Soundbar** mit Ein/Aus, Night, DRC, Surround und Audioformat. Das Format wird aus der Soundbar-Meldung übernommen, nicht geschätzt.

Wischen auf der Streaming-Leiste scrollt die Dienste. Ziehen am Lautstärkeregler ändert die Lautstärke. Ein horizontaler Wisch auf der übrigen Fläche wechselt die Seite.

### Tonmaschine

Die linke Seite enthält den Android-Screen aus der bestehenden Tonmaschine mit Verbindungseinstellungen, Status, Klangmodi, Lautsprecherpegeln, Profilen, Nachtmodus, DRC, Surround und gemeldetem Audioformat. Die Tonmaschine wird erst verbunden, wenn du in ihren Einstellungen eine IP-Adresse eingibst und **Verbinden** auswählst. Die Wiederverwendung der bestehenden Android-Implementierung ist durch den Quellcode belegt; ein Test an der LG S95QR steht noch aus.

Der rechte D-Pad-Bereich nutzt eine große, runde Navigation mit OK sowie Zurück und Home. Die D-Pad-Tasten sind erst nach einer vom TV bestätigten Steuersitzung mit gemeldeter Tastenunterstützung aktiv.

## Verbindung und Projektstand

Die Steuerung ist für das lokale Heimnetz geplant. Für den Fernseher ist der **Android TV Remote Service (Remote Protocol v2)** mit Kopplungscode implementiert. Die Schnittstelle ist nicht als offizielle Drittanbieter-API dokumentiert; Kompatibilität mit zukünftigen Service-Versionen kann nicht garantiert werden. Für die Soundbar soll Sofacockpit auf der bereits erprobten lokalen Steuerung der Tonmaschine aufbauen.

Das Android-Projekt verbindet die native Sofacockpit-Navigation mit dem Compose-Screen der Tonmaschine. Beim Öffnen ist der Startbildschirm in der Mitte sichtbar. Er zeigt auf der Tonmaschine-Seite und auf dem Startbildschirm denselben Verbindungszustand. Die Streaming-Leiste und der Lautstärkeregler behalten ihre eigenen Gesten. TV-Kopplung und grundlegende Navigation sind implementiert, aber am TCL noch nicht verifiziert. Streaming-App-Start, TV-Lautstärke, Stumm, Power und absolute Lautstärke folgen später.

Mit der offiziellen Google-TV-App wurden am TCL bereits die Navigation und die Weitergabe von Lautstärkebefehlen an die LG-Soundbar getestet. Dabei war das Handy per WLAN und der Fernseher per LAN im selben Heimnetz verbunden. Das ist kein Test von Sofacockpit.

Die Soundbar-Seite verwendet den Android-Code aus der öffentlichen [Tonmaschine-Implementierung](https://github.com/sschermer/Soundbar-APP/tree/main/android). Die übernommenen Dateien behalten deren MIT-Lizenzhinweis in [TONMASCHINE_LICENSE.md](TONMASCHINE_LICENSE.md); Android-Abhängigkeiten und Hinweise sind in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dokumentiert. Die Sofacockpit-Gerätetests stehen noch aus.

Zu prüfen sind insbesondere das Aufwecken des TV nach längerem Standby, das direkte Starten der Streaming-Apps, das Auslesen des Audioformats und das Auslesen und Setzen eines absoluten Lautstärkewerts für den Schieberegler.

### TV verbinden (ab v0.3.0)

1. Fernseher einschalten, Handy im gleichen Heimnetz (kein isoliertes Gast-WLAN) verbinden.
2. Auf dem Startbildschirm den TV-Status antippen oder auf der Steuerkreuz-Seite **TV-Verbindung** wählen.
3. **Fernseher suchen** antippen und den gefundenen TV auswählen. Alternativ die lokale IP-Adresse manuell eingeben.
4. **Koppeln** wählen und den am TV angezeigten sechsstelligen Hex-Code eingeben (0–9, A–F). Anschließend den Code bestätigen.
5. Erst nach **Verbunden** und gemeldeter Tastenunterstützung sind die sieben Navigationstasten aktiv.
6. Später im selben Dialog **Verbinden** wählen. Bei einer geänderten IP kann die Adresse aktualisiert werden. Bei einem anderen TV oder geändertem Zertifikat bewusst **Neu koppeln** wählen.

Keine Suche oder Verbindung beim App-Start; keine ADB-Verbindung, TV-Einstellungen oder zusätzlichen Apps nötig. Fehler bei Erreichbarkeit, Kopplung und Verbindungsabbruch werden angezeigt. Bei einem Fehler oder Neustart der App wird bewusst manuell erneut verbunden. Das Senden einer Taste bestätigt nicht die Ausführung am Fernseher.

Technik: getrennte Java-Komponenten für UI, Netzwerksitzung und Protobuf-Wireformat. mDNS-Dienst `_androidtvremote2._tcp.`, Kopplung per TLS (6467), Steuerung per TLS (6466). Der private RSA-Schlüssel wird pro App-Installation im Android Keystore erzeugt; nach bestätigtem Code wird der SHA-256-Fingerabdruck des TV-Zertifikats lokal gespeichert und bei späteren Steuersitzungen geprüft. Der Code wird nicht dauerhaft gespeichert. Kein Cloud-Dienst und keine neue Laufzeitbibliothek. Nur lokale private/Link-Local-IP-Adressen werden unterstützt.

Protokollreferenzen (eigene Implementierung, keine importierten Quelltexte oder Bibliotheken): [androidtvremote2](https://github.com/tronikos/androidtvremote2), [Polo-Nachrichten](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/polo.proto), [Remote-Nachrichten](https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remotemessage.proto).

Der erste echte Test am TCL muss Gerätesuche, Code-Eingabe, Wiederverbindung, D-Pad, OK, Zurück, Home und Abbruch prüfen. TV-LAN und Handy-WLAN sollten im gleichen lokalen Netz funktionieren, sofern mDNS und TCP nicht durch Netzisolation blockiert werden.

### Bauen

Das Android-Projekt verwendet Gradle, Kotlin und Jetpack Compose für den eingebetteten Tonmaschine-Screen. Mit Android SDK 36 und Gradle 9.6 lässt sich die Debug-APK mit `./gradlew :app:assembleDebug` bauen. Sie liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Der Debug-Build ist debug-signiert und nicht für eine Store-Veröffentlichung gedacht. Protokoll-Unit-Tests mit `./gradlew :app:testDebugUnitTest`. Der GitHub-Actions-Workflow führt diese Tests, den APK-Build und die Signaturprüfung aus. Debug-APKs verschiedener CI-Läufe können unterschiedliche Signaturschlüssel besitzen; ein direktes Update kann deshalb scheitern. Eine Deinstallation löscht lokale App-Daten.
