package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.android.components.dialog.settings.HistorySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeAlbumsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibrarySortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeLibraryToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistSortSettingsDialog
import app.n_zik.android.components.dialog.settings.LocalPlaylistToolbarSettingsDialog
import app.n_zik.android.components.navigation.dock.DockToolbarPreferences
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface

internal data class PortableControlList(
    val orderKey: String,
    val ids: List<String>,
    val visibilityPrefix: String,
    val visibilitySuffix: String,
)

private fun libraryToolbarLists() = mapOf(
    "songs" to HomeSongsToolbarSettingsDialog.portableLists(),
    "artists" to HomeArtistsToolbarSettingsDialog.portableLists(),
    "albums" to HomeAlbumsToolbarSettingsDialog.portableLists(),
    "library" to HomeLibraryToolbarSettingsDialog.portableLists(),
)

internal fun stockLibraryToolbarKeys(): List<String> =
    (libraryToolbarLists().values.flatMap { it.values } + LocalPlaylistToolbarSettingsDialog.portableList())
        .flatMap { list ->
            listOf(list.orderKey) + list.ids.map { list.visibilityPrefix + it + list.visibilitySuffix }
        } + DockToolbarPreferences.orderKey

internal fun ThemeFields.toolbars(schemaVersion: Int) {
    optional("toolbars") {
        group("toolbars") {
            libraryToolbarLists().forEach { (field, lists) -> controlLists(field, lists) }
            controlList("localPlaylist", LocalPlaylistToolbarSettingsDialog.portableList())
            optional("dockOrder") {
                order("dockOrder", DockToolbarPreferences.orderKey)
            }
            if (schemaVersion >= ThemeAppearanceV2) customToolbars(schemaVersion)
        }
    }
    optional("sortMenus") {
        group("sortMenus") {
            controlLists("songs", HomeSongsSortSettingsDialog.portableLists().mapValues { it.value.withoutDeviceSort() })
            controlLists("artists", HomeArtistsSortSettingsDialog.portableLists().mapValues { it.value.withoutDeviceSort() })
            controlLists("albums", HomeAlbumsSortSettingsDialog.portableLists().mapValues { it.value.withoutDeviceSort() })
            controlLists("library", HomeLibrarySortSettingsDialog.portableLists().mapValues { it.value.withoutDeviceSort() })
            controlList("localPlaylist", LocalPlaylistSortSettingsDialog.portableList().withoutDeviceSort())
            controlList("history", HistorySortSettingsDialog.portableList().withoutDeviceSort())
        }
    }
}

private fun PortableControlList.withoutDeviceSort() = copy(ids = ids.filterNot { it == "OnDevice" })

private fun ThemeFields.customToolbars(schemaVersion: Int) {
    fun acceptsVisibility(surface: ToolbarSurface, action: ToolbarAction): Boolean =
        (this is ImportThemeFields || surface != ToolbarSurface.PLAYER_TOP || action !in surface.locked) &&
            acceptsPortableKey(surface.visibilityKey(action))

    val surfaces = ToolbarSurface.entries.filter { surface ->
        (schemaVersion < ThemeAppearanceV3 || surface != ToolbarSurface.PLAYER_TOP) &&
            (this is ImportThemeFields || surface != ToolbarSurface.HISTORY) &&
            (acceptsPortableKey(surface.orderKey) || surface.actions.any { acceptsVisibility(surface, it) })
    }
    if (surfaces.isEmpty()) return
    optional("custom") {
        group("custom") {
            (this as? ImportThemeFields)?.requireNonEmptyGroup()
            surfaces.forEach { surface ->
                optional(surface.id) {
                    group(surface.id) {
                        (this as? ImportThemeFields)?.requireNonEmptyGroup()
                        if (acceptsPortableKey(surface.orderKey)) optional("order") {
                            order("order", surface.orderKey, surface.ids.toSet())
                        }
                        val actions = surface.actions.filter { acceptsVisibility(surface, it) }
                        if (actions.isNotEmpty()) optional("visibility") {
                            group("visibility") {
                                (this as? ImportThemeFields)?.requireNonEmptyGroup()
                                actions.forEach { action ->
                                    optional(action.id) {
                                        flag(action.id, surface.visibilityKey(action), surface.defaultVisible(action))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun Iterable<String>.hasCustomToolbarSettings(): Boolean =
    any { it.startsWith("customToolbar.") }

private fun ThemeFields.controlLists(field: String, lists: Map<String, PortableControlList>) {
    optional(field) {
        group(field) {
            lists.forEach { (name, list) -> controlList(name, list) }
        }
    }
}

private fun ThemeFields.controlList(field: String, list: PortableControlList) {
    optional(field) {
        group(field) {
            optional("order") {
                order("order", list.orderKey, list.ids.toSet())
            }
            optional("visibility") {
                group("visibility") {
                    list.ids.forEach { id ->
                        optional(id) {
                            flag(id, list.visibilityPrefix + id + list.visibilitySuffix, true)
                        }
                    }
                }
            }
        }
    }
}
