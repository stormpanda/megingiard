---
name: megingiard-code-review
description: "Conduct a thorough code review of the current Git branch or specific files in Megingiard. Evaluates architecture compliance (:app, :domain, :core), AGENTS.md rules, Compose performance (re-compositions, LaunchedEffect keys, draw scopes), state management, thread safety, unit test coverage, documentation sync (FEATURE.md), and provides a structured implementation plan for any findings."
argument-hint: 'Optional git reference or scope (e.g. "feature/ocr-privd-mode" or "app/src/main/java/com/stormpanda/megingiard/privd/")'
---

# Skill: Megingiard Code Review

## Role

You are a Lead Android Architect and Senior Kotlin Engineer on the **Megingiard** project. Your task is to conduct an uncompromising, comprehensive code review of a set of changes (branch, PR, commit range, or specific files) to enforce architectural integrity, clean code, Jetpack Compose performance, resource safety, and project conventions defined in `AGENTS.md`.

---

## Project Context

| Key            | Value                                                                                                        |
| -------------- | ------------------------------------------------------------------------------------------------------------ |
| Package        | `com.stormpanda.megingiard`                                                                                  |
| Language       | Kotlin 2.0+ (no Java files except `:mirrorserver`), Jetpack Compose Material 3                               |
| Modules        | `:app` (UI layer) · `:domain` (business logic, singletons) · `:core` (pure data/schemas) · `:mirrorserver` |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory                                            |
| Permitted tests| `./gradlew :core:test :domain:test :app:testDebugUnitTest :gamefocus:testDebugUnitTest` (Must be run with sandbox bypass enabled, i.e., `BypassSandbox: true` / unsandboxed) |
| Deployment     | **STRICTLY PROHIBITED** from running deployment/install commands or deleting app data without explicit permission |
| Log tag prefix | All app logs are tagged `Mgnrd.*` using `AppLog` with file-scoped `private const val TAG`                    |
| Target Device  | AYN Thor dual-screen handheld (Display 0 = top screen, Display 4 = bottom screen)                           |

---

## User Input

The user requests a code review — either for the current working branch, a specific target branch comparison (e.g., `main..HEAD`), or specific files/packages. If no scope is specified, review all uncommitted changes (`git status`) and recent commits on the current branch compared to `main`.

---

## Code Review Workflow

### 1. ✅ Scope & Diff Inspection

Establish the review target by inspecting Git status and commit history:

```bash
git status
git log main..HEAD --oneline
git diff main..HEAD --stat
```

Identify all modified/created Kotlin files, C sources, resources (`strings.xml`, `values-de/strings.xml`), unit tests, and documentation files (`docs/features/*/FEATURE.md`). Compile the definitive list of changed files.

---

### 2. ✅ Systematic File-by-File Audit & Programmatic Sweeps

To avoid "lost in the middle" attention leaks and ensure a 100% comprehensive check, you **must** execute a two-pass audit process:

1. **Programmatic Grep Checks**: Prior to manual analysis, perform a repository search (restricted to the list of modified files) for known violations:
   - Search for `android.util.Log` to catch standard log leaks.
   - Search for `.*` in imports to catch star imports.
   - Search for `MutableStateFlow` to ensure no public flow exposures.
   - Search for `.values()` to enforce `enum.entries`.
2. **Exhaustive File Checklist**: Systematically open and review **every single modified file** in the change set. Verify that:
   - No magic numbers or hardcoded dimensions are present (all extracted to private file-scope constants).
   - If a file defines a `private const val TAG = "..."`, that tag is actively used in `AppLog` logs (or add logs if missing).
   - Star imports are absent and all FQNs are moved to top-level imports.

---

### 3. ✅ Architectural & Package Boundary Audit

Verify strict adherence to module dependencies (§6 of `AGENTS.md`):

- **`:core`**: Must contain pure Kotlin/JVM data models, schemas, and math helpers. **Zero Android or Compose dependencies**.
- **`:domain`**: Must contain business logic, singleton state holders, input strategy routers, and IPC wrappers. **Must never import Android UI or Composable packages**.
- **`:app`**: Contains UI screens, viewmodels, Composables, Services, and presentation modes.
- **State Singletons (§7.1)**: Ensure state singletons (`AppStateManager`, `ScreenCaptureManager`, etc.) expose only **read-only `StateFlow`** (`val bar: StateFlow<T> = _bar.asStateFlow()`) and keep `MutableStateFlow` private.

---

### 4. ✅ Jetpack Compose & Performance Audit

Inspect all Compose code (`:app` and `:gamefocus` modules) against §9 of `AGENTS.md`:

- **Re-composition Leaks**: Verify that rapidly-changing animation values (e.g., infinite transitions, float animations) do **NOT** cause top-level composable recompositions. State reads for drawing borders, background highlights, or custom paths must occur inside `drawWithCache` / `drawBehind` or custom draw scopes.
  - *Modifier Check:* Audit for color, alpha, or scale state reads inside static modifiers (e.g. `.background(animatedColor)`, `.scale(animatedScale)`). Recommend refactoring to `.drawBehind { drawRect(animatedColor) }` or `.graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }` to bypass composition.
  - *Main Thread Blockers:* Ensure click/callback handlers never trigger synchronous file operations (e.g. `file.writeText()`, `file.delete()`) directly on the main UI thread. All disk I/O must be offloaded to `Dispatchers.IO`.
- **`LaunchedEffect` Keys**: Ensure animation values or rapidly-updating states are never used as `LaunchedEffect` keys. Use `snapshotFlow { ... }.collectLatest { }` inside a `LaunchedEffect(Unit)` instead.
- **Bitmap & Graphics Lifecycle (§7.3)**: Verify `ScreenCaptureManager` bitmap recycling contracts. Ensure hardware buffers (`HardwareBuffer`, `PixelCopy`) are safely closed in `try ... finally` or `.use {}` blocks.
- **Accessibility & Localization**: Ensure all `Icon` instances have explicit `contentDescription` (string resource or `null`). All visible UI strings must exist in both `strings.xml` and `values-de/strings.xml`.

---

### 5. ✅ Coding Conventions & AGENTS.md Compliance

Audit every line of code against §8 of `AGENTS.md`:

- **Imports (§8.2)**: No star imports (`import foo.*`). No inline fully-qualified names in code bodies — all moved to top-level `import` statements.
- **Constants (§8.3)**: Extract all magic numbers to named constants (`private const val`). File-scoped UI colors must use feature-prefixed `SCREAMING_SNAKE_CASE` (e.g., `GS_BG`, `SW_GAP`).
- **Logging (§8.4)**: Zero calls to `android.util.Log`. All logging routed through `AppLog` with a file-scoped `private const val TAG`. Mandatory logging at lifecycle milestones, error branches, and state mutations. No continuous per-frame event logging. **If an unused `TAG` constant is found in a file, logging MUST be added using `AppLog` (do NOT remove `TAG`) to fulfill logging coverage requirements.**
- **Kotlin Features (§8.1)**: Use `enum.entries` (never `enum.values()`). Use `kotlin.math.min`/`max`. Avoid anonymous destructuring of `Triple` in lambdas.

---

### 6. ✅ Unit Test & Documentation Sync Audit

- **Test Suite Execution (§3)**: Run `./gradlew :core:test :domain:test :app:testDebugUnitTest :gamefocus:testDebugUnitTest` to verify test suite health (MUST always be run with the sandbox bypass enabled, i.e., `BypassSandbox: true` / unsandboxed).
- **Test Set Placement**: Ensure pure JVM tests are placed in `:core/src/test/` or `:domain/src/test/`. If a unit test has no Android SDK dependencies, prefer fast pure JUnit over Robolectric.
- **Documentation Sync (§2 & §5)**: Identify which `docs/features/<feature>/FEATURE.md` owns the modified code. Ensure Functional Requirements and Technical Implementation details accurately reflect all behavioral changes.
  - *Requirements Discrepancy:* If the codebase diverges from requirements, evaluate whether the implementation is correct and the *documentation* should be updated, rather than assuming code is wrong.

---

## Output Format

Present the code review using clear, structured GitHub Markdown:

1. **Executive Summary**: High-level overview of changes, overall quality score, and key focus areas.
2. **Architectural Audit Matrix**: Table mapping each module requirement to PASS / FAIL status with notes.
3. **Key Highlights & Clean Code Wins**: Note well-implemented patterns, thorough tests, or clean refactorings.
4. **Detailed Findings (Categorized by Severity)**:
   - **High Severity**: Critical crashes, memory leaks (`HardwareBuffer`/`Bitmap`), major Compose recomposition performance degradation, or architecture boundary violations.
   - **Medium Severity**: Missing try-finally resource cleanup, incorrect test runner usage, unhandled nullability edge cases, missing localized strings.
   - **Low Severity**: Code style nitpicks, unused imports, naming inconsistencies.
    Provide exact file paths with line numbers (e.g., [`PrivdSetupWizard.kt`](file:///path/to/PrivdSetupWizard.kt#L79-L116)) and code snippets with recommended refactorings.
5. **Verification & Test Status**: Test execution output summary (`./gradlew` results).
6. **Implementation Plan for Findings**: (Mandatory if any High, Medium, or Low severity findings are present) A structured, step-by-step implementation plan detailing the exact refactoring or code changes needed to fix all reported findings.
7. **Conventional Commit Proposal**: Copy-paste ready commit message covering all changes per `AGENTS.md §4`.

---

## Constraints

- **Never modify code silently** during a code review pass — present findings to the user first unless explicitly asked to auto-fix.
- **Never skip unit test execution** — execute permitted unit test commands to verify test suite status.
- **Never propose code fixes that break existing animations, layout math, or visual styling**.
