package app.n_zik.android.components.settings.ui

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.R
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.enums.MiniPlayerButton
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.showButtonPlayerDiscoverKey

internal enum class ToolbarAction(val id: String, val label: Int, val icon: Int) {
    BOOKMARK("bookmark", R.string.library_action_bookmark, R.drawable.bookmark_outline),
    FOLLOW("follow", R.string.artist_action_follow, R.drawable.artist),
    DOWNLOAD("download", R.string.downloads_action_download_all, R.drawable.download),
    DELETE_DOWNLOADS("deleteDownloads", R.string.downloads_action_remove_all, R.drawable.downloaded),
    SHUFFLE("shuffle", R.string.player_action_shuffle_hint, R.drawable.shuffle),
    RADIO("radio", R.string.radio_action_start_hint, R.drawable.radio),
    LOCATE("locate", R.string.player_action_locate_current_song, R.drawable.locate),
    SELECT("select", R.string.common_action_select, R.drawable.checked_filled),
    CHANGE_TITLE("changeTitle", R.string.metadata_action_change_title, R.drawable.title_edit),
    CHANGE_ARTISTS("changeArtists", R.string.metadata_action_change_artists, R.drawable.artists_edit),
    CHANGE_PLAYLIST_BROWSE_ID("changePlaylistBrowseId", R.string.metadata_playlist_action_change_browse_id, R.drawable.title_edit),
    CHANGE_COVER("changeCover", R.string.metadata_action_change_cover, R.drawable.cover_edit),
    PLAY_NEXT("playNext", R.string.player_action_play_next, R.drawable.play_skip_forward),
    ENQUEUE("enqueue", R.string.player_action_enqueue, R.drawable.enqueue),
    ADD_TO_PLAYLIST("addToPlaylist", R.string.playlist_actions_title, R.drawable.playlist),
    FAVORITES("favorites", R.string.library_collection_favorites_action_manage, R.drawable.heart),
    SEARCH("search", R.string.search_title, R.drawable.search_circle),
    NOTIFICATIONS("notifications", R.string.notification_inbox_title, R.drawable.notification2),
    PROFILE_MENU("profileMenu", R.string.profile_title, R.drawable.person),
    COLLAPSE_PLAYER("collapsePlayer", R.string.common_action_close, R.drawable.chevron_down),
    AUDIO_VIDEO("audioVideo", R.string.settings_toolbar_audio_video, R.drawable.video),
    MENU("menu", R.string.common_menu_label, R.drawable.ellipsis_vertical),
    DISCOVER("discover", MiniPlayerButton.Discover),
    POSITION_LOCK("positionLock", R.string.playlist_reorder_lock_toggle_label, R.drawable.locked),
    REPEAT("repeat", MiniPlayerButton.Repeat),
    REMOVE("remove", R.string.player_queue_action_remove, R.drawable.trash),
    EXPORT("export", R.string.playlist_action_export, R.drawable.export_playlist),
    SORT("sort", R.string.library_sort_order_title, R.drawable.arrow_up);

    constructor(id: String, button: MiniPlayerButton) : this(id, button.labelRes, button.iconRes)
}

private val onlinePlaylistActions = listOf(
    ToolbarAction.BOOKMARK, ToolbarAction.SEARCH, ToolbarAction.SHUFFLE, ToolbarAction.RADIO,
    ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE,
    ToolbarAction.ADD_TO_PLAYLIST, ToolbarAction.DOWNLOAD, ToolbarAction.DELETE_DOWNLOADS, ToolbarAction.SELECT, ToolbarAction.FAVORITES,
)

private val playlistAndPodcastOverflowActions = setOf(ToolbarAction.PLAY_NEXT, ToolbarAction.FAVORITES, ToolbarAction.SELECT)

internal enum class ToolbarSurface(
    val id: String,
    val label: Int,
    val actions: List<ToolbarAction>,
    val locked: Set<ToolbarAction> = emptySet(),
    val defaultHidden: Set<ToolbarAction> = emptySet(),
    val overflowActions: Set<ToolbarAction> = emptySet(),
) {
    ALBUM_ONLINE("albumOnline", R.string.search_online, listOf(
        ToolbarAction.BOOKMARK, ToolbarAction.SEARCH, ToolbarAction.LOCATE, ToolbarAction.SHUFFLE, ToolbarAction.RADIO,
        ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE, ToolbarAction.SELECT, ToolbarAction.FAVORITES, ToolbarAction.ADD_TO_PLAYLIST,
        ToolbarAction.DOWNLOAD, ToolbarAction.DELETE_DOWNLOADS, ToolbarAction.CHANGE_TITLE, ToolbarAction.CHANGE_ARTISTS, ToolbarAction.CHANGE_COVER,
    )),
    ALBUM_DEVICE("albumDevice", R.string.library_on_device_title, listOf(
        ToolbarAction.BOOKMARK, ToolbarAction.SEARCH, ToolbarAction.SHUFFLE, ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE, ToolbarAction.SELECT,
        ToolbarAction.ADD_TO_PLAYLIST,
    )),
    ARTIST_OVERVIEW("artistOverview", R.string.artist_overview_title, listOf(
        ToolbarAction.FOLLOW, ToolbarAction.SEARCH, ToolbarAction.LOCATE, ToolbarAction.SHUFFLE, ToolbarAction.RADIO, ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE, ToolbarAction.SELECT,
        ToolbarAction.FAVORITES, ToolbarAction.ADD_TO_PLAYLIST, ToolbarAction.DOWNLOAD, ToolbarAction.DELETE_DOWNLOADS,
    )),
    ARTIST_LIBRARY("artistLibrary", R.string.library_title, listOf(
        ToolbarAction.FOLLOW, ToolbarAction.SEARCH, ToolbarAction.LOCATE, ToolbarAction.SHUFFLE, ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE, ToolbarAction.SELECT,
        ToolbarAction.FAVORITES, ToolbarAction.ADD_TO_PLAYLIST, ToolbarAction.DOWNLOAD, ToolbarAction.DELETE_DOWNLOADS,
    )),
    ARTIST_DEVICE("artistDevice", R.string.library_on_device_title, listOf(
        ToolbarAction.FOLLOW, ToolbarAction.SEARCH, ToolbarAction.SHUFFLE, ToolbarAction.PLAY_NEXT, ToolbarAction.ENQUEUE, ToolbarAction.SELECT,
        ToolbarAction.ADD_TO_PLAYLIST,
    )),
    ONLINE_PLAYLIST("onlinePlaylist", R.string.settings_toolbar_online_playlist, onlinePlaylistActions + listOf(
        ToolbarAction.CHANGE_TITLE, ToolbarAction.CHANGE_ARTISTS, ToolbarAction.CHANGE_PLAYLIST_BROWSE_ID,
    ), overflowActions = playlistAndPodcastOverflowActions + setOf(
        ToolbarAction.CHANGE_TITLE, ToolbarAction.CHANGE_ARTISTS, ToolbarAction.CHANGE_PLAYLIST_BROWSE_ID,
    )),
    PODCAST("podcast", R.string.settings_toolbar_online_podcast, onlinePlaylistActions,
        overflowActions = playlistAndPodcastOverflowActions),
    HEADER("header", R.string.settings_toolbar_global_header,
        listOf(ToolbarAction.NOTIFICATIONS, ToolbarAction.SEARCH, ToolbarAction.PROFILE_MENU), locked = setOf(ToolbarAction.PROFILE_MENU)),
    PLAYER_TOP("playerTop", R.string.settings_toolbar_player_top,
        listOf(ToolbarAction.COLLAPSE_PLAYER, ToolbarAction.AUDIO_VIDEO, ToolbarAction.MENU),
        locked = setOf(ToolbarAction.COLLAPSE_PLAYER, ToolbarAction.MENU)),
    QUEUE("queue", R.string.settings_toolbar_queue_action_bar, listOf(
        ToolbarAction.LOCATE, ToolbarAction.SEARCH, ToolbarAction.DISCOVER, ToolbarAction.POSITION_LOCK,
        ToolbarAction.REPEAT, ToolbarAction.SHUFFLE, ToolbarAction.SELECT, ToolbarAction.FAVORITES,
        ToolbarAction.ADD_TO_PLAYLIST, ToolbarAction.DOWNLOAD, ToolbarAction.DELETE_DOWNLOADS,
        ToolbarAction.EXPORT, ToolbarAction.REMOVE,
    ), defaultHidden = setOf(ToolbarAction.DISCOVER)),
    HISTORY("history", R.string.library_history_title, listOf(ToolbarAction.SORT));

    val preferencePrefix get() = "customToolbar.$id."
    val fixedFirst get() = actions.firstOrNull()?.takeIf { it == ToolbarAction.BOOKMARK || it == ToolbarAction.FOLLOW }
    val editableActions get() = actions.filterNot { it == fixedFirst }
    val orderKey get() = "${preferencePrefix}order"
    val visibilityPrefix get() = "${preferencePrefix}visible."
    val ids get() = actions.map(ToolbarAction::id)
    val preferenceKeys get() = listOf(orderKey) + actions.map(::visibilityKey)

    fun visibilityKey(action: ToolbarAction) = visibilityPrefix + action.id
    fun defaultVisible(action: ToolbarAction) = action !in defaultHidden
    fun legacyVisibilityKey(action: ToolbarAction): String? =
        showButtonPlayerDiscoverKey.takeIf { this == QUEUE && action == ToolbarAction.DISCOVER }
    fun defaultVisible(prefs: SharedPreferences, action: ToolbarAction): Boolean =
        legacyVisibilityKey(action)?.let { prefs.getBoolean(it, defaultVisible(action)) } ?: defaultVisible(action)

    fun resolveOrder(serialized: String): List<ToolbarAction> {
        val byId = actions.associateBy(ToolbarAction::id)
        val ordered = DockToolbarPreferences.orderedIds(ids, serialized).map(byId::getValue)
        val first = fixedFirst ?: return ordered
        return listOf(first) + ordered.filterNot { it == first }
    }

    fun readVisibleActions(prefs: SharedPreferences): List<ToolbarAction> {
        return resolveOrder(prefs.getString(orderKey, "").orEmpty()).filter { action ->
            action == fixedFirst || action in locked || prefs.getBoolean(visibilityKey(action), defaultVisible(prefs, action))
        }
    }
}

/** Reads the active profile. Availability only affects rendering and leaves the saved order intact. */
@Composable
internal fun ToolbarSurface.visibleActions(): List<ToolbarAction> {
    val prefs = LocalContext.current.preferences
    val visible = remember(prefs, this) { mutableStateOf(readVisibleActions(prefs)) }
    DisposableEffect(prefs, this) {
        val legacyKeys = actions.mapNotNull(::legacyVisibilityKey).toSet()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == null || key.startsWith(preferencePrefix) || key in legacyKeys) {
                visible.value = readVisibleActions(changedPrefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        visible.value = readVisibleActions(prefs)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return visible.value
}

/** Projects an action map into the configured order, dropping absent and hidden controls. */
@Composable
internal fun <T : Any> ToolbarSurface.ordered(actions: Map<ToolbarAction, T>): List<T> =
    visibleActions().mapNotNull(actions::get)
