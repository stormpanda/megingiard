# Megingiard Coding Guide — Detailed Rules & Examples

> This file holds the full rationale and code examples for the rules summarized in
> [`AGENTS.md`](../../AGENTS.md). Section numbers (§7–§16) are identical in both files, so
> references such as "§7.5" or "§8.4" resolve to the same rule in either document.
> §17 documents which rules the build enforces automatically.
> `AGENTS.md` is authoritative for *what* the rule is; this file explains *how* to apply it.
>
> Read the relevant section before editing code in that area (e.g. §9 before touching
> Composables, §7 before touching state singletons, §16 before styling UI).

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

### 7.4 Active Game Session Integrity

- **No synthetic fallback ROM paths:** When constructing an `ActiveGameSession`, `ActiveGameSession.romPath` MUST contain either an exact, verified file path/name (e.g. `Tactics Ogre (USA).iso` or `/storage/.../game.iso`) or `null` if the exact file path cannot be resolved directly from the emulator or SAF URI. Never populate `romPath` with synthetic, guessed, or fake fallback filenames (such as `"$derivedTitle.iso"`).

### 7.5 Zero-Heuristics & Strict Determinism Policy

No heuristics, fuzzy matching, guessing algorithms, loose substring deductions, synthetic fallbacks, or probabilistic approximations may be introduced anywhere in the codebase without the human operator's prior explicit approval.

- **Mandatory strict determinism:** Every state transition, app detection, package classification, path resolution, input routing, and configuration decision MUST be deterministic and backed by:
  1. **Canonical operating system APIs** (e.g. Android `PackageManager` intent resolution `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)`, official lifecycle callbacks, verified system services).
  2. **Explicit contracts & constants** (e.g. verified IPC contracts like `MegingiardIpcContract.GAMEFOCUS_PACKAGE`, explicit package constants, exact system package IDs).
  3. **Exact user configuration / verified data** (e.g. exact user-configured profile associations, exact filesystem paths confirmed to exist).
- **Prohibited patterns (non-exhaustive):**
  - ❌ **No substring guessing for roles or types:** Never use `packageName.contains("launcher")`, `packageName.contains("home")`, `packageName.contains("game")`, `title.contains("...")`, etc. to infer system roles or app categories. Substring checks cause false positives (e.g. `com.playrix.homescapes`, `net.kdt.pojavlaunch`, `com.ea.gp.homeworld`).
  - ❌ **No synthetic fallback data:** Never invent guessed or synthetic paths, filenames, or IDs (e.g. `"$title.iso"`) when exact data cannot be resolved (per §7.4).
  - ❌ **No heuristic fallback defaults:** If an entity, role, or state cannot be resolved with certainty through canonical APIs or exact data, return `null`, empty, or no-op.
- **Seeking permission for edge cases:** If a requirement genuinely lacks a canonical API and appears to require a heuristic, pause, explain the limitation to the user, propose the approach, and obtain explicit written approval before writing any heuristic code.

### 7.6 Persisted State Loading (Load-Once Guard)

Singletons that mirror a persisted file in memory (opt-out sets, registries, ID maps, custom names) MUST:

1. Load from disk **at most once per process**, guarded by a `@Volatile` `isXLoaded` flag that is checked and set inside the state lock.
2. Call the loader **before any in-memory mutation** (e.g. at the top of `setX` / `recordX`), so a later lazy load can never overwrite a fresh change.
3. Persist asynchronously from the in-memory snapshot (`persistScope` + `persistLock`); never re-read disk to "refresh" state while writes may be pending.
4. Reset the flag in `resetForTesting()`.

Re-reading disk on every reload or catalog refresh is a **High-severity** review finding: pending writes race with the reload and silently drop user choices.

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
- **No hardcoded hardware volume UUIDs:** Never hardcode specific MicroSD card volume UUID paths (such as `/storage/6914-318F/...`). Use `SafPathResolver.getStorageVolumeRoots()` to discover mounted storage locations dynamically.

### 8.4 Logging

> **Logging mandate: be generous.** The goal is that a single logcat capture at DEBUG level is sufficient to diagnose any bug — without needing a second run. When in doubt, log it.

- **Never use `android.util.Log` directly.** All log calls go through `AppLog` (`com.stormpanda.megingiard.AppLog`).
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

When calling APIs that changed signature across SDK versions, use
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

Never use rapidly-changing Compose state as a `LaunchedEffect` key. To react to
animation values or frequently-updating state, use `snapshotFlow { ... }.collectLatest { }`
inside a `LaunchedEffect(Unit)`.

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

`snapshotFlow` is in `androidx.compose.runtime`, **not** `kotlinx.coroutines`.

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

- **All user-visible strings** live in `res/values/strings.xml`, translated in every supported locale (currently `values-de` and `values-zh-rTW`). Parity is build-enforced (§17).
- Composables: `stringResource(R.string.key)`.
- Services / non-Compose code: `getString(R.string.key)` or `context.getString(...)`.
- Format args: `stringResource(R.string.key, arg1, arg2)` — not string concatenation.
- Internal defaults in singletons that are immediately overwritten by callbacks may remain as literals.

### 9.3 Accessibility

Every `Icon` and `Image` must have a meaningful `contentDescription` backed by a string
resource, or `null` if purely decorative.

### 9.4 Shared UI Components

- Reusable Composables (overlay controls, auto-hide timers) belong in `ui/`.
- Do not duplicate overlay code across screens. Use shared overlay tools and
  `AppStateManager.overlayVisible` / `triggerOverlay()`.

### 9.5 Collecting StateFlows

| Context                                                | Pattern                                                                   |
| ------------------------------------------------------ | ------------------------------------------------------------------------- |
| Inside a `@Composable`                                 | `.collectAsState()` — converts to Compose `State`, triggers recomposition |
| Inside a `Service`, `Presentation`, or coroutine scope | `.collect { }` — imperative side-effect, no recomposition involved        |

Never call `.collectAsState()` outside a Composable; never call raw `.collect {}` inside a Composable when the result drives UI.

### 9.6 Gamepad & Settings UI Conventions

- **No controller button prompts in Settings / Editor menus:** Controller button prompt glyphs (e.g. `GamePadGlyph` badges for A/B/X/Y or footer prompt bars) must never be displayed inside Megingiard Companion settings screens or MacroPad editor menus. Button prompt glyphs are reserved for Game Focus launcher UI.
- **In-place two-step confirmation for destructive actions:** Never launch modal window dialogs (`Dialog`, `AlertDialog`) from within overlay window contexts or editor menus (window type mismatch crashes on overlay contexts). Destructive actions (deleting profiles, layouts, buttons, macros) use `GamepadTwoStepConfirmCard`:
  1. Activating the item (Button A / touch click) changes its headline to a confirmation prompt (e.g. *"Really delete 'Profile'?"*) and changes the badge to *"Confirm"*.
  2. Activating again executes the action and resets state.
  3. Pressing Button B or navigating away (losing focus) cancels the confirmation state.
  4. Upon deletion, focus switches back to the primary selection item in the deck (e.g. profile choice card) and a toast confirms the deletion.

---

## 10 Coroutines & Lifecycle

### 10.1 CoroutineScope in Services / Presentation

- Use **`SupervisorJob()`**, never bare `Job()`, for class-level `CoroutineScope`
  instances in `Service` or `Presentation` subclasses. This prevents a single child
  failure from cancelling unrelated siblings.
- **Cancel the scope** in `onDestroy()` / teardown, following the order defined in §12.1:

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

override fun onDestroy() {
    super.onDestroy()
    scope.cancel()
    // … release hardware, then dismiss windows (§12.1)
}
```

### 10.2 No Duplicate Scopes

Never create a local `CoroutineScope` inside `onStartCommand()` or similar lifecycle
methods. Reuse the class-level scope.

### 10.3 Naming

Avoid naming your own methods identically to framework methods. Example: name a helper
`startForegroundNotification()` instead of `startForegroundService()` to prevent shadowing
`Context.startForegroundService()`.

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

- Only declare components that **actually exist** as classes. A missing class behind a
  `<service>` or `<receiver>` declaration causes a runtime crash on some OEMs.
- Required permissions for this project:
  - `FOREGROUND_SERVICE`
  - `FOREGROUND_SERVICE_MEDIA_PROJECTION`
  - `POST_NOTIFICATIONS`

---

## 12 Resource & Hardware Lifecycle

### 12.1 Teardown Order in Services & Presentations

Cancel the coroutine scope **before** releasing hardware handles or dismissing windows,
so in-flight async tasks never race against resource deallocation. This is the canonical
order used by the existing services (`ScreenCaptureService`, `MegingiardAccessibilityService`):

```kotlin
override fun onDestroy() {
    super.onDestroy()
    scope.cancel()                // 1. Stop all async tasks first
    virtualDisplay?.release()     // 2. Release hardware handles
    mediaProjection?.stop()
    mirrorPresentation?.dismiss() // 3. Tear down presentation windows
}
```

### 12.2 Multi-Display Activity Launching

When starting any `Activity` intended for the primary handheld screen (global settings,
Privileged Mode setup, media consent prompts) from a secondary-screen context, explicitly set
`ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`. By default, activity launches
inherit the display of the invoking context and would open on the secondary display.

```kotlin
val options = ActivityOptions.makeBasic().apply {
    launchDisplayId = Display.DEFAULT_DISPLAY
}
startActivity(intent, options.toBundle())
```

### 12.3 Window Presentation Lifecycle

- **Toggle visibility, preserve resources:** Use `Presentation.hide()` / `Presentation.show()` for mode switching during an active session.
- Only invoke `Presentation.dismiss()` in `onDestroy()` or deep service teardowns — it permanently destroys the window context and backing lifecycle.
- **Z-order layering:** Any `SurfaceView` receiving hardware rendering streams (e.g. `VirtualDisplay` output) **must** call `setZOrderMediaOverlay(true)` so the Hardware Composer layers it above window backgrounds.

### 12.4 Feature-Specific Architectures & Protocols

Do not add ad-hoc low-level rules here. The `FEATURE.md` files are the canonical specs:

- **Screen capture & mirroring server:** [mirror/FEATURE.md](../features/mirror/FEATURE.md) — privileged socket controls, `app_process` dex servers, generation race-guards.
- **Native key injection & uinput:** [keyboard/FEATURE.md](../features/keyboard/FEATURE.md) — stdin commands (`KD`/`KU`), event classification filters, `1..255` keycode limits.
- **Native touch injection & evdev:** [touchpad/FEATURE.md](../features/touchpad/FEATURE.md) — touchscreen event nodes (`/dev/input/event6`), landscape-inversion math, relative touch-move coalescing.

### 12.5 Input Injector Lifecycle

Input injector lifecycles are managed centrally by `InjectorLifecycleManager`: injectors are active
while the app is foregrounded and stopped on backgrounding or while the Privd setup wizard IME is
active. Screens never start/stop injectors locally (e.g. in a `DisposableEffect`).

---

## 13 Build & Dependencies

- All dependency versions live in `gradle/libs.versions.toml`. Never hardcode version strings in `build.gradle.kts`.
- `isMinifyEnabled = true` for release builds (R8 + resource shrinking). Keep rules live in the per-module `proguard-rules.pro` files (`companion/ui/`, `companion/domain/`, `gamefocus/ui/`). When adding components referenced by name from `AndroidManifest.xml`, by reflection, or by kotlinx.serialization, add explicit keep rules to the owning module's file.

---

## 14 Git Conventions

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`.
- Short imperative summary line, blank line, then bullet-point details for multi-topic commits.
- Keep commits atomic — one logical change per commit when feasible.

---

## 15 What NOT to Change

1. **`minSdk = 33`** — the device ships with this API level.
2. **`targetSdk = 35`** — keep in sync with `compileSdk`.
3. **All existing user-facing functionality** must be preserved across refactors. No feature removals without explicit human approval.
4. **Hardware rendering pipeline** (`MediaProjection` → `VirtualDisplay` → `SurfaceView` inside `Presentation`) — the only architecture that achieves zero-latency DRM-free mirroring on the AYN Thor.
5. **Synthetic LifecycleOwner in Presentation** — required for Compose inside the background-service window.
6. **Companion autonomy** — see §6.1.

---

## 16 Design System Rules

These rules encode the unified design system. Follow them when writing or editing any Composable.
See also [theming/FEATURE.md](../features/theming/FEATURE.md).

### 16.1 Typography — never inline `fontSize`

All text sizing uses `MaterialTheme.typography.*`. Inline `fontSize = XX.sp` in Composables
(outside `AppTheme.kt`) is forbidden.

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

**Allowed exceptions** (must be commented and declared as prefixed file-scope constants, e.g.
`KC_LABEL_WIDE_FONT_SIZE` in `KeyboardKeyCap.kt`):

- Computed/data-driven sizes: `(11 * btn.buttonSize.cols).sp` (button label scaled by size)
- Conditional key sizes: `if (widthWeight >= KC_WIDE_KEY_WEIGHT) KC_LABEL_WIDE_FONT_SIZE else KC_LABEL_FONT_SIZE`
- Internal renderers: `MaterialSymbols` size→sp conversion
- Intentionally tiny labels below 11sp (e.g. key-cap super labels at 8sp)

When only the size of a token is wanted (custom weight kept), pass `style = MaterialTheme.typography.<token>`
and keep the explicit `fontWeight` — it overrides the style's weight.

### 16.2 Colors — never hardcode `Color(0xFF...)`

All colors in Composables come from:

- `LocalAppColors.current.<token>` — app-defined semantic colors (accent, surface, error, etc.)
- `MaterialTheme.colorScheme.<token>` — M3 semantic colors (primary, onPrimary, etc.)
- `AppPalettes.<token>` (`:shared:ui`) — fixed, theme-independent colors (preset swatches,
  hardware-mimicking colors, data-visualisation palettes, status probes)

Hardcoded `Color(0xFF...)` literals in screen/composable code are forbidden. A new fixed color
goes into `AppPalettes.kt` as a named token; screens may alias it with a prefixed file-scope
constant (`private val SS_BG_COLOR = AppPalettes.polaroidFrame`).

Key token mapping:

| Value | Token |
|---|---|
| `Color(0xFFCF6679)` / `Color(0xFFB00020)` | `LocalAppColors.current.error` |
| `Color(0xFFFF9800)` (gamepad orange) | `LocalAppColors.current.actionColorGamepad` |
| `Color(0xFF2196F3)` (system blue) | `LocalAppColors.current.actionColorSystem` |

### 16.3 Spacing — prefer named constants over bare `XX.dp`

Extract dimension values to `private val` constants at file scope using SCREAMING_SNAKE_CASE
with a 2–3 letter feature prefix (e.g. `GS_SECTION_PADDING = 16.dp`). Bare magic `XX.dp`
inline in Composable code is a code smell.

### 16.4 M3 Component Colors — do not override what the ColorScheme provides

Do not pass manual `colors =` overrides to M3 components when the `ColorScheme` already handles them:

```kotlin
// ❌ WRONG — override defeats the design system
Switch(
    colors = SwitchDefaults.colors(checkedTrackColor = accentColor)
)

// ✅ CORRECT — let MaterialTheme.colorScheme.primary drive it
Switch(checked = ..., onCheckedChange = ...)
```

Applies to `Switch`, `Slider`, `Checkbox`, `RadioButton`, `OutlinedTextField`
(border colors may be overridden via `colors =` for contextual accent).

### 16.5 New Composable Parameters — no `accentColor: Color` proliferation

Do not add `accentColor: Color` as a new Composable parameter. Read accent from
`LocalAppColors.current.accent` or `MaterialTheme.colorScheme.primary` directly.
Existing `accentColor` parameters in older Composables may remain until a future refactor.

---

## 17 Build-Enforced Rules (`verifyArchitectureRules`)

The mechanically checkable rules above are enforced by the `verifyArchitectureRules` Gradle task
(`build-logic/src/main/kotlin/ArchitectureRules.kt`, tests in `build-logic/src/test/`). It runs
automatically before every `compile*Kotlin` task and as part of `./scripts/verify.sh`.
Comment lines (`//`, `*`, `/*`) are ignored.

| Rule ID | Scope | Enforces |
| ------- | ----- | -------- |
| `STAR_IMPORT` | all sources | §8.2 no star imports |
| `CORE_ANDROID_IMPORT` | `shared/core` | §6 no `android.*` / `androidx.*` in the pure JVM module |
| `DOMAIN_UI_IMPORT` | `*domain/src` | §6 no `androidx.compose.*` / `android.view.*` in domain modules |
| `ANDROID_LOG` | all sources except `AppLog.kt` | §8.4 `AppLog` only |
| `ENUM_VALUES` | `src/main` | §8.1 `enum.entries`, not `values()` |
| `JAVA_MATH` | `src/main` | §8.1 `kotlin.math`, not `java.lang.Math` |
| `BARE_JOB` | `src/main` | §10.1 `CoroutineScope(SupervisorJob() …)`, not `Job()` |
| `VOLUME_UUID_PATH` | `src/main` | §8.3 no hardcoded `/storage/XXXX-XXXX` paths |
| `PUBLIC_MUTABLE_STATE_FLOW` | `src/main`, top-level / first-level members | §7.1 `MutableStateFlow` must be `private` |
| `HEURISTIC_SUBSTRING` | `src/main` | §7.5 no `.contains("launcher" / "home" / "game")` role guessing |
| `INLINE_FONT_SIZE` | `src/main` except `AppTheme.kt` / `AppColors.kt` / `AppPalettes.kt` | §16.1 typography tokens |
| `HARDCODED_COLOR` | `src/main` except `AppTheme.kt` / `AppColors.kt` / `AppPalettes.kt` | §16.2 color tokens |
| `UNUSED_TAG` | `src/main` | §8.4 a declared `TAG` must be used (add logs, don't delete it) |
| `MISSING_TRANSLATION` | `src/main/res` | §9.2 every translatable default string exists in every locale |
| `STALE_TRANSLATION` | `src/main/res` | §9.2 no locale string without a default string |

### Baselines

Pre-existing violations are recorded per module in `<module>/architecture-baseline.txt`
(`RULE_ID|path|max_count`); a missing file means zero tolerated violations. The build fails only
when a file's count for a rule **exceeds** its baseline, and warns when a baseline can be lowered.
All original baselines have been burned down — currently no module has one, so any hit fails.

- **Agents never add or raise baseline entries.** Fix the code instead.
- When you fix an old violation, lower or delete its entry.
- The operator may regenerate all baselines after a deliberate decision:
  `./gradlew verifyArchitectureRules -PupdateArchitectureBaseline`.

### Adding a rule

Add the rule ID, regex, and check to `ArchitectureRules.kt`, cover it in `ArchitectureRulesTest.kt`,
add a row to the table above, and reference it from the matching AGENTS.md section. Rules must be
exact patterns without false positives on current code — otherwise baseline the existing hits.
