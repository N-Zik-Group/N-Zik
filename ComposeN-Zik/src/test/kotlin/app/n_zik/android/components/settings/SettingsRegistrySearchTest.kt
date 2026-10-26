package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.SETTINGS_GROUP_ABOUT_UPDATE
import app.n_zik.android.components.settings.common.OrganizedSettingsCategories
import app.n_zik.android.components.settings.common.cardsForCategory
import app.n_zik.android.components.settings.common.categoryId
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

/** Registry contracts for thematic destinations and legacy tab compatibility. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsRegistrySearchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theGlobalPageListsAboutThenUpdateAndThematicDestinations() {
        assertEquals(17, SettingsPageItems.size)
        assertEquals("about", SettingsPageItems.first().categoryId)
        assertTrue(SettingsPageItems[1] === SettingsUpdateItem)
        assertEquals(
            listOf("playback", "audio", "behavior", "interface", "player", "data", "downloads", "network",
                "youtube", "profiles", "services", "ai", "rewind", "maintenance", "system"),
            SettingsPageItems.drop(2).map { it.categoryId },
        )
        assertEquals(NavRoutes.updater.name, SettingsUpdateItem.route)
        assertEquals(null, SettingsUpdateItem.tab)
        assertTrue(SettingsPageItems.filter { it !== SettingsUpdateItem }.all { it.tab == null && it.route == null })
        // Legacy deep links retain their original tab numbers independently of hub order.
        assertEquals((0..8).toList(), SettingsCategories.map { it.tab })
    }

    @Test
    fun thematicGroupsAreCompleteAndKeepAboutAndUpdateTogether() {
        assertEquals((1..7).toList(), SettingsPageGroups.map { it.group })
        assertEquals(SETTINGS_GROUP_ABOUT_UPDATE, SettingsUpdateItem.group)
        assertEquals(listOf(R.string.about, R.string.update), SettingsPageItems.filter { it.group == 1 }.map { it.labelRes })
        assertTrue(SettingsPageGroups.all { group -> SettingsPageItems.any { it.group == group.group } })
        assertEquals(R.string.settings_global_update_description, SettingsUpdateItem.descriptionRes)
    }

    @Test
    fun everyCardHasOneDestinationAndResetsRetainDistinctSourceScopes() {
        val cards = OrganizedSettingsCategories.flatMap { cardsForCategory(it.id) }
        assertEquals(SettingsCards.size, cards.size)
        assertEquals(SettingsCards.toSet(), cards.toSet())
        assertEquals(cards.size, cards.map { it.tab to it.titleRes }.toSet().size)
        val resets = cards.filter { it.titleRes == R.string.settings_reset }
        assertEquals((0..7).toSet(), resets.map { it.tab }.toSet())
        assertTrue(resets.all { it.categoryId() == "system" })
        assertTrue(cardsForCategory("profiles").all { it.titleRes == R.string.profile_face })
        assertTrue(cardsForCategory("player").any { it.tab == 0 })
        assertTrue(cardsForCategory("player").any { it.tab == 2 })
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
    fun removedProfileStorageCardIsNotSearchableAndProfilesFocusUsesEditor() {
        val cards = SettingsCards.filter { it.tab == 5 }
        assertTrue(cards.none { it.titleRes == R.string.profiles })
        assertEquals("profiles", cards.single { it.titleRes == R.string.profile_face }.focusId)
        assertTrue(searchSettingsCards(context, context.getString(R.string.profile_data_waveforms))
            .none { it.tab == 5 })
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
