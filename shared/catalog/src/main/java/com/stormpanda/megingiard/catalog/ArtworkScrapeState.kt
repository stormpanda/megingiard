package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.Serializable

private const val TAG = "ArtworkScrapeState"

/** Artwork categories that are scraped independently from SteamGridDB. */
enum class ArtworkKind {
    COVER,
    LOGO,
    LIBRARY_ICON,
}

/**
 * Decides whether background scraping may fetch an artwork kind for an item: only when the user has not
 * opted out, no completed attempt is recorded, and no valid file exists on disk.
 */
fun shouldAutoScrape(
    isOptedOut: Boolean,
    wasAttempted: Boolean,
    hasFile: Boolean,
): Boolean = !isOptedOut && !wasAttempted && !hasFile

/** Persisted user opt-outs from automatic artwork (`gamefocus_artwork_optouts.json`). Logos cannot be opted out. */
@Serializable
internal data class ArtworkOptOuts(
    val cover: Set<String> = emptySet(),
    val libraryIcon: Set<String> = emptySet(),
) {
    fun isOptedOut(
        kind: ArtworkKind,
        packageName: String,
    ): Boolean =
        when (kind) {
            ArtworkKind.COVER -> cover.contains(packageName)
            ArtworkKind.LIBRARY_ICON -> libraryIcon.contains(packageName)
            ArtworkKind.LOGO -> false
        }

    /** Returns a copy with [packageName] added to / removed from the opt-out set of [kind]. */
    fun withOptOut(
        kind: ArtworkKind,
        packageName: String,
        optedOut: Boolean,
    ): ArtworkOptOuts {
        fun Set<String>.toggled(): Set<String> = if (optedOut) this + packageName else this - packageName
        return when (kind) {
            ArtworkKind.COVER -> {
                copy(cover = cover.toggled())
            }

            ArtworkKind.LIBRARY_ICON -> {
                copy(libraryIcon = libraryIcon.toggled())
            }

            ArtworkKind.LOGO -> {
                AppLog.w(TAG, "Ignoring opt-out change for $packageName: logos cannot be opted out")
                this
            }
        }
    }
}

/** Persisted completed scrape attempts per artwork kind (`gamefocus_scrape_attempts.json`). */
@Serializable
internal data class ScrapeAttempts(
    val cover: Set<String> = emptySet(),
    val logo: Set<String> = emptySet(),
    val libraryIcon: Set<String> = emptySet(),
) {
    fun wasAttempted(
        kind: ArtworkKind,
        packageName: String,
    ): Boolean = setFor(kind).contains(packageName)

    /** Returns a copy with [packageName] recorded for [kind], or `this` if it was already recorded. */
    fun withAttempt(
        kind: ArtworkKind,
        packageName: String,
    ): ScrapeAttempts {
        if (wasAttempted(kind, packageName)) return this
        return when (kind) {
            ArtworkKind.COVER -> copy(cover = cover + packageName)
            ArtworkKind.LOGO -> copy(logo = logo + packageName)
            ArtworkKind.LIBRARY_ICON -> copy(libraryIcon = libraryIcon + packageName)
        }
    }

    private fun setFor(kind: ArtworkKind): Set<String> =
        when (kind) {
            ArtworkKind.COVER -> cover
            ArtworkKind.LOGO -> logo
            ArtworkKind.LIBRARY_ICON -> libraryIcon
        }

    companion object {
        /**
         * Migrates the legacy `gamefocus_scraped_apps.txt` registry: every legacy package is treated as attempted
         * for all kinds, so existing apps are not backfilled with new artwork kinds automatically.
         */
        fun fromLegacy(legacyPackages: Set<String>): ScrapeAttempts {
            AppLog.i(TAG, "Migrating ${legacyPackages.size} legacy scraped packages to per-kind scrape attempts")
            return ScrapeAttempts(cover = legacyPackages, logo = legacyPackages, libraryIcon = legacyPackages)
        }
    }
}
