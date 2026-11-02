package app.n_zik.android.components.player

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerControlsType
import app.it.fast4x.rimusic.enums.PlayerInfoType
import app.it.fast4x.rimusic.enums.PlayerPlayButtonType
import app.it.fast4x.rimusic.enums.PlayerTimelineSize
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.screens.player.Controls
import app.it.fast4x.rimusic.ui.screens.player.PlayerSheetState
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerControlsTypeKey
import app.it.fast4x.rimusic.utils.playerInfoTypeKey
import app.it.fast4x.rimusic.utils.playerPlayButtonTypeKey
import app.it.fast4x.rimusic.utils.playerTimelineSizeKey
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalPlayerSheetState
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.FormatTable
import app.n_zik.android.core.database.SongTable
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w500dp-h1200dp-port")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class)
class PlayerPresetSceneGeometryTest {
    @get:Rule val compose = createComposeRule()
    private val player = mockk<Player>(relaxed = true)
    private val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @Before fun setup() {
        preferences.edit().clear().putBoolean("buttonzoomout", false).commit()
        every { binder.player } returns player
        every { player.currentMediaItem } returns null
        val cache = mockk<Cache>(relaxed = true)
        every { binder.downloadCache } returns cache
        every { binder.cache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        val songs = mockk<SongTable>()
        val formats = mockk<FormatTable>()
        every { songs.isLiked(any()) } returns flowOf(false)
        every { songs.likeState(any()) } returns flowOf(null)
        every { songs.findById(any()) } returns flowOf(null)
        every { formats.findBySongId(any()) } returns flowOf(null)
        mockkObject(Database, WaveformExtractor)
        every { Database.songTable } returns songs
        every { Database.formatTable } returns formats
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
    }

    @After fun cleanup() {
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test fun `preset1 compact Essential keeps metadata and transport together when portrait grows`() =
        verifyScene(expanded = true, type = PlayerType.Essential, timeline = PlayerTimelineType.ThinBar)

    @Test fun `preset6 compact Essential keeps metadata and transport together when portrait grows`() =
        verifyScene(expanded = true, type = PlayerType.Essential, timeline = PlayerTimelineType.Wavy)

    @Test fun `preset4 collapsed Modern assigns spare space below metadata instead of above it`() =
        verifyScene(expanded = false, type = PlayerType.Modern, timeline = PlayerTimelineType.AudioWaves,
            controlsType = PlayerControlsType.Modern, playButton = PlayerPlayButtonType.Disabled)

    @Test fun `preset5 collapsed Modern assigns spare space below metadata instead of above it`() =
        verifyScene(expanded = false, type = PlayerType.Modern, timeline = PlayerTimelineType.PinBar,
            playButton = PlayerPlayButtonType.Square)

    private fun verifyScene(
        expanded: Boolean,
        type: PlayerType,
        timeline: PlayerTimelineType,
        controlsType: PlayerControlsType = PlayerControlsType.Essential,
        playButton: PlayerPlayButtonType = PlayerPlayButtonType.CircularRibbed,
    ) {
        preferences.edit().putString(playerTypeKey, type.name)
            .putString(playerInfoTypeKey, PlayerInfoType.Essential.name)
            .putString(playerControlsTypeKey, controlsType.name)
            .putString(playerPlayButtonTypeKey, playButton.name)
            .putString(playerTimelineTypeKey, timeline.name)
            .putString(playerTimelineSizeKey, PlayerTimelineSize.Biggest.name)
            .putBoolean(showlyricsthumbnailKey, false).putBoolean(showthumbnailKey, true)
            .putBoolean(glassEnabledKey, false).commit()
        val height = mutableStateOf(900.dp)
        val nav = mockk<androidx.navigation.NavController>(relaxed = true)
        val sheet = mockk<PlayerSheetState>(relaxed = true)
        every { sheet.progress } returns 1f
        val media = UiMedia("preset-scene", "Preset title", "Preset artist", 246_000L, true)
        val priority = playerPortraitPrioritizesArtwork(true, type)
        val weighted = playerPortraitUsesWeightedInformation(expanded, false, type, false, false)
        compose.setContent { AppearanceProvider {
            CompositionLocalProvider(LocalPlayerServiceBinder provides binder, LocalPlayerSheetState provides sheet,
                LocalPlayerTransportGeometry provides remember { PlayerTransportGeometry() }) {
                PlayerGlassBudgetLayout(enabled = false, prioritizeArtwork = priority, expandInformation = weighted,
                    artworkVerticalPadding = if (priority) 16.dp else 0.dp,
                    modifier = Modifier.requiredSize(448.dp, height.value).playerTransportGeometryHost()
                        .testTag("preset.scene"),
                    header = { Box(Modifier.size(448.dp, 50.dp)) },
                    artwork = {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("preset.artworkSlot"),
                            contentAlignment = Alignment.Center) {
                            val edge = minOf(maxWidth, maxHeight)
                            Box(Modifier.size(edge).testTag("preset.cover"))
                        }
                    }, information = {
                        PlayerInformationGlassBlock {
                            PlayerPortraitInformationLayout(priority, weighted,
                                above = { Spacer(Modifier.height(10.dp)) }, controls = {
                                    Controls(navController = nav, onCollapse = {}, onBlurScaleChange = {},
                                        expandedplayer = expanded, titleExpanded = true, timelineExpanded = true,
                                        controlsExpanded = true, isShowingLyrics = false, media = media, mediaId = media.id,
                                        title = media.title, artist = media.artist, artistIds = null, albumId = null,
                                        shouldBePlaying = false, isBuffering = false, position = { 2_000L },
                                        duration = { 246_000L }, isExplicit = false,
                                        dynamicColorPalette = DefaultDarkColorPalette,
                                        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth())
                                }, below = {
                                    val toolbar = rememberPlayerActionToolbarLayout(GlassSettings(enabled = false))
                                    Column(Modifier.fillMaxWidth().requiredHeight(toolbar.hostHeight(50.dp))
                                        .testTag("preset.actions"), verticalArrangement = Arrangement.SpaceAround) {
                                        PlayerActionToolbar(listOf(PlayerActionSlot("menu") {
                                            PlayerActionIcon(app.n_zik.android.R.drawable.play, Color.White, {},
                                                Modifier.size(24.dp).testTag("preset.menu"))
                                        }), true, settings = GlassSettings(enabled = false), layout = toolbar,
                                            landscape = false)
                                    }
                                })
                        }
                    })
            }
        } }
        val firstInfo = bounds("player.informationRegion.info")
        val firstTimeline = bounds("player.informationRegion.timeline")
        val firstTransport = bounds("player.transport.row")
        val firstCover = bounds("preset.cover")
        compose.runOnIdle { height.value = 1100.dp }
        val info = bounds("player.informationRegion.info")
        val transport = bounds("player.transport.row")
        val actions = bounds("preset.actions")
        val delta = 200f * compose.density.density
        assertTrue("Real metadata and transport must survive natural measurement", info.height > 0 && transport.height > 0)
        assertEquals("Toolbar remains at the bottom of the complete scene", bounds("preset.scene").bottom, actions.bottom, 1f)
        assertTrue("Transport clears the visible toolbar", transport.bottom <= bounds("preset.menu").top + 1f)
        if (expanded) {
            assertEquals("Compact metadata follows bottom actions, not artwork", delta, info.top - firstInfo.top, 2f)
            assertEquals("Compact timeline follows its metadata", delta,
                bounds("player.informationRegion.timeline").top - firstTimeline.top, 2f)
            assertEquals("Compact transport keeps its original grouping", delta, transport.top - firstTransport.top, 3f)
            assertEquals("Artwork stays centered in the growing upper slot", delta / 2,
                bounds("preset.cover").center.y - firstCover.center.y, 2f)
        } else {
            assertEquals("Collapsed metadata remains beside the square cover", firstInfo.top, info.top, 2f)
            assertEquals("Collapsed cover size remains stable", firstCover.height, bounds("preset.cover").height, 1f)
            assertTrue("Extra height belongs between timeline and actions", transport.top > firstTransport.top + delta * .25f)
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }
}
