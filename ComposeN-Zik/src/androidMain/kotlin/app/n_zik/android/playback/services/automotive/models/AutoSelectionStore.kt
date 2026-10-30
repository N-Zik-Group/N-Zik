package app.n_zik.android.playback.services.automotive.models

import java.util.UUID
import androidx.media3.common.MediaItem
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract

/** Binds playback to the immutable list that the controller actually browsed. */
class AutoSelectionStore(
    private val profile: () -> String = { getActiveProfile(appContext()) },
    private val maxEntries: Int = 32,
    private val maxRetainedItems: Int = 50_000,
) {
    /** Canonical playback items and the exact selected index. */
    data class Selection(val items: List<MediaItem>, val startIndex: Int, val parentId: String)

    private data class Snapshot(val parent: String, val items: List<MediaItem>, val profile: String)

    private val entries = LinkedHashMap<String, Snapshot>()
    private var activeProfile: String? = null
    private var retainedItemCount = 0L

    init {
        require(maxEntries > 0 && maxRetainedItems > 0)
    }

    /** Retains the complete displayed queue and assigns scoped selection IDs. */
    @Synchronized
    fun capture(parentId: String, items: List<MediaItem>, expectedProfile: String? = null): List<MediaItem> {
        val capturedProfile = checkProfile()
        if (expectedProfile != null && expectedProfile != capturedProfile) return emptyList()
        val playable = items.filter { it.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(it.mediaId) }
        val canonical = playable.map { item ->
            val songId = item.localConfiguration?.customCacheKey ?: item.mediaId.substringAfterLast('/')
            item.buildUpon().setMediaId(songId).setMediaMetadata(
                item.mediaMetadata.buildUpon().setArtworkData(null, null).build()
            ).build()
        }
        val token = UUID.randomUUID().toString()
        entries[token] = Snapshot(parentId, canonical, capturedProfile)
        retainedItemCount += canonical.size
        // Browse has already materialized this queue. Keep one oversized newest snapshot intact,
        // rather than silently truncate it; total items stay within max(budget, newest size).
        while (entries.size > maxEntries || (retainedItemCount > maxRetainedItems && entries.size > 1)) {
            val removed = entries.remove(entries.keys.first()) ?: break
            retainedItemCount -= removed.items.size
        }
        var index = 0
        return items.map { item ->
            when {
                AutoMediaIdContract.isQueueAction(item.mediaId) && canonical.isNotEmpty() -> item.buildUpon().setMediaId(
                    AutoMediaIdContract.scopedAction(token, AutoMediaIdContract.isShuffle(item.mediaId))
                ).build()
                item.mediaMetadata.isPlayable == true && !AutoMediaIdContract.isQueueAction(item.mediaId) -> {
                    val occurrence = index++
                    item.buildUpon().setMediaId(
                        AutoMediaIdContract.selection(token, canonical[occurrence].mediaId, occurrence)
                    ).build()
                }
                else -> item
            }
        }
    }

    /** Resolves only a retained selection belonging to the active profile. */
    @Synchronized
    fun resolve(mediaId: String): Selection? {
        checkProfile()
        val id = AutoMediaIdContract.parseSelection(mediaId) ?: return null
        val snapshot = entries[id.token]?.takeIf { it.profile == activeProfile } ?: return null
        if (snapshot.items.getOrNull(id.index)?.mediaId != id.songId) return null
        return Selection(snapshot.items, id.index, snapshot.parent)
    }

    /** Resolves an action against its displayed capture, including after a refresh. */
    @Synchronized
    fun resolveAction(mediaId: String): Selection? {
        checkProfile()
        val id = AutoMediaIdContract.parseScopedAction(mediaId) ?: return null
        val snapshot = entries[id.token]?.takeIf { it.profile == activeProfile } ?: return null
        return Selection(snapshot.items, 0, snapshot.parent)
    }

    /** Supplies the newest displayed list for legacy container playback actions. */
    @Synchronized
    fun itemsForParent(parentId: String): List<MediaItem>? {
        checkProfile()
        return entries.values.lastOrNull { it.parent == parentId }?.items
    }

    /** Invalidates all selections when their owning session ends. */
    @Synchronized
    fun clear() {
        entries.clear()
        retainedItemCount = 0L
        activeProfile = null
    }

    private fun checkProfile(): String {
        val current = profile()
        if (current != activeProfile) {
            entries.clear()
            retainedItemCount = 0L
            activeProfile = current
        }
        return current
    }
}
