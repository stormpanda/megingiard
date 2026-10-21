# Implementation Plan Template (megingiard-feature)

> Used by `megingiard-feature` Step 5. Copy the 6-section skeleton below into the
> `implementation_plan.md` artifact. Keep the plan **lean, architectural, and fast to read**.

## Purpose: a lean architectural blueprint for same-session `Pro` execution

Because `Pro` runs both Planning Mode and Implementation Mode in the same session, **do not write implementation code in the plan** (write code only once, in the real source files during Implementation Mode).

The plan exists for two reasons:
1. **Fast human review:** Let the operator verify scope, UX, architecture, and module boundaries in 30 seconds before clicking **Proceed**.
2. **Complete execution checklist:** Lock in the interview decisions and list every affected file, edge case, unit test scenario, and localization / `FEATURE.md` target so nothing is missed during coding.

### What belongs in the plan vs. what waits for Implementation Mode

- **Include in the plan:**
  - Data model / persisted schema changes (e.g. new enum values, new `data class` fields + defaults for backward compatibility).
  - Non-obvious algorithmic or architectural choices in 1–2 concise bullets (or a 1–3 line signature/formula only when it clarifies a contract across modules).
  - Per-file responsibilities (1–3 concise prose bullets per file with clickable file links).
  - Edge cases, unit test scenarios, localization / `HelpModal` impact, and `FEATURE.md` targets.
- **Do NOT put in the plan:**
  - Function bodies, Compose UI trees, `when` mappings, imports, `TAG` / `private const val` declarations, full 3-locale XML/translation tables, or verbatim doc diffs.

---

## Skeleton

````markdown
# Implementation Plan: <Feature Name>

**Size:** S | M | L  ·  **Owning feature doc:** `docs/features/<x>/FEATURE.md`  ·  **Modules touched:** `:companion:ui`, …

## 1. Goal & Scope
- **Goal:** <one sentence, user-visible outcome>
- **In scope:** <bullets>
- **Out of scope:** <bullets>

## 2. Resolved Design Decisions
| # | Decision | Rationale | Rejected alternatives |
| - | -------- | --------- | --------------------- |
| D1 | … | … | … |

## 3. Architecture, Data Model & Compatibility
- **Module & state ownership:** Which singleton / module owns the state (`StateFlow` exposure, load-once guard if disk-backed, threading/scope).
- **Data model & persistence:** New/changed enum entries or fields + default values; backward compatibility with existing saved profiles / `.mgrd` backups; R8/ProGuard impact.
- **Key algorithm / UI contract:** 1–3 bullets on any non-obvious math, Compose effect pattern (`snapshotFlow`), gamepad focus behavior, or dual-screen targeting. (For **L** features, split into phases or include a brief mermaid data-flow diagram.)

## 4. Planned File Changes
Group by module. Give **1–3 concise prose bullets per file** describing *what* changes and why (no implementation code blocks):

### `:shared:core` / `:companion:domain`
- **[`File.kt`](file:///abs/path/File.kt)** *(NEW | MODIFIED)* — <1–3 bullets on responsibilities and contract changes>

### `:companion:ui`
- **[`Screen.kt`](file:///abs/path/Screen.kt)** *(MODIFIED)* — <1–3 bullets on UI placement, interactions, and state wiring>

## 5. Edge Cases & Verification Plan
- **Edge cases handled:**
  - <Scenario> → <expected deterministic behavior>
- **Unit test scenarios (`./scripts/verify.sh`):**
  - [`FooTest.kt`](file:///abs/path/FooTest.kt) *(NEW | MODIFIED)* — <bullet list of behaviors/cases to test>
- **Manual device verification (AYN Thor):**
  - <2–4 concise operator steps + expected result>

## 6. Localization, Help & Documentation Sync
- **Strings (`values`, `values-de`, `values-zh-rTW`):** <list the new/updated UI labels or descriptions to add across all 3 locales>
- **In-app Help (`HelpModal` / onboarding):** <file + help entry to add/update, or `n/a — no new settings option or gesture`>
- **Documentation (`FEATURE.md` / `ARCHITECTURE.md`):** <files + sections to update>
````

---

## Quality Gate (self-check before presenting)

Verify before presenting the plan to the user:

- [ ] Plan is concise and prose-driven — no full function bodies, Compose trees, or verbatim diffs.
- [ ] Every interview decision is captured in §2 and every edge case in §5.
- [ ] Persistence / serialization changes explicitly state backward compatibility and R8 impact in §3.
- [ ] Every affected production file, test file, string group, `HelpModal`, and `FEATURE.md` part file is listed in §4–§6.
- [ ] Zero heuristics (§7.5) and zero `:gamefocus:*` coupling for Companion features (§6.1).


