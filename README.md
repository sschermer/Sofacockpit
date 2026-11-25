# Sofacockpit

Sofacockpit ist eine geplante Android-App, die einen **TCL-Fernseher mit Google TV** und die **LG-Soundbar S95QR** in einer Fernbedienung zusammenführt. Die App richtet sich zunächst an den TCL 85C89L, der im Gerätemenü als 85C8L angezeigt wird.

## Bedienkonzept

Die App öffnet den **Startbildschirm** in der Mitte. Von dort sind die beiden Detailseiten mit einem Wisch erreichbar.

| Geste vom Startbildschirm | Seite |
| --- | --- |
| Nach rechts wischen | **Tonmaschine** für die erweiterten Soundbar-Einstellungen |
| Nach links wischen | **TV-Steuerkreuz** mit vier Richtungen, OK, Zurück und Home |

### Startbildschirm

- **TV** mit Verbindungsstatus. Stumm- und Power-Symbole kommen hinzu, sobald die TV-Verbindung integriert ist.
- **Streaming-Leiste** mit vier Beispiel-Schnellwahltasten und horizontalem Scrollen. TV-App-Start und Dienstoptionen sind noch nicht implementiert.
- **Lautstärke** mit Schieberegler und Tasten für einzelne Schritte mit − und +; die TV-Steuerung dafür ist noch nicht integriert.
- **Soundbar** mit Ein/Aus, Night, DRC, Surround und Audioformat. Das Format wird aus der Soundbar-Meldung übernommen, nicht geschätzt.

Wischen auf der Streaming-Leiste scrollt die Dienste. Ziehen am Lautstärkeregler ändert die Lautstärke. Ein horizontaler Wisch auf der übrigen Fläche wechselt die Seite.

### Tonmaschine

Die linke Seite enthält den Android-Screen aus der bestehenden Tonmaschine mit Verbindungseinstellungen, Status, Klangmodi, Lautsprecherpegeln, Profilen, Nachtmodus, DRC, Surround und gemeldetem Audioformat. Die Tonmaschine wird erst verbunden, wenn du in ihren Einstellungen eine IP-Adresse eingibst und **Verbinden** auswählst. Die Wiederverwendung der bestehenden Android-Implementierung ist durch den Quellcode belegt; ein Test an der LG S95QR steht noch aus.

Der rechte D-Pad-Bereich nutzt eine große, runde Navigation mit OK sowie Zurück und Home. Bis die TV-Kopplung ergänzt ist, senden die D-Pad-Tasten keine Befehle.

## Verbindung und Projektstand

Die Steuerung ist für das lokale Heimnetz geplant. Für den Fernseher ist der **Android TV Remote Service** mit Kopplungscode vorgesehen. Für die Soundbar soll Sofacockpit auf der bereits erprobten lokalen Steuerung der Tonmaschine aufbauen.

Das Android-Projekt verbindet die native Sofacockpit-Navigation mit dem Compose-Screen der Tonmaschine. Beim Öffnen ist der Startbildschirm in der Mitte sichtbar. Er zeigt auf der Tonmaschine-Seite und auf dem Startbildschirm denselben Verbindungszustand. Die Streaming-Leiste und der Lautstärkeregler behalten ihre eigenen Gesten. Die TV-Bedienung und das Starten von Streaming-Apps sind noch nicht implementiert.

Mit der offiziellen Google-TV-App wurden am TCL bereits die Navigation und die Weitergabe von Lautstärkebefehlen an die LG-Soundbar getestet. Dabei war das Handy per WLAN und der Fernseher per LAN im selben Heimnetz verbunden. Das ist kein Test von Sofacockpit.

Die Soundbar-Seite verwendet den Android-Code aus der öffentlichen [Tonmaschine-Implementierung](https://github.com/sschermer/Soundbar-APP/tree/main/android). Die übernommenen Dateien behalten deren MIT-Lizenzhinweis in [TONMASCHINE_LICENSE.md](TONMASCHINE_LICENSE.md); Android-Abhängigkeiten und Hinweise sind in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) dokumentiert. Die TV-Kopplung und Sofacockpit-Gerätetests stehen noch aus.

Zu prüfen sind insbesondere das Aufwecken des TV nach längerem Standby, das direkte Starten der Streaming-Apps, das Auslesen des Audioformats und das Auslesen und Setzen eines absoluten Lautstärkewerts für den Schieberegler.

### Bauen

Das Android-Projekt verwendet Gradle, Kotlin und Jetpack Compose für den eingebetteten Tonmaschine-Screen. Mit Android SDK 36 und Gradle 9.6 lässt sich die Debug-APK mit `./gradlew :app:assembleDebug` bauen. Sie liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`. Der Debug-Build ist debug-signiert und nicht für eine Store-Veröffentlichung gedacht.
