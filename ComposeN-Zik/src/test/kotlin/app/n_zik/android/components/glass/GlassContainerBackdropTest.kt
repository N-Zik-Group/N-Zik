package app.n_zik.android.components.glass

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassContainerBackdropTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun `custom modal and its child action sample completed player instead of page`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val enabled = context.preferences.getBoolean(glassEnabledKey, false)
        val store = GlassStore(context)
        val previous = store.read()
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        store.setBoolean(glassMenusKey, true)
        store.setAreaMode(GlassArea.Menus, GlassAreaMode.GlassOnly)
        store.setTint(GlassTintSource.None)
        // This fixture isolates scene routing; scrim brightness has dedicated pixel tests.
        store.setFloat(glassMenuDimmingKey, 0f)
        store.setFloat(glassFilmKey, .5f)
        store.setFloat(glassBlurKey, 0f)
        store.setFloat(glassVibrancyKey, 0f)
        store.setFloat(glassRimKey, 0f)
        store.setFloat(glassDepthKey, 0f)
        store.setFloat(glassAmountKey, 0f)
        val playerColor = mutableStateOf(Color.Red)
        val transparent = mutableStateOf(false)
        var modalView: View? = null
        val host = GlassMenuHostState()
        try {
            compose.setContent {
                val page = rememberLayerBackdrop()
                val player = rememberLayerBackdrop()
                val shape = RoundedCornerShape(12.dp)
                SideEffect { host.backdrop = page; host.overlayBackdrop = page }
                CompositionLocalProvider(LocalGlassMenuHost provides host,
                    LocalAppearance provides Appearance(DefaultDarkColorPalette,
                        typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                            CompositionLocalProvider(LocalGlassOverlayBackdrop provides player) {
                                Box(Modifier.fillMaxSize().layerBackdrop(player).background(playerColor.value)) {
                                    CustomModalBottomSheet(true, {}, shape = shape,
                                        containerColor = if (transparent.value) Color.Transparent else Color.Green,
                                        dragHandle = null, contentWindowInsets = { WindowInsets(0) }) {
                                        val view = LocalView.current
                                        SideEffect { modalView = view }
                                        Box(Modifier.fillMaxWidth().height(160.dp)) {
                                            Box(Modifier.size(80.dp, 60.dp).testTag("modal-action")
                                                .glassChipBackground(Color.Transparent, shape,
                                                    GlassSettings(enabled = true, film = 0f, rim = 0f,
                                                        blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)))
                                        }
                                    }
                                }
                            }
                            GlassDialogOverlayHost(host)
                        }
                    }
                }
            }
            fun check(expected: Color) {
                val bounds = compose.onNodeWithTag("modal-action").fetchSemanticsNode().boundsInRoot
                var actual = Color.Unspecified
                compose.runOnIdle {
                    val view = requireNotNull(modalView)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    actual = Color(bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))
                    bitmap.recycle()
                }
                assertEquals(expected.red, actual.red, .04f)
                assertEquals(expected.green, actual.green, .04f)
                assertEquals(expected.blue, actual.blue, .04f)
                compose.runOnIdle { assertEquals(1, host.dialogs.entries.size) }
            }
            check(Color.Green.copy(alpha = .5f).compositeOver(Color.Red))
            compose.runOnIdle { playerColor.value = Color.Yellow }
            check(Color.Green.copy(alpha = .5f).compositeOver(Color.Yellow))
            compose.runOnIdle { transparent.value = true }
            check(Color.Yellow)
        } finally {
            context.preferences.edit().putBoolean(glassEnabledKey, enabled).commit()
            store.setBoolean(glassMenusKey, previous.menus)
            previous.areaModes[GlassArea.Menus]?.let { store.setAreaMode(GlassArea.Menus, it) }
            store.setTint(previous.source)
            store.setFloat(glassMenuDimmingKey, previous.menuDimming)
            store.setFloat(glassFilmKey, previous.film)
            store.setFloat(glassBlurKey, previous.blur)
            store.setFloat(glassVibrancyKey, previous.vibrancy)
            store.setFloat(glassRimKey, previous.rim)
            store.setFloat(glassDepthKey, previous.depth)
            store.setFloat(glassAmountKey, previous.amount)
        }
    }

    @Test fun `nested actions sample changing rendered parent material instead of containing page`() {
        val parentFilm = mutableStateOf(Color.Green)
        val childSettings = GlassSettings(enabled = true, film = 0f, rim = 0f,
            blur = 0f, vibrancy = 0f, depth = 0f, amount = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            val page = rememberCanvasBackdrop { drawRect(Color.Red) }
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape),
                LocalGlassBackdrop provides page) {
                MaterialTheme {
                    GlassMaterialContainerColumn(Modifier.size(180.dp, 160.dp),
                        material = { exported -> Modifier.glassMaterial(
                            childSettings.copy(film = 1f), shape, ownBackdrop = page,
                            filmColor = parentFilm.value, exportedBackdrop = exported,
                            drawShadow = false,
                        ) }) {
                        GlassMaterialContainerColumn(Modifier.background(Color.Yellow.copy(alpha = .5f)),
                            includeParent = true, sectionFilm = Color.Yellow.copy(alpha = .5f)) {
                            Box(Modifier.size(100.dp, 40.dp).testTag("chip")
                                .glassChipBackground(Color.Transparent, shape, childSettings))
                            Box(Modifier.size(100.dp, 40.dp).testTag("dialog")
                                .glassDialogButtonBackground(Color.Transparent, shape, settings = childSettings))
                            PageButton({}, Modifier.size(100.dp, 40.dp).testTag("page"),
                                shape = shape, settings = childSettings, elevation = null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)) { }
                        }
                    }
                }
            }
        }
        fun assertParent(expected: Color) {
            listOf("chip", "dialog", "page").forEach { tag ->
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, pixels.height / 2]
                assertEquals("$tag actual=$actual", expected.red, actual.red, .03f)
                assertEquals("$tag green", expected.green, actual.green, .03f)
                assertEquals("$tag blue", expected.blue, actual.blue, .03f)
            }
        }
        assertParent(Color.Yellow.copy(alpha = .5f).compositeOver(Color.Green))
        compose.runOnIdle { parentFilm.value = Color.Blue }
        assertParent(Color.Yellow.copy(alpha = .5f).compositeOver(Color.Blue))
    }
}
