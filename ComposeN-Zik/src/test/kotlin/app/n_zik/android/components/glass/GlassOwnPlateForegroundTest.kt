package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.SettingsCardSurface
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.ui.styling.applyPitchBlack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassOwnPlateForegroundTest {
    @get:Rule val compose = createComposeRule()

    private val shape = RoundedCornerShape(0.dp)
    private val palette = DefaultDarkColorPalette.applyPitchBlack.copy(
        textSecondary = Color(0xFFFFCC88), textDisabled = Color.White.copy(alpha = .3f))
    private val whiteMaterial = GlassSettings(source = GlassTintSource.Custom,
        customColor = Color.White.toArgb(), tintStrength = 1f, film = 1f,
        quality = GlassQuality.Performance, rim = 0f, blur = 0f)

    @Composable
    private fun DarkHost(content: @Composable () -> Unit) {
        val decision = NeutralForegroundDecision(Color.Black, Color.White)
        CompositionLocalProvider(
            LocalAppearance provides Appearance(palette, Typography.fallback, shape, shape, shape),
            LocalContentColor provides Color.White,
            LocalNeutralForegroundTransition provides rememberUpdatedState(NeutralForegroundTransition(
                listOf(WeightedNeutralDecision(decision, 1f)), decision, 1f)),
            LocalNeutralForegroundOverride provides Color.White,
            LocalNeutralForegroundSurface provides Color.Black,
        ) { content() }
    }

    @Test fun `explicit white film overrides dark theme and inherited player ink without changing semantic roles`() {
        var actual = palette
        var inherited = Color.Unspecified
        var outside = Color.Unspecified
        var surface = Color.Unspecified
        compose.setContent {
            DarkHost {
                val film = glassFilmColor(whiteMaterial, Color.Black)
                val plate = effectiveSurfaceColor(Color.Black, film)
                GlassArtworkContent(whiteMaterial, surface = plate) {
                    val current = colorPalette()
                    val content = LocalContentColor.current
                    SideEffect { actual = current; inherited = content; surface = plate }
                }
                val current = colorPalette().text
                SideEffect { outside = current }
            }
        }
        compose.runOnIdle {
            assertEquals(Color.White, surface)
            assertEquals(Color.Black, actual.text)
            assertEquals(Color.Black, inherited)
            assertEquals(palette.textSecondary, actual.textSecondary)
            assertEquals(palette.textDisabled, actual.textDisabled)
            assertEquals(palette.text, outside)
        }
    }

    @Test fun `settings card uses its rendered film in both content branches and retains native ink when disabled`() {
        val settings = mutableStateOf(whiteMaterial)
        val foregroundRim = mutableStateOf(false)
        var ink = Color.Unspecified
        compose.setContent {
            DarkHost {
                SettingsCardSurface(Modifier.size(96.dp).testTag("card"), shape,
                    containerColor = Color.Black, backgroundColor = Color.Black,
                    shadowColor = Color.Transparent, settings = settings.value,
                    foregroundRim = foregroundRim.value) {
                    Text("Title", color = colorPalette().text,
                        onTextLayout = { ink = it.layoutInput.style.color })
                }
            }
        }
        fun assertCard(expectedFill: Color, expectedInk: Color) {
            val pixels = compose.onNodeWithTag("card").captureToImage().toPixelMap()
            val fill = pixels[pixels.width / 2, pixels.height - 4]
            assertEquals(expectedFill, fill)
            assertEquals(expectedInk, ink)
            assertTrue(contrastAgainst(ink, fill) >= TextContrast)
        }
        assertCard(Color.White, Color.Black)
        compose.runOnIdle { foregroundRim.value = true }
        assertCard(Color.White, Color.Black)
        compose.runOnIdle {
            foregroundRim.value = false
            settings.value = whiteMaterial.copy(actionButtons = false,
                areaModes = mapOf(GlassArea.Switches to GlassAreaMode.Disabled))
        }
        assertCard(Color.White, Color.Black)
        compose.runOnIdle { settings.value = settings.value.copy(settingsCards = false) }
        assertCard(Color.Black, palette.text)
        compose.runOnIdle { settings.value = whiteMaterial.copy(enabled = false) }
        assertCard(Color.Black, palette.text)
    }

    @Test fun `highlighted row uses white generic film and restores its native foreground when material is disabled`() {
        val settings = mutableStateOf(whiteMaterial)
        var ink = Color.Unspecified
        compose.setContent {
            DarkHost {
                GlassSongRowSurface(Color.Black, Color.DarkGray, shape, settings.value) { surface ->
                    Box(Modifier.size(96.dp).testTag("row").then(surface)) {
                        Text("Song", color = colorPalette().text,
                            onTextLayout = { ink = it.layoutInput.style.color })
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("row").captureToImage().toPixelMap()
        assertEquals(Color.White, pixels[pixels.width / 2, pixels.height - 4])
        assertEquals(Color.Black, ink)
        compose.runOnIdle { settings.value = settings.value.copy(nowPlaying = false) }
        compose.runOnIdle { assertEquals(palette.text, ink) }
        compose.runOnIdle { settings.value = whiteMaterial.copy(enabled = false) }
        compose.runOnIdle { assertEquals(palette.text, ink) }
    }
}
