package app.n_zik.android.playback.services.automotive.models

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.isLocal
import kotlinx.coroutines.flow.first

/** Effective connectivity and click-time eligibility shared by automotive entry points. */
class AutoAccessPolicy(
    private val context: Context,
    private val database: Database,
    private val downloadHelper: MyDownloadHelper,
    private val binder: PlayerServiceModern.Binder?,
) {
    val online: Boolean get() = NetworkQualityHelper.isNetworkConnected(context)

    /** Remote actions cannot initiate work while physical or profile-forced offline. */
    fun allowsCommand(action: String): Boolean = online || action !in NETWORK_COMMANDS

    /** Local files, completed downloads and known-length complete cache spans need no network. */
    suspend fun offlineIds(): Set<String> {
        val completed = downloadHelper.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }.keys
        if (binder == null) return completed
        val cached = database.formatTable.allWithSongs().first().filter {
            val length = it.format.contentLength
            length != null && length > 0 && binder.cache.isCached(it.song.id, 0L, length)
        }.map { it.song.id }
        return completed + cached
    }

    /** Retains original indices so duplicate occurrences and unavailable taps remain unambiguous. */
    suspend fun eligible(items: List<MediaItem>, parentId: String? = null): List<IndexedValue<MediaItem>> {
        val excluded = if (parentId != null && reappliesExclusions(parentId)) {
            DislikedExclusion.excludedSongIds(context, database)
        } else emptySet()
        val available = if (online) null else offlineIds()
        return items.withIndex().filter { (_, item) ->
            canonicalId(item) !in excluded && (available == null || item.isLocal || canonicalId(item) in available)
        }
    }

    companion object {
        fun canonicalId(item: MediaItem): String = item.localConfiguration?.customCacheKey
            ?: item.mediaId.substringAfterLast('/')

        private val NETWORK_COMMANDS = setOf(
            AutoSessionConstants.ACTION_TOGGLE_DOWNLOAD,
            AutoSessionConstants.ACTION_START_RADIO,
            AutoSessionConstants.ACTION_SEARCH,
            AutoSessionConstants.ACTION_TOGGLE_DISCOVER,
        )
        private val FILTERED_CATEGORIES = setOf(
            AutoSessionConstants.ID_SEARCH_SONGS,
            AutoSessionConstants.ID_SONGS_ALL,
            AutoSessionConstants.ID_SONGS_FAVORITES,
            AutoSessionConstants.ID_SONGS_DOWNLOADED,
            AutoSessionConstants.ID_SONGS_CACHED,
            AutoSessionConstants.ID_SONGS_TOP,
            AutoSessionConstants.ID_SONGS_ONDEVICE,
            AutoSessionConstants.ID_ALBUMS_LIBRARY,
            AutoSessionConstants.ID_ALBUMS_FAVORITES,
            AutoSessionConstants.ID_ARTISTS_LIBRARY,
            AutoSessionConstants.ID_ARTISTS_FAVORITES,
            AutoSessionConstants.ID_PLAYLISTS_LOCAL,
            AutoSessionConstants.ID_PLAYLISTS_YT,
            AutoSessionConstants.ID_PLAYLISTS_PINNED,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR,
            PlayerServiceModern.SONG,
            PlayerServiceModern.SEARCHED,
        )

        /** Direct detail, Disliked and Quick Picks retain their deliberately separate policies. */
        fun reappliesExclusions(parentId: String): Boolean = parentId.removeSuffix("_SHUFFLE").removeSuffix("_PLAY_ALL").substringBefore('/') in FILTERED_CATEGORIES
    }
}
