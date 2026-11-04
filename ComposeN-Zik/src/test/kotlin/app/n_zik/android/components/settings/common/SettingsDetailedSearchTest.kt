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

    @Test fun openPipIsSearchableGloballyAndOnItsPage() {
        val target = AllSettingsEntryTargets.single { it.id == "general.picture_in_picture.open" }
        listOf(Locale.ENGLISH to "open pip", Locale.FRENCH to "ouvrir pip").forEach { (locale, query) ->
            val localized = localized(locale)
            assertTrue(searchSettingsEntries(localized, query).contains(target))
            assertTrue(visibleCardTitleRes(localized, target.tab, query).contains(target.cardTitleRes))
            assertTrue(searchSettingsCards(localized, query).contains(target.parentCard()))
        }
    }

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
            it.tab == 0 && it.cardTitleRes == R.string.audio_effects && it.labelRes == R.string.settings_audio_skip_silence_label
        }
        assertTrue(searchSettingsEntries(french, french.getString(R.string.settings_audio_skip_silence_label)).contains(skipSilence))
        assertTrue(searchSettingsEntries(french, french.getString(R.string.settings_audio_skip_silence_description)).contains(skipSilence))
        val embedded = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.listen_together_server_url }
        assertTrue(searchSettingsEntries(french, french.getString(embedded.labelRes)).contains(embedded))
    }

    @Test fun dynamicBrightnessTitleAndDescriptionFindTheThemeControl() {
        val target = AllSettingsEntryTargets.single { it.id == "ui.theme.respect_brightness" }
        assertEquals(R.string.common_color_source_theme, target.cardTitleRes)
        listOf(R.string.settings_theme_keep_dark_mode_label, R.string.settings_theme_keep_dark_mode_description)
            .forEach { resource ->
                assertTrue(searchSettingsEntries(context, context.getString(resource)).contains(target))
            }
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
                val root = localized.getString(R.string.settings_title)
                val expectedPage = if (entry.id.startsWith("glass.")) localized.getString(R.string.glass_effect_title)
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
            assertTrue(searchSettingsEntries(context, context.getString(R.string.settings_debug_updater_preview_description)).contains(targets.single()))
        }
    }

    @Test fun searchFoldsCaseAndAccentsWithoutChangingStableIdentity() {
        val french = localized(Locale.FRENCH)
        val english = localized(Locale.ENGLISH)
        val target = EmbeddedSettingsEntryTargets.single { it.labelRes == R.string.maintenance_debug_logging_title }
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

    @Test fun sectionTitlesAvoidStaleUppercaseTranslationsWithoutChangingCardTargets() {
        val stableNames = mapOf(
            R.string.settings_section_troubleshooting_title to "troubleshooting",
            R.string.settings_section_proxy_title to "proxy",
            R.string.settings_section_contributors_title to "contributors",
            R.string.settings_section_player_action_bar_title to "player_action_bar",
            R.string.settings_section_smart_recommendations_title to "smart_recommendations",
            R.string.settings_section_service_lifetime_title to "service_lifetime",
            R.string.settings_section_notification_player_title to "notification_player",
            R.string.settings_section_backup_and_restore_title to "title_backup_and_restore",
            R.string.settings_section_fullscreen_lyrics_title to "full_screen_lyrics_components",
        )
        listOf(Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("pt")).forEach { locale ->
            val localized = localized(locale)
            stableNames.forEach { (titleResource, stableName) ->
                val card = SettingsCards.single { it.titleRes == titleResource }
                val translatedTitle = localized.getString(titleResource)
                assertEquals(translatedTitle, card.displayTitle(localized))
                assertFalse(translatedTitle.any(Char::isLetter) && translatedTitle == translatedTitle.uppercase(locale))
                assertTrue(searchSettingsCards(localized, translatedTitle).contains(card))
                assertTrue(searchSettingsCardResults(localized, translatedTitle).contains(card))
                assertEquals("card.${card.tab}.$stableName", settingsCardTargetId(localized, card.tab, card.titleRes))
            }
        }
        assertEquals(null, settingsSectionNavigationName(R.string.common_playback_section_title))
    }

    @Test fun artworkAndThemeSourcesFindEverySelectorAndKeepBackgroundStylesDistinct() {
        val english = localized(Locale.ENGLISH)
        val expectedTargets = setOf(
            "glass.tintSource",
            "appearance.colors_and_background.background_colors",
            "appearance.timeline_and_controls.player_controls_colors",
            "appearance.player_behavior_and_visuals.visualizer_white_color_option",
            "appearance.txt_lyrics.lyricscolor",
        )
        listOf(english.getString(R.string.common_color_source_artwork), english.getString(R.string.common_color_source_theme)).forEach { source ->
            val actualTargets = searchSettingsEntries(english, source).map { it.id }.toSet()
            assertTrue("Missing $source selectors: ${expectedTargets - actualTargets}", actualTargets.containsAll(expectedTargets))
        }
        mapOf(
            R.string.common_color_source_artwork to "Artwork",
            R.string.common_color_source_theme to "Theme",
            R.string.player_background_artwork_gradient_label to "Artwork gradient",
            R.string.player_background_theme_gradient_label to "Theme gradient",
            R.string.player_background_artwork_blurred_label to "Artwork blurred",
            R.string.player_background_artwork_fluid_gradient_label to "Artwork Fluid gradient",
            R.string.player_background_theme_fluid_gradient_label to "Theme Fluid gradient",
            R.string.player_background_artwork_m3e_morphing_label to "Artwork M3E morphing shapes",
            R.string.player_background_theme_m3e_morphing_label to "Theme M3E morphing shapes",
        ).forEach { (resource, label) -> assertEquals(label, english.getString(resource)) }
        val catalog = settingsEntryChoiceLabels(english::getString, Locale.ENGLISH)
        val backgrounds = catalog.getValue("appearance.colors_and_background.background_colors")
        assertTrue(backgrounds.containsAll(listOf(R.string.common_color_source_artwork, R.string.common_color_source_theme, R.string.player_background_artwork_gradient_label, R.string.player_background_theme_gradient_label, R.string.player_background_artwork_blurred_label).map(english::getString)))
        assertEquals(backgrounds.size, backgrounds.distinct().size)
        val animations = catalog.getValue("appearance.colors_and_background.gradienttype")
        assertTrue(animations.containsAll(listOf(R.string.player_background_artwork_fluid_gradient_label, R.string.player_background_theme_fluid_gradient_label, R.string.player_background_artwork_m3e_morphing_label, R.string.player_background_theme_m3e_morphing_label).map(english::getString)))
        assertEquals(animations.size, animations.distinct().size)
        animations.forEach { choice ->
            assertTrue(searchSettingsEntries(english, choice).any { it.id == "appearance.colors_and_background.gradienttype" })
        }
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
            "ai.quick_picks.quick_selection_type" to english.getString(R.string.recommendations_quick_picks_count, 3),
            "ui.settings_use_font_type.settings_use_font_type" to FontType.entries.first().name,
            "other.proxy.proxy_mode" to "SOCKS",
            "ai.statistics.statistics_max_number_of_items" to english.getString(R.string.statistics_items_limit_custom),
            "network.quality.audio_quality_format" to english.getString(R.string.audio_quality_format_high),
            "accounts.social_discord.discord_activity_type" to english.getString(R.string.discord_activity_watching),
            "embedded.profile.face_name_source" to english.getString(R.string.profile_name_source_local),
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
        val choice = arabic.getString(R.string.recommendations_quick_picks_count, 3)
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
            sectionTitleRes = R.string.library_favorites_title,
        ))
        val location = child.locationDescription(context)
        assertTrue(location.contains(context.getString(base.labelRes)))
        assertTrue(location.endsWith(context.getString(R.string.library_favorites_title)))
    }

    @Test fun glassPolicyResultsNameTheirAppearanceScopeAndDedicatedVisiblePath() {
        val entry = AllSettingsEntryTargets.single { it.id == "glass.destination.PlayerActions.ActionButtons" }
        assertEquals("${context.getString(R.string.glass_effect_title)} › ${context.getString(entry.labelRes)}",
            entry.searchResultTitle(context))
        assertEquals(listOf(R.string.settings_title, R.string.glass_effect_title, R.string.glass_effect_destination_playback)
            .joinToString(" › ") { context.getString(it) }, entry.locationDescription(context))
        assertFalse(entry.locationDescription(context).contains(context.getString(R.string.settings_category_interface)))
    }

    @Test fun functionalResultsKeepTheirVisibleLabelAndActualOrganizedCardPath() {
        val entry = AllSettingsEntryTargets.single { it.id == "general.audio_effects.skip_silence" }
        assertEquals(context.getString(R.string.settings_audio_skip_silence_label), entry.searchResultTitle(context))
        assertEquals(listOf(R.string.settings_title, R.string.settings_category_audio, R.string.audio_effects)
            .joinToString(" › ") { context.getString(it) }, entry.locationDescription(context))
        val audioQuality = AllSettingsEntryTargets.single { it.id == "network.quality.audio_quality_format" }
        assertEquals(listOf(R.string.settings_title, R.string.settings_network_title, R.string.settings_network_quality_title)
            .joinToString(" › ") { context.getString(it) }, audioQuality.locationDescription(context))
    }

}
