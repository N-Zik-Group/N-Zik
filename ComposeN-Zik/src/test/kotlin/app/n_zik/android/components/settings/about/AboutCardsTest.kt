package app.n_zik.android.components.settings.about

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithTag
import app.n_zik.android.components.settings.common.AboutSettingsEntryTargets
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.updater.models.UpdateEndpoints
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises retained support actions without opening external applications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AboutCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `support actions retain their repository destinations`() {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler { override fun openUri(uri: String) { opened.add(uri) } }
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )
        val registry = SettingsTargetRegistry()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalUriHandler provides handler, LocalSettingsTargets provides registry) {
                AboutSettingsContent(selectedCards = setOf(R.string.troubleshooting))
            }
        }
        AboutSettingsEntryTargets.filter { it.cardTitleRes == R.string.troubleshooting }.forEach { entry ->
            composeRule.onNodeWithTag("settings.target.${entry.id}", useUnmergedTree = true).assertExists()
        }
        composeRule.onNodeWithText(context.getString(R.string.contributors)).assertDoesNotExist()
        assertEquals("Rendering a search destination must not open a browser", emptyList<String>(), opened)
        composeRule.onNodeWithText(context.getString(R.string.view_the_source_code)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.report_an_issue)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.request_a_feature_or_suggest_an_idea)).performClick()
        assertEquals(listOf(UpdateEndpoints.REPO_URL,
            UpdateEndpoints.REPO_URL + "/issues/new?assignees=&labels=bug&template=bug_report.yaml",
            UpdateEndpoints.REPO_URL + "/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"), opened)
        assertEquals(listOf("about.app", "about.support", "about.contributors"), AboutSettingTargets.cards)
    }
}
