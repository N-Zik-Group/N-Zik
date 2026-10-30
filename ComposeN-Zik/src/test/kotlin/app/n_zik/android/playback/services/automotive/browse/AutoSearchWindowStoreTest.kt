package app.n_zik.android.playback.services.automotive.browse

import android.app.Application
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoSearchWindowStoreTest {
    private fun batch(id: String, continuation: String? = null) = AutoSearchWindowStore.Batch(
        listOf(MediaItem.Builder().setMediaId(id).build()), continuation,
    )

    @Test
    fun `concurrent duplicate Suite advances once replaces batch and consumed token is idempotent`() = runTest {
        val store = AutoSearchWindowStore()
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = requireNotNull(store.load("SEARCH_SONGS/AC/DC", "A", { "A" }) { batch("first", "cursor") })
        assertEquals(1, first.pageNumber)
        val token = requireNotNull(first.nextToken)
        val advance: suspend () -> AutoSearchWindowStore.Window? = {
            store.load(first.parentId, "A", { "A" }, token) { cursor ->
                calls++
                assertEquals("cursor", cursor)
                entered.complete(Unit)
                release.await()
                batch("second", "cursor2")
            }
        }
        val a = async { advance() }
        entered.await()
        val b = async { advance() }
        release.complete(Unit)
        val result = requireNotNull(a.await())
        assertEquals(listOf("second"), result.items.map { it.mediaId })
        assertEquals(result, b.await())
        assertEquals(2, result.pageNumber)
        assertNotEquals(token, result.nextToken)
        assertEquals(result, advance())
        assertEquals(1, calls)
    }

    @Test
    fun `provider error retains current window and the same token retries to the end`() = runTest {
        val store = AutoSearchWindowStore()
        val first = requireNotNull(store.load("SEARCH_VIDEOS/rock/pop", "A", { "A" }) { batch("first", "cursor") })
        val failed = store.load(first.parentId, "A", { "A" }, first.nextToken) { error("provider failed") }
        assertEquals(first, failed)
        val last = store.load(first.parentId, "A", { "A" }, first.nextToken) { batch("last") }
        assertEquals(listOf("last"), last?.items?.map { it.mediaId })
        assertNull(last?.nextToken)
    }

    @Test
    fun `new search invalidates an inflight batch without overwriting a newer window`() = runTest {
        val store = AutoSearchWindowStore()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pending = async {
            store.load("SEARCH_SONGS/old", "A", { "A" }) {
                entered.complete(Unit); release.await(); batch("stale", "cursor")
            }
        }
        entered.await()
        store.clear()
        val fresh = store.load("SEARCH_SONGS/new", "A", { "A" }) { batch("fresh", "new-cursor") }
        release.complete(Unit)
        assertNull(pending.await())
        assertEquals(fresh, store.load("SEARCH_SONGS/new", "A", { "A" }) { error("must reuse") })
    }

    @Test
    fun `profile and offline transitions reject pending publication and cross-profile routes`() = runTest {
        for (nextOwner in listOf(null, "B")) {
            val store = AutoSearchWindowStore()
            var owner: String? = "A"
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val pending = async {
                store.load("SEARCH_ALBUMS/query", "A", { owner }) {
                    entered.complete(Unit); release.await(); batch("private", "cursor")
                }
            }
            entered.await()
            owner = nextOwner
            release.complete(Unit)
            assertNull(pending.await())
        }
        val store = AutoSearchWindowStore()
        val a = requireNotNull(store.load("SEARCH_ALBUMS/query", "A", { "A" }) { batch("A", "cursor") })
        assertNull(store.parentFor(requireNotNull(a.nextToken), "B"))
    }

    @Test
    fun `categories and exact slash queries stay isolated and retention is bounded`() = runTest {
        val store = AutoSearchWindowStore(maxWindows = 2, maxTokens = 2)
        val a = requireNotNull(store.load("SEARCH_SONGS/AC/DC", "A", { "A" }) { batch("song", "s") })
        val b = requireNotNull(store.load("SEARCH_VIDEOS/AC/DC", "A", { "A" }) { batch("video", "v") })
        assertEquals(a, store.load(a.parentId, "A", { "A" }) { error("reuse") })
        assertEquals(b, store.load(b.parentId, "A", { "A" }) { error("reuse") })
        store.load("SEARCH_SONGS/AC", "A", { "A" }) { batch("different", "x") }
        assertNull(store.parentFor(requireNotNull(a.nextToken), "A"))
        assertTrue(store.parentFor(requireNotNull(b.nextToken), "A")?.contains("AC/DC") == true)
    }
    @Test
    fun `consumed token budget never evicts another category current continuation`() = runTest {
        val store = AutoSearchWindowStore(maxWindows = 2, maxTokens = 1)
        val albums = requireNotNull(store.load("SEARCH_ALBUMS/query", "A", { "A" }) { batch("album", "a") })
        var songs = requireNotNull(store.load("SEARCH_SONGS/query", "A", { "A" }) { batch("song", "s") })
        repeat(5) {
            songs = requireNotNull(store.load(songs.parentId, "A", { "A" }, songs.nextToken) { batch("song-$it", "s-$it") })
        }
        assertEquals(albums.parentId, store.parentFor(requireNotNull(albums.nextToken), "A"))
        val last = store.load(albums.parentId, "A", { "A" }, albums.nextToken) { batch("album-next") }
        assertEquals(listOf("album-next"), last?.items?.map { it.mediaId })
    }

}
