package app.n_zik.android.components.player

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerTimelineType
import app.it.fast4x.rimusic.models.ui.UiMedia
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.GetSeekBar
import app.it.fast4x.rimusic.utils.playerTimelineTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.extensions.audiobar.utils.WaveformResult
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class GetSeekBarBufferLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = ApplicationProvider.getApplicationContext<Application>().preferences

    @After fun cleanup() {
        preferences.edit().clear().commit()
        unmockkAll()
    }

    @Test
    fun `all actual seek bars keep following controls fixed through buffering in native and Glass`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        preferences.edit().clear().putString(playerTimelineTypeKey, PlayerTimelineType.Default.name).commit()
        val player = mockk<Player>(relaxed = true)
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        val cache = mockk<Cache>(relaxed = true)
        every { binder.player } returns player
        every { binder.playerUpdateTrigger } returns MutableStateFlow(0)
        every { binder.downloadCache } returns cache
        every { binder.cache } returns cache
        every { binder.service.currentSongStateDownload } returns MutableStateFlow(Download.STATE_STOPPED)
        // Keep the real VisualizerBar paused so geometry never starts hardware capture.
        every { player.isPlaying } returns false
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.getOrExtractWaveform(any(), any(), any()) } returns
            WaveformResult.Success(List(150) { 50 })
        val duration = mutableStateOf(C.TIME_UNSET)
        val width = mutableStateOf(360.dp)
        val glass = mutableStateOf(false)
        val playing = mutableStateOf(true)
        val media = UiMedia("buffer-layout", "Song", "Artist", 120_000L, false)
        val appearance = Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), CircleShape, CircleShape, CircleShape)
        compose.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalPlayerServiceBinder provides binder,
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass.value),
            ) {
                Column(Modifier.requiredWidth(width.value)) {
                    GetSeekBar({ 0L }, { duration.value }, media.id, media, playing.value, duration.value == C.TIME_UNSET)
                    Box(Modifier.height(40.dp).testTag("following.controls"))
                }
            }
        }
        for (w in listOf(360.dp, 640.dp)) for (type in PlayerTimelineType.entries)
            for (enabled in listOf(false, true)) for (shouldPlay in listOf(false, true)) {
                compose.runOnIdle {
                    width.value = w
                    glass.value = enabled
                    playing.value = shouldPlay
                    duration.value = C.TIME_UNSET
                    preferences.edit().putString(playerTimelineTypeKey, type.name).commit()
                }
                val context = "$w $type glass=$enabled shouldPlay=$shouldPlay"
                val loading = compose.onNodeWithTag("following.controls").fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { duration.value = 120_000L }
                assertEquals("Resolved timeline keeps the following controls fixed: $context", loading,
                    compose.onNodeWithTag("following.controls").fetchSemanticsNode().boundsInRoot)
                compose.runOnIdle { duration.value = C.TIME_UNSET }
                assertEquals("Buffering timeline keeps the following controls fixed: $context", loading,
                    compose.onNodeWithTag("following.controls").fetchSemanticsNode().boundsInRoot)
            }
    }
}
