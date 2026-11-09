package app.n_zik.android.components.image

import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfilePhotoWriteQueueTest {
    @Test
    fun `removal waits for cancelled blocking save and cannot be undone by its completion`() = runBlocking {
        val file = Files.createTempFile("profile-photo", ".jpg").toFile()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = launch(NzikDispatchers.DATA) {
            ProfilePhotoWriteQueue().save({ true }) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                file.writeText("late write")
                true
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            writer.cancel()
            val removal = launch(start = CoroutineStart.UNDISPATCHED) {
                ProfilePhotoWriteQueue().remove {
                    writer.cancelAndJoin()
                    file.delete()
                }
            }
            assertFalse(removal.isCompleted)
            release.countDown()
            removal.join()
            assertFalse(file.exists())
        } finally {
            release.countDown()
            writer.cancelAndJoin()
            file.delete()
        }
    }

    @Test
    fun `superseded request cannot start a replacement`() = runBlocking {
        assertFalse(ProfilePhotoWriteQueue().save({ false }) { error("stale save") })
    }
}
