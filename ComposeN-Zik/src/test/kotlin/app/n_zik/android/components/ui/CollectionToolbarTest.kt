package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import app.n_zik.android.components.glass.GlassToolbarButtons
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.GlassToolbarRow
import app.n_zik.android.components.glass.GlassActionCellWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `last playlist heart stays visible and clickable in native and Glass at narrow width`() {
        verifyToolbar(320, 8)
    }

    @Test fun `podcast actions use the same compact spacing and centered separate bookmark`() {
        verifyToolbar(400, 7)
    }

    @Test fun `album visible action count matches the original toolbar in native and Glass`() {
        verifyOriginalOverflow(40, 15, 12)
    }

    @Test fun `artist visible action count matches the original toolbar without a forced cap`() {
        verifyOriginalOverflow(100, 10, 7)
    }

    private fun verifyOriginalOverflow(bookmarkWidth: Int, originalGap: Int, actionCount: Int) {
        val width = mutableStateOf(320)
        val glass = mutableStateOf(false)
        val buttons = (0 until actionCount).map { object : Button {
            @Composable override fun ToolBarButton() {
                TabToolBar.Icon(painterResource(R.drawable.play), onClick = {})
            }
        } }
        compose.setContent {
            AppearanceProvider {
                val settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance)
                val bookmark: @Composable () -> Unit = {
                    if (bookmarkWidth == 40) TabToolBar.Icon(painterResource(R.drawable.bookmark), onClick = {})
                    else Box(Modifier.width(bookmarkWidth.dp).height(40.dp))
                }
                Column {
                    Row(Modifier.requiredWidth(width.value.dp).testTag("original")) {
                        if (bookmarkWidth == 40) GlassToolbarRow(1, settings = settings) { bookmark() }
                        else bookmark()
                        Spacer(Modifier.width(originalGap.dp))
                        GlassToolbarButtons(buttons, modifier = Modifier.fillMaxWidth(.8f),
                            settings = settings, disableAnimation = true)
                    }
                    CollectionButtonToolbar(
                        bookmark = bookmark,
                        buttons = buttons, bookmarkWidth = bookmarkWidth.dp,
                        originalNativeBookmarkWidth = if (bookmarkWidth == 40) 48.dp else bookmarkWidth.dp,
                        actionCellWidth = if (bookmarkWidth == 100) GlassActionCellWidth else CollectionActionCellWidth,
                        originalBookmarkGap = originalGap.dp, settings = settings,
                        modifier = Modifier.requiredWidth(width.value.dp).testTag("compact"))
                }
            }
        }
        for (available in listOf(240, 320, 352, 400, 600)) {
            for (material in listOf(false, true)) {
                compose.runOnIdle { width.value = available; glass.value = material }
                compose.waitForIdle()
                val original = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("original")))
                    .fetchSemanticsNodes().size
                compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("compact")))
                    .assertCountEquals(original)
            }
        }
    }

    private fun verifyToolbar(width: Int, count: Int) {
        val glass = mutableStateOf(false)
        var heartClicks = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(width.dp).testTag("available")) {
                    val actions: List<@Composable () -> Unit> = (0 until count).map { index -> @Composable {
                        GlassToolbarIcon(painterResource(R.drawable.heart), Color.White, 32.dp, true,
                            // Real legacy callers supply their own padding. It must not widen the cell.
                            modifier = Modifier.padding(horizontal = 5.dp), modifierOnContainer = true,
                            contentDescription = "action.$index", onClick = { heartClicks++ })
                    } }
                    CollectionToolbar(bookmark = {
                        GlassToolbarIcon(painterResource(R.drawable.bookmark), Color.White, 32.dp, true,
                            contentDescription = "bookmark", onClick = {})
                    }, actions = actions,
                        settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance))
                }
            }
        }
        var nativeCenters: List<Float>? = null
        for (material in listOf(false, true)) {
            compose.runOnIdle { glass.value = material }
            compose.waitForIdle()
            compose.onAllNodes(hasClickAction()).assertCountEquals(count + 1)
            val bounds = compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
            val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            controls.forEach {
                assertTrue("Every action fits within the row", it.left >= bounds.left && it.right <= bounds.right)
            }
            val left = controls.minOf { it.left }
            val right = controls.maxOf { it.right }
            assertEquals(bounds.center.x, (left + right) / 2, 1f)
            val centers = controls.map { it.center.x }
            if (material) assertEquals(nativeCenters, centers) else nativeCenters = centers
            val glyph = compose.onNodeWithContentDescription("action.${count - 1}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val previousGlyph = compose.onNodeWithContentDescription("action.${count - 2}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(20f * bounds.width / width, glyph.width, .1f)
            assertEquals(glyph.width, previousGlyph.width, .1f)
            compose.onAllNodes(hasClickAction())[count].performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(2, heartClicks) }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }
}
