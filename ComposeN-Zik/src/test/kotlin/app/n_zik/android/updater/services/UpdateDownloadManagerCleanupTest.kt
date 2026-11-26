package app.n_zik.android.updater.services

import android.content.Context
import app.n_zik.android.updater.ui.clearUpdateCache
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * gh-606 N3 — the APK purge core extracted from `UpdateDownloadManager.cleanupTempFiles` must
 * delete every file in the update directory while keeping the directory itself, and must be a
 * no-op (returning 0) when the directory is empty or missing.
 *
 * Also pins the update-menu wiring: [clearUpdateCache] (dispatched to [NzikDispatchers.DATA]
 * from the click) must call [UpdateDownloadManager.clearCache] exactly once, off the
 * caller's thread.
 */
class UpdateDownloadManagerCleanupTest {

    @Test
    fun `cleanup removes the apk files but keeps the directory`(@TempDir tmp: File) {
        val downloadDir = File(tmp, "nzik_updates").apply { mkdirs() }
        File(downloadDir, "nzik-update-1.0.0.apk").writeText("apk")
        File(downloadDir, "nzik-update-1.1.0-beta.apk").writeText("apk")

        val deleted = UpdateDownloadManager.cleanupUpdateDir(downloadDir)

        assertEquals(2, deleted)
        assertTrue(downloadDir.exists(), "the directory itself must survive the purge")
        assertTrue(downloadDir.listFiles().orEmpty().isEmpty(), "all apks must be gone")
    }

    @Test
    fun `cleanup of an empty directory deletes nothing`(@TempDir tmp: File) {
        val downloadDir = File(tmp, "nzik_updates").apply { mkdirs() }

        val deleted = UpdateDownloadManager.cleanupUpdateDir(downloadDir)

        assertEquals(0, deleted)
        assertTrue(downloadDir.exists())
    }

    @Test
    fun `cleanup of a missing directory deletes nothing and does not throw`(@TempDir tmp: File) {
        val deleted = UpdateDownloadManager.cleanupUpdateDir(File(tmp, "nzik_updates"))

        assertEquals(0, deleted)
    }

    @Test
    fun `clearUpdateCache purges the update cache exactly once off the caller thread`() = runBlocking {
        mockkObject(UpdateDownloadManager)
        every { UpdateDownloadManager.clearCache(any<Context>()) } returns true
        val context = mockk<Context>()

        val callerThreadName = Thread.currentThread().name
        val purgeThreadName = withContext(NzikDispatchers.DATA) {
            clearUpdateCache(context)
            Thread.currentThread().name
        }

        assertNotEquals(callerThreadName, purgeThreadName)
        verify(exactly = 1) { UpdateDownloadManager.clearCache(context) }
        unmockkObject(UpdateDownloadManager)
    }
}
