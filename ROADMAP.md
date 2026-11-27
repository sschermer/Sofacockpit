# Sofacockpit – Roadmap

Stand: 27. November 2026. Maßgebliche Aufgabenliste für alle Entwicklungs-Chats.

Ziel: Die vorhandenen Platzhalter und vereinbarten Funktionslücken Schritt für Schritt schließen. Sofacockpit bleibt eine schlanke, lokale Android-Fernbedienung für TCL Google TV und LG S95QR – möglichst ohne Zusatzsoftware auf dem TV oder dauerhaftes Debugging. Verbindliche Entwicklungsregeln: Rulepaper im ChatGPT-Projekt und [MEGAPROMPT.md](MEGAPROMPT.md). Der tatsächliche Entwicklungsstand ergibt sich aus Code, Build, Gerätetest und Releases.

## Status und Pflege

| Status | Nachweis |
| --- | --- |
| Geplant | Noch nicht begonnen |
| In Entwicklung | Untersuchung oder Umsetzung läuft; ggf. konkrete Blockade dokumentieren |
| Implementiert | Code vorhanden, relevante automatisierte Tests und Build erfolgreich; Gerätetest ggf. ausstehend |
| Getestet | Funktion am vorgesehenen Gerät vom Nutzer bestätigt |
| Abgeschlossen | Gerätetest bestätigt und stabile Veröffentlichung samt APK nachgewiesen |

Nur nach belegtem Fortschritt hochstufen. Ein Code-Commit ohne erfolgreichen Build bleibt in Entwicklung. Abgeschlossene Aufgaben nicht löschen; tatsächliche Version, Datum und Nachweis ergänzen. Fehlgeschlagene Tests und wieder geöffnete Aufgaben ausdrücklich vermerken. Die Zielversionen und Aufwandsangaben sind Planungswerte, keine Zusagen. Technische Abhängigkeiten oder ein neuer Nutzerauftrag können die Reihenfolge ändern; begründen und dokumentieren, nicht stillschweigend umpriorisieren.

## Phase 0 – Stabile Ausgangsversion

| ID | Aufgabe | Zielversion | Status | Nachweis / tatsächliche Version |
| --- | --- | --- | --- | --- |
| 0.1 | Nativer TCL-App-Start und Auswahl installierter Apps; stabile Freigabe | 1.1.7 | Abgeschlossen | v1.1.6: CI erfolgreich (Tests, Build, feste Signatur); Nutzer bestätigte Dyn nach Neuzuordnung, Netflix, YouTube, Abruf der App-Liste und Hinzufügen eines Favoriten. Funktionsgleiche v1.1.7: CI einschließlich Signaturprüfung erfolgreich, stabiles Release mit vollständiger APK verifiziert. Die davon unabhängige unvollständige App-Übersicht unter TV → Quellen wird in Aufgabe 0.2 korrigiert. |

[Bestandener CI-Lauf v1.1.6](https://github.com/sschermer/Sofacockpit/actions/runs/45924999804) · [Pre-Release v1.1.6](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.6) · [Bestandener CI-Lauf v1.1.7](https://github.com/sschermer/Sofacockpit/actions/runs/45927566041) · [Stabiles Release v1.1.7](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.7)

Einzelne Detailtests (System-Apps ein-/ausblenden, App-Liste manuell aktualisieren und vollständige Regression aller Bedienelemente) wurden nicht gesondert bestätigt. Das Kernziel der Phase ist am Gerät nachgewiesen.

## Vorrangige Korrektur vor Phase 1

| ID | Aufgabe | Zielversion | Priorität | Status | Nachweis / tatsächliche Version |
| --- | --- | --- | --- | --- | --- |
| 0.2 | TV → Quellen: tatsächlichen TV-App-Bestand anzeigen | 1.1.8 (Pre-Release) | Hoch – vor Tastatur | Implementiert | v1.1.8, versionCode 27: JVM-Tests, Release-Build und feste APK-Signaturprüfung erfolgreich; Pre-Release mit Sofacockpit.apk (14.744.512 Bytes) veröffentlicht. Gerätetest und stabile Freigabe ausstehend. |

**Ursache:** `MainActivity.renderSourcesApps()` verwendet gespeicherte Favoriten und den statischen 15-Dienste-Katalog aus `StreamingShortcuts.CATALOG`, nicht den per T-Cast abgerufenen App-Bestand. Der bereits funktionierende Live-Abruf (`223>>GET`) wird bisher für den Plus-/Zuordnungsdialog und die Paketprüfung beim Start eingesetzt. Der Plus-Dialog blendet vorhandene Favoriten und standardmäßig wahrscheinliche System-Apps aus.

**Ziel / Abnahmekriterien:**

- Verbindliche Datenrichtung: **TV (tatsächlich abgefragtes App-Inventar) → TV → Quellen (App-Übersicht) → Startbildschirm (aus dieser Übersicht ausgewählte Favoriten).** Die beiden Oberflächen verwenden denselben TV-bezogenen App-Bestand. Keine zweite unabhängig gepflegte App-Liste.
- Die App-Übersicht unter TV → Quellen ausschließlich aus dem tatsächlich vom verbundenen TV gelieferten Bestand (`223>>GET`) aufbauen. Kein statischer Dienstekatalog, keine Ergänzung durch gespeicherte Favoriten oder manuell eingegebene Links, auch nicht als Ersatz bei fehlgeschlagener Abfrage.
- Startfähige Apps unabhängig von der Favoritenauswahl anzeigen, anhand des Paketnamens eindeutig zuordnen und den tatsächlichen Namen verwenden. System-Apps standardmäßig ausblenden, bei Bedarf einblendbar lassen. Die Abfrage kann nur Apps anzeigen, die der TV über diese Schnittstelle meldet; nicht jede Android-Komponente ist startfähig.
- Der Startbildschirm zeigt eine konfigurierbare Teilmenge dieser TV-App-Übersicht. Favoriten über TV → Quellen auswählen bzw. über Plus dieselbe Auswahl verwenden. Favoritenmarkierung, Reihenfolge und gegebenenfalls benutzerdefinierte Bezeichnungen als lokale Metadaten erhalten, nicht als zusätzliche Quelle für die App-Übersicht. Bereits gespeicherte Zuordnungen und Deep Links kompatibel halten; nicht mehr gemeldete Apps nicht als verfügbare Favoriten darstellen oder gespeicherte Einstellungen ungefragt löschen.
- App-Bestand beim Öffnen der Quellen-Seite bzw. bei Bedarf aktualisieren, bei TV-Wechsel eindeutig trennen. Änderungen der bestätigten Liste auf beiden Oberflächen berücksichtigen. Kein Hintergrund-Polling, keine zusätzliche TV-App, kein ADB.
- Bei fehlender Verbindung, fehlender T-Cast-Unterstützung oder Abruffehler Status transparent anzeigen. Einen eventuell weiter angezeigten zuletzt bestätigten Bestand eindeutig als nicht aktuell verifiziert kennzeichnen. Kein Rückfall auf den eingebauten Dienstekatalog in Quellen oder Start-Favoritenauswahl.
- HDMI-Eingänge und deren vorhandene Steuerung sind nicht Gegenstand dieser App-Inventar-Korrektur.
- Tests: App-Übersicht mit gemeldetem TV-App-Bestand vergleichen, Installation/Deinstallation und Aktualisierung prüfen, Favoriten ausschließlich aus der Übersicht auswählen, konsistente Darstellung beider Oberflächen, App-Start und TV-Wechsel prüfen. Leere Liste, Abruffehler und nicht kompatible TVs berücksichtigen.

## Weitere gemeldete Funktionslücke vor Phase 1

| ID | Aufgabe | Zielversion | Priorität | Status | Nachweis / tatsächliche Version |
| --- | --- | --- | --- | --- | --- |
| 0.3 | HDMI-Eingänge direkt auswählen | Offen (Patch-Pre-Release vorgeschlagen) | Hoch – Vorschlag vor Phase 1 | In Entwicklung | Nutzer meldet am 27.11.2026: Direkttasten HDMI 1–4 schalten nicht um; allgemeine TV-Eingangsauswahl funktioniert. Getestete App-Version nicht ausdrücklich genannt. Quellcode nutzt Android-Remote-v2-Keycodes für beide Wege. Ursache auf TV-Seite und alternative Direktsteuerung noch nicht nachgewiesen. |

- Ziel: Einmaliges Antippen eines HDMI-Eingangs wechselt zuverlässig zum richtigen Eingang, ohne die manuelle Navigation im TV-Eingangsmenü. Individuelle HDMI-Namen und allgemeine TV-Eingangsauswahl bleiben erhalten.
- Zunächst vorhandene T-Cast-Steuerung und mögliche TCL-spezifische lokale Befehle untersuchen. Keine feste Zuordnung ungeprüfter Hardware-IDs, keine blind ausgeführte Menü-Tastenfolge und kein dauerhaftes ADB oder zusätzliche TV-App.
- Gerätetest aller vier Eingänge, aus verschiedenen Ausgangszuständen, auch mit erhaltenem HDMI-CEC/eARC-Verhalten. Keine Erfolgsmeldung allein aufgrund des Befehlsversands.
- Reihenfolge: Aufgabe 0.2 bleibt bis zur Abnahme offen; 0.3 als vorrangige Korrektur vor neuen Funktionen vorgeschlagen. Konkrete Umsetzung und Zielversion noch festzulegen.

## Phase 1 – TV-Fernbedienung vervollständigen (1.2.x)

| ID | Aufgabe | Ziel / Abnahmekriterien | Aufwand | Status | Tatsächliche Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| 1 | Tastatur | Smartphone-Texteingabe in TV-Eingabefeldern, ohne dauerhaftes ADB oder Zusatz-App; bestehende Navigation erhalten | Mittel | Geplant | – |
| 2 | Mauszeigersteuerung | Touchpad für freie Zeigerbewegung und Klick, sofern TV-Schnittstelle dies unterstützt; Pager-Gesten bleiben nutzbar | Mittel | Geplant | – |
| 3 | Absoluter Lautstärkeregler | Tatsächlichen Pegel direkt setzen; TV/Soundbar-Routing und Rückmeldung prüfen, keine simulierten Werte | Mittel | Geplant | – |

T-Cast ist für App-Starts bestätigt. Texteingabe, Maus und absolute TV-Lautstärke müssen separat am TCL nachgewiesen werden. Die Soundbar-Ansteuerung darf nicht mit TV-Lautstärkebefehlen kollidieren.

## Phase 2 – Soundbar vervollständigen (1.3.x)

| ID | Aufgabe | Ziel / Abnahmekriterien | Aufwand | Status | Tatsächliche Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| 4 | Bass und Höhen | Beide gemeldeten EQ-Werte ändern und bestätigten Status anzeigen; Wertebereiche prüfen | Gering | Geplant | – |
| 5 | Neural:X und IMAX Enhanced | Beide Optionen steuern und tatsächlichen Status anzeigen; gerätespezifische Verfügbarkeit berücksichtigen | Gering | Geplant | – |
| 6 | Direkte Soundbar-Stummschaltung | Unabhängig vom TV stummschalten und wieder aktivieren; Status nur bei belastbarer Rückmeldung | Mittel | Geplant | – |

Vorhandene Tonmaschine-Implementierung zuerst prüfen und wiederverwenden. Windows-Unterstützung allein belegt keine Android-Funktion.

## Phase 3 – Datensicherung vervollständigen (1.4.0)

| ID | Aufgabe | Ziel / Abnahmekriterien | Aufwand | Status | Tatsächliche Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| 7 | HDMI-Bezeichnungen sichern | Eigene HDMI-Namen in verschlüsseltem Backup exportieren/importieren; ältere Sicherungen kompatibel halten | Gering | Geplant | – |

## Phase 4 – Bildsteuerung (1.5.x)

| ID | Aufgabe | Ziel / Abnahmekriterien | Aufwand | Status | Tatsächliche Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| 8 | Bildmodus, Helligkeit, Kontrast | Jeweils zuverlässige Abfrage und direkte Änderung am TCL; Schnittstelle und Wertebereiche separat belegen | Hoch | Geplant | – |
| 9 | Persönliche Bildprofile | Unterstützte Bildparameter speichern, verwalten und wiederherstellen; abhängig von Aufgabe 8 | Mittel | Geplant | – |

Keine Änderung an TV-Systemsoftware, keine erfundenen Parameter. Die technische Machbarkeit ist bislang offen.

## Phase 5 – TV-Systemsteuerung (1.6.x)

| ID | Aufgabe | Ziel / Abnahmekriterien | Aufwand | Status | Tatsächliche Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| 10 | Sleep-Timer | Ausschaltzeit einstellen und ggf. verlässlich gemeldete Restzeit anzeigen | Mittel | Geplant | – |
| 11 | Energiesparmodus | Unterstützten Energiemodus auslesen und ändern; Wake-on-LAN und Standby-Verhalten separat prüfen | Hoch | Geplant | – |

## Nächste Aufgabe

**0.2 – TV-App-Übersicht korrigieren (hohe Priorität).** v1.1.8 ist als Pre-Release mit bestandenen automatisierten Tests, Build, Signaturprüfung und APK verfügbar. Jetzt am TCL testen, insbesondere Bestandswechsel und Favoriten. Erst nach Gerätebestätigung und stabiler Veröffentlichung als abgeschlossen einstufen; danach die gemeldete HDMI-Direktwahl (0.3) priorisiert angehen; Tastatur anschließend gemäß bestätigter Reihenfolge.

## Änderungsverlauf

- 27.11.2026: Roadmap angelegt. Alle 15 identifizierten Funktionslücken in elf Entwicklungsaufgaben zusammengefasst; Reihenfolge vom Nutzer bestätigt. Nativer TCL-App-Start, App-Liste, Neuzuordnung und neuer Favorit in v1.1.6 am Gerät bestätigt. Stabile Veröffentlichung als v1.1.7 vorgesehen.

- 27.11.2026: Stabile v1.1.7 (versionCode 26) und erfolgreicher CI-Lauf verifiziert. Aufgabe 0.1 abgeschlossen. Neue Nutzeranforderung mit hoher Priorität als 0.2 vor Phase 1 aufgenommen; Ursache für die falsche App-Übersicht im Quellcode bestätigt. Alle bestehenden Aufgaben und IDs bleiben erhalten.

- 27.11.2026: Aufgabe 0.2 präzisiert: Der abgefragte TV-App-Bestand ist die einzige Datenquelle für die App-Übersicht und die daraus ausgewählten Start-Favoriten. Kein statischer Katalog oder manuell ergänzter Dienst als angeblich installierte App. Keine funktionale Codeänderung; Priorität und Status unverändert.

- 27.11.2026: Aufgabe 0.2 umgesetzt (Code): gemeinsames gerätebezogenes T-Cast-Inventar ohne Persistenz, Quellen und aktive Favoriten daraus abgeleitet, System-App-Filter, Aktualisierung beim Öffnen und manuell; gespeicherte Zuordnungen bleiben erhalten. Unit-Tests ergänzt; v1.1.8 Pre-Release/CI zur Prüfung vorbereitet. **Keine Gerätetestbestätigung; Status bis zum CI-Nachweis In Entwicklung.**

- 27.11.2026: Aufgabe 0.2 auf **Implementiert** hochgestuft. v1.1.8 Pre-Release mit vollständiger signierter APK nachgewiesen: [CI-Lauf](https://github.com/sschermer/Sofacockpit/actions/runs/45929486093), [Pre-Release](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.8). JVM-Tests, Build, feste Signatur bestanden. Statischen Katalog auch als Neuinstallations-Vorauswahl entfernt. Gerätetest des tatsächlichen Inventars, von Deinstallationen und bestehender Favoritenzuordnung noch offen; daher nicht Getestet oder Abgeschlossen.

- 27.11.2026: Fehlgeschlagene HDMI-Direktwahl am TCL gemeldet; allgemeine Eingangsauswahl funktioniert nach Nutzerbeobachtung. Separate Aufgabe 0.3 aufgenommen, Quellcode geprüft, kein Funktionscode geändert. Aufgabe 0.2 bleibt Implementiert; deren App-Inventar-Abnahme ist separat offen.
