package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.DisplayDetector

private const val TAG = "GameFocusDefaultLauncherManager"

const val COMPANION_PACKAGE = "com.stormpanda.megingiard"
const val COMPANION_PACKAGE_DEBUG = "com.stormpanda.megingiard.debug"

/**
 * Coordinates default launcher detection and automatic companion app restoration
 * on the secondary bottom screen when returning to the home screen.
 */
object GameFocusDefaultLauncherManager {
    internal var overrideDefaultLauncherForTesting: Boolean? = null
    internal var overrideSecondaryDisplayIdForTesting: Int? = null

    internal fun resetForTesting() {
        overrideDefaultLauncherForTesting = null
        overrideSecondaryDisplayIdForTesting = null
    }

    /**
     * Checks whether Game Focus is currently configured as the system's default home launcher.
     */
    fun isDefaultLauncher(context: Context): Boolean {
        overrideDefaultLauncherForTesting?.let { return it }
        return try {
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
            val resolveInfo =
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
            val resolvedPackage = resolveInfo?.activityInfo?.packageName
            val isDefault = resolvedPackage != null && resolvedPackage == context.packageName
            AppLog.d(
                TAG,
                "Default launcher query: resolved='$resolvedPackage', current='${context.packageName}', isDefault=$isDefault",
            )
            isDefault
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to resolve default launcher: ${e.message}", e)
            false
        }
    }

    /**
     * Brings the Megingiard Companion app to the foreground on the secondary display (Display 4).
     *
     * Preserves the active Companion view/layout using [Intent.FLAG_ACTIVITY_NEW_TASK] and
     * [Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED].
     *
     * @return true if the companion launch intent was successfully dispatched.
     */
    fun launchCompanionOnSecondaryDisplay(context: Context): Boolean {
        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
        val targetDisplayId =
            overrideSecondaryDisplayIdForTesting ?: secondaryDisplay?.displayId ?: run {
                AppLog.w(TAG, "Cannot launch companion on secondary display: no secondary display found")
                return false
            }

        val pm = context.packageManager
        val isDebug = context.packageName.endsWith(".debug")
        val candidatePackages =
            if (isDebug) {
                listOf(COMPANION_PACKAGE_DEBUG, COMPANION_PACKAGE)
            } else {
                listOf(COMPANION_PACKAGE, COMPANION_PACKAGE_DEBUG)
            }

        for (pkg in candidatePackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val options =
                    ActivityOptions.makeBasic().apply {
                        setLaunchDisplayId(targetDisplayId)
                    }
                return try {
                    context.startActivity(launchIntent, options.toBundle())
                    AppLog.i(TAG, "Successfully launched/brought to front companion ($pkg) on display $targetDisplayId")
                    true
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to launch companion ($pkg) on display $targetDisplayId: ${e.message}", e)
                    false
                }
            }
        }

        AppLog.w(TAG, "Megingiard companion package not found among candidates: $candidatePackages")
        return false
    }

    /**
     * Handles home navigation by verifying default launcher status and restoring the companion app.
     *
     * @return true if Game Focus is default launcher and companion launch was initiated.
     */
    fun handleHomeNavigation(context: Context): Boolean {
        if (isDefaultLauncher(context)) {
            AppLog.i(TAG, "Game Focus is the default launcher; restoring companion on bottom screen")
            return launchCompanionOnSecondaryDisplay(context)
        }
        AppLog.d(TAG, "Game Focus is not the default launcher; skipping companion restoration on home")
        return false
    }
}
