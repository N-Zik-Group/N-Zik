package app.n_zik.android.core.coil

import android.app.Application
import app.n_zik.android.appContext
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.utils.getActiveProfile
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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

    @Test fun `only the targeted artwork aliases observe the eviction and internal cleanup stays silent`() {
        val profile = getActiveProfile(RuntimeEnvironment.getApplication())
        val original = "https://i.ytimg.com/vi/current/hqdefault.jpg"
        val alias = "pinned:https://i.ytimg.com/vi/current/maxresdefault.jpg"
        val other = "https://i.ytimg.com/vi/upcoming/hqdefault.jpg"
        val target = requireNotNull(SharedArtworkColors.observe(original, profile))
        assertSame(target, SharedArtworkColors.observe(alias, profile))
        val unrelated = requireNotNull(SharedArtworkColors.observe(other, profile))
        val otherProfile = requireNotNull(SharedArtworkColors.observe(original, "another-profile"))
        val targetRevision = target.revision.value
        val unrelatedRevision = unrelated.revision.value
        val otherProfileRevision = otherProfile.revision.value
        val generation = SharedArtworkColors.generation.value
        val events = mutableListOf<String>()
        every { memory.remove(any()) } answers {
            assertEquals(targetRevision, target.revision.value)
            events += "memory"
            true
        }
        every { disk.remove(any()) } answers {
            assertEquals(targetRevision, target.revision.value)
            events += "disk"
            true
        }
        ImageCacheFactory.clearCacheForKey(original, ImageCacheFactory.NetworkQuality.HIGH, invalidateColors = false)
        assertEquals(targetRevision, target.revision.value)
        ImageCacheFactory.clearCacheForKey(original, ImageCacheFactory.NetworkQuality.HIGH)
        assertEquals(listOf("memory", "disk", "memory", "disk"), events)
        assertEquals(targetRevision + 1, target.revision.value)
        assertEquals(unrelatedRevision, unrelated.revision.value)
        assertEquals(otherProfileRevision, otherProfile.revision.value)
        assertEquals(generation, SharedArtworkColors.generation.value)
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

    @Test fun `local file and resized Google artwork aliases share their source observation`() {
        val profile = getActiveProfile(RuntimeEnvironment.getApplication())
        val file = requireNotNull(SharedArtworkColors.observe("file:///cache/my%20cover.jpg", profile))
        assertSame(file, SharedArtworkColors.observe("/cache/my cover.jpg", profile))
        val remote = requireNotNull(SharedArtworkColors.observe("https://yt3.ggpht.com/cover=w120-h120", profile))
        assertSame(remote, SharedArtworkColors.observe("https://yt3.ggpht.com/cover=s0", profile))
    }

    @Test fun `signed non-video artwork refresh removes the original remote pixels and invalidates its palette`() {
        val original = "https://example.com/cover.jpg"
        val profile = getActiveProfile(RuntimeEnvironment.getApplication())
        val signed = requireNotNull(ArtworkContentProvider.uriForRemote(original, profile, square = true, revision = 0))
        val model = signed.toString()
        val requestedSource = ImageCacheFactory.currentArtworkSource(model, profile)
        val signedKey = ImageCacheFactory.generateCacheKeySync(model, ImageCacheFactory.NetworkQuality.HIGH)
        assertEquals(model, ImageCacheFactory.artworkColorSource(model, profile)?.uri)
        assertTrue(ImageCacheFactory.isCurrentArtworkSource(requestedSource))
        val color = requireNotNull(SharedArtworkColors.observe(original, profile))
        assertSame(color, SharedArtworkColors.observe(signed.toString(), profile))
        val before = color.revision.value
        val originalKey = ImageCacheFactory.generateCacheKeySync(original, ImageCacheFactory.NetworkQuality.HIGH)
        val evicted = mutableSetOf<String>()
        every { memory.remove(any()) } returns true
        every { disk.remove(any()) } answers {
            assertEquals(before, color.revision.value)
            evicted += firstArg<String>()
            true
        }

        ImageCacheFactory.refreshArtworkSourcesOrThrow(setOf(signed.toString()), profile)

        assertTrue(originalKey in evicted)
        assertEquals(before + 1, color.revision.value)
        assertFalse(ImageCacheFactory.isCurrentArtworkSource(requestedSource))
        val renewed = ImageCacheFactory.currentArtworkSource(model, profile)
        assertTrue(ImageCacheFactory.isCurrentArtworkSource(renewed))
        assertEquals(requestedSource.uri, renewed.uri)
        assertEquals(ImageCacheFactory.artworkSourceRevision(original, profile), renewed.remoteRevision)
        assertNotEquals(signedKey, ImageCacheFactory.generateCacheKeySync(model, ImageCacheFactory.NetworkQuality.HIGH))
    }
}
