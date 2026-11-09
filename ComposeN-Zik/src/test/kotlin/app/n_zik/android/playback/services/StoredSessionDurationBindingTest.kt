package app.n_zik.android.playback.services

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import android.app.Application
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StoredSessionDurationBindingTest {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private val gates = mutableListOf<CompletableDeferred<Long?>>()
    private val bindings = mutableListOf<StoredSessionDurationBinding>()

    @After
    fun tearDown() {
        bindings.forEach { it.close() }
        scope.cancel()
        gates.forEach { it.complete(null) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `database read starts without waiting for another UI turn after the transition`() {
        val source = TestPlayer("immediate-read")
        val started = CountDownLatch(1)
        val pending = gate()
        var publications = 0
        val binding = binding({ source.player }, {
            started.countDown()
            pending.await()
        }) { publications++ }

        binding.refreshPlayer()
        // Deliberately do not pump the paused UI looper: database submission must
        // already have happened inside the transition callback's current turn.
        assertTrue("Database read waited for another UI turn", started.await(3, TimeUnit.SECONDS))
        assertEquals(0, publications)
        pending.complete(157_000L)
        pumpUntil { publications == 1 }
        assertEquals(157_000L, binding.forMediaId("immediate-read"))
    }

    @Test
    fun `stored active duration is read off main without changing the player's unknown source duration`() {
        val source = TestPlayer("stored")
        var readOnMain: Boolean? = null
        var publications = 0
        val binding = binding({ source.player }, {
            readOnMain = Looper.myLooper() === Looper.getMainLooper()
            157_000L
        }) { publications++ }

        binding.refreshPlayer()
        pumpUntil { publications == 1 }

        assertEquals(false, readOnMain)
        assertEquals(157_000L, binding.forMediaId("stored"))
        assertNull(binding.forMediaId("other"))
        assertEquals(C.TIME_UNSET, source.player.duration)
        assertNull(source.player.mediaMetadata.durationMs)
    }

    @Test
    fun `late database response for previous song cannot publish over a replacement`() {
        val source = TestPlayer("first")
        val first = gate()
        val second = gate()
        val reads = CopyOnWriteArrayList<String>()
        val published = mutableListOf<String?>()
        val binding = binding({ source.player }, { id ->
            reads += id
            // Simulate a synchronous database read that cannot stop when its caller cancels.
            withContext(NonCancellable) { if (id == "first") first.await() else second.await() }
        }) { published += source.player.currentMediaItem?.mediaId }
        binding.refreshPlayer()
        pumpUntil { "first" in reads }
        source.select("second")
        pumpUntil { "second" in reads }
        first.complete(157_000L)
        second.complete(174_000L)
        pumpUntil { published.isNotEmpty() }

        assertEquals(listOf("second"), published)
        assertNull(binding.forMediaId("first"))
        assertEquals(174_000L, binding.forMediaId("second"))
    }

    @Test
    fun `closing a binding cancels delivery clears data and removes its listener`() {
        val source = TestPlayer("pending")
        val pending = gate()
        val reads = CopyOnWriteArrayList<String>()
        var publications = 0
        val binding = binding({ source.player }, { id ->
            reads += id
            withContext(NonCancellable) { pending.await() }
        }) { publications++ }
        binding.refreshPlayer()
        pumpUntil { reads.isNotEmpty() }
        binding.close()
        pending.complete(157_000L)
        shadowOf(Looper.getMainLooper()).idle()
        source.select("next")
        binding.refreshPlayer()

        assertTrue(source.listeners.isEmpty())
        assertNull(binding.forMediaId("pending"))
        assertEquals(0, publications)
        assertEquals(listOf("pending"), reads.toList())
    }

    @Test
    fun `live timeline invalidates pending finite lookup and permits a later finite item`() {
        val source = TestPlayer("track")
        val pending = gate()
        val reads = CopyOnWriteArrayList<String>()
        var publications = 0
        val binding = binding({ source.player }, { id ->
            reads += id
            withContext(NonCancellable) { pending.await() }
        }) { publications++ }
        binding.refreshPlayer()
        pumpUntil { reads.isNotEmpty() }
        source.setLive(true)
        pending.complete(157_000L)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(binding.forMediaId("track"))
        assertEquals(0, publications)

        source.setLive(false)
        pumpUntil { publications == 1 }
        assertEquals(157_000L, binding.forMediaId("track"))
        assertEquals(2, reads.size)
    }

    @Test
    fun `facade replacement with the same ID rejects the old result and uses the new facade`() {
        val first = TestPlayer("same")
        val second = TestPlayer("same")
        var active = first
        val oldRead = gate()
        val newRead = gate()
        val reads = CopyOnWriteArrayList<Int>()
        val published = mutableListOf<Player>()
        val binding = binding({ active.player }, {
            val index = reads.size
            reads += index
            withContext(NonCancellable) { if (index == 0) oldRead.await() else newRead.await() }
        }) { published += active.player }
        binding.refreshPlayer()
        pumpUntil { reads.size == 1 }
        active = second
        binding.refreshPlayer()
        pumpUntil { reads.size == 2 }
        oldRead.complete(157_000L)
        newRead.complete(174_000L)
        pumpUntil { published.isNotEmpty() }

        assertTrue(first.listeners.isEmpty())
        assertEquals(listOf(second.player), published)
        assertEquals(174_000L, binding.forMediaId("same"))
    }

    @Test
    fun `declared and live durations do not request optional database metadata`() {
        val source = TestPlayer("known", 157_000L)
        var reads = 0
        val binding = binding({ source.player }, { reads++; 174_000L }) {}
        binding.refreshPlayer()
        source.setLive(true)
        source.select("live")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, reads)
        assertNull(binding.forMediaId("known"))
        assertFalse(source.listeners.isEmpty())
    }

    private fun binding(
        player: () -> Player,
        read: suspend (String) -> Long?,
        resolved: () -> Unit,
    ) = StoredSessionDurationBinding(scope, player, read, resolved).also { bindings += it }

    private fun gate() = CompletableDeferred<Long?>().also { gates += it }

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Stored duration did not converge", condition())
    }

    private class TestPlayer(id: String, declaredDuration: Long? = null) {
        val listeners = mutableSetOf<Player.Listener>()
        private var item = item(id, declaredDuration)
        private var liveValue = false
        val player = mockk<Player>(relaxed = true) {
            every { currentMediaItem } answers { item }
            every { mediaMetadata } answers { item.mediaMetadata }
            every { duration } returns C.TIME_UNSET
            every { isCurrentMediaItemLive } answers { liveValue }
            every { addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
            every { removeListener(any()) } answers { listeners -= firstArg<Player.Listener>(); Unit }
        }

        fun select(id: String) {
            item = item(id, null)
            listeners.toList().forEach { it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
        }

        fun setLive(value: Boolean) {
            liveValue = value
            listeners.toList().forEach { it.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }
        }

        private fun item(id: String, duration: Long?): MediaItem = MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setDurationMs(duration).build()).build()
    }
}
