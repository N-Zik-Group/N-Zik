package app.n_zik.android.playback.services.automotive.session

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import app.n_zik.android.enums.MaxSongs
import app.n_zik.android.hasExplicitPrefix
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asSong
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.maxSongsInQueueAndroidAutoKey
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.playback.services.automotive.models.CancellableAutoMediaItemMapper
import app.n_zik.android.playback.services.isLocal
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.song
import app.n_zik.innertube.utils.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/** Resolves automotive voice and supported links into canonical, eligible playback items. */
internal class AutoPlaybackRequestResolver(
    private val context: Context,
    private val database: Database,
    private val access: AutoAccessPolicy,
) {
    suspend fun resolve(metadata: MediaItem.RequestMetadata): List<MediaItem> = try {
        val uri = metadata.mediaUri
        if (uri != null) {
            val id = youtubeId(uri)
            if (id == null) emptyList() else {
                val local = database.songTable.findById(id).first()
                val song = local ?: if (access.online) Innertube.song(id)?.getOrThrow()?.asSong else null
                eligible(listOfNotNull(song))
            }
        } else {
            val query = metadata.searchQuery?.trim().orEmpty()
            if (query.isBlank()) emptyList() else {
                val matches = database.songTable.findAllTitleArtistContains(query).first()
                    .filter { song ->
                        // The shared DAO uses LIKE, where these characters are wildcards.
                        query.none { it == '%' || it == '_' } ||
                            song.cleanTitle().contains(query, ignoreCase = true) ||
                            song.cleanArtistsText().contains(query, ignoreCase = true)
                    }
                    .sortedBy { if (it.cleanTitle().equals(query, ignoreCase = true)) 0 else 1 }
                val local = eligible(matches)
                if (local.isNotEmpty() || !access.online) local else {
                    val songs = Innertube.searchPage<Innertube.SongItem>(query, Innertube.SearchFilter.Song.value) {
                        Innertube.SongItem.from(it)
                    }?.getOrThrow()?.items.orEmpty().map { it.asSong }
                    eligible(songs)
                }
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Timber.tag("AutoPlaybackRequestResolver").w(error, "Unable to resolve automotive playback request")
        throw error
    }

    private suspend fun eligible(songs: List<Song>): List<MediaItem> {
        val parental = context.preferences.getBoolean(parentalControlEnabledKey, false)
        val candidates = songs.filter { !parental || !it.title.hasExplicitPrefix() }
        val identities = candidates.map { MediaItem.Builder().setMediaId(it.id).build() }
        val limit = context.preferences.getEnum(maxSongsInQueueAndroidAutoKey, MaxSongs.Unlimited).toInt()
        return access.eligible(identities, AutoSessionConstants.ID_SEARCH_SONGS).take(limit).map { eligible ->
            val song = candidates[eligible.index]
            CancellableAutoMediaItemMapper.mapSongToMediaItem(song, loadArtwork = song.isLocal)
        }
    }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        internal fun youtubeId(uri: Uri): String? {
            if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.userInfo != null ||
                uri.port !in setOf(-1, 80, 443)) return null
            val id = when (uri.host?.lowercase()) {
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" ->
                    if (uri.path == "/watch" && uri.getQueryParameters("v").size == 1) uri.getQueryParameter("v") else null
                "youtu.be" -> uri.pathSegments.singleOrNull()
                else -> null
            }
            return id?.takeIf(VIDEO_ID::matches)
        }
    }
}
