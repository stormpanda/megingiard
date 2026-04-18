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

---

## 2 Documentation Map

| Document                            | Purpose                                                               |
| ----------------------------------- | --------------------------------------------------------------------- |
| `README.md`                         | Project overview, feature list, quick links                           |
| `PRD.md`                            | Product requirements (German, authoritative)                          |
| `docs/REQUIREMENTS.md`              | Requirements overview & non-functional requirements                   |
| `docs/ARCHITECTURE.md`              | System architecture overview & key design decisions                   |
| `docs/features/mirror/FEATURE.md`   | Screen Mirror — functional requirements & technical implementation    |
| `docs/features/touchpad/FEATURE.md` | Virtual Touchpad — functional requirements & technical implementation |
| `docs/features/keyboard/FEATURE.md` | Virtual Keyboard — functional requirements & technical implementation |
| `docs/features/FEATURE_TEMPLATE.md` | Template for new feature documentation                                |

> **Convention:** Every feature has its own subfolder under `docs/features/<feature>/` containing a single `FEATURE.md`. This file is the **authoritative source of truth** for that feature's requirements and technical implementation. When adding a new feature, create a new subfolder and `FEATURE.md`.
> | `docs/BUILD_NATIVE.md` | How to rebuild the native touch-injector binary |
> | `AGENTS.md` _(this file)_ | AI agent coding conventions & constraints |

---

## 3 Package Structure

\`\`\`
com.stormpanda.megingiard
├── AppLog.kt # Unified logging facade (level-gated, tag-prefixed)\n├── AppStateManager.kt # Global app-level state (mode, lifecycle flags)
├── CaptureRequestActivity.kt # MediaProjection consent dialog (transparent Activity)
├── MainActivity.kt # Entry point, permission checks, display detection
├── MainAppScreen.kt # Top-level Composable (Crossfade + carousel overlay)
├── mirror/
│ ├── MirrorPresentation.kt # android.app.Presentation on secondary display
│ ├── MirrorPresentationLifecycleOwner.kt # Synthetic LifecycleOwner for Compose-in-Presentation
│ ├── MirrorScreen.kt # Mirror Composable (pan/zoom/freeze/lock/touch-projection + carousel nav)
│ ├── MirrorControlPanel.kt # Animated control pill (Stop/Freeze/Lock/TouchProjection buttons)
│ ├── MirrorCoordinateTransform.kt # Pure projectCoordinates() geometry helper
│ ├── ScreenCaptureManager.kt # Mirror state flows (scale, offset, freeze, lock, projection, etc.)
│ └── ScreenCaptureService.kt # Foreground Service managing MediaProjection
├── input/
│ ├── ShellInputInjector.kt # Native binary lifecycle, writer thread, MOVE coalescing (shared)
│ ├── TouchAction.kt # Shared DOWN/MOVE/UP enum
│ ├── TouchInjector.kt # Normalised → physical coordinate transform facade (shared)
│ ├── MouseInjector.kt # Public facade over ShellMouseInjector (shared)
│ └── ShellMouseInjector.kt # Native binary lifecycle + MOVE-coalescing writer thread (shared)
├── keyboard/
│ ├── KeyboardScreen.kt # Full keyboard Composable (QWERTZ/QWERTY/AZERTY + trackpoint)
│ ├── KeyboardKeyCap.kt # KeyBounds data class, KeyCap Composable
│ ├── KeyboardMouseOverlay.kt # Mouse button overlay (MouseButtonColumn, ScrollWheelButton, etc.)
│ ├── KeyboardState.kt # Modifier key state machine (INACTIVE/STICKY/HELD)
│ ├── KeyboardLayout.kt # Layout definitions, KeyDef data class, findKeyInLayout()
│ ├── KeyInjector.kt # Key injection facade (delegates to ShellKeyInjector)
│ ├── ShellKeyInjector.kt # Native binary lifecycle for key injection via /dev/uinput
│ ├── KeyAction.kt # Shared DOWN/UP enum
│ └── LinuxKeycodes.kt # Linux input-event-codes constants
├── settings/
│ ├── ColorWheelPicker.kt # HSV color picker Dialog (hue wheel + brightness slider)
│ ├── GlobalSettingsScreen.kt # Full-screen global settings Composable (scaffold + state only)
│ ├── GlobalSettingsComponents.kt # Extracted setting row Composables for GlobalSettingsScreen
│ ├── SettingsManager.kt # App-wide settings persistence via DataStore
│ └── ToolSettingsComponents.kt # Reusable row Composables (RememberSettingRow, SliderSettingRow, dropdowns, etc.)
├── config/
│ ├── ConfigSchema.kt # @Serializable data classes: MegingiardExport, ExportMetadata
│ └── ConfigManager.kt # Unified export/import: coordinator StateFlows, SAF I/O, UUID remap, checksum
├── macropad/
│ ├── MacroPadScreen.kt # Use-mode Composable (pad render, multi-touch, injector lifecycle)
│ ├── MacroPadButton.kt # PadButton & ScrollWheelFace Composables + button constants
│ ├── MacroPadActionDispatch.kt # injectActionDown/Up helpers (delegates to injectors)
│ ├── MacroPadEditor.kt # Full-screen layout editor (profile CRUD, drag-repositioning)
│ ├── PadCanvas.kt # Draggable editor canvas (DraggableButton, PadCanvas)
│ ├── PadButtonEditDialog.kt # Button create/edit dialog (ButtonEditDialog, SectionLabel)
│ ├── PadActionPicker.kt # Action-selection UI (ActionPicker, sub-pickers, KEYBOARD_KEY_PRESETS)
│ ├── AmbientMacroPadOverlay.kt # Ambient Display overlay (mirror background + blur/dim + MacroPad)
│ ├── AmbientSettingsOverlay.kt # Per-layout ambient settings editor (dim, vignette shape/area/transition/opacity/colour)
│ ├── MacroPadState.kt # Singleton state: profiles + active profile, CRUD, persistence
│ ├── MacroPadLayout.kt # Serializable data model: PadProfile, PadButton, PadAction
│ ├── GamepadInjector.kt # Public facade over ShellGamepadInjector
│ ├── ShellGamepadInjector.kt # Native binary lifecycle + writer thread for gamepad injection
│ └── GamepadKeycodes.kt # Linux BTN\_ constants + preset list
├── touchpad/
│ └── FullscreenMouseOverlay.kt # Fullscreen mouse overlay for macro action dispatch
└── ui/
├── IdlePill.kt # Always-visible edge pill (swipe affordance + close label)
└── PillMenu.kt # Fullscreen pill menu overlay (dual-card: bottom profile/layout, top mirror controls)
\`\`\`

**Rule:** New feature modules get their own sub-package. Shared UI components belong in `ui/`.

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
