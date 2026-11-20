package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.CircleShape
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
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
import app.n_zik.android.utils.cropVideoThumbnailsKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.appContext
import app.n_zik.android.components.player.ArtworkColorSnapshot
import app.n_zik.android.components.player.SharedArtworkColors
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewAssets
import app.n_zik.android.components.settings.appearance.preview.rememberThemePreviewAppearance
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.playback.services.ArtworkContentProvider
import app.n_zik.android.utils.coroutines.NzikDispatchers
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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    private var requestGate: CompletableDeferred<Unit>? = null
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
                    requestGate?.await()
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

    @Test fun `player keeps the loaded cover while the same request refreshes`() {
        val loaded = AtomicInteger()
        lateinit var mountedPainter: AsyncImagePainter
        source = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
            )) {
                val painter = ImageCacheFactory.Painter(
                    VIDEO_URL, playerArtworkMediaId = "video", onSuccess = { loaded.incrementAndGet() },
                )
                SideEffect { mountedPainter = painter }
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).background(ComposeColor.Black).testTag("retainedCover"),
                )
            }
        }
        fun centerPixel(): Int {
            val image = compose.onNodeWithTag("retainedCover").captureToImage().asAndroidBitmap()
            return image.getPixel(image.width / 2, image.height / 2)
        }
        compose.waitUntil(10_000) { loaded.get() >= 1 }
        assertEquals(Color.GREEN, centerPixel())

        val gate = CompletableDeferred<Unit>()
        requestGate = gate
        try {
            compose.runOnIdle { mountedPainter.restart() }
            compose.waitUntil(10_000) { requests.get() >= 2 }
            assertEquals(Color.GREEN, centerPixel())
        } finally {
            gate.complete(Unit)
        }
    }

    @Test fun `delayed real painter supplies shared colors without canonical decode`() {
        val url = "https://example.com/elected-painter.jpg"
        val gate = CompletableDeferred<Unit>()
        requestGate = gate
        source = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 155, 215))
        }
        val colors = AtomicReference<Result<ArtworkColorSnapshot?>?>()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
            )) {
                ImageCacheFactory.ObserveDisplayedArtwork()
                Image(
                    painter = ImageCacheFactory.Painter(url, publishArtworkColors = true),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                LaunchedEffect(url) {
                    launch(NzikDispatchers.DATA) {
                        delay(55)
                        gate.complete(Unit)
                    }
                    colors.set(runCatching { SharedArtworkColors.load(context, url) })
                }
            }
        }
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            colors.get() != null
        }
        val snapshot = checkNotNull(colors.get()?.getOrThrow())
        assertTrue(snapshot.artworkSamples.any { it.blue > .7f && it.red < .15f })
        assertEquals(1, requests.get())
    }

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

    @Test fun `cold palette extraction decodes the same effective video source as the painter`() {
        val video = "https://i.ytimg.com/vi/palette-cold/hqdefault.jpg"
        val remote = requireNotNull(ArtworkContentProvider.uriForRemote(video, previousProfile, true, 0))
        source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }

        val colors = AtomicReference<Result<Pair<ArtworkColorSnapshot, ArtworkColorSnapshot?>>?>()
        compose.setContent {
            LaunchedEffect(remote) {
                colors.set(runCatching {
                    val cold = checkNotNull(SharedArtworkColors.load(context, "pinned:$remote"))
                    cold to SharedArtworkColors.load(context, video)
                })
            }
        }
        compose.waitUntil(10_000) { colors.get() != null }
        val (cold, direct) = checkNotNull(colors.get()).getOrThrow()
        assertSame(cold, direct)
        assertEquals(listOf("https://i.ytimg.com/vi/palette-cold/maxresdefault.jpg"), requestedModels.toList())
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

    @Test fun `forced light and dark previews share the demo atlas artwork decode`() {
        val document = """{"schemaVersion":2,"categories":{"colors":{"palette":"dynamic","mode":"system"}}}"""
        val atlasUri = ThemePreviewAssets.file(context, "atlas.jpg").toURI().toString()
        source = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(10, 155, 215))
        }
        SharedArtworkColors.invalidate()
        val snapshot = AtomicReference<ArtworkColorSnapshot?>()
        val displayed = AtomicReference<Pair<ColorPalette, ColorPalette>>()
        compose.setContent {
            val light = rememberThemePreviewAppearance(document, false, ColorPaletteMode.Light)
            val dark = rememberThemePreviewAppearance(document, true, ColorPaletteMode.Dark)
            SideEffect { displayed.set(light.appearance.colorPalette to dark.appearance.colorPalette) }
            LaunchedEffect(atlasUri) { snapshot.set(SharedArtworkColors.load(context, atlasUri)) }
        }
        compose.waitUntil(10_000) {
            snapshot.get()?.let { artwork ->
                displayed.get() == (artwork.lightPalette to artwork.darkPalette)
            } == true
        }
        assertEquals(listOf(atlasUri), requestedModels.toList())
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
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape,
            )) {
                val loaded = ImageCacheFactory.Painter(url, contentScale = requested, onSuccess = { success = it })
                SideEffect { painter = loaded }
                Image(loaded, null, Modifier.size(80.dp).background(ComposeColor.Black)
                    .testTag("cover"), contentScale = ContentScale.Fit)
            }
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
