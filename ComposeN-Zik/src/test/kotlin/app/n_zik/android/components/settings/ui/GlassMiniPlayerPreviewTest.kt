package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
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
import org.robolectric.annotation.GraphicsMode

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

    @Test
    @Config(sdk = [32])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `preview controls sample mini player film and repaint live parent changes`() {
        val stageColor = mutableStateOf(Color.Red)
        val filmColor = mutableStateOf(Color.Green)
        val film = mutableStateOf(.5f)
        val scale = mutableStateOf(.5f)
        var previewView: View? = null
        composeRule.setContent {
            val stage = rememberLayerBackdrop()
            val view = LocalView.current
            SideEffect { previewView = view }
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalAppearance provides appearance.copy(
                    colorPalette = DefaultDarkColorPalette.copy(background2 = filmColor.value, text = Color.Transparent),
                    typography = typographyOf(Color.Transparent, true, false, FontType.Rubik),
                ),
                LocalGlassBackdrop provides stage,
            ) {
                Box(Modifier.size(360.dp * scale.value, 100.dp * scale.value)
                    .testTag("settings.glass.previewStage"), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(stage)
                        .background(Brush.horizontalGradient(listOf(stageColor.value, Color.Cyan))))
                    Box(Modifier.requiredWidth(360.dp).height(100.dp)
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
                        GlassMiniPlayerPreview(
                            GlassSettings(enabled = true, film = film.value, blur = 0f, rim = 0f,
                                tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f),
                            player = null,
                        )
                    }
                }
            }
        }
        fun checkRenderedParent() {
            val labels = listOf(R.string.player_action_previous, R.string.player_action_play,
                R.string.player_action_next, R.string.player_mini_button_audio_output)
            val controls = labels.map { label -> context.getString(label) to
                composeRule.onNodeWithContentDescription(context.getString(label)).fetchSemanticsNode().boundsInRoot }
            val body = composeRule.onNodeWithTag("settings.glass.miniPlayer").fetchSemanticsNode().boundsInRoot
            val stage = composeRule.onNodeWithTag("settings.glass.previewStage").fetchSemanticsNode().boundsInRoot
            composeRule.runOnIdle {
                val view = requireNotNull(previewView)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                // Capture the real view: a subnode capture can omit an exported sibling material.
                view.draw(Canvas(bitmap))
                try {
                    val samples = controls.joinToString { (label, bounds) ->
                        "$label $bounds center=${Color(bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt()))}" +
                            " top=${Color(bitmap.getPixel(bounds.center.x.toInt(), (bounds.top + bounds.height * .16f).toInt()))}"
                    }
                    fun assertPixel(label: String, filmPasses: Int, x: Float, y: Float) {
                        val source = Color(bitmap.getPixel(x.toInt(), (stage.bottom - 2f).toInt()))
                        val expected = (0 until filmPasses).fold(source) { background, _ ->
                            filmColor.value.copy(alpha = film.value).compositeOver(background)
                        }
                        val actual = Color(bitmap.getPixel(x.toInt(), y.toInt()))
                        assertEquals("$label red: $actual; $samples", expected.red, actual.red, .04f)
                        assertEquals("$label green: $actual", expected.green, actual.green, .04f)
                        assertEquals("$label blue: $actual", expected.blue, actual.blue, .04f)
                    }
                    assertPixel("mini player body", 1, body.center.x, body.top + body.height * .9f)
                    controls.forEach { (label, bounds) ->
                        assertPixel(label, 2, bounds.center.x, bounds.center.y)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        checkRenderedParent()
        composeRule.runOnIdle { stageColor.value = Color.Blue }
        checkRenderedParent()
        composeRule.runOnIdle { filmColor.value = Color.Yellow; film.value = .25f }
        checkRenderedParent()
        composeRule.runOnIdle { scale.value = .75f }
        checkRenderedParent()
        composeRule.runOnIdle { film.value = 0f }
        checkRenderedParent()
    }

    @Test fun `native controls follow configured order and visibility without changing preferences`() {
        context.preferences.edit()
            .putString(miniPlayerButtonOrderKey, "[\"like\",\"skip_forward\",\"play_pause\",\"skip_back\"]")
            .putBoolean(showMiniPlayerAudioOutputKey, false)
            .putBoolean(showMiniPlayerLikeKey, true)
            .commit()
        val before = context.preferences.all.toMap()
        showPreview()

        val labels = listOf(R.string.player_action_like, R.string.player_action_next,
            R.string.player_action_play, R.string.player_action_previous).map(context::getString)
        val controls = composeRule.onAllNodes(SemanticsMatcher("mini-player control") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it in labels } == true
        }).fetchSemanticsNodes().sortedBy { it.positionInRoot.x }
        assertEquals(labels, controls.map { it.config[SemanticsProperties.ContentDescription].single() })
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_mini_button_audio_output)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.metadata_unknown_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.metadata_unknown_artist)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_action_play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_notification_pause)).assertIsDisplayed()
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
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_action_play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_notification_pause)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.player_action_next)).performClick()
        verify(exactly = 0) { first.play(); first.pause(); next.play(); next.pause(); next.seekToNextMediaItem() }
    }
}
