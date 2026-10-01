package app.n_zik.android.playback.services.automotive.browse

import app.n_zik.android.playback.services.automotive.models.AutoSearchState
import android.content.Context
import androidx.media3.common.MediaItem
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.browse.handlers.*

class AutoBrowseTree(
    private val context: Context,
    private val database: Database,
    private val downloadHelper: MyDownloadHelper
) {
    private val cache = AutoBrowseCache()

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
        val isPagination = parentId.contains("_PAGE_")
        val actualParentId = if (isPagination) parentId.substringBefore("_PAGE_") else parentId

        // Every fresh navigation (non-pagination request) reloads from the source:
        // stale entries are dropped so each tab shows current data on open.
        // Pagination of the same parent still hits the cache.
        if (!isPagination) cache.clear()

        var list: List<MediaItem> = cache.get(actualParentId) ?: emptyList()
        
        if (list.isEmpty()) {
            val handler = handlers.firstOrNull { it.handles(actualParentId) }
            if (handler != null) {
                list = handler.getChildren(actualParentId, context, database, downloadHelper, binder)
            }
            if (list.isNotEmpty()) {
                cache.put(actualParentId, list)
            }
        }
        
        return PaginationHelper.paginate(context, list, actualParentId, pageIndex)
    }

    fun clearCache() {
        cache.clear()
        AutoSearchState.clear()
    }
}
