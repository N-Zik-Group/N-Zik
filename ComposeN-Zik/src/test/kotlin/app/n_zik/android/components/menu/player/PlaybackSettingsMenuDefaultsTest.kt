package app.n_zik.android.components.menu.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
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
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.listentogether.listenTogetherGuestLock
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.loudnessBaseGainKey
import app.n_zik.android.utils.playbackPitchKey
import app.n_zik.android.utils.playbackSpeedKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.volumeBoostLevelKey
import app.n_zik.android.utils.volumeNormalizationKey
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
        listenTogetherGuestLock.value = false
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

    @Test
    fun `open grid device volume dialog follows external volume changes`() {
        val label = context.getString(R.string.player_controls_device_volume_title)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val initial = max / 4
        val updated = max * 3 / 4
        val initialLabel = "${(initial.toFloat() / max * 100).toInt()}%"
        val updatedLabel = "${(updated.toFloat() / max * 100).toInt()}%"
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initial, 0)
        show(MenuStyle.Grid)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        val inDialog = hasAnyAncestor(hasTestTag("dialogFrameCard"))
        compose.onNode(hasText(initialLabel) and inDialog, useUnmergedTree = true).assertExists()

        compose.runOnIdle {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, updated, 0)
            context.sendBroadcast(Intent("android.media.VOLUME_CHANGED_ACTION"))
        }

        compose.onNode(hasText(initialLabel) and inDialog, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText(updatedLabel) and inDialog, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `disabled list gain ignores a long press on reset`() {
        seedCustomGain()
        context.preferences.edit().putBoolean(volumeNormalizationKey, false).commit()
        show(MenuStyle.List)
        val titleBounds = compose.onNodeWithText(title).performScrollTo().fetchSemanticsNode().boundsInRoot
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled) and
                SemanticsMatcher("disabled reset icon beside the loudness label") { node ->
                    node.boundsInRoot.right <= titleBounds.left &&
                        kotlin.math.abs(node.boundsInRoot.center.y - titleBounds.center.y) < 2f
                },
            useUnmergedTree = true,
        ).performTouchInput { longClick() }
        assertEquals(-8f, context.preferences.getFloat(loudnessBaseGainKey, Float.NaN), 0f)
    }

    @Test
    fun `disabled grid gain does not open editing or reset`() {
        seedCustomGain()
        context.preferences.edit().putBoolean(volumeNormalizationKey, false).commit()
        show(MenuStyle.Grid)
        scrollGridToGain()
        compose.onNodeWithText(title).assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithText(context.getString(R.string.common_action_reset)).assertDoesNotExist()
        assertEquals(-8f, context.preferences.getFloat(loudnessBaseGainKey, Float.NaN), 0f)
    }

    @Test
    fun `locked guest cannot edit playback parameters in list menu`() {
        seedLockedGuestPlaybackParameters()
        show(MenuStyle.List)
        for (resourceId in listOf(R.string.player_controls_speed_title, R.string.player_controls_pitch_title)) {
            val parameter = context.getString(resourceId)
            compose.onNode(
                hasContentDescription(parameter) and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
                useUnmergedTree = true,
            ).performScrollTo().assertIsNotEnabled().performTouchInput { click() }
        }
        assertGuestPlaybackParametersUnchanged()
    }

    @Test
    fun `locked guest cannot open playback parameter dialogs in grid menu`() {
        seedLockedGuestPlaybackParameters()
        show(MenuStyle.Grid)
        for (resourceId in listOf(R.string.player_controls_speed_title, R.string.player_controls_pitch_title)) {
            val parameter = context.getString(resourceId)
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(parameter))
            compose.onNodeWithText(parameter).assertIsNotEnabled().performTouchInput { click() }
        }
        compose.onNodeWithText(context.getString(R.string.common_action_reset)).assertDoesNotExist()
        assertGuestPlaybackParametersUnchanged()
    }

    private fun seedLockedGuestPlaybackParameters() {
        context.preferences.edit().putFloat(playbackSpeedKey, 1.5f)
            .putFloat(playbackPitchKey, 1.2f).commit()
        listenTogetherGuestLock.value = true
    }

    private fun assertGuestPlaybackParametersUnchanged() {
        assertEquals(1.5f, context.preferences.getFloat(playbackSpeedKey, Float.NaN), 0f)
        assertEquals(1.2f, context.preferences.getFloat(playbackPitchKey, Float.NaN), 0f)
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
