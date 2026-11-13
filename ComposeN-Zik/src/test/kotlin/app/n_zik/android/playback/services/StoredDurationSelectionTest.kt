package app.n_zik.android.playback.services

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import app.n_zik.android.models.Song
import app.n_zik.android.playback.services.automotive.session.enrichAutoQueueDurations
import app.n_zik.android.utils.coroutines.NzikDispatchers
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class StoredDurationSelectionTest {
    private val scope = NzikDispatchers.fireAndForget(NzikDispatchers.UI)
    private val source = TestPlayer()
    private var activePlayer = source.player
    private var profile = "default"
    private val owners = mutableListOf<StoredDurationSelectionOwner>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    @After
    fun tearDown() {
        owners.forEach { it.close() }
        scope.cancel()
        gates.forEach { it.complete(Unit) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `known duration is present on first publication and the play tail remains atomic`() {
        var readOnMain: Boolean? = null
        val owner = owner { items, _ ->
            enrichAutoQueueDurations(items) {
                readOnMain = Looper.myLooper() === Looper.getMainLooper()
                listOf(Song("song", "Stored", durationText = "2:37", thumbnailUrl = null))
            }
        }
        val operations = mutableListOf<String>()
        val job = owner.launch(source.player) { selection ->
            val items = selection.enrich(listOf(item("song")))
            selection.publish {
                assertEquals(157_000L, items.single().mediaMetadata.durationMs)
                assertTrue(Looper.myLooper() === Looper.getMainLooper())
                operations += "setMediaItems"
                source.change(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
                operations += listOf("prepare", "volume", "playWhenReady")
            }
        }
        pumpUntil { job.isCompleted }
        assertFalse(job.isCancelled)
        assertEquals(false, readOnMain)
        assertEquals(listOf("setMediaItems", "prepare", "volume", "playWhenReady"), operations)
        assertEquals(1, source.listeners.size)
    }

    @Test
    fun `latest tap owns publication even when older database work cannot be cancelled`() {
        val first = gate()
        val entered = CopyOnWriteArrayList<String>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ ->
            val id = items.single().mediaId
            entered += id
            if (id == "first") withContext(NonCancellable) { first.await() }
            items
        }
        val old = select(owner, "first", published)
        pumpUntil { "first" in entered }
        val latest = select(owner, "latest", published)
        pumpUntil { latest.isCompleted }
        first.complete(Unit)
        pumpUntil { old.isCompleted }
        assertTrue(old.isCancelled)
        assertEquals(listOf("latest"), published)
        assertEquals(1, source.listeners.size)
    }

    @Test
    fun `foreign playlist replacement invalidates a pending selection but source update does not`() {
        val ready = gate()
        val entered = CopyOnWriteArrayList<String>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ ->
            entered += items.single().mediaId
            ready.await()
            items
        }
        val old = select(owner, "old", published)
        pumpUntil { entered.isNotEmpty() }
        source.change(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        assertFalse(old.isCancelled)
        source.change(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        assertTrue(old.isCancelled)
        assertEquals(1, source.listeners.size)
        ready.complete(Unit)
        pumpUntil { old.isCompleted }
        assertTrue(published.isEmpty())
    }

    @Test
    fun `profile change and facade replacement each discard the pending result`() {
        for (changeProfile in listOf(true, false)) {
            val ready = gate()
            val entered = CompletableDeferred<Unit>()
            val published = mutableListOf<String>()
            val capturedProfiles = CopyOnWriteArrayList<String>()
            activePlayer = source.player
            profile = "default"
            val owner = owner { items, captured ->
                capturedProfiles += captured
                entered.complete(Unit)
                ready.await()
                items
            }
            val job = select(owner, "song", published)
            pumpUntil { entered.isCompleted }
            if (changeProfile) profile = "other" else activePlayer = TestPlayer().player
            ready.complete(Unit)
            pumpUntil { job.isCompleted }
            assertTrue(job.isCancelled)
            assertEquals(listOf("default"), capturedProfiles.toList())
            assertTrue(published.isEmpty())
            owner.close()
            assertTrue(source.listeners.isEmpty())
        }
    }

    @Test
    fun `close removes listener immediately even while an uncancellable read is still pending`() {
        val ready = gate()
        val entered = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ ->
            entered.complete(Unit)
            withContext(NonCancellable) { ready.await() }
            items
        }
        val job = select(owner, "song", published)
        pumpUntil { entered.isCompleted }
        owner.close()
        assertTrue(source.listeners.isEmpty())
        ready.complete(Unit)
        pumpUntil { job.isCompleted }
        assertTrue(job.isCancelled)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `explicit cancellation never publishes and leaves only the service observer`() {
        val ready = gate()
        val entered = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ -> entered.complete(Unit); ready.await(); items }
        val job = select(owner, "song", published)
        pumpUntil { entered.isCompleted }
        job.cancel()
        pumpUntil { job.isCompleted }
        assertEquals(1, source.listeners.size)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `off-main callers capture ownership without touching player until UI dispatch`() {
        val published = mutableListOf<String>()
        val owner = owner { items, _ -> items }
        val launched = CompletableDeferred<Job>()
        scope.launch(NzikDispatchers.DATA) {
            launched.complete(select(owner, "off-main", published))
        }
        pumpUntil { launched.isCompleted && published.isNotEmpty() }
        assertEquals(listOf("off-main"), published)
    }

    @Test
    fun `source update after publication conservatively suppresses stale metadata replacement`() {
        val resume = gate()
        val published = CompletableDeferred<Unit>()
        var metadataReplaced = false
        val owner = owner { items, _ -> items }
        val job = owner.launch(source.player) { selection ->
            selection.publish { published.complete(Unit) }
            resume.await()
            withContext(NzikDispatchers.UI) {
                selection.ensureCurrent()
                metadataReplaced = true
            }
        }
        pumpUntil { published.isCompleted }
        assertEquals(1, source.listeners.size)
        source.change(Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE)
        resume.complete(Unit)
        pumpUntil { job.isCompleted }
        assertTrue(job.isCancelled)
        assertFalse(metadataReplaced)
    }

    @Test
    fun `unbound temporary player retains publication without a database lookup or listener`() {
        var published = false
        val original = item("temporary")
        val job = launchStoredDurationSelection(source.player) { selection ->
            val items = selection.enrich(listOf(original))
            selection.publish {
                assertEquals(listOf(original), items)
                published = true
            }
        }
        pumpUntil { job.isCompleted }
        assertFalse(job.isCancelled)
        assertTrue(published)
        assertTrue(source.listeners.isEmpty())
    }

    @Test
    fun `foreign replacement after off-main launch but before UI entry cancels publication`() {
        val published = mutableListOf<String>()
        val owner = owner { items, _ -> items }
        val job = launchOffMain { select(owner, "stale", published) }
        // No UI pump has occurred since launch: only the already-bound observer can see this.
        source.change(Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
        pumpUntil { job.isCompleted }
        assertTrue(job.isCancelled)
        assertTrue(published.isEmpty())
        assertEquals(0, owner.pendingRequestCount)
    }

    @Test
    fun `cancellation before default coroutine entry still releases registry ownership`() {
        val published = mutableListOf<String>()
        val owner = owner { items, _ -> items }
        val job = launchOffMain { select(owner, "cancelled", published) }
        assertEquals(1, owner.pendingRequestCount)
        job.cancel()
        pumpUntil { job.isCompleted }
        assertTrue(published.isEmpty())
        assertEquals(0, owner.pendingRequestCount)
        val next = select(owner, "next", published)
        pumpUntil { next.isCompleted }
        assertEquals(listOf("next"), published)
    }

    @Test
    fun `deferred own playlist callback cannot cancel a later pending selection`() {
        val ready = gate()
        val secondEntered = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ ->
            if (items.single().mediaId == "second") {
                secondEntered.complete(Unit)
                ready.await()
            }
            items
        }
        var deliverOwnCallback: (() -> Unit)? = null
        val first = owner.launch(source.player) { selection ->
            selection.publish { deliverOwnCallback = source.deferredPlaylistChange() }
        }
        pumpUntil { first.isCompleted }
        val second = select(owner, "second", published)
        pumpUntil { secondEntered.isCompleted }
        checkNotNull(deliverOwnCallback).invoke()
        assertFalse(second.isCancelled)
        ready.complete(Unit)
        pumpUntil { second.isCompleted }
        assertEquals(listOf("second"), published)
    }

    @Test
    fun `real ExoPlayer playlist notification preserves owned publication and following selection`() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        activePlayer = player
        val owner = owner { items, _ -> items }
        try {
            val first = owner.launch(player) { selection ->
                selection.publish { player.setMediaItem(item("first")) }
            }
            pumpUntil { first.isCompleted }
            assertFalse(first.isCancelled)
            val second = owner.launch(player) { selection ->
                val items = selection.enrich(listOf(item("second")))
                selection.publish { player.setMediaItems(items) }
            }
            pumpUntil { second.isCompleted }
            assertFalse(second.isCancelled)
            assertEquals("second", player.currentMediaItem?.mediaId)
        } finally {
            owner.close()
            player.release()
        }
    }

    @Test
    fun `facade refresh moves the service observer and cancels old requests`() {
        val ready = gate()
        val entered = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        val owner = owner { items, _ -> entered.complete(Unit); ready.await(); items }
        val old = select(owner, "old", published)
        pumpUntil { entered.isCompleted }
        val replacement = TestPlayer()
        activePlayer = replacement.player
        owner.refreshPlayer()
        assertTrue(source.listeners.isEmpty())
        assertEquals(1, replacement.listeners.size)
        pumpUntil { old.isCompleted }
        assertTrue(old.isCancelled)
        assertTrue(published.isEmpty())
        owner.close()
        assertTrue(replacement.listeners.isEmpty())
    }

    @Test
    fun `bound facade notification admits new binder selection without changing player trigger`() {
        val trigger = MutableStateFlow(0)
        val binding = bindStoredDurationSelections(
            scope,
            trigger,
            currentProfile = { profile },
            enrich = { items, _ -> items },
        ) { activePlayer }
        val replacement = TestPlayer()
        try {
            assertEquals(1, source.listeners.size)
            activePlayer = replacement.player
            // This is the service's assignment boundary, before the MediaSession setter.
            notifyStoredDurationPlayerChanged()
            assertEquals(0, trigger.value)
            assertTrue(source.listeners.isEmpty())
            assertEquals(1, replacement.listeners.size)
            var published = false
            val job = launchStoredDurationSelection(replacement.player) { selection ->
                selection.enrich(listOf(item("new-binder")))
                selection.publish { published = true }
            }
            pumpUntil { job.isCompleted }
            assertFalse(job.isCancelled)
            assertTrue(published)
        } finally {
            binding.cancel()
            pumpUntil { binding.isCompleted }
        }
        assertTrue(source.listeners.isEmpty())
        assertTrue(replacement.listeners.isEmpty())
    }

    private fun owner(enrich: suspend (List<MediaItem>, String) -> List<MediaItem>) =
        StoredDurationSelectionOwner(scope, { activePlayer }, { profile }, enrich).also {
            owners += it
            it.refreshPlayer()
        }

    private fun launchOffMain(block: () -> Job): Job {
        val result = AtomicReference<Job>()
        val launched = CountDownLatch(1)
        scope.launch(NzikDispatchers.DATA) {
            result.set(block())
            launched.countDown()
        }
        assertTrue("Off-main request did not launch", launched.await(3, TimeUnit.SECONDS))
        return checkNotNull(result.get())
    }

    private fun select(owner: StoredDurationSelectionOwner, id: String, published: MutableList<String>): Job =
        owner.launch(source.player) { selection ->
            val items = selection.enrich(listOf(item(id)))
            selection.publish { published += items.single().mediaId }
        }

    private fun gate() = CompletableDeferred<Unit>().also { gates += it }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id").build()

    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for selection", condition())
    }

    private class TestPlayer {
        val listeners = mutableListOf<Player.Listener>()
        private var timeline = mockk<Timeline>()
        val player = mockk<Player>(relaxed = true) {
            every { applicationLooper } returns Looper.getMainLooper()
            every { currentTimeline } answers { timeline }
            every { addListener(any()) } answers { listeners += firstArg<Player.Listener>(); Unit }
            every { removeListener(any()) } answers { listeners -= firstArg<Player.Listener>(); Unit }
        }

        fun change(reason: Int) {
            timeline = mockk()
            listeners.toList().forEach { it.onTimelineChanged(timeline, reason) }
        }

        fun deferredPlaylistChange(): () -> Unit {
            timeline = mockk()
            val published = timeline
            return { listeners.toList().forEach {
                it.onTimelineChanged(published, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            } }
        }
    }
}
