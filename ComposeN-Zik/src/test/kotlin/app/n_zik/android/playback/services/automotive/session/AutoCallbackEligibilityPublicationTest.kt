package app.n_zik.android.playback.services.automotive.session

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Pins ownership after the final suspending eligibility check of each public callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoCallbackEligibilityPublicationTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private val releaseEligibility = CompletableDeferred<Unit>()
    private val entered = CountDownLatch(1)
    private var online = true
    private var originalProfile: String? = null
    private var originalPersistentQueue: Boolean? = null
    private val parent = "${AutoSessionConstants.ID_SEARCH_SONGS}/publication"

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } answers { online }
        originalProfile = context.profilePreferences.getString(activeProfileKey, null)
        switchProfile("A")
        originalPersistentQueue = if (context.preferences.contains(persistentQueueKey)) {
            context.preferences.getBoolean(persistentQueueKey, false)
        } else null
        mockkObject(Database)
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        every { songs.findByIdDirect("selected") } returns Song("selected", "Selected", "Artist", "3:00", null)
        val queue = mockk<QueuedMediaItemTable>()
        every { Database.queueTable } returns queue
        every { queue.all(any()) } returns flowOf(listOf(QueuedMediaItem(mediaItem = row(), position = 456L)))
        check(context.preferences.edit().putBoolean(persistentQueueKey, true).commit())
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
        mockkConstructor(AutoAccessPolicy::class)
    }

    @After
    fun tearDown() {
        releaseEligibility.complete(Unit)
        callback.release()
        switchProfile("A")
        val queueEditor = context.preferences.edit()
        originalPersistentQueue?.let { queueEditor.putBoolean(persistentQueueKey, it) }
            ?: queueEditor.remove(persistentQueueKey)
        check(queueEditor.commit())
        val profileEditor = context.profilePreferences.edit()
        originalProfile?.let { profileEditor.putString(activeProfileKey, it) }
            ?: profileEditor.remove(activeProfileKey)
        check(profileEditor.commit())
        unmockkAll()
    }

    @Test fun `append rejects profile change during eligibility`() = assertPublication(Operation.APPEND, Change.PROFILE)
    @Test fun `scoped item rejects profile change during eligibility`() = assertPublication(Operation.SCOPED_ITEM, Change.PROFILE)
    @Test fun `direct item rejects profile change during eligibility`() = assertPublication(Operation.DIRECT_ITEM, Change.PROFILE)
    private fun pumpUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        assertTrue("Application listener did not finish", condition())
    }

    @Test fun `resumption rejects profile change during eligibility`() = assertPublication(Operation.RESUME, Change.PROFILE)
    @Test fun `append rejects profile roundtrip during eligibility`() = assertPublication(Operation.APPEND, Change.PROFILE_ROUNDTRIP)
    @Test fun `scoped item rejects profile roundtrip during eligibility`() = assertPublication(Operation.SCOPED_ITEM, Change.PROFILE_ROUNDTRIP)
    @Test fun `direct item rejects profile roundtrip during eligibility`() = assertPublication(Operation.DIRECT_ITEM, Change.PROFILE_ROUNDTRIP)
    @Test fun `resumption rejects profile roundtrip during eligibility`() = assertPublication(Operation.RESUME, Change.PROFILE_ROUNDTRIP)
    @Test fun `append rejects offline roundtrip during eligibility`() = assertPublication(Operation.APPEND, Change.OFFLINE_ROUNDTRIP)
    @Test fun `scoped item rejects offline roundtrip during eligibility`() = assertPublication(Operation.SCOPED_ITEM, Change.OFFLINE_ROUNDTRIP)
    @Test fun `direct item rejects offline roundtrip during eligibility`() = assertPublication(Operation.DIRECT_ITEM, Change.OFFLINE_ROUNDTRIP)
    @Test fun `resumption rejects offline roundtrip during eligibility`() = assertPublication(Operation.RESUME, Change.OFFLINE_ROUNDTRIP)
    @Test fun `append rejects capture cleared during eligibility`() = assertPublication(Operation.APPEND, Change.CAPTURE)
    @Test fun `scoped item rejects capture cleared during eligibility`() = assertPublication(Operation.SCOPED_ITEM, Change.CAPTURE)
    @Test fun `set queue rejects capture cleared during final eligibility`() = assertPublication(Operation.SET, Change.CAPTURE)
    @Test fun `unchanged append remains publishable after delayed eligibility`() = assertPublication(Operation.APPEND, Change.NONE)
    @Test fun `unchanged scoped item remains publishable after delayed eligibility`() = assertPublication(Operation.SCOPED_ITEM, Change.NONE)
    @Test fun `unchanged direct item remains publishable after delayed eligibility`() = assertPublication(Operation.DIRECT_ITEM, Change.NONE)
    @Test fun `unchanged resumption preserves position after delayed eligibility`() = assertPublication(Operation.RESUME, Change.NONE)

    @Test fun `new selection cancels older future before reversed source completion can replace queue`() {
        val oldSourceEntered = AtomicBoolean()
        val oldSourceFinished = CountDownLatch(1)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == "old" && oldSourceEntered.compareAndSet(false, true)) {
                entered.countDown()
                try {
                    // Simulate a provider that finishes after cancellation; its Java future must
                    // still never succeed because Media3 applies every successful result.
                    withContext(NonCancellable) { releaseEligibility.await() }
                } finally { oldSourceFinished.countDown() }
            }
            items.withIndex().toList()
        }
        fun capture(id: String) = tree.selectionStore.capture("album/ordering", listOf(row().buildUpon()
            .setMediaId(id).setCustomCacheKey(id).build())).single()
        val oldRow = capture("old")
        val newRow = capture("new")
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val applied = CopyOnWriteArrayList<List<String>>()
        val appliedNew = CountDownLatch(1)
        fun observe(future: ListenableFuture<MediaSession.MediaItemsWithStartPosition>) {
            Futures.addCallback(future, object : FutureCallback<MediaSession.MediaItemsWithStartPosition> {
                override fun onSuccess(result: MediaSession.MediaItemsWithStartPosition) {
                    applied += result.mediaItems.map { it.mediaId }
                    appliedNew.countDown()
                }
                override fun onFailure(error: Throwable) = Unit
            }, Executor { it.run() })
        }
        val old = callback.onSetMediaItems(session, controller, mutableListOf(oldRow), 0, 11L)
        observe(old)
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        val newest = callback.onSetMediaItems(session, controller, mutableListOf(newRow), 0, 22L)
        observe(newest)
        assertEquals(listOf("new"), newest.get(15, TimeUnit.SECONDS).mediaItems.map { it.mediaId })
        pumpUntil { appliedNew.count == 0L }
        assertTrue("Superseded selection must be cancellation, never empty success", old.isCancelled)
        releaseEligibility.complete(Unit)
        assertTrue(oldSourceFinished.await(15, TimeUnit.SECONDS))
        assertEquals(listOf(listOf("new")), applied.toList())
    }

    @Test fun `policy invalidation cancels pending selection without applying an empty queue`() =
        assertInvalidationCancelsSelection(false)

    @Test fun `profile invalidation cancels pending selection without applying an empty queue`() =
        assertInvalidationCancelsSelection(true)

    private fun assertInvalidationCancelsSelection(profileChange: Boolean) {
        val finished = CountDownLatch(1)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            entered.countDown()
            try { withContext(NonCancellable) { releaseEligibility.await() } }
            finally { finished.countDown() }
            firstArg<List<MediaItem>>().withIndex().toList()
        }
        val shown = tree.selectionStore.capture(parent, listOf(row())).single()
        val applied = CopyOnWriteArrayList<List<String>>()
        val future = callback.onSetMediaItems(mockk(), mockk(), mutableListOf(shown), 0, 123L)
        Futures.addCallback(future, object : FutureCallback<MediaSession.MediaItemsWithStartPosition> {
            override fun onSuccess(result: MediaSession.MediaItemsWithStartPosition) { applied += result.mediaItems.map { it.mediaId } }
            override fun onFailure(error: Throwable) = Unit
        }, Executor { it.run() })
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        if (profileChange) switchProfile("B") else online = false
        callback.invalidatePublication()
        assertTrue("Invalidated future must be cancelled immediately", future.isCancelled)
        releaseEligibility.complete(Unit)
        assertTrue(finished.await(15, TimeUnit.SECONDS))
        assertTrue("No empty success may be applied to existing playback", applied.isEmpty())
    }

    @Test fun `explicit selection cancels modern playback resumption before reversed completion`() =
        assertResumptionCannotReplaceSelection(legacy = false)

    @Test fun `explicit selection cancels deprecated playback resumption before reversed completion`() =
        assertResumptionCannotReplaceSelection(legacy = true)

    private fun assertResumptionCannotReplaceSelection(legacy: Boolean) {
        val finished = CountDownLatch(1)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == "selected") {
                entered.countDown()
                try { withContext(NonCancellable) { releaseEligibility.await() } }
                finally { finished.countDown() }
            }
            items.withIndex().toList()
        }
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val events = CopyOnWriteArrayList<String>()
        val applied = CopyOnWriteArrayList<List<String>>()
        // MediaSessionImpl 1.10.1 applies every successful resumed queue, and calls play
        // on failure as well. Application-thread cancellation runs this fallback at entry.
        val old = if (legacy) callback.onPlaybackResumption(session, controller)
            else callback.onPlaybackResumption(session, controller, true)
        Futures.addCallback(old, object : FutureCallback<MediaSession.MediaItemsWithStartPosition> {
            override fun onSuccess(result: MediaSession.MediaItemsWithStartPosition) {
                applied += result.mediaItems.map { it.mediaId }
                events += "resumed-play"
            }
            override fun onFailure(error: Throwable) { events += "fallback-play" }
        }, Executor { it.run() })
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        val shown = tree.selectionStore.capture("album/explicit", listOf(row().buildUpon()
            .setMediaId("new").setCustomCacheKey("new").build())).single()
        val newer = callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 999L)
        assertTrue(old.isCancelled)
        assertEquals("Old play fallback must precede new queue publication", listOf("fallback-play"), events.toList())
        val result = newer.get(15, TimeUnit.SECONDS)
        applied += result.mediaItems.map { it.mediaId }
        events += "explicit-queue"
        assertEquals(999L, result.startPositionMs)
        releaseEligibility.complete(Unit)
        assertTrue(finished.await(15, TimeUnit.SECONDS))
        assertEquals(listOf(listOf("new")), applied.toList())
        assertEquals(listOf("fallback-play", "explicit-queue"), events.toList())
    }

    @Test fun `play joins pending explicit selection when saved queue is disabled without applying an empty queue`() {
        check(context.preferences.edit().putBoolean(persistentQueueKey, false).commit())
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            entered.countDown()
            releaseEligibility.await()
            firstArg<List<MediaItem>>().withIndex().toList()
        }
        val shown = tree.selectionStore.capture("album/explicit", listOf(row().buildUpon()
            .setMediaId("new").setCustomCacheKey("new").build())).single()
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val selection = callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 999L)
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        val play = callback.onPlaybackResumption(session, controller, true)
        val legacyPlay = callback.onPlaybackResumption(session, controller)
        assertSame("Play must reuse the pending replacement", selection, play)
        assertSame("Deprecated Play must preserve the same pending replacement", selection, legacyPlay)
        assertFalse(selection.isDone)
        val applied = CopyOnWriteArrayList<List<String>>()
        val events = CopyOnWriteArrayList<String>()
        val completed = CountDownLatch(2)
        fun observe(future: ListenableFuture<MediaSession.MediaItemsWithStartPosition>, resuming: Boolean) {
            Futures.addCallback(future, object : FutureCallback<MediaSession.MediaItemsWithStartPosition> {
                override fun onSuccess(result: MediaSession.MediaItemsWithStartPosition) {
                    applied += result.mediaItems.map { it.mediaId }
                    if (resuming) events += "play"
                    completed.countDown()
                }
                override fun onFailure(error: Throwable) {
                    if (resuming) events += "fallback-play"
                    completed.countDown()
                }
            }, Executor { it.run() })
        }
        observe(selection, false)
        observe(play, true)
        releaseEligibility.complete(Unit)
        assertEquals(999L, play.get(15, TimeUnit.SECONDS).startPositionMs)
        pumpUntil { completed.count == 0L }
        assertFalse(selection.isCancelled)
        assertEquals(listOf(listOf("new"), listOf("new")), applied.toList())
        assertEquals(listOf("play"), events.toList())
    }

    @Test fun `exceptionally failed explicit selection does not poison next Play saved recovery`() {
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == "broken") throw IllegalStateException("selection source failed")
            items.withIndex().toList()
        }
        val shown = tree.selectionStore.capture("album/failure", listOf(row().buildUpon()
            .setMediaId("broken").setCustomCacheKey("broken").build())).single()
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val failed = callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 999L)
        assertThrows(ExecutionException::class.java) { failed.get(15, TimeUnit.SECONDS) }
        assertFalse(failed.isCancelled)
        val recovered = callback.onPlaybackResumption(session, controller, true).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("selected"), recovered.mediaItems.map { it.mediaId })
        assertEquals(456L, recovered.startPositionMs)
    }

    @Test fun `metadata preview does not cancel pending explicit selection`() {
        val oldSourceEntered = AtomicBoolean()
        val oldSourceFinished = CountDownLatch(1)
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            val items = firstArg<List<MediaItem>>()
            if (items.firstOrNull()?.mediaId == "new" && oldSourceEntered.compareAndSet(false, true)) {
                entered.countDown()
                try { withContext(NonCancellable) { releaseEligibility.await() } }
                finally { oldSourceFinished.countDown() }
            }
            items.withIndex().toList()
        }
        fun capture(id: String) = tree.selectionStore.capture("album/explicit", listOf(row().buildUpon()
            .setMediaId(id).setCustomCacheKey(id).build())).single()
        val shown = capture("new")
        val session = mockk<MediaSession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val selection = callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 999L)
        assertTrue(entered.await(15, TimeUnit.SECONDS))
        val preview = callback.onPlaybackResumption(session, controller, false)
        assertEquals(listOf("selected"), preview.get(15, TimeUnit.SECONDS).mediaItems.map { it.mediaId })
        assertFalse(selection.isDone)
        val applied = CopyOnWriteArrayList<List<String>>()
        val appliedNewest = CountDownLatch(1)
        fun observe(future: ListenableFuture<MediaSession.MediaItemsWithStartPosition>) {
            Futures.addCallback(future, object : FutureCallback<MediaSession.MediaItemsWithStartPosition> {
                override fun onSuccess(result: MediaSession.MediaItemsWithStartPosition) {
                    applied += result.mediaItems.map { it.mediaId }
                    appliedNewest.countDown()
                }
                override fun onFailure(error: Throwable) = Unit
            }, Executor { it.run() })
        }
        observe(selection)
        val newest = callback.onSetMediaItems(session, controller, mutableListOf(capture("newest")), 0, 1234L)
        observe(newest)
        // A completed preview must never replace the pending-selection ownership slot.
        assertTrue("Second explicit choice must immediately cancel the first even after preview", selection.isCancelled)
        val result = newest.get(15, TimeUnit.SECONDS)
        assertEquals(listOf("newest"), result.mediaItems.map { it.mediaId })
        assertEquals(1234L, result.startPositionMs)
        pumpUntil { appliedNewest.count == 0L }
        releaseEligibility.complete(Unit)
        assertTrue(oldSourceFinished.await(15, TimeUnit.SECONDS))
        assertThrows(CancellationException::class.java) { selection.get(15, TimeUnit.SECONDS) }
        assertEquals(listOf(listOf("newest")), applied.toList())
    }

    private fun assertPublication(operation: Operation, change: Change) {
        var calls = 0
        coEvery { anyConstructed<AutoAccessPolicy>().eligible(any(), any()) } coAnswers {
            // Set resolves the captured category first, then checks the final queue separately.
            if (operation != Operation.SET || ++calls == 2) {
                entered.countDown()
                releaseEligibility.await()
            }
            firstArg<List<MediaItem>>().withIndex().toList()
        }
        val shown = tree.selectionStore.capture(parent, listOf(row())).single()
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        val pending = when (operation) {
            Operation.APPEND -> callback.onAddMediaItems(session, controller, mutableListOf(shown))
            Operation.SCOPED_ITEM -> callback.onGetItem(session, controller, shown.mediaId)
            Operation.DIRECT_ITEM -> callback.onGetItem(session, controller, "selected")
            Operation.RESUME -> callback.onPlaybackResumption(session, controller)
            Operation.SET -> callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 123L)
        }
        assertTrue("Callback did not reach final eligibility", entered.await(15, TimeUnit.SECONDS))
        assertFalse("Eligibility must still be suspended", pending.isDone)
        when (change) {
            Change.PROFILE -> switchProfile("B")
            Change.PROFILE_ROUNDTRIP -> {
                switchProfile("B")
                callback.invalidatePublication()
                switchProfile("A")
                callback.invalidatePublication()
            }
            Change.OFFLINE_ROUNDTRIP -> {
                online = false
                callback.invalidatePublication()
                online = true
                callback.invalidatePublication()
            }
            Change.CAPTURE -> tree.selectionStore.clear()
            Change.NONE -> Unit
        }
        releaseEligibility.complete(Unit)
        val accepted = change == Change.NONE
        if (!accepted && operation in setOf(Operation.RESUME, Operation.SET)) {
            assertThrows(CancellationException::class.java) { pending.get(15, TimeUnit.SECONDS) }
            assertTrue(pending.isCancelled)
            return
        }
        val result = pending.get(15, TimeUnit.SECONDS)
        when (operation) {
            Operation.APPEND -> {
                @Suppress("UNCHECKED_CAST")
                val items = result as List<MediaItem>
                assertEquals(if (accepted) listOf("selected") else emptyList<String>(), items.map { it.mediaId })
            }
            Operation.SCOPED_ITEM, Operation.DIRECT_ITEM -> {
                @Suppress("UNCHECKED_CAST")
                val item = result as LibraryResult<MediaItem>
                assertEquals(accepted, item.resultCode == SessionResult.RESULT_SUCCESS)
                assertEquals(accepted, item.value != null)
            }
            Operation.RESUME, Operation.SET -> {
                val queue = result as MediaSession.MediaItemsWithStartPosition
                assertEquals(if (accepted) listOf("selected") else emptyList<String>(), queue.mediaItems.map { it.mediaId })
                if (accepted) {
                    assertEquals(0, queue.startIndex)
                    assertEquals(456L, queue.startPositionMs)
                }
            }
        }
    }

    private fun switchProfile(profile: String) {
        check(context.profilePreferences.edit().putString(activeProfileKey, profile).commit())
    }

    private fun row(): MediaItem = MediaItem.Builder()
        .setMediaId("selected").setUri("https://example.test/selected").setCustomCacheKey("selected")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Selected").setArtist("Artist")
            .setIsPlayable(true).setIsBrowsable(false).build())
        .build()

    private enum class Operation { APPEND, SCOPED_ITEM, DIRECT_ITEM, RESUME, SET }
    private enum class Change { PROFILE, PROFILE_ROUNDTRIP, OFFLINE_ROUNDTRIP, CAPTURE, NONE }
}
