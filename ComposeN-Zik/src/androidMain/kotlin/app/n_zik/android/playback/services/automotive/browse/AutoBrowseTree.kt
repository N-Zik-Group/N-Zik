package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.AlbumsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.ArtistsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.BrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.PlaylistDetailHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.PlaylistsBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.QuickPicksBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.RootBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.SearchBrowseHandler
import app.n_zik.android.playback.services.automotive.browse.handlers.SongsBrowseHandler
import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import app.n_zik.android.playback.services.automotive.models.AutoSelectionStore
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import app.n_zik.android.playback.services.PlayerServiceModern

class AutoBrowseTree(
    private val context: Context,
    private val database: Database,
    private val downloadHelper: MyDownloadHelper
) {
    private val cache = AutoBrowseCache()
    internal val selectionStore = AutoSelectionStore()
    private val publicationLock = Any()
    private var cacheProfile: String? = null
    private var profileGeneration = 0L

    private val handlers: List<BrowseHandler> = listOf(
        RootBrowseHandler(),
        QuickPicksBrowseHandler(),
        SongsBrowseHandler(),
        ArtistsBrowseHandler(),
        AlbumsBrowseHandler(),
        PlaylistsBrowseHandler(),
        SearchBrowseHandler(),
        ArtistDetailHandler(),
        AlbumDetailHandler(),
        PlaylistDetailHandler()
    )

    suspend fun getChildren(
        parentId: String,
        pageIndex: Int,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val requestProfile = getActiveProfile(context)
        val generation = synchronized(publicationLock) {
            if (getActiveProfile(context) != requestProfile) return emptyList()
            if (cacheProfile != requestProfile) {
                cache.clear()
                selectionStore.clear()
                cacheProfile = requestProfile
                profileGeneration++
            }
            profileGeneration
        }
        val page = AutoMediaIdContract.parsePage(parentId)
        val actualParentId = page?.parentId ?: parentId
        val key = cacheKey(requestProfile, generation, actualParentId)
        val cached = synchronized(publicationLock) {
            if (!ownsPublication(requestProfile, generation)) return emptyList()
            // Ordinary navigation reloads its source; page requests reuse their parent.
            if (page == null) cache.clear()
            cache.get(key)
        }
        val list = if (cached != null) cached else {
            val handler = handlers.firstOrNull { it.handles(actualParentId) }
            val loaded = handler?.getChildren(actualParentId, context, database, downloadHelper, binder).orEmpty()
            synchronized(publicationLock) {
                // Rejected generations must not mutate retention or legacy parent lookups.
                if (!ownsPublication(requestProfile, generation)) return emptyList()
                val captured = selectionStore.capture(actualParentId, loaded, expectedProfile = requestProfile)
                if (!publishCached(requestProfile, generation, actualParentId, captured)) return emptyList()
                captured
            }
        }
        return synchronized(publicationLock) {
            if (!ownsPublication(requestProfile, generation)) return emptyList()
            PaginationHelper.paginate(context, list, actualParentId, page?.index ?: pageIndex)
        }
    }

    /** A late result must never enter the namespace of a newer profile generation. */
    internal fun publishCached(profile: String, generation: Long, parentId: String, items: List<MediaItem>): Boolean =
        synchronized(publicationLock) {
            if (!ownsPublication(profile, generation)) return@synchronized false
            cache.put(cacheKey(profile, generation, parentId), items)
            true
        }

    private fun ownsPublication(profile: String, generation: Long): Boolean =
        cacheProfile == profile && profileGeneration == generation && getActiveProfile(context) == profile

    private fun cacheKey(profile: String, generation: Long, parentId: String): String =
        "$generation:$profile:$parentId"

    fun clearCache() {
        cache.clear()
        AutoSearchState.clear()
    }
}
