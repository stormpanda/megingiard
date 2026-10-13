# Implementation Plan Template (megingiard-feature)

> Used by `megingiard-feature` Step 5. Copy the skeleton below into the
> `implementation_plan.md` artifact and fill **every** section. If a section truly does not
> apply, keep the heading and write `n/a — <one-line reason>`; never delete it silently.

## Purpose: a hand-off contract

The plan is written by a **high-reasoning planner** and executed by a possibly
**lightweight implementer** (smaller model, fresh session, no memory of the interview or
survey). The implementer's job is *transcription and verification*, not design.
Therefore:

1. **Self-contained.** The plan is the only input. Never write "as discussed", "see
   interview", "per survey notes", or refer to chat context. Copy the facts in.
2. **Decisions are encoded as code, not prose.** Any logic where a choice was made —
   branch conditions, ordering, threading/dispatcher, null/empty handling, error paths,
   state transitions, Compose keys — is written as a **Kotlin code block** (full body or
   near-complete code). Prose is only allowed for mechanical boilerplate.
3. **Locatable edits.** Every modification to an existing file is anchored to a
   **verbatim excerpt** of the current code, so the implementer cannot patch the wrong spot.
4. **Fenced scope.** The implementer must know what *not* to do: rejected approaches,
   intentional oddities, files that must not be touched.
5. **Mechanically verifiable.** Every task has a done-criterion and a command.

**Test for sufficiency:** could a junior engineer who has never seen this codebase produce
the same diff the planner has in mind, without making a single design decision? If not,
add detail where they would have to decide.

---

## Depth scaling

| Size | Typical signals | Required depth |
| ---- | --------------- | -------------- |
| **S** | 1–3 files, no new state, no persistence | All sections; boilerplate may be prose, decision logic still as code |
| **M** | 4–10 files, new StateFlow or setting, UI + domain | All sections in full; code blocks for every new/changed function containing logic |
| **L** | > 10 files, new module area, persistence/protocol change | **Split into phases**, one plan per phase (each phase ≤ M). Add a mermaid data-flow diagram and a migration section |

State the chosen size at the top. Scale the plan up, never down: when unsure, write more code.

---

## Skeleton

````markdown
# Implementation Plan: <Feature Name>

**Size:** S | M | L  ·  **Owning feature doc:** `docs/features/<x>/FEATURE.md`  ·  **Modules touched:** `:companion:ui`, …
**Base commit:** `<git rev-parse --short HEAD at planning time>` (anchors in §5 are verbatim against this commit)

## 0. Implementer Instructions
Execute with the `megingiard-feature` skill, section "Implementation Mode". Follow §12 task
by task. Do not redesign, rename, reorder, or add anything not in this plan. If an anchor
in §5 is not found verbatim, or a step cannot be done as written, **stop and report** —
do not improvise.

## 1. Goal & Scope
- **Goal:** <one sentence, user-visible outcome>
- **In scope:** <bullets>
- **Out of scope:** <bullets — things a reader might assume are included but are not>

## 2. Key Decisions
| # | Decision | Rationale | Rejected alternatives (and why) |
| - | -------- | --------- | ------------------------------- |
| D1 | … | … | … |

## 3. Current State (facts the implementer needs)
How the affected area works **today**, with clickable links + line ranges, e.g.
- [`MacroPadStateManager.updateLayout()`](file:///abs/path/MacroPadStateManager.kt#L120-L158) — persists via `persistScope`, load-once guard `isLayoutLoaded`.
- **Reference implementation to mirror:** <existing feature + link> — <exactly which pattern is copied, and where it differs>.

## 4. Architecture & Data Flow
- Ownership: which singleton owns the new state, who writes (`internal` setter), who reads (`StateFlow`).
- Threading: which scope / dispatcher runs each step; cancellation in `onDestroy()` (§12.1).
- Screen targeting: Display 0 / Display 4, `setLaunchDisplayId()` if an Activity is launched.
- (L mandatory, M optional) mermaid diagram: user action → ViewModel → domain → persistence → UI.

## 5. Per-File Change Specification
One subsection **per file**, in task order. For each:

### 5.x `module/src/.../File.kt` — NEW | MODIFIED
- **Purpose:** <one line>
- **Imports to add:** `import …` (explicit, §8.2)
- **Constants / TAG:** `private const val XX_NAME = <value>` — meaning.
- **Edit E5.x.1 — <short name>**
  - **Anchor (verbatim, current code):**
    ```kotlin
    <3–10 unique lines copied exactly from the file at the base commit>
    ```
  - **Action:** insert after | insert before | replace anchor with:
    ```kotlin
    <final code — full bodies for anything with logic, including AppLog calls>
    ```
- **Edit E5.x.2 — …** (repeat)
- **NEW files:** give the complete file content (package, imports, TAG, code). Only pure
  boilerplate (e.g. a long `when` mapping listed in a table) may be abbreviated, and then
  the table must list every entry.
- **Removed / renamed symbols:** <symbol> + every call site (file + edit id), or `none`.

## 6. Data Model, Persistence & Migration
- New/changed fields: `Class.field: Type = default`.
- Serialization: `@SerialName`, defaults so **old files still decode**; effect on `.mgrd` export/import.
- Load-once guard (§7.6): flag name, reset in `resetForTesting()`.
- Migration: <steps> or `n/a — no persisted shape change`.

## 7. UI Specification
- Placement relative to existing elements (name the sibling composables).
- Components: `AppModalDialog`, `GamepadTwoStepConfirmCard`, M3 `Switch` (no `colors =`, §16.4), …
- Tokens: `MaterialTheme.typography.<x>`, `LocalAppColors.current.<x>`, dimension constants with values.
- Gamepad focus: traversal order, initial focus, focus recovery (`docs/GAMEPAD_NAVIGATION.md`).
- States: loading / empty / error / disabled — what is shown for each.
(The code in §5 must already implement all of this; this section is the reviewable summary.)

## 8. Strings (all locales — §9.2)
| Key | en | de | zh-rTW | Target file(s) |
| --- | -- | -- | ------ | -------------- |
| `foo_title` | … | … | … | `companion/ui/src/main/res/values*/strings.xml` |

## 9. Edge Cases & Failure Modes
Every edge case raised in the interview **must** appear here.
| Scenario | Expected behaviour | Implemented in (edit id) |
| -------- | ------------------ | ------------------------ |
| App backgrounded mid-action | … | E5.2.1 |

## 10. Test Plan (§3.3)
For every test method: given / when / then with **concrete values**.
| Test class (path) | Method | Given | When | Then (exact assertion) |
| ----------------- | ------ | ----- | ---- | ---------------------- |
| `shared/core/src/test/kotlin/…/FooTest.kt` | `decode_oldFileWithoutField_usesDefault` | JSON `{"a":1}` | `decode()` | `result.foo == false` |
- Test helpers / fixtures needed: <names + where>.
- New `src/test` dir → add task to `scripts/run_unit_tests.sh`: yes / no.
- Untestable logic + reason → follow-up.

## 11. Documentation & In-App Help (§5)
- `FEATURE.md`: section name → text to add/replace (draft the actual sentences).
- `ARCHITECTURE.md` / `SECURITY_CONCEPT.md`: change or `n/a — reason`.
- `HelpModal`: file + entry title/body string keys (from §8); onboarding changes.
- `AGENTS.md` §2 row (new feature only).

## 12. Task Breakdown
Small, ordered tasks; each touches one concern and leaves the build compiling.
| Task | Edits | Done when | Verify |
| ---- | ----- | --------- | ------ |
| T1 | E5.1.1, E5.1.2 | data class has field `foo` with default | `./gradlew compileDebugKotlin` |
| T2 | … | … | … |
| Tn | — | all green | `./scripts/verify.sh`, then `megingiard-code-review` |

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
Must be **empty** before hand-off. Resolve with the user first.

## 16. Implementer Guardrails
- **Do NOT:** <rejected approaches from §2 restated as prohibitions, e.g. "do not use
  `LaunchedEffect(offset)` — use the snapshotFlow pattern in E5.3.2">
- **Looks wrong but is intentional:** <code that a "helpful" model might "fix">
- **Files that must not be modified:** <list, always incl. `:gamefocus:*` for Companion features>
- **If X happens, stop:** <known divergence risks, e.g. "if `foo()` already exists with a
  different signature, stop and report">
````

---

## Quality Gate (self-check before presenting)

Re-read the finished plan **as if you were a smaller model with no context** and fix it
until every item holds. Do not present a plan that fails any item.

**Hand-off fitness**
- [ ] No references to chat, interview, survey notes, or "as discussed"; §15 is empty.
- [ ] Every function containing a decision (condition, ordering, dispatcher, null path, error path, Compose key) is given as code, not prose.
- [ ] Every edit to an existing file has a verbatim anchor that occurs **exactly once** in that file (verify with `grep`).
- [ ] New files are given in full.
- [ ] Every task in §12 has a done-criterion and a verify command; the build compiles after each task.
- [ ] §16 restates every rejected alternative from §2 as a prohibition.

**Completeness**
- [ ] No vague verbs without code: "update", "adjust", "wire up", "refactor", "handle", "integrate".
- [ ] Every new constant, StateFlow, string key, and log line is named and appears in code.
- [ ] Every interview decision is in §2; every interview edge case is in §9 with an edit id.
- [ ] Every user-visible string is in §8 with text for **all** locales.
- [ ] Every UI-visible setting has a HelpModal entry in §11.
- [ ] §10 tests have concrete given/when/then values.
- [ ] No filler: "as needed", "if necessary", "etc.", "appropriate", "properly", "and so on", "TBD".
- [ ] Persistence changes state backward compatibility of existing user data.
- [ ] Plan does not touch `:gamefocus:*` to fix or support Companion behaviour (§6.1).
