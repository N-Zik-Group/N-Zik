package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.Title
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.glass.*
import app.n_zik.android.components.ui.screens.home.quickpicks.HomeSectionTitle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SectionTrailingAxisTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `standalone arrows grouped actions and header share a trailing center`() {
        val glass = mutableStateOf(false)
        compose.setContent {
            val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(LocalGlassPreviewSettings provides settings,
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)) {
                GlassRippleProvider(settings) {
                    Column(Modifier.width(300.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                            Spacer(Modifier.weight(1f))
                            GlassSectionActions(listOf(
                                GlassSectionAction(R.drawable.dice, "dice", onClick = {}),
                                GlassSectionAction(R.drawable.play, "play", onClick = {}),
                                GlassSectionAction(R.drawable.settings, "settings", onClick = {}),
                            ))
                        }
                        HomeSectionTitle("Home section", onClick = {})
                        Title("Other section", onClick = {})
                        Box(Modifier.fillMaxWidth().padding(end = headerActionEndPadding(glass.value)),
                            contentAlignment = Alignment.CenterEnd) {
                            Box(Modifier.size(HeaderActionCanvasSize).testTag("profile-canvas"))
                        }
                    }
                }
            }
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glass.value = enabled }
            val settings = compose.onNodeWithContentDescription("settings", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val expectedCenter = (settings.left + settings.right) / 2
            for (label in listOf("Home section", "Other section")) {
                val arrow = compose.onNodeWithContentDescription(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertEquals(label, expectedCenter, (arrow.left + arrow.right) / 2)
            }
            val header = compose.onNodeWithTag("profile-canvas").getUnclippedBoundsInRoot()
            assertEquals(expectedCenter, (header.left + header.right) / 2)
        }
    }
}
