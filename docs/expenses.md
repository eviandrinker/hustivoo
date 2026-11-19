# Ausgaben und Belege

## Architektur und Daten

Das neue Backend-Paket `digital.margraf.hustivoo_backend.expense` enthält validierte
Request-Records, einen businessgebundenen JDBC-Repository, Expense-/Receipt-/
Finance-Services und Controller mit einheitlichen JSON-Fehlern. JDBC verwendet die
bereits vorhandene Spring-JDBC-Abhängigkeit und dieselben Spring-Transaktionen wie
die vorhandene Membership-Prüfung. Bestehende JPA-Module bleiben unverändert.

`V9__expenses_receipts.sql` ergänzt ausschliesslich neue Strukturen:

| Tabelle | Inhalt |
| --- | --- |
| `suppliers` | Unternehmensbezogene Lieferanten inkl. Adresse, Kontakt, UID, IBAN, Archivstatus |
| `expense_categories` | Unternehmensbezogene, innerhalb des Unternehmens eindeutig benannte Kategorien |
| `expenses` | Ausgabe mit Lieferantensnapshot, Kategoriename, Beträgen, Steuersatz, Vorsteuerkennzeichnung, Status und Datum |
| `expense_payments` | Unveränderliche Zahlungsmarkierungen mit Korrekturinformationen |
| `expense_receipts` | Belegmetadaten; Dateiinhalt liegt separat im privaten Dateispeicher |

UUIDs, zusammengesetzte Business-Foreign-Keys, Checks, Indizes und Trigger sichern
die Zuordnungen und Zahlungshistorie. V1–V8 wurden nicht geändert. Die bestehenden
Hibernate-Entities werden weiterhin mit `ddl-auto=validate` validiert; die neuen
JDBC-Tabellen werden mit Flyway und Integrationstests geprüft.

Eine Ausgabe startet als UNPAID. PAID wird nur durch einen vollständigen
Zahlungsdatensatz erreicht. Eine Korrektur markiert diesen Datensatz mit Benutzer,
Zeitpunkt und Pflichtbegründung als zurückgenommen; anschliessend kann eine neue
Zahlung erfasst werden. CANCELLED ist endgültig und nur ohne aktive Zahlung möglich.
OVERDUE wird in Europe/Zurich aus Fälligkeit, UNPAID und positivem Bruttobetrag
abgeleitet und nicht gespeichert. Kritische Änderungen sperren die Ausgabenzeile.

Bearbeiten ist nur ohne Zahlungshistorie und bei UNPAID möglich. Löschen ist nur
bei UNPAID ohne Zahlungshistorie und ohne Belege zulässig. Bereits bezahlte oder
stornierte Belege bleiben erhalten; Belege entfernen ist nur bei noch unbezahlten
Ausgaben ohne Zahlungshistorie möglich. Nach einer Zahlungskorrektur bleiben die
ursprünglichen Ausgabendaten unveränderlich. Falsche Ausgaben können dann storniert
und neu erfasst werden. Es gibt bewusst keine stillen Änderungen alter Zahlungen.

Lieferanten- und Kategorienamen werden beim Erstellen übernommen. Bei Bearbeitung
bleiben sie erhalten, solange dieselbe Referenz gewählt ist. Ein bewusster Wechsel
zu einem anderen Lieferanten/einer anderen Kategorie erzeugt einen neuen Snapshot.
Archivierte Referenzen bleiben lesbar, können neuen Ausgaben aber nicht zugeordnet
werden. Kategorien können archiviert, nicht gelöscht werden. Vorschlagskategorien
werden nur über eine explizite, idempotente Aktion angelegt, nicht bei GET-Aufrufen.

## Beträge und MWST

- Speicherung: `NUMERIC(19,2)`, Berechnung: `BigDecimal`, API: Dezimalstrings.
- Netto darf nicht negativ sein; mehr als zwei Dezimalstellen werden abgelehnt.
- Berechnete MWST = Netto × Satz / 100, Rundung auf zwei Stellen mit HALF_UP.
- Brutto = Netto + tatsächlich gespeicherte MWST; kein vom Client geliefertes Brutto.
- Ein expliziter Beleg-Steuerbetrag darf höchstens 0.05 Währungseinheiten vom
  berechneten Wert abweichen, muss nicht negativ sein und darf Netto nicht übersteigen.
- Ohne MWST-Ausweis (`taxRate: null`) ist der Steuerbetrag zwingend 0.
- Der Satz ist ein Snapshot, kein Verweis auf eine globale Steuertabelle. Die UI
  bietet 8.1, 2.6 und 3.8 lediglich als editierbare Vorschläge an, keinen Default.
- Vorsteuer ist separat `UNCHECKED`, `DEDUCTIBLE`, `NOT_DEDUCTIBLE` oder `PARTIAL`.
  Default im Formular ist UNCHECKED. Es erfolgt keine automatische Beurteilung oder
  Berechnung eines Vorsteuerabzugs. PARTIAL ist eine Kennzeichnung zur weiteren Prüfung.
- Die Frontend-Vorschau arbeitet mit BigInt und ist ausdrücklich unverbindlich;
  das Backend validiert und berechnet erneut.

CHF ist die Standardwährung. Andere gültige Währungscodes sind möglich, aber dieses
Modul verwendet einheitlich zwei Dezimalstellen. Es gibt keine Wechselkursumrechnung,
keine komplette MWST-Abrechnung und keine Teilzahlungen für Ausgaben.

## API

Alle Pfade beginnen mit `/api/businesses/{businessId}`. OWNER und MEMBER dürfen
innerhalb ihrer Membership lesen und schreiben. Identität ausschliesslich aus dem
bestehenden Security Context; Session und CSRF bleiben aktiv.

| Methode | Ressource | Funktion |
| --- | --- | --- |
| GET / POST | `/expenses` | Liste / Erstellen |
| GET / PUT / DELETE | `/expenses/{id}` | Detail / Bearbeiten / zulässiges Löschen |
| POST | `/expenses/calculate` | Validierte Betragsberechnung |
| PATCH | `/expenses/{id}/cancel` | Storno mit `{reason}` |
| POST | `/expenses/{id}/payments` | Zahlung mit `{paymentDate, method}` |
| PATCH | `/expenses/{id}/payments/{payment}/reverse` | Korrektur mit `{reason}` |
| GET / POST | `/expenses/{id}/receipts` | Metadaten / Multipart-Upload (`file`) |
| GET | `/expenses/{id}/receipts/{receipt}` | Geschützter Dateiinhalt; `download=true` setzt Attachment |
| DELETE | `/expenses/{id}/receipts/{receipt}` | Zulässiges Entfernen |
| GET / POST | `/suppliers` | Suche / Erstellen |
| PUT | `/suppliers/{id}` | Bearbeiten |
| PATCH | `/suppliers/{id}/archive` bzw. `/restore` | Archivieren / Wiederherstellen |
| GET / POST | `/expense-categories` | Liste / Erstellen |
| PUT | `/expense-categories/{id}` | Umbenennen |
| PATCH | `/expense-categories/{id}/archive` bzw. `/restore` | Archivieren / Wiederherstellen |
| POST | `/expense-categories/defaults` | Vorschläge idempotent ergänzen |
| GET | `/finance/overview?from=YYYY-MM-DD&to=YYYY-MM-DD&currency=CHF` | Kennzahlen und Monatsreihe |

Ausgabenfilter: `search`, `from`, `to`, `supplierId`, `categoryId`, `status`.
Die Liste enthält `items`, nach Währung getrennte `totals` und `monthTotals` für den
aktuellen Monat. Beide Summen beziehen sich auf die Filter und schliessen Stornos
aus. Lieferantensuche verwendet `search` und `archived=false|true`.

Beispiel einer Ausgabe:

```json
{
  "supplierId": "UUID",
  "categoryId": "UUID",
  "description": "Software-Abonnement",
  "reference": "INV-2026-42",
  "expenseDate": "2026-11-19",
  "dueDate": "2026-12-19",
  "currency": "CHF",
  "netAmount": "100.00",
  "taxRate": "8.10",
  "taxAmount": null,
  "inputTax": "UNCHECKED",
  "notes": null
}
```

Fehler: 400 mit sicheren Feldmeldungen, 401 ohne Session, 403 bei fehlendem CSRF,
identische 404 für fremde und unbekannte Ressourcen, 409 bei unzulässigen Änderungen,
413 bei zu grossen Dateien, 503 bei nicht verfügbarem Dateispeicher. Keine internen
Exceptiondetails oder Dateiinhalte werden durch das Modul ausgegeben/protokolliert.

## Privater Belegspeicher

Konfiguration:

- `RECEIPT_STORAGE_DIRECTORY` / `hustivoo.receipts.directory`, Default
  `${user.home}/.hustivoo/receipts` (ausserhalb des Repositories).
- `hustivoo.receipts.max-bytes=10485760` (10 MiB).
- Multipart-Limits: 10 MB pro Datei, 11 MB pro Request. Bei Anpassungen beide Limits
  zusammen ändern. Aktuell ist die UI ebenfalls auf 10 MiB begrenzt.

Ein dediziertes lokales Verzeichnis verwenden, bei Containerbetrieb persistent
mounten. Neu angelegte Verzeichnisse erhalten unter POSIX Zugriffsrechte 0700.
Kein Webserver darf dieses Verzeichnis öffentlich ausliefern. Das Backend lehnt
einen Speicherort innerhalb des Repositories und einen direkt symbolisch verlinkten
Speicherordner ab. Datenbank und Dateien müssen gemeinsam gesichert werden.

Dateitypen werden anhand von Signatur und tatsächlicher Dekodierung geprüft:
PNG/JPEG werden neu kodiert (max. 20 Millionen Pixel). PDFs werden mit dem bereits
vorhandenen PDFBox vollständig gerendert und als neues Bild-PDF gespeichert
(max. 30 Seiten, begrenzte Seitengrösse und Auflösung). JavaScript, Formulare,
Links, eingebettete Dateien und angehängte Payloads werden nicht übernommen.
**Gespeichert und heruntergeladen wird die aufbereitete Darstellung, nicht das
byteidentische Original.** Textsuche, Signaturen und interaktive PDF-Inhalte gehen
verloren. Es gibt keine OCR, Cloud-Verarbeitung oder KI-Erkennung.

Dateinamen werden nur als bereinigte Metadaten genutzt; Pfade bestehen ausschliesslich
aus zufälligen UUIDs. Dateien werden zunächst temporär geschrieben und atomar
umbenannt, danach werden Metadaten in derselben Service-Transaktion erfasst.
Bei Rollback wird die Datei entfernt. Bei Prozessabbruch bleiben gegebenenfalls
verwaiste Dateien zurück: ein stündlicher Scan entfernt ausschliesslich passende,
unreferenzierte Speicherdateien, die älter als 24 Stunden sind. Schlägt die
Datenbankabfrage fehl, wird nichts gelöscht. Bei Entfernen wird die Datei erst
nach dem Commit gelöscht; ein Fehler wird durch denselben Scan erneut aufgegriffen.
Das ist keine verteilte Dateisystem-/Datenbanktransaktion.

Auslieferung nur nach Session-, Membership-, Business- und Ausgaben-Zuordnungsprüfung.
Antworten setzen korrekten Content-Type, sichere Content-Disposition, `nosniff`,
`Cache-Control: no-store` und eine restriktive Sandbox-CSP. Die UI lädt über den
bestehenden authentifizierten Blob-Client und widerruft Vorschau-URLs. PDF-Vorschau
läuft in einem sandboxed iframe; falls der Browser sie blockiert, bleibt Download
möglich. Der Upload nutzt XHR ausschliesslich für echten Fortschritt und teilt die
CSRF-/Session-/Fehlerlogik mit dem vorhandenen API-Client.

## Frontend und Dashboard

- `/expenses`: Filter, Tabelle mit Beleganzahl, getrennte Währungssummen und Leerzustände.
- `/expenses/new` und `/expenses/{id}`: eigener, kompakter Editor statt Dialogkaskaden,
  Vorsteuerkennzeichnung, Zahlungs-/Korrekturverlauf, Drag-and-drop, Mehrfachauswahl,
  Upload-Fortschritt, Vorschau und Download.
- `/expenses/settings`: Lieferanten inkl. Archivierung sowie Kategorienverwaltung.
- `/dashboard`: ausschliesslich echte Finanzkennzahlen statt bisheriger Demoinhalte.

Editor und Liste sind an die aktive Business-ID gebunden. Wechsel/Logout entfernt
Daten und ignoriert verspätete Lese-/Schreibantworten; laufende Uploads werden
abgebrochen. Vorschau-Blob-URLs werden widerrufen. Keine Daten werden in localStorage
gespeichert. Es werden keine Schreibvorgänge automatisch wiederholt.

Das Dashboard trennt Rechnungsumsatz (Ausstellungsdatum) von tatsächlichen Eingängen
(Zahlungsdatum) und erfasste Ausgaben (Ausgabedatum) von bezahlten Ausgaben
(Zahlungsdatum). Stornierte Rechnungen/Ausgaben und zurückgenommene Zahlungen zählen
nicht. Teilzahlungen bestehender Rechnungen werden nur mit ihrem tatsächlichen
Betrag berücksichtigt. Offene Forderungen werden nicht durch Payment-Joins dupliziert.
Die Aggregationen laufen mit einem konsistenten Datenbank-Snapshot. Zeitraum:
maximal fünf Jahre. Die UI zeigt CHF ohne Umrechnung; die Ausgabenliste trennt Währungen.
Offene Beträge sind der **aktuelle** Stand der im Zeitraum ausgestellten Belege,
keine rückwirkende Stichtagsbilanz. Kein steuerlicher Gewinn und kein vollständiger Cashflow.

## Tests und manuelle Prüfung

Neue Backend-Tests verwenden echte PostgreSQL-Testcontainers: CRUD, Storno, Zahlung,
begründete Korrektur, parallele Zahlung, MEMBER, MWST/Rundung/Vorsteuer, Snapshots,
Kategorien, Filter, Upload/Download, Inhalt/Grössenlimit, PDF-Sanitierung,
Rollback-/Orphan-Cleanup, Mandantentrennung/IDOR, Session/CSRF, Dashboard inkl.
Rechnungsteilzahlung, Stornos und Korrekturen sowie V8→V9-Upgrade mit Bestandsdaten.
Frontend-Tests prüfen API-Verträge, CSRF-Uploadfortschritt, Blob-Typprüfung,
BigInt-Vorschau, Auswahl/Validierung, State-Cleanup, Fehler/Leerzustände und gerenderte
Formulare/Zahlungshistorie/Belegaktionen/Dashboard-Kennzahlen.

Noch manuell im Browser prüfen (nicht durchgeführt):

1. Zwei Businesses und OWNER/MEMBER verwenden. Pro Business Lieferanten und
   Kategorien anlegen; Wechsel darf keine alten Daten anzeigen.
2. Ausgabe mit und ohne MWST, Rundungsabweichung und ungeprüfter Vorsteuer speichern.
   Lieferant später umbenennen; die alte Ausgabe muss ihren Snapshot behalten.
3. Zahlung erfassen, mit Begründung zurücknehmen, neu buchen; unzulässiges Editieren,
   Stornieren bezahlter Ausgaben und Löschen historischer Daten müssen scheitern.
4. Mehrere PDF/PNG/JPEG per Auswahl und Drag-and-drop hochladen; Fortschritt,
   Vorschau/Download, Escape, Dateinamen, grosse/ungültige Dateien prüfen.
5. Während Upload/Download Business wechseln oder abmelden; keine alte Vorschau
   oder Daten dürfen erneut erscheinen. Netzwerkausfall und Sessionablauf prüfen.
6. Rechnung ausstellen, Teilzahlung erfassen und zurücknehmen; CHF-Dashboard und
   Monatsdiagramm mit der Ausgaben-Zahlungshistorie abgleichen.
7. Desktop, Tablet und Smartphone: Tabellen-Scroll, Formulare, Tastaturfokus,
   Feldfehler, Screenreader-Labels und PDF-Vorschau testen.

Grenzen: keine Pagination, OCR, revisionssichere Originalarchivierung, Antivirus-
Engine, Wechselkurse oder vollständige Buchhaltung/MWST-Abrechnung. Einzelne
Dateien werden bis zum konfigurierten Limit im Speicher verarbeitet. Der private
Dateispeicher braucht Betriebsüberwachung und Backup; er ist zunächst lokal.

## Abschliessende Verifikation

- Backend: `./mvnw clean verify` erfolgreich; **142 Tests**, 0 Fehler,
  0 Fehlschläge, 0 übersprungen. Enthält 14 neue Ausgaben-Integrationstests.
  Flyway V9, bestehende Hibernate-Schemavalidierung und ausführbares JAR erfolgreich.
- Frontend: **117 Tests**, 0 Fehler, darunter 17 neue Ausgaben-/Dashboard-Tests.
- ESLint erfolgreich.
- TypeScript (`tsc -b`) erfolgreich.
- Vite-Production-Build erfolgreich.
- `git diff --check` ohne Befund.

Im Entwicklungsablauf wurden ein Test-Builder-Typfehler und ein direkter Feldzugriff
auf einen Spring-Repository-Proxy in der Aggregation behoben. Beide waren Codefehler,
keine Infrastrukturprobleme. Vor dem vollständigen Build waren etwa 12 GiB frei;
der Lauf dauerte etwa 2 Minuten 11 Sekunden und wurde erfolgreich abgeschlossen.
Keine Neuinstallation, keine Docker-Volume-/Datenbank-/Nutzerdaten-Löschung, keine
Änderung alter Migrationen und keine automatischen Commits oder Pushes.
