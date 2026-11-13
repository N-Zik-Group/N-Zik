package app.n_zik.android.components.dialog.song

import java.io.File
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.playback.services.automotive.models.ArtworkRevisions
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SongUpdateCoverResetTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: Context
    private lateinit var covers: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        covers = temporary.newFolder("active-covers")
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        mockkStatic("app.n_zik.android.core.profiles.ProfileDataDirsKt")
        every { getActiveProfile(context) } returns "active"
        every { coversDir(context, "active") } returns covers
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) } just runs
        mockkObject(ArtworkRevisions)
        every { ArtworkRevisions.invalidate() } just runs
    }

    @After
    fun teardown() = unmockkAll()

    @Test
    fun resetRemovesOnlyTheOwnedCoverAndEvictsEveryQuality() {
        val owned = File(covers, "cover_video.jpg").apply { writeText("custom") }
        val otherSong = File(covers, "cover_other.jpg").apply { writeText("other") }
        val otherProfile = File(temporary.newFolder("other-profile"), "cover_video.jpg").apply { writeText("other") }
        val userFile = temporary.newFile("selected-photo.jpg").apply { writeText("original") }

        SongUpdateCoverReset.reset(context, "video", "modified:${userFile.toURI()}", "active")

        assertFalse(owned.exists())
        assertTrue(otherSong.exists())
        assertTrue(otherProfile.exists())
        assertTrue(userFile.exists())
        ImageCacheFactory.NetworkQuality.entries.forEach { quality ->
            verify { ImageCacheFactory.clearCacheForKeyOrThrow(owned.canonicalPath, quality) }
        }
        verify(exactly = 1) { ArtworkRevisions.invalidate() }
    }

    @Test
    fun invalidIdentifiersCannotSelectFilesOutsideTheCoverDirectory() {
        assertNull(SongUpdateCoverReset.ownedCover(covers, "../../original"))
        assertNull(SongUpdateCoverReset.ownedCover(covers, "..\\original"))
        assertNull(SongUpdateCoverReset.ownedCover(covers, ""))
    }

    @Test
    fun profileChangeBeforeCleanupPreservesCoverAndCaches() {
        val owned = File(covers, "cover_video.jpg").apply { writeText("custom") }
        every { getActiveProfile(context) } returns "other-profile"
        SongUpdateCoverReset.reset(context, "video", "modified:custom", "active")
        assertTrue(owned.exists())
        verify(exactly = 0) { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) }
        verify(exactly = 0) { ArtworkRevisions.invalidate() }
    }

    @Test
    fun `failed owned cover deletion propagates without deleting anything else`() {
        val owned = File(covers, "cover_video.jpg").apply { mkdir() }
        val child = File(owned, "keep.txt").apply { writeText("nonempty directory") }
        val other = File(covers, "cover_other.jpg").apply { writeText("other") }
        try {
            SongUpdateCoverReset.reset(context, "video", "modified:custom", "active")
            org.junit.Assert.fail("Failed deletion must reach the update report")
        } catch (_: java.io.IOException) {
            assertTrue(child.exists())
            assertTrue(other.exists())
        }
        verify(exactly = 0) { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) }
        verify(exactly = 0) { ArtworkRevisions.invalidate() }
    }

    @Test
    fun `cover cache failure and cancellation propagate after owned file removal`() {
        val failure = java.io.IOException("Cache failure")
        every { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) } throws failure
        try {
            SongUpdateCoverReset.reset(context, "video", null, "active")
            org.junit.Assert.fail("Cache failure must propagate")
        } catch (actual: java.io.IOException) {
            org.junit.Assert.assertSame(failure, actual)
        }
        val cancellation = kotlinx.coroutines.CancellationException("Cancelled update")
        every { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) } throws cancellation
        try {
            SongUpdateCoverReset.reset(context, "video", null, "active")
            org.junit.Assert.fail("Cancellation must propagate")
        } catch (actual: kotlinx.coroutines.CancellationException) {
            org.junit.Assert.assertSame(cancellation, actual)
        }
    }


    @Test
    fun `real strict eviction reports disk cache errors through cover reset`() {
        val failure = java.io.IOException("Disk cache unavailable")
        val loader = io.mockk.mockk<coil3.ImageLoader>(relaxed = true)
        val diskCache = io.mockk.mockk<coil3.disk.DiskCache>()
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.DISK_CACHE } returns diskCache
        every { ImageCacheFactory.clearCacheForKeyOrThrow(any(), any()) } answers { callOriginal() }
        every { diskCache.remove(any()) } throws failure
        try {
            SongUpdateCoverReset.reset(context, "video", null, "active")
            org.junit.Assert.fail("Real cache eviction failure must propagate")
        } catch (actual: java.io.IOException) {
            org.junit.Assert.assertSame(failure, actual)
        }
        verify(exactly = 0) { ArtworkRevisions.invalidate() }
    }

}
