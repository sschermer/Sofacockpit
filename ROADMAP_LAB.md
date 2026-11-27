# Sofacockpit Lab – Roadmap

Stand: 27.11.2026

Diese Roadmap steuert die eigenständige Experimentier-App im Zweig `experiment/intuitive-ui`. Die [Roadmap der Haupt-App](https://github.com/sschermer/Sofacockpit/blob/main/ROADMAP.md) bleibt die zentrale projektweite Aufgabenliste. Lab-spezifische Aufgaben und ihre Reihenfolge werden hier gepflegt; gemeinsame Aufgaben werden mit ihren IDs verknüpft und bei relevanten Änderungen mit der zentralen Roadmap abgeglichen.

## Ausgangsstand

[Sofacockpit Lab v0.1.0](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-lab-v0.1.0), versionCode 1, Anwendungspaket `de.sofacockpit.lab`. [CI-Lauf 45936466855](https://github.com/sschermer/Sofacockpit/actions/runs/45936466855) erfolgreich: JVM-Tests, Build, feste Signatur, Paket-/Versionsprüfung und vollständige Pre-Release-APK verifiziert. Der Nutzer beurteilt den Entwurf positiv; das ist keine pauschale Geräteabnahme aller Funktionen. Reguläre App v1.1.10 und Rückkehrzweig `rollback/v1.1.10` bleiben geschützt.

## Verbindliche nächste Prioritäten

| ID | Rang | Aufgabe | Ziel / Abnahmekriterien | Status | Version / Nachweis |
| --- | --- | --- | --- | --- | --- |
| L01 | 1 | Sprachsteuerung | Tab Stimme aktivieren; bewusste Spracheingabe für vorhandene TV-/Soundbar-Befehle, Streamingdienste und HDMI-Auswahl; erkannte Befehle eindeutig auswerten, Fehler anzeigen, keine ungewollten Aktionen. Lokale/on-device Erkennung bevorzugen; Verfügbarkeit, Sprache, Berechtigungen und Datenschutz prüfen. Keine ständig lauschende App, keine neue Cloud-Abhängigkeit ohne Freigabe. Später Makros über dieselbe Befehlsebene auslösen. | Getestet | v0.1.1: CI und Pre-Release erfolgreich; Nutzer bestätigt erste Sprachsteuerung allgemein („klappt“); einzelne Befehle und Grenzfälle noch nicht separat abgenommen |
| L01.1 | 1a | Bearbeitbare Sprachsynonyme | Eigene Formulierungen vorhandenen Befehlen zuordnen, ändern und löschen; Standardformulierungen erhalten; dynamische Ziele prüfen; Kollisionen und Verneinungen ablehnen; lokal speichern, keine Geräteaktion durch Editor. | In Entwicklung | v0.1.2 geplant; Nutzerauftrag 27.11.2026 |
| L02 | 2 | Tastatur | Smartphone-Texteingabe in TV-Eingabefeldern. Bestehende Remote-v2-/T-Cast-Schnittstellen prüfen und nutzen, sofern geeignet; Texteingabe und Korrekturen am TCL testen. Kein dauerhaftes ADB oder zusätzliche TV-App. Bezug Haupt-Roadmap 1. | Geplant | Noch keine Texteingabesteuerung |
| L03 | 3 | Touchpad / Mauszeiger | Freie Zeigerbewegung und gezielter Klick auf dem TV, sofern die Schnittstelle dies unterstützt; von Steuerkreuz, Scrollen und haptischer Orientierung getrennte Gesten. Keine vorgetäuschte Maussteuerung durch bloße Richtungstasten. Bezug Haupt-Roadmap 2. | Geplant | Noch keine Maussteuerung |

Diese Reihenfolge gilt auf ausdrücklichen Nutzerwunsch. Technische Abhängigkeiten oder Blockaden dokumentieren; bei erforderlicher Umpriorisierung begründen und abstimmen. Erst nach den drei priorisierten Aufgaben die übrigen geplanten Funktionen nach bestätigter Reihenfolge bearbeiten. Kleine notwendige Korrekturen und Gerätetests bleiben möglich.

## Weitere offene Aufgaben – Reihenfolge noch nicht festgelegt

| ID | Bereich | Aufgabe | Ziel / Abnahmekriterien | Status | Bezug / Voraussetzung |
| --- | --- | --- | --- | --- | --- |
| L04 | Bedienkonzept | Eigene Aktivitäten / Makros | Quelle und ausdrücklich gewählte, nachgewiesene Bild-/Tonparameter speichern und wieder aufrufen; nicht ausgewählte Werte unverändert lassen. | Geplant | Platzhalter; 0.4 |
| L05 | Bedienkonzept | Automatisches Einschalten beim Aktivitätsstart | Nur bei explizitem Aktivitätsstart und erforderlichen Geräten; bestätigtes Wake-on-LAN nutzen, keine blinden Power-Toggles, Fehler transparent. | Geplant | 0.4; Gerätezustand und Wake-Funktion prüfen |
| L06 | Bedienkonzept | Zeitabhängige Makros | Optional z. B. Night Time abhängig von der Uhrzeit einstellen; bewusste Makroauswahl, keine ungefragten Hintergrundaktionen. | Geplant | L04 |
| L07 | Bedienkonzept | Google-Home-Integration | Sprachbefehle vom Google-Lautsprecher als optionale Integration untersuchen; Datenschutz und technische Voraussetzungen separat klären. Keine Pflicht-Cloud oder dauerhafte Abhängigkeit. | Geplant | L01; ggf. L04 |
| L08 | TV | Absoluter TV-Lautstärkeregler | Tatsächlichen Pegel direkt setzen; Routing und Rückmeldung prüfen, keine erfundenen Werte. | Geplant | 3 |
| L09 | TV | Bildmodus | Aktuellen Modus zuverlässig ermitteln und direkt wählen. | Geplant | Platzhalter; 8 |
| L10 | TV | Helligkeit und Kontrast | Gemeldete Bildparameter direkt ändern; Schnittstelle und Wertebereiche belegen. | Geplant | Platzhalter; 8 |
| L11 | TV | Individuelle Bildprofile | Unterstützte Bildparameter speichern, verwalten und wieder anwenden. | Geplant | Platzhalter; L09/L10; 9 |
| L12 | TV | Sleep-Timer | Ausschaltzeit einstellen und gemeldete Restzeit anzeigen, sofern unterstützt. | Geplant | Hinweis im UI; 10 |
| L13 | TV | Energiesparmodus | Unterstützte Einstellungen auslesen und ändern; Standby und Wake-on-LAN berücksichtigen. | Geplant | Hinweis im UI; 11 |
| L14 | Soundbar | Bass und Höhen | Gemeldete EQ-Werte ändern und Rückmeldung anzeigen; Wertebereiche prüfen. | Geplant | Nur Anzeige; 4 |
| L15 | Soundbar | Neural:X | Option mit belegtem Status steuern, sofern verfügbar. | Geplant | Platzhalter; 5 |
| L16 | Soundbar | IMAX Enhanced | Option mit belegtem Status steuern, sofern verfügbar. | Geplant | Platzhalter; 5 |
| L17 | Soundbar | Direkte Stummschaltung | Unabhängig vom TV stumm/wieder laut schalten, bestätigten Status anzeigen. | Geplant | 6 |
| L18 | Konfiguration | Zentrale Konfigurationsseite einschließlich NAS | TV, Soundbar, NAS-/Profilspeicher und übrige Einstellungen zentral erreichbar; separaten Konfigurationsscreen entbehrlich machen, Profilverwaltung erhalten. | Geplant | 7.1 |
| L19 | Konfiguration | Gemeinsame verschlüsselte Export-/Importdatei | NAS-Pfad, Zugangsdaten und Profilspeicherort zusammen mit bestehender Konfiguration sichern; kompatibler Import aus Haupt-App und Lab, Altformat weiter lesbar, keine unbeabsichtigte Löschung oder Profilüberschreibung. | Geplant | 7.1; für Wechsel mit nur einem Import auch Export der Haupt-App erweitern |
| L20 | Konfiguration | HDMI-Bezeichnungen sichern | Benennungen verschlüsselt exportieren/importieren; ältere Sicherungen kompatibel halten. | Geplant | 7 |

Die Zuordnung zu den Haupt-Roadmap-IDs bedeutet keine automatische Implementierungs- oder Release-Freigabe für die Haupt-App.

## Bereits implementiert – Gerätetests offen

| ID | Aufgabe | Status | Version / Nachweis und offene Abnahme |
| --- | --- | --- | --- |
| LT01 | Haptische Orientierung | Implementiert | Lab v0.1.0: Entprellung und Zonenwechsel im JVM-Test; physische Unterscheidbarkeit, unbeabsichtigte Betätigung und Scroll-Konflikte am Smartphone prüfen. |
| LT02 | Aktivitätenwechsel und Rückkehr zur Aktivität | Implementiert | Lab v0.1.0: kontextbezogene Navigation im Code, Build erfolgreich; Streaming-, HDMI- und Musikabläufe am Gerät prüfen. |
| LT03 | Animierte Quellenauswahl | Implementiert | Lab v0.1.0: Auswahl in derselben Zeile, TV-Inventar und HDMI-Namen; Animation, horizontales Scrollen und Erreichbarkeit testen. |
| LT04 | Separater Update-Mechanismus | Implementiert | Lab v0.1.0: getrennte Paketkennung, Tags und Assetnamen; JVM-Tests und signierte Veröffentlichung nachgewiesen. Parallele Installation und tatsächliches Update von Lab auf eine Folgeversion testen. |
| LT05 | Konfigurationsübernahme und NAS-Profilzugriff | Implementiert | Vorhandene Import-/Exportfunktion und NAS-Anbindung unverändert aus v1.1.10. Quellcodevergleich bestätigt gleiches bisheriges Sicherungs- und NAS-Profilformat. Import portabler TV-Kopplung und gemeinsame NAS-Profile am Gerät prüfen; NAS-Zugangsdaten derzeit getrennt einzurichten. Erweiterungen siehe L18–L20. |

## Pflege und Releases

- Statusstufen: Geplant, In Entwicklung, Implementiert (Code, relevante Tests und Build erfolgreich), Getestet (konkrete Nutzerbestätigung am Gerät), Abgeschlossen (Gerätetest und stabile Veröffentlichung nachgewiesen). Ein Lab-Pre-Release allein ist keine stabile Veröffentlichung.
- Die Roadmap bei Aufgabenbeginn, Umsetzung, Build, Gerätetest und Release aktualisieren. Historie und IDs dauerhaft erhalten. Bei gemeinsamen Funktionen die zentralen Aufgaben verknüpfen und den jeweiligen Stand pro App getrennt dokumentieren.
- Lab ausschließlich im Experimentierzweig entwickeln. Eigene signierte Pre-Releases, monoton steigende versionCodes, separater Update-Kanal; kein Merge in main und keine stabile Freigabe ohne Nutzerentscheidung.
- Technische Machbarkeit von Sprache, TV-Texteingabe, Maussteuerung und anderen neuen Geräteschnittstellen muss nachgewiesen werden. Platzhalter nicht als funktionierend ausgeben.
- Nächster Schritt auf ausdrücklichen Nutzerauftrag: **L01.1 bearbeitbare Sprachsynonyme**; danach L02 Tastatur. Bestehende L01-Gerätebestätigung bleibt erhalten.

## Änderungsverlauf

- 27.11.2026: Eigenständige Lab-Roadmap auf Nutzerwunsch angelegt. Sprachsteuerung, Tastatur und Touchpad/Mauszeiger verbindlich in dieser Reihenfolge priorisiert. Übrige offene Funktionen übernommen; ihre Reihenfolge bleibt offen. Keine Änderungen an Android-Code, Build oder Veröffentlichungen.

- 27.11.2026: L01 begonnen auf Nutzerfreigabe. Erste Version mit lokaler Android-Erkennung (ab API 31 bei verfügbarem Dienst), Texteingabe als lokaler Fallback, deterministischer deutscher Befehlsbibliothek, dynamischen TV-/HDMI-/Profilnamen, Vorschau mit expliziter Ausführung. Keine Cloud, keine Daueraufnahme. Mehrdeutige, negierte oder unbekannte Befehle nicht ausführen. Keine neue Geräteprotokollimplementierung; eigene Synonyme und Google Home bleiben separat.

- 27.11.2026: L01 v0.1.1 Quellcode: reine Java-Bibliothek mit vollständigen Phrasen, Verneinungs-/Kollisionsprüfung und dynamischen Namen; lokale Android-Erkennung per bewusstem Mikrofontipp, Texteingabe, explizite Vorschau und Bestätigung, vorhandene Befehle/Statusprüfungen. JVM-Tests hinzugefügt, CI-Nachweis ausstehend; Status In Entwicklung. Einzelne Pause/Fortsetzen-Befehle nicht als ungesicherte Toggle-Aktion interpretiert. Eigene Synonyme und Google Home offen.

- 27.11.2026: Lab v0.1.1 (versionCode 2) veröffentlicht als [Pre-Release](https://github.com/sschermer/Sofacockpit/releases/tag/sofacockpit-lab-v0.1.1), vollständige APK 14.760.952 Bytes, SHA-256 `2ba53330343b7328ff2abdb7eb07abdb0497fdc21ef4e0ece2192c382df720d2`, Release-Commit `97a900c15b61f0d166109d9463924cbb036250aa`. [CI 45940069614](https://github.com/sschermer/Sofacockpit/actions/runs/45940069614) vollständig erfolgreich: JVM-Tests, Build, feste Signatur, Paket-/Versionsprüfung und Veröffentlichung. Nutzer bestätigt die erste Sprachsteuerung allgemein mit „klappt“. L01 **Getestet** für die erste funktionsfähige Sprachsteuerung; keine Einzelabnahme sämtlicher Befehle, Fehlerfälle oder Updateübernahme daraus ableiten. Keine stabile Freigabe. Befehlsbibliothek ist fest im Code; eigene Synonyme und Verwaltung noch nicht implementiert. Nächster regulär priorisierter Schritt L02 Tastatur; Erweiterung der Sprachbibliothek bei ausdrücklichem Auftrag möglich.

- 27.11.2026: L01.1 begonnen. Nutzer beauftragt bearbeitbare Synonyme zu vorhandenen Befehlen. Vorschlag für später: Makros als eigene, aus erlaubten Gerätebefehlen zusammengesetzte Ziele mit Namen und Sprachsynonymen. Neue Makroverwaltung und neue Gerätefunktionen sind nicht Bestandteil dieses Auftrags. Sicherung eigener Synonyme bei L19 berücksichtigen.
