package app.n_zik.android.core.backup

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryStartupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `startup does not open the library until recovery returns even when the observer changes`() {
        val enteredRecovery = CountDownLatch(1)
        val releaseRecovery = CountDownLatch(1)
        val attempts = AtomicInteger()
        var initialized = false
        val startup = LibraryStartup(
            recover = {
                check(Thread.currentThread() != Looper.getMainLooper().thread)
                attempts.incrementAndGet()
                enteredRecovery.countDown()
                check(releaseRecovery.await(10, TimeUnit.SECONDS))
            },
            initialize = { initialized = true },
        )

        val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
        startup.start()
        try {
            assertTrue(enteredRecovery.await(5, TimeUnit.SECONDS))
            var destroyedObserverResumed = false
            var replacementObserverResumed = false
            val original = scope.launch {
                startup.awaitReady()
                destroyedObserverResumed = true
            }
            shadowOf(Looper.getMainLooper()).idle()
            original.cancel()
            scope.launch {
                startup.awaitReady()
                replacementObserverResumed = true
            }

            startup.start()
            shadowOf(Looper.getMainLooper()).idle()
            assertSame(LibraryStartupState.Waiting, startup.state.value)
            assertFalse(initialized)
            assertFalse(replacementObserverResumed)
            assertThrows(IllegalStateException::class.java, startup::requireRecovered)

            releaseRecovery.countDown()
            drainMainUntil { replacementObserverResumed }
            assertTrue(initialized)
            assertFalse(destroyedObserverResumed)
            assertEquals(1, attempts.get())
            startup.requireRecovered()
        } finally {
            releaseRecovery.countDown()
            scope.cancel()
        }
    }

    @Test
    fun `a malformed restore journal blocks access until explicit retry succeeds`() {
        val files = tmp.newFolder("files")
        val databases = tmp.newFolder("databases")
        val journal = files.resolve("database-restore-journal").apply { mkdir() }
        val pending = journal.resolve("pending").apply { writeText("invalid journal") }
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = files
            override fun getDatabasePath(name: String) = databases.resolve(name)
        }
        var initialized = 0
        val startup = LibraryStartup(
            recover = { DatabaseRestoreJournal.onProcessStart(context, mainProcess = false) },
            initialize = { initialized++ },
        )

        startup.start()
        drainMainUntil { startup.state.value is LibraryStartupState.Failed }
        assertTrue(pending.isFile)
        assertEquals(0, initialized)
        assertThrows(IllegalStateException::class.java, startup::requireRecovered)

        assertTrue(pending.delete())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(startup.state.value is LibraryStartupState.Failed)

        startup.start()
        drainMainUntil { startup.state.value == LibraryStartupState.Ready }
        assertEquals(1, initialized)
        startup.requireRecovered()
    }

    private fun drainMainUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue(condition())
    }
}
