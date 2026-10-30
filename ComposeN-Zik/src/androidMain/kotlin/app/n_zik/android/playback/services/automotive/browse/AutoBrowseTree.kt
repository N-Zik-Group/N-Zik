package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
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
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
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

    private val searchHandler = SearchBrowseHandler()
    private val handlers: List<BrowseHandler> = listOf(
        RootBrowseHandler(),
        QuickPicksBrowseHandler(),
        SongsBrowseHandler(),
        ArtistsBrowseHandler(),
        AlbumsBrowseHandler(),
        PlaylistsBrowseHandler(),
        searchHandler,
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
            val loadedRows = handler?.getChildren(actualParentId, context, database, downloadHelper, binder).orEmpty().map { item ->
                val uri = item.mediaMetadata.artworkUri
                val source = when {
                    uri?.scheme in setOf("http", "https") -> uri.toString()
                    uri?.authority == ArtworkContentProvider.AUTHORITY && uri.path == "/remote" -> uri.getQueryParameter("u")
                    else -> null
                }
                if (source == null) item else item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
                    .setArtworkUri(ArtworkContentProvider.uriForRemote(source, requestProfile,
                        context.preferences.getBoolean(cropVideoThumbnailsKey, true), ArtworkRevisions.current())).build()).build()
            }
            val access = AutoAccessPolicy(context, database, downloadHelper, binder)
            val loaded = if (access.online) loadedRows else {
                val available = access.eligible(loadedRows.filter { it.mediaMetadata.isPlayable == true }).map { it.value }.toSet()
                loadedRows.filter { it.mediaMetadata.isPlayable != true || it in available ||
                    (AutoMediaIdContract.isQueueAction(it.mediaId) && available.isNotEmpty()) }
            }
            synchronized(publicationLock) {
                // Rejected generations must not mutate retention or legacy parent lookups.
                if (!ownsPublication(requestProfile, generation)) return emptyList()
                val captureParent = if (handler is SearchBrowseHandler) handler.canonicalParent(actualParentId, context) ?: actualParentId else actualParentId
                val captured = selectionStore.capture(captureParent, loaded, expectedProfile = requestProfile)
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

    /** Pending loads lose publication ownership even if connectivity later returns. */
    fun invalidateAccess() = synchronized(publicationLock) {
        clearCache()
        selectionStore.clear()
    }

    fun refreshArtwork() = synchronized(publicationLock) { cache.clear() }

    /** Resets search ownership without dropping already displayed playback snapshots. */
    fun clearCache() = synchronized(publicationLock) {
        profileGeneration++
        cache.clear()
        searchHandler.windows.clear()
        AutoSearchState.clear()
    }
}
