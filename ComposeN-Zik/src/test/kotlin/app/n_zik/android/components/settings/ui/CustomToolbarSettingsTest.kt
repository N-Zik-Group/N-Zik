package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.playerTopThemeHiddenActions
import app.n_zik.android.components.dialog.settings.resetInterfaceToolbarSettings
import app.n_zik.android.components.settings.appearance.preview.ThemeAppearanceV2
import app.n_zik.android.components.settings.appearance.preview.ThemeCategory
import app.n_zik.android.components.settings.appearance.preview.ThemeCustomToolbarsMinimumVersionCode
import app.n_zik.android.components.settings.appearance.preview.ThemeSettings
import app.n_zik.android.components.settings.appearance.preview.StoreGroup
import app.n_zik.android.components.settings.appearance.preview.encodeThemeAppearance
import app.n_zik.android.components.settings.appearance.preview.parseThemeAppearance
import app.n_zik.android.components.settings.appearance.preview.themeAppearancePublicationMinimumVersionCode
import app.n_zik.android.components.settings.appearance.preview.toStoreGroups
import app.n_zik.android.components.settings.common.SettingsNestedEntryTargets
import app.n_zik.android.components.settings.common.searchSettingsEntries
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewContext
import app.n_zik.android.utils.showButtonPlayerDiscoverKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CustomToolbarSettingsTest {
    @Test fun `playlist and podcast keep their default menu order and honor saved visibility and order`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("toolbar-overflow", Context.MODE_PRIVATE)
        val common = listOf(ToolbarAction.PLAY_NEXT, ToolbarAction.SELECT, ToolbarAction.FAVORITES)
        val playlist = ToolbarSurface.ONLINE_PLAYLIST
        val podcast = ToolbarSurface.PODCAST
        val playlistMetadata = listOf(ToolbarAction.CHANGE_TITLE, ToolbarAction.CHANGE_ARTISTS, ToolbarAction.CHANGE_PLAYLIST_BROWSE_ID)
        assertEquals(common + playlistMetadata, playlist.readVisibleActions(prefs).filter(playlist.overflowActions::contains))
        assertEquals(common, podcast.readVisibleActions(prefs).filter(podcast.overflowActions::contains))

        prefs.edit().putString(playlist.orderKey, """["favorites","search","select"]""")
            .putBoolean(playlist.visibilityKey(ToolbarAction.SELECT), false).apply()
        assertEquals(
            listOf(ToolbarAction.FAVORITES, ToolbarAction.PLAY_NEXT) + playlistMetadata,
            playlist.readVisibleActions(prefs).filter(playlist.overflowActions::contains),
        )
        assertEquals(common, podcast.readVisibleActions(prefs).filter(podcast.overflowActions::contains))
    }

    @Test fun `hidden controls retain their place and other tabs and profiles keep defaults`() {
        val first = RuntimeEnvironment.getApplication().getSharedPreferences("toolbar-first", Context.MODE_PRIVATE)
        val second = RuntimeEnvironment.getApplication().getSharedPreferences("toolbar-second", Context.MODE_PRIVATE)
        val surface = ToolbarSurface.ALBUM_ONLINE
        val visibility = surface.visibilityKey(ToolbarAction.ENQUEUE)
        first.edit().putString(surface.orderKey, """["enqueue","obsolete","enqueue","shuffle"]""")
            .putBoolean(visibility, false).apply()

        assertFalse(ToolbarAction.ENQUEUE in surface.readVisibleActions(first))
        assertEquals(listOf(ToolbarAction.BOOKMARK, ToolbarAction.SHUFFLE), surface.readVisibleActions(first).take(2))
        assertEquals(surface.actions, surface.readVisibleActions(second))
        assertEquals(ToolbarSurface.ALBUM_DEVICE.actions, ToolbarSurface.ALBUM_DEVICE.readVisibleActions(first))

        first.edit().remove(visibility).apply()
        assertEquals(listOf(ToolbarAction.BOOKMARK, ToolbarAction.ENQUEUE, ToolbarAction.SHUFFLE), surface.readVisibleActions(first).take(3))
    }

    @Test fun `bookmark and follow remain first and visible and are not indexed in settings`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("toolbar-fixed", Context.MODE_PRIVATE)
        ToolbarSurface.entries.forEach { surface ->
            val first = surface.fixedFirst ?: return@forEach
            val second = surface.actions[1]
            prefs.edit().putBoolean(surface.visibilityKey(first), false)
                .putString(surface.orderKey, """["${second.id}","${first.id}"]""").apply()
            assertFalse(first in surface.editableActions)
            assertEquals(first, surface.readVisibleActions(prefs).first())
            assertFalse(SettingsNestedEntryTargets.any { it.nestedDestination?.itemId == "${surface.id}.${first.id}" })
        }
    }

    @Test fun `empty custom toolbar groups are rejected at each depth`() {
        listOf("{}", """{"albumOnline":{}}""", """{"albumOnline":{"visibility":{}}}""").forEach { custom ->
            val document = """{"schemaVersion":2,"categories":{"navigation":{"position":"bottom-floating","labels":"icon-only","toolbars":{"custom":$custom}}}}"""
            assertThrows(IllegalArgumentException::class.java) { parseThemeAppearance(document) }
        }
    }

    @Test fun `selective player appearance merges queue and player toolbar keys from navigation`() {
        val queue = ToolbarSurface.QUEUE.orderKey
        val playerTop = ToolbarSurface.PLAYER_TOP.visibilityKey(ToolbarAction.MENU)
        val album = ToolbarSurface.ALBUM_ONLINE.orderKey
        val settings = linkedMapOf(
            ThemeCategory.NAVIGATION to ThemeSettings(mapOf(queue to "[]", playerTop to false, album to "[]")),
            ThemeCategory.PLAYER_AND_QUEUE to ThemeSettings(mapOf("existingPlayerSetting" to true)),
        )
        val groups = settings.toStoreGroups()
        assertEquals(setOf(queue, playerTop, "existingPlayerSetting"), groups.getValue(StoreGroup.PlayerAndQueue).plain.keys)
        assertEquals(setOf(album), groups.getValue(StoreGroup.Navigation).plain.keys)
    }

    @Test fun `existing queue preference supplies default until overridden`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("toolbar-legacy", Context.MODE_PRIVATE)
        val queue = ToolbarSurface.QUEUE
        prefs.edit().putBoolean(showButtonPlayerDiscoverKey, true).apply()
        assertTrue(ToolbarAction.DISCOVER in queue.readVisibleActions(prefs))
        prefs.edit().putBoolean(queue.visibilityKey(ToolbarAction.DISCOVER), false).apply()
        assertFalse(ToolbarAction.DISCOVER in queue.readVisibleActions(prefs))
    }

    @Test fun `portable V2 round trips toolbar preferences and requires the supporting app version`() {
        val surface = ToolbarSurface.ALBUM_ONLINE
        val prefs = mapOf(
            surface.orderKey to """["enqueue","bookmark"]""",
            surface.visibilityKey(ToolbarAction.BOOKMARK) to false,
        )
        val encoded = encodeThemeAppearance(prefs, emptyMap<String, Any>(), schemaVersion = ThemeAppearanceV2)
        val decoded = parseThemeAppearance(encoded).getValue(ThemeCategory.NAVIGATION).plain
        prefs.forEach { (key, value) -> assertEquals(value, decoded[key]) }
        assertEquals(ThemeCustomToolbarsMinimumVersionCode, themeAppearancePublicationMinimumVersionCode(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            parseThemeAppearance(encoded, allowEmptyCategories = false,
                advertisedMinVersionCode = ThemeCustomToolbarsMinimumVersionCode - 1)
        }
        assertThrows(IllegalArgumentException::class.java) { encodeThemeAppearance(prefs, emptyMap<String, Any>()) }
    }

    @Test fun `defaults omit new fields and existing V1 toolbar data still decodes`() {
        val encoded = encodeThemeAppearance(emptyMap<String, Any>(), emptyMap<String, Any>(), schemaVersion = ThemeAppearanceV2)
        assertFalse(encoded.contains(""""custom":"""))
        val legacy = """
            {"schemaVersion":1,"categories":{"navigation":{
              "position":"bottom-floating","labels":"icon-only",
              "toolbars":{"songs":{"all":{"order":["shuffle"]}}}
            }}}
        """.trimIndent()
        val decoded = parseThemeAppearance(legacy).getValue(ThemeCategory.NAVIGATION).plain
        assertTrue(decoded.values.contains("""["shuffle"]"""))
        assertFalse(decoded.keys.any { it.startsWith("customToolbar.") })
    }

    @Test fun `artist device option resolves to the device tab with a unique item id`() {
        val matches = SettingsNestedEntryTargets.filter {
            it.nestedDestination?.openerId == "ui.toolbar.artist_details" &&
                it.nestedDestination.itemId.endsWith(".select")
        }
        assertEquals(listOf(0, 1, 2), matches.map { it.nestedDestination?.tabIndex })
        assertEquals(3, matches.map { it.nestedDestination?.itemId }.distinct().size)
    }

    @Test fun `artist library defaults include selection and preserve saved order and visibility`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("artist-library-order", Context.MODE_PRIVATE)
        val surface = ToolbarSurface.ARTIST_LIBRARY
        assertEquals(
            listOf("follow", "search", "locate", "shuffle", "playNext", "enqueue", "select", "favorites", "addToPlaylist", "download", "deleteDownloads"),
            surface.readVisibleActions(prefs).map(ToolbarAction::id),
        )
        prefs.edit().putString(surface.orderKey, """["download","enqueue","shuffle"]""")
            .putBoolean(surface.visibilityKey(ToolbarAction.DOWNLOAD), false).apply()
        assertEquals(
            listOf("follow", "enqueue", "shuffle", "search", "locate", "playNext", "select", "favorites", "addToPlaylist", "deleteDownloads"),
            surface.readVisibleActions(prefs).map(ToolbarAction::id),
        )
    }

    @Test fun `top controls hidden by theme are unavailable in the editor`() {
        val surface = ToolbarSurface.PLAYER_TOP
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("player-top-theme-controls", Context.MODE_PRIVATE)
        val menu = surface.visibilityKey(ToolbarAction.MENU)
        val close = surface.visibilityKey(ToolbarAction.COLLAPSE_PLAYER)
        prefs.edit().putBoolean(menu, false).putBoolean(close, false).commit()
        assertEquals(surface.actions, surface.readVisibleActions(prefs))
        assertEquals(setOf(ToolbarAction.MENU, ToolbarAction.COLLAPSE_PLAYER), surface.locked)
        val appearance = encodeThemeAppearance(prefs.all, emptyMap<String, Any>(), schemaVersion = ThemeAppearanceV2)
        assertFalse(parseThemeAppearance(appearance).values.any { menu in it.plain || close in it.plain })
        assertEquals(emptySet<ToolbarAction>(), playerTopThemeHiddenActions(true, false))
        assertEquals(setOf(ToolbarAction.MENU), playerTopThemeHiddenActions(true, true))
        assertEquals(
            setOf(ToolbarAction.COLLAPSE_PLAYER, ToolbarAction.MENU),
            playerTopThemeHiddenActions(false, false),
        )
    }

    @Test fun `toolbar search routes labels to the right dialog and artist tab`() {
        val context = RuntimeEnvironment.getApplication()
        val podcast = searchSettingsEntries(context, context.getString(R.string.settings_toolbar_online_podcast))
        assertTrue(podcast.any { it.id == "ui.toolbar.podcast" })
        val audioVideo = searchSettingsEntries(context, context.getString(R.string.settings_toolbar_audio_video))
        assertTrue(audioVideo.any { it.nestedDestination?.itemId == "top.action.audioVideo" })
        val playlist = searchSettingsEntries(context, context.getString(R.string.playlist_action_add))
        val artistOptions = playlist.filter { it.nestedDestination?.openerId == "ui.toolbar.artist_details" }
        assertEquals(setOf(0, 1, 2), artistOptions.mapNotNull { it.nestedDestination?.tabIndex }.toSet())
    }

    @Test fun `reset restores the current artist home and details defaults`() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("toolbar-reset", Context.MODE_PRIVATE)
        val context = ThemePreviewContext(app, prefs)
        val home = HomeArtistsToolbarSettingsDialog.portableLists().getValue("all")
        val surface = ToolbarSurface.ARTIST_DEVICE
        val queueKey = ToolbarSurface.QUEUE.orderKey
        val legacyPlayerKey = ToolbarSurface.PLAYER_TOP.orderKey
        prefs.edit().putString(home.orderKey, """["search","sort"]""")
            .putString(queueKey, """["shuffle"]""")
            .putString(legacyPlayerKey, """["menu"]""")
            .putString(surface.orderKey, """["enqueue","select"]""")
            .putBoolean(surface.visibilityKey(ToolbarAction.ADD_TO_PLAYLIST), false).commit()

        HomeArtistsToolbarSettingsDialog.reset(context)
        resetInterfaceToolbarSettings(context)
        assertEquals(org.json.JSONArray(home.ids).toString(), prefs.getString(home.orderKey, null))
        assertEquals(surface.actions, surface.readVisibleActions(prefs))
        assertEquals("""["shuffle"]""", prefs.getString(queueKey, null))
        assertEquals("""["menu"]""", prefs.getString(legacyPlayerKey, null))
    }
}
