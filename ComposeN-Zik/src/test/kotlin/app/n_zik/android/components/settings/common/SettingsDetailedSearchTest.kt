package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
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

}
