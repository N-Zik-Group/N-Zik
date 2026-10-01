package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import app.it.fast4x.rimusic.utils.activeProfileKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class WaveformExtractorCacheHitTest {

    @Test
    fun `existing JSON with enough samples returns Success without touching caches`(@TempDir tempDir: File) = runTest {
        val waveformDir = File(tempDir, "waveforms").apply { mkdirs() }
        val amplitudes = List(150) { it }
        File(waveformDir, "media-1.json").writeText(Gson().toJson(amplitudes))

        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        // The waveform dir is profile-aware (spec-profile-data-separation): the active profile
        // and its sharing flags come from the plain profile_preferences store.
        val profilePrefs = mockk<SharedPreferences>()
        every { profilePrefs.getString(any(), any()) } returns null
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        // caches is intentionally empty: the JSON hit must short-circuit before it is ever read.
        val result = WaveformExtractor.getOrExtractWaveform(context, "media-1", emptyList())

        assertTrue(result is WaveformResult.Success)
        assertEquals(amplitudes, (result as WaveformResult.Success).amplitudes)
    }

    /**
     * The profile-aware waveform dir (spec-profile-data-separation): with a NON-default active
     * profile the JSON is read from / written to / deleted under `waveforms_<id>`, never the
     * base `waveforms`. Reverting [WaveformExtractor] to the old literal dir would leave this
     * red while the default-profile tests above stayed green.
     */
    @Test
    fun `a non-default profile reads its JSON from the suffixed dir`(@TempDir tempDir: File) = runTest {
        val workDir = File(tempDir, "waveforms_work").apply { mkdirs() }
        val amplitudes = List(150) { it + 1 }
        File(workDir, "media-9.json").writeText(Gson().toJson(amplitudes))

        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        val profilePrefs = mockk<SharedPreferences>()
        // Active profile is "work" (the suffixed dir); the sharing flag stays separate.
        every { profilePrefs.getString(any(), any()) } answers {
            if (firstArg<String>() == activeProfileKey) "work" else null
        }
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        val result = WaveformExtractor.getOrExtractWaveform(context, "media-9", emptyList())

        assertTrue(
            result is WaveformResult.Success,
            "the profile's own JSON must be read from waveforms_<id>"
        )
        assertEquals(amplitudes, (result as WaveformResult.Success).amplitudes)
        // The base dir was never created — the read happened under the suffixed one.
        assertFalse(File(tempDir, "waveforms").exists(), "the base waveforms dir must not be touched")
    }
}
