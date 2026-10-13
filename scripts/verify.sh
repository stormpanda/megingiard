#!/usr/bin/env bash
# One-shot quality gate for Megingiard — run before proposing a commit.
#
#   1. AGENTS.md size budget (must stay loadable in full by AI agents)
#   2. Architecture rule verifier + its own unit tests (build-logic)
#   3. All module unit tests (scripts/run_unit_tests.sh)
#
# Must run outside the agent sandbox (Gradle needs local TCP/loopback sockets).
# Rules enforced by step 2: docs/agents/CODING_GUIDE.md §17.
set -euo pipefail

cd "$(dirname "$0")/.."

AGENTS_MD_MAX_BYTES=20480
DOC_MAX_BYTES=40960

echo "==> [1/3] Documentation size budgets"
agents_size=$(wc -c < AGENTS.md | tr -d ' ')
if [ "$agents_size" -gt "$AGENTS_MD_MAX_BYTES" ]; then
  echo "FAIL: AGENTS.md is $agents_size bytes (limit $AGENTS_MD_MAX_BYTES)."
  echo "      Move detail into docs/agents/CODING_GUIDE.md and keep AGENTS.md as a compact digest."
  exit 1
fi
echo "OK: AGENTS.md is $agents_size bytes (limit $AGENTS_MD_MAX_BYTES)"

oversized=$(find . -name '*.md' -not -path '*/build/*' -not -path './.gradle/*' -not -path '*/node_modules/*' -size +"${DOC_MAX_BYTES}"c)
if [ -n "$oversized" ]; then
  echo "FAIL: Markdown files above $DOC_MAX_BYTES bytes (agents cannot read them in one pass):"
  echo "$oversized"
  echo "      Split them into part files listed in a document map at the top of FEATURE.md."
  exit 1
fi
echo "OK: all Markdown files <= $DOC_MAX_BYTES bytes"

echo "==> [2/3] Architecture rules"
./gradlew -p build-logic test --quiet
./gradlew verifyArchitectureRules --continue --quiet

echo "==> [3/3] Unit tests"
./scripts/run_unit_tests.sh --quiet

echo "==> verify.sh: all checks passed"
