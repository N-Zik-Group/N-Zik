package app.n_zik.android.components.ui.screens.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.navigation.header.UnifiedTopNavigationGlass
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedHomeHeaderGlassTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Top collapse removes initial header and mini clearance along with the toolbar`() {
        // The scroll reserve stays fixed, whereas navigation has already moved up.
        assertEquals(340, sharedTopHomeGlassHeight(152, 152, 188, 1f))
        assertEquals(214, sharedTopHomeGlassHeight(88, 152, 188, .5f))
        assertEquals(88, sharedTopHomeGlassHeight(88, 152, 188, 0f))
        assertEquals(88, sharedTopHomeGlassHeight(88, 232, 188, 0f))
    }

    @Test fun `Top shares one live two tone pane without covering toolbar and keeps navigation when collapsed`() {
        val navTop = mutableIntStateOf(88)
        val toolbarVisible = mutableFloatStateOf(1f)
        val source = mutableStateOf(Color.Blue)
        compose.setContent {
            val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                val shared = requireNotNull(rememberSharedHomeHeaderGlass(true, "home", topNavigation = true))
                val settings = GlassSettings(film = .4f, blur = 0f, rim = 0f, tintStrength = 0f)
                SideEffect { shared.attach(this@SharedHomeHeaderGlassTest) }
                CompositionLocalProvider(LocalSharedHomeHeaderGlass provides shared) {
                    Box(Modifier.size(200.dp, 300.dp).testTag("top-scene")) {
                        Box(Modifier.fillMaxSize().sharedHomeGlassContent().background(source.value))
                        SharedHomeHeaderPane(
                            shared, settings, Color.Green, shape,
                            heightPx = { sharedTopHomeGlassHeight(shared.navigationBottomPx, 152, 88, toolbarVisible.floatValue) },
                            opacity = { 1f }, upperFilmColor = Color.Red,
                            upperHeightPx = { shared.navigationBottomPx },
                        )
                        Box(Modifier.offset(80.dp, 180.dp).size(20.dp).background(Color.Yellow))
                        // Root Top navigation is painted after page controls, like AppHeaderOverlay.
                        Box(Modifier.offset { IntOffset(0, navTop.intValue) }) {
                            UnifiedTopNavigationGlass(settings, Color.Red, shared.list, { navTop.intValue }) {
                                Box(Modifier.fillMaxWidth().height(64.dp))
                            }
                        }
                    }
                }
            }
        }
        fun pixel(x: Int, y: Int) = compose.onNodeWithTag("top-scene").captureToImage().toPixelMap()[x, y]
        for (y in listOf(30, 120, 150)) {
            assertEquals(.4f, pixel(40, y).red, .03f)
            assertEquals(.6f, pixel(40, y).blue, .03f)
        }
        for (y in listOf(154, 210)) {
            assertEquals(.4f, pixel(40, y).green, .03f)
            assertEquals(.6f, pixel(40, y).blue, .03f)
        }
        assertEquals(Color.Yellow, pixel(90, 190))
        assertEquals(Color.Blue, pixel(1, 238))
        compose.runOnIdle { navTop.intValue = 24; toolbarVisible.floatValue = 0f }
        assertEquals(.4f, pixel(40, 60).red, .03f)
        assertEquals(.6f, pixel(40, 60).blue, .03f)
        assertEquals(Color.Blue, pixel(40, 100))
        assertEquals(Color.Blue, pixel(1, 86))
        compose.runOnIdle { source.value = Color.Cyan }
        assertEquals(.6f, pixel(40, 60).green, .03f)
    }

    @Test fun `library homes share the pane with both Top areas enabled`() {
        for (position in NavigationBarPosition.entries) {
            for (tab in listOf("songs", "artists", "albums", "playlists", "quickpicks", "settings", null)) {
                val expected = tab in listOf("songs", "artists", "albums", "playlists")
                assertEquals(expected, usesSharedHomeHeaderGlass(true, tab, position, GlassSettings()))
                assertFalse(usesSharedHomeHeaderGlass(false, tab, position, GlassSettings()))
                assertFalse(usesSharedHomeHeaderGlass(true, tab, position, GlassSettings(enabled = false)))
                assertFalse(usesSharedHomeHeaderGlass(true, tab, position, GlassSettings(header = false)))
                if (position == NavigationBarPosition.Top)
                    assertFalse(usesSharedHomeHeaderGlass(true, tab, position, GlassSettings(nav = false)))
            }
        }
    }

    @Test fun `side pane and overflowing controls stop before either rail without double padding`() {
        val onRight = mutableStateOf(true)
        val height = mutableIntStateOf(0)
        val offset = mutableFloatStateOf(0f)
        compose.setContent {
            val shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                val shared = requireNotNull(rememberSharedHomeHeaderGlass(true, "home"))
                CompositionLocalProvider(
                    LocalSharedHomeHeaderGlass provides shared,
                    LocalSideRailContentInsets provides WindowInsets(
                        left = if (onRight.value) 0.dp else 50.dp,
                        right = if (onRight.value) 50.dp else 0.dp,
                    ),
                ) {
                    Box(Modifier.size(240.dp, 200.dp).testTag("side-scene")) {
                        Box(Modifier.fillMaxSize().sharedHomeGlassContent().background(Color.Blue))
                        HomeCollapsingHeader(offset, height, { 24.dp }, backgroundColor = Color.Red, shape = shape) {
                            Box(Modifier.fillMaxWidth().height(100.dp).drawBehind {
                                // Simulate descendants painting past their measured viewport.
                                drawRect(Color.Yellow, Offset(-80f, 0f), Size(size.width + 160f, size.height))
                            })
                        }
                    }
                }
            }
        }
        fun pixel(x: Int, y: Int) = compose.onNodeWithTag("side-scene").captureToImage().toPixelMap()[x, y]
        assertEquals(Color.Yellow, pixel(175, 60))
        for (x in listOf(183, 189, 210, 239)) assertEquals(Color.Blue, pixel(x, 60))
        assertEquals(Color.Blue, pixel(181, 123))
        compose.runOnIdle { onRight.value = false }
        assertEquals(Color.Yellow, pixel(65, 60))
        for (x in listOf(0, 30, 50, 57)) assertEquals(Color.Blue, pixel(x, 60))
        assertEquals(Color.Blue, pixel(58, 123))
        compose.runOnIdle { offset.floatValue = -100f }
        assertEquals(Color.Blue, pixel(120, 60))
    }

    @Test fun `one live material covers header and toolbar and follows the outer shape`() {
        val source = mutableStateOf(Color.Blue)
        val offset = mutableFloatStateOf(0f)
        val shape = mutableStateOf<Shape>(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
                thumbnailShape = shape.value, uiRoundnessShape = shape.value, artistThumbnailShape = shape.value,
            )) {
                val shared = requireNotNull(rememberSharedHomeHeaderGlass(true, "songs"))
                CompositionLocalProvider(LocalSharedHomeHeaderGlass provides shared) {
                    Box(Modifier.size(200.dp, 300.dp).testTag("scene")) {
                        Box(Modifier.fillMaxSize().sharedHomeGlassContent().background(source.value))
                        SharedHomeHeaderPane(
                            shared, GlassSettings(film = .4f, blur = 0f, rim = 0f, tintStrength = 0f),
                            Color.Red, shape.value,
                            heightPx = { sharedHomeGlassHeight(88, 112, offset.floatValue) },
                            opacity = { 1f },
                        )
                        // Foreground must never be recaptured by the shared material.
                        Box(Modifier.offset(80.dp, 100.dp).size(20.dp).background(Color.Yellow))
                        Box(Modifier.offset(80.dp, 100.dp).size(20.dp).testTag("sample")
                            .drawPlainBackdrop(shared.surface, { RectangleShape }, effects = {}))
                    }
                }
            }
        }
        fun pixel(x: Int, y: Int) = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[x, y]
        fun assertUniform() {
            for (y in listOf(12, 60, 120, 180)) {
                assertEquals(.4f, pixel(40, y).red, .03f)
            }
            val sample = compose.onNodeWithTag("sample").captureToImage().toPixelMap()[10, 10]
            assertEquals(.4f, sample.red, .03f)
            assertEquals(.6f, sample.blue, .03f)
        }
        assertUniform()
        assertEquals(Color.Blue, pixel(1, 198))
        assertEquals(Color.Blue, pixel(40, 220))
        compose.runOnIdle { source.value = Color.Green; shape.value = RectangleShape }
        assertEquals(.4f, pixel(1, 198).red, .03f)
        assertEquals(.6f, pixel(40, 120).green, .03f)
        compose.runOnIdle { offset.floatValue = -56f }
        assertEquals(Color.Green, pixel(40, 180))
        assertTrue(pixel(40, 120).red > .35f)
    }
}
