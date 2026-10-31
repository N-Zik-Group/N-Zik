package app.n_zik.android.components.glass

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassContextPolicyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)
    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun `legacy choices inherit in every supported role without writing migration overrides`() {
        val store = GlassStore(context)
        GlassArea.entries.forEachIndexed { index, area ->
            store.setAreaMode(area, GlassAreaMode.entries[index % GlassAreaMode.entries.size])
        }
        val before = preferences.all.toMap()
        val settings = GlassStore(context).read()
        GlassScope.entries.forEach { scope -> scope.areas.forEach { area ->
            assertEquals("$scope/$area", settings.mode(area), settings.contextMode(scope, area))
        } }
        assertTrue(settings.contextModes.isEmpty())
        assertEquals(before, preferences.all)
    }

    @Test fun `each explicit role survives reopening and does not alter siblings or common choices`() {
        val store = GlassStore(context)
        store.setAreaMode(GlassArea.ActionButtons, GlassAreaMode.ContourOnly)
        GlassScope.entries.forEach { scope -> scope.areas.forEach { area ->
            GlassAreaMode.entries.forEach { mode ->
                store.setContextMode(scope, area, mode)
                val settings = GlassStore(context).read()
                assertEquals(mode, settings.contextMode(scope, area))
                assertEquals(GlassAreaMode.ContourOnly, settings.mode(GlassArea.ActionButtons))
                GlassScope.entries.filter { it != scope }.forEach { other ->
                    assertEquals("$scope must not change $other", settings.mode(area), settings.contextMode(other, area))
                }
            }
            store.setContextMode(scope, area, null)
        } }
        assertTrue(GlassStore(context).read().contextModes.isEmpty())
    }

    @Test fun `shared changes update inheriting destinations and removing override restores live inheritance`() {
        val store = GlassStore(context)
        store.setContextMode(GlassScope.Home, GlassArea.ActionButtons, GlassAreaMode.Disabled)
        store.setAreaMode(GlassArea.ActionButtons, GlassAreaMode.GlassOnly)
        var settings = GlassStore(context).read()
        assertEquals(GlassAreaMode.Disabled, settings.contextMode(GlassScope.Home, GlassArea.ActionButtons))
        assertEquals(GlassAreaMode.GlassOnly, settings.contextMode(GlassScope.Library, GlassArea.ActionButtons))
        store.setContextMode(GlassScope.Home, GlassArea.ActionButtons, null)
        settings = GlassStore(context).read()
        assertEquals(GlassAreaMode.GlassOnly, settings.contextMode(GlassScope.Home, GlassArea.ActionButtons))
        assertFalse(preferences.contains(GlassContextArea(GlassScope.Home, GlassArea.ActionButtons).preferenceKey))
    }

    @Test fun `quality changes only optical defaults and reset clears all contextual and shared overrides`() {
        val store = GlassStore(context)
        val fresh = store.read()
        store.setProfile(GlassQuality.Balanced)
        store.setAreaMode(GlassArea.ActionButtons, GlassAreaMode.ContourOnly)
        store.setContextMode(GlassScope.PlayerTransport, GlassArea.ActionButtons, GlassAreaMode.Disabled)
        store.setTint(GlassTintSource.Artwork)
        store.setColor(0xff345678.toInt())
        store.setFloat(glassTintStrengthKey, .25f)
        store.setFloat(glassDialogDimmingKey, .22f)
        store.setFloat(glassMenuDimmingKey, .33f)
        store.setFloat(glassBlurKey, 20f)
        val before = store.read()
        store.setProfile(GlassQuality.Quality)
        val after = GlassStore(context).read()
        assertEquals(4f, after.blur, 0f)
        assertEquals(before.areaModes, after.areaModes)
        assertEquals(before.contextModes, after.contextModes)
        assertEquals(before.source, after.source)
        assertEquals(before.customColor, after.customColor)
        assertEquals(before.tintStrength, after.tintStrength, 0f)
        assertEquals(before.dialogDimming, after.dialogDimming, 0f)
        assertEquals(before.menuDimming, after.menuDimming, 0f)
        store.reset()
        assertEquals(fresh, GlassStore(context).read())
        assertFalse(preferences.all.keys.any { it.startsWith("context.") })
    }

    @Test fun `disabled master and image constraints remain authoritative with unknown data ignored`() {
        preferences.edit().putString("context.Future.action_buttons_mode", "GlassOnly")
            .putString(GlassContextArea(GlassScope.Home, GlassArea.ActionButtons).preferenceKey, "FutureMode").commit()
        val store = GlassStore(context)
        assertTrue(store.read().contextModes.isEmpty())
        store.setContextMode(GlassScope.Home, GlassArea.ActionButtons, GlassAreaMode.GlassAndContour)
        store.setAreaMode(GlassArea.Images, GlassAreaMode.GlassOnly)
        val settings = GlassStore(context).read().inScope(GlassScope.Home).copy(enabled = false)
        assertFalse(settings.usesGlassMaterial(GlassArea.ActionButtons))
        assertFalse(settings.usesGlassContour(GlassArea.ActionButtons))
        assertEquals(GlassAreaMode.ContourOnly, settings.mode(GlassArea.Images))
        assertEquals(GlassAreaMode.GlassAndContour, settings.inScope(GlassScope.Library).mode(GlassArea.ActionButtons))
    }

    @Test fun `insights routes use the Details card override rather than shared cards`() {
        val config = GlassSettings(areaModes = mapOf(GlassArea.SettingsCards to GlassAreaMode.Disabled),
            contextModes = mapOf(GlassContextArea(GlassScope.Details, GlassArea.SettingsCards) to GlassAreaMode.GlassOnly))
        listOf("artistInsights/artist-id", "albumInsights/album-id").forEach { route ->
            assertEquals(GlassAreaMode.GlassOnly, config.inScope(glassScopeForRoute(route)).mode(GlassArea.SettingsCards))
        }
        assertEquals(GlassAreaMode.Disabled, config.mode(GlassArea.SettingsCards))
    }

    @Test fun `route scopes recognize actual destinations and unknown names keep common policy`() {
        mapOf("home" to GlassScope.Home, "localPlaylist/{id}" to GlassScope.Details,
            "artistAlbums/{id}?params={params}" to GlassScope.Details,
            "artistInsights/{id}" to GlassScope.Details, "albumInsights/{id}" to GlassScope.Details,
            "profiles" to GlassScope.SettingsProfiles,
            "settings" to GlassScope.SettingsProfiles, "rewindHome" to GlassScope.Rewind,
            "searchResults/{query}" to GlassScope.Search, "queue" to GlassScope.Queue,
            "history" to GlassScope.Library, "futureSearch" to GlassScope.Shared, "" to GlassScope.Shared)
            .forEach { (route, expected) -> assertEquals(route, expected, glassScopeForRoute(route)) }
        assertEquals(GlassScope.Shared, glassScopeForRoute(null))
    }
}
