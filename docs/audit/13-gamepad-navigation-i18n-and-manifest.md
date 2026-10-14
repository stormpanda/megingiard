# 13 — Gamepad Navigation, Privileged Bootstrap, Manifest & Localization

> **Scope**: `:shared:ui` (`Gamepad*.kt`, `PrimaryOverlayInputBridge.kt`), `:companion:domain` (`PrivdAdbConnectionManager.kt`, `PrivdBootstrapper.kt`), `:companion:ui` (`MainActivity.kt`, `MirrorEditorTopOverlay.kt`, `CutoutLayoutEditor.kt`, `ConfigurationSettingsTab.kt`, `OnboardingWizardDialog.kt`, `AndroidManifest.xml`, `proguard-rules.pro`, `res/values*/strings.xml`), and `:gamefocus:ui` (`res/values*/strings.xml`).

---

## Summary of Findings

| ID | Severity | Component | Summary |
| :--- | :--- | :--- | :--- |
| **GPM-C01** | **CRITICAL** | `PrivdAdbConnectionManager.kt` / `PrivdBootstrapper.kt` | Premature ADB RSA/X.509 credential persistence in `getInstance()` poisons `hasCredentials()` (`isDevicePaired`) before pairing succeeds, locking Auto-Setup into an unrecoverable failure loop and storing raw unencrypted PKCS#8 keys on disk |
| **GPM-H01** | **HIGH** | `MainActivity.kt` / `AndroidManifest.xml` | Scoped Storage (`targetSdk = 35`) UID 2000 vs. app UID ownership mismatch in `ScreenshotTarget.TOP` / `BOTH` causes `BitmapFactory.decodeFile()` and `tempFile.delete()` to fail or leak `.temp_top_*.png` files |
| **GPM-H02** | **HIGH** | `MainActivity.kt` | `MainActivity.onCreate()` executes network update checks and Keystore HMAC key loading before `SignatureGuard.verify(this)` and leaks `MainActivity` via `SettingsManager` listeners |
| **GPM-H03** | **HIGH** | `GamepadScaffold.kt` | `GamepadTwoPaneScaffold` focus recovery unconditionally targets `activeCategoryRequester`, which silently fails on all sub-pages (`canFocus = false` when `isCustomBackActive == true`) |
| **GPM-H04** | **HIGH** | `PrimaryOverlayInputBridge.kt` | `Modifier.primaryOverlayFocusable` triggers `onClick()` on unpaired `KeyUp` (button bleed-through across screen/modal transitions) and strands `PressInteraction.Press` on `DPAD_CENTER`/`ENTER` |
| **GPM-H05** | **HIGH** | `MirrorEditorTopOverlay.kt` | Deleting a cutout in `MirrorEditorTopOverlay` (`DeleteCutoutCard`) orphans its `<cutoutId>.png` mask file in `filesDir/cutout_masks/` |
| **GPM-M01** | **MEDIUM** | `AndroidManifest.xml` / `proguard-rules.pro` | `MainActivity` is unnecessarily `android:exported="true"`, `LaunchTrampolineActivity` `.mgrd` intent filter omits `pathSuffix`/ZIP MIME types, and `proguard-rules.pro` omits 5 manifest components |
| **GPM-M02** | **MEDIUM** | `GamepadFormInputs.kt` / `GamepadCards.kt` | `GamepadSliderCard` omits `itemKey`, falling back to an ephemeral `FocusRequester` key that breaks focus restoration across sub-page pops and modal suspensions |
| **GPM-M03** | **MEDIUM** | `GamepadCards.kt` / `GamepadFormInputs.kt` / `GamepadColorPickers.kt` / `GamepadKeyHandlers.kt` | `GamepadAdjustableCard`, `GamepadSliderCard`, and `GamepadColorPaletteCard` omit `BackHandler` and Tier-2 value revert on `Button B` / `Back` (`GAMEPAD_NAVIGATION.md` violation) |
| **GPM-M04** | **MEDIUM** | `GamepadDialogControls.kt` | `rememberSaveExitPromptState` unconditionally calls `transferFocusToSidebar()` (plus a 50 ms retry), racing with sub-page parent-card focus restoration in `MacroPadEditor` |
| **GPM-M05** | **MEDIUM** | `PrimaryOverlayInputBridge.kt` / `GamepadDialogs.kt` | `processGenericMotionEvent` returns `false` on `L2`/`R2` trigger release when stick is neutral; `GamepadConfirmModal` lacks `InputMode.Keyboard` and `focusRecoveryEvents` handling |
| **GPM-M06** | **MEDIUM** | `CutoutLayoutEditor.kt` & 6 UI files | 8 unused `@Composable` functions across `:companion:ui` and `:shared:ui`, including a complete but unwired `CutoutLayoutEditorHelpModal` |
| **GPM-M07** | **MEDIUM** | `strings.xml` / `ConfigurationSettingsTab.kt` / `MirrorEditorTopOverlay.kt` / `OnboardingWizardDialog.kt` | Untranslated strings in `values-de` and `values-zh-rTW`, missing `debug/res/values-zh-rTW/strings.xml` in `:gamefocus:ui`, and hardcoded English UI strings in Composables |

---

## Detailed Findings

### [GPM-C01] CRITICAL — Premature ADB Credential Persistence Poisons `hasCredentials()` (`isDevicePaired`) Before Pairing Succeeds and Stores Raw PKCS#8 Private Key Unencrypted

- **Files**:
  - [PrivdAdbConnectionManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdAdbConnectionManager.kt#L72-L76)
  - [PrivdAdbConnectionManager.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdAdbConnectionManager.kt#L112-L203)
  - [PrivdBootstrapper.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/domain/src/main/java/com/stormpanda/megingiard/privd/PrivdBootstrapper.kt#L144-L207)
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L350-L352)
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L945-L951)

- **Code Quote**:
  ```kotlin
  // PrivdAdbConnectionManager.kt:72-76, 112-122, 194-200
  fun hasCredentials(context: Context): Boolean {
      val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
      val certFile = File(context.noBackupFilesDir, CERT_FILE_NAME)
      return keyFile.exists() && certFile.exists()
  }
  ...
  private fun loadOrCreateCredentials() {
      val keyFile = File(context.noBackupFilesDir, KEY_FILE_NAME)
      val certFile = File(context.noBackupFilesDir, CERT_FILE_NAME)

      if (keyFile.exists() && certFile.exists()) {
          ...
      }
      generateAndSaveCredentials(keyFile, certFile)
  }
  ...
  keyFile.writeBytes(privateKey.encoded)
  certFile.writeBytes(cert.encoded)
  ```
  ```kotlin
  // PrivdBootstrapper.kt:147-155 & 202-206
  suspend fun pair(context: Context, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
      try {
          val mgr = PrivdAdbConnectionManager.getInstance(context)
          val paired = mgr.pair(port, code)
  ```

- **Root Cause**:
  1. `PrivdAdbConnectionManager.getInstance(context)` calls `loadOrCreateCredentials()` inside its `init` block (`line 53`), which immediately generates the 2048-bit RSA keypair and self-signed X.509 certificate and writes `privd_adb_key.bin` and `privd_adb_cert.bin` to `context.noBackupFilesDir` (`lines 195-196`) **before** any SPAKE2+ pairing handshake (`mgr.pair(port, code)`) has even been attempted.
  2. Furthermore, `PrivdBootstrapper.bootstrapAndConnect(context)` (`line 202`) also calls `PrivdAdbConnectionManager.getInstance(context)` before `mgr.connect(...)`, which likewise creates `privd_adb_key.bin` and `privd_adb_cert.bin` on disk even on an unpaired device.
  3. Because `PrivdAdbConnectionManager.hasCredentials(context)` (`lines 72-76`) and `MainActivity.kt` (`lines 350-352`: `val isPaired = PrivdAdbConnectionManager.hasCredentials(this)`) determine `isDevicePaired` / `hasAdbCredentials` solely from `keyFile.exists() && certFile.exists()`, the very first call to `getInstance(context)` — even if the user mistypes the pairing code, cancels the dialog, or the pairing port times out — permanently flips `hasAdbCredentials` / `isDevicePaired` to `true`.
  4. Once `hasAdbCredentials` is `true`, `OnboardingWizardDialog.kt` (`lines 945-951`) and `MegingiardAccessibilityService` mark the Pairing checklist stage as `ChecklistStatus.DONE` and skip `STEP_2_PAIR_WAITING` entirely, jumping straight to `bootstrapAndConnect()`. Because the keypair was never actually registered with Android's `adbd` via SPAKE2+, TLS authentication fails with `ADB_PAIRING_REQUIRED`, trapping the user in an unrecoverable Auto-Setup failure loop until app storage is cleared or manual reset is invoked.
  5. **Security Impact**: Unlike `PrivdPairKey.kt` (which encrypts the `privd` HMAC key under Android Keystore AES-256-GCM), `PrivdAdbConnectionManager` writes the raw PKCS#8 RSA private key bytes directly to `privd_adb_key.bin` in plaintext (`keyFile.writeBytes(privateKey.encoded)` at `line 195`). This private key is a persistent Wireless Debugging trust credential that grants full `shell` (UID 2000) execution over localhost TLS whenever Wireless Debugging is enabled.

- **Recommendation**:
  1. Separate **keypair generation/storage** from **verified pairing status**: keep the generated keypair in memory (or in temporary staging files) until `mgr.pair(port, code)` returns `true` (or `mgr.connect()` succeeds), or maintain an explicit verified flag (`privd_adb_paired.flag` written only after `pair()` or `connect()` succeeds, and cleared whenever `connect()` fails with `AdbAuthenticationFailedException` / `ADB_PAIRING_REQUIRED`).
  2. Encrypt `privd_adb_key.bin` at rest using an Android Keystore `AES/GCM/NoPadding` secret key (matching the pattern in `PrivdPairKey.kt`) or generate/store the RSA keypair inside Android Keystore.

---

### [GPM-H01] HIGH — Scoped Storage (`targetSdk = 35`) UID Ownership Mismatch & Temp File Leak in Screenshot Capture

- **Files**:
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L647-L710)
  - [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L822-L860)
  - [AndroidManifest.xml](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/AndroidManifest.xml#L1-L11)

- **Code Quote**:
  ```kotlin
  // MainActivity.kt:648-655 & 683-702
  val dir = getScreenshotsDir() // File("/storage/emulated/0/Pictures/Screenshots")
  val filepath = File(dir, "Screenshot_$timestamp.png").absolutePath
  val ok = PrivdClient.requestScreenshot(0, filepath)
  ...
  val tempTopPath = File(dir, ".temp_top_$timestamp.png").absolutePath
  val ok = PrivdClient.requestScreenshot(0, tempTopPath)
  if (ok) {
      val topBmp = BitmapFactory.decodeFile(tempTopPath)
      val bottomBmp = captureBottomDisplayBitmap()
      ...
      File(tempTopPath).delete()
  }
  ```

- **Root Cause**:
  1. For `ScreenshotTarget.TOP` (`lines 647-660`) and `ScreenshotTarget.BOTH` (`lines 681-710`), `MainActivity` instructs `privd` (which runs as UID 2000 `shell`) to run `/system/bin/screencap -d 0 -p <filepath>` writing directly into `/storage/emulated/0/Pictures/Screenshots/`.
  2. Under Android 13–15 (`minSdk = 33`, `targetSdk = 35`) Scoped Storage (FUSE), files created in `/storage/emulated/0/Pictures/Screenshots/` by UID 2000 (`shell`) are attributed to UID 2000 (or `owner_package_name = com.android.shell`), **not** to `com.stormpanda.megingiard`.
  3. Because `AndroidManifest.xml` does not request `READ_MEDIA_IMAGES`, the unprivileged app process (`MainActivity`) is denied read and delete access by the FUSE daemon on files owned by UID 2000 in shared storage:
     - In `ScreenshotTarget.BOTH`, `BitmapFactory.decodeFile(tempTopPath)` (`line 687`) returns `null`, causing `BOTH` screenshot stitching to fail and silently fall back to saving only the bottom display (`lines 697-699`), while `File(tempTopPath).delete()` (`line 701`) fails (`EACCES`), leaking `.temp_top_<timestamp>.png` in `/storage/emulated/0/Pictures/Screenshots/`.
     - In `ScreenshotTarget.TOP`, `scanAndToast(filepath)` (`lines 822-835`) calls `BitmapFactory.decodeFile(filepath)` (`line 829`) on the main thread to populate `QuickMenuScreenshotResult.thumbnail`; this also returns `null` (broken thumbnail preview) and performs synchronous full-resolution PNG decoding on the UI thread.
  4. Additionally, `saveBitmapToPictures()` (`lines 844-860`) writes directly via `java.io.File("/storage/emulated/0/Pictures/Screenshots", ...)` and `FileOutputStream` rather than `MediaStore.Images.Media` with `ContentValues(IS_PENDING = 1)`.

- **Recommendation**:
  1. For `ScreenshotTarget.BOTH`, either write the temporary top-screen capture into `/data/local/tmp/` and stream/chmod it, or add a `SCREENSHOT_STREAM <display_id>` command to `privd` that streams the PNG/raw bytes over the authenticated Unix domain socket directly into the app's memory/cache directory, avoiding shared storage for intermediate files altogether.
  2. Decode the preview thumbnail off the UI thread (`withContext(Dispatchers.IO)`) using `BitmapFactory.Options().apply { inSampleSize = 4 }`.
  3. Use `MediaStore.Images.Media` (`ContentResolver.insert` + `openOutputStream`) when saving app-generated bitmaps (`BOTTOM` and stitched `BOTH`).

---

### [GPM-H02] HIGH — `MainActivity.onCreate()` Executes Network & Keystore Operations Before `SignatureGuard.verify(this)` and Leaks `MainActivity` in `SettingsManager`

- **File**: [MainActivity.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/MainActivity.kt#L270-L345)

- **Code Quote**:
  ```kotlin
  // MainActivity.kt:271-298
  SettingsManager.init(this)
  InjectorLifecycleManager.watch(this)
  if (SettingsManager.updateCheckOnStart) {
      lifecycleScope.launch {
          UpdateManager.checkForUpdates(
              currentVersion = BuildConfig.VERSION_NAME,
              includePreReleases = SettingsManager.updateIncludePrereleases
          )
      }
  }
  // Load Privd HMAC Key before probing so the challenge-response succeeds
  if (!PrivdClient.hasKey()) {
      val key = PrivdPairKey.load(this)
      if (key != null) {
          PrivdClient.setKey(key)
      }
  }

  // Runtime APK signature tamper check (no-op in debug builds)
  SignatureGuard.verify(this)
  ```
  ```kotlin
  // MainActivity.kt:334-339
  SettingsManager.onThemeChangedListener = {
      MegingiardSettingsProvider.notifyThemeChanged(this)
  }
  SettingsManager.onSettingsChangedListener = {
      MegingiardSettingsProvider.notifySettingsChanged(this)
  }
  ```

- **Root Cause**:
  1. `SignatureGuard.verify(this)` is called at `line 297`, **after** `SettingsManager.init(this)`, `InjectorLifecycleManager.watch(this)` (which starts background coroutines connecting to `privd`), `UpdateManager.checkForUpdates(...)`, and `PrivdPairKey.load(this)` / `PrivdClient.setKey(key)` (which decrypts the Keystore HMAC key into process memory). Even if `SignatureGuard.verify(this)` calls `finishAffinity()` on a tampered APK, background coroutines and `PrivdClient` state are already active in the process.
  2. At `lines 334-339`, `SettingsManager.onThemeChangedListener` and `SettingsManager.onSettingsChangedListener` capture `this` (`MainActivity`) inside lambdas stored on the process-wide `SettingsManager` singleton, and `onDestroy()` (`lines 586-594`) never clears them (or passes `applicationContext`), leaking the `MainActivity` instance across configuration changes or activity recreation.

- **Recommendation**:
  1. Move `SignatureGuard.verify(this)` to the very first line of `MainActivity.onCreate()` (immediately after `super.onCreate(savedInstanceState)`), and return immediately if verification fails.
  2. Capture `val appContext = applicationContext` in `SettingsManager.onThemeChangedListener` and `onSettingsChangedListener`.

---

### [GPM-H03] HIGH — `GamepadTwoPaneScaffold` Focus Recovery Broken on All Sub-Pages

- **File**: [GamepadScaffold.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadScaffold.kt#L661-L735)

- **Code Quote**:
  ```kotlin
  // GamepadScaffold.kt:683-700 & 727-730
  LaunchedEffect(activeCategoryRequester) {
      PrimaryOverlayInputBridge.focusRecoveryEvents.collect {
          for (attempt in 0 until FOCUS_RETRY_ATTEMPTS) {
              val focused = try { activeCategoryRequester.requestFocus(); true } catch (_: Exception) { false }
              if (focused) break
              delay(FOCUS_RETRY_DELAY_MS)
          }
      }
  }
  ...
  .focusProperties { canFocus = !isCustomBackActive }
  ```

- **Root Cause**:
  When inside any sub-page (`isCustomBackActive == true`), the Left Category Sidebar Rail sets `canFocus = !isCustomBackActive` (`false`) at `line 729`.
  However, the `PrimaryOverlayInputBridge.focusRecoveryEvents` collector (`lines 683-700`) unconditionally calls `activeCategoryRequester.requestFocus()`. Because `requestFocus()` returns `false` rather than throwing when `canFocus = false`, `focused` evaluates to `true` on `attempt == 0` and exits the loop without focusing anything. If focus is lost inside a sub-page (e.g., after touch scroll or dynamic card removal), D-Pad focus recovery fails instead of targeting `savedFocusKeyByDepth[effectiveNavKey]` or `firstContentRequester` (unlike `lines 664-674`).

- **Recommendation**:
  In the `focusRecoveryEvents` collector (`lines 683-700`), check `if (isCustomBackActive)` (mirroring `lines 664-674`): when inside a sub-page, focus `savedFocusKeyByDepth[effectiveNavKey]?.let { itemRequesters[it] } ?: firstContentRequester`, and use the boolean return value of `requestFocus()`.

---

### [GPM-H04] HIGH — `Modifier.primaryOverlayFocusable` Triggers `onClick` on Unpaired `KeyUp` (Bleed-Through) and Strands `PressInteraction.Press`

- **File**: [PrimaryOverlayInputBridge.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt#L223-L261)

- **Code Quote**:
  ```kotlin
  // PrimaryOverlayInputBridge.kt:225-260
  val keyHandlerModifier = if (onClick != null) {
      Modifier.onKeyEvent { keyEvent ->
          if (keyEvent.type == KeyEventType.KeyUp) {
              when (keyEvent.nativeKeyEvent.keyCode) {
                  KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                  KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> { onClick(); true }
                  else -> false
              }
          } else if (keyEvent.type == KeyEventType.KeyDown) {
              keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_A
          } else false
      }
  } else Modifier
  this.focusRequester(requester).onFocusChanged { isFocused = it.isFocused }
      .then(clickableModifier).then(keyHandlerModifier)
  ```

- **Root Cause**:
  1. **Unpaired `KeyUp` Bleed-Through**: `keyHandlerModifier` invokes `onClick()` on `KeyEventType.KeyUp` without tracking whether the matching `KeyDown` was received while this node was focused (unlike `GamepadCards.kt:127-166` which tracks `isPressedByKey`). Pressing `Button A` to open a dialog/overlay that auto-focuses a `primaryOverlayFocusable` button causes the subsequent `KeyUp` to immediately trigger `onClick()` on the newly focused button.
  2. **Stranded `PressInteraction.Press` Ripple**: Because `clickableModifier` precedes `keyHandlerModifier`, `KeyDown` for `KEYCODE_DPAD_CENTER` and `KEYCODE_ENTER` is not consumed by `keyHandlerModifier` (`lines 230-233`) and bubbles to `clickableModifier` (emitting `PressInteraction.Press`), whereas `KeyUp` is consumed by `keyHandlerModifier` before `clickableModifier` sees it, leaving the press ripple stuck.

- **Recommendation**:
  Track `var isKeyDownOnThisNode by remember { mutableStateOf(false) }` (cleared when `!state.isFocused`), consume `KeyDown` for all 4 activation keycodes in `keyHandlerModifier`, and only invoke `onClick()` on `KeyUp` if `isKeyDownOnThisNode` was `true`.

---

### [GPM-H05] HIGH — Deleting a Cutout in `MirrorEditorTopOverlay` Leaks Its PNG Mask File on Disk

- **File**: [MirrorEditorTopOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt#L723-L731)

- **Code Quote**:
  ```kotlin
  // MirrorEditorTopOverlay.kt:723-730
  DeleteCutoutCard(onDelete = {
      val updated = cutouts.filter { it.id != activeCutout.id }
      SettingsManager.mirrorCutouts = updated
      SettingsManager.saveCurrentSettings(context)
      selectedId = updated.firstOrNull()?.id
  })
  ```

- **Root Cause**:
  Unlike `GlobalSettingsViewModel.deleteMirrorCutout()` (`GlobalSettingsViewModel.kt:525`) and `MacroPadState.removeLayoutCutout()` (`MacroPadState.kt:595`), `MirrorEditorTopOverlay.kt` (`lines 723-730`) removes `activeCutout` without calling `CutoutMaskManager.deleteMask(context, activeCutout.id)`, orphaning `filesDir/cutout_masks/<cutoutId>.png` on disk (and in subsequent `.mgrd` exports).

- **Recommendation**:
  Call `CutoutMaskManager.deleteMask(context, activeCutout.id)` inside `DeleteCutoutCard`'s `onDelete` callback in `MirrorEditorTopOverlay.kt`.

---

### [GPM-M01] MEDIUM — `AndroidManifest.xml` Exported `MainActivity`, Narrow `.mgrd` Intent Filter, and Incomplete ProGuard Keep Rules

- **Files**:
  - [AndroidManifest.xml](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/AndroidManifest.xml#L33-L63)
  - [proguard-rules.pro](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/proguard-rules.pro#L25-L27)

- **Code Quote**:
  ```xml
  <!-- AndroidManifest.xml:44-61 -->
  <intent-filter>
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:mimeType="application/vnd.megingiard.config+json" />
  </intent-filter>
  </activity>
  <activity android:name=".MainActivity" android:exported="true"
  ```

- **Root Cause**:
  1. `MainActivity` is `android:exported="true"` (`line 60`) with no `<intent-filter>` or permission guard, allowing external callers to bypass `LaunchTrampolineActivity`'s Display 4 routing.
  2. `LaunchTrampolineActivity`'s `VIEW` `<intent-filter>` (`lines 44-48`) only matches `application/vnd.megingiard.config+json`, so Android file managers cannot open `.mgrd` ZIP files (`application/octet-stream` / `application/zip`).
  3. `companion/ui/proguard-rules.pro` (`lines 25-26`) only lists `MainActivity` and `ScreenCaptureService`, omitting `LaunchTrampolineActivity`, `PrimaryFocusAnchorActivity`, `PrimaryOverlayActivity`, `MegingiardAccessibilityService`, and `MegingiardSettingsProvider` (`AGENTS.md §13`).

- **Recommendation**:
  Set `android:exported="false"` on `MainActivity`, add `pathSuffix=".mgrd"` intent `<data>` filters on `LaunchTrampolineActivity`, and add explicit keep rules for all manifest components in `proguard-rules.pro`.

---

### [GPM-M02] MEDIUM — `GamepadSliderCard` Missing `itemKey` Breaks Focus Restoration Across Sub-Pages & Modal Suspensions

- **Files**:
  - [GamepadFormInputs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L458-L504)
  - [GamepadCards.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L81-L108)

- **Code Quote**:
  ```kotlin
  // GamepadFormInputs.kt:458-503 & GamepadCards.kt:101
  fun GamepadSliderCard(title: String, value: Float, onValueChange: (Float) -> Unit, ...) {
      GamepadFocusCard(modifier = ..., onClick = ..., isSubModalActive = isAdjusting) { ... }
  }
  val effectiveKey = itemKey ?: cardFocusRequester
  ```

- **Root Cause**:
  Unlike all other `Gamepad*Card` components in `:shared:ui`, `GamepadSliderCard` omits `itemKey: Any? = title`, causing `GamepadFocusCard` (`line 101`) to fall back to `cardFocusRequester` — an ephemeral `FocusRequester` recreated on remount that never matches `savedFocusKeyByDepth` in `GamepadTwoPaneScaffold`.

- **Recommendation**:
  Add `itemKey: Any? = title` to `GamepadSliderCard` and forward `itemKey = itemKey` to `GamepadFocusCard`.

---

### [GPM-M03] MEDIUM — `GamepadAdjustableCard`, `GamepadSliderCard`, & `GamepadColorPaletteCard` Missing `BackHandler` and Tier-2 Cancel Semantics

- **Files**:
  - [GamepadCards.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadCards.kt#L586-L648)
  - [GamepadFormInputs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadFormInputs.kt#L458-L542)
  - [GamepadColorPickers.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadColorPickers.kt#L109-L209)
  - [GamepadKeyHandlers.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadKeyHandlers.kt#L188-L248)
  - [docs/GAMEPAD_NAVIGATION.md](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/docs/GAMEPAD_NAVIGATION.md#L310-L317)

- **Code Quote**:
  ```kotlin
  // GamepadKeyHandlers.kt:209-214
  KeyEvent.KEYCODE_BUTTON_B,
  KeyEvent.KEYCODE_BACK -> {
      onExitAdjusting()
      true
  }
  ```

- **Root Cause**:
  1. `docs/GAMEPAD_NAVIGATION.md` (`lines 310-317`, "Tier 2 — Inline Adjustment Mode") specifies that pressing `A` confirms the adjustment while pressing `B` / `Back` **cancels** the adjustment (or exits adjustment mode when intercepted by back navigation).
  2. Unlike `GamepadTextFieldCard` (`GamepadFormInputs.kt:290-303`) and `GamepadReorderCard` (`GamepadListCards.kt:120-131`), which register both `LocalDeckBackInterceptor` and `BackHandler(enabled = ...)` so system Back gestures / hardware Back events exit Tier 2 instead of closing the entire screen or sub-page, `GamepadAdjustableCard`, `GamepadSliderCard`, and `GamepadColorPaletteCard` register **neither** `BackHandler` nor `LocalDeckBackInterceptor`. If a user triggers an Android system Back gesture (or a Back event routed through `OnBackPressedDispatcher`) while adjusting a slider or color palette, the entire sub-page or overlay closes instead of exiting adjustment mode.
  3. Furthermore, none of the three components snapshot `initialValue` when entering `isAdjusting = true` to revert on `KEYCODE_BUTTON_B` / `KEYCODE_BACK` / `KEYCODE_ESCAPE` (`handleAdjustmentKeyEvent` in `GamepadKeyHandlers.kt:209-214` also omits `KEYCODE_ESCAPE`).

- **Recommendation**:
  Register `BackHandler(enabled = isAdjusting)` and `LocalDeckBackInterceptor` in `GamepadAdjustableCard`, `GamepadSliderCard`, and `GamepadColorPaletteCard`, include `KeyEvent.KEYCODE_ESCAPE` in `handleAdjustmentKeyEvent`, and snapshot the pre-adjustment value when `isAdjusting` transitions to `true` so `Button B` / `Back` restores the original value.

---

### [GPM-M04] MEDIUM — `rememberSaveExitPromptState` Races With Sub-Page Parent Card Focus Restoration

- **File**: [GamepadDialogControls.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogControls.kt#L153-L191)

- **Code Quote**:
  ```kotlin
  // GamepadDialogControls.kt:163-179
  onSave = {
      isConfirmingSave = false
      onSave()
      transferFocusToSidebar()
  },
  onDiscard = {
      isConfirmingSave = false
      onDiscard()
      transferFocusToSidebar()
  },
  ```

- **Root Cause**:
  `rememberSaveExitPromptState` is used both for top-level tab exits and for sub-page exits inside `MacroPadEditor` (`MacroPadEditor.kt:223-242`, e.g., when backing out of editing a button or macro back to the parent content list).
  When `onSave` or `onDiscard` is chosen, `rememberSaveExitPromptState` unconditionally calls `transferFocusToSidebar()`, which immediately focuses `activeCategoryRequester` (the left sidebar rail) and launches a coroutine that waits `50 ms` (`SAVE_EXIT_FOCUS_RETRY_DELAY_MS`) and calls `activeCategoryRequester.requestFocus()` again (`lines 153-160`).
  Meanwhile, popping the sub-page changes `effectiveNavKey` in `GamepadTwoPaneScaffold` (`GamepadScaffold.kt:661-680`), which attempts to restore focus to the parent card in the right-hand content pane (`savedFocusKeyByDepth[effectiveNavKey]`). After 50 ms, `transferFocusToSidebar()`'s delayed retry steals focus away from the restored content card back to the left sidebar.

- **Recommendation**:
  Only call `transferFocusToSidebar()` when exiting to the top-level category rail (or pass a parameter `restoreToSidebar: Boolean = true` that is `false` when popping an internal sub-page where `savedFocusKeyByDepth` handles focus restoration).

---

### [GPM-M05] MEDIUM — `PrimaryOverlayInputBridge.processGenericMotionEvent` Returns `false` on `L2`/`R2` Release & `GamepadConfirmModal` Lacks Focus Recovery

- **Files**:
  - [PrimaryOverlayInputBridge.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/PrimaryOverlayInputBridge.kt#L117-L156)
  - [GamepadDialogs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadDialogs.kt#L216-L267)

- **Code Quote**:
  ```kotlin
  // PrimaryOverlayInputBridge.kt:118-136 & 155
  val l2Pressed = lTrigger > STICK_DEADZONE
  if (l2Pressed != isL2Pressed) {
      isL2Pressed = l2Pressed
      val action = if (l2Pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
      onDpadKey(action, KeyEvent.KEYCODE_BUTTON_L2)
  }
  ...
  return targetKeyCode != 0 || isL2Pressed || isR2Pressed
  ```

- **Root Cause**:
  1. In `PrimaryOverlayInputBridge.processGenericMotionEvent()`, when the user releases `L2` or `R2` (`l2Pressed` transitions from `true` to `false`) while the left analog stick is neutral (`targetKeyCode == 0`), `isL2Pressed` is set to `false` at `line 120`, `onDpadKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2)` is dispatched at `line 125`, and then `line 155` returns `targetKeyCode != 0 || isL2Pressed || isR2Pressed` — which evaluates to `false` even though the motion event was just handled to synthesize `ACTION_UP`!
  2. In `GamepadDialogs.kt` (`lines 216-267`), `GamepadConfirmModal` requests focus once inside `LaunchedEffect(Unit)` (`lines 240-248`), but unlike `GamepadSelectDialog` (`lines 109-125`), it neither calls `inputModeManager.requestInputMode(InputMode.Keyboard)` nor collects `PrimaryOverlayInputBridge.focusRecoveryEvents`. If the user touches anywhere on the modal backdrop or card without hitting a button, Compose enters `InputMode.Touch`, focus is lost, and pressing D-Pad cannot recover focus inside `GamepadConfirmModal`.

- **Recommendation**:
  1. Track `var triggerChanged = false` inside `processGenericMotionEvent` and return `triggerChanged || targetKeyCode != 0 || isL2Pressed || isR2Pressed`.
  2. In `GamepadConfirmModal`, call `inputModeManager.requestInputMode(InputMode.Keyboard)` and collect `PrimaryOverlayInputBridge.focusRecoveryEvents` to re-focus `cancelFocusRequester`, matching `GamepadSelectDialog`.

---

### [GPM-M06] MEDIUM — 8 Unused `@Composable` Functions Including Unwired `CutoutLayoutEditorHelpModal`

- **Files**:
  - [CutoutLayoutEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/CutoutLayoutEditor.kt#L628-L688) (`CutoutLayoutEditorHelpModal`)
  - [WelcomeTutorialDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/WelcomeTutorialDialog.kt#L52-L120) (`WelcomeTutorialDialog`)
  - [QuickMenuTutorialDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/QuickMenuTutorialDialog.kt#L458-L485) (`TutorialPulseHighlight`)
  - [MacroPadEditor.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/macropad/MacroPadEditor.kt#L729-L760) (`DeckDropdownField`)
  - [AppTheme.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/theme/AppTheme.kt#L331-L345) (`rememberBezelBrush`)
  - [CutoutLetterCircleIcon.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/CutoutLetterCircleIcon.kt#L138-L210) (`AddCutoutCircleIcon`, `SelectedCutoutCircleIcon`)
  - [GamepadGlyphs.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/shared/ui/src/main/java/com/stormpanda/megingiard/ui/GamepadGlyphs.kt#L99-L130) (`GamepadButtonPill`)

- **Root Cause**:
  A cross-module call-graph scan of all `@Composable` functions revealed 8 dead/unreferenced Composables. Most notably, `CutoutLayoutEditorHelpModal` (`CutoutLayoutEditor.kt:628-688`) is a complete help modal with localized strings (`R.string.cutout_editor_help_*`) and is documented as active in `docs/features/help/FEATURE.md:89, 146`, but no help button or state variable in `CutoutLayoutEditor` or `MirrorEditorTopOverlay` ever invokes it.

- **Recommendation**:
  Wire `CutoutLayoutEditorHelpModal` to a help button in `CutoutLayoutEditor` / `MirrorEditorTopOverlay` (fulfilling `docs/features/help/FEATURE.md`), and remove or consolidate the remaining dead Composables (`WelcomeTutorialDialog`, `TutorialPulseHighlight`, `DeckDropdownField`, `AddCutoutCircleIcon`, `SelectedCutoutCircleIcon`, `GamepadButtonPill`).

---

### [GPM-M07] MEDIUM — Untranslated Locale Strings, Missing `:gamefocus:ui` Debug Locale File, & Hardcoded English UI Strings (`AGENTS.md §9.2`)

- **Files**:
  - [companion/ui/src/main/res/values-de/strings.xml](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/res/values-de/strings.xml#L65) (`macropad_no_profile`, `onboarding_btn_finish` at line 939)
  - [companion/ui/src/main/res/values-zh-rTW/strings.xml](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/res/values-zh-rTW/strings.xml#L65) (`macropad_no_profile`, `macropad_action_group_app_launcher` at line 625, `integration_home_hero_idle_title` at line 1067)
  - [ConfigurationSettingsTab.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/settings/tabs/ConfigurationSettingsTab.kt#L340-L410)
  - [MirrorEditorTopOverlay.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/mirror/MirrorEditorTopOverlay.kt#L794)
  - [OnboardingWizardDialog.kt](file:///Users/maikthomalla/.gemini/antigravity/worktrees/Megingiard/check_current_time/companion/ui/src/main/java/com/stormpanda/megingiard/ui/onboarding/OnboardingWizardDialog.kt#L778)

- **Code Quote**:
  ```kotlin
  // ConfigurationSettingsTab.kt:340-348, 394, 408
  if (p.profiles.isNotEmpty()) parts.add("${p.profiles.size} ${if (p.profiles.size == 1) "profile" else "profiles"}")
  if (p.layouts.isNotEmpty()) parts.add("${p.layouts.size} ${if (p.layouts.size == 1) "layout" else "layouts"}")
  if (p.macros.isNotEmpty()) parts.add("${p.macros.size} ${if (p.macros.size == 1) "macro" else "macros"}")
  if (p.backgrounds.isNotEmpty()) parts.add("${p.backgrounds.size} ${if (p.backgrounds.size == 1) "background" else "backgrounds"}")
  ...
  subtitle = "${p.layouts.size} layouts, ${p.macros.size} macros",
  ```
  ```kotlin
  // MirrorEditorTopOverlay.kt:794
  val posLabel = if (cutouts.isNotEmpty() && currentIdx >= 0) "Cutout ${currentIdx + 1}" else stringResource(R.string.cutout_editor_none)
  ```

- **Root Cause**:
  1. **Untranslated strings in locale resource files**:
     - `macropad_no_profile` (`"No Profile Selected — Tap to Configure"`) is still in English in both `values-de/strings.xml:65` and `values-zh-rTW/strings.xml:65`.
     - `macropad_action_group_app_launcher` (`"App Launcher"`) and `integration_home_hero_idle_title` (`"Ready to Play"`) are still in English in `values-zh-rTW/strings.xml:625, 1067`.
     - `onboarding_btn_finish` (`"Finish Setup"`) is still in English in `values-de/strings.xml:939`.
  2. **Missing debug locale file in `:gamefocus:ui`**:
     - `gamefocus/ui/src/debug/res/values/strings.xml` and `values-de/strings.xml` define `app_name` (`"Game Focus Debug"`), but `gamefocus/ui/src/debug/res/values-zh-rTW/strings.xml` is missing.
  3. **Hardcoded English strings and string concatenation in Composables (`AGENTS.md §9.2`)**:
     - `ConfigurationSettingsTab.kt` (`lines 340-348, 394, 408`) builds import preview counts using hardcoded English words (`"profile"`, `"profiles"`, `"layout"`, `"layouts"`, `"macro"`, `"macros"`, `"background"`, `"backgrounds"`).
     - `MirrorEditorTopOverlay.kt` (`line 794`) uses hardcoded `"Cutout ${currentIdx + 1}"`.
     - `OnboardingWizardDialog.kt` (`lines 778, 1017`) uses hardcoded English fallback `"Daemon connection failed."`.

- **Recommendation**:
  Translate the 4 untranslated entries in `values-de` and `values-zh-rTW`, add `gamefocus/ui/src/debug/res/values-zh-rTW/strings.xml`, and replace the hardcoded strings in `ConfigurationSettingsTab.kt`, `MirrorEditorTopOverlay.kt`, and `OnboardingWizardDialog.kt` with parameterized `stringResource(R.string.xxx, ...)` / `pluralStringResource(...)` entries across all 3 locales.
