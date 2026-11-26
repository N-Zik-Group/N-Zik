package app.n_zik.android.core.database

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.utils.isGenericReleaseChannel
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.ArtistConjunctions
import app.n_zik.innertube.models.NavigationEndpoint

/**
 * Decision helper for `SongArtistMap` reconciliation.
 *
 * YTM returns different author lists for the same song depending on the playback
 * context (a channel playlist or radio credits the channel as an author). The
 * mapping must reflect the latest authoritative list, not the union of every
 * list ever seen — insert-only accumulation let a channel-derived artist row
 * collect plays of hundreds of unrelated songs (Rewind top-artist bug).
 *
 * A list may only *replace* existing rows when it is complete, i.e. every
 * parsed artist name is backed by an author entry carrying a browse ID. A
 * names-only or partial list must never erase mappings it cannot rewrite.
 */
object ArtistMappingReconcile {

    /** A changed or unlinked identity replaces its original credit for this song. */
    internal fun isOverrideOf(editedId: String, incomingId: String, originalId: () -> String?): Boolean =
        editedId == "$MODIFIED_PREFIX$incomingId" ||
            ((editedId.startsWith(MODIFIED_PREFIX) || editedId.startsWith("LOCAL_ARTIST_")) && originalId() == incomingId)

    /** Matches entries that are nothing but separators ("&", ",", "&&", …). */
    private val SEPARATOR_ONLY_REGEX = Regex("^[,&]+$")

    /**
     * Parses a YTM author list into (cleaned name, browseId) pairs, one per entry.
     *
     * Shared by [app.n_zik.android.core.database.Database.upsert] and the song update
     * dialog so the entry rules never diverge: names are trimmed and NBSP-normalized,
     * pure-separator entries and standalone conjunction entries are skipped (one
     * author entry = one artist - the entry name is kept whole, never split).
     *
     * @param authors raw author entries from a YTM response (may be null).
     * @return the surviving entries as name-to-browseId pairs (browseId is null when
     * the entry carries no browse endpoint).
     */
    fun parseAuthorEntries(
        authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?,
        includeIdOnly: Boolean = false,
    ): List<Pair<String, String?>> {
        return authors.orEmpty().mapNotNull { author ->
            val name = author.name?.trim()?.replace('\u00a0', ' ')
            if (name.isNullOrBlank()) {
                val id = author.endpoint?.browseId?.takeIf(String::isNotBlank)
                return@mapNotNull if (includeIdOnly && id != null) "" to id else null
            }
            if (name.matches(SEPARATOR_ONLY_REGEX) || isGenericReleaseChannel(name)) return@mapNotNull null
            if (ArtistConjunctions.conjunctions.any { name.equals(it, ignoreCase = true) }) return@mapNotNull null
            name to author.endpoint?.browseId
        }
    }

    /**
     * @param parsedNames artist names parsed from the fresh YTM author list.
     * @param authors raw author entries from the fresh YTM response.
     * @return true when the list is non-empty and complete (safe to replace the
     * existing mapping with it).
     */
    fun isCompleteAuthorList(
        parsedNames: List<String>,
        authors: List<Innertube.Info<NavigationEndpoint.Endpoint.Browse>>?,
    ): Boolean {
        val entries = parseAuthorEntries(authors, includeIdOnly = true)
        return parsedNames.isNotEmpty() && entries.map { it.first } == parsedNames &&
            entries.all { it.first.isNotBlank() && !it.second.isNullOrBlank() }
    }
}
