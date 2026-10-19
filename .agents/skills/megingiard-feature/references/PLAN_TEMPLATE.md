# Implementation Plan Template (megingiard-feature)

> Used by `megingiard-feature` Step 5. Copy the skeleton below into the
> `implementation_plan.md` artifact and fill **every** section. If a section truly does not
> apply, keep the heading and write `n/a — <one-line reason>`; never delete it silently.

## Purpose: high-signal specification for same-session Pro execution

The plan is authored and executed by a **high-reasoning model (`Pro`) in the same session** (while remaining self-contained enough to survive context compaction cleanly). The goal is **maximum architectural & UX precision with zero duplicate boilerplate**:

1. **Self-contained.** Never write "as discussed", "see interview", or refer to chat context. Record all resolved decisions in §2 and current-state facts in §3.
2. **Core decisions & signatures as code; zero duplicate boilerplate.**
   - Write exact data types, function signatures, constants (`TAG`, prefixed constants), and **core decision logic** (algorithms, state transitions, threading/dispatchers, Compose effect keys) as concise Kotlin code blocks in §5.
   - **Never write the same artifact twice inside the plan:**
     - **Strings** belong **only** in the §8 table (never as XML diff blocks in §5).
     - **Unit tests** belong **only** in the §10 table (never as full test file bodies in §5).
     - **Documentation updates** belong **only** in §11 (never as verbatim Markdown diff blocks in §5).
3. **Line-range targets instead of verbatim before-anchors.** Reference existing code locations by clickable file link + line range (`[File.kt](file:///abs/path/File.kt#L70-L95)`) and symbol name rather than copying multi-line before-blocks.
4. **Fenced scope.** State rejected approaches, intentional oddities, and files that must not be touched (§16).
5. **Single unified verification gate.** All planned edits are applied in one continuous pass in Implementation Mode, followed by a single `./scripts/verify.sh` + `megingiard-code-review` pass at the end.

---

## Depth scaling

| Size | Typical signals | Required depth |
| ---- | --------------- | -------------- |
| **S** | 1–3 files, no new state, no persistence | All sections; concise signatures + key logic snippets in §5 |
| **M** | 4–10 files, new StateFlow or setting, UI + domain | All sections; exact signatures, constants, and core logic blocks in §5 |
| **L** | > 10 files, new module area, persistence/protocol change | **Split into phases**, one plan per phase (each phase ≤ M). Add a mermaid data-flow diagram and a migration section |

State the chosen size at the top.

---

## Skeleton

````markdown
# Implementation Plan: <Feature Name>

**Size:** S | M | L  ·  **Owning feature doc:** `docs/features/<x>/FEATURE.md`  ·  **Modules touched:** `:companion:ui`, …
**Base commit:** `<git rev-parse --short HEAD at planning time>`

## 0. Execution Instructions
Execute with the `megingiard-feature` skill, section "Implementation Mode". Apply all planned
edits (§5 production code, §8 strings, §10 unit tests, §11 docs) in a single continuous pass
without intermediate Gradle runs, then run `./scripts/verify.sh` and `megingiard-code-review`
once at the end. Do not re-open design decisions.

## 1. Goal & Scope
- **Goal:** <one sentence, user-visible outcome>
- **In scope:** <bullets>
- **Out of scope:** <bullets — things a reader might assume are included but are not>

## 2. Key Decisions
| # | Decision | Rationale | Rejected alternatives (and why) |
| - | -------- | --------- | ------------------------------- |
| D1 | … | … | … |

## 3. Current State
How the affected area works **today**, with clickable links + line ranges, e.g.
- [`MacroPadState.updateLayout()`](file:///abs/path/MacroPadState.kt#L120-L158) — persists via `persistScope`, load-once guard `isLayoutLoaded`.
- **Reference implementation to mirror:** <existing feature + link> — <exactly which pattern is copied, and where it differs>.

## 4. Architecture & Data Flow
- Ownership: which singleton owns the new state, who writes (`internal` setter), who reads (`StateFlow`).
- Threading: which scope / dispatcher runs each step; cancellation in `onDestroy()` (§12.1).
- Screen targeting: Display 0 / Display 4, `setLaunchDisplayId()` if an Activity is launched.
- (L mandatory, M optional) mermaid diagram: user action → ViewModel → domain → persistence → UI.

## 5. Production Code Changes
One subsection **per production Kotlin/C file** (do **not** list `strings.xml`, unit test files, or `FEATURE.md` here — those are specified once in §8, §10, and §11):

### 5.x [`File.kt`](file:///abs/path/File.kt) — NEW | MODIFIED
- **Purpose:** <one line>
- **Constants / TAG:** `private const val TAG = "..."` (if logging is added), `private const val XX_NAME = <value>`
- **Changes:**
  - **Edit E5.x.1 (`symbolName`, `L70-L95`):** <concise description of the change + Kotlin code block showing exact signatures and core decision/state/Compose logic; omit repetitive boilerplate>
- **Removed / renamed symbols:** <symbol> + every call site, or `none`.

## 6. Data Model, Persistence & Migration
- New/changed fields: `Class.field: Type = default`.
- Serialization: `@Serializable`, `@SerialName`, defaults so **old files still decode**; R8/ProGuard impact; effect on `.mgrd` export/import.
- Load-once guard (§7.6): flag name, reset in `resetForTesting()`.
- Migration: <steps> or `n/a — no persisted shape change`.

## 7. UI Specification
- Placement relative to existing elements (name the sibling composables).
- Components: `AppModalDialog`, `GamepadTwoStepConfirmCard`, M3 `Switch` (no `colors =`, §16.4), …
- Tokens: `MaterialTheme.typography.<x>`, `LocalAppColors.current.<x>`, dimension constants with values.
- Gamepad focus: traversal order, initial focus, focus recovery (`docs/GAMEPAD_NAVIGATION.md`).
- States: loading / empty / error / disabled — what is shown for each.

## 8. Strings (all locales — §9.2)
Single source of truth for all new/updated strings (applied to `values/strings.xml`, `values-de/strings.xml`, and `values-zh-rTW/strings.xml`):
| Key | en | de | zh-rTW | Target module |
| --- | -- | -- | ------ | ------------- |
| `foo_title` | … | … | … | `:companion:ui` |

## 9. Edge Cases & Failure Modes
Every edge case raised in the interview **must** appear here.
| Scenario | Expected behaviour | Implemented in |
| -------- | ------------------ | -------------- |
| App backgrounded mid-action | … | E5.2.1 |

## 10. Test Plan (§3.3)
Single source of truth for all new/updated unit tests (do not duplicate full test file code in §5):
| Test class (path) | Method | Given | When | Then (exact assertion) |
| ----------------- | ------ | ----- | ---- | ---------------------- |
| `shared/core/src/test/kotlin/…/FooTest.kt` | `decode_oldFileWithoutField_usesDefault` | JSON `{"a":1}` | `decode()` | `result.foo == false` |
- New `src/test` dir → add task to `scripts/run_unit_tests.sh`: yes / no.
- Untestable logic + reason → follow-up.

## 11. Documentation & In-App Help (§5)
Single source of truth for doc updates (do not duplicate as diff blocks in §5):
- `FEATURE.md` (and split part files): file + section → concise summary of requirements/technical details to add or update.
- `ARCHITECTURE.md` / `SECURITY_CONCEPT.md`: change or `n/a — reason`.
- `HelpModal`: file + entry title/body string keys (from §8); onboarding changes.
- `AGENTS.md` §2 row (new feature only).

## 12. Task Checklist & Verification
Logical execution order for the continuous implementation pass; `./scripts/verify.sh` runs **once** at the end:
| Task | Scope | Done when |
| ---- | ----- | --------- |
| T1 | `:shared:core` / `:companion:domain` (`E5.1.*`, `E5.2.*`) | Models & domain logic updated |
| T2 | `:companion:ui` + strings (`E5.3.*`, `§8`) | UI wired and localized in EN/DE/ZH-TW |
| T3 | Unit tests (`§10`) + Docs (`§11`) | Tests & `FEATURE.md` synced |
| T4 | Final Gate | `./scripts/verify.sh` green + `megingiard-code-review` pass clean |

## 13. AGENTS.md Compliance & Risks
| Rule | How this plan satisfies it |
| ---- | -------------------------- |
| §6.1 Companion autonomy | … |
| §7.5 Zero-Heuristics | … |
| §9.1 LaunchedEffect keys | … |
| §12.1 Teardown order | … |
- **Risks:** <regression, recomposition / per-frame cost, compatibility> + mitigation.

## 14. Manual Verification (device)
Numbered operator steps on the AYN Thor with the expected result per step; whether
`docs/MANUAL_VERIFICATION.md` gains a regression case.

## 15. Open Questions / Assumptions
Must be **empty** before implementation starts. Resolve with the user first.

## 16. Guardrails
- **Do NOT:** <rejected approaches from §2 restated as prohibitions>
- **Looks wrong but is intentional:** <non-obvious design details to preserve>
- **Files that must not be modified:** <list, always incl. `:gamefocus:*` for Companion features>
````

---

## Quality Gate (self-check before presenting)

Verify before presenting the plan to the user:

- [ ] No references to chat or "as discussed"; §15 is empty.
- [ ] Every new/changed function signature, constant (`TAG`, prefixed UI constants), and core decision logic is specified in §5 without duplicating full boilerplate files.
- [ ] Strings appear **only** in §8 (with EN, DE, and ZH-TW translations), tests **only** in §10 (with concrete Given/When/Then), and docs **only** in §11.
- [ ] Every interview decision is in §2; every interview edge case is in §9.
- [ ] Every UI-visible setting has a HelpModal entry in §11.
- [ ] Persistence/serialization changes explicitly verify backward compatibility and R8 keep coverage.
- [ ] Plan does not touch `:gamefocus:*` to fix or support Companion behaviour (§6.1).

