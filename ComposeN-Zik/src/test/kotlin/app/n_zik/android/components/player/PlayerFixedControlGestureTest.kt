package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerFixedControlGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `fixed transport and time controls retain drag while outside still collapses sheet`() {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        preferences.edit().clear().putBoolean(glassEnabledKey, true).commit()
        val type = mutableStateOf(0)
        val enabled = mutableStateOf(true)
        var collapsedBy = 0f
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = DefaultDarkColorPalette,
                typography = typographyOf(Color.White, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(300.dp).testTag("sheet").draggable(
                    rememberDraggableState { collapsedBy += it }, Orientation.Vertical)) {
                    Box(Modifier.align(Alignment.Center).testTag("control")) {
                        val source = remember { MutableInteractionSource() }
                        when (type.value) {
                            0 -> PlayerTransportIconButton({}, R.drawable.play, Color.White,
                                source, Modifier.size(46.dp), enabled = enabled.value)
                            1 -> PlayerTransportSmallControl("gesture", interactionSource = source) {
                                PlayerTransportIconButton({}, R.drawable.play, Color.White,
                                    source, Modifier.size(26.dp), enabled = enabled.value)
                            }
                            2 -> PlayerSkipTimeGlass(settings = GlassSettings()) {
                                Box(Modifier.size(20.dp))
                            }
                            else -> PlayerInfoIconButton({}, R.drawable.album, Color.White,
                                Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
        for (control in 0..3) {
            compose.runOnIdle { type.value = control; collapsedBy = 0f }
            compose.onNodeWithTag("control").performTouchInput {
                swipe(center, center + Offset(0f, 110f), 400)
            }
            compose.runOnIdle { assertEquals("Control $control", 0f, collapsedBy, .01f) }
            compose.onNodeWithTag("sheet").performTouchInput {
                swipe(Offset(280f, 30f), Offset(280f, 250f), 400)
            }
            compose.runOnIdle { assertTrue("Outside control $control", collapsedBy > 0f) }
        }
        compose.runOnIdle { type.value = 0; enabled.value = false; collapsedBy = 0f }
        compose.onNodeWithTag("control").performTouchInput {
            swipe(center, center + Offset(0f, 110f), 400)
        }
        compose.runOnIdle { assertTrue("Disabled control leaves the gesture available", collapsedBy > 0f) }
    }
}
