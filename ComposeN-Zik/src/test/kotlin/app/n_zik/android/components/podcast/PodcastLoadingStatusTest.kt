package app.n_zik.android.components.podcast

import android.app.Application
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
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

    @Test fun `retry pill remains visible and invokes one action in Material and Glass`() {
        val glassEnabled = mutableStateOf(false)
        var retries = 0
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
                PodcastLoadingStatus(PodcastPageStatus.Error, isOnline = false) { retries++ }
            }
        }
        for (enabled in listOf(false, true)) {
            compose.runOnIdle { glassEnabled.value = enabled }
            compose.onNodeWithText(context.getString(R.string.error_no_internet)).assertIsDisplayed()
            compose.onNodeWithTag("podcast-loading").assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(if (enabled) 2 else 1, retries) }
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
        compose.onNodeWithText(context.getString(R.string.error)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("podcast-loading").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.retry)).assertDoesNotExist()
        compose.runOnIdle { status.value = PodcastPageStatus.Empty }
        compose.onNodeWithText(context.getString(R.string.no_items)).assertIsDisplayed()
        compose.onNodeWithTag("podcast-loading").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.retry)).assertDoesNotExist()
    }
}
