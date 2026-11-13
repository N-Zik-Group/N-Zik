package app.n_zik.android.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.database.FormatTable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the Format download-quality non-compliance queries backing the "Update downloads"
 * feature, using Robolectric (JUnit 4 runner) with an in-memory database:
 *
 * - a download tracked with the currently selected setting is compliant (never re-downloaded)
 * - a download tracked with a different setting is non-compliant (re-downloaded)
 * - a NULL tracking value (download made before quality tracking existed) is non-compliant
 * - "Auto" tracked against an "Auto" setting is compliant
 * - songs without a Format row are not part of the tracked set
 * - the COUNT query and the list query always agree
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FormatDownloadQualityTest {

    private lateinit var db: DatabaseInitializer
    private lateinit var formatDao: FormatTable

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        formatDao = db.formatTable
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    private fun insertFormat(songId: String, downloadQuality: String?) {
        formatDao.upsert(Format(songId = songId, itag = 251, downloadQuality = downloadQuality))
    }

    @Test
    fun `a download tracked with the current setting is compliant`() {
        insertSong("s1")
        insertFormat("s1", "High")

        assertEquals(emptyList<String>(), formatDao.findNonCompliantSongIds(listOf("s1"), "High"))
        assertEquals(0, formatDao.countNonCompliantDownloaded(listOf("s1"), "High"))
    }

    @Test
    fun `a download tracked with a different setting is non-compliant`() {
        insertSong("s1")
        insertFormat("s1", "Low")

        assertEquals(listOf("s1"), formatDao.findNonCompliantSongIds(listOf("s1"), "High"))
        assertEquals(1, formatDao.countNonCompliantDownloaded(listOf("s1"), "High"))
    }

    @Test
    fun `a download with a null tracking value is non-compliant`() {
        insertSong("s1")
        insertFormat("s1", null)

        assertEquals(listOf("s1"), formatDao.findNonCompliantSongIds(listOf("s1"), "High"))
        assertEquals(1, formatDao.countNonCompliantDownloaded(listOf("s1"), "High"))
    }

    @Test
    fun `an auto tracked download is compliant with the auto setting`() {
        insertSong("s1")
        insertFormat("s1", "Auto")

        assertEquals(emptyList<String>(), formatDao.findNonCompliantSongIds(listOf("s1"), "Auto"))
        assertEquals(0, formatDao.countNonCompliantDownloaded(listOf("s1"), "Auto"))
    }

    @Test
    fun `a mixed batch reports only the non-compliant downloads`() {
        insertSong("compliant")
        insertSong("different")
        insertSong("nullTracked")
        insertSong("alsoCompliant")
        insertFormat("compliant", "High")
        insertFormat("different", "Low")
        insertFormat("nullTracked", null)
        insertFormat("alsoCompliant", "High")

        val nonCompliant = formatDao.findNonCompliantSongIds(
            listOf("compliant", "different", "nullTracked", "alsoCompliant"), "High"
        )
        assertEquals(setOf("different", "nullTracked"), nonCompliant.toSet())
        assertEquals(2, formatDao.countNonCompliantDownloaded(
            listOf("compliant", "different", "nullTracked", "alsoCompliant"), "High"
        ))
    }

    @Test
    fun `songs without a format row are excluded from the tracked set`() {
        insertSong("tracked")
        insertSong("untracked")
        insertFormat("tracked", "High")

        assertEquals(listOf("tracked"), formatDao.findSongIdsWithFormat(listOf("tracked", "untracked")))
        assertEquals(emptyList<String>(), formatDao.findNonCompliantSongIds(listOf("untracked"), "High"))
        assertEquals(0, formatDao.countNonCompliantDownloaded(listOf("untracked"), "High"))
    }
}
