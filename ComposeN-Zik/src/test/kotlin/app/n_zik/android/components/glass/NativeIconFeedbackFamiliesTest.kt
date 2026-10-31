package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.it.fast4x.rimusic.ui.components.themed.DownloadStateIconButton
import app.it.fast4x.rimusic.ui.components.themed.Title2Actions
import app.it.fast4x.rimusic.ui.components.themed.Title3Actions
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class NativeIconFeedbackFamiliesTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(4.dp)
    private val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

    @Test fun `native dice play settings and refresh reserve equal nonoverlapping action seats`() {
        val clicks = IntArray(4)
        compose.setContent {
            val settings = GlassSettings(enabled = false)
            CompositionLocalProvider(LocalAppearance provides appearance, LocalGlassPreviewSettings provides settings) {
                Row {
                    GlassSectionActions(listOf(
                        GlassSectionAction(R.drawable.dice, "dice", nativeEndPadding = 12.dp) { clicks[0]++ },
                        GlassSectionAction(R.drawable.play, "play", nativeEndPadding = 12.dp) { clicks[1]++ },
                        GlassSectionAction(R.drawable.settings, "settings") { clicks[2]++ },
                    ), settings = settings)
                    NativeSectionIconAction(R.drawable.refresh, "refresh", iconSize = 18.dp,
                        settings = settings, onClick = { clicks[3]++ })
                }
            }
        }
        val bounds = listOf("dice", "play", "settings", "refresh").mapIndexed { index, name ->
            val node = compose.onNodeWithContentDescription(name)
            node.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
            for (edge in 0..3) node.performTouchInput {
                click(when (edge) {
                    0 -> Offset(1f, center.y)
                    1 -> Offset(width - 1f, center.y)
                    2 -> Offset(center.x, 1f)
                    else -> Offset(center.x, height - 1f)
                })
            }
            compose.runOnIdle { assertEquals(4, clicks[index]) }
            node.fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        bounds.take(3).zipWithNext().forEach { (left, right) ->
            assertEquals("The seat already supplies the old glyph spacing", left.width,
                right.center.x - left.center.x, .01f)
        }
    }

    @Test fun `both legacy title families retain independent forty two dp child action owners`() {
        val clicks = IntArray(5)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Column {
                    Title2Actions("two", onClick1 = { clicks[0]++ }, onClick2 = { clicks[1]++ })
                    Title3Actions("three", onClick1 = { clicks[2]++ }, onClick2 = { clicks[3]++ },
                        onClick3 = { clicks[4]++ })
                }
            }
        }
        val owners = compose.onAllNodes(hasClickAction() and !hasAnyDescendant(hasClickAction()), useUnmergedTree = true)
        assertEquals(5, owners.fetchSemanticsNodes().size)
        repeat(5) { index ->
            owners[index].assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
                .performTouchInput { click(Offset(1f, center.y)) }
        }
        compose.runOnIdle { assertEquals(listOf(1, 1, 1, 1, 1), clicks.toList()) }
        val bounds = owners.fetchSemanticsNodes().map { it.boundsInRoot }
        listOf(bounds.take(2), bounds.drop(2)).forEach { row ->
            row.zipWithNext().forEach { (left, right) ->
                assertEquals("Legacy heading actions use adjacent native seats", left.width,
                    right.center.x - left.center.x, .01f)
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun `idle and queued download actions include reserved padding without enlarging glyph slots`() {
        val clicks = IntArray(2)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(enabled = false)) {
                Row {
                    DownloadStateIconButton({ clicks[0]++ }, { error("Idle must not cancel") },
                        R.drawable.download, Color.Blue, Modifier.testTag("download.idle").padding(11.dp).size(20.dp),
                        downloadState = Download.STATE_STOPPED)
                    DownloadStateIconButton({ error("Queued must cancel") }, { clicks[1]++ },
                        R.drawable.download, Color.Blue, Modifier.testTag("download.queued").padding(11.dp).size(20.dp),
                        downloadState = Download.STATE_QUEUED)
                }
            }
        }
        listOf("download.idle", "download.queued").forEach { tag ->
            val node = compose.onNodeWithTag(tag)
            node.assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
            node.performTouchInput { click(Offset(1f, center.y)) }
        }
        compose.runOnIdle { assertEquals(listOf(1, 1), clicks.toList()) }
    }
}
