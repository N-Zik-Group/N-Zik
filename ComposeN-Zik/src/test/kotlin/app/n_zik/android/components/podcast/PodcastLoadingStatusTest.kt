package app.n_zik.android.components.podcast

import android.app.Application
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.LocalGlassScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PodcastLoadingStatusTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `loading summary switches to the actual localized episode count`() {
        val loadedCount = mutableIntStateOf(0)
        val resources = RuntimeEnvironment.getApplication().resources
        val loading = resources.getString(R.string.common_loading_message)
        compose.setContent { Text(podcastEpisodeLoadingLabel(loadedCount.intValue)) }

        compose.onNodeWithText(loading).assertIsDisplayed()
        for (count in listOf(1, 17)) {
            compose.runOnIdle { loadedCount.intValue = count }
            val summary = resources.getQuantityString(
                R.plurals.podcast_episode_count_with_duration, count, count, loading,
            )
            compose.onNodeWithText(summary).assertIsDisplayed()
        }
    }

    @Test fun `playlist actions and retry each invoke once in Material and Glass`() {
        val glassEnabled = mutableStateOf(false)
        var retries = 0
        var playlistActions = 0
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalGlassScope provides GlassScope.Details,
                LocalGlassPreviewSettings provides GlassSettings(
                    enabled = glassEnabled.value,
                    quality = GlassQuality.Performance,
                ),
            ) {
                PodcastLoadingStatus(
                    PodcastPageStatus.Error,
                    isOnline = false,
                    onPlaylistActions = { playlistActions++ },
                ) { retries++ }
            }
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glassEnabled.value = enabled }
            compose.onNodeWithText(context.getString(R.string.common_error_no_internet)).assertIsDisplayed()
            compose.onNodeWithTag("podcast-loading").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.playlist_actions_title)).assertIsDisplayed().performClick()
            compose.onNodeWithText(context.getString(R.string.common_action_retry)).assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(if (enabled) 2 else 1, retries)
                assertEquals(retries, playlistActions)
            }
        }
    }

    @Test fun `error removes spinner and retry starts loading while empty feed stays terminal`() {
        val status = mutableStateOf(PodcastPageStatus.Loading)
        var retries = 0
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            val shape = RoundedCornerShape(8.dp)
            CompositionLocalProvider(
                LocalAppearance provides Appearance(
                    DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
                ),
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
            ) {
                PodcastLoadingStatus(status.value, isOnline = true) {
                    retries++
                    status.value = PodcastPageStatus.Loading
                }
            }
        }
        compose.onNodeWithTag("podcast-loading").assertIsDisplayed()
        compose.runOnIdle { status.value = PodcastPageStatus.Error }
        compose.onNodeWithTag("podcast-loading").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_error_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.playlist_actions_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_action_retry)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("podcast-loading").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_action_retry)).assertDoesNotExist()
        compose.runOnIdle { status.value = PodcastPageStatus.Empty }
        compose.onNodeWithText(context.getString(R.string.common_empty_items)).assertIsDisplayed()
        compose.onNodeWithTag("podcast-loading").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.common_action_retry)).assertDoesNotExist()
    }
}
