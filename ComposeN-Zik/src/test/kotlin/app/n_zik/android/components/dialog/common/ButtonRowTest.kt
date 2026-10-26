package app.n_zik.android.components.dialog.common

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class ButtonRowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun buttonsWrapWithoutOverlapAndRemainTappableWithLargeFonts() {
        val scale = mutableStateOf(1f)
        val labels = listOf("Ajouter un séparateur", "Réinitialiser", "Annuler", "Confirmer")
        var clicked = ""
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalDensity provides Density(density.density, scale.value),
            ) {
                Box(Modifier.width(220.dp)) {
                    ButtonRow(labels.map { label -> DialogButton(label, { clicked = label }) })
                }
            }
        }
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        fun bounds(index: Int) = composeRule.onNodeWithText(labels[index]).fetchSemanticsNode().boundsInRoot
        assertTrue("The footer wraps instead of squeezing all four buttons: ${labels.indices.map { bounds(it) }}", bounds(3).top > bounds(0).top)
        composeRule.onNodeWithText(labels[3]).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(labels[3], clicked) }
        composeRule.runOnIdle { scale.value = 1.8f }
        repeat(3) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        for (first in labels.indices) {
            for (second in first + 1 until labels.size) {
                val a = bounds(first)
                val b = bounds(second)
                assertTrue(a.bottom <= b.top || a.right <= b.left || b.right <= a.left)
            }
            composeRule.onNodeWithText(labels[first]).performTouchInput { click() }
            composeRule.runOnIdle { assertEquals(labels[first], clicked) }
        }
    }
}
