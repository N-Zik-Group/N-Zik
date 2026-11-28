package app.n_zik.android.playback.services.automotive.browse

import app.n_zik.android.core.database.SavedPlaylistSongMetadata
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.session.AutoMediaIdContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

internal fun isAutoSavedPlaylistParent(parentId: String): Boolean =
    parentId == PlayerServiceModern.ROOT || parentId == PlayerServiceModern.PLAYLIST ||
        parentId.startsWith("PLAYLISTS_") || parentId.startsWith("ID_PLAYLISTS_") ||
        savedPlaylistDetailId(parentId) != null

internal fun savedPlaylistDetailId(parentId: String): Long? {
    val parts = parentId.split('/')
    if (parts.firstOrNull() != PlayerServiceModern.PLAYLIST ||
        !(parts.size == 2 || parts.size == 3 && AutoMediaIdContract.isPlaylistSection(parts[2]))
    ) return null
    return parts[1].toLongOrNull()
}

/** Membership changes are published by Room's playlist observer. */
internal fun Flow<List<SavedPlaylistSongMetadata>>.savedPlaylistMetadataChanges(): Flow<Unit> = flow {
    var previous = emptyMap<String, SavedPlaylistSongMetadata>()
    distinctUntilChanged().collect { current ->
        val hasMetadataChange = current.any { song -> previous[song.id]?.let { it != song } == true }
        previous = current.associateBy(SavedPlaylistSongMetadata::id)
        if (hasMetadataChange) emit(Unit)
    }
}

/** Notification-driven reloads do not constitute a new opening of an existing subscription. */
internal class AutoPlaylistVisits<Browser> {
    private val subscriptions = mutableMapOf<Pair<Browser, Long>, MutableSet<String>>()

    @Synchronized
    fun enter(browser: Browser, parentId: String): Long? {
        val playlistId = savedPlaylistDetailId(parentId) ?: return null
        if (AutoMediaIdContract.isDevicePlaylistSection(parentId.substringAfterLast('/'))) return null
        val parents = subscriptions.getOrPut(browser to playlistId) { mutableSetOf() }
        val first = parents.isEmpty()
        parents += parentId
        return playlistId.takeIf { first }
    }

    @Synchronized
    fun contains(browser: Browser, parentId: String): Boolean {
        val playlistId = savedPlaylistDetailId(parentId) ?: return false
        return subscriptions[browser to playlistId]?.contains(parentId) == true
    }

    @Synchronized
    fun leave(browser: Browser, parentId: String) {
        val playlistId = savedPlaylistDetailId(parentId) ?: return
        val key = browser to playlistId
        val parents = subscriptions[key] ?: return
        parents -= parentId
        if (parents.isEmpty()) subscriptions.remove(key)
    }

    @Synchronized
    fun leave(browser: Browser) {
        subscriptions.keys.removeAll { it.first == browser }
    }
}
