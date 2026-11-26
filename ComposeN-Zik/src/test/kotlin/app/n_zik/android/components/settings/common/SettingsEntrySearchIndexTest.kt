package app.n_zik.android.components.settings.common

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import app.n_zik.android.components.settings.appearance.PlayerSongSwipeSetting
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.settings.LastFmSettingsLogoutTarget
import app.n_zik.android.components.settings.lastFmSettingsSearchFallback
import app.n_zik.android.components.settings.accounts.discordSettingsSearchFallback
import app.n_zik.android.enums.Languages
import app.n_zik.android.utils.isAtLeastAndroid12
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsEntrySearchIndexTest {
    @Test
    fun `repeated queries reuse normalized resources and keep stable rank ordering`() {
        val strings = mapOf(1 to "Other", 2 to "Préfix", 3 to "pré", 4 to "A pré", 5 to "pré second")
        val entries = listOf(
            SettingsEntryTarget("keyword", 0, 1, 1, listOf(3)),
            SettingsEntryTarget("prefix", 0, 1, 2),
            SettingsEntryTarget("exact", 0, 1, 3),
            SettingsEntryTarget("contains", 0, 1, 4),
            SettingsEntryTarget("prefix2", 0, 1, 5),
            SettingsEntryTarget("description", 0, 1, 1),
        )
        val resolved = mutableListOf<Int>()
        val index = SettingsEntrySearchIndex(entries, mapOf("description" to 3)) {
            resolved.add(it)
            strings.getValue(it)
        }
        val expected = listOf("exact", "prefix", "prefix2", "contains", "keyword", "description")
        repeat(10) {
            assertEquals(expected, index.search(" PRE ").map { it.id })
            assertEquals(emptyList<SettingsEntryTarget>(), index.search("missing"))
            assertEquals(emptyList<SettingsEntryTarget>(), index.search(" "))
        }
        assertEquals(strings.keys.sorted(), resolved.sorted())
    }

    @Test
    fun `choice fallback folds whitespace accents and order without joining incompatible choices`() {
        val target = SettingsEntryTarget("reverb", 0, 1, 1)
        val titleMatch = SettingsEntryTarget("title", 0, 1, 2)
        val strings = mapOf(1 to "Audio réverb", 2 to "Small room", 3 to "Small", 4 to "Large")
        val resolved = mutableListOf<Int>()
        var catalogCalls = 0
        val index = SettingsEntrySearchIndex(
            listOf(target, titleMatch, target),
            emptyMap(),
            choiceLabels = { resolve ->
                catalogCalls++
                mapOf("reverb" to listOf(resolve(3), resolve(4), "None", "Small room"))
            },
        ) {
            resolved.add(it)
            strings.getValue(it)
        }
        repeat(10) {
            assertEquals(listOf(target), index.search("  REVERB   NÓNE  "))
            assertEquals(listOf(target), index.search("none reverb"))
            assertEquals(listOf(titleMatch, target), index.search("small room"))
            assertEquals(emptyList<SettingsEntryTarget>(), index.search("small large"))
            assertEquals(emptyList<SettingsEntryTarget>(), index.search("reverb small large"))
        }
        assertEquals(1, catalogCalls)
        assertEquals(strings.keys.sorted(), resolved.sorted())
    }

    @Test
    fun `choice fallback never expands description or keyword token semantics`() {
        val target = SettingsEntryTarget("entry", 0, 1, 1, listOf(2))
        val index = SettingsEntrySearchIndex(
            listOf(target), mapOf("entry" to 3),
            choiceLabels = { mapOf("entry" to listOf("small")) },
        ) { mapOf(1 to "Audio reverb", 2 to "Depth", 3 to "Effect").getValue(it) }
        assertEquals(emptyList<SettingsEntryTarget>(), index.search("depth small"))
        assertEquals(emptyList<SettingsEntryTarget>(), index.search("effect reverb"))
        assertEquals(listOf(target), index.search("depth"))
        assertEquals(listOf(target), index.search("effect"))
    }

    @Test
    fun `cache reuses same resources and invalidates on configuration changes`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val english = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) },
        ).resources
        val first = settingsEntrySearchIndex(english)
        assertSame(first, settingsEntrySearchIndex(english))
        val original = Configuration(english.configuration)
        try {
            val frenchConfig = Configuration(original).apply { setLocale(Locale.FRENCH) }
            @Suppress("DEPRECATION")
            english.updateConfiguration(frenchConfig, english.displayMetrics)
            val second = settingsEntrySearchIndex(english)
            assertNotSame(first, second)
            assertSame(second, settingsEntrySearchIndex(english))
            val target = EmbeddedSettingsEntryTargets.single { it.id.endsWith("enable_log_debug") }
            assertEquals(target, second.search("DEBOGAGE").single { it.id == target.id })
        } finally {
            @Suppress("DEPRECATION")
            english.updateConfiguration(original, english.displayMetrics)
        }
    }

    @Test
    fun `category searches tolerate omitted title separators and exact entry searches accept their group`() {
        val english = localizedContext(Locale.ENGLISH)
        assertTrue(searchSettingsCategories(english, "AI Quick Picks").any { it.id == "ai" })
        assertTrue(searchSettingsCategories(english, "Maintenance Extensions").any { it.id == "maintenance" })
        val target = AllSettingsEntryTargets.single { it.id == "glass.refractionDepth" }
        val section = R.string.glass_effect_group_edges
        val label = english.getString(target.labelRes)
        val group = english.getString(section)
        assertTrue(target in searchSettingsEntries(english, "$group › $label"))
        assertTrue(target in searchSettingsEntries(english, "$label $group"))
    }

    @Test
    fun `a localized setting can be qualified with its fallback group without flooding card searches`() {
        val french = localizedContext(Locale.FRENCH)
        val group = french.getString(R.string.settings_group_track_transitions)
        val label = french.getString(R.string.settings_audio_crossfade_duration_label)
        val target = AllSettingsEntryTargets.single { it.id == "general.playback.crossfade_duration" }
        assertTrue(target in searchSettingsEntries(french, "$group › $label"))
        assertTrue(target in searchSettingsEntries(french, "$label $group"))
        assertFalse(target in searchSettingsEntries(french, group))
        val queueTitle = french.getString(R.string.settings_queue_management_title)
        assertFalse(searchSettingsEntries(french, queueTitle).any { it.cardTitleRes == R.string.settings_queue_management_title })
        val option = french.getString(R.string.storage_cleanup_option_cached_images)
        assertTrue(searchSettingsEntries(french, option).any { it.id == "data.cache.clear-images" })
    }

    @Test
    @Config(sdk = [30, 31])
    fun `automatic picture in picture only appears in search when its setting is supported`() {
        val english = localizedContext(Locale.ENGLISH)
        Shadows.shadowOf(english.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val cardTitle = R.string.settings_picture_in_picture_title
        listOf(R.string.settings_enable_pip_auto, R.string.settings_picture_in_picture_android_12_auto_description).forEach { res ->
            val query = english.getString(res)
            assertEquals(isAtLeastAndroid12, searchSettingsEntries(english, query).any {
                it.id == "general.picture_in_picture.settings_enable_pip_auto"
            })
            assertEquals(isAtLeastAndroid12, searchSettingsCardResults(english, query).any { it.titleRes == cardTitle })
        }
        assertTrue(searchSettingsEntries(english, english.getString(R.string.settings_enable_pip)).any {
            it.id == "general.picture_in_picture.settings_enable_pip"
        })
    }

    @Test
    @Config(sdk = [30])
    fun `Glass tint stays searchable while unavailable Material You is excluded`() {
        val english = localizedContext(Locale.ENGLISH)
        val target = AllSettingsEntryTargets.single { it.id == "glass.tintSource" }
        assertFalse(target in searchSettingsEntries(english, english.getString(R.string.theme_palette_material_you)))
        assertTrue(target in searchSettingsEntries(english, english.getString(target.labelRes)))
    }

    @Test
    fun `displayed categories cards and settings are searchable in every configured language`() {
        val packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager
        Shadows.shadowOf(packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val locales = Languages.entries.filterNot { it == Languages.System }.map { Locale.forLanguageTag(it.code) }
        val fallbackLocale = Locale.forLanguageTag("qaa")
        val source = localizedContext(Locale.ENGLISH)
        (locales + fallbackLocale).forEach { locale ->
            val context = localizedContext(locale)
            OrganizedSettingsCategories.forEach { category ->
                val label = context.getString(category.labelRes)
                if (locale == fallbackLocale) assertEquals(source.getString(category.labelRes), label)
                assertTrue("$locale: category ${category.id} ($label)", category in searchSettingsCategories(context, label))
            }
            OrganizedSettingsCards.forEach { card ->
                val label = card.displayTitle(context)
                assertTrue("$locale: card $label", card in searchSettingsCardResults(context, label))
            }
            listOf(SettingsUpdateItem, SettingsThemeStoreItem, SettingsGlassItem, SettingsThemeStoreCreatorItem).forEach { page ->
                val label = context.getString(page.labelRes)
                assertTrue("$locale: page $label", page in searchSettingsPageItems(context, label))
            }
            val inactiveSwipe = PlayerSongSwipeSetting.entries.single {
                it != PlayerSongSwipeSetting.forLayout(
                    context.preferences.getEnum(playerTypeKey, PlayerType.Essential),
                    context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                )
            }
            AllSettingsEntryTargets.forEach visibleTargets@{ target ->
                if (target.id == inactiveSwipe.target) return@visibleTargets
                val label = context.getString(target.labelRes)
                if (locale == fallbackLocale) assertEquals(source.getString(target.labelRes), label)
                assertTrue("$locale: entry ${target.id} ($label)", target in searchSettingsEntries(context, label))
            }
        }
    }

    @Test
    fun `visible dock and maintenance text finds the owning settings`() {
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val context = localizedContext(locale)
            val dock = context.getString(R.string.settings_dock_expanded_tab_order_description)
            assertTrue(searchSettingsEntries(context, dock).any { it.id == "ui.floating_dock.toolbar_order" })
            val subsystems = context.getString(R.string.maintenance_live_subsystems)
            assertTrue(searchSettingsCardResults(context, subsystems).any { it.titleRes == R.string.maintenance_title })
        }
    }

    @Test
    fun `Discord card and both button fields are searchable by their displayed titles`() {
        val groups = listOf(
            R.string.discord_advanced_group_identity, R.string.discord_advanced_group_lines,
            R.string.discord_advanced_group_images, R.string.discord_advanced_group_progress,
            R.string.discord_advanced_group_buttons, R.string.discord_advanced_group_connection,
        )
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val context = localizedContext(locale)
            val cardTitle = context.getString(R.string.settings_discord_title) + " " +
                context.getString(R.string.updates_build_beta_label)
            val card = searchSettingsCardResults(context, cardTitle).single { it.titleRes == R.string.settings_discord_title }
            assertEquals(cardTitle, card.displayTitle(context))
            assertTrue(card in searchSettingsCardResults(context, context.getString(R.string.settings_discord_title)))
            assertTrue(card in searchSettingsCardResults(context, context.getString(R.string.updates_build_beta_label)))
            groups.forEach { group ->
                assertTrue(card in searchSettingsCardResults(context, context.getString(group)))
            }
            val buttonLabels = listOf(R.string.discord_advanced_button1, R.string.discord_advanced_button2)
            buttonLabels.forEachIndexed { index, buttonRes ->
                val button = context.getString(buttonRes)
                val fields = mapOf("label" to R.string.discord_advanced_button_label, "url" to R.string.discord_advanced_button_url)
                fields.forEach { (field, fieldRes) ->
                    val label = context.getString(fieldRes)
                    val expectedId = "accounts.social_discord.discord_advanced_button${index + 1}.$field"
                    val target = searchSettingsEntries(context, "$button - $label").first()
                    assertEquals(expectedId, target.id)
                    assertEquals("$button - $label", target.searchResultTitle(context))
                    assertEquals(expectedId, searchSettingsEntries(context, "$button $label").first().id)
                }
            }
            val intervalDescription = context.getString(R.string.discord_advanced_refresh_interval_description)
            assertTrue(searchSettingsEntries(context, intervalDescription).any {
                it.id == "accounts.social_discord.discord_advanced_refresh_interval"
            })
        }
    }

    @Test
    fun `hidden Discord button fields highlight the nearest visible prerequisite`() {
        val prefix = "accounts.social_discord."
        val target = "${prefix}discord_advanced_button2.url"
        val options = listOf("${prefix}discord_advanced_button2", "${prefix}discord_advanced_mode", "${prefix}discord_enable_rich_presence")
        options.indices.forEach { index ->
            assertEquals(options[index], discordSettingsSearchFallback(target, options.drop(index)::contains))
        }
    }

    @Test
    fun `About search resolves counted contributor labels in the language of the card`() {
        val groups = listOf(
            Triple("about.contributors.translators", R.plurals.about_translator_count, 14),
            Triple("about.contributors.about_developers_designers", R.plurals.about_developer_designer_count, 30),
        )
        val locales = listOf(Locale.ENGLISH, Locale.FRENCH, Locale.forLanguageTag("ar"), Locale.forLanguageTag("pt"))
        locales.forEach { locale ->
            val context = localizedContext(locale)
            groups.forEach { (id, labelRes, count) ->
                val title = context.resources.getQuantityString(labelRes, count, count)
                assertEquals(id, searchSettingsEntries(context, title).first().id)
                assertFalse(searchSettingsEntries(context, count.toString()).any { it.id == id })
                assertFalse(searchSettingsEntries(context, "$count unrelated text").any { it.id == id })
            }
        }
    }

    @Test
    fun `Last fm logout keeps its own result title and hidden options resolve to their switches`() {
        val context = localizedContext(Locale.FRENCH)
        val label = context.getString(R.string.account_action_logout)
        val logout = searchSettingsEntries(context, label).single { it.cardTitleRes == R.string.settings_lastfm_title }
        assertEquals(LastFmSettingsLogoutTarget, logout.id)
        assertEquals(label, logout.searchResultTitle(context))
        val master = "embedded.lastfm.lastfm_enable_scrobbling"
        val scrobble = "embedded.lastfm.lastfm_scrobble"
        val threshold = "embedded.lastfm.lastfm_scrobble_threshold"
        assertEquals(scrobble, lastFmSettingsSearchFallback(threshold, setOf(master, scrobble)::contains))
        assertEquals(master, lastFmSettingsSearchFallback(threshold, setOf(master)::contains))
        assertEquals(master, lastFmSettingsSearchFallback("embedded.lastfm.lastfm_now_playing", setOf(master)::contains))
    }

    @Test
    fun `automatic backup search preserves screen order when relevance ties`() {
        val enabled = "embedded.auto_backup.enabled"
        val location = "embedded.auto_backup.auto_backup_location"
        val results = searchSettingsEntries(localizedContext(Locale.ENGLISH), "backup")
            .map { it.id }
            .filter { it == enabled || it == location }
        assertEquals(listOf(enabled, location), results)
    }

    @Test
    fun `automatic backup finds GitHub inclusion by the visible localized label`() {
        val id = "embedded.auto_backup.include_github_credentials"
        listOf(Locale.ENGLISH, Locale.FRENCH).forEach { locale ->
            val context = localizedContext(locale)
            val query = context.getString(R.string.backup_include_github_identity_credentials)
            val target = searchSettingsEntries(context, query).single { it.id == id }
            assertEquals("data", target.parentCard().categoryId())
            assertTrue(target.destinationRoute(context).startsWith("settingsCategory/data?target=$id&"))
        }
    }

    private fun localizedContext(locale: Locale): Context {
        val context: Context = ApplicationProvider.getApplicationContext()
        return context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) },
        )
    }
}
