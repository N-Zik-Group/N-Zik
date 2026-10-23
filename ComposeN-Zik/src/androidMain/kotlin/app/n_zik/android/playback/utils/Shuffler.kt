package app.n_zik.android.playback.utils

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.PlayerServiceModern
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.utils.ToastRelay
import app.it.fast4x.rimusic.enums.MaxSongs
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.forcePlayFromBeginning
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.maxSongsInQueueKey
import app.it.fast4x.rimusic.utils.mediaItems
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.excludeDislikedSongsKey
import app.it.fast4x.rimusic.utils.excludeDislikedArtistsKey
import app.it.fast4x.rimusic.utils.excludeDislikedAlbumsKey
import app.it.fast4x.rimusic.enums.DislikeMode
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object Shuffler {

    /** Test seam: overridden with `UnconfinedTestDispatcher()` in `ShufflerTest` so assertions
     *  made right after [play] stay deterministic instead of racing a real background thread. */
    internal var backgroundDispatcher: CoroutineDispatcher = NzikDispatchers.DATA
    internal val scope = NzikDispatchers.fireAndForget(SupervisorJob())

    /**
     * [onComplete] is invoked once the real work for the given exit path is actually finished,
     * never before -- on the caller's own thread for the immediate `mediaItems.isEmpty()` early
     * return below (no coroutine involved yet), and on [NzikDispatchers.UI] for every other exit
     * path (empty after filtering, a filtering failure, and success/failure of the final playback
     * dispatch). `play()` itself stays non-suspend (~8 of its ~13 callers invoke it from a
     * non-suspend `onClick`), so callers that need to react to completion (e.g. clearing a loading
     * flag) must do so via this callback rather than by assuming `play()` blocks until done, as it
     * used to via `runBlocking`.
     */
    fun play(binder: PlayerServiceModern.Binder, mediaItems: List<MediaItem>, onComplete: () -> Unit = {}) {
        if (mediaItems.isEmpty()) {
            ToastRelay.i(R.string.no_song_to_shuffle)
            onComplete()
            return
        }

        scope.launch(backgroundDispatcher) {
            try {
                var filteredMediaItems = mediaItems

                // Filter disliked songs if setting is enabled
                val preferences = appContext().preferences
                val excludeDislikedSongs = preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
                if (excludeDislikedSongs.isEnabled) {
                    val dislikedSongIds = Database.songTable.getAllDislikedIds()
                    if (dislikedSongIds.isNotEmpty()) {
                        filteredMediaItems = filteredMediaItems.filter {
                            !dislikedSongIds.contains(it.mediaId)
                        }
                    }
                }

                // Filter songs from disliked artists if setting is enabled
                val excludeDislikedArtists = preferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
                if (excludeDislikedArtists.isEnabled) {
                    val dislikedArtistSongIds = Database.songTable.getSongsByDislikedArtists()
                    if (dislikedArtistSongIds.isNotEmpty()) {
                        filteredMediaItems = filteredMediaItems.filter {
                            !dislikedArtistSongIds.contains(it.mediaId)
                        }
                    }
                }

                // Filter songs from disliked albums if setting is enabled
                val excludeDislikedAlbums = preferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
                if (excludeDislikedAlbums.isEnabled) {
                    val dislikedAlbumSongIds = Database.songTable.getSongsByDislikedAlbums()
                    if (dislikedAlbumSongIds.isNotEmpty()) {
                        filteredMediaItems = filteredMediaItems.filter {
                            !dislikedAlbumSongIds.contains(it.mediaId)
                        }
                    }
                }

                if (filteredMediaItems.isEmpty()) {
                    withContext(NzikDispatchers.UI) {
                        ToastRelay.i(R.string.no_song_to_shuffle)
                        onComplete()
                    }
                    return@launch
                }

                val max = preferences
                    .getEnum(maxSongsInQueueKey, MaxSongs.Unlimited)
                    .toInt()
                val toPlay = filteredMediaItems.shuffled().take(max)
                withContext(NzikDispatchers.UI) {
                    try {
                        binder.stopRadio()
                        binder.player.forcePlayFromBeginning(toPlay)
                        ToastRelay.s(R.string.songs_shuffled, toPlay.size)
                    } catch (e: Exception) {
                        ToastRelay.e(R.string.no_song_found)
                    } finally {
                        onComplete()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Preference/DB lookups above (getAllDislikedIds/getSongsByDislikedArtists/
                // getSongsByDislikedAlbums) run with no caller-side try/finally to fall back on
//
                // now that this whole block is dispatched off-main -- without this catch, a
                // transient DB error would leave onComplete() (and therefore any caller's loading
//
                // flag) stuck forever instead of surfacing an error toast like the equivalent
//
                // failure a few lines below already does.
                withContext(NzikDispatchers.UI) {
                    ToastRelay.e(R.string.no_song_found)
                    onComplete()
                }
            }
        }
    }

    @JvmName("playSongs")
    fun play(binder: PlayerServiceModern.Binder, songs: List<Song>, onComplete: () -> Unit = {}) {
        play(binder, songs.map(Song::asMediaItem), onComplete)
    }

    fun queue(player: Player) {
        try {
            val current = player.currentMediaItemIndex
            val total = player.mediaItemCount
            if (total <= 1) return

            val items = player.mediaItems.toMutableList().apply {
                removeAt(current)
            }
            val count = items.size
            if (count > 0) {
                if (current > 0) player.removeMediaItems(0, current)
                if (current < player.mediaItemCount - 1) player.removeMediaItems(1, player.mediaItemCount)
                player.addMediaItems(items.shuffled())
                Toaster.s(R.string.queue_shuffled, formatArgs = *arrayOf(count))
            }
        } catch (e: Exception) {
            Toaster.e(R.string.no_song_found)
        }
    }

    fun <T> shuffle(list: List<T>): List<T> = list.shuffled()

    fun positions(playlistId: Long) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            try {
                val items = Database.songPlaylistMapTable.allSongsOf(playlistId).first()
                val count = items.size
                if (count == 0) return@launch
                val shuffled = items.shuffled()
                Database.asyncTransaction {
                    shuffled.forEachIndexed { i, song ->
                        Database.songPlaylistMapTable.updatePosition(playlistId, song.id, i)
                    }
                }
                withContext(NzikDispatchers.UI) {
                    Toaster.s(R.string.playlist_positions_shuffled, formatArgs = *arrayOf(count))
                }
            } catch (e: Exception) {
                withContext(NzikDispatchers.UI) {
                    Toaster.e(R.string.no_song_found)
                }
            }
        }
    }
}
