#!/usr/bin/env bash
# Canonical unit-test entry point for Megingiard (single source of truth).
#
# AI agents and contributors run this script instead of copying the Gradle task list.
# When a module gains a new src/test directory, add its test task HERE only.
#
# Must run outside the agent sandbox (Gradle needs local TCP/loopback sockets).
# Extra arguments are forwarded to Gradle (e.g. --tests 'com.stormpanda.*FooTest').
set -euo pipefail

cd "$(dirname "$0")/.."

exec ./gradlew \
  :shared:core:test \
  :shared:catalog:testDebugUnitTest \
  :shared:media:testDebugUnitTest \
  :shared:session:testDebugUnitTest \
  :shared:ui:testDebugUnitTest \
  :companion:domain:test \
  :companion:ui:testDebugUnitTest \
  :gamefocus:domain:testDebugUnitTest \
  :gamefocus:ui:testDebugUnitTest \
  --continue \
  "$@"
