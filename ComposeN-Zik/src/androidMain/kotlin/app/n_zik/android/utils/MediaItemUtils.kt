package app.n_zik.android.utils

import android.os.Bundle
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import app.n_zik.android.core.database.ArtistMappingReconcile
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.trackBrowseIdOrigin
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.models.Album
import app.n_zik.android.models.Info
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.R
import app.n_zik.android.cleanPrefix
import app.n_zik.android.components.song.isArtistIdForSong
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.playback.services.isLocal
import kotlinx.coroutines.flow.map

// ─── Composable fallbacks (for UI) ───

@Composable
fun MediaItem.artistTextWithFallback(): String {
    val artist = cleanPrefix(mediaMetadata.artist?.toString() ?: "")
    if (artist.isNotBlank() && artist != "null") return artist
    val dbSong by remember(mediaId) {
        Database.songTable.findById(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    val dbText = dbSong?.artistsText
    if (!dbText.isNullOrBlank() && dbText != "null") return cleanPrefix(dbText)
    return stringResource(R.string.metadata_unknown_artist)
}

@Composable
fun MediaItem.titleWithFallback(): String {
    displayTrackTitle(mediaMetadata.title?.toString())?.let { return it }
    val dbSong by remember(mediaId) {
        Database.songTable.findById(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    displayTrackTitle(dbSong?.title)?.let { return it }
    return stringResource(R.string.metadata_unknown_title)
}

internal fun displayTrackTitle(title: String?): String? =
    title?.let { cleanPrefix(cleanPrefix(it)) }?.takeIf { it.isNotBlank() && it != "null" }

internal fun playerTrackName(value: String?, placeholder: String, englishPlaceholder: String): String? =
    displayTrackTitle(value)?.trim()?.takeUnless {
        it.isBlank() || it.equals("null", ignoreCase = true) ||
            it.equals(placeholder, ignoreCase = true) || it.equals(englishPlaceholder, ignoreCase = true)
    }

internal fun resolvePlayerTrackName(
    embedded: String?,
    stored: String?,
    placeholder: String,
    englishPlaceholder: String,
): String? {
    val embeddedName = playerTrackName(embedded, placeholder, englishPlaceholder)
    val storedName = playerTrackName(stored, placeholder, englishPlaceholder)
    return when {
        storedName != null && stored?.startsWith(MODIFIED_PREFIX) == true -> storedName
        else -> embeddedName ?: storedName
    }
}

internal fun isGenericReleaseChannel(name: String?): Boolean =
    name?.trim()?.equals("Release - Topic", ignoreCase = true) == true

internal fun usableTrackArtistName(credit: String?, placeholder: String = "Unknown Artist"): String? =
    playerTrackName(credit, placeholder, "Unknown Artist")?.takeUnless {
        credit?.startsWith(MODIFIED_PREFIX, ignoreCase = true) != true && isGenericReleaseChannel(it)
    }

internal fun resolveTrackArtistName(
    embedded: String?,
    stored: String?,
    linkedArtists: List<Info>,
    placeholder: String,
): String? {
    val storedName = usableTrackArtistName(stored, placeholder)
    if (storedName != null && stored?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true) return storedName
    val embeddedName = usableTrackArtistName(embedded, placeholder)
    if (embeddedName != null && embedded?.startsWith(MODIFIED_PREFIX, ignoreCase = true) == true) return embeddedName

    val embeddedCount = embeddedName.splitArtistNames().size
    val storedCount = storedName.splitArtistNames().size
    val displayedName = if (storedCount > embeddedCount) storedName else embeddedName ?: storedName
    val orderedLinks = alignInfosToDisplayOrder(linkedArtists, displayedName)
    val linkedNames = orderedLinks.mapNotNull { usableTrackArtistName(it.name, placeholder) }
    // An incomplete mapping cannot replace a richer track byline.
    if (linkedNames.isNotEmpty() && linkedNames.size == orderedLinks.size &&
        linkedNames.size >= maxOf(embeddedCount, storedCount)
    ) {
        return linkedNames.joinToString(", ")
    }
    return displayedName
}

internal const val ARTIST_LINKED_NAMES_EXTRA = "artistLinkedNames"
internal const val DERIVED_PLAYBACK_ALBUM_EXTRA = "derivedPlaybackAlbum"

internal data class MediaItemArtistLinks(val entries: List<Info>, val complete: Boolean)

internal fun readMediaItemArtistLinks(
    artistIds: List<String>,
    displayNames: List<String>,
    linkedNames: List<String>?,
): MediaItemArtistLinks {
    val names = (linkedNames ?: displayNames).takeIf { it.size == artistIds.size }
    val entries = artistIds.mapIndexedNotNull { index, id ->
        id.takeIf(String::isNotBlank)?.let {
            Info(it, names?.get(index)?.takeIf(String::isNotBlank))
        }
    }
    val complete = names != null && names == displayNames && entries.isNotEmpty() &&
        entries.size == artistIds.size && entries.all { !it.name.isNullOrBlank() && !isDeviceArtistId(it.id) }
    return MediaItemArtistLinks(entries, complete)
}

@Composable
fun MediaItem.artistIdsWithFallback(initialArtists: List<Info> = emptyList()): List<Info> {
    val preferences = LocalContext.current.preferences
    val songId = mediaId.substringAfterLast('/')
    val dbArtists by remember(mediaId) {
        Database.artistTable.findBySongId(songId)
            .map { artists -> artists.map { Info(it.id, it.name) } }
    }.collectAsStateWithLifecycle(initialValue = initialArtists, context = NzikDispatchers.DATA)
    return alignInfosToDisplayOrder(
        artistLinksWithStoredFallback(dbArtists) { preferences.trackBrowseIdOrigin(songId, it) },
        artistTextWithFallback(),
    )
}

internal fun MediaItem.artistLinksWithStoredFallback(
    storedArtists: List<Info>,
    originalId: (String) -> String?,
): List<Info> = artistLinksWithStoredFallback(storedArtists, { it == null }, originalId)

private fun MediaItem.artistLinksWithStoredFallback(
    storedArtists: List<Info>,
    isMissingName: (String?) -> Boolean,
    originalId: (String) -> String?,
): List<Info> {
    val extras = mediaMetadata.extras
    val credits = readMediaItemArtistLinks(
        extras?.getStringArrayList("artistIds").orEmpty(),
        extras?.getStringArrayList("artistNames").orEmpty(),
        extras?.getStringArrayList(ARTIST_LINKED_NAMES_EXTRA),
    )
    val metadataLinks = credits.entries.filterNot { isDeviceArtistId(it.id) }
    val infos = if (!isLocal && storedArtists.isNotEmpty()) {
        val resolvedLinks = metadataLinks.mapNotNull { link ->
            val edited = storedArtists.singleOrNull { stored ->
                ArtistMappingReconcile.isOverrideOf(stored.id, link.id) { originalId(stored.id) }
            }
            val stored = storedArtists.firstOrNull { it.id == link.id }
            when {
                edited != null -> edited
                stored != null && (isMissingName(stored.name) || usableTrackArtistName(stored.name) == null) -> link
                else -> stored
            }
        }
        (resolvedLinks + storedArtists).distinctBy(Info::id)
    } else if (!isLocal) {
        metadataLinks
    } else {
        storedArtists
    }
    return infos.filter { isDeviceArtistId(it.id) == isLocal && !isGenericReleaseChannel(it.name) }
}

/**
 * Reorders [infos] so they follow the artist order of [displayText] — the artist line the
 * player actually displays — instead of the arbitrary order the link query (or the extras
 * built from it) happens to return.
 *
 * Rules (doubt → nothing moves): [displayText] is tokenized with the [cleanPrefix]-then-split-
 * on-"," convention (same as the rest of the name-convergence code), each token trimmed;
 * every token picks the FIRST still-unmatched info whose cleaned name equals it (whole token,
 * ignore case) — names are matched, ids are never guessed; infos no token claimed are appended
 * at the end, keeping their relative order; when not a single token matches (unknown artist
 * text, all names null) the original order is returned untouched.
 *
 * Pure and non-suspend: unit-testable, no DB access.
 *
 * @param infos the artists of the current media item, in link/extra order
 * @param displayText the artist line displayed in the player (already cleaned)
 * @return the infos ordered like the displayed artist line
 */
internal fun alignInfosToDisplayOrder(infos: List<Info>, displayText: String?): List<Info> {
    if (infos.size < 2 || displayText.isNullOrBlank()) return infos
    val tokens = cleanPrefix(displayText).split(",")
        .map { cleanPrefix(it.trim()).trim() }
        .filter { it.isNotBlank() }
    if (tokens.isEmpty()) return infos
    val remaining = infos.toMutableList()
    val ordered = ArrayList<Info>(infos.size)
    for (token in tokens) {
        val index = remaining.indexOfFirst { info ->
            info.name?.let { cleanPrefix(it).trim() }?.equals(token, ignoreCase = true) == true
        }
        if (index >= 0) ordered.add(remaining.removeAt(index))
    }
    if (ordered.isEmpty()) return infos // nothing matched: doubt → nothing moves
    ordered.addAll(remaining)
    return ordered
}

internal fun MediaItem.withPlaybackAlbum(albumId: String, albumTitle: String?, derived: Boolean = false): MediaItem {
    val metadata = mediaMetadata
    val extras = (metadata.extras?.let(::Bundle) ?: Bundle()).apply {
        putString("albumId", albumId)
        putBoolean(DERIVED_PLAYBACK_ALBUM_EXTRA, derived)
    }
    val albumMetadata = metadata.buildUpon()
        .setAlbumTitle(albumTitle?.takeIf(String::isNotBlank) ?: metadata.albumTitle)
        .setExtras(extras)
        .build()
    return buildUpon().setMediaMetadata(albumMetadata).build()
}

internal fun selectOnlinePlaybackAlbumId(
    explicitId: String?,
    exactTrackAlbumId: String?,
    storedAlbumIds: List<String>,
): String? {
    explicitId?.takeIf(::isOnlinePlaybackAlbumId)?.let { return it }
    val stored = storedAlbumIds.filter(::isOnlinePlaybackAlbumId).distinct()
    return stored.singleOrNull(::isUserEditedPlaybackAlbumId)
        ?: exactTrackAlbumId?.takeIf(::isOnlinePlaybackAlbumId)
        ?: stored.singleOrNull()
}

internal fun isUserEditedPlaybackAlbumId(id: String): Boolean =
    id.startsWith(MODIFIED_PREFIX) || id.startsWith("LOCAL_ALBUM_")

private fun isOnlinePlaybackAlbumId(id: String): Boolean = id.isNotBlank() && !isDeviceAlbumId(id)

internal fun editedPlaybackAlbumFor(
    explicitId: String,
    albums: List<Album>,
    originalId: (String) -> String?,
): Album? {
    if (!isOnlinePlaybackAlbumId(explicitId) || isUserEditedPlaybackAlbumId(explicitId)) return null
    return albums.singleOrNull { album ->
        !isDeviceAlbumId(album.id) && isUserEditedPlaybackAlbumId(album.id) &&
            (album.id == "$MODIFIED_PREFIX$explicitId" || originalId(album.id) == explicitId)
    }
}

@Composable
fun MediaItem.albumIdWithFallback(initialAlbumId: String? = null): String? {
    val preferences = LocalContext.current.preferences
    val songId = mediaId.substringAfterLast('/')
    val storedAlbums by remember(mediaId, preferences) {
        Database.songAlbumMapTable.findAlbumsOf(songId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    return playbackAlbumId(storedAlbums, initialAlbumId) { preferences.trackBrowseIdOrigin(songId, it) }
}

internal fun MediaItem.playbackAlbumId(
    storedAlbums: List<Album>?,
    initialAlbumId: String?,
    originalId: (String) -> String?,
): String? {
    val explicitId = mediaMetadata.extras?.getString("albumId")?.takeIf(::isOnlinePlaybackAlbumId)
    val albums = storedAlbums ?: return explicitId.takeUnless { isLocal } ?: initialAlbumId
    if (isLocal) return albums.firstOrNull { isDeviceAlbumId(it.id) }?.id

    if (mediaMetadata.extras?.getBoolean(DERIVED_PLAYBACK_ALBUM_EXTRA) == true) {
        return selectOnlinePlaybackAlbumId(null, null, albums.map(Album::id)) ?: explicitId
    }
    val edited = explicitId?.let { id ->
        editedPlaybackAlbumFor(id, albums, originalId)
    }
    return edited?.id ?: selectOnlinePlaybackAlbumId(explicitId, null, albums.map(Album::id))
}

internal data class PlayerTrackLinks(
    val albumId: String?,
    val artists: List<Info>,
    val albumPending: Boolean = false,
    val artistsPending: Boolean = false,
)

/** Re-use known links for this track until the current metadata or database read replaces them. */
@Composable
internal fun MediaItem.playerTrackLinks(cache: LruCache<String, PlayerTrackLinks>): PlayerTrackLinks = key(mediaId, cache) {
    val lastKnown = remember { cache.get(mediaId) }
    val preferences = LocalContext.current.preferences
    val songId = mediaId.substringAfterLast('/')
    val storedAlbums by remember(songId) {
        Database.songAlbumMapTable.findAlbumsOf(songId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    val storedArtists = rememberPlayerStoredArtists(songId)
    val originalId: (String) -> String? = { preferences.trackBrowseIdOrigin(songId, it) }
    val unknownArtist = stringResource(R.string.metadata_unknown_artist)
    val albumId = playbackAlbumId(storedAlbums, lastKnown?.albumId, originalId)
    val artists = artistLinksWithStoredFallback(
        storedArtists ?: lastKnown?.artists.orEmpty(),
        { playerTrackName(it, unknownArtist, "Unknown Artist") == null },
        originalId,
    ).filter { isArtistIdForSong(it.id, isLocal) }
    val links = PlayerTrackLinks(
        albumId = albumId,
        artists = artists,
        albumPending = storedAlbums == null && albumId == null,
        artistsPending = storedArtists == null && artists.isEmpty(),
    )
    SideEffect {
        if (links.albumId == null && links.artists.isEmpty()) cache.remove(mediaId)
        else cache.put(mediaId, links)
    }
    links
}

@Composable
internal fun rememberPlayerStoredArtists(songId: String): List<Info>? = key(songId) {
    remember {
        Database.artistTable.findBySongId(songId).map { artists -> artists.map { Info(it.id, it.name) } }
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA).value
}

/** Pure album-title resolution shared by the Composable preview fallback below: metadata first, then the DB record; fork prefixes ("e:", "modified:", "pinned:", the "🅴 " marker, ...) are stripped from both sources (same contract as [artistTextOrDb] / [titleOrDb]); a blank or literal "null" is treated as missing, and the result `null` means "unknown" (the caller applies its own localized fallback). */
fun resolveAlbumTitle(metadataAlbum: String?, dbAlbum: Album?): String? {
    val meta = cleanPrefix(metadataAlbum ?: "")
    if (meta.isNotBlank() && meta != "null") return meta
    val db = dbAlbum?.title?.let { cleanPrefix(it) }.orEmpty()
    return db.takeIf { it.isNotBlank() && it != "null" }
}

/**
 * Composable variant of [albumTitleOrDb] for UI previews: metadata first, then the DB as
 * a live Flow, so the preview re-renders once the DB learns the album (a streaming
 * MediaItem usually carries no albumTitle; the one-shot [albumTitleOrDb] used by the
 * presence re-resolves on its ~5 s refresh tick instead). The DB source mirrors the
 * player screen: the albumId extra (direct Album row) when the MediaItem carries one,
 * otherwise the song→album mapping created at playback start. Null = unknown.
 */
@Composable
fun MediaItem.albumTitleWithFallback(): String? {
    resolveAlbumTitle(mediaMetadata.albumTitle?.toString(), null)?.let { return it }
    val albumId = mediaMetadata.extras?.getString("albumId")?.takeIf { it.isNotBlank() }
    val dbAlbum by remember(albumId, mediaId) {
        if (albumId != null) Database.albumTable.findById(albumId)
        else Database.albumTable.findBySongId(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    return resolveAlbumTitle(null, dbAlbum)
}

// ─── Non-Composable fallbacks (for services, notifications, etc.) ───

suspend fun MediaItem.artistTextOrDb(): String {
    val artist = cleanPrefix(mediaMetadata.artist?.toString() ?: "")
    if (artist.isNotBlank() && artist != "null") return artist
    val dbText = Database.songTable.findByIdDirect(mediaId)?.artistsText
    if (!dbText.isNullOrBlank() && dbText != "null") return cleanPrefix(dbText)
    return artist
}

suspend fun MediaItem.albumTitleOrDb(): String {
    val album = cleanPrefix(mediaMetadata.albumTitle?.toString() ?: "")
    if (album.isNotBlank() && album != "null") return album
    // Same sources as the player screen: the albumId extra (direct album row) when the
    // MediaItem carries one, otherwise the song→album mapping created at playback start.
    val albumId = mediaMetadata.extras?.getString("albumId")?.takeIf { it.isNotBlank() }
    val dbAlbum = if (albumId != null) Database.albumTable.findByIdDirect(albumId)
        else Database.albumTable.findBySongIdDirect(mediaId)
    val dbTitle = dbAlbum?.title?.let { cleanPrefix(it) }
        ?.takeIf { it.isNotBlank() && it != "null" }
    return dbTitle ?: album
}

suspend fun MediaItem.titleOrDb(): String {
    val title = cleanPrefix(mediaMetadata.title?.toString() ?: "")
    if (title.isNotBlank() && title != "null") return title
    return Database.songTable.findByIdDirect(mediaId)?.title?.let { cleanPrefix(it) } ?: title
}
