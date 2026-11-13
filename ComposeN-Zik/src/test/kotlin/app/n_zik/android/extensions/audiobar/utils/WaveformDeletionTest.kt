package app.n_zik.android.extensions.audiobar.utils

import java.io.File
import java.io.IOException
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.core.profiles.waveformsDir

class WaveformDeletionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context = mockk<Context>()
    private lateinit var directory: File

    @Before
    fun setup() {
        directory = temporary.newFolder("waveforms")
        mockkStatic("app.n_zik.android.utils.ProfilePreferencesKt")
        mockkStatic("app.n_zik.android.core.profiles.ProfileDataDirsKt")
        mockkStatic("app.n_zik.android.core.profiles.ProfileDataSharingKt")
        every { getActiveProfile(context) } returns "active"
        every { waveformsDir(context, "active") } returns directory
        every { app.n_zik.android.core.profiles.isLegacyStorageShared(context, "active", any()) } returns false
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `strict deletion reports actual file failure and preserves unrelated files`() {
        val target = File(directory, "video.json").apply { mkdir() }
        val child = File(target, "keep.txt").apply { writeText("nonempty directory") }
        val other = File(directory, "other.json").apply { writeText("other") }
        try {
            WaveformExtractor.deleteWaveformOrThrow(context, "video")
            fail("Failed deletion must propagate")
        } catch (_: IOException) {
            assertTrue(child.exists())
            assertTrue(other.exists())
        }
        // Existing background callers retain their best-effort behavior.
        WaveformExtractor.deleteWaveform(context, "video")
        assertTrue(child.exists())
    }

    @Test
    fun `strict deletion succeeds for an owned waveform and an already absent file`() {
        val target = File(directory, "video.json").apply { writeText("waveform") }
        WaveformExtractor.deleteWaveformOrThrow(context, "video")
        assertFalse(target.exists())
        WaveformExtractor.deleteWaveformOrThrow(context, "video")
    }
}
