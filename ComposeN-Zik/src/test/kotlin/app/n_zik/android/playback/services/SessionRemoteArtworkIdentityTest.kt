package app.n_zik.android.playback.services

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.mockk.spyk
import java.io.File
import java.io.IOException
import app.n_zik.android.core.profiles.coversDir
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.network.utils.NetworkQualityHelper
import io.mockk.mockkObject
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.robolectric.RuntimeEnvironment
import app.n_zik.android.playback.services.automotive.models.SessionArtworkLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionRemoteArtworkIdentityTest {
    @Test fun `video browse URI serves seekable cropped or original art after missing maxres fallback`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.preferences
        val prior = if (prefs.contains(cropVideoThumbnailsKey)) prefs.getBoolean(cropVideoThumbnailsKey, true) else null
        val source = Bitmap.createBitmap(2400, 1350, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(NetworkQualityHelper, ImageCacheFactory)
        every { NetworkQualityHelper.isNetworkConnected(any()) } returns true
        every { ImageCacheFactory.getDownloadDecision(any()) } returns
            ImageCacheFactory.DownloadDecision(false, ImageCacheFactory.NetworkQuality.HIGH)
        val loader = mockk<ImageLoader>()
        every { ImageCacheFactory.LOADER } returns loader
        val missing = mockk<ErrorResult>()
        every { missing.image } returns null
        every { missing.throwable } returns IOException("HTTP 404")
        val success = mockk<SuccessResult>()
        every { success.image } returns source.asImage()
        every { success.dataSource } returns DataSource.MEMORY_CACHE
        val requests = mutableListOf<String>()
        coEvery { loader.execute(any()) } answers {
            val url = firstArg<ImageRequest>().data.toString()
            requests += url
            if (url.endsWith("maxresdefault.jpg")) missing else success
        }
        try {
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(context, null) })
            every { provider.callingPackage } returns LocalArtworkRules.ANDROID_AUTO_PACKAGE
            val videoId = UUID.randomUUID().toString()
            val song = Song(videoId, "Video", "Uploader", "1:00", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
            val uris = listOf(true, false).map { crop ->
                prefs.edit().putBoolean(cropVideoThumbnailsKey, crop).commit()
                val item = SessionMediaItemMapper.mapSongToMediaItem(song, AutoSessionConstants.ID_SEARCH_VIDEOS)
                assertEquals(MediaMetadata.MEDIA_TYPE_VIDEO, item.mediaMetadata.mediaType)
                assertNull(item.mediaMetadata.artworkData)
                val uri = requireNotNull(item.mediaMetadata.artworkUri)
                assertEquals("remote", uri.pathSegments.single())
                assertEquals(crop.toString(), uri.getQueryParameter("c"))
                val descriptor = requireNotNull(provider.openFile(uri, "r"))
                assertTrue(descriptor.statSize > 0)
                val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                    it.channel.position(1)
                    it.channel.position(0)
                    it.readBytes()
                }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                try {
                    assertEquals(512, bitmap.width)
                    assertEquals(if (crop) 512 else 288, bitmap.height)
                    assertTrue(Color.red(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) > 230)
                } finally { bitmap.recycle() }
                assertFalse(source.isRecycled)
                uri
            }
            assertNotEquals(uris[0], uris[1])
            assertEquals(listOf("maxresdefault.jpg", "sddefault.jpg", "maxresdefault.jpg", "sddefault.jpg"),
                requests.map { it.substringAfterLast('/') })
            every { provider.callingPackage } returns "untrusted.app"
            assertNull(provider.openFile(uris[0], "r"))
        } finally {
            source.recycle()
            prefs.edit().apply { if (prior == null) remove(cropVideoThumbnailsKey) else putBoolean(cropVideoThumbnailsKey, prior) }.commit()
            unmockkAll()
        }
    }

    @Test fun `session facade preserves registered private file artwork identity`() {
        val context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        val file = File.createTempFile("session-private-cover", ".png", context.cacheDir)
        try {
            val item = SessionMediaItemMapper.mapSongToMediaItem(
                Song("private-cover", "Title", "Artist", "1:00", file.toURI().toASCIIString()))
            val uri = requireNotNull(item.mediaMetadata.artworkUri)
            assertEquals("source", uri.pathSegments.first())
            val upstream = mockk<Player>(relaxed = true)
            every { upstream.currentMediaItem } returns item
            every { upstream.mediaMetadata } returns item.mediaMetadata
            every { upstream.playbackState } returns Player.STATE_READY
            assertEquals(uri, SessionArtworkPlayer(upstream).mediaMetadata.artworkUri)
        } finally { file.delete(); unmockkAll() }
    }

    @Test fun `custom song pixels survive browse queue and session metadata with duplicate identity intact`() {
        val context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        val songId = "custom-song-771"
        val file = File(coversDir(context, getActiveProfile(context)), "cover_$songId.jpg")
        val previous = file.takeIf { it.exists() }?.readBytes()
        file.parentFile?.mkdirs()
        val custom = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        try {
            file.outputStream().use { custom.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val song = Song(songId, "Custom song", "Artist", "1:00", file.toURI().toASCIIString())
            val browse = SessionMediaItemMapper.mapSongToMediaItem(song, AutoSessionConstants.ID_SONGS_ALL)
            val canonical = SessionMediaItemMapper.mapSongToMediaItem(song)
            val adjacent = MediaItem.Builder().setMediaId("adjacent").build()
            val queue = listOf(canonical, adjacent, canonical)
            val upstream = mockk<Player>(relaxed = true)
            every { upstream.currentMediaItem } returns queue[2]
            every { upstream.mediaMetadata } returns canonical.mediaMetadata
            every { upstream.playbackState } returns Player.STATE_READY
            every { upstream.mediaItemCount } returns queue.size
            every { upstream.currentMediaItemIndex } returns 2
            every { upstream.getMediaItemAt(any()) } answers { queue[firstArg()] }
            val facade = SessionArtworkPlayer(upstream)
            assertEquals(listOf(songId, "adjacent", songId), (0 until facade.mediaItemCount).map { facade.getMediaItemAt(it).mediaId })
            assertEquals(2, facade.currentMediaItemIndex)
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(context, null) })
            every { provider.callingPackage } returns LocalArtworkRules.ANDROID_AUTO_PACKAGE
            val metadata = listOf(browse.mediaMetadata, facade.getMediaItemAt(2).mediaMetadata, facade.mediaMetadata)
            metadata.forEach { value ->
                val descriptor = requireNotNull(provider.openFile(requireNotNull(value.artworkUri), "r"))
                val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                try {
                    val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    assertTrue(Color.red(pixel) > 230 && Color.blue(pixel) > 230 && Color.green(pixel) < 20)
                } finally { bitmap.recycle() }
            }
        } finally {
            custom.recycle()
            if (previous == null) file.delete() else file.writeBytes(previous)
            unmockkAll()
        }
    }

    @Test fun `signed source survives large queues but rejects forged expired cross profile and private sources`() {
        val now = System.currentTimeMillis()
        val source = "https://i.ytimg.com/vi/example/maxresdefault.jpg"
        val first = requireNotNull(ArtworkContentProvider.uriForRemote(source, "a", true, 1, now + 60000))
        repeat(2048) { ArtworkContentProvider.uriForRemote("https://img.example.com/$it", "a", true, 1, now + 60000) }
        assertEquals(source to true, ArtworkContentProvider.remoteSource(first, "a", now))
        assertNull(ArtworkContentProvider.remoteSource(first, "b", now))
        assertNull(ArtworkContentProvider.remoteSource(first, "a", now + 60001))
        assertNull(ArtworkContentProvider.remoteSource(first.buildUpon().appendQueryParameter("ignored", "x")
            .encodedQuery(first.encodedQuery?.replace("example", "forged")).build(), "a", now))
        assertNull(ArtworkContentProvider.uriForRemote("file:///data/private", "a", true, 1))
        assertNotEquals(first, ArtworkContentProvider.uriForRemote(source, "a", false, 1, now + 60000))
        assertNotEquals(first, ArtworkContentProvider.uriForRemote(source, "a", true, 2, now + 60000))
    }

    @Test fun `live crop refresh publishes changed URI and metadata event without decoding in getter`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.preferences
        val prior = if (prefs.contains(cropVideoThumbnailsKey)) prefs.getBoolean(cropVideoThumbnailsKey, true) else null
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        try {
            prefs.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
            val item = MediaItem.Builder().setMediaId("video").setMediaMetadata(MediaMetadata.Builder()
                .setArtworkUri(android.net.Uri.parse("https://i.ytimg.com/vi/video/maxresdefault.jpg")).build()).build()
            val upstream = mockk<Player>(relaxed = true)
            every { upstream.currentMediaItem } returns item
            every { upstream.mediaMetadata } returns item.mediaMetadata
            every { upstream.playbackState } returns Player.STATE_READY
            var clock = System.currentTimeMillis()
            val facade = SessionArtworkPlayer(upstream) { clock }
            val listener = mockk<Player.Listener>(relaxed = true)
            facade.addListener(listener)
            val count = ArtworkBitmapDecoder.decodeCount.get()
            val first = requireNotNull(facade.mediaMetadata.artworkUri)
            repeat(100) { assertEquals(first, facade.mediaMetadata.artworkUri) }
            assertEquals(count, ArtworkBitmapDecoder.decodeCount.get())
            prefs.edit().putBoolean(cropVideoThumbnailsKey, false).commit()
            facade.refreshArtwork()
            val next = requireNotNull(facade.mediaMetadata.artworkUri)
            assertNotEquals(first, next)
            assertEquals("false", next.getQueryParameter("c"))
            verify(exactly = 1) { listener.onMediaMetadataChanged(any()) }
            verify(exactly = 1) { listener.onEvents(facade, any()) }
            assertEquals(count, ArtworkBitmapDecoder.decodeCount.get())
            clock += 24 * 60 * 60 * 1000L + 1
            val renewed = requireNotNull(facade.mediaMetadata.artworkUri)
            assertNotEquals(next, renewed)
            assertEquals(renewed, facade.mediaMetadata.artworkUri)
            assertTrue(requireNotNull(renewed.getQueryParameter("e")).toLong() > clock)
            assertNotNull(ArtworkContentProvider.remoteSource(renewed, requireNotNull(renewed.getQueryParameter("p")), clock))
            facade.removeListener(listener)
        } finally {
            prefs.edit().apply { if (prior == null) remove(cropVideoThumbnailsKey) else putBoolean(cropVideoThumbnailsKey, prior) }.commit()
            unmockkAll()
        }
    }

    @Test fun `offline reuses encoded HIGH pixels without Coil and cached MEDIUM cannot pin online quality`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        mockkObject(NetworkQualityHelper, ImageCacheFactory)
        var online = true
        every { NetworkQualityHelper.isNetworkConnected(any()) } answers { online }
        val red = Bitmap.createBitmap(1024, 576, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val blue = Bitmap.createBitmap(1024, 576, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val calls = mutableListOf<Pair<Boolean, ImageCacheFactory.NetworkQuality>>()
        coEvery { ImageCacheFactory.loadBitmap(any(), any(), any(), any(), any(), any()) } answers {
            val offline = arg<Boolean>(4)
            val quality = arg<ImageCacheFactory.NetworkQuality>(3)
            calls += offline to quality
            if (!offline) blue else if (quality == ImageCacheFactory.NetworkQuality.MEDIUM) red else null
        }
        try {
            val warmUrl = "https://img.example.com/${UUID.randomUUID()}"
            val warm = requireNotNull(SessionArtworkLoader.load(warmUrl))
            online = false
            assertArrayEquals(warm, SessionArtworkLoader.load(warmUrl))
            assertEquals(listOf(false to ImageCacheFactory.NetworkQuality.HIGH), calls)
            calls.clear()
            val coldUrl = "https://img.example.com/${UUID.randomUUID()}"
            val medium = requireNotNull(SessionArtworkLoader.load(coldUrl))
            assertEquals(listOf(true to ImageCacheFactory.NetworkQuality.HIGH,
                true to ImageCacheFactory.NetworkQuality.MEDIUM), calls)
            online = true
            val high = requireNotNull(SessionArtworkLoader.load(coldUrl))
            assertFalse(medium.contentEquals(high))
            assertEquals(false to ImageCacheFactory.NetworkQuality.HIGH, calls.last())
        } finally { red.recycle(); blue.recycle(); unmockkAll() }
    }

    @Test fun `warm large bitmap crops directly to full bounded square without recycling borrowed pixels`() {
        val source = Bitmap.createBitmap(2400, 1350, Bitmap.Config.ARGB_8888)
        try {
            val square = SessionArtworkLoader.boundedOwnedCopy(source, true)
            assertEquals(512, square.width)
            assertEquals(512, square.height)
            square.recycle()
            assertFalse(source.isRecycled)
            val original = SessionArtworkLoader.boundedOwnedCopy(source, false)
            assertEquals(512, original.width)
            assertEquals(288, original.height)
            original.recycle()
            assertFalse(source.isRecycled)
        } finally { source.recycle() }
    }
}
