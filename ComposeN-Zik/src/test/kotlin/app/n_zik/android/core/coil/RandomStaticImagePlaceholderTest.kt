package app.n_zik.android.core.coil

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.enums.ColorPaletteMode
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.setActiveProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.crossfade
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import org.junit.Before
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RandomStaticImagePlaceholderTest {
    @get:Rule val compose = createComposeRule()
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setupPreferences() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @Test fun `one draw per logical identity and every polygon can be selected`() {
        val source = mutableStateOf("first")
        val revision = mutableIntStateOf(0)
        val unrelated = mutableIntStateOf(0)
        var draws = 0
        var latest: ImageVector? = null
        var observedTick = -1
        compose.setContent {
            val tick = unrelated.intValue
            val vector = rememberStaticImagePlaceholderVector(listOf(source.value, revision.intValue)) { count ->
                (draws++ % count)
            }
            SideEffect { latest = vector; observedTick = tick }
        }
        compose.runOnIdle {
            assertEquals(1, draws)
            assertSame(staticImagePlaceholderVectors[0], latest)
            unrelated.intValue++
        }
        compose.runOnIdle {
            assertEquals(1, observedTick)
            assertEquals(1, draws)
            assertSame(staticImagePlaceholderVectors[0], latest)
            source.value = "second"
        }
        compose.runOnIdle {
            assertEquals(2, draws)
            assertSame(staticImagePlaceholderVectors[1], latest)
        }
        for (index in 2 until staticImagePlaceholderVectors.size) {
            compose.runOnIdle { revision.intValue++ }
            compose.runOnIdle {
                assertEquals(index + 1, draws)
                assertSame(staticImagePlaceholderVectors[index], latest)
            }
        }
    }

    @Test fun `cache refreshes do not reroll pending remote facade shapes`() {
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val draws = AtomicInteger()
        val source = mutableStateOf("https://example.test/first.png")
        val loader = ImageLoader.Builder(context).crossfade(false).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    release.await()
                    return SuccessResult(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).asImage(),
                        chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory, Random.Default)
        every { ImageCacheFactory.LOADER } returns loader
        every { Random.Default.nextInt(staticImagePlaceholderVectors.size) } answers {
            draws.getAndIncrement() % staticImagePlaceholderVectors.size
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                    Column {
                        ImageCacheFactory.Thumbnail(source.value, shape = null, modifier = Modifier.size(48.dp).testTag("stableThumbnail"))
                        ImageCacheFactory.AsyncImage(source.value, shape = null, modifier = Modifier.size(48.dp).testTag("stableAsync"))
                        ImageCacheFactory.SubcomposeImage(source.value, modifier = Modifier.size(48.dp).testTag("stableSubcompose"))
                        val painter = rememberImageLoadingViewportPainter(ImageCacheFactory.Painter(source.value))
                        Image(painter, null, Modifier.size(48.dp).imageLoadingViewport(painter).testTag("stablePainter"))
                    }
                }
            }
            compose.waitUntil { requests.get() >= 4 }
            compose.waitForIdle()
            val tags = listOf("stableThumbnail", "stableAsync", "stableSubcompose", "stablePainter")
            val initial = tags.associateWith { pixels(it) }
            assertEquals(4, draws.get())
            repeat(3) {
                compose.runOnIdle { ImageCacheFactory.refreshArtworkQuality() }
                compose.waitForIdle()
                assertEquals("Global cache/quality updates keep the same visual identities", 4, draws.get())
                tags.forEach { tag -> assertArrayEquals("Pending shape remains static: $tag", initial.getValue(tag), pixels(tag)) }
            }
            compose.runOnIdle { source.value = "https://example.test/second.png" }
            compose.waitForIdle()
            assertEquals("A different logical source still gets a fresh choice", 8, draws.get())
        } finally {
            loader.shutdown()
            unmockkObject(ImageCacheFactory, Random.Default)
        }
    }

    @Test fun `pending model resolution and cache revisions keep the same local loading shape`() {
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val draws = AtomicInteger()
        val model = mutableStateOf<String?>(null)
        val revision = mutableIntStateOf(0)
        val loader = ImageLoader.Builder(context).crossfade(false).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    release.await()
                    return SuccessResult(Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).asImage(),
                        chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory, Random.Default)
        every { ImageCacheFactory.LOADER } returns loader
        every { Random.Default.nextInt(staticImagePlaceholderVectors.size) } answers {
            draws.getAndIncrement() % staticImagePlaceholderVectors.size
        }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                    Box(Modifier.size(48.dp, 96.dp)) {
                        ImageCacheFactory.LocalAsyncImage(model.value, null,
                            modifier = Modifier.size(48.dp, 96.dp).testTag("resolving"),
                            cacheKey = "revision-${revision.intValue}", fallback = R.drawable.checkmark,
                            fallbackDelayMs = 20, shape = null,
                            isResolvingModel = model.value == null, loadIdentity = "creator-avatar")
                    }
                }
            }
            compose.waitForIdle()
            val initial = pixels("resolving")
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(1_000)
            assertArrayEquals("Resolving a model must not time out to a fallback", initial, pixels("resolving"))
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { model.value = "https://example.test/avatar.png" }
            compose.waitForIdle()
            compose.waitUntil { requests.get() >= 1 }
            compose.waitForIdle()
            assertArrayEquals("The same glyph continues into actual image decoding", initial, pixels("resolving"))
            compose.runOnIdle { revision.intValue++ }
            compose.waitForIdle()
            assertArrayEquals("Cache revisions do not change the pending glyph", initial, pixels("resolving"))
            assertEquals(1, draws.get())
        } finally {
            compose.mainClock.autoAdvance = true
            loader.shutdown()
            unmockkObject(ImageCacheFactory, Random.Default)
        }
    }

    @Test fun `local null source preserves custom placeholder default shape timeout and absent fallback`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                Column {
                    ImageCacheFactory.LocalAsyncImage(null, null, placeholder = R.drawable.checkmark,
                        fallback = R.drawable.ic_launcher_box, fallbackDelayMs = 1_000,
                        modifier = Modifier.size(32.dp).testTag("custom"),
                        contentScale = ContentScale.Fit, shape = null)
                    ImageCacheFactory.LocalAsyncImage(null, null, fallback = R.drawable.checkmark,
                        fallbackDelayMs = 1_000, modifier = Modifier.size(32.dp).testTag("standard"),
                        contentScale = ContentScale.Fit, shape = null)
                    ImageCacheFactory.LocalAsyncImage(null, null, fallback = null,
                        fallbackDelayMs = 1_000, modifier = Modifier.size(32.dp).testTag("absent"),
                        contentScale = ContentScale.Fit, shape = null)
                    Image(painterResource(R.drawable.checkmark), null,
                        Modifier.size(32.dp).testTag("checkmark"), contentScale = ContentScale.Fit)
                    Image(painterResource(R.drawable.ic_launcher_box), null,
                        Modifier.size(32.dp).testTag("box"), contentScale = ContentScale.Fit)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        assertArrayEquals(pixels("checkmark"), pixels("custom"))
        assertTrue(pixels("standard").any { it == DefaultDarkColorPalette.accent.toArgb() })
        assertTrue(pixels("absent").any { it == DefaultDarkColorPalette.accent.toArgb() })
        compose.mainClock.advanceTimeBy(1_100)
        compose.waitForIdle()
        assertArrayEquals(pixels("box"), pixels("custom"))
        assertArrayEquals(pixels("checkmark"), pixels("standard"))
        compose.onNodeWithTag("absent").assertDoesNotExist()
    }

    @Test fun `loading backgrounds follow theme while retaining glyph clipping and custom artwork`() {
        val palette = mutableStateOf(DefaultDarkColorPalette)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(palette.value)) {
                val themed = rememberThemedImageLoadingPainter("stable")
                Column {
                    Image(themed, null, Modifier.size(48.dp).testTag("themed"))
                    Image(themed, null, Modifier.size(48.dp).testTag("painterAlpha"), alpha = .5f)
                    Image(themed, null,
                        Modifier.size(48.dp).graphicsLayer { alpha = .5f }.testTag("groupAlpha"))
                    Box(Modifier.size(48.dp).background(Color.Magenta)) {
                        Image(rememberThemedImageLoadingPainter("circle"), null,
                            Modifier.size(48.dp).clip(CircleShape).testTag("circle"))
                    }
                    Image(rememberThemedImageLoadingPainter("custom", R.drawable.checkmark), null,
                        Modifier.size(48.dp).testTag("customTheme"))
                    Image(rememberImageLoadingPainter("transparent"), null,
                        Modifier.size(48.dp).testTag("transparent"))
                }
            }
        }
        compose.waitForIdle()
        val dark = pixels("themed")
        val custom = pixels("customTheme")
        val transparent = pixels("transparent")
        assertEquals(DefaultDarkColorPalette.background1.toArgb(), dark.first())
        val groupAlpha = pixels("groupAlpha")
        val painterAlpha = pixels("painterAlpha")
        assertEquals("Group and painter captures have equal dimensions",
            compose.onNodeWithTag("groupAlpha").fetchSemanticsNode().boundsInRoot.size,
            compose.onNodeWithTag("painterAlpha").fetchSemanticsNode().boundsInRoot.size)
        assertEquals("Group and painter captures have equal pixel counts", groupAlpha.size, painterAlpha.size)
        val maximumAlphaDifference = groupAlpha.indices.maxOf { index ->
            listOf(0, 8, 16, 24).maxOf { shift ->
                kotlin.math.abs(((groupAlpha[index] ushr shift) and 255) -
                    ((painterAlpha[index] ushr shift) and 255))
            }
        }
        assertTrue("Image alpha fades the complete frame; maximum channel difference=$maximumAlphaDifference",
            maximumAlphaDifference <= 2)
        assertEquals(Color.Magenta.toArgb(), pixels("circle").first())
        compose.runOnIdle { palette.value = DefaultLightColorPalette }
        compose.waitForIdle()
        val light = pixels("themed")
        assertEquals(DefaultLightColorPalette.background1.toArgb(), light.first())
        val accent = DefaultDarkColorPalette.accent.toArgb()
        assertTrue("A readable accent remains exact", dark.any { it == accent } && light.any { it == accent })
        assertTrue("The selected shape stays in place across themes", dark.indices.all { index ->
            if (dark[index] == accent || light[index] == accent) {
                listOf(0, 8, 16).all { shift ->
                    kotlin.math.abs(((dark[index] ushr shift) and 255) - ((accent ushr shift) and 255)) <= 2 &&
                        kotlin.math.abs(((light[index] ushr shift) and 255) - ((accent ushr shift) and 255)) <= 2
                }
            } else true
        })
        assertEquals(Color.Magenta.toArgb(), pixels("circle").first())
        assertArrayEquals(custom, pixels("customTheme"))
        assertArrayEquals(transparent, pixels("transparent"))
        compose.runOnIdle {
            context.preferences.edit().putEnum(colorPaletteModeKey, ColorPaletteMode.PitchBlack).commit()
            palette.value = DefaultDarkColorPalette.copy(background0 = Color.Black, background1 = Color.Black)
        }
        compose.waitForIdle()
        val pitchBlack = pixels("themed")
        assertEquals("PitchBlack image plate follows the elevated navbar, not the black page",
            0xff171717.toInt(), pitchBlack.first())
        val center = pitchBlack.size / 2 + kotlin.math.sqrt(pitchBlack.size.toDouble()).toInt() / 2
        assertTrue(ColorUtils.calculateContrast(pitchBlack[center], pitchBlack.first()) >= 3.0)
    }

    @Test fun `rectangular Fit null images fill the entire loading frame`() {
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                Column {
                    ImageCacheFactory.Thumbnail(null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(96.dp, 48.dp).testTag("thumbnailFit"), shape = null)
                    ImageCacheFactory.AsyncImage(null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(96.dp, 48.dp).testTag("asyncFit"), shape = null)
                    ImageCacheFactory.SubcomposeImage(null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(96.dp, 48.dp).testTag("subcomposeFit"))
                    ImageCacheFactory.LocalAsyncImage(null, null, contentScale = ContentScale.Fit,
                        modifier = Modifier.size(48.dp, 96.dp).testTag("localFit"), shape = null)
                }
            }
        }
        compose.waitForIdle()
        listOf("thumbnailFit", "asyncFit", "subcomposeFit", "localFit").forEach { tag ->
            val frame = pixels(tag)
            assertEquals("The Fit margin belongs to the loading plate: $tag",
                DefaultDarkColorPalette.background1.toArgb(), frame.first())
            assertEquals(DefaultDarkColorPalette.background1.toArgb(), frame.last())
            assertTrue(frame.any { it == DefaultDarkColorPalette.accent.toArgb() })
        }
    }

    @Test fun `Crop and FillBounds keep one shared loading glyph contained and restore its viewport`() {
        val scale = mutableStateOf(ContentScale.Crop)
        val viewportOnly = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                val painter = rememberThemedImageLoadingPainter("shared-rectangles")
                val squarePainter = rememberImageLoadingViewportPainter(painter)
                val portraitPainter = rememberImageLoadingViewportPainter(painter)
                val landscapePainter = rememberImageLoadingViewportPainter(painter)
                Column {
                    Image(squarePainter, null, Modifier.size(48.dp).imageLoadingViewport(squarePainter).testTag("squareGlyph"),
                        contentScale = scale.value)
                    val portraitFrame = if (viewportOnly.value) Modifier.imageLoadingViewport(portraitPainter)
                        else Modifier.imageLoadingBackground(portraitPainter)
                    val landscapeFrame = if (viewportOnly.value) Modifier.imageLoadingViewport(landscapePainter)
                        else Modifier.imageLoadingBackground(landscapePainter)
                    Image(portraitPainter, null, Modifier.size(48.dp, 192.dp).then(portraitFrame).testTag("portraitGlyph"),
                        contentScale = scale.value)
                    Image(landscapePainter, null, Modifier.size(192.dp, 48.dp).then(landscapeFrame).testTag("landscapeGlyph"),
                        contentScale = scale.value)
                    Image(painter, null, Modifier.size(96.dp).testTag("unscopedGlyph"))
                }
            }
        }
        val accent = DefaultDarkColorPalette.accent.toArgb()
        fun inkBounds(tag: String): Pair<Int, Int> {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val ink = buildList {
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                    if (bitmap.getPixel(x, y) == accent) add(x to y)
            }
            assertTrue("The loading symbol is visible in $tag", ink.isNotEmpty())
            val left = ink.minOf { it.first }; val right = ink.maxOf { it.first }
            val top = ink.minOf { it.second }; val bottom = ink.maxOf { it.second }
            val shortEdge = minOf(bitmap.width, bitmap.height)
            assertTrue("$tag keeps horizontal margins", left > 0 && right < bitmap.width - 1)
            assertTrue("$tag keeps vertical margins", top > 0 && bottom < bitmap.height - 1)
            assertTrue("$tag fits the short edge", maxOf(right - left + 1, bottom - top + 1) <= shortEdge * .9f)
            assertTrue("$tag remains proportionally visible", minOf(right - left + 1, bottom - top + 1) >= shortEdge * .3f)
            return (right - left + 1) to (bottom - top + 1)
        }
        for (direct in listOf(false, true)) for (mode in listOf(ContentScale.Crop, ContentScale.FillBounds)) {
            compose.runOnIdle { scale.value = mode; viewportOnly.value = direct }
            val square = inkBounds("squareGlyph")
            assertEquals("Portrait preserves the same undistorted symbol", square, inkBounds("portraitGlyph"))
            assertEquals("Landscape preserves the same undistorted symbol", square, inkBounds("landscapeGlyph"))
            val large = inkBounds("unscopedGlyph")
            assertTrue("A prior frame cannot constrain a later unscoped drawing", large.first >= square.first * 1.7f)
        }
    }

    @Test fun `viewport proxy preserves successful rectangular artwork geometry alpha and tint`() {
        val bitmap = Bitmap.createBitmap(24, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        for (y in 0 until 24) for (x in 0 until 24) bitmap.setPixel(x, y, android.graphics.Color.BLUE)
        val scale = mutableStateOf(ContentScale.Fit)
        compose.setContent {
            val source = remember { BitmapPainter(bitmap.asImageBitmap()) }
            val framed = rememberImageLoadingViewportPainter(source)
            SideEffect { assertEquals(source.intrinsicSize, framed.intrinsicSize) }
            Column {
                Image(source, null, Modifier.size(96.dp, 48.dp).background(Color.Magenta).testTag("directSuccess"),
                    contentScale = scale.value, alpha = .5f, colorFilter = ColorFilter.tint(Color.Cyan, BlendMode.Multiply))
                Image(framed, null, Modifier.size(96.dp, 48.dp).background(Color.Magenta)
                    .imageLoadingViewport(framed).testTag("framedSuccess"),
                    contentScale = scale.value, alpha = .5f, colorFilter = ColorFilter.tint(Color.Cyan, BlendMode.Multiply))
            }
        }
        for (mode in listOf(ContentScale.Fit, ContentScale.Crop, ContentScale.FillBounds)) {
            compose.runOnIdle { scale.value = mode }
            assertArrayEquals("The viewport only affects placeholders, preserving successful $mode artwork",
                pixels("directSuccess"), pixels("framedSuccess"))
        }
    }

    @Test fun `switching absent image identity restarts its fallback timeout`() {
        val identity = mutableStateOf("first")
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                Box(Modifier.size(48.dp)) {
                    ImageCacheFactory.LocalAsyncImage(null, null, loadIdentity = identity.value,
                        fallback = null, fallbackDelayMs = 1_000, shape = null,
                        modifier = Modifier.size(48.dp).testTag("absent"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("absent").assertDoesNotExist()
        // Let Compose complete placement when recreating the previously removed image node.
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { identity.value = "second" }
        compose.waitForIdle()
        compose.onNodeWithTag("absent").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithTag("absent").assertDoesNotExist()
    }

    @Test fun `successful transparent artwork removes rectangular loading plate without restarting request`() {
        assertTransparentTransition(ContentScale.Crop)
    }

    @Test fun `transparent Fit artwork also clears the loading plate from its margins`() {
        assertTransparentTransition(ContentScale.Fit)
    }

    private fun assertTransparentTransition(scale: ContentScale) {
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val successes = AtomicInteger()
        val transparent = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(context).crossfade(false).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    release.await()
                    return SuccessResult(transparent.asImage(), chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                    Box(Modifier.size(48.dp, 192.dp).background(Color.Magenta)) {
                        ImageCacheFactory.LocalAsyncImage("controlled", null,
                            contentScale = scale, shape = null,
                            modifier = Modifier.size(48.dp, 192.dp).testTag("transition"),
                            onSuccess = { successes.incrementAndGet() })
                    }
                }
            }
            compose.waitUntil { requests.get() == 1 }
            compose.waitForIdle()
            val loading = pixels("transition")
            assertEquals(DefaultDarkColorPalette.background1.toArgb(), loading.first())
            assertTrue(loading.any { it == DefaultDarkColorPalette.accent.toArgb() })
            release.complete(Unit)
            compose.waitUntil { successes.get() == 1 }
            compose.waitForIdle()
            assertTrue("Transparent artwork reveals the parent after loading",
                pixels("transition").all { it == Color.Magenta.toArgb() })
            assertEquals("Loading callbacks invalidate drawing without rebuilding the request", 1, requests.get())
        } finally {
            loader.shutdown()
            unmockkObject(ImageCacheFactory)
        }
    }

    @Test fun `raw Coil Crop keeps its outgoing loading glyph contained during crossfade`() {
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val successes = AtomicInteger()
        val transparent = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(context).crossfade(2000).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): SuccessResult {
                    requests.incrementAndGet()
                    release.await()
                    return SuccessResult(transparent.asImage(), chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        mockkObject(ImageCacheFactory)
        every { ImageCacheFactory.LOADER } returns loader
        try {
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance(DefaultDarkColorPalette)) {
                    val painter = rememberImageLoadingViewportPainter(
                        ImageCacheFactory.Painter("https://example.test/portrait.png",
                            onSuccess = { successes.incrementAndGet() }))
                    Box(Modifier.size(48.dp, 192.dp).background(Color.Magenta)) {
                        Image(painter, null, Modifier.size(48.dp, 192.dp)
                            .imageLoadingViewport(painter).testTag("rawCrossfade"), contentScale = ContentScale.Crop)
                    }
                }
            }
            compose.waitUntil { requests.get() == 1 }
            fun assertContained() {
                val bitmap = compose.onNodeWithTag("rawCrossfade").captureToImage().asAndroidBitmap()
                val plate = bitmap.getPixel(0, 0)
                val ink = buildList {
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width)
                        if (bitmap.getPixel(x, y) != plate) add(x to y)
                }
                assertTrue("The loading glyph is still visible", ink.isNotEmpty())
                assertTrue("The glyph leaves horizontal margins", ink.minOf { it.first } > 0 && ink.maxOf { it.first } < bitmap.width - 1)
                assertTrue("The glyph fits the short edge vertically", ink.maxOf { it.second } - ink.minOf { it.second } < bitmap.width)
            }
            assertContained()
            compose.mainClock.autoAdvance = false
            release.complete(Unit)
            compose.waitUntil { successes.get() == 1 }
            compose.mainClock.advanceTimeByFrame()
            assertContained()
            compose.mainClock.autoAdvance = true
            compose.waitUntil(timeoutMillis = 5000) {
                pixels("rawCrossfade").all { it == Color.Magenta.toArgb() }
            }
            assertEquals("The viewport decorator never restarts the request", 1, requests.get())
        } finally {
            compose.mainClock.autoAdvance = true
            loader.shutdown()
            unmockkObject(ImageCacheFactory)
        }
    }

    @Test fun `navbar backgrounds retain a visible accent and stable shape for arbitrary themes`() {
        val readableAccent = Color(0xff8b5cf6)
        val palette = mutableStateOf(DefaultDarkColorPalette.copy(background0 = Color.Black, background1 = Color.Black, accent = readableAccent))
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance(palette.value)) {
                Image(rememberThemedImageLoadingPainter("contrast-stable"), null,
                    Modifier.size(48.dp).testTag("contrast"))
            }
        }
        compose.waitForIdle()
        val initial = pixels("contrast")
        val center = initial.size / 2 + kotlin.math.sqrt(initial.size.toDouble()).toInt() / 2
        val initialInk = initial[center]
        assertEquals(readableAccent.toArgb(), initialInk)
        val themes = listOf(
            Color.Black to readableAccent,
            Color.White to readableAccent,
            Color(0xff666666) to Color(0xff666666),
            Color(0xff334466) to Color(0xff3a496a),
            Color.White to Color(0xfff4aaaa),
            Color.Black to readableAccent.copy(alpha = 0f),
            Color.White to readableAccent.copy(alpha = .15f),
            Color.Black.copy(alpha = .2f) to Color.Black,
        )
        themes.forEach { (navigation, accent) ->
            compose.runOnIdle {
                palette.value = DefaultDarkColorPalette.copy(background0 = Color.Black,
                    background1 = navigation, background4 = Color.Magenta, accent = accent)
            }
            compose.waitForIdle()
            val frame = pixels("contrast")
            val ink = frame[center]
            val surface = frame.first()
            val expectedPlate = ColorUtils.compositeColors(navigation.toArgb(), Color.Black.toArgb())
            assertTrue("The plate matches the navbar composited over its page", listOf(0, 8, 16).all { shift ->
                kotlin.math.abs(((expectedPlate ushr shift) and 255) - ((surface ushr shift) and 255)) <= 1
            })
            assertTrue("The rendered accent stays at least 3:1 against its plate",
                ColorUtils.calculateContrast(ink, surface) >= 3.0)
            val original = accent.copy(alpha = 1f).toArgb()
            if (accent.alpha == 1f && ColorUtils.calculateContrast(original, surface) >= 3.05) {
                assertEquals("Already readable accents stay exact", original, ink)
            } else if (ColorUtils.calculateContrast(original, surface) < 3.05) {
                val before = FloatArray(3).also { ColorUtils.colorToHSL(original, it) }
                val after = FloatArray(3).also { ColorUtils.colorToHSL(ink, it) }
                if (before[1] > .05f && after[1] > .05f) {
                    val delta = kotlin.math.abs(before[0] - after[0])
                    assertTrue("Contrast adjustment keeps the accent hue", minOf(delta, 360f - delta) <= 3f)
                    assertEquals("Contrast adjustment keeps accent saturation", before[1], after[1], .04f)
                }
            }
            assertTrue("Theme changes preserve the selected shape", initial.indices.all { index ->
                if (initial[index] == initialInk || frame[index] == ink) {
                    listOf(0, 8, 16).all { shift ->
                        kotlin.math.abs(((initial[index] ushr shift) and 255) -
                            ((initialInk ushr shift) and 255)) <= 2 &&
                            kotlin.math.abs(((frame[index] ushr shift) and 255) -
                                ((ink ushr shift) and 255)) <= 2
                    }
                } else true
            })
        }
    }

    @Test fun `palette preserves loader geometry size tint and Material polygon family`() {
        val vectors = staticImagePlaceholderVectors
        val polygons = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
        assertEquals(polygons.size, vectors.size)
        assertTrue(vectors.size > 1)
        vectors.forEachIndexed { index, vector ->
            assertEquals(24.dp, vector.defaultWidth)
            assertEquals(24.dp, vector.defaultHeight)
            assertEquals(24f, vector.viewportWidth, 0f)
            assertEquals(24f, vector.viewportHeight, 0f)
            assertEquals(1, vector.root.size)
            val path = vector.root[0] as VectorPath
            assertEquals(SolidColor(Color(0xff666666)), path.fill)
            assertEquals(1f, path.fillAlpha, 0f)
            assertEquals(polygons[index].cubics.size, path.pathData.count { it is PathNode.CurveTo })
            assertTrue(path.pathData.first() is PathNode.MoveTo)
            assertEquals(PathNode.Close, path.pathData.last())
        }
        // Retained geometry golden from the former 24dp SoftBurst placeholder.
        val expected = floatArrayOf(
            15.742874f, 6.846393f, 15.901706f, 6.961304f, 16.100008f, 7.021656f,
            16.307783f, 7.005745f, 16.800079f, 6.968046f, 17.292374f, 6.930348f,
            17.784670f, 6.892650f, 18.416443f, 6.844271f, 18.878418f, 7.477441f,
            18.639536f, 8.064309f, 18.452330f, 8.524225f, 18.265123f, 8.984142f,
            18.077917f, 9.444060f, 17.920792f, 9.830072f, 18.065727f, 10.273105f,
            18.420618f, 10.491614f, 18.841053f, 10.750479f, 19.261486f, 11.009345f,
            19.681921f, 11.268210f, 20.221474f, 11.600418f, 20.223049f, 12.384206f,
            19.684837f, 12.718580f, 19.263052f, 12.980623f, 18.841267f, 13.242668f,
            18.419481f, 13.504711f, 18.065472f, 13.724647f, 17.922319f, 14.168259f,
            18.080996f, 14.553637f, 18.268978f, 15.010186f, 18.456959f, 15.466739f,
            18.644939f, 15.923289f, 18.886179f, 16.509192f, 18.426756f, 17.144217f,
            17.794792f, 17.098379f, 17.299536f, 17.062458f, 16.804279f, 17.026535f,
            16.309021f, 16.990614f, 15.893347f, 16.960464f, 15.516787f, 17.235210f,
            15.418638f, 17.640255f, 15.302364f, 18.120104f, 15.186090f, 18.599955f,
            15.069815f, 19.079805f, 14.920597f, 19.695608f, 14.175659f, 19.939312f,
            13.691333f, 19.530769f, 13.311776f, 19.210602f, 12.932219f, 18.890436f,
            12.552662f, 18.570271f, 12.234095f, 18.301552f, 11.767960f, 18.302490f,
            11.450477f, 18.572487f, 11.074360f, 18.892349f, 10.698243f, 19.212212f,
            10.322126f, 19.532074f, 9.839449f, 19.942562f, 9.093534f, 19.701859f,
            8.941843f, 19.086662f, 8.822963f, 18.604542f, 8.704083f, 18.122425f,
            8.585204f, 17.640306f, 8.485426f, 17.235661f, 8.107763f, 16.962431f,
            7.692215f, 16.994251f, 7.199920f, 17.031952f, 6.707623f, 17.069649f,
            6.215328f, 17.107347f, 5.583555f, 17.155727f, 5.121582f, 16.522558f,
            5.360463f, 15.935690f, 5.547671f, 15.475771f, 5.734877f, 15.015854f,
            5.922083f, 14.555936f, 6.079206f, 14.169923f, 5.934270f, 13.726891f,
            5.579379f, 13.508381f, 5.158947f, 13.249516f, 4.738513f, 12.990651f,
            4.318080f, 12.731785f, 3.778527f, 12.399577f, 3.776951f, 11.615790f,
            4.315163f, 11.281416f, 4.736948f, 11.019373f, 5.158731f, 10.757330f,
            5.580516f, 10.495288f, 5.934525f, 10.275352f, 6.077678f, 9.831740f,
            5.919002f, 9.446362f, 5.731021f, 8.989811f, 5.543041f, 8.533259f,
            5.355058f, 8.076709f, 5.113818f, 7.490807f, 5.573241f, 6.855783f,
            6.205204f, 6.901620f, 6.700464f, 6.937541f, 7.195721f, 6.973464f,
            7.690979f, 7.009385f, 8.106651f, 7.039534f, 8.483213f, 6.764788f,
            8.581360f, 6.359744f, 8.697636f, 5.879893f, 8.813910f, 5.400043f,
            8.930183f, 4.920193f, 9.079400f, 4.304390f, 9.824340f, 4.060688f,
            10.308666f, 4.469230f, 10.688223f, 4.789396f, 11.067780f, 5.109562f,
            11.447335f, 5.429728f, 11.765903f, 5.698446f, 12.232038f, 5.697508f,
            12.549520f, 5.427510f, 12.925637f, 5.107648f, 13.301754f, 4.787785f,
            13.677871f, 4.467923f, 14.160549f, 4.057436f, 14.906462f, 4.298140f,
            15.058155f, 4.913337f, 15.177035f, 5.395455f, 15.295915f, 5.877573f,
            15.414795f, 6.359691f, 15.464683f, 6.562014f, 15.584043f, 6.731483f,
            15.742874f, 6.846393f,
        )
        val actual = (vectors.first().root[0] as VectorPath).pathData.flatMap { node ->
            when (node) {
                is PathNode.MoveTo -> listOf(node.x, node.y)
                is PathNode.CurveTo -> listOf(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                PathNode.Close -> emptyList()
                else -> error("Unexpected path command")
            }
        }.toFloatArray()
        assertArrayEquals(expected, actual, .00001f)
    }

    @Test fun `all expressive frames render gray and stay unchanged as time advances`() {
        // Mount all vectors once, avoiding captures during a VectorPainter content replacement.
        compose.setContent {
            Column {
                staticImagePlaceholderVectors.forEachIndexed { index, vector ->
                    Image(rememberVectorPainter(vector), null,
                        Modifier.size(48.dp).testTag("placeholder$index"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val before = staticImagePlaceholderVectors.indices.map { index ->
            pixels("placeholder$index").also { frame ->
                assertTrue("Each polygon has a visible neutral interior", frame.any { it == 0xff666666.toInt() })
                assertTrue("The polygon stays clear of its container corners", frame.first() != 0xff666666.toInt())
            }
        }
        assertEquals("All seven shapes have distinct rendered outlines", before.size,
            before.map { it.contentHashCode() }.distinct().size)
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        before.forEachIndexed { index, frame ->
            assertArrayEquals("Shape $index stays frozen", frame, pixels("placeholder$index"))
        }
    }

    private fun appearance(palette: ColorPalette) = Appearance(
        colorPalette = palette,
        typography = Typography.fallback,
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun pixels(tag: String): IntArray {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
    }
}
