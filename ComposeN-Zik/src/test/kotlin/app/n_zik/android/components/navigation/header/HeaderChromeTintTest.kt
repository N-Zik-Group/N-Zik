package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassActionGroup
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassVerticalActionGroup
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.glassMaterial
import app.n_zik.android.surfaceColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderChromeTintTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerControlsFollowTheNavigationFilmWhenTheThemeChanges() {
        val color = mutableStateOf(Color(0xFF682F42))
        val settings = GlassSettings(enabled = true, quality = GlassQuality.Performance,
            film = 1f, rim = 0f, tintStrength = 0f)
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    colorPalette = DefaultDarkColorPalette.copy(background1 = color.value),
                    typography = Typography.fallback, thumbnailShape = shape,
                    uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
                LocalGlassPreviewSettings provides settings,
            ) {
                val film = surfaceColor(color.value)
                Column {
                    Box(Modifier.size(88.dp, 40.dp).testTag("nav").glassMaterial(settings, shape, filmColor = film))
                    HeaderIcon(R.drawable.chevron_back, modifier = Modifier.testTag("back")) {}
                    GlassActionGroup(settings, GlassArea.Header, Modifier.testTag("horizontal"), filmColor = film) {
                        repeat(2) { GlassActionGroupItem({}) {} }
                    }
                    GlassVerticalActionGroup(settings, GlassArea.Header, Modifier.testTag("vertical")) {
                        repeat(2) { GlassActionGroupItem({}) {} }
                    }
                }
            }
        }
        fun assertFilm() {
            val expected = compose.onNodeWithTag("nav").captureToImage().toPixelMap()[8, 20]
            for (tag in listOf("back", "horizontal", "vertical")) {
                val actual = compose.onNodeWithTag(tag).captureToImage().toPixelMap()[8, 20]
                assertEquals(tag, expected.red, actual.red, .015f)
                assertEquals(tag, expected.green, actual.green, .015f)
                assertEquals(tag, expected.blue, actual.blue, .015f)
            }
        }
        assertFilm()
        compose.runOnIdle { color.value = Color(0xFF285947) }
        assertFilm()
    }
}
