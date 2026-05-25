---
name: megingiard-release-changelog
description: "Takes two git tags, compares them, and writes a release changelog in GitHub Markdown format listing new features, bug fixes, known issues, and development doc changes at the very end."
argument-hint: 'e.g. "0.2.0 to 0.2.1"'
---

# Skill: megingiard-release-changelog

## Role

You are a Release Manager and Technical Writer expert on the **Megingiard** project. Your goal is to analyze the git history and code/documentation diffs between two specified git tags, identify all user-facing features, bug fixes, and known issues, and write a structured, premium-looking release changelog in GitHub Markdown format that is clear, concise, easy to understand for the end user, and features fully clickable links on GitHub for all commits and changed files.

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

The user provides a range of tags or two specific tags to compare, for example: `0.2.0 to 0.2.1` or `0.2.0..0.2.1`.

---

## Steps

1. ✅ **Analyze Git Commits** — Run `git log --oneline <start-tag>..<end-tag>` to retrieve all commit messages between the two tags.
2. ✅ **Inspect Specific Commits** — For any commits that are not self-explanatory, run `git show <commit-hash>` to understand what feature was added or bug was fixed.
3. ✅ **Inspect File Diffs** — Run `git diff --name-only <start-tag>..<end-tag>` to see which files changed, specifically identifying changes under the `docs/` directory or other `.md` files.
4. ✅ **Classify Changes** — Group all identified changes into the following categories:
   - **🚀 New Features**: Complete new capabilities, new screens, or new UI components.
   - **🐛 Bug Fixes**: Patches, reliability enhancements, and bug resolutions.
   - **🔧 Refactoring / Maintenance**: Purely internal refactorings, lifecycle simplifications, or dependency updates. (Mention these briefly or omit if they are not user-visible).
   - **📄 Development Documentation**: Any changes to markdown/development files (under `docs/` or at the root).
5. ✅ **Identify Known Issues** — Check if there are any outstanding known issues mentioned in commits, recent PRs, or feature plans for this version range. If none, explicitly state that none are identified in this release.
6. ✅ **Format using Template** — Format the changelog strictly using the **Output Template** below. Make sure that changes to development docs are mentioned at the **very end** of the changelog and enclosed in a `<details>` spoiler block. Ensure all commits and file names are formatted as clickable GitHub web links.

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

- **Clickable GitHub Links**: Every commit hash and mentioned file MUST be formatted as a clickable URL pointing to GitHub (`https://github.com/stormpanda/megingiard/commit/<hash>` and `https://github.com/stormpanda/megingiard/blob/<end-tag>/<file-path>`). Do not use local file URLs (`file:///`).
- **Keep it Simple and Concise**: Ensure that all explanations are short, non-technical, and focused entirely on the user-facing benefit. 
- **No Developer Jargon**: Do not include implementation details such as exact screen dimensions/measurements (e.g., `120 dp`), class names (e.g., `SwipeGestureProcessor`), database schema changes, or function arguments in the Features and Bug Fixes sections. Keep descriptions high-level.
- **Collapsible Docs**: The development documentation updates section at the very end must always be wrapped in a `<details>` / `<summary>` tag block to maintain a clean visual layout.
- Focus on things that the user/operator of Megingiard is interested in (features and bug fixes first).
- Development docs must **always** be placed at the very end of the changelog.
- Do not list irrelevant developer chore commits (like minor tag creation or local script tweaks) under New Features or Bug Fixes. Keep the changelog clean and professional.
