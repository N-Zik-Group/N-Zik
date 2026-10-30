package app.n_zik.android.playback.services.automotive.session

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.appContext
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Size
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercises legacy contextual IDs through the real callback and shared remote image policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoSessionRemoteArtworkPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val callbacks = mutableListOf<AutoSessionCallback>()
    private val bitmaps = mutableListOf<Bitmap>()

    @After fun tearDown() {
        callbacks.forEach { it.release() }
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        context.getSharedPreferences("playlist_thumbnail_store", 0).edit().clear().commit()
        unmockkAll()
    }

    @Test fun `contextual remote song retains quality cache keys and bounded decode`() {
        val url = "https://img.example.test/policy-cache.jpg"
        val requests = load(url)
        assertEquals(listOf(url), requests.map { it.data })
        val key = MessageDigest.getInstance("MD5").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(key, requests.single().diskCacheKey)
        assertEquals(key, requests.single().memoryCacheKey)
        verify { ImageCacheFactory.getDownloadDecision(url) }
    }

    @Test fun `contextual remote song retries missing YouTube high resolution cover`() {
        val original = "https://i.ytimg.com/vi/policy404xx/hqdefault.jpg"
        val high = original.replace("hqdefault", "maxresdefault")
        val fallback = original.replace("hqdefault", "sddefault")
        val requests = load(original, failUrl = high)
        assertEquals(listOf(high, fallback), requests.map { it.data })
        requests.forEach { request ->
            assertEquals(
                ImageCacheFactory.generateCacheKeySync(request.data as String, ImageCacheFactory.NetworkQuality.HIGH),
                request.diskCacheKey,
            )
            assertEquals(request.diskCacheKey, request.memoryCacheKey)
        }
    }

    @Test fun `expired learned playlist URL is cleared and original cover is retried`() {
        val original = "https://i.ytimg.com/pl_c/policy-playlist/mwEIC.jpg?rs=valid"
        val learned = "https://i.ytimg.com/pl_c/policy-playlist/mwEKC.jpg?rs=expired"
        context.getSharedPreferences("playlist_thumbnail_store", 0).edit()
            .putString("policy-playlist", "|$learned").commit()
        val requests = load(original, failUrl = learned)
        assertEquals(listOf(learned, original), requests.map { it.data })
        assertEquals("playlist_policy-playlist_HIGH", requests.first().diskCacheKey)
        verify { ImageCacheFactory.clearCacheForKey(learned, ImageCacheFactory.NetworkQuality.HIGH) }
        assertFalse(context.getSharedPreferences("playlist_thumbnail_store", 0).contains("policy-playlist"))
    }

    @Test fun `large warm cache result is bounded before owned encoding without changing borrowed pixels`() {
        val requests = load("https://img.example.test/policy-large-warm.jpg", width = 2048, height = 1536)
        assertEquals(1, requests.size)
    }

    @Test fun `already bounded warm cache result is copied without recycling borrowed bitmap`() {
        load("https://img.example.test/policy-small-warm.jpg", width = 256, height = 192)
    }

    @Test fun `default bitmap callers retain phone low quality without override`() {
        // JUnit is synchronous; exercise the suspend factory API directly.
        runBlocking {
            mockkStatic("app.n_zik.android.GlobalVarsKt")
            every { appContext() } returns context
            val url = "https://lh3.googleusercontent.com/phone-only=w60-h60"
            mockkObject(ImageCacheFactory)
            every { ImageCacheFactory.getDownloadDecision(url) } returns ImageCacheFactory.DownloadDecision(
                useNetwork = false, quality = ImageCacheFactory.NetworkQuality.LOW,
            )
            val loader = mockk<ImageLoader>()
            every { ImageCacheFactory.LOADER } returns loader
            val source = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmaps += it }
            val success = mockk<SuccessResult>()
            every { success.image } returns source.asImage()
            every { success.dataSource } returns DataSource.MEMORY_CACHE
            val requests = mutableListOf<ImageRequest>()
            coEvery { loader.execute(any()) } answers {
                requests += firstArg<ImageRequest>()
                success
            }
            ImageCacheFactory.loadBitmap(url, requestSize = 512)
            assertEquals(listOf(url.replace("w60-h60", "w300-h300")), requests.map { it.data })
        }
    }

    // JUnit is synchronous; bridge only to inspect Coil's suspend size resolver.
    private fun load(
        url: String, failUrl: String? = null, width: Int = 700, height: Int = 600,
    ): List<ImageRequest> = runBlocking {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(Database)
        val songs = mockk<SongTable>()
        every { Database.songTable } returns songs
        val song = Song("policy-${url.hashCode()}", "Remote", "Artist", "3:00", url)
        every { songs.findByIdDirect(song.id) } returns song
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.getDownloadDecision(url) } returns ImageCacheFactory.DownloadDecision(
            useNetwork = false, quality = ImageCacheFactory.NetworkQuality.LOW,
        )
        every { ImageCacheFactory.clearCacheForKey(any(), any()) } returns Unit
        val loader = mockk<ImageLoader>()
        every { ImageCacheFactory.LOADER } returns loader
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.RED)
            bitmaps += it
        }
        val originalPixels = IntArray(width * height).also {
            source.getPixels(it, 0, width, 0, 0, width, height)
        }
        val encodedDimensions = CopyOnWriteArrayList<Pair<Int, Int>>()
        mockkObject(ArtworkBitmapDecoder)
        every { ArtworkBitmapDecoder.jpeg(any(), any()) } answers {
            val owned = firstArg<Bitmap>()
            assertTrue("Encoding must own its input", owned !== source)
            assertTrue("Ownership must be bounded before encoding", owned.width <= 512 && owned.height <= 512)
            assertEquals(Bitmap.Config.ARGB_8888, owned.config)
            assertEquals(source.hasAlpha(), owned.hasAlpha())
            encodedDimensions += owned.width to owned.height
            callOriginal()
        }
        val success = mockk<SuccessResult>()
        every { success.image } returns source.asImage()
        every { success.dataSource } returns DataSource.MEMORY_CACHE
        val failure = mockk<ErrorResult>()
        every { failure.image } returns null
        every { failure.throwable } returns IllegalStateException("HTTP 404")
        val requests = CopyOnWriteArrayList<ImageRequest>()
        coEvery { loader.execute(any()) } answers {
            val request = firstArg<ImageRequest>()
            requests += request
            if (request.data == failUrl) failure else success
        }
        val callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
            .also { callbacks += it }
        val id = "${AutoSessionConstants.ID_SONGS_ALL}/${song.id}"
        val loaded = callback.onGetItem(mockk<MediaLibrarySession>(), mockk<MediaSession.ControllerInfo>(), id)
            .get(15, TimeUnit.SECONDS)
        assertEquals(SessionResult.RESULT_SUCCESS, loaded.resultCode)
        val item = requireNotNull(loaded.value)
        assertEquals(id, item.mediaId)
        val bytes = requireNotNull(item.mediaMetadata.artworkData)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        assertTrue("The real artwork must reach the encoder", encodedDimensions.isNotEmpty())
        val embedded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            assertTrue(embedded.width <= 512 && embedded.height <= 512)
            assertTrue(Color.red(embedded.getPixel(0, 0)) > 240)
            assertTrue(Color.green(embedded.getPixel(0, 0)) < 15)
        } finally { embedded.recycle() }
        assertFalse("Coil retains ownership of its bitmap", source.isRecycled)
        assertEquals(width, source.width)
        assertEquals(height, source.height)
        val retainedPixels = IntArray(width * height).also {
            source.getPixels(it, 0, width, 0, 0, width, height)
        }
        assertArrayEquals(originalPixels, retainedPixels)
        requests.forEach { request ->
            assertEquals(Size(Dimension.Pixels(512), Dimension.Pixels(512)), request.sizeResolver.size())
        }
        requests
    }
}
