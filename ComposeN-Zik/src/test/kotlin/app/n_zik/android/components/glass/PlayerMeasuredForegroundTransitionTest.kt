package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.player.LocalPlayerContrastTarget
import app.n_zik.android.components.player.PaletteFade
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayerMeasuredForegroundTransitionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 1f
    })

    @Test
    fun `measured labels and glyphs share displayed progress and finish with readable ink`() {
        val frame = mutableStateOf(PlayerForegroundFrame(1, 0f))
        val surface = mutableStateOf(Color.White)
        val shape = RoundedCornerShape(16.dp)
        val palette = DefaultDarkColorPalette.copy(text = Color.White, iconButtonPlayer = Color.White)
        val appearance = Appearance(palette, Typography.fallback, shape, shape, shape)
        var text = Color.Unspecified
        var glyph = Color.Unspecified
        var custom = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalForegroundSourcePalette provides palette,
                LocalPlayerForegroundFrame provides frame.value,
            ) {
                SurfaceForegroundContent(surface.value, preferMeasuredText = true) {
                    val currentText = colorPalette().text
                    val currentGlyph = playerControlGlyphColor(currentText)
                    val customGlyph = playerControlGlyphColor(Color.Red)
                    SideEffect { text = currentText; glyph = currentGlyph; custom = customGlyph }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(Color.Black, text)
            assertEquals(text, glyph)
            frame.value = PlayerForegroundFrame(1, .2f)
            surface.value = Color.Black
        }
        compose.runOnIdle {
            assertTrue(contrastAgainst(text, surface.value) >= TextContrast)
            assertTrue(contrastAgainst(glyph, surface.value) >= IconContrast)
            assertEquals(Color.Red, custom)
            surface.value = Color(0xFFF0F0F0)
            frame.value = PlayerForegroundFrame(1, .6f)
        }
        compose.runOnIdle {
            assertTrue(contrastAgainst(text, surface.value) >= TextContrast)
            assertTrue(contrastAgainst(glyph, surface.value) >= IconContrast)
            frame.value = PlayerForegroundFrame(1, 1f)
        }
        compose.runOnIdle {
            assertEquals(Color.Black, text)
            assertTrue(contrastAgainst(text, surface.value) >= TextContrast)
            assertTrue(contrastAgainst(glyph, surface.value) >= IconContrast)
        }
    }

    @Test
    fun `rapid retarget begins at the measured ink already displayed`() {
        val ink = PlayerMeasuredInkFrame()
        ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(1, 1f))
        val middle = ink.resolveEndpoint(Color.White, Color.Black, PlayerForegroundFrame(2, .5f))
        val restarted = ink.resolveEndpoint(Color.White, Color.Black, PlayerForegroundFrame(3, 0f))
        assertEquals(middle, restarted)
        assertEquals(Color.White, ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(3, 1f)))
    }

    @Test
    fun `a changed regional endpoint advances from the displayed ink within the same generation`() {
        val ink = PlayerMeasuredInkFrame()
        ink.resolveEndpoint(Color.White, Color.Black, PlayerForegroundFrame(1, 1f))
        val previous = ink.resolveEndpoint(Color.White, Color.DarkGray, PlayerForegroundFrame(2, .6f))
        val rebased = ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(2, .6f))
        val next = ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(2, .8f))
        assertEquals(previous, rebased)
        assertTrue(next.red > rebased.red && next.red < 1f)
        assertEquals(Color.White, ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(2, 1f)))
    }

    @Test
    fun `a neutral glyph moves to the nearest readable shade while its old polarity remains viable`() {
        val ink = PlayerMeasuredInkFrame()
        ink.resolveEndpoint(Color.Black, Color.Black, PlayerForegroundFrame(1, 1f), Color.White)
        val surface = Color(140, 140, 140)
        val middle = ink.resolveEndpoint(Color.Black, Color.White, PlayerForegroundFrame(2, .6f), surface)
        assertTrue(middle.red > 0f && middle.red < surface.red)
        assertTrue(contrastAgainst(middle, surface) >= IconContrast)
        val dark = Color(20, 20, 20)
        val switched = ink.resolveEndpoint(Color.Black, Color.White, PlayerForegroundFrame(2, .7f), dark)
        assertTrue(switched.red > dark.red)
        assertTrue(contrastAgainst(switched, dark) >= IconContrast)
    }

    @Test
    fun `a darker sample does not dim text after the fade has switched to white ink`() {
        val ink = PlayerMeasuredInkFrame()
        ink.resolveEndpoint(Color.Black, Color.Black, PlayerForegroundFrame(1, 1f), Color.White, TextContrast)
        val crossover = Color(112, 112, 112)
        val switched = ink.resolveEndpoint(Color.Black, Color.White, PlayerForegroundFrame(2, .45f),
            crossover, TextContrast)
        assertTrue(contrastAgainst(Color.Black, crossover) < TextContrast)
        assertTrue(contrastAgainst(switched, crossover) >= TextContrast)

        val darkerSample = Color(42, 42, 42)
        val next = ink.resolveEndpoint(Color.Black, Color.White, PlayerForegroundFrame(2, .5f),
            darkerSample, TextContrast)
        assertTrue(next.red >= switched.red)
        assertTrue(contrastAgainst(next, darkerSample) >= TextContrast)
        assertEquals(Color.White, ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(2, 1f),
            darkerSample, TextContrast))
    }

    @Test
    fun `darkening album art does not darken already readable transport ink before the polarity switch`() {
        fun gray(value: Int) = Color(value, value, value)
        val ink = PlayerMeasuredInkFrame()
        val initialInk = gray(30)
        assertEquals(initialInk, ink.resolveEndpoint(initialInk, initialInk, PlayerForegroundFrame(1, 1f), gray(180)))
        val risingInk = gray(39)
        assertEquals(risingInk, ink.resolveEndpoint(risingInk, risingInk, PlayerForegroundFrame(2, .2f), gray(159)))
        val dippedInk = gray(13)
        val nearCrossover = gray(130)
        val held = ink.resolveEndpoint(dippedInk, dippedInk, PlayerForegroundFrame(2, .4f), nearCrossover)
        assertEquals(risingInk, held)
        assertTrue(contrastAgainst(held, nearCrossover) >= IconContrast)

        val darkSurface = gray(99)
        val switched = ink.resolveEndpoint(dippedInk, dippedInk, PlayerForegroundFrame(2, .45f), darkSurface)
        assertTrue(switched.red > darkSurface.red)
        assertTrue(contrastAgainst(switched, darkSurface) >= IconContrast)
        assertEquals(Color.White, ink.resolveEndpoint(Color.White, Color.White, PlayerForegroundFrame(2, 1f), gray(50)))
    }

    @Test
    fun `trusted polarity fade uses semantic ink from the first frame and restores local contrast at rest`() {
        val frame = mutableStateOf(PlayerForegroundFrame(1, 0f, changesPolarity = true))
        val ink = Color(130, 130, 130)
        val shape = RoundedCornerShape(16.dp)
        val palette = DefaultDarkColorPalette.copy(text = ink)
        val appearance = Appearance(palette, Typography.fallback, shape, shape, shape)
        val film = Color.White.copy(alpha = .1f)
        var displayed = emptyList<Color>()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalForegroundSourcePalette provides palette,
                LocalPlayerForegroundFrame provides frame.value,
                LocalPlayerFollowsPaletteInk provides true,
            ) {
                SurfaceForegroundContent(ink, preferMeasuredText = true) {
                    val label = colorPalette().text
                    val glyph = surfaceGlyphColor(label.copy(alpha = .75f), film)
                    val transport = playerControlGlyphColor(label, film, measuredBackdrop = ink)
                    val outlineInk = surfaceNeutralForeground(Color.White)
                    val accent = surfaceGlyphColor(Color.Red, film)
                    SideEffect { displayed = listOf(label, glyph, transport, outlineInk, accent) }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(listOf(ink, ink.copy(alpha = .75f), ink, ink, Color.Red), displayed)
            frame.value = PlayerForegroundFrame(1, .5f, changesPolarity = true)
        }
        compose.runOnIdle {
            assertEquals(listOf(ink, ink.copy(alpha = .75f), ink, ink, Color.Red), displayed)
            frame.value = PlayerForegroundFrame(1, 1f, changesPolarity = true)
        }
        compose.runOnIdle {
            assertEquals(Color.Black, displayed.first())
            assertTrue(contrastAgainst(displayed[2], effectiveSurfaceColor(ink, film)) >= IconContrast)
            assertEquals(Color.Red, displayed.last())
        }
    }

    @Test
    fun `same palette artwork advances the shared frame and late samples have no tail`() {
        compose.mainClock.autoAdvance = false
        val surface = mutableStateOf(Color.Black)
        val shape = RoundedCornerShape(16.dp)
        val palette = DefaultDarkColorPalette.copy(text = Color.White, iconButtonPlayer = Color.White)
        val appearance = Appearance(palette, Typography.fallback, shape, shape, shape)
        var setArtwork: ((Pair<String, Long>) -> Unit)? = null
        var frame: PlayerForegroundFrame? = null
        var displayedSurface = Color.Unspecified
        var inks = emptyList<Color>()
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                PaletteFade(durationMillis = 360) {
                    val target = checkNotNull(LocalPlayerContrastTarget.current)
                    val currentFrame = LocalPlayerForegroundFrame.current
                    SideEffect {
                        setArtwork = { target.value = it }
                        frame = currentFrame
                    }
                    val measuredSurface = surface.value
                    SurfaceForegroundContent(measuredSurface, preferMeasuredText = true) {
                        val currentInks = listOf(colorPalette().text,
                            playerControlGlyphColor(Color.White), surfaceGlyphColor(Color.White))
                        SideEffect {
                            displayedSurface = measuredSurface
                            inks = currentInks
                        }
                    }
                }
            }
        }

        compose.mainClock.advanceTimeUntil(1_000) { frame != null }
        var generation = checkNotNull(frame).generation
        compose.runOnIdle { checkNotNull(setArtwork)("first" to 0L) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil(1_000) {
            val current = frame
            current != null && current.generation > generation && current.progress == 1f
        }
        compose.runOnIdle {
            assertEquals(List(3) { Color.White }, inks)
            generation = checkNotNull(frame).generation
            checkNotNull(setArtwork)("second" to 0L)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil(1_000) {
            val current = frame
            current != null && current.generation > generation && current.progress in .01f.. .99f
        }
        compose.mainClock.advanceTimeUntil(1_000) { frame?.progress == 1f }
        compose.runOnIdle {
            assertEquals(1f, checkNotNull(frame).progress, 0f)
            generation = checkNotNull(frame).generation
            surface.value = Color.White
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeUntil(128) { displayedSurface == Color.White }
        compose.runOnIdle {
            assertEquals(PlayerForegroundFrame(generation, 1f), frame)
            assertEquals(List(3) { Color.Black }, inks)
        }
    }

    @Test
    fun `measured endpoint retains native behavior outside a palette fade`() {
        assertEquals(Color.White, measuredPlayerForeground(Color.Black, Color.Black, TextContrast))
        assertEquals(Color.Black, PlayerMeasuredInkFrame().resolveEndpoint(Color.White, Color.Black, null))
        assertEquals(Color.Red, measuredPlayerForeground(Color.Red, Color.White, TextContrast))
        assertEquals(Color.Transparent,
            measuredPlayerForeground(Color.Transparent, Color.White, TextContrast))
    }
}
