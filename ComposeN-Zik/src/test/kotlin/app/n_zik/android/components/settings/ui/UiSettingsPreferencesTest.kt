package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.UiType
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.thumbnailSizeDpKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Golden expectations transcribed from UiSettings at baseline 89c9a371. */
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
        "keepPlayerMinimized" to true,
        "disableIconButtonOnTop" to false,
        "lastPlayerTimelineType" to "Default",
        "lastThumbnailSizeDp" to 30f,
        "UiType" to "RiMusic",
        "disablePlayerHorizontalSwipe" to false,
        "lastPlayerPlayButtonType" to "Rectangular",
        "colorPaletteName" to "Dynamic",
        "colorPaletteMode" to "Dark",
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
        "messageType" to "Modern",
        "showTopActionsBar" to true,
        "playerControlsType" to "Modern",
        "playerInfoType" to "Modern",
        "playerType" to "Essential",
        "queueType" to "Modern",
        "fadingedge" to false,
        "carousel" to true,
        "carouselSize" to "Biggest",
        "thumbnailType" to "Modern",
        "playerTimelineType" to "Wavy",
        "thumbnailSizeDpKey" to 0f,
        "playerTimelineSize" to "Biggest",
        "playerInfoShowIcons" to true,
        "miniPlayerType" to "Essential",
        "playerSwapControlsWithTimeline" to false,
        "playerPlayButtonType" to "CircularRibbed",
        "buttonzoomout" to true,
        "iconLikeType" to "Essential",
        "playerBackgroundColors" to "AnimatedGradient",
        "blackgradient" to false,
        "showTotalTimeQueue" to true,
        "showNextSongsInPlayer" to false,
        "showRemainingSongTime" to true,
        "disableScrollingText" to false,
        "effectRotation" to false,
        "thumbnailTapEnabled" to true,
        "clickOnLyricsText" to true,
        "backgroundProgress" to "MiniPlayer",
        "transparentBackgroundPlayerActionBar" to true,
        "actionspacedevenly" to true,
        "tapqueue" to true,
        "swipeUpQueue" to true,
        "showButtonPlayerAddToPlaylist" to true,
        "showButtonPlayerArrow" to true,
        "showButtonPlayerDownload" to true,
        "showButtonPlayerLoop" to false,
        "showButtonPlayerLyrics" to true,
        "expandedplayertoggle" to false,
        "showButtonPlayerShuffle" to true,
        "showButtonPlayerSleepTimer" to false,
        "showButtonPlayerMenu" to false,
        "showButtonPlayerSystemEqualizer" to false,
        "queueSwipeLeftAction" to "RemoveFromQueue",
        "queueSwipeRightAction" to "PlayNext",
        "playlistSwipeLeftAction" to "Favourite",
        "playlistSwipeRightAction" to "PlayNext",
        "albumSwipeLeftAction" to "PlayNext",
        "albumSwipeRightAction" to "Bookmark",
        "showButtonPlayerDiscover" to false,
        "playerEnableLyricsPopupMessage" to true,
        "visualizerEnabled" to false,
        "showthumbnail" to true,
        "searchDisplayOrder" to "SuggestionsFirst",
        "customColor" to Color.Green.hashCode(),
        "disableNavigationBackStack" to false,
        "showCachedPlaylist" to true,
        "rememberQueueScrollPosition" to true,
    )

    private val viMusic: Map<String, Any> = mapOf(
        "UiType" to "ViMusic",
        "disablePlayerHorizontalSwipe" to true,
        "disableIconButtonOnTop" to true,
        "playerTimelineType" to "AudioWaves",
        "visualizerEnabled" to false,
        "thumbnailSizeDpKey" to 30f,
        "thumbnailTapEnabled" to true,
        "showSearchTab" to true,
        "showStatsInNavbar" to true,
        "navigationBarPosition" to "Left",
        "showTopActionsBar" to false,
        "playerType" to "Modern",
        "queueType" to "Modern",
        "fadingedge" to false,
        "carousel" to true,
        "carouselSize" to "Medium",
        "thumbnailType" to "Essential",
        "playerTimelineSize" to "Medium",
        "playerInfoShowIcons" to true,
        "miniPlayerType" to "Modern",
        "playerSwapControlsWithTimeline" to false,
        "transparentBackgroundPlayerActionBar" to true,
        "playerControlsType" to "Essential",
        "playerPlayButtonType" to "Disabled",
        "buttonzoomout" to true,
        "iconLikeType" to "Essential",
        "playerBackgroundColors" to "CoverColorGradient",
        "blackgradient" to true,
        "showTotalTimeQueue" to false,
        "showRemainingSongTime" to false,
        "showNextSongsInPlayer" to false,
        "disableScrollingText" to false,
        "effectRotation" to true,
        "clickOnLyricsText" to true,
        "playerEnableLyricsPopupMessage" to true,
        "backgroundProgress" to "MiniPlayer",
        "actionspacedevenly" to false,
        "tapqueue" to false,
        "swipeUpQueue" to true,
        "showButtonPlayerDiscover" to false,
        "showButtonPlayerDownload" to false,
        "showButtonPlayerAddToPlaylist" to false,
        "showButtonPlayerLoop" to false,
        "showButtonPlayerShuffle" to false,
        "showButtonPlayerLyrics" to false,
        "expandedplayertoggle" to false,
        "showButtonPlayerSleepTimer" to false,
        "showButtonPlayerSystemEqualizer" to false,
        "showButtonPlayerArrow" to true,
        "showButtonPlayerMenu" to true,
        "showthumbnail" to true,
        "keepPlayerMinimized" to false,
    )

    @Test fun `full reset restores baseline profile values and store boundaries`() {
        seedOpposite(context.preferences, fullReset)
        context.preferences.edit().putString("unrelated", "keep").putString("playerSwipeSensitivity", "High").commit()
        store("preferences_untouched").edit().putString("UiType", "ViMusic").commit()
        store("preferences").edit().putString("unrelated", "global").putString("historySortMenuOrder", "history-sentinel")
            .putBoolean("hist_sort_DATE_visible", false).putBoolean("hometab_songs_enabled", false).commit()
        store("app_settings").edit().putBoolean("burger_menu_toggle_history", false).putString("unrelated", "app").commit()

        applyUiDefaults(context)

        assertEquals(fullReset + mapOf("unrelated" to "keep", "playerSwipeSensitivity" to "High"), context.preferences.all)
        assertEquals(mapOf("UiType" to "ViMusic"), store("preferences_untouched").all)
        assertEquals("global", store("preferences").getString("unrelated", null))
        assertEquals("history-sentinel", store("preferences").getString("historySortMenuOrder", null))
        assertFalse(store("preferences").getBoolean("hist_sort_DATE_visible", true))
        assertTrue(store("preferences").getBoolean("hometab_songs_enabled", false))
        assertEquals("[\"quickpicks\",\"songs\",\"artists\",\"albums\",\"playlists\"]", store("preferences").getString("homeTabsOrder", null))
        assertEquals("app", store("app_settings").getString("unrelated", null))
        assertTrue(store("app_settings").getBoolean("burger_menu_toggle_history", false))
        assertEquals("[\"history\",\"statistics\",\"rewind\",\"offline_mode\",\"listen_together\",\"bridge_server\",\"profiles\",\"pip\",\"separator\",\"settings\",\"extensions\",\"debug_logs\",\"maintenance\",\"rescue_center\"]", store("app_settings").getString("burger_menu_order", null))
        assertFalse(context.preferences.contains("homeTabsOrder"))
        assertFalse(context.preferences.contains("burger_menu_order"))
    }

    @Test fun `ViMusic applies exactly its baseline rewrite set in active profile`() {
        seedOpposite(context.preferences, viMusic)
        context.preferences.edit().putString("unrelated", "keep").commit()
        applyUiType(context, UiType.ViMusic)
        assertEquals(viMusic + mapOf("unrelated" to "keep"), context.preferences.all)
        assertTrue(store("preferences").all.isEmpty())
        assertTrue(store("app_settings").all.isEmpty())
    }

    @Test fun `RiMusic restores only six values and preserves ViMusic side effects`() {
        applyUiType(context, UiType.ViMusic)
        val remembered = mapOf("lastPlayerTimelineType" to "Wavy", "lastThumbnailSizeDp" to 42f, "lastPlayerPlayButtonType" to "CircularRibbed")
        context.preferences.edit().putString("lastPlayerTimelineType", "Wavy").putFloat("lastThumbnailSizeDp", 42f)
            .putString("lastPlayerPlayButtonType", "CircularRibbed").commit()
        applyUiType(context, UiType.RiMusic)
        val riMusic = mapOf("UiType" to "RiMusic", "disablePlayerHorizontalSwipe" to false, "disableIconButtonOnTop" to false,
            "playerTimelineType" to "Wavy", thumbnailSizeDpKey to 42f, "playerPlayButtonType" to "CircularRibbed", "navigationBarPosition" to "Bottom")
        assertEquals(viMusic + remembered + riMusic, context.preferences.all)
        assertTrue(store("preferences").all.isEmpty())
        assertTrue(store("app_settings").all.isEmpty())
    }

    @Test fun `RiMusic uses baseline remembered-value defaults when absent`() {
        applyUiType(context, UiType.RiMusic)
        assertEquals(mapOf("UiType" to "RiMusic", "disablePlayerHorizontalSwipe" to false, "disableIconButtonOnTop" to false,
            "playerTimelineType" to "Default", thumbnailSizeDpKey to 30f, "playerPlayButtonType" to "Rectangular", "navigationBarPosition" to "Bottom"), context.preferences.all)
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

    @Test fun `target inventory covers ten cards and stable unique setting and action identities`() {
        assertEquals(listOf("theme", "interface", "font", "swipe", "home-tabs", "sort-menus", "floating-dock", "toolbars", "burger", "reset"), UiSettingTargets.cards.map { it.id.removePrefix("ui.card.") })
        assertEquals(listOf(24, 13, 4, 7, 0, 0, 3, 0, 0, 0), UiSettingTargets.cards.map { it.preferenceKeys.size })
        assertEquals(listOf(2, 2, 0, 0, 5, 6, 1, 5, 1, 1), UiSettingTargets.cards.map { it.actions.size })
        assertEquals(UiSettingTargets.ids.size, UiSettingTargets.ids.toSet().size)
        assertTrue(UiSettingTargets.ids.none { it.contains(' ') })
        assertEquals(UiSettingTargets.ids.toSet(), UiSettingTargets.cards.reversed().flatMap { card ->
            listOf(card.id) + card.preferenceKeys.map { "${card.id}.setting.$it" } + card.actions.map { "${card.id}.action.$it" }
        }.toSet())
    }
}
