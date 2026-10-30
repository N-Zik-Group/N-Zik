package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.QueuedMediaItem
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.persistentQueueKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Converters
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.QueuedMediaItemTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Persistent queue artwork must survive loss of the process-local source registry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoArtworkResumptionSourceTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var provider: ArtworkContentProvider
    private lateinit var queue: QueuedMediaItemTable
    private lateinit var songs: SongTable
    private val files = mutableListOf<File>()
    private var previousPersistentQueue: Boolean? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        previousPersistentQueue = if (context.preferences.contains(persistentQueueKey)) {
            context.preferences.getBoolean(persistentQueueKey, false)
        } else null
        assertTrue(context.preferences.edit().putBoolean(persistentQueueKey, true).commit())
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(Database)
        queue = mockk()
        songs = mockk()
        every { Database.queueTable } returns queue
        every { Database.songTable } returns songs
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        provider = spyk(ArtworkContentProvider().also { it.attachInfo(context, null) })
        every { provider.callingPackage } returns context.packageName
        clearSourceRegistry()
    }

    @After
    fun tearDown() {
        callback.release()
        clearSourceRegistry()
        files.forEach { it.delete() }
        val editor = context.preferences.edit()
        previousPersistentQueue?.let { editor.putBoolean(persistentQueueKey, it) }
            ?: editor.remove(persistentQueueKey)
        editor.commit()
        unmockkAll()
    }

    @Test
    fun `serialized source extra rehydrates original pixels and retains queue metadata`() {
        val red = song("local:93871", "First saved song", Color.RED)
        val blue = song("local:93872", "Second saved song", Color.BLUE)
        val saved = listOf(red, blue).map { source ->
            roundTrip(SessionMediaItemMapper.mapSongToMediaItem(source, loadArtwork = true)).also {
                assertNull("Queue converter strips artwork bytes", it.mediaMetadata.artworkData)
                assertEquals(source.thumbnailUrl, it.mediaMetadata.extras
                    ?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA))
            }
        }
        val expired = saved.map { requireNotNull(it.mediaMetadata.artworkUri) }
        every { queue.all(any()) } returns flowOf(listOf(
            QueuedMediaItem(mediaItem = saved[0], position = null),
            QueuedMediaItem(mediaItem = saved[1], position = 12_345L),
        ))
        clearSourceRegistry()
        expired.forEach(::assertExpired)

        val resumed = resume()

        assertEquals(1, resumed.startIndex)
        assertEquals(12_345L, resumed.startPositionMs)
        assertEquals(listOf(red.id, blue.id), resumed.mediaItems.map { it.mediaId })
        assertEquals(listOf(red.title, blue.title), resumed.mediaItems.map { it.mediaMetadata.title.toString() })
        resumed.mediaItems.forEachIndexed { index, item ->
            assertTrue(requireNotNull(item.mediaMetadata.extras).getBoolean(persistentQueueKey))
            assertFreshPixels(item, expired[index], if (index == 0) Color.RED else Color.BLUE)
        }
        verify(exactly = 0) { songs.findById(any()) }
        assertTrue(context.preferences.getBoolean(persistentQueueKey, false))
    }

    @Test
    fun `legacy serialized opaque uri rehydrates pixels from song table`() {
        val source = song("local:93873", "Legacy saved title", Color.RED)
        val mapped = SessionMediaItemMapper.mapSongToMediaItem(source)
        val extras = Bundle(mapped.mediaMetadata.extras).apply {
            remove(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA)
        }
        val saved = roundTrip(mapped.buildUpon().setMediaMetadata(
            mapped.mediaMetadata.buildUpon().setExtras(extras).build()).build())
        assertNull(saved.mediaMetadata.extras?.getString(SessionMediaItemMapper.ORIGINAL_ARTWORK_SOURCE_EXTRA))
        val expired = requireNotNull(saved.mediaMetadata.artworkUri)
        every { queue.all(any()) } returns flowOf(listOf(QueuedMediaItem(mediaItem = saved, position = 7_654L)))
        every { songs.findById(source.id) } returns flowOf(source.copy(title = "Different database title"))
        clearSourceRegistry()
        assertExpired(expired)

        val resumed = resume()

        assertEquals(0, resumed.startIndex)
        assertEquals(7_654L, resumed.startPositionMs)
        val item = resumed.mediaItems.single()
        assertEquals(source.id, item.mediaId)
        assertEquals(source.title, item.mediaMetadata.title.toString())
        assertTrue(requireNotNull(item.mediaMetadata.extras).getBoolean(persistentQueueKey))
        assertFreshPixels(item, expired, Color.RED)
        verify(exactly = 1) { songs.findById(source.id) }
    }

    private fun resume(): MediaSession.MediaItemsWithStartPosition = callback.onPlaybackResumption(
        mockk<MediaSession>(), mockk<MediaSession.ControllerInfo>(),
    ).get(15, TimeUnit.SECONDS)

    private fun roundTrip(item: MediaItem): MediaItem = requireNotNull(
        Converters.mediaItemFromByteArray(requireNotNull(Converters.mediaItemToByteArray(item))),
    )

    private fun song(id: String, title: String, color: Int): Song {
        val file = File.createTempFile("durable-original-art", ".png", context.cacheDir).also { files += it }
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        return Song(id, title, "Saved artist", "1:00", file.toURI().toASCIIString())
    }

    private fun clearSourceRegistry() {
        // Reproduce process death without changing the serialized queue or durable files.
        val field = ArtworkContentProvider::class.java.getDeclaredField("sources").apply { isAccessible = true }
        val sources = field.get(null) as MutableMap<*, *>
        synchronized(sources) { sources.clear() }
    }

    private fun read(uri: Uri): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(
        requireNotNull(provider.openFile(uri, "r")),
    ).use { it.readBytes() }

    private fun assertExpired(uri: Uri) {
        assertEquals("source", uri.pathSegments.first())
        val unknown = uri.buildUpon().path("source/unknown-after-process-death").build()
        assertArrayEquals("Cleared tokens must serve only the placeholder", read(unknown), read(uri))
    }

    private fun assertFreshPixels(item: MediaItem, expired: Uri, color: Int) {
        val fresh = requireNotNull(item.mediaMetadata.artworkUri)
        assertEquals("content", fresh.scheme)
        assertEquals(ArtworkContentProvider.AUTHORITY, fresh.authority)
        assertEquals("source", fresh.pathSegments.first())
        assertNotEquals("Resumption must register a new token", expired, fresh)
        val bytes = read(fresh)
        assertEquals(0xff, bytes[0].toInt() and 0xff)
        assertEquals(0xd8, bytes[1].toInt() and 0xff)
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue("Provider must serve original pixels, with JPEG tolerance",
                kotlin.math.abs(Color.red(pixel) - Color.red(color)) < 25 &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(color)) < 25 &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(color)) < 25)
        } finally { bitmap.recycle() }
    }
}
