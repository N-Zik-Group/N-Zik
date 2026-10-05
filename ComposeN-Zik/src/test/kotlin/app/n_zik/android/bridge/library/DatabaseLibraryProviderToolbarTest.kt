package app.n_zik.android.bridge.library

import android.app.Application
import android.content.Context
import app.it.fast4x.rimusic.utils.setActiveProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONArray

/**
 * Contract §10.1 (since 1.8.0, feature `library.toolbar`): [DatabaseLibraryProvider.songsToolbar]
 * on real preferences — the toolbar the phone's tabs actually serve: the saved order kept to the
 * tab's available buttons (the missing ones appended in their default order), the hidden buttons
 * dropped, the locked buttons always kept, the tab's exclusions respected. It also pins the file
 * the toolbar is read from: the literal `preferences` file the phone's toolbar dialog writes,
 * not the profile-aware `preferences` accessor (a non-default profile would read a file the
 * phone never writes).
 *
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DatabaseLibraryProviderToolbarTest {

    private lateinit var provider: DatabaseLibraryProvider
    private lateinit var app: Application

    /** The literal `preferences` file — the one the phone's home toolbars read and write. */
    private val prefs: android.content.SharedPreferences
        get() = app.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        provider = DatabaseLibraryProvider(app)
    }

    private fun orderOf(vararg ids: String): String {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        return array.toString()
    }

    @Test
    fun `an untouched chip serves its tab buttons in their default order`() = runTest {
        assertEquals(
            listOf("sort", "position_lock", "match", "search", "sync_ytm_likes", "locator",
                "download_all", "delete_downloads", "shuffle", "smart_shuffle", "item_selector",
                "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
                "import_menu", "export_dialog", "update", "smart_trash"),
            provider.songsToolbar(SongFilter.ALL),
        )
    }

    @Test
    fun `the saved order of a chip is served, its hidden buttons dropped`() = runTest {
        // "shuffle" hidden on the all chip, a saved order with "locator" first: the served
        // toolbar starts with "locator" and lists the rest, "shuffle" gone
        prefs.edit()
            .putString("homeSongsToolbarOrder", orderOf("locator", "search", "shuffle"))
            .putBoolean("all_ts_shuffle", false)
            .commit()

        val served = provider.songsToolbar(SongFilter.ALL)
        assertFalse("shuffle" in served)
        assertEquals(listOf("locator", "search"), served.take(2))
        // The rest of the tab's buttons follow in their default order
        val rest = listOf("sort", "position_lock", "match", "sync_ytm_likes",
            "download_all", "delete_downloads", "smart_shuffle", "item_selector",
            "play_next", "enqueue", "add_to_favorite", "add_to_playlist",
            "import_menu", "export_dialog", "update", "smart_trash")
        assertEquals(rest, served.drop(2))
    }

    @Test
    fun `the locked buttons are served even when their toggle is off`() = runTest {
        prefs.edit()
            .putBoolean("favs_ts_position_lock", false)
            .putBoolean("favs_ts_match", false)
            .commit()

        val served = provider.songsToolbar(SongFilter.LIKED)
        // JUnit 4: the message goes first
        assertTrue("position_lock is locked: it stays", "position_lock" in served)
        assertTrue("match is locked: it stays", "match" in served)
    }

    @Test
    fun `the top chip serves its tab buttons without position_lock`() = runTest {
        val served = provider.songsToolbar(SongFilter.TOP)

        assertEquals("the top tab has 16 of the 20 buttons", 16, served.size)
        assertFalse("position_lock" in served)
        assertFalse("import_menu" in served)
        assertFalse("export_cache" in served)
        assertFalse("sync_ytm_likes" in served)
    }

    @Test
    fun `the ondevice chip serves only its tab buttons`() = runTest {
        val served = provider.songsToolbar(SongFilter.LOCAL)

        assertEquals("the OnDevice tab has 11 of the 20 buttons", 11, served.size)
        for (excluded in setOf("import_menu", "export_dialog", "export_cache", "smart_trash",
            "match", "download_all", "delete_downloads", "sync_ytm_likes", "update")) {
            assertFalse("$excluded is not on the OnDevice tab", excluded in served)
        }
        assertTrue("position_lock is on the OnDevice tab", "position_lock" in served)
    }

    @Test
    fun `the toolbar is read from the literal preferences file, not the profile-aware one`() = runTest {
        // A non-default profile: the profile-aware accessor resolves to `preferences_beta`, the
        // phone's home toolbars keep using the literal `preferences` file
        setActiveProfile("beta", app)
        val profilePrefs = app.getSharedPreferences("preferences_beta", Context.MODE_PRIVATE)

        // The phone's real toolbar (the literal file): "locator" first on the all chip
        prefs.edit().putString("homeSongsToolbarOrder", orderOf("locator", "search")).commit()
        // A decoy order in the profile file: if the provider read the profile-aware file, it would
        // serve this one instead
        profilePrefs.edit().putString("homeSongsToolbarOrder", orderOf("smart_trash", "enqueue")).commit()

        val served = provider.songsToolbar(SongFilter.ALL)
        // "locator" first: the literal file's order, not the profile file's decoy ("smart_trash"
        // first). The message goes first — JUnit 4 has no trailing-message overload
        assertEquals("the literal file's order is served, the profile file's is not", listOf("locator", "search"), served.take(2))
    }
}
