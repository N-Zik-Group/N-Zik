package app.n_zik.android.components.search

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class SearchHistoryGlassActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `glass delete edit and delete-all keep independent callbacks from query recall`() {
        assertCallbacks(glass = true)
    }

    @Test fun `native delete edit and delete-all retain the original row behavior`() {
        assertCallbacks(glass = false)
    }

    private fun assertCallbacks(glass: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE).edit().clear().commit()
        context.preferences.edit().putBoolean(glassEnabledKey, glass).commit()
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val recalled = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val edited = mutableListOf<String>()
        var cleared = 0
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                SearchHistorySection(
                    visible = true,
                    queries = listOf("Saved query"),
                    onQueryClick = { recalled += it },
                    onQueryDelete = { deleted += it },
                    onQueryDeleteAll = { cleared++ },
                    onQueryEdit = { edited += it },
                )
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        val delete = if (glass) compose.onNodeWithContentDescription(context.getString(R.string.delete))
            else compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        delete.performTouchInput { click() }
        delete.performTouchInput { longClick() }
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(listOf("Saved query"), deleted)
            assertEquals(listOf("Saved query"), edited)
            assertEquals(1, cleared)
            assertEquals(emptyList<String>(), recalled)
        }
        compose.onNodeWithText("Saved query").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("Saved query"), recalled) }
    }
}
