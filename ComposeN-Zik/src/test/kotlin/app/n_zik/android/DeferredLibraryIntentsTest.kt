package app.n_zik.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeferredLibraryIntentsTest {
    @Test
    fun `back to back warm links preserve the first active target and queue the rest`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val uri = Uri.parse("https://youtu.be/example")
        controller.newIntent(Intent(Intent.ACTION_VIEW, uri))
        controller.newIntent(Intent(Intent.ACTION_VIEW, uri))
        controller.newIntent(Intent(MainActivity.action_profiles))

        val state = Bundle()
        controller.saveInstanceState(state)
        val savedNavigation = checkNotNull(state.getBundle("libraryStartup.pendingNavigation"))
        assertEquals(uri, PendingLibraryNavigation.fromBundle(savedNavigation).uri)
        @Suppress("DEPRECATION")
        val queued = checkNotNull(state.getParcelableArrayList<Intent>("libraryStartup.deferredLaunchIntents"))
        assertEquals(listOf(Intent.ACTION_VIEW, MainActivity.action_profiles), queued.map(Intent::getAction))
        assertEquals(uri, queued.first().data)
    }

    @Test
    fun `warm requests wait for each navigation acknowledgement in arrival order`() = runTest {
        val queue = DeferredLibraryIntents()
        val activeTarget = mutableStateOf<String?>("cold-link")
        val deliveries = mutableListOf<String>()
        queue.offer(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/example")))
        queue.offer(Intent(MainActivity.action_profiles))

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.drain(consumersIdle = { activeTarget.value == null }) { intent ->
                val action = checkNotNull(intent.action)
                deliveries.add(action)
                activeTarget.value = action
            }
        }
        runCurrent()
        assertTrue(deliveries.isEmpty())

        Snapshot.withMutableSnapshot { activeTarget.value = null }
        runCurrent()
        assertEquals(listOf(Intent.ACTION_VIEW), deliveries)
        assertTrue(queue.hasPending)
        assertEquals(listOf(MainActivity.action_profiles), queue.snapshot().map(Intent::getAction))

        Snapshot.withMutableSnapshot { activeTarget.value = null }
        runCurrent()
        assertEquals(listOf(Intent.ACTION_VIEW, MainActivity.action_profiles), deliveries)
        assertTrue(queue.hasPending)

        Snapshot.withMutableSnapshot { activeTarget.value = null }
        runCurrent()
        assertFalse(queue.hasPending)
    }

    @Test
    fun `saved warm queue survives a plain launcher retap`() {
        val queue = DeferredLibraryIntents()
        val notification = Intent(Intent.ACTION_MAIN).putExtra("notification-token", 17L)
        queue.offer(notification)
        queue.offer(Intent(Intent.ACTION_MAIN))
        val restored = DeferredLibraryIntents()
        queue.snapshot().forEach(restored::offer)
        restored.offer(Intent(Intent.ACTION_MAIN))

        assertEquals(1, restored.snapshot().size)
        assertEquals(17L, restored.snapshot().single().getLongExtra("notification-token", 0L))
    }

    @Test
    fun `launch URL is replayable only before the cold library UI has started`() {
        val uri = Uri.parse("https://youtu.be/example")
        val launch = Intent(Intent.ACTION_VIEW, uri)

        assertEquals(uri, initialLibraryUri(launch, null, waitingColdLaunch = false))
        assertEquals(uri, initialLibraryUri(launch, Bundle(), waitingColdLaunch = true))
        assertEquals(null, initialLibraryUri(launch, Bundle(), waitingColdLaunch = false))
    }

    @Test
    fun `saved navigation carries resolved notification targets and an unacknowledged URI`() {
        val navigation = PendingLibraryNavigation(
            uri = Uri.parse("https://youtu.be/example"),
            shortcut = MainActivity.action_search,
            rewindDeck = 2025 to 0,
            rewindPlaylist = 17L,
            coldPlayerToken = 45L,
            openWarmPlayer = true,
            openProfiles = true,
            openUpdater = true,
        )
        val restored = PendingLibraryNavigation.fromBundle(navigation.toBundle())

        assertEquals(navigation, restored)
        assertTrue(restored.hasPending)
    }

    @Test
    fun `stopping the drain does not acknowledge a request still being handled`() = runTest {
        val queue = DeferredLibraryIntents()
        val processing = mutableStateOf(false)
        val actions = mutableListOf<String>()
        val dispatch: (Intent) -> Unit = {
            actions.add(checkNotNull(it.action))
            processing.value = true
        }
        queue.offer(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/example")))
        val first = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.drain({ !processing.value }, dispatch)
        }
        runCurrent()
        first.cancel()
        runCurrent()
        assertTrue(queue.hasPending)

        queue.offer(Intent(MainActivity.action_profiles))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            queue.drain({ !processing.value }, dispatch)
        }
        runCurrent()
        assertEquals(listOf(Intent.ACTION_VIEW), actions)

        Snapshot.withMutableSnapshot { processing.value = false }
        runCurrent()
        assertEquals(listOf(Intent.ACTION_VIEW, MainActivity.action_profiles), actions)
    }
}
