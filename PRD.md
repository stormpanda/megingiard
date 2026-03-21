# Product Requirements Document (PRD): Megingiard

## 1. Product Overview
**Name:** Megingiard
**Plattform:** Android
**Gerät:** AYN Thor (Gaming Handheld mit zwei Bildschirmen)
**Zweck:** Eine Companion-App für den zweiten Bildschirm ("Werkzeuggürtel"), die essenzielle Komfortfunktionen während der Nutzung des Hauptbildschirms bereitstellt.

## 2. Core Features (MVP)
Die App umfasst zwei Hauptwerkzeuge, die den gesamten zweiten Bildschirm ausnutzen.

### 2.1. Screen Mirroring (Hauptbildschirm spiegeln)
* **Live-Synchronisation:** Permanente, in Echtzeit synchronisierte Spiegelung des gesamten Hauptbildschirms.
  * **Qualitätsanspruch:** Die Bildqualität muss perfekt sein, auch wenn auf dem Hauptbildschirm ressourcenintensive Spiele laufen (hohe Performance und geringer Overhead).
  * **Manipulation:** Der User kann den gespiegelten Bildschirmausschnitt auf dem zweiten Bildschirm frei per Touch (Pinch-to-Zoom, Panning) verändern, um bestimmte Details im Spiel heranzuholen.
* **Momentaufnahme ("Freeze"):**
  * Erstellen eines hochauflösenden Screenshots des aktuellen Bildschirms um das Bild "einzufrieren".
  * Dieser Snapshot dient als Referenz (z.B. für Rätsel im Spiel) und kann in der App ebenfalls gezoomt und verschoben werden.
  * **Bedienung:** Ein kurzes Antippen (Tap) auf den zweiten Bildschirm öffnet ein Overlay mit semi-transparenten Kontroll-Buttons. Einer davon ist der Screenshot / Freeze-Button. Das Overlay verschwindet nach kurzer Inaktivität automatisch.

### 2.2. Medienkontrolle
* **Systemweite Steuerung:** Integration über die standardmäßigen Android-`MediaSession`-APIs. Dadurch werden alle üblichen Apps (Spotify, YouTube im Hintergrund, Podcast Player, etc.) automatisch unterstützt.
* **Funktionen:**
  * Anzeige des aktuellen Titels.
  * Play / Pause.
  * Medien überspringen (Vor/Zurück).
  * Mute / Unmute und Lautstärkeregelung.
  * Interaktiver Fortschrittsbalken (Scrubbing).

## 3. User Interface & User Experience (UX)
* **Startverhalten:** Beim Öffnen der App startet diese sofort in der "Mirror"-Funktion, um dem Nutzer einen sofortigen Mehrwert ohne Konfiguration zu bieten.
* **Navigation:** Der Wechsel zwischen den verfügbaren Tools (Mirror und Medienkontrolle) erfolgt per **Zwei-Finger-Swipe-Geste**.
* **Layout & Design:**
  * Die App läuft im randlosen "Immersive" Fullscreen-Modus (ohne Statusleiste oder störende Navigationsbuttons).
  * Die Oberflächen sind im Landscape-Format strikt für die Seitenverhältnisse **4:3** und **16:9** optimiert.
  * **Ästhetik:** Das Design (insbesondere der Medienkontrolle) bleibt zunächst bewusst dunkel und minimalistisch ("Dark Mode"), um während des Spielens auf dem Hauptbildschirm nicht durch grelle Farben abzulenken.
  * **Controls Overlay:** Steuerungs-Elemente zeigen sich ausschließlich bei einem Tap auf den Content und verblassen nach einigen Sekunden (Auto-Hide).
* **App-Lifecycle:** Da es sich um ein Android-Gerät handelt, vertraut Megingiard dem Systemstandard: Die App wird über den regulären Android Multi-Tasking-View (Recent Apps) beendet; eigene "Schließen"-Buttons im Interface existieren nicht.

## 4. Technische Architektur-Meilensteine (MVP)
1. **Projekt-Setup:** Initialisierung des Android-Projekts (z. B. nativ in Kotlin mit Jetpack Compose) mit spezifischer Ausrichtung auf Immersive Fullscreen.
2. **Screen Capture Service:** Implementierung der Android `MediaProjection` API für performantes Mirroring bei sehr hoher Bildqualität.
3. **Touch & Gesten-Handling:** Erkennung und Verarbeitung von Pinch-to-Zoom, freiem Panning im gespiegelten Content sowie der Zwei-Finger-Swipe-Geste für den globalen App-Status/Tool-Wechsel.
4. **Overlay UI Zustand:** Entwicklung des Timer-gesteuerten "Fade-In/Fade-Out" Modells für On-Screen-Controls beim Mirror-Tool.
5. **Media Controller Integration:** Aufbau eines Broadcast-Receivers / MediaControllers zur Interaktion mit dem System `MediaSessionManager`.
