package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.contextMode
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.rememberGlassSettings
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
class GlassQueuePolicySelectorTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
    private val store by lazy { GlassStore(context) }
    private val queueKey = GlassContextArea(GlassScope.Queue, GlassArea.Menus).preferenceKey
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp),
        uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
        preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `legacy glass only is displayed as disabled without changing saved settings`() {
        assertLegacyDisplay(GlassAreaMode.GlassOnly, R.string.glass_mode_disabled)
    }

    @Test fun `legacy glass and contour is displayed as contour only without changing saved settings`() {
        assertLegacyDisplay(GlassAreaMode.GlassAndContour, R.string.glass_mode_contour_only)
    }

    @Test fun `inherited glass only displays disabled without creating a queue override`() {
        assertLegacyDisplay(GlassAreaMode.GlassOnly, R.string.glass_mode_disabled, inherited = true)
    }

    @Test fun `inherited glass and contour displays contour without creating a queue override`() {
        assertLegacyDisplay(GlassAreaMode.GlassAndContour, R.string.glass_mode_contour_only, inherited = true)
    }

    @Test fun `choosing disabled preserves explicit queue override when shared menus have glass only`() {
        assertQueueSelection(GlassAreaMode.GlassOnly, GlassAreaMode.Disabled, R.string.glass_mode_disabled)
    }

    @Test fun `choosing contour preserves explicit queue override when shared menus have glass and contour`() {
        assertQueueSelection(GlassAreaMode.GlassAndContour, GlassAreaMode.ContourOnly, R.string.glass_mode_contour_only)
    }

    @Test fun `queue actions still offer all four material modes`() {
        showCard(GlassDestinationGroup.Playback, "Queue.ActionButtons")
        openSelector("Queue.ActionButtons")
        assertOptions(allModeLabels)
    }

    @Test fun `shared menus still offer all four material modes`() {
        showCard(GlassDestinationGroup.Windows, "Menus")
        openSelector("Menus")
        assertOptions(allModeLabels)
    }

    private fun assertLegacyDisplay(mode: GlassAreaMode, label: Int, inherited: Boolean = false) {
        if (inherited) store.setAreaMode(GlassArea.Menus, mode)
        else store.setContextMode(GlassScope.Queue, GlassArea.Menus, mode)
        val before = preferences.all.toMap()
        showCard()
        composeRule.onNode(
            hasText(context.getString(R.string.settings_current_value, context.getString(label))) and
                hasAnyAncestor(hasTestTag("settings.glass.mode.Queue.Menus")),
            useUnmergedTree = true,
        ).performScrollTo().assertIsDisplayed()
        openSelector()
        assertOptions(listOf(R.string.glass_mode_disabled, R.string.glass_mode_contour_only))
        composeRule.runOnIdle {
            assertEquals(before, preferences.all)
            if (inherited) assertFalse(preferences.contains(queueKey))
        }
    }

    private fun assertQueueSelection(sharedMode: GlassAreaMode, chosen: GlassAreaMode, label: Int) {
        store.setAreaMode(GlassArea.Menus, sharedMode)
        store.setContextMode(GlassScope.Queue, GlassArea.ActionButtons, GlassAreaMode.GlassAndContour)
        store.setAreaMode(GlassArea.NowPlaying, GlassAreaMode.GlassOnly)
        store.setAreaMode(GlassArea.MiniPlayer, GlassAreaMode.ContourOnly)
        val before = preferences.all.toMap()
        showCard()
        openSelector()
        assertOptions(listOf(R.string.glass_mode_disabled, R.string.glass_mode_contour_only))
        composeRule.onNode(
            hasText(context.getString(label)) and hasAnyAncestor(hasTestTag("valueSelectorList")),
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(before + (queueKey to chosen.name), preferences.all)
            assertEquals(chosen, GlassStore(context).read().contextMode(GlassScope.Queue, GlassArea.Menus))
        }
    }

    private fun showCard(group: GlassDestinationGroup = GlassDestinationGroup.Playback, tag: String = "Queue.Menus") {
        val target = glassDestinationRows(group).single {
            (if (it.scope == GlassScope.Shared) it.area.name else "${it.scope.name}.${it.area.name}") == tag
        }.searchId
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GlassDestinationCard(group, rememberGlassSettings(), store, target)
                }
            }
        }
    }

    private fun openSelector(tag: String = "Queue.Menus") {
        composeRule.onNodeWithTag("settings.glass.mode.$tag").performScrollTo().performClick()
    }

    private fun assertOptions(labels: List<Int>) {
        val allLabels = allModeLabels.map(context::getString)
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag("valueSelectorList")) and
                allLabels.map { hasText(it) }.reduce { first, next -> first or next },
        ).assertCountEquals(labels.size)
        labels.forEach { label ->
            composeRule.onNode(
                hasText(context.getString(label)) and hasAnyAncestor(hasTestTag("valueSelectorList")),
            ).assertIsDisplayed()
        }
    }

    private val allModeLabels = listOf(
        R.string.glass_mode_disabled,
        R.string.glass_mode_contour_only,
        R.string.glass_mode_glass_only,
        R.string.glass_mode_glass_and_contour,
    )
}
