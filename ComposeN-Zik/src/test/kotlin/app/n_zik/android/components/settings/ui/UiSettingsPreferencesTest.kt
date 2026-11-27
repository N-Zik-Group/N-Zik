package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.DefaultLightColorPalette
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.settings.appearance.applyAppearanceDefaults
import app.n_zik.android.components.settings.common.SettingsEntryTargets
import app.n_zik.android.components.theme.dynamicThemeRespectBrightnessKey
import app.n_zik.android.enums.MusicAnimationType
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.playlistindicatorKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reset expectations include audited defaults and supported presentation preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UiSettingsPreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun store(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Before fun setUp() {
        listOf("preferences", "preferences_ui-test", "preferences_untouched", "app_settings").forEach {
            store(it).edit().clear().commit()
        }
        setActiveProfile("ui-test", context)
    }

    @After fun tearDown() { setActiveProfile("default", context) }

    private fun seedOpposite(preferences: SharedPreferences, values: Map<String, Any>) {
        val editor = preferences.edit()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, !value)
                is Float -> editor.putFloat(key, value + 7f)
                is Int -> editor.putInt(key, value xor 0x1234)
                is String -> editor.putString(key, "seeded-$value")
                else -> error("Unsupported fixture type for $key")
            }
        }
        editor.commit()
    }

    private val fullReset: Map<String, Any> = mapOf(
        "hideStatusBarKey" to false,
        "uiRoundnessDpKey" to 25f,
        "artistThumbnailRoundnessDpKey" to 48f,
        "disableIconButtonOnTop" to false,
        "colorPaletteName" to "Dynamic",
        "colorPaletteMode" to "System",
        dynamicThemeRespectBrightnessKey to false,
        "indexNavigationTab" to "Default",
        "fontType" to "Rubik",
        "useSystemFont" to false,
        "applyFontPadding" to false,
        "isSwipeToActionEnabled" to true,
        "showSearchTab" to false,
        "showStatsInNavbar" to false,
        "navigationBarPosition" to "BottomFloating",
        "navigationBarType" to "IconOnly",
        "showFavoritesPlaylist" to true,
        "showDownloadedPlaylist" to true,
        "showOnDevicePlaylist" to true,
        "showFloatingIcon" to false,
        playlistindicatorKey to false,
        nowPlayingIndicatorKey to MusicAnimationType.Bubbles.name,
        "menuStyle" to "List",
        "transitionEffect" to "Fade",
        "showPinnedPlaylists" to true,
        "customThemeLight_Background0" to DefaultLightColorPalette.background0.hashCode(),
        "customThemeLight_Background1" to DefaultLightColorPalette.background1.hashCode(),
        "customThemeLight_Background2" to DefaultLightColorPalette.background2.hashCode(),
        "customThemeLight_Background3" to DefaultLightColorPalette.background3.hashCode(),
        "customThemeLight_Background4" to DefaultLightColorPalette.background4.hashCode(),
        "customThemeLight_Text" to DefaultLightColorPalette.text.hashCode(),
        "customThemeLight_textSecondary" to DefaultLightColorPalette.textSecondary.hashCode(),
        "customThemeLight_textDisabled" to DefaultLightColorPalette.textDisabled.hashCode(),
        "customThemeLight_iconButtonPlayer" to DefaultLightColorPalette.iconButtonPlayer.hashCode(),
        "customThemeLight_accent" to DefaultLightColorPalette.accent.hashCode(),
        "customThemeDark_Background0" to DefaultDarkColorPalette.background0.hashCode(),
        "customThemeDark_Background1" to DefaultDarkColorPalette.background1.hashCode(),
        "customThemeDark_Background2" to DefaultDarkColorPalette.background2.hashCode(),
        "customThemeDark_Background3" to DefaultDarkColorPalette.background3.hashCode(),
        "customThemeDark_Background4" to DefaultDarkColorPalette.background4.hashCode(),
        "customThemeDark_Text" to DefaultDarkColorPalette.text.hashCode(),
        "customThemeDark_textSecondary" to DefaultDarkColorPalette.textSecondary.hashCode(),
        "customThemeDark_textDisabled" to DefaultDarkColorPalette.textDisabled.hashCode(),
        "customThemeDark_iconButtonPlayer" to DefaultDarkColorPalette.iconButtonPlayer.hashCode(),
        "customThemeDark_accent" to DefaultDarkColorPalette.accent.hashCode(),
        "playerPosition" to "Bottom",
        "queueSwipeLeftAction" to "RemoveFromQueue",
        "queueSwipeRightAction" to "PlayNext",
        "playlistSwipeLeftAction" to "Favourite",
        "playlistSwipeRightAction" to "PlayNext",
        "albumSwipeLeftAction" to "PlayNext",
        "albumSwipeRightAction" to "Bookmark",
        "searchDisplayOrder" to "SuggestionsFirst",
        "customColor" to Color.Green.hashCode(),
        "disableNavigationBackStack" to false,
        "showCachedPlaylist" to true,
        "showMonthlyPlaylists" to true,
        "showOnDevicePlaylists" to true,
        "homeArtistsOrder" to """["all","favorites","disliked","on_device"]""",
        "homeAlbumsOrder" to """["all","favorites","disliked","on_device"]""",
        "homePlaylistsOrder" to """["all","pinned_playlists","rewind","yt_playlists","on_device"]""",
        "excludeDislikedAlbums" to "Enabled",
        "excludeDislikedArtists" to "Enabled",
        "showOnDeviceAlbums" to true,
        "showFavoritesAlbum" to true,
        "showFavoritesArtist" to true,
        "showYtPlaylists" to true,
        "showOnDeviceArtists" to true,
        "rightToLeftInterface" to false,
    )

    @Test fun `interface reset restores baseline profile values and store boundaries`() {
        seedOpposite(context.preferences, fullReset)
        context.preferences.edit().putString("unrelated", "keep").putString("playerSwipeSensitivity", "High").commit()
        context.preferences.edit().putBoolean("showButtonPlayerDiscover", true)
            .putBoolean("rememberQueueScrollPosition", false).commit()
        context.preferences.edit().putString("historySortMenuOrder", "profile-history-sentinel")
            .putBoolean("hist_sort_DATE_visible", false).putBoolean("hometab_songs_enabled", false)
            .putString("homeTabsOrder", "profile-tabs-sentinel").commit()
        store("preferences_untouched").edit().putString("UiType", "ViMusic").commit()
        store("preferences").edit().putString("unrelated", "global").putString("historySortMenuOrder", "history-sentinel")
            .putBoolean("hist_sort_DATE_visible", false).putBoolean("hometab_songs_enabled", false).commit()
        store("app_settings").edit().putBoolean("burger_menu_toggle_history", false).putString("unrelated", "app").commit()

        applyUiDefaults(context)

        // Reader defaults and toolbar dialogs also reset their own preferences, covered by their contract tests.
        val expected = fullReset + mapOf("unrelated" to "keep", "playerSwipeSensitivity" to "High")
        assertEquals(expected, context.preferences.all.filterKeys(expected::containsKey))
        assertTrue(context.preferences.getBoolean("showButtonPlayerDiscover", false))
        assertFalse(context.preferences.getBoolean("rememberQueueScrollPosition", true))
        assertEquals(mapOf("UiType" to "ViMusic"), store("preferences_untouched").all)
        assertEquals(mapOf("unrelated" to "global", "historySortMenuOrder" to "history-sentinel",
            "hist_sort_DATE_visible" to false, "hometab_songs_enabled" to false), store("preferences").all)
        assertEquals("[\"DATE\",\"ALPHABETICAL\",\"ARTIST\"]", context.preferences.getString("historySortMenuOrder", null))
        listOf("DATE", "ALPHABETICAL", "ARTIST").forEach { id ->
            assertTrue(context.preferences.getBoolean("hist_sort_${id}_visible", false))
        }
        assertTrue(context.preferences.getBoolean("hometab_songs_enabled", false))
        assertEquals("[\"quickpicks\",\"songs\",\"artists\",\"albums\",\"playlists\"]", context.preferences.getString("homeTabsOrder", null))
        assertEquals("app", store("app_settings").getString("unrelated", null))
        assertTrue(store("app_settings").getBoolean("burger_menu_toggle_history", false))
        assertEquals("[\"history\",\"statistics\",\"rewind\",\"offline_mode\",\"listen_together\",\"bridge_server\",\"profiles\",\"pip\",\"separator\",\"settings\",\"extensions\",\"debug_logs\",\"maintenance\",\"rescue_center\",\"search\",\"notifications\"]", store("app_settings").getString("burger_menu_order", null))
        assertFalse(store("app_settings").getBoolean("burger_menu_toggle_search", true))
        assertFalse(store("app_settings").getBoolean("burger_menu_toggle_notifications", true))
        assertFalse(store("preferences").contains("homeTabsOrder"))
        assertFalse(context.preferences.contains("burger_menu_order"))
    }

    @Test fun `player and interface resets preserve each other's customizations`() {
        val preferences = context.preferences
        preferences.edit()
            .putString("colorPaletteName", "Customized")
            .putString("colorPaletteMode", "Light")
            .putFloat("uiRoundnessDpKey", 11f)
            .putFloat("artistThumbnailRoundnessDpKey", 20f)
            .putString("playerType", "Modern")
            .putString("queueType", "Essential")
            .putFloat("thumbnailSizeLDpKey", 45f)
            .commit()

        applyAppearanceDefaults(context)

        assertEquals("Customized", preferences.getString("colorPaletteName", null))
        assertEquals("Light", preferences.getString("colorPaletteMode", null))
        assertEquals(11f, preferences.getFloat("uiRoundnessDpKey", -1f), 0f)
        assertEquals(20f, preferences.getFloat("artistThumbnailRoundnessDpKey", -1f), 0f)
        assertEquals("Essential", preferences.getString("playerType", null))
        assertEquals("Modern", preferences.getString("queueType", null))
        assertFalse(preferences.contains("thumbnailSizeLDpKey"))

        preferences.edit()
            .putString("playerType", "Modern")
            .putString("queueType", "Essential")
            .putFloat("thumbnailSizeLDpKey", 45f)
            .commit()

        applyUiDefaults(context)

        assertEquals("Dynamic", preferences.getString("colorPaletteName", null))
        assertEquals("System", preferences.getString("colorPaletteMode", null))
        assertEquals(25f, preferences.getFloat("uiRoundnessDpKey", -1f), 0f)
        assertEquals(48f, preferences.getFloat("artistThumbnailRoundnessDpKey", -1f), 0f)
        assertEquals("Modern", preferences.getString("playerType", null))
        assertEquals("Essential", preferences.getString("queueType", null))
        assertEquals(45f, preferences.getFloat("thumbnailSizeLDpKey", -1f), 0f)
    }

    @Test fun `reset does not recreate retired selectors or change Glass selection`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()

        applyUiDefaults(context)

        assertFalse(context.preferences.contains("UiType"))
        assertFalse(context.preferences.contains("messageType"))
        assertTrue(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun `palette reset changes exactly ten colors in selected palette and active profile`() {
        for (dark in listOf(false, true)) {
            context.preferences.edit().clear().commit()
            val prefix = if (dark) "customThemeDark" else "customThemeLight"
            val palette = fullReset.filterKeys { it.startsWith(prefix) }
            val allColors = fullReset.filterKeys { it.startsWith("customTheme") }
            seedOpposite(context.preferences, allColors)
            context.preferences.edit().putString("unrelated", "keep").commit()
            val before = context.preferences.all.toMap()
            resetUiPalette(context, dark)
            assertEquals(10, palette.size)
            assertEquals(before + palette, context.preferences.all)
            assertTrue(store("preferences").all.isEmpty())
            assertTrue(store("app_settings").all.isEmpty())
        }
    }

    @Test fun `global search has no retired presentation destinations`() {
        val retiredIds = setOf(
            "ui.theme.interface_in_use",
            "ui.user_interface.message_type",
            "ui.user_interface.vimusic_show_search_button_in_navigation_bar",
            "ui.user_interface.show_statistics_in_navigation_bar",
        )
        assertTrue(SettingsEntryTargets.none { it.id in retiredIds })
    }

    @Test fun `target inventory covers ten cards and stable unique setting and action identities`() {
        assertEquals(listOf("interface", "floating-dock", "theme", "font", "swipe", "home-tabs", "burger", "toolbars", "sort-menus", "reset"), UiSettingTargets.cards.map { it.id.removePrefix("ui.card.") })
        assertEquals(listOf(15, 1, 24, 3, 7, 0, 0, 0, 0, 0), UiSettingTargets.cards.map { it.preferenceKeys.size })
        assertEquals(listOf(1, 1, 2, 0, 0, 5, 1, 10, 6, 1), UiSettingTargets.cards.map { it.actions.size })
        assertEquals(UiSettingTargets.ids.size, UiSettingTargets.ids.toSet().size)
        assertTrue(UiSettingTargets.ids.none { it.contains(' ') })
        assertEquals(UiSettingTargets.ids.toSet(), UiSettingTargets.cards.reversed().flatMap { card ->
            listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } + card.actions.map { "${card.id}.action.$it" }
        }.toSet())
    }
}
