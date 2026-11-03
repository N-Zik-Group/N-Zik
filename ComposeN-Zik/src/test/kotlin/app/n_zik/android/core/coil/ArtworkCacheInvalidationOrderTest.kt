package app.n_zik.android.core.coil

import android.app.Application
import app.n_zik.android.appContext
import app.n_zik.android.components.player.SharedArtworkColors
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ArtworkCacheInvalidationOrderTest {
    private val memory = mockk<MemoryCache>()
    private val disk = mockk<DiskCache>()

    @Before fun prepareCaches() {
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns RuntimeEnvironment.getApplication()
        val loader = mockk<ImageLoader>()
        every { loader.memoryCache } returns memory
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.DISK_CACHE } returns disk
    }

    @After fun releaseMocks() { unmockkAll() }

    @Test fun `new color generation is published after targeted pixel eviction and internal cleanup stays silent`() {
        val generation = SharedArtworkColors.generation.value
        val events = mutableListOf<String>()
        every { memory.remove(any()) } answers {
            assertEquals(generation, SharedArtworkColors.generation.value)
            events += "memory"
            true
        }
        every { disk.remove(any()) } answers {
            assertEquals(generation, SharedArtworkColors.generation.value)
            events += "disk"
            true
        }
        ImageCacheFactory.clearCacheForKey("test://original", ImageCacheFactory.NetworkQuality.HIGH, invalidateColors = false)
        assertEquals(generation, SharedArtworkColors.generation.value)
        ImageCacheFactory.clearCacheForKey("test://original", ImageCacheFactory.NetworkQuality.HIGH)
        assertEquals(listOf("memory", "disk", "memory", "disk"), events)
        assertEquals(generation + 1, SharedArtworkColors.generation.value)
    }

    @Test fun `full pixel cache clear finishes before observers receive a new color generation`() {
        val generation = SharedArtworkColors.generation.value
        val events = mutableListOf<String>()
        every { memory.clear() } answers {
            assertEquals(generation, SharedArtworkColors.generation.value)
            events += "memory"
        }
        every { disk.clear() } answers {
            assertEquals(generation, SharedArtworkColors.generation.value)
            events += "disk"
        }
        ImageCacheFactory.clearImageCache()
        assertEquals(listOf("memory", "disk"), events)
        assertEquals(generation + 1, SharedArtworkColors.generation.value)
    }
}
