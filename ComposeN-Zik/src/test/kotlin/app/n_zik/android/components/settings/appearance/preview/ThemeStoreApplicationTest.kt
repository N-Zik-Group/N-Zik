package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.fontTypeKey
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
    private val exported = encodeThemeV1(emptyMap<String, String>(), emptyMap<String, String>())
    private val appearance = StoreAppearance(exported, exported.length, parseThemeV1(exported).toStoreGroups())

    @Test
    fun groupProjectionMovesIndividualSettingsOutOfTheirWireCategories() {
        assertEquals(StoreGroup.entries.toSet(), appearance.groups.keys)
        assertEquals(StoreGroup.Font, ownerOf(fontTypeKey))
        assertEquals(StoreGroup.Navigation, ownerOf(navigationBarPositionKey))
        assertEquals(StoreGroup.PageLayout, ownerOf(hideStatusBarKey))
        assertEquals(StoreGroup.Lyrics, ownerOf(showlyricsthumbnailKey))
        assertEquals(StoreGroup.Lyrics, ownerOf(showButtonPlayerLyricsKey))
        assertEquals(StoreGroup.Animations, ownerOf(animatedGradientKey))

        val baseNavigation = parseThemeV1("""{"schemaVersion":1,"categories":{"navigation":{"position":"bottom-floating","labels":"icon-only"}}}""")
        assertEquals(setOf(StoreGroup.Navigation), baseNavigation.toStoreGroups().keys)
    }

    @Test
    fun customizeCanLabelEveryPortableSettingIncludingGlass() {
        val keys = appearance.groups.values.flatMap { it.plain.keys + it.glass.keys }.toSet()
        assertEquals(keys, themeSettingNames.keys)
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

    private fun ownerOf(key: String): StoreGroup = appearance.groups.entries.single { key in it.value.plain }.key
}
