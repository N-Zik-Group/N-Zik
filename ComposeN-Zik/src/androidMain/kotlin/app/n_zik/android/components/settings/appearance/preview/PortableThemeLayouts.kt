package app.n_zik.android.components.settings.appearance.preview

import app.n_zik.android.components.dialog.settings.albumsDefaultOrder
import app.n_zik.android.components.dialog.settings.albumsThemeToggleKeys
import app.n_zik.android.components.dialog.settings.artistsDefaultOrder
import app.n_zik.android.components.dialog.settings.artistsThemeToggleKeys
import app.n_zik.android.components.dialog.settings.buildSectionDefs
import app.n_zik.android.components.dialog.settings.playlistsDefaultOrder
import app.n_zik.android.components.dialog.settings.playlistsThemeToggleKeys
import app.n_zik.android.components.dialog.settings.songsDefaultOrder
import app.n_zik.android.components.dialog.settings.songsThemeToggleKeys
import app.n_zik.android.enums.DislikeMode
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.excludeDislikedAlbumsKey
import app.n_zik.android.utils.excludeDislikedArtistsKey
import app.n_zik.android.utils.excludeDislikedSongsKey
import app.n_zik.android.utils.homeAlbumsOrderKey
import app.n_zik.android.utils.homeArtistsOrderKey
import app.n_zik.android.utils.homePlaylistsOrderKey
import app.n_zik.android.utils.homeSongsOrderKey
import app.n_zik.android.utils.quickPicksSectionOrderKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

internal fun ThemeFields.homeLayouts() {
    optional("homeSections") {
        group("homeSections") {
            layout("songs", homeSongsOrderKey, songsDefaultOrder, songsThemeToggleKeys, excludeDislikedSongsKey)
            layout("albums", homeAlbumsOrderKey, albumsDefaultOrder, albumsThemeToggleKeys, excludeDislikedAlbumsKey)
            layout("artists", homeArtistsOrderKey, artistsDefaultOrder, artistsThemeToggleKeys, excludeDislikedArtistsKey)
            layout("playlists", homePlaylistsOrderKey, playlistsDefaultOrder, playlistsThemeToggleKeys)
            val quickPicks = buildSectionDefs()
            layout("quickPicks", quickPicksSectionOrderKey, quickPicks.keys, quickPicks.mapValues { it.value.preferenceKey })
        }
    }
}

private fun ThemeFields.layout(
    field: String,
    orderKey: String,
    ids: Collection<String>,
    toggleKeys: Map<String, String>,
    dislikedKey: String? = null,
) {
    optional(field) {
        group(field) {
            optional("order") { order("order", orderKey, ids.toSet()) }
            if (dislikedKey != null) {
                optional("disliked") { option<DislikeMode>("disliked", dislikedKey, DislikeMode.Enabled) }
            }
            toggleKeys.forEach { (id, key) ->
                val name = id.replace('_', '-')
                optional(name) { flag(name, key, true) }
            }
        }
    }
}

internal val portableBurgerKeys: Set<String> get() =
    DataStoreUtils.DEFAULT_BURGER_MENU_ORDER.map(DataStoreUtils::burgerMenuToggleKey).toSet() +
        DataStoreUtils.KEY_BURGER_MENU_ORDER

private val burgerShortcutIds = DataStoreUtils.OPTIONAL_BURGER_MENU_SHORTCUTS
private val burgerShortcutKeys = burgerShortcutIds.map(DataStoreUtils::burgerMenuToggleKey)
private val legacyBurgerIds = DataStoreUtils.DEFAULT_BURGER_MENU_ORDER - burgerShortcutIds.toSet()

private fun Map<String, *>.burgerMenuOrder(): List<String> {
    val stored = this[DataStoreUtils.KEY_BURGER_MENU_ORDER] as? String ?: return emptyList()
    val order = runCatching { Json.parseToJsonElement(stored) as? JsonArray }.getOrNull()
    return order.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }
}

internal fun Map<String, *>.hasBurgerMenuShortcuts(): Boolean =
    burgerShortcutKeys.any(::containsKey) || burgerMenuOrder().any { it in burgerShortcutIds }

/** Drops default shortcut values so opening or resetting the editor preserves legacy theme compatibility. */
internal fun Map<String, *>.withoutDefaultBurgerMenuShortcuts(): Map<String, *> {
    if (!hasBurgerMenuShortcuts() || burgerShortcutKeys.any { this[it] == true }) return this
    val order = burgerMenuOrder()
    val defaultSuffix = order.takeLast(burgerShortcutIds.size) == burgerShortcutIds &&
        order.dropLast(burgerShortcutIds.size).containsAll(legacyBurgerIds)
    if (!defaultSuffix && order.any { it in burgerShortcutIds }) return this
    val legacyPreferences = minus(burgerShortcutKeys.toSet())
    if (!defaultSuffix) return legacyPreferences
    val legacyOrder = JsonArray(order.dropLast(burgerShortcutIds.size).map(::JsonPrimitive))
    return legacyPreferences + (DataStoreUtils.KEY_BURGER_MENU_ORDER to legacyOrder.toString())
}

internal fun ThemeFields.portableBurgerMenu(schemaVersion: Int) {
    optional("burgerMenu") {
        group("burgerMenu") {
            val ids = (if (schemaVersion >= ThemeAppearanceV2) DataStoreUtils.DEFAULT_BURGER_MENU_ORDER else legacyBurgerIds).toSet()
            optional("order") {
                order("order", DataStoreUtils.KEY_BURGER_MENU_ORDER, ids,
                    mapOf("separator" to DataStoreUtils.MAX_BURGER_MENU_SEPARATORS))
            }
            (ids - DataStoreUtils.LOCKED_BURGER_MENU_ITEMS).forEach { id ->
                val name = id.replace('_', '-')
                val toggleKey = DataStoreUtils.burgerMenuToggleKey(id)
                if (id !in burgerShortcutIds || acceptsPortableKey(toggleKey)) {
                    optional(name) { flag(name, toggleKey, DataStoreUtils.burgerMenuDefaultVisibility(id)) }
                }
            }
        }
    }
}
