package app.n_zik.android.components.settings

import android.app.Application
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.settings.appearance.preview.StoreTagFilters
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SwitchSettingEntry
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSwitchAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `plain row exposes one named switch with state and honors disabled state`() {
        assertAccessibleSwitch(withIcon = false, glass = false)
    }

    @Test fun `icon row exposes one named switch with state in the classic style`() {
        assertAccessibleSwitch(withIcon = true, glass = false)
    }

    @Test fun `icon row exposes one named switch with state in Glass`() {
        assertAccessibleSwitch(withIcon = true, glass = true)
    }

    @Test fun `Theme Store filter exposes one labeled switch per tag and applies selections`() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        var applied = emptyList<String>()
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(
                LocalGlassPreviewSettings provides GlassSettings(enabled = false),
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            ) {
                StoreTagFilters(
                    tags = listOf("dark", "artwork"),
                    effectiveTag = "dark",
                    selectedTags = emptyList(),
                    onTag = {},
                    onAdvanced = { applied = it },
                )
            }
        }

        compose.onNodeWithContentDescription(resources.getString(R.string.theme_store_filters)).performClick()
        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onAllNodes(switchRole).assertCountEquals(2)
        val dark = compose.onNode(switchRole and hasText(resources.getString(R.string.theme_store_tag_dark)))
        val artwork = compose.onNode(switchRole and hasText(resources.getString(R.string.theme_store_tag_artwork)))
        dark.assertIsOn().performClick().assertIsOff()
        artwork.assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(emptyList<String>(), applied) }
        compose.onNodeWithText(resources.getString(R.string.common_action_apply)).performClick()
        compose.runOnIdle { assertEquals(listOf("artwork"), applied) }
    }

    private fun assertAccessibleSwitch(withIcon: Boolean, glass: Boolean) {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var commits = 0
        val onChange: (Boolean) -> Unit = {
            commits++
            checked.value = it
        }
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(
                LocalGlassPreviewSettings provides GlassSettings(enabled = glass, quality = GlassQuality.Performance),
                LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            ) {
                if (withIcon) {
                    OtherSwitchSettingEntry(
                        title = "Recommendations",
                        text = "Show recommendations",
                        isChecked = checked.value,
                        enabled = enabled.value,
                        onCheckedChange = onChange,
                        icon = R.drawable.refresh,
                    )
                } else {
                    SwitchSettingEntry(
                        title = "Recommendations",
                        text = "Show recommendations",
                        isChecked = checked.value,
                        onCheckedChange = onChange,
                        isEnabled = enabled.value,
                    )
                }
            }
        }

        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onAllNodes(switchRole).assertCountEquals(1)
        val row = compose.onNode(switchRole and hasText("Recommendations"))
        row.assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, commits)
            enabled.value = false
        }
        row.assertIsOn().assertIsNotEnabled()
    }
}
