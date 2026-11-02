package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.LocalGlassContainerBackdrop
import app.n_zik.android.components.glass.LocalGlassMaterialSource
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.navigation.header.LocalHeaderProfileFace
import app.n_zik.android.components.navigation.header.initialHeaderProfileFace
import app.n_zik.android.surfaceColor
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersistentSideHeaderMaterialSourceTest {
    @get:Rule val compose = createComposeRule()

    @Before fun initializeDependencies() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val main = mockk<MainApplication>()
        every { main.applicationContext } returns application
        Dependencies.init(main)
    }

    @Test fun `persistent rail header samples its real Glass and native panel across live updates`() {
        val scene = mutableStateOf(Color.Blue)
        val film = mutableStateOf(.5f)
        val native = mutableStateOf(false)
        val panel = mutableStateOf(Color.Red)
        var searchLabel = ""
        var panelFilm = Color.Unspecified
        compose.setContent {
            val shape = RoundedCornerShape(0.dp)
            val settings = GlassSettings(nav = !native.value, film = film.value, blur = 0f,
                rim = 0f, tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
            val raw = rememberCanvasBackdrop { drawRect(scene.value) }
            val controller = rememberNavController()
            searchLabel = stringResource(R.string.search)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(DefaultDarkColorPalette.copy(
                    background1 = panel.value, text = Color.Transparent), Typography.fallback, shape, shape, shape),
                LocalGlassPreviewSettings provides settings,
                LocalGlassBackdrop provides raw,
                LocalGlassContainerBackdrop provides raw,
                LocalGlassMaterialSource provides raw,
                LocalHeaderProfileFace provides initialHeaderProfileFace("Test"),
            ) {
                panelFilm = surfaceColor(panel.value)
                Box(Modifier.size(200.dp, 700.dp).testTag("rail")) {
                    Box(Modifier.fillMaxSize().background(scene.value))
                    PersistentSideHeader(controller, NavigationBarPosition.Left)
                }
            }
        }
        for (isNative in listOf(false, true)) for (color in listOf(Color.Blue, Color.Green)) {
            for (opacity in listOf(.5f, 0f)) {
                compose.runOnIdle { native.value = isNative; scene.value = color; film.value = opacity }
                val parent = if (isNative) panelFilm else panelFilm.copy(alpha = opacity).compositeOver(color)
                assertSearchPixel(searchLabel, panelFilm.copy(alpha = opacity).compositeOver(parent))
            }
        }
        compose.runOnIdle { panel.value = Color.Cyan }
        compose.waitForIdle()
        assertSearchPixel(searchLabel, panelFilm)
    }

    private fun assertSearchPixel(label: String, expected: Color) {
        val search = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot.center
        val pixels = compose.onNodeWithTag("rail").captureToImage().toPixelMap()
        val actual = pixels[search.x.toInt(), search.y.toInt()]
        assertEquals("search red", expected.red, actual.red, .03f)
        assertEquals("search green", expected.green, actual.green, .03f)
        assertEquals("search blue", expected.blue, actual.blue, .03f)
    }
}
