package app.n_zik.android.components.musicbrainz.insights

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.SongItem
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
// API 32 exercises the real material without Robolectric's API 33 AGSL limitation.
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsCardTemplateTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalFoundationApi::class)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `ranked songs have no selected tile and highlight only the current media ID`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true).commit()
        var current = MediaItem.Builder().setMediaId("local:elsewhere").build()
        val listeners = mutableListOf<Player.Listener>()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } answers { current }
        every { player.addListener(any()) } answers { listeners.add(firstArg()); Unit }
        every { player.removeListener(any()) } answers { listeners.remove(firstArg()); Unit }
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        val palette = DefaultDarkColorPalette
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalPlayerServiceBinder provides binder,
                LocalAppearance provides Appearance(
                    colorPalette = palette,
                    typography = typographyOf(palette.text, true, false, FontType.Rubik),
                    thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
                ),
            ) {
                InfoCard("Top songs", R.drawable.musical_notes) {
                    listOf("local:first", "local:second").forEachIndexed { index, id ->
                        SongItem(
                            song = Song(id, "Rank ${index + 1}", durationText = "1:00", thumbnailUrl = null),
                            isLiked = false, inPlaylist = false, showThumbnail = false,
                            backgroundColor = Color.Transparent,
                            modifier = Modifier.testTag(id),
                        )
                    }
                }
            }
        }
        fun rowFill(id: String): Color {
            val pixels = compose.onNodeWithTag(id).captureToImage().toPixelMap()
            return pixels[4, pixels.height / 2]
        }
        fun transition(id: String) {
            compose.runOnIdle {
                current = MediaItem.Builder().setMediaId(id).build()
                listeners.toList().forEach { it.onMediaItemTransition(current, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
            }
        }
        val neutral = rowFill("local:first")
        assertEquals(palette.background1, neutral)
        assertEquals(neutral, rowFill("local:second"))
        transition("local:second")
        assertEquals(neutral, rowFill("local:first"))
        assertNotEquals(neutral, rowFill("local:second"))
        transition("local:first")
        assertNotEquals(neutral, rowFill("local:first"))
        assertEquals(neutral, rowFill("local:second"))
        transition("local:elsewhere")
        assertEquals(neutral, rowFill("local:first"))
        assertEquals(neutral, rowFill("local:second"))
    }

    @Test fun `Insights uses the Settings header surface spacing and live theme modes`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        val store = GlassStore(context)
        var palette by mutableStateOf(DefaultDarkColorPalette)
        var roundness by mutableStateOf(12.dp)
        var clicks = 0
        var showSettings by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(roundness)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette,
                typography = typographyOf(palette.text, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(Modifier.size(280.dp, 144.dp).background(palette.background0).testTag("template")) {
                    if (!showSettings) {
                        InfoCard(title = "Insights", icon = R.drawable.information) {
                            Text("Existing content", Modifier.clickable { clicks++ })
                        }
                    } else {
                        SettingsSectionCard(title = "Insights", icon = R.drawable.information, content = {
                            Column { Text("Existing content", Modifier.clickable { clicks++ }) }
                        })
                    }
                }
            }
        }
        fun assertTemplatePixels() {
            // Native rasterization rounds edge coverage by screen position, so reuse one slot.
            compose.runOnIdle { showSettings = false }
            val insights = compose.onNodeWithTag("template").captureToImage().toPixelMap()
            compose.runOnIdle { showSettings = true }
            val settings = compose.onNodeWithTag("template").captureToImage().toPixelMap()
            assertEquals(settings.width, insights.width)
            assertEquals(settings.height, insights.height)
            for (y in 0 until settings.height) for (x in 0 until settings.width) {
                assertEquals("Card pixel ($x, $y)", settings[x, y], insights[x, y])
            }
            compose.runOnIdle { showSettings = false }
        }
        assertTemplatePixels()
        compose.onAllNodesWithText("Existing content")[0].performClick()
        assertEquals(1, clicks)
        compose.runOnIdle { context.preferences.edit().putBoolean(glassEnabledKey, true).commit() }
        GlassAreaMode.entries.forEach { mode ->
            compose.runOnIdle { store.setAreaMode(GlassArea.SettingsCards, mode) }
            assertTemplatePixels()
        }
        compose.runOnIdle {
            palette = PureBlackColorPalette
            roundness = 24.dp
        }
        assertTemplatePixels()
        compose.runOnIdle {
            palette = DefaultLightColorPalette
            context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        }
        assertTemplatePixels()
        compose.onAllNodesWithText("Existing content")[0].performClick()
        assertEquals(2, clicks)
    }
}
