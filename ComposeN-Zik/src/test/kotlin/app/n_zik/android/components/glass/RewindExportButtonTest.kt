package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindPeriod
import app.n_zik.android.components.ui.screens.rewind.emptyRewindData
import app.n_zik.android.components.ui.screens.rewind.slides.RewindFinaleCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class RewindExportButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `finale export button opens options and invokes page action`() {
        val context = RuntimeEnvironment.getApplication()
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        var shares = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindFinaleCard(
                    data = emptyRewindData(RewindPeriod.Year(2026)), username = "Listener",
                    page = 15, pageCount = 16, active = true, shareMode = false,
                    onShare = {}, onRestart = {}, onShareSlide = { shares++ },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasText(context.getString(R.string.rw_finale_export_options)) and hasClickAction())
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.rw_finale_export_page))
            .assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, shares) }
    }
}
