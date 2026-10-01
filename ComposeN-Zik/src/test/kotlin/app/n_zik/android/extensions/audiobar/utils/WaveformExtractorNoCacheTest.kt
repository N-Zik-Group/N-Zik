package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.datasource.cache.Cache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.TreeSet

class WaveformExtractorNoCacheTest {

    @Test
    fun `caches with no spans returns NoCache`(@TempDir tempDir: File) = runTest {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        // The waveform dir is profile-aware (spec-profile-data-separation): the active profile
        // and its sharing flags come from the plain profile_preferences store.
        val profilePrefs = mockk<SharedPreferences>()
        every { profilePrefs.getString(any(), any()) } returns null
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        val emptyCache = mockk<Cache>()
        every { emptyCache.getCachedSpans(any()) } returns TreeSet()

        val result = WaveformExtractor.getOrExtractWaveform(context, "media-2", listOf(emptyCache))

        assertEquals(WaveformResult.NoCache, result)
    }
}
