package app.n_zik.android.components.settings.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassContextArea
import app.n_zik.android.components.glass.GlassScope
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.contextMode
import app.n_zik.android.components.settings.common.searchSettingsEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GlassDestinationCatalogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences get() = context.getSharedPreferences("nzik_square_glass_v2", Context.MODE_PRIVATE)

    @Before fun clear() { preferences.edit().clear().commit() }

    @Test fun `every shared area and supported context has exactly one searchable settings row`() {
        val rows = GlassDestinationGroup.entries.flatMap(::glassDestinationRows)
        val expected = GlassArea.entries.map { GlassContextArea(GlassScope.Shared, it) } +
            GlassScope.entries.flatMap { scope -> scope.areas.map { GlassContextArea(scope, it) } }
        assertEquals(expected.toSet(), rows.map { GlassContextArea(it.scope, it.area) }.toSet())
        assertEquals(expected.size, rows.size)
        assertEquals(rows.size, rows.map { it.searchId }.toSet().size)
        rows.forEach { row ->
            assertEquals(row.title, glassSearchOption(row.searchId)?.target?.labelRes)
        }
    }

    @Test fun `title favorite search has its own playback destination`() {
        val row = glassDestinationRows(GlassDestinationGroup.Playback).single { it.scope == GlassScope.PlayerFavorite }
        assertEquals(R.string.glass_role_player_favorite, row.title)
        assertEquals("destination.Playback", glassSearchOption(row.searchId)?.section)
        assertTrue(searchSettingsEntries(context, context.getString(row.title)).any { it.id == row.searchId })
        assertTrue(glassDestinationKeywordResources().contains(row.description))
    }

    @Test fun `title favorite override persists independently and reset restores inherited actions`() {
        val store = GlassStore(context)
        store.setAreaMode(GlassArea.ActionButtons, GlassAreaMode.ContourOnly)
        store.setContextMode(GlassScope.PlayerHeader, GlassArea.ActionButtons, GlassAreaMode.GlassOnly)
        store.setContextMode(GlassScope.PlayerTransport, GlassArea.ActionButtons, GlassAreaMode.Disabled)
        GlassAreaMode.entries.forEach { mode ->
            store.setContextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons, mode)
            val reloaded = GlassStore(context).read()
            assertEquals(mode, reloaded.contextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons))
            assertEquals(GlassAreaMode.GlassOnly, reloaded.contextMode(GlassScope.PlayerHeader, GlassArea.ActionButtons))
            assertEquals(GlassAreaMode.Disabled, reloaded.contextMode(GlassScope.PlayerTransport, GlassArea.ActionButtons))
            assertEquals(GlassAreaMode.ContourOnly, reloaded.contextMode(GlassScope.PlayerInformation, GlassArea.ActionButtons))
        }
        store.setContextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons, null)
        assertEquals(GlassAreaMode.ContourOnly, store.read().contextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons))
        assertFalse(preferences.contains(GlassContextArea(GlassScope.PlayerFavorite, GlassArea.ActionButtons).preferenceKey))
        store.setContextMode(GlassScope.PlayerFavorite, GlassArea.ActionButtons, GlassAreaMode.Disabled)
        store.reset()
        assertTrue(store.read().contextModes.isEmpty())
    }
}
