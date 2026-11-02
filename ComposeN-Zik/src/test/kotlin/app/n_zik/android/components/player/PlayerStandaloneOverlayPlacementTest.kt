package app.n_zik.android.components.player

import android.Manifest
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.lyricsTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.romanizationEnabledKey
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showVisualizerButtonsKey
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.visualizerEnabledKey
import app.n_zik.android.Dependencies
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.MainApplication
import app.n_zik.android.components.glass.GlassOverlayHost
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.player.lyrics.LyricsScreen
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.LyricsTable
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.extensions.nextvisualizer.views.NextVisualizer
import app.n_zik.android.models.Lyrics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import utils.ShadowVisualizerCaptureSizeRange
import utils.VisualizerHelper
import utils.VisualizerSnapshot
import views.VisualizerView
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w900dp-h900dp",
    shadows = [ShadowVisualizerCaptureSizeRange::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerStandaloneOverlayPlacementTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val previousSnapshotProvider = VisualizerHelper.snapshotProvider

    @Before fun setUp() {
        setActiveProfile("standalone-overlay-test", context)
        context.preferences.edit().clear()
            .putBoolean(showlyricsthumbnailKey, true)
            .putBoolean(showvisthumbnailKey, true)
            .putBoolean(visualizerEnabledKey, true)
            .putBoolean(showVisualizerButtonsKey, false)
            .putBoolean(romanizationEnabledKey, false)
            .putString(lyricsTypeKey, LyricsType.Unsynced.name).commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        val table = mockk<LyricsTable>()
        every { table.findAllBySongId("standalone-scene") } returns flowOf(listOf(
            Lyrics("standalone-scene", LyricsType.Unsynced.name, "Actual standalone lyrics", isEdited = true),
        ))
        mockkObject(Database)
        every { Database.lyricsTable } returns table
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Keep the real Android renderer, replacing only the hardware capture source.
        VisualizerHelper.snapshotProvider = {
            VisualizerSnapshot(ByteArray(1024), ByteArray(1024), 44_100_000)
        }
    }

    @After fun tearDown() {
        VisualizerHelper.snapshotProvider = previousSnapshotProvider
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
        unmockkAll()
    }

    @Test fun `real lyrics override saved cover placement in native and Glass`() {
        verifyPlacement(visualizer = false)
    }

    @Test fun `permission granted visualizer forwards standalone placement to its real renderer`() {
        verifyPlacement(visualizer = true)
    }

    private fun verifyPlacement(visualizer: Boolean) {
        val glass = mutableStateOf(false)
        val onCover = mutableStateOf(false)
        val landscape = mutableStateOf(false)
        var rootView: View? = null
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                    input: I, options: ActivityOptionsCompat?) {
                    error("Granted microphone permission must reach the renderer without a request")
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            rootView = LocalView.current.rootView
            val settings = GlassSettings(enabled = glass.value)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalPlayerServiceBinder provides null,
                LocalGlassPreviewSettings provides settings,
                LocalAppearance provides Appearance(palette,
                    typographyOf(palette.text, true, false, FontType.Rubik),
                    CircleShape, CircleShape, CircleShape),
            ) {
                Box(Modifier.size(if (landscape.value) 600.dp else 320.dp, 360.dp).testTag("actual.host")) {
                    GlassOverlayHost(active = true, settings = settings) {
                        if (visualizer) NextVisualizer(true, onCover = onCover.value, fadeHosted = true)
                        else LyricsScreen(
                            mediaId = "standalone-scene", isDisplayed = true, onDismiss = {},
                            size = 360.dp, mediaMetadataProvider = { MediaMetadata.EMPTY },
                            durationProvider = { 120_000L }, ensureSongInserted = {},
                            clickLyricsText = false, isLandscape = landscape.value,
                            onCover = onCover.value, fadeHosted = true,
                        )
                    }
                }
            }
        }
        settle()
        if (!visualizer) {
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && runCatching {
                    compose.onNodeWithText("Actual standalone lyrics").assertIsDisplayed()
                }.isFailure) settle()
            compose.onNodeWithText("Actual standalone lyrics").assertIsDisplayed()
        }
        for (wide in listOf(false, true)) for (enabled in listOf(false, true)) {
            compose.runOnIdle { landscape.value = wide; glass.value = enabled; onCover.value = false }
            settle()
            val host = compose.onNodeWithTag("actual.host").fetchSemanticsNode().boundsInRoot
            val standalone = sceneBounds()
            val reserve = with(compose.density) { (if (enabled) 20.dp else 0.dp).toPx() }
            assertEquals("Actual standalone canvas reserves the information gap", host.bottom - reserve, standalone.bottom, 1f)
            assertEquals(host.width, standalone.width, 1f)
            val standaloneSettingsInset = standalone.bottom - settingsBottom(standalone.right)
            if (visualizer) compose.runOnIdle {
                val canvas = requireNotNull(findVisualizer(requireNotNull(rootView)))
                assertEquals(standalone.width, canvas.width.toFloat(), 1f)
                assertEquals(standalone.height, canvas.height.toFloat(), 1f)
            }
            compose.runOnIdle { onCover.value = true }
            settle()
            // AndroidView and AnimatedVisibility can schedule placement after the first idle.
            // Record subsequent settled frames to distinguish deferred placement from a wrong inset.
            val embeddedInsetFrames = if (visualizer) List(3) {
                settle()
                val frame = sceneBounds()
                frame.bottom - settingsBottom(frame.right)
            } else emptyList()
            val embedded = sceneBounds()
            assertEquals("Embedded canvas retains its full artwork viewport", host.bottom, embedded.bottom, 1f)
            val embeddedSettingsInset = embedded.bottom - settingsBottom(embedded.right)
            assertEquals("Only the circular on-cover scene reserves 16dp below settings: " +
                "visualizer=$visualizer landscape=$wide glass=$enabled " +
                "standaloneInset=$standaloneSettingsInset embeddedInset=$embeddedSettingsInset " +
                "settledEmbeddedFrames=$embeddedInsetFrames",
                with(compose.density) { 16.dp.toPx() }, embeddedSettingsInset - standaloneSettingsInset, 1f)
            assertTrue(context.preferences.getBoolean(showlyricsthumbnailKey, false))
            assertTrue(context.preferences.getBoolean(showvisthumbnailKey, false))
        }
    }

    private fun sceneBounds() = compose.onNodeWithTag("glass.overlay.scene.background", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun settingsBottom(right: Float): Float {
        val buttons = compose.onAllNodes(hasClickAction() and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        val settings = buttons.filter {
            it.center.x > right - with(compose.density) { 65.dp.toPx() } && it.height > 0f
        }
        assertEquals("The trailing settings button must be unambiguous among $buttons", 1, settings.size)
        return settings.single().bottom
    }

    private fun settle() {
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        compose.waitForIdle()
    }

    private fun findVisualizer(view: View): VisualizerView? {
        if (view is VisualizerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findVisualizer(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
