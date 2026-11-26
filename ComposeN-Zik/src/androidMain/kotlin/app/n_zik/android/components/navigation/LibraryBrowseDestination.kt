package app.n_zik.android.components.navigation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.PlaylistLinkKind
import app.n_zik.android.core.database.playlistLinkKind
import app.n_zik.android.core.ondevice.isDeviceAlbumId
import app.n_zik.android.core.ondevice.isDeviceArtistId
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

private const val ENTITY_ORIGIN_KEY_PREFIX = "old_browse_id_"
private const val PLAYLIST_ORIGIN_KEY_PREFIX = "playlist_original_browse_id_"
internal const val RESET_BROWSE_ALIAS_KEY_PREFIX = "reset_browse_alias_"
internal const val RETIRED_PLAYLIST_BROWSE_KEY_PREFIX = "retired_playlist_browse_id_"

internal enum class LibraryBrowseKind { Album, Artist, Playlist, Podcast }

internal data class LibraryBrowseDestination(val browseId: String, val localPlaylistId: Long? = null)

private fun isManuallyReboundBrowseId(id: String): Boolean =
    id.startsWith(MODIFIED_PREFIX) ||
        (!isDeviceAlbumId(id) && id.startsWith("LOCAL_ALBUM_")) ||
        (!isDeviceArtistId(id) && id.startsWith("LOCAL_ARTIST_"))

internal fun editedBrowseCandidates(browseId: String, entries: Map<String, *>): List<String> {
    val origin = entries["$ENTITY_ORIGIN_KEY_PREFIX$browseId"] as? String ?: browseId
    return entries.mapNotNull { (key, value) ->
        if (!key.startsWith(ENTITY_ORIGIN_KEY_PREFIX) || value != origin) return@mapNotNull null
        key.removePrefix(ENTITY_ORIGIN_KEY_PREFIX).takeIf(::isManuallyReboundBrowseId)
    }
}

internal fun editedPlaylistRows(browseId: String, entries: Map<String, *>): List<Long> {
    val normalizedId = browseId.removePrefix("VL")
    return entries.mapNotNull { (key, value) ->
        if (!key.startsWith(PLAYLIST_ORIGIN_KEY_PREFIX) || value !is String ||
            value.removePrefix("VL") != normalizedId
        ) return@mapNotNull null
        key.removePrefix(PLAYLIST_ORIGIN_KEY_PREFIX).toLongOrNull()?.takeIf { it > 0L }
    }
}

internal suspend fun resolveLibraryBrowseDestination(
    browseId: String,
    kind: LibraryBrowseKind,
    entries: Map<String, *>,
): LibraryBrowseDestination {
    val fallback = LibraryBrowseDestination(browseId)
    if (kind == LibraryBrowseKind.Playlist || kind == LibraryBrowseKind.Podcast) {
        val previousRowId = (entries["$RETIRED_PLAYLIST_BROWSE_KEY_PREFIX$browseId"] as? Long)
            ?.takeIf { it > 0L }
        val rowIds = (editedPlaylistRows(browseId, entries) + listOfNotNull(previousRowId)).distinct()
        val rows = rowIds.mapNotNull { Database.playlistTable.findById(it).first() }
        val row = rows.singleOrNull() ?: return fallback
        val linkedId = row.browseId ?: return LibraryBrowseDestination(browseId, row.id)
        return when (playlistLinkKind(linkedId)) {
            PlaylistLinkKind.Local -> LibraryBrowseDestination(browseId, row.id)
            PlaylistLinkKind.YouTubePlaylist -> if (kind == LibraryBrowseKind.Playlist) LibraryBrowseDestination(linkedId) else fallback
            PlaylistLinkKind.YouTubePodcast -> if (kind == LibraryBrowseKind.Podcast) LibraryBrowseDestination(linkedId) else fallback
        }
    }

    suspend fun exists(id: String): Boolean = when (kind) {
        LibraryBrowseKind.Album -> Database.albumTable.findByIdDirect(id) != null
        LibraryBrowseKind.Artist -> Database.artistTable.findByIdDirect(id) != null
        else -> false
    }
    if (isManuallyReboundBrowseId(browseId) && exists(browseId)) return fallback
    val resetOrigin = (entries["$RESET_BROWSE_ALIAS_KEY_PREFIX$browseId"] as? String)
        ?.takeIf { it != browseId && exists(it) }
    val lookupId = resetOrigin ?: browseId
    val targets = editedBrowseCandidates(lookupId, entries).filter { exists(it) }
    return LibraryBrowseDestination(targets.singleOrNull() ?: lookupId)
}

private fun SharedPreferences.observeBrowseAliasChanges() = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key.startsWith(ENTITY_ORIGIN_KEY_PREFIX) || key.startsWith(PLAYLIST_ORIGIN_KEY_PREFIX) ||
            key.startsWith(RESET_BROWSE_ALIAS_KEY_PREFIX) || key.startsWith(RETIRED_PLAYLIST_BROWSE_KEY_PREFIX)
        ) trySend(Unit)
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.conflate()

/** Resolves only identities explicitly changed in the active profile before a detail loads. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberLibraryBrowseDestination(browseId: String, kind: LibraryBrowseKind): LibraryBrowseDestination? {
    if (browseId.isBlank() || isDeviceAlbumId(browseId) || isDeviceArtistId(browseId)) {
        return LibraryBrowseDestination(browseId)
    }
    val context: Context = LocalContext.current.applicationContext
    val preferences = context.preferences
    return key(preferences, browseId, kind) {
        val resolved by remember {
            preferences.observeBrowseAliasChanges().mapLatest {
                val owner = ProfileWorkOwner.capture(context) ?: return@mapLatest LibraryBrowseDestination(browseId)
                val result = resolveLibraryBrowseDestination(browseId, kind, preferences.all)
                result.takeIf { ProfileWorkOwner.isBound(context, owner) }
            }.distinctUntilChanged()
        }.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
        resolved
    }
}
