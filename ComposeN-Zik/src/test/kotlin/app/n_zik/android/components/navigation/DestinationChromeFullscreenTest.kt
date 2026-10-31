package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassScope
import app.n_zik.android.components.glass.inScope
import app.n_zik.android.components.glass.mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DestinationChromeFullscreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `rewind deck ignores top header reserve but rewind home keeps it`() = verify(NavigationBarPosition.Top)

    @Test fun `rewind deck ignores side rail reserve but rewind home keeps it`() = verify(NavigationBarPosition.Left)

    @Test fun `coexisting entries retain their appearance scope across forward back and rewind transitions`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("entry_glass_scope", context)
        context.preferences.edit().clear().putString(navigationBarPositionKey, NavigationBarPosition.Top.name).commit()
        val outer = mutableStateOf(GlassScope.Details)
        val routes = mutableStateOf(listOf("album/{id}", "settings"))
        val observed = mutableMapOf<String, GlassAreaMode>()
        var nested = GlassScope.Shared
        val settings = GlassSettings(contextModes = mapOf(
            GlassContextArea(GlassScope.Details, GlassArea.ActionButtons) to GlassAreaMode.Disabled,
            GlassContextArea(GlassScope.SettingsProfiles, GlassArea.ActionButtons) to GlassAreaMode.GlassOnly,
            GlassContextArea(GlassScope.Rewind, GlassArea.ActionButtons) to GlassAreaMode.ContourOnly,
        ))
        compose.setContent {
            CompositionLocalProvider(LocalGlassScope provides outer.value,
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0)) {
                routes.value.forEach { route ->
                    DestinationChrome(route) {
                        val mode = settings.inScope(LocalGlassScope.current).mode(GlassArea.ActionButtons)
                        SideEffect { observed[route] = mode }
                        CompositionLocalProvider(LocalGlassScope provides GlassScope.Queue) {
                            val scope = LocalGlassScope.current
                            SideEffect { nested = scope }
                        }
                    }
                }
            }
        }
        for (scope in listOf(GlassScope.SettingsProfiles, GlassScope.Details, GlassScope.Rewind)) {
            compose.runOnIdle { outer.value = scope }
            compose.runOnIdle {
                assertEquals(GlassAreaMode.Disabled, observed["album/{id}"])
                assertEquals(GlassAreaMode.GlassOnly, observed["settings"])
                assertEquals(GlassScope.Queue, nested)
            }
        }
        compose.runOnIdle { routes.value = listOf("settings", "rewind?year={year}") }
        for (scope in listOf(GlassScope.Rewind, GlassScope.SettingsProfiles)) {
            compose.runOnIdle { outer.value = scope }
            compose.runOnIdle {
                assertEquals(GlassAreaMode.GlassOnly, observed["settings"])
                assertEquals(GlassAreaMode.ContourOnly, observed["rewind?year={year}"])
            }
        }
    }

    private fun verify(position: NavigationBarPosition) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("fullscreen_chrome_${position.name}", context)
        context.preferences.edit().clear().putString(navigationBarPositionKey, position.name).commit()
        val route = mutableStateOf("rewind?year={year}&month={month}&scope={scope}")
        val header = mutableStateOf(88.dp)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppHeaderContentInset provides { header.value },
            ) {
                Box(Modifier.size(360.dp, 600.dp).testTag("scene")) {
                    DestinationChrome(route.value) {
                        val padding = if (route.value == "rewindHome") {
                            Modifier.padding(rememberPageChromeContentPadding())
                        } else Modifier
                        Box(padding.fillMaxSize().testTag("page"))
                    }
                }
            }
        }
        val scene = compose.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
        fun assertFullscreen() {
            assertEquals(scene, compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot)
        }
        assertFullscreen()
        compose.runOnIdle { header.value = 24.dp }
        assertFullscreen()
        compose.runOnIdle { route.value = "rewind" }
        assertFullscreen()
        compose.runOnIdle { route.value = "rewindHome" }
        val home = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        assertEquals(scene.top + 24f, home.top, 0f)
        if (position == NavigationBarPosition.Left) assertTrue(home.left > scene.left)
        else assertEquals(scene.left, home.left, 0f)
    }
}
