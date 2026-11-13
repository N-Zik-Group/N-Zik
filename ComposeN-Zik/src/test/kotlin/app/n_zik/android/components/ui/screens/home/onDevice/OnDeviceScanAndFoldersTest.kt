package app.n_zik.android.components.ui.screens.home.onDevice

import android.database.sqlite.SQLiteDatabase
import app.n_zik.android.core.utils.PathUtils
import app.n_zik.android.core.utils.localAudioSelection
import app.n_zik.android.core.utils.localAudioTitle
import app.n_zik.android.core.ondevice.DeviceLibrarySnapshot
import app.n_zik.android.core.utils.DeviceScan
import app.n_zik.android.core.utils.DeviceSongOrder
import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OnDeviceScanAndFoldersTest {
    @Test
    fun selectionAdmitsUnclassifiedTracksAndExcludesSystemAudio() {
        SQLiteDatabase.create(null).use { database ->
            database.execSQL(
                """
                CREATE TABLE audio (id INTEGER, is_music INTEGER, duration INTEGER, mime_type TEXT,
                    is_alarm INTEGER, is_notification INTEGER, is_ringtone INTEGER)
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO audio VALUES
                    (1, 0, 210000, 'audio/mpeg', 0, 0, 0),
                    (2, 0, 0, 'audio/flac', NULL, NULL, NULL),
                    (3, 1, 10000, 'audio/mpeg', 0, 0, 1),
                    (4, 0, 10000, 'audio/mpeg', 1, 0, 0),
                    (5, 0, 10000, 'audio/mpeg', 0, 1, 0)
                """.trimIndent()
            )

            database.rawQuery("SELECT id FROM audio WHERE $localAudioSelection ORDER BY id", null).use { cursor ->
                val ids = buildList {
                    while (cursor.moveToNext()) add(cursor.getInt(0))
                }
                assertEquals(listOf(1, 2), ids)
            }
        }
        assertEquals("track.mp3", localAudioTitle(" ", "track.mp3"))
    }

    @Test
    fun normalizesRelativePathsLegacyFileParentsAndMissingPaths() {
        assertEquals("Music/Live", PathUtils.normalizeFolder("/Music//Live/"))
        assertEquals("Music/Live", PathUtils.normalizeFolder("/storage/emulated/0/Music/Live/"))
        assertEquals("Music/Live", PathUtils.mediaFolder("/storage/emulated/0/Music/Live/song.mp3", true))
        assertEquals("", PathUtils.mediaFolder("/storage/emulated/0/song.mp3", true))
        assertEquals("", PathUtils.mediaFolder(null, false))
        assertEquals("Music/Live ", PathUtils.mediaFolder("Music/Live /", false))
    }

    @Test
    fun rootAndSiblingPathsRespectFolderBoundaries() {
        val paths = listOf("", "Music/Live", "Music/Live/Deep", "Music/Lives", "Music/Lively")
        assertEquals(listOf("Music"), PathUtils.getAvailablePaths(paths, ""))
        assertEquals(listOf("Deep"), PathUtils.getAvailablePaths(paths, "Music/Live"))
        assertTrue(PathUtils.isAtOrUnder("Music/Live/Deep", "Music/Live"))
        assertFalse(PathUtils.isAtOrUnder("Music/Lives", "Music/Live"))
        assertEquals("Music", PathUtils.childPath("", "Music"))
        assertEquals("", PathUtils.normalizeExclusion("/"))
        assertNull(PathUtils.normalizeExclusion("."))
        assertNull(PathUtils.normalizeExclusion("Music/.."))
    }

    @Test
    fun removableStorageHasSeparateFoldersAndHonorsExistingRelativeExclusions() {
        val removable = "abcd-1234/Music/Live"
        assertEquals(removable, PathUtils.mediaFolder("/storage/ABCD-1234/Music/Live/song.mp3", true))
        assertEquals(removable, PathUtils.mediaFolder("Music/Live/", false, "ABCD-1234"))
        assertEquals(listOf("Music", "abcd-1234"), PathUtils.getAvailablePaths(listOf(removable, "Music/Live"), ""))
        assertTrue(PathUtils.isExcluded(removable, "Music/Live"))
        assertFalse(PathUtils.isExcluded("Music/Live", removable))
    }

    @Test
    fun startsAtConfiguredFolderAndRetainsOrResolvesTheCurrentFolderAfterRefresh() {
        val initial = listOf("Music/Live/Deep", "Music/Live/Other", "Music/Studio")
        val start = PathUtils.resolveFolder(initial, currentFolder = null, startFolder = "Music/Live/")
        assertEquals("Music/Live", start)
        val selected = PathUtils.childPath(start, "Deep")

        assertEquals(selected, PathUtils.resolveFolder(initial.reversed(), selected, "Music/Studio"))
        assertEquals(start, PathUtils.resolveFolder(initial.drop(1), selected, "Music/Studio"))
        assertEquals("", PathUtils.resolveFolder(listOf("Podcasts"), selected, "Music/Studio"))
    }

    @Test
    fun refreshRevisionIsRetainedForTheRequestedProfile() = runBlocking {
        val requests = LocalSongRefreshRequests()
        requests.request("default")
        requests.request("default")
        assertEquals(2L, requests.forProfile("default").first())
        assertEquals(0L, requests.forProfile("work").first())
    }

    @Test
    fun oneScanSupportsChangingSortAndDirection() {
        val first = Song.makePlaceholder("local:1").copy(title = "beta")
        val second = Song.makePlaceholder("local:2").copy(title = "Alpha")
        val scan = DeviceScan(
            mapOf(first to "Music", second to "Download"),
            DeviceLibrarySnapshot(),
            mapOf(first.id to DeviceSongOrder("Z", 5, 120), second.id to DeviceSongOrder("a", 9, 60)),
        )
        assertEquals(listOf(second, first), scan.sortedSongs(OnDeviceSongSortBy.Title, SortOrder.Ascending).keys.toList())
        assertEquals(listOf(first, second), scan.sortedSongs(OnDeviceSongSortBy.DateAdded, SortOrder.Ascending).keys.toList())
        assertEquals(listOf(second, first), scan.sortedSongs(OnDeviceSongSortBy.Album, SortOrder.Ascending).keys.toList())
        assertEquals(listOf(first, second), scan.sortedSongs(OnDeviceSongSortBy.Duration, SortOrder.Descending).keys.toList())
    }

}
