package app.n_zik.android.components.playlist

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.components.menu.playlist.loadOnlinePlaylistContent
import app.n_zik.android.components.menu.playlist.withSourcePlaylistOwner
import app.n_zik.android.components.navigation.onlinePlaylistBrowseIdFromLink
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.rewind.RewindPlaylists.portablePlaylistName
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playlist.importSongsToPlaylist
import app.n_zik.android.utils.playlist.saveImportedPlaylistSort
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Saves a copy of a complete collection already loaded by the detail screen. */
internal fun launchLoadedPlaylistImport(
    context: Context,
    playlist: Playlist,
    songs: List<Song>?,
) {
    val app = context.applicationContext
    val owner = ProfileWorkOwner.capture(app) ?: return
    if (songs == null) {
        Toaster.e(R.string.playlist_import_failed)
        return
    }
    if (songs.isEmpty()) {
        Toaster.w(R.string.player_no_song_found)
        return
    }

    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
        try {
            val inserted = Database.awaitWriteBatch {
                if (ProfileWorkOwner.isBound(app, owner)) mapMissingPlaylistSongs(playlist, songs) else 0
            }
            if (inserted > 0 && ProfileWorkOwner.isBound(app, owner)) Toaster.done()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistLinkImport").e(error, "Could not save the loaded collection")
            if (ProfileWorkOwner.isBound(app, owner)) Toaster.e(R.string.playlist_import_failed)
        }
    }
}

/** Imports only complete collections, into the profile that confirmed the link. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun launchPlaylistLinkImport(
    context: Context,
    scope: CoroutineScope,
    playlistId: String,
    targetPlaylistId: Long? = null,
    onImported: () -> Unit = {},
) {
    val app = context.applicationContext
    val owner = ProfileWorkOwner.capture(app) ?: return
    val browseId = onlinePlaylistBrowseIdFromLink(playlistId)
    scope.launch(NzikDispatchers.DATA) {
        try {
            val source = loadOnlinePlaylistContent(browseId, fetchAllowed = { ProfileWorkOwner.isBound(app, owner) })
            if (!ProfileWorkOwner.isBound(app, owner)) return@launch
            if (source?.songs == null) {
                Toaster.e(R.string.playlist_import_failed)
                return@launch
            }
            if (source.songs.isEmpty()) {
                Toaster.w(R.string.player_no_song_found)
                return@launch
            }
            val songs = source.songs.map { it.copy(totalPlayTimeMs = 1L) }
            val importedId = Database.awaitWriteBatch {
                var committedId: Long? = null
                transaction {
                    if (!ProfileWorkOwner.isBound(app, owner)) return@transaction
                    val target = if (targetPlaylistId == null) {
                        val playlist = Playlist(
                            name = app.portablePlaylistName(source.title ?: app.getString(R.string.playlist_youtube_title)),
                            browseId = browseId,
                        ).withSourcePlaylistOwner(source.channel)
                        playlist.copy(id = playlistTable.insert(playlist))
                    } else {
                        checkNotNull(playlistTable.findById(targetPlaylistId).first()) {
                            "Import target playlist no longer exists"
                        }
                    }
                    importSongsToPlaylist(target, songs)
                    committedId = target.id
                }
                committedId
            }
            if (importedId != null) withContext(NzikDispatchers.UI) {
                if (ProfileWorkOwner.isBound(app, owner)) {
                    if (targetPlaylistId == null) app.preferences.saveImportedPlaylistSort(listOf(importedId))
                    onImported()
                    Toaster.done()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("PlaylistLinkImport").e(error, "Could not import the complete collection")
            if (ProfileWorkOwner.isBound(app, owner)) Toaster.e(R.string.playlist_import_failed)
        }
    }
}
