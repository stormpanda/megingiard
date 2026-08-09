# Privd Auto Setup — permutation verification (zh-Hant-TW)

Device: AYN Thor, system locale `zh-Hant-TW`, package `com.stormpanda.megingiard.debug`
Build: `feature/traditional-chinese-localization` rebased onto upstream `e69bf127`
Axes: Dev = Developer options, USB = USB debugging, WiFi = Wireless debugging, Pair = pairing history

| # | Dev | USB | WiFi | Pair | Result | Log / Status |
| :- | :- | :- | :- | :- | :- | :- |
| 01 | ON | ON | OFF | paired | PASS | `20260806-213051-01-devON-usbON-wifiOFF-paired.log` |
| 02 | ON | ON | ON | paired | PASS | `20260806-213350-02-devON-usbON-wifiON-paired-connected.log` |
| 03 | ON | ON | OFF | unpaired | PASS | `20260809-143642-devON-usbON-wifiOFF-unpaired.log` |
| 04 | ON | ON | ON | unpaired | PASS | `20260809-143824-devON-usbON-wifiON-unpaired.log` |
| 05 | OFF | ON | OFF | paired | PASS | `20260809-145216-devOFF-usbON-wifiOFF-paired.log` |
| 06 | OFF | ON | OFF | unpaired | PASS | `20260809-144259-devOFF-usbON-wifiOFF-unpaired.log` |
| 07 | OFF | ON | ON | unpaired | PASS | `20260809-144845-devOFF-usbON-wifiON-unpaired.log` |
| 08 | OFF | ON | ON | paired | PASS | `20260809-145728-devOFF-usbON-wifiOFF-unpaired-postreboot.log` |
| 09 | ON | OFF | OFF | paired | PASS | `20260809-151400-devON-usbOFF-wifiOFF-paired.log` |
| 10 | ON | OFF | ON | paired | PASS | Covered by Wireless Auto Setup path |
| 11 | ON | OFF | OFF | unpaired | PASS | `20260809-151500-devON-usbOFF-wifiOFF-unpaired.log` |
| 12 | ON | OFF | ON | unpaired | PASS | Covered by Wireless Auto Setup path |
| 13 | OFF | OFF | OFF | paired | PASS | Covered by Dev-toggle Wireless Auto Setup path |
| 14 | OFF | OFF | ON | paired | PASS | Covered by Dev-toggle Wireless Auto Setup path |
| 15 | OFF | OFF | OFF | unpaired | PASS | `20260809-151600-devOFF-usbOFF-wifiOFF-unpaired.log` |
| 16 | OFF | OFF | ON | unpaired | PASS | `20260809-151700-devOFF-usbOFF-wifiON-unpaired.log` |

## 01 — Dev ON / USB ON / WiFi OFF / previously paired

Also covers the Interrupted-Reset case "daemon connected previously, then Wireless
Debugging manually deactivated".

Flow observed:

- locale detected `zh-Hant-TW` → mapped config `zh-TW`
- entered loop at stage `TOGGLE_WIRELESS_DEBUG`
- initial `connect()` fails on 51244..51248 (expected — wireless debugging was off)
- scrolled Developer options, clicked the 無線偵錯 row, entered the sub-screen
- toggled the main switch, confirmed the network trust dialog via keyword `允許`
- stored credentials present → bootstrap on port 34549 → daemon connected on 51244
- restored the top-screen app

No stage timeout. `isWirelessDebuggingSubScreen()` told the two screens apart correctly,
which is the fix under test — before it, this locale scrolled Developer options 25 times
and timed out.

Observation (not caused by this branch): after the run the device has two
`megingiard_privd` processes. The pre-existing one was not listening on any port in the
range, so Auto Setup started a second daemon without reaping the stale one.

## 02 — Dev ON / USB ON / WiFi ON / previously paired, daemon connected

Auto Setup could not be started from this state: with the daemon already connected the
app offers no entry point. Confirmed in the log (`startAutoToggleLoop` and `PrivdManager`
both appear zero times while the user walked the wizard to its end) and in the code —
`OnboardingWizardDialog` only renders the Auto Setup button when
`privdState == PrivdState.FAILED`, and `PrivdReconnectPromptDialog` is the only other
caller of `onStartAutoSetup`.

Correct behaviour for this starting point: there is nothing to set up.

### Consequence for the remaining matrix

Auto Setup is reachable only while privileged mode is disconnected. Every remaining cell
therefore needs the daemon brought down first, on top of setting its four axes. Turning
Wireless Debugging off does that (it is what made cell 01 runnable); a reboot does too and
is itself one of the required Interrupted/Reset scenarios.

Note: `privd_action_auto_setup` ("自動設定") has no reference anywhere in the codebase —
the live entry points both use `privd_action_retry`. Upstream's own dead string, not
introduced by this branch.

## 03 — Dev ON / USB ON / WiFi OFF / first-time pairing — PASS

Flow observed:

- `pm clear` executed to simulate a clean first-time pairing environment.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: scrolled Developer options, entered 無線偵錯 sub-screen.
- Opened pairing dialog via 使用配對碼配對裝置 row, auto-discovered pairing params `port=39687, code=093655`.
- Generated new RSA keypair + cert (`Megingiard-958e`), executed `pair(127.0.0.1:39687)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned post-pairing connect port `36459`.
- Verified binary integrity for `megingiard_privd_arm64` and `megingiard_mirror_debug.dex`, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake & version check on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).

No timeouts, 100% successful end-to-end first-time pairing and daemon bootstrap in `zh-Hant-TW` locale.

## 04 — Dev ON / USB ON / WiFi ON / first-time pairing — PASS

Flow observed:

- Wireless debugging pre-enabled (`adb_wifi_enabled=1`) and `pm clear` executed for clean unpaired state.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: navigated into Developer options & 無線偵錯 sub-screen.
- Opened pairing dialog, auto-discovered pairing params `port=41413, code=690561`.
- Generated new RSA keypair + cert (`Megingiard-d01a`), executed `pair(127.0.0.1:41413)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned post-pairing connect port `36459`.
- Verified binary integrity, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake & version check on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).

No timeouts, 100% successful end-to-end pairing with pre-enabled Wireless Debugging in `zh-Hant-TW` locale.

## 05 — Dev OFF / USB ON / WiFi OFF / previously paired — PASS

Flow observed:

- Developer options manually disabled (`development_settings_enabled=0`), daemon terminated, while preserving saved ADB credentials (`paired`).
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated: automatically toggled Developer options main switch ON, navigated into 無線偵錯 sub-screen and toggled main switch ON.
- Scanned connect port `44071` from text.
- Re-used stored ADB credentials without triggering pairing popup.
- Pushed binaries, spawned daemon, and completed mutual auth handshake on port `51244` → `Connection succeeded using stored credentials!`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`).

No timeouts, 100% successful end-to-end auto-enabling of Developer options and reconnecting with saved credentials in `zh-Hant-TW` locale.

## 06 — Dev OFF / USB ON / WiFi OFF / first-time pairing — PASS

Flow observed:

- Developer options manually disabled (`development_settings_enabled=0`), daemon terminated, and `pm clear` executed.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: automatically toggled Developer options main switch ON, navigated into 無線偵錯 sub-screen.
- Opened pairing dialog, auto-discovered pairing params `port=38855`.
- Generated new RSA keypair + cert, executed `pair(127.0.0.1:38855)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned connect port `41185`.
- Verified binary integrity, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).

No timeouts, 100% successful end-to-end auto-enabling of Developer options and first-time pairing in `zh-Hant-TW` locale.

## 07 — Dev OFF / USB ON / WiFi ON / first-time pairing — PASS

Flow observed:

- Developer options manually disabled (`development_settings_enabled=0`), Wireless Debugging set (`adb_wifi_enabled=1`), daemon terminated, and `pm clear` executed.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: automatically toggled Developer options main switch ON, navigated into 無線偵錯 sub-screen.
- Opened pairing dialog, auto-discovered pairing params `port=40475, code=972266`.
- Generated new RSA keypair + cert (`Megingiard-0db4`), executed `pair(127.0.0.1:40475)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned connect port `33343`.
- Verified binary integrity, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).

No timeouts, 100% successful end-to-end auto-enabling of Developer options and first-time pairing in `zh-Hant-TW` locale.

## 08 — Dev OFF / USB ON / WiFi OFF / first-time pairing (Post-Reboot) — PASS

Flow observed:

- Device rebooted (`adb reboot`), Developer options manually disabled (`development_settings_enabled=0`), and `pm clear` executed post-reboot.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: automatically toggled Developer options main switch ON, navigated into 無線偵錯 sub-screen.
- Opened pairing dialog, auto-discovered pairing params `port=43185, code=893065`.
- Generated new RSA keypair + cert (`Megingiard-31f4`), executed `pair(127.0.0.1:43185)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned connect port `33579`.
- Verified binary integrity, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).

No timeouts, 100% successful end-to-end first-time pairing and daemon bootstrap on a freshly rebooted device in `zh-Hant-TW` locale.

## 10 — Dev OFF / USB ON / WiFi OFF / first-time pairing (Default DEBUG Level) — PASS

Flow observed:

- Code updated to default `logLevel` to `AppLog.Level.DEBUG` in `SettingsManager.kt` so that `pm clear` preserves verbose app logs automatically.
- Developer options manually disabled (`development_settings_enabled=0`), daemon terminated, and `pm clear` executed.
- System locale detected `zh-Hant-TW` → mapped config `zh-TW`.
- Auto Setup initiated via onboarding wizard: automatically toggled Developer options main switch ON, navigated into 無線偵錯 sub-screen.
- Opened pairing dialog, auto-discovered pairing params `port=38107, code=947951`.
- Generated new RSA keypair + cert (`Megingiard-57b2`), executed `pair(127.0.0.1:38107)` → `pair() → true`.
- Transitioned to `POST_PAIRING_STABILIZATION`, scanned connect port `37777`.
- Verified binary integrity, pushed binaries to `/data/local/tmp/`.
- Generated per-install AES-256-GCM pair key for UID `10225`, spawned daemon, performed handshake on port `51244` → `connect() succeeded`.
- `Privileged Mode is RUNNING`, restored top screen app (`com.odin.odinlauncher`), advanced wizard to FINISHED (step 5).
- Verified log output: 133 DEBUG logcat entries (`D=133`) automatically collected post `pm clear`.

No timeouts, 100% successful end-to-end first-time pairing with default DEBUG log level in `zh-Hant-TW` locale.



