package app.n_zik.android.core.coil

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import app.n_zik.android.utils.setActiveProfile

import app.n_zik.android.appContext

/** Repeated artwork loads must not invalidate every observing painter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageCacheFactoryPlaylistStoreTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var context: Context

    @Before fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        val stored = application.getSharedPreferences("playlist_thumbnail_store_default_legacy", Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        preferences = spyk(stored)
        context = mockk()
        every { context.filesDir } returns application.filesDir
        every { context.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE) } returns
            application.getSharedPreferences("profile_preferences", Context.MODE_PRIVATE)
        every { context.getSharedPreferences("playlist_thumbnail_store_default_legacy", Context.MODE_PRIVATE) } returns preferences
        val other = application.getSharedPreferences("playlist_thumbnail_store_voyage_legacy", Context.MODE_PRIVATE)
        other.edit().clear().commit()
        every { context.getSharedPreferences("playlist_thumbnail_store_voyage_legacy", Context.MODE_PRIVATE) } returns other
        setActiveProfile("default", context)
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
    }

    @Test fun `the same learned thumbnail key is isolated by profile`() {
        val main = "https://i.ytimg.com/pl_c/same/mwEKC.jpg?rs=main"
        val voyage = "https://i.ytimg.com/pl_c/same/mwEKC.jpg?rs=voyage"
        ImageCacheFactory.PlaylistThumbnailStore.save(main)
        setActiveProfile("voyage", context)
        ImageCacheFactory.PlaylistThumbnailStore.save(voyage)
        assertEquals(voyage, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("same"))
        setActiveProfile("default", context)
        assertEquals(main, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("same"))
    }

    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun `identical and lower quality loads do not write or notify painters again`() {
        val high = "https://i.ytimg.com/pl_c/playlist/mwEKC.jpg"
        val before = ProfileArtworkCache.storeVersion.value

        ImageCacheFactory.PlaylistThumbnailStore.save(high)
        repeat(10) { ImageCacheFactory.PlaylistThumbnailStore.save(high) }
        ImageCacheFactory.PlaylistThumbnailStore.save("https://i.ytimg.com/pl_c/playlist/mwEHKC.jpg")

        assertEquals(high, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("playlist"))
        assertEquals(before + 1, ProfileArtworkCache.storeVersion.value)
        verify(exactly = 1) { preferences.edit() }

        val replacement = "$high?revision=2"
        ImageCacheFactory.PlaylistThumbnailStore.save(replacement)

        assertEquals(replacement, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("playlist"))
        assertEquals(before + 2, ProfileArtworkCache.storeVersion.value)
        verify(exactly = 2) { preferences.edit() }
    }

    @Test fun `concurrent duplicate loads preserve both quality slots with one notification per change`() {
        val high = "https://i.ytimg.com/podcasts_artwork/show/mwEKC.jpg"
        val low = "https://i.ytimg.com/podcasts_artwork/show/mwEIC.jpg"
        val before = ProfileArtworkCache.storeVersion.value
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val calls = (0 until 16).map { index ->
                workers.submit {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    ImageCacheFactory.PlaylistThumbnailStore.save(if (index % 2 == 0) high else low)
                }
            }
            start.countDown()
            calls.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
        }

        assertEquals(high, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("show"))
        assertEquals(low, ImageCacheFactory.PlaylistThumbnailStore.getLowUrl("show"))
        assertEquals(before + 2, ProfileArtworkCache.storeVersion.value)
        verify(exactly = 2) { preferences.edit() }
    }

    @Test fun `video thumbnails do not write playlist metadata or notify painters`() {
        val video = "https://i.ytimg.com/vi/track/maxresdefault.jpg"
        val before = ProfileArtworkCache.storeVersion.value

        ImageCacheFactory.PlaylistThumbnailStore.save(video)

        assertEquals("https://i.ytimg.com/vi/track/hqdefault.jpg", video.thumbnail(300))
        assertNull(ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("track"))
        assertEquals(before, ProfileArtworkCache.storeVersion.value)
        verify(exactly = 0) { preferences.edit() }
    }

    @Test fun `playlist and podcast thumbnail resolution can reuse a learned signed source`() {
        for (path in listOf("pl_c", "podcasts_artwork")) {
            val original = "https://i.ytimg.com/$path/$path/mwEIC.jpg?rs=original"
            val learned = "https://i.ytimg.com/$path/$path/mwEKC.jpg?rs=learned"
            assertEquals(original, original.thumbnail(700))

            ImageCacheFactory.PlaylistThumbnailStore.save(learned)

            assertEquals(learned, original.thumbnail(700))
        }
    }

    @Test fun `a legacy video entry does not override a playlist cover with the same id`() {
        val playlist = "https://i.ytimg.com/pl_c/shared/mwEIC.jpg?rs=original"
        preferences.edit().putString("shared", "|https://i.ytimg.com/vi/shared/maxresdefault.jpg").commit()

        assertEquals(playlist, playlist.thumbnail(700))

        val learned = "https://i.ytimg.com/pl_c/shared/mwEHKC.jpg?rs=learned"
        ImageCacheFactory.PlaylistThumbnailStore.save(learned)

        assertEquals(learned, playlist.thumbnail(700))
    }
}
