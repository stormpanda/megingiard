# UX Improvement Plan (Post-Overhaul)

## Goal

Identify and prioritize concrete UX improvements after the completed UI overhaul, with an implementation path that can be executed incrementally without risky architecture changes.

## Analysis Scope

Reviewed the currently active UX-critical surfaces:

- `MainAppScreen`
- `ui/IdlePill`
- `ui/PillMenu`
- `macropad/MacroPadScreen`
- `macropad/MacroPadEditor`
- `keyboard/KeyboardScreen`
- `touchpad/FullscreenMouseOverlay`
- `settings/GlobalSettingsScreen`
- `domain/macropad/MacroPadState`

---

## Findings and Proposed Improvements

| ID | Finding | Evidence | UX Impact | Proposed Change |
| --- | --- | --- | --- | --- |
| UX-01 | Core navigation discoverability is weak for first-time users. | `IdlePill` is a minimal visual affordance (`72dp x 4dp`), while edge-swipe detection is implemented separately in `SwipeGestureProcessor` (`app/src/main/java/com/stormpanda/megingiard/ui/IdlePill.kt`, `domain/src/main/java/com/stormpanda/megingiard/SwipeGestureProcessor.kt`). | New users may not discover how to open Pill Menu quickly. | Add first-run coach marks (dismissible) for edge-swipe and pill behavior. |
| UX-02 | Mirror controls rely heavily on icon-only actions. | `MirrorControlCard` uses icon-only controls for start/stop/freeze/viewport/touch projection (`app/src/main/java/com/stormpanda/megingiard/ui/PillMenu.kt`). | Learnability cost and higher error risk for less-frequent controls. | Add optional compact labels/tooltips for mirror controls (setting-controlled if needed). |
| UX-03 | Mirror control touch targets are smaller than recommended ergonomic size. | `PM_MIRROR_BUTTON_SIZE = 36.dp` (`app/src/main/java/com/stormpanda/megingiard/ui/PillMenu.kt`). | Reduced hit accuracy during fast interaction. | Increase touch target to at least 44–48dp while keeping visual icon size compact. |
| UX-04 | Profile/layout naming flow has no inline validation. | `NameInputDialog` confirms raw text, and state layer does not enforce uniqueness (`app/src/main/java/com/stormpanda/megingiard/ui/PillMenu.kt`, `domain/src/main/java/com/stormpanda/megingiard/macropad/MacroPadState.kt`). | Duplicate or blank-like names reduce manageability for larger setups. | Add validation and conflict handling (empty, duplicate, whitespace-only). |
| UX-05 | Disabled-device feedback during MacroPad usage is noisy and transient. | Pressing disabled buttons triggers repeated Toasts (`app/src/main/java/com/stormpanda/megingiard/macropad/MacroPadScreen.kt`). | Repeated toasts interrupt flow; context is lost quickly. | Replace with rate-limited inline status banner/Snackbar in the pad surface. |
| UX-06 | Fullscreen overlays have no persistent "how to exit" hint. | `FullscreenMouseOverlay` and fullscreen keyboard exit via edge swipe only, but no visible instructional affordance in overlay content (`app/src/main/java/com/stormpanda/megingiard/touchpad/FullscreenMouseOverlay.kt`, `app/src/main/java/com/stormpanda/megingiard/keyboard/KeyboardScreen.kt`). | Users can get temporarily stuck/confused in fullscreen modes. | Add subtle one-line contextual hint that auto-fades; visibility can be globally enabled/disabled via a persistent setting. |
| UX-07 | Global settings information architecture scales poorly as options grow. | Single long `Column` with vertical scroll in `app/src/main/java/com/stormpanda/megingiard/settings/GlobalSettingsScreen.kt`. | Increased scan time and missed settings in extended sessions. | Introduce grouped collapsible sections and quick-jump chips at top. |
| UX-08 | Wrong-screen blocking overlay informs but does not assist recovery. | `WrongScreenOverlay` only shows message + arrow (`app/src/main/java/com/stormpanda/megingiard/MainAppScreen.kt`). | Recovery can feel unclear on first encounter. | Add explicit recovery actions (e.g. "Retry detection", "Open help"). |

---

## Prioritization

### Phase 1 — Quick Wins (Low risk, high UX value)

1. **UX-03** Increase mirror control touch targets.
2. **UX-06** Add fullscreen exit hints.
3. **UX-04** Add naming validation for profile/layout creation/rename.

### Phase 2 — Discoverability and Confidence

4. **UX-01** First-run onboarding for edge-swipe/pill menu.
5. **UX-02** Mirror control labels/tooltips.
6. **UX-08** Recovery actions in wrong-screen overlay.

### Phase 3 — Information Architecture

7. **UX-07** Settings restructuring (quick-jump + collapsible groups).
8. **UX-05** Replace toast-only disabled-device feedback with inline status.

---

## Implementation Notes

- Keep changes surgical and feature-local (avoid cross-module state churn unless required).
- Use existing state managers (`AppStateManager`, `MacroPadState`) for runtime UI state and interaction toggles.
- Example runtime state: whether an overlay/hint is currently visible during a session.
- Persist any new UX preferences through `SettingsManager` (DataStore) rather than in-memory-only flags.
- Example persisted preference: user opt-in/out for showing helper hints across app restarts.
- All new user-visible copy must be added to `app/src/main/res/values/strings.xml`.
- Preserve current gesture architecture (edge swipe + pill menu) and existing service/input lifecycles.

---

## Success Criteria

Each implemented item should define measurable acceptance criteria before coding. Suggested defaults:

- **Discoverability:** first-time users can open Pill Menu without external guidance.
- **Accuracy:** mirror control mis-taps reduced by larger touch target sizing.
- **Task completion:** users can exit fullscreen overlays immediately without trial-and-error.
- **Manageability:** profile/layout naming conflicts are prevented at input time.
- **Cognitive load:** settings are reachable with fewer scroll interactions.
