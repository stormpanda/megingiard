---
name: megingiard-skill-template
description: "Template for creating a new Megingiard agent skill. Use when: authoring a new SKILL.md for a repeatable Megingiard workflow. Copy this file, fill in the placeholders, and place it under .github/skills/<name>/SKILL.md."
argument-hint: 'Name of the new skill (e.g. "megingiard-refactor")'
---

# Skill: `<Name>`

> **How to use this template**
>
> 1. Create a folder `.github/skills/<slug>/` and place this file there as `SKILL.md`.
> 2. Set `name:` in the frontmatter to exactly `<slug>` (must match the folder name).
> 3. Fill in every `<placeholder>` section.
> 4. Delete this note block and any unused sections.
> 5. Confirm the skill appears in the VS Code slash-command picker (`/`) before shipping.

---

## Role

_One paragraph. Define the agent's persona, expertise, and goal for this skill._

Example: "You are an expert Android/Kotlin engineer working on the **Megingiard** project. Your goal is to …"

---

## Project Context (mandatory — include verbatim in every skill)

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

_Describe what the user provides when invoking this skill._

Example: "The user provides a free-form description of …"

---

## Steps

> Each step must be a distinct, ordered action. Keep step descriptions brief and action-oriented.
> Mark mandatory steps with ✅. Mark optional/conditional steps with ⚡.

1. ✅ **Step name** — what to do and why.
2. ✅ **Step name** — what to do and why.
3. ⚡ **Step name (conditional)** — when and why this step applies.
4. ✅ **Step name** — what to do and why.

---

## Output Requirements

- _What must the agent produce at the end of the skill._
- Always conclude with a **Conventional Commits** message proposal (see `AGENTS.md §4`).
  Format: `<type>: <short summary>\n\n- bullet 1\n- bullet 2`

---

## Constraints

- _List hard rules specific to this skill that differ from or add to `AGENTS.md`._
- Example: "Do not modify files outside the `:app` module."
- Example: "Always present the implementation plan before writing code."

---

## Mandatory Completion Checklist (from `AGENTS.md §3`)

Before marking the task done, verify:

- [ ] No `MutableStateFlow` exposed outside its owning singleton
- [ ] No FQN references inline — all moved to imports
- [ ] No magic numbers — extracted to named constants
- [ ] No `android.util.Log` — all logging via `AppLog`
- [ ] Every new file has `private const val TAG` and uses `AppLog`
- [ ] All user-visible strings in `strings.xml`
- [ ] All Icons have `contentDescription`
- [ ] `SupervisorJob()` used for class-level scopes
- [ ] Scope cancelled in `onDestroy()`
- [ ] No suspected compile errors (verified by static analysis)
