package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.NavRoutes
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
import app.n_zik.android.components.settings.common.UpdaterSettingsOption
import app.n_zik.android.components.settings.common.updaterSettingsOptionForQuery
import app.n_zik.android.components.settings.common.RewindNotificationSectionTarget
import app.n_zik.android.components.settings.common.RewindPlaylistSectionTarget
import app.n_zik.android.components.settings.common.duplicatesCategoryResult
import app.n_zik.android.components.settings.common.SettingsGlassItem
import app.n_zik.android.components.settings.common.SettingsThemeStoreCreatorItem
import app.n_zik.android.components.settings.common.SettingsThemeStoreItem
import app.n_zik.android.components.settings.common.SETTINGS_GLASS_ROUTE
import app.n_zik.android.components.settings.common.SETTINGS_THEME_STORE_ROUTE
import app.n_zik.android.components.settings.common.isGlassSettingsLink
import app.n_zik.android.components.settings.common.hasAutoUpdate
import app.n_zik.android.components.settings.common.isThemeStoreCreatorPage
import app.n_zik.android.components.settings.common.searchSettingsCardResults
import app.n_zik.android.components.settings.common.searchSettingsCards
import app.n_zik.android.components.settings.common.SettingsResetListenTogetherTarget
import app.n_zik.android.components.settings.common.destinationRoute
import app.n_zik.android.components.settings.common.SettingsEntryTarget
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.common.parentCard
import app.n_zik.android.components.settings.common.searchSettingsPageItems
import app.n_zik.android.components.settings.common.visibleCardTitleRes
import app.n_zik.android.components.settings.appearance.PlayerSongSwipeSetting
import app.n_zik.android.components.settings.ui.GlassSearchOptions
import app.n_zik.android.components.settings.ui.glassDestinationKeywordResources
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.listentogether.ListenTogetherServers
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import java.util.Locale
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
    fun youtubeAccountAndSyncDescriptionsFindTheirEntriesAndPage() {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        val entries = mapOf(
            R.string.account_youtube_change_account_description to R.string.account_youtube_change_account,
            R.string.account_youtube_sync_build_required to R.string.sync_data_with_ytm_account,
        )
        entries.forEach { (description, title) ->
            val query = english.getString(description)
            assertTrue("$query should find a YouTube card globally", searchSettingsCards(english, query).any { it.categoryId() == "youtube" })
            val inYouTubePage = searchSettingsEntries(english, query)
                .filter { it.parentCard().categoryId() == "youtube" }
            assertTrue("$query should find the corresponding entry on the YouTube page", inYouTubePage.any { it.labelRes == title })
        }
    }

    @Test
    fun unsupportedYouTubeSyncDetailsAreHiddenButTheMasterStaysDiscoverable() {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        mapOf(
            R.string.sync_status_detail to R.string.sync_data_with_ytm_account,
            R.string.sync_channels to R.string.sync_imported_playlists_automatic_title,
            R.string.sync_import_history to R.string.sync_import_from_youtube,
            R.string.sync_push_song_like to R.string.sync_push_to_youtube,
        ).forEach { (label, card) ->
            val query = english.getString(label)
            fun matchesSyncRow(entry: SettingsEntryTarget) = entry.labelRes == label && entry.cardTitleRes == card
            assertTrue(query, searchSettingsEntries(english, query, youtubeSyncSupported = true).any(::matchesSyncRow))
            assertTrue(query, searchSettingsCards(english, query, youtubeSyncSupported = true).any { it.titleRes == card })
            assertTrue(query, searchSettingsEntries(english, query, youtubeSyncSupported = false).none(::matchesSyncRow))
            if (card != R.string.sync_data_with_ytm_account) {
                assertTrue(query, searchSettingsCards(english, query, youtubeSyncSupported = false).none { it.titleRes == card })
            }
        }

        val master = english.getString(R.string.sync_data_with_ytm_account)
        assertTrue(searchSettingsEntries(english, master, youtubeSyncSupported = false)
            .any { it.labelRes == R.string.sync_data_with_ytm_account })
        assertTrue(searchSettingsCards(english, master, youtubeSyncSupported = false)
            .any { it.titleRes == R.string.sync_data_with_ytm_account })
    }

    @Test
    fun lastFmSearchOnlyIndexesBuildsWithBothCredentials() {
        val title = context.getString(R.string.settings_lastfm_title)
        val query = context.getString(R.string.lastfm_enable_scrobbling)
        for ((key, secret) in listOf("test-key" to "test-secret", "" to "test-secret", "test-key" to "")) {
            val available = isLastFmSettingsAvailable(key, secret)
            assertEquals(key.isNotBlank() && secret.isNotBlank(), available)
            assertEquals(available, searchSettingsCardResults(context, title, lastFmSupported = available)
                .any { it.titleRes == R.string.settings_lastfm_title })
            assertEquals(available, searchSettingsEntries(context, query, youtubeSyncSupported = true, lastFmSupported = available)
                .any { it.id == "embedded.lastfm.lastfm_enable_scrobbling" && it.parentCard().categoryId() == "services" })
        }
    }

    @Test
    fun deviceWideListenTogetherResetIsSearchableUnderSystem() {
        val query = "${context.getString(R.string.settings_reset)} ${context.getString(R.string.listen_together_title)}"
        val target = searchSettingsEntries(context, query).single { it.id == SettingsResetListenTogetherTarget }
        assertTrue(target.destinationRoute(context).startsWith("settingsCategory/system?"))
    }

    @Test
    fun glassChoiceLabelsDoNotGetTheHigherKeywordRank() {
        assertTrue(GlassSearchOptions.any { it.choiceRes.isNotEmpty() })
        assertTrue(GlassSearchOptions.all { option -> option.choiceRes.none(option.target.keywordRes::contains) })
    }

    @Test
    fun theGlobalPageListsProfileAboutUpdateAndThematicDestinations() {
        assertEquals(20, SettingsPageItems.size)
        assertEquals(listOf("profiles", "about"), SettingsPageItems.take(2).map { it.categoryId })
        assertTrue(SettingsPageItems[2] === SettingsUpdateItem)
        assertEquals(
            listOf("playback", "audio", "behavior", "interface", "player", "privacy", "data", "downloads", "network",
                "youtube", "services", "ai", "rewind", "maintenance", "system"),
            SettingsPageItems.drop(3).filter { it != SettingsGlassItem && it != SettingsThemeStoreItem }.map { it.categoryId },
        )
        assertEquals(NavRoutes.updater.name, SettingsUpdateItem.route)
        assertEquals(null, SettingsUpdateItem.tab)
        assertTrue(SettingsPageItems.filter { it !== SettingsUpdateItem && it !== SettingsGlassItem && it !== SettingsThemeStoreItem }.all { it.tab == null && it.route == null })
        // Legacy deep links retain their original tab numbers independently of hub order.
        assertEquals((0..8).toList(), SettingsCategories.map { it.tab })
    }

    @Test
    fun glassHasItsOwnDestinationBesideInterfaceAndSearchFindsItsControls() {
        val interfaceIndex = SettingsPageItems.indexOfFirst { it.categoryId == "interface" }
        assertEquals(SettingsThemeStoreItem, SettingsPageItems[interfaceIndex + 1])
        assertEquals(SettingsGlassItem, SettingsPageItems[interfaceIndex + 2])
        assertEquals(SettingsPageItems[interfaceIndex].group, SettingsThemeStoreItem.group)
        assertEquals(SettingsPageItems[interfaceIndex].group, SettingsGlassItem.group)
        assertEquals(SETTINGS_GLASS_ROUTE, SettingsGlassItem.route)
        assertTrue(cardsForCategory("interface").none { it.titleRes == R.string.glass_effect_title })
        listOf(R.string.glass_effect_title, R.string.glass_effect_enabled_title, R.string.glass_effect_refraction_depth, R.string.glass_effect_active_tab_indicator).forEach { label ->
            assertTrue(searchSettingsPageItems(context, context.getString(label)).contains(SettingsGlassItem))
        }
        listOf("glass", "liquid_glass", "card.1.liquid_glass").forEach { focus ->
            assertTrue(isGlassSettingsLink(1, focus))
        }
        assertTrue(!isGlassSettingsLink(1, "burger"))
        assertTrue(!isGlassSettingsLink(0, "glass"))
    }

    @Test
    @Config(sdk = [32])
    fun glassSearchHidesRefractionOnDevicesWithBlurOnly() {
        val refraction = context.getString(R.string.glass_effect_refraction_depth)
        val blur = context.getString(R.string.glass_effect_frost)

        assertTrue(searchSettingsEntries(context, refraction).none { it.id == "glass.refractionDepth" })
        assertTrue(SettingsGlassItem !in searchSettingsPageItems(context, refraction))
        assertTrue(searchSettingsEntries(context, blur).any { it.id == "glass.blur" })
    }

    @Test
    fun glassModeSearchMatchesItsDestinationGroupInEitherOrder() {
        val mode = context.getString(R.string.glass_effect_mode_contour_only)
        val group = context.getString(R.string.glass_effect_destination_navigation)
        fun modeResults(query: String) = searchSettingsEntries(context, query)
            .filter { it.id.startsWith("glass.") }.map { it.id }

        val row = context.getString(R.string.glass_effect_navigation_bars)
        for (scope in listOf(group, row)) {
            val modeFirst = modeResults("$mode $scope")
            assertTrue(modeFirst.isNotEmpty())
            assertEquals(modeFirst, modeResults("$scope $mode"))
        }
    }

    @Test
    fun everyVisibleGlassDestinationAndRoleFindsTheDedicatedPage() {
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(locale) },
            )
            glassDestinationKeywordResources().forEach { label ->
                val query = localized.getString(label)
                assertTrue("$locale: $query must find the Glass page",
                    searchSettingsPageItems(localized, query).contains(SettingsGlassItem))
            }
        }
    }

    @Test
    fun contextualGlassQueriesFindThePageWithoutRequiringTheWordGlass() {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        listOf("Temporary messages", "information panel", "Visualizer", "First launch").forEach { query ->
            val results = searchSettingsPageItems(english, query)
            assertEquals(query, listOf(SettingsGlassItem), results)
            assertEquals(SETTINGS_GLASS_ROUTE, results.single().route)
        }
    }

    @Test
    fun creatorActionsFindTheCreatorTabInBothEnglishAndFrench() {
        assertTrue(SettingsThemeStoreCreatorItem !in SettingsPageItems)
        assertEquals("${NavRoutes.themeStore.name}?tab=creator", SettingsThemeStoreCreatorItem.route)
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(locale) },
            )
            val store = localized.getString(SettingsThemeStoreItem.labelRes)
            val creator = localized.getString(SettingsThemeStoreCreatorItem.labelRes)
            listOf(
                "$store $creator",
                "$creator › $store",
            ).forEach { query ->
                assertTrue("$locale: $query", SettingsThemeStoreCreatorItem in searchSettingsPageItems(localized, query))
            }
            mapOf(
                R.string.theme_store_creator_profile to "profile",
                R.string.theme_store_edit_creator to "profile",
                R.string.theme_store_creator_identity to "public-information",
                R.string.theme_store_creator_publish to "publish-profile",
                R.string.theme_store_creator_publish_theme to "publish",
                R.string.theme_store_creator_submissions to "submissions",
                R.string.theme_store_create_local to "create-local",
                R.string.theme_store_edit_local to "edit-local",
                R.string.theme_store_local_themes to "create-local",
                R.string.theme_store_creator_my_themes to "my-themes",
            ).forEach { (label, target) ->
                val query = localized.getString(label)
                val result = searchSettingsPageItems(localized, query).single { it.isThemeStoreCreatorPage() }
                assertEquals("$locale: $query", "${SettingsThemeStoreCreatorItem.route}&target=$target", result.route)
                assertEquals("$locale: $query", label, result.labelRes)
            }
            assertTrue(
                SettingsThemeStoreCreatorItem !in searchSettingsPageItems(localized, store),
            )
        }
        val french = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.FRENCH) },
        )
        assertTrue(searchSettingsPageItems(french, "profil auteur").any { it.isThemeStoreCreatorPage() })
    }

    @Test
    fun updaterSearchMatchesDisplayedStatusesAndInstalledVersion() {
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        )
        val automaticCheck = english.getString(R.string.updates_automatic_check_title)
        val betaUpdates = english.getString(R.string.updates_beta_enabled_label)
        val redownload = english.getString(R.string.updates_action_download_current_version)
        val automaticStates = listOf(R.string.common_state_on, R.string.updates_check_mode_ask, R.string.common_state_off)
        val queries = automaticStates.map { "$automaticCheck: ${english.getString(it)}" to UpdaterSettingsOption.AutomaticCheck } +
            listOf(R.string.common_state_on, R.string.common_state_off).map {
                "$betaUpdates: ${english.getString(it)}" to UpdaterSettingsOption.BetaUpdates
            } + ("$redownload (1.18.3)" to UpdaterSettingsOption.DownloadCurrentVersion)
        queries.forEach { (query, target) ->
            assertEquals(query, target, updaterSettingsOptionForQuery(english, query, autoUpdate = true, versionName = "1.18.3"))
            assertEquals(query, null, updaterSettingsOptionForQuery(english, query, autoUpdate = false, versionName = "1.18.3"))
        }
    }

    @Test
    fun thematicGroupsAreCompleteAndKeepProfileAboutAndUpdateTogether() {
        assertEquals((1..7).toList(), SettingsPageGroups.map { it.group })
        assertEquals(SETTINGS_GROUP_ABOUT_UPDATE, SettingsUpdateItem.group)
        assertEquals(listOf(R.string.profile_title, R.string.about_title, R.string.common_action_update), SettingsPageItems.filter { it.group == 1 }.map { it.labelRes })
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
        val systemCards = cardsForCategory("system")
        assertEquals(resets, systemCards.takeLast(resets.size))
        assertEquals(R.string.settings_section_service_lifetime_title, systemCards.dropLast(resets.size).last().titleRes)
        assertTrue(cardsForCategory("profiles").all { it.titleRes == R.string.profile_face })
        assertTrue(cardsForCategory("player").any { it.tab == 0 })
        assertTrue(cardsForCategory("player").any { it.tab == 2 })
    }

    @Test fun theCardSearchFindsNamedSettingsGroups() {
        val indicators = context.getString(R.string.settings_interface_group_indicators)
        val queuePreview = context.getString(R.string.settings_group_queue_preview)
        assertTrue(searchSettingsCards(context, indicators).any { it.titleRes == R.string.settings_interface_title })
        assertTrue(searchSettingsCards(context, queuePreview).any { it.titleRes == R.string.settings_section_player_action_bar_title })
        val restoreDefaults = context.getString(R.string.settings_group_restore_defaults)
        assertTrue(searchSettingsCards(context, restoreDefaults).all { it.titleRes == R.string.settings_reset })
        assertTrue(searchSettingsCards(context, restoreDefaults).any { it.titleRes == R.string.settings_reset })
    }

    @Test fun cardSearchIgnoresAccentsInLocalizedKeywords() {
        val french = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.FRENCH) },
        )
        val label = french.getString(R.string.settings_section_service_lifetime_title)
        val query = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        assertTrue(label != query)
        assertTrue(searchSettingsCards(french, query).any { it.titleRes == R.string.settings_section_service_lifetime_title })
    }

    @Test fun privacyContainsHistoryControlsAndCaptureProtection() {
        val cards = cardsForCategory("privacy")
        assertTrue(cards.any { it.tab == 4 && it.titleRes == R.string.library_history_title })
        assertTrue(cards.none { it.tab == 3 && it.titleRes == R.string.settings_data_title })
        assertTrue(cardsForCategory("ai").any { it.tab == 3 && it.titleRes == R.string.settings_section_quick_picks_refresh_title })
        assertTrue(cards.any { it.titleRes == R.string.permissions_usage_journal })
        assertTrue(cards.any { it.titleRes == R.string.privacy_screen_protection })
        assertTrue(cardsForCategory("rewind").any { it.titleRes == R.string.rewind_settings_title })
    }

    @Test
    fun themeStoreIsDiscoverableByItsVisibleTitle() {
        val title = context.getString(R.string.theme_store_title)
        assertEquals(SETTINGS_THEME_STORE_ROUTE, searchSettingsPageItems(context, title)
            .single { it == SettingsThemeStoreItem }.route)
    }

    @Test
    fun thePageItemSearchMatchesOnlyTheUpdateItem() {
        val results = searchSettingsPageItems(context, "update")
        assertEquals(listOf(SettingsUpdateItem), results)

        val actionResults = if (hasAutoUpdate()) listOf(SettingsUpdateItem) else emptyList()
        assertEquals(actionResults, searchSettingsPageItems(context, "CHECK UPDATE"))
        assertEquals(actionResults, searchSettingsPageItems(context, "update available"))

        // Category labels are not part of the item-level index.
        assertTrue(searchSettingsPageItems(context, context.getString(R.string.about_title)).isEmpty())

        // A blank or non-matching query returns no item results.
        assertTrue(searchSettingsPageItems(context, "").isEmpty())
        assertTrue(searchSettingsPageItems(context, "   ").isEmpty())
        assertTrue(searchSettingsPageItems(context, "zzz_no_match").isEmpty())
    }

    @Test
    fun displayedProxyAndServerChoicesFindTheExactSettings() {
        val regionChoice = context.getString(R.string.network_proxy_region_auto)
        val server = ListenTogetherServers.servers.first()
        val serverChoice = "${server.name} - ${context.getString(server.locationRes)}"
        assertTrue(searchSettingsEntries(context, regionChoice).any { it.id == "other.proxy.settings_region_override" })
        assertTrue(searchSettingsEntries(context, serverChoice).any { it.id == "embedded.listen_together.listen_together_server_url" })
    }

    @Test
    fun rewindSectionTitlesFindTheirOwnTargets() {
        val sections = mapOf(
            R.string.rewind_settings_playlists_title to RewindPlaylistSectionTarget,
            R.string.rewind_settings_notifications to RewindNotificationSectionTarget,
        )
        sections.forEach { (label, target) ->
            val query = context.getString(label)
            val result = searchSettingsEntries(context, query).single { it.id == target }
            assertTrue(result.destinationRoute(context).startsWith("settingsCategory/rewind?target=$target"))
        }
    }

    @Test
    fun theSearchKeepsDistinctCardsAndCoalescesUnqualifiedCategoryLabels() {
        val categories = OrganizedSettingsCategories.filter { it.id in setOf("playback", "downloads", "about") }
        categories.forEach { category ->
            val exactCard = cardsForCategory(category.id).single { it.titleRes == category.labelRes }
            assertTrue(exactCard.duplicatesCategoryResult(context, categories))
            assertTrue(cardsForCategory(category.id).filter { it != exactCard }.none { it.duplicatesCategoryResult(context, categories) })
        }
    }

    @Test
    fun onlyTheSwipeDirectionOfferedByTheCurrentLayoutIsSearchable() {
        val config = Configuration(context.resources.configuration)
        listOf(Configuration.ORIENTATION_PORTRAIT, Configuration.ORIENTATION_LANDSCAPE).forEach { orientation ->
            config.orientation = orientation
            val screen = context.createConfigurationContext(config)
            listOf(PlayerType.Essential, PlayerType.Modern).forEach { layout ->
                screen.preferences.edit { putEnum(playerTypeKey, layout) }
                try {
                    val visible = if (layout == PlayerType.Modern && orientation == Configuration.ORIENTATION_PORTRAIT) {
                        PlayerSongSwipeSetting.Vertical
                    } else {
                        PlayerSongSwipeSetting.Horizontal
                    }
                    PlayerSongSwipeSetting.entries.forEach { setting ->
                        val matches = searchSettingsEntries(screen, screen.getString(setting.labelRes))
                        assertEquals(setting == visible, matches.any { it.id == setting.target })
                    }
                } finally {
                    screen.preferences.edit { remove(playerTypeKey) }
                }
            }
        }
    }

    @Test
    fun theCardSearchMatchesTitlesAndKeywords() {
        assertTrue("a blank query returns no card results", searchSettingsCards(context, "").isEmpty())

        val query = context.getString(R.string.settings_audio_skip_silence_label)
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
        assertTrue(cards.none { it.titleRes == R.string.profile_title })
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
            visibleCardTitleRes(context, 0, context.getString(R.string.settings_audio_skip_silence_label))
        )
    }
}
