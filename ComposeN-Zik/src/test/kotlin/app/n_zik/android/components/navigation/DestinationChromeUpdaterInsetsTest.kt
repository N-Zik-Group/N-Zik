package app.n_zik.android.components.navigation

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.enums.NavigationBarPosition
import app.n_zik.android.ui.styling.Dimensions
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
class DestinationChromeUpdaterInsetsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `top navigation removes only Updater player reserve`() = verify(NavigationBarPosition.Top)
    @Test fun `bottom navigation removes only Updater player reserve`() = verify(NavigationBarPosition.Bottom)
    @Test fun `floating navigation removes only Updater player reserve`() = verify(NavigationBarPosition.BottomFloating)
    @Test fun `left rail survives removal of Updater player reserve`() = verify(NavigationBarPosition.Left)
    @Test fun `right rail survives removal of Updater player reserve`() = verify(NavigationBarPosition.Right)

    private data class ObservedInsets(
        val bottom: Dp,
        val top: Int,
        val left: Int,
        val right: Int,
        val railLeft: Dp,
        val railRight: Dp,
    )

    private fun verify(position: NavigationBarPosition) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("updater_insets_${position.name}", context)
        context.preferences.edit().clear()
            .putString(navigationBarPositionKey, position.name).commit()
        val playerBottom = mutableStateOf(140.dp)
        val observed = mutableMapOf<String, ObservedInsets>()
        lateinit var view: View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPlayerAwareWindowInsets provides WindowInsets(
                    left = 7.dp, top = 24.dp, right = 11.dp, bottom = playerBottom.value,
                ),
                LocalAppHeaderContentInset provides { 88.dp },
                LocalAppHeaderOverlayInset provides { 88.dp },
            ) {
                // Both entries remain composed, as during a forward or back transition.
                listOf("updater", "settings").forEach { route ->
                    DestinationChrome(route) {
                        val density = LocalDensity.current
                        val insets = LocalPlayerAwareWindowInsets.current
                        val padding = rememberPageChromeContentPadding(topInsetProvider = { 0.dp })
                        val value = ObservedInsets(
                            padding.calculateBottomPadding(), insets.getTop(density),
                            insets.getLeft(density, LayoutDirection.Ltr),
                            insets.getRight(density, LayoutDirection.Ltr),
                            padding.calculateLeftPadding(LayoutDirection.Ltr),
                            padding.calculateRightPadding(LayoutDirection.Ltr),
                        )
                        SideEffect { observed[route] = value }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        val railLeft = if (position == NavigationBarPosition.Left) Dimensions.navigationRailWidth else 0.dp
        val railRight = if (position == NavigationBarPosition.Right) Dimensions.navigationRailWidth else 0.dp
        for ((navigationBottom, cutoutBottom) in listOf(72 to 0, 24 to 0, 24 to 36, 0 to 0)) {
            compose.runOnIdle {
                ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBottom))
                    .setVisible(WindowInsetsCompat.Type.navigationBars(), navigationBottom > 0)
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, 0, cutoutBottom))
                    .setVisible(WindowInsetsCompat.Type.displayCutout(), cutoutBottom > 0)
                    .build())
            }
            for (height in listOf(140.dp, 220.dp, 140.dp)) {
                compose.runOnIdle { playerBottom.value = height }
                compose.runOnIdle {
                    for (route in listOf("updater", "settings")) {
                        val actual = requireNotNull(observed[route])
                        assertEquals("$route bottom clearance", if (route == "updater") {
                            maxOf(navigationBottom, cutoutBottom).dp
                        } else height, actual.bottom)
                        assertEquals("$route inherited top", 24, actual.top)
                        assertEquals("$route inherited left and rail", 7 + railLeft.value.toInt(), actual.left)
                        assertEquals("$route inherited right and rail", 11 + railRight.value.toInt(), actual.right)
                        assertEquals("$route content left rail", railLeft, actual.railLeft)
                        assertEquals("$route content right rail", railRight, actual.railRight)
                    }
                }
            }
        }
    }
}
