package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HeaderActionCanvasTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `header canvas matches logo without changing page action dimensions`() {
        val vertical = mutableStateOf(false)
        val enabled = mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            val settings = GlassSettings(enabled = enabled.value, quality = GlassQuality.Performance)
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings, LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                GlassRippleProvider(settings) {
                    Column {
                        Box(Modifier.size(36.dp).testTag("logo"))
                        HeaderIcon(R.drawable.search, modifier = Modifier.testTag("search")) {}
                        HeaderIcon(R.drawable.chevron_back, modifier = Modifier.testTag("back")) {}
                        CompositionLocalProvider(LocalGlassActionCellSize provides DpSize(HeaderActionCanvasSize, HeaderActionCanvasSize)) {
                            if (vertical.value) GlassVerticalActionGroup(settings, GlassArea.Header) {
                                GlassActionGroupItem({}, Modifier.testTag("profile")) { Box(Modifier.size(32.dp).testTag("avatar")) }
                            } else GlassActionGroup(settings, GlassArea.Header) {
                                GlassActionGroupItem({}, Modifier.testTag("profile")) { Box(Modifier.size(32.dp).testTag("avatar")) }
                            }
                        }
                        GlassActionGroup(settings, GlassArea.ActionButtons) {
                            GlassActionGroupItem({}, Modifier.testTag("page")) {}
                        }
                    }
                }
            }
        }
        for (glass in listOf(false, true)) for (side in listOf(false, true)) {
            compose.runOnIdle { enabled.value = glass; vertical.value = side }
            listOf("logo", "search", "back", "profile").forEach {
                compose.onNodeWithTag(it).assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)
            }
            compose.onNodeWithTag("avatar", useUnmergedTree = true).assertWidthIsEqualTo(32.dp)
            compose.onNodeWithTag("page").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(40.dp)
        }
    }
}
