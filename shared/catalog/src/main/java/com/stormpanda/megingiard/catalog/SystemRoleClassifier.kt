package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SystemRoleClassifier"
private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val ANDROID_FRAMEWORK_PACKAGE = "android"

/**
 * Deterministic system role classifier for identifying home launchers, task switchers,
 * and system UI packages using canonical Android PackageManager intent resolution.
 */
object SystemRoleClassifier {
    private val _launcherPackages = MutableStateFlow<Set<String>>(emptySet())
    val launcherPackages: StateFlow<Set<String>> = _launcherPackages.asStateFlow()

    @Volatile
    private var isInitialized = false

    /**
     * Initializes the classifier and queries PackageManager for all activities declaring
     * [Intent.CATEGORY_HOME].
     */
    fun init(context: Context) {
        refreshLaunchers(context)
        isInitialized = true
    }

    /**
     * Re-queries PackageManager for installed launcher activities.
     */
    fun refreshLaunchers(context: Context) {
        try {
            val packageManager = context.packageManager
            val homeIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }

            val resolveList: List<ResolveInfo> =
                packageManager.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )

            val packages = resolveList.mapNotNull { it.activityInfo?.packageName }.toSet()
            _launcherPackages.value = packages
            AppLog.d(TAG, "Resolved ${packages.size} canonical launcher packages via Intent.CATEGORY_HOME: $packages")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to resolve launcher packages via Intent.CATEGORY_HOME: ${e.message}", e)
        }
    }

    /**
     * Deterministically checks if a given package name is a verified home launcher,
     * task switcher, or core system UI component.
     */
    fun isLauncherOrSystemUi(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val pkg = packageName.trim()

        if (pkg == SYSTEM_UI_PACKAGE || pkg == ANDROID_FRAMEWORK_PACKAGE) {
            return true
        }

        if (pkg.startsWith(MegingiardIpcContract.GAMEFOCUS_PACKAGE)) {
            return true
        }

        return _launcherPackages.value.contains(pkg)
    }

    /**
     * Injects a fixed set of launcher package names for pure JVM unit testing without Context.
     */
    fun setLaunchersForTesting(packages: Set<String>) {
        _launcherPackages.value = packages
    }

    /**
     * Resets the classifier state for testing teardown.
     */
    fun resetForTesting() {
        _launcherPackages.value = emptySet()
        isInitialized = false
    }
}
