package com.stormpanda.megingiard.device

import com.stormpanda.megingiard.AppLog

private const val TAG = "KnownDeviceProfiles"

/**
 * Declarative hardware/vendor metadata for verified dual-screen Android handhelds.
 *
 * Runtime display geometry, OS reflection paths, and kernel evdev capabilities are
 * always queried dynamically from Android and Linux APIs so unverified dual-screen
 * handhelds work out-of-the-box. This registry holds only non-discoverable vendor
 * facts (such as transient vendor system-overlay package prefixes) and exact model
 * identifiers for diagnostic reporting.
 */
data class DualScreenDeviceProfile(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val models: Set<String>,
    val vendorOverlayPrefixes: List<String>,
)

object KnownDeviceProfiles {
    val AYN_THOR =
        DualScreenDeviceProfile(
            id = "ayn_thor",
            displayName = "AYN Thor",
            manufacturer = "AYN",
            models = setOf("AYN Thor", "AYN Thor 2"),
            vendorOverlayPrefixes = listOf("com.odin."),
        )

    val RETROID_POCKET_DUO =
        DualScreenDeviceProfile(
            id = "retroid_pocket_duo",
            displayName = "Retroid Pocket Duo",
            manufacturer = "Retroid",
            models = setOf("Retroid Pocket Duo", "Retroid Pocket Duo Lite"),
            // Note: com.retroidpocket.gamelauncher is an intentional CATEGORY_HOME launcher
            // handled by SystemRoleClassifier and must NOT be treated as a transient overlay.
            vendorOverlayPrefixes =
                listOf(
                    "com.retroidpocket.dualscreen.",
                    "com.rp.",
                ),
        )

    val ALL: List<DualScreenDeviceProfile> =
        listOf(
            AYN_THOR,
            RETROID_POCKET_DUO,
        )

    /**
     * Canonical prefix list of vendor background/overlay packages that emit window state
     * changes without representing user-launched foreground applications.
     */
    val VENDOR_OVERLAY_PACKAGE_PREFIXES: List<String> =
        ALL.flatMap { it.vendorOverlayPrefixes }.distinct()

    /**
     * Returns `true` if [packageName] belongs to a known vendor system-overlay or hardware
     * service package that should be ignored during foreground app / session tracking.
     */
    fun isVendorOverlayPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return VENDOR_OVERLAY_PACKAGE_PREFIXES.any { prefix -> packageName.startsWith(prefix) }
    }

    /**
     * Resolves a verified [DualScreenDeviceProfile] by exact case-insensitive [model] match,
     * returning `null` for generic or not-yet-cataloged dual-screen devices.
     */
    fun resolveByModel(model: String?): DualScreenDeviceProfile? {
        val trimmed = model?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val matched =
            ALL.firstOrNull { profile ->
                profile.models.any { candidate -> candidate.equals(trimmed, ignoreCase = true) }
            }
        AppLog.d(TAG, "resolveByModel('$trimmed') -> ${matched?.id ?: "generic"}")
        return matched
    }
}
