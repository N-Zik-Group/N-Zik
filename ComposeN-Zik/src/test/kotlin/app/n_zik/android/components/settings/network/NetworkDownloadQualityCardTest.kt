package app.n_zik.android.components.settings.network

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.compose.rememberNavController
import app.n_zik.android.components.tab.Search
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.it.fast4x.rimusic.enums.AudioQualityFormat
import app.it.fast4x.rimusic.enums.ImageQualityFormat
import app.it.fast4x.rimusic.utils.audioQualityFormatKey
import app.it.fast4x.rimusic.utils.imageQualityFormatKey
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NetworkDownloadQualityCardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before fun resetQualityPreferences() {
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
    }

    @After fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile("default", context)
        unmockkAll()
    }

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) { block() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun currentValue(value: String) = context.getString(R.string.settings_current_value, value)

    private fun awaitTextCount(text: String, count: Int = 1) {
        // Flush snapshot invalidations before driving the frozen clock and Android looper.
        // Wait for the observable result rather than assuming a fixed frame count is enough.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Snapshot.sendApplyNotifications()
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size == count
        }
    }

    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
    @Test fun `reset-only system host shows a single restart action`() {
        context.preferences.edit().putString(audioQualityFormatKey, AudioQualityFormat.High.name).apply()
        content {
            NetworkSettingsContent(Search(initialVisible = false), rememberNavController(),
                selectedCards = setOf(R.string.settings_reset))
        }
        composeRule.onNodeWithText(context.getString(R.string.settings_restore_default_settings)).performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitTextCount(context.getString(R.string.restart_service))
        assertEquals(AudioQualityFormat.Auto.name, context.preferences.getString(audioQualityFormatKey, null))
        composeRule.onAllNodesWithText(context.getString(R.string.restart_service)).assertCountEquals(1)
    }

    @Test fun `thematic network quality excludes download controls`() {
        content {
            NetworkQualityCard("", remember { mutableStateOf(false) },
                remember { mutableStateOf(false) }, ImageCacheFactory.NetworkQuality.LOW,
                includeDownloadQuality = false) {}
        }
        composeRule.onNodeWithText(context.getString(R.string.audio_quality_format)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.image_quality_format)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.download_quality_format)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.update_downloads)).assertDoesNotExist()
    }

    @OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
    @Test fun `thematic host never duplicates quality rows in separate status cards`() {
        content {
            NetworkSettingsContent(Search(initialVisible = false), rememberNavController(),
                selectedCards = setOf(R.string.quality, R.string.settings_download_quality,
                    R.string.network_status, R.string.settings_download_status))
        }
        composeRule.onAllNodesWithText(context.getString(R.string.audio_quality_format)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.image_quality_format)).assertCountEquals(1)
        composeRule.onAllNodesWithText(context.getString(R.string.download_quality_format)).assertCountEquals(1)
        composeRule.onNodeWithText(context.getString(R.string.network_status)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.settings_download_status)).assertDoesNotExist()
    }

    @Test fun `legacy quality card retains both download controls`() {
        content {
            NetworkQualityCard(context.getString(R.string.download_quality_format),
                remember { mutableStateOf(false) }, remember { mutableStateOf(false) },
                ImageCacheFactory.NetworkQuality.LOW) {}
        }
        composeRule.onNodeWithText(context.getString(R.string.download_quality_format)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.update_downloads)).assertExists()
    }

    @Test fun `download quality card preserves settings while exposing both controls`() {
        val before = context.preferences.all.toMap()
        var restartRequests = 0
        content {
            NetworkDownloadQualityCard("", ImageCacheFactory.NetworkQuality.LOW) { restartRequests++ }
        }
        composeRule.onNodeWithText(context.getString(R.string.download_quality_format)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.update_downloads)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.audio_quality_format)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.image_quality_format)).assertDoesNotExist()
        assertEquals(before, context.preferences.all)
        assertEquals(0, restartRequests)
    }

    @Test fun `network automatic values follow detected quality without writing preferences`() {
        val quality = mutableStateOf(ImageCacheFactory.NetworkQuality.LOW)
        val before = context.preferences.all.toMap()
        content {
            NetworkQualityCard("", remember { mutableStateOf(false) },
                remember { mutableStateOf(false) }, quality.value, includeDownloadQuality = false) {}
        }
        val lowText = currentValue(context.getString(R.string.audio_quality_auto_fmt, context.getString(R.string.network_quality_low)))
        val highText = currentValue(context.getString(R.string.audio_quality_auto_fmt, context.getString(R.string.network_quality_high)))
        composeRule.onAllNodesWithText(lowText).assertCountEquals(2)
        composeRule.runOnIdle { quality.value = ImageCacheFactory.NetworkQuality.HIGH }
        awaitTextCount(highText, count = 2)
        composeRule.onAllNodesWithText(highText).assertCountEquals(2)
        composeRule.onAllNodesWithText(lowText).assertCountEquals(0)
        assertEquals(before, context.preferences.all)
    }

    @Test fun `explicit network values stay selected despite detected low quality`() {
        context.preferences.edit()
            .putString(audioQualityFormatKey, AudioQualityFormat.High.name)
            .putString(imageQualityFormatKey, ImageQualityFormat.Medium.name)
            .apply()
        content {
            NetworkQualityCard("", remember { mutableStateOf(false) },
                remember { mutableStateOf(false) }, ImageCacheFactory.NetworkQuality.LOW,
                includeDownloadQuality = false) {}
        }
        composeRule.onNodeWithText(currentValue(context.getString(R.string.audio_quality_format_high))).assertExists()
        composeRule.onNodeWithText(currentValue(context.getString(R.string.audio_quality_format_medium))).assertExists()
        composeRule.onNodeWithText(currentValue(context.getString(R.string.audio_quality_auto_fmt,
            context.getString(R.string.network_quality_low)))).assertDoesNotExist()
    }

    @Test fun `download automatic status shares its selector and selection retains restart action`() {
        var restartRequests = 0
        content {
            NetworkDownloadQualityCard("", ImageCacheFactory.NetworkQuality.MEDIUM) { restartRequests++ }
        }
        composeRule.onNodeWithText(currentValue(context.getString(R.string.audio_quality_auto_fmt,
            context.getString(R.string.network_quality_medium)))).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.download_quality_format))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitTextCount(context.getString(R.string.audio_quality_automatic))
        composeRule.onNodeWithText(context.getString(R.string.audio_quality_automatic)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.audio_quality_format_high))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        awaitTextCount(currentValue(context.getString(R.string.audio_quality_format_high)))
        assertEquals(DownloadQualityFormat.High.name, context.preferences.getString(downloadQualityFormatKey, null))
        assertEquals(1, restartRequests)
        composeRule.onNodeWithText(currentValue(context.getString(R.string.audio_quality_format_high))).assertExists()
    }
}
