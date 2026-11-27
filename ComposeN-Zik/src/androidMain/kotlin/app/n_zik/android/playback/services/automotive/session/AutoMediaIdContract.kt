package app.n_zik.android.playback.services.automotive.session

import android.net.Uri

/** Navigation and playback IDs shared by the automotive browser and session. */
object AutoMediaIdContract {
    private const val PAGE = "AA_PAGE"
    private const val SELECT = "AA_SELECT"
    private const val ACTION = "AA_ACTION"
    private const val PLAY_ALL = "PLAYLIST_PLAY_ALL"
    private const val DEVICE_PLAYLIST_ACTION = "device-playlist:"
    private const val DEVICE_GROUP_ACTION = "device-group:"
    internal const val PLAYLIST_SECTION_ALL = "all"
    internal const val PLAYLIST_SECTION_DEVICE = "on_device"

    internal fun devicePlaylistAction(id: Long): String = "$DEVICE_PLAYLIST_ACTION$id"

    internal fun devicePlaylistFromAction(token: String): Long? =
        token.takeIf { it.startsWith(DEVICE_PLAYLIST_ACTION) }?.removePrefix(DEVICE_PLAYLIST_ACTION)?.toLongOrNull()

    internal fun deviceGroupAction(parentId: String): String = DEVICE_GROUP_ACTION + Uri.encode(parentId)

    internal fun deviceGroupFromAction(token: String): String? =
        token.takeIf { it.startsWith(DEVICE_GROUP_ACTION) }?.removePrefix(DEVICE_GROUP_ACTION)?.let(Uri::decode)

    internal fun isDeviceGroupCategoryAction(mediaId: String): Boolean {
        val action = parsePlaylistShuffle(mediaId) ?: parsePlaylistPlayAll(mediaId) ?: return false
        return when (deviceGroupFromAction(action)) {
            AutoSessionConstants.ID_ALBUMS_ONDEVICE, AutoSessionConstants.ID_ARTISTS_ONDEVICE -> true
            else -> false
        }
    }

    internal fun isPlaylistSection(value: String): Boolean =
        value == PLAYLIST_SECTION_ALL || value == PLAYLIST_SECTION_DEVICE

    /** A song selection within a detail container. */
    data class ParsedSongSelection(val containerId: String, val section: String?, val songId: String)

    /** A page of an encoded browse parent. */
    data class Page(val parentId: String, val index: Int)

    /** A selection bound to one displayed list. */
    data class SelectionId(val token: String, val songId: String, val index: Int = 0)

    data class ScopedAction(val token: String, val shuffle: Boolean)

    /** Browsable continuation; it is never a playable song or queue action. */
    fun searchContinuation(token: String): String {
        require(token.isNotEmpty() && !token.contains('/'))
        return "AA_SEARCH_NEXT/$token"
    }

    fun parseSearchContinuation(mediaId: String): String? =
        parseAction(mediaId, "AA_SEARCH_NEXT")

    fun playlistShuffle(playlistId: String): String = "${AutoSessionConstants.ID_PLAYLIST_SHUFFLE}/$playlistId"

    fun parsePlaylistShuffle(mediaId: String?): String? = parseAction(mediaId, AutoSessionConstants.ID_PLAYLIST_SHUFFLE)

    fun playlistPlayAll(playlistId: String): String = "$PLAY_ALL/$playlistId"

    fun parsePlaylistPlayAll(mediaId: String?): String? = parseAction(mediaId, PLAY_ALL)

    private fun parseAction(mediaId: String?, prefix: String): String? {
        val parts = mediaId?.split('/') ?: return null
        return parts.getOrNull(1)?.takeIf { parts.size == 2 && parts[0] == prefix && it.isNotEmpty() }
    }

    fun isShuffle(mediaId: String): Boolean = parseScopedAction(mediaId)?.shuffle == true ||
        (!mediaId.contains('/') && mediaId.endsWith("_SHUFFLE")) || parsePlaylistShuffle(mediaId) != null

    fun isQueueAction(mediaId: String): Boolean = parseScopedAction(mediaId) != null || isShuffle(mediaId) ||
        (!mediaId.contains('/') && mediaId.endsWith("_PLAY_ALL")) || parsePlaylistPlayAll(mediaId) != null

    fun playAllFor(shuffleId: String): String = parseScopedAction(shuffleId)?.let { scopedAction(it.token, false) }
        ?: parsePlaylistShuffle(shuffleId)?.let(::playlistPlayAll)
        ?: (shuffleId.removeSuffix("_SHUFFLE") + "_PLAY_ALL")

    /** Resolves aggregate library actions; saved legacy YouTube actions mean All. */
    fun playlistCategoryForAction(mediaId: String): String? {
        val parentId = when {
            mediaId.endsWith("_SHUFFLE") -> mediaId.removeSuffix("_SHUFFLE")
            mediaId.endsWith("_PLAY_ALL") -> mediaId.removeSuffix("_PLAY_ALL")
            else -> return null
        }
        return when (parentId) {
            AutoSessionConstants.ID_PLAYLISTS_YT -> AutoSessionConstants.ID_PLAYLISTS_YT_ALL
            AutoSessionConstants.ID_PLAYLISTS_LOCAL,
            AutoSessionConstants.ID_PLAYLISTS_ON_DEVICE,
            AutoSessionConstants.ID_PLAYLISTS_YT_ALL,
            AutoSessionConstants.ID_PLAYLISTS_YT_PLAYLISTS,
            AutoSessionConstants.ID_PLAYLISTS_YT_PODCASTS,
            AutoSessionConstants.ID_PLAYLISTS_PINNED,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_ALL,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_MONTH,
            AutoSessionConstants.ID_PLAYLISTS_REWIND_YEAR -> parentId
            else -> null
        }
    }

    fun page(parentId: String, index: Int): String {
        require(parentId.isNotEmpty() && index >= 0)
        return "$PAGE/${Uri.encode(parentId)}/$index"
    }

    fun parsePage(mediaId: String): Page? {
        val parts = mediaId.split('/')
        if (parts.size != 3 || parts[0] != PAGE || parts[1].isEmpty()) return null
        val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Page(Uri.decode(parts[1]), index)
    }

    fun selection(token: String, songId: String, index: Int = 0): String {
        require(token.isNotEmpty() && !token.contains('/') && songId.isNotEmpty() && index >= 0)
        return "$SELECT/$token/$index/${Uri.encode(songId)}"
    }

    fun parseSelection(mediaId: String): SelectionId? {
        val parts = mediaId.split('/')
        if (parts.size != 4 || parts[0] != SELECT || parts[1].isEmpty() || parts[3].isEmpty()) return null
        val index = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return SelectionId(parts[1], Uri.decode(parts[3]), index)
    }

    fun scopedAction(token: String, shuffle: Boolean): String {
        require(token.isNotEmpty() && !token.contains('/'))
        return "$ACTION/$token/${if (shuffle) "SHUFFLE" else "PLAY_ALL"}"
    }

    fun parseScopedAction(mediaId: String): ScopedAction? {
        val parts = mediaId.split('/')
        if (parts.size != 3 || parts[0] != ACTION || parts[1].isEmpty()) return null
        return when (parts[2]) {
            "SHUFFLE" -> ScopedAction(parts[1], true)
            "PLAY_ALL" -> ScopedAction(parts[1], false)
            else -> null
        }
    }

    fun isScopedAction(mediaId: String): Boolean = mediaId.startsWith("$ACTION/")

    fun isScopedSelection(mediaId: String): Boolean = mediaId.startsWith("$SELECT/")

    fun parseSearchSelection(mediaId: String): String? {
        val parts = mediaId.split('/')
        return parts.getOrNull(1)?.takeIf {
            parts.size == 2 && it.isNotEmpty() && parts[0] in setOf(AutoSessionConstants.ID_SEARCH_SONGS, AutoSessionConstants.ID_SEARCH_VIDEOS)
        }
    }

    fun parseSongSelection(mediaId: String, prefix: String): ParsedSongSelection? {
        val parts = mediaId.split('/')
        if (parts.firstOrNull() != prefix) return null
        return when (parts.size) {
            3 -> ParsedSongSelection(parts[1], null, parts[2])
            4 -> ParsedSongSelection(parts[1], parts[2], parts[3])
            else -> null
        }?.takeIf { it.containerId.isNotEmpty() && it.songId.isNotEmpty() && (it.section == null || it.section.isNotEmpty()) }
    }
}
