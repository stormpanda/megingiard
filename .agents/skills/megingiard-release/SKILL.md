---
name: megingiard-release
description: "Orchestrate the Megingiard app release process: create release branch (release/X.Y.Z), set release version, build signed release APK for Megingiard Companion, generate SHA-256 checksum, compile changelog (strictly excluding Game Focus), upload GitHub release draft with Companion assets only, merge back to main locally (no auto-push of main), and bump development version."
---

# Skill: megingiard-release

## Role

You are a **Release Manager** expert on the **Megingiard** project. Your goal is to guide the repository through a seamless release cycle for the publicly distributed **Megingiard Companion** application. You will safely manage release branches (`release/X.Y.Z`), modify Gradle configurations, execute compilation pipelines for Megingiard Companion, coordinate git version tags, generate premium-grade release changelogs using git history (strictly excluding unreleased Game Focus features and docs), publish draft releases with Companion artifacts only to GitHub, merge release branches back into `main` locally, and transition the repository into active development mode with bumped versions.

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

The user requests to initiate a release. Releases can be started from `main` (for standard minor releases) or from an existing `release/*` branch (for hotfix patch releases). All configurations are dynamically parsed from `app/build.gradle.kts` and `local.properties`.

---

## Steps

Follow these sequential steps precisely:

1. ✅ **Prepare Git Status** — Check that the local repository is on `main` (for standard release) or a `release/*` branch (for hotfix release) and has a clean status (`git status --porcelain` is empty). Inform the user if any unstaged modifications exist.
2. ✅ **Execute Prepare Phase** — Run `scripts/release.sh prepare`. This determines the release version (minor release from `main` or patch bump from a `release/*` branch), creates/checkouts a new release branch `release/<version>` (e.g. `release/0.7.0` or `release/0.7.1`), updates `app/build.gradle.kts`, commits on the release branch, tags the commit, and pushes the release branch and tag to GitHub.
   - **Update Message**: Print the update exactly as: `Release version <version> successfully prepared and tagged on branch release/<version>.`
3. ✅ **Execute Build Phase** — Run `scripts/release.sh build`. This compiles the signed Megingiard Companion release APK (`:companion:ui:assembleRelease`) using the keystore credentials specified in `local.properties`, copies it to `app/release/`, and calls the checksum generator. **Note:** Game Focus release APKs are NOT built or copied to `app/release/` for public releases (use `scripts/release-gf.sh` or `scripts/release-dual.sh` for local Game Focus builds).
   - **Update Message**: Print the update exactly as: `Release Build <version> APK successfully created and signed.`
4. ✅ **Determine Previous Tag** — Find the previous git tag immediately before the current release version (e.g. by running `git describe --tags --abbrev=0 HEAD~1` or inspecting `git tag -l --sort=-v:refname` and skipping the current version).
5. ✅ **Generate Changelog** — Run the analysis steps defined in the `megingiard-release-changelog` skill comparing the `<previous-tag> to <current-tag>`. Ensure that all changes and documentation regarding unreleased Game Focus are strictly excluded.
   - Write this generated premium markdown changelog into a temporary file at `.tmp/release_changelog.md` (creating `.tmp/` if it doesn't exist).
6. ✅ **Execute Publish Phase** — Run `scripts/release.sh publish .tmp/release_changelog.md`. This reads the changelog file and uses the `gh` CLI to upload the draft release to GitHub with the signed Megingiard Companion APK and checksum file. **Note:** Only Megingiard Companion artifacts are uploaded; Game Focus APK and checksums are never published.
   - **Update Message**: Print the update exactly as: `Release draft Megingiard-v<version> successfully uploaded with APK and checksum.`
7. ✅ **Execute Finish Phase (Merge-Back)** — Run `scripts/release.sh finish`. This switches to `main` and merges the release branch locally into `main` (`git merge --no-ff`). **Note:** `main` is NOT pushed to remote automatically.
   - **Update Message**: Print the update exactly as: `Release branch release/<version> successfully merged into main locally.`
8. ✅ **Execute Bump Phase** — Run `scripts/release.sh bump`. This increments `versionCode` in `app/build.gradle.kts` and bumps `versionName` for development (minor version `0.X.0-SNAPSHOT` when releasing from `main`, or patch snapshot `0.X.Y-SNAPSHOT` when bumping a release branch). Commits locally on `main` (not pushed).
   - **Update Message**: Print the update exactly as: `Successfully bumped development version to <next-version> (code: <next-code>).`
9. ✅ **Reporting & Cleanup** — Provide the user with a summary of the draft release, pointing them to their GitHub Releases dashboard to review and publish the draft. Remind the user to manually push `main` (`git push origin main`) when ready. Delete the temporary `.tmp/release_changelog.md` file.

---

## Output Requirements

- Every release stage execution must output its dedicated success message in the exact format defined in the **Steps** section.
- Conclude the release with a breakdown of:
  1. The released version and its corresponding `versionCode`.
  2. The release branch created (`release/X.Y.Z`).
  3. The generated SHA-256 checksum for validation.
  4. The next development version and `versionCode` configured on `main`.
  5. A reminder that `main` was merged and bumped locally, but requires a manual `git push origin main`.
  6. A markdown draft release link for the user to publish manually.

---

## Constraints

- **No Game Focus Assets or Changelog Entries in Public Releases**: Megingiard Companion (`:companion:*`, `:shared:*`, `:mirrorserver`) is the primary standalone product released to the public. Game Focus (`:gamefocus:*`) is currently NOT released to the public (per `AGENTS.md §6.1`). You MUST NOT build, copy, checksum, or upload Game Focus APKs or assets in public releases (`scripts/release.sh`), and public release notes must contain zero references to Game Focus. For local development or testing of Game Focus release APKs, use `scripts/release-gf.sh` or `scripts/release-dual.sh` instead.
- **Release Branch Isolation**: Standard releases create `release/X.Y.0` from `main`. Hotfix releases starting on `release/X.Y.Z` create a new branch `release/X.Y.Z+1`.
- **Manual Push for Main**: You **MUST NOT** push `main` to remote after merging or bumping. Only release branches (`release/*`) and tags (`X.Y.Z`) are pushed automatically by `prepare`.
- **Draft Releases Only**: You **MUST NOT** publish the release to the public. Always use `--draft` in `gh release` commands so publication remains a manual process.
- **Secure Credentials**: Never display or log any sensitive properties (e.g., passwords or certificates) during execution. Rely strictly on Gradle's secure loading from `local.properties`.
- **Zsh Execution**: Always run `scripts/release.sh` using the target commands. Do not write custom script code or use alternative shell tools.
- **No Star Imports / Magic Numbers**: Any modifications to build configurations must adhere to the rules outlined in `AGENTS.md`.

---

## Mandatory Completion Checklist (from `AGENTS.md §3`)

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


