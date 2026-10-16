# Megingiard Complexity & Simplification Audits — Master Index

> **Template:** [`docs/audits/AUDIT_TEMPLATE.md`](AUDIT_TEMPLATE.md)
> **Scope:** All 13 features documented under [`docs/features/`](../features/) across `:companion:ui`, `:companion:domain`, `:shared:ui`, `:shared:catalog`, `:shared:media`, `:shared:session`, `:shared:core`, `:mirrorserver`, `:gamefocus:ui`, `:gamefocus:domain`, and `megingiard_privd.c`.
> **Goal:** Reduce accidental complexity, vestigial ghost layers, split-brain state, defensive paranoia, and UI/concurrency boilerplate while preserving 100% of user-facing functionality and hardware robustness.
> **Overall Status:** Completed (244 / 244 findings implemented, verified via `./scripts/verify.sh`, and committed individually)

---

## 1. Per-Feature Audit Map & Scorecard

| # | Feature | Audit Report | Feature Spec | ID Prefix | Findings | Est. Removable LOC | Impact | Status |
| :-: | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| 1 | **MacroPad** | [`macropad.md`](macropad.md) | [`features/macropad/FEATURE.md`](../features/macropad/FEATURE.md) | `MP` | 22 / 22 | ~1,935 | High | Completed |
| 2 | **Screen Mirror** | [`mirror.md`](mirror.md) | [`features/mirror/FEATURE.md`](../features/mirror/FEATURE.md) | `MR` | 19 / 19 | ~1,774 | High | Completed |
| 3 | **Quick Menu & App Shell** | [`quickmenu.md`](quickmenu.md) | [`features/quickmenu/FEATURE.md`](../features/quickmenu/FEATURE.md) | `QM` | 21 / 21 | ~1,660 | High | Completed |
| 4 | **Privileged Mode** | [`privileged-mode.md`](privileged-mode.md) | [`features/privileged-mode/FEATURE.md`](../features/privileged-mode/FEATURE.md) | `PM` | 18 / 18 | ~1,255 | High | Completed |
| 5 | **Virtual Keyboard** | [`keyboard.md`](keyboard.md) | [`features/keyboard/FEATURE.md`](../features/keyboard/FEATURE.md) | `KB` | 20 / 20 | ~1,230 | High | Completed |
| 6 | **Design System & Theming** | [`theming.md`](theming.md) | [`features/theming/FEATURE.md`](../features/theming/FEATURE.md) | `TH` | 18 / 18 | ~1,025 | High | Completed |
| 7 | **Game Focus Launcher** | [`gamefocus.md`](gamefocus.md) | [`features/gamefocus/FEATURE.md`](../features/gamefocus/FEATURE.md) | `GF` | 22 / 22 | ~875 | Med | Completed |
| 8 | **Integration API & Session** | [`integration-api.md`](integration-api.md) | [`features/integration-api/FEATURE.md`](../features/integration-api/FEATURE.md) | `IA` | 26 / 26 | ~660 | High | Completed |
| 9 | **Virtual Touchpad** | [`touchpad.md`](touchpad.md) | [`features/touchpad/FEATURE.md`](../features/touchpad/FEATURE.md) | `TP` | 23 / 23 | ~645 | High | Completed |
| 10 | **In-App Help & Onboarding** | [`help.md`](help.md) | [`features/help/FEATURE.md`](../features/help/FEATURE.md) | `HP` | 14 / 14 | ~565 (+54 XML) | Med | Completed |
| 11 | **Config Export/Import** | [`config.md`](config.md) | [`features/config/FEATURE.md`](../features/config/FEATURE.md) | `CF` | 19 / 19 | ~405 | Med | Completed |
| 12 | **Log Report & AppLog** | [`log-report.md`](log-report.md) | [`features/log-report/FEATURE.md`](../features/log-report/FEATURE.md) | `LR` | 10 / 10 | ~215 | Med | Completed |
| 13 | **Updates & Release Launcher** | [`updates.md`](updates.md) | [`features/updates/FEATURE.md`](../features/updates/FEATURE.md) | `UP` | 12 / 12 | ~152 | Med | Completed |
| — | **Total** | **13 Reports** | — | — | **244 / 244** | **~12,396** *(~11,000 net)* | — | **Completed** |

*(Note: A few cross-cutting findings—such as `PrimaryOverlayManager` vs. `PrimaryOverlayActivity`, `OnboardingWizardDialog` vs. `PrivdReconnectPromptDialog`, and `QuickMenuTutorialDialog`—appear in both the owning feature audit and the shell/theming/help audits; net unique removable/consolidatable code across the repository is ~11,000 LOC.)*

---

## 2. Aggregate Breakdown by Complexity Category

| Category | Findings Count | Est. Removable LOC | Primary Drivers Across the Codebase |
| :--- | :---: | :---: | :--- |
| **1. Over-Engineering & Speculative Abstractions** | 40 | ~3,210 | 92% cloned editors (`BackgroundSettingsEditor` / `MaskSettingsEditor`), 1-line pass-through ViewModels (`GlobalSettingsViewModel`, `MacroPadViewModel`), dead `NavDestination` deep-link hierarchy, hand-rolled JSON parser in `RetroArchLplParser`. |
| **2. Guarding Against Impossible Bugs (Defensive Paranoia)** | 30 | ~812 | Blocking mDNS self-discovery + 3-host retry loops, redundant `isFinite()`/clamping bounds in 4 layers, dead accent/umlaut normalizers, swallowed exceptions hiding fallback paths (`launchUrlOnPrimaryDisplay`). |
| **3. Premature Optimization** | 14 | ~660 | Unreachable CPU anchor-monitoring fallback & `CutoutFrameRingBuffer`, string-splitting `MM`/`M` coalescer in `PrivdClient.writerLoop`, unbounded `YuzuDetector.titleCache`, parallel `BooleanArray` slot tracking. |
| **4. Vestigial Architecture & Ghost Layers** | 42 | ~3,535 | Retired single-viewport/View-Lock pipeline (`MirrorViewportController`, `ViewportMath`), unreachable Keyboard Trackpoint & Mouse overlay (`TrackpointOverlay`, `KeyboardMouseOverlay`), dead `:shared:ui` tokens (`AppDimens`, 13 `AppColors` properties, `GamepadGlyphs`), per-profile device flags (`enableKeyboard`/`Mouse`/`Touch`/`Gamepad`), `AmbientPreviewManager`, dead tutorial modals. |
| **5. Derived State Stored as Mutable State (Split-Brain)** | 26 | ~1,177 | 12 focus/session `MutableStateFlow`s in `AppStateManager` synced imperatively with `AutoSwitchCoordinator` & `EmulatorDetectionFunnel`, manual `recomputeActiveState()` at 16 sites in `MacroPadState`, `_updateAvailable` in `UpdateManager`, `wasMirroringStartedByTouchpad` mutating persisted layout flags. |
| **6. Over-Complicated Concurrency & Ad-Hoc Race Guards** | 20 | ~755 | 6 `@Volatile CompletableDeferred` RPC slots in `PrivdClient`, `@Volatile directPrivdStartGeneration` counter in `ScreenCaptureService`, `scrapeMutex.tryLock()` + `AtomicBoolean` double loop in `InstalledAppsManager`, 7 lock objects in `InstalledAppsManager`. |
| **7. Overlapping Lifecycle Triggers & Debounce Band-Aids** | 19 | ~732 | `PrimaryFocusAnchorActivity.anchorPrimaryFocus()` invoked at 6 sites (including every key event in `MainActivity.dispatchKeyEvent`) behind a 250 ms debounce, 5 concurrent 1-second polling loops for Wireless Debugging / Accessibility state, triple-redundant touchpad `onCancel()` hooks. |
| **8. Compose State Mirroring & UI Boilerplate** | 27 | ~1,445 | ~300 LOC duplicated between `OnboardingWizardDialog` and `PrivdReconnectPromptDialog`, duplicated 90-line touchpad pointer loops in `FullscreenMouseOverlay` and `KeyboardScreen`, un-debounced full-profile JSON serialization on every slider/text-field frame in `MacroPadEditor`. |
| **9. Doc-to-Code Drift** | 26 | Doc sync | Outdated references to `Presentation` mirror windows, standalone native binaries (`touch_injector`), abstract Unix sockets (`@megingiard.privd`), 3-theme palettes, and retired UI controls across `docs/features/**/FEATURE.md`. |
| **Total** | **244** | **~12,396** | — |

---

## 3. Top 10 Cross-Cutting Simplification Wins (All Completed)

1. **Delete Unreachable Ghost Subsystems (~2,500 LOC, Zero Behavior Change)**
   - **Keyboard Trackpoint & Mouse Overlay (`[KB-09]`, `[KB-10]`, `[KB-11]`):** Deleted `TrackpointOverlay.kt`, `KeyboardMouseOverlay.kt`, `KeyType.TRACKPOINT`, `KbMouseBtnPos`, `KeyboardMode.SYMBOLS_2`, and `KeyAction.kt` (~610 LOC).
   - **Single-Viewport & CPU Anchor Fallback in Mirror (`[MR-07]`, `[MR-08]`, `[MR-09]`):** Removed `MirrorViewportController.kt`, `ThrottledTextureView.kt`, 5 dead `MirrorSettings` DataStore keys, `CutoutFrameRingBuffer.kt`, and the unreachable software CPU anchor loop (~780 LOC).
   - **Dead Navigation, Modal & Tutorial Classes (`[QM-01]`, `[QM-09]`, `[HP-05]`, `[HP-06]`, `[MP-09]`):** Removed `NavDestination.kt`, 5 unused `PrimaryModalPayload`/`PrimaryModalType` variants, `AmbientPreviewManager`, `CutoutLayoutEditorHelpModal`, `AppTutorialModal.kt`, `WelcomeTutorialDialog`, and `QuickMenuTutorialDialog` (~680 LOC).
   - **Unused `:shared:ui` Surface Area (`[TH-06]`–`[TH-10]`):** Removed `AppDimens` / `LocalAppDimens` (0/18 properties read), 13 unused `AppColors` tokens, `GamepadGlyphs.kt`, `GamepadSearchBar`, and unused `CutoutLetterCircleIcon` composables (~500 LOC).

2. **Unify Cloned UI Screens & Editors (~1,350 LOC)**
   - **MacroPad Background vs. Mask Editors (`[MP-01]`):** Unified `BackgroundSettingsEditor.kt` and `MaskSettingsEditor.kt` onto `LayoutMediaSettingsSection` and `LayoutMediaSlot` in `MacroPadMediaRepository` (~460 LOC).
   - **Onboarding & Privd Reconnect Wizards (`[PM-15]`, `[HP-11]`):** Extracted shared step builders (`SystemSetupStepContent`), `OnboardingWizardFooter`, and root/ADB activation actions between `OnboardingWizardDialog.kt` and `PrivdReconnectPromptDialog.kt` (~300 LOC).
   - **Touchpad Surface & Relative-Mouse Settings (`[TP-17]`, `[TP-18]`):** Shared `rememberGlobalTouchpadProcessor` / `touchpadSurfaceInput` between `FullscreenMouseOverlay` and `KeyboardScreen`, and shared the relative-mouse setting cards between `TouchpadSettingsOverlay` and `BackgroundTouchpadSettingsEditor` (~195 LOC).
   - **MacroPad 2D Gamepad Move/Resize Controller (`[MP-02]`):** Extracted `GamepadButtonAdjuster.kt` shared between `MacroPadEditor.kt` and `PadButtonEditDialog.kt` (~120 LOC).
   - **VisualAutoTuneCoordinator Calibration Loops (`[MR-03]`):** Consolidated `startCalibration` and `startLayoutAnchorCalibration` into `runCalibrationSession` (~180 LOC).

3. **Deduplicate `PrimaryOverlayManager` & `PrimaryOverlayActivity` Host Logic (`[QM-08]`, `[TH-14]`, ~160 LOC)**
   - Extracted `PrimaryOverlayHostSupport` (`rememberPrimaryOverlayFreezeEffect`, `dispatchPrimaryOverlayKeyEvent`, `PrimaryOverlayThemeRoot`) shared across top-screen modal hosts.

4. **Retire Pass-Through ViewModels (`[QM-02]`, `[LR-03]`, ~260 LOC)**
   - Deleted `MacroPadViewModel` and removed 1-line pass-through wrappers from `GlobalSettingsViewModel`.

5. **Clean Up Pre-`privd` Injector & Per-Profile Device Flag Scaffolding (`[MP-08]`, `[TP-06]`–`[TP-08]`, ~350 LOC)**
   - Simplified `MouseInjector`, `TouchInjector`, and `InjectorLifecycleManager` and removed vestigial per-profile device flags (`enableKeyboard`, `enableTouchpad`, `enableGamepad`, `enableMirror`) and unreachable `DisabledReason` / greyscale button rendering.

6. **Consolidate Split-Brain Reactive State (`[MP-12]`, `[IA-15]`, `[MR-13]`, `[TP-11]`, `[UP-06]`, ~550 LOC)**
   - Centralized `MacroPadState` mutations and `recomputeActiveState()`, derived `EffectiveFocusState` in `AppStateManager`, derived layout flows in `ScreenCaptureManager`, unified `updateAvailable` in `UpdateManager`, and moved touchpad mirror demand into `MirrorRuntimePolicy`.

7. **Simplify `PrivdClient` & `ScreenCaptureService` Concurrency (`[PM-11]`, `[PM-12]`, `[MR-15]`, ~250 LOC)**
   - Replaced the 6 `@Volatile CompletableDeferred` fields in `PrivdClient` with `PendingRpc` inside `commandMutex`, and replaced `@Volatile directPrivdStartGeneration` in `ScreenCaptureService` with `combine(...).collectLatest`.

8. **Remove `MainActivity.dispatchKeyEvent` Focus-Anchor Spam (`[QM-17]`, ~50 LOC)**
   - Restricted `PrimaryFocusAnchorActivity.anchorPrimaryFocus()` in `MainActivity.dispatchKeyEvent` to initial `KeyEvent.ACTION_DOWN` (`repeatCount == 0`) and consolidated lifecycle triggers.

9. **Debounce Live Slider & Text-Field Persistence in `MacroPadEditor` (`[MP-18]`, ~60 LOC)**
   - Consolidated preview teardown and debounced `MacroPadState` disk persistence.

10. **Synchronize `FEATURE.md` Documentation Across All 13 Features (`[MP-22]`, `[MR-19]`, `[PM-17]`, `[PM-18]`, `[KB-20]`, `[TP-20]`–`[TP-23]`, `[QM-20]`, `[TH-17]`, `[GF-21]`)**
    - Synchronized all `docs/features/**/FEATURE.md` specs, `docs/ARCHITECTURE.md`, `docs/BUILD_NATIVE.md`, `docs/GAMEPAD_NAVIGATION.md`, and `SECURITY_CONCEPT.md` with the simplified implementation.
