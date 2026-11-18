package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import app.n_zik.android.components.player.playerControlContrast
import app.n_zik.android.components.player.playerTintedArtworkSample
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
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
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SurfaceForegroundTest {
    // Compose caches Android's animation scale when its owner starts; pin the clock for this test.
    @get:Rule val compose = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale {
        override val scaleFactor: Float = 1f
    })
    private val shape = RoundedCornerShape(16.dp)
    private val appearance = Appearance(DefaultLightColorPalette, Typography.fallback, shape, shape, shape)
    private val lowFilm = GlassSettings(enabled = true, film = .08f, dialogDimming = .8f, menuDimming = .8f,
        optimizationEnabled = false)

    @Test fun `player text follows the measured zone and its Glass tint`() {
        val darkTitleBand = Color(0xFF202020)
        assertEquals(Color.White, measuredPlayerTextForeground(Color.Black, darkTitleBand))
        assertEquals(Color.Black, measuredPlayerTextForeground(Color.Black, Color.White))
        val lightGlassPlate = effectiveSurfaceColor(darkTitleBand, Color.White.copy(alpha = .9f))
        assertEquals(Color.Black, measuredPlayerTextForeground(Color.White, lightGlassPlate))
        assertEquals(Color.Red, measuredPlayerTextForeground(Color.Red, darkTitleBand))
    }

    @Test fun `active transport glyph contrasts against a bare background or its own Glass film`() {
        val gray = Color(0xFF6C6C6C)
        val film = Color.White.copy(alpha = .8f)
        var bare = Color.Unspecified
        var glazed = Color.Unspecified
        var themeAccent = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SurfaceForegroundContent(gray) {
                    val plainControl = playerControlGlyphColor(gray)
                    val glassControl = playerControlGlyphColor(Color.White, film)
                    val accentControl = playerControlGlyphColor(Color.Red)
                    SideEffect { bare = plainControl; glazed = glassControl; themeAccent = accentControl }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(contrastAgainst(bare, gray) >= IconContrast)
            assertTrue(contrastAgainst(glazed, effectiveSurfaceColor(gray, film)) >= IconContrast)
            assertEquals(Color.Red, themeAccent)
        }
    }

    @Test fun `foreground memo separates the native surface and the Glass plate`() {
        val memo = PlayerForegroundMemo()
        val transition = NeutralForegroundTransition(
            from = emptyList(),
            to = NeutralForegroundDecision(Color.White, Color.Black),
            progress = 1f,
            memo = memo,
        )
        assertEquals(Color.Black, transition.resolve(Color.Black, TextContrast))
        assertEquals(Color.White, transition.resolve(Color.Black, TextContrast, plate = Color.Black.copy(alpha = .95f)))
        assertEquals(Color.Black, transition.resolve(Color.Black, TextContrast))
    }

    @Test fun `foreground memo drops a calculated result when artwork is cleared`() {
        val memo = PlayerForegroundMemo()
        val key = NeutralForegroundEndpointKey(
            NeutralForegroundDecision(Color.White, Color.Black), Color.Black, TextContrast,
            plate = null, localSurface = null, glyph = false, enabled = true,
        )
        assertEquals(Color.Black, memo.resolve(key) { Color.Black })
        assertEquals(Color.Black, memo.resolve(key) { error("The same endpoint should be reused") })
        memo.clear()
        assertEquals(Color.White, memo.resolve(key) { Color.White })
    }

    @Test fun `film is evaluated after the scrim and glyphs keep readable accents`() {
        val surface = effectiveSurfaceColor(Color.White, Color.White.copy(alpha = .08f), Color.Black.copy(alpha = .8f))
        assertEquals(Color.White, neutralForeground(surface))
        assertTrue(contrastAgainst(Color.White, surface) >= TextContrast)
        assertEquals(Color.Yellow, readableForeground(Color.Yellow, surface, IconContrast))
        val corrected = readableForeground(Color.DarkGray, surface, IconContrast)
        assertTrue(contrastAgainst(corrected, surface) >= IconContrast)
        assertEquals(Color.White, corrected)
        assertEquals(Color.Black, neutralForeground(effectiveSurfaceColor(surface, Color(0xFFF7F7F7))))
    }

    @Test fun `shared selector delivers dimmed foreground to its content and opens native in black`() {
        val host = GlassMenuHostState()
        val settings = mutableStateOf(lowFilm)
        var colors = Color.Unspecified to Color.Unspecified
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TestTheme(settings.value) {
                val backdrop = rememberCanvasBackdrop { drawRect(Color.White) }
                SideEffect { host.backdrop = backdrop }
                CompositionLocalProvider(LocalGlassMenuHost provides host, LocalSurfaceBackdropColor provides Color.White) {
                    GlassDialogOverlayHost(host)
                    ValueSelectorDialog(onDismiss = {}, title = "Settings", selectedValue = true,
                        values = listOf(true, false), onValueSelected = {}, valueText = { value ->
                            val explicit = colorPalette().text
                            val implicit = LocalContentColor.current
                            SideEffect { colors = explicit to implicit }
                            value.toString()
                        },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(host.dialogs.hasDialogs)
            assertEquals(colors.first, colors.second)
            assertTrue(colors.first.red > DefaultLightColorPalette.text.red)
            settings.value = lowFilm.copy(enabled = false)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(DefaultLightColorPalette.text to DefaultLightColorPalette.text, colors)
        }
    }

    @Test fun `hosted profile style dropdown delivers the dimmed foreground to a descendant`() {
        val host = GlassMenuHostState()
        var colors = Color.Unspecified to Color.Unspecified
        compose.setContent {
            TestTheme(lowFilm) {
                val backdrop = rememberCanvasBackdrop { drawRect(Color.White) }
                SideEffect { host.backdrop = backdrop }
                CompositionLocalProvider(LocalGlassMenuHost provides host, LocalSurfaceBackdropColor provides Color.White) {
                    Box {
                        Box(Modifier.size(48.dp)) {
                            GlassDropdownMenu(true, {}, Color.White, shape) {
                                val explicit = colorPalette().text
                                val implicit = LocalContentColor.current
                                SideEffect { colors = explicit to implicit }
                                Box(Modifier.size(110.dp, 48.dp))
                            }
                        }
                        GlassOverlayHost(host)
                    }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(host.entry != null)
            assertEquals(colors.first, colors.second)
            assertTrue(colors.first.red > DefaultLightColorPalette.text.red)
        }
    }

    @Test fun `bright dark and mixed artwork adapt and the bottom tint participates`() {
        val dark = Color(0xFF303030)
        val light = Color(0xFFF0F0F0)
        assertEquals(Color.White, neutralForeground(playerControlContrast(dark, listOf(dark)).surface))
        assertEquals(Color.Black, neutralForeground(playerControlContrast(light, listOf(light)).surface))
        val mixed = playerControlContrast(dark, listOf(dark, light))
        assertTrue(mixed.scrim.alpha > 0f)
        assertTrue(contrastAgainst(Color.White, effectiveSurfaceColor(light, mixed.scrim)) >= TextContrast)
        val tinted = playerTintedArtworkSample(dark, Color.Transparent, Color.White.copy(alpha = .75f),
            gradientStart = 100f, height = 200f, position = 1f)
        assertEquals(Color.Black, neutralForeground(tinted))
    }

    @Test fun `player contrast adapts a dark palette to pale artwork and its own play plate`() {
        var text = Color.Unspecified
        var play = Color.Unspecified
        var nested = Color.Unspecified
        var scrim = Color.Unspecified
        var accent = Color.Unspecified
        val pale = Color(0xFFF3E8EE)
        val playPlate = Color(0xFF25304A)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance.copy(colorPalette = DefaultDarkColorPalette)) {
                SurfaceForegroundContent(pale) {
                    val textColor = colorPalette().text
                    val playColor = surfaceGlyphColor(surfaceSourcePalette().text, playPlate)
                    val currentScrim = surfaceForegroundScrim()
                    val currentAccent = colorPalette().accent
                    SurfaceForegroundContent(Color(0xFF101010)) {
                        val nestedColor = colorPalette().text
                        SideEffect { nested = nestedColor }
                    }
                    SideEffect { text = textColor; play = playColor; scrim = currentScrim; accent = currentAccent }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(contrastAgainst(text, pale) >= IconContrast)
            assertTrue(contrastAgainst(play, playPlate) >= IconContrast)
            assertEquals(DefaultDarkColorPalette.text, play)
            assertEquals(DefaultDarkColorPalette.text, nested)
            assertEquals(Color.Transparent, scrim)
            assertEquals(DefaultDarkColorPalette.accent, accent)
        }
    }

    @Test fun `all chromatic user colors and their transparency stay exact on any background`() {
        for (ink in listOf(Color(0xFF8659DA), Color(0xFFF08040), Color(0xFF369C91), Color(0xFF2674C8),
            Color(0xFFFFF0F0), Color(0xFFF0FFF8), Color(0xFF0F0000))
            .flatMap { listOf(it, it.copy(alpha = .35f), it.copy(alpha = .6f)) }) {
            for (surface in listOf(Color.White, Color.Black, Color.Red)) {
                assertEquals(ink, readableForeground(ink, surface, TextContrast))
            }
        }
        assertEquals(Color.Black, readableForeground(Color.LightGray, Color.White, TextContrast))
        assertEquals(Color.DarkGray, readableForeground(Color.DarkGray, Color.Black, IconContrast))
        assertEquals(Color.Gray, readableForeground(Color.Gray, Color.White, TextContrast))
        assertEquals(Color.Gray, readableForeground(Color.Gray, Color.Black, TextContrast))
    }

    @Test fun `nested Glass surface restores the original colored palette and glyph`() {
        val violet = Color(0xFFB991FF)
        val source = DefaultDarkColorPalette.copy(text = violet, textSecondary = violet,
            iconButtonPlayer = violet, accent = Color(0xFFFFBF50))
        var outer = Color.Unspecified
        var inner = Color.Unspecified
        var glyph = Color.Unspecified
        var disabled = Color.Unspecified
        var accent = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance.copy(colorPalette = source)) {
                SurfaceForegroundContent(Color.White) {
                    val parentInk = colorPalette().text
                    val plateGlyph = surfaceGlyphColor(surfaceSourcePalette().text, Color.Black)
                    SurfaceForegroundContent(Color.Black) {
                        val childInk = colorPalette().text
                        val disabledInk = colorPalette().textDisabled
                        val accentInk = colorPalette().accent
                        SideEffect { inner = childInk; disabled = disabledInk; accent = accentInk }
                    }
                    SideEffect { outer = parentInk; glyph = plateGlyph }
                }
            }
        }
        compose.runOnIdle {
            assertEquals(violet, outer)
            assertEquals(violet, inner)
            assertEquals(violet, glyph)
            assertEquals(source.textDisabled, disabled)
            assertEquals(source.accent, accent)
        }
    }

    @Test fun `sample arrival and sheet pause keep the same controls composition`() {
        val surface = mutableStateOf<Color?>(null)
        var identity: Any? = null
        compose.setContent {
            TestTheme(lowFilm) {
                SurfaceForegroundContent(surface.value) {
                    val token = remember { Any() }
                    SideEffect { identity = token }
                }
            }
        }
        var original: Any? = null
        compose.runOnIdle { original = identity; surface.value = Color.White }
        compose.runOnIdle { assertTrue(original === identity); surface.value = null }
        compose.runOnIdle { assertTrue(original === identity) }
    }

    @Test fun `common neutral decision applies together across local films in both themes`() {
        val selected = mutableStateOf(DefaultDarkColorPalette)
        run {
            var text = Color.Unspecified
            var colored = Color.Unspecified
            var sibling = Color.Unspecified
            var implicitColored = Color.Unspecified
            compose.setContent {
                CompositionLocalProvider(LocalAppearance provides appearance.copy(colorPalette = selected.value),
                    LocalNeutralForegroundOverride provides Color.Black,
                    LocalNeutralForegroundSurface provides Color.White,
                    LocalContentColor provides Color.Red) {
                    SurfaceForegroundContent(Color.Black) {
                        val ink = colorPalette().text
                        val custom = surfaceGlyphColor(Color.Red)
                        val implicit = LocalContentColor.current
                        SideEffect { implicitColored = implicit }
                        SurfaceForegroundContent(Color.White) {
                            val second = colorPalette().text
                            SideEffect { sibling = second }
                        }
                        SideEffect { text = ink; colored = custom }
                    }
                }
            }
            compose.runOnIdle {
                assertEquals(Color.Black, text)
                assertEquals(Color.Black, sibling)
                assertEquals(Color.Red, colored)
                assertEquals(Color.Red, implicitColored)
                selected.value = DefaultLightColorPalette
            }
            compose.runOnIdle {
                assertEquals(DefaultLightColorPalette.text, text)
                assertEquals(DefaultLightColorPalette.text, sibling)
                assertEquals(Color.Red, colored)
                assertEquals(Color.Red, implicitColored)
            }
        }
    }

    @Test fun `one fade updates neutral siblings together and preserves colored ink`() {
        val surface = mutableStateOf<Color?>(null)
        var first = Color.Unspecified
        var second = Color.Unspecified
        var colored = Color.Unspecified
        var glyph = Color.Unspecified
        var progress = 1f
        var track = Color.Unspecified
        val target = readableForeground(Color.White, Color.White, IconContrast)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val common = rememberNeutralForegroundFade(surface.value, Color.White)
            TestTheme(lowFilm) {
                CompositionLocalProvider(
                    LocalAppearance provides appearance.copy(colorPalette = DefaultDarkColorPalette.copy(text = Color.White)),
                    LocalNeutralForegroundTransition provides common,
                ) {
                    SurfaceForegroundContent(surface.value) {
                        val ink = colorPalette().text
                        val custom = surfaceGlyphColor(Color.Red)
                        val neutralGlyph = surfaceGlyphColor(colorPalette().text)
                        val inactiveTrack = surfaceProgressTrackColor(Color.Magenta, colorPalette().text)
                        val transitionProgress = common.value.progress
                        SideEffect { first = ink; colored = custom; glyph = neutralGlyph; track = inactiveTrack; progress = transitionProgress }
                    }
                    SurfaceForegroundContent(surface.value) {
                        val ink = colorPalette().text
                        SideEffect { second = ink }
                    }
                }
            }
        }
        compose.runOnIdle { surface.value = Color.White }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertEquals(first, second)
            assertEquals(first, glyph)
            assertEquals(first, track)
            assertTrue("Entry fade must be in progress at 80 ms: progress=$progress, ink=$first", progress > 0f && progress < 1f)
            assertTrue(first.red > target.red && first.red < 1f)
            assertEquals(Color.Red, colored)
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle {
            assertEquals(target, first)
            assertEquals(first, second)
            assertEquals(first, track)
            assertEquals(Color.Red, colored)
            surface.value = null
        }
        repeat(2) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertEquals(first, second)
            assertEquals(first, glyph)
            assertEquals(first, track)
            assertTrue("Exit fade must be in progress at 80 ms: progress=$progress, ink=$first", progress > 0f && progress < 1f)
            assertTrue(first.red > target.red && first.red < 1f)
            assertEquals(Color.Red, colored)
        }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle {
            assertEquals(Color.White, first)
            assertEquals(first, second)
            assertEquals(first, track)
            assertEquals(Color.Red, colored)
        }
    }

    @Test fun `one transition preserves readable gray exactly and retargets without a jump`() {
        val original = NeutralForegroundDecision(null, null)
        val pale = NeutralForegroundDecision(Color.White,
            readableForeground(Color.White, Color.White, IconContrast))
        val transition = NeutralForegroundTransition(listOf(WeightedNeutralDecision(original, 1f)), pale, .4f)
        assertEquals(Color.Gray, transition.resolve(Color.Gray))
        assertEquals(Color.Red, transition.resolve(Color.Red))
        val before = transition.resolve(Color.White)
        val retargeted = NeutralForegroundTransition(transition.frozenBlend(), original, 0f)
        val after = retargeted.resolve(Color.White)
        assertEquals(before.red, after.red, .0001f)
        assertEquals(before.green, after.green, .0001f)
        assertEquals(before.blue, after.blue, .0001f)
    }

    @Test fun `text switches fully to black while inactive gray glyph changes only slightly`() {
        assertEquals(Color.Black, readableForeground(Color.White, Color.White, TextContrast))
        assertEquals(Color.Black, readableForeground(Color.Gray, Color.Gray, TextContrast))
        for (original in listOf(Color.LightGray, Color.White.copy(alpha = .4f), Color.Gray.copy(alpha = .3f))) {
            val adjusted = readableGlyphForeground(original, Color.White, enabled = false)
            assertEquals(original.alpha, adjusted.alpha, 0f)
            assertTrue(kotlin.math.abs(original.red - adjusted.red) <= .080001f)
            assertTrue(kotlin.math.abs(original.green - adjusted.green) <= .080001f)
            assertTrue(kotlin.math.abs(original.blue - adjusted.blue) <= .080001f)
            assertTrue(adjusted != Color.Black && adjusted != Color.White)
        }
        assertEquals(Color.Red, readableGlyphForeground(Color.Red, Color.Red, enabled = false))
        val original = NeutralForegroundDecision(null, null)
        val pale = NeutralForegroundDecision(Color.White, Color.Black)
        val halfway = NeutralForegroundTransition(listOf(WeightedNeutralDecision(original, 1f)), pale, .5f)
        assertEquals(Color.Black, halfway.copy(progress = 1f).resolve(Color.White))
        val inactive = halfway.resolve(Color.LightGray, glyph = true, enabled = false)
        assertTrue(kotlin.math.abs(inactive.red - Color.LightGray.red) <= .080001f)
    }

    @Test fun `retargeting light to Glass dark and back shares continuous source based decisions`() {
        val light = NeutralForegroundDecision(Color.White, Color.Black)
        val dark = NeutralForegroundDecision(Color.Black, Color.White)
        val initial = NeutralForegroundTransition(listOf(WeightedNeutralDecision(light, 1f)), dark, .4f)
        val retargeted = NeutralForegroundTransition(initial.frozenBlend(), light, 0f)
        assertEquals(initial.resolve(Color.White).red, retargeted.resolve(Color.White).red, .0001f)
        assertEquals(Color.Black, retargeted.copy(progress = 1f).resolve(Color.White))
        assertEquals(Color.Red, retargeted.copy(progress = 1f).resolve(Color.Red))
    }

    @Composable
    private fun TestTheme(settings: GlassSettings, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAppearance provides appearance,
            LocalGlassPreviewSettings provides settings,
            LocalContentColor provides DefaultLightColorPalette.text,
            content = content,
        )
    }
}
