package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.miniPlayerButtonOrderKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showMiniPlayerAudioOutputKey
import app.it.fast4x.rimusic.utils.showMiniPlayerLikeKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.glassProfileDefaults
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlassMiniPlayerPreviewTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp),
        uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )
    private val playerState = mutableStateOf<Player?>(null)

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun showPreview() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GlassMiniPlayerPreview(
                    glassProfileDefaults(GlassQuality.Performance).copy(enabled = false),
                    player = playerState.value,
                )
            }
        }
    }

    @Test fun `native controls follow configured order and visibility without changing preferences`() {
        context.preferences.edit()
            .putString(miniPlayerButtonOrderKey, "[\"like\",\"skip_forward\",\"play_pause\",\"skip_back\"]")
            .putBoolean(showMiniPlayerAudioOutputKey, false)
            .putBoolean(showMiniPlayerLikeKey, true)
            .commit()
        val before = context.preferences.all.toMap()
        showPreview()

        val labels = listOf(R.string.miniplayer_button_like, R.string.miniplayer_button_skip_forward,
            R.string.play, R.string.miniplayer_button_skip_back).map(context::getString)
        val controls = composeRule.onAllNodes(SemanticsMatcher("mini-player control") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in labels } == true
        }).fetchSemanticsNodes().sortedBy { it.positionInRoot.x }
        assertEquals(labels, controls.map { it.config[SemanticsProperties.ContentDescription].single() })
        composeRule.onNodeWithContentDescription(context.getString(R.string.miniplayer_button_audio_output)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.unknown_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.unknown_artist)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.notification_pause)).assertIsDisplayed()
        assertEquals(before, context.preferences.all)
    }

    @Test fun `metadata follows player replacement while preview playback stays local`() {
        val first = mockk<Player>(relaxed = true)
        val next = mockk<Player>(relaxed = true)
        val firstListener = slot<Player.Listener>()
        val nextListener = slot<Player.Listener>()
        every { first.addListener(capture(firstListener)) } returns Unit
        every { next.addListener(capture(nextListener)) } returns Unit
        every { first.mediaMetadata } returns MediaMetadata.Builder().setTitle("First title").setArtist("First artist").build()
        every { next.mediaMetadata } returns MediaMetadata.Builder().setTitle("Next title").setArtist("Next artist").build()
        playerState.value = first
        showPreview()
        composeRule.onNodeWithText("First title").assertIsDisplayed()

        composeRule.runOnIdle { playerState.value = next }
        composeRule.onNodeWithText("Next title").assertIsDisplayed()
        composeRule.onNodeWithText("Next artist").assertIsDisplayed()
        verify { first.removeListener(firstListener.captured) }
        composeRule.runOnIdle {
            every { next.mediaMetadata } returns MediaMetadata.Builder().setTitle("Updated title").setArtist("Updated artist").build()
            nextListener.captured.onEvents(next, mockk())
        }
        composeRule.onNodeWithText("Updated title").assertIsDisplayed()
        composeRule.onNodeWithText("Updated artist").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.notification_pause)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.miniplayer_button_skip_forward)).performClick()
        verify(exactly = 0) { first.play(); first.pause(); next.play(); next.pause(); next.seekToNextMediaItem() }
    }
}
