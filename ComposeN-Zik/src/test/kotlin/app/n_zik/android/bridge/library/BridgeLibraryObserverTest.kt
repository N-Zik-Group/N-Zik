package app.n_zik.android.bridge.library

import android.app.Application
import android.content.Context
import app.n_zik.android.bridge.state.BridgeStateHub
import app.n_zik.android.bridge.state.LibraryChangedMessage
import app.n_zik.android.core.database.Database
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Contract §10.3 (since 1.7.3, feature `library.live`): what the [BridgeLibraryObserver] pushes —
 * a `libraryChanged` per library write (through [Database.libraryWriteNotifier]) and per songs
 * sort-menu preference edit — and what it never pushes.
 * JUnit 4 + [RobolectricTestRunner] through the junit-vintage-engine, with the plain [Application].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BridgeLibraryObserverTest {

    private lateinit var app: Application
    private val hub = BridgeStateHub(clock = { 42L })
    private lateinit var observer: BridgeLibraryObserver

    /** Every `libraryChanged` the hub published, in order. */
    private val kinds = mutableListOf<String>()
    private var subscription: BridgeStateHub.Subscription? = null

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        subscription = hub.subscribe { delta ->
            if (delta is LibraryChangedMessage) kinds += delta.kind
        }
        // The hub delivers a snapshot on subscribe: the list starts clean
        kinds.clear()
        observer = BridgeLibraryObserver(app, hub)
    }

    @After
    fun tearDown() {
        observer.stop()
        subscription?.cancel()
        kinds.clear()
    }

    // --- The sort-menu preference keys ---

    @Test
    fun `the seven chip order keys are sort menu keys`() {
        for (key in listOf(
            "homeSongsAllSortMenuOrder",
            "homeSongsFavoritesSortMenuOrder",
            "homeSongsCachedSortMenuOrder",
            "homeSongsDownloadedSortMenuOrder",
            "homeSongsTopSortMenuOrder",
            "homeSongsOnDeviceSortMenuOrder",
            "homeSongsDislikedSortMenuOrder",
        )) {
            assertTrue(key, BridgeLibraryObserver.isSongsSortMenuKey(key))
        }
    }

    @Test
    fun `the per chip option visibility keys are sort menu keys`() {
        // The sort chips read their SongSortBy options
        for (chip in listOf("all", "favs", "off", "dl", "disliked")) {
            for (option in listOf(
                "Title", "Artist", "AlbumName", "Duration", "PlayCount", "PlayTime",
                "RelativePlayTime", "DateAdded", "DatePlayed", "DateLiked", "Downloaded", "Custom",
            )) {
                assertTrue("$chip/$option", BridgeLibraryObserver.isSongsSortMenuKey("${chip}_sort_${option}_visible"))
            }
        }
        // The Top chip reads its period selector's options (StatisticsType)
        for (option in listOf("Today", "OneWeek", "OneMonth", "ThreeMonths", "SixMonths", "OneYear", "All")) {
            assertTrue("top/$option", BridgeLibraryObserver.isSongsSortMenuKey("top_sort_${option}_visible"))
        }
        // The OnDevice chip reads its OnDeviceSongSortBy options
        for (option in listOf("Title", "DateAdded", "Artist", "Duration", "Album")) {
            assertTrue("dev/$option", BridgeLibraryObserver.isSongsSortMenuKey("dev_sort_${option}_visible"))
        }
    }

    @Test
    fun `unrelated preference keys are not sort menu keys`() {
        for (key in listOf(
            "local_sort_Title_visible", // an unknown chip
            "top_sort_Title_hidden", // the wrong suffix
            "homeSongsTopSortOrder", // the sort itself, not the menu
            "dev_sort_AlbumName_visible", // OnDevice uses "Album", not "AlbumName"
            "top_sort_Yesterday_visible", // a StatisticsType name that does not exist
            "exoPlayerDiskCacheMaxSize",
            "",
        )) {
            assertFalse(key, BridgeLibraryObserver.isSongsSortMenuKey(key))
        }
    }

    // --- The library write notifier ---

    @Test
    fun `start registers the write notifier and stop clears it`() {
        assertNull(Database.libraryWriteNotifier)

        observer.start()
        val notifier = Database.libraryWriteNotifier
        assertTrue(notifier != null)
        notifier?.invoke(listOf("songs"))
        assertEquals(listOf("songs"), kinds)

        observer.stop()
        assertNull(Database.libraryWriteNotifier)
        // A write after the stop reaches no listener
        Database.libraryWriteNotifier?.invoke(listOf("songs"))
        assertEquals(listOf("songs"), kinds)
    }

    @Test
    fun `a write with several kinds emits one delta per kind`() {
        observer.start()

        Database.libraryWriteNotifier?.invoke(listOf("songs", "artists", "albums"))

        assertEquals(listOf("songs", "artists", "albums"), kinds)
    }

    @Test
    fun `start and stop are idempotent and a restarted observer listens again`() {
        observer.start()
        observer.start()
        observer.stop()
        assertNull(Database.libraryWriteNotifier)
        observer.stop()
        assertNull(Database.libraryWriteNotifier)

        observer.start()
        Database.libraryWriteNotifier?.invoke(listOf("playlists"))
        assertEquals(listOf("playlists"), kinds)
    }

    // --- The sort menu preference listener ---

    @Test
    fun `a sort menu edit emits songs, an unrelated preference edit does not`() {
        val prefs = app.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        observer.start()

        prefs.edit().putString("homeSongsTopSortMenuOrder", """["Title"]""").apply()
        assertEquals(listOf("songs"), kinds)

        prefs.edit().putBoolean("all_sort_Title_visible", false).apply()
        assertEquals(listOf("songs", "songs"), kinds)

        prefs.edit().putString("exoPlayerDiskCacheMaxSize", "2GB").apply()
        assertEquals(listOf("songs", "songs"), kinds)
    }

    @Test
    fun `a sort menu edit after the stop emits nothing`() {
        val prefs = app.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        observer.start()
        observer.stop()

        prefs.edit().putString("homeSongsAllSortMenuOrder", """["Title"]""").apply()

        assertEquals(emptyList<String>(), kinds)
    }
}
