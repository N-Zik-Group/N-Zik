package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassModalInputTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `menu keeps scaffold isolation through its retained closing animation`() {
        val host = GlassMenuHostState()
        var scaffoldMoves = 0
        val scaffold = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scaffoldMoves++
                return Offset.Zero
            }
        }.excludingGlassOverlays(host)
        val id = Any()
        host.show(GlassMenuEntry(id, Rect.Zero, GlassSettings(), {}, {}, shape = RoundedCornerShape(8.dp)))
        scaffold.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        host.hide(id)
        scaffold.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        assertEquals(0, scaffoldMoves)
        host.clear(id)
        scaffold.onPreScroll(Offset(0f, 20f), NestedScrollSource.UserInput)
        assertEquals(1, scaffoldMoves)
    }

    @Test fun `inside and outside modal drags preserve inner scrolling and isolate the page scaffold`() {
        val host = GlassMenuHostState()
        var visible by mutableStateOf(true)
        var scaffoldMoves = 0
        lateinit var pageScroll: androidx.compose.foundation.ScrollState
        lateinit var dialogScroll: androidx.compose.foundation.ScrollState
        val scaffold = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                scaffoldMoves++
                return Offset.Zero
            }
        }.excludingGlassOverlays(host)
        composeRule.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                pageScroll = rememberScrollState()
                dialogScroll = rememberScrollState()
                Box(Modifier.size(300.dp, 500.dp).nestedScroll(scaffold)) {
                    GlassModalContent(host) {
                        Column(Modifier.size(300.dp, 500.dp).verticalScroll(pageScroll).testTag("pageScroll")) {
                            Box(Modifier.height(1500.dp))
                        }
                        if (visible) GlassDialogWindow(true, {}, DialogProperties()) {
                            Column(Modifier.size(200.dp).verticalScroll(dialogScroll).testTag("dialogScroll")) {
                                Box(Modifier.height(1200.dp))
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        composeRule.onNodeWithTag("dialogScroll").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("glassDialogOverlay").performTouchInput {
            swipe(Offset(5f, height * .8f), Offset(5f, height * .2f))
        }
        composeRule.runOnIdle {
            assertTrue("The dialog must retain its own vertical scrolling", dialogScroll.value > 0)
            assertEquals(0, pageScroll.value)
            assertEquals(0, scaffoldMoves)
            visible = false
        }
        composeRule.onNodeWithTag("pageScroll").performTouchInput { swipeUp() }
        composeRule.runOnIdle {
            assertTrue(pageScroll.value > 0)
            assertTrue("Normal scaffold input resumes after dismissal", scaffoldMoves > 0)
        }
    }
}
