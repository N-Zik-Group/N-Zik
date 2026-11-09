package app.n_zik.android.components.ui.screens.rewind

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RewindCaptureCleanupTest {
    @get:Rule val cache = TemporaryFolder()

    @Test
    fun `capture cleanup waits for a later sweep while an export owns the files`() = runTest {
        val shareDir = cache.newFolder("shared_rewind")
        val now = System.currentTimeMillis()
        val cutoff = now - 24L * 60 * 60 * 1_000
        val expired = File(shareDir, "NZik_Rewind_GLOBAL_16.png").apply {
            writeText("export source")
            assertTrue(setLastModified(cutoff - 1))
        }
        val atCutoff = File(shareDir, "NZik_Rewind_2026_03_02.png").apply {
            writeText("recent")
            assertTrue(setLastModified(cutoff))
        }

        withRewindCaptures {
            cleanupExpiredRewindCaptures(cache.root, now)
            assertTrue(expired.exists())
        }

        cleanupExpiredRewindCaptures(cache.root, now)
        assertFalse(expired.exists())
        assertTrue(atCutoff.exists())
    }
}
