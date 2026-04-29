---
name: megingiard-pr-review-apply
description: "Apply requested changes from GitHub pull-request code review comments. Use when: reading review feedback on the current branch PR, building an approval-first implementation plan, implementing fixes, and checking whether FEATURE.md or architecture docs must be updated."
argument-hint: 'Describe context (optional): e.g. "use current branch PR and focus on unresolved review comments first"'
---

# Skill: Apply GitHub PR Review Requested Changes

## Role

You are an expert Android/Kotlin engineer for Megingiard. Your goal is to fetch and interpret all actionable GitHub PR feedback for the current branch, create an approval-first implementation plan, implement approved changes, and keep feature documentation synchronized.

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

## User Input

The user provides a free-form request to implement requested GitHub PR review changes, optionally including focus areas (for example: only unresolved comments, only specific folders, or plan-only mode).

## Steps

1. ✅ **Resolve PR for current branch** — detect active branch, resolve matching PR, and capture number/title/url/base/head/review decision.
2. ✅ **Fetch all review sources** — read reviews, inline comments, issue comments, and unresolved review threads so no actionable request is missed.
3. ✅ **Normalize actionable items** — deduplicate and classify feedback into required, recommended, optional; flag ambiguous items.
4. ✅ **Propose implementation plan** — present files, ordered steps, assumptions, risks, validation, and potential doc impacts.
5. ✅ **Wait for explicit approval** — do not edit code before user confirms the plan.
6. ✅ **Implement approved changes** — apply minimal, traceable edits and keep behavior aligned with requested review changes.
7. ✅ **Run static validation only** — perform static checks and map each review item to implemented change or explicit rationale.
8. ✅ **Run documentation sync check** — update affected FEATURE.md files and, if architecture changed, also docs/ARCHITECTURE.md and PRD.md.
9. ✅ **Deliver final report** — include mapping from comments to fixes, unresolved items, and a Conventional Commits message proposal.
10. ⚡ **Fallback mode without gh auth** — if GitHub CLI is unavailable or unauthenticated, ask for PR URL/number and continue with user-provided context.

## Command Cookbook (tested)

Use these commands in terminal to avoid rediscovering the workflow each time.

### A. Resolve repository, branch, and current-branch PR

```bash
command -v gh
git branch --show-current
gh repo view --json nameWithOwner,defaultBranchRef --jq '.nameWithOwner + "|" + .defaultBranchRef.name'
gh pr view --json number,title,url,reviewDecision,headRefName,baseRefName,author --jq '{number,title,url,reviewDecision,headRefName,baseRefName,author:.author.login}'
```

### B. Fetch reviews, inline comments, and issue comments

```bash
REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
PR=$(gh pr view --json number --jq .number)

# Reviews (state + body)
gh api repos/$REPO/pulls/$PR/reviews --paginate \
	--jq '.[] | [.id, .state, .user.login, (.submitted_at // ""), ((.body // "") | gsub("\n";" "))] | @tsv'

# Inline review comments (file/line)
gh api repos/$REPO/pulls/$PR/comments --paginate \
	--jq '.[] | [.id, .user.login, (.path // ""), (.line|tostring // ""), ((.body // "") | gsub("\n";" "))] | @tsv'

# PR conversation comments
gh api repos/$REPO/issues/$PR/comments --paginate \
	--jq '.[] | [.id, .user.login, ((.body // "") | gsub("\n";" "))] | @tsv'
```

### C. Fetch unresolved review threads (GraphQL fallback)

```bash
REPO=$(gh repo view --json nameWithOwner --jq .nameWithOwner)
OWNER=${REPO%%/*}
NAME=${REPO##*/}
PR=$(gh pr view --json number --jq .number)

gh api graphql \
	-f query='query($owner:String!, $name:String!, $number:Int!) { repository(owner:$owner, name:$name) { pullRequest(number:$number) { reviewThreads(first:100) { nodes { isResolved isOutdated path line comments(first:20) { nodes { author { login } body createdAt } } } } } } }' \
	-f owner="$OWNER" -f name="$NAME" -F number=$PR \
	--jq '.data.repository.pullRequest.reviewThreads.nodes[] | select(.isResolved == false) | [.path, (.line|tostring), (.isOutdated|tostring), ((.comments.nodes|last|.author.login) // ""), (((.comments.nodes|last|.body) // "") | gsub("\n";" "))] | @tsv'
```

### D. Notes on command behavior

- `gh pr view --json ...` does not currently expose `reviewThreads`; use the GraphQL command above.
- Use `--paginate` for all REST list endpoints to avoid truncated feedback.
- Add `| head -n <N>` temporarily only for quick inspection, not for final full parsing.

## Output Requirements

- PR context summary (number, URL, branch, base/head, review decision).
- Actionable feedback list, grouped and prioritized.
- Implementation plan shown before edits, then explicit user approval gate.
- Implementation summary with per-file change mapping.
- Static validation summary and unresolved items with rationale.
- Documentation sync summary with exact updated files or explicit no-change reason.
- Always conclude with a Conventional Commits message proposal covering all changes made in the session.

## Constraints

- Do not write code before user approves the implementation plan.
- Treat reviews, inline comments, issue comments, and unresolved threads as mandatory feedback sources.
- Perform static validation only; do not run `./gradlew` or build/compile tasks.
- Keep changes minimal and targeted to requested review items; avoid unrelated refactors.
- End by proposing a commit message only; do not auto-commit.

## Mandatory Completion Checklist (from `AGENTS.md §15`)

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
