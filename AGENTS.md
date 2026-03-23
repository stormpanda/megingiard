# AGENTS.md – AI Agent Coding Guidelines for Megingiard

> This file instructs AI coding agents (GitHub Copilot, Cursor, Cli### 5.4 Logging

- \*\*No `Log.d` / `Log### 4.3 Bitmap Lifecycle

`ScreenCaptureManager.setFrozenBitmap(bitmap)` **always calls `recycle()` on the
previous bitmap** before assigning the new one. Never call `recycle()` at call
sites — the## 13 Checklist for Every Change

Before marking a task as done, verify:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] No FQN references inline — all moved to imports
- [ ] No magic numbers — all extracted to named constants
- [ ] No `Log.d` / `Log.v` in committed code
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
- [ ] Zero compiler errors confirmed via IDE or `./gradlew compileDebugKotlin`he lifecycle.

**Exception:** If a `Bitmap` was just created but the operation that was meant to
hand it to the manager fails (e.g. `PixelCopy` returns a non-SUCCESS result), the
local call site **must** recycle it immediately, since the manager never received it.

````kotlin
PixelCopy.request(sv, bitmap, { result ->
    if (result == PixelCopy.SUCCESS) {
        ScreenCaptureManager.setFrozenBitmap(bitmap) // manager takes ownership
    } else {
        bitmap.recycle() // manager never got it, local cleanup required
    }
}, Handler(Looper.getMainLooper()))
```alls in committed code.** Remove debug logging
  before committing. `Log.w` / `L### 9.2  VirtualDisplay / MediaProjection

- Detach `VirtualDisplay.surface = null` to freeze; reassign to resume.
  Never recreate the VirtualDisplay to toggle freeze.
- Use `Presentation.hide()` / `Presentation.show()` for mode switching;
  never `dismiss()`, which destroys the window permanently.
- **Exception:** In `Service.onDestroy()` (full teardown), calling `dismiss()`
  is correct — the process is being destroyed anyway. The `hide()` vs `dismiss()`
  rule only applies to in-session mode switching.

### 9.3  SurfaceView Layer Order

- The `SurfaceView` that receives the `VirtualDisplay` output **must** call
  `setZOrderMediaOverlay(true)`. Without it, the hardware buffer renders behind
  the window background, producing a black screen.
- The `ComposeView` overlay is then layered on top of the `SurfaceView` by
  standard `FrameLayout` z-ordering.

### 9.4  Foreground Service

- Use `START_NOT_STICKY` as the return value in `onStartCommand()`.
  The service must not be auto-restarted by the system after being killed —
  re-acquiring `MediaProjection` requires a fresh user consent.
- Always call `startForeground()` / `startForegroundNotification()` before any
  `MediaProjection` work starts to avoid ANR on API 29+.

### 9.5  Multi-Display Activity Launching

- When launching an `Activity` that must appear on the primary screen
  (e.g. `CaptureRequestActivity`), use `ActivityOptions.setLaunchDisplayId(Display.DEFAULT_DISPLAY)`.
  Without this, the activity inherits the display of the calling context,
  which on the AYN Thor is the secondary display.

```kotlin
val options = ActivityOptions.makeBasic()
options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
startActivity(intent, options.toBundle())
````

### 9.6 MirrorPresentationLifecycleOwner Teardown

- `MirrorPresentationLifecycleOwner.destroy()` **must** be called in the
  Presentation's `setOnDismissListener`. It fires the `ON_DESTROY` lifecycle
  event that cleans up all Compose state registered against the owner.

````kotlin
setOnDismissListener {
    scope.cancel()
    lifecycleOwner.destroy()
}
```for genuine runtime warnings only.
- `ScreenCaptureService` and `MirrorPresentation` currently retain some `Log.d`
  calls for hardware pipeline diagnostics — these are a known technical debt item
  and should be cleaned up before any production release.

### 5.5  API-Level Branching

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
```etc.) on the
> conventions, patterns, and constraints that govern this project. Treat every rule
> as mandatory unless the human operator explicitly overrides it.

---

## 1 Project Identity

| Key               | Value                                                     |
| ----------------- | --------------------------------------------------------- |
| **Package**       | `com.stormpanda.megingiard`                               |
| **Language**      | Kotlin 2.0+ (no Java files)                               |
| **UI Framework**  | Jetpack Compose (Material 3, BOM-managed)                 |
| **Min SDK**       | 26 — **must not be raised**                               |
| **Target SDK**    | 35                                                        |
| **Build System**  | Gradle (Kotlin DSL), version catalog `libs.versions.toml` |
| **Device Target** | AYN Thor dual-screen Android handheld                     |

---

## 2 Documentation Map

| Document                  | Purpose                                           |
| ------------------------- | ------------------------------------------------- |
| `README.md`               | Project overview, feature list, quick links       |
| `PRD.md`                  | Product requirements (German, authoritative)      |
| `docs/REQUIREMENTS.md`    | Detailed functional & non-functional requirements |
| `docs/ARCHITECTURE.md`    | Hardware-level architecture decisions & rationale |
| `AGENTS.md` _(this file)_ | AI agent coding conventions & constraints         |

---

## 3 Package Structure

````

com.stormpanda.megingiard
├── AppStateManager.kt # Global app-level state (mode, lifecycle flags)
├── CaptureRequestActivity.kt # MediaProjection consent dialog (transparent Activity)
├── MainActivity.kt # Entry point, permission checks, display detection
├── MainAppScreen.kt # Top-level Composable (Crossfade + carousel overlay)
├── media/
│ ├── MegingiardNotificationListener.kt # NotificationListenerService + MediaState
│ └── MediaScreen.kt # Media dashboard Composable
├── mirror/
│ ├── MirrorPresentation.kt # android.app.Presentation on secondary display
│ ├── MirrorPresentationLifecycleOwner.kt # Synthetic LifecycleOwner for Compose-in-Presentation
│ ├── MirrorScreen.kt # Mirror Composable (pan/zoom/freeze controls)
│ ├── ScreenCaptureManager.kt # Mirror state flows (scale, offset, freeze, etc.)
│ └── ScreenCaptureService.kt # Foreground Service managing MediaProjection
└── ui/
└── CarouselOverlay.kt # Shared overlay components (auto-hide, chevron nav)

````

**Rule:** New feature modules get their own sub-package. Shared UI components belong in `ui/`.

---

## 4 State Management

### 4.1 Singleton State Holders

State is managed by **`object` singletons** (`AppStateManager`, `ScreenCaptureManager`, `MediaState`) that expose **read-only `StateFlow`** and keep all `MutableStateFlow` backing fields **`private`**.

```kotlin
// ✅ Correct pattern
object FooManager {
    private val _bar = MutableStateFlow(0)
    val bar: StateFlow<Int> = _bar.asStateFlow()

    fun setBar(value: Int) { _bar.value = value }
}

// ❌ Never expose MutableStateFlow
object FooManager {
    val bar = MutableStateFlow(0)   // WRONG
}
````

### 4.2 Visibility Rules

| Layer                           | Access to `MutableStateFlow` |
| ------------------------------- | ---------------------------- |
| Owning singleton                | `private` backing field      |
| Same module (Service, Listener) | `internal` update functions  |
| Composable / UI                 | Read-only `StateFlow` only   |

`MediaState.activeController` is `internal`; UI accesses it via the read-only
extension property `MediaState.controller`.

### 4.3 Bitmap Lifecycle

`ScreenCaptureManager.setFrozenBitmap(bitmap)` **always calls `recycle()` on the
previous bitmap** before assigning the new one. Never call `recycle()` at call
sites — the manager owns the lifecycle.

---

## 5 Kotlin Conventions

### 5.1 Language Level

- Use `AppMode.entries` (Kotlin 1.9+), never `AppMode.values()`.
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

### 5.4 Logging

- **No `Log.d` / `Log.v` calls in committed code.** Remove debug logging
  before committing. `Log.w` / `Log.e` for genuine runtime warnings only.

---

## 6 Jetpack Compose Rules

### 6.1 Side Effects & LaunchedEffect

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

### 6.2 String Resources

- **All user-visible strings** must live in `res/values/strings.xml`.
- Composables: `stringResource(R.string.key)`.
- Services / non-Compose code: `getString(R.string.key)` or `context.getString(...)`.
- Format args: `stringResource(R.string.key, arg1, arg2)` — not string concatenation.
- Internal defaults in singletons that are immediately overwritten by
  service callbacks (e.g. `"No Media"`) may remain as literals.

### 6.3 Accessibility

- Every `Icon` and `Image` must have a meaningful `contentDescription`
  backed by a string resource, or `null` if purely decorative.

### 6.4 Shared UI Components

- Reusable Composables (overlay controls, auto-hide timers) belong in `ui/`.
- Do not duplicate overlay or carousel code across screens. Use
  `CarouselOverlay` and `rememberAutoHideState()`.

---

## 7 Coroutines & Lifecycle

### 7.1 CoroutineScope in Services / Presentation

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

### 7.2 No Duplicate Scopes

- Never create a local `CoroutineScope` inside `onStartCommand()` or
  similar lifecycle methods. Reuse the class-level scope.

### 7.3 Naming

- Avoid naming your own methods identically to framework methods.
  Example: rename a helper `startForegroundNotification()` instead of
  `startForegroundService()` to prevent shadowing `Context.startForegroundService()`.

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

---

## 10 Build & Dependencies

- All dependency versions live in `gradle/libs.versions.toml`.
  Never hardcode version strings in `build.gradle.kts`.
- `isMinifyEnabled = false` for now (release builds).
  When enabling R8, add keep rules for `ScreenCaptureService` and
  `MegingiardNotificationListener` (referenced by the system via manifest).

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

1. **`minSdk = 26`** — the device ships with this API level.
2. **`targetSdk = 35`** — keep in sync with `compileSdk`.
3. **All existing user-facing functionality** must be preserved across
   refactors. No feature removals without explicit human approval.
4. **Hardware rendering pipeline** (`MediaProjection` → `VirtualDisplay` →
   `SurfaceView` inside `Presentation`) — this is the only architecture
   that achieves zero-latency DRM-free mirroring on the AYN Thor.
5. **Synthetic LifecycleOwner in Presentation** — required for Compose
   inside the background-service window.

---

## 13 Checklist for Every Change

Before marking a task as done, verify:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] No FQN references inline — all moved to imports
- [ ] No magic numbers — all extracted to named constants
- [ ] No `Log.d` / `Log.v` in committed code
- [ ] All user-visible strings in `strings.xml`
- [ ] All Icons have `contentDescription` (string resource or `null`)
- [ ] `SupervisorJob()` used (not `Job()`) for class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] Bitmap recycling handled by the manager, not call sites
- [ ] `snapshotFlow` imported from `androidx.compose.runtime`
- [ ] Zero compiler errors confirmed via IDE or `./gradlew compileDebugKotlin`
