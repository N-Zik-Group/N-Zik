package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassSideRailMenuPlacementTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `right rail menu mirrors left rail clearance for native and glass in both directions`() {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        val previousPosition = preferences.getString(navigationBarPositionKey, null)
        val host = GlassMenuHostState()
        val direction = mutableStateOf(LayoutDirection.Ltr)
        var viewport = Rect.Zero
        var density = 1f
        try {
            compose.setContent {
                density = LocalDensity.current.density
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction.value,
                    LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback,
                        RectangleShape, RectangleShape, RectangleShape),
                ) {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInRoot() }) {
                            GlassOverlayHost(host)
                        }
                    }
                }
            }
            compose.waitForIdle()
            for (glass in listOf(false, true)) for (layoutDirection in LayoutDirection.entries) {
                for (side in listOf(NavigationBarPosition.Left, NavigationBarPosition.Right)) {
                    val trigger = GlassMenuTriggerState()
                    compose.runOnIdle {
                        direction.value = layoutDirection
                        preferences.edit().putString(navigationBarPositionKey, side.name).commit()
                        val left = if (side == NavigationBarPosition.Left) viewport.left + 4f * density
                            else viewport.right - 48f * density
                        trigger.bounds = Rect(left, viewport.top + 20f * density,
                            left + 44f * density, viewport.top + 68f * density)
                        host.show(GlassMenuEntry(
                            id = Any(), anchor = trigger.bounds, trigger = trigger,
                            settings = GlassSettings(enabled = glass), onDismiss = {},
                            shape = RectangleShape, containerColor = Color.DarkGray,
                            content = { Box(Modifier.size(120.dp, 100.dp)) },
                        ))
                    }
                    compose.waitForIdle()
                    compose.runOnIdle {
                        val pane = requireNotNull(trigger.menuBounds)
                        val gap = if (side == NavigationBarPosition.Left) pane.left - trigger.bounds.right
                            else trigger.bounds.left - pane.right
                        val scenario = "side=$side glass=$glass direction=$layoutDirection pane=$pane"
                        assertEquals(scenario, 4f * density, gap, 1f)
                        assertTrue(scenario, pane.left >= viewport.left && pane.right <= viewport.right)
                    }
                }
            }
        } finally {
            preferences.edit().apply {
                if (previousPosition == null) remove(navigationBarPositionKey)
                else putString(navigationBarPositionKey, previousPosition)
            }.commit()
        }
    }
}
