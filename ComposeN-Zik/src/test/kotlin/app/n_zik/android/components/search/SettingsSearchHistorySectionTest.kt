package app.n_zik.android.components.search

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class SettingsSearchHistorySectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun advanceFrames(milliseconds: Long) {
        repeat(((milliseconds + 15) / 16).toInt()) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `history mounted visible expands progressively and preserves animated exit`() {
        val visible = mutableStateOf(true)
        val mounted = mutableStateOf(true)
        val query = mutableStateOf("")
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column {
                    if (mounted.value) {
                        val matches = filterSearchHistory(listOf("Saved query", "Another query"), query.value)
                        SearchHistorySection(
                            visible = visible.value && matches.isNotEmpty(),
                            queries = matches,
                            onQueryClick = {},
                            onQueryDelete = {},
                            onQueryDeleteAll = {},
                        )
                    }
                    Box(Modifier.size(20.dp).testTag("following_content"))
                }
            }
        }

        fun contentTop(): Float = composeRule.onNodeWithTag("following_content")
            .fetchSemanticsNode().boundsInRoot.top

        advanceFrames(80)
        composeRule.waitForIdle()
        val enteringTop = contentTop()
        advanceFrames(300)
        composeRule.waitForIdle()
        val expandedTop = contentTop()
        assertTrue("Entry pushes content progressively", enteringTop > 0f && enteringTop < expandedTop)

        composeRule.runOnIdle { visible.value = false }
        advanceFrames(80)
        composeRule.waitForIdle()
        val exitingTop = contentTop()
        assertTrue("Exit still shrinks progressively: $exitingTop / $expandedTop", exitingTop > 0f && exitingTop < expandedTop)
        advanceFrames(200)
        composeRule.waitForIdle()
        assertEquals(0f, contentTop(), 1f)

        // Lazy lists can dispose the history item and remount it already requested.
        composeRule.runOnIdle { mounted.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            visible.value = true
            mounted.value = true
        }
        advanceFrames(80)
        composeRule.waitForIdle()
        val remountedTop = contentTop()
        assertTrue("Remount must replay entry", remountedTop > 0f && remountedTop < expandedTop)
        advanceFrames(300)
        composeRule.waitForIdle()
        assertEquals(expandedTop, contentTop(), 1f)

        composeRule.runOnIdle { query.value = "saved" }
        advanceFrames(300)
        val filteredTop = contentTop()
        assertTrue("Only matching rows remain", filteredTop > 0f && filteredTop < expandedTop)
        composeRule.runOnIdle { query.value = "missing" }
        advanceFrames(80)
        assertTrue("No match still plays exit", contentTop() > 0f && contentTop() < filteredTop)
        advanceFrames(200)
        assertEquals(0f, contentTop(), 1f)
        composeRule.runOnIdle { query.value = "" }
        advanceFrames(300)
        assertEquals(expandedTop, contentTop(), 1f)
    }
}
