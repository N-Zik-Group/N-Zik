package app.n_zik.android.components.player

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.durationTextToMillis
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerQueueProcessingTest {
    @get:Rule val compose = createComposeRule()

    private fun media(id: String, title: String, artist: String = "", duration: String? = null) =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setExtras(Bundle().apply { duration?.let { putString("durationText", it) } }).build()
        ).build()

    private fun window(uid: String, item: MediaItem) = Timeline.Window().apply {
        this.uid = uid
        mediaItem = item
    }

    @Test
    fun `bulk actions resolve latest detached queue while retained visual rows are stale`() = runBlocking {
        // JUnit's synchronous API bridges to the production MEDIA normalization.
        val retained = window("retained", media("keep", "Kept"))
        val removed = window("removed", media("remove", "Removed"))
        val changed = window("changed", media("change", "Old title"))
        val original = listOf(removed, retained, changed)
        val snapshot = filterPlayerQueue(normalizePlayerQueue(original), "Removed")
        val updated = window("changed", media("change", "New title"))
        val added = window("added", media("new", "New track"))
        val latest = listOf(updated, retained, added, window("duplicate", added.mediaItem))
        var conversions = 0

        val songs = playerQueueBulkSongs(snapshot, latest, emptyList()) {
            conversions++
            it.asSong
        }

        assertEquals(listOf("change", "keep", "new", "new"), songs.map { it.id })
        assertEquals("New title", songs.first().title)
        assertSame(snapshot.allSongs[1], songs[1])
        assertEquals(3, conversions)
        assertEquals(listOf("remove"), snapshot.songs.map { it.id })
        assertTrue(playerQueueBulkSongs(snapshot, emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `bulk selection keeps its order but excludes removed songs and refreshes metadata`() = runBlocking {
        // JUnit's synchronous API bridges to the production MEDIA normalization.
        val original = listOf(window("one", media("one", "Old")), window("two", media("two", "Removed")))
        val snapshot = filterPlayerQueue(normalizePlayerQueue(original), "")
        val latest = listOf(window("one", media("one", "Current")), window("three", media("three", "Added")))
        val selected = snapshot.allSongs.reversed()

        val songs = playerQueueBulkSongs(snapshot, latest, selected)

        assertEquals(listOf("one"), songs.map { it.id })
        assertEquals("Current", songs.single().title)
        assertSame(snapshot.allSongs, playerQueueBulkSongs(snapshot, original, emptyList()) {
            error("A current snapshot must not reconvert metadata")
        })
    }

    @Test
    fun `normalization and search preserve cleaning order duplicates and cached songs on MEDIA`() = runBlocking {
        // JUnit's synchronous API needs a blocking bridge; all production work dispatches to MEDIA.
        val caller = Thread.currentThread()
        val windows = listOf(
            window("first", media("same", "e:Alpha One", "The Artist")),
            window("second", media("same", "Beta", "Other")),
        )
        var conversions = 0
        val normalized = normalizePlayerQueue(windows, songOf = {
            assertTrue(Thread.currentThread() !== caller)
            assertTrue(Thread.currentThread().name.startsWith("nzik-media-"))
            conversions++
            it.asSong
        })
        for (query in listOf("", "ALPHA", "artist", "e:", " ", "absent")) {
            val result = filterPlayerQueue(normalized, query)
            val expected = windows.filter {
                val song = it.mediaItem.asSong
                song.cleanTitle().contains(query, true) || song.cleanArtistsText().contains(query, true)
            }
            assertEquals(expected.map { it.uid }, result.rows.map { it.window.uid })
            assertEquals(expected.map { it.mediaItem.asSong }, result.songs)
        }
        val reorderedWindows = windows.reversed()
        val reordered = normalizePlayerQueue(reorderedWindows, normalized) { error("cached drag must not reconvert") }
        assertEquals(2, conversions)
        assertSame(normalized.rows[1].song, reordered.rows[0].song)
        assertEquals(listOf("second", "first"), filterPlayerQueue(reordered, "").rows.map { it.window.uid })
        val changed = listOf(window("first", media("same", "Fresh title")), windows[1])
        val refreshed = normalizePlayerQueue(changed, normalized)
        assertEquals("Fresh title", refreshed.rows[0].song.title)
    }

    @Test
    fun `cancelled normalization stops before the next item and cannot publish`() = runBlocking {
        // The worker is deliberately held to cancel it deterministically from JUnit's sync API.
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        var published = false
        val windows = List(20) { window("$it", media("$it", "Title $it")) }
        val work = async(start = CoroutineStart.UNDISPATCHED) {
            normalizePlayerQueue(windows, songOf = {
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                it.asSong
            })
            published = true
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            work.cancel()
        } finally {
            release.countDown()
        }
        work.join()
        assertTrue(work.isCancelled)
        assertFalse(published)
        assertEquals(1, calls.get())
    }

    @Test
    fun `new query cancels stale result while retaining visual rows and normalized metadata`() {
        val windows = listOf(window("one", media("one", "Alpha")), window("two", media("two", "Beta")))
        val input = mutableStateOf("")
        val slowStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        val releaseSlow = CompletableDeferred<Unit>()
        val normalizations = AtomicInteger()
        var result = PlayerQueueSnapshot.Empty
        compose.setContent {
            result = rememberPlayerQueueSnapshot(windows, input.value,
                normalizeQueue = { source, previous ->
                    normalizations.incrementAndGet()
                    normalizePlayerQueue(source, previous)
                },
                filterQueue = { normalized, query ->
                    if (query == "slow") {
                        slowStarted.complete(Unit)
                        try {
                            releaseSlow.await()
                        } finally {
                            slowCancelled.complete(Unit)
                        }
                    }
                    filterPlayerQueue(normalized, query)
                })
        }
        compose.waitUntil(5_000) { result.rows.size == 2 }
        applySnapshotChanges { input.value = "slow" }
        compose.waitUntil(5_000) { slowStarted.isCompleted }
        applySnapshotChanges {
            assertEquals(2, result.rows.size)
            input.value = "Beta"
        }
        compose.waitUntil(5_000) { result.input == "Beta" && slowCancelled.isCompleted }
        applySnapshotChanges {
            releaseSlow.complete(Unit)
            assertEquals(listOf("two"), result.rows.map { it.window.uid })
            assertEquals(1, normalizations.get())
        }
        compose.waitForIdle()
        applySnapshotChanges { assertEquals("Beta", result.input) }
    }

    @Test
    fun `timeline replacement cancels delayed normalization and retains rows until newest source publishes`() {
        val original = listOf(window("original", media("same", "Original")))
        val delayed = listOf(window("delayed", media("same", "Obsolete")))
        val latest = listOf(window("latest", media("same", "Latest")))
        val source = mutableStateOf(original)
        val delayedStarted = CompletableDeferred<Unit>()
        val delayedCancelled = CompletableDeferred<Unit>()
        val releaseDelayed = CompletableDeferred<Unit>()
        val latestStarted = CompletableDeferred<Unit>()
        val releaseLatest = CompletableDeferred<Unit>()
        var result = PlayerQueueSnapshot.Empty
        compose.setContent {
            result = rememberPlayerQueueSnapshot(source.value, "",
                normalizeQueue = { windows, previous ->
                    when {
                        windows === delayed -> {
                            delayedStarted.complete(Unit)
                            try {
                                releaseDelayed.await()
                            } finally {
                                delayedCancelled.complete(Unit)
                            }
                        }
                        windows === latest -> {
                            latestStarted.complete(Unit)
                            releaseLatest.await()
                        }
                    }
                    normalizePlayerQueue(windows, previous)
                })
        }
        compose.waitUntil(5_000) { result.source === original }
        applySnapshotChanges { source.value = delayed }
        compose.waitUntil(5_000) { delayedStarted.isCompleted }
        applySnapshotChanges {
            assertSame(original, result.source)
            assertEquals(listOf("original"), result.rows.map { it.window.uid })
            source.value = latest
        }
        compose.waitUntil(5_000) { latestStarted.isCompleted && delayedCancelled.isCompleted }
        applySnapshotChanges {
            assertSame(original, result.source)
            releaseDelayed.complete(Unit)
        }
        compose.waitForIdle()
        applySnapshotChanges {
            assertSame(original, result.source)
            releaseLatest.complete(Unit)
        }
        compose.waitUntil(5_000) { result.source === latest }
        applySnapshotChanges {
            assertEquals(listOf("latest"), result.rows.map { it.window.uid })
            assertEquals(listOf("Latest"), result.songs.map { it.title })
        }
    }

    private fun applySnapshotChanges(change: () -> Unit) {
        compose.runOnIdle {
            change()
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
    }

    @Test
    fun `equal detached timeline captures still publish latest source identity and query`() {
        val original = listOf(window("same-window", media("same", "Alpha")))
        val detached = original.map { window(it.uid.toString(), it.mediaItem) }
        assertEquals(original, detached)
        val source = mutableStateOf(original, referentialEqualityPolicy())
        val input = mutableStateOf("")
        var result = PlayerQueueSnapshot.Empty
        compose.setContent { result = rememberPlayerQueueSnapshot(source.value, input.value) }
        compose.waitUntil(5_000) { result.source === original }
        applySnapshotChanges { source.value = detached }
        compose.waitUntil(5_000) { result.source === detached }
        applySnapshotChanges { input.value = "Alpha" }
        compose.waitUntil(5_000) { result.input == "Alpha" }
        compose.runOnIdle {
            assertSame(detached, result.source)
            assertSame(detached[0], result.rows.single().window)
            assertEquals(listOf("same"), result.songIds)
        }
    }

    @Test
    fun `duration parsing runs on MEDIA and live uid lookup keeps duplicate tracks distinct`() = runBlocking {
        // Synchronous test bridge; production parsing dispatches independently of the caller.
        val items = listOf(media("same", "One", duration = "1:23"),
            media("same", "Two", duration = "1:02:03"), media("bad", "Bad", duration = "invalid"),
            media("missing", "Missing"))
        val caller = Thread.currentThread()
        var parsed = 0
        val duration = playerQueueDuration(items) {
            assertTrue(Thread.currentThread() !== caller)
            assertTrue(Thread.currentThread().name.startsWith("nzik-media-"))
            parsed++
            durationTextToMillis(it)
        }
        assertEquals(3, parsed)
        assertEquals(3_806_000L, duration)
        val liveWindows = listOf(window("two", items[1]), window("one", items[0]))
        val timeline = mockk<Timeline>()
        every { timeline.windowCount } returns liveWindows.size
        every { timeline.getWindow(any(), any()) } answers { liveWindows[firstArg()] }
        assertEquals(1, findPlayerQueueWindowIndex(timeline, "one"))
        assertEquals(0, findPlayerQueueWindowIndex(timeline, "two"))
        assertEquals(-1, findPlayerQueueWindowIndex(timeline, "removed"))
    }
}
