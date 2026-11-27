package app.n_zik.android.components.settings.common

import java.util.Locale
import app.n_zik.android.utils.secondsLabel
import app.n_zik.android.updater.models.UpdateEndpoints

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import app.n_zik.android.components.settings.appearance.PlayerSongSwipeSetting
import app.n_zik.android.components.ui.screens.bridge.BridgeAutoStopTarget
import app.n_zik.android.components.ui.screens.bridge.BridgePairedDevicesSettingsTarget
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.PresetsReverb
import app.n_zik.android.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsDetailedSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(locale: Locale): Context = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(locale) },
    )

    @Before
    fun enablePictureInPictureSupport() {
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
    }

    @Test fun globalResetChoicesUseTheSystemResetBreadcrumb() {
        val english = localized(Locale.ENGLISH)
        val expected = listOf(
            english.getString(R.string.settings_title),
            english.getString(R.string.settings_category_system),
            english.getString(R.string.settings_reset),
        ).joinToString(" › ")
        val resetTargets = setOf(SettingsResetAllTarget, SettingsResetChooserTarget, SettingsResetGlassTarget)
        val results = AllSettingsEntryTargets.filter { it.id in resetTargets }
        assertEquals(resetTargets, results.map { it.id }.toSet())
        results.forEach { assertEquals(expected, it.locationDescription(english)) }
    }

    @Test fun accountResetMatchesItsVisibleDeactivationNotice() {
        val english = localized(Locale.ENGLISH)
        val description = english.getString(R.string.settings_reset_account_usage_notice)
        val results = searchSettingsEntries(english, description)

        assertTrue(results.any { it.id == "accounts.settings_reset.settings_reset" })
    }

    @Test fun monthlyBackupSearchIncludesTheDaysOfferedByTheSelector() {
        val english = localized(Locale.ENGLISH)
        val label = english.getString(R.string.backup_auto_schedule_day_of_month)
        val target = "embedded.auto_backup.auto_backup_schedule_day_of_month"

        assertTrue(searchSettingsEntries(english, "$label 17").any { it.id == target })
        assertTrue(searchSettingsEntries(english, "$label 28").any { it.id == target })
        assertFalse(searchSettingsEntries(english, "$label 29").any { it.id == target })
    }

    @Test fun backupTimeSearchMatchesEachHourOfferedByTheSelector() {
        val english = localized(Locale.ENGLISH)
        val label = english.getString(R.string.backup_auto_schedule_time)
        val target = "embedded.auto_backup.auto_backup_schedule_time"
        for (hour in listOf("00:00", "03:00", "23:00")) {
            val entries = searchSettingsEntries(english, "$label $hour")
            assertTrue(hour, entries.any { it.id == target && it.parentCard("data").categoryId() == "data" })
        }
        assertFalse(searchSettingsEntries(english, "$label 24:00").any { it.id == target })
    }

    @Test fun customBackupIntervalSearchMatchesOnlyValuesOfferedByTheSelector() {
        val english = localized(Locale.ENGLISH)
        val label = english.getString(R.string.backup_auto_custom_interval_value)
        val target = "embedded.auto_backup.auto_backup_custom_interval_value"

        fun findsIntervalValue(query: String): Boolean = searchSettingsEntries(english, query).any {
            it.id == target && it.parentCard("data").categoryId() == "data"
        }

        for (value in 1..60) {
            assertTrue("$label $value", findsIntervalValue("$label $value"))
        }
        assertTrue(findsIntervalValue("37 $label"))
        assertTrue(findsIntervalValue("37"))
        assertFalse(findsIntervalValue("$label 0"))
        assertFalse(findsIntervalValue("$label 61"))
    }

    @Test fun backupIntervalSearchMatchesChoicesBeforeTheirSettingLabels() {
        val cases = listOf(
            Triple(
                "embedded.auto_backup.auto_backup_interval",
                R.string.backup_auto_interval,
                R.string.backup_auto_interval_monthly,
            ),
            Triple(
                "embedded.auto_backup.auto_backup_custom_interval_unit",
                R.string.backup_auto_custom_interval_unit,
                R.string.backup_auto_unit_weeks,
            ),
        )
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("qaa"))) {
            val resources = localized(locale)
            for ((target, labelRes, choiceRes) in cases) {
                val label = resources.getString(labelRes)
                val choice = resources.getString(choiceRes)
                val query = "$choice $label"

                assertTrue(query, searchSettingsEntries(resources, query).any {
                    it.id == target && it.parentCard("data").categoryId() == "data"
                })
            }
        }
    }

    @Test fun defaultPageSearchFollowsAvailableHomeTabsWithoutRebuildingTheIndex() {
        val english = localized(Locale.ENGLISH)
        val prefs = english.preferences
        val label = english.getString(R.string.settings_home_default_page_label)
        val artists = english.getString(R.string.library_artists_title)
        val quickPicks = english.getString(R.string.recommendations_title)
        val search = english.getString(R.string.search_title)
        val target = "ui.user_interface.default_page"
        fun findsDefaultPage(query: String): Boolean = searchSettingsEntries(english, query)
            .any { it.id == target && it.parentCard("interface").categoryId() == "interface" }

        assertTrue(findsDefaultPage("$label $artists"))
        assertTrue(findsDefaultPage("$label $quickPicks"))
        prefs.edit().putBoolean("hometab_artists_enabled", false).putBoolean(fullOfflineModeKey, true).apply()
        assertFalse(findsDefaultPage("$label $artists"))
        assertFalse(findsDefaultPage("$label $quickPicks"))
        assertTrue(findsDefaultPage("$label $search"))
        assertTrue(findsDefaultPage(label))

        prefs.edit().remove("hometab_artists_enabled").remove(fullOfflineModeKey).apply()
        assertTrue(findsDefaultPage("$artists $label"))
        assertTrue(findsDefaultPage("$quickPicks $label"))
    }

    @Test fun sectionHeadingsRouteToTheirOwnTargetAndQualifyChildBreadcrumbs() {
        val english = localized(Locale.ENGLISH)
        val group = english.getString(R.string.settings_group_audio_levels)
        val section = searchSettingsEntries(english, group).first()
        assertEquals("general.audio_effects.section.audio_levels", section.id)
        assertEquals(section.id, Uri.parse(section.destinationRoute(english)).getQueryParameter("target"))

        val row = searchSettingsEntries(english, english.getString(R.string.settings_audio_loudness_normalization_label))
            .first()
        assertTrue(row.locationDescription(english).endsWith(" › $group"))
    }

    @Test fun quickPicksResetKeepsItsStableLinkUnderRecommendationRefresh() {
        val target = AllSettingsEntryTargets.single { it.id == "ai.tab_data.reset_quick_picks" }
        assertEquals("ai", target.parentCard().categoryId())
        assertEquals(R.string.settings_section_quick_picks_refresh_title, target.cardTitleRes)
        val title = context.getString(target.cardTitleRes)
        val description = context.getString(R.string.recommendations_reset_content_description)
        assertTrue(target in searchSettingsEntries(context, description))
        assertTrue(target.cardTitleRes in visibleCardTitleRes(context, target.tab, description))
        assertTrue(target.cardTitleRes in visibleCardTitleRes(context, target.tab, title))
        assertEquals("card.3.tab_data", Uri.parse(target.destinationRoute(context)).getQueryParameter("parent"))
    }

    @Test fun entriesFollowTheirVisibleCardsAndTiedMatchesFollowRowOrder() {
        val english = localized(Locale.ENGLISH)
        val results = searchSettingsEntries(english, english.getString(R.string.modules_open))
        val openers = results.filter {
            it.id in setOf("embedded.profile.module_open", "general.picture_in_picture.open", "embedded.rewind.module_open")
        }
        assertEquals(
            listOf("embedded.profile.module_open", "general.picture_in_picture.open", "embedded.rewind.module_open"),
            openers.map { it.id },
        )
        assertTrue(openers[0].destinationRoute(english).startsWith("settingsCategory/profiles?"))
        assertTrue(openers[1].destinationRoute(english).startsWith("settingsCategory/player?"))
        assertTrue(openers[2].destinationRoute(english).startsWith("settingsCategory/rewind?"))

        val paletteMatches = searchSettingsEntries(english, english.getString(R.string.settings_theme_background_1_color_label))
            .filter { it.id == "ui.theme.color_background_1" || it.id == "ui.theme.color_background_1.dark" }
        assertEquals(listOf("ui.theme.color_background_1", "ui.theme.color_background_1.dark"), paletteMatches.map { it.id })
        val paletteOrder = AllSettingsEntryTargets
            .filter { it.cardTitleRes == R.string.common_color_source_theme }
            .map { it.id }
        assertTrue(paletteOrder.indexOf("ui.theme.color_accent") < paletteOrder.indexOf("ui.theme.title_reset_customized_light_colors"))
        assertTrue(paletteOrder.indexOf("ui.theme.title_reset_customized_light_colors") < paletteOrder.indexOf("ui.theme.color_background_1.dark"))
        assertEquals("ui.theme.title_reset_customized_dark_colors", paletteOrder.last())
    }

    @Test fun paletteResultsShowTheirLocalizedScopeAndMatchInEitherOrder() {
        val lightId = "ui.theme.color_background_1"
        val darkId = "ui.theme.color_background_1.dark"
        val paletteIds = setOf(lightId, darkId)
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = localized(locale)
            val label = localized.getString(R.string.settings_theme_background_1_color_label)
            val light = localized.getString(R.string.settings_theme_light_mode_label)
            val dark = localized.getString(R.string.settings_theme_dark_mode_label)
            fun matches(query: String) = searchSettingsEntries(localized, query).filter { it.id in paletteIds }

            assertEquals(listOf(lightId, darkId), matches(label).map { it.id })
            listOf(light to lightId, dark to darkId).forEach { (scope, id) ->
                val target = matches("$scope $label").single()
                assertEquals(id, target.id)
                assertEquals(listOf(target), matches("$label $scope"))
                assertEquals("$scope › $label", target.searchResultTitle(localized))
                assertEquals(listOf(target), matches(target.searchResultTitle(localized)))
                val expectedPath = listOf(
                    localized.getString(R.string.settings_title),
                    localized.getString(R.string.settings_category_interface),
                    localized.getString(R.string.common_color_source_theme),
                    localized.getString(R.string.settings_group_palette_selection),
                    scope,
                ).distinct().joinToString(" › ")
                assertEquals(expectedPath, target.locationDescription(localized))
                assertEquals(id, Uri.parse(target.destinationRoute(localized)).getQueryParameter("target"))
            }
        }
    }

    @Test fun movedQueuePreviewRowsKeepTheirStableIdsAndFocusWithinTheActionBar() {
        val ids = listOf(
            "appearance.player_behavior_and_visuals.show_next_songs_in_player",
            "appearance.player_behavior_and_visuals.songs_number_to_show",
            "appearance.player_behavior_and_visuals.showalbumcover",
        )
        val actionBar = cardsForCategory("player").single { it.titleRes == R.string.settings_section_player_action_bar_title }
        val order = AllSettingsEntryTargets.filter { it.tab == 2 && it.cardTitleRes == actionBar.titleRes }.map { it.id }
        val first = order.indexOf(ids.first())
        assertEquals(ids + "appearance.player_action_bar.tapqueue", order.drop(first).take(4))
        ids.forEach { id ->
            val entry = AllSettingsEntryTargets.single { it.id == id }
            val results = searchSettingsEntries(context, context.getString(entry.labelRes))
            assertTrue(entry in results)
            assertTrue(entry in results.filter { it.parentCard().categoryId() == "player" })
            val route = Uri.parse(entry.destinationRoute(context))
            assertEquals("settingsCategory/player", route.path)
            assertEquals(id, route.getQueryParameter("target"))
            assertEquals(settingsCardTargetId(context, actionBar.tab, actionBar.titleRes), route.getQueryParameter("parent"))
        }
    }

    @Test fun openPipIsSearchableGloballyAndOnItsPage() {
        val target = AllSettingsEntryTargets.single { it.id == "general.picture_in_picture.open" }
        listOf(Locale.ENGLISH to "open pip", Locale.FRENCH to "ouvrir pip").forEach { (locale, query) ->
            val localized = localized(locale)
            assertTrue(searchSettingsEntries(localized, query).contains(target))
            assertTrue(visibleCardTitleRes(localized, target.tab, query).contains(target.cardTitleRes))
            assertTrue(searchSettingsCards(localized, query).contains(target.parentCard()))
        }
    }

    @Test fun unsupportedPipActionDoesNotAppearInSearch() {
        Shadows.shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        val english = localized(Locale.ENGLISH)
        val query = english.getString(R.string.settings_picture_in_picture_open)

        assertFalse(searchSettingsEntries(english, query).any { it.id == "general.picture_in_picture.open" })
        assertFalse(R.string.settings_picture_in_picture_title in visibleCardTitleRes(english, 0, query))
    }

    @Test
    @Config(sdk = [32])
    fun backExitActionDoesNotAppearInSearchBeforeAndroid13() {
        val english = localized(Locale.ENGLISH)
        val query = english.getString(R.string.settings_behavior_back_button_exits_label)

        assertFalse(searchSettingsEntries(english, query).any { it.id == "general.app_behavior.close_app_with_back_button" })
        assertFalse(R.string.settings_app_behavior_title in visibleCardTitleRes(english, 0, query))
    }

    @Test fun trackTransitionScopeFindsTheSameResultsInEitherOrder() {
        val english = localized(Locale.ENGLISH)
        val scope = english.getString(R.string.settings_group_track_transitions)
        val reversed = scope.split(' ').reversed().joinToString(" ")

        assertEquals(searchSettingsEntries(english, scope), searchSettingsEntries(english, reversed))
        val cards = visibleCardTitleRes(english, 0, scope)
        assertTrue(R.string.common_playback_section_title in cards)
        assertEquals(cards, visibleCardTitleRes(english, 0, reversed))
    }

    @Test fun artworkSearchMatchesPartialWordsAndFindsTheDisplayedMotionSection() {
        val english = localized(Locale.ENGLISH)
        val id = "appearance.cover_and_thumbnail.carousel"
        val section = english.getString(R.string.settings_group_artwork_motion)
        val label = english.getString(R.string.settings_player_artwork_carousel_label)

        listOf("car vi", "vi car", "$section $label").forEach { query ->
            val result = searchSettingsEntries(english, query).single { it.id == id }
            assertTrue(result.locationDescription(english).contains(section))
        }
        assertFalse(searchSettingsEntries(english, "car caro").any { it.id == id })
    }

    @Test fun portraitOnlyPlayerSettingsAndTheirCardMatchesAreHiddenInLandscape() {
        fun oriented(orientationValue: Int): Context = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocale(Locale.ENGLISH)
                orientation = orientationValue
            },
        )
        val portrait = oriented(Configuration.ORIENTATION_PORTRAIT)
        val landscape = oriented(Configuration.ORIENTATION_LANDSCAPE)
        val queriesByTarget = mapOf(
            "appearance.player_behavior_and_visuals.blankspace" to listOf(
                R.string.settings_lyrics_top_spacing_label,
                R.string.player_top_spacing_description,
            ),
            "appearance.cover_and_thumbnail.swipe_Animation_No_Thumbnail" to listOf(
                R.string.player_artwork_hidden_swipe_animation,
                R.string.player_artwork_transition_circular,
            ),
            "appearance.cover_and_thumbnail.carousel" to listOf(R.string.settings_player_artwork_carousel_label),
            "appearance.cover_and_thumbnail.carouselsize" to listOf(R.string.settings_player_expanded_artwork_size_label),
        )
        queriesByTarget.forEach { (targetId, queryResources) ->
            val entry = AllSettingsEntryTargets.single { it.id == targetId }
            queryResources.forEach { resource ->
                val query = portrait.getString(resource)
                assertTrue(targetId, entry in searchSettingsEntries(portrait, query))
                assertFalse(targetId, entry in searchSettingsEntries(landscape, query))
                assertTrue(targetId, entry.parentCard() in searchSettingsCardResults(portrait, query))
                assertFalse(targetId, entry.parentCard() in searchSettingsCardResults(landscape, query))
            }
        }
    }

    @Test fun audioSearchFindsOptionsByTheirDialogDescriptions() {
        val english = localized(Locale.ENGLISH)
        val expected = mapOf(
            R.string.settings_audio_reverb_info_apply_a_depth_effect_to_the_audio to "general.audio_effects.settings_audio_reverb",
            R.string.settings_playback_pause_between_songs_description to "general.playback.pause_between_songs",
        )
        expected.forEach { (description, id) ->
            assertTrue(searchSettingsEntries(english, english.getString(description)).any { it.id == id })
        }
    }

    @Test fun networkStatusDescriptionsFindTheControlsShowingEffectiveQuality() {
        val english = localized(Locale.ENGLISH)
        val rows = listOf(
            Triple(R.string.network_current_artwork_quality_description, "network.quality.image_quality_format", "network"),
            Triple(R.string.network_current_stream_audio_quality_description, "network.quality.audio_quality_format", "network"),
            Triple(R.string.network_current_download_audio_quality_description, "embedded.network.download_quality_format", "downloads"),
        )
        rows.forEach { (description, target, category) ->
            val query = english.getString(description)
            val result = searchSettingsEntries(english, query).single { it.id == target }
            val route = Uri.parse(result.destinationRoute(english))
            assertEquals("settingsCategory/$category", route.path)
            assertEquals(target, route.getQueryParameter("target"))
            assertTrue(searchSettingsCardResults(english, query).contains(result.parentCard()))
        }

        val ids = searchSettingsEntries(english, english.getString(R.string.network_status)).map { it.id }
        assertTrue(ids.containsAll(rows.map { it.second }))
        assertFalse(ids.any { it.startsWith("network.network_status.") })
    }

    @Test fun storageSectionsAndCleanupActionsAreSearchableGloballyAndInData() {
        val labels = listOf(
            R.string.storage_breakdown_subtitle, R.string.storage_cleanup_profile_title,
            R.string.storage_cleanup_diagnostic_logs, R.string.storage_cleanup_clear_temporary_files,
            R.string.storage_cleanup_all, R.string.storage_cleanup_clear_network_cache,
            R.string.storage_cleanup_picker_title, R.string.storage_cleanup_select_caches,
            R.string.storage_cleanup_option_theme_catalog, R.string.storage_cleanup_option_fetched_lyrics,
        )
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH)) {
            val localized = localized(locale)
            for (label in labels) {
                val query = localized.getString(label)
                assertTrue(query, searchSettingsCardResults(localized, query).any { it.titleRes == R.string.storage_breakdown_title } ||
                    searchSettingsEntries(localized, query).any { it.cardTitleRes == R.string.storage_breakdown_title })
                assertTrue(R.string.storage_breakdown_title in visibleCardTitleRes(localized, 4, query))
            }
        }
    }

    @Test fun resultTitlesMatchTheVisibleStreamingCacheActionAndAboutAuthorLink() {
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = localized(locale)
            val cleanupLabel = localized.getString(R.string.storage_cleanup_clear_streaming_cache)
            val cleanup = searchSettingsEntries(localized, cleanupLabel).single { it.id == "data.cache.clear-songs" }
            assertEquals(cleanupLabel, cleanup.searchResultTitle(localized))
            assertTrue(cleanup in searchSettingsEntries(localized, localized.getString(R.string.cache_clear_song_dialog_title)))

            val author = searchSettingsEntries(localized, UpdateEndpoints.REAL_OWNER).single { it.id == AboutAuthorSettingsTarget }
            val authorLabel = aboutAuthorSearchLabel(localized::getString)
            assertEquals(authorLabel, author.searchResultTitle(localized))
            assertTrue(author in searchSettingsEntries(localized, authorLabel))
            assertEquals("settingsCategory/about", Uri.parse(author.destinationRoute(localized)).path)
        }
    }

    @Test fun allEntriesHaveUniqueIdsAndReachableParentCards() {
        assertTrue(SettingsEntryTargets.isNotEmpty())
        assertTrue(EmbeddedSettingsEntryTargets.isNotEmpty())
        val entries = AllSettingsEntryTargets
        val registered = SettingsEntryTargets + EmbeddedSettingsEntryTargets + AboutSettingsEntryTargets +
            PrivacySettingsEntryTargets + app.n_zik.android.components.settings.ui.GlassSearchOptions.map { it.target } +
            SettingsNestedEntryTargets
        assertEquals(registered.toSet(), entries.toSet())
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

    @Test fun customArtworkQualityIsSearchableGloballyAndInData() {
        val target = AllSettingsEntryTargets.single { it.id == "data.cache.artwork_quality" }
        val labels = listOf(target.labelRes, R.string.storage_image_quality_optimized)
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH)) {
            val localized = localized(locale)
            for (label in labels) {
                val query = localized.getString(label)
                assertTrue(target in searchSettingsEntries(localized, query))
                assertTrue(target.cardTitleRes in visibleCardTitleRes(localized, target.tab, query))
            }
        }
    }

    @Test fun profileBackupAndOnDeviceEntriesAreSearchableGloballyAndInData() {
        val entries = AllSettingsEntryTargets.filter {
            it.id.startsWith("embedded.auto_backup.") || it.id.startsWith("other.on_device.")
        }
        assertTrue(entries.any { it.id == "embedded.auto_backup.include_github_credentials" })
        assertTrue(entries.any { it.id == "other.on_device.summary" })
        for (locale in listOf(Locale.ENGLISH, Locale.FRENCH)) {
            val localized = localized(locale)
            for (entry in entries) {
                val query = localized.getString(entry.labelRes)
                assertEquals(R.string.storage_breakdown_title, entry.cardTitleRes)
                assertTrue(entry.id, entry in searchSettingsEntries(localized, query))
                assertTrue(entry.id, entry.cardTitleRes in visibleCardTitleRes(localized, 4, query))
            }
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

    @Test fun rightToLeftLayoutSearchTargetsTheInterfaceSwitch() {
        val target = AllSettingsEntryTargets.single { it.id == "ui.user_interface.right_to_left" }
        assertEquals(R.string.settings_interface_title, target.cardTitleRes)
        for (resource in listOf(R.string.settings_interface_right_to_left_label, R.string.settings_interface_right_to_left_description)) {
            val query = context.getString(resource)
            assertTrue(target in searchSettingsEntries(context, query))
            assertTrue(target.cardTitleRes in visibleCardTitleRes(context, target.tab, query))
        }
        val route = Uri.parse(target.destinationRoute(context))
        assertEquals("settingsCategory/interface", route.path)
        assertEquals(target.id, route.getQueryParameter("target"))
    }

    @Test fun everyRegisteredOptionIsSearchableInEnglishAndFrenchInItsOrganizedSection() {
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = localized(locale)
            val inactiveSwipe = PlayerSongSwipeSetting.entries.single {
                it != PlayerSongSwipeSetting.forLayout(
                    localized.preferences.getEnum(playerTypeKey, PlayerType.Essential),
                    localized.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                )
            }
            AllSettingsEntryTargets.forEach visibleTargets@{ entry ->
                if (entry.id == inactiveSwipe.target) return@visibleTargets
                val results = searchSettingsEntries(localized, localized.getString(entry.labelRes))
                assertTrue("Missing $locale search target ${entry.id}", results.contains(entry))
                val card = entry.parentCard()
                val category = OrganizedSettingsCategories.single { it.id == card.categoryId() }
                val location = entry.locationDescription(localized)
                val root = localized.getString(R.string.settings_title)
                val expectedPage = if (entry.id.startsWith("glass.")) localized.getString(R.string.glass_effect_title)
                    else localized.getString(category.labelRes)
                assertTrue("Wrong page for ${entry.id}: $location", location.startsWith("$root › $expectedPage"))
                val resetChoice = entry.id in setOf(
                    SettingsResetAllTarget, SettingsResetChooserTarget,
                    SettingsResetGlassTarget, SettingsResetListenTogetherTarget, SettingsResetPlaybackTarget,
                    SettingsResetDownloadsTarget, SettingsResetPrivacyTarget,
                )
                val parent = if (resetChoice) localized.getString(R.string.settings_reset) else card.displayTitle(localized)
                assertTrue("Missing parent card for ${entry.id}: $location", location.contains(parent))
                card.groupTitleRes?.takeUnless { resetChoice }?.let { groupTitle ->
                    assertTrue("Missing card subgroup for ${entry.id}: $location", localized.getString(groupTitle) in location.split(" › "))
                }
            }
        }
    }

    @Test fun theObsoleteUpdaterPreviewIsNoLongerIndexed() {
        assertTrue(EmbeddedSettingsEntryTargets.none { it.id == "embedded.maintenance.debug_updater_layout_access" })
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

    @Test fun sectionTitlesPreserveTranslationsWithoutChangingCardTargets() {
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
        val english = localized(Locale.ENGLISH)
        stableNames.keys.forEach { titleResource ->
            val sourceTitle = english.getString(titleResource)
            assertFalse(sourceTitle.any(Char::isLetter) && sourceTitle == sourceTitle.uppercase(Locale.ENGLISH))
        }
        listOf(Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("pt")).forEach { locale ->
            val localized = localized(locale)
            stableNames.forEach { (titleResource, stableName) ->
                val card = SettingsCards.single { it.titleRes == titleResource }
                val translatedTitle = localized.getString(titleResource)
                assertEquals(translatedTitle, card.displayTitle(localized))
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
        val catalog = settingsEntryChoiceLabels(english::getString, Locale.US) { id, count -> english.resources.getQuantityString(id, count, count) }
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
        val catalog = settingsEntryChoiceLabels(english::getString, Locale.US) { id, count -> english.resources.getQuantityString(id, count, count) }
        assertTrue(catalog.isNotEmpty())
        assertTrue(AllSettingsEntryTargets.map { it.id }.containsAll(catalog.keys))
        val samples = mapOf(
            "data.cache.image_cache_max_size" to "32MB",
            "general.playback.pause_between_songs" to secondsLabel(5, Locale.US),
            "general.playback.crossfade_duration" to secondsLabel(12, Locale.US),
            "ai.quick_picks.quick_selection_type" to english.resources.getQuantityString(R.plurals.recommendations_quick_picks_song_count, 3, 3),
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
        val choice = arabic.resources.getQuantityString(R.plurals.recommendations_quick_picks_song_count, 3, 3)
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
            } else if (entry.id == BridgePairedDevicesSettingsTarget || BridgeAutoStopTarget.entries.any { it.id == entry.id }) {
                assertEquals(app.n_zik.android.enums.NavRoutes.bridgeServer.name, uri.path)
            } else {
                val card = entry.parentCard()
                assertEquals("settingsCategory/${card.categoryId()}", uri.path)
                assertEquals(settingsCardTargetId(context, card.tab, card.titleRes), uri.getQueryParameter("parent"))
            }
        }
    }

    @Test fun pairedDevicesSearchTargetsTheBridgeCardFromNetwork() {
        val english = localized(Locale.ENGLISH)
        val query = english.getString(R.string.bridge_paired_devices)
        val target = searchSettingsEntries(english, query).single { it.id == BridgePairedDevicesSettingsTarget }
        assertEquals("network", target.parentCard().categoryId())
        assertEquals(R.string.bridge_server, target.parentCard().titleRes)
        assertEquals(query, target.searchResultTitle(english))
        assertEquals(target.destinationRoute(english), target.destinationRoute(english, "network"))
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
        assertEquals(listOf(R.string.settings_title, R.string.settings_category_audio, R.string.audio_effects, R.string.settings_group_focus_and_silence)
            .joinToString(" › ") { context.getString(it) }, entry.locationDescription(context))
        val audioQuality = AllSettingsEntryTargets.single { it.id == "network.quality.audio_quality_format" }
        assertEquals(listOf(R.string.settings_title, R.string.settings_network_title, R.string.settings_network_quality_title, R.string.settings_group_streaming_quality)
            .joinToString(" › ") { context.getString(it) }, audioQuality.locationDescription(context))
    }

    @Test fun imageSettingsWithTheSameRankKeepTheirVisibleOrder() {
        val quality = "data.cache.artwork_quality"
        val limit = "data.cache.image_cache_max_size"
        val ids = setOf(quality, limit)
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val matches = searchSettingsEntries(localized(locale), "image").map { it.id }.filter { it in ids }
            assertEquals(listOf(quality, limit), matches)
        }
    }

}
