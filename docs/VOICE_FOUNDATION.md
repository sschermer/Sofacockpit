# Sofacockpit Lab – gemeinsame Sprachgrundlage

Entwurf 27.11.2026. Gilt für die Weiterentwicklung im bestehenden Lab-Zweig. Kein semantisches Modell und kein neuer KI-Zweig beschlossen. Die regelbasierte Sprachsteuerung bleibt vollständig lokal.

## Zielbild (UI-Entwurf, vor größeren Änderungen am Gerät abzustimmen)

```
Stimme                                  Schließen

               [ Mikrofon ]
               Bereit / Höre zu / Werte aus

 Du:            Ich möchte Basketball schauen.
 Sofacockpit:   Dyn starten.
               Eindeutig erkannt – direkt ausführen

 [Text eingeben]        [Sprachbibliothek]
```

Bei mehreren Kandidaten tritt an die Stelle der einzelnen Vorschau eine kurze Frage mit passenden, tatsächlich verfügbaren Zielen (z. B. PlayStation 5 oder Xbox). Auswahl durch Sprache oder Antippen; niemals allein aufgrund eines ähnlichen Begriffs starten. Bei unbekanntem Satz keine willkürliche Auswahl erfinden. Möglichkeit, einen vorhandenen Befehl gezielt aus der Bibliothek zu wählen, oder abzubrechen.

Nutzerentscheidung 27.11.2026: Kurzer Tipp auf den Tab Stimme startet direkt die lokale Aufnahme, sofern Mikrofonfreigabe und Sprachdienst vorhanden sind. Langer Druck öffnet ausschließlich die Bibliothek. Diese erhält eine Vollbildansicht im gewählten Theme mit Suche, Befehlsgruppen, Filter Nur eigene sowie Detail- und Bearbeitungsansichten. Der Sprachdialog wird kompakt im gleichen Design gehalten. L01.3a setzt dies vor TTS und Dialoglogik um; Geräteabnahme bleibt notwendig.

## Bibliothek

- Standardbefehle, installierte TV-Apps, HDMI-Bezeichnungen, Klangmodi, lokale/NAS-Klangprofile; keine vorgetäuschten Funktionen.
- Pro vorhandenem Ziel Standardformulierungen und persönliche Synonyme. Künftige Makros als gleichartige benannte Ziele mit stabiler lokaler ID.
- Kurze Synonyme wie Basketball für Dyn automatisch mit passenden, vollständigen Satzmustern ergänzen. Typen getrennt behandeln: Quelle auswählen, App öffnen, Klangprofil auswählen, Schalter ein/aus, Lautstärke. Keine universelle Teilwortsuche. Für vollständige eigene Sätze exakte Zuordnung beibehalten.
- Alle Varianten in der Editorvorschau nachvollziehbar. Vor dem Speichern Konflikte prüfen; später entstandene Konflikte und nicht verfügbare Ziele blockieren. Bestehende Synonyme nicht löschen.
- Bei der späteren Gesamtsicherung die persönlichen Zuordnungen und Makros in das verschlüsselte Format integrieren; ältere Sicherungen kompatibel importieren, nicht ungefragt überschreiben.

## Dialogablauf (nächste Iteration L01.3, Entwurf)

1. Bewusster Start des Sprachdialogs; im Vordergrund, kein Always-On-Mikrofon. Lokale deutsche Spracherkennung. Text-Alternative immer erhalten.
2. Regelbasierte Interpretation über vollständige Phrasen, Verneinungs- und Konfliktprüfung. Ergebnis ist ein vorhandener Befehlsdatensatz oder ein begrenzter Auswahl-/Fehlerzustand; keine Geräteaktion direkt aus erkanntem Text.
3. Nutzerentscheidung 27.11.2026: Eindeutige finale Sprachbefehle nach bewusstem Aufnahmestart unmittelbar einmalig ausführen (L01.3b); Texteingaben weiter ausdrücklich absenden. Bei Unklarheit oder mehreren Kandidaten nur passende Rückfragen bzw. Auswahl anbieten, keine ungesicherte Ausführung. Bei Rückfrage nur zur aktuellen Auswahl passende Antworten akzeptieren. Beschränkte Anzahl von Versuchen und Zeitlimits. Gesprochene Antworten bleiben L01.3.
4. Lokale Text-to-Speech-Stimme auf Smartphone, bei fehlender lokaler deutscher Stimme sichtbare Rückmeldung. Kein Online-Fallback. Audio-Routing und Offline-Sprachqualität auf dem Referenzsmartphone testen; TV-Wiedergabe nicht gezielt unterbrechen. Mikrofon während der eigenen Sprachausgabe deaktiviert; erst nach Abschluss einer Frage im selben aktiven Vordergrunddialog wieder zuhören.
5. Abbruch, Hintergrundwechsel oder Zeitlimit beendet Aufnahme und Dialog. Keine erneute Aktivierung außerhalb des bewussten Dialogstarts. Bestehende Geräteprüfungen bei Ausführung wiederholen. Nur angeforderte oder bestätigte Zustände als solche bezeichnen.
6. Optionales bestätigtes Lernen L01.4: Nutzer ordnet eine unbekannte Formulierung ausdrücklich einem bestehenden Ziel zu, danach separate Frage, ob die Formulierung gespeichert werden soll. Kollisionsprüfung wie im Editor. Kein automatisches Lernen, keine Geräteaktion allein durch Speicherung.

## Gemeinsame Architektur

```
Lokale Speech-to-Text / Text
            |
      Interpretation
   (regelbasiert; optional später separater semantischer Vorschlagsdienst)
            |
  Bibliothek mit typisierten Zielen / Synonymen
            |
  Dialogzustand, direkte Ausführung / Auswahl bei Unklarheit
            |
  aktueller zulässiger Befehl / künftig Makro
            |
  bestehende TV-/Soundbar-Steuerung
```

Das optionale lokale semantische Modell wäre nur eine spätere, getrennt erprobbare Vorschlagsquelle. Es dürfte keine Rohbefehle erzeugen, keine Sicherheitsprüfung umgehen, keine eigenen Netzwerkaktionen auslösen und keine Kosten oder Cloud-Abhängigkeit verursachen. Modelllizenz, Größe, Laufzeit, deutsche Erkennungsqualität (insbesondere gegensätzliche Befehle) und Geräteverfügbarkeit zunächst untersuchen. Kein Modell hinzufügen, bevor die gemeinsame Basis und die Entscheidung des Nutzers vorliegen.

## Entwicklungsstufen und Abnahme

- L01.2 / v0.1.3: Satzmuster und Vorschau im vorhandenen Editor; Tests für Synonyme, unterschiedliche Befehlstypen, Verneinungen, Mehrdeutigkeiten, Kollisionen, fehlende Ziele und bestehende Speicherformate. UI bleibt zunächst Dialog.
- L01.3b / v0.1.5: direkte Ausführung eindeutig erkannter finaler Sprachbefehle; Einmaligkeit, Live-Zielprüfung und Vordergrundbindung. Texteingabe bestätigt; Geräteabnahme offen.
- L01.3: gemeinsame Dialogoberfläche, lokale Ausgabe über Smartphone, Rückfragen und Abbruch/Zeitlimit. UI-Entwurf und Gerätefeedback vor weiterem Ausbau.
- L01.4: bestätigtes Lernen; persistente Zuordnung mit Kollisionsprüfung.
- L04 / L19: Makros und Gesamtsicherung als gemeinsame Funktionen. Nicht in v0.1.3 vorziehen; separate kleinere Iterationen.
- Tastatur (L02) und Touchpad (L03) bleiben offene Prioritäten. Kein neuer KI-Zweig ohne Nutzerentscheidung.

Die Roadmap und der tatsächlich verifizierte Entwicklungsstand sind maßgeblich. Dieser Entwurf ersetzt weder Rulepaper noch bestehende Release-Regeln.
