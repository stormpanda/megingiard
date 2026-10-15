package com.stormpanda.megingiard.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lightweight data model representing GitHub release information returned by the
 * GitHub Releases API (`/repos/stormpanda/megingiard/releases/latest`).
 *
 * @property tagName The release tag name, e.g., "v0.8.1".
 * @property htmlUrl The web browser URL to the release page on GitHub.
 */
@Serializable
data class AppReleaseInfo(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("html_url")
    val htmlUrl: String,
)
