package app.n_zik.android.utils.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.n_zik.android.utils.addNext
import app.n_zik.android.utils.enqueue
import app.n_zik.android.utils.excludeMediaItems
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Issue #606 H8 (+M10 at call sites that also feed `Song.asMediaItem` through here) --
 * `Player.addNext`/`enqueue` (`app.n_zik.android.utils.Player.kt`, legacy, out of scope for
 * editing per AD-4 tier 1) filter the incoming list through `excludeMediaItems`, which does up to
 * 3 `runBlocking` DB reads whenever the caller passes a non-null [Context]. Every legacy caller
 * used to pass that context straight through on whatever thread it was already on -- Main, for
 * every call site this lot fixes -- blocking the UI on those DB reads.
 *
 * [Player.addNextOffMain] / [Player.enqueueOffMain] are the caller-side fix: run the filtering on
 * `NzikDispatchers.DATA`, then call the legacy `addNext`/`enqueue` with `context = null` so its
 * own filtering branch is skipped (the list has already been filtered) and the queue mutation
 * itself happens exactly once. This test mocks the legacy `PlayerKt` top-level functions (the same
 * technique `ShufflerTest` uses for `forcePlayFromBeginning`) so it can assert, in isolation, that:
 * (1) the filtered list -- not the original -- is what reaches `addNext`/`enqueue`, (2) `context`
 * reaching `addNext`/`enqueue` is `null` (so `excludeMediaItems` cannot run a second time inside
 * it), and (3) the filtering step actually runs off the caller's thread.
 */
class PlayerQueueOffMainTest {

    private fun mediaItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    @BeforeEach
    fun setup() {
        mockkStatic("app.n_zik.android.utils.PlayerKt")
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `addNextOffMain calls addNext with the filtered list and a null context`() = runBlocking {
        val player = mockk<Player>(relaxed = true)
        val context = mockk<Context>()
        val original = listOf(mediaItem("a"), mediaItem("b"))
        val filtered = listOf(mediaItem("a"))
        every { player.excludeMediaItems(original, context) } returns filtered
        every { player.addNext(any<List<MediaItem>>(), any()) } just Runs

        player.addNextOffMain(original, context)

        verify(exactly = 1) { player.excludeMediaItems(original, context) }
        verify(exactly = 1) { player.addNext(filtered, null) }
        verify(exactly = 0) { player.addNext(original, context) }
    }

    @Test
    fun `enqueueOffMain calls enqueue with the filtered list and a null context`() = runBlocking {
        val player = mockk<Player>(relaxed = true)
        val context = mockk<Context>()
        val original = listOf(mediaItem("a"), mediaItem("b"))
        val filtered = listOf(mediaItem("b"))
        every { player.excludeMediaItems(original, context) } returns filtered
        every { player.enqueue(any<List<MediaItem>>(), any()) } just Runs

        player.enqueueOffMain(original, context)

        verify(exactly = 1) { player.excludeMediaItems(original, context) }
        verify(exactly = 1) { player.enqueue(filtered, null) }
        verify(exactly = 0) { player.enqueue(original, context) }
    }

    @Test
    fun `addNextOffMain runs excludeMediaItems off the caller's thread`() = runBlocking {
        val player = mockk<Player>(relaxed = true)
        val context = mockk<Context>()
        val items = listOf(mediaItem("a"))
        val callerThreadName = Thread.currentThread().name
        var filterThreadName: String? = null
        every { player.excludeMediaItems(items, context) } answers {
            filterThreadName = Thread.currentThread().name
            items
        }
        every { player.addNext(any<List<MediaItem>>(), any()) } just Runs

        player.addNextOffMain(items, context)

        assertNotEquals(
            "excludeMediaItems must not run on the caller's (UI-simulating) thread once dispatched via addNextOffMain",
            callerThreadName,
            filterThreadName
        )
    }

    @Test
    fun `enqueueOffMain runs excludeMediaItems off the caller's thread`() = runBlocking {
        val player = mockk<Player>(relaxed = true)
        val context = mockk<Context>()
        val items = listOf(mediaItem("a"))
        val callerThreadName = Thread.currentThread().name
        var filterThreadName: String? = null
        every { player.excludeMediaItems(items, context) } answers {
            filterThreadName = Thread.currentThread().name
            items
        }
        every { player.enqueue(any<List<MediaItem>>(), any()) } just Runs

        player.enqueueOffMain(items, context)

        assertNotEquals(
            "excludeMediaItems must not run on the caller's (UI-simulating) thread once dispatched via enqueueOffMain",
            callerThreadName,
            filterThreadName
        )
    }
}
