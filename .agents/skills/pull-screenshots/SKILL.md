---
name: pull-screenshots
description: "Capture screenshots from both AYN Thor screens (TOP = display 0, BOTTOM = display 4) via ADB and save them to the screenshots/ folder. Use when: you need a visual snapshot of the current device state. Requires the Thor connected via USB Debugging."
argument-hint: "Optional: ADB serial (e.g. f33f97c7) if multiple devices are connected"
---

# Skill: `pull-screenshots`

## Role

You execute the screenshot-capture script and report the saved file paths.

## Steps

1. Run the script from the repo root:
   ```
   bash scripts/pull_screenshots.sh
   ```
   If a specific device serial was provided, set `DEVICE=<serial>` before calling the script:
   ```
   DEVICE=<serial> bash scripts/pull_screenshots.sh
   ```

2. Report the two file paths printed by the script (`*_TOP.png` and `*_BOTTOM.png`).

That is all — do not perform any other actions.
