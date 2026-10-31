package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.BuildConfig
import app.it.fast4x.rimusic.enums.PresetsReverb
import app.it.fast4x.rimusic.enums.FontType
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsDetailedSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(locale: Locale): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) },
    )

    @Test fun allEntriesHaveUniqueIdsAndReachableParentCards() {
        assertTrue(SettingsEntryTargets.isNotEmpty())
        assertTrue(EmbeddedSettingsEntryTargets.isNotEmpty())
        val entries = AllSettingsEntryTargets
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach { entry ->
            assertTrue(entry.id.isNotBlank())
            val parent = entry.parentCard()
            assertEquals(entry.tab, parent.tab)
            assertEquals(entry.cardTitleRes, parent.titleRes)
            assertTrue(OrganizedSettingsCategories.any { it.id == parent.categoryId() })
            assertTrue(context.getString(entry.labelRes).isNotBlank())
            entry.keywordRes.forEach { assertTrue(context.getString(it).isNotBlank()) }
        }
    }

    @Test fun localizedLabelAndDescriptionFindTheExactEntry() {
        val french = localized(Locale.FRENCH)
        val skipSilence = AllSettingsEntryTargets.single {
            it.tab == 0 && it.cardTitleRes == R.string.audio_effects && it.labelRes == R.string.skip_silence
        }
        assertTrue(searchSettingsEntries(french, french.getString(R.string.skip_silence)).contains(skipSilence))
        assertTrue(searchSettingsEntries(french, french.getString(R.string.skip_silent_parts_during_playback)).contains(skipSilence))
        val embedded = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.listen_together_server_url }
        assertTrue(searchSettingsEntries(french, french.getString(embedded.labelRes)).contains(embedded))
    }

    @Test fun everyRegisteredOptionIsSearchableInEnglishAndFrenchInItsOrganizedSection() {
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = localized(locale)
            AllSettingsEntryTargets.forEach { entry ->
                val results = searchSettingsEntries(localized, localized.getString(entry.labelRes))
                assertTrue("Missing $locale search target ${entry.id}", results.contains(entry))
                val card = entry.parentCard()
                val category = OrganizedSettingsCategories.single { it.id == card.categoryId() }
                val location = entry.locationDescription(localized)
                val root = localized.getString(R.string.settings)
                val expectedPage = if (entry.id.startsWith("glass.")) localized.getString(R.string.liquid_glass)
                    else localized.getString(category.labelRes)
                assertTrue("Wrong page for ${entry.id}: $location", location.startsWith("$root › $expectedPage"))
                assertTrue("Missing parent card for ${entry.id}: $location", location.contains(card.displayTitle(localized)))
            }
        }
    }

    @Test fun debugUpdaterAccessIsIndexedOnlyWhenTheControlExists() {
        val targets = EmbeddedSettingsEntryTargets.filter { it.id == "embedded.maintenance.debug_updater_layout_access" }
        assertEquals(if (BuildConfig.DEBUG) 1 else 0, targets.size)
        if (BuildConfig.DEBUG) {
            assertTrue(searchSettingsEntries(context, context.getString(R.string.debug_updater_layout_access_description)).contains(targets.single()))
        }
    }

    @Test fun searchFoldsCaseAndAccentsWithoutChangingStableIdentity() {
        val french = localized(Locale.FRENCH)
        val english = localized(Locale.ENGLISH)
        val target = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.enable_log_debug }
        assertNotEquals(english.getString(target.labelRes), french.getString(target.labelRes))
        val frenchResult = searchSettingsEntries(french, "DEBOGAGE").single { it.id == target.id }
        val englishResult = searchSettingsEntries(english, english.getString(target.labelRes)).single { it.id == target.id }
        assertEquals(englishResult.id, frenchResult.id)
        assertEquals(englishResult.parentCard(), frenchResult.parentCard())
    }

    @Test fun blankAndUnknownQueriesAreEmptyAndBroadMatchesNeverDuplicateIds() {
        assertTrue(searchSettingsEntries(context, "  ").isEmpty())
        assertTrue(searchSettingsEntries(context, "zzzz-unmatched-setting-945726").isEmpty())
        val results = searchSettingsEntries(localized(Locale.ENGLISH), "backup")
        assertFalse(results.isEmpty())
        assertEquals(results.size, results.map { it.id }.toSet().size)
    }
    @Test fun everyAvailableReverbFindsOneExactTargetInEitherWordOrder() {
        val english = localized(Locale.ENGLISH)
        val target = AllSettingsEntryTargets.single { it.id == "general.audio_effects.settings_audio_reverb" }
        PresetsReverb.entries.forEach { preset ->
            val option = english.getString(preset.textRes)
            listOf(option, "reverb $option", "$option reverb").forEach { query ->
                assertEquals(listOf(target), searchSettingsEntries(english, query).filter { it.id == target.id })
            }
        }
        assertFalse(searchSettingsEntries(english, "reverb small large").contains(target))
    }

    @Test fun choiceCatalogUsesExistingStableTargetsAndFiniteLocalizedNativeAndNumericLabels() {
        val english = localized(Locale.ENGLISH)
        val french = localized(Locale.FRENCH)
        val catalog = settingsEntryChoiceLabels(english::getString, Locale.ENGLISH)
        assertTrue(catalog.isNotEmpty())
        assertTrue(AllSettingsEntryTargets.map { it.id }.containsAll(catalog.keys))
        val samples = mapOf(
            "data.cache.image_cache_max_size" to "32MB",
            "general.playback.pause_between_songs" to "5s",
            "general.playback.crossfade_duration" to "12s",
            "ai.quick_picks.quick_selection_type" to english.getString(R.string.quick_selection, 3),
            "ui.settings_use_font_type.settings_use_font_type" to FontType.entries.first().name,
            "other.proxy.proxy_mode" to "SOCKS",
            "ai.statistics.statistics_max_number_of_items" to english.getString(R.string.max_items_custom),
            "network.quality.audio_quality_format" to english.getString(R.string.audio_quality_format_high),
            "accounts.social_discord.discord_activity_type" to english.getString(R.string.discord_activity_watching),
            "embedded.profile.face_name_source" to english.getString(R.string.face_source_profile),
        )
        samples.forEach { (id, choice) ->
            assertTrue("Missing choice $id: $choice", catalog.getValue(id).contains(choice))
            assertEquals(1, searchSettingsEntries(english, choice).count { it.id == id })
        }
        val frenchHigh = french.getString(R.string.audio_quality_format_high)
        assertEquals(1, searchSettingsEntries(french, frenchHigh).count { it.id == "network.quality.audio_quality_format" })
        assertFalse(catalog.containsKey("other.proxy.settings_proxy_password"))
        assertFalse(catalog.containsKey("general.audio_effects.settings_loudness_base_gain"))
    }

    @Test fun formattedChoiceLabelsUseTheResourcesLocale() {
        val arabic = localized(Locale.forLanguageTag("ar"))
        val choice = arabic.getString(R.string.quick_selection, 3)
        assertEquals(
            1,
            searchSettingsEntries(arabic, choice).count { it.id == "ai.quick_picks.quick_selection_type" },
        )
    }

    @Test fun everyEntryRoutesToItsActualDestinationAndPreservesTheExactTarget() {
        AllSettingsEntryTargets.forEach { entry ->
            val route = entry.destinationRoute(context)
            val uri = Uri.parse(route)
            assertEquals(entry.id, uri.getQueryParameter("target"))
            if (entry.id.startsWith("glass.")) {
                assertEquals(SETTINGS_GLASS_ROUTE, uri.path)
            } else {
                val card = entry.parentCard()
                assertEquals("settingsCategory/${card.categoryId()}", uri.path)
                assertEquals(settingsCardTargetId(context, card.tab, card.titleRes), uri.getQueryParameter("parent"))
            }
        }
    }

    @Test fun nestedBreadcrumbNamesItsRealOpenerAndDialogSubsection() {
        val base = SettingsEntryTargets.first()
        val child = base.copy(id = "nested.breadcrumb.test", nestedDestination = SettingsNestedDestination(
            openerId = base.id, dialogId = "breadcrumb", tabIndex = 0, itemId = "test",
            sectionTitleRes = R.string.favorites,
        ))
        val location = child.locationDescription(context)
        assertTrue(location.contains(context.getString(base.labelRes)))
        assertTrue(location.endsWith(context.getString(R.string.favorites)))
    }

    @Test fun glassPolicyResultsNameTheirAppearanceScopeAndDedicatedVisiblePath() {
        val entry = AllSettingsEntryTargets.single { it.id == "glass.destination.PlayerActions.ActionButtons" }
        assertEquals("${context.getString(R.string.liquid_glass)} › ${context.getString(entry.labelRes)}",
            entry.searchResultTitle(context))
        assertEquals(listOf(R.string.settings, R.string.liquid_glass, R.string.glass_destination_playback)
            .joinToString(" › ") { context.getString(it) }, entry.locationDescription(context))
        assertFalse(entry.locationDescription(context).contains(context.getString(R.string.settings_category_interface)))
    }

    @Test fun functionalResultsKeepTheirVisibleLabelAndActualOrganizedCardPath() {
        val entry = AllSettingsEntryTargets.single { it.id == "general.audio_effects.skip_silence" }
        assertEquals(context.getString(R.string.skip_silence), entry.searchResultTitle(context))
        assertEquals(listOf(R.string.settings, R.string.settings_category_audio, R.string.audio_effects)
            .joinToString(" › ") { context.getString(it) }, entry.locationDescription(context))
        val audioQuality = AllSettingsEntryTargets.single { it.id == "network.quality.audio_quality_format" }
        assertEquals(listOf(R.string.settings, R.string.tab_network, R.string.quality)
            .joinToString(" › ") { context.getString(it) }, audioQuality.locationDescription(context))
    }

}
