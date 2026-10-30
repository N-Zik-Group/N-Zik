package app.n_zik.android.components.artist

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistFollowButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `follow palette updates without toggling follow state in every material mode`() {
        val palette = mutableStateOf(DefaultDarkColorPalette.copy(accent = Color.Red))
        val state = mutableStateOf<Boolean?>(true)
        val mode = mutableStateOf(GlassAreaMode.Disabled)
        var clicks = 0
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                palette.value, Typography.fallback, shape, shape, shape,
            )) {
                ArtistFollowButton(state.value, { clicks++ }, Modifier.testTag("follow"),
                    GlassSettings(quality = GlassQuality.Performance, film = 1f, rim = 0f,
                        areaModes = mapOf(GlassArea.ActionButtons to mode.value)))
            }
        }
        fun assertFill(color: Color) {
            val image = compose.onNodeWithTag("follow").captureToImage().toPixelMap()
            val pixel = image[12, image.height / 2]
            assertEquals(color.red, pixel.red, .025f)
            assertEquals(color.green, pixel.green, .025f)
            assertEquals(color.blue, pixel.blue, .025f)
        }
        GlassAreaMode.entries.forEach { selected ->
            compose.runOnIdle { mode.value = selected; palette.value = palette.value.copy(accent = Color.Red) }
            assertFill(Color.Red)
            compose.runOnIdle { palette.value = palette.value.copy(accent = Color.Green) }
            assertFill(Color.Green)
            compose.onNodeWithTag("follow").assertWidthIsEqualTo(100.dp)
                .assertHeightIsEqualTo(if (selected.hasMaterial) 40.dp else 32.dp)
            compose.onNodeWithText(context.getString(R.string.following)).performClick()
        }
        compose.runOnIdle { assertEquals(4, clicks); state.value = false }
        compose.onNodeWithText(context.getString(R.string.disliked)).assertExists()
        assertFill(palette.value.red)
        compose.runOnIdle { state.value = null }
        compose.onNodeWithText(context.getString(R.string.follow)).assertExists()
        assertFill(palette.value.background2)
    }
}
