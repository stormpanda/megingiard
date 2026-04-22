# AGENTS.md – AI Agent Coding Guidelines for Megingiard

> This file instructs AI coding agents (GitHub Copilot, Cursor, Cline, etc.) on the
> conventions, patterns, and constraints that govern this project. Treat every rule
> as mandatory unless the human operator explicitly overrides it.

---

## 1 Project Identity

| Key               | Value                                                     |
| ----------------- | --------------------------------------------------------- |
| **Package**       | `com.stormpanda.megingiard`                               |
| **Language**      | Kotlin 2.0+ (no Java files)                               |
| **UI Framework**  | Jetpack Compose (Material 3, BOM-managed)                 |
| **Min SDK**       | 33 — **must not be raised**                               |
| **Target SDK**    | 35                                                        |
| **Build System**  | Gradle (Kotlin DSL), version catalog `libs.versions.toml` |
| **Device Target** | AYN Thor dual-screen Android handheld                     |

> [!CAUTION]
> **Secondary Display Only — Non-Negotiable Constraint**
>
> Megingiard **always** runs on the **secondary display**. Every `Activity`, `Composable`,
> `Presentation`, `Dialog`, and overlay MUST be anchored to or rendered on the secondary
> display (`displayId != Display.DEFAULT_DISPLAY`). This applies to all features without
> exception.
>
> The **only permitted exception** is when the user launches or moves the app to the primary
> display (this cannot be prevented by the app). `MainActivity` detects
> `displayId == Display.DEFAULT_DISPLAY` and shows a full-screen blocking overlay that
> instructs the user to move the app to the secondary display. All input events are consumed
> by this overlay — no functional UI is accessible. All capture auto-start paths are gated on
> `AppStateManager.isOnValidScreen`.
>
> **No agent may add any feature, dialog, service UI, or overlay that renders on
> `DEFAULT_DISPLAY`.** The `CaptureRequestActivity` (MediaProjection consent dialog) is the
> sole intentional exception and explicitly sets
> `ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)` — do not replicate this
> pattern elsewhere.

---

## 2 Documentation Map

| Document                             | Purpose                                                                               |
| ------------------------------------ | ------------------------------------------------------------------------------------- |
| `README.md`                          | Project overview, feature list, quick links                                           |
| `PRD.md`                             | Product requirements (authoritative)                                                  |
| `docs/REQUIREMENTS.md`               | Requirements overview & non-functional requirements                                   |
| `docs/ARCHITECTURE.md`               | System architecture overview & key design decisions                                   |
| `docs/features/mirror/FEATURE.md`    | Screen Mirror — functional requirements & technical implementation                    |
| `docs/features/macropad/FEATURE.md`  | MacroPad — profiles, layouts, actions, ambient display, macros                        |
| `docs/features/keyboard/FEATURE.md`  | Fullscreen Keyboard overlay — functional requirements & technical implementation      |
| `docs/features/touchpad/FEATURE.md`  | Fullscreen Mouse overlay — functional requirements & technical implementation         |
| `docs/features/config/FEATURE.md`    | Config Export / Import — schema, SAF file picker, migration                           |
| `docs/features/theming/FEATURE.md`   | Design System — AppColors, Typography, AppDimens, ColorScheme bridge                  |
| `docs/features/FEATURE_TEMPLATE.md`  | Template for new feature documentation                                                |

> **Convention:** Every feature has its own subfolder under `docs/features/<feature>/` containing a single `FEATURE.md`. This file is the **authoritative source of truth** for that feature's requirements and technical implementation. When adding a new feature, create a new subfolder and `FEATURE.md`.
> | `docs/BUILD_NATIVE.md` | How to rebuild the native touch-injector binary |
> | `AGENTS.md` _(this file)_ | AI agent coding conventions & constraints |

---

## 3 Package Structure

The project is split into three Gradle modules:

- **`:app`** — Android UI layer (Activities, Composables, ViewModels, Service)
- **`:domain`** — Business logic & state management (no Android UI dependencies)
- **`:core`** — Pure data models, serializable types, constants (no Android dependencies)

### app module (`app/src/main/java/com/stormpanda/megingiard`)

\`\`\`
com.stormpanda.megingiard [app module]
├── AppStateManager.kt # Stub — migrated to :domain module
├── CaptureRequestActivity.kt # MediaProjection consent dialog (transparent Activity)
├── MainActivity.kt # Entry point: permission checks, display detection, file pickers
├── MainAppScreen.kt # Top-level Composable (MacroPad content + fullscreen overlays)
├── mirror/
│ ├── MirrorPresentation.kt # android.app.Presentation on secondary display
│ ├── MirrorPresentationLifecycleOwner.kt # Synthetic LifecycleOwner for Compose-in-Presentation
│ └── ScreenCaptureService.kt # Foreground Service managing MediaProjection + VirtualDisplay
├── keyboard/
│ ├── KeyboardScreen.kt # Full keyboard Composable (QWERTZ/QWERTY/AZERTY + trackpoint)
│ ├── KeyboardKeyCap.kt # KeyCap Composable, key bounds tracking
│ └── KeyboardMouseOverlay.kt # Mouse button overlay (LMB/MMB/RMB/scroll)
├── settings/
│ ├── ColorWheelPicker.kt # HSV color picker (hue wheel + brightness slider)
│ ├── GlobalSettingsScreen.kt # Full-screen settings Composable (scaffold + state only)
│ ├── GlobalSettingsComponents.kt # Extracted setting row Composables for GlobalSettingsScreen
│ └── ToolSettingsComponents.kt # Reusable row Composables (SliderSettingRow, dropdowns, etc.)
├── macropad/
│ ├── MacroPadScreen.kt # Main pad Composable (button grid, multi-touch, injector lifecycle)
│ ├── MacroPadButton.kt # PadButton, ScrollWheelFace, AmbientPeekFace Composables
│ ├── MacroPadEditor.kt # Full-screen layout editor (profile/layout CRUD, drag-repositioning)
│ ├── PadCanvas.kt # Draggable editor canvas (DraggableButton, PadCanvas)
│ ├── PadButtonEditDialog.kt # Button create/edit dialog
│ ├── PadActionPicker.kt # Action-selection UI with category tabs
│ ├── AmbientMacroPadOverlay.kt # Ambient overlay (dim + vignette + buttons on secondary display)
│ ├── AmbientSettingsOverlay.kt # Per-layout ambient settings editor
│ ├── MacroListEditor.kt # In-editor macro list (add/edit/delete/reorder)
│ ├── MacroStepEditDialog.kt # Macro step timing/action editor dialog
│ ├── MacroTimelineEditor.kt # Visual macro timeline editor
│ ├── IconPickerDialog.kt # Material Symbols icon grid picker
│ ├── MaterialSymbols.kt # Material Symbols typeface integration
│ ├── MaterialIconRegistry.kt # Icon name → Material Symbols lookup
│ └── RoundedIconNames.kt # Rounded variant icon name list
├── touchpad/
│ └── FullscreenMouseOverlay.kt # Fullscreen relative-mouse overlay (triggered by FullScreenMouse action)
├── viewmodel/
│ ├── MainViewModel.kt # Facade ViewModel: SwipeGestureProcessor, config import helpers
│ ├── MirrorViewModel.kt # Mirror state exposure, viewport/touch-injector lifecycle
│ ├── MacroPadViewModel.kt # Multi-injector lifecycle, MacroPadHitTestEngine factory
│ └── KeyboardViewModel.kt # Key/mouse injector lifecycle, KeyRepeatController
└── ui/
├── AppTheme.kt # AppColors token system, palette factory (Dark/Light/Cyberpunk)
├── IdlePill.kt # Always-visible edge pill (swipe affordance + close label)
└── PillMenu.kt # Pill overlay (profile/layout card + mirror controls card)
\`\`\`

### domain module (`domain/src/main/java/com/stormpanda/megingiard`)

\`\`\`
com.stormpanda.megingiard [domain module]
├── AppLog.kt # Unified logging facade (level-gated, tag-prefixed)
├── AppStateManager.kt # Global app-level state (lifecycle + modal overlay flags)
├── SwipeGestureProcessor.kt # Edge-swipe gesture detection (shared by pill + mirror)
├── mirror/
│ ├── ScreenCaptureManager.kt # Mirror state flows (scale, offset, freeze, lock, projection, bitmap)
│ ├── MirrorViewportController.kt # Zoom/pan business logic + debounced DataStore persistence
│ ├── TouchProjectionController.kt # Gesture state machine for touch-projection (DOWN→MOVE\*→UP)
│ └── DisplayDetector.kt # Multi-display detection via DisplayManager
├── input/
│ ├── TouchInjector.kt # Normalised → physical coordinate transform facade
│ ├── ShellInputInjector.kt # Native touchinjector_arm64 lifecycle + MOVE coalescing
│ ├── MouseInjector.kt # Public facade over ShellMouseInjector
│ └── ShellMouseInjector.kt # Native mouseinjector_arm64 lifecycle + MOVE coalescing
├── keyboard/
│ ├── KeyInjector.kt # Key injection facade (delegates to ShellKeyInjector)
│ ├── ShellKeyInjector.kt # Native keyinjector_arm64 lifecycle (1–255 keycodes only)
│ ├── KeyboardState.kt # Modifier state machine (INACTIVE/STICKY/HELD)
│ └── KeyRepeatController.kt # Key repeat + trackpoint tracking per keyboard session
├── settings/
│ └── SettingsManager.kt # App-wide settings persistence via DataStore
├── config/
│ └── ConfigManager.kt # Export/import coordinator: SAF I/O, UUID remap, checksum
├── touchpad/
│ └── TouchpadGestureProcessor.kt # Mouse/touch mode gesture processing (tap-to-click, two-finger-tap)
└── macropad/
├── MacroPadState.kt # Singleton: profiles + layouts + macros CRUD, persistence
├── MacroPadActionDispatch.kt # injectActionDown/Up — routes PadAction types to injectors
├── MacroPadHitTestEngine.kt # Button lookup, per-pointer tracking, scroll/trackpoint dispatch
├── MacroExecutor.kt # Timed macro playback (compiles steps → flat event list)
├── GamepadInjector.kt # Public facade over ShellGamepadInjector
└── ShellGamepadInjector.kt # Native gamepadinjector_arm64 lifecycle + writer thread
\`\`\`

### core module (`core/src/main/kotlin/com/stormpanda/megingiard`)

\`\`\`
com.stormpanda.megingiard [core module]
├── mirror/
│ ├── MirrorCoordinateTransform.kt # Pure projectCoordinates() geometry helper
│ └── ViewportMath.kt # fitAspectRatio() helper
├── input/
│ └── TouchAction.kt # DOWN/MOVE/UP enum
├── keyboard/
│ ├── KeyboardLayout.kt # KeyDef, KbLayout enum, KbMouseBtnPos, layout factories
│ ├── KeyAction.kt # DOWN/UP enum
│ └── LinuxKeycodes.kt # Linux input-event-codes constants (KEY*\*)
├── settings/
│ └── ThemeMode.kt # DARK/LIGHT/CYBERPUNK enum
├── config/
│ └── ConfigSchema.kt # MegingiardExport, ExportMetadata, SCHEMA_VERSION (v3)
└── macropad/
├── MacroPadLayout.kt # PadProfile, PadLayout, PadButton, PadAction (sealed)
├── MacroData.kt # Macro, MacroStep (sealed), JoystickStick
└── GamepadKeycodes.kt # Linux BTN*\* constants + preset list
\`\`\`

**Rule:** New feature modules get their own sub-package. Shared UI components belong in `ui/`. Business logic with no Android UI dependency belongs in `:domain`. Pure data types and constants belong in `:core`.

---

## 4 State Management

### 4.1 Singleton State Holders

State is managed by **`object` singletons** (`AppStateManager`, `ScreenCaptureManager`) that expose **read-only `StateFlow`** and keep all `MutableStateFlow` backing fields **`private`**.

\`\`\`kotlin
// ✅ Correct pattern
object FooManager {
private val \_bar = MutableStateFlow(0)
val bar: StateFlow<Int> = \_bar.asStateFlow()

    fun setBar(value: Int) { _bar.value = value }

}

// ❌ Never expose MutableStateFlow
object FooManager {
val bar = MutableStateFlow(0) // WRONG
}
\`\`\`

### 4.2 Visibility Rules

| Layer                           | Access to `MutableStateFlow` |
| ------------------------------- | ---------------------------- |
| Owning singleton                | `private` backing field      |
| Same module (Service, Listener) | `internal` update functions  |
| Composable / UI                 | Read-only `StateFlow` only   |

### 4.3 Bitmap Lifecycle

`ScreenCaptureManager.setFrozenBitmap(bitmap)` **always calls `recycle()` on the
previous bitmap** before assigning the new one. Never call `recycle()` at call
sites — the manager owns the lifecycle.

**Exception:** If a `Bitmap` was just created but the operation that was meant to
hand it to the manager fails (e.g. `PixelCopy` returns a non-SUCCESS result), the
local call site **must** recycle it immediately, since the manager never received it.

\`\`\`kotlin
PixelCopy.request(sv, bitmap, { result ->
if (result == PixelCopy.SUCCESS) {
ScreenCaptureManager.setFrozenBitmap(bitmap) // manager takes ownership
} else {
bitmap.recycle() // manager never got it, local cleanup required
}
}, Handler(Looper.getMainLooper()))
\`\`\`

---

## 5 Kotlin Conventions

### 5.1 Language Level

- Use `enum.entries` (Kotlin 1.9+), never `enum.values()`.
- Use `kotlin.math.min` / `kotlin.math.max`, never `java.lang.Math.*`.
- Use `data class` destructuring or named access (`triple.first`) — avoid
  anonymous destructuring of non-data-class types like `Triple` in lambdas
  (causes ambiguous `componentN()` errors).
- String templates: `"$variable"`, not `"${variable}"` unless accessing a
  property (e.g. `"${obj.name}"`).

### 5.2 Imports

- **Always use explicit imports.** No star imports (`import foo.*`).
- **Never use fully-qualified names inline** in function bodies.
  Move every reference to a top-level `import` statement.
- Sort imports alphabetically; Android Studio / ktlint default ordering.

### 5.3 Constants

- Extract **all magic numbers** to `private const val` (primitives) or
  `private val` (Compose `Dp`, `Color`, etc.) at file scope.
- Use SCREAMING_SNAKE_CASE: `ZOOM_MIN`, `CONTROL_BUTTON_SIZE`, `OVERLAY_TIMEOUT_MS`.
- File-scoped color constants **must be prefixed** with a 2–3 letter screen/feature abbreviation to avoid cross-file collisions:
  ```kotlin
  // GlobalSettingsScreen.kt
  private val GS_BG = Color(0xFF121212)
  private val GS_SURFACE = Color(0xFF1C1C1E)
  ```

### 5.4 Logging

> **Logging mandate: be generous.** The goal is that a single logcat capture at DEBUG level is sufficient to diagnose any bug — without needing a second run. When in doubt, log it.

- **Never use `android.util.Log` directly.** All log calls must go through `AppLog` (`com.stormpanda.megingiard.AppLog`).
- The active log level is controlled at runtime via **Global Settings → Log Level** (persisted in DataStore). Default is `Level.WARN`.
- Use `AppLog.d()` for lifecycle / state-change events, `AppLog.w()` / `AppLog.e()` for genuine warnings and errors.
- High-volume per-frame or per-event calls (e.g., every MOVE event) must **not** be logged at any level.

#### Coverage Requirements

Every file must declare `private const val TAG = "ClassName"` at file scope (or a short alias ≤ 23 chars for Android's tag limit). Every feature implementation must include `AppLog` calls at the following call sites:

| Event type                | Level | Mandatory coverage                                                                                                                            |
| ------------------------- | ----- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Unrecoverable failure     | ERROR | Hardware init, VirtualDisplay, binary deploy                                                                                                  |
| Recoverable / fallback    | WARN  | Out-of-range params, unexpected signals                                                                                                       |
| Major lifecycle milestone | INFO  | Service start/stop, capture start/stop, mode change, `setOnValidScreen`, `setCapturing`, consent result, injector lifecycle (start confirmed) |
| State mutation / CRUD     | DEBUG | Every setter in state singletons, every profile/macro/folder add/update/delete/rename/reorder                                                 |
| Screen lifecycle          | DEBUG | `LaunchedEffect` injector start/stop blocks, `DisposableEffect.onDispose` for all screens                                                     |
| Modifier state machine    | DEBUG | All `KeyboardState` transitions (INACTIVE↔STICKY↔HELD)                                                                                        |
| Action dispatch           | DEBUG | Every `injectActionDown` / `injectActionUp` (except continuous-fire: ScrollWheel, TrackpointMove)                                             |

**What NOT to log (even at VERBOSE):**

- Per-MOVE-event touch/mouse/trackpoint coordinates (continuous fire)
- Per-animation-frame values
- Key repeat interval ticks
- Pan/zoom velocity
- `SettingsManager.updateXxxLive()` methods (called on every drag frame)

### 5.5 API-Level Branching

- When calling APIs that changed signature across SDK versions, use
  `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ)` branching with
  `@Suppress("DEPRECATION")` on the legacy branch — never silently call the
  deprecated path without the annotation.

\`\`\`kotlin
@Suppress("DEPRECATION")
val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
intent?.getParcelableExtra("DATA", Intent::class.java)
} else {
intent?.getParcelableExtra("DATA")
}
\`\`\`

---

## 6 Jetpack Compose Rules

### 6.1 Side Effects & LaunchedEffect

- **Never use rapidly-changing Compose state as a `LaunchedEffect` key.**
  If you need to react to animation values or frequently-updating state,
  use `snapshotFlow { ... }.collectLatest { }` inside a `LaunchedEffect(Unit)`.

\`\`\`kotlin
// ✅ Correct – single launch, reactive collection
LaunchedEffect(Unit) {
snapshotFlow { animScale.value }
.collectLatest { scale -> manager.setScale(scale) }
}

// ❌ Wrong – restarts coroutine on every animation frame
LaunchedEffect(animScale.value) {
manager.setScale(animScale.value)
}
\`\`\`

- `snapshotFlow` is in `androidx.compose.runtime`, **not** `kotlinx.coroutines`.

**Polling fallback:** When reacting to state that is _not_ exposed as a `Flow` (e.g., an imperative property updated by a system callback), use a `while(true) + delay()` poll inside a keyed `LaunchedEffect` rather than forcing an artificial flow:

```kotlin
// ✅ Correct – polling imperative state
LaunchedEffect(isActive) {
    if (isActive) {
        while (true) {
            val state = someManager.currentState
            // use state …
            delay(POLL_INTERVAL_MS)
        }
    }
}
```

The coroutine is automatically cancelled when the key (`isActive`) changes to `false`.

### 6.2 String Resources

- **All user-visible strings** must live in `res/values/strings.xml`.
- Composables: `stringResource(R.string.key)`.
- Services / non-Compose code: `getString(R.string.key)` or `context.getString(...)`.
- Format args: `stringResource(R.string.key, arg1, arg2)` — not string concatenation.
- Internal defaults in singletons that are immediately overwritten by
  callbacks may remain as literals.

### 6.3 Accessibility

- Every `Icon` and `Image` must have a meaningful `contentDescription`
  backed by a string resource, or `null` if purely decorative.

### 6.4 Shared UI Components

- Reusable Composables (overlay controls, auto-hide timers) belong in `ui/`.
- Do not duplicate overlay code across screens. Use
  shared overlay tools and `AppStateManager.overlayVisible` / `triggerOverlay()`.

### 6.5 Collecting StateFlows

| Context                                                | Pattern                                                                   |
| ------------------------------------------------------ | ------------------------------------------------------------------------- |
| Inside a `@Composable`                                 | `.collectAsState()` — converts to Compose `State`, triggers recomposition |
| Inside a `Service`, `Presentation`, or coroutine scope | `.collect { }` — imperative side-effect, no recomposition involved        |

Never call `.collectAsState()` outside a Composable; never call raw `.collect {}` inside a Composable when the result drives UI.

---

## 7 Coroutines & Lifecycle

### 7.1 CoroutineScope in Services / Presentation

- Use **`SupervisorJob()`**, never bare `Job()`, for class-level
  `CoroutineScope` instances in `Service` or `Presentation` subclasses.
  This prevents a single child failure from cancelling unrelated siblings.
- **Cancel the scope** in `onDestroy()` / teardown:

\`\`\`kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

override fun onDestroy() {
scope.cancel()
super.onDestroy()
}
\`\`\`

### 7.2 No Duplicate Scopes

- Never create a local `CoroutineScope` inside `onStartCommand()` or
  similar lifecycle methods. Reuse the class-level scope.

### 7.3 Naming

- Avoid naming your own methods identically to framework methods.
  Example: rename a helper `startForegroundNotification()` instead of
  `startForegroundService()` to prevent shadowing `Context.startForegroundService()`.

### 7.4 Combining Multiple StateFlows

When logic depends on **two or more independent `StateFlow`s**, use `combine()` rather than nesting `collect {}` calls:

```kotlin
// ✅ Correct – single derived signal
scope.launch {
    combine(
        AppStateManager.isActivityResumed,
        AppStateManager.isOnValidScreen
    ) { resumed, valid ->
        resumed && valid
    }.collect { shouldShow ->
        if (shouldShow) show() else hide()
    }
}

// ❌ Wrong – nested collects create race conditions
scope.launch {
    AppStateManager.currentMode.collect { mode ->
        AppStateManager.isActivityResumed.collect { resumed -> … }
    }
}
```

---

## 8 Android Manifest & Permissions

- Only declare components that **actually exist** as classes. A missing
  class behind a `<service>` or `<receiver>` declaration causes a runtime
  crash on some OEMs.
- Required permissions for this project:
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_MEDIA_PROJECTION`
  - `POST_NOTIFICATIONS`

---

## 9 Resource Management

### 9.1 Bitmap Handling

- Always recycle Bitmaps when they are no longer needed.
- Centralize recycle logic in the owning manager (see §4.3).

### 9.2 VirtualDisplay / MediaProjection

- Detach `VirtualDisplay.surface = null` to freeze; reassign to resume.
  Never recreate the VirtualDisplay to toggle freeze.
- Use `Presentation.hide()` / `Presentation.show()` for mode switching;
  never `dismiss()`, which destroys the window permanently.
- **Exception:** In `Service.onDestroy()` (full teardown), calling `dismiss()`
  is correct — the process is being destroyed anyway. The `hide()` vs `dismiss()`
  rule only applies to in-session mode switching.
- **Teardown order in `onDestroy()`:** Cancel the coroutine scope _before_ releasing hardware resources and calling `dismiss()`. This prevents in-flight coroutines from racing against resource deallocation:
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      scope.cancel()          // 1. stop all coroutines
      virtualDisplay?.release() // 2. release hardware
      mediaProjection?.stop()
      mirrorPresentation?.dismiss() // 3. destroy window
  }
  ```

### 9.3 SurfaceView Layer Order

- The `SurfaceView` that receives the `VirtualDisplay` output **must** call
  `setZOrderMediaOverlay(true)`. Without it, the hardware buffer renders behind
  the window background, producing a black screen.
- The `ComposeView` overlay is then layered on top of the `SurfaceView` by
  standard `FrameLayout` z-ordering.

### 9.4 Foreground Service

- Use `START_NOT_STICKY` as the return value in `onStartCommand()`.
  The service must not be auto-restarted by the system after being killed —
  re-acquiring `MediaProjection` requires a fresh user consent.
- Always call `startForeground()` / `startForegroundNotification()` before any
  `MediaProjection` work starts to avoid ANR on API 29+.

### 9.5 Multi-Display Activity Launching

- When launching an `Activity` that must appear on the primary screen
  (e.g. `CaptureRequestActivity`), use `ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`.
  Without this, the activity inherits the display of the calling context,
  which on the AYN Thor is the secondary display.

\`\`\`kotlin
val options = ActivityOptions.makeBasic()
options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
startActivity(intent, options.toBundle())
\`\`\`

### 9.6 MirrorPresentationLifecycleOwner — Setup & Teardown

**Setup:** After creating the `MirrorPresentationLifecycleOwner`, inject it into the Presentation's DecorView _before_ setting any `ComposeView` content:

```kotlin
window?.decorView?.apply {
    setViewTreeLifecycleOwner(lifecycleOwner)
    setViewTreeSavedStateRegistryOwner(lifecycleOwner)
    setViewTreeViewModelStoreOwner(lifecycleOwner)
}
```

Without this, Compose cannot find a lifecycle owner / ViewModel store and throws at runtime.

**Teardown:** `MirrorPresentationLifecycleOwner.destroy()` **must** be called in the
Presentation's `setOnDismissListener`. It fires the `ON_DESTROY` lifecycle
event that cleans up all Compose state registered against the owner and clears the `ViewModelStore`.

```kotlin
setOnDismissListener {
    scope.cancel()
    lifecycleOwner.destroy()
}
```

### 9.7 Native Touch Injection (Touchpad & Mirror Touch Projection)

- `ShellInputInjector` manages the lifecycle of the `touchinjector_arm64`
  native helper binary (bundled in `assets/`). On `start()`, the binary is
  copied to `filesDir`, made executable, and launched via `ProcessBuilder`.
  It opens `/dev/input/event6` once and stays alive for the session.
- The binary is driven via **stdin** (`"D x y\n"` / `"M x y\n"` / `"U x y\n"`)
  and signals readiness with `"R\n"` on stdout. A dedicated writer thread
  coalesces pending MOVE events (keep-latest) to prevent backlog.
- `TouchInjector.injectTouch()` converts normalised Compose coordinates to
  the sensor's physical portrait space:
  `sensor_x = (1 − normalizedY) * 1080`, `sensor_y = normalizedX * 1920`.
- `ShellInputInjector.stop()` **must** be called when leaving fullscreen mouse mode.
  In `FullscreenMouseOverlay` this is done via `DisposableEffect(Unit) { onDispose { TouchInjector.stop() } }`.
- The device node `/dev/input/event6` is `crw-rw-rw-` on the AYN Thor —
  no root or special permission required beyond the standard shell UID (2000).
  See `docs/BUILD_NATIVE.md` for the full build and protocol specification.

### 9.8 Native Key Injection (Keyboard)

- `ShellKeyInjector` manages the lifecycle of the `keyinjector_arm64`
  native helper binary (bundled in `assets/`). On `start()`, the binary is
  copied to `filesDir`, made executable, and launched via `ProcessBuilder`.
  It opens `/dev/uinput` once and stays alive for the keyboard session.
- The binary is driven via **stdin** (`"KD <keycode>\n"` / `"KU <keycode>\n"`)
  and signals readiness with `"R\n"` on stdout. A dedicated writer thread
  delivers all events **in order** — unlike touch injection, no MOVE coalescing
  is applied (every key-down and key-up must be preserved).
- `KeyInjector.stop()` **must** be called when leaving `KEYBOARD` mode.
  In `KeyboardScreen` this is done via `DisposableEffect(Unit) { onDispose { KeyInjector.stop() } }`.
- The device node `/dev/uinput` is accessible under the standard shell UID (2000) on the AYN Thor —
  no root or special permission required.
  See `docs/BUILD_NATIVE.md` for the full build and protocol specification.
- **Keycode registration range: 1–255 only.** The binary registers `UI_SET_KEYBIT` for codes 1–255.
  Codes 256+ are BTN\_ device buttons (mouse, gamepad, stylus). Registering `BTN_TOOL_PEN` (0x140 = 320)
  causes Android's `EventHub` to classify the device as `EXTERNAL_STYLUS` instead of `KEYBOARD`;
  an `EXTERNAL_STYLUS` device has no `KeyboardInputMapper`, so EV_KEY events are silently ignored by
  Android's input pipeline. All keyboard keycodes used by the app are ≤ 125. `ShellKeyInjector.injectKey()`
  enforces the matching 1..255 guard.

---

## 10 Build & Dependencies

- All dependency versions live in `gradle/libs.versions.toml`.
  Never hardcode version strings in `build.gradle.kts`.
- `isMinifyEnabled = false` for now (release builds).
  When enabling R8, add keep rules for `ScreenCaptureService` (referenced by the system via manifest).

---

## 11 Git Conventions

- **Commit messages** follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`.
- Use a short imperative summary line, then a blank line, then bullet-point
  details for multi-topic commits.
- Keep commits atomic — one logical change per commit when feasible.

---

## 12 What NOT to Change

These constraints are non-negotiable:

1. **`minSdk = 33`** — the device ships with this API level.
2. **`targetSdk = 35`** — keep in sync with `compileSdk`.
3. **All existing user-facing functionality** must be preserved across
   refactors. No feature removals without explicit human approval.
4. **Hardware rendering pipeline** (`MediaProjection` → `VirtualDisplay` →
   `SurfaceView` inside `Presentation`) — this is the only architecture
   that achieves zero-latency DRM-free mirroring on the AYN Thor.
5. **Synthetic LifecycleOwner in Presentation** — required for Compose
   inside the background-service window.

---

## 13 Commit Message Proposal

After completing every set of changes, you MUST propose a ready-to-use commit message. Use Conventional Commits format:

```
<type>: <short imperative summary>

- bullet describing change 1
- bullet describing change 2
```

**Scope of the commit message:**
The message MUST cover **every change made since the last commit** — across the entire conversation, not only the most recent editing step. This means: if the session involved multiple rounds of edits (e.g. first a refactor, then a bug fix, then a follow-up tweak), all of them must appear as bullets in the final proposal. Never limit the message to just the last reply or last file touched.

When in doubt, mentally run through all files that were modified during the session and verify each one is represented.

The proposal must be copy-paste ready — no placeholders. Present it as a code block so the user can copy it directly.

---

## 14 Documentation Sync After Changes

After implementing any change that affects a feature's behaviour, interface, or architecture:

1. **Identify affected features** — determine which `FEATURE.md` file(s) cover the changed code.
2. **Review the documentation** — read the relevant `FEATURE.md` and check whether the change invalidates any Functional Requirements or Technical Implementation section.
3. **Update `FEATURE.md` if needed** — keep the documentation in sync with the implementation. This includes:
   - Correcting or removing outdated requirements or technical descriptions.
   - Adding documentation for new behaviour introduced by the change.
4. **Propagate to higher-level docs if necessary** — if the change is architecturally significant, also review `docs/ARCHITECTURE.md` and `PRD.md`.

This rule applies to all changes, including bug fixes, refactors, and dependency updates that affect runtime behaviour.

---

## 16 Design System Rules

These rules encode the unified design system introduced in the typography/theming refactor.
Every agent **must** follow them when writing or editing any Composable.

### 16.1 Typography — NEVER inline `fontSize`

All text sizing **must** use `MaterialTheme.typography.*`. Inline `fontSize = XX.sp`
in Composables (outside `AppTheme.kt`) is **forbidden**.

| Use case                       | Token                                                  |
| ------------------------------ | ------------------------------------------------------ |
| Dialog titles, section headers | `MaterialTheme.typography.titleLarge` (18sp SemiBold)  |
| Section titles                 | `MaterialTheme.typography.titleMedium` (16sp SemiBold) |
| Subsection titles              | `MaterialTheme.typography.titleSmall` (14sp Medium)    |
| Macro names, list items        | `MaterialTheme.typography.bodyLarge` (15sp Normal)     |
| Standard row labels (default)  | `MaterialTheme.typography.bodyMedium` (14sp Normal)    |
| Secondary descriptions, hints  | `MaterialTheme.typography.bodySmall` (12sp Normal)     |
| Button labels                  | `MaterialTheme.typography.labelLarge` (14sp Medium)    |
| Dialog subtitles               | `MaterialTheme.typography.labelMedium` (13sp Medium)   |
| Category headers, pill labels  | `MaterialTheme.typography.labelSmall` (11sp Normal)    |

**Allowed exceptions** (must be commented):

- Computed/data-driven sizes: `(11 * btn.buttonSize.cols).sp` (button label scaled by size)
- Conditional key sizes: `if (widthWeight >= 1.5f) 11.sp else 12.sp` (keyboard key cap)
- Internal renderers: `MaterialSymbols` size→sp conversion
- Intentionally tiny labels below 11sp (e.g. icon name under icon picker grid at 8sp)

### 16.2 Colors — NEVER hardcode `Color(0xFF...)`

All colors in Composables must come from:

- `LocalAppColors.current.<token>` — for app-defined semantic colors (accent, surface, error, etc.)
- `MaterialTheme.colorScheme.<token>` — for M3 semantic colors (primary, onPrimary, etc.)

**Hardcoded `Color(0xFF...)` literals in screen/composable code are forbidden.**

Key token mapping:
| Value | Token |
|---|---|
| `Color(0xFFCF6679)` / `Color(0xFFB00020)` | `LocalAppColors.current.error` |
| `Color(0xFFFF9800)` (gamepad orange) | `LocalAppColors.current.actionColorGamepad` |
| `Color(0xFF2196F3)` (system blue) | `LocalAppColors.current.actionColorSystem` |

### 16.3 Spacing — Prefer named constants over bare `XX.dp`

All dimension values must be extracted to `private val` constants at file scope using
SCREAMING_SNAKE_CASE with a 2-3 letter feature prefix (e.g. `GS_SECTION_PADDING = 16.dp`).
Bare magic `XX.dp` inline in Composable code is a code smell.

### 16.4 M3 Component Colors — do NOT override what the ColorScheme provides

Do **not** pass manual `colors =` overrides to M3 components when the `ColorScheme`
already handles them correctly:

```kotlin
// ❌ WRONG — override defeats the design system
Switch(
    colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
)

// ✅ CORRECT — let MaterialTheme.colorScheme.primary drive it
Switch(checked = ..., onCheckedChange = ...)
```

This applies to: `Switch`, `Slider`, `Checkbox`, `RadioButton`, `OutlinedTextField`
(border colors are OK to override via `colors =` for contextual accent).

### 16.5 New Composable Parameters — no `accentColor: Color` proliferation

Do **not** add `accentColor: Color` as a new Composable parameter.
Read accent from `LocalAppColors.current.accent` or `MaterialTheme.colorScheme.primary` directly.
Existing `accentColor` parameters in older Composables may remain until a future refactor.

---

## 15 Checklist for Every Change

> **Compilation policy:** The human operator always compiles the project themselves.
> The agent must **never** run `./gradlew compileDebugKotlin` or any other build
> command to verify a change. Instead, perform **static analysis only**: check imports,
> symbol references, type compatibility, and API usage by reading the relevant source
> files. Flag any suspected compile error as a comment to the operator.

Before marking a task as done, verify:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] No FQN references inline — all moved to imports
- [ ] No magic numbers — all extracted to named constants
- [ ] No `android.util.Log` calls outside `AppLog.kt` — all logging via `AppLog`
- [ ] Every new file has `private const val TAG = "ClassName"` and uses `AppLog` per §5.4 Coverage Requirements
- [ ] All user-visible strings in `strings.xml`
- [ ] All Icons have `contentDescription` (string resource or `null`)
- [ ] `SupervisorJob()` used (not `Job()`) for class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] Bitmap recycling handled by the manager, not call sites (see §4.3 for the PixelCopy exception)
- [ ] `snapshotFlow` imported from `androidx.compose.runtime`
- [ ] Deprecated API branches annotated with `@Suppress("DEPRECATION")`
- [ ] New `Activity` launches on correct display via `ActivityOptions.setLaunchDisplayId()`
- [ ] `Presentation` mode switching uses `hide()`/`show()`, not `dismiss()` (except in `onDestroy()`)
- [ ] `MirrorPresentationLifecycleOwner.destroy()` called in `setOnDismissListener`
- [ ] `SurfaceView` receiving `VirtualDisplay` output has `setZOrderMediaOverlay(true)`
- [ ] Service `onStartCommand` returns `START_NOT_STICKY`
- [ ] `MirrorPresentation` show/hide reacts to presentation visibility conditions
- [ ] Touch injector process stopped in `DisposableEffect` when leaving `TOUCHPAD` mode
- [ ] Key injector process stopped in `DisposableEffect` when leaving `KEYBOARD` mode
- [ ] No suspected compile errors (verified via static analysis — imports, symbols, types)
