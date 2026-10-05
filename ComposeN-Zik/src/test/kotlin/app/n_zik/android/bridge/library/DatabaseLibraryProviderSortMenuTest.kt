package app.n_zik.android.bridge.library

import android.app.Application
import android.content.Context
import app.it.fast4x.rimusic.utils.setActiveProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray

/**
 * Contract §10.1 (since 1.7.3, feature `library.sortMenu`): [DatabaseLibraryProvider.songsSortMenu]
 * on real preferences — the menu the phone's tabs actually read: the saved order with its hidden
 * options dropped (and the rest of the visible options appended), the native order while the
 * user never reordered, the Top chip's periods, the OnDevice chip's own options. It also pins the
 * file the menu is read from: the literal `preferences` file the phone's sort UI uses, not the
 * profile-aware `preferences` accessor (a non-default profile would read a file the phone never
 * writes).
 *
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseLibraryProviderSortMenuTest {

    private lateinit var provider: DatabaseLibraryProvider
    private lateinit var app: Application

    /** The literal `preferences` file — the one the phone's sort menus read and write. */
    private val prefs: android.content.SharedPreferences
        get() = app.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        provider = DatabaseLibraryProvider(app)
    }

    private fun orderOf(vararg names: String): String {
        val array = JSONArray()
        names.forEach { array.put(it) }
        return array.toString()
    }

    private val sortChipNames = listOf(
        "Title", "Artist", "AlbumName", "Duration", "PlayCount",
        "PlayTime", "RelativePlayTime", "DateAdded", "DatePlayed", "DateLiked", "Downloaded", "Custom",
    )

    @Test
    fun `an untouched chip serves its native menu in wire order`() = runTest {
        assertEquals(SongsSortMenu.nativeOrder, provider.songsSortMenu(SongFilter.ALL))
    }

    @Test
    fun `the saved order of a chip is served, its hidden options dropped`() = runTest {
        // "Custom" hidden on the favorites chip, a saved order with it first: the served menu
        // starts with "title" (the first saved visible option) and lists the rest
        prefs.edit()
            .putString("homeSongsFavoritesSortMenuOrder", orderOf("Custom", "Title", "Artist"))
            .putBoolean("favs_sort_Custom_visible", false)
            .commit()

        val menu = provider.songsSortMenu(SongFilter.LIKED)
        // The saved visible options ("Title", then "Artist") keep their saved order, the rest of
        // the visible options follow in their native order
        val rest = SongsSortMenu.nativeOrder.filter { it != "title" && it != "artist" && it != "custom" }
        assertEquals(listOf("title", "artist") + rest, menu)
    }

    @Test
    fun `the top chip serves its periods, the onDevice chip its own menu`() = runTest {
        assertEquals(
            listOf("today", "week", "month", "3months", "6months", "year", "all"),
            provider.songsSortMenu(SongFilter.TOP),
        )
        assertEquals(
            listOf("title", "dateAdded", "artist", "duration", "album"),
            provider.songsSortMenu(SongFilter.LOCAL),
        )
    }

    @Test
    fun `the menu is read from the literal preferences file, not the profile-aware one`() = runTest {
        // A non-default profile: the profile-aware accessor resolves to `preferences_beta`, the
        // phone's sort menus keep using the literal `preferences` file
        setActiveProfile("beta", app)
        val profilePrefs = app.getSharedPreferences("preferences_beta", Context.MODE_PRIVATE)

        // The phone's real menu (the literal file): "Custom" first on the all chip
        prefs.edit().putString("homeSongsAllSortMenuOrder", JSONArray(sortChipNames.reversed()).toString()).commit()
        // A decoy order in the profile file: if the provider read the profile-aware file, it would
        // serve this one instead
        profilePrefs.edit().putString("homeSongsAllSortMenuOrder", JSONArray(sortChipNames).toString()).commit()

        val menu = provider.songsSortMenu(SongFilter.ALL)
        // "custom" first: the literal file's order, not the profile file's decoy ("Title" first).
        // The message goes first — JUnit 4 has no trailing-message overload
        assertEquals("the literal file's order is served, the profile file's is not", SongsSortMenu.nativeOrder.reversed(), menu)
    }
}
