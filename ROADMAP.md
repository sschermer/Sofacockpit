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
| 0.3 | HDMI-Eingänge direkt auswählen | 1.1.10 | Hoch – vor Neugestaltung und Phase 1 | Abgeschlossen | v1.1.9: Nutzer bestätigte alle vier nativen HDMI-Direktwahlen am TCL 85C89L. v1.1.10 (versionCode 29): reguläre Bedienung mit identischem bestätigtem Protokoll, bisherigen Portbestätigungen und einmaliger Modellwahl für unbekannte Kopplungen; Nutzerfreigabe und stabiles Release mit vollständiger signierter APK, CI-Tests und Build nachgewiesen. Kurzer UI-Regressionstest nach Update empfohlen. |

- Ziel: Einmaliges Antippen eines HDMI-Eingangs wechselt zuverlässig zum richtigen Eingang, ohne die manuelle Navigation im TV-Eingangsmenü. Individuelle HDMI-Namen und allgemeine TV-Eingangsauswahl bleiben erhalten.
- Zunächst vorhandene T-Cast-Steuerung und mögliche TCL-spezifische lokale Befehle untersuchen. Keine feste Zuordnung ungeprüfter Hardware-IDs, keine blind ausgeführte Menü-Tastenfolge und kein dauerhaftes ADB oder zusätzliche TV-App.
- Gerätetest: Alle vier HDMI-Eingänge am Referenz-TCL erfolgreich bestätigt. Gesonderte Tests aus verschiedenen Ausgangszuständen sowie HDMI-CEC/eARC-Regression wurden nicht ausdrücklich berichtet. Keine Erfolgsmeldung allein aufgrund des Befehlsversands.
- Reihenfolge am 27.11.2026 vom Nutzer bestätigt: HDMI-Direktwahl vor Neugestaltung und Tastatur. Aufgabe 0.2 bleibt bis zur separaten Geräteabnahme offen. Native TCL-Eingangslinks über die bestehende Remote-v2-Sitzung sind am Referenzgerät ohne ADB bestätigt. v1.1.10 übernimmt vorhandene lokale Portbestätigungen und ermöglicht einmalige Auswahl des bestätigten Referenzmodellprofils für eine unbekannte Kopplung. Quellen: https://community.home-assistant.io/t/android-tv-hdmi/234411/107 und https://github.com/Linkestis/tcl-google-tv-adb-hdmi-ha .

## Experimentelle Neugestaltung vor Phase 1

| ID | Aufgabe | Zielversion | Priorität | Status | Nachweis / tatsächliche Version |
| --- | --- | --- | --- | --- | --- |
| 0.4 | Intuitives Bedienkonzept, Screens und Layout neu entwickeln | Pre-Releases, Versionsnummer offen | Hoch – vor Phase 1 | In Entwicklung | Nutzer hat am 27.11.2026 weitreichende Gestaltungsfreiheit für Oberfläche und Bedienlogik erteilt. Rückkehrpunkt v1.1.10 gesichert; zunächst Nutzungsszenarien und Interaktionskonzept klären. Noch kein UI-Code geändert und kein experimentelles Release veröffentlicht. |

- Rückkehrpunkt: [stabiles Release v1.1.10](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.10), versionCode 29, Commit `553472b4d01c0bf04540eef8d8cd4faec49c000f`, zusätzlich [rollback/v1.1.10](https://github.com/sschermer/Sofacockpit/tree/rollback/v1.1.10). Release, Tag und Rückkehrzweig nicht verändern.
- Experimente ausschließlich im separaten Zweig [experiment/intuitive-ui](https://github.com/sschermer/Sofacockpit/tree/experiment/intuitive-ui). Keine Übernahme in main oder stabile Veröffentlichung ohne Nutzerfreigabe. Der bisherige Workflow baut nur main; vor einem experimentellen APK-Build ist ein branchbezogener Pre-Release-Pfad mit unveränderter Signatur einzurichten.
- Schwerpunkt: häufige Aktionen leicht auffindbar, wenige Schritte, einhändige Bedienung, gute Lesbarkeit und Touchziele; Navigation, Screens, Layout, Gesten und Interaktionsformen dürfen im Experiment neu gedacht werden. Die bisher vorgeschriebene Screen-Struktur ist hierfür ausdrücklich nicht bindend; sonstige Sicherheits-, Datenschutz-, Protokoll-, Daten- und Release-Regeln gelten unverändert.
- Bestätigte Nutzungsszenarien: vollständiger Ersatz der physischen TV- und Soundbar-Fernbedienungen mit erweiterten Funktionen. Gaming (HDMI): Bild- und Klangmodus, Lautstärke, DRC/Night Time abhängig von der Uhrzeit. Streaming: Steuerkreuz, OK (auch Pause), Home, Vor/Zurück, Lautstärke. Individuelle Aktivitäten/Makros sind ausdrücklich erwünscht: Quelle, unterstützte Bild-/Klangparameter und passende Bedienoberfläche mit einer Aktion wählen. Nicht nachgewiesene Bildsteuerung bleibt technische Abhängigkeit von Aufgabe 8; keine vorgetäuschten Befehle oder Erfolgsmeldungen. Einzelsteuerung bleibt zugänglich.
- Stimme, Touchpad/Maus und weitere Eingabeformen sind mögliche Ansätze, aber keine Zusage der technischen Machbarkeit. Nur nachgewiesene Funktionen als verfügbar darstellen; keine ungeprüfte Geräteaktion, unnötige Cloud-Abhängigkeit oder Berechtigung. Vor größeren Änderungen Entwurf abstimmen.
- Funktionierende Gerätesteuerung, Konfiguration, Themes, Favoriten, Profile und sichere Kopplung möglichst unverändert wiederverwenden; keine destruktiven Datenmigrationen. Bei Rückkehr auf dem Smartphone den unveränderten funktionalen Stand von v1.1.10 mit höherem versionCode und neuer Versionsnummer, gleicher Paketkennung und Signatur erneut bauen und veröffentlichen. Datenkompatibilität separat prüfen.
- Die separate Geräteabnahme von Aufgabe 0.2 bleibt offen; deren Status wird nicht ohne Nachweis geändert. Phase 1 und weitere Aufgaben bleiben in der Roadmap erhalten.

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

**0.2 – TV-App-Übersicht:** Die separate Geräteabnahme (insbesondere Bestandswechsel und Favoriten) bleibt offen. **0.3 – HDMI-Direktwahl:** Abgeschlossen; v1.1.9 am Referenz-TCL für alle vier Eingänge bestätigt, reguläre v1.1.10 stabil veröffentlicht. Kurzer UI-Regressionstest nach Update empfohlen. **0.4 – Intuitives Bedienkonzept:** Rückkehrpunkt v1.1.10 eingerichtet; experimentelle Entwicklung vor Phase 1, zunächst Nutzungsszenarien und Entwurf. Tastatur und weitere Funktionen anschließend gemäß Roadmap bzw. ausdrücklich abgestimmten technischen Abhängigkeiten. Keine ungeprüfte Hochstufung von Aufgabe 0.2.

## Änderungsverlauf

- 27.11.2026: Roadmap angelegt. Alle 15 identifizierten Funktionslücken in elf Entwicklungsaufgaben zusammengefasst; Reihenfolge vom Nutzer bestätigt. Nativer TCL-App-Start, App-Liste, Neuzuordnung und neuer Favorit in v1.1.6 am Gerät bestätigt. Stabile Veröffentlichung als v1.1.7 vorgesehen.

- 27.11.2026: Stabile v1.1.7 (versionCode 26) und erfolgreicher CI-Lauf verifiziert. Aufgabe 0.1 abgeschlossen. Neue Nutzeranforderung mit hoher Priorität als 0.2 vor Phase 1 aufgenommen; Ursache für die falsche App-Übersicht im Quellcode bestätigt. Alle bestehenden Aufgaben und IDs bleiben erhalten.

- 27.11.2026: Aufgabe 0.2 präzisiert: Der abgefragte TV-App-Bestand ist die einzige Datenquelle für die App-Übersicht und die daraus ausgewählten Start-Favoriten. Kein statischer Katalog oder manuell ergänzter Dienst als angeblich installierte App. Keine funktionale Codeänderung; Priorität und Status unverändert.

- 27.11.2026: Aufgabe 0.2 umgesetzt (Code): gemeinsames gerätebezogenes T-Cast-Inventar ohne Persistenz, Quellen und aktive Favoriten daraus abgeleitet, System-App-Filter, Aktualisierung beim Öffnen und manuell; gespeicherte Zuordnungen bleiben erhalten. Unit-Tests ergänzt; v1.1.8 Pre-Release/CI zur Prüfung vorbereitet. **Keine Gerätetestbestätigung; Status bis zum CI-Nachweis In Entwicklung.**

- 27.11.2026: Aufgabe 0.2 auf **Implementiert** hochgestuft. v1.1.8 Pre-Release mit vollständiger signierter APK nachgewiesen: [CI-Lauf](https://github.com/sschermer/Sofacockpit/actions/runs/45929486093), [Pre-Release](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.8). JVM-Tests, Build, feste Signatur bestanden. Statischen Katalog auch als Neuinstallations-Vorauswahl entfernt. Gerätetest des tatsächlichen Inventars, von Deinstallationen und bestehender Favoritenzuordnung noch offen; daher nicht Getestet oder Abgeschlossen.

- 27.11.2026: Fehlgeschlagene HDMI-Direktwahl am TCL gemeldet; allgemeine Eingangsauswahl funktioniert nach Nutzerbeobachtung. Separate Aufgabe 0.3 aufgenommen, Quellcode geprüft, kein Funktionscode geändert. Aufgabe 0.2 bleibt Implementiert; deren App-Inventar-Abnahme ist separat offen.

- 27.11.2026: Aufgabe 0.3 priorisiert und v1.1.9-Testkandidat implementiert: explizite Bestätigung der nativen HDMI-Eingangslinks je gekoppeltem Gerät und Port, allgemeine Eingangsauswahl und Benennung bleiben unverändert. Automatisierte URI-/Wire-/Grenzwerttests ergänzt; Build und Gerätetest ausstehend. Keine neue Bibliothek oder TV-Software.

- 27.11.2026: Aufgabe 0.3 auf **Getestet** hochgestuft. Nutzer bestätigt alle vier HDMI-Direktwahltests am TCL 85C89L; HW15–HW18 funktionieren auf dem Referenzgerät. [CI-Lauf v1.1.9](https://github.com/sschermer/Sofacockpit/actions/runs/45930670278) vollständig erfolgreich (Tests, APK-Build, feste Signatur, Veröffentlichung); [Pre-Release v1.1.9](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.9) mit vollständiger APK (14.744.512 Bytes) verifiziert. Stabile Freigabe und separate Geräteabnahme von 0.2 stehen aus. Keine Änderung des Funktionscodes.

- 27.11.2026: Nutzer gibt die reguläre Implementierung und stabile Veröffentlichung der am Gerät getesteten HDMI-Direktwahl frei. v1.1.10 (versionCode 29) übernimmt den bestätigten nativen Befehl, entfernt Testdialoge für bereits bestätigte Eingänge und stellt langes Drücken zum Umbenennen wieder her. Neue Kopplungen können das bestätigte Referenzmodellprofil einmalig auswählen. Regressionstests für gerätebezogene Übernahme und Profilwahl ergänzt. CI und Veröffentlichung noch nicht verifiziert; 0.2 bleibt separat offen.

- 27.11.2026: Aufgabe 0.3 **Abgeschlossen**. Alle vier HDMI-Direktwahlbefehle mit v1.1.9 am Gerät bestätigt; reguläre Bedienung mit unveränderter nativer Zuordnung auf Nutzerfreigabe als [stabiles Release v1.1.10](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-v1.1.10) (versionCode 29) veröffentlicht. [CI-Lauf](https://github.com/sschermer/Sofacockpit/actions/runs/45931281811): JVM-Tests, APK-Build, feste Signatur und Veröffentlichung erfolgreich; vollständige APK (14.744.512 Bytes) verifiziert. UI-Regression nach Update und separate Geräteabnahme von Aufgabe 0.2 stehen noch aus.

- 27.11.2026: Aufgabe 0.4 aufgenommen. Auf Nutzerwunsch v1.1.10 als festen Rückkehrpunkt definiert, exakten Release-Commit zusätzlich als Zweig rollback/v1.1.10 gesichert. Separater Experimentzweig experiment/intuitive-ui für die Neugestaltung vorgesehen; main bleibt funktional unverändert. Keine experimentelle APK und keine Änderung der Gerätesteuerung in dieser Iteration.
