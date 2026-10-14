# AGENTS.md – AI Agent Coding Guidelines for Megingiard

> Mandatory rules for AI coding agents unless the human operator explicitly overrides them.
>
> This file states every rule compactly and must stay **below 20 KB** so it is loaded in full
> into every agent session. Rationale and code examples for §7–§16 live in
> [`docs/agents/CODING_GUIDE.md`](docs/agents/CODING_GUIDE.md) under the **same section
> numbers** — read the matching section before editing code in that area. Put new long
> examples there, not here.

---

## 1 Project Identity

| Key               | Value |
| ----------------- | ----- |
| **Package**       | `com.stormpanda.megingiard` |
| **Language**      | Kotlin 2.0+. Only `:mirrorserver` is Java (shell-UID DEX for `app_process`). No new Java modules. |
| **UI Framework**  | Jetpack Compose (Material 3, BOM-managed) |
| **Min SDK**       | 33 — **must not be raised** |
| **Target SDK**    | 35 |
| **Build System**  | Gradle (Kotlin DSL), version catalog `gradle/libs.versions.toml` |
| **Device Target** | AYN Thor dual-screen handheld (Display 0 = top, Display 4 = bottom). **Dual-screen only — single-screen is permanently unsupported.** |

---

## 2 Documentation Map

| Document | Purpose |
| -------- | ------- |
| `README.md` / `LICENSE` / `CONTRIBUTING.md` | Overview · Source-Available License 1.0 · contribution & licensing rules |
| `SECURITY_CONCEPT.md` | Threat model, hardening layers, links to detailed docs |
| `docs/agents/CODING_GUIDE.md` | **Full rules & examples for §7–§16** |
| `docs/ARCHITECTURE.md` | Architecture overview & key design decisions |
| `docs/BUILD_NATIVE.md` | Native C binaries: build & protocol specs |
| `docs/GAMEPAD_NAVIGATION.md` | Gamepad focus traversal, isolation & recovery |
| `docs/MANUAL_VERIFICATION.md` | Manual regression tests & PR sanity checklists |
| `docs/REQUIREMENTS.md` | Functional & non-functional requirements |
| `docs/audit/README.md` | Codebase audit reports & remediation tracking |
| `docs/features/FEATURE_TEMPLATE.md` | Template for new feature docs |
| `docs/features/config/FEATURE.md` | Config Export/Import (`.mgrd` backup, profile sharing) |
| `docs/features/gamefocus/FEATURE.md` | Game Focus launcher variant — split, see its doc map |
| `docs/features/help/FEATURE.md` | In-App Help (HelpModal infrastructure & content) |
| `docs/features/integration-api/FEATURE.md` | Integration API (clients, state sharing, profile query) |
| `docs/features/keyboard/FEATURE.md` | Virtual Keyboard |
| `docs/features/log-report/FEATURE.md` | Log Report Export |
| `docs/features/macropad/FEATURE.md` | MacroPad (profiles, layouts, macros) — split, see its doc map |
| `docs/features/mirror/FEATURE.md` | Screen Mirror — split, see its doc map |
| `docs/features/privileged-mode/FEATURE.md` | Privileged Mode (daemon, bootstrap, feature flags) — split |
| `docs/features/quickmenu/FEATURE.md` | Quick Menu Bar & Quick Menu |
| `docs/features/theming/FEATURE.md` | Design System (AppColors, Typography, AppDimens) |
| `docs/features/touchpad/FEATURE.md` | Virtual Touchpad |
| `docs/features/updates/FEATURE.md` | Update Check & Release Launcher |

- **Documentation language is English only** — every `*.md` file in this repository.
- **New features:** create `docs/features/<feature>/FEATURE.md` from the template first, then add a row to the table above.
- **Size cap:** every `*.md` file < 40 KB so it fits one agent read (`verify.sh`-enforced). Split larger specs into sibling part files listed in a document map at the top of `FEATURE.md`; doc sync (§5) covers all parts.
- **Doc sync after every change:** see §5.

---

## 3 Checklist for Every Change

### 3.1 Permitted commands

| Purpose | Command | Notes |
| ------- | ------- | ----- |
| **Quality gate** | `./scripts/verify.sh` | AGENTS.md size + architecture rules (CODING_GUIDE §17) + all unit tests. Loop until green. |
| Unit tests only | `./scripts/run_unit_tests.sh` | Canonical test task list. Extra args forwarded to Gradle. |
| Compile check | `./gradlew compileDebugKotlin` or `./gradlew :companion:ui:assembleDebug` | Also runs `verifyArchitectureRules`. |
| Coverage | `./gradlew koverHtmlReport` / `koverLog` / `koverXmlReport` | `build/reports/kover/html/index.html` |
| Native daemon rebuild | `./scripts/build_megingiard_privd.sh` | Mandatory after editing `megingiard_privd.c` |

- These are the **only** Gradle commands the agent may run. Gradle needs local TCP sockets → always `BypassSandbox: true`.
- Fix rule-verifier findings in code. Never add to or raise `architecture-baseline.txt` counts; lower them when you fix old violations.
- **No deployment** without explicit operator permission: no `installDebug`, no ADB deploy, no clearing app data.

### 3.2 Native binaries

- After modifying `companion/ui/src/main/cpp/megingiard_privd.c`, run `./scripts/build_megingiard_privd.sh` from the workspace root **before** proposing the commit message. Fix build errors before continuing.
- **Daemon versioning:** when changing the daemon or its protocol, increment `PRIVD_VERSION` in both `megingiard_privd.c` (`#define PRIVD_VERSION <N>`) and `shared/core/src/main/kotlin/com/stormpanda/megingiard/privd/PrivdConstants.kt` (`const val PRIVD_VERSION = <N>`), then rebuild.

### 3.3 Unit tests

After every implementation (feature, bug fix, refactor):

1. **Write new tests** for new pure logic (pure functions, data compilers, state machines, serialization round-trips).
2. **Update existing tests** whose behaviour or signature changed.
3. **Run `./scripts/verify.sh`** and report the result. Confirm new test classes ran via their `build/test-results/**/TEST-*.xml`.

- Placement: `:shared:core` → `shared/core/src/test/kotlin/`; other modules → `<module>/src/test/java/`. Prefer plain JUnit over Robolectric when possible.
- New `src/test` directory in a module → add its task to `scripts/run_unit_tests.sh` (the only place the task list lives).
- Failure unrelated to the change set → inspect its `TEST-*.xml`, re-run once, report as flaky (with test name), not as a regression.
- Logic untestable without major refactoring → state it as a follow-up task; never skip silently.

### 3.4 Auto code review

After completing an implementation and before proposing the commit message, run the `megingiard-code-review` skill on all modified files (grep sweeps + file-by-file checklist). Because this review is part of your own implementation task, **fix the findings directly** and list them in your summary. (A review the user requests on its own presents findings first — see the skill.)

### 3.5 Done checklist

Before marking a task as done, verify:

- [ ] `./scripts/verify.sh` passes (mechanical rules: CODING_GUIDE §17)
- [ ] `megingiard-code-review` pass executed on all modified files (§3.4)
- [ ] New/changed pure logic covered by tests; existing tests updated where behaviour changed (§3.3); Kover checked when required
- [ ] Native daemon rebuilt and `PRIVD_VERSION` bumped if C source changed (§3.2)
- [ ] Disk-backed state uses load-once guard (§7.6); bitmap recycling owned by the manager (§7.3)
- [ ] Zero-Heuristics respected — no fuzzy matching, synthetic fallbacks, or approximations without explicit approval (§7.5)
- [ ] No inline FQNs, no magic numbers (§8.2, §8.3); new files declare `TAG` and log per §8.4
- [ ] Deprecated API branches annotated `@Suppress("DEPRECATION")` (§8.5)
- [ ] Icons have `contentDescription` (§9.3); no fast-changing `LaunchedEffect` keys (§9.1)
- [ ] Modal dialogs / popups use `AppModalDialog` / `AppAlertDialog` or `rememberBezelBrush()`; destructive overlay actions use two-step confirmation (§9.6)
- [ ] Scopes cancelled in `onDestroy()` in the §12.1 order; new `Activity` launches set `setLaunchDisplayId()` (§12.2)
- [ ] `WindowOverlayLifecycleOwner.destroy()` called when an overlay view is removed; Service `onStartCommand` returns `START_NOT_STICKY`
- [ ] Input injectors managed only by `InjectorLifecycleManager` — no local start/stop in screen `DisposableEffect`s
- [ ] Companion bugs fixed only in Companion / shared modules — never via Game Focus (§6.1)
- [ ] Spacing uses prefixed constants (§16.3)
- [ ] `FEATURE.md`, HelpModal entries, onboarding, and strings in **every** locale updated (§5)

---

## 4 Commit Message Proposal

After every set of changes, propose a copy-paste-ready commit message (no placeholders) in its own code block, using Conventional Commits:

```
<type>: <short imperative summary>

- bullet describing change 1
- bullet describing change 2
```

- Run `git status` first. The message MUST cover **every** file it lists (staged and unstaged alike) — not just what you remember editing. Never include files already committed earlier in the conversation.
- Types: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`. Keep commits atomic when feasible (§14).

---

## 5 Documentation Sync After Changes

Applies to **every** change that affects behaviour, interface, or architecture — including bug fixes, refactors, and dependency updates:

1. Identify the owning `FEATURE.md` file(s) via the §2 table, read them, and fix any Functional Requirement or Technical Implementation text the change invalidated; document new behaviour.
2. Architecturally significant changes → also update `docs/ARCHITECTURE.md`.
3. Security-affecting changes → also update `SECURITY_CONCEPT.md` and the detailed doc owning the mechanism (`docs/ARCHITECTURE.md`, `docs/BUILD_NATIVE.md`, or the feature's `FEATURE.md`).
4. New features → new `FEATURE.md` from the template + row in §2.
5. **In-app help:** if user interaction changes (buttons, modes, settings, gestures), update the matching `HelpModal`, onboarding popups, and strings in every locale (§9.2). **Every settings option visible in the UI must have an explanation entry in its HelpModal.**
6. **Walkthroughs** / session summaries include a "Future Improvements" section listing code, design, or architecture improvements noticed during the work (even if unrelated).

---

## 6 Package Structure

10 feature-first Gradle modules:

| Module | Role |
| ------ | ---- |
| `:companion:ui` | Companion app UI (`com.stormpanda.megingiard`): Activities, ViewModels, Composables, Presentations |
| `:companion:domain` | Companion business logic, device managers, injection facades (Touchpad, MacroPad, Keyboard, Mirror, Privd) |
| `:gamefocus:ui` | Launcher app (`com.stormpanda.megingiard.gamefocus`): poster browsing, SteamGridDB artwork, theme sync |
| `:gamefocus:domain` | Launcher logic, ROM launchers (`RetroArchLauncher`, `GameNativeLauncher`) |
| `:shared:ui` | Design system (`AppColors`, `AppTheme`), `AppModalDialog`, overlay modifiers, `GamePadButton`, Material Symbols |
| `:shared:catalog` | App index, ROM scanning (`InstalledAppsManager`, `RomManager`, `DisplayDetector`, `RomLauncherRegistry`) |
| `:shared:media` | Artwork fetchers, HTTP, caches (`SteamGridDbClient`) |
| `:shared:session` | Active game detection (`EmulatorDetectionFunnel`, `*Detector`) |
| `:shared:core` | Pure JVM models, schemas, `AppLog`, constants, math (`ViewportMath`). **No Android dependencies.** |
| `:mirrorserver` | Java shell-UID DEX (`app_process`) for privileged mirroring |

- Packages inside modules are feature-centric: `macropad/`, `mirror/`, `keyboard/`, `input/`, `privd/`, `catalog/`, `media/`, `session/`, `ui/`.
- **Placement rule:** shared business logic → `:shared:catalog` / `:shared:media` / `:shared:session`; pure data types, math, logging → `:shared:core`; feature logic → `:companion:domain` / `:gamefocus:domain` (no Android UI or Compose imports); UI → `:companion:ui` / `:gamefocus:ui`.

### 6.1 Strict Decoupling: Megingiard Companion vs. Game Focus

> [!CAUTION]
> **Megingiard Companion is a fully standalone app with zero dependency on Game Focus.**
>
> - Companion (`:companion:*`, `:mirrorserver`, `:shared:*`) is the public product. Game Focus (`:gamefocus:*`) is **not released** and users are never required to use it — they may run any launcher (stock, Odin/Thor, Nova, Daijisho, ES-DE, Beacon, …) on the top screen.
> - **Never fix, partially fix, or mitigate a Companion bug by changing Game Focus.** Companion must be 100% correct on its own.
> - Companion must never assume Game Focus is installed, running, foregrounded, or managing top-screen window state. Inter-process integration (e.g. `MegingiardThemeProvider` theme sync) is optional and one-way: Companion is the authority, Game Focus a passive consumer.

---

## 7 State Management — details: [CODING_GUIDE §7](docs/agents/CODING_GUIDE.md#7-state-management)

- **7.1** State lives in `object` singletons (`AppStateManager`, `ScreenCaptureManager`). `MutableStateFlow` backing fields are always `private`; expose `val x: StateFlow<T> = _x.asStateFlow()`.
- **7.2** Same-module callers (Services, Listeners) mutate via `internal` update functions; Composables only read `StateFlow`.
- **7.3** `ScreenCaptureManager.setFrozenBitmap()` recycles the previous bitmap — never call `recycle()` at call sites. **Exception:** if the hand-off fails (e.g. `PixelCopy` non-SUCCESS), the call site recycles the bitmap it just created.
- **7.4** `ActiveGameSession.romPath` is an exact verified path/filename or `null` — never a synthetic name like `"$title.iso"`.
- **7.5 Zero-Heuristics (red line):** no heuristics, fuzzy matching, substring guessing (`packageName.contains("launcher")`), synthetic fallbacks, or approximations **without the operator's prior explicit approval**. Decisions must rest on canonical OS APIs, explicit contracts/constants, or exact user/verified data. If unresolvable, return `null` / empty / no-op. If a requirement seems to need a heuristic, stop, explain, propose, and wait for written approval.
- **7.6 Load-once guard:** singletons mirroring a persisted file load from disk at most once per process (`@Volatile isXLoaded` checked/set inside the state lock), call the loader before any mutation, persist asynchronously from the in-memory snapshot (`persistScope` + `persistLock`), never re-read disk to "refresh", and reset the flag in `resetForTesting()`. Re-reading disk on reload is a High-severity finding.

---

## 8 Kotlin Conventions — details: [CODING_GUIDE §8](docs/agents/CODING_GUIDE.md#8-kotlin-conventions)

- **8.1** `enum.entries` (never `.values()`); `kotlin.math.min/max` (never `java.lang.Math`); no anonymous destructuring of `Triple`/non-data classes in lambdas; `"$x"` not `"${x}"` unless accessing a property.
- **8.2** Explicit imports only — no star imports, no fully-qualified names inline in code bodies; default ktlint ordering.
- **8.3** No magic numbers: extract to file-scope `private const val` / `private val` in SCREAMING_SNAKE_CASE. File-scope UI constants carry a 2–3 letter feature prefix (`GS_SECTION_PADDING`). Never hardcode volume UUID paths like `/storage/6914-318F` — use `SafPathResolver.getStorageVolumeRoots()`.
- **8.4 Logging:** be generous — one DEBUG logcat capture should diagnose any bug. Only `AppLog` (never `android.util.Log`). Every file declares `private const val TAG = "ClassName"` (≤ 23 chars). Mandatory coverage: ERROR for unrecoverable failures, WARN for recoverable/fallback paths, INFO for lifecycle milestones, DEBUG for every state-singleton setter / CRUD op / screen lifecycle effect / modifier state transition / action dispatch. **Never log** per-frame, per-MOVE, key-repeat, velocity, or `updateXxxLive()` events. An unused `TAG` means logging is missing — add logs, don't delete the `TAG`.
- **8.5** SDK-dependent API calls use `Build.VERSION.SDK_INT` branching with `@Suppress("DEPRECATION")` on the legacy branch.

---

## 9 Jetpack Compose Rules — details: [CODING_GUIDE §9](docs/agents/CODING_GUIDE.md#9-jetpack-compose-rules)

- **9.1** Never key a `LaunchedEffect` on fast-changing state; use `LaunchedEffect(Unit) { snapshotFlow { … }.collectLatest { … } }`. `snapshotFlow` comes from `androidx.compose.runtime`. For non-Flow imperative state, poll with `while (true) { …; delay(POLL_MS) }` inside a keyed `LaunchedEffect`.
- **9.2** All user-visible strings in `strings.xml`, translated in every locale (`values-de`, `values-zh-rTW`; build-enforced). Use `stringResource(R.string.x, args)` — never concatenation.
- **9.3** Every `Icon` / `Image` has a string-resource `contentDescription`, or `null` if decorative.
- **9.4** Reusable Composables live in `ui/`; never duplicate overlay code — use `AppStateManager.overlayVisible` / `triggerOverlay()`.
- **9.5** `.collectAsState()` inside Composables only; `.collect {}` only in Services / Presentations / coroutine scopes.
- **9.6** No controller button-prompt glyphs in Companion settings or MacroPad editor menus (Game Focus only). Never open `Dialog` / `AlertDialog` from overlay windows or editor menus — destructive actions use in-place two-step confirmation (`GamepadTwoStepConfirmCard`).

---

## 10 Coroutines & Lifecycle — details: [CODING_GUIDE §10](docs/agents/CODING_GUIDE.md#10-coroutines--lifecycle)

- **10.1** Class-level scopes in Services / Presentations use `CoroutineScope(SupervisorJob() + …)` and are cancelled in `onDestroy()` (order: §12.1).
- **10.2** Never create a local `CoroutineScope` in `onStartCommand()` or other lifecycle methods — reuse the class scope.
- **10.3** Don't name methods like framework methods (use `startForegroundNotification()`, not `startForegroundService()`).
- **10.4** Depend on several `StateFlow`s via `combine()`, never nested `collect {}`.

---

## 11 Android Manifest & Permissions

Only declare components whose classes exist (missing classes crash on some OEMs). Required permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `POST_NOTIFICATIONS`.

---

## 12 Resource & Hardware Lifecycle — details: [CODING_GUIDE §12](docs/agents/CODING_GUIDE.md#12-resource--hardware-lifecycle)

- **12.1 Teardown order:** `super.onDestroy()` → `scope.cancel()` → release hardware (`VirtualDisplay`, `MediaProjection`) → dismiss windows/Presentations. The scope is always cancelled before resources are released.
- **12.2** Activities meant for the primary screen launched from a secondary-screen context set `ActivityOptions.makeBasic().apply { launchDisplayId = Display.DEFAULT_DISPLAY }`.
- **12.3** Use `Presentation.hide()` / `show()` for mode switches; `dismiss()` only in final teardown. `SurfaceView`s fed by `VirtualDisplay` call `setZOrderMediaOverlay(true)`.
- **12.4** Low-level protocol rules live in the feature specs (mirror, keyboard, touchpad `FEATURE.md`), not here.

---

## 13 Build & Dependencies

- All versions in `gradle/libs.versions.toml`; never hardcode versions in `build.gradle.kts`.
- Release builds use R8 (`isMinifyEnabled = true`) + resource shrinking. Keep rules live in each module's `proguard-rules.pro` (`companion/ui/`, `companion/domain/`, `gamefocus/ui/`); add keep rules for anything referenced from the manifest, by reflection, or by kotlinx.serialization.

---

## 14 Git Conventions

Conventional Commits; short imperative summary, blank line, bullet details; atomic commits when feasible.

---

## 15 What NOT to Change

1. `minSdk = 33` and `targetSdk = 35` (in sync with `compileSdk`).
2. Existing user-facing functionality — no removals without explicit operator approval.
3. The hardware mirroring pipeline (`MediaProjection` → `VirtualDisplay` → `SurfaceView` inside `Presentation`).
4. The synthetic `LifecycleOwner` in `Presentation` (required for Compose in the service window).
5. Companion autonomy from Game Focus (§6.1).

---

## 16 Design System Rules — details: [CODING_GUIDE §16](docs/agents/CODING_GUIDE.md#16-design-system-rules)

- **16.1** Never inline `fontSize = XX.sp`; use `MaterialTheme.typography.*` (token table in CODING_GUIDE). Documented, commented exceptions only.
- **16.2** Never hardcode `Color(0xFF…)` in screen/Composable code; use `LocalAppColors.current.*` or `MaterialTheme.colorScheme.*`.
- **16.3** No bare `XX.dp` in Composables; use prefixed file-scope constants.
- **16.4** Don't pass `colors =` overrides to M3 `Switch` / `Slider` / `Checkbox` / `RadioButton` / `OutlinedTextField` when the ColorScheme already covers it (text-field border accents are OK).
- **16.5** Don't add new `accentColor: Color` parameters; read `LocalAppColors.current.accent` or `MaterialTheme.colorScheme.primary`.
