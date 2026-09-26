package app.n_zik.android.utils

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import app.n_zik.android.core.database.Database
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Info
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.cleanPrefix

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
    return stringResource(R.string.unknown_artist)
}

@Composable
fun MediaItem.titleWithFallback(): String {
    val title = mediaMetadata.title?.toString() ?: ""
    if (title.isNotBlank() && title != "null") return title
    val dbSong by remember(mediaId) {
        Database.songTable.findById(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    val dbTitle = dbSong?.title
    if (!dbTitle.isNullOrBlank() && dbTitle != "null") return dbTitle
    return stringResource(R.string.unknown_title)
}

@Composable
fun MediaItem.artistIdsWithFallback(): List<Info> {
    val ids = mediaMetadata.extras?.getStringArrayList("artistIds")
    val infos = if (!ids.isNullOrEmpty()) {
        val names = mediaMetadata.extras?.getStringArrayList("artistNames")
        ids.mapIndexed { i, id -> Info(id, names?.getOrNull(i)) }
    } else {
        val dbArtists by remember(mediaId) {
            Database.artistTable.findBySongId(mediaId)
        }.collectAsStateWithLifecycle(initialValue = emptyList(), context = NzikDispatchers.DATA)
        dbArtists.map { Info(it.id, it.name) }
    }
    // The links (and the extras built from them) carry no display order: SQLite
    // returns them in an arbitrary order, so the profile-photo pager used to
    // disagree with the artist text shown in the player. Follow the displayed
    // text order instead (see alignInfosToDisplayOrder for the doubt rule).
    return alignInfosToDisplayOrder(infos, artistTextWithFallback())
}

/**
 * Reorders [infos] so they follow the artist order of [displayText] — the artist
 * line the player actually displays — instead of the arbitrary order the link
 * query (or the extras built from it) happens to return.
 *
 * Rules (doubt → nothing moves):
 * - [displayText] is tokenized with the [cleanPrefix]-then-split-on-"," convention
 *   (same as the rest of the name-convergence code), each token trimmed;
 * - every token picks the FIRST still-unmatched info whose cleaned name equals it
 *   (whole token, ignore case) — names are matched, ids are never guessed;
 * - infos no token claimed are appended at the end, keeping their relative order;
 * - when not a single token matches (unknown artist text, all names null) the
 *   original order is returned untouched.
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

@Composable
fun MediaItem.albumIdWithFallback(): String? {
    val albumId = mediaMetadata.extras?.getString("albumId")
    if (!albumId.isNullOrBlank()) return albumId
    val dbAlbum by remember(mediaId) {
        Database.albumTable.findBySongId(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    return dbAlbum?.id
}

/**
 * Pure album-title resolution shared by the Composable preview fallback below: metadata
 * first, then the DB record; fork prefixes ("e:", "modified:", "pinned:", the "🅴 "
 * marker, ...) are stripped from both sources (same contract as [artistTextOrDb] /
 * [titleOrDb]); a blank or literal "null" is treated as missing, and the result `null`
 * means "unknown" (the caller applies its own localized fallback).
 */
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

fun MediaItem.artistTextOrDb(): String {
    val artist = cleanPrefix(mediaMetadata.artist?.toString() ?: "")
    if (artist.isNotBlank() && artist != "null") return artist
    val dbText = Database.songTable.findByIdDirect(mediaId)?.artistsText
    if (!dbText.isNullOrBlank() && dbText != "null") return cleanPrefix(dbText)
    return artist
}

fun MediaItem.albumTitleOrDb(): String {
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

fun MediaItem.titleOrDb(): String {
    val title = cleanPrefix(mediaMetadata.title?.toString() ?: "")
    if (title.isNotBlank() && title != "null") return title
    return Database.songTable.findByIdDirect(mediaId)?.title?.let { cleanPrefix(it) } ?: title
}
