# Complexity & Simplification Audit: {Feature Name}

> **Feature Spec:** [`docs/features/{feature}/FEATURE.md`](../features/{feature}/FEATURE.md)
> **Modules & Packages:** `:companion:ui` (`.../{feature}/`), `:companion:domain` (`.../{feature}/`), `:shared:*`
> **Audit Status:** Draft — Awaiting Operator Selection

---

## 1. Executive Summary & Scorecard

| Category | Findings Count | Est. Lines Removable | Potential Impact |
| :--- | :---: | :---: | :--- |
| 1. Over-Engineering & Speculative Abstractions | 0 | ~0 | Low / Med / High |
| 2. Guarding Against Impossible Bugs (Defensive Paranoia) | 0 | ~0 | Low / Med / High |
| 3. Premature Optimization | 0 | ~0 | Low / Med / High |
| 4. Vestigial Architecture & Ghost Layers | 0 | ~0 | Low / Med / High |
| 5. Derived State Stored as Mutable State (Split-Brain) | 0 | ~0 | Low / Med / High |
| 6. Over-Complicated Concurrency & Ad-Hoc Race Guards | 0 | ~0 | Low / Med / High |
| 7. Overlapping Lifecycle Triggers & Debounce Band-Aids | 0 | ~0 | Low / Med / High |
| 8. Compose State Mirroring & UI Boilerplate | 0 | ~0 | Low / Med / High |
| 9. Doc-to-Code Drift | 0 | ~0 | Low / Med / High |
| **Total** | **0** | **~0** | — |

### Key Takeaways
- {1–3 bullet summary of the biggest architectural or code simplifications identified in this feature.}
- {Note what is already clean/well-structured and should be left untouched.}

---

## 2. Detailed Findings by Category

<!-- Include all 9 categories. If a category has no findings in this feature, keep the heading and write "None identified — <brief reason why this area is already clean>." -->

### 2.1 Over-Engineering & Speculative Abstractions
*Single-implementation interfaces, pass-through facades/wrappers, over-parameterized functions/Composables where every caller passes the same value, or unnecessary cross-module indirection.*

#### `[{PREFIX}-01]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {What the code currently does and how many layers/parameters are involved.}
- **Why It's Over-Complex:** {Why the abstraction or indirection does not buy real decoupling, safety, or testability.}
- **Proposed Simplification:** {Concrete refactoring that keeps 100% of functionality and robustness.}
- **Verification & Doc Sync:** {Unit tests to update/add + `FEATURE.md` sections affected.}

---

### 2.2 Guarding Against Impossible Bugs (Defensive Paranoia)
*Catching exceptions that cannot be thrown, re-validating invariants already enforced at the boundary, fallback branches for states prevented by the type system, or silent no-op guards that hide real contract violations.*

#### `[{PREFIX}-02]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Describe the redundant check, `try/catch`, or impossible fallback branch.}
- **Why It's Unnecessary:** {Prove why this state cannot occur given callers/types/invariants.}
- **Proposed Simplification:** {Remove dead guard or replace with a single boundary invariant.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.3 Premature Optimization
*Custom caches, object pools, manual diffing, or intricate data structures for small/infrequent operations where straightforward Kotlin idioms are just as fast and much easier to reason about.*

#### `[{PREFIX}-03]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Describe the optimization mechanism and its bookkeeping overhead.}
- **Why It's Premature:** {Explain actual data scale / call frequency (e.g., UI clicks vs. 120Hz frame loop).}
- **Proposed Simplification:** {Replace with direct computation or standard collection op.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.4 Vestigial Architecture & Half-Removed Evolutions ("Ghost Layers")
*Superseded fallback pipelines, deprecated DataStore/JSON fields still threaded through runtime logic, dead feature flags, or legacy migrations that should be dropped or isolated to load-time normalization.*

#### `[{PREFIX}-04]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Describe the leftover mechanism, field, or fallback path.}
- **Why It's Vestigial:** {Explain which newer architecture replaced it.}
- **Proposed Simplification:** {Delete dead path or quarantine legacy migration to a single deserialization boundary.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.5 Derived State Stored as Mutable State (Split-Brain State)
*Values persisted in DataStore or held in separate `MutableStateFlow`s that can be computed deterministically from existing state via pure functions, computed properties, or `combine()` / `map()`.*

#### `[{PREFIX}-05]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Which derived field/flow is stored and where it is manually recomputed.}
- **Why It's Problematic:** {Risk of desynchronization across CRUD/import paths + boilerplate.}
- **Proposed Simplification:** {Compute on read as a pure property or reactive `StateFlow` operator.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.6 Over-Complicated Concurrency & Ad-Hoc Race Guards
*Mixing `@Synchronized`, `@Volatile`, `AtomicInteger` generation counters, and `delay()` retry loops where structured concurrency (`StateFlow.update`, `Mutex`, `collectLatest`, or sequential channel/actor) eliminates races by construction.*

#### `[{PREFIX}-06]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Describe the locks, counters, or retry timers currently used.}
- **Why It's Over-Complex:** {How the current synchronization overlaps or obscures ordering.}
- **Proposed Simplification:** {Single structured concurrency primitive that guarantees sequential execution.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.7 Overlapping Lifecycle Triggers & Debounce Band-Aids
*Multiple Android/Compose lifecycle hooks (`onCreate`, `onResume`, `onNewIntent`, `onTopResumedActivityChanged`, `onConfigurationChanged`, `DisposableEffect`) firing the same side effect and relying on time-based debounce timers.*

#### `[{PREFIX}-07]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {List the overlapping call sites and any debounce workarounds.}
- **Why It's Fragile:** {Why time-based debouncing or multi-hook triggering is harder to reason about than state transitions.}
- **Proposed Simplification:** {Consolidate to the single authoritative lifecycle transition or reactive state collector.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.8 Compose State Mirroring & UI Boilerplate
*Copying `StateFlow` values into local `remember { mutableStateOf(...) }` + `LaunchedEffect` sync loops, or duplicating navigation/confirmation/picker state across screens.*

#### `[{PREFIX}-08]` {Short Descriptive Title}
- **Location:** [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Complexity Reduction:** High | Medium | Low
- **Regression Risk:** Low | Medium | High
- **Current State:** {Show the mirrored state variables and sync effects.}
- **Why It's Over-Complex:** {Extra recompositions, stale-state edge cases, and UI clutter.}
- **Proposed Simplification:** {Read directly from `collectAsState()` or encapsulate into a single immutable edit-draft state.}
- **Verification & Doc Sync:** {Tests / docs affected.}

---

### 2.9 Doc-to-Code Drift
*Contradictions inside `FEATURE.md`, documentation describing removed mechanisms, or dead code/tests kept alive only because old docs still mention them.*

#### `[{PREFIX}-09]` {Short Descriptive Title}
- **Location:** [`FEATURE.md`](../features/{feature}/FEATURE.md#L1-L20) vs. [`{FileName.kt}`](../../path/to/FileName.kt#L1-L20)
- **Current Mismatch:** {What the doc says vs. what the code actually does.}
- **Proposed Resolution:** {Update doc to match reality, or remove the dead code together with the doc section.}

---

## 3. Recommended Action Plan (Operator Selection)

> Select which findings to implement (`All`, `Low-risk only`, or specific IDs):

- [ ] **`[{PREFIX}-01]`** — {Title} *(Complexity: High, Risk: Low)*
- [ ] **`[{PREFIX}-02]`** — {Title} *(Complexity: Med, Risk: Low)*
- [ ] **`[{PREFIX}-03]`** — {Title} *(Complexity: Low, Risk: Low)*
