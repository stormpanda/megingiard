# UI Rewrite PRD — Megingiard

## Zusammenfassung

Vollständige Neugestaltung der App-Oberfläche: Die vier bisherigen Modi (Mirror, Touchpad, Keyboard, MacroPad) werden zu einem einzigen MacroPad-zentrierten Modus konsolidiert. Mirror, Touchpad und Keyboard werden als MacroPad-Button-Aktionen eingebettet. Per-Profil-Struktur mit mehreren Layouts pro Profil. Idle Pill öffnet ein Menü statt Carousel. Default-Profile werden mitgeliefert und sind wiederherstellbar.

---

## Architektur-Entscheidungen

| Entscheidung                          | Ergebnis                                                                                         |
| ------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Per-App-Profile                       | Nur manuelle Benennung durch den User, keine technische App-Zuordnung                            |
| AppMode Enum                          | Entfällt (nur noch MacroPad als einziger Modus)                                                  |
| Carousel Overlay                      | Entfällt komplett, ersetzt durch Idle-Pill-Menü                                                  |
| Overlay Timeout Setting               | Entfällt (kein Carousel mehr)                                                                    |
| Mirror Start                          | Explizit via Play-Button auf MacroPad                                                            |
| Ambient Display                       | Implizit aktiver Standard-Darstellungsmodus wenn Mirror läuft                                    |
| Lock Button                           | Fällt komplett weg                                                                               |
| Viewport Edit                         | Eigener temporärer Modus (Pan/Zoom auf Mirror-Bild), floating X-Button oben rechts zum Schließen |
| Fullscreen Maus                       | Nur relativer Maus-Modus (kein absoluter Touchpad)                                               |
| Fullscreen Keyboard Layout            | Per Button konfigurierbar (QWERTZ/QWERTY/AZERTY)                                                 |
| Rückkehr aus Fullscreen Maus/Keyboard | Idle-Pill-Geste, dazu "x close" Label oberhalb der Pill                                          |
| Ambient Peek                          | Bleibt als Screen-Mirroring-Button, schließt über Idle-Pill-Geste + "x close"                    |
| Ambient Settings (Dim/Vignette)       | Pro Layout gespeichert, eigener Modus via Idle-Pill-Menü-Button                                  |
| Mirror Viewport                       | Pro Layout gespeichert                                                                           |
| Makro-Ordner                          | Flache Liste pro Profil, keine Ordner mehr                                                       |
| Makros                                | Per-Profil, können nur kopiert (nicht verschoben) werden                                         |
| Default-Profile                       | Erstmal nur ein leeres "Default", später ergänzbar, wiederherstellbar in Global Settings         |
| Config Export/Import                  | Pro-Profil (einzelnes Profil exportieren), nur Layouts + Buttons + Makros, kein Theme            |
| Globale Settings                      | Log Level, Sprache, Overlay Position bleiben global                                              |
| Global Settings Zugang                | Im Idle-Pill-Menü                                                                                |
| Theme/Accent                          | Global (Override pro Profil für später vorgesehen)                                               |
| Profil-Editor                         | Liste von Layouts (an/aus, umsortieren) + Theme/Accent (Platzhalter für später)                  |
| Layout an/aus                         | Deaktivierte Layouts werden geskippt bei "nächstes/vorheriges Layout" Buttons                    |

---

## Datenmodell

### PadProfile (erweitert)

```
PadProfile
  id: String (UUID)
  name: String
  layouts: List<PadLayout>             // NEU: mehrere Layouts pro Profil
  activeLayoutId: String               // NEU: welches Layout gerade angezeigt wird
  macros: List<Macro>                  // NEU: Makros gehören zum Profil (flache Liste)
  enableKeyboard: Boolean              // auto-computed from all layouts
  enableGamepad: Boolean
  enableMouse: Boolean
  isDefault: Boolean = false           // NEU: Default-Profile (wiederherstellbar)
```

### PadLayout (NEU)

```
PadLayout
  id: String (UUID)
  name: String
  enabled: Boolean = true              // an/aus (wird geskippt bei Navigation)
  buttons: List<PadButton>            // Button-Definitionen (wie bisher PadProfile.buttons)

  // Mirror/Ambient Settings pro Layout:
  ambientDim: Float = 0f
  ambientVignetteEnabled: Boolean = false
  ambientVignetteShape: VignetteShape = RADIAL
  ambientVignetteVisibleArea: Float = 0.7f
  ambientVignetteTransition: Float = 0.5f
  ambientVignetteOpacity: Float = 0.6f
  ambientVignetteColor: Int = 0xFF000000
  mirrorSavedScale: Float = 1f
  mirrorSavedOffsetX: Float = 0f
  mirrorSavedOffsetY: Float = 0f
```

### PadButton

Bisherige Felder bleiben vollständig erhalten. Neue Action-Typen ergänzt.

### Neue PadAction-Typen

**Screen Mirroring Kategorie:**

- `MirrorPlayStop` — Toggle: Mirror starten/stoppen
- `MirrorFreeze` — Toggle: Frame einfrieren/fortsetzen
- `MirrorViewportEdit` — Aktiviert Viewport-Edit-Modus (Pan/Zoom auf Mirror-Bild)
- `MirrorTouchProjection` — Toggle: Touch Projection an/aus
- `AmbientPeek` — (existiert bereits) Alle Buttons ausblenden für klare Mirror-Sicht

**Profile/Navigation Kategorie:**

- `LayoutNext` — Nächstes aktives Layout im Profil
- `LayoutPrevious` — Vorheriges aktives Layout im Profil
- `ProfileSwitcher` — Öffnet Profil-Auswahl-Dialog

**Spezial Kategorie:**

- `FullScreenMouse(sensitivity: Float)` — Relativer Maus-Modus (Fullscreen)
- `FullScreenKeyboard(layout: KbLayout = QWERTZ)` — Volle Tastatur als Fullscreen-Overlay

### Makro-Datenmodell

```
Macro
  id: String (UUID)
  name: String
  steps: List<MacroStep>
  // folderId ENTFÄLLT (keine Ordner mehr)
```

Makros gehören zum Profil (nicht global). Kein Verschieben zwischen Profilen, nur Kopieren.

---

## UI-Komponenten

### 1. Idle Pill (überarbeitet)

- **Idle-Zustand:** Wie bisher — kleiner horizontaler Strich am Bildschirmrand
- **Geste:** Wisch-Geste öffnet das **Pill-Menü** (statt Carousel)
- **Kontext-abhängig:** In Fullscreen-Modi (Maus, Keyboard, Peek) zeigt die Pill oberhalb ein "x close" Icon+Label

### 2. Pill-Menü (NEU — ersetzt Carousel)

Ein Dialog/Overlay mit folgenden Elementen:

- **Profil-Sektion:** Aktuelles Profil angezeigt, Dropdown/Liste zum Wechseln, "Neues Profil" erstellen
- **Layout-Sektion:** Aktuelles Layout angezeigt, Liste/Dropdown zum Wechseln, "Neues Layout" erstellen (mit Template-Auswahl aus allen Profilen)
- **Aktionen:**
  - "Edit Layout" → öffnet MacroPad-Editor
  - "Ambient Settings" → öffnet Ambient-Settings-Modus
  - "Global Settings" → öffnet GlobalSettingsScreen

### 3. MacroPad-Screen (überarbeitet — Hauptscreen)

- Immer die Haupt-UI der App (kein Crossfade zwischen Modi mehr)
- Zeigt das aktive Layout des aktiven Profils
- Bei aktivem Mirror: Ambient Display (Mirror-Hintergrund + Buttons darüber)
- Bei inaktivem Mirror: Normaler dunkler Hintergrund + Buttons

### 4. MacroPad-Editor (erweitert)

- **Profil-Editor-Bereich:**
  - Liste der Layouts (an/aus Toggle, Drag-Reorder)
  - Neues Layout erstellen (Template-Auswahl aus allen Profilen)
  - Layout löschen
  - Profil umbenennen/löschen
  - (Platzhalter: Theme/Accent Override pro Profil für später)
- **Button hinzufügen** — erweiterte Action-Kategorien:
  - Keyboard Key (wie bisher)
  - Gamepad Button (wie bisher)
  - Mouse Button (wie bisher)
  - Scroll Wheel (wie bisher)
  - Trackpoint Move (wie bisher)
  - Macro (wie bisher, aber nur Profil-eigene Makros wählbar)
  - **Screen Mirroring** (NEU: Play/Stop, Freeze, Viewport Edit, Touch Projection, Ambient Peek)
  - **Profile/Navigation** (NEU: Layout Next/Previous, Profile Switcher)
  - **Spezial** (NEU: Full Screen Maus, Full Screen Tastatur)

### 5. Fullscreen-Maus (NEU, basiert auf TouchpadScreen)

- Wird modal über dem MacroPad angezeigt (Overlay)
- Nur relativer Maus-Modus
- "x close" über Idle Pill zum Schließen
- Sensitivity wird am Button konfiguriert

### 6. Fullscreen-Keyboard (überarbeitet aus KeyboardScreen)

- Wird modal über dem MacroPad angezeigt (Overlay)
- Layout (QWERTZ/QWERTY/AZERTY) am Button konfiguriert
- Alle bisherigen Keyboard-Features bleiben erhalten:
  - Trackpoint (analog pointer cursor)
  - Mouse Button Overlay (LMB/MMB/RMB/M4/M5/Scroll)
  - Modifier State Machine (INACTIVE/STICKY/HELD)
  - Key Repeat (konfigurierbar)
- "x close" über Idle Pill zum Schließen

### 7. Viewport-Edit-Modus (NEU)

- Mirror-Bild füllt gesamten Screen
- Pan/Zoom Gesten aktiv (Pinch-to-Zoom, Drag-to-Pan)
- Floating X-Button oben rechts zum Schließen
- Viewport (scale, offsetX, offsetY) wird pro Layout gespeichert

### 8. Ambient-Settings-Modus (überarbeitet)

- MacroPad + Mirror als Live-Vorschau im Hintergrund
- Overlay-Panel mit Dim/Vignette-Einstellungen
- Aufruf via Button im Idle-Pill-Menü
- Settings werden pro Layout gespeichert

### 9. Profil-Editor (NEU, innerhalb MacroPad-Editor)

- Layout-Liste:
  - Toggle (an/aus) pro Layout
  - Drag-Reorder
  - Neues Layout erstellen (Template-Auswahl aus allen Profilen)
  - Layout löschen
- Profil umbenennen
- Profil löschen (mit Bestätigung)
- (Platzhalter: Theme/Accent Override — für später)

### 10. Profile-Switcher-Dialog (NEU)

- Overlay zur Profil-Auswahl
- Ausgelöst durch MacroPad-Button mit `ProfileSwitcher` Action
- Zeigt alle verfügbaren Profile
- Wechselt aktives Profil bei Auswahl

### 11. GlobalSettingsScreen (vereinfacht)

- **Entfällt:** Overlay Timeout, Tool-Reihenfolge, Tool-Aktivierung
- **Bleibt:** Log Level, Sprache, Overlay Position (top/bottom)
- **Bleibt:** Theme, Accent Color
- **Neu:** Default-Profile wiederherstellen
- **Überarbeitet:** Config Export/Import → Pro-Profil-Export

---

## User Flow

### App-Start

1. Beim Start öffnet sich das zuletzt geöffnete Profil mit dem zuletzt geöffneten Layout
2. Gibt es noch kein eigenes Profil: "Default" Profil mit leerem MacroPad
3. Alles muss frei konfiguriert werden

### Hauptansicht

- MacroPad zeigt das aktive Layout des aktiven Profils
- Wenn Mirror via Play-Button gestartet: Ambient Display (Mirror + Buttons)
- Wenn kein Mirror: Dunkler Hintergrund + Buttons

### Idle Pill Interaktion

1. Wisch-Geste auf der Idle Pill → Pill-Menü öffnet sich
2. Im Menü: Profil wechseln, Layout wechseln, Neues erstellen, Editieren, Ambient Settings, Global Settings
3. In Fullscreen-Modi: Wisch-Geste zeigt "x close" zum Schließen

### Screen Mirroring

1. User platziert Mirror-Buttons auf MacroPad (Play/Stop, Freeze, Viewport Edit, Touch Projection)
2. Play-Button startet MediaProjection → Ambient Display wird automatisch aktiv
3. Viewport-Edit-Button → temporärer Pan/Zoom-Modus auf Mirror-Bild
4. Touch-Projection-Button → Touch-Events werden an Primary Display weitergeleitet
5. Freeze-Button → aktueller Frame wird eingefroren

### Fullscreen-Overlays

1. Fullscreen-Maus-Button gedrückt → relativer Maus-Modus als Overlay
2. Fullscreen-Keyboard-Button gedrückt → volle Tastatur als Overlay
3. Rückkehr jeweils via Idle-Pill-Geste + "x close"

---

## Entfallende Komponenten

| Komponente                                  | Grund                                 |
| ------------------------------------------- | ------------------------------------- |
| `AppMode` Enum (MIRROR, TOUCHPAD, KEYBOARD) | Nur noch MacroPad als Hauptmodus      |
| `CarouselOverlay`                           | Ersetzt durch Pill-Menü               |
| `MirrorScreen.kt` (Standalone)              | Mirror eingebettet in Ambient Display |
| `MirrorControlPanel.kt`                     | Ersetzt durch MacroPad-Buttons        |
| `TouchpadScreen.kt`                         | Ersetzt durch FullscreenMouse         |
| `ToolSettingsPanel.kt`                      | Alles per Button/Layout/Profil        |
| `MirrorToolSettings.kt`                     | Mirror-Settings per Layout            |
| `TouchpadToolSettings.kt`                   | Settings am Button                    |
| `KeyboardToolSettings.kt`                   | Settings am Button                    |
| `MacroPadToolSettings.kt`                   | Ambient Settings → eigener Modus      |
| Makro-Ordner System (`MacroFolder`)         | Flache Liste pro Profil               |
| `Crossfade` Mode-Switching                  | Nur noch ein Modus                    |
| Absoluter Touchpad-Modus                    | Nur noch relative Maus                |

---

## Beibehaltene Funktionalität

Alle bisherigen Kernfunktionen bleiben erhalten und werden in die neue Struktur überführt:

- **Screen Mirroring:** Live-Mirroring, Pan/Zoom, Freeze Frame, Touch Projection — über MacroPad-Buttons gesteuert
- **Virtuelle Tastatur:** QWERTZ/QWERTY/AZERTY, Modifier State Machine, Trackpoint, Mouse Overlay, Key Repeat — als Fullscreen-Overlay
- **Maus-Steuerung:** Relativer Maus-Modus — als Fullscreen-Overlay
- **MacroPad:** Alle Action-Typen (Keyboard, Gamepad, Mouse, ScrollWheel, Trackpoint, Macro, Peek)
- **Ambient Display:** Mirror-Hintergrund + Buttons, Dim/Vignette-Konfiguration
- **Config Export/Import:** Überarbeitet für Pro-Profil-Export
- **Theming:** Dark/Light/Cyberpunk Themes, Custom Accent Color
- **Makro-System:** Timeline-Editor, alle Step-Typen (GamepadButtonTap, JoystickMove, DPadTap)

---

## Implementierungsphasen

### Phase 0: Datenmodell _(blockiert alle anderen Phasen)_

1. `PadLayout` als eigene `@Serializable` Klasse definieren
2. `PadProfile` um `layouts`, `activeLayoutId`, `macros` erweitern
3. Neue `PadAction`-Typen hinzufügen
4. `Macro.folderId` entfernen, Makros in Profil integrieren
5. DataStore-Migrationscode: alte Profile → neues Schema
6. `MacroPadState` anpassen: Layout-CRUD, aktives Layout, Makro-CRUD pro Profil

### Phase 1: Idle Pill & Navigation _(abhängig von Phase 0)_

7. `CarouselOverlay` → `IdlePill` + `PillMenu` umbauen
8. `MainAppScreen` Crossfade entfernen, nur MacroPad als Content
9. `AppStateManager` vereinfachen: modale Overlay-States

### Phase 2: MacroPad als Hauptscreen _(parallel mit Phase 1)_

10. MacroPadScreen als Hauptscreen (zeigt aktives Layout)
11. Layout-Navigation (LayoutNext/Previous)
12. ProfileSwitcher-Dialog

### Phase 3: Neue Button-Aktionen _(abhängig von Phase 0)_

13. Screen Mirroring Buttons
14. Profile/Navigation Buttons
15. Spezial Buttons
16. `PadActionPicker` erweitern
17. `MacroPadActionDispatch` erweitern

### Phase 4: Fullscreen-Overlays _(abhängig von Phase 1 + 3)_

18. `FullscreenMouseOverlay`
19. Fullscreen-Keyboard als Overlay
20. Viewport-Edit-Overlay

### Phase 5: Ambient Display & Mirror _(abhängig von Phase 2 + 3)_

21. `AmbientMacroPadOverlay` → Settings aus Layout lesen
22. Mirror Play/Stop via Button
23. Touch Projection, Freeze, Peek

### Phase 6: Editor & Profil-Management _(abhängig von Phase 0 + 5)_

24. Profil-Editor: Layout-Liste (an/aus, reorder, CRUD)
25. Layout-Template-Auswahl
26. Makro-Editor ohne Ordner, pro Profil
27. Ambient-Settings-Modus

### Phase 7: Settings & Export/Import _(abhängig von Phase 0 + 6)_

28. `GlobalSettingsScreen` vereinfachen + Default-Profile wiederherstellen
29. `SettingsManager` aufräumen
30. Config Export/Import: Schema v3, Pro-Profil, v2→v3 Migration

### Phase 8: Aufräumen _(nach allen Phasen)_

31. Entfallende Dateien löschen
32. strings.xml aufräumen
33. Dokumentation aktualisieren (FEATURE.md, PRD.md, ARCHITECTURE.md, AGENTS.md)

---

## Offene Punkte (auf "später" verschoben)

- Theme/Accent Override pro Profil
- Weitere Default-Profile (Gaming, Productivity etc.)
- Absoluter Touch-Modus (bisheriger Touchpad) entfällt zugunsten relativer Maus
