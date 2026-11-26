package app.n_zik.android.components.song

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.models.Album
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.isLocal
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.isUserEditedPlaybackAlbumId
import app.n_zik.android.utils.selectOnlinePlaybackAlbumId
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.nextPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class TrackAlbumLink(val album: Album, val route: String = album.id) {
    fun menuRoute(): String {
        val params = route.substringAfter("?params=", "")
        val destination = "${NavRoutes.album.name}/${Uri.encode(album.id)}"
        return if (params.isBlank()) destination else "$destination?params=${Uri.encode(params)}"
    }
}

internal suspend fun resolveTrackAlbumLink(
    song: Song,
    albumId: String? = null,
    albumTitle: String? = null,
    onOnlineLookup: suspend () -> Unit = {},
): TrackAlbumLink? {
    val explicit = albumId?.takeIf { it.isNotBlank() && isDeviceAlbumId(it) == song.isLocal }
    val albums = Database.songAlbumMapTable.findAlbumsOfDirect(song.id)
    val edited = albums.singleOrNull { isDeviceAlbumId(it.id) == song.isLocal && isUserEditedPlaybackAlbumId(it.id) }
    if (explicit != null) {
        val album = Database.findEditedAlbumLink(song.id, explicit)
            ?: edited
            ?: Database.albumTable.findByIdDirect(explicit)
            ?: Album(explicit, albumTitle)
        return TrackAlbumLink(album)
    }

    if (song.isLocal) return albums.firstOrNull { isDeviceAlbumId(it.id) }?.let { TrackAlbumLink(it) }
    edited?.let { return TrackAlbumLink(it) }

    val exact = if (song.id.length == 11) {
        val context = appContext()
        if (!OfflineMode.enabled(context) && NetworkQualityHelper.isNetworkConnected(context)) {
            onOnlineLookup()
            Innertube.nextPage(videoId = song.id)
                ?.onFailure {
                    if (it is CancellationException) throw it
                    Timber.tag("TrackAlbumLink").w(it, "Album lookup failed for ${song.id}")
                }
                ?.getOrNull()
                ?.itemsPage?.items
                ?.firstOrNull { it.info?.endpoint?.videoId == song.id }
                ?.album?.takeIf { !it.endpoint?.browseId.isNullOrBlank() }
                ?: if (albums.isEmpty()) searchExactTrackSongs(song).mapNotNull { it.album }
                    .filter { it.endpoint?.browseId?.startsWith("MPRE") == true }
                    .distinctBy { it.endpoint?.browseId }.singleOrNull()
                else null
        } else null
    } else null
    val endpoint = exact?.endpoint
    val selectedId = selectOnlinePlaybackAlbumId(null, endpoint?.browseId, albums.map(Album::id)) ?: return null
    val selected = albums.firstOrNull { it.id == selectedId }
        ?: Database.albumTable.findByIdDirect(selectedId)
        ?: Album(selectedId, exact?.name)
    val params = endpoint?.params?.takeIf { selectedId == endpoint.browseId && it.isNotBlank() }
    return TrackAlbumLink(selected, if (params != null) "$selectedId?params=$params" else selectedId)
}

@Composable
internal fun rememberTrackAlbumLink(
    song: Song,
    albumId: String? = null,
    albumTitle: String? = null
): State<TrackAlbumLink?> = key(song.id, albumId, albumTitle) {
    val initial = albumId
        ?.takeIf { it.isNotBlank() && isDeviceAlbumId(it) == song.isLocal }
        ?.let { TrackAlbumLink(Album(it, albumTitle)) }
    produceState(initial) {
        value = withContext(NzikDispatchers.DATA) {
            try {
                resolveTrackAlbumLink(song, albumId, albumTitle)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.tag("TrackAlbumLink").w(error, "Album lookup failed for ${song.id}")
                initial
            }
        }
    }
}
