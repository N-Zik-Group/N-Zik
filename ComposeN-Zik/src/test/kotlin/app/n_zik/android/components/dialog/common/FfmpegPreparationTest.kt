package app.n_zik.android.components.dialog.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import app.n_zik.android.modules.ModuleState

@OptIn(ExperimentalCoroutinesApi::class)
class FfmpegPreparationTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cancel stops the preparation and allows a fresh attempt`() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val finishCleanup = CompletableDeferred<Unit>()
            val preparation = FfmpegPreparation {
                if (entered.complete(Unit)) {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            stopped.complete(Unit)
                            finishCleanup.await()
                        }
                    }
                }
                ModuleState.READY
            }

            val result = async { preparation.awaitReady() }
            try {
                entered.await()
                assertTrue(preparation.isPreparing)
                preparation.cancel()
                stopped.await()
                assertTrue(preparation.isPreparing)
                assertFalse(preparation.showsProgress)
            } finally {
                finishCleanup.complete(Unit)
            }
            assertNull(result.await())
            assertTrue(stopped.isCompleted)
            assertFalse(preparation.isPreparing)
            assertSame(ModuleState.READY, preparation.awaitReady())
        }
    }

    @Test
    fun `a disposed owner cannot start preparation`() = runBlocking {
        val preparation = FfmpegPreparation { error("Preparation started after disposal") }
        preparation.dispose()
        assertNull(preparation.awaitReady())
    }
}
