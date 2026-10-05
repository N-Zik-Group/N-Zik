package app.n_zik.android.components.dialog.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.BuiltInPlaylist
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The changed option sets of the phone's songs sort-settings dialog: "Custom" is offered on every
 * sort chip (its real tab menu shows it too, its visibility flags default to visible), while
 * "Downloaded" stays out of the cached and downloaded chips — the same sets the contract §10.1
 * `sortMenu` serves and the phone's tabs display.
 *
 * The sets themselves are pinned on the dialog's pure option logic
 * ([HomeSongsSortSettingsDialog.tabAvailableIds] and [HomeSongsSortSettingsDialog.parseOrder],
 * internal for that). The Compose test below is a render + tab-switch smoke: `performScrollTo` on
 * off-screen nodes (the tail tabs of the horizontal row, the tail rows of the lazy list) is
 * unreliable under Robolectric, so the smoke sticks to the nodes that are composed and reachable
 * at this window size.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on the
 * JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4), with the plain
 * [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class HomeSongsSortSettingsDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    @Test
    fun `every sort chip offers the custom order, the cached and downloaded chips do not offer the downloaded sort`() {
        val ids = HomeSongsSortSettingsDialog.tabAvailableIds

        // "Custom" on every song chip — the phone's own tab menus show it too (its visibility
        // flags default to visible)
        for (tab in listOf(
                BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked,
                BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded
            )) {
            assertTrue("\"Custom\" is offered on the $tab chip", "Custom" in (ids[tab] ?: emptyList()))
        }

        // "Downloaded" stays out of the cached and downloaded chips, present on the rest
        assertFalse("Downloaded" in (ids[BuiltInPlaylist.Offline] ?: emptyList()))
        assertFalse("Downloaded" in (ids[BuiltInPlaylist.Downloaded] ?: emptyList()))
        assertTrue("Downloaded" in (ids[BuiltInPlaylist.All] ?: emptyList()))

        // The Top and OnDevice chips serve their own menus, not the song options
        assertEquals(
            listOf("Today", "OneWeek", "OneMonth", "ThreeMonths", "SixMonths", "OneYear", "All"),
            ids[BuiltInPlaylist.Top],
        )
        assertEquals(
            listOf("Title", "DateAdded", "Artist", "Duration", "Album"),
            ids[BuiltInPlaylist.OnDevice],
        )
    }

    @Test
    fun `parseOrder keeps the saved order, drops the unknown options and appends the missing ones`() {
        val favorites = HomeSongsSortSettingsDialog.tabAvailableIds[BuiltInPlaylist.Favorites]!!

        // An untouched chip serves its available options as-is
        assertEquals(favorites, HomeSongsSortSettingsDialog.parseOrder("", BuiltInPlaylist.Favorites))

        // A saved order: the saved visible options keep their order, the rest follows in the
        // available order
        assertEquals(
            listOf("Custom", "Title") + (favorites - "Custom" - "Title"),
            HomeSongsSortSettingsDialog.parseOrder("""["Custom","Title"]""", BuiltInPlaylist.Favorites),
        )

        // Unknown ids (an option dropped from the chip) are out, duplicates collapse
        assertEquals(
            favorites,
            HomeSongsSortSettingsDialog.parseOrder("""["Gone","Title","Title"]""", BuiltInPlaylist.Favorites),
        )

        // Unparseable input falls back to the available options
        assertEquals(
            HomeSongsSortSettingsDialog.tabAvailableIds[BuiltInPlaylist.All]!!,
            HomeSongsSortSettingsDialog.parseOrder("not a json array", BuiltInPlaylist.All),
        )
    }

    @Test
    fun `the dialog renders its tabs and first rows, and the favorites tab switch keeps them`() {
        content { HomeSongsSortSettingsDialog.DialogBody() }

        // All seven tabs are in the tree (the ones past the visible range too — the tab row
        // composes them all). fetchSemanticsNodes, not assertExists: this Compose version's
        // collection has no assertExists
        for (tab in listOf(
                BuiltInPlaylist.All, BuiltInPlaylist.Favorites, BuiltInPlaylist.Disliked,
                BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded, BuiltInPlaylist.Top,
                BuiltInPlaylist.OnDevice
            )) {
            assertTrue(
                "the $tab tab is in the tree",
                composeRule.onAllNodesWithText(app.getString(tab.textId)).fetchSemanticsNodes().isNotEmpty(),
            )
        }

        // The first row of the active (All) tab is composed and visible
        composeRule.onNodeWithText(app.getString(R.string.sort_title)).assertIsDisplayed()

        // The Favorites tab (the 2nd one — reachable without a horizontal scroll at this width):
        // the switch recomposes the rows, the dialog stays stable
        composeRule.onNodeWithText(app.getString(BuiltInPlaylist.Favorites.textId)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(app.getString(R.string.sort_title)).assertIsDisplayed()
    }
}
