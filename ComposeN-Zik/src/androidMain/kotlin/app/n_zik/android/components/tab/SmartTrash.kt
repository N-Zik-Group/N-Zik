package app.n_zik.android.components.tab

import android.content.Context
import androidx.media3.datasource.cache.Cache
import app.n_zik.android.core.database.*
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.LocalPlayerServiceBinder
import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.tab.toolbar.ConfirmDialog
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.asMediaItem
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.appContext

@UnstableApi
class SmartTrash private constructor(
    activeState: MutableState<Boolean>,
    private val binder: PlayerServiceModern.Binder?,
    private val builtInPlaylist: () -> BuiltInPlaylist,
    private val getSongs: () -> List<Song>,
    private val itemsOnDisplay: () -> List<Song>
): MenuIcon, Descriptive, ConfirmDialog {

    companion object {
        @Composable
        operator fun invoke(
            builtInPlaylist: () -> BuiltInPlaylist,
            getSongs: () -> List<Song>,
            itemsOnDisplay: () -> List<Song>
        ) = SmartTrash(
            remember { mutableStateOf(false) },
            LocalPlayerServiceBinder.current,
            builtInPlaylist,
            getSongs,
            itemsOnDisplay
        )
    }

    override val iconId: Int = R.drawable.trash
    override val messageId: Int = R.string.smart_trash
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.smart_trash )

    private val hasSelection get() = getSongs().size != itemsOnDisplay().size
    private val songCount get() = getSongs().size

    override val confirmationTitle: String
        @Composable get() = stringResource(when (builtInPlaylist()) {
            BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked -> R.string.dialog_remove_favorites
            BuiltInPlaylist.Offline -> R.string.dialog_clear_song_cache
            BuiltInPlaylist.Downloaded -> R.string.dialog_clear_downloads
            BuiltInPlaylist.Top -> R.string.dialog_reset_listening_history
            else -> R.string.dialog_delete_songs
        })
    override val confirmationAction: String
        @Composable get() = stringResource(when (builtInPlaylist()) {
            BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked -> R.string.dialog_remove
            BuiltInPlaylist.Offline -> R.string.clear
            BuiltInPlaylist.Top -> R.string.reset
            else -> R.string.delete
        })

    override val dialogTitle: String
        @Composable
        get() {
            val count = songCount
            return when( builtInPlaylist() ) {
                BuiltInPlaylist.All -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_all_delete, count )
                    else
                        stringResource( R.string.smart_trash_all_clear, count )
                }
                BuiltInPlaylist.Favorites -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_favorites_delete, count )
                    else
                        stringResource( R.string.smart_trash_favorites_clear, count )
                }
                BuiltInPlaylist.Offline -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_cached_delete, count )
                    else
                        stringResource( R.string.smart_trash_cached_clear, count )
                }
                BuiltInPlaylist.Downloaded -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_downloaded_delete, count )
                    else
                        stringResource( R.string.smart_trash_downloaded_clear, count )
                }
                BuiltInPlaylist.Top -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_top_delete, count )
                    else
                        stringResource( R.string.smart_trash_top_clear, count )
                }
                BuiltInPlaylist.OnDevice -> ""
                BuiltInPlaylist.Disliked -> {
                    if( hasSelection )
                        stringResource( R.string.smart_trash_all_delete, count )
                    else
                        stringResource( R.string.smart_trash_all_clear, count )
                }
            }
        }

    override var isActive: Boolean by activeState

    override fun onShortClick() = super.onShortClick()

    override fun onConfirm() {
        val songs = getSongs()

        when( builtInPlaylist() ) {
            BuiltInPlaylist.All -> deleteFromDatabase( songs )
            BuiltInPlaylist.Favorites -> removeFromFavorites( songs )
            BuiltInPlaylist.Offline -> clearCache( songs )
            BuiltInPlaylist.Downloaded -> deleteDownloads( songs )
            BuiltInPlaylist.Top -> resetPlayHistory( songs )
            BuiltInPlaylist.OnDevice -> {}
            BuiltInPlaylist.Disliked -> removeFromFavorites( songs )
        }

        Toaster.done()
    }

    private fun deleteFromDatabase( songs: List<Song> ) {
        Database.asyncTransaction {
            songs.forEach { song ->
                binder?.cache?.removeResource( song.id )
                binder?.downloadCache?.removeResource( song.id )
                songPlaylistMapTable.deleteBySongId( song.id )
                songArtistMapTable.deleteBySongId( song.id )
                songAlbumMapTable.deleteBySongId( song.id )
                formatTable.deleteBySongId( song.id )
                songTable.delete( song )
                WaveformExtractor.deleteWaveform(appContext(), song.id)
            }
            
            // Clean up orphaned artists, albums and empty playlists
            val deletedArtists = artistTable.deleteOrphaned()
            val deletedAlbums = albumTable.deleteOrphaned()
            // Delete playlists that have no songs left
            val playlists = playlistTable.getAll()
            for (playlist in playlists) {
                val songCount = songPlaylistMapTable.countSongsInPlaylist(playlist.id)
                if (songCount == 0) {
                    playlistTable.delete(playlist)
                }
            }
            Database.importSongTable.clear()
            Timber.tag("SmartTrash").d("Cleaned up $deletedArtists orphaned artists, $deletedAlbums orphaned albums")
        }
    }

    private fun removeFromFavorites( songs: List<Song> ) {
        Database.asyncTransaction {
            songTable.unlikeByIds( songs.map { it.id } )
        }
    }

    private fun clearCache( songs: List<Song> ) {
        Database.asyncTransaction {
            songs.forEach { song ->
                binder?.cache?.removeResource( song.id )
                binder?.downloadCache?.removeResource( song.id )
                WaveformExtractor.deleteWaveform(appContext(), song.id)
                formatTable.deleteBySongId( song.id )
                formatTable.updateContentLengthOf( song.id )
            }
        }
    }

    private fun deleteDownloads( songs: List<Song> ) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            deleteDownloadsOffMain( songs, binder?.cache, binder?.downloadCache, appContext() )
        }
    }

    private fun resetPlayHistory( songs: List<Song> ) {
        Database.asyncTransaction {
            eventTable.deleteBySongIds( songs.map { it.id } )
        }
    }
}

/**
 * Deletes the downloads for a batch of songs (issue #606 H4) — one scope per batch,
 * never one per song. Runs on the dispatcher it is launched from; null caches are no-ops.
 *
 * @param cache the streaming cache, may be null
 * @param downloadCache the download cache, may be null
 * @param context application context passed through to [MyDownloadHelper.removeDownload]
 */
@UnstableApi
internal suspend fun deleteDownloadsOffMain(
    songs: List<Song>,
    cache: Cache?,
    downloadCache: Cache?,
    context: Context
) {
    songs.forEach { song ->
        cache?.removeResource( song.id )
        downloadCache?.removeResource( song.id )
        Database.asyncTransaction {
            formatTable.deleteBySongId( song.id )
        }
        MyDownloadHelper.removeDownload( context, song.asMediaItem )
    }
}
