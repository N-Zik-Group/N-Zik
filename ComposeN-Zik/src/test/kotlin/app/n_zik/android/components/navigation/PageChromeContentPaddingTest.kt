package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.n_zik.android.LocalPlayerAwareWindowInsets
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class PageChromeContentPaddingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `left rail remains physically left with RTL content`() =
        verifyScrollableClearance(onRight = false, direction = LayoutDirection.Rtl)

    @Test fun `right rail remains physically right with LTR content`() =
        verifyScrollableClearance(onRight = true, direction = LayoutDirection.Ltr)

    private fun verifyScrollableClearance(onRight: Boolean, direction: LayoutDirection) {
        val topReserve = mutableFloatStateOf(148f)
        var compositions = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides direction,
                LocalSideRailContentInsets provides WindowInsets(
                    left = if (onRight) 0.dp else 50.dp,
                    right = if (onRight) 50.dp else 0.dp,
                ),
                LocalPlayerAwareWindowInsets provides WindowInsets(top = 24.dp, bottom = 132.dp),
            ) {
                SideEffect { compositions++ }
                val topInset = remember { { topReserve.floatValue.dp } }
                val padding = rememberPageChromeContentPadding(
                    base = PaddingValues(start = 12.dp, end = 16.dp, bottom = 100.dp),
                    topInsetProvider = topInset,
                )
                Box(Modifier.size(300.dp, 400.dp).testTag("viewport")) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        contentPadding = padding,
                    ) {
                        items(20, key = { it }, contentType = { "row" }) { index ->
                            Box(Modifier.fillMaxWidth().height(60.dp).testTag("row-$index"))
                        }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("list")
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val firstRow = compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport, list.fetchSemanticsNode().boundsInRoot)
        assertEquals(viewport.top + 148f, firstRow.top, 0f)
        assertEquals(viewport.left + if (onRight) 12f else 66f, firstRow.left, 0f)
        assertEquals(viewport.right - if (onRight) 66f else 12f, firstRow.right, 0f)

        val originalCompositions = compositions
        compose.runOnIdle { topReserve.floatValue = 164f }
        assertEquals(viewport.top + 164f,
            compose.onNodeWithTag("row-0").fetchSemanticsNode().boundsInRoot.top, 0f)
        assertEquals(viewport, list.fetchSemanticsNode().boundsInRoot)
        assertEquals(originalCompositions, compositions)

        list.performScrollToIndex(19)
        assertEquals(viewport.bottom - 132f,
            compose.onNodeWithTag("row-19").fetchSemanticsNode().boundsInRoot.bottom, 0f)
        assertEquals(viewport, list.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `existing large bottom reserve is retained without adding chrome twice`() {
        val padding = PageChromeContentPadding(
            base = PaddingValues(top = 8.dp, bottom = 230.dp),
            topInset = { 88.dp },
            railInsets = PaddingValues(0.dp),
            playerInsets = PaddingValues(top = 24.dp, bottom = 132.dp),
        )
        assertEquals(230.dp, padding.calculateBottomPadding())
        assertEquals(96.dp, padding.calculateTopPadding())
    }

    @Test fun `home toolbar reserve follows its visible height`() {
        val visibleHeight = mutableFloatStateOf(160f)
        val padding = PageChromeContentPadding(
            base = PaddingValues(bottom = 32.dp),
            topInset = { 88.dp },
            railInsets = PaddingValues(0.dp),
            playerInsets = PaddingValues(0.dp),
            extraTop = { visibleHeight.floatValue.dp },
        )
        assertEquals(248.dp, padding.calculateTopPadding())
        visibleHeight.floatValue = 0f
        assertEquals(88.dp, padding.calculateTopPadding())
    }
}
