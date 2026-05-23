# Contributing to Megingiard

First of all, thank you for taking the time to contribute to Megingiard! We are excited to collaborate with the community to build a state-of-the-art companion experience for dual-screen Android handhelds.

By contributing to this repository, you help make Megingiard more robust and powerful for everyone.

---

## Important: Licensing Policy

Megingiard is a proprietary, source-available project. It is **not** an open-source project under the strict OSI definition. 

**Free Software Promise:**
Megingiard is, and will always remain, completely free of charge for personal, non-commercial use. The project and all official releases are dedicated to providing a free utility for handheld enthusiast communities.

Please note the following terms before you begin contributing:
1. **License Agreement:** All contributions (including code, documentation, graphics, and issues) you submit will be governed by the [LICENSE](LICENSE) terms.
2. **Section 3 Agreement:** In accordance with **Section 3 (Contributions)** of our license, by opening a Pull Request or submitting modifications, you grant the Copyright Holder a perpetual, worldwide, non-exclusive, royalty-free, irrevocable license to use, reproduce, modify, display, sublicense, and distribute your contribution.
3. **No Financial Compensation:** Contributions are completely voluntary and do not entitle the contributor to financial compensation, royalties, or ownership claims over the software.

---

## Code Guidelines & Conventions

To maintain codebase health and high execution performance, please adhere to the following standards when writing code:

### 1. Architectural Integrity
Megingiard is split into three modular layers:
* **`:app` (Android UI Layer):** Jetpack Compose, ViewModels, Activities, and foreground services. Place shared Composable components in `ui/`.
* **`:domain` (Business Logic & State Management):** Pure business logic, hardware interaction, and state flows. **Must not import Android UI or View elements.**
* **`:core` (Data Structures & Constants):** Pure Kotlin models, serializable schemas, and common constants. **Must have no Android framework dependencies.**

### 2. Kotlin & Compose Conventions
* **Kotlin 2.0+:** Write modern Kotlin. Use `enum.entries` (never `enum.values()`). Use `kotlin.math.min`/`max` (never `java.lang.Math.*`).
* **Imports:** Always use explicit, fully-qualified imports. **Do not use wildcard/star imports** (`import foo.*`).
* **Constants:** Extract all magic numbers to private file-scoped constants using `SCREAMING_SNAKE_CASE` (e.g., `private const val OVERLAY_TIMEOUT_MS = 3000L`).
* **Color Tokens:** Prefix any file-scoped Compose colors with a 2–3 letter feature code to avoid collisions (e.g. `private val MP_BUTTON_RED = Color(...)` for MacroPad).

### 3. Logging Mandate
* **Never use `android.util.Log` directly.** All logging must route through `com.stormpanda.megingiard.AppLog`.
* Add informative logs at major lifecycle milestones, state mutations, and error pathways.
* Avoid logging continuous high-frequency events (like pointer coordinates or animations) to keep logcat output clean and legible.

---

## How to Contribute

### Step 1: Open or Select an Issue
Before writing code, search our issue tracker or open a new issue describing what you want to build or fix. This helps avoid duplicate work and ensures the design aligns with the project goals.

### Step 2: Fork and Branch
1. Fork the official Megingiard repository.
2. Clone your fork locally.
3. Create a new descriptive branch from `main`:
   ```bash
   git checkout -b feature/my-amazing-feature
   # or
   git checkout -b bugfix/fix-mirror-crash
   ```

### Step 3: Implement and Verify
1. Make your code changes following our codebase guidelines.
2. **Mandatory Testing:** Run the unit tests locally before submitting any contribution to ensure there are no regressions. Passing all unit tests is a strict requirement for Pull Request approval. Run the tests using Gradle:
   ```bash
   ./gradlew test
   ```
3. Compile and build the project using Gradle.
4. **Hardware Verification:** Because Megingiard is highly specific to dual-screen devices (like the AYN Thor), please test your changes on physical hardware or a compatible dual-display emulator if possible. Verify that Jetpack Compose Presentation overlays render correctly and coordinate transformations work seamlessly.

### Step 4: Submit a Pull Request
1. Commit your changes with clear, descriptive commit messages.
2. Push your branch to your GitHub fork.
3. Open a Pull Request (PR) against the `main` branch of the official repository.
4. Describe your changes clearly in the PR description, linking to any resolved issues.

---

## Need Help?
If you have questions about the system architecture, need help getting your local build set up, or want to discuss a major feature design, feel free to open a Discussion on GitHub. We're happy to collaborate!
