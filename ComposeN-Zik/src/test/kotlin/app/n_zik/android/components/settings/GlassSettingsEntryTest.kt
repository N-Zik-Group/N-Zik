package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.common.GlobalPageItemRow
import app.n_zik.android.components.settings.common.SettingsGlassItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class GlassSettingsEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `global Glass effect entry uses the shared beta badge before its name without a description`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.preferences.edit().putBoolean(glassEnabledKey, false).commit()
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(16.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                DefaultDarkColorPalette, Typography.fallback, shape, shape, shape,
            )) {
                GlobalPageItemRow(SettingsGlassItem, { clicks++ }, testTag = "glass-entry")
            }
        }
        val title = compose.onNodeWithText(context.getString(R.string.liquid_glass), useUnmergedTree = true)
        val beta = compose.onNodeWithText(context.getString(R.string.beta_title), useUnmergedTree = true)
        assertEquals("Glass effect", context.getString(R.string.liquid_glass))
        title.assertIsDisplayed()
        beta.assertIsDisplayed()
        assertTrue(beta.fetchSemanticsNode().boundsInRoot.right <= title.fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithText(context.getString(R.string.page_glass_summary), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("glass-entry").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
