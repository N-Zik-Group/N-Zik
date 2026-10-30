package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
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
class GlassDescriptionControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native expansion stays passive while glass adds one working action`() {
        val enabled = mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            AppearanceProvider {
                GlassDescriptionExpansion({ clicks++ }, settings = GlassSettings(enabled = enabled.value)) {
                    BasicText("Show more")
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.runOnIdle { enabled.value = true }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.onNodeWithText("Show more").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun `explicit surface preserves badge bounds without adding an action`() {
        val enabled = mutableStateOf(false)
        var iconId = 0
        compose.setContent {
            AppearanceProvider {
                iconId = explicitBadgeIconId(GlassSettings(enabled = enabled.value))
                Box(Modifier.size(18.dp).glassExplicitBadgeBackground(GlassSettings(enabled = enabled.value)).testTag("badge"))
            }
        }
        listOf(false, true).forEach { glass ->
            compose.runOnIdle { enabled.value = glass }
            compose.onNodeWithTag("badge").assertWidthIsEqualTo(18.dp).assertHeightIsEqualTo(18.dp)
            compose.onAllNodes(hasClickAction()).assertCountEquals(0)
            compose.runOnIdle { assertEquals(if (glass) R.drawable.explicit_glass_glyph else R.drawable.explicit, iconId) }
        }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
