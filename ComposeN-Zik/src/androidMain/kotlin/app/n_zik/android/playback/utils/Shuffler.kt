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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object Shuffler {

    fun play(binder: PlayerServiceModern.Binder, mediaItems: List<MediaItem>) {
        if (mediaItems.isEmpty()) {
            Toaster.i(R.string.no_song_to_shuffle)
            return
        }

        var filteredMediaItems = mediaItems

        // Filter disliked songs if setting is enabled
        val preferences = appContext().preferences
        val excludeDislikedSongs = preferences.getString(excludeDislikedSongsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedSongs.isEnabled) {
            val dislikedSongIds = runBlocking {
                Database.songTable.getAllDislikedIds()
            }
            if (dislikedSongIds.isNotEmpty()) {
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedSongIds.contains(it.mediaId)
                }
            }
        }

        // Filter songs from disliked artists if setting is enabled
        val excludeDislikedArtists = preferences.getString(excludeDislikedArtistsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedArtists.isEnabled) {
            val dislikedArtistSongIds = runBlocking {
                Database.songTable.getSongsByDislikedArtists()
            }
            if (dislikedArtistSongIds.isNotEmpty()) {
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedArtistSongIds.contains(it.mediaId)
                }
            }
        }

        // Filter songs from disliked albums if setting is enabled
        val excludeDislikedAlbums = preferences.getString(excludeDislikedAlbumsKey, DislikeMode.Enabled.name)?.let { runCatching { DislikeMode.valueOf(it) }.getOrNull() } ?: DislikeMode.Enabled
        if (excludeDislikedAlbums.isEnabled) {
            val dislikedAlbumSongIds = runBlocking {
                Database.songTable.getSongsByDislikedAlbums()
            }
            if (dislikedAlbumSongIds.isNotEmpty()) {
                filteredMediaItems = filteredMediaItems.filter {
                    !dislikedAlbumSongIds.contains(it.mediaId)
                }
            }
        }

        if (filteredMediaItems.isEmpty()) {
            Toaster.i(R.string.no_song_to_shuffle)
            return
        }

        val max = preferences
            .getEnum(maxSongsInQueueKey, MaxSongs.Unlimited)
            .toInt()
        val toPlay = filteredMediaItems.shuffled().take(max)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                binder.stopRadio()
                binder.player.forcePlayFromBeginning(toPlay)
                Toaster.s(R.string.songs_shuffled, formatArgs = *arrayOf(toPlay.size))
            } catch (e: Exception) {
                Toaster.e(R.string.no_song_found)
            }
        }
    }

    @JvmName("playSongs")
    fun play(binder: PlayerServiceModern.Binder, songs: List<Song>) {
        play(binder, songs.map(Song::asMediaItem))
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
        CoroutineScope(Dispatchers.Default).launch {
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
                withContext(Dispatchers.Main) {
                    Toaster.s(R.string.playlist_positions_shuffled, formatArgs = *arrayOf(count))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toaster.e(R.string.no_song_found)
                }
            }
        }
    }
}
