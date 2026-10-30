package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HeaderSearchVisibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shared horizontal glyph keeps its navigation fraction when the page branch remounts`() {
        val progress = mutableStateOf(1f)
        val generation = mutableStateOf(0)
        var glyphX = 0f
        compose.setContent {
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                Row {
                    key(generation.value) {
                        HorizontalHeaderSearchVisibility(progress) {
                            Box(Modifier.size(44.dp, 40.dp), contentAlignment = Alignment.Center) {
                                androidx.compose.material3.Icon(
                                    androidx.compose.ui.res.painterResource(app.n_zik.android.R.drawable.search),
                                    "search", Modifier.size(20.dp).onGloballyPositioned { glyphX = it.positionInRoot().x })
                            }
                        }
                    }
                    Box(Modifier.size(44.dp, 40.dp).testTag("persistent-profile"))
                }
            }
        }
        val profile = compose.onNodeWithTag("persistent-profile").fetchSemanticsNode().boundsInRoot
        val resting = glyphX
        listOf(.8f, .6f, .4f, .6f, .8f, 1f).forEach { fraction ->
            compose.runOnIdle { progress.value = fraction }
            compose.waitForIdle()
            val before = glyphX
            assertEquals(resting + profile.width * (1f - fraction), before, 1f)
            compose.runOnIdle { generation.value++; Snapshot.sendApplyNotifications() }
            compose.waitForIdle()
            assertEquals("remount keeps the actual search glyph position", before, glyphX, 1f)
            assertEquals(profile, compose.onNodeWithTag("persistent-profile").fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test
    fun `shared vertical progress survives a remounted search slot and retains the profile`() {
        val progress = mutableStateOf(1f)
        val generation = mutableStateOf(0)
        var profilesCreated = 0
        compose.setContent {
            CompositionLocalProvider(LocalSideHeaderSearchProgress provides progress) {
                Column(Modifier.testTag("shared-actions")) {
                    key(generation.value) {
                        VerticalHeaderSearchVisibility(requireNotNull(LocalSideHeaderSearchProgress.current)) {
                            Box(Modifier.size(44.dp, 40.dp))
                        }
                    }
                    DisposableEffect(Unit) { profilesCreated++; onDispose { } }
                    Box(Modifier.size(44.dp, 40.dp))
                }
            }
        }
        val actions = compose.onNodeWithTag("shared-actions")
        val full = actions.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { progress.value = .5f }
        val halfway = actions.fetchSemanticsNode().boundsInRoot.height
        assertEquals(full * .75f, halfway, 1f)
        compose.runOnIdle { generation.value++ }
        assertEquals(halfway, actions.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { progress.value = 0f }
        assertEquals(full / 2f, actions.fetchSemanticsNode().boundsInRoot.height, 1f)
        compose.runOnIdle { progress.value = .25f }
        assertEquals(full * .625f, actions.fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(1, profilesCreated)
    }

    @Test
    fun `vertical search shrinks and returns gradually beside the retained profile`() {
        val visible = mutableStateOf(true)
        var profilesCreated = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val visibility = rememberHeaderSearchVisibility(visible.value)
            Column(Modifier.testTag("vertical-actions")) {
                HeaderSearchVisibility(visibility, motionAllowed = true, vertical = true) {
                    Box(Modifier.size(44.dp, 40.dp))
                }
                DisposableEffect(Unit) { profilesCreated++; onDispose { } }
                Box(Modifier.size(44.dp, 40.dp).testTag("vertical-profile"))
            }
        }
        val actions = compose.onNodeWithTag("vertical-actions")
        val full = actions.fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { visible.value = false }
        advanceFrames(5)
        val middle = actions.fetchSemanticsNode().boundsInRoot.height
        assertTrue(middle < full && middle > full / 2f)
        advanceFrames(20)
        assertTrue(actions.fetchSemanticsNode().boundsInRoot.height <= full / 2f + 1f)
        compose.runOnIdle { visible.value = true }
        advanceFrames(5)
        val returning = actions.fetchSemanticsNode().boundsInRoot.height
        assertTrue(returning > full / 2f && returning < full)
        advanceFrames(20)
        assertEquals(full, actions.fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(1, profilesCreated)
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    @Test
    fun `search starts settled then changes width gradually without recreating the profile`() {
        val visible = mutableStateOf(true)
        lateinit var visibility: MutableTransitionState<Boolean>
        var profilesCreated = 0
        var profilesDisposed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            visibility = rememberHeaderSearchVisibility(visible.value)
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                Row(Modifier.testTag("actions")) {
                    HeaderSearchVisibility(visibility, motionAllowed = true) { Box(Modifier.size(44.dp, 40.dp)) }
                    DisposableEffect(Unit) {
                        profilesCreated++
                        onDispose { profilesDisposed++ }
                    }
                    Box(Modifier.size(44.dp, 40.dp).testTag("profile"))
                }
            }
        }
        val actions = compose.onNodeWithTag("actions")
        actions.assertWidthIsEqualTo(88.dp)
        assertTrue(visibility.isIdle)
        val profile = compose.onNodeWithTag("profile")
        val profileBounds = profile.fetchSemanticsNode().boundsInRoot
        val fullWidth = actions.fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { visible.value = false }
        advanceFrames(5)
        val shrinkingWidth = actions.fetchSemanticsNode().boundsInRoot.width
        assertTrue("shrinking width $shrinkingWidth must be between ${fullWidth / 2} and $fullWidth",
            shrinkingWidth > fullWidth / 2 && shrinkingWidth < fullWidth)
        assertEquals(profileBounds, profile.fetchSemanticsNode().boundsInRoot)
        assertTrue("the exiting search still owns its cell", visibility.currentState || visibility.targetState)
        advanceFrames(20)
        actions.assertWidthIsEqualTo(44.dp)
        assertFalse(visibility.currentState || visibility.targetState)
        compose.runOnIdle { visible.value = true }
        advanceFrames(5)
        val expandingWidth = actions.fetchSemanticsNode().boundsInRoot.width
        assertTrue("expanding width $expandingWidth must be between ${fullWidth / 2} and $fullWidth",
            expandingWidth > fullWidth / 2 && expandingWidth < fullWidth)
        assertEquals(profileBounds, profile.fetchSemanticsNode().boundsInRoot)
        advanceFrames(20)
        actions.assertWidthIsEqualTo(88.dp)
        compose.runOnIdle { assertEquals(1, profilesCreated); assertEquals(0, profilesDisposed) }
    }

    @Test
    fun `native search slides from behind the anchored profile in both directions`() {
        val visible = mutableStateOf(true)
        var searchX = Float.NaN
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val visibility = rememberHeaderSearchVisibility(visible.value)
            Box(Modifier.width(88.dp), contentAlignment = Alignment.CenterEnd) {
                Row {
                    HeaderSearchVisibility(visibility, motionAllowed = true, slideFromProfile = true) {
                        Box(Modifier.size(44.dp, 40.dp).onGloballyPositioned { searchX = it.positionInRoot().x })
                    }
                    Box(Modifier.size(44.dp, 40.dp).testTag("profile"))
                }
            }
        }
        val profile = compose.onNodeWithTag("profile")
        val anchor = profile.fetchSemanticsNode().boundsInRoot
        val restingX = searchX
        compose.runOnIdle { visible.value = false }
        advanceFrames(5)
        assertTrue("search must slide toward the profile, not merely clip", searchX > restingX && searchX < anchor.left)
        assertEquals(anchor, profile.fetchSemanticsNode().boundsInRoot)
        advanceFrames(20)
        compose.runOnIdle { visible.value = true }
        advanceFrames(5)
        assertTrue("search must emerge from the profile side", searchX > restingX && searchX < anchor.left)
        assertEquals(anchor, profile.fetchSemanticsNode().boundsInRoot)
        advanceFrames(20)
        assertEquals(restingX, searchX, 1f)
    }

    @Test
    fun `reduced motion settles directly and initially hidden search has no entrance`() {
        val visible = mutableStateOf(false)
        compose.setContent {
            val visibility = rememberHeaderSearchVisibility(visible.value)
            Row(Modifier.testTag("actions")) {
                HeaderSearchVisibility(visibility, motionAllowed = false, slideFromProfile = true) { Box(Modifier.size(44.dp, 40.dp)) }
                Box(Modifier.size(44.dp, 40.dp))
            }
        }
        val actions = compose.onNodeWithTag("actions")
        actions.assertWidthIsEqualTo(44.dp)
        compose.runOnIdle { visible.value = true }
        actions.assertWidthIsEqualTo(88.dp)
        compose.runOnIdle { visible.value = false }
        actions.assertWidthIsEqualTo(44.dp)
    }
}
