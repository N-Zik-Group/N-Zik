package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassMenuHostState
import app.n_zik.android.components.glass.GlassNavigationContent
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassMiniPlayerBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassMiniPlayerNavigationBackdropTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `sibling mini samples moving navigation but never records its own film or foreground`() {
        val host = GlassMenuHostState()
        val navColor = mutableStateOf(Color.Green)
        val navY = mutableStateOf(40)
        val navigationY = mutableStateOf(10)
        val enabled = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        val film = mutableStateOf(.5f)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback,
                RectangleShape, RectangleShape, RectangleShape,
            )) {
                val page = rememberLayerBackdrop()
                Box(Modifier.size(240.dp, 200.dp).background(Color.Black)) {
                    if (mounted.value) Box(Modifier.offset { IntOffset(20, navigationY.value) }.size(200.dp, 160.dp)) {
                        GlassNavigationContent(host, enabled.value) {
                            AppHeaderOverlay(
                                header = { Box(Modifier.fillMaxWidth().height(20.dp).background(Color.Yellow)) },
                                navigation = {
                                    Box(Modifier.offset { IntOffset(0, navY.value) }
                                        .fillMaxWidth().height(40.dp).drawBehind { drawRect(navColor.value) })
                                },
                            ) {
                                Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                            }
                        }
                    }
                    Box(Modifier.offset(50.dp, 60.dp).size(100.dp, 60.dp).testTag("mini")
                        .glassMiniPlayerBackground(
                            GlassSettings(enabled = enabled.value, film = film.value, blur = 0f,
                                vibrancy = 0f, depth = 0f, amount = 0f, rim = 0f),
                            RectangleShape, host.miniPlayerBackdrop ?: page, Color.Magenta,
                        )) {
                        Box(Modifier.offset(40.dp, 5.dp).size(12.dp).background(Color.White))
                    }
                }
            }
        }
        fun assertPixels(top: Color, bottom: Color) {
            val pixels = compose.onNodeWithTag("mini").captureToImage().toPixelMap()
            fun check(expected: Color, actual: Color) {
                assertEquals(expected.red, actual.red, .03f)
                assertEquals(expected.green, actual.green, .03f)
                assertEquals(expected.blue, actual.blue, .03f)
            }
            check(top, pixels[20, 10])
            check(bottom, pixels[20, 50])
            check(Color.White, pixels[45, 10])
        }
        fun tinted(color: Color) = Color.Magenta.copy(alpha = film.value).compositeOver(color)
        assertPixels(tinted(Color.Green), tinted(Color.Blue))
        compose.runOnIdle { assertNotNull(host.miniPlayerBackdrop); navColor.value = Color.Red }
        assertPixels(tinted(Color.Red), tinted(Color.Blue))
        compose.runOnIdle { navY.value = 80 }
        assertPixels(tinted(Color.Blue), tinted(Color.Red))
        compose.runOnIdle { navigationY.value = 25 }
        assertPixels(tinted(Color.Blue), tinted(Color.Red))
        // Repeated consumer-only changes cannot accumulate its previous film in the source.
        for (opacity in listOf(.25f, .75f, 0f, .5f)) {
            compose.runOnIdle { film.value = opacity }
            assertPixels(tinted(Color.Blue), tinted(Color.Red))
        }
        compose.runOnIdle { enabled.value = false }
        assertPixels(Color.Magenta, Color.Magenta)
        compose.runOnIdle { assertNull(host.miniPlayerBackdrop); enabled.value = true }
        assertPixels(tinted(Color.Blue), tinted(Color.Red))
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { assertNull(host.miniPlayerBackdrop) }
    }
}
