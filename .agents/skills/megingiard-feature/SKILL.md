---
name: megingiard-feature
description: "Plan and implement a new feature for the Megingiard Android app. Use when: adding new user-visible behavior, extending existing features (MacroPad, Mirror, Keyboard, Touchpad, Settings), or wiring up new Compose screens. Reads existing docs, presents an implementation plan for approval, implements code, and synchronizes FEATURE.md documentation."
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
| Modules        | `:app` (UI) · `:domain` (business logic) · `:core` (pure data)             |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Build policy   | **Never run `./gradlew`** — static analysis only (imports, symbols, types) |
| Log tag prefix | All app logs are tagged `Mgnrd.*`                                          |
| ADB path       | `~/Library/Android/sdk/platform-tools/adb`                                 |

---

## User Input

The user describes the feature informally — e.g. "I'd like to delete buttons by swiping right" or "add a dark-mode toggle to the settings screen". No structured template required.

---

## Steps

### 1. ✅ Understand the feature

Read the user's description carefully. Establish internally:

- What should the feature do? (user-visible behavior)
- Which area of the app is affected? (MacroPad, Mirror, Keyboard, Touchpad, Settings, …)
- Is this an extension of an existing feature or something entirely new?

---

### 2. ✅ Read existing documentation

Identify the affected feature area and read the relevant documentation in full:

**Documentation map** (from `AGENTS.md §2`):

| Area                     | Documentation                       |
| ------------------------ | ----------------------------------- |
| Screen Mirror            | `docs/features/mirror/FEATURE.md`   |
| Virtual Touchpad         | `docs/features/touchpad/FEATURE.md` |
| Virtual Keyboard         | `docs/features/keyboard/FEATURE.md` |
| MacroPad / Layout Editor | `docs/features/macropad/FEATURE.md` |
| Pill Menu                | `docs/features/pillmenu/FEATURE.md` |
| Design System / Theming  | `docs/features/theming/FEATURE.md`  |
| Config Export/Import     | `docs/features/config/FEATURE.md`   |
| Overall architecture     | `docs/ARCHITECTURE.md`              |

Also read:

- `docs/REQUIREMENTS.md` — non-functional requirements
- The relevant section of `AGENTS.md` (e.g. §9 for Compose, §7 for state, §12 for resources)

Goal: a complete picture of what already exists in this area and how it works.

---

### 3. ✅ Survey the codebase

Explore the affected code area:

- Read the relevant files from the module tree (`AGENTS.md §6`)
- Identify:
  - Which **state singletons** (`object` in `:domain`) are involved?
  - Which **ViewModels** (`/viewmodel/`) coordinate this area?
  - Which **Composables** (`/app/`) render it?
  - Which **data models** (`:core`) may need extending?
- Look for similar existing features as an implementation reference (e.g. how other buttons/actions were added)

---

### 4. ✅ Create and present an implementation plan

**Write a structured plan** with the following sections and present it to the user **before writing any code**:

```
## Implementation Plan: <Feature Name>

### Goal
<One sentence describing what the feature achieves.>

### Affected files
- `path/to/File.kt` — what changes here
- ...

### New files (if needed)
- `path/to/NewFile.kt` — purpose

### Data model changes (if needed)
- <Class> in `:core`: <what is added>

### State changes (if needed)
- <Manager> in `:domain`: <new StateFlows or methods>

### Implementation order
1. Step 1 (e.g. extend data model)
2. Step 2 (e.g. update state singleton)
3. Step 3 (e.g. add ViewModel logic)
4. Step 4 (e.g. render in Composable)
5. Step 5 (e.g. strings, docs)

### Open questions / assumptions
- <If anything is unclear, state it explicitly here>
```

> ⚠️ **Wait for the user's approval** before starting implementation.
> If the user requests corrections or additions to the plan, revise it first.

---

### 5. ✅ Implement the feature

Implement according to plan. Follow `AGENTS.md` strictly:

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

- Business logic with no Android UI dependency → `:domain`
- Pure data types / constants → `:core`
- Composables / ViewModels / Activities → `:app`

---

### 6. ✅ Update documentation (mandatory)

After implementation, always synchronize documentation:

**Extending an existing feature:**

1. Open the relevant `docs/features/<feature>/FEATURE.md`
2. Check whether Functional Requirements or Technical Implementation sections are now outdated
3. Add new requirements and a technical description for the new behavior
4. If the architecture is affected: review `docs/ARCHITECTURE.md`

**Brand-new feature:**

1. Create `docs/features/<feature>/FEATURE.md` based on `docs/features/FEATURE_TEMPLATE.md`
2. Add a row to the Documentation Map table in `AGENTS.md §2`
3. Check `docs/ARCHITECTURE.md` for impact

---

### 7. ✅ Static analysis & checklist

Perform a static review — no build command:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] All new references as imports — no FQN in function bodies
- [ ] No magic numbers — `private const val` used
- [ ] No `android.util.Log` — all logging via `AppLog`
- [ ] Every new file has `private const val TAG` + `AppLog` usage per §8.4
- [ ] All user-visible strings in `strings.xml`
- [ ] Icons have `contentDescription`
- [ ] `SupervisorJob()` for new class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] Bitmap recycling handled by the manager, not call sites
- [ ] `snapshotFlow` imported from `androidx.compose.runtime`
- [ ] Deprecated APIs annotated with `@Suppress("DEPRECATION")`
- [ ] New Activities launched on correct display via `ActivityOptions.setLaunchDisplayId()`
- [ ] `Presentation` mode switches use `hide()`/`show()`, not `dismiss()`
- [ ] Service `onStartCommand` returns `START_NOT_STICKY` (for new Services)
- [ ] `FEATURE.md` updated / created
- [ ] No suspected compile errors
- [ ] New or changed pure logic is covered by unit tests in `:core` or `:domain`
- [ ] Existing tests updated if the change modifies previously-tested behaviour
- [ ] `./gradlew :core:test :domain:test` executed and all tests pass

---

## Output Requirements

1. **Implementation plan** (presented before coding — see Step 4)
2. **Implemented code**: all changed and new files with an explanation per file
3. **Documentation updates**: which `FEATURE.md` sections were changed and how
4. **Conventional Commits message** as a copy-paste-ready code block (covering all changes):

```
feat: <short imperative summary>

- <what was implemented>
- <documentation update>
- <additional changes if any>
```

---

## Constraints

- Never run `./gradlew` or any other build command
- **One exception:** `./gradlew :core:test :domain:test` **must** be run after every implementation to verify all unit tests pass. This is the only permitted Gradle invocation.
- After implementation, always write or update unit tests for new or changed pure logic in `:core` / `:domain`. If logic is not testable without major refactoring, document it as a follow-up task instead of skipping silently.
- **Always present the plan before implementing** — no silent coding
- Never remove existing functionality without explicit user approval
- Do not design features that cross module boundaries in ways that violate the architecture in `docs/ARCHITECTURE.md`
- If the feature is too large for a single plan-review cycle: split it into phases and plan each phase separately
