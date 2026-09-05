---
name: megingiard-release-changelog
description: "Takes two git tags, compares them, and writes a release changelog in GitHub Markdown format listing new features, bug fixes, known issues, and development doc changes at the very end (strictly excluding unreleased Game Focus changes)."
argument-hint: 'e.g. "0.2.0 to 0.2.1"'
---

# Skill: megingiard-release-changelog

## Role

You are a Release Manager and Technical Writer expert on the **Megingiard** project. Your goal is to analyze the git history and code/documentation diffs between two specified git tags, identify all user-facing features, bug fixes, and known issues for **Megingiard Companion** (the sole publicly released product), and write a structured, premium-looking release changelog in GitHub Markdown format that is clear, concise, easy to understand for the end user, and features fully clickable links on GitHub for all commits and changed files.

> [!IMPORTANT]
> **Game Focus is NOT public:** Game Focus (`:gamefocus:*`, `gamefocus/`) is an unreleased, in-development launcher application. Public releases and release notes are strictly for Megingiard Companion. Under no circumstances should Game Focus features, bug fixes, optimizations, or documentation be included in public release notes.

---

## Project Context (mandatory — include verbatim in every skill)

| Key             | Value                                                                      |
| --------------- | -------------------------------------------------------------------------- |
| Package         | `com.stormpanda.megingiard`                                                |
| Language        | Kotlin 2.0+, Jetpack Compose Material 3                                    |
| Modules         | 10 Feature-First modules (`:companion:ui`, `:companion:domain`, `:shared:*`) |
| Standalone Rule | Companion has ZERO dependencies on Game Focus. Game Focus is unreleased; never include Game Focus in public releases or changelogs! |
| Coding rules    | **`AGENTS.md`** at workspace root — treat every rule as mandatory          |
| Build policy    | **Never run `./gradlew`** — static analysis only (imports, symbols, types) |
| Log tag prefix  | All app logs are tagged `Mgnrd.*`                                          |
| ADB path        | `~/Library/Android/sdk/platform-tools/adb`                                 |

---

## User Input

The user provides a range of tags or two specific tags to compare, for example: `0.2.0 to 0.2.1` or `0.2.0..0.2.1`.

---

## Steps

1. ✅ **Analyze Git Commits & Filter Game Focus** — Run `git log --oneline <start-tag>..<end-tag>` to retrieve all commit messages between the two tags.
   **Game Focus Filtering (MANDATORY):** Game Focus is NOT released to the public. You MUST filter out and omit all commits, PRs, and changes that pertain to Game Focus:
   - Commits tagged with `(gamefocus)` (e.g. `feat(gamefocus): ...`, `fix(gamefocus): ...`, `perf(gamefocus): ...`).
   - Commits with titles referencing Game Focus or launcher functionality (e.g. `Feature/top screen launcher`, `Feature/game focus library`, `Feature/game focus emulator support`, `Feature/alphabetical browsing`, etc.).
   - Commits whose file diffs exclusively touch `gamefocus/` or `:gamefocus:*` modules.
   - Any features or bug fixes that are exclusively for the Game Focus launcher. Only changes affecting **Megingiard Companion** or companion-relevant shared modules (`:shared:*`, `:companion:*`, `:mirrorserver`) should be included.
2. ✅ **Inspect Specific Commits** — For any commits that are not self-explanatory, run `git show <commit-hash>` to understand what feature was added or bug was fixed. Verify that the change applies to Companion and not Game Focus.
3. ✅ **Inspect File Diffs** — Run `git diff --name-only <start-tag>..<end-tag>` to see which files changed, specifically identifying changes under the `docs/` directory or other `.md` files.
   **Omit Game Focus files:** Ignore any changed files under `gamefocus/` and documentation under `docs/features/gamefocus/`.
4. ✅ **Classify Changes** — Group all identified Companion changes into the following categories:
   - **🚀 New Features**: Complete new capabilities, new screens, or new UI components for Megingiard Companion.
   - **🐛 Bug Fixes**: Patches, reliability enhancements, and bug resolutions for Megingiard Companion.
   - **🔧 Refactoring / Maintenance**: Purely internal refactorings, lifecycle simplifications, or dependency updates for Megingiard Companion. (Mention these briefly or omit if they are not user-visible).
   - **📄 Development Documentation**: Any changes to markdown/development files (under `docs/` or at the root), strictly excluding `docs/features/gamefocus/`.
5. ✅ **Identify Known Issues** — Check if there are any outstanding known issues mentioned in commits, recent PRs, or feature plans for this version range. If none, explicitly state that none are identified in this release.
6. ✅ **Format using Template** — Format the changelog strictly using the **Output Template** below. Make sure that changes to development docs are mentioned at the **very end** of the changelog and enclosed in a `<details>` spoiler block (do NOT include Game Focus documentation). Ensure all commits and file names are formatted as clickable GitHub web links.

---

## Output Template

Your generated changelog MUST follow this template precisely:

```markdown
# Megingiard Release Notes — <start-tag> to <end-tag>

[Short, engaging summary of the release — highlighting the primary theme/focus of this update]

## 🚀 New Features

- **<Feature Name>** ([<commit-hash-short>](https://github.com/stormpanda/megingiard/commit/<commit-hash>)): <Short, high-level, user-friendly explanation of what the feature does and how it helps the user. Avoid developer jargon, internal class names, or exact code parameters.>
- ...

## 🐛 Bug Fixes

- **<Fix Name>** ([<commit-hash-short>](https://github.com/stormpanda/megingiard/commit/<commit-hash>)): <Short, high-level, user-friendly explanation of the fix, the problem the user experienced, and how it was resolved. Do not include implementation details like measurements or code variables.>
- ...

## ⚠️ Known Issues

- <Describe any known issues for this release, or write "None identified in this release" if there are no known issues.>

---

## 📄 Development & Documentation Updates

<details>
  <summary>View Documentation Details</summary>
  
  _The following development docs and specifications were updated in this release:_

  - **<Document Title>** ([<file-basename>](https://github.com/stormpanda/megingiard/blob/<end-tag>/<file-path>)): <Brief description of the documentation updates made and why.>
  - ...
  
</details>
```

---

## Output Requirements

- The changelog must be written in **English**.
- The formatting must be clean, structured, and premium GitHub Markdown.
- Always conclude with a **Conventional Commits** message proposal for committing this changelog.
  Format: `docs(changelog): add release notes for <end-tag>`

---

## Constraints

- **Strict Exclusion of Game Focus**: Megingiard Companion is the primary standalone product released to the public. Game Focus (`gamefocus/`, `:gamefocus:ui`, `:gamefocus:domain`, `docs/features/gamefocus/`) is currently NOT released to the public. You MUST strictly exclude all commits, PRs, features, bug fixes, performance improvements, and documentation related to Game Focus from the changelog. The public release notes must never mention Game Focus or include Game Focus links or details.
- **Clickable GitHub Commit Links**: Every commit hash and mentioned file MUST be formatted as a clickable URL pointing to GitHub (`https://github.com/stormpanda/megingiard/commit/<hash>` and `https://github.com/stormpanda/megingiard/blob/<end-tag>/<file-path>`). Do not use local file URLs (`file:///`).
- **Commits on main Only**: You MUST only link commit hashes that exist directly on the `main` branch (such as merge commits or commits present in `main`'s history). Never reference transient commits from topic or unmerged feature branches.
- **No Raw PR Numbers**: NEVER output raw PR numbers like `(#78)` or `#78`. Always resolve the actual commit hash(es) on `main` associated with the change and format them as clickable commit links (`([`hash-short`](https://github.com/stormpanda/megingiard/commit/hash))`).
- **Keep it Simple and Concise**: Ensure that all explanations are short, non-technical, and focused entirely on the user-facing benefit. 
- **No Developer Jargon**: Do not include implementation details such as exact screen dimensions/measurements (e.g., `120 dp`), class names (e.g., `SwipeGestureProcessor`), database schema changes, or function arguments in the Features and Bug Fixes sections. Keep descriptions high-level.
- **Collapsible Docs**: The development documentation updates section at the very end must always be wrapped in a `<details>` / `<summary>` tag block to maintain a clean visual layout.
- Focus on things that the user/operator of Megingiard is interested in (features and bug fixes first).
- Development docs must **always** be placed at the very end of the changelog.
- Do not list irrelevant developer chore commits (like minor tag creation or local script tweaks) under New Features or Bug Fixes. Keep the changelog clean and professional.


