package app.n_zik.android.playback.services.automotive.session

import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants.ID_PLAYLIST_SHUFFLE

/**
 * Contract for the Android Auto SELECTION mediaIds of the browse detail
 * containers (artist / album / playlist detail pages) and the playlist
 * shuffle item, emitted by the browse detail handlers and consumed by
 * [AutoSessionCallback] queue resolution.
 *
 * Detail-page songs carry the full navigation context:
 *   "{prefix}/{containerId}/{songId}"            (artist/album/playlist detail pages)
 *   "{prefix}/{containerId}/{section}/{songId}"  (artist section pages only —
 *                                                 the resolver ignores `section`
 *                                                 for album/playlist)
 *
 * The segment depth is part of the contract: handlers must never emit the bare prefix
 * (e.g. "artist/{songId}") as a song path — that used to produce unresolvable empty
 * queues reported as "Unable to perform the selection" (issue #777).
 *
 * Scope note: the other resolver inputs (QUICK_PICKS, SONGS_*, SEARCH_*, SONG,
 * SEARCHED) have their own ad-hoc segment depths and are deliberately NOT part of
 * this contract. Song mediaId emission itself is the plain "{path}/{songId}"
 * concatenation in [SessionMediaItemMapper]; the emitted shapes are pinned by
 * `AutoBrowseDetailHandlersMediaIdTest`.
 */
object AutoMediaIdContract {

    /** A song selection parsed from a detail-page mediaId. */
    data class ParsedSongSelection(
        val containerId: String,
        val section: String?,
        val songId: String,
    )

    /**
     * MediaId of the "Shuffle" item shown on a playlist detail page.
     *
     * @param playlistId The playlist id (local numeric id or YTM browse id).
     */
    fun playlistShuffle(playlistId: String): String = "$ID_PLAYLIST_SHUFFLE/$playlistId"

    /**
     * Parses a playlist-shuffle mediaId and returns its playlist id.
     *
     * @return null when [mediaId] is not a well-formed playlist-shuffle id.
     */
    fun parsePlaylistShuffle(mediaId: String?): String? {
        if (mediaId == null) return null
        val parts = mediaId.split("/")
        if (parts.size != 2 || parts[0] != ID_PLAYLIST_SHUFFLE || parts[1].isEmpty()) return null
        return parts[1]
    }

    /**
     * Parses a detail-page song mediaId of the form
     * "{prefix}/{containerId}/{songId}" or "{prefix}/{containerId}/{section}/{songId}".
     *
     * @param mediaId The mediaId of the selected song.
     * @param prefix Expected container prefix (e.g. "artist", "album", "playlist").
     * @return null when [mediaId] is malformed for the given prefix (wrong depth,
     *          wrong prefix, or an empty container/section/song segment).
     */
    fun parseSongSelection(mediaId: String, prefix: String): ParsedSongSelection? {
        val parts = mediaId.split("/")
        if (parts.firstOrNull() != prefix) return null
        return when (parts.size) {
            3 -> ParsedSongSelection(parts[1], null, parts[2])
            4 -> ParsedSongSelection(parts[1], parts[2], parts[3])
            else -> null
        }?.takeIf { it.containerId.isNotEmpty() && it.songId.isNotEmpty() && (it.section == null || it.section.isNotEmpty()) }
    }
}
