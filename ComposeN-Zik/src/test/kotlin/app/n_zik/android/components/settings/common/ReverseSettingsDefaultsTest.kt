package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ContentType
import app.it.fast4x.rimusic.enums.Countries
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.enums.SongSortBy
import app.it.fast4x.rimusic.enums.SortOrder
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.utils.Preference
import app.it.fast4x.rimusic.utils.currentVisualizerKey
import app.it.fast4x.rimusic.utils.filterContentTypeKey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.floatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetXkey
import app.it.fast4x.rimusic.utils.multiFloatActionIconOffsetYkey
import app.it.fast4x.rimusic.utils.otherLanguageAppAlbumKey
import app.it.fast4x.rimusic.utils.otherLanguageAppArtistKey
import app.it.fast4x.rimusic.utils.otherLanguageAppPlaylistKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.selectedCountryCodeKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.bridge.AutoStopSettings
import app.n_zik.android.bridge.BridgeServerController
import app.n_zik.android.components.search.SearchHistoryStore
import app.n_zik.android.components.settings.ai.applyAiDefaults
import app.n_zik.android.components.settings.appearance.applyAppearanceDefaults
import app.n_zik.android.components.settings.data.applyDataDefaults
import app.n_zik.android.components.settings.network.applyNetworkDefaults
import app.n_zik.android.components.settings.other.applyOtherDefaults
import app.n_zik.android.components.settings.ui.applyUiDefaults
import app.n_zik.android.utils.playlist.PlaylistInsertionPosition
import app.n_zik.android.utils.playlist.PlaylistInsertionPreferences
import app.n_zik.android.utils.playlist.PlaylistRefreshPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ReverseSettingsDefaultsTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val prefs get() = context.preferences

    @Before fun setUp() {
        listOf("preferences", "preferences_reverse-reset", "preferences_other-reset", "app_settings").forEach {
            store(it).edit().clear().commit()
        }
        setActiveProfile("reverse-reset", context)
    }

    @After fun tearDown() { setActiveProfile("default", context) }

    private val liveRegistry: List<Preference.Key<*>> = listOf(
        Preference.HOME_ARTIST_ITEM_SIZE,
        Preference.HOME_ALBUM_ITEM_SIZE,
        Preference.HOME_LIBRARY_ITEM_SIZE,
        Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD,
        Preference.HOME_SONGS_SORT_BY,
        Preference.HOME_SONGS_FAVORITES_SORT_BY,
        Preference.HOME_SONGS_OFFLINE_SORT_BY,
        Preference.HOME_SONGS_DOWNLOADED_SORT_BY,
        Preference.HOME_SONGS_TOP_SORT_BY,
        Preference.HOME_SONGS_DISLIKED_SORT_BY,
        Preference.HOME_ON_DEVICE_SONGS_SORT_BY,
        Preference.HOME_ARTISTS_FAVORITES_SORT_BY,
        Preference.HOME_ARTISTS_LIBRARY_SORT_BY,
        Preference.HOME_ARTISTS_DISLIKED_SORT_BY,
        Preference.HOME_ALBUMS_FAVORITES_SORT_BY,
        Preference.HOME_ALBUMS_LIBRARY_SORT_BY,
        Preference.HOME_ALBUMS_DISLIKED_SORT_BY,
        Preference.HOME_LIBRARY_PLAYLIST_SORT_BY,
        Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_BY,
        Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_BY,
        Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_BY,
        Preference.HISTORY_SORT_BY,
        Preference.HOME_SONGS_SORT_ORDER,
        Preference.HOME_SONGS_FAVORITES_SORT_ORDER,
        Preference.HOME_SONGS_OFFLINE_SORT_ORDER,
        Preference.HOME_SONGS_DOWNLOADED_SORT_ORDER,
        Preference.HOME_SONGS_TOP_SORT_ORDER,
        Preference.HOME_SONGS_DISLIKED_SORT_ORDER,
        Preference.HOME_ON_DEVICE_SONGS_SORT_ORDER,
        Preference.HOME_ARTISTS_FAVORITES_SORT_ORDER,
        Preference.HOME_ARTISTS_LIBRARY_SORT_ORDER,
        Preference.HOME_ARTISTS_DISLIKED_SORT_ORDER,
        Preference.HOME_ALBUMS_FAVORITES_SORT_ORDER,
        Preference.HOME_ALBUMS_LIBRARY_SORT_ORDER,
        Preference.HOME_ALBUMS_DISLIKED_SORT_ORDER,
        Preference.HOME_LIBRARY_PLAYLIST_SORT_ORDER,
        Preference.HOME_LIBRARY_YT_PLAYLIST_SORT_ORDER,
        Preference.HOME_LIBRARY_PINNED_PLAYLIST_SORT_ORDER,
        Preference.HOME_LIBRARY_MONTHLY_PLAYLIST_SORT_ORDER,
        Preference.HISTORY_SORT_ORDER,
        Preference.SEARCH_RESULT_GRID_STATES,
    )

    private fun readRegistry(preferences: SharedPreferences, setting: Preference.Key<*>): String? =
        when (val default = setting.default) {
            is Enum<*> -> preferences.getString(setting.key, default.name)
            is String -> preferences.getString(setting.key, default)
            else -> error("Unexpected reader type for ${setting.key}")
        }

    @Test fun `interface restores every live registry fallback and mounted selected sort in the active profile`() {
        assertEquals(41, liveRegistry.size)
        val fresh = liveRegistry.associate { it.key to readRegistry(prefs, it) }
        val untouched = store("preferences_other-reset")
        val editor = prefs.edit()
        liveRegistry.forEach { setting ->
            val default = setting.default
            val changed = if (default is Enum<*>) default.declaringJavaClass.enumConstants
                .first { it != default }.name else "changed"
            editor.putString(setting.key, changed)
            untouched.edit().putString(setting.key, "other-profile").commit()
            store("preferences").edit().putString(setting.key, "principal-profile").commit()
        }
        editor.putString("HomeArtistsSortBy", "retired").putString("miniPlayerSlot1", "retired-slot")
            .putString("playlist-data", "saved playlists").commit()
        lateinit var mountedSort: MutableState<SongSortBy>
        compose.setContent { mountedSort = Preference.remember(Preference.HOME_SONGS_FAVORITES_SORT_BY) }
        assertTrue(mountedSort.value != SongSortBy.DateLiked)
        val otherBefore = untouched.all.toMap()
        val principalBefore = liveRegistry.associate { it.key to store("preferences").getString(it.key, null) }

        repeat(2) {
            compose.runOnIdle { applyUiDefaults(context) }
            compose.waitForIdle()
            assertEquals(SongSortBy.DateLiked, mountedSort.value)
            liveRegistry.forEach { assertEquals(it.key, fresh[it.key], readRegistry(prefs, it)) }
            assertEquals(otherBefore, untouched.all)
            assertEquals(principalBefore, liveRegistry.associate { setting -> setting.key to store("preferences").getString(setting.key, null) })
            assertEquals("retired", prefs.getString("HomeArtistsSortBy", null))
            assertEquals("retired-slot", prefs.getString("miniPlayerSlot1", null))
            assertEquals("saved playlists", prefs.getString("playlist-data", null))
        }
    }

    @Test fun `data restores all page enablement choices while preserving their recorded query arrays`() {
        val fresh = SearchHistoryStore.pages.associateWith { SearchHistoryStore.isEnabled(prefs, it) }
        SearchHistoryStore.pages.forEach { page ->
            prefs.edit().putBoolean(SearchHistoryStore.enabledKey(page), false)
                .putString("searchHistory_$page", "[\"saved $page\"]").commit()
            store("preferences_other-reset").edit().putBoolean(SearchHistoryStore.enabledKey(page), false).commit()
        }
        val untouched = store("preferences_other-reset").all.toMap()
        repeat(2) {
            applyDataDefaults(context)
            SearchHistoryStore.pages.forEach { page ->
                assertEquals(page, fresh[page], SearchHistoryStore.isEnabled(prefs, page))
                assertEquals(listOf("saved $page"), SearchHistoryStore.all(prefs, page))
            }
            assertEquals(untouched, store("preferences_other-reset").all)
        }
    }

    @Test fun `playlist settings restore contextual readers without removing playlist data or similar keys`() {
        val insertion = PlaylistInsertionPreferences(context)
        val refresh = PlaylistRefreshPreferences(context)
        val sources = listOf(
            Playlist(id = 7, name = "Remote", browseId = "VLPLremote", isEditable = false),
            Playlist(id = -2, name = "Local"),
        )
        val freshRefresh = sources.associate { it.id to refresh.get(it) }
        sources.forEach {
            insertion.set(it.id, PlaylistInsertionPosition.Beginning)
            refresh.set(it.id, !freshRefresh.getValue(it.id))
            prefs.edit().putString("PlaylistSongsSortBy_${it.id}", "Artist")
                .putString("PlaylistSongsSortOrder_${it.id}", "Descending").commit()
        }
        val saved = mapOf(
            "playlist_insertion_position_7_notes" to "notes",
            "playlist_refresh_on_open_7_backup" to "metadata",
            "PlaylistSongsSortBy_custom" to "unrelated",
            "playlist-data" to "playlist contents",
            "queue" to "saved queue",
        )
        saved.forEach { (key, value) -> prefs.edit().putString(key, value).commit() }
        store("preferences_other-reset").edit().putString("PlaylistSongsSortBy_7", "Artist").commit()
        repeat(2) {
            resetInterfaceReaderDefaults(prefs)
            resetPlaylistRefreshDefaults(prefs)
            sources.forEach {
                assertEquals(PlaylistInsertionPosition.End, insertion.get(it.id))
                assertEquals(freshRefresh[it.id], refresh.get(it))
                assertFalse(prefs.contains("PlaylistSongsSortBy_${it.id}"))
                assertEquals(SortOrder.Ascending, prefs.getEnum("PlaylistSongsSortOrder_${it.id}", SortOrder.Ascending))
            }
            saved.forEach { (key, value) -> assertEquals(value, prefs.getString(key, null)) }
            assertEquals("Artist", store("preferences_other-reset").getString("PlaylistSongsSortBy_7", null))
        }
    }

    @Test fun `network reset updates loaded bridge flow and storage without removing paired devices`() {
        BridgeServerController.setAutoStopNoClient(context, 60)
        BridgeServerController.setAutoStopInactivity(context, 30)
        prefs.edit().putString("paired_devices", "saved-pairings").commit()
        store("preferences_other-reset").edit().putInt("bridgeAutoStopNoClientMinutes", 60).commit()
        repeat(2) {
            applyNetworkDefaults(context)
            assertEquals(AutoStopSettings(), BridgeServerController.autoStopSettings.value)
            assertEquals(15, prefs.getInt("bridgeAutoStopNoClientMinutes", -1))
            assertEquals(0, prefs.getInt("bridgeAutoStopInactivityMinutes", -1))
            assertEquals("saved-pairings", prefs.getString("paired_devices", null))
            assertEquals(60, store("preferences_other-reset").getInt("bridgeAutoStopNoClientMinutes", -1))
        }
    }

    private fun isolatedReaderSnapshot(preferences: SharedPreferences): Map<String, Any> = mapOf(
        currentVisualizerKey to preferences.getInt(currentVisualizerKey, 0),
        otherLanguageAppAlbumKey to preferences.getEnum(otherLanguageAppAlbumKey, Languages.System),
        otherLanguageAppArtistKey to preferences.getEnum(otherLanguageAppArtistKey, Languages.System),
        otherLanguageAppPlaylistKey to preferences.getEnum(otherLanguageAppPlaylistKey, Languages.System),
        selectedCountryCodeKey to preferences.getEnum(selectedCountryCodeKey, Countries.ZZ),
        filterContentTypeKey to preferences.getEnum(filterContentTypeKey, ContentType.All),
        floatActionIconOffsetXkey to preferences.getFloat(floatActionIconOffsetXkey, 0F),
        floatActionIconOffsetYkey to preferences.getFloat(floatActionIconOffsetYkey, 0F),
        multiFloatActionIconOffsetXkey to preferences.getFloat(multiFloatActionIconOffsetXkey, 0F),
        multiFloatActionIconOffsetYkey to preferences.getFloat(multiFloatActionIconOffsetYkey, 0F),
    )

    @Test fun `feature category resets restore isolated readers without touching another profile`() {
        val firstRun = isolatedReaderSnapshot(prefs)
        val other = store("preferences_other-reset")
        for (target in listOf(prefs, other)) {
            val editor = target.edit()
            firstRun.forEach { (key, value) ->
                when (value) {
                    is Enum<*> -> editor.putString(key, value.declaringJavaClass.enumConstants.first { it != value }.name)
                    is Float -> editor.putFloat(key, 125F)
                    is Int -> editor.putInt(key, 7)
                }
            }
            editor.putString("ytCookie", "saved account").putString("queue", "saved queue").commit()
        }
        val otherBefore = other.all.toMap()
        assertTrue(firstRun != isolatedReaderSnapshot(prefs))
        repeat(2) {
            applyUiDefaults(context)
            applyAppearanceDefaults(context)
            applyOtherDefaults(context)
            applyAiDefaults(context)
            assertEquals(firstRun, isolatedReaderSnapshot(prefs))
            assertEquals(otherBefore, other.all)
            assertEquals("saved account", prefs.getString("ytCookie", null))
            assertEquals("saved queue", prefs.getString("queue", null))
        }
    }
}
