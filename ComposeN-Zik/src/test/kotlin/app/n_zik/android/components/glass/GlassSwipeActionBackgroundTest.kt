package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.SwipeableContent
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSwipeActionBackgroundTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var previousMaster: Boolean? = null

    @Before fun saveMasterPreference() {
        previousMaster = if (context.preferences.contains(glassEnabledKey))
            context.preferences.getBoolean(glassEnabledKey, false) else null
    }

    @After fun restoreMasterPreference() {
        context.preferences.edit().apply {
            previousMaster?.let { putBoolean(glassEnabledKey, it) } ?: remove(glassEnabledKey)
        }.commit()
    }

    @Test fun `embedded rows expose their card at rest and retain the swipe action film`() {
        val revealed = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        compose.setContent {
            Appearance {
                CompositionLocalProvider(LocalSwipeRestingBackground provides Color.Transparent) {
                    Box(Modifier.size(160.dp, 64.dp).background(Color.Blue).testTag("card-row")) {
                        Box(Modifier.size(160.dp, 64.dp).glassSwipeActionBackground(
                            originalColor = Color.Green, shape = RoundedCornerShape(12.dp),
                            isRevealed = revealed.value,
                            settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance,
                                film = .5f, rim = 0f, source = GlassTintSource.None),
                            backdropColor = Color.Black,
                        ))
                    }
                }
            }
        }
        fun pixel(): Color {
            val pixels = compose.onNodeWithTag("card-row").captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 2]
        }
        listOf(false, true).forEach { glass ->
            compose.runOnIdle { enabled.value = glass; revealed.value = false }
            assertEquals(Color.Blue, pixel())
            compose.runOnIdle { revealed.value = true }
            assertNotEquals(Color.Blue, pixel())
            if (!glass) assertEquals(Color.Green, pixel())
            compose.runOnIdle { revealed.value = false }
            assertEquals(Color.Blue, pixel())
        }
    }

    @Test fun `native reveal is pixel identical and Glass uses translucent action film with safe backdrop`() {
        val wrapped = mutableStateOf(false)
        val settings = mutableStateOf(GlassSettings(enabled = false, film = .5f,
            tintStrength = 0f, blur = 0f, vibrancy = 0f, rim = 0f))
        val fill = mutableStateOf(Color.Green)
        val area = mutableStateOf(GlassArea.ActionButtons)
        val hosted = mutableStateOf(false)
        val revealed = mutableStateOf(true)
        compose.setContent {
            Appearance {
                val shape = RoundedCornerShape(12.dp)
                val forbidden = rememberCanvasBackdrop { error("Swipe reveal sampled its containing page") }
                val safePage = rememberCanvasBackdrop { drawRect(Color.Blue) }
                CompositionLocalProvider(LocalGlassBackdrop provides forbidden) {
                    Box(Modifier.size(160.dp, 56.dp).testTag("reveal").background(Color.Black).clip(shape)
                        .then(if (wrapped.value) Modifier.glassSwipeActionBackground(
                            fill.value, shape, revealed.value, settings.value, area.value,
                            backdrop = if (hosted.value) safePage else null, backdropColor = Color.Black,
                        ) else Modifier.background(fill.value)))
                }
            }
        }
        val native = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
        compose.runOnIdle { wrapped.value = true }
        val off = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
        for (y in 0 until off.height) for (x in 0 until off.width) assertEquals(native[x, y], off[x, y])
        compose.runOnIdle { settings.value = settings.value.copy(enabled = true, rim = 1f); revealed.value = false }
        val resting = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
        for (y in 0 until resting.height) for (x in 0 until resting.width) assertEquals(native[x, y], resting[x, y])
        compose.runOnIdle { revealed.value = true; settings.value = settings.value.copy(rim = 0f) }
        fun check(expected: Color) {
            val pixels = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(expected.red, actual.red, .02f)
            assertEquals(expected.green, actual.green, .02f)
            assertEquals(expected.blue, actual.blue, .02f)
            assertEquals(Color.Black, pixels[0, 0])
        }
        for (surface in listOf(GlassArea.ActionButtons, GlassArea.MiniPlayer)) {
            for (color in listOf(Color.Green, Color.Red)) {
                compose.runOnIdle { area.value = surface; fill.value = color; settings.value = settings.value.copy(enabled = true) }
                check(color.copy(alpha = .5f).compositeOver(Color.Black))
                compose.runOnIdle { hosted.value = true }
                check(color.copy(alpha = .5f).compositeOver(Color.Blue))
                compose.runOnIdle { hosted.value = false; settings.value = settings.value.copy(areaModes = mapOf(surface to GlassAreaMode.Disabled)) }
                check(color)
                compose.runOnIdle { settings.value = settings.value.copy(areaModes = emptyMap()) }
            }
        }
    }

    @Test fun `shared swipe renderer keeps both callbacks and returns to rest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
            .edit().clear().putFloat(glassFilmKey, .5f).commit()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        var left = 0
        var right = 0
        compose.setContent {
            Appearance {
                SwipeableContent(swipeToLeftIcon = R.drawable.close, swipeToRightIcon = R.drawable.close,
                    onSwipeToLeft = { left++ }, onSwipeToRight = { right++ },
                    backgroundColor = Color.Green, modifier = Modifier.testTag("swipe")) {
                    Box(Modifier.size(280.dp, 64.dp))
                }
            }
        }
        val row = compose.onNodeWithTag("swipe")
        val resting = row.captureToImage().toPixelMap()
        fun assertRestored() {
            val actual = row.captureToImage().toPixelMap()
            for (y in 0 until actual.height) for (x in 0 until actual.width) assertEquals(resting[x, y], actual[x, y])
        }
        assertEquals(Color.Green, resting[resting.width / 2, resting.height / 2])
        fun exercise(glass: Boolean): Pair<Int, Int> {
            for (direction in listOf(1f, -1f)) {
                row.performTouchInput {
                    down(Offset(if (direction > 0f) width * .1f else width * .9f, center.y))
                    moveBy(Offset(width * .2f * direction, 0f), 300)
                }
                val revealed = row.captureToImage().toPixelMap()
                if (glass) assertNotEquals(Color.Green, revealed[revealed.width / 2, revealed.height / 2])
                else assertEquals(Color.Green, revealed[revealed.width / 2, revealed.height / 2])
                row.performTouchInput { cancel() }
                compose.waitForIdle()
                assertRestored()
            }
            row.performTouchInput { swipe(Offset(width * .1f, center.y), Offset(width * .9f, center.y), 600) }
            compose.waitForIdle()
            compose.runOnIdle { org.junit.Assert.assertTrue(right > 0); assertEquals(0, left) }
            assertRestored()
            row.performTouchInput { swipe(Offset(width * .9f, center.y), Offset(width * .1f, center.y), 600) }
            compose.waitForIdle()
            compose.runOnIdle { org.junit.Assert.assertTrue(right > 0); org.junit.Assert.assertTrue(left > 0) }
            assertRestored()
            return left to right
        }
        val nativeCallbacks = exercise(glass = false)
        compose.runOnIdle {
            left = 0
            right = 0
            context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        }
        compose.waitForIdle()
        assertEquals("Glass must preserve the native swipe callback sequence", nativeCallbacks, exercise(glass = true))
    }

    @Composable private fun Appearance(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(12.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
