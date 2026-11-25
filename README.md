# Sofacockpit

Sofacockpit ist eine geplante Android-App, die einen **TCL-Fernseher mit Google TV** und die **LG-Soundbar S95QR** in einer Fernbedienung zusammenführt. Die App richtet sich zunächst an den TCL 85C89L, der im Gerätemenü als 85C8L angezeigt wird.

## Bedienkonzept

Die App öffnet den **Startbildschirm** in der Mitte. Von dort sind die beiden Detailseiten mit einem Wisch erreichbar.

| Geste vom Startbildschirm | Seite |
| --- | --- |
| Nach rechts wischen | **Tonmaschine** für die erweiterten Soundbar-Einstellungen |
| Nach links wischen | **TV-Steuerkreuz** mit vier Richtungen, OK, Zurück und Home |

### Startbildschirm

- **TV** mit Ein/Aus und Stumm.
- **Streaming-Leiste** mit vier direkt sichtbaren Schnellwahltasten und weiteren Diensten durch horizontales Scrollen. Kurzes Tippen startet den Dienst auf dem TV. Langes Drücken ist für spätere Optionen pro Dienst vorgesehen.
- **Lautstärke** mit Schieberegler und Tasten für einzelne Schritte mit − und +.
- **Soundbar** mit eigener Ein/Aus-Taste, sechs Klangprofil-Tasten in zwei Reihen, einer Anzeige des aktuellen Audioformats und Schaltern für Nachtmodus, DRC (Dynamikkompression) und Surround.

Wischen auf der Streaming-Leiste scrollt die Dienste. Ziehen am Lautstärkeregler ändert die Lautstärke. Ein horizontaler Wisch auf der übrigen Fläche wechselt die Seite.

### Tonmaschine

Die Soundbar-Seite integriert die erweiterten Funktionen der bestehenden Tonmaschine, darunter Klangmodi und Pegel für Subwoofer, Center und rückwärtige Lautsprecher.

## Verbindung und Projektstand

Die Steuerung ist für das lokale Heimnetz geplant. Für den Fernseher ist der **Android TV Remote Service** mit Kopplungscode vorgesehen. Für die Soundbar soll Sofacockpit auf der bereits erprobten lokalen Steuerung der Tonmaschine aufbauen.

Das Projekt befindet sich in der Konzeptphase. Mit der offiziellen Google-TV-App wurden am TCL bereits die Navigation und die Weitergabe von Lautstärkebefehlen an die LG-Soundbar getestet. Dabei war das Handy per WLAN und der Fernseher per LAN im selben Heimnetz verbunden.

Für Sofacockpit stehen die eigene Kopplung und der Gerätetest noch aus. Zu prüfen sind insbesondere das Aufwecken des TV nach längerem Standby, das direkte Starten der Streaming-Apps, das Auslesen des Audioformats und das Auslesen und Setzen eines absoluten Lautstärkewerts für den Schieberegler.
