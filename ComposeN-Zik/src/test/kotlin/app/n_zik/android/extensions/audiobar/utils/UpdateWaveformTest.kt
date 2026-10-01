package app.n_zik.android.extensions.audiobar.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Covers WaveformExtractor.updateWaveform's own delete-then-extract wiring (as opposed to
 * retryUntilReady in isolation, covered by UpdateWaveformRetryTest): a stale JSON must not leak
 * a stale Success into the result of a manual "Update waveform" request.
 */
class UpdateWaveformTest {

    @Test
    fun `deletes the stale JSON before extracting, so a stale cache hit cannot leak through`(@TempDir tempDir: File) = runTest {
        val waveformDir = File(tempDir, "waveforms").apply { mkdirs() }
        val savedFile = File(waveformDir, "media-update-test.json")
        savedFile.writeText(Gson().toJson(List(150) { it }))

        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns tempDir
        // The waveform dir is profile-aware (spec-profile-data-separation): the active profile
        // and its sharing flags come from the plain profile_preferences store.
        val profilePrefs = mockk<SharedPreferences>()
        every { profilePrefs.getString(any(), any()) } returns null
        every { profilePrefs.getBoolean(any(), any()) } returns false
        every { context.getSharedPreferences(any(), any()) } returns profilePrefs

        // No caches -> if the stale JSON were NOT deleted first, this would short-circuit to
        // Success from the stale file; deletion forces the real (empty-cache) outcome instead.
        val result = WaveformExtractor.updateWaveform(context, "media-update-test", emptyList())

        assertEquals(WaveformResult.NoCache, result)
        assertFalse(savedFile.exists())
    }
}
