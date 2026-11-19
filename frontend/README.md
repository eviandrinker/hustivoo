# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend updating the configuration to enable type-aware lint rules:

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...

      // Remove tseslint.configs.recommended and replace with this
      tseslint.configs.recommendedTypeChecked,
      // Alternatively, use this for stricter rules
      tseslint.configs.strictTypeChecked,
      // Optionally, add this for stylistic rules
      tseslint.configs.stylisticTypeChecked,

      // Other configs...
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

You can also install [eslint-plugin-react-x](https://npmx.dev/package/eslint-plugin-react-x) and [eslint-plugin-react-dom](https://npmx.dev/package/eslint-plugin-react-dom) for React-specific lint rules:

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // Other configs...
      // Enable lint rules for React
      reactX.configs['recommended-typescript'],
      // Enable lint rules for React DOM
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // other options...
    },
  },
])

```

## Hustivoo dashboard

Use Node 22. The development server forwards `/api` to `http://localhost:8080`.
Start the Spring Boot backend separately to see the live connection indicator.

- `npm run dev`: local development
- `npm run lint`: ESLint
- `npm run build`: TypeScript project checks and production build

The app redirects `/` to `/dashboard`. The other six workspace routes currently
show placeholder pages. Production hosting must serve `index.html` for frontend
routes and route `/api` to the backend (the Vite proxy is development-only).

Financial figures, chart history, clients, invoices, and projects are demo fixtures
in `src/data/dashboard.ts`; currency is CHF. Profit is revenue less expenses,
before tax; effective hourly rate is that profit divided by tracked hours.
No business data is persisted and no authentication is implemented.

The existing `src/api` client and health service handle HTTP communication.
`src/hooks/useBackendHealth.ts` manages the shell's live connection state.
`src/layouts`, `src/pages`, and `src/components` contain the presentation layer.
Tailwind is integrated through its Vite plugin, and `components.json` configures
shadcn/ui with a local Card component and the `@/` source alias.

## Frontend authentication

`/login` and `/register` use the existing Hustivoo controls. All workspace routes
are protected. `AuthProvider` restores the browser session via `/api/v1/auth/me`
before rendering routes; a failed connection offers retry rather than incorrectly
showing a signed-out state. Authenticated visitors to auth pages go to the dashboard.
A protected destination is retained through login/registration, with redirects
restricted to known local workspace paths.

The shared API client forces `credentials: 'same-origin'` and accepts only relative
`/api/` URLs. It fetches `/api/v1/auth/csrf` before each mutation and sends the returned
`X-CSRF-TOKEN`. Tokens are held only in memory. Login and logout refresh CSRF after
success; if that refresh fails, the next mutation obtains a fresh token. No mutation
is automatically replayed. HTTP 204 is supported for logout. The browser manages
the HttpOnly session cookie; neither it nor authentication tokens are stored in
localStorage/sessionStorage.

Registration validates email, a nonblank password of at least 12 characters,
at most 72 characters/UTF-8 bytes, and matching confirmation. Success redirects
to login with confirmation; it does not automatically authenticate. Backend
validation remains authoritative. Errors are translated into safe user messages.

Authenticated 401 responses clear local identity. A 403 checks `/auth/me` to
distinguish an expired session from a rejected CSRF token; it never retries the
original mutation. Returning to a visible tab rechecks the session. Connection
failures during these background checks do not falsely sign users out. Logout
clears state only after server success (or a confirmed expired session); server
failures remain visible with a retryable Sign out action.

Files: `src/auth` owns state/guards/messages, `src/api/auth.ts` validates API data,
`src/pages/AuthPage.tsx` handles both forms, and `LogoutButton` is part of the shell.
Dashboard/module content and backend sources are unchanged. The backend must be
running with the documented auth endpoints and the existing Vite `/api` proxy.

Run `npm test` with Node 22 for six focused client tests using Node's built-in
runner and the existing TypeScript dependency. No additional testing dependency
was installed. Run `npm run lint` and `npm run build` for ESLint, strict TypeScript,
and bundling. These client tests mock fetch; they do not replace a browser test
of registration, login, refresh, routing and logout against the backend.

## Business-Onboarding

Nach der Auth-Wiederherstellung lädt `BusinessBoundary` die echten Unternehmen
über `/api/businesses`. Der benutzergebundene Provider wird bei Logout oder
Accountwechsel entfernt; sein Store wird geleert. Verspätete Antworten können
mittels Request-Generation keine alten Unternehmensdaten zurückschreiben.

`src/api/business.ts` validiert Listen-, Einzel- und Erstellungsantworten zur
Laufzeit und verwendet ausschliesslich den bestehenden Cookie-/CSRF-Client.
`src/business/store.ts` verwaltet Liste, Auswahl, Lade-/Fehlerzustand, Refresh und
Erstellung; React bindet ihn über `useSyncExternalStore` ein. Die Auswahl bleibt
nur im Speicher und wird nach einem Reload aus der serverseitigen Membership-
Liste wiederhergestellt (erstes Unternehmen). Refresh erhält eine noch gültige
Auswahl; entfernte Memberships verschwinden aus der Auswahl. Der Switcher ist
nur UI-Zustand, niemals serverseitige Autorisierung.

Routing wartet erst auf Auth, dann auf Businesses. Nur eine erfolgreiche leere
Liste führt zu `/onboarding`. API-Fehler zeigen Retry und Logout. Ein vorhandenes
Unternehmen führt von `/onboarding` zum Dashboard; andere geschützte Modulrouten
bleiben erreichbar. Erfolgreiche Erstellung übernimmt das Response-DTO direkt
in den Store und leitet zum Dashboard weiter, ohne einen zweiten unsicheren POST.

Das Formular übernimmt die Backend-Grenzen: Name erforderlich, maximal 255
Zeichen; Branche und Unternehmensform optional, maximal 120 Zeichen. Leere
optionale Texte werden null. Land/Währung starten mit CH/CHF und werden als
ISO-Codes normalisiert. Die Eingabe erlaubt sämtliche ISO-Codes statt einer
künstlich beschränkten Länder-/Währungsliste. Die vollständige ISO-Gültigkeit
prüft das Backend; 400 wird verständlich angezeigt. Bei unbestätigter Erstellung
kann zuerst die Liste neu geladen werden, um Doppelanlagen zu vermeiden.

`npm test` führt nun 14 Tests aus. Die zusätzlichen Tests prüfen den tatsächlichen
API-Client und Store: leere/vorhandene Listen, Wiederherstellung, Erstellung,
Validierung, API-/Netzwerkfehler, Wechsel, Logout-Cleanup und verspätete Antworten.
Test-Fixtures sind ausschliesslich in Tests vorhanden. UI und Business-Liste
verwenden keine Mock-Unternehmen. Diese Tests ersetzen keine Browserprüfung;
React-Routing/Formularinteraktionen wurden nicht im Browser verifiziert.

Backend und vorhandene Dashboard-Demokennzeichnung bleiben unverändert. Keine
neuen Abhängigkeiten. Keine Auswahl wird in localStorage gespeichert.

### Visuelle Überarbeitung des Onboardings

Das Onboarding verwendet eine zentrierte, maximal 560px breite Card mit Branding,
„Schritt 2 von 2“, nativen Dropdowns und dem Button „Loslegen“. Branchen und
Schweizer Unternehmensformen bieten „Sonstiges“ mit optionaler Freitexteingabe;
„Noch nicht festgelegt“ wird als null übertragen. Land und Währung verwenden
kuratierte Auswahloptionen aus `business/onboarding-options.ts` mit unveränderten
ISO-Werten (Defaults CH/CHF). Dies ersetzt die vorherige freie Code-Eingabe.

Validierungsfehler erscheinen erst nach Absenden direkt am Feld; das erste
fehlerhafte Feld erhält Fokus. Globale Banner bleiben Server-/Netzwerkfehlern
vorbehalten. Der vorhandene BusinessGate bietet Retry bei Listen-Ladefehlern;
das Erstellungsformular bietet diesen Button nicht mehr an. Backend-400-Antworten
enthalten keine Feldzuordnung und werden daher als knapper Formularhinweis
angezeigt. Provider, API, Auth und Routing bleiben unverändert.

16 Tests prüfen unter anderem die Auswahlwerte, Freitext-Zuordnung und die genaue
Fehlermeldung bei leerem Unternehmensnamen. Keine Browserprüfung durchgeführt.

## Operatives Kundenmodul

`/customers` ersetzt die bisherige Platzhalterseite. `CustomersPage` zeigt echte
Kunden des aktiven Unternehmens, Suche, Archivfilter, Tabelle, Erstellen/Bearbeiten
im nativen Dialog, Bestätigung vor Archivierung und Wiederherstellung. Es werden
keine Mock-Kontakte angezeigt. Pflichtfeld ist der Anzeigename; personenbezogene
und Firmenangaben sind gemäss Backend optional. Fehler erscheinen am Feld oder
als verständliche allgemeine Meldung. Nach bestätigten Schreibvorgängen gibt es
Erfolgsfeedback und einen Listen-Refresh.

Dateien: `src/api/customers.ts`, `src/customers/{store,validation,CustomerForm}`,
`src/pages/CustomersPage.tsx`, Routing und zugehörige Styles. Der bestehende
API-Client wurde ausschliesslich um sichere feldbezogene 400-Fehler erweitert;
Session/CSRF bleiben unverändert. Alle Mutationen nutzen denselben Client.

Der Workspace wird mit der aktiven Business-ID als React-Key gemountet. Ein
Business-Wechsel entfernt alte Liste, Dialog und lokale Eingaben sofort.
Unmount/Logout leert den Store und invalidiert ausstehende Requests. Auch
verspätete Mutationen dürfen keinen Refresh alter Daten auslösen. Der API-Client
verwirft zusätzlich Responses mit einer unerwarteten Business-ID. Dies ist eine
UI-Schutzschicht, kein Ersatz für Backend-Autorisierung.

Suche wird um 200ms verzögert; nur die neueste Antwort aktualisiert die Liste.
Archivfilter zeigt bei Aktivierung aktive und archivierte Kontakte. Bei einem
ungewissen Schreibresultat wird nicht automatisch erneut geschrieben; die Liste
soll vor einem weiteren Versuch neu geladen werden. Daten sind nicht persistent
im Browser gespeichert.

Verifikation: 24 Frontend-Tests erfolgreich, einschliesslich 8 neuer API-/Store-/
Validierungstests. ESLint, TypeScript (`tsc -b`) und Vite-Production-Build erfolgreich.
Keine zusätzlichen Abhängigkeiten. Keine Änderungen an Auth-/Onboarding-Logik.

Noch manuell im Browser prüfen (nicht als durchgeführt behauptet):
1. Mit realer Session Kunden beider Typen anlegen, bearbeiten und suchen.
2. Tastatur/Fokus im Formular und im Archivierungsdialog; Escape und Fehlermeldungen.
3. Archivieren, Filter aktivieren, wiederherstellen und Erfolgsfeedback prüfen.
4. Business-Wechsel/Logout bei langsamem Request und offenem Dialog.
5. Mobile Tabellen-Scrollbarkeit und Formular bei 375px/768px, Desktop bei 1280px.
6. Backend-Unterbruch, Session-Ablauf, CSRF-Ablehnung und anschliessender Retry.

Die automatisierten Frontend-Tests verwenden den bestehenden Node-Runner mit
Mock-Fetch und prüfen die echten API-/Store-Funktionen, keine DOM-Interaktionen.

## Mini-CRM-Erweiterung

`/customers/:customerId` zeigt Kundennummer, Status und die Bereiche Übersicht,
Kontaktpersonen, interne Notizen und serverseitige Aktivitäten. Die Kundenliste
verlinkt auf die Details und bietet Status-/Typfilter sowie Sortierung nach Name
oder Erstellungsdatum. Archivierung bleibt unabhängig vom Kundenstatus.

Firmenkontakte lassen sich anlegen, bearbeiten, entfernen und als Hauptkontakt
markieren. Das Entfernen verlangt eine Bestätigung. Notizen werden ausschliesslich
hinzugefügt; Ersteller, Zeitstempel und Aktivitäten stammen vom Backend.

Der typisierte Client `src/api/customers.ts` verwendet weiterhin den gemeinsamen
Session-/CSRF-Client. Zusätzlich zu den Kundenendpunkten nutzt er unter
`/api/businesses/{businessId}/customers/{customerId}`:

- `PATCH /status`
- `GET/POST /contacts` und `PUT/DELETE /contacts/{contactId}`
- `GET/POST /notes`
- `GET /activities`

Antworten werden auf Struktur und erwartete Business-/Kunden-Zuordnung geprüft.
`src/customers/detail-store.ts` verwaltet den Ladezustand getrennt von der Ansicht.
Business- oder Kundenwechsel erzeugen einen neuen Workspace; Unmount und Logout
verwerfen Daten und verspätete Antworten. Mutationen werden nie automatisch
wiederholt. Schlägt nur das Nachladen nach einem bestätigten Schreibvorgang fehl,
bleibt dessen Erfolg erkennbar und es wird ausschliesslich das Lesen wiederholt.
Die serverseitige Mandantenautorisierung bleibt massgeblich.

Wiederverwendbare Kontaktformulare, Status-Badges und deutsche Beschriftungen
liegen unter `src/customers`. Die Bereiche der Detailseite lassen sich später
um weitere Module ergänzen; Angebote, Rechnungen und Projekte sind nicht implementiert.
Tabs unterstützen Pfeiltasten, Home und End; Tabellen bieten echte Links für
Tastaturnavigation. Responsive Styles verwenden das bestehende Designsystem.

Verifikation der Erweiterung: alle 33 Frontend-Tests, ESLint, TypeScript (`tsc -b`)
und Production-Build erfolgreich. Neue Tests decken CRM-Verträge, CSRF-Mutationen,
Filter, Validierung, unerwartete Antworten, Lade-/Netzwerkfehler sowie Cleanup und
verspätete Antworten beim Business-Wechsel/Logout ab. Keine neuen Abhängigkeiten.
Browser-/DOM-Tests wurden nicht durchgeführt; Kontakt-Dialoge, Tabs, mobile
Darstellung und vollständige Abläufe mit realem Backend sind noch manuell zu prüfen.

## Leistungskatalog (`/services`)

Die neue Sidebar-Seite „Leistungen“ nutzt AppLayout, BusinessProvider und die
vorhandenen Controls, Tabelle, Dialoge und Design-Tokens. Sie bietet echte,
mandantengebundene Leistungen mit Suche nach Name/Beschreibung, Kategorie- und
Aktiv-/Archivfilter, Sortierung nach Name/Preis sowie Erstellen, Bearbeiten,
Archivieren mit Bestätigung und Wiederherstellen. Keine Produkt-Mockdaten.

- `src/api/services.ts`: typisierte DTOs, Runtime-Validierung und vorhandener
  Session-/CSRF-Client; alle sechs Backend-Endpunkte unter
  `/api/businesses/{businessId}/services`. Responses müssen zur erwarteten
  Business-/Leistungs-ID passen. Schreib-Payloads enthalten nur erlaubte Felder.
- `src/services/store.ts`: Liste, Filter und getrennte Lade-/Fehlerzustände;
  neueste Leseantwort gewinnt. Mutationen werden niemals automatisch wiederholt.
- `src/services/ServiceForm.tsx`: responsiver Dialoginhalt mit deutschen Labels,
  optionalen Feldern, feldbezogenen Fehlern und Fokus auf dem ersten Fehler.
- `src/services/ServiceResults.tsx`: unabhängig testbare Tabellen-, Leer-, Lade-
  und Fehlerdarstellung.
- `src/services/validation.ts`: Normalisierung, Backend-Limits, Einheitenlabels
  und exakte Preisformatierung ohne Gleitkommazahlen.
- `src/pages/ServicesPage.tsx`: businessgebundener Workspace und Dialogabläufe.

Preise und Steuersätze bleiben Dezimalstrings vom Eingabefeld bis zur API.
Kommas werden als Dezimaltrenner akzeptiert und in Punkte normalisiert. Preis:
0 bis 99999999999999999.99, maximal zwei Dezimalstellen. Steuersatz: optional,
0–100, maximal zwei Dezimalstellen. CHF ist die Standardwährung; Vorschläge
umfassen CHF/EUR/USD/GBP, weitere dreistellige Codes können eingegeben werden;
die ISO-Gültigkeit prüft das Backend. Die Tabelle zeigt Nettopreise. Der
Kategoriefilter sucht den vollständigen Kategorienamen ohne Beachtung der
Gross-/Kleinschreibung. Preissortierung beinhaltet keine Währungsumrechnung.

Business-Wechsel remountet die Seite und entfernt sofort Liste, Filter und
Dialogeingaben. Unmount/Logout leert den Store und invalidiert verspätete Lese-
und Schreibantworten. Keine Leistungsdaten werden im Browser persistiert.
Die Business-Auswahl ersetzt niemals die serverseitige Autorisierung.
Schlägt das Nachladen nach einem bestätigten Schreibvorgang fehl, zeigt die Seite
Erfolgsfeedback und separat den Ladefehler mit Retry; es wird nicht erneut geschrieben.

Verifikation: **48 Frontend-Tests bestanden**, darunter 15 neue API-/State-/
Validierungs- und React-Render-Tests. ESLint, TypeScript (`tsc -b`) und
Vite-Production-Build erfolgreich. Die Render-Tests verwenden Reacts vorhandenen
Server-Renderer ohne zusätzliche Abhängigkeiten. Sie prüfen echte Komponenten,
aber keine interaktiven DOM-/Browserabläufe.

Noch manuell im Browser mit laufendem Backend prüfen:

1. Als OWNER und MEMBER Leistung erstellen, bearbeiten, archivieren und
   wiederherstellen; Preise und Steuersatz nach Reload vergleichen.
2. Suche, vollständige Kategorie, Status und alle Sortierungen; leere Ergebnisse.
3. Dialog mit Tab/Shift+Tab/Escape, Labels, Fehlerfokus, Disabled-/Loading-Zustände
   und Screenreader; keine doppelten Aktionen durch mehrfaches Klicken.
4. Business-Wechsel und Logout bei langsamem Laden oder Speichern, danach
   Session-Wiederherstellung und direkter Aufruf von `/services`.
5. Netzwerkausfall, ungültige CSRF-Anfrage und abgelaufene Session; keine
   versehentlichen doppelten Schreibvorgänge beim erneuten Laden.
6. Smartphone (375px), Tablet (768px) und Desktop: Dialoghöhe, Tabelle mit
   horizontalem Scrollen, lange Namen und mobile Sidebar.

Es wurde keine Browserprüfung durchgeführt. Der Katalog ist nicht paginiert;
Angebote, Rechnungen, Preis-Snapshots und automatische Steuerberechnung sind
bewusst noch nicht implementiert. Authentifizierung, Onboarding und CRM bleiben
funktional unverändert. Keine neuen Abhängigkeiten, Commits oder Pushes.

### UI/UX-Überarbeitung des Leistungskatalogs

Der Leistungseditor verwendet einen maximal 600px breiten Dialog mit dem Titel
„Neue Leistung“ bzw. „Leistung bearbeiten“, kurzem Untertitel und beschriftetem
X-Button. Die Abschnitte „Allgemein“ und „Preisgestaltung“ ordnen die Felder;
zusammengehörige Preisfelder stehen maximal zweispaltig, auf Smartphones
untereinander. Die Beschreibung bleibt kompakt. Der Preis zeigt die jeweils
gewählte Währung als Präfix; Eingabewerte, Normalisierung und API bleiben unverändert.
Es werden keine Steuersätze vorbelegt.

Der Formularinhalt scrollt unabhängig von Dialogkopf und Footer. Der Footer
enthält „Abbrechen“ und den jeweiligen Speichern-Button. Der Fokus startet im
Namensfeld; das native Dialogelement begrenzt den Tastaturfokus und stellt ihn
beim Schliessen wieder her. X, Escape und Abbrechen schützen laufende Speicherung.
Bei geänderten Eingaben fragt eine native Bestätigung vor dem Verwerfen nach;
bei abgelehntem Verwerfen und fehlgeschlagenem Speichern bleiben die Eingaben erhalten.
Der gemeinsame Dialog erhielt nur optionale Eigenschaften; andere Module behalten
ihr bisheriges Layout und Verhalten.

Die Liste bietet kompaktere Filter, gruppierte Namen/Beschreibungen und Preis mit
Einheit in einer Spalte. `ServiceActions.tsx` verwendet ein natives Popover mit
beschriftetem Drei-Punkte-Button und normalen, per Tab erreichbaren Aktionen.
Das Popover liegt ausserhalb des Tabellen-Clippings; Escape und Klick ausserhalb
schliessen es. Es benötigt einen aktuellen Browser mit Popover-Unterstützung.

Ergänzte Render-Tests prüfen Abschnitte, Footer, Preisfeld-Zuordnung, Währungspräfix,
Popover-Trigger und deaktiviertes Dialog-X. Alle 51 Frontend-Tests bestanden.
Die Tests simulieren keine echten Browserinteraktionen. Manuell noch prüfen:

- Desktop und Smartphone: Kopf/Footer bei wenig Bildschirmhöhe sichtbar,
  Formular scrollbar, Tabelle horizontal erreichbar.
- Tab/Shift+Tab im Dialog und Aktions-Popover; Öffnen per Enter/Leertaste,
  Escape, Klick ausserhalb des Popovers und Fokus nach dem Schliessen.
- Änderungen eingeben und X/Escape/Abbrechen betätigen: Verwerfen ablehnen und
  bestätigen; während Speicherung dürfen diese Aktionen nicht schliessen.
- Währung wechseln, Dezimalkomma und grosse Preise speichern, Validierungsfehler
  und Serverfehler auslösen; Eingaben müssen erhalten bleiben.
- Archivieren und Wiederherstellen über das Popover, einschliesslich letzter
  Tabellenzeile und schmaler Viewports.

Keine Browserprüfung durchgeführt. Backend, API, Mandantentrennung und
Preisverarbeitung wurden für diese visuelle Überarbeitung nicht geändert.

## Angebotsverwaltung

Routen: `/quotes` (Liste), `/quotes/new`, `/quotes/:id` (Detail), `/quotes/:id/edit`.
Neuer Navigationseintrag „Angebote“. Dateien: `src/api/quotes.ts`, `src/quotes/*`,
`src/pages/Quote*.tsx`; `api/client.ts` erhielt `requestBlob` für den PDF-Download mit
derselben Session-/Fehlerbehandlung wie JSON-Requests. Keine neuen Abhängigkeiten.

- **Liste**: Suche (Nummer/Kunde), Statusfilter, Tabelle mit Nummer, Kunde, Datum
  (+ Gültigkeit), Betrag, Status (inkl. „Abgelaufen“), Aktionen Öffnen, Bearbeiten
  (nur Entwürfe), PDF. Lade-, Leer-, Filter- und Fehlerzustände; keine Mock-Daten.
- **Editor**: Kunde (nur aktive Kunden; ein archivierter Kunde eines Entwurfs bleibt
  wählbar), Datum/Gültigkeit (Default +30 Tage), Währung, Positionen aus dem Katalog
  (nur gleiche Währung) oder frei, Menge/Einheit/Preis/MWST bearbeiten, sortieren,
  entfernen, Einleitung, Abschluss, interne Notiz. Rechts (mobil darunter) eine
  Zusammenfassung. Desktop zweispaltig, ab 1100px einspaltig, Positionsfelder ab 760px
  zweispaltig. Warnung bei ungespeicherten Änderungen (Abbrechen/Tab schliessen).
- **Detail**: Dokumentansicht mit Serverbeträgen, interne Notiz separat, Aktionen
  je Status mit Bestätigungsdialog (Senden, Annehmen, Ablehnen, Löschen), PDF.

Beträge bleiben Dezimalstrings. `quotes/money.ts` berechnet eine **Vorschau** exakt mit
BigInt nach derselben Regel wie der Server (HALF_UP pro Zeile); gespeicherte Werte
kommen immer vom Server, der Client sendet nie Summen. Antworten werden streng
geprüft (Business-ID, ID, Geldformat). Stores verwerfen späte Antworten per Epoch;
Business-Wechsel leert die Liste sofort und führt von Detail/Editor zurück zu
`/quotes`; Logout entfernt alle Angebotsdaten (Unmount + `clear()`), späte PDF-Antworten
werden nicht mehr gespeichert.

Tests: `tests/quotes.test.mjs` (API, Whitelisting, CSRF, PDF, Berechnungen, Validierung,
Stores inkl. Business-Wechsel/Logout) und `tests/quotes-view.test.mjs` (Liste, Editor,
Kunden-/Leistungsauswahl, Statusaktionen, Detail). Gesamt **68 Tests bestanden**;
ESLint, TypeScript und Production-Build erfolgreich.

Zusätzlich wurde eine isolierte Vorschau (Wegwerf-DB) im Headless-Browser geprüft:
Liste, Detail, Editor (Desktop und 390px ohne horizontales Scrollen), Hinzufügen einer
Katalogleistung und Speichern – Vorschau- und Serversumme stimmten überein.

Manuell noch prüfen:

1. Tastatur: Editor komplett per Tab, Positionen sortieren/entfernen (Fokus), Dialoge mit Escape.
2. Screenreader: Positionsgruppen („Position n“), Fehlermeldungen, Summen-Live-Region.
3. PDF-Download in Safari, Firefox und Chrome; Dateiname, Öffnen, Druckbild.
4. Business-Wechsel und Logout während Laden, Speichern und PDF-Erstellung.
5. Zwei Tabs: Entwurf in Tab A bearbeiten, in Tab B senden, in A speichern → Hinweis 409.
6. Abgelaufene Session/CSRF-Fehler beim Speichern; Netzwerkausfall.
7. Tablet (768px) und sehr lange Kundennamen/Beschreibungen; viele Positionen.

## Unternehmenseinstellungen (`/settings`)

`SettingsPage` zeigt die Einstellungen des aktiven Unternehmens in vier Abschnitten:

- **Unternehmen:** Name, Rechtsform und Branche (mit Vorschlägen), Währung nur zur
  Ansicht.
- **Kontakt & Adresse:** Strasse, PLZ, Ort, Land, E-Mail, Telefon, Website.
- **Steuern & Bankverbindung:** UID/MWST-Nummer, IBAN, Bank.
- **Dokumentdesign:** Logo mit Vorschau, Akzentfarbe (Farbwähler, Hex-Feld,
  „Standardfarbe verwenden“), einzeilige Fusszeile mit Zeichenzähler und eine
  vereinfachte Briefkopf-Vorschau. Die Vorschau folgt denselben Regeln wie das PDF.

Weitere Details:

- **Bausteine:** `api/settings.ts`, `settings/validation.ts`, `settings/store.ts`,
  `settings/SettingsForm.tsx`, `pages/SettingsPage.tsx`. Wiederverwendet werden
  `FormField`, `Input`, `Select`, `Button`, `PageHeader`, `InformationPanel`,
  `LoadingState` und `ErrorState`.
- **Zustände:**
  - Laden und Ladefehler mit „Erneut laden“.
  - Eine Speicherleiste unten am Bildschirmrand zeigt ungespeicherte Änderungen,
    „Wird gespeichert…“, Erfolg und Fehler. Sie bietet „Verwerfen“; beim Verlassen
    der Seite mit ungespeicherten Änderungen fragt der Browser nach.
  - Ungültige Felder werden markiert und fokussiert. Server-Feldfehler werden auf
    die passende deutsche Meldung abgebildet.
- **Validierung:** spiegelt die Serverregeln (IBAN Mod 97, Schweizer
  UID-Prüfziffer, Kontrast der Akzentfarbe ≥ 4.5:1, Längen, keine
  Zeilenumbrüche). Der Server bleibt massgeblich.
- **Logo:**
  - Die Datei wird vor dem Upload geprüft (PNG/JPEG, höchstens 2 MB, nicht leer)
    und sofort hochgeladen.
  - Die Vorschau zeigt das vom Server normalisierte Logo
    (`/settings/logo?v=<logoId>`, gleiche Session).
  - Ersetzen und Entfernen laufen unabhängig vom übrigen Formular, ungespeicherte
    Eingaben bleiben erhalten.
- **Rollen:** MEMBER sieht dieselben Daten in einem deaktivierten `fieldset`,
  lesbar gestylt, mit einem Hinweis und ohne Speichern-, Upload- oder
  Entfernen-Buttons. Massgeblich ist `editable` aus der API, das für MEMBER nie
  `true` werden kann.
- **Unternehmenswechsel und Logout:**
  - Die Seite ist per `key={active.id}` an das Unternehmen gebunden.
  - `settingsStore.clear()` verwirft Daten und macht laufende Lese- und
    Schreibantworten wirkungslos. Antworten mit fremder Business-ID werden
    verworfen.
  - Nach dem Speichern aktualisiert `businessStore.replace()` den Namen in der
    Navigation, ohne den gesamten Business-Kontext neu zu laden.
- **Tests:** `tests/settings.test.mjs` (API, Parsing, Validierung, Store,
  Business-Store) und `tests/settings-view.test.mjs` (Owner- und Member-Ansicht,
  Logo-Zustände, Vorschau).

## Rechnungsverwaltung (`/invoices`)

### Seiten

- **`/invoices` – Übersicht:**
  - Titel „Rechnungen“, Button „Neue Rechnung“.
  - Suche nach Nummer oder Kunde, Statusfilter inklusive „Überfällig“.
  - Tabelle mit Nummer, Kunde, Datum, Fälligkeit (rot, wenn überfällig), Total, offenem
    Betrag, Status-Badge und Aktionen.
  - Leer-, Lade- und Fehlerzustände.
- **`/invoices/new` und `/invoices/:id/edit` – Editor:**
  - Kunde, Rechnungsdatum, Zahlungsfrist (Sofort bis 90 Tage, mit „Fällig am …“),
    Währung (Hinweis, dass der QR-Zahlteil nur CHF/EUR kann).
  - Positionen und Texte, Live-Summen.
  - Der Positionsbereich ist mit dem Angebotseditor geteilt
    (`documents/PositionsEditor.tsx`), ebenso die Positionsvalidierung
    (`validatePositions`).
- **`/invoices/:id` – Details:**
  - Aktionen nach Status: Bearbeiten, PDF, Rechnung ausstellen, Zahlung erfassen,
    Stornieren, Löschen.
  - Bezahlt und Offen in der Zusammenfassung, Zahlungshistorie mit „Stornieren“ je
    Zahlung (der Grund bleibt sichtbar), Link zum Angebot.
  - Hinweis zum QR-Zahlteil: vorhanden, als Entwurfsvorschau oder fehlend mit den
    Gründen des Servers und Link zu den Einstellungen.
  - Ausstellen und Löschen werden bestätigt. Zahlung, Rechnungsstorno und
    Zahlungsstorno nutzen kompakte Dialoge mit Formular.

### Angebote

Bei angenommenen Angeboten gibt es „Rechnung erstellen“. Danach öffnet sich die neue
Rechnung. Besteht schon eine, verlinkt die Aktion „Rechnung RE-…“ direkt. Ein Doppelklick
oder ein zweiter Tab endet dank `409 + invoiceId` auf derselben Rechnung
(`api/invoices.ts › invoiceFromQuote`).

### Technik

- **Zahlungsvalidierung:** Datum nicht in der Zukunft, Betrag > 0 mit höchstens den
  Nachkommastellen der Währung, nie über dem offenen Betrag (BigInt-Arithmetik). Für
  Stornos ist ein Grund Pflicht.
- **Stores** (`invoices/store.ts`) mit Epoch- und Lifecycle-Schutz.
- **Unternehmenswechsel und Logout:** Seiten sind per `key` an das Unternehmen
  gebunden, Detail- und Editorseiten springen beim Wechsel auf `/invoices`. Späte
  Antworten werden verworfen.
- **Tests:**
  - `tests/invoices.test.mjs`: API, Whitelist, CSRF, Umwandlung und 409, Parsing,
    Validierung, Rechte nach Status, Stores.
  - `tests/invoices-view.test.mjs`: Liste, Editor, Aktionen, Detail, QR-Hinweis,
    Zahlungsformular, Angebotsaktion.


## Ausgaben, Belege und echte Finanzübersicht

`/expenses`, `/expenses/new`, `/expenses/:expenseId` und `/expenses/settings`
ergänzen die Ausgaben-, Lieferanten-, Kategorien- und Belegverwaltung. Das
Dashboard nutzt nun echte CHF-Daten aus Rechnungen, Zahlungen und Ausgaben.
Architektur, API, Einschränkungen und die noch erforderlichen manuellen
Browserprüfungen stehen in [docs/expenses.md](../docs/expenses.md).

Der gemeinsame API-Client wurde um einen CSRF-geschützten Multipart-Upload mit
Fortschritt ergänzt. Bestehende JSON-/Blob-Anfragen bleiben erhalten. Die neuen
Frontend-Tests verwenden weiterhin den vorhandenen Node-Runner und Reacts
Server-Renderer; es wurden keine neuen Dependencies installiert.
