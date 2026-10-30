package app.n_zik.android.components.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
