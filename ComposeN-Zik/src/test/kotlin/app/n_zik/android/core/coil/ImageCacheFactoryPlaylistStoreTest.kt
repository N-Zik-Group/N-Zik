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
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

import app.n_zik.android.appContext

/** Repeated artwork loads must not invalidate every observing painter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageCacheFactoryPlaylistStoreTest {
    private lateinit var preferences: SharedPreferences

    @Before fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        val stored = application.getSharedPreferences("playlist_thumbnail_store", Context.MODE_PRIVATE)
        stored.edit().clear().commit()
        preferences = spyk(stored)
        val context = mockk<Context>()
        every { context.getSharedPreferences("playlist_thumbnail_store", Context.MODE_PRIVATE) } returns preferences
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
    }

    @After fun tearDown() {
        unmockkAll()
    }

    @Test fun `identical and lower quality loads do not write or notify painters again`() {
        val high = "https://i.ytimg.com/vi/track/maxresdefault.jpg"
        val before = storeVersion()

        ImageCacheFactory.PlaylistThumbnailStore.save("track", high, true)
        repeat(10) { ImageCacheFactory.PlaylistThumbnailStore.save("track", high, true) }
        ImageCacheFactory.PlaylistThumbnailStore.save(
            "track", "https://i.ytimg.com/vi/track/sddefault.jpg", true,
        )

        assertEquals(high, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("track"))
        assertEquals(before + 1, storeVersion())
        verify(exactly = 1) { preferences.edit() }

        val replacement = "$high?revision=2"
        ImageCacheFactory.PlaylistThumbnailStore.save("track", replacement, true)

        assertEquals(replacement, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("track"))
        assertEquals(before + 2, storeVersion())
        verify(exactly = 2) { preferences.edit() }
    }

    @Test fun `concurrent duplicate loads preserve both quality slots with one notification per change`() {
        val high = "https://i.ytimg.com/vi/track/maxresdefault.jpg"
        val low = "https://i.ytimg.com/vi/track/hqdefault.jpg"
        val before = storeVersion()
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val calls = (0 until 16).map { index ->
                workers.submit {
                    assertTrue(start.await(5, TimeUnit.SECONDS))
                    ImageCacheFactory.PlaylistThumbnailStore.save(
                        "track", if (index % 2 == 0) high else low, index % 2 == 0,
                    )
                }
            }
            start.countDown()
            calls.forEach { it.get(5, TimeUnit.SECONDS) }
        } finally {
            workers.shutdownNow()
        }

        assertEquals(high, ImageCacheFactory.PlaylistThumbnailStore.getHighUrl("track"))
        assertEquals(low, ImageCacheFactory.PlaylistThumbnailStore.getLowUrl("track"))
        assertEquals(before + 2, storeVersion())
        verify(exactly = 2) { preferences.edit() }
    }

    private fun storeVersion(): Int {
        // Observe the actual invalidation signal without adding a production test accessor.
        val field = ImageCacheFactory::class.java.getDeclaredField("storeVersion")
        field.isAccessible = true
        return (field.get(ImageCacheFactory) as StateFlow<*>).value as Int
    }
}
