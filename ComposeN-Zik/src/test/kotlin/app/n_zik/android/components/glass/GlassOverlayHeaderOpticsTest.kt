package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.theme.appearanceBackground
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises native blur; API 33 refraction AGSL requires device coverage.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassOverlayHeaderOpticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `expanded menu blurs header and body from the same completed capture`() {
        val host = GlassMenuHostState()
        val includeHeader = mutableStateOf(true)
        val includeBackground = mutableStateOf(true)
        val shape = RoundedCornerShape(12.dp)
        compose.setContent {
            val page = rememberLayerBackdrop()
            val completed = rememberLayerBackdrop()
            val captureHeader = includeHeader.value
            SideEffect {
                host.backdrop = page
                host.overlayBackdrop = if (captureHeader) completed else null
            }
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(240.dp, 320.dp).testTag("scene")) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().layerBackdrop(completed,
                            frozen = { !host.hasOverlays })
                            .then(if (includeBackground.value) Modifier.appearanceBackground() else Modifier),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0),
                        topBar = {
                            Box(Modifier.fillMaxWidth().height(160.dp).drawBehind {
                                // Glass AppHeader leaves gaps transparent around its controls.
                                val controlsHeight = 104.dp.toPx()
                                drawRect(Color.Red, size = Size(size.width, controlsHeight))
                                drawRect(Color.Yellow, Offset(size.width / 2, 0f), Size(size.width / 2, controlsHeight))
                            })
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().layerBackdrop(page).padding(padding).drawBehind {
                            drawRect(Color.Blue)
                            drawRect(Color.Cyan, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
                        })
                    }
                    // Missing capture must reveal green, never the real source underneath.
                    Box(Modifier.fillMaxSize().background(Color.Green))
                    GlassOverlayHost(host)
                }
            }
        }
        compose.runOnIdle {
            with(compose.density) {
                host.show(GlassMenuEntry(
                    id = "header-crossing-menu", anchor = Rect(40.dp.toPx(), 0f, 80.dp.toPx(), 32.dp.toPx()),
                    settings = GlassSettings(enabled = true, blur = 12f, film = 0f, rim = 0f, menuDimming = 0f,
                        vibrancy = 0f, depth = 0f, amount = 0f),
                    shape = shape, onDismiss = {},
                    content = { Box(Modifier.size(160.dp, 180.dp)) },
                ))
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        fun sample(y: Int): Color {
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            return with(compose.density) { pixels[120.dp.roundToPx(), y.dp.roundToPx()] }
        }
        val header = sample(88)
        val body = sample(180)
        assertTrue("header must be sampled and blurred across its red/yellow boundary: $header",
            header.red > .8f && header.blue < .1f && header.green in .1f.. .9f)
        assertTrue("body must be sampled and blurred across its blue/cyan boundary: $body",
            body.blue > .8f && body.red < .1f && body.green in .1f.. .9f)
        val headerGap = sample(132)
        val background = DefaultDarkColorPalette.background0
        assertTrue("transparent header gaps must capture the appearance background: $headerGap",
            kotlin.math.abs(headerGap.red - background.red) < .1f &&
                kotlin.math.abs(headerGap.green - background.green) < .1f &&
                kotlin.math.abs(headerGap.blue - background.blue) < .1f)
        compose.runOnIdle { includeBackground.value = false }
        val missingBackground = sample(132)
        assertTrue("without the background the transparent header leaks the green underlay: $missingBackground",
            kotlin.math.abs(missingBackground.green - 1f) < .03f && missingBackground.red < .1f && missingBackground.blue < .1f)
        compose.runOnIdle { includeHeader.value = false }
        val missingHeader = sample(88)
        assertTrue("page-only source must fail the header sample negative control: $missingHeader",
            kotlin.math.abs(missingHeader.green - 1f) < .03f && missingHeader.red < .1f && missingHeader.blue < .1f)
    }
}
