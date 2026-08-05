# Contributing to Megingiard

First of all, thank you for taking the time to contribute to Megingiard! We are excited to collaborate with the community to build a state-of-the-art companion experience for dual-screen Android handhelds.

By contributing to this repository, you help make Megingiard more robust and powerful for everyone.

---

## Important: Licensing Policy & Code Usage

Megingiard is a proprietary, source-available project. It is **not** an open-source project under the Open Source Initiative (OSI) definition. 

Please note the following terms regarding the code and how you are permitted to work with it:

1. **License Agreement:** All contributions (including code, documentation, graphics, and issues) you submit will be governed by the [LICENSE](LICENSE) terms.
2. **Section 3 Agreement:** In accordance with **Section 3 (Contributions)** of our license, by opening a Pull Request or submitting modifications, you grant the Copyright Holder a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, reproduce, modify, display, sublicense, and distribute your contribution.
3. **No Financial Compensation:** Contributions are completely voluntary and do not entitle the contributor to financial compensation, royalties, or ownership claims over the software.
4. **Source Code Modifications & Public Forks:** 
   - You are permitted to modify the source code for your own personal, non-commercial use on hardware you personally own.
   - You may publish your modified source code in a public fork of the official repository on GitHub (or other hosting platform) **solely** for the purpose of preparing and submitting a Pull Request directly to the official upstream Megingiard repository.
   - Publishing or sharing modified source code for any other reason is strictly prohibited.
5. **No Binary/APK Distribution:** 
   - You are **strictly prohibited** from compiling and distributing binaries (such as APKs, DEX files, or native libraries) of modified versions of this software.
   - You may not share custom-compiled builds with anyone else for any reason.
   - **Exception:** You are permitted to share compiled test binaries privately and directly with the original repository maintainer solely for review and testing. Custom binaries must **never** be uploaded or attached publicly (such as directly in GitHub PR comments or issues).
6. **Personal Use Limits:** If you want to use custom modifications long-term, you must compile the code yourself and run it strictly for your own personal, non-commercial use. You may not distribute that build to other users.

---

## AI Agentic Development Workflow (Google Antigravity & Gemini)

The Megingiard codebase and its development workflows are heavily optimized for **Google Antigravity** using **Gemini models**. The repository contains specific configurations, system prompts, rules, and scripts designed to enable seamless pair-programming with AI coding agents.

### Custom Skills & Automated Workflows
We define custom workspace-level agentic instructions and automation scripts inside the [.agents/](.agents) directory. These are structured as "skills" that can be executed to automate or verify work:
* `megingiard-bugfix`: Coordinates diagnostics, tracing root causes, and implementing clean bug fixes on hardware.
* `megingiard-code-review`: Audits code changes against architectural layer rules, Jetpack Compose performance guidelines, and state management conventions.
* `megingiard-feature`: Guides the implementation of new user-visible features or Compose screens.
* `megingiard-pr-review-apply`: Applies changes from PR feedback and reviews documentation sync.
* `megingiard-release` & `megingiard-release-changelog`: Automates tag comparisons, changelog compilation, and version bumps.
* `pull-screenshots`: Automates capturing screenshots from both screens of physical hardware (AYN Thor) via ADB.

### Recommending Google Antigravity & Gemini
We **strongly recommend** that all contributors adopt Google Antigravity paired with Gemini models for developing contributions in this codebase. 
> [!IMPORTANT]
> Because our formatting, logging guidelines, testing standards, and file boundaries are strictly enforced via the `.agents/` workflows and [AGENTS.md](AGENTS.md) rules, contributions developed using alternative tooling may produce significant code alignment and style review overhead. To avoid delays in reviewing and merging your Pull Requests, aligning your agentic stack with the project's native Antigravity setup is highly encouraged.

---

## Code Guidelines & Conventions

To maintain codebase health and high execution performance, please adhere to the following standards defined in [AGENTS.md](AGENTS.md):

### 1. Architectural Integrity
Megingiard is split into modular layers:
* **`:app` (Android UI Layer):** Jetpack Compose, ViewModels, Activities, and foreground services. Place shared Composable components in `ui/`.
* **`:domain` (Business Logic & State Management):** Pure business logic, hardware interaction, and state flows. **Must not import Android UI or View elements.**
* **`:core` (Data Structures & Constants):** Pure Kotlin models, serializable schemas, and common constants. **Must have no Android framework dependencies.**

### 2. Kotlin & Compose Conventions
* **Kotlin 2.0+:** Write modern Kotlin. Use `enum.entries` (never `enum.values()`). Use `kotlin.math.min`/`max` (never `java.lang.Math.*`).
* **Imports:** Always use explicit, fully-qualified imports. **Do not use wildcard/star imports** (`import foo.*`).
* **Constants:** Extract all magic numbers to private file-scoped constants using `SCREAMING_SNAKE_CASE`.
* **Color Tokens:** Prefix any file-scoped Compose colors with a 2–3 letter feature code to avoid collisions (e.g. `MP_BUTTON_RED` for MacroPad).

### 3. Logging Mandate
* **Never use `android.util.Log` directly.** All logging must route through `com.stormpanda.megingiard.AppLog`.
* Add informative logs at major lifecycle milestones, state mutations, and error pathways (see [AGENTS.md](AGENTS.md) for mandatory log coverage).

---

## How to Contribute

### Step 1: Open an Issue First
**You must open a GitHub issue describing your proposed change and receive explicit approval/feedback from the maintainer before starting work.** This prevents duplicate work, ensures your proposal aligns with the architectural design, and guarantees your PR will be considered.

### Step 2: Fork and Branch
1. Fork the official Megingiard repository.
2. Clone your fork locally.
3. Create a new descriptive branch from `main`:
   ```bash
   git checkout -b feature/my-amazing-feature
   # or
   git checkout -b bugfix/fix-mirror-crash
   ```

### Step 3: Implement, Synchronize, and Verify
1. **Sync with Upstream:** Before implementing and before submitting a PR, ensure your branch is updated to the latest commit on the upstream `main` branch:
   ```bash
   git fetch upstream
   git rebase upstream/main
   # or merge
   git merge upstream/main
   ```
2. **Implement:** Write your code following the guidelines in [AGENTS.md](AGENTS.md).
3. **Mandatory Testing:** Run the unit tests locally before submitting any contribution to ensure there are no regressions:
   ```bash
   ./gradlew test
   ```
4. **Mandatory Documentation Sync:**
   - If your changes affect a feature's behavior or settings, you **must** update the corresponding `docs/features/<feature>/FEATURE.md` documentation.
   - If user interaction behavior or settings options changed, you **must** update the corresponding help menus and `HelpModal` screens to avoid undocumented features.
5. **Hardware Verification:** If possible, verify your changes on physical hardware (e.g. AYN Thor).

### Step 4: Submit a Pull Request
1. Commit your changes with clear, descriptive commit messages matching Conventional Commits.
2. Push your branch to your GitHub fork.
3. Open a Pull Request (PR) against the `main` branch of the official repository.
4. Describe your changes clearly in the PR description. If a test binary is needed, contact the maintainer to arrange sending it privately; **never** attach compiled APKs or binaries publicly in GitHub comments or issues.

---

## Need Help?
If you have questions about the system architecture, need help getting your local build set up, or want to discuss a design, feel free to open a Discussion on GitHub. We're happy to collaborate!
