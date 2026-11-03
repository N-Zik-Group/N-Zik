package app.n_zik.android.components.menu.player

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.loudnessBaseGainKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.volumeBoostLevelKey
import app.it.fast4x.rimusic.utils.volumeNormalizationKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackSettingsMenuDefaultsTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null
    private val title get() = context.getString(R.string.settings_loudness_base_gain)

    @Before
    fun setUp() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("playback_menu_defaults_test", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true)
            .putBoolean(volumeNormalizationKey, true)
            .commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @After
    fun tearDown() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `list missing gain displays the runtime default without persisting it`() {
        show(MenuStyle.List)
        compose.onNodeWithText(title).performScrollTo()
        compose.onNodeWithText("5.00 dB").assertExists()
        assertFalse(context.preferences.contains(loudnessBaseGainKey))
    }

    @Test
    fun `grid missing gain displays the runtime default without persisting it`() {
        show(MenuStyle.Grid)
        scrollGridToGain()
        compose.onNodeWithText("5.00 dB").assertExists()
        assertFalse(context.preferences.contains(loudnessBaseGainKey))
    }

    @Test
    fun `list long press resets gain to five and preserves volume boost`() {
        seedCustomGain()
        show(MenuStyle.List)
        val titleBounds = compose.onNodeWithText(title).performScrollTo().fetchSemanticsNode().boundsInRoot
        // Rows without semantics are flattened into the menu column. Locate the icon
        // beside this title by geometry instead of matching their shared parent.
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick) and
                SemanticsMatcher("reset icon beside the loudness label") { node ->
                    node.boundsInRoot.right <= titleBounds.left &&
                        kotlin.math.abs(node.boundsInRoot.center.y - titleBounds.center.y) < 2f
                },
            useUnmergedTree = true,
        ).performTouchInput { longClick() }
        assertReset()
    }

    @Test
    fun `grid dialog reset restores gain to five and preserves volume boost`() {
        seedCustomGain()
        show(MenuStyle.Grid)
        scrollGridToGain()
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText(context.getString(R.string.common_action_reset)).performClick()
        assertReset()
    }

    private fun seedCustomGain() {
        context.preferences.edit().putFloat(loudnessBaseGainKey, -8f)
            .putFloat(volumeBoostLevelKey, 3f).commit()
    }

    private fun assertReset() {
        compose.runOnIdle {
            assertEquals(5f, context.preferences.getFloat(loudnessBaseGainKey, Float.NaN), 0f)
            assertEquals(3f, context.preferences.getFloat(volumeBoostLevelKey, Float.NaN), 0f)
        }
        compose.onNodeWithText("5.00 dB").assertExists()
    }

    private fun scrollGridToGain() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(title))
    }

    private fun show(style: MenuStyle) {
        val menu = PlaybackSettingsMenu.create(
            binder = mockk<PlayerServiceModern.Binder>(relaxed = true),
            menuState = MenuState(),
            styleState = mutableStateOf(style),
            onDismiss = {},
            onBlurScaleChange = {},
        )
        val shape = RoundedCornerShape(16.dp)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                menu.MenuComponent()
            }
        }
    }
}
