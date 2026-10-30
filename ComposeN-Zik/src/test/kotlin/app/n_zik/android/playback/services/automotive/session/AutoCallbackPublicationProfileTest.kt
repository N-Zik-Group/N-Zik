package app.n_zik.android.playback.services.automotive.session

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.app.Application
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.every
import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.DislikedExclusion
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.SuccessResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import java.util.concurrent.CancellationException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Public callback results must still belong to the active profile when published. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoCallbackPublicationProfileTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree
    private val songs = mockk<SongTable>()
    private val queue = mockk<QueuedMediaItemTable>()
    private val releaseRead = CompletableDeferred<Unit>()
    private var bitmap: Bitmap? = null
    private val parent = "${AutoSessionConstants.ID_SEARCH_SONGS}/publication"

    @Before
    fun setUp() {
        mockkObject(NetworkQualityHelper)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        switchProfile("A")
        mockkObject(Database, DislikedExclusion)
        coEvery { DislikedExclusion.excludedSongIds(any(), any()) } returns emptySet()
        every { Database.songTable } returns songs
        every { Database.queueTable } returns queue
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After
    fun tearDown() {
        releaseRead.complete(Unit)
        callback.release()
        listOf("A", "B").forEach { profile ->
            switchProfile(profile)
            context.preferences.edit().remove(persistentQueueKey).commit()
        }
        context.profilePreferences.edit().remove(activeProfileKey).commit()
        unmockkAll()
        bitmap?.recycle()
    }

    @Test
    fun `mixed append rejects all rows after profile changes during later database read`() {
        assertMixedAppendRejected(returnToA = false)
    }

    @Test
    fun `mixed append rejects invalidated capture even when original profile is active again`() {
        assertMixedAppendRejected(returnToA = true)
    }

    @Test
    fun `get item rejects profile change during artwork mapping and permits fresh item`() {
        val song = Song(
            id = "publication-item", title = "Item", artistsText = "Artist", durationText = "3:00",
            thumbnailUrl = "https://example.test/publication-profile-item.jpg",
        )
        every { songs.findByIdDirect(song.id) } returns song
        val loader = mockk<ImageLoader>()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        val artwork = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap = it }
        val loaded = mockk<SuccessResult>()
        every { loaded.image } returns artwork.asImage()
        every { loaded.dataSource } returns DataSource.MEMORY_CACHE
        val entered = CountDownLatch(1)
        coEvery { loader.execute(any()) } coAnswers {
            entered.countDown()
            releaseRead.await()
            loaded
        }
        val session = mockk<MediaLibrarySession>()
        val controller = mockk<MediaSession.ControllerInfo>()
        // The contextual overload loads remote artwork through the real mapper.
        val mediaId = "${AutoSessionConstants.ID_SONGS_ALL}/${song.id}"
        val pending = callback.onGetItem(session, controller, mediaId)
        assertTrue("Item mapper did not reach Coil", entered.await(15, TimeUnit.SECONDS))
        assertFalse(pending.isDone)
        switchProfile("B")
        releaseRead.complete(Unit)

        val rejected = pending.get(15, TimeUnit.SECONDS)
        assertTrue("Old-profile item must return an error", rejected.resultCode != SessionResult.RESULT_SUCCESS)
        assertEquals(null, rejected.value)

        val fresh = callback.onGetItem(session, controller, mediaId).get(15, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, fresh.resultCode)
        assertEquals(mediaId, fresh.value?.mediaId)
    }

    @Test
    fun `resumption rejects queue read across profile change and permits fresh queue`() {
        check(context.preferences.edit().putBoolean(persistentQueueKey, true).commit())
        val entered = CountDownLatch(1)
        every { queue.all(any()) } returns flow {
            entered.countDown()
            releaseRead.await()
            emit(listOf(QueuedMediaItem(mediaItem = row("private-A"), position = 456L)))
        }
        val pending = callback.onPlaybackResumption(mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>())
        assertTrue("Resumption did not reach queue flow", entered.await(15, TimeUnit.SECONDS))
        assertFalse(pending.isDone)
        switchProfile("B")
        releaseRead.complete(Unit)

        assertThrows(CancellationException::class.java) { pending.get(15, TimeUnit.SECONDS) }
        assertTrue(pending.isCancelled)

        check(context.preferences.edit().putBoolean(persistentQueueKey, true).commit())
        every { queue.all(any()) } returns flow {
            emit(listOf(QueuedMediaItem(mediaItem = row("public-B"), position = 789L)))
        }
        val fresh = callback.onPlaybackResumption(
            mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
        ).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("public-B"), fresh.mediaItems.map { it.mediaId })
        assertEquals(0, fresh.startIndex)
        assertEquals(789L, fresh.startPositionMs)
    }

    private fun assertMixedAppendRejected(returnToA: Boolean) {
        val shown = tree.selectionStore.capture(parent, listOf(row("private-A"))).single()
        val entered = CountDownLatch(1)
        val databaseSong = Song("db-row", "DB row", "Artist", "3:00", null)
        every { songs.findById(databaseSong.id) } returns flow {
            entered.countDown()
            releaseRead.await()
            emit(databaseSong)
        }
        val pending = callback.onAddMediaItems(
            mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
            mutableListOf(shown, MediaItem.Builder().setMediaId(databaseSong.id).build()),
        )
        assertTrue("Append did not reach its second row", entered.await(15, TimeUnit.SECONDS))
        assertFalse(pending.isDone)
        switchProfile("B")
        if (returnToA) {
            // Observing B invalidates A's token before the real preference returns to A.
            tree.selectionStore.capture(parent, listOf(row("public-B")))
            switchProfile("A")
        }
        releaseRead.complete(Unit)
        assertTrue("Append must reject the complete mixed result", pending.get(15, TimeUnit.SECONDS).isEmpty())

        switchProfile("B")
        val fresh = tree.selectionStore.capture(parent, listOf(row("fresh-B"))).single()
        val accepted = callback.onAddMediaItems(
            mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(), mutableListOf(fresh),
        ).get(15, TimeUnit.SECONDS)
        assertEquals(listOf("fresh-B"), accepted.map { it.mediaId })
    }

    private fun switchProfile(profile: String) {
        check(context.profilePreferences.edit().putString(activeProfileKey, profile).commit())
    }

    private fun row(id: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri("https://example.test/$id")
        .setCustomCacheKey(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(id).setArtist("Artist")
            .setIsPlayable(true).setIsBrowsable(false).build())
        .build()
}
