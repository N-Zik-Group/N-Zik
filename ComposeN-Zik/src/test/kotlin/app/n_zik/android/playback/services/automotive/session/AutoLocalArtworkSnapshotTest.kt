package app.n_zik.android.playback.services.automotive.session

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.profilePreferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.playback.services.LocalArtworkRules
import app.n_zik.android.playback.services.automotive.browse.AutoBrowseTree
import app.n_zik.android.playback.services.automotive.models.ArtworkBitmapDecoder
import app.n_zik.android.playback.services.automotive.models.SessionMediaItemMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Local artwork must remain addressable after snapshots discard embedded bytes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutoLocalArtworkSnapshotTest {
    private lateinit var context: Context
    private lateinit var callback: AutoSessionCallback
    private lateinit var tree: AutoBrowseTree

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<TestApplication>()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        callback = AutoSessionCallback(context, Database, mockk<MyDownloadHelper>(relaxed = true))
        tree = AutoSessionCallback::class.java.getDeclaredField("autoBrowseTree").apply {
            isAccessible = true
        }.get(callback) as AutoBrowseTree
    }

    @After
    fun tearDown() {
        try {
            callback.release()
            tree.clearCache()
        } finally {
            unmockkAll()
        }
    }

    private fun assertCallbackArtwork(original: MediaItem, expected: Uri): MediaItem {
        val shown = tree.selectionStore.capture("album/local-art", listOf(original)).single()
        val retained = requireNotNull(tree.selectionStore.resolve(shown.mediaId)).items.single()
        val controller = mockk<MediaSession.ControllerInfo>()
        val session = mockk<MediaSession>()
        val set = callback.onSetMediaItems(session, controller, mutableListOf(shown), 0, 0L)
            .get(15, TimeUnit.SECONDS).mediaItems.single()
        val get = callback.onGetItem(mockk<MediaLibrarySession>(), controller, shown.mediaId)
            .get(15, TimeUnit.SECONDS)
        assertEquals(0, get.resultCode)
        val add = callback.onAddMediaItems(session, controller, mutableListOf(shown))
            .get(15, TimeUnit.SECONDS).single()
        listOf(retained, set, requireNotNull(get.value), add).forEach { item ->
            assertEquals(expected, item.mediaMetadata.artworkUri)
            assertNull(item.mediaMetadata.artworkData)
        }
        return requireNotNull(get.value)
    }

    @Test
    fun `snapshot item uri serves trusted MediaStore pixels as bounded seekable JPEG`() {
        val previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        val profile = "snapshot-mediastore-93803"
        val songId = "local:93803"
        val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, 93803L)
        // Different audio and album IDs prove that the provider resolves the album first.
        val albumUri = Uri.parse("content://media/external/audio/albumart/83903")
        val customCover = File(coversDir(context, profile), "cover_$songId.jpg")
        val file = File.createTempFile("snapshot-mediastore", ".png", context.cacheDir)
        try {
            assertTrue(context.profilePreferences.edit().putString(activeProfileKey, profile).commit())
            assertTrue("fixture must exercise MediaStore without a custom cover", !customCover.exists())
            val source = Bitmap.createBitmap(1700, 1100, Bitmap.Config.ARGB_8888)
            try {
                source.eraseColor(Color.RED)
                file.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { source.recycle() }
            val resolver = mockk<ContentResolver>()
            every { resolver.query(audioUri, any(), null, null, null) } answers {
                assertEquals(listOf(MediaStore.Audio.Media.ALBUM_ID), secondArg<Array<String>>().toList())
                MatrixCursor(arrayOf(MediaStore.Audio.Media.ALBUM_ID)).apply { addRow(arrayOf(83903L)) }
            }
            every { resolver.openInputStream(albumUri) } answers { file.inputStream() }
            val wrapped = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = resolver
            }
            every { appContext() } returns wrapped
            val provider = spyk(ArtworkContentProvider().also { it.attachInfo(wrapped, null) })
            every { provider.callingPackage } returns LocalArtworkRules.ANDROID_AUTO_PACKAGE

            val song = Song(songId, "Local title", "Artist", "1:00", albumUri.toString())
            val original = SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true)
            assertNotNull("fixture starts with embedded artwork which snapshots strip", original.mediaMetadata.artworkData)
            val returned = assertCallbackArtwork(original, ArtworkContentProvider.uriFor(songId))
            // Open the URI actually returned by onGetItem, after the snapshot removed bytes.
            val returnedUri = requireNotNull(returned.mediaMetadata.artworkUri)
            val descriptor = requireNotNull(provider.openFile(returnedUri, "r"))
            assertTrue("provider must return a regular nonempty file", descriptor.statSize > 0)
            val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val first = input.read()
                val second = input.read()
                assertEquals(0xff, first)
                assertEquals(0xd8, second)
                // Rewind the same open descriptor, which fails for a non-seekable pipe.
                input.channel.position(0L)
                assertEquals(0L, input.channel.position())
                input.readBytes()
            }
            val options = BitmapFactory.Options()
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
            try {
                assertEquals("image/jpeg", options.outMimeType)
                assertEquals(bitmap.width, bitmap.height)
                assertTrue("provider artwork must be bounded", bitmap.width in 1..512)
                val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                assertTrue("must serve MediaStore source pixels with JPEG tolerance",
                    Color.red(pixel) > 230 && Color.green(pixel) < 25 && Color.blue(pixel) < 25)
            } finally { bitmap.recycle() }
            verify(exactly = 1) { resolver.query(audioUri, any(), null, null, null) }
            verify(atLeast = 1) { resolver.openInputStream(albumUri) }
        } finally {
            file.delete()
            every { appContext() } returns context
            assertTrue(context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit())
        }
    }

    @Test
    fun `MediaStore artwork bytes survive snapshot callbacks through a provider uri`() {
        val file = File.createTempFile("local-snapshot-art", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val uri = Uri.parse("content://media/external/audio/albumart/93801")
            val resolver = mockk<ContentResolver>()
            every { resolver.openInputStream(uri) } answers { file.inputStream() }
            val wrapped = object : ContextWrapper(context) {
                override fun getContentResolver(): ContentResolver = resolver
            }
            every { appContext() } returns wrapped
            val song = Song("local:93801", "Local title", "Artist", "1:00", uri.toString())
            val expected = ArtworkContentProvider.uriFor(song.id)
            val before = ArtworkBitmapDecoder.decodeCount.get()
            assertEquals(expected, SessionMediaItemMapper.baseMediaItem(song).mediaMetadata.artworkUri)
            assertEquals(expected, SessionMediaItemMapper.mapSongToMediaItem(song).mediaMetadata.artworkUri)
            assertEquals(expected, SessionMediaItemMapper.mapSongToMediaItem(song, "album/local-art").mediaMetadata.artworkUri)
            assertEquals(before, ArtworkBitmapDecoder.decodeCount.get())
            verify(exactly = 0) { resolver.openInputStream(any()) }
            listOf(
                SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true),
                SessionMediaItemMapper.mapSongToMediaItem(song, "album/local-art", loadArtwork = true)
            ).forEach { original ->
                val bytes = requireNotNull(original.mediaMetadata.artworkData)
                val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
                assertTrue(Color.red(decoded.getPixel(32, 32)) > 230)
                decoded.recycle()
                assertCallbackArtwork(original, expected)
            }
            val generic = song.copy(id = "local:93802", thumbnailUrl = file.toURI().toASCIIString())
            val sourceUri = requireNotNull(SessionMediaItemMapper.baseMediaItem(generic).mediaMetadata.artworkUri)
            assertEquals("source", sourceUri.pathSegments.first())
            assertCallbackArtwork(SessionMediaItemMapper.mapSongToMediaItem(generic, loadArtwork = true), sourceUri)
            assertCallbackArtwork(
                SessionMediaItemMapper.mapSongToMediaItem(generic, "album/local-art", loadArtwork = true), sourceUri
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing and resource local artwork retain provider uri with serialized fallback`() {
        listOf(null, "android.resource://${context.packageName}/missing", "file:///missing/local-cover.png").forEachIndexed { index, uri ->
            val song = Song("local:${93810 + index}", "Local title", "Artist", "1:00", uri)
            val expected = ArtworkContentProvider.uriFor(song.id)
            assertEquals(expected, SessionMediaItemMapper.baseMediaItem(song).mediaMetadata.artworkUri)
            listOf(
                SessionMediaItemMapper.mapSongToMediaItem(song, loadArtwork = true),
                SessionMediaItemMapper.mapSongToMediaItem(song, "album/local-art", loadArtwork = true)
            ).forEach { original ->
                assertNotNull(original.mediaMetadata.artworkUri)
                assertCallbackArtwork(original, expected)
            }
        }
    }
}
