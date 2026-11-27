package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.utils.lyricsCoverDimmingEnabledKey
import app.n_zik.android.utils.lyricsCoverDimmingOpacityKey
import app.n_zik.android.utils.visualizerCoverDimmingOpacityKey
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.CarouselSize
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.utils.carouselKey
import app.n_zik.android.utils.carouselSizeKey
import app.n_zik.android.utils.expandedplayerKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.thumbnailSpacingKey
import app.n_zik.android.utils.thumbnailSpacingLKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.fontTypeKey
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.thumbnailSizeLDpKey
import app.n_zik.android.utils.hideStatusBarKey
import app.n_zik.android.utils.navigationBarPositionKey
import app.n_zik.android.utils.showButtonPlayerLyricsKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemeStoreApplicationTest {
    private val exported = encodeThemeAppearance(emptyMap<String, String>(), emptyMap<String, String>())
    private val appearance = StoreAppearance(exported, exported.length, parseThemeAppearance(exported).toStoreGroups())

    @Test
    fun m3eShapesAndModernCarouselSettingsSurviveThemeApplication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = context.getSharedPreferences("m3e-carousel", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "m3e-carousel")
        for (animation in listOf(AnimatedGradient.M3EMorphingTheme, AnimatedGradient.M3EMorphingCover)) {
            val settings = mapOf(
                animatedGradientKey to animation.name,
                playerBackgroundColorsKey to PlayerBackgroundColors.AnimatedGradient.name,
                playerTypeKey to PlayerType.Modern.name,
                expandedplayerKey to true,
                carouselKey to true,
                carouselSizeKey to CarouselSize.Biggest.name,
                thumbnailSpacingKey to -15f,
                thumbnailSpacingLKey to 10f,
            )
            val document = encodeThemeAppearance(settings, emptyMap<String, Any>(), schemaVersion = 2)
            val groups = parseThemeAppearance(document).toStoreGroups()
            val saved = StoreAppearance(document, document.length, groups)
            val selected = setOf(StoreGroup.PlayerAndQueue, StoreGroup.Animations)
            requireNotNull(saved.selectedSettings(StoreCompatibility(99), 100, selected)).applyTo(target, glass)
            for ((key, value) in settings) assertEquals(key, value, target.all[key])
        }
    }

    @Test
    fun groupProjectionMovesIndividualSettingsOutOfTheirWireCategories() {
        assertEquals(StoreGroup.entries.toSet(), appearance.groups.keys)
        assertEquals(StoreGroup.Font, ownerOf(fontTypeKey))
        assertEquals(StoreGroup.Navigation, ownerOf(navigationBarPositionKey))
        assertEquals(StoreGroup.PageLayout, ownerOf(hideStatusBarKey))
        assertEquals(StoreGroup.Lyrics, ownerOf(showlyricsthumbnailKey))
        assertEquals(StoreGroup.Lyrics, ownerOf(showButtonPlayerLyricsKey))
        assertEquals(StoreGroup.Animations, ownerOf(animatedGradientKey))

        val baseNavigation = parseThemeAppearance("""{"schemaVersion":1,"categories":{"navigation":{"position":"bottom-floating","labels":"icon-only"}}}""")
        assertEquals(setOf(StoreGroup.Navigation), baseNavigation.toStoreGroups().keys)
    }

    @Test
    fun customizeCanLabelEveryPortableSettingIncludingGlass() {
        val v1 = appearance.groups.values.flatMap { it.plain.keys + it.glass.keys }
        val v2 = parseThemeAppearance(defaultThemeAppearanceDocument()).values.flatMap { it.plain.keys + it.glass.keys }
        val keys = (v1 + v2 + setOf(
            m3eAlwaysShowShapesKey, "playerActionBarTopSlots", "playerActionBarBottomAudioVideo",
            lyricsCoverDimmingEnabledKey, lyricsCoverDimmingOpacityKey, visualizerCoverDimmingOpacityKey,
        )).toSet()
        // Default documents omit optional controls. Customized V2 imports still need labels.
        assertEquals(keys, themeSettingNames.keys.filterNot {
            it.startsWith("customToolbar.") || it == "burger_menu_toggle_search" || it == "burger_menu_toggle_notifications"
        }.toSet())
        assertEquals(true, themeSettingNames.containsKey("customToolbar.albumOnline.visible.search"))
        assertEquals(true, themeSettingNames.containsKey("burger_menu_toggle_search"))
        assertEquals("enabled", themeSettingNames[glassEnabledKey])
        assertEquals("lyrics", themeSettingNames[showButtonPlayerLyricsKey])
    }

    @Test
    fun onlyAnExplicitCompatibleMinimumAllowsApplication() {
        val selected = setOf(StoreGroup.Colors)
        assertNull(appearance.selectedSettings(StoreCompatibility(null), 100, selected))
        assertNull(appearance.selectedSettings(StoreCompatibility(101), 100, selected))
        assertNull(appearance.selectedSettings(StoreCompatibility(100), 100, emptySet()))
        assertNotNull(appearance.selectedSettings(StoreCompatibility(100), 100, selected))
        assertNotNull(appearance.selectedSettings(StoreCompatibility(99), 100, selected))
        assertNull(appearance.selectedSettings(StoreCompatibility(1), 96, selected))
        assertNotNull(appearance.selectedSettings(StoreCompatibility(1), 97, selected))
    }

    @Test
    fun bundledStockDeclaresTheVersionThreeMinimum() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stock = requireNotNull(bundledDefaultTheme(context).theme)
        assertEquals(2, stock.revision)
        assertEquals(ThemeAppearanceV3, stock.schemaVersion)
        assertEquals(ThemeAppearanceV3MinimumVersionCode, stock.compatibility.minVersionCode)
        assertEquals(StoreSupport.RequiresUpdate, stock.compatibility.supportAt(100))
        assertEquals(StoreSupport.Supported, stock.compatibility.supportAt(101))
        assertEquals(StoreSupport.RequiresUpdate, stock.supportAt(100))
    }

    @Test
    fun versionGateChecksTheDocumentEvenWhenAnImportedV2ThemeDeclaredAnEarlierVersion() {
        val document = """{"schemaVersion":2,"categories":{"colors":{"palette":"dynamic","mode":"pure-black"}}}"""
        val imported = StoreAppearance(document, document.length, parseThemeAppearance(document).toStoreGroups())
        val groups = setOf(StoreGroup.Colors)
        val compatibility = StoreCompatibility(99)

        assertEquals(101, imported.minimumVersionCode)
        assertNull(imported.selectedSettings(compatibility, 100, groups))
        assertNotNull(imported.selectedSettings(compatibility, 101, groups))
    }

    @Test
    fun bundledStockClearsSavedM3eShapeOverrideWithItsAnimationSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = context.getSharedPreferences("stock-m3e", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "stock-m3e")
        val saved = bundledDefaultTheme(context)
        val theme = requireNotNull(saved.theme)
        val document = saved.document
        val stock = StoreAppearance(document, document.length, parseThemeAppearance(document).toStoreGroups())
        target.edit().putBoolean(m3eAlwaysShowShapesKey, true).apply()

        assertNull(stock.selectedSettings(theme, 100, setOf(StoreGroup.Colors)))
        requireNotNull(stock.selectedSettings(theme, 100, setOf(StoreGroup.Colors), bundled = true)).applyTo(target, glass)
        assertEquals(true, target.all[m3eAlwaysShowShapesKey])
        val undo = requireNotNull(stock.selectedSettings(theme, 100, setOf(StoreGroup.Animations), bundled = true)).applyTo(target, glass)
        assertFalse(target.contains(m3eAlwaysShowShapesKey))
        undo()
        assertEquals(true, target.all[m3eAlwaysShowShapesKey])
    }

    @Test
    fun selectiveApplyAndUndoOnlyTouchSelectedKeysInTheTargetProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = context.getSharedPreferences("preferences_work", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "work")
        val otherGlass = GlassProfilePreferences.preferences(context, "travel")
        val area = GlassArea.Nav.preferenceKey
        target.edit().putString(colorPaletteModeKey, "Light").putString(fontTypeKey, "Existing font")
            .putString("account", "Existing account").putInt("audioQuality", 3).putString("libraryData", "existing").apply()
        otherGlass.edit().putString(area, "Other profile").apply()
        val beforePlain = target.all
        val beforeGlass = glass.all
        val selected = requireNotNull(appearance.selectedSettings(StoreCompatibility(100), 100, setOf(StoreGroup.Colors, StoreGroup.Glass)))

        val undo = selected.applyTo(target, glass)

        assertEquals(beforePlain + selected.plain, target.all)
        assertEquals(beforeGlass + selected.glass, glass.all)
        assertEquals("Other profile", otherGlass.getString(area, null))

        target.edit().putString(colorPaletteModeKey, "Edited afterwards").apply()
        undo()

        assertEquals(beforePlain + (colorPaletteModeKey to "Edited afterwards"), target.all)
        assertFalse(target.contains(glassEnabledKey))
        assertEquals(beforeGlass, glass.all)
    }


    @Test
    fun customizeRoutesAndUndoesAppMenuSettingsSeparatelyFromProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = context.getSharedPreferences("theme-test-profile", Context.MODE_PRIVATE)
        val appSettings = DataStoreUtils.prefs(context)
        val glass = GlassProfilePreferences.preferences(context, "work")
        val menuKey = DataStoreUtils.burgerMenuToggleKey("history")
        appSettings.edit().putBoolean(menuKey, true).putString("unrelated", "keep").apply()
        val values = mapOf(menuKey to false, homeTabsOrderKey to """["songs"]""")
        val settings = parseThemeAppearance(encodeThemeAppearance(values, emptyMap<String, Any>()))
            .toStoreGroups().getValue(StoreGroup.Navigation)

        val undo = settings.applyTo(profile, glass, appSettings)
        assertFalse(appSettings.getBoolean(menuKey, true))
        assertFalse(profile.contains(menuKey))
        assertEquals("keep", appSettings.getString("unrelated", null))
        assertEquals(values[homeTabsOrderKey], profile.getString(homeTabsOrderKey, null))

        undo()
        assertEquals(true, appSettings.getBoolean(menuKey, false))
        assertFalse(profile.contains(homeTabsOrderKey))
    }


    @Test
    fun importedLandscapeInheritanceClearsAnOverrideAndUndoRestoresIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val profile = context.getSharedPreferences("theme-landscape-profile", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "landscape")
        profile.edit().putFloat(thumbnailSizeLDpKey, 61f).apply()
        val settings = parseThemeAppearance(exported).toStoreGroups().getValue(StoreGroup.PlayerAndQueue)

        val undo = settings.applyTo(profile, glass)
        assertFalse(profile.contains(thumbnailSizeLDpKey))
        undo()
        assertEquals(61f, profile.getFloat(thumbnailSizeLDpKey, 0f))
    }

    private fun ownerOf(key: String): StoreGroup = appearance.groups.entries.single { key in it.value.plain }.key
}
