package app.n_zik.android.components.menu

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.ui.components.themed.IconButton
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MenuBookmarkSemanticsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `artwork badge adds no click target and the action describes each transition`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val state = mutableStateOf<Boolean?>(true)
        var clicks = 0
        composeRule.setContent {
            val appearance = Appearance(ColorPalette.fallback, Typography.fallback, CircleShape, CircleShape, CircleShape)
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Row {
                    Box(Modifier.size(64.dp)) {
                        state.value?.let { MenuBookmarkBadge(bookmarked = it) }
                    }
                    IconButton(
                        icon = R.drawable.bookmark,
                        color = Color.White,
                        modifier = Modifier.size(48.dp),
                        contentDescription = menuLikeActionDescription(
                            state.value, true, R.string.album_action_bookmark, R.string.library_action_remove_bookmark,
                        ),
                        onClick = { clicks++ },
                    )
                }
            }
        }

        listOf(
            true to R.string.library_dislikes_action_add,
            false to R.string.library_dislikes_action_remove,
            null to R.string.album_action_bookmark,
        ).forEach { (likeState, label) ->
            composeRule.runOnIdle { state.value = likeState }
            composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
            composeRule.onNode(hasClickAction() and hasContentDescription(app.getString(label))).performClick()
        }
        composeRule.runOnIdle { assertEquals(3, clicks) }
    }
}
