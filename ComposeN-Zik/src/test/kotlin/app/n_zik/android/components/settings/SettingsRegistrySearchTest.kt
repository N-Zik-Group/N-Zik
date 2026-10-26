package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SETTINGS_GROUP_ABOUT_UPDATE
import app.n_zik.android.components.settings.common.SETTINGS_GROUP_CATEGORIES
import app.n_zik.android.components.settings.common.SettingsCategories
import app.n_zik.android.components.settings.common.SettingsCards
import app.n_zik.android.components.settings.common.SettingsPageGroups
import app.n_zik.android.components.settings.common.SettingsPageItems
import app.n_zik.android.components.settings.common.SettingsUpdateItem
import app.n_zik.android.components.settings.common.searchSettingsCards
import app.n_zik.android.components.settings.common.searchSettingsPageItems
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Registry contract test for the global settings page (spec-settings-global-page,
 * rows UPDATE_ITEM and the card-level search): the page lists the About
 * category first, then the Update item (targeting the existing
 * [NavRoutes.updater] route, not a tab), then the remaining categories in tab
 * order. The item-level search indexes the Update item only; the card-level
 * search indexes card titles and keywords.
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine
 * on the JUnit 5 platform, with the plain [Application] so string resources
 * resolve without the app's heavy init.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsRegistrySearchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theGlobalPageListsAboutFirstThenUpdateThenTheBaseCategories() {
        assertEquals("the page must list 10 items", 10, SettingsPageItems.size)

        assertEquals(
            "the About category must be the first item on the global page",
            8,
            SettingsPageItems.first().tab
        )
        assertTrue(
            "the Update item must be the second item on the global page",
            SettingsPageItems[1] === SettingsUpdateItem
        )
        assertEquals(
            "the remaining items must be the 8 base categories in tab order",
            (0..7).toList(),
            SettingsPageItems.drop(2).map { it.tab }
        )

        assertEquals(NavRoutes.updater.name, SettingsUpdateItem.route)
        assertEquals("the Update item targets a route, not a settings tab", null, SettingsUpdateItem.tab)

        assertEquals((0..8).toList(), SettingsCategories.map { it.tab })
        SettingsCategories.forEach { category ->
            assertEquals("categories target tabs, not routes", null, category.route)
        }
    }

    @Test
    fun theItemsCarryTheirGroupAndDescription() {
        // Grouping is registry data only: card 1 = About + Update, card 2 =
        // the 8 categories. A later re-grouping edits these entries, never
        // the page code.
        assertEquals(2, SettingsPageGroups.size)
        assertEquals(SETTINGS_GROUP_ABOUT_UPDATE, SettingsPageGroups[0].group)
        assertEquals(SETTINGS_GROUP_CATEGORIES, SettingsPageGroups[1].group)

        assertEquals(SETTINGS_GROUP_ABOUT_UPDATE, SettingsUpdateItem.group)
        assertEquals(
            SETTINGS_GROUP_ABOUT_UPDATE,
            SettingsCategories.first { it.tab == 8 }.group
        )
        SettingsCategories.filter { it.tab in 0..7 }.forEach {
            assertEquals(
                "category ${it.tab} must be in the categories group",
                SETTINGS_GROUP_CATEGORIES,
                it.group
            )
        }

        // The row descriptions reuse each tab's existing header description.
        assertEquals(R.string.settings_global_update_description, SettingsUpdateItem.descriptionRes)
        assertEquals(R.string.general_settings_description, SettingsCategories.first { it.tab == 0 }.descriptionRes)
        assertEquals(R.string.about_description, SettingsCategories.first { it.tab == 8 }.descriptionRes)
    }

    @Test
    fun thePageItemSearchMatchesOnlyTheUpdateItem() {
        val results = searchSettingsPageItems(context, "update")
        assertEquals(listOf(SettingsUpdateItem), results)

        // Case-insensitive; all three of the Update item's keywords match.
        assertEquals(listOf(SettingsUpdateItem), searchSettingsPageItems(context, "CHECK UPDATE"))
        assertEquals(listOf(SettingsUpdateItem), searchSettingsPageItems(context, "update available"))

        // Category labels are not part of the item-level index.
        assertTrue(searchSettingsPageItems(context, context.getString(R.string.about)).isEmpty())

        // A blank or non-matching query returns no item results.
        assertTrue(searchSettingsPageItems(context, "").isEmpty())
        assertTrue(searchSettingsPageItems(context, "   ").isEmpty())
        assertTrue(searchSettingsPageItems(context, "zzz_no_match").isEmpty())
    }

    @Test
    fun theCardSearchMatchesTitlesAndKeywords() {
        assertTrue("a blank query returns no card results", searchSettingsCards(context, "").isEmpty())

        val query = context.getString(R.string.skip_silence)
        val audioEffects = SettingsCards.first { it.tab == 0 && it.titleRes == R.string.audio_effects }
        assertTrue(
            "the card whose keywords contain the query must be found",
            searchSettingsCards(context, query).contains(audioEffects)
        )
        assertTrue(
            "the match must be case-insensitive",
            searchSettingsCards(context, query.lowercase()).contains(audioEffects)
        )

        assertTrue(searchSettingsCards(context, "zzz_no_match").isEmpty())
    }

    @Test
    fun theVisibleCardTitlesForTheGeneralTabCoverItsTenCards() {
        assertEquals(
            "a blank query keeps every card of the tab",
            SettingsCards.filter { it.tab == 0 }.mapTo(HashSet(10)) { it.titleRes },
            visibleCardTitleRes(context, 0, "")
        )

        assertEquals(
            "a query keeps only the matching card",
            setOf(R.string.audio_effects),
            visibleCardTitleRes(context, 0, context.getString(R.string.skip_silence))
        )
    }
}
