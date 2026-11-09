package app.n_zik.android.playback.services.automotive.browse

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
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
    private val emittedFolders = linkedMapOf<String, MediaItem>()
    private data class BrowseKey(val browser: Any, val parentId: String)
    private val visibleLists = linkedMapOf<BrowseKey, List<MediaItem>>()

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
        binder: PlayerServiceModern.Binder?,
        browser: Any = Unit,
    ): List<MediaItem> {
        val requestProfile = getActiveProfile(context)
        val generation = synchronized(publicationLock) {
            if (getActiveProfile(context) != requestProfile) return emptyList()
            if (cacheProfile != requestProfile) {
                cache.clear()
                selectionStore.clear()
                emittedFolders.clear()
                visibleLists.clear()
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
        val list = if (cached != null) cached else if (actualParentId == AutoSuggestedQueueLoader.ROOT_ID) {
            val suggestions = AutoSuggestedQueueLoader.load(context, database, downloadHelper, binder)
            synchronized(publicationLock) {
                if (!ownsPublication(requestProfile, generation)) return emptyList()
                val captured = suggestions.mapNotNull { suggestion ->
                    val browsable = suggestion.items.isEmpty()
                    val id = if (browsable) suggestion.browseId ?: return@mapNotNull null
                        else AutoMediaIdContract.playlistPlayAll("suggested")
                    val action = withRemoteArtwork(MediaItem.Builder()
                        .setMediaId(id)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(suggestion.title)
                            .setArtworkUri(suggestion.artworkUri).setMediaType(suggestion.mediaType)
                            .setIsPlayable(!browsable).setIsBrowsable(browsable).build()).build(), requestProfile)
                    if (browsable) action else selectionStore.capture(suggestion.parentId, listOf(action) + suggestion.items,
                        expectedProfile = requestProfile).firstOrNull()
                }.filter { it.mediaMetadata.isBrowsable == true || selectionStore.resolveAction(it.mediaId) != null }
                if (!publishCached(requestProfile, generation, actualParentId, captured)) return emptyList()
                captured
            }
        } else {
            val handler = handlers.firstOrNull { it.handles(actualParentId) }
            val loadedRows = handler?.getChildren(actualParentId, context, database, downloadHelper, binder).orEmpty().map { item ->
                withRemoteArtwork(item, requestProfile)
            }
            val access = AutoAccessPolicy(context, database, downloadHelper, binder)
            val loaded = if (access.online) loadedRows else {
                val available = access.eligible(loadedRows.filter { it.mediaMetadata.isPlayable == true }).map { it.value }.toSet()
                loadedRows.filter { it.mediaMetadata.isPlayable != true || it in available ||
                    AutoMediaIdContract.playlistCategoryForAction(it.mediaId) != null ||
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
            val visible = if (actualParentId == AutoSuggestedQueueLoader.ROOT_ID) list
                else PaginationHelper.paginate(context, list, actualParentId, page?.index ?: pageIndex)
            visible.also {
                visibleLists[BrowseKey(browser, parentId)] = visible
                while (visibleLists.size > 32) visibleLists.remove(visibleLists.keys.first())
            }
        }
    }

    /** Later standard pages retain the exact rows captured by the first request. */
    fun retainedChildren(parentId: String, browser: Any = Unit): List<MediaItem>? = synchronized(publicationLock) {
        if (cacheProfile != getActiveProfile(context)) null else visibleLists[BrowseKey(browser, parentId)]
    }

    /** Releases page captures when their controller disconnects. */
    fun releaseBrowser(browser: Any) = synchronized(publicationLock) {
        visibleLists.keys.removeAll { it.browser == browser }
    }

    /** Keeps a displayed queue's own title and artwork when the host asks for its item. */
    fun retainedAction(mediaId: String): MediaItem? = synchronized(publicationLock) {
        if (cacheProfile != getActiveProfile(context)) return@synchronized null
        visibleLists.values.asSequence().flatten().firstOrNull { it.mediaId == mediaId }
            ?.takeIf { AutoMediaIdContract.isScopedAction(mediaId) && selectionStore.retains(listOf(it)) }
    }

    /** Retains only real browsable rows, never a broad handler prefix match. */
    fun retainFolders(items: List<MediaItem>, profile: String) = synchronized(publicationLock) {
        if (getActiveProfile(context) != profile) return@synchronized
        if (cacheProfile != profile) {
            invalidateAccess()
            cacheProfile = profile
        }
        retainFoldersLocked(items)
    }

    private fun retainFoldersLocked(items: List<MediaItem>) {
        items.filter { it.mediaMetadata.isBrowsable == true }.forEach {
            emittedFolders.remove(it.mediaId)
            emittedFolders[it.mediaId] = it
        }
        while (emittedFolders.size > 512) emittedFolders.remove(emittedFolders.keys.first())
    }

    suspend fun folder(mediaId: String, binder: PlayerServiceModern.Binder?): MediaItem? {
        synchronized(publicationLock) {
            if (cacheProfile == getActiveProfile(context)) emittedFolders[mediaId]?.let { return it }
        }
        if (mediaId == PlayerServiceModern.ROOT) return rootItem()
        if (mediaId == AutoSuggestedQueueLoader.ROOT_ID) return rootItem(suggested = true)
        RootBrowseHandler().getChildren(PlayerServiceModern.ROOT, context, database, downloadHelper, binder)
            .firstOrNull { it.mediaId == mediaId }?.let { return it }
        val parent = STATIC_PARENTS[mediaId] ?: return null
        return handlers.firstOrNull { it.handles(parent) }
            ?.getChildren(parent, context, database, downloadHelper, binder)
            ?.firstOrNull { it.mediaId == mediaId && it.mediaMetadata.isBrowsable == true }
    }

    fun rootItem(suggested: Boolean = false): MediaItem = MediaItem.Builder()
        .setMediaId(if (suggested) AutoSuggestedQueueLoader.ROOT_ID else PlayerServiceModern.ROOT)
        .setMediaMetadata(MediaMetadata.Builder().setIsPlayable(false)
            .setIsBrowsable(true).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private companion object {
        val STATIC_PARENTS = buildMap {
            listOf(AutoSessionConstants.ID_SONGS_ALL, AutoSessionConstants.ID_SONGS_FAVORITES,
                AutoSessionConstants.ID_SONGS_DISLIKED, AutoSessionConstants.ID_SONGS_DOWNLOADED,
                AutoSessionConstants.ID_SONGS_CACHED, AutoSessionConstants.ID_SONGS_TOP,
                AutoSessionConstants.ID_SONGS_ONDEVICE).forEach { put(it, PlayerServiceModern.SONG) }
            listOf(AutoSessionConstants.ID_ARTISTS_LIBRARY, AutoSessionConstants.ID_ARTISTS_FAVORITES,
                AutoSessionConstants.ID_ARTISTS_DISLIKED).forEach { put(it, PlayerServiceModern.ARTIST) }
            listOf(AutoSessionConstants.ID_ALBUMS_LIBRARY, AutoSessionConstants.ID_ALBUMS_FAVORITES,
                AutoSessionConstants.ID_ALBUMS_DISLIKED).forEach { put(it, PlayerServiceModern.ALBUM) }
            listOf(AutoSessionConstants.ID_PLAYLISTS_LOCAL, AutoSessionConstants.ID_PLAYLISTS_PINNED,
                AutoSessionConstants.ID_PLAYLISTS_REWIND, AutoSessionConstants.ID_PLAYLISTS_YT)
                .forEach { put(it, PlayerServiceModern.PLAYLIST) }
            listOf(AutoSessionConstants.ID_PLAYLISTS_YT_ALL, AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
                AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS).forEach { put(it, AutoSessionConstants.ID_PLAYLISTS_YT) }
            listOf(AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL, AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
                AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR).forEach { put(it, AutoSessionConstants.ID_PLAYLISTS_REWIND) }
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

    private fun withRemoteArtwork(item: MediaItem, profile: String): MediaItem {
        val uri = item.mediaMetadata.artworkUri
        val source = when {
            uri?.scheme in setOf("http", "https") -> uri.toString()
            uri?.authority == ArtworkContentProvider.AUTHORITY && uri.path == "/remote" -> uri.getQueryParameter("u")
            else -> null
        }
        return if (source == null) item else item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
            .setArtworkUri(ArtworkContentProvider.uriForRemote(source, profile,
                context.preferences.getBoolean(cropVideoThumbnailsKey, true), ArtworkRevisions.current())).build()).build()
    }

    /** Pending loads lose publication ownership even if connectivity later returns. */
    fun invalidateAccess() = synchronized(publicationLock) {
        clearCache()
        selectionStore.clear()
        emittedFolders.clear()
        visibleLists.clear()
    }

    fun refreshArtwork() = synchronized(publicationLock) {
        cache.clear()
        val profile = getActiveProfile(context)
        if (cacheProfile != profile) return@synchronized
        val refreshedItems = mutableMapOf<MediaItem, MediaItem>()
        fun refresh(item: MediaItem): MediaItem {
            refreshedItems[item]?.let { return it }
            val uri = item.mediaMetadata.artworkUri ?: return item
            if (uri.authority != ArtworkContentProvider.AUTHORITY || uri.path != "/remote") return item
            val source = uri.getQueryParameter("u") ?: return item
            return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(
                ArtworkContentProvider.uriForRemote(source, profile,
                    context.preferences.getBoolean(cropVideoThumbnailsKey, true), ArtworkRevisions.current())
            ).build()).build().also { refreshedItems[item] = it }
        }
        emittedFolders.replaceAll { _, item -> refresh(item) }
        visibleLists.replaceAll { _, items -> items.map(::refresh) }
    }

    /** Resets search ownership without dropping already displayed playback snapshots. */
    fun clearCache() = synchronized(publicationLock) {
        profileGeneration++
        cache.clear()
        visibleLists.keys.removeAll { key ->
            val parent = AutoMediaIdContract.parsePage(key.parentId)?.parentId ?: key.parentId
            parent.startsWith("SEARCH_") || AutoMediaIdContract.parseSearchContinuation(parent) != null
        }
        searchHandler.windows.clear()
        emittedFolders.keys.removeAll { id ->
            AutoMediaIdContract.parseSearchContinuation(AutoMediaIdContract.parsePage(id)?.parentId ?: id) != null
        }
        AutoSearchState.clear()
    }
}
