package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.actionExpandedKey
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.useSystemFontKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.glassProfileDefaults
import app.n_zik.android.components.glass.glassSettingsFromPreferences
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.customThemeDark_Background0Key
import app.n_zik.android.utils.lyricsFontSizeKey
import app.n_zik.android.utils.lyricsSizeKey
import app.n_zik.android.utils.lyricsTypeKey
import app.n_zik.android.utils.miniPlayerButtonOrderKey
import app.n_zik.android.enums.PlayerOpeningMode
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.holdToSpeedUpRateKey
import app.n_zik.android.utils.playerOpeningModeKey
import app.n_zik.android.utils.playerLastModeWasVideoKey
import app.n_zik.android.utils.nowPlayingIndicatorKey
import app.n_zik.android.utils.playlistindicatorKey
import app.n_zik.android.utils.Preference
import app.n_zik.android.utils.playerBackdropKey
import app.n_zik.android.utils.floatActionIconOffsetXkey
import app.n_zik.android.utils.showMiniPlayerAudioOutputKey
import app.n_zik.android.utils.uiRoundnessDpKey
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.homeSongsOrderKey
import app.n_zik.android.utils.playerActionBarButtonOrderKey
import app.n_zik.android.themed.n_zik.screen.player.resolvePlayerActionBarOrder
import app.n_zik.android.utils.thumbnailSizeDpKey
import app.n_zik.android.utils.thumbnailSizeLDpKey
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.glass.glassBlurKey
import app.n_zik.android.components.glass.glassJellyEnabledKey
import app.n_zik.android.utils.navigationBarPositionKey

class ThemePreviewFormatTest {
    @Test
    fun `pure black mode round trips locally in V2 with independent palette and Glass overrides`() {
        val plain = mapOf(colorPaletteNameKey to "Dynamic", colorPaletteModeKey to "PureBlack",
            glassEnabledKey to true, customThemeDark_Background0Key to Color.Red.hashCode())
        val optics = mapOf(GlassArea.Nav.preferenceKey to "GlassOnly", GlassArea.MiniPlayer.preferenceKey to "Disabled")
        val encoded = encodeThemeAppearance(plain, optics, schemaVersion = ThemeAppearanceV2)
        val parsed = parseThemeAppearance(encoded)
        assertEquals(parsed, parseThemeAppearance(encoded, false, advertisedMinVersionCode = 99,
            validateFeatureCompatibility = false))
        val colors = parsed.getValue(ThemeCategory.COLORS).plain
        assertEquals("Dynamic", colors[colorPaletteNameKey])
        assertEquals("PureBlack", colors[colorPaletteModeKey])
        assertEquals(ThemePureBlackMinimumVersionCode, themeAppearanceFeatureMinimumVersionCode(parsed))
        val combined = parsed.values.flatMap { it.plain.entries }.associate { it.toPair() }
        val glass = parsed.getValue(ThemeCategory.GLASS).glass
        assertEquals("GlassOnly", glass[GlassArea.Nav.preferenceKey])
        assertEquals("Disabled", glass[GlassArea.MiniPlayer.preferenceKey])
        assertEquals(encoded, encodeThemeAppearance(combined, glass, schemaVersion = ThemeAppearanceV2))
        assertThrows(IllegalArgumentException::class.java) {
            parseThemeAppearance(encoded, false, advertisedMinVersionCode = 100)
        }
        assertEquals(parsed, parseThemeAppearance(encoded, false, advertisedMinVersionCode = 101))
        assertThrows(IllegalArgumentException::class.java) { encodeThemeAppearance(plain, optics, schemaVersion = 1) }
        val legacyModeError = assertThrows(IllegalArgumentException::class.java) {
            parseThemeAppearance("""{"schemaVersion":1,"categories":{"colors":{"palette":"dynamic","mode":"pure-black"}}}""")
        }
        assertEquals("Pure Black mode requires theme appearance V2", legacyModeError.message)
    }

    @Test
    fun `pure black feature floor does not reinterpret the named palette or schema floor`() {
        val namedPalette = """{"schemaVersion":2,"categories":{"colors":{"palette":"pure-black","mode":"dark"}}}"""
        assertEquals(99, themeAppearancePublicationMinimumVersionCode(namedPalette))
        assertEquals(0, themeAppearanceFeatureMinimumVersionCode(parseThemeAppearance(namedPalette)))
        parseThemeAppearance(namedPalette, false, advertisedMinVersionCode = 99)
        assertThrows(IllegalArgumentException::class.java) {
            parseThemeAppearance(namedPalette.replace("\"dark\"", "\"unknown-mode\""))
        }
        assertEquals(null, submissionMinimumVersionCode("101", 100, null, requiredMinimum = 101))
        assertEquals(null, submissionMinimumVersionCode("101", 100, 101, requiredMinimum = 101))
        assertEquals(null, submissionMinimumVersionCode("100", 101, null, requiredMinimum = 101))
        assertEquals(101, submissionMinimumVersionCode("101", 101, null, requiredMinimum = 101))
    }

    @Test
    fun `system font preference delegates font choice to Android`() {
        val document = encodeThemeAppearance(
            mapOf(useSystemFontKey to true, fontTypeKey to FontType.Poppins.name),
            emptyMap<String, Any>(),
        )
        for (glass in listOf(false, true)) {
            val preferences = themePreviewPreferences(document, glass).plain
            val systemFont = preferences.getValue(useSystemFontKey) as Boolean
            val selectedFont = FontType.valueOf(preferences.getValue(fontTypeKey) as String)
            assertTrue(systemFont)
            assertEquals(FontType.Poppins, selectedFont)
            assertEquals(FontFamily.Default, typographyOf(Color.White, systemFont, false, selectedFont).m.fontFamily)
        }
    }

    @Test
    fun `v1 appearance maps to isolated preview values`() {
        val theme = parseThemeAppearance("""
            {"schemaVersion":1,"categories":{
              "colors":{"palette":"dynamic","mode":"system"},
              "navigation":{"position":"bottom-floating","labels":"icon-only"},
              "glass":{"enabled":true,"tintSource":"theme-accent","navigation":"glass-only",
                "miniPlayer":"disabled","nowPlaying":"glass-and-contour"}
            }}
        """.trimIndent())
        assertEquals("Dynamic", theme.getValue(ThemeCategory.COLORS).plain[colorPaletteNameKey])
        assertEquals("System", theme.getValue(ThemeCategory.COLORS).plain[colorPaletteModeKey])
        assertEquals("BottomFloating", theme.getValue(ThemeCategory.NAVIGATION).plain[navigationBarPositionKey])
        val glass = theme.getValue(ThemeCategory.GLASS)
        assertEquals("GlassOnly", glass.glass[GlassArea.Nav.preferenceKey])
        assertFalse(glass.plain.containsKey(GlassArea.Nav.preferenceKey))
    }

    @Test
    fun `v1 appearance rejects non appearance fields`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseThemeAppearance("""{"schemaVersion":1,"categories":{"colors":{
                "palette":"dynamic","mode":"system","activeProfile":"other"
            }}}""")
        }
    }

    @Test
    fun `preview variants preserve every decoded setting except the Glass master switch`() {
        val document = encodeThemeAppearance(
            mapOf(hideStatusBarKey to true, glassEnabledKey to false),
            mapOf(GlassArea.Nav.preferenceKey to "GlassOnly", GlassArea.MiniPlayer.preferenceKey to "Disabled"),
            schemaVersion = ThemeAppearanceV2,
        )
        val theme = parseThemeAppearance(document)
        val plain = theme.values.flatMap { it.plain.entries }.associate { it.toPair() }
        val optics = theme.getValue(ThemeCategory.GLASS).glass

        val normal = themePreviewPreferences(document, glass = false)
        val glass = themePreviewPreferences(document, glass = true)

        assertEquals(true, normal.plain[hideStatusBarKey])
        assertEquals(plain + (glassEnabledKey to false), normal.plain)
        assertEquals(plain + (glassEnabledKey to true), glass.plain)
        assertEquals(optics, normal.glass)
        assertEquals(optics, glass.glass)
    }

    @Test
    fun `empty preferences export creates an importable default theme`() {
        val document = encodeThemeAppearance(emptyMap<String, Any>(), emptyMap<String, Any>())
        val groups = parseThemeAppearance(document).toStoreGroups()
        assertEquals(StoreGroup.entries.toSet(), groups.keys)
        assertEquals("System", groups.getValue(StoreGroup.Colors).plain[colorPaletteModeKey])
        assertEquals(25f, groups.getValue(StoreGroup.PageLayout).plain[uiRoundnessDpKey])
        assertEquals("Auto", groups.getValue(StoreGroup.Lyrics).plain[lyricsTypeKey])
        assertEquals(true, groups.getValue(StoreGroup.PlayerAndQueue).plain[showMiniPlayerAudioOutputKey])
    }

    @Test
    fun `customization round trips through the same document used for previews`() {
        val plain = mapOf(
            colorPaletteNameKey to "Customized",
            customThemeDark_Background0Key to -101,
            uiRoundnessDpKey to 26f,
            nowPlayingIndicatorKey to "CrazyBars",
            playlistindicatorKey to true,
            playerBackdropKey to 32f,
            floatActionIconOffsetXkey to -63.25f,
            Preference.HOME_ARTIST_ITEM_SIZE.key to "BIG",
            Preference.SEARCH_RESULT_GRID_STATES.key to "1010101",
            lyricsFontSizeKey to "Medium",
            lyricsSizeKey to 42f,
            lyricsTypeKey to "Karaoke",
            actionExpandedKey to false,
            showNextSongsInPlayerKey to true,
            miniPlayerButtonOrderKey to """["lyrics","play_pause"]""",
            showMiniPlayerAudioOutputKey to false,
            "githubAccessToken" to "not-a-theme-setting",
            "prevNextSongs" to "onesong",
        )
        val glass = mapOf(
            glassBlurKey to 11f,
            glassJellyEnabledKey to false,
            GlassArea.Header.preferenceKey to "ContourOnly",
        )
        val document = encodeThemeAppearance(plain, glass)
        val preview = themePreviewPreferences(document, glass = true)
        assertEquals(plain - setOf("githubAccessToken", "prevNextSongs"), preview.plain.filterKeys { it in plain })
        assertEquals(glass, preview.glass.filterKeys { it in glass })
        assertFalse(document.contains("not-a-theme-setting"))
        assertEquals(plain.filterKeys { it in setOf(lyricsFontSizeKey, lyricsSizeKey, lyricsTypeKey, actionExpandedKey) },
            parseThemeAppearance(document).toStoreGroups().getValue(StoreGroup.Lyrics).plain.filterKeys { it in plain })
    }

    @Test
    fun `V2 round trips opening mode and boost without carrying the last used mode`() {
        val playerPreferences = mapOf(
            playerOpeningModeKey to PlayerOpeningMode.RememberLast.name,
            holdToSpeedUpKey to true,
            holdToSpeedUpRateKey to 2.7f,
        )
        val legacy = encodeThemeAppearance(playerPreferences, emptyMap<String, Any>(), schemaVersion = 1)
        val latest = encodeThemeAppearance(
            playerPreferences + (playerLastModeWasVideoKey to true),
            emptyMap<String, Any>(),
            schemaVersion = ThemeAppearanceV2,
        )
        val latestPlayer = parseThemeAppearance(latest).getValue(ThemeCategory.PLAYER_AND_QUEUE).plain
        assertEquals(playerPreferences, latestPlayer.filterKeys { it in playerPreferences })
        assertFalse(playerLastModeWasVideoKey in latestPlayer)
        assertFalse(playerOpeningModeKey in parseThemeAppearance(legacy).getValue(ThemeCategory.PLAYER_AND_QUEUE).plain)
        assertEquals(1, themeAppearanceSchemaFor(98))
        assertEquals(ThemeAppearanceV2, themeAppearanceSchemaFor(99))
    }

    @Test
    fun `older themes can keep their button orders after the video action is removed`() {
        val current = encodeThemeAppearance(
            mapOf(
                miniPlayerButtonOrderKey to """["play_pause"]""",
                playerActionBarButtonOrderKey to """["download"]""",
            ),
            emptyMap<String, Any>(),
        )
        val order = """"buttonOrder":["play-pause"]"""
        assertTrue(order in current)
        val actionOrder = """"actionBarOrder":["download"]"""
        assertTrue(actionOrder in current)
        val document = current.replace(order, """"buttonOrder":["video","play-pause"]""")
            .replace(actionOrder, """"actionBarOrder":["video","download"]""")
        val preview = themePreviewPreferences(document, glass = false)
        assertEquals("""["play_pause"]""", preview.plain[miniPlayerButtonOrderKey])
        assertEquals("""["download"]""", preview.plain[playerActionBarButtonOrderKey])
    }

    @Test
    fun `landscape cover follows portrait until explicitly customized`() {
        fun decode(plain: Map<String, Any>) =
            parseThemeAppearance(encodeThemeAppearance(plain, emptyMap<String, Any>()))
                .getValue(ThemeCategory.PLAYER_AND_QUEUE).plain

        assertEquals(UseThemeDefault, decode(mapOf(thumbnailSizeDpKey to 83f))[thumbnailSizeLDpKey])
        assertEquals(52f, decode(mapOf(thumbnailSizeDpKey to 83f, thumbnailSizeLDpKey to 52f))[thumbnailSizeLDpKey])
    }

    @Test
    fun `nested orders preserve app identifiers and repeated menu separators`() {
        val plain = mapOf(
            homeTabsOrderKey to """["playlists","songs"]""",
            DockToolbarPreferences.orderKey to """["musical_notes","cloud_download","time_outline"]""",
            homeSongsOrderKey to """["on_device","downloaded"]""",
            playerActionBarButtonOrderKey to """["add_to_playlist","lyrics"]""",
            DataStoreUtils.KEY_BURGER_MENU_ORDER to """["history","separator","profiles","separator","settings"]""",
            DataStoreUtils.burgerMenuToggleKey("history") to false,
        )
        val document = encodeThemeAppearance(plain, emptyMap<String, Any>())
        assertEquals(plain, themePreviewPreferences(document, false).plain.filterKeys { it in plain })
    }

    @Test
    fun `sparse previews fill from Stock without adding settings to import`() {
        val partial = """{"schemaVersion":1,"categories":{"colors":{"palette":"dynamic","mode":"light"}}}"""
        val imported = parseThemeAppearance(partial)
        val demo = themePreviewPreferences(partial, glass = true)

        assertEquals(setOf(ThemeCategory.COLORS), imported.keys)
        assertEquals("Light", demo.plain[colorPaletteModeKey])
        assertEquals(25f, demo.plain[uiRoundnessDpKey])
        assertEquals("1111111", demo.plain[Preference.SEARCH_RESULT_GRID_STATES.key])
        assertEquals("Auto", demo.plain[lyricsTypeKey])
        assertEquals(true, demo.plain[glassEnabledKey])
        assertEquals(emptyMap<String, Any>(), demo.glass)
    }

    @Test
    fun `sparse Glass previews keep the chosen quality defaults`() {
        val partial = """
            {"schemaVersion":1,"categories":{"glass":{"enabled":true,"tintSource":"none",
                "navigation":"glass-and-contour","miniPlayer":"glass-and-contour",
                "nowPlaying":"glass-and-contour","quality":"quality"}}}
        """.trimIndent()
        val demo = themePreviewPreferences(partial, glass = true)
        val settings = glassSettingsFromPreferences(demo.glass, GlassQuality.Balanced)

        assertEquals(GlassQuality.Quality, settings.quality)
        assertEquals(glassProfileDefaults(GlassQuality.Quality).blur, settings.blur)
    }

    @Test
    fun `default exported action order retains the live player toolbar`() {
        val partial = """{"schemaVersion":1,"categories":{"colors":{"palette":"dynamic","mode":"light"}}}"""
        val preview = themePreviewPreferences(partial, glass = true)
        val serialized = preview.plain.getValue(playerActionBarButtonOrderKey) as String

        assertEquals(resolvePlayerActionBarOrder(""), resolvePlayerActionBarOrder(serialized))
        assertTrue("add_to_playlist" in resolvePlayerActionBarOrder(serialized))
        assertEquals(listOf("lyrics", "shuffle"), resolvePlayerActionBarOrder("""["lyrics","shuffle"]"""))
    }
}
