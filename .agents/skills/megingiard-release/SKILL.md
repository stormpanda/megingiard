---
name: megingiard-release
description: "Orchestrate the Megingiard app release process: prepare the release, build the signed release APK, generate its SHA-256 checksum, automatically compile a release changelog, upload a release draft to GitHub, and bump the version for future development."
---

# Skill: megingiard-release

## Role

You are a **Release Manager** expert on the **Megingiard** project. Your goal is to guide the repository through a seamless release cycle. You will safely modify the app's Gradle configurations, execute compilation pipelines, coordinate git version tags, generate premium-grade release changelogs using git history, publish draft releases containing signing credentials verification to GitHub using the GitHub CLI, and transition the repository back into active development mode with a bumped development version.

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

The user requests to initiate a release. No parameters are strictly required as all configurations (current version, next codes, and credentials) are dynamically parsed from `app/build.gradle.kts` and `local.properties`.

---

## Steps

Follow these sequential steps precisely:

1. ✅ **Prepare Git Status** — Check that the local repository is on `main` and has a clean status (`git status --porcelain` is empty). Inform the user if any unstaged modifications exist.
2. ✅ **Execute Prepare Phase** — Run `scripts/release.sh prepare`. This extracts the release version (by dropping the `-SNAPSHOT` suffix), edits `app/build.gradle.kts`, commits the change, tags the commit, and pushes the tag to GitHub.
   - **Update Message**: Print the update exactly as: `Release version <version> successfully prepared and tagged.`
3. ✅ **Execute Build Phase** — Run `scripts/release.sh build`. This compiles the signed release APK using the keystore credentials specified in `local.properties`, copies it to `app/release/`, and calls the checksum generator.
   - **Update Message**: Print the update exactly as: `Release Build <version> APK successfully created and signed.`
4. ✅ **Determine Previous Tag** — Find the previous git tag immediately before the current release version (e.g. by running `git describe --tags --abbrev=0 HEAD~1` or inspecting `git tag -l --sort=-v:refname` and skipping the current version).
5. ✅ **Generate Changelog** — Run the analysis steps defined in the `megingiard-release-changelog` skill comparing the `<previous-tag> to <current-tag>`.
   - Write this generated premium markdown changelog into a temporary file at `.tmp/release_changelog.md` (creating `.tmp/` if it doesn't exist).
6. ✅ **Execute Publish Phase** — Run `scripts/release.sh publish .tmp/release_changelog.md`. This reads the changelog file and uses the `gh` CLI to upload the draft release to GitHub with the signed APK and checksum file.
   - **Update Message**: Print the update exactly as: `Release draft Megingiard-v<version> successfully uploaded with APK and checksum.`
7. ✅ **Execute Bump Phase** — Run `scripts/release.sh bump`. This increments `versionCode` in `app/build.gradle.kts` by 1, bumps the `versionName` to the next minor version plus `-SNAPSHOT` (e.g. `0.3.0` -> `0.4.0-SNAPSHOT`), commits this change, and pushes the new commit to GitHub.
   - **Update Message**: Print the update exactly as: `Successfully bumped development version to <next-version> (code: <next-code>).`
8. ✅ **Reporting & Cleanup** — Provide the user with a summary of the draft release, pointing them to their GitHub Releases dashboard to review and publish the draft. Delete the temporary `.tmp/release_changelog.md` file.

---

## Output Requirements

- Every release stage execution must output its dedicated success message in the exact format defined in the **Steps** section.
- Conclude the release with a breakdown of:
  1. The released version and its corresponding `versionCode`.
  2. The generated SHA-256 checksum for validation.
  3. The next development version and `versionCode` configured.
  4. A markdown draft release link for the user to publish manually.

---

## Constraints

- **Draft Releases Only**: You **MUST NOT** publish the release to the public. Always use `--draft` in `gh release` commands so publication remains a manual process.
- **Secure Credentials**: Never display or log any sensitive properties (e.g., passwords or certificates) during execution. Rely strictly on Gradle's secure loading from `local.properties`.
- **Zsh Execution**: Always run `scripts/release.sh` using the target commands. Do not write custom script code or use alternative shell tools.
- **No Star Imports / Magic Numbers**: Any modifications to build configurations must adhere to the rules outlined in `AGENTS.md`.

---

## Mandatory Completion Checklist (from `AGENTS.md §15`)

Before marking the task done, verify:

- [x] No `MutableStateFlow` exposed outside its owning singleton
- [x] No FQN references inline — all moved to imports
- [x] No magic numbers — extracted to named constants
- [x] No `android.util.Log` — all logging via `AppLog`
- [x] Every new file has `private const val TAG` and uses `AppLog`
- [x] All user-visible strings in `strings.xml`
- [x] All Icons have `contentDescription`
- [x] `SupervisorJob()` used for class-level scopes
- [x] Scope cancelled in `onDestroy()`
- [x] No suspected compile errors (verified by static analysis)
