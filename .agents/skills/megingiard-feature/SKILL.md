---
name: megingiard-feature
description: "Plan and implement a new feature for the Megingiard Android app. Use when: adding new user-visible behavior, extending existing features (MacroPad, Mirror, Keyboard, Touchpad, Settings), wiring up new Compose screens, OR executing an approved implementation_plan.md produced by this skill. Planning Mode conducts an interactive interview, surveys code and docs, and writes a self-contained hand-off plan; Implementation Mode executes such a plan literally, verifies each task, and synchronizes FEATURE.md documentation."
argument-hint: 'Describe the feature (e.g. "add a dark-mode toggle to the settings screen")'
---

# Skill: Megingiard Feature Development

## Role

You are an experienced Android/Kotlin engineer with deep knowledge of the **Megingiard** project. Your task is to systematically plan a newly described feature, align it with the existing feature set, implement it cleanly, and keep the documentation fully up to date.

---

## Project Context

| Key            | Value                                                                      |
| -------------- | -------------------------------------------------------------------------- |
| Package        | `com.stormpanda.megingiard`                                                |
| Language       | Kotlin 2.0+, Jetpack Compose Material 3                                    |
| Modules        | `:companion:ui` (UI) · `:companion:domain` (business logic) · `:shared:*` |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Standalone Rule| Companion has ZERO dependencies on Game Focus. Never couple Companion to Game Focus! |
| Build policy   | Only the commands permitted in `AGENTS.md` §3.1 (`./scripts/run_unit_tests.sh`, compile check) — always with sandbox bypass |
| Log tag prefix | All app logs are tagged `Mgnrd.*`                                          |
| ADB path       | `~/Library/Android/sdk/platform-tools/adb`                                 |

---

## Modes

| Mode | Trigger | Steps |
| ---- | ------- | ----- |
| **Planning Mode** | The user describes a feature informally (e.g. "delete buttons by swiping right") | Steps 1–5. Surveys code/docs, conducts the design interview, and presents a concise, high-signal `implementation_plan.md` for user approval. |
| **Implementation Mode** | The user approves the plan (e.g. "proceed") or points to an approved `implementation_plan.md` | Section **Implementation Mode** below, then Steps 7–8. Executes all planned tasks in a continuous pass and runs `./scripts/verify.sh` once at the end. |

Both modes are optimized for a **same-model, same-session (`Pro` all the way)** workflow while remaining self-contained enough to survive context compaction seamlessly. Design decisions are locked in during Planning Mode — Implementation Mode never re-opens them.

---

## Steps

### 1. ✅ Understand the feature

Read the user's description carefully. Establish internally:

- What should the feature do? (user-visible behavior)
- Which area of the app is affected? (MacroPad, Mirror, Keyboard, Touchpad, Settings, …)
- Is this an extension of an existing feature or something entirely new?

---

### 2. ✅ Read existing documentation

Identify the affected feature area via the **Documentation Map in `AGENTS.md §2`** (the authoritative, always-current list — do not rely on a copy) and read the owning `FEATURE.md` **in full, including every split part file** listed in its document map.

Also read:

- `docs/ARCHITECTURE.md` — overall architecture
- `docs/REQUIREMENTS.md` — non-functional requirements
- The matching sections of `docs/agents/CODING_GUIDE.md` (e.g. §9 Compose, §7 state, §12 resources)
- `docs/GAMEPAD_NAVIGATION.md` if the feature adds focusable UI

Goal: a complete picture of what already exists in this area and how it works.

---

### 3. ✅ Survey the codebase

Explore the affected code area. **Read the files you expect to modify** (not just grep hits) so you know exact signatures, line ranges, and conventions.

- Read the relevant files from the module tree (`AGENTS.md §6`)
- Identify:
  - Which **state singletons** (`object` in `:companion:domain`) are involved?
  - Which **ViewModels** (`/viewmodel/`) coordinate this area?
  - Which **Composables** (`:companion:ui`) render it?
  - Which **data models** (`:shared:core`) may need extending, and are they persisted / exported in `.mgrd`?
  - Which **HelpModal**, onboarding, and `strings.xml` entries cover this area today?
  - Which **existing tests** cover this area (`src/test/**`)?
- Find the **closest existing feature** as a reference implementation (e.g. how another toggle/action/setting was added end-to-end) and trace it from UI to persistence. Record these facts directly into §3 of `implementation_plan.md` after the interview (no separate `scratch/survey_notes.md` file needed).

---

### 4. ✅ Conduct interactive design interview

Before drafting the implementation plan, conduct an interactive interview with the user to resolve design decisions, UX behavior, technical trade-offs, and edge cases.

> 💡 **Interview Rules (mirroring `/grill-me`):**
> - **Always conduct an interview for every feature**, even simple ones, to validate assumptions and explore edge cases.
> - **Strictly one question at a time**: Never batch multiple questions into a single message or overwhelm the user.
> - **Use the `ask_question` tool**: Present the question through the interactive modal with selectable options and write-in support.
> - **Always provide recommended answers**: The first option must be prefixed with `(Recommended)` and represent the best architectural/UX decision based on the codebase survey.
> - **Ground questions in the codebase**: If a question can be answered by inspecting the code or documentation, inspect it yourself first. Only ask the user about trade-offs, behavioral preferences, UX choices, and ambiguities.
> - **Walk down the decision tree dynamically**: Continue interviewing sequentially down each branch of the design tree until all requirements, edge cases, and architectural choices are completely unambiguous and agreed upon. Announce completion before transitioning to the plan.

#### Systematic Exploration Domains:
Explore each relevant domain sequentially:
1. **UI/UX & Gamepad Navigation:**
   - Visual placement, sizing, padding, and theme colors (`MaterialTheme.typography`, `LocalAppColors.current`, `ColorScheme`)
   - Touch interactions, gestures, animations, and haptic feedback
   - Gamepad 2D focus traversal, focus recovery, and button glyphs (`GamePadButton`)
2. **State & Persistence:**
   - Singleton ownership in `:companion:domain` (StateFlow vs MutableStateFlow)
   - Persistence requirements (Proto DataStore vs SAF filesystem)
   - Lifecycle scoping (Activity, Service, Presentation, class-level `SupervisorJob`)
3. **Dual-Screen & Standalone Architecture:**
   - Screen targeting (AYN Thor top display 0 vs bottom display 4)
   - **Companion Autonomy:** Strict zero-dependency rule on Game Focus (`AGENTS.md §6.1`)
   - Presentation lifecycle (`hide()`/`show()` instead of `dismiss()`)
4. **Edge Cases & Failure Modes:**
   - Backgrounding / app switching / screen sleep
   - Display disconnects or reconnects
   - Missing permissions or daemon unavailable (Privileged Mode)
   - Null, empty, or error states in UI
5. **Help & Localization:**
   - Strings required for `strings.xml`, `values-de/strings.xml`, and `values-zh-rTW/strings.xml`
   - In-app `HelpModal` and onboarding walkthrough entries (every visible setting must have a help entry)
6. **Persistence compatibility & testing:**
   - Must existing saved data / `.mgrd` backups still load? Default values for new fields?
   - Which behaviours must be unit-tested vs. manually verified on device?

Keep a running **decision log** (question → chosen answer → rationale → rejected options) during the interview; it becomes §2 of the plan.

---

### 5. ✅ Create and present an implementation plan

1. **Read [`references/PLAN_TEMPLATE.md`](references/PLAN_TEMPLATE.md)** (next to this file) — it defines the streamlined same-session plan structure (§0–§16), depth scaling (S/M/L), and the quality gate.
2. **Write the plan as an artifact** `implementation_plan.md` in the artifact directory (`RequestFeedback: true`, `UserFacing: true`) — not inline in chat. Record the base commit (`git rev-parse --short HEAD`). Fill every section; inapplicable sections say `n/a — <reason>`.
3. **Specify signatures & core decision logic without writing boilerplate twice:**
   - Give exact signatures, constants (`TAG`, prefixed constants), target file + line ranges (`file:///.../File.kt#L70-L95`), and concise code blocks for **core algorithms / branching / state transitions / Compose effects**.
   - **Do NOT duplicate** full production files, verbatim before-anchors, XML string blocks, full test file bodies, or verbatim Markdown doc diffs inside §5 — strings live only in §8, tests only in §10, and doc updates only in §11.
4. **Run the Quality Gate** at the end of `PLAN_TEMPLATE.md`. Fix every failing item **before** presenting.
5. In chat, give only a short pointer to the artifact plus any open questions — do not re-summarize the plan. Resolve all open questions with the user and fold the answers into the plan; §15 must be empty before implementation.

> ⚠️ **Wait for the user's approval** before starting implementation.
> If the user requests corrections or additions, revise the artifact (and re-run the quality gate) first.

---

## Implementation Mode

You are executing an approved plan. **The plan is the specification; do not re-open design decisions.**

### Rules

1. **Ensure the plan is loaded**: if continuing in the same session, you already have it; if resuming after compaction or in a new session, read `implementation_plan.md` first.
2. **Execute all planned edits (`§5`, `§8` strings, `§10` tests, and `§11` docs) in a single continuous pass**:
   - Batch independent file edits in parallel where possible.
   - **Do NOT run intermediate Gradle compile or unit test commands between tasks (`T1`, `T2`, …)** unless you hit an unexpected ambiguity that requires checking compilation. All verification is consolidated into the single `./scripts/verify.sh` run in Step 8.
3. **Follow the planned specifications strictly**:
   - Use the exact signatures, constants, state ownership, Compose effect patterns, and guardrails in §5 and §16.
   - Write all localized strings from §8 across `values/strings.xml`, `values-de/strings.xml`, and `values-zh-rTW/strings.xml`.
   - Implement all unit test cases specified in §10.
   - Apply all documentation and `HelpModal` updates specified in §11.
4. **Stop instead of improvising** if a referenced symbol/signature does not match what the plan assumed, or if a step would require a new architectural or UX decision. Report what you expected, what you found, and 1–2 candidate fixes.
5. **Single verification & self-review pass at the end**: once all code, string, test, and doc edits are applied, proceed to Step 8 (`./scripts/verify.sh` + `megingiard-code-review` pass), then present the final summary with a **Deviations from plan** section (`none` if none), a **Future Improvements** section (`AGENTS.md §5`), and the commit message (`AGENTS.md §4`).

---

### Coding rules (planner writes plan code to satisfy them; implementer checks them while transcribing)

**State & architecture:**

- State singletons (`object`) — only `private MutableStateFlow`, public `StateFlow` (§7.1)
- Use `combine()` for dependent flows, never nested `collect {}` calls (§10.4)
- `SupervisorJob()` for new class-level scopes (§10.1)

**Compose:**

- Never use rapidly-changing values as a `LaunchedEffect` key (§9.1)
- Use `snapshotFlow {}` from `androidx.compose.runtime` for reactive state observation (§9.1)
- All user-visible strings in `strings.xml` (§9.2)
- Icons always have `contentDescription` (§9.3)

**Design system (§16):**

- No inline `fontSize = XX.sp` — always use `MaterialTheme.typography.*`
- No hardcoded `Color(0xFF...)` — always use `LocalAppColors.current.*` or `MaterialTheme.colorScheme.*`
- Extract dimensions to `private val FEATURE_PREFIX_DIMENSION = XX.dp` at file scope

**Code quality:**

- No magic numbers — `private const val` at file scope
- No `android.util.Log` — all logging via `AppLog`
- Every new file needs `private const val TAG = "ClassName"` and full AppLog coverage (§8.4)
- No FQN references in the function body — everything in imports

**Module ownership:**

- Business logic with no Android UI dependency → `:companion:domain` (or `:shared:session` / `:shared:catalog` / `:shared:media`)
- Pure data types / constants / math helpers → `:shared:core`
- Composables / ViewModels / Activities → `:companion:ui`

---

### 7. ✅ Update documentation & help tutorials (mandatory)

After implementation, always synchronize documentation and in-app help. In Implementation Mode, use the drafted text from plan §11 and string keys from §8; the checklist below is the completeness check.

**Extending an existing feature:**

1. Open the relevant `docs/features/<feature>/FEATURE.md`
2. Check whether Functional Requirements or Technical Implementation sections are now outdated
3. Add new requirements and a technical description for the new behavior
4. If the architecture is affected: review `docs/ARCHITECTURE.md`
5. **Update in-app help/tutorials**: if the change modifies user interaction (buttons, view modes, settings, gestures), update the corresponding `HelpModal` files, onboarding/welcome popups, and localized strings (`strings.xml`, `values-de/strings.xml`, `values-zh-rTW/strings.xml`).

**Brand-new feature:**

1. Create `docs/features/<feature>/FEATURE.md` based on `docs/features/FEATURE_TEMPLATE.md`
2. Add a row to the Documentation Map table in `AGENTS.md §2`
3. Check `docs/ARCHITECTURE.md` for impact
4. **Create in-app help/tutorials**: build the help modal and onboarding dialog for the new feature, registering their strings in the localized resources.

---

### 8. ✅ Verify & checklist

1. Run `./scripts/verify.sh` (sandbox bypass). It runs the architecture rule verifier (imports, logging, `MutableStateFlow` visibility, design tokens, translations, …) and all unit tests. Fix every reported finding — never raise a baseline count.
2. Walk through the full **Done checklist in `AGENTS.md` §3.5** (do not rely on a partial copy). Feature-specific emphasis:
   - `FEATURE.md` created / updated and added to the `AGENTS.md` §2 table if new
   - In-app help (`HelpModal`), onboarding, and strings in every locale (`values`, `values-de`, `values-zh-rTW`) updated
   - Every item of plan §9 (edge cases) and §10 (test plan) is implemented, or the deviation is reported
   - New pure logic covered by unit tests

---

## Output Requirements

**Planning Mode**

1. **Interactive design interview**: Conducted one-by-one via `ask_question` tool before drafting the plan
2. **Implementation plan**: `implementation_plan.md` artifact following `references/PLAN_TEMPLATE.md` (`RequestFeedback: true`, `UserFacing: true`), passing its quality gate, approved before coding

**Implementation Mode**

1. **Implemented code**: All changed and new files, mapped to plan task ids
2. **Deviations from plan**: every fix beyond the planned specification, or `none`
3. **Documentation updates**: Which `FEATURE.md` sections were changed and how
4. **Future Improvements**: Observations noted during implementation (`AGENTS.md §5`)
5. **Conventional Commits message** as a copy-paste-ready code block (covering all changes):

```
feat: <short imperative summary>

- <what was implemented>
- <documentation update>
- <additional changes if any>
```

---

## Constraints

- Only run the build/test commands permitted in `AGENTS.md` §3.1 (sandbox bypass enabled).
- Run `./scripts/verify.sh` **once at the end of Implementation Mode** (do not run intermediate Gradle compile/test checks after each sub-task unless resolving an unexpected ambiguity).
- After implementation, always write or update unit tests for new or changed pure logic in `:shared:core` / `:companion:domain`. If logic is not testable without major refactoring, document it as a follow-up task instead of skipping silently.
- **Planning Mode: always conduct an interactive interview via `ask_question` before drafting the plan** — walk down each branch of the design tree one question at a time with recommendations until all requirements are unambiguous.
- **Always present the plan before implementing** — no silent coding
- **Implementation Mode: never re-open design decisions** — stop and report instead (Implementation Mode rule 4)
- Never remove existing functionality without explicit user approval
- Do not design features that cross module boundaries in ways that violate the architecture in `docs/ARCHITECTURE.md`
- **Companion Autonomy:** Never introduce dependencies in Megingiard Companion on Game Focus. Megingiard Companion must function 100% standalone.
- If the feature is too large for a single plan-review cycle: split it into phases and plan each phase separately
