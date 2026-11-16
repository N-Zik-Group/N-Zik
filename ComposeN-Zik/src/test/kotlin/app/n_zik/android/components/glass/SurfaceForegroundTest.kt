package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(16.dp)
    private val appearance = Appearance(DefaultLightColorPalette, Typography.fallback, shape, shape, shape)
    private val lowFilm = GlassSettings(enabled = true, film = .08f, dialogDimming = .8f, menuDimming = .8f,
        optimizationEnabled = false)

    @Test fun `film is evaluated after the scrim and glyphs keep readable accents`() {
        val surface = effectiveSurfaceColor(Color.White, Color.White.copy(alpha = .08f), Color.Black.copy(alpha = .8f))
        assertEquals(Color.White, neutralForeground(surface))
        assertTrue(contrastAgainst(Color.White, surface) >= TextContrast)
        assertEquals(Color.Yellow, readableForeground(Color.Yellow, surface, IconContrast))
        assertEquals(Color.White, readableForeground(Color.DarkGray, surface, IconContrast))
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
            assertEquals(Color.White to Color.White, colors)
            settings.value = lowFilm.copy(enabled = false)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Color.Black to Color.Black, colors)
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
            assertEquals(Color.White to Color.White, colors)
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

    @Composable
    private fun TestTheme(settings: GlassSettings, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAppearance provides appearance,
            LocalGlassPreviewSettings provides settings,
            LocalContentColor provides Color.Red,
            content = content,
        )
    }
}
