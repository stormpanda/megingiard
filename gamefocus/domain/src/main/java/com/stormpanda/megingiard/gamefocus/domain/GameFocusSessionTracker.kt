package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.DisplayDetector
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager

private const val TAG = "GameFocusSessionTracker"
private const val PRIMARY_DISPLAY_ID = 0
private const val INVALID_DISPLAY_ID = -1
private const val THOR_SECONDARY_DISPLAY_FALLBACK = 4

private const val RETROARCH_PACKAGE_PREFIX = "com.retroarch"

private sealed interface SessionEntry {
    val packageName: String

    data class LaunchedApp(
        val appInfo: InstalledAppInfo,
    ) : SessionEntry {
        override val packageName: String
            get() = appInfo.packageName
    }

    data class DetectedPackage(
        override val packageName: String,
    ) : SessionEntry

    fun resolveApp(): InstalledAppInfo? =
        when (this) {
            is LaunchedApp -> appInfo
            is DetectedPackage -> InstalledAppsManager.installedApps.value.find { it.packageName == packageName && !it.isRom }
        }
}

/**
 * Tracks the most recently active applications across both screens (Display 0 and Display 4)
 * and restores them when the user presses Back on the home gallery.
 */
object GameFocusSessionTracker {
    @Volatile
    private var topSession: SessionEntry? = null

    @Volatile
    private var bottomSession: SessionEntry? = null

    internal var overrideSecondaryDisplayIdForTesting: Int? = null

    val lastTopPackage: String?
        get() = topSession?.packageName

    val lastBottomPackage: String?
        get() = bottomSession?.packageName

    val lastTopApp: InstalledAppInfo?
        get() = topSession?.resolveApp()

    val lastBottomApp: InstalledAppInfo?
        get() = bottomSession?.resolveApp()

    /**
     * Returns true if there is at least one tracked top or bottom app available to restore.
     */
    fun hasActiveSession(): Boolean = topSession != null || bottomSession != null

    /**
     * Records a launch initiated by Game Focus on the primary top display (Display 0).
     */
    fun recordTopLaunch(appInfo: InstalledAppInfo) {
        topSession = SessionEntry.LaunchedApp(appInfo)
        AppLog.i(TAG, "Recorded top screen launch: ${appInfo.label} (${appInfo.packageName})")
    }

    /**
     * Records a launch initiated by Game Focus on the secondary bottom display (Display 4).
     */
    fun recordBottomLaunch(appInfo: InstalledAppInfo) {
        bottomSession = SessionEntry.LaunchedApp(appInfo)
        AppLog.i(TAG, "Recorded bottom screen launch: ${appInfo.label} (${appInfo.packageName})")
    }

    /**
     * Records a window change detected by an accessibility service.
     */
    fun recordWindowChanged(
        displayId: Int,
        packageName: String,
        secondaryDisplayId: Int?,
    ) {
        val targetSecondaryId = secondaryDisplayId ?: THOR_SECONDARY_DISPLAY_FALLBACK
        if (displayId == PRIMARY_DISPLAY_ID || displayId == INVALID_DISPLAY_ID) {
            val currentTop = (topSession as? SessionEntry.LaunchedApp)?.appInfo
            if (currentTop != null && currentTop.isRom) {
                val isEmulatorPackage =
                    currentTop.emulatorPackage == packageName ||
                        (currentTop.retroArchCore != null && packageName.startsWith(RETROARCH_PACKAGE_PREFIX))
                if (isEmulatorPackage || packageName == currentTop.packageName) {
                    AppLog.d(TAG, "Window change matches active ROM emulator ($packageName), keeping ROM session")
                    return
                }
            }
            topSession = SessionEntry.DetectedPackage(packageName)
            AppLog.d(TAG, "Window changed on top screen ($displayId): package=$packageName")
        } else if (displayId == targetSecondaryId) {
            bottomSession = SessionEntry.DetectedPackage(packageName)
            AppLog.d(TAG, "Window changed on bottom screen ($displayId): package=$packageName")
        }
    }

    /**
     * Clears all session tracking state.
     */
    fun clearSession() {
        topSession = null
        bottomSession = null
        AppLog.d(TAG, "Cleared session tracking state")
    }

    internal fun resetForTesting() {
        clearSession()
        overrideSecondaryDisplayIdForTesting = null
    }

    /**
     * Restores the tracked previous session apps on their respective screens.
     *
     * @return true if at least one app launch/restore intent was dispatched.
     */
    suspend fun restorePreviousSession(context: Context): Boolean {
        if (!hasActiveSession()) {
            AppLog.d(TAG, "No active session to restore")
            return false
        }

        var topRestored = false
        var bottomRestored = false

        val topApp = lastTopApp
        val topPkg = lastTopPackage
        if (topApp != null) {
            AppLog.i(TAG, "Restoring top app from InstalledAppInfo: ${topApp.label}")
            topRestored = InstalledAppsManager.launchAppOnPrimaryDisplay(context, topApp)
            if (!topRestored && !topApp.isRom) {
                AppLog.w(TAG, "Primary display launch via InstalledAppInfo failed; falling back to package launch: ${topApp.packageName}")
                topRestored = launchPackageOnDisplay(context, topApp.packageName, PRIMARY_DISPLAY_ID)
            }
        } else if (topPkg != null) {
            AppLog.i(TAG, "Restoring top app from package: $topPkg")
            topRestored = launchPackageOnDisplay(context, topPkg, PRIMARY_DISPLAY_ID)
        }

        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
        val secondaryDisplayId = overrideSecondaryDisplayIdForTesting ?: secondaryDisplay?.displayId ?: THOR_SECONDARY_DISPLAY_FALLBACK

        val bottomApp = lastBottomApp
        val bottomPkg = lastBottomPackage
        if (bottomApp != null) {
            AppLog.i(TAG, "Restoring bottom app from InstalledAppInfo: ${bottomApp.label}")
            bottomRestored = InstalledAppsManager.launchAppOnSecondaryDisplay(context, bottomApp)
            if (!bottomRestored && !bottomApp.isRom) {
                AppLog.w(
                    TAG,
                    "Secondary display launch via InstalledAppInfo failed; falling back to package launch: ${bottomApp.packageName}",
                )
                bottomRestored = launchPackageOnDisplay(context, bottomApp.packageName, secondaryDisplayId)
            }
        } else if (bottomPkg != null) {
            AppLog.i(TAG, "Restoring bottom app from package: $bottomPkg on display $secondaryDisplayId")
            bottomRestored = launchPackageOnDisplay(context, bottomPkg, secondaryDisplayId)
        }

        AppLog.i(TAG, "Restored previous session: topRestored=$topRestored, bottomRestored=$bottomRestored")
        return topRestored || bottomRestored
    }

    private fun launchPackageOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int,
    ): Boolean {
        val pm = context.packageManager
        val launchIntent =
            pm.getLaunchIntentForPackage(packageName) ?: run {
                AppLog.w(TAG, "Cannot launch package '$packageName': launch intent not resolved")
                return false
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options =
            ActivityOptions.makeBasic().apply {
                setLaunchDisplayId(displayId)
            }
        return try {
            context.startActivity(launchIntent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch package '$packageName' on display $displayId: ${e.message}", e)
            false
        }
    }
}
