package app.n_zik.android.components.player

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassOverlayScene
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.glassOverlayCanvasViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerCoverOverlayViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `measuring adjacent information plate never collapses either embedded overlay canvas`() {
        val glass = mutableStateOf(true)
        val visualizer = mutableStateOf(false)
        lateinit var geometry: PlayerTransportGeometry
        compose.setContent {
            val settings = GlassSettings(enabled = glass.value)
            val shape = RoundedCornerShape(16.dp)
            geometry = remember { PlayerTransportGeometry() }
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalPlayerTransportGeometry provides geometry,
            ) {
                val canvas: @Composable () -> Unit = {
                    GlassOverlayScene(
                        background = Color.Black,
                        scope = if (visualizer.value) GlassScope.Visualizer else GlassScope.Lyrics,
                        modifier = Modifier.fillMaxSize()
                            .glassOverlayCanvasViewport(settings, showThumbnail = LocalPlayerCoverOverlay.current)
                            .testTag("measured-cover-scene"),
                        scene = {
                            AndroidView(factory = { View(it) },
                                modifier = Modifier.fillMaxSize().testTag("measured-android-canvas"))
                        },
                    ) {}
                }
                Box(Modifier.size(600.dp, 340.dp).playerTransportGeometryHost()) {
                    PlayerLandscapeColumns(
                        contentInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 0),
                        artwork = {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                                PlayerCoverOverlayHost(
                                    visibility = PlayerCoverOverlayVisibility(!visualizer.value, visualizer.value),
                                    modifier = Modifier.size(180.dp).testTag("measured-cover"),
                                    artwork = { Box(Modifier.fillMaxSize()) },
                                    lyrics = canvas, visualizer = canvas,
                                )
                            }
                        },
                        controls = {
                            PlayerInformationGlassBlock(settings = settings) {
                                PlayerInformationGlassRegion("info") { Box(Modifier.size(240.dp, 60.dp)) }
                            }
                        },
                    )
                }
            }
        }
        for (enabled in listOf(true, false, true)) for (visual in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled; visualizer.value = visual }
            compose.waitForIdle()
            if (enabled) compose.runOnIdle {
                assertTrue("The real information plate must publish its global top", geometry.informationSurfaceTop != null)
            }
            repeat(6) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val card = compose.onNodeWithTag("measured-cover").fetchSemanticsNode().boundsInRoot
                val scene = compose.onNodeWithTag("measured-cover-scene").fetchSemanticsNode().boundsInRoot
                val androidCanvas = compose.onNodeWithTag("measured-android-canvas").fetchSemanticsNode().boundsInRoot
                assertTrue("Measured canvas must remain visible after geometry feedback", scene.height > 0f)
                assertEquals(card, scene)
                assertEquals(card, androidCanvas)
            }
        }
    }
}
