package app.n_zik.android.components.menu.song

import androidx.compose.runtime.Composable
import androidx.media3.common.MediaItem
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.song.artistMenuIdentity
import app.n_zik.android.components.song.isEditedArtistId
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Info
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.ARTIST_LINKED_NAMES_EXTRA
import app.n_zik.android.utils.alignInfosToDisplayOrder
import app.n_zik.android.utils.artistLinksWithStoredFallback
import app.n_zik.android.utils.readMediaItemArtistLinks

@Composable
internal fun rememberTrackMenuArtists(
    source: MediaItem?,
    song: Song,
    storedArtists: List<Artist>,
): List<Info> {
    if (song.isLocal) return deviceTrackMenuArtists(storedArtists)
    val stored = storedArtists.map { Info(it.id, it.name) }
    val previousId = { id: String -> trackBrowseIdOrigin(song.id, id) }
    val links = (source?.artistLinksWithStoredFallback(stored, previousId) ?: stored)
        .groupBy { artistMenuIdentity(it.id, previousId) }
        .values.map { equivalent -> equivalent.firstOrNull { isEditedArtistId(it.id) } ?: equivalent.first() }
    if (links.isEmpty()) return links

    val extras = source?.mediaMetadata?.extras ?: return links
    val displayNames = extras.getStringArrayList("artistNames").orEmpty()
    val sourceLinks = readMediaItemArtistLinks(
        extras.getStringArrayList("artistIds").orEmpty(),
        displayNames,
        extras.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA),
    ).entries
    return alignInfosToDisplayOrder(
        mergeTrackMenuArtistCredits(links, sourceLinks, displayNames),
        song.cleanArtistsText(),
    )
}

internal fun deviceTrackMenuArtists(storedArtists: List<Artist>): List<Info> =
    storedArtists.filter { isDeviceArtistId(it.id) }.distinctBy(Artist::id).map { Info(it.id, it.name) }

internal fun mergeTrackMenuArtistCredits(
    resolvedLinks: List<Info>,
    sourceLinks: List<Info>,
    sourceNames: List<String>,
): List<Info> {
    val linkedNames = (resolvedLinks + sourceLinks).mapNotNull(Info::name)
    val unlinkedCredits = sourceNames.filterNot { name ->
        linkedNames.any { cleanPrefix(it).trim().equals(cleanPrefix(name).trim(), ignoreCase = true) }
    }
    return resolvedLinks + unlinkedCredits.map { Info("", it) }
}
