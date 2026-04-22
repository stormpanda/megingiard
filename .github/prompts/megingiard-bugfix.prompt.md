---
mode: agent
description: "Megingiard – Analyze and fix a bug (with ADB logs from the Thor device)"
tools:
  - codebase
  - editFiles
  - problems
  - terminal
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
| Modules        | `:app` (UI) · `:domain` (business logic) · `:core` (pure data)             |
| Coding rules   | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Build policy   | **Never run `./gradlew`** — static analysis only (imports, symbols, types) |
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
- Respect the module structure from `AGENTS.md §3`:
  - State singletons → `:domain`
  - Composables / ViewModels → `:app`
  - Data models → `:core`

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
- No new code without a `TAG` constant and the mandatory log coverage (§5.4)
- Correct import statements — no FQN references in the function body

---

### 7. ⚡ Update documentation (if behavior changes)

If the fix changes **externally visible behavior** of a component:

1. Identify the relevant `docs/features/<feature>/FEATURE.md`
2. Update Functional Requirements or Technical Implementation accordingly
3. For architecturally significant changes: also review `docs/ARCHITECTURE.md`

Pure internal bug fixes with no behavioral change do not require documentation updates.

---

### 8. ✅ Static analysis & checklist

Perform a static review — no build command:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] All new references as imports — no FQN in the function body
- [ ] No magic numbers — `private const val` used
- [ ] No `android.util.Log` — all logging via `AppLog`
- [ ] Every new file has `private const val TAG` + `AppLog` usage
- [ ] All user-visible strings in `strings.xml`
- [ ] Icons have `contentDescription`
- [ ] `SupervisorJob()` for new class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] No suspected compile errors

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

- Never run `./gradlew` or any other build command
- Never use `android.util.Log` directly
- Do not remove or refactor any functionality unrelated to the bug
- If the bug cannot be clearly located: explicitly state what is still unclear and ask the user for more information
