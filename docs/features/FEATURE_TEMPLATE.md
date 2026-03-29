# Feature: {Feature Name}

<!-- Replace {Feature Name} with the name of the feature (e.g. "Virtual Keyboard"). -->

> **Related source:** `app/src/main/java/com/stormpanda/megingiard/{feature}/`
> _(Add additional source paths for any shared infrastructure packages, e.g. `input/`.)_

<!-- If the feature uses a native binary, uncomment and fill in the three lines below: -->
<!-- > **Native source:** `app/src/main/cpp/{source}.c` -->
<!-- > **Binary asset:** `app/src/main/assets/{binary}_arm64` -->
<!-- > **Build instructions:** [`docs/BUILD_NATIVE.md`](../BUILD_NATIVE.md) -->

---

## Functional Requirements

### Overview

{One paragraph describing the feature from the user's perspective — what problem it solves and what value it delivers.}

### FR-{X}1: {Requirement Name}

<!-- FR code convention: use a 2-letter prefix that identifies the feature
     (M = Mirror, D = meDia, T = Touchpad, K = Keyboard, …) followed by a
     sequential number. Example: FR-M1, FR-K3.
     Requirement names should be short noun phrases, e.g. "Touch Surface",
     "Transport Controls", "No Special Permissions Required". -->

- {Requirement written as a MUST / MUST NOT imperative sentence.}
- {Add further bullets for sub-requirements of the same concern.}

### FR-{X}2: {Requirement Name}

- {…}

<!-- Add further FR sections as needed. There is no fixed number;
     use as many as the feature warrants. -->

---

## Technical Implementation

### {Primary Architecture / Overview Section}

{Describe the key architectural decisions, component relationships, and data flow.
Include ASCII diagrams where they add clarity. Example:

```
User Input (Compose)
      │
      ▼
{FeatureManager}   ← state (StateFlow)
      │
      ▼
{FeatureService}   ← background work
```

}

### {Additional Sections As Needed}

{Explain notable subsystems, algorithms, or patterns that are not immediately
obvious from the code. Typical sections: protocol descriptions, state machines,
coordinate transforms, lifecycle management, settings persistence.}

### Source Files

<!-- Always the last section.
     List every file that directly implements this feature.
     For files from shared packages (e.g. input/), use a relative path prefix
     such as ../input/. -->

| File                  | Responsibility                         |
| --------------------- | -------------------------------------- |
| `{FeatureScreen}.kt`  | {Short description of the file's role} |
| `{FeatureManager}.kt` | {Short description of the file's role} |
