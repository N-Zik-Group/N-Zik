package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassProfileDefaults
import app.n_zik.android.core.network.fullOfflineModeKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlassNavigationPreviewTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp),
        uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun showPreview() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GlassNavigationPreview(glassProfileDefaults(GlassQuality.Performance).copy(enabled = false))
            }
        }
    }

    @Test fun configuredOrderAndVisibilityAreUsedAndSelectionStaysLocal() {
        context.preferences.edit()
            .putString(homeTabsOrderKey, "[\"playlists\",\"albums\",\"songs\",\"artists\",\"quickpicks\"]")
            .putBoolean("hometab_artists_enabled", false)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)
            .putInt(homeScreenTabIndexKey, 3)
            .commit()
        showPreview()

        val tabs = composeRule.onAllNodes(isTab).fetchSemanticsNodes().sortedBy { it.positionInRoot.x }
        assertEquals(
            listOf(R.string.playlists, R.string.albums, R.string.songs, R.string.quick_picks).map(context::getString),
            tabs.map { it.config[SemanticsProperties.ContentDescription].single() },
        )
        composeRule.onNodeWithContentDescription(context.getString(R.string.artists)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.playlists)).assertIsSelected()
        composeRule.onNodeWithContentDescription(context.getString(R.string.songs)).performClick().assertIsSelected()
        assertEquals(3, context.preferences.getInt(homeScreenTabIndexKey, -1))
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun offlineChangesRemoveQuickPicksAndKeepThePreviewSelectionValid() {
        showPreview()
        composeRule.onNodeWithContentDescription(context.getString(R.string.quick_picks)).assertIsSelected()
        composeRule.runOnIdle {
            context.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.quick_picks)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.songs)).assertIsSelected()
        composeRule.onAllNodes(isTab).assertCountEquals(4)
    }

    @Test fun singleVisibleTabHidesTheBarLikeHomeNavigation() {
        context.preferences.edit()
            .putBoolean(fullOfflineModeKey, true)
            .putBoolean("hometab_artists_enabled", false)
            .putBoolean("hometab_albums_enabled", false)
            .putBoolean("hometab_playlists_enabled", false)
            .commit()
        showPreview()
        composeRule.onAllNodes(isTab).assertCountEquals(0)
    }
}
