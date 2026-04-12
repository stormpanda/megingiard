package com.stormpanda.megingiard.config

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroState
import com.stormpanda.megingiard.settings.SettingsManager
import java.time.Instant

private const val TAG = "ConfigExporter"

/**
 * Builds [MegingiardExport] snapshots from the current live state of all singletons.
 *
 * All reads are from [StateFlow.value] — safe to call on any thread.
 * The resulting export is self-contained and can be serialized immediately via [ConfigFileWriter].
 */
object ConfigExporter {

    /**
     * Builds a [ExportType.FULL] export from the current app state.
     * All sections (global, mirror, touchpad, keyboard, macropad) are populated.
     *
     * @param metadata Pre-filled or user-supplied metadata (see [defaultMetadata]).
     */
    fun buildFullExport(metadata: ExportMetadata): MegingiardExport {
        AppLog.i(TAG, "buildFullExport: author=${metadata.author}")
        val sm = SettingsManager

        val global = GlobalSettingsSection(
            enabledTools = sm.enabledTools.value.map { it.name },
            toolOrder = sm.toolOrder.value.map { it.name },
            overlayTimeoutMs = sm.overlayTimeoutMs.value,
            overlayAtBottom = sm.overlayAtBottom.value,
            accentColor = argbToHex(sm.accentColor.value.toArgb()),
            themeMode = sm.themeMode.value.name,
            appLanguage = sm.appLanguage.value.name,
            logLevel = sm.logLevel.value.name,
            rememberLastTool = sm.rememberLastTool.value,
        )

        val mirror = MirrorSettingsSection(
            autoStartCapture = sm.autoStartCapture.value,
            rememberViewport = sm.rememberViewport.value,
            rememberLock = sm.rememberLock.value,
            rememberProjection = sm.rememberProjection.value,
            pinchWhileProjecting = sm.pinchWhileProjecting.value,
        )

        val touchpad = TouchpadSettingsSection(
            useMouse = sm.touchpadUseMouse.value,
            tapToClick = sm.touchpadTapToClick.value,
            twoFingerTap = sm.touchpadTwoFingerTap.value,
        )

        val keyboard = KeyboardSettingsSection(
            layout = sm.kbLayout.value.name,
            trackpointEnabled = sm.kbTrackpointEnabled.value,
            mouseBtnPos = sm.kbMouseBtnPos.value.name,
            repeatEnabled = sm.kbRepeatEnabled.value,
            fullscreen = sm.kbFullscreen.value,
        )

        val macropadSettings = MacroPadSettingsSection(
            ambientEnabled = sm.macropadAmbientEnabled.value,
            ambientDim = sm.macropadAmbientDim.value,
            ambientPreview = sm.macropadAmbientPreview.value,
            ambientApplyTheme = sm.macropadAmbientApplyTheme.value,
            vignetteEnabled = sm.macropadAmbientVignetteEnabled.value,
            vignetteShape = sm.macropadAmbientVignetteShape.value.name,
            vignetteVisibleArea = sm.macropadAmbientVignetteVisibleArea.value,
            vignetteTransition = sm.macropadAmbientVignetteTransition.value,
            vignetteOpacity = sm.macropadAmbientVignetteOpacity.value,
            vignetteColor = argbToHex(sm.macropadAmbientVignetteColor.value),
        )

        val macropad = MacroPadSection(
            settings = macropadSettings,
            activeProfileId = MacroPadState.activeProfileId.value,
            profiles = MacroPadState.profiles.value,
            macros = MacroState.macros.value,
            macroFolders = MacroState.folders.value,
        )

        val sections = ExportSections(
            global = global,
            mirror = mirror,
            touchpad = touchpad,
            keyboard = keyboard,
            macropad = macropad,
        )

        val checksum = ChecksumUtil.computeChecksum(sections)
        AppLog.d(TAG, "buildFullExport: checksum=$checksum profiles=${macropad.profiles.size} macros=${macropad.macros.size}")

        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            type = ExportType.FULL,
            metadata = metadata,
            checksum = checksum,
            sections = sections,
        )
    }

    /**
     * Creates pre-filled [ExportMetadata] with the app version and device info.
     * Author, description, and tags are left blank for the user to fill in.
     */
    fun defaultMetadata(context: Context): ExportMetadata {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        return ExportMetadata(
            exportedAt = Instant.now().toString(),
            appVersionName = packageInfo?.versionName ?: "unknown",
            appVersionCode = packageInfo?.longVersionCode?.toInt() ?: 0,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    /** Formats an ARGB Int as `"#AARRGGBB"` uppercase hex. */
    internal fun argbToHex(argb: Int): String = "#%08X".format(argb)
}
