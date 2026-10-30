package app.n_zik.android.components.navigation.header

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.components.glass.GlassActionGroupItem
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassMenuEntry
import app.n_zik.android.components.glass.GlassMenuTriggerState
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassVerticalActionGroup
import app.n_zik.android.components.glass.glassMenuReturnAnchor
import app.n_zik.android.components.navigation.pageOwnsNavigationOverlay
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideHeaderMenuTriggerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `native closing menu follows replacement header when Back moves profile`() = verifyRouteChange(false)

    @Test fun `Glass closing menu follows replacement header when Back moves profile`() = verifyRouteChange(true)

    private fun verifyRouteChange(glass: Boolean) {
        val shared = GlassMenuTriggerState()
        val route = mutableStateOf("home")
        val seen = mutableListOf<GlassMenuTriggerState>()
        val resolvedFace = ProfileFace("My photo", FaceAvatar.Photo("profile-photo.png"))
        val faces = mutableListOf<ProfileFace?>()
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(
                    LocalSideHeaderMenuTrigger provides shared,
                    LocalHeaderProfileFace provides resolvedFace,
                ) {
                    // Keep an outgoing home composition alive just as NavHost transitions do.
                    for (page in listOf("home", "settings")) key(page) {
                        if (pageOwnsNavigationOverlay(page, route.value)) {
                            Column {
                                if (sideHeaderShowsBack(page)) Box(Modifier.size(48.dp))
                                val trigger = rememberHeaderMenuTrigger(vertical = true)
                                val face = LocalHeaderProfileFace.current
                                SideEffect { seen.add(trigger); faces.add(face) }
                                GlassVerticalActionGroup(
                                    GlassSettings(enabled = glass, quality = GlassQuality.Performance),
                                    GlassArea.Header, trailingTrigger = trigger,
                                ) {
                                    GlassActionGroupItem({}) { }
                                    GlassActionGroupItem({}, menuTrigger = trigger) { Box(Modifier.size(24.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        var opening = Rect.Zero
        compose.runOnIdle { opening = shared.bounds }
        val entry = GlassMenuEntry(Any(), opening, GlassSettings(enabled = glass), {}, {},
            visible = false, trigger = shared, shape = RectangleShape)
        compose.runOnIdle { route.value = "settings" }
        compose.waitForIdle()
        var destination = Rect.Zero
        compose.runOnIdle {
            destination = shared.bounds
            assertNotEquals(opening, destination)
            assertEquals(opening.top + 48f * compose.density.density, destination.top, .1f)
            assertEquals(destination, glassMenuReturnAnchor(entry))
            seen.forEach { assertSame(shared, it) }
            faces.forEach { assertSame(resolvedFace, it) }
        }
        // An outgoing page must not remeasure the shared source back to its old location.
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(destination, shared.bounds)
            route.value = "home"
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(opening, shared.bounds)
            assertEquals(opening, glassMenuReturnAnchor(entry))
            seen.forEach { assertSame(shared, it) }
            faces.forEach { assertSame(resolvedFace, it) }
        }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        ), content = content)
    }
}
