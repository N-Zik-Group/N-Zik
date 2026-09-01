package app.n_zik.android.utils

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.models.Info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
    }.collectAsStateWithLifecycle(initialValue = null)
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
    }.collectAsStateWithLifecycle(initialValue = null)
    val dbTitle = dbSong?.title
    if (!dbTitle.isNullOrBlank() && dbTitle != "null") return dbTitle
    return stringResource(R.string.unknown_title)
}

@Composable
fun MediaItem.artistIdsWithFallback(): List<Info> {
    val ids = mediaMetadata.extras?.getStringArrayList("artistIds")
    if (!ids.isNullOrEmpty()) {
        val names = mediaMetadata.extras?.getStringArrayList("artistNames")
        return ids.mapIndexed { i, id -> Info(id, names?.getOrNull(i)) }
    }
    val dbArtists by remember(mediaId) {
        Database.artistTable.findBySongId(mediaId)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    return dbArtists.map { Info(it.id, it.name) }
}

@Composable
fun MediaItem.albumIdWithFallback(): String? {
    val albumId = mediaMetadata.extras?.getString("albumId")
    if (!albumId.isNullOrBlank()) return albumId
    val dbAlbum by remember(mediaId) {
        Database.albumTable.findBySongId(mediaId)
    }.collectAsStateWithLifecycle(initialValue = null)
    return dbAlbum?.id
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
    val album = mediaMetadata.albumTitle?.toString() ?: ""
    if (album.isNotBlank() && album != "null") return album
    return Database.albumTable.findBySongIdDirect(mediaId)?.title ?: album
}
