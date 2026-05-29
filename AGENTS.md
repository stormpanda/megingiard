# AGENTS.md – AI Agent Coding Guidelines for Megingiard

> This file instructs AI coding agents (GitHub Copilot, Cursor, Cline, etc.) on the
> conventions, patterns, and constraints that govern this project. Treat every rule
> as mandatory unless the human operator explicitly overrides it.

---

## 1 Project Identity

| Key               | Value                                                                                                                                                                                                  |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Package**       | `com.stormpanda.megingiard`                                                                                                                                                                            |
| **Language**      | Kotlin 2.0+ (no Java files). **Exception:** the `:mirrorserver` module uses Java because it produces a standalone shell-UID DEX loaded by `/system/bin/app_process`. No new Java modules may be added. |
| **UI Framework**  | Jetpack Compose (Material 3, BOM-managed)                                                                                                                                                              |
| **Min SDK**       | 33 — **must not be raised**                                                                                                                                                                            |
| **Target SDK**    | 35                                                                                                                                                                                                     |
| **Build System**  | Gradle (Kotlin DSL), version catalog `libs.versions.toml`                                                                                                                                              |
| **Device Target** | AYN Thor dual-screen Android handheld                                                                                                                                                                  |

---

## 2 Documentation Map

| Document                                   | Purpose                                                                                  |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `README.md`                                | Project overview, feature list, quick links                                              |
| `LICENSE`                                  | Megingiard Source-Available License (Version 1.0)                                        |
| `CONTRIBUTING.md`                          | Contribution guidelines, codebase standards, and licensing compliance                    |
| `SECURITY_CONCEPT.md`                      | Security concept overview, threat model, hardening layers, and links to detailed docs    |
| `docs/ARCHITECTURE.md`                     | System architecture overview & key design decisions                                      |
| `docs/BUILD_NATIVE.md`                     | Build setup, instructions, and protocol specifications for native C binaries             |
| `docs/MANUAL_VERIFICATION.md`              | Manual Verification Guide — step-by-step manual regression tests and PR sanity checklists |
| `docs/REQUIREMENTS.md`                     | Requirements overview & non-functional requirements                                      |
| `docs/features/config/FEATURE.md`          | Configuration Export/Import — portable `.mgrd` app-wide backup and profile sharing       |
| `docs/features/FEATURE_TEMPLATE.md`        | Template for new feature documentation                                                   |
| `docs/features/keyboard/FEATURE.md`        | Virtual Keyboard — functional requirements & technical implementation                    |
| `docs/features/log-report/FEATURE.md`      | Log Report Export — save logcat output to a file for bug reports                         |
| `docs/features/macropad/FEATURE.md`        | MacroPad — profiles, layouts, and custom macro triggers & execution                      |
| `docs/features/mirror/FEATURE.md`          | Screen Mirror — functional requirements & technical implementation                       |
| `docs/features/pillmenu/FEATURE.md`        | Idle Pill & Pill Menu — navigation overlays & mirror control cards                       |
| `docs/features/privileged-mode/FEATURE.md` | Privileged Mode — on-device privileged daemon, ADB-Wireless bootstrap, per-feature flags |
| `docs/features/theming/FEATURE.md`         | Design System — AppColors, Typography, AppDimens, ColorScheme bridge                     |
| `docs/features/touchpad/FEATURE.md`        | Virtual Touchpad — functional requirements & technical implementation                    |

> [!IMPORTANT]
> **Documentation language: English only.**
>
> All documentation files in this repository (`*.md` under `docs/`, `AGENTS.md`,
> `README.md`, `FEATURE.md` files, etc.) MUST be written in **English**.
> Every file — including new `FEATURE.md` files, and architecture notes — must be written and maintained in
> English so that all AI agents can process them without ambiguity.

> [!IMPORTANT]
> **Mandatory doc-sync rule — applies after every code change.**
>
> After implementing any change that affects a feature’s behaviour, interface, or architecture,
> you MUST:
>
> 1. Identify which `FEATURE.md` file(s) cover the changed code (use the table above).
> 2. Read the relevant `FEATURE.md` and check whether the change invalidates any Functional
>    Requirements or Technical Implementation section.
> 3. Update `FEATURE.md` to reflect the change — correct outdated descriptions, remove stale
>    requirements, add documentation for new behaviour.
> 4. If the change is architecturally significant, also update `docs/ARCHITECTURE.md`.
>
> This applies to **all** changes — including bug fixes, refactors, and dependency updates that
> affect runtime behaviour. Do not skip this step.
>
> Security-affecting changes MUST also update `SECURITY_CONCEPT.md` and whichever detailed
> document owns the changed mechanism (`docs/ARCHITECTURE.md`, `docs/BUILD_NATIVE.md`, or the
> relevant feature `FEATURE.md`).

> [!NOTE]
> **New features — create a `FEATURE.md` first.**
>
> Every new feature MUST have its own `FEATURE.md` in a dedicated subfolder
> `docs/features/<feature>/`. Use [`docs/features/FEATURE_TEMPLATE.md`](docs/features/FEATURE_TEMPLATE.md)
> as the starting point. Once created, **add a row for it to the Documentation Map table above**
> so all agents can discover it.

---

## 3 Checklist for Every Change (Elevated for AI Attention)

> **Compilation policy:** The agent is encouraged to run `./gradlew compileDebugKotlin` or `./gradlew :app:assembleDebug` to verify compile safety before presenting changes to the human operator.

> **Native binary rebuild policy:** Whenever a native C source file is modified, the
> agent **must** immediately run the corresponding build script to rebuild the bundled
> binary. The scripts are at the workspace root:
>
> | Source file                           | Build script                                                             |
> | ------------------------------------- | ------------------------------------------------------------------------ |
> | `app/src/main/cpp/megingiard_privd.c` | `./build_megingiard_privd.sh`                                            |
> | `app/src/main/cpp/touchinjector.c`    | Manual compile in `docs/BUILD_NATIVE.md` until a dedicated script exists |
> | `app/src/main/cpp/keyinjector.c`      | `./build_keyinjector.sh`                                                 |
> | `app/src/main/cpp/mouseinjector.c`    | `./build_mouseinjector.sh`                                               |
> | `app/src/main/cpp/gamepadinjector.c`  | `./build_gamepadinjector.sh`                                             |
>
> Run the script **before** proposing the commit message. If the build fails, fix the
> source error before proceeding. The scripts must be run from the workspace root.

> **Unit test policy:** After every implementation — feature, bug fix, or refactor —
> the agent **must**:
>
> 1. **Write new tests** for any new pure logic in `:core` or `:domain` (pure functions,
>    data compilers, state machines, serialization round-trips).
> 2. **Update existing tests** if the change modifies the behaviour or signature of
>    already-tested code.
> 3. **Run all tests** via `./gradlew :core:test :domain:test` and report the result.
>    This, along with sandbox compilation commands for verifying compile safety, are the **only** Gradle commands the agent is permitted to run.
>
> Tests must be placed in the correct source set:
>
> - `:core` pure-JVM tests → `core/src/test/kotlin/`
> - `:domain` local tests → `domain/src/test/java/`
>
> If logic cannot be unit-tested without significant refactoring, state that explicitly
> as a follow-up task rather than skipping silently.

Before marking a task as done, verify:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] No FQN references inline — all moved to imports
- [ ] No magic numbers — all extracted to named constants
- [ ] No `android.util.Log` calls outside `AppLog.kt` — all logging via `AppLog`
- [ ] Every new file has `private const val TAG = "ClassName"` and uses `AppLog` per §8.4 Coverage Requirements
- [ ] All user-visible strings in `strings.xml`
- [ ] All Icons have `contentDescription` (string resource or `null`)
- [ ] `SupervisorJob()` used (not `Job()`) for class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] Bitmap recycling handled by the manager, not call sites (see §7.3 for the PixelCopy exception)
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
- [ ] No suspected compile errors (verified via static analysis or build compiles)
- [ ] New or changed pure logic is covered by unit tests in `:core` or `:domain`
- [ ] Existing tests updated if the change modifies previously-tested behaviour
- [ ] `./gradlew :core:test :domain:test` executed and all tests pass (permitted test command)
- [ ] If any native C source was modified, the corresponding build script was run and produced a new binary

---

## 4 Commit Message Proposal (Elevated for AI Attention)

After completing every set of changes, you MUST propose a ready-to-use commit message. Use Conventional Commits format:

```
<type>: <short imperative summary>

- bullet describing change 1
- bullet describing change 2
```

**Scope of the commit message:**
The message MUST cover **every uncommitted change currently visible in `git status`** — across the entire active working copy. If the session involved multiple rounds of edits without commits, all of them must appear as bullets in the final proposal. However, never include files that have already been committed in previous steps of the conversation and are no longer present in `git status`.

**Determining which files changed:** Before writing the commit message, run `git status` to get the definitive list of modified files. Include **all** files shown — both staged and unstaged — without distinguishing between the two states. Do not rely solely on memory of what was edited during the conversation; the `git status` output is the authoritative source.

The proposal must be copy-paste ready — no placeholders. You must present it as a separate code block so the user can copy it directly.

---

## 5 Documentation Sync After Changes (Elevated for AI Attention)

After implementing any change that affects a feature’s behaviour, interface, or architecture:

1. **Identify affected features** — determine which `FEATURE.md` file(s) cover the changed code
   (consult the Documentation Map in §2).
2. **Review the documentation** — read the relevant `FEATURE.md` and check whether the change
   invalidates any Functional Requirements or Technical Implementation section.
3. **Update `FEATURE.md` if needed** — keep the documentation in sync with the implementation:
   - Correct or remove outdated requirements or technical descriptions.
   - Add documentation for new behaviour introduced by the change.
4. **Propagate to higher-level docs if necessary** — if the change is architecturally significant,
   also review `docs/ARCHITECTURE.md`.
5. **New features** — create a new `docs/features/<feature>/FEATURE.md` using
   [`docs/features/FEATURE_TEMPLATE.md`](docs/features/FEATURE_TEMPLATE.md) as the starting
   point, then **add a row to the Documentation Map table in §2**.

This rule applies to all changes, including bug fixes, refactors, and dependency updates that
affect runtime behaviour.

---

## 6 Package Structure

The project is split into three Gradle modules:

* **`:app`** — Android UI layer (Activities, viewmodels, custom Compose views, and secondary screen presentations).
* **`:domain`** — Platform-free business logic, device managers, input injection facades, and singleton state holders. Must **never** import Android UI or Composable dependencies.
* **`:core`** — Pure JVM/Kotlin data models, serializable schemas, constants, and math helpers. Must **never** have Android dependencies.

### Core Architectural Directories
Across all modules, files are organized into feature-centric packages. Keep these structural rules in mind:
* `macropad/` — Profiles, layout configurations, macro engines, and injection coordinator flows.
* `mirror/` — Screen mirroring presentation views, viewport math, and capture-session managers.
* `keyboard/` — Virtual keyboard Composable layouts, key caps, and key injector services.
* `input/` / `privd/` — Local socket IPC clients, ADB-Wireless wizards, and evdev/uinput wrappers.
* `ui/` — Design system constants, AppTheme palette factories, and reusable edge overlay pill menus.

**Rule:** New feature modules get their own sub-package. Shared UI components belong in `:app/.../ui/`. Business logic with no Android UI dependency belongs in `:domain`. Pure data types and constants belong in `:core`.

---

## 7 State Management

### 7.1 Singleton State Holders

State is managed by **`object` singletons** (`AppStateManager`, `ScreenCaptureManager`) that expose **read-only `StateFlow`** and keep all `MutableStateFlow` backing fields **`private`**.

```kotlin
// ✅ Correct pattern
object FooManager {
    private val _bar = MutableStateFlow(0)
    val bar: StateFlow<Int> = _bar.asStateFlow()

    fun setBar(value: Int) { _bar.value = value }
}

// ❌ Never expose MutableStateFlow
object FooManager {
    val bar = MutableStateFlow(0) // WRONG
}
```

### 7.2 Visibility Rules

| Layer                           | Access to `MutableStateFlow` |
| ------------------------------- | ---------------------------- |
| Owning singleton                | `private` backing field      |
| Same module (Service, Listener) | `internal` update functions  |
| Composable / UI                 | Read-only `StateFlow` only   |

### 7.3 Bitmap Lifecycle

`ScreenCaptureManager.setFrozenBitmap(bitmap)` **always calls `recycle()` on the
previous bitmap** before assigning the new one. Never call `recycle()` at call
sites — the manager owns the lifecycle.

**Exception:** If a `Bitmap` was just created but the operation that was meant to
hand it to the manager fails (e.g. `PixelCopy` returns a non-SUCCESS result), the
local call site **must** recycle it immediately, since the manager never received it.

```kotlin
PixelCopy.request(sv, bitmap, { result ->
    if (result == PixelCopy.SUCCESS) {
        ScreenCaptureManager.setFrozenBitmap(bitmap) // manager takes ownership
    } else {
        bitmap.recycle() // manager never got it, local cleanup required
    }
}, Handler(Looper.getMainLooper()))
```

---

## 8 Kotlin Conventions

### 8.1 Language Level

- Use `enum.entries` (Kotlin 1.9+), never `enum.values()`.
- Use `kotlin.math.min` / `kotlin.math.max`, never `java.lang.Math.*`.
- Use `data class` destructuring or named access (`triple.first`) — avoid
  anonymous destructuring of non-data-class types like `Triple` in lambdas
  (causes ambiguous `componentN()` errors).
- String templates: `"$variable"`, not `"${variable}"` unless accessing a
  property (e.g. `"${obj.name}"`).

### 8.2 Imports

- **Always use explicit imports.** No star imports (`import foo.*`).
- **Never use fully-qualified names inline** in function bodies.
  Move every reference to a top-level `import` statement.
- Sort imports alphabetically; Android Studio / ktlint default ordering.

### 8.3 Constants

- Extract **all magic numbers** to `private const val` (primitives) or
  `private val` (Compose `Dp`, `Color`, etc.) at file scope.
- Use SCREAMING_SNAKE_CASE: `ZOOM_MIN`, `CONTROL_BUTTON_SIZE`, `OVERLAY_TIMEOUT_MS`.
- File-scoped color constants **must be prefixed** with a 2–3 letter screen/feature abbreviation to avoid cross-file collisions:
  ```kotlin
  // GlobalSettingsScreen.kt
  private val GS_BG = Color(0xFF121212)
  private val GS_SURFACE = Color(0xFF1C1C1E)
  ```

### 8.4 Logging

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

### 8.5 API-Level Branching

- When calling APIs that changed signature across SDK versions, use
  `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.XYZ)` branching with
  `@Suppress("DEPRECATION")` on the legacy branch — never silently call the
  deprecated path without the annotation.

```kotlin
@Suppress("DEPRECATION")
val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    intent?.getParcelableExtra("DATA", Intent::class.java)
} else {
    intent?.getParcelableExtra("DATA")
}
```

---

## 9 Jetpack Compose Rules

### 9.1 Side Effects & LaunchedEffect

- **Never use rapidly-changing Compose state as a `LaunchedEffect` key.**
  If you need to react to animation values or frequently-updating state,
  use `snapshotFlow { ... }.collectLatest { }` inside a `LaunchedEffect(Unit)`.

```kotlin
// ✅ Correct – single launch, reactive collection
LaunchedEffect(Unit) {
    snapshotFlow { animScale.value }
        .collectLatest { scale -> manager.setScale(scale) }
}

// ❌ Wrong – restarts coroutine on every animation frame
LaunchedEffect(animScale.value) {
    manager.setScale(animScale.value)
}
```

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

### 9.2 String Resources

- **All user-visible strings** must live in `res/values/strings.xml`.
- Composables: `stringResource(R.string.key)`.
- Services / non-Compose code: `getString(R.string.key)` or `context.getString(...)`.
- Format args: `stringResource(R.string.key, arg1, arg2)` — not string concatenation.
- Internal defaults in singletons that are immediately overwritten by
  callbacks may remain as literals.
- All strings must be translated to all supported languages.

### 9.3 Accessibility

- Every `Icon` and `Image` must have a meaningful `contentDescription`
  backed by a string resource, or `null` if purely decorative.

### 9.4 Shared UI Components

- Reusable Composables (overlay controls, auto-hide timers) belong in `ui/`.
- Do not duplicate overlay code across screens. Use
  shared overlay tools and `AppStateManager.overlayVisible` / `triggerOverlay()`.

### 9.5 Collecting StateFlows

| Context                                                | Pattern                                                                   |
| ------------------------------------------------------ | ------------------------------------------------------------------------- |
| Inside a `@Composable`                                 | `.collectAsState()` — converts to Compose `State`, triggers recomposition |
| Inside a `Service`, `Presentation`, or coroutine scope | `.collect { }` — imperative side-effect, no recomposition involved        |

Never call `.collectAsState()` outside a Composable; never call raw `.collect {}` inside a Composable when the result drives UI.

---

## 10 Coroutines & Lifecycle

### 10.1 CoroutineScope in Services / Presentation

- Use **`SupervisorJob()`**, never bare `Job()`, for class-level
  `CoroutineScope` instances in `Service` or `Presentation` subclasses.
  This prevents a single child failure from cancelling unrelated siblings.
- **Cancel the scope** in `onDestroy()` / teardown:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
}
```

### 10.2 No Duplicate Scopes

- Never create a local `CoroutineScope` inside `onStartCommand()` or
  similar lifecycle methods. Reuse the class-level scope.

### 10.3 Naming

- Avoid naming your own methods identically to framework methods.
  Example: rename a helper `startForegroundNotification()` instead of
  `startForegroundService()` to prevent shadowing `Context.startForegroundService()`.

### 10.4 Combining Multiple StateFlows

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

## 11 Android Manifest & Permissions

- Only declare components that **actually exist** as classes. A missing
  class behind a `<service>` or `<receiver>` declaration causes a runtime
  crash on some OEMs.
- Required permissions for this project:
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_MEDIA_PROJECTION`
  - `POST_NOTIFICATIONS`

---

## 12 Resource & Hardware Lifecycle

### 12.1 Teardown Order in Services & Presentations
- **Cancel Scopes First:** In `onDestroy()` or cleanup callbacks, always cancel the coroutine scope _before_ releasing hardware handles or dismissing windows. This ensures in-flight async tasks do not race against resource deallocation or throw illegal state exceptions.
- **Teardown Sequence:**
  ```kotlin
  override fun onDestroy() {
      super.onDestroy()
      scope.cancel()          // 1. Stop all async tasks first
      virtualDisplay?.release() // 2. Release hardware handles
      mediaProjection?.stop()
      mirrorPresentation?.dismiss() // 3. Tear down presentation windows
  }
  ```

### 12.2 Multi-Display Activity Launching
- When starting any `Activity` intended for the primary handheld screen (e.g., global settings, Privileged Mode setup, or media consent prompts) from a secondary-screen context, you **must** explicitly configure `ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`. 
- By default, Android activity launches inherit the display ID of the invoking context, which would cause primary-screen activities to open incorrectly on the secondary display.
  ```kotlin
  val options = ActivityOptions.makeBasic().apply {
      launchDisplayId = Display.DEFAULT_DISPLAY
  }
  startActivity(intent, options.toBundle())
  ```

### 12.3 Window Presentation Lifecycle
- **Toggle Visibility, Preserve Resources:** Use `Presentation.hide()` and `Presentation.show()` for mode switching during an active session to temporarily hide overlay windows without destroying their backing resources.
- Only invoke `Presentation.dismiss()` in `onDestroy()` or deep service teardowns, as it permanently destroys the window context and backing lifecycle.
- **Z-Order Layering:** Any `SurfaceView` receiving hardware rendering streams (e.g., `VirtualDisplay` outputs) **must** call `setZOrderMediaOverlay(true)` to ensure that Android's Hardware Composer layers it correctly on top of window backgrounds instead of rendering it behind them.

### 12.4 Feature-Specific Architectures & Protocols
For feature-specific, low-level technical configurations, do **not** add ad-hoc rules to this document. Instead, consult the dedicated `FEATURE.md` files which serve as the canonical technical specifications:
- **Screen Capture & Mirroring Server:** See [docs/features/mirror/FEATURE.md](docs/features/mirror/FEATURE.md) for detail on privileged socket controls, `app_process` dex servers, and generation race-guards.
- **Native Key Injection & uinput:** See [docs/features/keyboard/FEATURE.md](docs/features/keyboard/FEATURE.md) for the stdin commands (`KD`/`KU`), event classification filters, and the `1..255` keyboard keycode limits.
- **Native Touch Injection & evdev:** See [docs/features/touchpad/FEATURE.md](docs/features/touchpad/FEATURE.md) for touchscreen event nodes (`/dev/input/event6`), absolute coordinate landscape-inversion math, and relative touch-move coalescing queues.

---

## 13 Build & Dependencies

- All dependency versions live in `gradle/libs.versions.toml`.
  Never hardcode version strings in `build.gradle.kts`.
- `isMinifyEnabled = true` for release builds (R8 + resource shrinking).
  Keep rules live in `app/proguard-rules.pro`. When adding new components
  referenced by name from `AndroidManifest.xml`, by reflection, or by
  kotlinx.serialization, add explicit keep rules.

---

## 14 Git Conventions

- **Commit messages** follow [Conventional Commits](https://www.conventionalcommits.org/):
  `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`.
- Use a short imperative summary line, then a blank line, then bullet-point
  details for multi-topic commits.
- Keep commits atomic — one logical change per commit when feasible.

---

## 15 What NOT to Change

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
