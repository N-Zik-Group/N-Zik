package app.n_zik.android.components.navigation.nav

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.UiTypeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class NavigationTabTransitionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString(UiTypeKey, UiType.RiMusic.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name).commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `registered reordered tabs settle immediately without changing viewport or retaining old actions`() {
        var reverse by mutableStateOf(false)
        var destination by mutableStateOf(false)
        var containerWidth by mutableStateOf(320.dp)
        var oldClicks = 0
        var newClicks = 0
        val controller = mockk<NavController>(relaxed = true)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(ColorPalette.fallback,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Row(Modifier.size(containerWidth, 72.dp).testTag("viewport"), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val currentDestination = destination
                    val bar = HorizontalNavigationBar(0, {
                        if (currentDestination) newClicks++ else oldClicks++
                    }, controller)
                    bar.add { item ->
                        item(0, if (destination) "New" else "Old", R.drawable.library)
                        item(1, "Other", R.drawable.search)
                    }
                    bar.DrawButtons(if (reverse) listOf(1, 0) else listOf(0, 1))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val original = compose.onNodeWithContentDescription("Old").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { reverse = true; destination = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Old").assertDoesNotExist()
        val start = compose.onNodeWithContentDescription("New").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("New").performClick()
        compose.mainClock.advanceTimeBy(128)
        val middle = compose.onNodeWithContentDescription("New").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(400)
        val end = compose.onNodeWithContentDescription("New").fetchSemanticsNode().boundsInRoot
        assertTrue("The current slot must move to its new position", start.center.x > original.center.x)
        assertEquals(start, middle)
        assertEquals(start, end)
        assertEquals(viewport, compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(0, oldClicks); assertEquals(1, newClicks) }

        // Dock resizing must not leave any trailing icon placement animation.
        compose.runOnIdle { containerWidth = 280.dp; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        val resized = compose.onNodeWithContentDescription("New").fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(128)
        assertEquals(resized, compose.onNodeWithContentDescription("New").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun `real bars immediately replace tabs with current actions in every position and material`() {
        var position by mutableStateOf(NavigationBarPosition.Top)
        var count by mutableStateOf(3)
        var destination by mutableStateOf(false)
        var oldClicks = 0
        var newClicks = 0
        val controller = mockk<NavController>(relaxed = true)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(ColorPalette.fallback,
                typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape)) {
                Box(Modifier.size(360.dp, 500.dp).testTag("viewport")) {
                    val currentDestination = destination
                    val action: (Int) -> Unit = { if (currentDestination) newClicks++ else oldClicks++ }
                    if (position == NavigationBarPosition.Left || position == NavigationBarPosition.Right) {
                        Column(Modifier.size(64.dp, 480.dp)) {
                            val bar = VerticalNavigationBar(0, action, controller)
                            bar.add { item -> repeat(count) { item(it, "${if (destination) "New" else "Old"} $it", R.drawable.library) } }
                            bar.DrawTabs(rememberLayerBackdrop())
                        }
                    } else {
                        Box(Modifier.size(360.dp, 90.dp)) {
                            val bar = HorizontalNavigationBar(0, action, controller, Modifier.fillMaxSize())
                            bar.add { item -> repeat(count) { item(it, "${if (destination) "New" else "Old"} $it", R.drawable.library) } }
                            bar.Draw()
                        }
                    }
                }
            }
        }
        for (glass in listOf(false, true)) for (side in NavigationBarPosition.entries) {
            compose.runOnIdle {
                context.preferences.edit().putBoolean(glassEnabledKey, glass)
                    .putString(navigationBarPositionKey, side.name)
                    .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name).commit()
                position = side; count = 3; destination = false
            }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { count = 2; destination = true; Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Old 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Old 2").assertDoesNotExist()
            val firstFrame = compose.onNodeWithContentDescription("New 1").fetchSemanticsNode().boundsInRoot
            val previousClicks = newClicks
            compose.onNodeWithContentDescription("New 1").performClick()
            compose.runOnIdle { assertEquals(previousClicks + 1, newClicks); assertEquals(0, oldClicks) }
            compose.mainClock.advanceTimeBy(400)
            assertEquals(firstFrame, compose.onNodeWithContentDescription("New 1").fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle {
                context.preferences.edit().putString(navigationBarTypeKey, NavigationBarType.IconOnly.name).commit()
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithText("New 1", useUnmergedTree = true).assertDoesNotExist()
            assertEquals(viewport, compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot)
            compose.mainClock.autoAdvance = true
        }
    }
}
