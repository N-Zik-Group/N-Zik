package app.n_zik.android.components.player

import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.models.Song
import app.n_zik.android.utils.asSong
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
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

    private fun media(
        id: String,
        title: String,
        artist: String = "",
        duration: String? = null,
        durationMs: Long? = null,
    ) =
        MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .setDurationMs(durationMs)
                .setExtras(Bundle().apply { duration?.let { putString("durationText", it) } }).build()
        ).build()

    private fun window(
        uid: String,
        item: MediaItem,
        durationMs: Long? = null,
        placeholder: Boolean = false,
    ) = Timeline.Window().apply {
        this.uid = uid
        mediaItem = item
        durationUs = durationMs?.times(1000) ?: C.TIME_UNSET
        isPlaceholder = placeholder
    }

    private fun timeline(vararg windows: Timeline.Window): Timeline = mockk<Timeline>().also { timeline ->
        every { timeline.windowCount } returns windows.size
        every { timeline.isEmpty } returns windows.isEmpty()
        every { timeline.getWindow(any(), any()) } answers { windows[firstArg()] }
    }

    @Test
    fun `first nonempty timeline supplies placeholders until its rows arrive`() {
        val windows = listOf(window("first", media("first", "First")), window("second", media("second", "Second")))
        val release = CompletableDeferred<Unit>()
        var snapshot = PlayerQueueSnapshot.Empty
        compose.setContent {
            snapshot = rememberPlayerQueueSnapshot(windows, "", normalizeQueue = { source, previous ->
                release.await()
                normalizePlayerQueue(source, previous)
            })
        }
        compose.runOnIdle {
            assertEquals(2, snapshot.initialPlaceholderCount(windows.size))
            assertEquals(0, snapshot.initialPlaceholderCount(0))
            release.complete(Unit)
        }
        compose.waitUntil(5_000) { snapshot.rows.size == 2 }
        compose.runOnIdle { assertEquals(0, snapshot.initialPlaceholderCount(windows.size)) }
    }

    @Test
    fun `queue duration stays visible on source updates and resets for different items`() {
        val item = media("same", "Same track", duration = "1:00")
        val replacement = media("other", "Different track", duration = "2:00")
        val first = timeline(window("same", item))
        val second = timeline(window("same", item, durationMs = 61_789L))
        val third = timeline(window("other", replacement))
        val source = mutableStateOf(first, referentialEqualityPolicy())
        val readings = CopyOnWriteArrayList<Pair<Timeline, PlayerQueueDuration>>()
        compose.setContent {
            val captured = source.value
            val items = if (captured === third) listOf(replacement) else listOf(item)
            val total = rememberPlayerQueueDuration(captured, items, enabled = true, profile = "default")
            SideEffect { readings.add(captured to total) }
        }
        compose.waitUntil(5_000) { (first to PlayerQueueDuration.Known(60_000L)) in readings }
        applySnapshotChanges { source.value = second }
        compose.waitUntil(5_000) { (second to PlayerQueueDuration.Known(61_789L)) in readings }
        applySnapshotChanges { source.value = third }
        compose.waitUntil(5_000) { (third to PlayerQueueDuration.Known(120_000L)) in readings }
        applySnapshotChanges { source.value = Timeline.EMPTY }
        compose.waitUntil(5_000) { (Timeline.EMPTY to PlayerQueueDuration.Known(0L)) in readings }
        assertTrue((first to PlayerQueueDuration.Loading) in readings)
        assertFalse((second to PlayerQueueDuration.Loading) in readings)
        assertTrue((second to PlayerQueueDuration.Known(60_000L)) in readings)
        assertTrue((third to PlayerQueueDuration.Loading) in readings)
        assertFalse((third to PlayerQueueDuration.Known(61_789L)) in readings)
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
    fun `total uses resolved and declared lengths plus a batched stored fallback for each occurrence`() = runBlocking {
        // JUnit's synchronous API bridges to production MEDIA calculation and DATA lookup.
        val stored = media("stored", "Stored duration", duration = "00:00")
        val windows = arrayOf(
            window("first", media("same", "One", duration = "1:23", durationMs = 84_123L), durationMs = 84_987L),
            window("second", media("same", "Two", duration = "1:02:03", durationMs = 3_724_567L),
                durationMs = 5_000L, placeholder = true),
            window("legacy", media("legacy", "Legacy duration", duration = "2:01")),
            window("stored-one", stored),
            window("stored-two", stored),
        )
        val source = timeline(*windows)
        every { source.getWindow(any(), any()) } answers {
            assertTrue(Thread.currentThread().name.startsWith("nzik-media-"))
            windows[firstArg()]
        }
        val batches = mutableListOf<List<String>>()
        val duration = playerQueueDuration(source) { ids ->
            batches.add(ids)
            listOf(Song.makePlaceholder("stored").copy(durationText = "1:05"))
        }
        assertEquals(listOf(listOf("stored")), batches)
        assertEquals(PlayerQueueDuration.Known(4_060_554L), duration)
    }

    @Test
    fun `unknown tracks do not leave a partial total or turn an unresolved queue into zero`() = runBlocking {
        // JUnit's synchronous API bridges to the production suspend calculation.
        val known = window("known", media("known", "Known length", durationMs = 170_000L))
        val invalid = window("invalid", media("invalid", "Invalid duration", duration = "5:91"))
        val missing = window("missing", media("missing", "Missing duration", durationMs = 0L))
        assertEquals(PlayerQueueDuration.Unavailable, playerQueueDuration(timeline(known, invalid, missing)) { ids ->
            assertEquals(listOf("invalid", "missing"), ids)
            listOf(Song.makePlaceholder("invalid").copy(durationText = "1:00"))
        })
        assertEquals(PlayerQueueDuration.Unavailable, playerQueueDuration(timeline(missing)) { emptyList() })
        assertEquals(PlayerQueueDuration.Known(0L), playerQueueDuration(Timeline.EMPTY) { error("No lookup for empty queue") })
    }

    @Test
    fun `live uid lookup keeps duplicate tracks distinct`() {
        val item = media("same", "Track")
        val source = timeline(window("two", item), window("one", item))
        assertEquals(1, findPlayerQueueWindowIndex(source, "one"))
        assertEquals(0, findPlayerQueueWindowIndex(source, "two"))
        assertEquals(-1, findPlayerQueueWindowIndex(source, "removed"))
    }
}
