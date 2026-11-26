package app.n_zik.android.components.menu

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class MenuEntryInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `list and grid expose toggle state and keep informational rows passive`() {
        val useGrid = mutableStateOf(false)
        val checked = mutableStateOf(false)
        val shape = RoundedCornerShape(8.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val toggle = Modifier.menuToggleSemantics(checked.value)
                val toggleClick = { checked.value = !checked.value }
                if (useGrid.value) {
                    GridMenu.Menu(showDragHandle = false) {
                        item { GridMenu.Entry("Listener", icon = {}) }
                        item {
                            GridMenu.Entry("Sync volume", icon = {}, modifier = toggle, onClick = toggleClick)
                        }
                    }
                } else {
                    ListMenu.Menu(showDragHandle = false) {
                        ListMenu.Entry("Listener", icon = {})
                        ListMenu.Entry("Sync volume", icon = {}, modifier = toggle, onClick = toggleClick)
                    }
                }
            }
        }

        val toggle = composeRule.onNodeWithText("Sync volume")
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        val noClick = SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick)
        for (grid in listOf(false, true)) {
            composeRule.runOnIdle {
                useGrid.value = grid
                checked.value = false
            }
            composeRule.onNodeWithText("Listener").assert(noClick)
            toggle.assert(switchRole).assertIsOff().performClick().assertIsOn()
        }
    }
}
