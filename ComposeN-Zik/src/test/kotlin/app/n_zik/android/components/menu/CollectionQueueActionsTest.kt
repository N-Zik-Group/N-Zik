package app.n_zik.android.components.menu

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.player.MenuQueueEdits
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CollectionQueueActionsTest {
    @get:Rule
    val compose = createComposeRule()
    private val scheduler = TestCoroutineScheduler()

    @Before fun useControlledMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
    }

    @After fun restoreMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `play next and enqueue share one pending request and allow retry`() {
        val actions = AtomicReference<Pair<MenuIcon, MenuIcon>>()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val attempts = AtomicInteger()
        val player = mockk<Player>()
        val queueEdits = MenuQueueEdits({ player }, setShuffleOrder = {})
        val menuState = MenuState()
        val songs = listOf(Song(id = "track", title = "Title", durationText = null, thumbnailUrl = null))
        compose.setContent {
            val context = LocalContext.current
            val current = collectionQueueActions(context, menuState, queueEdits::capture, songs) {
                attempts.incrementAndGet()
                started.countDown()
                finish.await(5, TimeUnit.SECONDS)
                throw CancellationException("Stop before queue mutation")
            }
            SideEffect { actions.set(current) }
        }

        try {
            compose.runOnIdle {
                val (playNext, enqueue) = actions.get()
                playNext.onShortClick()
                enqueue.onShortClick()
                scheduler.runCurrent()
            }
            compose.waitUntil(timeoutMillis = 5_000) { started.count == 0L }
            compose.runOnIdle {
                val (playNext, enqueue) = actions.get()
                assertFalse(playNext.isEnabled)
                assertFalse(enqueue.isEnabled)
                assertEquals(1, attempts.get())
            }
        } finally {
            finish.countDown()
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            runQueueCallbacks()
            actions.get().let { (playNext, enqueue) -> playNext.isEnabled && enqueue.isEnabled }
        }
        compose.runOnIdle { actions.get().second.onShortClick() }
        compose.waitUntil(timeoutMillis = 5_000) {
            runQueueCallbacks()
            attempts.get() == 2 && actions.get().second.isEnabled
        }
    }

    private fun runQueueCallbacks() = compose.runOnUiThread {
        scheduler.runCurrent()
        Snapshot.sendApplyNotifications()
    }
}
