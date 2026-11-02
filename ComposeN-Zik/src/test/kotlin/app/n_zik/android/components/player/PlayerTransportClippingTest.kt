package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerInfoShowIconsKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w400dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerTransportClippingTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.After fun resetPreferences() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit().clear().commit()
    }

    @Test fun `native constrained carousel preserves transport pixels above optional next track`() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit().clear()
            .putBoolean(glassEnabledKey, false).putBoolean(playerInfoShowIconsKey, false).commit()
        val nextTrack = mutableStateOf(false)
        val geometry = PlayerTransportGeometry()
        val information = PlayerInformationBounds()
        var transport = Rect.Zero
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerTransportGeometry provides geometry,
                LocalPlayerInformationBounds provides information) {
                PlayerGlassBudgetLayout(
                    enabled = false, expandedCarousel = true,
                    modifier = Modifier.requiredSize(300.dp, 500.dp).background(Color.White)
                        .playerTransportGeometryHost().testTag("native.viewport"),
                    header = { Box(Modifier.size(300.dp, 40.dp)) },
                    artwork = { Box(Modifier.size(300.dp).testTag("native.cover")) },
                    information = {
                        Column {
                            Box(Modifier.playerInfoAwareContentSize(false, GlassSettings(enabled = false))
                                .testTag("native.controls")
                                .onGloballyPositioned { information.host = it; information.update() }) {
                                Column {
                                    PlayerInformationGlassRegion("info") { Box(Modifier.size(300.dp, 30.dp)) }
                                    PlayerInformationGlassRegion("timeline") { Box(Modifier.size(300.dp, 30.dp)) }
                                    PlayerTransportCenter {
                                        Box(Modifier.size(80.dp).background(Color.Magenta).onGloballyPositioned {
                                            val origin = it.positionInRoot()
                                            transport = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                                        })
                                    }
                                }
                            }
                            Spacer(Modifier.size(300.dp, 60.dp))
                            Column(Modifier.playerActionPanelBounds().testTag("native.actions")) {
                                if (nextTrack.value) Box(Modifier.size(300.dp, 40.dp).background(Color.Gray))
                                Box(Modifier.size(300.dp, 56.dp).background(Color.DarkGray)
                                    .onGloballyPositioned { geometry.toolbar = geometry.bounds(it) })
                            }
                        }
                    })
            }
        } }
        for (next in listOf(false, true, false)) {
            compose.runOnIdle { nextTrack.value = next }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("native.viewport").fetchSemanticsNode().boundsInRoot
            val cover = compose.onNodeWithTag("native.cover").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("native.controls").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("native.actions").fetchSemanticsNode().boundsInRoot
            val density = compose.density.density
            assertEquals((if (next) 164f else 204f) * density, cover.height, 1f)
            assertEquals(140f * density, controls.height, 1f)
            assertEquals(80f * density, transport.height, 1f)
            assertTrue(cover.bottom <= controls.top + 1f)
            assertTrue(transport.top >= controls.top + 60f * density - 1f)
            assertTrue("Native transport must clear the whole next-track panel", transport.bottom <= actions.top + 1f)
            assertEquals(viewport.bottom, actions.bottom, 1f)
            assertTrue("Fixture must exercise translated content outside its original controls slot",
                transport.bottom > controls.bottom + 1f)
            val pixels = compose.onNodeWithTag("native.viewport").captureToImage().toPixelMap()
            val x = (transport.center.x - viewport.left).toInt()
            val y = (transport.bottom - viewport.top - 5f * density).toInt()
            assertEquals("Native transport stays painted below its original slot with next=$next",
                Color.Magenta, pixels[x, y])
        }
    }

    @Test fun `translated transport remains fully painted with Essential and hidden Modern icons and next track`() {
        ApplicationProvider.getApplicationContext<Application>().preferences.edit().clear()
            .putBoolean(glassEnabledKey, true).putBoolean(playerInfoShowIconsKey, false).commit()
        val nextTrack = mutableStateOf(true)
        val modern = mutableStateOf(false)
        val geometry = PlayerTransportGeometry()
        var transport = Rect.Zero
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerTransportGeometry provides geometry) {
                Box(Modifier.requiredSize(300.dp, 300.dp).background(Color.White)
                    .playerTransportGeometryHost().testTag("viewport")) {
                    Column(Modifier.fillMaxSize()) {
                        PlayerInformationGlassBlock(landscape = false) {
                            Box(Modifier.playerInfoAwareContentSize(modern.value, GlassSettings(enabled = true))
                                .testTag("translated.controls")) {
                                Column {
                                    PlayerInformationGlassRegion("info") { Box(Modifier.size(300.dp, 30.dp)) }
                                    PlayerInformationGlassRegion("timeline") { Box(Modifier.size(300.dp, 30.dp)) }
                                    PlayerTransportCenter {
                                        Box(Modifier.size(80.dp).background(Color.Magenta).onGloballyPositioned {
                                            val origin = it.positionInRoot()
                                            transport = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                                        })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Column(Modifier.playerActionPanelBounds()) {
                            if (nextTrack.value) Box(Modifier.size(300.dp, 40.dp).background(Color.Gray))
                            Box(Modifier.size(300.dp, 56.dp).background(Color.DarkGray))
                        }
                    }
                }
            }
        } }
        for (isModern in listOf(false, true)) for (next in listOf(false, true)) {
            compose.runOnIdle { modern.value = isModern; nextTrack.value = next }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val density = compose.density.density
            val bottom = geometry.actionPanel?.top ?: error("Missing action panel")
            val plate = compose.onNodeWithTag("player.informationBlock.surface", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals((plate.bottom - viewport.top + bottom - transport.height) / 2f,
                transport.top - viewport.top, 1f)
            val controls = compose.onNodeWithTag("translated.controls").fetchSemanticsNode().boundsInRoot
            assertTrue("Fixture must move transport below its original clipping boundary",
                transport.bottom > controls.bottom + 1f)
            val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            val x = (transport.center.x - viewport.left).toInt()
            val y = (transport.bottom - viewport.top - 5f * density).toInt()
            assertEquals("Lower transport stays visible: modern=$isModern next=$next",
                Color.Magenta, pixels[x, y])
        }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
