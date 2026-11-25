# Megaprompt für das Sofacockpit-Projekt

Du arbeitest am öffentlichen GitHub-Repository `sschermer/Sofacockpit`. Entwickle gemeinsam mit Steffen eine minimalistische Android-App namens **Sofacockpit**, die den TCL-Fernseher mit Google TV und die LG-Soundbar S95QR über das lokale Heimnetz bedient.

Deine Aufgabe ist, das Projekt schrittweise bis zu einer alltagstauglichen, installierbaren Android-App umzusetzen. Lies zuerst den Repository-Inhalt und die vorhandene Projektstruktur. Halte dich an etablierte Muster im Projekt und vermeide Änderungen, die nicht zum Auftrag gehören. Stelle pro Iteration eine installierbare APK bereit und fasse verständlich zusammen, was sich geändert hat, was funktioniert und was am echten Gerät noch geprüft werden muss.

## Gerät und bestätigter Ausgangspunkt

- Fernseher: **TCL 85C89L**, im Gerätemenü auch als **85C8L** angezeigt.
- TV-Plattform: **Google TV / Android TV 14**.
- Soundbar: **LG S95QR**. Die Bezeichnung DS95QR wurde ebenfalls erwähnt, Modellaufschrift noch nicht abschließend verifiziert.
- Der Fernseher ist üblicherweise per LAN verbunden, das Android-Handy per WLAN im selben Heimnetz.
- Mit der offiziellen Google-TV-App wurden Navigation und Lautstärkebefehle bereits getestet. Die Lautstärkebefehle über den TV steuern die Soundbar.
- Die vorhandene App **Tonmaschine** kann die LG-Soundbar lokal steuern. Im Projekt sollen vorhandene Implementierungen und das zugehörige Repository gesucht und geprüft werden, bevor Steuerungsprotokolle oder Funktionen neu entwickelt werden.
- TV-Kopplung und -Bedienung aus Sofacockpit selbst sind noch nicht implementiert oder am Gerät verifiziert.

Behandle obige Punkte als Nutzungsangaben. Behaupte eine Funktion erst als unterstützt, wenn sie im Code überprüft oder am vorgesehenen Gerät getestet wurde. Kennzeichne sauber, was der Nutzer bestätigt hat, was aus vorhandener Implementierung folgt und was noch offen ist.

## Bedienkonzept

Die App hat drei horizontal benachbarte Seiten. Beim Öffnen ist immer der **Startbildschirm in der Mitte** sichtbar.

- Vom Startbildschirm **nach links wischen** zeigt das **TV-Steuerkreuz** von rechts.
- Vom Startbildschirm **nach rechts wischen** zeigt die **Tonmaschine** von links.
- Rückwärtswischen bringt jeweils zum Startbildschirm zurück.
- Seitenwechsel erfolgen durch horizontales Wischen auf der freien Fläche.
- Bedienelemente mit eigenen horizontalen Gesten behalten diese Gesten. Zum Beispiel scrollt die Streaming-Leiste, ohne versehentlich die Seite zu wechseln.
- Verhindere, dass Wischen auf dem Lautstärkeregler unabsichtlich die Seite wechselt.
- Zeige einen dezenten Seitenindikator für die drei Seiten.
- Nutze eine einfache, dunkle, gut lesbare und minimalistische Oberfläche. Touchziele sollen auf einem Handy großzügig und gut erreichbar sein. Die App soll sich wie eine praktische Fernbedienung anfühlen, nicht wie ein umfangreiches Dashboard.

### 1. Startbildschirm

#### Fernseher

- Schaltflächen **Stumm** und **Ein/Aus**.
- Der Power-Status soll erkennbar sein, sofern er zuverlässig verfügbar ist.
- Biete TV-Bedienung per Android TV Remote Service mit sicherer Kopplung über den vom Fernseher angezeigten Code an. Bevorzuge eine unterstützte Android-TV-Schnittstelle gegenüber undokumentierten Hacks.
- Die Verbindung soll Handy-WLAN zu TV-LAN im selben lokalen Netz unterstützen.

#### Streaming-Dienste

- Eine horizontal scrollbare Leiste zeigt zunächst **vier Dienst-Tasten**.
- Weitere Dienste lassen sich durch Scrollen nach links und rechts erreichen.
- Kurzes Tippen startet den jeweiligen Dienst auf dem Fernseher.
- Langes Drücken ist für später geplante erweiterte Optionen je Dienst reserviert. In der ersten nutzbaren Version noch keine unbelegten Aktionen erfinden. Wenn noch keine Optionen existieren, kann langes Drücken eine knappe Rückmeldung anzeigen.
- Die Konfiguration der Dienste soll später erweiterbar sein. Nutzer können Dienste und deren Android-TV-App-IDs hinzufügen, entfernen und sortieren. Entwickle diese Konfiguration erst, wenn die Grundbedienung stabil ist.
- Eine nicht installierte oder nicht startbare TV-App soll eine verständliche Fehlermeldung auslösen.

#### Lautstärke

- Eine gemeinsame Zeile mit **Minus**, **Schieberegler** und **Plus**.
- Minus und Plus ändern die Lautstärke schrittweise.
- Der Regler soll die Lautstärke direkt setzen, falls die Schnittstelle den absoluten Wert zuverlässig unterstützt.
- Zeige den tatsächlichen Lautstärkewert nur, wenn er verlässlich ausgelesen wird. Täusche keinen synchronisierten Regler vor, wenn nur relative Lauter-/Leiser-Befehle möglich sind.
- Die Lautstärkesteuerung über den TV hat zunächst Vorrang, da die Weitergabe an die Soundbar im Nutzeraufbau bereits funktioniert. Direkte Soundbar-Lautstärkesteuerung darf zusätzlich eingebaut werden, wenn die vorhandene Tonmaschine eine belastbare Steuerung und Rückmeldung bietet.

#### Soundbar

- Eigene **Ein/Aus**-Taste.
- **Sechs Klangprofile** als zwei Reihen mit je drei Tasten. Bereits bekannte Modus-IDs aus der bestehenden Tonmaschine sind zu übernehmen, sofern deren Implementierung dies bestätigt: AI Sound Pro 19, Standard 0, Cinema 7, Clear Voice Pro 26, Sports 21, Music 6, Game 22 und Bass Blast 14. Diese acht bekannten Modi sind Kandidaten, aus denen die sechs sichtbaren Profile ausgewählt beziehungsweise konfiguriert werden. Die endgültigen sechs Profile bestimmt der Nutzer.
- Zeige das **aktuelle Audioformat** nur dann an, wenn die Soundbar oder vorhandene Steuerungslogik es zuverlässig melden kann. Keine Werte schätzen.
- Schalter für **Nachtmodus**, **DRC** und **Surround**. DRC steht für Dynamikkompression. Übernimm IDs und Werte aus der vorhandenen Tonmaschine, sofern vorhanden.
- Der Status dieser Schalter soll mit dem tatsächlichen Soundbar-Status synchronisiert werden, wenn die Schnittstelle ihn auslesen kann. Bei fehlender Rückmeldung darf die App keine erfolgreiche Statusänderung vortäuschen. Zeige ausstehende, erfolgreiche und fehlgeschlagene Befehle erkennbar an.

### 2. TV-Steuerkreuz

- Vier Richtungstasten mit **OK** in der Mitte.
- **Zurück** und **Home**.
- Bediengefühl und Anordnung sollen der virtuellen Fernbedienung in der Google-TV-App ähneln.
- Die Grundbedienung muss einhändig möglich sein.
- Ein Mauszeiger wie bei einer LG Magic Remote ist keine Voraussetzung der ersten Version. Zunächst das Steuerkreuz zuverlässig fertigstellen.
- Wiederholtes Halten einer Richtungstaste darf nur umgesetzt werden, wenn die gewählte TV-Schnittstelle dies sauber unterstützt.

### 3. Tonmaschine

Diese Seite soll die bereits vorhandene Tonmaschine für die LG-Soundbar integrieren. Finde und prüfe die vorhandene Android-Implementierung und gegebenenfalls das zugehörige öffentliche Quell-Repository. Übernimm bestehende, funktionierende Logik, Wertebereiche und Modus-IDs, statt eine zweite, abweichende Soundbar-Steuerung zu erfinden.

Die Seite soll mindestens die bereits bestätigten Funktionen der Tonmaschine verfügbar machen, soweit sie in einer überprüften Android-Implementierung tatsächlich existieren:

- Klangmodus auswählen
- Pegel für Subwoofer, Center und hintere beziehungsweise obere Lautsprecher einstellen
- Nachtmodus, DRC und Surround beziehungsweise Rear-Steuerung
- Verbindungs- und Befehlsstatus sichtbar machen
- Navigation zurück zum mittleren Startbildschirm

Die Plattformunterstützung und verfügbare Geräteeigenschaften sind am echten S95QR zu prüfen. Die Funktionalität der Windows-App belegt für sich allein nicht, dass dieselbe Steuerung auf Android portierbar ist.

## Technische Arbeitsweise

1. Prüfe zuerst Dateien, Git-Verlauf, Build-System und bestehende Android- oder Soundbar-Projekte. Suche nach README, Lizenzdateien und Hinweisen zu den Tonmaschine-Implementierungen.
2. Verwende die bereits etablierte Technologie im Repository. Falls noch kein Android-Projekt besteht, wähle eine wartbare, native Android-Lösung und dokumentiere die Wahl kurz.
3. Teile TV- und Soundbar-Verbindung in klar abgegrenzte Komponenten. Die Oberfläche soll nicht direkt Netzwerkprotokolle ausführen.
4. Verwalte Verbindungszustand, Kopplung, Zeitüberschreitungen, Wiederholversuche und App-Wechsel nachvollziehbar. Netzwerkoperationen dürfen die Oberfläche nicht blockieren.
5. Speichere nötige Kopplungsdaten sicher mit den Android-üblichen Mechanismen. Frage Berechtigungen nur an, wenn sie für die gewählte Funktion benötigt werden, und erkläre sie in einfacher Sprache.
6. Vermeide unnötige Internetverbindungen. Grundlegende Fernbedienungsfunktionen laufen im lokalen Netz. Keine Telemetrie, Werbung oder Analyse-SDKs hinzufügen.
7. Halte den Code verständlich, wartbar und sparsam. Keine unnötigen Abstraktionen oder nicht verwendeten Bibliotheken.
8. Verändere keine TV-Einstellungen und führe keine Befehle gegen Geräte aus, ohne dass Steffen die Kopplung beziehungsweise den jeweiligen Test ausdrücklich vornimmt.
9. Führe nicht ungefragt destruktive Aktionen aus. Keine Paketdeinstallation, Zurücksetzung oder Änderung an System-Apps des Fernsehers.
10. Behaupte keinen Gerätetest, APK-Build oder Erfolg, der nicht tatsächlich erfolgt ist.

## Öffentliche Repository- und Lizenzregeln

Dieses Repository ist öffentlich.

- Verwende nur eigenen Code, rechtmäßig nutzbare Abhängigkeiten und vom Nutzer bereitgestellte oder ausdrücklich freigegebene Ressourcen.
- Prüfe die Lizenz jeder neu hinzugefügten Abhängigkeit und halte erforderliche Hinweise und Lizenzdateien ein.
- Kopiere keine fremden App-Oberflächen, proprietären Assets, Icons oder Quelltexte ohne ausdrückliche Erlaubnis. Eine funktional vertraute Fernbedienung ist möglich, das visuelle Design soll eigenständig bleiben.
- Füge eine passende Lizenz für das Sofacockpit-Projekt erst hinzu, wenn der Nutzer eine Lizenz festgelegt oder die Veröffentlichung unter einer konkreten Lizenz bestätigt hat. Erfinde keine Lizenzentscheidung.
- Hinterlege keine privaten IP-Adressen, Passwörter, Kopplungscodes, Tokens oder sonstigen persönlichen Zugangsdaten im Repository.

## Entwicklungsablauf pro Iteration

Arbeite in kleinen, überprüfbaren Schritten. Pro Iteration:

1. Fasse das konkrete Ziel knapp zusammen.
2. Prüfe den relevanten bestehenden Code und die Auswirkungen auf vorhandene Funktionen.
3. Implementiere den vereinbarten Schritt.
4. Baue die Debug- oder Release-APK, soweit die verfügbare Umgebung dies zulässt.
5. Führe die vorhandenen passenden Prüfungen aus. Erfinde keine Testergebnisse. Wenn ein echter TV- oder Soundbar-Test nötig ist, gib Steffen kurze, konkrete Anweisungen und warte auf seine Rückmeldung, bevor du den Test als bestanden meldest.
6. Stelle die APK als Datei zum Herunterladen bereit. Nenne Build-Variante und ob sie signiert ist. Verwende keinen privaten Signaturschlüssel des Nutzers und veröffentliche keine Schlüssel.
7. Aktualisiere README oder Projektdokumentation, wenn Bedienung, Einrichtung oder bestätigter Projektstand sich verändert hat.
8. Berichte kurz: Änderungen, Build- und Testergebnis, Gerätestatus und verbleibende offene Punkte.

Gehe bei unklaren Details, die den nächsten Schritt wesentlich beeinflussen, mit einer konkreten Frage an Steffen heran. Triff reversible UI- und Implementierungsentscheidungen selbst und dokumentiere sie. Die sechs Standardprofile und die vier zunächst sichtbaren Streaming-Tasten können zunächst als Platzhalter beziehungsweise Beispielzustand gebaut werden, bis der Nutzer seine Auswahl nennt. Kennzeichne solche Werte klar als konfigurierbar.

## Erstes Entwicklungsziel

Beginne mit einer Bestandsaufnahme des Repositories und den konkreten nächsten Schritten für eine minimalistische erste Version. Implementiere danach in kleinen Iterationen:

1. Grundgerüst der drei horizontal wechselbaren Seiten mit Startbildschirm in der Mitte.
2. Startbildschirm mit Layout für TV, Streaming-Leiste, kombinierte Lautstärkesteuerung und Soundbar-Schalter.
3. TV-Kopplung und Steuerkreuz.
4. TV-App-Start und konfigurierbare Streaming-Dienste.
5. Soundbar-Steuerung durch geprüfte Wiederverwendung der Tonmaschine.
6. Integration, Fehlerbehandlung, Geräteeinrichtung und APK-Ausgabe.

Reihenfolge kann angepasst werden, wenn die Repository-Bestandsaufnahme technische Abhängigkeiten zeigt. Jede Iteration soll eine installierbare und vorführbare Verbesserung liefern, sobald die Build-Umgebung dies ermöglicht.
