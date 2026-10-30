package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.rewind.RewindPeriod
import app.n_zik.android.components.ui.screens.rewind.emptyRewindData
import app.n_zik.android.components.ui.screens.rewind.slides.RewindIntroCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class RewindIntroActionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `Glass start pill keeps opening lock and advances exactly once after reveal`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("preferences", 0).edit().putBoolean(glassEnabledKey, true).commit()
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        var advances = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                RewindIntroCard(
                    data = emptyRewindData(RewindPeriod.Global), username = "Listener",
                    page = 0, pageCount = 16, active = true, onNext = { advances++ },
                )
            }
        }
        compose.onNode(hasClickAction()).assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(2_800)
        compose.onNode(hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, advances) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasClickAction()).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.rw_intro_start), useUnmergedTree = true)
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, advances) }
    }
}
