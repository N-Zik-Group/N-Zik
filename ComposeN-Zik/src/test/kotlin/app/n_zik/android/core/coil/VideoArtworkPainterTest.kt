package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.cropVideoThumbnailsKey
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.playback.services.ArtworkContentProvider
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.toBitmap
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VideoArtworkPainterTest {
    @get:Rule val compose = createComposeRule()
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val requests = AtomicInteger()
    private val requestedModels = CopyOnWriteArrayList<String>()
    private lateinit var loader: ImageLoader
    private lateinit var source: Bitmap
    private var sourceDataSource = DataSource.MEMORY_CACHE
    private var previousCrop: Boolean? = null
    private lateinit var previousProfile: String

    @Before fun setup() {
        previousProfile = getActiveProfile(context)
        previousCrop = context.preferences.all[cropVideoThumbnailsKey] as? Boolean
        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, true).commit()
        mockkStatic("app.n_zik.android.GlobalVarsKt")
        every { appContext() } returns context
        loader = ImageLoader.Builder(context).crossfade(true).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    requestedModels += chain.request.data.toString()
                    return SuccessResult(source.asImage(), chain.request, sourceDataSource)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        every { ImageCacheFactory.getDownloadDecision(any()) } returns
            ImageCacheFactory.DownloadDecision(false, ImageCacheFactory.NetworkQuality.HIGH)
    }

    @After fun cleanup() {
        loader.shutdown()
        setActiveProfile(previousProfile, context)
        context.preferences.edit().apply {
            previousCrop?.let { putBoolean(cropVideoThumbnailsKey, it) } ?: remove(cropVideoThumbnailsKey)
        }.commit()
        unmockkAll()
    }

    @Test fun `mounted wide video responds to crop changes through the real painter factory`() =
        checkMountedPainter(160, 80, VIDEO_URL, appliesCrop = true)

    @Test fun `mounted tall video responds to crop changes through the real painter factory`() =
        checkMountedPainter(80, 160, VIDEO_URL, appliesCrop = true)

    @Test fun `network video initially fitted responds to crop changes with loader crossfade enabled`() {
        sourceDataSource = DataSource.NETWORK
        checkMountedPainter(160, 80, VIDEO_URL, appliesCrop = true, initialCrop = false)
    }

    @Test fun `network video initially cropped responds to fit changes with loader crossfade enabled`() {
        sourceDataSource = DataSource.NETWORK
        checkMountedPainter(160, 80, VIDEO_URL, appliesCrop = true)
    }

    @Test fun `modified video covers retain their original geometry`() =
        checkMountedPainter(160, 80, "pinned:modified:$VIDEO_URL", appliesCrop = false)

    @Test fun `explicit fit painter keeps decorative geometry`() =
        checkMountedPainter(160, 80, VIDEO_URL, appliesCrop = false, requested = ContentScale.Fit)

    @Test fun `automotive crop snapshots load original video pixels for live phone changes`() {
        val uri = requireNotNull(ArtworkContentProvider.uriForRemote(VIDEO_URL, previousProfile, true, 0))
        checkMountedPainter(160, 80, uri.toString(), appliesCrop = true)
        assertEquals(listOf("https://i.ytimg.com/vi/video/maxresdefault.jpg"), requestedModels.toList())
    }

    @Test fun `provider normalization rejects wrong profiles expired signatures and tampering`() {
        val uri = requireNotNull(ArtworkContentProvider.uriForRemote(VIDEO_URL, previousProfile, true, 0))
        assertEquals(VIDEO_URL, displayArtworkSource(uri.toString(), previousProfile))
        assertEquals(uri.toString(), displayArtworkSource(uri.toString(), "another-profile"))
        val expired = requireNotNull(ArtworkContentProvider.uriForRemote(VIDEO_URL, previousProfile, true, 0,
            expires = System.currentTimeMillis() - 1)).toString()
        assertEquals(expired, displayArtworkSource(expired, previousProfile))
        val tampered = uri.toString().replace("c=true", "c=false")
        assertEquals(tampered, displayArtworkSource(tampered, previousProfile))
    }

    @Test fun `local private artist and custom provider sources retain their identity`() {
        val artist = requireNotNull(ArtworkContentProvider.uriForRemote(
            "https://lh3.googleusercontent.com/artist", previousProfile, true, 0)).toString()
        val video = requireNotNull(ArtworkContentProvider.uriForRemote(VIDEO_URL, previousProfile, true, 0))
        for (url in listOf(artist, "pinned:modified:$video", "content://media/external/images/42",
            "content://${ArtworkContentProvider.AUTHORITY}/source/private-token",
            ArtworkContentProvider.uriFor("song").toString(), "file:///private/cover.jpg")) {
            assertEquals(url, displayArtworkSource(url, previousProfile))
        }
    }

    @Test fun `mounted provider normalization invalidates when the active profile changes`() {
        val uri = requireNotNull(ArtworkContentProvider.uriForRemote(VIDEO_URL, previousProfile, true, 0)).toString()
        var resolved: String? = null
        compose.setContent { resolved = rememberDisplayArtworkSource(uri) }
        compose.runOnIdle {
            assertEquals(VIDEO_URL, resolved)
            setActiveProfile("video-crop-other-profile", context)
        }
        compose.runOnIdle {
            assertEquals(uri, resolved)
            setActiveProfile(previousProfile, context)
        }
        compose.runOnIdle { assertEquals(VIDEO_URL, resolved) }
    }

    private fun checkMountedPainter(
        width: Int,
        height: Int,
        url: String,
        appliesCrop: Boolean,
        requested: ContentScale = ContentScale.Crop,
        initialCrop: Boolean = true,
    ) {
        context.preferences.edit().putBoolean(cropVideoThumbnailsKey, initialCrop).commit()
        source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
            for (y in 0 until height) for (x in 0 until width) {
                val inCenter = if (width > height) x in width / 4 until width * 3 / 4
                    else y in height / 4 until height * 3 / 4
                if (inCenter) setPixel(x, y, Color.GREEN)
            }
        }
        var painter: AsyncImagePainter? = null
        var success: AsyncImagePainter.State.Success? = null
        compose.setContent {
            val loaded = ImageCacheFactory.Painter(url, contentScale = requested, onSuccess = { success = it })
            SideEffect { painter = loaded }
            Image(loaded, null, Modifier.size(80.dp).background(ComposeColor.Black)
                .testTag("cover"), contentScale = ContentScale.Fit)
        }
        compose.waitUntil(10_000) { success != null }
        compose.waitForIdle()
        val initialPainter = requireNotNull(painter)
        val initialRequests = requests.get()
        compose.runOnIdle {
            assertSame(source, success?.result?.image?.toBitmap())
            assertEquals(if (appliesCrop && initialCrop) Size(80f, 80f) else Size(width.toFloat(), height.toFloat()),
                initialPainter.intrinsicSize)
        }
        assertFrame(width > height, cropped = appliesCrop && initialCrop)
        compose.runOnIdle { context.preferences.edit().putBoolean(cropVideoThumbnailsKey, !initialCrop).commit() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(initialPainter, painter)
            assertEquals(if (appliesCrop && !initialCrop) Size(80f, 80f) else Size(width.toFloat(), height.toFloat()),
                initialPainter.intrinsicSize)
            assertEquals(initialRequests, requests.get())
        }
        assertFrame(width > height, cropped = appliesCrop && !initialCrop)
        compose.runOnIdle { context.preferences.edit().putBoolean(cropVideoThumbnailsKey, initialCrop).commit() }
        compose.waitForIdle()
        assertFrame(width > height, cropped = appliesCrop && initialCrop)
    }

    private fun assertFrame(wide: Boolean, cropped: Boolean) {
        val bitmap = compose.onNodeWithTag("cover").captureToImage().asAndroidBitmap()
        val horizontalEdge = bitmap.getPixel(bitmap.width / 8, bitmap.height / 2)
        val verticalEdge = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
        assertEquals(Color.GREEN, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertEquals(if (cropped) Color.GREEN else if (wide) Color.RED else Color.BLACK, horizontalEdge)
        assertEquals(if (cropped) Color.GREEN else if (wide) Color.BLACK else Color.RED, verticalEdge)
    }

    private companion object {
        const val VIDEO_URL = "https://i.ytimg.com/vi/video/hqdefault.jpg"
    }
}
