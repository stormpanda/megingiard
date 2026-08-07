package com.stormpanda.megingiard.update

/**
 * Utility functions for semantic version comparison to determine whether an update is available.
 */
object SemVerComparator {
    /**
     * Determines whether [latestTag] represents a newer version than [currentVersion].
     *
     * Handles leading 'v'/'V', '-SNAPSHOT' pre-release suffixes, and numerical version components.
     * For example:
     * - `isUpdateAvailable("0.8.0-SNAPSHOT", "v0.8.0")` -> `true`
     * - `isUpdateAvailable("0.8.0", "v0.8.0")` -> `false`
     * - `isUpdateAvailable("0.8.0-SNAPSHOT", "v0.8.1")` -> `true`
     * - `isUpdateAvailable("0.8.1", "v0.8.0")` -> `false`
     *
     * @param currentVersion Current app version name (e.g. "0.8.0-SNAPSHOT").
     * @param latestTag Release tag from GitHub (e.g. "v0.8.1").
     * @return `true` if [latestTag] is strictly newer than [currentVersion].
     */
    fun isUpdateAvailable(
        currentVersion: String,
        latestTag: String,
    ): Boolean {
        if (currentVersion.isBlank() || latestTag.isBlank()) return false

        val cleanCurrentRaw = currentVersion.trim().removePrefix("v").removePrefix("V")
        val cleanLatestRaw = latestTag.trim().removePrefix("v").removePrefix("V")

        val isCurrentPreRelease = cleanCurrentRaw.contains("-")
        val currentCoreVersion = cleanCurrentRaw.substringBefore("-")
        val latestCoreVersion = cleanLatestRaw.substringBefore("-")

        val currentComponents = parseVersionComponents(currentCoreVersion)
        val latestComponents = parseVersionComponents(latestCoreVersion)

        val maxLen = maxOf(currentComponents.size, latestComponents.size)
        for (i in 0 until maxLen) {
            val curr = currentComponents.getOrElse(i) { 0 }
            val lat = latestComponents.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }

        // Numerical core components are identical (e.g. 0.8.0 vs 0.8.0).
        // If current is a pre-release build and latest is a full release, the release is newer.
        return isCurrentPreRelease && !cleanLatestRaw.contains("-")
    }

    private fun parseVersionComponents(versionString: String): List<Int> =
        versionString
            .split(".")
            .mapNotNull { it.filter { char -> char.isDigit() }.toIntOrNull() }
}
