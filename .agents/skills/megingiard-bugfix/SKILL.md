---
name: megingiard-bugfix
description: "Analyze and fix a bug in the Megingiard Android app. Use when: diagnosing crashes, unexpected behavior, or ADB logcat errors on the AYN Thor device. Fetches device logs, traces the root cause through the Kotlin/Compose codebase, implements a clean fix, and proposes a Conventional Commits message."
argument-hint: 'Describe the bug (e.g. "app crashes when opening the macro editor")'
---

# Skill: Megingiard Bug Fix

## Role

You are an experienced Android/Kotlin engineer with deep knowledge of the **Megingiard** project. Your task is to systematically analyze a reported bug — starting from real device logs — and then implement a clean, rule-compliant fix.

---

## Project Context

| Key            | Value                                                                      |
| -------------- | -------------------------------------------------------------------------- |
| Package        | `com.stormpanda.megingiard`                                                |
| Language       | Kotlin 2.0+, Jetpack Compose Material 3                                    |
| Modules        | `:companion:ui` (UI) · `:companion:domain` (business logic) · `:shared:*` |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Standalone Rule| Companion has ZERO dependencies on Game Focus. Never fix Companion bugs via Game Focus! |
| Build policy   | Only the commands permitted in `AGENTS.md` §3.1 (`./scripts/run_unit_tests.sh`, compile check) — always with sandbox bypass |
| Log tag prefix | All app logs are tagged `Mgnrd.*`                                          |
| ADB path       | `~/Library/Android/sdk/platform-tools/adb`                                 |

---

## User Input

The user describes the bug informally — e.g. "when I do X, Y happens instead of Z" or "the app crashes when opening the editor". No structured template required.

---

## Steps

### 1. ✅ Understand the bug

Read the user's description carefully. Establish internally:

- Which feature / component is affected?
- What is the expected vs. observed behavior?
- Is there a reproducible trigger?
- **Is the bug in Megingiard Companion?** If the issue affects Megingiard Companion (`:companion:*`), it MUST be treated as a standalone app with ZERO dependencies on Game Focus (`:gamefocus:*`). Game Focus is an optional, unreleased launcher; users are not forced to use it. All Companion bugs must be fixed entirely within Companion or `:shared:*` modules.

---

### 2. ✅ Fetch ADB logs (if Thor is reachable)

First check whether the device is connected:

```bash
~/Library/Android/sdk/platform-tools/adb devices
```

If a device is listed as `device` (not `offline`), fetch the logs:

```bash
~/Library/Android/sdk/platform-tools/adb logcat -d -v time 2>/dev/null \
  | grep -E "Mgnrd\.|AndroidRuntime|FATAL|beginning of crash" \
  | tail -400
```

> **Filter explanation:**
>
> - `Mgnrd.*` — all app-owned logs (routed through `AppLog`)
> - `AndroidRuntime` / `FATAL` / `beginning of crash` — JVM crashes and stack traces
> - `-d` — dump the current buffer and exit (no streaming)
> - `-v time` — include a timestamp on every line

If no device is reachable, proceed without logs and note that explicitly.

---

### 3. ✅ Analyze the logs

Look for:

- `E/Mgnrd.*` — error entries from the app
- `W/Mgnrd.*` — warnings (may precede the bug)
- Stack traces under `AndroidRuntime` / `FATAL`
- Temporal sequence: what happens just _before_ the failure?
- Which components (e.g. `Mgnrd.MacroPadState`, `Mgnrd.ScreenCaptureService`) are involved?

Formulate a **first hypothesis** about the root cause.

---

### 4. ✅ Explore the codebase

Navigate from the hypothesis to the relevant files:

- Search for the affected class/function using semantic search or grep
- Read the relevant source files (ViewModel, Manager, Composable, Service)
- Respect the module structure from `AGENTS.md §6`:
  - State singletons → `:companion:domain` (or `:shared:catalog` / `:shared:session` / `:shared:media`)
  - Composables / ViewModels → `:companion:ui`
  - Data models → `:shared:core`

Also check:

- `AGENTS.md` — whether one of its rules explains the cause (common cases: wrong `LaunchedEffect` key, exposed `MutableStateFlow`, wrong scope type)
- The relevant `docs/features/<feature>/FEATURE.md` for context on the affected feature

---

### 5. ✅ Determine the root cause

State clearly:

- The exact line / function where the bug originates
- Why that code produces the wrong behavior
- Whether it is a logic error, lifecycle error, race condition, or API misuse

---

### 6. ✅ Implement the fix

Apply the correction directly in the code. Follow `AGENTS.md` strictly:

- Only the necessary change — no opportunistic refactoring
- No new magic numbers — extract to `private const val`
- Logging via `AppLog` (never `android.util.Log`)
- No new code without a `TAG` constant and the mandatory log coverage (§8.4)
- Correct import statements — no FQN references in the function body
- **Zero Game Focus dependency:** If fixing a Megingiard Companion bug, do NOT touch `:gamefocus:*` or rely on Game Focus behavior. Fix the issue strictly within `:companion:*` or `:shared:*`.

---

### 7. ⚡ Update documentation (if behavior changes)

If the fix changes **externally visible behavior** of a component:

1. Identify the relevant `docs/features/<feature>/FEATURE.md`
2. Update Functional Requirements or Technical Implementation accordingly
3. For architecturally significant changes: also review `docs/ARCHITECTURE.md`

Pure internal bug fixes with no behavioral change do not require documentation updates.

---

### 8. ✅ Verify & checklist

1. Run `./scripts/verify.sh` (sandbox bypass). It runs the architecture rule verifier (imports, logging, `MutableStateFlow` visibility, design tokens, translations, …) and all unit tests. Fix every reported finding — never raise a baseline count.
2. Walk through the full **Done checklist in `AGENTS.md` §3.5** (do not rely on a partial copy). Bugfix-specific emphasis:
   - Companion autonomy: a Companion fix must not modify or depend on Game Focus (§6.1)
   - A regression test reproduces the bug if the fix touches pure logic

---

## Output Requirements

1. **Bug analysis**: Short summary — root cause and affected component(s)
2. **Implemented fix**: All changed files with an explanation of each change
3. **Conventional Commits message** as a copy-paste-ready code block:

```
fix: <short imperative summary>

- <what was changed and why>
- <additional changes if any>
```

---

## Constraints

- Only run the build/test commands permitted in `AGENTS.md` §3.1 (sandbox bypass enabled).
- `./scripts/run_unit_tests.sh` **must** be run after every fix to verify all unit tests pass.
- If the fix touches pure logic in `:shared:core` / `:companion:domain`, always write a regression test covering the fixed behaviour. If not testable without major refactoring, document it as a follow-up.
- Never use `android.util.Log` directly
- Do not remove or refactor any functionality unrelated to the bug
- **Never fix Megingiard Companion bugs via Game Focus:** Megingiard Companion is a standalone app. Game Focus is currently unreleased and optional; users are not forced to use Game Focus. Any bug in Megingiard Companion MUST be resolved with Megingiard Companion treated as a standalone app with zero dependencies on Game Focus. Never modify `:gamefocus:*` to fix or work around a Companion issue.
