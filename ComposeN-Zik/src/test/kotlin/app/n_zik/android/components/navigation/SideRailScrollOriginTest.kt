package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SideRailScrollOriginTest {
    @get:Rule val compose = createComposeRule()

    @Test fun railDragScrollsRailButOnlyPageDragReachesChrome() {
        val origin = SideRailScrollOrigin()
        var pageTravel = 0f
        var railTravel = 0
        val root = object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!origin.take()) pageTravel += chromeScrollDelta(consumed.y, available.y, false)
                return Offset.Zero
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalSideRailScrollOrigin provides origin) {
                val railState = rememberScrollState()
                railTravel = railState.value
                Row(Modifier.nestedScroll(root)) {
                    Box(Modifier.size(80.dp, 220.dp).sideRailScrollOrigin()) {
                        Box(Modifier.fillMaxWidth().testTag("rail").verticalScroll(railState)) {
                            Box(Modifier.height(1200.dp))
                        }
                    }
                    Box(Modifier.size(160.dp, 220.dp)) {
                        Box(Modifier.fillMaxWidth().testTag("page").verticalScroll(rememberScrollState())) {
                            Box(Modifier.height(1200.dp))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("rail").performTouchInput { swipeUp() }
        compose.runOnIdle {
            assertTrue("The rail must remain scrollable", railTravel > 0)
            assertEquals("Rail travel must not compact the mini-player", 0f, pageTravel, 0f)
        }
        compose.onNodeWithTag("page").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("Page scrolling must still drive chrome", pageTravel < 0f) }
    }
}
